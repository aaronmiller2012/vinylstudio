package com.halftonestudio;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Build, validate and bound a cutter ZIP on app-private disk before SAF publication.
 * This does not imply that the selected content provider offers atomic commits.
 */
final class StagedCutZip implements AutoCloseable {
    static final long MAX_ZIP_BYTES=128_000_000L;
    static final long MAX_ENTRY_BYTES=48_000_000L;
    private final File file;
    private boolean opened, verified;
    StagedCutZip(File cache) throws IOException {
        file=File.createTempFile("vinyl-cut-", ".pending.zip",cache);
    }
    OutputStream openStream() throws IOException {
        if(opened)throw new IOException("Export stage already opened");opened=true;verified=false;
        return new BufferedOutputStream(new FilterOutputStream(new FileOutputStream(file)) {
            private long written;
            private void budget(int n)throws IOException {if(n<0||written+n>MAX_ZIP_BYTES)
                throw new IOException("Cut archive exceeds staging disk budget");written+=n;}
            @Override public void write(int b)throws IOException {budget(1);out.write(b);}
            @Override public void write(byte[] b,int o,int n)throws IOException {budget(n);out.write(b,o,n);}
        },32768);
    }
    void verify() throws IOException {
        verified=false;
        if(!opened||file.length()<32||file.length()>MAX_ZIP_BYTES)throw new IOException("Incomplete cut export stage");
        int files=0,svgs=0,dxfs=0;long bytes=0;
        Set<String> names=new HashSet<>();
        // ZipInputStream reads members fully and checks entry CRC on closeEntry.
        try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            ZipEntry e;byte[] block=new byte[32768];
            while((e=zip.getNextEntry())!=null){
                String name=e.getName();
                if(++files>96||name==null||name.isEmpty()||name.indexOf('/')>=0||name.indexOf('\\')>=0||
                    name.contains("..")||e.isDirectory()||!names.add(name))throw new IOException("Unsafe cut ZIP member");
                boolean svg=name.toLowerCase(Locale.ROOT).endsWith(".svg");
                boolean dxf=name.toLowerCase(Locale.ROOT).endsWith(".dxf");
                if(svg)svgs++;
                if(dxf)dxfs++;
                java.io.ByteArrayOutputStream dxfStart=dxf?new java.io.ByteArrayOutputStream():null;
                byte[] dxfTail=dxf?new byte[64]:null;int tailCount=0;
                int n;long member=0;
                while((n=zip.read(block))!=-1){
                    member+=n;bytes+=n;
                    if(dxf){
                        int first=Math.min(n,2048-dxfStart.size());if(first>0)dxfStart.write(block,0,first);
                        for(int b=0;b<n;b++)dxfTail[(tailCount++)%dxfTail.length]=block[b];
                    }
                    if(member>MAX_ENTRY_BYTES||bytes>MAX_ZIP_BYTES*2)throw new IOException("Cut ZIP exceeds entry budget");
                }
                if(dxf){
                    String prefix=dxfStart.toString("US-ASCII");
                    if(!prefix.startsWith("0\r\nSECTION\r\n")||!prefix.contains("AC1012"))
                        throw new IOException("Cut ZIP contains invalid/non-R13 DXF sheet: "+name);
                    byte[] last=new byte[Math.min(tailCount,dxfTail.length)];
                    int from=tailCount-last.length;
                    for(int i=0;i<last.length;i++)last[i]=dxfTail[(from+i)%dxfTail.length];
                    String suffix=new String(last,java.nio.charset.StandardCharsets.US_ASCII);
                    if(!suffix.contains("0\r\nENDSEC\r\n0\r\nEOF\r\n"))
                        throw new IOException("Cut ZIP DXF sheet is incomplete: "+name);
                }
                zip.closeEntry();
            }
        }catch(ZipException ex){throw new IOException("Cut ZIP CRC verification failed",ex);}
        if(svgs==0&&dxfs==0)throw new IOException("Cut export has no SVG or DXF sheets");
        // Each output destination uses one physical coordinate convention.
        // Mixing R13 DXF and SVG in a single job can silently change scale.
        if(svgs>0&&dxfs>0)throw new IOException("Mixed SVG/DXF cut sheets are not one cutter job");
        try(ZipFile zip=new ZipFile(file)){
            ZipEntry csv=zip.getEntry("layer-manifest.csv");
            if(csv!=null){
                // Never allocate the manifest based on an untrusted ZIP size.
                if(csv.getSize()>1_000_000)throw new IOException("Oversized layer manifest");
                String manifest;
                try(InputStream in=zip.getInputStream(csv)){
                    ByteArrayOutputStream buffer=new ByteArrayOutputStream();byte[] chunk=new byte[4096];int n;
                    while((n=in.read(chunk))!=-1){if(buffer.size()+n>1_000_000)throw new IOException("Oversized manifest");buffer.write(chunk,0,n);}
                    manifest=buffer.toString("UTF-8");
                }
                String[] lines=manifest.split("\\r?\\n");
                for(int i=1;i<lines.length;i++){
                    String[] fields=lines[i].split(",",-1);
                    if(fields.length>1&&(fields[1].endsWith(".svg")||fields[1].endsWith(".dxf"))&&!names.contains(fields[1]))
                        throw new IOException("Layer manifest refers to missing cut sheet: "+fields[1]);
                }
            }
        }
        // Publication must be impossible unless the *entire* ZIP was read and
        // its CRC, member names, and layer references were checked.
        verified=true;
    }
    void copyTo(OutputStream destination) throws IOException {
        if(!verified)throw new IOException("Cut archive has not passed final verification");
        if(destination==null)throw new IOException("Export destination unavailable");
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))){
            byte[] block=new byte[32768];int n;
            while((n=in.read(block))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Export interrupted");destination.write(block,0,n);}
            destination.flush();
        }
    }
    /** Verify that a document provider actually persisted the exact ZIP bytes.
     * Some providers return a writable stream but leave a zero-length or
     * truncated document after it closes. No bitmap or ZIP is loaded in RAM.
     */
    void verifyPublishedCopy(InputStream published) throws IOException {
        if(!verified)throw new IOException("Unverified cut archive cannot be checked for publication");
        if(published==null)throw new IOException("Unable to read saved cut ZIP for verification");
        byte[] sourceBytes=new byte[32768],savedBytes=new byte[32768];
        try(InputStream expected=new BufferedInputStream(new FileInputStream(file))){
            int n;
            while((n=expected.read(sourceBytes))!=-1){
                int offset=0;
                while(offset<n){
                    int got=published.read(savedBytes,offset,n-offset);
                    if(got<0)throw new IOException("Saved cut ZIP is truncated or empty");
                    if(got==0){
                        int next=published.read();
                        if(next<0)throw new IOException("Saved cut ZIP is truncated or empty");
                        savedBytes[offset++]=(byte)next;
                    }else offset+=got;
                }
                for(int i=0;i<n;i++)if(sourceBytes[i]!=savedBytes[i])
                    throw new IOException("Saved cut ZIP differs from verified export stage");
            }
            if(published.read()!=-1)throw new IOException("Saved cut ZIP contains unexpected extra bytes");
        }
    }
    @Override public void close(){if(file.exists()&&!file.delete())file.deleteOnExit();}
}

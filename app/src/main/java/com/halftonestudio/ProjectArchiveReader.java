package com.halftonestudio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Bounded, single-pass reader for the existing photo/settings project ZIP format. */
public final class ProjectArchiveReader {
    private ProjectArchiveReader() {}
    public static final int MAX_ENTRIES=16;
    public static final long MAX_UNCOMPRESSED=86_000_000L;
    public static final long MAX_IMAGE_ENTRY=32_000_000L;
    public static final long MAX_SETTINGS_ENTRY=1_000_000L;
    public static final long MAX_UNKNOWN_ENTRY=65_536L;
    public static final long MAX_SOURCE_ENTRY=20_000_000L;
    public static final String NATIVE_SOURCE_ENTRY="source-original.bin";
    /** One logical image per role, regardless of filename extension. Shared by
     * legacy import, streaming import, and library's two validation passes. */
    static final class EntryPolicy {
        private final Set<String> names=new HashSet<>();
        private boolean photo, original, settings, source, manifest;
        private int count;
        static final class EntryInfo {
            final boolean photo,original,settings,source;
            final long limit;
            EntryInfo(boolean photo,boolean original,boolean settings,boolean source,long limit){
                this.photo=photo;this.original=original;this.settings=settings;this.source=source;this.limit=limit;
            }
            boolean image(){return photo||original;}
        }
        EntryInfo accept(String name) throws IOException {
            if(++count>MAX_ENTRIES)throw new IOException("Too many project archive entries");
            if(name==null||name.isEmpty()||name.contains("..")||name.indexOf('/')>=0||
                    name.indexOf('\\')>=0||!names.add(name))
                throw new IOException("Invalid or duplicate project archive entry");
            boolean p=name.equals("photo.png")||name.equals("photo.jpg");
            boolean o=name.equals("original.png")||name.equals("original.jpg");
            boolean j=name.equals("settings.json");
            boolean n=name.equals(NATIVE_SOURCE_ENTRY);
            if(name.equals(ProjectManifest.ENTRY)){
                if(manifest)throw new IOException("Multiple project manifests");
                manifest=true;
                return new EntryInfo(false,false,false,false,ProjectManifest.MAX_BYTES);
            }
            if(p&&photo)throw new IOException("Multiple photo entries");
            if(o&&original)throw new IOException("Multiple original entries");
            if(j&&settings)throw new IOException("Multiple settings entries");
            if(n&&source)throw new IOException("Multiple native source entries");
            photo|=p;original|=o;settings|=j;source|=n;
            return new EntryInfo(p,o,j,n,j?MAX_SETTINGS_ENTRY:(p||o)?MAX_IMAGE_ENTRY:
                    n?MAX_SOURCE_ENTRY:MAX_UNKNOWN_ENTRY);
        }
        void requireComplete() throws IOException {
            if(!photo||!settings)throw new IOException("Missing project photo or settings");
        }
    }
    public static final class ProjectEntries {
        public final byte[] photo,original,settings,nativeSource;
        private ProjectEntries(byte[] photo,byte[] original,byte[] settings,byte[] nativeSource) {
            this.photo=photo;this.original=original;this.settings=settings;this.nativeSource=nativeSource;
        }
    }
    /** Fixed chunks avoid ByteArrayOutputStream's oversized backing-array copies
     * when reopening 20-32MB photos. One final exactly-sized image array remains.
     */
    private static final class ChunkCollector {
        private final java.util.ArrayList<byte[]> chunks=new java.util.ArrayList<>();
        private final java.util.ArrayList<Integer> sizes=new java.util.ArrayList<>();
        private int total;
        void write(byte[] b,int off,int n){
            if(n==0)return;
            byte[] copy=new byte[n];System.arraycopy(b,off,copy,0,n);
            chunks.add(copy);sizes.add(n);total+=n;
        }
        byte[] finish(){
            byte[] result=new byte[total];int offset=0;
            for(int i=0;i<chunks.size();i++){
                int n=sizes.get(i);System.arraycopy(chunks.get(i),0,result,offset,n);offset+=n;
            }
            return result;
        }
    }
    /**
     * v3: reopen a project without retaining two 32 MB compressed image arrays.
     * Images are streamed into private short-lived files; settings and optional
     * native source remain bounded in memory for backward compatibility.
     *
     * The caller MUST close StagedProject in a finally block or register its
     * close() method with ProjectLoadOwnership before allocating bitmaps.
     */
    public static final class StagedProject implements AutoCloseable {
        public final java.io.File photo, original;
        public final byte[] settings, nativeSource;
        public final String photoFormat, originalFormat;
        private boolean closed;
        private StagedProject(java.io.File photo, java.io.File original,
                              byte[] settings, byte[] nativeSource,
                              String photoFormat, String originalFormat) {
            this.photo=photo;this.original=original;this.settings=settings;
            this.nativeSource=nativeSource;this.photoFormat=photoFormat;
            this.originalFormat=originalFormat;
        }
        @Override public synchronized void close() {
            if(closed)return;
            closed=true;
            if(photo!=null)photo.delete();
            if(original!=null)original.delete();
        }
    }
    private static final class StreamImageCheck {
        final java.io.ByteArrayOutputStream header=new java.io.ByteArrayOutputStream(96*1024);
        final byte[] tail=new byte[12];
        final ProjectImageSignature.PngChunks png;
        long bytes;int tailWritten;
        StreamImageCheck(String name){png=name.endsWith(".png")?new ProjectImageSignature.PngChunks():null;}
        void accept(byte[] buffer,int n)throws IOException {
            if(png!=null)png.accept(buffer,0,n);
            int head=(int)Math.min(n,96*1024-header.size());
            if(head>0)header.write(buffer,0,head);
            for(int i=0;i<n;i++)tail[tailWritten++%12]=buffer[i];
            bytes+=n;
        }
        String finish(String name)throws IOException {
            if(png!=null)png.finish();
            byte[] end=new byte[Math.min(tailWritten,12)];
            for(int i=0;i<end.length;i++)end[i]=tail[(tailWritten-end.length+i)%12];
            ProjectImageSignature.requireValidSuffix(name,header.toByteArray(),bytes,end);
            return name.endsWith(".png")?"png":"jpg";
        }
    }
    /** Uses only the private application cache and refuses path-like ZIP names. */
    public static StagedProject readToFiles(ZipInputStream zip,java.io.File cacheDir) throws IOException {
        if(zip==null||cacheDir==null||!cacheDir.isDirectory())
            throw new IOException("Project archive staging directory unavailable");
        java.io.File photo=null,original=null;
        byte[] settings=null,nativeSource=null;
        String photoFormat=null,originalFormat=null;
        long total=0;
        EntryPolicy policy=new EntryPolicy();
        byte[] buffer=new byte[32768];
        try {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){
                if(Thread.currentThread().isInterrupted())
                    throw new IOException("Project open canceled");
                String name=entry.getName();
                EntryPolicy.EntryInfo info=policy.accept(name);
                boolean isPhoto=info.photo, isOriginal=info.original;
                boolean isSettings=info.settings, isSource=info.source;
                long limit=info.limit;
                if(entry.getSize()>limit)throw new IOException("Project entry exceeds size limit");
                java.io.File staged=null;
                java.io.OutputStream output=null;
                java.io.ByteArrayOutputStream mem=(isSettings||isSource)?new java.io.ByteArrayOutputStream():null;
                StreamImageCheck check=(isPhoto||isOriginal)?new StreamImageCheck(name):null;
                try {
                    if(check!=null){
                        // Host-tested pure Java (no StorageManager); a conservative low-space guard only.
                        //noinspection UsableSpace
                        if(cacheDir.getUsableSpace()>0&&cacheDir.getUsableSpace()<8L*1024*1024)
                            throw new IOException("Insufficient private storage to open project");
                        staged=java.io.File.createTempFile("vinyl-v3-reopen-", "."+(name.endsWith(".png")?"png":"jpg"),cacheDir);
                        output=new java.io.BufferedOutputStream(new java.io.FileOutputStream(staged));
                    }
                    int n;long entryBytes=0;
                    while((n=zip.read(buffer))!=-1){
                        if(Thread.currentThread().isInterrupted())throw new IOException("Project open canceled");
                        if(n==0)continue;
                        entryBytes+=n;total+=n;
                        if(entryBytes>limit||total>MAX_UNCOMPRESSED)
                            throw new IOException("Project archive exceeds decompression limits");
                        if(output!=null)output.write(buffer,0,n);
                        if(mem!=null)mem.write(buffer,0,n);
                        if(check!=null)check.accept(buffer,n);
                    }
                    if(output!=null){output.close();output=null;}
                    if(check!=null){
                        String format=check.finish(name);
                        if(isPhoto){photo=staged;photoFormat=format;}
                        else{original=staged;originalFormat=format;}
                        staged=null;
                    }else if(isSettings){settings=mem.toByteArray();
                    }else if(isSource){nativeSource=mem.toByteArray();}
                }finally{
                    if(output!=null)try{output.close();}catch(IOException ignored){}
                    if(staged!=null)staged.delete();
                }
                zip.closeEntry();
            }
            policy.requireComplete();
            JsonSyntaxVerifier.requireObject(settings);
            return new StagedProject(photo,original,settings,nativeSource,photoFormat,originalFormat);
        }catch(IOException|RuntimeException|Error error){
            if(photo!=null)photo.delete();if(original!=null)original.delete();
            throw error;
        }
    }
    public static ProjectEntries read(ZipInputStream zip) throws IOException {
        if(zip==null)throw new IOException("Missing project input stream");
        byte[] photo=null,original=null,settings=null,nativeSource=null;
        long total=0;EntryPolicy policy=new EntryPolicy();
        byte[] chunk=new byte[8192];ZipEntry entry;
        while((entry=zip.getNextEntry())!=null) {
            EntryPolicy.EntryInfo info=policy.accept(entry.getName());
            String name=entry.getName();
            boolean isPhoto=info.photo, isOriginal=info.original;
            boolean isSettings=info.settings, isSource=info.source;
            long limit=info.limit;
            // Every entry, including unknown entries, is streamed and counted.
            // This prevents a small compressed ZIP from expanding without bound.
            if(entry.getSize()>limit && entry.getSize()>=0)throw new IOException("Oversized ZIP entry: "+name);
            ChunkCollector buffer=(isPhoto||isOriginal||isSettings||isSource)?new ChunkCollector():null;
            long bytes=0;int n;
            while((n=zip.read(chunk))!=-1) {
                if(Thread.currentThread().isInterrupted())throw new IOException("Project verification canceled");
                bytes+=n;total+=n;
                if(bytes>limit||total>MAX_UNCOMPRESSED)
                    throw new IOException("Project ZIP exceeds size limit");
                if(buffer!=null)buffer.write(chunk,0,n);
            }
            if(isPhoto) {if(photo!=null)throw new IOException("Multiple photo entries");photo=buffer.finish();}
            if(isOriginal) {if(original!=null)throw new IOException("Multiple original entries");original=buffer.finish();}
            if(isSettings)settings=buffer.finish();
            if(isSource)nativeSource=buffer.finish();
            zip.closeEntry();
        }
        policy.requireComplete();
        // This legacy in-memory path is also used for verifying freshly
        // written projects. Never accept an archive with malformed settings
        // merely because ZIP entries and CRCs were intact.
        JsonSyntaxVerifier.requireObject(settings);
        return new ProjectEntries(photo,original,settings,nativeSource);
    }
}

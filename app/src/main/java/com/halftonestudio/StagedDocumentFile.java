package com.halftonestudio;

import java.io.*;

/** Stages PNG/PDF documents to prevent failures during generation from writing
 * half a file to the user's SAF-selected destination. Final provider copy is
 * not guaranteed atomic (the storage provider controls its own persistence).
 */
final class StagedDocumentFile implements AutoCloseable {
    enum Kind { PDF, PNG }
    private static final long MAX_BYTES=128_000_000L;
    private final File file;
    private boolean opened;
    StagedDocumentFile(File cache)throws IOException {file=File.createTempFile("vinyl-document-",".pending",cache);}
    OutputStream openStream()throws IOException {
        if(opened)throw new IOException("Document stage already opened");
        opened=true;
        return new BufferedOutputStream(new FilterOutputStream(new FileOutputStream(file)){
            private long written;
            private void budget(int n)throws IOException{
                if(n<0||written+n>MAX_BYTES)throw new IOException("Artwork document exceeds staging budget");
                written+=n;
            }
            @Override public void write(int b)throws IOException{budget(1);out.write(b);}
            @Override public void write(byte[] data,int offset,int count)throws IOException{
                budget(count);out.write(data,offset,count);
            }
        },32768);
    }
    void verify(Kind kind)throws IOException {
        if(!opened||file.length()<24||file.length()>MAX_BYTES)throw new IOException("Incomplete artwork document");
        if(kind==Kind.PNG){verifyPngChunks();return;}
        try(RandomAccessFile in=new RandomAccessFile(file,"r")){
            byte[] head=new byte[5];in.readFully(head);
            if(!"%PDF-".equals(new String(head,java.nio.charset.StandardCharsets.US_ASCII)))
                throw new IOException("Invalid PDF header");
            int length=(int)Math.min(2048,file.length());
            byte[] tail=new byte[length];in.seek(file.length()-length);in.readFully(tail);
            if(!new String(tail,java.nio.charset.StandardCharsets.ISO_8859_1).contains("%%EOF"))
                throw new IOException("PDF is missing its final trailer");
        }
    }
    /** Validate the entire PNG chunk sequence and CRCs, not just an IEND label.
     * Streams compressed IDAT bytes without inflating or allocating image pixels.
     */
    private void verifyPngChunks()throws IOException {
        byte[] signature={(byte)137,80,78,71,13,10,26,10};
        try(DataInputStream stream=new DataInputStream(new BufferedInputStream(new FileInputStream(file)))){
            byte[] head=new byte[8];stream.readFully(head);
            if(!java.util.Arrays.equals(signature,head))throw new IOException("Invalid PNG signature");
            boolean ihdr=false,idat=false,iend=false;
            long read=8L,length=file.length();byte[] buffer=new byte[32768];
            while(!iend){
                if(length-read<12)throw new IOException("Truncated PNG chunk header");
                long size=stream.readInt()&0xffffffffL;read+=4;
                byte[] type=new byte[4];stream.readFully(type);read+=4;
                if(size>MAX_BYTES||size>length-read-4)
                    throw new IOException("Invalid PNG chunk length");
                for(byte b:type)if(!((b>='a'&&b<='z')||(b>='A'&&b<='Z')))
                    throw new IOException("Invalid PNG chunk name");
                String name=new String(type,java.nio.charset.StandardCharsets.US_ASCII);
                if(!ihdr){
                    if(!"IHDR".equals(name)||size!=13)
                        throw new IOException("PNG has no valid IHDR");
                    ihdr=true;
                }else if("IHDR".equals(name))throw new IOException("Duplicate PNG header");
                if("IDAT".equals(name))idat=true;
                if("IEND".equals(name)){
                    if(size!=0||!idat)throw new IOException("PNG ended without image data");
                    iend=true;
                }
                java.util.zip.CRC32 crc=new java.util.zip.CRC32();crc.update(type);
                long remaining=size;
                while(remaining>0){
                    int n=(int)Math.min(remaining,buffer.length);
                    stream.readFully(buffer,0,n);crc.update(buffer,0,n);
                    remaining-=n;read+=n;
                }
                long expected=stream.readInt()&0xffffffffL;read+=4;
                if(crc.getValue()!=expected)throw new IOException("PNG chunk CRC mismatch: "+name);
            }
            if(read!=length)throw new IOException("PNG has extra data after IEND");
        }
    }
    void copyTo(OutputStream target)throws IOException {
        if(target==null)throw new IOException("No destination selected");
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))){
            byte[] chunk=new byte[32768];int n;
            while((n=in.read(chunk))!=-1){
                if(Thread.currentThread().isInterrupted())throw new IOException("Document export interrupted");
                target.write(chunk,0,n);
            }target.flush();
        }
    }
    @Override public void close(){if(file.exists()&&!file.delete())file.deleteOnExit();}
}

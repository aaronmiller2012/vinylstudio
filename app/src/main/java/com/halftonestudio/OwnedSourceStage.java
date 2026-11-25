package com.halftonestudio;

import java.io.*;
import java.nio.file.*;

/** One immutable, bounded staging file per request. Caller owns its lifetime. */
final class OwnedSourceStage implements AutoCloseable {
    final File file;
    private OwnedSourceStage(File file){this.file=file;}
    static OwnedSourceStage capture(File directory,InputStream source,long limit) throws IOException {
        if(source==null)throw new IOException("Source is inaccessible");
        File pending=null;
        try(InputStream in=source){
            pending=File.createTempFile("vinyl-owned-source-",".pending",directory);
            try(FileOutputStream out=new FileOutputStream(pending)){
                byte[] buffer=new byte[32768];long count=0;int n;
                while((n=in.read(buffer))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Source capture canceled");
                    count+=n;if(count>limit)throw new IOException("Source exceeds size limit");
                    out.write(buffer,0,n);
                }
                if(count==0)throw new IOException("Source is empty");
                out.getFD().sync();
            }
            return new OwnedSourceStage(pending);
        }catch(IOException|RuntimeException|OutOfMemoryError failure){if(pending!=null)pending.delete();throw failure;}
    }
    InputStream open() throws IOException {return new BufferedInputStream(new FileInputStream(file));}
    void publish(File target) throws IOException {
        Files.move(file.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    @Override public void close(){file.delete();}
}

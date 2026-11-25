package com.halftonestudio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

/** Immutable source bytes captured before ZIP writing. The caller owns the
 * snapshot and removes it in finally. Null sources remain optional.
 * No exported project is allowed to read a mutable global cache path.
 */
final class ProjectSourceSnapshot implements AutoCloseable {
    final File archiveOriginal,nativeOriginal;
    private final File directory;
    private ProjectSourceSnapshot(File dir,File archive,File nativePhoto){
        directory=dir;archiveOriginal=archive;nativeOriginal=nativePhoto;
    }
    static ProjectSourceSnapshot capture(File cache,File archive,File nativePhoto)throws IOException{
        File dir=new File(cache,"vinyl-project-snapshot-"+UUID.randomUUID());
        if(!dir.mkdir())throw new IOException("Could not create project source snapshot");
        try{
            File a=copy(archive,new File(dir,"encoded-original"),ProjectArchiveReader.MAX_IMAGE_ENTRY);
            if(archive!=null&&a==null)throw new IOException("Full-resolution project original disappeared during save");
            File n=copy(nativePhoto,new File(dir,"native-original"),NativeLimit.MAX);
            // Preserve extension so the archive writer retains PNG or JPEG.
            if(a!=null){File renamed=new File(dir,archive.getName().endsWith(".jpg")?"original.jpg":"original.png");
                if(!a.renameTo(renamed))throw new IOException("Could not label encoded original");a=renamed;}
            return new ProjectSourceSnapshot(dir,a,n);
        }catch(IOException|RuntimeException|OutOfMemoryError failure){delete(dir);throw failure;}
    }
    private static final class NativeLimit {static final long MAX=96L*1024*1024;}
    private static File copy(File from,File to,long maximum)throws IOException{
        if(from==null||!from.isFile())return null;
        if(from.length()<1||from.length()>maximum)throw new IOException("Original source out of bounds");
        try(FileInputStream in=new FileInputStream(from);FileOutputStream out=new FileOutputStream(to)){
            byte[] bytes=new byte[32768];int n;long count=0;
            while((n=in.read(bytes))!=-1){
                if(Thread.currentThread().isInterrupted())throw new IOException("Project snapshot cancelled");
                count+=n;if(count>maximum)throw new IOException("Original source exceeded snapshot limit");
                out.write(bytes,0,n);
            }
            if(count!=from.length())throw new IOException("Original changed while snapshotting");
            out.getFD().sync();
        }
        return to;
    }
    private static void delete(File dir){
        if(dir!=null&&dir.isDirectory())for(File f:dir.listFiles()==null?new File[0]:dir.listFiles())f.delete();
        if(dir!=null)dir.delete();
    }
    @Override public void close(){delete(directory);}
}

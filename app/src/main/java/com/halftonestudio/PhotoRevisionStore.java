package com.halftonestudio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** A pair of saved photographs is a transaction, not two independent renames.
 * The pointer changes only AFTER both encoded files are durable. No global
 * bitmap is retained and legacy single-file data remains readable.
 */
final class PhotoRevisionStore {
    static final String ROOT="vinyl-photo-revisions", PHOTO="editing-photo.png", ORIGINAL="editing-original.png";
    private static final String POINTER="active.txt";
    static final class Pair {
        final File photo,original;
        Pair(File photo,File original){this.photo=photo;this.original=original;}
    }
    private PhotoRevisionStore(){}
    private static File root(File directory){return new File(directory,ROOT);}
    private static boolean validId(String id){return id!=null&&id.matches("rev-[a-f0-9-]{36}");}
    private static String[] pointer(File directory){
        File p=new File(root(directory),POINTER);
        if(!p.isFile())return new String[0];
        try{
            String[] lines=new String(Files.readAllBytes(p.toPath()),StandardCharsets.US_ASCII).split("\\n",-1);
            if(lines.length<2||!validId(lines[0]))return new String[0];
            return new String[]{lines[0],validId(lines[1])?lines[1]:""};
        }catch(IOException e){return new String[0];}
    }
    private static Pair fromId(File directory,String id){
        if(!validId(id))return null;
        File dir=new File(root(directory),id),photo=new File(dir,PHOTO),original=new File(dir,ORIGINAL);
        return photo.isFile()&&photo.length()>0&&original.isFile()&&original.length()>0?
            new Pair(photo,original):null;
    }
    /** An incomplete revision is never selected, even after process termination. */
    static synchronized Pair active(File directory){
        String[] ids=pointer(directory);
        if(ids.length!=0){
            Pair current=fromId(directory,ids[0]);
            if(current!=null)return current;
            Pair previous=fromId(directory,ids[1]);
            if(previous!=null)return previous;
            return null;
        }
        // Migration only: pre-v3 projects stored two independent files.
        File photo=new File(directory,PHOTO),original=new File(directory,ORIGINAL);
        if(!photo.isFile()||photo.length()==0)return null;
        return new Pair(photo,original.isFile()&&original.length()>0?original:photo);
    }
    static synchronized File photo(File directory){Pair p=active(directory);return p==null?new File(directory,PHOTO):p.photo;}
    static synchronized File original(File directory){Pair p=active(directory);return p==null?new File(directory,ORIGINAL):p.original;}
    static synchronized boolean hasPhoto(File directory){return photo(directory).isFile();}
    interface Writer {void write(File photo,File original,Pair prior) throws IOException;}
    static synchronized void commit(File directory,Writer write) throws IOException {
        File root=root(directory);
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Cannot create photo revision directory");
        String id="rev-"+UUID.randomUUID().toString();
        File stage=new File(root,id+".pending"),committed=new File(root,id);
        if(!stage.mkdir())throw new IOException("Cannot stage photo revision");
        String[] old=pointer(directory);
        Pair prior=active(directory);
        boolean committedDirectory=false;
        try{
            write.write(new File(stage,PHOTO),new File(stage,ORIGINAL),prior);
            if(new File(stage,PHOTO).length()<1||new File(stage,ORIGINAL).length()<1)
                throw new IOException("Photo revision is incomplete");
            moveAtomic(stage.toPath(),committed.toPath());
            committedDirectory=true;
            // The existing active pointer may already reference a damaged
            // revision. Retain the last *readable* pair as the rollback, not
            // the first (possibly corrupt) revision ID in active.txt.
            String previous="";
            if(old.length!=0){
                if(fromId(directory,old[0])!=null)previous=old[0];
                else if(fromId(directory,old[1])!=null)previous=old[1];
            }
            File pointerTemp=new File(root,"active-"+UUID.randomUUID()+".tmp");
            try(FileOutputStream out=new FileOutputStream(pointerTemp)){
                out.write((id+"\n"+previous+"\n").getBytes(StandardCharsets.US_ASCII));
                out.getFD().sync();
            }
            try{moveAtomic(pointerTemp.toPath(),new File(root,POINTER).toPath());}
            finally{pointerTemp.delete();}
            // Keep the preceding revision for recovery if a committed file is lost.
            for(File child:root.listFiles()==null?new File[0]:root.listFiles()){
                if(child.isDirectory()&&child.getName().startsWith("rev-")&&
                   !child.getName().equals(id)&&!child.getName().equals(previous))deleteTree(child);
            }
        }finally{
            deleteTree(stage);
            if(!committedDirectory)deleteTree(committed);
        }
    }
    static void sync(File file) throws IOException {
        try(java.io.FileInputStream in=new java.io.FileInputStream(file)){in.getFD().sync();}
    }
    static void copyOrLink(File source,File destination) throws IOException {
        if(source==null||!source.isFile())throw new IOException("Prior original is missing");
        try{Files.createLink(destination.toPath(),source.toPath());}
        catch(IOException|UnsupportedOperationException unsupported){
            Files.copy(source.toPath(),destination.toPath(),StandardCopyOption.REPLACE_EXISTING);
            sync(destination);
        }
    }
    private static void moveAtomic(Path source,Path target) throws IOException {
        try{Files.move(source,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException failure){
            throw new IOException("Filesystem does not support atomic photo revisions",failure);
        }
    }
    private static void deleteTree(File f){
        if(f.isDirectory())for(File c:f.listFiles()==null?new File[0]:f.listFiles())deleteTree(c);
        f.delete();
    }
    static synchronized void clear(File directory){deleteTree(root(directory));new File(directory,PHOTO).delete();new File(directory,ORIGINAL).delete();}
}

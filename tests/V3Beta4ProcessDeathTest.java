package com.halftonestudio;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Actual separate-JVM termination tests, not merely mock exceptions. */
public final class V3Beta4ProcessDeathTest {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static void write(File p,String data)throws IOException{Files.writeString(p.toPath(),data);}
    private static String read(File p)throws IOException{return Files.readString(p.toPath());}
    private static void save(File dir,String suffix)throws Exception{
        PhotoRevisionStore.commit(dir,(photo,original,prior)->{
            write(photo,"PHOTO_"+suffix);write(original,"ORIGINAL_"+suffix);
        });
    }
    private static void pair(File dir,String suffix)throws Exception{
        PhotoRevisionStore.Pair p=PhotoRevisionStore.active(dir);
        check(p!=null,"No readable photo revision");
        check(read(p.photo).equals("PHOTO_"+suffix),"Editor photo revision mismatch");
        check(read(p.original).equals("ORIGINAL_"+suffix),"Camera original revision mismatch");
    }
    private static int child(File dir,String mode)throws Exception{
        Process process=new ProcessBuilder(System.getProperty("java.home")+File.separator+"bin"+File.separator+"java",
            "-cp",System.getProperty("java.class.path"),V3Beta4ProcessDeathTest.class.getName(),"child",dir.toString(),mode)
            .inheritIO().start();
        check(process.waitFor(15,TimeUnit.SECONDS),"Child save process hung");
        return process.exitValue();
    }
    private static void runParent()throws Exception{
        File dir=Files.createTempDirectory("vinyl-beta4-process-").toFile();
        save(dir,"A");pair(dir,"A");
        check(child(dir,"after-first-image")==31,"First crash injection did not terminate");pair(dir,"A");
        check(child(dir,"after-both-images")==32,"Second crash injection did not terminate");pair(dir,"A");
        check(child(dir,"after-commit")==33,"Committed crash injection did not terminate");pair(dir,"B");
        check(child(dir,"queued-before-start")==34,"Queued crash injection did not terminate");pair(dir,"B");
        // This is a KNOWN LIMIT, not a claim that queued-only edits recover.
        System.out.println("PASS Beta4 process-death recovery: partial images rolled back, committed pair retained");
        System.out.println("KNOWN LIMIT: in-memory-only queued edits are lost when killed before staging");
        PhotoRevisionStore.clear(dir);
    }
    private static void runChild(File dir,String mode)throws Exception{
        switch(mode){
            case "after-first-image":
                PhotoRevisionStore.commit(dir,(p,o,prior)->{
                    write(p,"PHOTO_B");Runtime.getRuntime().halt(31);
                });break;
            case "after-both-images":
                PhotoRevisionStore.commit(dir,(p,o,prior)->{
                    write(p,"PHOTO_B");write(o,"ORIGINAL_B");Runtime.getRuntime().halt(32);
                });break;
            case "after-commit":
                save(dir,"B");Runtime.getRuntime().halt(33);break;
            case "queued-before-start":
                CountDownLatch wait=new CountDownLatch(1);
                PhotoPersistenceQueue.execute(()->{
                    try{wait.await();}catch(InterruptedException ex){Thread.currentThread().interrupt();}
                });
                PhotoPersistenceQueue.execute(()->{
                    try{save(dir,"C");}catch(Exception error){throw new RuntimeException(error);}
                });
                Runtime.getRuntime().halt(34);break;
            default:throw new IllegalArgumentException(mode);
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==3&&args[0].equals("child"))runChild(new File(args[1]),args[2]);
        else runParent();
    }
}

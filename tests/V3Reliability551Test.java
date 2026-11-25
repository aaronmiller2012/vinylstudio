package com.halftonestudio;

import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.zip.*;

/** Reproduces overlapping source selection, stale leases and library rotation. */
public final class V3Reliability551Test {
    private static void check(boolean pass,String reason){if(!pass)throw new AssertionError(reason);}
    private static byte[] bytes(String text){return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);}
    private static StudioProjectLibrary.ArchiveWriter archive(String label){
        byte[] png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAFUlEQVR4nGPUSNnCwMDAwMDAxAADABYwAUTo8FNRAAAAAElFTkSuQmCC");
        return out->StudioProjectArchiveWriter.write(new ZipOutputStream(out),stream->{try{stream.write(png);return true;}catch(IOException error){return false;}},stream->{try{stream.write(png);return true;}catch(IOException error){return false;}},"{\"name\":\""+label+"\"}");
    }
    private static String settings(InputStream in)throws Exception{
        try(ZipInputStream zip=new ZipInputStream(in)){return new String(ProjectArchiveReader.read(zip).settings,java.nio.charset.StandardCharsets.UTF_8);}
    }
    private static void remove(File file){if(file.isDirectory())for(File f:file.listFiles())remove(f);file.delete();}
    public static void main(String[] args)throws Exception{
        File dir=Files.createTempDirectory("vinyl551-").toFile();
        try{
            File target=new File(dir,"active.bin");Files.write(target.toPath(),bytes("old"));
            OwnedSourceStage a=OwnedSourceStage.capture(dir,new ByteArrayInputStream(bytes("A")),100);
            OwnedSourceStage b=OwnedSourceStage.capture(dir,new ByteArrayInputStream(bytes("B")),100);
            check(!a.file.equals(b.file),"overlapping import stage collision");
            a.close();check(b.file.isFile()&&Files.readString(target.toPath()).equals("old"),"cancel changed active or second import");
            b.publish(target);b.close();check(Files.readString(target.toPath()).equals("B"),"wrong original published");
            int count=dir.list().length;
            try{OwnedSourceStage.capture(dir,new ByteArrayInputStream(bytes("too long")),3);throw new AssertionError("size gate");}catch(IOException expected){}
            check(dir.list().length==count,"failed stage leaked file");
            OwnedSourceStage stale=OwnedSourceStage.capture(dir,new ByteArrayInputStream(bytes("stale")),100);
            ProjectLoadOwnership lease=new ProjectLoadOwnership();lease.own(stale::close);lease.workerFinished(true);lease.cancel();
            check(!stale.file.exists()&&!lease.transferToUi(),"destroyed UI retained import stage");
            StudioProjectSessionState session=new StudioProjectSessionState();long t=session.beginLoad();session.beginLoad();check(!session.isCurrentLoad(t),"superseded import accepted");
            StudioProjectLibrary one=StudioProjectLibrary.shared(dir),two=StudioProjectLibrary.shared(new File(dir,"."));
            check(one==two,"rotation acquired a separate monitor");
            StudioProjectLibrary.Entry entry=one.save(null,"test","CMYK",null,archive("A"));
            try(InputStream pinned=one.openArchive(entry.id)){
                two.save(entry.id,"test","CMYK",null,archive("B"));two.save(entry.id,"test","CMYK",null,archive("C"));
                check(settings(pinned).contains("\"A\""),"pruned revision changed an open stream");
            }
            check(settings(two.openArchive(entry.id)).contains("\"C\""),"latest revision mismatch");
            try(InputStream pinned=one.openArchive(entry.id)){two.delete(entry.id);check(settings(pinned).contains("\"C\""),"delete invalidated open archive");}
            check(one.get(entry.id)==null,"deleted project reappeared");
            StudioProjectLibrary.Entry queued=one.save(null,"Queued","CMYK",null,archive("queued"));
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            StudioLibraryJobs jobs=new StudioLibraryJobs(one,Runnable::run);
            ByteArrayOutputStream exported=new ByteArrayOutputStream();
            jobs.exportProject(queued.id,()->{
                entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("test timeout");}
                catch(InterruptedException e){throw new InterruptedIOException("export interrupted");}
                return exported;
            },()->{},error->{});
            try{
                check(entered.await(5,TimeUnit.SECONDS),"worker did not reach provider");
                jobs.rename(queued.id,"Retained",ignored->{},error->{});
                jobs.close();
            }finally{release.countDown();}
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(!"Retained".equals(one.get(queued.id).name)&&System.nanoTime()<until)Thread.sleep(10);
            check("Retained".equals(one.get(queued.id).name),"activity close dropped accepted rename");
            check(settings(new ByteArrayInputStream(exported.toByteArray())).contains("queued"),"rotation truncated provider export");

            System.out.println("PASS 551 isolated import staging, cancellation, bounds, process-shared library and pinned archive reads");
        }finally{remove(dir);}
    }
}

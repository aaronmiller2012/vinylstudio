package com.halftonestudio;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.zip.*;

/** Test asynchronous serialization and all library operations without Android. */
public final class StudioLibraryJobsTest {
    static void yes(boolean value,String text){if(!value)throw new AssertionError(text);}
    static <T> T await(Consumer<Consumer<T>> op) throws Exception {
        CountDownLatch latch=new CountDownLatch(1);
        AtomicReference<T> value=new AtomicReference<>();
        op.accept(x->{value.set(x);latch.countDown();});
        yes(latch.await(15,TimeUnit.SECONDS),"asynchronous library operation timed out");
        return value.get();
    }
    static byte[] archive() throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(b)){
            for(String name:new String[]{"photo.png","settings.json"}){
                z.putNextEntry(new ZipEntry(name));
                z.write(name.equals("photo.png")?java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAFUlEQVR4nGPUSNnCwMDAwMDAxAADABYwAUTo8FNRAAAAAElFTkSuQmCC"):"{}".getBytes("UTF-8"));
                z.closeEntry();
            }
        }
        return b.toByteArray();
    }
    public static void main(String[] unused) throws Exception {
        File root=Files.createTempDirectory("vinyl-rc56-jobs-").toFile();
        StudioProjectLibrary lib=new StudioProjectLibrary(root);
        byte[] bytes=archive();
        StudioProjectLibrary.Entry stored=lib.save(null,"Original","Stencil",new byte[]{(byte)137,80,78,71,13,10,26,10,0,0,0,13,73,72,68,82,0,0,0,1,0,0,0,1},out->out.write(bytes));
        try(StudioLibraryJobs jobs=new StudioLibraryJobs(lib,Runnable::run)){
            AtomicReference<Exception> err=new AtomicReference<>();
            List<StudioProjectLibrary.Entry> items=await(done->jobs.list(done,e->{err.set(e);done.accept(null);}));
            yes(err.get()==null&&items.size()==1,"list from worker");
            StudioProjectLibrary.Entry renamed=await(done->jobs.rename(stored.id,"Renamed",done,e->{err.set(e);done.accept(null);}));
            yes(err.get()==null&&"Renamed".equals(renamed.name),"rename from worker");
            ByteArrayOutputStream projectOut=new ByteArrayOutputStream();
            Boolean exported=await(done->jobs.exportProject(stored.id,()->projectOut,()->done.accept(true),e->{err.set(e);done.accept(false);}));
            yes(exported&&Arrays.equals(bytes,projectOut.toByteArray()),"stream per-project export");
            ByteArrayOutputStream backup=new ByteArrayOutputStream();
            Integer count=await(done->jobs.backup(()->backup,done,e->{err.set(e);done.accept(null);}));
            yes(err.get()==null&&count==1,"all-project backup");
            Integer imported=await(done->jobs.restore(()->new ByteArrayInputStream(backup.toByteArray()),done,e->{err.set(e);done.accept(null);}));
            yes(err.get()==null&&imported==0&&lib.list().size()==1,"restore must skip original content");
            Boolean deleted=await(done->jobs.delete(stored.id,()->done.accept(true),e->{err.set(e);done.accept(false);}));
            yes(deleted&&lib.list().isEmpty(),"delete in worker");
        }
        System.out.println("PASS RC56 background project list/rename/export/backup/restore/delete");
    }
}

package com.halftonestudio;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** Behavioral verification using genuine project ZIPs, not source-code text checks. */
public final class V3M3DamagedLibraryTest {
    private static void yes(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    private static <T> T await(Consumer<Consumer<T>> start) throws Exception {
        CountDownLatch done=new CountDownLatch(1);AtomicReference<T> value=new AtomicReference<>();
        start.accept(v->{value.set(v);done.countDown();});
        yes(done.await(30,TimeUnit.SECONDS),"worker operation timed out");return value.get();
    }
    private static void delete(File file){
        if(file.isDirectory()){File[] files=file.listFiles();if(files!=null)for(File inner:files)delete(inner);}
        if(file.exists())yes(file.delete(),"could not clean fixture");
    }
    public static void main(String[] args)throws Exception {
        File root=Files.createTempDirectory("vinyl-m3-damaged-").toFile();
        try {
            StudioProjectLibrary library=new StudioProjectLibrary(root);
            byte[] valid=StudioLibraryJobsTest.archive();
            StudioProjectLibrary.Entry saved=library.save(null,"Family portrait","Lines",null,out->out.write(valid));
            yes(library.list().size()==1&&!library.list().get(0).damaged,"valid project should be healthy");

            // A backup must be complete, restorable, and based on the real ZIP payload.
            ByteArrayOutputStream backup=new ByteArrayOutputStream();
            try(StudioLibraryJobs jobs=new StudioLibraryJobs(library,Runnable::run)) {
                AtomicReference<Exception> failed=new AtomicReference<>();
                Integer count=await(done->jobs.backup(()->backup,done,e->{failed.set(e);done.accept(null);}));
                yes(failed.get()==null&&count!=null&&count==1,"staged backup must complete");
                yes(backup.size()>valid.length,"backup ZIP should have content");
                File verify=new File(root,"complete-backup.zip");
                Files.write(verify.toPath(),backup.toByteArray());
                try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(verify)){
                    yes(zip.getEntry("manifest.properties")!=null,"staged backup manifest absent");
                    yes(zip.size()>=4,"backup lacks actual project payload");
                }
            }
            // A torn staged ZIP must be rejected before its target document is opened.
            File torn=new File(root,"torn-backup.zip");
            byte[] truncated=Arrays.copyOf(backup.toByteArray(),backup.size()-19);
            Files.write(torn.toPath(),truncated);
            try{StudioProjectBackup.verifyStaged(torn,1);throw new AssertionError("torn staged ZIP accepted");}
            catch(IOException expected){}
            File original=library.archive(saved.id);
            byte[] corruption=new byte[]{17,23,42,7};
            Files.write(original.toPath(),corruption);
            StudioProjectLibrary.Entry damaged=library.list().get(0);
            yes(damaged.damaged,"all-corrupt project must remain visible in gallery");
            yes("Family portrait".equals(damaged.name),"card label should survive archive damage");
            yes(library.get(saved.id)==null,"damaged project must not be treated as readable");
            try(InputStream in=library.openDamagedArchiveForRecovery(saved.id)){
                yes(Arrays.equals(in.readAllBytes(),corruption),"recovery export must retain exact corrupt bytes");
            }
            try{StudioProjectBackup.preflight(library);throw new AssertionError("backup silently omitted damage");}
            catch(IOException expected){yes(expected.getMessage().contains("Unreadable"),"backup should name damaged project");}
            try(StudioLibraryJobs jobs=new StudioLibraryJobs(library,Runnable::run)){
                AtomicInteger destinationOpens=new AtomicInteger();AtomicReference<Exception> failed=new AtomicReference<>();
                Integer skipped=await(done->jobs.backup(()->{destinationOpens.incrementAndGet();return new ByteArrayOutputStream();},
                    done,e->{failed.set(e);done.accept(null);}));
                yes(skipped==null&&failed.get()!=null&&destinationOpens.get()==0,
                    "failed preflight must leave selected backup document untouched");
                ByteArrayOutputStream recovery=new ByteArrayOutputStream();
                Boolean recovered=await(done->jobs.exportDamagedProject(saved.id,()->recovery,()->done.accept(true),
                    e->{failed.set(e);done.accept(false);}));
                yes(Boolean.TRUE.equals(recovered)&&Arrays.equals(recovery.toByteArray(),corruption),
                    "recovery export lost corrupt bytes");
            }
            // Damaged entries cannot prevent importing independent good projects.
            int imported=StudioProjectBackup.importAll(new ByteArrayInputStream(backup.toByteArray()),library);
            yes(imported==1,"restoring into a damaged library should remain possible");
            yes(library.list().size()==2,"damaged entry must be retained alongside restored one");
            library.delete(saved.id);
            yes(library.list().size()==1,"corrupted revision must be explicitly deletable");
            yes(!library.list().get(0).damaged,"restored project should be healthy");
            yes(library.list().get(0).name.equals("Family portrait"),"restored label mismatch");
            // A revision whose ZIP was deleted entirely must still appear for deletion.
            StudioProjectLibrary.Entry vanished=library.save(null,"Missing file","Stencil",null,
                out->out.write(valid));
            yes(library.archive(vanished.id).delete(),"could not simulate missing archive");
            StudioProjectLibrary.Entry missing=library.list().stream().filter(e->e.id.equals(vanished.id))
                .findFirst().orElseThrow(()->new AssertionError("missing ZIP entry was hidden"));
            yes(missing.damaged&&"Missing file".equals(missing.name),"lost-archive card was not retained");
            library.delete(vanished.id);
            yes(library.list().size()==1,"missing ZIP entry was not removable");
            // No private staging files are left after success or failure.
            File[] files=new File(root,"project-library").listFiles();
            if(files!=null)for(File f:files)yes(!f.getName().startsWith("vinyl-backup-"),"backup staging leaked");
            System.out.println("PASS M3 unreadable project lifecycle, raw recovery, staged backup and restore");
        }finally{delete(root);}
    }
}

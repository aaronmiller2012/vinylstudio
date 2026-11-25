package com.halftonestudio;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/**
 * RC170 safety requirement, stated behaviorally: every library job that writes a
 * user-selected document (project export, damaged-project recovery export, library
 * backup) must REPLACE that document, never leave stale trailing bytes, and must not
 * touch it at all when the job fails before producing output.
 *
 * The simulated provider appends unless asked for "wt", which is how a provider that
 * ignores plain "w" truncation corrupts a ZIP that is shorter than the old file.
 */
public final class LibraryDocumentTruncationTest {
    private static void yes(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    private static final byte[] STALE=new byte[256*1024];
    static{Arrays.fill(STALE,(byte)0x5A);}

    /** A SAF-like document whose output mode is honored literally. */
    static final class Document {
        final File file;final List<String> modes=new ArrayList<>();
        Document(File dir,String name) throws IOException {
            file=new File(dir,name);Files.write(file.toPath(),STALE);
        }
        StudioLibraryJobs.OutputTarget target(){
            return LibraryDocumentStreams.document(mode->{
                modes.add(mode);
                return new FileOutputStream(file,!mode.contains("t"));
            });
        }
        byte[] bytes() throws IOException {return Files.readAllBytes(file.toPath());}
        boolean untouched() throws IOException {return modes.isEmpty()&&Arrays.equals(bytes(),STALE);}
    }
    /** Jobs run on the library worker thread: wait for success OR failure. */
    private static <T> T run(java.util.function.BiConsumer<Consumer<T>,Consumer<Exception>> start,
                             AtomicReference<Exception> failure) throws InterruptedException {
        java.util.concurrent.CountDownLatch latch=new java.util.concurrent.CountDownLatch(1);
        AtomicReference<T> value=new AtomicReference<>();
        start.accept(v->{value.set(v);latch.countDown();},e->{failure.set(e);latch.countDown();});
        yes(latch.await(30,java.util.concurrent.TimeUnit.SECONDS),"library job timed out");
        return value.get();
    }
    private static void delete(File file){
        File[] inner=file.listFiles();if(inner!=null)for(File f:inner)delete(f);
        file.delete();
    }
    public static void main(String[] args) throws Exception {
        File root=Files.createTempDirectory("vinyl-truncation-").toFile();
        try{
            StudioProjectLibrary library=new StudioProjectLibrary(root);
            byte[] zip=StudioLibraryJobsTest.archive();
            yes(zip.length<STALE.length,"fixture must be shorter than the stale document");
            StudioProjectLibrary.Entry good=library.save(null,"Good","Lines",null,out->out.write(zip));
            StudioProjectLibrary.Entry broken=library.save(null,"Broken","Lines",null,out->out.write(zip));
            try(StudioLibraryJobs jobs=new StudioLibraryJobs(library,Runnable::run)){
                AtomicReference<Exception> failure=new AtomicReference<>();

                // 1. Library backup (healthy library): staged, then replaces the document.
                Document backup=new Document(root,"backup.zip");
                Integer count=run((done,fail)->jobs.backup(backup.target(),done,fail),failure);
                yes(failure.get()==null&&count!=null&&count==2,"backup failed: "+failure.get());
                yes(backup.modes.equals(List.of("wt")),"backup must open its document exactly once in wt");
                yes(backup.bytes().length<STALE.length,"backup left stale trailing bytes");
                try(ZipFile z=new ZipFile(backup.file)){yes(z.size()==7,"backup document is not a complete ZIP");}
                StudioProjectBackup.verifyStaged(backup.file,2);

                // 2. Project export: exact archive bytes, nothing left over.
                Document export=new Document(root,"project.zip");
                Boolean exported=run((done,fail)->jobs.exportProject(good.id,export.target(),()->done.accept(true),fail),failure);
                yes(Boolean.TRUE.equals(exported)&&failure.get()==null,"project export failed: "+failure.get());
                yes(export.modes.equals(List.of("wt")),"project export must open in wt");
                yes(Arrays.equals(export.bytes(),zip),"project export is not byte-exact (stale tail?)");

                // 3. Damaged-project recovery: exact corrupt bytes, nothing left over.
                byte[] corrupt={9,8,7,6,5};
                Files.write(library.archive(broken.id).toPath(),corrupt);
                yes(library.get(broken.id)==null,"fixture project should now be unreadable");
                Document recovery=new Document(root,"recovery.zip");
                Boolean recovered=run((done,fail)->jobs.exportDamagedProject(broken.id,recovery.target(),()->done.accept(true),fail),failure);
                yes(Boolean.TRUE.equals(recovered)&&failure.get()==null,"recovery export failed: "+failure.get());
                yes(recovery.modes.equals(List.of("wt")),"recovery export must open in wt");
                yes(Arrays.equals(recovery.bytes(),corrupt),"recovery export is not byte-exact (stale tail?)");

                // Failures before output must never open (and thus never truncate) the document.
                Document refusedBackup=new Document(root,"refused-backup.zip");
                failure.set(null);
                LibraryDocumentTruncationTest.<Integer>run((done,fail)->jobs.backup(refusedBackup.target(),done,fail),failure);
                yes(failure.get()!=null,"backup of a library with a damaged project must fail");
                yes(refusedBackup.untouched(),"failed backup touched the selected document");

                Document refusedExport=new Document(root,"refused-export.zip");
                failure.set(null);
                run((done,fail)->jobs.exportProject("00000000-0000-0000-0000-000000000000",refusedExport.target(),()->done.accept(true),fail),failure);
                yes(failure.get()!=null,"export of a missing project must fail");
                yes(refusedExport.untouched(),"failed export touched the selected document");

                Document refusedRecovery=new Document(root,"refused-recovery.zip");
                failure.set(null);
                run((done,fail)->jobs.exportDamagedProject(good.id,refusedRecovery.target(),()->done.accept(true),fail),failure);
                yes(failure.get()!=null,"recovery export of a healthy project must be refused");
                yes(refusedRecovery.untouched(),"refused recovery export touched the selected document");
            }
            try{LibraryDocumentStreams.document(mode->null).open();throw new AssertionError("null provider stream accepted");}
            catch(FileNotFoundException expected){}
            System.out.println("PASS library document writes truncate (backup, export, recovery) and failed jobs leave documents untouched");
        }finally{delete(root);}
    }
}

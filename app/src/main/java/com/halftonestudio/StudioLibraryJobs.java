package com.halftonestudio;

import java.io.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Serialized, asynchronous library operations, independent of Android UI widgets.
 * Archive and backup streams are acquired on the worker, never on the UI thread.
 * Callbacks are delivered by the supplied UI executor while this owner is active.
 */
final class StudioLibraryJobs implements AutoCloseable {
    interface InputSource { InputStream open() throws IOException; }
    interface OutputTarget { OutputStream open() throws IOException; }

    private final StudioProjectLibrary library;
    private final Executor main;
    // Keep slow file operations serialized, but never admit an unlimited backlog.
    // CallerRunsPolicy is forbidden: it could execute archive I/O on the UI thread.
    static final int MAX_QUEUED = 32;
    private final ThreadPoolExecutor work = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(MAX_QUEUED),
            r -> {Thread t=new Thread(r,"VinylLibraryIO");
                  t.setPriority(Thread.NORM_PRIORITY-1);return t;},
            new ThreadPoolExecutor.AbortPolicy());
    private final Consumer<Exception> diagnostics;
    private volatile boolean closed;

    StudioLibraryJobs(StudioProjectLibrary library,Executor callbackExecutor){
        this(library,callbackExecutor,ignored->{});
    }

    StudioLibraryJobs(StudioProjectLibrary library,Executor callbackExecutor,
                      Consumer<Exception> diagnostics){
        if(library==null||callbackExecutor==null||diagnostics==null)
            throw new IllegalArgumentException("Library, UI executor, and diagnostics required");
        this.library=library;
        this.main=callbackExecutor;
        this.diagnostics=diagnostics;
    }

    private void deliverFailure(Consumer<Exception> failed,Exception error){
        try{diagnostics.accept(error);}catch(RuntimeException ignored){/* never block UI callback */}
        if(closed)return;
        try{main.execute(()->{if(!closed)failed.accept(error);});}
        catch(RejectedExecutionException ignored){/* UI is shutting down */}
    }

    private <T> void submit(Callable<T> job,Consumer<T> done,Consumer<Exception> failed){
        if(closed){deliverFailure(failed,new RejectedExecutionException("Library is closing"));return;}
        try{
            work.execute(()->{
                T value=null;Exception error=null;
                try{
                    if(Thread.currentThread().isInterrupted())
                        throw new IOException("Library operation cancelled");
                    value=job.call();
                }
                catch(OutOfMemoryError oom){error=new IOException("Not enough memory for this library operation",oom);}
                catch(Exception ex){error=ex;}
                if(closed)return;
                final T result=value;final Exception failure=error;
                if(failure!=null){deliverFailure(failed,failure);return;}
                try{main.execute(()->{if(!closed)done.accept(result);});}
                catch(RejectedExecutionException ignored){/* UI is shutting down */}
            });
        }catch(RejectedExecutionException e){
            // Expose bounded-queue back pressure without running work on the UI.
            deliverFailure(failed,new RejectedExecutionException("Library queue full (32 pending); try again",e));
        }
    }

    void list(Consumer<List<StudioProjectLibrary.Entry>> done,Consumer<Exception> error){
        submit(library::list,done,error);
    }
    void rename(String id,String newName,Consumer<StudioProjectLibrary.Entry> done,Consumer<Exception> error){
        submit(()->library.rename(id,newName),done,error);
    }
    void delete(String id,Runnable done,Consumer<Exception> error){
        submit(()->{library.delete(id);return Boolean.TRUE;},ignored->done.run(),error);
    }
    void exportProject(String id,OutputTarget destination,Runnable done,Consumer<Exception> error){
        submit(()->{
            try(InputStream in=library.openArchive(id);OutputStream out=destination.open()){
                if(out==null)throw new IOException("Cannot open export destination");
                byte[] buffer=new byte[32*1024];int n;
                while((n=in.read(buffer))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new IOException("Project export interrupted");
                    out.write(buffer,0,n);
                }
                out.flush();
            }
            return Boolean.TRUE;
        },ignored->done.run(),error);
    }
    /** Stage a complete readable backup before touching the selected SAF document.
     * Generic document providers do not guarantee atomic replacement after open. */
    void backup(OutputTarget destination,Consumer<Integer> done,Consumer<Exception> error){
        submit(()->{
            StudioProjectBackup.preflight(library);
            File staged=library.newBackupTemp();
            try{
                int count;
                try(FileOutputStream raw=new FileOutputStream(staged);
                    BufferedOutputStream out=new BufferedOutputStream(raw)){
                    count=StudioProjectBackup.exportAll(library,out);
                    out.flush();raw.getFD().sync();
                }
                StudioProjectBackup.verifyStaged(staged,count);
                try(InputStream in=new BufferedInputStream(new FileInputStream(staged));
                    OutputStream out=destination.open()){
                    if(out==null)throw new IOException("Cannot open backup destination");
                    byte[] buffer=new byte[32*1024];int n;
                    while((n=in.read(buffer))!=-1){
                        if(Thread.currentThread().isInterrupted())throw new IOException("Library backup cancelled");
                        out.write(buffer,0,n);
                    }
                    out.flush();
                }
                return count;
            }finally{if(staged.exists()&&!staged.delete())staged.deleteOnExit();}
        },done,error);
    }
    void exportDamagedProject(String id,OutputTarget destination,Runnable done,Consumer<Exception> error){
        submit(()->{
            try(InputStream in=library.openDamagedArchiveForRecovery(id);
                OutputStream out=destination.open()){
                if(out==null)throw new IOException("Cannot open recovery export destination");
                byte[] buf=new byte[32*1024];int n;long copied=0;
                while((n=in.read(buf))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new IOException("Recovery export interrupted");
                    copied+=n;
                    if(copied>90_000_000L)throw new IOException("Damaged archive exceeds recovery export limit");
                    out.write(buf,0,n);
                }
                out.flush();
            }
            return Boolean.TRUE;
        },ignored->done.run(),error);
    }
    void restore(InputSource source,Consumer<Integer> done,Consumer<Exception> error){
        submit(()->{
            try(InputStream in=source.open()){
                if(in==null)throw new IOException("Cannot open library backup");
                return StudioProjectBackup.importAll(in,library);
            }
        },done,error);
    }
    int queuedOperations(){return work.getQueue().size();}
    /** Accepted library operations finish even when the screen is recreated.
     * Suppress callbacks to the obsolete UI but never interrupt a provider write. */
    @Override public void close(){closed=true;work.shutdown();}
}

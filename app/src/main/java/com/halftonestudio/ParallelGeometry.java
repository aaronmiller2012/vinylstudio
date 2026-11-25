package com.halftonestudio;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Build 555: shared work-stealing pool for independent geometry work (vector Boolean
 * union trees). Results are byte-identical to sequential execution because only the
 * scheduling changes, never the operation order inside a branch.
 *
 * Cancellation: render lanes cancel by interrupting their thread. Pool workers cannot
 * see that interrupt, so every invocation carries a token that {@link
 * CancellationCheckpoint#check()} and {@link StyleKit#checkCancelled()} also honor.
 * The calling thread polls its own interrupt and trips the token.
 */
final class ParallelGeometry {
    private ParallelGeometry() { }
    static final int PARALLELISM=Math.max(1,Math.min(6,Runtime.getRuntime().availableProcessors()-1));
    private static final ForkJoinPool POOL=new ForkJoinPool(PARALLELISM,pool->{
        ForkJoinWorkerThread t=ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
        t.setName("VinylGeometry-"+t.getPoolIndex());t.setDaemon(true);t.setPriority(Thread.NORM_PRIORITY);
        return t;
    },null,false);
    private static final ThreadLocal<AtomicBoolean> TOKEN=new ThreadLocal<>();

    /** True when the enclosing parallel invocation was cancelled. */
    static boolean cancelled(){AtomicBoolean t=TOKEN.get();return t!=null&&t.get();}
    /** Test hook: force the sequential path to prove both produce identical geometry. */
    static volatile boolean forceSequential=false;
    static boolean enabled(){return PARALLELISM>1&&!forceSequential;}

    /** A task whose body observes the invocation's cancellation token. */
    abstract static class Task<T> extends RecursiveTask<T> {
        final AtomicBoolean token;
        Task(AtomicBoolean token){this.token=token;}
        abstract T work();
        @Override protected final T compute(){
            AtomicBoolean prior=TOKEN.get();TOKEN.set(token);
            try{if(token.get())throw new CancellationException();return work();}
            finally{if(prior==null)TOKEN.remove();else TOKEN.set(prior);}
        }
    }

    /** Token for a new top-level invocation, or the enclosing one when already nested. */
    static AtomicBoolean token(){AtomicBoolean t=TOKEN.get();return t!=null?t:new AtomicBoolean();}

    /** Run a task tree, propagating the caller's interrupt as cancellation. */
    static <T> T invoke(Task<T> task){
        if(Thread.currentThread() instanceof ForkJoinWorkerThread&&((ForkJoinWorkerThread)Thread.currentThread()).getPool()==POOL)
            return task.invoke(); // nested: join the existing tree
        ForkJoinTask<T> running=POOL.submit(task);
        while(true){
            try{return running.get(25,TimeUnit.MILLISECONDS);}
            catch(TimeoutException waiting){
                if(Thread.currentThread().isInterrupted()||cancelled()){abandon(task,running);throw new CancellationException();}
            }catch(InterruptedException interrupted){
                abandon(task,running);Thread.currentThread().interrupt();throw new CancellationException();
            }catch(ExecutionException failed){
                Throwable cause=failed.getCause();
                if(cause instanceof RuntimeException)throw (RuntimeException)cause;
                if(cause instanceof Error)throw (Error)cause;
                throw new IllegalStateException(cause);
            }
        }
    }
    private static void abandon(Task<?> task,ForkJoinTask<?> running){
        task.token.set(true);
        // Workers stop at their next checkpoint; wait briefly so no stale work outlives the render.
        try{running.get(2,TimeUnit.SECONDS);}catch(Exception ignored){}
    }
}

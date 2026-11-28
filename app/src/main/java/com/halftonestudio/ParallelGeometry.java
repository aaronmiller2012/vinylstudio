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
    private static volatile boolean memoryConstrained=false;
    /** M7 P5 (measured on a 192 MB-heap tablet, docs/assessments/2026-11-26-m7-device-report.md):
     * peak memory is driven by how many colour sheets are in flight, not by thread count.
     * So the two kinds of parallelism have separate budgets:
     * <ul><li>within-sheet union trees ({@link #enabled()}): any heap above 128 MB that is
     * not a low-RAM device (Chainmail 6 in: 16.1 s / 61 MB sequential vs 12.2 s / 77 MB);</li>
     * <li>concurrent sheets ({@link #allowConcurrentSheets}): never on heaps up to 512 MB
     * (all-sheets-concurrent reached the full 192 MB heap even with 2 threads).</li></ul> */
    static final long UNION_PARALLEL_MIN_HEAP=128L*1024*1024, CONCURRENT_SHEETS_MIN_HEAP=512L*1024*1024;
    private static final int MAX_UNION_DEPTH=3; // == VectorBooleanEngine.PARALLEL_DEPTH
    private static volatile int unionDepth=MAX_UNION_DEPTH;
    /** Test/benchmark override of the union-tree depth (-1 = heap policy). */
    static volatile int unionDepthOverride=-1;
    static void configureMemoryBudget(boolean lowRam,long maxHeapBytes){
        memoryConstrained=lowRam||maxHeapBytes<=UNION_PARALLEL_MIN_HEAP;
        heapBytes=maxHeapBytes;
        // Concurrent union branches of ONE sheet also hold large intermediate geometry:
        // Fishnet at depth 3 (8 branches) reached the 192 MB tablet heap ceiling.
        unionDepth=maxHeapBytes<=256L*1024*1024?1:maxHeapBytes<=512L*1024*1024?2:MAX_UNION_DEPTH;
    }
    private static volatile long heapBytes=Long.MAX_VALUE;
    /** Union-tree fork depth; only scheduling changes, the tree and its operations do not. */
    static int unionDepth(){int o=unionDepthOverride;return o>=0?o:unionDepth;}
    /** Depth for one sheet of this physical width. On heaps up to 256 MB, artwork 8 in or wider
     * unions sequentially: at 10.8 in depth 1 peaked at 160-163 MB of 192 MB (Chainmail 34.8 s,
     * Fishnet 59.5 s) vs 102-105 MB sequential (49.5 s / 90.7 s), too little headroom. */
    static int unionDepth(float widthMm){
        int o=unionDepthOverride;if(o>=0)return o;
        if(heapBytes<=256L*1024*1024&&widthMm>=203.2f)return 0;
        return unionDepth;
    }
    static boolean enabled(){return PARALLELISM>1&&!forceSequential&&!memoryConstrained;}

    /** Bound independent sheet buffering on typical phone heaps. Preserve the
     * existing union-tree operation order; only sheet scheduling changes.
     * Every vector engine that processes colour sheets concurrently must ask this. */
    static boolean allowConcurrentSheets(int inputPoints,float widthMm,long heapBytes){
        if(heapBytes<=CONCURRENT_SHEETS_MIN_HEAP)return false; // one sheet at a time on phone heaps
        if(heapBytes<=768L*1024*1024 && (widthMm>=203.2f||inputPoints>=60000))return false;
        if(heapBytes<=1536L*1024*1024 && inputPoints>=160000)return false;
        return true;
    }

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
    /** Body over the half-open index range [from,to). Must only write disjoint outputs. */
    interface RangeBody{void run(int from,int to);}
    /** M7: deterministic data-parallel loop for independent rows/columns of ONE sheet
     * (results never depend on scheduling). Sequential when parallel geometry is disabled
     * (low-RAM/small heap), when the work is small, or when already inside the pool. */
    static void forRange(int n,long workPerItem,RangeBody body){
        int chunks=Math.min(PARALLELISM,Math.max(1,(int)Math.min(n,(long)n*workPerItem/250_000L)));
        if(!enabled()||chunks<=1||n<2||Thread.currentThread() instanceof ForkJoinWorkerThread){body.run(0,n);return;}
        final AtomicBoolean token=token();
        final int parts=chunks;
        invoke(new Task<Void>(token){
            @Override Void work(){
                java.util.ArrayList<Task<Void>> tasks=new java.util.ArrayList<>(parts);
                for(int c=0;c<parts;c++){
                    final int from=(int)((long)n*c/parts),to=(int)((long)n*(c+1)/parts);
                    tasks.add(new Task<Void>(token){@Override Void work(){body.run(from,to);return null;}});
                }
                invokeAll(tasks);
                return null;
            }
        });
    }
    private static void abandon(Task<?> task,ForkJoinTask<?> running){
        task.token.set(true);
        // Workers stop at their next checkpoint; wait briefly so no stale work outlives the render.
        try{running.get(2,TimeUnit.SECONDS);}catch(Exception ignored){}
    }
}

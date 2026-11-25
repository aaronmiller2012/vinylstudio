package com.halftonestudio;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Runs independent per-layer engine work on a small shared pool.
 *
 * Each standard halftone/line layer reads the same immutable source raster and
 * settings snapshot and writes only to its own Result.shapes/lineMetrics slot,
 * so layers can be generated concurrently with byte-identical geometry.
 *
 * Contract preserved from the sequential loop:
 *  - The first failing layer in layer order supplies the thrown exception.
 *  - Interrupting the calling render thread cancels (interrupts) every layer task.
 *  - Android-free and JVM-testable.
 */
final class LayerParallelism {
    private LayerParallelism() {}
    interface LayerTask { void run(int layer); }

    /** Helper threads besides the calling render thread. At most three helpers
     * (four layers in flight) keeps peak memory bounded on 4–8 GB phones. */
    static final int MAX_WORKERS=Math.max(0,Math.min(3,Runtime.getRuntime().availableProcessors()-1));
    private static final ThreadPoolExecutor POOL=new ThreadPoolExecutor(Math.max(1,MAX_WORKERS),Math.max(1,MAX_WORKERS),
        15L,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(16),r->{
            Thread t=new Thread(r,"VinylLayer");t.setDaemon(true);t.setPriority(Thread.NORM_PRIORITY);return t;},new ThreadPoolExecutor.CallerRunsPolicy());
    static { POOL.allowCoreThreadTimeOut(true); }
    /** Test hook: force the original sequential order. */
    static volatile boolean sequential=false;

    /** Batch independent specialty work, at most one helper. */
    static void runSpecialty(int count,long scratchPerLayer,LayerTask task){
        Runtime r=Runtime.getRuntime();long available=r.maxMemory()-(r.totalMemory()-r.freeMemory());
        int width=(!sequential&&MAX_WORKERS>0&&scratchPerLayer>0&&scratchPerLayer<=Math.min(24L*1024*1024,available/6))?2:1;
        for(int start=0;start<count;start+=width){
            int[] batch=new int[Math.min(width,count-start)];for(int j=0;j<batch.length;j++)batch[j]=start+j;
            run(batch,task);
        }
    }

    static void run(int[] layers,LayerTask task){
        if(layers.length==0)return;
        if(Thread.currentThread().isInterrupted())
            throw new CancellationException("Cancelled obsolete preview generation");
        if(sequential||MAX_WORKERS<1||layers.length==1){
            for(int layer:layers){
                if(Thread.currentThread().isInterrupted())throw new CancellationException("Cancelled obsolete preview generation");
                task.run(layer);
            }
            return;
        }
        // The caller computes the last layer itself instead of idling.
        List<Future<?>> futures=new ArrayList<>(layers.length-1);
        RuntimeException inlineFailure=null;Error inlineError=null;
        try{
            for(int i=0;i<layers.length-1;i++){final int layer=layers[i];futures.add(POOL.submit(()->task.run(layer)));}
            try{task.run(layers[layers.length-1]);}
            catch(RuntimeException e){inlineFailure=e;}
            catch(Error e){inlineError=e;}
            for(int i=0;i<futures.size();i++){
                try{futures.get(i).get();}
                catch(InterruptedException interrupted){
                    Thread.currentThread().interrupt();
                    throw new CancellationException("Cancelled obsolete preview generation");
                }catch(ExecutionException failed){
                    // Lower layer index wins, matching the sequential loop.
                    Throwable cause=failed.getCause();
                    if(cause instanceof RuntimeException)throw (RuntimeException)cause;
                    if(cause instanceof Error)throw (Error)cause;
                    throw new IllegalStateException(cause);
                }
            }
            if(inlineError!=null)throw inlineError;
            if(inlineFailure!=null)throw inlineFailure;
            if(Thread.currentThread().isInterrupted())
                throw new CancellationException("Cancelled obsolete preview generation");
        }finally{
            boolean cancelled=false;
            for(Future<?> f:futures)if(!f.isDone()){
                f.cancel(true);cancelled=true;
            }
            // ThreadPoolExecutor does not automatically remove cancelled
            // FutureTasks from its queue. Purge only on cancellation/failure,
            // never on a successful interactive frame.
            if(cancelled)POOL.purge();
        }
    }
}

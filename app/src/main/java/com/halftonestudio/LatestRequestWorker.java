package com.halftonestudio;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Dedicated one-slot, latest-request-wins background lane for photo palette work.
 * A canceled job must still check its generation token before publishing to the UI.
 */
final class LatestRequestWorker implements AutoCloseable {
    private final ThreadPoolExecutor worker;
    private Future<?> active;
    private boolean closed;
    LatestRequestWorker(String name) {
        worker=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(1),r->{
                Thread thread=new Thread(r,name);thread.setPriority(Thread.NORM_PRIORITY-1);
                return thread;
            },new ThreadPoolExecutor.AbortPolicy());
    }
    synchronized void submit(Runnable job) {
        if(closed)throw new RejectedExecutionException("Worker closed");
        cancel();
        active=worker.submit(job);
    }
    synchronized void cancel() {
        if(active!=null){active.cancel(true);active=null;}
        worker.getQueue().clear();
    }
    @Override public synchronized void close() {
        if(closed)return;
        closed=true;cancel();worker.shutdownNow();
    }
}

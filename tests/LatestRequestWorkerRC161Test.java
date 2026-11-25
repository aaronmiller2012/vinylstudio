package com.halftonestudio;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises the actual latest-only worker rather than checking a source string. */
public final class LatestRequestWorkerRC161Test {
    private static void check(boolean good,String message){if(!good)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        LatestRequestWorker worker=new LatestRequestWorker("RC161-test");
        CountDownLatch began=new CountDownLatch(1),release=new CountDownLatch(1);
        CountDownLatch lastDone=new CountDownLatch(1);
        AtomicInteger oldRuns=new AtomicInteger(),latestRuns=new AtomicInteger();
        try {
            worker.submit(()->{
                began.countDown();
                // Simulate a slow bitmap operation that ignores interrupts until
                // it completes, holding the worker while queued requests change.
                long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
                while(release.getCount()>0&&System.nanoTime()<end){
                    try{release.await(50,TimeUnit.MILLISECONDS);}
                    catch(InterruptedException ignored){/* intentionally noninterruptible */}
                }
            });
            check(began.await(3,TimeUnit.SECONDS),"first task never started");
            worker.submit(()->oldRuns.incrementAndGet());
            worker.submit(()->{latestRuns.incrementAndGet();lastDone.countDown();});
            release.countDown();
            check(lastDone.await(3,TimeUnit.SECONDS),"latest task did not execute");
            check(oldRuns.get()==0,"stale queued work ran");
            check(latestRuns.get()==1,"new task ran wrong number of times");
            worker.close();
            try{worker.submit(()->{});throw new AssertionError("closed worker accepted job");}
            catch(java.util.concurrent.RejectedExecutionException expected){}
        }finally{release.countDown();worker.close();}
        System.out.println("PASS RC161 latest-only palette scheduling and shutdown");
    }
}

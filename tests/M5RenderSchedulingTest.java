package com.halftonestudio;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * M5 lifecycle: deterministic race tests for the render lanes and the cut-preparation executor.
 * No sleeps decide pass/fail; every ordering is forced with latches.
 */
public final class M5RenderSchedulingTest {
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}

    /** A newer submission on a lane interrupts the running task and drops queued ones; only the
     *  newest result is published when each publication is gated by a generation token. */
    static void staleLaneResultsNeverPublish() throws Exception {
        try(StudioRenderController c=new StudioRenderController()){
            final AtomicInteger generation=new AtomicInteger(),published=new AtomicInteger(-1);
            final CountDownLatch firstRunning=new CountDownLatch(1),firstInterrupted=new CountDownLatch(1);
            final int g1=generation.incrementAndGet();
            c.submit(StudioRenderController.Lane.STANDARD,()->{
                firstRunning.countDown();
                try{Thread.sleep(30_000);}catch(InterruptedException e){firstInterrupted.countDown();
                    // A worker that ignores cancellation and finishes anyway must still be rejected.
                    if(generation.get()==g1)published.set(g1);}
            });
            check(firstRunning.await(5,TimeUnit.SECONDS),"first render never started");
            final int g2=generation.incrementAndGet();
            final int g3;
            // g2 is queued behind the running g1, then superseded by g3 before it starts.
            c.submit(StudioRenderController.Lane.STANDARD,()->{if(generation.get()==g2)published.set(g2);});
            g3=generation.incrementAndGet();
            final CountDownLatch done=new CountDownLatch(1);
            c.submit(StudioRenderController.Lane.STANDARD,()->{if(generation.get()==g3)published.set(g3);done.countDown();});
            check(firstInterrupted.await(5,TimeUnit.SECONDS),"running render was not interrupted");
            check(done.await(5,TimeUnit.SECONDS),"newest render never ran");
            check(published.get()==g3,"stale generation published: "+published.get());
        }
    }

    /** Lanes are independent: cancelling the PREVIEW lane never cancels a STANDARD render. */
    static void lanesAreIndependent() throws Exception {
        try(StudioRenderController c=new StudioRenderController()){
            final CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
            final AtomicInteger interrupted=new AtomicInteger();
            Future<?> standard=c.submit(StudioRenderController.Lane.STANDARD,()->{
                started.countDown();
                try{release.await();}catch(InterruptedException e){interrupted.incrementAndGet();}
            });
            check(started.await(5,TimeUnit.SECONDS),"standard never started");
            c.cancel(StudioRenderController.Lane.PREVIEW);
            c.cancel(StudioRenderController.Lane.SPECIALTY);
            check(interrupted.get()==0&&!standard.isDone(),"cancelling another lane interrupted the standard render");
            release.countDown();
            standard.get(5,TimeUnit.SECONDS);
        }
    }

    /**
     * Rapid slider releases while a cut-preparation stage is running and cannot reach a checkpoint
     * quickly. Each release cancels the previous job and submits a new one. Cancelled futures must
     * not keep occupying the bounded queue, or the newest stage is rejected and the status stays on
     * "Preparing cut-ready geometry" with no stage ever started.
     */
    static void cancelledCutPreparationDoesNotStarveQueue() throws Exception {
        try(StudioRenderController c=new StudioRenderController()){
            ExecutorService cut=c.cutPreparation();
            final CountDownLatch running=new CountDownLatch(1),release=new CountDownLatch(1);
            Future<?> busy=cut.submit(()->{
                running.countDown();
                while(true){try{release.await();return;}catch(InterruptedException ignoredUntilCheckpoint){}} // uncooperative stage
            });
            check(running.await(5,TimeUnit.SECONDS),"cut stage never started");
            Future<?> previous=busy;
            final AtomicInteger newestRan=new AtomicInteger();
            try{
            for(int release_i=0;release_i<8;release_i++){
                c.cancelCutPreparation(previous);
                final boolean last=release_i==7;
                try{previous=cut.submit(()->{if(last)newestRan.incrementAndGet();});}
                catch(RejectedExecutionException starved){
                    throw new AssertionError("release #"+release_i+" rejected: cancelled jobs still hold the queue");
                }
            }
            }finally{release.countDown();}
            previous.get(5,TimeUnit.SECONDS);
            check(newestRan.get()==1,"newest cut stage did not run");
        }
    }

    public static void main(String[] args) throws Exception {
        staleLaneResultsNeverPublish();
        lanesAreIndependent();
        cancelledCutPreparationDoesNotStarveQueue();
        System.out.println("PASS M5RenderSchedulingTest");
    }
}

package com.halftonestudio;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class StudioRenderControllerTest {
    private static void eq(boolean yes,String reason){if(!yes)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception{
        try(StudioRenderController r=new StudioRenderController()){
            CountDownLatch started=new CountDownLatch(1);
            CountDownLatch interruption=new CountDownLatch(1);
            Future<?> canceled=r.submit(StudioRenderController.Lane.PREVIEW,()->{
                started.countDown();
                try{Thread.sleep(20000);}catch(InterruptedException e){interruption.countDown();Thread.currentThread().interrupt();}
            });
            eq(started.await(3,TimeUnit.SECONDS),"preview lane starts");
            AtomicInteger sequence=new AtomicInteger();
            Future<?> last=r.submit(StudioRenderController.Lane.PREVIEW,()->sequence.incrementAndGet());
            last.get(4,TimeUnit.SECONDS);
            eq(canceled.isCancelled()&&interruption.await(3,TimeUnit.SECONDS),"superseded render interrupted");
            eq(sequence.get()==1,"replacement render executes exactly once");
            Future<?> standard=r.submit(StudioRenderController.Lane.STANDARD,()->sequence.addAndGet(10));
            Future<?> specialty=r.submit(StudioRenderController.Lane.SPECIALTY,()->sequence.addAndGet(100));
            standard.get(3,TimeUnit.SECONDS);specialty.get(3,TimeUnit.SECONDS);
            eq(sequence.get()==111,"standard and specialty lanes both run");
            // Worker exceptions must release the memory budget, allowing a
            // fresh user render even after an OOM in an earlier job.
            Future<?> failed=r.submit(StudioRenderController.Lane.STANDARD,()->{
                throw new OutOfMemoryError("synthetic render allocation failure");
            });
            try{failed.get(4,TimeUnit.SECONDS);throw new AssertionError("OOM task unexpectedly succeeded");}
            catch(ExecutionException expected){
                eq(expected.getCause() instanceof OutOfMemoryError,"failed task cause was lost");
            }
            Future<?> recovered=r.submit(StudioRenderController.Lane.STANDARD,()->sequence.addAndGet(1000));
            recovered.get(4,TimeUnit.SECONDS);
            eq(sequence.get()==1111,"renderer failed to recover after OOM");
            Future<?> pending=r.submit(StudioRenderController.Lane.PREVIEW,()->{
                try{Thread.sleep(20000);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}
            });
            r.cancelAll();
            eq(pending.isCancelled(),"cancel all lanes");
        }
        System.out.println("PASS RC137 render controller cancellation, OOM recovery, replacement, parallel lanes and cleanup");
    }
}

package com.halftonestudio;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Adversarial concurrency/cache contract for the audited render-speed merge. */
public final class V3RenderSpeedHardeningTest {
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        // Lowest layer ID must win regardless of completion order, including
        // a failure on the inline (last) layer.
        int[] layers={0,1,2,3};
        try{
            LayerParallelism.run(layers,li->{
                if(li==0)throw new IllegalArgumentException("layer 0 first");
                if(li==1)throw new IllegalStateException("layer 1 second");
                if(li==3)throw new IllegalStateException("inline last");
            });
            throw new AssertionError("Expected first layer failure");
        }catch(IllegalArgumentException e){
            require("layer 0 first".equals(e.getMessage()),"Failure order changed");
        }
        // Interrupt at entry must not enqueue any work.
        final boolean[] scheduled={false};
        Thread alreadyInterrupted=new Thread(()->{
            Thread.currentThread().interrupt();
            try{
                LayerParallelism.run(layers,li->{scheduled[0]=true;});
                throw new AssertionError("Interrupt ignored at entry");
            }catch(CancellationException expected){}
        });
        alreadyInterrupted.start();alreadyInterrupted.join(2500);
        require(!alreadyInterrupted.isAlive()&&!scheduled[0],"Pre-interrupt scheduling occurred");

        // Interrupt an active caller while child tasks are running; it must
        // stop promptly, rather than returning a late frame after cancellation.
        final CountDownLatch started=new CountDownLatch(1);
        final AtomicReference<Throwable> outcome=new AtomicReference<>();
        Thread active=new Thread(()->{
            try{
                LayerParallelism.run(layers,li->{
                    started.countDown();
                    for(int k=0;k<1200;k++){
                        if(Thread.currentThread().isInterrupted())throw new CancellationException();
                        try{Thread.sleep(4);}catch(InterruptedException e){
                            Thread.currentThread().interrupt();throw new CancellationException();
                        }
                    }
                });
                outcome.set(new AssertionError("Cancelled frame was published"));
            }catch(Throwable t){outcome.set(t);}
        });
        active.start();
        require(started.await(5,TimeUnit.SECONDS),"No parallel work started");
        active.interrupt();active.join(5000);
        require(!active.isAlive(),"Active render failed to stop");
        require(outcome.get() instanceof CancellationException,"Wrong interruption result: "+outcome.get());

        // Source-array identity, substrate, and explicit cache invalidation.
        int[] alpha={0x80800000};
        ScaleAwarePhotoSampler a=new ScaleAwarePhotoSampler(alpha,1,1,10,10,0xffffffff);
        ScaleAwarePhotoSampler b=new ScaleAwarePhotoSampler(alpha,1,1,10,10,0xff000000);
        float light=a.lightAt(5,5),dark=b.lightAt(5,5);
        require(light>dark+.4f,"Transparent photo background color was not respected");
        ScaleAwarePhotoSampler.clearLightCache();
        ScaleAwarePhotoSampler c=new ScaleAwarePhotoSampler(alpha,1,1,10,10,0xffffffff);
        require(Float.floatToIntBits(light)==Float.floatToIntBits(c.lightAt(5,5)),
                "Cache invalidation changed luminance");
        System.out.println("V3RenderSpeedHardeningTest PASS (priority, interruption, cache)");
    }
}

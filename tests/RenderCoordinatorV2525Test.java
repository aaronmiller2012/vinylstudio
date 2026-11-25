package com.halftonestudio;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
/** Ensures controller cancellation signals actual running work and all lanes close. */
public final class RenderCoordinatorV2525Test {
    public static void main(String[] unused) throws Exception {
        StudioRenderController controller=new StudioRenderController();
        CountDownLatch started=new CountDownLatch(1),finished=new CountDownLatch(1);
        AtomicBoolean canceled=new AtomicBoolean(false);
        try {
            controller.submit(StudioRenderController.Lane.STANDARD,()->{
                started.countDown();
                try {Thread.sleep(10000);} catch(InterruptedException exception) {
                    canceled.set(true);Thread.currentThread().interrupt();
                }
            });
            if(!started.await(2,TimeUnit.SECONDS))throw new AssertionError("Original render did not start");
            controller.submit(StudioRenderController.Lane.STANDARD,finished::countDown);
            if(!finished.await(2,TimeUnit.SECONDS))throw new AssertionError("New render blocked");
            if(!canceled.get())throw new AssertionError("Old render did not receive cancellation");
            CountDownLatch io=new CountDownLatch(1),diagnostics=new CountDownLatch(1),startup=new CountDownLatch(1);
            controller.io().execute(io::countDown);
            controller.diagnostics().execute(diagnostics::countDown);
            controller.startupIo().execute(startup::countDown);
            if(!io.await(2,TimeUnit.SECONDS)||!diagnostics.await(2,TimeUnit.SECONDS)||!startup.await(2,TimeUnit.SECONDS))
                throw new AssertionError("Owned scheduling lane is blocked");
        } finally {controller.close();}
        System.out.println("PASS render supersession, cancellation, and owned worker lanes");
    }
}

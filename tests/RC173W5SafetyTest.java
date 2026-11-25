package com.halftonestudio;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Executable adversarial tests; no Android SDK needed. */
public final class RC173W5SafetyTest {
    static void check(boolean yes,String label){if(!yes)throw new AssertionError(label);}
    static StyleKit.Result sheet(float[]... paths){
        StyleKit.Result result=new StyleKit.Result(100,100);
        StyleKit.Layer layer=new StyleKit.Layer("Safety sheet",0xff111111);
        result.layers.add(layer);
        for(float[] path:paths)layer.loops.add(path);
        return result;
    }
    static List<StyleCutGeometryPreflight.Warning> scan(float[]... paths){
        return StyleCutGeometryPreflight.inspect(sheet(paths),.6f,20);
    }
    static boolean hasReason(List<StyleCutGeometryPreflight.Warning> ws,String reason){
        for(StyleCutGeometryPreflight.Warning w:ws)if(w.reason.contains(reason))return true;
        return false;
    }
    public static void main(String[] ignored){
        float[] a=StyleKit.rect(2,10,18,26),b=StyleKit.rect(10,2,26,18);
        check(hasReason(scan(a,b),"Intersecting"),"Crossing contours escaped preflight");
        check(scan(StyleKit.rect(2,2,20,20),StyleKit.rect(30,2,48,20)).isEmpty(),
            "Independent safe-separated contours flagged");
        check(scan(StyleKit.rect(2,2,40,40),StyleKit.rect(12,12,30,30)).isEmpty(),
            "Legitimate wide nested hole was rejected");
        check(hasReason(scan(StyleKit.rect(2,2,20,20),StyleKit.rect(20,8,30,14)),"Touching"),
            "Touching loops must be reviewed");
        check(!scan(StyleKit.rect(2,2,20,20),StyleKit.rect(20,2,30,20)).isEmpty(),
            "Collinear overlapping boundary escaped");
        check(hasReason(scan(StyleKit.rect(2,2,20,20),StyleKit.rect(20.25f,2,38.25f,20)),"gap"),
            "0.25 mm gap escaped minimum clearance");
        AtomicInteger disposed=new AtomicInteger();
        ProjectLoadOwnership stale=new ProjectLoadOwnership();
        stale.own(disposed::incrementAndGet);
        stale.cancel();
        check(disposed.get()==0,"Canceled worker recycled a bitmap while worker was active");
        stale.workerFinished(true);
        check(disposed.get()==1,"Canceled worker did not release its resource");
        check(!stale.transferToUi(),"Canceled resource transferred to UI");
        ProjectLoadOwnership queued=new ProjectLoadOwnership();
        queued.own(disposed::incrementAndGet);
        queued.workerFinished(true);
        check(disposed.get()==1,"Queued UI callback recycled early");
        queued.cancel();
        queued.cancel();
        check(disposed.get()==2,"Dropped callback did not clean up once");
        ProjectLoadOwnership accepted=new ProjectLoadOwnership();
        accepted.own(disposed::incrementAndGet);
        accepted.workerFinished(true);
        check(accepted.transferToUi(),"Valid load could not transfer ownership");
        accepted.cancel();
        check(disposed.get()==2,"UI-owned bitmap was recycled by load cancellation");
        ProjectLoadOwnership rejected=new ProjectLoadOwnership();
        rejected.own(disposed::incrementAndGet);
        rejected.workerFinished(false);
        check(disposed.get()==3,"Failed UI dispatch leaked resource");
        try{rejected.own(disposed::incrementAndGet);throw new AssertionError("Canceled load retained resource");}
        catch(CancellationException expected){}
        check(disposed.get()==4,"Late resource was not disposed immediately");
        // Exercise the cancellation/worker-finish race on separate threads.
        ProjectLoadOwnership concurrent=new ProjectLoadOwnership();
        AtomicInteger freed=new AtomicInteger();
        CountDownLatch acceptedByWorker=new CountDownLatch(1),continueWorker=new CountDownLatch(1);
        Thread worker=new Thread(()->{
            concurrent.own(freed::incrementAndGet);
            acceptedByWorker.countDown();
            try{continueWorker.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}
            concurrent.workerFinished(true);
        });
        worker.start();
        try{check(acceptedByWorker.await(3,TimeUnit.SECONDS),"Worker did not acquire resource");}
        catch(InterruptedException e){throw new AssertionError(e);}
        concurrent.cancel();
        check(freed.get()==0,"Cancellation recycled a resource still in use by worker");
        continueWorker.countDown();
        try{worker.join(3000);}catch(InterruptedException e){throw new AssertionError(e);}
        check(!worker.isAlive()&&freed.get()==1,"Worker-finish did not release canceled resource once");
        System.out.println("PASS RC173-W5 crossing/overlap/tangent/nested/gap and background/UI ownership races");
    }
}

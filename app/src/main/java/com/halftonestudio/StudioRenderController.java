package com.halftonestudio;

import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Lifecycle-owned foreground render scheduling. Engine code never depends on Android.
 * The three lanes isolate standard geometry, specialty geometry, and raster previews.
 * Superseded work is interrupted and removed from its lane's queue; a caller must
 * additionally use its render-generation ID before publishing results to the UI.
 */
final class StudioRenderController implements AutoCloseable {
    enum Lane { STANDARD, SPECIALTY, PREVIEW }
    private final ThreadPoolExecutor[] workers = new ThreadPoolExecutor[Lane.values().length];
    private final Future<?>[] active = new Future<?>[Lane.values().length];
    private boolean closed;
    private final RenderWorkBudget memoryBudget=new RenderWorkBudget();
    // Never allow a background backlog to retain unlimited project bitmaps.
    // Rejected tasks are surfaced by StudioActivity.scheduleDiskJob rather
    // than silently dropped or run on the Android UI thread.
    private final ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,
        new java.util.concurrent.ArrayBlockingQueue<Runnable>(32),
        r->thread(r,"VinylDiskIO"),new ThreadPoolExecutor.AbortPolicy());
    // CPU-heavy cut geometry/preflight is isolated from durable project saves.
    // A second export must not create an unbounded queue retaining bitmaps.
    private final ThreadPoolExecutor cutPreparation=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,
        new java.util.concurrent.ArrayBlockingQueue<Runnable>(2),
        r->thread(r,"VinylCutPreflight"),new ThreadPoolExecutor.AbortPolicy());
    private final ExecutorService startup=Executors.newSingleThreadExecutor(r->thread(r,"VinylStartup"));
    private final ThreadPoolExecutor diagnostics=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,
        new java.util.concurrent.ArrayBlockingQueue<Runnable>(12), r->thread(r,"VinylDiagnostics"),
        new ThreadPoolExecutor.DiscardOldestPolicy());
    private static Thread thread(Runnable run,String name){
        Thread worker=new Thread(run,name);worker.setPriority(Thread.NORM_PRIORITY-1);return worker;
    }
    /** Foreground render lanes produce the artwork the user is waiting for.
     * On ART, Java priority 4 (NORM_PRIORITY-1) maps to nice 10
     * (ANDROID_PRIORITY_BACKGROUND), which gives a thread roughly a tenth of the
     * CPU share of the UI thread and steers it toward little cores during a
     * slider drag. Priority 5 maps to nice 0. Disk, preflight, startup and
     * diagnostics work keeps the lower priority. */
    private static Thread renderThread(Runnable run,String name){
        Thread worker=new Thread(run,name);worker.setPriority(Thread.NORM_PRIORITY);return worker;
    }
    ExecutorService io(){return io;}
    ExecutorService cutPreparation(){return cutPreparation;}
    /** M5: cancel a cut-preparation job AND free its queue slot. A cancelled future otherwise stays in
     * the bounded 2-slot queue until the running stage ends, so rapid slider releases behind a long
     * stage get RejectedExecutionException and the newest cut-ready stage never starts. */
    void cancelCutPreparation(Future<?> job){
        if(job==null)return;
        job.cancel(true);
        if(job instanceof Runnable)cutPreparation.remove((Runnable)job);
    }
    ExecutorService startupIo(){return startup;}
    ExecutorService diagnostics(){return diagnostics;}

    StudioRenderController() {
        for (int i=0; i<workers.length; i++) {
            final Lane lane=Lane.values()[i];
            workers[i] = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(),
                    r->renderThread(r,"VinylRender-"+lane.name()));
        }
    }

    synchronized Future<?> submit(Lane lane, Runnable task) {
        if (closed) return java.util.concurrent.CompletableFuture.completedFuture(null);
        int i=lane.ordinal();
        cancelLocked(i);
        // Future cancellation is cooperative; existing generation guards also
        // prevent stale results from replacing newly generated artwork.
        Future<?> future=workers[i].submit(()->memoryBudget.run(lane,task));
        active[i]=future;
        return future;
    }

    synchronized boolean hasPending(Lane lane) {
        Future<?> future=active[lane.ordinal()];
        return future!=null && !future.isDone();
    }

    synchronized void cancel(Lane lane) { cancelLocked(lane.ordinal()); }

    private void cancelLocked(int i) {
        Future<?> task=active[i];
        if(task!=null) task.cancel(true);
        active[i]=null;
        workers[i].getQueue().clear();
    }

    synchronized void cancelAll() {
        for (int i=0; i<workers.length; i++) cancelLocked(i);
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed=true;
        cancelAll();
        for (ThreadPoolExecutor executor:workers) executor.shutdownNow();
        // Do not discard an export/project-library write already accepted by IO.
        // Its completion callback is lifecycle-gated; these queued writes may
        // finish after their Activity has been destroyed.
        io.shutdown();cutPreparation.shutdownNow();startup.shutdownNow();diagnostics.shutdownNow();
    }
}

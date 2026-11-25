package com.halftonestudio;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Process-scoped serial lane for local photo saves/clear/restore, not render tasks.
 * It is intentionally NOT shut down by Activity.onDestroy(): a queued edit must
 * finish before a recreated Activity reads the same files. Queue saturation
 * explicitly rejects work; a process kill can still interrupt a pending save.
 * Existing files remain intact until atomic replacement completes.
 */
final class PhotoPersistenceQueue {
    private static final ThreadPoolExecutor DISK=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<Runnable>(8),r->{
            Thread t=new Thread(r,"VinylPhotoPersistence");
            t.setPriority(Thread.NORM_PRIORITY-1);t.setDaemon(true);return t;
        },new ThreadPoolExecutor.AbortPolicy());
    private PhotoPersistenceQueue(){}
    static void execute(Runnable work) throws RejectedExecutionException {
        if(work==null)throw new IllegalArgumentException("Missing persistence task");
        DISK.execute(work);
    }
}

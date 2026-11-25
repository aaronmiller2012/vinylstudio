package com.halftonestudio;

import java.util.concurrent.CancellationException;
/** 64-sample cooperative cancellation. Per-thread counter, no per-sample allocations. */
final class CancellationCheckpoint {
    private CancellationCheckpoint() {}
    private static final ThreadLocal<int[]> TICKS=ThreadLocal.withInitial(()->new int[1]);
    static boolean shouldCancel(){
        int[] count=TICKS.get();
        return ((++count[0]&63)==0)&&(Thread.currentThread().isInterrupted()||ParallelGeometry.cancelled());
    }
    static void check(){if(Thread.currentThread().isInterrupted()||ParallelGeometry.cancelled())throw new CancellationException();}
}

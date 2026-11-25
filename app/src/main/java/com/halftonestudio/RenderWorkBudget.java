package com.halftonestudio;

import java.util.concurrent.Semaphore;

/** Limits simultaneous peak allocations without changing any render engine.
 * Heavy geometry consumes two out of three slots. A short bitmap-layer preview
 * can still run alongside one heavy render; two heavy renders cannot overlap.
 * Acquisition responds to Future.cancel(true) and always releases its slots.
 */
final class RenderWorkBudget {
    private final Semaphore slots=new Semaphore(3,false);
    private static int cost(StudioRenderController.Lane lane){
        return lane==StudioRenderController.Lane.PREVIEW?1:2;
    }
    void run(StudioRenderController.Lane lane,Runnable task){
        final int permits=cost(lane);
        boolean acquired=false;
        try{
            slots.acquire(permits);
            acquired=true;
            if(!Thread.currentThread().isInterrupted())task.run();
        }catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
        }finally{
            if(acquired)slots.release(permits);
        }
    }
    int availableSlots(){return slots.availablePermits();}
}

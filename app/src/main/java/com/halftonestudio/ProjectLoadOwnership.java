package com.halftonestudio;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * Owns intermediate resources until a background project load is accepted by
 * its UI. The Activity may be destroyed between Handler.post and callback.
 * Cancellation does not recycle a bitmap while the worker is still decoding or
 * inspecting it: cleanup occurs on worker completion or after UI rejection.
 * No Android dependency, so the race is testable on the host JVM.
 */
final class ProjectLoadOwnership {
    private final ArrayList<Runnable> disposers=new ArrayList<>();
    private boolean cancelled,workerFinished,transferred,discarded;

    synchronized boolean isCancelled(){return cancelled;}

    synchronized void own(Runnable cleanup){
        if(cleanup==null)throw new IllegalArgumentException("Missing load cleanup");
        if(cancelled||transferred||discarded){
            cleanup.run();
            throw new CancellationException("Project load no longer owns its resources");
        }
        disposers.add(cleanup);
    }

    synchronized void cancel(){
        cancelled=true;
        if(workerFinished)dispose();
    }

    /** Called in a background finally block whether postUi succeeded or not. */
    synchronized void workerFinished(boolean posted){
        workerFinished=true;
        if(!posted||cancelled)dispose();
    }

    /** Called only on the UI thread, after accepting the document for display. */
    synchronized boolean transferToUi(){
        if(cancelled||discarded)return false;
        transferred=true;
        disposers.clear();
        return true;
    }

    synchronized void discard(){
        cancelled=true;
        // Do not recycle a bitmap still being decoded on another thread.
        if(workerFinished)dispose();
    }

    private void dispose(){
        if(discarded||transferred)return;
        discarded=true;
        List<Runnable> cleanups=new ArrayList<>(disposers);
        disposers.clear();
        for(int i=cleanups.size()-1;i>=0;i--){
            try{cleanups.get(i).run();}catch(RuntimeException ignored){
                // One corrupt temporary resource must not prevent the rest
                // from being released. No user-owned bitmap is in this list.
            }
        }
    }
}

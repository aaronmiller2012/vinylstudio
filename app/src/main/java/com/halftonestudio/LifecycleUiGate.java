package com.halftonestudio;
/** Drop both queued and future UI callbacks after Activity destruction. */
final class LifecycleUiGate {
    interface Poster { boolean post(Runnable task); }
    private volatile boolean closed;
    boolean dispatch(Poster poster,Runnable callback){
        if(closed)return false;
        return poster.post(()->{if(!closed)callback.run();});
    }
    void close(){closed=true;}
}

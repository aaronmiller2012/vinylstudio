package com.halftonestudio;

import java.util.ArrayDeque;

/** Main-thread-owned history; conservatively account for UTF-16 plus per-entry overhead. */
final class BoundedEditHistory {
    private final ArrayDeque<String> items=new ArrayDeque<>();
    private final int maxEntries;
    private final long maxBytes;
    private long bytes;
    BoundedEditHistory(int maxEntries,long maxBytes){this.maxEntries=maxEntries;this.maxBytes=maxBytes;}
    private static long cost(String value){return 64L+2L*value.length();}
    void push(String value){
        long size=cost(value);
        // An oversized edit must not leave older undo states that skip this edit.
        if(size>maxBytes){clear();return;}
        items.push(value);bytes+=size;
        while(items.size()>maxEntries||bytes>maxBytes)bytes-=cost(items.removeLast());
    }
    String pop(){String value=items.pop();bytes-=cost(value);return value;}
    String peek(){return items.peek();}
    boolean isEmpty(){return items.isEmpty();}
    int size(){return items.size();}
    long bytes(){return bytes;}
    void clear(){items.clear();bytes=0;}
}

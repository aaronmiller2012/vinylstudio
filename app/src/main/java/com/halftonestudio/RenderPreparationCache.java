package com.halftonestudio;

import java.util.LinkedHashMap;
import java.util.Map;

/** Thread-safe LRU for immutable prepared image snapshots, with optional byte ceiling.
 * Dropping an entry removes ownership only; never recycle a bitmap in use by a renderer.
 */
final class RenderPreparationCache<K,V> {
    interface Weigher<V> {long bytes(V item);}
    private final int capacity;
    private final long maxBytes;
    private final Weigher<V> weigher;
    private final LinkedHashMap<K,V> values=new LinkedHashMap<>(4,.75f,true);
    private long retainedBytes;
    RenderPreparationCache(int capacity){this(capacity,Long.MAX_VALUE,null);}
    RenderPreparationCache(int capacity,long maxBytes,Weigher<V> weigher){
        if(capacity<1||maxBytes<1)throw new IllegalArgumentException("Invalid cache budget");
        this.capacity=capacity;this.maxBytes=maxBytes;this.weigher=weigher;
    }
    private long weight(V value){return weigher==null?0L:Math.max(0L,weigher.bytes(value));}
    synchronized V get(K key){return values.get(key);}
    synchronized void put(K key,V value){
        if(key==null||value==null)throw new IllegalArgumentException("null cache entries");
        V previous=values.remove(key);
        if(previous!=null)retainedBytes-=weight(previous);
        long incoming=weight(value);
        // Oversized snapshots remain usable by their caller but are not retained.
        if(incoming>maxBytes)return;
        values.put(key,value);retainedBytes+=incoming;
        while(values.size()>capacity||retainedBytes>maxBytes){
            Map.Entry<K,V> eldest=values.entrySet().iterator().next();
            retainedBytes-=weight(eldest.getValue());values.remove(eldest.getKey());
        }
    }
    synchronized void clear(){values.clear();retainedBytes=0;}
    synchronized int size(){return values.size();}
    synchronized long retainedBytes(){return retainedBytes;}
}

package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Supplier;

/** Exact, bounded, activity-owned cache of pure float-image filters.
 * Both input and output are copied: engines may normalize/modify their arrays.
 * Content equality (not a hash, identity or approximate checksum) is required.
 * No cache lock is held while executing a filter. An invalidation epoch blocks
 * an obsolete computation from repopulating an explicitly cleared cache. */
public final class RenderImageFieldCache {
    private static final class Entry {
        final int w,h,kind,bits;
        final float[] in,out;
        Entry(float[] in,float[] out,int w,int h,int kind,int bits){
            this.in=in.clone();this.out=out.clone();this.w=w;this.h=h;this.kind=kind;this.bits=bits;
        }
        long bytes(){return 8L*in.length;}
        boolean matches(float[] a,int w,int h,int kind,int bits){
            return this.w==w&&this.h==h&&this.kind==kind&&this.bits==bits&&Arrays.equals(in,a);
        }
    }
    private final long maxBytes;
    private final int maxEntries;
    private final ArrayList<Entry> entries=new ArrayList<>();
    private long bytes,epoch,hits,misses;
    public RenderImageFieldCache(int maxEntries,long maxBytes){
        if(maxEntries<1||maxBytes<0)throw new IllegalArgumentException("Invalid cache budget");
        this.maxEntries=maxEntries;this.maxBytes=maxBytes;
    }
    public float[] compute(float[] input,int w,int h,int kind,int bits,Supplier<float[]> work){
        StyleKit.checkCancelled();
        if(8L*input.length>maxBytes)return work.get();
        final long generation;
        synchronized(this){
            generation=epoch;
            for(int i=entries.size()-1;i>=0;i--){
                Entry e=entries.get(i);
                if(e.matches(input,w,h,kind,bits)){
                    entries.remove(i);entries.add(e);hits++;
                    return e.out.clone();
                }
            }
            misses++;
        }
        float[] result=work.get();
        StyleKit.checkCancelled();
        if(result.length!=input.length)return result;
        synchronized(this){
            if(generation!=epoch)return result;
            long size=8L*input.length;
            while(!entries.isEmpty()&&(entries.size()>=maxEntries||bytes+size>maxBytes))
                bytes-=entries.remove(0).bytes();
            Entry e=new Entry(input,result,w,h,kind,bits);
            entries.add(e);bytes+=e.bytes();
        }
        return result;
    }
    public synchronized void clear(){entries.clear();bytes=0;epoch++;}
    synchronized long hitCount(){return hits;}
    synchronized long missCount(){return misses;}
    synchronized long retainedBytes(){return bytes;}
}

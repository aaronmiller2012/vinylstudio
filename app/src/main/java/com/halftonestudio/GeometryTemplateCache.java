package com.halftonestudio;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Supplier;
/** Bounded immutable-by-copy cache of photo-independent polygon templates. */
final class GeometryTemplateCache {
    private static final int MAX_BYTES=2*1024*1024, MAX_ENTRIES=6;
    private static final LinkedHashMap<String,Entry> cache=new LinkedHashMap<>(8,.75f,true);
    private static int bytes; private static long epoch;
    private GeometryTemplateCache(){}
    private static final class Entry {final List<float[]> data;final int size;Entry(List<float[]> d,int n){data=d;size=n;}}
    static String key(String type,int pattern,float w,float h,float unit){return type+":"+pattern+":"+Float.floatToIntBits(w)+":"+Float.floatToIntBits(h)+":"+Float.floatToIntBits(unit);}
    static List<float[]> get(String key,Supplier<List<float[]>> builder){
        Entry hit;long ticket;synchronized(cache){hit=cache.get(key);ticket=epoch;}
        if(hit!=null)return copy(hit.data);
        List<float[]> fresh=builder.get();long size=0;for(float[] a:fresh)size+=32L+a.length*4L;
        if(size>MAX_BYTES)return fresh;
        Entry entry=new Entry(copy(fresh),(int)size);
        synchronized(cache){if(ticket==epoch){Entry prev=cache.put(key,entry);if(prev!=null)bytes-=prev.size;bytes+=entry.size;
            while(bytes>MAX_BYTES||cache.size()>MAX_ENTRIES){String first=cache.keySet().iterator().next();bytes-=cache.remove(first).size;}}}
        return fresh;
    }
    private static List<float[]> copy(List<float[]> input){ArrayList<float[]> out=new ArrayList<>(input.size());for(float[] a:input){CancellationCheckpoint.check();out.add(a.clone());}return out;}
    static void clear(){synchronized(cache){cache.clear();bytes=0;epoch++;}}
    static int retainedBytes(){synchronized(cache){return bytes;}}
}

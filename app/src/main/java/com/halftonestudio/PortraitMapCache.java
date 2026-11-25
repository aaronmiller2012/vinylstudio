package com.halftonestudio;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

/** Two immutable maps; hash and build off-lock. Invalidated work cannot refill the cache. */
final class PortraitMapCache {
    private final Object lock = new Object();
    private long epoch, minimumRevision;
    private final LinkedHashMap<Key, PortraitStructureMap> entries = new LinkedHashMap<Key, PortraitStructureMap>(4,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key,PortraitStructureMap> e){return size()>2;}
    };
    private static final class Key {
        final long revision; final int width,height; final byte[] digest;
        Key(long revision,int width,int height,int[] pixels){
            this.revision=revision;this.width=width;this.height=height;
            try {
                MessageDigest md=MessageDigest.getInstance("SHA-256");
                byte[] buffer=new byte[4096];int used=0;
                for(int i=0;i<pixels.length;i++){
                    if((i&16383)==0)checkCancelled();
                    int pixel=pixels[i];buffer[used++]=(byte)(pixel>>>24);buffer[used++]=(byte)(pixel>>>16);
                    buffer[used++]=(byte)(pixel>>>8);buffer[used++]=(byte)pixel;
                    if(used==buffer.length){md.update(buffer);used=0;}
                }
                md.update(buffer,0,used);digest=md.digest();
            }catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 unavailable",e);}
        }
        @Override public int hashCode(){return 31*(31*(31*Long.hashCode(revision)+width)+height)+Arrays.hashCode(digest);}
        @Override public boolean equals(Object obj){if(!(obj instanceof Key))return false;Key k=(Key)obj;
            return revision==k.revision&&width==k.width&&height==k.height&&Arrays.equals(digest,k.digest);}
    }
    private static void checkCancelled(){if(Thread.currentThread().isInterrupted())throw new CancellationException();}
    PortraitStructureMap get(long revision,int[] pixels,int w,int h){return get(revision,pixels,w,h,()->new PortraitStructureMap(pixels,w,h));}
    // Supplier overload enables deterministic concurrency testing without Android or sleeps.
    PortraitStructureMap get(long revision,int[] pixels,int w,int h,Supplier<PortraitStructureMap> build){
        final long ticket;
        synchronized(lock){ticket=epoch;}
        final Key key=new Key(revision,w,h,pixels);
        synchronized(lock){if(ticket==epoch&&revision>=minimumRevision){PortraitStructureMap hit=entries.get(key);if(hit!=null)return hit;}}
        checkCancelled();PortraitStructureMap result=build.get();checkCancelled();
        synchronized(lock){
            if(ticket!=epoch||revision<minimumRevision)return result;
            PortraitStructureMap hit=entries.get(key);if(hit!=null)return hit;
            entries.put(key,result);return result;
        }
    }
    void photoChanged(long revision){synchronized(lock){minimumRevision=Math.max(minimumRevision,revision);epoch++;entries.clear();}}
    void clear(){synchronized(lock){epoch++;entries.clear();}}
}

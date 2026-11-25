package com.halftonestudio;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class V3Stability550Test {
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    static void portrait() throws Exception {
        int[] a=new int[16];Arrays.fill(a,0xff222222);
        int[] b=a.clone();b[0]++;b[1]-=31;
        check(Arrays.hashCode(a)==Arrays.hashCode(b),"collision fixture");
        PortraitMapCache cache=new PortraitMapCache();
        PortraitStructureMap first=cache.get(1,a,4,4);
        check(first==cache.get(1,a.clone(),4,4),"same content hit");
        check(first!=cache.get(1,b,4,4),"old 32-bit hash collision must not alias");
        check(cache.get(1,a,4,4)==cache.get(1,a,4,4,()->new PortraitStructureMap(a,4,4)),"same key");
        int[] six=new int[24];check(cache.get(1,six,4,6)!=cache.get(1,six,6,4),"dimensions separate");
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try {
            CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
            cache.clear();
            Future<PortraitStructureMap> old=workers.submit(()->cache.get(1,a,4,4,()->{
                started.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("release timeout");}
                catch(InterruptedException ex){throw new CancellationException();}return new PortraitStructureMap(a,4,4);
            }));
            check(started.await(5,TimeUnit.SECONDS),"build started");
            // These calls must finish before the intentionally blocked constructor is released.
            workers.submit(()->{cache.clear();cache.photoChanged(2);}).get(2,TimeUnit.SECONDS);
            PortraitStructureMap next=cache.get(2,a,4,4);
            release.countDown();PortraitStructureMap stale=old.get(5,TimeUnit.SECONDS);
            check(cache.get(2,a,4,4)==next,"latest portrait remains cached");
            check(cache.get(1,a,4,4)!=stale,"old work must not refill after invalidation");
            CountDownLatch startTrim=new CountDownLatch(1),endTrim=new CountDownLatch(1);
            cache.clear();
            Future<PortraitStructureMap> trimmed=workers.submit(()->cache.get(2,a,4,4,()->{
                startTrim.countDown();try{if(!endTrim.await(5,TimeUnit.SECONDS))throw new AssertionError("trim release timeout");}
                catch(InterruptedException ex){throw new CancellationException();}return new PortraitStructureMap(a,4,4);
            }));
            check(startTrim.await(5,TimeUnit.SECONDS),"trim started");workers.submit(cache::clear).get(2,TimeUnit.SECONDS);
            endTrim.countDown();check(cache.get(2,a,4,4)!=trimmed.get(5,TimeUnit.SECONDS),"in-flight map cannot repopulate trimmed cache");
        }finally{workers.shutdownNow();}
        System.out.println("PASS portrait cache hash collision, exact dimensions, concurrent trim/import and stale builds");
    }
    static void history(){
        BoundedEditHistory h=new BoundedEditHistory(3,230);
        h.push("a");h.push("b");h.push("c");h.push("d");
        check(h.size()==3&&h.bytes()<=230,"count and memory bound");
        check(h.pop().equals("d")&&h.pop().equals("c")&&h.pop().equals("b")&&h.bytes()==0,"LIFO accounting");
        h.push("a");h.push("b".repeat(80));check(h.size()==1&&h.bytes()<=230,"byte eviction");
        h.push("x".repeat(300));check(h.isEmpty()&&h.bytes()==0,"oversize no skipped history");
        h.push("new");h.clear();check(h.bytes()==0,"clear");
        System.out.println("PASS bounded edit history capacity, byte eviction and accounting");
    }
    static void lifecycle(){
        LifecycleEditorSnapshot cache=new LifecycleEditorSnapshot();AtomicInteger editor=new AtomicInteger(),draft=new AtomicInteger();
        LifecycleEditorSnapshot.Value paused=cache.capture(1,1,()->"edit"+editor.incrementAndGet(),()->"draft"+draft.incrementAndGet());
        check(cache.capture(1,1,()->"edit"+editor.incrementAndGet(),()->"draft"+draft.incrementAndGet())==paused,"save-state reuses pause");
        check(cache.capture(1,1,()->"edit"+editor.incrementAndGet(),()->"draft"+draft.incrementAndGet())==paused,"stop reuses pause");
        check(editor.get()==1&&draft.get()==1,"one serialization");
        check(cache.capture(2,1,()->"new",()->"draft")!=paused,"revision invalidation");
        cache.invalidate();check(cache.capture(2,1,()->"other",()->"newDraft").draft.equals("newDraft"),"callback invalidation");
        check(cache.capture(2,2,()->"photo",()->"newDraft").editor.equals("photo"),"photo invalidation");
        System.out.println("PASS lifecycle pause/save/stop serialization reuse and invalidation");
    }
    static void registration() throws Exception {
        TessellationEngine.Options opts=new TessellationEngine.Options();opts.layerCount=2;opts.registration=false;
        ArrayList<TessellationEngine.Tile> tiles=new ArrayList<>();tiles.add(new TessellationEngine.Tile(new float[]{10,10,20,10,20,20},0,.5f));
        TessellationEngine.Result old=new TessellationEngine.Result(100,100,opts,tiles);
        String before=old.svg(0);
        float[][] marks={{-10,-10},{160,-10},{-10,160},{160,160}};
        TessellationEngine.Result next=old.withRegistration(marks,true,RegistrationMarks.SQUARE,3,1);
        marks[0][0]=99;
        check(old.options!=next.options&&!old.options.registration&&old.options.marks==null,"old options immutable");
        check(next.options.registration&&next.options.markSizeMm==3&&next.options.markStyle==RegistrationMarks.SQUARE&&next.options.marks[0][0]==-10,"full mark revision and defensive copy");
        check(old.tiles==next.tiles&&old.counts==next.counts&&old.nonempty==next.nonempty,"geometry shared, no recompute");
        check(before.equals(old.svg(0)),"old SVG unaffected");
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try{
            Future<?> copying=worker.submit(()->{for(int i=0;i<2000;i++){TessellationEngine.Options copy=old.options.copy();check(!copy.registration&&copy.marks==null&&copy.markSizeMm==opts.markSizeMm,"export received mixed options");}});
            TessellationEngine.Result active=next;for(int i=0;i<2000;i++)active=active.withRegistration(null,(i&1)==0,RegistrationMarks.CROSS,4,2);
            copying.get(5,TimeUnit.SECONDS);
        }finally{worker.shutdownNow();}
        System.out.println("PASS registration result replacement, path preservation, concurrent export snapshot");
    }
    public static void main(String[] args)throws Exception{portrait();history();lifecycle();registration();}
}

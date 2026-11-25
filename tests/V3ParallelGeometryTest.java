package com.halftonestudio;

import java.util.*;
import java.util.concurrent.*;

/** Build 555: parallel vector Boolean geometry is identical to sequential and cancellable. */
public final class V3ParallelGeometryTest {
    static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    static ArrayList<float[]> sheet(int motifs,long seed){
        VectorBooleanEngine.Session s=VectorBooleanEngine.begin(2,.254f);
        s.setClip(Collections.singletonList(new float[]{0,0,300,0,300,300,0,300}));
        SplittableRandom r=new SplittableRandom(seed);
        for(int i=0;i<motifs;i++){
            float x=(float)r.nextDouble(5,295),y=(float)r.nextDouble(5,295),rx=(float)r.nextDouble(1.5,4),ry=(float)r.nextDouble(1.5,4);
            float a=(float)r.nextDouble(0,3.1);
            s.add(1,VectorBooleanEngine.ellipse(x,y,rx,ry,a,s.tolerance),VectorBooleanEngine.ellipse(x,y,rx*.55f,ry*.55f,a,s.tolerance));
        }
        return s.finish(1,300,300);
    }
    static boolean same(List<float[]> a,List<float[]> b){
        if(a.size()!=b.size())return false;
        for(int i=0;i<a.size();i++)if(!Arrays.equals(a.get(i),b.get(i)))return false;
        return true;
    }
    public static void main(String[] args) throws Exception {
        VectorBooleanEngine.install(new JtsVectorBooleanBackend());
        ParallelGeometry.forceSequential=true;
        ArrayList<float[]> sequential=sheet(1500,7);
        ParallelGeometry.forceSequential=false;
        ArrayList<float[]> parallel=sheet(1500,7);
        check(same(sequential,parallel),"parallel union must produce identical contours");
        // Full style through the per-sheet parallel finish.
        int[] px=V3AllStyleExportMatrixTest.portrait(90,120);
        VinylStyleSuite.Config c=new VinylStyleSuite.Config();
        c.style=VinylStyleSuite.CHAINMAIL;c.widthMm=152.4f;c.detail=50;c.colorCount=5;c.minimumMm=.254f;c.draft=false;
        ParallelGeometry.forceSequential=true;StyleKit.Result a=VinylStyleSuite.generate(px,90,120,c);
        ParallelGeometry.forceSequential=false;StyleKit.Result b=VinylStyleSuite.generate(px,90,120,c);
        for(int i=0;i<a.layers.size();i++)check(same(a.layers.get(i).loops,b.layers.get(i).loops),"sheet "+(i+1)+" differs between sequential and parallel");
        // Interrupting the render thread cancels pool workers promptly.
        ExecutorService lane=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"test-render-lane");t.setDaemon(true);return t;});
        Future<?> job=lane.submit(()->sheet(15000,11)); // within the 2.5M input-point budget, runs for seconds
        Thread.sleep(400);
        if(job.isDone()){job.get();throw new AssertionError("cancellation test needs a render still running at cancel time");}
        long start=System.nanoTime();
        job.cancel(true);
        lane.shutdown();
        check(lane.awaitTermination(5,TimeUnit.SECONDS),"cancelled render lane must stop within 5 s");
        long ms=(System.nanoTime()-start)/1_000_000;
        // Workers released: a fresh parallel render completes normally right after.
        long t=System.nanoTime();check(!sheet(600,3).isEmpty(),"pool unusable after cancellation");
        check((System.nanoTime()-t)/1_000_000<20_000,"pool still busy with cancelled work");
        System.out.println("PASS 555 parallel geometry: identical to sequential (parallelism "+ParallelGeometry.PARALLELISM+"), cancelled in "+ms+" ms");
    }
}

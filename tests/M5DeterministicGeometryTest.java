package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import org.locationtech.jts.geom.Geometry;

/**
 * M5 performance/determinism behaviour test on real production engine output.
 *  1. The conditioned-only {@code removeMicroFragmentsLocal} is geometrically equal to the shared
 *     {@code removeMicroFragments} (including nested islands inside filled holes).
 *  2. A conditioned Prismatic render through the local route has the same loops/area as the
 *     shared route (a wrapper backend forces the old whole-sheet re-union).
 *  3. Geometry never depends on wall clock or scheduling: identical hash on repeat, sequential vs
 *     parallel, and with an artificially slow backend; TSP 2-opt and cut preparation are
 *     workload-bounded, so a starved (stalled/hogged) environment gives the same tour/sheets.
 */
public final class M5DeterministicGeometryTest {
    static volatile boolean stop;
    static void require(boolean ok,String m){if(!ok)throw new AssertionError(m);}
    static long hash(StyleKit.Result r){
        long h=1125899906842597L;
        for(StyleKit.Layer l:r.layers){
            h=h*31+l.color;
            for(float[] loop:l.loops){h=h*31+loop.length;for(float v:loop)h=h*31+Float.floatToIntBits(v);}
        }
        return h;
    }
    static float[] rect(float x,float y,float w,float h){return new float[]{x,y,x+w,y,x+w,y+h,x,y+h};}
    static float[] rectCw(float x,float y,float w,float h){return new float[]{x,y,x,y+h,x+w,y+h,x+w,y};}

    /** Delegating backend; can force the shared micro-fragment route and/or sleep on every operation. */
    static final class WrapBackend implements VectorBooleanEngine.Backend {
        final JtsVectorBooleanBackend d=new JtsVectorBooleanBackend();final boolean sharedMicro,slow;
        WrapBackend(boolean sharedMicro,boolean slow){this.sharedMicro=sharedMicro;this.slow=slow;}
        void nap(){if(slow)try{Thread.sleep(1);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
        public Object path(float[][] c){return d.path(c);}
        public Object clipPath(float[][] c){return d.clipPath(c);}
        public Object removeMicroFragments(Object s,float a,float b){nap();return d.removeMicroFragments(s,a,b);}
        @Override public Object removeMicroFragmentsLocal(Object s,float a,float b){
            nap();return sharedMicro?d.removeMicroFragments(s,a,b):d.removeMicroFragmentsLocal(s,a,b);}
        public Object batch(VectorBooleanEngine.Motif[] m,int lo,int hi){nap();return d.batch(m,lo,hi);}
        public Object combine(Object a,Object b,boolean dj){nap();return d.combine(a,b,dj);}
        public Object intersect(Object a,Object b){nap();return d.intersect(a,b);}
        public ArrayList<float[]> contours(Object s){return d.contours(s);}
        @Override public Object cutCondition(Object s,Object c,float a,float b,float t){nap();return d.cutCondition(s,c,a,b,t);}
        @Override public double area(Object s){return d.area(s);}
    }

    static void microFragments(){
        JtsVectorBooleanBackend b=new JtsVectorBooleanBackend();
        Random rnd=new Random(5);
        ArrayList<VectorBooleanEngine.Motif> motifs=new ArrayList<>();
        // 12 plates, each with a 2x2 hole, an 8x8 hole, an island in each hole, and a speck outside.
        for(int i=0;i<12;i++){
            float x=(i%4)*30,y=(i/4)*30;
            motifs.add(new VectorBooleanEngine.Motif(new float[][]{rect(x,y,24,24),rectCw(x+3,y+3,2,2),rectCw(x+10,y+10,8,8)}));
            motifs.add(new VectorBooleanEngine.Motif(new float[][]{rect(x+3.5f,y+3.5f,1,1)}));
            motifs.add(new VectorBooleanEngine.Motif(new float[][]{rect(x+12,y+12,3,3)}));
            motifs.add(new VectorBooleanEngine.Motif(new float[][]{rect(x+26+rnd.nextFloat(),y+26,.2f,.2f)}));
        }
        Object sheet=b.batch(motifs.toArray(new VectorBooleanEngine.Motif[0]),0,motifs.size());
        float minV=.1f;
        for(float waste:new float[]{1f,5f,70f}){
            Geometry shared=(Geometry)b.removeMicroFragments(sheet,minV,waste);
            Geometry local=(Geometry)b.removeMicroFragmentsLocal(sheet,minV,waste);
            require(local.isValid(),"local result invalid");
            require(shared.equalsTopo(local),"local micro-fragment removal differs from shared at waste "+waste);
            require(Math.abs(shared.getArea()-local.getArea())<1e-6,"area differs");
            System.out.printf(Locale.US,"waste %.0f: polygons %d, area %.3f (shared == local)%n",waste,local.getNumGeometries(),local.getArea());
        }
        require(b.removeMicroFragmentsLocal(sheet,0f,0f)==sheet,"no-op must return the same immutable geometry");
        // a hole of 4 mm2 with a 1 mm2 island: waste threshold 5 fills it and the island vanishes
        Geometry filled=(Geometry)b.removeMicroFragmentsLocal(sheet,minV,5f);
        require(filled.getNumGeometries()<((Geometry)sheet).getNumGeometries(),"islands in filled holes must be absorbed");
        System.out.println("micro-fragment local == shared (topological equality): OK");
    }

    static StyleKit.Result render(float mm,int style,int[] pix,int w,int h){
        VinylStyleSuite.Config c=new VinylStyleSuite.Config();c.style=style;c.widthMm=mm;c.detail=68;c.colorCount=4;
        c.palette=new int[]{0xff966b57,0xff794a37,0xff53301e,0xff2b1d15};c.minimumMm=.8f;c.gapMm=.5f;
        return VinylStyleSuite.generate(pix,w,h,c);
    }
    static double area(StyleKit.Result r){double a=0;for(StyleKit.Layer l:r.layers)for(float[] p:l.loops)a+=StyleKit.signedArea(p);return a;}

    static void pipeline(){
        int w=104,h=96;int[] pix=PrismaticWeaveV3Test.ramp(w,h);
        for(int style:new int[]{VinylStyleSuite.PRISMATIC_CHEVRON,VinylStyleSuite.PRISMATIC_DIAGONAL}){
            VectorBooleanEngine.install(new WrapBackend(true,false));
            StyleKit.Result shared=render(101.6f,style,pix,w,h);
            VectorBooleanEngine.install(new WrapBackend(false,false));
            StyleKit.Result local=render(101.6f,style,pix,w,h);
            require(shared.layers.size()==local.layers.size(),"layer count");
            for(int i=0;i<shared.layers.size();i++){
                StyleKit.Layer a=shared.layers.get(i),b=local.layers.get(i);
                require(a.loops.size()==b.loops.size(),"loop count differs, style "+style+" layer "+i+": "+a.loops.size()+" vs "+b.loops.size());
            }
            require(Math.abs(area(shared)-area(local))<1e-3*Math.max(1,Math.abs(area(shared))),"conditioned area differs");
            System.out.printf(Locale.US,"style %d: shared vs local conditioned geometry: %d loops, area %.3f vs %.3f%n",style,
                shared.layers.stream().mapToInt(l->l.loops.size()).sum(),area(shared),area(local));
        }
    }

    static StyleKit.Result raster(){
        StyleKit.Result r=new StyleKit.Result(100,100);
        StyleKit.Layer l=new StyleKit.Layer("wood",0xff553311);
        l.loops.add(rect(5,5,40,40));l.loops.add(rectCw(20,20,.3f,.3f));l.loops.add(rect(45.2f,5,40,40));
        r.layers.add(l);return r;
    }

    static void determinism()throws Exception{
        int w=104,h=96;int[] pix=PrismaticWeaveV3Test.ramp(w,h);
        for(int style:new int[]{VinylStyleSuite.PRISMATIC_CHEVRON,VinylStyleSuite.PRISMATIC_BRAIDED_FLOW,VinylStyleSuite.PRISMATIC_DIAGONAL}){
            VectorBooleanEngine.install(new WrapBackend(false,false));
            ParallelGeometry.forceSequential=false;
            long a=hash(render(101.6f,style,pix,w,h)),b=hash(render(101.6f,style,pix,w,h));
            ParallelGeometry.forceSequential=true;
            long seq=hash(render(101.6f,style,pix,w,h));
            ParallelGeometry.forceSequential=false;
            VectorBooleanEngine.install(new WrapBackend(false,true)); // artificially slow device
            long slow=hash(render(101.6f,style,pix,w,h));
            require(a==b,"same input twice differs, style "+style);
            require(a==seq,"sequential differs from parallel, style "+style);
            require(a==slow,"slow environment changes geometry, style "+style);
            System.out.printf("style %d geometry hash %016x identical (repeat, sequential, slow backend)%n",style,a);
        }
        // cut preparation is bounded by workload, not wall clock
        StyleKit.Result tiles=raster();
        long p1=hash(VinylStyleSuite.prepareForCutting(tiles,VinylStyleSuite.WOODGRAIN,.8f,.5f));
        stop=false;
        for(int i=0;i<4;i++){Thread t=new Thread(()->{double x=0;while(!stop)x+=Math.sqrt(x+1);});t.setDaemon(true);t.start();}
        long p2;try{p2=hash(VinylStyleSuite.prepareForCutting(tiles,VinylStyleSuite.WOODGRAIN,.8f,.5f));}finally{stop=true;}
        require(p1==p2,"prepareForCutting depends on CPU load");
        long work=VinylStyleSuite.layerWork(tiles.layers.get(0).loops);
        require(work>0&&work==VinylStyleSuite.layerWork(tiles.layers.get(0).loops),"work counter must be pure");
        // a sheet list that exhausts the budget: later sheets keep source geometry, always the same ones
        StyleKit.Result many=new StyleKit.Result(100,100);
        long budget=work*2+1; // layers 0-2 are prepared (work is added before each check), the rest keep source geometry
        for(int i=0;i<6;i++){StyleKit.Layer l=new StyleKit.Layer("s"+i,0xff553311+i);l.loops.addAll(tiles.layers.get(0).loops);many.layers.add(l);}
        StyleKit.Result prepared=VinylStyleSuite.prepareForCutting(many,VinylStyleSuite.WOODGRAIN,.8f,.5f,budget);
        long m1=hash(prepared),m2=hash(VinylStyleSuite.prepareForCutting(many,VinylStyleSuite.WOODGRAIN,.8f,.5f,budget));
        require(m1==m2,"budget exhaustion must be deterministic");
        for(int i=3;i<6;i++)require(prepared.layers.get(i).loops.equals(many.layers.get(i).loops),"sheet past the work budget must keep generated geometry");
        require(!prepared.layers.get(0).loops.equals(many.layers.get(0).loops),"within budget the sheet must be conditioned");
        System.out.println("prepareForCutting identical under CPU starvation and at budget exhaustion: OK");
        // TSP 2-opt: tiny nominal budget so the cap binds; a stall must not move the cut point
        int n=3000;Random rnd=new Random(11);float[] p=new float[2*n];
        for(int i=0;i<2*n;i++)p[i]=rnd.nextFloat()*200;
        int[][] nb=TspArtEngine.knn(p,n,8,200,200);
        int[] hashes=new int[3];
        for(int k=0;k<3;k++){
            int[] tour=TspArtEngine.hilbertOrder(p,n,200,200);
            if(k==1){stop=false;Thread t=new Thread(()->{while(!stop)Thread.yield();});t.setDaemon(true);t.start();}
            if(k==2)Thread.sleep(50);
            TspArtEngine.twoOpt(p,tour,nb,1);
            stop=true;hashes[k]=Arrays.hashCode(tour);
        }
        require(hashes[0]==hashes[1]&&hashes[1]==hashes[2],"TSP 2-opt tour depends on wall clock");
        int[] full=TspArtEngine.hilbertOrder(p,n,200,200),capped=TspArtEngine.hilbertOrder(p,n,200,200);
        TspArtEngine.twoOpt(p,full,nb,2500);TspArtEngine.twoOpt(p,capped,nb,Long.MAX_VALUE/TspArtEngine.WORK_PER_MS);
        require(Arrays.equals(full,capped),"the 2500 nominal-ms suite budget must not bind at 3000 points");
        System.out.println("TSP 2-opt identical under stalls; suite budget not binding at 3000 points: OK");
    }
    public static void main(String[] a)throws Exception{
        microFragments();
        pipeline();
        determinism();
        System.out.println("PASS M5DeterministicGeometryTest");
    }
}

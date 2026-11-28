package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * M8 / C2: grouped cut repair must not condition geometry whose result can never be used.
 * When any defective group exceeds the conditioner's deterministic budget (or more than eight
 * groups are defective), the layer is left unchanged; that decision is now made before any
 * rasterized conditioning. Also: withinBudget() agrees with condition() on real sheets, and a
 * feasible grouped repair still runs and is still accepted.
 */
public final class M8RepairFeasibilityTest {
    static int failures;
    static void check(boolean ok,String m){System.out.println((ok?"PASS ":"FAIL ")+m);if(!ok)failures++;}
    static float[] rect(float x,float y,float w,float h){return new float[]{x,y,x+w,y,x+w,y+h,x,y+h};}
    /** Two lobes joined by a sub-minimum neck: a real vinyl-width defect. */
    static float[] dumbbell(float x,float y,float s,float neck){
        float m=y+s/2;
        return new float[]{x,y,x+s,y,x+s,m-neck/2,x+s+2,m-neck/2,x+s+2,y,x+2*s+2,y,x+2*s+2,y+s,x+s+2,y+s,x+s+2,m+neck/2,x+s,m+neck/2,x+s,y+s,x,y+s};
    }
    static boolean same(List<float[]> a,List<float[]> b){
        if(a.size()!=b.size())return false;
        for(int i=0;i<a.size();i++)if(!Arrays.equals(a.get(i),b.get(i)))return false;
        return true;
    }
    public static void main(String[] args)throws Exception{
        final float min=.254f,gap=.508f,W=290,H=290;
        // 1. Small defective groups first, then one huge defective group (grid > 2.5 MP).
        StyleKit.Result r=new StyleKit.Result(W,H);
        StyleKit.Layer l=new StyleKit.Layer("sheet",0xff000000);
        for(int i=0;i<3;i++)l.loops.add(dumbbell(2+i*20,2,6,.12f));
        l.loops.add(dumbbell(10,30,120,.12f));
        r.layers.add(l);
        ArrayList<float[]> before=new ArrayList<>();for(float[] q:l.loops)before.add(q.clone());
        check(!CutGeometryConditioner.withinBudget(List.of(l.loops.get(3)),W,H,min,gap),"huge group is over the conditioner budget");
        check(CutGeometryConditioner.withinBudget(List.of(l.loops.get(0)),W,H,min,gap),"small group is within budget");
        long runs=CutGeometryConditioner.RASTER_RUNS.sum();
        VinylStyleSuite.conditionForCutting(r,l,min,gap);
        long used=CutGeometryConditioner.RASTER_RUNS.sum()-runs;
        check(same(before,l.loops),"infeasible grouped repair leaves the layer byte-identical");
        check(used==0,"no rasterized conditioning spent on an unusable repair ("+used+" runs; was 3)");
        // 2. More than eight defective groups: unchanged, no conditioning.
        StyleKit.Result r9=new StyleKit.Result(W,H);
        StyleKit.Layer l9=new StyleKit.Layer("nine",0xff000000);
        for(int i=0;i<9;i++)l9.loops.add(dumbbell(2+(i%3)*95,2+(i/3)*95,40,.12f));
        r9.layers.add(l9);
        ArrayList<float[]> b9=new ArrayList<>();for(float[] q:l9.loops)b9.add(q.clone());
        runs=CutGeometryConditioner.RASTER_RUNS.sum();
        VinylStyleSuite.conditionForCutting(r9,l9,min,gap);
        used=CutGeometryConditioner.RASTER_RUNS.sum()-runs;
        check(same(b9,l9.loops),"nine defective groups: layer unchanged");
        check(used==0,"nine defective groups: no rasterized conditioning ("+used+" runs)");
        // 3. A feasible grouped repair still happens (two far-apart small defects on a wide sheet).
        StyleKit.Result rf=new StyleKit.Result(W,H);
        StyleKit.Layer lf=new StyleKit.Layer("feasible",0xff000000);
        lf.loops.add(dumbbell(2,2,6,.12f));lf.loops.add(dumbbell(270,270,6,.12f));
        rf.layers.add(lf);
        ArrayList<float[]> bf=new ArrayList<>(lf.loops);
        VinylStyleSuite.conditionForCutting(rf,lf,min,gap);
        StyleKit.Result chk=new StyleKit.Result(W,H);chk.layers.add(lf);
        check(!same(bf,lf.loops)&&StyleCutGeometryPreflight.inspect(chk,min,gap,10).isEmpty(),"feasible grouped repair still applied and clean");
        // 3b. The same over-budget fallback is wired on the local (exact-geometry) path too.
        StyleKit.Result rl=new StyleKit.Result(W,H);
        StyleKit.Layer ll=new StyleKit.Layer("feasible-local",0xff000000);
        ll.loops.add(dumbbell(2,2,6,.12f));ll.loops.add(dumbbell(270,270,6,.12f));
        rl.layers.add(ll);
        ArrayList<float[]> bl=new ArrayList<>(ll.loops);
        ArrayList<StyleKit.Layer> deferred=new ArrayList<>();
        VinylStyleSuite.conditionForCutting(rl,ll,min,gap,true,deferred);
        StyleKit.Result chl=new StyleKit.Result(W,H);chl.layers.add(ll);
        check(!same(bl,ll.loops)&&StyleCutGeometryPreflight.inspect(chl,min,gap,10).isEmpty()&&deferred.isEmpty(),
            "local path: over-budget sheet falls back to grouped repair (clean) and is never deferred to vector morphology");
        // 4. withinBudget() == condition() budget outcome on real organic sheets.
        VectorBooleanEngine.install((VectorBooleanEngine.Backend)Class.forName("com.halftonestudio.JtsVectorBooleanBackend").getDeclaredConstructor().newInstance());
        M4ArtBench.Photo p=M4ArtBench.load(new java.io.File("tests/m4/photos/astronaut.png"),900);
        int agree=0,over=0;
        for(int style:new int[]{VinylStyleSuite.CAMO_URBAN,VinylStyleSuite.QUILLING_RIBBONS,VinylStyleSuite.MARQUETRY}){
            StyleKit.Result g=VinylStyleSuite.generate(p.argb,p.w,p.h,M4ArtBench.suiteConfig(style,p,101.6f,min,gap));
            for(StyleKit.Layer s:g.layers){
                if(s.loops.isEmpty())continue;
                for(List<float[]> group:CutRepairPartitions.group(s.loops,2f*gap)){
                    boolean predicted=CutGeometryConditioner.withinBudget(group,g.widthMm,g.heightMm,min,gap),ran;
                    try{CutGeometryConditioner.condition(group,g.widthMm,g.heightMm,min,gap);ran=true;}
                    catch(CutGeometryConditioner.BudgetExceededException e){ran=false;}
                    if(predicted==ran)agree++;else check(false,"withinBudget disagrees with condition() on "+VinylStyleSuite.NAMES[style]);
                    if(!ran)over++;
                }
            }
        }
        check(agree>20&&over>0,String.format(Locale.US,"withinBudget matches condition() on %d real groups (%d over budget)",agree,over));
        if(failures>0){System.out.println(failures+" M8 repair feasibility check(s) FAILED");System.exit(1);}
        System.out.println("PASS M8 repair feasibility test");
    }
}

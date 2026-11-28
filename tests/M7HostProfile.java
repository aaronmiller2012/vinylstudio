package com.halftonestudio;

import java.io.File;

/** M7: host timing/profiling harness for one style (pair with JFR). Not a gate.
 * java ... M7HostProfile <styleId> <widthMm> <photo> [repeats] [sequential|parallel] */
public final class M7HostProfile {
    public static void main(String[] a) throws Exception {
        VectorBooleanEngine.install(new JtsVectorBooleanBackend());
        int id=Integer.parseInt(a[0]);float width=Float.parseFloat(a[1]);
        int repeats=a.length>3?Integer.parseInt(a[3]):3;
        ParallelGeometry.forceSequential=a.length<=4||!"parallel".equals(a[4]);
        M4ArtBench.Photo p=M4ArtBench.load(new File(a[2]),1800);
        for(int r=0;r<repeats;r++){
            float minMm=Float.parseFloat(System.getProperty("min","0.8")),gapMm=Float.parseFloat(System.getProperty("gap","0.5"));
            VinylStyleSuite.Config c=M4ArtBench.suiteConfig(id,p,width,minMm,gapMm);
            long t=System.nanoTime();
            StyleKit.Result res=VinylStyleSuite.generate(p.argb,p.w,p.h,c);
            long ms=(System.nanoTime()-t)/1_000_000;
            int loops=0;for(StyleKit.Layer l:res.layers)loops+=l.loops.size();
            long t2=System.nanoTime();
            StyleKit.Result cut=Boolean.getBoolean("noCut")?res:VinylStyleSuite.prepareForCutting(res,id,minMm,gapMm);
            long cutMs=(System.nanoTime()-t2)/1_000_000;
            System.out.printf("style=%d (%s) width=%.1f run=%d engineMs=%d cutStageMs=%d layers=%d loops=%d%n",id,VinylStyleSuite.NAMES[id],width,r,ms,cutMs,res.layers.size(),loops);
        }
    }
}

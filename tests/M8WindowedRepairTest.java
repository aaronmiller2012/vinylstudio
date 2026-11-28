package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;

/**
 * M8 C2b: capped windowed repair of organic sheets whose raster repair is infeasible.
 * Real engine output (Urban Camo, Quilling Ribbons, Marbled Ink on two photos): findings never
 * increase per sheet, the total drops, the deterministic work budget is respected, results are
 * identical on repeat, every changed sheet keeps >= 90% of its vinyl, and exact geometric styles
 * are never handed to vector morphology.
 */
public final class M8WindowedRepairTest {
    static int failures;
    static void check(boolean ok,String m){System.out.println((ok?"PASS ":"FAIL ")+m);if(!ok)failures++;}
    static int findings(StyleKit.Result r,int li){
        StyleKit.Result one=new StyleKit.Result(r.widthMm,r.heightMm);one.layers.add(r.layers.get(li));
        return StyleCutGeometryPreflight.inspect(one,.254f,.508f,100000).size();
    }
    static int critical(StyleKit.Result r,int li){
        StyleKit.Result one=new StyleKit.Result(r.widthMm,r.heightMm);one.layers.add(r.layers.get(li));
        return VinylStyleSuite.critical(StyleCutGeometryPreflight.inspect(one,.254f,.508f,100000));
    }
    static String hash(StyleKit.Result r){StringBuilder b=new StringBuilder();for(StyleKit.Layer l:r.layers){for(float[] q:l.loops)b.append(Arrays.hashCode(q)).append(',');b.append('|');}return Integer.toHexString(b.toString().hashCode());}
    public static void main(String[] args)throws Exception{
        VectorBooleanEngine.install((VectorBooleanEngine.Backend)Class.forName("com.halftonestudio.JtsVectorBooleanBackend").getDeclaredConstructor().newInstance());
        int before=0,after=0,cases=0,changedSheets=0;
        for(String name:new String[]{"astronaut.png","coffee.png"}){
            M4ArtBench.Photo p=M4ArtBench.load(new java.io.File("tests/m4/photos/"+name),512);
            for(int style:new int[]{VinylStyleSuite.CAMO_URBAN,VinylStyleSuite.QUILLING_RIBBONS,VinylStyleSuite.MARBLED_INK}){
                StyleKit.Result g=VinylStyleSuite.generate(p.argb,p.w,p.h,M4ArtBench.suiteConfig(style,p,152.4f,.254f,.508f));
                // Reference: the cut-ready stage without the windowed pass.
                StyleKit.Result ref=new StyleKit.Result(g.widthMm,g.heightMm);ArrayList<StyleKit.Layer> deferred=new ArrayList<>();
                for(StyleKit.Layer l:g.layers){StyleKit.Layer c=new StyleKit.Layer(l.name,l.color);c.loops.addAll(l.loops);c.lines.addAll(l.lines);c.vectorGeometryLocked=l.vectorGeometryLocked;
                    if(!c.vectorGeometryLocked&&c.lines.isEmpty())VinylStyleSuite.conditionForCutting(ref,c,VinylStyleSuite.cutMinimumMm(.254f),VinylStyleSuite.generationGapMm(.508f,.254f),false,deferred);
                    ref.layers.add(c);}
                StyleKit.Result cut=VinylStyleSuite.prepareForCutting(g,style,.254f,.508f);
                long work=VinylStyleSuite.lastWindowedWork;
                StyleKit.Result again=VinylStyleSuite.prepareForCutting(g,style,.254f,.508f);
                String tag=String.format(Locale.US,"%s %s 6 in",name,VinylStyleSuite.NAMES[style]);
                check(hash(cut).equals(hash(again))&&work==VinylStyleSuite.lastWindowedWork,tag+": deterministic");
                check(work>0&&work<=VinylStyleSuite.WINDOWED_REPAIR_WORK,tag+": work "+work+" within budget "+VinylStyleSuite.WINDOWED_REPAIR_WORK);
                int fb=0,fa=0;boolean noWorse=true,coverage=true,noCritical=true;
                for(int li=0;li<cut.layers.size();li++){
                    int x=findings(ref,li),y=findings(cut,li);fb+=x;fa+=y;
                    if(y>x)noWorse=false;
                    if(critical(cut,li)>critical(ref,li))noCritical=false;
                    if(!ref.layers.get(li).loops.equals(cut.layers.get(li).loops)){
                        changedSheets++;
                        coverage&=ConfettiVectorRepair.retainsCoverage(ref.layers.get(li).loops,cut.layers.get(li).loops,cut.widthMm,cut.heightMm,.254f);
                        // Loops of trees the windows did not reach are kept byte-for-byte.
                        IdentityHashMap<float[],Boolean> kept=new IdentityHashMap<>();for(float[] q:cut.layers.get(li).loops)kept.put(q,true);
                        int same=0;for(float[] q:ref.layers.get(li).loops)if(kept.containsKey(q))same++;
                        if(same==0&&ref.layers.get(li).loops.size()>50)check(false,tag+" L"+(li+1)+": no original loop kept verbatim");
                    }
                }
                check(noWorse,tag+": no sheet gains findings");
                check(noCritical,tag+": no sheet gains contacts / crossings / overlaps");
                check(coverage,tag+": changed sheets keep >= 90% of their vinyl");
                check(fa<fb,String.format(Locale.US,"%s: findings %d -> %d",tag,fb,fa));
                before+=fb;after+=fa;cases++;
            }
        }
        check(changedSheets>0,"windowed repair changed "+changedSheets+" sheets");
        // Exact geometric styles never reach vector morphology.
        int[] q=V3AllStyleExportMatrixTest.portrait(80,80);
        for(int style:new int[]{VinylStyleSuite.TESSELLATION_MOTIF,VinylStyleSuite.TRUCHET,VinylStyleSuite.LOW_POLY}){
            if(VinylStyleSuite.organicCutGeometry(style))continue;
            VinylStyleSuite.Config c=new VinylStyleSuite.Config();c.style=style;c.widthMm=152.4f;c.detail=60;c.colorCount=4;c.minimumMm=.254f;
            StyleKit.Result g=VinylStyleSuite.generate(q,80,80,c);
            VinylStyleSuite.prepareForCutting(g,style,.254f,.508f);
            check(VinylStyleSuite.lastWindowedWork==0,VinylStyleSuite.NAMES[style]+": no windowed vector repair");
        }
        if(failures>0){System.out.println(failures+" M8 windowed repair check(s) FAILED");System.exit(1);}
        System.out.printf(Locale.US,"PASS M8 capped windowed repair: %d cases, findings %d -> %d%n",cases,before,after);
    }
}

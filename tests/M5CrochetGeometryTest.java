package com.halftonestudio;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/**
 * M5 Crochet (style 39): the FINISHED cut sheets of the real production engine, measured
 * with exact vector morphology (M5CutGeometryProbe), not nominal primitive sizes.
 * Every overlay sheet must have: no vinyl narrower than the cut minimum (opening residue),
 * no necks, no vanished islands, no waste narrower than the weed gap, no hole below the
 * filet space minimum, zero cut-inspector findings, and nothing removed or filled by the
 * cut-ready stage. Also: Snip containment, all layer counts, Detail extremes, flat
 * photos, material minimum extremes, portrait 11 in, invalid input and determinism.
 */
public final class M5CrochetGeometryTest {
    static int failures=0;
    static void check(boolean ok,String what){
        if(ok)System.out.println("PASS "+what);else{failures++;System.out.println("FAIL "+what);}
    }
    static final float MIN=.8f,BRIDGE=.5f;
    static StyleKit.Result suite(M4ArtBench.Photo p,float widthMm,int layers,int detail,float min,boolean[] allowed){
        VinylStyleSuite.Config c=M4ArtBench.suiteConfig(VinylStyleSuite.CROCHET,p,widthMm,min,BRIDGE);
        c.colorCount=layers;c.palette=null;c.allowed=allowed;if(detail>=0)c.detail=detail;
        return VinylStyleSuite.generate(p.argb,p.w,p.h,c);
    }
    /** Hole minimum promised by the engine: max(1.75 x engine minimum, 3 x gap) at the
     *  user's 0.8 mm minimum (engine minimum = 0.8 x 1.12 + 0.04). */
    static float holeMinMm(float min){
        float engineMin=Math.min(4f,min*1.12f+.04f),gap=StyleCutGeometryPreflight.exportGapMm(BRIDGE,min);
        return Math.max(1.75f*engineMin,3f*gap)-.05f;
    }
    static void finished(String tag,StyleKit.Result r,float min){
        float gap=StyleCutGeometryPreflight.exportGapMm(BRIDGE,min);
        float hole=holeMinMm(min);
        M5CutGeometryProbe.Stats all=new M5CutGeometryProbe.Stats();
        for(int l=1;l<r.layers.size();l++){
            List<float[]> loops=r.layers.get(l).loops;
            check(!loops.isEmpty(),tag+" L"+(l+1)+" carries yarn");
            for(float[] p:loops)for(float v:p)if(!Float.isFinite(v)){check(false,tag+" finite coordinates");return;}
            all.add(M5CutGeometryProbe.measure(loops,min,gap,hole*hole*.9f));
        }
        check(all.narrowPieces==0&&all.necks==0&&all.vanished==0,
            tag+" no sub-minimum vinyl: narrow="+all.narrowPieces+" ("+String.format("%.2f",all.narrowArea)+" mm2) necks="+all.necks+" vanished="+all.vanished);
        check(all.narrowWastePieces==0&&all.subGapHoles==0,tag+" no sub-gap waste: slits="+all.narrowWastePieces+" holes="+all.subGapHoles);
        check(all.tinyHoles==0,tag+" every hole >= the filet space minimum ("+all.holes+" holes, smallest "+
            String.format("%.2f",all.minHoleArea)+" mm2)");
        int findings=StyleCutGeometryPreflight.inspect(r,min,gap,100000).size();
        check(findings==0,tag+" cut inspector findings "+findings);
        StyleKit.Result cut=VinylStyleSuite.prepareForCutting(r,VinylStyleSuite.CROCHET,min,BRIDGE);
        int[] a=M4ArtBench.topology(r),b=M4ArtBench.topology(cut);
        boolean same=true;
        for(int l=0;l<r.layers.size();l++)same&=r.layers.get(l).loops.size()==cut.layers.get(l).loops.size();
        check(a[0]==b[0]&&a[1]==b[1]&&same,tag+" cut-ready stage changes nothing (pieces "+a[0]+"->"+b[0]+", holes "+a[1]+"->"+b[1]+")");
        // Overlays never leave the solid L1 foundation (Snip), and L1 is solid.
        Geometry base=M5CutGeometryProbe.region(r.layers.get(0).loops);
        double outside=0;
        for(int l=1;l<r.layers.size();l++)
            outside+=OverlayNGRobust.overlay(M5CutGeometryProbe.region(r.layers.get(l).loops),base,OverlayNG.DIFFERENCE).getArea();
        check(outside<1e-3,tag+" overlays inside the L1 foundation (outside area "+outside+" mm2)");
    }
    static boolean[] ellipse(int w,int h){
        boolean[] m=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){double dx=(x-w*.5)/(w*.43),dy=(y-h*.5)/(h*.47);m[y*w+x]=dx*dx+dy*dy<1;}
        return m;
    }
    static M4ArtBench.Photo flat(int argb){int[] a=new int[160*200];Arrays.fill(a,argb);return new M4ArtBench.Photo("flat",a,160,200);}
    public static void main(String[] args)throws Exception{
        File dir=new File(args.length>0?args[0]:"tests/m4/photos");
        M4ArtBench.Photo astro=M4ArtBench.load(new File(dir,"astronaut.png"),850);
        M4ArtBench.Photo horse=M4ArtBench.load(new File(dir,"horse.png"),850);
        M4ArtBench.Photo portrait=M5CrochetBench.portraitCrop(astro);
        // Physical sizes, including the tall 11 in portrait.
        for(float in:new float[]{3,6,11})finished("astronaut "+in+" in",suite(astro,in*25.4f,9,-1,MIN,null),MIN);
        finished("astronaut portrait 11 in",suite(portrait,279.4f,9,-1,MIN,null),MIN);
        finished("horse 4 in",suite(horse,101.6f,9,-1,MIN,null),MIN);
        // All layer counts.
        for(int layers=2;layers<=9;layers++)finished("astronaut 4 in, "+layers+" layers",suite(astro,101.6f,layers,-1,MIN,null),MIN);
        // Detail extremes (coarse grid with half blocks / finest grid).
        finished("astronaut 6 in detail 0",suite(astro,152.4f,9,0,MIN,null),MIN);
        finished("astronaut 6 in detail 100",suite(astro,152.4f,9,100,MIN,null),MIN);
        // Material minimum extremes (CutMaterialProfile 0.65 - 1.10 mm).
        for(float min:new float[]{.65f,1.10f})finished("astronaut 3 in min "+min,suite(astro,76.2f,9,-1,min,null),min);
        // Snip mask boundary: an elliptical subject.
        finished("astronaut 6 in elliptical Snip",suite(astro,152.4f,6,-1,MIN,ellipse(astro.w,astro.h)),MIN);
        // Flat photos: zero tonal information still yields every sheet, cut-safe.
        finished("flat grey 4 in",suite(flat(0xff808080),101.6f,9,-1,MIN,null),MIN);
        finished("flat black 4 in",suite(flat(0xff000000),101.6f,5,-1,MIN,null),MIN);
        // Tone: open and solid squares both occur on a real portrait, and the stitch
        // pitch adapts to size (coarser grid relative to the artwork at 3 in).
        StyleKit.Result small=suite(astro,76.2f,9,-1,MIN,null),large=suite(astro,279.4f,9,-1,MIN,null);
        int sq3=squares(small.summary),sq11=squares(large.summary);
        check(sq3>0&&sq11>sq3*3,"lattice adapts to size: "+sq3+" squares at 3 in, "+sq11+" at 11 in");
        check(small.summary.contains(" open / ")&&!small.summary.contains(" 0 open /")&&!small.summary.contains("/ 0 block"),
            "portrait uses both open mesh and solid blocks: "+small.summary.replaceAll(".*filet ","filet "));
        // Determinism.
        StyleKit.Result again=suite(astro,76.2f,9,-1,MIN,null);
        boolean same=again.layers.size()==small.layers.size();
        for(int l=0;same&&l<small.layers.size();l++){
            List<float[]> x=small.layers.get(l).loops,y=again.layers.get(l).loops;
            same=x.size()==y.size();
            for(int i=0;same&&i<x.size();i++)same=Arrays.equals(x.get(i),y.get(i));
        }
        check(same,"deterministic geometry");
        // Invalid input fails clearly, never as NaN geometry.
        NineLayerCraftPatternEngine.Options bad=new NineLayerCraftPatternEngine.Options();
        bad.pattern=NineLayerCraftPatternEngine.CROCHET;bad.widthMm=Float.NaN;
        check(throwsIae(()->NineLayerCraftPatternEngine.generate(astro.argb,astro.w,astro.h,bad)),"NaN width rejected");
        NineLayerCraftPatternEngine.Options neg=new NineLayerCraftPatternEngine.Options();
        neg.pattern=NineLayerCraftPatternEngine.CROCHET;neg.minCutMm=-1f;
        check(throwsIae(()->NineLayerCraftPatternEngine.generate(astro.argb,astro.w,astro.h,neg)),"negative minimum rejected");
        NineLayerCraftPatternEngine.Options huge=new NineLayerCraftPatternEngine.Options();
        huge.pattern=NineLayerCraftPatternEngine.CROCHET;huge.minCutMm=4f;huge.widthMm=25f;huge.layers=9;
        check(throwsIae(()->NineLayerCraftPatternEngine.generate(astro.argb,astro.w,astro.h,huge)),
            "too-small artwork for 4 mm stitches refused with a message");
        VinylStyleSuite.Config c=M4ArtBench.suiteConfig(VinylStyleSuite.CROCHET,astro,76.2f,MIN,BRIDGE);c.style=999;
        check(throwsIae(()->VinylStyleSuite.generate(astro.argb,astro.w,astro.h,c)),"invalid style ID rejected");
        // Legacy single-stitch primitive: a block carries much more yarn than a mesh.
        int[] px=new int[100*100];Arrays.fill(px,0xff777777);
        StyleKit.Source src=new StyleKit.Source(px,100,100,null,100f);
        NineLayerCraftPatternEngine.Options o=new NineLayerCraftPatternEngine.Options();o.minCutMm=.936f;
        PatternPortraitTrioEngine.Surface open=new PatternPortraitTrioEngine.Surface(src,1,o.minCutMm,false);
        PatternPortraitTrioEngine.Surface block=new PatternPortraitTrioEngine.Surface(src,1,o.minCutMm,false);
        NineLayerCraftPatternEngine.crochet(open,new NineLayerCraftPatternEngine.Cell(50,50,.84f,.05f,1L,0,0),8f,1f,o);
        NineLayerCraftPatternEngine.crochet(block,new NineLayerCraftPatternEngine.Cell(50,50,.22f,.05f,1L,0,0),8f,1f,o);
        int a=count(open.ink[0]),b=count(block.ink[0]);
        check(a>0&&b>a*1.5,"single block stitch "+b+" px vs open mesh "+a+" px");
        if(failures>0){System.out.println(failures+" M5 crochet geometry check(s) FAILED");System.exit(1);}
        System.out.println("PASS M5 crochet finished-geometry test");
    }
    static int squares(String summary){
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("filet (\\d+)x(\\d+) squares").matcher(summary);
        return m.find()?Integer.parseInt(m.group(1))*Integer.parseInt(m.group(2)):-1;
    }
    static int count(boolean[] b){int n=0;for(boolean v:b)if(v)n++;return n;}
    static boolean throwsIae(Runnable r){
        try{r.run();return false;}catch(IllegalArgumentException expected){return true;}
    }
}

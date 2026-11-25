package com.halftonestudio;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;

/** M5 Confetti: the production palette path (VinylPaletteMapping) + the real FallenConfettiEngine through
 *  VinylStyleSuite on a real photo. Behavioural: composite fidelity, solid silhouette base, opaque overprint,
 *  shape variants, reverse/surface invariants. */
public final class M5ConfettiBaseTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static final int SENTINEL=0xffff00ff;

    static M4ArtBench.Render render(int style,M4ArtBench.Photo p,int[] colors,int count,float inches)throws Exception{
        VinylStyleSuite.Config c=M4ArtBench.suiteConfig(style,p,inches*25.4f,.8f,.5f);
        c.colorCount=count;c.palette=Arrays.copyOf(colors,count);c.leadColor=colors[0];
        StyleKit.Result r=VinylStyleSuite.generate(p.argb,p.w,p.h,c);
        M4ArtBench.Render rr=new M4ArtBench.Render();rr.widthMm=inches*25.4f;rr.heightMm=r.heightMm;rr.paper=SENTINEL;
        for(StyleKit.Layer l:r.layers){M4ArtBench.Sheet sh=new M4ArtBench.Sheet();sh.color=l.color;sh.loops.addAll(l.loops);sh.lines.addAll(l.lines);sh.strokeMm=l.strokeMm;rr.sheets.add(sh);}
        return rr;
    }
    static double dE(M4ArtBench.Render rr,M4ArtBench.Photo p,BufferedImage comp){
        return Double.parseDouble(M4ArtBench.metrics(rr,p,comp).split("\t")[3]);
    }
    public static void main(String[] a)throws Exception{
        File dir=new File(a.length>0?a[0]:"tests/m4/photos");
        M4ArtBench.Photo p=M4ArtBench.load(new File(dir,"astronaut.png"),600);
        int[] ph=M4ArtBench.thumb(p,96);boolean[] v=new boolean[ph.length];Arrays.fill(v,true);
        int[] eight=PhotoPaletteOptimizer.optimize(ph,v,8).colors;
        final int count=6;
        // ---- production palette path, pre-M5 vs M5 (photo palette)
        int[] before=PaletteOrientation.reverseActive(FoundationColorPolicy.anchorPhotoPalette(eight,count,ph,false),count,false,true);
        int[] after=VinylPaletteMapping.forStyle(VinylStyleSuite.CONFETTI,count,0xffffffff,eight,false,true,ph);
        // every swatch retained exactly once, parked ones untouched, deterministic, saved palettes (no photo) untouched
        int[] x=after.clone(),y=eight.clone();Arrays.sort(x);Arrays.sort(y);check(Arrays.equals(x,y),"swatches lost/recoloured");
        check(Arrays.equals(after,VinylPaletteMapping.forStyle(VinylStyleSuite.CONFETTI,count,0xffffffff,eight,false,true,ph)),"nondeterministic");
        check(Arrays.equals(VinylPaletteMapping.forStyle(VinylStyleSuite.CONFETTI,count,0xff000000,eight,false,true,null),eight),"saved/no-photo palette must stay as saved");
        for(int s:new int[]{0xffffffff,0xff000000,0xff224488})
            check(Arrays.equals(after,VinylPaletteMapping.forStyle(VinylStyleSuite.CONFETTI,count,s,eight,false,true,ph)),"mounting surface changed palette");
        // reverse: involution, base stays L1
        int[] rev=VinylPaletteMapping.forStyle(VinylStyleSuite.CONFETTI,count,0xffffffff,eight,true,true,ph);
        check(rev[0]==after[0],"Reverse moved the solid base");
        check(Arrays.equals(PaletteOrientation.reverseActive(rev,count,true,true),after),"Reverse not an involution");
        // highlight swatch is active (the pre-M5 first-N flow parked it)
        double p95=M5PhotoSubsetTest.p95L(ph);
        double gapB=p95-M5PhotoSubsetTest.maxL(before,count),gapA=p95-M5PhotoSubsetTest.maxL(after,count);
        check(gapA<gapB*.6,String.format(Locale.US,"highlight still parked: gap %.3f -> %.3f",gapB,gapA));
        // named palette without a photo: base is the median tone, not the darkest
        int[] named=VinylPalettes.get(19,8);
        int[] nm=VinylPaletteMapping.forStyle(VinylStyleSuite.CONFETTI,count,0xffffffff,named,false,false,null);
        double dark=1;for(int i=0;i<count;i++)dark=Math.min(dark,PerceptualVinylColor.lab(nm[i])[0]);
        check(PerceptualVinylColor.lab(nm[0])[0]>dark+.05,"named palette base is still the darkest colour");

        // ---- real engine: fidelity and physical structure
        M4ArtBench.Render rb=render(VinylStyleSuite.CONFETTI,p,before,count,4f),ra=render(VinylStyleSuite.CONFETTI,p,after,count,4f);
        BufferedImage cb=M4ArtBench.composite(rb,5f),ca=M4ArtBench.composite(ra,5f);
        double deB=dE(rb,p,cb),deA=dE(ra,p,ca);
        System.out.println(String.format(Locale.US,"astronaut 4in photo palette: viewing-scale OKLab dE %.4f -> %.4f (%.0f%%)",deB,deA,100*(deA-deB)/deB));
        check(deA<deB*.90,"expected at least 10% lower dE");
        // solid silhouette: no mounting-surface sentinel visible anywhere inside the artwork (anti-alias edge excepted)
        int[] px=ca.getRGB(0,0,ca.getWidth(),ca.getHeight(),null,0,ca.getWidth());int hole=0;
        for(int q:px)if((q&0xffffff)==(SENTINEL&0xffffff))hole++;
        check(hole<px.length*.002,"base is not a solid silhouette: "+hole+" px of "+px.length);
        // opaque overprint: fragment sheets overlap the base and each other, one L1 + (count-1) upper sheets
        check(ra.sheets.size()==count,"sheet count "+ra.sheets.size());
        check(ra.sheets.get(0).color==after[0],"L1 colour must be the chosen base");
        // ---- every shape variant still produces fragments on the new base
        int[] ids={VinylStyleSuite.CONFETTI,VinylStyleSuite.CONFETTI_ROUND,VinylStyleSuite.CONFETTI_SQUARE,
            VinylStyleSuite.CONFETTI_PAPER,VinylStyleSuite.CONFETTI_STREAMER,VinylStyleSuite.CONFETTI_FALLEN};
        for(int id:ids){
            PatternPortraitTrioEngine.Options o=new PatternPortraitTrioEngine.Options();
            o.kind=PatternPortraitTrioEngine.CONFETTI;o.confettiMode=VinylStyleSuite.confettiShapeMode(id);
            o.detail=60;o.organic=50;o.layers=count;o.widthMm=101.6f;o.palette=Arrays.copyOf(after,count);o.spacingMm=.35f;o.minCutMm=.8f;
            StyleKit.Source src=new StyleKit.Source(p.argb,p.w,p.h,null,101.6f).autoLevels(.02f,.98f);
            FallenConfettiEngine.Plan plan=FallenConfettiEngine.sample(src,o,o.palette);
            check(plan.pieces.size()>60,"too few fragments in mode "+id);
            int used=0;for(int t:plan.types)if(t>0)used++;
            check(o.confettiMode==FallenConfettiEngine.MODE_MIXED||o.confettiMode==FallenConfettiEngine.MODE_FALLEN?used>=3:used==1,"shape variants broken "+id);
            for(FallenConfettiEngine.Piece pc:plan.pieces)check(pc.slot>=1&&pc.slot<count,"fragment on base slot");
        }
        System.out.println("PASS M5 confetti base policy");
    }
}

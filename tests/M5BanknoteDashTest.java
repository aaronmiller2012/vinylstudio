package com.halftonestudio;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * M5 Banknote Engraving error-diffused highlight dashes: production engine output on a real
 * photograph (tests/m4/photos/astronaut.png) through the production suite route.
 *  - the physical cut gate finds nothing (no sub-minimum island, bridge or gap) at
 *    3, 6 and 11 in, portrait, 0.8 mm and 0.254 mm minimum, for every selectable variant;
 *  - per-colour pieces stay under the 5,000-shape SVG limit;
 *  - highlights (bright source) are much lighter than the legacy minimum-width floor while
 *    shadows keep their vinyl (tone mapping, not a uniform screen);
 *  - an all-dark image is unchanged by diffusion (FIXED ribbons are never altered);
 *  - a circular Snip does not thin the engraving inside the retained region;
 *  - invalid / NaN option values are rejected, output is deterministic.
 */
public final class M5BanknoteDashTest {
    static int failures=0;
    static void check(boolean ok,String what){if(!ok){failures++;System.out.println("FAIL "+what);}else System.out.println("ok   "+what);}
    static int[] px;static int pw,ph;

    static BanknoteEngravingEngine.Options options(float widthMm,float minMm,int variant,float diffusion){
        // Same mapping as VinylStyleSuite.generate (case BANKNOTE) for detail 0.5.
        BanknoteEngravingEngine.Options o=new BanknoteEngravingEngine.Options();
        o.widthMm=widthMm;o.variant=variant;
        float diagonal=(float)Math.hypot(widthMm,widthMm*ph/(float)pw);
        o.pitchMm=Math.max(minMm+.35f,diagonal/(55f+95f*.5f));
        o.minMm=Math.max(.254f,minMm);o.gapMm=Math.max(.24f,minMm*.70f);
        o.pitchMm=Math.max(o.pitchMm,o.minMm+o.gapMm+.24f);
        o.warpMm=.18f*o.pitchMm;o.weightMm=Math.max(minMm,.7f);
        o.minRunMm=Math.max(1.4f,o.minMm*3f);
        o.crossEtch=variant==BanknoteEngravingEngine.CROSS_ETCH||variant==BanknoteEngravingEngine.TWO_INK;
        o.vignette=variant==BanknoteEngravingEngine.VIGNETTE;
        o.dashHighlights=variant==BanknoteEngravingEngine.HIGHLIGHT_DASH;
        o.highlightDiffusion=diffusion;
        return o;
    }
    /** Fraction of a band of the image (by source luminance) covered by primary vinyl. */
    static double[] bandCoverage(StyleKit.Result r,int[] source,boolean[] region){
        int W=240,H=Math.round(240f*r.heightMm/r.widthMm);
        BufferedImage img=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=img.createGraphics();g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,W,H);
        g.setColor(java.awt.Color.BLACK);g.scale(W/r.widthMm,H/r.heightMm);
        java.awt.geom.Path2D.Float path=new java.awt.geom.Path2D.Float(java.awt.geom.Path2D.WIND_EVEN_ODD);
        for(float[] xy:r.layers.get(0).loops){path.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)path.lineTo(xy[i],xy[i+1]);path.closePath();}
        g.fill(path);g.dispose();
        double[] cov=new double[3];int[] n=new int[3];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            int sx=Math.min(pw-1,x*pw/W),sy=Math.min(ph-1,y*ph/H);
            if(region!=null&&!region[sy*pw+sx])continue;
            int p=source[sy*pw+sx];
            float lum=(.2126f*((p>>16)&255)+.7152f*((p>>8)&255)+.0722f*(p&255))/255f;
            int band=lum>.78f?0:lum>.40f?1:2;
            n[band]++;if((img.getRGB(x,y)&0xff)<128)cov[band]++;
        }
        for(int b=0;b<3;b++)cov[b]=n[b]==0?Double.NaN:cov[b]/n[b];
        return cov;
    }
    static StyleKit.Result run(int[] p,BanknoteEngravingEngine.Options o){return BanknoteEngravingEngine.generate(p,pw,ph,o);}

    public static void main(String[] a)throws Exception{
        BufferedImage in=ImageIO.read(new File("tests/m4/photos/astronaut.png"));
        pw=240;ph=Math.round(240f*in.getHeight()/in.getWidth());
        BufferedImage img=new BufferedImage(pw,ph,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=img.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(in,0,0,pw,ph,null);g.dispose();
        px=img.getRGB(0,0,pw,ph,null,0,pw);for(int i=0;i<px.length;i++)px[i]|=0xff000000;
        int[] variants={0,1,2,3,4,5,7,8};

        // 1. Cut safety and SVG budget at 3/6/11 in, both minimums, every variant (production defaults).
        for(float inches:new float[]{3f,6f,11f})for(float min:new float[]{.8f,.254f})for(int v:variants){
            BanknoteEngravingEngine.Options o=options(inches*25.4f,min,v,-1f);
            StyleKit.Result r=run(px,o),legacy=run(px,options(inches*25.4f,min,v,0f));
            float gap=StyleCutGeometryPreflight.exportGapMm(o.gapMm,o.minMm);
            int w=StyleCutGeometryPreflight.inspect(r,o.minMm,gap,1000).size();
            int wl=StyleCutGeometryPreflight.inspect(legacy,o.minMm,gap,1000).size();
            int max=0;for(StyleKit.Layer l:r.layers)max=Math.max(max,l.loops.size());
            // Dashes add no finding; pre-existing findings of an unchanged path (Concentric) are reported.
            // Automatic rule: at 0.254 mm only the Highlight dashes variant changes.
            if(min<.3f&&v!=BanknoteEngravingEngine.HIGHLIGHT_DASH)
                check(sameLoops(r,legacy),String.format("%s %.0fin 0.254 mm unchanged from pre-M5",BanknoteEngravingEngine.VARIANTS[v],inches));
            if(min>.5f&&v!=BanknoteEngravingEngine.CONCENTRIC)
                check(!sameLoops(r,legacy),String.format("%s %.0fin 0.8 mm uses highlight dashes",BanknoteEngravingEngine.VARIANTS[v],inches));
            check(w<=wl&&(w==0||sameLoops(r,legacy))&&max<5000,String.format("%s %.0fin min %.3f: %d findings (legacy %d), %d pieces max",
                BanknoteEngravingEngine.VARIANTS[v],inches,min,w,wl,max));
        }
        // 2. Tone mapping at 6 in / 0.8 mm: highlights light, shadows kept.
        for(int v:new int[]{0,1,4}){
            StyleKit.Result legacy=run(px,options(152.4f,.8f,v,0f)),dash=run(px,options(152.4f,.8f,v,.5f));
            double[] cl=bandCoverage(legacy,px,null),cd=bandCoverage(dash,px,null);
            check(cd[0]<cl[0]*.6,String.format("%s highlight coverage %.3f -> %.3f",BanknoteEngravingEngine.VARIANTS[v],cl[0],cd[0]));
            check(cd[2]>=cl[2]*.85,String.format("%s shadow coverage %.3f -> %.3f",BanknoteEngravingEngine.VARIANTS[v],cl[2],cd[2]));
            check(cd[2]-cd[0]>cl[2]-cl[0]+.10,String.format("%s shadow-highlight contrast %.3f -> %.3f",
                BanknoteEngravingEngine.VARIANTS[v],cl[2]-cl[0],cd[2]-cd[0]));
        }
        // 2b. Variant identity: with both diffused at 0.8 mm, Highlight dashes keeps more
        //     highlight dashes than Straight engraving (they must not become identical).
        {
            StyleKit.Result st=run(px,options(152.4f,.8f,BanknoteEngravingEngine.STRAIGHT,-1f));
            StyleKit.Result hd=run(px,options(152.4f,.8f,BanknoteEngravingEngine.HIGHLIGHT_DASH,-1f));
            double[] cs=bandCoverage(st,px,null),ch=bandCoverage(hd,px,null);
            check(!sameLoops(st,hd)&&ch[0]>cs[0]&&ch[1]>cs[1],String.format(
                "Highlight dashes distinct from Straight at 0.8 mm: highlight %.3f vs %.3f, mid %.3f vs %.3f",ch[0],cs[0],ch[1],cs[1]));
        }
        // 3. All-dark photo: every sample is a FIXED ribbon, geometry is identical.
        int[] dark=new int[px.length];Arrays.fill(dark,0xff101010);
        StyleKit.Result d0=run(dark,options(152.4f,.8f,0,0f)),d1=run(dark,options(152.4f,.8f,0,1f));
        check(sameLoops(d0,d1),"all-dark image unchanged by diffusion ("+d0.layers.get(0).loops.size()+" ribbons)");
        // All-white photo: no highlight dashes at all.
        int[] white=new int[px.length];Arrays.fill(white,0xffffffff);
        BanknoteEngravingEngine.Options ow=options(152.4f,.8f,0,1f);ow.autoLevels=false;
        check(run(white,ow).layers.get(0).loops.isEmpty(),"all-white image cuts nothing with diffusion");
        // 4. Circular Snip: coverage inside the retained disc (eroded 4 mm) is unchanged.
        boolean[] allowed=new boolean[px.length],inner=new boolean[px.length];
        float cx=pw*.5f,cy=ph*.42f,rad=pw*.36f,erode=4f*pw/152.4f;
        for(int y=0;y<ph;y++)for(int x=0;x<pw;x++){double d=Math.hypot(x-cx,y-cy);allowed[y*pw+x]=d<rad;inner[y*pw+x]=d<rad-erode;}
        for(int v:new int[]{0,4}){
            BanknoteEngravingEngine.Options full=options(152.4f,.8f,v,.5f),snip=options(152.4f,.8f,v,.5f);
            snip.allowed=allowed;
            // auto levels see different pixels under a Snip; compare with levels off.
            full.autoLevels=false;snip.autoLevels=false;
            double[] cf=bandCoverage(run(px,full),px,inner),cs=bandCoverage(run(px,snip),px,inner);
            double tf=0,ts=0;for(int b=0;b<3;b++){if(!Double.isNaN(cf[b]))tf+=cf[b];if(!Double.isNaN(cs[b]))ts+=cs[b];}
            check(Math.abs(ts-tf)<=.05*Math.max(tf,.05),String.format("%s Snip interior coverage %.3f vs %.3f",
                BanknoteEngravingEngine.VARIANTS[v],ts,tf));
        }
        // 4b. Shape budget: a uniform light-grey 11 in sheet at the finest pitch would need tens of
        //     thousands of dashes; the sheet is regenerated with longer dashes (or the pre-M5
        //     geometry) instead of failing or exceeding the 5,000-shape limit.
        {
            int[] grey=new int[px.length];Arrays.fill(grey,0xffb0b0b0);
            BanknoteEngravingEngine.Options o=options(279.4f,.254f,0,1f);
            o.autoLevels=false;o.pitchMm=1.8f;
            StyleKit.Result r=run(grey,o);
            int n=r.layers.get(0).loops.size();
            check(n>0&&n<=BanknoteEngravingEngine.DashPlan.PIECE_CAP,"dense grey 11 in sheet within shape cap: "+n+" pieces");
        }
        // 5. Invalid options fail closed; output deterministic.
        for(float bad:new float[]{Float.NaN,2f,-2f,Float.POSITIVE_INFINITY}){
            boolean threw=false;
            try{run(px,options(152.4f,.8f,0,bad));}catch(IllegalArgumentException expected){threw=true;}
            check(threw,"highlightDiffusion "+bad+" rejected");
        }
        check(sameLoops(run(px,options(101.6f,.8f,4,-1f)),run(px,options(101.6f,.8f,4,-1f))),"deterministic");
        if(failures>0)throw new AssertionError(failures+" banknote dash failures");
        System.out.println("PASS M5BanknoteDashTest");
    }
    static boolean sameLoops(StyleKit.Result a,StyleKit.Result b){
        if(a.layers.size()!=b.layers.size())return false;
        for(int i=0;i<a.layers.size();i++){
            List<float[]> x=a.layers.get(i).loops,y=b.layers.get(i).loops;
            if(x.size()!=y.size())return false;
            for(int k=0;k<x.size();k++)if(!Arrays.equals(x.get(k),y.get(k)))return false;
        }
        return true;
    }
}

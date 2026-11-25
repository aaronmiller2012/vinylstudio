package com.halftonestudio;

import java.util.Arrays;

/** Regression against the 2.42 bug where 100% photo influence looked uniform. */
public final class AdaptiveCurvePortraitTest {
    private static void ok(boolean value,String msg){if(!value)throw new AssertionError(msg);}
    private static SpaceFillingCurves.Result carrier(int family,int depth){
        SpaceFillingCurves.Options o=new SpaceFillingCurves.Options();
        o.family=family;o.depth=depth;o.width=360;o.height=360;
        return SpaceFillingCurves.generate(o);
    }
    private static int[] image(int w,int h,boolean darkOnLeft){
        int[] pixels=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int c=x<w/2?(darkOnLeft?12:245):(darkOnLeft?245:12);
            pixels[y*w+x]=0xff000000|(c<<16)|(c<<8)|c;
        }
        return pixels;
    }
    private static AdaptiveCurvePortrait.Options controls(int[] pixels,int w,int h,int method,float influence){
        AdaptiveCurvePortrait.Options o=new AdaptiveCurvePortrait.Options();
        o.pixels=pixels;o.width=w;o.height=h;o.method=method;o.influence=influence;
        o.contrast=.75f;o.highlightProtection=1f;o.shadowBoost=.75f;o.edgePriority=.4f;
        return o;
    }
    private static double[] lengthByHalf(SpaceFillingCurves.Result r,float midX){
        double[] lengths=new double[2];
        for(int i=2;i<r.xy.length;i+=2){
            double x=(r.xy[i-2]+r.xy[i])*.5;
            double len=Math.hypot(r.xy[i]-r.xy[i-2],r.xy[i+1]-r.xy[i-1]);
            lengths[x<midX?0:1]+=len;
        }
        return lengths;
    }
    private static void testTonalCoverage(){
        int[] px=image(96,96,true);
        for(int family=0;family<7;family++){
            SpaceFillingCurves.Result base=carrier(family,3);
            AdaptiveCurvePortrait.Options o=controls(px,96,96,AdaptiveCurvePortrait.DENSITY,1f);
            SpaceFillingCurves.Result result=AdaptiveCurvePortrait.render(base,360,360,o);
            double[] lengths=lengthByHalf(result,180f);
            ok(lengths[0]>lengths[1]*1.55,
                "No portrait tonal contrast for "+SpaceFillingCurves.NAMES[family]+": "+Arrays.toString(lengths));
            ok(result.vertices<=AdaptiveCurvePortrait.MAX_OUTPUT_POINTS,"Exceeded output budget");
            ok(result.shortestStep==base.shortestStep,"carrier physical pitch changed");
            for(int q=0;q<result.xy.length;q+=2){
                float xx=result.xy[q],yy=result.xy[q+1];
                ok(Float.isFinite(xx)&&Float.isFinite(yy),"Nonfinite adaptive geometry");
                ok(xx>=-.15f&&xx<=360.15f&&yy>=-.15f&&yy<=360.15f,
                    "Adaptive geometry escaped artwork: family="+family+" x="+xx+" y="+yy);
            }
            SpaceFillingCurves.Result repeat=AdaptiveCurvePortrait.render(base,360,360,o);
            ok(Arrays.equals(repeat.xy,result.xy),"Non-deterministic photo traversal");
            AdaptiveCurvePortrait.Options zero=controls(px,96,96,AdaptiveCurvePortrait.HYBRID,0f);
            ok(AdaptiveCurvePortrait.render(base,360,360,zero)==base,"Image influence 0 must preserve carrier");
        }
    }
    private static void testToneMethodsAndControls(){
        int[] px=image(80,80,true);
        SpaceFillingCurves.Result base=carrier(SpaceFillingCurves.PEANO,4);
        SpaceFillingCurves.Result density=AdaptiveCurvePortrait.render(base,360,360,controls(px,80,80,AdaptiveCurvePortrait.DENSITY,1));
        SpaceFillingCurves.Result fold=AdaptiveCurvePortrait.render(base,360,360,controls(px,80,80,AdaptiveCurvePortrait.FOLD,1));
        SpaceFillingCurves.Result hybrid=AdaptiveCurvePortrait.render(base,360,360,controls(px,80,80,AdaptiveCurvePortrait.HYBRID,1));
        ok(density.vertices<base.vertices,"Bright-region density reduction failed");
        ok(fold.vertices>base.vertices,"Shadow folding failed");
        ok(!Arrays.equals(density.xy,fold.xy)&&!Arrays.equals(density.xy,hybrid.xy),"Methods are redundant");
        AdaptiveCurvePortrait.Options low=controls(px,80,80,AdaptiveCurvePortrait.HYBRID,1);
        low.highlightProtection=0;
        SpaceFillingCurves.Result less=AdaptiveCurvePortrait.render(base,360,360,low);
        ok(less.vertices>density.vertices,"Highlight preservation slider is ineffective");
        low.highlightProtection=1f;low.shadowBoost=0f;
        SpaceFillingCurves.Result noFold=AdaptiveCurvePortrait.render(base,360,360,low);
        ok(noFold.vertices<hybrid.vertices,"Shadow density slider is ineffective");
    }
    private static void testReversePhotoAndMoore(){
        SpaceFillingCurves.Result base=carrier(SpaceFillingCurves.MOORE,4);
        AdaptiveCurvePortrait.Options o=controls(image(96,96,false),96,96,AdaptiveCurvePortrait.HYBRID,1);
        SpaceFillingCurves.Result r=AdaptiveCurvePortrait.render(base,360,360,o);
        double[] lengths=lengthByHalf(r,180f);
        ok(lengths[1]>lengths[0]*1.55,"Photo flip did not move darkness to the right");
        ok(r.closed,"Moore must remain closed");
        ok(Math.hypot(r.xy[0]-r.xy[r.xy.length-2],r.xy[1]-r.xy[r.xy.length-1])<.0001,
            "Moore photographic loop not closed");
    }
    private static void testRibbonPhotographicCoverage(){
        int[] px=image(96,96,true);
        SpaceFillingCurves.Result base=carrier(SpaceFillingCurves.GOSPER,3);
        SpaceFillingCurves.Result path=AdaptiveCurvePortrait.render(base,360,360,
            controls(px,96,96,AdaptiveCurvePortrait.DENSITY,1f));
        SpaceFillingRibbon.Options options=new SpaceFillingRibbon.Options();
        options.artworkWidthMm=200f;options.maximumWidthMm=1.15f;
        options.minimumFeatureMm=.55f;options.minimumGapMm=.40f;
        options.photoInfluence=1f;options.toneContrast=.75f;
        options.highlightProtection=1f;options.image=px;
        options.imageWidth=96;options.imageHeight=96;
        // Validate tonal response as a DRAFT; exact final clearance is checked
        // independently and may reject dense fold/shortcut geometry.
        options.draft=true;
        SpaceFillingRibbon.Result output=SpaceFillingRibbon.generate(path,360,360,options);
        int[] coverage=new int[2];
        for(int y=0;y<output.height;y++)for(int x=0;x<output.width;x++)
            if(output.mask[y*output.width+x])coverage[x<output.width/2?0:1]++;
        ok(coverage[0]>coverage[1]*1.35,
            "Vinyl portrait does not respond to photographic tone: "+Arrays.toString(coverage));
        ok(output.coveredPixels>0&&output.draft,"ribbon draft unavailable");
    }
    public static void main(String[] args){
        testTonalCoverage();testToneMethodsAndControls();testReversePhotoAndMoore();testRibbonPhotographicCoverage();
        System.out.println("PASS v2.43: photo measured by dark/light line-length, seven curve families, continuous adaptive methods, settings, Moore closure, deterministic output");
    }
}

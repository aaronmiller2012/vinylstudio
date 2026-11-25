package com.halftonestudio;

import android.graphics.Bitmap;
import android.graphics.Path;
import java.util.Arrays;

/** Regression for physical sampling and the production single-line wiring. */
public final class SingleLineSamplerV2534Test {
    private static void ok(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    private static int[] photo(int n,boolean stripes){
        int[] a=new int[n*n];
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){
            int c=stripes?((x/2+y/4)%2==0?18:244):(x<n/2?20:235);
            a[y*n+x]=0xff000000|c<<16|c<<8|c;
        }
        return a;
    }
    private static void sampler(){
        int[] a=photo(96,false);
        ScaleAwarePhotoSampler s=new ScaleAwarePhotoSampler(a,96,96,120,120,0xffffffff);
        ok(s.lineDarkness(20,60,0,1,.45f)>.75f,"dark portrait feature vanished");
        ok(s.lineDarkness(100,60,0,1,.45f)<.2f,"bright region became dark");
        // At the boundary, +x is bright and -x is dark -> negative bias.
        ok(s.tonalRibbonBias(60,60,1,0,1.5f)<0,"side light/dark sign reversed");
        int[] opaque=a.clone();opaque[48*96+48]=0x00000000;
        ScaleAwarePhotoSampler alpha=new ScaleAwarePhotoSampler(opaque,96,96,120,120,0xffffffff);
        ok(alpha.lineDarkness(60.5f,60.5f,1,0,.45f)==0,"alpha exclusion failed");
        int[] big=photo(192,false);
        ScaleAwarePhotoSampler scaled=new ScaleAwarePhotoSampler(big,192,192,120,120,0xffffffff);
        ok(Math.abs(s.lineDarkness(20,60,0,1,.45f)-scaled.lineDarkness(20,60,0,1,.45f))<.03f,
            "luminance changed under resampling");
    }
    private static void ribbons(){
        SpaceFillingCurves.Options curve=new SpaceFillingCurves.Options();
        curve.width=400;curve.height=400;curve.depth=1;curve.family=SpaceFillingCurves.GOSPER;
        SpaceFillingCurves.Result carrier=SpaceFillingCurves.generate(curve);
        SpaceFillingRibbon.Options opts=new SpaceFillingRibbon.Options();
        opts.artworkWidthMm=120;opts.maximumWidthMm=1.20f;
        opts.minimumFeatureMm=.55f;opts.minimumGapMm=.4f;
        opts.photoInfluence=1f;opts.imageWidth=96;opts.imageHeight=96;opts.image=photo(96,true);
        opts.independentEdgeSampling=false;
        SpaceFillingRibbon.Result base=SpaceFillingRibbon.generate(carrier,400,400,opts);
        opts.independentEdgeSampling=true;
        SpaceFillingRibbon.Result tuned=SpaceFillingRibbon.generate(carrier,400,400,opts);
        ok(tuned.coveredPixels>100,"tonal ribbon vanished");
        ok(!Arrays.equals(base.mask,tuned.mask),"two-sided photographic modulation had no effect");
        ok(tuned.svg().contains("fill-rule=\"evenodd\""),"silhouette fill missing");
        ok(!tuned.svg().contains("stroke-width"),"ribbon exported as centerline stroke");
        SpaceFillingRibbon.Result repeat=SpaceFillingRibbon.generate(carrier,400,400,opts);
        ok(Arrays.equals(tuned.mask,repeat.mask),"photographic ribbon not deterministic");
        // Clipping to transparent subject must not create vinyl outside alpha.
        int[] mask=opts.image.clone();
        for(int y=0;y<96;y++)for(int x=0;x<30;x++)mask[y*96+x]=0;
        opts.image=mask;
        SpaceFillingRibbon.Result masked=SpaceFillingRibbon.generate(carrier,400,400,opts);
        for(int y=0;y<masked.height;y++)for(int x=0;x<masked.width*.29f;x++)
            ok(!masked.mask[y*masked.width+x],"transparent substrate was covered by vinyl");
    }
    private static void centerlines(){
        Bitmap bitmap=new Bitmap(96,96,photo(96,false));
        for(ContinuousLineModes.LineStyle style:new ContinuousLineModes.LineStyle[]{
            ContinuousLineModes.LineStyle.CONCENTRIC_RINGS,
            ContinuousLineModes.LineStyle.SINE_RASTER,
            ContinuousLineModes.LineStyle.CONTOUR_HATCH,
            ContinuousLineModes.LineStyle.ADAPTIVE_HILBERT,
            ContinuousLineModes.LineStyle.TONAL_SPIROGRAPH}){
            Path path=ContinuousLineModes.generatePath(bitmap,style,45,false,120);
            ok(path.moves==1&&path.lines>40,"broken centerline style: "+style);
        }
        for(int f=0;f<7;f++){
            SpaceFillingCurves.Options o=new SpaceFillingCurves.Options();
            o.width=96;o.height=96;o.depth=2;o.family=f;
            var carrier=SpaceFillingCurves.generate(o);
            AdaptiveCurvePortrait.Options tone=new AdaptiveCurvePortrait.Options();
            tone.width=96;tone.height=96;tone.pixels=photo(96,false);
            tone.artworkWidthMm=120;tone.influence=.85f;
            var rendered=AdaptiveCurvePortrait.render(carrier,96,96,tone);
            ok(rendered.vertices>=2&&rendered.vertices<=AdaptiveCurvePortrait.MAX_OUTPUT_POINTS,
                "recursive centerline lost: "+f);
        }
    }
    public static void main(String[] args){
        sampler();ribbons();centerlines();
        System.out.println("PASS v2.53.4: physical two-sided widths, no alpha cuts, determinism, all active line families");
    }
}

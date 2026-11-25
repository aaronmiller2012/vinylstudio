package com.halftonestudio;

/** End-to-end host reproduction of the v2.43 dark-photo rendering regression. */
public final class CurveRenderRecoveryTest {
    private static void ok(boolean test,String message){if(!test)throw new AssertionError(message);}
    private static int[] photo(int w,int h,boolean leftDark){
        int[] pixels=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int gray=(x<(w*3)/4)==leftDark?15:235;
            pixels[y*w+x]=0xff000000|(gray<<16)|(gray<<8)|gray;
        }
        return pixels;
    }
    private static double darkVsLight(SpaceFillingCurves.Result r,float cutoff){
        double dark=0,light=0;
        for(int i=2;i<r.xy.length;i+=2){
            float x=(r.xy[i-2]+r.xy[i])*.5f;
            double d=Math.hypot(r.xy[i]-r.xy[i-2],r.xy[i+1]-r.xy[i-1]);
            if(x<cutoff)dark+=d;else light+=d;
        }
        return dark/Math.max(1,light);
    }
    private static void testPreviouslyFailingMaximumDepth(){
        int w=850,h=850;
        int[] pixels=photo(w,h,true);
        for(int family=0;family<SpaceFillingCurves.NAMES.length;family++){
            SpaceFillingCurves.Options g=new SpaceFillingCurves.Options();
            g.family=family;g.depth=5;g.width=w;g.height=h;g.photoInfluence=0f;
            SpaceFillingCurves.Result carrier=SpaceFillingCurves.generate(g);
            AdaptiveCurvePortrait.Options c=new AdaptiveCurvePortrait.Options();
            c.pixels=pixels;c.width=w;c.height=h;c.influence=1f;c.method=AdaptiveCurvePortrait.HYBRID;
            c.shadowBoost=.9f;c.highlightProtection=1f;c.contrast=.75f;
            SpaceFillingCurves.Result r=AdaptiveCurvePortrait.render(carrier,w,h,c);
            ok(r.vertices>1,"empty path: "+family);
            ok(r.vertices<=Math.max(carrier.vertices,AdaptiveCurvePortrait.DEVICE_POINT_BUDGET),
                "exceeded bounded preview output: "+family+" / "+r.vertices);
            ok(r.vertices<=AdaptiveCurvePortrait.MAX_OUTPUT_POINTS,"exceeded absolute point safety budget");
            ok(r.xy[0]==carrier.xy[0]&&r.xy[1]==carrier.xy[1],"lost start vertex");
            ok(r.xy[r.xy.length-2]==carrier.xy[carrier.xy.length-2]&&
                r.xy[r.xy.length-1]==carrier.xy[carrier.xy.length-1],"lost end vertex");
            if(family==SpaceFillingCurves.PEANO||family==SpaceFillingCurves.WUNDERLICH||
               family==SpaceFillingCurves.COIL||family==SpaceFillingCurves.MEURTHE){
                ok(r.vertices<carrier.vertices*2,"maximum-depth path not bounded for family "+family);
            }
        }
    }
    private static void testTonalResponseStillPresent(){
        SpaceFillingCurves.Options g=new SpaceFillingCurves.Options();
        g.family=SpaceFillingCurves.PEANO;g.depth=5;g.width=850;g.height=850;
        SpaceFillingCurves.Result carrier=SpaceFillingCurves.generate(g);
        AdaptiveCurvePortrait.Options c=new AdaptiveCurvePortrait.Options();
        c.pixels=photo(850,850,true);c.width=850;c.height=850;c.influence=1f;c.method=AdaptiveCurvePortrait.HYBRID;
        c.shadowBoost=.85f;c.contrast=.7f;c.highlightProtection=1f;
        SpaceFillingCurves.Result r=AdaptiveCurvePortrait.render(carrier,850,850,c);
        ok(darkVsLight(r,850*.75f)>2.0,"adaptive highlights not simplified enough to show portrait");
        c.draft=true;SpaceFillingCurves.Result draft=AdaptiveCurvePortrait.render(carrier,850,850,c);
        ok(draft.vertices<=Math.max(carrier.vertices,AdaptiveCurvePortrait.DRAFT_POINT_BUDGET),"draft geometry exploded");
        ok(draft.vertices>1,"draft lost geometry");
    }
    public static void main(String[] args){
        testPreviouslyFailingMaximumDepth();testTonalResponseStillPresent();
        System.out.println("PASS v2.44: all curve families render at max-depth dark photo; bounded size, endpoints, draft, photographic contrast");
    }
}

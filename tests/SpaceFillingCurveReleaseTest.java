package com.halftonestudio;
import java.util.Arrays;
import java.util.HashSet;
/** Deterministic source regression gate for advanced curve families and closed vinyl ribbons. */
public final class SpaceFillingCurveReleaseTest {
    private static void ok(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
    private static SpaceFillingCurves.Options options(int family,int depth){
        SpaceFillingCurves.Options o=new SpaceFillingCurves.Options();
        o.family=family;o.depth=depth;o.width=400;o.height=320;
        return o;
    }
    private static void testDistinctFamilies(){
        HashSet<Integer> fingerprints=new HashSet<>();
        for(int family=0;family<7;family++){
            SpaceFillingCurves.Options o=options(family,3);
            SpaceFillingCurves.Result result=SpaceFillingCurves.generate(o);
            ok(result.family==family,"wrong family metadata");
            ok(result.vertices>=16,"family too small: "+family);
            ok(result.xy.length==(long)result.vertices*2,"broken point buffer");
            for(int i=0;i<result.xy.length;i+=2){
                ok(Float.isFinite(result.xy[i])&&Float.isFinite(result.xy[i+1]),"nonfinite geometry");
                ok(result.xy[i]>=-0.1f&&result.xy[i]<=o.width+.1f&&result.xy[i+1]>=-.1f&&result.xy[i+1]<=o.height+.1f,"path outside artwork");
            }
            ok(fingerprints.add(Arrays.hashCode(result.xy)),"same curve from different style: "+family);
            boolean expectClosed=family==SpaceFillingCurves.MOORE;
            ok(result.closed==expectClosed,"incorrect Moore loop state");
            if(expectClosed){int end=result.xy.length-2;
                ok(Math.hypot(result.xy[0]-result.xy[end],result.xy[1]-result.xy[end+1])<.0001f,"Moore loop is open");
            }
            SpaceFillingCurves.Result again=SpaceFillingCurves.generate(o);
            ok(Arrays.equals(result.xy,again.xy),"nondeterministic curve: "+family);
        }
    }
    private static void testTernaryCoverage(){
        for(int family:new int[]{SpaceFillingCurves.PEANO,SpaceFillingCurves.WUNDERLICH,
            SpaceFillingCurves.COIL,SpaceFillingCurves.MEURTHE}){
            for(int depth=1;depth<=4;depth++){
                SpaceFillingCurves.Options o=options(family,depth);o.width=600;o.height=600;
                SpaceFillingCurves.Result r=SpaceFillingCurves.generate(o);
                int width=(int)Math.round(Math.pow(3,depth));
                ok(r.vertices==width*width,"3x3 recursion must visit every vertex once: "+family+","+depth+","+r.vertices);
                HashSet<String> unique=new HashSet<>();
                for(int i=0;i<r.xy.length;i+=2){
                    String key=Math.round(r.xy[i]*10000)+"/"+Math.round(r.xy[i+1]*10000);
                    ok(unique.add(key),"duplicate grid vertex in family "+family);
                    if(i>0){double dist=Math.hypot(r.xy[i]-r.xy[i-2],r.xy[i+1]-r.xy[i-1]);
                        ok(Math.abs(dist-r.shortestStep)<.0003*r.shortestStep,"disconnected traversal in family "+family+" depth "+depth);}
                }
            }
        }
    }
    private static void testControlsActuallyChangeGeometry(){
        SpaceFillingCurves.Options o=options(SpaceFillingCurves.GOSPER,3);
        SpaceFillingCurves.Result base=SpaceFillingCurves.generate(o);
        o.rotationDegrees=45;
        SpaceFillingCurves.Result rotated=SpaceFillingCurves.generate(o);
        ok(!Arrays.equals(base.xy,rotated.xy),"rotation control inert");
        o.rotationDegrees=0;o.cornerRounding=.85f;
        SpaceFillingCurves.Result rounded=SpaceFillingCurves.generate(o);
        ok(!Arrays.equals(base.xy,rounded.xy),"rounding control inert");
        int[] pixels=new int[64*64];
        for(int y=0;y<64;y++)for(int x=0;x<64;x++)pixels[y*64+x]=(x<32?0xff101010:0xffeeeeee);
        o.cornerRounding=0;o.pixels=pixels;o.imageWidth=64;o.imageHeight=64;o.photoInfluence=.9f;
        SpaceFillingCurves.Result influenced=SpaceFillingCurves.generate(o);
        ok(!Arrays.equals(base.xy,influenced.xy),"photo influence control inert");
        o.depth=5;o.family=SpaceFillingCurves.PEANO;o.pixels=null;
        ok(SpaceFillingCurves.generate(o).vertices==59049,"highest practical Peano recursion wrong");
    }
    private static void testRasterUnionRibbon(){
        SpaceFillingCurves.Options o=options(SpaceFillingCurves.GOSPER,2);
        SpaceFillingCurves.Result r=SpaceFillingCurves.generate(o);
        SpaceFillingRibbon.Options cut=new SpaceFillingRibbon.Options();
        cut.artworkWidthMm=120f;cut.maximumWidthMm=1.25f;
        cut.minimumFeatureMm=.5f;cut.minimumGapMm=.5f;
        SpaceFillingRibbon.Result vinyl=SpaceFillingRibbon.generate(r,o.width,o.height,cut);
        ok(vinyl.coveredPixels>100,"missing merged vinyl areas");
        ok(vinyl.pixelMm<=.17f,"insufficient physical SVG precision");
        String svg=vinyl.svg();
        ok(svg.contains("fill-rule=\"evenodd\""),"compound contour fill missing");
        ok(svg.contains("mm\""),"physical millimeters not exported");
        ok(!svg.contains("<image")&&!svg.contains("<clipPath")&&!svg.contains("<mask"),"unsupported SVG data");
        ok(svg.contains("Z\"/>")&&!svg.contains("stroke-width"),"vinyl SVG is an open stroke");
        cut.draft=true;
        SpaceFillingRibbon.Result preview=SpaceFillingRibbon.generate(r,o.width,o.height,cut);
        ok(preview.width<=580&&preview.height<=580,"draft raster not bounded");
        boolean guarded=false;try{preview.svg();}catch(IllegalStateException ex){guarded=true;}
        ok(guarded,"draft SVG export must be forbidden");cut.draft=false;
        cut.imageWidth=100;cut.imageHeight=80;cut.image=new int[cut.imageWidth*cut.imageHeight];
        for(int y=0;y<cut.imageHeight;y++)for(int x=0;x<cut.imageWidth;x++)
            cut.image[y*cut.imageWidth+x]=x<50?0x00000000:0xff333333;
        SpaceFillingRibbon.Result transparent=SpaceFillingRibbon.generate(r,o.width,o.height,cut);
        for(int y=0;y<transparent.height;y++)for(int x=0;x<transparent.width/2-3;x++)
            ok(!transparent.mask[y*transparent.width+x],"transparent photo background became cut vinyl");
        cut.image=null;
        o.depth=5;o.family=SpaceFillingCurves.PEANO;
        r=SpaceFillingCurves.generate(o);
        boolean rejected=false;
        try{SpaceFillingRibbon.generate(r,o.width,o.height,cut);}catch(IllegalArgumentException ex){rejected=ex.getMessage().contains("pitch");}
        ok(rejected,"too-dense curve was not rejected for vinyl cutting");
    }
    public static void main(String[] args){
        testDistinctFamilies();testTernaryCoverage();testControlsActuallyChangeGeometry();testRasterUnionRibbon();
        System.out.println("PASS v2.42: seven distinct deterministic curves, 3x3 Hamiltonian coverage, closed Moore, functional settings, raster-union cuttable SVG, physical pitch safety");
    }
}

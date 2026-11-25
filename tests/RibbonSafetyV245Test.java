package com.halftonestudio;
/** Physical ribbon regression: mat-sized raster, post-modulation clearance, draft/export. */
public final class RibbonSafetyV245Test {
    private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    private static SpaceFillingCurves.Result carrier(){
        SpaceFillingCurves.Options o=new SpaceFillingCurves.Options();
        o.family=SpaceFillingCurves.GOSPER;o.depth=1;o.width=400;o.height=400;o.photoInfluence=0;
        return SpaceFillingCurves.generate(o);
    }
    private static SpaceFillingRibbon.Options settings(float width){
        SpaceFillingRibbon.Options o=new SpaceFillingRibbon.Options();
        o.artworkWidthMm=width;o.maximumWidthMm=.9f;o.minimumFeatureMm=.55f;o.minimumGapMm=.45f;
        return o;
    }
    private static void testMatSizes(){
        SpaceFillingCurves.Result line=carrier();
        for(float width:new float[]{199f,200f,250f,292f}){
            SpaceFillingRibbon.Result r=SpaceFillingRibbon.generate(line,400,400,settings(width));
            check(r.coveredPixels>0,"blank vinyl at "+width);
            check(r.width<=SpaceFillingRibbon.MAX_RASTER_SIDE,"raster cap at "+width);
            check((long)r.width*r.height<=SpaceFillingRibbon.MAX_RASTER_PIXELS,"budget cap mismatch at "+width);
            check(r.widthMm==width,"incorrect physical SVG width");
        }
    }
    private static void testRealClearance(){
        // An actual folded path returns near its earlier stroke. The original
        // carrier's shortestStep cannot see this non-adjacent close approach.
        float[] folded={0,0,10,0,10,10,0,10,0,.45f,10,.45f};
        float[] widths={.25f,.25f,.25f,.25f,.25f,.25f};
        boolean rejected=false;
        try{RibbonClearance.verify(folded,1,widths,1f,.35f,.5f);}
        catch(IllegalArgumentException expected){rejected=expected.getMessage().contains("too close");}
        check(rejected,"final folded centerline escaped physical clearance check");
        float[] open={0,0,10,0,10,10,20,10};
        RibbonClearance.verify(open,1,new float[]{.2f,.2f,.2f,.2f},1f,.4f,.4f);
    }
    private static void testDraft(){
        var o=settings(292f);o.draft=true;
        var r=SpaceFillingRibbon.generate(carrier(),400,400,o);
        check(r.draft&&r.width<=580,"draft not bounded");
        boolean prohibited=false;
        try{r.svg();}catch(IllegalStateException e){prohibited=true;}
        check(prohibited,"draft SVG export permitted");
    }
    public static void main(String[] args){
        testMatSizes();testRealClearance();testDraft();
        System.out.println("PASS v2.45: 199/200/250/292 mm ribbons, actual centerline clearance, draft export block");
    }
}

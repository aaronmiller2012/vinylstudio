package com.halftonestudio;

import java.util.Arrays;
/** Regression reproductions from the 2026-11-16 Wolfram math audit. */
public final class V3MathRepairTest {
    private static void ok(boolean cond,String failure){if(!cond)throw new AssertionError(failure);}
    private static float[] rect(float a,float b,float c,float d){return new float[]{a,b,c,b,c,d,a,d};}
    private static int[] uniform(int w,int h,int color){int[] x=new int[w*h];Arrays.fill(x,color);return x;}
    private static void overlayTranslation(){
        int w=100,h=100;
        int[] p=uniform(w,h,0xffff1010);
        int[] colors={0xff101010,0xffff1010};
        float[][] marks={rect(39,39,47,47),null,null};
        int[] first=OpaqueOverlaySwatchPlanner.choose(p,null,w,h,0,0,w,h,marks,colors,2,.95f);
        ok(first[0]==1,"Valid accent at missed 7x7 sample centers was discarded: "+Arrays.toString(first));
        marks[0]=rect(46,46,54,54);
        int[] second=OpaqueOverlaySwatchPlanner.choose(p,null,w,h,0,0,w,h,marks,colors,2,.95f);
        ok(second[0]==first[0],"Translation changed same-area filled mark's owner");
        boolean[] allowed=new boolean[w*h];
        ok(OpaqueOverlaySwatchPlanner.choose(p,allowed,w,h,0,0,w,h,marks,colors,2,.95f)[0]==0,
            "Fully snipped-out accent acquired vinyl layer");
        Arrays.fill(allowed,false);
        for(int y=46;y<54;y++)for(int x=46;x<54;x++)allowed[y*w+x]=true;
        ok(OpaqueOverlaySwatchPlanner.choose(p,allowed,w,h,0,0,w,h,marks,colors,2,.95f)[0]==1,
            "Snip-valid narrow accent incorrectly discarded");
        int[] transparent=p.clone();for(int y=46;y<54;y++)for(int x=46;x<54;x++)transparent[y*w+x]&=0x00ffffff;
        ok(OpaqueOverlaySwatchPlanner.choose(transparent,null,w,h,0,0,w,h,marks,colors,2,.95f)[0]==0,
            "Fully transparent accent acquired vinyl layer");
        System.out.println("PASS M01 translated, Snip-clipped and transparent overprint marks");
    }
    private static void snipNonFinite(){
        SnipMask empty=new SnipMask();
        SnipMask removal=new SnipMask();
        removal.add(new SnipMask.Operation(SnipMask.REMOVE_INSIDE,.2f,.2f,.3f,.2f,.3f,.3f));
        for(SnipMask s:new SnipMask[]{empty,removal}){
            for(float x:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}){
                ok(!s.allows(x,.5f),"Snip accepted nonfinite x");
                ok(!s.allows(.5f,x),"Snip accepted nonfinite y");
            }
            ok(!s.safeSegment(Float.NaN,0,1,1,100,100),"Snip segment accepted NaN");
            ok(!s.allowsPolygon(rect(0,0,1,Float.NaN),100,100),"Snip polygon accepted NaN");
            ok(!s.allowsCircle(.5f,.5f,Float.POSITIVE_INFINITY,100,100),"Snip circle accepted infinity");
            ok(s.clearanceMm(Float.NaN,10,100,100)==0,"Snip distance accepted NaN");
            ok(s.clearanceWithinMm(10,10,100,100,Float.NaN)==0,"Snip local distance accepted NaN");
            ok(s.allows(.5f,.5f),"Ordinary normalized point rejected");
        }
        System.out.println("PASS M02 finite Snip coordinate requirements");
    }
    private static void cropRounding(){
        int[] box=PhotoCropMath.pixelBounds(new float[]{.10049f,.25f,.20098f,.75f},1000,1000);
        ok(box[0]==100&&box[2]==101,"Crop has inconsistent independent edge rounding: "+Arrays.toString(box));
        float[] normalized=PhotoCropMath.normalizedPixelBounds(box,1000,1000);
        int[] roundTrip=PhotoCropMath.pixelBounds(normalized,1000,1000);
        ok(Arrays.equals(box,roundTrip),"Actual crop bitmap and reported metadata disagree");
        ok(PhotoCropMath.pixelBounds(new float[]{.9999f,.999f,1,1},1000,1000)[2]>0,
            "Edge-clamped crop has zero width");
        System.out.println("PASS M04 exact integer crop edges and normalized metadata");
    }
    private static void extremeAspect(){
        int[][] source={{100,5000},{5000,100},{600,900},{150,150},{5,3000},{3000,5}};
        for(int[] size:source){
            float[] c=PhotoCropMath.fitAspect(new float[]{0,0,1,1},size[0],size[1],1);
            double physicalRatio=(c[2]-c[0])*size[0]/((c[3]-c[1])*size[1]);
            ok(Math.abs(physicalRatio-1)<.0002,"Square crop lost aspect on "+Arrays.toString(size)+" ratio="+physicalRatio);
            for(float v:c)ok(Float.isFinite(v)&&v>=0&&v<=1,"Invalid crop bound");
            float[] min=PhotoCropMath.minimumSpan(size[0],size[1],1);
            ok(Math.abs(min[0]*size[0]-min[1]*size[1])<.001,"Aspect broken by drag minimum");
        }
        float[] tall=PhotoCropMath.fitAspect(new float[]{0,0,1,1},100,5000,1);
        ok(Math.abs(tall[3]-tall[1]-.02f)<.00001,"Tall image crop expanded to invalid 7% minimum");
        System.out.println("PASS M05 aspect-lock on extreme image ratios and resize constraints");
    }
    private static void matEnvelopes(){
        CricutSizeAdvisor.Estimate invalid=CricutSizeAdvisor.estimate(11,1,1,true,2.5f,1f,8f,false);
        ok(Math.abs(invalid.sheetWidthMm-295.4)<.005,"Registration sheet envelope arithmetic changed");
        ok(!invalid.fits12x12&&!invalid.fits12x24,"11-inch art plus marks incorrectly fits Cricut mat");
        double max=CricutSizeAdvisor.maximumWidthIn(false,1,1,true,2.5f,1f,8f,false);
        ok(max>10.86&&max<10.88,"Expected roughly 10.870in safe max, got "+max);
        ok(CricutSizeAdvisor.estimate(max,1,1,true,2.5f,1f,8f,false).fits12x12,
            "Advertised 12x12 width is actually too large");
        ok(CricutSizeAdvisor.estimate(10.5,1,1,true,2.5f,1f,8f,false).fits12x12,
            "Viable 12x12 art incorrectly rejected");
        ok(Math.abs(CricutSizeAdvisor.estimate(10.5,1,1,false,2.5f,1f,8f,false).sheetWidthMm-(10.5*25.4+16))<.0001,
            "Unregistered art lost its actual outer-spacing padding");
        System.out.println("PASS M03 12x12/12x24 envelope and useful size advice");
    }
    public static void main(String[] unused){
        overlayTranslation();snipNonFinite();cropRounding();extremeAspect();matEnvelopes();
    }
}

package com.halftonestudio;
import java.util.concurrent.CancellationException;
import java.util.Arrays;
public final class SpiralPortraitRibbonV2532Test {
    static void ok(boolean x,String message){if(!x)throw new AssertionError(message);}
    static int[] portrait(int w,int h,boolean mask){
        int[] image=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float nx=(x-w/2f)/(w*.5f),ny=(y-h/2f)/(h*.5f);
            boolean hair=ny<-.28f && nx*nx<.38f;
            boolean eyes=(ny>-.21f&&ny<-.14f&&Math.abs(Math.abs(nx)-.27f)<.08f);
            boolean nose=ny>-.12f&&ny<.30f&&Math.abs(nx)<.055f;
            boolean mouth=ny>.34f&&ny<.39f&&Math.abs(nx)<.22f;
            int tone=(hair||eyes||nose||mouth)?28:(int)(235-65*Math.max(0f,1f-nx*nx-ny*ny));
            int a=mask&&Math.abs(nx)<.05&&Math.abs(ny)<.05?0:255;
            image[y*w+x]=(a<<24)|(tone<<16)|(tone<<8)|tone;
        }
        return image;
    }
    static SpiralPortraitRibbon.Options opts(){SpiralPortraitRibbon.Options o=new SpiralPortraitRibbon.Options();o.widthMm=150;o.detail=62;o.minCutMm=.8f;o.minGapMm=.5f;return o;}
    public static void main(String[] args){
        SpiralPortraitRibbon.Options o=opts();
        SpiralPortraitRibbon.Result r=SpiralPortraitRibbon.generate(portrait(420,420,false),420,420,o);
        ok(r.samples>1000,"insufficient samples");
        ok(r.cutPath.moves==1&&r.cutPath.closes==1&&r.cutPath.quads>10,"not one smooth closed path");
        ok(r.observedMinWidthMm>=o.minCutMm-.001f,"ribbon narrower than min width");
        ok(r.observedMaxWidthMm<=r.pitchMm-r.gapMm+.001f,"ribbon exceeds maximum width allowed by turn gap");
        ok(r.observedMaxWidthMm-r.observedMinWidthMm>.25f,"spiral has no photographic width modulation");
        String svg=r.svg();
        ok(svg.contains("<path fill=\"#171717\" d=\"M")&&svg.contains("Q")&&svg.contains("Z\"/>")&&
           !svg.contains("stroke=")&&!svg.contains("<clipPath"),"invalid filled spiral SVG");
        SpiralPortraitRibbon.Result different=SpiralPortraitRibbon.generate(portrait(840,840,false),840,840,o);
        ok(Math.abs(different.pitchMm-r.pitchMm)<.00001f,"pitch depends on preview raster resolution");
        ok(Math.abs(different.observedMaxWidthMm-r.observedMaxWidthMm)<.05f,"scale changes physical widths");
        SpiralPortraitRibbon.Result tall=SpiralPortraitRibbon.generate(portrait(360,480,false),360,480,o);
        ok(Math.abs(tall.heightMm-200f)<.001f,"portrait aspect ratio lost");
        SpiralPortraitRibbon.Result masked=SpiralPortraitRibbon.generate(portrait(420,420,true),420,420,o);
        ok(masked.hasTransparentMask,"transparent cutouts must be recognized");
        ok(masked.cutPieces>=2&&masked.cutPath.moves==masked.cutPieces&&
            masked.cutPath.closes==masked.cutPieces,"transparent cutouts must split into closed cut regions");
        ok(masked.svg().contains("Z"),"masked output must contain closed SVG paths");
        boolean rejected=false;Thread.currentThread().interrupt();try{SpiralPortraitRibbon.generate(portrait(420,420,false),420,420,o);}catch(CancellationException e){rejected=true;}finally{Thread.interrupted();}
        ok(rejected,"cancellation not honored");
        System.out.println("PASS spiral: independent photo widths, opaque continuous ribbon, masked cut pieces, scale invariance, cancellation");
        System.out.println("points="+r.samples+" widthRangeMm="+r.observedMinWidthMm+".."+r.observedMaxWidthMm+" pitchMm="+r.pitchMm+" svgBytes="+svg.length());
    }
}

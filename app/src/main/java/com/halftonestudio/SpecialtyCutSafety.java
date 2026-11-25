package com.halftonestudio;

import android.graphics.Bitmap;
import android.graphics.Color;

/** Shared source preparation and conservative export validation. */
public final class SpecialtyCutSafety {
    private SpecialtyCutSafety() {}
    /** Source has already been reframed by PhotoCropEditor. The returned copy
     * retains its pixel coordinates; masked areas become transparent white.
     * Individual continuous paths may NOT treat this mask as a physical snip. */
    public static Bitmap prepare(Bitmap source, SnipMask snips, boolean circle) {
        if(source==null||source.isRecycled())throw new IllegalArgumentException("Missing photograph");
        int w=source.getWidth(),h=source.getHeight();
        if((long)w*h>16000000L)throw new IllegalArgumentException("Image too large for specialty engine");
        if(!circle&&(snips==null||snips.isEmpty()))return source;
        int[] pixels=new int[w*h];source.getPixels(pixels,0,w,0,0,w,h);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float nx=(x+0.5f)/w,ny=(y+0.5f)/h;
            boolean allowed=!circle||4f*((nx-.5f)*(nx-.5f)+(ny-.5f)*(ny-.5f))<=1f;
            if(allowed&&snips!=null)allowed=snips.allows(nx,ny);
            if(!allowed)pixels[y*w+x]=Color.TRANSPARENT;
        }
        return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
    }
    public static void validateSingleSvg(String svg){validateSingleSvg(svg,OutputDeviceProfile.CRICUT);}
    public static void validateSingleSvg(String svg,int outputDeviceId){
        if(svg==null||!svg.contains("<svg")||!svg.contains("<path")||!svg.contains("fill=\"none\""))
            throw new IllegalArgumentException("Missing open centerline SVG geometry");
        if(outputDeviceId==OutputDeviceProfile.CRICUT)CutSvgCompatibility.verify(svg);
        else CutSvgCompatibility.verify(svg,outputDeviceId);
        int count=count(svg,"<path");
        if(count!=1)throw new IllegalArgumentException("Expected exactly one continuous toolpath");
        if(!svg.contains(" d=\"M"))throw new IllegalArgumentException("Toolpath has no starting move");
    }
    /** Dedicated single-line vinyl mode: pen strokes must never masquerade as closed cut silhouettes. */
    public static void validateVinylRibbonSvg(String svg){validateVinylRibbonSvg(svg,OutputDeviceProfile.CRICUT);}
    public static void validateVinylRibbonSvg(String svg,int outputDeviceId){
        validateLayerSvg(svg,outputDeviceId);
        if(svg.contains("fill=\"none\"")||svg.contains("stroke=\"")||
           svg.contains("<line ")||svg.contains("<polyline "))
            throw new IllegalArgumentException("Vinyl export contains pen/engraving strokes");
        // Union contour export is expected to be closed, not a centerline.
        if(!svg.matches("(?s).*\\b[Zz]\\b.*"))
            throw new IllegalArgumentException("Vinyl export contains no closed cut outline");
    }
    public static void validateLayerSvg(String svg){validateLayerSvg(svg,OutputDeviceProfile.CRICUT);}
    public static void validateLayerSvg(String svg,int outputDeviceId){
        if(svg==null||!svg.contains("<svg")||!svg.contains("<path"))
            throw new IllegalArgumentException("Blank SVG stencil layer");
        if(outputDeviceId==OutputDeviceProfile.CRICUT)CutSvgCompatibility.verify(svg);
        else CutSvgCompatibility.verify(svg,outputDeviceId);
    }
    private static int count(String s,String sub){int n=0,p=0;while((p=s.indexOf(sub,p))>=0){n++;p+=sub.length();}return n;}
}

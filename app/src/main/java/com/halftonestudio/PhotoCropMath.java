package com.halftonestudio;

/** Android-independent crop geometry.  Normalized bounds are in photo coordinates;
 * aspect ratios and minimum visible sizes are measured in PHOTO PIXELS.
 * This avoids the old 7%-of-each-axis rule distorting tall/wide photographs.
 */
public final class PhotoCropMath {
    private PhotoCropMath() {}
    private static double clamp(double x,double low,double high){
        return Math.max(low,Math.min(high,x));
    }
    /** Return a centered and photo-bounded crop of the desired physical ratio. */
    public static float[] fitAspect(float[] prior,int imageWidth,int imageHeight,float targetRatio){
        if(prior==null||prior.length!=4||imageWidth<1||imageHeight<1||
            !Float.isFinite(targetRatio)||targetRatio<=0)
            throw new IllegalArgumentException("Invalid aspect-lock crop geometry");
        double normRatio=(double)targetRatio*imageHeight/imageWidth;
        if(!Double.isFinite(normRatio)||normRatio<=0)
            throw new IllegalArgumentException("Invalid aspect-lock ratio");
        double left=Float.isFinite(prior[0])?clamp(prior[0],0,1):0;
        double top=Float.isFinite(prior[1])?clamp(prior[1],0,1):0;
        double right=Float.isFinite(prior[2])?clamp(prior[2],0,1):1;
        double bottom=Float.isFinite(prior[3])?clamp(prior[3],0,1):1;
        double cx=clamp((left+right)*.5,0,1),cy=clamp((top+bottom)*.5,0,1);
        double width=Math.max(0,right-left),height=Math.max(0,bottom-top);
        if(width<1e-9||height<1e-9){width=1;height=1;}
        // Never grow one normalized dimension independently: that would
        // destroy the requested width:height ratio on extreme source images.
        if(width/height>normRatio)width=height*normRatio;
        else height=width/normRatio;
        double minPixelsX=Math.min(8,imageWidth),minPixelsY=Math.min(8,imageHeight);
        double grow=Math.max(1,Math.max(minPixelsX/(width*imageWidth),
                                        minPixelsY/(height*imageHeight)));
        width*=grow;height*=grow;
        double fit=Math.min(1,Math.min(1/width,1/height));
        width*=fit;height*=fit;
        cx=clamp(cx,width*.5,1-width*.5);
        cy=clamp(cy,height*.5,1-height*.5);
        return new float[]{(float)(cx-width*.5),(float)(cy-height*.5),
                           (float)(cx+width*.5),(float)(cy+height*.5)};
    }
    /** Minimum constrained drag spans, expressed in normalized coordinates. */
    public static float[] minimumSpan(int w,int h,float aspect){
        if(w<1||h<1||!Float.isFinite(aspect)||aspect<=0)
            throw new IllegalArgumentException("Invalid crop drag constraints");
        double n=(double)aspect*h/w;
        double minimumWidth=Math.max(Math.min(8,w)/(double)w,
                                    n*Math.min(8,h)/(double)h);
        double width=Math.min(Math.min(1,n),minimumWidth);
        return new float[]{(float)width,(float)(width/n)};
    }
    /** Integer half-open pixel edges are rounded individually, never by
     * independently rounding the width, which could disagree by one pixel. */
    public static int[] pixelBounds(float[] normalized,int w,int h){
        if(normalized==null||normalized.length!=4||w<1||h<1)
            throw new IllegalArgumentException("Invalid crop pixel bounds");
        for(float v:normalized)if(!Float.isFinite(v))
            throw new IllegalArgumentException("Non-finite crop boundary");
        int x0=Math.max(0,Math.min(w-1,Math.round(normalized[0]*w)));
        int y0=Math.max(0,Math.min(h-1,Math.round(normalized[1]*h)));
        int x1=Math.max(x0+1,Math.min(w,Math.round(normalized[2]*w)));
        int y1=Math.max(y0+1,Math.min(h,Math.round(normalized[3]*h)));
        return new int[]{x0,y0,x1-x0,y1-y0};
    }
    public static float[] normalizedPixelBounds(int[] rect,int w,int h){
        if(rect==null||rect.length!=4||w<1||h<1||rect[0]<0||rect[1]<0||
           rect[2]<1||rect[3]<1||rect[0]+rect[2]>w||rect[1]+rect[3]>h)
            throw new IllegalArgumentException("Invalid integer photo crop");
        return new float[]{rect[0]/(float)w,rect[1]/(float)h,
            (rect[0]+rect[2])/(float)w,(rect[1]+rect[3])/(float)h};
    }
}

package com.halftonestudio;
/** Physical units for raster stencil cleanup; area is invariant under resizing. */
public final class PhysicalFeatureRules {
    private PhysicalFeatureRules() {}
    public static int minimumIslandPixels(float minimumAreaMm2,int width,int height,float widthMm,float heightMm){
        if(!Float.isFinite(minimumAreaMm2)||minimumAreaMm2<0||!Float.isFinite(widthMm)||!Float.isFinite(heightMm)||widthMm<=0||heightMm<=0||width<1||height<1)
            throw new IllegalArgumentException("Invalid physical feature dimensions");
        double area=(double)minimumAreaMm2*width*height/((double)widthMm*heightMm);
        return (int)Math.max(0,Math.min(10000,Math.ceil(area)));
    }
}

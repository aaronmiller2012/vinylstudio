package com.halftonestudio;

/**
 * Conservative outer-background attenuation for CMYK portrait screening.
 * No AI segmentation or permanent masking: central face region is protected.
 * Only bright, locally smooth colors that resemble upper/side photo borders
 * are downweighted. Never changes source pixels, Snips, or SVG boundaries.
 */
public final class PortraitBackgroundGuard {
    private PortraitBackgroundGuard() {}
    public static float scale(int[] src,int w,int h,float x,float y,
            float widthMm,float heightMm,int paper,float amount){
        if(amount<=.001f || w<12 || h<12)return 1f;
        float u=x/widthMm,v=y/heightMm;
        // Broad protected oval includes eyes, temples, jaw, beard and chest.
        float dx=(u-.5f)/.35f,dy=(v-.50f)/.51f;
        float dist=(float)Math.sqrt(dx*dx+dy*dy);
        if(dist<=1f)return 1f;
        int ix=Math.min(w-1,Math.max(0,(int)(u*w)));
        int iy=Math.min(h-1,Math.max(0,(int)(v*h)));
        int pixel=src[iy*w+ix];
        if(((pixel>>>24)&255)<240)return 1f; // transparent/paper already excluded by separation
        float lum=lum(pixel);
        if(lum<.51f)return 1f;
        // Source border samples are small enough not to include the central portrait.
        // Fixed cardinal samples avoid costly per-pixel flood fill and leakage
        // across low-contrast skin/background boundaries.
        int[] borders={src[Math.min(h-1,h/12)*w+Math.min(w-1,w/12)],
                src[Math.min(h-1,h/12)*w+Math.min(w-1,w*11/12)],
                src[Math.min(h-1,h/3)*w+Math.min(w-1,w/25)],
                src[Math.min(h-1,h/3)*w+Math.min(w-1,w*24/25)]};
        float similarity=0f;
        for(int rgb:borders){
            float d=distance(pixel,rgb);
            similarity=Math.max(similarity,Math.max(0f,Math.min(1f,(90f-d)/65f)));
        }
        if(similarity<=0f)return 1f;
        int radiusX=Math.max(1,w/180),radiusY=Math.max(1,h/180);
        float gradient=Math.abs(lum(src[iy*w+Math.min(w-1,ix+radiusX)])
                 -lum(src[iy*w+Math.max(0,ix-radiusX)]))
                 +Math.abs(lum(src[Math.min(h-1,iy+radiusY)*w+ix])
                 -lum(src[Math.max(0,iy-radiusY)*w+ix]));
        float smooth=Math.max(0f,Math.min(1f,(.075f-gradient)/.055f));
        float outer=Math.max(0f,Math.min(1f,(dist-1f)/.28f));
        return 1f-.96f*Math.min(1f,amount*1.6f)*similarity*smooth*outer;
    }
    /** A deliberately conservative warm-subject hint, NOT a face detector. */
    public static boolean warmSubject(int pixel,float x,float y,float w,float h){
        float dx=(x/w-.5f)/.34f,dy=(y/h-.50f)/.48f;
        if(dx*dx+dy*dy>1f)return false;
        int r=(pixel>>16)&255,g=(pixel>>8)&255,b=pixel&255;
        // Warm skin/brown facial detail differs from gray or yellow-green wall.
        return r-g>11 && r-b>9 && r-g>=(g-b)*.48f && r>70;
    }
    /** Optional light-tone preservation for preprocessed grayscale portraits.
     * Opaque paper-white and transparent pixels never qualify. The conservative
     * central portrait ROI avoids turning exterior image borders into vinyl.
     */
    public static boolean grayPortraitSubject(int pixel,float x,float y,float w,float h){
        if(((pixel>>>24)&255)<240)return false;
        float dx=(x/w-.5f)/.35f,dy=(y/h-.49f)/.48f;
        if(dx*dx+dy*dy>1f)return false;
        int r=(pixel>>16)&255,g=(pixel>>8)&255,b=pixel&255;
        int max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b));
        return max-min<=14 && lum(pixel)<.88f;
    }
    private static float lum(int p){return (((p>>16)&255)*.2126f+((p>>8)&255)*.7152f+(p&255)*.0722f)/255f;}
    private static float distance(int a,int b){
        float r=((a>>16)&255)-((b>>16)&255);
        float g=((a>>8)&255)-((b>>8)&255);
        float bl=(a&255)-(b&255);
        return (float)Math.sqrt(r*r+g*g+bl*bl);
    }
}

package com.halftonestudio;

/** Work at requested photo resolution without decoding the entire sensor image. */
public final class PhotoImportSizing {
    private PhotoImportSizing() {}
    public static int sampleSize(int width,int height,int bound) {
        if(width<=0||height<=0||bound<=0)throw new IllegalArgumentException("Invalid photo sizing");
        int sample=1,largest=Math.max(width,height);
        while((long)largest>=2L*sample*bound)sample*=2;
        return sample;
    }
    public static int[] finalSize(int width,int height,int bound){
        int largest=Math.max(width,height);
        if(largest<=bound)return new int[]{width,height};
        double factor=(double)bound/largest;
        return new int[]{Math.max(1,(int)Math.round(width*factor)),
                         Math.max(1,(int)Math.round(height*factor))};
    }
}

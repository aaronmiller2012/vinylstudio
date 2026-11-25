package com.halftonestudio;

/** Pure-Java image decode planning, usable by Android imports and host regression tests.
 * Native originals remain encoded on disk; these limits are for interactive previews.
 */
final class PhotoDecodeBudget {
    static final int EDIT_MAX_SIDE=1800;
    static final int ORIGINAL_PREVIEW_MAX_SIDE=2400;
    static final long EDIT_MAX_PIXELS=3_000_000L;
    static final long ORIGINAL_PREVIEW_MAX_PIXELS=4_500_000L;
    private static final long MAX_IMAGE_DIMENSION=100_000L;
    private static final long MAX_IMAGE_PIXELS=200_000_000L;

    static final class Plan {
        final int sampleSize, width, height;
        Plan(int sampleSize,int width,int height){
            this.sampleSize=sampleSize;this.width=width;this.height=height;
        }
    }
    private PhotoDecodeBudget(){}

    static Plan plan(int width,int height,int maxSide,long maxPixels){
        if(width<1||height<1||width>MAX_IMAGE_DIMENSION||height>MAX_IMAGE_DIMENSION
                ||(long)width*height>MAX_IMAGE_PIXELS)
            throw new IllegalArgumentException("Invalid saved photo dimensions");
        if(maxSide<1||maxPixels<1)throw new IllegalArgumentException("Invalid photo decode budget");
        // Android's decoder rounds subsampling down to powers of two. Keep the
        // temporary decoded bitmap at most 1.5x the final size in each dimension
        // and 1.5x the final pixel budget before applying accurate final scaling.
        int sample=1;
        while(sample<65536 && ((long)Math.max(width,height)>((long)maxSide*3/2)*sample
                || (long)width*height > maxPixels*3/2*sample*sample))sample*=2;
        double ratio=Math.min(1d,Math.min((double)maxSide/Math.max(width,height),
            Math.sqrt((double)maxPixels/((double)width*height))));
        int outW=Math.max(1,(int)Math.floor(width*ratio));
        int outH=Math.max(1,(int)Math.floor(height*ratio));
        // Rounding can push a thin image one pixel over the budget.
        while((long)outW*outH>maxPixels){if(outW>=outH)outW--;else outH--;}
        return new Plan(sample,outW,outH);
    }
}

package com.halftonestudio;
/** Exact visible-alpha photographic samples; independent of mounting surface color.
 * M4/D12: swatches come from {@link PhotoPaletteOptimizer} (whole-image OKLab histogram,
 * support-weighted seeding, near-duplicate merge). Deterministic for identical input. */
public final class PhotoPaletteExtractor {
    private PhotoPaletteExtractor(){}
    public static int[] extract(int[] pixels,int w,int h){
        return analyze(pixels,w,h).colors;
    }
    /** Eight swatches plus their photo support and how many are perceptually distinct. */
    public static PhotoPaletteOptimizer.Result analyze(int[] pixels,int w,int h){
        if(pixels==null||w<1||h<1||pixels.length!=(long)w*h)
            throw new IllegalArgumentException("Invalid prepared photo");
        boolean[] valid=new boolean[pixels.length];int count=0;
        for(int i=0;i<pixels.length;i++)if((pixels[i]>>>24)>=128){valid[i]=true;count++;}
        if(count==0)throw new IllegalArgumentException("Photo has no visible pixels");
        return PhotoPaletteOptimizer.optimize(pixels,valid,8);
    }
}

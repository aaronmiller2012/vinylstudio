package com.halftonestudio;

/** Extract realistic photo swatches with {@link PhotoPaletteOptimizer}. Transparent substrate and untouched paper are excluded.
 * Non-Android so the identical palette algorithm is testable on the NAS.
 */
public final class StipplePhotoPalette {
    private StipplePhotoPalette() {}
    public static int[] derive(int[] pixels,int width,int height,int count,int paper) {
        if(pixels==null||pixels.length!=(long)width*height||count<2||count>8)
            throw new IllegalArgumentException("Invalid photo palette request");
        boolean[] valid=new boolean[pixels.length];int populated=0;
        for(int i=0;i<pixels.length;i++) {
            int c=pixels[i];int dr=((c>>16)&255)-((paper>>16)&255);
            int dg=((c>>8)&255)-((paper>>8)&255),db=(c&255)-(paper&255);
            valid[i]=(c>>>24)>=128&&(dr*dr+dg*dg+db*db)>360;
            if(valid[i])populated++;
        }
        if(populated<count)throw new IllegalArgumentException("Not enough visible photo colors");
        // M4/D12: the shared deterministic OKLab palette optimizer (no strided
        // sampling, no outlier seeds, near-duplicates merged).
        int[] colors=PhotoPaletteOptimizer.optimize(pixels,valid,count).colors;
        return colors;
    }
}

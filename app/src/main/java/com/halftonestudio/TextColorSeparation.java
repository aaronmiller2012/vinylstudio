package com.halftonestudio;

import java.util.Arrays;

/**
 * Pure-Java physical-vinyl colour evaluator. Every candidate is scored against the
 * ACTUAL opaque Layer-1 background. A glyph pixel shows one upper-layer swatch;
 * a missing glyph pixel shows the background vinyl. This is NOT CMYK ink blending.
 * Scores are positive when the text shape improves the source's Lab-colour match.
 */
public final class TextColorSeparation {
    private TextColorSeparation() {}
    public static final class Analysis {
        /** [detail layer][5x7 pixel] benefit of upper vinyl instead of the solid base. */
        public final float[][] gains;
        public final float[] tonalPatch;
        public final float edge;
        Analysis(float[][] gains,float[] tonalPatch,float edge) {
            this.gains=gains;this.tonalPatch=tonalPatch;this.edge=edge;
        }
    }
    public static Analysis analyze(int[] rgb35,float[] tone35,int[] palette,float edge) {
        if(rgb35.length!=35||tone35.length!=35||palette.length<2)
            throw new IllegalArgumentException("Expected 5x7 samples and 2..8 palette colours");
        float[][] pLab=StyleKit.labOf(palette);
        float[][] gains=new float[palette.length-1][35];
        for(int b=0;b<35;b++){
            float[] source=StyleKit.toLab(rgb35[b]);
            float base=StyleKit.labDist2(source,pLab[0]);
            for(int layer=1;layer<palette.length;layer++)
                gains[layer-1][b]=(base-StyleKit.labDist2(source,pLab[layer]))/1000f;
        }
        return new Analysis(gains,Arrays.copyOf(tone35,35),edge);
    }
    /** RC62: blend original sample color with its coherent spatial region.
     * Region guidance is deliberately bounded; sharp photographic features must
     * still override the low-frequency cluster representative. */
    public static Analysis analyze(int[] rgb35,float[] tone35,int[] palette,float edge,
                                   int regionColor,float regionWeight) {
        if(regionWeight<=0f)return analyze(rgb35,tone35,palette,edge);
        float weight=Math.max(0f,Math.min(.45f,regionWeight));
        float[][] swatches=StyleKit.labOf(palette);
        float[] representative=StyleKit.toLab(regionColor);
        float[][] gains=new float[palette.length-1][35];
        for(int j=0;j<35;j++){
            float[] actual=StyleKit.toLab(rgb35[j]);
            float blendedBase=(1-weight)*StyleKit.labDist2(actual,swatches[0])+
                weight*StyleKit.labDist2(representative,swatches[0]);
            for(int i=1;i<palette.length;i++){
                float blendedDetail=(1-weight)*StyleKit.labDist2(actual,swatches[i])+
                    weight*StyleKit.labDist2(representative,swatches[i]);
                gains[i-1][j]=(blendedBase-blendedDetail)/1000f;
            }
        }
        return new Analysis(gains,Arrays.copyOf(tone35,35),edge);
    }
    public static float improvement(Analysis a,long mask,int slot) {
        float[] g=a.gains[slot-1];float sum=0;
        for(int bit=0;bit<35;bit++)if((mask&(1L<<bit))!=0)sum+=g[bit];
        return sum/35f;
    }
    public static float structure(Analysis a,long mask) {
        float mean=0;for(float t:a.tonalPatch)mean+=t;mean/=35f;
        int count=Long.bitCount(mask);if(count==0||count==35)return 0;
        float dot=0;for(int bit=0;bit<35;bit++)if((mask&(1L<<bit))!=0)
            dot+=a.tonalPatch[bit]-mean;
        // Encourages glyphs whose shape follows an edge rather than filling a rectangle.
        return 1.7f*dot/35f;
    }
    /** Small capped diversity bonus: only breaks near-ties; never forces unsuitable swatches. */
    public static float usageBonus(int slot,int[] counts,int total) {
        if(counts.length<3)return 0;
        float share=counts[slot]/(float)Math.max(1,total);
        float target=1f/(counts.length-1);
        return Math.max(-.16f,Math.min(.16f,(target-share)*.38f));
    }
    public static float dither(int row,int col) {
        final int[] bayer={0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5};
        return ((bayer[(row&3)*4+(col&3)]+.5f)/16f-.5f)*.12f;
    }
}

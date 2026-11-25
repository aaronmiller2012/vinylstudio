package com.halftonestudio;
/** Shared physical-slot decisions. No RGB mutation, sheet merging, or rendering side effects. */
final class PerceptualLayerColorEngine {
    private PerceptualLayerColorEngine() {}
    static int[] finishPreset(int style,int[] full,int count,boolean reverse){
        boolean nested=style==VinylStyleSuite.POSTER_STENCIL||style==VinylStyleSuite.TOPOGRAPHIC||style==VinylStyleSuite.REDUCTION_WOODCUT;
        if(!nested)return PaletteOrientation.reverseActive(full,count,reverse,NewStyleLayerPolicy.isStructuralLayer(style,0));
        int[] out=full.clone();
        // Rank only active slots. Nested tonal masks place darkest vinyl uppermost.
        for(int i=1;i<count;i++){int c=out[i],j=i-1;double l=ColorWorkspacePalette.lightness(c);
            while(j>=0&&ColorWorkspacePalette.lightness(out[j])<l){out[j+1]=out[j];j--;}out[j+1]=c;}
        return PaletteOrientation.reverseActive(out,count,reverse,NewStyleLayerPolicy.isStructuralLayer(style,0));
    }
    static double light(int rgb){return PerceptualVinylColor.lab(rgb)[0];}
    static double error(int rgb,int color){double[] a=PerceptualVinylColor.lab(rgb),b=PerceptualVinylColor.lab(color);
        double l=a[0]-b[0],x=a[1]-b[1],y=a[2]-b[2];return 1.95*l*l+1.03*(x*x+y*y);}
    /** Sample entire strand, not an arbitrary row/column color cycle. */
    static int strandColor(int[] samples,int count,PhotoSwatchAssociation association){
        if(count==0)return 0;long r=0,g=0,b=0;
        for(int i=0;i<count;i++){int c=samples[i];r+=(c>>>16)&255;g+=(c>>>8)&255;b+=c&255;}
        return association.choose(0xff000000|((int)(r/count)<<16)|((int)(g/count)<<8)|(int)(b/count));
    }
}

package com.halftonestudio;

import java.util.Arrays;

/**
 * Image-aware foundation choice for opaque vinyl, without Android dependencies.
 * A foundation is chosen from real photo-derived swatches, not invented from the
 * mounting surface. Reordering is restricted to explicit photo-palette applies.
 * Never call this from a render, zoom, pan, or physical layer-count adjustment.
 */
public final class FoundationColorPolicy {
    private FoundationColorPolicy() {}

    public static final int NONE=0, OPTIONAL=1, STRUCTURAL=2, SEPARATE_SNIP=3;

    /** Describes CURRENT engines, not a request to add another physical sheet. */
    public static int newStylePolicy(int style){
        if(style==VinylStyleSuite.TEXT || style==VinylStyleSuite.GLASS ||
           style==VinylStyleSuite.POSTER_STENCIL ||
           VinylStyleSuite.isPrismaticFamily(style) ||
           style==VinylStyleSuite.TESSELLATION_MOTIF ||
           style==VinylStyleSuite.CHAINMAIL || VinylStyleSuite.isConfetti(style)||
           VinylStyleSuite.isCraft(style)||VinylStyleSuite.isOrganicFlow(style))
            return STRUCTURAL;
        if(style==VinylStyleSuite.LOW_POLY || style==VinylStyleSuite.MARQUETRY ||
           style==VinylStyleSuite.TORN_COLLAGE || style==VinylStyleSuite.REDUCTION_WOODCUT)
            return OPTIONAL; // No additional sheet is silently allocated.
        return NONE; // Particularly single-line, contour and engraving styles.
    }
    /** Whether the intrinsic L1 is an adjustable photo-colour foundation. */
    public static boolean photoBaseIsFirst(int style){
        return style==VinylStyleSuite.TESSELLATION_MOTIF ||
            VinylStyleSuite.isPrismaticFamily(style) ||
            style==VinylStyleSuite.CHAINMAIL || VinylStyleSuite.isConfetti(style)||
            VinylStyleSuite.isCraft(style)||VinylStyleSuite.isOrganicFlow(style);
    }
    /** Prismatic uses a light underlying plate for dark overprints. Other tiled
     * portraits place dark body/shadow vinyl behind lighter marks. */
    public static boolean lightFoundation(int style){
        return VinylStyleSuite.isPrismaticFamily(style);
    }

    private static double lightness(int argb){return PerceptualVinylColor.lab(argb)[0];}
    private static double chroma(int argb){
        double[] l=PerceptualVinylColor.lab(argb);
        return Math.hypot(l[1],l[2]);
    }
    /** The 8-colour palette always retains its actual RGB values. Population
     * comes from a prepared photo if present; transparent pixels do not vote. */
    public static int bestIndex(int[] colors,int active,int[] preparedArgb,boolean light){
        if(colors==null||active<1||active>colors.length)
            throw new IllegalArgumentException("Invalid foundation palette");
        double[] luminance=new double[active],chroma=new double[active];
        for(int i=0;i<active;i++){luminance[i]=lightness(colors[i]);chroma[i]=chroma(colors[i]);}
        double[][] labs=new double[active][];
        for(int j=0;j<active;j++)labs[j]=PerceptualVinylColor.lab(colors[j]);
        int[] members=new int[active]; int total=0;
        if(preparedArgb!=null){
            int step=Math.max(1,preparedArgb.length/6000);
            for(int i=0;i<preparedArgb.length;i+=step){
                int px=preparedArgb[i];if((px>>>24)<128)continue;
                double[] lab=PerceptualVinylColor.lab(px);
                int best=0;double dist=Double.POSITIVE_INFINITY;
                for(int j=0;j<active;j++){
                    double dl=lab[0]-labs[j][0],da=lab[1]-labs[j][1],db=lab[2]-labs[j][2];
                    double d=dl*dl+da*da+db*db;
                    if(d<dist){dist=d;best=j;}
                }
                members[best]++;total++;
            }
        }
        // Rank by actual perceptual lightness rather than array order.
        double[] sorted=luminance.clone();Arrays.sort(sorted);
        double desired=light?sorted[Math.max(0,(int)Math.ceil((active-1)*.84))]:
            sorted[Math.min(active-1,(int)Math.round((active-1)*.24))];
        int best=0; double scoreBest=Double.NEGATIVE_INFINITY;
        for(int i=0;i<active;i++){
            double population=total==0?1.0/active:members[i]/(double)total;
            double off=Math.abs(luminance[i]-desired);
            double directional=light?Math.max(0,desired-luminance[i]):
                Math.max(0,luminance[i]-desired);
            // A rare purple/black accent should not win merely for being darkest.
            double rarity=total>0&&population<.05?.24:0;
            double chromaPenalty=Math.max(0,chroma[i]-.095)*.35;
            double score=.48*population-.92*off-(light?1.75:.62)*directional-rarity-chromaPenalty;
            if(score>scoreBest+1e-12){scoreBest=score;best=i;}
        }
        return best;
    }
    public static int bestColor(int[] colors,int active,int[] preparedArgb,boolean light){
        return colors[bestIndex(colors,active,preparedArgb,light)];
    }
    /** Move a supported photo-derived foundation to slot zero, preserving all
     * other colors, physical slot count and dormant swatches. */
    public static int[] anchorPhotoPalette(int[] original,int active,int[] preparedArgb,boolean light){
        if(original==null||active<1||active>original.length)
            throw new IllegalArgumentException("Invalid photo swatches");
        int[] out=original.clone();
        int winner=bestIndex(original,active,preparedArgb,light);
        int anchor=out[winner];
        System.arraycopy(out,0,out,1,winner);
        out[0]=anchor;
        return out;
    }
}

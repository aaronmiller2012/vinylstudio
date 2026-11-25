package com.halftonestudio;

import java.util.Arrays;

/**
 * Pure palette decisions for physically opaque vinyl. Only explicitly applying a
 * preset calls these methods: changing paper, layer count, or editing a chip
 * must never silently change the already committed physical stack.
 */
public final class PaletteStylePolicy {
    private PaletteStylePolicy() { }

    public static final int GENERAL = 0;
    public static final int PHOTO_CELLS = 1;
    public static final int LINE_RIBBONS = 2;
    public static final int STENCIL = 3;
    public static final int GLASS = 4;
    public static final int TOPOGRAPHIC = 5;
    public static final int TEXT = 6;
    public static final int CMYK = 7;
    public static final int CONFETTI_STACK = 8;
    public static final int PRISMATIC_CELLS = 9;
    public static final int CHAINMAIL_TONES = 10;
    public static final int DARK_OVER_LIGHT = 11;

    public static int familyForNewStyle(int style) {
        if(style == VinylStyleSuite.GLASS) return GLASS;
        if(style == VinylStyleSuite.TOPOGRAPHIC) return TOPOGRAPHIC;
        if(style == VinylStyleSuite.POSTER_STENCIL) return DARK_OVER_LIGHT;
        if(style == VinylStyleSuite.TEXT) return TEXT;
        if(style == VinylStyleSuite.LOW_POLY) return PHOTO_CELLS;
        if(VinylStyleSuite.isConfetti(style)||VinylStyleSuite.isCraft(style)||
            VinylStyleSuite.isOrganicFlow(style)) return CONFETTI_STACK;
        if(VinylStyleSuite.isPrismaticFamily(style)) return PRISMATIC_CELLS;
        if(style == VinylStyleSuite.CHAINMAIL) return CHAINMAIL_TONES;
        if(style == VinylStyleSuite.MARQUETRY || style == VinylStyleSuite.TORN_COLLAGE)
            return PHOTO_CELLS;
        if(style == VinylStyleSuite.REDUCTION_WOODCUT) return DARK_OVER_LIGHT;
        if(style == VinylStyleSuite.WOVEN_RIBBON) return LINE_RIBBONS;
        return GENERAL;
    }

    /**
     * Apply a new eight-swatch preset to the chosen style. Result is a stable
     * eight-position physical array (including colors parked for later use).
     * Lead/base slots never move for Glass or text.
     */
    public static int[] arrange(int family,int[] full,int count,int surface) {
        if(full == null || full.length != 8 || count < 2 || count > 8)
            throw new IllegalArgumentException("Expected eight swatches and two to eight sheets");
        if(family == GLASS) return full.clone(); // Lead and pane IDs are fixed by the glass preset.
        if(family == TEXT) return anchored(full,count,surface);
        if(family == TOPOGRAPHIC || family == DARK_OVER_LIGHT) return tonalWithBase(full,count,true);
        if(family == STENCIL || family == CHAINMAIL_TONES) return tonalProgression(full,count);
        if(family == CONFETTI_STACK) return tonalWithBase(full,count,false);
        if(family == PRISMATIC_CELLS) return tonalWithBase(full,count,true);
        if(family == CMYK) return full.clone(); // Channel identities are never optimized as generic colors.
        return ColorWorkspacePalette.arrangedForSurface(full,count,surface);
    }

    private static int[] anchored(int[] full,int count,int surface) {
        // L1 is the user's fixed lead/silhouette. Select L2..LN by perceptual
        // distinction; unused swatches remain parked at their original RGB.
        int[] out=new int[8];out[0]=full[0];
        boolean[] used=new boolean[8];used[0]=true;
        for(int slot=1;slot<count;slot++) {
            int best=-1;double score=Double.NEGATIVE_INFINITY;
            for(int j=1;j<8;j++)if(!used[j]) {
                double nearest=Double.POSITIVE_INFINITY;
                for(int k=0;k<slot;k++)nearest=Math.min(nearest,
                    PerceptualVinylColor.distance(full[j],out[k]));
                double substrate=PerceptualVinylColor.distance(full[j],surface);
                double value=nearest+.12d*substrate;
                if(value>score+1e-10) {score=value;best=j;}
            }
            used[best]=true;out[slot]=full[best];
        }
        int next=count;
        for(int j=1;j<8;j++)if(!used[j])out[next++]=full[j];
        return out;
    }

    /**
     * A style-specific solid vinyl foundation: dark underlay for tossed confetti,
     * light underlay for prismatic facets. Select the overlay swatches across the
     * entire available tonal range. These are physical colors, never generated RGB.
     * Only invoked when a user explicitly applies a preset.
     */
    private static int[] tonalWithBase(int[] full,int count,boolean lightBase) {
        Integer[] order=new Integer[8];
        for(int i=0;i<8;i++)order[i]=i;
        Arrays.sort(order,(a,b)-> {
            int c=Double.compare(ColorWorkspacePalette.lightness(full[a]),
                                 ColorWorkspacePalette.lightness(full[b]));
            return c!=0?c:Integer.compare(a,b);
        });
        int[] output=new int[8]; boolean[] used=new boolean[8];
        int anchor=lightBase?order[7]:order[0];
        output[0]=full[anchor]; used[anchor]=true;
        for(int i=1;i<count;i++) {
            // Top overlays receive evenly spread levels, not a near-duplicate
            // set of adjacent browns. For the light base, progress toward dark.
            int pick=lightBase?
                order[Math.max(0,7-Math.round(i*7f/(count-1)))]:
                order[Math.min(7,Math.round(i*7f/(count-1)))];
            if(used[pick]) {
                int candidate=-1;
                double best=Double.NEGATIVE_INFINITY;
                for(int p:order)if(!used[p]) {
                    double distance=Double.POSITIVE_INFINITY;
                    for(int j=0;j<i;j++)
                        distance=Math.min(distance,PerceptualVinylColor.distance(full[p],output[j]));
                    if(distance>best){best=distance;candidate=p;}
                }
                pick=candidate;
            }
            output[i]=full[pick];used[pick]=true;
        }
        int next=count;
        for(int p:order)if(!used[p])output[next++]=full[p];
        return output;
    }

    private static int[] tonalProgression(int[] full,int count) {
        // Color levels progress from darkest to lightest, independent of the
        // mounting surface. Sample the full range for every explicit preset click.
        Integer[] sorted=new Integer[8];
        for(int i=0;i<8;i++)sorted[i]=i;
        Arrays.sort(sorted,(a,b)-> {
            int compare=Double.compare(ColorWorkspacePalette.lightness(full[a]),
                ColorWorkspacePalette.lightness(full[b]));
            return compare!=0?compare:Integer.compare(a,b);
        });
        int[] out=new int[8];boolean[] used=new boolean[8];
        for(int i=0;i<count;i++) {
            int index=sorted[Math.round(i*7f/(count-1))];
            out[i]=full[index];used[index]=true;
        }
        int cursor=count;
        for(int index:sorted)if(!used[index])out[cursor++]=full[index];
        return out;
    }
}

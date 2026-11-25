package com.halftonestudio;

/**
 * Perceptual, style-aware assignment of photographed tessellation cells to the
 * user's actual opaque vinyl swatches. This is a mapping policy, NOT a palette
 * generator: it never edits colors, their physical order, or enabled slots.
 *
 * The first sheet of a solid-base tessellation is a legitimate visible vinyl
 * color. A cell belonging to it is left uncovered (returned as BASE) instead
 * of being forced onto an unrelated overlay sheet.
 *
 * Pure Java and deterministic. No Android color APIs, no stochastic dither,
 * no extra geometry/cuts, and no per-cell palette color conversions.
 */
final class TessellationToneMapper {
    static final int BASE = -1;
    private static final double BASE_WIN_RATIO = .84; // prefer overlay on near-ties

    private final int firstOverlay, end;
    private final boolean hasBase;
    private final double[][] colors;
    private final double lightWeight, chromaWeight, toneStrength;
    private final double sourceLow, sourceHigh, paletteLow, paletteHigh;
    private final boolean normalize;

    TessellationToneMapper(StyleKit.Source photo, TessellationMotifEngine.Options opts, boolean base) {
        this.firstOverlay = base ? 1 : 0;
        this.end = opts.layerCount;
        this.hasBase = base;
        this.colors = new double[end][];
        double pLow = Double.POSITIVE_INFINITY, pHigh = Double.NEGATIVE_INFINITY;
        for (int i=0; i<end; ++i) {
            colors[i] = PerceptualVinylColor.lab(opts.palette[i]);
            pLow = Math.min(pLow, colors[i][0]);
            pHigh = Math.max(pHigh, colors[i][0]);
        }
        paletteLow=pLow;paletteHigh=pHigh;
        // Larger, bolder motifs need stronger separation of light and shadow.
        // Small many-piece tilings can afford a little more chroma fidelity.
        int pattern=opts.pattern;
        if (pattern<=TessellationMotifEngine.RHOMBITRIHEX) {
            lightWeight=1.55;chromaWeight=1.15;toneStrength=.24;
        } else if (pattern<TessellationMotifEngine.INTERLOCKING_BASKET) {
            lightWeight=1.70;chromaWeight=1.12;toneStrength=.28;
        } else if (pattern==TessellationMotifEngine.FRAMED_SQUARE ||
                   pattern==TessellationMotifEngine.STEPPED_CROSS ||
                   pattern==TessellationMotifEngine.CROSSHAIR_GRID ||
                   pattern==TessellationMotifEngine.NESTED_HEXAGONS) {
            lightWeight=1.95;chromaWeight=1.06;toneStrength=.34;
        } else if (pattern==TessellationMotifEngine.TRIANGULAR_RIBBONS ||
                   pattern==TessellationMotifEngine.ISOMETRIC_RIBBONS ||
                   pattern==TessellationMotifEngine.INSET_RHOMBUS_CUBES) {
            lightWeight=1.78;chromaWeight=1.12;toneStrength=.29;
        } else {
            lightWeight=1.70;chromaWeight=1.13;toneStrength=.28;
        }
        // Subject-only photometric percentiles: pixels outside the Snip may
        // never influence the palette range. Deterministic bounded sampling.
        int[] histogram = new int[256];
        int population=0;
        int stride=Math.max(1, photo.argb.length/12000);
        for (int i=0;i<photo.argb.length;i+=stride) {
            if ((i&4095)==0) CancellationCheckpoint.check();
            if (!photo.valid[i]) continue;
            double l=PerceptualVinylColor.lab(photo.argb[i])[0];
            int bucket=Math.max(0,Math.min(255,(int)Math.round(l*255)));
            histogram[bucket]++;population++;
        }
        sourceLow=quantile(histogram,population,.04);
        sourceHigh=quantile(histogram,population,.96);
        // Do not invent contrast in near-flat images or palettes with nearly
        // identical lightness. A manual swatch palette remains authoritative.
        normalize=population>32 && sourceHigh-sourceLow>.16 &&
                  paletteHigh-paletteLow>.16;
    }

    private static double quantile(int[] histogram,int n,double pct) {
        if (n<=0) return 0;
        int target=(int)Math.floor((n-1)*pct),seen=0;
        for (int i=0;i<histogram.length;i++) {
            seen+=histogram[i];
            if(seen>target)return i/255d;
        }
        return 1;
    }

    /** Returns a physical overlay slot or BASE (leave the base silhouette exposed). */
    int select(int rgb) {
        double[] sample=PerceptualVinylColor.lab(rgb);
        // Preserve an exact (or quantization-equivalent) swatch match; a source
        // patch already matching a chosen vinyl color must not be tone-shifted.
        for (int i=0;i<end;i++) {
            double d=distance(sample,colors[i]);
            if(d<.000001) return hasBase && i==0 ? BASE : i;
        }
        if(normalize){
            double mapped=paletteLow+(paletteHigh-paletteLow)*
                Math.max(0,Math.min(1,(sample[0]-sourceLow)/(sourceHigh-sourceLow)));
            sample[0]=Math.max(0,Math.min(1,sample[0]+
                Math.max(-.085,Math.min(.085,(mapped-sample[0])*toneStrength))));
        }
        int best=firstOverlay;
        double bestScore=Double.POSITIVE_INFINITY;
        for (int i=firstOverlay;i<end;i++){
            double score=distance(sample,colors[i]);
            if (score<bestScore){best=i;bestScore=score;}
        }
        // Base participates in color association but is intentionally not
        // assigned to an overlay: the underlying solid sheet is already there.
        // Require a clear perceptual improvement to avoid weak checkerboarding.
        if(hasBase && distance(sample,colors[0]) < bestScore*BASE_WIN_RATIO)
            return BASE;
        return best;
    }

    private double distance(double[] a,double[] b) {
        double dl=a[0]-b[0],da=a[1]-b[1],db=a[2]-b[2];
        return lightWeight*dl*dl+chromaWeight*(da*da+db*db);
    }
}

package com.halftonestudio;

/** Physical page and memory limits for PNG/PDF artwork exports. JVM-testable. */
public final class ArtworkExportLayout {
    private ArtworkExportLayout() { }
    public static final int PNG_MAX_SIDE=4096, PNG_MAX_PIXELS=12_000_000;
    public static final float PDF_POINTS_PER_MM=72f/25.4f;
    public static int[] pngPixels(float widthMm,float heightMm) {
        requireSize(widthMm,heightMm);
        double nominal=300.0/25.4;
        double scale=Math.min(nominal,Math.min(PNG_MAX_SIDE/(double)widthMm,
                PNG_MAX_SIDE/(double)heightMm));
        if(widthMm*heightMm*scale*scale>PNG_MAX_PIXELS)
            scale=Math.sqrt(PNG_MAX_PIXELS/(double)(widthMm*heightMm));
        int w=Math.max(1,(int)Math.floor(widthMm*scale));
        int h=Math.max(1,(int)Math.floor(heightMm*scale));
        if(w>PNG_MAX_SIDE||h>PNG_MAX_SIDE||((long)w)*h>PNG_MAX_PIXELS)
            throw new IllegalArgumentException("PNG pixel dimensions exceed safe memory limit");
        return new int[]{w,h};
    }
    public static int[] pdfPoints(float widthMm,float heightMm) {
        requireSize(widthMm,heightMm);
        int w=Math.max(1,Math.round(widthMm*PDF_POINTS_PER_MM));
        int h=Math.max(1,Math.round(heightMm*PDF_POINTS_PER_MM));
        if(w>14400||h>14400)throw new IllegalArgumentException("PDF artboard too large");
        return new int[]{w,h};
    }
    private static void requireSize(float widthMm,float heightMm) {
        if(!Float.isFinite(widthMm)||!Float.isFinite(heightMm)||widthMm<=0||heightMm<=0||
                widthMm>3000||heightMm>3000)
            throw new IllegalArgumentException("Invalid artwork dimensions");
    }
}

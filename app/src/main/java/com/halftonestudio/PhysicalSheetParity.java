package com.halftonestudio;

/** Physical-size contract for all illustrations in an assembly job.
 * Comparison is between actual *sheet* dimensions, not the cropped artwork;
 * every sheet includes the same registration clearance/padding.
 * No Android Canvas, bitmap scaling or cutter-device assumptions.
 */
public final class PhysicalSheetParity {
    private PhysicalSheetParity() {}
    public static final float TOLERANCE_MM=0.05f;
    public static void requireSameBoard(float referenceWidthMm,float referenceHeightMm,
                                        float layerWidthMm,float layerHeightMm) {
        if(!Float.isFinite(referenceWidthMm)||!Float.isFinite(referenceHeightMm)||
           !Float.isFinite(layerWidthMm)||!Float.isFinite(layerHeightMm)||
           referenceWidthMm<=0||referenceHeightMm<=0||layerWidthMm<=0||layerHeightMm<=0)
            throw new IllegalArgumentException("Assembly board dimensions must be finite and positive");
        if(Math.abs(referenceWidthMm-layerWidthMm)>TOLERANCE_MM||
           Math.abs(referenceHeightMm-layerHeightMm)>TOLERANCE_MM)
            throw new IllegalArgumentException("Assembly artwork boards differ; registration would be misaligned");
    }
    public static void requireFitsArtwork(float boardWidthMm,float boardHeightMm,
                                           float artworkWidthMm,float artworkHeightMm) {
        requireSameBoard(boardWidthMm,boardHeightMm,boardWidthMm,boardHeightMm);
        if(!Float.isFinite(artworkWidthMm)||!Float.isFinite(artworkHeightMm)||
           artworkWidthMm<=0||artworkHeightMm<=0||
           boardWidthMm+TOLERANCE_MM<artworkWidthMm||boardHeightMm+TOLERANCE_MM<artworkHeightMm)
            throw new IllegalArgumentException("Finished artwork does not fit assembly board");
    }
}

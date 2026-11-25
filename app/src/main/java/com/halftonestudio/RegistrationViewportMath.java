package com.halftonestudio;

/** Stable UI layout independent of the dynamically expanding physical cut sheet. */
public final class RegistrationViewportMath {
    private RegistrationViewportMath() {}

    /** Reserve room for *default* marks, not editable custom coordinates.
     * The exported SVG uses RegistrationMarks.layout() and may have a larger
     * sheet padding; it MUST NOT alter the photo/portrait zoom on screen.
     */
    public static float stablePreviewPadding(float widthMm, float heightMm,
            float diameterMm, float clearanceMm, float basePadMm) {
        float[][] initial=RegistrationMarks.defaultPositions(
                widthMm,heightMm,diameterMm,clearanceMm);
        return RegistrationPreviewGeometry.padding(widthMm,heightMm,
                diameterMm,clearanceMm,initial,basePadMm);
    }

    /** Clamp a moving cut mark's center within the reachable view rectangle.
     * This changes only the user gesture, never stored marks on pan/zoom or export.
     * Margin includes the mark's physical radius projected to screen pixels.
     */
    public static float clampTouch(float xPx,float extentPx,float marginPx) {
        if(!Float.isFinite(extentPx)||extentPx<=0)return 0f;
        if(!Float.isFinite(xPx))return extentPx*.5f;
        float inset=Float.isFinite(marginPx)?Math.max(0f,marginPx):0f;
        inset=Math.min(inset,extentPx*.48f);
        return Math.max(inset,Math.min(extentPx-inset,xPx));
    }
}

package com.halftonestudio;

/**
 * Modern line-only tonal transfer. The cutter minimum remains absolute:
 * weak source tone is encoded by a wider legal ribbon or by supported
 * minimum-width stretches on existing carrier lanes, never by sub-mm SVG strokes.
 * No channel may gain a positive weight when its original coverage is zero.
 */
public final class LineToneRecovery {
    private LineToneRecovery() { }

    public static float widthResponse(float coverage,float gamma,float recovery) {
        if (!Float.isFinite(coverage) || !Float.isFinite(gamma) || !Float.isFinite(recovery))
            throw new IllegalArgumentException("Nonfinite line tone");
        float v=Math.max(0f,Math.min(1f,coverage));
        if (v==0f || v==1f) return v;
        float amount=Math.max(0f,Math.min(1f,recovery));
        // Match dot area response more closely without letting gamma reach zero.
        float effectiveGamma=Math.max(.40f, gamma*(1f-.48f*amount));
        return (float)Math.pow(v,effectiveGamma);
    }

    /** Nearby channel evidence, but no invented coverage at a blank carrier. */
    public static float directionalFootprint(float center,float sideA,float sideB) {
        center=Math.max(0f,Math.min(1f,center));
        if(center<=.008f)return center;
        float supported=Math.max(0f,Math.min(1f,(sideA+sideB)*.5f));
        // Asymmetric 1-D sample: retain the centerline's true color ownership.
        return Math.max(center,Math.min(1f,center*.80f+supported*.20f));
    }

    /**
     * B2 follow-up: retain the original carrier tone at a genuine transverse
     * photographic edge instead of borrowing a neighbor's darker pigment.
     *
     * The old response is preserved EXACTLY when Shape Definition is disabled,
     * when the center is blank, or when both sides agree with the center.
     * The revised response is bounded between the center and the historical
     * response: it cannot create coverage in a blank swatch, erase existing
     * center evidence, or increase an existing ribbon beyond its old width.
     * Actual minimum cut widths and continuity are still owned by the renderer.
     */
    public static float edgePreservingDirectionalFootprint(float center,float sideA,float sideB,
                                                            float shapeDefinition) {
        float old=directionalFootprint(center,sideA,sideB);
        float c=Math.max(0f,Math.min(1f,center));
        if(shapeDefinition<=0f||c<=.008f||old<=c)return old;
        float a=Math.max(0f,Math.min(1f,sideA));
        float b=Math.max(0f,Math.min(1f,sideB));
        // Ignore small fluctuations/noise. A sharply different side is
        // evidence of a genuine boundary at this carrier position.
        float difference=Math.max(Math.abs(c-a),Math.abs(c-b));
        float evidence=Math.max(0f,Math.min(1f,(difference-.18f)/.36f));
        float strength=Math.max(0f,Math.min(1f,shapeDefinition));
        return Math.max(c,old-(old-c)*(.90f*strength*evidence));
    }

    /** Bound bend displacement by nearby actual carriers (wave/zigzag only). */
    public static float localBendAllowance(float bend,float previous,float next,float numericalMm) {
        float relative=Math.max(Math.abs(bend-previous),Math.abs(bend-next));
        return Math.max(0f,relative*.5f+Math.max(0f,numericalMm));
    }
}

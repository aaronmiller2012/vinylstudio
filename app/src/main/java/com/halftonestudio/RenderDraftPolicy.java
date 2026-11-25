package com.halftonestudio;

/** The draft preview changes processing resolution, never vinyl geometry settings.
 * Package-private and JVM-testable without Android dependencies.
 */
final class RenderDraftPolicy {
    static final int DEBOUNCE_MS=60;
    static final int HEAVY_DEBOUNCE_MS=120;
    static final int MAX_SIDE_PX=480;
    private RenderDraftPolicy() { }
    static int[] dimensions(int width,int height) {
        if(width<=0||height<=0)throw new IllegalArgumentException("Invalid image dimensions");
        int longest=Math.max(width,height);
        // Half-size linear dimensions, capped to 480px during interaction.
        // Very small sources do not need a separate draft allocation.
        if(longest<=128)return new int[]{width,height};
        float factor=Math.min(.5f,(float)MAX_SIDE_PX/longest);
        return new int[]{Math.max(1,Math.round(width*factor)),
            Math.max(1,Math.round(height*factor))};
    }
    /** Costly algorithms use their last frame during dragging, not a misleading draft. */
    static boolean supportsSpecialtyDraft(boolean maze,int styleId) {
        return !maze&&styleId!=VinylStyleSuite.TSP;
    }
    static float sampleStepMm(float original) {
        if(!Float.isFinite(original)||original<=0)throw new IllegalArgumentException("Invalid sample step");
        return Math.max(original,.70f);
    }
}

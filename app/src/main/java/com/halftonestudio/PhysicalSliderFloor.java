package com.halftonestudio;

/**
 * M4 / D8: physical-size sliders never go below what the selected material can cut.
 *
 * The floor is applied in two places that must agree:
 *  - the slider's range (so the visible value is the value that takes effect), and
 *  - the render snapshot (so saved projects with older, smaller values are generated at
 *    the floor without rewriting the stored value; raising the material minimum and
 *    lowering it again restores the user's own setting).
 * Pure and host-testable.
 */
final class PhysicalSliderFloor {
    private PhysicalSliderFloor(){}

    /** Smallest kept-vinyl feature the current cut settings allow. */
    static float featureMm(float minimumMm){
        return Math.max(VinylEngine.MINIMUM_CUT_WIDTH_MM,Float.isFinite(minimumMm)?minimumMm:VinylEngine.MINIMUM_CUT_WIDTH_MM);
    }
    /** Smallest weed gap the current cut settings allow (the export gap). */
    static float gapMm(float bridgeMm,float minimumMm){
        return StyleCutGeometryPreflight.exportGapMm(bridgeMm,featureMm(minimumMm));
    }
    /** Value that takes effect: never below the floor. */
    static float effective(float value,float floor){
        return Float.isFinite(value)?Math.max(value,floor):floor;
    }
    /** Slider range [low, high] with the floor applied; high is kept at least one step above low. */
    static float[] range(float staticLow,float staticHigh,float floor,float step){
        float low=Math.max(staticLow,floor);
        float high=Math.max(staticHigh,low+step);
        return new float[]{low,high};
    }
}

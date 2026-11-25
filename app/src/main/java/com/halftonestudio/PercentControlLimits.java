package com.halftonestudio;

/** Percentage controls use 100% as the physical/output maximum.
 * The renderer retains its legacy API ranges for old characterization tests;
 * the UI, automatic tuning, and project migration normalize active values here.
 */
final class PercentControlLimits {
    private PercentControlLimits() { }
    static final float MAX = 1.0f;
    private static float limited(float value, float minimum) {
        if (!Float.isFinite(value)) return MAX;
        return Math.max(minimum, Math.min(MAX, value));
    }
    static float pointDensity(float value) {return limited(value, .50f);}
    static float dotSize(float value) {return limited(value, .60f);}
    static float dotCoverage(float value) {return limited(value, .35f);}
}

package com.halftonestudio;

/** Single source of truth for NEW CMYK vinyl portrait parameters.
 * Saved projects retain explicitly serialized settings, including deliberate
 * per-color overrides. These are vinyl-art defaults, not print ICC profiles.
 */
public final class CmykPortraitDefaults {
    private CmykPortraitDefaults() {}
    public static final float BLACK_COLOR_PROTECTION=.26f;
    public static final float SHADOW_COLOR_RECOVERY=.38f;
    public static final float PORTRAIT_MIDTONES=.62f;
    public static final float CYAN_STRENGTH=1.00f;
    public static final float LINE_CONTINUITY=.25f;

    /** Explicit preset application, not project loading or rendering. */
    public static void applyPreset(VinylEngine.Settings s) {
        if(s.mode!=VinylEngine.CMYK)return;
        s.blackColorProtection=BLACK_COLOR_PROTECTION;
        s.shadowColorRecovery=SHADOW_COLOR_RECOVERY;
        s.portraitMidtones=PORTRAIT_MIDTONES;
        for(VinylEngine.Layer layer:s.layers) {
            if(layer.cmykChannel>=0 && layer.cmykChannel<=3)layer.strength=1f;
        }
    }
}




/** Verified v2.24 CMYK vinyl-line look, explicitly selected or new-project default.
 * Never called on saved project load, palettes, or layer count changes.
 */
final class ClassicCmykLines {
    private ClassicCmykLines() {}
    public static void apply(VinylEngine.Settings s) {
        if(s.mode!=VinylEngine.CMYK)return;
        s.classicCmykLines=true;
        s.blackColorProtection=.18f;
        s.shadowColorRecovery=.48f;
        s.portraitMidtones=.82f;
        s.lineContinuity=0f;
        s.linePresenceRecovery=0f;
        s.lineHighlightRetention=0f;
        s.portraitBackgroundGuard=0f;
        for(VinylEngine.Layer l:s.layers) {
            if(l.cmykChannel>=0 && l.cmykChannel<=3) {
                // Preserve actual chosen vinyl colors and angle adjustments.
                // Only tone-strength and newer line-recovery controls are reset.
                l.strength=l.cmykChannel==0?1.12f:1f;
            }
        }
    }
    /** Select modern v2.52 line processing without modifying artistic choices. */
    public static void useModern(VinylEngine.Settings s) {
        s.classicCmykLines=false;
        // Unlike a full preset reset, do not overwrite manual strengths or palettes.
    }
}

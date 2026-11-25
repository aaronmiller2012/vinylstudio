package com.halftonestudio;

/**
 * Global style actions own all standard vinyl layers. A per-layer override is
 * opt-in from Layers and must not silently defeat the user's next Shapes choice.
 * No saved-project schema or engine API changes are required.
 */
final class GlobalPatternSelection {
    private GlobalPatternSelection() {}

    static void clearOverrides(VinylEngine.Settings settings) {
        if (settings == null) throw new IllegalArgumentException("Missing settings");
        for (VinylEngine.Layer layer : settings.layers) {
            layer.lineStyleOverride = -1;
        }
    }

    static void apply(VinylEngine.Settings settings, int style,
                      int profile, float widthIn, float heightIn) {
        // Validate the style before clearing user-configured layer choices.
        if (style < 0 || style >= VinylEngine.PATTERNS.length)
            throw new IllegalArgumentException("Unknown pattern " + style);
        SmartHalftonePresets.apply(settings, style, profile, widthIn, heightIn);
        clearOverrides(settings);
    }
}

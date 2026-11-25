package com.halftonestudio;

/** Only the explicit linked state permits master settings to overwrite sheet edits.
 * No UI side effects: safe to exercise in host regression tests and saved-project repair. */
public final class MasterLayerLinkPolicy {
    private MasterLayerLinkPolicy() { }
    public static void applyIfLinked(VinylEngine.Settings settings) {
        if(settings==null)throw new IllegalArgumentException("Missing vinyl settings");
        if(!settings.linkedControls)return;
        for(VinylEngine.Layer layer:settings.layers){
            layer.spacing=settings.masterSpacingMm;
            layer.density=settings.masterDensity;
            layer.contrast=settings.masterContrast;
        }
    }
}

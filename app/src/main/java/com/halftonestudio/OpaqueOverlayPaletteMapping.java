package com.halftonestudio;

/**
 * The palette shown on an opaque overlay card MUST be the same physical L1..LN
 * swatches committed when it is tapped. These decisions happen only on an
 * explicit palette click, never during a render, material edit or paper change.
 *
 * A preset is a collection of candidate colors and may be arranged for the
 * current photograph. A saved palette is ALREADY a physical stack: never
 * silently sort it again. L1 is an invariant structural silhouette sheet.
 */
public final class OpaqueOverlayPaletteMapping {
    private OpaqueOverlayPaletteMapping() {}

    private static void validate(int[] swatches,int count){
        if(swatches==null||swatches.length!=8||count<2||count>8)
            throw new IllegalArgumentException("Overprint palettes require eight swatches and 2–8 sheets");
    }

    public static int[] preset(int[] swatches,int count,int surface,int[] preparedPhoto,
                               boolean photoPalette,boolean reverse){
        validate(swatches,count);
        // The photo supplies active candidates from ALL eight swatches, even
        // when the user cuts only two/four sheets. Surface affects the preview
        // backing only; never optimize vinyl hues against its colour.
        int[] physical=OpaqueOverlayPhotoPalette.arrange(swatches,count,preparedPhoto);
        // L1 must never move; inactive swatches remain in their parked slots.
        return PaletteOrientation.reverseActive(physical,count,reverse,true);
    }

    /** The saved entry already describes the vinyl order; preserve every slot. */
    public static int[] saved(int[] savedPhysical,int count,boolean reverse){
        validate(savedPhysical,count);
        return PaletteOrientation.reverseActive(savedPhysical,count,reverse,true);
    }

    public static int[] savedPreview(int[] savedPhysical,int count,boolean reverse,int hueDegrees){
        return PaletteHueShift.rotate(saved(savedPhysical,count,reverse),hueDegrees);
    }
}

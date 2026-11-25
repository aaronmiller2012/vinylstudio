package com.halftonestudio;

/** Eight persistent physical vinyl swatches for the additional multicolor styles.
 * Applying a preset is explicit. Subsequent layer-count changes must not
 * reorder or replace these colors. Lead is always physical slot 0 for glass.
 */
public final class NewStylePalettePresets {
    private NewStylePalettePresets() {}
    public static int[] standard(int style,int count,int paper,int id) {
        if(!NewStyleColorSlots.multi(style))
            throw new IllegalArgumentException("Not a multicolor art style");
        return PaletteStylePolicy.arrange(PaletteStylePolicy.familyForNewStyle(style),
            VinylPalettes.get(id,8),count,paper);
    }
    public static int[] creative(int style,int count,int paper,int id) {
        if(!NewStyleColorSlots.multi(style))
            throw new IllegalArgumentException("Not a multicolor art style");
        return PaletteStylePolicy.arrange(PaletteStylePolicy.familyForNewStyle(style),
            ColorWorkspacePalette.tessPalette(id),count,paper);
    }
    /** Stored named palettes contain base colors and a separately saved hue shift. */
    public static int[] saved(int[] bases,int hueDegrees) {
        if(bases==null||(bases.length!=8&&bases.length!=9))
            throw new IllegalArgumentException("Eight or nine physical vinyl colors required");
        return PaletteHueShift.rotate(bases,hueDegrees);
    }
}

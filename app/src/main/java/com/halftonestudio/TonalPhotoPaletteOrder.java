package com.halftonestudio;

import java.util.Arrays;

/** Vinyl appearance mapping for automatic source-color tonal portraits.
 * SourceColorStencilQuantizer sorts cluster IDs dark-to-light in CIELAB.
 * Physical layer placement uses area/coverage and must NOT be used to
 * assign photographic color. No polygon, owner or cut mask is modified.
 */
public final class TonalPhotoPaletteOrder {
    private TonalPhotoPaletteOrder() { }

    /** Copy a chosen palette into stable dark-to-light lightness order.
     * Tie order is stable, preserving related equal-lightness hues. */
    public static int[] sortDarkToLight(int[] chosen) {
        if (chosen == null || chosen.length < 2 || chosen.length > 8)
            throw new IllegalArgumentException("Tonal palette needs 2 to 8 colors");
        int[] sorted = chosen.clone();
        for (int color : sorted) if ((color >>> 24) != 255)
            throw new IllegalArgumentException("Transparent tonal vinyl swatch");
        for (int i = 1; i < sorted.length; i++) {
            int selected = sorted[i];
            double lum = ColorWorkspacePalette.lightness(selected);
            int j = i - 1;
            while (j >= 0 && ColorWorkspacePalette.lightness(sorted[j]) > lum + 1e-9) {
                sorted[j + 1] = sorted[j];
                j--;
            }
            sorted[j + 1] = selected;
        }
        return sorted;
    }

    /** The photograph's source-color ID is a tonal rank, not L1..Ln.
     * Only a new operator-selected preset calls this. Saved project overrides,
     * individually picked swatches and named physical palettes are untouched. */
    public static int[] mapToSource(int[] existing, int[] picked, int active, int hue) {
        if (existing == null || existing.length < 8 || picked == null ||
            active < 2 || active > 8 || picked.length != active ||
            hue < -180 || hue > 180)
            throw new IllegalArgumentException("Invalid tonal source palette");
        int[] sorted = sortDarkToLight(picked);
        int[] result = Arrays.copyOf(existing, existing.length);
        for (int source = 0; source < active; source++)
            result[source] = PaletteHueShift.rotateOne(sorted[source], -hue);
        return result;
    }
}

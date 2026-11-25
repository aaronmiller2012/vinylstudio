package com.halftonestudio;

/**
 * The tonal engine stores vinyl overrides by photographic source-color ID,
 * but displays/exports layers in physical (bottom-to-top) stack order.
 * A color edit in L1..LN must always be mapped through sourceIndex.
 */
public final class TonalPaletteMapping {
    private TonalPaletteMapping() { }

    public static int[] display(int[] rendered, int[] sourceOrder, int[] overrides,
                                int renderedHue, int selectedHue) {
        validate(rendered, sourceOrder, overrides);
        int[] shown = new int[sourceOrder.length];
        for (int i = 0; i < shown.length; i++) {
            int source = sourceOrder[i];
            int override = overrides[source];
            // A fresh result contains exact displayed colors, including manual
            // selections. A pending render must not show stale source RGB.
            if (renderedHue == selectedHue && override == 0) {
                shown[i] = rendered[i];
            } else if (override != 0) {
                shown[i] = PaletteHueShift.rotateOne(override, selectedHue);
            } else {
                shown[i] = PaletteHueShift.rotateOne(
                    PaletteHueShift.rotateOne(rendered[i], -renderedHue), selectedHue);
            }
        }
        return shown;
    }

    /** Save a palette selected in physical order as source-index overrides. */
    public static int[] fromPhysical(int[] oldOverrides, int[] physical,
                                     int[] sourceOrder, int selectedHue) {
        validate(physical, sourceOrder, oldOverrides);
        int[] next = oldOverrides.clone();
        for (int i = 0; i < physical.length; i++) {
            // Palette entries are stored as base RGB, and Hue is applied once
            // by the tonal renderer after photo separation.
            next[sourceOrder[i]] = PaletteHueShift.rotateOne(physical[i], -selectedHue);
        }
        return next;
    }

    public static int[] physicalOrder(int[] sourceOrder, int count) {
        if (sourceOrder == null || count < 1 || count > sourceOrder.length)
            throw new IllegalArgumentException("No tonal physical layers available");
        int[] result = new int[count];
        System.arraycopy(sourceOrder, 0, result, 0, count);
        return result;
    }

    private static void validate(int[] physical, int[] sourceOrder, int[] overrides) {
        if (physical == null || sourceOrder == null || overrides == null ||
            physical.length == 0 || physical.length != sourceOrder.length ||
            physical.length > overrides.length)
            throw new IllegalArgumentException("Invalid tonal palette mapping");
        boolean[] used = new boolean[overrides.length];
        for (int source : sourceOrder) {
            if (source < 0 || source >= overrides.length || used[source])
                throw new IllegalArgumentException("Invalid tonal layer source indices");
            used[source] = true;
        }
    }
}

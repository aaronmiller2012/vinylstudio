package com.halftonestudio;

/** Stateless RGB hue rotation for physical vinyl swatch previews and exports.
 * A saved immutable base palette is always the input: dragging back to zero
 * reproduces its exact original ARGB values without accumulated rounding drift.
 * S and V are preserved (to within eight-bit quantization), and grayscale
 * remains unchanged. The mounting surface is NEVER transformed.
 */
public final class PaletteHueShift {
    private PaletteHueShift() {}
    public static int clampDegrees(int degrees) {
        return Math.max(-180, Math.min(180, degrees));
    }
    public static int[] rotate(int[] base, int degrees) {
        if (base == null || (base.length != 8 && base.length != 9)) throw new IllegalArgumentException("Eight base swatches required");
        int[] out = base.clone();
        int change = clampDegrees(degrees);
        if (change == 0) return out;
        for (int i = 0; i < out.length; i++) out[i] = rotateOne(base[i], change);
        return out;
    }
    public static int rotateOne(int color, int degrees) {
        int amount = clampDegrees(degrees);
        if (amount == 0) return color;
        final float r = ((color >>> 16) & 255) / 255f;
        final float g = ((color >>> 8) & 255) / 255f;
        final float b = (color & 255) / 255f;
        final float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        final float chroma = max - min;
        if (chroma <= 0f) return color; // neutral colors cannot have a hue
        float hue;
        if (max == r) hue = 60f * ((g - b) / chroma);
        else if (max == g) hue = 60f * ((b - r) / chroma + 2f);
        else hue = 60f * ((r - g) / chroma + 4f);
        hue = (hue + amount) % 360f;
        if (hue < 0f) hue += 360f;
        float x = chroma * (1f - Math.abs((hue / 60f) % 2f - 1f));
        float rr, gg, bb;
        if (hue < 60f) { rr = chroma; gg = x; bb = 0f; }
        else if (hue < 120f) { rr = x; gg = chroma; bb = 0f; }
        else if (hue < 180f) { rr = 0f; gg = chroma; bb = x; }
        else if (hue < 240f) { rr = 0f; gg = x; bb = chroma; }
        else if (hue < 300f) { rr = x; gg = 0f; bb = chroma; }
        else { rr = chroma; gg = 0f; bb = x; }
        float offset = min;
        int ri = Math.max(0, Math.min(255, Math.round((rr + offset) * 255f)));
        int gi = Math.max(0, Math.min(255, Math.round((gg + offset) * 255f)));
        int bi = Math.max(0, Math.min(255, Math.round((bb + offset) * 255f)));
        return (color & 0xff000000) | (ri << 16) | (gi << 8) | bi;
    }
}

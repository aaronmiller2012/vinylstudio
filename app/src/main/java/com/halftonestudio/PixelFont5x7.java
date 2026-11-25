package com.halftonestudio;

import java.util.*;

/** Built-in 5x7 bitmap font as exact vector outlines (pixel runs merged, holes kept). Android-free. */
public final class PixelFont5x7 implements TextHalftoneEngine.GlyphSource {
    private static final Map<Character, String> ROWS = new HashMap<>();
    private static void g(char c, String rows) { ROWS.put(c, rows); }
    static {
        g('0', "01110,10001,10011,10101,11001,10001,01110"); g('1', "00100,01100,00100,00100,00100,00100,01110");
        g('2', "01110,10001,00001,00010,00100,01000,11111"); g('3', "11110,00001,00001,01110,00001,00001,11110");
        g('4', "00010,00110,01010,10010,11111,00010,00010"); g('5', "11111,10000,11110,00001,00001,10001,01110");
        g('6', "00110,01000,10000,11110,10001,10001,01110"); g('7', "11111,00001,00010,00100,01000,01000,01000");
        g('8', "01110,10001,10001,01110,10001,10001,01110"); g('9', "01110,10001,10001,01111,00001,00010,01100");
        g('A', "01110,10001,10001,11111,10001,10001,10001"); g('B', "11110,10001,10001,11110,10001,10001,11110");
        g('C', "01110,10001,10000,10000,10000,10001,01110"); g('D', "11110,10001,10001,10001,10001,10001,11110");
        g('E', "11111,10000,10000,11110,10000,10000,11111"); g('F', "11111,10000,10000,11110,10000,10000,10000");
        g('G', "01110,10001,10000,10111,10001,10001,01111"); g('H', "10001,10001,10001,11111,10001,10001,10001");
        g('I', "01110,00100,00100,00100,00100,00100,01110"); g('J', "00111,00010,00010,00010,00010,10010,01100");
        g('K', "10001,10010,10100,11000,10100,10010,10001"); g('L', "10000,10000,10000,10000,10000,10000,11111");
        g('M', "10001,11011,10101,10101,10001,10001,10001"); g('N', "10001,10001,11001,10101,10011,10001,10001");
        g('O', "01110,10001,10001,10001,10001,10001,01110"); g('P', "11110,10001,10001,11110,10000,10000,10000");
        g('Q', "01110,10001,10001,10001,10101,10010,01101"); g('R', "11110,10001,10001,11110,10100,10010,10001");
        g('S', "01111,10000,10000,01110,00001,00001,11110"); g('T', "11111,00100,00100,00100,00100,00100,00100");
        g('U', "10001,10001,10001,10001,10001,10001,01110"); g('V', "10001,10001,10001,10001,10001,01010,00100");
        g('W', "10001,10001,10001,10101,10101,10101,01010"); g('X', "10001,10001,01010,00100,01010,10001,10001");
        g('Y', "10001,10001,01010,00100,00100,00100,00100"); g('Z', "11111,00001,00010,00100,01000,10000,11111");
        g('.', "00000,00000,00000,00000,00000,01100,01100"); g(',', "00000,00000,00000,00000,01100,00100,01000");
        g(':', "00000,01100,01100,00000,01100,01100,00000"); g('-', "00000,00000,00000,11111,00000,00000,00000");
        g('+', "00000,00100,00100,11111,00100,00100,00000"); g('=', "00000,00000,11111,00000,11111,00000,00000");
        g('*', "00000,10101,01110,11111,01110,10101,00000"); g('#', "01010,01010,11111,01010,11111,01010,01010");
        g('%', "11000,11001,00010,00100,01000,10011,00011"); g('@', "01110,10001,10111,10101,10110,10000,01110");
        g('/', "00001,00010,00010,00100,01000,01000,10000"); g('!', "00100,00100,00100,00100,00100,00000,00100");
        g('?', "01110,10001,00001,00010,00100,00000,00100"); g('&', "01100,10010,10100,01000,10101,10010,01101");
        g('\\', "10000,01000,01000,00100,00010,00010,00001");
        g('|', "00100,00100,00100,00100,00100,00100,00100");
        g('_', "00000,00000,00000,00000,00000,00000,11111");
        g('\u2588', "11111,11111,11111,11111,11111,11111,11111");
    }
    /** Stable 35-bit mask in row-major order, matching the actual cuttable glyph geometry. */
    public static long mask(int codePoint) {
        String rows = ROWS.get(Character.toUpperCase((char) codePoint));
        if (rows == null) return 0L;
        long result = 0L;
        int bit = 0;
        for (int i = 0; i < rows.length(); i++) {
            char c = rows.charAt(i);
            if (c == ',') continue;
            if (c == '1') result |= 1L << bit;
            bit++;
        }
        return result;
    }

    private final HashMap<Integer, ArrayList<float[]>> cache = new HashMap<>();

    @Override public float aspect(int cp) { return 5f / 7f; }

    /** Filled 4-neighbours of (x,y); the glyph edge counts as empty. */
    private static int filled(boolean[] on, int x, int y) {
        int n = 0;
        if (x > 0 && on[y * 5 + x - 1]) n++; if (x < 4 && on[y * 5 + x + 1]) n++;
        if (y > 0 && on[(y - 1) * 5 + x]) n++; if (y < 6 && on[(y + 1) * 5 + x]) n++;
        return n;
    }
    @Override public ArrayList<float[]> outline(int cp) {
        char c = Character.toUpperCase((char) cp);
        ArrayList<float[]> hit = cache.get((int) c); if (hit != null) return hit;
        String rows = ROWS.get(c); ArrayList<float[]> loops = new ArrayList<>();
        if (rows != null) {
            String[] r = rows.split(","); int gw = 6; float[] pts = new float[2 * gw * 8];
            for (int y = 0; y <= 7; y++) for (int x = 0; x <= 5; x++) { pts[2 * (y * gw + x)] = x / 7f; pts[2 * (y * gw + x) + 1] = y / 7f; }
            ArrayList<Integer> tri = new ArrayList<>();
            boolean[] on = new boolean[35];
            for (int y = 0; y < 7; y++) for (int x = 0; x < 5; x++) on[y * 5 + x] = r[y].charAt(x) == '1';
            // Two pixels meeting only at a corner make a zero-width vinyl
            // joint that a blade cannot cut or weed. Bridge each such corner
            // with one orthogonal pixel (on the stroke's outer side).
            // Empty pixels meeting only at a corner pinch the outline into a
            // self-touching vertex; fill the more enclosed of the two.
            for (int pass = 0; pass < 8; pass++) {
                boolean changed = false;
                for (int y = 0; y < 6; y++) for (int x = 0; x < 4; x++) {
                    int i00 = y * 5 + x, i01 = i00 + 1, i10 = i00 + 5, i11 = i00 + 6;
                    boolean tl = on[i00], tr = on[i01], bl = on[i10], br = on[i11];
                    if (tl && br && !tr && !bl) { on[i01] = true; changed = true; }
                    else if (tr && bl && !tl && !br) { on[i00] = true; changed = true; }
                    else if (!tl && !br && tr && bl) { on[filled(on, x, y) >= filled(on, x + 1, y + 1) ? i00 : i11] = true; changed = true; }
                    else if (!tr && !bl && tl && br) { on[filled(on, x + 1, y) >= filled(on, x, y + 1) ? i01 : i10] = true; changed = true; }
                }
                if (!changed) break;
            }
            for (int y = 0; y < 7; y++) for (int x = 0; x < 5; x++) if (on[y * 5 + x]) {
                int a = y * gw + x, b = a + 1, cc = a + gw + 1, d = a + gw; tri.add(a); tri.add(b); tri.add(cc); tri.add(a); tri.add(cc); tri.add(d);
            }
            if (!tri.isEmpty()) { int[] t = new int[tri.size()]; for (int i = 0; i < t.length; i++) t[i] = tri.get(i); int[] sub = new int[t.length / 3]; for (int i = 0; i < sub.length; i++) sub[i] = i; loops = StyleKit.mergeTriangles(pts, t, sub); }
        }
        cache.put((int) c, loops); return loops;
    }
}

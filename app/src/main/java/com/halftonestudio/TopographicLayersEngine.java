package com.halftonestudio;

import java.util.*;

/**
 * Topographic / papercut layers: tone is elevation. Marching squares on a smoothed tone field gives
 * closed contours; LAYERS emits one stacked vinyl layer per elevation (each layer is the region at or
 * above its level, holes included), LINES emits the isolines themselves (optionally as cuttable ribbons).
 */
public final class TopographicLayersEngine {
    private TopographicLayersEngine() {}

    public enum Mode { LAYERS, LINES }
    public enum Spacing { EQUAL_AREA, EQUAL_TONE }

    public static final class Options {
        public float widthMm = 152.4f;
        public Mode mode = Mode.LAYERS;
        public Spacing spacing = Spacing.EQUAL_AREA; // EQUAL_AREA: every layer covers a similar amount of the picture
        public int levels = 7;                  // LAYERS: 2..14 stacked layers (incl. base); LINES: number of isolines (1..60)
        public boolean darkIsHigh = true;       // true: shadows are peaks
        public float smoothMm = 1.4f;           // field blur; larger = rounder, calmer contours
        public float chaikinPasses = 1;         // 0..3 extra rounding
        public float simplifyMm = 0.06f;        // contour simplification tolerance
        public float minAreaMm2 = 5f;           // drop specks smaller than this (cannot be weeded)
        public int lightArgb = 0xffe8dcc2, darkArgb = 0xff3b2a1d, paperArgb = 0xfff7f3ea;
        /** Exact physical vinyl swatches, one per level; never recolored. */
        public int[] palette=null;
        public int lineArgb = 0xff2a2118;
        public float lineMm = 0.35f;            // LINES stroke width; with ribbon=true this becomes polygon width
        public boolean ribbon = true;           // LINES: emit cuttable ribbon polygons instead of strokes
        public int indexEvery = 5;              // LINES: every nth line is drawn heavier (0 = none)
        public float indexLineMm = 0.7f;
        public boolean autoLevels = true;
        public float gamma = 1f, contrast = 1f;
        public boolean[] allowed = null;
        public Options copy() { Options o = new Options(); o.widthMm = widthMm; o.mode = mode; o.spacing = spacing; o.levels = levels; o.darkIsHigh = darkIsHigh; o.smoothMm = smoothMm; o.chaikinPasses = chaikinPasses; o.simplifyMm = simplifyMm; o.minAreaMm2 = minAreaMm2; o.lightArgb = lightArgb; o.darkArgb = darkArgb; o.paperArgb = paperArgb; o.palette=palette==null?null:palette.clone(); o.lineArgb = lineArgb; o.lineMm = lineMm; o.ribbon = ribbon; o.indexEvery = indexEvery; o.indexLineMm = indexLineMm; o.autoLevels = autoLevels; o.gamma = gamma; o.contrast = contrast; o.allowed = allowed; return o; }
        void validate() {
            if (mode == Mode.LAYERS && (levels < 2 || levels > 14)) throw new IllegalArgumentException("levels must be 2..14 for LAYERS");
            if (mode == Mode.LINES && (levels < 1 || levels > 60)) throw new IllegalArgumentException("levels must be 1..60 for LINES");
            if (smoothMm < 0 || smoothMm > 20) throw new IllegalArgumentException("smoothMm must be 0..20");
            if (lineMm < 0.1f) throw new IllegalArgumentException("lineMm too thin");
            if (palette != null && palette.length != levels)
                throw new IllegalArgumentException("Vinyl swatches must match physical topographic levels");
        }
    }

    public static StyleKit.Result generate(int[] argb, int w, int h, Options o) {
        o.validate();
        StyleKit.Source src = new StyleKit.Source(argb, w, h, o.allowed, o.widthMm);
        if (o.autoLevels) src.autoLevels(.02f, .98f);
        src.tone(o.gamma, o.contrast);
        if (!src.anyValid()) throw new IllegalArgumentException("No usable pixels");
        float scale = Math.min(1f, 420f / Math.max(src.w, src.h));
        int fw = Math.max(8, Math.round(src.w * scale)), fh = Math.max(8, Math.round(src.h * scale)); float px = src.widthMm / fw;
        float[] f = new float[fw * fh]; boolean[] valid = new boolean[fw * fh];
        for (int y = 0; y < fh; y++) for (int x = 0; x < fw; x++) {if(((x)&63)==0)CancellationCheckpoint.check(); float mx = (x + .5f) * px, my = (y + .5f) * px; valid[y * fw + x] = src.validAt(mx, my); float l = src.lumAt(mx, my); f[y * fw + x] = o.darkIsHigh ? 1f - l : l; }
        f = StyleKit.blur(f, fw, fh, o.smoothMm / px);
        // padded field: outside / masked = -1 so every contour closes
        int pw = fw + 2, ph = fh + 2; float[] pf = new float[pw * ph]; Arrays.fill(pf, -1f);
        ArrayList<Float> vals = new ArrayList<>();
        for (int y = 0; y < fh; y++) for (int x = 0; x < fw; x++) if (valid[y * fw + x]) { pf[(y + 1) * pw + x + 1] = f[y * fw + x]; vals.add(f[y * fw + x]); }
        if (vals.isEmpty()) throw new IllegalArgumentException("No usable pixels");
        float[] sorted = new float[vals.size()]; for (int i = 0; i < sorted.length; i++) sorted[i] = vals.get(i); Arrays.sort(sorted);
        int L = o.levels; float[] th = new float[L];
        if (o.mode == Mode.LAYERS) {
            th[0] = -0.5f; // base = whole valid area
            for (int k = 1; k < L; k++) th[k] = o.spacing == Spacing.EQUAL_AREA ? sorted[Math.min(sorted.length - 1, (int) (sorted.length * (k / (float) L)))] : sorted[0] + (sorted[sorted.length - 1] - sorted[0]) * k / L;
        } else for (int k = 0; k < L; k++) th[k] = o.spacing == Spacing.EQUAL_AREA ? sorted[Math.min(sorted.length - 1, (int) (sorted.length * ((k + 1) / (float) (L + 1))))] : sorted[0] + (sorted[sorted.length - 1] - sorted[0]) * (k + 1) / (L + 1);
        StyleKit.Result res = new StyleKit.Result(src.widthMm, src.heightMm); res.paperArgb = o.paperArgb;
        int[] colors = o.palette==null?StyleKit.ramp(o.lightArgb,o.darkArgb,Math.max(2,L)):
            o.palette.clone(); int loopsTotal = 0;
        for (int k = 0; k < L; k++) {
            StyleKit.checkCancelled();
            ArrayList<float[]> loops = contours(pf, pw, ph, th[k], px);
            ArrayList<float[]> clean = new ArrayList<>();
            for (float[] lp : loops) {CancellationCheckpoint.check();
                float[] p = o.chaikinPasses > 0 ? StyleKit.chaikin(lp, 2, true, (int) o.chaikinPasses) : lp;
                if (o.simplifyMm > 0) { float[] open = Arrays.copyOf(p, p.length + 2); open[p.length] = p[0]; open[p.length + 1] = p[1]; float[] s = StyleKit.rdp(open, o.simplifyMm); if (s.length >= 8) p = Arrays.copyOf(s, s.length - 2); }
                p=StyleKit.clipToArtboardOrOriginal(p,src.widthMm,src.heightMm);
                if (Math.abs(StyleKit.signedArea(p)) >= o.minAreaMm2 && p.length >= 6) clean.add(p);
            }
            loopsTotal += clean.size();
            if (o.mode == Mode.LAYERS) { StyleKit.Layer layer = new StyleKit.Layer("topo-" + (k + 1), colors[k]); layer.loops.addAll(clean); res.layers.add(layer); }
            else {
                boolean heavy = o.indexEvery > 0 && (k + 1) % o.indexEvery == 0; float wmm = heavy ? o.indexLineMm : o.lineMm;
                StyleKit.Layer layer = new StyleKit.Layer("isoline-" + (k + 1),
                    o.palette==null?o.lineArgb:colors[k]); layer.strokeMm = wmm;
                for (float[] p : clean) {CancellationCheckpoint.check();
                    if (o.ribbon) { float[] hw = new float[p.length / 2]; Arrays.fill(hw, wmm / 2); ArrayList<float[]> two = StyleKit.ribbonClosed(p, hw, 2f);
                        // A tight bend folds the offset outline over itself; a
                        // self-crossing contour cannot be cut, so skip that ring.
                        boolean crossing = false; if (two != null) for (float[] q : two) if (StyleKit.selfIntersections(q, 1) > 0) { crossing = true; break; }
                        if (two != null && !crossing) layer.loops.addAll(two); }
                    else { float[] line = Arrays.copyOf(p, p.length + 2); line[p.length] = p[0]; line[p.length + 1] = p[1]; layer.lines.add(line); }
                }
                res.layers.add(layer);
            }
        }
        res.summary = "Topographic " + o.mode + ": " + L + " levels, " + loopsTotal + " contours";
        return res;
    }

    /** Marching squares on a padded field; returns closed loops in mm (pad offset removed). */
    static ArrayList<float[]> contours(float[] f, int w, int h, float t, float px) {
        int[] a0 = new int[2 * w * h], a1 = new int[2 * w * h]; Arrays.fill(a0, -1); Arrays.fill(a1, -1);
        for (int j = 0; j < h - 1; j++) for (int i = 0; i < w - 1; i++) {if(((i)&63)==0)CancellationCheckpoint.check();
            float tl = f[j * w + i], tr = f[j * w + i + 1], br = f[(j + 1) * w + i + 1], bl = f[(j + 1) * w + i];
            boolean btl = tl >= t, btr = tr >= t, bbr = br >= t, bbl = bl >= t;
            int top = (j * w + i) * 2, bottom = ((j + 1) * w + i) * 2, left = (j * w + i) * 2 + 1, right = (j * w + i + 1) * 2 + 1;
            boolean cT = btl != btr, cR = btr != bbr, cB = bbl != bbr, cL = btl != bbl; int cnt = (cT ? 1 : 0) + (cR ? 1 : 0) + (cB ? 1 : 0) + (cL ? 1 : 0);
            if (cnt == 2) { int[] e = new int[2]; int m = 0; if (cT) e[m++] = top; if (cR) e[m++] = right; if (cB) e[m++] = bottom; if (cL) e[m++] = left; link(a0, a1, e[0], e[1]); }
            else if (cnt == 4) {
                boolean centerIn = (tl + tr + br + bl) / 4f >= t;
                if (btl == bbr) { // tl & br share state
                    if (btl == centerIn) { link(a0, a1, top, right); link(a0, a1, bottom, left); } else { link(a0, a1, top, left); link(a0, a1, bottom, right); }
                } else { if (btr == centerIn) { link(a0, a1, top, left); link(a0, a1, bottom, right); } else { link(a0, a1, top, right); link(a0, a1, bottom, left); } }
            }
        }
        ArrayList<float[]> loops = new ArrayList<>(); boolean[] seen = new boolean[a0.length];
        for (int e = 0; e < a0.length; e++) {if(((e)&63)==0)CancellationCheckpoint.check();
            if (a0[e] < 0 || seen[e]) continue;
            ArrayList<float[]> pts = new ArrayList<>(); int prev = -1, cur = e; boolean closed = false;
            while (true) {
                seen[cur] = true; pts.add(edgePoint(f, w, cur, t, px));
                int nx = (a0[cur] >= 0 && a0[cur] != prev && !seen[a0[cur]]) ? a0[cur] : ((a1[cur] >= 0 && a1[cur] != prev && !seen[a1[cur]]) ? a1[cur] : -1);
                if (nx < 0) { closed = (a0[cur] == e || a1[cur] == e); break; } prev = cur; cur = nx;
            }
            if (pts.size() >= 4 && closed) { float[] lp = new float[2 * pts.size()]; for (int i = 0; i < pts.size(); i++) {if(((i)&63)==0)CancellationCheckpoint.check(); lp[2 * i] = pts.get(i)[0]; lp[2 * i + 1] = pts.get(i)[1]; } loops.add(lp); }
        }
        return loops;
    }
    private static void link(int[] a0, int[] a1, int a, int b) { if (a0[a] < 0) a0[a] = b; else a1[a] = b; if (a0[b] < 0) a0[b] = a; else a1[b] = a; }
    private static float[] edgePoint(float[] f, int w, int id, float t, float px) {
        int cell = id / 2, i = cell % w, j = cell / w; float x, y;
        if ((id & 1) == 0) { float a = f[j * w + i], b = f[j * w + i + 1], u = Math.abs(b - a) < 1e-9f ? .5f : StyleKit.clampF((t - a) / (b - a), 0, 1); x = i + u; y = j; }
        else { float a = f[j * w + i], b = f[(j + 1) * w + i], u = Math.abs(b - a) < 1e-9f ? .5f : StyleKit.clampF((t - a) / (b - a), 0, 1); x = i; y = j + u; }
        return new float[]{(x - 1 + .5f) * px, (y - 1 + .5f) * px};
    }
}

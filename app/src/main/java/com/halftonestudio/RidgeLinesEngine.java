package com.halftonestudio;

import java.util.*;

/**
 * Joy Division / "unknown pleasures" ridge lines. Horizontal lines are displaced by tone (RIDGE) or
 * carry a sine whose amplitude follows tone (SINE). Hidden-line removal lets front lines occlude the
 * ones behind them, and with a ribbon width the visible runs are offset so neighbouring ribbons never overlap.
 */
public final class RidgeLinesEngine {
    private RidgeLinesEngine() {}

    public enum Mode { RIDGE, SINE }

    public static final class Options {
        public float widthMm = 152.4f;
        public int lineCount = 70;               // 10..300
        public Mode mode = Mode.RIDGE;
        public float amplitudeLines = 4.5f;      // peak height measured in line spacings (0.5..12)
        public float waveMm = 2.4f;              // SINE: wavelength
        public float smoothMm = 0.9f;            // horizontal tone smoothing (kills pixel noise on the ridges)
        public boolean peakOnDark = true;        // false: bright areas rise
        public boolean hiddenLines = true;
        public float ribbonMm = 0.6f;            // 0 = open polylines (stroke); >0 = cuttable ribbon polygons
        public float gapMm = 0.25f;              // clearance between a front ribbon and the one hidden behind it
        public float stepMm = 0.3f;              // sampling step along each line
        public float minRunMm = 1.2f;            // drop visible fragments shorter than this
        public float marginMm = 4f;
        public int inkArgb = 0xff0e0e12, paperArgb = 0xffffffff;
        public boolean autoLevels = true;
        public float gamma = 1f, contrast = 1f;
        public boolean[] allowed = null;
        public Options copy() { Options o = new Options(); o.widthMm = widthMm; o.lineCount = lineCount; o.mode = mode; o.amplitudeLines = amplitudeLines; o.waveMm = waveMm; o.smoothMm = smoothMm; o.peakOnDark = peakOnDark; o.hiddenLines = hiddenLines; o.ribbonMm = ribbonMm; o.gapMm = gapMm; o.stepMm = stepMm; o.minRunMm = minRunMm; o.marginMm = marginMm; o.inkArgb = inkArgb; o.paperArgb = paperArgb; o.autoLevels = autoLevels; o.gamma = gamma; o.contrast = contrast; o.allowed = allowed; return o; }
        void validate() {
            if (lineCount < 10 || lineCount > 300) throw new IllegalArgumentException("lineCount must be 10..300");
            if (amplitudeLines < 0.5f || amplitudeLines > 12) throw new IllegalArgumentException("amplitudeLines must be 0.5..12");
            if (stepMm < 0.1f || stepMm > 2) throw new IllegalArgumentException("stepMm must be 0.1..2");
            if (ribbonMm < 0 || ribbonMm > 3) throw new IllegalArgumentException("ribbonMm must be 0..3");
            if (waveMm < 0.8f) throw new IllegalArgumentException("waveMm too small");
        }
    }

    public static StyleKit.Result generate(int[] argb, int w, int h, Options o) {
        o.validate();
        StyleKit.Source src = new StyleKit.Source(argb, w, h, o.allowed, o.widthMm);
        if (o.autoLevels) src.autoLevels(.02f, .98f);
        src.tone(o.gamma, o.contrast);
        if (!src.anyValid()) throw new IllegalArgumentException("No usable pixels");
        float W = src.widthMm, H = src.heightMm, x0 = o.marginMm, x1 = W - o.marginMm;
        float top0 = o.marginMm + o.amplitudeLines * ((H - 2 * o.marginMm) / (o.lineCount - 1 + o.amplitudeLines)), bottom0 = H - o.marginMm;
        float spacing = (bottom0 - top0) / (o.lineCount - 1), amp = o.amplitudeLines * spacing, hw = o.ribbonMm / 2;
        int ns = Math.max(8, (int) ((x1 - x0) / o.stepMm) + 1); float[] xs = new float[ns];
        for (int k = 0; k < ns; k++) xs[k] = x0 + (x1 - x0) * k / (ns - 1);
        // horizontally smoothed tone per line (blur a one-row-tall strip at source resolution)
        float[] toneField = src.lum.clone(); for (int i = 0; i < toneField.length; i++) toneField[i] = o.peakOnDark ? 1f - toneField[i] : toneField[i];
        float[] blurred = StyleKit.blur(toneField, src.w, src.h, Math.max(0.5f, o.smoothMm / src.mmPerPx));
        StyleKit.Result res = new StyleKit.Result(W, H); res.paperArgb = o.paperArgb;
        StyleKit.Layer layer = new StyleKit.Layer("ridge-lines", o.inkArgb); layer.strokeMm = Math.max(0.2f, o.ribbonMm); res.layers.add(layer);
        float[] env = new float[ns]; Arrays.fill(env, Float.MAX_VALUE);
        int runs = 0;
        for (int li = o.lineCount - 1; li >= 0; li--) {if(((li)&63)==0)CancellationCheckpoint.check();
            if ((li & 7) == 0) StyleKit.checkCancelled();
            float base = top0 + li * spacing, imgY = Math.min(H - 0.01f, H * li / (o.lineCount - 1f));
            float[] ys = new float[ns]; boolean[] ok = new boolean[ns];
            for (int k = 0; k < ns; k++) {if(((k)&63)==0)CancellationCheckpoint.check();
                int pi = src.pixelIndex(xs[k], imgY);
                float tone = StyleKit.clampF(blurred[pi], 0f, 1f); ok[k] = src.valid[pi];
                float disp = o.mode == Mode.RIDGE ? amp * tone : amp * tone * (float) Math.sin(2 * Math.PI * xs[k] / o.waveMm);
                ys[k] = base - disp;
            }
            boolean[] vis = new boolean[ns]; float[] g = new float[ns];
            for (int k = 0; k < ns; k++) {if(((k)&63)==0)CancellationCheckpoint.check(); g[k] = o.hiddenLines ? env[k] - (ys[k] + hw) : 1f; vis[k] = ok[k] && g[k] >= 0; }
            // runs of visible samples with interpolated entry/exit points
            int k = 0;
            while (k < ns) {
                if (!vis[k]) { k++; continue; }
                int s = k; while (k < ns && vis[k]) k++; int e = k - 1;
                ArrayList<float[]> pts = new ArrayList<>();
                if (s > 0 && ok[s - 1] && o.hiddenLines) { float t = g[s] / (g[s] - g[s - 1]); pts.add(new float[]{xs[s] + (xs[s - 1] - xs[s]) * t, ys[s] + (ys[s - 1] - ys[s]) * t}); }
                for (int q = s; q <= e; q++) pts.add(new float[]{xs[q], ys[q]});
                if (e < ns - 1 && ok[e + 1] && o.hiddenLines) { float t = g[e] / (g[e] - g[e + 1]); pts.add(new float[]{xs[e] + (xs[e + 1] - xs[e]) * t, ys[e] + (ys[e + 1] - ys[e]) * t}); }
                if (pts.size() < 2) continue;
                float[] xy = new float[2 * pts.size()]; for (int q = 0; q < pts.size(); q++) {if(((q)&63)==0)CancellationCheckpoint.check(); xy[2 * q] = pts.get(q)[0]; xy[2 * q + 1] = pts.get(q)[1]; }
                float len = 0; for (int q = 1; q < pts.size(); q++) len += Math.hypot(xy[2 * q] - xy[2 * q - 2], xy[2 * q + 1] - xy[2 * q - 1]);
                if (len < o.minRunMm) continue;
                xy = StyleKit.rdp(xy, 0.02f);
                if (o.ribbonMm > 0) { float[] poly = StyleKit.ribbon(xy, hw, 2f); if (poly != null) { layer.loops.add(poly); runs++; } } else { layer.lines.add(xy); runs++; }
            }
            if (o.hiddenLines) for (int q = 0; q < ns; q++) env[q] = Math.min(env[q], ys[q] - hw - o.gapMm);
        }
        res.summary = "Ridge lines " + o.mode + ": " + o.lineCount + " lines, " + runs + " visible runs";
        return res;
    }
}

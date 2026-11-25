package com.halftonestudio;

import java.util.*;

/**
 * TSP art: one continuous line through weighted-stipple points. Tone-weighted Voronoi stippling
 * (Lloyd relaxation on a density map) -> Hilbert-curve start tour -> neighbour-list 2-opt with
 * don't-look bits -> cut at the longest edge -> Chaikin smoothing. Optionally also emits a
 * tone-modulated ribbon polygon so the line can be cut as vinyl.
 */
public final class TspArtEngine {
    private TspArtEngine() {}

    public static final class Options {
        public float widthMm = 152.4f;
        public int pointCount = 7000;          // 200..20000 (more points = finer tonal detail)
        public int lloydIterations = 14;       // 0..40
        public float darknessGamma = 2.0f;     // >1 pushes points into the darkest areas
        public float minDensity = 0.0f;        // >0 keeps a faint line field in white areas
        public long timeBudgetMs = 6000;       // 2-opt work cap, expressed as nominal ms (deterministic: see WORK_PER_MS)
        public boolean openAtLongestEdge = true;
        public int smoothIterations = 2;       // Chaikin passes on the final line
        public float strokeMm = 0.6f;          // max line width (ribbon / stroke)
        public float minStrokeMm = 0.25f;
        public boolean ribbon = true;          // also emit cuttable ribbon polygon
        public int inkArgb = 0xff111111, paperArgb = 0xffffffff;
        public boolean autoLevels = true;      // stretch tones so the photo uses the full dark..light range
        public float gamma = 1f, contrast = 1f;
        public long seed = 3;
        public boolean[] allowed = null;
        public Options copy() { Options o = new Options(); o.widthMm = widthMm; o.pointCount = pointCount; o.lloydIterations = lloydIterations; o.darknessGamma = darknessGamma; o.minDensity = minDensity; o.timeBudgetMs = timeBudgetMs; o.openAtLongestEdge = openAtLongestEdge; o.smoothIterations = smoothIterations; o.strokeMm = strokeMm; o.minStrokeMm = minStrokeMm; o.ribbon = ribbon; o.autoLevels = autoLevels; o.inkArgb = inkArgb; o.paperArgb = paperArgb; o.gamma = gamma; o.contrast = contrast; o.seed = seed; o.allowed = allowed; return o; }
        void validate() {
            if (pointCount < 200 || pointCount > 20000) throw new IllegalArgumentException("pointCount must be 200..20000");
            if (lloydIterations < 0 || lloydIterations > 40) throw new IllegalArgumentException("lloydIterations must be 0..40");
            if (!Float.isFinite(strokeMm) || !Float.isFinite(minStrokeMm)
                    || strokeMm < minStrokeMm || minStrokeMm <= 0)
                throw new IllegalArgumentException("Invalid stroke range");
            if (smoothIterations < 0 || smoothIterations > 4) throw new IllegalArgumentException("smoothIterations must be 0..4");
        }
    }

    public static final class Stats { public double initialLength, finalLength; public int points, passes; }
    public static Stats lastStats = new Stats(); // diagnostic only (not thread safe)

    public static StyleKit.Result generate(int[] argb, int w, int h, Options o) {
        o.validate();
        StyleKit.Source src = new StyleKit.Source(argb, w, h, o.allowed, o.widthMm);
        if (o.autoLevels) src.autoLevels(.02f, .98f);
        src.tone(o.gamma, o.contrast);
        if (!src.anyValid()) throw new IllegalArgumentException("No usable pixels");
        float W = src.widthMm, H = src.heightMm;
        // density map at <=320 px
        float scale = Math.min(1f, 320f / Math.max(src.w, src.h));
        int dw = Math.max(8, Math.round(src.w * scale)), dh = Math.max(8, Math.round(src.h * scale));
        float pxMm = W / dw;
        float[] dens = new float[dw * dh]; double total = 0;
        for (int y = 0; y < dh; y++) for (int x = 0; x < dw; x++) {if(((x)&63)==0)CancellationCheckpoint.check();
            float mx = (x + .5f) * pxMm, my = (y + .5f) * pxMm;
            float v = src.validAt(mx, my) ? Math.max(o.minDensity, (float) Math.pow(src.darkAt(mx, my), o.darknessGamma)) : 0f;
            dens[y * dw + x] = v; total += v;
        }
        if (total <= 0) throw new IllegalArgumentException("Image is entirely white/masked");
        // 1) initial points by rejection sampling on density
        SplittableRandom rnd = new SplittableRandom(o.seed);
        int n = o.pointCount; float[] p = new float[2 * n]; int got = 0, tries = 0;
        while (got < n && tries++ < n * 4000) {
            int x = rnd.nextInt(dw), y = rnd.nextInt(dh); if ((float)rnd.nextDouble() <= dens[y * dw + x]) { p[2 * got] = (x + (float)rnd.nextDouble()) * pxMm; p[2 * got + 1] = (y + (float)rnd.nextDouble()) * pxMm; got++; }
        }
        if (got < n) { n = got; p = Arrays.copyOf(p, 2 * n); }
        if (n < 8) throw new IllegalArgumentException("Too few points");
        // 2) weighted Lloyd relaxation (Secord stippling)
        for (int it = 0; it < o.lloydIterations; it++) {
            StyleKit.checkCancelled();
            float cell = (float) Math.max(pxMm, Math.sqrt(W * (dh * pxMm) / n) * 1.2);
            int gw = (int) (W / cell) + 1, gh = (int) (dh * pxMm / cell) + 1;
            int[] head = new int[gw * gh]; Arrays.fill(head, -1); int[] nxt = new int[n];
            for (int i = 0; i < n; i++) {if(((i)&63)==0)CancellationCheckpoint.check(); int g = StyleKit.clampI((int) (p[2 * i + 1] / cell), 0, gh - 1) * gw + StyleKit.clampI((int) (p[2 * i] / cell), 0, gw - 1); nxt[i] = head[g]; head[g] = i; }
            double[] sx = new double[n], sy = new double[n], sw = new double[n];
            for (int y = 0; y < dh; y++) for (int x = 0; x < dw; x++) {if(((x)&63)==0)CancellationCheckpoint.check();
                float wgt = dens[y * dw + x]; if (wgt <= 0) continue;
                float mx = (x + .5f) * pxMm, my = (y + .5f) * pxMm;
                int best = nearest(p, head, nxt, gw, gh, cell, mx, my);
                sx[best] += mx * wgt; sy[best] += my * wgt; sw[best] += wgt;
            }
            for (int i = 0; i < n; i++) if (sw[i] > 0) { p[2 * i] = (float) (sx[i] / sw[i]); p[2 * i + 1] = (float) (sy[i] / sw[i]); }
        }
        // 3) Hilbert start tour
        int[] tour = hilbertOrder(p, n, W, H);
        // 4) neighbour lists + 2-opt
        int K = 8; int[][] nb = knn(p, n, K, W, H);
        double t0len = length(p, tour); lastStats.initialLength = t0len;
        int passes = twoOpt(p, tour, nb, o.timeBudgetMs);
        lastStats.finalLength = length(p, tour); lastStats.points = n; lastStats.passes = passes;
        // 5) open the loop at its longest edge
        if (o.openAtLongestEdge) {
            int bi = 0; double bl = -1;
            for (int i = 0; i < n; i++) {if(((i)&63)==0)CancellationCheckpoint.check(); int a = tour[i], b = tour[(i + 1) % n]; double l = Math.hypot(p[2 * a] - p[2 * b], p[2 * a + 1] - p[2 * b + 1]); if (l > bl) { bl = l; bi = i; } }
            int[] rot = new int[n]; for (int i = 0; i < n; i++) rot[i] = tour[(bi + 1 + i) % n]; tour = rot;
        }
        // 6) smooth the tour, then size the ribbon from the SMOOTHED line so it can never touch itself
        float[] xy0 = new float[2 * n]; for (int i = 0; i < n; i++) {if(((i)&63)==0)CancellationCheckpoint.check(); xy0[2 * i] = p[2 * tour[i]]; xy0[2 * i + 1] = p[2 * tour[i] + 1]; }
        float[] line = StyleKit.chaikin(xy0, 2, false, o.smoothIterations);
        int m = line.length / 2; float[] hw = ribbonHalfWidths(line, o.strokeMm / 2, o.minStrokeMm / 2);
        StyleKit.Result res = new StyleKit.Result(W, H); res.paperArgb = o.paperArgb;
        StyleKit.Layer ln = new StyleKit.Layer("tsp-line", o.inkArgb); ln.strokeMm = (o.strokeMm + o.minStrokeMm) / 2; ln.lines.add(line); res.layers.add(ln);
        int ribbonScale = -1;
        if (o.ribbon) {
            StyleKit.Layer rb = new StyleKit.Layer("tsp-ribbon", o.inkArgb);
            float[] hs = hw.clone(); int cap = 5, maxRepairs = 40;
            for (int attempt = 0; attempt <= maxRepairs; attempt++) {
                StyleKit.checkCancelled();
                float[] poly = StyleKit.ribbon(line, hs, 2.0f, true);
                if (poly == null) break;
                StyleKit.IntersectionCheck check = StyleKit.checkSelfIntersections(poly, 2000);
                if (check.indeterminate) break; // D11: never accept a ribbon that could not be proven simple
                ArrayList<int[]> bad = check.pairs;
                if (bad.isEmpty()) { rb.loops.add(poly); ribbonScale = attempt; break; }
                // narrow the ribbon around every crossing (map polygon edge -> centre-line point) and try again
                for (int[] pr : bad) for (int e : pr) {CancellationCheckpoint.check();
                    int pt = e < m ? e : (e < m + cap ? m - 1 : (e < 2 * m + cap ? m - 1 - (e - m - cap) : 0));
                    for (int k = Math.max(0, pt - 8); k <= Math.min(m - 1, pt + 8); k++) hs[k] = Math.max(0.02f, hs[k] * (attempt > 20 ? 0.6f : 0.82f));
                }
            }
            if (!rb.loops.isEmpty()) res.layers.add(rb);
        }
        res.summary = String.format(Locale.US, "TSP art: %d points, tour %.0f -> %.0f mm (%.1f%% shorter), %d 2-opt passes%s", n, t0len, lastStats.finalLength, 100 * (1 - lastStats.finalLength / t0len), passes, o.ribbon ? (ribbonScale < 0 ? ", ribbon NOT emitted (could not remove self-touching)" : ", ribbon ok after " + ribbonScale + " repair passes") : "");
        return res;
    }

    private static int nearest(float[] p, int[] head, int[] nxt, int gw, int gh, float cell, float x, float y) {
        int gx = StyleKit.clampI((int) (x / cell), 0, gw - 1), gy = StyleKit.clampI((int) (y / cell), 0, gh - 1);
        int best = -1; float bd = Float.MAX_VALUE;
        for (int r = 0; r <= Math.max(gw, gh); r++) {if(((r)&63)==0)CancellationCheckpoint.check();
            for (int yy = Math.max(0, gy - r); yy <= Math.min(gh - 1, gy + r); yy++) for (int xx = Math.max(0, gx - r); xx <= Math.min(gw - 1, gx + r); xx++) {if(((xx)&63)==0)CancellationCheckpoint.check();
                if (Math.max(Math.abs(xx - gx), Math.abs(yy - gy)) != r) continue;
                for (int i = head[yy * gw + xx]; i >= 0; i = nxt[i]) {if(((i)&63)==0)CancellationCheckpoint.check(); float dx = p[2 * i] - x, dy = p[2 * i + 1] - y, d = dx * dx + dy * dy; if (d < bd) { bd = d; best = i; } }
            }
            if (best >= 0 && Math.sqrt(bd) <= r * cell) break;
        }
        return best;
    }

    static int[] hilbertOrder(float[] p, int n, float W, float H) {
        long[] key = new long[n]; int side = 1 << 12; float s = Math.max(W, H);
        for (int i = 0; i < n; i++) {if(((i)&63)==0)CancellationCheckpoint.check();
            int x = StyleKit.clampI((int) (p[2 * i] / s * (side - 1)), 0, side - 1), y = StyleKit.clampI((int) (p[2 * i + 1] / s * (side - 1)), 0, side - 1);
            long d = 0; int xx = x, yy = y;
            for (int q = side / 2; q > 0; q /= 2) {if(((q)&63)==0)CancellationCheckpoint.check(); int rx = (xx & q) > 0 ? 1 : 0, ry = (yy & q) > 0 ? 1 : 0; d += (long) q * q * ((3 * rx) ^ ry); if (ry == 0) { if (rx == 1) { xx = side - 1 - xx; yy = side - 1 - yy; } int t = xx; xx = yy; yy = t; } }
            key[i] = (d << 20) | i;
        }
        Arrays.sort(key); int[] t = new int[n]; for (int i = 0; i < n; i++) t[i] = (int) (key[i] & 0xfffff); return t;
    }

    static int[][] knn(float[] p, int n, int K, float W, float H) {
        float cell = (float) Math.max(1e-3, Math.sqrt(W * H / n) * 1.5);
        int gw = (int) (W / cell) + 1, gh = (int) (H / cell) + 1; int[] head = new int[gw * gh]; Arrays.fill(head, -1); int[] nxt = new int[n];
        for (int i = 0; i < n; i++) {if(((i)&63)==0)CancellationCheckpoint.check(); int g = StyleKit.clampI((int) (p[2 * i + 1] / cell), 0, gh - 1) * gw + StyleKit.clampI((int) (p[2 * i] / cell), 0, gw - 1); nxt[i] = head[g]; head[g] = i; }
        int[][] out = new int[n][]; K = Math.min(K, n - 1);
        for (int i = 0; i < n; i++) {if(((i)&63)==0)CancellationCheckpoint.check();
            if ((i & 255) == 0) StyleKit.checkCancelled();
            int gx = StyleKit.clampI((int) (p[2 * i] / cell), 0, gw - 1), gy = StyleKit.clampI((int) (p[2 * i + 1] / cell), 0, gh - 1);
            ArrayList<long[]> cand = new ArrayList<>();
            for (int r = 0; r <= Math.max(gw, gh); r++) {if(((r)&63)==0)CancellationCheckpoint.check();
                for (int yy = Math.max(0, gy - r); yy <= Math.min(gh - 1, gy + r); yy++) for (int xx = Math.max(0, gx - r); xx <= Math.min(gw - 1, gx + r); xx++) {if(((xx)&63)==0)CancellationCheckpoint.check();
                    if (Math.max(Math.abs(xx - gx), Math.abs(yy - gy)) != r) continue;
                    for (int j = head[yy * gw + xx]; j >= 0; j = nxt[j]) if (j != i) { float dx = p[2 * j] - p[2 * i], dy = p[2 * j + 1] - p[2 * i + 1]; cand.add(new long[]{Float.floatToIntBits(dx * dx + dy * dy), j}); }
                }
                if (cand.size() >= K && r >= 1) { cand.sort((a, b) -> Long.compare(a[0], b[0])); float kth = Float.intBitsToFloat((int) cand.get(K - 1)[0]); if (Math.sqrt(kth) <= r * cell) break; }
            }
            cand.sort((a, b) -> Long.compare(a[0], b[0])); int k = Math.min(K, cand.size()); out[i] = new int[k]; for (int q = 0; q < k; q++) out[i][q] = (int) cand.get(q)[1];
        }
        return out;
    }

    static double length(float[] p, int[] t) { double s = 0; int n = t.length; for (int i = 0; i < n; i++) {if(((i)&63)==0)CancellationCheckpoint.check(); int a = t[i], b = t[(i + 1) % n]; s += Math.hypot(p[2 * a] - p[2 * b], p[2 * a + 1] - p[2 * b + 1]); } return s; }
    private static double d(float[] p, int a, int b) { return Math.hypot(p[2 * a] - p[2 * b], p[2 * a + 1] - p[2 * b + 1]); }

    /** Neighbour evaluations that one nominal millisecond of the old wall-clock budget stands for. */
    static final long WORK_PER_MS = 20_000L;
    /** Array 2-opt with neighbour lists and don't-look bits. Returns number of sweeps.
     *  The cap is workload (candidate evaluations), never wall clock, so the tour is identical
     *  on every device; CancellationCheckpoint still keeps it responsive to cancel. */
    static int twoOpt(float[] p, int[] tour, int[][] nb, long budgetMs) {
        int n = tour.length; int[] pos = new int[n]; for (int i = 0; i < n; i++) pos[tour[i]] = i;
        boolean[] dlb = new boolean[n]; long work = 0, maxWork = Math.max(0, budgetMs) * WORK_PER_MS; int sweeps = 0; boolean improved = true;
        outer:
        while (improved && work < maxWork) {
            improved = false; sweeps++; StyleKit.checkCancelled();
            for (int c = 0; c < n; c++) {if(((c)&63)==0)CancellationCheckpoint.check();
                if (dlb[c]) continue; boolean found = false;
                for (int dir = 0; dir < 2 && !found; dir++) {if(((dir)&63)==0)CancellationCheckpoint.check();
                    int t1 = c, i1 = pos[t1], t2 = tour[dir == 0 ? (i1 + 1) % n : (i1 + n - 1) % n];
                    double d12 = d(p, t1, t2);
                    for (int t3 : nb[t1]) {CancellationCheckpoint.check(); work++;
                        double d13 = d(p, t1, t3); if (d13 >= d12) break;
                        int i3 = pos[t3], t4 = tour[dir == 0 ? (i3 + 1) % n : (i3 + n - 1) % n];
                        if (t3 == t2 || t4 == t1) continue;
                        double gain = d12 + d(p, t3, t4) - d13 - d(p, t2, t4);
                        if (gain > 1e-7) {
                            if (dir == 0) reverse(tour, pos, (pos[t2]), pos[t3]); else reverse(tour, pos, pos[t3], pos[t2]);
                            work += Math.min(n, 64); // reversal cost is part of the workload
                            dlb[t1] = dlb[t2] = dlb[t3] = dlb[t4] = false; found = true; improved = true; break;
                        }
                    }
                }
                if (!found) dlb[c] = true;
                if (work >= maxWork) break outer;
            }
        }
        return sweeps;
    }
    /** Reverses the cyclic segment tour[i..j], choosing the shorter side. */
    private static void reverse(int[] tour, int[] pos, int i, int j) {
        int n = tour.length; int len = ((j - i + n) % n) + 1;
        if (len * 2 > n) { int ni = (j + 1) % n, nj = (i - 1 + n) % n; i = ni; j = nj; len = n - len; }
        for (int k = 0; k < len / 2; k++) {if(((k)&63)==0)CancellationCheckpoint.check(); int a = (i + k) % n, b = (j - k + n) % n; int ta = tour[a], tb = tour[b]; tour[a] = tb; tour[b] = ta; pos[tb] = a; pos[ta] = b; }
    }

    private static int idxAt(float[] cum, float target) { int lo = 0, hi = cum.length - 1; if (target <= 0) return 0; if (target >= cum[hi]) return hi; while (lo < hi) { int mid = (lo + hi) >>> 1; if (cum[mid] < target) lo = mid + 1; else hi = mid; } return lo; }

    /** Half width per point: tone-free, limited by the nearest non-neighbouring part of the line and by local curvature. */
    static float[] ribbonHalfWidths(float[] line, float maxHalf, float minHalf) {
        int m = line.length / 2; float[] cum = new float[m];
        for (int i = 1; i < m; i++) cum[i] = cum[i - 1] + (float) Math.hypot(line[2 * i] - line[2 * i - 2], line[2 * i + 1] - line[2 * i - 1]);
        float cell = Math.max(0.5f, maxHalf * 6); float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < m; i++) {if(((i)&63)==0)CancellationCheckpoint.check(); minX = Math.min(minX, line[2 * i]); maxX = Math.max(maxX, line[2 * i]); minY = Math.min(minY, line[2 * i + 1]); maxY = Math.max(maxY, line[2 * i + 1]); }
        int gw = (int) ((maxX - minX) / cell) + 1, gh = (int) ((maxY - minY) / cell) + 1; int[] head = new int[gw * gh]; Arrays.fill(head, -1); int[] nxt = new int[m];
        for (int i = 0; i < m; i++) {if(((i)&63)==0)CancellationCheckpoint.check(); int g = (int) ((line[2 * i + 1] - minY) / cell) * gw + (int) ((line[2 * i] - minX) / cell); nxt[i] = head[g]; head[g] = i; }
        float skip = maxHalf * 8; float[] hw = new float[m];
        for (int i = 0; i < m; i++) {if(((i)&63)==0)CancellationCheckpoint.check();
            float px = line[2 * i], py = line[2 * i + 1], best = maxHalf * 6; int gx = (int) ((px - minX) / cell), gy = (int) ((py - minY) / cell);
            for (int yy = Math.max(0, gy - 1); yy <= Math.min(gh - 1, gy + 1); yy++) for (int xx = Math.max(0, gx - 1); xx <= Math.min(gw - 1, gx + 1); xx++)
                for (int q = head[yy * gw + xx]; q >= 0; q = nxt[q]) {if(((q)&63)==0)CancellationCheckpoint.check(); if (Math.abs(cum[q] - cum[i]) < skip) continue; float d = (float) Math.hypot(line[2 * q] - px, line[2 * q + 1] - py); if (d < best) best = d; }
            float h = Math.min(maxHalf, 0.42f * best);
            for (float arc : new float[]{0.25f, 0.5f, 0.9f, 1.5f, 2.4f}) {CancellationCheckpoint.check(); // curvature at several scales catches hairpins that span many points
                int lo = idxAt(cum, cum[i] - arc), hi = idxAt(cum, cum[i] + arc); if (lo == i || hi == i) continue;
                double ax = line[2 * lo], ay = line[2 * lo + 1], bx = px, by = py, cx = line[2 * hi], cy = line[2 * hi + 1];
                double ab = Math.hypot(bx - ax, by - ay), bc = Math.hypot(cx - bx, cy - by), ca = Math.hypot(ax - cx, ay - cy), area2 = Math.abs((bx - ax) * (cy - ay) - (by - ay) * (cx - ax));
                if (area2 > 1e-9) h = (float) Math.min(h, 0.6 * (ab * bc * ca) / (2 * area2));
            }
            if (i > 0 && i < m - 1) { // an offset corner slides hw*tan(turn/2) along both neighbouring segments; it must stay inside them
                double d1x = px - line[2 * i - 2], d1y = py - line[2 * i - 1], d2x = line[2 * i + 2] - px, d2y = line[2 * i + 3] - py, l1 = Math.hypot(d1x, d1y), l2 = Math.hypot(d2x, d2y);
                if (l1 > 1e-6 && l2 > 1e-6) { double cs = (d1x * d2x + d1y * d2y) / (l1 * l2); if (cs < 0.9999) { double t = Math.sqrt((1 - cs) / (1 + cs + 1e-9)); h = (float) Math.min(h, 0.8 * Math.min(l1, l2) / (2 * t)); } }
            }
            hw[i] = Math.max(0.03f, h);
        }
        // light smoothing so width changes gradually
        float[] o = hw.clone(); for (int it = 0; it < 3; it++) {if(((it)&63)==0)CancellationCheckpoint.check(); float[] t = o.clone(); for (int i = 1; i < m - 1; i++) o[i] = Math.min(t[i], .25f * t[i - 1] + .5f * t[i] + .25f * t[i + 1]); }
        return o;
    }
}

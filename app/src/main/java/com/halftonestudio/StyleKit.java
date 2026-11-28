package com.halftonestudio;

import java.util.*;
import java.util.concurrent.CancellationException;

/**
 * Shared, Android-free toolkit for the vector "portrait style" engines (low-poly, stained glass,
 * TSP art, linocut, Truchet, text halftone, topographic, ridge lines).
 *
 * Conventions
 *  - All geometry is in millimetres, origin top-left, y down.
 *  - Closed polygons are flat float[]{x0,y0,x1,y1,...} with NO repeated first point.
 *  - Polygon loops produced here keep "interior on the left" in math axes (positive signed area),
 *    holes are negative. Fill with even-odd and orientation never matters.
 *  - Everything is deterministic for a given seed. Long loops call checkCancelled().
 */
public final class StyleKit {
    private StyleKit() {}

    public static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()||ParallelGeometry.cancelled()) throw new CancellationException("Style generation cancelled");
    }

    // ------------------------------------------------------------------ result model

    public static final class Layer {
        public final String name;
        public int color;
        /** Closed polygons (use even-odd fill: nested loops are holes). */
        public final ArrayList<float[]> loops = new ArrayList<>();
        /** Open polylines (single-line art). Stroked, not filled. */
        public final ArrayList<float[]> lines = new ArrayList<>();
        public float strokeMm = 0.3f;
        /** True for an independent final Boolean vector sheet; do not retrace it through a pixel grid. */
        public boolean vectorGeometryLocked;
        public Layer(String name, int color) { this.name = name; this.color = color; }
        public int pointCount() { int n = 0; for (float[] l : loops) n += l.length / 2; for (float[] l : lines) n += l.length / 2; return n; }
    }

    public static final class Result {
        public final float widthMm, heightMm;
        public final ArrayList<Layer> layers = new ArrayList<>();
        public int paperArgb = 0xffffffff;
        public String summary = "";
        public PerfectPortraitMaze.Result maze; // verified puzzle topology
        public Result(float widthMm, float heightMm) { this.widthMm = widthMm; this.heightMm = heightMm; }
        public int totalPoints() { int n = 0; for (Layer l : layers) n += l.pointCount(); return n; }
    }

    // ------------------------------------------------------------------ source image

    /** Prepared photo: luminance 0 (black)..1 (white), validity mask (snip/background), mm mapping. */
    public static final class Source {
        public final int w, h;
        public final float widthMm, heightMm, mmPerPx;
        public final int[] argb;
        public final boolean[] valid;
        public final float[] lum;
        /** Photos larger than ~1400 px on the long side are box-averaged down: the engines work in mm, so extra pixels only cost time. */
        public static final int MAX_SIDE = 1400;
        public Source(int[] argb0, int w0, int h0, boolean[] allowed, float widthMm) {
            if (argb0 == null || w0 < 2 || h0 < 2 || argb0.length != (long) w0 * h0) throw new IllegalArgumentException("Invalid image");
            if (allowed != null && allowed.length != argb0.length) throw new IllegalArgumentException("Mask size differs from image");
            if (!(widthMm > 0) || widthMm > 2000) throw new IllegalArgumentException("Invalid width");
            int f = Math.max(1, (int) Math.ceil(Math.max(w0, h0) / (double) MAX_SIDE));
            int w1 = (w0 + f - 1) / f, h1 = (h0 + f - 1) / f;
            this.w = w1; this.h = h1; this.widthMm = widthMm; this.mmPerPx = widthMm / w1; this.heightMm = h1 * mmPerPx;
            this.argb = f == 1 ? argb0 : new int[w1 * h1]; valid = new boolean[w1 * h1]; lum = new float[w1 * h1];
            // Most Android specialty renders are already downsampled. Avoid the
            // box-averaging loop entirely for the exact one-pixel case.
            if (f == 1) {
                for (int i = 0; i < argb0.length; i++) {
                    if ((i & 8191) == 0) checkCancelled();
                    int c = argb0[i];
                    valid[i] = (c >>> 24) >= 128 && (allowed == null || allowed[i]);
                    lum[i] = (0.2126f * ((c >> 16) & 255) + 0.7152f * ((c >> 8) & 255) + 0.0722f * (c & 255)) / 255f;
                }
                return;
            }
            for (int y = 0; y < h1; y++) {
                if ((y & 31) == 0) checkCancelled();
                for (int x = 0; x < w1; x++) {
                    int n = 0, ok = 0, r = 0, g = 0, b = 0;
                    for (int yy = y * f; yy < Math.min(h0, y * f + f); yy++) for (int xx = x * f; xx < Math.min(w0, x * f + f); xx++) {
                        int i = yy * w0 + xx, c = argb0[i]; n++;
                        if ((c >>> 24) >= 128 && (allowed == null || allowed[i])) { ok++; r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255; }
                    }
                    int di = y * w1 + x; valid[di] = ok * 2 >= n && ok > 0;
                    if (f > 1) this.argb[di] = ok == 0 ? 0 : 0xff000000 | ((r / ok) << 16) | ((g / ok) << 8) | (b / ok);
                    int c = this.argb[di]; lum[di] = (0.2126f * ((c >> 16) & 255) + 0.7152f * ((c >> 8) & 255) + 0.0722f * (c & 255)) / 255f;
                }
            }
        }
        /** Applies gamma (>1 darkens mids) and contrast (1 = unchanged) to luminance in place. */
        public Source tone(float gamma, float contrast) {
            for (int i = 0; i < lum.length; i++) { if ((i & 8191) == 0) checkCancelled(); lum[i] = toneCurve(lum[i], gamma, contrast); }
            return this;
        }
        /** Stretches the valid-pixel luminance so the lo..hi percentiles map to 0..1 (no-op on flat images). */
        public Source autoLevels(float loPct, float hiPct) {
            int cnt = 0; for (int i = 0; i < valid.length; i++) { if ((i & 8191) == 0) checkCancelled(); if (valid[i]) cnt++; }
            if (cnt < 16) return this;
            float[] t = new float[cnt]; int k = 0;
            for (int i = 0; i < lum.length; i++) { if ((i & 8191) == 0) checkCancelled(); if (valid[i]) t[k++] = lum[i]; }
            float lo = selectPercentile(t, Math.min(cnt - 1, (int) (cnt * loPct)));
            float hi = selectPercentile(t, Math.min(cnt - 1, (int) (cnt * hiPct)));
            if (hi - lo < 0.04f) return this;
            for (int i = 0; i < lum.length; i++) { if ((i & 8191) == 0) checkCancelled(); lum[i] = clampF((lum[i] - lo) / (hi - lo), 0, 1); }
            return this;
        }
        public float lumAt(float xMm, float yMm) {
            float fx = xMm / mmPerPx - .5f, fy = yMm / mmPerPx - .5f;
            int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
            float tx = fx - x0, ty = fy - y0;
            int xa = clampI(x0, 0, w - 1), xb = clampI(x0 + 1, 0, w - 1), ya = clampI(y0, 0, h - 1), yb = clampI(y0 + 1, 0, h - 1);
            float a = lum[ya * w + xa] * (1 - tx) + lum[ya * w + xb] * tx;
            float b = lum[yb * w + xa] * (1 - tx) + lum[yb * w + xb] * tx;
            return a * (1 - ty) + b * ty;
        }
        public float darkAt(float xMm, float yMm) { return 1f - lumAt(xMm, yMm); }
        public int pixelIndex(float xMm, float yMm) { return clampI((int) (yMm / mmPerPx), 0, h - 1) * w + clampI((int) (xMm / mmPerPx), 0, w - 1); }
        public boolean validAt(float xMm, float yMm) {
            if (xMm < 0 || yMm < 0 || xMm >= widthMm || yMm >= heightMm) return false;
            return valid[pixelIndex(xMm, yMm)];
        }
        public int colorAt(float xMm, float yMm) { return argb[pixelIndex(xMm, yMm)] | 0xff000000; }
        public boolean anyValid() { for (boolean b : valid) if (b) return true; return false; }
    }

    public static float toneCurve(float v, float gamma, float contrast) {
        v = Math.max(0f, Math.min(1f, v));
        v = (float) Math.pow(v, gamma <= 0 ? 1f : gamma);
        return Math.max(0f, Math.min(1f, (v - .5f) * contrast + .5f));
    }
    public static int clampI(int v, int lo, int hi) { return v < lo ? lo : Math.min(v, hi); }
    public static float clampF(float v, float lo, float hi) { return v < lo ? lo : Math.min(v, hi); }
    public static float smoothstep(float a, float b, float x) { float t = clampF((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); }

    // ------------------------------------------------------------------ image filters

    /** A render-local scope enables reuse across settled previews, without a global
     * cache or changing the semantics of standalone/export calls. */
    private static final ThreadLocal<RenderImageFieldCache> FIELD_CACHE = new ThreadLocal<>();
    public static <T> T withImageFieldCache(RenderImageFieldCache cache, java.util.function.Supplier<T> work) {
        RenderImageFieldCache previous=FIELD_CACHE.get();
        try { FIELD_CACHE.set(cache); return work.get(); }
        finally { if(previous==null) FIELD_CACHE.remove(); else FIELD_CACHE.set(previous); }
    }

    public static float[] blur(float[] a, int w, int h, float sigmaPx) {
        checkCancelled();
        if (sigmaPx < .3f) return a.clone();
        RenderImageFieldCache cache=FIELD_CACHE.get();
        if(cache==null)return blurUncached(a,w,h,sigmaPx);
        return cache.compute(a,w,h,0,Float.floatToIntBits(sigmaPx),()->blurUncached(a,w,h,sigmaPx));
    }
    static float[] blurUncached(float[] a, int w, int h, float sigmaPx) {
        if (sigmaPx < .3f) return a.clone();
        int r = Math.max(1, (int) Math.ceil(sigmaPx * 3));
        float[] k = new float[2 * r + 1]; float sum = 0;
        for (int i = -r; i <= r; i++) { k[i + r] = (float) Math.exp(-(i * i) / (2 * sigmaPx * sigmaPx)); sum += k[i + r]; }
        for (int i = 0; i < k.length; i++) k[i] /= sum;
        float[] t = new float[a.length], o = new float[a.length];
        // Same taps, same accumulation order. Eliminate clamps in the interior.
        for (int y = 0; y < h; y++) {
            if ((y & 15) == 0) checkCancelled();
            int row=y*w;
            for(int x=0;x<w;x++) {
                float v=0;
                if(x>=r && x<w-r) {
                    int at=row+x-r;
                    for(int j=0;j<k.length;j++)v+=a[at+j]*k[j];
                } else {
                    for(int i=-r;i<=r;i++)v+=a[row+clampI(x+i,0,w-1)]*k[i+r];
                }
                t[row+x]=v;
            }
        }
        // Row-major vertical pass with contiguous reads instead of striding
        // through the entire intermediate image for each output pixel.
        // Each output sees exactly the original sequence of additions.
        for(int y=0;y<h;y++) {
            if((y&15)==0)checkCancelled();
            int row=y*w;
            for(int i=-r;i<=r;i++) {
                if((i&63)==0)checkCancelled();
                int src=clampI(y+i,0,h-1)*w;
                float weight=k[i+r];
                for(int x=0;x<w;x++)o[row+x]+=t[src+x]*weight;
            }
        }
        return o;
    }

    /** Normalised gradient magnitude 0..1 of a (pre-blurred) field; 97th percentile maps to 1. */
    public static float[] edgeMap(float[] field, int w, int h) {
        checkCancelled();
        RenderImageFieldCache cache=FIELD_CACHE.get();
        if(cache==null)return edgeMapUncached(field,w,h);
        return cache.compute(field,w,h,1,0,()->edgeMapUncached(field,w,h));
    }
    static float[] edgeMapUncached(float[] field, int w, int h) {
        float[] g = new float[field.length];
        for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
            if(x==1 && (y&31)==0)checkCancelled();
            int i = y * w + x;
            float gx = (field[i - w + 1] + 2 * field[i + 1] + field[i + w + 1]) - (field[i - w - 1] + 2 * field[i - 1] + field[i + w - 1]);
            float gy = (field[i + w - 1] + 2 * field[i + w] + field[i + w + 1]) - (field[i - w - 1] + 2 * field[i - w] + field[i - w + 1]);
            g[i] = (float) Math.sqrt(gx * gx + gy * gy);
        }
        // Only one order statistic is needed. Sorting all 850k pixels costs
        // O(N log N) and temporarily stresses the Android heap. A bounded,
        // three-way quickselect keeps exactly the same percentile element.
        float[] values = g.clone();
        float p = Math.max(1e-6f, selectPercentile(values,(int)(values.length*.97f)));
        for (int i = 0; i < g.length; i++) g[i] = Math.min(1f, g[i] / p);
        return g;
    }

    /** In-place order statistic, bit-identical to Arrays.sort(values)[index]
     * for finite/nonnegative gradient magnitudes. Duplicates use a three-way
     * partition; adversarial pivots fall back to a bounded Arrays.sort range.
     */
    static float selectPercentile(float[] values,int index){
        if(values.length==0||index<0||index>=values.length)
            throw new IllegalArgumentException("Invalid percentile selection");
        int lo=0,hi=values.length-1;
        int depth=2*(32-Integer.numberOfLeadingZeros(values.length))+8;
        while(lo<hi){
            checkCancelled();
            if((--depth)<0){Arrays.sort(values,lo,hi+1);return values[index];}
            int mid=lo+((hi-lo)>>>1);
            float a=values[lo],b=values[mid],c=values[hi];
            float pivot=Float.compare(a,b)<0?
                (Float.compare(b,c)<0?b:(Float.compare(a,c)<0?c:a)):
                (Float.compare(a,c)<0?a:(Float.compare(b,c)<0?c:b));
            int lt=lo,i=lo,gt=hi;
            int visits=0;
            while(i<=gt){
                if((visits++&8191)==0)checkCancelled();
                int comp=Float.compare(values[i],pivot);
                if(comp<0){float v=values[lt];values[lt++]=values[i];values[i++]=v;}
                else if(comp>0){float v=values[gt];values[gt--]=values[i];values[i]=v;}
                else i++;
            }
            if(index<lt)hi=lt-1;
            else if(index>gt)lo=gt+1;
            else return values[index];
        }
        return values[lo];
    }

    // ------------------------------------------------------------------ variable-density point sampling

    /**
     * Dart-throwing Poisson-disc sampling with a spacing that shrinks near edges.
     * spacing(x,y) = maxR - (maxR-minR) * importance^edgeGamma. Returns flat xy in mm.
     * @param margin keep points at least this far from the rectangle sides (needed by Delaunay).
     */
    public static float[] sampleSites(Source s, float minR, float maxR, float edgeGamma, int maxPoints, float margin, long seed) {
        if (!(minR > 0) || maxR < minR) throw new IllegalArgumentException("Invalid spacing range");
        float[] imp = edgeMap(blur(s.lum, s.w, s.h, Math.max(1f, 0.8f / s.mmPerPx)), s.w, s.h);
        SplittableRandom rnd = new SplittableRandom(seed);
        float cell = maxR;
        int gw = (int) Math.ceil(s.widthMm / cell) + 1, gh = (int) Math.ceil(s.heightMm / cell) + 1;
        ArrayList<ArrayList<Integer>> grid = new ArrayList<>(Collections.nCopies(gw * gh, null));
        float[] pts = new float[Math.min(maxPoints, 200000) * 2], rad = new float[pts.length / 2];
        int n = 0, fails = 0, failLimit = 4000 + (int) (s.widthMm * s.heightMm / (minR * minR) * 6);
        while (n < maxPoints && fails < failLimit) {
            if ((fails & 1023) == 0) checkCancelled();
            float x = margin + (float)rnd.nextDouble() * Math.max(0, s.widthMm - 2 * margin);
            float y = margin + (float)rnd.nextDouble() * Math.max(0, s.heightMm - 2 * margin);
            if (!s.validAt(x, y)) { fails++; continue; }
            float e = imp[s.pixelIndex(x, y)];
            float r = maxR - (maxR - minR) * (float) Math.pow(e, edgeGamma);
            int cx = (int) (x / cell), cy = (int) (y / cell);
            boolean ok = true;
            for (int gy = Math.max(0, cy - 1); gy <= Math.min(gh - 1, cy + 1) && ok; gy++)
                for (int gx = Math.max(0, cx - 1); gx <= Math.min(gw - 1, cx + 1) && ok; gx++) {
                    ArrayList<Integer> list = grid.get(gy * gw + gx); if (list == null) continue;
                    for (int j : list) {
                        float dx = pts[2 * j] - x, dy = pts[2 * j + 1] - y, rr = .5f * (r + rad[j]);
                        if (dx * dx + dy * dy < rr * rr) { ok = false; break; }
                    }
                }
            if (!ok) { fails++; continue; }
            fails = 0; pts[2 * n] = x; pts[2 * n + 1] = y; rad[n] = r;
            int gi = cy * gw + cx; if (grid.get(gi) == null) grid.set(gi, new ArrayList<>()); grid.get(gi).add(n); n++;
        }
        return Arrays.copyOf(pts, 2 * n);
    }

    // ------------------------------------------------------------------ colour

    public static float[] toLab(int argb) {
        double r = lin(((argb >> 16) & 255) / 255.0), g = lin(((argb >> 8) & 255) / 255.0), b = lin((argb & 255) / 255.0);
        double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047, y = 0.2126 * r + 0.7152 * g + 0.0722 * b, z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new float[]{(float) (116 * fy - 16), (float) (500 * (fx - fy)), (float) (200 * (fy - fz))};
    }
    private static double lin(double c) { return c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4); }
    private static double f(double t) { return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116; }
    public static float labDist2(float[] a, float[] b) { float l = a[0] - b[0], p = a[1] - b[1], q = a[2] - b[2]; return l * l + p * p + q * q; }
    public static int luma255(int c) { return (2126 * ((c >> 16) & 255) + 7152 * ((c >> 8) & 255) + 722 * (c & 255)) / 10000; }

    public static int nearestIndex(int argb, float[][] paletteLab) {
        float[] lab = toLab(argb); int best = 0; float bd = Float.MAX_VALUE;
        for (int i = 0; i < paletteLab.length; i++) { float d = labDist2(lab, paletteLab[i]); if (d < bd) { bd = d; best = i; } }
        return best;
    }
    public static float[][] labOf(int[] palette) { float[][] l = new float[palette.length][]; for (int i = 0; i < palette.length; i++) l[i] = toLab(palette[i]); return l; }

    /** Deterministic k-means++ in Lab on weighted colours. Returns k colours sorted dark to light. */
    public static int[] kmeans(int[] colors, float[] weights, int k, long seed) {
        int n = colors.length; if (n == 0) throw new IllegalArgumentException("No colours");
        k = Math.max(1, Math.min(k, n));
        float[][] lab = new float[n][]; for (int i = 0; i < n; i++) lab[i] = toLab(colors[i]);
        SplittableRandom rnd = new SplittableRandom(seed);
        float[][] c = new float[k][]; c[0] = lab[rnd.nextInt(n)].clone();
        float[] d2 = new float[n]; Arrays.fill(d2, Float.MAX_VALUE);
        for (int j = 1; j < k; j++) {
            double total = 0;
            for (int i = 0; i < n; i++) { d2[i] = Math.min(d2[i], labDist2(lab[i], c[j - 1])); total += d2[i] * weights[i]; }
            double pick = rnd.nextDouble() * total, acc = 0; int chosen = n - 1;
            for (int i = 0; i < n; i++) { acc += d2[i] * weights[i]; if (acc >= pick) { chosen = i; break; } }
            c[j] = lab[chosen].clone();
        }
        int[] own = new int[n];
        for (int it = 0; it < 14; it++) {
            double[][] sum = new double[k][3]; double[] wt = new double[k];
            for (int i = 0; i < n; i++) {
                int b = 0; float bd = Float.MAX_VALUE;
                for (int j = 0; j < k; j++) { float d = labDist2(lab[i], c[j]); if (d < bd) { bd = d; b = j; } }
                own[i] = b; wt[b] += weights[i]; for (int q = 0; q < 3; q++) sum[b][q] += lab[i][q] * weights[i];
            }
            for (int j = 0; j < k; j++) if (wt[j] > 0) for (int q = 0; q < 3; q++) c[j][q] = (float) (sum[j][q] / wt[j]);
        }
        double[][] rgb = new double[k][3]; double[] wt = new double[k];
        for (int i = 0; i < n; i++) { int b = own[i]; wt[b] += weights[i]; rgb[b][0] += ((colors[i] >> 16) & 255) * weights[i]; rgb[b][1] += ((colors[i] >> 8) & 255) * weights[i]; rgb[b][2] += (colors[i] & 255) * weights[i]; }
        ArrayList<Integer> out = new ArrayList<>();
        for (int j = 0; j < k; j++) if (wt[j] > 0) out.add(0xff000000 | ((int) Math.round(rgb[j][0] / wt[j]) << 16) | ((int) Math.round(rgb[j][1] / wt[j]) << 8) | (int) Math.round(rgb[j][2] / wt[j]));
        out.sort(Comparator.comparingInt(StyleKit::luma255));
        int[] r = new int[out.size()]; for (int i = 0; i < r.length; i++) r[i] = out.get(i); return r;
    }

    /** Linear ramp from dark to light colour with n stops. */
    public static int[] ramp(int dark, int light, int n) {
        int[] r = new int[n];
        for (int i = 0; i < n; i++) {
            float t = n == 1 ? 0 : i / (float) (n - 1);
            int rr = Math.round(((dark >> 16) & 255) * (1 - t) + ((light >> 16) & 255) * t), gg = Math.round(((dark >> 8) & 255) * (1 - t) + ((light >> 8) & 255) * t), bb = Math.round((dark & 255) * (1 - t) + (light & 255) * t);
            r[i] = 0xff000000 | (rr << 16) | (gg << 8) | bb;
        }
        return r;
    }

    public static int boostSaturation(int argb, float amount) {
        float r = ((argb >> 16) & 255) / 255f, g = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f, l = .299f * r + .587f * g + .114f * b;
        r = clampF(l + (r - l) * (1 + amount), 0, 1); g = clampF(l + (g - l) * (1 + amount), 0, 1); b = clampF(l + (b - l) * (1 + amount), 0, 1);
        return 0xff000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    // ------------------------------------------------------------------ polygon utilities

    public static double signedArea(float[] p) {
        double a = 0; int n = p.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) a += (double) p[2 * j] * p[2 * i + 1] - (double) p[2 * i] * p[2 * j + 1];
        return a / 2;
    }
    public static float[] reversed(float[] p) {
        int n = p.length / 2; float[] r = new float[p.length];
        for (int i = 0; i < n; i++) { r[2 * i] = p[2 * (n - 1 - i)]; r[2 * i + 1] = p[2 * (n - 1 - i) + 1]; } return r;
    }
    public static float[] rect(float x0, float y0, float x1, float y1) { return new float[]{x0, y0, x1, y0, x1, y1, x0, y1}; }
    public static float[] circle(float cx, float cy, float r, int seg) {
        float[] p = new float[2 * seg]; for (int i = 0; i < seg; i++) { double a = 2 * Math.PI * i / seg; p[2 * i] = (float) (cx + r * Math.cos(a)); p[2 * i + 1] = (float) (cy + r * Math.sin(a)); } return p;
    }
    public static float[] centroid(float[] p) {
        double a = 0, cx = 0, cy = 0; int n = p.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) { double cr = (double) p[2 * j] * p[2 * i + 1] - (double) p[2 * i] * p[2 * j + 1]; a += cr; cx += (p[2 * j] + p[2 * i]) * cr; cy += (p[2 * j + 1] + p[2 * i + 1]) * cr; }
        if (Math.abs(a) < 1e-9) { double sx = 0, sy = 0; for (int i = 0; i < n; i++) { sx += p[2 * i]; sy += p[2 * i + 1]; } return new float[]{(float) (sx / n), (float) (sy / n)}; }
        return new float[]{(float) (cx / (3 * a)), (float) (cy / (3 * a))};
    }

    /** Keeps the part of poly with nx*x+ny*y >= c (Sutherland-Hodgman). */
    public static float[] clipHalfPlane(float[] poly, double nx, double ny, double c) {
        int n = poly.length / 2; if (n == 0) return poly;
        float[] out = new float[poly.length + 8]; int m = 0;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = poly[2 * i], yi = poly[2 * i + 1], xj = poly[2 * j], yj = poly[2 * j + 1];
            double di = nx * xi + ny * yi - c, dj = nx * xj + ny * yj - c;
            if ((di >= 0) != (dj >= 0)) { double t = dj / (dj - di); if (m + 2 > out.length) out = Arrays.copyOf(out, out.length * 2); out[m++] = (float) (xj + (xi - xj) * t); out[m++] = (float) (yj + (yi - yj) * t); }
            if (di >= 0) { if (m + 2 > out.length) out = Arrays.copyOf(out, out.length * 2); out[m++] = (float) xi; out[m++] = (float) yi; }
        }
        return Arrays.copyOf(out, m);
    }

    /** Cut-safe artboard intersection for polygons from padded fields and
     * offset ribbons. Never silently discard a damaged or disconnected shape:
     * an unsupported repair leaves the original out-of-board geometry intact,
     * which the final export validator will reject before any file is saved.
     * The same vector result is used by preview and per-layer export.
     */
    public static float[] clipToArtboardOrOriginal(float[] poly,float widthMm,float heightMm){
        if(poly==null||poly.length<6||!(widthMm>0&&heightMm>0))return poly;
        boolean outside=false;
        for(int i=0;i<poly.length;i+=2){
            if(!Float.isFinite(poly[i])||!Float.isFinite(poly[i+1]))return poly;
            if(poly[i]<0||poly[i]>widthMm||poly[i+1]<0||poly[i+1]>heightMm)
                outside=true;
        }
        if(!outside)return poly;
        double initial=signedArea(poly);
        if(Math.abs(initial)<1e-5)return poly;
        float[] candidate=clipHalfPlane(poly,1,0,0);
        candidate=clipHalfPlane(candidate,-1,0,-widthMm);
        candidate=clipHalfPlane(candidate,0,1,0);
        candidate=clipHalfPlane(candidate,0,-1,-heightMm);
        if(candidate.length<6)return poly;
        candidate=removeCollinear(candidate,.00005f);
        double clipped=signedArea(candidate);
        if(candidate.length<6||Math.signum(initial)!=Math.signum(clipped)||
            Math.abs(clipped)<Math.abs(initial)*.90||selfIntersections(candidate,1)>0)
            return poly;
        for(int i=0;i<candidate.length;i+=2)
            if(!Float.isFinite(candidate[i])||!Float.isFinite(candidate[i+1])||
                candidate[i]<-.0001f||candidate[i]>widthMm+.0001f||
                candidate[i+1]<-.0001f||candidate[i+1]>heightMm+.0001f)return poly;
        return candidate;
    }

    /** Preserve an entire continuous ribbon's contour and physical width by
     * shifting it at most one millimeter into the artboard. Only available if
     * its entire bounds fit; the final sheet inspector still verifies collisions.
     * Large overshoots are left unmodified and export fails closed.
     */
    public static float[] translateWithinArtboardOrOriginal(float[] poly,float widthMm,float heightMm){
        if(poly==null||poly.length<6||!(widthMm>0&&heightMm>0))return poly;
        float x0=Float.POSITIVE_INFINITY,y0=Float.POSITIVE_INFINITY;
        float x1=Float.NEGATIVE_INFINITY,y1=Float.NEGATIVE_INFINITY;
        for(int i=0;i<poly.length;i+=2){
            if(!Float.isFinite(poly[i])||!Float.isFinite(poly[i+1]))return poly;
            x0=Math.min(x0,poly[i]);x1=Math.max(x1,poly[i]);
            y0=Math.min(y0,poly[i+1]);y1=Math.max(y1,poly[i+1]);
        }
        if(x0>=0&&y0>=0&&x1<=widthMm&&y1<=heightMm)return poly;
        if(x1-x0>widthMm||y1-y0>heightMm)return poly;
        float dx=x0<0?-x0:(x1>widthMm?widthMm-x1:0);
        float dy=y0<0?-y0:(y1>heightMm?heightMm-y1:0);
        if(Math.abs(dx)>1f||Math.abs(dy)>1f)return poly;
        float[] shifted=poly.clone();
        for(int i=0;i<shifted.length;i+=2){
            shifted[i]+=dx;shifted[i+1]+=dy;
            // Compensate only float rounding at a physical artboard boundary.
            shifted[i]=Math.max(0,Math.min(widthMm,shifted[i]));
            shifted[i+1]=Math.max(0,Math.min(heightMm,shifted[i+1]));
        }
        return shifted;
    }

    /** Exact inward offset of a CONVEX polygon with positive area; returns null if it vanishes. */
    public static float[] insetConvex(float[] poly, float d) {
        if (signedArea(poly) < 0) poly = reversed(poly);
        float[] cur = poly; int n = poly.length / 2;
        for (int i = 0; i < n && cur.length >= 6; i++) {
            int j = (i + 1) % n; double dx = poly[2 * j] - poly[2 * i], dy = poly[2 * j + 1] - poly[2 * i + 1], len = Math.hypot(dx, dy);
            if (len < 1e-9) continue;
            double nx = -dy / len, ny = dx / len;
            cur = clipHalfPlane(cur, nx, ny, nx * poly[2 * i] + ny * poly[2 * i + 1] + d);
        }
        return cur.length >= 6 && Math.abs(signedArea(cur)) > 1e-4 ? cur : null;
    }

    /** Moves every edge d to its left (positive d shrinks a CCW loop and grows holes); mitered. Null if degenerate. */
    public static float[] offsetLeft(float[] p, float d, float miterLimit) {
        int n = p.length / 2; if (n < 3) return null;
        float[] o = new float[p.length];
        for (int i = 0; i < n; i++) {
            int a = (i + n - 1) % n, b = (i + 1) % n;
            double e1x = p[2 * i] - p[2 * a], e1y = p[2 * i + 1] - p[2 * a + 1], e2x = p[2 * b] - p[2 * i], e2y = p[2 * b + 1] - p[2 * i + 1];
            double l1 = Math.hypot(e1x, e1y), l2 = Math.hypot(e2x, e2y); if (l1 < 1e-9 || l2 < 1e-9) return null;
            double n1x = -e1y / l1, n1y = e1x / l1, n2x = -e2y / l2, n2y = e2x / l2;
            double mx = n1x + n2x, my = n1y + n2y, ml = Math.hypot(mx, my);
            if (ml < 1e-6) { mx = n1x; my = n1y; ml = 1; }
            mx /= ml; my /= ml;
            double cos = Math.max(mx * n1x + my * n1y, 1.0 / miterLimit);
            o[2 * i] = (float) (p[2 * i] + mx * d / cos); o[2 * i + 1] = (float) (p[2 * i + 1] + my * d / cos);
        }
        double a0 = signedArea(p), a1 = signedArea(o);
        if (a0 * a1 <= 0 || Math.abs(a1) < 1e-4) return null;
        return o;
    }

    public static float[] removeCollinear(float[] p, float eps) {
        int n = p.length / 2; if (n < 4) return p;
        float[] o = new float[p.length]; int m = 0;
        for (int i = 0; i < n; i++) {
            int a = (i + n - 1) % n, b = (i + 1) % n;
            double cr = (p[2 * i] - p[2 * a]) * (double) (p[2 * b + 1] - p[2 * a + 1]) - (p[2 * i + 1] - p[2 * a + 1]) * (double) (p[2 * b] - p[2 * a]);
            double len = Math.hypot(p[2 * b] - p[2 * a], p[2 * b + 1] - p[2 * a + 1]);
            if (len > 1e-9 && Math.abs(cr) / len <= eps) continue;
            o[m++] = p[2 * i]; o[m++] = p[2 * i + 1];
        }
        return m >= 6 ? Arrays.copyOf(o, m) : p;
    }

    /** Chaikin corner cutting on interleaved points with `stride` channels (2 = xy, 3 = xy+width). */
    public static float[] chaikin(float[] a, int stride, boolean closed, int iterations) {
        for (int it = 0; it < iterations; it++) {
            int n = a.length / stride; if (n < 3) return a;
            int segs = closed ? n : n - 1;
            float[] o = new float[(closed ? 2 * n : 2 * segs + 2) * stride]; int m = 0;
            if (!closed) for (int q = 0; q < stride; q++) o[m++] = a[q];
            for (int i = 0; i < segs; i++) {
                int j = (i + 1) % n;
                for (int q = 0; q < stride; q++) o[m++] = .75f * a[i * stride + q] + .25f * a[j * stride + q];
                for (int q = 0; q < stride; q++) o[m++] = .25f * a[i * stride + q] + .75f * a[j * stride + q];
            }
            if (!closed) for (int q = 0; q < stride; q++) o[m++] = a[(n - 1) * stride + q];
            a = Arrays.copyOf(o, m);
        }
        return a;
    }

    /** Ramer-Douglas-Peucker on an open xy polyline. */
    public static float[] rdp(float[] p, float eps) {
        int n = p.length / 2; if (n < 3) return p;
        boolean[] keep = new boolean[n]; keep[0] = keep[n - 1] = true;
        ArrayDeque<int[]> st = new ArrayDeque<>(); st.push(new int[]{0, n - 1});
        while (!st.isEmpty()) {
            int[] r = st.pop(); int a = r[0], b = r[1]; double bd = -1; int bi = -1;
            double dx = p[2 * b] - p[2 * a], dy = p[2 * b + 1] - p[2 * a + 1], len = Math.hypot(dx, dy);
            for (int i = a + 1; i < b; i++) {
                double d = len < 1e-9 ? Math.hypot(p[2 * i] - p[2 * a], p[2 * i + 1] - p[2 * a + 1]) : Math.abs(dx * (p[2 * a + 1] - p[2 * i + 1]) - (p[2 * a] - p[2 * i]) * dy) / len;
                if (d > bd) { bd = d; bi = i; }
            }
            if (bd > eps) { keep[bi] = true; st.push(new int[]{a, bi}); st.push(new int[]{bi, b}); }
        }
        float[] o = new float[2 * n]; int m = 0;
        for (int i = 0; i < n; i++) if (keep[i]) { o[m++] = p[2 * i]; o[m++] = p[2 * i + 1]; }
        return Arrays.copyOf(o, m);
    }

    /**
     * Variable-width ribbon around an open polyline. halfW has one entry per point.
     * Returns one closed polygon (left side forward, right side back); caps are flat unless round.
     */
    public static float[] ribbon(float[] xy, float[] halfW, float miter, boolean roundCaps) {
        int n = xy.length / 2; if (n < 2) return null;
        float[] L = new float[2 * n], R = new float[2 * n];
        for (int i = 0; i < n; i++) {
            double tx = 0, ty = 0, nx1 = 0, ny1 = 0; int cnt = 0;
            if (i > 0) { double dx = xy[2 * i] - xy[2 * i - 2], dy = xy[2 * i + 1] - xy[2 * i - 1], l = Math.hypot(dx, dy); if (l > 1e-9) { tx += dx / l; ty += dy / l; nx1 = -dy / l; ny1 = dx / l; cnt++; } }
            double nx2 = nx1, ny2 = ny1;
            if (i < n - 1) { double dx = xy[2 * i + 2] - xy[2 * i], dy = xy[2 * i + 3] - xy[2 * i + 1], l = Math.hypot(dx, dy); if (l > 1e-9) { tx += dx / l; ty += dy / l; nx2 = -dy / l; ny2 = dx / l; if (cnt == 0) { nx1 = nx2; ny1 = ny2; } cnt++; } }
            double tl = Math.hypot(tx, ty), nx, ny;
            if (tl < 1e-6) { nx = nx2; ny = ny2; } else { nx = -ty / tl; ny = tx / tl; }
            double cos = Math.max(nx * nx1 + ny * ny1, Math.max(nx * nx2 + ny * ny2, 1e-3));
            cos = Math.max(cos, 1.0 / miter);
            double hw = halfW[i] / cos;
            L[2 * i] = (float) (xy[2 * i] + nx * hw); L[2 * i + 1] = (float) (xy[2 * i + 1] + ny * hw);
            R[2 * i] = (float) (xy[2 * i] - nx * hw); R[2 * i + 1] = (float) (xy[2 * i + 1] - ny * hw);
        }
        int cap = roundCaps ? 5 : 0; float[] o = new float[2 * (2 * n + 2 * cap)]; int m = 0;
        for (int i = 0; i < n; i++) { o[m++] = L[2 * i]; o[m++] = L[2 * i + 1]; }
        if (cap > 0) { // round end cap around last point
            double ex = xy[2 * n - 2], ey = xy[2 * n - 1], ax = L[2 * n - 2] - ex, ay = L[2 * n - 1] - ey;
            for (int c = 1; c <= cap; c++) { double a = -Math.PI * c / (cap + 1), ca = Math.cos(a), sa = Math.sin(a); o[m++] = (float) (ex + ax * ca - ay * sa); o[m++] = (float) (ey + ax * sa + ay * ca); }
        }
        for (int i = n - 1; i >= 0; i--) { o[m++] = R[2 * i]; o[m++] = R[2 * i + 1]; }
        if (cap > 0) {
            double ex = xy[0], ey = xy[1], ax = R[0] - ex, ay = R[1] - ey;
            for (int c = 1; c <= cap; c++) { double a = -Math.PI * c / (cap + 1), ca = Math.cos(a), sa = Math.sin(a); o[m++] = (float) (ex + ax * ca - ay * sa); o[m++] = (float) (ey + ax * sa + ay * ca); }
        }
        return Arrays.copyOf(o, m);
    }
    /** Ribbon around a CLOSED polyline: returns {outer loop, inner loop} (fill even-odd). */
    public static ArrayList<float[]> ribbonClosed(float[] xy, float[] halfW, float miter) {
        int n = xy.length / 2; if (n < 3) return null;
        float[] L = new float[2 * n], R = new float[2 * n];
        for (int i = 0; i < n; i++) {
            int a = (i + n - 1) % n, b = (i + 1) % n;
            double d1x = xy[2 * i] - xy[2 * a], d1y = xy[2 * i + 1] - xy[2 * a + 1], l1 = Math.hypot(d1x, d1y), d2x = xy[2 * b] - xy[2 * i], d2y = xy[2 * b + 1] - xy[2 * i + 1], l2 = Math.hypot(d2x, d2y);
            if (l1 < 1e-9 || l2 < 1e-9) return null;
            double n1x = -d1y / l1, n1y = d1x / l1, n2x = -d2y / l2, n2y = d2x / l2, mx = n1x + n2x, my = n1y + n2y, ml = Math.hypot(mx, my);
            if (ml < 1e-6) { mx = n1x; my = n1y; ml = 1; } mx /= ml; my /= ml;
            double cos = Math.max(mx * n1x + my * n1y, 1.0 / miter), hw = halfW[i] / cos;
            L[2 * i] = (float) (xy[2 * i] + mx * hw); L[2 * i + 1] = (float) (xy[2 * i + 1] + my * hw);
            R[2 * i] = (float) (xy[2 * i] - mx * hw); R[2 * i + 1] = (float) (xy[2 * i + 1] - my * hw);
        }
        ArrayList<float[]> out = new ArrayList<>(); out.add(L); out.add(R); return out;
    }
    public static float[] ribbon(float[] xy, float halfW, float miter) {
        float[] w = new float[xy.length / 2]; Arrays.fill(w, halfW); return ribbon(xy, w, miter, false);
    }

    /** Result of a self-intersection check. {@code indeterminate} means the work budget ran out:
     *  the contour was NOT proven simple and must never be treated as valid (D11). */
    public static final class IntersectionCheck {
        public final ArrayList<int[]> pairs; public final boolean indeterminate;
        IntersectionCheck(ArrayList<int[]> pairs, boolean indeterminate) { this.pairs = pairs; this.indeterminate = indeterminate; }
        public boolean valid() { return !indeterminate && pairs.isEmpty(); }
    }
    /** Grid bounds: cells (flat int arrays, ~4 MB), edge-in-cell insertions (memory, ~32 MB) and
     *  pair tests (time). The grid adapts its cell size instead of giving up. */
    static final long MAX_GRID_CELLS = 1_000_000L, MAX_INSERTIONS = 8_000_000L, DEFAULT_WORK_BUDGET = 40_000_000L;

    /** Number of proper self-intersections of a closed polygon, up to cap. 0 = proven simple.
     *  An indeterminate check (budget exhausted, non-finite input) counts as {@code max(1,cap)}
     *  crossings, so every caller treats it as not simple (fail closed). */
    public static int selfIntersections(float[] p, int cap) { return selfIntersectionsWithBudget(p, cap, DEFAULT_WORK_BUDGET); }
    static int selfIntersectionsWithBudget(float[] p, int cap, long budget) {
        IntersectionCheck c = checkSelfIntersections(p, cap, budget);
        return c.indeterminate ? Math.max(1, Math.max(cap, c.pairs.size())) : c.pairs.size();
    }
    public static IntersectionCheck checkSelfIntersections(float[] p, int cap) { return checkSelfIntersections(p, cap, DEFAULT_WORK_BUDGET); }

    /** Exact crossing search over a uniform grid (edge i runs from vertex i to i+1), stored as flat
     *  primitive arrays (compressed rows per cell). Each candidate pair is tested only in the cell
     *  where the two edges' bounding boxes first overlap, so no pair is tested twice and no hash
     *  set is needed. Exceeding the insertion or pair budget yields an indeterminate result. */
    static IntersectionCheck checkSelfIntersections(float[] p, int cap, long budget) {
        ArrayList<int[]> found = new ArrayList<>();
        int n = p.length / 2; if (n < 4) return new IntersectionCheck(found, false);
        for (int i = 0; i < 2 * n; i++) if (!Float.isFinite(p[i])) return new IntersectionCheck(found, true);
        float minx = Float.MAX_VALUE, miny = Float.MAX_VALUE, maxx = -Float.MAX_VALUE, maxy = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) { minx = Math.min(minx, p[2 * i]); maxx = Math.max(maxx, p[2 * i]); miny = Math.min(miny, p[2 * i + 1]); maxy = Math.max(maxy, p[2 * i + 1]); }
        double avg = 0; for (int i = 0; i < n; i++) { int j = (i + 1) % n; avg += Math.hypot(p[2 * j] - p[2 * i], p[2 * j + 1] - p[2 * i + 1]); }
        double spanX = Math.max(maxx - minx, 1e-6), spanY = Math.max(maxy - miny, 1e-6);
        double cell = Math.max(Math.max(avg / n * 2, 1e-3), Math.sqrt(spanX * spanY / (double) MAX_GRID_CELLS));
        while ((long) (spanX / cell + 1) * (long) (spanY / cell + 1) > MAX_GRID_CELLS) cell *= 1.25;
        final int gw = (int) (spanX / cell) + 1, gh = (int) (spanY / cell) + 1;
        int[] x0 = new int[n], y0 = new int[n], x1 = new int[n], y1 = new int[n];
        int[] start = new int[gw * gh + 1];
        long insertions = 0;
        for (int i = 0; i < n; i++) {
            if ((i & 4095) == 0) checkCancelled();
            int j = (i + 1) % n;
            x0[i] = Math.min(gw - 1, (int) ((Math.min(p[2 * i], p[2 * j]) - minx) / cell));
            x1[i] = Math.min(gw - 1, (int) ((Math.max(p[2 * i], p[2 * j]) - minx) / cell));
            y0[i] = Math.min(gh - 1, (int) ((Math.min(p[2 * i + 1], p[2 * j + 1]) - miny) / cell));
            y1[i] = Math.min(gh - 1, (int) ((Math.max(p[2 * i + 1], p[2 * j + 1]) - miny) / cell));
            insertions += (long) (x1[i] - x0[i] + 1) * (y1[i] - y0[i] + 1);
            if (insertions > MAX_INSERTIONS || insertions > budget) return new IntersectionCheck(found, true);
            for (int y = y0[i]; y <= y1[i]; y++) for (int x = x0[i]; x <= x1[i]; x++) start[y * gw + x + 1]++;
        }
        for (int c = 0; c < gw * gh; c++) start[c + 1] += start[c];
        int[] fill = new int[gw * gh];
        int[] items = new int[(int) insertions];
        for (int i = 0; i < n; i++)
            for (int y = y0[i]; y <= y1[i]; y++) for (int x = x0[i]; x <= x1[i]; x++) { int c = y * gw + x; items[start[c] + fill[c]++] = i; }
        fill = null;
        long work = insertions;
        for (int c = 0; c < gw * gh; c++) {
            int from = start[c], to = start[c + 1], size = to - from;
            if (size < 2) continue;
            work += (long) size * (size - 1) / 2;
            if (work > budget) return new IntersectionCheck(found, true);
            if ((c & 1023) == 0) checkCancelled();
            int cx = c % gw, cy = c / gw;
            for (int a = from; a < to; a++) for (int b = a + 1; b < to; b++) {
                int i = items[a], j = items[b]; if (i > j) { int t = i; i = j; j = t; }
                if (j - i <= 1 || (i == 0 && j == n - 1)) continue;
                // Test once: only in the first cell shared by both bounding boxes.
                if (Math.max(x0[i], x0[j]) != cx || Math.max(y0[i], y0[j]) != cy) continue;
                if (segCross(p, i, (i + 1) % n, j, (j + 1) % n)) { found.add(new int[]{i, j}); if (found.size() >= cap) return new IntersectionCheck(found, false); }
            }
        }
        return new IntersectionCheck(found, false);
    }
    private static boolean segCross(float[] p, int a, int b, int c, int d) {
        double ax = p[2 * a], ay = p[2 * a + 1], bx = p[2 * b], by = p[2 * b + 1], cx = p[2 * c], cy = p[2 * c + 1], dx = p[2 * d], dy = p[2 * d + 1];
        double o1 = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax), o2 = (bx - ax) * (dy - ay) - (by - ay) * (dx - ax);
        double o3 = (dx - cx) * (ay - cy) - (dy - cy) * (ax - cx), o4 = (dx - cx) * (by - cy) - (dy - cy) * (bx - cx);
        return o1 * o2 < 0 && o3 * o4 < 0;
    }

    // ------------------------------------------------------------------ Delaunay + Voronoi

    /** Bowyer-Watson Delaunay triangulation. Returns CCW index triples into pts (n points). */
    public static int[] delaunay(float[] pts, int n) {
        if (n < 3) return new int[0];
        double minx = 1e30, miny = 1e30, maxx = -1e30, maxy = -1e30;
        for (int i = 0; i < n; i++) { minx = Math.min(minx, pts[2 * i]); maxx = Math.max(maxx, pts[2 * i]); miny = Math.min(miny, pts[2 * i + 1]); maxy = Math.max(maxy, pts[2 * i + 1]); }
        double d = Math.max(maxx - minx, maxy - miny), mx = (minx + maxx) / 2, my = (miny + maxy) / 2;
        double[] X = new double[n + 3], Y = new double[n + 3];
        for (int i = 0; i < n; i++) { // deterministic sub-micron jitter prevents exactly collinear/cocircular input
            long hsh = i * 0x9E3779B97F4A7C15L; hsh ^= hsh >>> 29;
            X[i] = pts[2 * i] + (((hsh & 1023) / 1023.0) - .5) * 1e-6 * d; Y[i] = pts[2 * i + 1] + ((((hsh >>> 10) & 1023) / 1023.0) - .5) * 1e-6 * d;
        }
        X[n] = mx - 40 * d; Y[n] = my - 20 * d; X[n + 1] = mx; Y[n + 1] = my + 40 * d; X[n + 2] = mx + 40 * d; Y[n + 2] = my - 20 * d;
        int cap = 8 * n + 64; int[] ta = new int[cap], tb = new int[cap], tc = new int[cap]; double[] ccx = new double[cap], ccy = new double[cap], cr2 = new double[cap]; boolean[] alive = new boolean[cap];
        int[] free = new int[cap]; int nFree = 0, used = 0;
        used = addTri(ta, tb, tc, ccx, ccy, cr2, alive, X, Y, used, n, n + 1, n + 2);
        for (int p = 0; p < n; p++) {
            if ((p & 255) == 0) checkCancelled();
            double px = X[p], py = Y[p];
            HashMap<Long, Integer> edges = new HashMap<>(); ArrayList<Integer> bad = new ArrayList<>();
            for (int t = 0; t < used; t++) if (alive[t]) { double dx = px - ccx[t], dy = py - ccy[t]; if (dx * dx + dy * dy < cr2[t]) bad.add(t); }
            for (int t : bad) { alive[t] = false; free[nFree++] = t; countEdge(edges, ta[t], tb[t]); countEdge(edges, tb[t], tc[t]); countEdge(edges, tc[t], ta[t]); }
            for (Map.Entry<Long, Integer> e : edges.entrySet()) if (e.getValue() == 1) {
                int a = (int) (e.getKey() >>> 32), b = (int) (e.getKey() & 0xffffffffL);
                int slot = nFree > 0 ? free[--nFree] : used++;
                if (slot >= ta.length) { int nc = ta.length * 2; ta = Arrays.copyOf(ta, nc); tb = Arrays.copyOf(tb, nc); tc = Arrays.copyOf(tc, nc); ccx = Arrays.copyOf(ccx, nc); ccy = Arrays.copyOf(ccy, nc); cr2 = Arrays.copyOf(cr2, nc); alive = Arrays.copyOf(alive, nc); free = Arrays.copyOf(free, nc); }
                setTri(ta, tb, tc, ccx, ccy, cr2, alive, X, Y, slot, a, b, p);
            }
        }
        int[] out = new int[used * 3]; int m = 0;
        for (int t = 0; t < used; t++) if (alive[t] && ta[t] < n && tb[t] < n && tc[t] < n) { out[m++] = ta[t]; out[m++] = tb[t]; out[m++] = tc[t]; }
        return Arrays.copyOf(out, m);
    }
    private static void countEdge(HashMap<Long, Integer> m, int a, int b) { long k = a < b ? ((long) a << 32) | b : ((long) b << 32) | a; m.merge(k, 1, Integer::sum); }
    private static int addTri(int[] ta, int[] tb, int[] tc, double[] ccx, double[] ccy, double[] cr2, boolean[] alive, double[] X, double[] Y, int used, int a, int b, int c) { setTri(ta, tb, tc, ccx, ccy, cr2, alive, X, Y, used, a, b, c); return used + 1; }
    private static void setTri(int[] ta, int[] tb, int[] tc, double[] ccx, double[] ccy, double[] cr2, boolean[] alive, double[] X, double[] Y, int t, int a, int b, int c) {
        double cross = (X[b] - X[a]) * (Y[c] - Y[a]) - (Y[b] - Y[a]) * (X[c] - X[a]);
        if (cross < 0) { int s = b; b = c; c = s; }
        ta[t] = a; tb[t] = b; tc[t] = c; alive[t] = true;
        double ax = X[a], ay = Y[a], bx = X[b] - ax, by = Y[b] - ay, cx = X[c] - ax, cy = Y[c] - ay;
        double dd = 2 * (bx * cy - by * cx);
        if (Math.abs(dd) < 1e-30) { ccx[t] = ax; ccy[t] = ay; cr2[t] = Double.MAX_VALUE; return; }
        double b2 = bx * bx + by * by, c2 = cx * cx + cy * cy, ux = (cy * b2 - by * c2) / dd, uy = (bx * c2 - cx * b2) / dd;
        ccx[t] = ax + ux; ccy[t] = ay + uy; cr2[t] = ux * ux + uy * uy;
    }

    /**
     * Union of triangles (index triples into pts, CCW) by cancelling shared edges.
     * Returns boundary loops with interior on the left (outer CCW, holes CW); pinch points are split.
     */
    public static ArrayList<float[]> mergeTriangles(float[] pts, int[] tris, int[] subset) {
        HashMap<Long, Boolean> edges = new HashMap<>();
        for (int s : subset) {
            int a = tris[3 * s], b = tris[3 * s + 1], c = tris[3 * s + 2];
            int[][] es = {{a, b}, {b, c}, {c, a}};
            for (int[] e : es) {
                long k = ((long) e[0] << 32) | e[1], rev = ((long) e[1] << 32) | e[0];
                if (edges.remove(rev) == null) edges.put(k, Boolean.TRUE);
            }
        }
        HashMap<Integer, ArrayList<Integer>> out = new HashMap<>();
        for (long k : edges.keySet()) out.computeIfAbsent((int) (k >>> 32), q -> new ArrayList<>()).add((int) (k & 0xffffffffL));
        ArrayList<float[]> loops = new ArrayList<>();
        for (Integer start : new ArrayList<>(out.keySet())) {
            while (out.containsKey(start) && !out.get(start).isEmpty()) {
                ArrayList<Integer> verts = new ArrayList<>(); int v = start, prev = -1; int guard = 0;
                while (guard++ < 10_000_000) {
                    ArrayList<Integer> opts = out.get(v); if (opts == null || opts.isEmpty()) break;
                    int pick = 0;
                    if (opts.size() > 1 && prev >= 0) { // rightmost turn splits touching regions into separate loops
                        double bestAng = Double.MAX_VALUE;
                        double ix = pts[2 * v] - pts[2 * prev], iy = pts[2 * v + 1] - pts[2 * prev + 1];
                        for (int q = 0; q < opts.size(); q++) {
                            double ox = pts[2 * opts.get(q)] - pts[2 * v], oy = pts[2 * opts.get(q) + 1] - pts[2 * v + 1];
                            double ang = Math.atan2(ix * oy - iy * ox, ix * ox + iy * oy);
                            if (ang < bestAng) { bestAng = ang; pick = q; }
                        }
                    }
                    int next = opts.remove(pick); verts.add(v); prev = v; v = next;
                    if (v == start && (out.get(v) == null || verts.size() >= 3)) { break; }
                }
                if (verts.size() >= 3) { float[] l = new float[verts.size() * 2]; for (int i = 0; i < verts.size(); i++) { l[2 * i] = pts[2 * verts.get(i)]; l[2 * i + 1] = pts[2 * verts.get(i) + 1]; } loops.add(removeCollinear(l, 1e-4f)); }
            }
        }
        return loops;
    }

    /** Clipped Voronoi cells for sites (flat xy, n sites) inside [x0,x1]x[y0,y1]. Cells are convex, positive area. */
    public static float[][] voronoiCells(float[] sites, int n, float x0, float y0, float x1, float y1) {
        float[][] cells = new float[n][];
        float cell = (float) Math.max(1e-3, Math.sqrt((x1 - x0) * (y1 - y0) / Math.max(1, n)));
        int gw = (int) ((x1 - x0) / cell) + 1, gh = (int) ((y1 - y0) / cell) + 1;
        ArrayList<ArrayList<Integer>> grid = new ArrayList<>(Collections.nCopies(gw * gh, null));
        for (int i = 0; i < n; i++) { int gx = clampI((int) ((sites[2 * i] - x0) / cell), 0, gw - 1), gy = clampI((int) ((sites[2 * i + 1] - y0) / cell), 0, gh - 1); if (grid.get(gy * gw + gx) == null) grid.set(gy * gw + gx, new ArrayList<>()); grid.get(gy * gw + gx).add(i); }
        for (int i = 0; i < n; i++) {
            if ((i & 127) == 0) checkCancelled();
            float sx = sites[2 * i], sy = sites[2 * i + 1];
            int gx = clampI((int) ((sx - x0) / cell), 0, gw - 1), gy = clampI((int) ((sy - y0) / cell), 0, gh - 1);
            float[] poly = rect(x0, y0, x1, y1);
            for (int r = 0; r <= Math.max(gw, gh); r++) {
                for (int yy = gy - r; yy <= gy + r; yy++) for (int xx = gx - r; xx <= gx + r; xx++) {
                    if (Math.max(Math.abs(xx - gx), Math.abs(yy - gy)) != r || xx < 0 || yy < 0 || xx >= gw || yy >= gh) continue;
                    ArrayList<Integer> list = grid.get(yy * gw + xx); if (list == null) continue;
                    for (int j : list) {
                        if (j == i) continue;
                        double nx = sx - sites[2 * j], ny = sy - sites[2 * j + 1]; double l = Math.hypot(nx, ny); if (l < 1e-6) continue;
                        double mx = (sx + sites[2 * j]) / 2, my = (sy + sites[2 * j + 1]) / 2;
                        poly = clipHalfPlane(poly, nx / l, ny / l, (nx * mx + ny * my) / l);
                        if (poly.length < 6) break;
                    }
                }
                if (poly.length < 6) break;
                double R = 0; for (int k = 0; k < poly.length / 2; k++) R = Math.max(R, Math.hypot(poly[2 * k] - sx, poly[2 * k + 1] - sy));
                if (r >= 1 && r * cell >= 2 * R) break;
            }
            cells[i] = poly.length >= 6 && signedArea(poly) > 1e-6 ? poly : null;
        }
        return cells;
    }

    // ------------------------------------------------------------------ SVG

    public static String toSvg(Result r, boolean paper) {
        StringBuilder s = new StringBuilder(1 << 16);
        s.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"");
        fx(s, r.widthMm); s.append("mm\" height=\""); fx(s, r.heightMm); s.append("mm\" viewBox=\"0 0 "); fx(s, r.widthMm); s.append(' '); fx(s, r.heightMm); s.append("\">\n");
        if (paper) { s.append("<rect width=\"100%\" height=\"100%\" fill=\"").append(hex(r.paperArgb)).append("\"/>\n"); }
        for (Layer l : r.layers) appendLayer(s, l);
        return s.append("</svg>\n").toString();
    }
    /** One layer's cut geometry in artwork millimetres; shared by toSvg and NewStyleExport. */
    static void appendLayer(StringBuilder s, Layer l) {
            if (l.loops.isEmpty() && l.lines.isEmpty()) return;
            s.append("<g id=\"").append(l.name.replaceAll("[^A-Za-z0-9_-]", "-")).append("\">\n");
            if (!l.loops.isEmpty()) {
                s.append("<path fill=\"").append(hex(l.color)).append("\" fill-rule=\"evenodd\" d=\"");
                for (float[] p : l.loops) { for (int i = 0; i < p.length / 2; i++) { s.append(i == 0 ? 'M' : 'L'); fx(s, p[2 * i]); s.append(' '); fx(s, p[2 * i + 1]); s.append(' '); } s.append("Z "); }
                s.append("\"/>\n");
            }
            for (float[] p : l.lines) {
                s.append("<path fill=\"none\" stroke=\"").append(hex(l.color)).append("\" stroke-width=\""); fx(s, l.strokeMm); s.append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"");
                for (int i = 0; i < p.length / 2; i++) { s.append(i == 0 ? 'M' : 'L'); fx(s, p[2 * i]); s.append(' '); fx(s, p[2 * i + 1]); s.append(' '); }
                s.append("\"/>\n");
            }
            s.append("</g>\n");
    }
    private static String hex(int argb) { return String.format(Locale.US, "#%06X", argb & 0xffffff); }
    static void fx(StringBuilder out, float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite coordinate");
        long scaled = Math.round(Math.abs((double) value) * 1000d);
        if (value < 0 && scaled != 0) out.append('-');
        out.append(scaled / 1000L).append('.');
        long f = scaled % 1000L; if (f < 100) out.append('0'); if (f < 10) out.append('0'); out.append(f);
    }
}

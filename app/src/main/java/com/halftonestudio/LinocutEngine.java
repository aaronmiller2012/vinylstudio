package com.halftonestudio;

import java.util.*;

/**
 * Linocut / woodcut: carved strokes that follow the form of the photo. An edge-following flow field
 * (structure tensor, blended with a base angle where the image is flat) drives evenly spaced
 * streamlines; each stroke becomes a tapered ribbon whose width grows with darkness and which ends
 * in a pointed gouge exit where the tone gets light. Strokes can never touch (width < separation).
 * Optional second pass cross-hatches only the darkest areas on its own layer.
 *
 * M5 relief polarity ({@link Options#shadowCarve}): a relief block prints black wherever it is NOT
 * carved. Highlights are cleared away, leaving black lines ("black-line" cutting); shadows are left
 * as solid block and only thin white gouges are cut into them ("white-line" cutting). Above
 * {@link Options#carveTone} the ink sheet is therefore a solid shadow region whose white gouges are
 * cut out of it (even-odd holes, exact ribbons that follow the same flow field). Black strokes keep a
 * full weed gap from the solid region, gouges keep a full minimum cut width of ink around them, and
 * both are verified against a distance field of the final shadow outline.
 */
public final class LinocutEngine {
    private LinocutEngine() {}

    private static final int DETAIL_BUCKETS = 24;

    public static final class Options {
        public float widthMm = 152.4f;
        public float pitchMm = 1.5f;            // centre-to-centre stroke spacing target (0.8..8)
        public float minWidthMm = 0.30f;        // narrowest cuttable stroke
        public float gapMm = 0f;                // weed gap kept between neighbouring strokes (0 = legacy)
        public float maxWidthFraction = 0.62f;  // max stroke width as a fraction of the separation distance (<0.9)
        public float cutoff = 0.16f;            // darkness below this carves nothing (paper white)
        public float flowSmoothMm = 3.6f;       // bigger = calmer strokes, smaller = more detail following
        public float followForm = 1.0f;         // 0 = parallel strokes at baseAngle, 1 = follow edges fully
        public float baseAngleDeg = -35f;       // stroke direction where the image has no structure
        public float taperMm = 2.0f;            // pointed gouge ends
        public float minLengthMm = 2.4f;
        public boolean crossPass = false;       // second pass, rotated 90 degrees, darkest areas only
        public float crossCutoff = 0.65f;
        public int inkArgb = 0xff15151a, crossArgb = 0xff15151a, paperArgb = 0xfff4efe4;
        public boolean autoLevels = true;
        public float gamma = 1f, contrast = 1f;
        public long seed = 11;
        public boolean[] allowed = null;
        /** M4: stroke spacing follows tone so coverage tracks the photograph (false = 558 spacing). */
        public boolean toneSpacing = true;
        /** M5: the clearance between two strokes is the mean of both strokes' local spacings, with a
         *  physical floor of both half widths plus the weed gap (false = M4: the new stroke's own
         *  spacing only, which kept light-tone strokes up to 2.6 pitches away from dark strokes). */
        public boolean balancedSpacing = false;
        /** M5: shadows above {@link #carveTone} are a solid block with carved white gouges. */
        public boolean shadowCarve = false;
        /** Darkness (0..1, after levels) where the block stops being cleared and starts being carved. */
        public float carveTone = 0.52f;
        /** M5: local-contrast gain at facial-feature scale applied to darkness (0 = off, as M4). */
        public float featureContrast = 0f;
        public Options copy() { Options o = new Options(); o.widthMm = widthMm; o.pitchMm = pitchMm; o.minWidthMm = minWidthMm; o.gapMm = gapMm; o.maxWidthFraction = maxWidthFraction; o.cutoff = cutoff; o.flowSmoothMm = flowSmoothMm; o.followForm = followForm; o.baseAngleDeg = baseAngleDeg; o.taperMm = taperMm; o.minLengthMm = minLengthMm; o.crossPass = crossPass; o.crossCutoff = crossCutoff; o.inkArgb = inkArgb; o.crossArgb = crossArgb; o.paperArgb = paperArgb; o.autoLevels = autoLevels; o.gamma = gamma; o.contrast = contrast; o.seed = seed; o.allowed = allowed; o.toneSpacing = toneSpacing; o.balancedSpacing = balancedSpacing; o.shadowCarve = shadowCarve; o.carveTone = carveTone; o.featureContrast = featureContrast; return o; }
        void validate() {
            // Written as !(in range) so NaN from imported or corrupt settings is rejected too.
            if (!(pitchMm >= 0.8f && pitchMm <= 8f)) throw new IllegalArgumentException("pitchMm must be 0.8..8");
            if (!(minWidthMm > 0.05f && minWidthMm <= pitchMm)) throw new IllegalArgumentException("Invalid minWidthMm");
            if (!(maxWidthFraction >= 0.2f && maxWidthFraction <= 0.9f)) throw new IllegalArgumentException("maxWidthFraction must be 0.2..0.9");
            if (!(followForm >= 0 && followForm <= 1)) throw new IllegalArgumentException("followForm must be 0..1");
            if (!(gapMm >= 0 && gapMm < pitchMm) || !Float.isFinite(cutoff) || !Float.isFinite(crossCutoff) || !(taperMm >= 0 && taperMm < 100)
                    || !(minLengthMm >= 0 && minLengthMm < 1000) || !(flowSmoothMm > 0 && flowSmoothMm < 1000) || !Float.isFinite(baseAngleDeg))
                throw new IllegalArgumentException("Invalid Linocut option");
            if (shadowCarve && !(carveTone >= 0.30f && carveTone <= 0.95f)) throw new IllegalArgumentException("carveTone must be 0.30..0.95");
            if (!(featureContrast >= 0f && featureContrast <= 3f)) throw new IllegalArgumentException("featureContrast must be 0..3");
        }
    }

    private static final class Field {
        final int w, h; final float pxMm; final float[] fx, fy, dark, detail; final boolean[] valid;
        Field(int w, int h, float pxMm) {
            this.w = w; this.h = h; this.pxMm = pxMm;
            fx = new float[w * h]; fy = new float[w * h]; dark = new float[w * h]; detail = new float[w * h]; valid = new boolean[w * h];
        }
        float bil(float[] a, float xMm, float yMm) {
            float x = xMm / pxMm - .5f, y = yMm / pxMm - .5f; int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y); float tx = x - x0, ty = y - y0;
            int xa = StyleKit.clampI(x0, 0, w - 1), xb = StyleKit.clampI(x0 + 1, 0, w - 1), ya = StyleKit.clampI(y0, 0, h - 1), yb = StyleKit.clampI(y0 + 1, 0, h - 1);
            return (a[ya * w + xa] * (1 - tx) + a[ya * w + xb] * tx) * (1 - ty) + (a[yb * w + xa] * (1 - tx) + a[yb * w + xb] * tx) * ty;
        }
        void dir(float xMm, float yMm, boolean rotate, float[] out) {
            float vx = bil(fx, xMm, yMm), vy = bil(fy, xMm, yMm); if (rotate) { vx = -vx; vy = -vy; }
            double a = 0.5 * Math.atan2(vy, vx); out[0] = (float) Math.cos(a); out[1] = (float) Math.sin(a);
        }
        boolean valid(float xMm, float yMm) { int x = (int) (xMm / pxMm), y = (int) (yMm / pxMm); return x >= 0 && y >= 0 && x < w && y < h && valid[y * w + x]; }
    }

    private static Field buildField(StyleKit.Source src, Options o) {
        float scale = Math.min(1f, 300f / Math.max(src.w, src.h));
        int dw = Math.max(8, Math.round(src.w * scale)), dh = Math.max(8, Math.round(src.h * scale)); float pxMm = src.widthMm / dw;
        Field f = new Field(dw, dh, pxMm); float[] L = new float[dw * dh];
        for (int y = 0; y < dh; y++) for (int x = 0; x < dw; x++) {if(((x)&63)==0)CancellationCheckpoint.check(); float mx = (x + .5f) * pxMm, my = (y + .5f) * pxMm; L[y * dw + x] = src.lumAt(mx, my); f.valid[y * dw + x] = src.validAt(mx, my); }
        float[] Ls = StyleKit.blur(L, dw, dh, 1.2f);
        float[] jxx = new float[dw * dh], jxy = new float[dw * dh], jyy = new float[dw * dh];
        for (int y = 1; y < dh - 1; y++) for (int x = 1; x < dw - 1; x++) {if(((x)&63)==0)CancellationCheckpoint.check();
            int i = y * dw + x; float gx = (Ls[i + 1] - Ls[i - 1]) * .5f, gy = (Ls[i + dw] - Ls[i - dw]) * .5f; jxx[i] = gx * gx; jxy[i] = gx * gy; jyy[i] = gy * gy;
        }
        float sig = Math.max(1.2f, o.flowSmoothMm / pxMm);
        jxx = StyleKit.blur(jxx, dw, dh, sig); jxy = StyleKit.blur(jxy, dw, dh, sig); jyy = StyleKit.blur(jyy, dw, dh, sig);
        float[] trace = new float[dw * dh]; for (int i = 0; i < trace.length; i++) trace[i] = jxx[i] + jyy[i];
        float[] sorted = trace.clone(); Arrays.sort(sorted); float ref = Math.max(1e-7f, sorted[(int) (sorted.length * .9f)]);
        double phi = Math.toRadians(o.baseAngleDeg); float bx = (float) Math.cos(2 * phi), by = (float) Math.sin(2 * phi);
        for (int i = 0; i < dw * dh; i++) {if(((i)&63)==0)CancellationCheckpoint.check();
            double dxy = jxx[i] - jyy[i], d2 = jxy[i] * 2, mag = Math.sqrt(dxy * dxy + d2 * d2), tr = Math.max(1e-9, trace[i]);
            float coh = (float) (mag / tr);
            float strength = StyleKit.smoothstep(0.05f, 1f, trace[i] / ref);
            float detail = StyleKit.clampF(0.62f * strength + 0.38f * coh, 0f, 1f);
            f.detail[i] = detail;
            float wgt = StyleKit.clampF(o.followForm * (0.35f + 0.65f * coh) * (0.55f + 0.45f * strength), 0f, 1f);
            double th2 = Math.atan2(d2, dxy); // doubled gradient angle; stroke runs along the edge = gradient + 90 deg -> doubled + 180 deg
            f.fx[i] = (float) (wgt * -Math.cos(th2) + (1 - wgt) * bx);
            f.fy[i] = (float) (wgt * -Math.sin(th2) + (1 - wgt) * by);
        }
        float[] D = new float[dw * dh]; for (int i = 0; i < D.length; i++) D[i] = 1f - L[i];
        System.arraycopy(StyleKit.blur(D, dw, dh, Math.max(0.8f, o.pitchMm * .45f / pxMm)), 0, f.dark, 0, D.length);
        if (o.featureContrast > 0) {
            // M5 feature emphasis (a carver's local contrast): eyes, brows, nostrils, lips and glasses
            // frames are darker than the skin around them by less than the tone steps a 0.8 mm cut can
            // show. Unsharp masking at feature scale (about an eye's height, never below 1.5 stroke
            // spacings) deepens them into solid marks and clears the skin beside them.
            float sigmaF = Math.max(1.5f * o.pitchMm, FEATURE_SCALE * Math.max(src.widthMm, src.heightMm)) / pxMm;
            float[] base = StyleKit.blur(f.dark, dw, dh, sigmaF);
            for (int i = 0; i < D.length; i++) f.dark[i] = StyleKit.clampF(f.dark[i] + o.featureContrast * (f.dark[i] - base[i]), 0f, 1f);
        }
        return f;
    }

    private static final class Occupancy {
        final float cell; final int gw, gh; final int[] head; int[] nxt = new int[4096], line = new int[4096], seq = new int[4096]; float[] px = new float[4096], py = new float[4096]; int n = 0;
        final ArrayList<Boolean> deadList = new ArrayList<>(); boolean[] dead = new boolean[1024];
        Occupancy(float W, float H, float cell) { this.cell = cell; gw = (int) (W / cell) + 2; gh = (int) (H / cell) + 2; head = new int[gw * gh]; Arrays.fill(head, -1); }
        /** M5 balanced spacing: local spacing and untapered half width recorded per sample. */
        float[] pr = new float[4096], hr = new float[4096]; float maxP = 0f, maxH = 0f;
        void add(float x, float y, int ln, int sq) { add(x, y, ln, sq, 0f, 0f); }
        void add(float x, float y, int ln, int sq, float pitch, float hw) {
            if (n == nxt.length) { int c = n * 2; nxt = Arrays.copyOf(nxt, c); line = Arrays.copyOf(line, c); seq = Arrays.copyOf(seq, c); px = Arrays.copyOf(px, c); py = Arrays.copyOf(py, c); pr = Arrays.copyOf(pr, c); hr = Arrays.copyOf(hr, c); }
            int g = StyleKit.clampI((int) (y / cell), 0, gh - 1) * gw + StyleKit.clampI((int) (x / cell), 0, gw - 1);
            pr[n] = pitch; hr[n] = hw; maxP = Math.max(maxP, pitch); maxH = Math.max(maxH, hw);
            px[n] = x; py[n] = y; line[n] = ln; seq[n] = sq; nxt[n] = head[g]; head[g] = n++;
        }
        /**
         * Balanced test: blocked when closer than {@code factor} times the MEAN of both samples' local
         * spacings, or closer than both half widths plus {@code land} (the physical weed gap / ink land).
         * Using only the new stroke's spacing (M4) made light strokes keep their wide spacing from a
         * dark neighbour, which is the white halo beside dark strokes.
         */
        boolean blockedBalanced(float x, float y, float pitch, float factor, float hw, float land, int ln, int sq, int guard) {
            float reachMm = Math.max(factor * 0.5f * (pitch + maxP), hw + maxH + land);
            int cx = (int) (x / cell), cy = (int) (y / cell), reach = (int) Math.ceil(reachMm / cell);
            for (int yy = Math.max(0, cy - reach); yy <= Math.min(gh - 1, cy + reach); yy++) for (int xx = Math.max(0, cx - reach); xx <= Math.min(gw - 1, cx + reach); xx++)
                for (int q = head[yy * gw + xx]; q >= 0; q = nxt[q]) {if(((q)&63)==0)CancellationCheckpoint.check();
                    if (line[q] < dead.length && dead[line[q]]) continue;
                    if (line[q] == ln && Math.abs(seq[q] - sq) <= guard) continue;
                    float lim = Math.max(factor * 0.5f * (pitch + pr[q]), hw + hr[q] + land);
                    float dx = px[q] - x, dy = py[q] - y; if (dx * dx + dy * dy < lim * lim) return true;
                }
            return false;
        }
        /** True when a stroke of half width {@code hw} at (x, y) would come within {@code clear} of a recorded ribbon. */
        boolean nearRibbon(float x, float y, float hw, float clear) {
            if (n == 0) return false;
            float reachMm = hw + maxH + clear;
            int cx = (int) (x / cell), cy = (int) (y / cell), reach = (int) Math.ceil(reachMm / cell);
            for (int yy = Math.max(0, cy - reach); yy <= Math.min(gh - 1, cy + reach); yy++) for (int xx = Math.max(0, cx - reach); xx <= Math.min(gw - 1, cx + reach); xx++)
                for (int q = head[yy * gw + xx]; q >= 0; q = nxt[q]) {
                    if (line[q] < dead.length && dead[line[q]]) continue;
                    float lim = hw + hr[q] + clear, dx = px[q] - x, dy = py[q] - y;
                    if (dx * dx + dy * dy < lim * lim) return true;
                }
            return false;
        }
        void kill(int ln) { if (ln >= dead.length) dead = Arrays.copyOf(dead, Math.max(ln + 1, dead.length * 2)); dead[ln] = true; }
        boolean blocked(float x, float y, float r, int ln, int sq, int guard) {
            int cx = (int) (x / cell), cy = (int) (y / cell), reach = (int) Math.ceil(r / cell); float r2 = r * r;
            for (int yy = Math.max(0, cy - reach); yy <= Math.min(gh - 1, cy + reach); yy++) for (int xx = Math.max(0, cx - reach); xx <= Math.min(gw - 1, cx + reach); xx++)
                for (int q = head[yy * gw + xx]; q >= 0; q = nxt[q]) {if(((q)&63)==0)CancellationCheckpoint.check();
                    if (line[q] < dead.length && dead[line[q]]) continue;
                    if (line[q] == ln && Math.abs(seq[q] - sq) <= guard) continue;
                    float dx = px[q] - x, dy = py[q] - y; if (dx * dx + dy * dy < r2) return true;
                }
            return false;
        }
    }

    public static StyleKit.Result generate(int[] argb, int w, int h, Options o) {
        o.validate();
        StyleKit.Source src = new StyleKit.Source(argb, w, h, o.allowed, o.widthMm);
        if (o.autoLevels) src.autoLevels(.02f, .98f);
        src.tone(o.gamma, o.contrast);
        if (!src.anyValid()) throw new IllegalArgumentException("No usable pixels");
        Field field = buildField(src, o);
        StyleKit.Result res = new StyleKit.Result(src.widthMm, src.heightMm); res.paperArgb = o.paperArgb;
        int[] stats = new int[6];
        // Weed gap held between black strokes and the solid block (and between strokes when balanced).
        float gapC = Math.max(o.gapMm, Math.max(0.25f, o.minWidthMm * 0.5f));
        Carve carve = o.shadowCarve ? buildCarve(src, field, o, gapC) : null;
        StyleKit.Layer ink = pass(src, field, o, false, o.cutoff, o.inkArgb, "linocut-ink", stats, carve, null, gapC);
        Occupancy gougeOcc = null;
        if (carve != null) {
            // Relief order: the uncarved block, then the white gouges cut out of it (even-odd holes).
            int budget = Math.max(0, MAX_INK_LOOPS - ink.loops.size() - carve.loops.size());
            gougeOcc = new Occupancy(src.widthMm, src.heightMm, Math.max(0.30f, o.minWidthMm));
            ArrayList<float[]> cuts = gouges(src, field, o, carve, gougeOcc, stats, budget);
            ink.loops.addAll(carve.loops);
            ink.loops.addAll(cuts);
        }
        res.layers.add(ink);
        if (o.crossPass) res.layers.add(pass(src, field, o, true, o.crossCutoff, o.crossArgb, "linocut-cross", stats, null, gougeOcc, gapC));
        res.summary = "Linocut: " + stats[0] + " strokes, " + stats[1] + " skipped (self-touching), pitch " + o.pitchMm + " mm"
            + (carve == null ? "" : ", " + stats[3] + " white gouges carved in " + carve.components + " solid shadow areas");
        return res;
    }

    /** Suite layers are capped at 5000 cut pieces; stay below it with headroom for the shadow outline. */
    static final int MAX_INK_LOOPS = 4800;
    /** Pixel budget of the shadow-block grid (about 4 float fields of this size are live at once). */
    static final double CARVE_PIXELS = 1.0e6;
    /** White fraction below which the block is left solid (deepest shadows). */
    static final float GOUGE_CUTOFF = 0.07f;
    /** Hysteresis: a shadow region must somewhere be this much darker than carveTone. */
    static final float CARVE_SEED = 0.10f;
    /** Inspector-guided repair rounds on the block outline. */
    static final int BLOCK_REPAIR_ROUNDS = 3;
    /** Feature-emphasis scale as a fraction of the artwork's long side (about a portrait eye's height). */
    static final float FEATURE_SCALE = 0.015f;

    /** M5: the uncarved shadow block (final cut outline) and a signed distance to its outline. */
    static final class Carve {
        final ArrayList<float[]> loops; final float g; final int w, h; final float[] sd; final int components;
        Carve(ArrayList<float[]> loops, float g, int w, int h, float[] sd, int components) { this.loops = loops; this.g = g; this.w = w; this.h = h; this.sd = sd; this.components = components; }
        /** Signed distance in mm, positive inside solid ink. Grid pixel (i, j) has its centre at ((i - .5) g, (j - .5) g). */
        float sd(float xMm, float yMm) {
            float fx = xMm / g + .5f, fy = yMm / g + .5f;
            int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
            if (!(x0 >= 0 && y0 >= 0 && x0 + 1 < w && y0 + 1 < h)) return -1e3f;
            float tx = fx - x0, ty = fy - y0; int i = y0 * w + x0;
            return (sd[i] * (1 - tx) + sd[i + 1] * tx) * (1 - ty) + (sd[i + w] * (1 - tx) + sd[i + w + 1] * tx) * ty;
        }
    }

    /**
     * The block region left solid, built on a physical grid of pitch g:
     *  1. darkness at or above {@link Options#carveTone}, inside the Snip and the artboard;
     *  2. hysteresis: only regions that somewhere reach carveTone + {@link #CARVE_SEED} are shadows,
     *     so flat mid-tones hovering at the threshold stay black-line cut instead of becoming blotches;
     *  3. opening by the minimum cut width (no ink neck or speck below the minimum), narrow light
     *     slivers below the weed gap filled, and opened again;
     *  4. regions smaller than about one stroke spacing dropped (black-line strokes draw them);
     *  5. outline traced from the blurred signed distance (sub-pixel, no staircase), which is also the
     *     distance field that black strokes and gouges are checked against.
     * Returns null when no shadow survives.
     */
    static Carve buildCarve(StyleKit.Source src, Field f, Options o, float gapC) {
        float minW = o.minWidthMm, W = src.widthMm, H = src.heightMm;
        float g = (float) Math.max(0.08, Math.max(minW / 4.0, Math.sqrt(W * (double) H / CARVE_PIXELS)));
        int w = (int) Math.ceil(W / g) + 2, h = (int) Math.ceil(H / g) + 2;
        float[] field = new float[w * h]; Arrays.fill(field, -1f);
        boolean[] mask = new boolean[w * h];
        boolean any = false;
        for (int j = 1; j < h - 1; j++) {
            if ((j & 31) == 0) StyleKit.checkCancelled();
            float y = (j - .5f) * g; if (y >= H) continue;
            for (int i = 1; i < w - 1; i++) {
                float x = (i - .5f) * g; if (x >= W || !src.validAt(x, y)) continue;
                float v = f.bil(f.dark, x, y) - o.carveTone; field[j * w + i] = v;
                if (v >= 0) { mask[j * w + i] = true; any = true; }
            }
        }
        if (!any) return null;
        float span = Math.max(2.2f * minW, 1.1f * o.pitchMm) / g;
        keepComponents(mask, field, w, h, CARVE_SEED, 0f);
        int rv = discRadius(minW + g, g), rg = discRadius(gapC + g, g); // one grid step of margin: the traced outline is sub-pixel
        mask = CutGeometryConditioner.open(mask, w, h, rv);
        boolean[] waste = new boolean[mask.length];
        for (int i = 0; i < mask.length; i++) waste[i] = !mask[i];
        boolean[] wideWaste = CutGeometryConditioner.open(waste, w, h, rg);
        for (int i = 0; i < mask.length; i++) if (waste[i] && !wideWaste[i] && field[i] > -1f) mask[i] = true;
        waste = null; wideWaste = null;
        for (int x = 0; x < w; x++) { mask[x] = false; mask[(h - 1) * w + x] = false; }
        for (int y = 0; y < h; y++) { mask[y * w] = false; mask[y * w + w - 1] = false; }
        mask = CutGeometryConditioner.open(mask, w, h, rv);
        if (keepComponents(mask, field, w, h, -1e9f, span) == 0) return null;
        boolean[] usable = new boolean[mask.length];
        for (int i = 0; i < mask.length; i++) usable[i] = field[i] > -1f;
        field = null;
        float[][] sdOut = new float[1][];
        ArrayList<float[]> block = traceBlock(mask, w, h, g, W, H, minW, sdOut);
        // Guided repair with the export inspector as the oracle: opening leaves cusps (waste wedges
        // narrower than the weed gap) and the sub-pixel outline can pinch a hole against its outline.
        // Fill waste at a reported gap, cut ink at a reported neck, re-open, retrace.
        int spot = discRadius(Math.max(minW, gapC) + g, g) + 1;
        for (int round = 0; round < BLOCK_REPAIR_ROUNDS && !block.isEmpty(); round++) {
            StyleKit.Result one = new StyleKit.Result(W, H);
            StyleKit.Layer l = new StyleKit.Layer("block", 0xff000000); l.loops.addAll(block); one.layers.add(l);
            List<StyleCutGeometryPreflight.Warning> found;
            try { found = StyleCutGeometryPreflight.inspect(one, minW, gapC, 2000); }
            catch (IllegalArgumentException invalid) { break; }
            if (found.isEmpty()) break;
            boolean[] cut = new boolean[mask.length], fill = new boolean[mask.length]; boolean anyCut = false, anyFill = false;
            for (StyleCutGeometryPreflight.Warning wv : found) {
                int cx = Math.round(wv.midXMm / g + .5f), cy = Math.round(wv.midYMm / g + .5f);
                if (cx < 1 || cy < 1 || cx >= w - 1 || cy >= h - 1) continue;
                if (mask[cy * w + cx]) { cut[cy * w + cx] = true; anyCut = true; } else { fill[cy * w + cx] = true; anyFill = true; }
            }
            if (!anyCut && !anyFill) break;
            float lim = spot * (float) spot + spot + .01f;
            if (anyFill) { float[] d = CutGeometryConditioner.edt(fill, w, h); for (int i = 0; i < mask.length; i++) if (d[i] <= lim && usable[i]) mask[i] = true; }
            if (anyCut) { float[] d = CutGeometryConditioner.edt(cut, w, h); for (int i = 0; i < mask.length; i++) if (d[i] <= lim) mask[i] = false; }
            for (int x = 0; x < w; x++) { mask[x] = false; mask[(h - 1) * w + x] = false; }
            for (int y = 0; y < h; y++) { mask[y * w] = false; mask[y * w + w - 1] = false; }
            mask = CutGeometryConditioner.open(mask, w, h, rv);
            block = traceBlock(mask, w, h, g, W, H, minW, sdOut);
        }
        if (block.isEmpty()) return null;
        float[] sd = sdOut[0];
        for (int i = 0; i < sd.length; i++) sd[i] *= g;
        int outers = 0;
        for (int d : nestingDepth(block)) if ((d & 1) == 0) outers++;
        return new Carve(block, g, w, h, sd, outers);
    }

    /** Outline of the mask from its blurred signed distance (pixels, positive inside), returned in sdOut[0]. */
    private static ArrayList<float[]> traceBlock(boolean[] mask, int w, int h, float g, float W, float H, float minW, float[][] sdOut) {
        boolean[] outside = new boolean[mask.length];
        for (int i = 0; i < mask.length; i++) outside[i] = !mask[i];
        float[] toOut = CutGeometryConditioner.edt(outside, w, h); outside = null;
        float[] toIn = CutGeometryConditioner.edt(mask, w, h);
        float[] sd = new float[mask.length];
        for (int i = 0; i < sd.length; i++) sd[i] = mask[i] ? (float) Math.sqrt(toOut[i]) - .5f : .5f - (float) Math.sqrt(toIn[i]);
        toOut = null; toIn = null;
        sd = StyleKit.blur(sd, w, h, .6f);
        for (int x = 0; x < w; x++) { sd[x] = -1; sd[(h - 1) * w + x] = -1; }
        for (int y = 0; y < h; y++) { sd[y * w] = -1; sd[y * w + w - 1] = -1; }
        sdOut[0] = sd;
        ArrayList<float[]> traced = TopographicLayersEngine.contours(sd, w, h, 0f, g);
        ArrayList<float[]> block = new ArrayList<>();
        float eps = Math.min(.04f, g * .35f), minArea = minW * minW * .5f;
        for (float[] loop : traced) {
            StyleKit.checkCancelled();
            float[] closed = Arrays.copyOf(loop, loop.length + 2);
            closed[loop.length] = loop[0]; closed[loop.length + 1] = loop[1];
            float[] simple = StyleKit.rdp(closed, eps);
            if (simple.length >= 8) simple = Arrays.copyOf(simple, simple.length - 2);
            float[] keep = simple.length >= 6 && StyleKit.selfIntersections(simple, 1) == 0 ? simple : loop;
            if (keep.length < 6 || StyleKit.selfIntersections(keep, 1) > 0) continue;
            if (Math.abs(StyleKit.signedArea(keep)) < minArea) continue;
            for (int k = 0; k < keep.length; k += 2) { keep[k] = Math.max(0f, Math.min(W, keep[k])); keep[k + 1] = Math.max(0f, Math.min(H, keep[k + 1])); }
            block.add(keep);
        }
        return block;
    }

    /** Smallest integer pixel radius whose disc (2r + 1 pixels) spans {@code mm}. */
    private static int discRadius(float mm, float g) { return Math.max(1, (int) Math.ceil((mm / g - 1f) / 2f)); }

    /**
     * Clears 4-connected ink components that never reach {@code seed} in {@code field}, or whose bounds
     * are below {@code spanPx} in both directions. Returns the number of components kept.
     */
    private static int keepComponents(boolean[] mask, float[] field, int w, int h, float seed, float spanPx) {
        int[] stack = new int[1024], members = new int[1024]; boolean[] seen = new boolean[mask.length]; int kept = 0;
        for (int s = 0; s < mask.length; s++) {
            if (!mask[s] || seen[s]) continue;
            if ((s & 4095) == 0) StyleKit.checkCancelled();
            int top = 0, count = 0, x0 = w, y0 = h, x1 = -1, y1 = -1; float peak = -Float.MAX_VALUE;
            stack[top++] = s; seen[s] = true;
            while (top > 0) {
                int i = stack[--top], x = i % w, y = i / w;
                if (count == members.length) members = Arrays.copyOf(members, count * 2);
                members[count++] = i; peak = Math.max(peak, field[i]);
                x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y);
                int[] nb = {x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1, y > 0 ? i - w : -1, y < h - 1 ? i + w : -1};
                for (int q : nb) if (q >= 0 && mask[q] && !seen[q]) { seen[q] = true; if (top == stack.length) stack = Arrays.copyOf(stack, top * 2); stack[top++] = q; }
            }
            boolean keep = peak >= seed && (x1 - x0 + 1 >= spanPx || y1 - y0 + 1 >= spanPx);
            if (keep) kept++; else for (int k = 0; k < count; k++) mask[members[k]] = false;
        }
        return kept;
    }

    /** Even-odd nesting depth of each loop (loops are simple and mutually non-crossing). */
    static int[] nestingDepth(List<float[]> loops) {
        int n = loops.size(); float[][] box = new float[n][]; double[] area = new double[n];
        for (int i = 0; i < n; i++) { box[i] = bounds(loops.get(i)); area[i] = Math.abs(StyleKit.signedArea(loops.get(i))); }
        int[] depth = new int[n];
        for (int i = 0; i < n; i++) {
            if ((i & 63) == 0) StyleKit.checkCancelled();
            float[] p = loops.get(i); float x = p[0], y = p[1];
            for (int j = 0; j < n; j++) {
                if (j == i || area[j] <= area[i] || x < box[j][0] || x > box[j][2] || y < box[j][1] || y > box[j][3]) continue;
                if (MaskedFacetClipper.inside(loops.get(j), x, y)) depth[i]++;
            }
        }
        return depth;
    }

    static float[] bounds(float[] p) {
        float a = Float.POSITIVE_INFINITY, b = a, c = Float.NEGATIVE_INFINITY, d = c;
        for (int k = 0; k + 1 < p.length; k += 2) { a = Math.min(a, p[k]); c = Math.max(c, p[k]); b = Math.min(b, p[k + 1]); d = Math.max(d, p[k + 1]); }
        return new float[]{a, b, c, d};
    }

    private static float localPitch(Options o, float dark, float detail) {
        float tighten = 1f - 0.24f * detail - 0.14f * dark;
        float minPitch = Math.max(Math.max(o.minWidthMm * 1.7f, o.pitchMm * 0.60f), o.minWidthMm + o.gapMm * 1.3f);
        float pitch = Math.max(minPitch, o.pitchMm * tighten);
        if (!o.toneSpacing) return pitch;
        // M4 tone-to-line mapping: vinyl coverage is stroke width / stroke spacing. When the
        // cut minimum leaves little width range (0.88-1.2 mm at a 2 mm pitch with a 0.8 mm
        // minimum), width alone cannot carry the photograph and every tone looked alike.
        // Spacing therefore widens in light tones so coverage tracks the tone. Detail still
        // tightens spacing locally so features keep their strokes.
        float tone = toneWithDetail(dark, detail);
        float maxW = Math.max(o.minWidthMm, o.maxWidthFraction * o.pitchMm * 0.82f);
        float width = o.minWidthMm + (maxW - o.minWidthMm) * StyleKit.smoothstep(o.cutoff, 1f, tone);
        float wanted = width / Math.max(0.05f, tone) * (1f - 0.30f * detail);
        return StyleKit.clampF(wanted, pitch, o.pitchMm * 3.2f);
    }

    private static float toneWithDetail(float dark, float detail) {
        return StyleKit.clampF(Math.max(dark, 0.70f * dark + 0.30f * detail), 0f, 1f);
    }

    /** Untapered stroke width at a point (the M4 tone-to-width mapping, unchanged). */
    private static float strokeWidth(Options o, float maxWBase, float dark, float detail, float cutoff) {
        float tone = toneWithDetail(dark, detail);
        float localPitch = localPitch(o, dark, detail);
        float maxW = Math.max(o.minWidthMm, Math.min(maxWBase, o.maxWidthFraction * localPitch * 0.96f));
        // Leave a full weed gap to the neighbouring stroke.
        if (o.gapMm > 0) maxW = Math.max(o.minWidthMm, Math.min(maxW, localPitch - o.gapMm * 1.3f));
        float localCutoff = Math.max(0.03f, cutoff - 0.08f * detail);
        return o.minWidthMm + (maxW - o.minWidthMm) * StyleKit.smoothstep(localCutoff, 1f, tone);
    }

    private static StyleKit.Layer pass(StyleKit.Source src, Field f, Options o, boolean rotate, float cutoff, int color, String name, int[] stats,
                                       Carve carve, Occupancy avoid, float gapC) {
        StyleKit.Layer layer = new StyleKit.Layer(name, color);
        float minPitch = Math.max(Math.max(o.minWidthMm * 1.7f, o.pitchMm * 0.60f), o.minWidthMm + o.gapMm * 1.3f);
        float occCell = Math.max(0.30f, minPitch * 0.65f);
        float step = Math.max(0.18f, o.pitchMm * 0.22f);
        float gs = Math.max(o.minWidthMm * 1.2f, o.pitchMm * 0.65f);
        float maxWBase = Math.max(o.minWidthMm, o.maxWidthFraction * o.pitchMm * 0.82f);
        // M5: black strokes stay a full weed gap outside the solid block; cross strokes stay off the white gouges.
        final Carve block = rotate ? null : carve;
        final boolean m5 = o.balancedSpacing || block != null || avoid != null;
        // Samples are one step apart: two ribbons can pass up to half a step closer than their nearest samples.
        final float land = o.gapMm + 0.5f * step;
        Occupancy occ = new Occupancy(src.widthMm, src.heightMm, occCell);
        SplittableRandom rnd = new SplittableRandom(o.seed + (rotate ? 977 : 0));
        int sx = Math.max(1, (int) Math.ceil(src.widthMm / gs)), sy = Math.max(1, (int) Math.ceil(src.heightMm / gs));
        int totalSeeds = sx * sy;
        int[] bucketOf = new int[totalSeeds];
        int[] counts = new int[DETAIL_BUCKETS];
        Arrays.fill(bucketOf, -1);
        for (int i = 0; i < totalSeeds; i++) {if(((i)&63)==0)CancellationCheckpoint.check();
            float cx = ((i % sx) + 0.5f) * gs;
            float cy = ((i / sx) + 0.5f) * gs;
            if (!f.valid(cx, cy)) continue;
            float dark = f.bil(f.dark, cx, cy);
            float detail = f.bil(f.detail, cx, cy);
            float score = toneWithDetail(dark, detail) * (0.60f + 0.40f * detail);
            if (score <= Math.max(0.04f, cutoff - 0.06f)) continue;
            int b = StyleKit.clampI((int) Math.floor(score * (DETAIL_BUCKETS - 1) + 0.5f), 0, DETAIL_BUCKETS - 1);
            bucketOf[i] = b;
            counts[b]++;
        }
        int[][] buckets = new int[DETAIL_BUCKETS][];
        for (int b = 0; b < DETAIL_BUCKETS; b++) buckets[b] = new int[counts[b]];
        Arrays.fill(counts, 0);
        for (int i = 0; i < totalSeeds; i++) {if(((i)&63)==0)CancellationCheckpoint.check();
            int b = bucketOf[i];
            if (b >= 0) buckets[b][counts[b]++] = i;
        }
        for (int b = 0; b < DETAIL_BUCKETS; b++) {if(((b)&63)==0)CancellationCheckpoint.check();
            int[] arr = buckets[b];
            for (int i = arr.length - 1; i > 0; i--) {if(((i)&63)==0)CancellationCheckpoint.check();
                int j = rnd.nextInt(i + 1);
                int t = arr[i]; arr[i] = arr[j]; arr[j] = t;
            }
        }
        float[] dv = new float[2];
        int lineId = 0, maxSteps = (int) (650f / step) + 50, guard = Math.max(3, (int) Math.ceil(2 * minPitch / step) + 1);
        // M5 seam: strokes run along the block outline (it is an isophote, like the flow), so the
        // first stroke beside the block is seeded at exactly the clearance distance. Left to random
        // seeds that band stays empty and reads as a white halo one spacing wide.
        float[] pre = block == null ? new float[0] : outlineSeeds(block, o, maxWBase, gapC, f, cutoff);
        int preN = pre.length / 2;
        for (int pi = 0; pi < preN; pi++) {
            if ((pi & 63) == 0) StyleKit.checkCancelled();
            lineId = trace(src, f, o, rotate, cutoff, stats, block, avoid, gapC, layer, occ, dv, pre[2 * pi], pre[2 * pi + 1], lineId, maxSteps, guard, step, maxWBase, m5, land);
        }
        for (int b = DETAIL_BUCKETS - 1; b >= 0; b--) {if(((b)&63)==0)CancellationCheckpoint.check();
            int[] order = buckets[b];
            for (int oi = 0; oi < order.length; oi++) {if(((oi)&63)==0)CancellationCheckpoint.check();
                int seed = order[oi];
                if (((oi + (DETAIL_BUCKETS - 1 - b) * 257) & 255) == 0) StyleKit.checkCancelled();
                float x0 = ((seed % sx) + (float) rnd.nextDouble()) * gs;
                float y0 = ((seed / sx) + (float) rnd.nextDouble()) * gs;
                lineId = trace(src, f, o, rotate, cutoff, stats, block, avoid, gapC, layer, occ, dv, x0, y0, lineId, maxSteps, guard, step, maxWBase, m5, land);
            }
        }
        return layer;
    }

    /** Traces, widths and emits one stroke from (x0, y0); returns the next line id. */
    private static int trace(StyleKit.Source src, Field f, Options o, boolean rotate, float cutoff, int[] stats, Carve block, Occupancy avoid, float gapC,
                             StyleKit.Layer layer, Occupancy occ, float[] dv, float x0, float y0, int lineId, int maxSteps, int guard, float step, float maxWBase,
                             boolean m5, float land) {
        {
            {
                if (!f.valid(x0, y0)) return lineId;
                float dark0 = f.bil(f.dark, x0, y0);
                float detail0 = f.bil(f.detail, x0, y0);
                float tone0 = toneWithDetail(dark0, detail0);
                float cutoff0 = Math.max(0.03f, cutoff - 0.08f * detail0);
                float pitch0 = localPitch(o, dark0, detail0);
                float hw0 = m5 ? 0.5f * strokeWidth(o, maxWBase, dark0, detail0, cutoff) : 0f;
                if (tone0 < cutoff0) return lineId;
                // M5: keep the whole ribbon on the artboard instead of shifting it into a neighbour later.
                if (m5 && !onBoard(x0, y0, 1.2f * hw0, src)) return lineId;
                if (o.balancedSpacing ? occ.blockedBalanced(x0, y0, pitch0, 1f, hw0, land, -1, 0, 0) : occ.blocked(x0, y0, pitch0, -1, 0, 0)) return lineId;
                if (block != null && block.sd(x0, y0) > -(1.2f * hw0 + gapC + 1.5f * block.g)) return lineId;
                if (avoid != null && avoid.nearRibbon(x0, y0, hw0, gapC + step * .5f)) return lineId;
                int id = lineId++;
                ArrayList<float[]> fwd = new ArrayList<>(), back = new ArrayList<>();
                occ.add(x0, y0, id, 0, pitch0, hw0);
                f.dir(x0, y0, rotate, dv); float d0x = dv[0], d0y = dv[1];
                for (int side = 0; side < 2; side++) {if(((side)&63)==0)CancellationCheckpoint.check();
                    float x = x0, y = y0, dx = side == 0 ? d0x : -d0x, dy = side == 0 ? d0y : -d0y;
                    ArrayList<float[]> out = side == 0 ? fwd : back;
                    for (int s = 1; s <= maxSteps; s++) {if(((s)&63)==0)CancellationCheckpoint.check();
                        f.dir(x, y, rotate, dv);
                        if (dv[0] * dx + dv[1] * dy < 0) { dv[0] = -dv[0]; dv[1] = -dv[1]; }
                        float mx = x + dv[0] * step * .5f, my = y + dv[1] * step * .5f;
                        f.dir(mx, my, rotate, dv);
                        if (dv[0] * dx + dv[1] * dy < 0) { dv[0] = -dv[0]; dv[1] = -dv[1]; }
                        if (dv[0] * dx + dv[1] * dy < 0.42f) break; // turned more than ~65 degrees in one step
                        float nx = x + dv[0] * step, ny = y + dv[1] * step;
                        if (nx < 0 || ny < 0 || nx >= src.widthMm || ny >= src.heightMm || !f.valid(nx, ny)) break;
                        float dark = f.bil(f.dark, nx, ny);
                        float detail = f.bil(f.detail, nx, ny);
                        float tone = toneWithDetail(dark, detail);
                        float localCutoff = Math.max(0.03f, cutoff - 0.08f * detail);
                        if (tone < localCutoff) break;
                        int sq = side == 0 ? s : -s;
                        float localPitch = localPitch(o, dark, detail);
                        // M4 review: the stroke's own recent points are ignored over twice the
                        // blocking radius actually used. With tone spacing that radius reaches
                        // 2.6x pitch; a guard sized from minPitch made light-tone strokes block
                        // themselves and break into ~5 mm dashes.
                        float radius = localPitch * 0.82f;
                        float hw = m5 ? 0.5f * strokeWidth(o, maxWBase, dark, detail, cutoff) : 0f;
                        if (o.balancedSpacing) {
                            float own = Math.max(radius, 2 * hw + land);
                            int localGuard = Math.max(guard, (int) Math.ceil(2 * own / step) + 1);
                            if (occ.blockedBalanced(nx, ny, localPitch, 0.82f, hw, land, id, sq, localGuard)) break;
                        } else {
                            int localGuard = o.toneSpacing ? Math.max(guard, (int) Math.ceil(2 * radius / step) + 1) : guard;
                            if (occ.blocked(nx, ny, radius, id, sq, localGuard)) break;
                        }
                        if (m5 && !onBoard(nx, ny, 1.2f * hw, src)) break;
                        if (block != null && block.sd(nx, ny) > -(1.2f * hw + gapC + 1.5f * block.g)) break;
                        if (avoid != null && avoid.nearRibbon(nx, ny, hw, gapC + step * .5f)) break;
                        occ.add(nx, ny, id, sq, localPitch, hw); out.add(new float[]{nx, ny});
                        x = nx; y = ny; dx = dv[0]; dy = dv[1];
                    }
                }
                int m = back.size() + 1 + fwd.size();
                float meanDetail = detail0;
                if (m <= 1) { occ.kill(id); return lineId; }
                float[] xy = new float[2 * m]; int k = 0;
                for (int i = back.size() - 1; i >= 0; i--) {if(((i)&63)==0)CancellationCheckpoint.check(); xy[k++] = back.get(i)[0]; xy[k++] = back.get(i)[1]; }
                xy[k++] = x0; xy[k++] = y0; for (float[] q : fwd) {CancellationCheckpoint.check(); xy[k++] = q[0]; xy[k++] = q[1]; }
                float[] hw = new float[m]; float total = (m - 1) * step;
                for (int i = 0; i < m; i++) meanDetail += f.bil(f.detail, xy[2 * i], xy[2 * i + 1]);
                meanDetail /= (m + 1f);
                float minLength = Math.max(1.8f, o.minLengthMm - 0.9f * meanDetail);
                if (total < minLength) { occ.kill(id); return lineId; }
                for (int i = 0; i < m; i++) {if(((i)&63)==0)CancellationCheckpoint.check();
                    float x = xy[2 * i], y = xy[2 * i + 1];
                    float dark = f.bil(f.dark, x, y), detail = f.bil(f.detail, x, y);
                    float wv = strokeWidth(o, maxWBase, dark, detail, cutoff);
                    float edge = Math.min(i * step, total - i * step);
                    float tp = o.taperMm <= 0 ? 1f : (float) Math.pow(StyleKit.clampF(edge / o.taperMm, 0, 1), 0.70);
                    // A physically cuttable ribbon may taper only to the selected
                    // material minimum, never to an uncuttable needle.
                    hw[i] = 0.5f * Math.max(o.minWidthMm, wv * tp);
                }
                float[] poly = StyleKit.ribbon(xy, hw, 2.0f, false);
                if (poly == null || StyleKit.selfIntersections(poly, 1) > 0) { occ.kill(id); stats[1]++; return lineId; }
                // A normal ribbon whose cap barely crosses the page should be
                // moved intact, not cropped into an underwidth end fragment.
                poly=StyleKit.translateWithinArtboardOrOriginal(poly,src.widthMm,src.heightMm);
                // M5: verify the FINAL outline, not the centreline: every vertex keeps the
                // weed gap from the solid block.
                if (block != null && !clearOf(poly, block, gapC)) { occ.kill(id); stats[2]++; return lineId; }
                layer.loops.add(poly); stats[0]++;
            }
        }
        return lineId;
    }

    private static boolean onBoard(float x, float y, float r, StyleKit.Source src) {
        return x >= r && y >= r && x <= src.widthMm - r && y <= src.heightMm - r;
    }

    /** Seeds one stroke clearance outside the block outline, about one spacing apart along it. */
    private static float[] outlineSeeds(Carve c, Options o, float maxWBase, float gapC, Field f, float cutoff) {
        float[] out = new float[256]; int n = 0;
        for (float[] loop : c.loops) {
            int m = loop.length / 2; float run = 0f;
            for (int i = 0; i < m; i++) {
                float ax = loop[2 * i], ay = loop[2 * i + 1], bx = loop[2 * ((i + 1) % m)], by = loop[2 * ((i + 1) % m) + 1];
                float len = (float) Math.hypot(bx - ax, by - ay); if (len < 1e-6f) continue;
                float nx = -(by - ay) / len, ny = (bx - ax) / len;
                for (float t = run == 0f ? 0f : (o.pitchMm - run); t < len; t += o.pitchMm) {
                    float px = ax + (bx - ax) * t / len, py = ay + (by - ay) * t / len;
                    float dark = f.bil(f.dark, px, py), detail = f.bil(f.detail, px, py);
                    float hw = 0.5f * strokeWidth(o, maxWBase, dark, detail, cutoff);
                    float off = 1.2f * hw + gapC + 1.5f * c.g + 0.05f;
                    float sx = px + nx * off, sy = py + ny * off;
                    if (c.sd(sx, sy) >= 0) { sx = px - nx * off; sy = py - ny * off; }
                    if (n + 2 > out.length) out = Arrays.copyOf(out, out.length * 2);
                    out[n++] = sx; out[n++] = sy;
                }
                run = (run + len) % o.pitchMm;
            }
        }
        return Arrays.copyOf(out, n);
    }

    /** Every vertex of a black ribbon lies at least {@code gap} outside the solid block. */
    private static boolean clearOf(float[] poly, Carve c, float gap) {
        for (int k = 0; k + 1 < poly.length; k += 2) if (c.sd(poly[k], poly[k + 1]) > -(gap + c.g)) return false;
        return true;
    }

    /** White gouge width for the white fraction {@code light} (1 - darkness) inside the block. */
    private static float gougeWidth(Options o, float gMin, float gMax, float light) {
        return gMin + (gMax - gMin) * StyleKit.smoothstep(GOUGE_CUTOFF, Math.max(GOUGE_CUTOFF + 0.05f, 1f - o.carveTone), light);
    }
    /** Gouge spacing: white coverage (gouge width / spacing) tracks the white fraction, the mirror of M4 tone spacing. */
    private static float gougePitch(Options o, float w, float light, float detail, float land) {
        float wanted = w / Math.max(0.03f, light) * (1f - 0.30f * detail);
        return Math.max(w + land, Math.min(wanted, o.pitchMm * 4f));
    }

    /**
     * White-line cutting: gouges follow the same form field as the black strokes, inside the solid block,
     * each a closed ribbon with round (U-gouge) ends that becomes an even-odd hole. Every gouge keeps a
     * full minimum cut width of ink to the block outline and to the next gouge, and is at least the
     * cut minimum (and the weed gap) wide, so both the carved waste and the ink between are cuttable.
     */
    private static ArrayList<float[]> gouges(StyleKit.Source src, Field f, Options o, Carve c, Occupancy occ, int[] stats, int budget) {
        ArrayList<float[]> out = new ArrayList<>();
        if (budget <= 0) return out;
        float minW = o.minWidthMm, land = minW;
        float gMin = Math.max(minW, o.gapMm + 0.1f);
        float gMax = Math.max(gMin, o.maxWidthFraction * o.pitchMm * 0.82f);
        float step = Math.max(0.18f, o.pitchMm * 0.22f);
        float gs = Math.max(minW * 1.2f, o.pitchMm * 0.65f);
        float inset = land + c.g; // ink kept between a gouge edge and the block outline
        SplittableRandom rnd = new SplittableRandom(o.seed + 4241);
        int sx = Math.max(1, (int) Math.ceil(src.widthMm / gs)), sy = Math.max(1, (int) Math.ceil(src.heightMm / gs));
        int[] bucketOf = new int[sx * sy]; int[] counts = new int[DETAIL_BUCKETS];
        Arrays.fill(bucketOf, -1);
        for (int i = 0; i < bucketOf.length; i++) {if(((i)&63)==0)CancellationCheckpoint.check();
            float cx = ((i % sx) + 0.5f) * gs, cy = ((i / sx) + 0.5f) * gs;
            if (!f.valid(cx, cy) || c.sd(cx, cy) < 0.5f * gMin + inset) continue;
            float light = 1f - f.bil(f.dark, cx, cy), detail = f.bil(f.detail, cx, cy);
            if (light < GOUGE_CUTOFF - 0.04f * detail) continue;
            float score = StyleKit.clampF(light * 2f, 0f, 1f) * (0.60f + 0.40f * detail);
            int b = StyleKit.clampI((int) Math.floor(score * (DETAIL_BUCKETS - 1) + 0.5f), 0, DETAIL_BUCKETS - 1);
            bucketOf[i] = b; counts[b]++;
        }
        int[][] buckets = new int[DETAIL_BUCKETS][];
        for (int b = 0; b < DETAIL_BUCKETS; b++) buckets[b] = new int[counts[b]];
        Arrays.fill(counts, 0);
        for (int i = 0; i < bucketOf.length; i++) if (bucketOf[i] >= 0) buckets[bucketOf[i]][counts[bucketOf[i]]++] = i;
        for (int[] arr : buckets) for (int i = arr.length - 1; i > 0; i--) { int j = rnd.nextInt(i + 1); int t = arr[i]; arr[i] = arr[j]; arr[j] = t; }
        float[] dv = new float[2];
        int lineId = 0, maxSteps = (int) (650f / step) + 50;
        for (int b = DETAIL_BUCKETS - 1; b >= 0 && out.size() < budget; b--) {
            int[] order = buckets[b];
            for (int oi = 0; oi < order.length && out.size() < budget; oi++) {
                if ((oi & 63) == 0) StyleKit.checkCancelled();
                int seed = order[oi];
                float x0 = ((seed % sx) + (float) rnd.nextDouble()) * gs, y0 = ((seed / sx) + (float) rnd.nextDouble()) * gs;
                if (!f.valid(x0, y0)) continue;
                float light0 = 1f - f.bil(f.dark, x0, y0), detail0 = f.bil(f.detail, x0, y0);
                if (light0 < GOUGE_CUTOFF - 0.04f * detail0) continue;
                float w0 = gougeWidth(o, gMin, gMax, light0), p0 = gougePitch(o, w0, light0, detail0, land);
                if (c.sd(x0, y0) < 0.6f * w0 + inset) continue;
                if (occ.blockedBalanced(x0, y0, p0, 1f, 0.5f * w0, land + step * .5f, -1, 0, 0)) continue;
                int id = lineId++;
                ArrayList<float[]> fwd = new ArrayList<>(), back = new ArrayList<>();
                occ.add(x0, y0, id, 0, p0, 0.5f * w0);
                f.dir(x0, y0, false, dv); float d0x = dv[0], d0y = dv[1];
                for (int side = 0; side < 2; side++) {
                    float x = x0, y = y0, dx = side == 0 ? d0x : -d0x, dy = side == 0 ? d0y : -d0y;
                    ArrayList<float[]> line = side == 0 ? fwd : back;
                    for (int s = 1; s <= maxSteps; s++) {if(((s)&63)==0)CancellationCheckpoint.check();
                        f.dir(x, y, false, dv);
                        if (dv[0] * dx + dv[1] * dy < 0) { dv[0] = -dv[0]; dv[1] = -dv[1]; }
                        float mx = x + dv[0] * step * .5f, my = y + dv[1] * step * .5f;
                        f.dir(mx, my, false, dv);
                        if (dv[0] * dx + dv[1] * dy < 0) { dv[0] = -dv[0]; dv[1] = -dv[1]; }
                        if (dv[0] * dx + dv[1] * dy < 0.42f) break;
                        float nx = x + dv[0] * step, ny = y + dv[1] * step;
                        if (nx < 0 || ny < 0 || nx >= src.widthMm || ny >= src.heightMm || !f.valid(nx, ny)) break;
                        float light = 1f - f.bil(f.dark, nx, ny), detail = f.bil(f.detail, nx, ny);
                        if (light < GOUGE_CUTOFF - 0.04f * detail) break;
                        float wv = gougeWidth(o, gMin, gMax, light), pv = gougePitch(o, wv, light, detail, land);
                        if (c.sd(nx, ny) < 0.6f * wv + inset) break;
                        int sq = side == 0 ? s : -s;
                        float own = Math.max(0.82f * pv, wv + land + step);
                        int guard = Math.max(3, (int) Math.ceil(2 * own / step) + 1);
                        if (occ.blockedBalanced(nx, ny, pv, 0.82f, 0.5f * wv, land + step * .5f, id, sq, guard)) break;
                        occ.add(nx, ny, id, sq, pv, 0.5f * wv); line.add(new float[]{nx, ny});
                        x = nx; y = ny; dx = dv[0]; dy = dv[1];
                    }
                }
                int m = back.size() + 1 + fwd.size();
                float total = (m - 1) * step;
                if (m < 2 || total < Math.max(1.8f, o.minLengthMm - 0.9f * detail0)) { occ.kill(id); continue; }
                float[] xy = new float[2 * m]; int k = 0;
                for (int i = back.size() - 1; i >= 0; i--) { xy[k++] = back.get(i)[0]; xy[k++] = back.get(i)[1]; }
                xy[k++] = x0; xy[k++] = y0; for (float[] q : fwd) { xy[k++] = q[0]; xy[k++] = q[1]; }
                float[] hw = new float[m];
                for (int i = 0; i < m; i++) {
                    float light = 1f - f.bil(f.dark, xy[2 * i], xy[2 * i + 1]);
                    float edge = Math.min(i * step, total - i * step);
                    float tp = o.taperMm <= 0 ? 1f : (float) Math.pow(StyleKit.clampF(edge / o.taperMm, 0, 1), 0.70);
                    // Carved waste narrows only to the cut minimum (and the weed gap), never to a slit.
                    hw[i] = 0.5f * Math.max(gMin, gougeWidth(o, gMin, gMax, light) * tp);
                }
                float[] poly = StyleKit.ribbon(xy, hw, 2.0f, true);
                if (poly == null || StyleKit.selfIntersections(poly, 1) > 0) { occ.kill(id); stats[1]++; continue; }
                // Verify the final outline: every vertex keeps a full ink land to the block outline.
                boolean inside = true;
                for (int q = 0; q + 1 < poly.length && inside; q += 2) if (c.sd(poly[q], poly[q + 1]) < land + 0.5f * c.g) inside = false;
                if (!inside) { occ.kill(id); stats[4]++; continue; }
                out.add(poly); stats[3]++;
            }
        }
        return out;
    }
}

package com.halftonestudio;

import java.util.*;

/**
 * Tone-driven Truchet tiles. ARCS chains the quarter-circle arcs of neighbouring tiles into long
 * continuous ribbons (no seams) whose width follows the photo's tone; DIAGONALS draws pointed
 * slash ribbons; TRIANGLES fills half/whole tiles and merges them into few cut shapes; MIXED uses
 * solid blocks in dark cells and arcs in mid tones.
 */
public final class TruchetTilesEngine {
    private TruchetTilesEngine() {}

    public enum Pattern { ARCS, DIAGONALS, TRIANGLES, MIXED }

    public static final class Options {
        public float widthMm = 152.4f;
        public float cellMm = 6f;               // 2..30
        public Pattern pattern = Pattern.ARCS;
        public float emptyBelow = 0.10f;        // darkness below this leaves the tile empty
        public float solidAbove = 0.72f;        // MIXED/TRIANGLES: darker cells become solid
        public float maxWidthFraction = 0.36f;  // widest ribbon as a fraction of the cell (<0.40 keeps arcs apart)
        public float minWidthMm = 0.35f;        // narrower than this cannot be cut: the ribbon is split there
        public float gapMm = 0f;                // TRIANGLES: clearance between merged shapes and neighbours
        public boolean invert = false;          // true = dark tiles of a light photo (use for light-on-dark vinyl)
        public int inkArgb = 0xff17181d, paperArgb = 0xfff5f1e8;
        public boolean autoLevels = true;
        public float gamma = 1f, contrast = 1f;
        public long seed = 5;
        public boolean[] allowed = null;
        public Options copy() { Options o = new Options(); o.widthMm = widthMm; o.cellMm = cellMm; o.pattern = pattern; o.emptyBelow = emptyBelow; o.solidAbove = solidAbove; o.maxWidthFraction = maxWidthFraction; o.minWidthMm = minWidthMm; o.gapMm = gapMm; o.invert = invert; o.inkArgb = inkArgb; o.paperArgb = paperArgb; o.autoLevels = autoLevels; o.gamma = gamma; o.contrast = contrast; o.seed = seed; o.allowed = allowed; return o; }
        void validate() {
            if (cellMm < 2f || cellMm > 30f) throw new IllegalArgumentException("cellMm must be 2..30");
            if (maxWidthFraction < 0.1f || maxWidthFraction > 0.4f) throw new IllegalArgumentException("maxWidthFraction must be 0.1..0.4");
            if (emptyBelow < 0 || emptyBelow >= solidAbove || solidAbove > 1) throw new IllegalArgumentException("Invalid tone thresholds");
        }
    }

    private static boolean flip(long seed, int i, int j) { long h = seed * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL + j * 0x165667B19E3779F9L; h ^= h >>> 31; h *= 0xD6E8FEB86659FD93L; h ^= h >>> 29; return (h & 1) == 1; }

    public static StyleKit.Result generate(int[] argb, int w, int h, Options o) {
        o.validate();
        StyleKit.Source src = new StyleKit.Source(argb, w, h, o.allowed, o.widthMm);
        if (o.autoLevels) src.autoLevels(.02f, .98f);
        src.tone(o.gamma, o.contrast);
        if (!src.anyValid()) throw new IllegalArgumentException("No usable pixels");
        float W = src.widthMm, H = src.heightMm, c = o.cellMm;
        int nx = Math.max(1, (int) (W / c)), ny = Math.max(1, (int) (H / c)); float ox = (W - nx * c) / 2, oy = (H - ny * c) / 2;
        float[] dark = new float[nx * ny]; boolean[] ok = new boolean[nx * ny];
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {if(((i)&63)==0)CancellationCheckpoint.check();
            float sum = 0; for (int q = 0; q < 9; q++) sum += src.darkAt(ox + (i + (q % 3 + .5f) / 3f) * c, oy + (j + (q / 3 + .5f) / 3f) * c);
            sum /= 9f; dark[j * nx + i] = o.invert ? 1f - sum : sum; ok[j * nx + i] = src.validAt(ox + (i + .5f) * c, oy + (j + .5f) * c);
        }
        StyleKit.Result res = new StyleKit.Result(W, H); res.paperArgb = o.paperArgb;
        StyleKit.Layer ink = new StyleKit.Layer("truchet", o.inkArgb); res.layers.add(ink);
        boolean[] solid = new boolean[nx * ny], arcCell = new boolean[nx * ny];
        for (int k = 0; k < nx * ny; k++) {if(((k)&63)==0)CancellationCheckpoint.check();
            if (!ok[k] || dark[k] < o.emptyBelow) continue;
            switch (o.pattern) {
                case ARCS: arcCell[k] = true; break;
                case DIAGONALS: arcCell[k] = true; break;
                case TRIANGLES: solid[k] = true; break;
                case MIXED: if (dark[k] >= o.solidAbove) solid[k] = true; else arcCell[k] = true; break;
            }
        }
        int pieces = 0;
        if (o.pattern == Pattern.ARCS || o.pattern == Pattern.MIXED) pieces += arcs(o, ink, nx, ny, ox, oy, c, dark, arcCell);
        if (o.pattern == Pattern.DIAGONALS) pieces += diagonals(o, ink, nx, ny, ox, oy, c, dark, arcCell);
        if (o.pattern == Pattern.TRIANGLES || o.pattern == Pattern.MIXED) pieces += solids(o, ink, nx, ny, ox, oy, c, dark, solid);
        res.summary = "Truchet " + o.pattern + ": " + (nx * ny) + " tiles, " + pieces + " cut shapes";
        return res;
    }

    private static float widthMm(Options o, float d, float c) {
        if (d < o.emptyBelow) return 0f;
        return o.minWidthMm + (o.maxWidthFraction * c - o.minWidthMm) * StyleKit.smoothstep(o.emptyBelow, 1f, d);
    }

    // ---------------------------------------------------------------- arcs (chained)
    private static int arcs(Options o, StyleKit.Layer ink, int nx, int ny, float ox, float oy, float c, float[] dark, boolean[] cellOn) {
        int hCount = nx * (ny + 1), nodes = hCount + (nx + 1) * ny;
        float[] nxp = new float[nodes], nyp = new float[nodes], nd = new float[nodes];
        for (int j = 0; j <= ny; j++) for (int i = 0; i < nx; i++) {if(((i)&63)==0)CancellationCheckpoint.check(); int id = j * nx + i; nxp[id] = ox + (i + .5f) * c; nyp[id] = oy + j * c; nd[id] = avg(dark, nx, ny, i, j - 1, i, j); }
        for (int j = 0; j < ny; j++) for (int i = 0; i <= nx; i++) {if(((i)&63)==0)CancellationCheckpoint.check(); int id = hCount + j * (nx + 1) + i; nxp[id] = ox + i * c; nyp[id] = oy + (j + .5f) * c; nd[id] = avg(dark, nx, ny, i - 1, j, i, j); }
        ArrayList<int[]> arcs = new ArrayList<>(); ArrayList<float[]> centers = new ArrayList<>();
        int[][] adj = new int[nodes][2]; int[] deg = new int[nodes];
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {if(((i)&63)==0)CancellationCheckpoint.check();
            if (!cellOn[j * nx + i]) continue;
            int top = j * nx + i, bottom = (j + 1) * nx + i, left = hCount + j * (nx + 1) + i, right = hCount + j * (nx + 1) + i + 1;
            float x0 = ox + i * c, y0 = oy + j * c, x1 = x0 + c, y1 = y0 + c;
            int[][] pair; float[][] cen;
            if (flip(o.seed, i, j)) { pair = new int[][]{{top, left}, {bottom, right}}; cen = new float[][]{{x0, y0}, {x1, y1}}; }
            else { pair = new int[][]{{top, right}, {bottom, left}}; cen = new float[][]{{x1, y0}, {x0, y1}}; }
            for (int q = 0; q < 2; q++) {if(((q)&63)==0)CancellationCheckpoint.check(); int id = arcs.size(); arcs.add(pair[q]); centers.add(cen[q]); for (int e : pair[q]) {CancellationCheckpoint.check(); if (deg[e] >= 2) throw new IllegalStateException("Truchet node degree > 2"); adj[e][deg[e]++] = id; } }
        }
        boolean[] used = new boolean[arcs.size()]; int shapes = 0;
        // open chains first (start at degree-1 nodes), then cycles
        for (int pass = 0; pass < 2; pass++) for (int nd0 = 0; nd0 < nodes; nd0++) {if(((nd0)&63)==0)CancellationCheckpoint.check();
            if (deg[nd0] == 0) continue; if (pass == 0 && deg[nd0] != 1) continue;
            int firstArc = -1; for (int q = 0; q < deg[nd0]; q++) if (!used[adj[nd0][q]]) { firstArc = adj[nd0][q]; break; } if (firstArc < 0) continue;
            ArrayList<float[]> pts = new ArrayList<>(); int node = nd0, arc = firstArc; boolean closed = false;
            while (arc >= 0 && !used[arc]) {
                used[arc] = true; int[] ab = arcs.get(arc); int other = ab[0] == node ? ab[1] : ab[0]; float[] cen = centers.get(arc);
                double a0 = Math.atan2(nyp[node] - cen[1], nxp[node] - cen[0]), a1 = Math.atan2(nyp[other] - cen[1], nxp[other] - cen[0]), da = a1 - a0;
                while (da > Math.PI) da -= 2 * Math.PI; while (da < -Math.PI) da += 2 * Math.PI;
                int seg = 10;
                for (int s = (pts.isEmpty() ? 0 : 1); s <= seg; s++) {if(((s)&63)==0)CancellationCheckpoint.check(); double t = s / (double) seg, a = a0 + da * t; float wmm = lerp(widthMm(o, nd[node], c), widthMm(o, nd[other], c), (float) t); pts.add(new float[]{(float) (cen[0] + c / 2 * Math.cos(a)), (float) (cen[1] + c / 2 * Math.sin(a)), wmm / 2}); }
                node = other; int next = -1; for (int q = 0; q < deg[node]; q++) if (!used[adj[node][q]]) next = adj[node][q];
                if (next < 0 && node == nd0 && deg[nd0] == 2) closed = true; arc = next;
            }
            shapes += emit(ink, pts, closed, o.minWidthMm / 2);
        }
        return shapes;
    }
    private static float avg(float[] d, int nx, int ny, int i0, int j0, int i1, int j1) { float s = 0; int n = 0; if (i0 >= 0 && j0 >= 0 && i0 < nx && j0 < ny) { s += d[j0 * nx + i0]; n++; } if (i1 >= 0 && j1 >= 0 && i1 < nx && j1 < ny) { s += d[j1 * nx + i1]; n++; } return n == 0 ? 0 : s / n; }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /** Splits a chain where it becomes too thin to cut, then emits one ribbon per surviving run. */
    private static int emit(StyleKit.Layer ink, ArrayList<float[]> pts, boolean closed, float minHalf) {
        int n = pts.size(); if (n < 3) return 0; int count = 0;
        boolean allOk = true; for (float[] p : pts) if (p[2] < minHalf) { allOk = false; break; }
        if (closed && allOk) {
            float[] xy = new float[2 * (n - 1)], hw = new float[n - 1]; for (int i = 0; i < n - 1; i++) {if(((i)&63)==0)CancellationCheckpoint.check(); xy[2 * i] = pts.get(i)[0]; xy[2 * i + 1] = pts.get(i)[1]; hw[i] = pts.get(i)[2]; }
            ArrayList<float[]> two = StyleKit.ribbonClosed(xy, hw, 2f); if (two != null) { ink.loops.addAll(two); return 2; } return 0;
        }
        int start = -1;
        for (int i = 0; i <= n; i++) {if(((i)&63)==0)CancellationCheckpoint.check();
            boolean good = i < n && pts.get(i)[2] >= minHalf;
            if (good && start < 0) start = i;
            if (!good && start >= 0) {
                int m = i - start; if (m >= 3) { float[] xy = new float[2 * m], hw = new float[m]; for (int q = 0; q < m; q++) {if(((q)&63)==0)CancellationCheckpoint.check(); xy[2 * q] = pts.get(start + q)[0]; xy[2 * q + 1] = pts.get(start + q)[1]; hw[q] = pts.get(start + q)[2]; } float[] poly = StyleKit.ribbon(xy, hw, 2f, false); if (poly != null) { ink.loops.add(poly); count++; } }
                start = -1;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- diagonals (pointed slashes)
    private static int diagonals(Options o, StyleKit.Layer ink, int nx, int ny, float ox, float oy, float c, float[] dark, boolean[] on) {
        int count = 0;
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {if(((i)&63)==0)CancellationCheckpoint.check();
            if (!on[j * nx + i]) continue; float wmm = widthMm(o, dark[j * nx + i], c); if (wmm < o.minWidthMm) continue;
            float x0 = ox + i * c, y0 = oy + j * c; boolean f = flip(o.seed, i, j);
            float ax = x0, ay = f ? y0 : y0 + c, bx = x0 + c, by = f ? y0 + c : y0; // A -> B corner to corner
            double dx = bx - ax, dy = by - ay, l = Math.hypot(dx, dy), nx2 = -dy / l * wmm / 2, ny2 = dx / l * wmm / 2; float t = 0.22f;
            float[] poly = {ax, ay, (float) (ax + dx * t + nx2), (float) (ay + dy * t + ny2), (float) (ax + dx * (1 - t) + nx2), (float) (ay + dy * (1 - t) + ny2), bx, by, (float) (ax + dx * (1 - t) - nx2), (float) (ay + dy * (1 - t) - ny2), (float) (ax + dx * t - nx2), (float) (ay + dy * t - ny2)};
            if (StyleKit.signedArea(poly) < 0) poly = StyleKit.reversed(poly); ink.loops.add(poly); count++;
        }
        return count;
    }

    // ---------------------------------------------------------------- solid triangles / squares, merged
    private static int solids(Options o, StyleKit.Layer ink, int nx, int ny, float ox, float oy, float c, float[] dark, boolean[] on) {
        int gw = nx + 1; float[] pts = new float[2 * gw * (ny + 1)];
        for (int j = 0; j <= ny; j++) for (int i = 0; i <= nx; i++) {if(((i)&63)==0)CancellationCheckpoint.check(); pts[2 * (j * gw + i)] = ox + i * c; pts[2 * (j * gw + i) + 1] = oy + j * c; }
        ArrayList<Integer> tri = new ArrayList<>();
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {if(((i)&63)==0)CancellationCheckpoint.check();
            if (!on[j * nx + i]) continue; float d = dark[j * nx + i];
            int a = j * gw + i, b = a + 1, cc = a + gw + 1, dd = a + gw; // a=TL b=TR c=BR d=BL, CCW in math axes: a,b,c,d
            boolean whole = d >= o.solidAbove || o.pattern == Pattern.MIXED, f = flip(o.seed, i, j);
            if (whole) { addTri(tri, a, b, cc, pts); addTri(tri, a, cc, dd, pts); }
            else if (f) { if (flip(o.seed + 1, i, j)) addTri(tri, a, b, dd, pts); else addTri(tri, b, cc, dd, pts); }
            else { if (flip(o.seed + 1, i, j)) addTri(tri, a, b, cc, pts); else addTri(tri, a, cc, dd, pts); }
        }
        if (tri.isEmpty()) return 0;
        int[] t = new int[tri.size()]; for (int i = 0; i < t.length; i++) t[i] = tri.get(i); int[] subset = new int[t.length / 3]; for (int i = 0; i < subset.length; i++) subset[i] = i;
        int count = 0;
        for (float[] loop : StyleKit.mergeTriangles(pts, t, subset)) {CancellationCheckpoint.check();
            float[] use = loop;
            if (o.gapMm > 0) { float[] off = StyleKit.offsetLeft(loop, o.gapMm / 2, 2.5f); if (off == null || StyleKit.selfIntersections(off, 1) > 0) continue; use = off; }
            ink.loops.add(use); count++;
        }
        return count;
    }
    private static void addTri(ArrayList<Integer> tri, int a, int b, int c, float[] p) {
        double cr = (p[2 * b] - p[2 * a]) * (double) (p[2 * c + 1] - p[2 * a + 1]) - (p[2 * b + 1] - p[2 * a + 1]) * (double) (p[2 * c] - p[2 * a]);
        if (cr < 0) { int t = b; b = c; c = t; } tri.add(a); tri.add(b); tri.add(c);
    }
}

package com.halftonestudio;

import java.util.*;

/**
 * Low-poly portrait: edge-aware point cloud -> Delaunay triangulation -> flat-colour facets,
 * grouped into 2..12 vinyl colours. Same-colour neighbours can be merged into single cut shapes
 * (fewer cuts, no visible seams), and a vinyl gap is applied to every shape.
 */
public final class LowPolyDelaunayEngine {
    private LowPolyDelaunayEngine() {}

    public static final class Options {
        public float widthMm = 152.4f;
        public int layerCount = 10;           // 2..12 vinyl colours (more colours = more visible facets)
        public int[] palette = null;          // optional fixed vinyl colours (nearest match); overrides layerCount
        public float minSpacingMm = 3.5f;     // facet size at strong edges
        public float maxSpacingMm = 10.0f;    // facet size in flat areas
        public float edgeBias = 0.7f;         // 0.3 = even mesh .. 2 = strongly follows edges
        public int maxPoints = 2500;
        public float gapMm = 0.25f;           // clearance between neighbouring different-colour shapes
        public boolean mergeSameColor = false;// true = union adjacent same-colour facets: far fewer cuts but a posterised look
        public float gamma = 1f, contrast = 1f;
        public long seed = 1;
        public int paperArgb = 0xffffffff;
        public boolean[] allowed = null;      // optional mask (snip / subject)
        public Options copy() { Options o = new Options(); o.widthMm = widthMm; o.layerCount = layerCount; o.palette = palette; o.minSpacingMm = minSpacingMm; o.maxSpacingMm = maxSpacingMm; o.edgeBias = edgeBias; o.maxPoints = maxPoints; o.gapMm = gapMm; o.mergeSameColor = mergeSameColor; o.gamma = gamma; o.contrast = contrast; o.seed = seed; o.paperArgb = paperArgb; o.allowed = allowed; return o; }
        void validate() {
            if (layerCount < 2 || layerCount > 12) throw new IllegalArgumentException("layerCount must be 2..12");
            if (minSpacingMm < 1f || maxSpacingMm < minSpacingMm) throw new IllegalArgumentException("Invalid spacing");
            if (maxPoints < 16 || maxPoints > 6000) throw new IllegalArgumentException("maxPoints must be 16..6000");
            if (!Float.isFinite(gapMm) || gapMm < 0 || gapMm > 20.1f) throw new IllegalArgumentException("gapMm must be finite and 0..20.1 mm");
        }
    }

    public static StyleKit.Result generate(int[] argb, int w, int h, Options o) {
        o.validate();
        StyleKit.Source src = new StyleKit.Source(argb, w, h, o.allowed, o.widthMm).tone(o.gamma, o.contrast);
        if (!src.anyValid()) throw new IllegalArgumentException("No usable pixels");
        float W = src.widthMm, H = src.heightMm;
        // 1) points: border ring + corners + edge-aware interior
        float margin = o.minSpacingMm * 0.45f;
        float[] inner = StyleKit.sampleSites(src, o.minSpacingMm, o.maxSpacingMm, o.edgeBias, o.maxPoints, margin, o.seed);
        ArrayList<float[]> border = new ArrayList<>();
        float step = (o.minSpacingMm + o.maxSpacingMm) * 0.5f;
        int nx = Math.max(1, Math.round(W / step)), ny = Math.max(1, Math.round(H / step));
        for (int i = 0; i < nx; i++) {if(((i)&63)==0)CancellationCheckpoint.check(); border.add(new float[]{W * i / nx, 0}); border.add(new float[]{W - W * i / nx, H}); }
        for (int i = 0; i < ny; i++) {if(((i)&63)==0)CancellationCheckpoint.check(); border.add(new float[]{W, H * i / ny}); border.add(new float[]{0, H - H * i / ny}); }
        int n = inner.length / 2 + border.size();
        float[] pts = new float[2 * n];
        System.arraycopy(inner, 0, pts, 0, inner.length);
        for (int i = 0; i < border.size(); i++) {if(((i)&63)==0)CancellationCheckpoint.check(); pts[inner.length + 2 * i] = border.get(i)[0]; pts[inner.length + 2 * i + 1] = border.get(i)[1]; }
        int[] tri = StyleKit.delaunay(pts, n);
        int T = tri.length / 3;
        // 2) facet colour = mean of 4 samples; drop facets outside the mask
        int[] tcol = new int[T]; boolean[] keep = new boolean[T]; float[] tarea = new float[T];
        for (int t = 0; t < T; t++) {if(((t)&63)==0)CancellationCheckpoint.check();
            float ax = pts[2 * tri[3 * t]], ay = pts[2 * tri[3 * t] + 1], bx = pts[2 * tri[3 * t + 1]], by = pts[2 * tri[3 * t + 1] + 1], cx = pts[2 * tri[3 * t + 2]], cy = pts[2 * tri[3 * t + 2] + 1];
            float gx = (ax + bx + cx) / 3, gy = (ay + by + cy) / 3;
            tarea[t] = Math.abs((bx - ax) * (cy - ay) - (by - ay) * (cx - ax)) / 2;
            keep[t] = tarea[t] > 0.02f && src.validAt(gx, gy);
            float[][] sp = {{gx, gy}, {(gx + ax) / 2, (gy + ay) / 2}, {(gx + bx) / 2, (gy + by) / 2}, {(gx + cx) / 2, (gy + cy) / 2}};
            int r = 0, g = 0, b = 0;
            for (float[] q : sp) {CancellationCheckpoint.check(); int c = src.colorAt(q[0], q[1]); r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255; }
            tcol[t] = 0xff000000 | ((r / 4) << 16) | ((g / 4) << 8) | (b / 4);
        }
        // 3) palette
        int[] palette;
        if (o.palette != null && o.palette.length > 0) palette = o.palette.clone();
        else {
            ArrayList<Integer> cs = new ArrayList<>(); ArrayList<Float> ws = new ArrayList<>();
            for (int t = 0; t < T; t++) if (keep[t]) { cs.add(tcol[t]); ws.add(tarea[t]); }
            if (cs.isEmpty()) throw new IllegalArgumentException("No facets inside the mask");
            int[] ca = new int[cs.size()]; float[] wa = new float[cs.size()];
            for (int i = 0; i < ca.length; i++) {if(((i)&63)==0)CancellationCheckpoint.check(); ca[i] = cs.get(i); wa[i] = ws.get(i); }
            palette = StyleKit.kmeans(ca, wa, o.layerCount, o.seed);
        }
        PhotoSwatchAssociation association=new PhotoSwatchAssociation(
            palette,palette.length,null,src,PhotoSwatchAssociation.FACETS);
        int[] layerOf = new int[T]; ArrayList<ArrayList<Integer>> groups = new ArrayList<>();
        for (int i = 0; i < palette.length; i++) groups.add(new ArrayList<>());
        for (int t = 0; t < T; t++) if (keep[t]) { layerOf[t] = association.choose(tcol[t]); groups.get(layerOf[t]).add(t); }
        // 4) geometry per layer
        StyleKit.Result res = new StyleKit.Result(W, H); res.paperArgb = o.paperArgb;
        int shapes = 0;
        for (int li = 0; li < palette.length; li++) {if(((li)&63)==0)CancellationCheckpoint.check();
            ArrayList<Integer> g = groups.get(li); if (g.isEmpty()) continue;
            StyleKit.Layer layer = new StyleKit.Layer("lowpoly-" + (li + 1), palette[li]); res.layers.add(layer);
            if (o.mergeSameColor) {
                int[] subset = new int[g.size()]; for (int i = 0; i < subset.length; i++) subset[i] = g.get(i);
                for (float[] loop : StyleKit.mergeTriangles(pts, tri, subset)) {CancellationCheckpoint.check();
                    float[] use = loop;
                    if (o.gapMm > 0) {
                        float[] off = StyleKit.offsetLeft(loop, o.gapMm / 2, 2.5f);
                        if (off != null && StyleKit.selfIntersections(off, 1) == 0) use = off; else if (Math.abs(StyleKit.signedArea(loop)) < o.gapMm * o.gapMm * 4) continue;
                    }
                    layer.loops.add(use); shapes++;
                }
            } else {
                for (int t : g) {CancellationCheckpoint.check();
                    float[] p = {pts[2 * tri[3 * t]], pts[2 * tri[3 * t] + 1], pts[2 * tri[3 * t + 1]], pts[2 * tri[3 * t + 1] + 1], pts[2 * tri[3 * t + 2]], pts[2 * tri[3 * t + 2] + 1]};
                    float[] use = o.gapMm > 0 ? StyleKit.insetConvex(p, o.gapMm / 2) : p;
                    if (use != null) { layer.loops.add(use); shapes++; }
                }
            }
        }
        res.summary = "Low-poly: " + T + " facets, " + shapes + " cut shapes, " + res.layers.size() + " colours";
        return res;
    }
}

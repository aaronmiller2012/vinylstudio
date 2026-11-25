package com.halftonestudio;

import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Typeface;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Real-font glyph outlines for TextHalftoneEngine using Android's text paths. Bold, blocky or stencil
 * typefaces cut best. Contours are flattened to polylines; holes (O, A, B...) are separate contours
 * and are filled even-odd by the engine. NOTE: this is the only file that needs the Android SDK.
 */
public final class AndroidGlyphSource implements TextHalftoneEngine.GlyphSource {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final HashMap<Integer, ArrayList<float[]>> cache = new HashMap<>();
    private final HashMap<Integer, Float> aspects = new HashMap<>();
    private static final float EM = 200f;        // layout size used while measuring
    private final float flattenStep;             // distance between sampled points along curves, in em/200 units

    public AndroidGlyphSource(Typeface typeface) { this(typeface, 3f); }
    public AndroidGlyphSource(Typeface typeface, float flattenStep) {
        paint.setTypeface(typeface == null ? Typeface.DEFAULT_BOLD : typeface); paint.setTextSize(EM); this.flattenStep = Math.max(1f, flattenStep);
    }

    /** Unit box: cap height maps to 1 (y down), width = advance / cap height. */
    @Override public ArrayList<float[]> outline(int cp) {
        ArrayList<float[]> hit = cache.get(cp); if (hit != null) return hit;
        String s = new String(Character.toChars(cp)); Path path = new Path(); paint.getTextPath(s, 0, s.length(), 0, 0, path);
        android.graphics.Rect cap = new android.graphics.Rect(); paint.getTextBounds("H", 0, 1, cap);
        float capH = Math.max(1f, cap.height()), advance = paint.measureText(s);
        ArrayList<float[]> loops = new ArrayList<>(); PathMeasure pm = new PathMeasure(path, false); float[] pos = new float[2];
        do {
            float len = pm.getLength(); if (len <= 0.5f) continue;
            int n = Math.max(8, (int) Math.ceil(len / flattenStep)); float[] l = new float[2 * n];
            for (int i = 0; i < n; i++) { pm.getPosTan(len * i / n, pos, null); l[2 * i] = (pos[0]) / capH; l[2 * i + 1] = (pos[1] + capH) / capH; } // baseline y=0 -> bottom of box = 1
            loops.add(l);
        } while (pm.nextContour());
        cache.put(cp, loops); aspects.put(cp, advance / capH); return loops;
    }
    @Override public float aspect(int cp) { if (!aspects.containsKey(cp)) outline(cp); Float a = aspects.get(cp); return a == null ? 0.7f : a; }
}

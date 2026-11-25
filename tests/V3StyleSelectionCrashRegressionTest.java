package com.halftonestudio;

/** Regression for UI-thread crash on first-time selection of appended styles. */
public final class V3StyleSelectionCrashRegressionTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        int tested=0;
        // The exact selection path in StudioActivity calls forNewStyle before rendering.
        for (int id=0; id<VinylStyleSuite.NAMES.length; id++) {
            if (!NewStyleVariantPolicy.selectable(id)) continue;
            for (float width : new float[]{50.8f, 101.6f, 279.4f}) {
                PhotoStyleDefaults.Initial value=PhotoStyleDefaults.forNewStyle(id,null,.8f,width);
                check(value.detail>=25 && value.detail<=97,
                      "invalid detail for style " + id);
                check(Float.isFinite(value.weightMm) && value.weightMm>0,
                      "invalid weight for style " + id);
                check(Float.isFinite(value.leadMm) && value.leadMm>0,
                      "invalid lead for style " + id);
                tested++;
            }
        }
        for (int id : new int[]{VinylStyleSuite.POSTER_STENCIL}) {
            check(NewStyleVariantPolicy.selectable(id), "new style must be selectable");
            check(PhotoStyleDefaults.forNewStyle(id,null,.8f,254f).detail>=60,
                  "insufficient default portrait detail");
        }
        check(!NewStyleVariantPolicy.selectable(VinylStyleSuite.POP_PANELS), "removed Pop Panels still selectable");
        System.out.println("PASS first-time new-style UI defaults for " + tested + " scenarios");
    }
}

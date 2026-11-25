package com.halftonestudio;

/** Image-aware initial settings, applied once when choosing a style, not at every redraw.
 * All units returned for cut geometry are millimeters. This never changes colors,
 * masks, registration positions, layer count, or the selected material's minimum.
 */
final class PhotoStyleDefaults {
    static final class Initial {
        final int detail;
        final float weightMm,leadMm;
        Initial(int d,float weight,float lead){detail=d;weightMm=weight;leadMm=lead;}
    }
    private PhotoStyleDefaults(){}
    private static float clamp(float n,float low,float high){return Math.max(low,Math.min(high,n));}
    static Initial forNewStyle(int style,AutoPhotoTuner.Stats stats,float minCutMm,float artWidthMm){
        if(style<0||style>=VinylStyleSuite.NAMES.length)throw new IllegalArgumentException("Style ID");
        final float min=Math.max(VinylEngine.MINIMUM_CUT_WIDTH_MM,
            Float.isFinite(minCutMm)?minCutMm:VinylEngine.MINIMUM_CUT_WIDTH_MM);
        final float width=Math.max(25f,Float.isFinite(artWidthMm)?artWidthMm:152.4f);
        final float complexity=stats==null?.40f:clamp(.56f*stats.edge+.44f*stats.contrast,0f,1f);
        final int[] base={69,77,58,78,65,76,68,69,0,75,88,
            75,80,0,78,72,0,0,0,0,0,76,0,0,70,0,70,82,68,0,
            82,82,84,77,83,76,83,72,78,73,76,74,78,87,89,86,85,87,
            70,70,70,70,70};
        final int[] boosts={16,18,12,18,14,16,12,18,0,18,12,
            16,12,0,16,14,0,0,0,0,0,14,0,0,14,0,14,18,13,0,
            18,18,20,16,19,17,18,16,14,18,16,15,17,12,12,12,14,15,
            14,14,14,14,14};
        int detail=Math.round(base[style]+(complexity-.40f)*boosts[style]);
        // Small output at a large physical minimum needs fewer cells and simpler cuts.
        float budget=width/min;
        if(budget<120f)detail=Math.min(detail,55);
        else if(budget<190f)detail=Math.min(detail,68);
        // Camouflage requires denser cells to preserve faces at 11 in. These
        // are one-time selection defaults; slider edits and saved values win.
        if(CamouflagePalette.isCamouflage(style) && budget>=190f){
            int floor=style==VinylStyleSuite.CAMO_DIGITAL?93:
                style==VinylStyleSuite.CAMO_URBAN?91:
                style==VinylStyleSuite.CAMO_DESERT?89:92;
            detail=Math.max(detail,Math.round(floor+(complexity-.40f)*8f));
        }
        // RC168: actual knotted mesh/filet stitch rows can hold more photographic
        // structure at 11 inches. This is applied only at initial style selection.
        if(budget>=190f && (style==VinylStyleSuite.FISHNET||style==VinylStyleSuite.CROCHET)){
            int minimum=style==VinylStyleSuite.FISHNET?84:82;
            detail=Math.max(detail,Math.round(minimum+(complexity-.40f)*7f));
        }
        detail=(int)clamp(detail,25,CamouflagePalette.isCamouflage(style)?97:90);
        float weight=VinylStyleSuite.isOrganicFlow(style)?1f:
            Math.max(min,clamp(.70f+.13f*complexity,.62f,1.1f));
        float lead=Math.max(.6f,Math.max(min*1.15f,1.05f));
        return new Initial(detail,weight,lead);
    }
}

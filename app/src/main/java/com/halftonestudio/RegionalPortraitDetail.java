package com.halftonestudio;

/** Deterministic, physical-scale detail budget. It does not detect or identify faces.
 * It allocates more tonal fidelity to textured central subject regions while
 * preserving the background, transparencies, and color-layer ownership.
 */
public final class RegionalPortraitDetail {
    private RegionalPortraitDetail() {}
    private static float clamp(float a){return Math.max(0f,Math.min(1f,a));}
    public static float priority(float xMm,float yMm,float widthMm,float heightMm,
                                 float edge,float coverage,int alpha){
        if(!(widthMm>0&&heightMm>0)||alpha<128||coverage<=.0001f)return 0f;
        float x=(xMm/widthMm-.5f)/.40f,y=(yMm/heightMm-.43f)/.46f;
        float ellipse=clamp(1f-x*x-y*y);
        // No blanket filling of white background; texture is needed to amplify.
        float detail=clamp((edge-.035f)/.22f);
        return clamp(ellipse*detail*clamp(coverage*3f));
    }
    /** Bounded increase only. Never create a nonzero color channel from blank paper. */
    public static float improve(float coverage,float priority,float strength){
        if(coverage<=.0001f)return coverage;
        float boost=.22f*clamp(priority)*clamp(strength);
        return clamp(coverage+boost*(float)Math.sqrt(coverage)*(1f-coverage));
    }
}

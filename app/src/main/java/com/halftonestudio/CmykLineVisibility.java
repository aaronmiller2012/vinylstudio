package com.halftonestudio;

/** Shared CMYK highlight response for both legacy and modern line engines.
 * A zero strength is bit-for-bit inactive; blank channel, alpha exclusions and
 * intentional broken/fade geometry never acquire artificial cut paths.
 */
public final class CmykLineVisibility {
    private CmykLineVisibility() {}
    public static float apply(float coverage,float strength,float regionalPriority){
        if(strength<=.0001f || coverage<=.0001f)return coverage;
        float c=Math.min(1f,Math.max(0f,coverage));
        float s=Math.min(1f,Math.max(0f,strength));
        // Lift midtone/weak photographic evidence but not zero coverage.
        float lifted=c+s*.45f*((float)Math.sqrt(c)-c);
        return RegionalPortraitDetail.improve(lifted,regionalPriority,s);
    }
}

package com.halftonestudio;

/**
 * Restrained CMYK-on-white coverage shaping for opaque vinyl.
 *
 * This does NOT simulate subtractive ink mixing. It reduces excessive overlapping
 * ribbons in chromatic portrait midtones while preserving deep neutral features
 * and the original cyan / magenta / yellow source information. No new cuts are
 * introduced where a channel has zero coverage. The separation remains an
 * approximation and must be evaluated using the finished opaque stack.
 */
public final class CmykWhitePaperBalance {
    private CmykWhitePaperBalance() {}
    private static float clamp(float v){return Math.max(0f,Math.min(1f,v));}

    /** Channel identity: cyan 0, magenta 1, yellow 2, black 3. */
    public static float adjust(float coverage,int rgb,int channel,int paper){
        // Colored mounting surfaces require separate stack rules. Do not apply
        // white-paper calibration to black or saturated backgrounds.
        int pr=(paper>>16)&255,pg=(paper>>8)&255,pb=paper&255;
        if(pr<242||pg<242||pb<242||channel<0||channel>3)return clamp(coverage);
        if(coverage<=0f)return 0f;
        float r=((rgb>>16)&255)/255f,g=((rgb>>8)&255)/255f,b=(rgb&255)/255f;
        float luma=.2126f*r+.7152f*g+.0722f*b;
        float chroma=Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b));
        // Soft plateau: excludes dark eyes/beard and paper highlights, where
        // detail and negative-space behavior must remain untouched.
        float mid=clamp((luma-.22f)/.29f)*clamp((.91f-luma)/.26f);
        float factor=1f;
        if(channel==2){
            // Yellow vinyl is highly visible on white, even though printing ink
            // is not. Avoid broad solid-yellow bands over skin midtones.
            factor=1f-.29f*mid;
        }else if(channel==3){
            // Prevent black from swallowing facial chroma while retaining neutral
            // gray and dark-hair blacks. Other channel geometry is unchanged.
            float chromatic=clamp((chroma-.07f)/.31f);
            factor=1f-.50f*mid*chromatic;
        }else if(channel==1){
            // Magenta remains the primary warm-shading color on white vinyl.
            factor=1f-.07f*mid;
        }else if(channel==0){
            // Preserve a small cyan contribution for cool shadows and warm-hair
            // information recovered by shadowColorRecovery.
            float warm=clamp((r-b-.05f)/.40f);
            factor=1f-.07f*mid*warm;
        }
        return clamp(coverage*factor);
    }
}

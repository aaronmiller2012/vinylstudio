package com.halftonestudio;

/** Physical feasibility of an interleaved detail ribbon screen (millimeters).
 * All geometry depends on the cutting floor and the required weed gap.
 * When a second lane cannot fit, the engine enhances the original ribbon.
 * This class has no Android dependencies and is exercised by host tests.
 */
public final class LineRibbonPolicy {
    private LineRibbonPolicy() {}
    public static final class Plan {
        public final boolean secondLane;
        public final float primaryHalfLimit, detailHalfLimit;
        private Plan(boolean second, float primary, float detail){
            secondLane=second; primaryHalfLimit=primary; detailHalfLimit=detail;
        }
    }
    public static Plan plan(float pitchMm, float minimumMm, float gapMm,
                            float unrestrictedMainHalf, float requestedDetailStrength) {
        if(!Float.isFinite(pitchMm)||!Float.isFinite(minimumMm)||!Float.isFinite(gapMm)
            ||!Float.isFinite(unrestrictedMainHalf)||!Float.isFinite(requestedDetailStrength)
            ||pitchMm<=0||minimumMm<=0||gapMm<0||unrestrictedMainHalf<0)
            throw new IllegalArgumentException("Invalid line screen geometry");
        if(requestedDetailStrength<=0) return new Plan(false,unrestrictedMainHalf,0);
        final float centerDistance=pitchMm*.5f;
        // Main ribbon and detail ribbon must each reach minimum physical width,
        // with an actual weedable gap in the half-pitch separating them.
        if(centerDistance+1e-5f < minimumMm+gapMm || unrestrictedMainHalf*2f<minimumMm)
            return new Plan(false,unrestrictedMainHalf,0);
        float primary=Math.min(unrestrictedMainHalf,centerDistance-gapMm-minimumMm*.5f);
        if(primary*2f+1e-5f<minimumMm)
            return new Plan(false,unrestrictedMainHalf,0);
        // The full detail lane cannot steal the reserved weed gap. More detail
        // affects tonal activation, not the physical minimum viable width.
        float secondary=Math.min(centerDistance-gapMm-primary, pitchMm*.25f);
        if(secondary*2f+1e-5f<minimumMm)
            return new Plan(false,unrestrictedMainHalf,0);
        return new Plan(true,primary,secondary);
    }
}

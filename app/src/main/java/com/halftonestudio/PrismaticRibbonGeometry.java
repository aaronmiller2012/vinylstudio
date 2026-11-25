package com.halftonestudio;

/** Geometry-only helpers for the experimental sibling engines. Units are lane pitches. */
final class PrismaticRibbonGeometry {
    enum Pattern { CONTOUR_FLOW, TWILL, SILK, BRAIDED_FLOW, CHEVRON }
    /** Twill stagger cap, in lane pitches. */
    static final float MAX_OFFSET = .12f;
    /** Braided pair sway and contour steering caps. Larger than the kit's .12 so the
     * pattern is visible at default strength; the engine measures the resulting lane
     * approach and slope and narrows ribbons (or widens the pitch) accordingly. */
    static final float BRAID_OFFSET = .16f, CONTOUR_OFFSET = .26f;
    private static final double TAU = Math.PI * 2;
    private PrismaticRibbonGeometry() {}
    static float clamp(float x, float lo, float hi) { return Math.max(lo, Math.min(hi,x)); }
    static float smooth(float t) { return t*t*(3-2*t); }
    static float wave(Pattern mode, float along, float amp, float freq, float phase, boolean cross) {
        double a=along*freq+phase;
        switch(mode) {
            case TWILL: return amp*.38f*(float)Math.sin(a);
            case SILK: return amp*(cross?.35f:1.10f)*(float)Math.sin(a*.62);
            case BRAIDED_FLOW: return amp*.60f*(float)Math.sin(a);
            case CHEVRON:
                // Smooth triangular wave: finite curvature at the turns.
                return amp*1.55f*(float)(Math.asin(.91*Math.sin(a))/Math.asin(.91));
            case CONTOUR_FLOW: default: return amp*.32f*(float)Math.sin(a);
        }
    }
    static float decorativeOffset(Pattern mode, int lane, float along, float strength, boolean cross) {
        double a=TAU*along/5.0;
        if(mode==Pattern.BRAIDED_FLOW) return BRAID_OFFSET*strength*(float)Math.sin(a+(lane&1)*Math.PI+(cross?1.1:0));
        if(mode==Pattern.TWILL) return MAX_OFFSET*.55f*strength*(float)Math.sin(a+lane*TAU/4.0);
        return 0;
    }
    /** Local gradient tensor -> signed side/along slope of its dominant edge tangent.
     * Only confident, near-forward tangents can steer the field. */
    static float tangentSlope(float xx,float xy,float yy,boolean cross) {
        double trace=xx+yy;
        if(trace<1e-7) return 0;
        double confidence=Math.hypot(xx-yy,2*xy)/trace;
        double theta=.5*Math.atan2(2*xy,xx-yy)+Math.PI/2;
        double tx=Math.cos(theta),ty=Math.sin(theta);
        double u=(tx+ty)*.7071067811865476,v=(ty-tx)*.7071067811865476;
        double along=cross?v:u,side=cross?u:v;
        if(Math.abs(along)<.55) return 0;
        return clamp((float)(side/along*confidence),-.65f,.65f);
    }
    static float interpolate(float[] field,int row,int steps,float local) {
        if(local<=0)return field[row];
        if(local>=steps-1)return field[row+steps-1];
        int k=(int)Math.floor(local);float t=smooth(local-k);
        return field[row+k]+(field[row+k+1]-field[row+k])*t;
    }
    /** Soft aesthetic prior; actual perceptual error remains the primary cost.
     * Twill is a staggered coverage motif, not physically alternating layer order. */
    static float prior(Pattern mode,boolean cross,int lane,int step,float wa,float wb,float strength) {
        // Silk: one dominant flowing family over fine cross threads.
        if(mode==Pattern.SILK) return strength*420f*wb*wb;
        if(mode==Pattern.TWILL) {
            float own=cross?wb:wa, other=cross?wa:wb;
            boolean preferOwn=Math.floorMod(lane+(cross?-step:step),4)<2;
            float mismatch=preferOwn?Math.max(0,other-own):Math.max(0,own-other);
            return strength*320f*mismatch*mismatch;
        }
        return 0;
    }
}

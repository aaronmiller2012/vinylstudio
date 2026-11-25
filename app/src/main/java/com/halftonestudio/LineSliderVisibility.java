package com.halftonestudio;

/**
 * M5: which line Adjust sliders have a measurable effect, so the editor shows only those.
 * Derived from M5LineSliderEffectTest (211 slider/style/route cases on real engine output);
 * that test fails if a slider hidden here starts working or a visible one stops working.
 * No Android APIs.
 */
public final class LineSliderVisibility {
    private LineSliderVisibility(){}

    /** lineSmoothing / contourStrength only bend the Variable-width, Contour and Hatch centrelines. */
    private static boolean bendsCentrelines(int pattern){
        return pattern==VinylEngine.LINE||pattern==VinylEngine.CONTOUR||pattern==VinylEngine.HATCH;
    }
    public static boolean contourSmoothing(int pattern){return bendsCentrelines(pattern);}
    public static boolean followForm(int pattern){return bendsCentrelines(pattern);}
    /** The Classic v2.24 CMYK line engine has no micro-gap stage. */
    public static boolean microGapRecovery(boolean classicCmykLines){return !classicCmykLines;}
    /** The modern engine applies light-area retention only to parallel ribbon presence tracking. */
    public static boolean lightAreaLines(int pattern,boolean classicCmykLines){
        return classicCmykLines||!(pattern==VinylEngine.BEADED||pattern==VinylEngine.RINGS||pattern==VinylEngine.RAYS);
    }
    /** Effective cut pattern of each enabled layer (a layer override wins over the main style). */
    private static boolean anyLayer(VinylEngine.Settings s,java.util.function.IntPredicate p){
        if(p.test(s.pattern))return true;
        if(s.layers!=null)for(VinylEngine.Layer l:s.layers)
            if(l!=null&&l.enabled&&l.lineStyleOverride>=0&&p.test(l.lineStyleOverride))return true;
        return false;
    }
    /** QA fix: per-layer line style overrides make these sliders effective even when the main style ignores them. */
    public static boolean contourSmoothing(VinylEngine.Settings s){return anyLayer(s,LineSliderVisibility::contourSmoothing);}
    public static boolean followForm(VinylEngine.Settings s){return anyLayer(s,LineSliderVisibility::followForm);}
    public static boolean lightAreaLines(VinylEngine.Settings s){
        return anyLayer(s,p->lightAreaLines(p,s.classicCmykLines));
    }
    /** Route names as used by M5LineSliderEffectTest ("picker" = modern, "classic" = v2.24). */
    public static boolean visible(String route,int pattern,String slider){
        boolean classic="classic".equals(route);
        switch(slider){
            case "Contour smoothing":return contourSmoothing(pattern);
            case "Follow form (bend)":return followForm(pattern);
            case "Micro-gap recovery":return microGapRecovery(classic);
            case "Light-area lines":return lightAreaLines(pattern,classic);
            default:return true;
        }
    }
}

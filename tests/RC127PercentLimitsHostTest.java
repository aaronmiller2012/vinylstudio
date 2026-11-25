package com.halftonestudio;

/** JVM-only guard against >100% UI / saved / auto-tune scale values. */
public final class RC127PercentLimitsHostTest {
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){
        for(float v:new float[]{1f,1.15f,1.6f,2f,Float.POSITIVE_INFINITY,Float.NaN}){
            check(PercentControlLimits.pointDensity(v)<=1f,"Point density > 100%");
            check(PercentControlLimits.dotSize(v)<=1f,"Dot size > 100%");
            check(PercentControlLimits.dotCoverage(v)<=1f,"Coverage > 100%");
        }
        check(PercentControlLimits.pointDensity(.75f)==.75f,"Normal point density changed");
        check(PercentControlLimits.dotSize(.9f)==.9f,"Normal dot size changed");
        check(PercentControlLimits.dotCoverage(.8f)==.8f,"Normal coverage changed");
        check(PercentControlLimits.pointDensity(.2f)==.5f,"Point density lower bound");
        check(PercentControlLimits.dotSize(.2f)==.6f,"Dot size lower bound");
        check(PercentControlLimits.dotCoverage(.2f)==.35f,"Coverage lower bound");
        System.out.println("PASS RC127: percentage controls restricted to 100% maximum");
    }
}

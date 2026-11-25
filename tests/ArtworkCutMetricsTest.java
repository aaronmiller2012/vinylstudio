package com.halftonestudio;
import java.util.Arrays;
import java.util.Collections;
public final class ArtworkCutMetricsTest {
    static void check(boolean b){if(!b)throw new AssertionError();}
    static void near(double a,double b){check(Math.abs(a-b)<1e-7);}
    public static void main(String[] args){
        float[] outer={0,0,10,0,10,10,0,10},hole={2,2,2,4,4,4,4,2};
        ArtworkCutMetrics.Stats s=ArtworkCutMetrics.measure(Arrays.asList(VinylEngine.Shape.compound(new float[][]{outer,hole}),VinylEngine.Shape.circle(20,20,2)));
        check(s.regions==2&&s.holes==1&&s.loops==3);near(s.lengthMm,48+4*Math.PI);near(s.summedAreaMm2,96+4*Math.PI);
        check(ArtworkCutMetrics.measure(Collections.emptyList()).loops==0);
        s=ArtworkCutMetrics.measure(Arrays.asList(VinylEngine.Shape.polygon(outer),VinylEngine.Shape.polygon(outer)));
        near(s.summedAreaMm2,200);check(s.regions==2);
        check(Arrays.equals(outer,new float[]{0,0,10,0,10,10,0,10}));
        try{ArtworkCutMetrics.measure(Arrays.asList(VinylEngine.Shape.circle(0,0,Float.NaN)));throw new AssertionError();}catch(IllegalArgumentException expected){}
        try{ArtworkCutMetrics.measure(Arrays.asList(VinylEngine.Shape.polygon(0,0,1,1,2,2)));throw new AssertionError();}catch(IllegalArgumentException expected){}
        Thread.currentThread().interrupt();try{ArtworkCutMetrics.measure(Arrays.asList(VinylEngine.Shape.circle(0,0,1)));throw new AssertionError();}catch(java.util.concurrent.CancellationException expected){}finally{Thread.interrupted();}
        System.out.println("PASS artwork cut metrics: compound holes, circles, overlap semantics, immutability, invalid input, cancellation");
    }
}

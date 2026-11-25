package com.halftonestudio;

import java.util.Locale;

/**
 * D11: a resource limit must never be reported as "no self-intersections".
 * StyleKit.selfIntersections used to return 0 (simple) whenever its grid would exceed
 * 4M cells, i.e. for large contours with short edges (stipple, dense contour styles at 11 in).
 */
public final class V3IntersectionCheckTest {
    static void ok(boolean v,String m){if(!v)throw new AssertionError(m);}

    /** Ellipse with n vertices; optionally two vertices a quarter turn apart are swapped, which
     *  creates long chords that cross robustly (a twist of ADJACENT vertices on a 0.016 mm edge
     *  is below float resolution and is not a proper crossing). */
    static float[] ellipse(int n,float rx,float ry,boolean crossing){
        float[] p=new float[2*n];
        for(int i=0;i<n;i++){double a=2*Math.PI*i/n;p[2*i]=(float)(150+rx*Math.cos(a));p[2*i+1]=(float)(120+ry*Math.sin(a));}
        if(crossing){int i=n/3,j=i+n/4; float tx=p[2*i],ty=p[2*i+1]; p[2*i]=p[2*j];p[2*i+1]=p[2*j+1];p[2*j]=tx;p[2*j+1]=ty;}
        return p;
    }

    public static void main(String[] args){
        // Small cases keep their meaning.
        float[] square={0,0,10,0,10,10,0,10};
        float[] bowtie={0,0,10,10,10,0,0,10};
        ok(StyleKit.selfIntersections(square,1)==0,"square must be simple");
        ok(StyleKit.selfIntersections(bowtie,1)==1,"bow-tie must cross");
        ok(StyleKit.checkSelfIntersections(square,1).valid(),"square check must be valid");
        ok(!StyleKit.checkSelfIntersections(bowtie,1).valid(),"bow-tie check must be invalid");
        float[] triangle={0,0,5,0,0,5};
        ok(StyleKit.selfIntersections(triangle,1)==0,"triangle is simple");

        // Dense, physically large contours: about 0.016 mm edges on a 270 x 210 mm ellipse.
        // The old grid would need more than 4M cells and reported 0 even with a real crossing.
        float[] denseCrossing=ellipse(48_000,135,105,true);
        float[] denseSimple=ellipse(48_000,135,105,false);
        long t0=System.nanoTime();
        int crossings=StyleKit.selfIntersections(denseCrossing,1);
        long crossingMs=(System.nanoTime()-t0)/1_000_000;
        ok(crossings>0,"dense contour with a real crossing reported simple (fail-open)");
        StyleKit.IntersectionCheck crossingCheck=StyleKit.checkSelfIntersections(denseCrossing,4);
        ok(!crossingCheck.indeterminate&&!crossingCheck.pairs.isEmpty(),"dense crossing should be found exactly, not guessed");
        t0=System.nanoTime();
        StyleKit.IntersectionCheck simpleCheck=StyleKit.checkSelfIntersections(denseSimple,1);
        long simpleMs=(System.nanoTime()-t0)/1_000_000;
        ok(simpleCheck.valid(),"dense simple contour must be proven valid, got "+(simpleCheck.indeterminate?"indeterminate":"crossings"));
        ok(StyleKit.selfIntersections(denseSimple,1)==0,"dense simple contour must count 0");

        // Budget exhaustion is INDETERMINATE and every existing caller sees "not simple".
        StyleKit.IntersectionCheck starved=StyleKit.checkSelfIntersections(denseSimple,1,1_000L);
        ok(starved.indeterminate&&!starved.valid(),"exhausted budget must be indeterminate, never valid");
        ok(StyleKit.selfIntersectionsWithBudget(denseSimple,1,1_000L)>0,"indeterminate must count as not simple (fail closed)");

        // Non-finite coordinates can never be proven simple.
        float[] nan={0,0,10,0,Float.NaN,10,0,10};
        ok(!StyleKit.checkSelfIntersections(nan,1).valid()&&StyleKit.selfIntersections(nan,1)>0,"NaN contour must not be valid");

        // Duplicate consecutive vertices and collinear runs are not crossings.
        float[] dup={0,0,5,0,5,0,10,0,10,10,0,10};
        ok(StyleKit.selfIntersections(dup,1)==0,"duplicate vertex is not a crossing");

        // Bounded cost on dense input (host JVM; device is slower, so this only guards algorithmic regressions).
        ok(crossingMs<8000&&simpleMs<8000,String.format(Locale.US,"dense checks too slow: %d ms / %d ms",crossingMs,simpleMs));
        System.out.printf(Locale.US,"PASS intersection check: dense crossing found (%d ms), dense simple proven (%d ms), budget exhaustion fails closed%n",
            crossingMs,simpleMs);
    }
}

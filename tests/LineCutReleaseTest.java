package com.halftonestudio;

/** Physical-lane and exported-SVG geometry regression checks. */
public final class LineCutReleaseTest {
    static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    public static void main(String[] args){
        LineRibbonPolicy.Plan usual=LineRibbonPolicy.plan(2.54f,.8f,.5f,.90f,.8f);
        check(!usual.secondLane,"Unsafe subminimum half-pitch must not create cuts");
        LineRibbonPolicy.Plan roomy=LineRibbonPolicy.plan(2.54f,.35f,.30f,.90f,.8f);
        check(roomy.secondLane,"Adequate pitch should allow second ribbon");
        check(roomy.primaryHalfLimit*2>=.35f && roomy.detailHalfLimit*2>=.35f,
            "Each lane must meet physical cutting floor");
        check(roomy.primaryHalfLimit+roomy.detailHalfLimit+.30f<=1.27f+.00001f,
            "Adjacent lanes must retain weed gap");
        int w=88,h=88;
        int[] photo=LineArtEngineTest.portrait(w,h);
        VinylEngine.Settings s=LineArtEngineTest.settings();
        s.pattern=VinylEngine.STRAIGHT;s.minimumMm=.8f;s.bridgeMm=.5f;s.detailBoost=0;
        for(VinylEngine.Layer l:s.layers)l.spacing=2.54f;
        VinylEngine.Result no=VinylEngine.generate(photo,w,h,s);
        s.detailBoost=.88f;
        VinylEngine.Result boosted=VinylEngine.generate(photo,w,h,s);
        check(boosted.count()>0 && !VinylEngine.svg(no,3).equals(VinylEngine.svg(boosted,3)),
            "Physically impossible detail lanes must instead enhance main ribbons");
        check(VinylEngine.validateExport(boosted)==null,"Cut output must remain within SVG structural limits");
        LineCutDiagnostics.Report audit=LineCutDiagnostics.inspect(boosted);
        System.out.println("Production-like test: "+audit.ribbons+" ribbons, "+audit.selfIntersections+
            " confirmed self crossings; "+audit.subMinimumWidthSamples+" underwidth sampled regions; "+
            "complete="+audit.complete+" length="+audit.cutLengthMm);
        check(audit.ribbons>0 && audit.cutLengthMm>0,"Missing cut diagnostics");
        check(audit.subMinimumWidthSamples==0,"Output contains cuts thinner than the minimum floor");
        VinylEngine.Settings contourSettings=LineArtEngineTest.settings();
        contourSettings.classicCmykLines=false;
        contourSettings.pattern=VinylEngine.LINE;
        contourSettings.contourStrength=.95f;
        VinylEngine.Result variable=VinylEngine.generate(photo,w,h,contourSettings);
        contourSettings.pattern=VinylEngine.CONTOUR;
        VinylEngine.Result contoured=VinylEngine.generate(photo,w,h,contourSettings);
        check(!VinylEngine.svg(variable,3).equals(VinylEngine.svg(contoured,3)),
            "Contour and variable-width must remain visibly distinct cut styles");
        // Confirm the diagnostics actually detect an invalid polygon rather
        // than merely producing a report for known good artwork.
        VinylEngine.Settings b=LineArtEngineTest.settings();b.pattern=VinylEngine.STRAIGHT;
        VinylEngine.Result crossing=VinylEngine.generate(photo,w,h,b);
        crossing.shapes.get(0).clear();
        crossing.shapes.get(0).add(VinylEngine.Shape.polygon(5,5,25,25,5,25,25,5));
        LineCutDiagnostics.Report unsafe=LineCutDiagnostics.inspect(crossing);
        check(unsafe.selfIntersections>0,"Proper crossing in artificial shape undetected");
        System.out.println("PASS: physically feasible detail lanes, fallback, geometry diagnostics and SVG limits");
    }
}

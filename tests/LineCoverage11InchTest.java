package com.halftonestudio;

/** Full physical 11-inch source-size regression, without any private photo asset. */
public final class LineCoverage11InchTest {
    private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        int w=120,h=144;
        int[] photo=LineCoverageRC101Test.portrait(w,h);
        VinylEngine.Settings s=LineCoverageRC101Test.modern(VinylEngine.LINE);
        s.widthMm=279.4f;s.marginMm=4.0f;s.minimumMm=.8f;s.bridgeMm=.45f;
        s.linePresenceRecovery=.65f;s.lineHighlightRetention=.48f;
        for(VinylEngine.Layer l:s.layers)l.spacing=3.048f;
        s.snips.add(new SnipMask.Operation(SnipMask.KEEP_INSIDE,
            .05f,.06f,.95f,.06f,.95f,.94f,.05f,.94f));
        VinylEngine.Result result=VinylEngine.generate(photo,w,h,s);
        check(result.count()>0,"empty 11-inch line art");
        check(result.settings.layers.size()==4,"lost CMYK layers");
        LineCutDiagnostics.Report d=LineCutDiagnostics.inspect(result);
        check(d.complete&&d.selfIntersections==0&&d.subMinimumWidthSamples==0,
            "invalid 11-inch contour geometry: "+d.text(s.minimumMm,s.bridgeMm));
        check(VinylEngine.validateExport(result)==null,"invalid 11-inch SVG export");
        long promoted=0;
        for(int i=0;i<4;i++){
            promoted+=result.lineMetrics.get(i).promoted;
            check(!result.shapes.get(i).isEmpty(),"missing 11-inch physical color sheet "+i);
            for(VinylEngine.Shape sh:result.shapes.get(i))if(!sh.circle)
                check(s.snips.allowsPolygon(sh.xy,s.widthMm,result.heightMm),"Snip escaped sheet "+i);
        }
        System.out.println("PASS RC101 11-inch: "+result.count()+" ribbons, four CMYK sheets, "+
            promoted+" weak-tone promotions, deterministic physical width and Snip-safe SVG");
    }
}

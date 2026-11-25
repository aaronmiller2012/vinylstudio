package com.halftonestudio;

/**
 * RC103 regression for an opaque-vinyl portrait with cropped transparency.
 * A physical width budget must never destroy the source-channel evidence used
 * to decide which of the original carrier ribbons should remain continuous.
 */
public final class ContourLineRetentionRC103Test {
    static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args){
        final int w=160,h=184;
        int[] source=LineCoverageRC101Test.portrait(w,h);
        for(int k=0;k<source.length;k++) {
            if((source[k]&0x00ffffff)==0x00ffffff)source[k]=0x00ffffff;
        }
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        SmartHalftonePresets.apply(s,VinylEngine.CONTOUR,
                VinylSizing.BALANCED,11f,11f*h/w);
        s.registration=false;s.marginMm=1.5f;
        require(s.opaqueLineBalance,"RC103 portrait engine not selected");
        VinylEngine.Result r=VinylEngine.generate(source,w,h,s);
        require(r.opaqueLineScales==null && r.opaqueLineDetails==null,
                "transient portrait buffers escaped render");
        require(VinylEngine.validateExport(r)==null,"invalid contour SVG");
        LineCutDiagnostics.Report diagnostics=LineCutDiagnostics.inspect(r);
        require(diagnostics.complete && diagnostics.subMinimumWidthSamples==0
                && diagnostics.selfIntersections==0,
                "line recovery made uncuttable geometry");
        // With RC102's accidental `sourceCoverage *= sheetScale`, the yellow
        // and magenta carriers in this fixture had NO promoted continuity
        // samples even though they contained valid original photo color.
        // The test checks genuine restoration, not an increase in path count:
        // longer ribbons are preferable to thousands of disconnected bits.
        long yellow=r.lineMetrics.get(0).promoted;
        long magenta=r.lineMetrics.get(2).promoted;
        require(yellow>400,"warm portrait yellow lines were dropped: "+yellow);
        require(magenta>400,"warm portrait magenta lines were dropped: "+magenta);
        require(!r.shapes.get(0).isEmpty() && !r.shapes.get(2).isEmpty(),
                "original chromatic carrier missing");
        require(r.count()<1800,"line continuity fragmented into excess paths");
        // Pure transparent source must still generate no fictional lines.
        int[] empty=new int[w*h];
        require(VinylEngine.generate(empty,w,h,s).count()==0,
                "transparent background unexpectedly became vinyl");
        System.out.printf("PASS RC103: 11-inch transparent portrait contours; " +
                "yellow promoted=%d magenta promoted=%d, cut pieces=%d; " +
                "zero intersecting or subminimum ribbons%n",yellow,magenta,r.count());
    }
}

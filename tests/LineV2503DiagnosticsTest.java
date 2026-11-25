package com.halftonestudio;
/** Report geometry defects, including styles that must not be shipped without review. */
public final class LineV2503DiagnosticsTest {
    public static void main(String[] args){
        int[] styles={5,8,9,12,13,14,15,20,16,17,18,19};
        int[] src=LineV2494SignatureFixture.photo(66,64,1,0xfffaf9f6);
        for(int style:styles){
            VinylEngine.Settings s=VinylEngine.Settings.defaults();
            s.pattern=style;s.widthMm=68;s.marginMm=2;s.registration=false;
            s.masterSpacingMm=3.1f;s.minimumMm=.65f;s.bridgeMm=.42f;
            VinylEngine.Result r=VinylEngine.generate(src,66,64,s);
            LineCutDiagnostics.Report audit=LineCutDiagnostics.inspect(r);
            if(audit.ribbons==0||audit.selfIntersections!=0||audit.subMinimumWidthSamples!=0||!audit.complete)
                throw new AssertionError("Unsafe style="+style+" ribbons="+audit.ribbons+
                    " selfCross="+audit.selfIntersections+" underWidth="+audit.subMinimumWidthSamples+
                    " complete="+audit.complete);
            System.out.println("PASS geometry style="+style+" ribbons="+audit.ribbons+
                " selfCross=0 underWidth=0 complete=true");
        }
    }
}

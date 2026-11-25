package com.halftonestudio;

/** Mirrors the arguments StudioActivity passes to the specialty exporters, so host tests
 *  exercise the same export call the app makes. Keep in sync with exportNewStyle and the
 *  tessellation option setup in StudioActivity. */
final class SuiteExportCall {
    private SuiteExportCall(){}
    static String layerSvg(StyleKit.Result r,int i,boolean circleCrop){
        // StudioActivity.exportNewStyle passes settings.circleCrop (ADR-0001).
        return NewStyleExport.layerSvg(r,i,true,RegistrationMarks.CIRCLE,
            V3ExportSheetContractTest.MARK_MM,V3ExportSheetContractTest.CLEAR_MM,null,V3ExportSheetContractTest.MIN_CUT,circleCrop);
    }
    static void circle(TessellationEngine.Options o,boolean circleCrop){
        // StudioActivity sets opts.circleCrop=settings.circleCrop for tessellation renders.
        o.circleCrop=circleCrop;
    }
}

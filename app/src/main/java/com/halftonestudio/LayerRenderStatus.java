package com.halftonestudio;

import java.util.Locale;

/** Android-independent status formatting for the current committed render.
 * Counts use the exact exported paths; coverage is only an advisory estimate.
 */
public final class LayerRenderStatus {
    private LayerRenderStatus() {}

    public static String describe(VinylEngine.Result result, VinylDiagnostics.Report diagnostics,
                                  int selectedLayer, String diagnosticIssue) {
        if (result == null) return "Import a photo to begin";
        int n=result.settings.layers.size();
        if (selectedLayer<0 || selectedLayer>=n) {
            if (result.count()==0) return "0 cut shapes · Try a different palette, tone or minimum width";
            int active=0,max=0;
            for(int i=0;i<n;i++) if(result.settings.layers.get(i).enabled){
                active++;
                max=Math.max(max,VinylEngine.svgElementsForLayer(result,i));
            }
            String status=active+" layers · SVG max "+max+"/5k · "+result.count()+" total";
            return diagnosticIssue==null?status:status+" · "+diagnosticIssue;
        }
        VinylEngine.Layer selected=result.settings.layers.get(selectedLayer);
        int cuts=result.shapes.get(selectedLayer).size();
        String label=selected.name+" · "+cuts+" cuts";
        if (diagnostics!=null && diagnostics.layers!=null && selectedLayer<diagnostics.layers.length) {
            VinylDiagnostics.LayerStats d=diagnostics.layers[selectedLayer];
            label+=String.format(Locale.US," · %.1f%% covered · %.1f%% visible",d.coveredPct,d.visiblePct);
        } else if (diagnosticIssue!=null) {
            label+=" · "+diagnosticIssue;
        } else if (result.count()>0) {
            label+=" · Visibility calculating…";
        }
        if (cuts==0 && result.settings.mode==VinylEngine.CMYK && selected.cmykChannel==0)
            label+=" · Check Color → Shadow recovery";
        return label;
    }
}

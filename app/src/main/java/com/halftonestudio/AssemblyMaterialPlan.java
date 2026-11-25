package com.halftonestudio;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Deterministic sheet-footprint guidance for a physical multilayer craft job.
 * Not a nesting, vinyl-fill or weeding-margin estimate. Each physical vinyl
 * operation requires its own rectangular blank, including registration margins.
 */
public final class AssemblyMaterialPlan {
    public final float sheetWidthMm,sheetHeightMm;
    public final int vinylSheets;
    public final double totalBlankAreaSqCm;
    public final Map<Integer,Integer> sheetsByArgb;
    private AssemblyMaterialPlan(float w,float h,int n,Map<Integer,Integer> colors) {
        sheetWidthMm=w;sheetHeightMm=h;vinylSheets=n;
        totalBlankAreaSqCm=(double)w*h*n/100.0;
        sheetsByArgb=Collections.unmodifiableMap(new LinkedHashMap<>(colors));
    }
    public static AssemblyMaterialPlan from(AssemblyGuidePlan guide,float boardWidthMm,float boardHeightMm) {
        if(guide==null)throw new IllegalArgumentException("Missing assembly guide");
        PhysicalSheetParity.requireSameBoard(boardWidthMm,boardHeightMm,boardWidthMm,boardHeightMm);
        PhysicalSheetParity.requireFitsArtwork(boardWidthMm,boardHeightMm,guide.widthMm,guide.heightMm);
        LinkedHashMap<Integer,Integer> colors=new LinkedHashMap<>();
        int count=0;
        for(AssemblyGuidePlan.Layer layer:guide.layers){
            if(!layer.exported||layer.kind!=AssemblyGuidePlan.Kind.VINYL)continue;
            count++;
            colors.put(layer.color,colors.getOrDefault(layer.color,0)+1);
        }
        return new AssemblyMaterialPlan(boardWidthMm,boardHeightMm,count,colors);
    }
    public String summary() {
        if(vinylSheets==0)return "No vinyl sheets: pen / engraving or reference operations only.";
        return String.format(Locale.US,"%d full-size vinyl sheet%s of %.2f × %.2f in each (minimum %.1f cm² combined blank footprint).",
                vinylSheets,vinylSheets==1?"":"s",sheetWidthMm/25.4f,sheetHeightMm/25.4f,totalBlankAreaSqCm);
    }
    public String disclaimer() {
        return "Rectangular minimum only; not vinyl coverage or a nest/cost estimate. Add margins for weeding, handling, mat limits and material tests.";
    }
}

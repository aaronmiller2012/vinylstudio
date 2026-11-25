package com.halftonestudio;

import android.graphics.Path;
import java.util.ArrayList;

/** Collects assembly-guide layer pages and their deferred physical metrics. */
final class GuideAssembler {
    private final String paletteKey;
    final AssemblyGuidePlan.Builder plan;
    final ArrayList<AssemblyGuideDocument.LayerPage> pages=new ArrayList<>();
    int next=1;
    GuideAssembler(String project,String style,AssemblyLayout layout,int paper,boolean referenceOnly,String paletteKey){
        this.paletteKey=paletteKey;
        plan=new AssemblyGuidePlan.Builder(project,style,layout.widthMm,layout.heightMm,paper)
            .registration(layout.registration,RegistrationMarks.name(layout.markStyle),layout.markSizeMm)
            .referenceOnly(referenceOnly);
    }
    void add(String name,int color,int count,AssemblyGuidePlan.Kind kind,String note,
            ArtworkDocumentExport.Page artwork){
        String physicalCode=kind==AssemblyGuidePlan.Kind.VINYL?
            ManufacturerVinylPalettes.matchCode(paletteKey,color):"";
        String qualifiedNote=physicalCode.isEmpty()?note:
            (note==null||note.isEmpty()?"":note+" ")+physicalCode;
        AssemblyGuidePlan.Layer layer=new AssemblyGuidePlan.Layer(next++,name,color,count,true,kind,qualifiedNote);
        plan.addLayer(layer);pages.add(new AssemblyGuideDocument.LayerPage(layer,artwork));
    }
    void standardMetrics(java.util.List<VinylEngine.Shape> shapes){
        int index=pages.size()-1; AssemblyGuideDocument.LayerPage page=pages.get(index);
        pages.set(index,new AssemblyGuideDocument.LayerPage(page.layer,page.artwork,()->ArtworkCutMetrics.measure(shapes)));
    }
    void pathMetrics(double scale,Path... paths){
        int index=pages.size()-1;AssemblyGuideDocument.LayerPage page=pages.get(index);
        pages.set(index,new AssemblyGuideDocument.LayerPage(page.layer,page.artwork,()->AssemblyPathMetrics.measure(scale,paths)));
    }
    AssemblyGuideDocument.Document finish(ArtworkDocumentExport.Page composite){
        return new AssemblyGuideDocument.Document(plan.layerMappingComplete(true).build(),composite,pages);
    }
}

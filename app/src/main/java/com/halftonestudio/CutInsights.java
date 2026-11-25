package com.halftonestudio;

import java.util.ArrayList;
import java.util.List;

/** Advisory cut diagnostics. Not a substitute for a machine/material test cut.
 * Cricut's 5000-path upload constraint is enforced by VinylEngine separately.
 */
public final class CutInsights {
    private CutInsights() {}
    public static final class Notice {
        public final String title,detail,action;
        public final boolean severe;
        Notice(String name,String detail,String action,boolean severe){this.title=name;this.detail=detail;this.action=action;this.severe=severe;}
    }
    public static List<Notice> inspect(VinylEngine.Result r){
        ArrayList<Notice> notes=new ArrayList<>();
        String marks=VinylEngine.validateMarks(r);
        if(marks!=null)notes.add(new Notice("Registration interference",marks,"Open Marks and move the highlighted mark away from every vinyl layer",true));
        for(int i=0;i<r.shapes.size();i++){
            if(!r.settings.layers.get(i).enabled)continue;
            int paths=VinylEngine.svgElementsForLayer(r,i);
            if(paths>5000)notes.add(new Notice("Layer "+(i+1)+" is over Cricut's SVG limit",
                paths+" paths in this SVG","Increase shared spacing or run Auto Fit",true));
            else if(paths>4300)notes.add(new Notice("Layer "+(i+1)+" approaches the SVG limit",
                paths+" paths in this SVG","Increase spacing slightly for extra headroom",false));
            long vertices=0;
            for(VinylEngine.Shape sh:r.shapes.get(i))vertices+=sh.vertexCount();
            if(vertices>100000)notes.add(new Notice("Layer "+(i+1)+" has complex contours",
                String.format(java.util.Locale.getDefault(),"%,d",vertices)+" polygon vertices (approx.)",
                "Increase sampling distance in Adjust or reduce engraving detail",false));
        }
        if(r.settings.mode==VinylEngine.STENCIL){
            notes.add(new Notice("Solid-color stencil contours",
                "Colors are selected from the configured vinyl palette using Lab color distance. Small islands and tight internal cutouts may require manual weeding.",
                "Inspect each layer in the preview and make a scrap-vinyl test before production",false));
        }
        int pattern=r.settings.pattern;
        if(r.settings.mode!=VinylEngine.STENCIL){
            boolean crossings=false,curves=false,beads=false;
            for(int li=0;li<r.settings.layers.size();li++){
                VinylEngine.Layer color=r.settings.layers.get(li);
                if(!color.enabled)continue;
                int style=color.lineStyleOverride>=0?color.lineStyleOverride:pattern;
                if(style==VinylEngine.HATCH||style==VinylEngine.CROSSHATCH)crossings=true;
                if(style==VinylEngine.WAVE||style==VinylEngine.CONTOUR||style==VinylEngine.LINE||
                    style==VinylEngine.RINGS||style==VinylEngine.ZIGZAG)curves=true;
                if(style==VinylEngine.BEADED)beads=true;
                if(LineArtEngine.isLineStyle(style)){
                    float spacing=Math.max(.8f,color.spacing);
                    float maxWidth=spacing*r.settings.lineMaxFraction*color.lineWidthScale;
                    if(style==VinylEngine.BEADED)maxWidth=spacing*.60f;
                    if(maxWidth<r.settings.minimumMm){
                        notes.add(new Notice("Layer "+(li+1)+" too narrow for cutter",
                            String.format(java.util.Locale.US,"Maximum theoretical width %.2f mm is below requested %.2f mm",maxWidth,r.settings.minimumMm),
                            "Increase spacing/width or lower the minimum feature size only after a test cut",true));
                    }
                }
            }
            if(crossings)notes.add(new Notice("Layer-safe hatching",
                "Hatch and crosshatch styles use one contour family per vinyl layer to avoid same-color double cuts. Composite crosshatching can arise from the angles of separate layers.",
                "Inspect close intersections across separate colors and test-cut one layer",false));
            if(curves)notes.add(new Notice("Line contour tolerance",
                "Bends, corners and tiny holes may challenge weeding even when exported vertices meet the minimum width setting.",
                "Use Easy weed if necessary and check details at physical size",false));
            if(beads)notes.add(new Notice("Small separate beads",
                "Beaded screens use many individually cut circles and can produce many tiny weed pieces.",
                "Increase spacing or use a simple ribbon style for very small portraits",false));
            if(r.settings.detailBoost>.01f)notes.add(new Notice("Adaptive fine-detail strokes",
                "Short additional ribbons appear at selected tonal edges. Local line density can be approximately twice the base LPI.",
                "Check the eyes, lips and narrow features at actual cut dimensions",false));
            if(r.settings.mode==VinylEngine.CMYK&&r.settings.anglePreset!=VinylEngine.ANGLES_PARALLEL){
                for(int i=0;i<r.settings.layers.size();i++)for(int j=i+1;j<r.settings.layers.size();j++){
                    float a=r.settings.layers.get(i).angle,b=r.settings.layers.get(j).angle;
                    float diff=Math.abs(a-b)%180f;diff=Math.min(diff,180f-diff);
                    if(diff<7.5f){
                        notes.add(new Notice("Similar screen angles",
                            r.settings.layers.get(i).name+" and "+r.settings.layers.get(j).name+
                            " are "+String.format(java.util.Locale.US,"%.1f°",diff)+" apart and may create unwanted pattern interference.",
                            "Fine-tune one channel ±2–5° in Adjust and inspect the composite",false));
                    }
                }
            }
        }
        return notes;
    }
}

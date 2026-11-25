package com.halftonestudio;

/** Single-source layer SVG export; registration marks are identical on all vinyl sheets.
 *  Marked sheets use the shared {@link SheetFrame} (ADR-0001), so they align with the Snip
 *  base sheet and with the marks drawn in the editor preview, including circle crops. */
final class NewStyleExport {
    private NewStyleExport(){}
    /** Rectangular-crop overload kept for existing callers and tests. */
    static String layerSvg(StyleKit.Result result,int index,boolean registration,int markStyle,float markMm,float clearanceMm){
        return layerSvg(result,index,registration,markStyle,markMm,clearanceMm,null,.8f,false);
    }
    /** Rectangular-crop overload kept for existing callers and tests. */
    static String layerSvg(StyleKit.Result result,int index,boolean registration,int markStyle,float markMm,float clearanceMm,float[][] customMarks,float minimumCutMm){
        return layerSvg(result,index,registration,markStyle,markMm,clearanceMm,customMarks,minimumCutMm,false);
    }
    static String layerSvg(StyleKit.Result result,int index,boolean registration,int markStyle,float markMm,float clearanceMm,
                           float[][] customMarks,float minimumCutMm,boolean circleCrop){
        if(result==null||index<0||index>=result.layers.size())throw new IllegalArgumentException("Invalid vector layer");
        StyleKit.Layer layer=result.layers.get(index);
        if(!registration){
            // Unmarked sheets: artboard == artwork, unchanged since build 556.
            StyleKit.Result single=new StyleKit.Result(result.widthMm,result.heightMm);
            single.paperArgb=result.paperArgb;single.layers.add(layer);
            return StyleKit.toSvg(single,false);
        }
        SheetFrame frame=frame(result,markMm,clearanceMm,customMarks,circleCrop);
        String unsafe=RegistrationCutGeometry.style(result,markMm,clearanceMm,frame.marks()).firstIssue();
        if(unsafe!=null)throw new IllegalArgumentException(unsafe);
        StringBuilder out=new StringBuilder(4096+layer.loops.size()*96);
        out.append(frame.open(layer.color));
        StyleKit.appendLayer(out,layer);
        frame.appendMarks(out,markStyle,markMm,minimumCutMm);
        return out.append(SheetFrame.CLOSE).toString();
    }
    /** The frame every marked suite sheet (and its Snip base) uses; also what Cut Check validates. */
    static SheetFrame frame(StyleKit.Result result,float markMm,float clearanceMm,float[][] customMarks,boolean circleCrop){
        return SheetFrame.of(result.widthMm,result.heightMm,true,markMm,clearanceMm,customMarks,
            SheetFrame.SPECIALTY_PADDING_MM,circleCrop);
    }
}

package com.halftonestudio;

/** Conservative validation: SVG paths, compound subpaths, physical mm units, and unsupported shapes. */
public final class LayeredCellExportSafety {
    private LayeredCellExportSafety(){}
    private static int count(String text,String term){int n=0,i=0;while((i=text.indexOf(term,i))>=0){n++;i+=term.length();}return n;}
    public static void validate(LayeredCellPortraitEngine.Result r,int layer,String svg){
        if(r==null||layer<0||layer>=r.options.layerCount||r.pieceCounts[layer]<=0||svg==null)
            throw new IllegalArgumentException("Empty portrait layer");
        if(!svg.startsWith("<svg ")||!svg.contains("mm\" height=\"")||!svg.contains("mm\" viewBox=\""))
            throw new IllegalArgumentException("Physical SVG sizes must use millimeters");
        for(String unsupported:new String[]{"<clipPath","<mask","<image","<use","<foreignObject","<pattern","<linearGradient","<radialGradient","NaN","Infinity"})
            if(svg.contains(unsupported))throw new IllegalArgumentException("Unsupported SVG content: "+unsupported);
        int marks=r.options.registration?4:0;
        int pieces=r.pieceCounts[layer];
        int pathCount=count(svg,"<path ");
        int circles=r.options.registration&&r.options.markStyle==RegistrationMarks.CIRCLE?4:0;
        if(pathCount!=pieces+marks-circles||count(svg,"<circle ")!=circles)
            throw new IllegalArgumentException("SVG vector element count disagrees with the generated pieces");
        if(pieces+marks>4800||r.contourCounts[layer]+marks>4800)
            throw new IllegalArgumentException("Conservative Cricut contour budget exceeded");
        if(!svg.contains("fill-rule=\"evenodd\""))throw new IllegalArgumentException("Compound hole fill rule missing");
        // Only same-sheet cutting contours need minimum separation. Fish Scale
        // pieces on DIFFERENT physical sheets intentionally stack in vinyl.
        PhysicalContourClearance.requireLayerClearance(r,layer);
    }
}

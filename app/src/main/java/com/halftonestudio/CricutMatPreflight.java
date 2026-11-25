package com.halftonestudio;

import java.io.IOException;

/** Maker/Explore on-mat cut dimensions, not Print Then Cut dimensions.
 * Applies only to the Cricut SVG output target. No resizing or implicit
 * rotation: either orientation must fit the largest supported 12x24 mat.
 */
final class CricutMatPreflight {
    private static final double SHORT_MM=11.5*25.4;
    private static final double LONG_MM=23.5*25.4;
    /** Written into every Cricut cut ZIP: which mat the sheets physically need. */
    static final String NOTE_FILE="CRICUT-MAT.txt";
    /** Mat identifiers persisted in projects ("cricutMat"); the selected mat is authoritative. */
    static final String MAT_12X12="12x12",MAT_12X24="12x24";
    private CricutMatPreflight(){}
    /** Unknown or missing values fall back to null (no selection), never to a larger mat. */
    static String normalizeMat(String mat){
        if(MAT_12X12.equals(mat))return MAT_12X12;
        if(MAT_12X24.equals(mat))return MAT_12X24;
        return null;
    }
    static String matLabel(String mat){return MAT_12X24.equals(mat)?"12 × 24 in":"12 × 12 in";}
    /** True when a marked sheet of this size can be cut on the given mat without any scaling. */
    static boolean fitsMat(double widthMm,double heightMm,String mat){
        String need=requiredMat(widthMm,heightMm);
        if(need==null)return false;
        return MAT_12X24.equals(mat)||need.equals(MAT_12X12);
    }
    private static boolean within(double a,double limit){return a<=limit+0.001;}
    /** "12x12" when the sheet fits 11.5 x 11.5 in, "12x24" when it fits only 11.5 x 23.5 in
     *  (either orientation), otherwise null (no Cricut mat). */
    static String requiredMat(double widthMm,double heightMm){
        if(!(Double.isFinite(widthMm)&&Double.isFinite(heightMm)&&widthMm>0&&heightMm>0))return null;
        if(within(widthMm,SHORT_MM)&&within(heightMm,SHORT_MM))return "12x12";
        if((within(widthMm,SHORT_MM)&&within(heightMm,LONG_MM))||(within(heightMm,SHORT_MM)&&within(widthMm,LONG_MM)))return "12x24";
        return null;
    }
    /** Plain-language note for the ZIP; all sheets of one export share one artboard. */
    static String note(double widthMm,double heightMm){
        String mat=requiredMat(widthMm,heightMm);
        String size=String.format(java.util.Locale.US,"Each cut sheet is %.2f × %.2f in (%.1f × %.1f mm), including registration margins.\n",
            widthMm/25.4,heightMm/25.4,widthMm,heightMm);
        if("12x12".equals(mat))return "Mat: 12 × 12 in (StandardGrip or LightGrip).\n"+size+
            "The usable cutting area of a 12 × 12 in mat is about 11.5 × 11.5 in.\n";
        return "Mat: 12 × 24 in REQUIRED.\n"+size+
            "These sheets will not fit a 12 × 12 in mat (usable area about 11.5 × 11.5 in).\n"+
            "Use a 12 × 24 in mat, or reduce the artwork size in Export → Size and export again.\n"+
            "Do not let Design Space resize the sheets: every layer must stay at this exact size to register.\n";
    }
    /** Fail-closed check against the mat the user selected. Never rescales. */
    static void requireFits(double widthMm,double heightMm,String file,String selectedMat) throws IOException {
        requireFits(widthMm,heightMm,file);
        String mat=normalizeMat(selectedMat);
        if(mat==null||fitsMat(widthMm,heightMm,mat))return;
        throw new IOException(String.format(java.util.Locale.US,
            "Selected Cricut mat is %s: %s is %.2f x %.2f inches including registration marks, "+
            "larger than the 11.5 x 11.5 inch cutting area. Choose the 12 x 24 mat in Size, "+
            "or reduce the artwork width. VinylStudio will never silently rescale a cut sheet.",
            matLabel(mat),file,widthMm/25.4,heightMm/25.4));
    }
    /** ZIP note for an export whose sheets passed the selected-mat check. */
    static String note(double widthMm,double heightMm,String selectedMat){
        String mat=normalizeMat(selectedMat);
        if(mat==null)return note(widthMm,heightMm);
        String size=String.format(java.util.Locale.US,"Each cut sheet is %.2f × %.2f in (%.1f × %.1f mm), including registration margins.\n",
            widthMm/25.4,heightMm/25.4,widthMm,heightMm);
        if(MAT_12X12.equals(mat))return "Selected mat: 12 × 12 in (StandardGrip or LightGrip).\n"+size+
            "The usable cutting area of a 12 × 12 in mat is about 11.5 × 11.5 in.\n"+
            "Do not let Design Space resize the sheets: every layer must stay at this exact size to register.\n";
        String need=requiredMat(widthMm,heightMm);
        return "Selected mat: 12 × 24 in.\n"+size+
            (MAT_12X24.equals(need)?"These sheets will not fit a 12 × 12 in mat (usable area about 11.5 × 11.5 in).\n":
                "These sheets would also fit a 12 × 12 in mat.\n")+
            "Do not let Design Space resize the sheets: every layer must stay at this exact size to register.\n";
    }
    static void requireFits(double widthMm,double heightMm,String file) throws IOException {
        if(!(Double.isFinite(widthMm)&&Double.isFinite(heightMm)&&widthMm>0&&heightMm>0))
            throw new IOException("Invalid Cricut physical sheet dimensions: "+file);
        if(requiredMat(widthMm,heightMm)==null)
            throw new IOException(String.format(java.util.Locale.US,
                "Cricut Maker mat limit: %s is %.2f x %.2f inches, exceeding 11.5 x 23.5 inches " +
                "(12 x 24 mat). Move registration marks into safe blank space or reduce " +
                "artwork size; VinylStudio will never silently rescale a cut sheet.",
                file,widthMm/25.4,heightMm/25.4));
    }
}

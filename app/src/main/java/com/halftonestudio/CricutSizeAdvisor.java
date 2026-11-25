package com.halftonestudio;

import java.util.Locale;

/** Exact mathematical preview of the registration envelope for the size dialog.
 * The final SVG/CricutMatPreflight check remains authoritative: a specialty
 * exporter or hand-moved marks can require more space. This is an advisor,
 * not a rescale operation and not a blanket limit for other cutters.
 */
public final class CricutSizeAdvisor {
    private CricutSizeAdvisor() {}
    private static final double SHORT=11.5*25.4;
    private static final double LONG=23.5*25.4;
    public static final class Estimate {
        public final double artWidthMm,artHeightMm,sheetWidthMm,sheetHeightMm;
        public final boolean fits12x12,fits12x24;
        Estimate(double a,double b,double w,double h){
            artWidthMm=a;artHeightMm=b;sheetWidthMm=w;sheetHeightMm=h;
            fits12x12=w<=SHORT+.001&&h<=SHORT+.001;
            fits12x24=(w<=SHORT+.001&&h<=LONG+.001)||
                (h<=SHORT+.001&&w<=LONG+.001);
        }
        public String label(){
            return String.format(Locale.US,"Estimated cut sheet %.3f × %.3f in · %s",
                sheetWidthMm/25.4,sheetHeightMm/25.4,
                fits12x12?"fits 12×12 mat":fits12x24?"requires 12×24 mat":"exceeds Cricut mat limits");
        }
    }
    public static Estimate estimate(double artWidthIn,double sourceWidth,double sourceHeight,
                                    boolean registration,float markSizeMm,float clearanceMm,
                                    float paddingMm,boolean circleCrop){
        return estimate(artWidthIn,sourceWidth,sourceHeight,registration,markSizeMm,clearanceMm,paddingMm,circleCrop,null);
    }
    /** Same envelope as the exporters' {@link SheetFrame}: custom marks enlarge the sheet exactly
     *  as they do on export. The padding is the larger of the suite (8 mm) and standard
     *  (outer spacing) margins, so the estimate never under-reports either exporter. */
    public static Estimate estimate(double artWidthIn,double sourceWidth,double sourceHeight,
                                    boolean registration,float markSizeMm,float clearanceMm,
                                    float paddingMm,boolean circleCrop,float[][] customMarks){
        if(!Double.isFinite(artWidthIn)||artWidthIn<=0||
           !Double.isFinite(sourceWidth)||!Double.isFinite(sourceHeight)||
           sourceWidth<=0||sourceHeight<=0)
            throw new IllegalArgumentException("Invalid physical art dimensions");
        double w=artWidthIn*25.4,h=w*sourceHeight/sourceWidth;
        if(!Double.isFinite(w)||!Double.isFinite(h)||w>Float.MAX_VALUE||h>Float.MAX_VALUE)
            throw new IllegalArgumentException("Unrepresentable physical art size");
        // The standard VinylEngine still adds outerSpacingMm to the board
        // without registration marks; omitting that padding would report a
        // physically oversized SVG as 12x12 compatible.
        if(!registration){
            double pad=Math.max(0,paddingMm);
            return new Estimate(w,h,w+2*pad,h+2*pad);
        }
        RegistrationMarks.Layout layout=RegistrationMarks.artworkLayout((float)w,(float)h,
            markSizeMm,clearanceMm,customMarks,Math.max(SheetFrame.SPECIALTY_PADDING_MM,paddingMm),circleCrop);
        double boardW=w+2*(double)layout.paddingMm,boardH=h+2*(double)layout.paddingMm;
        return new Estimate(w,h,boardW,boardH);
    }
    /** Monotone search of feasible artwork widths, respecting mark padding.
     * This informs the artist rather than modifying their output geometry. */
    public static double maximumWidthIn(boolean longMat,double sourceW,double sourceH,
            boolean registration,float markSizeMm,float clearanceMm,float paddingMm,boolean circleCrop){
        return maximumWidthIn(longMat,sourceW,sourceH,registration,markSizeMm,clearanceMm,paddingMm,circleCrop,
            RegistrationMarks.PLACEMENT_CORNERS);
    }
    /** Compact marks for a saved placement at this width, or null for the legacy corner default. */
    public static float[][] layoutMarks(int markLayout,double artWidthIn,double sourceW,double sourceH,
            float markSizeMm,float clearanceMm){
        if(RegistrationMarks.normalizePlacement(markLayout)!=RegistrationMarks.PLACEMENT_ABOVE_BELOW)return null;
        double w=artWidthIn*25.4,h=w*sourceH/sourceW;
        return RegistrationMarks.aboveBelowPositions((float)w,(float)h,markSizeMm,clearanceMm);
    }
    /** As above, honouring the project's saved mark layout (M5 compact above/below marks). */
    public static double maximumWidthIn(boolean longMat,double sourceW,double sourceH,
            boolean registration,float markSizeMm,float clearanceMm,float paddingMm,boolean circleCrop,int markLayout){
        double lo=0,hi=24;
        for(int i=0;i<32;i++){
            double mid=(lo+hi)*.5;
            boolean fits;
            try{
                Estimate e=estimate(mid,sourceW,sourceH,registration,
                    markSizeMm,clearanceMm,paddingMm,circleCrop,
                    layoutMarks(markLayout,mid,sourceW,sourceH,markSizeMm,clearanceMm));
                fits=longMat?e.fits12x24:e.fits12x12;
            }catch(IllegalArgumentException ex){fits=false;}
            if(fits)lo=mid;else hi=mid;
        }
        return Math.max(0,lo-.0001); // do not advertise a rounding-edge oversize
    }
}

package com.halftonestudio;

import java.util.ArrayList;

/** Automatic extra physical vinyl sheet, independent of the 2–8 detail-color slots.
 * Uses actual prepared-alpha/Snip geometry, never a visual-only SVG mask.
 */
public final class AutoSilhouetteUnderlay {
    private AutoSilhouetteUnderlay() {}
    public static final int DEFAULT_COLOR=0xffffffff;

    /** These styles already contain a filled foundation or are single-path tools. */
    public static boolean standardEligible(VinylEngine.Settings s) {
        return s!=null&&(s.mode==VinylEngine.CMYK||s.mode==VinylEngine.SPOT)
            && s.snips!=null&&!s.snips.isEmpty() && !hasLegacyBase(s);
    }
    public static boolean specialtyEligible(int style,int variant,boolean stippleOrVoronoi) {
        if(stippleOrVoronoi)return true;
        return style==VinylStyleSuite.LINOCUT||style==VinylStyleSuite.RIDGE||style==VinylStyleSuite.BANKNOTE
            ||(style==VinylStyleSuite.TOPOGRAPHIC&&variant==1)
            ||style==VinylStyleSuite.FINGERPRINT||style==VinylStyleSuite.WOVEN_RIBBON
            ||style==VinylStyleSuite.WOODGRAIN;
    }
    public static boolean hasLegacyBase(VinylEngine.Settings s){
        if(s==null)return false;
        for(VinylEngine.Layer l:s.layers)if(l.silhouetteBase)return true;
        return false;
    }
    public static final class Output {
        public final VinylEngine.Result result;
        public final VinylEngine.Shape shape;
        public final int color;
        Output(VinylEngine.Result result, int color){this.result=result;this.shape=result.shapes.get(0).get(0);this.color=color;}
        /** Color-only changes reuse identical validated Snip contours. */
        Output recolor(int nextColor){return nextColor==color?this:new Output(result,nextColor);}
        public String svg(){
            String markIssue=VinylEngine.validateMarks(result);
            if(markIssue!=null)throw new IllegalStateException("Solid Snip base registration: "+markIssue);
            return VinylEngine.svg(result,0,color);
        }
        public int cutContours(){return shape.contours==null?1:shape.contours.length;}
    }
    /** Computes in a rendering worker. The backing photo is not blended with paper. */
    public static Output make(VinylEngine.Settings original,int[] rgba,int width,int height,int color){
        if(original==null||rgba==null||width<2||height<2||rgba.length!=width*height
                ||original.snips==null||original.snips.isEmpty())return null;
        VinylEngine.Settings s=original.copy();
        s.layers.clear();
        VinylEngine.Layer base=new VinylEngine.Layer("Solid Snip background",color);
        base.silhouetteBase=true;base.cmykChannel=-1;
        s.layers.add(base);
        s.snipFillMode=SnipBackground.EMPTY; // Snip display fill must not restore cut-away pixels.
        float hh=s.widthMm*height/(float)width;
        // ADR-0001: identical frame resolution to every detail sheet it is stacked under.
        SheetFrame frame=SheetFrame.of(s.widthMm,hh,s.registration,s.markSizeMm,
                s.registrationClearanceMm,s.marks,s.outerSpacingMm,s.circleCrop);
        float[][] positions=frame.registration()?frame.marks():
            RegistrationMarks.artworkLayout(s.widthMm,hh,s.markSizeMm,s.registrationClearanceMm,s.marks,s.outerSpacingMm,s.circleCrop).positions;
        VinylEngine.Result r=new VinylEngine.Result(s,hh,frame.paddingMm,positions);
        r.shapes.add(new ArrayList<>());
        r.lineMetrics.add(new LinePresenceTracker.Metrics());
        SolidSnipBase.generate(r,rgba,width,height,0);
        return new Output(r,color);
    }
}

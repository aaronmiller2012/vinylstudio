package com.halftonestudio;

import java.util.Locale;

/** Shared vinyl registration geometry: one closed, cuttable contour per mark.
 * Millimeter geometry is generated once per render and reused unchanged by
 * SVG exports and the Android preview for every physical color layer.
 */
public final class RegistrationMarks {
    private RegistrationMarks() {}
    public static final int CROSS=0, CIRCLE=1, CORNER=2, SQUARE=3, STAR=4, DIAMOND=5, TRIANGLE=6;
    public static final float DEFAULT_DIAMETER_MM=2.5f, MIN_DIAMETER_MM=2f, MAX_DIAMETER_MM=12f;
    public static float checkedSize(float mm) {
        if(!Float.isFinite(mm)||mm<MIN_DIAMETER_MM||mm>MAX_DIAMETER_MM)
            throw new IllegalArgumentException("Registration mark diameter must be 2–12 mm");
        return mm;
    }
    public static boolean validStyle(int style) { return style>=CROSS&&style<=TRIANGLE; }
    public static String name(int style) {
        switch(style){case CIRCLE:return "Circle";case CORNER:return "L corner";case SQUARE:return "Square";
            case STAR:return "Star";case DIAMOND:return "Diamond";case TRIANGLE:return "Triangle";default:return "Cross";}
    }
    /** A registration layout is expressed in ARTWORK coordinates, not sheet coordinates.
     * The same coordinates are translated by the export's artboard padding for every sheet.
     */
    public static final class Layout {
        public final float[][] positions;
        public final float paddingMm;
        private Layout(float[][] positions,float paddingMm){this.positions=positions;this.paddingMm=paddingMm;}
    }
    public static float[][] copyPositions(float[][] positions){
        if(positions==null)return null;
        if(positions.length!=4)throw new IllegalArgumentException("Exactly four registration marks are required");
        float[][] copied=new float[4][2];
        for(int i=0;i<4;i++){
            if(positions[i]==null||positions[i].length!=2||!Float.isFinite(positions[i][0])||!Float.isFinite(positions[i][1]))
                throw new IllegalArgumentException("Registration mark coordinates must be finite XY pairs");
            copied[i]=positions[i].clone();
        }
        return copied;
    }
    public static Layout layout(float width,float height,float diameterMm,float clearanceMm,float[][] selected,float requestedPaddingMm){
        checkedSize(diameterMm);
        if(!Float.isFinite(width)||!Float.isFinite(height)||width<=0||height<=0 ||
           !Float.isFinite(clearanceMm)||clearanceMm<0||clearanceMm>50||
           !Float.isFinite(requestedPaddingMm)||requestedPaddingMm<0)
            throw new IllegalArgumentException("Invalid registration artboard parameters");
        float[][] marks=selected==null?defaultPositions(width,height,diameterMm,clearanceMm):copyPositions(selected);
        // Do not inflate a physical sheet to hide malformed or extreme manual
        // registration positions. The same gate serves saved projects on load
        // and every cutter-specific export (before padding is calculated).
        RegistrationCutGeometry.Report validity=RegistrationCutGeometry.basic(
            width,height,diameterMm,clearanceMm,marks);
        if(!validity.valid())throw new IllegalArgumentException(validity.firstIssue());
        float envelope=markRadius(diameterMm,clearanceMm);
        float pad=requestedPaddingMm;
        // M5 compact layout: marks exactly at the above/below positions use ONLY the margin the
        // marks need (no outer-spacing floor), so an 11 in artwork fits a 12x12 mat. Any other
        // positions (every legacy project, every manually placed mark) keep the floor.
        // QA fix: the editor places compact marks from the photo aspect, while suite/specialty
        // engines size the artwork from their prepared raster (0.1-0.2 mm taller or shorter). A
        // compact set that matches exactly in x and in the top row, with the bottom row within
        // 1 mm, is re-seated on THIS artwork's exact compact positions so every sheet (and the
        // Snip base) gets the same compact frame. Hand-placed marks never match this pattern.
        if(!isAboveBelow(marks,width,height,diameterMm,clearanceMm)&&isAboveBelowNear(marks,width,height,diameterMm,clearanceMm))
            marks=aboveBelowPositions(width,height,diameterMm,clearanceMm);
        if(isAboveBelow(marks,width,height,diameterMm,clearanceMm))pad=compactPaddingMm(diameterMm,clearanceMm);
        for(int i=0;i<marks.length;i++){
            float[] m=marks[i];
            // Preserve custom positions, enlarging the sheet rather than clipping them.
            pad=Math.max(pad,Math.max(Math.max(-m[0],m[0]-width),Math.max(-m[1],m[1]-height))+envelope+.5f);
            for(int j=0;j<i;j++){
                float[] other=marks[j];
                float dx=other[0]-m[0],dy=other[1]-m[1];
                if(dx*dx+dy*dy<4f*envelope*envelope)
                    throw new IllegalArgumentException("Registration marks overlap: "+(j+1)+" and "+(i+1));
            }
        }
        return new Layout(marks,pad);
    }
    /** Conservative specialty-export preflight: reject marks on the physical artwork area.
     * Standard VinylEngine can allow a mark inside an empty portion of the artwork
     * because it validates against the actual cut geometry instead.
     */
    public static void requireOutsideArtwork(Layout layout,float width,float height,float diameterMm,float clearanceMm){
        float r=markRadius(diameterMm,clearanceMm);
        for(int i=0;i<4;i++){
            float x=layout.positions[i][0],y=layout.positions[i][1];
            if(x+r>0&&x-r<width&&y+r>0&&y-r<height)
                throw new IllegalArgumentException("Registration mark "+(i+1)+" intersects the artwork area; move it outside the design");
        }
    }
    /** Absolute filled-outline coordinates for a mark, with NO nested/overlapping paths. */
    public static float[] outline(int style,float cx,float cy,float diameterMm){
        return outline(style,cx,cy,diameterMm,.8f);
    }
    public static float[] outline(int style,float cx,float cy,float diameterMm,float minimumCutMm){
        if(!validStyle(style))throw new IllegalArgumentException("Unknown mark style");
        float r=checkedSize(diameterMm)/2f;
        if(!Float.isFinite(minimumCutMm)||minimumCutMm<=0||minimumCutMm>=diameterMm)
            throw new IllegalArgumentException("Registration size must exceed material minimum cut width");
        switch(style){
            case CIRCLE: return null; // one genuine SVG circle, not a stroked path
            case SQUARE: return new float[]{cx-r,cy-r,cx+r,cy-r,cx+r,cy+r,cx-r,cy+r};
            case DIAMOND:return new float[]{cx,cy-r,cx+r,cy,cx,cy+r,cx-r,cy};
            case TRIANGLE:return new float[]{cx,cy-r,cx+r*.8660254f,cy+r*.5f,cx-r*.8660254f,cy+r*.5f};
            case STAR:{float[] points=new float[20];for(int i=0;i<10;i++){
                double a=-Math.PI/2+i*Math.PI/5;float rad=(i%2==0?r:r*.48f);
                points[2*i]=cx+rad*(float)Math.cos(a);points[2*i+1]=cy+rad*(float)Math.sin(a);
            }return points;}
            case CORNER:{float e=Math.max(r*.36f,minimumCutMm);return new float[]{cx-r,cy-r,cx+r,cy-r,cx+r,cy-r+e,cx-r+e,cy-r+e,cx-r+e,cy+r,cx-r,cy+r};}
            default:{ // cross is a SINGLE UNION polygon, not two overlapping SVG rectangles
                float e=Math.max(r*.18f,minimumCutMm*.5f);return new float[]{cx-e,cy-r,cx+e,cy-r,cx+e,cy-e,cx+r,cy-e,cx+r,cy+e,
                        cx+e,cy+e,cx+e,cy+r,cx-e,cy+r,cx-e,cy+e,cx-r,cy+e,cx-r,cy-e,cx-e,cy-e};}
        }
    }
    private static String f(float x){return String.format(Locale.US,"%.3f",x);}
    public static String svgMark(int style,float cx,float cy,float sizeMm){
        return svgMark(style,cx,cy,sizeMm,.8f);
    }
    public static String svgMark(int style,float cx,float cy,float sizeMm,float minimumCutMm){
        float[] xy=outline(style,cx,cy,sizeMm,minimumCutMm);
        if(xy==null)return "<circle cx=\""+f(cx)+"\" cy=\""+f(cy)+"\" r=\""+f(sizeMm/2f)+"\"/>\n";
        StringBuilder s=new StringBuilder("<path d=\"M ");
        for(int i=0;i<xy.length;i+=2){if(i>0)s.append(" L ");s.append(f(xy[i])).append(' ').append(f(xy[i+1]));}
        return s.append(" Z\"/>\n").toString();
    }
    public static float markRadius(float sizeMm,float clearanceMm){return checkedSize(sizeMm)/2f+Math.max(0,clearanceMm);}
    /** Default automatic layout for a genuinely circular/elliptic crop. Its
     * unused corners can hold physical marks, unlike a rectangular portrait.
     * Never replace manually dragged marks. Unsupported dimensions use the
     * unchanged outside-artwork defaults, and the final mark/cut oracle still
     * checks physical collisions on *every* sheet.
     */
    public static float[][] automaticPositions(float width,float height,float diameterMm,
            float clearanceMm,boolean circleCrop){
        if(!circleCrop)return defaultPositions(width,height,diameterMm,clearanceMm);
        float radius=markRadius(diameterMm,clearanceMm);
        float inset=radius+2f;
        if(!Float.isFinite(width)||!Float.isFinite(height)||width<4f*inset||height<4f*inset)
            return defaultPositions(width,height,diameterMm,clearanceMm);
        // Test the nearest point in the mark's conservative clearance disk
        // against a full-size ellipse, NOT against only the center point.
        // The actual crop has a further inset, so the test is conservative.
        double nx=(width*.5-inset-radius)/(width*.5);
        double ny=(height*.5-inset-radius)/(height*.5);
        if(nx*nx+ny*ny<=1.00001)
            return defaultPositions(width,height,diameterMm,clearanceMm);
        return new float[][]{{inset,inset},{width-inset,inset},
            {inset,height-inset},{width-inset,height-inset}};
    }
    /** Physical artboard for automatic circular registration. Keep the marks
     * at the same artwork-local coordinates on every color sheet. Preserve
     * every custom layout and rectangular artwork padding without modification.
     * The 11.5in limit is checked again by the Cricut output gate; this only
     * recovers unused border area, never scales an SVG.
     */
    public static Layout artworkLayout(float width,float height,float diameterMm,
            float clearanceMm,float[][] selected,float requestedPaddingMm,boolean circleCrop){
        if(selected!=null||!circleCrop)
            return layout(width,height,diameterMm,clearanceMm,selected,requestedPaddingMm);
        float[][] automatic=automaticPositions(width,height,diameterMm,clearanceMm,true);
        float[][] outside=defaultPositions(width,height,diameterMm,clearanceMm);
        if(automatic[0][0]==outside[0][0]&&automatic[0][1]==outside[0][1])
            return layout(width,height,diameterMm,clearanceMm,null,requestedPaddingMm);
        final float makerShortMm=11.5f*25.4f;
        float budget=(makerShortMm-Math.max(width,height))*.5f-.15f;
        float requested=Math.max(0f,requestedPaddingMm);
        // If artwork itself does not fit, do not invent an impossible smaller
        // artboard. Cricut preflight will give the actual width error.
        if(budget>0)requested=Math.min(requested,budget);
        return layout(width,height,diameterMm,clearanceMm,automatic,requested);
    }
    // ---- M5 saved mark-layout option -------------------------------------------------------
    /** Saved project key "markLayout". 0 = today's four-corner marks (default for every legacy project). */
    public static final int PLACEMENT_CORNERS=0;
    /** Two marks above and two below the artwork, inside its width: no side margin is needed. */
    public static final int PLACEMENT_ABOVE_BELOW=1;
    /** Unknown or out-of-range saved values fall back to the legacy corner layout. */
    public static int normalizePlacement(int placement){
        return placement==PLACEMENT_ABOVE_BELOW?PLACEMENT_ABOVE_BELOW:PLACEMENT_CORNERS;
    }
    /** Distance from the artwork edge to a compact mark's centre: its clearance disk plus 1 mm. */
    public static float compactOffsetMm(float diameterMm,float clearanceMm){return markRadius(diameterMm,clearanceMm)+1f;}
    /** Uniform sheet margin of the compact layout: offset + radius + 0.5 mm (same envelope rule as layout()). */
    public static float compactPaddingMm(float diameterMm,float clearanceMm){
        return compactOffsetMm(diameterMm,clearanceMm)+markRadius(diameterMm,clearanceMm)+.5f;
    }
    /**
     * PURE function used by the preview, Cut Check and every exporter: mark centres in artwork
     * millimetres for the compact layout. Order matches defaultPositions (TL, TR, BL, BR). Marks sit
     * above (y &lt; 0) and below (y &gt; height) the artwork and horizontally inside [0,width], so
     * they never overlap the artwork and never widen the sheet. Independent of zoom/pan.
     */
    public static float[][] aboveBelowPositions(float width,float height,float diameterMm,float clearanceMm){
        float r=markRadius(diameterMm,clearanceMm),off=compactOffsetMm(diameterMm,clearanceMm);
        float inset=r+1f;
        return new float[][]{{inset,-off},{width-inset,-off},{inset,height+off},{width-inset,height+off}};
    }
    /** True when the four marks equal the compact layout for this artwork (tolerance 0.001 mm). */
    public static boolean isAboveBelow(float[][] marks,float width,float height,float diameterMm,float clearanceMm){
        if(marks==null||marks.length!=4)return false;
        float[][] want=aboveBelowPositions(width,height,diameterMm,clearanceMm);
        for(int i=0;i<4;i++){
            if(marks[i]==null||marks[i].length!=2)return false;
            if(!(Math.abs(marks[i][0]-want[i][0])<=.001f&&Math.abs(marks[i][1]-want[i][1])<=.001f))return false;
        }
        return true;
    }
    /** Bottom-row tolerance for {@link #isAboveBelowNear}: artwork-height disagreement between photo aspect and prepared raster. */
    public static final float COMPACT_HEIGHT_TOLERANCE_MM=1f;
    /**
     * True when the marks are an automatically placed compact set computed for an artwork whose
     * height differs by at most {@link #COMPACT_HEIGHT_TOLERANCE_MM}: x of all four and y of the top
     * pair match exactly (0.001 mm), the two bottom marks share one y within the tolerance.
     */
    public static boolean isAboveBelowNear(float[][] marks,float width,float height,float diameterMm,float clearanceMm){
        if(marks==null||marks.length!=4)return false;
        float[][] want=aboveBelowPositions(width,height,diameterMm,clearanceMm);
        for(int i=0;i<4;i++){
            if(marks[i]==null||marks[i].length!=2||!Float.isFinite(marks[i][0])||!Float.isFinite(marks[i][1]))return false;
            if(Math.abs(marks[i][0]-want[i][0])>.001f)return false;
            if(i<2&&Math.abs(marks[i][1]-want[i][1])>.001f)return false;
        }
        return Math.abs(marks[2][1]-marks[3][1])<=.001f&&Math.abs(marks[2][1]-want[2][1])<=COMPACT_HEIGHT_TOLERANCE_MM;
    }
    /** Mark centres for a saved placement. A circle crop only affects the CORNERS placement. */
    public static float[][] positionsFor(int placement,float width,float height,float diameterMm,
            float clearanceMm,boolean circleCrop){
        if(normalizePlacement(placement)==PLACEMENT_ABOVE_BELOW)
            return aboveBelowPositions(width,height,diameterMm,clearanceMm);
        return automaticPositions(width,height,diameterMm,clearanceMm,circleCrop);
    }
    /**
     * Restoration rule: manually placed or previously saved marks are NEVER replaced. Only when no
     * marks are stored does the saved placement choose them. Returns a copy, or null for the legacy
     * corners with no stored marks (callers keep their existing null handling).
     */
    public static float[][] resolveSaved(int placement,float[][] saved,float width,float height,
            float diameterMm,float clearanceMm,boolean circleCrop){
        if(saved!=null)return copyPositions(saved);
        if(normalizePlacement(placement)==PLACEMENT_ABOVE_BELOW)
            return aboveBelowPositions(width,height,diameterMm,clearanceMm);
        return null;
    }
    public static float[][] defaultPositions(float width,float height,float diameterMm,float clearanceMm){
        float d=Math.max(4f,markRadius(diameterMm,clearanceMm)+1f);
        return new float[][]{{-d,-d},{width+d,-d},{-d,height+d},{width+d,height+d}};
    }
}

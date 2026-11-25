package com.halftonestudio;

import java.util.Locale;
import java.util.Arrays;

/** Post-generation line-SVG review, executed off the UI thread at export.
 * This is a diagnostic, NOT a proof of manufacturability. In particular,
 * bounding-box and width checks cannot establish spacing of every nonlocal
 * shape pair. Known self-crossings are reported; unchecked geometry is named.
 */
public final class LineCutDiagnostics {
    private LineCutDiagnostics() {}
    private static final long MAX_TESTS = 6_000_000L;
    public static final class Report {
        public int ribbons, contours, subMinimumWidthSamples, selfIntersections;
        public float cutLengthMm = 0, smallestObservedWidthMm = Float.POSITIVE_INFINITY;
        public boolean complete = true;
        public long testedPairs = 0;
        public String text(float requestedMm, float weedGapMm) {
            return String.format(Locale.US,
                "Closed artwork ribbons: %d\nCutting contours: %d\nEstimated contour cut length: %.2f mm\n"+
                "Smallest sampled ribbon width: %s mm\nSamples under requested %.2f mm floor: %d\n"+
                "Confirmed self-crossing edge pairs: %d\nSelf-intersection check: %s (%d comparisons)\n"+
                "Requested minimum gap: %.2f mm\n"+
                "IMPORTANT: This check does not prove minimum gap between separate polygons, minimum neck after cutting, " +
                "or physical weedability. Inspect the actual SVG and perform a test cut.\n",
                ribbons, contours, cutLengthMm,
                Float.isFinite(smallestObservedWidthMm)
                    ? String.format(Locale.US,"%.3f",smallestObservedWidthMm) : "not applicable",
                requestedMm, subMinimumWidthSamples, selfIntersections,
                complete ? "completed for polygon self-crossings" : "INCOMPLETE — polygon complexity budget exceeded",
                testedPairs, weedGapMm);
        }
    }
    public static Report inspect(VinylEngine.Result result) {
        Report out = new Report();
        if(result == null) throw new IllegalArgumentException("Missing SVG geometry");
        for(int li=0;li<result.shapes.size();li++) {
            if(!VinylEngine.cutLayer(result, li))continue;
            VinylEngine.Layer layer=result.settings.layers.get(li);
            int style=layer.lineStyleOverride>=0?layer.lineStyleOverride:result.settings.pattern;
            if(!LineArtEngine.isLineStyle(style))continue;
            boolean ribbonStyle = style!=VinylEngine.RINGS && style!=VinylEngine.RAYS && style!=VinylEngine.BEADED;
            for(VinylEngine.Shape shape:result.shapes.get(li)) {
                out.ribbons++;
                if(shape.circle){out.contours++;out.cutLengthMm+=2f*(float)Math.PI*shape.r;continue;}
                float[][] rings=shape.contours==null?new float[][]{shape.xy}:shape.contours;
                for(float[] ring:rings){
                    if(ring==null || ring.length<6 || (ring.length&1)!=0) {
                        out.selfIntersections++;continue;
                    }
                    out.contours++;
                    int n=ring.length/2;
                    for(int i=0;i<n;i++){
                        int j=(i+1)%n;
                        out.cutLengthMm+=(float)Math.hypot(ring[i*2]-ring[j*2],ring[i*2+1]-ring[j*2+1]);
                    }
                    if(ribbonStyle && n>=4 && (n&1)==0) {
                        int samples=n/2;
                        for(int i=0;i<samples;i++){
                            int j=n-1-i;
                            float width=(float)Math.hypot(ring[2*i]-ring[2*j],ring[2*i+1]-ring[2*j+1]);
                            out.smallestObservedWidthMm=Math.min(out.smallestObservedWidthMm,width);
                            if(width+.003f<result.settings.minimumMm)out.subMinimumWidthSamples++;
                        }
                    }
                    // Sweep by segment x-bounds before testing precise crossings.
                    // Long portrait ribbons can have thousands of vertices, but
                    // only nearby edge boxes can possibly intersect. The old
                    // nested all-pairs loop exhausted the diagnostic budget on
                    // perfectly valid, dense line art.
                    Edge[] sorted=new Edge[n];
                    boolean bad=false;
                    for(int i=0;i<n;i++){
                        int j=(i+1)%n;
                        float ax=ring[2*i],ay=ring[2*i+1],bx=ring[2*j],by=ring[2*j+1];
                        if(!finite(ax,ay,bx,by)){bad=true;break;}
                        sorted[i]=new Edge(i,ax,ay,bx,by);
                    }
                    if(bad){out.selfIntersections++;continue;}
                    Arrays.sort(sorted,(a,b)->Float.compare(a.minX,b.minX));
                    for(int i=0;i<n&&out.complete;i++){
                        Edge a=sorted[i];
                        for(int j=i+1;j<n;j++){
                            Edge b=sorted[j];
                            if(b.minX>a.maxX)break;
                            if(a.maxY<b.minY || b.maxY<a.minY)continue;
                            int delta=Math.abs(a.index-b.index);
                            if(delta==1||delta==n-1)continue;
                            if(++out.testedPairs>MAX_TESTS){out.complete=false;break;}
                            if((out.testedPairs&32767)==0&&CancellationCheckpoint.shouldCancel())
                                throw new java.util.concurrent.CancellationException();
                            if(intersects(a.ax,a.ay,a.bx,a.by,b.ax,b.ay,b.bx,b.by))
                                out.selfIntersections++;
                        }
                    }
                }
            }
        }
        return out;
    }
    private static final class Edge {
        final int index;
        final float ax,ay,bx,by,minX,maxX,minY,maxY;
        Edge(int index,float ax,float ay,float bx,float by){
            this.index=index;this.ax=ax;this.ay=ay;this.bx=bx;this.by=by;
            minX=Math.min(ax,bx);maxX=Math.max(ax,bx);
            minY=Math.min(ay,by);maxY=Math.max(ay,by);
        }
    }
    private static boolean finite(float... a){for(float v:a)if(!Float.isFinite(v))return false;return true;}
    private static double cross(float ax,float ay,float bx,float by,float cx,float cy){
        return (double)(bx-ax)*(cy-ay)-(double)(by-ay)*(cx-ax);
    }
    private static boolean intersects(float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy){
        double x=cross(ax,ay,bx,by,cx,cy), y=cross(ax,ay,bx,by,dx,dy);
        double z=cross(cx,cy,dx,dy,ax,ay), w=cross(cx,cy,dx,dy,bx,by);
        // Collinear touching and tangent endpoints require a separate
        // tolerance-aware CAD check; don't mislabel them as a proper crossing.
        return x*y < -1e-12 && z*w < -1e-12;
    }
}

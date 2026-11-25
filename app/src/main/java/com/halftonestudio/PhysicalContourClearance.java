package com.halftonestudio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Host-testable physical cut-path clearance validator. Coordinates of all
 * piece polygons are source pixels, converted to millimeters by mmPerPx.
 * Narrow shape interiors require the separate material-width check. */
public final class PhysicalContourClearance {
    private PhysicalContourClearance() {}
    private static final class Bounds {
        final LayeredCellPortraitEngine.Piece piece;
        final float l,t,r,b;
        Bounds(LayeredCellPortraitEngine.Piece p){
            piece=p;
            float a=Float.POSITIVE_INFINITY,c=Float.POSITIVE_INFINITY;
            float z=Float.NEGATIVE_INFINITY,d=Float.NEGATIVE_INFINITY;
            for(int j=0;j<p.outer.length;j+=2){
                a=Math.min(a,p.outer[j]);z=Math.max(z,p.outer[j]);
                c=Math.min(c,p.outer[j+1]);d=Math.max(d,p.outer[j+1]);
            }
            l=a;t=c;r=z;b=d;
        }
    }
    private static float sq(float x){return x*x;}
    private static float segmentPointDistanceSquared(float ax,float ay,float bx,float by,float px,float py){
        float dx=bx-ax,dy=by-ay;
        float denom=dx*dx+dy*dy;
        float u=denom>0f?Math.max(0f,Math.min(1f,((px-ax)*dx+(py-ay)*dy)/denom)):0f;
        return sq(px-ax-u*dx)+sq(py-ay-u*dy);
    }
    private static float cross(float ax,float ay,float bx,float by,float cx,float cy){
        return (bx-ax)*(cy-ay)-(by-ay)*(cx-ax);
    }
    private static boolean intersect(float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy){
        float abC=cross(ax,ay,bx,by,cx,cy),abD=cross(ax,ay,bx,by,dx,dy);
        float cdA=cross(cx,cy,dx,dy,ax,ay),cdB=cross(cx,cy,dx,dy,bx,by);
        return (abC>0&&abD<0||abC<0&&abD>0)&&
            (cdA>0&&cdB<0||cdA<0&&cdB>0);
    }
    /** Exact distance for piecewise linear contours, never just vertex-to-vertex. */
    public static float minDistance(float[] a,float[] b){
        float min=Float.POSITIVE_INFINITY;
        for(int i=0;i<a.length;i+=2){
            int ii=(i+2)%a.length;
            float ax=a[i],ay=a[i+1],bx=a[ii],by=a[ii+1];
            for(int j=0;j<b.length;j+=2){
                int jj=(j+2)%b.length;
                float cx=b[j],cy=b[j+1],dx=b[jj],dy=b[jj+1];
                if(intersect(ax,ay,bx,by,cx,cy,dx,dy))return 0f;
                float dist=Math.min(Math.min(segmentPointDistanceSquared(ax,ay,bx,by,cx,cy),
                        segmentPointDistanceSquared(ax,ay,bx,by,dx,dy)),
                        Math.min(segmentPointDistanceSquared(cx,cy,dx,dy,ax,ay),
                        segmentPointDistanceSquared(cx,cy,dx,dy,bx,by)));
                min=Math.min(min,dist);
            }
        }
        return (float)Math.sqrt(min);
    }
    /** Check only physical sheet peers. An opaque upper sheet may overlap a
     * different lower sheet, but a single Cricut cut sheet cannot overlap itself. */
    public static void requireLayerClearance(LayeredCellPortraitEngine.Result result,int layer){
        if(!result.options.fishScales && result.options.overprintKind==LayeredCellPortraitEngine.OVERPRINT_NONE)return;
        // The single solid silhouette can legitimately contain adjacent mask
        // islands. Overlay marks, by contrast, must remain separate per sheet.
        if(result.options.overprintKind!=LayeredCellPortraitEngine.OVERPRINT_NONE&&layer==0)return;
        float gapPx=result.options.gapMm*result.width/result.options.widthMm;
        if(gapPx<=0f)return;
        // Permit 0.004 mm numerical tolerance for rounded 0.001mm SVG coords.
        float needed=Math.max(0,gapPx-0.004f*result.width/result.options.widthMm);
        List<Bounds> bounds=new ArrayList<>();
        for(LayeredCellPortraitEngine.Piece p:result.pieces)
            if(p.layer==layer)bounds.add(new Bounds(p));
        bounds.sort(Comparator.comparingDouble(a->a.l));
        for(int i=0;i<bounds.size();i++){
            if((i&31)==0)StyleKit.checkCancelled();
            Bounds a=bounds.get(i);
            for(int j=i+1;j<bounds.size();j++){
                Bounds b=bounds.get(j);
                if(b.l>a.r+needed)break;
                if(b.b<a.t-needed || b.t>a.b+needed)continue;
                float actual=minDistance(a.piece.outer,b.piece.outer);
                if(actual<needed ||
                   a.piece.contains(b.piece.outer[0],b.piece.outer[1]) ||
                   b.piece.contains(a.piece.outer[0],a.piece.outer[1]))
                    throw new IllegalArgumentException(
                    "Same-sheet cell gap below configured minimum on sheet "+(layer+1)+
                    ": "+(actual*result.options.widthMm/result.width)+" mm < "+result.options.gapMm+" mm, cells "+a.piece.cell+" / "+b.piece.cell+" bounds ["+a.l+","+a.t+","+a.r+","+a.b+"] vs ["+b.l+","+b.t+","+b.r+","+b.b+"]");
            }
        }
    }
}

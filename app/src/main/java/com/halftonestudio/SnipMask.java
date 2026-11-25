package com.halftonestudio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Nondestructive polygon snipping in normalized, cropped-photo coordinates.
 * This class is Android-free so source and Cricut output can be tested identically.
 * Each operation either removes a polygon's interior or keeps only its interior.
 * Operations apply in order to ALL color layers; registration marks are excluded.
 *
 * Export intentionally contains no SVG clipPath/mask elements: those aren't
 * supported by Cricut Design Space. VinylEngine omits or separates generated
 * solid geometry before serializing ordinary closed SVG paths.
 */
public final class SnipMask {
    public static final int REMOVE_INSIDE=0, KEEP_INSIDE=1;
    private static final double EPS=1e-7;
    public static final int MAX_OPERATIONS=64;
    public static final int MAX_VERTICES=256;

    public static final class Operation {
        public final int kind;
        /** Interleaved x,y coordinates, normalized to [0,1]. */
        public final float[] xy;
        // Immutable normalized bounds. Recomputing these for every ribbon
        // sample previously scanned up to 256 vertices per Snip operation.
        final float minX, minY, maxX, maxY;
        public Operation(int kind,float... coordinates){
            if(kind!=REMOVE_INSIDE && kind!=KEEP_INSIDE)throw new IllegalArgumentException("Unknown snip operation");
            if(coordinates==null||coordinates.length<6||coordinates.length>MAX_VERTICES*2||(coordinates.length&1)!=0)
                throw new IllegalArgumentException("A snip requires 3–256 points");
            xy=coordinates.clone();this.kind=kind;
            for(int i=0;i<xy.length;i++)if(!Float.isFinite(xy[i])||xy[i]<0||xy[i]>1)
                throw new IllegalArgumentException("Snip point must remain on the photo");
            float loX=Float.POSITIVE_INFINITY,loY=Float.POSITIVE_INFINITY;
            float hiX=Float.NEGATIVE_INFINITY,hiY=Float.NEGATIVE_INFINITY;
            for(int i=0;i<xy.length;i+=2){
                loX=Math.min(loX,xy[i]);hiX=Math.max(hiX,xy[i]);
                loY=Math.min(loY,xy[i+1]);hiY=Math.max(hiY,xy[i+1]);
            }
            minX=loX;minY=loY;maxX=hiX;maxY=hiY;
            int n=xy.length/2;double area=0;
            for(int i=0;i<n;i++){
                int j=(i+1)%n;
                area+=(double)xy[2*i]*xy[2*j+1]-(double)xy[2*j]*xy[2*i+1];
                if(Math.hypot(xy[2*i]-xy[2*j],xy[2*i+1]-xy[2*j+1])<1e-5)
                    throw new IllegalArgumentException("Snip has identical neighboring points");
            }
            if(Math.abs(area)<1e-6)throw new IllegalArgumentException("Snip area is too small");
            for(int a=0;a<n;a++)for(int b=a+1;b<n;b++){
                if(b==a+1||(a==0&&b==n-1))continue;
                int ai=(a+1)%n,bi=(b+1)%n;
                if(segmentsCross(xy[2*a],xy[2*a+1],xy[2*ai],xy[2*ai+1],
                        xy[2*b],xy[2*b+1],xy[2*bi],xy[2*bi+1]))
                    throw new IllegalArgumentException("Snip outline crosses itself; move a point");
            }
        }
        Operation copy(){return new Operation(kind,xy);}
    }
    private final ArrayList<Operation> operations=new ArrayList<>();
    public SnipMask(){}
    public SnipMask copy(){SnipMask s=new SnipMask();for(Operation op:operations)s.operations.add(op.copy());return s;}
    public boolean isEmpty(){return operations.isEmpty();}
    public int size(){return operations.size();}
    public List<Operation> operations(){return Collections.unmodifiableList(operations);}
    public void add(Operation op){if(operations.size()>=MAX_OPERATIONS)throw new IllegalArgumentException("Maximum 64 snips per project");operations.add(op.copy());}
    /** Edit and reinsert at the original position so keep/remove ordering persists. */
    public Operation removeAt(int index){return operations.remove(index).copy();}
    public void insert(int index,Operation op){
        if(operations.size()>=MAX_OPERATIONS)throw new IllegalArgumentException("Maximum 64 snips per project");
        operations.add(index,op.copy());
    }
    public void removeLast(){if(!operations.isEmpty())operations.remove(operations.size()-1);}
    public void clear(){operations.clear();}

    public boolean allows(float nx,float ny){
        // Comparisons with NaN are always false.  Reject non-finite points
        // *before* the empty/removal-only fast path can implicitly allow them.
        if(!Float.isFinite(nx)||!Float.isFinite(ny)||nx<0||nx>1||ny<0||ny>1)return false;
        for(Operation op:operations){
            // A point outside a polygon's expanded bounding box cannot be
            // inside it. EPS padding retains the original boundary-touch rule.
            boolean inBounds=nx>=op.minX-EPS&&nx<=op.maxX+EPS&&
                ny>=op.minY-EPS&&ny<=op.maxY+EPS;
            boolean inside=inBounds&&contains(op.xy,nx,ny);
            if((op.kind==REMOVE_INSIDE&&inside)||(op.kind==KEEP_INSIDE&&!inside))return false;
        }
        return true;
    }
    /** Conservative physical distance to any snip boundary. A shape of radius r
     * is safe only when its center is allowed and its r-neighborhood is inside.
     */
    public float clearanceMm(float xMm,float yMm,float wMm,float hMm){
        if(!Float.isFinite(xMm)||!Float.isFinite(yMm)||!Float.isFinite(wMm)||
           !Float.isFinite(hMm)||wMm<=0||hMm<=0)return 0;
        if(isEmpty())return Float.POSITIVE_INFINITY;
        if(!allows(xMm/wMm,yMm/hMm))return 0;
        float nearest=Float.POSITIVE_INFINITY;
        for(Operation op:operations){float[] p=op.xy;int n=p.length/2;
            for(int j=0;j<n;j++){
                int k=(j+1)%n;
                float dist=segmentDistance(xMm,yMm,p[j*2]*wMm,p[j*2+1]*hMm,p[k*2]*wMm,p[k*2+1]*hMm);
                nearest=Math.min(nearest,dist);
            }
        }
        return nearest;
    }
    /** Fast no-clipping threshold used at thousands of ribbon samples. */
    public float clearanceWithinMm(float xMm,float yMm,float wMm,float hMm,float relevantMm){
        if(!Float.isFinite(xMm)||!Float.isFinite(yMm)||!Float.isFinite(wMm)||
           !Float.isFinite(hMm)||!Float.isFinite(relevantMm)||
           wMm<=0||hMm<=0||relevantMm<0)return 0;
        if(isEmpty())return Float.POSITIVE_INFINITY;
        if(!allows(xMm/wMm,yMm/hMm))return 0;
        float nearest=relevantMm+1;
        for(Operation op:operations){float[] p=op.xy;int n=p.length/2;
            float minX=op.minX*wMm,maxX=op.maxX*wMm;
            float minY=op.minY*hMm,maxY=op.maxY*hMm;
            float dx=Math.max(Math.max(minX-xMm,xMm-maxX),0),dy=Math.max(Math.max(minY-yMm,yMm-maxY),0);
            if(dx*dx+dy*dy>relevantMm*relevantMm)continue;
            for(int j=0;j<n;j++){int k=(j+1)%n;
                nearest=Math.min(nearest,segmentDistance(xMm,yMm,p[j*2]*wMm,p[j*2+1]*hMm,p[k*2]*wMm,p[k*2+1]*hMm));
            }
        }
        return nearest;
    }
    public boolean safeSegment(float ax,float ay,float bx,float by,float wMm,float hMm){
        if(!Float.isFinite(ax)||!Float.isFinite(ay)||!Float.isFinite(bx)||
           !Float.isFinite(by)||!Float.isFinite(wMm)||!Float.isFinite(hMm)||
           wMm<=0||hMm<=0)return false;
        if(isEmpty())return true;
        if(!allows(ax/wMm,ay/hMm)||!allows(bx/wMm,by/hMm))return false;
        for(Operation op:operations)if(intersectsBoundary(op.xy,ax/wMm,ay/hMm,bx/wMm,by/hMm))return false;
        return true;
    }
    /** Exact conservative intersection predicate: never exports an uncut SVG
     * polygon that intrudes into any removed region or exits a keep region.
     * Boundary-touching subjects are rejected rather than risk a stray cut.
     */
    public boolean allowsPolygon(float[] subject,float wMm,float hMm){
        if(subject==null||subject.length<6||(subject.length&1)!=0||
           !Float.isFinite(wMm)||!Float.isFinite(hMm)||wMm<=0||hMm<=0)return false;
        for(float coordinate:subject)if(!Float.isFinite(coordinate))return false;
        if(isEmpty())return true;
        int n=subject.length/2;
        for(int i=0;i<n;i++)if(!allows(subject[2*i]/wMm,subject[2*i+1]/hMm))return false;
        for(Operation op:operations){
            int m=op.xy.length/2;
            for(int i=0;i<n;i++){
                int j=(i+1)%n;
                if(intersectsBoundary(op.xy,subject[2*i]/wMm,subject[2*i+1]/hMm,
                        subject[2*j]/wMm,subject[2*j+1]/hMm))return false;
            }
            // A removed polygon can be entirely enclosed by a generated large
            // cut shape without crossing its boundary. Reject such a shape.
            if(op.kind==REMOVE_INSIDE)for(int k=0;k<m;k++){
                float qx=op.xy[2*k]*wMm,qy=op.xy[2*k+1]*hMm;
                if(contains(subject,qx,qy))return false;
            }
        }
        return true;
    }
    public boolean allowsCircle(float x,float y,float radius,float wMm,float hMm){
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(radius)||
           !Float.isFinite(wMm)||!Float.isFinite(hMm)||
           radius<0||wMm<=0||hMm<=0)return false;
        if(isEmpty())return true;
        // Safe, complete disk instead of testing an inscribed approximation.
        return clearanceWithinMm(x,y,wMm,hMm,radius+.08f)>radius+.05f;
    }
    private static boolean contains(float[] polygon,float x,float y){
        int n=polygon.length/2;boolean result=false;
        for(int i=0,j=n-1;i<n;j=i++){
            float xi=polygon[2*i],yi=polygon[2*i+1],xj=polygon[2*j],yj=polygon[2*j+1];
            if(segmentDistance(x,y,xi,yi,xj,yj)<EPS)return true;
            if((yi>y)!=(yj>y) && x<(xj-xi)*(y-yi)/(yj-yi)+xi)result=!result;
        }
        return result;
    }
    private static boolean intersectsBoundary(float[] polygon,float ax,float ay,float bx,float by){
        int n=polygon.length/2;
        for(int i=0;i<n;i++){int j=(i+1)%n;
            if(segmentsCross(ax,ay,bx,by,polygon[2*i],polygon[2*i+1],polygon[2*j],polygon[2*j+1]))return true;
        }return false;
    }
    private static boolean segmentsCross(double ax,double ay,double bx,double by,double cx,double cy,double dx,double dy){
        double a=orient(ax,ay,bx,by,cx,cy),b=orient(ax,ay,bx,by,dx,dy);
        double c=orient(cx,cy,dx,dy,ax,ay),d=orient(cx,cy,dx,dy,bx,by);
        if(((a>EPS&&b<-EPS)||(a<-EPS&&b>EPS))&&((c>EPS&&d<-EPS)||(c<-EPS&&d>EPS)))return true;
        if(Math.abs(a)<EPS&&onSegment(ax,ay,bx,by,cx,cy))return true;
        if(Math.abs(b)<EPS&&onSegment(ax,ay,bx,by,dx,dy))return true;
        if(Math.abs(c)<EPS&&onSegment(cx,cy,dx,dy,ax,ay))return true;
        if(Math.abs(d)<EPS&&onSegment(cx,cy,dx,dy,bx,by))return true;
        return false;
    }
    private static double orient(double ax,double ay,double bx,double by,double px,double py){return (bx-ax)*(py-ay)-(by-ay)*(px-ax);}
    private static boolean onSegment(double ax,double ay,double bx,double by,double x,double y){return x>=Math.min(ax,bx)-EPS&&x<=Math.max(ax,bx)+EPS&&y>=Math.min(ay,by)-EPS&&y<=Math.max(ay,by)+EPS;}
    private static float segmentDistance(float px,float py,float ax,float ay,float bx,float by){
        float dx=bx-ax,dy=by-ay,len=dx*dx+dy*dy;
        if(len<1e-12f)return (float)Math.hypot(px-ax,py-ay);
        float t=Math.max(0,Math.min(1,((px-ax)*dx+(py-ay)*dy)/len));
        return (float)Math.hypot(px-(ax+t*dx),py-(ay+t*dy));
    }
}

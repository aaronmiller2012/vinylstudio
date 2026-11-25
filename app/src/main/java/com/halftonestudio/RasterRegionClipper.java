package com.halftonestudio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Pure-Java clip of an analytic filled region to an arbitrary source-pixel mask.
 * Used ONLY for partly masked cells: fully visible cells preserve smooth polygons.
 * Traces square pixel boundaries as explicit SVG-ready contour loops; no SVG clipPath.
 * Coordinates of traced boundaries correspond to boundaries of the source pixels.
 */
final class RasterRegionClipper {
    static final class Region {
        final float[] outer;
        final List<float[]> holes;
        Region(float[] outer, List<float[]> holes) {this.outer=outer;this.holes=holes;}
    }
    private static final class Edge {
        final int x0,y0,x1,y1,dir;
        boolean used;
        Edge(int a,int b,int c,int d,int orientation){x0=a;y0=b;x1=c;y1=d;dir=orientation;}
    }
    private static final class Loop {
        final float[] xy;
        final double area;
        Loop(float[] xy){this.xy=xy;this.area=area(xy);}
    }
    private static long key(int x,int y){return ((long)y<<32)|(x&0xffffffffL);}
    private static double area(float[] p){double a=0;for(int i=0;i<p.length;i+=2){int j=(i+2)%p.length;a+=(double)p[i]*p[j+1]-(double)p[j]*p[i+1];}return a*.5;}
    private static boolean contains(float[] p,float x,float y){boolean odd=false;int n=p.length/2;
        for(int i=0,j=n-1;i<n;j=i++){
            float xi=p[i*2],yi=p[i*2+1],xj=p[j*2],yj=p[j*2+1];
            if((yi>y)!=(yj>y)&&x<(xj-xi)*(y-yi)/(yj-yi)+xi)odd=!odd;
        }return odd;
    }
    private static boolean allowed(int[] pixels,boolean[] mask,int w,int x,int y){int idx=y*w+x;
        return (pixels[idx]>>>24)>=128&&(mask==null||mask[idx]);
    }
    private static boolean cell(boolean[] a,int ww,int hh,int x,int y){return x>=0&&y>=0&&x<ww&&y<hh&&a[y*ww+x];}
    /** Binary opening removes one-pixel islands, narrow isthmuses and needle-like clipped fragments. */
    private static boolean[] removeNarrow(boolean[] initial,int width,int height,int radius){
        if(radius<=0)return initial;
        boolean[] core=new boolean[initial.length];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)if(cell(initial,width,height,x,y)){
            boolean ok=true;
            for(int dy=-radius;dy<=radius&&ok;dy++)for(int dx=-radius;dx<=radius;dx++){
                if(dx*dx+dy*dy<=radius*radius&&!cell(initial,width,height,x+dx,y+dy)){ok=false;break;}
            }
            core[y*width+x]=ok;
        }
        boolean[] result=new boolean[initial.length];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)if(core[y*width+x]){
            for(int dy=-radius;dy<=radius;dy++)for(int dx=-radius;dx<=radius;dx++){
                int xx=x+dx,yy=y+dy;
                if(dx*dx+dy*dy<=radius*radius&&cell(initial,width,height,xx,yy))result[yy*width+xx]=true;
            }
        }
        return result;
    }
    private static void add(HashMap<Long,ArrayList<Edge>> starts,List<Edge> all,int x0,int y0,int x1,int y1,int dir){
        Edge e=new Edge(x0,y0,x1,y1,dir);all.add(e);
        starts.computeIfAbsent(key(x0,y0),k->new ArrayList<>(2)).add(e);
    }
    private static float[] bound(float[] points,float x0,float y0,float x1,float y1){
        ArrayList<float[]> unique=new ArrayList<>();
        for(int i=0;i<points.length;i+=2){
            float x=Math.min(x1,Math.max(x0,points[i]));
            float y=Math.min(y1,Math.max(y0,points[i+1]));
            if(!unique.isEmpty()){
                float[] prev=unique.get(unique.size()-1);
                if(Math.abs(prev[0]-x)<1e-5&&Math.abs(prev[1]-y)<1e-5)continue;
            }
            unique.add(new float[]{x,y});
        }
        if(unique.size()>1){float[] a=unique.get(0),b=unique.get(unique.size()-1);
            if(a[0]==b[0]&&a[1]==b[1])unique.remove(unique.size()-1);
        }
        if(unique.size()<3)return new float[0];
        ArrayList<Float> clean=new ArrayList<>();
        int n=unique.size();
        for(int i=0;i<n;i++){
            float[] a=unique.get((i+n-1)%n),b=unique.get(i),c=unique.get((i+1)%n);
            double cross=(b[0]-a[0])*(c[1]-b[1])-(b[1]-a[1])*(c[0]-b[0]);
            if(Math.abs(cross)<1e-6)continue;
            clean.add(b[0]);clean.add(b[1]);
        }
        float[] result=new float[clean.size()];for(int i=0;i<result.length;i++)result[i]=clean.get(i);
        return result;
    }
    private static float[] simplify(ArrayList<int[]> pts){
        // Diagonal-touch ambiguities are routed by turn priority; collinear edges collapse.
        ArrayList<Integer> coords=new ArrayList<>();int n=pts.size();
        for(int i=0;i<n;i++){
            int[] a=pts.get((i+n-1)%n),b=pts.get(i),c=pts.get((i+1)%n);
            if((b[0]-a[0])*(c[1]-b[1])!=(b[1]-a[1])*(c[0]-b[0])){coords.add(b[0]);coords.add(b[1]);}
        }
        float[] out=new float[coords.size()];for(int i=0;i<out.length;i++)out[i]=coords.get(i);return out;
    }
    static List<Region> clip(float[] outer,float[] hole,int[] source,boolean[] mask,int w,int h,
                             int xStart,int yStart,int xEnd,int yEnd,float cellX0,float cellY0,float cellX1,float cellY1,
                             float mmPerPixel,float minFeatureMm){
        return clipInternal(outer,hole,source,mask,w,h,xStart,yStart,xEnd,yEnd,
            cellX0,cellY0,cellX1,cellY1,mmPerPixel,minFeatureMm,false);
    }
    /** Only Fish Scale uses conservative raster opening. Other existing
     * portrait styles retain their previous clipping and golden geometry. */
    static List<Region> clipFishScale(float[] outer,float[] hole,int[] source,boolean[] mask,int w,int h,
                             int xStart,int yStart,int xEnd,int yEnd,float cellX0,float cellY0,float cellX1,float cellY1,
                             float mmPerPixel,float minFeatureMm){
        return clipInternal(outer,hole,source,mask,w,h,xStart,yStart,xEnd,yEnd,
            cellX0,cellY0,cellX1,cellY1,mmPerPixel,minFeatureMm,true);
    }
    private static List<Region> clipInternal(float[] outer,float[] hole,int[] source,boolean[] mask,int w,int h,
                             int xStart,int yStart,int xEnd,int yEnd,float cellX0,float cellY0,float cellX1,float cellY1,
                             float mmPerPixel,float minFeatureMm,boolean strictWidth){
        // Project imports and specialty callers must not be able to request an
        // out-of-range or oversized pixel trace. Check before array allocation,
        // not after the first attempted source/mask read.
        if(w<=0||h<=0||source==null||(long)w*h!=source.length ||
           (mask!=null&&mask.length!=source.length) ||
           xStart<0||yStart<0||xEnd>w||yEnd>h||xEnd<xStart||yEnd<yStart ||
           !Float.isFinite(cellX0)||!Float.isFinite(cellY0)||
           !Float.isFinite(cellX1)||!Float.isFinite(cellY1)||
           cellX1<=cellX0||cellY1<=cellY0 ||
           !Float.isFinite(mmPerPixel)||mmPerPixel<=0 ||
           !Float.isFinite(minFeatureMm)||minFeatureMm<0)
            throw new IllegalArgumentException("Invalid Snip raster region or physical pixel scale");
        if(outer==null||outer.length<6||(outer.length&1)!=0 ||
           (hole!=null&&(hole.length<6||(hole.length&1)!=0)))
            throw new IllegalArgumentException("Invalid Snip outline");
        for(float n:outer)if(!Float.isFinite(n))throw new IllegalArgumentException("Nonfinite Snip outline");
        if(hole!=null)for(float n:hole)if(!Float.isFinite(n))throw new IllegalArgumentException("Nonfinite Snip hole");
        int ww=xEnd-xStart,hh=yEnd-yStart;
        if(ww<=0||hh<=0)return new ArrayList<>();
        boolean[] pixels=new boolean[ww*hh];boolean any=false;
        for(int y=0;y<hh;y++)for(int x=0;x<ww;x++){
            int sx=x+xStart,sy=y+yStart;
            if(sx<0||sy<0||sx>=w||sy>=h||!allowed(source,mask,w,sx,sy))continue;
            // The cell's physical/fractional crop is part of the raster
            // topology, not a polygon-point clamp applied *after* tracing.
            // A pixel outside the crop can otherwise connect islands through
            // an invisible bridge and create unassigned holes at the edge.
            if(sx+1<=cellX0||sx>=cellX1||sy+1<=cellY0||sy>=cellY1)continue;
            float px=sx+.5f,py=sy+.5f;
            if(contains(outer,px,py)&&(hole==null||!contains(hole,px,py))){pixels[y*ww+x]=true;any=true;}
        }
        if(!any)return new ArrayList<>();
        // Inscribed disk diameter >= requested material feature width (conservative).
        // Pixel resolution bounds the precision of this guarantee.
        // Legacy clipping is unchanged; Fish Scale uses a stricter minimum.
        // If minFeature is subpixel we cannot certify a pixel's true width:
        // remove doubtful islands rather than permitting tiny cut fragments.
        int radius=strictWidth&&minFeatureMm>0f
            ?Math.max(1,(int)Math.ceil(minFeatureMm/(2f*mmPerPixel)))
            :(int)Math.floor(minFeatureMm/(2f*mmPerPixel));
        // The strict Fish Scale mode must never falsely certify a feature
        // larger than the maximum supported opening radius. Do not silently
        // weaken the physical minimum by clamping its morphological kernel.
        if(strictWidth&&radius>12)
            throw new IllegalArgumentException("Fish Scale Snip raster is too coarse for requested minimum width; enlarge artwork or reduce minimum cut width");
        pixels=removeNarrow(pixels,ww,hh,Math.min(12,radius));
        List<Edge> edges=new ArrayList<>();HashMap<Long,ArrayList<Edge>> starts=new HashMap<>();
        for(int y=0;y<hh;y++)for(int x=0;x<ww;x++)if(cell(pixels,ww,hh,x,y)){
            int a=x+xStart,b=y+yStart;
            if(!cell(pixels,ww,hh,x,y-1))add(starts,edges,a,b,a+1,b,0); // top: east
            if(!cell(pixels,ww,hh,x+1,y))add(starts,edges,a+1,b,a+1,b+1,1); // right: south
            if(!cell(pixels,ww,hh,x,y+1))add(starts,edges,a+1,b+1,a,b+1,2); // bottom: west
            if(!cell(pixels,ww,hh,x-1,y))add(starts,edges,a,b+1,a,b,3); // left: north
        }
        List<Loop> outside=new ArrayList<>(),holes=new ArrayList<>();
        for(Edge start:edges){
            if(start.used)continue;
            Edge e=start;ArrayList<int[]> corners=new ArrayList<>();int steps=0;
            do{
                if(e.used)throw new IllegalStateException("Self-touching raster contour");
                e.used=true;corners.add(new int[]{e.x0,e.y0});
                ArrayList<Edge> successors=starts.get(key(e.x1,e.y1));
                Edge best=null;int rank=-1;
                if(successors!=null)for(Edge next:successors)if(!next.used||next==start){
                    int turn=(next.dir-e.dir+4)%4;
                    int priority=turn==1?4:turn==0?3:turn==3?2:1; // keep filled region to right
                    if(priority>rank){rank=priority;best=next;}
                }
                if(best==null)throw new IllegalStateException("Open raster outline at "+e.x1+","+e.y1);
                e=best;
                if(++steps>edges.size())throw new IllegalStateException("Contour trace overflow");
            }while(e!=start);
            float[] loop=bound(simplify(corners),cellX0,cellY0,cellX1,cellY1);
            if(loop.length<6)continue;
            Loop shape=new Loop(loop);
            // Fractional boundary pixels can occupy much less than 0.5 of a
            // source pixel. Preserve them for explicit cut-safety review;
            // dropping them here silently loses details or creates holes.
            if(Math.abs(shape.area)<1e-8)continue;
            if(shape.area>0)outside.add(shape);else holes.add(shape);
        }
        ArrayList<Region> regions=new ArrayList<>();
        for(Loop exterior:outside){
            ArrayList<float[]> partHoles=new ArrayList<>();
            for(Loop interior:holes){
                // A hole boundary point is strictly inside its exterior in a valid pixel union.
                if(contains(exterior.xy,interior.xy[0],interior.xy[1]))partHoles.add(interior.xy);
            }
            regions.add(new Region(exterior.xy,partHoles));
        }
        if(!holes.isEmpty()){
            int found=0;for(Region r:regions)found+=r.holes.size();
            if(found!=holes.size())throw new IllegalStateException("Unassigned raster holes: "+holes.size()+" vs "+found);
        }
        return regions;
    }
    private RasterRegionClipper(){}
}

package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;

/** Masks planar facets against the actual (including Snip) alpha silhouette.
 * Exact source polygons are retained when wholly valid. Only partial polygons
 * are locally raster-intersected and reconstructed via subpixel contours.
 * No Android dependencies, no rectangular background lead-sheet leakage.
 * The approximation tolerance is bounded by the SOURCE pixel pitch; therefore
 * cut-safety must be checked on the final reconstructed geometry.
 */
public final class MaskedFacetClipper {
    private MaskedFacetClipper() {}
    public static ArrayList<float[]> intersect(float[] poly, StyleKit.Source src) {
        ArrayList<float[]> out=new ArrayList<>();
        if(poly==null||poly.length<6)return out;
        float pitch=src.mmPerPx;
        float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
        for(int i=0;i<poly.length;i+=2){
            minX=Math.min(minX,poly[i]);minY=Math.min(minY,poly[i+1]);
            maxX=Math.max(maxX,poly[i]);maxY=Math.max(maxY,poly[i+1]);
        }
        int x0=Math.max(0,(int)Math.floor(minX/pitch)-2),y0=Math.max(0,(int)Math.floor(minY/pitch)-2);
        int x1=Math.min(src.w-1,(int)Math.ceil(maxX/pitch)+2),y1=Math.min(src.h-1,(int)Math.ceil(maxY/pitch)+2);
        if(x1<x0||y1<y0)return out;
        int w=x1-x0+1,h=y1-y0+1,pw=w+2,ph=h+2;
        // Small-locality allocation; no per-pixel Path or Polygon objects.
        float[] occupancy=new float[pw*ph];Arrays.fill(occupancy,-1f);
        int hits=0;
        for(int y=y0;y<=y1;y++){
            if((y&31)==0)StyleKit.checkCancelled();
            for(int x=x0;x<=x1;x++){
                if(!src.valid[y*src.w+x])continue;
                if(inside(poly,(x+.5f)*pitch,(y+.5f)*pitch)){
                    occupancy[(y-y0+1)*pw+(x-x0+1)]=1f;hits++;
                }
            }
        }
        if(hits==0)return out;
        for(float[] loop:TopographicLayersEngine.contours(occupancy,pw,ph,0f,pitch)){
            if(loop.length<6)continue;
            // Topographic contour origin is at (0,0) after its one-pixel border.
            for(int j=0;j<loop.length;j+=2){loop[j]+=x0*pitch;loop[j+1]+=y0*pitch;}
            loop=StyleKit.removeCollinear(loop,Math.max(0.001f,pitch*.03f));
            if(loop.length>=6&&Math.abs(StyleKit.signedArea(loop))>=Math.max(.0002,pitch*pitch*.1))
                out.add(loop);
        }
        return out;
    }
    static boolean inside(float[] p,float x,float y){
        boolean in=false;int n=p.length/2;
        for(int i=0,j=n-1;i<n;j=i++){
            float ax=p[i*2],ay=p[i*2+1],bx=p[j*2],by=p[j*2+1];
            if((ay>y)!=(by>y)&&x<(bx-ax)*(y-ay)/(by-ay)+ax)in=!in;
        }
        return in;
    }
}

package com.halftonestudio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded, geometry-derived positions for advisory on-artwork cut markers.
 * A marker is not a proof of minimum gap or weedability; no geometry changes.
 */
public final class CutWarningLocator {
    private CutWarningLocator() {}
    public static final class Marker {
        public final int layer;public final float xMm,yMm;public final String reason;
        Marker(int layer,float x,float y,String why){this.layer=layer;xMm=x;yMm=y;reason=why;}
    }
    public static List<Marker> locate(VinylEngine.Result r,int limit){
        if(r==null||limit<1)return Collections.emptyList();
        ArrayList<Marker> found=new ArrayList<>();
        final float floor=r.settings.minimumMm;
        for(int li=0;li<r.shapes.size()&&found.size()<limit;li++){
            if(!VinylEngine.cutLayer(r,li))continue;
            VinylEngine.Layer layer=r.settings.layers.get(li);
            int style=layer.lineStyleOverride<0?r.settings.pattern:layer.lineStyleOverride;
            boolean ribbon=LineArtEngine.isLineStyle(style)&&style!=VinylEngine.BEADED&&
                style!=VinylEngine.RINGS&&style!=VinylEngine.RAYS;
            List<VinylEngine.Shape> shapes=r.shapes.get(li);
            int stride=Math.max(1,shapes.size()/Math.max(1,limit));
            for(int k=0;k<shapes.size()&&found.size()<limit;k++){
                if((k&511)==0&&Thread.currentThread().isInterrupted())return found;
                VinylEngine.Shape sh=shapes.get(k);
                if(sh.circle){
                    if(sh.r*2f+.003f<floor)
                        found.add(new Marker(li,sh.x,sh.y,"Tiny isolated cut"));
                    continue;
                }
                if(!ribbon||sh.contours!=null||sh.xy==null)continue;
                float[] xy=sh.xy;int n=xy.length/2;
                if(n<4||(n&1)!=0)continue;
                // Pairs of vertices describe the two sides of a filled ribbon.
                // Report the narrowest sampled section only; do not spam the preview.
                float best=Float.POSITIVE_INFINITY,bx=0,by=0;
                int samples=n/2;
                for(int i=0;i<samples;i+=Math.max(1,samples/64)){
                    int j=n-i-1;
                    float x1=xy[2*i],y1=xy[2*i+1],x2=xy[2*j],y2=xy[2*j+1];
                    float d=(float)Math.hypot(x1-x2,y1-y2);
                    if(d<best){best=d;bx=(x1+x2)*.5f;by=(y1+y2)*.5f;}
                }
                if(best+.003f<floor)found.add(new Marker(li,bx,by,"Ribbon narrower than material minimum"));
            }
            // An SVG over-limit is an aggregate warning. Point to the densest
            // region; individual cutter-safe shapes are not incorrectly marked bad.
            if(VinylEngine.svgElementsForLayer(r,li)>5000 && found.size()<limit){
                int grid=8;int[] bins=new int[grid*grid];
                for(int k=0;k<shapes.size();k+=stride){
                    float[] b=shapes.get(k).bounds();float x=(b[0]+b[2])*.5f,y=(b[1]+b[3])*.5f;
                    int ix=Math.min(grid-1,Math.max(0,(int)(x/r.settings.widthMm*grid)));
                    int iy=Math.min(grid-1,Math.max(0,(int)(y/r.heightMm*grid)));
                    bins[iy*grid+ix]++;
                }
                int best=0;for(int t=1;t<bins.length;t++)if(bins[t]>bins[best])best=t;
                found.add(new Marker(li,(best%grid+.5f)*r.settings.widthMm/grid,
                    (best/grid+.5f)*r.heightMm/grid,"High cut-path density"));
            }
        }
        return Collections.unmodifiableList(found);
    }
}

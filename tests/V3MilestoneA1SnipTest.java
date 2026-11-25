package com.halftonestudio;

import java.util.*;

/** Conservative host fixture for the existing pixel/Snip contour tracer.
 * This does not claim to prove subpixel visual parity on Android.
 */
public final class V3MilestoneA1SnipTest {
    private static void ok(boolean b,String s){if(!b)throw new AssertionError(s);}
    private static double area(float[] poly){return Math.abs(StyleKit.signedArea(poly));}
    private static double filledArea(List<RasterRegionClipper.Region> output){
        double count=0;
        for(RasterRegionClipper.Region region:output){
            count+=area(region.outer);
            for(float[] hole:region.holes)count-=area(hole);
        }
        return count;
    }
    private static List<RasterRegionClipper.Region> trace(int w,int h,boolean[] mask){
        int[] source=new int[w*h];Arrays.fill(source,0xff343434);
        float[] rect={0,0,w,0,w,h,0,h};
        return RasterRegionClipper.clip(rect,null,source,mask,w,h,0,0,w,h,0,0,w,h,.5f,0);
    }
    private static void valid(List<RasterRegionClipper.Region> regions,int w,int h){
        for(RasterRegionClipper.Region region:regions){
            ArrayList<float[]> loops=new ArrayList<>();loops.add(region.outer);loops.addAll(region.holes);
            for(float[] poly:loops){
                ok(poly.length>=6,"Degenerate Snip outline");
                ok(StyleKit.selfIntersections(poly,1)==0,"Self-crossing Snip outline");
                for(int j=0;j<poly.length;j+=2){
                    ok(poly[j]>=0&&poly[j]<=w&&poly[j+1]>=0&&poly[j+1]<=h,
                        "Raster contour escaped input bounds");
                }
            }
        }
    }
    public static void main(String[] args){
        int w=24,h=24;
        boolean[] donut=new boolean[w*h];
        for(int y=2;y<22;y++)for(int x=2;x<22;x++)
            if(!(x>=8&&x<16&&y>=8&&y<16))donut[y*w+x]=true;
        List<RasterRegionClipper.Region> ring=trace(w,h,donut);
        valid(ring,w,h);
        ok(ring.size()==1&&ring.get(0).holes.size()==1,"Snip hole was lost");
        ok(Math.abs(filledArea(ring)-(20*20-8*8))<.01,"Snip positive/negative area mismatch");
        boolean[] split=new boolean[w*h];
        for(int y=2;y<8;y++)for(int x=2;x<8;x++)split[y*w+x]=true;
        for(int y=14;y<20;y++)for(int x=14;x<20;x++)split[y*w+x]=true;
        List<RasterRegionClipper.Region> islands=trace(w,h,split);
        valid(islands,w,h);
        ok(islands.size()==2&&Math.abs(filledArea(islands)-72)<.01,
            "Two separate Snip regions were joined or dropped");
        // Representative diagonal boundaries / narrow holes / crop edges.
        // Only 4-connected masks are used; a diagonal single-pixel touch is
        // itself a non-manifold vinyl geometry that must be separately warned.
        int cases=0;
        for(int shift=0;shift<7;shift++)for(int width=3;width<=12;width+=3){
            boolean[] stripe=new boolean[w*h];int pixels=0;
            for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){
                if(x>=y/2+shift&&x<y/2+shift+width){stripe[y*w+x]=true;pixels++;}
            }
            List<RasterRegionClipper.Region> traced=trace(w,h,stripe);
            valid(traced,w,h);
            ok(Math.abs(filledArea(traced)-pixels)<.01,"Snip diagonal coverage changed at "+shift+"/"+width);
            cases++;
        }
        System.out.println("PASS Milestone A1 Snip: holes, disjoint islands, "+cases+" diagonal masks, area/bounds/topology parity");
    }
}

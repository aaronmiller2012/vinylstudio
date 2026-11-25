package com.halftonestudio;

import java.util.*;

/** A real filled, Snip-constrained cut sheet; not a displayed background or SVG mask. */
public final class SolidSnipBase {
    private SolidSnipBase(){}
    public static void generate(VinylEngine.Result result,int[] pixels,int w,int h,int layer){
        VinylEngine.Settings s=result.settings;
        if(!s.layers.get(layer).silhouetteBase)throw new IllegalArgumentException("Not a base layer");
        int maxSide=Math.max(w,h),rw=w,rh=h;
        if(maxSide>900){double k=900.0/maxSide;rw=Math.max(2,(int)Math.round(w*k));rh=Math.max(2,(int)Math.round(h*k));}
        int[] opaque=new int[rw*rh];Arrays.fill(opaque,0xffffffff);
        boolean[] allowed=new boolean[rw*rh];
        float width=s.widthMm,height=result.heightMm;
        float inset=Math.max(0,s.marginMm)+Math.max(.35f,Math.min(width/rw,height/rh)*1.5f);
        boolean any=false;
        for(int y=0;y<rh;y++){
            if((y&31)==0 && Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            float ny=(y+.5f)/rh, py=ny*height;
            for(int x=0;x<rw;x++){
                float nx=(x+.5f)/rw, px=nx*width;
                if(px<inset||px>width-inset||py<inset||py>height-inset)continue;
                if(s.circleCrop){float dx=(px-width/2)/(width/2-inset),dy=(py-height/2)/(height/2-inset);if(dx*dx+dy*dy>1)continue;}
                if(s.snips!=null&&!s.snips.allows(nx,ny))continue;
                int sx=Math.min(w-1,(int)(nx*w)),sy=Math.min(h-1,(int)(ny*h));
                if((pixels[sy*w+sx]>>>24)<128)continue;
                allowed[y*rw+x]=true;any=true;
            }
        }
        if(!any)throw new IllegalArgumentException("The Snip has no fillable area for a vinyl background");
        StyleKit.Source src=new StyleKit.Source(opaque,rw,rh,allowed,width);
        ArrayList<float[]> loops=SolidSnipContourRepair.repair(
            VinylStyleSuite.subjectLoops(src),s.minimumMm);
        if(loops.isEmpty())throw new IllegalArgumentException("Could not trace the solid Snip background");
        // A compound even-odd path includes all disjoint islands and removed holes.
        // All rings share one vinyl sheet and identical physical coordinates.
        if(loops.size()+VinylEngine.registrationElements(result)>5000)
            throw new IllegalArgumentException("Snip outline exceeds SVG cut contour limit");
        result.shapes.get(layer).add(VinylEngine.Shape.compound(loops.toArray(new float[0][])));
    }
}

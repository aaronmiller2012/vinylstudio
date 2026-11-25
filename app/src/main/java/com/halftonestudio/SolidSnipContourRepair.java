package com.halftonestudio;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Conservative, optional physical cleanup for a raster-traced solid Snip base.
 * The original nondestructive Snip remains unchanged. A candidate replaces the
 * exported base only if every loop retains its orientation and area, and the
 * FINAL compound outline passes the normal cut-geometry inspector.
 * No heuristic result is represented as cut-qualified without final SVG review.
 */
final class SolidSnipContourRepair {
    private SolidSnipContourRepair() {}
    private static final int MAX_VERTICES=50000;

    static ArrayList<float[]> repair(List<float[]> original, float minimumMm) {
        ArrayList<float[]> fallback=new ArrayList<>(original);
        if(original.isEmpty() || !Float.isFinite(minimumMm) || minimumMm<=0)return fallback;
        int vertices=0;
        for(float[] ring:original){
            if(ring==null||ring.length<6||(ring.length&1)!=0)return fallback;
            vertices+=ring.length/2;
            if(vertices>MAX_VERTICES)return fallback;
        }
        // Avoid changing a base already verified by the exact source inspector.
        if(passes(original,minimumMm))return fallback;
        // The tolerance is bounded in *physical mm*, not source pixels. 0.25mm
        // eliminates sub-cuttable marching-square stair-steps at 11in without
        // smoothing away a 1mm hole. Never exceed 0.35mm of boundary movement.
        float tolerance=Math.min(.35f,Math.max(.25f,minimumMm*1.03f));
        ArrayList<float[]> out=new ArrayList<>(original.size());
        for(float[] ring:original){
            float[] simpler=simplifyClosed(ring,tolerance);
            if(simpler.length<6 || simpler.length>ring.length)return fallback;
            double areaBefore=StyleKit.signedArea(ring),areaAfter=StyleKit.signedArea(simpler);
            if(!Double.isFinite(areaBefore)||!Double.isFinite(areaAfter)||
                    Math.abs(areaBefore)<1e-8 || areaBefore*areaAfter<=0 ||
                    Math.abs(areaAfter-areaBefore)>Math.abs(areaBefore)*.005)return fallback;
            out.add(simpler);
        }
        // Cut safety, including opposite neck edges, disjoint loops and holes,
        // is checked against the final simplified outline. No warning is exempt.
        if(!passes(out,minimumMm))return fallback;
        return out;
    }

    private static boolean passes(List<float[]> rings,float minimumMm){
        try{
            StyleKit.Result page=new StyleKit.Result(1,1);
            StyleKit.Layer layer=new StyleKit.Layer("solid Snip physical outline",0xff000000);
            layer.loops.addAll(rings);page.layers.add(layer);
            return StyleCutGeometryPreflight.inspect(page,minimumMm,1).isEmpty();
        }catch(RuntimeException error){return false;}
    }

    /** Ramer-Douglas-Peucker on two closed-ring arcs. The most distant vertex
     * from the first is a second fixed anchor, avoiding a degenerate closing
     * line. Both arcs are simplified independently; no ring is opened.
     */
    static float[] simplifyClosed(float[] p,float tolerance){
        int n=p.length/2;
        if(n<5)return p;
        int opposite=1;double far=-1;
        for(int i=1;i<n;i++){
            double dx=(double)p[2*i]-p[0],dy=(double)p[2*i+1]-p[1];
            double d=dx*dx+dy*dy;
            if(d>far){far=d;opposite=i;}
        }
        if(opposite<1||opposite>=n||far<=0)return p;
        boolean[] keep=new boolean[n];
        keep[0]=true;keep[opposite]=true;
        reduce(p,0,opposite,tolerance,keep);
        // Closing arc uses a virtual copy of point zero at index n.
        reduce(p,opposite,n,tolerance,keep);
        int count=0;for(boolean b:keep)if(b)count++;
        if(count<3||count==n)return p;
        float[] result=new float[count*2];int pos=0;
        for(int i=0;i<n;i++)if(keep[i]){
            result[pos++]=p[i*2];result[pos++]=p[i*2+1];
        }
        return result;
    }
    private static void reduce(float[] p,int first,int last,float tolerance,boolean[] keep){
        ArrayDeque<int[]> tasks=new ArrayDeque<>();tasks.push(new int[]{first,last});
        double limit=(double)tolerance*tolerance;
        int n=p.length/2;
        while(!tasks.isEmpty()){
            int[] pair=tasks.pop();int a=pair[0],b=pair[1];
            double ax=p[2*(a%n)],ay=p[2*(a%n)+1];
            double bx=p[2*(b%n)],by=p[2*(b%n)+1];
            double dx=bx-ax,dy=by-ay,den=dx*dx+dy*dy;
            int farIndex=-1;double max=limit;
            for(int i=a+1;i<b;i++){
                double x=p[2*(i%n)],y=p[2*(i%n)+1];
                double t=den==0?0:Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/den));
                double ex=x-ax-t*dx,ey=y-ay-t*dy,dist=ex*ex+ey*ey;
                if(dist>max){max=dist;farIndex=i;}
            }
            if(farIndex>=0){keep[farIndex%n]=true;
                tasks.push(new int[]{a,farIndex});tasks.push(new int[]{farIndex,b});
            }
        }
    }
}

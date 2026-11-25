package com.halftonestudio;

import java.util.ArrayList;

/**
 * Late vector-level Snip for parallel vinyl ribbons. Tone, carrier selection,
 * smoothing and width are computed on the original photograph. This pass
 * removes only carrier sections whose filled width intersects an exclusion.
 * It never emits SVG clipPaths or joins across a hole. Cutter-safe closed
 * contours are constructed by the line engines and verified by VinylEngine.
 */
final class SnipRibbonPostClip {
    private SnipRibbonPostClip() {}
    static ArrayList<ArrayList<float[]>> split(ArrayList<float[]> run,SnipMask mask,
                                                float widthMm,float heightMm){
        ArrayList<ArrayList<float[]>> pieces=new ArrayList<>();
        if(run.size()<2)return pieces;
        if(mask==null||mask.isEmpty()){
            pieces.add(run); // no-mask geometry stays byte-for-byte identical
            return pieces;
        }
        ArrayList<float[]> current=new ArrayList<>();
        float priorClearance=0;
        for(int i=0;i<run.size();i++){
            if((i&63)==0)CancellationCheckpoint.check();
            float[] q=run.get(i);
            float radius=radius(q);
            float reach=radius+.035f;
            float clearance=mask.clearanceWithinMm(q[0],q[1],widthMm,heightMm,reach+.01f);
            boolean valid=clearance>reach;
            if(valid&&!current.isEmpty()){
                float[] prev=current.get(current.size()-1);
                float length=(float)Math.hypot(q[0]-prev[0],q[1]-prev[1]);
                // The distance-to-boundary function is 1-Lipschitz. Fully
                // interior segments need no expensive intersection search.
                if(Math.min(priorClearance,clearance)<=Math.max(radius,radius(prev))+.035f+length)
                    valid=mask.safeSegment(prev[0],prev[1],q[0],q[1],widthMm,heightMm)
                          && corridorSafe(mask,prev,q,widthMm,heightMm,length);
            }
            if(!valid){
                if(current.size()>=2)pieces.add(current);
                current=new ArrayList<>();
                continue;
            }
            current.add(q);
            priorClearance=clearance;
        }
        if(current.size()>=2)pieces.add(current);
        return pieces;
    }
    private static float radius(float[] q){
        return q[2]*(q.length>3?1f+Math.abs(q[3]):1f);
    }
    private static boolean corridorSafe(SnipMask mask,float[] a,float[] b,
                                         float width,float height,float length){
        // Only near boundaries: inspect segment fill at <= .20 mm intervals.
        int steps=Math.max(1,(int)Math.ceil(length/.20f));
        if(steps>200)return false; // fail closed for malformed discontinuities
        float extra=Math.max(radius(a),radius(b))+.035f;
        for(int k=1;k<steps;k++){
            float t=k/(float)steps;
            float x=a[0]+(b[0]-a[0])*t,y=a[1]+(b[1]-a[1])*t;
            if(mask.clearanceWithinMm(x,y,width,height,extra+.01f)<=extra)return false;
        }
        return true;
    }
}

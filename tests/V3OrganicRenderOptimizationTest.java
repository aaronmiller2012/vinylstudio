package com.halftonestudio;
import java.util.*;
public final class V3OrganicRenderOptimizationTest {
    static void req(boolean good,String message){if(!good)throw new AssertionError(message);}
    static float[] ellipse(double cx,double cy,double rx,double ry,int n){
        float[] ring=new float[n*2];for(int j=0;j<n;j++){
            double rad=2*Math.PI*j/n;
            ring[j*2]=(float)(cx+rx*Math.cos(rad));ring[j*2+1]=(float)(cy+ry*Math.sin(rad));
        }return ring;
    }
    public static void main(String[]args){
        int w=118,h=120;float cell=.7f;
        ArrayList<float[]> rings=new ArrayList<>();
        rings.add(ellipse(42,43,25,28,120));
        rings.add(ellipse(42,43,11,9,54)); // nested hole
        rings.add(ellipse(58,53,4,3,20));
        rings.add(ellipse(70,62,8,12,48));
        boolean[] reference=OrganicFlowCutSmoother.rasterize(rings,w,h,cell);
        req(reference!=null,"Bad baseline");
        boolean[] valid=new boolean[w*h];Arrays.fill(valid,true);
        float[] importance=new float[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)
            if(x>35&&x<90&&y>35&&y<105)importance[y*w+x]=1f;
        OrganicFlowCutSmoother.FidelityTracker tracker=
            new OrganicFlowCutSmoother.FidelityTracker(reference,rings,importance,valid,w,h,cell);
        for(int i=0;i<rings.size();i++){
            float[] prior=rings.get(i);
            float[] next=prior.clone();
            for(int n=0;n<next.length;n+=2){next[n]+=.06f;next[n+1]+=.04f;}
            rings.set(i,next);
            OrganicFlowCutSmoother.FidelityTracker.Change change=tracker.evaluate(rings,i,prior,next);
            boolean[] full=OrganicFlowCutSmoother.rasterize(rings,w,h,cell);
            req(full!=null,"Failed full raster");
            if(change==null){
                // Reject only if the full reference fidelity budget is exceeded.
                req(!OrganicFlowCutSmoother.preservesPhotoFeatures(reference,rings,importance,valid,w,h,cell),
                    "Local fidelity rejected a globally acceptable move");
                rings.set(i,prior);
            }else{
                tracker.commit(change);
                req(Arrays.equals(full,tracker.current),"Cached ROI raster differs from full raster at "+i);
                req(OrganicFlowCutSmoother.preservesPhotoFeatures(reference,rings,importance,valid,w,h,cell),
                    "Region validator permitted an invalid global change");
            }
        }
        // Standalone rectangular crop must match the corresponding full result.
        boolean[] full=OrganicFlowCutSmoother.rasterize(rings,w,h,cell);
        boolean[] roi=OrganicFlowCutSmoother.rasterizeRegion(rings,w,h,cell,22,29,100,111);
        req(roi!=null,"ROI scan returned null");
        for(int y=29;y<111;y++)for(int x=22;x<100;x++)
            req(full[y*w+x]==roi[(y-29)*78+x-22],"ROI parity mismatch "+x+","+y);
        System.out.println("PASS ROI fidelity parity with nested holes and successive contour commits");
    }
}

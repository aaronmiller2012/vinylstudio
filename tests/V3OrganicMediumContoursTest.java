package com.halftonestudio;
import java.util.ArrayList;

/** Guards the second smoothing pass against top-18-only regression. */
public final class V3OrganicMediumContoursTest {
    public static void main(String[] args){
        StyleKit.Layer light=new StyleKit.Layer("light fill",0xffeee7bc);
        for(int i=0;i<45;i++){
            float x=5+(i%9)*10f,y=5+(i/9)*10f;
            // Each 2x2mm hole is larger than the material minimum and should
            // be considered even when it is not one of the 18 biggest loops.
            light.loops.add(new float[]{x,y,x+2,y,x+2,y+2,x,y+2});
        }
        ArrayList<Integer> p50=OrganicFlowCutSmoother.uniformContourCandidates(light,.5f,.8f,.25f);
        ArrayList<Integer> p100=OrganicFlowCutSmoother.uniformContourCandidates(light,1f,.8f,.25f);
        if(p50.size()!=26||p100.size()!=30)
            throw new AssertionError("Medium contours excluded: 50="+p50.size()+", 100="+p100.size());
        // The same geometry must be available to all physical sheets regardless
        // of their swatch/color (including white).
        StyleKit.Layer dark=new StyleKit.Layer("dark fill",0xff112233);
        dark.loops.addAll(light.loops);
        if(!p100.equals(OrganicFlowCutSmoother.uniformContourCandidates(dark,1f,.8f,.25f)))
            throw new AssertionError("Smoothing selection depends on color");
        System.out.println("PASS medium cutout selection at 50% and 100%; palette independent");
    }
}

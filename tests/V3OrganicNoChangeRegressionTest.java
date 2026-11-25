package com.halftonestudio;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Prevent a slider that only deletes tiny pieces, leaving every surviving path jagged. */
public final class V3OrganicNoChangeRegressionTest {
    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    private static float[] rect(float x,float y,float w,float h){
        return new float[]{x,y,x+w,y,x+w,y+h,x,y+h};
    }
    private static Set<String> shapes(StyleKit.Result result){
        Set<String> out=new HashSet<>();
        for(int l=1;l<result.layers.size();l++)
            for(float[] xy:result.layers.get(l).loops)out.add(Arrays.toString(xy));
        return out;
    }
    private static void historicalProblem(){
        int w=180,h=215;
        int[] photo=V3AllStyleExportMatrixTest.portrait(w,h);
        for(int style=VinylStyleSuite.QUILLING_RIBBONS;
            style<=VinylStyleSuite.QUILLED_MARBLING;style++){
            StyleKit.Result[] output=new StyleKit.Result[2];
            for(int i=0;i<2;i++){
                VinylStyleSuite.Config c=new VinylStyleSuite.Config();
                c.style=style;c.widthMm=279.4f;c.minimumMm=.8f;c.gapMm=.5f;
                c.colorCount=6;c.detail=100;c.flowStrength=2.5f;
                c.organicSmoothing=100*i;
                output[i]=VinylStyleSuite.generate(photo,w,h,c);
            }
            require(output[0].layers.size()==output[1].layers.size(),"Layer count drift");
            for(int k=0;k<output[0].layers.get(0).loops.size();k++)
                require(Arrays.equals(output[0].layers.get(0).loops.get(k),
                    output[1].layers.get(0).loops.get(k)),"Structural Snip moved");
            Set<String> original=shapes(output[0]);
            int unchanged=0,remaining=0;
            for(int l=1;l<output[1].layers.size();l++)for(float[] xy:output[1].layers.get(l).loops){
                remaining++;
                if(original.contains(Arrays.toString(xy)))unchanged++;
            }
            int rounded=remaining-unchanged;
            require(rounded>=Math.min(25,Math.max(8,remaining/10)),
                "Smoothing only removed fragments for style "+style+": rounded="+rounded);
            require(output[1].summary.contains("Rounded "),
                "Completed render lacks an applied-curve count");
            require(output[0].summary.contains("Smoothing off"),
                "Smoothing-off geometry not identified");
            System.out.println("PASS style="+style+" retained "+remaining+
                " outlines, actually rounded "+rounded+" (previously 0)");
        }
    }
    private static void unrelatedExistingCutWarningsMustNotBlockSafeCurves(){
        StyleKit.Layer layer=new StyleKit.Layer("nearby risk",0xff000000);
        float[] original=rect(10,10,10,10);
        layer.loops.add(rect(10.1f,10.1f,9.8f,9.8f));
        layer.loops.add(rect(40,10,8,8));
        layer.loops.add(rect(48.2f,10,8,8)); // existing gap below required .5mm
        require(OrganicFlowCutSmoother.localNonRegression(layer,0,original,.8f,.5f,80,80),
            "Existing unrelated narrow gap prevents a safe contour improvement");
        StyleKit.Layer unsafe=new StyleKit.Layer("new contact",0xff000000);
        float[] before=rect(10,10,10,10);
        unsafe.loops.add(rect(10,10,10.35f,10));
        unsafe.loops.add(rect(20.2f,10,7,10));
        require(!OrganicFlowCutSmoother.localNonRegression(unsafe,0,before,.8f,.5f,80,80),
            "A new contact introduced by smoothing must be rejected");
        System.out.println("PASS local relative safety: preexisting warning allowed, new contact rejected");
    }
    public static void main(String[] args){
        unrelatedExistingCutWarningsMustNotBlockSafeCurves();
        historicalProblem();
    }
}

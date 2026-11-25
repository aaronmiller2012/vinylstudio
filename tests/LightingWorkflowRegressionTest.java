package com.halftonestudio;

import java.util.Arrays;
/** Verifies nondestructive photo lighting changes the actual layered-cell cut paths. */
public final class LightingWorkflowRegressionTest {
    private static void require(boolean truth,String why){if(!truth)throw new AssertionError(why);}
    public static void main(String[] args){
        final int w=100,h=130;
        int[] source=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float radial=(float)Math.max(0,1-Math.pow((x-w*.5)/(w*.44),2)-Math.pow((y-h*.48)/(h*.47),2));
            int v=Math.max(12,Math.min(245,Math.round(20+192*radial+22*(x/(float)w))));
            source[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;
        }
        int[] original=source.clone();
        VinylEngine.Settings settings=VinylEngine.Settings.defaults();
        settings.exposureStops=-1.3f;settings.photoContrast=1.35f;
        int[] darkened=ImageLevels.apply(source,w,h,settings);
        require(!Arrays.equals(source,darkened),"Exposure changed no pixels");
        require(Arrays.equals(source,original),"Lighting mutated original image");
        LayeredCellPortraitEngine.Options opts=new LayeredCellPortraitEngine.Options();
        opts.columns=12;opts.widthMm=150;opts.minFeatureMm=.50f;
        opts.layerCount=4;opts.motifDepth=2;
        opts.colors=new int[]{0xff171923,0xff70505a,0xffbe8a80,0xffead6bf};
        LayeredCellPortraitEngine.Result before=LayeredCellPortraitEngine.generate(source,w,h,opts);
        LayeredCellPortraitEngine.Result after=LayeredCellPortraitEngine.generate(darkened,w,h,opts);
        boolean different=false;
        for(int i=0;i<4;i++)if(!before.svg(i).equals(after.svg(i)))different=true;
        require(different,"Lighting must change actual layered-cell cut geometry");
        require(WorkflowFlow.next(WorkflowFlow.SNIP,1)==WorkflowFlow.LIGHT,"Lighting after Snip");
        require(WorkflowFlow.tool(WorkflowFlow.LIGHT)==10,"Lighting routes to levels tool");
        System.out.println("PASS: Layered Cell lighting changes vinyl geometry without mutating photo or stage IDs");
    }
}

package com.halftonestudio;
import java.util.Arrays;
public final class V3OrganicDraftPerformanceTest {
    public static void main(String[] args){
        int w=125,h=144;int[] pixels=new int[w*h];
        boolean[] allowed=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float xx=(x-w*.5f)/(w*.48f),yy=(y-h*.5f)/(h*.48f);
            allowed[y*w+x]=xx*xx+yy*yy<1f;
            int v=Math.min(255,Math.max(0,(int)(85+70*xx+30*Math.cos(yy*17))));
            pixels[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;
        }
        for(int mode=0;mode<3;mode++){
            OrganicFlowPortraitEngine.Options o=new OrganicFlowPortraitEngine.Options();
            o.mode=mode;o.widthMm=101.6f;o.detail=78;o.flow=1.8f;o.minCutMm=.8f;
            o.gapMm=.5f;o.layers=6;o.allowed=allowed;
            o.draft=true;long start=System.nanoTime();
            StyleKit.Result draft=OrganicFlowPortraitEngine.generate(pixels,w,h,o);
            long draftMs=(System.nanoTime()-start)/1000000;
            if(!draft.summary.contains("Draft preview")||draft.layers.size()!=6)
                throw new AssertionError("Draft not identified as provisional");
            for(StyleKit.Layer layer:draft.layers)if(layer.lines.size()!=0)
                throw new AssertionError("Draft gained unsupported pen paths");
            o.draft=false;start=System.nanoTime();
            StyleKit.Result full=OrganicFlowPortraitEngine.generate(pixels,w,h,o);
            long fullMs=(System.nanoTime()-start)/1000000;
            if(full.summary.contains("Draft preview")||full.layers.size()!=6)
                throw new AssertionError("Full render missing or incorrectly marked");
            System.out.println("PASS draft/final mode="+mode+" draftMs="+draftMs+" fullMs="+fullMs);
        }
    }
}

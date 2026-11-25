package com.halftonestudio;

import java.util.*;
import java.util.concurrent.Future;

/** RC169 executable checks: lifecycle cancellation and perimeter textile geometry. */
public final class RC169ReliabilityGeometryTest {
    private static void check(boolean ok,String note){if(!ok)throw new AssertionError(note);}
    public static void main(String[] args) throws Exception {
        StudioRenderController scheduler=new StudioRenderController();
        scheduler.close();
        Future<?> discarded=scheduler.submit(StudioRenderController.Lane.STANDARD,
            ()->{throw new AssertionError("A late render must never execute after close");});
        check(discarded.isDone(),"Closed scheduler must return completed future");
        boolean ran=false;
        try{discarded.get();}catch(Exception e){throw new AssertionError("Closed submission should be harmless",e);}
        check(!ran,"Late render work did not execute");

        final int W=96,H=96;
        int[] source=new int[W*H];boolean[] allowed=new boolean[W*H];
        Arrays.fill(source,0xff886650);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            double dx=(x-48)/33.0,dy=(y-48)/31.0;
            allowed[y*W+x]=dx*dx+dy*dy<=1;
        }
        StyleKit.Source photo=new StyleKit.Source(source,W,H,allowed,120f);
        NineLayerCraftPatternEngine.Options options=new NineLayerCraftPatternEngine.Options();
        options.pattern=NineLayerCraftPatternEngine.FISHNET;
        options.widthMm=120f;options.allowed=allowed;options.layers=2;
        float pitch=NineLayerCraftPatternEngine.textilePitch(photo,options);
        ArrayList<NineLayerCraftPatternEngine.Cell> cells=
            NineLayerCraftPatternEngine.createCells(photo,options,pitch);
        int outside=0,upstream=0;
        for(NineLayerCraftPatternEngine.Cell node:cells){
            if(!photo.validAt(node.x,node.y)){
                outside++;
                for(int side=-1;side<=1;side+=2){
                    int col=node.col+(((node.row&1)==0)?(side>0?0:-1):(side>0?1:0));
                    if(photo.validAt(NineLayerCraftPatternEngine.fishnetNodeX(node.row+1,col,pitch),
                        NineLayerCraftPatternEngine.fishnetNodeY(node.row+1,col,pitch)))upstream++;
                }
            }
        }
        check(outside>0&&upstream>0,"Perimeter parent nodes and incoming fishnet strands missing");
        System.out.println("PASS RC169 closed render lane is harmless; fishnet perimeter parents="+outside+
            ", valid incoming links="+upstream);
    }
}

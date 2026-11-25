package com.halftonestudio;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;

/** Real behavior tests for bounded drafts and cooperative cancellation, without Android SDK. */
public final class RenderResponsivenessRC162Test {
    private static void need(boolean yes,String reason){if(!yes)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception{
        need(RenderDraftPolicy.MAX_SIDE_PX==480,"drag bitmap is larger than 480 px");
        need(RenderDraftPolicy.DEBOUNCE_MS==60,"standard drag debounce changed");
        need(RenderDraftPolicy.HEAVY_DEBOUNCE_MS==120,"heavy-style drag debounce unexpected");
        need(RenderDraftPolicy.dimensions(2400,1800)[0]==480,"standard draft cap");
        need(RenderDraftPolicy.dimensions(720,480)[0]==360,"small source draft must be half-size");
        need(!RenderDraftPolicy.supportsSpecialtyDraft(true,-1),"Maze must retain last frame on drag");
        need(!RenderDraftPolicy.supportsSpecialtyDraft(false,VinylStyleSuite.TSP),"TSP must retain last frame on drag");
        int[] argb=new int[160*200];java.util.Arrays.fill(argb,0xff74543b);
        StyleKit.Source source=new StyleKit.Source(argb,160,200,null,280f);
        PatternPortraitTrioEngine.Surface finalSurface=new PatternPortraitTrioEngine.Surface(source,6,.254f,false);
        PatternPortraitTrioEngine.Surface draftSurface=new PatternPortraitTrioEngine.Surface(source,6,.254f,true);
        need(Math.max(draftSurface.w,draftSurface.h)<=460,"Confetti/Chainmail draft raster cap");
        need(draftSurface.w*draftSurface.h<finalSurface.w*finalSurface.h,"Draft mask not smaller");
        PrismaticDiagonalPortraitEngine.Options prism=new PrismaticDiagonalPortraitEngine.Options();
        prism.draft=true;
        need(prism.copy().draft,"Prismatic draft setting lost in options.copy()");
        VinylStyleSuite.Config config=new VinylStyleSuite.Config();config.draft=true;
        need(config.copy().draft,"New-style draft setting lost in config.copy()");
        boolean canceled=false;
        Thread.currentThread().interrupt();
        try{CancellationCheckpoint.check();}
        catch(CancellationException expected){canceled=true;}
        finally{Thread.interrupted();}
        need(canceled,"Checkpoint did not stop interrupted work");
        try(StudioRenderController controller=new StudioRenderController()){
            CountDownLatch started=new CountDownLatch(1);
            Future<?> old=controller.submit(StudioRenderController.Lane.SPECIALTY,()->{
                started.countDown();
                while(true){CancellationCheckpoint.check();Thread.onSpinWait();}
            });
            need(started.await(3,TimeUnit.SECONDS),"old engine never started");
            long start=System.nanoTime();
            Future<?> newest=controller.submit(StudioRenderController.Lane.SPECIALTY,()->{});
            newest.get(3,TimeUnit.SECONDS);
            need(old.isCancelled(),"old render not marked canceled");
            need(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<2500,"old engine delayed replacement render");
        }
        System.out.println("PASS RC162 cancellation unblocks render lane; bounded standard/specialty/pattern drafts");
    }
}

package com.halftonestudio;

import java.util.Random;

/** Verifies buffer reuse across multiple full bands, including Snip edge halos. */
public final class RC87TiledLightingTest {
    private static void verify(int w,int h,boolean clipped){
        Random random=new Random(87001+h);
        int[] rgb=new int[w*h];
        for(int i=0;i<rgb.length;i++)rgb[i]=random.nextInt()|0xff000000;
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.levelsBlack=12;s.levelsWhite=244;s.levelsGamma=.72f;s.exposureStops=.15f;
        s.levelsShadows=.34f;s.levelsHighlights=-.18f;s.photoContrast=1.15f;s.edgePriority=.71f;
        if(clipped){
            s.snips.add(new SnipMask.Operation(SnipMask.KEEP_INSIDE,
                .05f,.07f,.95f,.07f,.95f,.96f,.05f,.96f));
            s.snips.add(new SnipMask.Operation(SnipMask.REMOVE_INSIDE,
                .33f,.20f,.66f,.20f,.66f,.76f,.33f,.76f));
        }
        int[] standard=ImageLevels.apply(rgb,w,h,s);
        int[] tiled=TiledImageLevels.apply(rgb,w,h,s);
        if(standard.length!=tiled.length)throw new AssertionError("Array size changed");
        for(int i=0;i<standard.length;i++)if(standard[i]!=tiled[i])
            throw new AssertionError("Lighting mismatch row="+(i/w)+" column="+(i%w)+" clipped="+clipped);
    }
    public static void main(String[] args){
        for(int height:new int[]{1,255,256,257,258,512,513,621}){
            verify(49,height,false);
            verify(49,height,true);
        }
        System.out.println("PASS RC87: tiled lighting identical across halo boundaries, 8 image heights, Snip on/off");
    }
}

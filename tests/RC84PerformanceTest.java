package com.halftonestudio;

import java.util.Random;

/** Real behavioral checks, not string-only tests. */
public final class RC84PerformanceTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        int w=101,h=93;int[] pixels=new int[w*h];
        Random r=new Random(8450);
        for(int i=0;i<pixels.length;i++){
            int v=(i*23+r.nextInt(256))&255;
            pixels[i]=0xff000000|(v<<16)|((255-v)<<8)|(v/2);
        }
        AutoPhotoTuner.Stats stats=AutoPhotoTuner.analyze(pixels,w,h,null,false);
        for(int style=0;style<VinylStyleSuite.NAMES.length;style++){
            PhotoStyleDefaults.Initial init=PhotoStyleDefaults.forNewStyle(style,stats,.8f,152.4f);
            check(init.detail>=25&&init.detail<=90,"Initial detail out of bounds "+style);
            check(init.weightMm>=.8f,"Material minimum not honored "+style);
            check(init.leadMm>=.8f,"Lead gap too small "+style);
            PhotoStyleDefaults.Initial small=PhotoStyleDefaults.forNewStyle(style,stats,1.2f,80f);
            check(small.detail<=init.detail,"Small art should not increase detail "+style);
            check(small.weightMm>=1.2f,"Width must protect small material "+style);
        }
        TspArtEngine.Options invalid=new TspArtEngine.Options();
        invalid.minStrokeMm=.8f;invalid.strokeMm=.7f;
        boolean rejected=false;
        try{invalid.validate();}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Direct TSP unsafe stroke must still fail");
        // The suite reconciles the UI's 0.7mm default with the material's 0.8mm minimum.
        VinylStyleSuite.Config config=new VinylStyleSuite.Config();
        config.style=VinylStyleSuite.TSP;config.colorCount=2;config.minimumMm=.8f;
        config.weightMm=.7f;config.detail=0;config.variant=0;
        config.palette=new int[]{0xff111111,0xffffffff};config.widthMm=101.6f;
        StyleKit.Result output=VinylStyleSuite.generate(pixels,w,h,config);
        check(output!=null&&!output.layers.isEmpty(),"TSP selection should not throw Invalid stroke range");
        config.variant=1; // The exact "Cut Vinyl" variant reported failing on-device.
        StyleKit.Result cut=VinylStyleSuite.generate(pixels,w,h,config);
        check(cut!=null&&!cut.layers.isEmpty(),"TSP Cut Vinyl must not throw Invalid stroke range");
        check(cut.summary!=null&&!cut.summary.isEmpty(),"TSP Cut Vinyl must return diagnostics");
        boolean cuttable=false;
        for(StyleKit.Layer layer:cut.layers)if(!layer.loops.isEmpty())cuttable=true;
        check(cuttable,"TSP Cut Vinyl must produce a cuttable filled ribbon");
        System.out.println("TSP Cut Vinyl: "+cut.summary);
        // Gamma parity: non-neutral image adjustment, snips off, both implementations.
        for(float gamma:new float[]{.68f,.84f,1f,1.14f,1.42f,2f}){
            VinylEngine.Settings s=VinylEngine.Settings.defaults();
            s.levelsGamma=gamma;s.levelsBlack=16;s.levelsWhite=237;
            s.photoContrast=1.12f;s.levelsShadows=.15f;s.edgePriority=.25f;
            int[] expected=ImageLevelsReference.apply(pixels,w,h,s);
            int[] fast=ImageLevels.apply(pixels,w,h,s);
            int[] tiled=TiledImageLevels.apply(pixels,w,h,s);
            for(int i=0;i<fast.length;i++){
                check(fast[i]==tiled[i],"Tiled/standard lighting disagrees gamma="+gamma+" at "+i);
                int a=expected[i],b=fast[i];
                check((a>>>24)==(b>>>24),"Gamma changed alpha");
                for(int shift:new int[]{0,8,16}){
                    int difference=Math.abs(((a>>>shift)&255)-((b>>>shift)&255));
                    check(difference<=1,"Gamma channel difference >1 at "+i+" gamma="+gamma);
                }
            }
        }
        System.out.println("PASS RC84 eight style defaults, material floors, TSP integration, gamma parity and tiling");
    }
}

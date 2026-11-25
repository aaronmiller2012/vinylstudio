package com.halftonestudio;

import java.io.File;
import java.util.Locale;

/**
 * M4 gap contract. Build 558 generated suite artwork at a fixed Config.gapMm = 0.5 mm
 * while the cut-ready stage and export held it to the material's bridge. This test
 * renders real suite styles from a real photograph with (a) the 558 wiring and (b)
 * the M4 contract, and measures classified cut findings at the export gap.
 *
 * Contract asserted:
 *  - default settings (bridge 0.5 mm) are unchanged: identical generated geometry;
 *  - for a wide custom bridge, generation designed to that bridge leaves fewer
 *    findings for the cut-ready stage to repair, summed over the styles;
 *  - generation and the cut-ready stage derive the gap from one function.
 */
public final class M4GapContractTest {
    public static void main(String[] args)throws Exception{
        M4ArtBench.Photo photo=M4ArtBench.load(new File("tests/m4/photos/astronaut.png"),480);
        int[] styles={VinylStyleSuite.LINOCUT,VinylStyleSuite.REDUCTION_WOODCUT,VinylStyleSuite.TORN_COLLAGE,
            VinylStyleSuite.CROCHET,VinylStyleSuite.CAMOUFLAGE,VinylStyleSuite.CONFETTI,VinylStyleSuite.QUILLED_MARBLING};
        float min=.8f;
        // 1. defaults unchanged
        if(VinylStyleSuite.generationGapMm(.5f,min)!=.5f)throw new AssertionError("default bridge must keep the 0.5 mm generation gap");
        int defaultChanged=0;
        for(int style:styles){
            VinylStyleSuite.Config c=M4ArtBench.suiteConfig(style,photo,152.4f,min,.5f);
            c.gapMm=.5f;StyleKit.Result legacy=VinylStyleSuite.generate(photo.argb,photo.w,photo.h,c);
            c.gapMm=VinylStyleSuite.generationGapMm(.5f,min);StyleKit.Result fixed=VinylStyleSuite.generate(photo.argb,photo.w,photo.h,c);
            if(!sameGeometry(legacy,fixed))defaultChanged++;
        }
        if(defaultChanged!=0)throw new AssertionError(defaultChanged+" styles changed geometry at the default bridge");
        // 2. wide custom bridge
        int legacyTotal=0,fixedTotal=0;
        for(float bridge:new float[]{1.0f,.30f}){
            System.out.printf(Locale.US,"bridge %.2f mm (min %.2f): style  legacy-findings  m4-findings  legacy-pieces  m4-pieces%n",bridge,min);
            float gap=VinylStyleSuite.generationGapMm(bridge,min);
            for(int style:styles){
                VinylStyleSuite.Config c=M4ArtBench.suiteConfig(style,photo,152.4f,min,bridge);
                c.gapMm=.5f;StyleKit.Result legacy=VinylStyleSuite.generate(photo.argb,photo.w,photo.h,c);
                c.gapMm=gap;StyleKit.Result fixed=VinylStyleSuite.generate(photo.argb,photo.w,photo.h,c);
                int fl=StyleCutGeometryPreflight.inspect(legacy,min,gap,100000).size();
                int ff=StyleCutGeometryPreflight.inspect(fixed,min,gap,100000).size();
                if(bridge==1.0f){legacyTotal+=fl;fixedTotal+=ff;}
                System.out.printf(Locale.US,"  %-28s %8d %12d %14d %10d%n",VinylStyleSuite.NAMES[style],fl,ff,
                    M4ArtBench.topology(legacy)[0],M4ArtBench.topology(fixed)[0]);
            }
        }
        System.out.printf(Locale.US,"bridge 1.0 mm total findings: 558 wiring %d, M4 contract %d%n",legacyTotal,fixedTotal);
        if(!(fixedTotal<legacyTotal))throw new AssertionError("designing to the export gap must leave fewer findings than designing to 0.5 mm");
        System.out.println("PASS M4 gap contract: defaults unchanged; generation uses the export gap");
    }
    static boolean sameGeometry(StyleKit.Result a,StyleKit.Result b){
        if(a.layers.size()!=b.layers.size())return false;
        for(int i=0;i<a.layers.size();i++){
            StyleKit.Layer x=a.layers.get(i),y=b.layers.get(i);
            if(x.loops.size()!=y.loops.size()||x.lines.size()!=y.lines.size())return false;
            for(int j=0;j<x.loops.size();j++)if(!java.util.Arrays.equals(x.loops.get(j),y.loops.get(j)))return false;
        }
        return true;
    }
}

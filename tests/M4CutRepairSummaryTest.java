package com.halftonestudio;

import java.io.File;
import java.util.Locale;

/**
 * M4: the cut-ready stage states what it removed. Real photo, real engines, real
 * prepareForCutting. The counts must agree with an independent topology count, the
 * sentence must name them, untouched artwork must say nothing, and counting must stay a
 * small fraction of the stage's own cost.
 */
public final class M4CutRepairSummaryTest {
    public static void main(String[] args)throws Exception{
        M4ArtBench.Photo photo=M4ArtBench.load(new File("tests/m4/photos/astronaut.png"),850);
        int failures=0;
        System.out.println("style @size           holes filled  pieces removed  stage ms  count ms");
        for(int style:new int[]{VinylStyleSuite.CROCHET,VinylStyleSuite.CHAINMAIL,VinylStyleSuite.LOW_POLY,VinylStyleSuite.TORN_COLLAGE})
        for(float size:new float[]{76.2f,152.4f}){
            VinylStyleSuite.Config c=M4ArtBench.suiteConfig(style,photo,size,.8f,.5f);
            StyleKit.Result gen=VinylStyleSuite.generate(photo.argb,photo.w,photo.h,c);
            long t0=System.nanoTime();
            StyleKit.Result cut=VinylStyleSuite.prepareForCutting(gen,style,.8f,.5f);
            long stageMs=(System.nanoTime()-t0)/1_000_000;
            long t1=System.nanoTime();
            CutRepairSummary s=CutRepairSummary.of(gen,cut);
            long countMs=(System.nanoTime()-t1)/1_000_000;
            int[] before=M4ArtBench.topology(gen),after=M4ArtBench.topology(cut);
            System.out.printf(Locale.US,"%-26s %4.1fin %8d %14d %9d %9d%n",VinylStyleSuite.NAMES[style],size/25.4f,
                s.totalHoles,s.totalPieces,stageMs,countMs);
            // Independent whole-artwork count (M4ArtBench) bounds the per-sheet net sum from below.
            if(s.totalHoles<Math.max(0,before[1]-after[1])){failures++;System.out.println("  FAIL hole count below independent net count");}
            String sentence=s.sentence(.8f,.5f);
            if(s.any()!=!sentence.isEmpty()){failures++;System.out.println("  FAIL sentence presence");}
            if(s.totalHoles>0&&!sentence.contains("filled "+s.totalHoles)){failures++;System.out.println("  FAIL sentence omits holes: "+sentence);}
            String status=s.shortSentence(.8f,.5f);
            if(s.any()&&(status.length()>100||StatusLinePolicy.classify(status)!=StatusLinePolicy.Kind.PROBLEM)){failures++;System.out.println("  FAIL status form: "+status);}
            if(countMs>Math.max(250,stageMs/4)){failures++;System.out.println("  FAIL counting is not cheap relative to the stage");}
            if(s.csv(cut).split("\n").length!=cut.layers.size()+1){failures++;System.out.println("  FAIL csv rows");}
        }
        // Untouched geometry reports nothing.
        VinylStyleSuite.Config c=M4ArtBench.suiteConfig(VinylStyleSuite.LOW_POLY,photo,152.4f,.8f,.5f);
        StyleKit.Result gen=VinylStyleSuite.generate(photo.argb,photo.w,photo.h,c);
        CutRepairSummary same=CutRepairSummary.of(gen,gen);
        if(same.any()||!same.sentence(.8f,.5f).isEmpty()){failures++;System.out.println("FAIL identical geometry reported repairs");}
        // Hole filling by the cut stage must be visible to the user. M4 used Crochet at 3 in; the
        // M5 filet redesign is cut-clean by construction, so Chainmail (5 holes filled at 3 in,
        // measured) now carries the reporting check, and Crochet must need no repair at all.
        c=M4ArtBench.suiteConfig(VinylStyleSuite.CHAINMAIL,photo,76.2f,.8f,.5f);
        gen=VinylStyleSuite.generate(photo.argb,photo.w,photo.h,c);
        SuiteCutStage stage=SuiteCutStage.prepare(gen,VinylStyleSuite.CHAINMAIL,.8f,.5f);
        System.out.println("Chainmail 3 in status: "+stage.repairSentence());
        if(stage.repair.totalHoles==0||stage.repairSentence().isEmpty()){failures++;System.out.println("FAIL Chainmail hole filling not reported");}
        StyleKit.Result crochetGen=VinylStyleSuite.generate(photo.argb,photo.w,photo.h,
            M4ArtBench.suiteConfig(VinylStyleSuite.CROCHET,photo,76.2f,.8f,.5f));
        SuiteCutStage crochetStage=SuiteCutStage.prepare(crochetGen,VinylStyleSuite.CROCHET,.8f,.5f);
        if(crochetStage.repair.any()){failures++;System.out.println("FAIL M5 Crochet still needs cut repair: "+crochetStage.repairSentence());}
        if(failures>0)throw new AssertionError(failures+" cut repair summary failures");
        System.out.println("PASS M4 cut-repair summary: removed holes and pieces are reported");
    }
}

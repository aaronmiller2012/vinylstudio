package com.halftonestudio;

/**
 * D7: the cut-ready stage of a suite (art) style render.
 *
 * {@link VinylStyleSuite#prepareForCutting} repairs narrow features (re-raster and re-trace of
 * flagged regions). It changes geometry for about half of style/size cases and costs several
 * times the generation time, so it is not run on every slider release. Instead the final render
 * is followed by this stage in the background; its result replaces the preview, Cut Check counts
 * and the export source. Export reuses a prepared result only when it was prepared from the
 * geometry being exported with the same cut parameters; otherwise export prepares again and the
 * caller must publish that result to the preview, so nothing is regenerated unseen.
 */
final class SuiteCutStage {
    final StyleKit.Result generated,cut;
    final int style;
    final float minimumMm,bridgeMm;
    /** M4: pieces and holes the repair removed (never concealed from the user). */
    final CutRepairSummary repair;

    private SuiteCutStage(StyleKit.Result generated,StyleKit.Result cut,int style,float minimumMm,float bridgeMm){
        this.generated=generated;this.cut=cut;this.style=style;this.minimumMm=minimumMm;this.bridgeMm=bridgeMm;
        this.repair=CutRepairSummary.of(generated,cut);
    }
    /** Short status-line form; empty when nothing was removed. */
    String repairStatus(){return repair.shortSentence(VinylStyleSuite.cutMinimumMm(minimumMm),VinylStyleSuite.generationGapMm(bridgeMm,minimumMm));}
    /** Status sentence describing what repair removed; empty when nothing was. */
    String repairSentence(){
        return repair.sentence(VinylStyleSuite.cutMinimumMm(minimumMm),VinylStyleSuite.generationGapMm(bridgeMm,minimumMm));
    }

    /** Runs on a worker; honors cancellation through StyleKit.checkCancelled. */
    static SuiteCutStage prepare(StyleKit.Result generated,int style,float minimumMm,float bridgeMm){
        if(generated==null)throw new IllegalArgumentException("Missing final style geometry");
        return new SuiteCutStage(generated,VinylStyleSuite.prepareForCutting(generated,style,minimumMm,bridgeMm),
            style,minimumMm,bridgeMm);
    }

    /** True when {@code shown} is exactly this stage's cut geometry for these export parameters. */
    boolean matches(StyleKit.Result shown,int currentStyle,float currentMinimumMm,float currentBridgeMm){
        return shown!=null&&shown==cut&&currentStyle==style&&
            Float.compare(currentMinimumMm,minimumMm)==0&&Float.compare(currentBridgeMm,bridgeMm)==0;
    }

    /** What to prepare from: the stage's original generated geometry when {@code shown} is its
     *  cut result, so conditioning is never applied to already-conditioned geometry. */
    static StyleKit.Result sourceFor(SuiteCutStage stage,StyleKit.Result shown){
        return stage!=null&&shown==stage.cut?stage.generated:shown;
    }
    /** Geometry to export: the prepared result when it matches, else null (prepare and publish first). */
    static StyleKit.Result reusable(SuiteCutStage ready,StyleKit.Result shown,int style,float minimumMm,float bridgeMm){
        return ready!=null&&ready.matches(shown,style,minimumMm,bridgeMm)?ready.cut:null;
    }
}

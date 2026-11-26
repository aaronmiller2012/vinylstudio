package com.halftonestudio;

import android.animation.ValueAnimator;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Google-Photos-inspired, independently designed Android editor.
 * No UI framework dependencies or copyrighted code from other halftone apps.
 */
public class StudioActivity extends Activity {
    private static final int IMPORT=101,EXPORT=102,SAVE_PROJECT=103,OPEN_PROJECT=104, ALTERNATE_EXPORT=108, EXPORT_LIBRARY_ITEM=109, EXPORT_LIBRARY_BACKUP=110, IMPORT_LIBRARY_BACKUP=111, EXPORT_PNG=112, EXPORT_PDF=113, EXPORT_ASSEMBLY_GUIDE=114, EXPORT_CALIBRATION_SVG=115, EXPORT_CALIBRATION_DXF=116, EXPORT_ORIGINAL_CUTS=117, CAPTURE_PHOTO=118;
    private static final int BG=StudioTheme.BG,CANVAS=StudioTheme.CANVAS,SURFACE=StudioTheme.SURFACE,TEXT=StudioTheme.TEXT,MUTED=StudioTheme.MUTED,ACCENT=StudioTheme.ACCENT,SELECTED=StudioTheme.ACCENT_FILL;
    /** New project defaults only; never migrate explicit cut widths in saved files. */
    private static VinylEngine.Settings newProjectSettings(){
        VinylEngine.Settings fresh=VinylEngine.Settings.defaults();
        fresh.minimumMm=VinylEngine.MINIMUM_CUT_WIDTH_MM;
        fresh.cricutMat=CricutMatPreflight.MAT_12X12; // Explore Air 2 default
        return fresh;
    }
    private VinylEngine.Settings settings=newProjectSettings();
    /** Output preference is UI-only; editing geometry and old saved projects stay unchanged. */
    private int outputDeviceId=OutputDeviceProfile.CRICUT;
    private void chooseOutputDevice(){
        final String[] options=OutputDeviceProfile.names();
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_send_artwork_to))
            .setSingleChoiceItems(options,outputDeviceId,(dialog,which)->{
                if(outputDeviceId!=OutputDeviceProfile.byId(which).id)projectReviewProgress.invalidate();
                outputDeviceId=OutputDeviceProfile.byId(which).id;
                getPreferences(MODE_PRIVATE).edit().putInt("outputDeviceId",outputDeviceId).apply();
                dialog.dismiss();setToolInternal(7);
            }).setNegativeButton("Cancel",null).show();
    }
    private void writeOutputHandoff(ZipOutputStream zip,int deviceId) throws IOException {
        putZip(zip,"output-target.txt",OutputDeviceProfile.handoffText(deviceId));
    }
    private Bitmap photo,originalPhoto;
    // Bounded original camera encoding loaded from a portable project archive.
    // Cut ZIPs never include this; the renderer continues using the working preview.
    private byte[] restoredOriginalSource;
    private volatile boolean showCutRiskMarkers=false;
    private volatile VinylEngine.Result cutRiskResult;
    private volatile List<CutWarningLocator.Marker> cutRiskMarkers=Collections.emptyList();
    private volatile Object specialtyRiskSource;
    private volatile List<CutWarningLocator.Marker> specialtyRiskMarkers=Collections.emptyList();
    private Object currentSpecialtyRiskSource(){
        if(newStyleActive())return newStyleResult;
        if(tonalStencilMode)return tonalResult;
        if(layeredCellMode)return layeredResult;
        if(tessellationMode)return tessellationResult;
        if(spiralRibbonResult!=null&&continuousStyle==ContinuousLineModes.LineStyle.SPIRAL)return spiralRibbonResult;
        if(activeCurveFamily()&&curveVinylRibbon)return curveRibbonResult;
        return null; // Pen/engrave centerlines and maze walls are not blade-cut polygons.
    }
    private void toggleSpecialtyCutRiskMarkers(){
        if(showCutRiskMarkers){showCutRiskMarkers=false;artwork.invalidate();return;}
        if(!specialtyFinalReady()){toast(getString(R.string.ui_finish_rendering_before_inspecting_cut_risks));return;}
        final Object source=currentSpecialtyRiskSource();
        if(source==null){toast(getString(R.string.ui_this_is_a_pen_path_not));return;}
        final int token=alternateGeneration;
        final float minimum=settings.minimumMm;
        final float gap=StyleCutGeometryPreflight.exportGapMm(settings.bridgeMm,minimum);
        final int styleId=newStyleId;
        showCutRiskMarkers=true;
        if(source==specialtyRiskSource){artwork.invalidate();return;}
        specialtyRiskSource=null;specialtyRiskMarkers=Collections.emptyList();
        diagnosticsExecutor.execute(()->{
            try{
                final List<CutWarningLocator.Marker> marks;
                if(source instanceof SpiralPortraitRibbon.Result){
                    ArrayList<CutWarningLocator.Marker> found=new ArrayList<>();
                    for(float[] mm:((SpiralPortraitRibbon.Result)source).narrowCutLocations(minimum,80))
                        found.add(new CutWarningLocator.Marker(0,mm[0],mm[1],"Narrow spiral ribbon"));
                    marks=Collections.unmodifiableList(found);
                }else if(source instanceof StyleKit.Result){
                    ArrayList<CutWarningLocator.Marker> found=new ArrayList<>();
                    for(StyleCutGeometryPreflight.Warning warning:
                            StyleCutGeometryPreflight.inspect((StyleKit.Result)source,minimum,
                                gap,80)){
                        int physicalSlot=NewStyleColorSlots.slot(styleId,
                            ((StyleKit.Result)source).layers.get(warning.layer).name,warning.layer);
                        found.add(new CutWarningLocator.Marker(physicalSlot,warning.midXMm,warning.midYMm,warning.reason));
                    }
                    marks=Collections.unmodifiableList(found);
                }else marks=SpecialtyCutWarningLocator.locate(source,minimum,80);
                postUi(()->{
                    if(!showCutRiskMarkers||token!=alternateGeneration||
                        !specialtyFinalReady()||source!=currentSpecialtyRiskSource())return;
                    specialtyRiskMarkers=marks;specialtyRiskSource=source;
                    artwork.invalidate();
                    toast(marks.isEmpty()?"No sampled narrow pieces found; a test cut is still required":
                        marks.size()+" possible narrow cut pieces highlighted");
                });
            }catch(java.util.concurrent.CancellationException canceled){
                // A newer render replaced this geometry; do not show stale results.
            }catch(RuntimeException e){
                android.util.Log.w("VinylCutCheck","Specialty cut review failed",e);
            }
        });
    }
    /** Old-generation markers are never displayed with a new render. */
    private void toggleCutRiskMarkers(){
        if(showCutRiskMarkers){showCutRiskMarkers=false;artwork.invalidate();return;}
        if(!standardFinalReady()){toast(getString(R.string.ui_finish_rendering_before_inspecting_cut_risks));return;}
        final VinylEngine.Result reference=latest;
        final int generation=serial;
        showCutRiskMarkers=true;
        if(cutRiskResult==reference){artwork.invalidate();return;}
        cutRiskResult=null;cutRiskMarkers=Collections.emptyList();
        diagnosticsExecutor.execute(()->{
            List<CutWarningLocator.Marker> locations=CutWarningLocator.locate(reference,80);
            postUi(()->{
                if(serial!=generation||reference!=latest||!showCutRiskMarkers)return;
                cutRiskMarkers=locations;cutRiskResult=reference;
                artwork.invalidate();
                toast(locations.isEmpty()?"No sampled narrow cuts located; still test cut":
                    locations.size()+" sampled cut locations highlighted");
            });
        });
    }

    /** Incremented whenever the underlying photographic content changes. */
    private volatile long photoRevision=1L;
    private final PortraitMapCache portraitCache=new PortraitMapCache();
    /** Bounded, exact-key cache of immutable specialty preparation snapshots.
     * Never cache a draft as the final result: max processing side belongs to the key.
     * The two phases let lighting changes reuse the expensive snip/crop operation.
     */
    private final RenderPreparationCache<String,SpecialtyBase> specialtyBaseCache=
        new RenderPreparationCache<>(2,12L*1024*1024,
            value->bitmapBytes(value.bitmap)+4L*value.pixels.length);
    private final RenderPreparationCache<String,SpecialtyPrepared> specialtyLitCache=
        new RenderPreparationCache<>(2,18L*1024*1024,
            value->bitmapBytes(value.bitmap)+bitmapBytes(value.unlit)+
                4L*value.pixels.length+4L*value.unlitPixels.length);
    /** Conservative accounting: avoid holding onto more specialty snapshots than needed. */
    private static long bitmapBytes(Bitmap bitmap){
        return bitmap==null?0L:4L*bitmap.getWidth()*bitmap.getHeight();
    }
    private static String specialtySourceKey(long revision,Bitmap source,SnipMask snips,int fillMode,boolean circular,int maxSide){
        StringBuilder key=new StringBuilder(160).append(revision).append('|').append(source.getWidth()).append('|')
            .append(source.getHeight()).append('|').append(fillMode).append('|').append(circular)
            .append('|').append(maxSide);
        if(snips!=null)for(SnipMask.Operation op:snips.operations()){
            key.append('|').append(op.kind).append(':').append(op.xy.length);
            for(float value:op.xy)key.append(',').append(Float.floatToIntBits(value));
        }
        return key.toString(); // exact encoding, no lossy hash collisions
    }
    private static String specialtyLightingKey(String base,VinylEngine.Settings settings){
        return base+'|'+settings.levelsBlack+'|'+settings.levelsWhite+'|'
            +Float.floatToIntBits(settings.exposureStops)+'|'+Float.floatToIntBits(settings.photoContrast)+'|'
            +Float.floatToIntBits(settings.levelsGamma)+'|'+Float.floatToIntBits(settings.levelsShadows)+'|'
            +Float.floatToIntBits(settings.levelsHighlights)+'|'+Float.floatToIntBits(settings.edgePriority);
    }
    private SpecialtyPrepared specialtyPrepared(long revision,Bitmap source,SnipMask snips,int fillMode,boolean circular,
            boolean draft,VinylEngine.Settings adjustments){
        int maxSide=draft?480:850;
        String baseKey=specialtySourceKey(revision,source,snips,fillMode,circular,maxSide);
        String fullKey=specialtyLightingKey(baseKey,adjustments);
        SpecialtyPrepared cached=specialtyLitCache.get(fullKey);
        if(cached!=null)return cached;
        SpecialtyBase base=specialtyBaseCache.get(baseKey);
        if(base==null){
            Bitmap prepared;
            if(fillMode!=0&&snips!=null&&!snips.isEmpty()){
                int w=source.getWidth(),h=source.getHeight();int[] sourcePixels=new int[w*h];
                source.getPixels(sourcePixels,0,w,0,0,w,h);
                int[] filled=SnipBackground.apply(sourcePixels,w,h,snips,fillMode);
                Bitmap replacement=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                replacement.setPixels(filled,0,w,0,0,w,h);
                prepared=SpecialtyCutSafety.prepare(replacement,null,circular);
            }else prepared=SpecialtyCutSafety.prepare(source,snips,circular);
            int largest=Math.max(prepared.getWidth(),prepared.getHeight());
            if(largest>maxSide){float factor=maxSide/(float)largest;
                prepared=Bitmap.createScaledBitmap(prepared,Math.max(24,Math.round(prepared.getWidth()*factor)),
                    Math.max(24,Math.round(prepared.getHeight()*factor)),true);
            }
            int w=prepared.getWidth(),h=prepared.getHeight();int[] input=new int[w*h];
            prepared.getPixels(input,0,w,0,0,w,h);
            base=new SpecialtyBase(prepared,input);
            if(Thread.currentThread().isInterrupted())throw new CancellationException("Stale photo preparation");
            specialtyBaseCache.put(baseKey,base);
        }
        Bitmap rendered=base.bitmap;
        int[] argb=base.pixels;
        if(!ImageLevels.isNeutral(adjustments)){
            argb=ImageLevels.apply(base.pixels,base.bitmap.getWidth(),base.bitmap.getHeight(),adjustments);
            if(Thread.currentThread().isInterrupted())throw new CancellationException("Stale lighting render");
            rendered=Bitmap.createBitmap(base.bitmap.getWidth(),base.bitmap.getHeight(),Bitmap.Config.ARGB_8888);
            rendered.setPixels(argb,0,base.bitmap.getWidth(),0,0,base.bitmap.getWidth(),base.bitmap.getHeight());
            // Preserve the old final-render contract: generators sampled pixels
            // from this Android bitmap, which can round premultiplied alpha.
            int[] bitmapArgb=new int[argb.length];
            rendered.getPixels(bitmapArgb,0,base.bitmap.getWidth(),0,0,base.bitmap.getWidth(),base.bitmap.getHeight());
            argb=bitmapArgb;
        }
        cached=new SpecialtyPrepared(rendered,base.bitmap,argb,base.pixels);
        specialtyLitCache.put(fullKey,cached);
        return cached;
    }
    private boolean skipNextPhotoPaletteSuggestion=false;
    private synchronized void changedPhoto(){projectReviewProgress.invalidate();photoRevision++;draftSourceCache.clear();ScaleAwarePhotoSampler.clearLightCache();invalidatePreparedPalette();portraitCache.photoChanged(photoRevision);specialtyBaseCache.clear();specialtyLitCache.clear();specialtyFieldCache.clear();GeometryTemplateCache.clear();newStyleGeometryMemo.clear();
        boolean restoringPalette=skipNextPhotoPaletteSuggestion;
        skipNextPhotoPaletteSuggestion=false;
        if(!restoringPalette&&stippleActive()&&"Photo-derived".equals(tessPalette)&&photo!=null)suggestStipplePhotoColors();}
    /** Snip presentation is composited by the GPU; no draw-time full-photo pixel buffers. */
    /** Optional independent artwork mode. Existing CMYK engine and project format remain unchanged. */
    private ContinuousLineModes.LineStyle continuousStyle=null;
    private boolean tonalStencilMode=false;
    private boolean tessellationMode=false;
    /** Independent new style ID; legacy GRID_MOTIF and all existing style indices remain stable. */
    private boolean layeredCellMode=false;
    /** Separate Multilayer style; never reinterprets existing Layered Cell projects. */
    private boolean offsetCellMode=false;
    private boolean bottleCapMode=false;
    private boolean fishScaleMode=false;
    private int overprintCellKind=LayeredCellPortraitEngine.OVERPRINT_NONE;
    /** M3: the ONLY method that assigns the artwork-mode fields above and newStyleId.
     * They are derived from one StyleSelection, so no picker can leave two modes on. */
    private void applyStyleSelection(StyleSelection s){
        continuousStyle=s.is(StyleSelection.Kind.CONTINUOUS_LINE)?ContinuousLineModes.LineStyle.valueOf(s.lineStyle):null;
        tonalStencilMode=s.is(StyleSelection.Kind.TONAL_STENCIL);
        mazePortraitMode=s.is(StyleSelection.Kind.MAZE);
        tessellationMode=s.is(StyleSelection.Kind.TESSELLATION);
        layeredCellMode=s.is(StyleSelection.Kind.LAYERED_CELL);
        offsetCellMode=s.offsetCell();bottleCapMode=s.bottleCap();fishScaleMode=s.fishScale();
        overprintCellKind=s.overprintKind;
        newStyleId=s.suiteId;
    }
    /** The current selection, read back from the derived fields. */
    private StyleSelection styleSelection(){
        return StyleSelection.fromLegacyFlags(newStyleId,continuousStyle==null?null:continuousStyle.name(),
            tonalStencilMode,mazePortraitMode,tessellationMode,layeredCellMode,offsetCellMode,bottleCapMode,
            fishScaleMode,overprintCellKind);
    }
    /** Additional supplied vector engines share a single output/preview path. */
    private int newStyleId=-1,newStyleDetail=55,newStyleVariant=0,newStyleColorCount=6,newStyleSelectedLayer=-1;
    private float newStyleLeadMm=1.4f,newStyleWeightMm=.7f;
    /** 0 = original traced detail; 100 = maximum cut-oriented simplification. */
    private int newStyleSmoothing=OrganicFlowCutSmoother.DEFAULT;
    private boolean newStyleMatchVinyl=false;
    /** Independent swatches; never reuse or overwrite Tessellation's palette. */
    private int[] newStyleColors={0xff24252c,0xff71485e,0xffb36e89,0xffe3aa9e,
        0xfff1d6ba,0xff7695b1,0xff694b9d,0xffd87b45,0xffa9c886};
    private final int[][] newStylePaletteMemory=new int[VinylStyleSuite.NAMES.length][];
    private final int[] newStyleCountMemory=new int[VinylStyleSuite.NAMES.length];
    private final boolean[] newStyleManualMemory=new boolean[VinylStyleSuite.NAMES.length];
    /** RC165: distinguishes an explicit choice of photo-auto colors from legacy RC164 auto defaults. */
    private final boolean[] newStylePhotoAutoMemory=new boolean[VinylStyleSuite.NAMES.length];
    private final int[] newStyleDetailMemory=new int[VinylStyleSuite.NAMES.length];
    private final int[] newStyleSmoothingMemory=new int[VinylStyleSuite.NAMES.length];
    private final int[] newStyleVariantMemory=new int[VinylStyleSuite.NAMES.length];
    private final float[] newStyleWeightMemory=new float[VinylStyleSuite.NAMES.length];
    private final float[] newStyleLeadMemory=new float[VinylStyleSuite.NAMES.length];
    private final boolean[] newStyleSettingsSaved=new boolean[VinylStyleSuite.NAMES.length];
    private String newStyleWords="VINYL STUDIO";
    private int newStyleTessMotif=0;
    // Dedicated prismatic controls. Saved per project; sliders only change geometry
    // when this style is active. Other style defaults and palettes are unchanged.
    private float prismaticSpacingMm=.32f;
    private float trioSpacingMm=.40f;
    private int trioOrganic=50;
    // Independent geometry defaults survive switching styles; saved projects
    // also carry each style's last settings in the existing memory array.
    private static final float[] TRIO_SPACING_DEFAULTS={.35f,.45f,.40f,.35f,.35f,.35f,.35f,.35f};
    private static final int[] TRIO_ORGANIC_DEFAULTS={70,50,45,65,65,70,60,85};
    private final float[] trioSpacingMemory=TRIO_SPACING_DEFAULTS.clone();
    private final int[] trioOrganicMemory=TRIO_ORGANIC_DEFAULTS.clone();
    private int prismaticIrregularity=43,prismaticRoundness=52,prismaticFeature=76,prismaticTone=65;
    private PopPosterEngine.Options popOptions=PopPosterEngine.preset(PopPosterEngine.POP,0,254f);
    private PopPosterEngine.Options posterOptions=PopPosterEngine.preset(PopPosterEngine.POSTER,0,254f);
    private PopPosterEngine.Options popPosterOptions(){
        return newStyleId==VinylStyleSuite.POP_PANELS?popOptions:posterOptions;
    }
    private volatile StyleKit.Result newStyleResult;
    /** D7: cut-ready stage of the current suite render; when it matches newStyleResult the preview,
     *  Cut Check and export all use exactly the conditioned cut geometry. UI-thread owned. */
    private SuiteCutStage newStyleCutStage;
    /** Variant and color count of the render that produced newStyleResult (for Cut Check counts). */
    private int newStyleRenderVariant,newStyleRenderColorCount;
    private java.util.concurrent.Future<?> newStyleCutJob;
    private void cancelNewStyleCutPreparation(){
        if(newStyleCutJob!=null){renderController.cancelCutPreparation(newStyleCutJob);newStyleCutJob=null;}
    }
    /** Style switch / project close: stop background cut stages and release their results. */
    private void discardCutReadyStages(){
        cancelNewStyleCutPreparation();cancelStippleCutPreparation();
        newStyleCutStage=null;stippleCutResult=null;stippleCutGeneration=-1;stippleCutFailedGeneration=-1;
    }
    private boolean newStyleCutReady(){
        return SuiteCutStage.reusable(newStyleCutStage,newStyleResult,newStyleId,settings.minimumMm,settings.bridgeMm)!=null;
    }
    /** Publishes cut-ready geometry for the render that produced {@code generated} (UI thread). */
    private void publishNewStyleCut(int token,StyleKit.Result generated,SuiteCutStage stage,
                                    NewStyleVectorCache vectors,StyleLayerOperations.Counts[] operations){
        if(token!=alternateGeneration||alternatePreviewDraft||newStyleResult!=generated)return;
        newStyleCutStage=stage;newStyleResult=stage.cut;newStyleVectors=vectors;
        if(operations!=null)newStyleOperations=operations;
        // Risk markers belong to the geometry they were computed on.
        showCutRiskMarkers=false;specialtyRiskSource=null;specialtyRiskMarkers=Collections.emptyList();
        commitMarkReport();
        String repaired=stage.repairStatus(); // M4: removed geometry is stated, not hidden
        alternateCutWarnings=repaired.isEmpty()?
            "Cut-ready geometry shown; this is what will be cut. A test cut is still required.":repaired;
        alternateStatus=stage.cut.summary+" · "+alternateCutWarnings;
        if(status!=null)status.setText(alternateStatus);
        artwork.invalidate();
        if((activeWorkspace==4&&tool==3)||(activeWorkspace==2&&tool==1))refreshShelfPanel();
    }
    /** Starts background cut preparation after a FINAL suite render (UI thread). */
    private void startNewStyleCutPreparation(int token,StyleKit.Result generated,int style,int variant,int colorCount){
        startNewStyleCutPreparation(token,generated,generated,style,variant,colorCount);
    }
    /** @param shown the result on screen (publication is gated on it); @param prepareFrom never already-conditioned. */
    private void startNewStyleCutPreparation(int token,StyleKit.Result shown,StyleKit.Result prepareFrom,int style,int variant,int colorCount){
        final StyleKit.Result generated=shown;
        cancelNewStyleCutPreparation();
        final float min=settings.minimumMm,bridge=settings.bridgeMm;
        try{
            newStyleCutJob=renderController.cutPreparation().submit(()->{
                try{
                    SuiteCutStage stage=SuiteCutStage.prepare(prepareFrom,style,min,bridge);
                    NewStyleVectorCache vectors=new NewStyleVectorCache(stage.cut);
                    StyleLayerOperations.Counts[] operations=style==VinylStyleSuite.POP_PANELS?null:
                        StyleLayerOperations.bySlot(stage.cut,style,variant,colorCount);
                    if(Thread.currentThread().isInterrupted()||token!=alternateGeneration)return;
                    postUi(()->publishNewStyleCut(token,generated,stage,vectors,operations));
                }catch(CancellationException superseded){
                    // A newer render or an export replaced this preparation.
                }catch(OutOfMemoryError|RuntimeException failure){
                    android.util.Log.w("VinylNewStyles","Background cut preparation failed; export will prepare",failure);
                    postUi(()->{if(token==alternateGeneration&&status!=null)
                        status.setText("Cut-ready geometry unavailable; export will prepare it and show the result");});
                }
            });
        }catch(java.util.concurrent.RejectedExecutionException busy){
            // An export is preparing; it publishes its own cut geometry. Never leave the status
            // claiming a background stage that was not started.
            if(status!=null&&token==alternateGeneration)status.setText(getString(R.string.m5_cut_ready_deferred_to_export));
        }
    }
    private volatile StyleLayerOperations.Counts[] newStyleOperations;
    private volatile NewStyleVectorCache newStyleVectors;
    private boolean newStyleActive(){return newStyleId>=0;}

    private int layeredColumns=26, layeredMotifDepth=3, layeredFamily=LayeredCellPortraitEngine.AUTO;
    private float layeredPhotoDetail=.74f,layeredArtDetail=.60f,layeredSeamMm=.12f,layeredMinFeatureMm=.50f;
    // Legacy projects keep sliced geometry; explicit new selection enables stacked high detail.
    private boolean layeredSolidBase=false,layeredExtraMotif=false;
    private int layeredBackground=-1; // -1 image-selected, otherwise fixed vinyl color layer
    /** -1 means global motif; otherwise cells where this color dominates use its family. */
    private final int[] layeredLayerFamilies={-1,-1,-1,-1,-1,-1,-1,-1};
    private volatile LayeredCellPortraitEngine.Result layeredResult=null;
    private int tessellationStyle=TessellationEngine.VORONOI, tessLayerCount=5,tessDetail=70,tessSelectedLayer=-1;
    private float tessGapMm=.36f,tessEdge=.65f;
    private String tessPalette="Portrait";
    private MultiColorStippleEngine.Options stippleSettings=new MultiColorStippleEngine.Options();
    private boolean stippleHighlightCollisions=false;
    private boolean stippleActive(){return tessellationMode&&tessellationStyle==TessellationEngine.STIPPLE;}
    private void stippleOrderNormalize(){
        boolean[] used=new boolean[8];int[] next=new int[8];int z=0;
        for(int value:stippleSettings.order)if(value>=0&&value<tessLayerCount&&!used[value]){next[z++]=value;used[value]=true;}
        for(int i=0;i<tessLayerCount;i++)if(!used[i])next[z++]=i;
        for(int value:stippleSettings.order)if(value>=tessLayerCount&&value<8&&!used[value]){next[z++]=value;used[value]=true;}
        for(int i=tessLayerCount;i<8;i++)if(!used[i])next[z++]=i;
        stippleSettings.order=next;
    }
    private void moveStippleLayer(int from,int to){
        stippleOrderNormalize();int[] seq=stippleSettings.order;int a=-1,b=-1;
        for(int i=0;i<tessLayerCount;i++){if(seq[i]==from)a=i;if(seq[i]==to)b=i;}
        if(a<0||b<0||a==b)return;
        checkpoint();int value=seq[a];if(a<b)System.arraycopy(seq,a+1,seq,a,b-a);
        else System.arraycopy(seq,b,seq,b+1,a-b);
        seq[b]=value;setToolInternal(3);requestAlternate();
    }
    private void repositionStipple(int from,int position){
        stippleOrderNormalize();int[] seq=stippleSettings.order;int a=-1;
        for(int i=0;i<tessLayerCount;i++)if(seq[i]==from){a=i;break;}
        if(a<0||position<0||position>=tessLayerCount||a==position)return;
        checkpoint();int value=seq[a];if(a<position)System.arraycopy(seq,a+1,seq,a,position-a);
        else System.arraycopy(seq,position,seq,position+1,a-position);
        seq[position]=value;setToolInternal(3);requestAlternate();
    }
    private int[] tessColors={0xff24252c,0xff71485e,0xffb36e89,0xffe3aa9e,0xfff1d6ba,0xff7695b1,0xff694b9d,0xffd87b45};
    /** Kept separate so a full hue scrub never destroys original swatch values. */
    private int[] tessHueBaseColors=tessColors.clone();
    private int[] tessPaletteRanks=SmartPaletteReduction.identity(8);
    private int tessHueDegrees=0;
    // Photo quantization runs asynchronously. Any palette edit, hue change,
    // layer-count change or restored project invalidates older suggestions.
    // Never permit a late callback to undo a user's color selection.
    private long stipplePhotoPaletteRequest=0L;
    /** Orientation belongs to the palette tile, never to the mounting surface. */
    private final java.util.HashMap<String,Boolean> paletteReversed=new java.util.HashMap<>();
    private String selectedPaletteKey="cmyk";
    private volatile int[] preparedPhotoPalette=null;
    /** Matched bounded photo pixels for actual cluster-population scoring. */
    private volatile int[] preparedPhotoColorPixels=null;
    private final SmartPaletteReduction.Workspace paletteReductionWorkspace=new SmartPaletteReduction.Workspace();
    private final SmartPaletteReduction.Workspace asyncReductionWorkspace=new SmartPaletteReduction.Workspace();
    private final LatestRequestWorker reductionWorker=new LatestRequestWorker("VinylPaletteReduction");
    private long reductionRequest;
    private void cancelPaletteReduction(){reductionRequest++;reductionWorker.cancel();}
    private void queuePaletteReduction(int[] colors,int[] ranks,int count,int policy,int required,int eligible,
            java.util.function.Consumer<int[]> commit){
        cancelPaletteReduction();
        final long job=reductionRequest,revision=photoRevision;
        final int standardGeneration=serial,specialGeneration=alternateGeneration;
        final int[] bank=colors.clone(),order=ranks.clone(),samples=preparedPhotoColorPixels;
        reductionWorker.submit(()->{
            try{
                int[] plan=asyncReductionWorkspace.plan(bank,order,count,samples,policy,required,eligible);
                if(Thread.currentThread().isInterrupted())return;
                postUi(()->{
                    if(job!=reductionRequest||revision!=photoRevision||standardGeneration!=serial||
                        specialGeneration!=alternateGeneration||isFinishing()||isDestroyed())return;
                    // If photo analysis arrived during selection, don't apply a
                    // plan scored against the old sample population.
                    if(samples!=preparedPhotoColorPixels){toast("Photo colors changed; select the layer count again");return;}
                    commit.accept(plan);
                });
            }catch(CancellationException cancelled){/* obsolete request */}
            catch(RuntimeException|OutOfMemoryError failure){postUi(()->{if(job==reductionRequest)toast(getString(R.string.ui_unable_to_select_vinyl_colors));});}
        });
    }
    // Bounded final-only filter cache; draft/UI scopes cannot evict final fields.
    private final RenderImageFieldCache specialtyFieldCache=new RenderImageFieldCache(3,12L*1024*1024);
    private final NewStyleGeometryMemo newStyleGeometryMemo=new NewStyleGeometryMemo();
    private long specialtyVisibleRevision=-1;
    private int specialtyVisibleStyle=-1,specialtyVisibleVariant=-1;
    private volatile String preparedPhotoPaletteKey="";
    private long preparedPhotoRequest=0L;
    /** Palette suggestions are latest-only; diagnostic and export work cannot delay them. */
    private final LatestRequestWorker paletteWorker=new LatestRequestWorker("VinylPalette");
    private int newStyleHueDegrees=0;
    private int[] newStyleHueBaseColors=newStyleColors.clone();
    private int[] newStylePaletteRanks=SmartPaletteReduction.identity(9);
    private final int[][] newStyleRankMemory=new int[VinylStyleSuite.NAMES.length][];
    private boolean reversedPalette(String key){return Boolean.TRUE.equals(paletteReversed.get(key));}
    private void invalidatePreparedPalette(){
        preparedPhotoPaletteKey="";preparedPhotoPalette=null;preparedPhotoColorPixels=null;paletteReductionWorkspace.clear();
        preparedPhotoRequest++;paletteWorker.cancel();cancelPaletteReduction();
    }
    /** A photo palette is a suggestion. Never overwrite colors edited by the user. */
    private void ensurePreparedPhotoPalette(){
        if(photo==null)return;
        final Bitmap source=photo;
        final String key=specialtySourceKey(photoRevision,source,settings.snips,0,settings.circleCrop,160)
            +"|"+specialtyLightingKey("lighting",settings);
        if(key.equals(preparedPhotoPaletteKey))return;
        final long job=++preparedPhotoRequest;
        preparedPhotoPaletteKey=key;preparedPhotoPalette=null;preparedPhotoColorPixels=null;
        final SnipMask mask=settings.snips==null?null:settings.snips.copy();
        final boolean circle=settings.circleCrop;
        final VinylEngine.Settings levels=settings.copy();
        try{
            paletteWorker.submit(()->{
                Bitmap prepared=null,scaled=null;
                try{
                    if(Thread.currentThread().isInterrupted())return;
                    prepared=SpecialtyCutSafety.prepare(source,mask,circle);
                    int largest=Math.max(prepared.getWidth(),prepared.getHeight());
                    scaled=prepared;
                    if(largest>160){
                        float scale=160f/largest;
                        scaled=Bitmap.createScaledBitmap(prepared,
                            Math.max(1,Math.round(prepared.getWidth()*scale)),
                            Math.max(1,Math.round(prepared.getHeight()*scale)),true);
                    }
                    if(Thread.currentThread().isInterrupted())return;
                    int w=scaled.getWidth(),h=scaled.getHeight();
                    int[] rgba=new int[w*h];
                    scaled.getPixels(rgba,0,w,0,0,w,h);
                    if(!ImageLevels.isNeutral(levels))rgba=ImageLevels.apply(rgba,w,h,levels);
                    // The mutable working array may be replaced; capture a final reference.
                    final int[] palettePixels=rgba;
                    final int[] suggested=PhotoPaletteExtractor.extract(palettePixels,w,h);
                    if(Thread.currentThread().isInterrupted())return;
                    postUi(()->{
                        if(isFinishing()||isDestroyed()||photo!=source||job!=preparedPhotoRequest||
                            !key.equals(preparedPhotoPaletteKey))return;
                        preparedPhotoPalette=suggested;
                        preparedPhotoColorPixels=palettePixels;
                        updateAutomaticSnipFoundation(suggested,palettePixels,false);
                        if(tool==2){invalidateToolPanels();refreshShelfPanel();}
                    });
                }catch(OutOfMemoryError oom){
                    // Avoid retaining partially prepared bitmap data; preserve manual swatches.
                    android.util.Log.e("VinylPalette","Photo palette memory limit",oom);
                    postUi(()->paletteFailure(job,key,"Not enough memory to sample photo colors"));
                }catch(java.util.concurrent.CancellationException canceled){
                    // New photo, style, or project superseded the request.
                }catch(RuntimeException ex){
                    android.util.Log.w("VinylPalette","Photo palette unavailable",ex);
                    postUi(()->paletteFailure(job,key,"Could not analyze photo colors"));
                }finally{
                    // SpecialtyCutSafety.prepare returns the shared photo if no mask is applied.
                    // Never recycle a bitmap owned by the editor or another render lane.
                    if(scaled!=null&&scaled!=prepared&&scaled!=source&&!scaled.isRecycled())scaled.recycle();
                    if(prepared!=null&&prepared!=source&&!prepared.isRecycled())prepared.recycle();
                }
            });
        }catch(java.util.concurrent.RejectedExecutionException ignored){
            paletteFailure(job,key,"Palette worker is unavailable");
        }
    }
    private void paletteFailure(long job,String key,String reason){
        if(job!=preparedPhotoRequest||!key.equals(preparedPhotoPaletteKey)||isFinishing()||isDestroyed())return;
        preparedPhotoPalette=null;preparedPhotoColorPixels=null;preparedPhotoPaletteKey="";
        android.util.Log.w("VinylPalette",reason);
        if(tool==2){invalidateToolPanels();refreshShelfPanel();}
    }

    private void invalidateStipplePhotoPaletteRequest(){stipplePhotoPaletteRequest++;}
    private void setTessBasePalette(int[] colors) {
        if(colors==null||colors.length!=8)throw new IllegalArgumentException("Expected eight vinyl swatches");
        invalidateStipplePhotoPaletteRequest();
        tessHueBaseColors=colors.clone();tessHueDegrees=0;tessColors=colors.clone();
        tessPaletteRanks=SmartPaletteReduction.identity(8);
    }
    /** Count selection parks whole swatch identities, not just the last colors. */
    private void reduceTessPalette(int n){
        boolean structural=layeredCellMode&&(offsetCellMode||layeredSolidBase||overprintCellKind>0);
        int required=layeredCellMode&&layeredBackground>=0&&layeredBackground<8?1<<layeredBackground:0;
        int eligible=255;
        if(stippleActive()){eligible=0;for(int i=0;i<8;i++)if(stippleSettings.enabled[i])eligible|=1<<i;}
        int[] p=paletteReductionWorkspace.plan(tessColors,tessPaletteRanks,n,preparedPhotoColorPixels,
            structural?SmartPaletteReduction.STRUCTURAL:SmartPaletteReduction.PORTRAIT,required,eligible);
        applyTessReduction(p);
    }
    private void applyTessReduction(int[] p){
        tessColors=SmartPaletteReduction.permute(tessColors,p);
        tessHueBaseColors=SmartPaletteReduction.permute(tessHueBaseColors,p);
        tessPaletteRanks=SmartPaletteReduction.permute(tessPaletteRanks,p);
        int[] families=SmartPaletteReduction.permute(layeredLayerFamilies,p);
        System.arraycopy(families,0,layeredLayerFamilies,0,8);
        layeredBackground=SmartPaletteReduction.remap(layeredBackground,p);
        stippleSettings.colors=SmartPaletteReduction.permute(stippleSettings.colors,p);
        stippleSettings.density=SmartPaletteReduction.permute(stippleSettings.density,p);
        stippleSettings.minDotMm=SmartPaletteReduction.permute(stippleSettings.minDotMm,p);
        stippleSettings.enabled=SmartPaletteReduction.permute(stippleSettings.enabled,p);
        for(int i=0;i<8;i++)stippleSettings.order[i]=SmartPaletteReduction.remap(stippleSettings.order[i],p);
        invalidateStipplePhotoPaletteRequest();
    }
    private void reduceNewStylePalette(int n){
        if((!newStyleMatchVinyl&&newStyleId!=VinylStyleSuite.TESSELLATION_MOTIF)||!NewStyleColorSlots.multi(newStyleId))return; // auto quantizers recalculate from photo
        int capacity=NewStyleColorSlots.maximumSelectable(newStyleId);
        if(n<NewStyleColorSlots.minimumSelectable(newStyleId)||n>capacity)return;
        // Fixed four-sheet ribbon banks must restore their original four sheets.
        int bank=capacity;
        int[] colors=Arrays.copyOf(newStyleColors,bank);
        int[] ranks=Arrays.copyOf(newStylePaletteRanks,bank);
        int policy=SmartPaletteReduction.policyForStyle(newStyleId,newStyleVariant);
        int[] p=paletteReductionWorkspace.plan(colors,ranks,n,preparedPhotoColorPixels,policy);
        applyNewStyleReduction(p);
    }
    private void applyNewStyleReduction(int[] p){
        newStyleColors=SmartPaletteReduction.permute(newStyleColors,p);
        newStyleHueBaseColors=SmartPaletteReduction.permute(newStyleHueBaseColors,p);
        newStylePaletteRanks=SmartPaletteReduction.permute(newStylePaletteRanks,p);
    }
    private static JSONArray paletteRanksJson(int[] ranks){JSONArray a=new JSONArray();for(int r:ranks)a.put(r);return a;}
    private static int[] loadPaletteRanks(JSONArray a,int n){
        if(a==null||a.length()!=n)return SmartPaletteReduction.identity(n);
        int[] p=new int[n];for(int i=0;i<n;i++)p[i]=a.optInt(i,-1);
        return SmartPaletteReduction.validRanks(p,n);
    }
    private void applyTessHue(int degrees) {
        invalidateStipplePhotoPaletteRequest();
        tessHueDegrees=PaletteHueShift.clampDegrees(degrees);
        tessColors=PaletteHueShift.rotate(tessHueBaseColors,tessHueDegrees);
    }
    private void preserveManualTessColor(int index,int color) {
        invalidateStipplePhotoPaletteRequest();
        tessHueBaseColors[index]=PaletteHueShift.rotateOne(color,-tessHueDegrees);
        tessColors[index]=color; // Keep the exact user-selected ARGB value on commit.
    }

    /** Standard CMYK, spot and line/shape styles use their own reversible palette. */
    private final int[] standardHueBaseColors=new int[8];
    private int[] standardPaletteRanks=SmartPaletteReduction.identity(8);
    private int[] tonalPaletteRanks=SmartPaletteReduction.identity(8);
    /** CMYK accent swatches are retained even while their layer slots are inactive.
     * Black changes its physical position as accents are added, so it cannot
     * share an index-based storage slot with them.
     */
    private final int[] cmykAccentHueBaseColors=new int[4];
    private int standardHueDegrees=0;
    /** Source-derived tonal stencil colors rotate after photo segmentation. */
    private int tonalHueDegrees=0;
    private void setStandardParkedPalette(int[] colors,int hue){
        if(settings.mode==VinylEngine.CMYK||colors==null||colors.length!=8)return;
        int count=settings.layers.size();ArrayList<VinylEngine.Layer> previous=new ArrayList<>(parkedVinylLayers);
        parkedVinylLayers.clear();
        for(int i=7;i>=count;i--){
            int from=previous.size()-1-(i-count);
            VinylEngine.Layer layer=from>=0?previous.get(from):new VinylEngine.Layer("Vinyl "+(i+1),colors[i]);
            if(from<0){layer.spacing=settings.masterSpacingMm;layer.density=settings.masterDensity;layer.contrast=settings.masterContrast;}
            layer.color=PaletteHueShift.rotateOne(colors[i],hue);layer.cmykChannel=-1;
            parkedVinylLayers.add(layer);
        }
        standardPaletteRanks=SmartPaletteReduction.identity(8);
    }
    private void rebaseStandardHue(){
        standardPaletteRanks=SmartPaletteReduction.identity(8);
        java.util.Arrays.fill(standardHueBaseColors,0);
        for(int i=0;i<settings.layers.size()&&i<8;i++)
            standardHueBaseColors[i]=settings.layers.get(i).color;
        if(settings.mode!=VinylEngine.CMYK)for(int i=0;i<parkedVinylLayers.size();i++){
            int slot=settings.layers.size()+parkedVinylLayers.size()-i-1;
            if(slot<8)standardHueBaseColors[slot]=parkedVinylLayers.get(i).color;
        }
        java.util.Arrays.fill(cmykAccentHueBaseColors,0);
        if(settings.mode==VinylEngine.CMYK)for(int i=3;i<settings.layers.size()-1;i++)
            cmykAccentHueBaseColors[i-3]=standardHueBaseColors[i];
        standardHueDegrees=0;
    }
    private void ensureStandardHueBase(){
        if(standardHueBaseColors[0]==0)rebaseStandardHue();
    }
    private void applyStandardHue(int degrees){
        ensureStandardHueBase();
        standardHueDegrees=PaletteHueShift.clampDegrees(degrees);
        for(int i=0;i<settings.layers.size()&&i<8;i++)
            settings.layers.get(i).color=PaletteHueShift.rotateOne(standardHueBaseColors[i],standardHueDegrees);
        // Inactive custom layers must follow hue edits, so reactivation shows the
        // same palette, not an outdated color from the moment they were parked.
        if(settings.mode!=VinylEngine.CMYK){
            for(int i=0;i<parkedVinylLayers.size();i++){
                int slot=settings.layers.size()+parkedVinylLayers.size()-i-1;
                if(slot<8&&standardHueBaseColors[slot]!=0)
                    parkedVinylLayers.get(i).color=PaletteHueShift.rotateOne(
                        standardHueBaseColors[slot],standardHueDegrees);
            }
        }
    }
    private void preserveManualStandardColor(int index,int color){
        ensureStandardHueBase();
        standardHueBaseColors[index]=PaletteHueShift.rotateOne(color,-standardHueDegrees);
        settings.layers.get(index).color=color;
    }
    private int currentVinylHue(){
        return newStyleActive()?newStyleHueDegrees:
            tessellationMode||layeredCellMode?tessHueDegrees:
            tonalStencilMode?tonalHueDegrees:standardHueDegrees;
    }
    /** One reverse action applies to the actual edited vinyl colors, not an old
     * palette card or cached source ordering. Preserve hue, dormant sheets, and
     * physical lead / Y-K anchors; applying twice restores the exact colors. */
    private void reverseActiveVinylPalette(){
        if(photo==null){toast(getString(R.string.ui_import_a_photo_first));return;}
        if(continuousStyle!=null||mazePortraitMode){toast(getString(R.string.ui_this_style_has_no_editable_color));return;}
        if(tonalStencilMode&&!tonalReadyForPaletteEdit()){
            toast(getString(R.string.ui_finish_rendering_stencil_colors_before_reversing));return;
        }
        if(newStyleActive()&&!newStyleMatchVinyl&&
           (newStyleResult==null||alternateReadyGeneration!=alternateGeneration||alternatePreviewDraft)){
            toast(getString(R.string.ui_finish_rendering_photo_colors_before_editing));return;
        }
        checkpoint();
        if(newStyleActive()){
            if(!freezeRenderedNewStylePalette())return;
            int count=NewStyleColorSlots.slotCount(newStyleId,newStyleVariant,newStyleColorCount);
            boolean keepLead=NewStyleLayerPolicy.isStructuralLayer(newStyleId,0);
            newStyleHueBaseColors=PaletteOrientation.reverseActive(newStyleHueBaseColors,count,true,keepLead);
            newStylePaletteRanks=SmartPaletteReduction.identity(9);
            applyCurrentVinylHue(newStyleHueDegrees);
        }else if(tessellationMode||layeredCellMode){
            // L1 is a real silhouette for Offset Cell and enhanced Layered Cell.
            // Never reverse its structural vinyl into an upper detail position.
            boolean keepLead=layeredCellMode&&(offsetCellMode||layeredSolidBase||overprintCellKind>0);
            tessHueBaseColors=PaletteOrientation.reverseActive(tessHueBaseColors,tessLayerCount,true,keepLead);
            tessPaletteRanks=SmartPaletteReduction.identity(8);
            applyTessHue(tessHueDegrees);
        }else if(tonalStencilMode){
            int[] displayed=huePreviewColors(); // Physical L1..LN order, not source IDs.
            int[] sourceOrder=new int[displayed.length];
            for(int i=0;i<sourceOrder.length;i++)sourceOrder[i]=tonalSourceIndex(i);
            int[] changed=PhysicalLayerColorReverse.reverse(
                tonalColorOverrides,displayed,sourceOrder,tonalHueDegrees);
            System.arraycopy(changed,0,tonalColorOverrides,0,changed.length);
            tonalPaletteRanks=SmartPaletteReduction.identity(8);
        }else{
            ensureStandardHueBase();
            int count=settings.layers.size();
            int[] active=java.util.Arrays.copyOf(standardHueBaseColors,count);
            if(settings.mode==VinylEngine.CMYK)
                active=PaletteOrientation.orientCmyk(active,true);
            else active=PaletteOrientation.reverseActive(active,count,true,false);
            System.arraycopy(active,0,standardHueBaseColors,0,count);
            standardPaletteRanks=SmartPaletteReduction.identity(8);
            applyStandardHue(standardHueDegrees);
        }
        // This is a physical order edit, independent of any palette tile's
        // original saved orientation. Do not reapply a preset after this edit.
        selectedPaletteKey="";
        invalidateToolPanels();refreshShelfPanel();refreshHueArtwork();
    }

    /** Tonal colors are presented in physical stack order. Their overrides are
     * stored by source-photo-color index. Use one mapping for every color UI. */
    private int[] tonalDisplayedColors(){
        int count=tonalLayerCount;
        int[] fallback=new int[count];java.util.Arrays.fill(fallback,0xff777777);
        MultilayerStencilEngine.Result result=tonalResult;
        if(result==null||result.layers.size()!=count)return fallback;
        // The completed result is the single source of truth for UI, preview
        // and exported vinyl. Reconstruct colors only while a new render runs.
        if(alternateReadyGeneration==alternateGeneration&&!alternatePreviewDraft &&
                result.options.paletteHueDegrees==tonalHueDegrees){
            int[] exact=new int[count];
            for(int i=0;i<count;i++)exact[i]=result.layers.get(i).color;
            return exact;
        }
        int[] sources=new int[count],rendered=new int[count];
        for(int i=0;i<count;i++){
            MultilayerStencilEngine.Layer layer=result.layers.get(i);
            sources[i]=layer.sourceIndex;rendered[i]=layer.color;
        }
        return TonalPaletteMapping.display(rendered,sources,tonalColorOverrides,
            result.options.paletteHueDegrees,tonalHueDegrees);
    }
    private boolean tonalReadyForPaletteEdit(){
        return tonalResult!=null&&tonalResult.layers.size()==tonalLayerCount&&
            alternateReadyGeneration==alternateGeneration&&!alternatePreviewDraft;
    }
    /** Don't assign user-selected L1 colors to unrelated photographic source IDs. */
    private void parkTonalPresetTail(int[] chosen,int count){
        for(int i=count;i<8;i++)tonalColorOverrides[i]=PaletteHueShift.rotateOne(chosen[i],-tonalHueDegrees);
        tonalPaletteRanks=SmartPaletteReduction.identity(8);
        if(!tonalMatchMyVinyl){
            Integer[] slots={0,1,2,3,4,5,6,7};
            Arrays.sort(slots,(a,b)->Double.compare(ColorWorkspacePalette.lightness(tonalColorOverrides[a]),ColorWorkspacePalette.lightness(tonalColorOverrides[b])));
            for(int i=0;i<8;i++)tonalPaletteRanks[slots[i]]=i;
        }
    }
    /** Source band IDs are not physical stack IDs. Auto/mixed source overrides
     * stay with the quantizer; only a complete explicit material bank is reduced. */
    private void reduceTonalPalette(int n){
        for(int c:tonalColorOverrides)if(c==0)return;
        int[] colors=PaletteHueShift.rotate(tonalColorOverrides,tonalHueDegrees);
        int required=tonalBaseSource>=0&&tonalBaseSource<8?1<<tonalBaseSource:0;
        int[] p=paletteReductionWorkspace.plan(colors,tonalPaletteRanks,n,preparedPhotoColorPixels,SmartPaletteReduction.TONAL,required);
        applyTonalReduction(p);
    }
    private void applyTonalReduction(int[] p){
        // Preserve committed source-band order, including explicit reversals.
        // Newly applied presets establish their canonical dark-to-light ranks.
        int[] next=SmartPaletteReduction.permute(tonalColorOverrides,p);
        System.arraycopy(next,0,tonalColorOverrides,0,8);
        tonalPaletteRanks=SmartPaletteReduction.permute(tonalPaletteRanks,p);
        tonalBaseSource=SmartPaletteReduction.remap(tonalBaseSource,p);
    }
    private boolean applyTonalPhysicalColors(int[] physical,int hue){
        MultilayerStencilEngine.Result result=tonalResult;
        if(result==null||result.layers.size()!=tonalLayerCount||
                alternateReadyGeneration!=alternateGeneration||alternatePreviewDraft){
            toast(getString(R.string.ui_finish_rendering_stencil_colors_before_choosing));return false;
        }
        int[] sources=new int[tonalLayerCount];
        for(int i=0;i<sources.length;i++)sources[i]=result.layers.get(i).sourceIndex;
        int[] mapped=TonalPaletteMapping.fromPhysical(tonalColorOverrides,physical,sources,hue);
        System.arraycopy(mapped,0,tonalColorOverrides,0,tonalColorOverrides.length);
        tonalPaletteRanks=SmartPaletteReduction.identity(8);
        tonalHueDegrees=PaletteHueShift.clampDegrees(hue);
        return true;
    }
    /** A built-in tonal preset is attached to photographic source-lightness IDs,
     * NEVER to physical sheet positions. SourceColorStencilQuantizer assigns
     * source ID 0 to the darkest cluster, while the physical base is selected
     * by coverage and can be any source ID. Existing saved physical palettes
     * still use applyTonalPhysicalColors to preserve explicit operator edits. */
    private boolean applyTonalSourcePaletteColors(int[] picked,int hue){
        if(!tonalReadyForPaletteEdit()){
            toast(getString(R.string.ui_finish_rendering_stencil_colors_before_choosing));return false;
        }
        if(tonalMatchMyVinyl){
            // Explicit roll matching reassigns source owners; its swatch order
            // is operator-defined, not quantizer lightness order.
            return applyTonalPhysicalColors(picked,hue);
        }
        int[] mapped=TonalPhotoPaletteOrder.mapToSource(
            tonalColorOverrides,picked,tonalLayerCount,hue);
        System.arraycopy(mapped,0,tonalColorOverrides,0,tonalColorOverrides.length);
        tonalPaletteRanks=SmartPaletteReduction.identity(8);
        tonalHueDegrees=PaletteHueShift.clampDegrees(hue);
        return true;
    }
    private int[] huePreviewColors(){
        if(newStyleActive())return java.util.Arrays.copyOf(newStyleColors,NewStyleColorSlots.slotCount(newStyleId,newStyleVariant,newStyleColorCount));
        if(tessellationMode||layeredCellMode)return java.util.Arrays.copyOf(tessColors,tessLayerCount);
        if(tonalStencilMode)return tonalDisplayedColors();
        int[] colors=new int[settings.layers.size()];
        for(int i=0;i<colors.length;i++)colors[i]=settings.layers.get(i).color;
        return colors;
    }
    private void applyCurrentVinylHue(int degrees){
        if(newStyleActive()){
            if(!freezeRenderedNewStylePalette())return;
            newStyleHueDegrees=PaletteHueShift.clampDegrees(degrees);
            newStyleColors=PaletteHueShift.rotate(newStyleHueBaseColors,newStyleHueDegrees);
            rememberNewStylePalette();
        }else if(tessellationMode||layeredCellMode)applyTessHue(degrees);
        else if(tonalStencilMode)tonalHueDegrees=PaletteHueShift.clampDegrees(degrees);
        else applyStandardHue(degrees);
    }
    private void refreshHueArtwork(){
        if(newStyleActive()||tessellationMode||layeredCellMode||tonalStencilMode)requestAlternate();
        else requestRender();
    }

    private volatile TessellationEngine.Result tessellationResult=null;
    private boolean mazePortraitMode=false;
    private int mazeGrid=54, mazeDifficulty=1;
    private boolean mazeShowSolution=true;
    private volatile MazePortraitEngine.Result mazeResult=null;
    private int continuousDensity=65, tonalLayerCount=5, tonalBlur=1;
    /** Each 2..8-layer stencil configuration remembers its own 0..100 detail. */
    private final int[] tonalDetailByCount={0,0,40,44,48,52,56,60,64};
    /** Physical preview/SVG stroke width; open centerlines are not cuttable vinyl ribbons. */
    private float continuousStrokeMm=0.35f;
    /** Separate settings for genuine recursive space-filling curve families. */
    private int curveDepth=3;
    private float curvePhotoInfluence=.85f,curveCornerRounding=.15f,curveRotationDegrees=0f;
    private int curveToneMethod=AdaptiveCurvePortrait.HYBRID;
    private float curveToneContrast=.55f,curveShadowBoost=.75f,curveHighlightProtection=.85f,curveEdgePriority=.55f;
    private boolean curveVinylRibbon=false;
    private float curveRibbonWidthMm=1.15f,curveMinFeatureMm=.55f,curveMinGapMm=.45f;
    private volatile SpaceFillingCurves.Result curvePathResult=null;
    private volatile boolean curveDraftPreview=false;
    private volatile SpaceFillingRibbon.Result curveRibbonResult=null;
    private volatile SpiralPortraitRibbon.Result spiralRibbonResult=null;
    private boolean activeCurveFamily(){return ContinuousLineModes.curveFamily(continuousStyle)>=0;}

    private final int[] tonalColorOverrides=new int[8];
    /** Original quantizer cluster identity, not visual stack position. */
    private int tonalBaseSource=-1;
    private boolean tonalAutoWhiteBackground=true;
    private boolean tonalCutLayerPreview=true;
    private boolean tonalIncludeSourcePhoto=false; // legacy saved-project field; no longer authorizes sharing
    private volatile boolean includeSourcePhotoInCutZip=false; // private by default, never restored from a project
    /** Session-only inactive normal vinyl layers; discarded by Reset Project. */
    private final ArrayList<VinylEngine.Layer> parkedVinylLayers=new ArrayList<>();
    private int tonalSelectedLayer=-1; // -1 = ALL; 0..N-1 solo tonal layer
    private float tonalEdge=0.65f;
    private volatile Path continuousPreview=null;
    private volatile MultilayerStencilEngine.Result tonalResult=null;
    /** Tonal blade-loop counts are calculated after the preview is published, off the UI thread. */
    private volatile int[] tonalCutCounts=null;
    private volatile int tonalCutCountGeneration=-1;
    private volatile Bitmap tonalDisplayBitmap=null;
    private volatile Bitmap alternatePreviewBitmap=null;
    private volatile StippleVectorCache stippleVectorCache=null;
    private volatile SpecialtyVectorCache specialtyVectorCache=null;
    private boolean stippleShowCutOutlines=false;
    private volatile String alternateStatus="";
    private volatile int continuousBladeContours=-1,continuousPenPaths=-1,mazeWallSegments=-1;
    private volatile int alternateGeneration=0;
    private volatile int alternateReadyGeneration=-1;
    private Bitmap alternatePreparedSource=null;
    private int alternatePreparedWidth=0,alternatePreparedHeight=0;
    private String alternateCutWarnings="";
    private boolean alternateActive(){return newStyleActive()||continuousStyle!=null||tonalStencilMode||mazePortraitMode||tessellationMode||layeredCellMode;}
    /** A specialty render is usable only when its own final generation and geometry agree.
     * Never substitute an old standard result, even if it remains cached for quick switching. */
    private boolean specialtyFinalReady(){
        if(!alternateActive()||alternatePreviewDraft||alternateReadyGeneration<0||
                alternateReadyGeneration!=alternateGeneration)return false;
        if(newStyleActive())return newStyleResult!=null&&newStyleVectors!=null;
        if(layeredCellMode)return layeredResult!=null;
        if(tessellationMode)return tessellationResult!=null;
        if(tonalStencilMode)return tonalResult!=null;
        if(mazePortraitMode)return mazeResult!=null;
        if(continuousStyle==ContinuousLineModes.LineStyle.SPIRAL)
            return spiralRibbonResult!=null&&!spiralRibbonResult.draft;
        if(continuousStyle!=null&&activeCurveFamily()&&curveVinylRibbon)
            return curveRibbonResult!=null&&!curveRibbonResult.draft&&!curveDraftPreview;
        return continuousStyle!=null&&continuousPreview!=null&&!curveDraftPreview;
    }
    private boolean standardFinalReady(){
        return !alternateActive()&&latest!=null&&renderedSerial==serial&&!standardPreviewDraft;
    }

    private int[] pixels;private int imageW,imageH;
    private volatile VinylEngine.Result latest;
    private volatile VinylDiagnostics.Report latestDiagnostics;
    /** UI-only diagnostic state; rendering and SVG exports remain independent of this advisory scan. */
    private String visibilityIssue;
    private String currentMarkIssue;
    private RegistrationCutGeometry.Report committedMarkReport;
    private int committedMarkHash;
    private boolean assemblyReferenceOnly;
    /** The finished cut paths are authoritative. Unknown styles fail conservatively. */
    private RegistrationCutGeometry.Report activeMarkReport(){
        if(!settings.registration)return null;
        final AutoSilhouetteUnderlay.Output base=currentUnderlay();
        RegistrationCutGeometry.Report report;
        float w=settings.widthMm;
        float h=w*imageH/(float)Math.max(1,imageW);
        float[][] points=settings.marks;
        if(newStyleActive()&&newStyleResult!=null){
            w=newStyleResult.widthMm;h=newStyleResult.heightMm;
            report=RegistrationCutGeometry.style(newStyleResult,settings.markSizeMm,
                settings.registrationClearanceMm,NewStyleExport.frame(newStyleResult,settings.markSizeMm,
                    settings.registrationClearanceMm,points,settings.circleCrop).marks());
        }else if(!alternateActive()&&latest!=null){
            w=latest.settings.widthMm;h=latest.heightMm;points=latest.marks;
            report=RegistrationCutGeometry.standard(latest);
        }else if(layeredCellMode&&layeredResult!=null){
            LayeredCellPortraitEngine.Options o=layeredResult.options;
            w=o.widthMm;h=w*layeredResult.height/(float)Math.max(1,layeredResult.width);points=o.marks;
            report=RegistrationCutGeometry.cells(layeredResult);
        }else if(tessellationMode&&tessellationResult!=null){
            TessellationEngine.Options o=tessellationResult.options;
            w=o.widthMm;h=w*tessellationResult.height/(float)Math.max(1,tessellationResult.width);points=o.marks;
            report=RegistrationCutGeometry.tessellation(tessellationResult);
        }else if(tonalStencilMode&&tonalResult!=null){
            w=tonalResult.widthMm;h=tonalResult.heightMm;points=tonalResult.registrationPoints;
            report=tonalResult.registrationReport();
        }else{
            report=RegistrationCutGeometry.strictArtwork(w,h,settings.markSizeMm,
                settings.registrationClearanceMm,points);
        }
        return RegistrationCutGeometry.underlay(report,w,h,settings.markSizeMm,
            settings.registrationClearanceMm,points,base);
    }
    private void commitMarkReport(){
        try{
            committedMarkReport=activeMarkReport();
            currentMarkIssue=committedMarkReport==null?null:committedMarkReport.firstIssue();
            float[][] marks=settings.marks;
            if(marks==null){float w=artwork==null?settings.widthMm:artwork.gestureWidth();
                float h=artwork==null?settings.widthMm*imageH/(float)Math.max(1,imageW):artwork.gestureHeight();
                marks=RegistrationMarks.automaticPositions(w,h,settings.markSizeMm,settings.registrationClearanceMm,settings.circleCrop);
            }
            committedMarkHash=java.util.Arrays.deepHashCode(marks);
        }catch(RuntimeException error){committedMarkReport=null;currentMarkIssue=error.getMessage();}
    }

    /** Mark-only changes regenerate export-safe geometry in the background, but
     * never swap the completed artwork preview for the source photograph. */
    private boolean registrationRenderPending=false;
    private int tool=0,chosen=-1,markIndex=0,renderedSerial=-1;
    private int workflowStep=0,designType=0;
    private boolean tonalMatchMyVinyl=false; // opt-in; no new per-layer sliders
    private boolean showStackControls=false; // 0 halftone, 1 flat stencil, 2 line portrait
    private TextView workflowNext,workflowHeading,canvasHint,editorSubtitle;
    private ProgressBar renderProgress;
    private LinearLayout channelOverlay;
    private TextView zoomShortcut;
    private String projectLabel="New project";
    private String currentLibraryId=null;
    private final StudioProjectSessionState projectSession=new StudioProjectSessionState();
    private final PendingLibraryExport pendingLibraryExport=new PendingLibraryExport();
    private static final String STATE_PENDING_LIBRARY_EXPORT="pendingLibraryExportId";
    private static final String STATE_PENDING_CAMERA="pendingCameraCaptureUri";
    /** Capture target handed to the camera app; restored if Android kills us while it is open. */
    private Uri pendingCameraCapture;
    private StudioProjectLibrary projectLibrary;
    private StudioLibraryJobs libraryJobs;
    private StudioProjectThumbnails projectThumbnails;
    private boolean renderBusy=false;
    private int previewMode=0; // 0 vinyl, 1 black cut shapes, 2 single layer, 3 photo overlay
    private final int[] workspaceDockDp={208,225,225,210,300,210};
    private int dragStartHeight;
    private LinearLayout phaseBar;
    // One-dock editor: all tools are directly accessible without phase navigation.
    private FrameLayout editorArea;
    private LinearLayout canvasColumn,controlsColumn,favoritesRow,miniPanel;
    private FrameLayout canvasStage;
    private HorizontalScrollView favoritesScroller;
    private TextView fullScreenHandle;
    private boolean canvasFullscreen=false,landscapeSplit=false,miniPanelEnabled=true;
    private final ArrayList<String> quickFavorites=new ArrayList<>();
    private String favoritesSignature=null;
    private String pendingScrollTo=null;
    private HorizontalScrollView layerScroller;
    private final int[] lastStepInPhase={WorkflowFlow.IMPORT,WorkflowFlow.LAYERS,WorkflowFlow.MARKS};
    private int selectedPhase=WorkflowFlow.PREPARE;
    private boolean draggingSheet=false;
    private float sheetTouchY;
    private int sheetStartHeight;
    private LinearLayout workflowProgress;
    private volatile int serial=0;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private volatile boolean activityDestroyed=false;
    /** All asynchronous UI deliveries pass through the lifecycle boundary. */
    private final LifecycleUiGate lifecycleUi=new LifecycleUiGate();
    private boolean postUi(Runnable callback){
        return lifecycleUi.dispatch(handler::post,()->{
            if(!activityDestroyed&&!isFinishing()&&!isDestroyed()){
                lifecycleEditorSnapshot.invalidate();
                callback.run();
            }
        });
    }
    /** One lifecycle owner; IO, startup and diagnostics remain separate scheduling lanes. */
    private final StudioRenderController renderController=new StudioRenderController();
    /** Library project whose kept cut sheets are being exported (lost if Android kills the process). */
    private String pendingOriginalCutsId;
    /** Build 553 share/recovery flows, extracted behind a narrow host interface. */
    private final StudioProductFlows productFlows=new StudioProductFlows(this,new StudioProductFlows.Host(){
        @Override public boolean postUi(Runnable task){return StudioActivity.this.postUi(task);}
        @Override public boolean scheduleDiskJob(Runnable job){return StudioActivity.this.scheduleDiskJob(job);}
        @Override public void runDiagnostics(Runnable job){diagnosticsExecutor.execute(job);}
        @Override public void toast(String message){StudioActivity.this.toast(message);}
        @Override public android.content.SharedPreferences prefs(){return getPreferences(MODE_PRIVATE);}
        @Override public boolean alive(){return !activityDestroyed&&!isFinishing();}
        @Override public boolean startupRestoring(){return startupRestoring;}
        @Override public void importSharedPhoto(Uri uri){
            requestProjectSwitch(()->{cancelStartupRestore();beginPhotoImport(uri);},"the shared photo");
        }
        @Override public void startLibraryBackup(){chooseLibraryBackup(true);}
        @Override public long editRevision(){return projectSession.revision();}
        @Override public String photoToken(){return photo==null?"":photoPairToken(photoRevision);}
    });
    private final ExecutorService executor=renderController.io();
    /** Never block the UI or discard a save silently when disk work is saturated. */
    private boolean scheduleDiskJob(Runnable job){
        try{executor.execute(job);return true;}
        catch(java.util.concurrent.RejectedExecutionException full){
            postUi(()->toast(getString(R.string.ui_storage_queue_is_busy_retry_when)));
            return false;
        }
    }

    /** Only CPU-heavy export preparation uses this lane. ZIP publication,
     * project saves and photos remain on their existing serialized queues. */
    private boolean scheduleCutPreparationJob(Runnable job){
        try{renderController.cutPreparation().execute(job);return true;}
        catch(java.util.concurrent.RejectedExecutionException busy){
            postUi(()->toast(getString(R.string.ui_cut_geometry_preparation_is_busy_finish)));
            return false;
        }
    }

    // Keeps pending UI-delivery resources reachable until accepted/rejected.
    // Activity destruction cancels their ownership without racing worker decode.
    private final java.util.Set<ProjectLoadOwnership> pendingProjectLoads=
        java.util.Collections.newSetFromMap(
            new java.util.concurrent.ConcurrentHashMap<ProjectLoadOwnership,Boolean>());
    private void discardProjectLoad(ProjectLoadOwnership ownership){
        ownership.discard();pendingProjectLoads.remove(ownership);
    }
    private final ExecutorService startupIo=renderController.startupIo();
    private volatile int startupRestoreSerial=0;
    private volatile boolean startupRestoring=false;
    private int startupSavedStep=0;
    private String startupSavedDraft=null;
    private void cancelStartupRestore(){startupRestoreSerial++;startupRestoring=false;}
    private boolean sliderDragging=false;
    private volatile int previewRequestId=0;
    /** Virtual L1 does not consume one of the 2–8 editable vinyl colors. */
    private volatile int autoSilhouetteColor=AutoSilhouetteUnderlay.DEFAULT_COLOR;
    /** Manual L1 color is persistent; background palette suggestions never overwrite it. */
    private boolean autoSilhouetteColorManual=false;
    private void updateAutomaticSnipFoundation(int[] swatches,int[] pixels,boolean alreadyRendering){
        if(autoSilhouetteColorManual||!underlayRequested()||swatches==null||swatches.length==0)return;
        int candidate=FoundationColorPolicy.bestColor(swatches,swatches.length,pixels,false);
        if(candidate==autoSilhouetteColor)return;
        autoSilhouetteColor=candidate;
        if(photo!=null)projectSession.markEdited();
        if(!alreadyRendering){
            // Only the independent, solid Snip layer changed color: existing
            // geometry and registration marks are still identical and cut-safe.
            if(recolorReadySnipBase(candidate))return;
            if(alternateActive())requestAlternate();else requestRender();
        }
    }
    private boolean recolorReadySnipBase(int nextColor){
        AutoSilhouetteUnderlay.Output previous=currentUnderlay();
        if(previous==null)return false; // a pending geometry render must finish normally
        if(newStyleActive()||tessellationMode)specialtyUnderlay=previous.recolor(nextColor);
        else standardUnderlay=previous.recolor(nextColor);
        if(artwork!=null)artwork.invalidate();
        return true;
    }

    /** The one-sheet selection leaves all 2–8 detail-color settings intact but does not cut them. */
    private boolean silhouetteOnlyMode=false;
    /** Transient preview isolation. Never changes saved export sheet selections. */
    private boolean previewBaseOnly=false;
    private boolean silhouetteOnly(){return silhouetteOnlyMode&&underlayRequested();}
    private boolean basePreviewActive(){return previewBaseOnly&&underlayRequested();}
    private void selectSilhouetteOnly(boolean only){
        if(only&&!underlayRequested()){toast(getString(R.string.ui_apply_a_snip_first_to_create));return;}
        if(silhouetteOnlyMode==only)return;
        checkpoint();projectSession.markEdited();projectReviewProgress.invalidate();silhouetteOnlyMode=only;previewBaseOnly=false;chosen=-1;newStyleSelectedLayer=-1;tessSelectedLayer=-1;previewMode=0;
        invalidateToolPanels();rebuildLayers();setToolInternal(3);if(artwork!=null)artwork.invalidate();
    }
    private volatile AutoSilhouetteUnderlay.Output standardUnderlay=null, specialtyUnderlay=null;
    private volatile int standardUnderlaySerial=-1, specialtyUnderlayGeneration=-1;
    private AutoSilhouetteUnderlay.Output currentUnderlay(){
        if(photo==null||settings.snips==null||settings.snips.isEmpty()||AutoSilhouetteUnderlay.hasLegacyBase(settings))return null;
        if(newStyleActive())return AutoSilhouetteUnderlay.specialtyEligible(newStyleId,newStyleVariant,false)
            &&specialtyUnderlayGeneration==alternateReadyGeneration&&alternateReadyGeneration==alternateGeneration?specialtyUnderlay:null;
        if(tessellationMode)return specialtyUnderlayGeneration==alternateReadyGeneration&&alternateReadyGeneration==alternateGeneration?specialtyUnderlay:null;
        if(tonalStencilMode||layeredCellMode||continuousStyle!=null||mazePortraitMode)return null;
        return AutoSilhouetteUnderlay.standardEligible(settings)&&standardUnderlaySerial==renderedSerial
                &&renderedSerial==serial?standardUnderlay:null;
    }
    private boolean underlayRequested(){
        if(photo==null||settings.snips==null||settings.snips.isEmpty()||AutoSilhouetteUnderlay.hasLegacyBase(settings))return false;
        if(newStyleActive())return AutoSilhouetteUnderlay.specialtyEligible(newStyleId,newStyleVariant,false);
        if(tessellationMode)return tessellationStyle==TessellationEngine.STIPPLE
            ||tessellationStyle==TessellationEngine.VORONOI||tessellationStyle==TessellationEngine.GRID_MOTIF;
        if(tonalStencilMode||layeredCellMode||continuousStyle!=null||mazePortraitMode)return false;
        return AutoSilhouetteUnderlay.standardEligible(settings);
    }
    /** Keeps all color-wheel controls visible and reachable on short phone screens. */
    private android.widget.ScrollView scrollColorEditor(android.view.View child){
        android.widget.ScrollView container=new android.widget.ScrollView(this);
        container.setFillViewport(false);
        container.addView(child);
        return container;
    }
    private void changeAutoSilhouetteColor(){
        if(!underlayRequested())return;
        final int initial=autoSilhouetteColor;
        VinylColorEditor editor=new VinylColorEditor(this,initial);
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_l1_solid_snip_vinyl_color))
            .setView(scrollColorEditor(editor)).setNegativeButton("Cancel",null)
            .setPositiveButton("Apply",(dialog,which)->{
                try{
                    int color=editor.getColorOrThrow();
                    if(color==initial)return;
                    checkpoint();autoSilhouetteColor=color;autoSilhouetteColorManual=true;
                    invalidateToolPanels();
                    if(!recolorReadySnipBase(color)){
                        if(alternateActive())requestAlternate();else requestRender();
                    }
                    setToolInternal(3);
                }catch(IllegalArgumentException ex){toast(getString(R.string.ui_could_not_apply_the_selected_vinyl));}
            }).show();
    }
    /** The filled silhouette is the first REAL physical sheet, regardless of renderer.
     *  This card lives in the same layer list as each detail sheet. */
    private void showAutomaticSilhouette(LinearLayout host){
        if(!underlayRequested())return;
        AutoSilhouetteUnderlay.Output base=currentUnderlay();
        LinearLayout card=group(host);
        LinearLayout row=horizontal(card);
        TextView swatch=text("L1",15,contrastText(autoSilhouetteColor),true);
        swatch.setGravity(Gravity.CENTER);
        swatch.setBackground(outlined(autoSilhouetteColor,previewBaseOnly?ACCENT:0xff516171,12));
        row.addView(swatch,new LinearLayout.LayoutParams(dp(60),dp(52)));
        swatch.setOnClickListener(v->changeAutoSilhouetteColor());
        LinearLayout detail=new LinearLayout(this);detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(dp(10),0,dp(3),0);row.addView(detail,new LinearLayout.LayoutParams(0,-2,1));
        detail.addView(text("01  L1 · Solid Snip background · "+colorHex(autoSilhouetteColor),13,TEXT,true));
        detail.addView(text(getString(R.string.ui_filled_vinyl_silhouette),12,MUTED,false));
        detail.addView(text(base==null?"Cut contours pending final render":
            "1 filled shape · "+base.cutContours()+" blade contours",12,ACCENT,true));
        action(row,previewBaseOnly?"All":"View",()->{
            previewBaseOnly=!previewBaseOnly;chosen=-1;newStyleSelectedLayer=-1;
            tessSelectedLayer=-1;tonalSelectedLayer=-1;previewMode=0;
            invalidateToolPanels();setToolInternal(3);if(artwork!=null)artwork.invalidate();
        });
        action(row,"Edit",this::changeAutoSilhouetteColor);
    }

    /** Cut totals belong to the completed standard geometry generation, not a preview. */
    private int[] standardLayerCutCounts=null;
    private int standardCountSerial=-1;
    private static final String CUT_COUNT_TAG_PREFIX="vinyl-cut-count-";
    /** Drafts are display-only even when a renderer produces structurally valid vectors. */
    private volatile boolean alternatePreviewDraft=true;
    private volatile boolean standardPreviewDraft=true;
    /** Visibility scans never block the interactive geometry renderer. */
    private final ExecutorService diagnosticsExecutor=renderController.diagnostics();
    private final BoundedEditHistory undo=new BoundedEditHistory(40,2L*1024*1024),
        redo=new BoundedEditHistory(40,2L*1024*1024);
    private final LifecycleEditorSnapshot lifecycleEditorSnapshot=new LifecycleEditorSnapshot();
    private boolean lifecycleEditorPaused;
    private LinearLayout screen,navRow,subtoolRow,sheetContent,layerBar,chipContainer;
    private HorizontalScrollView subtoolScroller;
    private volatile int activeWorkspace=0; // read by the Prepare-preview worker as a stale guard
    private int builtWorkspace=-1,activeSubtoolPosition=0;
    private HorizontalScrollView workflowScroller;
    private FrameLayout sheetHolder;
    private ScrollView sheetScroll;
    /** Active compact settings pill per workspace tool; no extra More toolbar. */
    private final HashMap<String,String> selectedSettingPills=new HashMap<>();
    /** Small LRU of detached tool panels. Keys include workflow and visual state;
     * mutations invalidate cached controls before they can become stale.
     */
    private final LinkedHashMap<String,LinearLayout> toolPanelCache=new LinkedHashMap<String,LinearLayout>(8,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,LinearLayout> item){return size()>4;}
    };
    private long toolPanelRevision=0;
    private void invalidateToolPanels(){toolPanelRevision++;toolPanelCache.clear();}

    /** Native Android 13+ predictive/system Back, installed only on supported API levels. */
    private Object systemBackCallback;
    private static final String STATE_STEP="vinyl.rc161.workflow";
    private static final String STATE_DRAFT="vinyl.rc161.draft";
    private static final String STATE_FULLSCREEN="vinyl.rc161.fullscreen";
    private static final String STATE_TYPE="vinyl.rc161.type";
    private static final String STATE_EDITOR_SNAPSHOT="vinyl.rc173.editorSnapshot";
    private CanvasView artwork;
    private TextView status,title,toolTitle;
    private final Runnable hideStatusLine=()->{if(status!=null)status.setVisibility(View.GONE);};
    /** D9: show the canvas status line according to its message (never in full-screen canvas). */
    private void applyStatusPolicy(){
        if(status==null)return;
        handler.removeCallbacks(hideStatusLine);
        CharSequence text=status.getText();
        long ms=StatusLinePolicy.displayMs(text);
        if(ms<0||canvasFullscreen){status.setVisibility(View.GONE);return;}
        status.setTextColor(StatusLinePolicy.classify(text)==StatusLinePolicy.Kind.PROBLEM?0xffFFD093:MUTED);
        status.setVisibility(View.VISIBLE);
        if(ms>0)handler.postDelayed(hideStatusLine,ms);
    }
    private boolean panelOpen=false,restoring=false;
    /** Search text survives gallery refreshes after rename/delete; never saved in project ZIPs. */
    private String gallerySearchQuery="";
    /** Reusable animation prevents conflicting dock transitions during rapid tool changes. */
    private ValueAnimator dockAnimator;
    private static final int DOCK_COLLAPSED_DP=57;
    private static final int DOCK_COMPACT_DP=250;
    private static final int DOCK_MAX_PERCENT=38;
    /** Keep one slider per group: the selected control is remembered when changing tools. */
    private final HashMap<String,String> selectedSliderPills=new HashMap<>();
    /** Draft polygon is separate from saved, undoable snips until Apply is tapped. */
    private final ArrayList<float[]> snipDraft=new ArrayList<>();
    private boolean snipClosed=false,snipPan=false;
    private int editingSnipIndex=-1;
    private SnipMask.Operation editingOriginal=null;
    private final SnipTimeline snipTimeline=new SnipTimeline();
    private int snipAction=SnipMask.REMOVE_INSIDE;
    private int chosenProfile=VinylSizing.BALANCED;
    private boolean showOriginal=false;
    private int cutMaterialProfileId=CutMaterialProfile.CUSTOM;
    private volatile int heatmapGeneration=-1,heatmapLayer=-2;
    private volatile StippleCoverageInspector.Scan cachedHeatmap=null;
    private final String[] tabs={"Image","Halftone","Vinyl colors","Stack","Curves","Marks","Cut check","Export","Snip","Artwork type","Levels"};
    /** Keep tool IDs stable for project compatibility; display Snip after Image. */
    private static final String[] STEP_NAMES=WorkflowFlow.NAMES;
    private static final String[] STEP_ICONS={"image","crop","snip","layers","type","grid","marks","download","sun"};
    private static final String[] PHASE_ICONS={"crop","palette","scissors"};
    private static final int[] STEP_TOOLS=WorkflowFlow.TOOL_IDS;
    private final int[] presets={0xff202936,0xffe684a3,0xff67b7cb,0xffeec95c,0xff526c9b,0xffbe704d,0xff86b78c,0xffa987cc,
            0xff0d513d,0xfff3e8d5,0xffed454f,0xff2444aa,0xffdddde4,0xffe38e35};
    /** Source document retained for lossless round-trip of unknown project fields. */
    private JSONObject projectSourceJson;
    private int projectSourceSchema=ProjectSettingsMigration.CURRENT_SCHEMA;
    private boolean newerProjectSchemaWarning;
    /** Engine build recorded in the most recently parsed project settings (0 = pre-553). */
    private int projectEngineBuild=EngineBuild.CURRENT;
    private final Runnable renderDebounce=()->render();
    private static final class PreparedToneCache {
        final int[] pixels;final long signature;
        PreparedToneCache(int[] pixels,long signature){this.pixels=pixels;this.signature=signature;}
    }
    private volatile PreparedToneCache preparedToneCache;
    private final RenderDraftSourceCache draftSourceCache=new RenderDraftSourceCache();
    private volatile Bitmap tonalPreview;
    @Override public void onCreate(Bundle state){super.onCreate(state);
        VectorBooleanEngine.install(new JtsVectorBooleanBackend());
        android.app.ActivityManager memoryManager=(android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);
        ParallelGeometry.configureMemoryBudget(memoryManager!=null&&memoryManager.isLowRamDevice(),Runtime.getRuntime().maxMemory());
        if(state!=null)pendingLibraryExport.restore(state.getString(STATE_PENDING_LIBRARY_EXPORT));
        if(state!=null&&state.getString(STATE_PENDING_CAMERA)!=null)pendingCameraCapture=Uri.parse(state.getString(STATE_PENDING_CAMERA));
        // The system bars are transparent and mandatory edge-to-edge on Android 15+.
        PlatformCompat.colorSystemBars(getWindow(),BG);
        installSystemBackCallback();
        VinylCrashReport.install(getApplicationContext());
        projectLibrary=StudioProjectLibrary.shared(getFilesDir());
        libraryJobs=new StudioLibraryJobs(projectLibrary,task->postUi(task),
            error->VinylCrashReport.recordLibraryError(getApplicationContext(),error));
        projectThumbnails=new StudioProjectThumbnails(handler);
        final android.content.SharedPreferences guidePrefs=getPreferences(MODE_PRIVATE);
        final boolean showFirstTimeGuide=VinylQuickStartGuide.isFirstInstall(this,guidePrefs);
        loadUiPreferences();
        outputDeviceId=OutputDeviceProfile.byId(guidePrefs.getInt("outputDeviceId",OutputDeviceProfile.CRICUT)).id;
        for(int i=0;i<workspaceDockDp.length;i++)workspaceDockDp[i]=getPreferences(MODE_PRIVATE)
            .getInt("workspaceDockHeight"+i,workspaceDockDp[i]);
        buildUI();
        rebuildLayers(); // CMYK arrangement is visible before any photo is imported.
        // D19: the editor exactly as a fresh install has it, captured before any autosave,
        // project or Bundle is applied. New Project restores it, so per-style memory
        // (suite palettes/detail/variant, curve, maze, layered, stipple, material...) can
        // never leak from the previous project into the next photo.
        pristineEditorJson=snapshot();
        restoreLocal();
        // Read legacy inline state only when restoring a Bundle created by an older release.
        // Current releases persist editor and Snip JSON in local preferences.
        if(state!=null){
            String savedEditor=state.getString(STATE_EDITOR_SNAPSHOT);
            if(savedEditor!=null){try{
                JSONObject obj=new JSONObject(savedEditor);
                settings=fromJson(obj);restoreParkedVinylLayers(obj);restoreArtworkModes(obj);
                chosen=-1;rebuildLayers();
            }catch(Exception unreadable){
                // Persisted local settings (restoreLocal, which quarantines bad text) are the fallback.
                android.util.Log.w("VinylStudio","Instance-state editor snapshot unreadable; using autosave",unreadable);
            }}
        }
        // UI-only state is kept small; the actual artwork lives in the atomic project/photo store.
        if(state!=null)startupSavedDraft=state.getString(STATE_DRAFT,startupSavedDraft);
        currentLibraryId=getPreferences(MODE_PRIVATE).getString("currentLibraryId",null);
        projectLabel=currentLibraryId==null?"New project":getPreferences(MODE_PRIVATE).getString("currentLibraryName","Photo project");
        adoptLibraryHandoff(); // D14: a save that finished while the previous Activity was being destroyed
        getPreferences(MODE_PRIVATE).registerOnSharedPreferenceChangeListener(libraryHandoffListener);
        startupSavedStep=getPreferences(MODE_PRIVATE).getInt("workflowStep",WorkflowFlow.SNIP);
        if(state!=null)startupSavedStep=state.getInt(STATE_STEP,startupSavedStep);
        workflowStep=photo==null?0:startupSavedStep;
        for(int phase=0;phase<3;phase++)lastStepInPhase[phase]=getPreferences(MODE_PRIVATE)
            .getInt("lastPhaseStep"+phase,WorkflowFlow.firstStage(phase));
        designType=getPreferences(MODE_PRIVATE).getInt("workflowType",settings.mode==VinylEngine.STENCIL?1:0);
        if(state!=null)designType=state.getInt(STATE_TYPE,designType);
        if(photo==null)workflowStep=0;
        setWorkflowStep(workflowStep);
        if(state!=null&&state.getBoolean(STATE_FULLSCREEN,false))setCanvasFullscreen(true);
        if(photo!=null){requestRender();projectSession.setDirty(getPreferences(MODE_PRIVATE).getBoolean("editorDirty",true));updateEditorSubtitle();}
        if(state==null)productFlows.acceptSharedImage(getIntent());
        if(state==null)postUi(productFlows::maybeOfferCrashReport);
        if(productFlows.hasPendingSharedImage()){
            // A photo shared from another app takes priority over the guide and gallery.
        }else if(showFirstTimeGuide){
            postUi(()->showQuickStartGuide(false,false));
        }else if(photo==null&&!startupRestoring){
            libraryJobs.list(items->{if(!items.isEmpty()&&!isFinishing()&&!isDestroyed())showProjectLibrary();},
                e->android.util.Log.w("VinylLibrary","Could not index startup projects",e));
        }
        // The previous unsolicited swipe toast is superseded by the opt-in guide tips.
    }
    /** Shared view factory; the helpers below delegate so extracted screens build identical views. */
    private final StudioUi ui=new StudioUi(this);
    private int dp(float val){return ui.dp(val);}
    private GradientDrawable shape(int c,float rad){return ui.shape(c,rad);}
    private GradientDrawable outlined(int color,int border,float rad){return ui.outlined(color,border,rad);}
    /** Adds a touch ripple clipped to the same rounded rectangle, so every pill/card gives press feedback. */
    private Drawable ripple(Drawable content,float rad){return ui.ripple(content,rad);}
    private GradientDrawable topRounded(int c,float rad){
        GradientDrawable d=new GradientDrawable();d.setColor(c);float r=dp(rad);
        d.setCornerRadii(new float[]{r,r,r,r,0,0,0,0});return d;
    }
    /** Shared pill look: selected = accent container + accent outline; idle = raised surface + hairline. */
    private Drawable pillBackground(boolean selected,float rad){return ui.pillBackground(selected,rad);}
    private void styleSeekBar(SeekBar bar){
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(StudioTheme.ACCENT_INK));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(StudioTheme.OUTLINE_STRONG));
    }
    /** Round 48dp touch target with ripple for header glyph buttons. */
    private void styleHeaderButton(TextView t){ui.styleHeaderButton(t);}
    private void buildUI(){
        screen=new LinearLayout(this);screen.setOrientation(LinearLayout.VERTICAL);screen.setBackgroundColor(BG);
        setContentView(screen);
        screen.setOnApplyWindowInsetsListener((v,insets)->{
            int left,top,right,bottom;
            if(Build.VERSION.SDK_INT>=35){
                android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|
                    WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
                left=b.left;top=b.top;right=b.right;bottom=b.bottom;
            }else{
                // Window decor already fits system bars before API 35. Never double-pad.
                left=top=right=bottom=0;
            }
            screen.setPadding(left,top,right,bottom);return insets;
        });
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(16),0,dp(10),0);
        header.setTag("editorHeader");
        screen.addView(header,new LinearLayout.LayoutParams(-1,dp(50)));
        LinearLayout headText=new LinearLayout(this);headText.setOrientation(LinearLayout.VERTICAL);
        header.addView(headText,new LinearLayout.LayoutParams(0,-2,1));
        // Frequent history actions live at the upper right, not in a scrolling toolbar.
        TextView topUndo=text("↶",27,TEXT,true);topUndo.setGravity(Gravity.CENTER);
        topUndo.setContentDescription(topUndo.getContext().getString(R.string.quality_undo_previous_edit));topUndo.setOnClickListener(v->performHistoryAction(false));
        styleHeaderButton(topUndo);header.addView(topUndo,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView topRedo=text("↷",27,TEXT,true);topRedo.setGravity(Gravity.CENTER);
        topRedo.setContentDescription(topRedo.getContext().getString(R.string.quality_redo_previous_edit));topRedo.setOnClickListener(v->performHistoryAction(true));
        styleHeaderButton(topRedo);header.addView(topRedo,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView fullscreenButton=text("⛶",24,TEXT,true);fullscreenButton.setGravity(Gravity.CENTER);
        fullscreenButton.setContentDescription(fullscreenButton.getContext().getString(R.string.quality_toggle_full_screen_artwork));
        fullscreenButton.setOnClickListener(v->setCanvasFullscreen(!canvasFullscreen));
        styleHeaderButton(fullscreenButton);header.addView(fullscreenButton,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView overflow=text("⋮",26,TEXT,true);overflow.setGravity(Gravity.CENTER);
        overflow.setContentDescription(overflow.getContext().getString(R.string.quality_project_options));
        overflow.setOnClickListener(v->showProjectMenu(overflow));
        styleHeaderButton(overflow);header.addView(overflow,new LinearLayout.LayoutParams(dp(48),dp(48)));
        title=text("VinylStudio ▾",17,TEXT,true);
        title.setContentDescription(title.getContext().getString(R.string.quality_open_project_library));
        title.setOnClickListener(v->showProjectLibrary());
        headText.addView(title);
        editorSubtitle=text("New project",13,MUTED,false);editorSubtitle.setSingleLine(true);
        editorSubtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        headText.addView(editorSubtitle);
        phaseBar=null;workflowProgress=null; // Removed entirely; no hidden reserved space.
        editorArea=new FrameLayout(this);
        screen.addView(editorArea,new LinearLayout.LayoutParams(-1,0,1));
        canvasColumn=new LinearLayout(this);canvasColumn.setOrientation(LinearLayout.VERTICAL);
        controlsColumn=new LinearLayout(this);controlsColumn.setOrientation(LinearLayout.VERTICAL);
        artwork=new CanvasView();
        // Accessibility (build 554): the canvas is announced, and pinch/double-tap zoom has a TalkBack action.
        artwork.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        artwork.setContentDescription(getString(R.string.canvas_description_empty));
        artwork.setAccessibilityDelegate(new View.AccessibilityDelegate(){
            @Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){
                super.onInitializeAccessibilityNodeInfo(host,info);
                info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(R.id.canvas_action_reset_zoom,getString(R.string.canvas_action_reset_zoom)));
            }
            @Override public boolean performAccessibilityAction(View host,int action,Bundle args){
                if(action==R.id.canvas_action_reset_zoom){artwork.resetZoom();return true;}
                return super.performAccessibilityAction(host,action,args);
            }
        });
        canvasStage=new FrameLayout(this);canvasStage.setBackgroundColor(CANVAS);
        canvasStage.addView(artwork,new FrameLayout.LayoutParams(-1,-1));
        renderProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        renderProgress.setIndeterminate(true);renderProgress.setVisibility(View.GONE);
        renderProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        canvasStage.addView(renderProgress,new FrameLayout.LayoutParams(-1,dp(3),Gravity.TOP));
        channelOverlay=new LinearLayout(this);channelOverlay.setOrientation(LinearLayout.HORIZONTAL);
        channelOverlay.setGravity(Gravity.CENTER_VERTICAL);channelOverlay.setPadding(dp(5),dp(3),dp(5),dp(3));
        channelOverlay.setBackground(shape(StudioTheme.SCRIM,StudioTheme.R_CHIP));
        FrameLayout.LayoutParams channelsP=new FrameLayout.LayoutParams(-2,dp(38),Gravity.TOP|Gravity.START);
        channelsP.topMargin=dp(12);channelsP.leftMargin=dp(9);
        // Channel chips are no longer overlaid on artwork; use the Layers workspace.
        channelOverlay.setVisibility(View.GONE);
        zoomShortcut=text("100%",13,TEXT,true);zoomShortcut.setGravity(Gravity.CENTER);
        zoomShortcut.setBackground(ripple(outlined(StudioTheme.SCRIM,StudioTheme.OUTLINE,StudioTheme.R_CHIP),StudioTheme.R_CHIP));
        zoomShortcut.setOnClickListener(v->artwork.resetZoom());
        zoomShortcut.setContentDescription(zoomShortcut.getContext().getString(R.string.quality_fit_artwork_to_canvas));
        FrameLayout.LayoutParams zoomP=new FrameLayout.LayoutParams(dp(55),dp(38),Gravity.TOP|Gravity.END);
        zoomP.topMargin=dp(12);zoomP.rightMargin=dp(9);canvasStage.addView(zoomShortcut,zoomP);
        zoomShortcut.setOnLongClickListener(v->{showPreviewMenu(zoomShortcut);return true;});
        canvasColumn.addView(canvasStage,new LinearLayout.LayoutParams(-1,0,1));
        canvasHint=text("Pinch to zoom · drag to pan · double-tap to fit",11,0xffDBE2EB,false);
        canvasHint.setPadding(dp(12),dp(7),dp(12),dp(7));canvasHint.setBackground(outlined(StudioTheme.SCRIM,StudioTheme.OUTLINE,StudioTheme.R_CHIP));
        FrameLayout.LayoutParams hintP=new FrameLayout.LayoutParams(-2,dp(34),Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        hintP.topMargin=dp(8);canvasStage.addView(canvasHint,hintP);
        // No floating sliders: the image is a dedicated canvas, controls only below.
        fullScreenHandle=text(getString(R.string.ui_show_controls),12,TEXT,true);fullScreenHandle.setGravity(Gravity.CENTER);
        fullScreenHandle.setBackground(ripple(outlined(StudioTheme.SCRIM,StudioTheme.ACCENT,StudioTheme.R_PILL),StudioTheme.R_PILL));
        FrameLayout.LayoutParams exitP=new FrameLayout.LayoutParams(dp(148),dp(36),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        exitP.bottomMargin=dp(12);canvasStage.addView(fullScreenHandle,exitP);
        fullScreenHandle.setVisibility(View.GONE);
        fullScreenHandle.setOnClickListener(v->setCanvasFullscreen(false));
        // performClick() is dispatched on ACTION_UP below, so TalkBack activation still works.
        //noinspection ClickableViewAccessibility
        fullScreenHandle.setOnTouchListener(new View.OnTouchListener(){float downY;
            @Override public boolean onTouch(View v,MotionEvent e){
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downY=e.getY();return true;}
                if(e.getActionMasked()==MotionEvent.ACTION_UP){if(downY-e.getY()>dp(12)||Math.abs(downY-e.getY())<=dp(12))v.performClick();return true;}
                return true;
            }
        });
        status=text(getString(R.string.ui_import_a_photograph_to_start),12,MUTED,false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); // announce render/save/export progress
        status.setSingleLine(true);status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(12),0,dp(10),0);canvasColumn.addView(status,new LinearLayout.LayoutParams(-1,dp(18)));
        status.setVisibility(View.GONE);
        status.setOnClickListener(v->setTool(6));
        // D9: every status.setText(...) now becomes visible feedback (StatusLinePolicy):
        // progress stays until replaced, problems stay readable, routine notes auto-hide.
        status.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){}
            public void afterTextChanged(android.text.Editable e){applyStatusPolicy();}
        });
        layerScroller=new HorizontalScrollView(this);layerScroller.setHorizontalScrollBarEnabled(false);layerScroller.setFillViewport(false);layerScroller.setBackgroundColor(BG);
        layerBar=new LinearLayout(this);layerBar.setOrientation(LinearLayout.HORIZONTAL);layerBar.setGravity(Gravity.CENTER_VERTICAL);layerBar.setPadding(dp(10),dp(2),dp(8),dp(2));
        layerScroller.addView(layerBar,new HorizontalScrollView.LayoutParams(-2,-1));
        // Keep CMYK channels visible inside the only bottom controls shelf.
        // Layer selection lives on the dedicated Layers page; no duplicate permanent toolbar.
        favoritesScroller=new HorizontalScrollView(this);favoritesScroller.setHorizontalScrollBarEnabled(false);favoritesScroller.setBackgroundColor(BG);
        favoritesRow=new LinearLayout(this);favoritesRow.setGravity(Gravity.CENTER_VERTICAL);favoritesRow.setPadding(dp(6),0,dp(6),0);
        favoritesScroller.addView(favoritesRow,new HorizontalScrollView.LayoutParams(-2,-1));
        // Quick favorites duplicated the workspace tools; keep it detached for compatibility.
        sheetHolder=new FrameLayout(this);sheetHolder.setBackground(topRounded(SURFACE,StudioTheme.R_SHEET));sheetHolder.setElevation(dp(6));
        controlsColumn.addView(sheetHolder,new LinearLayout.LayoutParams(-1,dp(DOCK_COLLAPSED_DP)));
        LinearLayout sheetRoot=new LinearLayout(this);sheetRoot.setOrientation(LinearLayout.VERTICAL);
        sheetHolder.addView(sheetRoot,new FrameLayout.LayoutParams(-1,-1));
        View grip=new View(this);grip.setBackground(shape(StudioTheme.OUTLINE_STRONG,3));
        LinearLayout gripRow=new LinearLayout(this);gripRow.setGravity(Gravity.CENTER);
        gripRow.addView(grip,new LinearLayout.LayoutParams(dp(36),dp(4)));
        sheetRoot.addView(gripRow,new LinearLayout.LayoutParams(-1,dp(14)));attachSheetDrag(gripRow);
        LinearLayout sheetHead=new LinearLayout(this);sheetHead.setGravity(Gravity.CENTER_VERTICAL);sheetHead.setPadding(dp(16),0,dp(8),0);
        sheetRoot.addView(sheetHead,new LinearLayout.LayoutParams(-1,dp(40)));
        subtoolScroller=new HorizontalScrollView(this);subtoolScroller.setHorizontalScrollBarEnabled(false);subtoolScroller.setBackgroundColor(SURFACE);
        subtoolRow=new LinearLayout(this);subtoolRow.setGravity(Gravity.CENTER_VERTICAL);subtoolRow.setPadding(dp(10),0,dp(10),0);
        subtoolScroller.addView(subtoolRow,new HorizontalScrollView.LayoutParams(-2,-1));
        sheetRoot.addView(subtoolScroller,new LinearLayout.LayoutParams(-1,dp(StudioTheme.H_SUBTOOL+2)));
        subtoolScroller.setVisibility(View.GONE);
        // No duplicate compact slider row: the subtool pills open the actual settings panel.

        toolTitle=text("",16,TEXT,true);toolTitle.setGravity(Gravity.CENTER_VERTICAL);sheetHead.addView(toolTitle,new LinearLayout.LayoutParams(0,-2,1));
        workflowNext=text("",12,ACCENT,true); // Kept for legacy calls, no extra navigation button.
        attachSheetDrag(sheetHead);
        sheetScroll=new ScrollView(this);sheetScroll.setFillViewport(false);sheetScroll.setVerticalScrollBarEnabled(false);
        sheetRoot.addView(sheetScroll,new LinearLayout.LayoutParams(-1,0,1));
        sheetScroll.setVisibility(View.GONE);
        sheetContent=new LinearLayout(this);sheetContent.setPadding(dp(12),dp(2),dp(12),dp(20));sheetContent.setOrientation(LinearLayout.VERTICAL);sheetScroll.addView(sheetContent);
        workflowScroller=new HorizontalScrollView(this);workflowScroller.setHorizontalScrollBarEnabled(false);workflowScroller.setFillViewport(true);workflowScroller.setBackgroundColor(StudioTheme.BG);
        navRow=new LinearLayout(this);navRow.setPadding(dp(6),dp(4),dp(6),dp(4));navRow.setGravity(Gravity.CENTER_VERTICAL);
        workflowScroller.addView(navRow,new HorizontalScrollView.LayoutParams(-1,-1));
        // The dock is part of the same shelf, not a second floating navigation bar.
        controlsColumn.removeView(sheetHolder);
        controlsColumn.addView(workflowScroller,new LinearLayout.LayoutParams(-1,dp(StudioTheme.H_NAV)));
        controlsColumn.addView(sheetHolder,new LinearLayout.LayoutParams(-1,dp(DOCK_COLLAPSED_DP)));
        applyAdaptiveLayout();
        refreshMiniPanel(); // Legacy quick action row is deliberately not mounted.
    }
    private TextView text(String value,int size,int color,boolean strong){return ui.text(value,size,color,strong);}
    private void space(LinearLayout parent,int dp){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(dp)));}
    /** A card has exactly one parent. Some cards belong inside the Layers overview. */
    private LinearLayout group(LinearLayout parent){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(dp(16),dp(16),dp(16),dp(16));b.setBackground(outlined(StudioTheme.SURFACE_HIGH,StudioTheme.OUTLINE,StudioTheme.R_CARD));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(16);parent.addView(b,p);return b;}
    private LinearLayout group(){return group(sheetContent);}
    private void section(LinearLayout parent,String s){
        String friendly=VinylPlainLanguage.label(s);
        String help=VinylPlainLanguage.help(s);
        if(help==null){TextView t=text(friendly,13,StudioTheme.ACCENT_INK,true);t.setLetterSpacing(.03f);parent.addView(t);}
        else{LinearLayout row=horizontal(parent);
            TextView t=text(friendly,13,StudioTheme.ACCENT_INK,true);t.setLetterSpacing(.03f);
            row.addView(t,new LinearLayout.LayoutParams(0,dp(43),1));
            addSettingHelp(row,friendly,help);
        }
        space(parent,6);
    }
    private TextView action(LinearLayout parent,String name,Runnable click){
        String friendly=VinylPlainLanguage.label(name);
        TextView t=text(friendly,14,TEXT,true);
        t.setGravity(Gravity.CENTER);t.setPadding(dp(14),0,dp(14),0);
        t.setBackground(ripple(outlined(StudioTheme.ACCENT_FILL,StudioTheme.OUTLINE_STRONG,StudioTheme.R_PILL),StudioTheme.R_PILL));
        // Minimum (not fixed) height so large system font sizes are never clipped.
        t.setMinHeight(dp(StudioTheme.H_PILL));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);
        lp.rightMargin=dp(8);lp.bottomMargin=dp(6);parent.addView(t,lp);
        String tip=VinylPlainLanguage.help(name);
        if(tip!=null){t.setTooltipText(tip);t.setContentDescription(friendly+". "+tip);}
        t.setOnClickListener(v->click.run());return t;
    }
    private LinearLayout horizontal(LinearLayout parent){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);parent.addView(row,new LinearLayout.LayoutParams(-1,-2));return row;}
    private static String sliderValueText(String label,float value,float increment){
        return SliderRangePolicy.format(value,increment,label.contains("%"));
    }
    private void slider(LinearLayout parent,String label,float value,float min,float max,float increment,java.util.function.Consumer<Float> callback){
        LinearLayout row=horizontal(parent);String friendly=VinylPlainLanguage.label(label);
        TextView key=text(friendly,14,TEXT,false);row.addView(key,new LinearLayout.LayoutParams(0,-2,1));
        addSettingHelp(row,friendly,ContextualLearning.sliderHelp(label,sliderValueText(label,min,increment),sliderValueText(label,max,increment)));
        int initialProgress=SliderRangePolicy.progress(value,min,max,increment);
        float initialValue=SliderRangePolicy.value(initialProgress,min,max,increment);
        TextView display=text(sliderValueText(label,initialValue,increment),13,StudioTheme.ACCENT_INK,true);display.setGravity(Gravity.CENTER);display.setPadding(dp(10),0,dp(10),0);display.setBackground(shape(StudioTheme.ACCENT_SOFT,StudioTheme.R_CHIP));row.addView(display,new LinearLayout.LayoutParams(-2,dp(28)));
        SeekBar bar=new SeekBar(this);bar.setMax(SliderRangePolicy.steps(min,max,increment));bar.setProgress(initialProgress);bar.setContentDescription(friendly+", "+display.getText());styleSeekBar(bar);
        parent.addView(bar,new LinearLayout.LayoutParams(-1,dp(48)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){@Override public void onStartTrackingTouch(SeekBar s){checkpoint();sliderDragging=true;}@Override public void onStopTrackingTouch(SeekBar s){sliderDragging=false;invalidateToolPanels();requestRender();}@Override public void onProgressChanged(SeekBar s,int progress,boolean fromUser){if(!fromUser)return;float v=SliderRangePolicy.value(progress,min,max,increment);display.setText(sliderValueText(label,v,increment));s.setContentDescription(friendly+", "+display.getText());if(progress%10==0)s.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);int previousSerial=serial,previousAlt=alternateGeneration;callback.accept(v);
            if(previousSerial==serial&&previousAlt==alternateGeneration)requestRender();}});
    }
    private void addSettingHelp(LinearLayout row,String label,String explanation){
        if(explanation==null)return;
        TextView info=text("ⓘ",17,ACCENT,true);
        info.setGravity(Gravity.CENTER);
        info.setContentDescription("Help: "+label+". "+explanation);
        info.setTooltipText(explanation); // Android minSdk 26, also works for S Pen hover.
        row.addView(info,new LinearLayout.LayoutParams(dp(48),dp(48)));
        info.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(label)
            .setMessage(explanation).setPositiveButton("Close",null).show());
    }
    private static String inches(float mm){return String.format(Locale.US,"%.3f in",VinylEngine.toInches(mm));}
    private void inchSlider(LinearLayout parent,String label,float valueMm,float minIn,float maxIn,float stepIn,java.util.function.Consumer<Float> onMillimeters){
        LinearLayout row=horizontal(parent);String friendly=VinylPlainLanguage.label(label);
        TextView key=text(friendly,14,TEXT,false);row.addView(key,new LinearLayout.LayoutParams(0,-2,1));
        addSettingHelp(row,friendly,ContextualLearning.sliderHelp(label,inches(VinylEngine.toMm(minIn)),inches(VinylEngine.toMm(maxIn))));
        int initialProgress=SliderRangePolicy.progress(VinylEngine.toInches(valueMm),minIn,maxIn,stepIn);
        float visibleMm=VinylEngine.toMm(SliderRangePolicy.value(initialProgress,minIn,maxIn,stepIn));
        TextView display=text(inches(visibleMm),13,StudioTheme.ACCENT_INK,true);display.setGravity(Gravity.CENTER);display.setPadding(dp(10),0,dp(10),0);display.setBackground(shape(StudioTheme.ACCENT_SOFT,StudioTheme.R_CHIP));row.addView(display,new LinearLayout.LayoutParams(-2,dp(28)));
        SeekBar bar=new SeekBar(this);bar.setMax(SliderRangePolicy.steps(minIn,maxIn,stepIn));
        bar.setProgress(initialProgress);
        bar.setContentDescription(friendly+", "+display.getText());
        styleSeekBar(bar);
        parent.addView(bar,new LinearLayout.LayoutParams(-1,dp(48)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onStartTrackingTouch(SeekBar seek){checkpoint();sliderDragging=true;}
            @Override public void onStopTrackingTouch(SeekBar seek){sliderDragging=false;invalidateToolPanels();requestRender();}
            @Override public void onProgressChanged(SeekBar seek,int progress,boolean user){if(!user)return;
                float mm=VinylEngine.toMm(SliderRangePolicy.value(progress,minIn,maxIn,stepIn));display.setText(inches(mm));seek.setContentDescription(friendly+", "+display.getText());int previousSerial=serial,previousAlt=alternateGeneration;onMillimeters.accept(mm);
                if(previousSerial==serial&&previousAlt==alternateGeneration)requestRender();}
        });
    }
    /** Master controls never overwrite manually customized layers. Only the explicit
     * link action (which sets linkedControls=true first) propagates all values. */
    private void setLinkedValues(){MasterLayerLinkPolicy.applyIfLinked(settings);}
    private void reselectStandardLayers(int n,int[] precomputed){
        ArrayList<VinylEngine.Layer> pool=new ArrayList<>(settings.layers);
        for(int i=parkedVinylLayers.size()-1;i>=0;i--)pool.add(parkedVinylLayers.get(i));
        int m=pool.size();if(m>8||m<n)return;
        int[] colors=new int[m];for(int i=0;i<m;i++)colors[i]=pool.get(i).color;
        int[] ranks=SmartPaletteReduction.validRanks(Arrays.copyOf(standardPaletteRanks,m),m);
        int eligible=0;for(int i=0;i<m;i++)if(pool.get(i).enabled)eligible|=1<<i;
        int[] p=precomputed!=null?precomputed:paletteReductionWorkspace.plan(colors,ranks,n,preparedPhotoColorPixels,SmartPaletteReduction.PORTRAIT,0,eligible);
        int[] bases=standardHueBaseColors.clone();
        settings.layers.clear();parkedVinylLayers.clear();
        for(int i=0;i<m;i++){
            int old=p[i];standardPaletteRanks[i]=ranks[old];
            standardHueBaseColors[i]=bases[old]!=0?bases[old]:PaletteHueShift.rotateOne(colors[old],-standardHueDegrees);
            if(i<n)settings.layers.add(pool.get(old));
        }
        for(int i=m-1;i>=n;i--)parkedVinylLayers.add(pool.get(p[i]));
    }
    private void countLayers(int n){
        cancelPaletteReduction();final int target=Math.max(2,Math.min(8,n));if(target==settings.layers.size())return;
        if(settings.mode!=VinylEngine.CMYK&&!hasSolidBase(settings)&&standardHueBaseColors[0]!=0){
            ArrayList<VinylEngine.Layer> pool=new ArrayList<>(settings.layers);
            for(int i=parkedVinylLayers.size()-1;i>=0;i--)pool.add(parkedVinylLayers.get(i));
            int m=pool.size();if(m<=8&&m>=target){
                int[] colors=new int[m];int eligible=0;for(int i=0;i<m;i++){colors[i]=pool.get(i).color;if(pool.get(i).enabled)eligible|=1<<i;}
                queuePaletteReduction(colors,SmartPaletteReduction.validRanks(Arrays.copyOf(standardPaletteRanks,m),m),target,
                    SmartPaletteReduction.PORTRAIT,0,eligible,p->countLayersCommitted(target,p));return;
            }
        }
        countLayersCommitted(target,null);
    }
    private void countLayersCommitted(int n,int[] precomputed){if(n==settings.layers.size())return;
        if(hasSolidBase(settings)){toast(getString(R.string.ui_remove_the_solid_background_before_changing));return;}
        if(settings.mode==VinylEngine.CMYK){
            if(n<4){toast(getString(R.string.ui_cmyk_needs_four_base_colors_choose));return;}
            if(!VinylEngine.CmykExtendedStack.hasAnchors(settings.layers)){
                toast("CMYK channel roles are incomplete. Reapply CMYK preset.");return;}
            checkpoint();ensureStandardHueBase();
            // Snapshot base colors by layer identity; the black anchor moves
            // when extras are inserted ahead of it. Keep all inactive accents
            // in separate fixed slots rather than overwriting them with K.
            ArrayList<VinylEngine.Layer> previous=new ArrayList<>(settings.layers);
            int[] oldBases=standardHueBaseColors.clone();
            for(int i=3;i<previous.size()-1;i++)
                cmykAccentHueBaseColors[i-3]=oldBases[i];
            VinylEngine.CmykExtendedStack.resize(settings.layers,n,
                settings.masterSpacingMm,settings.masterDensity,settings.masterContrast);
            int[] nextBases=oldBases.clone();
            for(int i=0;i<settings.layers.size();i++){
                VinylEngine.Layer layer=settings.layers.get(i);
                int before=previous.indexOf(layer);
                if(before>=0){
                    nextBases[i]=oldBases[before]; // Existing color remains exact.
                }else{
                    int accent=i-3;
                    int base=accent>=0&&accent<cmykAccentHueBaseColors.length?
                        cmykAccentHueBaseColors[accent]:0;
                    if(base==0)base=layer.color;
                    nextBases[i]=base;
                    layer.color=PaletteHueShift.rotateOne(base,standardHueDegrees);
                    if(accent>=0&&accent<4)cmykAccentHueBaseColors[accent]=base;
                }
            }
            System.arraycopy(nextBases,0,standardHueBaseColors,0,8);
            if(settings.anglePreset==VinylEngine.ANGLES_PARALLEL)settings.anglePreset=VinylEngine.ANGLES_CLASSIC;
            if(settings.anglePreset!=VinylEngine.ANGLES_CUSTOM)VinylEngine.applyAnglePreset(settings);
            chosen=-1;rebuildLayers();setToolInternal(3);requestRender();return;
        }
        checkpoint();ensureStandardHueBase();
        while(settings.layers.size()<n){int i=settings.layers.size();VinylEngine.Layer l;
            if(!parkedVinylLayers.isEmpty())l=parkedVinylLayers.remove(parkedVinylLayers.size()-1);
            else{
                int[] occupied=new int[settings.layers.size()];
                for(int j=0;j<occupied.length;j++)occupied[j]=settings.layers.get(j).color;
                int[] available=settings.paletteId>=0?
                    PaletteOrientation.orient(VinylPalettes.get(settings.paletteId,8),
                        reversedPalette("preset:"+settings.paletteId)):VinylEngine.START_COLORS;
                int newColor=ColorWorkspacePalette.nextUnusedColor(available,occupied);
                int base=standardHueBaseColors[i]==0?newColor:standardHueBaseColors[i];
                standardHueBaseColors[i]=base;
                l=new VinylEngine.Layer("Vinyl "+(i+1),
                    PaletteHueShift.rotateOne(base,standardHueDegrees));
                l.spacing=settings.masterSpacingMm;l.density=settings.masterDensity;
                l.contrast=settings.masterContrast;
            }
            settings.layers.add(l);
        }
        while(settings.layers.size()>n)
            parkedVinylLayers.add(settings.layers.remove(settings.layers.size()-1));
        reselectStandardLayers(n,precomputed);
        // The complete Layer object follows each physical color; no preset reapply or hue reset.
        if(settings.anglePreset==VinylEngine.ANGLES_PARALLEL)settings.anglePreset=VinylEngine.ANGLES_CLASSIC;
        if(settings.anglePreset!=VinylEngine.ANGLES_CUSTOM)VinylEngine.applyAnglePreset(settings);
        chosen=-1;rebuildLayers();setToolInternal(3);requestRender();
    }
    private void layerCountControl(LinearLayout parent){
        section(parent,"Vinyl colors · "+settings.layers.size()+" (4 recommended)");
        LinearLayout row=horizontal(parent);for(int n=2;n<=8;n++){
            final int count=n;TextView b=text(""+n,13,TEXT,n==settings.layers.size());b.setGravity(Gravity.CENTER);
            b.setBackground(outlined(n==settings.layers.size()?SELECTED:StudioTheme.SURFACE_HIGH,n==settings.layers.size()?ACCENT:StudioTheme.OUTLINE,12));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(37),1);lp.rightMargin=dp(4);row.addView(b,lp);
            b.setOnClickListener(v->countLayers(count));
        }
        parent.addView(text(SmartHalftonePresets.advice(settings.layers.size()),12,MUTED,false));
        action(parent,"Try recommended: "+VinylEngine.PATTERNS[SmartHalftonePresets.recommendedPattern(settings.layers.size())],
            ()->applyHalftoneStyle(SmartHalftonePresets.recommendedPattern(settings.layers.size())));
    }
    /** Preset changes are deliberate; edits and saved custom stacks are never resorted. */
    private void applyActivePresetColors(){
        if(settings.mode==VinylEngine.CMYK||settings.paletteId<0||settings.paletteId>=VinylPalettes.NAMES.length)return;
        int count=settings.layers.size();
        int[] colors=PaletteOrientation.reverseActive(PaletteStylePolicy.arrange(currentPaletteFamily(),
            VinylPalettes.get(settings.paletteId,8),count,settings.paper),count,reversedPalette("preset:"+settings.paletteId),false);
        for(int i=0;i<count;i++){
            VinylEngine.Layer l=settings.layers.get(i);
            l.color=colors[i];l.cmykChannel=-1;l.name=VinylPalettes.NAMES[settings.paletteId]+" "+(i+1);
        }
        setStandardParkedPalette(colors,0);
    }
    private void selectMountingSurface(int color){
        if(settings.paper==color)return;
        checkpoint();settings.paper=color;settings.substrateAware=false;
        if(newStyleActive()){
            // The substrate is a preview background. A palette or geometry render
            // must never be started just because the mounting surface changed.
            setToolInternal(2);
            if(artwork!=null)artwork.invalidate();
            return;
        }
        // Surface is only a substrate/preview setting. Never reorder any vinyl colors,
        // invert palette orientation, or discard manual colors/hue on this action.
        // Do not regenerate vinyl paths because the mounting background changed.
        // This also avoids selecting/dropping a dark-color SVG based on paper contrast.
        if(latest!=null)latest.settings.paper=color;
        rebuildLayers();invalidateToolPanels();setToolInternal(2);
        if(artwork!=null)artwork.invalidate();
    }
    private void usePreset(int id){checkpoint();if(settings.mode==VinylEngine.CMYK){settings.mode=VinylEngine.SPOT;for(VinylEngine.Layer layer:settings.layers)layer.cmykChannel=-1;}
        selectedPaletteKey="preset:"+id;
        settings.paletteId=id;settings.anglePreset=VinylEngine.ANGLES_CLASSIC;
        applyActivePresetColors();rebaseStandardHue();rebuildLayers();setTool(2);requestRender();
    }
    /** Unified preview for every built-in, saved and specialty palette tile.
     * Inactive future sheets are visible but muted; glass L1 remains the lead. */
    private void paletteStrip(LinearLayout tile,int[] colors,int activeCount){
        LinearLayout swatches=horizontal(tile);
        int visible=colors.length;
        for(int i=0;i<visible;i++){
            final int color=colors[i];
            View chip=new View(this);
            chip.setBackground(outlined(color,0xff536075,3));
            if(i>=activeCount)chip.setAlpha(.30f);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(23),1);
            cp.rightMargin=dp(2);swatches.addView(chip,cp);
        }
    }
    private int visiblePaletteCount(boolean cmyk){
        if(cmyk&&!newStyleActive())return 4;
        if(newStyleActive()&&NewStyleColorSlots.multi(newStyleId))
            return NewStyleColorSlots.slotCount(newStyleId,newStyleVariant,newStyleColorCount);
        if(tessellationMode||layeredCellMode)return tessLayerCount;
        if(tonalStencilMode)return tonalLayerCount;
        return settings.layers.size();
    }
    /** These are eight opaque vinyl swatches, NOT translucent CMYK ink. The
     * specialty gallery uses the same eight values it actually applies. */
    private static int[] specialtyProcessColors(){
        return new int[]{0xff131313,0xff00aeef,0xffec008c,0xffffdd00,
            0xff1672de,0xfff47e26,0xff12a894,0xfff8f5e9};
    }
    /** Four classic channels plus four unique companion colors for 8-sheet
     * specialty vinyl art. L1 is dark lead for Glass, not yellow ink. */
    private int[] newStyleCmykPalette(){
        int count=NewStyleColorSlots.slotCount(newStyleId,newStyleVariant,newStyleColorCount);
        return PerceptualLayerColorEngine.finishPreset(newStyleId,expandNewStyleSwatches(VinylPaletteMapping.processEight(newStyleId,
            Math.min(8,count),settings.paper,false)),count,reversedPalette("cmyk"));
    }
    /** A selected preset has one consistent eight-sheet physical layout.
     * Do not reselect or reorder colors on a layer-count change or paper change.
     * A NEW click applies a surface-optimized physical stack, not a UI-only sample. */
    /** Eight-color legacy preset libraries stay unchanged. The new craft engines have
     * a ninth independently editable vinyl swatch (not a duplicate cut sheet). */
    private static int[] expandNewStyleSwatches(int[] input){
        if(input.length==9)return input.clone();
        if(input.length!=8)throw new IllegalArgumentException("Expected an eight/nine-color palette");
        int[] colors=java.util.Arrays.copyOf(input,9);
        int a=input[5],b=input[7];
        colors[8]=0xff000000|(((((a>>16)&255)+((b>>16)&255))/2)<<16)|
            (((((a>>8)&255)+((b>>8)&255))/2)<<8)|
            (((a&255)+(b&255))/2);
        return colors;
    }
    private int[] newStylePresetForKey(String key,int[] original){
        if(original.length!=8)throw new IllegalArgumentException("Eight vinyl colors required");
        int count=NewStyleColorSlots.slotCount(newStyleId,newStyleVariant,newStyleColorCount);
        int[] colors="photo".equals(key)&&FoundationColorPolicy.photoBaseIsFirst(newStyleId)?
            FoundationColorPolicy.anchorPhotoPalette(original,Math.min(8,count),preparedPhotoColorPixels,
                FoundationColorPolicy.lightFoundation(newStyleId)):original;
        // M5: Confetti gets the photo pixels so its solid base and active photo swatches are
        // chosen perceptually (ConfettiBasePolicy, PhotoSwatchSubset). Saved palettes stay exact.
        int[] mapped=VinylPaletteMapping.forStyle(newStyleId,Math.min(8,count),
            settings.paper,colors,false,
            "photo".equals(key)||key.startsWith("saved:"),
            VinylStyleSuite.isConfetti(newStyleId)&&!key.startsWith("saved:")?preparedPhotoColorPixels:null);
        return PerceptualLayerColorEngine.finishPreset(newStyleId,expandNewStyleSwatches(mapped),count,reversedPalette(key));
    }
    /** Preview and commit must display the same opaque vinyl sheet order.
     * In Overprint modes L1 is structural, so reversing a palette must keep
     * that foundation anchored; inactive swatches remain available unchanged.
     * Only explicit palette selection uses this method. Never call it when
     * the mounting surface changes or a manual swatch is edited. */
    private int[] specialtyPhysicalPalette(String key,int[] input){
        if(input==null||input.length!=8)throw new IllegalArgumentException("Eight vinyl swatches required");
        if(layeredCellMode&&overprintCellKind>0)
            return OpaqueOverlayPaletteMapping.preset(input,tessLayerCount,settings.paper,
                preparedPhotoColorPixels,"photo".equals(key),reversedPalette(key));
        boolean base=layeredCellMode&&(offsetCellMode||layeredSolidBase);
        int[] arranged="photo".equals(key)?
            (base?FoundationColorPolicy.anchorPhotoPalette(input,tessLayerCount,
                preparedPhotoColorPixels,false):input.clone()):
            PaletteStylePolicy.arrange(PaletteStylePolicy.PHOTO_CELLS,
                input,tessLayerCount,settings.paper);
        return PaletteOrientation.reverseActive(arranged,tessLayerCount,
            reversedPalette(key),base);
    }
    /** Tap applies a palette. Reverse also immediately applies the newly
     * oriented palette, including tiles that were previously inactive. */
    private void inlinePaletteTile(LinearLayout row,String key,String label,int[] base,boolean cmyk){
        final boolean selected=selectedPaletteKey.equals(key);
        LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(6),dp(7),dp(6),dp(7));
        tile.setBackground(outlined(selected?SELECTED:StudioTheme.OUTLINE,
            selected?ACCENT:0xff536075,12));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(87),1);
        lp.rightMargin=dp(6);lp.bottomMargin=dp(8);row.addView(tile,lp);
        int[] colors=cmyk?PaletteOrientation.orientCmyk(base,reversedPalette(key)):
            PaletteOrientation.orient(base,reversedPalette(key));
        if(newStyleActive()&&NewStyleColorSlots.multi(newStyleId)){
            if(cmyk)colors=newStyleCmykPalette();
            else colors=newStylePresetForKey(key,base);
        }else if(tessellationMode||layeredCellMode||tonalStencilMode){
            int[] input=cmyk?specialtyProcessColors():base;
            int[] oriented=PaletteOrientation.orient(input,reversedPalette(key));
            int previewCount=Math.max(2,Math.min(8,visiblePaletteCount(false)));
            colors=layeredCellMode||tessellationMode?
                specialtyPhysicalPalette(key,input):
                "photo".equals(key)?oriented:
                PaletteOrientation.reverseActive(
                    PaletteStylePolicy.arrange(currentPaletteFamily(),input,
                        previewCount,settings.paper),previewCount,reversedPalette(key),false);
            if(tonalStencilMode){
                // A preset preview must show the same tonal assignment it will
                // produce after tapping: source brightness, then physical order.
                if("photo".equals(key)&&tonalReadyForPaletteEdit()){
                    colors=tonalDisplayedColors();
                }else if(!"photo".equals(key)){
                    int[] sorted=TonalPhotoPaletteOrder.sortDarkToLight(
                        java.util.Arrays.copyOf(colors,previewCount));
                    if(tonalReadyForPaletteEdit()&&!tonalMatchMyVinyl){
                        int[] physical=new int[previewCount];
                        for(int i=0;i<previewCount;i++)
                            physical[i]=sorted[tonalSourceIndex(i)];
                        colors=physical;
                    }else colors=sorted;
                }
            }
        }
        if(!newStyleActive()&&!tessellationMode&&!layeredCellMode&&!tonalStencilMode&&!cmyk&&
            !"photo".equals(key)){
            int family=LineArtEngine.isLineStyle(settings.pattern)?PaletteStylePolicy.LINE_RIBBONS:PaletteStylePolicy.PHOTO_CELLS;
            int count=settings.layers.size();
            colors=PaletteOrientation.reverseActive(PaletteStylePolicy.arrange(family,base,count,settings.paper),
                count,reversedPalette(key),false);
        }
        // The selected tile represents what is actually in the vinyl sheets.
        // Respect hue shifts and individual edits rather than previewing stale
        // source preset RGB values while the artwork uses another color.
        if(selected){
            if(newStyleActive()){
                // Auto photo colors live in the generated result, while fixed
                // palettes live in newStyleColors; both must show actual L1..LN.
                int[] actual=newStyleColors.clone();
                int slots=NewStyleColorSlots.slotCount(newStyleId,newStyleVariant,newStyleColorCount);
                for(int i=0;i<slots;i++)actual[i]=newStyleDisplayedColor(i);
                colors=actual;
            }else if(tessellationMode||layeredCellMode)colors=tessColors.clone();
            else if(tonalStencilMode)colors=tonalDisplayedColors();
            else if(!newStyleActive()){
                int[] actual=colors.length==8?colors.clone():new int[8];
                int n=Math.min(settings.layers.size(),8);
                for(int i=0;i<n;i++)actual[i]=settings.layers.get(i).color;
                colors=actual;
            }
        }
        final int active=Math.min(colors.length,visiblePaletteCount(cmyk));
        paletteStrip(tile,colors,active);
        TextView title=text(label,12,TEXT,true);title.setMaxLines(1);title.setGravity(Gravity.CENTER);
        tile.addView(title,new LinearLayout.LayoutParams(-1,dp(23)));
        tile.setContentDescription((selected?"Selected palette. ":"Tap to apply palette. ")+label);
        tile.setOnClickListener(v->applyInlinePalette(key,true));
    }
    /** Gallery displays only relevant choices first, with the complete catalog
     * behind one optional section. Physical slots are never changed by ranking. */
    private boolean showAllBuiltInPalettes=false;

    private int currentPaletteFamily(){
        if(newStyleActive())return PaletteStylePolicy.familyForNewStyle(newStyleId);
        if(settings.mode==VinylEngine.CMYK)return PaletteStylePolicy.CMYK;
        if(tonalStencilMode)return PaletteStylePolicy.STENCIL;
        if(tessellationMode||layeredCellMode)return PaletteStylePolicy.PHOTO_CELLS;
        if(LineArtEngine.isLineStyle(settings.pattern))return PaletteStylePolicy.LINE_RIBBONS;
        return PaletteStylePolicy.PHOTO_CELLS;
    }
    private void inlinePaletteCatalogTile(LinearLayout row,int id){
        String key=PaletteRecommendations.key(id);
        // Keep stable preset IDs and names. Only the displayed label is
        // qualified so Creative Ocean/CMYK cannot be mistaken for Standard.
        String label=(id>=VinylPalettes.NAMES.length?"Art · ":"")+PaletteRecommendations.name(id);
        inlinePaletteTile(row,key,label,PaletteRecommendations.colors(id),false);
    }
    private void inlinePaletteGallery(LinearLayout host){
        int family=currentPaletteFamily();
        boolean classicCmyk=family==PaletteStylePolicy.CMYK;
        section(host,classicCmyk?"CMYK vinyl channels":"Recommended vinyl palettes");
        if(!classicCmyk)host.addView(text((tonalStencilMode?
            "Tonal presets assign darkest vinyl to darkest photo regions and lighter vinyl to highlights. " :
            "Chosen for this style, photograph, active layer count, and mounting surface. ") +
            "Tap to apply; existing manual colors stay unchanged until you choose a palette.",12,MUTED,false));
        LinearLayout first=horizontal(host);
        inlinePaletteTile(first,"cmyk",newStyleActive()?"Process-inspired vinyl":"Classic CMYK",
            new int[]{0xffffdd00,0xff00aeef,0xffec008c,0xff131313},true);
        if(preparedPhotoPalette!=null)
            inlinePaletteTile(first,"photo",classicCmyk?"Photo → Spot":"Photo colors",
                preparedPhotoPalette,false);
        else if(!classicCmyk){
            LinearLayout pending=new LinearLayout(this);pending.setOrientation(LinearLayout.VERTICAL);
            pending.setPadding(dp(8),dp(10),dp(8),dp(8));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(82),1);first.addView(pending,lp);
            pending.addView(text(getString(R.string.ui_photo_colors),12,TEXT,true));
            pending.addView(text(photo==null?"Import a photo":"Analyzing photo…",12,MUTED,false));
        }
        section(host,"Manufacturer vinyl · physical material references");
        LinearLayout materials=horizontal(host);
        for(int id=0;id<ManufacturerVinylPalettes.COUNT;id++)
            inlinePaletteTile(materials,"material:"+id,
                id==0?"ORACAL 651":"Siser EasyWeed",ManufacturerVinylPalettes.colors(id),false);
        host.addView(text("Approximate screen RGB, not measured vinyl colors. ORACAL 651 is adhesive; EasyWeed is heat-transfer vinyl. " +
            "Use a physical swatch chart. Selecting a material palette does not purchase or identify actual stock.",12,MUTED,false));
        if(classicCmyk)host.addView(text(getString(R.string.ui_selecting_another_palette_switches_to_spot),12,MUTED,false));
        int count=Math.max(2,Math.min(8,visiblePaletteCount(false)));
        int[] recommended=PaletteRecommendations.choose(family,preparedPhotoPalette,
            settings.paper,count,6);
        for(int start=0;start<recommended.length;start+=2){
            LinearLayout row=horizontal(host);
            for(int j=start;j<Math.min(start+2,recommended.length);j++)
                inlinePaletteCatalogTile(row,recommended[j]);
        }
        // All swatches remain visible; no second drawer or hidden catalog.
        // Recommended palettes are listed first; remaining choices follow.
        section(host,"All built-in palettes · "+PaletteRecommendations.CATALOG_SIZE);
        boolean[] listed=new boolean[PaletteRecommendations.CATALOG_SIZE];
        for(int id:recommended)if(id>=0&&id<listed.length)listed[id]=true;
        for(int start=0;start<PaletteRecommendations.CATALOG_SIZE;){
            LinearLayout row=horizontal(host);
            int added=0;
            while(start<PaletteRecommendations.CATALOG_SIZE&&added<2){
                int id=start++;
                if(listed[id])continue;
                inlinePaletteCatalogTile(row,id);added++;
            }
        }
        host.addView(text("Bright chips are active physical sheets; dim chips are saved for later. " +
            "Stained Glass keeps its L1 lead; saved and manually edited colors retain their positions.",12,MUTED,false));
    }
    private static int paletteIndex(String key,int prefix,int fallback){
        try {
            int id=Integer.parseInt(key.substring(prefix));
            int limit=prefix==7?VinylPalettes.NAMES.length:ColorWorkspacePalette.TESS_NAMES.length;
            return id>=0&&id<limit?id:fallback;
        }catch(RuntimeException invalid){return fallback;}
    }
    private void applyInlinePalette(String key,boolean record){
        if(photo==null)return;
        if("photo".equals(key)&&preparedPhotoPalette==null){toast(getString(R.string.ui_photo_colors_are_still_being_generated));return;}
        if(tonalStencilMode&&!tonalReadyForPaletteEdit()){
            toast(getString(R.string.ui_finish_rendering_stencil_colors_before_choosing));return;
        }
        if(record)checkpoint();
        int[] base;
        if("cmyk".equals(key))base=new int[]{0xffffdd00,0xff00aeef,0xffec008c,0xff131313};
        else if("photo".equals(key))base=preparedPhotoPalette.clone();
        else if(key.startsWith("preset:"))base=VinylPalettes.get(paletteIndex(key,7,0),8);
        else if(key.startsWith("creative:"))base=ColorWorkspacePalette.tessPalette(paletteIndex(key,9,0));
        else if(ManufacturerVinylPalettes.fromKey(key)>=0)
            base=ManufacturerVinylPalettes.colors(ManufacturerVinylPalettes.fromKey(key));
        else return;
        if(newStyleActive()&&!NewStyleColorSlots.multi(newStyleId))return;
        selectedPaletteKey=key;
        if("photo".equals(key) && underlayRequested())
            updateAutomaticSnipFoundation(base,preparedPhotoColorPixels,true);
        if(newStyleActive()){
            if(!NewStyleColorSlots.multi(newStyleId))return;
            int[] all="cmyk".equals(key)?newStyleCmykPalette():newStylePresetForKey(key,base);
            newStyleColors=all.clone();newStyleHueBaseColors=all.clone();newStyleHueDegrees=0;
            newStylePaletteRanks=SmartPaletteReduction.identity(9);
            newStyleMatchVinyl=true;newStylePhotoAutoMemory[newStyleId]=false;
            rememberNewStylePalette();requestAlternate();
        }else if(tessellationMode||layeredCellMode){
            int[] full=base.length==8?base:specialtyProcessColors();
            setTessBasePalette(specialtyPhysicalPalette(key,full));
            tessPalette=key;requestAlternate();
        }else if(tonalStencilMode){
            if("photo".equals(key)){
                // The true photo-derived colors are the quantizer's source
                // cluster means, not a stale eight-chip Prepare palette.
                Arrays.fill(tonalColorOverrides,0);tonalPaletteRanks=SmartPaletteReduction.identity(8);
                tonalMatchMyVinyl=false;
            }else{
                int[] full=base.length==8?base:specialtyProcessColors();
                int[] chosen=PaletteStylePolicy.arrange(PaletteStylePolicy.STENCIL,
                    full,tonalLayerCount,settings.paper);
                if(!applyTonalSourcePaletteColors(
                        java.util.Arrays.copyOf(chosen,tonalLayerCount),0))return;
                parkTonalPresetTail(chosen,tonalLayerCount);
            }
            requestAlternate();
        }else{
            if("cmyk".equals(key)){
                // Preserve channel IDs and Y/K anchors. A repeated Reverse toggles
                // against the canonical palette, not an already hue-shifted array.
                if(settings.mode!=VinylEngine.CMYK||!VinylEngine.CmykExtendedStack.hasAnchors(settings.layers))configureCmyk();
                int n=settings.layers.size();int[] physical=new int[n];
                for(int i=0;i<n;i++)physical[i]=settings.layers.get(i).color;
                // A CMYK preset click resets only its base 4 channels; retain
                // explicitly edited accent vinyl colors.
                physical[0]=0xffffdd00;physical[1]=0xff00aeef;
                physical[2]=0xffec008c;physical[n-1]=0xff131313;
                physical=PaletteOrientation.orientCmyk(physical,reversedPalette(key));
                for(int i=0;i<n;i++)settings.layers.get(i).color=physical[i];
                settings.paletteId=-1;
            }else{
                if(settings.mode==VinylEngine.CMYK){settings.mode=VinylEngine.SPOT;
                    for(VinylEngine.Layer layer:settings.layers)layer.cmykChannel=-1;}
                int[] mapped="photo".equals(key)?PaletteOrientation.orient(base,reversedPalette(key)):
                    PaletteOrientation.reverseActive(
                        PaletteStylePolicy.arrange(currentPaletteFamily(),
                            base,settings.layers.size(),settings.paper),
                        settings.layers.size(),reversedPalette(key),false);
                for(int i=0;i<settings.layers.size();i++)settings.layers.get(i).color=mapped[i];
                setStandardParkedPalette(mapped,0);
                settings.paletteId=key.startsWith("preset:")?paletteIndex(key,7,-1):-1;
            }
            rebaseStandardHue();requestRender();
        }
        invalidateToolPanels();setToolInternal(2);
    }
    private void setCmyk(){checkpoint();configureCmyk();selectedPaletteKey="cmyk";
        if(reversedPalette("cmyk")){
            int n=settings.layers.size();int[] actual=new int[n];
            for(int i=0;i<n;i++)actual[i]=settings.layers.get(i).color;
            actual=PaletteOrientation.orientCmyk(actual,true);
            for(int i=0;i<n;i++)settings.layers.get(i).color=actual[i];
        }
        rebaseStandardHue();chosen=-1;rebuildLayers();setTool(2);requestRender();}
    private void configureCmyk(){designType=0;settings.paletteId=-1;
        // Vinyl application order is bottom to top, not abstract channel order.
        int[] colors={0xffffdd00,0xff00aeef,0xffec008c,0xff131313};
        String[] names={"Yellow","Cyan","Magenta","Black"};
        int[] channels={2,0,1,3};
        parkedVinylLayers.clear();while(settings.layers.size()>4)settings.layers.remove(settings.layers.size()-1);
        while(settings.layers.size()<4)settings.layers.add(new VinylEngine.Layer("Vinyl",0xff222222));
        for(int i=0;i<4;i++){VinylEngine.Layer l=settings.layers.get(i);l.name=names[i];l.color=colors[i];l.cmykChannel=channels[i];l.strength=1f;l.enabled=true;l.lineWidthScale=1f;l.lineGammaOverride=0f;l.angleFineTuneDeg=0f;l.lineStyleOverride=-1;}
        settings.mode=VinylEngine.CMYK;settings.anglePreset=VinylEngine.ANGLES_CLASSIC;
        ClassicCmykLines.apply(settings);
        settings.linkedControls=true;setLinkedValues();VinylEngine.applyAnglePreset(settings);
        rebaseStandardHue();
            }
    /** One-tap looks. Four-color CMYK lines is the default and first card. */
    private static final String[] LOOK_NAMES={"4-color lines","4-color contour","3-color lines","CMYK dots"};
    private static final String[] LOOK_SUBS={"Y C M K at split angles","Lines bend with the face","No yellow layer","Classic round halftone"};
    private int currentLook(){
        if(settings.mode!=VinylEngine.CMYK)return -1;
        boolean noYellow=false;for(VinylEngine.Layer l:settings.layers)if(l.cmykChannel==2&&!l.enabled)noYellow=true;
        if(settings.pattern==VinylEngine.ROUND)return 3;
        if(noYellow)return 2;
        if(settings.pattern==VinylEngine.CONTOUR)return 1;
        if(settings.pattern==VinylEngine.LINE)return 0;
        return -1;
    }
    private void applyLook(int look){
        if(hasPendingSnip()){toast(getString(R.string.ui_finish_or_discard_the_current_snip));return;}
        checkpoint();configureCmyk();
        int style=look==1?VinylEngine.CONTOUR:look==3?VinylEngine.ROUND:VinylEngine.LINE;
        float w=VinylEngine.toInches(settings.widthMm);
        float h=imageW>0?w*imageH/(float)imageW:w*1.25f;
        GlobalPatternSelection.apply(settings,style,chosenProfile,w,h);
        if(look==2)for(VinylEngine.Layer l:settings.layers)if(l.cmykChannel==2)l.enabled=false;
        chosen=-1;rebuildLayers();refreshShelfPanel();requestRender();
    }
    private void lookPresetCards(){
        LinearLayout g=group();section(g,"Look · tap to apply");
        int cur=currentLook();
        for(int i=0;i<LOOK_NAMES.length;i+=2){
            LinearLayout row=horizontal(g);
            for(int j=i;j<Math.min(i+2,LOOK_NAMES.length);j++){
                final int id=j;boolean on=cur==j;
                LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(8),dp(12),dp(8));
                card.setBackground(outlined(on?0xff344C66:StudioTheme.SURFACE_HIGH,on?ACCENT:0xff475261,14));
                card.addView(text((j==0?"★  ":"")+LOOK_NAMES[j],14,TEXT,true));
                card.addView(text(LOOK_SUBS[j],11,MUTED,false));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.rightMargin=dp(8);lp.bottomMargin=dp(8);row.addView(card,lp);
                card.setOnClickListener(v->applyLook(id));
                card.setContentDescription(LOOK_NAMES[j]+" look");
            }
        }
    }
    private void shapeControls(){
        LinearLayout g=group();section(g,"Shape definition");
        slider(g,"Edge-following lines · %",settings.shapeDefinition*100,0,100,5,v->settings.shapeDefinition=v/100f);
        if(LineSliderVisibility.followForm(settings))
            slider(g,"Follow form (bend) · %",settings.contourStrength*100,0,100,5,v->settings.contourStrength=v/100f);
        slider(g,"Line weight in shadows · %",settings.lineMaxFraction*100,40,95,5,v->settings.lineMaxFraction=v/100f);
        g.addView(text(getString(R.string.ui_edge_following_thickens_and_connects_lines),12,MUTED,false));
    }
    private void applyScreenPreset(int preset){
        // Do not allow a one-tap parallel override to collapse CMYK channels.
        if(settings.mode==VinylEngine.CMYK&&preset==VinylEngine.ANGLES_PARALLEL){
            toast(getString(R.string.ui_cmyk_uses_separated_screens_select_custom));return;
        }
        checkpoint();settings.anglePreset=preset;
        if(preset!=VinylEngine.ANGLES_CUSTOM)VinylEngine.applyAnglePreset(settings);
        if(activeWorkspace==4){invalidateToolPanels();setToolInternal(3);}else setTool(1);
        requestRender();
    }
    private void applyHalftoneStyle(int style){checkpoint();rememberNewStylePalette();discardCutReadyStages();applyStyleSelection(StyleSelection.HALFTONE);newStyleResult=null;newStyleVectors=null;
        float w=VinylEngine.toInches(settings.widthMm);
        float h=imageW>0?w*imageH/(float)imageW:w*1.25f;
        chosenProfile=VinylSizing.BALANCED;
        GlobalPatternSelection.apply(settings,style,chosenProfile,w,h);
        AutoPhotoTuner.Suggestion start=selectionSuggestion(LineArtEngine.isLineStyle(style)?
            AutoPhotoTuner.LINES:AutoPhotoTuner.SHAPES);
        if(start!=null){
            settings.masterSpacingMm=Math.max(settings.minimumMm,start.spacingMm);
            settings.masterContrast=start.screenContrast;
            if(LineArtEngine.isLineStyle(style)){
                settings.lineMaxFraction=start.lineWidth;
                settings.detailBoost=start.lineDetail;
                settings.contourStrength=start.lineContour;
            }else settings.shapeScale=start.shapeScale;
            if(settings.linkedControls)setLinkedValues();
        }
        retainCutMaterialProfile();setTool(1);requestRender();
    }
    private void applySmartProfile(int profile){
        checkpoint();chosenProfile=Math.max(0,Math.min(2,profile));
        float w=VinylEngine.toInches(settings.widthMm);
        float h=imageW>0?w*imageH/(float)imageW:w*1.25f;
        SmartHalftonePresets.apply(settings,settings.pattern,chosenProfile,w,h);
        retainCutMaterialProfile();setTool(1);requestRender();
    }


    /** Six primary workspaces. Keep serialized WorkflowFlow IDs and tool IDs unchanged. */
    private static final String[] WORKSPACE_NAMES={"✂", "◇", "☷", "◉", "▤", "⇧"};
    private static final String[] WORKSPACE_LABELS={"Prepare","Styles","Adjust","Colors","Layers","Export"};
    private static final String[][] WORKSPACE_TOOLS={
        {"Import","Crop","Snip","Lighting","Size"},
        {}, {}, {}, {},
        {"Boundary","Marks","Cut Check","Export"}
    };
    private static final int[][] WORKSPACE_IDS={
        {WorkflowFlow.IMPORT,WorkflowFlow.CROP,WorkflowFlow.SNIP,WorkflowFlow.LIGHT,-1},
        {}, {}, {}, {}, {14,6,13,7}
    };
    private boolean boundaryPanelSelected=false;
    /** True after an artwork input changes or an unfinished render is canceled. */
    private volatile boolean stencilNeedsRender=true;
    /** Entering Prepare is a view change, NOT a photo edit. Preserve completed SVG geometry. */
    private void switchWorkspaceState(int next){
        int previous=activeWorkspace;
        if(previous==next)return;
        activeWorkspace=next;
        if(next==0){
            boolean missing=alternateActive()?alternateReadyGeneration!=alternateGeneration||alternatePreviewDraft:
                latest==null||renderedSerial!=serial||standardPreviewDraft;
            if(missing)stencilNeedsRender=true;
            handler.removeCallbacks(renderDebounce);
            handler.removeCallbacks(alternateDraftDebounce);
            renderController.cancel(StudioRenderController.Lane.STANDARD);
            renderController.cancel(StudioRenderController.Lane.SPECIALTY);
            renderController.cancel(StudioRenderController.Lane.PREVIEW);
            
            previewRequestId++;
            // In-flight results are not reused. Completed results retain their
            // generation IDs for an instant return to Shapes/Colors/Layers/Export.
            if(missing){serial++;alternateGeneration++;alternateReadyGeneration=-1;renderedSerial=-1;}
            setRenderBusy(false);
            requestPreparePhotoPreview();
        }else if(previous==0&&renderController.hasPending(StudioRenderController.Lane.STANDARD)){
            // A Prepare lighting worker must not race with a stencil job.
            renderController.cancel(StudioRenderController.Lane.STANDARD);
        }
        if(next!=0&&photo!=null){
            boolean missing=alternateActive()?alternateReadyGeneration!=alternateGeneration||alternatePreviewDraft:
                latest==null||renderedSerial!=serial||standardPreviewDraft;
            if(stencilNeedsRender||missing)requestRender();
        }
    }

    private final ArrayList<FrameLayout> dockPillBackgrounds=new ArrayList<>();
    private final ArrayList<TextView> dockPillLabels=new ArrayList<>();
    private final ArrayList<TextView> workspaceToolButtons=new ArrayList<>();
    private void selectWorkspace(int index){
        int next=Math.max(0,Math.min(WORKSPACE_NAMES.length-1,index));
        // Shapes is a launcher, not a second parameter workspace.
        if(next==1){
            if(hasPendingSnip()){toast(getString(R.string.ui_finish_your_snip_before_changing_artwork));return;}
            switchWorkspaceState(1);
            updateEditorSubtitle();rebuildNavigation();
            showUnifiedStyleDrawer();
            if(artwork!=null)artwork.invalidate();
            return;
        }
        switchWorkspaceState(next);
        if(next!=0&&photo!=null)ensurePreparedPhotoPalette();
        if(next==4)VinylQuickStartGuide.tip(this,getPreferences(MODE_PRIVATE),"layers",R.string.guide_layers_tip);
        if(next==5)VinylQuickStartGuide.tip(this,getPreferences(MODE_PRIVATE),"export",R.string.guide_export_tip);
        updateEditorSubtitle();rebuildNavigation();
        if(next==0){
            int step=activeSubtoolPosition>=0&&activeSubtoolPosition<5?activeSubtoolPosition:0;
            openWorkspaceSubtool(0,step);
        } else if(next==2){activeSubtoolPosition=0;setToolInternal(1);expandToolSheet();}
        else if(next==3){activeSubtoolPosition=0;setToolInternal(2);expandToolSheet();}
        else if(next==4){activeSubtoolPosition=0;setToolInternal(3);expandToolSheet();}
        else if(next==5){openWorkspaceSubtool(5,0);}
        if(panelOpen&&!landscapeSplit)animateSheet(sheetMaxHeight());
        if(artwork!=null)artwork.invalidate();
    }
    private void openWorkspaceSubtool(int category,int position){
        if(WORKSPACE_IDS[category].length<=position)return;
        switchWorkspaceState(category);
        activeSubtoolPosition=position;
        int id=WORKSPACE_IDS[category][position];
        if(category==0){
            if(position==4){
                if(photo==null){toast(getString(R.string.ui_import_a_photo_first));activeSubtoolPosition=0;setWorkflowStep(WorkflowFlow.IMPORT);}
                else if(hasPendingSnip()){
                    toast(getString(R.string.ui_apply_or_discard_your_current_snip));
                    activeSubtoolPosition=2;setWorkflowStep(WorkflowFlow.SNIP);
                }else showFinishedSizeDialog();
            }else setWorkflowStep(id);
        }else openDockTool(id);
        if(category==0&&position==2)VinylQuickStartGuide.tip(this,getPreferences(MODE_PRIVATE),"snip",R.string.guide_snip_tip);
        rebuildNavigation();
        expandToolSheet();
    }
    private String workspaceName(int index){return WORKSPACE_LABELS[Math.max(0,Math.min(WORKSPACE_LABELS.length-1,index))];}
    private void rebuildNavigation(){
        if(navRow==null)return;
        if(dockPillLabels.size()!=WORKSPACE_NAMES.length){
            navRow.removeAllViews();dockPillLabels.clear();dockPillBackgrounds.clear();
            for(int i=0;i<WORKSPACE_NAMES.length;i++){
                final int selected=i;
                FrameLayout pill=new FrameLayout(this);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(StudioTheme.H_NAV-8),1);lp.leftMargin=dp(2);lp.rightMargin=dp(2);
                navRow.addView(pill,lp);
                TextView label=text(WORKSPACE_NAMES[i]+"\n"+workspaceName(i),11,MUTED,true);label.setLetterSpacing(.02f);
                label.setGravity(Gravity.CENTER);pill.addView(label,new FrameLayout.LayoutParams(-1,-1));
                // All six primary workspaces remain visible and directly selectable.
                pill.setOnClickListener(v->{v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);selectWorkspace(selected);});
                pill.setContentDescription(workspaceName(i)+" workspace");
                dockPillBackgrounds.add(pill);dockPillLabels.add(label);
            }
        }
        for(int i=0;i<WORKSPACE_NAMES.length;i++){
            dockPillLabels.get(i).setText(getString(R.string.dock_pill_label,WORKSPACE_NAMES[i],workspaceName(i)));
            dockPillBackgrounds.get(i).setContentDescription(workspaceName(i)+" workspace");
            boolean active=activeWorkspace==i;
            dockPillBackgrounds.get(i).setBackground(ripple(active?shape(StudioTheme.ACCENT_SOFT,StudioTheme.R_CARD):shape(Color.TRANSPARENT,StudioTheme.R_CARD),StudioTheme.R_CARD));
            dockPillLabels.get(i).setTextColor(active?StudioTheme.ACCENT_INK:MUTED);
            dockPillLabels.get(i).setTypeface(android.graphics.Typeface.create(active?"sans-serif-medium":"sans-serif",android.graphics.Typeface.NORMAL));
        }
        if(subtoolRow!=null&&builtWorkspace!=activeWorkspace){
            builtWorkspace=activeWorkspace;
            subtoolRow.removeAllViews();workspaceToolButtons.clear();
            for(int i=0;i<WORKSPACE_TOOLS[activeWorkspace].length;i++){
                final int category=activeWorkspace,position=i;
                TextView b=text(WORKSPACE_TOOLS[category][position],13,TEXT,true);
                b.setGravity(Gravity.CENTER);b.setPadding(dp(12),0,dp(12),0);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(StudioTheme.H_SUBTOOL-4));lp.rightMargin=dp(8);
                subtoolRow.addView(b,lp);workspaceToolButtons.add(b);
                b.setOnClickListener(v->openWorkspaceSubtool(category,position));
            }
            subtoolScroller.scrollTo(0,0);
        }
        if(subtoolScroller!=null)subtoolScroller.setVisibility(panelOpen&&(activeWorkspace==0||activeWorkspace==5)?View.VISIBLE:View.GONE);
        for(int i=0;i<workspaceToolButtons.size();i++){
            int id=WORKSPACE_IDS[activeWorkspace][i];
            boolean selected=i==activeSubtoolPosition;
            TextView b=workspaceToolButtons.get(i);
            b.setBackground(pillBackground(selected,StudioTheme.R_CHIP));b.setTextColor(selected?StudioTheme.ACCENT_INK:TEXT);
        }
    }
    private boolean isDockActive(int id){
        if(id==11)return tool==3&&showStackControls;
        if(id==12)return tool==4;
        if(id==13)return tool==6;
        if(id==0||id==1||id==2||id==8||id==4||id==5||id==7||id==3||id==6)return workflowStep==id;
        if(id==10)return tool==2;
        return workflowStep==WorkflowFlow.ADJUST;
    }
    private void openDockTool(int id){
        if(id==11){showStackControls=false;setTool(3);return;}
        if(id==12){setTool(4);return;}
        if(id==14){boundaryPanelSelected=true;setTool(6);return;}
        if(id==13){boundaryPanelSelected=false;setTool(6);return;}
        if(id==10){setTool(2);return;}
        if(id==WorkflowFlow.CROP){setWorkflowStep(WorkflowFlow.CROP);return;}
        setWorkflowStep(id);
    }
    private boolean hasPendingSnip(){return !snipDraft.isEmpty()||editingSnipIndex>=0;}
    private void selectPhase(int phase){
        phase=WorkflowFlow.clampPhase(phase);
        int preferred=lastStepInPhase[phase];
        if(WorkflowFlow.phaseOf(preferred)!=phase)preferred=WorkflowFlow.firstStage(phase);
        setWorkflowStep(preferred);
    }
    private void moveWorkflow(int delta){
        int next=WorkflowFlow.next(workflowStep,delta);
        if(next==-2){requestExport();return;}
        if(next==-1)return;
        setWorkflowStep(next);
    }
    private void setWorkflowStep(int requested){
        int next=WorkflowFlow.navigate(requested,photo!=null,hasPendingSnip());
        if(photo==null&&WorkflowFlow.needsPhoto(requested))toast(getString(R.string.ui_import_and_crop_a_photo_first));
        else if(hasPendingSnip()&&WorkflowFlow.mustFinishSnip(requested,true))toast(getString(R.string.ui_apply_or_discard_the_current_snip));
        if(next!=workflowStep&&next!=WorkflowFlow.LAYERS)showStackControls=false;
        workflowStep=next;
        selectedPhase=WorkflowFlow.phaseOf(workflowStep);
        lastStepInPhase[selectedPhase]=workflowStep;
        if(activeWorkspace==0&&WorkflowFlow.phaseOf(workflowStep)==WorkflowFlow.PREPARE)
            activeSubtoolPosition=workflowStep==WorkflowFlow.LIGHT?3:workflowStep;
        setToolInternal(STEP_TOOLS[workflowStep]);
    }
    /** A small persistent adjustment dock, not a half-screen sheet. */
    private void expandToolSheet(){
        if(sheetHolder==null||sheetScroll==null)return;
        panelOpen=true;
        subtoolScroller.setVisibility(View.VISIBLE);
        if(activeWorkspace!=0&&activeWorkspace!=5)subtoolScroller.setVisibility(View.GONE);
        sheetScroll.setVisibility(View.VISIBLE);
        if(!landscapeSplit)animateSheet(sheetMaxHeight());
    }
    private void collapseToolSheet(){
        if(sheetHolder==null||sheetScroll==null)return;
        panelOpen=false;
        subtoolScroller.setVisibility(View.GONE);
        sheetScroll.setVisibility(View.GONE);
        if(!landscapeSplit)animateSheet(dp(DOCK_COLLAPSED_DP));
    }
    private void toggleSheet(){if(panelOpen)collapseToolSheet();else expandToolSheet();}
    private int sheetMaxHeight(){
        // Use the smaller of a 250dp shelf or 38% of the available window.
        // Scroll internally for long menus; never cover most of the image.
        int usable=editorArea!=null&&editorArea.getHeight()>0?
            editorArea.getHeight():getResources().getDisplayMetrics().heightPixels-dp(65);
        int maxDp=Math.min(340,(int)(usable/getResources().getDisplayMetrics().density*.50f));
        return dp(Math.max(155,Math.min(workspaceDockDp[activeWorkspace],maxDp)));
    }
    private void animateSheet(int target){
        if(sheetHolder==null||landscapeSplit)return;
        target=Math.max(dp(DOCK_COLLAPSED_DP),Math.min(sheetMaxHeight(),target));
        if(dockAnimator!=null){dockAnimator.cancel();dockAnimator=null;}
        int current=sheetHolder.getLayoutParams().height;
        if(current==target){syncCanvasDockSpace(current);return;}
        dockAnimator=ValueAnimator.ofInt(current,target);
        dockAnimator.setDuration(155);
        dockAnimator.addUpdateListener(a->{android.view.ViewGroup.LayoutParams lp=sheetHolder.getLayoutParams();
            lp.height=(Integer)a.getAnimatedValue();sheetHolder.setLayoutParams(lp);
            syncCanvasDockSpace(lp.height);});
        dockAnimator.start();
    }
    /** Reserve the drawer's actual height below the artwork; it never covers the photo. */
    private void syncCanvasDockSpace(int height){
        if(landscapeSplit||canvasFullscreen||canvasColumn==null)return;
        int reserved=dp(50)+Math.max(dp(DOCK_COLLAPSED_DP),height);
        if(canvasColumn.getPaddingBottom()!=reserved)canvasColumn.setPadding(0,0,0,reserved);
    }
    /** Short swipe on the grip opens or closes the dock; the canvas handles other gestures. */
    private void attachSheetDrag(View handle){
        handle.setOnClickListener(v->toggleSheet());
        handle.setOnTouchListener((v,e)->{
            if(landscapeSplit)return false;
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    sheetTouchY=e.getRawY();draggingSheet=false;
                    dragStartHeight=sheetHolder.getLayoutParams().height;return true;
                case MotionEvent.ACTION_MOVE:
                    if(Math.abs(e.getRawY()-sheetTouchY)>dp(12))draggingSheet=true;
                    if(draggingSheet&&panelOpen&&!landscapeSplit){
                        int available=editorArea.getHeight();
                        int max=Math.min(dp(340),Math.max(dp(160),available/2));
                        int target=Math.max(dp(155),Math.min(max,dragStartHeight+Math.round(sheetTouchY-e.getRawY())));
                        android.view.ViewGroup.LayoutParams lp=sheetHolder.getLayoutParams();
                        lp.height=target;sheetHolder.setLayoutParams(lp);syncCanvasDockSpace(target);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if(draggingSheet){
                        if(panelOpen){
                            int height=sheetHolder.getLayoutParams().height;
                            if(height<dp(148)){collapseToolSheet();}
                            else{workspaceDockDp[activeWorkspace]=Math.round(height/getResources().getDisplayMetrics().density);
                                getPreferences(MODE_PRIVATE).edit().putInt("workspaceDockHeight"+activeWorkspace,workspaceDockDp[activeWorkspace]).apply();}
                        }else if(e.getRawY()<sheetTouchY-dp(12))expandToolSheet();
                        else collapseToolSheet();
                    }else v.performClick();
                    draggingSheet=false;return true;
                case MotionEvent.ACTION_CANCEL:draggingSheet=false;return true;
                default:return false;
            }
        });
        handle.setContentDescription(handle.getContext().getString(R.string.quality_swipe_up_for_compact_adjustments_or_down_to_collapse));
    }
    /** Direct subpanel links also update phase/stage highlights, avoiding mismatched UI state. */
    private void setTool(int t){
        int destination=WorkflowFlow.stageForTool(t,workflowStep);
        int safe=WorkflowFlow.navigate(destination,photo!=null,hasPendingSnip());
        if(safe!=destination){
            switchWorkspaceState(0);activeSubtoolPosition=0;
            setWorkflowStep(safe);return;
        }
        workflowStep=destination;
        if(photo!=null&&t!=0&&t!=8&&t!=10)ensurePreparedPhotoPalette();
        selectedPhase=WorkflowFlow.phaseOf(workflowStep);
        lastStepInPhase[selectedPhase]=workflowStep;
        // Keep the highlighted main tab synchronized with deep links, saved
        // favorites, palette changes, and direct panel actions.
        int workspace=t==2?3:t==3?4:(t==5||t==6||t==7)?5:
            (t==1||t==4||t==9)?2:0;
        switchWorkspaceState(workspace);
        if(workspace==0)activeSubtoolPosition=t==10?3:t==8?2:destination==WorkflowFlow.CROP?1:0;
        else if(workspace==2)activeSubtoolPosition=0;
        else if(workspace==5)activeSubtoolPosition=t==5?1:t==6?(boundaryPanelSelected?0:2):3;
        else activeSubtoolPosition=0;
        setToolInternal(t);
        expandToolSheet();
    }
    /** Refresh only the open shelf controls; preserve scroll, zoom, and canvas View identity. */
    private void refreshShelfPanel(){
        invalidateToolPanels();
        if(sheetScroll==null||sheetContent==null)return;
        final int y=sheetScroll.getScrollY();
        // Keep the existing shelf window and navigation; only replace its parameter card.
        sheetContent.removeAllViews();
        switch(tool){
            case 1:if(alternateActive())halftonePanel();else if(settings.mode==VinylEngine.STENCIL)stencilAdjustPanel();else halftonePanel();break;
            case 2:if(newStyleActive())newStyleColorsPanel();else colorPaletteOnlyPanel();break;
            case 3:if(newStyleActive())newStyleLayersPanel();else unifiedLayersPanel();break;
            case 4:curvesPanel();break;
            default:return;
        }
        decorateCollapsibleSections();
        sheetScroll.post(()->sheetScroll.scrollTo(0,y));
    }
    private String toolPanelCacheKey(int t){
        return toolPanelRevision+"|"+t+"|"+workflowStep+"|"+activeWorkspace+"|"+designType+
            "|"+photoRevision+"|"+toolPanelRevision+
            "|"+chosen+"|"+tonalSelectedLayer+"|"+tessSelectedLayer+"|"+newStyleId+"|"+newStyleVariant+"|"+newStyleColorCount+"|"+newStyleDetail+"|"+
            tonalLayerCount+"|"+tessLayerCount+"|"+tonalCutLayerPreview+"|"+
            tonalStencilMode+"|"+layeredCellMode+"|"+tessellationMode+"|"+showStackControls+
            "|"+boundaryPanelSelected+"|"+panelOpen+"|"+chosenProfile;
    }
    private void setToolInternal(int t){
        VinylCrashReport.breadcrumb("workspace="+activeWorkspace+" tool="+t);
        // Snip controls are stateful (mode, points, applied masks). Never reuse a stale card.
        if(t==8||t==5)invalidateToolPanels();
        if(t!=10&&tool==10)showOriginal=false;
        int previousTool=tool;
        tool=t;
        toolTitle.setText(workspaceName(activeWorkspace));
        workflowNext.setText(workflowStep==WorkflowFlow.EXPORT?"Export →":
            workflowStep==WorkflowFlow.SNIP&&hasPendingSnip()?"Finish snip":"Continue →");
        workflowNext.setAlpha(hasPendingSnip()&&workflowStep==WorkflowFlow.SNIP?.55f:1f);
        rebuildNavigation();
        if(layerScroller!=null)layerScroller.setVisibility(View.GONE);
        if(status!=null)status.setVisibility(View.GONE);
        if(canvasHint!=null)canvasHint.setVisibility(workflowStep==WorkflowFlow.SNIP||workflowStep==WorkflowFlow.MARKS?View.VISIBLE:View.GONE);
        // A cache hit avoids reconstructing hundreds of controls on a simple
        // tab switch. The host scroll view remains untouched; only its detached
        // panel subtree is swapped back into place.
        String panelKey=toolPanelCacheKey(tool);
        LinearLayout ready=toolPanelCache.get(panelKey);
        if(ready==null){
            final long panelBuildStarted=android.os.SystemClock.elapsedRealtimeNanos();
            android.os.Trace.beginSection("VinylStudio.PanelBuild");
            ready=new LinearLayout(this);ready.setOrientation(LinearLayout.VERTICAL);
            final LinearLayout host=sheetContent;
            sheetContent=ready;
            try{
                switch(tool){
                    case 0:if(workflowStep==WorkflowFlow.CROP)cropStepPanel();else imageStepPanel();break;
                    case 1:if(alternateActive())halftonePanel();else if(settings.mode==VinylEngine.STENCIL)stencilAdjustPanel();else halftonePanel();break;
                    case 2:if(newStyleActive())newStyleColorsPanel();else colorPaletteOnlyPanel();break;
                    case 3:if(newStyleActive())newStyleLayersPanel();else unifiedLayersPanel();break;
                    case 4:curvesPanel();break;
                    case 5:marksPanel();break;
                    case 6:if(boundaryPanelSelected)boundaryPanel();else cutPanel();break;
                    case 7:exportStepPanel();break;
                    case 8:snipPanel();break;
                    case 9:typeStepPanel();break;
                    case 10:levelsPanel();break;
                }
                decorateCollapsibleSections();
            }finally{
                sheetContent=host;
                android.os.Trace.endSection();
                android.util.Log.d("VinylPerf", "panelTool="+t+" buildMs="+
                    ((android.os.SystemClock.elapsedRealtimeNanos()-panelBuildStarted)/1_000_000L));
            }
            toolPanelCache.put(panelKey,ready);
        }
        final int retainedScroll=sheetScroll==null?0:sheetScroll.getScrollY();
        sheetContent.removeAllViews();
        if(ready.getParent() instanceof ViewGroup)((ViewGroup)ready.getParent()).removeView(ready);
        sheetContent.addView(ready,new LinearLayout.LayoutParams(-1,-2));
        if(t==3)updateVisibleLayerCutLabels();
        final String find=pendingScrollTo;pendingScrollTo=null;
        sheetScroll.post(()->{
            if(find!=null)scrollToControl(find);
            else if(tool==3&&previousTool==3)sheetScroll.scrollTo(0,retainedScroll);
            else sheetScroll.scrollTo(0,0);
        });
        refreshMiniPanel(); // Legacy quick action row is deliberately not mounted.
        // Panel navigation does not rerender the artwork or reset its zoom.
        if(artwork!=null&&(tool==8||tool==5||previousTool==8||previousTool==5))artwork.invalidate();
        if(sheetScroll!=null)sheetScroll.setVisibility(landscapeSplit||panelOpen?View.VISIBLE:View.GONE);
    }
    private void rebuildLayers(){
        if(layerBar==null)return;
        final int previousScroll=layerScroller==null?0:layerScroller.getScrollX();
        layerBar.removeAllViews();
        TextView comp=chip("ALL",chosen==-1,0xffd6e4ff);
        comp.setOnClickListener(v->{if(chosen==-1)return;chosen=-1;rebuildLayers();refreshLayerStatus();refreshShelfPanel();artwork.invalidate();});
        layerBar.addView(comp);
        if(underlayRequested()){
            TextView baseChip=chip("1 Snip base",silhouetteOnly(),autoSilhouetteColor);
            baseChip.setContentDescription(baseChip.getContext().getString(R.string.quality_physical_vinyl_layer_1_automatic_filled_snip_background));
            baseChip.setOnClickListener(v->{pendingScrollTo="Physical L1";setToolInternal(3);});
            layerBar.addView(baseChip);
        }
        if(!silhouetteOnly())for(int i=0;i<settings.layers.size();i++){
            final int index=i;VinylEngine.Layer layer=settings.layers.get(i);
            String label=layer.cmykChannel==0?"C":layer.cmykChannel==1?"M":layer.cmykChannel==2?"Y":layer.cmykChannel==3?"K":layer.name;
            int physicalIndex=i+(underlayRequested()?2:1);
            TextView pill=chip(physicalIndex+" "+label,chosen==index,layer.color);
            pill.setAlpha(layer.enabled?1f:.38f);
            pill.setContentDescription(label+", physical vinyl layer "+physicalIndex+" of "+(settings.layers.size()+(underlayRequested()?1:0)));
            pill.setOnClickListener(v->{chosen=chosen==index?-1:index;rebuildLayers();refreshLayerStatus();
                if(tool==2||tool==3||tool==4||tool==1)refreshShelfPanel();artwork.invalidate();});
            layerBar.addView(pill);
        }
        if(layerScroller!=null)layerScroller.post(()->layerScroller.scrollTo(previousScroll,0));
        refreshChannelOverlay();updateEditorSubtitle();
    }
    private TextView chip(String label,boolean active,int dot){TextView c=text("●  "+label,13,active?TEXT:MUTED,true);android.text.SpannableString sp=new android.text.SpannableString("●  "+label);sp.setSpan(new android.text.style.ForegroundColorSpan(dot),0,1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);c.setText(sp);c.setGravity(Gravity.CENTER);c.setPadding(dp(11),0,dp(11),0);c.setBackground(pillBackground(active,StudioTheme.R_PILL));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(36));p.rightMargin=dp(7);c.setLayoutParams(p);return c;}
    private void refreshLayerStatus(){
        if(latest==null)return;
        String text=LayerRenderStatus.describe(latest,latestDiagnostics,chosen,visibilityIssue);
        status.setText(currentMarkIssue==null?text:getString(R.string.status_check_registration_marks,text));
    }
    /** A custom per-color ribbon can need midtone recovery even if the overall pattern is dots. */
    private boolean hasMidtoneRibbonLayer(){
        for(VinylEngine.Layer vinyl : settings.layers)if(vinyl.enabled){
            int style=vinyl.lineStyleOverride<0?settings.pattern:vinyl.lineStyleOverride;
            if(LineArtEngine.supportsPortraitMidtones(style))return true;
        }
        return false;
    }
    private VinylEngine.Layer layer(){return settings.layers.get(Math.max(0,Math.min(chosen,settings.layers.size()-1)));}
    private String svgStatus(VinylEngine.Result r){
        int max=0,active=0;for(int i=0;i<r.shapes.size();i++)if(r.settings.layers.get(i).enabled){active++;max=Math.max(max,VinylEngine.svgElementsForLayer(r,i));}
        AutoSilhouetteUnderlay.Output base=currentUnderlay();
        if(silhouetteOnly()&&base!=null)return "1 physical layer · SVG max "+
            (base.cutContours()+(r.settings.registration?4:0))+"/5k · 1 filled silhouette";
        if(base!=null){active++;max=Math.max(max,base.cutContours()+(r.settings.registration?4:0));}
        return active+" physical layers · SVG max "+max+"/5k · "+(r.count()+(base==null?0:1))+" filled shapes";
    }
    private void appendSvgCounts(LinearLayout holder,VinylEngine.Result r){
        holder.addView(text(getString(R.string.ui_each_exported_color_is_a_separate),12,MUTED,false));
        int enabled=0,exported=0,omitted=0;
        AutoSilhouetteUnderlay.Output base=currentUnderlay();
        if(base!=null){
            if(silhouetteOnly())enabled=0;
            enabled++;exported++;
            int paths=base.cutContours()+(r.settings.registration?4:0);
            holder.addView(text("L1 · Solid Snip background · "+colorHex(base.color)+" · "+paths+
                " / 5,000 cut paths "+(paths>5000?"OVER LIMIT":"OK"),13,paths>5000?0xffEF8B8B:0xff92E7BB,true));
        }
        if(!silhouetteOnly())for(int i=0;i<r.shapes.size();i++){
            VinylEngine.Layer l=r.settings.layers.get(i);
            if(!l.enabled)continue;
            enabled++;
            boolean cuts=VinylEngine.cutLayer(r,i);
            if(cuts)exported++;else omitted++;
            int n=VinylEngine.svgElementsForLayer(r,i);
            String label=base==null?ExportColorLabels.physicalLabel(i,l.color,l.cmykChannel):
                "L"+(i+2)+" · "+colorHex(l.color)+" ("+ExportColorLabels.channel(l.cmykChannel)+")";
            String value=cuts?String.format(Locale.US,"%,d / 5,000 paths %s",n,
                n>5000?"OVER LIMIT":n>4300?"Near limit":"OK"):
                ExportColorLabels.status(l.enabled,false);
            TextView row=text(label+" · "+value,13,
                !cuts?0xffFFD093:n>5000?0xffEF8B8B:n>4300?0xffFFD093:0xff92E7BB,true);
            row.setPadding(dp(2),dp(8),dp(2),dp(5));holder.addView(row);
        }
        holder.addView(text(String.format(Locale.US,
            "%d of %d enabled physical colors will be exported as SVGs%s",exported,enabled,
            omitted>0?" · "+omitted+" with nothing to cut":""),12,
            omitted>0?0xffFFD093:MUTED,true));
        // D5: the mounting surface no longer affects which layers have geometry.
        if(omitted>0)holder.addView(text("A color with nothing to cut at this size, or one excluded in Layers, is not exported. "+
            "Pick a color that appears in the photo, or adjust coverage in Layers, if it must be cut.",12,MUTED,false));
        holder.addView(text(String.format(Locale.US,
            "All layers combined: %,d generated artwork shapes (including excluded colors; informational only)",
            r.count()+(base==null?0:1)),12,MUTED,false));
    }
    private void acceptCrop(PhotoCropEditor.CropResult result){
        settings.circleCrop=result.circular;photo=result.bitmap;changedPhoto();canvasGeometry=null;imageW=photo.getWidth();imageH=photo.getHeight();pixels=new int[imageW*imageH];photo.getPixels(pixels,0,imageW,0,0,imageW,imageH);
        latest=null;renderedSerial=-1;settings.marks=null;chosen=-1;artwork.resetZoom();
        preparedToneCache=null;settings.snips.clear();snipDraft.clear();snipClosed=false;snipPan=false;editingSnipIndex=-1;editingOriginal=null;snipTimeline.clear();
        tonalPreview=null;rebuildLayers();setWorkflowStep(WorkflowFlow.SNIP);status.setText(getString(R.string.crop_applied_status));requestRender();saveLocalPhoto();
    }
    private void openCropForCurrent(float targetAspect,float targetWidth){
        Bitmap source=originalPhoto!=null?originalPhoto:photo;if(source==null){toast(getString(R.string.ui_import_a_photo_first));pick();return;}
        Runnable show=()->PhotoCropEditor.show(this,source,null,targetAspect,result->{checkpoint();
            if(targetWidth>0){
                // Reframing a selected photograph must not silently revert
                // manual line spacing, brightness/detail, palette or per-layer
                // tuning to the style's initial presets. Only the requested
                // physical artwork width changes here; the crop determines height.
                settings.widthMm=VinylEngine.toMm(targetWidth);
            }
            acceptCrop(result); // Cropping changes coordinates, so existing snips must reset.
        },null);
        if(settings.snips!=null&&!settings.snips.isEmpty())new AlertDialog.Builder(this)
            .setTitle(getString(R.string.ui_reframe_this_photograph))
            .setMessage("Changing the crop will clear previous snips and reposition registration marks. The original photograph is preserved.")
            .setNegativeButton(getString(R.string.ui_keep_current_crop),null).setPositiveButton(getString(R.string.ui_reframe_and_clear_snips),(d,which)->show.run()).show();
        else show.run();
    }
    private void choosePosterSize(VinylSizing.Size size){
        chosenProfile=Math.max(0,Math.min(2,chosenProfile));
        // Cropping to the chosen aspect ratio MUST precede the physical size change.
        openCropForCurrent(size.widthIn/size.heightIn,size.widthIn);
    }
    private void setVinylProfile(int id){chosenProfile=Math.max(0,Math.min(2,id));
        if(photo==null){setTool(0);return;}
        checkpoint();float w=VinylEngine.toInches(settings.widthMm),h=w*imageH/(float)imageW;
        SmartHalftonePresets.apply(settings,settings.pattern,chosenProfile,w,h);retainCutMaterialProfile();setToolInternal(tool);requestRender();
    }
    private void autoFitPaths(){if(photo==null||pixels==null){toast(getString(R.string.ui_import_and_crop_first));return;}
        final VinylEngine.Settings source=settings.copy();final int[] input=pixels;final int w=imageW,h=imageH;final int profile=chosenProfile;
        final int requested=++serial;handler.removeCallbacks(renderDebounce);status.setText(R.string.quality_fitting_every_svg_independently);
        scheduleDiskJob(()->{try{
            int[] fittedPixels=ImageLevels.isNeutral(source)?input:ImageLevels.apply(input,w,h,source);
            if(requested!=serial)return;
            VinylSizing.Fit fit=VinylSizing.fit(fittedPixels,w,h,source,profile);
            CanvasGeometry fitGeometry=CanvasGeometry.create(fit.result);
            postUi(()->{if(requested!=serial)return;
            checkpoint();settings=fit.settings;latest=fit.result;canvasGeometry=fitGeometry;renderedSerial=requested;
            rebuildLayers();setToolInternal(tool);artwork.invalidate();status.setText(svgStatus(fit.result));
            toast("Spacing chosen for "+VinylSizing.PROFILES[profile]+" · most complex SVG: "+fit.maxLayer+" paths");
        });}catch(Exception err){postUi(()->{if(requested==serial){status.setText(getString(R.string.status_auto_fit_failed,friendlyFailure(err,"Unable to fit this artwork")));toast(getString(R.string.ui_try_easy_weed_or_a_smaller));}});}});
    }
    private void imageStepPanel(){
        LinearLayout a=group();
        action(a,photo==null?"Import photo":"Import another photo",this::pick);
        action(a,getString(R.string.take_photo),this::takePhoto);
        if(photo!=null)action(a,"Compare original",()->{showOriginal=!showOriginal;artwork.invalidate();});
        action(a,"Project library",this::showProjectLibrary);
    }
    private void cropStepPanel(){
        LinearLayout a=group();section(a,"Crop and reframe");
        a.addView(text("Set the image composition here. Re-cropping clears previous snips because their coordinates change. Final physical dimensions are chosen separately in Size, after Lighting.",12,MUTED,false));
        action(a,"Crop / Reframe photograph",()->openCropForCurrent(0,0));
    }

    private void chooseDesignType(int type){
        checkpoint();designType=type;
        if(type==1){settings.mode=VinylEngine.STENCIL;settings.dither=VinylEngine.NEAREST;
            settings.stencilCleanup=Math.max(0,Math.min(3,settings.stencilCleanup));}
        else{
            settings.mode=VinylEngine.CmykExtendedStack.hasAnchors(settings.layers)
                ? VinylEngine.CMYK : VinylEngine.SPOT;
            if(type==2){float w=VinylEngine.toInches(settings.widthMm);
                float h=imageW>0?w*imageH/(float)imageW:w*1.25f;
                SmartHalftonePresets.apply(settings,VinylEngine.LINE,chosenProfile,w,h);}
        }
        chosen=-1;rebuildLayers();setTool(9);requestRender();
    }
    private void typeStepPanel(){
        LinearLayout a=group();section(a,"What kind of vinyl artwork?");
        a.addView(text(getString(R.string.ui_choose_how_colors_become_cut_paths),12,MUTED,false));
        designTypeCard(a,1,"Multicolor stencil","Flat-color vector contours, with optional paper background and cleanup. No halftone dots.");
        designTypeCard(a,0,"Photographic halftone","Round dots, squares, dashes, waves and more. CMYK or custom separated vinyl layers.");
        designTypeCard(a,2,"Contour line portrait","Connected variable-width lines, automatically tuned for portraits.");
    }
    private void designTypeCard(LinearLayout parent,int id,String label,String description){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14),dp(10),dp(14),dp(10));
        card.setBackground(outlined(designType==id?0xff344C66:StudioTheme.SURFACE_HIGH,designType==id?ACCENT:0xff475261,15));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);parent.addView(card,lp);
        card.addView(text((designType==id?"●  ":"○  ")+label,15,TEXT,true));
        card.addView(text(description,12,MUTED,false));
        card.setOnClickListener(v->chooseDesignType(id));
    }
    private void linkedMasterControlsNotice(LinearLayout panel){
        if(settings.linkedControls)return;
        panel.addView(text("Independent layer spacing, density or contrast is active. " +
            "Edit each sheet in Layers, or explicitly relink to use master sliders.",12,MUTED,false));
        action(panel,"Relink master screen controls",()->{
            checkpoint();settings.linkedControls=true;setLinkedValues();
            invalidateToolPanels();requestRender();
        });
    }
    private void stencilAdjustPanel(){
        LinearLayout a=group();section(a,"Flat multicolor stencil");
        // Automatic image-aware setup runs on style selection (no extra button).
        linkedMasterControlsNotice(a);
        if(settings.linkedControls)
            inchSlider(a,"Region spacing · smaller = finer",settings.masterSpacingMm,.035f,.25f,.005f,
                v->{settings.masterSpacingMm=v;setLinkedValues();});
        a.addView(text(getString(R.string.ui_color_regions_are_traced_into_continuous),12,MUTED,false));
        // Existing cleanup and rounding settings are still honored for legacy
        // projects; default values are selected automatically for new projects.
        action(a,settings.stencilPaperBackground?"Paper as background: ON":"Paper as background: OFF",()->{
            checkpoint();settings.stencilPaperBackground=!settings.stencilPaperBackground;setTool(1);requestRender();});
        a.addView(text(getString(R.string.ui_choose_vinyl_colors_under_colors_and),12,MUTED,false));
        a.addView(text(getString(R.string.ui_detail_controls_how_finely_colors_are),12,MUTED,false));
    }
    private void exportStepPanel(){
        LinearLayout a=group();section(a,"Review and export vinyl cuts");
        a.addView(text(alternateActive()?
            "The active style exports its actual final SVG geometry. Vinyl ribbons are closed cuts; pen centerlines are drawing paths. Multicolor files retain their layer order.":
            "Each visible vinyl color produces one cut sheet with identical registration marks and artboard dimensions. Empty or explicitly skipped sheets are excluded. Files are numbered in physical application order.",12,MUTED,false));
        if(alternateActive()){
            if(!silhouetteOnly())appendSpecialtyCutReview(a);
            AutoSilhouetteUnderlay.Output base=currentUnderlay();
            if(base!=null)a.addView(text("Extra L1 · Solid Snip · "+colorHex(base.color)+
                " · "+base.cutContours()+" cut contours. " +
                "The selected color layers remain intact and export as L2 onward.",12,ACCENT,true));
        }
        else if(standardFinalReady()){
            if(silhouetteOnly())a.addView(text("1 physical sheet · L1 solid Snip · " + colorHex(autoSilhouetteColor)+" · 1 filled shape",13,ACCENT,true));
            else appendSvgCounts(a,latest);
            String issue=VinylEngine.validateExport(latest);
            a.addView(text(issue==null?"Ready for cut export — test cut on scrap":"Needs attention: "+issue,
                12,issue==null?0xff90E5BB:0xffFFC39E,true));
        }else a.addView(text("Final render pending — export is disabled until the current artwork is ready.",12,0xffFFC39E,true));
        final OutputDeviceProfile.Profile target=OutputDeviceProfile.byId(outputDeviceId);
        if(target.svgCutZip){
            action(a,includeSourcePhotoInCutZip?"Include photo in CUT ZIP: ON":"Include photo in CUT ZIP: OFF",()->{
                includeSourcePhotoInCutZip=!includeSourcePhotoInCutZip;setToolInternal(7);
            });
        }
        action(a,"Output: "+target.name,this::chooseOutputDevice);
        a.addView(text(target.summary,12,MUTED,false));
        action(a,target.svgCutZip?"Export cut ZIP":"Export printable PDF",this::requestExport);
        action(a,"Export composite PNG image",()->requestArtworkDocument(false));
        action(a,"Export composite PDF document",()->requestArtworkDocument(true));
        action(a,"Export assembly guide PDF",this::requestAssemblyGuide);
        a.addView(text("PNG and PDF show the finished composite, not calibrated Print Then Cut sheets. " +
            "Cutter ZIPs retain separate physical layers. DXF import requires checking physical size. No output target changes your cut paths or machine settings.",12,MUTED,false));
        action(a,"Save to project library",this::saveCurrentProject);
        action(a,"Back up editable project as ZIP",()->chooseProject(true));
    }

    /** RC129: one searchable, responsive art-style picker for every existing engine. */
    private void showUnifiedStyleDrawer(){ showCompactStylePicker("All"); }
    /** Bounded on-selection photo analysis. Never runs during onDraw or slider movement. */
    private AutoPhotoTuner.Stats selectionPhotoStats(){
        if(photo==null)return null;
        Bitmap sample=null;
        try{
            int w=photo.getWidth(),h=photo.getHeight();
            float ratio=Math.min(1f,96f/Math.max(1,Math.max(w,h)));
            int sw=Math.max(2,Math.round(w*ratio)),sh=Math.max(2,Math.round(h*ratio));
            sample=Bitmap.createScaledBitmap(photo,sw,sh,true);
            int[] colors=new int[sw*sh];sample.getPixels(colors,0,sw,0,0,sw,sh);
            return AutoPhotoTuner.analyze(colors,sw,sh,settings.snips,settings.circleCrop);
        }catch(RuntimeException|OutOfMemoryError failure){return null;}
        finally{if(sample!=null&&sample!=photo&&!sample.isRecycled())sample.recycle();}
    }
    private AutoPhotoTuner.Suggestion selectionSuggestion(int family){
        AutoPhotoTuner.Stats stats=selectionPhotoStats();
        if(stats==null)return null;
        try{return AutoPhotoTuner.suggest(stats,family,AutoPhotoTuner.BALANCED,
            settings.widthMm,Math.max(VinylEngine.MINIMUM_CUT_WIDTH_MM,settings.minimumMm));}
        catch(IllegalArgumentException invalid){return null;}
    }
    /** Saved-project art-engine IDs remain stable when the picker entries move. */
    private void activateNewStyle(int id){ activateNewStyle(id,-1); }
    /** A requested tessellation layout is applied before the first render, not after it. */
    private void activateNewStyle(int id,int requestedVariant){
        if(!NewStyleVariantPolicy.selectable(id))return;
        checkpoint();
        rememberNewStylePalette(); // stores the OUTGOING suite style's palette; must precede the switch
        applyStyleSelection(StyleSelection.suite(id));newStyleVariant=0;newStyleSelectedLayer=-1;
        if(id>=VinylStyleSuite.CONFETTI&&id<=VinylStyleSuite.CONFETTI_FALLEN){
            trioSpacingMm=trioSpacingMemory[id-VinylStyleSuite.CONFETTI];
            trioOrganic=trioOrganicMemory[id-VinylStyleSuite.CONFETTI];
        }
        alternateStatus=VinylStyleSuite.NAMES[id]+" · preparing preview…";
        newStyleResult=null;newStyleVectors=null;
        if(newStyleSettingsSaved[id]){
            newStyleDetail=newStyleDetailMemory[id];
            newStyleVariant=NewStyleVariantPolicy.restore(id,newStyleVariantMemory[id]);
            newStyleWeightMm=newStyleWeightMemory[id];newStyleLeadMm=newStyleLeadMemory[id];
            newStyleSmoothing=OrganicFlowCutSmoother.DEFAULT;
        }else {
            PhotoStyleDefaults.Initial initial=PhotoStyleDefaults.forNewStyle(id,
                selectionPhotoStats(),settings.minimumMm,settings.widthMm);
            newStyleDetail=initial.detail;
            newStyleWeightMm=initial.weightMm;
            newStyleLeadMm=initial.leadMm;
            newStyleSmoothing=OrganicFlowCutSmoother.DEFAULT;
        }
        if(id==VinylStyleSuite.TESSELLATION_MOTIF&&!newStyleSettingsSaved[id])
            newStyleVariant=TessellationMotifEngine.TRIHEX;
        if(requestedVariant>=0)
            newStyleVariant=NewStyleVariantPolicy.restore(id,requestedVariant);
        final boolean savedPalette=newStylePaletteMemory[id]!=null;
        final boolean useCamoDefaults=CamouflagePalette.useFamilyDefault(id,savedPalette,
            newStyleManualMemory[id],newStylePhotoAutoMemory[id]);
        if(savedPalette){
            newStyleColors=useCamoDefaults?
                CamouflagePalette.defaults(id,expandNewStyleSwatches(newStylePaletteMemory[id])):
                expandNewStyleSwatches(newStylePaletteMemory[id]);
            newStyleColorCount=Math.max(NewStyleColorSlots.minimumSelectable(id),
                Math.min(NewStyleColorSlots.maximumSelectable(id),newStyleCountMemory[id]));
            newStyleMatchVinyl=useCamoDefaults||newStyleManualMemory[id];
        }else{
            newStyleColors=CamouflagePalette.defaults(id,expandNewStyleSwatches(tessColors));
            int preferred=id==VinylStyleSuite.POP_PANELS?4:
                (id==VinylStyleSuite.POSTER_STENCIL||VinylStyleSuite.isCraft(id))?9:
                id==VinylStyleSuite.TESSELLATION_MOTIF?8:6;
            newStyleColorCount=Math.max(NewStyleColorSlots.minimumSelectable(id),
                Math.min(NewStyleColorSlots.maximumSelectable(id),preferred));
            newStyleMatchVinyl=useCamoDefaults;
            if(id==VinylStyleSuite.POSTER_STENCIL){
                newStyleColors=PopPosterEngine.posterPalette(posterOptions.posterPalette,9);
                newStyleMatchVinyl=true;
                newStylePhotoAutoMemory[id]=false;
            }
        }
        newStylePaletteRanks=SmartPaletteReduction.validRanks(savedPalette&&!useCamoDefaults?newStyleRankMemory[id]:null,9);
        if(id==VinylStyleSuite.POSTER_STENCIL)posterOptions.toneCount=newStyleColorCount;
        if(useCamoDefaults){
            newStylePhotoAutoMemory[id]=false;
            selectedPaletteKey="camo:"+id;
        }
        newStyleHueBaseColors=newStyleColors.clone();newStyleHueDegrees=0;
        activeWorkspace=2;activeSubtoolPosition=0;alternateGeneration++;
        setToolInternal(1);expandToolSheet();requestAlternate();
    }
    /** Select any of the 28 tessellation layouts directly from the shared picker.
     * Keep detail, layer count and manually edited swatches when only the layout changes.
     * Switching from another style performs exactly one initial geometry render.
     */
    private void selectTessellationLayout(int pattern){
        if(!TessellationMotifEngine.selectablePattern(pattern))return;
        if(!newStyleActive()||newStyleId!=VinylStyleSuite.TESSELLATION_MOTIF){
            activateNewStyle(VinylStyleSuite.TESSELLATION_MOTIF,pattern);
            return;
        }
        boolean changed=newStyleVariant!=pattern;
        if(changed){
            checkpoint();
            newStyleVariant=pattern;
            newStyleVariantMemory[VinylStyleSuite.TESSELLATION_MOTIF]=pattern;
        }
        activeWorkspace=2;activeSubtoolPosition=0;
        setToolInternal(1);expandToolSheet();
        if(changed)requestAlternate();
    }
    private static String newStyleVariantName(int style,int variant){
        switch(style){
            case VinylStyleSuite.TSP:return variant==0?"Pen / Engrave":"Cut Vinyl";
            case VinylStyleSuite.LINOCUT:return variant==0?"Single pass":"Crosshatch";
            case VinylStyleSuite.TRUCHET:return new String[]{"Arcs","Diagonals","Triangles","Mixed"}[Math.max(0,Math.min(3,variant))];
            case VinylStyleSuite.TEXT:return variant==0?"Legacy ASCII":(variant==2?"Word Knockout":"Word Mosaic");
            case VinylStyleSuite.TOPOGRAPHIC:return variant==0?"Filled layers":"Contour ribbons";
                        case VinylStyleSuite.TESSELLATION_MOTIF:return TessellationMotifEngine.PATTERNS[
                Math.max(0,Math.min(TessellationMotifEngine.PATTERNS.length-1,variant))];
            case VinylStyleSuite.BANKNOTE:return BanknoteEngravingEngine.VARIANTS[
                Math.max(0,Math.min(BanknoteEngravingEngine.VARIANTS.length-1,variant))];
            default:
                if(style>=VinylStyleSuite.MARQUETRY&&style<=VinylStyleSuite.ARCHITECTURAL)
                    return ExpandedPortraitStylesEngine.variantName(style-VinylStyleSuite.MARQUETRY,variant);
                return "Standard";
        }
    }
    private void popPosterControls(LinearLayout host){
        final boolean pop=newStyleId==VinylStyleSuite.POP_PANELS;
        final PopPosterEngine.Options o=popPosterOptions();
        String[] presets=pop?PopPosterEngine.POP_PRESETS:PopPosterEngine.POSTER_PRESETS;
        action(host,"Preset: "+presets[Math.max(0,Math.min(presets.length-1,o.preset))]+"  ›",()->
            new AlertDialog.Builder(this).setTitle(getString(R.string.ui_choose_a_preset)).setItems(presets,(dialog,which)->{
                checkpoint();PopPosterEngine.Options updated=PopPosterEngine.preset(pop?0:1,which,settings.widthMm);
                if(pop)popOptions=updated;else{
                    posterOptions=updated;
                    posterOptions.toneCount=newStyleColorCount; // Layers owns the count.
                }
                requestAlternate();setToolInternal(1);
            }).show());
        action(host,"Layer construction: "+(o.mode==PopPosterEngine.STACKED?"Stacked":"Knockout")+"  ›",()->{
            checkpoint();o.mode=o.mode==PopPosterEngine.STACKED?PopPosterEngine.KNOCKOUT:PopPosterEngine.STACKED;
            requestAlternate();setToolInternal(1);
        });
        if(o.mode==PopPosterEngine.KNOCKOUT)slider(host,"Underlay trap · mm",o.trapMm,0f,1.2f,.1f,v->{
            o.trapMm=v;requestAlternate();});
        action(host,"Background: "+(o.bgMode==0?"Auto":o.bgMode==1?"By tone":"Off")+"  ›",()->{
            checkpoint();o.bgMode=(o.bgMode+1)%3;requestAlternate();setToolInternal(1);
        });
        if(o.bgMode==PopPosterEngine.BG_TONE)slider(host,"Background tone",o.bgToneThreshold,.02f,.75f,.01f,v->{
            o.bgToneThreshold=v;requestAlternate();});
        if(o.bgMode==PopPosterEngine.BG_AUTO)slider(host,"Auto background tolerance",o.bgTolerance,.02f,.35f,.01f,v->{
            o.bgTolerance=v;requestAlternate();});
        slider(host,"Photo contrast",o.contrast,.6f,2f,.05f,v->{o.contrast=v;requestAlternate();});
        slider(host,"Photo smoothing",o.blur,0f,5f,1f,v->{o.blur=v;requestAlternate();});
        if(pop){
            String[] grids={"2×2","1×1","1×4","3×3","2×3","4×1"};
            action(host,"Panel grid: "+grids[o.grid]+"  ›",()->
                new AlertDialog.Builder(this).setTitle(getString(R.string.ui_panel_layout)).setItems(grids,(d,index)->{
                    checkpoint();o.grid=index;requestAlternate();setToolInternal(1);
                }).show());
            slider(host,"Panel gutter · %",o.gutter*100f,0,8,.5f,v->{o.gutter=v/100f;requestAlternate();});
            slider(host,"Shadow threshold",o.shadowT,.15f,.85f,.02f,v->{
                o.shadowT=v;if(o.keyT<o.shadowT+.02f)o.keyT=o.shadowT+.02f;requestAlternate();});
            slider(host,"Key threshold",o.keyT,.4f,.95f,.01f,v->{
                o.keyT=Math.max(v,o.shadowT+.02f);requestAlternate();});
            slider(host,"Black screen offset · %",o.keyOff*100f,0,4,.2f,v->{o.keyOff=v/100f;requestAlternate();});
            slider(host,"Color block offset · %",o.blockOff*100f,0,4,.2f,v->{o.blockOff=v/100f;requestAlternate();});
            action(host,"Edit individual panel palettes  ›",()->popPosterPalettePicker());
            host.addView(text(getString(R.string.ui_photo_labels_are_shared_by_all),12,MUTED,false));
        }else{
            host.addView(text(getString(R.string.ui_choose_2_9_physical_layers_in),12,MUTED,false));
            slider(host,"Tone balance",o.toneBias,-.2f,.2f,.02f,v->{o.toneBias=v;requestAlternate();});
            String[] backgrounds={"Split","Rays","Solid","Paper","None"};
            action(host,"Backdrop: "+backgrounds[o.posterBackdrop]+"  ›",()->{
                checkpoint();o.posterBackdrop=(o.posterBackdrop+1)%backgrounds.length;requestAlternate();setToolInternal(1);
            });
            if(o.posterBackdrop==PopPosterEngine.BACK_RAYS)slider(host,"Ray count",o.rays,8,48,2,v->{
                o.rays=Math.round(v);requestAlternate();});
            action(host,"Stripe texture: "+(o.texture?"On":"Off"),()->{
                checkpoint();o.texture=!o.texture;requestAlternate();setToolInternal(1);
            });
            if(o.texture){
                slider(host,"Stripe spacing · px",o.texturePeriod,4,20,1,v->{o.texturePeriod=v;requestAlternate();});
                slider(host,"Stripe fill",o.textureFill,.3f,.7f,.05f,v->{o.textureFill=v;requestAlternate();});
            }
            slider(host,"Frame · %",o.frame*100f,0,8,.5f,v->{o.frame=v/100f;requestAlternate();});
            action(host,"Banner text: "+(o.banner.isEmpty()?"None":o.banner)+"  ›",()->{
                final EditText input=new EditText(this);input.setSingleLine(true);input.setText(o.banner);
                input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(24)});
                new AlertDialog.Builder(this).setTitle(getString(R.string.ui_banner_text_up_to_24_characters))
                    .setView(input).setPositiveButton("Apply",(d,w)->{
                        checkpoint();o.banner=input.getText().toString().trim();requestAlternate();setToolInternal(1);
                    }).setNegativeButton("Cancel",null).show();
            });
        }
    }
    private void popPosterPalettePicker(){
        final PopPosterEngine.Options o=popPosterOptions();
        int count=PopPosterEngine.GRIDS[o.grid][0]*PopPosterEngine.GRIDS[o.grid][1];
        String[] entries=new String[count];for(int i=0;i<count;i++){
            int idx=o.panelPalettes[Math.min(i,o.panelPalettes.length-1)];
            entries[i]="Panel "+(i+1)+" · "+PopPosterEngine.POP_NAMES[idx];
        }
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_pop_panel_palettes)).setItems(entries,(d,panel)->{
            new AlertDialog.Builder(this).setTitle("Panel "+(panel+1)+" vinyl palette")
                .setItems(PopPosterEngine.POP_NAMES,(dd,selection)->{
                    checkpoint();o.panelPalettes[panel]=selection;
                    if(o.panelColors!=null&&panel<o.panelColors.length)o.panelColors[panel]=null;
                    requestAlternate();
                }).show();
        }).show();
    }
    private void editPopPosterHex(int panel,int slot){
        final PopPosterEngine.Options o=popPosterOptions();boolean pop=newStyleId==VinylStyleSuite.POP_PANELS;
        int[] colors=pop?(o.panelColors!=null&&o.panelColors[panel]!=null?o.panelColors[panel]:
            PopPosterEngine.POP_COLORS[o.panelPalettes[panel]]):
            (o.posterColors!=null?o.posterColors:PopPosterEngine.posterPalette(o.posterPalette,o.toneCount));
        EditText input=new EditText(this);input.setSingleLine(true);
        input.setText(String.format(Locale.US,"#%06X",colors[slot]&0xffffff));
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_physical_vinyl_color_rrggbb))
            .setView(input).setPositiveButton("Apply",(d,w)->{
                String entry=input.getText().toString().trim();
                if(!entry.matches("#[0-9a-fA-F]{6}")){toast(getString(R.string.ui_enter_a_six_digit_hex_color));return;}
                checkpoint();int value=Color.parseColor(entry);
                if(pop){
                    if(o.panelColors==null)o.panelColors=new int[9][];
                    if(o.panelColors[panel]==null)o.panelColors[panel]=PopPosterEngine.POP_COLORS[o.panelPalettes[panel]].clone();
                    o.panelColors[panel][slot]=value;
                }else{
                    if(o.posterColors==null)o.posterColors=PopPosterEngine.posterPalette(o.posterPalette,o.toneCount);
                    o.posterColors[slot]=value;
                }
                requestAlternate();setToolInternal(2);
            }).setNegativeButton("Cancel",null).show();
    }
    private void popPosterColorsPanel(){
        final boolean pop=newStyleId==VinylStyleSuite.POP_PANELS;
        final PopPosterEngine.Options o=popPosterOptions();
        LinearLayout mount=group();section(mount,"Mounting surface");mountingSurfaceChoices(mount);
        LinearLayout g=group();section(g,"Vinyl palette · "+VinylStyleSuite.NAMES[newStyleId]);
        if(pop){
            action(g,"Choose colors for each panel  ›",()->popPosterPalettePicker());
            int n=PopPosterEngine.GRIDS[o.grid][0]*PopPosterEngine.GRIDS[o.grid][1];
            for(int panel=0;panel<n;panel++){
                final int index=panel;int[] cols=o.panelColors!=null&&o.panelColors[panel]!=null?
                    o.panelColors[panel]:PopPosterEngine.POP_COLORS[o.panelPalettes[panel]];
                g.addView(text("Panel "+(panel+1)+" · "+PopPosterEngine.POP_NAMES[o.panelPalettes[panel]],13,ACCENT,true));
                for(int slot=0;slot<4;slot++){
                    final int k=slot;int c=cols[slot];
                    action(g,(slot==0?"Background":slot==1?"Color block":slot==2?"Shadow":"Key black")+
                        " · "+String.format(Locale.US,"#%06X",c&0xffffff)+"  ›",()->editPopPosterHex(index,k));
                }
            }
        }else{
            action(g,"Palette: "+PopPosterEngine.POSTER_NAMES[o.posterPalette]+"  ›",()->
                new AlertDialog.Builder(this).setTitle(getString(R.string.ui_poster_palette)).setItems(PopPosterEngine.POSTER_NAMES,(d,which)->{
                    checkpoint();o.posterPalette=which;o.posterColors=null;requestAlternate();setToolInternal(2);
                }).show());
            int[] cols=o.posterColors==null?PopPosterEngine.posterPalette(o.posterPalette,o.toneCount):o.posterColors;
            for(int slot=0;slot<cols.length;slot++){
                final int i=slot;int c=cols[slot];
                action(g,"Tone "+(slot+1)+" · "+String.format(Locale.US,"#%06X",c&0xffffff)+"  ›",()->editPopPosterHex(0,i));
            }
            action(g,"Swap two adjacent tones",()->{
                checkpoint();if(o.posterColors==null)o.posterColors=PopPosterEngine.posterPalette(o.posterPalette,o.toneCount);
                if(o.posterColors.length>2){int j=o.posterColors.length-2,t=o.posterColors[j];
                    o.posterColors[j]=o.posterColors[j-1];o.posterColors[j-1]=t;}
                requestAlternate();setToolInternal(2);
            });
        }
        g.addView(text("One export sheet per distinct vinyl color. Existing sheet coordinates, Snip openings and registration marks remain shared.",12,MUTED,false));
    }
    private void popPosterLayersPanel(){
        LinearLayout host=group();section(host,"Physical vinyl sheets · "+VinylStyleSuite.NAMES[newStyleId]);
        StyleKit.Result rendered=newStyleResult;
        if(rendered==null){host.addView(text(getString(R.string.ui_finish_rendering_to_see_the_exact),12,MUTED,false));return;}
        host.addView(text("Actual sheets: "+rendered.layers.size()+". All cuts in each color remain at their panel positions.",12,MUTED,false));
        for(int i=0;i<rendered.layers.size();i++){
            final int index=i;StyleKit.Layer layer=rendered.layers.get(i);
            action(host,"Sheet "+String.format(Locale.US,"%02d",i+1)+" · "+
                String.format(Locale.US,"#%06X",layer.color&0xffffff)+" · "+layer.loops.size()+" contours"+
                (newStyleSelectedLayer==i?" · Solo":""),()->{
                    newStyleSelectedLayer=newStyleSelectedLayer==index?-1:index;artwork.invalidate();setToolInternal(3);
                });
        }
        host.addView(text("A base region and the other panels must remain aligned. Changing artwork dimensions resets registration geometry, not palettes.",12,MUTED,false));
    }
    private void newStyleControls(LinearLayout host){
        section(host,newStyleId==VinylStyleSuite.TEXT?
            (newStyleVariant==0?"Legacy ASCII":(newStyleVariant==2?"Word Knockout":"Word Mosaic")):VinylStyleSuite.NAMES[newStyleId]);
        if(newStyleId>=VinylStyleSuite.MARQUETRY&&newStyleId<=VinylStyleSuite.ARCHITECTURAL)
            host.addView(text(ExpandedPortraitStylesEngine.DESCRIPTIONS[newStyleId-VinylStyleSuite.MARQUETRY]+
                ". Geometry is rebuilt as bounded vectors using the selected material's cut limits.",13,MUTED,false));
        if(newStyleId==VinylStyleSuite.TEXT)
            host.addView(text(newStyleVariant==0?
                "Symbols follow the photograph's color regions and important edges.":
                (newStyleVariant==2?"Opaque vinyl color plates contain cut-out, readable words. The Snip background shows through the letters.":
                 "Complete cuttable words build image detail over the solid Snip background."),13,MUTED,false));
        if(VinylStyleSuite.isPrismaticFamily(newStyleId)){
            // First control is always Spacing for this style. All controls directly
            // affect vector geometry (no hidden Advanced drawer or dead sliders).
            slider(host,"Spacing · mm",prismaticSpacingMm,.2f,1.6f,.1f,v->{
                prismaticSpacingMm=v;requestAlternate();
            });
        }
        if(VinylStyleSuite.isConfetti(newStyleId)||newStyleId==VinylStyleSuite.CHAINMAIL){
            slider(host,"Spacing · mm",trioSpacingMm,.2f,1.8f,.1f,v->{
                trioSpacingMm=v;requestAlternate();
            });
        }
        slider(host,VinylStyleSuite.isPrismaticFamily(newStyleId)?"Ribbon density / detail":
            newStyleId==VinylStyleSuite.TESSELLATION_MOTIF?"Tile spacing · finer at higher detail":
            newStyleId==VinylStyleSuite.GLASS?"Pane detail":"Detail",newStyleDetail,0,100,5,v->{
            newStyleDetail=Math.round(v);requestAlternate();
        });
        if(VinylStyleSuite.isPopPoster(newStyleId)){popPosterControls(host);return;}
        if(VinylStyleSuite.isPrismaticFamily(newStyleId)){
            slider(host,"Tonal balance · dark / light",prismaticTone,0,100,5,v->{
                prismaticTone=Math.round(v);requestAlternate();
            });
            // Ribbon siblings use the same saved value as wave amplitude plus the
            // style's own pattern strength (offsets, stagger or tangent steering).
            slider(host,newStyleId==VinylStyleSuite.PRISMATIC_DIAGONAL?"Serpentine bend":"Pattern strength",prismaticIrregularity,0,100,5,v->{
                prismaticIrregularity=Math.round(v);requestAlternate();
            });
            slider(host,"Curve roundness",prismaticRoundness,0,100,5,v->{
                prismaticRoundness=Math.round(v);requestAlternate();
            });
            slider(host,"Facial detail emphasis",prismaticFeature,0,100,5,v->{
                prismaticFeature=Math.round(v);requestAlternate();
            });
            host.addView(text(prismaticStyleNote(newStyleId)+" · independent opaque sheets · photo-matched coverage · solid Snip base",12,MUTED,false));
        }
        if(VinylStyleSuite.isConfetti(newStyleId)||newStyleId==VinylStyleSuite.CHAINMAIL){
            slider(host,VinylStyleSuite.isConfetti(newStyleId)?"Fragment irregularity":"Ring weave variation",
                trioOrganic,0,100,5,v->{trioOrganic=Math.round(v);requestAlternate();});
            host.addView(text(VinylStyleSuite.isConfetti(newStyleId)?
                "Scattered and overlapping fragments · solid L1 base · original cut-safe shapes · 2–8 vinyl layers":
                "European 4-in-1 inspired linked rings · solid L1 base · every overlay swatch populated · 2–8 vinyl layers",12,MUTED,false));
        }
        if(VinylStyleSuite.isOrganicFlow(newStyleId)){
            slider(host,newStyleId==VinylStyleSuite.QUILLING_RIBBONS?"Curl strength":
                newStyleId==VinylStyleSuite.MARBLED_INK?"Swirl strength":"Curl + swirl strength",
                newStyleWeightMm,.25f,2.5f,.05f,v->{
                    newStyleWeightMm=v;requestAlternate();
                });
            host.addView(text("Automatic 50% contour smoothing preserves photo detail while rounding cut edges. L1 Snip remains unchanged; export matches the preview.",12,MUTED,false));
            host.addView(text(getString(R.string.ui_image_guided_flowing_color_regions_protected),12,MUTED,false));
        }
        if(newStyleId==VinylStyleSuite.TESSELLATION_MOTIF)
            host.addView(text("33 tiling layouts · photographic tile colors · solid silhouette base",12,MUTED,false));
        if(newStyleId==VinylStyleSuite.GLASS)slider(host,"Lead thickness · mm",newStyleLeadMm,.6f,3f,.1f,v->{
            newStyleLeadMm=v;requestAlternate();
        });
        if(newStyleId==VinylStyleSuite.LINOCUT || newStyleId==VinylStyleSuite.BANKNOTE ||
           (newStyleId>=VinylStyleSuite.MARQUETRY&&newStyleId<=VinylStyleSuite.ARCHITECTURAL&&
            ExpandedPortraitStylesEngine.supportsWeight(newStyleId-VinylStyleSuite.MARQUETRY)))
            slider(host,"Line weight · mm",newStyleWeightMm,.5f,1.5f,.1f,v->{newStyleWeightMm=v;requestAlternate();});
        int variants=NewStyleVariantPolicy.count(newStyleId);
        if(variants>1&&newStyleId!=VinylStyleSuite.TEXT)action(host,"Variant: "+newStyleVariantName(newStyleId,newStyleVariant)+"  ›",()->{
            if(newStyleId==VinylStyleSuite.TESSELLATION_MOTIF){
                showTessellationLayoutPicker();
            }else{
                checkpoint();newStyleVariant=NewStyleVariantPolicy.next(newStyleId,newStyleVariant);
                setToolInternal(1);requestAlternate();
            }
        });
        if(newStyleId==VinylStyleSuite.TEXT&&newStyleVariant!=0)
            action(host,"Style: "+newStyleVariantName(newStyleId,newStyleVariant)+"  ›",()->{
                checkpoint();newStyleVariant=newStyleVariant==2?1:2;
                newStyleVariantMemory[VinylStyleSuite.TEXT]=newStyleVariant;
                setToolInternal(1);requestAlternate();
            });
        if(newStyleId==VinylStyleSuite.TEXT)host.addView(text(
            "L1 is the filled Snip background. Detail uses 1–7 additional opaque vinyl colors; the total remains 2–8 layers. Photo structure and color are analyzed before text placement.",12,MUTED,false));
        if(newStyleId==VinylStyleSuite.TEXT&&newStyleVariant!=0)
            action(host,"Edit words",()->{
                final EditText entry=new EditText(this);entry.setSingleLine(true);
                entry.setText(newStyleWords);entry.setMaxLines(5);
                new AlertDialog.Builder(this).setTitle(getString(R.string.ui_text_halftone_words)).setView(entry)
                    .setPositiveButton("Apply",(di,which)->{
                        checkpoint();String value=entry.getText().toString().trim();
                        newStyleWords=value.isEmpty()?"VINYL STUDIO":value;
                        requestAlternate();
                    }).setNegativeButton("Cancel",null).show();
            });
        host.addView(text("Cut dimensions and geometric safety follow the selected material. Colors and physical layer count are in Colors; no extra geometry sliders.",12,MUTED,false));
        host.addView(text(alternateStatus,12,ACCENT,false));
    }
    private void rememberNewStylePalette(){
        if(newStyleId<0||newStyleId>=VinylStyleSuite.NAMES.length)return;
        newStylePaletteMemory[newStyleId]=newStyleColors.clone();
        newStyleRankMemory[newStyleId]=newStylePaletteRanks.clone();
        newStyleCountMemory[newStyleId]=newStyleColorCount;
        newStyleManualMemory[newStyleId]=newStyleMatchVinyl;
        newStyleDetailMemory[newStyleId]=newStyleDetail;
        newStyleSmoothingMemory[newStyleId]=newStyleSmoothing;
        newStyleVariantMemory[newStyleId]=newStyleVariant;
        newStyleWeightMemory[newStyleId]=newStyleWeightMm;
        newStyleLeadMemory[newStyleId]=newStyleLeadMm;
        if(newStyleId>=VinylStyleSuite.CONFETTI&&newStyleId<=VinylStyleSuite.CONFETTI_FALLEN){
            int n=newStyleId-VinylStyleSuite.CONFETTI;
            trioSpacingMemory[n]=trioSpacingMm;
            trioOrganicMemory[n]=trioOrganic;
        }
        newStyleSettingsSaved[newStyleId]=true;
    }
    /** Copy currently displayed auto-quantized colors into their physical slots before editing. */
    private void newStyleCaptureRenderedColors(){
        if(newStyleResult==null||alternateReadyGeneration!=alternateGeneration)return;
        for(int i=0;i<newStyleResult.layers.size();i++){
            StyleKit.Layer layer=newStyleResult.layers.get(i);
            int slot=NewStyleColorSlots.slot(newStyleId,layer.name,i);
            if(slot>=0&&slot<newStyleColors.length)newStyleColors[slot]=layer.color;
        }
    }
    /** Freeze exactly the rendered photo colors before any manual Hue/Reverse edit.
     * If rendering is pending, fail visibly instead of substituting an old preset. */
    private boolean freezeRenderedNewStylePalette(){
        if(newStyleMatchVinyl)return true;
        if(newStyleResult==null||alternateReadyGeneration!=alternateGeneration||alternatePreviewDraft){
            toast(getString(R.string.ui_finish_rendering_photo_colors_before_editing));
            return false;
        }
        newStyleCaptureRenderedColors();
        newStylePaletteRanks=SmartPaletteReduction.identity(9);
        newStyleHueBaseColors=newStyleColors.clone();
        newStyleHueDegrees=0;
        newStyleMatchVinyl=true;
        newStylePhotoAutoMemory[newStyleId]=false;
        selectedPaletteKey="";
        rememberNewStylePalette();
        return true;
    }
    private int newStyleDisplayedColor(int slot){
        if(!newStyleMatchVinyl&&newStyleResult!=null&&alternateReadyGeneration==alternateGeneration){
            for(int i=0;i<newStyleResult.layers.size();i++){
                StyleKit.Layer layer=newStyleResult.layers.get(i);
                if(NewStyleColorSlots.slot(newStyleId,layer.name,i)==slot)return layer.color;
            }
        }
        return newStyleColors[slot];
    }
    private void editNewStyleColor(int slot){
        if(slot<0||slot>=NewStyleColorSlots.slotCount(newStyleId,newStyleVariant,newStyleColorCount))return;
        int original=newStyleDisplayedColor(slot);
        VinylColorEditor editor=new VinylColorEditor(this,original);
        new AlertDialog.Builder(this).setTitle("Vinyl color L"+(slot+1))
            .setView(scrollColorEditor(editor)).setNegativeButton("Cancel",null)
            .setPositiveButton("Apply",(dialog,which)->{
                final int chosenColor;
                try{chosenColor=editor.getColorOrThrow();}
                catch(IllegalArgumentException ex){toast(getString(R.string.ui_could_not_apply_the_selected_vinyl));return;}
                checkpoint();
                if(!newStyleMatchVinyl){
                    if(!freezeRenderedNewStylePalette())return;
                }
                newStyleColors[slot]=chosenColor;
                newStyleHueBaseColors[slot]=PaletteHueShift.rotateOne(chosenColor,-newStyleHueDegrees);
                newStyleMatchVinyl=true;
                newStylePhotoAutoMemory[newStyleId]=false;
                selectedPaletteKey=""; // A manual swatch is no longer the unmodified selected preset.
                rememberNewStylePalette();newStyleSelectedLayer=-1;
                setToolInternal(activeWorkspace==4?3:2);requestAlternate();
            }).show();
    }
    /** Compact real swatches; auto mode shows the actual colors, not an unrelated palette. */
    private void newStyleSwatchRow(LinearLayout parent,int slot,boolean selected,boolean populated){
        final int color=newStyleDisplayedColor(slot);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(9),dp(7),dp(9),dp(7));
        row.setBackground(outlined(SURFACE,selected?ACCENT:0xff536075,12));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(7);parent.addView(row,rp);
        TextView swatch=text("L"+(slot+1),13,contrastText(color),true);
        swatch.setGravity(Gravity.CENTER);swatch.setBackground(outlined(color,0xff78889d,10));
        row.addView(swatch,new LinearLayout.LayoutParams(dp(66),dp(48)));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-2,1);ip.leftMargin=dp(10);row.addView(info,ip);
        info.addView(text(colorHex(color)+(newStyleId==VinylStyleSuite.GLASS&&slot==0?" · Lead":""),13,TEXT,true));
        info.addView(text(!specialtyFinalReady()?"Cuts pending render":populated?"Cut geometry ready":"No cuts on this color",11,MUTED,false));
        action(row,"Edit",()->editNewStyleColor(slot));
    }
    /** One palette picker for all multicolor art engines. The same physical
     * swatch slots feed preview, layer cards and SVG export. */
    private void applyNewStylePalette(int[] physicalColors,String name){
        if(!newStyleActive()||!NewStyleColorSlots.multi(newStyleId))return;
        if(physicalColors==null||(physicalColors.length!=8 && physicalColors.length!=9))
            throw new IllegalArgumentException("Palette needs eight or nine physical swatches");
        checkpoint();
        newStyleColors=expandNewStyleSwatches(physicalColors);newStyleHueBaseColors=newStyleColors.clone();newStyleHueDegrees=0;
        newStylePaletteRanks=SmartPaletteReduction.identity(9);
        newStyleMatchVinyl=true;
        newStylePhotoAutoMemory[newStyleId]=false;
        newStyleSelectedLayer=-1;
        rememberNewStylePalette();
        setToolInternal(2);
        requestAlternate();
        toast("Applied “"+name+"”");
    }
    private void newStylePalettePicker(){
        if(!newStyleActive()||!NewStyleColorSlots.multi(newStyleId))return;
        final int style=newStyleId;
        final int count=NewStyleColorSlots.slotCount(style,newStyleVariant,newStyleColorCount);
        final android.app.Dialog dialog=new android.app.Dialog(this);
        LinearLayout root=styleDialogRoot("Vinyl palettes",dialog,false);
        ScrollView scroll=new ScrollView(this);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12),dp(8),dp(12),dp(16));scroll.addView(list);
        action(list,"Save current colors…",()->{
            dialog.dismiss();promptSaveNamedPalette();
        });
        section(list,"Built-in palettes · "+(VinylPalettes.NAMES.length+ColorWorkspacePalette.TESS_NAMES.length));
        if(CamouflagePalette.isCamouflage(style)){
            section(list,"Camouflage family preset");
            LinearLayout row=horizontal(list);
            final int[] family=CamouflagePalette.defaults(style,newStyleColors);
            newStylePalettePickerTile(row,CamouflagePalette.name(style)+" camouflage",family,count,()->{
                selectedPaletteKey="camo:"+style;
                dialog.dismiss();applyNewStylePalette(family,CamouflagePalette.name(style)+" camouflage");
            });
        }
        section(list,"Standard palettes");
        for(int first=0;first<VinylPalettes.NAMES.length;first+=2){
            LinearLayout row=horizontal(list);
            for(int j=first;j<Math.min(first+2,VinylPalettes.NAMES.length);j++){
                final int index=j;
                int[] colors;
                final String key="preset:"+index;
                colors=newStylePresetForKey(key,VinylPalettes.get(index,8));
                final int[] physical=colors;
                newStylePalettePickerTile(row,VinylPalettes.NAMES[j],physical,count,()->{
                    selectedPaletteKey=key;
                    dialog.dismiss();applyNewStylePalette(physical,VinylPalettes.NAMES[index]);
                });
            }
        }
        section(list,"Creative palettes");
        for(int first=0;first<ColorWorkspacePalette.TESS_NAMES.length;first+=2){
            LinearLayout row=horizontal(list);
            for(int j=first;j<Math.min(first+2,ColorWorkspacePalette.TESS_NAMES.length);j++){
                final int index=j;
                int[] colors;
                final String key="creative:"+index;
                colors=newStylePresetForKey(key,ColorWorkspacePalette.tessPalette(index));
                final int[] physical=colors;
                newStylePalettePickerTile(row,ColorWorkspacePalette.TESS_NAMES[j],physical,count,()->{
                    selectedPaletteKey=key;
                    dialog.dismiss();applyNewStylePalette(physical,ColorWorkspacePalette.TESS_NAMES[index]);
                });
            }
        }
        // Reuse the existing, persistent palette library instead of making
        // a second storage format for the new engines.
        java.util.ArrayList<SavedVinylPaletteLibrary.Entry> saved=readSavedPalettes();
        if(!saved.isEmpty()){
            section(list,"My saved vinyl palettes · "+saved.size());
            for(int first=0;first<saved.size();first+=2){
                LinearLayout row=horizontal(list);
                for(int j=first;j<Math.min(first+2,saved.size());j++){
                    final SavedVinylPaletteLibrary.Entry entry=saved.get(j);
                    final String key="saved:"+entry.id;
                    final int[] physical=savedNewStyleColors(entry,reversedPalette(key));
                    newStylePalettePickerTile(row,entry.name,physical,count,()->{
                        selectedPaletteKey=key;
                        dialog.dismiss();applyNewStylePalette(physical,entry.name);
                    });
                }
            }
        }
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        openStyleDialog(dialog,root,false);
    }
    private void newStylePalettePickerTile(LinearLayout row,String name,int[] colors,int active,Runnable choose){
        LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(7),dp(8),dp(7),dp(8));
        tile.setBackground(outlined(SURFACE,0xff536075,12));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(82),1);
        lp.rightMargin=dp(6);lp.bottomMargin=dp(7);row.addView(tile,lp);
        paletteStrip(tile,colors,active);
        TextView title=text(name,12,TEXT,true);title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);tile.addView(title,new LinearLayout.LayoutParams(-1,dp(34)));
        tile.setContentDescription("Apply vinyl palette "+name);
        tile.setOnClickListener(v->choose.run());
    }
    /** One compact, accessible surface chooser across every Colors branch.
     *  This changes only paperArgb, never the physical swatch order. */
    private void mountingSurfaceChoices(LinearLayout parent){
        LinearLayout row=horizontal(parent);
        final int[] tones={0xfffaf9f6,0xfff4e7ce,0xff18191e,settings.paper};
        final String[] names={"White","Cream","Black","Custom"};
        for(int i=0;i<tones.length;i++){
            final int index=i;
            boolean selected=i==3 ? (settings.paper!=tones[0] && settings.paper!=tones[1] &&
                    settings.paper!=tones[2]) : settings.paper==tones[i];
            LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);tile.setPadding(dp(4),dp(6),dp(4),dp(4));
            tile.setMinimumHeight(dp(64));
            tile.setBackground(outlined(selected?StudioTheme.ACCENT_SOFT:StudioTheme.SURFACE,
                selected?StudioTheme.ACCENT:StudioTheme.OUTLINE,StudioTheme.R_CHIP));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(74),1);
            if(i>0)lp.leftMargin=dp(8);row.addView(tile,lp);
            View color=new View(this);color.setBackground(outlined(tones[i],StudioTheme.OUTLINE_STRONG,8));
            tile.addView(color,new LinearLayout.LayoutParams(-1,dp(25)));
            TextView label=text(names[i]+(selected?" ✓":""),12,selected?StudioTheme.ACCENT_INK:TEXT,true);
            label.setGravity(Gravity.CENTER);label.setSingleLine(true);tile.addView(label);
            tile.setContentDescription(names[i]+" mounting surface"+(selected?", selected":""));
            tile.setOnClickListener(v->{if(index==3)editPaper();else selectMountingSurface(tones[index]);});
        }
    }
    private void newStyleColorsPanel(){
        ensurePreparedPhotoPalette();
        // First control on Colors for every editable new-style palette.
        vinylPaletteHueControl(group());
        LinearLayout paper=group();section(paper,"Mounting surface");
        mountingSurfaceChoices(paper);
        LinearLayout g=group();section(g,"Active vinyl colors · "+VinylStyleSuite.NAMES[newStyleId]);
        final boolean multi=NewStyleColorSlots.multi(newStyleId);
        final int slots=NewStyleColorSlots.slotCount(newStyleId,newStyleVariant,newStyleColorCount);
        if(multi){
            // Previously missing for Low-poly, Stained Glass and Topographic.
            // Applying a preset stores its actual 8 physical swatches and
            // routes them through the existing fixed-vinyl rendering adapter.
            action(g,newStyleMatchVinyl?"Use automatic photo colors":"Keep these vinyl colors",()->{
                checkpoint();
                if(!newStyleMatchVinyl){
                    if(!freezeRenderedNewStylePalette())return;
                    // freeze makes manual=true; stay in manual mode on this action.
                }else{
                    newStyleMatchVinyl=false;
                }
                newStylePhotoAutoMemory[newStyleId]=!newStyleMatchVinyl;
                selectedPaletteKey="";
                rememberNewStylePalette();setToolInternal(2);requestAlternate();
            });
            g.addView(text(getString(R.string.ui_layer_count_and_physical_stack_order),12,MUTED,false));
        }
        if(newStyleId==VinylStyleSuite.POSTER_STENCIL){
            action(g,"Poster vinyl palettes  ›",()->
                new AlertDialog.Builder(this).setTitle(getString(R.string.ui_nine_color_poster_vinyl_palette))
                    .setItems(PopPosterEngine.POSTER_NAMES,(d,which)->{
                        posterOptions.posterPalette=which;
                        applyNewStylePalette(PopPosterEngine.posterPalette(which,9),
                            PopPosterEngine.POSTER_NAMES[which]+" poster");
                    }).show());
        }
        if(CamouflagePalette.isCamouflage(newStyleId)){
            final int camoStyle=newStyleId;
            action(g,"Restore "+CamouflagePalette.name(camoStyle)+" camouflage colors",()->{
                final int[] family=CamouflagePalette.defaults(camoStyle,newStyleColors);
                selectedPaletteKey="camo:"+camoStyle;
                applyNewStylePalette(family,CamouflagePalette.name(camoStyle)+" camouflage");
            });
            g.addView(text(getString(R.string.ui_camouflage_starts_with_its_own_nine),12,MUTED,false));
        }
        g.addView(text(multi?"Tap any swatch to choose its physical vinyl color. An edit fixes the palette; automatic photo colors remain available.":
            "One cut color per physical layer. Tap a swatch to change the actual exported color.",12,MUTED,false));
        for(int slot=0;slot<slots;slot++){
            boolean used=false;
            if(newStyleResult!=null&&alternateReadyGeneration==alternateGeneration)
                for(int i=0;i<newStyleResult.layers.size();i++){
                    StyleKit.Layer layer=newStyleResult.layers.get(i);
                    if(NewStyleColorSlots.slot(newStyleId,layer.name,i)==slot&&
                        (!layer.loops.isEmpty()||!layer.lines.isEmpty())){used=true;break;}
                }
            newStyleSwatchRow(g,slot,false,used);
        }
        if(newStyleId==VinylStyleSuite.GLASS)
            g.addView(text("L1 is the lead underlay; glass colors are L2–L"+slots+". Lead counts toward the eight-layer maximum.",12,MUTED,false));
        if(newStyleId==VinylStyleSuite.TEXT)
            g.addView(text("L1 is the filled Snip background; L2–L"+slots+" are text detail. Every color is a separate vinyl sheet; maximum eight including the background.",12,MUTED,false));
        if(multi){
            inlinePaletteGallery(group());
            savedPaletteLibraryPanel();
        }
    }
    private void chooseNewStyleCount(int n){
        cancelPaletteReduction();if(n==newStyleColorCount)return;
        java.util.function.Consumer<int[]> commit=p->{checkpoint();if(p!=null)applyNewStyleReduction(p);
            newStyleColorCount=n;newStyleSelectedLayer=-1;rememberNewStylePalette();requestAlternate();setToolInternal(3);};
        if((!newStyleMatchVinyl&&newStyleId!=VinylStyleSuite.TESSELLATION_MOTIF)||!NewStyleColorSlots.multi(newStyleId)){commit.accept(null);return;}
        int capacity=NewStyleColorSlots.maximumSelectable(newStyleId);
        if(n<NewStyleColorSlots.minimumSelectable(newStyleId)||n>capacity)return;
        queuePaletteReduction(Arrays.copyOf(newStyleColors,capacity),Arrays.copyOf(newStylePaletteRanks,capacity),n,
            SmartPaletteReduction.policyForStyle(newStyleId,newStyleVariant),0,(1<<capacity)-1,commit);
    }
    private void chooseTessCount(int n){
        cancelPaletteReduction();if(n==tessLayerCount)return;
        boolean structural=layeredCellMode&&(offsetCellMode||layeredSolidBase||overprintCellKind>0);
        int required=layeredCellMode&&layeredBackground>=0&&layeredBackground<8?1<<layeredBackground:0;
        int eligible=255;if(stippleActive()){eligible=0;for(int i=0;i<8;i++)if(stippleSettings.enabled[i])eligible|=1<<i;}
        queuePaletteReduction(tessColors,tessPaletteRanks,n,structural?SmartPaletteReduction.STRUCTURAL:SmartPaletteReduction.PORTRAIT,
            required,eligible,p->{checkpoint();applyTessReduction(p);tessLayerCount=n;tessSelectedLayer=-1;
                if(stippleActive())stippleOrderNormalize();if(layeredBackground>=n)layeredBackground=-1;
                requestAlternate();setToolInternal(3);});
    }
    private void chooseTonalCount(int n){
        cancelPaletteReduction();if(n==tonalLayerCount)return;
        java.util.function.Consumer<int[]> commit=p->{checkpoint();if(p!=null)applyTonalReduction(p);tonalLayerCount=n;tonalSelectedLayer=-1;
            if(tonalMatchMyVinyl)for(int c=0;c<n;c++)if(tonalColorOverrides[c]==0){tonalMatchMyVinyl=false;break;}
            requestAlternate();setToolInternal(3);};
        for(int color:tonalColorOverrides)if(color==0){commit.accept(null);return;}
        int required=tonalBaseSource>=0&&tonalBaseSource<8?1<<tonalBaseSource:0;
        queuePaletteReduction(PaletteHueShift.rotate(tonalColorOverrides,tonalHueDegrees),tonalPaletteRanks,n,
            SmartPaletteReduction.TONAL,required,255,commit);
    }
    private void newStyleLayersPanel(){
        LinearLayout g=group();
        if(newStyleId<0||newStyleId>=VinylStyleSuite.NAMES.length){
            g.addView(text(getString(R.string.ui_this_artwork_style_could_not_be),12,MUTED,false));return;
        }
        section(g,"Physical layers · "+VinylStyleSuite.NAMES[newStyleId]);
        if(underlayRequested()){
            layerCountPicker(g,newStyleColorCount,n->{
                chooseNewStyleCount(n);
            });
            showAutomaticSilhouette(g);
        }
        if(underlayRequested())g.addView(text(silhouetteOnly()?"One physical sheet: L1 filled Snip only":
            newStyleColorCount+" detail colors + 1 solid Snip = "+(newStyleColorCount+1)+" physical sheets",12,ACCENT,true));
        if(silhouetteOnly())return;
        final int physicalSlots=NewStyleColorSlots.slotCount(newStyleId,newStyleVariant,newStyleColorCount);
        // Same physical-layer overview as ordinary, Cell and Tessellation styles.
        // Swatches always represent exported L1..LN colors, including L1 bases.
        int[] layerColors=new int[physicalSlots];
        String[] layerNames=new String[physicalSlots];
        boolean[] layerPopulated=new boolean[physicalSlots];
        for(int i=0;i<physicalSlots;i++){
            layerColors[i]=newStyleDisplayedColor(i);
            layerNames[i]="L"+(i+(underlayRequested()?2:1));
            layerPopulated[i]=newStyleOperations==null||i>=newStyleOperations.length||
                newStyleOperations[i].bladeContours>0||newStyleOperations[i].penPaths>0;
        }
        colorSwatchGrid(g,layerColors,layerNames,layerPopulated,newStyleSelectedLayer,index->{
            previewBaseOnly=false;
            newStyleSelectedLayer=newStyleSelectedLayer==index?-1:index;
            setToolInternal(3);
            if(artwork!=null)artwork.invalidate();
        });
        if(NewStyleColorSlots.multi(newStyleId)&&!underlayRequested()&&
           NewStyleColorSlots.maximumSelectable(newStyleId)>NewStyleColorSlots.minimumSelectable(newStyleId))
            layerCountPicker(g,newStyleColorCount,n->{
                chooseNewStyleCount(n);
            });
        if(VinylStyleSuite.isConfetti(newStyleId)||VinylStyleSuite.isCraft(newStyleId)||
           VinylStyleSuite.isOrganicFlow(newStyleId)||newStyleId==VinylStyleSuite.POSTER_STENCIL)
            g.addView(text("L1 is the solid Snip silhouette. It remains on every export; L2–L"+
                physicalSlots+(VinylStyleSuite.isCraft(newStyleId)?" contain separately tone-balanced cut motifs.":
                VinylStyleSuite.isOrganicFlow(newStyleId)?" contain photo-guided ribbons and marbled color regions.":
                newStyleId==VinylStyleSuite.POSTER_STENCIL?" are independently editable poster-tone plates.":
                " contain the overlapping confetti pieces."),12,ACCENT,true));
        if(newStyleSelectedLayer>=0||previewBaseOnly)action(g,"Show complete artwork",()->{
            previewBaseOnly=false;newStyleSelectedLayer=-1;setToolInternal(3);artwork.invalidate();
        });
        StyleLayerOperations.Counts[] operations=newStyleOperations;
        if(!specialtyFinalReady()||newStyleResult==null||operations==null||
            operations.length!=NewStyleColorSlots.slotCount(newStyleId,newStyleVariant,newStyleColorCount)){
            g.addView(text(getString(R.string.ui_cut_counts_pending_final_render),12,MUTED,false));return;
        }
        g.addView(text((StyleLayerOperations.totalCuts(operations)+
            (currentUnderlay()==null?0:currentUnderlay().cutContours()))+" blade contours · "+
            StyleLayerOperations.totalPenPaths(operations)+" pen/engrave paths · registration marks excluded",12,ACCENT,true));
        for(int slot=0;slot<operations.length;slot++){
            final int index=slot,color=newStyleDisplayedColor(slot);
            StyleLayerOperations.Counts ops=operations[slot];
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16),dp(12),dp(16),dp(12));
            card.setBackground(outlined(StudioTheme.SURFACE,
                newStyleSelectedLayer==slot?StudioTheme.ACCENT:StudioTheme.OUTLINE,StudioTheme.R_CARD));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
            cp.bottomMargin=dp(8);g.addView(card,cp);
            LinearLayout summary=horizontal(card);
            TextView swatch=text("L"+(slot+(underlayRequested()?2:1)),13,contrastText(color),true);
            swatch.setGravity(Gravity.CENTER);swatch.setBackground(outlined(color,StudioTheme.OUTLINE_STRONG,10));
            summary.addView(swatch,new LinearLayout.LayoutParams(dp(52),dp(52)));
            LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-2,1);
            ip.leftMargin=dp(12);summary.addView(info,ip);
            // Actions go below the summary rather than squeezing three buttons
            // beside the title on 360dp phones or with large font scaling.
            LinearLayout cardActions=horizontal(card);
            info.addView(text("L"+(slot+(underlayRequested()?2:1))+(newStyleId==VinylStyleSuite.GLASS&&slot==0?" · lead underlay":
                newStyleId==VinylStyleSuite.TEXT?(slot==0?" · Snipped background":" · Text detail"):
                (VinylStyleSuite.isConfetti(newStyleId)||newStyleId==VinylStyleSuite.CHAINMAIL||VinylStyleSuite.isCraft(newStyleId)||newStyleId==VinylStyleSuite.POSTER_STENCIL)&&slot==0?" · Solid base":"")+
                " · "+colorHex(color),12,TEXT,true));
            info.addView(text(String.format(Locale.US,"%,d blade cuts",ops.bladeContours),12,ACCENT,true));
            if(ops.penPaths>0)info.addView(text(String.format(Locale.US,"%,d pen/engrave paths",ops.penPaths),12,MUTED,false));
            if(ops.bladeContours==0&&ops.penPaths==0)info.addView(text(getString(R.string.ui_no_artwork_on_this_color),11,MUTED,false));
            action(cardActions,newStyleSelectedLayer==slot?"All":"View",()->{
                previewBaseOnly=false;
                newStyleSelectedLayer=newStyleSelectedLayer==index?-1:index;
                setToolInternal(3);artwork.invalidate();
            });
            action(cardActions,"Edit",()->editNewStyleColor(index));
            if(NewStyleColorSlots.multi(newStyleId)&&operations.length>NewStyleColorSlots.minimumSelectable(newStyleId)&&
               NewStyleColorSlots.maximumSelectable(newStyleId)>NewStyleColorSlots.minimumSelectable(newStyleId)&&
               !(NewStyleLayerPolicy.isStructuralLayer(newStyleId,index)))
                action(cardActions,"Delete",()->confirmRemoveNewStyleLayer(index));
        }
        g.addView(text(getString(R.string.ui_every_closed_outer_boundary_and_hole),12,MUTED,false));
    }
    /** Selected style is not `chosen`, which identifies a physical layer. */
    private static boolean isLayeredSpecialtyId(int id){
        return id==10||id==19||id==20||id==21||(id>=22&&id<=25);
    }
    private static int overprintKindForId(int id){
        return id>=22&&id<=25?id-21:LayeredCellPortraitEngine.OVERPRINT_NONE;
    }
    private boolean selectedMultilayerSpecialty(int id){
        if(newStyleActive())return false;
        switch(id){
            case 6:return tonalStencilMode;
            case 8:return tessellationMode&&tessellationStyle==TessellationEngine.VORONOI;
            case 9:return tessellationMode&&tessellationStyle==TessellationEngine.GRID_MOTIF;
            case 10:return layeredCellMode&&!offsetCellMode&&!bottleCapMode&&!fishScaleMode&&overprintCellKind==0;
            case 19:return layeredCellMode&&offsetCellMode;
            case 20:return layeredCellMode&&bottleCapMode;
            case 21:return layeredCellMode&&fishScaleMode;
            case 22:case 23:case 24:case 25:
                return layeredCellMode&&overprintCellKind==overprintKindForId(id);
            case 18:return stippleActive();
            default:return false;
        }
    }
    /** Style identity is separate from the display name so saved style IDs never change. */
    private static final class PickerChoice {
        final String category,name,description,previewKind;
        final boolean selected;
        final Runnable activate;
        PickerChoice(String category,String name,String description,String previewKind,boolean selected,Runnable activate){
            this.category=category;this.name=name;this.description=description;this.previewKind=previewKind;
            this.selected=selected;this.activate=activate;
        }
        String searchKey(){return category+" "+StylePickerFamilies.family(previewKind)+" "+name+" "+description+" "+previewKind+" "+StyleDiscovery.aliases(previewKind);}
    }
    private void addPickerChoice(ArrayList<PickerChoice> out,String group,String label,String summary,
                                 String thumbnail,boolean selected,Runnable activate){
        out.add(new PickerChoice(group,label,summary,thumbnail,selected,activate));
    }
    private ArrayList<PickerChoice> collectStylePickerChoices(android.app.Dialog dialog){
        ArrayList<PickerChoice> out=new ArrayList<>();
        final String multi="Multilayer", tess="Tessellation", shapes="Geometric styles",
            lines="Lines", single="Single Line", arts="Art techniques";
        final String[] names={"Tonal Stencil","Voronoi Mosaic","Grid Mosaic","Layered Cells",
            "Offset Cells","Bottle Caps","Fish Scales","Weighted Stipple",
            "Overprint Orbits","Overprint Lozenge","Overprint Diamonds","Overprint Mixed Marks"};
        final String[] desc={"Stacked vinyl layers","Adaptive polygons","Rectangular photo tiles","Nested motifs",
            "Staggered cells","Circular cap mosaic","Scalloped scales","Variable dots",
            "Overlapping oval color cells","Crossed capsule color cells","Rotated rounded diamonds","Photo-adaptive mixed marks"};
        final String[] art={"multilayer.tonal","multilayer.voronoi","multilayer.grid",
            "multilayer.layered_cells","multilayer.offset_cells","multilayer.bottle_caps",
            "multilayer.fish_scales","multilayer.weighted_stipple",
            "multilayer.overprint_orbits","multilayer.overprint_lozenge",
            "multilayer.overprint_diamonds","multilayer.overprint_mixed"};
        final int[] ids={6,8,9,10,19,20,21,18,22,23,24,25};
        for(int i=0;i<ids.length;i++){
            final int id=ids[i];
            addPickerChoice(out,multi,names[i],desc[i],art[i],selectedMultilayerSpecialty(id),()->{
                dialog.dismiss();activateSpecialtyStyle(id);
            });
        }
        for(int id:new int[]{VinylStyleSuite.LOW_POLY,VinylStyleSuite.GLASS,
            VinylStyleSuite.TOPOGRAPHIC}){
            final int style=id;
            String shortName=id==VinylStyleSuite.LOW_POLY?"Low Poly":
                id==VinylStyleSuite.GLASS?"Stained Glass":"Topographic";
            String tip=id==VinylStyleSuite.LOW_POLY?"Triangle facets":
                id==VinylStyleSuite.GLASS?"Leaded color mosaic":"Contour layers";
            addPickerChoice(out,multi,shortName,tip,StudioThumbnailRegistry.newStyle(id),
                newStyleActive()&&newStyleId==style,()->{dialog.dismiss();activateNewStyle(style);});
        }
        // Only supported tilings are shown; archived variant IDs remain reserved.
        // They share the original stable style ID 10; only their saved variant ID differs.
        for(int i=0;i<TessellationMotifEngine.PATTERN_SLOT_COUNT;i++){
            if(!TessellationMotifEngine.selectablePattern(i))continue;
            final int pattern=i;
            addPickerChoice(out,tess,TessellationMotifEngine.PATTERNS[i],"Geometric tiling",
                StudioThumbnailRegistry.tessellationKey(i),
                newStyleActive()&&newStyleId==VinylStyleSuite.TESSELLATION_MOTIF&&newStyleVariant==i,
                ()->{dialog.dismiss();selectTessellationLayout(pattern);});
        }
        for(int id:VinylEngine.SHAPE_STYLES){
            final int style=id;
            String original=VinylEngine.PATTERNS[id];
            String label=id==VinylEngine.ROUND?"Round Dots":
                id==VinylEngine.SQUARE?"Squares":
                id==VinylEngine.ASCII?"ASCII Screen":
                id==VinylEngine.ROUNDED_SQUARE?"Rounded Squares":original;
            addPickerChoice(out,shapes,label,"Photographic cut motifs",StudioThumbnailRegistry.shape(id),
                settings.pattern==style&&!alternateActive(),()->{
                    dialog.dismiss();alternateGeneration++;
                    applyHalftoneStyle(style);activeWorkspace=2;activeSubtoolPosition=0;
                    setToolInternal(1);expandToolSheet();
                });
        }
        for(int id:new int[]{VinylEngine.LINE,VinylEngine.STRAIGHT,VinylEngine.CONTOUR,
                VinylEngine.WAVE,VinylEngine.HATCH,VinylEngine.CROSSHATCH,VinylEngine.ZIGZAG,VinylEngine.BROKEN}){
            final int style=id;
            String label=id==VinylEngine.LINE?"Variable Width":
                id==VinylEngine.HATCH?"Engraving Hatches":
                id==VinylEngine.BROKEN?"Broken Lines":VinylEngine.PATTERNS[id];
            addPickerChoice(out,lines,label,"Vector ribbon screen",StudioThumbnailRegistry.line(id),
                settings.pattern==style&&!alternateActive(),()->{
                    dialog.dismiss();alternateGeneration++;
                    applyHalftoneStyle(style);activeWorkspace=2;activeSubtoolPosition=0;
                    setToolInternal(1);expandToolSheet();
                });
        }
        for(int index=0;index<PRIMARY_SINGLE_LINES.length;index++){
            final ContinuousLineModes.LineStyle line=PRIMARY_SINGLE_LINES[index];
            String label=line==ContinuousLineModes.LineStyle.ADAPTIVE_HILBERT?
                "Hilbert":SINGLE_LINE_NAMES[index];
            addPickerChoice(out,single,label,"One continuous path",StudioThumbnailRegistry.single(line),continuousStyle==line,()->{
                dialog.dismiss();
                if(ContinuousLineModes.curveFamily(line)>=0)selectSpaceCurve(line);
                else activateSpecialtyStyle(line==ContinuousLineModes.LineStyle.SPIRAL?0:4);
            });
        }
        for(int id:new int[]{VinylStyleSuite.TSP,VinylStyleSuite.LINOCUT,VinylStyleSuite.TRUCHET,
                VinylStyleSuite.BANKNOTE}){
            final int style=id;
            String shortName=id==VinylStyleSuite.TSP?"TSP Drawing":
                id==VinylStyleSuite.LINOCUT?"Linocut":
                id==VinylStyleSuite.TRUCHET?"Truchet Tiles":"Banknote Engraving";
            String artDescription=id==VinylStyleSuite.LINOCUT?"Carved line art":
                id==VinylStyleSuite.BANKNOTE?"Engraved hatching":
                id==VinylStyleSuite.TRUCHET?"Quarter-turn tiles":
                id==VinylStyleSuite.TSP?"Traveling-salesman art":"Flowing terrain lines";
            addPickerChoice(out,arts,shortName,artDescription,StudioThumbnailRegistry.newStyle(id),newStyleActive()&&newStyleId==style,()->{
                dialog.dismiss();activateNewStyle(style);
            });
        }
        // RC142: preserve stable IDs but omit retired RC141 experiments from every picker tab.
        for(int local=0;local<ExpandedPortraitStylesEngine.NAMES.length;local++){
            if(!ExpandedPortraitStylesEngine.isActive(local))continue;
            final int style=VinylStyleSuite.MARQUETRY+local;
            addPickerChoice(out,ExpandedPortraitStylesEngine.CATEGORIES[local],
                ExpandedPortraitStylesEngine.NAMES[local],ExpandedPortraitStylesEngine.DESCRIPTIONS[local],
                ExpandedPortraitStylesEngine.THUMBNAIL_KEYS[local],
                newStyleActive()&&newStyleId==style,()->{dialog.dismiss();activateNewStyle(style);});
        }
        // Six independent, saved Confetti styles share one cut-safe scatter engine.
        for(int id:new int[]{VinylStyleSuite.CONFETTI_ROUND,VinylStyleSuite.CONFETTI_SQUARE,
                VinylStyleSuite.CONFETTI_PAPER,VinylStyleSuite.CONFETTI_STREAMER,
                VinylStyleSuite.CONFETTI,VinylStyleSuite.CONFETTI_FALLEN}){
            final int style=id;
            String summary=id==VinylStyleSuite.CONFETTI_ROUND?"Round scattered paper discs":
                id==VinylStyleSuite.CONFETTI_SQUARE?"Rotated square confetti pieces":
                id==VinylStyleSuite.CONFETTI_PAPER?"Torn irregular paper fragments":
                id==VinylStyleSuite.CONFETTI_STREAMER?"Short twisted paper strips":
                id==VinylStyleSuite.CONFETTI_FALLEN?"Locally accumulated piles of mixed pieces":
                "Multiple shapes scattered together over a solid silhouette";
            addPickerChoice(out,multi,VinylStyleSuite.NAMES[id],summary,
                StudioThumbnailRegistry.newStyle(id),newStyleActive()&&newStyleId==style,
                ()->{dialog.dismiss();activateNewStyle(style);});
        }
        addPickerChoice(out,multi,VinylStyleSuite.NAMES[VinylStyleSuite.CHAINMAIL],
            "Four-neighbor staggered ring weave with every swatch populated",
            StudioThumbnailRegistry.newStyle(VinylStyleSuite.CHAINMAIL),
            newStyleActive()&&newStyleId==VinylStyleSuite.CHAINMAIL,
            ()->{dialog.dismiss();activateNewStyle(VinylStyleSuite.CHAINMAIL);});
        // RC164: four independent U.S.-inspired camo geometry/palette families.
        for(int id:new int[]{VinylStyleSuite.CAMOUFLAGE,VinylStyleSuite.CAMO_DESERT,
                VinylStyleSuite.CAMO_URBAN,VinylStyleSuite.CAMO_DIGITAL,
                VinylStyleSuite.FISHNET,VinylStyleSuite.CROCHET}){
            final int style=id;
            String note=id==VinylStyleSuite.CAMOUFLAGE?"Organic woodland breakup with broad, lobed regions":
                id==VinylStyleSuite.CAMO_DESERT?"Sand-colored horizontal brush patches":
                id==VinylStyleSuite.CAMO_URBAN?"Angular urban gray patches":
                id==VinylStyleSuite.CAMO_DIGITAL?"Stair-stepped clustered digital blocks":
                id==VinylStyleSuite.FISHNET?"Tensioned diamond net · wrapped knots · face-aware cord weight":
                "Filet crochet · chain rows · open and filled stitch cells";
            addPickerChoice(out,multi,VinylStyleSuite.NAMES[id],note,
                StudioThumbnailRegistry.newStyle(id),newStyleActive()&&newStyleId==style,
                ()->{dialog.dismiss();activateNewStyle(style);});
        }
        for(int id:new int[]{VinylStyleSuite.QUILLING_RIBBONS,
                VinylStyleSuite.MARBLED_INK,VinylStyleSuite.QUILLED_MARBLING}){
            final int style=id;
            final String tip=id==VinylStyleSuite.QUILLING_RIBBONS?
                "Photo-guided paper-quill curls and ribbon contours":
                id==VinylStyleSuite.MARBLED_INK?
                "Flowing, swirled color pools with protected facial detail":
                "Marbled color bands filled with decorative quilling curls";
            addPickerChoice(out,arts,VinylStyleSuite.NAMES[id],tip,
                StudioThumbnailRegistry.newStyle(id),newStyleActive()&&newStyleId==style,
                ()->{dialog.dismiss();activateNewStyle(style);});
        }
        addPickerChoice(out,arts,"Poster Stencil",
            "Nine-layer poster portrait · stripes, backdrops and banner",
            StudioThumbnailRegistry.newStyle(VinylStyleSuite.POSTER_STENCIL),
            newStyleActive()&&newStyleId==VinylStyleSuite.POSTER_STENCIL,
            ()->{dialog.dismiss();activateNewStyle(VinylStyleSuite.POSTER_STENCIL);});
        addPickerChoice(out,multi,"Prismatic Diagonal Portrait",
            "Continuous serpentine ribbons · overlapping diagonal weave",
            StudioThumbnailRegistry.newStyle(VinylStyleSuite.PRISMATIC_DIAGONAL),
            newStyleActive()&&newStyleId==VinylStyleSuite.PRISMATIC_DIAGONAL,
            ()->{dialog.dismiss();activateNewStyle(VinylStyleSuite.PRISMATIC_DIAGONAL);});
        // Ribbon siblings sit next to the original in the same Multilayer group.
        for(int id=VinylStyleSuite.PRISMATIC_CONTOUR_FLOW;id<=VinylStyleSuite.PRISMATIC_CHEVRON;id++){
            final int style=id;
            addPickerChoice(out,multi,VinylStyleSuite.NAMES[id],prismaticStyleNote(id),
                StudioThumbnailRegistry.newStyle(id),newStyleActive()&&newStyleId==style,
                ()->{dialog.dismiss();activateNewStyle(style);});
        }
        return out;
    }
    static String prismaticStyleNote(int style){
        switch(style){
            case VinylStyleSuite.PRISMATIC_CONTOUR_FLOW:return "Diagonal ribbons gently steered along facial contours";
            case VinylStyleSuite.PRISMATIC_TWILL:return "Staggered twill-like ribbon rhythm";
            case VinylStyleSuite.PRISMATIC_SILK:return "Soft asymmetric waves with restrained second ribbons";
            case VinylStyleSuite.PRISMATIC_BRAIDED_FLOW:return "Paired ribbons swaying in opposite phase";
            case VinylStyleSuite.PRISMATIC_CHEVRON:return "Rounded chevron ribbons";
            default:return "Interwoven diagonal ribbons";
        }
    }
    /** Reuse the same visible Tessellation tab from the in-style layout control. */
    private void showTessellationLayoutPicker(){ showCompactStylePicker("Tessellation"); }

    private int stylePickerColumns(){
        float density=getResources().getDisplayMetrics().density;
        float widthDp=getResources().getDisplayMetrics().widthPixels/density;
        if(getResources().getConfiguration().fontScale>=1.35f && widthDp<600)return 2;
        return widthDp<345?2:widthDp>=720?4:3;
    }
    private View compactPickerTile(PickerChoice choice, List<String> favorites, Runnable refresh){
        LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(7),dp(7),dp(7),dp(5));tile.setMinimumHeight(dp(114));
        tile.setBackground(ripple(outlined(choice.selected?StudioTheme.ACCENT_SOFT:StudioTheme.SURFACE_HIGH,
            choice.selected?StudioTheme.ACCENT:StudioTheme.OUTLINE,StudioTheme.R_CARD),StudioTheme.R_CARD));
        FrameLayout thumb=new FrameLayout(this);
        thumb.setBackground(shape(StudioTheme.SURFACE,dp(9)));
        StudioStylePreview preview=new StudioStylePreview(this,choice.previewKind);
        thumb.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        tile.addView(thumb,new LinearLayout.LayoutParams(-1,dp(54)));
        if(choice.selected){
            TextView check=text("✓",14,StudioTheme.ACCENT_INK,true);
            check.setGravity(Gravity.CENTER);check.setBackground(shape(StudioTheme.ACCENT_SOFT,dp(10)));
            FrameLayout.LayoutParams checkParams=new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.TOP|Gravity.END);
            thumb.addView(check,checkParams);
        }
        TextView title=new TextView(this);title.setText(choice.name);title.setTextSize(12.5f);
        title.setTextColor(TEXT);title.setTypeface(null,android.graphics.Typeface.BOLD);
        title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        int textHeight=getResources().getConfiguration().fontScale>1.20f?dp(48):dp(35);
        LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(-1,textHeight);tl.topMargin=dp(3);tile.addView(title,tl);
        TextView subtitle=new TextView(this);subtitle.setText(choice.description);subtitle.setTextSize(10.5f);
        subtitle.setTextColor(MUTED);subtitle.setSingleLine(true);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.addView(subtitle,new LinearLayout.LayoutParams(-1,dp(17)));
        tile.setOnClickListener(v->{
            List<String> recent=readDiscovery("recent",StyleDiscovery.RECENT_LIMIT);
            StyleDiscovery.touch(recent,choice.previewKind,StyleDiscovery.RECENT_LIMIT);
            saveDiscovery("recent",recent);
            choice.activate.run();
        });
        tile.setOnLongClickListener(v->{
            boolean favorite=favorites.contains(choice.previewKind);
            new android.app.AlertDialog.Builder(this).setTitle(choice.name)
                .setMessage(choice.description+"\n\n"+choice.category)
                .setPositiveButton(favorite?"Remove favorite":"Add favorite",(d,w)->{
                    if(favorite)favorites.remove(choice.previewKind);
                    else StyleDiscovery.touch(favorites,choice.previewKind,StyleDiscovery.LIMIT);
                    saveDiscovery("favorites",favorites);refresh.run();
                }).setNegativeButton("Close",null).show();
            return true;
        });
        tile.setContentDescription(choice.name+". "+choice.description+
            (choice.selected?". Currently selected":"")+(favorites.contains(choice.previewKind)?". Favorite":"")+
            ". Tap to select. Long press for details and favorites.");
        if(favorites.contains(choice.previewKind))title.setText(getString(R.string.style_favorite_name,choice.name));
        return tile;
    }
    private void fillCompactPickerGrid(LinearLayout grid,ArrayList<PickerChoice> choices,
                                      String category,String query,List<String> favorites,List<String> recent,Runnable refresh){
        grid.removeAllViews();
        final int columns=stylePickerColumns();
        
        ArrayList<PickerChoice> matches=new ArrayList<>();
        for(PickerChoice choice:choices){
            if(category.equals("Favorites")){if(!favorites.contains(choice.previewKind))continue;}
            else if(category.equals("Recent")){if(!recent.contains(choice.previewKind))continue;}
            else if(!StylePickerFamilies.matches(category,choice.category,choice.previewKind))continue;
            if(!StyleDiscovery.matches(choice.searchKey(),query))continue;
            matches.add(choice);
        }
        if(category.equals("Recent"))matches.sort(java.util.Comparator.comparingInt(c->recent.indexOf(c.previewKind)));
        if(matches.isEmpty()){
            TextView empty=text(category.equals("Favorites")?"No matching favorites. Long press a style to add it.":category.equals("Recent")?"No matching recent styles. Select a style first.":"No styles found. Try All or a different search.",14,MUTED,false);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(12),dp(20),dp(12),dp(20));
            grid.addView(empty,new LinearLayout.LayoutParams(-1,-2));return;
        }
        for(int index=0;index<matches.size();index+=columns){
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,-2);rowLp.bottomMargin=dp(8);
            grid.addView(row,rowLp);
            for(int col=0;col<columns;col++){
                View tile=index+col<matches.size()?compactPickerTile(matches.get(index+col),favorites,refresh):new View(this);
                int tileHeight=getResources().getConfiguration().fontScale>1.20f?dp(149):dp(122);
                LinearLayout.LayoutParams itemLp=new LinearLayout.LayoutParams(0,tileHeight,1f);
                if(col>0)itemLp.leftMargin=dp(8);
                row.addView(tile,itemLp);
            }
        }
    }
    private List<String> readDiscovery(String key,int limit){
        try { return StyleDiscovery.decode(getSharedPreferences("style_discovery_v1",MODE_PRIVATE).getString(key,""),limit); }
        catch(ClassCastException invalid){ return new ArrayList<>(); }
    }
    private void saveDiscovery(String key,List<String> values){
        getSharedPreferences("style_discovery_v1",MODE_PRIVATE).edit().putString(key,String.join("\n",values)).apply();
    }
    private void showCompactStylePicker(String initialCategory){
        final android.app.Dialog dialog=new android.app.Dialog(this);
        final LinearLayout root=styleDialogRoot("Style Picker",dialog,false);
        final ArrayList<PickerChoice> choices=collectStylePickerChoices(dialog);
        final String[] currentCategory={"Styles".equals(initialCategory)?"Geometric styles":initialCategory};
        final List<String> favorites=readDiscovery("favorites",StyleDiscovery.LIMIT);
        final List<String> recent=readDiscovery("recent",StyleDiscovery.RECENT_LIMIT);
        TextView hint=text(getString(R.string.ui_tap_to_select_long_press_for),12,MUTED,false);
        root.addView(hint,new LinearLayout.LayoutParams(-1,-2));
        EditText search=new EditText(this);search.setTextSize(14);search.setTextColor(TEXT);
        search.setSingleLine(true);search.setHint(getString(R.string.ui_search_styles));search.setHintTextColor(MUTED);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(dp(16),0,dp(16),0);search.setMinimumHeight(dp(48));
        search.setBackground(outlined(StudioTheme.SURFACE_HIGH,StudioTheme.OUTLINE,StudioTheme.R_CHIP));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(48));sp.bottomMargin=dp(8);root.addView(search,sp);
        android.widget.HorizontalScrollView categories=new android.widget.HorizontalScrollView(this);
        categories.setHorizontalScrollBarEnabled(false);
        LinearLayout chips=new LinearLayout(this);chips.setOrientation(LinearLayout.HORIZONTAL);
        categories.addView(chips,new android.widget.HorizontalScrollView.LayoutParams(-2,dp(48)));
        LinearLayout.LayoutParams chipsLp=new LinearLayout.LayoutParams(-1,dp(48));chipsLp.bottomMargin=dp(8);
        root.addView(categories,chipsLp);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout grid=new LinearLayout(this);grid.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        final String[] groups=StylePickerFamilies.tabs();
        final Runnable[] repaint={null};
        repaint[0]=()->{
            chips.removeAllViews();
            for(String group:groups){
                boolean active=group.equals(currentCategory[0]);
                TextView chip=new TextView(this);chip.setText(group.equals("Art techniques")?"Art":group);
                chip.setTextSize(12);chip.setTextColor(active?StudioTheme.ACCENT_INK:TEXT);
                chip.setTypeface(null,android.graphics.Typeface.BOLD);
                chip.setGravity(Gravity.CENTER);chip.setPadding(dp(13),0,dp(13),0);
                chip.setMinHeight(dp(48));chip.setMinWidth(dp(48));chip.setSelected(active);
                chip.setBackground(ripple(outlined(active?StudioTheme.ACCENT_SOFT:StudioTheme.SURFACE_HIGH,
                    active?StudioTheme.ACCENT:StudioTheme.OUTLINE,StudioTheme.R_CHIP),StudioTheme.R_CHIP));
                LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(-2,dp(48));cl.rightMargin=dp(8);
                chips.addView(chip,cl);
                chip.setOnClickListener(v->{currentCategory[0]=group;repaint[0].run();scroll.scrollTo(0,0);});
            }
            fillCompactPickerGrid(grid,choices,currentCategory[0],search.getText().toString(),favorites,recent,repaint[0]);
            int activeIndex=0;
            for(int i=0;i<groups.length;i++)if(groups[i].equals(currentCategory[0]))activeIndex=i;
            final int focusIndex=activeIndex;
            categories.post(()->{
                if(focusIndex<chips.getChildCount()){
                    View activeChip=chips.getChildAt(focusIndex);
                    categories.smoothScrollTo(Math.max(0,activeChip.getLeft()-dp(16)),0);
                }
            });
        };
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                fillCompactPickerGrid(grid,choices,currentCategory[0],s.toString(),favorites,recent,repaint[0]);scroll.scrollTo(0,0);
            }
            public void afterTextChanged(android.text.Editable e){}
        });
        root.setFocusableInTouchMode(true);root.requestFocus();
        repaint[0].run();
        openStyleDialog(dialog,root,false);
    }
    private void showMultilayerPickerDialog(){ showCompactStylePicker("Multilayer"); }
    /** Distinct sample tiles show positive word vinyl versus word-shaped plate
     * openings without invoking the full image engine merely to draw a menu. */
    private View wordDrawerPreview(final boolean knockout){
        return new View(this){
            final android.graphics.Paint pen=new android.graphics.Paint(3);
            @Override protected void onDraw(android.graphics.Canvas c){
                super.onDraw(c);
                float sx=getWidth()/74f,sy=getHeight()/48f;
                c.save();c.scale(sx,sy);
                pen.setColor(0xff182c3b);
                c.drawRoundRect(1,1,73,47,6,6,pen);
                if(knockout){
                    pen.setColor(0xffba8b76);
                    c.drawRoundRect(5,5,69,43,5,5,pen);
                    pen.setColor(0xff182c3b);
                    pen.setTextSize(13);pen.setFakeBoldText(true);
                    c.drawText("VINYL",10,22,pen);
                    pen.setTextSize(11);c.drawText("WORDS",10,36,pen);
                }else{
                    pen.setColor(0xffe0c6a8);pen.setTextSize(14);pen.setFakeBoldText(true);
                    c.drawText("VINYL",6,18,pen);
                    pen.setColor(0xff76b0ac);pen.setTextSize(10);
                    c.drawText("STUDIO",20,31,pen);
                    pen.setColor(0xffd39b84);pen.setTextSize(8);
                    c.drawText("ART",9,41,pen);
                }
                c.restore();
            }
        };
    }
    private void stylePickerBack(android.app.Dialog dialog){
        dialog.dismiss();showUnifiedStyleDrawer();
    }
    /** Header gestures are isolated from the ScrollView: browsing styles never dismisses the sheet. */
    private LinearLayout styleDialogRoot(String title,android.app.Dialog dialog,boolean child){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(5),dp(16),dp(16));root.setBackground(shape(SURFACE,StudioTheme.R_SHEET));
        FrameLayout dragRegion=new FrameLayout(this);
        LinearLayout.LayoutParams dragParams=new LinearLayout.LayoutParams(-1,dp(34));root.addView(dragRegion,dragParams);
        View handle=new View(this);handle.setBackground(shape(0xff718096,8));
        FrameLayout.LayoutParams handleParams=new FrameLayout.LayoutParams(dp(48),dp(5),Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        handleParams.topMargin=dp(10);dragRegion.addView(handle,handleParams);
        dragRegion.setContentDescription(dragRegion.getContext().getString(R.string.quality_drag_up_to_expand_or_down_to_dismiss_artwork_styles));
        dragRegion.setOnClickListener(v->{android.view.Window w=dialog.getWindow();
            if(w!=null)w.setLayout(-1,Math.round(getResources().getDisplayMetrics().heightPixels*.94f));});
        // performClick() is dispatched on tap release below, so accessibility activation still works.
        //noinspection ClickableViewAccessibility
        dragRegion.setOnTouchListener(new View.OnTouchListener(){
            float touchY;
            @Override public boolean onTouch(View v,android.view.MotionEvent event){
                if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){touchY=event.getRawY();return true;}
                if(event.getActionMasked()==android.view.MotionEvent.ACTION_UP){
                    float distance=event.getRawY()-touchY;
                    if(distance>dp(65))dialog.dismiss();
                    else if(distance < -dp(45)){
                        android.view.Window w=dialog.getWindow();
                        if(w!=null)w.setLayout(-1,Math.round(getResources().getDisplayMetrics().heightPixels*.94f));
                    }else v.performClick();
                    return true;
                }
                return true;
            }
        });
        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header,new LinearLayout.LayoutParams(-1,dp(48)));
        if(child){TextView back=text("‹",30,TEXT,true);back.setGravity(Gravity.CENTER);
            back.setContentDescription(back.getContext().getString(R.string.quality_back_to_artwork_styles));header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
            back.setOnClickListener(v->stylePickerBack(dialog));}
        TextView name=text(title,18,TEXT,true);header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView close=text("×",28,TEXT,true);close.setGravity(Gravity.CENTER);
        close.setContentDescription(close.getContext().getString(R.string.quality_close_artwork_styles));header.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));
        close.setOnClickListener(v->dialog.dismiss());
        return root;
    }
    private void openStyleDialog(android.app.Dialog dialog,LinearLayout root,boolean child){
        dialog.setContentView(root);dialog.setCancelable(true);dialog.setCanceledOnTouchOutside(true);
        // Dialog owns Back while visible; OnCancel handles gesture and button equally.
        // Dismiss from selecting a style does not trigger the cancel navigation.
        if(child)dialog.setOnCancelListener(d->showUnifiedStyleDrawer());
        dialog.show();android.view.Window win=dialog.getWindow();
        if(win!=null){win.setBackgroundDrawableResource(android.R.color.transparent);
            win.setGravity(Gravity.BOTTOM);win.setLayout(-1,Math.min(getResources().getDisplayMetrics().heightPixels*85/100,dp(820)));}
    }
    /** One row component reused by all five picker categories. */
    private void styleDrawerRow(LinearLayout parent,String title,String description,View preview,Runnable callback){
        styleDrawerRow(parent,title,description,preview,false,callback);
    }
    private void styleDrawerRow(LinearLayout parent,String title,String description,
                                View preview,boolean selected,Runnable callback){
        LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(8),dp(8),dp(12),dp(8));
        line.setMinimumHeight(dp(80));
        line.setBackground(ripple(outlined(selected?StudioTheme.ACCENT_SOFT:StudioTheme.SURFACE_HIGH,
            selected?StudioTheme.ACCENT:StudioTheme.OUTLINE,StudioTheme.R_CARD),StudioTheme.R_CARD));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(80));
        lp.bottomMargin=dp(8);parent.addView(line,lp);
        if(preview!=null){
            FrameLayout frame=new FrameLayout(this);
            frame.setBackground(shape(StudioTheme.SURFACE,10));
            line.addView(frame,new LinearLayout.LayoutParams(dp(64),dp(56)));
            frame.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        }
        LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(12),0,0,0);line.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        TextView name=text(title,14,TEXT,true);name.setMaxLines(2);
        words.addView(name);
        if(description!=null&&!description.isEmpty()){
            TextView sub=text(description,12,MUTED,false);sub.setMaxLines(2);
            words.addView(sub);
        }
        TextView status=text(selected?"✓":"›",selected?19:23,selected?StudioTheme.ACCENT_INK:MUTED,true);
        status.setGravity(Gravity.CENTER);
        line.addView(status,new LinearLayout.LayoutParams(dp(32),dp(48)));
        line.setTag(title.toLowerCase(Locale.ROOT));
        line.setContentDescription(title+". "+description+(selected?". Currently selected":". Select style"));
        line.setOnClickListener(v->callback.run());
    }
    /** Search only this category. No rerender or image-analysis work while typing. */
    private void addStyleSearch(LinearLayout root,ScrollView scroll,LinearLayout items){
        EditText search=new EditText(this);
        search.setSingleLine(true);search.setTextSize(14);search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);search.setHint(getString(R.string.ui_find_a_style));
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(dp(16),0,dp(16),0);search.setMinimumHeight(dp(48));
        search.setBackground(outlined(StudioTheme.SURFACE_HIGH,StudioTheme.OUTLINE,StudioTheme.R_CHIP));
        search.setContentDescription(search.getContext().getString(R.string.quality_search_artwork_styles_in_this_category));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(48));
        sp.bottomMargin=dp(12);root.addView(search,sp);
        // Do not launch the keyboard every time the style sheet opens.
        root.setFocusableInTouchMode(true);root.requestFocus();
        TextView empty=text(getString(R.string.ui_no_matching_styles),13,MUTED,false);
        empty.setGravity(Gravity.CENTER);empty.setVisibility(View.GONE);
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                String q=s.toString().trim().toLowerCase(Locale.ROOT);
                int matches=0;
                for(int i=0;i<items.getChildCount();i++){
                    View child=items.getChildAt(i);
                    if("style-section".equals(child.getTag()))continue;
                    boolean show=q.isEmpty()||String.valueOf(child.getTag()).contains(q) ||
                        (child.getContentDescription()!=null &&
                         child.getContentDescription().toString().toLowerCase(Locale.ROOT).contains(q));
                    child.setVisibility(show?View.VISIBLE:View.GONE);
                    if(show)matches++;
                }
                // Only show a section when a child below it remains visible.
                for(int i=0;i<items.getChildCount();i++){
                    View heading=items.getChildAt(i);
                    if(!"style-section".equals(heading.getTag()))continue;
                    boolean populated=false;
                    for(int j=i+1;j<items.getChildCount();j++){
                        View next=items.getChildAt(j);
                        if("style-section".equals(next.getTag()))break;
                        if(next.getVisibility()==View.VISIBLE){populated=true;break;}
                    }
                    heading.setVisibility(populated?View.VISIBLE:View.GONE);
                }
                empty.setVisibility(matches==0?View.VISIBLE:View.GONE);
                scroll.scrollTo(0,0);
            }
            public void afterTextChanged(android.text.Editable e){}
        });
        root.addView(empty);
    }
    private void stylePickerSection(LinearLayout list,String heading){
        TextView label=text(heading,12,StudioTheme.ACCENT_INK,true);
        label.setPadding(dp(8),dp(12),0,dp(8));label.setTag("style-section");
        list.addView(label);
    }
    /** Simple stable preview; lightweight artwork, never a full specialty render. */
    private View styleIconPreview(final String title){
        return new View(this){
            final Paint pen=new Paint(3);
            final Path shape=new Path(); // reused: no allocation per frame
            @Override protected void onDraw(Canvas c){
                super.onDraw(c);
                float w=getWidth(),h=getHeight();
                c.drawColor(StudioTheme.SURFACE);
                String k=title.toLowerCase(Locale.ROOT);
                final boolean offset=k.contains("offset")||k.contains("brick")||k.contains("scale");
                final boolean circle=k.contains("bottle")||k.contains("stipple")||k.contains("voronoi");
                final boolean scallop=k.contains("fish scale");
                final boolean diagonal=k.contains("low-poly")||k.contains("topographic")||k.contains("glass");
                final int[] colors={0xffb78278,0xff815a61,0xffd39d8f,0xff5f4658};
                float pitchX=w/4.5f,pitchY=h/3.5f;
                for(int row=0;row<4;row++)for(int col=-1;col<5;col++){
                    float x=col*pitchX+(offset&&(row&1)==1?pitchX*.5f:0)+2;
                    float y=row*pitchY+2;
                    pen.setColor(colors[Math.floorMod(col+row*3,colors.length)]);
                    if(scallop){
                        Path scale=shape;scale.rewind();
                        scale.moveTo(x+pitchX*.18f,y);
                        scale.lineTo(x+pitchX*.82f,y);
                        scale.cubicTo(x+pitchX*.99f,y+pitchY*.4f,x+pitchX*.75f,y+pitchY*.98f,x+pitchX*.5f,y+pitchY);
                        scale.cubicTo(x+pitchX*.25f,y+pitchY*.98f,x+pitchX*.01f,y+pitchY*.4f,x+pitchX*.18f,y);
                        c.drawPath(scale,pen);
                    }else if(circle)c.drawCircle(x+pitchX*.45f,y+pitchY*.45f,Math.min(pitchX,pitchY)*.43f,pen);
                    else if(diagonal){Path tri=shape;tri.rewind();tri.moveTo(x,y);tri.lineTo(x+pitchX,y+pitchY*.5f);tri.lineTo(x,y+pitchY);tri.close();c.drawPath(tri,pen);}
                    else c.drawRect(x,y,x+pitchX*.93f,y+pitchY*.91f,pen);
                }
            }
        };
    }
    /** All multilayer line styles in one drawer; never silently reroute to ellipses. */
    private void showLinesPickerDialog(){ showCompactStylePicker("Lines"); }
    /** User-facing single-line styles. Retired styles remain in the stable enum to open old projects. */
    private static final ContinuousLineModes.LineStyle[] PRIMARY_SINGLE_LINES={
        ContinuousLineModes.LineStyle.SPIRAL,ContinuousLineModes.LineStyle.ADAPTIVE_HILBERT,
        ContinuousLineModes.LineStyle.GOSPER,ContinuousLineModes.LineStyle.PEANO,
        ContinuousLineModes.LineStyle.WUNDERLICH,ContinuousLineModes.LineStyle.MOORE,
        ContinuousLineModes.LineStyle.COIL};
    private static final String[] ASCII_SAMPLE_GLYPHS={"A","@","+","/","#"};
    private static final String[] SINGLE_LINE_NAMES={"Spiral","Adaptive Hilbert","Gosper",
        "Peano","Wunderlich","Moore","Coil"};
    private void showSingleLinePickerDialog(){ showCompactStylePicker("Single Line"); }
    /** Distinct multi-stroke and glyph engines belong to Art techniques, not Single Line.
     * Their immutable style IDs remain unchanged for imported/saved projects. */
    private void showArtTechniquesDialog(){ showCompactStylePicker("Art techniques"); }
    /** Sample previews use the SAME mathematical carrier as the real recursive styles. */
    private final class SingleLineSample extends View {
        final ContinuousLineModes.LineStyle style;final Paint pen=new Paint(3);final Path carrier;
        private final Path line=new Path(); // reused across frames
        SingleLineSample(ContinuousLineModes.LineStyle style){super(StudioActivity.this);this.style=style;
            if(ContinuousLineModes.curveFamily(style)>=0){
                SpaceFillingCurves.Options o=new SpaceFillingCurves.Options();
                o.family=ContinuousLineModes.curveFamily(style);o.depth=2;
                o.width=160;o.height=110;o.photoInfluence=0;
                SpaceFillingCurves.Result geometry=SpaceFillingCurves.generate(o);
                carrier=ContinuousLineModes.curvePath(geometry);
            }else carrier=null;
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float w=getWidth(),h=getHeight();
            pen.setStyle(Paint.Style.STROKE);pen.setColor(0xffa4c7ed);
            pen.setStrokeWidth(dp(1.25f));pen.setStrokeJoin(Paint.Join.ROUND);
            if(carrier!=null){canvas.save();canvas.scale(w/160f,h/110f);canvas.drawPath(carrier,pen);canvas.restore();return;}
            line.rewind();
            if(style==ContinuousLineModes.LineStyle.SPIRAL){
                for(int n=0;n<440;n++){float t=n*.065f,r=(Math.min(w,h)*.43f)*n/440f;
                    float x=w/2+r*(float)Math.cos(t),y=h/2+r*(float)Math.sin(t);
                    if(n==0)line.moveTo(x,y);else line.lineTo(x,y);}
            }else{ // adaptive square-grid Hilbert carrier, continuous
                float[][] points={{.1f,.1f},{.1f,.4f},{.4f,.4f},{.4f,.1f},{.6f,.1f},
                    {.9f,.1f},{.9f,.4f},{.6f,.4f},{.6f,.6f},{.9f,.6f},
                    {.9f,.9f},{.6f,.9f},{.4f,.9f},{.4f,.6f},{.1f,.6f},{.1f,.9f}};
                for(int i=0;i<points.length;i++){
                    float x=points[i][0]*w,y=points[i][1]*h;
                    if(i==0)line.moveTo(x,y);else line.lineTo(x,y);}
            }
            canvas.drawPath(line,pen);
        }
    }
    /** Text portraits share Colors/Layers/Export but have distinct tool screens. */
    private void activateTextPortrait(int variant){
        // Word Portrait is new-workflow variant 1, while legacy ASCII variant 0
        // remains loadable by immutable project ID 5 without a second picker tile.
        activateNewStyle(VinylStyleSuite.TEXT);
        newStyleVariant=variant;
        newStyleVariantMemory[VinylStyleSuite.TEXT]=variant;
        requestAlternate();setToolInternal(1);
    }
    private void showShapePickerDialog(){ showCompactStylePicker("Styles"); }
    /** Older saved styles remain renderable but do not clutter the main selection list. */

    private void selectSpaceCurve(ContinuousLineModes.LineStyle curve){
        if(ContinuousLineModes.curveFamily(curve)<0)
            throw new IllegalArgumentException("Not a space-filling curve: "+curve);
        checkpoint();rememberNewStylePalette();
        applyStyleSelection(StyleSelection.continuous(curve.name()));newStyleResult=null;newStyleVectors=null;
        mazeResult=null;continuousPreview=null;tonalResult=null;tonalDisplayBitmap=null;
        tessellationResult=null;curvePathResult=null;curveRibbonResult=null;
        spiralRibbonResult=null;alternatePreviewBitmap=null;specialtyVectorCache=null;
        activeWorkspace=2;activeSubtoolPosition=0;
        alternateGeneration++;invalidateToolPanels();setToolInternal(1);expandToolSheet();requestAlternate();
    }
    private void spaceCurveControls(LinearLayout host){
        section(host,"Recursive space-filling curve");
        host.addView(text(getString(R.string.ui_photo_aware_centerlines_bright_regions_use),12,MUTED,false));
        action(host,"Rendering method: "+AdaptiveCurvePortrait.METHODS[curveToneMethod]+"  ▾",()->{
            new AlertDialog.Builder(this).setTitle(getString(R.string.ui_photographic_curve_rendering))
                .setSingleChoiceItems(AdaptiveCurvePortrait.METHODS,curveToneMethod,(dialog,selected)->{
                    curveToneMethod=selected;dialog.dismiss();setToolInternal(1);requestAlternate();})
                .setNegativeButton("Cancel",null).show();
        });
        int maxDepth=continuousStyle==ContinuousLineModes.LineStyle.SIERPINSKI?7:
            continuousStyle==ContinuousLineModes.LineStyle.MOORE?6:5;
        if(curveDepth>maxDepth)curveDepth=maxDepth;
        slider(host,"Recursion / line density",curveDepth,1,maxDepth,1,v->{curveDepth=Math.round(v);requestAlternate();});
        slider(host,"Image influence · %",curvePhotoInfluence*100f,0,100,5,v->{curvePhotoInfluence=v/100f;requestAlternate();});
        slider(host,"Tone contrast · %",curveToneContrast*100f,0,100,5,v->{curveToneContrast=v/100f;requestAlternate();});
        slider(host,"Shadow density · %",curveShadowBoost*100f,0,100,5,v->{curveShadowBoost=v/100f;requestAlternate();});
        slider(host,"Highlight preservation · %",curveHighlightProtection*100f,0,100,5,v->{curveHighlightProtection=v/100f;requestAlternate();});
        slider(host,"Edge definition · %",curveEdgePriority*100f,0,100,5,v->{curveEdgePriority=v/100f;requestAlternate();});
        slider(host,"Corner rounding · %",curveCornerRounding*100f,0,100,5,v->{curveCornerRounding=v/100f;requestAlternate();});
        slider(host,"Rotation · degrees",curveRotationDegrees,-180,180,15,v->{curveRotationDegrees=v;requestAlternate();});
        action(host,curveVinylRibbon?"Output: cuttable vinyl ribbon":"Output: pen / engraving centerline",()->{
            curveVinylRibbon=!curveVinylRibbon;setToolInternal(1);requestAlternate();});
        if(curveVinylRibbon){
            // D8: ranges start at the material's cut minimum / weed gap (the render applies the same floor).
            final float featureFloor=PhysicalSliderFloor.featureMm(settings.minimumMm);
            final float gapFloor=PhysicalSliderFloor.gapMm(settings.bridgeMm,settings.minimumMm);
            float[] ribbonRange=PhysicalSliderFloor.range(.55f,3.0f,featureFloor,.05f);
            slider(host,"Maximum vinyl ribbon width · mm",PhysicalSliderFloor.effective(curveRibbonWidthMm,ribbonRange[0]),ribbonRange[0],ribbonRange[1],.05f,v->{curveRibbonWidthMm=v;curveMinFeatureMm=Math.min(curveMinFeatureMm,curveRibbonWidthMm);requestAlternate();});
            float ribbonNow=PhysicalSliderFloor.effective(curveRibbonWidthMm,ribbonRange[0]);
            float[] featureRange=PhysicalSliderFloor.range(.40f,Math.min(1.20f,ribbonNow),featureFloor,.05f);
            slider(host,"Minimum vinyl feature · mm",PhysicalSliderFloor.effective(curveMinFeatureMm,featureRange[0]),featureRange[0],featureRange[1],.05f,v->{curveMinFeatureMm=v;requestAlternate();});
            float[] gapRange=PhysicalSliderFloor.range(.30f,1.50f,gapFloor,.05f);
            slider(host,"Minimum weed gap · mm",PhysicalSliderFloor.effective(curveMinGapMm,gapRange[0]),gapRange[0],gapRange[1],.05f,v->{curveMinGapMm=v;requestAlternate();});
            if(featureRange[0]>.40f||gapRange[0]>.30f)host.addView(text(String.format(Locale.US,
                "Ranges start at the material minimum (%.2f mm) and weed gap (%.2f mm).",featureFloor,gapFloor),12,MUTED,false));
            host.addView(text(getString(R.string.ui_the_vinyl_export_is_one_merged),12,MUTED,false));
        }else{
            slider(host,"Pen / engraving stroke · mm",continuousStrokeMm,.10f,1.50f,.05f,v->{continuousStrokeMm=v;refreshSingleLinePreview();});
            host.addView(text(getString(R.string.ui_pen_engrave_output_is_one_connected),12,MUTED,false));
        }
    }
    private void showHalftoneStylesDrawer(){showUnifiedStyleDrawer();}
    /** Picker index -> selection; the mapping the specialty picker has always used. */
    private StyleSelection specialtySelection(int idx){
        if(idx==18||idx==8||idx==9)return StyleSelection.tessellation();
        if(isLayeredSpecialtyId(idx))return StyleSelection.layeredCell(idx==19?StyleSelection.Cell.OFFSET:
            idx==20?StyleSelection.Cell.BOTTLE_CAP:idx==21?StyleSelection.Cell.FISH_SCALE:StyleSelection.Cell.PLAIN,
            overprintKindForId(idx));
        if(idx>=0&&idx<=5)return StyleSelection.continuous(ContinuousLineModes.LineStyle.values()[idx].name());
        if(idx==6)return StyleSelection.tonalStencil();
        if(idx==7)return StyleSelection.maze();
        return StyleSelection.HALFTONE;
    }
    private void activateSpecialtyStyle(int idx){
        // Historical specialty ID 26 is retired; do not re-enable it through old UI events.
        if(idx==26)return;
        checkpoint();rememberNewStylePalette();
        newStyleResult=null;newStyleVectors=null;newStyleSelectedLayer=-1;
        applyStyleSelection(specialtySelection(idx));
        mazeResult=null;continuousPreview=null;tonalResult=null;tonalDisplayBitmap=null;tessellationResult=null;
        if(idx==18){tessellationStyle=TessellationEngine.STIPPLE;tessSelectedLayer=-1;
            reduceTessPalette(8);tessLayerCount=8; // Eight independent swatch slots on explicit style selection.
            // Explicit style selection uses the new standard, but loading an old
            // project still preserves its previous chosen overlap mode.
            stippleSettings.overlap=MultiColorStippleEngine.OVERPRINT;
            stippleSettings.assignment=MultiColorStippleEngine.NEAREST;
            tessPalette="Photo-derived";
            stippleOrderNormalize();}
        else if(isLayeredSpecialtyId(idx)){tessSelectedLayer=-1;}
        else if(idx==8||idx==9){tessellationStyle=idx==8?TessellationEngine.VORONOI:TessellationEngine.GRID_MOTIF;tessSelectedLayer=-1;}
        else if(idx==6){tonalSelectedLayer=-1;}
        AutoPhotoTuner.Suggestion start=selectionSuggestion(
            idx==18?AutoPhotoTuner.STIPPLE:isLayeredSpecialtyId(idx)?AutoPhotoTuner.LAYERED:
            idx==8||idx==9?AutoPhotoTuner.TESSELLATION:idx<=5?AutoPhotoTuner.CONTINUOUS:
            idx==6?AutoPhotoTuner.TONAL:AutoPhotoTuner.MAZE);
        if(start!=null){
            if(idx==18){stippleSettings.adaptiveCoverage=true;stippleSettings.pointDensity=PercentControlLimits.pointDensity(start.stippleDensity);
                stippleSettings.dotSize=PercentControlLimits.dotSize(start.stippleSize);stippleSettings.targetCoverage=start.stippleFill;}
            else if(isLayeredSpecialtyId(idx)){layeredColumns=start.layeredColumns;layeredPhotoDetail=start.layeredPhotoDetail;}
            else if(idx==8||idx==9){tessDetail=start.mosaicDetail;tessEdge=start.mosaicEdge;}
            else if(idx<=5)continuousDensity=start.continuousDetail;
            else if(idx==6)tonalDetailByCount[tonalLayerCount]=start.tonalDetail;
            else if(idx==7)mazeGrid=start.mazeGrid;
        }
        if(isLayeredSpecialtyId(idx)){
            // New selections begin at the highest physically allowed photographic
            // fidelity. User edits made afterward are never reset by rendering.
            reduceTessPalette(8);tessLayerCount=8;layeredSolidBase=true;layeredExtraMotif=false;
            layeredColumns=40;layeredMotifDepth=3;layeredPhotoDetail=1f;
            layeredArtDetail=.94f;layeredMinFeatureMm=.30f;
            layeredSeamMm=.06f;tessGapMm=.18f;layeredBackground=-1;
            if(idx==19){layeredSolidBase=true;layeredExtraMotif=false;}
            if(idx==20){layeredSolidBase=false;layeredColumns=36;layeredExtraMotif=true;}
            if(idx==21){layeredSolidBase=false;layeredColumns=34;layeredExtraMotif=true;layeredPhotoDetail=1f;}
            if(overprintCellKind>0){layeredSolidBase=true;layeredExtraMotif=false;
                layeredColumns=34;layeredPhotoDetail=1f;layeredArtDetail=.68f;
                layeredMotifDepth=2;layeredBackground=-1;
                // A new style selection is an explicit reset point. Arrange
                // the current candidates once; slider edits, paper and saves
                // never silently reassign the physical colour sheets.
                setTessBasePalette(OpaqueOverlayPhotoPalette.arrange(
                    tessColors,tessLayerCount,preparedPhotoColorPixels));
            }
        }
        activeWorkspace=2;activeSubtoolPosition=0;
        if(underlayRequested()){
            if(preparedPhotoPalette!=null)
                updateAutomaticSnipFoundation(preparedPhotoPalette,preparedPhotoColorPixels,true);
            else ensurePreparedPhotoPalette();
        }
        alternateGeneration++;setToolInternal(1);expandToolSheet();requestAlternate();
        if(idx==18)suggestStipplePhotoColors();
    }
    /** Variations share the same carrier family; they are controls, not extra drawer styles. */
    private void singleLineVariations(LinearLayout host){
        if(continuousStyle==ContinuousLineModes.LineStyle.SPIRAL||
           continuousStyle==ContinuousLineModes.LineStyle.CONCENTRIC_RINGS){
            action(host,"Spiral variation: "+(continuousStyle==ContinuousLineModes.LineStyle.SPIRAL?"Photo ribbon":"Concentric"),()->{
                activateSpecialtyStyle(continuousStyle==ContinuousLineModes.LineStyle.SPIRAL?1:0);
            });
        }
    }
    private int autoTuneStyle(){
        if(stippleActive())return AutoPhotoTuner.STIPPLE;
        if(newStyleActive())return AutoPhotoTuner.TESSELLATION;
        if(layeredCellMode)return AutoPhotoTuner.LAYERED;
        if(tessellationMode)return AutoPhotoTuner.TESSELLATION;
        if(tonalStencilMode)return AutoPhotoTuner.TONAL;
        if(settings.mode==VinylEngine.STENCIL)return AutoPhotoTuner.STENCIL;
        if(mazePortraitMode)return AutoPhotoTuner.MAZE;
        if(continuousStyle!=null)return AutoPhotoTuner.CONTINUOUS;
        return LineArtEngine.isLineStyle(settings.pattern)?AutoPhotoTuner.LINES:AutoPhotoTuner.SHAPES;
    }
    private void addPhotoAutoAction(LinearLayout panel){
        action(panel,"✦ Best settings for this photo",this::chooseAutoTuneGoal);
        panel.addView(text("Suggests detail and cut-safe sizes for the selected style. Keeps your colors, hue, layer count, crop and material.",12,MUTED,false));
    }
    private volatile int autoTuneSerial=0;
    private void chooseAutoTuneGoal(){
        if(photo==null){toast(getString(R.string.ui_import_a_photo_first));return;}
        final String[] choices={"Maximum detail · fine photographic features",
            "Balanced · detail and easier cutting","Easy cutting · bigger, simpler details"};
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_best_settings_for_this_photo))
            .setItems(choices,(dialog,index)->analyzeForAutoTune(index))
            .setNegativeButton("Cancel",null).show();
    }
    private void analyzeForAutoTune(int goal){
        final Bitmap source=photo;
        if(source==null){toast(getString(R.string.ui_import_a_photo_first));return;}
        final long revision=photoRevision;
        final int style=autoTuneStyle();
        final int stamp=++autoTuneSerial;
        final float widthMm=settings.widthMm,smallest=settings.minimumMm;
        final long photoSettingsSignature=tonalSignature(null,settings);
        final VinylEngine.Settings photoAdjustments=settings.copy();
        final boolean roundCrop=settings.circleCrop;
        final SnipMask exclusions=settings.snips==null?null:settings.snips.copy();
        // Keep image analysis bounded on phone; never pass the original full-resolution pixels.
        final int maxSide=Math.max(source.getWidth(),source.getHeight());
        final float scale=Math.min(1f,224f/Math.max(1,maxSide));
        final int w=Math.max(2,Math.round(source.getWidth()*scale));
        final int h=Math.max(2,Math.round(source.getHeight()*scale));
        final Bitmap small=Bitmap.createScaledBitmap(source,w,h,true);
        final int[] argb=new int[w*h];
        small.getPixels(argb,0,w,0,0,w,h);
        if(small!=source)small.recycle();
        toast(getString(R.string.ui_analyzing_photo));
        diagnosticsExecutor.execute(()->{
            try{
                final int[] adjusted=ImageLevels.isNeutral(photoAdjustments)?argb:
                    ImageLevels.apply(argb,w,h,photoAdjustments);
                final AutoPhotoTuner.Stats stats=AutoPhotoTuner.analyze(adjusted,w,h,exclusions,roundCrop);
                final AutoPhotoTuner.Suggestion suggestion=AutoPhotoTuner.suggest(stats,style,goal,widthMm,smallest);
                postUi(()->{
                    if(stamp!=autoTuneSerial||photo!=source||photoRevision!=revision||autoTuneStyle()!=style||
                       tonalSignature(null,settings)!=photoSettingsSignature||
                       settings.widthMm!=widthMm||settings.minimumMm!=smallest){
                        toast(getString(R.string.ui_photo_or_style_changed_run_auto));return;
                    }
                    showAutoTuneSuggestion(stats,suggestion,source,revision,photoSettingsSignature,widthMm,smallest);
                });
            }catch(Exception problem){postUi(()->{
                if(stamp==autoTuneSerial)toast("Could not analyze photo: "+friendlyFailure(problem,"Photo analysis failed"));
            });}
        });
    }
    private void showAutoTuneSuggestion(AutoPhotoTuner.Stats stats,
            AutoPhotoTuner.Suggestion suggestion,Bitmap source,long revision,
            long signature,float widthMm,float smallest){
        final String goal=suggestion.goal==AutoPhotoTuner.DETAIL?"Maximum detail":
            suggestion.goal==AutoPhotoTuner.EASY?"Easy cutting":"Balanced";
        String message=stats.summary()+"\n\n"+goal+" recommendation:\n"+
            suggestion.summary()+"\n\nYour swatches, hue shift, layer count, mounting surface, cut material and Snip boundaries will not change. These are starting points; check Export > Cut validation before cutting.";
        new AlertDialog.Builder(this).setTitle("Photo recommendations")
            .setMessage(message)
            .setNeutralButton(getString(R.string.ui_try_on_canvas),(d,which)->applyAutoTuneIfCurrent(suggestion,source,revision,signature,widthMm,smallest,true))
            .setPositiveButton(getString(R.string.ui_apply_settings),(d,which)->applyAutoTuneIfCurrent(suggestion,source,revision,signature,widthMm,smallest,false))
            .setNegativeButton(getString(R.string.ui_keep_current),null).show();
    }
    private void applyAutoTuneIfCurrent(AutoPhotoTuner.Suggestion r,Bitmap source,long revision,
            long signature,float widthMm,float smallest,boolean trial){
        if(photo!=source||photoRevision!=revision||autoTuneStyle()!=r.style||
           tonalSignature(null,settings)!=signature||settings.widthMm!=widthMm||
           settings.minimumMm!=smallest){
            toast(getString(R.string.ui_photo_or_style_changed_run_auto));return;
        }
        checkpoint(); // One Undo always restores the exact original project state.
        switch(r.style){
            case AutoPhotoTuner.STIPPLE:
                stippleSettings.adaptiveCoverage=true;
                stippleSettings.pointDensity=PercentControlLimits.pointDensity(r.stippleDensity);
                stippleSettings.dotSize=PercentControlLimits.dotSize(r.stippleSize);
                stippleSettings.targetCoverage=r.stippleFill;
                stippleSettings.optimizePacking=true;
                break;
            case AutoPhotoTuner.TESSELLATION:
                tessDetail=r.mosaicDetail;tessEdge=r.mosaicEdge;break;
            case AutoPhotoTuner.LAYERED:
                layeredColumns=r.layeredColumns;layeredPhotoDetail=r.layeredPhotoDetail;break;
            case AutoPhotoTuner.TONAL:
                if(tonalStencilMode)tonalDetailByCount[tonalLayerCount]=r.tonalDetail;
                else settings.stencilCleanup=r.goal==AutoPhotoTuner.EASY?2:1;
                break;
            case AutoPhotoTuner.STENCIL:
                settings.masterSpacingMm=r.spacingMm;
                settings.stencilCleanup=r.stencilCleanup;
                settings.stencilSmoothing=r.stencilSmoothing;
                if(settings.linkedControls)setLinkedValues();
                break;
            case AutoPhotoTuner.MAZE:mazeGrid=r.mazeGrid;break;
            case AutoPhotoTuner.CONTINUOUS:continuousDensity=r.continuousDetail;break;
            case AutoPhotoTuner.LINES:
            case AutoPhotoTuner.SHAPES:
                settings.masterSpacingMm=r.spacingMm;
                settings.masterContrast=r.screenContrast;
                if(r.style==AutoPhotoTuner.LINES){
                    settings.lineMaxFraction=r.lineWidth;
                    settings.detailBoost=r.lineDetail;
                    settings.contourStrength=r.lineContour;
                }else settings.shapeScale=r.shapeScale;
                if(settings.linkedControls)setLinkedValues();
                break;
            default:break;
        }
        invalidateToolPanels();
        setToolInternal(1);
        if(alternateActive())requestAlternate();else requestRender();
        toast(trial?"Preview applied. Tap Undo to restore your settings.":
            "Auto settings applied. Tap Undo to revert.");
    }

    private void addCompactSpecialtyAdjustments(){
        if(newStyleActive()){LinearLayout g=group();newStyleControls(g);return;}
        LinearLayout g=group();section(g,"Style adjustments");
        // Automatic image-aware setup runs on style selection (no extra button).
        if(layeredCellMode){layeredControls(g);}
        else if(tessellationMode){
            if(stippleActive())stippleControls(g);else tessellationControls(g);
        }else if(mazePortraitMode){
            slider(g,"Maze detail",mazeGrid,20,100,2,v->{mazeGrid=Math.round(v);requestAlternate();});
            slider(g,"Difficulty",mazeDifficulty,0,3,1,v->{mazeDifficulty=Math.round(v);requestAlternate();});
            action(g,mazeShowSolution?"Hide solution":"Show solution",()->{mazeShowSolution=!mazeShowSolution;artwork.invalidate();setToolInternal(1);});
        }else if(tonalStencilMode){
            tonalDetailControl(g);

        }else if(activeCurveFamily()){
            spaceCurveControls(g);
        }else{
            singleLineVariations(g);
            if(continuousStyle!=ContinuousLineModes.LineStyle.SPIRAL)
                slider(g,"Line width · mm",continuousStrokeMm,.10f,1.50f,.05f,v->{continuousStrokeMm=v;refreshSingleLinePreview();});
            slider(g,"Portrait detail",continuousDensity,1,100,1,v->{continuousDensity=Math.round(v);requestAlternate();});
        }
        g.addView(text(alternateStatus,12,ACCENT,false));
    }

    private void refreshSingleLinePreview(){
        if(curveVinylRibbon&&curveRibbonResult!=null&&activeCurveFamily())return;
        if(continuousPreview!=null&&alternatePreparedSource!=null){
            alternatePreviewBitmap=renderAlternatePreviewBitmap(alternatePreparedSource,continuousPreview,null,null,1200);
            artwork.invalidate();
        }
    }
    private void stippleControls(LinearLayout host){
        section(host,"Photo-derived weighted stippling · 2–8 colors");
        host.addView(text(getString(R.string.ui_stack_colors_closest_vinyl_color_each),12,MUTED,false));
        if(stippleSettings.overlap!=MultiColorStippleEngine.OVERPRINT ||
           stippleSettings.assignment!=MultiColorStippleEngine.NEAREST){
            host.addView(text(getString(R.string.ui_this_saved_project_has_a_legacy),12,MUTED,false));
            action(host,"Use normal color stacking",()->{
                checkpoint();stippleSettings.overlap=MultiColorStippleEngine.OVERPRINT;
                stippleSettings.assignment=MultiColorStippleEngine.NEAREST;
                requestAlternate();setToolInternal(1);
            });
        }
        // The palette must be visible without switching away from Adjust. This is
        // the same saved tessColors array that drives Layers and all SVG exports.
        section(host,"Color layers · tap to edit");
        for(int first=0;first<tessLayerCount;first+=4){
            LinearLayout swatches=horizontal(host);
            for(int i=first;i<Math.min(tessLayerCount,first+4);i++){
                final int index=i,color=tessColors[i];
                TextView chip=text("L"+(i+1),12,contrastText(color),true);
                chip.setGravity(Gravity.CENTER);
                chip.setBackground(outlined(color,0xff78889d,9));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);
                lp.rightMargin=dp(6);lp.bottomMargin=dp(7);swatches.addView(chip,lp);
                chip.setContentDescription("Edit layer "+(i+1)+" swatch "+colorHex(color));
                chip.setOnClickListener(v->editTessColor(index));
            }
        }
        section(host,"Stipple cut shapes");
        final int[] shapeIds={MultiColorStippleEngine.ROUND,MultiColorStippleEngine.SQUARE,
            MultiColorStippleEngine.DIAMOND,MultiColorStippleEngine.HEXAGON,
            MultiColorStippleEngine.ELLIPSE,MultiColorStippleEngine.CAPSULE,
            MultiColorStippleEngine.ROUNDED_SQUARE,MultiColorStippleEngine.STAR};
        for(int start=0;start<shapeIds.length;start+=4){
            LinearLayout row=horizontal(host);
            for(int j=start;j<Math.min(start+4,shapeIds.length);j++){
                final int id=shapeIds[j];String name=MultiColorStippleEngine.SHAPE_NAMES[id];
                TextView pill=text(name,11,TEXT,stippleSettings.shape==id);
                pill.setGravity(Gravity.CENTER);pill.setPadding(dp(2),0,dp(2),0);
                pill.setBackground(outlined(stippleSettings.shape==id?SELECTED:SURFACE,
                    stippleSettings.shape==id?ACCENT:0xff536075,10));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(41),1);
                lp.rightMargin=dp(4);lp.bottomMargin=dp(5);row.addView(pill,lp);
                pill.setContentDescription(name+" stipple shape");
                pill.setOnClickListener(v->{
                    if(stippleSettings.shape==id)return;
                    checkpoint();stippleSettings.shape=id;invalidateToolPanels();setToolInternal(1);requestAlternate();
                });
            }
        }
        section(host,"Primary adjustments");
        // RC75: Stipple settings are direct controls; no hidden Advanced drawer.
        // Obsolete legacy detail control removed in favor of Point density and Dot size.
        if(stippleSettings.adaptiveCoverage && stippleSettings.overlap!=MultiColorStippleEngine.SHARED){
            slider(host,"Detail · point density %",PercentControlLimits.pointDensity(stippleSettings.pointDensity)*100f,50,100,5,v->{
                stippleSettings.pointDensity=PercentControlLimits.pointDensity(v/100f);stippleSettings.optimizePacking=true;requestAlternate();
            });
            slider(host,"Dot size · %",PercentControlLimits.dotSize(stippleSettings.dotSize)*100f,60,100,5,v->{
                stippleSettings.dotSize=PercentControlLimits.dotSize(v/100f);stippleSettings.optimizePacking=true;requestAlternate();
            });
        }
        action(host,stippleSettings.adaptiveCoverage?"Fill empty areas: On · tap for original":"Fill empty areas: Off · tap to enable",()->{
            checkpoint();stippleSettings.adaptiveCoverage=!stippleSettings.adaptiveCoverage;
            invalidateToolPanels();setToolInternal(1);requestAlternate();
        });
        if(stippleSettings.adaptiveCoverage)
            slider(host,"Fill · target eligible coverage %",stippleSettings.targetCoverage*100f,25,80,5,v->{
                stippleSettings.targetCoverage=v/100f;requestAlternate();
            });
        host.addView(text("Point density and Dot size stop at 100%. Higher density requests do not create miniature filler shapes. Minimum cut dimensions, gaps, masks and each color's SVG limits take priority. Coverage is an estimate, not a guaranteed fill.",12,MUTED,false));
        TessellationEngine.Result result=tessellationResult;
        if(result!=null&&result.stipple!=null)host.addView(text(result.stipple.report(),12,MUTED,false));
        host.addView(text(getString(R.string.ui_customize_physical_layer_density_minimum_dot),12,MUTED,false));
    }

    /** Each selectable gap must survive the cutter preflight without being silently raised. */
    private float minimumTessellationGapMm(){
        float cutFloor=StyleCutGeometryPreflight.exportGapMm(settings.bridgeMm,settings.minimumMm)+.06f;
        return SliderRangePolicy.ceiling(cutFloor,.15f,.05f);
    }
    private float maximumTessellationGapMm(float minimum){
        // Large custom material bridges must not make a slider's max smaller than its min.
        return Math.max(1.20f,minimum+.45f);
    }
    private float normalizeTessellationGap(){
        float minimum=minimumTessellationGapMm();
        tessGapMm=SliderRangePolicy.normalize(tessGapMm,minimum,
            maximumTessellationGapMm(minimum),.05f);
        return minimum;
    }
    private void tessellationGapSlider(LinearLayout host,String label){
        float min=normalizeTessellationGap();
        slider(host,label,tessGapMm,min,maximumTessellationGapMm(min),.05f,
            v->{tessGapMm=v;requestAlternate();});
    }

    /** Compact tessellation settings: only shown when its style is active. */
    private void tessellationControls(LinearLayout host){
        if(tessellationResult!=null&&alternateReadyGeneration==alternateGeneration&&tessellationResult.options.layerCount==tessLayerCount){
            int n=tessellationResult.populated();
            String info=n+"/"+tessLayerCount+" layers · "+tessellationResult.tiles.size()+" tiles";
            if(tessSelectedLayer>=0&&tessellationResult.counts!=null&&tessSelectedLayer<tessellationResult.counts.length)
                info+=" · L"+(tessSelectedLayer+1)+" "+(tessellationResult.counts[tessSelectedLayer]+(tessellationResult.options.registration?4:0))+" closed cuts (incl. marks)";
            host.addView(text(info,12,ACCENT,false));
        }
        tessellationGapSlider(host,"Vinyl gap / spacing · mm");
        slider(host,"Tile detail",tessDetail,10,100,5,v->{tessDetail=Math.round(v);});
        if(tessellationStyle==TessellationEngine.VORONOI)
            slider(host,"Edge-aware detail",tessEdge*100,0,100,10,v->{tessEdge=v/100f;});
        host.addView(text(getString(R.string.ui_one_closed_shape_per_tile_svg),12,MUTED,false));
    }
    /** Artist-facing controls; keep the 2–8 swatches in the existing color workspace. */
    private void layeredControls(LinearLayout host){
        if(fishScaleMode){
            tessellationGapSlider(host,"Gap between scales · mm");
            section(host,"Fish Scale Mosaic Portrait · interlocking scallops");
            host.addView(text(getString(R.string.ui_rounded_bottom_scales_with_pointed_upper),12,MUTED,false));
            action(host,"Solid silhouette underlay: "+(layeredSolidBase?"On":"Off"),()->{
                layeredSolidBase=!layeredSolidBase;setToolInternal(1);requestAlternate();});
            action(host,"Raised edge accent sheet: "+(layeredExtraMotif?"On":"Off"),()->{
                layeredExtraMotif=!layeredExtraMotif;setToolInternal(1);requestAlternate();});
            slider(host,"Scale density",layeredColumns,10,40,1,v->{layeredColumns=Math.round(v);requestAlternate();});
            slider(host,"Photo sampling detail",layeredPhotoDetail*100,0,100,5,v->{layeredPhotoDetail=v/100f;requestAlternate();});
            if(layeredResult!=null&&alternateReadyGeneration==alternateGeneration)
                host.addView(text(layeredResult.summary(),12,ACCENT,false));
            host.addView(text("Scales only occupy their exposed row bands; edge accents use a separate physical sheet. No duplicate overlapping cuts within a sheet.",12,MUTED,false));
            return;
        }
        if(bottleCapMode){
            tessellationGapSlider(host,"Gap between caps · mm");
            section(host,"Bottle Cap Mosaic Portrait · staggered circles");
            host.addView(text(getString(R.string.ui_photo_sampled_circular_caps_each_color),12,MUTED,false));
            action(host,"Solid silhouette underlay: "+(layeredSolidBase?"On":"Off"),()->{
                layeredSolidBase=!layeredSolidBase;setToolInternal(1);requestAlternate();});
            action(host,"Raised rim highlight sheet: "+(layeredExtraMotif?"On":"Off"),()->{
                layeredExtraMotif=!layeredExtraMotif;setToolInternal(1);requestAlternate();});
            slider(host,"Cap density",layeredColumns,10,40,1,v->{layeredColumns=Math.round(v);requestAlternate();});
            slider(host,"Photo sampling detail",layeredPhotoDetail*100,0,100,5,v->{layeredPhotoDetail=v/100f;requestAlternate();});
            if(layeredResult!=null&&alternateReadyGeneration==alternateGeneration)
                host.addView(text(layeredResult.summary(),12,ACCENT,false));
            host.addView(text(getString(R.string.ui_circles_stay_separate_on_each_vinyl),12,MUTED,false));
            return;
        }
        tessellationGapSlider(host,"Gap between cells · mm");
        if(overprintCellKind>0){
            section(host,LayeredCellPortraitEngine.overprintName(overprintCellKind)+" · opaque layered portrait");
            host.addView(text("Solid Snip foundation plus independent, partly overlapping shapes on different vinyl sheets. No nested rings or cutout motifs.",12,MUTED,false));
            slider(host,"Cell density",layeredColumns,8,40,1,v->{layeredColumns=Math.round(v);requestAlternate();});
            slider(host,"Photo detail",layeredPhotoDetail*100,0,100,5,v->{layeredPhotoDetail=v/100f;requestAlternate();});
            slider(host,"Layer overlap",layeredArtDetail*100,0,100,5,v->{layeredArtDetail=v/100f;requestAlternate();});
            slider(host,"Marks per cell",layeredMotifDepth,1,3,1,v->{layeredMotifDepth=Math.round(v);requestAlternate();});
            host.addView(text(getString(R.string.ui_2_3_marks_use_distinct_upper),12,MUTED,false));
            if(layeredResult!=null&&alternateReadyGeneration==alternateGeneration)
                host.addView(text(layeredResult.summary(),12,ACCENT,false));
            return;
        }
        section(host,offsetCellMode?"Offset Cell Portrait · staggered mosaic":"Layered Cell Portrait · photo mosaic");
        if(offsetCellMode)host.addView(text(getString(R.string.ui_alternate_rows_shift_by_half_a),12,MUTED,false));
        host.addView(text(offsetCellMode?
            "Solid Snip silhouette underlay supports staggered photographic cells.":
            layeredSolidBase?
            "Solid Snip silhouette supports the portrait; upper colors refine photographic detail.":
            "Colored photographic cells fit together without a solid underlay.",12,MUTED,false));
        action(host,"Motif family: "+layeredFamilyName(layeredFamily)+"  ›",()->{
            String[] motifs={"Photo adaptive / Mixed","Ellipse","Rounded square","Diamond","Hexagon","Octagon","Capsule / Edge","Soft star","Scallop"};
            new AlertDialog.Builder(this).setTitle(getString(R.string.ui_mosaic_motif)).setSingleChoiceItems(motifs,layeredFamily,(d,index)->{
                layeredFamily=index;d.dismiss();setToolInternal(1);requestAlternate();}).setNegativeButton("Cancel",null).show();
        });

        if(!offsetCellMode){
            action(host,"Solid silhouette base: "+(layeredSolidBase?"On":"Off"),()->{
                layeredSolidBase=!layeredSolidBase;setToolInternal(1);requestAlternate();});
        }
        slider(host,"Cell density",layeredColumns,10,40,1,v->{layeredColumns=Math.round(v);requestAlternate();});
        slider(host,"Photo detail",layeredPhotoDetail*100,0,100,5,v->{layeredPhotoDetail=v/100f;requestAlternate();});
        slider(host,"Artistic detail",layeredArtDetail*100,0,100,5,v->{layeredArtDetail=v/100f;requestAlternate();});
        slider(host,"Motif depth",layeredMotifDepth,1,3,1,v->{layeredMotifDepth=Math.round(v);requestAlternate();});
        section(host,"Vinyl cutting · physical millimeters");

        // Show the effective cut-safe seam, not the obsolete 0–0.5 mm
        // target which was entirely overridden by the 0.56 mm default floor.
        final float seamFloor=StyleCutGeometryPreflight.exportGapMm(settings.bridgeMm,settings.minimumMm)+.06f;
        slider(host,"Color seam · mm (cut-safe)",Math.max(layeredSeamMm,seamFloor),
            seamFloor,Math.min(20.1f,Math.max(.5f,seamFloor+.50f)),.02f,v->{layeredSeamMm=v;requestAlternate();});
        host.addView(text(getString(R.string.ui_cut_material_sets_the_minimum_seam),12,MUTED,false));
        // Physical minimums belong in Export > Cut material (Custom only).
        if(layeredResult!=null&&alternateReadyGeneration==alternateGeneration)
            host.addView(text(layeredResult.summary(),12,ACCENT,false));
        host.addView(text(getString(R.string.ui_thin_or_complex_cuts_may_fail),12,MUTED,false));
    }
    private void applyLayeredLook(int look){
        checkpoint();
        if(look==0){layeredColumns=34;layeredPhotoDetail=.98f;layeredArtDetail=.16f;
            layeredMotifDepth=2;layeredMinFeatureMm=.50f;layeredSeamMm=.12f;tessGapMm=.28f;}
        else if(look==1){layeredColumns=26;layeredPhotoDetail=.74f;layeredArtDetail=.60f;
            layeredMotifDepth=3;layeredMinFeatureMm=.50f;layeredSeamMm=.12f;tessGapMm=.30f;}
        else {layeredColumns=18;layeredPhotoDetail=.72f;layeredArtDetail=.30f;
            layeredMotifDepth=2;layeredMinFeatureMm=.85f;layeredSeamMm=.20f;tessGapMm=.55f;}
        setToolInternal(1);requestAlternate();
    }
    /** Photograph-to-color suggestion only; never pretends that computed RGB colors are purchasable vinyl. */
    /** Photo colors are resolved asynchronously. Custom/manual palettes and loaded
     * presets are never silently overwritten by an in-flight quantization job.
     */
    private void suggestStipplePhotoColors(){
        if(!stippleActive()||photo==null)return;
        final Bitmap source=photo;final long revision=photoRevision;
        final long paletteRequest=++stipplePhotoPaletteRequest;
        final int count=tessLayerCount, fill=settings.snipFillMode;
        final SnipMask snips=settings.snips==null?null:settings.snips.copy();
        final boolean circle=settings.circleCrop;
        final int paper=settings.separationBackground; // photo palette excludes the separation background
        tessPalette="Photo-derived";
        diagnosticsExecutor.execute(()->{
            try{
                float factor=Math.min(1f,320f/Math.max(source.getWidth(),source.getHeight()));
                Bitmap thumb=factor>=1f?source:Bitmap.createScaledBitmap(source,
                    Math.max(12,Math.round(source.getWidth()*factor)),
                    Math.max(12,Math.round(source.getHeight()*factor)),true);
                if(fill==0)thumb=SpecialtyCutSafety.prepare(thumb,snips,circle);
                int w=thumb.getWidth(),h=thumb.getHeight();int[] rgba=new int[w*h];
                thumb.getPixels(rgba,0,w,0,0,w,h);
                int[] suggested=StipplePhotoPalette.derive(rgba,w,h,8,paper);
                postUi(()->{
                    if(photo!=source||photoRevision!=revision||!stippleActive()||
                       paletteRequest!=stipplePhotoPaletteRequest||
                       tessLayerCount!=count||!"Photo-derived".equals(tessPalette))return;
                    checkpoint();setTessBasePalette(suggested);
                    updateAutomaticSnipFoundation(suggested,rgba,true);
                    invalidateToolPanels();requestAlternate();
                });
            }catch(Exception ex){postUi(()->{
                if(photo==source&&stippleActive()&&"Photo-derived".equals(tessPalette))
                    toast("Photo palette: "+friendlyFailure(ex,"Unable to extract photo colors"));
            });}
        });
    }
    private long layeredPhotoPaletteRequest=0L;
    private void suggestLayeredPhotoColors(){
        if(!layeredCellMode||photo==null)return;
        final Bitmap source=photo;
        final long sourceRevision=photoRevision;
        final long paletteRequest=++layeredPhotoPaletteRequest;
        final int layerCount=tessLayerCount;
        final boolean overprint=overprintCellKind>0;
        final int renderKey=alternateGeneration;
        final SnipMask snips=settings.snips==null?null:settings.snips.copy();
        final boolean circle=settings.circleCrop;
        final int fill=settings.snipFillMode;
        try{diagnosticsExecutor.execute(()->{
            Bitmap scaled=null,thumbnail=null;
            try{
                int largest=Math.max(source.getWidth(),source.getHeight());
                float scale=Math.min(1f,384f/largest);
                scaled=scale==1?source:Bitmap.createScaledBitmap(source,
                    Math.max(12,Math.round(source.getWidth()*scale)),Math.max(12,Math.round(source.getHeight()*scale)),true);
                thumbnail=fill==0?SpecialtyCutSafety.prepare(scaled,snips,circle):scaled;
                int w=thumbnail.getWidth(),h=thumbnail.getHeight();
                int[] pixels=new int[w*h];thumbnail.getPixels(pixels,0,w,0,0,w,h);
                boolean[] valid=new boolean[pixels.length];
                for(int i=0;i<pixels.length;i++)valid[i]=(pixels[i]>>>24)>=128;
                int[] colors=PhotoPaletteOptimizer.optimize(pixels,valid,
                    overprint?8:layerCount).colors; // M4/D12
                postUi(()->{
                    if(isFinishing()||isDestroyed()||photo!=source||sourceRevision!=photoRevision||
                       paletteRequest!=layeredPhotoPaletteRequest||!layeredCellMode)return;
                    if(layerCount!=tessLayerCount||overprint!=(overprintCellKind>0)||renderKey!=alternateGeneration){
                        toast(getString(R.string.photo_suggestion_skipped));return;
                    }
                    checkpoint();int[] candidate=tessColors.clone();
                    for(int i=0;i<(overprint?8:layerCount);i++)candidate[i]=colors[i];
                    if(overprint)candidate=OpaqueOverlayPhotoPalette.arrange(candidate,layerCount,pixels);
                    else if(offsetCellMode||layeredSolidBase)
                        candidate=FoundationColorPolicy.anchorPhotoPalette(candidate,layerCount,pixels,false);
                    setTessBasePalette(candidate);
                    tessPalette="Photo suggested";
                    setTool(2);requestAlternate();
                    toast(getString(R.string.ui_photo_colors_suggested_match_swatches_to));
                });
            }catch(OutOfMemoryError oom){postUi(()->{
                if(!isFinishing()&&!isDestroyed()&&photo==source&&sourceRevision==photoRevision&&
                    paletteRequest==layeredPhotoPaletteRequest&&layeredCellMode)toast(getString(R.string.photo_suggestion_memory));
            });}catch(Exception e){postUi(()->{
                if(!isFinishing()&&!isDestroyed()&&photo==source&&sourceRevision==photoRevision&&
                    paletteRequest==layeredPhotoPaletteRequest&&layeredCellMode)toast("Photo colors: "+friendlyFailure(e,"Unable to update photo colors"));
            });}finally{
                if(thumbnail!=null&&thumbnail!=scaled&&thumbnail!=source&&!thumbnail.isRecycled())thumbnail.recycle();
                if(scaled!=null&&scaled!=source&&!scaled.isRecycled())scaled.recycle();
            }
        });}catch(java.util.concurrent.RejectedExecutionException busy){toast(getString(R.string.photo_suggestion_busy));}
    }
    private String layeredFamilyName(int f){
        String[] names={"Photo adaptive / Mixed","Ellipse","Rounded square","Diamond","Hexagon","Octagon","Capsule / Edge","Soft star","Scallop"};
        return names[Math.max(0,Math.min(names.length-1,f))];
    }
    private void selectLayeredLayer(int index){
        tessSelectedLayer=index;
        requestSelectedPreview();
        setToolInternal(activeWorkspace==4?3:2);
    }
    private SpecialtyPreviewSettings captureSpecialtyPreviewSettings(){
        return new SpecialtyPreviewSettings(settings.paper,settings.widthMm,continuousStrokeMm,
            stippleHighlightCollisions,mazeShowSolution,tonalCutLayerPreview,tonalSelectedLayer);
    }
    private Bitmap renderLayeredPreview(LayeredCellPortraitEngine.Result result,int maxSide,int selected,SpecialtyPreviewSettings presentation){
        float scale=Math.min(1f,maxSide/(float)Math.max(result.width,result.height));
        int w=Math.max(1,Math.round(result.width*scale)),h=Math.max(1,Math.round(result.height*scale));
        Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.drawColor(presentation.paper);
        canvas.scale(w/(float)result.width,h/(float)result.height);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setStyle(Paint.Style.FILL);
        // C2: reuse native Path storage instead of allocating per vinyl piece.
        Path path=new Path();path.setFillType(Path.FillType.EVEN_ODD);
        // The generator groups marks by cell, NOT by physical sheet. Drawing
        // in source order lets a lower layer conceal a higher one in JPEG/PDF
        // previews, contrary to vector preview and SVG cut assembly.
        int pieceIndex=0;
        for(int layer=0;layer<result.options.layerCount;layer++){
            if(selected>=0&&layer!=selected)continue;
            for(LayeredCellPortraitEngine.Piece piece:result.pieces){
                if((pieceIndex++&63)==0)CancellationCheckpoint.check();
                if(piece.layer!=layer)continue;
                paint.setColor(result.options.colors[layer]);
                path.rewind();path.setFillType(Path.FillType.EVEN_ODD);
                addLayeredRing(path,piece.outer);
                for(float[] hole:piece.holes)addLayeredRing(path,hole);
                canvas.drawPath(path,paint);
            }
        }
        return bitmap;
    }
    private static void addLayeredRing(Path path,float[] xy){
        for(int j=0;j<xy.length;j+=2){if(j==0)path.moveTo(xy[j],xy[j+1]);else path.lineTo(xy[j],xy[j+1]);}
        path.close();
    }
    private void selectTessLayer(int index){
        tessSelectedLayer=index;
        requestSelectedPreview();
        setToolInternal(activeWorkspace==4?3:2);
    }
    private void chooseTessPalette(){
        final String[] choices={"Studio Portrait · white","Illuminated Portrait · black","Balanced Eight · either","Vivid","Ocean","Vintage","CMYK"};
        new AlertDialog.Builder(this).setTitle("Tessellation vinyl palette")
            .setItems(choices,(dialog,which)->{
                final int[][] palettes={
                    {0xff20252c,0xff3b4565,0xff24727c,0xffb34e45,0xffc66a3e,0xffb28b3b,0xffb86a86,0xff63753b},
                    {0xfff4eee2,0xffddd8ca,0xfff0cf6f,0xfff6ac8b,0xfff16c64,0xff7dd8b2,0xff72b8e8,0xffb9a0ed},
                    {0xffb74b43,0xffb77735,0xff98833b,0xff288963,0xff1d8499,0xff4673bd,0xff8b61b0,0xffb25291},
                    {0xff191b2a,0xff6e3d9e,0xffc85088,0xfff09d57,0xfff1cf6b,0xff2c84b8,0xff36c1b8,0xffecdede},
                    {0xff162c40,0xff235276,0xff3687ad,0xff70b9bf,0xffc9eddf,0xff47769e,0xffa3cdd6,0xfff8eee0},
                    {0xff2a2c32,0xff794342,0xffc46b52,0xffdfa777,0xfff3d9aa,0xff537d77,0xffa0787b,0xffefd9c4},
                    {0xff181818,0xffdf0077,0xff00a4db,0xffffdd00,0xfff3e4ca,0xff854bb9,0xfff06d46,0xff39aa91}
                };
                checkpoint();tessPalette=choices[which];
                setTessBasePalette(layeredCellMode&&overprintCellKind>0?
                    OpaqueOverlayPhotoPalette.arrange(palettes[which],tessLayerCount,preparedPhotoColorPixels):
                    palettes[which]);
                setToolInternal(1);requestAlternate();
            }).show();
    }
    private void editTessColor(int idx){
        if(idx<0||idx>=(newStyleActive()?newStyleColorCount:tessLayerCount))return;
        final int old=tessColors[idx];
        VinylColorEditor editor=new VinylColorEditor(this,old);
        new AlertDialog.Builder(this).setTitle("Layer "+(idx+1)+" · vinyl color")
            .setView(scrollColorEditor(editor)).setNegativeButton("Cancel",null)
            .setPositiveButton("Apply",(dialog,which)->{
                final int color;
                try{color=editor.getColorOrThrow();}
                catch(IllegalArgumentException ex){toast(getString(R.string.ui_could_not_apply_the_selected_vinyl));return;}
                checkpoint();preserveManualTessColor(idx,color);tessPalette="Custom";
                selectedPaletteKey="";
                invalidateToolPanels();
                if(activeWorkspace==3&&tool==2)refreshShelfPanel();
                else if(activeWorkspace==2&&tool==1)refreshShelfPanel();
                else setToolInternal(activeWorkspace==4?3:2);
                requestAlternate();
            }).show();
    }
    private Bitmap renderTessPreview(TessellationEngine.Result result,int maxSide,int selected,SpecialtyPreviewSettings presentation){
        float scale=Math.min(1f,maxSide/(float)Math.max(result.width,result.height));
        int w=Math.max(1,Math.round(result.width*scale)),h=Math.max(1,Math.round(result.height*scale));
        Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
        canvas.drawColor(presentation.paper);canvas.scale(w/(float)result.width,h/(float)result.height);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setStyle(Paint.Style.FILL);
        Path path=new Path();path.setFillType(Path.FillType.EVEN_ODD);
        int visited=0;
        for(int physicalOrder=0;physicalOrder<(result.stipple==null?1:result.options.layerCount);physicalOrder++){
          int stackLayer=result.stipple==null?-1:result.stipple.options.order[physicalOrder];
          for(TessellationEngine.Tile tile:result.tiles){
            if((visited++&63)==0)CancellationCheckpoint.check();
            if(stackLayer>=0&&tile.layer!=stackLayer)continue;
            if(selected>=0&&tile.layer!=selected)continue;
            paint.setColor(result.options.colors[tile.layer]);
            path.rewind();path.setFillType(Path.FillType.EVEN_ODD);
            for(int i=0;i<tile.xy.length;i+=2){if(i==0)path.moveTo(tile.xy[i],tile.xy[i+1]);else path.lineTo(tile.xy[i],tile.xy[i+1]);}
            path.close();
            for(float[] hole:tile.holes){for(int i=0;i<hole.length;i+=2){if(i==0)path.moveTo(hole[i],hole[i+1]);else path.lineTo(hole[i],hole[i+1]);}path.close();}
            canvas.drawPath(path,paint);
          }
        }
        if(result.stipple!=null&&presentation.collisions&&result.stipple.options.overlap!=MultiColorStippleEngine.OVERPRINT){
            Paint ring=new Paint(Paint.ANTI_ALIAS_FLAG);ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(Math.max(1.5f,2.5f/scale));ring.setColor(0xffff4242);
            int shown=0;for(MultiColorStippleEngine.Collision hit:result.stipple.originalOverlaps){
                if(selected>=0&&hit.a!=selected&&hit.b!=selected)continue;
                canvas.drawCircle(hit.x,hit.y,Math.max(2f,3f/scale),ring);
                if(++shown>500)break;
            }
        }
        return bitmap;
    }
    private Bitmap renderTessCollisionPreview(TessellationEngine.Result result,int maxSide,SpecialtyPreviewSettings presentation){
        Bitmap base=renderTessPreview(result,maxSide,-1,presentation);
        if(result.stipple==null||result.stipple.options.overlap==MultiColorStippleEngine.OVERPRINT)return base;
        Canvas c=new Canvas(base);float sx=base.getWidth()/(float)result.width,sy=base.getHeight()/(float)result.height;
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(0xffff3030);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);
        for(MultiColorStippleEngine.Collision hit:result.stipple.originalOverlaps){
            c.drawCircle(hit.x*sx,hit.y*sy,4,p);
        }
        return base;
    }

    private void tonalDetailControl(LinearLayout host){
        final int count=tonalLayerCount;
        slider(host,"Level of detail · "+count+" layers",tonalDetailByCount[count],0,100,5,
            value->{tonalDetailByCount[count]=Math.round(value);requestAlternate();});
        host.addView(text("Lower = broader, cleaner vinyl regions. Higher = more photographic detail. Each layer count remembers its setting.",12,MUTED,false));
    }
    private void tonalPicker(LinearLayout host){
        if(!tonalStencilMode)return;
        host.addView(text(getString(R.string.ui_stacked_vinyl_the_first_cut_is),12,MUTED,false));
        action(host,tonalMatchMyVinyl?"Colors: Match my vinyl rolls":"Colors: From photo",()->{
            if(!tonalMatchMyVinyl){
                for(int i=0;i<tonalLayerCount;i++)if(tonalColorOverrides[i]==0){
                    new AlertDialog.Builder(this).setTitle(getString(R.string.ui_select_your_vinyl_colors))
                        .setMessage(getString(R.string.ui_choose_a_palette_or_set_a))
                        .setPositiveButton("OK",null).show();return;
                }
            }
            checkpoint();tonalMatchMyVinyl=!tonalMatchMyVinyl;
            tonalBaseSource=-1;tonalSelectedLayer=-1;
            requestAlternate();invalidateToolPanels();setToolInternal(3);
        });
        action(host,tonalAutoWhiteBackground?"Background: Remove plain white (auto)":"Background: Keep full photo",
            ()->{checkpoint();tonalAutoWhiteBackground=!tonalAutoWhiteBackground;requestAlternate();setToolInternal(3);});
        action(host,tonalBaseSource<0?"Base color: Auto":"Base color: Source color "+(tonalBaseSource+1),
            ()->showTonalBaseChooser());
        action(host,tonalCutLayerPreview?"Single-layer view: Actual cut vinyl":"Single-layer view: Top-visible color only",
            ()->{tonalCutLayerPreview=!tonalCutLayerPreview;selectTonalLayer(tonalSelectedLayer);});
        host.addView(text(getString(R.string.ui_cut_layer_shows_what_will_be),12,MUTED,false));


        if(tonalResult!=null){
            StringBuilder report=new StringBuilder("Vector layers ");int populated=0;
            for(int i=0;i<tonalResult.layers.size();i++)if(!tonalResult.isEmpty(i))populated++;
            report.append(populated).append('/').append(tonalLayerCount).append(" populated");
            if(tonalSelectedLayer>=0&&tonalSelectedLayer<tonalResult.layers.size()){
                int i=tonalSelectedLayer;
                report.append(" · L").append(i+1).append(" #").append(String.format(Locale.US,"%06X",tonalResult.layers.get(i).color&0xffffff))
                    .append(" · ").append(tonalVectorContourCount(tonalResult,i)).append(" vector contours");
            }
            host.addView(text(report.toString(),12,ACCENT,false));
        }
    }
    private int tonalSourceIndex(int displayIndex){
        MultilayerStencilEngine.Result result=tonalResult;
        return result!=null&&result.layers.size()==tonalLayerCount&&displayIndex>=0&&displayIndex<result.layers.size()
                ?result.layers.get(displayIndex).sourceIndex:displayIndex;
    }
    private void showTonalBaseChooser(){
        final int count=tonalLayerCount;
        String[] labels=new String[count+1];labels[0]="Auto (choose broadest useful color)";
        for(int i=0;i<count;i++){
            int color=tonalColorOverrides[i];
            if(color==0&&tonalResult!=null)for(MultilayerStencilEngine.Layer layer:tonalResult.layers)
                if(layer.sourceIndex==i){color=layer.color;break;}
            labels[i+1]="Source color "+(i+1)+(color!=0?" · "+colorHex(color):"");
        }
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_choose_bottom_support_color))
            .setSingleChoiceItems(labels,Math.max(0,tonalBaseSource+1),(dialog,which)->{
                checkpoint();tonalBaseSource=which-1;dialog.dismiss();tonalSelectedLayer=-1;
                requestAlternate();setToolInternal(3);
            }).setNegativeButton("Cancel",null).show();
    }
    private void editTonalColor(int index){
        if(index<0||index>=tonalLayerCount)return;
        final int sourceIndex=tonalSourceIndex(index);
        int initial=tonalDisplayedColors()[index];
        VinylColorEditor editor=new VinylColorEditor(this,initial);
        new AlertDialog.Builder(this).setTitle("Layer "+(index+1)+" · vinyl color")
            .setView(scrollColorEditor(editor)).setNegativeButton("Cancel",null)
            .setNeutralButton(getString(R.string.ui_use_source_color),(dialog,which)->{
                checkpoint();tonalPaletteRanks=SmartPaletteReduction.identity(8);tonalColorOverrides[sourceIndex]=0;requestAlternate();
                invalidateToolPanels();setToolInternal(activeWorkspace==4?3:2);
            })
            .setPositiveButton("Apply",(dialog,which)->{
                final int color;
                try{color=editor.getColorOrThrow();}
                catch(IllegalArgumentException ex){toast(getString(R.string.ui_could_not_apply_the_selected_vinyl));return;}
                checkpoint();tonalPaletteRanks=SmartPaletteReduction.identity(8);tonalColorOverrides[sourceIndex]=PaletteHueShift.rotateOne(color,-tonalHueDegrees);
                selectedPaletteKey="";
                requestAlternate();invalidateToolPanels();setToolInternal(activeWorkspace==4?3:2);
            }).show();
    }
    private void selectTonalLayer(int index){
        tonalSelectedLayer=index;
        requestSelectedPreview();
        setToolInternal(activeWorkspace==4?3:2);
    }
    private static int tonalVectorContourCount(MultilayerStencilEngine.Result result,int index){
        return result.isEmpty(index)?0:result.cutContourCount(index);
    }
    /** Selection does not rasterize 1200 px artwork on the Android main thread. */
    private void requestSelectedPreview(){
        final int request=++previewRequestId;
        renderController.cancel(StudioRenderController.Lane.PREVIEW);
        
        final int token=alternateReadyGeneration;
        if(token!=alternateGeneration)return;
        final LayeredCellPortraitEngine.Result cell=layeredResult;
        final TessellationEngine.Result tess=tessellationResult;
        final MultilayerStencilEngine.Result tonal=tonalResult;
        final int selection=tonalStencilMode?tonalSelectedLayer:tessSelectedLayer;
        final SpecialtyPreviewSettings presentation=captureSpecialtyPreviewSettings();
        if(cell==null&&tess==null&&tonal==null)return;
        if((stippleActive()&&tess!=null&&stippleVectorCache!=null&&stippleVectorCache.source==tess)
            || (specialtyVectorCache!=null&&
                (specialtyVectorCache.source==tonal||specialtyVectorCache.source==tess||specialtyVectorCache.source==cell))){
            artwork.invalidate();return; // Layer selection only changes the vector draw order.
        }
        renderController.submit(StudioRenderController.Lane.PREVIEW,()->{
            try{
                Bitmap bmp=cell!=null?renderLayeredPreview(cell,1200,selection,presentation):
                    tess!=null?renderTessPreview(tess,1200,selection,presentation):renderTonalPreview(tonal,1200,selection,presentation);
                if(Thread.currentThread().isInterrupted())return;
                postUi(()->{
                    if(request!=previewRequestId||token!=alternateGeneration||token!=alternateReadyGeneration)return;
                    alternatePreviewBitmap=bmp;
                    if(tonal!=null)tonalDisplayBitmap=bmp;
                    if(artwork!=null)artwork.invalidate();
                });
            }catch(CancellationException ignored){
                // User selected another layer before compositing completed.
            }catch(RuntimeException | OutOfMemoryError error){
                android.util.Log.e("VinylHalftone","Layer preview failed",error);
                postUi(()->{if(request==previewRequestId&&token==alternateGeneration&&status!=null)
                    status.setText(R.string.status_layer_preview_unavailable);});
            }
        });
    }
    /** RC57: bounded-size thumbnail raster. No full-resolution overlay or intermediate
     * Bitmap is needed just to display a <= 1600px preview. SVG masks are unchanged. */
    private Bitmap renderTonalPreview(MultilayerStencilEngine.Result tones,int maxSide,int selected,SpecialtyPreviewSettings presentation){
        int n=tones.layers.size();
        int[] colors=new int[n];
        boolean[][] masks=new boolean[n][];
        for(int li=0;li<n;li++){
            MultilayerStencilEngine.Layer layer=tones.layers.get(li);
            colors[li]=layer.color;
            masks[li]=selected>=0&&!presentation.cutLayer?layer.visibleMask:layer.mask;
        }
        BoundedTonalPreview.Result preview=BoundedTonalPreview.compose(
            tones.width,tones.height,maxSide,presentation.paper,colors,masks,selected);
        Bitmap image=Bitmap.createBitmap(preview.width,preview.height,Bitmap.Config.ARGB_8888);
        image.setPixels(preview.argb,0,preview.width,0,0,preview.width,preview.height);
        return image;
    }

    /** The source image remains immutable; stale background results are discarded. */
    private final Runnable alternateDraftDebounce=()->startAlternateRender(true);
    private void requestAlternate(){requestAlternate(false);}
    private void requestAlternate(boolean registrationOnly){
        projectReviewProgress.invalidate();
        if(!registrationOnly)registrationRenderPending=false;
        stencilNeedsRender=true;
        // The current slider's TextViews remain valid during a drag. Rebuild only
        // after commit instead of discarding up to dozens of cached panels/second.
        if(!sliderDragging)invalidateToolPanels();
        if(sliderDragging){
            // Settings changed: the old final render must be immediately
            // ineligible for export, even before the debounced draft begins.
            alternateReadyGeneration=-1;
            alternatePreviewDraft=true;
            alternateGeneration++;
            if(activeWorkspace==4&&tool==3)updateVisibleLayerCutLabels();
            renderController.cancel(StudioRenderController.Lane.SPECIALTY);
            handler.removeCallbacks(alternateDraftDebounce);
            if(RenderDraftPolicy.supportsSpecialtyDraft(mazePortraitMode,newStyleActive()?newStyleId:-1))
                handler.postDelayed(alternateDraftDebounce,RenderDraftPolicy.HEAVY_DEBOUNCE_MS);
            // For Maze/TSP retain the last frame until the final render on release.
            return;
        }
        handler.removeCallbacks(alternateDraftDebounce);
        startAlternateRender(false);
    }
    /** The additional art styles all use one render lane and one vector geometry model. */
    private void startNewStyleRender(boolean draft){
        if(!newStyleActive()||photo==null){setRenderBusy(false);return;}
        setRenderBusy(true);
        cancelNewStyleCutPreparation(); // D7: stale cut preparation must never publish
        final int token=++alternateGeneration;
        final Bitmap source=photo;
        final long revision=photoRevision;
        final SnipMask snips=settings.snips==null?null:settings.snips.copy();
        // Multilayer Text needs the *physical Snip silhouette* for its first
        // vinyl sheet. A white/black visual Snip fill is never allowed to
        // resurrect removed pixels as a solid underlay.
        final int fillMode=(newStyleId==VinylStyleSuite.TEXT||
            newStyleId==VinylStyleSuite.TESSELLATION_MOTIF||
            AutoSilhouetteUnderlay.specialtyEligible(newStyleId,newStyleVariant,false))?0:settings.snipFillMode;
        final boolean circular=settings.circleCrop;
        final VinylEngine.Settings adjustments=settings.copy();
        final int[] underlayPixels=pixels;
        final int underlayW=imageW,underlayH=imageH,underlayColor=autoSilhouetteColor;
        final VinylStyleSuite.Config config=new VinylStyleSuite.Config();
        config.style=newStyleId;config.variant=newStyleVariant;
        // Legacy saved motif preferences remain readable but no longer affect tile geometry.
        config.detail=draft?Math.min(newStyleDetail,40):newStyleDetail;
        config.draft=draft;
        config.colorCount=Math.max(newStyleId==VinylStyleSuite.GLASS?3:2,
            Math.min(NewStyleColorSlots.maximumSelectable(newStyleId),newStyleColorCount));config.widthMm=settings.widthMm;
        config.leadMm=newStyleLeadMm;config.weightMm=newStyleWeightMm;
        config.prismaticGapMm=prismaticSpacingMm;
        config.trioSpacingMm=trioSpacingMm;config.trioOrganic=trioOrganic;
        config.flowStrength=VinylStyleSuite.isOrganicFlow(newStyleId)?newStyleWeightMm:1f;
        config.organicSmoothing=OrganicFlowCutSmoother.DEFAULT;
        if(VinylStyleSuite.isPopPoster(newStyleId)){
            config.popPoster=popPosterOptions().copy();
            config.popPoster.toneCount=config.colorCount;
            config.popPoster.textRenderer=PopPosterTextMask::create;
        }
        config.prismaticIrregularity=prismaticIrregularity;
        config.prismaticRoundness=prismaticRoundness;
        config.prismaticFeature=prismaticFeature;config.prismaticTone=prismaticTone;
        config.minimumMm=settings.minimumMm;config.paper=settings.separationBackground; // D5: not the mounting surface
        // M4 gap contract: design to the same weed gap the cut-ready stage and export enforce.
        config.gapMm=VinylStyleSuite.generationGapMm(settings.bridgeMm,settings.minimumMm);
        config.leadColor=newStyleColors[0];config.words=newStyleWords;
        if(config.style==VinylStyleSuite.TESSELLATION_MOTIF||
            !NewStyleColorSlots.multi(config.style)||newStyleMatchVinyl)
            config.palette=NewStyleColorSlots.fixedPalette(config.style,config.variant,
                config.colorCount,newStyleColors);
        alternateReadyGeneration=-1;alternatePreviewDraft=true;
        continuousBladeContours=-1;continuousPenPaths=-1;mazeWallSegments=-1;
        // Keep last good visible artwork until the new draft/final arrives.
        // Export gating uses alternateReadyGeneration, never these cached paths.
        newStyleOperations=null;showCutRiskMarkers=false;
        // On the first selection, show real provisional artwork before the more
        // expensive final vector/cut render. Draft remains strictly ineligible
        // for export and cut warnings. Subsequent slider adjustments keep the
        // last visible frame while a new final render is produced.
        // Reuse a visible preview of this photo/style/variant rather than doing
        // a second provisional geometry pass after every slider release.
        final boolean usableOrganicPreview=newStyleResult!=null&&!newStyleResult.layers.isEmpty()&&newStyleVectors!=null&&
            newStyleVectors.source==newStyleResult&&specialtyVisibleRevision==revision&&
            specialtyVisibleStyle==config.style&&specialtyVisibleVariant==config.variant;
        final boolean firstOrganicPreview=!draft&&VinylStyleSuite.isOrganicFlow(config.style)&&!usableOrganicPreview;
        renderController.cancel(StudioRenderController.Lane.SPECIALTY);
        renderController.submit(StudioRenderController.Lane.SPECIALTY,()->{
            final long renderStart=android.os.SystemClock.elapsedRealtime();
            try{
                if(firstOrganicPreview){
                    try{
                        SpecialtyPrepared quick=specialtyPrepared(revision,source,snips,
                            fillMode,circular,true,adjustments);
                        if(token!=alternateGeneration||Thread.currentThread().isInterrupted())return;
                        VinylStyleSuite.Config light=config.copy();
                        light.draft=true;
                        light.detail=Math.min(config.detail,40);
                        int dw=quick.bitmap.getWidth(),dh=quick.bitmap.getHeight();
                        light.allowed=new boolean[dw*dh];
                        float dHeight=light.widthMm*dh/(float)dw;
                        for(int y=0;y<dh;y++){
                            if((y&31)==0)StyleKit.checkCancelled();
                            for(int x=0;x<dw;x++){
                                int i=y*dw+x;
                                float mx=(x+.5f)*light.widthMm/dw;
                                float my=(y+.5f)*dHeight/dh;
                                light.allowed[i]=((quick.pixels[i]>>>24)>=128)&&
                                    mx>=adjustments.marginMm&&my>=adjustments.marginMm&&
                                    mx<=light.widthMm-adjustments.marginMm&&
                                    my<=dHeight-adjustments.marginMm;
                            }
                        }
                        StyleKit.Result provisional=VinylStyleSuite.generate(quick.pixels,dw,dh,light);
                        NewStyleVectorCache quickVectors=new NewStyleVectorCache(provisional);
                        if(token!=alternateGeneration||Thread.currentThread().isInterrupted())return;
                        postUi(()->{
                            // A queued draft must NEVER overwrite a newer final.
                            if(token!=alternateGeneration||
                                (alternateReadyGeneration==token&&!alternatePreviewDraft))return;
                            newStyleResult=provisional;newStyleVectors=quickVectors;
                            specialtyVisibleRevision=revision;specialtyVisibleStyle=config.style;specialtyVisibleVariant=config.variant;
                            newStyleOperations=null;
                            alternateReadyGeneration=token;
                            alternatePreviewDraft=true;
                            alternateStatus="Draft visible · generating final cut geometry";
                            artwork.invalidate();
                            if(status!=null)status.setText(alternateStatus);
                        });
                    }catch(java.util.concurrent.CancellationException cancelled){throw cancelled;
                    }catch(RuntimeException | OutOfMemoryError quickError){
                        // Draft is opportunistic. A preview failure must never
                        // prevent the authoritative final render from running.
                        android.util.Log.w("VinylNewStyles","Provisional preview unavailable",quickError);
                    }
                }
                android.util.Log.i("VinylNewStyles","Starting final render style="+config.style+
                    " generation="+token+" elapsedMs="+
                    (android.os.SystemClock.elapsedRealtime()-renderStart));
                SpecialtyPrepared prep;
                android.os.Trace.beginSection("VinylStudio.NewStylePrepare");
                try{prep=specialtyPrepared(revision,source,snips,fillMode,circular,draft,adjustments);}
                finally{android.os.Trace.endSection();}
                if(token!=alternateGeneration||Thread.currentThread().isInterrupted())return;
                int w=prep.bitmap.getWidth(),h=prep.bitmap.getHeight();
                int[] rgb=prep.pixels;
                config.allowed=new boolean[w*h];
                float hm=config.widthMm*h/(float)w,margin=adjustments.marginMm;
                for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                    if((y&63)==0&&Thread.currentThread().isInterrupted())throw new CancellationException();
                    int i=y*w+x;
                    float mx=(x+.5f)*config.widthMm/w,my=(y+.5f)*hm/h;
                    config.allowed[i]=((rgb[i]>>>24)>=128)&&
                        mx>=margin&&my>=margin&&mx<=config.widthMm-margin&&my<=hm-margin;
                }
                final AutoSilhouetteUnderlay.Output generatedBase=
                    AutoSilhouetteUnderlay.specialtyEligible(config.style,config.variant,false)
                    &&snips!=null&&!snips.isEmpty()&&underlayPixels!=null?
                    makeNewStyleUnderlay(adjustments,underlayPixels,underlayW,underlayH,underlayColor):null;
                StyleKit.Result result;
                android.os.Trace.beginSection("VinylStudio.NewStyleGeometry");
                try{result=draft?VinylStyleSuite.generate(rgb,w,h,config):
                    StyleKit.withImageFieldCache(specialtyFieldCache,()->newStyleGeometryMemo.generate(rgb,w,h,config,()->VinylStyleSuite.generate(rgb,w,h,config)));}
                finally{android.os.Trace.endSection();}
                NewStyleVectorCache vectors=new NewStyleVectorCache(result);
                android.util.Log.d("VinylPerf","newstyle styleKey=newstyle:"+config.style+":"+config.variant+" draft="+draft+
                    " layers="+config.colorCount+" totalMs="+(android.os.SystemClock.elapsedRealtime()-renderStart));
                android.util.Log.i("VinylNewStyles","Style="+config.style+
                    " final vectors ready elapsedMs="+
                    (android.os.SystemClock.elapsedRealtime()-renderStart));
                // Count the exported closed loops and open polylines by stable
                // physical swatch slot on the render lane, not on the UI thread.
                StyleLayerOperations.Counts[] operations=draft?null:
                    config.style==VinylStyleSuite.POP_PANELS?null:
                    StyleLayerOperations.bySlot(result,config.style,config.variant,config.colorCount);
                if(token!=alternateGeneration||Thread.currentThread().isInterrupted())return;
                postUi(()->{
                    if(token!=alternateGeneration)return;
                    if(operations!=null&&newStyleSelectedLayer>=operations.length)newStyleSelectedLayer=-1;
                    newStyleResult=result;newStyleVectors=vectors;newStyleOperations=operations;
                    specialtyVisibleRevision=revision;specialtyVisibleStyle=config.style;specialtyVisibleVariant=config.variant;
                    specialtyUnderlay=generatedBase==null?null:generatedBase.recolor(autoSilhouetteColor);specialtyUnderlayGeneration=token;
                    alternatePreparedSource=prep.bitmap;
                    alternatePreparedWidth=w;alternatePreparedHeight=h;
                    alternateReadyGeneration=token;alternatePreviewDraft=draft;registrationRenderPending=false;
                    if(!draft)commitMarkReport();
                    newStyleCutStage=null;newStyleRenderVariant=config.variant;newStyleRenderColorCount=config.colorCount;
                    alternateCutWarnings=draft?"Draft only · release slider for cut checks.":
                        "Preparing cut-ready geometry in the background";
                    alternateStatus=result.summary+" · "+alternateCutWarnings;
                    artwork.invalidate();setRenderBusy(false);
                    if(status!=null)status.setText(alternateStatus);
                    if((activeWorkspace==4&&tool==3)||(activeWorkspace==2&&tool==1)){
                        refreshShelfPanel();
                    }
                    if(!draft)startNewStyleCutPreparation(token,result,config.style,config.variant,config.colorCount);
                });
            }catch(CancellationException cancelled){
                // A newer render replaces this one.
            }catch(OutOfMemoryError oom){
                android.util.Log.e("VinylNewStyles","Render out of memory",oom);
                draftSourceCache.clear();ScaleAwarePhotoSampler.clearLightCache();specialtyFieldCache.clear();GeometryTemplateCache.clear();newStyleGeometryMemo.clear();
                postUi(()->{if(token==alternateGeneration){
                    setRenderBusy(false);alternateStatus="Not enough memory for this style; lower Detail";
                    toast(alternateStatus);artwork.invalidate();
                }});
            }catch(Exception error){
                android.util.Log.e("VinylNewStyles","Render failed",error);
                postUi(()->{if(token==alternateGeneration){
                    setRenderBusy(false);alternateStatus="Render failed: "+friendlyFailure(error,"Unable to render these settings");
                    toast(alternateStatus);artwork.invalidate();
                    if(activeWorkspace==2){invalidateToolPanels();refreshShelfPanel();}
                }});
            }
        });
    }
    private void startAlternateRender(boolean draft){
        cancelStippleCutPreparation(); // D7: a stale cut stage must never publish
        if(newStyleActive()){startNewStyleRender(draft);return;}
        if(!alternateActive()||photo==null){setRenderBusy(false);return;}
        setRenderBusy(true);
        tonalCutCounts=null;tonalCutCountGeneration=-1;
        final int token=++alternateGeneration;
        continuousBladeContours=-1;continuousPenPaths=-1;mazeWallSegments=-1;
        showCutRiskMarkers=false;specialtyRiskSource=null;specialtyRiskMarkers=Collections.emptyList();
        if(activeWorkspace==4&&tool==3)updateVisibleLayerCutLabels();
        ++previewRequestId;
        renderController.cancel(StudioRenderController.Lane.PREVIEW);
        
        final Bitmap source=photo;
        final long sourceRevision=photoRevision;
        final SnipMask snips=settings.snips==null?null:settings.snips.copy();
        final int fillMode=settings.snipFillMode;
        final boolean circular=settings.circleCrop;
        final float cutInsetMm=settings.marginMm,outerSpacingMm=settings.outerSpacingMm;
        final ContinuousLineModes.LineStyle style=continuousStyle;
        final boolean mazeMode=mazePortraitMode;
        final boolean tessMode=tessellationMode;
        final boolean layeredMode=layeredCellMode, offsetMode=offsetCellMode, capMode=bottleCapMode, scaleMode=fishScaleMode;
        final int overprintMode=overprintCellKind;
        final int layerCols=layeredColumns,layerDepth=layeredMotifDepth,layerFamily=layeredFamily,layerBackdrop=layeredBackground;
        final boolean layerSolidBase=offsetCellMode||layeredSolidBase,
            layerExtraMotif=(capMode||scaleMode)&&layeredExtraMotif;
        // Mosaic and cell styles never generate below the cut settings the
        // export inspects against: minimum feature >= "Minimum cut width",
        // tile gap >= "Minimum gap / bridge" (plus rounding margin).
        final float cutGapFloor=StyleCutGeometryPreflight.exportGapMm(settings.bridgeMm,settings.minimumMm)+.06f;
        // Reconcile imported projects and updated material bridges before using
        // the value for preview, SVG, cut counts, or persistence.
        normalizeTessellationGap();
        final float layerPhoto=layeredPhotoDetail,layerArt=layeredArtDetail,layerSeam=Math.max(layeredSeamMm,cutGapFloor),
            layerMin=Math.max(layeredMinFeatureMm,settings.minimumMm+.05f);
        final int mosaicStyle=tessellationStyle, mosaicCount=tessLayerCount,mosaicDetail=tessDetail;
        final float mosaicGap=Math.max(tessGapMm,cutGapFloor),mosaicEdge=tessEdge;
        final int[] mosaicColors=tessColors.clone();
        final MultiColorStippleEngine.Options stippleSnapshot=stippleSettings.copy();
        // D8: a dot below the cut minimum is a sub-minimum feature; floor it at render.
        for(int i=0;i<stippleSnapshot.minDotMm.length;i++)
            stippleSnapshot.minDotMm[i]=PhysicalSliderFloor.effective(stippleSnapshot.minDotMm[i],PhysicalSliderFloor.featureMm(settings.minimumMm));
        stippleSnapshot.paper=settings.separationBackground; // D5: not the mounting surface
        final int[] mosaicLayerFamilies=layeredLayerFamilies.clone();
        final int mazeSize=mazeGrid,mazeLevel=mazeDifficulty;
        alternateReadyGeneration=-1;
        // Keep the previous vector cache visible during generation; its
        // generation token remains invalid for export and cut diagnostics.
        alternatePreviewDraft=true;
        alternateCutWarnings="";
        // Preserve the previous image while a new style is being generated.
        renderController.cancel(StudioRenderController.Lane.SPECIALTY);
        
        final int density=continuousDensity,count=tonalLayerCount,blur=tonalBlur;
        final float stencilDetail=tonalDetailByCount[count]/100f;
        final float strokeMm=continuousStrokeMm;
        final int curveDepthSetting=curveDepth;
        final float curvePhoto=curvePhotoInfluence,curveRound=curveCornerRounding,curveRotation=curveRotationDegrees;
        final int portraitMethod=curveToneMethod;
        final float portraitContrast=curveToneContrast,portraitShadow=curveShadowBoost,
            portraitHighlight=curveHighlightProtection,portraitEdge=curveEdgePriority;
        // D8: never generate ribbons, features or gaps below the selected material's limits.
        final float ribbonFeatureFloor=PhysicalSliderFloor.featureMm(settings.minimumMm);
        final float ribbonWidth=PhysicalSliderFloor.effective(curveRibbonWidthMm,Math.max(.55f,ribbonFeatureFloor)),
            ribbonFeature=PhysicalSliderFloor.effective(curveMinFeatureMm,ribbonFeatureFloor),
            ribbonGap=PhysicalSliderFloor.effective(curveMinGapMm,PhysicalSliderFloor.gapMm(settings.bridgeMm,settings.minimumMm));
        final boolean ribbonMode=curveVinylRibbon;

        final float edge=tonalEdge,width=settings.widthMm;
        final VinylEngine.Settings imageAdjustments=settings.copy();
        final int[] underlayPixels=pixels;
        final int underlayW=imageW,underlayH=imageH,underlayColor=autoSilhouetteColor;
        final int requestedBase=tonalBaseSource;
        final int requestedTonalHue=tonalHueDegrees;
        // M5: snapshot on the UI thread. The worker used to clone tonalColorOverrides and read
        // tonalMatchMyVinyl itself, racing with palette edits that System.arraycopy into the array.
        final int[] requestedTonalColors=tonalColorOverrides.clone();
        final boolean requestedTonalMatch=tonalMatchMyVinyl;
        final boolean removeWhite=tonalAutoWhiteBackground;
        final boolean useMarks=settings.registration;
        final int markStyle=settings.markStyle;
        final float markSize=settings.markSizeMm;
        final float markClearance=settings.registrationClearanceMm;
        final float markMinCut=settings.minimumMm;
        final float[][] markPositions=RegistrationMarks.copyPositions(settings.marks);
        renderController.submit(StudioRenderController.Lane.SPECIALTY,()->{
            try{
                if(token!=alternateGeneration||Thread.currentThread().isInterrupted())return;
                Path line=null;SpaceFillingCurves.Result curveGeometry=null;SpaceFillingRibbon.Result ribbonGeometry=null;SpiralPortraitRibbon.Result spiralGeometry=null;
                String curveCutIssue=null;
                MultilayerStencilEngine.Result tones=null;MazePortraitEngine.Result maze=null;TessellationEngine.Result mosaic=null;LayeredCellPortraitEngine.Result cellPortrait=null;
                long prepareStarted=System.nanoTime();
                // A real-device Perfetto trace separates cache/preparation
                // latency from geometry latency; never trace per-pixel loops.
                SpecialtyPrepared preparedSnapshot;
                android.os.Trace.beginSection("VinylStudio.SpecialtyPrepare");
                try{
                    preparedSnapshot=specialtyPrepared(sourceRevision,source,snips,fillMode,circular,draft,imageAdjustments);
                }finally{android.os.Trace.endSection();}
                Bitmap prepared=preparedSnapshot.bitmap;
                final Bitmap unlitPrepared=preparedSnapshot.unlit;
                final int[] preparedPixelsSnapshot=preparedSnapshot.pixels;
                if(token!=alternateGeneration||Thread.currentThread().isInterrupted())return;
                long engineStarted=System.nanoTime();
                final Bitmap lightingPreview=prepared==unlitPrepared?null:prepared;
                postUi(()->{if(token==alternateGeneration){
                    tonalPreview=lightingPreview;
                    if(tool==10&&artwork!=null)artwork.invalidate();
                }});
                if(token!=alternateGeneration)return;
                if(tessMode||layeredMode){
                    int w=prepared.getWidth(),h=prepared.getHeight();int[] colors=preparedPixelsSnapshot;
                    // The stipple preview ALWAYS uses a small input; full weighted Lloyd
                    // relaxation and final export coordinates are computed only at export.
                    if(tessMode&&mosaicStyle==TessellationEngine.STIPPLE&&Math.max(w,h)>350){
                        float factor=350f/Math.max(w,h);
                        Bitmap small=Bitmap.createScaledBitmap(prepared,Math.max(12,Math.round(w*factor)),Math.max(12,Math.round(h*factor)),true);
                        w=small.getWidth();h=small.getHeight();colors=new int[w*h];
                        small.getPixels(colors,0,w,0,0,w,h);
                    }
                    TessellationEngine.Options opts=tessMode?new TessellationEngine.Options():null;
                    if(tessMode){opts.style=mosaicStyle;opts.layerCount=mosaicCount;opts.detail=draft?Math.min(mosaicDetail,35):mosaicDetail;
                    opts.gapMm=mosaicGap;opts.edgeEmphasis=mosaicEdge;opts.colors=mosaicColors;
                    opts.widthMm=width;opts.outerSpacingMm=outerSpacingMm;opts.registration=useMarks;opts.markStyle=markStyle;opts.markSizeMm=markSize;
                    opts.registrationClearanceMm=markClearance;opts.minimumCutMm=markMinCut;
                    opts.marks=RegistrationMarks.copyPositions(markPositions);opts.circleCrop=circular;
                    if(mosaicStyle==TessellationEngine.STIPPLE){opts.stippleOptions=stippleSnapshot.copy();opts.stippleOptions.draft=true;}}
                    boolean[] allowed=null;
                    if(circular||cutInsetMm>0||(fillMode==0&&snips!=null&&!snips.isEmpty())){
                        allowed=new boolean[w*h];
                        for(int yy=0;yy<h;yy++)for(int xx=0;xx<w;xx++){
                            float nx=(xx+.5f)/w,ny=(yy+.5f)/h;
                            float px=nx*width,py=ny*(width*h/(float)w);
                            float hh=width*h/(float)w;
                            boolean keep=px>=cutInsetMm&&py>=cutInsetMm&&px<=width-cutInsetMm&&py<=hh-cutInsetMm;
                            if(keep&&circular){double rx=width*.5-cutInsetMm,ry=hh*.5-cutInsetMm;
                                keep=rx>0&&ry>0&&Math.pow((px-width*.5)/rx,2)+Math.pow((py-hh*.5)/ry,2)<=1; }
                            if(keep&&fillMode==0&&snips!=null)keep=snips.allows(nx,ny);
                            allowed[yy*w+xx]=keep;
                        }
                        if(tessMode)opts.allowedPixels=allowed;
                    }
                    if(tessMode)mosaic=TessellationEngine.generate(colors,w,h,opts);
                    else{
                        LayeredCellPortraitEngine.Options cells=new LayeredCellPortraitEngine.Options();
                        cells.columns=LayeredCellReleaseProfile.safeColumns(draft?Math.min(layerCols,18):layerCols,w,h,width,layerMin,mosaicGap,layerSolidBase);
                        cells.motifDepth=draft?Math.min(layerDepth,2):layerDepth;cells.family=layerFamily;cells.layerFamilies=mosaicLayerFamilies;
                        cells.photoDetail=layerPhoto;cells.ornamentation=layerArt;cells.colors=mosaicColors;
                        cells.solidBase=layerSolidBase;cells.detailMotifOverlay=layerExtraMotif;
                        cells.overprintKind=overprintMode;
                        cells.layerCount=mosaicCount;cells.widthMm=width;cells.gapMm=mosaicGap;
                        cells.seamGapMm=layerSeam;cells.minFeatureMm=layerMin;
                        cells.outerSpacingMm=outerSpacingMm;cells.registration=useMarks;
                        cells.markStyle=markStyle;cells.markSizeMm=markSize;
                        cells.registrationClearanceMm=markClearance;cells.minimumCutMm=markMinCut;
                        cells.marks=RegistrationMarks.copyPositions(markPositions);cells.circleCrop=circular;
                        cells.lockOuterToLayer=layerBackdrop<mosaicCount?layerBackdrop:-1;
                        cells.allowedPixels=allowed;
                        android.os.Trace.beginSection(overprintMode>0?
                            "VinylStudio.OverprintGeometry":"VinylStudio.CellGeometry");
                        try{
                            cellPortrait=scaleMode?FishScaleMosaicEngine.generate(colors,w,h,cells):
                                capMode?BottleCapMosaicEngine.generate(colors,w,h,cells):
                                offsetMode?OffsetCellPortraitEngine.generate(colors,w,h,cells):
                                LayeredCellPortraitEngine.generate(colors,w,h,cells);
                        }finally{android.os.Trace.endSection();}
                    }
                }else if(mazeMode){
                    MazePortraitEngine.Options opt=new MazePortraitEngine.Options();
                    opt.gridSize=draft?Math.min(mazeSize,40):mazeSize;opt.difficulty=mazeLevel;opt.diameterMm=width;
                    opt.brightnessBias=1.2f+mazeLevel*0.7f;
                    opt.seed=20261023L+mazeLevel*1709L;
                    maze=MazePortraitEngine.generate(prepared,opt);
                }else if(style!=null){
                    int family=ContinuousLineModes.curveFamily(style);
                    if(family>=0){
                        int w=prepared.getWidth(),h=prepared.getHeight();int[] photoPixels=preparedPixelsSnapshot;
                        int[] adjustedPixels=photoPixels;
                        SpaceFillingCurves.Options curveOptions=new SpaceFillingCurves.Options();
                        curveOptions.family=family;
                        curveOptions.depth=Math.min(draft?Math.min(curveDepthSetting,4):curveDepthSetting,
                            family==SpaceFillingCurves.SIERPINSKI?7:family==SpaceFillingCurves.MOORE?6:5);
                        curveOptions.circularCrop=circular;
                        curveOptions.width=w;curveOptions.height=h;
                        float safeMm=cutInsetMm+(ribbonMode?ribbonWidth*.5f:0f);
                        float minPhysicalSide=Math.min(width,width*h/(float)w);
                        if(safeMm>=minPhysicalSide*.22f)throw new IllegalArgumentException("Cut inset is too large for the curve artwork");
                        curveOptions.padding=Math.max(.042f,safeMm/Math.max(1f,minPhysicalSide));
                        curveOptions.rotationDegrees=curveRotation;curveOptions.cornerRounding=curveRound;
                        // Separate the mathematical traversal from its photographic
                        // rendering. The old 11% vertex shift left the entire page
                        // nearly uniformly black even with Influence at 100%.
                        curveOptions.photoInfluence=0f;
                        curveGeometry=SpaceFillingCurves.generate(curveOptions);
                        AdaptiveCurvePortrait.Options portraitOptions=new AdaptiveCurvePortrait.Options();
                        portraitOptions.method=portraitMethod;portraitOptions.influence=curvePhoto;
                        portraitOptions.contrast=portraitContrast;portraitOptions.shadowBoost=portraitShadow;
                        portraitOptions.highlightProtection=portraitHighlight;
                        portraitOptions.edgePriority=portraitEdge;portraitOptions.draft=draft;
                        portraitOptions.pixels=adjustedPixels;portraitOptions.width=w;portraitOptions.height=h;
                        portraitOptions.artworkWidthMm=width;
                        portraitOptions.structure=portraitCache.get(sourceRevision,adjustedPixels,w,h);
                        curveGeometry=AdaptiveCurvePortrait.render(curveGeometry,w,h,portraitOptions);
                        line=ContinuousLineModes.curvePath(curveGeometry);
                        if(ribbonMode){
                            SpaceFillingRibbon.Options cutOptions=new SpaceFillingRibbon.Options();
                            cutOptions.artworkWidthMm=width;cutOptions.maximumWidthMm=ribbonWidth;
                            cutOptions.draft=draft;
                            cutOptions.minimumFeatureMm=Math.min(ribbonFeature,ribbonWidth);
                            cutOptions.minimumGapMm=ribbonGap;cutOptions.photoInfluence=curvePhoto;
                            cutOptions.image=adjustedPixels;cutOptions.imageWidth=w;cutOptions.imageHeight=h;
                            cutOptions.toneContrast=portraitContrast;cutOptions.highlightProtection=portraitHighlight;
                            // A spacing/feature failure must never erase the
                            // finished centerline or strand the editor on a
                            // blank canvas. Show a non-exportable fallback with
                            // an explicit warning instead.
                            try{ribbonGeometry=SpaceFillingRibbon.generate(curveGeometry,w,h,cutOptions);}
                            catch(IllegalArgumentException unsafe){
                                if(Thread.currentThread().isInterrupted())return;
                                curveCutIssue=unsafe.getMessage();
                            }
                        }
                    }else if(style==ContinuousLineModes.LineStyle.SPIRAL){
                        SpiralPortraitRibbon.Options so=new SpiralPortraitRibbon.Options();
                        so.detail=draft?Math.max(1,Math.round(density*.65f)):density;
                        so.widthMm=width;so.minCutMm=Math.max(.45f,imageAdjustments.minimumMm);
                        so.minGapMm=Math.max(.45f,imageAdjustments.minimumMm*.60f);
                        so.insetMm=cutInsetMm;so.paper=imageAdjustments.separationBackground;so.draft=draft;
                        spiralGeometry=SpiralPortraitRibbon.generate(preparedPixelsSnapshot,
                            prepared.getWidth(),prepared.getHeight(),so);
                     }else line=ContinuousLineModes.generatePath(prepared,style,draft?Math.max(1,Math.round(density*.65f)):density,circular,width);
                }else{
                    final int w=prepared.getWidth(),h=prepared.getHeight();
                    // Scale once to the largest safe processing grid. Integer-step
                    // shrinking used to throw away substantially more detail than necessary.
                    double ratio=Math.min(1.0,Math.sqrt((double)MultilayerStencilEngine.MAX_PIXELS/((double)w*h)));
                    Bitmap small=ratio<1.0?Bitmap.createScaledBitmap(prepared,
                        Math.max(1,(int)Math.floor(w*ratio)),Math.max(1,(int)Math.floor(h*ratio)),true):prepared;
                    int[] pixels=new int[small.getWidth()*small.getHeight()];
                    small.getPixels(pixels,0,small.getWidth(),0,0,small.getWidth(),small.getHeight());
                    int[] backgroundReference=pixels;
                    // Lighting was already applied once, before dispatch to specialty styles.
                    if(prepared!=unlitPrepared){
                        Bitmap backgroundSmall=ratio<1.0?Bitmap.createScaledBitmap(unlitPrepared,
                            small.getWidth(),small.getHeight(),true):unlitPrepared;
                        backgroundReference=new int[pixels.length];
                        backgroundSmall.getPixels(backgroundReference,0,small.getWidth(),0,0,small.getWidth(),small.getHeight());
                    }
                    MultilayerStencilEngine.Options options=new MultilayerStencilEngine.Options();
                    options.layerCount=count;options.blurRadius=draft?Math.min(blur,2):blur;options.edgeStrength=edge;options.detail=stencilDetail;options.sourceColorMode=true;
                    options.baseLayer=requestedBase>=count?-1:requestedBase;
                    options.autoWhiteBackground=removeWhite;
                    options.backgroundReference=backgroundReference;
                    options.widthMm=width;options.cutInsetMm=cutInsetMm;options.outerSpacingMm=outerSpacingMm;options.registration=useMarks;
                    options.markStyle=markStyle;options.markDiameterMm=markSize;
                    options.registrationClearanceMm=markClearance;options.minimumCutMm=markMinCut;
                    options.marks=RegistrationMarks.copyPositions(markPositions);options.circleCrop=circular;
                    options.colors=requestedTonalColors.clone(); // 0 means source-derived; only explicit recolors override
                    options.paletteHueDegrees=requestedTonalHue; // Color-only change after ownership
                    options.matchVinylPalette=requestedTonalMatch;
                    if(options.matchVinylPalette)for(int i=0;i<count;i++)
                        if(options.colors[i]==0){options.matchVinylPalette=false;break;}
                    // Existing minimum cut-size setting is reused by preflight.
                    // Trap and smoothing are derived automatically; no extra sliders.
                    options.bridgeMode=false;
                    // Physical-area threshold, rather than hardcoded input pixels.
                    options.minimumIslandPixels=PhysicalFeatureRules.minimumIslandPixels(
                        0.10f,small.getWidth(),small.getHeight(),width,
                        width*small.getHeight()/(float)small.getWidth());
                    tones=MultilayerStencilEngine.generate(pixels,small.getWidth(),small.getHeight(),options);
                }
                if(token!=alternateGeneration)return;
                long previewStarted=System.nanoTime();
                // Prepare the SAME closed cut contours as export, once, off the UI thread.
                // All specialty styles are now drawn by Canvas paths; JPEGs are only
                // generated on explicit export, never as the finished main preview.
                final SpecialtyVectorCache specialtyVectors=spiralGeometry!=null?SpecialtyVectorCache.spiral(spiralGeometry,spiralGeometry.imageWidth,spiralGeometry.imageHeight,spiralGeometry.cutPath):
                    ribbonGeometry!=null?SpecialtyVectorCache.ribbon(ribbonGeometry):
                    cellPortrait!=null?SpecialtyVectorCache.layered(cellPortrait):
                    mosaic!=null&&mosaic.stipple==null?SpecialtyVectorCache.tessellation(mosaic):
                    tones!=null?SpecialtyVectorCache.tonal(tones):null;
                if(token!=alternateGeneration||Thread.currentThread().isInterrupted())return;
                final int completedBladeContours=spiralGeometry!=null?spiralGeometry.cutPieces:
                    ribbonGeometry!=null&&!draft?MultilayerStencilEngine.countUnionContours(
                        ribbonGeometry.mask,ribbonGeometry.width,ribbonGeometry.height):0;
                final int completedPenPaths=spiralGeometry!=null||ribbonGeometry!=null?0:
                    maze!=null?0:line!=null||curveGeometry!=null?1:0;
                final int completedMazeWalls=maze==null?0:maze.wallSegments;
                final Bitmap display=null;
                android.util.Log.d("VinylPerf", "specialty style="+(style==null?(layeredMode?"layered-cell":tessMode?"tessellation":mazeMode?"maze":"stencil"):style.name())
                    +" styleKey="+(style!=null?"single:"+style.name():layeredMode?"cell:"+(overprintMode>0?"overprint-"+overprintMode:offsetMode?"offset":capMode?"cap":scaleMode?"scale":"layered"):tessMode?"mosaic:"+mosaicStyle:mazeMode?"maze":"tonal")
                    +" draft="+draft+" prepareMs="+((engineStarted-prepareStarted)/1_000_000L)
                    +" engineMs="+((previewStarted-engineStarted)/1_000_000L)
                    +" previewMs="+((System.nanoTime()-previewStarted)/1_000_000L));
                final Path ready=line;final SpaceFillingCurves.Result spaceReady=curveGeometry;
                final SpaceFillingRibbon.Result ribbonReady=ribbonGeometry;
                final SpiralPortraitRibbon.Result spiralReady=spiralGeometry;
                final String ribbonIssue=curveCutIssue;
                final MultilayerStencilEngine.Result layers=tones;final MazePortraitEngine.Result mazeReady=maze;final TessellationEngine.Result mosaicReady=mosaic;final LayeredCellPortraitEngine.Result cellReady=cellPortrait;
                int tonalConcerns=0;
                if(layers!=null&&!draft){
                    float widthLimit=imageAdjustments.minimumMm;
                    float gapLimit=StyleCutGeometryPreflight.exportGapMm(imageAdjustments.bridgeMm,widthLimit);
                    for(int i=0;i<layers.layers.size();i++)if(!layers.isEmpty(i)){
                        CancellationCheckpoint.check();
                        if(VinylCutPreflight.inspect(layers,i,widthLimit,gapLimit).needsReview())tonalConcerns++;
                    }
                }
                final int completedTonalConcerns=tonalConcerns;
                final Bitmap preview=display;
                final StippleVectorCache vectors=(mosaicReady!=null && mosaicStyle==TessellationEngine.STIPPLE)
                    ?new StippleVectorCache(mosaicReady):null;
                final Bitmap sourceSnapshot=prepared;
                final AutoSilhouetteUnderlay.Output generatedBase=
                    (tessMode&&(mosaicStyle==TessellationEngine.STIPPLE||
                    mosaicStyle==TessellationEngine.VORONOI||mosaicStyle==TessellationEngine.GRID_MOTIF))
                    &&snips!=null&&!snips.isEmpty()&&underlayPixels!=null?
                    AutoSilhouetteUnderlay.make(imageAdjustments,underlayPixels,underlayW,underlayH,underlayColor):null;
                postUi(()->{if(token!=alternateGeneration)return;
                    continuousPreview=ready;curvePathResult=spaceReady;curveRibbonResult=ribbonReady;spiralRibbonResult=spiralReady;
                    curveDraftPreview=(spaceReady!=null&&draft);tonalResult=layers;tessellationResult=mosaicReady;stippleVectorCache=vectors;specialtyVectorCache=specialtyVectors;layeredResult=cellReady;tonalDisplayBitmap=null;alternatePreviewBitmap=preview;mazeResult=mazeReady;
                    specialtyUnderlay=generatedBase==null?null:generatedBase.recolor(autoSilhouetteColor);specialtyUnderlayGeneration=token;
                    alternatePreparedSource=sourceSnapshot;
                    alternatePreparedWidth=sourceSnapshot.getWidth();alternatePreparedHeight=sourceSnapshot.getHeight();
                    alternateReadyGeneration=token;
                    alternatePreviewDraft=draft;
                    registrationRenderPending=false;
                    continuousBladeContours=completedBladeContours;
                    continuousPenPaths=completedPenPaths;
                    mazeWallSegments=completedMazeWalls;
                    if(!draft){stencilNeedsRender=false;commitMarkReport();}
                    alternateCutWarnings=draft?"Draft only · release slider for cut checks.":spiralReady!=null?(spiralReady.draft?"Draft spiral: release slider to prepare cut SVG.":
                        spiralReady.hasTransparentMask?"Transparent photo: "+spiralReady.cutPieces+" separate cut piece(s) within the visible subject. Test weedability.":
                        "Variable-width spiral portrait. One connected vinyl ribbon; test cut."):
                        ribbonReady!=null?(ribbonReady.draft?"Draft preview: release slider to compute cut-ready ribbon.":"Unioned filled vinyl ribbon. Check narrow turns and test cut."):
                        ribbonIssue!=null?"Vinyl ribbon unavailable: "+ribbonIssue+" Showing centerline only; EXPORT DISABLED until resolved.":
                        (style!=null||mazeMode)?"Open wall/line centerlines; pen/engraving only. Do not use for weedable vinyl.":
                        "Inspect minimum features and perform a material test cut.";
                    if(completedTonalConcerns>0)alternateCutWarnings=completedTonalConcerns+" layers have fine-geometry cut risks; inspect cut-checks.csv after export.";
                    int populated=0;if(layers!=null)for(int li=0;li<layers.layers.size();li++)if(!layers.isEmpty(li))populated++;
                    alternateStatus=(cellReady!=null?cellReady.summary():mosaicReady!=null?mosaicReady.summary():mazeReady!=null?mazeReady.summary():layers!=null?"Source-color stencil "+populated+"/"+count+" layers populated · "+layers.status():"Continuous "+(style==null?"portrait":style.name())+" generated"+(spaceReady!=null?" · "+spaceReady.vertices+" vertices"+(ribbonReady!=null?" · cut silhouette ready":""):""))+" · "+alternateCutWarnings;
                    if(activeWorkspace==4 && (style!=null || mazeMode))refreshShelfPanel();
                    else if(activeWorkspace==4 && (layers!=null || cellReady!=null || mosaicReady!=null))
                        updateVisibleLayerCutLabels();
                    artwork.invalidate();setRenderBusy(false);if(status!=null)status.setText(alternateStatus);
                    if(!draft&&mosaicReady!=null&&mosaicReady.stipple!=null){
                        alternateStatus=mosaicReady.summary()+" · Preparing cut-ready dots in the background";
                        if(status!=null)status.setText(alternateStatus);
                        startStippleCutPreparation(token,mosaicReady);
                    }
                    // Tonal SVGs contain one compound <path> with many closed subpaths;
                    // count each physical blade loop on the diagnostics lane after preview.
                    if(layers!=null&&!draft){
                        tonalCutCounts=null;tonalCutCountGeneration=-1;
                        diagnosticsExecutor.execute(()->{
                            try{
                                int[] cuts=LayerCutCounts.tonal(layers);
                                postUi(()->{
                                    if(token!=alternateGeneration||tonalResult!=layers||alternatePreviewDraft)return;
                                    tonalCutCounts=cuts;tonalCutCountGeneration=token;
                                    if(activeWorkspace==4)updateVisibleLayerCutLabels();
                                });
                            }catch(java.util.concurrent.CancellationException ignored){}catch(RuntimeException error){
                                android.util.Log.e("VinylStudio","Cut-count analysis failed",error);
                            }
                        });
                    }
                });
            }catch(RuntimeException | OutOfMemoryError ex){
                if(token!=alternateGeneration||Thread.currentThread().isInterrupted()||ex instanceof CancellationException)return;
                if(ex instanceof OutOfMemoryError){
                    // Do not silently substitute lower-resolution physical cuts.
                    specialtyBaseCache.clear();specialtyLitCache.clear();specialtyFieldCache.clear();GeometryTemplateCache.clear();newStyleGeometryMemo.clear();
                }
                // ExecutorService otherwise captures fatal worker errors without
                // delivering them to the UI, leaving Rendering... displayed.
                android.util.Log.e("VinylStudio", "Specialty render failed: "+(style==null?"stencil":style.name()),ex);
                // D9: never show raw exception text; details stay in the log above.
                final String why=ex instanceof OutOfMemoryError?"not enough device memory. Lower Detail or the artwork size":
                    friendlyFailure(ex,"these settings could not be rendered. Try lower Detail or another size");
                postUi(()->{if(token==alternateGeneration){
                    alternateStatus="Render failed: "+why;
                    alternateReadyGeneration=-1;
                    setRenderBusy(false);if(status!=null)status.setText(alternateStatus);
                    if(editorSubtitle!=null)editorSubtitle.setText(alternateStatus);
                    toast(alternateStatus);
                    if(artwork!=null)artwork.invalidate();
                }});
            }
        });
    }
    private int alternateExportToken=-1;
    private float alternateExportWidthMm=0f;
    /** One physical-color sheet per populated layer; conservative artboard estimate. */
    private static String materialEstimate(float widthMm,float heightMm,int layers){
        float w=Math.max(0,widthMm),h=Math.max(0,heightMm);
        return String.format(Locale.US,
            "ARTBOARD / MATERIAL PLANNING (not a nesting layout)\n"+
            "Each vinyl color: %.1f mm × %.1f mm (%.2f in × %.2f in)\n"+
            "Layers: %d; nominal total sheet area: %.0f cm²\n"+
            "Add material for registration marks, carrier margins and weeding. " +
            "All SVGs must retain identical physical dimensions.\n",
            w,h,w/25.4f,h/25.4f,layers,w*h*layers/100f);
    }
    /** Specialty/new-style archives are staged BEFORE Android creates a file.
     * This closes the same empty-ZIP failure that was repaired for standard
     * halftones in Beta 24. One prepared archive owns its immutable render token.
     */
    private StagedCutZip preparedSpecialtyCutZip;
    private int preparedSpecialtyGeneration=-1,preparedSpecialtyDevice=-1;
    private AutoSilhouetteUnderlay.Output preparedSpecialtyBase;
    private boolean preparingSpecialtyCutZip;
    private void discardPreparedSpecialtyCutZip(){
        StagedCutZip old=preparedSpecialtyCutZip;
        preparedSpecialtyCutZip=null;preparedSpecialtyGeneration=-1;
        preparedSpecialtyBase=null;preparedSpecialtyDevice=-1;
        if(old!=null)old.close();
    }
    private boolean specialtyPreparedStillCurrent(){
        return preparedSpecialtyCutZip!=null&&alternateActive()&&specialtyFinalReady()&&
            preparedSpecialtyGeneration==alternateGeneration&&
            preparedSpecialtyGeneration==alternateReadyGeneration&&
            preparedSpecialtyDevice==outputDeviceId&&preparedSpecialtyBase==currentUnderlay();
    }
    /** Only called on UI thread; stage has passed CRC and member validation. */
    private void offerPreparedSpecialtyZip(StagedCutZip complete,int generation,int device,
                                          AutoSilhouetteUnderlay.Output base,String label){
        preparingSpecialtyCutZip=false;
        if(isFinishing()||isDestroyed()||!alternateActive()||!specialtyFinalReady()||
                generation!=alternateGeneration||generation!=alternateReadyGeneration||
                outputDeviceId!=device||base!=currentUnderlay()){
            complete.close();return; // Superseded preview: no stale error dialog.
        }
        discardPreparedSpecialtyCutZip();
        preparedSpecialtyCutZip=complete;preparedSpecialtyGeneration=generation;
        preparedSpecialtyDevice=device;preparedSpecialtyBase=base;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");
        i.putExtra(Intent.EXTRA_TITLE,"VinylStudio-"+label+"-cut-layers.zip");
        try{startActivityForResult(i,ALTERNATE_EXPORT);}
        catch(RuntimeException failure){
            discardPreparedSpecialtyCutZip();
            showStandardExportError(friendlyFailure(failure,"Android file picker unavailable"));
        }
    }
    private void publishPreparedSpecialtyZip(Uri uri){
        final StagedCutZip ready=preparedSpecialtyCutZip;
        final long reviewToken=projectReviewProgress.revision();
        if(!specialtyPreparedStillCurrent()){
            discardPreparedSpecialtyCutZip();discardFailedStandardDocument(uri);
            showStandardExportError("Specialty artwork changed before saving. Export again.");return;
        }
        // Transfer stage ownership to the background write before another
        // UI export can replace the pending file.
        preparedSpecialtyCutZip=null;preparedSpecialtyGeneration=-1;
        preparedSpecialtyBase=null;preparedSpecialtyDevice=-1;
        if(!scheduleDiskJob(()->{
            Exception failure=null;
            try{publishStagedCutZip(ready,uri);}
            catch(Exception e){failure=e;discardFailedStandardDocument(uri);}
            finally{ready.close();}
            final Exception error=failure;
            postUi(()->{
                if(error!=null)showStandardExportError(friendlyFailure(error,"Specialty ZIP save failed"));
                else {projectReviewProgress.record(reviewToken,ProjectReviewProgress.CUT_ZIP);toast(getString(R.string.ui_verified_specialty_zip_written));productFlows.offerShareCutSheets(uri);
                    productFlows.rememberVerifiedExport(uri,projectReviewProgress.revision()==reviewToken);}
            });
        })){
            ready.close();discardFailedStandardDocument(uri);
            showStandardExportError("Cannot start the specialty ZIP save job");
        }
    }
    private void requestAlternateExport(){
        if(!alternateActive()||photo==null){toast(getString(R.string.ui_choose_a_specialty_style_and_import));return;}
        if(!specialtyFinalReady()){
            toast(getString(R.string.ui_finish_rendering_this_style_before_exporting));return;
        }
        if(activeCurveFamily()&&curveVinylRibbon&&curveRibbonResult==null){
            toast(getString(R.string.ui_vinyl_ribbon_is_not_ready_reduce));return;
        }
        if(activeCurveFamily()&&(curveDraftPreview||(curveVinylRibbon&&curveRibbonResult!=null&&curveRibbonResult.draft))){
            toast(getString(R.string.ui_preview_is_still_a_draft_release));return;
        }
        // The filled photo Spiral explicitly clips against alpha/Snip masks.
        // Open pen centerlines cannot safely be clipped into independent pieces.
        if(continuousStyle!=null&&continuousStyle!=ContinuousLineModes.LineStyle.SPIRAL&&
                settings.snipFillMode==0&&settings.snips!=null&&!settings.snips.isEmpty()){
            toast(getString(R.string.ui_pen_centerlines_cannot_honor_snips_without));return;
        }
        if(settings.registration){
            try{
                RegistrationCutGeometry.Report report=activeMarkReport();
                if(report!=null&&!report.valid()){
                    new AlertDialog.Builder(this).setTitle("Registration marks need attention")
                        .setMessage(report.firstIssue()+"\n\nMove the highlighted mark or reset all four marks before exporting cut-ready SVGs.")
                        .setPositiveButton(getString(R.string.ui_adjust_marks),(dialog,which)->openWorkspaceSubtool(5,1))
                        .setNegativeButton("Cancel",null).show();return;
                }
            }catch(RuntimeException error){toast("Registration check: "+friendlyFailure(error,"Registration marks need adjustment"));return;}
        }
        if(preparingSpecialtyCutZip||preparedSpecialtyCutZip!=null){
            toast(getString(R.string.ui_finish_or_cancel_the_current_specialty));return;
        }
        alternateExportToken=alternateReadyGeneration;
        alternateExportWidthMm=settings.widthMm;
        // No SAF document exists at this point. The exact final geometry must
        // pass all compatibility and cut-sheet checks before the Save picker.
        if(newStyleActive())exportNewStyle(null);
        else if(silhouetteOnly()&&currentUnderlay()!=null)exportSilhouetteOnly(null,currentUnderlay());
        else exportAlternate(null);
    }
    private static void putZip(ZipOutputStream out,String filename,String text) throws IOException{
        out.putNextEntry(new ZipEntry(filename));out.write(text.getBytes(StandardCharsets.UTF_8));out.closeEntry();
    }
    private static String safeName(String raw){
        String cleaned=(raw==null?"layer":raw).replaceAll("[^A-Za-z0-9_-]","-").toLowerCase(Locale.US);
        return cleaned.isEmpty()?"layer":cleaned;
    }
    private static String colorHex(int color){return String.format(Locale.US,"#%06X",color&0xffffff);}
    private int[] previewLayerColors(int count){
        int[] colors=new int[Math.max(1,count)];
        final int[] extra={0xffe1b94c,0xff42b7c9,0xffd6689d,0xff2a3040,0xff86a5d6,0xff61a979,0xffbd8bd8,0xffdc8062};
        for(int i=0;i<colors.length;i++)colors[i]=i<settings.layers.size()?settings.layers.get(i).color:extra[i%extra.length];
        return colors;
    }
    private String tonalLayerName(int index){
        if(index>=0&&index<settings.layers.size())return settings.layers.get(index).name;
        return "Tone Layer "+(index+1);
    }
    private static void putJpeg(ZipOutputStream zip,String name,Bitmap bitmap) throws IOException{
        if(bitmap==null)return;
        zip.putNextEntry(new ZipEntry(name));bitmap.compress(Bitmap.CompressFormat.JPEG,92,zip);zip.closeEntry();
    }
    private static void putPng(ZipOutputStream zip,String name,Bitmap bitmap) throws IOException{
        if(bitmap==null)return;
        zip.putNextEntry(new ZipEntry(name));bitmap.compress(Bitmap.CompressFormat.PNG,100,zip);zip.closeEntry();
    }
    private static AutoSilhouetteUnderlay.Output makeNewStyleUnderlay(VinylEngine.Settings settings,
            int[] px,int w,int h,int color){
        VinylEngine.Settings copy=settings.copy();
        // NewStyleExport uses an 8 mm registration margin and zero without marks.
        // Keep SVG board extents identical for all separately imported sheets.
        copy.outerSpacingMm=copy.registration?SheetFrame.SPECIALTY_PADDING_MM:0f;
        return AutoSilhouetteUnderlay.make(copy,px,w,h,color);
    }
    private static String autoBaseFilename(AutoSilhouetteUnderlay.Output base){
        return String.format(Locale.US,"01-solid-snip-%06x.svg",base.color&0xffffff);
    }
    private String standardLayerManifest(VinylEngine.Result result,AutoSilhouetteUnderlay.Output base){
        StringBuilder csv=new StringBuilder("order,file,physical_layer,color_hex,separation_channel,enabled,svg_elements\n");
        if(base!=null)csv.append("1,").append(autoBaseFilename(base)).append(",\"L1 Solid Snip background\",")
            .append(colorHex(base.color)).append(",\"BASE\",true,").append(base.cutContours()+(result.settings.registration?4:0)).append('\n');
        int sheetNo=base==null?1:2;
        for(int i=0;i<result.settings.layers.size();i++){
            VinylEngine.Layer layer=result.settings.layers.get(i);
            if(!VinylEngine.cutLayer(result,i))continue;
            String file=String.format(Locale.US,"%02d-vinyl-%06x.svg",sheetNo,layer.color&0xffffff);
            csv.append(sheetNo).append(',').append(file).append(',').append('"').append(ExportColorLabels.physicalLabel(i,layer.color,layer.cmykChannel)).append('"').append(',')
                .append(colorHex(layer.color)).append(',').append('"').append(ExportColorLabels.channel(layer.cmykChannel)).append('"').append(',').append(layer.enabled).append(',')
                .append(VinylEngine.svgElementsForLayer(result,i)).append('\n');
            sheetNo++;
        }
        return csv.toString();
    }
    private String tonalLayerFilename(MultilayerStencilEngine.Layer layer){
        return String.format(Locale.US,"%02d-%s-%06x.svg",layer.index+1,layer.baseLayer?"base":"overlay",layer.color&0xffffff);
    }
    private String tonalLayerManifest(MultilayerStencilEngine.Result layers){
        StringBuilder csv=new StringBuilder("order,file,role,color_hex,source_color_index,build_pixels,visible_pixels,contours,detail_percent\n");
        for(int i=0;i<layers.layers.size();i++){
            MultilayerStencilEngine.Layer layer=layers.layers.get(i);
            if(layer.pixelCount<=0)continue;
            csv.append(i+1).append(',').append(tonalLayerFilename(layer)).append(',')
                .append(layer.baseLayer?"base":"overlay").append(',')
                .append(colorHex(layer.color)).append(',').append(layer.sourceIndex+1).append(',')
                .append(layer.pixelCount).append(',').append(layer.visiblePixelCount).append(',')
                .append(tonalVectorContourCount(layers,i)).append(',')
                .append(tonalDetailByCount[tonalLayerCount]).append('\n');
        }
        return csv.toString();
    }
    private Bitmap renderStandardCompositePreview(VinylEngine.Result result,int maxSide,
            AutoSilhouetteUnderlay.Output base,boolean baseOnly){
        float boardW=result.boardW,boardH=result.boardH;
        float scale=Math.max(.5f,Math.min(maxSide/Math.max(boardW,boardH),6f));
        int w=Math.max(320,Math.round(boardW*scale));
        int h=Math.max(320,Math.round(boardH*scale));
        Bitmap bmp=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas cv=new Canvas(bmp); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setDither(true);
        cv.drawColor(CANVAS);
        cv.scale(scale,scale);
        p.setStyle(Paint.Style.FILL); p.setColor(result.settings.paper);
        cv.drawRect(result.padMm,result.padMm,result.padMm+result.settings.widthMm,result.padMm+result.heightMm,p);
        cv.save(); cv.translate(result.padMm,result.padMm);
        if(base!=null){
            Path silhouette=new Path();silhouette.setFillType(Path.FillType.EVEN_ODD);
            float[][] rings=base.shape.contours==null?new float[][]{base.shape.xy}:base.shape.contours;
            for(float[] ring:rings){for(int j=0;j<ring.length;j+=2){
                if(j==0)silhouette.moveTo(ring[j],ring[j+1]);else silhouette.lineTo(ring[j],ring[j+1]);
            }silhouette.close();}
            p.setColor(base.color);p.setStyle(Paint.Style.FILL);cv.drawPath(silhouette,p);
        }
        Path reusableShapePath=new Path();
        int drawnShapes=0;
        if(!baseOnly)for(int li=0;li<result.shapes.size();li++){
            if(!VinylEngine.cutLayer(result,li))continue;
            p.setStyle(Paint.Style.FILL); p.setColor(result.settings.layers.get(li).color);
            for(VinylEngine.Shape sh:result.shapes.get(li)){
                if((drawnShapes++&63)==0)CancellationCheckpoint.check();
                if(sh.circle)cv.drawCircle(sh.x,sh.y,sh.r,p);
                else{
                    Path path=reusableShapePath;path.rewind();path.setFillType(Path.FillType.EVEN_ODD);
                    float[][] rings=sh.contours==null?new float[][]{sh.xy}:sh.contours;
                    for(float[] ring:rings){
                        for(int i=0;i<ring.length;i+=2){ if(i==0)path.moveTo(ring[i],ring[i+1]); else path.lineTo(ring[i],ring[i+1]); }
                        path.close();
                    }
                    cv.drawPath(path,p);
                }
            }
        }
        cv.restore();
        if(result.settings.registration)for(int i=0;i<result.marks.length;i++){
            float[] mark=result.settings.marks==null?result.marks[i]:result.settings.marks[i];
            p.setColor(0xff43B9AB); p.setStyle(Paint.Style.FILL);
            float[] coords=RegistrationMarks.outline(result.settings.markStyle,mark[0],mark[1],result.settings.markSizeMm,
                Math.min(result.settings.minimumMm,result.settings.markSizeMm*.9f));
            if(coords==null)cv.drawCircle(mark[0]+result.padMm,mark[1]+result.padMm,result.settings.markSizeMm/2f,p);
            else {Path path=new Path(); path.moveTo(coords[0]+result.padMm,coords[1]+result.padMm); for(int k=2;k<coords.length;k+=2)path.lineTo(coords[k]+result.padMm,coords[k+1]+result.padMm); path.close(); cv.drawPath(path,p);}    
        }
        return bmp;
    }
    private Bitmap renderSpaceRibbonPreview(SpaceFillingRibbon.Result ribbon,int maxSide){
        // C2: do not allocate a full-resolution ARGB array AND a full-size Bitmap
        // simply to downscale a binary ribbon mask for display. This preview-only
        // compositor retains antialiased edge coverage while bounding memory.
        BoundedBinaryMaskPreview.Result preview=BoundedBinaryMaskPreview.compose(
            ribbon.width,ribbon.height,ribbon.mask,maxSide);
        Bitmap bmp=Bitmap.createBitmap(preview.width,preview.height,Bitmap.Config.ARGB_8888);
        bmp.setPixels(preview.argb,0,preview.width,0,0,preview.width,preview.height);
        return bmp;
    }
    private Bitmap renderAlternatePreviewBitmap(Bitmap prepared,Path line,MultilayerStencilEngine.Result tones,MazePortraitEngine.Result maze,int maxSide){
        return renderAlternatePreviewBitmap(prepared,line,tones,maze,maxSide,captureSpecialtyPreviewSettings());
    }
    private Bitmap renderAlternatePreviewBitmap(Bitmap prepared,Path line,MultilayerStencilEngine.Result tones,MazePortraitEngine.Result maze,int maxSide,SpecialtyPreviewSettings presentation){
        if(maze!=null){
            int side=Math.min(maxSide,Math.max(Math.round(maze.canvasSizePx),700));
            Bitmap bmp=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888);
            Canvas cv=new Canvas(bmp); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setDither(true);
            cv.drawColor(Color.WHITE);
            float scale=side/(float)maze.canvasSizePx; cv.scale(scale,scale);
            p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(Color.BLACK); p.setStrokeWidth(Math.max(.8f,maze.canvasSizePx/520f)); cv.drawPath(maze.wallsPath,p);
            if(presentation.solution){p.setColor(Color.BLACK); p.setStrokeWidth(Math.max(1.6f,maze.canvasSizePx/240f)); cv.drawPath(maze.solutionPath,p);}            
            return bmp;
        }
        if(tones!=null)return renderTonalPreview(tones,maxSide,presentation.tonalSelection,presentation);
        if(line!=null&&prepared!=null){
            int srcW=prepared.getWidth(),srcH=prepared.getHeight();
            float scale=Math.min(1f,maxSide/(float)Math.max(srcW,srcH));
            int w=Math.max(320,Math.round(srcW*scale)),h=Math.max(320,Math.round(srcH*scale));
            Bitmap bmp=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            Canvas cv=new Canvas(bmp); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setDither(true);
            cv.drawColor(Color.WHITE);
            cv.scale(w/(float)srcW,h/(float)srcH);
            p.setStyle(Paint.Style.STROKE); p.setColor(Color.BLACK);
            p.setStrokeWidth(Math.max(.05f,presentation.stroke*srcW/Math.max(1f,presentation.width))); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            cv.drawPath(line,p);
            return bmp;
        }
        return prepared;
    }
    private void publishStagedDocument(StagedDocumentFile stage,Uri uri,StagedDocumentFile.Kind type)
            throws IOException {
        stage.verify(type);
        try(OutputStream target=getContentResolver().openOutputStream(uri,"wt")){
            if(target==null)throw new IOException("Selected document is not writable");
            stage.copyTo(target);
        }
    }
    /** Providers vary in their support of explicit truncation mode; never fall back
     * after any bytes have already been written. The caller verifies saved bytes.
     * Plain 'w' can fail to truncate: keep the post-copy validation mandatory.
     */
    private OutputStream openCutDocumentOutput(Uri uri) throws IOException {
        try {
            OutputStream writable=getContentResolver().openOutputStream(uri,"wt");
            if(writable!=null)return writable;
        }catch(java.io.FileNotFoundException | IllegalArgumentException unsupported){
            android.util.Log.w("VinylStudio","SAF provider rejected truncate mode; attempting write mode",unsupported);
        }
        OutputStream writable=getContentResolver().openOutputStream(uri,"w");
        if(writable==null)throw new IOException("Selected export document is unavailable");
        return writable;
    }
    /** Validated stage first: only publish after all SVGs, reports, and CRCs pass.
     * A third-party SAF provider cannot guarantee atomicity for its final copy.
     */
    private void publishStagedCutZip(StagedCutZip stage,Uri uri) throws IOException {
        stage.verify();
        try(OutputStream target=openCutDocumentOutput(uri)){
            stage.copyTo(target);
        }
        // The provider has closed its writable stream. Verify the bytes it
        // actually persisted, not only the in-memory/staged success status.
        try(InputStream saved=getContentResolver().openInputStream(uri)){
            stage.verifyPublishedCopy(saved);
        }
    }
    /** Per-layer export of the exact preview geometry; no rerun at save time. */
    private void exportNewStyle(Uri uri){exportNewStyle(uri,false,-1,null);}
    private void exportNewStyle(Uri uri,boolean acknowledgedCutSafety,
                                int acknowledgedGeneration,StyleKit.Result acknowledgedResult){
        final int targetForThisExport=outputDeviceId;
        final String matForThisExport=selectedCricutMat();
        final boolean includeSource=includeSourcePhotoInCutZip;
        final AutoSilhouetteUnderlay.Output capturedBase=currentUnderlay();
        if(silhouetteOnly()&&currentUnderlay()!=null){exportSilhouetteOnly(uri,currentUnderlay());return;}
        final int token=alternateGeneration;
        final StyleKit.Result result=newStyleResult;
        final int style=newStyleId;
        final boolean marks=settings.registration;
        final int markStyle=settings.markStyle;
        final float markSize=settings.markSizeMm, clearance=settings.registrationClearanceMm;
        final float minCut=settings.minimumMm;
        final float minGap=StyleCutGeometryPreflight.exportGapMm(settings.bridgeMm,settings.minimumMm);
        final float bridgeMm=settings.bridgeMm;
        final float[][] manualMarks=RegistrationMarks.copyPositions(settings.marks);
        final boolean circleCrop=settings.circleCrop; // ADR-0001: same marks as preview and Snip base
        // D7: export the exact cut-ready geometry the preview shows; if it is not ready (or was
        // prepared with other cut parameters), export prepares it once and publishes it to the preview.
        final StyleKit.Result readyCut=SuiteCutStage.reusable(newStyleCutStage,newStyleResult,newStyleId,minCut,bridgeMm);
        final SuiteCutStage readyStage=readyCut!=null?newStyleCutStage:null;
        final int renderVariant=newStyleRenderVariant,renderColorCount=newStyleRenderColorCount;
        // Never condition already-conditioned geometry when cut parameters changed.
        final StyleKit.Result exportSource=SuiteCutStage.sourceFor(newStyleCutStage,newStyleResult);
        final StyleKit.Result[] reviewedCut={result};
        if(result==null||newStyleVectors==null||newStyleVectors.source!=result||
           !specialtyFinalReady()||token!=alternateExportToken){
            toast(getString(R.string.ui_finish_rendering_before_exporting_this_style));return;
        }
        // Never apply consent to a different render than the one reviewed.
        if(acknowledgedCutSafety&&
                (token!=acknowledgedGeneration||result!=acknowledgedResult)){
            toast(getString(R.string.ui_artwork_changed_export_again_to_review));return;
        }
        if(matPreflightRefused())return;
        if(readyCut==null)cancelNewStyleCutPreparation(); // this export prepares and publishes it
        preparingSpecialtyCutZip=true;
        if(!scheduleCutPreparationJob(()->{
            StagedCutZip stage=null;
            try{
                // Physical cut preparation runs here, off the render loop, on
                // a copy: only sheets the export inspector would flag change.
                final StyleKit.Result cut;
                final SuiteCutStage cutStage;
                if(readyCut!=null){cut=readyCut;cutStage=readyStage;}
                else{
                    SuiteCutStage prepared=SuiteCutStage.prepare(exportSource,style,minCut,bridgeMm);
                    cut=prepared.cut;cutStage=prepared;
                    NewStyleVectorCache cutVectors=new NewStyleVectorCache(cut);
                    StyleLayerOperations.Counts[] cutOperations=style==VinylStyleSuite.POP_PANELS?null:
                        StyleLayerOperations.bySlot(cut,style,renderVariant,renderColorCount);
                    // Posted before any review dialog, so the user reviews what the preview shows.
                    postUi(()->publishNewStyleCut(token,result,prepared,cutVectors,cutOperations));
                }
                reviewedCut[0]=cut;
                // Never publish a staged ZIP before the user has seen the
                // physical-cut report, including incomplete complexity scans.
                java.util.List<StyleCutGeometryPreflight.Warning> geometryWarnings=
                    StyleCutGeometryPreflight.inspect(cut,Math.max(.2f,minCut),minGap,500);
                CutSafetyReviewPolicy.Decision geometryDecision=
                    CutSafetyReviewPolicy.assess(geometryWarnings,500);
                final boolean baseUnverified=capturedBase!=null;
                final CutSafetyReviewPolicy.Decision cutReview=baseUnverified?
                    new CutSafetyReviewPolicy.Decision(geometryDecision.critical,
                        geometryDecision.narrow,geometryDecision.unverified+1):geometryDecision;
                if(cutReview.requiresConfirmation()&&!acknowledgedCutSafety){
                    postUi(()->{
                        preparingSpecialtyCutZip=false;
                        if(token!=alternateGeneration||isFinishing()||isDestroyed())return;
                        new AlertDialog.Builder(this)
                            .setTitle(cutReview.title())
                            .setMessage(cutReview.message())
                            .setNegativeButton(getString(R.string.ui_cancel_export),(d,which)->{})
                            .setPositiveButton(cutReview.dangerous()?"Export despite risks":"Export with warning",
                                (d,which)->exportNewStyle(uri,true,token,cut))
                            .show();
                    });
                    return;
                }
                stage=new StagedCutZip(getCacheDir());
                try(OutputStream stream=stage.openStream()){
                    if(stream==null)throw new IOException("Cannot open export destination");
                    if(token!=alternateGeneration)throw new java.util.concurrent.CancellationException("Superseded export");
                    try(ZipOutputStream zip=new ZipOutputStream(stream)){
                    final CutSheetZipWriter cuts=new CutSheetZipWriter(zip,targetForThisExport,minCut,minGap,acknowledgedCutSafety).selectMat(matForThisExport);
                    // A reviewed SOURCE preflight warning remains unqualified even
                    // when the final-sheet geometry has no additional findings.
                    cuts.noteSourceFindings(cutReview.requiresConfirmation());
                    StringBuilder manifest=new StringBuilder("order,file,color_hex,closed_cuts,pen_paths,registration_marks\n");
                    int exported=0;
                    final AutoSilhouetteUnderlay.Output base=capturedBase;
                    if(base!=null){String svg=base.svg();CutSvgCompatibility.verify(svg,targetForThisExport);
                        cuts.put(autoBaseFilename(base),svg);
                        manifest.append("1,").append(autoBaseFilename(base)).append(',').append(colorHex(base.color))
                            .append(',').append(base.cutContours()).append(",0,").append(marks?4:0).append('\n');
                        exported++;
                    }
                    for(int i=0;i<cut.layers.size();i++){
                        if(token!=alternateGeneration||Thread.currentThread().isInterrupted())throw new CancellationException();
                        StyleKit.Layer layer=cut.layers.get(i);
                        if(layer.loops.isEmpty()&&layer.lines.isEmpty())continue;
                        if(targetForThisExport==OutputDeviceProfile.CRICUT && layer.loops.size()+layer.lines.size()+(marks?4:0)>5000)
                            throw new IOException("Too many paths in layer "+(i+1)+"; reduce Detail");
                        int physicalSlot=NewStyleColorSlots.slot(style,layer.name,i);
                        String filename=String.format(Locale.US,"L%02d-%s-%06x.svg",physicalSlot+(base==null?1:2),safeName(layer.name),layer.color&0xffffff);
                        String svg=NewStyleExport.layerSvg(cut,i,marks,markStyle,markSize,clearance,manualMarks,minCut,circleCrop);
                        CutSvgCompatibility.verify(svg,targetForThisExport);
                        if(layer.lines.isEmpty())SpecialtyCutSafety.validateVinylRibbonSvg(svg,targetForThisExport);
                        cuts.put(filename,svg);
                        manifest.append(physicalSlot+(base==null?1:2)).append(',').append(filename).append(',').append(colorHex(layer.color)).append(',')
                            .append(layer.loops.size()).append(',').append(layer.lines.size()).append(',').append(marks?4:0).append('\n');
                        exported++;
                    }
                    if(exported==0)throw new IOException("Style contains no exportable geometry");
                    putZip(zip,"layer-manifest.csv",cuts.manifest(manifest.toString()));
                    if(includeSource)putJpeg(zip,"source-photo.jpg",originalPhoto==null?photo:originalPhoto);
                    StringBuilder review=new StringBuilder("physical_layer,x_mm,y_mm,warning\n");
                    for(StyleCutGeometryPreflight.Warning warning:geometryWarnings){
                        int slot=NewStyleColorSlots.slot(style,cut.layers.get(warning.layer).name,warning.layer);
                        // Match layer-manifest physical numbering when the base
                        // silhouette occupies the first vinyl sheet.
                        review.append(slot+(base==null?1:2)).append(',').append(warning.xMm).append(',')
                            .append(warning.yMm).append(',').append(warning.reason).append('\n');
                    }
                    if(geometryWarnings.size()>=500)
                        review.append("0,0,0,Warning report limit reached; remaining cut safety is not verified\n");
                    if(baseUnverified)
                        review.append("1,0,0,Solid Snip base contour requires independent physical review\n");
                    putZip(zip,"cut-safety-review.csv",review.toString());
                    // M4: what cut prep removed, per sheet (net), so nothing is silently dropped.
                    if(cutStage!=null)putZip(zip,"cut-prep-changes.csv",cutStage.repair.csv(cut));
                    String repairNote=cutStage==null?"":cutStage.repairSentence();
                    putZip(zip,"README.txt",VinylStyleSuite.NAMES[style]+"\n"+cut.summary+"\n"+
                        (repairNote.isEmpty()?"":repairNote+" See cut-prep-changes.csv.\n")+
                        "Each file is a separate physical layer. Filled outlines are blade cuts; open lines are for pen/engrave only. "+
                        "Keep all files at the stated SVG physical scale and Attach their registration marks in Design Space. "+
                        "For Stained Glass, apply lead underlay first, then numbered glass color layers. "+
                        "Apply every style's sheets in file-number order, L01 at the bottom; Torn-Paper Collage and Marquetry "+
                        "pieces deliberately extend under the next sheets, so order matters. "+
                        "Inspect narrow tips and test-cut before applying.\n");
                    cuts.finish();writeOutputHandoff(zip,targetForThisExport);
                    zip.finish();
                    }
                    if(token!=alternateGeneration)throw new java.util.concurrent.CancellationException("Superseded export");
                    // Main-thread offerPreparedSpecialtyZip validates result/base/device again.
                }
                stage.verify();
                final StagedCutZip verified=stage;stage=null;
                postUi(()->offerPreparedSpecialtyZip(verified,token,targetForThisExport,capturedBase,"art-style"));
            }catch(FinalCutReviewRequired unverified){
                if(stage!=null)stage.close();
                postUi(()->{
                    preparingSpecialtyCutZip=false;
                    if(token!=alternateGeneration||isFinishing()||isDestroyed())return;
                    if(acknowledgedCutSafety){showStandardExportError(unverified.getMessage());return;}
                    new AlertDialog.Builder(this).setTitle(getString(R.string.ui_final_cut_geometry_needs_review))
                        .setMessage(unverified.getMessage()+"\n\nSave an INSPECTION-ONLY ZIP with warnings? Do not cut it.")
                        .setNegativeButton("Cancel",null)
                        .setPositiveButton(getString(R.string.ui_save_inspection_zip),(d,w)->exportNewStyle(null,true,token,reviewedCut[0]))
                        .show();
                });
            }catch(java.util.concurrent.CancellationException superseded){
                if(stage!=null)stage.close();
                postUi(()->{preparingSpecialtyCutZip=false;});
            }catch(OutOfMemoryError oom){
                if(stage!=null)stage.close();
                postUi(()->{preparingSpecialtyCutZip=false;showStandardExportError("Insufficient memory preparing style ZIP");});
            }catch(Exception error){
                if(stage!=null)stage.close();
                android.util.Log.e("VinylNewStyles","Export failed",error);
                postUi(()->{preparingSpecialtyCutZip=false;
                    if(token==alternateGeneration&&!isFinishing()&&!isDestroyed())
                        showStandardExportError(friendlyFailure(error,"Unable to prepare cut sheets"));});
            }
        }))preparingSpecialtyCutZip=false;
    }
    /** D7: export-resolution stipple of the current render; UI-thread owned. */
    private TessellationEngine.Result stippleCutResult;
    private int stippleCutGeneration=-1,stippleCutFailedGeneration=-1;
    private java.util.concurrent.Future<?> stippleCutJob;
    private boolean stippleCutReady(TessellationEngine.Result shown){
        return shown!=null&&shown.stipple!=null&&shown==stippleCutResult&&stippleCutGeneration==alternateGeneration;
    }
    private void cancelStippleCutPreparation(){
        if(stippleCutJob!=null){renderController.cancelCutPreparation(stippleCutJob);stippleCutJob=null;}
    }
    /** Swaps the 350 px draft stipple preview for the export geometry (UI thread). */
    private void publishStippleCut(int token,TessellationEngine.Result preview,TessellationEngine.Result full,StippleVectorCache vectors){
        if(token!=alternateGeneration||alternatePreviewDraft||tessellationResult!=preview)return;
        tessellationResult=full;stippleVectorCache=vectors;
        stippleCutResult=full;stippleCutGeneration=token;
        showCutRiskMarkers=false;specialtyRiskSource=null;specialtyRiskMarkers=Collections.emptyList();
        cachedHeatmap=null;heatmapGeneration=-1;
        commitMarkReport();
        alternateCutWarnings="Cut-ready dots shown; these are the dots that will be cut. A test cut is still required.";
        alternateStatus=full.summary()+" · "+alternateCutWarnings;
        if(status!=null)status.setText(alternateStatus);
        if(activeWorkspace==4)refreshShelfPanel();
        artwork.invalidate();
    }
    /** After a FINAL stipple preview, prepare the export geometry in the background (UI thread). */
    private void startStippleCutPreparation(int token,TessellationEngine.Result preview){
        cancelStippleCutPreparation();
        final Bitmap source=photo;final VinylEngine.Settings snapshot=settings.copy();
        if(source==null)return;
        try{
            stippleCutJob=renderController.cutPreparation().submit(()->{
                try{
                    TessellationEngine.Result full=fullResolutionStipple(source,snapshot,preview.options);
                    StippleVectorCache vectors=new StippleVectorCache(full);
                    if(Thread.currentThread().isInterrupted()||token!=alternateGeneration)return;
                    postUi(()->publishStippleCut(token,preview,full,vectors));
                }catch(CancellationException superseded){
                    // A newer render or the export replaced this preparation.
                }catch(IOException|OutOfMemoryError|RuntimeException failure){
                    android.util.Log.w("VinylStudio","Background stipple cut preparation failed; export will prepare",failure);
                    postUi(()->{if(token==alternateGeneration){stippleCutFailedGeneration=token;
                        if(status!=null)status.setText("Cut-ready dots unavailable; export will prepare them and show the result");}});
                }
            });
        }catch(java.util.concurrent.RejectedExecutionException busy){
            // An export is preparing; it publishes its own cut geometry.
        }
    }
    /** D7: the export-resolution stipple (full weighted relaxation, up to 1200 px) for a preview
     *  render. Shared by the background cut stage and the export so they can never diverge. */
    private static TessellationEngine.Result fullResolutionStipple(Bitmap fullStippleSource,
            VinylEngine.Settings fullStippleSettings,TessellationEngine.Options previewOptions) throws IOException {
        // Export-only source preparation. Preview is capped at 350 px;
        // full weighted centroid assignment uses up to 1200 px.
        if(fullStippleSource==null)throw new IOException("Photograph unavailable");
        SnipMask mask=fullStippleSettings.snips;
        Bitmap high=fullStippleSource;
        if(fullStippleSettings.snipFillMode!=0&&mask!=null&&!mask.isEmpty()){
            int iw=high.getWidth(),ih=high.getHeight();int[] raw=new int[iw*ih];
            high.getPixels(raw,0,iw,0,0,iw,ih);
            int[] filled=SnipBackground.apply(raw,iw,ih,mask,fullStippleSettings.snipFillMode);
            high=Bitmap.createBitmap(filled,iw,ih,Bitmap.Config.ARGB_8888);
            high=SpecialtyCutSafety.prepare(high,null,fullStippleSettings.circleCrop);
        }else high=SpecialtyCutSafety.prepare(high,mask,fullStippleSettings.circleCrop);
        int longest=Math.max(high.getWidth(),high.getHeight());
        if(longest>1200){float f=1200f/longest;
            high=Bitmap.createScaledBitmap(high,Math.max(12,Math.round(high.getWidth()*f)),
                Math.max(12,Math.round(high.getHeight()*f)),true);
        }
        int w=high.getWidth(),h=high.getHeight();int[] argb=new int[w*h];
        high.getPixels(argb,0,w,0,0,w,h);
        if(!ImageLevels.isNeutral(fullStippleSettings))
            argb=ImageLevels.apply(argb,w,h,fullStippleSettings);
        TessellationEngine.Options o=previewOptions.copy();
        o.stippleOptions.draft=false;
        boolean[] safe=new boolean[w*h];
        float canvasHeight=fullStippleSettings.widthMm*h/(float)w;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float nx=(x+.5f)/w,ny=(y+.5f)/h;
            float px=nx*fullStippleSettings.widthMm,py=ny*canvasHeight;
            float inset=fullStippleSettings.marginMm;
            boolean keep=px>=inset&&py>=inset&&px<=fullStippleSettings.widthMm-inset&&py<=canvasHeight-inset;
            if(keep&&fullStippleSettings.circleCrop){double rx=fullStippleSettings.widthMm*.5-inset,ry=canvasHeight*.5-inset;
                keep=rx>0&&ry>0&&Math.pow((px-fullStippleSettings.widthMm*.5)/rx,2)+
                    Math.pow((py-canvasHeight*.5)/ry,2)<=1;
            }
            if(keep&&fullStippleSettings.snipFillMode==0&&mask!=null)keep=mask.allows(nx,ny);
            safe[y*w+x]=keep;
        }
        o.allowedPixels=safe;
        TessellationEngine.Result full=TessellationEngine.generate(argb,w,h,o);
        if(full.tiles.isEmpty())throw new IOException("Full-resolution stipple produced no cuttable dots");
        return full;
    }
    private void exportAlternate(Uri uri){exportAlternate(uri,false,-1);}
    private void exportAlternate(Uri uri,boolean reviewed,int reviewedGeneration){
        final int targetForThisExport=outputDeviceId;
        final String matForThisExport=selectedCricutMat();
        final boolean includeSource=includeSourcePhotoInCutZip;
        final AutoSilhouetteUnderlay.Output capturedBase=currentUnderlay();
        if(newStyleActive()){exportNewStyle(uri);return;}
        if(silhouetteOnly()&&currentUnderlay()!=null){exportSilhouetteOnly(uri,currentUnderlay());return;}
        final ContinuousLineModes.LineStyle style=continuousStyle;
        final MultilayerStencilEngine.Result layers=tonalResult;
        final MazePortraitEngine.Result maze=mazeResult;
        final TessellationEngine.Result mosaic=tessellationResult;
        final Bitmap fullStippleSource=photo;
        final Bitmap sourceForZip=originalPhoto==null?photo:originalPhoto;
        final SpecialtyPreviewSettings presentation=captureSpecialtyPreviewSettings();
        final VinylEngine.Settings fullStippleSettings=settings.copy();
        final TessellationEngine.Result stippleReady=stippleCutReady(mosaic)?mosaic:null;
        if(mosaic!=null&&mosaic.stipple!=null&&stippleReady==null&&stippleCutFailedGeneration!=alternateGeneration){
            // D7: the 6-13k export dots must be on screen before the user reviews or exports them.
            if(stippleCutJob==null||stippleCutJob.isDone())startStippleCutPreparation(alternateGeneration,mosaic);
            toast("Preparing the exact dots to cut. Export again when the status says Cut-ready.");
            return;
        }
        final LayeredCellPortraitEngine.Result cellPortrait=layeredResult;
        final Path previewPath=continuousPreview==null?null:new Path(continuousPreview);
        final SpaceFillingRibbon.Result exportedRibbon=curveRibbonResult;
        final SpiralPortraitRibbon.Result exportedSpiral=spiralRibbonResult;
        final boolean curveOutput=ContinuousLineModes.curveFamily(style)>=0;
        final Bitmap prepared=alternatePreparedSource;
        final int previewWidth=alternatePreparedWidth,previewHeight=alternatePreparedHeight;
        final int renderToken=alternateReadyGeneration;
        final float mm=alternateExportWidthMm;
        final float lineStrokeMm=continuousStrokeMm;
        final float minimumCutMm=settings.minimumMm;
        final float minimumGapMm=settings.bridgeMm;
        if(!alternateActive()||!specialtyFinalReady()||renderToken<0||
                renderToken!=alternateExportToken||renderToken!=alternateGeneration){
            toast(getString(R.string.ui_artwork_or_style_changed_reopen_export));return;
        }
        if(reviewed&&reviewedGeneration!=renderToken){
            toast(getString(R.string.ui_artwork_changed_review_cut_geometry_again));return;
        }
        // Alternate vector formats do not yet share a proven general-purpose
        // final-contour oracle. Do not present them as certified cut-safe.
        boolean penOnly=maze!=null||(style!=null&&!curveOutput&&
            style!=ContinuousLineModes.LineStyle.SPIRAL);
        if(!reviewed&&!penOnly){
            new AlertDialog.Builder(this).setTitle("Cut safety not fully verified")
                .setMessage("This export uses final vector geometry, but a complete " +
                    "same-sheet contact/neck analysis is not yet available for this style. " +
                    "Inspect each physical cut layer and test-cut before applying vinyl. " +
                    "Do not assume the preview proves safe blade geometry.")
                .setNegativeButton(getString(R.string.ui_cancel_export),(d,w)->{})
                .setPositiveButton(getString(R.string.ui_export_with_review),(d,w)->exportAlternate(uri,true,renderToken))
                .show();return;
        }
        if(matPreflightRefused())return;
        preparingSpecialtyCutZip=true;
        if(!scheduleCutPreparationJob(()->{
            StagedCutZip stage=null;
            try{
                stage=new StagedCutZip(getCacheDir());
                try(OutputStream stream=stage.openStream()){
                if(stream==null)throw new IOException("Cannot open export destination");
                if(renderToken!=alternateGeneration)throw new IOException("Artwork changed; regenerate export");
                try(ZipOutputStream zip=new ZipOutputStream(stream)){
                    final CutSheetZipWriter cuts=new CutSheetZipWriter(zip,targetForThisExport,minimumCutMm,
                        StyleCutGeometryPreflight.exportGapMm(minimumGapMm,minimumCutMm),reviewed).selectMat(matForThisExport);
                    final AutoSilhouetteUnderlay.Output base=capturedBase;
                    if(base!=null){String svg=base.svg();CutSvgCompatibility.verify(svg,targetForThisExport);
                        cuts.put(autoBaseFilename(base),svg);}
                    TessellationEngine.Result exportMosaic=mosaic;
                    if(mosaic!=null&&mosaic.stipple!=null){
                        if(prepared==null)throw new IOException("Prepared source unavailable");
                        if(stippleReady!=null)exportMosaic=stippleReady; // D7: the cut-ready preview itself
                        else{
                            exportMosaic=fullResolutionStipple(fullStippleSource,fullStippleSettings,mosaic.options);
                            final TessellationEngine.Result shownFull=exportMosaic;
                            final StippleVectorCache fullVectors=new StippleVectorCache(shownFull);
                            // The user sees exactly what this export cuts.
                            postUi(()->publishStippleCut(renderToken,mosaic,shownFull,fullVectors));
                        }
                        for(int n=0;n<exportMosaic.counts.length;n++)
                            if(targetForThisExport==OutputDeviceProfile.CRICUT && exportMosaic.counts[n]+(exportMosaic.options.registration?4:0)>5000)
                                throw new IOException("Swatch "+(n+1)+" exceeds Cricut 5,000 vector shapes; reduce Stipple detail");
                    }
                    ExportBounds.requireLayers(cellPortrait!=null?cellPortrait.options.layerCount:
                        exportMosaic!=null?exportMosaic.options.layerCount:
                        layers!=null?layers.layers.size():1);
                    Bitmap composite=exportedRibbon!=null&&curveOutput?renderSpaceRibbonPreview(exportedRibbon,1600):
                        cellPortrait!=null?renderLayeredPreview(cellPortrait,1600,-1,presentation):exportMosaic!=null?renderTessPreview(exportMosaic,1600,-1,presentation):layers!=null?renderTonalPreview(layers,1600,-1,presentation):renderAlternatePreviewBitmap(prepared,previewPath,layers,maze,1600,presentation);
                    putJpeg(zip,"whole-image-preview.jpg",composite);
                    float estimatedWidth=exportMosaic!=null&&exportMosaic.stipple!=null?
                        exportMosaic.stipple.options.widthMm:mm;
                    float ratio=exportMosaic!=null?exportMosaic.height/(float)exportMosaic.width:
                        cellPortrait!=null?cellPortrait.height/(float)cellPortrait.width:
                        prepared!=null?prepared.getHeight()/(float)Math.max(1,prepared.getWidth()):1f;
                    putZip(zip,"material-estimate.txt",materialEstimate(estimatedWidth,
                        estimatedWidth*ratio,exportMosaic!=null?exportMosaic.options.layerCount:
                        cellPortrait!=null?cellPortrait.options.layerCount:layers!=null?layers.layers.size():1));
                    // Source photo is private by default across ALL specialty cuts.
                    if(includeSource)
                        putJpeg(zip,"source-photo.jpg",sourceForZip);
                    if(cellPortrait!=null){
                        int exported=0;
                        StringBuilder csv=new StringBuilder("order,file,color_hex,cut_pieces,cut_contours,svg_paths\n");
                        for(int layer=0;layer<cellPortrait.options.layerCount;layer++){
                            if(cellPortrait.pieceCounts[layer]==0)continue;
                            String name=String.format(Locale.US,"%02d-layered-cell-%06x.svg",layer+1,
                                cellPortrait.options.colors[layer]&0xffffff);
                            String svg=cellPortrait.svg(layer);
                            LayeredCellExportSafety.validate(cellPortrait,layer,svg);
                            SpecialtyCutSafety.validateLayerSvg(svg,targetForThisExport);
                            cuts.put(name,svg);exported++;
                            csv.append(layer+1).append(',').append(name).append(',')
                                .append(colorHex(cellPortrait.options.colors[layer])).append(',')
                                .append(cellPortrait.pieceCounts[layer]).append(',')
                                .append(cellPortrait.contourCounts[layer]+(cellPortrait.options.registration?4:0)).append(',')
                                .append(cellPortrait.pieceCounts[layer]+(cellPortrait.options.registration?4:0)).append('\n');
                        }
                        if(exported==0)throw new IOException("No cuttable color layers");
                        putZip(zip,"layer-manifest.csv",cuts.manifest(csv.toString()));
                        putZip(zip,"README.txt",cellPortrait.summary()+"\nSeparate flat-color, sliced/inlaid vinyl layers. "
                            +"Cut pieces, contour subpaths and SVG path elements are different counts. "
                            +"All registration marks have identical positions; test Design Space import and cut before applying.\n");
                    }else if(exportMosaic!=null){
                        int exported=0;StringBuilder csv=new StringBuilder("order,file,color_hex,cut_contours,tile_count\n");
                        for(int position=0;position<exportMosaic.options.layerCount;position++){
                            int layer=exportMosaic.stipple==null?position:exportMosaic.stipple.options.order[position];
                            if(exportMosaic.counts[layer]==0)continue;
                            String name=String.format(Locale.US,"%02d-%s-%06x.svg",position+(base==null?1:2),
                                exportMosaic.stipple!=null?"stipple":"tessellation",exportMosaic.options.colors[layer]&0xffffff);
                            String svg=exportMosaic.svg(layer);SpecialtyCutSafety.validateLayerSvg(svg,targetForThisExport);
                            cuts.put(name,svg);exported++;
                            csv.append(position+(base==null?1:2)).append(',').append(name).append(',').append(colorHex(exportMosaic.options.colors[layer])).append(',')
                                .append(exportMosaic.counts[layer]+(exportMosaic.options.registration?4:0)).append(',').append(exportMosaic.counts[layer]).append('\n');
                        }
                        if(exported==0)throw new IOException("No populated tessellation layers");
                        if(base!=null)csv.insert(csv.indexOf("\n")+1,
                            "1,"+autoBaseFilename(base)+","+colorHex(base.color)+","+base.cutContours()+",1\n");
                        putZip(zip,"layer-manifest.csv",cuts.manifest(csv.toString()));
                        if(exportMosaic.stipple!=null){
                            MultiColorStippleEngine.Result points=exportMosaic.stipple;
                            // Design Space accepts at most 5,000 SVG paths per upload.
                            // Do not ship a misleading grouped cutting SVG when the total
                            // exceeds that limit; the per-color files remain cuttable.
                            int totalPaths=0;
                            for(int c:exportMosaic.counts)totalPaths+=c;
                            totalPaths+=exportMosaic.options.registration?4*exportMosaic.options.layerCount:0;
                            // Do not include a second, unqualified cut file joining
                            // distinct colors: merging the layers changes physical
                            // contact semantics and invites accidental cutting.
                            // Legacy optional all-swatches-grouped.svg is intentionally
                            // omitted even when under 5000 paths.
                            putZip(zip,"grouped-svg-not-included.txt",
                                "A merged cross-color SVG is omitted for cut safety. "+
                                "Estimated total motifs: "+totalPaths+". Import only the "+
                                "numbered per-color sheets at original physical size.\n");
                            StipplePreflight.Report preflight=StipplePreflight.inspect(points,
                                minimumCutMm,points.options.overprintGapMm);
                            putZip(zip,"stipple-cut-preflight.txt",preflight.summary()+"\n"+
                                points.report()+"\nDiagnostic based on circle envelopes; inspect actual vector contours.\n");
                            StringBuilder collisions=new StringBuilder("layer_a,layer_b,x_mm,y_mm,mode\n");
                            float mmPerPixel=points.options.widthMm/points.width;
                            for(MultiColorStippleEngine.Collision overlap:points.options.overlap==MultiColorStippleEngine.OVERPRINT?
                                    java.util.Collections.<MultiColorStippleEngine.Collision>emptyList():points.originalOverlaps)
                                collisions.append(overlap.a+1).append(',').append(overlap.b+1).append(',')
                                    .append(String.format(Locale.US,"%.3f,%.3f",overlap.x*mmPerPixel,overlap.y*mmPerPixel))
                                    .append(',').append(points.options.overlap).append('\n');
                            putZip(zip,"overlap-report.csv",collisions.toString());
                            putZip(zip,"overlap-summary.txt",points.report()+"\n"+
                                "Shared/spacing: cross-layer gaps are checked before export. Knockout: subtracts higher shapes " +
                                "with approx. 0.10 mm raster-vector precision; requested trap remains intentionally under the top sheet. " +
                                "Overprint: all overlaps are intentional and excluded from collision warnings.\n");
                            Bitmap highlighted=renderTessCollisionPreview(exportMosaic,1400,presentation);
                            putJpeg(zip,"overlap-highlight-preview.jpg",highlighted);
                        }
                        putZip(zip,"README.txt",exportMosaic.summary()+"\nIndividual closed-path vinyl cells. Separate colors, identical registration geometry. No bitmap images are embedded in SVG. Test cut before applying.\n");
                    }else if(maze!=null){
                        String walls=maze.toWallSvg(0.25f);
                        String solution=maze.toSolutionSvg(0.35f);
                        String combined=maze.toCombinedSvg(0.25f,0.35f);
                        SpecialtyCutSafety.validateLayerSvg(walls,targetForThisExport);
                        SpecialtyCutSafety.validateLayerSvg(solution,targetForThisExport);
                        SpecialtyCutSafety.validateLayerSvg(combined,targetForThisExport);
                        cuts.put("maze-walls.svg",walls);
                        cuts.put("maze-solution.svg",solution);
                        cuts.put("maze-combined-preview.svg",combined);
                        putZip(zip,"README.txt",maze.summary()+"\nOne solvable route. Maze wall SVG has multiple open contours; only the solution SVG is a single open stroke. Pen/engraving only, not closed cuttable vinyl. Included JPEG shows the full preview.\n");
                    }else if(style==ContinuousLineModes.LineStyle.SPIRAL){
                        if(exportedSpiral==null||exportedSpiral.draft)throw new IOException("Final spiral cut geometry unavailable");
                        String svg=exportedSpiral.svg();
                        SpecialtyCutSafety.validateVinylRibbonSvg(svg,targetForThisExport);
                        cuts.put("spiral-portrait-vinyl.svg",svg);
                        putZip(zip,"README.txt",exportedSpiral.summary()+"\n"+
                            (exportedSpiral.hasTransparentMask?
                                "Alpha-masked photo: vinyl cuts only where the ribbon safely fits within the visible subject. " +
                                "Transparent exclusions split the spiral into "+exportedSpiral.cutPieces+" separately closed pieces. " +
                                "This is NOT a single connected ribbon; inspect and weed every piece. ":
                                "Opaque photo: one connected closed vinyl ribbon. ")+
                            "No pen/engraving strokes. Both edges vary independently with photograph brightness. " +
                            "Nominal width and turn gap are bounded; test cut at the real dimensions.\n");
                    }else if(style!=null&&curveOutput&&exportedRibbon!=null){
                        String svg=exportedRibbon.svg();
                        SpecialtyCutSafety.validateVinylRibbonSvg(svg,targetForThisExport);
                        cuts.put("vinyl-ribbon-"+safeName(style.name())+".svg",svg);
                        putZip(zip,"README.txt","VinylStudio 2.43 · "+style.name()+". One filled unioned closed vinyl silhouette. "
                            +"Not an open stroke. Photo-controlled ribbon width creates tone. "
                            +exportedRibbon.report()+"\nSVG raster-union precision is bounded by physical pixel size; "
                            +"thin necks and closely approaching nonadjacent turns require inspection. "
                            +"No same-layer duplicate cut edges are intentionally created. Test-cut at final dimensions.\n");
                    }else if(style!=null){
                        if(previewPath==null||renderToken<0)throw new IOException("Preview unavailable");
                        ContinuousToolpathValidator.Report report=ContinuousToolpathValidator.check(previewPath,previewWidth,previewHeight,mm,!curveOutput);
                        if(!report.valid)throw new IOException(report.message);
                        String svg=ContinuousLineSvg.svg(previewPath,previewWidth,previewHeight,mm,lineStrokeMm);
                        SpecialtyCutSafety.validateSingleSvg(svg,targetForThisExport);
                        cuts.put("single-line-"+style.name().toLowerCase(Locale.US)+".svg",svg);
                        putZip(zip,"README.txt",(curveOutput&&style==ContinuousLineModes.LineStyle.MOORE?"One closed continuous CENTERLINE loop; pen or engraving only. ":"One OPEN centerline; pen or engraving only. ")+"Not a weedable vinyl silhouette. No extra registration geometry. Included JPEG shows preview.\n"+report.message+"\n");
                    } else if(layers!=null){
                        int nonEmpty=0;
                        for(int i=0;i<layers.layers.size();i++){
                            if(layers.isEmpty(i))continue;
                            int contours=tonalVectorContourCount(layers,i);
                            if(targetForThisExport==OutputDeviceProfile.CRICUT&&contours>4800)throw new IOException("Layer "+(i+1)+" has "+contours+
                                " contours. Lower Level of detail before Cricut export.");
                            String svg=layers.svg(i);
                            SpecialtyCutSafety.validateLayerSvg(svg,targetForThisExport);
                            String file=tonalLayerFilename(layers.layers.get(i));
                            cuts.put(file,svg);nonEmpty++;
                        }
                        if(nonEmpty==0)throw new IOException("No non-empty tonal layers");
                        putZip(zip,"layer-manifest.csv",cuts.manifest(tonalLayerManifest(layers)));
                        StringBuilder cutChecks=new StringBuilder("order,minimum_mm,gap_mm,short_horizontal_run_pixels,short_vertical_run_pixels,short_gap_horizontal_pixels,short_gap_vertical_pixels,review_required\n");
                        for(int i=0;i<layers.layers.size();i++)if(!layers.isEmpty(i)){
                            VinylCutPreflight.Report check=VinylCutPreflight.inspect(layers,i,minimumCutMm,StyleCutGeometryPreflight.exportGapMm(minimumGapMm,minimumCutMm));
                            cutChecks.append(i+1).append(',').append(check.requestedMinimumMm).append(',')
                                .append(check.requestedGapMm).append(',')
                                .append(check.narrowHorizontalPixels).append(',').append(check.narrowVerticalPixels).append(',')
                                .append(check.narrowGapPixelsHorizontal).append(',').append(check.narrowGapPixelsVertical)
                                .append(',').append(check.needsReview()).append('\n');
                        }
                        putZip(zip,"cut-checks.csv",cutChecks.toString());
                        putZip(zip,"README.txt",layers.status()+"\nCut and apply the base first; add overlays in file-number order. Cut support regions may overlap across DIFFERENT vinyl colors, but there are no intentionally overlapping contours within a single color. All registration marks share physical coordinates. Preview represents the actual stacked masks. Inspect thin features and test cut before production.\n");
                    } else throw new IOException("Artwork has not generated");
                    cuts.finish();writeOutputHandoff(zip,targetForThisExport);
                    zip.finish();
                }
                if(renderToken!=alternateGeneration)throw new java.util.concurrent.CancellationException("Superseded export");
                // Final underlay/readiness check runs on the UI thread when the stage is offered.
                } // Flush and close the staged output stream before CRC verification.
                stage.verify();
                final StagedCutZip verified=stage;stage=null;
                postUi(()->offerPreparedSpecialtyZip(verified,renderToken,targetForThisExport,capturedBase,"specialty"));
            }catch(FinalCutReviewRequired unverified){
                if(stage!=null)stage.close();
                postUi(()->{
                    preparingSpecialtyCutZip=false;
                    if(renderToken!=alternateGeneration||isFinishing()||isDestroyed())return;
                    if(reviewed){showStandardExportError(unverified.getMessage());return;}
                    new AlertDialog.Builder(this).setTitle(getString(R.string.ui_final_cut_geometry_needs_review))
                        .setMessage(unverified.getMessage()+"\n\nSave an INSPECTION-ONLY ZIP with warnings? Do not cut it.")
                        .setNegativeButton("Cancel",null)
                        .setPositiveButton(getString(R.string.ui_save_inspection_zip),(d,w)->exportAlternate(null,true,renderToken))
                        .show();
                });
            }catch(java.util.concurrent.CancellationException superseded){
                if(stage!=null)stage.close();
                postUi(()->{preparingSpecialtyCutZip=false;});
            }catch(OutOfMemoryError oom){
                if(stage!=null)stage.close();
                postUi(()->{preparingSpecialtyCutZip=false;showStandardExportError("Insufficient memory preparing specialty ZIP");});
            }catch(Exception e){
                if(stage!=null)stage.close();
                postUi(()->{preparingSpecialtyCutZip=false;
                    if(renderToken==alternateGeneration&&!isFinishing()&&!isDestroyed())
                        showStandardExportError(friendlyFailure(e,"Unable to prepare this style"));});
            }
        }))preparingSpecialtyCutZip=false;
    }
    private void halftonePanel(){
        // Artwork style selection is in Shapes; Adjust contains parameters only.
        if(alternateActive()){
            addCompactSpecialtyAdjustments();
            return;
        }
        // Balanced is the fixed default for newly chosen styles. Old saved project
        // settings are retained until the user explicitly selects another style.
        LinearLayout controls=group();section(controls,VinylEngine.PATTERNS[settings.pattern]+" · three adjustments");
        // Automatic image-aware setup runs on style selection (no extra button).
        controls.addView(text(SmartHalftonePresets.explanation(settings.pattern),12,MUTED,false));
        if(settings.pattern==VinylEngine.ASCII)
            controls.addView(text(getString(R.string.ui_ascii_uses_adaptive_physically_safe_vector),12,MUTED,false));
        if(settings.pattern==VinylEngine.WORD_SCREEN){
            controls.addView(text(getString(R.string.ui_complete_words_are_distributed_over_the),12,MUTED,false));
            action(controls,"Edit words · "+settings.screenWords,()->{
                final EditText entry=new EditText(this);entry.setSingleLine(true);
                entry.setText(settings.screenWords);entry.setSelection(entry.length());
                entry.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                new android.app.AlertDialog.Builder(this).setTitle(getString(R.string.ui_words_for_portrait))
                    .setMessage(getString(R.string.ui_separate_words_with_spaces_use_short))
                    .setView(entry).setPositiveButton("Apply",(dialog,which)->{
                        String safe=WordScreenMotif.normalize(entry.getText().toString());
                        if(safe.isEmpty()){toast(getString(R.string.ui_enter_at_least_one_word));return;}
                        checkpoint();settings.screenWords=safe;setToolInternal(1);requestRender();
                    }).setNegativeButton("Cancel",null).show();
            });
        }
        linkedMasterControlsNotice(controls);
        if(settings.linkedControls)
            inchSlider(controls,"1 · Spacing",settings.masterSpacingMm,.065f,.40f,.005f,v->{settings.masterSpacingMm=v;setLinkedValues();});
        if(LineArtEngine.supportsShapeDefinition(settings.pattern))shapeControls();
        switch(settings.pattern){
            case VinylEngine.LINE:
                slider(controls,"2 · Maximum line width · %",settings.lineMaxFraction*100,30,92,1,v->settings.lineMaxFraction=v/100f);
                slider(controls,"3 · Contour flow · %",settings.contourStrength*100,0,100,5,
                       v->settings.contourStrength=v/100f);
                slider(controls,"4 · Fine-detail ribbon strokes · %",settings.detailBoost*100,0,85,5,
                       v->settings.detailBoost=v/100f);
                controls.addView(text(getString(R.string.ui_emphasizes_existing_eyes_lips_silhouettes_and),12,MUTED,false));
                break;
            case VinylEngine.STRAIGHT:
            case VinylEngine.FADE:
                slider(controls,"2 · Maximum line width · %",settings.lineMaxFraction*100,25,90,1,v->settings.lineMaxFraction=v/100f);
                slider(controls,"3 · Fine-detail strokes · %",settings.detailBoost*100,0,100,5,v->settings.detailBoost=v/100f);
                break;
            case VinylEngine.CONTOUR:
                slider(controls,"2 · Contour flow · %",settings.contourStrength*100,0,100,5,v->settings.contourStrength=v/100f);
                slider(controls,"3 · Fine-detail strokes · %",settings.detailBoost*100,0,100,5,v->settings.detailBoost=v/100f);
                break;
            case VinylEngine.CROSSHATCH:
                slider(controls,"2 · Hatch rotation · °",settings.crossAngleDeg,25,90,5,v->settings.crossAngleDeg=v);
                slider(controls,"3 · Shadow emphasis · %",(1-settings.hatchThreshold)*100,5,65,5,v->settings.hatchThreshold=1-v/100f);
                break;
            case VinylEngine.RINGS:case VinylEngine.RAYS:
                slider(controls,"2 · Focal point X · %",settings.radialFocusX*100,0,100,5,v->settings.radialFocusX=v/100f);
                slider(controls,"3 · Focal point Y · %",settings.radialFocusY*100,0,100,5,v->settings.radialFocusY=v/100f);
                break;
            case VinylEngine.ZIGZAG:
                slider(controls,"2 · Zigzag height · %",settings.waveAmplitude*100,0,22,1,v->settings.waveAmplitude=v/100f);
                slider(controls,"3 · Zigzags per inch",settings.waveCyclesPerInch,.3f,5f,.1f,v->settings.waveCyclesPerInch=v);
                break;
            case VinylEngine.BROKEN:
                slider(controls,"2 · Break response (gamma)",settings.lineGamma,.45f,2.8f,.05f,v->settings.lineGamma=v);
                slider(controls,"3 · Maximum line width · %",settings.lineMaxFraction*100,25,90,1,v->settings.lineMaxFraction=v/100f);
                break;
            case VinylEngine.BEADED:
                slider(controls,"2 · Bead size · %",settings.lineMaxFraction*100,30,95,1,v->settings.lineMaxFraction=v/100f);
                slider(controls,"3 · Bead tonal response",settings.lineGamma,.45f,2.8f,.05f,v->settings.lineGamma=v);
                break;
            case VinylEngine.WAVE:
                slider(controls,"2 · Wave intensity · %",settings.waveAmplitude*100,0,22,1,v->settings.waveAmplitude=v/100f);
                slider(controls,"3 · Maximum line width · %",settings.lineMaxFraction*100,30,78,1,v->settings.lineMaxFraction=v/100f);
                break;
            case VinylEngine.HATCH:
                slider(controls,"2 · Shadow emphasis · %",(1f-settings.hatchThreshold)*100,5,55,1,v->settings.hatchThreshold=1f-v/100f);
                slider(controls,"3 · Maximum line width · %",settings.lineMaxFraction*100,30,78,1,v->settings.lineMaxFraction=v/100f);
                break;
            case VinylEngine.ROUND:case VinylEngine.CROSS:case VinylEngine.STAR:
            case VinylEngine.DASH:case VinylEngine.ELLIPSE:case VinylEngine.DIAMOND:case VinylEngine.HEX:case VinylEngine.SQUARE:
            case VinylEngine.SPIRAL:case VinylEngine.TRIANGLE:case VinylEngine.OCTAGON:
            case VinylEngine.ROUNDED_SQUARE:case VinylEngine.TEARDROP:case VinylEngine.HEART:case VinylEngine.CAPSULE:case VinylEngine.ASCII:case VinylEngine.WORD_SCREEN:
                slider(controls,"2 · Shape size · %",settings.shapeScale*100,30,95,1,v->settings.shapeScale=v/100f);
                break;
            default:
                if(settings.linkedControls){
                    slider(controls,"2 · Dot coverage · %",PercentControlLimits.dotCoverage(settings.masterDensity)*100,35,100,5,v->{settings.masterDensity=PercentControlLimits.dotCoverage(v/100f);setLinkedValues();});
                    slider(controls,"3 · Contrast",settings.masterContrast,.5f,2f,.05f,v->{settings.masterContrast=v;setLinkedValues();});
                }
        }
        if(settings.mode==VinylEngine.CMYK && LineArtEngine.isLineStyle(settings.pattern)){
            action(controls,settings.classicCmykLines?"CMYK lines · Classic v2.24 ✓":"CMYK lines · Modern",()->{
                checkpoint();if(settings.classicCmykLines)ClassicCmykLines.useModern(settings);
                else ClassicCmykLines.apply(settings);
                invalidateToolPanels();requestRender();
            });
            action(controls,"Restore v2.24 line balance",()->{
                checkpoint();ClassicCmykLines.apply(settings);
                invalidateToolPanels();requestRender();
            });
        }

        if(settings.mode==VinylEngine.CMYK && LineArtEngine.isLineStyle(settings.pattern)
                && settings.pattern!=VinylEngine.BROKEN && settings.pattern!=VinylEngine.FADE
                && LineSliderVisibility.lightAreaLines(settings)){
            slider(controls,"Light-area lines · %",settings.lineHighlightRetention*100,0,100,5,
                    v->settings.lineHighlightRetention=v/100f);
            controls.addView(text(getString(R.string.ui_preserves_supported_light_tone_detail_across),12,MUTED,false));
        }
        // RC75: all line-refinement controls are contextual and visible inline.
        if(settings.mode==VinylEngine.CMYK && !settings.classicCmykLines && LineArtEngine.supportsMicroContinuity(settings.pattern)){
            slider(controls,"Portrait line retention · %",settings.linePresenceRecovery*100,0,100,5,v->settings.linePresenceRecovery=v/100f);
            slider(controls,"Outer background reduction · %",settings.portraitBackgroundGuard*100,0,100,5,v->settings.portraitBackgroundGuard=v/100f);
            controls.addView(text(getString(R.string.ui_line_retention_joins_short_supported_under),12,MUTED,false));
        }
        controls.addView(text(String.format(Locale.US,"Base screen: %.1f LPI · %.3f in spacing · minimum requested cut width %.3f in",
            VinylEngine.MM_PER_INCH/Math.max(.1f,settings.masterSpacingMm),VinylEngine.toInches(settings.masterSpacingMm),VinylEngine.toInches(settings.minimumMm)),12,MUTED,false));
        if(settings.mode==VinylEngine.CMYK && hasMidtoneRibbonLayer()){
            slider(controls,"Portrait midtone recovery · %",settings.portraitMidtones*100,0,100,5,v->settings.portraitMidtones=v/100f);
            controls.addView(text(getString(R.string.ui_restores_facial_detail_without_reducing_the),12,MUTED,false));
        }
        if(LineArtEngine.isLineStyle(settings.pattern)&&settings.lineMaxFraction*settings.masterSpacingMm<settings.minimumMm)
            controls.addView(text(getString(R.string.ui_cut_warning_requested_line_width_may),12,0xffFFD093,true));
        if(settings.pattern==VinylEngine.CROSSHATCH||settings.pattern==VinylEngine.HATCH)
            controls.addView(text(getString(R.string.ui_hatching_uses_one_nonintersecting_ribbon_family),12,MUTED,false));
        action(controls,"Reset best settings",()->applyHalftoneStyle(settings.pattern));
        // Minimum cut feature is exclusively in Export > Cut Check; layer screens stay in Layers.
        if(VinylEngine.isShapeStyle(settings.pattern)&&settings.linkedControls)
            slider(controls,"Shape contrast",settings.masterContrast,.5f,2f,.05f,v->{settings.masterContrast=v;setLinkedValues();});
        if(SmartHalftonePresets.ribbons(settings.pattern)){
            if(LineArtEngine.supportsMicroContinuity(settings.pattern)&&settings.linePresenceRecovery<=.001f
                    &&LineSliderVisibility.microGapRecovery(settings.classicCmykLines)){
                slider(controls,"Micro-gap recovery · %",settings.lineContinuity*100,0,100,5,v->settings.lineContinuity=v/100f);
                controls.addView(text(getString(R.string.ui_only_one_nearly_cuttable_sample_between),12,MUTED,false));
            }
            slider(controls,"Tonal response",settings.lineGamma,.35f,2.8f,.05f,v->settings.lineGamma=v);
            if(LineSliderVisibility.contourSmoothing(settings))
                slider(controls,"Contour smoothing",settings.lineSmoothing,0,.9f,.05f,v->settings.lineSmoothing=v);
        }
    }
    private void chooseChannelLineStyle(int index){
        if(index<0||index>=settings.layers.size())return;
        VinylEngine.Layer l=settings.layers.get(index);
        final int[] styles={VinylEngine.LINE,VinylEngine.WAVE,VinylEngine.STRAIGHT,
            VinylEngine.ROUND,VinylEngine.SQUARE,VinylEngine.ELLIPSE,VinylEngine.DIAMOND,
            VinylEngine.HEX,VinylEngine.STAR,VinylEngine.CROSS,VinylEngine.DASH,
            VinylEngine.TRIANGLE,VinylEngine.OCTAGON,VinylEngine.ROUNDED_SQUARE,
            VinylEngine.TEARDROP,VinylEngine.HEART,VinylEngine.CAPSULE,VinylEngine.ASCII,VinylEngine.WORD_SCREEN,
            VinylEngine.CROSSHATCH,VinylEngine.ZIGZAG,VinylEngine.BROKEN,
            VinylEngine.BEADED,VinylEngine.FADE,VinylEngine.HATCH};
        String[] options=new String[styles.length+1];options[0]="Follow main style";
        for(int i=0;i<styles.length;i++)options[i+1]=VinylEngine.PATTERNS[styles[i]];
        new AlertDialog.Builder(this).setTitle(l.name+" · cut pattern")
            .setItems(options,(dialog,option)->{checkpoint();l.lineStyleOverride=option==0?-1:styles[option-1];
                setTool(3);requestRender();})
            .setNegativeButton("Cancel",null).show();
    }
    /** The only per-layer editor. All active renderer layers remain visible as colored swatches. */
    /** A cut count is valid only for the final generation that produced it. */
    private boolean visibleFinalLayerGeometryReady(){
        if(continuousStyle!=null||mazePortraitMode)return specialtyFinalReady();
        if(newStyleActive())return specialtyFinalReady();
        if(layeredCellMode||tessellationMode||tonalStencilMode){
            return alternateReadyGeneration==alternateGeneration&&!alternatePreviewDraft&&
                (layeredCellMode?layeredResult!=null:tessellationMode?tessellationResult!=null:tonalResult!=null);
        }
        return latest!=null&&renderedSerial==serial&&!standardPreviewDraft;
    }
    /** No geometry work on the UI thread: standard totals are precomputed by the render lane. */
    private int[] visibleCutCounts(){
        if(!visibleFinalLayerGeometryReady())return null;
        if(newStyleActive()){
            StyleLayerOperations.Counts[] values=newStyleOperations;
            if(values==null)return null;
            int[] cuts=new int[values.length];
            for(int i=0;i<cuts.length;i++)cuts[i]=values[i].bladeContours;
            return cuts;
        }
        if(layeredCellMode)return LayerCutCounts.layeredCell(layeredResult);
        if(tessellationMode)return LayerCutCounts.mosaic(tessellationResult);
        if(tonalStencilMode)return tonalCutCountGeneration==alternateReadyGeneration?tonalCutCounts:null;
        return standardCountSerial==renderedSerial?standardLayerCutCounts:null;
    }
    /** Refresh existing labels without replacing cards, sliders, swatches, or scroll position. */
    private void updateVisibleLayerCutLabels(){
        if(activeWorkspace!=4||tool!=3||sheetContent==null)return;
        final int[] counts=visibleCutCounts();
        final boolean geometryReady=visibleFinalLayerGeometryReady();
        int count=(layeredCellMode||tessellationMode)?tessLayerCount:
            tonalStencilMode?tonalLayerCount:settings.layers.size();
        for(int i=0;i<count;i++){
            View candidate=sheetContent.findViewWithTag(CUT_COUNT_TAG_PREFIX+i);
            if(!(candidate instanceof TextView))continue;
            TextView value=(TextView)candidate;
            boolean skipped=!layeredCellMode&&!tessellationMode&&!tonalStencilMode&&
                i<settings.layers.size()&&settings.layers.get(i).excludeFromExport;
            String label=skipped?
                (counts!=null&&i<counts.length?String.format(Locale.US,
                 "0 exported cuts · %,d design contours retained",counts[i]):"Sheet excluded from export"):
                counts!=null&&i<counts.length?
                String.format(Locale.US,"%,d cuts",counts[i]):
                geometryReady?"Counting cuts…":"Cuts pending render";
            if(!label.contentEquals(value.getText()))value.setText(label);
            value.setTextColor(counts!=null?ACCENT:MUTED);
        }
    }

    private static boolean hasSolidBase(VinylEngine.Settings s){
        for(VinylEngine.Layer l:s.layers)if(l.silhouetteBase)return true;
        return false;
    }
    /** Add one actual physical vinyl sheet, never relabel a line screen as solid. */
    private void addSolidSnipBackground(int color,boolean hideSubstrateLayer){
        if(alternateActive()||continuousStyle!=null||mazePortraitMode){toast(getString(R.string.ui_solid_snip_base_is_available_for));return;}
        if(hasSolidBase(settings)){toast(getString(R.string.ui_this_artwork_already_has_a_filled));return;}
        if(settings.layers.size()>=8){toast(getString(R.string.ui_eight_physical_layers_already_selected_remove));return;}
        if(settings.snips==null||settings.snips.isEmpty()){
            toast(getString(R.string.ui_create_a_snip_outline_in_prepare));return;
        }
        final int previousHue=standardHueDegrees;
        checkpoint();
        VinylEngine.Layer base=new VinylEngine.Layer("Solid Snip background",color);
        base.silhouetteBase=true;base.cmykChannel=-1;base.spacing=settings.masterSpacingMm;
        settings.layers.add(0,base);
        if(hideSubstrateLayer)for(int i=1;i<settings.layers.size();i++){
            VinylEngine.Layer l=settings.layers.get(i);
            int dr=((l.color>>16)&255)-((settings.paper>>16)&255);
            int dg=((l.color>>8)&255)-((settings.paper>>8)&255);
            int db=(l.color&255)-(settings.paper&255);
            if(l.enabled&&dr*dr+dg*dg+db*db<=settings.substrateTolerance*settings.substrateTolerance){
                l.enabled=false;l.autoDisabledForBase=true;
            }
        }
        if(settings.mode==VinylEngine.CMYK)VinylEngine.CmykExtendedStack.canonicalize(settings.layers);
        chosen=-1;rebaseStandardHueAfterCountEdit(previousHue);rebuildLayers();setToolInternal(3);requestRender();
        toast(getString(R.string.ui_solid_snip_l1_added_review_its));
    }
    private void removeSolidSnipBackground(){
        if(!hasSolidBase(settings))return;
        final int previousHue=standardHueDegrees;
        checkpoint();settings.layers.removeIf(l->l.silhouetteBase);
        for(VinylEngine.Layer l:settings.layers)if(l.autoDisabledForBase){l.enabled=true;l.autoDisabledForBase=false;}
        if(settings.mode==VinylEngine.CMYK)VinylEngine.CmykExtendedStack.canonicalize(settings.layers);
        chosen=-1;rebaseStandardHueAfterCountEdit(previousHue);rebuildLayers();setToolInternal(3);requestRender();
    }
    private void offerSolidSnipBackground(LinearLayout host){
        if(!hasSolidBase(settings))return;
        section(host,"Legacy solid Snip background");
        host.addView(text("This previously saved project already has a real filled base. " +
            "It is not duplicated by automatic underlays.",12,ACCENT,false));
        action(host,"Remove legacy base (restore saved detail colors)",this::removeSolidSnipBackground);
    }
    private void unifiedLayersPanel(){
        if(continuousStyle!=null||mazePortraitMode){
            LinearLayout single=group();section(single,"Single Line · toolpaths");
            if(!specialtyFinalReady()||continuousBladeContours<0||continuousPenPaths<0){
                single.addView(text(getString(R.string.ui_counting_final_geometry),12,MUTED,false));return;
            }
            int blades=continuousBladeContours,pens=continuousPenPaths;
            single.addView(text(getString(R.string.ui_l1_black_000000),13,TEXT,true));
            single.addView(text(String.format(Locale.US,"%,d blade %s",blades,blades==1?"cut":"cuts"),13,ACCENT,true));
            if(mazePortraitMode)single.addView(text(String.format(Locale.US,"%,d wall segments for pen/engraving",mazeWallSegments),12,MUTED,false));
            else single.addView(text(String.format(Locale.US,"%,d pen/engrave paths",pens),12,MUTED,false));
            single.addView(text(blades>0?
                "Closed contours (including holes) are separate blade traversals. Registration marks are excluded.":
                "Open centerlines are drawing/engraving paths, not adhesive-vinyl cuts.",12,MUTED,false));
            return;
        }
        final boolean cells=layeredCellMode, tess=tessellationMode, tonal=tonalStencilMode;
        final int count=(cells||tess)?Math.max(0,Math.min(tessLayerCount,tessColors.length)):
            tonal?Math.max(0,Math.min(tonalLayerCount,tonalColorOverrides.length)):settings.layers.size();
        final int requestedSelection=(cells||tess)?tessSelectedLayer:tonal?tonalSelectedLayer:chosen;
        final int selected=requestedSelection>=0&&requestedSelection<count?requestedSelection:-1;
        LinearLayout overview=group(); // Swatches are the first controls; no duplicate section heading.
        if(count==0){overview.addView(text(getString(R.string.ui_no_valid_vinyl_layers_choose_a),12,MUTED,false));return;}
        int[] colors=new int[count];String[] labels=new String[count];boolean[] populated=new boolean[count];
        int[] generatedTonal=tonal?tonalDisplayedColors():previewLayerColors(count);
        for(int i=0;i<count;i++){
            if(cells||tess)colors[i]=tessColors[i];
            else if(tonal)colors[i]=tonalResult!=null&&tonalResult.layers.size()==count&&alternateReadyGeneration==alternateGeneration?
                tonalResult.layers.get(i).color:generatedTonal[i];
            else colors[i]=settings.layers.get(i).color;
            labels[i]="L"+PhysicalLayerLayout.physicalNumber(i,underlayRequested());
            populated[i]=cells?layeredResult==null||alternateReadyGeneration!=alternateGeneration||
                layeredResult.pieceCounts==null||i>=layeredResult.pieceCounts.length||layeredResult.pieceCounts[i]>0:
                tess?tessellationResult==null||alternateReadyGeneration!=alternateGeneration||
                tessellationResult.counts==null||i>=tessellationResult.counts.length||tessellationResult.counts[i]>0:
                tonal?tonalResult==null||tonalResult.layers.size()!=count||!tonalResult.isEmpty(i):settings.layers.get(i).enabled;
        }
        // Source-color masks are computed from this photograph. Never display
        // arbitrary fallback colors as though they were the rendered vinyl.
        final boolean tonalColorsReady=!tonal || tonalResult!=null &&
            tonalResult.layers.size()==count && alternateReadyGeneration==alternateGeneration;
        if(underlayRequested()){
            layerCountPicker(overview,count,n->{
                if(cells||tess){chooseTessCount(n);}
                else countLayers(n);
            });
            showAutomaticSilhouette(overview);
        }
        if(silhouetteOnly()){
            layerCountPicker(overview,count,n->{
                if(n>=2){selectSilhouetteOnly(false);if(n!=count){
                    if(tessellationMode){reduceTessPalette(n);tessLayerCount=n;requestAlternate();}
                    else countLayers(n);
                }}
            });
            return;
        }
        if(tonalColorsReady){
            // The filled base and detail layer cards below are now the sole
            // physical layer stack; don't duplicate their swatches above them.
            if(!underlayRequested())colorSwatchGrid(overview,colors,labels,populated,selected,idx->selectUnifiedLayer(idx));
        }else{
            overview.addView(text("Preparing photo-derived layer colors…",13,MUTED,false));
        }
        if(tonal){section(overview,"Source color masks and stacking");tonalPicker(overview);}
        if(!cells&&!tess&&!tonal&&hasSolidBase(settings))offerSolidSnipBackground(overview);
        if(selected>=0||basePreviewActive())action(overview,"Show complete artwork",()->selectUnifiedLayer(-1));
        if(!underlayRequested())layerCountPicker(overview,count,n->{
            if(cells||tess)chooseTessCount(n);
            else if(tonal)chooseTonalCount(n);
            else countLayers(n);
        });
        if(!cells&&!tess&&!tonal&&settings.mode==VinylEngine.CMYK){
            if(CmykStackOrder.needsCorrection(settings)){
                overview.addView(text(getString(R.string.ui_older_cmyk_stack_detected_yellow_should),12,0xffFFD093,true));
                action(overview,"Correct physical CMYK order · Y / C / M / K",()->{
                    checkpoint();CmykStackOrder.normalize(settings);chosen=-1;
                    rebuildLayers();setToolInternal(3);requestRender();
                });
            }
            LinearLayout angles=horizontal(overview);
            action(angles,"Classic CMYK angles",()->{applyScreenPreset(VinylEngine.ANGLES_CLASSIC);invalidateToolPanels();setToolInternal(3);});
            action(angles,"Alternate angles",()->{applyScreenPreset(VinylEngine.ANGLES_PHOTOSHOP);invalidateToolPanels();setToolInternal(3);});
            // Explicitly expose the existing vinyl-only separation controls;
            // these affect tonal coverage, not the line geometry algorithm.
            LinearLayout balance=group();section(balance,"CMYK shadow balance");
            slider(balance,"Protect black in colored areas · %",settings.blackColorProtection*100,0,80,5,
                v->settings.blackColorProtection=v/100f);
            slider(balance,"Recover CMY in shadows · %",settings.shadowColorRecovery*100,0,85,5,
                v->settings.shadowColorRecovery=v/100f);
            balance.addView(text("Black is the top opaque vinyl sheet. More shadow color can be hidden underneath it. Inspect each layer independently.",12,MUTED,false));
            action(balance,"Reset CMYK balance",()->{checkpoint();if(settings.classicCmykLines)ClassicCmykLines.apply(settings);else CmykPortraitDefaults.applyPreset(settings);invalidateToolPanels();requestRender();});
        }
        if(cells&&overprintCellKind==0){
            action(overview,"Cell backdrop: "+(layeredBackground<0?"Follow photograph":"Solid L"+(layeredBackground+1)),()->{
                String[] choices=new String[tessLayerCount+1];choices[0]="Photo matched / varied";
                for(int i=0;i<tessLayerCount;i++)choices[i+1]="Solid L"+(i+1)+" · "+colorHex(tessColors[i]);
                new AlertDialog.Builder(this).setTitle(getString(R.string.ui_background_for_every_cell))
                    .setSingleChoiceItems(choices,layeredBackground+1,(d,index)->{
                        checkpoint();layeredBackground=index-1;d.dismiss();
                        setToolInternal(3);requestAlternate();
                    }).setNegativeButton("Cancel",null).show();
            });
        }
        // Cache or read the actual final cut paths, never estimate from rendered pixels.
        // Registration marks are separate and are excluded from these artwork counts.
        final boolean finalGeometryReady=visibleFinalLayerGeometryReady();
        final int[] cutCounts=visibleCutCounts();
        // Global cell density, tile detail and tonal detail live only in Adjust.
        // Layer-specific motif/visibility/color controls remain below.
        for(int i=0;i<count;i++){
            final int index=i,color=colors[i];
            LinearLayout card=group();
            LinearLayout row=horizontal(card);
            TextView swatch=text(labels[i],13,contrastText(color),true);swatch.setGravity(Gravity.CENTER);
            swatch.setBackground(outlined(color,selected==i?ACCENT:0xff516171,12));
            row.addView(swatch,new LinearLayout.LayoutParams(dp(60),dp(52)));
            LinearLayout detail=new LinearLayout(this);detail.setOrientation(LinearLayout.VERTICAL);
            detail.setPadding(dp(10),0,0,0);row.addView(detail,new LinearLayout.LayoutParams(0,-2,1));
            detail.addView(text(String.format(Locale.US,"%02d  %s",i+(underlayRequested()?2:1),labels[i])+" · "+colorHex(color),13,TEXT,true));
            String familyLabel=cells?(overprintCellKind>0?(i==0?"Solid Snip foundation":
                "Independent overprint cell"):
                layeredLayerFamilies[i]<0?"Photo adaptive":layeredFamilyName(layeredLayerFamilies[i])):
                tess?(stippleActive()?"Weighted stipple dot":"Mosaic tile"):tonal?"Source-color cut":settings.layers.get(i).lineStyleOverride<0?
                VinylEngine.PATTERNS[Math.max(0,Math.min(VinylEngine.PATTERNS.length-1,settings.pattern))]:
                VinylEngine.PATTERNS[Math.max(0,Math.min(VinylEngine.PATTERNS.length-1,settings.layers.get(i).lineStyleOverride))];
            detail.addView(text(familyLabel,12,MUTED,false));
            if(cells&&overprintCellKind>0&&layeredResult!=null&&
               alternateReadyGeneration==alternateGeneration&&!alternatePreviewDraft&&
               layeredResult.swatchVisibility!=null){
                OpaqueOverlaySwatchPlanner.Visibility vis=layeredResult.swatchVisibility;
                if(i<vis.visibleArea.length)
                    detail.addView(text("~"+vis.visiblePercent(i)+"% of finished portrait visible"+
                        (vis.hiddenPercent(i)>0?" · "+vis.hiddenPercent(i)+"% of this cut sheet covered above":""),
                        12,MUTED,false));
            }
            final boolean standardLayer=!cells&&!tess&&!tonal;
            final boolean isLegacyBase=standardLayer&&i<settings.layers.size()&&settings.layers.get(i).silhouetteBase;
            final boolean sheetSkipped=standardLayer&&i<settings.layers.size()&&settings.layers.get(i).excludeFromExport;
            String cutLabel=sheetSkipped?
                (cutCounts!=null&&i<cutCounts.length?String.format(Locale.US,
                 "0 exported cuts · %,d design contours retained",cutCounts[i]):"Sheet excluded from export"):
                standardLayer&&i<settings.layers.size()&&!settings.layers.get(i).enabled?
                "Disabled · 0 exported cuts":cutCounts!=null&&i<cutCounts.length?
                String.format(Locale.US,"%,d %s",cutCounts[i],isLegacyBase?"base contours":"cuts"):
                finalGeometryReady?"Counting cuts…":"Cuts pending render";
            TextView cutsView=text(cutLabel,12,cutCounts!=null?ACCENT:MUTED,true);
            cutsView.setTag(CUT_COUNT_TAG_PREFIX+i);
            detail.addView(cutsView);
            action(row,selected==i?"Close":"Edit",()->selectUnifiedLayer(selected==index?-1:index));
            if(count>2&&!(cells&&overprintCellKind>0&&index==0)&&(!(!cells&&!tess&&!tonal&&hasSolidBase(settings)&&
                  settings.layers.get(index).silhouetteBase)))
                action(card,"Delete L"+(index+(underlayRequested()?2:1)),
                    ()->confirmRemoveUnifiedLayer(index));
            // Visible on every standard sheet, including the anchored CMYK channels.
            // Excluding a sheet is separate from hiding it in the renderer; no
            // empty SVG is written and another sheet's contours are not changed.
            if(!cells&&!tess&&!tonal){
                final VinylEngine.Layer cutSheet=settings.layers.get(index);
                action(card,cutSheet.excludeFromExport?"Restore this cut sheet":"Skip this cut sheet",()->{
                    checkpoint();cutSheet.excludeFromExport=!cutSheet.excludeFromExport;
                    if(latest!=null&&index<latest.settings.layers.size())
                        latest.settings.layers.get(index).excludeFromExport=cutSheet.excludeFromExport;
                    invalidateToolPanels();setToolInternal(3);
                    if(artwork!=null)artwork.invalidate();
                });
            }
            if(selected!=i)continue;
            if(!cells&&!tess&&!tonal&&settings.mode!=VinylEngine.CMYK){
                // Operate on the selected vinyl sheet directly, not through a second "Stack controls" page.
                LinearLayout placement=horizontal(card);
                action(placement,"Move up",()->moveLayer(index,-1));
                action(placement,"Move down",()->moveLayer(index,1));
            }
            LinearLayout actions=horizontal(card);
            action(actions,"Color wheel",()->{
                if(cells||tess)editTessColor(index);else if(tonal)editTonalColor(index);else editColor(index);
            });
            if(cells||(!tonal&&!tess&&settings.mode!=VinylEngine.STENCIL)){
                if(cells&&overprintCellKind==0){
                    action(card,"Cell motif: "+(layeredLayerFamilies[i]<0?"Photo adaptive":layeredFamilyName(layeredLayerFamilies[i]))+"  ▾",
                        ()->chooseLayeredLayerFamily(index));
                    card.addView(text(getString(R.string.ui_cells_dominated_by_this_photo_color),12,MUTED,false));
                }else if(!cells){
                    VinylEngine.Layer layer=settings.layers.get(index);
                    action(card,"Shape / line: "+(layer.lineStyleOverride<0?"Use global style":VinylEngine.PATTERNS[layer.lineStyleOverride])+"  ▾",
                        ()->chooseChannelLineStyle(index));
                    section(card,"This layer's screen");
                    card.addView(text(getString(R.string.ui_independent_screen_controls_affect_only_this),12,MUTED,false));
                    inchSlider(card,"Line / dot spacing",layer.spacing,.065f,.40f,.005f,v->{settings.linkedControls=false;layer.spacing=v;});
                    slider(card,"Screen angle · °",layer.angle,0,179,1,v->{settings.anglePreset=VinylEngine.ANGLES_CUSTOM;layer.angle=v;});
                    slider(card,"Coverage",layer.density,.30f,2f,.05f,v->{settings.linkedControls=false;layer.density=v;});
                    slider(card,"Contrast",layer.contrast,.5f,2f,.05f,v->{settings.linkedControls=false;layer.contrast=v;});
                    slider(card,"Line width · %",layer.lineWidthScale*100,40,155,5,v->layer.lineWidthScale=v/100f);
                    slider(card,"Tone response · gamma (0=shared)",layer.lineGammaOverride,0,3f,.10f,v->layer.lineGammaOverride=v);
                    LinearLayout toggle=horizontal(card);
                    action(toggle,layer.enabled?"Hide layer":"Show layer",()->{checkpoint();layer.enabled=!layer.enabled;rebuildLayers();setToolInternal(3);requestRender();});
                    action(toggle,"Reset screen",()->{checkpoint();layer.spacing=settings.masterSpacingMm;layer.density=settings.masterDensity;layer.contrast=settings.masterContrast;layer.lineWidthScale=1f;layer.lineGammaOverride=0;layer.lineStyleOverride=-1;
                        if(settings.anglePreset!=VinylEngine.ANGLES_CUSTOM)VinylEngine.applyAnglePreset(settings);setToolInternal(3);requestRender();});
                }
            }else if(tess){
                if(stippleActive()){
                    final MultiColorStippleEngine.Options so=stippleSettings;
                    action(card,so.enabled[index]?"Enabled · tap to hide":"Disabled · tap to show",()->{
                        checkpoint();so.enabled[index]=!so.enabled[index];setToolInternal(3);requestAlternate();});
                    slider(card,"Layer density · %",so.density[index]*100,0,100,5,v->{so.density[index]=v/100f;requestAlternate();});
                    float[] dotRange=PhysicalSliderFloor.range(.4f,3f,PhysicalSliderFloor.featureMm(settings.minimumMm),.1f); // D8
                    slider(card,"Minimum dot diameter · mm",PhysicalSliderFloor.effective(so.minDotMm[index],dotRange[0]),dotRange[0],dotRange[1],.1f,v->{so.minDotMm[index]=v;requestAlternate();});
                    if(dotRange[0]>.4f)card.addView(text(String.format(Locale.US,"Starts at the material minimum (%.2f mm).",dotRange[0]),12,MUTED,false));
                    action(card,"Inspect vector cuts for this layer",()->showStippleCutPaths(index));
                    section(card,"Physical stacking order · drag the handle to reorder");
                    stippleOrderNormalize();
                    LinearLayout stack=horizontal(card);
                    for(int pos=0;pos<tessLayerCount;pos++){
                        final int position=pos,owner=stippleSettings.order[pos];
                        TextView drag=text("L"+(owner+1)+"  ≡",12,TEXT,true);
                        drag.setPadding(dp(8),dp(8),dp(8),dp(8));
                        drag.setBackground(outlined(owner==index?SELECTED:StudioTheme.SURFACE_HIGH,0xff546274,6));
                        stack.addView(drag,new LinearLayout.LayoutParams(0,-2,1));
                        drag.setOnLongClickListener(v->{v.startDragAndDrop(android.content.ClipData.newPlainText("stipple-layer",""+owner),
                            new View.DragShadowBuilder(v),null,0);return true;});
                        drag.setOnDragListener((v,event)->{
                            if(event.getAction()==DragEvent.ACTION_DRAG_STARTED)return true;
                            if(event.getAction()==DragEvent.ACTION_DROP){
                                try{int from=Integer.parseInt(event.getClipData().getItemAt(0).getText().toString());
                                    repositionStipple(from,position);}catch(Exception ignored){}return true;
                            }return true;
                        });
                    }
                }else card.addView(text(getString(R.string.ui_one_closed_polygon_per_tile_the),12,MUTED,false));
            }else if(tonal)card.addView(text(getString(R.string.ui_vector_mask_follows_this_photo_color),12,MUTED,false));
            else card.addView(text(getString(R.string.ui_flat_contour_stencil_its_color_regions),12,MUTED,false));
            if(cells&&layeredResult!=null&&alternateReadyGeneration==alternateGeneration&&
               layeredResult.pieceCounts!=null&&index<layeredResult.pieceCounts.length)
                card.addView(text(String.format(Locale.US,"%,d separate pieces · holes counted as additional cuts",
                    layeredResult.pieceCounts[index]),12,MUTED,false));
        }
    }
    private void selectUnifiedLayer(int index){
        if(layeredCellMode){tessSelectedLayer=index;requestSelectedPreview();}
        else if(tessellationMode){tessSelectedLayer=index;requestSelectedPreview();}
        else if(tonalStencilMode){tonalSelectedLayer=index;requestSelectedPreview();}
        else{chosen=index;rebuildLayers();
            // M3: inspecting a layer keeps the user's zoom and pan (it used to reset them).
            if(artwork!=null)artwork.invalidate();}
        previewBaseOnly=false;
        invalidateToolPanels();setToolInternal(3);
    }
    private void chooseLayeredLayerFamily(int index){
        if(!layeredCellMode||index<0||index>=tessLayerCount)return;
        final String[] names={"Follow global / photo adaptive","Ellipse","Rounded square","Diamond","Hexagon","Octagon","Capsule / Edge","Soft star","Scallop"};
        new AlertDialog.Builder(this).setTitle("Layer "+(index+1)+" · cell motif")
            .setSingleChoiceItems(names,layeredLayerFamilies[index]+1,(dialog,choice)->{
                checkpoint();layeredLayerFamilies[index]=choice==0?-1:choice;dialog.dismiss();invalidateToolPanels();setToolInternal(3);requestAlternate();
            }).setNegativeButton("Cancel",null).show();
    }

    /** Responsive 2–8 swatch grid. 2–4 fit in one row, 5–8 use two rows. */
    private void colorSwatchGrid(LinearLayout parent,int[] colors,String[] labels,boolean[] populated,int selected,java.util.function.IntConsumer onChoose){
        int columns=ColorWorkspacePalette.columns(colors.length);
        for(int start=0;start<colors.length;start+=columns){
            LinearLayout row=horizontal(parent);
            for(int i=start;i<Math.min(start+columns,colors.length);i++){
                final int index=i;final int color=colors[i];boolean valid=populated==null||populated[i];
                LinearLayout cell=new LinearLayout(this);cell.setOrientation(LinearLayout.VERTICAL);
                cell.setPadding(dp(3),dp(2),dp(3),dp(3));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(73),1);lp.rightMargin=dp(3);lp.bottomMargin=dp(4);row.addView(cell,lp);
                TextView swatch=text(labels[i],13,contrastText(color),true);swatch.setGravity(Gravity.CENTER);
                swatch.setBackground(outlined(color,selected==i?ACCENT:0xff536172,10));
                swatch.setMinHeight(dp(48));cell.addView(swatch,new LinearLayout.LayoutParams(-1,dp(49)));
                TextView label=text(valid?"#"+String.format(Locale.US,"%06X",color&0xffffff):"EMPTY",11,valid?MUTED:0xffFFB08C,false);
                label.setGravity(Gravity.CENTER);cell.addView(label,new LinearLayout.LayoutParams(-1,dp(20)));
                cell.setContentDescription(labels[i]+" color #"+String.format(Locale.US,"%06X",color&0xffffff)+(valid?"":" empty layer"));
                cell.setOnClickListener(v->onChoose.accept(index));
            }
        }
    }




    /** Number the control by actual exportable sheets, not by internal color slots. */
    private void layerCountPicker(LinearLayout parent,int current,java.util.function.IntConsumer choose){
        final boolean base=underlayRequested();
        section(parent,base?"Physical vinyl sheets":"Layer count");
        if(base)parent.addView(text(silhouetteOnly()?"1 sheet · solid Snip silhouette only; detail settings are retained.":
            PhysicalLayerLayout.physicalCount(current,true)+" sheets · L1 filled Snip + "+current+" detail colors.",12,ACCENT,true));
        LinearLayout row=horizontal(parent);
        for(int physical=base?1:2;physical<=(base?9:
            (newStyleActive()&&(VinylStyleSuite.isCraft(newStyleId)||
                newStyleId==VinylStyleSuite.POSTER_STENCIL)?9:8));physical++){
            final int n=physical;
            int detailCount=base?n-1:n;
            boolean styleAllows=!newStyleActive() || (detailCount>=NewStyleColorSlots.minimumSelectable(newStyleId)&&detailCount<=NewStyleColorSlots.maximumSelectable(newStyleId));
            boolean available=(base&&n==1)||((!base||n>=3)&&styleAllows);
            boolean active=base?(n==1?silhouetteOnly():!silhouetteOnly()&&n==current+1):n==current;
            TextView pill=text(Integer.toString(n),13,available?TEXT:MUTED,active);
            pill.setGravity(Gravity.CENTER);
            pill.setBackground(outlined(active?SELECTED:0xff303943,active?ACCENT:0xff506071,10));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);lp.rightMargin=dp(3);row.addView(pill,lp);
            pill.setContentDescription(!base?n+" vinyl colors":n==1?"One sheet: solid silhouette only":
                n==2?"Two sheets unavailable: this engine requires two detail colors":n+" physical sheets including silhouette");
            pill.setOnClickListener(v->{
                if(!available){toast("This layer count is not supported by this style");return;}
                if(base){
                    if(n==1){selectSilhouetteOnly(true);return;}
                    if(n==2){toast(getString(R.string.ui_this_style_needs_at_least_two));return;}
                    if(silhouetteOnlyMode)selectSilhouetteOnly(false);
                    choose.accept(n-1);
                }else choose.accept(n);
            });
        }
    }
    private void applyTonalPalette(int paletteId){
        if(paletteId<0){
            checkpoint();Arrays.fill(tonalColorOverrides,0);tonalPaletteRanks=SmartPaletteReduction.identity(8);
            tonalMatchMyVinyl=false;selectedPaletteKey="photo";
        }else{
            int[] colors=ColorWorkspacePalette.selectForSurface(
                VinylPalettes.get(paletteId,8),tonalLayerCount,settings.paper);
            if(!tonalReadyForPaletteEdit())return;
            checkpoint();if(!applyTonalSourcePaletteColors(colors,tonalHueDegrees))return;
            int[] full=ColorWorkspacePalette.arrangedForSurface(VinylPalettes.get(paletteId,8),tonalLayerCount,settings.paper);
            parkTonalPresetTail(full,tonalLayerCount);
            selectedPaletteKey="preset:"+paletteId;
        }
        tonalSelectedLayer=-1;requestAlternate();setToolInternal(2);
    }
    private void applyTessPalette(int id){
        checkpoint();tessPalette=ColorWorkspacePalette.TESS_NAMES[id];
        int[] input=ColorWorkspacePalette.tessPalette(id);
        setTessBasePalette(layeredCellMode&&overprintCellKind>0?
            OpaqueOverlayPhotoPalette.arrange(input,tessLayerCount,preparedPhotoColorPixels):
            ColorWorkspacePalette.arrangedForSurface(input,tessLayerCount,settings.paper));
        tessSelectedLayer=-1;requestAlternate();setToolInternal(2);
    }
    /** When a preset's layer count changes, match the visible palette preview exactly. */
    private void refreshTessPaletteForCount(){
        for(int id=0;id<ColorWorkspacePalette.TESS_NAMES.length;id++)
            if(ColorWorkspacePalette.TESS_NAMES[id].equals(tessPalette)){
                int offset=tessHueDegrees;
                tessHueBaseColors=ColorWorkspacePalette.arrangedForSurface(ColorWorkspacePalette.tessPalette(id),tessLayerCount,settings.paper);
                tessPaletteRanks=SmartPaletteReduction.identity(8);
                applyTessHue(offset); // Mounting-surface changes should not reset vinyl hue.
                return;
            }
    }
    /** Vinyl-only hue transformation. Mounting surface and photo stay unchanged.
     * Slider gestures use debounced specialty previews, then a full redraw on release.
     */
    private void vinylPaletteHueControl(LinearLayout host){
        section(host,"Vinyl palette hue · all active swatches");
        LinearLayout header=horizontal(host);
        TextView description=text(getString(R.string.ui_hue_shift),14,TEXT,false);
        header.addView(description,new LinearLayout.LayoutParams(0,-2,1));
        TextView amount=text(String.format(Locale.US,"%+d°",currentVinylHue()),14,ACCENT,true);
        header.addView(amount);
        SeekBar bar=new SeekBar(this);bar.setMax(360);bar.setProgress(currentVinylHue()+180);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        bar.setContentDescription(bar.getContext().getString(R.string.quality_rotate_all_vinyl_swatch_hues_minus_180_to_plus_180_degrees));
        host.addView(bar,new LinearLayout.LayoutParams(-1,dp(43)));
        // Small live legend, distinct from the editable full swatch cards below.
        LinearLayout preview=horizontal(host);
        final ArrayList<View> previewChips=new ArrayList<>();
        int[] chipColors=huePreviewColors();
        for(int i=0;i<chipColors.length;i++){
            View chip=new View(this);chip.setBackground(shape(chipColors[i],5));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(21),1);
            p.rightMargin=dp(4);preview.addView(chip,p);previewChips.add(chip);
        }
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onStartTrackingTouch(SeekBar seek){checkpoint();sliderDragging=true;}
            @Override public void onProgressChanged(SeekBar seek,int progress,boolean fromUser){
                if(!fromUser)return;
                int newValue=progress-180;
                if(newValue==currentVinylHue())return;
                applyCurrentVinylHue(newValue);
                if(currentVinylHue()!=newValue){seek.setProgress(currentVinylHue()+180);return;}
                amount.setText(String.format(Locale.US,"%+d°",newValue));
                int[] updated=huePreviewColors();
                for(int i=0;i<previewChips.size();i++)
                    previewChips.get(i).setBackground(shape(updated[i],5));
                refreshHueArtwork();
            }
            @Override public void onStopTrackingTouch(SeekBar seek){
                sliderDragging=false;
                refreshShelfPanel(); // Refresh all eight editable swatch cards after drag.
                refreshHueArtwork();
            }
        });
        LinearLayout paletteActions=horizontal(host);
        action(paletteActions,"Reset hue to 0°",()->{
            if(currentVinylHue()==0)return;
            checkpoint();applyCurrentVinylHue(0);refreshShelfPanel();refreshHueArtwork();
        });
        // ADR-0006 (accepted, option A): one action, named for what it does. Classic CMYK swaps
        // the vinyl colours over fixed separations; every other family mirrors tones.
        boolean cmykSwap=!newStyleActive()&&!tessellationMode&&!layeredCellMode&&!tonalStencilMode&&settings.mode==VinylEngine.CMYK;
        TextView reverse=action(paletteActions,getString(cmykSwap?R.string.m5_reverse_label_cmyk:R.string.m5_reverse_label_tones),this::reverseActiveVinylPalette);
        reverse.setContentDescription(getString(cmykSwap?R.string.m5_reverse_help_cmyk:R.string.m5_reverse_help_tones));
        host.addView(text("Changes vinyl swatches together, not the mounting surface. " +
            "Preserves the original palette for reset; actual vinyl colors should be matched before cutting.",12,MUTED,false));
    }
    // Named swatches live in app-private preferences, outside project documents.
    // Built-in presets are never inserted here and cannot consume library slots.
    private static final String PALETTE_LIBRARY_PREFS="vinylstudio-palettes";
    private static final String PALETTE_LIBRARY_KEY="named_library_v2";
    private boolean showAllSavedPalettes=false;

    private java.util.ArrayList<SavedVinylPaletteLibrary.Entry> readSavedPalettes(){
        android.content.SharedPreferences prefs=getSharedPreferences(PALETTE_LIBRARY_PREFS,MODE_PRIVATE);
        String encoded=prefs.getString(PALETTE_LIBRARY_KEY,null);
        java.util.ArrayList<SavedVinylPaletteLibrary.Entry> result=new java.util.ArrayList<>();
        if(encoded!=null){
            try{
                JSONArray entries=new JSONArray(encoded);
                for(int i=0;i<entries.length()&&result.size()<SavedVinylPaletteLibrary.MAX_ENTRIES;i++){
                    try{
                        JSONObject item=entries.getJSONObject(i);
                        JSONArray a=item.getJSONArray("colors");
                        if(a.length()!=8&&a.length()!=9)continue;
                        int[] c=new int[a.length()];for(int k=0;k<c.length;k++)c[k]=a.getInt(k);
                        int[] accents=new int[4];
                        JSONArray accentJson=item.optJSONArray("cmykAccentBases");
                        if(accentJson!=null&&accentJson.length()==4)
                            for(int k=0;k<4;k++)accents[k]=accentJson.getInt(k);
                        SavedVinylPaletteLibrary.Entry entry=new SavedVinylPaletteLibrary.Entry(
                            item.getString("id"),item.getString("name"),c,item.optInt("hue",0),
                            item.optBoolean("cmykSource",false),item.optInt("cmykBlackBase",0),accents);
                        if(SavedVinylPaletteLibrary.findId(result,entry.id)==null &&
                           SavedVinylPaletteLibrary.findName(result,entry.name)==null)result.add(entry);
                    }catch(Exception malformed){android.util.Log.w("VinylStudio","Skipping damaged saved palette",malformed);}
                }
            }catch(Exception damaged){
                android.util.Log.e("VinylStudio","Could not read palette library",damaged);
                toast(getString(R.string.ui_saved_palette_library_could_not_be));
            }
            return result;
        }
        // One-time, non-destructive migration from the single favorite in RC19.
        String old=prefs.getString("favorite",null);
        if(old!=null){
            try{
                JSONObject legacy=new JSONObject(old);
                JSONArray a=legacy.getJSONArray("colors");
                if(a.length()==8){
                    int[] c=new int[8];for(int i=0;i<8;i++)c[i]=a.getInt(i);
                    result.add(new SavedVinylPaletteLibrary.Entry(java.util.UUID.randomUUID().toString(),
                        "Imported favorite",c,legacy.optInt("hue",0)));
                    // Never delete the legacy favorite until the new library is written.
                    writeSavedPalettes(result);
                }
            }catch(Exception malformed){android.util.Log.w("VinylStudio","Could not migrate legacy palette",malformed);}
        }
        return result;
    }
    private boolean writeSavedPalettes(java.util.List<SavedVinylPaletteLibrary.Entry> items){
        if(items.size()>SavedVinylPaletteLibrary.MAX_ENTRIES)return false;
        try{
            JSONArray encoded=new JSONArray();
            for(SavedVinylPaletteLibrary.Entry entry:items){
                JSONObject j=new JSONObject();JSONArray colors=new JSONArray();
                for(int c:entry.baseColors)colors.put(c);
                j.put("id",entry.id);j.put("name",entry.name);
                JSONArray accents=new JSONArray();
                for(int c:entry.cmykAccentBases)accents.put(c);
                j.put("colors",colors);j.put("hue",entry.hueDegrees);
                j.put("cmykSource",entry.cmykSource);
                j.put("cmykBlackBase",entry.cmykBlackBase);
                j.put("cmykAccentBases",accents);encoded.put(j);
            }
            // commit() is intentional: never announce success until the library is stored.
            return getSharedPreferences(PALETTE_LIBRARY_PREFS,MODE_PRIVATE).edit()
                .putString(PALETTE_LIBRARY_KEY,encoded.toString()).commit();
        }catch(Exception e){android.util.Log.e("VinylStudio","Saving palette library failed",e);return false;}
    }
    private int[] currentPaletteBases(){
        if(newStyleActive())return newStyleColors.clone();
        if(tessellationMode||layeredCellMode)return tessHueBaseColors.clone();
        if(tonalStencilMode){
            int[] colors=new int[8];int[] displayed=tonalDisplayedColors();
            System.arraycopy(displayed,0,colors,0,Math.min(displayed.length,8));
            int[] fallback=VinylPalettes.get(0,8);
            for(int i=0;i<8;i++)if(colors[i]==0)colors[i]=fallback[i];
            return colors;
        }
        ensureStandardHueBase();
        int[] bases=standardHueBaseColors.clone();
        // A two-to-four-layer project may never have activated the remaining
        // swatches. Save usable vinyl colors in all eight slots, not zeros.
        int[] fallback=VinylPalettes.get(Math.max(0,Math.min(VinylPalettes.NAMES.length-1,
            settings.paletteId)),8);
        for(int i=0;i<8;i++)if(bases[i]==0)bases[i]=fallback[i];
        return bases;
    }
    private void promptSaveNamedPalette(){
        if(tonalStencilMode&&!tonalReadyForPaletteEdit()){
            toast(getString(R.string.ui_finish_rendering_stencil_colors_before_saving));return;
        }
        final EditText name=new EditText(this);name.setSingleLine(true);
        name.setHint(getString(R.string.ui_e_g_forest_portrait_or_pink));
        name.setInputType(android.text.InputType.TYPE_CLASS_TEXT|
            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setFilters(new android.text.InputFilter[]{
            new android.text.InputFilter.LengthFilter(SavedVinylPaletteLibrary.MAX_NAME_LENGTH)});
        final LinearLayout host=new LinearLayout(this);host.setPadding(dp(22),dp(6),dp(22),dp(2));
        host.setOrientation(LinearLayout.VERTICAL);
        host.addView(text(getString(R.string.ui_name_your_current_swatches_and_hue),13,MUTED,false));
        host.addView(name,new LinearLayout.LayoutParams(-1,dp(52)));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Save vinyl palette")
            .setView(host).setNegativeButton("Cancel",null)
            .setPositiveButton("Save",null).create();
        dialog.setOnShowListener(ignore->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            final String label;
            try{label=SavedVinylPaletteLibrary.checkedName(name.getText().toString());}
            catch(IllegalArgumentException invalid){name.setError(invalid.getMessage());return;}
            java.util.ArrayList<SavedVinylPaletteLibrary.Entry> saved=readSavedPalettes();
            SavedVinylPaletteLibrary.Entry existing=SavedVinylPaletteLibrary.findName(saved,label);
            if(existing!=null){
                new AlertDialog.Builder(this).setTitle(getString(R.string.ui_replace_saved_palette))
                    .setMessage("A palette named “"+existing.name+"” already exists. Replace its saved colors and hue?")
                    .setNegativeButton(getString(R.string.ui_keep_both_unchanged),null)
                    .setPositiveButton(getString(R.string.ui_replace),(d,w)->{
                        if(commitNamedPalette(saved,existing.id,label)){dialog.dismiss();}
                    }).show();
                return;
            }
            if(saved.size()>=SavedVinylPaletteLibrary.MAX_ENTRIES){
                name.setError("Library full — delete a saved palette first");return;
            }
            if(commitNamedPalette(saved,java.util.UUID.randomUUID().toString(),label))dialog.dismiss();
        }));
        dialog.show();
    }
    private boolean commitNamedPalette(java.util.List<SavedVinylPaletteLibrary.Entry> prior,String id,String name){
        boolean cmyk=settings.mode==VinylEngine.CMYK &&
            !tonalStencilMode && !newStyleActive() && !tessellationMode && !layeredCellMode;
        int[] accents=cmyk?cmykAccentHueBaseColors.clone():new int[4];
        int black=0;
        if(cmyk){
            int count=settings.layers.size();
            black=standardHueBaseColors[count-1];
            int[] fallback=VinylPalettes.get(0,8);
            for(int i=0;i<4;i++)if(accents[i]==0)accents[i]=fallback[i+3];
        }
        // Save the actual photo-derived colors shown on screen, not unused
        // fallback swatches, when storing a new-style palette in Auto mode.
        if(newStyleActive()&&!newStyleMatchVinyl)newStyleCaptureRenderedColors();
        SavedVinylPaletteLibrary.Entry entry=new SavedVinylPaletteLibrary.Entry(id,name,
            currentPaletteBases(),newStyleActive()||tonalStencilMode?0:currentVinylHue(),cmyk,black,accents);
        java.util.ArrayList<SavedVinylPaletteLibrary.Entry> updated;
        try{updated=SavedVinylPaletteLibrary.saveFirst(prior,entry);}
        catch(IllegalArgumentException error){toast(friendlyFailure(error,"Unable to apply the selected settings"));return false;}
        if(!writeSavedPalettes(updated)){toast(getString(R.string.ui_could_not_save_palette_your_previous));return false;}
        showAllSavedPalettes=false;
        toast("Saved “"+entry.name+"”");refreshShelfPanel();return true;
    }
    private int[] savedNewStyleColors(SavedVinylPaletteLibrary.Entry saved,boolean reversed){
        int count=NewStyleColorSlots.slotCount(newStyleId,newStyleVariant,newStyleColorCount);
        int[] colors=expandNewStyleSwatches(NewStylePalettePresets.saved(saved.baseColors,saved.hueDegrees));
        return PaletteOrientation.reverseActive(colors,count,reversed,
            NewStyleLayerPolicy.isStructuralLayer(newStyleId,0));
    }
    private void applySavedPalette(SavedVinylPaletteLibrary.Entry saved){applySavedPalette(saved,true);}
    private void applySavedPalette(SavedVinylPaletteLibrary.Entry saved,boolean record){
        final String key="saved:"+saved.id;
        final boolean reversed=reversedPalette(key);
        if(newStyleActive()){
            if(!NewStyleColorSlots.multi(newStyleId))return;
            selectedPaletteKey=key;
            applyNewStylePalette(savedNewStyleColors(saved,reversed),saved.name);
            return;
        }
        if(continuousStyle!=null||mazePortraitMode){
            toast(getString(R.string.ui_select_an_editable_multicolor_style_to));return;
        }
        if(tonalStencilMode&&!tonalReadyForPaletteEdit()){
            toast(getString(R.string.ui_finish_rendering_stencil_colors_before_choosing));return;
        }
        if(record)checkpoint();
        selectedPaletteKey=key;
        if(tessellationMode||layeredCellMode){
            int[] oriented=layeredCellMode&&overprintCellKind>0?
                OpaqueOverlayPaletteMapping.saved(Arrays.copyOf(saved.baseColors,8),tessLayerCount,reversed):
                PaletteOrientation.orient(Arrays.copyOf(saved.baseColors,8),reversed);
            setTessBasePalette(oriented);applyTessHue(saved.hueDegrees);
            tessPalette="Saved · "+saved.name;requestAlternate();
        }else if(tonalStencilMode){
            int[] mapped=PaletteOrientation.reverseActive(Arrays.copyOf(saved.baseColors,8),
                tonalLayerCount,reversed,false);
            if(!applyTonalPhysicalColors(java.util.Arrays.copyOf(mapped,tonalLayerCount),saved.hueDegrees))return;
            for(int i=tonalLayerCount;i<8;i++)tonalColorOverrides[i]=PaletteHueShift.rotateOne(mapped[i],-tonalHueDegrees);
            tonalPaletteRanks=SmartPaletteReduction.identity(8);
            requestAlternate();
        }else{
            int[] mapped=Arrays.copyOf(saved.baseColors,8);
            if(settings.mode==VinylEngine.CMYK){
                // Reverse accents but do not move the physical Y and K anchors.
                int count=settings.layers.size();
                int[] accents=saved.cmykSource?saved.cmykAccentBases:saved.baseColors;
                for(int i=0;i<4;i++)
                    cmykAccentHueBaseColors[i]=saved.cmykSource?accents[i]:accents[i+3];
                for(int i=3;i<count-1;i++)mapped[i]=cmykAccentHueBaseColors[i-3];
                mapped[count-1]=saved.cmykSource && saved.cmykBlackBase!=0?
                    saved.cmykBlackBase:saved.baseColors[7];
                // Only the active layers reverse: 558 reversed all 8 slots, which moved the K
                // anchor (index count-1) into a parked slot for 4-7 layer projects.
                if(reversed)mapped=PaletteOrientation.orientCmykActive(mapped,count,true);
            }else if(reversed)mapped=PaletteOrientation.orient(mapped,true);
            if(settings.mode!=VinylEngine.CMYK)setStandardParkedPalette(mapped,saved.hueDegrees);
            System.arraycopy(mapped,0,standardHueBaseColors,0,8);
            applyStandardHue(saved.hueDegrees);requestRender();
        }
        invalidateToolPanels();setToolInternal(2);
        if(record)toast("Applied “"+saved.name+"”");
    }
    private void confirmDeleteSavedPalette(SavedVinylPaletteLibrary.Entry target){
        new AlertDialog.Builder(this).setTitle("Delete saved palette?")
            .setMessage("Delete “"+target.name+"” from this device? This does not change the current artwork or built-in presets.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Delete",(d,w)->{
                // Re-read latest state to avoid deleting an unrelated entry after edits.
                java.util.ArrayList<SavedVinylPaletteLibrary.Entry> saved=readSavedPalettes();
                if(SavedVinylPaletteLibrary.findId(saved,target.id)==null){toast(getString(R.string.ui_palette_already_removed));return;}
                if(!writeSavedPalettes(SavedVinylPaletteLibrary.remove(saved,target.id))){
                    toast(getString(R.string.ui_could_not_delete_palette));return;
                }
                String removedKey="saved:"+target.id;
                paletteReversed.remove(removedKey);
                if(removedKey.equals(selectedPaletteKey))selectedPaletteKey="";
                toast("Deleted “"+target.name+"”");refreshShelfPanel();
            }).show();
    }
    private void savedPaletteLibraryPanel(){
        LinearLayout group=group();
        java.util.ArrayList<SavedVinylPaletteLibrary.Entry> entries=readSavedPalettes();
        section(group,"Saved palettes · "+entries.size()+"/"+SavedVinylPaletteLibrary.MAX_ENTRIES);
        action(group,"Save current palette…",this::promptSaveNamedPalette);
        if(entries.isEmpty()){
            group.addView(text(getString(R.string.ui_name_a_palette_to_reuse_its),12,MUTED,false));
            return;
        }
        int shown=entries.size();
        for(int i=0;i<shown;i++){
            SavedVinylPaletteLibrary.Entry entry=entries.get(i);
            LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(9),dp(9),dp(8),dp(6));
            final String savedKey="saved:"+entry.id;
            final boolean selected=selectedPaletteKey.equals(savedKey);
            item.setBackground(outlined(selected?SELECTED:0xff252E39,
                selected?ACCENT:0xff536275,12));
            item.setContentDescription((selected?"Selected saved palette ":"Tap to apply saved palette ")+entry.name);
            item.setOnClickListener(v->applySavedPalette(entry));
            LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(-1,-2);
            ilp.bottomMargin=dp(7);group.addView(item,ilp);
            item.addView(text(entry.name+"  ·  "+String.format(Locale.US,"%+d°",entry.hueDegrees),14,TEXT,true));
            int[] displayColors=entry.cmykSource?
                PaletteOrientation.orientCmyk(Arrays.copyOf(entry.baseColors,8),reversedPalette(savedKey)):
                PaletteOrientation.orient(Arrays.copyOf(entry.baseColors,8),reversedPalette(savedKey));
            displayColors=PaletteHueShift.rotate(displayColors,entry.hueDegrees);
            if(layeredCellMode&&overprintCellKind>0)
                displayColors=selected?tessColors.clone():
                    OpaqueOverlayPaletteMapping.savedPreview(Arrays.copyOf(entry.baseColors,8),tessLayerCount,
                        reversedPalette(savedKey),entry.hueDegrees);
            if(newStyleActive())displayColors=selected?newStyleColors.clone():
                savedNewStyleColors(entry,reversedPalette(savedKey));
            paletteStrip(item,displayColors,Math.min(displayColors.length,visiblePaletteCount(false)));
            LinearLayout buttons=horizontal(item);
            TextView delete=action(buttons,"Delete",()->confirmDeleteSavedPalette(entry));
            delete.setTextColor(0xffFFB4A8);
            delete.setContentDescription("Delete saved palette "+entry.name);
        }
        group.addView(text(getString(R.string.ui_tap_a_saved_palette_to_apply),12,MUTED,false));
    }
    /** Display the same physical colors that feed Layers and SVG export.
     * The main style owns the renderer, this panel owns no duplicate palette. */
    private void activeVinylSwatchesPanel(){
        if(tessellationMode||layeredCellMode){
            if(stippleActive())return; // Stipple has its own actual eight-chip editor below.
            LinearLayout host=group();
            section(host,"Active vinyl · "+tessLayerCount+" colors");
            int[] colors=java.util.Arrays.copyOf(tessColors,tessLayerCount);
            String[] labels=new String[tessLayerCount];
            for(int i=0;i<labels.length;i++)labels[i]="L"+(i+1);
            colorSwatchGrid(host,colors,labels,null,-1,this::editTessColor);
            if(layeredCellMode&&overprintCellKind>0){
                host.addView(text("Opaque vinyl: L1 is the solid foundation; L2–L"+
                    tessLayerCount+" cover lower sheets wherever they overlap. " +
                    "Swatch edits change the actual vinyl color and recalculate photo matching. " +
                    "The mounting surface never reverses your stack.",12,MUTED,false));
                LayeredCellPortraitEngine.Result finished=layeredResult;
                if(finished!=null&&alternateReadyGeneration==alternateGeneration&&
                   !alternatePreviewDraft&&finished.swatchVisibility!=null){
                    OpaqueOverlaySwatchPlanner.Visibility visibility=finished.swatchVisibility;
                    StringBuilder visible=new StringBuilder("Estimated final visible areas: ");
                    for(int layer=0;layer<visibility.visibleArea.length;layer++){
                        if(layer>0)visible.append(" · ");
                        visible.append('L').append(layer+1).append(' ')
                            .append(visibility.visiblePercent(layer)).append('%');
                    }
                    host.addView(text(visible.toString(),12,MUTED,false));
                }
            }
            return;
        }
        if(tonalStencilMode){
            LinearLayout host=group();section(host,"Active stencil colors · "+tonalLayerCount);
            if(tonalResult==null||tonalResult.layers.size()!=tonalLayerCount){
                host.addView(text(getString(R.string.ui_preparing_photo_derived_stencil_colors),12,MUTED,false));return;
            }
            int[] colors=tonalDisplayedColors();String[] labels=new String[tonalLayerCount];
            for(int i=0;i<labels.length;i++)labels[i]="L"+(i+1);
            colorSwatchGrid(host,colors,labels,null,-1,this::editTonalColor);
            return;
        }
        if(settings.layers.isEmpty())return;
        LinearLayout host=group();section(host,"Active vinyl sheets · "+settings.layers.size());
        int n=settings.layers.size();int[] colors=new int[n];String[] labels=new String[n];
        for(int i=0;i<n;i++){
            VinylEngine.Layer layer=settings.layers.get(i);
            colors[i]=layer.color;
            String channel=layer.cmykChannel>=0?" · "+layer.name:"";
            labels[i]="L"+(i+1)+channel;
        }
        colorSwatchGrid(host,colors,labels,null,chosen,this::editColor);
        host.addView(text("Tap a chip to edit the exported vinyl color. Layer order is controlled only in Layers; " +
            "mounting-surface changes never reorder existing sheets.",12,MUTED,false));
    }
    private void colorPaletteOnlyPanel(){
        ensurePreparedPhotoPalette();
        // Pure single-line/maze styles have no editable palette to rotate.
        // All other styles show hue at the top, including Stipple.
        if(continuousStyle==null&&!mazePortraitMode)vinylPaletteHueControl(group());
        LinearLayout b=group();section(b,"Mounting surface");
        if(settings.snipFillMode!=SnipBackground.EMPTY&&settings.snips!=null&&!settings.snips.isEmpty()){
            b.addView(text("Legacy project: snipped regions use a " +
                (settings.snipFillMode==SnipBackground.WHITE?"white":"black")+
                " photo fill, not transparency. This changes generated cuts and is separate from mounting-surface color.",12,0xffFFD093,false));
            action(b,"Convert snipped areas to transparent",()->{
                checkpoint();settings.snipFillMode=SnipBackground.EMPTY;
                setToolInternal(2);requestRender();
            });
        }
        mountingSurfaceChoices(b);
        if(continuousStyle==null&&!mazePortraitMode){
            if(!stippleActive())activeVinylSwatchesPanel();
        }
        if(continuousStyle!=null||mazePortraitMode){
            b.addView(text(continuousStyle==ContinuousLineModes.LineStyle.SPIRAL?
                "Black vinyl portrait. Transparent photo areas stay uncut; some ribbons may be separate pieces.":
                "Single drawing path · black on white. A neutral black line has no hue to rotate.",13,MUTED,false));return;
        }
        if(tonalStencilMode){
            LinearLayout source=group();section(source,"Source-derived colors");
            action(source,"Use original photo colors",()->applyTonalPalette(-1));
        }
        if(tessellationMode||layeredCellMode){
            if(stippleActive()){
                // v2.50.9: RC9 accidentally returned before showing editable swatches.
                // Always display the actual, serialized tessColors used by the engine.
                LinearLayout derived=group();section(derived,"Vinyl swatches · "+tessLayerCount+" active / 8 available");
                derived.addView(text("Source: "+tessPalette+". Tap a swatch to edit its actual vinyl color. " +
                    "Manual edits are kept until you choose Regenerate.",12,MUTED,false));
                for(int first=0;first<8;first+=4){
                    LinearLayout row=horizontal(derived);
                    for(int i=first;i<Math.min(first+4,8);i++){
                        final int layer=i,swatch=tessColors[i];
                        final boolean active=i<tessLayerCount;
                        LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);
                        tile.setPadding(dp(3),dp(2),dp(3),dp(3));
                        tile.setBackground(outlined(SURFACE,active?ACCENT:0xff536075,10));
                        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(73),1);
                        lp.rightMargin=dp(3);lp.bottomMargin=dp(4);row.addView(tile,lp);
                        TextView face=text("L"+(i+1),13,contrastText(swatch),true);
                        face.setGravity(Gravity.CENTER);
                        face.setBackground(outlined(swatch,0xff78889d,10));
                        tile.addView(face,new LinearLayout.LayoutParams(-1,dp(49)));
                        TextView edit=text(active?colorHex(swatch):"INACTIVE",10,active?MUTED:0xffFFB08C,false);
                        edit.setGravity(Gravity.CENTER);
                        tile.addView(edit,new LinearLayout.LayoutParams(-1,dp(20)));
                        tile.setContentDescription("Layer "+(i+1)+" color "+colorHex(swatch)+
                            (active?". Tap to edit":". Tap to activate"));
                        tile.setOnClickListener(v->{
                            if(layer>=tessLayerCount){
                                checkpoint();invalidateStipplePhotoPaletteRequest();
                                tessLayerCount=layer+1;stippleOrderNormalize();
                                tessSelectedLayer=-1;invalidateToolPanels();setToolInternal(2);requestAlternate();
                            }else editTessColor(layer);
                        });
                    }
                }
                action(derived,"Use all 8 colors",()->{
                    if(tessLayerCount==8)return;
                    checkpoint();invalidateStipplePhotoPaletteRequest();
                    reduceTessPalette(8);tessLayerCount=8;stippleOrderNormalize();
                    tessSelectedLayer=-1;invalidateToolPanels();setToolInternal(2);requestAlternate();
                });
                action(derived,stippleShowCutOutlines?"Hide cut outlines on artwork":"Show cut outlines on artwork",()->{
                    stippleShowCutOutlines=!stippleShowCutOutlines;
                    invalidateToolPanels();setToolInternal(2);artwork.invalidate();
                });
                action(derived,"Inspect all SVG cut paths",()->showStippleCutPaths(-1));
                derived.addView(text("The main canvas draws the actual preview cut polygons as vectors. " +
                    "Pinch to 12× and pan to inspect. Export regenerates higher-detail millimeter SVGs.",12,MUTED,false));
                // Palette library follows these editable eight vinyl swatches.
            }
        }
        inlinePaletteGallery(group());
        savedPaletteLibraryPanel();
    }
    /** Palette card order matches the stack created when the preset is selected. */
    /** View the current cut contours directly as vector Paths without rasterizing to a Bitmap.
     * The current result is the draft; export regenerates the final vectors independently.
     */
    /** Immutable vector preview. Built on the specialty worker, never inside onDraw.
     * Pixel-space vertices are the SAME tessellation polygons used by SVG export.
     * Paint colors are taken from the matching immutable engine result.
     */
    private static final class StippleVectorCache {
        final TessellationEngine.Result source;
        final Path[] layers;
        final int[] colors;
        private StippleVectorCache(TessellationEngine.Result result,Path[] layers,int[] colors){
            source=result;this.layers=layers;this.colors=colors;
        }
        StippleVectorCache rebind(TessellationEngine.Result result){
            return new StippleVectorCache(result,layers,colors);
        }
        StippleVectorCache(TessellationEngine.Result result){
            source=result;
            int count=result.options.layerCount;layers=new Path[count];
            colors=result.options.colors.clone();
            for(int i=0;i<count;i++){
                layers[i]=new Path();layers[i].setFillType(Path.FillType.EVEN_ODD);
            }
            for(TessellationEngine.Tile tile:result.tiles){
                if(tile.layer<0||tile.layer>=count)continue;
                Path dest=layers[tile.layer];
                addCutRing(dest,tile.xy);
                for(float[] hole:tile.holes)addCutRing(dest,hole);
            }
        }
    }

    private void showStippleCutPaths(int requestedLayer){
        if(!stippleActive())return;
        final TessellationEngine.Result current=tessellationResult;
        if(current==null||current.tiles.isEmpty()){
            toast(getString(R.string.ui_render_the_stipple_first_to_inspect));return;
        }
        final int visibleLayer=requestedLayer>=0&&requestedLayer<current.options.layerCount?requestedLayer:-1;
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12),dp(8),dp(12),dp(8));
        int count=0;for(TessellationEngine.Tile tile:current.tiles)if(visibleLayer<0||tile.layer==visibleLayer)count++;
        body.addView(text(count+" closed vector cut shapes"+
            (visibleLayer<0?" · all active swatches":" · layer "+(visibleLayer+1))+
            ". This is current preview geometry; export recalculates full-detail SVGs.",12,MUTED,false));
        View vectorView=new View(this){
            private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint contour=new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path tilePath=new Path(); // one path rewound per tile; previews can hold thousands
            @Override protected void onDraw(Canvas canvas){
                super.onDraw(canvas);
                int vw=getWidth(),vh=getHeight();if(vw<=0||vh<=0)return;
                canvas.drawColor(settings.paper); // mounting surface is display-only
                float scale=Math.min((vw-dp(20))/(float)current.width,(vh-dp(20))/(float)current.height);
                if(scale<=0)return;
                canvas.save();canvas.translate((vw-current.width*scale)/2f,(vh-current.height*scale)/2f);
                canvas.scale(scale,scale);
                fill.setStyle(Paint.Style.FILL);
                contour.setStyle(Paint.Style.STROKE);contour.setColor(0xff8dbbdb);
                contour.setStrokeWidth(Math.max(.22f,.6f/scale));
                for(int z=0;z<current.options.layerCount;z++){
                    int layer=current.stipple==null?z:current.stipple.options.order[z];
                    if(visibleLayer>=0&&layer!=visibleLayer)continue;
                    fill.setColor(current.options.colors[layer]);
                    for(TessellationEngine.Tile tile:current.tiles){
                        if(tile.layer!=layer)continue;
                        Path path=tilePath;path.rewind();path.setFillType(Path.FillType.EVEN_ODD);
                        addCutRing(path,tile.xy);
                        for(float[] hole:tile.holes)addCutRing(path,hole);
                        canvas.drawPath(path,fill);
                        canvas.drawPath(path,contour);
                    }
                }
                canvas.restore();
            }
        };
        body.addView(vectorView,new LinearLayout.LayoutParams(-1,dp(370)));
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_svg_cut_path_inspector))
            .setView(body).setPositiveButton("Close",null).show();
    }
    private static void addCutRing(Path path,float[] xy){
        if(xy==null||xy.length<6)return;
        path.moveTo(xy[0],xy[1]);
        for(int i=2;i<xy.length;i+=2)path.lineTo(xy[i],xy[i+1]);
        path.close();
    }

    private int[] presetTessSwatches(int paletteId){
        int[] input=ColorWorkspacePalette.tessPalette(paletteId);
        if(layeredCellMode&&overprintCellKind>0)return java.util.Arrays.copyOf(
            OpaqueOverlayPhotoPalette.arrange(input,tessLayerCount,preparedPhotoColorPixels),tessLayerCount);
        return ColorWorkspacePalette.selectForSurface(input,tessLayerCount,settings.paper);
    }
    private void specialtyPaletteTile(LinearLayout row,String name,int[] swatches,int id,boolean tonal){
        LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setPadding(dp(6),dp(6),dp(6),dp(5));
        tile.setBackground(outlined(StudioTheme.OUTLINE,0xff4b5867,13));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(82),1);lp.rightMargin=dp(5);lp.bottomMargin=dp(6);row.addView(tile,lp);
        LinearLayout bar=horizontal(tile);
        for(int c:swatches){View v=new View(this);v.setBackground(shape(c,3));LinearLayout.LayoutParams sw=new LinearLayout.LayoutParams(0,dp(31),1);
            sw.rightMargin=dp(1);bar.addView(v,sw);}
        TextView title=text(name,13,TEXT,true);title.setGravity(Gravity.CENTER);title.setMaxLines(2);
        tile.addView(title,new LinearLayout.LayoutParams(-1,dp(32)));
        tile.setContentDescription(name+" · "+swatches.length+" vinyl colors");
        tile.setOnClickListener(v->{if(tonal)applyTonalPalette(id);else applyTessPalette(id);});
    }
    private int contrastText(int c){return (((c>>16)&255)*.299+((c>>8)&255)*.587+(c&255)*.114)>150?0xff11151A:Color.WHITE;}
    private void modePill(LinearLayout row,String name,int mode){TextView b=action(row,name,()->{
        if(mode==VinylEngine.CMYK){setCmyk();return;}
        checkpoint();designType=0;settings.mode=mode;if(mode==VinylEngine.SPOT)for(VinylEngine.Layer layer:settings.layers)layer.cmykChannel=-1;rebaseStandardHue();rebuildLayers();setTool(2);requestRender();
    });b.setBackground(shape(settings.mode==mode?SELECTED:StudioTheme.SURFACE_HIGH,20));}
    private void applyPalette(int[] pal){checkpoint();for(int i=0;i<settings.layers.size();i++)settings.layers.get(i).color=pal[i%pal.length];rebaseStandardHue();rebuildLayers();setTool(2);requestRender();}

    /** On layer edits preserve the actual colors and the previously selected hue angle. */
    private void rebaseStandardHueAfterCountEdit(int hue){
        rebaseStandardHue();standardHueDegrees=hue;
        for(int i=0;i<settings.layers.size()&&i<8;i++)
            standardHueBaseColors[i]=PaletteHueShift.rotateOne(settings.layers.get(i).color,-hue);
        if(settings.mode==VinylEngine.CMYK)for(int i=3;i<settings.layers.size()-1;i++)
            cmykAccentHueBaseColors[i-3]=standardHueBaseColors[i];
        else for(int i=0;i<parkedVinylLayers.size();i++){
            int slot=settings.layers.size()+parkedVinylLayers.size()-i-1;
            if(slot<8)standardHueBaseColors[slot]=PaletteHueShift.rotateOne(parkedVinylLayers.get(i).color,-hue);
        }
    }
    private void addLayer(){if(hasSolidBase(settings)){toast("Remove the solid base before changing the layer count");return;}if(settings.layers.size()>=8){toast(getString(R.string.ui_eight_layers_is_the_maximum));return;}
        final int oldHue=standardHueDegrees;
        if(settings.mode==VinylEngine.CMYK){
            if(!VinylEngine.CmykExtendedStack.hasAnchors(settings.layers)){toast(getString(R.string.ui_reapply_the_cmyk_preset_first));return;}
            checkpoint();chosen=VinylEngine.CmykExtendedStack.addSpot(settings.layers,settings.masterSpacingMm,settings.masterDensity,settings.masterContrast);
        }else{
            countLayers(settings.layers.size()+1);return;
        }
        if(settings.anglePreset==VinylEngine.ANGLES_PARALLEL)settings.anglePreset=VinylEngine.ANGLES_CLASSIC;
        if(settings.anglePreset!=VinylEngine.ANGLES_CUSTOM)VinylEngine.applyAnglePreset(settings);
        rebaseStandardHueAfterCountEdit(oldHue);setTool(3);rebuildLayers();requestRender();}
    /** One confirmed deletion removes the intended physical color, not the
     * entire artwork. Structural lead/text sheets stay anchored. */
    private void confirmRemoveNewStyleLayer(int index){
        if(!newStyleActive()||!NewStyleColorSlots.multi(newStyleId))return;
        final int minimum=NewStyleColorSlots.minimumSelectable(newStyleId);
        if(newStyleColorCount<=minimum||index<0||index>=newStyleColorCount||
           NewStyleLayerPolicy.isStructuralLayer(newStyleId,index)){
            toast(getString(R.string.ui_this_structural_sheet_cannot_be_deleted));return;
        }
        new AlertDialog.Builder(this).setTitle("Delete vinyl layer L"+(index+1)+"?")
            .setMessage(getString(R.string.ui_remove_this_color_from_the_artwork))
            .setNegativeButton("Cancel",null).setPositiveButton("Delete",(dialog,which)->{
                checkpoint();
                VinylLayerDelete.remove(newStyleColors,index,newStyleColorCount);
                VinylLayerDelete.remove(newStyleHueBaseColors,index,newStyleColorCount);
                newStylePaletteRanks=SmartPaletteReduction.identity(9);
                newStyleColorCount--;newStyleSelectedLayer=-1;
                newStyleMatchVinyl=true;newStylePhotoAutoMemory[newStyleId]=false;
                selectedPaletteKey="";
                rememberNewStylePalette();invalidateToolPanels();setToolInternal(3);requestAlternate();
            }).show();
    }
    private void confirmRemoveUnifiedLayer(int index){
        final boolean cells=layeredCellMode,tiles=tessellationMode,tonal=tonalStencilMode;
        final int count=(cells||tiles)?tessLayerCount:tonal?tonalLayerCount:settings.layers.size();
        if(index<0||index>=count||count<=2){toast(getString(R.string.ui_keep_at_least_two_vinyl_colors));return;}
        final boolean standard=!cells&&!tiles&&!tonal;
        if(standard&&settings.layers.get(index).silhouetteBase){
            toast(getString(R.string.ui_remove_the_solid_snip_base_with));return;
        }
        final boolean convertCmyk=standard&&settings.mode==VinylEngine.CMYK&&
            settings.layers.get(index).cmykChannel>=0;
        String detail="Delete L"+(index+(underlayRequested()?2:1))+" and its exported cuts? " +
            "Remaining vinyl colors keep their order. Undo can restore the layer.";
        if(convertCmyk)detail+=" This will convert the anchored CMYK screen into editable spot-color layers.";
        if(tonal)detail+=" Tonal masks will be regenerated for the remaining colors.";
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_delete_vinyl_layer))
            .setMessage(detail).setNegativeButton("Cancel",null)
            .setPositiveButton("Delete",(dialog,which)->{
                if(cells||tiles){
                    checkpoint();
                    VinylLayerDelete.remove(tessColors,index,tessLayerCount);
                    VinylLayerDelete.remove(tessHueBaseColors,index,tessLayerCount);
                    tessPaletteRanks=SmartPaletteReduction.identity(8);
                    VinylLayerDelete.remove(layeredLayerFamilies,index,tessLayerCount);
                    if(layeredBackground==index)layeredBackground=-1;
                    else if(layeredBackground>index)layeredBackground--;
                    if(stippleActive()){
                        invalidateStipplePhotoPaletteRequest();
                        int[] newOrder=VinylLayerDelete.removeOrder(stippleSettings.order,index,tessLayerCount);
                        VinylLayerDelete.remove(stippleSettings.colors,index,tessLayerCount);
                        VinylLayerDelete.remove(stippleSettings.density,index,tessLayerCount);
                        VinylLayerDelete.remove(stippleSettings.minDotMm,index,tessLayerCount);
                        VinylLayerDelete.remove(stippleSettings.enabled,index,tessLayerCount);
                        stippleSettings.order=newOrder;
                    }
                    tessLayerCount--;tessSelectedLayer=-1;
                    tessPalette="Custom";selectedPaletteKey="";
                    requestAlternate();
                }else if(tonal){
                    checkpoint();
                    int sourceIndex=tonalSourceIndex(index);
                    VinylLayerDelete.remove(tonalColorOverrides,sourceIndex,tonalLayerCount);
                    tonalPaletteRanks=SmartPaletteReduction.identity(8);
                    if(tonalBaseSource==sourceIndex)tonalBaseSource=-1;
                    else if(tonalBaseSource>sourceIndex)tonalBaseSource--;
                    tonalLayerCount--;tonalSelectedLayer=-1;
                    selectedPaletteKey="";requestAlternate();
                }else{
                    checkpoint();
                    int oldHue=standardHueDegrees;
                    if(convertCmyk){
                        settings.mode=VinylEngine.SPOT;settings.paletteId=-1;
                        for(VinylEngine.Layer layer:settings.layers)layer.cmykChannel=-1;
                    }
                    settings.layers.remove(index);
                    chosen=-1;selectedPaletteKey="";
                    rebaseStandardHueAfterCountEdit(oldHue);
                    rebuildLayers();requestRender();
                }
                invalidateToolPanels();setToolInternal(3);
            }).show();
    }
    private void removeLayer(){if(hasSolidBase(settings)){toast(getString(R.string.ui_use_remove_solid_snip_background_to));return;}if(settings.layers.size()<=2){toast(getString(R.string.ui_keep_at_least_two_layers));return;}
        final int oldHue=standardHueDegrees;
        if(settings.mode==VinylEngine.CMYK){
            int index=Math.max(0,Math.min(chosen,settings.layers.size()-1));
            if(settings.layers.size()<=4||settings.layers.get(index).cmykChannel!=-1){toast(getString(R.string.ui_yellow_cyan_magenta_and_black_are));return;}
            checkpoint();VinylEngine.CmykExtendedStack.removeSpot(settings.layers,index);
        }else{checkpoint();settings.layers.remove(Math.max(0,Math.min(chosen,settings.layers.size()-1)));}
        chosen=Math.min(chosen,settings.layers.size()-1);
        if(settings.anglePreset==VinylEngine.ANGLES_PARALLEL)settings.anglePreset=VinylEngine.ANGLES_CLASSIC;
        if(settings.anglePreset!=VinylEngine.ANGLES_CUSTOM)VinylEngine.applyAnglePreset(settings);
        rebaseStandardHueAfterCountEdit(oldHue);setTool(3);rebuildLayers();requestRender();}
    private void moveLayer(int i,int dir){if(settings.mode==VinylEngine.CMYK){toast(getString(R.string.ui_cmyk_is_anchored_y_c_m));return;}int target=i+dir;if(target<0||target>=settings.layers.size())return;checkpoint();settings.paletteId=-1;Collections.swap(settings.layers,i,target);rebaseStandardHue();chosen=target;setTool(3);rebuildLayers();requestRender();}
    private void editPaper(){
        // Shared HSV wheel, brightness and exact black/white presets, but do not
        // mutate the actual mounting surface until Apply is pressed.
        VinylColorEditor editor=new VinylColorEditor(this,settings.paper);
        new AlertDialog.Builder(this).setTitle("Mounting surface · color")
            .setMessage(getString(R.string.ui_preview_background_only_never_exported_as))
            .setView(scrollColorEditor(editor))
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Apply",(dialog,which)->{
                try{selectMountingSurface(editor.getColorOrThrow());}
                catch(IllegalArgumentException invalid){toast(getString(R.string.ui_invalid_mounting_color));}
            }).show();
    }
    private final LiveColorPreview liveColorPreview=new LiveColorPreview();
    private void editColor(int i){
        final int ix=Math.max(0,Math.min(i,settings.layers.size()-1));
        final VinylEngine.Layer current=settings.layers.get(ix);
        final int initialColor=current.color;
        final String originalName=current.name;
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12),dp(0),dp(12),dp(0));
        VinylColorEditor editor=new VinylColorEditor(this,initialColor);
        body.addView(editor);
        // D15: the live preview is drawn from liveColorPreview; the real layer is only
        // changed by Apply, so autosave/process death never persist an uncommitted color.
        liveColorPreview.begin(ix,initialColor);
        editor.setListener(color->{liveColorPreview.update(color);if(artwork!=null)artwork.invalidate();});
        EditText label=new EditText(this);label.setSingleLine(true);
        label.setText(originalName);label.setHint(getString(R.string.ui_vinyl_layer_name));
        body.addView(label);
        new AlertDialog.Builder(this).setTitle("Vinyl layer "+(ix+1)+" · color")
            .setView(scrollColorEditor(body)).setNegativeButton("Cancel",null)
            .setPositiveButton("Apply",(dialog,which)->{
                liveColorPreview.end();
                try{
                    final int color=editor.getColorOrThrow();
                    checkpoint();preserveManualStandardColor(ix,color);
                    current.name=label.getText().toString().trim().isEmpty()?"Vinyl "+(ix+1):label.getText().toString().trim();
                    settings.paletteId=-1;
                    selectedPaletteKey="";
                    rebuildLayers();invalidateToolPanels();setToolInternal(activeWorkspace==3?2:3);requestRender();
                }catch(IllegalArgumentException ex){
                    toast(getString(R.string.ui_could_not_apply_the_selected_vinyl));if(artwork!=null)artwork.invalidate();}
            }).setOnDismissListener(dialog->{liveColorPreview.end();if(artwork!=null)artwork.invalidate();}).show();
    }
    private void curvesPanel(){LinearLayout a=group();section(a,"Tone curve · "+layer().name);TextView info=text(getString(R.string.ui_drag_the_five_handles_to_change),12,MUTED,false);a.addView(info);CurveView cv=new CurveView();a.addView(cv,new LinearLayout.LayoutParams(-1,dp(170)));LinearLayout row=horizontal(a);action(row,"Reset curve",()->{checkpoint();layer().curve=new float[]{0,.25f,.5f,.75f,1};cv.invalidate();requestRender();});action(row,"Copy to all",()->{checkpoint();float[] p=layer().curve.clone();for(VinylEngine.Layer l:settings.layers)l.curve=p.clone();requestRender();});
    }
    private void marksPanel(){LinearLayout a=group();section(a,"Four shared registration marks");TextView info=text(getString(R.string.ui_tap_a_mark_and_drag_it),12,MUTED,false);a.addView(info);LinearLayout row=horizontal(a);for(int i=0;i<4;i++){final int idx=i;TextView choice=action(row,"Mark "+(i+1),()->{markIndex=idx;setTool(5);artwork.invalidate();});choice.setBackground(shape(markIndex==i?SELECTED:StudioTheme.SURFACE_HIGH,18));}
        LinearLayout ctrl=group();section(ctrl,"Registration shape · same on every layer");
        HorizontalScrollView markScroll=new HorizontalScrollView(this);markScroll.setHorizontalScrollBarEnabled(false);ctrl.addView(markScroll);
        LinearLayout styles=new LinearLayout(this);styles.setGravity(Gravity.CENTER_VERTICAL);markScroll.addView(styles);
        String[] shapeNames={"Cross","Circle","L corner","Square","Star","Diamond","Triangle"};
        TextView[] stylePills=new TextView[shapeNames.length];
        for(int n=0;n<shapeNames.length;n++){final int style=n;TextView pill=action(styles,shapeNames[n],()->{checkpoint();settings.markStyle=style;
            for(int k=0;k<stylePills.length;k++)if(stylePills[k]!=null)stylePills[k].setBackground(shape(k==style?SELECTED:StudioTheme.SURFACE_HIGH,18));
            requestRegistrationRender();});stylePills[n]=pill;pill.setBackground(shape(settings.markStyle==n?SELECTED:StudioTheme.SURFACE_HIGH,18));}
        slider(ctrl,"Mark diameter (mm)",settings.markSizeMm,2f,12f,.5f,v->{settings.markSizeMm=v;requestRegistrationRender();});
        ctrl.addView(text(getString(R.string.ui_size_changes_the_real_cut_shape),12,MUTED,false));
        inchSlider(ctrl,"Registration clearance",settings.registrationClearanceMm,.02f,.20f,.01f,v->{settings.registrationClearanceMm=v;requestRegistrationRender();});
        action(ctrl,"Reset all four marks",()->{checkpoint();settings.marks=null;markIndex=0;requestRegistrationRender();});
        // M5: compact layout lets 11 in landscape artwork fit a 12 x 12 mat (291.4 mm sheet).
        boolean compact=settings.markLayout==RegistrationMarks.PLACEMENT_ABOVE_BELOW;
        action(ctrl,compact?"Mark layout: above and below ✓ (use corners)":"Mark layout: corners ✓ (use above and below)",()->{
            checkpoint();
            settings.markLayout=compact?RegistrationMarks.PLACEMENT_CORNERS:RegistrationMarks.PLACEMENT_ABOVE_BELOW;
            settings.marks=null;materializedCompactMarks=null;markIndex=0;
            requestRegistrationRender();refreshShelfPanel();
        });
        ctrl.addView(text("Above and below places two marks over and two under the artwork, so no side margin is needed. Changing layout replaces all four marks.",12,MUTED,false));
        if(photo!=null){float displayedW=artwork==null?settings.widthMm:artwork.gestureWidth();
            float displayedH=artwork==null?settings.widthMm*imageH/(float)Math.max(1,imageW):artwork.gestureHeight();
            float[][] m=settings.marks==null?RegistrationMarks.automaticPositions(displayedW,displayedH,
                settings.markSizeMm,settings.registrationClearanceMm,settings.circleCrop):settings.marks;TextView pos=text(String.format(Locale.US,"Mark %d: X %.3f in · Y %.3f in",markIndex+1,VinylEngine.toInches(m[markIndex][0]),VinylEngine.toInches(m[markIndex][1])),13,ACCENT,true);ctrl.addView(pos);}
    }
    /** Physical finished-artwork size, excluding registration-mark padding. */
    private String finishedSizeLabel(){
        if(imageW<=0||imageH<=0)return "Import and crop an image to measure its height";
        double widthIn=VinylEngine.toInches(settings.widthMm);
        double heightIn=widthIn*imageH/(double)imageW;
        return String.format(Locale.US,"Width %.3f in  ×  Height %.3f in",widthIn,heightIn);
    }
    /** The selected Cricut mat. A legacy project (saved before the mat was recorded) adopts the
     *  smallest mat its CURRENT marked sheet fits, so exports that worked keep working; the
     *  choice is then stored in the project. Never changes the artwork size. */
    private String selectedCricutMat(){
        if(settings.cricutMat==null){
            String inferred=CricutMatPreflight.MAT_12X12;
            if(imageW>0&&imageH>0)try{
                CricutSizeAdvisor.Estimate e=sizeEstimate(VinylEngine.toInches(settings.widthMm),true);
                if(!e.fits12x12&&e.fits12x24)inferred=CricutMatPreflight.MAT_12X24;
            }catch(IllegalArgumentException ignored){}
            settings.cricutMat=inferred;
        }
        return settings.cricutMat;
    }
    private void selectCricutMat(String mat){
        String chosen=CricutMatPreflight.normalizeMat(mat);
        if(chosen==null||chosen.equals(selectedCricutMat()))return;
        checkpoint();settings.cricutMat=chosen;
        // Prepared ZIPs carry the previous mat's preflight and note.
        discardPreparedStandardCutZip();discardPreparedSpecialtyCutZip();
        projectReviewProgress.invalidate();
    }
    /** Early, certain refusal before an expensive final render: uses the SMALLER of the
     *  exporters' margins, so it only refuses sheets that cannot fit the selected mat on any
     *  export path. The writer's check on the actual marked sheet stays authoritative. */
    private boolean matPreflightRefused(){
        if(outputDeviceId!=OutputDeviceProfile.CRICUT||imageW<=0||imageH<=0)return false;
        final String mat=selectedCricutMat();
        final CricutSizeAdvisor.Estimate low;
        try{low=CricutSizeAdvisor.estimate(VinylEngine.toInches(settings.widthMm),imageW,imageH,settings.registration,
            settings.markSizeMm,settings.registrationClearanceMm,Math.min(SheetFrame.SPECIALTY_PADDING_MM,settings.outerSpacingMm),
            settings.circleCrop,settings.marks);}
        catch(IllegalArgumentException invalid){return false;} // the exporter reports the layout error
        if(CricutMatPreflight.fitsMat(low.sheetWidthMm,low.sheetHeightMm,mat))return false;
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(getString(R.string.ui_cricut_mat_size_warning))
            .setMessage(String.format(Locale.US,"With registration marks each cut sheet is at least %.2f × %.2f in. "+
                "That does not fit the selected %s mat (cutting area about 11.5 × 11.5 in). "+
                "Nothing was exported and the artwork was not resized.",
                low.sheetWidthMm/25.4,low.sheetHeightMm/25.4,CricutMatPreflight.matLabel(mat)))
            .setNegativeButton("Cancel",null)
            .setNeutralButton(getString(R.string.ui_change_size),(d,w)->showFinishedSizeDialog());
        if(CricutMatPreflight.MAT_12X12.equals(mat)&&low.fits12x24)
            b.setPositiveButton("Use 12 × 24 mat",(d,w)->{selectCricutMat(CricutMatPreflight.MAT_12X24);toast("Mat set to 12 × 24 in. Export again.");});
        b.show();
        return true;
    }
    /** Sheet envelope for the artwork at this width: the same registration frame as the exporters.
     *  Custom marks only apply at the current width (a size change resets them). */
    private CricutSizeAdvisor.Estimate sizeEstimate(double widthIn,boolean currentWidth){
        return CricutSizeAdvisor.estimate(widthIn,imageW,imageH,settings.registration,settings.markSizeMm,
            settings.registrationClearanceMm,settings.outerSpacingMm,settings.circleCrop,
            currentWidth?settings.marks:CricutSizeAdvisor.layoutMarks(settings.markLayout,widthIn,imageW,imageH,
                settings.markSizeMm,settings.registrationClearanceMm));
    }
    /** Size feedback against the selected mat. Each exporter still performs the authoritative
     *  fail-closed check on the actual marked sheet. Other cutters are not limited here. */
    private String cricutSizeFeedback(double widthIn,String mat){
        try{
            boolean current=Math.abs(VinylEngine.toMm((float)widthIn)-settings.widthMm)<=.005f;
            CricutSizeAdvisor.Estimate s=sizeEstimate(widthIn,current);
            String fit=CricutMatPreflight.fitsMat(s.sheetWidthMm,s.sheetHeightMm,mat)?
                "fits the selected "+CricutMatPreflight.matLabel(mat)+" mat":
                "does NOT fit the selected "+CricutMatPreflight.matLabel(mat)+" mat";
            double safe=CricutSizeAdvisor.maximumWidthIn(CricutMatPreflight.MAT_12X24.equals(mat),imageW,imageH,
                settings.registration,settings.markSizeMm,settings.registrationClearanceMm,
                settings.outerSpacingMm,settings.circleCrop,settings.markLayout);
            return String.format(Locale.US,"Cut sheet with marks %.3f × %.3f in · %s · largest width for this mat ≈ %.3f in",
                s.sheetWidthMm/25.4,s.sheetHeightMm/25.4,fit,safe);
        }catch(IllegalArgumentException err){return "Registration layout cannot fit at this size";}
    }
    /** Exact numeric width input; height is derived rather than editable to prevent stretching. */
    private void showFinishedSizeDialog(){
        if(photo==null||imageW<=0||imageH<=0){toast(getString(R.string.import_image_first));return;}
        final EditText entry=new EditText(this);
        entry.setSingleLine(true);
        entry.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        entry.setText(String.format(Locale.US,"%.3f",VinylEngine.toInches(settings.widthMm)));
        entry.selectAll();entry.setHint(getString(R.string.ui_width_in_inches));
        final LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24),dp(8),dp(24),0);
        box.addView(text(getString(R.string.ui_finished_artwork_width_inches),14,TEXT,true));
        box.addView(entry);
        TextView height=text("Height is calculated automatically from the cropped image ratio.",12,MUTED,false);
        box.addView(height);
        final TextView readout=text(finishedSizeLabel(),14,ACCENT,true);box.addView(readout);
        final String[] mat={selectedCricutMat()};
        final boolean cricut=outputDeviceId==OutputDeviceProfile.CRICUT;
        final TextView cutSheet=text(cricut?cricutSizeFeedback(VinylEngine.toInches(settings.widthMm),mat[0]):"",12,MUTED,false);
        if(cricut){
            box.addView(text("Cricut mat",13,TEXT,true));
            LinearLayout mats=horizontal(box);
            final TextView[] pills=new TextView[2];
            final String[] ids={CricutMatPreflight.MAT_12X12,CricutMatPreflight.MAT_12X24};
            for(int m=0;m<2;m++){final int idx=m;
                pills[m]=action(mats,CricutMatPreflight.matLabel(ids[m]),()->{
                    mat[0]=ids[idx];
                    for(int k=0;k<2;k++)pills[k].setBackground(shape(ids[k].equals(mat[0])?SELECTED:StudioTheme.SURFACE_HIGH,18));
                    try{double w=Double.parseDouble(entry.getText().toString().trim());
                        cutSheet.setText(w>0&&Double.isFinite(w)?cricutSizeFeedback(w,mat[0]):"");}
                    catch(Exception err){cutSheet.setText("");}
                });
                pills[m].setBackground(shape(ids[m].equals(mat[0])?SELECTED:StudioTheme.SURFACE_HIGH,18));
            }
        }
        box.addView(cutSheet);
        entry.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence v,int start,int count,int after){}
            public void onTextChanged(CharSequence v,int start,int before,int count){
                try{double w=Double.parseDouble(v.toString());
                    readout.setText(w>0&&Double.isFinite(w)?String.format(Locale.US,"Width %.3f in  ×  Height %.3f in",w,w*imageH/(double)imageW):"Enter a positive width");
                    cutSheet.setText(cricut&&w>0&&Double.isFinite(w)?cricutSizeFeedback(w,mat[0]):"");
                }catch(Exception err){readout.setText(R.string.quality_enter_a_width_in_inches);cutSheet.setText("");}
            }
            public void afterTextChanged(android.text.Editable e){}
        });
        final AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(getString(R.string.ui_finished_artwork_size))
            .setView(box)
            .setNegativeButton("Cancel",null)
            .setPositiveButton(getString(R.string.ui_apply_size),null).create();
        dialog.setOnShowListener(unused->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            final double w;
            try{w=Double.parseDouble(entry.getText().toString().trim());}
            catch(Exception err){entry.setError("Enter a valid width");return;}
            if(!Double.isFinite(w)||w<0.5||w>24){entry.setError("Width must be 0.5–24 inches");return;}
            final Runnable accept=()->{
                if(cricut)selectCricutMat(mat[0]);
                float mm=VinylEngine.toMm((float)w);
                if(Math.abs(settings.widthMm-mm)>0.005f){
                    checkpoint();settings.widthMm=mm;
                    // Custom coordinates refer to the previous physical artwork size.
                    // Force defaults outside new bounds for every renderer and export.
                    settings.marks=null;markIndex=0;currentMarkIssue=null;
                    invalidateToolPanels();requestRender();
                }
                dialog.dismiss();rebuildNavigation();
            };
            // Do not silently accept an impossible standard-mat layout. Large
            // Silhouette/other-cutter artwork is still permitted by explicit choice.
            try{
                CricutSizeAdvisor.Estimate sheet=sizeEstimate(w,Math.abs(VinylEngine.toMm((float)w)-settings.widthMm)<=.005f);
                if(cricut&&!CricutMatPreflight.fitsMat(sheet.sheetWidthMm,sheet.sheetHeightMm,mat[0])){
                    new AlertDialog.Builder(this).setTitle(getString(R.string.ui_cricut_mat_size_warning))
                        .setMessage(sheet.label()+". It does not fit the selected "+CricutMatPreflight.matLabel(mat[0])+
                            " mat, so Cricut export will be refused until you choose a larger mat or a smaller width. "+
                            "VinylStudio never silently shrinks the artwork.")
                        .setNegativeButton(getString(R.string.ui_change_size),null)
                        .setPositiveButton(getString(R.string.ui_keep_this_size),(d,which)->accept.run()).show();
                    return;
                }
            }catch(IllegalArgumentException err){entry.setError("Invalid registration layout at this size");return;}
            accept.run();
        }));
        dialog.show();
    }
    /** Snip controls only: operation, polygon closing, and application. */
    private void snipPanel(){
        LinearLayout a=group();
        if(photo==null){action(a,"Import photo",this::pick);action(a,getString(R.string.take_photo),this::takePhoto);return;}
        LinearLayout modes=horizontal(a);
        TextView keep=action(modes,"Keep inside",()->{snipAction=SnipMask.KEEP_INSIDE;setTool(8);});
        keep.setBackground(outlined(snipAction==SnipMask.KEEP_INSIDE?SELECTED:StudioTheme.SURFACE_HIGH,
            snipAction==SnipMask.KEEP_INSIDE?ACCENT:StudioTheme.OUTLINE,14));
        TextView remove=action(modes,"Remove inside",()->{snipAction=SnipMask.REMOVE_INSIDE;setTool(8);});
        remove.setBackground(outlined(snipAction==SnipMask.REMOVE_INSIDE?SELECTED:StudioTheme.SURFACE_HIGH,
            snipAction==SnipMask.REMOVE_INSIDE?ACCENT:StudioTheme.OUTLINE,14));
        LinearLayout edits=horizontal(a);
        if(!snipClosed){
            action(edits,"Close points",()->{
                if(snipDraft.size()<3){toast(getString(R.string.ui_place_at_least_3_points_to));return;}
                recordSnip();snipClosed=true;
                getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                setTool(8);
            });
        }else action(edits,"Apply snip",this::applySnip);

        if(!snipDraft.isEmpty()){
            a.addView(text(snipDraft.size()+" points"+(snipClosed?" · closed":""),12,MUTED,false));
            action(a,"Discard outline",()->{
                recordSnip();
                if(editingSnipIndex>=0&&editingOriginal!=null)settings.snips.insert(editingSnipIndex,editingOriginal);
                editingSnipIndex=-1;editingOriginal=null;snipDraft.clear();snipClosed=false;
                setTool(8);requestRender();
            });
        }
        if(settings.snips!=null&&!settings.snips.isEmpty()){
            LinearLayout saved=group();section(saved,"Saved snips · "+settings.snips.size());
            for(int i=0;i<settings.snips.size();i++){
                final int index=i;
                action(saved,"Edit "+(i+1),()->editAppliedSnip(index));
            }
            action(saved,"Clear all",()->new AlertDialog.Builder(this)
                .setTitle(getString(R.string.ui_clear_all_snips))
                .setNegativeButton("Cancel",null).setPositiveButton("Clear",(d,w)->{
                    recordSnip();settings.snips.clear();snipDraft.clear();snipClosed=false;
                    editingSnipIndex=-1;editingOriginal=null;setTool(8);requestRender();
                }).show());

        }
    }
    private void applySnip(){
        if(!snipClosed||snipDraft.size()<3){toast(getString(R.string.ui_close_a_polygon_with_at_least));return;}
        float[] xy=new float[snipDraft.size()*2];for(int i=0;i<snipDraft.size();i++){xy[i*2]=snipDraft.get(i)[0];xy[i*2+1]=snipDraft.get(i)[1];}
        try{SnipMask.Operation op=new SnipMask.Operation(snipAction,xy);
            recordSnip();
            if(editingSnipIndex>=0)settings.snips.insert(editingSnipIndex,op);else settings.snips.add(op);
            editingSnipIndex=-1;editingOriginal=null;snipDraft.clear();snipClosed=false;
            chosen=-1;rebuildLayers();setTool(8);requestRender();
            toast(getString(R.string.ui_snip_applied));
        }catch(IllegalArgumentException e){new AlertDialog.Builder(this).setTitle(getString(R.string.ui_fix_selection)).setMessage(e.getMessage()).setPositiveButton("OK",null).show();}
    }
    private void recordSnip(){snipTimeline.remember(snipDraft,snipClosed,settings.snips,editingSnipIndex,editingOriginal);}
    private void applySnipState(SnipTimeline.State state){
        if(state==null){toast(getString(R.string.ui_nothing_to_undo_or_redo));return;}
        snipDraft.clear();for(float[] p:state.points)snipDraft.add(p.clone());
        snipClosed=state.closed;editingSnipIndex=state.editingIndex;
        editingOriginal=state.original==null?null:state.original.copy();
        settings.snips=state.mask.copy();
        rebuildLayers();setTool(8);requestRender();
    }
    private void snipHistory(boolean forward){
        SnipTimeline.State state=forward?
            snipTimeline.redo(snipDraft,snipClosed,settings.snips,editingSnipIndex,editingOriginal):
            snipTimeline.undo(snipDraft,snipClosed,settings.snips,editingSnipIndex,editingOriginal);
        applySnipState(state);
    }
    private void editAppliedSnip(int ix){
        if(ix<0||ix>=settings.snips.size())return;
        if(!snipDraft.isEmpty()){
            toast(getString(R.string.ui_finish_or_discard_the_current_outline));return;
        }
        recordSnip();SnipMask.Operation op=settings.snips.removeAt(ix);
        editingSnipIndex=ix;editingOriginal=op.copy();snipAction=op.kind;snipDraft.clear();
        for(int j=0;j<op.xy.length;j+=2)snipDraft.add(new float[]{op.xy[j],op.xy[j+1]});
        snipClosed=true;setTool(8);requestRender();
        toast(getString(R.string.ui_drag_the_points_and_apply_or));
    }
    private Path snipPath(float[] normalized,float wMm,float hMm){Path path=new Path();
        for(int i=0;i<normalized.length;i+=2){float x=normalized[i]*wMm,y=normalized[i+1]*hMm;
            if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
        }path.close();return path;
    }
    private float[] draftPoints(){float[] xy=new float[snipDraft.size()*2];for(int i=0;i<snipDraft.size();i++){xy[2*i]=snipDraft.get(i)[0];xy[2*i+1]=snipDraft.get(i)[1];}return xy;}
    private void boundaryPanel(){
        LinearLayout a=group();
        slider(a,"No-cut inset · mm",settings.marginMm,0,15,.25f,v->settings.marginMm=v);
        slider(a,"Outside material space · mm",settings.outerSpacingMm,0,30,.5f,v->settings.outerSpacingMm=v);
        a.addView(text(settings.circleCrop?"Inset follows circular crop · no extra outline cut":"Inset follows rectangular crop · no extra outline cut",12,ACCENT,false));
        if(continuousStyle!=null||mazePortraitMode)a.addView(text(activeCurveFamily()&&curveVinylRibbon?
            "Filled unioned ribbon SVG is for vinyl cutting. Review minimum width and gap before cutting.":
            "Open pen/engrave paths are not vinyl cuts; exclusion trimming is not supported for these exports.",12,0xffFFD093,false));

    }
    /** Reapply a deliberately selected stock profile after a sizing/style preset.
     * A manual dimension edit switches to Custom and is never overwritten here. */
    private void retainCutMaterialProfile(){
        if(cutMaterialProfileId==CutMaterialProfile.CUSTOM)return;
        CutMaterialProfile.Profile p=CutMaterialProfile.byId(cutMaterialProfileId);
        settings.minimumMm=p.featureMm;settings.bridgeMm=p.gapMm;
        stippleSettings.overprintGapMm=p.sheetGapMm;
        for(int i=0;i<stippleSettings.minDotMm.length;i++)
            stippleSettings.minDotMm[i]=Math.max(stippleSettings.minDotMm[i],p.dotMm);
    }
    private void selectCutMaterialProfile(int id){
        if(id<0||id>=CutMaterialProfile.names().length)return;
        checkpoint();cutMaterialProfileId=id;
        CutMaterialProfile.Profile p=CutMaterialProfile.byId(id);
        if(id!=CutMaterialProfile.CUSTOM){
            settings.minimumMm=p.featureMm;settings.bridgeMm=p.gapMm;
            stippleSettings.overprintGapMm=p.sheetGapMm;
            for(int i=0;i<stippleSettings.minDotMm.length;i++)
                stippleSettings.minDotMm[i]=Math.max(stippleSettings.minDotMm[i],p.dotMm);
            if(layeredCellMode)layeredMinFeatureMm=Math.max(layeredMinFeatureMm,p.featureMm);
        }
        invalidateToolPanels();setToolInternal(6);
        if(alternateActive())requestAlternate();else requestRender();
    }
    private void addMaterialProfileControls(LinearLayout a){
        section(a,"Cut material · advisory starting point");
        action(a,"Profile: "+CutMaterialProfile.byId(cutMaterialProfileId).name+"  ›",()->
            new AlertDialog.Builder(this).setTitle(getString(R.string.ui_material_cut_profile))
                .setSingleChoiceItems(CutMaterialProfile.names(),cutMaterialProfileId,(dialog,id)->{
                    dialog.dismiss();selectCutMaterialProfile(id);
                }).setNegativeButton("Cancel",null).show());
        a.addView(text(getString(R.string.ui_profiles_initialize_dimensional_limits_not_cutte),12,MUTED,false));
        action(a,"Export scrap calibration test…",()->new AlertDialog.Builder(this).setTitle(getString(R.string.ui_scrap_calibration_deliberately_tiny_features))
            .setItems(new String[]{"SVG (Cricut / SVG cutters)","DXF (Silhouette Basic)"},(dialog,which)->requestMaterialCalibration(which==1))
            .setNegativeButton("Cancel",null).show());
        action(a,"Record scrap calibration results…",this::showCalibrationRecords);
        if(stippleActive()&&cutMaterialProfileId==CutMaterialProfile.CUSTOM){
            slider(a,"Stipple same-sheet clearance · mm",stippleSettings.overprintGapMm,0,1.5f,.05f,v->{
                cutMaterialProfileId=CutMaterialProfile.CUSTOM;stippleSettings.overprintGapMm=v;requestAlternate();
            });
            a.addView(text(getString(R.string.ui_this_gap_applies_between_shapes_cut),12,MUTED,false));
        }
    }
    private void showCalibrationRecords(){
        android.content.SharedPreferences prefs=getSharedPreferences("scrap_calibration_v1",MODE_PRIVATE);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(list);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(getString(R.string.ui_calibration_profiles)).setView(scroll).setPositiveButton("Close",null).create();
        action(list,"New profile…",()->{dialog.dismiss();editCalibrationProfile(null,new CalibrationProfile("New profile","",CalibrationRecord.blank()));});
        String legacy=prefs.getString("results","");
        if(CalibrationRecord.valid(legacy)) action(list,"Copy previous calibration record…",()->{dialog.dismiss();editCalibrationProfile(null,new CalibrationProfile("Previous record",prefs.getString("material","").substring(0,Math.min(2048,prefs.getString("material","").length())),legacy));});
        java.util.ArrayList<String> keys=new java.util.ArrayList<>(prefs.getAll().keySet());java.util.Collections.sort(keys);
        for(String key:keys){if(!key.startsWith("profile_"))continue;
            try{CalibrationProfile p=CalibrationProfile.decode(prefs.getString(key,""));
                action(list,p.name,()->{dialog.dismiss();new AlertDialog.Builder(this).setTitle(p.name).setItems(new String[]{"Edit","Duplicate","Delete"},(d,w)->{
                    if(w==0)editCalibrationProfile(key,p);
                    else if(w==1)editCalibrationProfile(null,new CalibrationProfile(p.name.substring(0,Math.min(70,p.name.length()))+" copy",p.notes,p.results));
                    else new AlertDialog.Builder(this).setTitle(getString(R.string.ui_delete_profile)).setMessage(p.name).setNegativeButton("Cancel",null).setPositiveButton("Delete",(a,b)->{toast(prefs.edit().remove(key).commit()?"Profile deleted":"Delete failed");showCalibrationRecords();}).show();
                }).show();});
            }catch(RuntimeException e){list.addView(text(getString(R.string.ui_unreadable_calibration_profile_retained),12,MUTED,false));}
        }
        dialog.show();
    }
    private void editCalibrationProfile(String key,CalibrationProfile profile){
        android.content.SharedPreferences prefs=getSharedPreferences("scrap_calibration_v1",MODE_PRIVATE);
        final String[] record={profile.results};
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        EditText name=new EditText(this);name.setHint(getString(R.string.ui_profile_name));name.setText(profile.name);content.addView(name);
        EditText material=new EditText(this);material.setHint(getString(R.string.ui_material_cutter_blade_and_pressure));
        material.setText(profile.notes);content.addView(material);
        content.addView(text(getString(R.string.ui_record_one_test_sheet_pass_means),12,MUTED,false));
        for(int row=0;row<CalibrationRecord.ROWS.length;row++){
            final int r=row;
            action(content,CalibrationRecord.ROWS[row]+" …",()->{
                LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
                for(int size=0;size<CalibrationRecord.SIZES.length;size++){
                    section(body,CalibrationRecord.SIZES[size]+" mm");
                    for(int stage=0;stage<3;stage++){
                        final int z=size,t=stage;
                        android.widget.Spinner choice=new android.widget.Spinner(this);
                        String label=new String[]{"Cut","Weed","Transfer"}[stage];
                        android.widget.ArrayAdapter<String> adapter=new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{label+": not tested",label+": fail",label+": pass"});
                        choice.setAdapter(adapter);choice.setSelection(record[0].charAt(CalibrationRecord.index(r,z,t))-'0');
                        choice.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
                            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){record[0]=CalibrationRecord.set(record[0],r,z,t,position);}
                            public void onNothingSelected(android.widget.AdapterView<?> parent){}
                        });body.addView(choice);
                    }
                }
                android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(body);
                new AlertDialog.Builder(this).setTitle(CalibrationRecord.ROWS[r]).setView(scroll).setPositiveButton("Done",null).show();
            });
        }
        action(content,"Review candidate limits",()->{
            StringBuilder summary=new StringBuilder();
            for(int r=0;r<CalibrationRecord.ROWS.length;r++){
                int i=CalibrationRecord.candidate(record[0],r);
                summary.append(CalibrationRecord.ROWS[r]).append(": ").append(i<0?"Insufficient consistent passes":CalibrationRecord.SIZES[i]+" mm (candidate)").append('\n');
            }
            new AlertDialog.Builder(this).setTitle(getString(R.string.ui_unverified_candidate_limits)).setMessage(summary.toString()).setPositiveButton("Close",null).show();
        });
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(content);
        AlertDialog editor=new AlertDialog.Builder(this).setTitle(getString(R.string.ui_advisory_calibration_profile)).setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        editor.setOnShowListener(d->editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{CalibrationProfile p=new CalibrationProfile(name.getText().toString(),material.getText().toString(),record[0]);
                String id=key==null?"profile_"+java.util.UUID.randomUUID().toString():key;
                int count=0;for(String k:prefs.getAll().keySet())if(k.startsWith("profile_"))count++;
                if(key==null&&count>=100){toast(getString(R.string.ui_profile_limit_reached_100));return;}
                if(!prefs.edit().putString(id,CalibrationProfile.encode(p)).commit()){toast(getString(R.string.ui_profile_could_not_be_saved));return;}
                editor.dismiss();toast(getString(R.string.ui_calibration_profile_saved));showCalibrationRecords();
            }catch(IllegalArgumentException e){toast(getString(R.string.ui_enter_a_name_1_80_characters));}
        }));editor.show();
    }
    private void requestMaterialCalibration(boolean dxf){
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE,"VinylStudio-scrap-calibration-"+(dxf?"DXF":"SVG")+".zip");
        startActivityForResult(intent,dxf?EXPORT_CALIBRATION_DXF:EXPORT_CALIBRATION_SVG);
    }
    private void exportMaterialCalibration(Uri uri,boolean dxf){
        scheduleDiskJob(()->{
            try(StagedCutZip stage=new StagedCutZip(getCacheDir())){
                MaterialCalibrationSheet.write(stage.openStream(),dxf);
                publishStagedCutZip(stage,uri);
                postUi(()->toast(getString(R.string.ui_scrap_calibration_exported_read_the_zip)));
            }catch(Exception error){postUi(()->toast("Calibration export failed: "+friendlyFailure(error,"Unable to save calibration")));}
        });
    }
    private void appendStippleCoverageDiagnostic(LinearLayout a){
        TessellationEngine.Result mosaic=tessellationResult;
        if(!stippleActive()||mosaic==null||mosaic.stipple==null||alternatePreviewDraft||
                alternateReadyGeneration!=alternateGeneration)return;
        MultiColorStippleEngine.Result r=mosaic.stipple;
        section(a,"Stipple coverage map · current vector preview");
        a.addView(text(getString(R.string.ui_teal_covered_amber_nearby_opening_red),12,MUTED,false));
        int selected=tessSelectedLayer;
        if(cachedHeatmap!=null&&heatmapGeneration==alternateGeneration&&heatmapLayer==selected){
            android.widget.ImageView im=new android.widget.ImageView(this);
            StippleCoverageInspector.Scan scan=cachedHeatmap;
            im.setImageBitmap(Bitmap.createBitmap(scan.heatmap,scan.width,scan.height,Bitmap.Config.ARGB_8888));
            im.setAdjustViewBounds(true);im.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            a.addView(im,new LinearLayout.LayoutParams(-1,dp(220)));
            a.addView(text(String.format(Locale.US,"Eligible coverage estimate: %.1f%% · %d / %d pixels%s",scan.percent(),scan.covered,scan.eligible,
                selected<0?"":" · color "+(selected+1)),12,ACCENT,true));
        }else{
            a.addView(text(getString(R.string.ui_preparing_diagnostic),12,MUTED,false));
            final int token=alternateGeneration,owner=selected;
            final Bitmap source=alternatePreparedSource;
            diagnosticsExecutor.execute(()->{
                try{
                    int[] argb=null;
                    if(source!=null){
                        // The cut-ready stipple is at export resolution; match the source to it.
                        Bitmap matched=source.getWidth()==r.width&&source.getHeight()==r.height?source:
                            Bitmap.createScaledBitmap(source,r.width,r.height,true);
                        argb=new int[r.width*r.height];matched.getPixels(argb,0,r.width,0,0,r.width,r.height);
                    }
                    StippleCoverageInspector.Scan scan=StippleCoverageInspector.inspect(r,argb,owner);
                    postUi(()->{
                        if(token!=alternateGeneration||owner!=tessSelectedLayer)return;
                        cachedHeatmap=scan;heatmapGeneration=token;heatmapLayer=owner;
                        if(tool==6&&!boundaryPanelSelected){invalidateToolPanels();setToolInternal(6);}
                    });
                }catch(Exception e){android.util.Log.w("VinylCutCheck","Coverage scan unavailable",e);}
            });
        }
        StipplePreflight.Report preflight=StipplePreflight.inspect(r,settings.minimumMm,stippleSettings.overprintGapMm);
        a.addView(text(preflight.summary(),12,preflight.tooClose>0||preflight.undersized>0?0xffF1CF8E:MUTED,false));
        action(a,"Inspect individual closed vector paths",()->showStippleCutPaths(tessSelectedLayer));
    }
    /** Advisory results are always scoped to the active style and its completed generation.
     * This method must not recompute a full vector job on the UI thread. */
    private void appendSpecialtyCutReview(LinearLayout panel){
        section(panel,"Current style · production review");
        if(!specialtyFinalReady()){
            panel.addView(text("Final artwork is not ready. Finish adjustments and let this style render before exporting.",
                12,0xffFFC39E,true));
            return;
        }
        if(newStyleActive()){
            panel.addView(text(getString(R.string.ui_art_style_geometry_inspect_the_layer),
                12,ACCENT,true));
            return;
        }
        if(layeredCellMode){
            int layers=0,pieces=0;
            for(int i=0;i<layeredResult.options.layerCount;i++){
                if(layeredResult.pieceCounts[i]>0)layers++;
                pieces+=layeredResult.pieceCounts[i];
            }
            panel.addView(text("Layered Cell · "+layers+" populated layers · "+pieces+" cut pieces",12,ACCENT,true));
        }else if(tessellationMode){
            int total=0;
            for(int n:tessellationResult.counts)total+=n;
            panel.addView(text((stippleActive()?"Stipple":"Tessellation")+" · "+total+
                " closed shapes across "+tessellationResult.populated()+" colors",12,ACCENT,true));
            if(tessellationResult.options.registration)
                panel.addView(text("Registration marks contribute additional cut paths.",12,MUTED,false));
        }else if(tonalStencilMode){
            int populated=0;
            for(int i=0;i<tonalResult.layers.size();i++)if(!tonalResult.isEmpty(i))populated++;
            panel.addView(text("Multilayer stencil · "+populated+" populated vinyl layers",12,ACCENT,true));
            if(tonalCutCountGeneration==alternateReadyGeneration&&tonalCutCounts!=null){
                for(int i=0;i<tonalCutCounts.length;i++)if(tonalCutCounts[i]>0)
                    panel.addView(text("Layer "+(i+1)+": "+tonalCutCounts[i]+" cut contours",12,MUTED,false));
            }
        }else if(continuousStyle==ContinuousLineModes.LineStyle.SPIRAL){
            panel.addView(text("Photo Spiral · "+spiralRibbonResult.cutPieces+" closed vinyl piece(s)",12,ACCENT,true));
            if(spiralRibbonResult.hasTransparentMask)
                panel.addView(text(getString(R.string.ui_transparent_background_divides_this_ribbon_into),12,0xffFFC39E,false));
        }else if(activeCurveFamily()&&curveVinylRibbon){
            panel.addView(text(continuousStyle.name()+" · closed vinyl ribbon. Inspect tight corners and narrow necks.",12,ACCENT,true));
        }else if(mazePortraitMode){
            panel.addView(text(getString(R.string.ui_maze_pen_engraving_paths_not_a),12,0xffFFC39E,true));
        }else{
            panel.addView(text(continuousStyle.name()+" · one centerline for pen/engraving, not adhesive-vinyl cutting.",12,0xffFFC39E,true));
        }
        if(alternateCutWarnings!=null&&!alternateCutWarnings.isEmpty())
            panel.addView(text(alternateCutWarnings,12,0xffFFC39E,false));
        panel.addView(text("The numbered SVG files are checked again during export. Test-cut real vinyl before application.",12,MUTED,false));
    }
    private boolean artworkMetricsBusy=false;
    private void showArtworkCutMetrics(){
        if(artworkMetricsBusy){toast(getString(R.string.ui_artwork_analysis_is_running));return;}
        if(alternateActive()){showSpecialtyArtworkMetrics();return;}
        if(!standardFinalReady()){toast(getString(R.string.ui_wait_for_the_final_artwork_render));return;}
        final VinylEngine.Result snapshot=latest;
        final AutoSilhouetteUnderlay.Output base=currentUnderlay();final boolean only=silhouetteOnly();
        if(underlayRequested()&&base==null){toast(getString(R.string.ui_wait_for_the_solid_snip_base));return;}
        final int generation=serial;
        final float inspectionMinimum=settings.minimumMm;
        final float inspectionGap=settings.bridgeMm;
        artworkMetricsBusy=true;
        try{diagnosticsExecutor.execute(()->{
            String report;
            try{
                StringBuilder b=new StringBuilder("Artwork contours only; excludes registration. Areas are summed, not unioned. Cut length excludes travel.\n\n");
                if(base!=null){ArtworkCutMetrics.Stats m=ArtworkCutMetrics.measure(java.util.Collections.singletonList(base.shape));
                    b.append(String.format(Locale.US,"Solid Snip base\n%d regions · %d holes · %d cut loops\n%.1f mm cut length · %.2f cm² summed area\n\n",m.regions,m.holes,m.loops,m.lengthMm,m.summedAreaMm2/100));}
                for(int i=0;!only&&i<snapshot.shapes.size();i++){
                    if(generation!=serial)return;
                    if(!VinylEngine.cutLayer(snapshot,i))continue;
                    ArtworkCutMetrics.Stats m=ArtworkCutMetrics.measure(snapshot.shapes.get(i));
                    b.append(String.format(Locale.US,"%s\n%d regions · %d holes · %d cut loops\n%.1f mm cut length · %.2f cm² summed area\n\n",snapshot.settings.layers.get(i).name,m.regions,m.holes,m.loops,m.lengthMm,m.summedAreaMm2/100));
                }
                try{
                    java.util.List<java.util.List<VinylEngine.Shape>> sheets=new java.util.ArrayList<>();
                    java.util.List<String> names=new java.util.ArrayList<>();
                    if(base!=null){sheets.add(java.util.Collections.singletonList(base.shape));names.add("Solid Snip base");}
                    for(int i=0;!only&&i<snapshot.shapes.size();i++)if(VinylEngine.cutLayer(snapshot,i)){sheets.add(snapshot.shapes.get(i));names.add(snapshot.settings.layers.get(i).name);}
                    boolean[] included=new boolean[sheets.size()];java.util.Arrays.fill(included,true);
                    final long start=System.nanoTime();
                    ArtworkVisibility.Layer[] coverage=ArtworkVisibility.measure(sheets,included,()->{
                        if(generation!=serial||System.nanoTime()-start>5_000_000_000L)throw new IllegalStateException("Analysis cancelled or time limit");
                    });
                    b.append("Combined footprint and planned export stack (circles approximated):\n\n");
                    for(int i=0;i<coverage.length;i++)if(coverage[i]!=null){
                        ArtworkVisibility.Layer m=coverage[i];
                        b.append(String.format(Locale.US,"%s\n%d footprint components · %d footprint holes\n%.2f cm² union · %.2f visible · %.2f hidden\n\n",names.get(i),m.pieces,m.holes,m.area/100,m.visible/100,m.hidden/100));
                        b.append(ArtworkVisibility.diagnostics(m,inspectionMinimum,inspectionGap,()->{if(generation!=serial||System.nanoTime()-start>5_000_000_000L)throw new IllegalStateException("Analysis cancelled or time limit");})).append("\n");
                    }
                }catch(RuntimeException|OutOfMemoryError unavailable){b.append("Combined footprint unavailable for this geometry or analysis budget. Contour measurements above remain valid.\n");}
                report=b.toString();
            }catch(RuntimeException|OutOfMemoryError failure){report="Artwork measurements unavailable. Existing cut validation is unchanged.";}
            finally{postUi(()->artworkMetricsBusy=false);}
            final String message=report;
            postUi(()->{if(generation==serial&&latest==snapshot&&standardFinalReady()&&!alternateActive()&&currentUnderlay()==base&&silhouetteOnly()==only)
                new AlertDialog.Builder(this).setTitle("Artwork cut measurements").setMessage(message).setPositiveButton("Close",null).show();});
        });}catch(RejectedExecutionException busy){artworkMetricsBusy=false;toast(getString(R.string.ui_analysis_queue_busy_retry_shortly));}
    }
    private void showSpecialtyArtworkMetrics(){
        if(!specialtyFinalReady()){toast(getString(R.string.ui_wait_for_the_final_artwork_render));return;}
        final StyleKit.Result style=newStyleActive()?newStyleResult:null;
        final TessellationEngine.Result tiles=tessellationMode?tessellationResult:null;
        final LayeredCellPortraitEngine.Result cells=layeredCellMode?layeredResult:null;
        final MultilayerStencilEngine.Result tonal=tonalStencilMode?tonalResult:null;
        final SpaceFillingRibbon.Result ribbon=activeCurveFamily()&&curveVinylRibbon?curveRibbonResult:null;
        final SpiralPortraitRibbon.Result spiral=continuousStyle==ContinuousLineModes.LineStyle.SPIRAL?spiralRibbonResult:null;
        final float inspectionMinimum=settings.minimumMm;
        final float inspectionGap=settings.bridgeMm;
        final AutoSilhouetteUnderlay.Output base=currentUnderlay();
        final boolean only=silhouetteOnly();
        if(underlayRequested()&&base==null){toast(getString(R.string.ui_wait_for_the_solid_snip_base));return;}
        final int styleId=newStyleId, generation=alternateGeneration, revision=serial;
        if(style==null&&tiles==null&&cells==null&&tonal==null&&ribbon==null&&spiral==null&&!only){
            final Path line=mazeResult!=null?mazeResult.wallsPath:continuousPreview;
            final Bitmap prepared=alternatePreparedSource==null?photo:alternatePreparedSource;
            final int sourceWidth=mazeResult!=null?Math.max(1,Math.round(mazeResult.canvasSizePx)):(prepared==null?0:prepared.getWidth());
            if(line==null||sourceWidth<=0){toast(getString(R.string.ui_final_toolpath_is_unavailable));return;}
            showPenArtworkMetrics(line,settings.widthMm/(double)sourceWidth,generation,revision);
            return;
        }
        artworkMetricsBusy=true;
        try{diagnosticsExecutor.execute(()->{
            String message;
            try{
                Runnable guard=()->{if(generation!=alternateGeneration||revision!=serial)throw new CancellationException();};
                SpecialtyArtworkMetrics.Input input=spiral!=null&&style==null&&tiles==null&&cells==null&&tonal==null&&ribbon==null?SpecialtyArtworkMetrics.collectSpiral(spiral,base,only,guard):SpecialtyArtworkMetrics.collect(style,styleId,tiles,cells,tonal,ribbon,base,only,guard);
                message=SpecialtyArtworkMetrics.report(input,inspectionMinimum,inspectionGap,guard);
            }catch(RuntimeException|OutOfMemoryError failure){android.util.Log.w("VinylStudio","Artwork measurements failed",failure);
                message="Artwork measurements unavailable: "+(failure instanceof OutOfMemoryError?"memory limit":friendlyFailure(failure,"geometry analysis failed"))+". Existing cut validation is unchanged.";}
            finally{postUi(()->artworkMetricsBusy=false);}
            final String report=message;
            postUi(()->{if(generation==alternateGeneration&&revision==serial&&specialtyFinalReady()&&currentUnderlay()==base&&silhouetteOnly()==only)
                new AlertDialog.Builder(this).setTitle("Artwork cut measurements").setMessage(report).setPositiveButton("Close",null).show();});
        });}catch(RejectedExecutionException busy){artworkMetricsBusy=false;toast(getString(R.string.ui_analysis_queue_busy_retry_shortly));}
    }
    private void showPenArtworkMetrics(Path path,double scale,int generation,int revision){
        // Snapshot the already-rendered path; no regeneration or pixel tracing for this report.
        final Path snapshot=new Path(path);
        artworkMetricsBusy=true;
        try{diagnosticsExecutor.execute(()->{
            String message;
            try{
                ArtworkCutMetrics.Stats m=AssemblyPathMetrics.measure(scale,snapshot);
                message=String.format(Locale.US,"%d open paths · %d closed contours\nApprox. %.1f mm total toolpath length\n\nArtwork only. Excludes registration and travel between paths. Pen or engraving paths are not filled vinyl; no material area or blade clearance is inferred.",m.openPaths,m.loops,m.lengthMm);
            }catch(RuntimeException|OutOfMemoryError failure){message="Toolpath measurements unavailable for this geometry or analysis budget. Existing export checks remain unchanged.";}
            finally{postUi(()->artworkMetricsBusy=false);}
            final String report=message;
            postUi(()->{if(generation==alternateGeneration&&revision==serial&&specialtyFinalReady())
                new AlertDialog.Builder(this).setTitle(getString(R.string.ui_artwork_toolpath_measurements)).setMessage(report).setPositiveButton("Close",null).show();});
        });}catch(RejectedExecutionException busy){artworkMetricsBusy=false;toast(getString(R.string.ui_analysis_queue_busy_retry_shortly));}
    }
    private void cutPanel(){
        LinearLayout a=group();section(a,"Cut validation");
        action(a,"Artwork cut measurements",this::showArtworkCutMetrics);
        action(a,"Cutter import instructions",this::showCutterHandoff);
        addMaterialProfileControls(a);
        // Standard presets set cut minima automatically. Only Custom exposes physical overrides.
        if(cutMaterialProfileId==CutMaterialProfile.CUSTOM){
            inchSlider(a,"Minimum cut width",settings.minimumMm,.010f,.120f,.005f,v->{settings.minimumMm=v;});
            inchSlider(a,"Minimum gap / bridge",settings.bridgeMm,0,.120f,.005f,v->{settings.bridgeMm=v;});
            if(layeredCellMode){
                // D8: the render uses max(value, minimum + 0.05 mm); the slider starts there too (no dead zone).
                float[] cellRange=PhysicalSliderFloor.range(.3f,1.5f,settings.minimumMm+.05f,.05f);
                slider(a,"Layered Cell minimum feature · mm",PhysicalSliderFloor.effective(layeredMinFeatureMm,cellRange[0]),cellRange[0],cellRange[1],.05f,
                    v->{layeredMinFeatureMm=v;requestAlternate();});
            }
        }
        if(alternateActive()){
            if(specialtyFinalReady()&&currentSpecialtyRiskSource()!=null){
                action(a,showCutRiskMarkers?"Hide cut risks":"Show cut risks",()->{
                    toggleSpecialtyCutRiskMarkers();invalidateToolPanels();setToolInternal(6);
                });
            }
            appendSpecialtyCutReview(a);
            appendStippleCoverageDiagnostic(a);
            a.addView(text(getString(R.string.ui_material_profile_controls_the_physical_limits),12,MUTED,false));
            return;
        }
        if(standardFinalReady()){appendSvgCounts(a,latest);
            if(latestDiagnostics!=null){section(a,"Opaque vinyl visibility");
                for(int i=0;i<latestDiagnostics.layers.length;i++){
                    VinylDiagnostics.LayerStats st=latestDiagnostics.layers[i];
                    a.addView(text(String.format(Locale.US,"%s: %.1f%% covered, %.1f%% visible, %.0f%% hidden by upper layers",latest.settings.layers.get(i).name,st.coveredPct,st.visiblePct,st.hiddenFractionPct),12,st.hiddenFractionPct>=80?0xffF0CA89:MUTED,false));
                }
            }
            for(CutInsights.Notice issue:CutInsights.inspect(latest)){
                LinearLayout notice=horizontal(a);
                notice.addView(text((issue.severe?"● ":"◇ ")+issue.title+" · "+issue.detail,12,issue.severe?0xffF39A99:0xffF1CF8E,true));
                a.addView(text("Fix: "+issue.action,12,MUTED,false));
            }
            LinearLayout quickActions=horizontal(a);
            action(quickActions,showCutRiskMarkers?"Hide cut risks":"Show cut risks",()->{
                toggleCutRiskMarkers();invalidateToolPanels();setToolInternal(6);
            });
            action(quickActions,"Auto fit cuts",()->autoFitPaths());
            action(quickActions,"Adjust marks",()->openWorkspaceSubtool(5,1));
        }
        if(settings.snips!=null&&!settings.snips.isEmpty())a.addView(text(settings.snips.size()+" manual snip(s) affect every layer. Standard line ribbons are generated before Snip trimming; other shapes touching an edge may be omitted. No SVG clipping paths are exported.",12,0xff9FD8F0,false));
        a.addView(text(getString(R.string.ui_each_enabled_vinyl_color_is_cut),12,MUTED,false));
    }

    private long tonalSignature(int[] input,VinylEngine.Settings s){return tonalSignature(photoRevision,s);}
    private static long tonalSignature(long sourceRevision,VinylEngine.Settings s){
        long k=sourceRevision;k=31*k+s.levelsBlack;k=31*k+s.levelsWhite;
        k=31*k+Float.floatToIntBits(s.exposureStops);k=31*k+Float.floatToIntBits(s.photoContrast);
        k=31*k+Float.floatToIntBits(s.levelsGamma);k=31*k+Float.floatToIntBits(s.levelsShadows);
        k=31*k+Float.floatToIntBits(s.levelsHighlights);k=31*k+Float.floatToIntBits(s.edgePriority);
        if(s.snips!=null)for(SnipMask.Operation op:s.snips.operations()){
            k=31*k+op.kind;k=31*k+Arrays.hashCode(op.xy);
        }
        return k==0?1:k;
    }
    private void levelsPanel(){
        LinearLayout panel=group();
        slider(panel,"Exposure (stops)",settings.exposureStops,-1.5f,1.5f,.1f,v->settings.exposureStops=v);
        slider(panel,"Contrast",settings.photoContrast,.5f,1.8f,.05f,v->settings.photoContrast=v);
        slider(panel,"Shadows",settings.levelsShadows,-1f,1f,.05f,v->settings.levelsShadows=v);
        LinearLayout actions=horizontal(panel);
        action(actions,"Auto levels",()->{
            if(pixels==null){toast(getString(R.string.ui_import_a_photo_first));return;}
            checkpoint();int[] levels=ImageLevels.autoPoints(pixels,imageW,imageH,settings);
            settings.levelsBlack=levels[0];settings.levelsWhite=levels[1];setTool(10);requestRender();
        });
        action(actions,"Edge focus",()->{checkpoint();settings.edgePriority=.75f;
            settings.detailBoost=Math.max(settings.detailBoost,.55f);setTool(10);requestRender();});
        action(actions,"Reset",()->{checkpoint();settings.exposureStops=0;settings.photoContrast=1f;settings.levelsBlack=0;settings.levelsWhite=255;
            settings.levelsGamma=1;settings.levelsShadows=0;settings.levelsHighlights=0;settings.edgePriority=0;
            settings.detailBoost=0;setTool(10);requestRender();});
        slider(panel,"Black point",settings.levelsBlack,0,Math.min(180,settings.levelsWhite-1),1,v->settings.levelsBlack=Math.round(v));
        slider(panel,"White point",settings.levelsWhite,Math.max(100,settings.levelsBlack+1),255,1,v->settings.levelsWhite=Math.round(v));
        slider(panel,"Midtone gamma",settings.levelsGamma,.3f,3f,.05f,v->settings.levelsGamma=v);
        // Shadows is already available with Exposure and Contrast above.
        slider(panel,"Highlight detail",settings.levelsHighlights,-1f,1f,.05f,v->settings.levelsHighlights=v);
        slider(panel,"Edge definition",settings.edgePriority*100,0,100,5,v->settings.edgePriority=v/100f);
    }
    private void requestRender(){requestRender(false);}
    /** Registration geometry does not affect generated tile or New Style paths.
     * Reuse their immutable artwork and update only mark placement. Other
     * engines retain the proven RC145 render path until their SVG carriers can
     * be independently updated with equivalent safety guarantees.
     */
    private boolean stippleWasCutReady;
    private boolean reuseRegistrationOnly(){
        if(photo==null||activeWorkspace==0||!specialtyFinalReady())return false;
        // The solid Snip base has its own registration-bearing SVG. Rebuild both
        // sheets together rather than exporting new marks with an old base.
        if(currentUnderlay()!=null||underlayRequested())return false;
        final float[][] manual=RegistrationMarks.copyPositions(settings.marks);
        if(newStyleActive()&&newStyleResult!=null){
            projectSession.markEdited();
            currentMarkIssue=RegistrationPreviewGeometry.issue(newStyleResult.widthMm,
                newStyleResult.heightMm,settings.markSizeMm,settings.registrationClearanceMm,manual);
        }else if(tessellationMode&&tessellationResult!=null){
            TessellationEngine.Result previous=tessellationResult;
            stippleWasCutReady=stippleCutReady(previous);
            TessellationEngine.Result updated=previous.withRegistration(manual,settings.registration,
                settings.markStyle,settings.markSizeMm,settings.registrationClearanceMm);
            if(stippleVectorCache!=null&&stippleVectorCache.source==previous)
                stippleVectorCache=stippleVectorCache.rebind(updated);
            if(specialtyVectorCache!=null&&specialtyVectorCache.source==previous)
                specialtyVectorCache=specialtyVectorCache.rebindTessellation(updated);
            tessellationResult=updated;
            TessellationEngine.Options o=updated.options;
            showCutRiskMarkers=false;specialtyRiskSource=null;specialtyRiskMarkers=Collections.emptyList();
            projectSession.markEdited();
            currentMarkIssue=RegistrationPreviewGeometry.issue(o.widthMm,
                o.widthMm*tessellationResult.height/(float)Math.max(1,tessellationResult.width),
                settings.markSizeMm,settings.registrationClearanceMm,manual);
        }else return false;
        // Prepared ZIPs and diagnostics bind to a generation, not just geometry.
        // Mark edits invalidate an in-flight/staged ZIP even when paths are reused.
        alternateReadyGeneration=++alternateGeneration;
        ++previewRequestId;
        discardPreparedSpecialtyCutZip();
        registrationRenderPending=false;stencilNeedsRender=false;
        // D7: marks do not change cut geometry; keep a finished cut-ready stage, restart a pending one.
        if(newStyleActive()&&newStyleResult!=null){
            if(!newStyleCutReady())startNewStyleCutPreparation(alternateGeneration,newStyleResult,
                SuiteCutStage.sourceFor(newStyleCutStage,newStyleResult),newStyleId,newStyleRenderVariant,newStyleRenderColorCount);
        }else if(tessellationResult!=null&&tessellationResult.stipple!=null){
            if(stippleWasCutReady){stippleCutResult=tessellationResult;stippleCutGeneration=alternateGeneration;}
            else startStippleCutPreparation(alternateGeneration,tessellationResult);
        }
        stippleWasCutReady=false;
        commitMarkReport();
        if(artwork!=null)artwork.invalidate();
        if(activeWorkspace==4&&tool==5)refreshShelfPanel();
        return true;
    }
    /** Last compact mark set placed automatically (a clone; null when none). */
    private float[][] materializedCompactMarks;
    /**
     * M5 compact registration layout. Above/below marks are written as ordinary explicit marks so
     * every preview, check and exporter sees the same four points; RegistrationMarks.layout()
     * recognises them and drops the side margin. Marks the user dragged (no longer equal to the
     * last automatic set) are never moved. Corners (the default) keep the legacy null-marks path.
     */
    private void materializeMarkLayout(){
        if(RegistrationMarks.normalizePlacement(settings.markLayout)!=RegistrationMarks.PLACEMENT_ABOVE_BELOW){
            materializedCompactMarks=null;return;
        }
        if(photo==null)return;
        float w=settings.widthMm,h=settings.widthMm*imageH/(float)Math.max(1,imageW);
        // Automatic: none yet, unchanged since we placed them, or (after reopening a project) a
        // saved set that is still the compact pattern for this artwork. Dragged marks are kept.
        boolean automatic=settings.marks==null||(materializedCompactMarks!=null&&
            java.util.Arrays.deepEquals(settings.marks,materializedCompactMarks))||
            RegistrationMarks.isAboveBelowNear(settings.marks,w,h,settings.markSizeMm,settings.registrationClearanceMm);
        if(!automatic)return;
        settings.marks=RegistrationMarks.positionsFor(settings.markLayout,w,h,settings.markSizeMm,
            settings.registrationClearanceMm,settings.circleCrop);
        materializedCompactMarks=RegistrationMarks.copyPositions(settings.marks);
    }
    private void requestRegistrationRender(){
        materializeMarkLayout();
        projectReviewProgress.invalidate();
        committedMarkReport=null;
        if(!reuseRegistrationOnly())requestRender(true);
    }
    private void requestRender(boolean keepCurrentRegistrationArtwork){
        materializeMarkLayout();
        projectReviewProgress.invalidate();
        registrationRenderPending=keepCurrentRegistrationArtwork && photo!=null && activeWorkspace!=0;
        if(!keepCurrentRegistrationArtwork&&artwork!=null)artwork.clearMarksViewportPad();
        if(photo!=null)projectSession.markEdited();
        stencilNeedsRender=true;
        if(!sliderDragging)invalidateToolPanels();
        if(activeWorkspace==0){
            // Strict photo-only Prepare: no standard/specialty vector generation.
            serial++; alternateGeneration++; previewRequestId++;
            handler.removeCallbacks(renderDebounce);
            handler.removeCallbacks(alternateDraftDebounce);
            renderController.cancel(StudioRenderController.Lane.STANDARD);
            renderController.cancel(StudioRenderController.Lane.SPECIALTY);
            renderController.cancel(StudioRenderController.Lane.PREVIEW);
            
            alternateReadyGeneration=-1;standardPreviewDraft=true;alternatePreviewDraft=true;
            renderedSerial=-1;
            setRenderBusy(false);
            requestPreparePhotoPreview();
            if(artwork!=null)artwork.invalidate();
            return;
        }
        if(photo!=null)setRenderBusy(true);settings.substrateAware=false;if(alternateActive()){
            requestAlternate(keepCurrentRegistrationArtwork);artwork.invalidate();return;
        }serial++;latestDiagnostics=null;visibilityIssue=null;currentMarkIssue=null;
        if(activeWorkspace==4&&tool==3)updateVisibleLayerCutLabels();
        handler.removeCallbacks(renderDebounce);
        renderController.cancel(StudioRenderController.Lane.STANDARD);
        renderController.cancel(StudioRenderController.Lane.PREVIEW);
        
        
        handler.postDelayed(renderDebounce,sliderDragging?RenderDraftPolicy.DEBOUNCE_MS:100);artwork.invalidate();}
    /** Lighting is the only processing in Prepare; never constructs vinyl cut paths. */
    private void requestPreparePhotoPreview(){
        if(photo==null||pixels==null){tonalPreview=null;return;}
        final int token=serial;
        final VinylEngine.Settings snapshot=settings.copy();
        if(ImageLevels.isNeutral(snapshot)){
            tonalPreview=null; // Raw bitmap, with live mask clipping in CanvasView.
            return;
        }
        final int[] source=pixels;final int w=imageW,h=imageH;
        tonalPreview=null; // Never show lighting from an older photograph or slider position.
        renderController.submit(StudioRenderController.Lane.STANDARD,()->{
            try{
                int[] adjusted=ImageLevels.apply(source,w,h,snapshot);
                if(Thread.currentThread().isInterrupted()||token!=serial||activeWorkspace!=0)return;
                Bitmap image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                image.setPixels(adjusted,0,w,0,0,w,h);
                postUi(()->{
                    if(token==serial&&activeWorkspace==0){tonalPreview=image;artwork.invalidate();}
                });
            }catch(java.util.concurrent.CancellationException ignored){}
            catch(RuntimeException | OutOfMemoryError failure){
                // Photo preparation is advisory. Preserve the original image
                // when lighting preview cannot be allocated or processed.
                if(token!=serial||Thread.currentThread().isInterrupted())return;
                if(failure instanceof OutOfMemoryError){draftSourceCache.clear();ScaleAwarePhotoSampler.clearLightCache();}
                android.util.Log.e("VinylStudio","Prepare photo preview failed",failure);
                postUi(()->{if(token==serial&&activeWorkspace==0){
                    tonalPreview=null;
                    if(artwork!=null)artwork.invalidate();
                    if(status!=null)status.setText(failure instanceof OutOfMemoryError?
                        "Photo preview needs more memory":"Photo preview unavailable");
                }});
            }
        });
    }
    private void render(){if(pixels==null)return;final int id=serial;
        final VinylEngine.Settings s=settings.copy();final int[] input=pixels;
        final int w=imageW,h=imageH;
        final int underlayColor=autoSilhouetteColor;
        final long sourceRevision=photoRevision;
        final boolean draft=sliderDragging;
        standardPreviewDraft=true;
        status.setText(draft?"Draft cut preview…":"Generating cut paths…");
        renderController.submit(StudioRenderController.Lane.STANDARD,()->{try{
            if(id!=serial||Thread.currentThread().isInterrupted())return; // drop obsolete renders
            long prepareStart=System.nanoTime();
            // Draft geometry uses the same physical dimensions, screen pitch,
            // cut width and gap. Only source pixels and trace sample frequency
            // may change. Do the resize BEFORE tone adjustment to save work.
            final int[] draftSize=draft?RenderDraftPolicy.dimensions(w,h):new int[]{w,h};
            final int vectorW=draftSize[0],vectorH=draftSize[1];
            final long draftStart=System.nanoTime();
            final int[] previewInput=draft?
                draftSourceCache.draft(input,w,h,vectorW,vectorH,sourceRevision):input;
            final long draftPreparationMs=(System.nanoTime()-draftStart)/1_000_000L;
            long tonalKey=ImageLevels.isNeutral(s)?0L:tonalSignature(sourceRevision,s);
            int[] tonalInput;
            // One snapshot of both fields. An invalidation on another thread
            // cannot produce an NPE between signature lookup and pixel access.
            final PreparedToneCache cachedTone=preparedToneCache;
            if(tonalKey==0L)tonalInput=previewInput;
            else if(!draft&&cachedTone!=null&&tonalKey==cachedTone.signature)
                tonalInput=cachedTone.pixels;
            else{
                tonalInput=TiledImageLevels.apply(previewInput,vectorW,vectorH,s);
                // Never put a low-resolution draft into the final-size tone cache.
                if(!draft)preparedToneCache=new PreparedToneCache(tonalInput,tonalKey);
            }
            if(id!=serial||Thread.currentThread().isInterrupted())return;
            Bitmap tone=null;
            if(tonalKey!=0L){tone=Bitmap.createBitmap(vectorW,vectorH,Bitmap.Config.ARGB_8888);
                tone.setPixels(tonalInput,0,vectorW,0,0,vectorW,vectorH);}
            final Bitmap visibleTone=tone;
            if(id==serial&&visibleTone!=null){
                postUi(()->{if(id==serial){tonalPreview=visibleTone;artwork.invalidate();}});
            }
            final AutoSilhouetteUnderlay.Output generatedBase=AutoSilhouetteUnderlay.standardEligible(s)
                ?AutoSilhouetteUnderlay.make(s,input,w,h,underlayColor):null;
            int[] geometryInput=tonalInput;
            if(s.snipFillMode!=0&&s.snips!=null&&!s.snips.isEmpty()&&!hasSolidBase(s)&&generatedBase==null){
                geometryInput=SnipBackground.apply(tonalInput,vectorW,vectorH,s.snips,s.snipFillMode);
                s.snips=new SnipMask();
            }
            if(draft){
                // A small step change reduces tracing cost but doesn't change
                // physical line pitch, layer spacing, minimum width or gaps.
                s.lineSampleMm=RenderDraftPolicy.sampleStepMm(s.lineSampleMm);
            }
            if(id!=serial||Thread.currentThread().isInterrupted())return;
            long engineStart=System.nanoTime();
            VinylEngine.Result out;
            android.os.Trace.beginSection("VinylStudio.StandardGeometry");
            try{out=VinylEngine.generate(geometryInput,vectorW,vectorH,s);}
            finally{android.os.Trace.endSection();}
            if(id!=serial||Thread.currentThread().isInterrupted())return;
            final String markIssue=draft?null:VinylEngine.validateMarks(out);
            long previewStart=System.nanoTime();
            final CanvasGeometry vectorCache=CanvasGeometry.create(out);
            // O(number of contours) work stays off the main thread.
            final int[] completedCounts=draft?null:LayerCutCounts.standard(out);
            android.util.Log.d("VinylPerf", "standard draft="+draft+" styleKey=standard:"+s.pattern+" mode="+s.mode+" draftSourceMs="+draftPreparationMs+" prepareMs="+((engineStart-prepareStart)/1_000_000L)
                +" engineMs="+((previewStart-engineStart)/1_000_000L)
                +" canvasMs="+((System.nanoTime()-previewStart)/1_000_000L));
            if(id!=serial||Thread.currentThread().isInterrupted())return;
            // Publish usable cut paths immediately. Visibility analysis is independent and slower.
            postUi(()->{if(id!=serial)return;latest=out;canvasGeometry=vectorCache;tonalPreview=visibleTone;
                latestDiagnostics=null;visibilityIssue=null;currentMarkIssue=markIssue;renderedSerial=id;standardPreviewDraft=draft;
                standardLayerCutCounts=completedCounts;standardCountSerial=completedCounts==null?-1:id;
                registrationRenderPending=false;
                standardUnderlay=generatedBase==null?null:generatedBase.recolor(autoSilhouetteColor);standardUnderlaySerial=id;
                if(!draft){stencilNeedsRender=false;commitMarkReport();}
                artwork.invalidate();
                setRenderBusy(false);refreshLayerStatus(); // Cut counts are available immediately, before the visibility scan.
                if(activeWorkspace==4&&!draft)updateVisibleLayerCutLabels();
                // Exact vector paths stay visible during pinch/drag. No bitmap gesture swap.
            });
            if(!draft&&out.count()>0)diagnosticsExecutor.execute(()->{
                try{if(id!=serial)return;
                    VinylDiagnostics.Report visibility=VinylDiagnostics.analyze(out,72);
                    postUi(()->{if(id==serial&&latest==out){
                        latestDiagnostics=visibility;
                        visibilityIssue=null;
                        refreshLayerStatus(); // Show covered/visible values without another chip tap.
                    }});
                }catch(RuntimeException error){
                    android.util.Log.e("VinylHalftone", "Vinyl visibility analysis failed", error);
                    postUi(()->{if(id==serial&&latest==out){
                        latestDiagnostics=null;
                        visibilityIssue="Visibility estimate unavailable";
                        refreshLayerStatus(); // Keep valid path counts visible if advisory stats fail.
                    }});
                }
            });
        }catch(RuntimeException | OutOfMemoryError failure){
            if(id!=serial||Thread.currentThread().isInterrupted()||
                failure instanceof java.util.concurrent.CancellationException)return;
            if(failure instanceof OutOfMemoryError){
                // Reclaim optional caches without modifying the photo or
                // silently reducing physical cut resolution or vinyl widths.
                draftSourceCache.clear();ScaleAwarePhotoSampler.clearLightCache();preparedToneCache=null;
                specialtyBaseCache.clear();specialtyLitCache.clear();specialtyFieldCache.clear();GeometryTemplateCache.clear();newStyleGeometryMemo.clear();
            }
            android.util.Log.e("VinylStudio","Standard artwork render failed",failure);
            // D9: user-facing reason only; the stack trace is logged above.
            final String reason=failure instanceof OutOfMemoryError?
                "Not enough memory for this artwork. Lower the size or spacing detail":
                friendlyFailure(failure,"These settings could not be rendered. Try a larger spacing or another size");
            postUi(()->{if(id==serial){
                // Existing vectors are display-only: export cannot use stale paths.
                renderedSerial=-1;standardPreviewDraft=true;latestDiagnostics=null;
                if(artwork!=null)artwork.invalidate();
                setRenderBusy(false);
                if(status!=null)status.setText(getString(R.string.status_render_failed,reason));
                toast("Render failed: "+reason);
            }});
        }});
    }
    private void confirmExitProject(){
        if(projectSession.isSaving()||ProjectSaveGate.busy()){toast(getString(R.string.ui_finish_saving_before_closing_the_project));return;}
        if(photo==null){setWorkflowStep(0);return;}
        if(!projectSession.isDirty()){closeCurrentProject();return;}
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_save_changes_before_closing))
            .setMessage(getString(R.string.ui_your_edits_have_not_yet_been))
            .setNegativeButton("Cancel",null)
            .setNeutralButton("Discard",(d,w)->closeCurrentProject())
            .setPositiveButton(getString(R.string.ui_save_close),(d,w)->saveCurrentProject(this::closeCurrentProject)).show();
    }
    /** Fresh-install editor JSON (see onCreate). Null only if capture failed. */
    private String pristineEditorJson;
    /** Restore every serialized editor field to its fresh-install value (D19). */
    private void restorePristineEditor(){
        if(pristineEditorJson==null)return;
        boolean wasRestoring=restoring;restoring=true;
        try{
            JSONObject fresh=new JSONObject(pristineEditorJson);
            // The selected vinyl material is the user's physical stock, like the cutter: keep it.
            // Resetting it to Custom would silently drop its minimum cut width (e.g. Delicate 1.10 mm).
            fresh.put("cutMaterialProfileId",cutMaterialProfileId);
            settings=fromJson(fresh);restoreParkedVinylLayers(fresh);restoreArtworkModes(fresh);
        }catch(Exception unexpected){
            android.util.Log.w("VinylStudio","Pristine editor restore failed; explicit resets still apply",unexpected);
        }finally{restoring=wasRestoring;}
    }
    /** Instrumentation hooks (androidTest, same package). Not used by the app. */
    String editorSnapshotForTest(){return snapshot();}
    String pristineEditorForTest(){return pristineEditorJson;}
    void applyEditorForTest(String json) throws org.json.JSONException {
        JSONObject o=new JSONObject(json);settings=fromJson(o);restoreParkedVinylLayers(o);restoreArtworkModes(o);
    }
    /** Runs only the D19 reset; never deletes autosave or project files (unlike New Project). */
    void restorePristineEditorForTest(){restorePristineEditor();}
    int toolForTest(){return tool;}
    int workspaceForTest(){return activeWorkspace;}
    void selectWorkspaceForTest(int index){selectWorkspace(index);}
    void checkpointForTest(){checkpoint();}
    void undoForTest(){history(false);}
    float zoomForTest(){return artwork==null?Float.NaN:artwork.zoomForTest();}
    void zoomToForTest(float zoom){if(artwork!=null)artwork.zoomToForTest(zoom);}
    void selectLayerForTest(int index){selectUnifiedLayer(index);}
    float trioSpacingMemoryForTest(int n){return trioSpacingMemory[n];}
    void setTrioSpacingMemoryForTest(int n,float mm){trioSpacingMemory[n]=mm;}
    int cutMaterialForTest(){return cutMaterialProfileId;}
    /** The mat exports enforce (resolving a legacy project the same way export does). */
    String cricutMatForTest(){return selectedCricutMat();}
    private void closeCurrentProject(){
        if(projectSession.isSaving()){toast(getString(R.string.ui_finish_saving_before_closing_the_project));return;}
        projectSession.closeProject();
        discardCutReadyStages();
        encodedProjectOriginal=false;
        // Synchronous on purpose: must be durable before the encoded original is deleted.
        //noinspection ApplySharedPref
        getPreferences(MODE_PRIVATE).edit().putBoolean("encodedProjectOriginal",false).commit();
        serial++;alternateGeneration++;handler.removeCallbacks(renderDebounce);
        renderController.cancel(StudioRenderController.Lane.SPECIALTY);
        currentLibraryId=null;
        applyStyleSelection(StyleSelection.HALFTONE);newStyleResult=null;newStyleVectors=null;newStyleSelectedLayer=-1;
        cancelStartupRestore();NativeOriginalPhoto.clear(getFilesDir());restoredOriginalSource=null;photo=null;originalPhoto=null;changedPhoto();pixels=null;imageW=0;imageH=0;latest=null;canvasGeometry=null;tonalPreview=null;projectLabel="New project";autoSilhouetteColor=AutoSilhouetteUnderlay.DEFAULT_COLOR;autoSilhouetteColorManual=false;silhouetteOnlyMode=false;previewBaseOnly=false;
        setRenderBusy(false);
        applyStyleSelection(StyleSelection.HALFTONE);
        continuousPreview=null;spiralRibbonResult=null;tonalResult=null;tessellationResult=null;layeredResult=null;mazeResult=null;alternatePreviewBitmap=null;
        restorePristineEditor(); // before the resets below: it loads pristine JSON as the source
        projectSourceJson=null;projectSourceSchema=ProjectSettingsMigration.CURRENT_SCHEMA;
        newerProjectSchemaWarning=false;
        parkedVinylLayers.clear();alternateReadyGeneration=-1;settings=newProjectSettings();
        selectedPaletteKey="cmyk";paletteReversed.clear();invalidatePreparedPalette();
        newStyleHueDegrees=0;newStyleHueBaseColors=newStyleColors.clone();
        rebaseStandardHue();
        tonalHueDegrees=0;tonalMatchMyVinyl=false;
        tessColors=new int[]{0xff24252c,0xff71485e,0xffb36e89,0xffe3aa9e,
            0xfff1d6ba,0xff7695b1,0xff694b9d,0xffd87b45};
        tessHueDegrees=0;tessHueBaseColors=tessColors.clone();tessPalette="Portrait";
        tessPaletteRanks=SmartPaletteReduction.identity(8);newStylePaletteRanks=SmartPaletteReduction.identity(9);
        Arrays.fill(layeredLayerFamilies,-1);
        Arrays.fill(tonalColorOverrides,0);tonalPaletteRanks=SmartPaletteReduction.identity(8);tonalBaseSource=-1;tonalAutoWhiteBackground=true;tonalCutLayerPreview=true;tonalIncludeSourcePhoto=false;continuousStrokeMm=.35f;
        undo.clear();redo.clear();snipDraft.clear();snipTimeline.clear();snipClosed=false;
        editingSnipIndex=-1;editingOriginal=null;chosen=-1;
        getPreferences(MODE_PRIVATE).edit().remove("editor").remove("draftSnipV22")
            .remove("currentLibraryId").remove("currentLibraryName").remove(PhotoPairToken.PREF).apply();
        LibraryIdHandoff.clear(libraryHandoffStore()); // D14: never adopt into an empty editor

        // Process-scoped serial queue: clears follow pending photo writes even
        // if Android recreates this Activity before they finish.
        schedulePhotoJob(()->{
            ProjectPhotoStore.clear(getFilesDir());
            new File(getFilesDir(),"editing-photo.jpg").delete();
            new File(getFilesDir(),"editing-original.jpg").delete();
            projectPhotoStore.reset();
        });
        scheduleDiskJob(()->ProjectEncodedOriginal.clear(getFilesDir()));
        rebuildLayers();setWorkflowStep(0);artwork.invalidate();updateEditorSubtitle();status.setText(R.string.quality_project_closed_import_a_new_photo_or_open_a_project);
    }
    /** Library entry point for a clean document. Existing save/discard protection is reused. */
    private void requestNewProject(){
        requestProjectSwitch(()->{
            closeCurrentProject();
            pick();
        },"a new project");
    }
    /** Photo straight from the camera app, written into a private capture file (no CAMERA permission). */
    private void takePhoto(){
        final android.content.Context app=getApplicationContext();
        if(!scheduleDiskJob(()->{
            final Uri target;
            try{target=SharedExportProvider.uriFor(app,SharedExportFiles.newCameraCapture(SharedExportProvider.root(app)));}
            catch(IOException error){postUi(()->toast(getString(R.string.camera_unavailable)));return;}
            postUi(()->{
                Intent capture=new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                capture.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,target);
                capture.setClipData(android.content.ClipData.newRawUri("capture",target));
                capture.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try{pendingCameraCapture=target;startActivityForResult(capture,CAPTURE_PHOTO);}
                catch(android.content.ActivityNotFoundException none){pendingCameraCapture=null;toast(getString(R.string.camera_unavailable));}
            });
        }))toast(getString(R.string.camera_unavailable));
    }
    private void pick(){
        Intent photoPicker=Build.VERSION.SDK_INT>=33?
            new Intent(android.provider.MediaStore.ACTION_PICK_IMAGES):null;
        if(photoPicker!=null){
            // The system photo picker ships with every Android 13+ device. Launch it directly:
            // resolveActivity() is unreliable under Android 11+ package visibility rules.
            photoPicker.setType("image/*");
            try{startActivityForResult(photoPicker,IMPORT);return;}
            catch(android.content.ActivityNotFoundException unavailable){/* fall back to documents */}
        }
        Intent document=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        document.addCategory(Intent.CATEGORY_OPENABLE);
        document.setType("image/*");
        startActivityForResult(document,IMPORT);
    }
    private String projectStyleName(){
        if(newStyleActive()&&newStyleId>=0&&newStyleId<VinylStyleSuite.NAMES.length)
            return VinylStyleSuite.NAMES[newStyleId];
        if(tonalStencilMode)return "Multilayer stencil";
        if(layeredCellMode)return overprintCellKind>0?LayeredCellPortraitEngine.overprintName(overprintCellKind):fishScaleMode?"Fish Scale Mosaic Portrait":bottleCapMode?"Bottle Cap Mosaic Portrait":offsetCellMode?"Offset Cell Portrait":"Layered Cell";
        if(tessellationMode)return "Tessellation";
        if(mazePortraitMode)return "Maze";
        if(continuousStyle!=null)return continuousStyle.name().replace('_',' ');
        return settings.mode==VinylEngine.STENCIL?"Stencil":"Vinyl artwork";
    }
    private static String projectStyleFromJson(JSONObject json){
        int newStyle=json.optInt("newStyleId",-1);
        if(newStyle==VinylStyleSuite.RETIRED_STYLE_ID)return "Photo · choose another style";
        if(newStyle>=0&&newStyle<VinylStyleSuite.NAMES.length)return VinylStyleSuite.NAMES[newStyle];
        if(json.optBoolean("tonalStencilMode",false))return "Multilayer stencil";
        if(json.optBoolean("layeredCellMode",false))return json.optInt("overprintCellKind",0)>0?
                LayeredCellPortraitEngine.overprintName(json.optInt("overprintCellKind",0)):
                json.optBoolean("fishScaleMode",false)?"Fish Scale Mosaic Portrait":json.optBoolean("bottleCapMode",false)?"Bottle Cap Mosaic Portrait":json.optBoolean("offsetCellMode",false)?"Offset Cell Portrait":"Layered Cell";
        if(json.optBoolean("tessellationMode",false))return "Tessellation";
        if(json.optBoolean("mazePortraitMode",false))return "Maze";
        String line=json.optString("continuousStyleName","");
        return line.isEmpty()?"Vinyl artwork":line.replace('_',' ');
    }
    private String externalProjectName(Uri uri){
        String name="Imported project";
        try(android.database.Cursor cursor=getContentResolver().query(uri,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);
        }catch(Exception ignored){}
        if(name==null||name.trim().isEmpty())name="Imported project";
        name=name.replaceAll("(?i)\\.zip$","");
        return name;
    }
    private void saveCurrentProject(){saveCurrentProject(null);}
    private void saveCurrentProject(Runnable afterSaved){
        if(projectSession.isSaving()){toast(getString(R.string.ui_a_project_save_is_already_running));return;}
        if(photo==null){toast(getString(R.string.ui_import_a_photograph_before_saving));return;}
        if(!snipDraft.isEmpty()||editingSnipIndex>=0){
            new AlertDialog.Builder(this).setTitle("Unapplied snip")
                .setMessage(getString(R.string.ui_finish_the_current_snip_before_saving))
                .setPositiveButton(getString(R.string.ui_go_to_snip),(d,w)->setTool(8)).setNegativeButton("Cancel",null).show();
            return;
        }
        if(currentLibraryId!=null){saveProjectToLibrary(projectLabel,false,afterSaved);return;}
        promptProjectName(false,afterSaved);
    }
    private void promptProjectName(boolean saveAs,Runnable afterSaved){
        if(photo==null){toast(getString(R.string.ui_no_photo_is_open));return;}
        EditText input=new EditText(this);input.setSingleLine(true);input.setSelectAllOnFocus(true);
        input.setText(saveAs?projectLabel+" copy":projectStyleName()+" · "+
            new java.text.SimpleDateFormat("MMM d, HH:mm",Locale.US).format(new Date()));
        LinearLayout wrapper=new LinearLayout(this);wrapper.setPadding(dp(22),dp(8),dp(22),0);
        wrapper.addView(input,new LinearLayout.LayoutParams(-1,-2));
        new AlertDialog.Builder(this).setTitle(saveAs?"Save as new project":"Save to project library")
            .setView(wrapper).setPositiveButton("Save",(d,w)->saveProjectToLibrary(input.getText().toString(),saveAs,afterSaved))
            .setNegativeButton("Cancel",null).show();
    }
    private void saveProjectToLibrary(String projectName){saveProjectToLibrary(projectName,false,null);}
    private void saveProjectToLibrary(String projectName,boolean saveAs,Runnable afterSaved){
        if(photo==null){toast(getString(R.string.ui_import_a_photograph_before_saving));return;}
        if(projectSession.isSaving()||ProjectSaveGate.busy()){toast(getString(R.string.ui_a_project_save_is_already_running_e1988f));return;}
        if(!snipDraft.isEmpty()||editingSnipIndex>=0){toast(getString(R.string.ui_finish_the_current_snip_before_saving_72686b));return;}
        final Bitmap photoSnap=photo,originalSnap=originalPhoto==null?photo:originalPhoto;
        final byte[] originalSourceSnap=restoredOriginalSource;
        final File nativeSourceFile=NativeOriginalPhoto.active(getFilesDir());
        if(nativeSourceTooLargeForPortableZip(nativeSourceFile,originalSourceSnap))
            toast(getString(R.string.ui_camera_native_source_exceeds_20_mb));
        final boolean requireArchiveOriginal=encodedProjectOriginal;
        final Bitmap specialtySnap=alternateActive()&&specialtyFinalReady()?alternatePreviewBitmap:null;
        final VinylEngine.Result standardSnap=!alternateActive()&&standardFinalReady()?latest:null;
        final AutoSilhouetteUnderlay.Output thumbnailBase=currentUnderlay();
        final boolean thumbnailOnly=silhouetteOnly();
        final String json,style=projectStyleName();
        try{json=toJson(settings).toString();}
        catch(RuntimeException|OutOfMemoryError error){reportProjectCaptureFailure(error);return;}
        final String oldId=saveAs?null:currentLibraryId,rawName=projectName;
        final String editorIdAtSave=currentLibraryId; // D14: the id the editor shows (also for Save As)
        final long savedRevision=projectSession.revision();
        final String savedPhotoToken=photoPairToken(photoRevision);
        if(!ProjectSaveGate.begin()){
            toast(getString(R.string.ui_another_project_save_is_finishing));return;
        }
        if(!projectSession.beginSave()){ProjectSaveGate.end();return;}
        if(status!=null)status.setText(R.string.quality_saving_project_to_library);toast(getString(R.string.ui_saving_project));
        if(!scheduleDiskJob(()->{
            boolean gateReleased=false;
            try{
                byte[] thumb=ProjectSaveSupport.optionalThumbnail(()->{
                    Bitmap generated=null;
                    try{
                        if(specialtySnap!=null)return StudioProjectThumbnails.encode(specialtySnap);
                        if(standardSnap!=null){
                            generated=renderStandardCompositePreview(standardSnap,360,thumbnailBase,thumbnailOnly);
                            return StudioProjectThumbnails.encode(generated);
                        }
                        return StudioProjectThumbnails.encode(photoSnap);
                    }finally{if(generated!=null)generated.recycle();}
                },()->StudioProjectThumbnails.encode(photoSnap),
                    error->android.util.Log.w("VinylLibrary","Optional thumbnail unavailable",error));
                StudioProjectLibrary.Entry entry;
                // Source fidelity gate: never fall back to a preview if the
                // verified encoded original disappeared or is ambiguous.
                final File verifiedOriginal=ProjectEncodedOriginal.requireSelected(
                    getFilesDir(),requireArchiveOriginal);
                try(ProjectSourceSnapshot sources=ProjectSourceSnapshot.capture(
                    getCacheDir(),verifiedOriginal,
                    nativeSourceFile!=null&&nativeSourceFile.isFile()&&
                    nativeSourceFile.length()<=ProjectArchiveReader.MAX_SOURCE_ENTRY?nativeSourceFile:null)){
                    // Build 554: carry the cut sheets verified for exactly this edit, if any.
                    final File exportedCuts=LastVerifiedExport.matching(getFilesDir(),savedRevision,savedPhotoToken);
                    entry=projectLibrary.save(oldId,rawName,style,thumb,out->{
                        ZipOutputStream zip=new ZipOutputStream(out);
                        StudioProjectArchiveWriter.write(zip,
                            stream->photoSnap.compress(Bitmap.CompressFormat.PNG,100,stream),
                            stream->originalSnap.compress(Bitmap.CompressFormat.PNG,100,stream),json,
                            portableOriginalSupplier(sources.nativeOriginal,originalSourceSnap),sources.archiveOriginal);
                    },exportedCuts);
                }
                ProjectSaveGate.end();gateReleased=true;
                // D14: durable BEFORE the UI callback, which a recreated Activity never runs.
                LibraryIdHandoff.record(libraryHandoffStore(),entry.id,entry.name,savedPhotoToken,editorIdAtSave);
                boolean delivered=postUi(()->{
                    LibraryIdHandoff.clear(libraryHandoffStore()); // delivered directly
                    boolean stillCurrent=photo==photoSnap;
                    boolean safeToSwitch=projectSession.completeSave(savedRevision,stillCurrent);
                    if(stillCurrent){
                        currentLibraryId=entry.id;projectLabel=entry.name;
                        lifecycleEditorSnapshot.invalidate();
                        getPreferences(MODE_PRIVATE).edit().putString("currentLibraryId",entry.id)
                            .putString("currentLibraryName",entry.name)
                            .putBoolean("editorDirty",projectSession.isDirty()).apply();
                        updateEditorSubtitle();
                    }
                    projectThumbnails.remove(entry.id);
                    if(status!=null)status.setText(projectSession.isDirty()?"Snapshot saved; new edits are unsaved":"Project saved to library");
                    toast(projectSession.isDirty()?"Saved snapshot; newer edits remain unsaved":"Project saved");
                    if(afterSaved!=null){
                        if(safeToSwitch)afterSaved.run();
                        else toast(getString(R.string.ui_finish_saving_newer_changes_before_switching));
                    }
                });
                if(!delivered)projectSession.failSave();
            }catch(OutOfMemoryError oom){
                projectSession.failSave();
                android.util.Log.e("VinylLibrary","Project save ran out of memory",oom);
                postUi(()->{
                    if(status!=null)status.setText(R.string.status_project_not_saved_memory);
                    toast(getString(R.string.ui_project_not_saved_insufficient_memory));
                });
            }catch(Exception e){
                projectSession.failSave();android.util.Log.e("VinylLibrary","Project save failed",e);postUi(()->{
                if(status!=null)status.setText(getString(R.string.status_project_save_failed,friendlyFailure(e,"Unable to save project")));
                toast("Project NOT saved: "+friendlyFailure(e,"Unable to save project"));
            });}finally{if(!gateReleased)ProjectSaveGate.end();}
        })){projectSession.failSave();ProjectSaveGate.end();}
    }
    private void openLibraryProject(StudioProjectLibrary.Entry entry){
        if(entry.damaged){
            new AlertDialog.Builder(this).setTitle("Project cannot be opened")
                .setMessage("All saved revisions of this project appear unreadable. Its original bytes are still stored. Use the project's menu to export the damaged ZIP for recovery or delete it after safeguarding your files.")
                .setPositiveButton("OK",null).show();
            return;
        }
        requestProjectSwitch(()->loadProject(null,entry.id,entry.name),entry.name);
    }
    private void requestProjectSwitch(Runnable open,String label){
        if(projectSession.isSaving()||ProjectSaveGate.busy()){toast(getString(R.string.ui_finish_the_current_save_first));return;}
        if(photo==null||!projectSession.isDirty()){open.run();return;}
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_unsaved_changes))
            .setMessage("Save your current edits before opening "+label+"?")
            .setNegativeButton("Cancel",null)
            .setNeutralButton(getString(R.string.ui_discard_open),(d,w)->open.run())
            .setPositiveButton(getString(R.string.ui_save_open),(d,w)->saveCurrentProject(open)).show();
    }
    private void projectCardMenu(View anchor,StudioProjectLibrary.Entry entry,Dialog gallery){
        PopupMenu menu=new PopupMenu(this,anchor);
        if(entry.damaged){menu.getMenu().add("Export damaged ZIP");menu.getMenu().add("Delete");}
        else {menu.getMenu().add("Export ZIP");menu.getMenu().add(getString(R.string.gallery_original_cuts));menu.getMenu().add("Rename");menu.getMenu().add("Delete");}
        menu.setOnMenuItemClickListener(item->{
            if(item.getTitle().toString().equals(getString(R.string.gallery_original_cuts))){
                // Existence is checked off the UI thread; the picker opens only if sheets were kept.
                final String id=entry.id,label=entry.name;
                scheduleDiskJob(()->{
                    final boolean available=projectLibrary.exportedCuts(id)!=null;
                    postUi(()->{
                        if(!available){toast(getString(R.string.gallery_original_cuts_none));return;}
                        pendingOriginalCutsId=id;
                        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/zip");
                        intent.putExtra(Intent.EXTRA_TITLE,label.replaceAll("[^a-zA-Z0-9 ._-]","_")+"-cut-sheets.zip");
                        startActivityForResult(intent,EXPORT_ORIGINAL_CUTS);
                    });
                });
            }else if(item.getTitle().toString().equals("Export ZIP")||
                    item.getTitle().toString().equals("Export damaged ZIP")){
                pendingLibraryExport.set(entry.damaged?"damaged:"+entry.id:entry.id);
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/zip");
                intent.putExtra(Intent.EXTRA_TITLE,entry.name.replaceAll("[^a-zA-Z0-9 ._-]","_")+
                    (entry.damaged?"-DAMAGED-RECOVERY.zip":".zip"));
                startActivityForResult(intent,EXPORT_LIBRARY_ITEM);
            }else if(item.getTitle().toString().equals("Rename")){
                EditText name=new EditText(this);name.setSingleLine(true);name.setText(entry.name);
                LinearLayout wrap=new LinearLayout(this);wrap.setPadding(dp(20),dp(10),dp(20),0);
                wrap.addView(name,new LinearLayout.LayoutParams(-1,-2));
                new AlertDialog.Builder(this).setTitle(getString(R.string.ui_rename_project)).setView(wrap)
                    .setNegativeButton("Cancel",null).setPositiveButton("Rename",(d,w)->{
                        final String label=name.getText().toString();
                        libraryJobs.rename(entry.id,label,renamed->{
                            if(entry.id.equals(currentLibraryId)){projectLabel=renamed.name;updateEditorSubtitle();}
                            gallery.dismiss();showProjectLibrary();
                        },e->toast("Rename failed: "+friendlyFailure(e,"Unable to rename project")));
                    }).show();
            }else{
                new AlertDialog.Builder(this).setTitle(getString(R.string.ui_delete_this_project))
                    .setMessage(""+entry.name+" will be permanently deleted from the in-app library. Exported ZIP backups are not affected.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{
                        libraryJobs.delete(entry.id,()->{
                            projectThumbnails.remove(entry.id);
                            if(entry.id.equals(currentLibraryId))currentLibraryId=null;
                            gallery.dismiss();showProjectLibrary();
                        },e->toast("Delete failed: "+friendlyFailure(e,"Unable to delete project")));
                    }).show();
            }
            return true;
        });menu.show();
    }
    private void showProjectLibrary(){
        if(canvasFullscreen)setCanvasFullscreen(false);
        libraryJobs.list(entries->{if(!isFinishing()&&!isDestroyed()){showProjectLibraryWithEntries(entries);productFlows.maybeShowBackupReminder(entries.size());}},
            e->toast("Could not open project library: "+friendlyFailure(e,"Unable to open project library")));
    }
    /** Search/sort are in-memory operations, never synchronous disk reads or ZIP decodes. */
    /** Gallery UI lives in ProjectGalleryDialog; the activity supplies actions through its Host. */
    private void showProjectLibraryWithEntries(final List<StudioProjectLibrary.Entry> saved){
        new ProjectGalleryDialog(this,ui,new ProjectGalleryDialog.Host(){
            @Override public boolean hasEditableProject(){return photo!=null;}
            @Override public void requestNewProject(){StudioActivity.this.requestNewProject();}
            @Override public void saveCurrentProject(){StudioActivity.this.saveCurrentProject();}
            @Override public void importProjectZip(){chooseProject(false);}
            @Override public void chooseLibraryBackup(boolean export){StudioActivity.this.chooseLibraryBackup(export);}
            @Override public void openProject(StudioProjectLibrary.Entry entry){openLibraryProject(entry);}
            @Override public void cardMenu(View anchor,StudioProjectLibrary.Entry entry,Dialog gallery){projectCardMenu(anchor,entry,gallery);}
            @Override public void bindThumbnail(String id,ImageView target){projectThumbnails.bind(projectLibrary,id,target);}
            @Override public String searchQuery(){return gallerySearchQuery;}
            @Override public void setSearchQuery(String query){gallerySearchQuery=query;}
            @Override public android.content.SharedPreferences prefs(){return getPreferences(MODE_PRIVATE);}
        }).show(saved);
    }
    private void chooseLibraryBackup(boolean export){
        Intent i=new Intent(export?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");
        if(export)i.putExtra(Intent.EXTRA_TITLE,"VinylStudio-Library-Backup-"+
            new java.text.SimpleDateFormat("yyyyMMdd",Locale.US).format(new Date())+".zip");
        startActivityForResult(i,export?EXPORT_LIBRARY_BACKUP:IMPORT_LIBRARY_BACKUP);
    }
    /** Every library job writes a user-selected SAF document through this truncating target. */
    private StudioLibraryJobs.OutputTarget libraryDocument(Uri target){
        return LibraryDocumentStreams.document(mode->getContentResolver().openOutputStream(target,mode));
    }
    private void exportLibraryArchive(Uri target,String id){
        if(id.startsWith("damaged:")){
            libraryJobs.exportDamagedProject(id.substring("damaged:".length()),
                libraryDocument(target),
                ()->toast("Damaged ZIP saved for recovery; it has not been validated"),
                e->toast("Recovery export failed: "+friendlyFailure(e,"Unable to write this document")));
        }else{
            libraryJobs.exportProject(id,libraryDocument(target),
                ()->toast(getString(R.string.ui_project_zip_exported)),
                e->toast("Export failed: "+friendlyFailure(e,"Unable to write this document")));
        }
    }
    private void exportLibraryBackup(Uri target){
        toast(getString(R.string.ui_creating_library_backup));
        libraryJobs.backup(libraryDocument(target),
            count->{productFlows.recordLibraryBackup();
                toast("Backed up "+count+" projects. Keep this ZIP safe.");},
            e->toast("Backup failed: "+friendlyFailure(e,"Unable to complete the backup")));
    }
    private void importLibraryBackup(Uri source){
        toast(getString(R.string.ui_restoring_projects_existing_projects_will_not));
        libraryJobs.restore(()->getContentResolver().openInputStream(source),count->{
            projectThumbnails.evictAll();toast("Restored "+count+" projects");showProjectLibrary();
        },e->toast("Restore stopped: "+friendlyFailure(e,"The backup could not be read")+". Already restored projects remain saved."));
    }
    private void chooseProject(boolean write){
        if(write&&(!snipDraft.isEmpty()||editingSnipIndex>=0)){
            new AlertDialog.Builder(this).setTitle("Unapplied snip")
                .setMessage(getString(R.string.ui_finish_or_discard_the_current_outline_5db323))
                .setNegativeButton("Cancel",null).setPositiveButton(getString(R.string.ui_go_to_snip),(d,w)->setTool(8)).show();return;
        }
        Intent intent=new Intent(write?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/zip");if(write)intent.putExtra(Intent.EXTRA_TITLE,"VinylStudio-project.zip");startActivityForResult(intent,write?SAVE_PROJECT:OPEN_PROJECT);}

    private AssemblyLayout assemblyLayout(float width,float height,boolean registration,
            int markStyle,float markSize,float clearance,float[][] requested,float outerSpacing,float minimum){
        return assemblyLayout(width,height,registration,markStyle,markSize,clearance,requested,outerSpacing,minimum,false);
    }
    /** Guide/PDF pages use the same SheetFrame as the cut sheets (ADR-0001), circle crop included. */
    private AssemblyLayout assemblyLayout(float width,float height,boolean registration,
            int markStyle,float markSize,float clearance,float[][] requested,float outerSpacing,float minimum,boolean circleCrop){
        if(!registration||assemblyReferenceOnly)return new AssemblyLayout(width,height,Math.max(0f,outerSpacing),false,null,
            markStyle,markSize,minimum);
        SheetFrame frame=SheetFrame.of(width,height,true,markSize,clearance,requested,outerSpacing,circleCrop);
        return new AssemblyLayout(width,height,frame.paddingMm,true,frame.marks(),markStyle,markSize,minimum);
    }
    private AssemblyLayout specialtyAssemblyLayout(SpecialtyVectorCache vectors){
        Object source=vectors.source;
        if(source instanceof MultilayerStencilEngine.Result){
            MultilayerStencilEngine.Result r=(MultilayerStencilEngine.Result)source;
            MultilayerStencilEngine.Options o=r.options;
            return assemblyLayout(r.widthMm,r.heightMm,o.registration,o.markStyle,o.markDiameterMm,
                o.registrationClearanceMm,r.registrationPoints,o.outerSpacingMm,o.minimumCutMm,o.circleCrop);
        }
        if(source instanceof LayeredCellPortraitEngine.Result){
            LayeredCellPortraitEngine.Result r=(LayeredCellPortraitEngine.Result)source;
            LayeredCellPortraitEngine.Options o=r.options;
            float height=o.widthMm*r.height/(float)Math.max(1,r.width);
            return assemblyLayout(o.widthMm,height,o.registration,o.markStyle,o.markSizeMm,
                o.registrationClearanceMm,o.marks,o.outerSpacingMm,o.minimumCutMm,o.circleCrop);
        }
        if(source instanceof TessellationEngine.Result){
            TessellationEngine.Result r=(TessellationEngine.Result)source;
            TessellationEngine.Options o=r.options;
            float height=o.widthMm*r.height/(float)Math.max(1,r.width);
            return assemblyLayout(o.widthMm,height,o.registration,o.markStyle,o.markSizeMm,
                o.registrationClearanceMm,o.marks,o.outerSpacingMm,o.minimumCutMm,o.circleCrop);
        }
        float height=settings.widthMm*vectors.height/(float)Math.max(1,vectors.width);
        return assemblyLayout(settings.widthMm,height,false,settings.markStyle,settings.markSizeMm,
            settings.registrationClearanceMm,null,Math.max(0f,settings.outerSpacingMm),settings.minimumMm);
    }
    private String specialtyGuideName(Object source,int index){
        if(source instanceof MultilayerStencilEngine.Result){
            MultilayerStencilEngine.Layer layer=((MultilayerStencilEngine.Result)source).layers.get(index);
            return layer.baseLayer?"Base Layer":"Overlay "+(index+1);
        }
        if(source instanceof LayeredCellPortraitEngine.Result){
            LayeredCellPortraitEngine.Result r=(LayeredCellPortraitEngine.Result)source;
            return r.options.solidBase&&index==0?"Solid Cell Base":"Cell Layer "+(index+1);
        }
        if(source instanceof TessellationEngine.Result)return "Tessellation Layer "+(index+1);
        if(source instanceof SpiralPortraitRibbon.Result)return "Spiral Vinyl Ribbon";
        if(source instanceof SpaceFillingRibbon.Result)
            return (continuousStyle==null?"Continuous":""+continuousStyle.name().replace('_',' '))+" Vinyl Ribbon";
        return "Vinyl Layer "+(index+1);
    }
    private int specialtyGuideCount(Object source,int index){
        if(source instanceof MultilayerStencilEngine.Result)return tonalVectorContourCount((MultilayerStencilEngine.Result)source,index);
        if(source instanceof LayeredCellPortraitEngine.Result)return ((LayeredCellPortraitEngine.Result)source).contourCounts[index];
        if(source instanceof TessellationEngine.Result)return ((TessellationEngine.Result)source).counts[index];
        if(source instanceof SpiralPortraitRibbon.Result)return Math.max(1,((SpiralPortraitRibbon.Result)source).cutPieces);
        return 1;
    }
    private static String nonemptyLayerName(String value,int fallback){
        return value==null||value.trim().isEmpty()?"Layer "+fallback:value.trim();
    }
    private AssemblyGuideDocument.Document buildAssemblyGuideDocument(){
        final AutoSilhouetteUnderlay.Output base=currentUnderlay();
        final boolean baseOnly=silhouetteOnly();
        final int paper=settings.paper;
        if(!alternateActive()){
            VinylEngine.Result result=latest;
            if(result==null)throw new IllegalStateException("Final standard geometry is unavailable");
            AssemblyLayout layout=new AssemblyLayout(result.settings.widthMm,result.heightMm,result.padMm,
                result.settings.registration&&!assemblyReferenceOnly,
                assemblyReferenceOnly?null:result.marks,result.settings.markStyle,result.settings.markSizeMm,result.settings.minimumMm);
            GuideAssembler guide=new GuideAssembler(projectLabel,projectStyleName(),layout,paper,assemblyReferenceOnly,selectedPaletteKey);
            ArtworkDocumentExport.Page composite=ArtworkDocumentExport.standard(result,base,baseOnly,!assemblyReferenceOnly);
            if(base!=null){guide.add("Solid Snip",base.color,base.cutContours(),AssemblyGuidePlan.Kind.VINYL,
                "Bottom support silhouette.",ArtworkDocumentExport.standardLayer(result,base,-1,!assemblyReferenceOnly));
                guide.standardMetrics(java.util.Collections.singletonList(base.shape));}
            if(!baseOnly)for(int i=0;i<result.settings.layers.size();i++)if(VinylEngine.cutLayer(result,i)){
                VinylEngine.Layer layer=result.settings.layers.get(i);
                guide.add(nonemptyLayerName(layer.name,i+1),layer.color,result.shapes.get(i).size(),
                    AssemblyGuidePlan.Kind.VINYL,ExportColorLabels.channel(layer.cmykChannel),
                    ArtworkDocumentExport.standardLayer(result,base,i,!assemblyReferenceOnly));
                guide.standardMetrics(result.shapes.get(i));
            }
            return guide.finish(composite);
        }
        if(newStyleActive()){
            StyleKit.Result result=newStyleResult;NewStyleVectorCache vectors=newStyleVectors;
            if(result==null||vectors==null||vectors.source!=result)throw new IllegalStateException("Final art-style vectors are unavailable");
            AssemblyLayout layout=assemblyLayout(result.widthMm,result.heightMm,settings.registration,
                settings.markStyle,settings.markSizeMm,settings.registrationClearanceMm,settings.marks,
                settings.registration?SheetFrame.SPECIALTY_PADDING_MM:0f,settings.minimumMm,settings.circleCrop);
            GuideAssembler guide=new GuideAssembler(projectLabel,projectStyleName(),layout,paper,assemblyReferenceOnly,selectedPaletteKey);
            ArtworkDocumentExport.Page composite=ArtworkDocumentExport.newStyle(result,vectors,base,paper,layout.padMm,baseOnly,
                layout.registration,layout.marks,layout.markStyle,layout.markSizeMm,layout.minimumMm);
            if(base!=null){guide.add("Solid Snip",base.color,base.cutContours(),AssemblyGuidePlan.Kind.VINYL,
                "Bottom support silhouette.",ArtworkDocumentExport.newStyleLayer(result,vectors,base,paper,layout.padMm,-1,
                    layout.registration,layout.marks,layout.markStyle,layout.markSizeMm,layout.minimumMm));
                guide.standardMetrics(java.util.Collections.singletonList(base.shape));}
            if(!baseOnly){
                ArrayList<Integer> indices=new ArrayList<>();
                for(int i=0;i<result.layers.size();i++)if(!result.layers.get(i).loops.isEmpty()||!result.layers.get(i).lines.isEmpty())indices.add(i);
                indices.sort(Comparator.comparingInt(i->NewStyleColorSlots.slot(newStyleId,result.layers.get(i).name,i)));
                for(int index:indices){
                    StyleKit.Layer layer=result.layers.get(index);
                    AssemblyGuidePlan.Kind kind=layer.loops.isEmpty()?AssemblyGuidePlan.Kind.PEN_OR_ENGRAVE:AssemblyGuidePlan.Kind.VINYL;
                    String note=!layer.loops.isEmpty()&&!layer.lines.isEmpty()?layer.lines.size()+" open pen path(s) are included with the filled geometry.":"";
                    guide.add(nonemptyLayerName(layer.name,index+1),layer.color,layer.loops.size()+layer.lines.size(),kind,note,
                        ArtworkDocumentExport.newStyleLayer(result,vectors,base,paper,layout.padMm,index,
                            layout.registration,layout.marks,layout.markStyle,layout.markSizeMm,layout.minimumMm));
                    guide.pathMetrics(1.0,vectors.fills[index],vectors.strokes[index]);
                }
            }
            return guide.finish(composite);
        }
        SpecialtyVectorCache specialty=specialtyVectorCache;
        if(specialty!=null&&(specialty.source==tonalResult||specialty.source==layeredResult||
                specialty.source==tessellationResult||specialty.source==curveRibbonResult||specialty.source==spiralRibbonResult)){
            AssemblyLayout layout=specialtyAssemblyLayout(specialty);
            GuideAssembler guide=new GuideAssembler(projectLabel,projectStyleName(),layout,paper,assemblyReferenceOnly,selectedPaletteKey);
            ArtworkDocumentExport.Page composite=ArtworkDocumentExport.specialty(specialty,base,layout.widthMm,paper,
                layout.padMm,baseOnly,layout.registration,layout.marks,layout.markStyle,layout.markSizeMm,layout.minimumMm);
            if(base!=null){guide.add("Solid Snip",base.color,base.cutContours(),AssemblyGuidePlan.Kind.VINYL,
                "Bottom support silhouette.",ArtworkDocumentExport.specialtyLayer(specialty,base,layout.widthMm,paper,
                    layout.padMm,-1,layout.registration,layout.marks,layout.markStyle,layout.markSizeMm,layout.minimumMm));
                guide.standardMetrics(java.util.Collections.singletonList(base.shape));}
            if(!baseOnly)for(int index:specialty.stackOrder){
                if(index<0||index>=specialty.layers.length||specialty.layers[index].isEmpty())continue;
                guide.add(specialtyGuideName(specialty.source,index),specialty.colors[index],specialtyGuideCount(specialty.source,index),
                    AssemblyGuidePlan.Kind.VINYL,"",ArtworkDocumentExport.specialtyLayer(specialty,base,layout.widthMm,paper,
                        layout.padMm,index,layout.registration,layout.marks,layout.markStyle,layout.markSizeMm,layout.minimumMm));
                guide.pathMetrics(layout.widthMm/(double)specialty.width,specialty.layers[index]);
            }
            return guide.finish(composite);
        }
        if(stippleVectorCache!=null&&stippleVectorCache.source==tessellationResult){
            TessellationEngine.Result result=tessellationResult;StippleVectorCache vectors=stippleVectorCache;
            TessellationEngine.Options o=result.options;
            float height=o.widthMm*result.height/(float)Math.max(1,result.width);
            AssemblyLayout layout=assemblyLayout(o.widthMm,height,o.registration,o.markStyle,o.markSizeMm,
                o.registrationClearanceMm,o.marks,o.outerSpacingMm,o.minimumCutMm,o.circleCrop);
            int[] order=result.stipple==null?new int[result.options.layerCount]:result.stipple.options.order.clone();
            if(result.stipple==null)for(int i=0;i<order.length;i++)order[i]=i;
            GuideAssembler guide=new GuideAssembler(projectLabel,projectStyleName(),layout,paper,assemblyReferenceOnly,selectedPaletteKey);
            ArtworkDocumentExport.Page composite=ArtworkDocumentExport.stipple(vectors.layers,vectors.colors,order,
                result.width,result.height,base,layout.widthMm,paper,layout.padMm,baseOnly,layout.registration,
                layout.marks,layout.markStyle,layout.markSizeMm,layout.minimumMm);
            if(base!=null){guide.add("Solid Snip",base.color,base.cutContours(),AssemblyGuidePlan.Kind.VINYL,
                "Bottom support silhouette.",ArtworkDocumentExport.stippleLayer(vectors.layers,vectors.colors,result.width,
                    result.height,base,layout.widthMm,paper,layout.padMm,-1,layout.registration,layout.marks,
                    layout.markStyle,layout.markSizeMm,layout.minimumMm));
                guide.standardMetrics(java.util.Collections.singletonList(base.shape));}
            if(!baseOnly)for(int index:order){
                if(index<0||index>=vectors.layers.length||vectors.layers[index].isEmpty())continue;
                guide.add("Stipple Layer "+(index+1),vectors.colors[index],result.counts[index],AssemblyGuidePlan.Kind.VINYL,"",
                    ArtworkDocumentExport.stippleLayer(vectors.layers,vectors.colors,result.width,result.height,base,
                        layout.widthMm,paper,layout.padMm,index,layout.registration,layout.marks,layout.markStyle,
                        layout.markSizeMm,layout.minimumMm));
                guide.pathMetrics(layout.widthMm/(double)result.width,vectors.layers[index]);
            }
            return guide.finish(composite);
        }
        Path line;int sourceW,sourceH;float stroke;String name,note;
        if(mazeResult!=null){
            line=mazeResult.wallsPath;sourceW=sourceH=Math.max(1,Math.round(mazeResult.canvasSizePx));stroke=.25f;
            name="Maze Walls";note="The solution path remains a reference in the cut ZIP and is not an assembly layer.";
        }else{
            line=continuousPreview;Bitmap prepared=alternatePreparedSource==null?photo:alternatePreparedSource;
            if(line==null||prepared==null)throw new IllegalStateException("Final pen path is unavailable");
            sourceW=prepared.getWidth();sourceH=prepared.getHeight();stroke=continuousStrokeMm;
            name=continuousStyle==null?"Single Line":continuousStyle.name().replace('_',' ');
            note="Open centerline for pen or engraving; not a weedable vinyl silhouette.";
        }
        float width=settings.widthMm,height=width*sourceH/(float)sourceW;
        AssemblyLayout layout=assemblyLayout(width,height,false,settings.markStyle,0f,0f,null,0f,settings.minimumMm);
        GuideAssembler guide=new GuideAssembler(projectLabel,projectStyleName(),layout,paper,assemblyReferenceOnly,selectedPaletteKey);
        ArtworkDocumentExport.Page page=ArtworkDocumentExport.linePath(line,sourceW,sourceH,width,paper,0xff171717,stroke);
        guide.add(name,0xff171717,1,AssemblyGuidePlan.Kind.PEN_OR_ENGRAVE,note,page);
        guide.pathMetrics(width/(double)sourceW,line);
        return guide.finish(page);
    }
    private int pendingAssemblyGeneration=-1;
    private boolean pendingAssemblyAlternate=false;
    private void requestAssemblyGuide(){
        if(!snipDraft.isEmpty()||editingSnipIndex>=0){toast(getString(R.string.ui_finish_or_discard_the_active_snip));return;}
        if(photo==null){toast(getString(R.string.ui_import_a_photograph_first));return;}
        if(alternateActive()?!specialtyFinalReady():!standardFinalReady()){
            toast("Release sliders and finish the final render before exporting");return;
        }
        if(settings.registration){
            final String unsafe;
            try{RegistrationCutGeometry.Report report=activeMarkReport();
                unsafe=report==null?null:report.firstIssue();
            }catch(RuntimeException error){toast("Assembly guide: "+friendlyFailure(error,"Unable to prepare assembly guide"));return;}
            if(unsafe!=null){
                new AlertDialog.Builder(this).setTitle("Registration marks need attention")
                    .setMessage(unsafe+"\n\nA reference-only PDF can still be created. It will omit the unsafe marks and clearly state that diagrams are not to scale or suitable for cutting alignment.")
                    .setNegativeButton(getString(R.string.ui_adjust_marks),(dialog,which)->openWorkspaceSubtool(5,1))
                    .setPositiveButton("Reference PDF without marks",(dialog,which)->startAssemblyGuidePicker(true))
                    .show();return;
            }
        }
        startAssemblyGuidePicker(false);
    }
    private void startAssemblyGuidePicker(boolean reference){
        assemblyReferenceOnly=reference;
        pendingAssemblyAlternate=alternateActive();
        pendingAssemblyGeneration=pendingAssemblyAlternate?alternateGeneration:serial;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        String date=new java.text.SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date());
        String name=reference?"VinylStudio-reference-guide-":"VinylStudio-assembly-guide-";
        intent.putExtra(Intent.EXTRA_TITLE,name+date+".pdf");
        startActivityForResult(intent,EXPORT_ASSEMBLY_GUIDE);
    }
    private void exportAssemblyGuide(Uri uri){
        boolean alternate=alternateActive();int generation=alternate?alternateGeneration:serial;
        if(generation!=pendingAssemblyGeneration||alternate!=pendingAssemblyAlternate||
                (alternate?!specialtyFinalReady():!standardFinalReady())){
            toast(getString(R.string.ui_artwork_changed_while_choosing_a_file));return;
        }
        final AssemblyGuideDocument.Document guide;
        try{
            if(settings.registration&&!assemblyReferenceOnly){
                RegistrationCutGeometry.Report marks=activeMarkReport();
                if(marks!=null&&!marks.valid())throw new IllegalStateException(marks.firstIssue());
            }
            guide=buildAssemblyGuideDocument();
        }
        catch(RuntimeException error){toast("Assembly guide: "+friendlyFailure(error,"Unable to prepare assembly guide"));return;}
        final long reviewToken=projectReviewProgress.revision();
        toast("Preparing assembly guide PDF…");
        scheduleDiskJob(()->{
            try{
                if(Thread.currentThread().isInterrupted())return;
                try(StagedDocumentFile stage=new StagedDocumentFile(getCacheDir())){
                    try(OutputStream output=stage.openStream()){
                        AssemblyGuideDocument.writePdf(guide,output);
                    }
                    publishStagedDocument(stage,uri,StagedDocumentFile.Kind.PDF);
                }
                postUi(()->{projectReviewProgress.record(reviewToken,guide.plan.referenceOnly?ProjectReviewProgress.REFERENCE_PDF:ProjectReviewProgress.ASSEMBLY_PDF);toast(getString(R.string.ui_assembly_guide_pdf_exported));});
            }catch(OutOfMemoryError memory){
                android.util.Log.e("VinylAssemblyGuide","Assembly guide exceeded available memory",memory);
                postUi(()->toast(getString(R.string.ui_assembly_guide_needs_too_much_memory)));
            }catch(Exception error){
                android.util.Log.e("VinylAssemblyGuide","Assembly guide export failed",error);
                postUi(()->toast("Assembly guide failed: "+friendlyFailure(error,"Unable to create assembly guide")));
            }
        });
    }
    /** Request a color-composite artwork document, not a blade-cut file. */
    private int pendingDocumentGeneration=-1;
    private boolean pendingDocumentAlternate=false;
    private void requestArtworkDocument(boolean pdf){
        if(!snipDraft.isEmpty()||editingSnipIndex>=0){
            toast(getString(R.string.ui_finish_or_discard_the_active_snip));return;
        }
        if(photo==null){toast(getString(R.string.ui_import_a_photograph_first));return;}
        if(alternateActive()?!specialtyFinalReady():!standardFinalReady()){
            toast("Release sliders and finish the final render before exporting");return;
        }
        pendingDocumentAlternate=alternateActive();
        pendingDocumentGeneration=pendingDocumentAlternate?alternateGeneration:serial;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(pdf?"application/pdf":"image/png");
        String date=new java.text.SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE,"VinylStudio-artwork-"+date+(pdf?".pdf":".png"));
        startActivityForResult(intent,pdf?EXPORT_PDF:EXPORT_PNG);
    }
    private void exportArtworkDocument(Uri uri,boolean pdf){
        boolean alternate=alternateActive();
        int generation=alternate?alternateGeneration:serial;
        if(generation!=pendingDocumentGeneration||alternate!=pendingDocumentAlternate||
                (alternate?!specialtyFinalReady():!standardFinalReady())){
            toast(getString(R.string.ui_artwork_changed_while_choosing_a_file_327279));return;
        }
        final AutoSilhouetteUnderlay.Output base=currentUnderlay();
        final boolean baseOnly=silhouetteOnly();
        final int paper=settings.paper;
        final SpecialtyPreviewSettings presentation=captureSpecialtyPreviewSettings();
        final float mm=settings.widthMm;
        final ArtworkDocumentExport.Page page;
        final TessellationEngine.Result rasterTess;
        final MazePortraitEngine.Result rasterMaze;
        final Bitmap rasterPrepared;
        final Path rasterPath;
        final Bitmap rasterExisting;
        try {
            if(!alternate){
                page=ArtworkDocumentExport.standard(latest,base,baseOnly);
                rasterTess=null;rasterMaze=null;rasterPrepared=null;rasterPath=null;rasterExisting=null;
            }else if(newStyleActive()){
                final StyleKit.Result result=newStyleResult;
                final NewStyleVectorCache vectors=newStyleVectors;
                if(result==null||vectors==null||vectors.source!=result)
                    throw new IllegalStateException("Final art-style vectors are unavailable");
                float pad=Math.max(settings.registration?10f:0f,settings.outerSpacingMm);
                // Same mark centers as the suite cut sheets (ADR-0001), circle crop included.
                final float[][] marks=settings.registration?NewStyleExport.frame(result,settings.markSizeMm,
                    settings.registrationClearanceMm,settings.marks,settings.circleCrop).marks():null;
                if(marks!=null){
                    float radius=RegistrationMarks.markRadius(settings.markSizeMm,settings.registrationClearanceMm);
                    for(float[] mark:marks)pad=Math.max(pad,
                        Math.max(Math.max(-mark[0],mark[0]-result.widthMm),
                            Math.max(-mark[1],mark[1]-result.heightMm))+radius+.5f);
                }
                page=ArtworkDocumentExport.newStyle(result,vectors,base,paper,pad,baseOnly,
                    settings.registration,marks,settings.markStyle,settings.markSizeMm,settings.minimumMm);
                rasterTess=null;rasterMaze=null;rasterPrepared=null;rasterPath=null;rasterExisting=null;
            }else if(specialtyVectorCache!=null&&(
                    specialtyVectorCache.source==tonalResult||
                    specialtyVectorCache.source==layeredResult||
                    specialtyVectorCache.source==tessellationResult||
                    specialtyVectorCache.source==curveRibbonResult||
                    specialtyVectorCache.source==spiralRibbonResult)){
                page=ArtworkDocumentExport.specialty(specialtyVectorCache,base,mm,paper,
                    Math.max(0f,settings.outerSpacingMm),baseOnly);
                rasterTess=null;rasterMaze=null;rasterPrepared=null;rasterPath=null;rasterExisting=null;
            }else if(stippleVectorCache!=null&&stippleVectorCache.source==tessellationResult){
                final TessellationEngine.Result tess=tessellationResult;
                final StippleVectorCache vectors=stippleVectorCache;
                final int[] order=tess.stipple==null?new int[0]:tess.stipple.options.order.clone();
                page=ArtworkDocumentExport.stipple(vectors.layers,vectors.colors,order,
                    tess.width,tess.height,base,mm,paper,Math.max(0f,settings.outerSpacingMm),baseOnly);
                rasterTess=null;rasterMaze=null;rasterPrepared=null;rasterPath=null;rasterExisting=null;
            }else{
                page=null;
                rasterTess=tessellationResult;rasterMaze=mazeResult;
                rasterPrepared=alternatePreparedSource;rasterPath=continuousPreview==null?null:new Path(continuousPreview);
                rasterExisting=alternatePreviewBitmap;
                if(rasterTess==null&&rasterMaze==null&&rasterPath==null&&rasterExisting==null)
                    throw new IllegalStateException("This style has no completed composite image");
            }
        }catch(RuntimeException invalid){toast("Artwork export: "+friendlyFailure(invalid,"Artwork export settings are invalid"));return;}
        toast("Preparing "+(pdf?"PDF":"PNG")+" artwork…");
        scheduleDiskJob(()->{
            Bitmap generated=null;
            try {
                ArtworkDocumentExport.Page artwork=page;
                if(artwork==null){
                    // A raster fallback is used only for styles without a complete
                    // native vector cache (e.g., stipple and maze pen paths).
                    generated=rasterTess!=null?renderTessPreview(rasterTess,3000,-1,presentation):
                        rasterMaze!=null?renderAlternatePreviewBitmap(rasterPrepared,rasterPath,null,rasterMaze,3000,presentation):
                        rasterPath!=null?renderAlternatePreviewBitmap(rasterPrepared,rasterPath,null,null,3000,presentation):rasterExisting;
                    if(generated==null)throw new IOException("Final composite image unavailable");
                    artwork=ArtworkDocumentExport.image(generated,mm,
                        mm*generated.getHeight()/(float)generated.getWidth(),paper);
                }
                if(Thread.currentThread().isInterrupted())return;
                // Do not open the destination before a document is ready. A picker
                // URI is writable only through the Android content resolver.
                try(StagedDocumentFile stage=new StagedDocumentFile(getCacheDir())){
                    try(OutputStream output=stage.openStream()){
                        if(pdf)ArtworkDocumentExport.writePdf(artwork,output);
                        else ArtworkDocumentExport.writePng(artwork,output);
                    }
                    publishStagedDocument(stage,uri,
                        pdf?StagedDocumentFile.Kind.PDF:StagedDocumentFile.Kind.PNG);
                }
                postUi(()->toast((pdf?"PDF":"PNG")+" artwork exported"));
            }catch(OutOfMemoryError memory){
                android.util.Log.e("VinylArtworkExport","Artwork document exceeded available memory",memory);
                postUi(()->toast(getString(R.string.ui_artwork_export_needs_too_much_memory)));
            }catch(Exception error){
                android.util.Log.e("VinylArtworkExport","Artwork document failed",error);
                postUi(()->toast("Artwork export failed: "+friendlyFailure(error,"Unable to export artwork")));
            }finally{
                // Existing editor bitmaps belong to the project and must not be recycled.
                if(generated!=null&&generated!=rasterExisting&&generated!=rasterPrepared)generated.recycle();
            }
        });
    }

    /** The only visible Export command. Route by CURRENT style, not cached standard geometry. */
    private void requestExport(){
        if(!snipDraft.isEmpty()||editingSnipIndex>=0){
            new AlertDialog.Builder(this).setTitle("Unapplied snip")
                .setMessage(getString(R.string.ui_the_current_selection_has_not_been))
                .setNegativeButton("Cancel",null).setPositiveButton(getString(R.string.ui_finish_snip),(d,w)->setTool(8)).show();return;
        }
        if(photo==null){toast(getString(R.string.ui_import_a_photograph_first));return;}
        if(!OutputDeviceProfile.byId(outputDeviceId).svgCutZip){
            requestArtworkDocument(true);return;
        }
        if(alternateActive()){
            requestAlternateExport();
            return;
        }
        if(!standardFinalReady()){toast(getString(R.string.ui_wait_for_this_style_s_final));return;}
        String error=VinylEngine.validateExport(latest);
        if(error==null&&settings.registration){
            try{RegistrationCutGeometry.Report marks=activeMarkReport();
                if(marks!=null)error=marks.firstIssue();
            }catch(RuntimeException invalid){error=friendlyFailure(invalid,"Registration settings need attention");}
        }
        if(error!=null){new AlertDialog.Builder(this).setTitle(getString(R.string.ui_export_needs_attention)).setMessage(error+"\n\nCheck the Cuts panel. If a mark overlaps artwork, move it; if the layer has too many shapes, run Auto fit or increase spacing.").setPositiveButton("OK",null).show();return;}beginPreparedStandardCutZip();}
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req==CAPTURE_PHOTO){ // camera apps return no data Intent; the photo is in our capture file
            final Uri captured=pendingCameraCapture;pendingCameraCapture=null;
            if(result!=RESULT_OK||captured==null)return;
            if(ProjectSaveGate.busy()){toast(getString(R.string.ui_finish_the_current_project_save_before));return;}
            beginPhotoImport(captured);
            return;
        }
        if(result!=RESULT_OK||data==null||data.getData()==null){
            if(req==EXPORT_LIBRARY_ITEM)pendingLibraryExport.clear();
            if(req==EXPORT)discardPreparedStandardCutZip();
            if(req==ALTERNATE_EXPORT)discardPreparedSpecialtyCutZip();
            if(result==RESULT_OK&&(data==null||data.getData()==null))
                toast(getString(R.string.ui_the_selected_file_was_not_returned));
            return;
        }
        Uri uri=data.getData();
        if(!DocumentAccess.isContentUri(uri)) {
            if(req==EXPORT)discardPreparedStandardCutZip();
            if(req==ALTERNATE_EXPORT)discardPreparedSpecialtyCutZip();
            toast(getString(R.string.ui_select_a_document_using_the_android));return;
        }
        if(req==IMPORT){
            if(ProjectSaveGate.busy()){toast(getString(R.string.ui_finish_the_current_project_save_before));return;}
            beginPhotoImport(uri);
        }
        else if(req==EXPORT_PNG||req==EXPORT_PDF)exportArtworkDocument(uri,req==EXPORT_PDF);
        else if(req==EXPORT_ASSEMBLY_GUIDE)exportAssemblyGuide(uri);
        else if(req==EXPORT_CALIBRATION_SVG||req==EXPORT_CALIBRATION_DXF)exportMaterialCalibration(uri,req==EXPORT_CALIBRATION_DXF);
        else if(req==EXPORT)publishPreparedStandardCutZip(uri);else if(req==ALTERNATE_EXPORT)publishPreparedSpecialtyZip(uri);else if(req==SAVE_PROJECT){
            DocumentAccess.releaseUnusedProjectGrants(getContentResolver(),getPreferences(MODE_PRIVATE).getString("recentProjectUris",""));
            boolean persistable=DocumentAccess.persist(getContentResolver(),uri,data.getFlags(),true);
            writeProject(uri,persistable);
        }else if(req==OPEN_PROJECT){
            requestProjectSwitch(()->{
                cancelStartupRestore();
                DocumentAccess.releaseUnusedProjectGrants(getContentResolver(),getPreferences(MODE_PRIVATE).getString("recentProjectUris",""));
                if(DocumentAccess.persist(getContentResolver(),uri,data.getFlags(),false))rememberProject(uri);
                loadProject(uri);
            },"the imported project");
        }else if(req==EXPORT_LIBRARY_ITEM){
            String id=pendingLibraryExport.consume();
            if(id!=null)exportLibraryArchive(uri,id);
            else toast(getString(R.string.ui_the_selected_project_was_lost_during));
        }else if(req==EXPORT_LIBRARY_BACKUP){
            exportLibraryBackup(uri);
        }else if(req==EXPORT_ORIGINAL_CUTS){
            final String id=pendingOriginalCutsId;pendingOriginalCutsId=null;
            if(id==null){toast(getString(R.string.gallery_original_cuts_lost));return;}
            scheduleDiskJob(()->{
                Exception failure=null;
                try{
                    File cuts=projectLibrary.exportedCuts(id);
                    if(cuts==null)throw new IOException("No cut sheets were kept with this project");
                    try(InputStream in=new java.io.FileInputStream(cuts);OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
                        if(out==null)throw new IOException("Destination unavailable");
                        byte[] buf=new byte[32768];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
                    }
                }catch(IOException|RuntimeException e){failure=e;}
                final Exception error=failure;
                postUi(()->toast(error==null?getString(R.string.gallery_original_cuts_saved):
                    friendlyFailure(error,getString(R.string.gallery_original_cuts_failed))));
            });
        }else if(req==IMPORT_LIBRARY_BACKUP){
            importLibraryBackup(uri);
        }
    }
    private int photoPreviewLimit(int requested){
        android.app.ActivityManager am=(android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);
        int memoryClassMb=am==null?128:am.getMemoryClass();
        boolean lowRam=am!=null&&am.isLowRamDevice();
        return PhotoMemoryPolicy.previewLimit(requested,Runtime.getRuntime().maxMemory(),memoryClassMb,lowRam);
    }
    private Bitmap decodeImage(Uri uri,int bound) throws IOException{
        if(Build.VERSION.SDK_INT<28){
            String mime=getContentResolver().getType(uri);
            String end=uri.getLastPathSegment();
            String type=mime==null?"":mime.toLowerCase(Locale.ROOT);
            String filename=end==null?"":end.toLowerCase(Locale.ROOT);
            if(type.equals("image/heic")||type.equals("image/heif")||
                    filename.endsWith(".heic")||filename.endsWith(".heif"))
                throw new IOException("HEIC/HEIF photos need Android 9 or newer. Convert this photo to JPEG or PNG.");
        }
        if(bound<128)throw new IOException("Unsupported photo preview size");
        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        try(InputStream stream=getContentResolver().openInputStream(uri)){
            if(stream==null)throw new IOException("Unable to open image");
            BitmapFactory.decodeStream(stream,null,bounds);
        }
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("Image format not supported");
        // Decode with the largest power-of-two sample that still reaches the requested
        // working width, then perform one final accurate resize (4000 -> 2000 -> 1800).
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inSampleSize=PhotoImportSizing.sampleSize(bounds.outWidth,bounds.outHeight,bound);
        opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap b=null;
        try{
            try(InputStream stream=getContentResolver().openInputStream(uri)){
                if(stream==null)throw new IOException("Unable to open image data");
                b=BitmapFactory.decodeStream(stream,null,opts);
            }
            if(b==null)throw new IOException("Could not decode image");
            int orientation=1;
            try(InputStream stream=getContentResolver().openInputStream(uri)){
                if(stream!=null){
                    androidx.exifinterface.media.ExifInterface exif=new androidx.exifinterface.media.ExifInterface(stream);
                    orientation=exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,1);
                }
            }catch(IOException|RuntimeException e){
                android.util.Log.w("VinylStudio","EXIF orientation unavailable; importing unrotated image",e);
            }
            float[] transform=ExifOrientationTransform.matrix(orientation);
            if(transform!=null){
                Matrix matrix=new Matrix();matrix.setValues(transform);
                Bitmap changed=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),matrix,true);
                if(changed!=b){b.recycle();b=changed;}
            }
            int longest=Math.max(b.getWidth(),b.getHeight());
            if(longest>bound){
                int[] finalSize=PhotoImportSizing.finalSize(b.getWidth(),b.getHeight(),bound);
                Bitmap resized=Bitmap.createScaledBitmap(b,finalSize[0],finalSize[1],true);
                if(resized!=b){b.recycle();b=resized;}
            }
            return b;
        }catch(OutOfMemoryError memory){
            if(b!=null&&!b.isRecycled())b.recycle();
            throw new IOException("Photo exceeds available memory; try a smaller image",memory);
        }
    }
    /** One sheet includes the filled compound Snip outline and its registration marks. */
    private void exportSilhouetteOnly(Uri uri,AutoSilhouetteUnderlay.Output base){
        exportSilhouetteOnly(uri,base,false);
    }
    private void exportSilhouetteOnly(Uri uri,AutoSilhouetteUnderlay.Output base,boolean acknowledged){
        final int targetForThisExport=outputDeviceId;
        final String matForThisExport=selectedCricutMat();
        if(base==null){toast(getString(R.string.ui_finish_snip_render_before_exporting_l1));return;}
        if((alternateActive()?!specialtyFinalReady():!standardFinalReady())||base!=currentUnderlay()){
            toast(getString(R.string.ui_complete_the_final_render_before_exporting));return;
        }
        if(!acknowledged){
            new AlertDialog.Builder(this).setTitle(getString(R.string.ui_solid_base_cut_safety_review))
                .setMessage("The solid Snip contour and its interior holes are not fully " +
                    "qualified by the general cut-width analyzer. Inspect the cut path " +
                    "and test a small sample before cutting this sheet.")
                .setNegativeButton(getString(R.string.ui_cancel_export),(d,w)->{})
                .setPositiveButton(getString(R.string.ui_export_with_review),(d,w)->exportSilhouetteOnly(uri,base,true))
                .show();return;
        }
        if(matPreflightRefused())return;
        status.setText(getString(R.string.export_writing_status));
        preparingSpecialtyCutZip=true;
        final float minimumCutMm=settings.minimumMm,minimumGapMm=settings.bridgeMm;
        final int renderToken=alternateReadyGeneration;
        if(!scheduleDiskJob(()->{
            StagedCutZip stage=null;
            try{
                stage=new StagedCutZip(getCacheDir());
                try(OutputStream os=stage.openStream()){
                if(os==null)throw new IOException("No output stream");
                try(ZipOutputStream zip=new ZipOutputStream(os)){
                    final CutSheetZipWriter cuts=new CutSheetZipWriter(zip,targetForThisExport,minimumCutMm,
                        StyleCutGeometryPreflight.exportGapMm(minimumGapMm,minimumCutMm),acknowledged).selectMat(matForThisExport);
                    String svg=base.svg();CutSvgCompatibility.verify(svg,targetForThisExport);
                    cuts.put(autoBaseFilename(base),svg);
                    putZip(zip,"layer-manifest.csv","order,file,role,color_hex,filled_shapes,blade_contours\n"+
                        "1,"+cuts.fileName(autoBaseFilename(base))+",Solid Snip background,"+colorHex(base.color)+",1,"+base.cutContours()+"\n");
                    putZip(zip,"README.txt","ONE PHYSICAL VINYL SHEET: L1 filled Snip background.\n"+
                        "Color: "+colorHex(base.color)+". Interior Snip openings are cut holes.\n"+
                        "Registration geometry is included only if enabled in the project.\n"+
                        "Your other detail layers are preserved in the project but not exported in this mode.\n");
                    cuts.finish();writeOutputHandoff(zip,targetForThisExport);
                    zip.finish();
                }
                if(base!=currentUnderlay()||(alternateActive()?!specialtyFinalReady():!standardFinalReady()))
                    throw new IOException("Solid base changed before export publication");
                } // Close the staged stream before checking ZIP CRCs.
                stage.verify();
                final StagedCutZip verified=stage;stage=null;
                postUi(()->offerPreparedSpecialtyZip(verified,renderToken,targetForThisExport,base,"solid-base"));
            }catch(OutOfMemoryError oom){
                if(stage!=null)stage.close();
                postUi(()->{preparingSpecialtyCutZip=false;showStandardExportError("Insufficient memory preparing base ZIP");});
            }catch(Exception ex){
                if(stage!=null)stage.close();
                postUi(()->{preparingSpecialtyCutZip=false;showStandardExportError(friendlyFailure(ex,"Unable to prepare base sheet"));});
            }
        }))preparingSpecialtyCutZip=false;
    }
    /** Standard SVG ZIP export is prepared and CRC-verified BEFORE asking Android
     * to create a document. SAF creates the destination immediately when the
     * picker returns; a later safety failure used to leave a 0-byte ZIP.
     */
    private static final class PreparedStandardCutZip implements AutoCloseable {
        final StagedCutZip stage;
        final VinylEngine.Result result;
        final AutoSilhouetteUnderlay.Output base;
        final int deviceId, sheets, critical, narrow, unverified;
        final boolean baseOnly,diagnostic;
        /** Selected Cricut mat this ZIP was checked against (null for other cutters' legacy path). */
        final String mat;
        PreparedStandardCutZip(StagedCutZip stage,VinylEngine.Result result,
                AutoSilhouetteUnderlay.Output base,int deviceId,int sheets,
                int critical,int narrow,int unverified,boolean baseOnly,String mat){
            this.baseOnly=baseOnly;this.mat=mat;
            this.stage=stage;this.result=result;this.base=base;this.deviceId=deviceId;
            this.sheets=sheets;this.critical=critical;this.narrow=narrow;this.unverified=unverified;
            diagnostic=critical+narrow+unverified>0;
        }
        @Override public void close(){stage.close();}
    }
    // Accessed on the UI thread; the background worker retains its own stage
    // until a UI callback accepts ownership, so a failed preparation cannot
    // publish a document or race a second export.
    private PreparedStandardCutZip preparedStandardCutZip;
    private boolean preparingStandardCutZip;
    private void discardPreparedStandardCutZip(){
        PreparedStandardCutZip old=preparedStandardCutZip;
        preparedStandardCutZip=null;
        if(old!=null)old.close();
    }
    private boolean stillCurrent(PreparedStandardCutZip prep){
        return prep!=null&&prep.result==latest&&prep.base==currentUnderlay()&&
            prep.deviceId==outputDeviceId&&prep.baseOnly==(silhouetteOnly()&&prep.base!=null)&&
            java.util.Objects.equals(prep.mat,selectedCricutMat())&&
            standardFinalReady()&&!alternateActive();
    }
    private void showStandardExportError(String message){
        status.setText(R.string.status_export_failed_details);
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_svg_zip_export_failed))
            .setMessage(message+"\n\nNo cut file was published. The artwork and all settings remain unchanged.")
            .setPositiveButton("OK",null).show();
    }
    /** An ACTION_CREATE_DOCUMENT result is always a *new* document. Delete
     * only that result on failed publication; never touch an existing project.
     * Providers without delete support may still leave a placeholder.
     */
    private void discardFailedStandardDocument(Uri uri){
        if(uri==null)return;
        try{
            if(android.provider.DocumentsContract.isDocumentUri(this,uri)&&
                    !android.provider.DocumentsContract.deleteDocument(getContentResolver(),uri))
                android.util.Log.w("VinylCutExport","Provider refused to delete failed cut document");
        }catch(Exception unsupported){
            android.util.Log.w("VinylCutExport","Unable to delete failed cut document",unsupported);
        }
    }
    private void beginPreparedStandardCutZip(){
        if(preparingStandardCutZip){toast(getString(R.string.ui_cut_zip_preparation_is_already_running));return;}
        if(!standardFinalReady()||latest==null){toast(getString(R.string.ui_finish_rendering_before_export));return;}
        if(matPreflightRefused())return;
        discardPreparedStandardCutZip();
        preparingStandardCutZip=true;
        status.setText(R.string.status_checking_cut_sheets);
        final VinylEngine.Result captured=latest;
        final AutoSilhouetteUnderlay.Output base=currentUnderlay();
        final int device=outputDeviceId;
        final String mat=selectedCricutMat();
        final boolean sourcePhoto=includeSourcePhotoInCutZip;
        final boolean baseOnly=silhouetteOnly()&&base!=null;
        final String projectSettingsSnapshot=toJson(captured.settings).toString();
        final Bitmap exportPhoto=originalPhoto==null?photo:originalPhoto;
        if(!scheduleCutPreparationJob(()->{
            PreparedStandardCutZip completed=null;Exception failure=null;
            try{completed=prepareStandardCutZip(captured,base,device,mat,sourcePhoto,exportPhoto,baseOnly,projectSettingsSnapshot);}
            catch(OutOfMemoryError oom){failure=new IOException("Insufficient memory preparing the export. Try disabling source photo inclusion.",oom);}
            catch(Exception ex){failure=ex;}
            final PreparedStandardCutZip ready=completed;
            final Exception problem=failure;
            if(!postUi(()->{
                preparingStandardCutZip=false;
                if(problem!=null){
                    android.util.Log.e("VinylCutExport","Standard cut ZIP preparation failed",problem);
                    showStandardExportError(friendlyFailure(problem,"Unable to prepare cut ZIP"));
                    return;
                }
                if(!stillCurrent(ready)){
                    ready.close();toast(getString(R.string.ui_artwork_changed_while_preparing_export_export));return;
                }
                preparedStandardCutZip=ready;
                if(ready.diagnostic){
                    String reason="Cut inspection found "+ready.critical+" contacts/invalid contours, "+
                        ready.narrow+" narrow features and "+ready.unverified+" incomplete checks.\n\n"+
                        "This ZIP is for INSPECTION ONLY; do not send it to a cutter. " +
                        "The ZIP contains cut-sheet-safety.csv and NOT-CUT-QUALIFIED.txt.";
                    new AlertDialog.Builder(this).setTitle(getString(R.string.ui_cut_geometry_needs_review))
                        .setMessage(reason)
                        .setNegativeButton("Cancel",(d,w)->discardPreparedStandardCutZip())
                        .setOnCancelListener(d->discardPreparedStandardCutZip())
                        .setPositiveButton(getString(R.string.ui_save_inspection_zip),(d,w)->openPreparedStandardCutPicker(ready))
                        .show();
                }else openPreparedStandardCutPicker(ready);
            })){
                if(ready!=null)ready.close();
            }
        })){
            preparingStandardCutZip=false;
        }
    }
    private void openPreparedStandardCutPicker(PreparedStandardCutZip ready){
        if(!stillCurrent(ready)||ready!=preparedStandardCutZip){
            discardPreparedStandardCutZip();toast(getString(R.string.ui_artwork_changed_before_save_export_again));return;
        }
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_TITLE,"VinylStudio-cut-layers-"+
            new java.text.SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date())+".zip");
        try{startActivityForResult(i,EXPORT);}
        catch(RuntimeException pickerFailure){
            discardPreparedStandardCutZip();
            showStandardExportError(friendlyFailure(pickerFailure,"Android document picker is unavailable"));
        }
    }
    private void publishPreparedStandardCutZip(Uri uri){
        final PreparedStandardCutZip ready=preparedStandardCutZip;
        preparedStandardCutZip=null;
        if(!stillCurrent(ready)){
            if(ready!=null)ready.close();
            discardFailedStandardDocument(uri);
            showStandardExportError("Artwork or cutting settings changed. Reopen Export and save again.");
            return;
        }
        final long reviewToken=projectReviewProgress.revision();
        status.setText(R.string.status_writing_verified_zip);
        if(!scheduleDiskJob(()->{
            Exception failure=null;
            try{
                if(!stillCurrent(ready))throw new IOException("Artwork changed before ZIP publication");
                publishStagedCutZip(ready.stage,uri);
            }catch(Exception ex){failure=ex;discardFailedStandardDocument(uri);}
            finally{ready.close();}
            final Exception error=failure;
            postUi(()->{
                if(error!=null){
                    android.util.Log.e("VinylCutExport","SAF cut ZIP publication failed",error);
                    showStandardExportError(friendlyFailure(error,"Unable to write cut ZIP")+
                        "\nThe newly-created document was deleted where supported by Android.");
                }else{
                    projectReviewProgress.record(reviewToken,ready.diagnostic?ProjectReviewProgress.DIAGNOSTIC_ZIP:ProjectReviewProgress.CUT_ZIP);
                    status.setText(ready.diagnostic?"Diagnostic ZIP saved · NOT CUT QUALIFIED":
                        "Export complete · "+ready.sheets+" SVG/DXF sheets");
                    toast(ready.diagnostic?"Inspection ZIP written · NOT CUT QUALIFIED":"SVG/DXF ZIP written");
                    if(!ready.diagnostic)productFlows.offerShareCutSheets(uri);
                    productFlows.rememberVerifiedExport(uri,!ready.diagnostic&&projectReviewProgress.revision()==reviewToken);
                }
            });
        })){
            ready.close();discardFailedStandardDocument(uri);
        }
    }
    /** Heavy geometry scan and ZIP creation must finish before SAF picker.
     * On any failure no new user-facing document exists at all.
     */
    private PreparedStandardCutZip prepareStandardCutZip(VinylEngine.Result result,
            AutoSilhouetteUnderlay.Output base,int target,String mat,boolean includeSource,Bitmap photoForZip,
            boolean baseOnly,String projectSettingsSnapshot) throws IOException {
        StagedCutZip stage=new StagedCutZip(getCacheDir());
        try{
            java.util.List<StyleCutGeometryPreflight.Warning> warnings=baseOnly?
                java.util.Collections.emptyList():
                StandardCutSafety.inspect(result,Math.max(.2f,result.settings.minimumMm),
                    StyleCutGeometryPreflight.exportGapMm(result.settings.bridgeMm,result.settings.minimumMm),500);
            CutSafetyReviewPolicy.Decision review=CutSafetyReviewPolicy.assess(warnings,500);
            LineCutDiagnostics.Report lineReview=baseOnly?null:LineCutDiagnostics.inspect(result);
            if(!baseOnly&&!lineReview.complete)
                throw new IOException("Line intersection inspection exceeded its budget; increase spacing or reduce detail");
            if(!baseOnly&&lineReview.selfIntersections>0)
                throw new IOException("Line ribbons contain "+lineReview.selfIntersections+" crossings; increase spacing or simplify detail");
            int sheets=0,critical=review.critical,narrow=review.narrow,unverified=review.unverified;
            try(OutputStream os=stage.openStream();ZipOutputStream zip=new ZipOutputStream(os)){
                CutSheetZipWriter cuts=new CutSheetZipWriter(zip,target,result.settings.minimumMm,
                    StyleCutGeometryPreflight.exportGapMm(result.settings.bridgeMm,result.settings.minimumMm),true).selectMat(mat);
                cuts.noteSourceFindings(review.total()>0);
                ExportBounds.requireLayers(result.settings.layers.size());
                if(baseOnly){
                    String svg=base.svg();CutSvgCompatibility.verify(svg,target);
                    cuts.put(autoBaseFilename(base),svg);sheets++;
                    putZip(zip,"layer-manifest.csv","order,file,role,color_hex,filled_shapes,blade_contours\n"+
                        "1,"+cuts.fileName(autoBaseFilename(base))+",Solid Snip background,"+
                        colorHex(base.color)+",1,"+base.cutContours()+"\n");
                    putZip(zip,"README.txt","ONE PHYSICAL VINYL SHEET: L1 solid Snip background.\n"+
                        "Snip holes remain cut openings; registration is included if enabled.\n");
                }else{
                if(base!=null){
                    String svg=base.svg();CutSvgCompatibility.verify(svg,target);
                    cuts.put(autoBaseFilename(base),svg);sheets++;
                }
                int nextSheet=base==null?1:2;
                for(int i=0;i<result.settings.layers.size();i++){
                    if(!VinylEngine.cutLayer(result,i))continue;
                    VinylEngine.Layer layer=result.settings.layers.get(i);
                    String name=String.format(Locale.US,"%02d-vinyl-%06x.svg",nextSheet++,layer.color&0xffffff);
                    String svg=VinylEngine.svg(result,i);
                    CutSvgCompatibility.verify(svg,target);cuts.put(name,svg);sheets++;
                }
                if(sheets==0)throw new IOException("No included cut sheets; restore at least one sheet in Layers");
                putJpeg(zip,"whole-image-preview.jpg",renderStandardCompositePreview(result,1600,base,baseOnly));
                if(includeSource)putJpeg(zip,"source-photo.jpg",photoForZip);
                putZip(zip,"layer-manifest.csv",cuts.manifest(standardLayerManifest(result,base)));
                putZip(zip,"material-estimate.txt",materialEstimate(result.settings.widthMm,result.heightMm,sheets));
                putZip(zip,"assembly-instructions.txt",VinylEngine.productionSummary(result));
                putZip(zip,"cut-report.txt",VinylEngine.report(result));
                if(lineReview.ribbons>0)
                    putZip(zip,"line-cut-diagnostics.txt",lineReview.text(result.settings.minimumMm,result.settings.bridgeMm));
                VinylDiagnostics.Report visible=VinylDiagnostics.analyze(result,96);
                StringBuilder note=new StringBuilder("OPAQUE VINYL STACK VISIBILITY · SAMPLED ESTIMATES\n");
                note.append("Coverage is a percent of the image artboard (excluding registration marks).\n");
                note.append("Visible is the remaining share after later-applied opaque vinyl covers earlier layers.\n");
                note.append("Hidden is the percentage of each layer's own cut area obscured by later layers.\n");
                for(int li=0;li<visible.layers.length;li++){
                    VinylDiagnostics.LayerStats ls=visible.layers[li];
                    note.append(String.format(Locale.US,
                        "%02d %s · paths=%d · coverage=%.1f%% · visible=%.1f%% · hidden=%.0f%%\n",
                        li+1,result.settings.layers.get(li).name,ls.pathCount,ls.coveredPct,ls.visiblePct,ls.hiddenFractionPct));
                }
                putZip(zip,"vinyl-visibility.txt",note.toString());
                putZip(zip,"project-settings.json",projectSettingsSnapshot);
                StringBuilder safetyCsv=new StringBuilder("physical_layer,x_mm,y_mm,warning\n");
                for(StyleCutGeometryPreflight.Warning warning:warnings)
                    safetyCsv.append(warning.layer+1+(base==null?0:1)).append(',').append(warning.xMm)
                        .append(',').append(warning.yMm).append(',').append(warning.reason).append('\n');
                if(warnings.size()>=500)
                    safetyCsv.append("0,0,0,Warning report limit reached; remaining cut safety not verified\n");
                putZip(zip,"cut-safety-review.csv",safetyCsv.toString());
                putZip(zip,"README.txt",OutputDeviceProfile.handoffText(target));
                } // end complete multi-sheet export
                critical+=cuts.criticalFindingCount();
                narrow+=cuts.narrowFindingCount();
                unverified+=cuts.unknownFindingCount();
                cuts.finish();writeOutputHandoff(zip,target);zip.finish();
            }
            stage.verify();
            return new PreparedStandardCutZip(stage,result,base,target,sheets,critical,narrow,unverified,baseOnly,mat);
        }catch(IOException|RuntimeException|Error ex){stage.close();throw ex;}
    }
    private void writeProject(Uri uri,boolean persistable){
        if(photo==null){toast(getString(R.string.import_image_first));return;}
        if(!snipDraft.isEmpty()||editingSnipIndex>=0){toast(getString(R.string.ui_finish_the_current_snip_before_exporting));return;}
        final Bitmap bitmap=photo,full=originalPhoto==null?photo:originalPhoto;
        final byte[] originalSourceSnap=restoredOriginalSource;
        final File nativeSourceFile=NativeOriginalPhoto.active(getFilesDir());
        if(nativeSourceTooLargeForPortableZip(nativeSourceFile,originalSourceSnap))
            toast(getString(R.string.ui_camera_native_source_exceeds_20_mb));
        final boolean requireArchiveOriginal=encodedProjectOriginal;
        final String snap;
        try{snap=toJson(settings).toString();}
        catch(RuntimeException|OutOfMemoryError error){reportProjectCaptureFailure(error);return;}
        if(!ProjectSaveGate.begin()){toast(getString(R.string.ui_finish_the_current_project_save_first));return;}
        if(!projectSession.beginSave()){ProjectSaveGate.end();toast(getString(R.string.ui_project_save_already_running));return;}
        if(!scheduleDiskJob(()->{
            File stage=null;
            try{
                // Validate a completed archive before touching the SAF document. SAF does not
                // provide a general atomic replace operation; a failed final copy can still
                // leave a partial destination, unlike the app's internal project library.
                // Source fidelity gate: never fall back to a preview if the
                // verified encoded original disappeared or is ambiguous.
                final File verifiedOriginal=ProjectEncodedOriginal.requireSelected(
                    getFilesDir(),requireArchiveOriginal);
                stage=File.createTempFile("vinyl-project-",".zip",getCacheDir());
                try(ProjectSourceSnapshot sources=ProjectSourceSnapshot.capture(
                        getCacheDir(),verifiedOriginal,
                        nativeSourceFile!=null&&nativeSourceFile.isFile()&&
                        nativeSourceFile.length()<=ProjectArchiveReader.MAX_SOURCE_ENTRY?nativeSourceFile:null);
                    OutputStream fileOut=new BufferedOutputStream(new FileOutputStream(stage));
                    ZipOutputStream zip=new ZipOutputStream(fileOut)){
                    StudioProjectArchiveWriter.write(zip,
                        stream->bitmap.compress(Bitmap.CompressFormat.PNG,100,stream),
                        stream->full.compress(Bitmap.CompressFormat.PNG,100,stream),snap,
                        portableOriginalSupplier(sources.nativeOriginal,originalSourceSnap),sources.archiveOriginal);
                }
                StudioProjectLibrary.verifyProjectArchive(stage);
                try(InputStream ready=new BufferedInputStream(new FileInputStream(stage));
                    OutputStream target=getContentResolver().openOutputStream(uri,"wt")){
                    if(target==null)throw new IOException("Selected document cannot be written");
                    byte[] buffer=new byte[32768];int n;
                    while((n=ready.read(buffer))!=-1)target.write(buffer,0,n);
                    target.flush();
                }
                postUi(()->{if(persistable)rememberProject(uri);toast(getString(R.string.project_written));});
            }catch(OutOfMemoryError oom){
                android.util.Log.e("VinylStudio","Insufficient memory during project save",oom);
                postUi(()->toast(getString(R.string.ui_project_save_failed_insufficient_memory)));
            }catch(Exception e){
                android.util.Log.e("VinylStudio","Project save failed",e);
                postUi(()->toast("Project save failed: "+friendlyFailure(e,"Unable to save project")));
            }finally{
                projectSession.failSave();ProjectSaveGate.end();
                if(stage!=null&&!stage.delete())android.util.Log.w("VinylStudio","Could not delete temporary project archive");
                if(persistable)postUi(()->DocumentAccess.releaseUnusedProjectGrants(getContentResolver(),
                    getPreferences(MODE_PRIVATE).getString("recentProjectUris","")));
            }
        })){projectSession.failSave();ProjectSaveGate.end();}
    }
    /** If the camera's encoded source is under 20 MB, preserve it verbatim in
     * editable project ZIPs. The project reader enforces the same cap.
     * External cutting exports intentionally do not call this method.
     */
    private static boolean nativeSourceTooLargeForPortableZip(File activeSource,byte[] restoredSource){
        return activeSource!=null&&activeSource.isFile()&&
            activeSource.length()>ProjectArchiveReader.MAX_SOURCE_ENTRY ||
            restoredSource!=null&&restoredSource.length>ProjectArchiveReader.MAX_SOURCE_ENTRY;
    }
    private static StudioProjectArchiveWriter.SourceSupplier portableOriginalSupplier(
            File activeSource,byte[] restoredSource){
        return ProjectSaveSupport.portableOriginal(activeSource,restoredSource);
    }
    private void reportProjectCaptureFailure(Throwable error){
        android.util.Log.e("VinylLibrary","Unable to capture project settings",error);
        toast(error instanceof OutOfMemoryError?"Project not saved: insufficient memory":
            "Project not saved: "+friendlyFailure(error,"Unable to capture project settings"));
    }
    private static Bitmap decodeProjectImage(byte[] data) throws IOException {
        return decodeProjectImage(data,0);
    }
    /** Bound the editing bitmap while retaining the full original for faithful
     * project resaves and source-photo export. Zero keeps native resolution.
     */
    private static Bitmap decodeProjectImage(byte[] data,int editorMaxSide) throws IOException {
        BitmapFactory.Options probe=new BitmapFactory.Options();probe.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(data,0,data.length,probe);
        if(probe.outWidth<=0||probe.outHeight<=0||probe.outWidth>6000||probe.outHeight>6000||
                (long)probe.outWidth*probe.outHeight>24_000_000L)
            throw new IOException("Project image exceeds supported dimensions (6000px / 24MP)");
        // Retain the full-resolution original for editable project saves. A cut-preview
        // bitmap may be downscaled later, but silently downscaling this image would
        // permanently destroy source detail when the user saves the project again.
        Bitmap result=null;
        try {
            BitmapFactory.Options options=new BitmapFactory.Options();
            if(editorMaxSide>0){
                int longest=Math.max(probe.outWidth,probe.outHeight),sample=1;
                while(longest/(sample*2)>=editorMaxSide)sample*=2;
                options.inSampleSize=sample;
            }
            result=BitmapFactory.decodeByteArray(data,0,data.length,options);
            if(result==null)throw new IOException("Invalid project image");
            if(editorMaxSide>0&&Math.max(result.getWidth(),result.getHeight())>editorMaxSide){
                float ratio=editorMaxSide/(float)Math.max(result.getWidth(),result.getHeight());
                Bitmap smaller=Bitmap.createScaledBitmap(result,Math.max(1,Math.round(result.getWidth()*ratio)),
                    Math.max(1,Math.round(result.getHeight()*ratio)),true);
                if(smaller!=result){result.recycle();result=smaller;}
            }
            return result;
        }catch(OutOfMemoryError oom){
            if(result!=null&&!result.isRecycled())result.recycle();
            throw new IOException("Not enough memory for this project photograph",oom);
        }
    }
    /** v3 disk-backed archive preview: decode bounds first, never retain 32 MB
     * compressed project-image arrays alongside two decoded bitmaps.
     */
    private static Bitmap decodeProjectImage(File file,int editorMaxSide) throws IOException {
        if(file==null||!file.isFile())throw new IOException("Project image missing");
        BitmapFactory.Options probe=new BitmapFactory.Options();probe.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getAbsolutePath(),probe);
        if(probe.outWidth<=0||probe.outHeight<=0||probe.outWidth>6000||probe.outHeight>6000||
                (long)probe.outWidth*probe.outHeight>24_000_000L)
            throw new IOException("Project image exceeds supported dimensions (6000px / 24MP)");
        Bitmap result=null;
        try{
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inScaled=false;
            options.inPreferredConfig=Bitmap.Config.ARGB_8888;
            PhotoDecodeBudget.Plan plan=editorMaxSide>0?
                PhotoDecodeBudget.plan(probe.outWidth,probe.outHeight,editorMaxSide,
                    Math.min(4_500_000L,(long)editorMaxSide*editorMaxSide)):null;
            if(plan!=null)options.inSampleSize=plan.sampleSize;
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Project open canceled");
            result=BitmapFactory.decodeFile(file.getAbsolutePath(),options);
            if(result==null)throw new IOException("Unable to decode project image");
            if(plan!=null&&(result.getWidth()>plan.width||result.getHeight()>plan.height)){
                Bitmap scaled=Bitmap.createScaledBitmap(result,plan.width,plan.height,true);
                if(scaled!=result){result.recycle();result=scaled;}
            }
            return result;
        }catch(OutOfMemoryError oom){
            if(result!=null&&!result.isRecycled())result.recycle();
            throw new IOException("Insufficient memory to open project image",oom);
        }catch(RuntimeException failure){
            if(result!=null&&!result.isRecycled())result.recycle();
            throw failure;
        }
    }
    private static String stableDocumentKey(String value){
        try{byte[] bytes=java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder(64);
            for(byte b:bytes)hex.append(String.format(Locale.US,"%02x",b&255));
            return hex.toString();
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private void beginPhotoImport(Uri uri){
        final long token=projectSession.beginLoad(),revision=projectSession.revision();
        cancelStartupRestore();status.setText(R.string.quality_importing_photo_for_cropping);
        final ProjectLoadOwnership ownership=new ProjectLoadOwnership();
        pendingProjectLoads.add(ownership);
        if(!scheduleDiskJob(()->{
            boolean posted=false;
            try{
                OwnedSourceStage source=null;
                try{source=NativeOriginalPhoto.stage(getCacheDir(),getContentResolver(),uri);}
                catch(IOException cacheError){android.util.Log.w("VinylPerf","Native source cache unavailable",cacheError);}
                final OwnedSourceStage staged=source;
                if(staged!=null)ownership.own(staged::close);
                final Bitmap b=decodeImage(staged==null?uri:Uri.fromFile(staged.file),photoPreviewLimit(1800));
                ownership.own(()->{if(!b.isRecycled())b.recycle();});
                posted=postUi(()->{
                    if(!projectSession.isCurrentLoad(token)||projectSession.revision()!=revision||ownership.isCancelled()){
                        discardProjectLoad(ownership);return;
                    }
                    workflowStep=WorkflowFlow.CROP;selectedPhase=WorkflowFlow.PREPARE;
                    lastStepInPhase[WorkflowFlow.PREPARE]=WorkflowFlow.CROP;rebuildNavigation();
                    PhotoCropEditor.show(this,b,null,0,selection->{
                        if(!projectSession.isCurrentLoad(token)||projectSession.revision()!=revision||
                            ProjectSaveGate.busy()||projectSession.isSaving()||ownership.isCancelled()){
                            if(selection.bitmap!=b)selection.bitmap.recycle();
                            discardProjectLoad(ownership);toast(getString(R.string.ui_project_changed_while_importing_import_again));return;
                        }
                        try{
                            if(staged!=null)NativeOriginalPhoto.commit(getFilesDir(),staged);
                            else NativeOriginalPhoto.clear(getFilesDir());
                        }catch(IOException error){
                            // Never attach the previous photo's native bytes to a new photo.
                            if(selection.bitmap!=b)selection.bitmap.recycle();
                            discardProjectLoad(ownership);toast(getString(R.string.ui_original_source_could_not_be_committed));return;
                        }
                        if(!ownership.transferToUi())return;
                        pendingProjectLoads.remove(ownership);
                        includeSourcePhotoInCutZip=false;currentLibraryId=null;projectLabel="Photo project";
                        restoredOriginalSource=null;originalPhoto=b;settings.marks=null;undo.clear();redo.clear();
                        encodedProjectOriginal=false;projectSession.markEdited();
                        // Synchronous on purpose: must be durable before the encoded original is deleted.
                        //noinspection ApplySharedPref
                        getPreferences(MODE_PRIVATE).edit().putBoolean("encodedProjectOriginal",false).commit();
                        scheduleDiskJob(()->ProjectEncodedOriginal.clear(getFilesDir()));
                        float wi=VinylEngine.toInches(settings.widthMm);
                        float hi=wi*selection.bitmap.getHeight()/(float)selection.bitmap.getWidth();
                        SmartHalftonePresets.apply(settings,settings.pattern,chosenProfile,wi,hi);acceptCrop(selection);
                    },()->{
                        discardProjectLoad(ownership);
                        status.setText(photo==null?"Select a photo to begin":"Import canceled · previous artwork preserved");
                        if(photo==null)setWorkflowStep(0);
                    });
                });
            }catch(java.util.concurrent.CancellationException canceled){/* lease owns cleanup */}
            catch(Exception|OutOfMemoryError error){
                postUi(()->{if(projectSession.isCurrentLoad(token))toast("Import: "+friendlyFailure(error,"Unable to import photograph"));});
            }finally{
                ownership.workerFinished(posted);
                if(!posted||ownership.isCancelled())pendingProjectLoads.remove(ownership);
            }
        }))discardProjectLoad(ownership);
    }
    private InputStream requireProjectStream(Uri uri) throws IOException {
        InputStream input=getContentResolver().openInputStream(uri);
        if(input==null)throw new IOException("Project document could not be opened");
        return input;
    }
    private void loadProject(Uri uri){loadProject(uri,null,null);}
    private void loadProject(Uri uri,String savedId,String savedName){
        if(ProjectSaveGate.busy()){toast(getString(R.string.ui_project_save_is_in_progress_retry));return;}
        cancelStartupRestore();
        final long loadToken=projectSession.beginLoad();
        final long openedAtEditRevision=projectSession.revision();
        final int[] defaultProjectColors=tessColors.clone();
        final ProjectLoadOwnership ownership=new ProjectLoadOwnership();
        pendingProjectLoads.add(ownership);
        if(!scheduleDiskJob(()->{
        boolean posted=false;
        try{
        final OwnedSourceStage external=savedId==null?OwnedSourceStage.capture(getCacheDir(),requireProjectStream(uri),90_000_000L):null;
        if(external!=null)ownership.own(external::close);
        try(ZipInputStream zip=new ZipInputStream(external==null?projectLibrary.openArchive(savedId):external.open())){
        // v3: spool compressed images into short-lived, bounded private files.
        // The lease removes them on successful UI acceptance, cancellation,
        // exceptions and Activity destruction; full originals remain encoded.
        final ProjectArchiveReader.StagedProject project=ProjectArchiveReader.readToFiles(zip,getCacheDir());
        ownership.own(project::close);
        final boolean sameOriginal=project.original==null||
            ProjectImageFileCompare.equal(project.photo,project.original);
        final File originalEncoded=sameOriginal?project.photo:project.original;
        final String originalFormat=sameOriginal?project.photoFormat:project.originalFormat;
        final byte[] settingsBytes=project.settings;
        final int previewMax=photoPreviewLimit(2400);
        final ProjectEncodedOriginal.Stage encodedStage=ProjectEncodedOriginal.stage(
            getCacheDir(),originalEncoded,originalFormat);
        ownership.own(encodedStage::discard);
        final Bitmap full=decodeProjectImage(originalEncoded,previewMax);
        ownership.own(()->{if(!full.isRecycled())full.recycle();});
        final Bitmap bitmap=sameOriginal?full:decodeProjectImage(project.photo,previewMax);
        if(bitmap!=full)ownership.own(()->{if(!bitmap.isRecycled())bitmap.recycle();});
        // The decoded bitmaps and encoded-original stage now own all needed bytes.
        // A successful transferToUi() intentionally clears lease disposers,
        // so remove these temporary ZIP-image files BEFORE transferring ownership.
        project.close();
        if(ownership.isCancelled())return;
        // Both are still editable. Only the working photo/pixel array is bounded;
        // project-source fidelity stays at the original stored resolution.
        // Pixel-copy can allocate many megabytes. Prepare on the I/O worker,
        // never block the main thread while opening an older high-resolution ZIP.
        final int[] projectPixels=new int[bitmap.getWidth()*bitmap.getHeight()];
        bitmap.getPixels(projectPixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
        final byte[] nativeSource=project.nativeSource;
        OwnedSourceStage nativeTemp=null;
        if(nativeSource!=null&&nativeSource.length>0){
            try{nativeTemp=OwnedSourceStage.capture(getCacheDir(),new ByteArrayInputStream(nativeSource),ProjectArchiveReader.MAX_SOURCE_ENTRY);}
            catch(IOException error){android.util.Log.w("VinylLibrary","Native source persistence unavailable",error);}
        }
        final OwnedSourceStage nativeStage=nativeTemp;
        if(nativeStage!=null)ownership.own(nativeStage::close);
        JSONObject projectJson=new JSONObject(new String(settingsBytes,StandardCharsets.UTF_8));
        final int savedStep=WorkflowFlow.clamp(projectJson.optInt("workflowStep",WorkflowFlow.LAYERS));
        ProjectOpenPreflight.validate(projectJson); // Pure validation on worker; never mutate Activity here.
        final int[] projectTonalOverrides=new int[8];JSONArray tonalSaved=projectJson.optJSONArray("tonalColorOverrides");
        if(tonalSaved!=null)for(int i=0;i<8&&i<tonalSaved.length();i++)projectTonalOverrides[i]=tonalSaved.optInt(i,0);
        final float projectStroke=(float)Math.max(.10,Math.min(1.50,projectJson.optDouble("singleLineStrokeMm",.35)));
        final boolean projectTess=projectJson.optBoolean("tessellationMode",false);
        final int projectStyle=Math.max(0,Math.min(TessellationEngine.STIPPLE,projectJson.optInt("tessellationStyle",0)));
        final int projectCount=Math.max(2,Math.min(8,projectJson.optInt("tessellationCount",5)));
        final int projectDetail=Math.max(10,Math.min(100,projectJson.optInt("tessellationDetail",52)));
        final float projectGap=(float)Math.max(.15,Math.min(1.2,projectJson.optDouble("tessellationGap",.36)));
        final float projectEdge=(float)Math.max(0,Math.min(1,projectJson.optDouble("tessellationEdge",.65)));
        final String projectPalette=projectJson.optString("tessellationPalette","Portrait");
        final int[] projectColors=defaultProjectColors.clone();JSONArray colorJson=projectJson.optJSONArray("tessellationColors");
        if(colorJson!=null)for(int j=0;j<Math.min(8,colorJson.length());j++)projectColors[j]=colorJson.optInt(j,projectColors[j]);
        final JSONArray baseJson=projectJson.optJSONArray("tessellationHueBaseColors");
        final int[] projectHueBase=projectColors.clone();
        if(baseJson!=null&&baseJson.length()==8)for(int j=0;j<8;j++)projectHueBase[j]=baseJson.optInt(j,projectHueBase[j]);
        final int projectHue=baseJson!=null&&baseJson.length()==8?
            PaletteHueShift.clampDegrees(projectJson.optInt("tessellationHueDegrees",0)):0;
        // Opening a pre-library ZIP imports its validated content into the gallery. Reopening
        // the same SAF document updates the same local item instead of creating duplicates.
        StudioProjectLibrary.Entry imported=null;
        String importWarning=null;
        if(savedId==null){
            if(ownership.isCancelled()||!projectSession.isCurrentLoad(loadToken))return;
            String uriKey="library-import-"+stableDocumentKey(uri.toString());
            String former=getPreferences(MODE_PRIVATE).getString(uriKey,
                getPreferences(MODE_PRIVATE).getString("library-import-"+
                    Integer.toHexString(uri.toString().hashCode()),null));
            String filename=externalProjectName(uri);
            try{
                byte[] thumb=ProjectSaveSupport.optionalThumbnail(()->StudioProjectThumbnails.encode(bitmap),null,
                    error->android.util.Log.w("VinylLibrary","Optional thumbnail unavailable",error));
                imported=projectLibrary.save(former,filename,projectStyleFromJson(projectJson),thumb,out->{
                    try(InputStream source=external.open()){
                        byte[] buffer=new byte[8192];int n;long copied=0;
                        while((n=source.read(buffer))!=-1){
                            copied+=n;if(copied>90_000_000L)throw new IOException("Project ZIP too large");
                            out.write(buffer,0,n);
                        }
                    }
                });
                getPreferences(MODE_PRIVATE).edit().putString(uriKey,imported.id).apply();
            }catch(Exception importError){
                android.util.Log.e("VinylLibrary","Project opened but library import failed",importError);
                importWarning=friendlyFailure(importError,"It opened, but could not be added to your project library");
            }
        }
        if(external!=null)external.close();
        final String openedId=savedId==null?(imported==null?null:imported.id):savedId;
        final String openedName=savedId==null?(imported==null?"Imported project":imported.name):savedName;
        final String warning=importWarning;
        posted=postUi(()->{
            if(ownership.isCancelled()||!projectSession.isCurrentLoad(loadToken)||ProjectSaveGate.busy()){
                // An older load must not change global original-cache files
                // while a queued export is capturing the previous project.
                discardProjectLoad(ownership);
                if(ProjectSaveGate.busy())toast(getString(R.string.ui_project_save_is_finishing_reopen_afterward));
                return;
            }
            if(projectSession.revision()!=openedAtEditRevision){
                discardProjectLoad(ownership);
                toast(getString(R.string.ui_project_was_modified_while_opening_open));return;
            }
            try{ProjectEncodedOriginal.commit(getFilesDir(),encodedStage);}
            catch(IOException cacheError){
                discardProjectLoad(ownership);
                toast(getString(R.string.ui_full_resolution_original_could_not_be));return;
            }
            // Decode project state ONLY on the UI thread and only once the accepted load
            // has passed cancellation, edit-revision, save-busy, and archive validation.
            // Never deserialize into Activity fields on a worker thread.
            final VinylEngine.Settings acceptedSettings;
            try{acceptedSettings=fromJson(projectJson);}
            catch(Exception invalid){
                android.util.Log.e("VinylProject","Validated project could not be applied",invalid);
                discardProjectLoad(ownership);
                toast(getString(R.string.ui_project_settings_could_not_be_restored));return;
            }
            if(!ownership.transferToUi()){
                discardProjectLoad(ownership);return;
            }
            pendingProjectLoads.remove(ownership);
            includeSourcePhotoInCutZip=false; // never import sharing consent from project files
            encodedProjectOriginal=true;
            // Synchronous on purpose: must be durable before save gates read it.
            //noinspection ApplySharedPref
            getPreferences(MODE_PRIVATE).edit().putBoolean("encodedProjectOriginal",true).commit();
            currentLibraryId=openedId;projectLabel=openedName;
            NativeOriginalPhoto.clear(getFilesDir());restoredOriginalSource=nativeSource;
            if(nativeStage!=null){
                try{NativeOriginalPhoto.commit(getFilesDir(),nativeStage);restoredOriginalSource=null;}
                catch(IOException error){android.util.Log.w("VinylLibrary","Native source retained in memory only",error);}
                finally{nativeStage.close();}
            }
            photo=bitmap;originalPhoto=full==null?bitmap:full;
            imageW=bitmap.getWidth();imageH=bitmap.getHeight();pixels=projectPixels;settings=acceptedSettings;
            // A restored Photo-derived Stipple palette is already authoritative.
            // Re-derivation here used to destroy its saved Hue and Reverse state.
            skipNextPhotoPaletteSuggestion=true;changedPhoto();
            restoreParkedVinylLayers(projectJson);restoreArtworkModes(projectJson);tessellationResult=null;stippleVectorCache=null;tessellationStyle=projectStyle;tessLayerCount=projectCount;tessDetail=projectDetail;
            tessGapMm=projectGap;tessEdge=projectEdge;tessColors=projectColors;
            invalidateStipplePhotoPaletteRequest();
            tessHueBaseColors=projectHueBase;tessHueDegrees=projectHue;
            tessPalette=projectPalette;stippleOrderNormalize();tessSelectedLayer=-1;layeredResult=null;latest=null;canvasGeometry=null;latestDiagnostics=null;tonalPreview=null;preparedToneCache=null;undo.clear();redo.clear();snipDraft.clear();snipClosed=false;editingSnipIndex=-1;editingOriginal=null;snipTimeline.clear();chosen=-1;rebuildLayers();setWorkflowStep(savedStep);requestRender();projectSession.setDirty(false);saveLocalPhoto();updateEditorSubtitle();
            if(warning!=null){projectSession.setDirty(true);updateEditorSubtitle();
                new AlertDialog.Builder(this).setTitle(getString(R.string.ui_project_opened_not_added_to_library))
                    .setMessage("The project is open for editing, but the gallery copy failed: "+warning+
                        "\n\nUse Save to library to retry, or export a ZIP backup.")
                    .setPositiveButton("OK",null).show();
            }else toast(getString(R.string.project_opened));
            if(newerProjectSchemaWarning)
                new AlertDialog.Builder(this).setTitle(getString(R.string.ui_newer_project_format))
                    .setMessage("This project was saved by a newer version of VinylStudio (schema "
                        +projectSourceSchema+"). Some settings may not be supported. Unknown fields are retained when saving, but check the artwork before exporting.")
                    .setPositiveButton("OK",null).show();
            else productFlows.maybeShowEngineChangeNotice(projectEngineBuild,currentLibraryId);});
        }
    }catch(java.util.concurrent.CancellationException cancelled){
        // Project was closed or this Activity was destroyed during decode.
    }catch(OutOfMemoryError oom){
        android.util.Log.e("VinylStudio","Project open exhausted available memory",oom);
        postUi(()->toast(getString(R.string.ui_open_failed_insufficient_image_memory)));
    }catch(Exception e){
        postUi(()->toast("Open failed: "+friendlyFailure(e,"Unable to open this project")));
    }finally{
        ownership.workerFinished(posted);
        if(!posted||ownership.isCancelled())pendingProjectLoads.remove(ownership);
    }}))discardProjectLoad(ownership);
    }
    private JSONObject toJson(VinylEngine.Settings s){JSONObject out;
        try{
        out=projectSourceJson==null?new JSONObject():new JSONObject(projectSourceJson.toString());
        out.put("schema",Math.max(ProjectSettingsMigration.CURRENT_SCHEMA,projectSourceSchema));out.put(EngineBuild.KEY,EngineBuild.CURRENT);out.put("autoSilhouetteColor",autoSilhouetteColor);out.put("autoSilhouetteColorManual",autoSilhouetteColorManual);out.put("silhouetteOnlyMode",silhouetteOnlyMode);out.put("workflowStep",workflowStep);out.put("workflowType",designType);out.put("sizeProfile",chosenProfile);out.put("widthMm",s.widthMm);out.put("marginMm",s.marginMm);out.put("outerSpacingMm",s.outerSpacingMm);out.put("minimumMm",s.minimumMm);out.put("bridgeMm",s.bridgeMm);out.put("registrationClearanceMm",s.registrationClearanceMm);out.put("mode",s.mode);out.put("stencilCleanup",s.stencilCleanup);out.put("stencilSmoothing",s.stencilSmoothing);out.put("stencilPaperBackground",s.stencilPaperBackground);out.put("dither",s.dither);out.put("pattern",s.pattern);out.put("paper",s.paper);out.put("separationBackground",s.separationBackground);out.put("markStyle",s.markStyle);out.put("circleCrop",s.circleCrop);if(s.cricutMat!=null)out.put("cricutMat",s.cricutMat);out.put("markLayout",s.markLayout);out.put("snipFillMode",s.snipFillMode);out.put("markSizeMm",s.markSizeMm);out.put("registration",s.registration);out.put("substrateAware",false);out.put("substrateTolerance",s.substrateTolerance);
        out.put("selectedPaletteKey",selectedPaletteKey);
        JSONObject reverseJson=new JSONObject();
        for(java.util.Map.Entry<String,Boolean> e:paletteReversed.entrySet())reverseJson.put(e.getKey(),e.getValue());
        out.put("paletteReversed",reverseJson);
        JSONArray photoJson=new JSONArray();if(preparedPhotoPalette!=null)for(int c:preparedPhotoPalette)photoJson.put(c);
        out.put("preparedPhotoPalette",photoJson);
        out.put("newStyleHueDegrees",newStyleHueDegrees);
        JSONArray newHueBase=new JSONArray();for(int c:newStyleHueBaseColors)newHueBase.put(c);
        out.put("newStyleHueBaseColors",newHueBase);
        out.put("newStylePaletteRanks",paletteRanksJson(newStylePaletteRanks));
        out.put("paletteId",s.paletteId);out.put("exposureStops",s.exposureStops);out.put("photoContrast",s.photoContrast);out.put("levelsBlack",s.levelsBlack);out.put("levelsWhite",s.levelsWhite);out.put("levelsGamma",s.levelsGamma);out.put("levelsShadows",s.levelsShadows);out.put("levelsHighlights",s.levelsHighlights);out.put("edgePriority",s.edgePriority);
        out.put("blackColorProtection",s.blackColorProtection);out.put("shadowColorRecovery",s.shadowColorRecovery);out.put("portraitMidtones",s.portraitMidtones);out.put("linkedControls",s.linkedControls);out.put("masterSpacingMm",s.masterSpacingMm);
        out.put("masterDensity",PercentControlLimits.dotCoverage(s.masterDensity));out.put("masterContrast",s.masterContrast);
        out.put("anglePreset",s.anglePreset);out.put("screenRotationDeg",s.screenRotationDeg);
        out.put("lineMaxFraction",s.lineMaxFraction);out.put("lineGamma",s.lineGamma);
        out.put("lineSmoothing",s.lineSmoothing);out.put("lineSampleMm",s.lineSampleMm);out.put("lineContinuity",s.lineContinuity);out.put("linePresenceRecovery",s.linePresenceRecovery);out.put("opaqueLineBalance",s.opaqueLineBalance);out.put("lineHighlightRetention",s.lineHighlightRetention);out.put("classicCmykLines",s.classicCmykLines);out.put("portraitBackgroundGuard",s.portraitBackgroundGuard);out.put("contourStrength",s.contourStrength);
        out.put("detailBoost",s.detailBoost);out.put("shapeDefinition",s.shapeDefinition);out.put("crossAngleDeg",s.crossAngleDeg);
        out.put("radialFocusX",s.radialFocusX);out.put("radialFocusY",s.radialFocusY);
        out.put("lineInvert",s.lineInvert);out.put("waveAmplitude",s.waveAmplitude);out.put("waveCyclesPerInch",s.waveCyclesPerInch);out.put("hatchThreshold",s.hatchThreshold);out.put("shapeScale",s.shapeScale);out.put("screenWords",s.screenWords);
        JSONArray snips=new JSONArray();if(s.snips!=null)for(SnipMask.Operation operation:s.snips.operations()){
            JSONObject item=new JSONObject();item.put("kind",operation.kind);JSONArray coords=new JSONArray();
            for(float value:operation.xy)coords.put(value);item.put("points",coords);snips.put(item);
        }out.put("snips",snips);
        JSONArray marks=new JSONArray();if(s.marks!=null)for(float[] p:s.marks)marks.put(new JSONArray().put(p[0]).put(p[1]));out.put("marks",marks);
        JSONArray previousLayers=out.optJSONArray("layers");
        JSONArray layers=new JSONArray();int layerIndex=0;
        for(VinylEngine.Layer l:s.layers){
            // Retain unrecognized per-layer JSON keys, too, not just top-level fields.
            JSONObject previous=previousLayers==null?null:previousLayers.optJSONObject(layerIndex++);
            JSONObject o=previous==null?new JSONObject():new JSONObject(previous.toString());
            o.put("name",l.name);o.put("color",l.color);o.put("cmykChannel",l.cmykChannel);o.put("silhouetteBase",l.silhouetteBase);o.put("autoDisabledForBase",l.autoDisabledForBase);o.put("enabled",l.enabled);o.put("excludeFromExport",l.excludeFromExport);o.put("angle",l.angle);o.put("spacing",l.spacing);o.put("density",l.density);o.put("contrast",l.contrast);o.put("strength",l.strength);o.put("lineStyleOverride",l.lineStyleOverride);o.put("lineWidthScale",l.lineWidthScale);
            o.put("lineGammaOverride",l.lineGammaOverride);o.put("angleFineTuneDeg",l.angleFineTuneDeg);JSONArray c=new JSONArray();for(float v:l.curve)c.put(v);o.put("curve",c);layers.put(o);}out.put("layers",layers);
        // Tessellation is a project-artwork mode, not a change to the original CMYK layers.
        JSONArray tonalOverrideJson=new JSONArray();for(int c:tonalColorOverrides)tonalOverrideJson.put(c);out.put("tonalColorOverrides",tonalOverrideJson);
        out.put("singleLineStrokeMm",continuousStrokeMm);
        out.put("curveDepth",curveDepth);out.put("curvePhotoInfluence",curvePhotoInfluence);
        out.put("curveToneMethod",curveToneMethod);out.put("curveToneContrast",curveToneContrast);
        out.put("curveShadowBoost",curveShadowBoost);out.put("curveHighlightProtection",curveHighlightProtection);
        out.put("curveEdgePriority",curveEdgePriority);
        out.put("curveCornerRounding",curveCornerRounding);out.put("curveRotationDegrees",curveRotationDegrees);
        out.put("curveVinylRibbon",curveVinylRibbon);out.put("curveRibbonWidthMm",curveRibbonWidthMm);
        out.put("curveMinFeatureMm",curveMinFeatureMm);out.put("curveMinGapMm",curveMinGapMm);
        out.put("popPanelsSettings",savePopPosterSettings(popOptions));
        out.put("posterStencilSettings",savePopPosterSettings(posterOptions));
        // New Poster projects source physical L1–L9 colors from newStyleColors.
        // Absence of this marker identifies legacy custom posterColors projects.
        out.put("posterUsesUnifiedPalette",true);
        out.put("newStyleId",newStyleId);out.put("newStyleDetail",newStyleDetail);
        out.put("newStyleVariant",newStyleVariant);out.put("newStyleColorCount",newStyleColorCount);
        out.put("newStyleTessMotif",newStyleTessMotif);
        out.put("newStyleLeadMm",newStyleLeadMm);out.put("newStyleWeightMm",newStyleWeightMm);
        out.put("newStyleSmoothing",newStyleSmoothing);
        out.put("prismaticSpacingMm",prismaticSpacingMm);
        out.put("trioSpacingMm",trioSpacingMm);out.put("trioOrganic",trioOrganic);
        out.put("prismaticIrregularity",prismaticIrregularity);
        out.put("prismaticRoundness",prismaticRoundness);
        out.put("prismaticFeature",prismaticFeature);
        out.put("prismaticTone",prismaticTone);
        out.put("newStyleMatchVinyl",newStyleMatchVinyl);out.put("newStyleWords",newStyleWords);
        rememberNewStylePalette();
        JSONArray newColors=new JSONArray();for(int c:newStyleColors)newColors.put(c);
        out.put("newStyleColors",newColors);
        JSONArray memories=new JSONArray();
        for(int i=0;i<VinylStyleSuite.NAMES.length;i++){
            JSONObject entry=new JSONObject();
            if(newStylePaletteMemory[i]!=null){
                JSONArray colors=new JSONArray();for(int c:newStylePaletteMemory[i])colors.put(c);
                entry.put("colors",colors);entry.put("count",newStyleCountMemory[i]);
                entry.put("paletteRanks",paletteRanksJson(SmartPaletteReduction.validRanks(newStyleRankMemory[i],9)));
                entry.put("manual",newStyleManualMemory[i]);
                entry.put("explicitPhotoAuto",newStylePhotoAutoMemory[i]);
                entry.put("detail",newStyleDetailMemory[i]);
                entry.put("variant",newStyleVariantMemory[i]);
                entry.put("weight",newStyleWeightMemory[i]);
                entry.put("smoothing",newStyleSmoothingMemory[i]);
                entry.put("lead",newStyleLeadMemory[i]);
                if(i>=VinylStyleSuite.CONFETTI&&i<=VinylStyleSuite.CONFETTI_FALLEN){
                    int n=i-VinylStyleSuite.CONFETTI;
                    entry.put("trioSpacingMm",trioSpacingMemory[n]);
                    entry.put("trioOrganic",trioOrganicMemory[n]);
                }
            }
            memories.put(entry);
        }
        out.put("newStylePaletteMemory",memories);
        out.put("continuousStyleName",continuousStyle==null?"":continuousStyle.name());
        out.put("tonalStencilMode",tonalStencilMode);out.put("tonalLayerCount",tonalLayerCount);
        out.put("tonalBlur",tonalBlur);out.put("tonalEdge",tonalEdge);
        out.put("tonalBaseSource",tonalBaseSource);out.put("tonalMatchMyVinyl",tonalMatchMyVinyl);out.put("tonalAutoWhiteBackground",tonalAutoWhiteBackground);
        out.put("tonalCutLayerPreview",tonalCutLayerPreview);out.put("tonalIncludeSourcePhoto",tonalIncludeSourcePhoto);
        JSONArray tonalDetailJson=new JSONArray();for(int v:tonalDetailByCount)tonalDetailJson.put(v);
        out.put("tonalDetailByCount",tonalDetailJson);
        out.put("continuousDensity",continuousDensity);
        out.put("mazePortraitMode",mazePortraitMode);out.put("mazeGrid",mazeGrid);
        out.put("mazeDifficulty",mazeDifficulty);
        out.put("tessellationMode",tessellationMode);out.put("tessellationStyle",tessellationStyle);
        out.put("stippleOverlap",stippleSettings.overlap);out.put("stippleAssignment",stippleSettings.assignment);
        out.put("stippleShape",stippleSettings.shape);
        out.put("stippleAdaptiveCoverage",stippleSettings.adaptiveCoverage);
        out.put("stippleTargetCoverage",stippleSettings.targetCoverage);
        out.put("stipplePointDensity",PercentControlLimits.pointDensity(stippleSettings.pointDensity));
        out.put("stippleDotSize",PercentControlLimits.dotSize(stippleSettings.dotSize));
        out.put("stippleOptimizePacking",stippleSettings.optimizePacking);
        out.put("stippleTrapMm",stippleSettings.trapMm);out.put("stippleCrossGapMm",stippleSettings.minGapMm);
        out.put("stippleSameSheetGapMm",stippleSettings.overprintGapMm);
        out.put("cutMaterialProfileId",cutMaterialProfileId);
        JSONArray sd=new JSONArray(),sm=new JSONArray(),se=new JSONArray(),sz=new JSONArray();
        for(int i=0;i<8;i++){sd.put(stippleSettings.density[i]);sm.put(stippleSettings.minDotMm[i]);se.put(stippleSettings.enabled[i]);sz.put(stippleSettings.order[i]);}
        out.put("stippleDensity",sd);out.put("stippleMinDotMm",sm);out.put("stippleEnabled",se);out.put("stippleOrder",sz);
        out.put("tessellationCount",tessLayerCount);out.put("tessellationDetail",tessDetail);
        out.put("tessellationGap",tessGapMm);out.put("tessellationEdge",tessEdge);
        out.put("layeredCellMode",layeredCellMode);out.put("offsetCellMode",layeredCellMode&&offsetCellMode);out.put("bottleCapMode",layeredCellMode&&bottleCapMode);out.put("fishScaleMode",layeredCellMode&&fishScaleMode);out.put("overprintCellKind",layeredCellMode?overprintCellKind:0);
        out.put("layeredColumns",layeredColumns);out.put("layeredMotifDepth",layeredMotifDepth);
        out.put("layeredFamily",layeredFamily);out.put("layeredPhotoDetail",layeredPhotoDetail);
        out.put("layeredArtDetail",layeredArtDetail);out.put("layeredSeamMm",layeredSeamMm);
        out.put("layeredMinFeatureMm",layeredMinFeatureMm);out.put("layeredBackground",layeredBackground);
        out.put("layeredSolidBase",offsetCellMode||layeredSolidBase);
        out.put("layeredExtraMotif",(bottleCapMode||fishScaleMode)&&layeredExtraMotif);
        JSONArray families=new JSONArray();for(int family:layeredLayerFamilies)families.put(family);
        out.put("layeredLayerFamilies",families);
        // Save dormant layers to preserve hand-edited colors across layer-count changes.
        JSONArray parkedJson=new JSONArray();for(VinylEngine.Layer old:parkedVinylLayers){
            JSONObject item=new JSONObject();item.put("name",old.name);item.put("color",old.color);
            item.put("cmykChannel",old.cmykChannel);item.put("enabled",old.enabled);item.put("excludeFromExport",old.excludeFromExport);
            item.put("angle",old.angle);item.put("spacing",old.spacing);item.put("density",old.density);
            item.put("contrast",old.contrast);item.put("strength",old.strength);
            item.put("lineStyleOverride",old.lineStyleOverride);item.put("lineWidthScale",old.lineWidthScale);
            item.put("lineGammaOverride",old.lineGammaOverride);item.put("angleFineTuneDeg",old.angleFineTuneDeg);
            JSONArray curve=new JSONArray();for(float f:old.curve)curve.put(f);item.put("curve",curve);
            parkedJson.put(item);
        }
        out.put("parkedVinylLayers",parkedJson);
        out.put("tessellationPalette",tessPalette);
        JSONArray colors=new JSONArray();for(int c:tessColors)colors.put(c);out.put("tessellationColors",colors);
        JSONArray hueBase=new JSONArray();for(int c:tessHueBaseColors)hueBase.put(c);
        out.put("tessellationHueBaseColors",hueBase);out.put("tessellationHueDegrees",tessHueDegrees);
        out.put("tessPaletteRanks",paletteRanksJson(tessPaletteRanks));
        ensureStandardHueBase();
        JSONArray standardHueBase=new JSONArray();for(int c:standardHueBaseColors)standardHueBase.put(c);
        out.put("standardHueBaseColors",standardHueBase);out.put("standardHueDegrees",standardHueDegrees);
        out.put("standardPaletteRanks",paletteRanksJson(standardPaletteRanks));
        out.put("tonalPaletteRanks",paletteRanksJson(tonalPaletteRanks));
        JSONArray accentHueBase=new JSONArray();for(int c:cmykAccentHueBaseColors)accentHueBase.put(c);
        out.put("cmykAccentHueBaseColors",accentHueBase);
        out.put("tonalHueDegrees",tonalHueDegrees);
        }catch(JSONException e){throw new IllegalStateException(e);}return out;}
    private static float finiteRange(double value,float min,float max,float fallback){
        return LoadValueConstraints.finiteRange(value,min,max,fallback);
    }
    /** Untrusted project layer controls must be finite before they reach geometry. */
    private static void normalizeLoadedLayer(VinylEngine.Layer layer){
        layer.angle=finiteRange(layer.angle,-360f,360f,45f);
        layer.spacing=finiteRange(layer.spacing,.1f,100f,3f);
        layer.density=finiteRange(layer.density,0f,2f,1f);
        layer.contrast=finiteRange(layer.contrast,.05f,6f,1f);
        layer.strength=finiteRange(layer.strength,0f,4f,1f);
        for(int c=0;c<layer.curve.length;c++)
            layer.curve[c]=finiteRange(layer.curve[c],0f,1f,c/4f);
    }
    private VinylEngine.Settings fromJson(JSONObject json) throws JSONException {
        projectSourceSchema=ProjectSettingsMigration.readSchema(json);
        newerProjectSchemaWarning=ProjectSettingsMigration.isFuture(json);
        projectEngineBuild=EngineBuild.normalize(json.optInt(EngineBuild.KEY,0));
        projectSourceJson=new JSONObject(json.toString());
        json=ProjectSettingsMigration.migrate(json);
        autoSilhouetteColor=json.optInt("autoSilhouetteColor",AutoSilhouetteUnderlay.DEFAULT_COLOR);
        autoSilhouetteColorManual=json.optBoolean("autoSilhouetteColorManual",
            json.has("autoSilhouetteColor")&&autoSilhouetteColor!=AutoSilhouetteUnderlay.DEFAULT_COLOR);
        silhouetteOnlyMode=json.optBoolean("silhouetteOnlyMode",false);chosenProfile=Math.max(0,Math.min(2,json.optInt("sizeProfile",VinylSizing.BALANCED)));VinylEngine.Settings s=VinylEngine.Settings.defaults();s.layers.clear();s.widthMm=finiteRange(json.optDouble("widthMm",152.4),10f,1000f,152.4f);s.marginMm=finiteRange(json.optDouble("marginMm",5),0f,s.widthMm*.45f,5f);s.outerSpacingMm=finiteRange(json.optDouble("outerSpacingMm",8),0f,60f,8f);s.minimumMm=finiteRange(json.optDouble("minimumMm",VinylEngine.MINIMUM_CUT_WIDTH_MM),.051f,20f,VinylEngine.MINIMUM_CUT_WIDTH_MM);s.bridgeMm=finiteRange(json.optDouble("bridgeMm",.5),0f,20f,.5f);s.registrationClearanceMm=finiteRange(json.optDouble("registrationClearanceMm",1),0f,20f,1f);s.mode=json.optInt("mode",VinylEngine.CMYK);if(s.mode<VinylEngine.SPOT||s.mode>VinylEngine.STENCIL)s.mode=VinylEngine.CMYK;s.stencilCleanup=Math.max(0,Math.min(3,json.optInt("stencilCleanup",1)));s.stencilSmoothing=finiteRange(json.optDouble("stencilSmoothing",.10f),0f,.25f,.10f);s.stencilPaperBackground=json.optBoolean("stencilPaperBackground",true);
        designType=json.optInt("workflowType",s.mode==VinylEngine.STENCIL?1:0);s.dither=Math.max(VinylEngine.NEAREST,Math.min(VinylEngine.FLOYD,json.optInt("dither",1)));s.pattern=Math.max(0,Math.min(VinylEngine.PATTERNS.length-1,json.optInt("pattern",VinylEngine.LINE)));s.paper=json.optInt("paper",0xfffaf9f6);
        // D5: projects saved before the mounting surface was decoupled separated against their
        // saved paper; inherit it so their cut geometry reproduces exactly on reopen.
        s.separationBackground=json.has("separationBackground")?json.optInt("separationBackground",s.paper):s.paper;s.circleCrop=json.optBoolean("circleCrop",false);s.cricutMat=CricutMatPreflight.normalizeMat(json.optString("cricutMat",null));s.markLayout=RegistrationMarks.normalizePlacement(json.optInt("markLayout",RegistrationMarks.PLACEMENT_CORNERS));s.snipFillMode=Math.max(0,Math.min(2,json.optInt("snipFillMode",0)));s.markStyle=Math.max(0,Math.min(6,json.optInt("markStyle",0)));s.markSizeMm=finiteRange(json.optDouble("markSizeMm",RegistrationMarks.DEFAULT_DIAMETER_MM),RegistrationMarks.MIN_DIAMETER_MM,RegistrationMarks.MAX_DIAMETER_MM,RegistrationMarks.DEFAULT_DIAMETER_MM);s.registration=json.optBoolean("registration",true);s.substrateAware=false;s.substrateTolerance=Math.max(0,Math.min(80,json.optInt("substrateTolerance",24)));
        s.paletteId=json.optInt("paletteId",-1);
        if(s.paletteId<0||s.paletteId>=VinylPalettes.NAMES.length)s.paletteId=-1;
        selectedPaletteKey=json.optString("selectedPaletteKey",s.mode==VinylEngine.CMYK?"cmyk":
            s.paletteId>=0?"preset:"+s.paletteId:"");
        paletteReversed.clear();JSONObject reversedJson=json.optJSONObject("paletteReversed");
        if(reversedJson!=null){java.util.Iterator<String> keys=reversedJson.keys();
            while(keys.hasNext()){String key=keys.next();paletteReversed.put(key,reversedJson.optBoolean(key,false));}}
        JSONArray savedPhotoPalette=json.optJSONArray("preparedPhotoPalette");
        if(savedPhotoPalette!=null&&savedPhotoPalette.length()==8){
            int[] copy=new int[8];for(int i=0;i<8;i++)copy[i]=savedPhotoPalette.optInt(i,0xff808080);
            preparedPhotoPalette=copy;
        }else preparedPhotoPalette=null;
        preparedPhotoPaletteKey="";
        JSONArray newHueJson=json.optJSONArray("newStyleHueBaseColors");
        if(newHueJson!=null&&newHueJson.length()>=8){
            newStyleHueBaseColors=new int[9];for(int i=0;i<9;i++)
                newStyleHueBaseColors[i]=newHueJson.optInt(i,newStyleColors[i]);
            newStyleHueDegrees=PaletteHueShift.clampDegrees(json.optInt("newStyleHueDegrees",0));
        }else{newStyleHueBaseColors=newStyleColors.clone();newStyleHueDegrees=0;}

        s.exposureStops=finiteRange(json.optDouble("exposureStops",0f),-1.5f,1.5f,0f);
        s.photoContrast=finiteRange(json.optDouble("photoContrast",1f),.5f,1.8f,1f);
        s.levelsBlack=Math.max(0,Math.min(254,json.optInt("levelsBlack",0)));s.levelsWhite=Math.max(s.levelsBlack+1,Math.min(255,json.optInt("levelsWhite",255)));
        s.levelsGamma=finiteRange(json.optDouble("levelsGamma",1f),.3f,3f,1f);s.levelsShadows=finiteRange(json.optDouble("levelsShadows",0f),-1f,1f,0f);
        s.levelsHighlights=finiteRange(json.optDouble("levelsHighlights",0f),-1f,1f,0f);s.edgePriority=finiteRange(json.optDouble("edgePriority",.55f),0f,1f,.55f);
        s.blackColorProtection=finiteRange(json.optDouble("blackColorProtection",CmykPortraitDefaults.BLACK_COLOR_PROTECTION),0f,.8f,CmykPortraitDefaults.BLACK_COLOR_PROTECTION);
        s.shadowColorRecovery=finiteRange(json.optDouble("shadowColorRecovery",CmykPortraitDefaults.SHADOW_COLOR_RECOVERY),0f,.85f,CmykPortraitDefaults.SHADOW_COLOR_RECOVERY);
        s.portraitMidtones=finiteRange(json.optDouble("portraitMidtones",CmykPortraitDefaults.PORTRAIT_MIDTONES),0f,1f,CmykPortraitDefaults.PORTRAIT_MIDTONES);
        s.linkedControls=json.optBoolean("linkedControls",false); // preserve missing-field legacy projects
        s.masterSpacingMm=finiteRange(json.optDouble("masterSpacingMm",2.54),.1f,100f,2.54f);
        s.masterDensity=PercentControlLimits.dotCoverage((float)json.optDouble("masterDensity",1));
        s.masterContrast=finiteRange(json.optDouble("masterContrast",1),.05f,6f,1f);
        s.anglePreset=json.optInt("anglePreset",VinylEngine.ANGLES_CUSTOM); // do not silently rotate legacy cut paths
        s.screenRotationDeg=finiteRange(json.optDouble("screenRotationDeg",0),-360f,360f,0f);
        s.lineMaxFraction=finiteRange(json.optDouble("lineMaxFraction",.85),.06f,.98f,.85f);
        s.lineGamma=finiteRange(json.optDouble("lineGamma",1),.2f,4f,1f);
        s.lineSmoothing=finiteRange(json.optDouble("lineSmoothing",.38),0f,.98f,.38f);
        s.lineSampleMm=finiteRange(json.optDouble("lineSampleMm",.45),.1f,2f,.45f);
        // Legacy projects without the new setting stay geometry-exact until
        // their owner selects Reset best settings. Never silently bridge old cuts.
        s.lineContinuity=finiteRange(json.optDouble("lineContinuity",0f),0f,1f,0f);
        // Existing saved projects MUST remain byte-identical until the user opts in.
        s.linePresenceRecovery=finiteRange(json.optDouble("linePresenceRecovery",0f),0f,1f,0f);
        s.opaqueLineBalance=json.optBoolean("opaqueLineBalance",false);
        s.lineHighlightRetention=finiteRange(json.optDouble("lineHighlightRetention",0f),0f,1f,0f);
        // Older saved projects keep their original modern appearance until
        // the user explicitly selects Classic v2.24. Removed carrier flags
        // are intentionally ignored, including in previously saved projects.
        s.classicCmykLines=json.optBoolean("classicCmykLines",false);
        s.portraitBackgroundGuard=finiteRange(json.optDouble("portraitBackgroundGuard",0f),0f,1f,0f);
        s.contourStrength=finiteRange(json.optDouble("contourStrength",.65),0f,1f,.65f);
        s.detailBoost=finiteRange(json.optDouble("detailBoost",0),0f,1f,0f);
        s.shapeDefinition=finiteRange(json.optDouble("shapeDefinition",0),0f,1f,0f);
        s.crossAngleDeg=finiteRange(json.optDouble("crossAngleDeg",60),25f,90f,60f);
        s.radialFocusX=finiteRange(json.optDouble("radialFocusX",.5),0f,1f,.5f);
        s.radialFocusY=finiteRange(json.optDouble("radialFocusY",.35),0f,1f,.35f);
        s.lineInvert=json.optBoolean("lineInvert",false);
        s.waveAmplitude=finiteRange(json.optDouble("waveAmplitude",.14),0f,.23f,.14f);
        s.waveCyclesPerInch=finiteRange(json.optDouble("waveCyclesPerInch",1.6),.1f,8f,1.6f);
        s.hatchThreshold=finiteRange(json.optDouble("hatchThreshold",.70),.05f,.95f,.70f);s.shapeScale=finiteRange(json.optDouble("shapeScale",.88),.3f,.95f,.88f);s.screenWords=WordScreenMotif.normalize(json.optString("screenWords","VINYL STUDIO"));
        JSONArray snips=json.optJSONArray("snips");s.snips=new SnipMask();
        if(snips!=null){if(snips.length()>SnipMask.MAX_OPERATIONS)throw new JSONException("Too many snips");
            for(int k=0;k<snips.length();k++){JSONObject item=snips.getJSONObject(k);JSONArray coords=item.getJSONArray("points");
                if(coords.length()<6||coords.length()>SnipMask.MAX_VERTICES*2||(coords.length()&1)!=0)throw new JSONException("Bad snip");
                float[] points=new float[coords.length()];for(int j=0;j<points.length;j++)points[j]=(float)coords.getDouble(j);
                try{s.snips.add(new SnipMask.Operation(item.getInt("kind"),points));}
                catch(IllegalArgumentException bad){throw new JSONException("Invalid snip: "+bad.getMessage());}
            }
        }
        JSONArray a=json.getJSONArray("layers");if(a.length()<2||a.length()>8)throw new JSONException("Layer count must be 2–8");for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);VinylEngine.Layer l=new VinylEngine.Layer(o.optString("name","Vinyl "+(i+1)),o.optInt("color",VinylEngine.START_COLORS[i]));l.cmykChannel=o.optInt("cmykChannel",-1);l.silhouetteBase=o.optBoolean("silhouetteBase",false);l.autoDisabledForBase=o.optBoolean("autoDisabledForBase",false);
            if(l.cmykChannel<0&&s.mode==VinylEngine.CMYK){String n=l.name.toLowerCase(Locale.US);
                if(n.equals("cyan"))l.cmykChannel=0;else if(n.equals("magenta"))l.cmykChannel=1;
                else if(n.equals("yellow"))l.cmykChannel=2;else if(n.equals("black"))l.cmykChannel=3;
                else if(a.length()==4)l.cmykChannel=i; // Only legacy four-channel CMYK had implicit indices.
            }
            l.enabled=o.optBoolean("enabled",true);l.excludeFromExport=o.optBoolean("excludeFromExport",false);l.angle=(float)o.optDouble("angle",45);l.spacing=(float)o.optDouble("spacing",3);l.density=(float)o.optDouble("density",1);l.contrast=(float)o.optDouble("contrast",1);l.strength=(float)o.optDouble("strength",l.cmykChannel==0&&s.mode==VinylEngine.CMYK?1.12:1);
            l.lineStyleOverride=Math.max(-1,Math.min(VinylEngine.PATTERNS.length-1,o.optInt("lineStyleOverride",-1)));l.lineWidthScale=finiteRange(o.optDouble("lineWidthScale",1),.3f,1.8f,1f);
            l.lineGammaOverride=finiteRange(o.optDouble("lineGammaOverride",0),0f,4f,0f);l.angleFineTuneDeg=finiteRange(o.optDouble("angleFineTuneDeg",0),-12f,12f,0f);
            JSONArray cv=o.optJSONArray("curve");if(cv!=null&&cv.length()==5)for(int j=0;j<5;j++)l.curve[j]=(float)cv.optDouble(j,j/4f);normalizeLoadedLayer(l);s.layers.add(l);}JSONArray m=json.optJSONArray("marks");if(m!=null&&m.length()==4){s.marks=new float[4][2];for(int i=0;i<4;i++){JSONArray p=m.getJSONArray(i);s.marks[i][0]=(float)p.getDouble(0);s.marks[i][1]=(float)p.getDouble(1);}}if(s.mode==VinylEngine.CMYK){if(VinylEngine.CmykExtendedStack.hasAnchors(s.layers))VinylEngine.CmykExtendedStack.canonicalize(s.layers);else s.mode=VinylEngine.SPOT;}
        if(s.linkedControls){for(VinylEngine.Layer l:s.layers){l.spacing=s.masterSpacingMm;l.density=s.masterDensity;l.contrast=s.masterContrast;}}
        if(s.anglePreset!=VinylEngine.ANGLES_CUSTOM)VinylEngine.applyAnglePreset(s);
        return s;}
    /** Restore inactive layer settings from project and undo history without exposing extra swatches. */
    private void restoreParkedVinylLayers(JSONObject json){
        parkedVinylLayers.clear();JSONArray saved=json.optJSONArray("parkedVinylLayers");
        if(saved==null)return;
        for(int i=0;i<saved.length()&&i<8;i++){
            JSONObject item=saved.optJSONObject(i);if(item==null)continue;
            VinylEngine.Layer layer=new VinylEngine.Layer(item.optString("name","Vinyl"),item.optInt("color",0xff222222));
            layer.cmykChannel=item.optInt("cmykChannel",-1);layer.enabled=item.optBoolean("enabled",true);layer.excludeFromExport=item.optBoolean("excludeFromExport",false);
            layer.angle=(float)item.optDouble("angle",45);layer.spacing=(float)item.optDouble("spacing",3);
            layer.density=(float)item.optDouble("density",1);layer.contrast=(float)item.optDouble("contrast",1);
            layer.strength=(float)item.optDouble("strength",1);
            layer.lineStyleOverride=Math.max(-1,Math.min(VinylEngine.PATTERNS.length-1,item.optInt("lineStyleOverride",-1)));
            layer.lineWidthScale=finiteRange(item.optDouble("lineWidthScale",1),.3f,1.8f,1f);
            layer.lineGammaOverride=finiteRange(item.optDouble("lineGammaOverride",0),0f,4f,0f);
            layer.angleFineTuneDeg=finiteRange(item.optDouble("angleFineTuneDeg",0),-12f,12f,0f);
            JSONArray curve=item.optJSONArray("curve");
            if(curve!=null&&curve.length()==5)for(int j=0;j<5;j++)layer.curve[j]=(float)curve.optDouble(j,j/4f);
            normalizeLoadedLayer(layer);
            parkedVinylLayers.add(layer);
        }
    }
    /** Save both new styles independently so preset, panel placement, and physical colors
     * survive project ZIP export, library save, undo/redo, and restoration. */
    private static JSONObject savePopPosterSettings(PopPosterEngine.Options o)throws JSONException{
        JSONObject j=new JSONObject();
        j.put("preset",o.preset);j.put("grid",o.grid);j.put("bgMode",o.bgMode);
        j.put("posterBackdrop",o.posterBackdrop);j.put("toneCount",o.toneCount);
        j.put("posterPalette",o.posterPalette);j.put("rays",o.rays);j.put("textureTone",o.textureTone);
        j.put("mode",o.mode);j.put("minIslandPx",o.minIslandPx);
        j.put("gutter",o.gutter);j.put("shadowT",o.shadowT);j.put("keyT",o.keyT);
        j.put("keyOff",o.keyOff);j.put("blockOff",o.blockOff);
        j.put("contrast",o.contrast);j.put("gamma",o.gamma);j.put("blur",o.blur);
        j.put("bgTolerance",o.bgTolerance);j.put("bgToneThreshold",o.bgToneThreshold);
        j.put("toneBias",o.toneBias);j.put("texturePeriod",o.texturePeriod);
        j.put("textureFill",o.textureFill);j.put("frame",o.frame);j.put("trapMm",o.trapMm);
        j.put("texture",o.texture);j.put("includeBase",o.includeBase);
        j.put("autoLevels",o.autoLevels);j.put("banner",o.banner==null?"":o.banner);
        JSONArray palettes=new JSONArray();for(int v:o.panelPalettes)palettes.put(v);
        j.put("panelPalettes",palettes);
        if(o.panelColors!=null){JSONArray perPanel=new JSONArray();
            for(int[] colors:o.panelColors){JSONArray palette=new JSONArray();
                if(colors!=null)for(int color:colors)palette.put(color);perPanel.put(palette);}
            j.put("panelColors",perPanel);
        }
        if(o.posterColors!=null){JSONArray colors=new JSONArray();for(int color:o.posterColors)colors.put(color);
            j.put("posterColors",colors);}
        return j;
    }
    private static PopPosterEngine.Options loadPopPosterSettings(JSONObject j,int style){
        PopPosterEngine.Options d=PopPosterEngine.preset(style,0,254f);
        if(j==null)return d;
        try{
            int p=Math.max(0,Math.min(style==PopPosterEngine.POP?5:4,j.optInt("preset",0)));
            PopPosterEngine.Options o=PopPosterEngine.preset(style,p,254f);
            o.grid=j.optInt("grid",o.grid);o.bgMode=j.optInt("bgMode",o.bgMode);
            o.posterBackdrop=j.optInt("posterBackdrop",o.posterBackdrop);
            o.toneCount=j.optInt("toneCount",o.toneCount);
            o.posterPalette=j.optInt("posterPalette",o.posterPalette);
            o.rays=j.optInt("rays",o.rays);o.textureTone=j.optInt("textureTone",o.textureTone);
            o.mode=j.optInt("mode",o.mode);o.minIslandPx=j.optInt("minIslandPx",o.minIslandPx);
            o.gutter=(float)j.optDouble("gutter",o.gutter);
            o.shadowT=(float)j.optDouble("shadowT",o.shadowT);
            o.keyT=(float)j.optDouble("keyT",o.keyT);
            o.keyOff=(float)j.optDouble("keyOff",o.keyOff);
            o.blockOff=(float)j.optDouble("blockOff",o.blockOff);
            o.contrast=(float)j.optDouble("contrast",o.contrast);
            o.gamma=(float)j.optDouble("gamma",o.gamma);o.blur=(float)j.optDouble("blur",o.blur);
            o.bgTolerance=(float)j.optDouble("bgTolerance",o.bgTolerance);
            o.bgToneThreshold=(float)j.optDouble("bgToneThreshold",o.bgToneThreshold);
            o.toneBias=(float)j.optDouble("toneBias",o.toneBias);
            o.texturePeriod=(float)j.optDouble("texturePeriod",o.texturePeriod);
            o.textureFill=(float)j.optDouble("textureFill",o.textureFill);
            o.frame=(float)j.optDouble("frame",o.frame);
            o.trapMm=(float)j.optDouble("trapMm",o.trapMm);
            o.texture=j.optBoolean("texture",o.texture);
            o.includeBase=j.optBoolean("includeBase",o.includeBase);
            o.autoLevels=j.optBoolean("autoLevels",o.autoLevels);
            o.banner=j.optString("banner",o.banner);
            JSONArray palettes=j.optJSONArray("panelPalettes");
            if(palettes!=null&&palettes.length()==9)for(int i=0;i<9;i++)
                o.panelPalettes[i]=Math.max(0,Math.min(PopPosterEngine.POP_COLORS.length-1,palettes.optInt(i,i%8)));
            JSONArray perPanel=j.optJSONArray("panelColors");
            if(perPanel!=null&&perPanel.length()<=9){o.panelColors=new int[9][];
                for(int i=0;i<perPanel.length();i++){
                    JSONArray colors=perPanel.optJSONArray(i);
                    if(colors!=null&&colors.length()==4){o.panelColors[i]=new int[4];
                        for(int k=0;k<4;k++)o.panelColors[i][k]=colors.optInt(k,0xff141414)|0xff000000;}
                }
            }
            JSONArray poster=j.optJSONArray("posterColors");
            if(poster!=null&&poster.length()==o.toneCount){o.posterColors=new int[o.toneCount];
                for(int k=0;k<o.toneCount;k++)o.posterColors[k]=poster.optInt(k,0xff141414)|0xff000000;}
            o.check();return o;
        }catch(RuntimeException invalid){return d;}
    }
    private void restoreArtworkModes(JSONObject json){
        popOptions=loadPopPosterSettings(json.optJSONObject("popPanelsSettings"),PopPosterEngine.POP);
        posterOptions=loadPopPosterSettings(json.optJSONObject("posterStencilSettings"),PopPosterEngine.POSTER);
        final int storedStyleId=json.optInt("newStyleId",-1);
        newStyleId=VinylStyleSuite.restoredStyleId(storedStyleId);
        if(VinylStyleSuite.isRetiredStyleId(storedStyleId)){
            String retired=storedStyleId==VinylStyleSuite.RETIRED_STYLE_ID?"Typography Portrait":"This saved style";
            alternateStatus=retired+" has been retired. Your photo and Snip are retained; choose a new style.";
            toast(retired+" retired; photo and Snip retained");
        }
        newStyleDetail=Math.max(0,Math.min(100,json.optInt("newStyleDetail",55)));
        newStyleSmoothing=OrganicFlowCutSmoother.DEFAULT; // Ignore legacy custom slider values
        final int savedVariant=json.optInt("newStyleVariant",0);
        newStyleVariant=NewStyleVariantPolicy.restore(newStyleId,savedVariant);
        if(newStyleId==VinylStyleSuite.TESSELLATION_MOTIF&&
            !TessellationMotifEngine.selectablePattern(savedVariant)){
            alternateStatus="This tessellation layout was retired. Trihexagonal selected; your photo and Snip are retained.";
            toast("Retired tessellation changed to Trihexagonal; original photo and Snip retained");
        }
        newStyleTessMotif=Math.max(0,Math.min(TessellationMotifEngine.LEGACY_MOTIF_COUNT-1,json.optInt("newStyleTessMotif",0)));
        newStyleColorCount=newStyleId>=0?
            Math.max(NewStyleColorSlots.minimumSelectable(newStyleId),
                Math.min(NewStyleColorSlots.maximumSelectable(newStyleId),json.optInt("newStyleColorCount",6))):6;
        // The common physical-layer count now owns Poster. Existing projects
        // retain their previously selected 2–5 layer count on migration.
        if(newStyleId==VinylStyleSuite.POSTER_STENCIL){
            int storedCount=json.optInt("newStyleColorCount",posterOptions.toneCount);
            newStyleColorCount=Math.max(2,Math.min(9,storedCount));
            posterOptions.toneCount=newStyleColorCount;
        }
        newStyleLeadMm=(float)Math.max(.6,Math.min(3,json.optDouble("newStyleLeadMm",1.4)));
        newStyleWeightMm=(float)Math.max(VinylStyleSuite.isOrganicFlow(newStyleId)?.25:.5,
            Math.min(VinylStyleSuite.isOrganicFlow(newStyleId)?2.5:1.5,
                json.optDouble("newStyleWeightMm",.7)));
        prismaticSpacingMm=(float)Math.max(.2,Math.min(1.6,json.optDouble("prismaticSpacingMm",.32)));
        trioSpacingMm=(float)Math.max(.2,Math.min(1.8,json.optDouble("trioSpacingMm",.40)));
        trioOrganic=Math.max(0,Math.min(100,json.optInt("trioOrganic",50)));
        prismaticIrregularity=Math.max(0,Math.min(100,json.optInt("prismaticIrregularity",43)));
        prismaticRoundness=Math.max(0,Math.min(100,json.optInt("prismaticRoundness",52)));
        prismaticFeature=Math.max(0,Math.min(100,json.optInt("prismaticFeature",76)));
        prismaticTone=Math.max(0,Math.min(100,json.optInt("prismaticTone",65)));
        newStyleMatchVinyl=json.optBoolean("newStyleMatchVinyl",false);
        newStylePaletteRanks=loadPaletteRanks(json.optJSONArray("newStylePaletteRanks"),9);
        JSONArray newColorJson=json.optJSONArray("newStyleColors");
        if(newColorJson!=null&&newColorJson.length()>=8)
            for(int i=0;i<9;i++)newStyleColors[i]=newColorJson.optInt(i,newStyleColors[i]);
        if(newStyleId==VinylStyleSuite.POSTER_STENCIL &&
           !json.optBoolean("posterUsesUnifiedPalette",false)){
            // Before the nine-layer upgrade, Poster swatches lived exclusively
            // in posterStencilSettings.posterColors and never drove Colors/Layers.
            // Move these into the shared physical slots without discarding edits.
            int[] nine=PopPosterEngine.posterPalette(posterOptions.posterPalette,9);
            int[] old=posterOptions.posterColors;
            if(old==null||old.length!=newStyleColorCount)
                old=PopPosterEngine.posterPalette(posterOptions.posterPalette,newStyleColorCount);
            System.arraycopy(old,0,nine,0,newStyleColorCount);
            newStyleColors=nine;newStyleHueBaseColors=nine.clone();
            newStyleHueDegrees=0;newStyleMatchVinyl=true;
            newStylePhotoAutoMemory[newStyleId]=false;
        }
        JSONArray memories=json.optJSONArray("newStylePaletteMemory");
        for(int i=0;i<VinylStyleSuite.NAMES.length;i++){
            newStylePaletteMemory[i]=null;newStyleRankMemory[i]=null;newStyleCountMemory[i]=0;newStyleManualMemory[i]=false;
            newStylePhotoAutoMemory[i]=false;
            newStyleSettingsSaved[i]=false;
            // D19: Confetti geometry memory is per project. Without a saved entry it must
            // return to the defaults, not keep the previous project's (or document's) values.
            if(i>=VinylStyleSuite.CONFETTI&&i<=VinylStyleSuite.CONFETTI_FALLEN){
                int n=i-VinylStyleSuite.CONFETTI;
                trioSpacingMemory[n]=TRIO_SPACING_DEFAULTS[n];trioOrganicMemory[n]=TRIO_ORGANIC_DEFAULTS[n];
            }
            JSONObject item=memories==null?null:memories.optJSONObject(i);
            JSONArray colors=item==null?null:item.optJSONArray("colors");
            if(colors!=null&&colors.length()>=8){
                int[] slots=new int[9];for(int j=0;j<9;j++)slots[j]=colors.optInt(j,newStyleColors[j]);
                newStylePaletteMemory[i]=slots;
                newStyleRankMemory[i]=loadPaletteRanks(item.optJSONArray("paletteRanks"),9);
                newStyleCountMemory[i]=Math.max(NewStyleColorSlots.minimumSelectable(i),
                    Math.min(NewStyleColorSlots.maximumSelectable(i),item.optInt("count",6)));
                newStyleManualMemory[i]=item.optBoolean("manual",false);
                newStylePhotoAutoMemory[i]=item.optBoolean("explicitPhotoAuto",false);
                newStyleDetailMemory[i]=Math.max(0,Math.min(100,item.optInt("detail",55)));
                newStyleVariantMemory[i]=NewStyleVariantPolicy.restore(i,item.optInt("variant",0));
                newStyleSmoothingMemory[i]=OrganicFlowCutSmoother.DEFAULT; // Legacy setting retained in JSON only
                newStyleWeightMemory[i]=(float)Math.max(VinylStyleSuite.isOrganicFlow(i)?.25:.5,
                    Math.min(VinylStyleSuite.isOrganicFlow(i)?2.5:1.5,
                        item.optDouble("weight",.7)));
                newStyleLeadMemory[i]=(float)Math.max(.6,Math.min(3,item.optDouble("lead",1.4)));
                if(i>=VinylStyleSuite.CONFETTI&&i<=VinylStyleSuite.CONFETTI_FALLEN){
                    int n=i-VinylStyleSuite.CONFETTI;
                    trioSpacingMemory[n]=(float)Math.max(.2,Math.min(1.8,
                        item.optDouble("trioSpacingMm",trioSpacingMemory[n])));
                    trioOrganicMemory[n]=Math.max(0,Math.min(100,
                        item.optInt("trioOrganic",trioOrganicMemory[n])));
                }
                newStyleSettingsSaved[i]=item.has("detail");
            }
        }
        newStyleWords=json.optString("newStyleWords","VINYL STUDIO");
        newStyleResult=null;newStyleVectors=null;newStyleSelectedLayer=-1;
        // Saved final colors are authoritative; legacy Poster migrations install
        // new physical hue bases before the artwork is shown.
        if(json.optJSONArray("newStyleHueBaseColors")==null ||
           (newStyleId==VinylStyleSuite.POSTER_STENCIL &&
            !json.optBoolean("posterUsesUnifiedPalette",false)))
            {newStyleHueBaseColors=newStyleColors.clone();newStyleHueDegrees=0;}
        if(newStyleActive()){
            if(CamouflagePalette.isCamouflage(newStyleId)&&!newStyleMatchVinyl)
                newStylePhotoAutoMemory[newStyleId]=true; // honor an open project's explicit saved appearance
            rememberNewStylePalette();
        }
        String lineName=json.optString("continuousStyleName","");
        String savedLine=null;
        try{if(!lineName.isEmpty()&&!lineName.equals("TONAL_SPIROGRAPH"))savedLine=ContinuousLineModes.LineStyle.valueOf(lineName).name();}catch(IllegalArgumentException ignored){}
        boolean savedTonal=json.optBoolean("tonalStencilMode",false);
        boolean savedMaze=json.optBoolean("mazePortraitMode",false);
        boolean savedTess=json.optBoolean("tessellationMode",false);
        boolean savedLayered=json.optBoolean("layeredCellMode",false);
        final int savedOverprintKind=json.optInt("overprintCellKind",0);
        // Retired Painterly Cell Weave projects keep photo/Snip/settings but
        // return to normal photo mode. Never reinterpret ID 5 as Mixed Marks.
        if(savedLayered&&StyleSelection.isRetiredOverprint(savedOverprintKind)){
            savedLayered=false;
            if(projectSourceJson!=null){
                projectSourceJson.remove("layeredCellMode");
                projectSourceJson.remove("overprintCellKind");
            }
        }
        int savedSuite=newStyleId; // already restored (retired ids map to -1) above
        if(VinylStyleSuite.isRetiredStyleId(storedStyleId)){
            if(projectSourceJson!=null)projectSourceJson.remove("newStyleId");
            savedLine=null;savedTonal=false;savedMaze=false;savedTess=false;savedLayered=false;
        }
        // Compatibility: deprecated numbered-portrait projects still load their photo,
        // lighting and cut settings, but fall back to normal artwork. A former
        // numbered-output toggle on an existing line style simply becomes that
        // same line style's normal output.
        if(json.optBoolean("dotToDotPortrait",false)){
            // One-time tombstone migration. Leaving this flag in projectSourceJson
            // would reset a newly chosen style on every undo/reopen/restart.
            if(projectSourceJson!=null)projectSourceJson.remove("dotToDotPortrait");
            savedSuite=-1;savedLine=null;savedTonal=false;savedMaze=false;savedTess=false;savedLayered=false;
        }
        // Older saved projects can only have one artwork mode at a time; StyleSelection
        // applies the historical precedence (suite > line > tessellation > layered > tonal > maze).
        applyStyleSelection(StyleSelection.fromLegacyFlags(savedSuite,savedLine,savedTonal,savedMaze,savedTess,savedLayered,
            json.optBoolean("offsetCellMode",false),json.optBoolean("bottleCapMode",false),
            json.optBoolean("fishScaleMode",false),savedLayered?savedOverprintKind:0));
        tessellationStyle=Math.max(0,Math.min(TessellationEngine.STIPPLE,json.optInt("tessellationStyle",0)));
        stippleSettings=new MultiColorStippleEngine.Options();
        stippleSettings.overlap=Math.max(0,Math.min(3,json.optInt("stippleOverlap",MultiColorStippleEngine.OVERPRINT)));
        stippleSettings.assignment=Math.max(0,Math.min(1,json.optInt("stippleAssignment",0)));
        stippleSettings.shape=Math.max(0,Math.min(MultiColorStippleEngine.SHAPE_NAMES.length-1,
            json.optInt("stippleShape",MultiColorStippleEngine.ROUND)));
        stippleSettings.adaptiveCoverage=json.optBoolean("stippleAdaptiveCoverage",true);
        stippleSettings.targetCoverage=(float)Math.max(.20,Math.min(.80,json.optDouble("stippleTargetCoverage",.64)));
        // Existing project files keep their original 100% quantity and physical
        // dot size, avoiding silent changes to a previously exported design.
        stippleSettings.pointDensity=PercentControlLimits.pointDensity((float)json.optDouble("stipplePointDensity",1));
        stippleSettings.dotSize=PercentControlLimits.dotSize((float)json.optDouble("stippleDotSize",1));
        stippleSettings.optimizePacking=json.optBoolean("stippleOptimizePacking",false);
        stippleSettings.trapMm=(float)Math.max(0,Math.min(1.5,json.optDouble("stippleTrapMm",.3)));
        stippleSettings.minGapMm=(float)Math.max(0,Math.min(3,json.optDouble("stippleCrossGapMm",.35)));
        stippleSettings.overprintGapMm=(float)Math.max(0,Math.min(3,json.optDouble("stippleSameSheetGapMm",.08)));
        cutMaterialProfileId=Math.max(0,Math.min(4,json.optInt("cutMaterialProfileId",0)));
        JSONArray sd=json.optJSONArray("stippleDensity"),sm=json.optJSONArray("stippleMinDotMm"),se=json.optJSONArray("stippleEnabled"),sz=json.optJSONArray("stippleOrder");
        for(int i=0;i<8;i++){
            if(sd!=null&&i<sd.length())stippleSettings.density[i]=(float)Math.max(0,Math.min(1,sd.optDouble(i,1)));
            if(sm!=null&&i<sm.length())stippleSettings.minDotMm[i]=(float)Math.max(.4,Math.min(3,sm.optDouble(i,.8)));
            if(se!=null&&i<se.length())stippleSettings.enabled[i]=se.optBoolean(i,true);
            if(sz!=null&&i<sz.length())stippleSettings.order[i]=sz.optInt(i,i);
        }
        tessLayerCount=Math.max(2,Math.min(8,json.optInt("tessellationCount",5)));
        tessDetail=Math.max(10,Math.min(100,json.optInt("tessellationDetail",70)));
        // The gap slider expands above 1.2 mm for a large custom material bridge.
        // Do not silently truncate a previously saved, cutter-safe larger gap.
        tessGapMm=(float)Math.max(.15,Math.min(5.0,json.optDouble("tessellationGap",.36)));
        tessEdge=(float)Math.max(0,Math.min(1,json.optDouble("tessellationEdge",.65)));
        layeredColumns=Math.max(10,Math.min(40,json.optInt("layeredColumns",24)));
        layeredMotifDepth=Math.max(1,Math.min(3,json.optInt("layeredMotifDepth",3)));
        layeredFamily=Math.max(0,Math.min(LayeredCellPortraitEngine.SCALLOP,json.optInt("layeredFamily",0)));
        layeredPhotoDetail=(float)Math.max(0,Math.min(1,json.optDouble("layeredPhotoDetail",.70)));
        layeredArtDetail=(float)Math.max(0,Math.min(1,json.optDouble("layeredArtDetail",.52)));
        layeredSeamMm=(float)Math.max(0,Math.min(.5,json.optDouble("layeredSeamMm",.12)));
        layeredMinFeatureMm=(float)Math.max(.3,Math.min(1.5,json.optDouble("layeredMinFeatureMm",.5)));
        layeredBackground=Math.max(-1,Math.min(7,json.optInt("layeredBackground",-1)));
        // Missing keys mean a pre-RC116 saved project: preserve its sliced cells.
        layeredSolidBase=json.optBoolean("layeredSolidBase",false);
        layeredExtraMotif=json.optBoolean("layeredExtraMotif",false);
        if(offsetCellMode||(!bottleCapMode&&!fishScaleMode))layeredExtraMotif=false;
        if(offsetCellMode||overprintCellKind>0)layeredSolidBase=true;
        Arrays.fill(layeredLayerFamilies,-1);
        JSONArray familyValues=json.optJSONArray("layeredLayerFamilies");
        if(familyValues!=null)for(int i=0;i<Math.min(8,familyValues.length());i++)
            layeredLayerFamilies[i]=Math.max(-1,Math.min(LayeredCellPortraitEngine.SCALLOP,familyValues.optInt(i,-1)));
        tessPalette=json.optString("tessellationPalette","Portrait");
        if(layeredBackground>=tessLayerCount)layeredBackground=-1;
        tessPaletteRanks=loadPaletteRanks(json.optJSONArray("tessPaletteRanks"),8);
        JSONArray tessSaved=json.optJSONArray("tessellationColors");
        if(tessSaved!=null)for(int i=0;i<Math.min(8,tessSaved.length());i++)tessColors[i]=tessSaved.optInt(i,tessColors[i]);
        JSONArray hueSaved=json.optJSONArray("tessellationHueBaseColors");
        if(hueSaved!=null&&hueSaved.length()==8){
            int[] base=new int[8];for(int i=0;i<8;i++)base[i]=hueSaved.optInt(i,tessColors[i]);
            tessHueBaseColors=base;
            tessHueDegrees=PaletteHueShift.clampDegrees(json.optInt("tessellationHueDegrees",0));
            applyTessHue(tessHueDegrees);
        }else{
            // Old projects have only final colors: preserve them byte-for-byte.
            tessHueBaseColors=tessColors.clone();tessHueDegrees=0;
        }
        if(newColorJson==null){
            // Pre-RC43 art styles used Tessellation swatches as their fixed vinyl palette.
            newStyleColors=expandNewStyleSwatches(tessColors);
            newStyleHueBaseColors=newStyleColors.clone();newStyleHueDegrees=0;
            if(newStyleActive())rememberNewStylePalette();
        }
        standardPaletteRanks=loadPaletteRanks(json.optJSONArray("standardPaletteRanks"),8);
        tonalPaletteRanks=loadPaletteRanks(json.optJSONArray("tonalPaletteRanks"),8);
        JSONArray stdBase=json.optJSONArray("standardHueBaseColors");
        if(stdBase!=null&&stdBase.length()==8&&stdBase.optInt(0,0)!=0){
            for(int i=0;i<8;i++)standardHueBaseColors[i]=stdBase.optInt(i,0);
            standardHueDegrees=PaletteHueShift.clampDegrees(json.optInt("standardHueDegrees",0));
            // fromJson() has already assigned the loaded project to settings.
            // The JSON contains exact final RGB values: do not rotate them
            // during restore, or manual swatch edits will drift.
        }else{
            java.util.Arrays.fill(standardHueBaseColors,0);
            for(int i=0;i<settings.layers.size()&&i<8;i++)
                standardHueBaseColors[i]=settings.layers.get(i).color;
            standardHueDegrees=0; // Pre-hue projects preserve exact saved colors.
        }
        java.util.Arrays.fill(cmykAccentHueBaseColors,0);
        JSONArray savedAccents=json.optJSONArray("cmykAccentHueBaseColors");
        if(savedAccents!=null){
            for(int i=0;i<4;i++)cmykAccentHueBaseColors[i]=savedAccents.optInt(i,0);
        }else if(settings.mode==VinylEngine.CMYK){
            // Migrate older projects with active CMYK extras safely.
            for(int i=3;i<settings.layers.size()-1;i++)
                cmykAccentHueBaseColors[i-3]=standardHueBaseColors[i];
        }else java.util.Arrays.fill(cmykAccentHueBaseColors,0);
        tonalHueDegrees=PaletteHueShift.clampDegrees(json.optInt("tonalHueDegrees",0));
        tonalLayerCount=Math.max(2,Math.min(8,json.optInt("tonalLayerCount",5)));
        tonalBlur=Math.max(0,Math.min(8,json.optInt("tonalBlur",1)));
        tonalEdge=(float)Math.max(0,Math.min(1,json.optDouble("tonalEdge",.65)));
        tonalBaseSource=Math.max(-1,Math.min(tonalLayerCount-1,json.optInt("tonalBaseSource",-1)));
        tonalMatchMyVinyl=json.optBoolean("tonalMatchMyVinyl",false);
        tonalAutoWhiteBackground=json.optBoolean("tonalAutoWhiteBackground",true);
        tonalCutLayerPreview=json.optBoolean("tonalCutLayerPreview",true);
        tonalIncludeSourcePhoto=json.optBoolean("tonalIncludeSourcePhoto",false);
        int[] defaults={0,0,40,44,48,52,56,60,64};
        System.arraycopy(defaults,0,tonalDetailByCount,0,defaults.length);
        JSONArray details=json.optJSONArray("tonalDetailByCount");
        if(details!=null)for(int i=2;i<=8&&i<details.length();i++)
            tonalDetailByCount[i]=Math.max(0,Math.min(100,details.optInt(i,defaults[i])));
        continuousDensity=Math.max(1,Math.min(100,json.optInt("continuousDensity",65)));
        curveDepth=Math.max(1,Math.min(7,json.optInt("curveDepth",3)));
        curvePhotoInfluence=(float)Math.max(0,Math.min(1,json.optDouble("curvePhotoInfluence",.85)));
        curveToneMethod=Math.max(0,Math.min(2,json.optInt("curveToneMethod",AdaptiveCurvePortrait.HYBRID)));
        curveToneContrast=(float)Math.max(0,Math.min(1,json.optDouble("curveToneContrast",.55)));
        curveShadowBoost=(float)Math.max(0,Math.min(1,json.optDouble("curveShadowBoost",.75)));
        curveHighlightProtection=(float)Math.max(0,Math.min(1,json.optDouble("curveHighlightProtection",.85)));
        curveEdgePriority=(float)Math.max(0,Math.min(1,json.optDouble("curveEdgePriority",.55)));
        curveCornerRounding=(float)Math.max(0,Math.min(1,json.optDouble("curveCornerRounding",.15)));
        curveRotationDegrees=(float)Math.max(-180,Math.min(180,json.optDouble("curveRotationDegrees",0)));
        curveVinylRibbon=json.optBoolean("curveVinylRibbon",false);
        curveRibbonWidthMm=(float)Math.max(.55,Math.min(3,json.optDouble("curveRibbonWidthMm",1.15)));
        curveMinFeatureMm=(float)Math.max(.4,Math.min(1.2,json.optDouble("curveMinFeatureMm",.55)));
        curveMinGapMm=(float)Math.max(.3,Math.min(1.5,json.optDouble("curveMinGapMm",.45)));
        mazeGrid=Math.max(20,Math.min(100,json.optInt("mazeGrid",54)));
        mazeDifficulty=Math.max(0,Math.min(3,json.optInt("mazeDifficulty",1)));
        continuousStrokeMm=(float)Math.max(.10,Math.min(1.5,json.optDouble("singleLineStrokeMm",.35)));
        Arrays.fill(tonalColorOverrides,0);JSONArray overrides=json.optJSONArray("tonalColorOverrides");
        if(overrides!=null)for(int i=0;i<8&&i<overrides.length();i++)tonalColorOverrides[i]=overrides.optInt(i,0);
    }
    private String snapshot(){return toJson(settings).toString();}
    private void checkpoint(){cancelPaletteReduction();if(restoring)return;undo.push(snapshot());redo.clear();}
    private void history(boolean forward){cancelPaletteReduction();BoundedEditHistory src=forward?redo:undo,dest=forward?undo:redo;if(src.isEmpty()){toast("Nothing to "+(forward?"redo":"undo"));return;}dest.push(snapshot());try{invalidateStipplePhotoPaletteRequest();JSONObject restored=new JSONObject(src.pop());settings=fromJson(restored);restoreParkedVinylLayers(restored);restoreArtworkModes(restored);chosen=-1;rebuildLayers();
        // M3: undo/redo keeps the workspace and panel the user is in, refreshed with the restored values
        // (it used to jump to the Layers panel, tool 3, from any workspace).
        requestRender();invalidateToolPanels();setToolInternal(tool);}catch(Exception e){toast(getString(R.string.ui_could_not_restore_edit));}}
    private String draftJson(){
        JSONObject out=new JSONObject();try{
            out.put("closed",snipClosed);out.put("action",snipAction);
            out.put("editingIndex",editingSnipIndex);
            if(editingOriginal!=null){JSONObject backup=new JSONObject();backup.put("kind",editingOriginal.kind);
                JSONArray coords=new JSONArray();for(float value:editingOriginal.xy)coords.put(value);
                backup.put("points",coords);out.put("editingOriginal",backup);}
            JSONArray a=new JSONArray();for(float[] point:snipDraft)a.put(new JSONArray().put(point[0]).put(point[1]));
            out.put("points",a);
        }catch(JSONException ignored){}return out.toString();
    }
    private void restoreDraft(String value){
        cancelPaletteReduction();
        snipDraft.clear();editingSnipIndex=-1;editingOriginal=null;snipClosed=false;
        if(value==null)return;
        try{JSONObject data=new JSONObject(value);JSONArray a=data.optJSONArray("points");
            if(a==null||a.length()>SnipMask.MAX_VERTICES)return;
            for(int i=0;i<a.length();i++){
                JSONArray point=a.getJSONArray(i);float x=(float)point.getDouble(0),y=(float)point.getDouble(1);
                if(!Float.isFinite(x)||!Float.isFinite(y)||x<0||x>1||y<0||y>1)throw new JSONException("Invalid point");
                snipDraft.add(new float[]{x,y});
            }
            snipClosed=data.optBoolean("closed",false)&&snipDraft.size()>=3;
            int action=data.optInt("action",SnipMask.REMOVE_INSIDE);
            snipAction=(action==SnipMask.KEEP_INSIDE)?action:SnipMask.REMOVE_INSIDE;
            int ix=data.optInt("editingIndex",-1);
            editingSnipIndex=ix>=0&&ix<=settings.snips.size()?ix:-1;
            JSONObject backup=data.optJSONObject("editingOriginal");
            if(backup!=null&&editingSnipIndex>=0){JSONArray pts=backup.getJSONArray("points");
                float[] xy=new float[pts.length()];for(int j=0;j<xy.length;j++)xy[j]=(float)pts.getDouble(j);
                editingOriginal=new SnipMask.Operation(backup.getInt("kind"),xy);
            }
        }catch(Exception e){snipDraft.clear();snipClosed=false;editingSnipIndex=-1;editingOriginal=null;}
    }
    /** Whether the current editable project has a verified encoded original cache. */
    private boolean encodedProjectOriginal=false;
    private final ProjectPhotoStore projectPhotoStore=new ProjectPhotoStore();
    private boolean schedulePhotoJob(Runnable work){
        try {PhotoPersistenceQueue.execute(work);return true;}
        catch(java.util.concurrent.RejectedExecutionException rejected){
            postUi(()->toast(getString(R.string.ui_photo_storage_is_busy_changes_are)));
            return false;
        }
    }
    /** Unique per process; combined with photoRevision it names one photo state. */
    private static final String PHOTO_TOKEN_PROCESS=java.util.UUID.randomUUID().toString().substring(0,13);
    private long restoredPhotoRevision=Long.MIN_VALUE;
    private String restoredPhotoToken="";
    /** Token for the current photo: the restored pair keeps its on-disk token until edited. */
    private String photoPairToken(long revision){
        return revision==restoredPhotoRevision?restoredPhotoToken:PHOTO_TOKEN_PROCESS+"-r"+revision;
    }
    private void saveLocalPhoto(){
        final Bitmap bitmap=photo,full=originalPhoto==null?bitmap:originalPhoto;
        if(bitmap==null)return;
        final long revision=photoRevision;
        final String token=photoPairToken(revision);
        final File files=getFilesDir();
        schedulePhotoJob(()->{
            if(revision!=photoRevision)return; // do not persist a superseded crop
            try{projectPhotoStore.save(files,bitmap,full,revision,token);}
            catch(IOException | RuntimeException | OutOfMemoryError saveError){
                android.util.Log.e("VinylStudio","Local photo save failed",saveError);
                postUi(()->toast(getString(R.string.ui_photo_not_saved_to_device_storage)));
            }
        });
    }
    /** Restore small JSON preferences immediately; decode saved image off the UI thread.
     * An old restore cannot overwrite an import/open or a closed project. */
    private void restoreLocal(){
        final android.content.SharedPreferences prefs=getPreferences(MODE_PRIVATE);
        try{chosenProfile=prefs.getInt("sizeProfile",VinylSizing.BALANCED);}
        catch(RuntimeException wrongType){chosenProfile=VinylSizing.BALANCED;}
        // D1: an unreadable autosave is quarantined verbatim (editor.corrupt) BEFORE the
        // defaults below can be autosaved over it. Never discard the user's state silently.
        EditorAutosaveRecovery.Outcome<Boolean> restored=EditorAutosaveRecovery.restore(
            new EditorAutosaveRecovery.Store(){
                public String get(String key){
                    try{return prefs.getString(key,null);}catch(ClassCastException notText){return null;}
                }
                public void putAll(java.util.Map<String,String> changes){
                    android.content.SharedPreferences.Editor edit=prefs.edit();
                    for(java.util.Map.Entry<String,String> e:changes.entrySet())
                        if(e.getValue()==null)edit.remove(e.getKey());else edit.putString(e.getKey(),e.getValue());
                    edit.commit(); // durable before any fallback autosave can run
                }
            },
            "editor",
            raw->{JSONObject obj=new JSONObject(raw);
                settings=fromJson(obj);restoreParkedVinylLayers(obj);restoreArtworkModes(obj);return Boolean.TRUE;},
            System.currentTimeMillis());
        if(restored.restored()){
            if(newerProjectSchemaWarning)postUi(()->toast(getString(R.string.ui_this_project_uses_a_newer_format)));
        }else if(restored.quarantined){
            android.util.Log.e("VinylStudio","Autosaved editor state unreadable; kept as editor.corrupt: "+restored.reason);
            settings=newProjectSettings();projectSourceJson=null;
            projectSourceSchema=ProjectSettingsMigration.CURRENT_SCHEMA;newerProjectSchemaWarning=false;
            postUi(()->toast(getString(R.string.ui_editor_state_could_not_be_restored)));
        }
        chosen=-1;rebuildLayers();
        File directory=getFilesDir();
        final File scratch=getCacheDir();
        // Retain provenance intent even if the cache is corrupt/ambiguous.
        // Otherwise subsequent save silently substitutes a sampled bitmap.
        encodedProjectOriginal=prefs.getBoolean("encodedProjectOriginal",false);
        final long expectedRevision=photoRevision;
        final int ticket=++startupRestoreSerial;
        startupSavedDraft=prefs.getString("draftSnipV22",null);
        final boolean savedDirty=prefs.getBoolean("editorDirty",true);
        final String savedPhotoToken=prefs.getString(PhotoPairToken.PREF,"");
        startupRestoring=true;
        // Restores queue BEHIND any accepted write from the previous Activity.
        // This prevents reading an old photo while its atomic replacement waits.
        if(!schedulePhotoJob(()->{
            // Reclaim stale crash/orphan scratch files off the UI thread.
            ProjectTempJanitor.prune(scratch,System.currentTimeMillis());
            // The existence test MUST run behind pending photo saves. Checking
            // before entering the serial queue loses a first import on rotation.
            if(!StartupPhotoLoader.hasSavedPhoto(directory)){
                postUi(()->{if(ticket==startupRestoreSerial){startupRestoring=false;
                    currentLibraryId=null;projectLabel="New project";projectSession.setDirty(false);updateEditorSubtitle();}
                    productFlows.deliverPendingSharedImage();});
                return;
            }
            long start=System.nanoTime();
            StartupPhotoLoader.Snapshot result=null;
            try{result=StartupPhotoLoader.load(directory);}catch(Exception e){
                android.util.Log.w("VinylPerf","startupPhotoRestoreFailed",e);
            }
            final StartupPhotoLoader.Snapshot loaded=result;
            final ProjectLoadOwnership restoreOwnership=new ProjectLoadOwnership();
            pendingProjectLoads.add(restoreOwnership);
            if(loaded!=null)restoreOwnership.own(loaded::discard);
            android.util.Log.d("VinylPerf","startupPhotoDecodeMs="+((System.nanoTime()-start)/1_000_000L));
            boolean delivered=postUi(()->{
                if(ticket!=startupRestoreSerial||isFinishing()||isDestroyed()||photoRevision!=expectedRevision){
                    discardProjectLoad(restoreOwnership);
                    productFlows.deliverPendingSharedImage();
                    return;
                }
                startupRestoring=false;
                if(loaded==null){
                    discardProjectLoad(restoreOwnership);
                    status.setText(R.string.quality_saved_photo_unavailable_import_a_photograph);
                    artwork.invalidate();productFlows.deliverPendingSharedImage();return;
                }
                if(!restoreOwnership.transferToUi()){
                    discardProjectLoad(restoreOwnership);productFlows.deliverPendingSharedImage();return;
                }
                pendingProjectLoads.remove(restoreOwnership);
                photo=loaded.photo;originalPhoto=loaded.original;
                projectPhotoStore.markExistingOriginal(originalPhoto,getFilesDir());
                imageW=loaded.width;imageH=loaded.height;pixels=loaded.pixels;
                skipNextPhotoPaletteSuggestion=true;
                changedPhoto();
                restoredPhotoRevision=photoRevision;restoredPhotoToken=loaded.pairToken;
                PhotoPairToken.Verdict pairing=PhotoPairToken.compare(savedPhotoToken,loaded.pairToken);
                if(pairing==PhotoPairToken.Verdict.MISMATCH){
                    // Settings were saved for a different photo state than the one on disk
                    // (app closed between the two saves). Pixel-space Snip drafts cannot be trusted.
                    android.util.Log.w("VinylLibrary","Editor settings and autosaved photo are from different saves");
                    startupSavedDraft=null;
                    new AlertDialog.Builder(this).setTitle(getString(R.string.ui_some_recent_edits_were_not_restored))
                        .setMessage("VinylStudio closed while saving. Your photo was restored, but the latest crop, Snip or "+
                            "layer edits may belong to a different version of it. Check the artwork before exporting.")
                        .setPositiveButton("OK",null).show();
                }
                restoreDraft(startupSavedDraft);
                chosen=-1;rebuildLayers();
                setWorkflowStep(startupSavedStep);
                requestRender();
                projectSession.setDirty(savedDirty);updateEditorSubtitle();
                if(pairing==PhotoPairToken.Verdict.MISMATCH){projectSession.setDirty(true);updateEditorSubtitle();}
                artwork.invalidate();
                android.util.Log.d("VinylPerf","startupPhotoRestoreCommitted");
                productFlows.deliverPendingSharedImage();
            });
            restoreOwnership.workerFinished(delivered);
            if(!delivered||restoreOwnership.isCancelled())pendingProjectLoads.remove(restoreOwnership);
        })){startupRestoring=false;postUi(productFlows::deliverPendingSharedImage);}
    }
    /** Keep editor and draft payloads out of the Binder state transaction.
     * Preferences are updated in one transaction before handing lifecycle control back.
     */
    private LifecycleEditorSnapshot.Value lastPersistedEditor;
    private String lastPersistedSignature;
    private void persistLifecycleEditor(){
        final LifecycleEditorSnapshot.Value saved;
        try{saved=lifecycleEditorSnapshot.capture(projectSession.revision(),photoRevision,
            this::snapshot,()->startupRestoring?startupSavedDraft:draftJson());}
        catch(RuntimeException|OutOfMemoryError error){
            android.util.Log.e("VinylLibrary","Editor autosave failed; previous state retained",error);
            return;
        }
        // onPause, onSaveInstanceState and onStop all land here; the snapshot is cached, so
        // skip identical SharedPreferences writes (Android waits on queued applies in onStop).
        final String token=photo==null?"":photoPairToken(photoRevision);
        final String signature=currentLibraryId+"\u0000"+projectLabel+"\u0000"+projectSession.isDirty()+"\u0000"+
            chosenProfile+"\u0000"+(startupRestoring?startupSavedStep:workflowStep)+"\u0000"+designType+"\u0000"+
            lastStepInPhase[0]+","+lastStepInPhase[1]+","+lastStepInPhase[2]+"\u0000"+token;
        if(saved==lastPersistedEditor&&signature.equals(lastPersistedSignature))return;
        lastPersistedEditor=saved;lastPersistedSignature=signature;
        getPreferences(MODE_PRIVATE).edit().putString("editor",saved.editor)
            .putString("currentLibraryId",currentLibraryId)
            .putString("currentLibraryName",projectLabel)
            .putBoolean("editorDirty",projectSession.isDirty())
            .putString("draftSnipV22",saved.draft).putInt("sizeProfile",chosenProfile)
            .putString(PhotoPairToken.PREF,token)
            .putInt("workflowStep",startupRestoring?startupSavedStep:workflowStep).putInt("workflowType",designType)
            .putInt("lastPhaseStep0",lastStepInPhase[0])
            .putInt("lastPhaseStep1",lastStepInPhase[1])
            .putInt("lastPhaseStep2",lastStepInPhase[2]).apply();
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        productFlows.acceptSharedImage(intent);
    }
    @Override protected void onResume(){
        super.onResume();lifecycleEditorPaused=false;lifecycleEditorSnapshot.invalidate();
    }
    @Override protected void onPause(){
        lifecycleEditorPaused=true;lifecycleEditorSnapshot.invalidate();
        persistLifecycleEditor();super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle outState){
        if(!lifecycleEditorPaused)lifecycleEditorSnapshot.invalidate();
        persistLifecycleEditor();
        outState.putString(STATE_PENDING_LIBRARY_EXPORT,pendingLibraryExport.save());
        if(pendingCameraCapture!=null)outState.putString(STATE_PENDING_CAMERA,pendingCameraCapture.toString());
        outState.putInt(STATE_STEP,startupRestoring?startupSavedStep:workflowStep);
        outState.putBoolean(STATE_FULLSCREEN,canvasFullscreen);
        outState.putInt(STATE_TYPE,designType);
        super.onSaveInstanceState(outState);
    }
    @Override protected void onStop(){
        saveUiPreferences();persistLifecycleEditor();super.onStop();
    }
    private static String friendlyFailure(Throwable error,String fallback){
        return FailureText.describe(error,fallback);
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    // ---- One-dock productivity tools --------------------------------------------------
    private static final String[] SEARCH_LABELS={
        "Import photograph","Crop / size","Snip polygon","Light: exposure","Light: contrast",
        "Light: shadows","Layers / count","Art type / stencil","Patterns","Spacing",
        "Coverage","Contrast","Cyan strength","Vinyl colors","Layer stack",
        "Registration marks","Minimum cut width","Cutter check","Export SVG"
    };
    private static final String[] SEARCH_KEYS={
        "import","crop","snip","exposure","lightcontrast","shadows","layers","type","pattern",
        "spacing","coverage","contrast","curves","cyan","colors","stack","marks","minimum","check","export"
    };
    private void loadUiPreferences(){
        android.content.SharedPreferences prefs=getPreferences(MODE_PRIVATE);
        miniPanelEnabled=prefs.getBoolean("floatingControls",true);
        quickFavorites.clear();
        String saved=prefs.getString("quickTools","spacing,coverage,contrast,curves");
        for(String token:saved.split(",")){
            String id=token.trim();if(!id.isEmpty()&&!quickFavorites.contains(id))quickFavorites.add(id);
        }
    }
    private void saveUiPreferences(){
        getPreferences(MODE_PRIVATE).edit().putString("quickTools",android.text.TextUtils.join(",",quickFavorites))
            .putBoolean("floatingControls",miniPanelEnabled).apply();
    }
    private String shortControl(String id){switch(id){
        case "spacing":return "Spacing";case "coverage":return "Coverage";case "contrast":return "Contrast";
        case "curves":return "Curves";case "cyan":return "Cyan";case "marks":return "Marks";
        case "minimum":return "Cut width";case "exposure":return "Exposure";case "shadows":return "Shadows";
        default:return id;
    }}
    private void performHistoryAction(boolean redoAction){
        if(tool==8&&(redoAction?snipTimeline.redoCount():snipTimeline.undoCount())>0)
            snipHistory(redoAction);
        else history(redoAction);
    }
    private void refreshFavorites(){
        if(favoritesRow==null)return;
        String signature=(settings.mode==VinylEngine.CMYK&&settings.pattern==VinylEngine.LINE)+"/"+quickFavorites.toString();
        if(signature.equals(favoritesSignature)&&favoritesRow.getChildCount()>0)return;
        favoritesSignature=signature;
        favoritesRow.removeAllViews();
        quickPill("✦ 4C LINE",()->applyFourColorLinePreset(),settings.mode==VinylEngine.CMYK&&settings.pattern==VinylEngine.LINE);
        quickPill("Import",this::pick,false);
        quickPill("Styles",this::showHalftoneStylesDrawer,false);
        quickPill("Find",this::showControlSearch,false);
        
        quickPill("Full",()->setCanvasFullscreen(true),false);
        quickPill("Export",()->setTool(7),false);
        quickPill("Projects",v->showProjectLibrary(),false);
        for(String id:new ArrayList<>(quickFavorites)){
            TextView pill=quickPill("☆ "+shortControl(id),()->openControl(id),false);
            pill.setOnLongClickListener(v->{quickFavorites.remove(id);saveUiPreferences();refreshFavorites();return true;});
        }
        quickPill("+ Pin",this::showPinPicker,false);
    }
    private TextView quickPill(String caption,Runnable click,boolean active){
        return quickPill(caption,(View v)->click.run(),active);
    }
    /** Pass the tapped pill to menus so PopupMenu uses the correct anchor. */
    private TextView quickPill(String caption,View.OnClickListener click,boolean active){
        TextView pill=text(caption,12,active?0xff9DEAFF:TEXT,true);pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(14),0,dp(14),0);
        pill.setBackground(outlined(active?0xff1C4251:0xff26303D,active?0xff2EADBF:0xff3B4554,18));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(35));lp.rightMargin=dp(6);
        favoritesRow.addView(pill,lp);pill.setOnClickListener(click);return pill;
    }
    /** One-tap mode selection only changes geometry if the photo was using a different mode.
     * Tapping the active preset simply opens its Line controls, preserving adjustments. */
    private void applyFourColorLinePreset(){
        if(hasPendingSnip()){toast(getString(R.string.ui_apply_or_discard_the_current_snip_2d21cc));return;}
        if(settings.mode==VinylEngine.CMYK&&settings.pattern==VinylEngine.LINE){
            // The global style can still be overridden on every layer.
            boolean overridden=false;
            for(VinylEngine.Layer l:settings.layers)if(l.lineStyleOverride>=0){overridden=true;break;}
            if(!overridden){setTool(1);return;}
        }
        checkpoint();designType=0;settings.paletteId=-1;
        int[] colors={0xffffdd00,0xff00aeef,0xffec008c,0xff131313};
        String[] names={"Yellow","Cyan","Magenta","Black"};int[] channels={2,0,1,3};
        settings.layers.clear();
        for(int i=0;i<4;i++){VinylEngine.Layer l=new VinylEngine.Layer(names[i],colors[i]);l.cmykChannel=channels[i];l.enabled=true;
            l.strength=1f;settings.layers.add(l);}
        settings.mode=VinylEngine.CMYK;settings.anglePreset=VinylEngine.ANGLES_CLASSIC;
        float width=VinylEngine.toInches(settings.widthMm);
        float height=imageW>0?width*imageH/(float)imageW:width*1.25f;
        chosenProfile=VinylSizing.BALANCED;
        SmartHalftonePresets.apply(settings,VinylEngine.LINE,chosenProfile,width,height);
        settings.linkedControls=true;setLinkedValues();VinylEngine.applyAnglePreset(settings);
        chosen=-1;rebuildLayers();setTool(1);requestRender();
    }
    private void showPinPicker(){
        String[] choices={"Spacing","Coverage","Contrast","Cyan strength","Exposure","Shadows","Minimum cut width","Registration marks"};
        String[] ids={"spacing","coverage","contrast","cyan","exposure","shadows","minimum","marks"};
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_pin_a_control_up_to_four)).setItems(choices,(dialog,which)->{
            String id=ids[which];if(quickFavorites.contains(id)){quickFavorites.remove(id);}
            else if(quickFavorites.size()>=4){toast(getString(R.string.ui_long_press_a_favorite_to_free));return;}
            else quickFavorites.add(id);
            saveUiPreferences();refreshFavorites();
        }).setNegativeButton("Cancel",null).show();
    }
    private void showControlSearch(){
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(10),dp(16),dp(10));
        EditText query=new EditText(this);query.setSingleLine(true);query.setHint(getString(R.string.ui_find_spacing_curves_cyan));
        query.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        body.addView(query,new LinearLayout.LayoutParams(-1,dp(55)));
        ListView results=new ListView(this);body.addView(results,new LinearLayout.LayoutParams(-1,dp(310)));
        ArrayList<String> ids=new ArrayList<>();ArrayList<String> display=new ArrayList<>();
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,display);
        results.setAdapter(adapter);
        final Runnable populate=()->{
            ids.clear();display.clear();String needle=query.getText().toString().toLowerCase(Locale.US).trim();
            for(int i=0;i<SEARCH_KEYS.length;i++){
                if(needle.isEmpty()||SEARCH_LABELS[i].toLowerCase(Locale.US).contains(needle)
                    ||SEARCH_KEYS[i].contains(needle)){
                    ids.add(SEARCH_KEYS[i]);display.add(SEARCH_LABELS[i]);
                }
            }adapter.notifyDataSetChanged();
        };
        final AlertDialog dialog=new AlertDialog.Builder(this).setTitle(getString(R.string.ui_jump_to_control))
            .setView(body).setNegativeButton("Close",null).create();
        results.setOnItemClickListener((parent,v,position,id)->{
            if(position<ids.size()){String dest=ids.get(position);dialog.dismiss();openControl(dest);}
        });
        query.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){populate.run();}
            public void afterTextChanged(android.text.Editable s){}
        });
        populate.run();dialog.getWindow();dialog.show();
    }
    private void openControl(String id){
        if(canvasFullscreen)setCanvasFullscreen(false);
        // A draft snip may not be abandoned by jumping to another tool.
        switch(id){
            case "import":openDockTool(WorkflowFlow.IMPORT);return;
            case "crop":openDockTool(WorkflowFlow.CROP);return;
            case "snip":openDockTool(WorkflowFlow.SNIP);return;
            case "exposure":case "lightcontrast":case "shadows":
                pendingScrollTo=id.equals("lightcontrast")?"Contrast":shortControl(id);setTool(10);return;
            case "layers":openDockTool(WorkflowFlow.LAYERS);return;
            case "type":openDockTool(WorkflowFlow.TYPE);return;
            case "colors":case "cyan":pendingScrollTo=id.equals("cyan")?"Cyan":null;setTool(2);return;
            case "stack":openDockTool(11);return;
            case "curves":setTool(4);return;
            case "marks":openDockTool(WorkflowFlow.MARKS);return;
            case "minimum":pendingScrollTo="Minimum cut";setTool(6);return;
            case "check":setTool(6);return;
            case "export":openDockTool(WorkflowFlow.EXPORT);return;

            case "pattern":openDockTool(WorkflowFlow.TYPE);return;
            case "spacing":pendingScrollTo="Spacing";setTool(1);return;
            case "coverage":pendingScrollTo="Coverage";setTool(1);return;
            case "contrast":pendingScrollTo="Contrast";setTool(1);return;
        }
    }
    /** One horizontally scrollable row of pill tabs controls every tool's parameter groups. */
    private void decorateCollapsibleSections(){
        // RC126: show every relevant Adjust control in a vertical scroll. The old
        // double horizontal pill system hid line spacing, detail, and safety
        // settings, especially at larger system font sizes. Colors and Layers
        // were already vertical; preserve their behavior.
        if(activeWorkspace==2 || activeWorkspace==3 || activeWorkspace==4)return;
        final ArrayList<LinearLayout> panels=new ArrayList<>();
        final ArrayList<String> labels=new ArrayList<>();
        for(int i=0;i<sheetContent.getChildCount();i++){
            View child=sheetContent.getChildAt(i);
            if(!(child instanceof LinearLayout))continue;
            LinearLayout p=(LinearLayout)child;
            if(p.getChildCount()<2||!(p.getChildAt(0) instanceof TextView))continue;
            TextView head=(TextView)p.getChildAt(0);
            String heading=head.getText().toString().trim();
            if(heading.isEmpty())continue;
            // Heading is represented by a selectable pill, not duplicated inside the card.
            p.removeViewAt(0);
            if(p.getChildCount()>0 && !(p.getChildAt(0) instanceof TextView)){
                View spacer=p.getChildAt(0);
                if(spacer.getLayoutParams()!=null && spacer.getLayoutParams().height<=dp(8))p.removeViewAt(0);
            }
            // Remove lengthy explanatory copy from routine adjustments; preserve cut/export safety.
            if(tool!=6 && tool!=7){
                for(int j=p.getChildCount()-1;j>=0;j--){
                    View row=p.getChildAt(j);
                    if(row instanceof TextView){
                        TextView t=(TextView)row;
                        if(PlatformCompat.pxToSp(t.getTextSize(),getResources().getDisplayMetrics())<=12.5f
                                && t.getText().length()>115)p.removeViewAt(j);
                    }
                }
            }
            panels.add(p);labels.add(heading);
        }
        if(panels.isEmpty())return;
        String key=activeWorkspace+":"+tool+":"+workflowStep;
        String saved=selectedSettingPills.get(key);
        int preferred=0;
        if(tool==1||tool==4||tool==6||tool==10){
            for(int i=0;i<panels.size();i++){
                if(hasDirectSlider(panels.get(i))){preferred=i;break;}
            }
        }
        String initial=labels.contains(saved)?saved:labels.get(preferred);
        selectedSettingPills.put(key,initial);
        HorizontalScrollView rail=new HorizontalScrollView(this);
        rail.setHorizontalScrollBarEnabled(false);
        rail.setFillViewport(false);
        LinearLayout chips=new LinearLayout(this);chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chips.setPadding(dp(2),0,dp(2),dp(3));
        rail.addView(chips,new HorizontalScrollView.LayoutParams(-2,dp(45)));
        final ArrayList<TextView> buttons=new ArrayList<>();
        for(int i=0;i<panels.size();i++){
            final int which=i;
            TextView button=text(labels.get(i),13,TEXT,true);
            button.setGravity(Gravity.CENTER);
            button.setPadding(dp(15),0,dp(15),0);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(38));lp.rightMargin=dp(7);
            chips.addView(button,lp);buttons.add(button);
            button.setOnClickListener(v->{
                String next=labels.get(which);
                selectedSettingPills.put(key,next);
                for(int j=0;j<panels.size();j++){
                    boolean active=j==which;
                    panels.get(j).setVisibility(active?View.VISIBLE:View.GONE);
                    buttons.get(j).setBackground(outlined(active?SELECTED:StudioTheme.SURFACE_HIGH,active?ACCENT:StudioTheme.OUTLINE,20));
                    buttons.get(j).setTextColor(active?TEXT:MUTED);
                }
                sheetScroll.smoothScrollTo(0,0);
            });
        }
        for(int i=0;i<panels.size();i++){
            boolean active=labels.get(i).equals(initial);
            panels.get(i).setVisibility(active?View.VISIBLE:View.GONE);
            buttons.get(i).setBackground(outlined(active?SELECTED:StudioTheme.SURFACE_HIGH,active?ACCENT:StudioTheme.OUTLINE,20));
            buttons.get(i).setTextColor(active?TEXT:MUTED);
        }
        sheetContent.addView(rail,0,new LinearLayout.LayoutParams(-1,dp(47)));
        // Convert multiple visible sliders in each group into selectable parameters.
        // Existing SeekBars and their listeners are reused unchanged: no new rendering path.
        for(int i=0;i<panels.size();i++)compactSliders(panels.get(i),key+":"+labels.get(i));
    }
    private boolean hasDirectSlider(LinearLayout group){
        for(int i=0;i<group.getChildCount();i++)if(group.getChildAt(i) instanceof SeekBar)return true;
        return false;
    }
    private void compactSliders(LinearLayout group,String key){
        final ArrayList<LinearLayout> headings=new ArrayList<>();
        final ArrayList<SeekBar> sliders=new ArrayList<>();
        final ArrayList<String> names=new ArrayList<>();
        for(int i=1;i<group.getChildCount();i++){
            View child=group.getChildAt(i);
            if(!(child instanceof SeekBar))continue;
            View before=group.getChildAt(i-1);
            if(!(before instanceof LinearLayout))continue;
            LinearLayout heading=(LinearLayout)before;
            if(heading.getChildCount()<1||!(heading.getChildAt(0) instanceof TextView))continue;
            String title=((TextView)heading.getChildAt(0)).getText().toString().trim();
            if(title.isEmpty())continue;
            headings.add(heading);sliders.add((SeekBar)child);names.add(title);
        }
        if(sliders.size()<2)return;
        final String previous=selectedSliderPills.get(key);
        int start=names.indexOf(previous);
        if(start<0)start=0;
        final int initial=start;
        HorizontalScrollView chooser=new HorizontalScrollView(this);
        chooser.setHorizontalScrollBarEnabled(false);
        LinearLayout pills=new LinearLayout(this);pills.setOrientation(LinearLayout.HORIZONTAL);
        pills.setGravity(Gravity.CENTER_VERTICAL);
        chooser.addView(pills,new HorizontalScrollView.LayoutParams(-2,dp(38)));
        final ArrayList<TextView> choices=new ArrayList<>();
        for(int i=0;i<names.size();i++){
            final int selected=i;
            String label=names.get(i).replaceAll("^[0-9]+\\s*[·.]\\s*", "");
            TextView chip=text(label,12,TEXT,true);chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(12),0,dp(12),0);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(33));p.rightMargin=dp(6);
            pills.addView(chip,p);choices.add(chip);
            chip.setOnClickListener(v->{
                selectedSliderPills.put(key,names.get(selected));
                for(int j=0;j<sliders.size();j++){
                    boolean active=j==selected;
                    headings.get(j).setVisibility(active?View.VISIBLE:View.GONE);
                    sliders.get(j).setVisibility(active?View.VISIBLE:View.GONE);
                    choices.get(j).setBackground(outlined(active?SELECTED:StudioTheme.SURFACE_HIGH,
                        active?ACCENT:StudioTheme.OUTLINE,18));
                    choices.get(j).setTextColor(active?TEXT:MUTED);
                }
            });
        }
        selectedSliderPills.put(key,names.get(initial));
        for(int i=0;i<sliders.size();i++){
            boolean active=i==initial;
            headings.get(i).setVisibility(active?View.VISIBLE:View.GONE);
            sliders.get(i).setVisibility(active?View.VISIBLE:View.GONE);
            choices.get(i).setBackground(outlined(active?SELECTED:StudioTheme.SURFACE_HIGH,
                active?ACCENT:StudioTheme.OUTLINE,18));
            choices.get(i).setTextColor(active?TEXT:MUTED);
        }
        int insert=group.indexOfChild(headings.get(0));
        group.addView(chooser,Math.max(0,insert),new LinearLayout.LayoutParams(-1,dp(41)));
    }
    private boolean containsControlText(View v,String needle){
        if(v instanceof TextView && ((TextView)v).getText().toString().toLowerCase(Locale.US).contains(needle.toLowerCase(Locale.US)))return true;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(containsControlText(g.getChildAt(i),needle))return true;}
        return false;
    }
    private View findControlText(View v,String needle){
        if(v instanceof TextView&&((TextView)v).getText().toString().toLowerCase(Locale.US).contains(needle.toLowerCase(Locale.US)))return v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){
            View found=findControlText(g.getChildAt(i),needle);if(found!=null)return found;
        }}return null;
    }
    private void scrollToControl(String word){
        View target=findControlText(sheetContent,word);
        if(target==null){sheetScroll.scrollTo(0,0);return;}
        android.graphics.Rect rect=new android.graphics.Rect();target.getDrawingRect(rect);
        sheetContent.offsetDescendantRectToMyCoords(target,rect);
        sheetScroll.smoothScrollTo(0,Math.max(0,rect.top-dp(45)));
    }
    private void refreshMiniPanel(){/* Legacy hook; no controls are placed over the canvas. */}
    /** Adapt by available window width, not device model; keep the same CanvasView across rotations. */
    private void applyAdaptiveLayout(){
        if(editorArea==null)return;
        int widthDp=(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);
        boolean split=widthDp>=600;
        landscapeSplit=split;
        View adaptiveHeader=screen.findViewWithTag("editorHeader");
        if(adaptiveHeader!=null){ViewGroup.LayoutParams hp=adaptiveHeader.getLayoutParams();hp.height=dp(split?50:50);adaptiveHeader.setLayoutParams(hp);}
        ViewGroup.LayoutParams np=workflowScroller.getLayoutParams();np.height=dp(split?49:50);workflowScroller.setLayoutParams(np);
        if(sheetScroll!=null)sheetScroll.setVisibility(split||panelOpen?View.VISIBLE:View.GONE);
        if(subtoolScroller!=null)subtoolScroller.setVisibility(split||panelOpen?View.VISIBLE:View.GONE);
        editorArea.removeAllViews();
        if(split){
            int screenWidth=getResources().getDisplayMetrics().widthPixels;
            int controlsWidth=Math.min(dp(370),screenWidth*46/100);
            canvasColumn.setPadding(0,0,0,0);
            editorArea.addView(canvasColumn,new FrameLayout.LayoutParams(screenWidth-controlsWidth,-1,Gravity.START));
            editorArea.addView(controlsColumn,new FrameLayout.LayoutParams(controlsWidth,-1,Gravity.END));
            sheetHolder.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1));
            status.setVisibility(View.GONE);
        }else{
            // Keep the art visible: reserve exactly the current dock height.
            canvasColumn.setPadding(0,0,0,canvasFullscreen?0:dp(50)+(panelOpen?sheetMaxHeight():dp(DOCK_COLLAPSED_DP)));
            editorArea.addView(canvasColumn,new FrameLayout.LayoutParams(-1,-1,Gravity.TOP));
            editorArea.addView(controlsColumn,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
            sheetHolder.setLayoutParams(new LinearLayout.LayoutParams(-1,panelOpen?sheetMaxHeight():dp(DOCK_COLLAPSED_DP)));
            status.setVisibility(View.GONE);
        }
        screen.requestLayout();
        if(artwork!=null)artwork.invalidate();
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration newConfig){
        super.onConfigurationChanged(newConfig);applyAdaptiveLayout();
        if(screen!=null)screen.requestApplyInsets();
        if(canvasFullscreen)setCanvasFullscreen(true);
    }
    private void showQuickStartGuide(boolean fullHelp){
        showQuickStartGuide(fullHelp,true);
    }
    /** Menu launches open on the page for the current workspace; the first-run tour always starts at page 1. */
    private void showQuickStartGuide(boolean fullHelp,boolean fromMenu){
        VinylQuickStartGuide.show(this,getPreferences(MODE_PRIVATE),photo==null?this::loadTutorialSample:null,fullHelp,
            fromMenu&&photo!=null?activeWorkspace:-1,outputDeviceId,photo!=null,WORKSPACE_LABELS,this::selectWorkspace);
    }
    private void loadTutorialSample(){
        if(ProjectSaveGate.busy()){toast(getString(R.string.ui_finish_the_current_project_save_first));return;}
        if(photo!=null){toast(getString(R.string.ui_close_the_current_project_before_loading));return;}
        projectSession.invalidateLoads();cancelStartupRestore();
        Bitmap sample=VinylTutorialSample.create();
        NativeOriginalPhoto.clear(getFilesDir());restoredOriginalSource=null;originalPhoto=sample;projectLabel="Sample flower";
        encodedProjectOriginal=false;
        // Synchronous on purpose: must be durable before the encoded original is deleted.
        //noinspection ApplySharedPref
        getPreferences(MODE_PRIVATE).edit().putBoolean("encodedProjectOriginal",false).commit();
        scheduleDiskJob(()->ProjectEncodedOriginal.clear(getFilesDir()));
        float width=VinylEngine.toInches(settings.widthMm);
        SmartHalftonePresets.apply(settings,settings.pattern,chosenProfile,width,width*sample.getHeight()/(float)sample.getWidth());
        projectSession.markEdited();acceptCrop(new PhotoCropEditor.CropResult(sample,new float[]{0f,0f,1f,1f},false));
    }
    private void showSearchHelp(){
        LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        android.widget.EditText query=new android.widget.EditText(this); query.setSingleLine(true); query.setHint(getString(R.string.ui_search_help_e_g_marks)); content.addView(query);
        ScrollView scroll=new ScrollView(this); LinearLayout results=new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); scroll.addView(results); content.addView(scroll,new LinearLayout.LayoutParams(-1,dp(320)));
        Runnable refresh=()->{results.removeAllViews(); String q=query.getText().toString(); int count=0;
            int[] titles={R.string.guide_title_1,R.string.guide_title_2,R.string.guide_title_3,R.string.guide_title_4,R.string.guide_title_5,R.string.guide_title_6,R.string.guide_title_7,R.string.guide_title_8,R.string.guide_title_9,R.string.guide_title_10,R.string.guide_title_11,R.string.guide_title_12,R.string.guide_title_13,R.string.guide_title_14,R.string.guide_title_15}, bodies={R.string.guide_body_1,R.string.guide_body_2,R.string.guide_body_3,R.string.guide_body_4,R.string.guide_body_5,R.string.guide_body_6,R.string.guide_body_7,R.string.guide_body_8,R.string.guide_body_9,R.string.guide_body_10,R.string.guide_body_11,R.string.guide_body_12,R.string.guide_body_13,R.string.guide_body_14,R.string.guide_body_15}, tips={R.string.guide_tip_1,R.string.guide_tip_2,R.string.guide_tip_3,R.string.guide_tip_4,R.string.guide_tip_5,R.string.guide_tip_6,R.string.guide_tip_7,R.string.guide_tip_8,R.string.guide_tip_9,R.string.guide_tip_10,R.string.guide_tip_11,R.string.guide_tip_12,R.string.guide_tip_13,R.string.guide_tip_14,R.string.guide_tip_15};
            for(int i=0;i<titles.length;i++){int t=titles[i],b=bodies[i],tip=tips[i]; String title=getString(t),body=getString(b)+"\n"+getString(tip);
                if(!ContextualLearning.matches(q,title+" "+body))continue;count++;results.addView(text(title,15,TEXT,true));results.addView(text(body,13,TEXT,false));}
            if(count==0)results.addView(text(getString(R.string.ui_no_matching_help_try_a_shorter),13,TEXT,false));};
        query.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){refresh.run();}public void afterTextChanged(android.text.Editable e){}});refresh.run();
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_search_help)).setView(content).setPositiveButton("Close",null).show();
    }
    private void showCutterHandoff(){
        OutputDeviceProfile.Profile profile=OutputDeviceProfile.byId(outputDeviceId);
        new AlertDialog.Builder(this).setTitle(profile.handoffSoftware).setMessage(profile.instructions).setPositiveButton("Close",null).show();
    }
    private final ProjectReviewProgress projectReviewProgress=new ProjectReviewProgress();
    private void showProjectChecklist(){
        final AlertDialog[] checklistDialog=new AlertDialog[1];
        final long reviewToken=projectReviewProgress.revision();
        final boolean finalReady=photo!=null&&(alternateActive()?specialtyFinalReady():standardFinalReady());
        String[] tasks={"Style selected", "Detail reviewed", "Vinyl colors reviewed", "Physical layer order reviewed", "Cut Check reviewed"};
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(8),dp(16),dp(8));
        content.addView(text(photo!=null?"Photo loaded. Confirm each review yourself; rendering changes reset confirmations.":"Import a photo first.",14,TEXT,false));
        content.addView(text(photo==null?"Photo: not loaded":(alternateActive()?specialtyFinalReady():standardFinalReady())?"Photo loaded · final artwork ready":"Photo loaded · final artwork not ready",14,TEXT,true));
        for(int i=0;i<tasks.length;i++){
            final int index=i;LinearLayout row=horizontal(content);
            android.widget.CheckBox check=new android.widget.CheckBox(this);check.setText(tasks[i]);check.setChecked(projectReviewProgress.checked(i));check.setEnabled(photo!=null&&(i==0||finalReady));check.setMinHeight(dp(48));
            row.addView(check,new LinearLayout.LayoutParams(0,-2,1));check.setOnCheckedChangeListener((button,checked)->{if(!projectReviewProgress.confirm(reviewToken,index,checked))checklistDialog[0].dismiss();});
            TextView open=text("Open",14,ACCENT,true);open.setGravity(Gravity.CENTER);open.setContentDescription("Open workspace for "+tasks[i]);row.addView(open,new LinearLayout.LayoutParams(dp(56),dp(48)));open.setOnClickListener(v->{checklistDialog[0].dismiss();selectWorkspace(index+1);});
        }
        action(content,"Prepare / import photo",()->{checklistDialog[0].dismiss();selectWorkspace(0);});
        section(content,"Documents written for this revision");
        content.addView(text(projectReviewProgress.written(ProjectReviewProgress.CUT_ZIP)?"Cut/toolpath ZIP: written":"Cut/toolpath ZIP: not recorded",14,TEXT,false));
        content.addView(text(projectReviewProgress.written(ProjectReviewProgress.ASSEMBLY_PDF)?"Assembly PDF: written":"Assembly PDF: not recorded",14,TEXT,false));
        if(projectReviewProgress.written(ProjectReviewProgress.DIAGNOSTIC_ZIP))content.addView(text(getString(R.string.ui_inspection_zip_written_not_cut_qualified),12,MUTED,false));
        if(projectReviewProgress.written(ProjectReviewProgress.REFERENCE_PDF))content.addView(text(getString(R.string.ui_reference_pdf_written_not_an_alignment),12,MUTED,false));
        action(content,"Open Export",()->{checklistDialog[0].dismiss();selectWorkspace(5);});
        content.addView(text(getString(R.string.ui_session_only_progress_documents_written_and),12,MUTED,false));
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        checklistDialog[0]=new AlertDialog.Builder(this).setTitle(getString(R.string.ui_project_review_checklist)).setView(scroll)
            .setNeutralButton("Reset",(dialog,which)->{projectReviewProgress.invalidate();showProjectChecklist();})
            .setPositiveButton("Close",null).show();
    }
    private void showProjectMenu(View anchor){
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add("Project library");menu.getMenu().add("Save to library");
        menu.getMenu().add("Export project ZIP");menu.getMenu().add("Import project ZIP");
        menu.getMenu().add("Search help");menu.getMenu().add("Project checklist");
        menu.getMenu().add("Preview mode");menu.getMenu().add(getString(R.string.guide_user_menu));
        menu.getMenu().add(getString(R.string.guide_menu));
        menu.getMenu().add(getString(VinylQuickStartGuide.tipsEnabled(getPreferences(MODE_PRIVATE))?R.string.guide_tip_toggle_on:R.string.guide_tip_toggle_off));
        menu.getMenu().add(getString(R.string.privacy_about_menu));
        menu.getMenu().add("Copy last crash report");
        menu.getMenu().add("Save as new project");
        menu.getMenu().add("Exit project");
        menu.setOnMenuItemClickListener(item->{String name=item.getTitle().toString();
            if(name.equals("Project library"))showProjectLibrary();
            else if(name.equals("Save to library"))saveCurrentProject();
            else if(name.equals("Save as new project"))promptProjectName(true,null);
            else if(name.equals("Export project ZIP"))chooseProject(true);
            else if(name.equals("Import project ZIP"))chooseProject(false);
            else if(name.equals("Search help"))showSearchHelp();
            else if(name.equals("Project checklist"))showProjectChecklist();
            else if(name.equals("Preview mode"))showPreviewMenu(anchor);
            else if(name.equals(getString(R.string.privacy_about_menu)))showPrivacyAndAbout();
            else if(name.equals("Copy last crash report")){
                String report=VinylCrashReport.read(getApplicationContext());
                if(report==null)toast(getString(R.string.ui_no_recorded_java_crash_on_this));
                else productFlows.showCrashReportActions(report,false);
            }
            else if(name.equals(getString(R.string.guide_user_menu)))showQuickStartGuide(false);
            else if(name.equals(getString(R.string.guide_menu)))showQuickStartGuide(true);
            else if(name.equals(getString(R.string.guide_tip_toggle_on))||name.equals(getString(R.string.guide_tip_toggle_off))){
                android.content.SharedPreferences prefs=getPreferences(MODE_PRIVATE);
                VinylQuickStartGuide.setTips(prefs,!VinylQuickStartGuide.tipsEnabled(prefs));
            }
            else if(name.equals("Exit project"))confirmExitProject();
            return true;});menu.show();
    }
    /** Offline privacy text is always available; release builds additionally include a live URL. */
    private void showPrivacyAndAbout(){
        ScrollView scroller=new ScrollView(this);
        TextView policy=text(getString(R.string.privacy_about_text),14,TEXT,false);
        policy.setPadding(dp(18),dp(14),dp(18),dp(14));
        scroller.addView(policy);
        AlertDialog.Builder dialog=new AlertDialog.Builder(this)
            .setTitle(R.string.privacy_about_title).setView(scroller)
            .setPositiveButton(R.string.guide_close,null);
        final String url=getString(R.string.privacy_policy_url).trim();
        if(url.startsWith("https://")){
            dialog.setNeutralButton(R.string.privacy_policy_view_online,(ignored,which)->{
                try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
                catch(android.content.ActivityNotFoundException missing){toast(getString(R.string.no_web_browser_available));}
            });
        }
        dialog.show();
    }
    private void showPreviewMenu(View anchor){
        PopupMenu menu=new PopupMenu(this,anchor);
        String[] modes={"Vinyl colors","Black cut lines","Single layer","Overlay photo"};
        for(int i=0;i<modes.length;i++){
            final int mode=i;menu.getMenu().add(modes[i]).setCheckable(true).setChecked(previewMode==i)
                .setOnMenuItemClickListener(item->{previewMode=mode;
                    if(mode==2&&chosen<0&&settings.layers.size()>0){chosen=0;rebuildLayers();}
                    if(mode!=2&&chosen>=0){chosen=-1;rebuildLayers();}
                    artwork.invalidate();return true;});
        }menu.show();
    }
    private void setRenderBusy(boolean busy){
        renderBusy=busy;
        if(renderProgress!=null)renderProgress.setVisibility(busy?View.VISIBLE:View.GONE);
        updateEditorSubtitle();
    }
    private void updateEditorSubtitle(){
        if(editorSubtitle==null)return;
        if(renderBusy){editorSubtitle.setText(R.string.quality_rendering);return;}
        if(photo==null){editorSubtitle.setText(R.string.quality_new_project);return;}
        String info=projectLabel;
        if(chosen>=0&&chosen<settings.layers.size())info=settings.layers.get(chosen).name;
        else if(newStyleActive())info=VinylStyleSuite.NAMES[newStyleId];
        else if(tonalStencilMode)info="Multilayer stencil";
        else if(layeredCellMode)info=overprintCellKind>0?LayeredCellPortraitEngine.overprintName(overprintCellKind):fishScaleMode?"Fish Scale Mosaic Portrait":bottleCapMode?"Bottle Cap Mosaic Portrait":offsetCellMode?"Offset Cell Portrait":"Layered Cell Portrait";
        else if(tessellationMode)info="Tessellation portrait";
        else if(mazePortraitMode)info="Maze portrait";
        else if(continuousStyle!=null)info=continuousStyle.toString().replace('_',' ');
        editorSubtitle.setText(projectSession.isDirty()?getString(R.string.editor_subtitle_unsaved,info):info);
        if(artwork!=null)artwork.setContentDescription(getString(R.string.canvas_description,info));
    }
    private void refreshChannelOverlay(){
        if(channelOverlay==null)return;
        channelOverlay.removeAllViews();
        boolean available=false; // Layer visibility belongs exclusively to Layers.
        channelOverlay.setVisibility(available?View.VISIBLE:View.GONE);
        if(!available)return;
        String[] channels={"C","M","Y","K"};int[] colors={0xff00D7E9,0xffE65CB1,0xffEBD053,0xffECEEF2};
        for(int i=0;i<4;i++){
            final int channel=i;int index=-1;
            for(int j=0;j<4;j++)if(settings.layers.get(j).cmykChannel==channel){index=j;break;}
            if(index<0)continue;
            final int at=index;VinylEngine.Layer layer=settings.layers.get(at);
            TextView dot=text(channels[i],13,colors[i],true);dot.setGravity(Gravity.CENTER);
            dot.setBackground(outlined(0xff233040,layer.enabled?colors[i]:0xff515B68,18));
            dot.setAlpha(layer.enabled?1f:.4f);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(32),dp(30));lp.rightMargin=dp(5);
            channelOverlay.addView(dot,lp);
            dot.setContentDescription(channels[i]+" layer, "+(layer.enabled?"shown":"hidden")+". Tap to toggle");
            dot.setOnClickListener(v->{checkpoint();layer.enabled=!layer.enabled;rebuildLayers();requestRender();});
        }
    }
    private void setCanvasFullscreen(boolean fullscreen){
        canvasFullscreen=fullscreen;
        // Keep header and its undo / redo / full-screen toggle accessible in both modes.
        View header=screen.findViewWithTag("editorHeader");if(header!=null)header.setVisibility(View.VISIBLE);
        controlsColumn.setVisibility(fullscreen?View.GONE:View.VISIBLE);
        canvasColumn.setPadding(0,0,0,fullscreen||landscapeSplit?0:dp(50)+(panelOpen?sheetMaxHeight():dp(DOCK_COLLAPSED_DP)));
        workflowScroller.setVisibility(fullscreen?View.GONE:View.VISIBLE);
        layerScroller.setVisibility(fullscreen?View.GONE:View.VISIBLE);
        status.setVisibility(View.GONE);
        canvasHint.setVisibility(fullscreen?View.GONE:((workflowStep==WorkflowFlow.SNIP||workflowStep==WorkflowFlow.MARKS)?View.VISIBLE:View.GONE));
        fullScreenHandle.setVisibility(fullscreen?View.VISIBLE:View.GONE);
        if(miniPanel!=null)miniPanel.setVisibility(View.GONE); // No floating editor controls.
        if(Build.VERSION.SDK_INT>=30){
            WindowInsetsController controller=getWindow().getInsetsController();
            if(controller!=null){
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                if(fullscreen)controller.hide(WindowInsets.Type.navigationBars());
                else controller.show(WindowInsets.Type.navigationBars());
            }
        }else{
            // API 26–29 fallback: WindowInsetsController was introduced in API 30.
            PlatformCompat.legacyHideNavigation(getWindow().getDecorView(),fullscreen);
        }
        screen.requestLayout();
    }
    private void handleEditorBack(){
        if(canvasFullscreen){setCanvasFullscreen(false);return;}
        if(panelOpen){collapseToolSheet();return;}
        confirmExitProject();
    }
    private void installSystemBackCallback(){
        if(Build.VERSION.SDK_INT>=33)systemBackCallback=BackApi33.register(this);
    }
    /** Keep API-33 classes out of the Activity's field signature on API 26–32. */
    @androidx.annotation.RequiresApi(33)
    private static final class BackApi33 {
        static OnBackInvokedCallback register(StudioActivity activity){
            OnBackInvokedCallback callback=activity::handleEditorBack;
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,callback);
            return callback;
        }
        static void unregister(StudioActivity activity,Object token){
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                (OnBackInvokedCallback)token);
        }
    }
    @SuppressWarnings("deprecation") // API 33+ uses the registered OnBackInvokedCallback instead.
    @Override public void onBackPressed(){
        // Pre-Android-13 devices still dispatch through the legacy activity method.
        handleEditorBack();
    }
    // URI-based recent projects: these are persisted SAF document grants, not duplicated private photos.
    private void rememberProject(Uri uri){
        if(uri==null)return;
        android.content.SharedPreferences prefs=getPreferences(MODE_PRIVATE);
        String updated=RecentProjectUris.remember(uri.toString(),prefs.getString("recentProjectUris",""));
        prefs.edit().putString("recentProjectUris",updated).apply();
        DocumentAccess.releaseUnusedProjectGrants(getContentResolver(),updated);
    }
    private final class CurveView extends View {
        Paint pen=new Paint(3);private final Path curvePath=new Path();int dragging=-1;float pad=dp(24);CurveView(){super(StudioActivity.this);setBackground(shape(0xff1C222B,13));setContentDescription(getString(R.string.accessibility_curve));setFocusable(true);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth()-2*pad,h=getHeight()-2*pad;pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(dp(1));pen.setColor(0xff364454);for(int j=0;j<5;j++){float x=pad+j*w/4,y=pad+j*h/4;c.drawLine(x,pad,x,pad+h,pen);c.drawLine(pad,y,pad+w,y,pen);}
            pen.setColor(ACCENT);pen.setStrokeWidth(dp(2.6f));curvePath.reset();float[] cv=layer().curve;for(int j=0;j<5;j++){float x=pad+j*w/4,y=pad+(1-cv[j])*h;if(j==0)curvePath.moveTo(x,y);else curvePath.lineTo(x,y);}c.drawPath(curvePath,pen);pen.setStyle(Paint.Style.FILL);for(int j=0;j<5;j++){float x=pad+j*w/4,y=pad+(1-cv[j])*h;pen.setColor(0xff263C5B);c.drawCircle(x,y,dp(10),pen);pen.setColor(ACCENT);c.drawCircle(x,y,dp(6),pen);}pen.setStyle(Paint.Style.STROKE);
        }
        @Override public boolean onTouchEvent(MotionEvent e){float w=getWidth()-2*pad,h=getHeight()-2*pad;switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:{checkpoint();dragging=Math.max(0,Math.min(4,Math.round((e.getX()-pad)/w*4)));if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);}
            case MotionEvent.ACTION_MOVE:if(dragging>=0){layer().curve[dragging]=Math.max(0,Math.min(1,1-(e.getY()-pad)/h));invalidate();requestRender();}return true;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:dragging=-1;if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;
            default:return true;}
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
    private final class PatternSample extends View {
        final int kind;private final Path samplePath=new Path();Paint p=new Paint(3);PatternSample(int k){super(StudioActivity.this);kind=k;}
        @Override protected void onDraw(Canvas c){p.setColor(0xffa4c7ed);p.setStyle(Paint.Style.FILL);for(int y=0;y<3;y++)for(int x=0;x<5;x++){float cx=(x+.5f)*getWidth()/5f,cy=(y+.5f)*getHeight()/3f,r=dp(2f)+x*dp(.35f);if(LineArtEngine.isLineStyle(kind)&&kind!=VinylEngine.LINE&&kind!=VinylEngine.HATCH&&kind!=VinylEngine.WAVE){
                if(y==0&&x==0){
                    p.setColor(0xffa4c7ed);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2.6f));
                    float left=dp(5),right=getWidth()-dp(5),middle=getHeight()*.45f;
                    if(kind==VinylEngine.RINGS){for(int a=1;a<=3;a++)c.drawCircle(getWidth()*.47f,middle,a*dp(8),p);}
                    else if(kind==VinylEngine.RAYS){for(int a=0;a<7;a++){double th=a*2*Math.PI/7;c.drawLine(getWidth()*.48f,middle,getWidth()*.48f+dp(27)*(float)Math.cos(th),middle+dp(22)*(float)Math.sin(th),p);}}
                    else if(kind==VinylEngine.BEADED){p.setStyle(Paint.Style.FILL);for(int a=0;a<7;a++)c.drawCircle(left+a*(right-left)/7,middle,dp(2+a%3),p);}
                    else {Path path=samplePath;path.rewind();path.moveTo(left,middle);
                        for(int a=1;a<=18;a++){float xx=left+(right-left)*a/18;
                            float yy=middle;
                            if(kind==VinylEngine.CONTOUR)yy+=dp(5)*(float)Math.sin(a*.35);
                            if(kind==VinylEngine.ZIGZAG)yy+=(a%2==0?dp(6):-dp(6));
                            if(kind==VinylEngine.BROKEN&&a%5==0){c.drawPath(path,p);path.rewind();path.moveTo(xx,yy);}else path.lineTo(xx,yy);
                        }c.drawPath(path,p);
                        if(kind==VinylEngine.CROSSHATCH){c.drawLine(left,middle+dp(9),right,middle-dp(9),p);}
                    }
                    p.setStyle(Paint.Style.FILL);
                }continue;
            }
            if(kind==VinylEngine.WAVE){if(y==0&&x==0){Path wave=samplePath;wave.rewind();for(int col=0;col<5;col++){float u=col*getWidth()/4f;float vv=getHeight()*.43f+dp(4)*(float)Math.sin(col*1.5);if(col==0)wave.moveTo(u,vv);else wave.lineTo(u,vv);}p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(3));c.drawPath(wave,p);p.setStyle(Paint.Style.FILL);}continue;}if(kind==VinylEngine.LINE||kind==VinylEngine.HATCH){if(y==0&&x==0){Path line=samplePath;line.rewind();float left=dp(4),right=getWidth()-dp(4),yc=getHeight()*.48f;line.moveTo(left,yc-dp(1));line.cubicTo(right*.3f,yc-dp(1),right*.55f,yc-dp(5),right,yc-dp(6));line.lineTo(right,yc+dp(6));line.cubicTo(right*.55f,yc+dp(5),right*.3f,yc+dp(1),left,yc+dp(1));line.close();c.drawPath(line,p);}continue;}if(kind==VinylEngine.WORD_SCREEN){
                    if(y==1&&x==0){p.setTextSize(dp(11));p.setTypeface(android.graphics.Typeface.MONOSPACE);
                        p.setStyle(Paint.Style.FILL);c.drawText("WORD",dp(2),getHeight()*.53f,p);}continue;}
                if(kind==VinylEngine.ASCII){
                    p.setTypeface(android.graphics.Typeface.MONOSPACE);p.setTextSize(dp(12));
                    c.drawText(ASCII_SAMPLE_GLYPHS[(x+y*2)%5],cx-dp(4),cy+dp(4),p);
                }else if(kind==VinylEngine.CROSS){float rr=r*.38f;c.drawRect(cx-rr,cy-r,cx+rr,cy+r,p);c.drawRect(cx-r,cy-rr,cx+r,cy+rr,p);}else if(kind==VinylEngine.STAR){Path star=samplePath;star.rewind();for(int j=0;j<10;j++){double a=-Math.PI/2+j*Math.PI/5;float rad=(j%2==0?r:r*.45f);float px=cx+(float)Math.cos(a)*rad,py=cy+(float)Math.sin(a)*rad;if(j==0)star.moveTo(px,py);else star.lineTo(px,py);}star.close();c.drawPath(star,p);}else if(kind==1)c.drawRect(cx-r,cy-r,cx+r,cy+r,p);else if(kind==2)c.drawOval(cx-r,cy-r*.6f,cx+r,cy+r*.6f,p);else if(kind==3){Path d=samplePath;d.rewind();d.moveTo(cx,cy-r);d.lineTo(cx+r,cy);d.lineTo(cx,cy+r);d.lineTo(cx-r,cy);d.close();c.drawPath(d,p);}else if(kind==4||kind==5)c.drawRoundRect(cx-r,cy-r*.45f,cx+r,cy+r*.45f,r*.35f,r*.35f,p);else if(kind==6||kind==VinylEngine.TRIANGLE||kind==VinylEngine.OCTAGON){
                    int sides=kind==VinylEngine.TRIANGLE?3:kind==VinylEngine.OCTAGON?8:6;
                    Path poly=samplePath;poly.rewind();for(int j=0;j<sides;j++){double t=-Math.PI/2+j*2*Math.PI/sides;float xx=cx+r*(float)Math.cos(t),yy=cy+r*(float)Math.sin(t);if(j==0)poly.moveTo(xx,yy);else poly.lineTo(xx,yy);}poly.close();c.drawPath(poly,p);
                }else if(kind==VinylEngine.ROUNDED_SQUARE)c.drawRoundRect(cx-r,cy-r,cx+r,cy+r,r*.32f,r*.32f,p);
                else if(kind==VinylEngine.CAPSULE)c.drawRoundRect(cx-r,cy-r*.58f,cx+r,cy+r*.58f,r*.58f,r*.58f,p);
                else if(kind==VinylEngine.TEARDROP){Path tip=samplePath;tip.rewind();tip.moveTo(cx,cy-r);tip.cubicTo(cx+r*.9f,cy-r*.4f,cx+r,cy+r*.8f,cx,cy+r);tip.cubicTo(cx-r,cy+r*.8f,cx-r*.9f,cy-r*.4f,cx,cy-r);tip.close();c.drawPath(tip,p);}
                else if(kind==VinylEngine.HEART){Path love=samplePath;love.rewind();love.moveTo(cx,cy+r);love.cubicTo(cx-r*1.4f,cy+r*.1f,cx-r,cy-r*.9f,cx,cy-r*.3f);love.cubicTo(cx+r,cy-r*.9f,cx+r*1.4f,cy+r*.1f,cx,cy+r);love.close();c.drawPath(love,p);}
                else c.drawCircle(cx,cy,r,p);}}
    }
    /** Immutable geometry snapshot; built by the vector worker, never from onDraw().
     * All paths are built at source physical coordinates, one per layer.
     * Each path uses even/odd compound contours and contains its own circles.
     */
    private static final class CanvasGeometry {
        final VinylEngine.Result source;
        final Path[] paths;
        private CanvasGeometry(VinylEngine.Result r,Path[] p){source=r;paths=p;}
        static CanvasGeometry create(VinylEngine.Result result){
            Path[] paths=new Path[result.shapes.size()];
            for(int li=0;li<paths.length;li++){
                if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
                // Nonzero winding merges overlapping same-color pieces without
                // parity holes; inner contours are oriented opposite to outers.
                Path whole=new Path();whole.setFillType(result.settings.layers.get(li).silhouetteBase?Path.FillType.EVEN_ODD:Path.FillType.WINDING);
                int shapeNo=0;
                for(VinylEngine.Shape shape:result.shapes.get(li)){
                    if((shapeNo++&63)==0&&Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
                    if(shape.circle){whole.addCircle(shape.x,shape.y,shape.r,Path.Direction.CW);continue;}
                    float[][] rings=shape.contours==null?new float[][]{shape.xy}:shape.contours;
                    for(int ringNo=0;ringNo<rings.length;ringNo++){
                        float[] ring=rings[ringNo];
                        double area=0;
                        for(int v=0;v<ring.length;v+=2){
                            int next=(v+2)%ring.length;
                            area+=(double)ring[v]*ring[next+1]-(double)ring[next]*ring[v+1];
                        }
                        boolean clockwise=area>0;
                        boolean shouldClockwise=(ringNo==0);
                        int start=clockwise==shouldClockwise?0:ring.length-2;
                        int step=clockwise==shouldClockwise?2:-2;
                        for(int v=0;v<ring.length;v+=2){
                            int index=(start+v*step/2+ring.length)%ring.length;
                            if(v==0)whole.moveTo(ring[index],ring[index+1]);
                            else whole.lineTo(ring[index],ring[index+1]);
                        }
                        whole.close();
                    }
                }
                paths[li]=whole;
            }
            return new CanvasGeometry(result,paths);
        }

    }
    private volatile CanvasGeometry canvasGeometry;
    private final class CanvasView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        // Path effects are hardware accelerated on Android 9+; show equivalent solid
        // cut boundaries on API 26/27 rather than forcing all canvas frames to CPU.
        private final DashPathEffect marginDashes=new DashPathEffect(new float[]{1f,1f},0);
        private final DashPathEffect snipDashes=new DashPathEffect(new float[]{1.3f,.9f},0);
        private final RectF artworkRect=new RectF(),photoRect=new RectF();
        private final Path draftSnipPath=new Path();
        private final CheckerboardPainter prepareChecker=new CheckerboardPainter(0xffE7EAEE,0xffCCD2D9);
        private final CheckerboardPainter snipChecker=new CheckerboardPainter(0xffC9D0D7,0xffE3E7EB);
        private boolean transforming=false;
        // Transient, low-memory snapshot for panning/zooming complex cut vectors.
        // It is discarded after the gesture. Never used for SVG export or final idle view.
        private Bitmap gestureSnapshot=null;
        private float gestureSnapshotZoom,gestureSnapshotPanX,gestureSnapshotPanY;
        private final RectF gestureSnapshotDest=new RectF();
        private boolean capturingGestureSnapshot=false;
        private void releaseGestureSnapshot(){
            Bitmap old=gestureSnapshot;gestureSnapshot=null;
            if(old!=null&&!old.isRecycled())old.recycle();
        }
        private void captureGestureSnapshot(){
            releaseGestureSnapshot();
            if(activeWorkspace==0||tool==5||tool==8||showOriginal||holdCompare)return;
            if(getWidth()<2||getHeight()<2)return;
            if(alternateActive()&&alternateReadyGeneration!=alternateGeneration)return;
            // Never allocate a full-screen bitmap just for a quick tap if there is
            // no completed vector geometry to accelerate.
            if(newStyleVectors==null&&specialtyVectorCache==null&&stippleVectorCache==null
                    &&canvasGeometry==null&&continuousPreview==null)return;
            int ww=getWidth(),hh=getHeight();
            float factor=Math.min(1f,1600f/Math.max(ww,hh));
            int bw=Math.max(1,Math.round(ww*factor));
            int bh=Math.max(1,Math.round(hh*factor));
            Bitmap snapshot=null;
            try{
                snapshot=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888);
                Canvas offscreen=new Canvas(snapshot);
                offscreen.scale(bw/(float)ww,bh/(float)hh);
                capturingGestureSnapshot=true;
                try{draw(offscreen);}
                finally{capturingGestureSnapshot=false;}
                gestureSnapshot=snapshot;
                gestureSnapshotZoom=zoom;
                gestureSnapshotPanX=panX;
                gestureSnapshotPanY=panY;
            }catch(OutOfMemoryError|RuntimeException failure){
                // A slower final vector render is safer than a failed gesture.
                if(snapshot!=null&&snapshot!=gestureSnapshot&&!snapshot.isRecycled())snapshot.recycle();
                releaseGestureSnapshot();capturingGestureSnapshot=false;
            }
        }
        private void drawGestureSnapshot(Canvas cv){
            cv.drawColor(CANVAS);
            float centerX=getWidth()*.5f,centerY=getHeight()*.5f;
            cv.save();
            cv.translate(centerX+panX,centerY+panY);
            cv.scale(zoom/Math.max(.001f,gestureSnapshotZoom),
                     zoom/Math.max(.001f,gestureSnapshotZoom));
            cv.translate(-centerX-gestureSnapshotPanX,-centerY-gestureSnapshotPanY);
            gestureSnapshotDest.set(0,0,getWidth(),getHeight());
            paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);
            cv.drawBitmap(gestureSnapshot,null,gestureSnapshotDest,paint);
            cv.restore();
        }
        private final android.view.ScaleGestureDetector scaler;
        private final android.view.GestureDetector taps;
        private float zoom=1,panX=0,panY=0,lastX,lastY;
        private float scale=1,originX,originY;
        private int draggedMark=-1,snipVertex=-1;
        private float snipDownX,snipDownY,fullSwipeStartY=-1f;
        private boolean snipMultitouch=false,dragWasRecorded=false,ignoreFingerSnip=false;
        private int snipPointerId=MotionEvent.INVALID_POINTER_ID;
        private float midpointX,midpointY,hoverX=-1,hoverY=-1;
        private long stylusNearbyUntil=0;
        private float[] originalVertex=null;
        private float[][] originalMarks=null;
        private boolean marksStartedDefault=false;
        private int activeToolType=MotionEvent.TOOL_TYPE_UNKNOWN;
        private boolean stylusActive=false,holdCompare=false;
        private final int touchSlop=android.view.ViewConfiguration.get(StudioActivity.this).getScaledTouchSlop();
        CanvasView(){super(StudioActivity.this);
            scaler=new android.view.ScaleGestureDetector(StudioActivity.this,new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScale(android.view.ScaleGestureDetector d){
                    float old=zoom;zoom=ViewportMath.bounded(zoom*d.getScaleFactor());
                    panX=ViewportMath.zoomPan(panX,getWidth()/2f,d.getFocusX(),old,zoom);
                    panY=ViewportMath.zoomPan(panY,getHeight()/2f,d.getFocusY(),old,zoom);
                    clamp();syncZoomLabel();invalidate();return true;
                }
            });
            taps=new android.view.GestureDetector(StudioActivity.this,new android.view.GestureDetector.SimpleOnGestureListener(){
                @Override public boolean onDown(MotionEvent e){return true;}
                @Override public void onLongPress(MotionEvent e){
                    if(tool!=8&&draggedMark<0){holdCompare=true;invalidate();}
                }
                @Override public boolean onDoubleTap(MotionEvent e){resetZoom();return true;}});
        }
        void resetZoom(){zoom=1;panX=panY=0;marksViewportPad=-1f;syncZoomLabel();invalidate();}
        float zoomForTest(){return zoom;}
        void zoomToForTest(float z){zoom=z;syncZoomLabel();invalidate();}
        private String zoomLabel(float value){
            int tenths=Math.max(0,Math.round(value*10f));
            return (tenths/10)+"."+(tenths%10);
        }
        private void syncZoomLabel(){
            if(zoomShortcut!=null)zoomShortcut.setText(getString(R.string.zoom_percent,Math.round(zoom*100f)));
        }
        private void clamp(){panX=ViewportMath.limitPan(panX,zoom,getWidth());panY=ViewportMath.limitPan(panY,zoom,getHeight());}
        private void setupTransform(float ww,float hh){scale=Math.min((getWidth()-dp(25))/ww,(getHeight()-dp(28))/hh);if(scale<0)scale=1;originX=(getWidth()-ww*scale)/2f;originY=(getHeight()-hh*scale)/2f;}
        // Every renderer publishes the physical artwork rectangle used by its Canvas.
        // Hit testing MUST NOT consult `latest`: it may be a different engine or stale.
        private float viewedArtW=0f,viewedArtH=0f,viewedPad=0f;
        private float markDragFixedPad=-1f;
        private float marksViewportPad=-1f;
        private void clearMarksViewportPad(){marksViewportPad=-1f;}
        private void setArtworkTransform(float artW,float artH,float pad){
            viewedArtW=artW;viewedArtH=artH;viewedPad=pad;
            setupTransform(artW+2*pad,artH+2*pad);
        }
        private float previewMarkPad(float artW,float artH,float pad){
            // Do not change scale/origin underneath the finger while dragging a mark.
            if(draggedMark>=0&&markDragFixedPad>=0f)return markDragFixedPad;
            // The mark can extend the physical export sheet, but must not resize
            // or shift the photo/geometry under the finger or after release.
            if(tool==5&&marksViewportPad>=0f)return marksViewportPad;
            if(tool!=5)marksViewportPad=-1f;
            if(!settings.registration)return pad;
            // The preview is an ARTWORK viewport, not a physical sheet layout.
            // Custom marks extend the exported sheet, but moving one must not
            // zoom, shift or rescale the portrait (even after gesture release).
            return RegistrationViewportMath.stablePreviewPadding(artW,artH,
                settings.markSizeMm,settings.registrationClearanceMm,pad);
        }
        private float[] viewToArtwork(float screenX,float screenY){
            float xx=ViewportMath.toContent(screenX,getWidth()/2f,panX,zoom);
            float yy=ViewportMath.toContent(screenY,getHeight()/2f,panY,zoom);
            return new float[]{(xx-originX)/Math.max(.0001f,scale)-viewedPad,
                (yy-originY)/Math.max(.0001f,scale)-viewedPad};
        }
        private float gestureWidth(){return activeWorkspace==0?prepareWidth():viewedArtW>0?viewedArtW:settings.widthMm;}
        private float gestureHeight(){return activeWorkspace==0?prepareHeight():viewedArtH>0?viewedArtH:prepareHeight();}
        // One visual overlay for all cuttable previews; the exact same physical
        // four centers are reused by SVG export regardless of selected layer.
        private final Path[] markPaths={new Path(),new Path(),new Path(),new Path()};
        private int cachedMarkHash=Integer.MIN_VALUE,cachedMarkStyle=-1;
        private String cachedMarkPlacementIssue;
        private float cachedMarkW=-1,cachedMarkH=-1,cachedMarkSize=-1,cachedMarkMin=-1;
        private float cachedMarkClearance=-1;
        private float[][] cachedDefaultMarks;
        private float defaultMarkW=-1,defaultMarkH=-1,defaultMarkSize=-1,defaultMarkClearance=-1;
        private boolean defaultMarkCircleCrop=false;
        private float[][] displayedMarks(float w,float h){
            if(settings.marks!=null)return settings.marks;
            if(cachedDefaultMarks==null||w!=defaultMarkW||h!=defaultMarkH||
                    settings.markSizeMm!=defaultMarkSize||settings.registrationClearanceMm!=defaultMarkClearance||
                    settings.circleCrop!=defaultMarkCircleCrop){
                defaultMarkW=w;defaultMarkH=h;defaultMarkSize=settings.markSizeMm;
                defaultMarkClearance=settings.registrationClearanceMm;
                defaultMarkCircleCrop=settings.circleCrop;
                cachedDefaultMarks=RegistrationMarks.automaticPositions(w,h,defaultMarkSize,defaultMarkClearance,settings.circleCrop);
            }
            return cachedDefaultMarks;
        }
        private void drawRegistrationOverlay(Canvas cv,float artW,float artH){
            if(!settings.registration||tool==8)return;
            float[][] marks=displayedMarks(artW,artH);
            float minCut=Math.min(settings.minimumMm,settings.markSizeMm*.9f);
            int hash=java.util.Arrays.deepHashCode(marks);
            if(hash!=cachedMarkHash||cachedMarkStyle!=settings.markStyle||
                    cachedMarkSize!=settings.markSizeMm||cachedMarkMin!=minCut||
                    cachedMarkClearance!=settings.registrationClearanceMm||
                    cachedMarkW!=artW||cachedMarkH!=artH){
                cachedMarkHash=hash;cachedMarkStyle=settings.markStyle;
                cachedMarkSize=settings.markSizeMm;cachedMarkMin=minCut;
                cachedMarkClearance=settings.registrationClearanceMm;
                cachedMarkW=artW;cachedMarkH=artH;
                cachedMarkPlacementIssue=RegistrationPreviewGeometry.issue(artW,artH,settings.markSizeMm,
                    settings.registrationClearanceMm,marks);
                for(int i=0;i<4;i++){
                    Path path=markPaths[i];path.rewind();
                    float[] xy=RegistrationMarks.outline(settings.markStyle,marks[i][0],marks[i][1],
                        settings.markSizeMm,minCut);
                    if(xy==null)path.addCircle(marks[i][0],marks[i][1],settings.markSizeMm/2f,Path.Direction.CW);
                    else addCutRing(path,xy);
                }
            }
            RegistrationCutGeometry.Report basicIssues=null;
            try{basicIssues=RegistrationCutGeometry.basic(artW,artH,settings.markSizeMm,
                settings.registrationClearanceMm,marks);}catch(IllegalArgumentException invalid){}
            boolean committed=committedMarkReport!=null&&committedMarkHash==hash;
            paint.setStyle(Paint.Style.FILL);
            for(int i=0;i<4;i++){
                boolean invalid=(basicIssues!=null&&basicIssues.invalid(i))||
                    (committed&&committedMarkReport.invalid(i));
                paint.setColor(invalid?0xffE96C65:tool==5&&markIndex==i?0xff6CBFFC:0xff43B9AB);
                cv.drawPath(markPaths[i],paint);
                if(tool==5&&markIndex==i){
                    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.25f);
                    cv.drawCircle(marks[i][0],marks[i][1],Math.max(3f,settings.markSizeMm*.65f),paint);
                    paint.setStyle(Paint.Style.FILL);
                }
            }
        }
        /** Non-destructive Prepare canvas has its own transform, independent of SVG geometry. */
        private float prepareWidth(){return settings.widthMm;}
        private float prepareHeight(){return photo==null?settings.widthMm:settings.widthMm*photo.getHeight()/(float)photo.getWidth();}
        private final SnipCanvasPathCache snipPathCache=new SnipCanvasPathCache();
        private void drawPreparePhoto(Canvas cv){drawEditedPhoto(cv,true);}
        /** Preview while vector paths are pending must use the SAME crop, snips and
         * lighting as Prepare. Never expose the unsnipped photo on a stencil tab. */
        private void drawEditedPhoto(Canvas cv,boolean editing){
            cv.drawColor(CANVAS);
            if(photo==null){
                paint.setColor(0xffD8E4F0);paint.setTextSize(dp(14));paint.setTextAlign(Paint.Align.CENTER);
                cv.drawText(startupRestoring?"Restoring last project…":"Import a photograph",getWidth()/2f,getHeight()/2f,paint);
                paint.setTextAlign(Paint.Align.LEFT);return;
            }
            float ww=prepareWidth(),hh=prepareHeight(),pad=Math.max(0,settings.outerSpacingMm);
            // Keep marks editable if a temporarily invalid drag prevents vector regeneration.
            if(!editing&&tool==5&&activeWorkspace!=0)
                pad=previewMarkPad(ww,hh,pad);
            setArtworkTransform(ww,hh,pad);
            cv.save();cv.translate(getWidth()/2f+panX,getHeight()/2f+panY);cv.scale(zoom,zoom);
            cv.translate(-getWidth()/2f,-getHeight()/2f);
            cv.translate(originX,originY);cv.scale(scale,scale);cv.translate(pad,pad);
            float checker=Math.max(2f,Math.min(7f,ww/18f));
            paint.setStyle(Paint.Style.FILL);
            prepareChecker.draw(cv,ww,hh,checker);
            // Pre-v2.48 projects could fill excluded pixels with opaque white
            // or black. Show that fill here instead of incorrectly showing
            // transparency; new snips default to checkerboard transparency.
            if(settings.snipFillMode!=SnipBackground.EMPTY&&settings.snips!=null&&!settings.snips.isEmpty()){
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(settings.snipFillMode==SnipBackground.WHITE?Color.WHITE:Color.BLACK);
                cv.drawRect(0,0,ww,hh,paint);
            }
            cv.save();
            if(settings.snips!=null){
                Path[] cached=snipPathCache.paths(settings.snips,ww,hh);
                java.util.List<SnipMask.Operation> ops=settings.snips.operations();
                for(int i=0;i<cached.length;i++){
                    if(ops.get(i).kind==SnipMask.KEEP_INSIDE)cv.clipPath(cached[i]);
                    else cv.clipOutPath(cached[i]);
                }
            }
            Bitmap display=(showOriginal||holdCompare||tonalPreview==null)?photo:tonalPreview;
            paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);paint.setAlpha(255);
            photoRect.set(0,0,ww,hh);cv.drawBitmap(display,null,photoRect,paint);cv.restore();
            if(!editing&&tool==5&&activeWorkspace!=0)
                drawRegistrationOverlay(cv,ww,hh);
            if(editing&&tool==8){
                if(settings.snips!=null){
                    Path[] cached=snipPathCache.paths(settings.snips,ww,hh);
                    java.util.List<SnipMask.Operation> ops=settings.snips.operations();
                    for(int i=0;i<cached.length;i++){
                        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.25f);
                        paint.setColor(ops.get(i).kind==SnipMask.REMOVE_INSIDE?0xffF18C83:0xff82E0B9);
                        cv.drawPath(cached[i],paint);
                    }
                }
                if(!snipDraft.isEmpty()){
                    Path p=draftSnipPath;p.rewind();for(int i=0;i<snipDraft.size();i++){
                        float px=snipDraft.get(i)[0]*ww,py=snipDraft.get(i)[1]*hh;
                        if(i==0)p.moveTo(px,py);else p.lineTo(px,py);
                    }
                    if(snipClosed)p.close();
                    if(snipClosed){paint.setStyle(Paint.Style.FILL);paint.setColor(snipAction==SnipMask.REMOVE_INSIDE?0x44FC766F:0x3358E3AE);cv.drawPath(p,paint);}
                    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.35f);
                    paint.setColor(snipAction==SnipMask.REMOVE_INSIDE?0xffFA9D92:0xff7EF0B6);cv.drawPath(p,paint);
                    paint.setStyle(Paint.Style.FILL);
                    for(int i=0;i<snipDraft.size();i++){
                        float px=snipDraft.get(i)[0]*ww,py=snipDraft.get(i)[1]*hh;
                        paint.setColor(0xff172332);cv.drawCircle(px,py,1.35f,paint);
                        paint.setColor(i==0?0xffF8D264:0xffC6E9FF);cv.drawCircle(px,py,.85f,paint);
                    }
                }
            }
            cv.restore();
            if(editing&&tool==8&&hoverX>=0&&hoverY>=0){
                paint.setStyle(Paint.Style.STROKE);paint.setColor(0xff74E6CE);paint.setStrokeWidth(dp(1));
                cv.drawCircle(hoverX,hoverY,dp(10),paint);
            }
        }
        /** Specialty preview is owned by its generator, not by a stale standard
         * dot/line render. Presentation scaling never changes SVG coordinates. */
        private void drawStandaloneSpecialty(Canvas cv,Bitmap preview){
            cv.drawColor(CANVAS);
            if(preview==null)return;
            float width=prepareWidth();
            float height=width*preview.getHeight()/Math.max(1f,preview.getWidth());
            float pad=Math.max(0,settings.outerSpacingMm);
            pad=previewMarkPad(width,height,pad);
            setArtworkTransform(width,height,pad);
            cv.save();cv.translate(getWidth()/2f+panX,getHeight()/2f+panY);
            cv.scale(zoom,zoom);cv.translate(-getWidth()/2f,-getHeight()/2f);
            cv.translate(originX,originY);cv.scale(scale,scale);
            paint.setStyle(Paint.Style.FILL);paint.setColor(settings.paper);
            cv.drawRect(pad,pad,pad+width,pad+height,paint);
            paint.setAlpha(255);paint.setColor(Color.WHITE);
            photoRect.set(pad,pad,pad+width,pad+height);
            cv.drawBitmap(preview,null,photoRect,paint);
            if(tool==5&&(tonalStencilMode||tessellationMode||layeredCellMode||stippleActive()||newStyleActive())){
                cv.save();cv.translate(pad,pad);
                drawRegistrationOverlay(cv,width,height);
                cv.restore();
            }
            cv.restore();
        }
        /** Finished specialty preview uses the same vector geometry as each exported sheet.
         * Selecting a layer, zooming or panning changes only Canvas state, never rasters. */
        /** Invoked with Canvas already translated to the artwork origin in mm.
         * This display overlay never enters source SVG paths or export geometry.
         */
        private void drawSpecialtyRiskOverlay(Canvas cv,int selected,float artworkWidthMm){
            if(!showCutRiskMarkers||alternatePreviewDraft||silhouetteOnly()||basePreviewActive()||
                specialtyRiskSource!=currentSpecialtyRiskSource())return;
            if(previewMode==2&&selected<0)selected=0;
            float radius=Math.max(1.1f,artworkWidthMm/105f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0xffEF584D);
            paint.setStrokeWidth(Math.max(.32f,artworkWidthMm/700f));
            for(CutWarningLocator.Marker mark:specialtyRiskMarkers){
                if(selected>=0&&mark.layer!=selected)continue;
                cv.drawCircle(mark.xMm,mark.yMm,radius,paint);
                cv.drawLine(mark.xMm-radius*.4f,mark.yMm,mark.xMm+radius*.4f,mark.yMm,paint);
                cv.drawLine(mark.xMm,mark.yMm-radius*.4f,mark.xMm,mark.yMm+radius*.4f,paint);
            }
        }
        private AutoSilhouetteUnderlay.Output cachedUnderlaySource=null;
        private final Path cachedUnderlayPath=new Path();
        private void drawUnderlayInArtworkMm(Canvas cv,boolean specialty){
            AutoSilhouetteUnderlay.Output base=currentUnderlay();
            if(base==null||showOriginal||holdCompare||previewMode==3)return;
            if(previewMode==2&&!basePreviewActive())return; // selected detail layer uses its own cut preview
            if(!basePreviewActive()&&(specialty?(newStyleActive()?newStyleSelectedLayer>=0:tessSelectedLayer>=0):chosen>=0))return;
            if(cachedUnderlaySource!=base){
                cachedUnderlaySource=base;
                cachedUnderlayPath.rewind();
                cachedUnderlayPath.setFillType(Path.FillType.EVEN_ODD);
                float[][] rings=base.shape.contours;
                if(rings==null)rings=new float[][]{base.shape.xy};
                for(float[] points:rings){
                    cachedUnderlayPath.moveTo(points[0],points[1]);
                    for(int i=2;i<points.length;i+=2)cachedUnderlayPath.lineTo(points[i],points[i+1]);
                    cachedUnderlayPath.close();
                }
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(previewMode==1?Color.BLACK:base.color);
            cv.drawPath(cachedUnderlayPath,paint);
        }
        private void drawSpecialtyVectors(Canvas cv,SpecialtyVectorCache cache){
            cv.drawColor(CANVAS);
            final float artW=settings.widthMm, artH=artW*cache.height/Math.max(1f,cache.width);
            final float pad=previewMarkPad(artW,artH,Math.max(0f,settings.outerSpacingMm));
            setArtworkTransform(artW,artH,pad);
            cv.save();cv.translate(getWidth()/2f+panX,getHeight()/2f+panY);
            cv.scale(zoom,zoom);cv.translate(-getWidth()/2f,-getHeight()/2f);
            cv.translate(originX,originY);cv.scale(scale,scale);
            paint.setStyle(Paint.Style.FILL);paint.setColor(previewMode==1?Color.WHITE:settings.paper);
            cv.drawRect(pad,pad,pad+artW,pad+artH,paint);
            cv.save();cv.translate(pad,pad);
            drawUnderlayInArtworkMm(cv,true);
            cv.scale(artW/cache.width,artH/cache.height);
            int selected=tonalStencilMode?tonalSelectedLayer:(tessellationMode||layeredCellMode)?tessSelectedLayer:-1;
            if(!silhouetteOnly()&&!basePreviewActive())for(int j=0;j<cache.stackOrder.length;j++){
                final int li=cache.stackOrder[j];
                if(li<0||li>=cache.layers.length)continue;
                if(selected>=0&&selected!=li)continue;
                if(previewMode==2&&selected<0&&li!=0)continue;
                Path path=cache.layers[li];
                if(tonalStencilMode&&selected>=0&&!tonalCutLayerPreview&&cache.topVisible!=null)
                    path=cache.topVisible[li];
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(previewMode==1?Color.BLACK:cache.colors[li]);
                cv.drawPath(path,paint);
            }
            cv.restore(); // Restore the physical sheet origin (the pad translation was removed).
            // Both marks and warnings are expressed in artwork-local millimeters.
            // Do not draw them in sheet coordinates or they shift up/left by pad.
            cv.save();cv.translate(pad,pad);
            if(tonalStencilMode||tessellationMode||layeredCellMode)
                drawRegistrationOverlay(cv,artW,artH);
            drawSpecialtyRiskOverlay(cv,selected,artW);
            cv.restore();
            cv.restore();
            vectorZoomLabel(cv);
        }
        private void drawNewStyleVectors(Canvas cv,NewStyleVectorCache cache){
            cv.drawColor(CANVAS);
            final StyleKit.Result result=cache.source;
            final float artW=result.widthMm,artH=result.heightMm;
            final float pad=previewMarkPad(artW,artH,
                Math.max(settings.registration?8f:0f,settings.outerSpacingMm));
            setArtworkTransform(artW,artH,pad);
            cv.save();cv.translate(getWidth()/2f+panX,getHeight()/2f+panY);
            cv.scale(zoom,zoom);cv.translate(-getWidth()/2f,-getHeight()/2f);
            cv.translate(originX,originY);cv.scale(scale,scale);
            paint.setStyle(Paint.Style.FILL);paint.setColor(settings.paper);
            cv.drawRect(pad,pad,pad+artW,pad+artH,paint);
            cv.save();cv.translate(pad,pad);
            drawUnderlayInArtworkMm(cv,true);
            if(!silhouetteOnly()&&!basePreviewActive())for(int i=0;i<cache.fills.length;i++){
                int slot=NewStyleColorSlots.slot(newStyleId,result.layers.get(i).name,i);
                if(newStyleSelectedLayer>=0&&newStyleSelectedLayer!=slot)continue;
                if(previewMode==2&&newStyleSelectedLayer<0&&slot!=0)continue;
                paint.setColor(previewMode==1?Color.BLACK:cache.colors[i]);
                paint.setStyle(Paint.Style.FILL);
                cv.drawPath(cache.fills[i],paint);
                if(!result.layers.get(i).lines.isEmpty()){
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
                    paint.setStrokeWidth(cache.strokeWidths[i]);
                    cv.drawPath(cache.strokes[i],paint);
                }
            }
            drawRegistrationOverlay(cv,artW,artH);
            drawSpecialtyRiskOverlay(cv,newStyleSelectedLayer,artW);
            cv.restore();cv.restore();vectorZoomLabel(cv);
        }
        private void drawContinuousVectors(Canvas cv){
            final SpaceFillingRibbon.Result ribbon=curveRibbonResult;
            final Path line=continuousPreview;
            final MazePortraitEngine.Result maze=mazeResult;
            final Bitmap src=alternatePreparedSource;
            final float nativeW=maze!=null?maze.canvasSizePx:
                (src!=null?src.getWidth():Math.max(1,alternatePreparedWidth));
            final float nativeH=maze!=null?maze.canvasSizePx:
                (src!=null?src.getHeight():Math.max(1,alternatePreparedHeight));
            final float artW=settings.widthMm,artH=artW*nativeH/Math.max(1f,nativeW);
            final float pad=Math.max(0f,settings.outerSpacingMm);
            cv.drawColor(CANVAS);setArtworkTransform(artW,artH,pad);
            cv.save();cv.translate(getWidth()/2f+panX,getHeight()/2f+panY);
            cv.scale(zoom,zoom);cv.translate(-getWidth()/2f,-getHeight()/2f);
            cv.translate(originX,originY);cv.scale(scale,scale);
            paint.setStyle(Paint.Style.FILL);paint.setColor(settings.paper);
            cv.drawRect(pad,pad,pad+artW,pad+artH,paint);
            cv.save();cv.translate(pad,pad);
            cv.scale(artW/nativeW,artH/nativeH);
            if(maze!=null){
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setColor(0xff222222);paint.setStrokeWidth(Math.max(.8f,maze.canvasSizePx/520f));
                cv.drawPath(maze.wallsPath,paint);
                if(mazeShowSolution){paint.setColor(0xffE6673E);
                    paint.setStrokeWidth(Math.max(1.6f,maze.canvasSizePx/240f));
                    cv.drawPath(maze.solutionPath,paint);}
            }else if(line!=null){
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);paint.setColor(Color.BLACK);
                float stroke=Math.max(.05f,continuousStrokeMm*nativeW/Math.max(.001f,artW));
                paint.setStrokeWidth(stroke);cv.drawPath(line,paint);
            }
            cv.restore();cv.restore();vectorZoomLabel(cv);
        }
        private void vectorZoomLabel(Canvas cv){
            if(zoom>1.01f){
                paint.setStyle(Paint.Style.FILL);paint.setColor(0xffC8E6FC);
                paint.setTextSize(dp(12));paint.setTextAlign(Paint.Align.RIGHT);
                cv.drawText(String.format(Locale.US,"%.1f× · vector%s",zoom,
                    alternatePreviewDraft?" draft":""),getWidth()-dp(16),dp(60),paint);
                paint.setTextAlign(Paint.Align.LEFT);
            }
        }
        /** Hardware Canvas receives immutable cut paths. Zoom/pan never redraws a bitmap
         * or generates fresh stipples; only the canvas transform changes. */
        private void drawStippleVectors(Canvas cv,StippleVectorCache cache){
            TessellationEngine.Result result=cache.source;
            cv.drawColor(CANVAS);
            float artW=settings.widthMm;
            float artH=artW*result.height/Math.max(1f,result.width);
            float pad=previewMarkPad(artW,artH,Math.max(0f,settings.outerSpacingMm));
            setArtworkTransform(artW,artH,pad);
            cv.save();
            cv.translate(getWidth()/2f+panX,getHeight()/2f+panY);
            cv.scale(zoom,zoom);
            cv.translate(-getWidth()/2f,-getHeight()/2f);
            cv.translate(originX,originY);cv.scale(scale,scale);
            paint.setStyle(Paint.Style.FILL);paint.setColor(settings.paper);
            cv.drawRect(pad,pad,pad+artW,pad+artH,paint);
            cv.save();cv.translate(pad,pad);
            drawUnderlayInArtworkMm(cv,true);
            cv.scale(artW/Math.max(1f,result.width),artH/Math.max(1f,result.height));
            if(!silhouetteOnly()&&!basePreviewActive())for(int order=0;order<cache.layers.length;order++){
                int layer=result.stipple==null?order:result.stipple.options.order[order];
                if(layer<0||layer>=cache.layers.length)continue;
                if(tessSelectedLayer>=0&&layer!=tessSelectedLayer)continue;
                paint.setStyle(Paint.Style.FILL);paint.setColor(cache.colors[layer]);
                cv.drawPath(cache.layers[layer],paint);
                if(stippleShowCutOutlines){
                    paint.setStyle(Paint.Style.STROKE);paint.setColor(0xff64D2FF);
                    float pixelScale=Math.max(.001f,scale*zoom*artW/Math.max(1f,result.width));
                    paint.setStrokeWidth(.85f/pixelScale);
                    cv.drawPath(cache.layers[layer],paint);
                }
            }
            cv.restore();
            // Stipple layer geometry was local to (pad, pad); overlays must be too.
            cv.save();cv.translate(pad,pad);
            drawRegistrationOverlay(cv,artW,artH);
            drawSpecialtyRiskOverlay(cv,tessSelectedLayer,artW);
            cv.restore();
            cv.restore();
            if(zoom>1.01f){
                paint.setStyle(Paint.Style.FILL);paint.setColor(0xffC8E6FC);
                paint.setTextSize(dp(12));paint.setTextAlign(Paint.Align.RIGHT);
                cv.drawText(zoomLabel(zoom)+"× · vector",getWidth()-dp(16),dp(60),paint);
                paint.setTextAlign(Paint.Align.LEFT);
            }
        }
        @Override protected void onDraw(Canvas cv){super.onDraw(cv);
            if(transforming&&!capturingGestureSnapshot&&gestureSnapshot!=null
                    &&tool!=5&&tool!=8&&activeWorkspace!=0){
                drawGestureSnapshot(cv);return;
            }
            if(activeWorkspace==0){drawPreparePhoto(cv);return;}
            cv.drawColor(CANVAS);VinylEngine.Result r=latest;
            StippleVectorCache cuts=stippleVectorCache;
            if(stippleActive()&&(alternateReadyGeneration==alternateGeneration||registrationRenderPending)
                    &&cuts!=null&&cuts.source==tessellationResult&&!showOriginal&&!holdCompare){
                drawStippleVectors(cv,cuts);return;
            }
            // All completed specialty styles draw native paths from their cut geometry.
            // Source/comparison mode is intentionally photographic, not a fake vector.
            if(alternateActive()&&(alternateReadyGeneration==alternateGeneration||registrationRenderPending)){
                if((showOriginal||holdCompare||tool==10)&&photo!=null){
                    drawEditedPhoto(cv,false);return;
                }
                if(newStyleActive()&&newStyleVectors!=null&&newStyleVectors.source==newStyleResult){
                    drawNewStyleVectors(cv,newStyleVectors);return;
                }
                SpecialtyVectorCache vectors=specialtyVectorCache;
                if(vectors!=null&&(
                    vectors.source==tonalResult||vectors.source==layeredResult||
                    vectors.source==tessellationResult||vectors.source==curveRibbonResult||vectors.source==spiralRibbonResult)){
                    drawSpecialtyVectors(cv,vectors);return;
                }
                if((continuousStyle!=null&&continuousPreview!=null)||
                    (mazePortraitMode&&mazeResult!=null)){
                    drawContinuousVectors(cv);return;
                }
            }
            // The standard render can be null or stale after Prepare changed a snip
            // or lighting value. In both cases display the edited photograph, not
            // photo (the unmasked source). A completed specialty bitmap stands on
            // its own; it does not require any standard VinylEngine.Result.
            if(alternateActive() && r==null && (alternateReadyGeneration==alternateGeneration||registrationRenderPending)
                    && alternatePreviewBitmap!=null && !showOriginal && !holdCompare){
                drawStandaloneSpecialty(cv,alternatePreviewBitmap);
                return;
            }
            if(r==null || (alternateActive() && alternateReadyGeneration!=alternateGeneration && !registrationRenderPending)
                    || (!alternateActive() && renderedSerial!=serial && !registrationRenderPending)){
                drawEditedPhoto(cv,false);
                paint.setStyle(Paint.Style.FILL);paint.setColor(0xffD8E4F0);
                paint.setTextSize(dp(14));paint.setTextAlign(Paint.Align.CENTER);
                cv.drawText(photo==null?"Import a photograph":"Preparing cut preview…",getWidth()/2f,getHeight()-dp(18),paint);
                paint.setTextAlign(Paint.Align.LEFT);return;
            }
            final float standardPad=previewMarkPad(r.settings.widthMm,r.heightMm,r.padMm);
            setArtworkTransform(r.settings.widthMm,r.heightMm,standardPad);
            artworkRect.set(0,0,r.settings.widthMm,r.heightMm);cv.save();cv.translate(getWidth()/2f+panX,getHeight()/2f+panY);cv.scale(zoom,zoom);cv.translate(-getWidth()/2f,-getHeight()/2f);
            cv.translate(originX,originY);cv.scale(scale,scale);
            paint.setStyle(Paint.Style.FILL);
            int inspectionPaper=r.settings.paper;
            if(chosen>=0&&chosen<r.settings.layers.size()&&!alternateActive()){
                int vinyl=r.settings.layers.get(chosen).color;
                int red=(vinyl>>16)&255,green=(vinyl>>8)&255,blue=vinyl&255;
                inspectionPaper=(red*299+green*587+blue*114>=185000)?0xff26323c:0xfff7f7f7;
            }
            paint.setColor(previewMode==1?Color.WHITE:inspectionPaper);
            cv.drawRect(standardPad,standardPad,standardPad+r.settings.widthMm,standardPad+r.heightMm,paint);
            cv.save();cv.translate(standardPad,standardPad);
            if((showOriginal||tool==8||tool==10||holdCompare||previewMode==3||r.count()==0)&&photo!=null){
                // The canvas may use Android clipping; exported SVGs NEVER do.
                // Exported cut geometry is filtered by SnipMask inside VinylEngine.
                if(tool==8){
                    snipChecker.draw(cv,r.settings.widthMm,r.heightMm,10f);
                }
                cv.save();
                // Compose legacy white/black snip fills on the canvas instead of
                // allocating an entire processed bitmap inside onDraw().
                if(tool==8&&settings.snipFillMode!=SnipBackground.EMPTY
                        &&settings.snips!=null&&!settings.snips.isEmpty()){
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(settings.snipFillMode==SnipBackground.WHITE?Color.WHITE:Color.BLACK);
                    cv.drawRect(0,0,r.settings.widthMm,r.heightMm,paint);
                }
                SnipMask drawSnips=(tool==8)?settings.snips:r.settings.snips;
                if(drawSnips!=null){
                    Path[] contours=snipPathCache.paths(drawSnips,r.settings.widthMm,r.heightMm);
                    java.util.List<SnipMask.Operation> ops=drawSnips.operations();
                    for(int i=0;i<contours.length;i++){
                        if(ops.get(i).kind==SnipMask.KEEP_INSIDE)cv.clipPath(contours[i]);
                        else cv.clipOutPath(contours[i]);
                    }
                }
                paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);
                Bitmap imageToShow=showOriginal||holdCompare?photo:
                    (tool==10||r.count()==0)&&tonalPreview!=null?tonalPreview:photo;
                if(previewMode==3&&!showOriginal&&!holdCompare&&tool!=8&&tool!=10){paint.setAlpha(90);}
                cv.drawBitmap(imageToShow,null,artworkRect,paint);
                paint.setAlpha(255);
                cv.restore();
            }
            if(!alternateActive()&&!showOriginal&&!holdCompare&&tool!=8&&tool!=10){
                // A single-sheet inspection uses a neutral background, not L1 or
                // the mounting substrate; the actual full composite still has L1.
                if(chosen<0)drawUnderlayInArtworkMm(cv,false);
                CanvasGeometry cache=canvasGeometry;
                if(!silhouetteOnly()&&!basePreviewActive()&&cache!=null&&cache.source==r){
                    for(int li=0;li<cache.paths.length;li++){
                        if(chosen>=0&&chosen!=li)continue;
                        if(previewMode==2&&chosen<0&&li!=0)continue;
                        if(chosen>=0){
                            // Inspection may show a deliberately skipped sheet so
                            // its source contours remain available for restoration.
                            if(!r.settings.layers.get(li).enabled)continue;
                        }else if(!VinylEngine.cutLayer(r,li))continue;
                        paint.setStyle(Paint.Style.FILL);
                        paint.setColor(previewMode==1?Color.BLACK:liveColorPreview.colorFor(li,r.settings.layers.get(li).color));
                        cv.drawPath(cache.paths[li],paint);
                    }
                }
            }
            // Draw bounded geometry-derived advisory locations, not an obsolete
            // preview bitmap. Artwork and marker coordinates share millimeters.
            if(!silhouetteOnly()&&!basePreviewActive()&&!alternateActive()&&showCutRiskMarkers&&cutRiskResult==r&&
                    !showOriginal&&!holdCompare&&tool!=8&&tool!=10){
                for(CutWarningLocator.Marker mark:cutRiskMarkers){
                    if(chosen>=0&&chosen!=mark.layer)continue;
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(.32f,r.settings.widthMm/700f));
                    paint.setColor(0xffEF584D);
                    float rad=Math.max(1.1f,r.settings.widthMm/105f);
                    cv.drawCircle(mark.xMm,mark.yMm,rad,paint);
                    cv.drawLine(mark.xMm-rad*.4f,mark.yMm,mark.xMm+rad*.4f,mark.yMm,paint);
                    cv.drawLine(mark.xMm,mark.yMm-rad*.4f,mark.xMm,mark.yMm+rad*.4f,paint);
                }
            }
            if(alternateActive()&&!showOriginal&&!holdCompare&&tool!=10){
                if(continuousStyle!=null){paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);cv.drawRect(0,0,settings.widthMm,r.heightMm,paint);}
                if(mazeResult!=null&&mazePortraitMode){
                    float side=mazeResult.canvasSizePx;
                    cv.save();cv.scale(settings.widthMm/side,r.heightMm/side);
                    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(.3f,side/520f));paint.setColor(0xffECEFF3);
                    cv.drawPath(mazeResult.wallsPath,paint);
                    if(mazeShowSolution){paint.setColor(0xffE6673E);paint.setStrokeWidth(Math.max(.7f,side/250f));cv.drawPath(mazeResult.solutionPath,paint);}
                    cv.restore();
                }else if(alternatePreviewBitmap!=null&&(continuousStyle!=null||tonalStencilMode||tessellationMode||layeredCellMode)){
                    paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);
                    cv.drawBitmap(alternatePreviewBitmap,null,artworkRect,paint);
                }else if(continuousPreview!=null&&continuousStyle!=null){
                    paint.setStyle(Paint.Style.STROKE);paint.setColor(Color.BLACK);paint.setStrokeWidth(Math.max(.22f,settings.widthMm/280f));paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
                    cv.save();cv.scale(settings.widthMm/photo.getWidth(),r.heightMm/photo.getHeight());
                    cv.drawPath(continuousPreview,paint);cv.restore();
                }else if(tonalDisplayBitmap!=null&&tonalStencilMode){
                    paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);
                    cv.drawBitmap(tonalDisplayBitmap,null,artworkRect,paint);
                }else if(continuousStyle!=null&&alternateStatus.startsWith("Curve render failed:")){
                    // Failure remains visible even when the compact status row
                    // is collapsed; do not leave a silent white canvas.
                    paint.setStyle(Paint.Style.FILL);paint.setColor(0xff8b2530);
                    paint.setTextSize(Math.max(3f,settings.widthMm/38f));
                    cv.drawText("Curve render failed",settings.widthMm*.08f,r.heightMm*.44f,paint);
                    paint.setTextSize(Math.max(2f,settings.widthMm/58f));
                    cv.drawText("Lower recursion and retry",settings.widthMm*.08f,r.heightMm*.50f,paint);
                }
            }
            paint.setStyle(Paint.Style.STROKE);paint.setColor(0xff60a7e0);paint.setStrokeWidth(.17f);paint.setPathEffect(Build.VERSION.SDK_INT>=28?marginDashes:null);float m=r.settings.marginMm;if(r.settings.circleCrop)cv.drawOval(m,m,r.settings.widthMm-m,r.heightMm-m,paint);else cv.drawRect(m,m,r.settings.widthMm-m,r.heightMm-m,paint);paint.setPathEffect(null);
            drawRegistrationOverlay(cv,r.settings.widthMm,r.heightMm);
            if(tool==8){
                // Show applied masks and the currently editable, draggable vertices.
                if(settings.snips!=null){
                    Path[] outlines=snipPathCache.paths(settings.snips,r.settings.widthMm,r.heightMm);
                    java.util.List<SnipMask.Operation> ops=settings.snips.operations();
                    for(int i=0;i<outlines.length;i++){
                        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.24f);
                        paint.setColor(ops.get(i).kind==SnipMask.REMOVE_INSIDE?0xffF18C83:0xff82E0B9);
                        paint.setPathEffect(Build.VERSION.SDK_INT>=28?snipDashes:null);
                        cv.drawPath(outlines[i],paint);paint.setPathEffect(null);
                    }
                }
                if(snipDraft.size()>0){
                    Path outline=draftSnipPath;outline.rewind();for(int i=0;i<snipDraft.size();i++){
                        float px=snipDraft.get(i)[0]*r.settings.widthMm,py=snipDraft.get(i)[1]*r.heightMm;
                        if(i==0)outline.moveTo(px,py);else outline.lineTo(px,py);
                    }
                    if(snipClosed)outline.close();
                    if(snipClosed){paint.setStyle(Paint.Style.FILL);paint.setColor(snipAction==SnipMask.REMOVE_INSIDE?0x44FC766F:0x3358E3AE);cv.drawPath(outline,paint);}
                    paint.setStyle(Paint.Style.STROKE);paint.setColor(snipAction==SnipMask.REMOVE_INSIDE?0xffFA9D92:0xff7EF0B6);paint.setStrokeWidth(.34f);cv.drawPath(outline,paint);
                    paint.setPathEffect(null);paint.setStyle(Paint.Style.FILL);
                    for(int i=0;i<snipDraft.size();i++){
                        float px=snipDraft.get(i)[0]*r.settings.widthMm,py=snipDraft.get(i)[1]*r.heightMm;
                        paint.setColor(0xff172332);cv.drawCircle(px,py,1.35f,paint);
                        paint.setColor(i==0?0xffF8D264:0xffC6E9FF);cv.drawCircle(px,py,.85f,paint);
                    }
                }
            }
            cv.restore();cv.restore();
            if(r.count()==0&&tool!=8){
                paint.setStyle(Paint.Style.FILL);paint.setColor(0xffF6CE91);
                paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(12));
                cv.drawText("No vinyl cut shapes · review light, colors or detail",getWidth()/2f,getHeight()-dp(13),paint);
                paint.setTextAlign(Paint.Align.LEFT);
            }
            if(zoom>1.01f){
                paint.setTextSize(dp(12));paint.setColor(0xffC8E6FC);paint.setStyle(Paint.Style.FILL);
                paint.setTextAlign(Paint.Align.RIGHT);
                cv.drawText(zoomLabel(zoom)+"×",getWidth()-dp(15),dp(22),paint);
                paint.setTextAlign(Paint.Align.LEFT);
            }
            if(tool==8&&hoverX>=0&&hoverY>=0){
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(0xff74E6CE);
                cv.drawCircle(hoverX,hoverY,dp(10),paint);
                cv.drawLine(hoverX-dp(15),hoverY,hoverX+dp(15),hoverY,paint);
                cv.drawLine(hoverX,hoverY-dp(15),hoverX,hoverY+dp(15),paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }
        /** Android dispatches S Pen hover before contact, enabling palm-safe vertex work. */
        @Override public boolean onHoverEvent(MotionEvent event){
            if(tool!=8||event.getToolType(0)!=MotionEvent.TOOL_TYPE_STYLUS)return super.onHoverEvent(event);
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_HOVER_EXIT){hoverX=hoverY=-1;stylusNearbyUntil=0;}
            else if(action==MotionEvent.ACTION_HOVER_ENTER||action==MotionEvent.ACTION_HOVER_MOVE){
                hoverX=event.getX();hoverY=event.getY();stylusNearbyUntil=android.os.SystemClock.uptimeMillis()+220;
            }
            invalidate();return true;
        }
        /** Two-finger movement is independent of whether Snip or Marks is selected. */
        private float midX(MotionEvent e){return (e.getX(0)+e.getX(1))*.5f;}
        private float midY(MotionEvent e){return (e.getY(0)+e.getY(1))*.5f;}
        private boolean isCanceled(MotionEvent event){
            return event.getActionMasked()==MotionEvent.ACTION_CANCEL ||
                (Build.VERSION.SDK_INT>=33&&(event.getFlags()&MotionEvent.FLAG_CANCELED)!=0);
        }
        private void rollbackGesture(){
            if(dragWasRecorded){SnipTimeline.State before=snipTimeline.cancelLast();
                if(before!=null)applySnipState(before);
            }else if(snipVertex>=0&&originalVertex!=null&&snipVertex<snipDraft.size()){
                snipDraft.set(snipVertex,originalVertex.clone());
            }
            if(draggedMark>=0){
                settings.marks=marksStartedDefault?null:originalMarks;
            }
            snipVertex=draggedMark=-1;markDragFixedPad=-1f;
            dragWasRecorded=false;originalVertex=null;
            invalidate();
        }
        /** A single finger or pen creates snip vertices; multi-finger contact navigates the canvas. */
        private void beginSnipContact(MotionEvent event,int index,float gw,float gh){
            snipPointerId=event.getPointerId(index);
            activeToolType=event.getToolType(index);
            stylusActive=SnipInputPolicy.isPen(activeToolType);
            if(stylusActive)stylusNearbyUntil=android.os.SystemClock.uptimeMillis()+220;
            ignoreFingerSnip=SnipInputPolicy.ignoreForPalm(activeToolType,
                android.os.SystemClock.uptimeMillis()<stylusNearbyUntil);
            lastX=snipDownX=event.getX(index);lastY=snipDownY=event.getY(index);
            snipVertex=-1;originalVertex=null;dragWasRecorded=false;snipMultitouch=false;
            if(snipPan||ignoreFingerSnip)return;
            float[] pos=viewToArtwork(lastX,lastY);
            float threshold=dp(stylusActive?13:23)/(scale*zoom);
            for(int i=snipDraft.size()-1;i>=0;i--){
                float[] point=snipDraft.get(i);
                if(Math.hypot(point[0]*gw-pos[0],point[1]*gh-pos[1])<threshold){
                    snipVertex=i;originalVertex=point.clone();break;
                }
            }
        }
        /** Finish only the captured pointer. A palm leaving cannot finish an S Pen point. */
        private void finishSnipContact(float x,float y,float gw,float gh){
            boolean wasPen=stylusActive;
            if(!snipPan&&!snipMultitouch&&!ignoreFingerSnip&&!dragWasRecorded&&
                Math.hypot(x-snipDownX,y-snipDownY)<dp(14)){
                if(snipVertex==0&&!snipClosed&&snipDraft.size()>=3){
                    recordSnip();snipClosed=true;performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);setTool(8);
                }else if(snipVertex<0){
                    float[] point=viewToArtwork(x,y);
                    if(point[0]<0||point[0]>gw||point[1]<0||point[1]>gh){
                        toast(getString(R.string.ui_place_the_point_inside_the_photograph));
                    }else if(snipClosed){toast(getString(R.string.ui_apply_or_discard_the_closed_selection));}
                    else if(!snipDraft.isEmpty()&&snipDraft.size()>=3&&
                        Math.hypot(point[0]-snipDraft.get(0)[0]*gw,
                            point[1]-snipDraft.get(0)[1]*gh)<dp(16)/(scale*zoom)){
                        recordSnip();snipClosed=true;performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);setTool(8);
                    }else if(snipDraft.size()>=SnipMask.MAX_VERTICES){
                        toast("Maximum "+SnipMask.MAX_VERTICES+" points per selection");
                    }else{
                        recordSnip();snipDraft.add(new float[]{point[0]/gw,
                            point[1]/gh});performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);setTool(8);
                    }
                }
                performClick();
            }
            if(wasPen)stylusNearbyUntil=android.os.SystemClock.uptimeMillis()+180;
            stylusActive=false;snipPointerId=MotionEvent.INVALID_POINTER_ID;
            snipVertex=-1;dragWasRecorded=false;originalVertex=null;
            invalidate();
        }
        @Override public boolean performClick(){super.performClick();return true;}
        @Override public boolean onTouchEvent(MotionEvent event){
            if(canvasFullscreen&&event.getPointerCount()==1&&event.getToolType(0)==MotionEvent.TOOL_TYPE_FINGER){
                int action=event.getActionMasked();
                if(action==MotionEvent.ACTION_DOWN&&event.getY()>getHeight()-dp(78)){
                    fullSwipeStartY=event.getY();return true;
                }
                if(fullSwipeStartY>=0){
                    if(action==MotionEvent.ACTION_MOVE&&fullSwipeStartY-event.getY()>dp(40)){
                        fullSwipeStartY=-1;setCanvasFullscreen(false);return true;
                    }
                    if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){fullSwipeStartY=-1;return true;}
                    return true;
                }
            }
            VinylEngine.Result result=latest;
            final float gw=gestureWidth(),gh=gestureHeight();
            if(result==null&&activeWorkspace!=0&&!alternateActive()){
                if(event.getActionMasked()==MotionEvent.ACTION_UP)pick();return true;
            }
            if(result==null&&photo==null){if(event.getActionMasked()==MotionEvent.ACTION_UP)pick();return true;}
            if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
            int kind=event.getActionMasked();
            if(kind==MotionEvent.ACTION_CANCEL){
                transforming=false;releaseGestureSnapshot();rollbackGesture();stylusActive=false;
                snipPointerId=MotionEvent.INVALID_POINTER_ID;snipMultitouch=true;
                holdCompare=false;invalidate();return true;
            }
            if(kind==MotionEvent.ACTION_POINTER_UP&&isCanceled(event)){
                int leaving=event.getPointerId(event.getActionIndex());
                if(tool==8&&SnipInputPolicy.keepPenAfterSecondaryExit(stylusActive,
                    snipPointerId,leaving))return true;
                rollbackGesture();snipMultitouch=true;
                snipPointerId=MotionEvent.INVALID_POINTER_ID;stylusActive=false;return true;
            }
            if(tool!=8||snipPan)taps.onTouchEvent(event);
            boolean penPresent=false;
            if(tool==8)for(int p=0;p<event.getPointerCount();p++){
                if(SnipInputPolicy.isPen(event.getToolType(p))){penPresent=true;break;}
            }
            if(!(tool==8&&penPresent))scaler.onTouchEvent(event);
            if(kind==MotionEvent.ACTION_DOWN){
                if(tool==8){beginSnipContact(event,0,gw,gh);return true;}
                activeToolType=event.getToolType(0);
                stylusActive=activeToolType==MotionEvent.TOOL_TYPE_STYLUS||activeToolType==MotionEvent.TOOL_TYPE_ERASER;
                if(stylusActive)stylusNearbyUntil=android.os.SystemClock.uptimeMillis()+1400;
                ignoreFingerSnip=tool==8&&activeToolType==MotionEvent.TOOL_TYPE_FINGER&&
                    android.os.SystemClock.uptimeMillis()<stylusNearbyUntil;
                lastX=snipDownX=event.getX();lastY=snipDownY=event.getY();
                draggedMark=-1;markDragFixedPad=-1f;
                snipVertex=-1;originalVertex=null;originalMarks=null;
                marksStartedDefault=false;
                dragWasRecorded=false;snipMultitouch=false;
                captureGestureSnapshot();
                if(tool==5&&settings.registration){
                    float[] pos=viewToArtwork(lastX,lastY);
                    // Specialty styles may not have a current standard VinylEngine.Result.
                    // Registration coordinates are always kept in physical artwork mm.
                    float[][] touchMarks=displayedMarks(gw,gh);
                    for(int i=0;i<touchMarks.length;i++){
                        float[] mark=touchMarks[i];
                        float threshold=Math.max(2.2f,dp(stylusActive?14:27)/(scale*zoom));
                        if(Math.hypot(mark[0]-pos[0],mark[1]-pos[1])<=threshold){
                            draggedMark=i;markIndex=i;checkpoint();
                            markDragFixedPad=viewedPad;
                            marksViewportPad=viewedPad;
                            marksStartedDefault=(settings.marks==null);
                            if(settings.marks!=null){originalMarks=new float[settings.marks.length][];
                                for(int j=0;j<settings.marks.length;j++)originalMarks[j]=settings.marks[j].clone();}
                            break;
                        }
                    }
                }
                return true;
            }
            if(kind==MotionEvent.ACTION_POINTER_DOWN){
                if(tool!=8&&tool!=5)transforming=true;
                int newIndex=event.getActionIndex();
                if(tool==8&&stylusActive){ // Ignore a resting palm while the captured pen edits.
                    return true;
                }
                if(tool==8&&SnipInputPolicy.isPen(event.getToolType(newIndex))){
                    // A palm may touch first. The S Pen gets priority when it contacts later.
                    rollbackGesture();beginSnipContact(event,newIndex,gw,gh);return true;
                }
                snipMultitouch=true;
                // A second finger cancels an in-progress vertex manipulation.
                if(dragWasRecorded||snipVertex>=0||draggedMark>=0)rollbackGesture();
                snipVertex=-1;draggedMark=-1;markDragFixedPad=-1f;
                if(event.getPointerCount()>=2){midpointX=midX(event);midpointY=midY(event);}
                return true;
            }
            if(kind==MotionEvent.ACTION_POINTER_UP){
                if(tool==8&&stylusActive){
                    int leaving=event.getActionIndex();
                    if(event.getPointerId(leaving)==snipPointerId){
                        finishSnipContact(event.getX(leaving),event.getY(leaving),gw,gh);
                        snipMultitouch=true;
                    }
                    // Finger/palm release must not interrupt the captured pen.
                    return true;
                }
                snipMultitouch=true;snipVertex=draggedMark=-1;markDragFixedPad=-1f;
                int leaving=event.getActionIndex();int remaining=(leaving==0?1:0);
                if(remaining<event.getPointerCount()){
                    lastX=event.getX(remaining);lastY=event.getY(remaining);
                }
                return true;
            }
            if(kind==MotionEvent.ACTION_MOVE){
                if(tool!=8&&tool!=5)transforming=true;
                int captureIndex=tool==8?event.findPointerIndex(snipPointerId):-1;
                if(tool==8&&stylusActive&&captureIndex<0)return true;
                if(event.getPointerCount()>=2&&!(tool==8&&stylusActive)){
                    // Pinch is handled by ScaleGestureDetector; translate by the
                    // moving focal point as well, otherwise two-finger panning fails.
                    float mx=midX(event),my=midY(event);
                    panX+=mx-midpointX;panY+=my-midpointY;
                    midpointX=mx;midpointY=my;clamp();invalidate();
                    snipMultitouch=true;
                    return true;
                }
                float x=event.getX(captureIndex>=0?captureIndex:0);
                float y=event.getY(captureIndex>=0?captureIndex:0);
                if(tool==8&&!snipPan&&!ignoreFingerSnip&&snipVertex>=0&&!scaler.isInProgress()){
                    float distance=(float)Math.hypot(x-snipDownX,y-snipDownY);
                    if(!dragWasRecorded&&distance>Math.max(stylusActive?dp(2):touchSlop,dp(1))){
                        recordSnip();dragWasRecorded=true;
                    }
                    if(dragWasRecorded){
                        float[] pos=viewToArtwork(x,y);
                        snipDraft.set(snipVertex,new float[]{Math.max(0,Math.min(1,pos[0]/gw)),
                            Math.max(0,Math.min(1,pos[1]/gh))});
                        invalidate();
                    }
                }else if(draggedMark>=0&&!scaler.isInProgress()){
                    // Keep the complete cut mark reachable, including in immersive
                    // full-screen mode. Coordinates remain in physical artwork mm.
                    float marginPx=Math.max(dp(7),settings.markSizeMm*.5f*scale*zoom+dp(3));
                    float dragX=RegistrationViewportMath.clampTouch(x,getWidth(),marginPx);
                    float dragY=RegistrationViewportMath.clampTouch(y,getHeight(),marginPx);
                    float[] pos=viewToArtwork(dragX,dragY);
                    if(settings.marks==null)settings.marks=RegistrationMarks.automaticPositions(
                        gw,gh,settings.markSizeMm,settings.registrationClearanceMm,settings.circleCrop);
                    settings.marks[draggedMark][0]=RegistrationCutGeometry.clampManual(pos[0],gw,settings.markSizeMm,settings.registrationClearanceMm);
                    settings.marks[draggedMark][1]=RegistrationCutGeometry.clampManual(pos[1],gh,settings.markSizeMm,settings.registrationClearanceMm);
                    invalidate();
                }else if((tool!=8||snipPan)&&!scaler.isInProgress()){
                    panX+=x-lastX;panY+=y-lastY;clamp();invalidate();
                }
                lastX=x;lastY=y;return true;
            }
            if(kind==MotionEvent.ACTION_UP){
                transforming=false;releaseGestureSnapshot();invalidate();
                if(holdCompare){holdCompare=false;invalidate();}
                if(tool==8){
                    if(event.getPointerId(0)==snipPointerId)
                        finishSnipContact(event.getX(),event.getY(),gw,gh);
                    else {snipPointerId=MotionEvent.INVALID_POINTER_ID;stylusActive=false;}
                    return true;
                }
                stylusActive=false;
                if(draggedMark>=0){draggedMark=-1;markDragFixedPad=-1f;
                    originalMarks=null;requestRegistrationRender();if(tool==5)setTool(5);}
                else if(!snipMultitouch&&Math.hypot(event.getX()-snipDownX,event.getY()-snipDownY)<=touchSlop)performClick();
                return true;
            }
            return true;
        }

    }
    @Override public void onTrimMemory(int level){
        super.onTrimMemory(level);
        if(PlatformCompat.shouldReleaseCaches(level)){
            portraitCache.clear();
            specialtyBaseCache.clear();specialtyLitCache.clear();specialtyFieldCache.clear();GeometryTemplateCache.clear();newStyleGeometryMemo.clear();draftSourceCache.clear();ScaleAwarePhotoSampler.clearLightCache();
            toolPanelCache.clear();projectThumbnails.evictAll();
            android.util.Log.d("VinylPerf","trimMemory level="+level+" clearedReconstructibleCaches");
        }
    }
    // ---- D14 library id handoff -------------------------------------------------------
    private LibraryIdHandoff.Store libraryHandoffStore(){
        final android.content.SharedPreferences prefs=getPreferences(MODE_PRIVATE);
        return new LibraryIdHandoff.Store(){
            @Override public String get(String key){return prefs.getString(key,null);}
            @SuppressWarnings("ApplySharedPref") // durability matters more than latency here (IO thread)
            @Override public void putAll(java.util.Map<String,String> values){
                android.content.SharedPreferences.Editor edit=prefs.edit();
                for(java.util.Map.Entry<String,String> e:values.entrySet())edit.putString(e.getKey(),e.getValue());
                edit.commit();
            }
            @Override public void removeAll(String... keys){
                android.content.SharedPreferences.Editor edit=prefs.edit();for(String k:keys)edit.remove(k);edit.apply();
            }
        };
    }
    /** The token of the document on screen: the restored photo once loaded, else the autosaved one. */
    private String displayedPhotoToken(){
        if(photo!=null&&!startupRestoring)return photoPairToken(photoRevision);
        return getPreferences(MODE_PRIVATE).getString(PhotoPairToken.PREF,"");
    }
    private void adoptLibraryHandoff(){
        if(activityDestroyed)return;
        LibraryIdHandoff.Adopted adopted=LibraryIdHandoff.adopt(libraryHandoffStore(),currentLibraryId,displayedPhotoToken());
        if(adopted==null)return;
        currentLibraryId=adopted.id;projectLabel=adopted.name;
        getPreferences(MODE_PRIVATE).edit().putString("currentLibraryId",adopted.id)
            .putString("currentLibraryName",adopted.name).apply();
        projectThumbnails.remove(adopted.id);
        if(status!=null)updateEditorSubtitle();
    }
    /** Covers a save that completes AFTER this Activity was created. */
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener libraryHandoffListener=(prefs,key)->{
        if(LibraryIdHandoff.ID.equals(key))postUi(this::adoptLibraryHandoff);
    };
    @Override protected void onDestroy(){
        getPreferences(MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(libraryHandoffListener);
        discardPreparedStandardCutZip();discardPreparedSpecialtyCutZip(); // M5: staged specialty ZIP temp file
        activityDestroyed=true;
        lifecycleUi.close();
        for(ProjectLoadOwnership pending:pendingProjectLoads)pending.cancel();
        pendingProjectLoads.clear();
        serial++;alternateGeneration++;startupRestoreSerial++;
        invalidatePreparedPalette();paletteWorker.close();reductionWorker.close();
        if(Build.VERSION.SDK_INT>=33&&systemBackCallback!=null){
            BackApi33.unregister(this,systemBackCallback);
            systemBackCallback=null;
        }
        cancelStartupRestore();super.onDestroy();handler.removeCallbacksAndMessages(null);
        toolPanelCache.clear();projectThumbnails.evictAll();draftSourceCache.clear();ScaleAwarePhotoSampler.clearLightCache();
        projectThumbnails.close();
        libraryJobs.close();
        renderController.close();specialtyFieldCache.clear();GeometryTemplateCache.clear();newStyleGeometryMemo.clear();}
}

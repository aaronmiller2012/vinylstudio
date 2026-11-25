package com.halftonestudio;

import java.util.ArrayList;

/**
 * Vinyl-cuttable photographic line families. All shapes are filled closed polygons
 * (or beaded circles) in millimeters, never SVG strokes or clipping paths.
 *
 * These are image-guided approximations, not a facial-landmark detector.
 * Small-portrait detail strokes are added only near luminance edges and keep
 * a separate reserved corridor between their neighboring primary ribbons.
 */
public final class LineArtEngine {
    private LineArtEngine() {}

    public static boolean isLineStyle(int style) {
        switch (style) {
            case VinylEngine.LINE: case VinylEngine.WAVE: case VinylEngine.HATCH:
            case VinylEngine.STRAIGHT: case VinylEngine.CONTOUR: case VinylEngine.CROSSHATCH:
            case VinylEngine.RINGS: case VinylEngine.RAYS: case VinylEngine.ZIGZAG:
            case VinylEngine.BROKEN: case VinylEngine.BEADED: case VinylEngine.FADE:
                return true;
            default: return false;
        }
    }

    /** Portrait midtone recovery modifies width along the parallel ribbon family.
     * Rings, rays and bead shapes use independent geometry engines and therefore
     * must not expose a slider that has no effect on their output.
     */
    public static boolean supportsPortraitMidtones(int style) {
        return isLineStyle(style) && style!=VinylEngine.RINGS
            && style!=VinylEngine.RAYS && style!=VinylEngine.BEADED;
    }

    /** Edge-aware width and continuity for tonal line ribbons. */
    public static boolean supportsShapeDefinition(int style) {
        return style==VinylEngine.LINE||style==VinylEngine.CONTOUR||style==VinylEngine.WAVE
            ||style==VinylEngine.STRAIGHT||style==VinylEngine.FADE||style==VinylEngine.HATCH;
    }

    public static void generate(VinylEngine.Result result, int[] src, int w, int h, int layerIndex, int style) {
        // Four-color vintage ribbon geometry is frozen from the user's v2.24 source.
        // Anchored CMYK sheets may use it in 4–8-color projects; spot accents
        // retain the current renderer and do not inherit the classic CMYK screen.
        VinylEngine.Settings settings=result.settings;
        if(settings.classicCmykLines && settings.mode==VinylEngine.CMYK
                && layerIndex>=0 && layerIndex<settings.layers.size()
                && settings.layers.get(layerIndex).cmykChannel>=0) {
            ClassicLineArtEngine.generate(result,src,w,h,layerIndex,style);
            return;
        }
        if (style==VinylEngine.RINGS) { concentric(result,src,w,h,layerIndex); return; }
        if (style==VinylEngine.RAYS) { rays(result,src,w,h,layerIndex); return; }
        if (style==VinylEngine.BEADED) { beads(result,src,w,h,layerIndex); return; }
        if (style==VinylEngine.HATCH || style==VinylEngine.CROSSHATCH) {
            // Never cross independently cut ribbons within one opaque vinyl sheet.
            // CMYK and custom vinyl can achieve crosshatching by using different
            // line angles on separate physical color layers. A second pass here
            // would make duplicate internal cut paths at every intersection.
            parallel(result,src,w,h,layerIndex,style,false,false);
            return;
        }
        parallel(result,src,w,h,layerIndex,style,false,false);
        VinylEngine.Settings s=result.settings;
        // Only straight/fade styles can reserve half-spacing lanes safely.
        // Contour and wave centerlines move laterally, so a second lane can
        // intersect its neighbors on the same physical vinyl sheet. For those
        // styles, detailBoost enhances the existing ribbon instead.
        if(s.detailBoost>0.001f && (style==VinylEngine.STRAIGHT || style==VinylEngine.FADE)) {
            parallel(result,src,w,h,layerIndex,style,false,true);
        }
    }

    private static float limit(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    private static float tone(VinylEngine.Result r,int[] src,int w,int h,int li,float x,float y){
        VinylEngine.Settings settings=r.settings;
        int pixel=VinylEngine.at(src,w,h,x,y,settings.widthMm,r.heightMm);
        float base=VinylEngine.coverage(pixel,settings,li);
        int style=settings.layers.get(li).lineStyleOverride>=0?
            settings.layers.get(li).lineStyleOverride:settings.pattern;
        if(settings.mode!=VinylEngine.CMYK || !settings.classicCmykLines ||
           settings.lineHighlightRetention<=.0001f ||
           style==VinylEngine.BROKEN || style==VinylEngine.FADE || (pixel>>>24)<128)return base;
        // Full-photo tonal analysis happens before vector-level Snip clipping.
        float priority=r.photoSampler==null?0f:
            r.photoSampler.regionalPriority(x,y,base);
        return CmykLineVisibility.apply(base,settings.lineHighlightRetention,priority);
    }
    private static float lum(VinylEngine.Result r,int[] src,int w,int h,float x,float y){
        int p=VinylEngine.at(src,w,h,x,y,r.settings.widthMm,r.heightMm);
        int alpha=(p>>>24)&255;
        if(alpha<255){int bg=r.settings.separationBackground;int rr=(((p>>16)&255)*alpha+((bg>>16)&255)*(255-alpha))/255;
            int gg=(((p>>8)&255)*alpha+((bg>>8)&255)*(255-alpha))/255;
            int bb=((p&255)*alpha+(bg&255)*(255-alpha))/255;
            return (.2126f*rr+.7152f*gg+.0722f*bb)/255f;
        }
        return (.2126f*((p>>16)&255)+.7152f*((p>>8)&255)+.0722f*(p&255))/255f;
    }
    private static float edge(VinylEngine.Result r,int[] src,int w,int h,float x,float y,float probe){
        float dx=lum(r,src,w,h,x+probe,y)-lum(r,src,w,h,x-probe,y);
        float dy=lum(r,src,w,h,x,y+probe)-lum(r,src,w,h,x,y-probe);
        return limit((float)Math.sqrt(dx*dx+dy*dy)*1.2f,0,1);
    }
    /** Lightweight transverse footprint keeps narrow photo features between carriers. */
    private static float carrierTone(VinylEngine.Result r,int[] src,int w,int h,int li,
                                     float x,float y,float nx,float ny,float spacing,float center){
        VinylEngine.Settings s=r.settings;
        if(center<=.008f || s.lineInvert || s.linePresenceRecovery<=.001f)return center;
        float probe=Math.min(1.0f,Math.max(.25f,spacing*.24f));
        float a=0f,b=0f;
        float ax=x+nx*probe,ay=y+ny*probe,bx=x-nx*probe,by=y-ny*probe;
        if(ax>=0&&ay>=0&&ax<s.widthMm&&ay<r.heightMm){
            int pixel=VinylEngine.at(src,w,h,ax,ay,s.widthMm,r.heightMm);
            if((pixel>>>24)>=128)a=VinylEngine.coverage(pixel,s,li);
        }
        if(bx>=0&&by>=0&&bx<s.widthMm&&by<r.heightMm){
            int pixel=VinylEngine.at(src,w,h,bx,by,s.widthMm,r.heightMm);
            if((pixel>>>24)>=128)b=VinylEngine.coverage(pixel,s,li);
        }
        int actualStyle=s.layers.get(li).lineStyleOverride>=0?
            s.layers.get(li).lineStyleOverride:s.pattern;
        if(supportsShapeDefinition(actualStyle))
            return LineToneRecovery.edgePreservingDirectionalFootprint(center,a,b,s.shapeDefinition);
        return LineToneRecovery.directionalFootprint(center,a,b);
    }


    private static float gamma(VinylEngine.Settings s,VinylEngine.Layer l){return l.lineGammaOverride>0?l.lineGammaOverride:s.lineGamma;}
    private static float halfWidth(VinylEngine.Settings s,VinylEngine.Layer l,float spacing,float weight,
                                   float displacement,boolean secondary,boolean detail,boolean reserveDetailLanes){
        float max=s.lineMaxFraction*spacing*.5f*l.lineWidthScale;
        max=Math.min(max,Math.max(0,(spacing-s.bridgeMm-2*displacement)*.46f));
        // Previous .20*spacing main-line cap made useful midtones thinner than
        // minimumMm whenever detail strokes were enabled. Allocate a corridor
        // to each line family without starving the main portrait ribbons.
        if(reserveDetailLanes && s.detailBoost>.001f && !secondary && !detail)
            max=Math.min(max,Math.max(0,spacing*.5f-s.bridgeMm-spacing*.09f*s.detailBoost));
        if(secondary)max*=.40f;
        if(detail)max=Math.min(max,spacing*.09f*limit(s.detailBoost,0,1));
        int actualStyle=l.lineStyleOverride>=0?l.lineStyleOverride:s.pattern;
        boolean tonalFamily=supportsMicroContinuity(actualStyle) ||
            actualStyle==VinylEngine.HATCH || actualStyle==VinylEngine.CROSSHATCH;
        float recovery=(!secondary&&!detail&&!s.lineInvert&&tonalFamily)
            ?Math.max(s.linePresenceRecovery,s.lineHighlightRetention):0f;
        float width=max*LineToneRecovery.widthResponse(weight,gamma(s,l),recovery);
        return width;
    }
    private static boolean valid(VinylEngine.Result r,float x,float y,float margin){
        return x>=margin && y>=margin && x<=r.settings.widthMm-margin && y<=r.heightMm-margin;
    }
    private static void flush(VinylEngine.Result r,int li,ArrayList<float[]> run){
        if(run.size()<2)return;
        for(ArrayList<float[]> piece:SnipRibbonPostClip.split(run,r.settings.snips,
                r.settings.widthMm,r.heightMm))emitRibbon(r,li,piece,0);
    }
    private static void emitRibbon(VinylEngine.Result r,int li,ArrayList<float[]> run,int depth){
        if(run.size()<2)return;
        int style=r.settings.layers.get(li).lineStyleOverride>=0
            ?r.settings.layers.get(li).lineStyleOverride:r.settings.pattern;
        float[] xy=(style==VinylEngine.LINE||style==VinylEngine.CONTOUR)
            ?LineRibbonRepair.safeOutline(run,r.settings.minimumMm)
            :LineRibbonRepair.outline(run);
        if(xy==null)return;
        if(r.settings.snips!=null&&!r.settings.snips.isEmpty()&&
           !r.settings.snips.allowsPolygon(xy,r.settings.widthMm,r.heightMm)){
            // An offset contour may cross a concave Snip even when carrier
            // samples are safe. Split locally; never discard the full ribbon.
            if(run.size()<4||depth>=12)return;
            int mid=run.size()/2;
            emitRibbon(r,li,new ArrayList<>(run.subList(0,mid)),depth+1);
            emitRibbon(r,li,new ArrayList<>(run.subList(mid,run.size())),depth+1);
            return;
        }
        VinylEngine.add(r,li,VinylEngine.Shape.polygon(xy));
    }
    /** Never modifies Broken, Fade, Hatches, Rings, Rays or Beaded lines. */
    public static boolean supportsMicroContinuity(int style){
        return style==VinylEngine.LINE||style==VinylEngine.STRAIGHT
            ||style==VinylEngine.CONTOUR||style==VinylEngine.WAVE
            ||style==VinylEngine.ZIGZAG;
    }
    private static void addSample(VinylEngine.Result r,int li,ArrayList<float[]> run,float x,float y,float half){
        if(half*2<r.settings.minimumMm){flush(r,li,run);run.clear();}
        else run.add(new float[]{x,y,half});
    }
    private static void addRefinedSample(VinylEngine.Result r,int li,ArrayList<float[]> run,
                                         float x,float y,float half,float maxHalf,
                                         float nx,float ny,float centerCoverage,
                                         ScaleAwarePhotoSampler.Coverage shade,int style){
        if(2f*half<r.settings.minimumMm){flush(r,li,run);run.clear();return;}
        float bias=0;
        if(r.photoSampler!=null && shade!=null &&
           supportsShapeDefinition(style) && !r.settings.lineInvert && centerCoverage>.04f){
            bias=r.photoSampler.ribbonBiasWithinSnip(x,y,nx,ny,half,centerCoverage,
                   shade,null) *r.settings.shapeDefinition;
            // Never escape the existing lane's maximum width/corridor.
            if(half>0) {
                float allowed=Math.max(0,Math.min(.18f,maxHalf/half-1f));
                bias=limit(bias,-allowed,allowed);
            }
        }
        run.add(new float[]{x,y,half,bias});
    }
    private static boolean safeSample(VinylEngine.Result r,ArrayList<float[]> run,float x,float y,float widthAllowance){
        VinylEngine.Settings s=r.settings;
        if(!valid(r,x,y,s.marginMm+widthAllowance))return false;
        // Circle framing remains part of image geometry; a freeform Snip is
        // applied only after line widths, continuity and bends are finalized.
        return LineCropBoundary.accepts(s.circleCrop,x,y,s.widthMm,r.heightMm,
                                    s.marginMm,widthAllowance);
    }
    private static float tri(double radians){
        double v=radians/(Math.PI*2);v-=Math.floor(v);
        return (float)(1-4*Math.abs(v-.5));
    }
    private static void parallel(VinylEngine.Result r,int[] src,int w,int h,int li,int style,
                                 boolean secondary,boolean detail){
        VinylEngine.Settings s=r.settings;VinylEngine.Layer l=s.layers.get(li);
        final ScaleAwarePhotoSampler.Coverage shade=p->VinylEngine.coverage(p,s,li);
        float spacing=Math.max(.8f,l.spacing),extent=(float)Math.hypot(s.widthMm,r.heightMm)*.57f;
        float tStep=Math.min(s.lineSampleMm,Math.max(.18f,spacing*.22f));
        if((2*extent/spacing)*(2*extent/tStep)>600000)throw new IllegalArgumentException("Line screen has too many samples; increase spacing");
        boolean wave=style==VinylEngine.WAVE, zig=style==VinylEngine.ZIGZAG;
        boolean contour=style==VinylEngine.LINE||style==VinylEngine.CONTOUR||style==VinylEngine.HATCH;
        // Crosshatch on opaque vinyl uses a single cutter-safe family per color.
        // Rotate those families as a group to make the Cross Angle setting real;
        // different physical colors supply the crossing directions.
        double radians=Math.toRadians(l.angle+(style==VinylEngine.CROSSHATCH?s.crossAngleDeg:0f)
                +(secondary?s.crossAngleDeg:0f));
        float ca=(float)Math.cos(radians),sa=(float)Math.sin(radians),nx=-sa,ny=ca;
        float frequency=2f*(float)Math.PI*Math.max(.1f,s.waveCyclesPerInch)/VinylEngine.MM_PER_INCH;
        // v2.50.3: Keep the user's proven v2.49.4 LINE behavior untouched.
        // CONTOUR alone has a slightly wider, bounded bending envelope.
        // This makes CONTOUR genuinely distinct without affecting other line families.
        float displacement=(wave||zig?spacing*Math.min(.22f,s.waveAmplitude):
            contour?spacing*(style==VinylEngine.CONTOUR?.18f:.16f)*s.contourStrength:0);
        float maxHalf=Math.min(spacing*.49f*l.lineWidthScale*s.lineMaxFraction,
                Math.max(0,(spacing-s.bridgeMm-2*displacement)*.46f));
        if(s.detailBoost>0 && !secondary && (style==VinylEngine.STRAIGHT || style==VinylEngine.FADE))
            maxHalf=Math.min(maxHalf,Math.max(0,spacing*.5f-s.bridgeMm-spacing*.09f*s.detailBoost));
        if(detail)maxHalf=Math.min(maxHalf,spacing*.09f*s.detailBoost);
        if(secondary)maxHalf*=.40f;
        if(2*maxHalf<s.minimumMm)return;
        float start=-extent+(detail?spacing*.5f:0);
        for(float v=start;v<=extent;v+=spacing){CancellationCheckpoint.check();
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            ArrayList<float[]> run=new ArrayList<>();float[] pending=null;float smooth=0;
            boolean presence=(s.linePresenceRecovery>.001f || s.lineHighlightRetention>.001f) && supportsMicroContinuity(style)
                && !secondary && !detail && !s.lineInvert;
            ArrayList<LinePresenceTracker.Sample> lane=presence?new ArrayList<>():null;
            LinePresenceTracker.Metrics metrics=r.lineMetrics.get(li);
            boolean micro=!presence && s.lineContinuity>.001f && supportsMicroContinuity(style)
                && !secondary && !detail;
            int laneNumber=Math.round(v/spacing);
            int cancelTick1=0;
            for(float t=-extent;t<=extent+tStep*.5f;t+=tStep){
                if((++cancelTick1&63)==0)CancellationCheckpoint.check();
                float x=s.widthMm*.5f+t*ca-v*sa,y=r.heightMm*.5f+t*sa+v*ca;
                float bend=0;
                if(wave)bend=displacement*(float)Math.sin(t*frequency+v/spacing*.24f);
                else if(zig)bend=displacement*tri(t*frequency+v/spacing*.18f);
                else if(contour){
                    float probe=Math.max(.22f,Math.min(spacing*.52f,1.7f));
                    float d=lum(r,src,w,h,x-nx*probe,y-ny*probe)-lum(r,src,w,h,x+nx*probe,y+ny*probe);
                    float target=d*displacement*1.5f;
                    float response=.08f+.22f*(1f-limit(s.lineSmoothing,0,1));
                    smooth=limit(smooth+(target-smooth)*response,-displacement,displacement);
                    bend=smooth;
                }
                x+=nx*bend;y+=ny*bend;
                // Snip recovery uses a true hard barrier. A candidate never jumps
                // across an excluded hole, even if both its endpoints are allowed.
                float safetyAllowance=maxHalf+displacement;
                boolean sampleSafe=safeSample(r,run,x,y,safetyAllowance);
                if(!sampleSafe){
                    if(presence)lane.add(new LinePresenceTracker.Sample(x,y,0,0,false,false));
                    flush(r,li,run);run.clear();pending=null;continue;
                }
                float weight=tone(r,src,w,h,li,x,y);
                boolean backgroundEnabled=s.portraitBackgroundGuard>.001f && supportsMicroContinuity(style)
                    && !secondary && !detail && !s.lineInvert;
                float bgScale=backgroundEnabled?PortraitBackgroundGuard.scale(src,w,h,x,y,s.widthMm,r.heightMm,s.separationBackground,s.portraitBackgroundGuard):1f;
                if(bgScale<.999f)metrics.backgroundDownweighted++;
                weight*=bgScale;
                // Only source-supported same-channel tone may promote a carrier.
                float sourceCoverage=weight;
                if(!secondary&&!detail&&!s.lineInvert && supportsMicroContinuity(style))
                    weight=carrierTone(r,src,w,h,li,x,y,nx,ny,spacing,weight);
                if(s.lineInvert)weight=1-weight;
                float sharedEdge=-1f;
                if(supportsPortraitMidtones(style)
                        && !secondary && !detail && s.portraitMidtones>0){
                    // Lift only existing nonzero channel information; a blank pixel
                    // remains blank. This retains highlights but prevents a harsh
                    // cut-width threshold from erasing most of a face.
                    float mid=(float)Math.sqrt(limit(weight,0f,1f));
                    weight+=s.portraitMidtones*(mid-weight);
                    // Reinforce edges near eyes, lips and hair, scaled by tonal content.
                    sharedEdge=edge(r,src,w,h,x,y,Math.max(.22f,spacing*.30f));
                    if(sourceCoverage>.008f)
                        weight=limit(weight+s.portraitMidtones*.16f*sharedEdge*(1-weight),0f,1f);
                }
                float shapeEdge=0f;
                if(s.shapeDefinition>.001f && !secondary && !detail && supportsShapeDefinition(style) && weight>.04f){
                    // Existing channel information only: paper-white pixels never gain cuts.
                    if(sharedEdge<0)sharedEdge=edge(r,src,w,h,x,y,Math.max(.22f,spacing*.30f));
                    shapeEdge=sharedEdge;
                    // Black carries structure; chromatic layers get a lighter touch.
                    float gain=(l.cmykChannel==3||s.mode!=VinylEngine.CMYK)?1f:.55f;
                    if(sourceCoverage>.008f)
                        weight=limit(weight+.55f*s.shapeDefinition*gain*shapeEdge*(1-weight),0f,1f);
                }
                if(s.detailBoost>.001f && !secondary && !detail &&
                        (style==VinylEngine.LINE || style==VinylEngine.CONTOUR || style==VinylEngine.WAVE)){
                    float localEdge=edge(r,src,w,h,x,y,Math.max(.25f,spacing*.28f));
                    if(sourceCoverage>.008f)
                        weight=limit(weight+.20f*s.detailBoost*localEdge*(1-weight),0f,1f);
                }
                if(secondary)weight=limit((weight-s.hatchThreshold)/(1-s.hatchThreshold),0,1);
                if(!secondary && (style==VinylEngine.HATCH || style==VinylEngine.CROSSHATCH)){
                    // The engraving/shadow slider must change real cut widths.
                    // A secondary intersecting hatch would double-cut the same
                    // vinyl sheet, so favor darker portions of this safe family.
                    float threshold=.30f*s.hatchThreshold;
                    weight=limit((weight-threshold)/(1f-threshold),0f,1f);
                }
                if(detail){
                    // Extra half-spacing strokes appear near fine image structure, not uniformly.
                    float e=edge(r,src,w,h,x,y,Math.max(.2f,spacing*.25f));
                    float emphasis=limit((e-(.06f+(.15f*(1-s.detailBoost))))/.28f,0,1);
                    weight*=emphasis;
                }
                if(style==VinylEngine.BROKEN){
                    if(weight<.32f){flush(r,li,run);run.clear();continue;}
                    float period=Math.max(spacing*2.5f,3.0f);
                    float phase=(t+v*.21f)/period;phase-=Math.floor(phase);
                    if(weight<.84f&&phase>(.35f+.50f*weight)){
                        flush(r,li,run);run.clear();continue;
                    }
                }
                if(style==VinylEngine.FADE)weight=limit((weight-.08f)/.92f,0,1);
                float bendAllowance=displacement;
                if(!secondary&&!detail&&s.linePresenceRecovery>.001f&&(wave||zig)){
                    float phase0=t*frequency+v/spacing*(wave?.24f:.18f);
                    float phasePrev=t*frequency+(v-spacing)/spacing*(wave?.24f:.18f);
                    float phaseNext=t*frequency+(v+spacing)/spacing*(wave?.24f:.18f);
                    float neighborPrev=displacement*(wave?(float)Math.sin(phasePrev):tri(phasePrev));
                    float neighborNext=displacement*(wave?(float)Math.sin(phaseNext):tri(phaseNext));
                    float actual=displacement*(wave?(float)Math.sin(phase0):tri(phase0));
                    bendAllowance=LineToneRecovery.localBendAllowance(actual,neighborPrev,neighborNext,.08f);
                }
                float localMaxHalf=halfWidth(s,l,spacing,1f,bendAllowance,secondary,detail,
                    (style==VinylEngine.STRAIGHT || style==VinylEngine.FADE));
                float half=halfWidth(s,l,spacing,weight,bendAllowance,secondary,detail,
                    (style==VinylEngine.STRAIGHT || style==VinylEngine.FADE));
                if(r.opaqueLineScales!=null && !secondary && !detail){
                    int ix=Math.max(0,Math.min(r.opaqueLineImageWidth-1,
                            (int)(x/s.widthMm*r.opaqueLineImageWidth)));
                    int iy=Math.max(0,Math.min(r.opaqueLineImageHeight-1,
                            (int)(y/r.heightMm*r.opaqueLineImageHeight)));
                    int pixelIndex=iy*r.opaqueLineImageWidth+ix;
                    float sheetScale=r.opaqueLineScales[pixelIndex]*r.opaqueLineDetails[pixelIndex];
                    // Compensate the ACTUAL final width, not raw source CMYK.
                    // The minimum-width hysteresis below still owns short
                    // weak segments; never silently emit subminimum paths.
                    float physicalMax=localMaxHalf;
                    float physicalHalf=half;
                    half=Math.min(physicalMax,half*sheetScale);
                    localMaxHalf=Math.min(physicalMax,localMaxHalf*sheetScale);
                    // Minimum-width vinyl is a binary decision. Width clipping
                    // alone would DELETE facial midtone lanes after quantization.
                    // Keep a low-discrepancy selection of EXISTING ribbons at
                    // the legal width. A lane's phase stays stable along its
                    // length: no checkerboard chatter, new centerlines, or
                    // adjacent duplicate cuts.
                    float minHalf=s.minimumMm*.5f+.012f;
                    if(half<minHalf && physicalHalf>=minHalf &&
                       physicalMax>=minHalf && sourceCoverage>.008f){
                        float phase=(float)(laneNumber*.61803398875+
                            (l.cmykChannel+1)*.38196601125);
                        phase-=Math.floor(phase);
                        if(phase<Math.min(1f,half/minHalf))half=minHalf;
                    }
                    // IMPORTANT: the opaque stack budget controls the *geometry*
                    // of each layer, not whether the original photograph
                    // contains this channel. RC102 multiplied the source
                    // evidence by sheetScale here. The hysteresis stage then
                    // rejected entire real midtone runs, especially magenta
                    // and cyan, as if the source photo contained no color.
                    // Preserve unscaled source evidence for presence decisions;
                    // the final ribbon half-width still obeys the calibrated
                    // coverage budget and the cutter minimum.
                }
                if(shapeEdge*s.shapeDefinition>.18f && weight>.12f){
                    // Keep strong feature edges as connected cuttable ribbons instead of dropping to dashes.
                    float floorHalf=s.minimumMm*.5f+.02f;
                    if(half<floorHalf&&floorHalf<=localMaxHalf)half=floorHalf;
                }
                // Snip is applied to completed carrier sections in flush(),
                // not here: never alter tone, width or presence during tracing.
                if(presence){
                    int sourcePixel=VinylEngine.at(src,w,h,x,y,s.widthMm,r.heightMm);
                    boolean subject=PortraitBackgroundGuard.warmSubject(
                        sourcePixel,x,y,s.widthMm,r.heightMm);
                    // The new opt-in control also handles grayscale portraits
                    // prepared on an opaque white background. Existing saved
                    // settings retain the exact previous warm-subject detection.
                    if(s.lineHighlightRetention>0.001f && !subject)
                        subject=PortraitBackgroundGuard.grayPortraitSubject(
                            sourcePixel,x,y,s.widthMm,r.heightMm);
                    // Snip and crop alpha are geometric exclusions, not a reason
                    // to replace the photograph-based portrait subject heuristic.
                    // Using every retained pixel as a subject changes hysteresis
                    // and turns skin highlights/background into dense CMYK ribbons.
                    // The existing allowed/safeSample checks own hard boundaries.
                    lane.add(new LinePresenceTracker.Sample(x,y,half,sourceCoverage,sourceCoverage>.008f,subject));
                }else if(micro){
                    // A valid candidate cannot start a ribbon. It must have real
                    // color evidence and sit BETWEEN two already-cuttable samples.
                    // A second rejected sample or any blank resets the entire gap.
                    float minHalf=s.minimumMm*.5f;
                    float weakMin=minHalf*(1f-.30f*s.lineContinuity);
                    if(half<minHalf){
                        if(pending==null && run.size()>=2 && half>=weakMin
                                && sourceCoverage>.10f){
                            pending=new float[]{x,y,minHalf+.006f};
                        }else{
                            flush(r,li,run);run.clear();pending=null;
                        }
                        continue;
                    }
                    if(pending!=null){
                        // Consecutive accepted samples bound a short real
                        // interruption. Never interpolate unsupported pixels.
                        run.add(pending);pending=null;
                    }
                    addRefinedSample(r,li,run,x,y,half,maxHalf,nx,ny,sourceCoverage,shade,style);
                }else if(presence) addSample(r,li,run,x,y,half);
                else addRefinedSample(r,li,run,x,y,half,maxHalf,nx,ny,sourceCoverage,shade,style);
            }
            if(presence){
                for(ArrayList<float[]> segment:LinePresenceTracker.extract(lane,s.minimumMm,tStep,s.linePresenceRecovery,laneNumber,l.cmykChannel,metrics,s.lineHighlightRetention))
                    flush(r,li,segment);
            }else flush(r,li,run);
        }
    }
    private static void beads(VinylEngine.Result r,int[] src,int w,int h,int li){
        VinylEngine.Settings s=r.settings;VinylEngine.Layer l=s.layers.get(li);
        float spacing=Math.max(.8f,l.spacing),extent=(float)Math.hypot(s.widthMm,r.heightMm)*.56f;
        float beadGap=spacing*.72f;
        // Bead Size quick slider must change the actual circle radius. The
        // maximum remains within each bead's isolated cut corridor.
        float beadScale=.45f+.55f*limit(s.lineMaxFraction/.95f,0f,1f);
        float limitRadius=Math.max(0,Math.min(spacing*.30f,beadGap*.38f)*beadScale);
        if(limitRadius*2<s.minimumMm)return;
        double a=Math.toRadians(l.angle);float ca=(float)Math.cos(a),sa=(float)Math.sin(a);
        if((2*extent/spacing)*(2*extent/beadGap)>550000)throw new IllegalArgumentException("Beaded lines too dense; increase spacing");
        int cancelTick2=0;
        for(float v=-extent;v<=extent;v+=spacing)for(float t=-extent;t<=extent;t+=beadGap){
            if((++cancelTick2&63)==0)CancellationCheckpoint.check();
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            float x=s.widthMm*.5f+t*ca-v*sa,y=r.heightMm*.5f+t*sa+v*ca;
            if(!valid(r,x,y,s.marginMm+limitRadius))continue;
            float weight=tone(r,src,w,h,li,x,y);
            if(s.lineInvert)weight=1-weight;
            float rad=Math.min(limitRadius,limitRadius*l.lineWidthScale)*(float)Math.pow(weight,gamma(s,l));
            if(2*rad<s.minimumMm)continue;
            VinylEngine.add(r,li,VinylEngine.Shape.circle(x,y,rad));
        }
    }
    /**
     * Concentric ribbons have radial normals, not the open-polyline tangents used
     * by parallel stripes. A complete loop must have TWO closed cut contours:
     * the outer boundary and its paper opening. Joining the two ends of an open
     * ribbon polygon at the circle seam creates a self-intersection and double
     * cuts. For a partial arc we use radial offsets and two real end caps.
     */
    private static void flushRing(VinylEngine.Result r,int li,ArrayList<float[]> run,
                                  float cx,float cy,boolean closed){
        int n=run.size();if(n<2)return;
        if(closed && n>=4){
            // Remove the repeated 2PI sample; each ring below is independently closed by SVG Z.
            int points=n-1;
            float[] outer=new float[points*2],inner=new float[points*2];
            for(int i=0;i<points;i++){if(((i)&63)==0)CancellationCheckpoint.check();
                float[] q=run.get(i);
                float dx=q[0]-cx,dy=q[1]-cy;
                float d=(float)Math.hypot(dx,dy);
                if(d<=q[2]+.01f)return; // No zero-radius or inverted inner contour.
                float nx=dx/d,ny=dy/d;
                outer[2*i]=q[0]+nx*q[2];outer[2*i+1]=q[1]+ny*q[2];
                inner[2*i]=q[0]-nx*q[2];inner[2*i+1]=q[1]-ny*q[2];
            }
            VinylEngine.add(r,li,VinylEngine.Shape.compound(new float[][]{outer,inner}));
        }else{
            float[] xy=new float[n*4];
            for(int i=0;i<n;i++){if(((i)&63)==0)CancellationCheckpoint.check();
                float[] q=run.get(i);float dx=q[0]-cx,dy=q[1]-cy;
                float d=(float)Math.hypot(dx,dy);
                if(d<=q[2]+.01f)return;
                float nx=dx/d,ny=dy/d;
                xy[2*i]=q[0]+nx*q[2];xy[2*i+1]=q[1]+ny*q[2];
                int j=2*n+2*(n-i-1);
                xy[j]=q[0]-nx*q[2];xy[j+1]=q[1]-ny*q[2];
            }
            VinylEngine.add(r,li,VinylEngine.Shape.polygon(xy));
        }
    }
    private static void concentric(VinylEngine.Result r,int[] src,int w,int h,int li){
        VinylEngine.Settings s=r.settings;VinylEngine.Layer l=s.layers.get(li);
        float spacing=Math.max(.8f,l.spacing),cx=s.radialFocusX*s.widthMm,cy=s.radialFocusY*r.heightMm;
        float extent=(float)Math.max(Math.max(Math.hypot(cx,cy),Math.hypot(cx-s.widthMm,cy)),
                                  Math.max(Math.hypot(cx,cy-r.heightMm),Math.hypot(cx-s.widthMm,cy-r.heightMm)));
        float halfMax=Math.min(spacing*s.lineMaxFraction*.5f*l.lineWidthScale,(spacing-s.bridgeMm)*.46f);
        if(2*halfMax<s.minimumMm)return;
        // Rings have no visual orientation: angle alone cannot distinguish them.
        // Stagger radial phase by CMYK identity to prevent identical concentric cuts.
        float phase=s.mode==VinylEngine.CMYK&&l.cmykChannel>=0
                ? spacing*(l.cmykChannel*.20f):0f;
        for(float radius=Math.max(spacing*.9f,s.minimumMm)+phase;radius<=extent+spacing;radius+=spacing){CancellationCheckpoint.check();
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            int steps=Math.max(24,(int)Math.ceil(2*Math.PI*radius/Math.min(s.lineSampleMm,.5f)));
            steps=Math.min(steps,5000);
            ArrayList<float[]> run=new ArrayList<>();
            for(int k=0;k<=steps;k++){if(((k)&63)==0)CancellationCheckpoint.check();
                double a=2*Math.PI*k/steps;
                float x=cx+radius*(float)Math.cos(a),y=cy+radius*(float)Math.sin(a);
                if(!safeSample(r,run,x,y,halfMax)) {flushRing(r,li,run,cx,cy,false);run.clear();continue;}
                float weight=tone(r,src,w,h,li,x,y);
                if(s.lineInvert)weight=1-weight;
                float half=halfWidth(s,l,spacing,weight,0,false,false,false);
                if(half*2<s.minimumMm){flushRing(r,li,run,cx,cy,false);run.clear();}
                else run.add(new float[]{x,y,half});
            }
            boolean closed=run.size()==steps+1;
            flushRing(r,li,run,cx,cy,closed);
        }
    }
    private static void rays(VinylEngine.Result r,int[] src,int w,int h,int li){
        VinylEngine.Settings s=r.settings;VinylEngine.Layer l=s.layers.get(li);
        float spacing=Math.max(.8f,l.spacing),cx=s.radialFocusX*s.widthMm,cy=s.radialFocusY*r.heightMm;
        float outer=(float)Math.hypot(s.widthMm,r.heightMm);
        int rays=Math.max(12,Math.min(130,(int)(2*Math.PI*(outer*.43f)/spacing)));
        float inner=Math.max(2.5f*spacing,rays*spacing/(2f*(float)Math.PI));
        float step=Math.min(s.lineSampleMm,Math.max(.18f,spacing*.25f));
        float maxHalf=Math.min(spacing*s.lineMaxFraction*.5f*l.lineWidthScale,(spacing-s.bridgeMm)*.46f);
        if(2*maxHalf<s.minimumMm)return;
        for(int i=0;i<rays;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            double a=2*Math.PI*i/rays+Math.toRadians(l.angle);
            float ca=(float)Math.cos(a),sa=(float)Math.sin(a);
            ArrayList<float[]> run=new ArrayList<>();
            int cancelTick4=0;
            for(float t=inner;t<=outer;t+=step){
                if((++cancelTick4&63)==0)CancellationCheckpoint.check();
                float x=cx+t*ca,y=cy+t*sa;
                if(!safeSample(r,run,x,y,maxHalf)){flush(r,li,run);run.clear();continue;}
                float weight=tone(r,src,w,h,li,x,y);
                if(s.lineInvert)weight=1-weight;
                float half=halfWidth(s,l,spacing,weight,0,false,false,false);
                addSample(r,li,run,x,y,half);
            }
            flush(r,li,run);
        }
    }
}


/**
 * Unmodified v2.24 line geometry, retained as a pinned compatibility reference.
 * Only the class and constructor identifiers were renamed, preserving the old
 * width and segmentation algorithms. Requires v2.24 CMYK tone profile below.
 */
/**
 * Vinyl-cuttable photographic line families. All shapes are filled closed polygons
 * (or beaded circles) in millimeters, never SVG strokes or clipping paths.
 *
 * These are image-guided approximations, not a facial-landmark detector.
 * Small-portrait detail strokes are added only near luminance edges and keep
 * a separate reserved corridor between their neighboring primary ribbons.
 */
final class ClassicLineArtEngine {
    private ClassicLineArtEngine() {}

    public static boolean isLineStyle(int style) {
        switch (style) {
            case VinylEngine.LINE: case VinylEngine.WAVE: case VinylEngine.HATCH:
            case VinylEngine.STRAIGHT: case VinylEngine.CONTOUR: case VinylEngine.CROSSHATCH:
            case VinylEngine.RINGS: case VinylEngine.RAYS: case VinylEngine.ZIGZAG:
            case VinylEngine.BROKEN: case VinylEngine.BEADED: case VinylEngine.FADE:
                return true;
            default: return false;
        }
    }

    /** Portrait midtone recovery modifies width along the parallel ribbon family.
     * Rings, rays and bead shapes use independent geometry engines and therefore
     * must not expose a slider that has no effect on their output.
     */
    public static boolean supportsPortraitMidtones(int style) {
        return isLineStyle(style) && style!=VinylEngine.RINGS
            && style!=VinylEngine.RAYS && style!=VinylEngine.BEADED;
    }

    /** Edge-aware width and continuity for tonal line ribbons. */
    public static boolean supportsShapeDefinition(int style) {
        return style==VinylEngine.LINE||style==VinylEngine.CONTOUR||style==VinylEngine.WAVE
            ||style==VinylEngine.STRAIGHT||style==VinylEngine.FADE||style==VinylEngine.HATCH;
    }

    public static void generate(VinylEngine.Result result, int[] src, int w, int h, int layerIndex, int style) {
        if (style==VinylEngine.RINGS) { concentric(result,src,w,h,layerIndex); return; }
        if (style==VinylEngine.RAYS) { rays(result,src,w,h,layerIndex); return; }
        if (style==VinylEngine.BEADED) { beads(result,src,w,h,layerIndex); return; }
        if (style==VinylEngine.HATCH || style==VinylEngine.CROSSHATCH) {
            // Never cross independently cut ribbons within one opaque vinyl sheet.
            // CMYK and custom vinyl can achieve crosshatching by using different
            // line angles on separate physical color layers. A second pass here
            // would make duplicate internal cut paths at every intersection.
            parallel(result,src,w,h,layerIndex,style,false,false);
            return;
        }
        parallel(result,src,w,h,layerIndex,style,false,false);
        VinylEngine.Settings s=result.settings;
        // Only straight/fade styles can reserve half-spacing lanes safely.
        // Contour and wave centerlines move laterally, so a second lane can
        // intersect its neighbors on the same physical vinyl sheet. For those
        // styles, detailBoost enhances the existing ribbon instead.
        if(s.detailBoost>0.001f && (style==VinylEngine.STRAIGHT || style==VinylEngine.FADE)) {
            parallel(result,src,w,h,layerIndex,style,false,true);
        }
    }

    private static float limit(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    private static float tone(VinylEngine.Result r,int[] src,int w,int h,int li,float x,float y){
        VinylEngine.Settings settings=r.settings;
        int pixel=VinylEngine.at(src,w,h,x,y,settings.widthMm,r.heightMm);
        float base=VinylEngine.coverage(pixel,settings,li);
        int style=settings.layers.get(li).lineStyleOverride>=0?
            settings.layers.get(li).lineStyleOverride:settings.pattern;
        if(settings.mode!=VinylEngine.CMYK || !settings.classicCmykLines ||
           settings.lineHighlightRetention<=.0001f ||
           style==VinylEngine.BROKEN || style==VinylEngine.FADE || (pixel>>>24)<128)return base;
        // Full-photo tonal analysis happens before vector-level Snip clipping.
        float priority=r.photoSampler==null?0f:
            r.photoSampler.regionalPriority(x,y,base);
        return CmykLineVisibility.apply(base,settings.lineHighlightRetention,priority);
    }
    private static float lum(VinylEngine.Result r,int[] src,int w,int h,float x,float y){
        int p=VinylEngine.at(src,w,h,x,y,r.settings.widthMm,r.heightMm);
        int alpha=(p>>>24)&255;
        if(alpha<255){int bg=r.settings.separationBackground;int rr=(((p>>16)&255)*alpha+((bg>>16)&255)*(255-alpha))/255;
            int gg=(((p>>8)&255)*alpha+((bg>>8)&255)*(255-alpha))/255;
            int bb=((p&255)*alpha+(bg&255)*(255-alpha))/255;
            return (.2126f*rr+.7152f*gg+.0722f*bb)/255f;
        }
        return (.2126f*((p>>16)&255)+.7152f*((p>>8)&255)+.0722f*(p&255))/255f;
    }
    private static float edge(VinylEngine.Result r,int[] src,int w,int h,float x,float y,float probe){
        float dx=lum(r,src,w,h,x+probe,y)-lum(r,src,w,h,x-probe,y);
        float dy=lum(r,src,w,h,x,y+probe)-lum(r,src,w,h,x,y-probe);
        return limit((float)Math.sqrt(dx*dx+dy*dy)*1.2f,0,1);
    }
    private static float gamma(VinylEngine.Settings s,VinylEngine.Layer l){return l.lineGammaOverride>0?l.lineGammaOverride:s.lineGamma;}
    private static float halfWidth(VinylEngine.Settings s,VinylEngine.Layer l,float spacing,float weight,
                                   float displacement,boolean secondary,boolean detail,boolean reserveDetailLanes){
        float max=s.lineMaxFraction*spacing*.5f*l.lineWidthScale;
        max=Math.min(max,Math.max(0,(spacing-s.bridgeMm-2*displacement)*.46f));
        // Previous .20*spacing main-line cap made useful midtones thinner than
        // minimumMm whenever detail strokes were enabled. Allocate a corridor
        // to each line family without starving the main portrait ribbons.
        if(reserveDetailLanes && s.detailBoost>.001f && !secondary && !detail)
            max=Math.min(max,Math.max(0,spacing*.5f-s.bridgeMm-spacing*.09f*s.detailBoost));
        if(secondary)max*=.40f;
        if(detail)max=Math.min(max,spacing*.09f*limit(s.detailBoost,0,1));
        float width=max*(float)Math.pow(limit(weight,0,1),gamma(s,l));
        return width;
    }
    private static boolean valid(VinylEngine.Result r,float x,float y,float margin){
        return x>=margin && y>=margin && x<=r.settings.widthMm-margin && y<=r.heightMm-margin;
    }
    private static void flush(VinylEngine.Result r,int li,ArrayList<float[]> run){
        if(run.size()<2)return;
        for(ArrayList<float[]> piece:SnipRibbonPostClip.split(run,r.settings.snips,
                r.settings.widthMm,r.heightMm))emitRibbon(r,li,piece,0);
    }
    private static void emitRibbon(VinylEngine.Result r,int li,ArrayList<float[]> run,int depth){
        if(run.size()<2)return;
        float[] xy=LineRibbonRepair.outline(run);
        if(xy==null)return;
        if(r.settings.snips!=null&&!r.settings.snips.isEmpty()&&
           !r.settings.snips.allowsPolygon(xy,r.settings.widthMm,r.heightMm)){
            if(run.size()<4||depth>=12)return;
            int mid=run.size()/2;
            emitRibbon(r,li,new ArrayList<>(run.subList(0,mid)),depth+1);
            emitRibbon(r,li,new ArrayList<>(run.subList(mid,run.size())),depth+1);
            return;
        }
        VinylEngine.add(r,li,VinylEngine.Shape.polygon(xy));
    }
    private static void addSample(VinylEngine.Result r,int li,ArrayList<float[]> run,float x,float y,float half){
        if(half*2<r.settings.minimumMm){flush(r,li,run);run.clear();}
        else run.add(new float[]{x,y,half});
    }
    private static boolean safeSample(VinylEngine.Result r,ArrayList<float[]> run,float x,float y,float widthAllowance){
        VinylEngine.Settings s=r.settings;
        if(!valid(r,x,y,s.marginMm+widthAllowance))return false;
        // Circle crops must shorten each ribbon as it is traced. Otherwise
        // VinylEngine.add() rejects the ENTIRE long polygon at the SVG gate.
        if(!LineCropBoundary.accepts(s.circleCrop,x,y,s.widthMm,r.heightMm,
                                    s.marginMm,widthAllowance))return false;
        if(s.snips==null||s.snips.isEmpty())return true;
        if(s.snips.clearanceWithinMm(x,y,s.widthMm,r.heightMm,widthAllowance+.1f)<s.minimumMm*.5f+.1f)return false;
        if(!run.isEmpty()){
            float[] p=run.get(run.size()-1);
            if(!s.snips.safeSegment(p[0],p[1],x,y,s.widthMm,r.heightMm))return false;
        }
        return true;
    }
    private static boolean fullPhotoSample(VinylEngine.Result r,float x,float y,float allowance){
        VinylEngine.Settings s=r.settings;
        return valid(r,x,y,s.marginMm+allowance)&&
            LineCropBoundary.accepts(s.circleCrop,x,y,s.widthMm,r.heightMm,
                s.marginMm,allowance);
    }
    private static float tri(double radians){
        double v=radians/(Math.PI*2);v-=Math.floor(v);
        return (float)(1-4*Math.abs(v-.5));
    }
    private static void parallel(VinylEngine.Result r,int[] src,int w,int h,int li,int style,
                                 boolean secondary,boolean detail){
        VinylEngine.Settings s=r.settings;VinylEngine.Layer l=s.layers.get(li);
        float spacing=Math.max(.8f,l.spacing),extent=(float)Math.hypot(s.widthMm,r.heightMm)*.57f;
        float tStep=Math.min(s.lineSampleMm,Math.max(.18f,spacing*.22f));
        if((2*extent/spacing)*(2*extent/tStep)>600000)throw new IllegalArgumentException("Line screen has too many samples; increase spacing");
        boolean wave=style==VinylEngine.WAVE, zig=style==VinylEngine.ZIGZAG;
        boolean contour=style==VinylEngine.LINE||style==VinylEngine.CONTOUR||style==VinylEngine.HATCH;
        // Crosshatch on opaque vinyl uses a single cutter-safe family per color.
        // Rotate those families as a group to make the Cross Angle setting real;
        // different physical colors supply the crossing directions.
        double radians=Math.toRadians(l.angle+(style==VinylEngine.CROSSHATCH?s.crossAngleDeg:0f)
                +(secondary?s.crossAngleDeg:0f));
        float ca=(float)Math.cos(radians),sa=(float)Math.sin(radians),nx=-sa,ny=ca;
        float frequency=2f*(float)Math.PI*Math.max(.1f,s.waveCyclesPerInch)/VinylEngine.MM_PER_INCH;
        float displacement=(wave||zig?spacing*Math.min(.22f,s.waveAmplitude):contour?spacing*.16f*s.contourStrength:0);
        float maxHalf=Math.min(spacing*.49f*l.lineWidthScale*s.lineMaxFraction,
                Math.max(0,(spacing-s.bridgeMm-2*displacement)*.46f));
        if(s.detailBoost>0 && !secondary && (style==VinylEngine.STRAIGHT || style==VinylEngine.FADE))
            maxHalf=Math.min(maxHalf,Math.max(0,spacing*.5f-s.bridgeMm-spacing*.09f*s.detailBoost));
        if(detail)maxHalf=Math.min(maxHalf,spacing*.09f*s.detailBoost);
        if(secondary)maxHalf*=.40f;
        if(2*maxHalf<s.minimumMm)return;
        float start=-extent+(detail?spacing*.5f:0);
        for(float v=start;v<=extent;v+=spacing){CancellationCheckpoint.check();
            ArrayList<float[]> run=new ArrayList<>();float smooth=0;
            // A user Snip must not disable sustained portrait-highlight lanes.
            // The selection remains a hard boundary at each carrier sample.
            boolean visibility=s.mode==VinylEngine.CMYK && s.lineHighlightRetention>.0001f &&
                LineArtEngine.supportsMicroContinuity(style) && !secondary && !detail &&
                !s.lineInvert;
            ArrayList<LinePresenceTracker.Sample> lane=visibility?new ArrayList<>():null;
            LinePresenceTracker.Metrics metrics=r.lineMetrics.get(li);
            int laneNumber=Math.round(v/spacing);
            int cancelTick5=0;
            for(float t=-extent;t<=extent+tStep*.5f;t+=tStep){
                if((++cancelTick5&63)==0)CancellationCheckpoint.check();
                float x=s.widthMm*.5f+t*ca-v*sa,y=r.heightMm*.5f+t*sa+v*ca;
                float bend=0;
                if(wave)bend=displacement*(float)Math.sin(t*frequency+v/spacing*.24f);
                else if(zig)bend=displacement*tri(t*frequency+v/spacing*.18f);
                else if(contour){
                    float probe=Math.max(.22f,Math.min(spacing*.52f,1.7f));
                    float d=lum(r,src,w,h,x-nx*probe,y-ny*probe)-lum(r,src,w,h,x+nx*probe,y+ny*probe);
                    float target=d*displacement*1.5f;
                    float response=.08f+.22f*(1f-limit(s.lineSmoothing,0,1));
                    smooth=limit(smooth+(target-smooth)*response,-displacement,displacement);
                    bend=smooth;
                }
                x+=nx*bend;y+=ny*bend;
                boolean allowed=fullPhotoSample(r,x,y,maxHalf+displacement);
                if(!allowed){
                    if(visibility)lane.add(new LinePresenceTracker.Sample(x,y,0f,0f,false,false));
                    flush(r,li,run);run.clear();continue;
                }
                float weight=tone(r,src,w,h,li,x,y);
                if(s.lineInvert)weight=1-weight;
                float sharedEdge=-1f;
                if(supportsPortraitMidtones(style)
                        && !secondary && !detail && s.portraitMidtones>0){
                    // Lift only existing nonzero channel information; a blank pixel
                    // remains blank. This retains highlights but prevents a harsh
                    // cut-width threshold from erasing most of a face.
                    float mid=(float)Math.sqrt(limit(weight,0f,1f));
                    weight+=s.portraitMidtones*(mid-weight);
                    // Reinforce edges near eyes, lips and hair, scaled by tonal content.
                    sharedEdge=edge(r,src,w,h,x,y,Math.max(.22f,spacing*.30f));
                    weight=limit(weight+s.portraitMidtones*.16f*sharedEdge*(1-weight),0f,1f);
                }
                float shapeEdge=0f;
                if(s.shapeDefinition>.001f && !secondary && !detail && supportsShapeDefinition(style) && weight>.04f){
                    // Existing channel information only: paper-white pixels never gain cuts.
                    if(sharedEdge<0)sharedEdge=edge(r,src,w,h,x,y,Math.max(.22f,spacing*.30f));
                    shapeEdge=sharedEdge;
                    // Black carries structure; chromatic layers get a lighter touch.
                    float gain=(l.cmykChannel==3||s.mode!=VinylEngine.CMYK)?1f:.55f;
                    weight=limit(weight+.55f*s.shapeDefinition*gain*shapeEdge*(1-weight),0f,1f);
                }
                if(s.detailBoost>.001f && !secondary && !detail &&
                        (style==VinylEngine.LINE || style==VinylEngine.CONTOUR || style==VinylEngine.WAVE)){
                    float localEdge=edge(r,src,w,h,x,y,Math.max(.25f,spacing*.28f));
                    weight=limit(weight+.20f*s.detailBoost*localEdge*(1-weight),0f,1f);
                }
                if(secondary)weight=limit((weight-s.hatchThreshold)/(1-s.hatchThreshold),0,1);
                if(!secondary && (style==VinylEngine.HATCH || style==VinylEngine.CROSSHATCH)){
                    // The engraving/shadow slider must change real cut widths.
                    // A secondary intersecting hatch would double-cut the same
                    // vinyl sheet, so favor darker portions of this safe family.
                    float threshold=.30f*s.hatchThreshold;
                    weight=limit((weight-threshold)/(1f-threshold),0f,1f);
                }
                if(detail){
                    // Extra half-spacing strokes appear near fine image structure, not uniformly.
                    float e=edge(r,src,w,h,x,y,Math.max(.2f,spacing*.25f));
                    float emphasis=limit((e-(.06f+(.15f*(1-s.detailBoost))))/.28f,0,1);
                    weight*=emphasis;
                }
                if(style==VinylEngine.BROKEN){
                    if(weight<.32f){flush(r,li,run);run.clear();continue;}
                    float period=Math.max(spacing*2.5f,3.0f);
                    float phase=(t+v*.21f)/period;phase-=Math.floor(phase);
                    if(weight<.84f&&phase>(.35f+.50f*weight)){
                        flush(r,li,run);run.clear();continue;
                    }
                }
                if(style==VinylEngine.FADE)weight=limit((weight-.08f)/.92f,0,1);
                float half=halfWidth(s,l,spacing,weight,displacement,secondary,detail,(style==VinylEngine.STRAIGHT || style==VinylEngine.FADE));
                if(shapeEdge*s.shapeDefinition>.18f && weight>.12f){
                    // Keep strong feature edges as connected cuttable ribbons instead of dropping to dashes.
                    float floorHalf=s.minimumMm*.5f+.02f;
                    if(half<floorHalf&&floorHalf<=maxHalf)half=floorHalf;
                }
                if(visibility){
                    int sampled=VinylEngine.at(src,w,h,x,y,s.widthMm,r.heightMm);
                    boolean subject=PortraitBackgroundGuard.warmSubject(sampled,x,y,s.widthMm,r.heightMm)||
                        PortraitBackgroundGuard.grayPortraitSubject(sampled,x,y,s.widthMm,r.heightMm);
                    lane.add(new LinePresenceTracker.Sample(x,y,half,weight,true,subject));
                }else addSample(r,li,run,x,y,half);
            }
            if(visibility){
                for(ArrayList<float[]> segment:LinePresenceTracker.extract(lane,s.minimumMm,tStep,
                    s.linePresenceRecovery,laneNumber,l.cmykChannel,metrics,s.lineHighlightRetention))
                    flush(r,li,segment);
            }else flush(r,li,run);
        }
    }
    private static void beads(VinylEngine.Result r,int[] src,int w,int h,int li){
        VinylEngine.Settings s=r.settings;VinylEngine.Layer l=s.layers.get(li);
        float spacing=Math.max(.8f,l.spacing),extent=(float)Math.hypot(s.widthMm,r.heightMm)*.56f;
        float beadGap=spacing*.72f;
        // Bead Size quick slider must change the actual circle radius. The
        // maximum remains within each bead's isolated cut corridor.
        float beadScale=.45f+.55f*limit(s.lineMaxFraction/.95f,0f,1f);
        float limitRadius=Math.max(0,Math.min(spacing*.30f,beadGap*.38f)*beadScale);
        if(limitRadius*2<s.minimumMm)return;
        double a=Math.toRadians(l.angle);float ca=(float)Math.cos(a),sa=(float)Math.sin(a);
        if((2*extent/spacing)*(2*extent/beadGap)>550000)throw new IllegalArgumentException("Beaded lines too dense; increase spacing");
        int cancelTick6=0;
        for(float v=-extent;v<=extent;v+=spacing)for(float t=-extent;t<=extent;t+=beadGap){
            if((++cancelTick6&63)==0)CancellationCheckpoint.check();
            float x=s.widthMm*.5f+t*ca-v*sa,y=r.heightMm*.5f+t*sa+v*ca;
            if(!valid(r,x,y,s.marginMm+limitRadius))continue;
            float weight=tone(r,src,w,h,li,x,y);
            if(s.lineInvert)weight=1-weight;
            float rad=Math.min(limitRadius,limitRadius*l.lineWidthScale)*(float)Math.pow(weight,gamma(s,l));
            if(2*rad<s.minimumMm)continue;
            VinylEngine.add(r,li,VinylEngine.Shape.circle(x,y,rad));
        }
    }
    private static void concentric(VinylEngine.Result r,int[] src,int w,int h,int li){
        VinylEngine.Settings s=r.settings;VinylEngine.Layer l=s.layers.get(li);
        float spacing=Math.max(.8f,l.spacing),cx=s.radialFocusX*s.widthMm,cy=s.radialFocusY*r.heightMm;
        float extent=(float)Math.max(Math.max(Math.hypot(cx,cy),Math.hypot(cx-s.widthMm,cy)),
                                  Math.max(Math.hypot(cx,cy-r.heightMm),Math.hypot(cx-s.widthMm,cy-r.heightMm)));
        float halfMax=Math.min(spacing*s.lineMaxFraction*.5f*l.lineWidthScale,(spacing-s.bridgeMm)*.46f);
        if(2*halfMax<s.minimumMm)return;
        // Rings have no visual orientation: angle alone cannot distinguish them.
        // Stagger radial phase by CMYK identity to prevent identical concentric cuts.
        float phase=s.mode==VinylEngine.CMYK&&l.cmykChannel>=0
                ? spacing*(l.cmykChannel*.20f):0f;
        for(float radius=Math.max(spacing*.9f,s.minimumMm)+phase;radius<=extent+spacing;radius+=spacing){CancellationCheckpoint.check();
            int steps=Math.max(24,(int)Math.ceil(2*Math.PI*radius/Math.min(s.lineSampleMm,.5f)));
            steps=Math.min(steps,5000);
            ArrayList<float[]> run=new ArrayList<>();
            for(int k=0;k<=steps;k++){if(((k)&63)==0)CancellationCheckpoint.check();
                double a=2*Math.PI*k/steps;
                float x=cx+radius*(float)Math.cos(a),y=cy+radius*(float)Math.sin(a);
                if(!safeSample(r,run,x,y,halfMax)) {flush(r,li,run);run.clear();continue;}
                float weight=tone(r,src,w,h,li,x,y);
                if(s.lineInvert)weight=1-weight;
                float half=halfWidth(s,l,spacing,weight,0,false,false,false);
                addSample(r,li,run,x,y,half);
            }
            flush(r,li,run);
        }
    }
    private static void rays(VinylEngine.Result r,int[] src,int w,int h,int li){
        VinylEngine.Settings s=r.settings;VinylEngine.Layer l=s.layers.get(li);
        float spacing=Math.max(.8f,l.spacing),cx=s.radialFocusX*s.widthMm,cy=s.radialFocusY*r.heightMm;
        float outer=(float)Math.hypot(s.widthMm,r.heightMm);
        int rays=Math.max(12,Math.min(130,(int)(2*Math.PI*(outer*.43f)/spacing)));
        float inner=Math.max(2.5f*spacing,rays*spacing/(2f*(float)Math.PI));
        float step=Math.min(s.lineSampleMm,Math.max(.18f,spacing*.25f));
        float maxHalf=Math.min(spacing*s.lineMaxFraction*.5f*l.lineWidthScale,(spacing-s.bridgeMm)*.46f);
        if(2*maxHalf<s.minimumMm)return;
        for(int i=0;i<rays;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            double a=2*Math.PI*i/rays+Math.toRadians(l.angle);
            float ca=(float)Math.cos(a),sa=(float)Math.sin(a);
            ArrayList<float[]> run=new ArrayList<>();
            int cancelTick8=0;
            for(float t=inner;t<=outer;t+=step){
                if((++cancelTick8&63)==0)CancellationCheckpoint.check();
                float x=cx+t*ca,y=cy+t*sa;
                if(!safeSample(r,run,x,y,maxHalf)){flush(r,li,run);run.clear();continue;}
                float weight=tone(r,src,w,h,li,x,y);
                if(s.lineInvert)weight=1-weight;
                float half=halfWidth(s,l,spacing,weight,0,false,false,false);
                addSample(r,li,run,x,y,half);
            }
            flush(r,li,run);
        }
    }
}

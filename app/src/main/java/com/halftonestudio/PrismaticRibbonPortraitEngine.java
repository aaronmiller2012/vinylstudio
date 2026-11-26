package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;

/** Photo-guided, continuous diagonal ribbons on independent opaque vinyl sheets.
 * The two warped diagonal families cross without subtracting lower material.
 * Physical layer order alone determines the visible color at each intersection.
 */
public final class PrismaticRibbonPortraitEngine {
    private PrismaticRibbonPortraitEngine() {}
    public enum Mode { CONTOUR_FLOW, TWILL, SILK, BRAIDED_FLOW, CHEVRON }
    public static String label(Mode mode){
        switch(mode){
            case TWILL:return "Prismatic Twill";
            case SILK:return "Prismatic Silk";
            case BRAIDED_FLOW:return "Prismatic Braided Flow";
            case CHEVRON:return "Prismatic Chevron";
            default:return "Prismatic Contour Flow";
        }
    }
    private static final float INV_SQRT2 = 0.7071067811865476f;
    private static final float TAU = (float)(2 * Math.PI);

    /** Above this artwork width the lane count grows with sqrt(width) instead of staying fixed. */
    static final float PITCH_REFERENCE_MM=152.4f;
    public static final class Options {
        public int detail=68, irregularity=43, roundness=52, featureEmphasis=76, toneBalance=65;
        public Mode mode=Mode.CONTOUR_FLOW;
        public float widthMm=152.4f, minCutMm=.254f, gapMm=.32f;
        public int layers=6;
        public int[] palette=null;
        public int paperArgb=0xfffaf9f5;
        public boolean[] allowed=null;
        public long seed=20261104L;
        public boolean draft=false;
        public Options copy() {
            Options p=new Options(); p.mode=mode; p.detail=detail;p.irregularity=irregularity;
            p.roundness=roundness;p.featureEmphasis=featureEmphasis;p.toneBalance=toneBalance;
            p.widthMm=widthMm;p.minCutMm=minCutMm;p.gapMm=gapMm;p.layers=layers;
            p.palette=palette==null?null:palette.clone();p.paperArgb=paperArgb;p.draft=draft;
            p.allowed=allowed==null?null:allowed.clone();p.seed=seed;return p;
        }
        void validate(){
            if(mode==null||detail<0||detail>100||irregularity<0||irregularity>100||
               roundness<0||roundness>100||featureEmphasis<0||featureEmphasis>100||
               toneBalance<0||toneBalance>100||
               layers<2||layers>8||!Float.isFinite(widthMm)||widthMm<20||widthMm>2000||
               !Float.isFinite(minCutMm)||minCutMm<.10f||minCutMm>4||
               !Float.isFinite(gapMm)||gapMm<.10f||gapMm>5||
               (palette!=null&&palette.length!=layers))
                throw new IllegalArgumentException("Invalid prismatic portrait settings");
        }
    }

    private static final class Field {
        final StyleKit.Source photo;
        final int w,h,n;
        final float pitch,widthMm,heightMm;
        final boolean[] valid;
        Field(StyleKit.Source photo, Options o){
            this.photo=photo;
            widthMm=photo.widthMm;heightMm=photo.heightMm;
            // Resolution is bounded on the phone. At very large physical sizes we
            // intentionally raise the effective feature floor instead of promising
            // physically unsafe subpixel islands.
            float sidePx=Math.min(o.draft?460f:1200f,Math.max(o.draft?280f:360f,
                Math.max(widthMm,heightMm)/Math.max(.15f,o.minCutMm*.38f)));
            float unit=Math.max(widthMm,heightMm)/sidePx;
            w=Math.max(24,Math.round(widthMm/unit));h=Math.max(24,Math.round(heightMm/unit));
            pitch=widthMm/w;n=w*h;valid=new boolean[n];
            for(int y=0;y<h;y++){if(((y)&63)==0)CancellationCheckpoint.check();
                if((y&31)==0)StyleKit.checkCancelled();
                for(int x=0;x<w;x++)valid[y*w+x]=photo.validAt((x+.5f)*pitch,(y+.5f)*pitch);
            }
        }
        int index(float x,float y){
            int xx=StyleKit.clampI((int)(x/pitch),0,w-1),
                yy=StyleKit.clampI((int)(y/pitch),0,h-1);
            return yy*w+xx;
        }
        boolean isValid(float x,float y){return x>=0&&y>=0&&x<widthMm&&y<heightMm&&valid[index(x,y)];}
    }

    public static StyleKit.Result generate(int[] pixels,int width,int height,Options original){
        if(original==null)throw new IllegalArgumentException("Options required");
        Options o=original.copy();o.validate();
        StyleKit.Source source=new StyleKit.Source(pixels,width,height,o.allowed,o.widthMm)
            .autoLevels(.02f,.98f);
        if(!source.anyValid())throw new IllegalArgumentException("No pixels inside the Snip");
        final Field f=new Field(source,o);
        int[] palette=o.palette==null?derivePalette(source,o.layers,o.seed):o.palette.clone();
        if(palette.length!=o.layers)throw new IllegalStateException("Palette length mismatch");


        // Physical pitch is shared by both families, but their centerlines bend
        // independently. No cell clipping, parent masks, or knockout carving.
        final int across=18+Math.round(o.detail*.42f);
        final float effectiveGap=Math.max(o.gapMm+f.pitch,2f*f.pitch);
        final float minWidth=Math.max(o.minCutMm+2*f.pitch,3.5f*f.pitch);
        final float[][] lab=StyleKit.labOf(palette);
        // Build the actual centerline geometry first, then derive the width bounds
        // from it. Lanes are offset along the side axis, so a centerline slope s
        // shrinks both the perpendicular ribbon width and the perpendicular gap by
        // 1/sqrt(1+s^2); adjacent lanes that approach by D cells lose D of their
        // spacing. Silk and Chevron have no lane offsets (D=0) and keep the full
        // tonal range; Braided Flow pays for its paired approach. If no width can
        // satisfy both the minimum cut and the weed gap, the pitch grows instead.
        float detailCell=(float)Math.sqrt(2)*f.widthMm/across;
        // M5 size-aware pitch: with a fixed lane count an 11 in portrait got the same number
        // of ribbons as a 6 in one, so likeness stopped improving with size. Above 6 in the
        // pitch grows with sqrt(width) (8 photos, Chevron/Contour Flow at 11 in: tone
        // .85 -> .90, SSIM .62 -> .76, ~1.3x render time). Holding the 6 in pitch measured
        // slightly better likeness but ~4x the render time and memory, so it was rejected.
        if(f.widthMm>PITCH_REFERENCE_MM)
            detailCell=(float)(Math.sqrt(2)*Math.sqrt(f.widthMm*PITCH_REFERENCE_MM)/across);
        float cell=Math.max(Math.max(minWidth*2.5f,effectiveGap*2.8f),detailCell);
        BandField first=null,second=null;float maxFraction=0,minFraction=1;
        for(int attempt=0;attempt<8;attempt++){
            first=new BandField(f,o,cell,false,lab);
            second=new BandField(f,o,cell,true,lab);
            // Width limits are local: a lane may only be as wide as its own
            // neighbourhood allows (approach and slope at that sample), so one
            // tight braid crossing no longer thins every ribbon in the portrait.
            first.limits(effectiveGap,minWidth);second.limits(effectiveGap,minWidth);
            minFraction=Math.max(first.minFraction,second.minFraction);
            // Colour fitting uses the width most lanes can take; the few tighter
            // samples are clamped continuously to their own limit after fitting.
            maxFraction=Math.min(first.typicalMax,second.typicalMax);
            if(Math.min(first.feasibleShare(minFraction*1.15f),second.feasibleShare(minFraction*1.15f))>=.95f)break;
            cell*=1.15f;
        }
        if(maxFraction<minFraction)throw new IllegalArgumentException("No feasible ribbon pitch; enlarge the artwork or lower the minimum cut width");
        final java.util.List<BandPair> candidates=bandPairs(lab,palette,minFraction,maxFraction);
        first.twillTop=second.twillTop=maxFraction;
        first.fit(candidates,lab);second.fit(candidates,lab);
        for(int pass=0;pass<2;pass++){first.refine(second,candidates,lab);second.refine(first,candidates,lab);}
        first.clampWidths(minFraction);second.clampWidths(minFraction);
        // Final geometry comes from the continuous ribbon field; draft and
        // backend-free host fixtures retain the original raster route.
        VectorBooleanEngine.Session vector=o.draft?null:VectorBooleanEngine.begin(o.layers,o.minCutMm,o.gapMm);
        if(vector!=null)return vectorResult(f,o,palette,first,second,vector);
        final boolean[][] masks=new boolean[o.layers][f.n];
        System.arraycopy(f.valid,0,masks[0],0,f.n);
        // Each diagonal coordinate repeats on the raster: precompute its warp.
        float[] warpA=new float[f.w+f.h+1],warpB=new float[f.w+f.h+1];
        float diagonalStep=f.pitch*INV_SQRT2/cell;
        for(int i=0;i<warpA.length;i++){
            warpA[i]=first.wave(i*diagonalStep);
            warpB[i]=second.wave((i-f.w)*diagonalStep);
        }
        int overlapPixels=0;
        for(int y=0;y<f.h;y++){
            if((y&15)==0)StyleKit.checkCancelled();
            for(int x=0;x<f.w;x++){
                int at=y*f.w+x;if(!f.valid[at])continue;
                float u=(x+y+1)*diagonalStep,v=(y-x)*diagonalStep;
                int a=first.paint(masks,at,u,v,warpA[x+y+1]),
                    b=second.paint(masks,at,v,u,warpB[y-x+f.w]);
                if(a!=0&&b!=0&&a!=b)overlapPixels++;
            }
        }
        StyleKit.Result result=new StyleKit.Result(f.widthMm,f.heightMm);
        result.paperArgb=o.paperArgb;
        float minArea=Math.max(2.5f*f.pitch*f.pitch,o.minCutMm*o.minCutMm*1.5f);
        for(int slot=0;slot<o.layers;slot++){
            StyleKit.checkCancelled();
            boolean[] mask=masks[slot];
            if(slot!=0){
                int radius=Math.max(1,(int)Math.ceil(o.minCutMm*.40f/f.pitch));
                mask=open(mask,f.w,f.h,Math.min(7,radius),f.valid);
                mask=removeSmall(mask,f.w,f.h,
                    Math.max(3,(int)Math.ceil(minArea/(f.pitch*f.pitch))));
                // Fill only enclosed, tiny same-sheet pinholes. Deliberate open
                // ribbon spacing and all Snip holes remain untouched.
                fillPinholes(mask,f,Math.max(3,(int)Math.ceil(Math.max(
                    effectiveGap*effectiveGap,cell*cell*.035f)/(f.pitch*f.pitch))));
            }
            StyleKit.Layer layer=vectorize(mask,f,"prismatic-"+o.mode.name().toLowerCase(java.util.Locale.ROOT)+"-"+(slot+1),palette[slot],minArea,slot==0);
            // Keep the physical slot index stable even when a color has zero
            // cut pieces. Auto swatches, layer labels, and export lookups must
            // never shift to the previous plate when an accent is absent.
            result.layers.add(layer);
        }
        if(result.layers.isEmpty())throw new IllegalStateException("No vector geometry generated");
        int pieces=0;
        for(StyleKit.Layer layer:result.layers){CancellationCheckpoint.check();
            pieces+=layer.loops.size();
            if(layer.loops.size()>8000)throw new IllegalStateException("Too many tiny cut pieces");
        }
        result.summary=label(o.mode)+" · interwoven ribbons, "+
            overlapPixels+" cross-color overlap samples, "+result.layers.size()+" physical layers, "+
            pieces+" cut contours (minimum feature floor "+
            Math.round(Math.max(o.minCutMm,2*f.pitch)*100f)/100f+" mm)";
        return result;
    }

    private static StyleKit.Result vectorResult(Field f,Options o,int[] palette,
            BandField first,BandField second,VectorBooleanEngine.Session vector){
        StyleKit.Result result=new StyleKit.Result(f.widthMm,f.heightMm);
        result.paperArgb=o.paperArgb;
        StyleKit.Layer base=vectorize(f.valid,f,"prismatic-"+o.mode.name().toLowerCase(java.util.Locale.ROOT)+"-1",palette[0],0,true);
        for(int i=0;i<base.loops.size();i++)base.loops.set(i,
            StyleKit.clipToArtboardOrOriginal(base.loops.get(i),f.widthMm,f.heightMm));
        vector.setClip(base.loops);
        base.loops.clear();base.loops.addAll(vector.foundation(f.widthMm,f.heightMm));
        base.vectorGeometryLocked=true;result.layers.add(base);
        first.emit(vector);second.emit(vector);
        int pieces=base.loops.size();
        float minArea=Math.max(2.5f*f.pitch*f.pitch,o.minCutMm*o.minCutMm*1.5f);
        float effectiveGap=Math.max(o.gapMm+f.pitch,2*f.pitch);
        // Fill enclosed weave holes up to 6% of a cell: in shadows the crossing ribbons
        // leave small diamonds that read as a white lattice and are tedious to weed.
        float holeArea=Math.max(effectiveGap*effectiveGap,first.cell*first.cell*.06f);
        // Post-Boolean cut conditioning in exact vector space: close same-sheet weed
        // gaps narrower than the gap (two ribbon families of one colour can end a
        // hair apart) and open vinyl necks narrower than the minimum cut (Snip-edge
        // slivers). Small margins absorb arc flattening. The changed area is reported.
        float closeR=o.gapMm*.5f+.03f,openR=o.minCutMm*.5f+.03f;
        double before=0,after=0;
        double[][] area=new double[o.layers][2];
        ArrayList<ArrayList<float[]>> sheets=vector.finishConditionedAll(f.widthMm,f.heightMm,minArea,holeArea,closeR,openR,area);
        for(int slot=1;slot<o.layers;slot++){
            CancellationCheckpoint.check();
            StyleKit.Layer layer=new StyleKit.Layer("prismatic-"+o.mode.name().toLowerCase(java.util.Locale.ROOT)+"-"+(slot+1),palette[slot]);
            layer.loops.addAll(sheets.get(slot));
            before+=area[slot][0];after+=area[slot][1];
            layer.vectorGeometryLocked=true;result.layers.add(layer);pieces+=layer.loops.size();
        }
        result.summary=label(o.mode)+" · continuous interwoven ribbons · independent vector Boolean overlays · "+
            result.layers.size()+" physical layers, "+pieces+" cut contours (minimum feature floor "+
            Math.round(Math.max(o.minCutMm,2*f.pitch)*100f)/100f+" mm)"+
            (Double.isFinite(before)&&before>0?String.format(java.util.Locale.US,
                " · cut conditioning %+.1f%% overlay area (gaps < %.2f mm merged, necks < %.2f mm removed)",
                100*(after-before)/before,2*closeR,2*openR):"");
        return result;
    }

    /** Each candidate predicts spatial average of three REAL opaque colors.
     * At a crossing the higher physical index wins. This is an area-average
     * perceptual planning model, not translucent pigment mixing in the preview.
     */
    private static final class BandPair {
        final int a,b;final float wa,wb,l,x,y,penalty,ca,cb;
        BandPair(int a,int b,float wa,float wb,float[][] lab,float[][] rgb){
            this.a=a;this.b=b;this.wa=wa;this.wb=wb;
            ca=a>=b?wa:wa*(1-wb);cb=b>a?wb:wb*(1-wa);
            float[] visible=RibbonColorMath.composite(rgb,a,b,wa,wb);
            l=visible[0];x=visible[1];y=visible[2];
            penalty=34f*(wa-wb)*(wa-wb)+(a==b&&a!=0&&lab.length>2?22f:0f);
        }
    }
    private static java.util.List<BandPair> bandPairs(float[][] lab,int[] palette,float min,float max){
        ArrayList<BandPair> out=new ArrayList<>();
        float[][] rgb=new float[palette.length][];for(int i=0;i<palette.length;i++)rgb[i]=RibbonColorMath.rgb(palette[i]);
        // Diagonal's four levels. (Six finer levels were measured: the area model then
        // prefers middle widths that render lighter, and fidelity dropped.)
        float[] widths={Math.min(max,min),Math.max(min,max*.42f),
            Math.max(min,max*.70f),max};
        for(int a=0;a<lab.length;a++)for(int b=0;b<lab.length;b++)
            for(int i=0;i<(a==0?1:widths.length);i++)for(int j=0;j<(b==0?1:widths.length);j++)
                out.add(new BandPair(a,b,a==0?0:widths[i],b==0?0:widths[j],lab,rgb));
        return out;
    }
    private static final class BandField {
        final Field f;final Options o;final float cell;final boolean cross;
        final int startLane,startAlong,lanes,steps;
        final byte[] slot;final float[] width;
        final float[][] targets;final float[] sampleX,sampleY;final float adapt,chromaWeight,feature;
        final float amplitude,frequency,phase,transition;
        final float[] waveByRow, offsets;
        final PrismaticRibbonGeometry.Pattern pattern;
        BandField(Field f,Options o,float cell,boolean cross,float[][] lab){
            this.f=f;this.o=o;this.cell=cell;this.cross=cross;
            pattern=PrismaticRibbonGeometry.Pattern.valueOf(o.mode.name());
            float[] along=diagonalBounds(f.widthMm,f.heightMm,!cross);
            float[] side=diagonalBounds(f.widthMm,f.heightMm,cross);
            startAlong=(int)Math.floor(along[0]/cell)-3;
            startLane=(int)Math.floor(side[0]/cell)-3;
            steps=(int)Math.ceil(along[1]/cell)-startAlong+4;
            lanes=(int)Math.ceil(side[1]/cell)-startLane+4;
            slot=new byte[steps*lanes];width=new float[slot.length];waveByRow=new float[steps];
            targets=new float[slot.length][];sampleX=new float[slot.length];sampleY=new float[slot.length];
            amplitude=.10f+.25f*o.irregularity/100f;
            frequency=TAU/(3.4f+2.0f*o.roundness/100f);
            phase=TAU*uniform(hash(cross?1:0,7,o.seed));
            transition=.18f+.11f*o.roundness/100f;
            for(int k=0;k<steps;k++)waveByRow[k]=wave(startAlong+k);
            offsets=new float[steps*lanes];
            buildOffsets();
            feature=o.featureEmphasis/100f;
            float lightMin=100f,lightMax=0f;
            for(float[] c:lab){lightMin=Math.min(lightMin,c[0]);lightMax=Math.max(lightMax,c[0]);}
            float range=lightMax-lightMin;
            adapt=StyleKit.clampF((75f-range)/35f,0f,1f);
            chromaWeight=.72f*(1f-.35f*adapt);
        }
        float[] localMax;float minFraction,maxFraction,typicalMax;
        /** Per-sample width bounds. Lanes are offset along the side axis, so at slope s
         * the perpendicular width and gap shrink by 1/sqrt(1+s^2); a lane whose
         * neighbour approaches by D cells loses D of its spacing. */
        void limits(float effectiveGap,float minWidth){
            localMax=new float[lanes*steps];
            float[] waveSlope=new float[steps];
            for(int k=0;k<steps;k++){
                float best=0;
                for(int i=-4;i<=4;i++){float a=startAlong+k+i/8f;best=Math.max(best,Math.abs(wave(a+1/16f)-wave(a-1/16f))*8f);}
                waveSlope[k]=best;
            }
            float worstStretch=1;maxFraction=0;
            for(int lane=0;lane<lanes;lane++){
                if((lane&7)==0)CancellationCheckpoint.check();
                int row=lane*steps;
                for(int k=0;k<steps;k++){
                    float o0=offsets[row+k];
                    float approach=0;
                    if(lane+1<lanes)approach=Math.max(approach,o0-offsets[row+steps+k]);
                    if(lane>0)approach=Math.max(approach,offsets[row-steps+k]-o0);
                    float offSlope=0;
                    if(k>0)offSlope=Math.abs(o0-offsets[row+k-1]);
                    if(k+1<steps)offSlope=Math.max(offSlope,Math.abs(offsets[row+k+1]-o0));
                    float slope=waveSlope[k]+offSlope,stretch=(float)Math.sqrt(1+slope*slope);
                    worstStretch=Math.max(worstStretch,stretch);
                    float m=Math.min(.85f,1f-approach-1.16f*effectiveGap*stretch/cell);
                    localMax[row+k]=m;maxFraction=Math.max(maxFraction,m);
                }
            }
            minFraction=minWidth*worstStretch/cell;
            float[] sorted=localMax.clone();java.util.Arrays.sort(sorted);
            typicalMax=sorted.length==0?maxFraction:sorted[(int)(sorted.length*.35f)];
        }
        /** Narrow only the samples whose own neighbourhood is tighter than the fitted width. */
        void clampWidths(float floor){
            for(int i=0;i<width.length;i++)
                if(slot[i]!=0&&width[i]>localMax[i])width[i]=Math.max(floor,localMax[i]);
        }
        /** Share of samples whose local limit admits at least {@code width}. */
        float feasibleShare(float width){
            int ok=0;for(float m:localMax)if(m>=width)ok++;
            return localMax.length==0?1:ok/(float)localMax.length;
        }
        void fit(java.util.List<BandPair> candidates,float[][] lab){
            float lightMin=100f,lightMax=0f;
            for(float[] c:lab){lightMin=Math.min(lightMin,c[0]);lightMax=Math.max(lightMax,c[0]);}
            float range=lightMax-lightMin;
            float[] swatchCosts=new float[lab.length];
            for(int lane=0;lane<lanes;lane++){
                StyleKit.checkCancelled();
                int previous=0;
                for(int k=0;k<steps;k++){
                    if((k&15)==0)CancellationCheckpoint.check();
                    float a=startAlong+k,b=startLane+lane+waveByRow[k]+offsets[lane*steps+k];
                    float u=(cross?b:a)*cell,v=(cross?a:b)*cell;
                    float px=(u-v)*INV_SQRT2,py=(u+v)*INV_SQRT2;
                    // Adjacent valid samples extend color to a clipped Snip edge;
                    // source pixels outside the Snip never vote for a swatch.
                    float[] sample=sampleWithinCell(f,px,py,cell*1.4f);
                    if(sample==null){previous=0;continue;}
                    px=sample[0];py=sample[1];
                    float[] target=StyleKit.toLab(averageCellColor(f.photo,f,px,py,cell*(1.25f-.70f*feature)));
                    float[] local=StyleKit.toLab(f.photo.colorAt(px,py));
                    for(int ch=0;ch<3;ch++)target[ch]+=(local[ch]-target[ch])*.55f*feature;
                    // Fit the available vinyl lightness range for narrow palettes.
                    // Do not sample transparent pixels or modify the physical colors.
                    float normalized=f.photo.lum[f.photo.pixelIndex(px,py)];
                    float absolute=.9f*target[0]+.1f*normalized*100f;
                    target[0]=StyleKit.clampF(absolute*(1-adapt)+adapt*(lightMin+range*normalized)+
                        (o.toneBalance-50)*Math.max(.18f,.55f*(1-adapt)+.32f*adapt*range/100f),0,100);
                    // Limited tonal gamut: use the nearest available pigment's chroma
                    // as a soft target instead of chasing unattainable source hues.
                    int near=0;for(int j=1;j<lab.length;j++)if(Math.abs(lab[j][0]-target[0])<Math.abs(lab[near][0]-target[0]))near=j;
                    for(int c=1;c<3;c++)target[c]+=adapt*.72f*(lab[near][c]-target[c]);
                    RibbonColorMath.fillCosts(target,lab,swatchCosts);
                    BandPair best=null;float bestCost=Float.POSITIVE_INFINITY;
                    float cap=twillCap(lane,k,target[0]);
                    for(BandPair pair:candidates){
                        if((cross?pair.wb:pair.wa)>cap)continue;
                        int own=cross?pair.b:pair.a;
                        float cost=pairCost(pair,target,swatchCosts)+patternCost(pair,lane,k);
                        if(previous!=own)cost+=(previous==0||own==0?85f:95f)*(1.2f-.35f*feature)*(1f-.62f*adapt);
                        if(cost<bestCost){best=pair;bestCost=cost;}
                    }
                    int at=lane*steps+k;
                    targets[at]=target;sampleX[at]=px;sampleY[at]=py;
                    slot[at]=(byte)(cross?best.b:best.a);width[at]=cross?best.wb:best.wa;
                    previous=slot[at];
                }
            }
        }
        /** Twill: on the two "under" steps of each four-step repeat (staggered one
         * step per lane, so the floats form a diagonal rib) a ribbon is capped at a
         * thin binding width and the crossing family must carry the tone. */
        private float twillCap(int lane,int k,float targetL){
            if(pattern!=PrismaticRibbonGeometry.Pattern.TWILL||o.irregularity==0)return Float.POSITIVE_INFINITY;
            boolean over=Math.floorMod(lane+startLane+(cross?-(k+startAlong):(k+startAlong)),4)<2;
            if(over)return Float.POSITIVE_INFINITY;
            // Strength 0..100 maps the binding cap from full width down to ~45%.
            float depth=.55f*o.irregularity/100f;
            // M5 image-aware binding: the texture reads in midtones; deep shadows need the
            // coverage, so the cap releases fully below L* 24. 8 photos x 6/8 in: tone
            // .808 -> .825, SSIM .502 -> .548, dE .0764 -> .0689 (14 better, 0 worse).
            depth*=1f-StyleKit.smoothstep(40f,24f,targetL);
            return twillTop*(1f-depth)+1e-6f;
        }
        /** 0 in midtones/highlights, 1 in deep shadow (photo luminance). */
        private float shadow(float x,float y){
            return StyleKit.smoothstep(.42f,.16f,f.photo.lum[f.photo.pixelIndex(x,y)]);
        }
        float twillTop=Float.POSITIVE_INFINITY;
        private float patternCost(BandPair p,int lane,int k){
            return PrismaticRibbonGeometry.prior(pattern,cross,lane+startLane,k+startAlong,p.wa,p.wb,o.irregularity/100f);
        }
        private float offset(int lane,float along){
            return PrismaticRibbonGeometry.interpolate(offsets,lane*steps,steps,along-startAlong);
        }
        private int closestLane(float along,float side,float warp){
            int guess=Math.round(side-warp)-startLane,best=-1;
            float distance=Float.POSITIVE_INFINITY;
            for(int lane=Math.max(0,guess-1);lane<=Math.min(lanes-1,guess+1);lane++){
                float d=Math.abs(side-warp-offset(lane,along)-(startLane+lane));
                if(d<distance){distance=d;best=lane;}
            }
            return best;
        }
        private void buildOffsets(){
            float strength=o.irregularity/100f;
            for(int lane=0;lane<lanes;lane++){
                CancellationCheckpoint.check();
                int row=lane*steps;float current=0;
                for(int k=0;k<steps;k++){
                    if((k&15)==0)CancellationCheckpoint.check();
                    float along=startAlong+k;
                    if(pattern!=PrismaticRibbonGeometry.Pattern.CONTOUR_FLOW){
                        float off=PrismaticRibbonGeometry.decorativeOffset(pattern,lane+startLane,along,strength,cross);
                        if(pattern==PrismaticRibbonGeometry.Pattern.BRAIDED_FLOW){
                            // M5 image-aware sway: paired lanes that approach each other must
                            // thin, which in deep shadow costs coverage, so the sway relaxes
                            // there (smoothly along k). 8 photos x 6/8 in: tone .808 -> .822,
                            // SSIM .503 -> .537, dE .0742 -> .0708.
                            float side=startLane+lane+waveByRow[k];
                            float u=(cross?side:along)*cell,v=(cross?along:side)*cell;
                            float keep=1f-.75f*shadow((u-v)*INV_SQRT2,(u+v)*INV_SQRT2);
                            current=k==0?keep:.6f*current+.4f*keep;
                            off*=current;
                        }
                        offsets[row+k]=off;
                        continue;
                    }
                    float side=startLane+lane+waveByRow[k];
                    float u=(cross?side:along)*cell,v=(cross?along:side)*cell;
                    float px=(u-v)*INV_SQRT2,py=(u+v)*INV_SQRT2;
                    float slope=localSlope(px,py);
                    // Bounded leaky integration: no accumulated drift or lane crossings.
                    current=PrismaticRibbonGeometry.clamp(.55f*current+.24f*slope*strength,
                        -PrismaticRibbonGeometry.CONTOUR_OFFSET,PrismaticRibbonGeometry.CONTOUR_OFFSET);
                    offsets[row+k]=current;
                }
                if(pattern==PrismaticRibbonGeometry.Pattern.CONTOUR_FLOW){
                    float next=0;
                    for(int k=steps-1;k>=0;k--){next=.5f*next+.5f*offsets[row+k];offsets[row+k]=next;}
                }
            }
        }
        private float localSlope(float x,float y){
            // A small smoothed structure tensor in physical coordinates. Only valid
            // photo samples vote; Snip boundaries are not treated as photo edges.
            float delta=Math.max(f.pitch,cell*.12f),radius=cell*.24f;
            float xx=0,xy=0,yy=0;
            for(int j=-1;j<=1;j++)for(int i=-1;i<=1;i++){
                float px=x+i*radius,py=y+j*radius;
                if(!f.isValid(px-delta,py)||!f.isValid(px+delta,py)||
                   !f.isValid(px,py-delta)||!f.isValid(px,py+delta))continue;
                float gx=lum(px+delta,py)-lum(px-delta,py),gy=lum(px,py+delta)-lum(px,py-delta);
                float weight=(i==0?2:1)*(j==0?2:1);
                xx+=weight*gx*gx;xy+=weight*gx*gy;yy+=weight*gy*gy;
            }
            return PrismaticRibbonGeometry.tangentSlope(xx,xy,yy,cross);
        }
        private float lum(float x,float y){return f.photo.lum[f.photo.pixelIndex(x,y)];}
        private float pairCost(BandPair p,float[] target,float[] swatchCosts){
            float dl=target[0]-p.l,da=target[1]-p.x,db=target[2]-p.y;
            float speckle=p.ca*swatchCosts[p.a]+p.cb*swatchCosts[p.b];
            return (2.45f+1.6f*adapt)*dl*dl+chromaWeight*(da*da+db*db)+(.16f+.30f*adapt)*speckle+p.penalty;
        }
        private int nearest(float x,float y){
            float u=(x+y)*INV_SQRT2/cell,v=(y-x)*INV_SQRT2/cell;
            float along=cross?v:u,side=cross?u:v;
            int k=Math.round(along)-startAlong,lane=closestLane(along,side,wave(along));
            return k<0||k>=steps||lane<0||lane>=lanes?-1:lane*steps+k;
        }
        void refine(BandField other,java.util.List<BandPair> candidates,float[][] lab){
            java.util.ArrayList<java.util.ArrayList<BandPair>> bySlot=new java.util.ArrayList<>();
            for(int i=0;i<lab.length;i++)bySlot.add(new java.util.ArrayList<>());
            for(BandPair p:candidates)bySlot.get(cross?p.a:p.b).add(p);
            float[] swatchCosts=new float[lab.length];
            for(int lane=0;lane<lanes;lane++){
                StyleKit.checkCancelled();
                for(int k=0;k<steps;k++){
                    int at=lane*steps+k;float[] target=targets[at];if(target==null)continue;
                    RibbonColorMath.fillCosts(target,lab,swatchCosts);
                    int j=other.nearest(sampleX[at],sampleY[at]),op=j<0?0:other.slot[j];
                    float ow=j<0?0:other.width[j];BandPair best=null;float bestCost=Float.POSITIVE_INFINITY;
                    int prev=k==0?0:slot[at-1],next=k==steps-1?0:slot[at+1];
                    float cap=twillCap(lane,k,target[0]);
                    for(BandPair p:bySlot.get(op)){
                        if(Math.abs((cross?p.wa:p.wb)-ow)>1e-5f)continue;
                        if((cross?p.wb:p.wa)>cap)continue;
                        int own=cross?p.b:p.a;
                        float cost=pairCost(p,target,swatchCosts)+patternCost(p,lane,k)+((own==prev?0:75)+(own==next?0:75))*(1-.35f*feature);
                        if(cost<bestCost){bestCost=cost;best=p;}
                    }
                    if(best!=null){slot[at]=(byte)(cross?best.b:best.a);width[at]=cross?best.wb:best.wa;}
                }
            }
        }
        float wave(float along){
            return PrismaticRibbonGeometry.wave(pattern,along,amplitude,frequency,phase,cross);
        }
        /** Construct one outline for each contiguous same-sheet run, avoiding
         * incremental unions of adjacent ribbon segments. */
        void emit(VectorBooleanEngine.Session vector){
            for(int lane=0;lane<lanes;lane++){
                CancellationCheckpoint.check();int row=lane*steps;
                for(int start=0;start<steps;){
                    int color=slot[row+start],end=start;
                    while(end+1<steps&&slot[row+end+1]==color)end++;
                    if(color!=0){
                        ArrayList<float[]> points=new ArrayList<>();
                        if(start>0){double reach=slot[row+start-1]==0?.65:.5+transition;
                            append(points,lane,start-1,1-reach,1,color,vector.tolerance);}
                        for(int k=start;k<end;k++)append(points,lane,k,0,1,color,vector.tolerance);
                        if(end+1<steps){double reach=slot[row+end+1]==0?.65:.5+transition;
                            append(points,lane,end,0,reach,color,vector.tolerance);}
                        int n=points.size(),j=0;float[] loop=new float[n*4];
                        for(int i=0;i<n;i++){float[] p=points.get(i);loop[j++]=p[0];loop[j++]=p[1];}
                        for(int i=n-1;i>=0;i--){float[] p=points.get(i);loop[j++]=p[2];loop[j++]=p[3];}
                        loop=VectorBooleanEngine.clean(loop);
                        if(loop.length>=6&&Math.abs(StyleKit.signedArea(loop))>1e-7)vector.add(color,loop);
                    }
                    start=end+1;
                }
            }
        }
        private float[] point(int lane,int k,double t,int color){
            int i=lane*steps+k,j=i+1,a=slot[i],b=slot[j];double smooth=t*t*(3-2*t);
            double r=.5*(a==0?width[j]:b==0?width[i]:width[i]+(width[j]-width[i])*smooth),half=r;
            if(a!=b){double along=a==color?t:1-t,reach=(a==0||b==0)?.65:.5+transition;
                double d=Math.max(0,along-(reach-r));
                half=Math.abs(along-reach)<1e-10?0:Math.sqrt(Math.max(0,r*r-d*d));}
            double along=startAlong+k+t,side=startLane+lane+wave((float)along)+offset(lane,(float)along);
            double u=(cross?side-half:along)*cell,v=(cross?along:side-half)*cell;
            double u2=(cross?side+half:along)*cell,v2=(cross?along:side+half)*cell;
            return new float[]{(float)((u-v)*INV_SQRT2),(float)((u+v)*INV_SQRT2),
                (float)((u2-v2)*INV_SQRT2),(float)((u2+v2)*INV_SQRT2)};
        }
        private void append(ArrayList<float[]> out,int lane,int k,double a,double b,int color,float tol){
            float[] p=point(lane,k,a,color),q=point(lane,k,b,color);
            if(out.isEmpty())out.add(p);subdivide(out,lane,k,a,b,p,q,color,tol,0);
        }
        private void subdivide(ArrayList<float[]> out,int lane,int k,double a,double b,float[] p,float[] q,
                int color,float tol,int depth){
            double mid=(a+b)*.5;float[] m=point(lane,k,mid,color);
            // A bounded parametric step prevents missed sinusoidal inflections.
            if(depth<22&&(b-a>.125||error(m,p,q,0)>tol||error(m,p,q,2)>tol)){
                subdivide(out,lane,k,a,mid,p,m,color,tol,depth+1);
                subdivide(out,lane,k,mid,b,m,q,color,tol,depth+1);
            }else out.add(q);
        }
        private static double error(float[] p,float[] a,float[] b,int i){
            double dx=b[i]-a[i],dy=b[i+1]-a[i+1],den=dx*dx+dy*dy;
            double t=den==0?0:Math.max(0,Math.min(1,((p[i]-a[i])*dx+(p[i+1]-a[i+1])*dy)/den));
            return Math.hypot(p[i]-a[i]-t*dx,p[i+1]-a[i+1]-t*dy);
        }
        int paint(boolean[][] masks,int at,float along,float side,float warp){
            int lane=closestLane(along,side,warp);
            int k=(int)Math.floor(along)-startAlong;
            if(lane<0||lane>=lanes||k<0||k+1>=steps)return 0;
            int i=lane*steps+k,j=i+1;
            float t=along-(startAlong+k),smooth=t*t*(3-2*t);
            int a=slot[i],b=slot[j],top=0;
            float distance=Math.abs(side-warp-offset(lane,along)-(startLane+lane));
            if(a==0&&b==0)return 0;
            // Maintain a finite ribbon width right up to the rounded cap.
            // Never interpolate an entire segment to zero width.
            float w=a==0?width[j]:b==0?width[i]:width[i]+(width[j]-width[i])*smooth;
            float radius=w*.5f;
            if(a==b){if(distance<=radius){masks[a][at]=true;return a;}return 0;}
            float endA=b==0?.65f:.5f+transition;
            float endB=a==0?.65f:.5f+transition;
            if(a!=0&&roundedEnd(t,distance,radius,endA)){masks[a][at]=true;top=a;}
            if(b!=0&&roundedEnd(1-t,distance,radius,endB)){masks[b][at]=true;top=Math.max(top,b);}
            return top;
        }
    }

    static boolean roundedEnd(float along,float side,float radius,float end){
        float d=Math.max(0f,along-(end-radius));
        return d*d+side*side<=radius*radius;
    }

    private static float[] diagonalBounds(float w,float h,boolean first){
        float[] a={0,w*INV_SQRT2,h*INV_SQRT2,(w+h)*INV_SQRT2};
        if(!first)a=new float[]{0,-w*INV_SQRT2,h*INV_SQRT2,(h-w)*INV_SQRT2};
        float lo=Float.POSITIVE_INFINITY,hi=Float.NEGATIVE_INFINITY;
        for(float x:a){CancellationCheckpoint.check();lo=Math.min(lo,x);hi=Math.max(hi,x);}return new float[]{lo,hi};
    }
    /** Nearest available valid source pixel for a partially snipped boundary cell. */
    private static float[] sampleWithinCell(Field f,float cx,float cy,float cell){
        if(f.isValid(cx,cy))return new float[]{cx,cy};
        float d=cell*.27f;
        for(int a=0;a<8;a++){if(((a)&63)==0)CancellationCheckpoint.check();
            float theta=a*TAU/8f;
            float x=cx+d*(float)Math.cos(theta),y=cy+d*(float)Math.sin(theta);
            if(f.isValid(x,y))return new float[]{x,y};
        }
        return null;
    }
    /**
     * Local mean reduces a single dark pixel's tendency to cover an entire cell.
     * Sample at the center and a symmetric ring, ignoring the transparent Snip.
     * This is a photo/color sample, not an additional bitmap allocation.
     */
    private static int averageCellColor(StyleKit.Source src,Field f,float x,float y,
                                        float cell){
        float radius=cell*.205f;
        float red=0,green=0,blue=0,weight=0;
        for(int j=0;j<9;j++){if(((j)&63)==0)CancellationCheckpoint.check();
            float px=x+(j==0?0:(float)Math.cos(TAU*(j-1)/8f)*radius);
            float py=y+(j==0?0:(float)Math.sin(TAU*(j-1)/8f)*radius);
            if(!f.isValid(px,py))continue;
            int c=src.colorAt(px,py);
            float w=j==0?2f:1f;
            red+=w*((c>>16)&255);green+=w*((c>>8)&255);blue+=w*(c&255);weight+=w;
        }
        if(weight==0)return src.colorAt(x,y);
        return 0xff000000|(Math.round(red/weight)<<16)|
            (Math.round(green/weight)<<8)|Math.round(blue/weight);
    }
    /** A single pale sheet underneath opaque colored islands, with perceptual tone
     *  controlling the amount of upper material. Fill is never full in a highlight. */
    static float coverageForTone(float targetL,float baseL,float inkL){
        if(Math.abs(baseL-inkL)<2f)return .10f;
        float coverage=(targetL-baseL)/(inkL-baseL);
        return StyleKit.clampF(coverage,.055f,.925f);
    }
    /** Choose the pigment that can reproduce the photograph when spatially mixed
     *  with the physical base. Matching full-area swatches alone caused muddy skin. */
    static int toneAwareSlot(float[] target,float[][] labs,float baseL){
        int best=1;float bestCost=Float.POSITIVE_INFINITY;
        for(int i=1;i<labs.length;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            float l=labs[i][0];
            float f=coverageForTone(target[0],baseL,l);
            float predictedL=baseL+f*(l-baseL);
            float predictedA=labs[0][1]+f*(labs[i][1]-labs[0][1]);
            float predictedB=labs[0][2]+f*(labs[i][2]-labs[0][2]);
            float dl=predictedL-target[0], da=predictedA-target[1], db=predictedB-target[2];
            float direct=StyleKit.labDist2(target,labs[i]);
            float cost=2.6f*dl*dl+.73f*(da*da+db*db)+.18f*direct;
            // Prefer visible colored interiors over an unnecessarily dark cell.
            if(f>.78f)cost+=(f-.78f)*(f-.78f)*800f;
            // Preserve intentionally reversed/custom physical layer order.
            // The model must work for both pale and dark Snip foundations.
            if(Math.abs(l-baseL)<4f)cost+=800f;
            if(cost<bestCost){bestCost=cost;best=i;}
        }
        return best;
    }
    /** Binary disk opening removes sub-minimum-width points and narrow bridges. */
    private static boolean[] open(boolean[] raw,int w,int h,int radius,boolean[] valid){
        int n=w*h;
        boolean[] eroded=new boolean[n],clean=new boolean[n];
        int rr=radius*radius;
        for(int y=0;y<h;y++){if(((y)&63)==0)CancellationCheckpoint.check();
            if((y&31)==0)StyleKit.checkCancelled();
            for(int x=0;x<w;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                int i=y*w+x;if(!raw[i])continue;boolean ok=true;
                disk:for(int dy=-radius;dy<=radius;dy++)for(int dx=-radius;dx<=radius;dx++){if(((dx)&63)==0)CancellationCheckpoint.check();
                    if(dx*dx+dy*dy>rr)continue;
                    int xx=x+dx,yy=y+dy;
                    if(xx<0||yy<0||xx>=w||yy>=h||!raw[yy*w+xx]){ok=false;break disk;}
                }
                eroded[i]=ok;
            }
        }
        for(int y=0;y<h;y++){if(((y)&63)==0)CancellationCheckpoint.check();
            if((y&31)==0)StyleKit.checkCancelled();
            for(int x=0;x<w;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                int i=y*w+x;if(!valid[i])continue;
                disk:for(int dy=-radius;dy<=radius;dy++)for(int dx=-radius;dx<=radius;dx++){if(((dx)&63)==0)CancellationCheckpoint.check();
                    if(dx*dx+dy*dy>rr)continue;
                    int xx=x+dx,yy=y+dy;
                    if(xx>=0&&yy>=0&&xx<w&&yy<h&&eroded[yy*w+xx]){
                        clean[i]=raw[i];break disk;
                    }
                }
            }
        }
        return clean;
    }
    private static boolean[] removeSmall(boolean[] raw,int w,int h,int minPixels){
        boolean[] result=raw.clone(),seen=new boolean[raw.length];
        int[] queue=new int[raw.length];
        for(int i=0;i<raw.length;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            if(!raw[i]||seen[i])continue;
            int head=0,tail=0;queue[tail++]=i;seen[i]=true;
            while(head<tail){int at=queue[head++],x=at%w,y=at/w;
                if(x>0&&raw[at-1]&&!seen[at-1]){seen[at-1]=true;queue[tail++]=at-1;}
                if(x+1<w&&raw[at+1]&&!seen[at+1]){seen[at+1]=true;queue[tail++]=at+1;}
                if(y>0&&raw[at-w]&&!seen[at-w]){seen[at-w]=true;queue[tail++]=at-w;}
                if(y+1<h&&raw[at+w]&&!seen[at+w]){seen[at+w]=true;queue[tail++]=at+w;}
            }
            if(tail<minPixels)for(int j=0;j<tail;j++)result[queue[j]]=false;
        }
        return result;
    }
    private static void fillPinholes(boolean[] mask,Field f,int limit){
        boolean[] seen=new boolean[f.n];int[] queue=new int[f.n];
        for(int i=0;i<f.n;i++){
            if((i&1023)==0)CancellationCheckpoint.check();
            if(mask[i]||seen[i]||!f.valid[i])continue;
            int head=0,tail=1;queue[0]=i;seen[i]=true;boolean exterior=false;
            while(head<tail){
                if((head&1023)==0)CancellationCheckpoint.check();
                int at=queue[head++],x=at%f.w,y=at/f.w;
                for(int k=0;k<4;k++){
                    if(k==0&&x==0||k==1&&x==f.w-1||k==2&&y==0||k==3&&y==f.h-1){
                        exterior=true;continue;
                    }
                    int next=at+(k==0?-1:k==1?1:k==2?-f.w:f.w);
                    if(!f.valid[next]){exterior=true;continue;}
                    if(!mask[next]&&!seen[next]){seen[next]=true;queue[tail++]=next;}
                }
            }
            if(!exterior&&tail<=limit)for(int j=0;j<tail;j++)mask[queue[j]]=true;
        }
    }
    private static StyleKit.Layer vectorize(boolean[] mask,Field f,String name,int color,
                                             float minArea,boolean base){
        StyleKit.Layer layer=new StyleKit.Layer(name,color);
        int pw=f.w+2,ph=f.h+2;float[] field=new float[pw*ph];
        Arrays.fill(field,-1f);
        for(int y=0;y<f.h;y++){if(((y)&63)==0)CancellationCheckpoint.check();
            if((y&63)==0)StyleKit.checkCancelled();
            for(int x=0;x<f.w;x++){
                int at=y*f.w+x;
                if(!mask[at]&&!f.valid[at])continue;
                float center=mask[at]?1f:-1f;
                field[(y+1)*pw+x+1]=center;
            }
        }
        ArrayList<float[]> loops=TopographicLayersEngine.contours(field,pw,ph,0f,f.pitch);
        for(float[] loop:loops){CancellationCheckpoint.check();
            if((layer.loops.size()&63)==0)StyleKit.checkCancelled();
            float[] raw=loop;
            loop=StyleKit.removeCollinear(loop,Math.max(.012f,f.pitch*.08f));
            if(loop.length<6||Math.abs(StyleKit.signedArea(loop))<minArea)continue;
            if(StyleKit.selfIntersections(loop,1)!=0)continue;
            if(!base&&loop.length>=16){
                float[] candidate=StyleKit.chaikin(raw,2,true,1);
                candidate=SolidSnipContourRepair.simplifyClosed(candidate,Math.min(.035f,f.pitch*.16f));
                double area=StyleKit.signedArea(loop),newArea=StyleKit.signedArea(candidate);
                if(candidate.length>=6&&area*newArea>0&&Math.abs(newArea-area)<=Math.abs(area)*.025f&&
                    StyleKit.selfIntersections(candidate,1)==0)loop=candidate;
            }
            if(layer.loops.size()>8000)throw new IllegalStateException("Excessive cut detail");
            layer.loops.add(loop);
        }
        return layer;
    }
    private static int hash(int a,int b,long seed){
        int h=(int)(seed^(seed>>>32))^a*0x9e3779b9^b*0x85ebca6b;
        h^=h>>>16;h*=0x7feb352d;h^=h>>>15;h*=0x846ca68b;return h^(h>>>16);
    }
    private static float uniform(int bits){
        int h=bits;h^=h>>>16;h*=0x7feb352d;h^=h>>>15;h*=0x846ca68b;h^=h>>>16;
        return (h&0x00ffffff)/16777216f;
    }
    private static int[] derivePalette(StyleKit.Source src,int count,long seed){
        int stride=Math.max(1,src.argb.length/4500),valid=0;
        int[] color=new int[(src.argb.length+stride-1)/stride];
        float[] weight=new float[color.length];
        for(int i=0;i<src.argb.length;i+=stride)if(src.valid[i]){
            color[valid]=src.argb[i];weight[valid]=1f;valid++;
        }
        if(valid==0)throw new IllegalArgumentException("Empty photo palette");
        int[] sampled=StyleKit.kmeans(Arrays.copyOf(color,valid),Arrays.copyOf(weight,valid),
            Math.min(count,valid),seed);
        int[] result=new int[count];
        // K-means returns dark-to-light. Lightest Snip underlay first means
        // visible highlights can survive darker overlays. Auto palettes only:
        // a pale, photo-tinted base expands contrast without changing manually
        // selected physical vinyl swatches or the mounting surface.
        for(int i=0;i<count;i++)result[i]=sampled[Math.max(0,sampled.length-1-
            Math.min(sampled.length-1,i))];
        float light=StyleKit.toLab(result[0])[0];
        if(light<88f){
            float brighten=StyleKit.clampF((88f-light)/(100f-light),0f,.78f);
            int c=result[0];
            int rr=Math.round(((c>>16)&255)*(1f-brighten)+255f*brighten);
            int gg=Math.round(((c>>8)&255)*(1f-brighten)+255f*brighten);
            int bb=Math.round((c&255)*(1f-brighten)+255f*brighten);
            result[0]=0xff000000|(rr<<16)|(gg<<8)|bb;
        }
        return result;
    }
}

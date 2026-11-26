package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;

/** Photo-guided, continuous diagonal ribbons on independent opaque vinyl sheets.
 * The two warped diagonal families cross without subtracting lower material.
 * Physical layer order alone determines the visible color at each intersection.
 */
public final class PrismaticDiagonalPortraitEngine {
    private PrismaticDiagonalPortraitEngine() {}
    private static final float INV_SQRT2 = 0.7071067811865476f;
    private static final float TAU = (float)(2 * Math.PI);

    public static final class Options {
        public int detail=68, irregularity=43, roundness=52, featureEmphasis=76, toneBalance=65;
        public float widthMm=152.4f, minCutMm=.254f, gapMm=.32f;
        public int layers=6;
        public int[] palette=null;
        public int paperArgb=0xfffaf9f5;
        public boolean[] allowed=null;
        public long seed=20261104L;
        public boolean draft=false;
        /** M5: >0 conditions the final vector sheets so same-sheet weed gaps below this width
         *  are merged and necks below the minimum cut are removed (the Prismatic ribbon
         *  conditioner). 0 keeps the build 559 sheets exactly. Ribbon layout is unaffected. */
        public float conditionGapMm=0f;
        /** M5: above {@link PrismaticRibbonPortraitEngine#PITCH_REFERENCE_MM} the ribbon pitch grows
         *  with sqrt(width) instead of the lane count staying fixed (the sibling styles' rule).
         *  false keeps the build 559 layout exactly. */
        public boolean sizeAwarePitch=false;
        public Options copy() {
            Options p=new Options(); p.detail=detail;p.irregularity=irregularity;
            p.roundness=roundness;p.featureEmphasis=featureEmphasis;p.toneBalance=toneBalance;
            p.widthMm=widthMm;p.minCutMm=minCutMm;p.gapMm=gapMm;p.layers=layers;
            p.palette=palette==null?null:palette.clone();p.paperArgb=paperArgb;p.draft=draft;
            p.allowed=allowed==null?null:allowed.clone();p.seed=seed;p.conditionGapMm=conditionGapMm;p.sizeAwarePitch=sizeAwarePitch;return p;
        }
        void validate(){
            if(detail<0||detail>100||irregularity<0||irregularity>100||
               roundness<0||roundness>100||featureEmphasis<0||featureEmphasis>100||
               toneBalance<0||toneBalance>100||
               layers<2||layers>8||!Float.isFinite(widthMm)||widthMm<20||widthMm>2000||
               !Float.isFinite(minCutMm)||minCutMm<.10f||minCutMm>4||
               !Float.isFinite(gapMm)||gapMm<.10f||gapMm>5||
               !Float.isFinite(conditionGapMm)||conditionGapMm<0||conditionGapMm>5||
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
        float detailCell=(float)Math.sqrt(2)*f.widthMm/across;
        if(o.sizeAwarePitch&&f.widthMm>PrismaticRibbonPortraitEngine.PITCH_REFERENCE_MM)
            detailCell=(float)(Math.sqrt(2)*Math.sqrt(f.widthMm*PrismaticRibbonPortraitEngine.PITCH_REFERENCE_MM)/across);
        final float cell=Math.max(Math.max(minWidth*2.5f,effectiveGap*2.8f),detailCell);
        final float maxFraction=Math.min(.85f,1f-1.16f*effectiveGap/cell);
        final float minFraction=minWidth/cell;
        final float[][] lab=StyleKit.labOf(palette);
        final java.util.List<BandPair> candidates=bandPairs(lab,palette,minFraction,maxFraction);
        BandField first=new BandField(f,o,cell,false,candidates,lab);
        BandField second=new BandField(f,o,cell,true,candidates,lab);
        for(int pass=0;pass<2;pass++){first.refine(second,candidates,lab);second.refine(first,candidates,lab);}
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
            StyleKit.Layer layer=vectorize(mask,f,"prismatic-diagonal-"+(slot+1),palette[slot],minArea,slot==0);
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
        result.summary="Prismatic Diagonal Portrait · interwoven ribbons, "+
            overlapPixels+" cross-color overlap samples, "+result.layers.size()+" physical layers, "+
            pieces+" cut contours (minimum feature floor "+
            Math.round(Math.max(o.minCutMm,2*f.pitch)*100f)/100f+" mm)";
        return result;
    }

    private static StyleKit.Result vectorResult(Field f,Options o,int[] palette,
            BandField first,BandField second,VectorBooleanEngine.Session vector){
        StyleKit.Result result=new StyleKit.Result(f.widthMm,f.heightMm);
        result.paperArgb=o.paperArgb;
        StyleKit.Layer base=vectorize(f.valid,f,"prismatic-diagonal-1",palette[0],0,true);
        for(int i=0;i<base.loops.size();i++)base.loops.set(i,
            StyleKit.clipToArtboardOrOriginal(base.loops.get(i),f.widthMm,f.heightMm));
        vector.setClip(base.loops);
        base.loops.clear();base.loops.addAll(vector.foundation(f.widthMm,f.heightMm));
        base.vectorGeometryLocked=true;result.layers.add(base);
        first.emit(vector);second.emit(vector);
        int pieces=base.loops.size();
        float minArea=Math.max(2.5f*f.pitch*f.pitch,o.minCutMm*o.minCutMm*1.5f);
        float effectiveGap=Math.max(o.gapMm+f.pitch,2*f.pitch);
        float holeArea=Math.max(effectiveGap*effectiveGap,first.cell*first.cell*.035f);
        // M5: the 559 locked sheets left ~300 same-sheet weed gaps per portrait (an end cap
        // of one family stopping just short of a same-colour ribbon of the other family).
        // Measured on 8 photos at 6 and 8 in: findings 326 -> 4.6 per render, tone
        // correlation -0.001, OKLab dE +0.0001. The ribbon layout itself is unchanged.
        ArrayList<ArrayList<float[]>> conditioned=null;double before=0,after=0;
        float closeR=o.conditionGapMm*.5f+.03f,openR=o.minCutMm*.5f+.03f;
        if(o.conditionGapMm>0){
            double[][] area=new double[o.layers][2];
            conditioned=vector.finishConditionedAll(f.widthMm,f.heightMm,minArea,holeArea,closeR,openR,area);
            for(int slot=1;slot<o.layers;slot++){before+=area[slot][0];after+=area[slot][1];}
        }
        for(int slot=1;slot<o.layers;slot++){
            CancellationCheckpoint.check();
            StyleKit.Layer layer=new StyleKit.Layer("prismatic-diagonal-"+(slot+1),palette[slot]);
            layer.loops.addAll(conditioned!=null?conditioned.get(slot):vector.finish(slot,f.widthMm,f.heightMm,true,minArea,holeArea));
            layer.vectorGeometryLocked=true;result.layers.add(layer);pieces+=layer.loops.size();
        }
        result.summary="Prismatic Diagonal Portrait · continuous interwoven ribbons · independent vector Boolean overlays · "+
            result.layers.size()+" physical layers, "+pieces+" cut contours (minimum feature floor "+
            Math.round(Math.max(o.minCutMm,2*f.pitch)*100f)/100f+" mm)"+
            (conditioned!=null&&Double.isFinite(before)&&before>0?String.format(java.util.Locale.US,
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
        final float[] waveByRow;
        BandField(Field f,Options o,float cell,boolean cross,java.util.List<BandPair> candidates,float[][] lab){
            this.f=f;this.o=o;this.cell=cell;this.cross=cross;
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
            feature=o.featureEmphasis/100f;
            float lightMin=100f,lightMax=0f;
            for(float[] c:lab){lightMin=Math.min(lightMin,c[0]);lightMax=Math.max(lightMax,c[0]);}
            float range=lightMax-lightMin;
            adapt=StyleKit.clampF((75f-range)/35f,0f,1f);
            chromaWeight=.72f*(1f-.35f*adapt);
            float[] swatchCosts=new float[lab.length];
            for(int lane=0;lane<lanes;lane++){
                StyleKit.checkCancelled();
                int previous=0;
                for(int k=0;k<steps;k++){
                    if((k&15)==0)CancellationCheckpoint.check();
                    float a=startAlong+k,b=startLane+lane+waveByRow[k];
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
                    for(BandPair pair:candidates){
                        int own=cross?pair.b:pair.a;
                        float cost=pairCost(pair,target,swatchCosts);
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
        private float pairCost(BandPair p,float[] target,float[] swatchCosts){
            float dl=target[0]-p.l,da=target[1]-p.x,db=target[2]-p.y;
            float speckle=p.ca*swatchCosts[p.a]+p.cb*swatchCosts[p.b];
            return (2.45f+1.6f*adapt)*dl*dl+chromaWeight*(da*da+db*db)+(.16f+.30f*adapt)*speckle+p.penalty;
        }
        private int nearest(float x,float y){
            float u=(x+y)*INV_SQRT2/cell,v=(y-x)*INV_SQRT2/cell;
            float along=cross?v:u,side=cross?u:v;
            int k=Math.round(along)-startAlong,lane=Math.round(side-wave(along))-startLane;
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
                    for(BandPair p:bySlot.get(op)){
                        if(Math.abs((cross?p.wa:p.wb)-ow)>1e-5f)continue;
                        int own=cross?p.b:p.a;
                        float cost=pairCost(p,target,swatchCosts)+((own==prev?0:75)+(own==next?0:75))*(1-.35f*feature);
                        if(cost<bestCost){bestCost=cost;best=p;}
                    }
                    if(best!=null){slot[at]=(byte)(cross?best.b:best.a);width[at]=cross?best.wb:best.wa;}
                }
            }
        }
        float wave(float along){
            return amplitude*((float)Math.sin(along*frequency+phase)+
                .15f*(float)Math.sin(along*frequency*.47f-phase));
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
            double along=startAlong+k+t,side=startLane+lane+wave((float)along);
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
            float shifted=side-warp;int lane=Math.round(shifted)-startLane;
            int k=(int)Math.floor(along)-startAlong;
            if(lane<0||lane>=lanes||k<0||k+1>=steps)return 0;
            int i=lane*steps+k,j=i+1;
            float t=along-(startAlong+k),smooth=t*t*(3-2*t);
            int a=slot[i],b=slot[j],top=0;
            float distance=Math.abs(shifted-Math.round(shifted));
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

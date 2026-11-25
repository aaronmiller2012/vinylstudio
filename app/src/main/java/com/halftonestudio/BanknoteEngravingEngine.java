package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * General-purpose banknote-like portrait engraving for opaque, cut vinyl.
 * The engine emits filled, closed ribbons only. A separate physical sheet is
 * used for cross-etching, and source alpha / Snip is never painted over.
 * No Android dependencies; deterministic on a plain JVM.
 */
public final class BanknoteEngravingEngine {
    private BanknoteEngravingEngine() { }

    /** Variant codes are saved in projects; never reorder these IDs. */
    public static final int STRAIGHT = 0, CONTOUR = 1, CROSS_ETCH = 2,
            WAVY = 3, HIGHLIGHT_DASH = 4, VIGNETTE = 5,
            RETIRED_GUILLOCHE = 6, TWO_INK = 7, CONCENTRIC = 8;
    public static final String[] VARIANTS = {
            "Straight engraving", "Contour flow", "Cross-etched shadows",
            "Wavy engraving", "Highlight dashes", "Vignette",
            "(retired variant 6)", "Two-ink engraving", "Concentric portrait"
    };
    private static final int MAX_PIECES = 5000;

    public static final class Options {
        public float widthMm = 152.4f;
        public float pitchMm = 1.65f, angleDeg = 35f, warpMm = 0.28f;
        public float minMm = 0.254f, gapMm = 0.24f, stepMm = 0.4f;
        public float minRunMm = 1.4f, marginMm = 1.0f;
        public float widthGamma = 0.85f, swellMm = 1f, weightMm = 0.7f;
        public float crossThreshold = 0.69f, crossAngleDeg = 75f;
        public boolean crossEtch = false, dashHighlights = false;
        public boolean vignette = false;
        public int variant = CONTOUR;
        public int inkArgb = 0xff193e32, crossArgb = 0xff11151a;
        public int paperArgb = 0xfffaf9f5;
        public boolean autoLevels = true;
        public float gamma = 1f, contrast = 1f;
        public boolean[] allowed = null;
        /** M5 error-diffused highlight dashes for the main engraving (never the cross-etch
         *  sheet or Concentric). Negative (default) = automatic: the Highlight dashes variant
         *  (replacing its fixed 8.5 mm rhythm) and any variant whose cut minimum is at least
         *  half the widest ribbon. 0 = pre-M5 geometry. Above 0 = always, at this strength. */
        public float highlightDiffusion = -1f;

        public Options copy() {
            Options n = new Options();
            n.widthMm=widthMm;n.pitchMm=pitchMm;n.angleDeg=angleDeg;n.warpMm=warpMm;
            n.minMm=minMm;n.gapMm=gapMm;n.stepMm=stepMm;n.minRunMm=minRunMm;
            n.marginMm=marginMm;n.widthGamma=widthGamma;n.swellMm=swellMm;n.weightMm=weightMm;
            n.crossThreshold=crossThreshold;n.crossAngleDeg=crossAngleDeg;
            n.crossEtch=crossEtch;n.dashHighlights=dashHighlights;n.highlightDiffusion=highlightDiffusion;
            n.vignette=vignette;
            n.variant=variant;n.inkArgb=inkArgb;n.crossArgb=crossArgb;
            n.paperArgb=paperArgb;
            n.autoLevels=autoLevels;n.gamma=gamma;n.contrast=contrast;
            n.allowed=allowed==null?null:allowed.clone();return n;
        }
        public void validate() {
            if (!finite(widthMm, 20, 2000) || !finite(pitchMm, .45f, 12f) ||
                    !finite(angleDeg, -360, 360) || !finite(warpMm, 0, 10) ||
                    !finite(minMm, .10f, 4) || !finite(gapMm, .1f, 4) ||
                    !finite(stepMm, .15f, .5f) || !finite(minRunMm, .4f, 15) ||
                    !finite(marginMm, 0, 40) || !finite(widthGamma, .25f, 3) ||
                    !finite(swellMm, 0, 5) || !finite(weightMm, .2f, 3f) || !finite(crossThreshold, .40f, .95f) ||
                    !finite(crossAngleDeg, 45, 100) || !finite(highlightDiffusion, -1, 1) ||
                    !finite(gamma, .2f, 4) ||
                    !finite(contrast, .2f, 3) || variant < 0 || variant >= VARIANTS.length ||
                    variant == RETIRED_GUILLOCHE)
                throw new IllegalArgumentException("Invalid banknote engraving options");
            if (minMm + gapMm + .08f > pitchMm)
                throw new IllegalArgumentException("Pitch must exceed minimum width plus weed gap");
            boolean crossing = crossEtch || variant == CROSS_ETCH || variant == TWO_INK;
            if (crossing && inkArgb == crossArgb)
                throw new IllegalArgumentException("Cross-etch requires a different vinyl color");
        }
        private static boolean finite(float n, float lo, float hi) {
            return Float.isFinite(n) && n >= lo && n <= hi;
        }
    }

    private static final class Buffer {
        float[] xy = new float[256], hw = new float[128];
        int n = 0;
        void add(float x, float y, float half) {
            if (n == hw.length) {hw=Arrays.copyOf(hw,hw.length*2);xy=Arrays.copyOf(xy,xy.length*2);}
            xy[2*n]=x;xy[2*n+1]=y;hw[n]=half;n++;
        }
        void reset() {n=0;}
    }

    /** Bilinear millimetre sampler on a lower resolution, smoothly blurred flow field. */
    private static final class Flow {
        final float[] field;
        final int w,h;
        final float px;
        Flow(StyleKit.Source src) {
            // Resample to ~1.5 mm so a 4-8 mm blur stays bounded on phone CPUs.
            int block=Math.max(1,(int)Math.ceil(1.5f/src.mmPerPx));
            w=(src.w+block-1)/block;h=(src.h+block-1)/block;
            px=src.widthMm/w;
            float[] a=new float[w*h];
            for(int y=0;y<h;y++){if(((y)&63)==0)CancellationCheckpoint.check();
                if((y&15)==0)StyleKit.checkCancelled();
                for(int x=0;x<w;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                    double sum=0;int count=0;
                    for(int yy=y*block;yy<Math.min(src.h,(y+1)*block);yy++)
                        for(int xx=x*block;xx<Math.min(src.w,(x+1)*block);xx++){if(((xx)&63)==0)CancellationCheckpoint.check();
                            int i=yy*src.w+xx;
                            if(src.valid[i]){sum+=src.lum[i];count++;}
                        }
                    a[y*w+x]=count==0?.5f:(float)(sum/count);
                }
            }
            field=StyleKit.blur(a,w,h,Math.max(.35f,5f/px));
        }
        float get(float x,float y) {
            float fx=x/px-.5f,fy=y/px-.5f;
            int x0=(int)Math.floor(fx),y0=(int)Math.floor(fy);
            float tx=fx-x0,ty=fy-y0;
            int a=StyleKit.clampI(x0,0,w-1),b=StyleKit.clampI(x0+1,0,w-1);
            int c=StyleKit.clampI(y0,0,h-1),d=StyleKit.clampI(y0+1,0,h-1);
            return (field[c*w+a]*(1-tx)+field[c*w+b]*tx)*(1-ty)
                  +(field[d*w+a]*(1-tx)+field[d*w+b]*tx)*ty;
        }
    }

    public static StyleKit.Result generate(int[] argb, int w, int h, Options o) {
        if (o == null) throw new IllegalArgumentException("Options required");
        o.validate();
        // Saved variant IDs describe visual styles even when called directly
        // from host tests or a future alternate UI, not only via VinylStyleSuite.
        // Work on a copy so the caller's options remain unchanged.
        o=o.copy();
        if(o.variant==TWO_INK){
            // Green primary engraving carries midtones; black screen enters
            // earlier and at a different orientation than CROSS_ETCH shadows.
            o.crossThreshold=Math.min(o.crossThreshold,.52f);
            o.crossAngleDeg=Math.max(o.crossAngleDeg,85f);
            o.widthGamma=Math.max(o.widthGamma,1.10f);
        }
        StyleKit.Source src=new StyleKit.Source(argb,w,h,o.allowed,o.widthMm);
        if(o.autoLevels)src.autoLevels(.02f,.98f);
        src.tone(o.gamma,o.contrast);
        if(!src.anyValid())throw new IllegalArgumentException("No usable pixels");
        if(o.marginMm*2>=Math.min(src.widthMm,src.heightMm))
            throw new IllegalArgumentException("Artwork too small for margin");
        StyleKit.Result out=new StyleKit.Result(src.widthMm,src.heightMm);
        out.paperArgb=o.paperArgb;
        StyleKit.Layer primary=new StyleKit.Layer("banknote-lines",o.inkArgb);
        primary.strokeMm=o.minMm;out.layers.add(primary);
        boolean crossing=o.crossEtch||o.variant==CROSS_ETCH||o.variant==TWO_INK;
        StyleKit.Layer secondary=null;
        if(crossing){secondary=new StyleKit.Layer("banknote-cross-etch",o.crossArgb);
            secondary.strokeMm=o.minMm;out.layers.add(secondary);}
        Flow flow=(o.variant==CONTOUR||o.variant==CROSS_ETCH||o.variant==TWO_INK||o.warpMm>0)?new Flow(src):null;
        float[] dark=new float[src.lum.length];
        for(int i=0;i<dark.length;i++)dark[i]=src.valid[i]?1f-src.lum[i]:0f;
        float[] soft=StyleKit.blur(dark,src.w,src.h,Math.max(.35f,.34f/src.mmPerPx));
        DashPlan plan=dashPlan(o);
        int main;
        for(int attempt=0;;attempt++){
            main=o.variant==CONCENTRIC?
                rings(src,soft,primary,o):pass(src,soft,flow,primary,o,o.angleDeg,false,plan);
            if(plan==null||!plan.stats.overflow)break;
            // Too many dashes for one SVG: longer (fewer) dashes; the last attempt is the
            // pre-M5 geometry. Deterministic: no time budget is involved.
            primary.loops.clear();
            plan=attempt<2?plan.longer(1.8f):null;
        }
        int cross=0;
        if(crossing) cross=pass(src,soft,flow,secondary,o,o.angleDeg+o.crossAngleDeg,true,null);
        out.summary="Banknote engraving: "+main+" main runs, "+cross+" cross-etch runs"+
                "; "+out.layers.size()+" vinyl colors";
        return out;
    }

    /** Tone (0 paper .. 1 ink) below which the main engraving is modulated into dashes. */
    static final float DASH_TONE=.45f;
    /** Strength used when diffusion is automatic; duty = (tone/DASH_TONE)^(1+4(1-strength)). */
    static final float DEFAULT_DIFFUSION=.75f;
    /** The Highlight dashes variant keeps visibly more highlight dashes (linear duty), so it
     *  stays distinct from Straight engraving when both are diffused (0.8 mm profiles). */
    static final float HIGHLIGHT_DASH_DIFFUSION=1f;
    /** Automatic diffusion for every line variant once the cut minimum is at least this share
     *  of the widest ribbon: the width can then no longer carry tone (at 0.8 mm the 560
     *  output is a near-uniform screen). */
    static final float COLLAPSED_WIDTH_RANGE=.5f;

    /** Main-pass warp envelope and widest ribbon, shared by pass() and the dash decision. */
    private static float warpCap(Options o){
        return (o.variant==STRAIGHT||o.variant==HIGHLIGHT_DASH||o.variant==VIGNETTE)?0f:
                Math.min(o.warpMm,Math.min(.12f*o.pitchMm,(o.pitchMm-o.minMm-o.gapMm)*.20f));
    }
    private static float maxWidth(Options o,float warpCap){
        // Every polygon remains in its own disjoint lane. Miter is capped at 1.15.
        // This conservative envelope also reserves the physical gap after warping.
        float laneMax=Math.min(o.pitchMm-o.gapMm,2f*((o.pitchMm-o.gapMm)*.5f-warpCap)/1.15f);
        return Math.min(laneMax,Math.max(o.minMm,o.weightMm*1.65f));
    }
    static float diffusionStrength(Options o){
        if(o.highlightDiffusion>0f)return Math.min(1f,o.highlightDiffusion);
        if(o.highlightDiffusion==0f||o.variant==CONCENTRIC)return 0f;
        if(o.dashHighlights||o.variant==HIGHLIGHT_DASH)return HIGHLIGHT_DASH_DIFFUSION;
        float widest=maxWidth(o,warpCap(o));
        return widest>0f&&o.minMm>=COLLAPSED_WIDTH_RANGE*widest?DEFAULT_DIFFUSION:0f;
    }
    static DashPlan dashPlan(Options o){
        float strength=diffusionStrength(o);
        if(!(strength>0f)||o.variant==CONCENTRIC)return null;
        return new DashPlan(o.minMm,Math.max(o.minRunMm,2f*o.minMm),o.gapMm+DashPlan.GAP_MARGIN_MM,
            1f+4f*(1f-strength),new DashPlan.Stats());
    }

    private static int pass(StyleKit.Source src,float[] dark,Flow flow,StyleKit.Layer layer,
                            Options o,float angle,boolean cross,DashPlan plan) {
        final double radians=Math.toRadians(angle),ca=Math.cos(radians),sa=Math.sin(radians);
        final float nx=(float)-sa,ny=(float)ca;
        final float cx=src.widthMm/2f,cy=src.heightMm/2f;
        final float diag=(float)Math.hypot(src.widthMm,src.heightMm);
        final float warpCap=warpCap(o);
        final float maxWidth=maxWidth(o,warpCap);
        if(maxWidth < o.minMm)throw new IllegalArgumentException("Insufficient room for cuttable ribbons");
        int runs=0,lineNumber=0;
        Buffer b=new Buffer();
        int nLines=(int)Math.ceil(diag/o.pitchMm)+1;
        if(nLines>250)throw new IllegalArgumentException(
                "Too many engraving lines for a cuttable screen; increase Pitch or reduce Detail");
        final int[] emitted={0};
        final Buffer dashBuffer=new Buffer();
        for(int li=0;li<nLines;li++){
            StyleKit.checkCancelled();
            float s=(li-(nLines-1)*.5f)*o.pitchMm;
            lineNumber++;
            b.reset();
            // M5: highlights become error-diffused dashes (DashModulator): coverage
            // falls to paper with tone instead of stopping at a minimum-width floor.
            final DashModulator dm=(plan==null||cross)?null:
                new DashModulator(plan,o.stepMm,li,layer.loops,run->{
                    dashBuffer.reset();
                    for(float[] q:run)dashBuffer.add(q[0],q[1],q[2]);
                    emitted[0]+=flush(dashBuffer,src,o,layer);
                });
            int steps=(int)Math.ceil(diag/o.stepMm);
            for(int j=0;j<=steps;j++){if(((j)&63)==0)CancellationCheckpoint.check();
                if((j&127)==0)StyleKit.checkCancelled();
                float t=(j-steps*.5f)*o.stepMm;
                float ux=cx+(float)(t*ca)+s*nx;
                float uy=cy+(float)(t*sa)+s*ny;
                float offset=warpCap==0?0:warpCap*2f*(flow.get(ux,uy)-.5f);
                if(o.variant==WAVY){
                    // Make the wave unmistakably visible while staying inside each
                    // cut lane. The earlier implementation used a very long wavelength
                    // and a tiny amplitude, which often looked straight on-device.
                    float local=sample(dark,src,ux,uy);
                    float safeSwing=Math.max(0f,.5f*(o.pitchMm-o.gapMm-maxWidth)*.92f);
                    float waveAmp=Math.max(warpCap,Math.min(safeSwing,Math.max(.62f,warpCap*2.4f)));
                    float wavelength=Math.max(5.4f,o.pitchMm*2.9f);
                    float phase=(float)(2*Math.PI*(t/wavelength))+li*.78f;
                    float envelope=.35f+.65f*StyleKit.clampF(local,0,1);
                    float wave=(float)(Math.sin(phase)+0.22*Math.sin(phase*1.9f+s*.11f));
                    float baseFlow=warpCap==0||flow==null?0f:warpCap*.35f*2f*(flow.get(ux,uy)-.5f);
                    offset=baseFlow+waveAmp*envelope*wave;
                    offset=StyleKit.clampF(offset,-safeSwing,safeSwing);
                }
                float x=ux+nx*offset,y=uy+ny*offset;
                boolean okay=src.validAt(x,y) && x>=o.marginMm+maxWidth/2f &&
                        y>=o.marginMm+maxWidth/2f &&
                        x<=src.widthMm-o.marginMm-maxWidth/2f &&
                        y<=src.heightMm-o.marginMm-maxWidth/2f;
                float tone=okay?sample(dark,src,x,y):0;
                if(cross && tone<o.crossThreshold)okay=false;
                if(o.vignette||o.variant==VIGNETTE){
                    float ex=(x-cx)/Math.max(1f,cx-o.marginMm),ey=(y-cy)/Math.max(1f,cy-o.marginMm);
                    tone*=1f-StyleKit.smoothstep(.45f,1.25f,(float)Math.hypot(ex,ey));
                }
                if(dm==null && okay && !cross && (o.dashHighlights||o.variant==HIGHLIGHT_DASH) && tone<.45f){
                    // Deterministic long highlight dashes; no pinholes / subminimum slivers.
                    float duty=.30f+.52f*StyleKit.clampF(tone/.45f,0,1);
                    float phase=(t+.37f*li*o.pitchMm)%8.5f;
                    if(phase<0)phase+=8.5f;
                    if(phase>8.5f*duty)okay=false;
                }
                float fill=(float)Math.pow(StyleKit.clampF(tone,0,1),o.widthGamma);
                float width=o.minMm+(maxWidth-o.minMm)*fill;
                if(cross){
                    // Cross strokes carry at most half primary width, but remain
                    // >= minMm or are absent; never draw an illegal skinny stroke.
                    width*=.5f;
                    if(width<o.minMm)okay=false;
                }
                float half=Math.max(o.minMm*.5f,width*.5f);
                if(okay && !insideRibbon(src,x,y,nx,ny,half,o.marginMm))okay=false;
                if(dm!=null){
                    if(!okay)dm.hard();
                    else if(tone>=DASH_TONE)dm.fixed(new float[]{x,y,half});
                    else dm.soft(x,y,StyleKit.clampF(tone/DASH_TONE,0,1),half);
                }
                else if(okay)b.add(x,y,half);
                else {runs+=flush(b,src,o,layer);b.reset();}
                if(layer.loops.size()>=MAX_PIECES)throw new IllegalArgumentException(
                        "Banknote exceeds 5000 cut pieces; reduce Detail or turn off highlight dashes");
            }
            if(dm!=null)dm.end();
            runs+=flush(b,src,o,layer);
            if(layer.loops.size()>=MAX_PIECES)throw new IllegalArgumentException(
                        "Banknote exceeds 5000 cut pieces; reduce Detail or turn off highlight dashes");
        }
        return runs+emitted[0];
    }

    private static float sample(float[] field,StyleKit.Source s,float x,float y) {
        float fx=x/s.mmPerPx-.5f,fy=y/s.mmPerPx-.5f;
        int xi=(int)Math.floor(fx),yi=(int)Math.floor(fy);
        float tx=fx-xi,ty=fy-yi;
        int x0=StyleKit.clampI(xi,0,s.w-1),x1=StyleKit.clampI(xi+1,0,s.w-1);
        int y0=StyleKit.clampI(yi,0,s.h-1),y1=StyleKit.clampI(yi+1,0,s.h-1);
        return (field[y0*s.w+x0]*(1-tx)+field[y0*s.w+x1]*tx)*(1-ty)
                +(field[y1*s.w+x0]*(1-tx)+field[y1*s.w+x1]*tx)*ty;
    }

    private static boolean insideRibbon(StyleKit.Source s,float x,float y,
                                        float nx,float ny,float h,float margin) {
        return x-h>margin && y-h>margin && x+h<s.widthMm-margin && y+h<s.heightMm-margin &&
                s.validAt(x,y) && s.validAt(x+nx*h,y+ny*h) &&
                s.validAt(x-nx*h,y-ny*h) &&
                s.validAt(x+nx*h*.55f,y+ny*h*.55f) &&
                s.validAt(x-nx*h*.55f,y-ny*h*.55f);
    }

    private static int flush(Buffer b,StyleKit.Source src,Options o,StyleKit.Layer layer) {
        return flushSegment(b.xy,b.hw,0,b.n,src,o,layer,0);
    }

    /** Recover sound portions rather than dropping an entire portrait line on a Snip edge. */
    private static int flushSegment(float[] points,float[] widths,int start,int count,
                                    StyleKit.Source src,Options o,StyleKit.Layer layer,int depth) {
        if(layer.loops.size()>=MAX_PIECES)throw new IllegalArgumentException(
                "Banknote exceeds 5000 cut pieces; reduce Detail or turn off highlight dashes");
        if(count<2||depth>32)return 0;
        float len=0;
        for(int i=start+1;i<start+count;i++)
            len+=(float)Math.hypot(points[2*i]-points[2*i-2],points[2*i+1]-points[2*i-1]);
        if(len<o.minRunMm)return 0;
        float[] xy=Arrays.copyOfRange(points,start*2,(start+count)*2);
        float[] hw=Arrays.copyOfRange(widths,start,start+count);
        if(o.swellMm>0){
            float walked=0;
            for(int i=0;i<count;i++){if(((i)&63)==0)CancellationCheckpoint.check();
                if(i>0)walked+=(float)Math.hypot(xy[2*i]-xy[2*i-2],xy[2*i+1]-xy[2*i-1]);
                float t=StyleKit.clampF(Math.min(walked,len-walked)/o.swellMm,0,1);
                t=t*t*(3-2*t);
                hw[i]=.5f*o.minMm+(hw[i]-.5f*o.minMm)*t;
            }
        }
        float[] poly=StyleKit.ribbon(xy,hw,1.15f,false);
        if(poly==null||poly.length<6||Math.abs(StyleKit.signedArea(poly))<o.minMm*o.minRunMm*.20f)return 0;
        int bad=-1;
        for(int i=0;i<poly.length;i+=2){if(((i)&63)==0)CancellationCheckpoint.check();
            float x=poly[i],y=poly[i+1];
            if(!src.validAt(x,y)||x<o.marginMm||y<o.marginMm ||
                    x>src.widthMm-o.marginMm||y>src.heightMm-o.marginMm){
                int v=i/2;
                bad=v<count?v:2*count-1-v;
                break;
            }
        }
        if(bad<0 && StyleKit.selfIntersections(poly,1)!=0)bad=count/2;
        if(bad>=0){
            // A tiny mask notch or offset outside the Snip invalidates only
            // the few samples touching it. Do not discard unrelated detail.
            int left=flushSegment(points,widths,start,bad,src,o,layer,depth+1);
            int right=flushSegment(points,widths,start+bad+1,count-bad-1,src,o,layer,depth+1);
            return left+right;
        }
        layer.loops.add(poly);return 1;
    }

    private static int rings(StyleKit.Source src,float[] dark,StyleKit.Layer layer,Options o){
        float cx=src.widthMm/2f,cy=src.heightMm/2f;
        float max=(float)Math.hypot(cx,cy),maxWidth=(o.pitchMm-o.gapMm)/1.15f;
        if(maxWidth<o.minMm)throw new IllegalArgumentException("Pitch too narrow for ring width and weed gap");
        int runs=0;Buffer b=new Buffer();
        for(float radius=o.pitchMm;radius<max;radius+=o.pitchMm){
            StyleKit.checkCancelled();b.reset();
            int n=Math.max(48,(int)Math.ceil(2*Math.PI*radius/o.stepMm));
            for(int k=0;k<=n;k++){if(((k)&63)==0)CancellationCheckpoint.check();
                if((k&127)==0)StyleKit.checkCancelled();
                float theta=(float)(2*Math.PI*k/n);
                float x=cx+radius*(float)Math.cos(theta),y=cy+radius*(float)Math.sin(theta);
                float value=src.validAt(x,y)?sample(dark,src,x,y):0;
                float width=o.minMm+(maxWidth-o.minMm)*(float)Math.pow(value,o.widthGamma);
                float nx=(float)Math.cos(theta),ny=(float)Math.sin(theta);
                boolean ok=insideRibbon(src,x,y,nx,ny,width*.5f,o.marginMm);
                if(o.dashHighlights && value<.09f)ok=false;
                if(ok)b.add(x,y,width*.5f);
                else {runs+=flush(b,src,o,layer);b.reset();}
                if(layer.loops.size()>=MAX_PIECES)throw new IllegalArgumentException(
                        "Banknote exceeds 5000 cut pieces; reduce Detail or turn off highlight dashes");
            }
            if(b.n==n+1 && tryClosedRing(b,src,o,layer))runs++;
            else runs+=flush(b,src,o,layer);
            if(layer.loops.size()>=MAX_PIECES)throw new IllegalArgumentException(
                        "Banknote exceeds 5000 cut pieces; reduce Detail or turn off highlight dashes");
        }
        return runs;
    }

    /** Preserve a complete annular ribbon as outer/inner even-odd contours. */
    private static boolean tryClosedRing(Buffer b,StyleKit.Source src,Options o,StyleKit.Layer layer){
        if(b.n<5 || layer.loops.size()+2>MAX_PIECES)return false;
        int n=b.n-1; // Last angular sample repeats the first.
        float[] xy=Arrays.copyOf(b.xy,n*2),half=Arrays.copyOf(b.hw,n);
        ArrayList<float[]> loops=StyleKit.ribbonClosed(xy,half,1.15f);
        if(loops==null||loops.size()!=2)return false;
        for(float[] poly:loops){CancellationCheckpoint.check();
            if(StyleKit.selfIntersections(poly,1)!=0)return false;
            for(int i=0;i<poly.length;i+=2){if(((i)&63)==0)CancellationCheckpoint.check();
                if(poly[i]<=o.marginMm||poly[i+1]<=o.marginMm ||
                        poly[i]>=src.widthMm-o.marginMm||poly[i+1]>=src.heightMm-o.marginMm ||
                        !src.validAt(poly[i],poly[i+1]))return false;
            }
        }
        layer.loops.addAll(loops);return true;
    }


    /** Physical plan for highlight dashes; lengths in millimetres along the traversal. */
    static final class DashPlan {
        /** Pieces per sheet at which new dashes stop and the sheet is regenerated with
         *  longer dashes; keeps every colour inside Cricut's 5,000-shape SVG limit. */
        static final int PIECE_CAP=4400;
        /** Extra end-to-end clearance over the weed gap: flat dash ends on a bending
         *  centreline can bring corners slightly closer than the centre spacing. */
        static final float GAP_MARGIN_MM=.15f;
        static final class Stats { boolean overflow;int dashes; }
        final float minimumMm,minDashMm,minGapMm,dutyGamma;
        final Stats stats;
        DashPlan(float minimumMm,float minDashMm,float minGapMm,float dutyGamma,Stats stats){
            this.minimumMm=minimumMm;this.minDashMm=minDashMm;this.minGapMm=minGapMm;
            this.dutyGamma=dutyGamma;this.stats=stats;
        }
        DashPlan longer(float factor){
            return new DashPlan(minimumMm,minDashMm*factor,minGapMm,dutyGamma,new Stats());
        }
    }

    /**
     * Run-length-limited 1-D sigma-delta (pulse-density) modulation of one engraving line.
     *
     * The caller classifies every sample as HARD (outside the artwork, margin or Snip:
     * nothing may be cut across it), FIXED (a continuous ribbon at its own legal width) or
     * SOFT (a tone the ribbon should only partly cover). A SOFT sample is either cut at its
     * legal width or left as paper; its duty d (0..1, shaped as d^gamma) is the share of its
     * length that must look covered, so the running error e = sum(d) - sum(cut steps) is
     * carried along the line and a dash starts once e reaches half a minimum dash.
     * <ul>
     * <li>a stand-alone dash is at least minDash long (k samples cover k-1 steps, so the
     *     error is credited one step when a dash ends);</li>
     * <li>any paper gap between two pieces of the line is at least minGap long; a shorter
     *     gap in front of a FIXED ribbon is filled instead, merging into that ribbon;</li>
     * <li>hysteresis: a FIXED ribbon is never split, and its continuation stays attached
     *     while the diffused error says it is covered;</li>
     * <li>a dash cut short by a HARD sample before reaching minDash is dropped.</li>
     * </ul>
     * |e| is clamped to a few dash lengths, so the long-run cut fraction equals the mean duty
     * up to a bounded phase error per line. Deterministic: the only state is the line's
     * start phase (golden-ratio stagger, so neighbouring lines do not band).
     */
    static final class DashModulator {
        interface Sink{void emit(ArrayList<float[]> run);}
        private final float thr,gamma;
        private final int minOn,gapSteps;
        private final Sink sink;
        private final DashPlan plan;
        private final java.util.List<?> pieces;
        private ArrayList<float[]> run=new ArrayList<>();
        private final ArrayList<float[]> gap=new ArrayList<>();
        private boolean on,pure;
        private int onCount,index,lastOn=Integer.MIN_VALUE/4;
        private float e;
        DashModulator(DashPlan plan,float stepMm,int line,java.util.List<?> pieces,Sink sink){
            this.plan=plan;this.sink=sink;this.pieces=pieces;
            float step=Math.max(.05f,stepMm);
            gamma=plan.dutyGamma;
            minOn=1+Math.max(1,(int)Math.ceil(plan.minDashMm/step-1e-4f));
            gapSteps=Math.max(1,(int)Math.ceil(plan.minGapMm/step-1e-4f));
            thr=Math.max(.5f,minOn*.5f);
            double phase=line*.6180339887+.3819660113;
            phase-=Math.floor(phase);
            e=(float)(thr*phase);
        }
        void fixed(float[] sample){
            int i=index++;
            if(!run.isEmpty()&&!on)run.addAll(gap); // paper gap shorter than minGap: fill it
            gap.clear();
            run.add(sample);on=true;pure=false;onCount=0;lastOn=i;e=0f;
        }
        /** half: the legal (>= minimum) half-width this sample is cut at if it is covered. */
        void soft(float x,float y,float duty,float half){
            int i=index++;
            float d=duty>0f?(float)Math.pow(Math.min(1f,duty),gamma):0f; // NaN and <=0: paper
            e+=d;
            float[] sample={x,y,half};
            if(on){
                boolean mayStop=!pure||onCount>=minOn;
                if(mayStop&&e<.5f){
                    on=false;if(pure)e+=1f;
                    gap.clear();gap.add(sample);
                    if(i-lastOn>=gapSteps)flushRun();
                }else{run.add(sample);onCount++;e-=1f;lastOn=i;}
            }else{
                if(!run.isEmpty())gap.add(sample);
                boolean gapOk=i-lastOn>=gapSteps;
                if(gapOk&&!run.isEmpty())flushRun();
                if(gapOk&&e>=thr&&d>0f&&!plan.stats.overflow){
                    if(pieces.size()>=DashPlan.PIECE_CAP)plan.stats.overflow=true;
                    else{on=true;pure=true;onCount=1;run.add(sample);e-=1f;lastOn=i;gap.clear();}
                }
            }
            e=Math.max(-minOn,Math.min(minOn+gapSteps,e));
        }
        void hard(){
            index++;
            if(!run.isEmpty())flushRun();
            run=new ArrayList<>();gap.clear();on=false;pure=false;onCount=0;lastOn=Integer.MIN_VALUE/4;
        }
        void end(){hard();}
        private void flushRun(){
            if(run.size()>=2&&!(pure&&onCount<minOn)){
                if(pure)plan.stats.dashes++;
                sink.emit(run);
            }
            run=new ArrayList<>();gap.clear();on=false;
        }
    }
}

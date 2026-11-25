package com.halftonestudio;

import java.util.Arrays;

/**
 * Three new photo-aware CUT styles. Geometry stays in the existing StyleKit.Result
 * model: an opaque, full-Snip base followed by independent, physical, FILLED
 * SVG/DXF color sheets. No pen strokes, Android state, random allocations in
 * the pixel loop, or external geometry dependency.
 *
 * This is deliberately a two-stage renderer: preserve tonal/feature topology
 * first, decorate the broad color regions second. A pure noise-based marbling
 * texture would discard eyes, hair, and faces. Every mask is raster-unioned on
 * the same physical grid and traced using the existing production contour path.
 */
public final class OrganicFlowPortraitEngine {
    private OrganicFlowPortraitEngine() {}
    public static final int QUILLING=0, MARBLING=1, QUILLED_MARBLING=2;
    public static final class Options {
        public int mode=QUILLING, detail=86, layers=6, smoothing=OrganicFlowCutSmoother.DEFAULT;
        public float widthMm=279.4f, minCutMm=.8f, gapMm=.5f, flow=1f;
        public int[] palette;
        public int paperArgb=0xffffffff;
        public boolean[] allowed;
        public boolean draft;
        public void validate(){
            if(mode<0||mode>2||detail<0||detail>100||smoothing<0||smoothing>100||layers<2||layers>8||
                !Float.isFinite(widthMm)||widthMm<20||widthMm>2000||
                !Float.isFinite(minCutMm)||minCutMm<.1f||minCutMm>4||
                !Float.isFinite(gapMm)||gapMm<.1f||gapMm>5||
                !Float.isFinite(flow)||flow<.25f||flow>2.5f||
                palette!=null&&palette.length!=layers)
                throw new IllegalArgumentException("Invalid organic flow settings");
        }
    }
    private static final float TAU=(float)(2*Math.PI);
    private static float clamp(float a,float b,float x){return Math.max(a,Math.min(b,x));}
    private static float fract(float x){return x-(float)Math.floor(x);}
    private static long mix(long v){v=(v^(v>>>30))*0xbf58476d1ce4e5b9L;
        v=(v^(v>>>27))*0x94d049bb133111ebL;return v^(v>>>31);}
    private static float jitter(int a,int b,int salt){
        return ((mix(((long)a<<32)^(b&0xffffffffL)^salt)>>>40)*0x1.0p-24f)-.5f;
    }
    private static float at(float[] f,StyleKit.Source s,float x,float y){
        return f[s.pixelIndex(x,y)];
    }
    private static int[] palette(Options o){
        if(o.palette!=null)return o.palette.clone();
        int[] presets={0xff211c26,0xff483d58,0xff70647a,0xffa3808b,
            0xffcda4a0,0xffe3c2a8,0xffe9d4bd,0xfff9ece1};
        int[] c=new int[o.layers];for(int i=0;i<c.length;i++)
            c[i]=presets[Math.round(i*7f/Math.max(1,c.length-1))];
        return c;
    }
    /** A photo-informed balanced quantile ramp; no swatches are silently reordered. */
    static final class ToneMap {
        final int[] rank;
        final PhotoSwatchAssociation association;
        final float[] cdf=new float[256];
        ToneMap(StyleKit.Source s,int[] colors){
            association=new PhotoSwatchAssociation(colors,colors.length,null,s,PhotoSwatchAssociation.REGIONS);
            Integer[] indices=new Integer[colors.length];
            for(int i=0;i<indices.length;i++)indices[i]=i;
            Arrays.sort(indices,(a,b)->{
                int v=Double.compare(PerceptualLayerColorEngine.light(colors[a]),PerceptualLayerColorEngine.light(colors[b]));
                return v==0?Integer.compare(a,b):v;
            });
            rank=new int[indices.length];for(int i=0;i<rank.length;i++)rank[i]=indices[i];
            int[] hist=new int[256];int total=0;
            for(int i=0;i<s.lum.length;i++)if(s.valid[i]){
                hist[StyleKit.clampI(Math.round(s.lum[i]*255),0,255)]++;total++;
            }
            int acc=0;
            for(int i=0;i<256;i++){
                acc+=hist[i];
                cdf[i]=total==0?i/255f:Math.max(0,(acc-hist[i]*.5f)/(float)total);
            }
        }
        int slot(float tone){return rank[rankFor(tone)];}
        int slot(float tone,int rgb){
            // Preserve the core of every tonal band. Unrestricted nearest-color
            // matching could consume an entire neighboring band (including
            // highlights), leaving a registered physical sheet empty.
            float position=rankPosition(tone);
            int band=Math.min(rank.length-1,(int)position);
            int original=rank[band];
            float fraction=position-band;
            int neighbor=fraction<.15f?band-1:fraction>.85f?band+1:-1;
            if(neighbor<0||neighbor>=rank.length)return original;
            int candidate=association.chooseNearRankCached(rgb,band);
            return candidate==rank[neighbor]?candidate:original;
        }
        private int rankFor(float tone){
            return Math.min(rank.length-1,(int)rankPosition(tone));
        }
        private float rankPosition(float tone){
            tone=clamp(0,1,tone);
            float normalized=.35f*tone+.65f*cdf[Math.min(255,Math.round(tone*255))];
            return normalized*rank.length;
        }
    }
    /** Immutable one-image analysis cache. The full normalized luminance and
     * Snip mask are compared, not just object identity or a lossy checksum:
     * same photo/width/colors => reusable; changed crop, lighting or size =>
     * recompute. Cached arrays are read-only once published to other renders.
     * The one-entry bound prevents unbounded photo-history memory growth.
     */
    private static final class Analysis {
        final int w,h,widthBits;
        final float[] luminance,low,edges;
        final boolean[] valid;
        Analysis(StyleKit.Source s,float[] low,float[] edges){
            w=s.w;h=s.h;widthBits=Float.floatToIntBits(s.widthMm);
            luminance=s.lum.clone();valid=s.valid.clone();
            this.low=low;this.edges=edges;
        }
        boolean matches(StyleKit.Source s){
            return w==s.w&&h==s.h&&widthBits==Float.floatToIntBits(s.widthMm)&&
                Arrays.equals(luminance,s.lum)&&Arrays.equals(valid,s.valid);
        }
    }
    private static volatile Analysis cachedAnalysis;
    private static final java.util.concurrent.atomic.AtomicInteger ANALYSIS_HITS=
        new java.util.concurrent.atomic.AtomicInteger();
    static void clearAnalysisCacheForTest(){cachedAnalysis=null;ANALYSIS_HITS.set(0);}
    static int analysisCacheHitsForTest(){return ANALYSIS_HITS.get();}
    private static Analysis analyzePhoto(StyleKit.Source s){
        StyleKit.checkCancelled();
        Analysis cached=cachedAnalysis;
        if(cached!=null&&cached.matches(s)){
            ANALYSIS_HITS.incrementAndGet();return cached;
        }
        final float[] low=StyleKit.blur(s.lum,s.w,s.h,Math.max(.75f,1.25f/s.mmPerPx));
        final float[] edges=StyleKit.edgeMap(StyleKit.blur(s.lum,s.w,s.h,
            Math.max(.55f,.52f/s.mmPerPx)),s.w,s.h);
        // At very high resolution, avoid retaining a multi-megabyte secondary
        // snapshot in addition to the active export/render working set.
        if((long)s.w*s.h<=1_100_000L){
            cached=new Analysis(s,low,edges);
            cachedAnalysis=cached;return cached;
        }
        return new Analysis(s,low,edges);
    }

    /** Millimetre-domain local high-pass so small photographic features survive quantization. */
    static float enhancedTone(StyleKit.Source s,float[] low,float x,float y){
        float raw=s.lumAt(x,y);
        return clamp(0f,1f,raw+.56f*(raw-at(low,s,x,y)));
    }
    /** A deterministic locally vortexed and combed inverse sampling map. */
    private static void warped(StyleKit.Source s,float x,float y,float pitch,float flow,float protection,float[] result){
        float scale=Math.max(11f,pitch*7.8f);
        int cellX=(int)Math.floor(x/scale), cellY=(int)Math.floor(y/scale);
        float cx=(cellX+.5f)*scale,cy=(cellY+.5f)*scale;
        float dx=x-cx,dy=y-cy;
        float r2=dx*dx+dy*dy,local=(float)Math.exp(-r2/(scale*scale*.52f));
        float direction=((cellX+cellY)&1)==0?1f:-1f;
        float angle=direction*local*.85f*flow*(1-protection*.87f);
        float cs=(float)Math.cos(angle),sn=(float)Math.sin(angle);
        float xx=cx+dx*cs-dy*sn,yy=cy+dx*sn+dy*cs;
        float amp=Math.min(scale*.19f,3.3f)*flow*(1-protection*.89f);
        xx+=amp*(float)Math.sin(y/scale*TAU*.72f+Math.sin(x/scale*1.4f));
        yy+=amp*.70f*(float)Math.sin(x/scale*TAU*.63f+Math.cos(y/scale*1.2f));
        result[0]=clamp(0,s.widthMm-.0001f,xx);result[1]=clamp(0,s.heightMm-.0001f,yy);
    }
    /** Flattened paper-quill spirals with an image-guided tangent in edge areas. */
    static boolean ribbon(float x,float y,float pitch,float mm,float min,float gap,
                          float flow,float gx,float gy,float edge){
        float cell=Math.max(pitch*4.7f,9f),cx=(float)Math.floor(x/cell),cy=(float)Math.floor(y/cell);
        int ix=(int)cx,iy=(int)cy;
        float centerX=(cx+.5f+jitter(ix,iy,0x123417)*.78f)*cell;
        float centerY=(cy+.5f+jitter(ix,iy,0x746515)*.78f)*cell;
        float dx=x-centerX,dy=y-centerY;
        // Each coil has its own axis and ellipticity, rather than a repeated
        // lattice of identical circular stamps.
        float spin=jitter(ix,iy,0x275113)*1.65f;
        float sn=(float)Math.sin(spin),cs=(float)Math.cos(spin);
        float ux=dx*cs-dy*sn,uy=dx*sn+dy*cs;
        float ellipse=.80f+jitter(ix,iy,0x701305)*.32f;
        float rad=(float)Math.hypot(ux*ellipse,uy/ellipse);
        // The center is a filled coil core, never a sub-minimum isolated dot.
        if(rad<Math.max(min+mm*2f,pitch*.58f))return true;
        float theta=(float)Math.atan2(uy,ux);
        float spiral=rad/pitch+theta/(TAU)*(.62f+.33f*flow);
        // Neighboring coils share a softly changing, image-oriented contour field.
        float gm=(float)Math.hypot(gx,gy);
        float tangent=gm>.0001f?(-gy*dx+gx*dy)/(gm*pitch):spiral;
        // A weak/noisy local gradient must NOT scramble the entire spiral
        // phase into random specks. Blend only near meaningful photo edges.
        float meaningful=clamp(0,1,(gm-.013f)/.055f);
        float mix=clamp(0,.68f,edge*.54f*meaningful);
        float phase=spiral*(1-mix)+tangent*mix;
        // Safe widths in a sampled grid. If the physical budget cannot fit
        // a ribbon and a weed gap, make this region solid instead of a sliver.
        float safeWidth=min+2.2f*mm, safeGap=gap+2.2f*mm;
        if(pitch<=safeWidth+safeGap+.08f)return true;
        float coverage=clamp(safeWidth/pitch,1-safeGap/pitch,
            .80f-(edge*.06f));
        return fract(phase)<coverage;
    }
    /**
     * Geometry constants of a single paper-quill cell do not depend on the
     * sampled pixel. Cache the four deterministic hashes and two trig values
     * once per cell instead of evaluating them for every occupied pixel.
     * Per-render immutable data: safe for concurrent/cancelled render jobs.
     */
    static final class RibbonField {
        final float cell;
        final int nx,ny;
        final float[] centerX,centerY,sin,cos,ellipse;
        RibbonField(float pitch,float widthMm,float heightMm){
            cell=Math.max(pitch*4.7f,9f);
            nx=Math.max(1,1+(int)Math.floor(widthMm/cell));
            ny=Math.max(1,1+(int)Math.floor(heightMm/cell));
            int length=Math.multiplyExact(nx,ny);
            centerX=new float[length];centerY=new float[length];
            sin=new float[length];cos=new float[length];ellipse=new float[length];
            for(int y=0;y<ny;y++){
                if((y&31)==0)StyleKit.checkCancelled();
                for(int x=0;x<nx;x++){
                    int i=y*nx+x;
                    // Keep the original float evaluation order for parity.
                    centerX[i]=((float)x+.5f+jitter(x,y,0x123417)*.78f)*cell;
                    centerY[i]=((float)y+.5f+jitter(x,y,0x746515)*.78f)*cell;
                    float spin=jitter(x,y,0x275113)*1.65f;
                    sin[i]=(float)Math.sin(spin);cos[i]=(float)Math.cos(spin);
                    ellipse[i]=.80f+jitter(x,y,0x701305)*.32f;
                }
            }
        }
        boolean ribbon(float x,float y,float pitch,float mm,float min,float gap,
                       float flow,float gx,float gy,float edge){
            int ix=(int)Math.floor(x/cell),iy=(int)Math.floor(y/cell);
            if(ix<0||iy<0||ix>=nx||iy>=ny)
                return OrganicFlowPortraitEngine.ribbon(x,y,pitch,mm,min,gap,flow,gx,gy,edge);
            int index=iy*nx+ix;
            float dx=x-centerX[index],dy=y-centerY[index];
            float sn=sin[index],cs=cos[index];
            float ux=dx*cs-dy*sn,uy=dx*sn+dy*cs;
            float elliptical=ellipse[index];
            float rad=(float)Math.hypot(ux*elliptical,uy/elliptical);
            if(rad<Math.max(min+mm*2f,pitch*.58f))return true;
            float theta=(float)Math.atan2(uy,ux);
            float spiral=rad/pitch+theta/(TAU)*(.62f+.33f*flow);
            float gm=(float)Math.hypot(gx,gy);
            float tangent=gm>.0001f?(-gy*dx+gx*dy)/(gm*pitch):spiral;
            float meaningful=clamp(0,1,(gm-.013f)/.055f);
            float mix=clamp(0,.68f,edge*.54f*meaningful);
            float phase=spiral*(1-mix)+tangent*mix;
            float safeWidth=min+2.2f*mm,safeGap=gap+2.2f*mm;
            if(pitch<=safeWidth+safeGap+.08f)return true;
            float coverage=clamp(safeWidth/pitch,1-safeGap/pitch,.80f-(edge*.06f));
            return fract(phase)<coverage;
        }
    }
    /** Disk morphology in the same millimetre grid as the final vector paths.
     * Close microscopic gaps first, then open fragile strands. This is not a
     * substitute for the independent final-sheet geometric inspector.
     */
    static void conditionMasks(PatternPortraitTrioEngine.Surface surface,float min,float gap){
        conditionMasks(surface,min,gap,false);
    }
    // Square kernels retain orthogonal digital camouflage geometry.
    static void conditionMasks(PatternPortraitTrioEngine.Surface surface,float min,float gap,boolean orthogonal){
        final int w=surface.w,h=surface.h;
        final int close=Math.max(1,(int)Math.ceil((gap*.50f+.07f)/surface.mm));
        final int open=Math.max(1,(int)Math.ceil((min*.50f+.08f)/surface.mm));
        // Two reusable buffers for all color sheets, and one disk kernel per
        // radius. The four exact binary morphology passes are unchanged.
        // No shared/static caches: concurrent draft/final renders stay isolated.
        final DiskKernel closeKernel=new DiskKernel(close,orthogonal),openKernel=new DiskKernel(open,orthogonal);
        final boolean[] a=new boolean[w*h],b=new boolean[w*h];
        for(int l=1;l<surface.ink.length;l++){
            StyleKit.checkCancelled();
            final boolean[] mask=surface.ink[l];
            morphInto(mask,a,w,h,closeKernel,true);
            morphInto(a,b,w,h,closeKernel,false);
            morphInto(b,a,w,h,openKernel,false);
            morphInto(a,b,w,h,openKernel,true);
            for(int i=0;i<mask.length;i++)mask[i]=b[i]&&surface.valid[i];
        }
    }
    private static final class DiskKernel {
        final int[] dx,dy;
        DiskKernel(int radius,boolean orthogonal){
            int count=0;
            for(int y=-radius;y<=radius;y++)for(int x=-radius;x<=radius;x++)
                if(orthogonal||x*x+y*y<=radius*radius)count++;
            dx=new int[count];dy=new int[count];int n=0;
            for(int y=-radius;y<=radius;y++)for(int x=-radius;x<=radius;x++)
                if(orthogonal||x*x+y*y<=radius*radius){dx[n]=x;dy[n]=y;n++;}
        }
    }
    private static void morphInto(boolean[] input,boolean[] output,int w,int h,
                                  DiskKernel kernel,boolean dilation){
        for(int y=0;y<h;y++){
            if((y&31)==0)StyleKit.checkCancelled();
            for(int x=0;x<w;x++){
                boolean value=!dilation;
                for(int k=0;k<kernel.dx.length;k++){
                    int xx=x+kernel.dx[k], yy=y+kernel.dy[k];
                    boolean inside=xx>=0&&xx<w&&yy>=0&&yy<h&&input[yy*w+xx];
                    if(dilation&&inside){value=true;break;}
                    if(!dilation&&!inside){value=false;break;}
                }
                output[y*w+x]=value;
            }
        }
    }
    /**
     * Marching-square tracing still preserves raster stair-steps. For Organic Flow
     * the filled regions are intentionally broad and decorative, so we can round
     * those micro-steps in millimetres after tracing, then validate the result
     * with the same geometry preflight used before export.
     */
    static StyleKit.Result smoothCutContours(StyleKit.Result raw,float minCut,float gap,float mm){
        final float maxShift=Math.min(.30f,Math.max(.10f,Math.min(minCut,gap)*.42f+mm*.28f));
        final float simplify=Math.min(.22f,Math.max(.08f,mm*.62f));
        for(int li=0;li<raw.layers.size();li++){
            StyleKit.Layer original=raw.layers.get(li);
            if(original.loops.isEmpty())continue;
            StyleKit.Layer candidate=new StyleKit.Layer(original.name,original.color);
            for(float[] loop:original.loops){
                StyleKit.checkCancelled();
                candidate.loops.add(smoothLoop(loop,maxShift,simplify,minCut,mm));
            }
            if(validLayer(candidate,raw.widthMm,raw.heightMm,minCut,gap)){
                original.loops.clear();
                original.loops.addAll(candidate.loops);
            }
        }
        raw.summary=raw.summary+" · smoothed contour repair";
        return raw;
    }
    private static boolean validLayer(StyleKit.Layer layer,float widthMm,float heightMm,float minCut,float gap){
        try{
            StyleKit.Result page=new StyleKit.Result(widthMm,heightMm);
            StyleKit.Layer copy=new StyleKit.Layer(layer.name,layer.color);
            copy.loops.addAll(layer.loops);
            page.layers.add(copy);
            return StyleCutGeometryPreflight.inspect(page,minCut,gap,8).isEmpty();
        }catch(RuntimeException error){
            return false;
        }
    }
    private static float[] smoothLoop(float[] loop,float maxShift,float simplify,float minCut,float mm){
        if(loop==null||loop.length<8||(loop.length&1)!=0)return loop;
        final int n=loop.length/2;
        if(n<5)return loop;
        double area0=StyleKit.signedArea(loop);
        if(!Double.isFinite(area0)||Math.abs(area0)<1e-7)return loop;
        float[] current=loop.clone();
        int passes=n>96?2:1;
        for(int pass=0;pass<passes;pass++){
            float[] next=new float[current.length];
            for(int i=0;i<n;i++){
                int prev=(i+n-1)%n,nextIndex=(i+1)%n;
                float px=current[prev*2],py=current[prev*2+1];
                float cx=current[i*2],cy=current[i*2+1];
                float nx=current[nextIndex*2],ny=current[nextIndex*2+1];
                float tx=.25f*px+.5f*cx+.25f*nx;
                float ty=.25f*py+.5f*cy+.25f*ny;
                float dx=tx-cx,dy=ty-cy;
                float len=(float)Math.hypot(dx,dy);
                float limit=maxShift*(pass==0?1f:.72f);
                if(len>limit&&len>1e-6f){
                    float scale=limit/len;dx*=scale;dy*=scale;
                }
                next[i*2]=cx+dx;next[i*2+1]=cy+dy;
            }
            current=next;
        }
        current=rescaleArea(loop,current);
        float[] simplified=SolidSnipContourRepair.simplifyClosed(current,simplify);
        if(simplified.length>=8)current=simplified;
        current=StyleKit.removeCollinear(current,Math.max(.010f,Math.min(simplify*.5f,mm*.16f)));
        double area1=StyleKit.signedArea(current);
        if(current.length<6||!Double.isFinite(area1)||area0*area1<=0)return loop;
        double ratio=Math.abs(area1/area0);
        if(ratio<.972||ratio>1.028)return loop;
        float minimumArea=Math.max(mm*mm*2.6f,minCut*minCut*.22f);
        if(Math.abs(area1)<minimumArea)return loop;
        if(StyleKit.selfIntersections(current,1)>0)return loop;
        return current;
    }
    private static float[] rescaleArea(float[] original,float[] candidate){
        double area0=Math.abs(StyleKit.signedArea(original));
        double area1=Math.abs(StyleKit.signedArea(candidate));
        if(!Double.isFinite(area0)||!Double.isFinite(area1)||area0<1e-8||area1<1e-8)return candidate;
        double scale=Math.sqrt(area0/area1);
        if(!(scale>.965&&scale<1.035))return candidate;
        float cx=0,cy=0;int n=candidate.length/2;
        for(int i=0;i<n;i++){cx+=candidate[i*2];cy+=candidate[i*2+1];}
        cx/=n;cy/=n;
        float[] out=candidate.clone();
        for(int i=0;i<n;i++){
            out[i*2]=(float)(cx+(out[i*2]-cx)*scale);
            out[i*2+1]=(float)(cy+(out[i*2+1]-cy)*scale);
        }
        return out;
    }
    public static StyleKit.Result generate(int[] pixels,int width,int height,Options o){
        if(o==null)throw new IllegalArgumentException("Missing settings");
        o.validate();
        final StyleKit.Source s=new StyleKit.Source(pixels,width,height,o.allowed,o.widthMm);
        if(!s.anyValid())throw new IllegalArgumentException("No subject remains inside Snip");
        s.autoLevels(.015f,.985f);
        final int[] colors=palette(o);
        final PatternPortraitTrioEngine.Surface surface=
            new PatternPortraitTrioEngine.Surface(s,o.layers,o.minCutMm,o.draft);
        final float mm=surface.mm;
        final Analysis analysis=analyzePhoto(s);
        final float[] low=analysis.low,edges=analysis.edges;
        final ToneMap tones=new ToneMap(s,colors);
        // Run at a detail-dependent and physically constrained raster pitch.
        final float detail=o.detail/100f;
        final float fine=(3.65f-1.85f*detail)*(float)Math.sqrt(s.widthMm/279.4f);
        // Smoothing is a vector-boundary operation, not a second Detail slider.
        // The former 44% pitch expansion at 100% destroyed quill/face features.
        final float ribbonPitch=Math.max(o.minCutMm+o.gapMm+mm*4.7f+.16f,fine);
        final RibbonField ribbonField=o.mode==MARBLING?null:
            new RibbonField(ribbonPitch,s.widthMm,s.heightMm);
        // Draft frames are visual-only and cannot be exported. Avoid building
        // saliency/curve-validation state for a transient drag preview.
        final float[] photoImportance=o.draft?null:new float[surface.w*surface.h];
        final float[] warpPoint=new float[2];
        int populated=0;
        for(int y=0;y<surface.h;y++){
            if((y&15)==0)StyleKit.checkCancelled();
            for(int x=0;x<surface.w;x++){
                final int idx=y*surface.w+x;
                if(!surface.valid[idx])continue;
                final float xx=(x+.5f)*mm, yy=(y+.5f)*mm;
                surface.ink[0][idx]=true; // always an actual closed, structural Snip sheet
                // One photo-only lookup per pixel, reused by saliency,
                // edge protection and ribbon preservation. Evaluation order
                // matches enhancedTone() exactly for geometry parity.
                final float lowTone=at(low,s,xx,yy);
                final float rawTone=s.lumAt(xx,yy);
                final float original=clamp(0f,1f,rawTone+.56f*(rawTone-lowTone));
                final float edge=at(edges,s,xx,yy);
                // Source-photo saliency BEFORE the decorative warp. The near-edge
                // region is given a preservation budget independently of slider.
                final float localContrast=Math.abs(original-lowTone);
                if(!o.draft)photoImportance[idx]=clamp(0f,1f,Math.max(edge*1.05f,
                    localContrast*5.0f));
                float tone=original;
                float gx=0,gy=0;
                if(o.mode!=MARBLING){
                    float delta=Math.max(s.mmPerPx,.42f);
                    gx=(s.lumAt(xx+delta,yy)-s.lumAt(xx-delta,yy))/(2f*delta);
                    gy=(s.lumAt(xx,yy+delta)-s.lumAt(xx,yy-delta))/(2f*delta);
                }
                if(o.mode!=QUILLING){
                    float protectedEdge=clamp(0,1,edge*1.85f+
                        Math.abs(original-lowTone)*1.5f);
                    warped(s,xx,yy,ribbonPitch,o.flow,protectedEdge,warpPoint);
                    float shifted=s.validAt(warpPoint[0],warpPoint[1])?
                        enhancedTone(s,low,warpPoint[0],warpPoint[1]):original;
                    float change=(o.mode==MARBLING?.62f:.46f)*(1-protectedEdge);
                    // Tone displacement is bounded; strong feature edges use
                    // the ORIGINAL image, not the warped outside-Snip pixels.
                    tone=clamp(0,1,original*(1-change)+shifted*change);
                    float phase=(xx+o.flow*2f*(float)Math.sin(yy/(ribbonPitch*4f)))/
                        (ribbonPitch*2.7f)+.55f*(float)Math.sin(yy/(ribbonPitch*2.1f));
                    tone=clamp(0,1,tone+(.065f+detail*.025f)*
                        (float)Math.sin(TAU*phase)*(1-protectedEdge));
                }
                int slot=tones.slot(tone,s.colorAt(xx,yy));
                if(slot==0)continue;
                boolean cover=true;
                if(o.mode!=MARBLING){
                    // In high-detail eyes, lips, and silhouette transitions,
                    // prioritize the original color island over decoration.
                    boolean preserve=edge>.72f||Math.abs(original-lowTone)>.17f;
                    cover=preserve||ribbonField.ribbon(xx,yy,ribbonPitch,mm,o.minCutMm,o.gapMm,
                        o.flow,gx,gy,edge);
                }
                if(cover){surface.ink[slot][idx]=true;populated++;}
            }
        }
        // Physical mask conditioning runs BEFORE preview/export vectorization, so
        // both consumers receive exactly the same closed polygons. Unlike an
        // export-only repair this cannot surprise the user on the cutting mat.
        conditionMasks(surface,o.minCutMm,o.gapMm);
        // Feature-aware cleanup: do not delete eyebrows, eye highlights, nostril
        // openings, lips and other small photographic structures just because
        // their islands are below an arbitrary global area threshold.
        final float[] protection=o.draft?null:OrganicFlowCutSmoother.expandImportance(
            photoImportance,surface.valid,surface.w,surface.h);
        if(!o.draft)OrganicFlowCutSmoother.discardMicroIslands(
            surface,o.smoothing,o.minCutMm,protection);
        if(populated==0)throw new IllegalArgumentException(
            "No printable detail survives at this size and minimum cut width");
        final String label=o.mode==QUILLING?"Quilling Ribbons":
            o.mode==MARBLING?"Marbled Ink":"Quilled Marbling";
        StyleKit.Result out=surface.finish(colors,"organic-flow",populated,o.minCutMm);
        // The project/export pipeline refuses a draft result; expensive
        // curve validation runs only once for the final settled frame.
        if(!o.draft)out=OrganicFlowCutSmoother.contour(out,surface,o.minCutMm,o.gapMm,
            o.smoothing,protection);
        final String curveStatus=o.draft?"Draft preview · not cut-qualified":
            o.smoothing==0?"Smoothing off":out.summary;
        if(out.layers.size()<2)throw new IllegalArgumentException(
            "Artwork too small for detail at this cut width; reduce layers or enlarge artwork");
        out.paperArgb=o.paperArgb;
        out.summary=label+" · solid L1 Snip + "+(o.layers-1)+
            " image-aware color sheets · "+surface.w+"x"+surface.h+
            " sampled cut grid · "+String.format(java.util.Locale.US,"%.2f",ribbonPitch)+
            " mm safe ribbon pitch · smoothing "+o.smoothing+"% · "+curveStatus;
        return out;
    }
}

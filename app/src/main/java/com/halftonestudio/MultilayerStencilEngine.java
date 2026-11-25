package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Pure-Java tonal stencil separation for 2..8 physical layers.
 *
 * Each pixel belongs to one visible color; physical underlays can overlap across layers.
 * Registration marks are emitted with
 * shared physical coordinates. Reusable stencil sheets (bridgeMode=true) and
 * individually cut adhesive-vinyl layers (bridgeMode=false) have distinct rules.
 *
 * The output consists of cut regions, not photos or CSS clip masks. SVG pixels
 * are converted to physical millimeters, and adjacent cells are run-length
 * merged horizontally to avoid per-pixel paths. Regions are deliberately kept
 * disjoint; no same-layer overlapping/nested contours are introduced.
 *
 * Memory/time are O(width*height) plus SVG output. Call off the UI thread.
 */
public final class MultilayerStencilEngine {
    private MultilayerStencilEngine() { }
    private static volatile SmoothCache smoothCache;
    private static final java.util.concurrent.atomic.AtomicInteger smoothMisses=new java.util.concurrent.atomic.AtomicInteger();
    private static final class SmoothCache {
        final int width,height,radius;
        final float[] source,smoothed;
        final boolean[] valid;
        SmoothCache(float[] source,boolean[] valid,int w,int h,int radius,float[] smoothed){
            this.source=source.clone();this.valid=valid.clone();this.width=w;this.height=h;
            this.radius=radius;this.smoothed=smoothed;
        }
    }
    /** Cache only exact inputs. Photo changes, crop, background and inset affect
     * valid/luminance, so relying on a photo identity or a hash would be unsafe.
     */
    private static float[] cachedBilateral(float[] lum,boolean[] valid,int w,int h,int radius){
        SmoothCache entry=smoothCache;
        if(entry!=null&&entry.width==w&&entry.height==h&&entry.radius==radius
                &&Arrays.equals(entry.source,lum)&&Arrays.equals(entry.valid,valid))return entry.smoothed;
        float[] computed=bilateral(lum,valid,w,h,radius);
        if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Cancelled smoothing");
        smoothCache=new SmoothCache(lum,valid,w,h,radius,computed);
        smoothMisses.incrementAndGet();
        return computed;
    }
    static int smoothingComputationsForTest(){return smoothMisses.get();}
    static void clearSmoothingCacheForTest(){smoothCache=null;smoothMisses.set(0);}

    public static final int MIN_LAYERS = 2, MAX_LAYERS = 8;
    public static final int MAX_PIXELS = 1_000_000;
    public static final class Options {
        public int layerCount = 4;
        public int blurRadius = 2; // 0..8, edge-preserving bilateral filter
        public float edgeStrength = 0.65f; // 0..1
        public float edgeThreshold = 0.12f; // normalized gradient
        public int minimumIslandPixels = 7; // backwards-compatible floor for old projects
        /** Physical cleanup area, independent of raster preview resolution (mm squared). */
        public float minimumIslandAreaMm2 = .10f;
        /** Automatic cut-geometry trapping beneath upper opaque vinyl (mm). */
        public float underlayTrapMm = .30f;
        /** Contour corner easing, capped by the local raster cell size (mm). */
        public float curveRadiusMm = .28f;
        /** Only when all requested physical swatches were explicitly selected. */
        public boolean matchVinylPalette = false;
        /** 0 = graphic/simplified; 1 = maximal photo texture. */
        public float detail=0.52f;
        public boolean sourceColorMode=false; // exact photo-driven color clustering, no arbitrary paint bands
        /** Default physical workflow for adhesive vinyl: lower layers support upper overlays. */
        public boolean stackedVinyl=true;
        /** True: remove only near-white regions connected to the photo border; false: keep the image rectangle. */
        public boolean autoWhiteBackground=true;
        /** Unadjusted pixels provide a stable subject matte when tonal image controls change brightness. */
        public int[] backgroundReference=null;
        /** -1 = auto choose broad base tone; 0..N-1 = use that source layer as the bottom-most base. */
        public int baseLayer=-1;
        public boolean bridgeMode = false; // true ONLY for reusable stencil sheets
        public int bridgeWidthPixels = 2;
        public int bridgeSearchPixels = 24;
        public float widthMm = 203.2f;
        public float heightMm = 0f; // derive from aspect ratio if unset
        public boolean registration = true;
        public int markStyle = RegistrationMarks.CROSS;
        public float markDiameterMm = 3f;
        public float registrationClearanceMm = 2f;
        public float minimumCutMm=.8f;
        public float[][] marks=null;
        /** Circle crop: automatic marks sit in the unused corners, as the editor preview draws them (ADR-0001). */
        public boolean circleCrop=false;
        public float cutInsetMm=0f,outerSpacingMm=8f;
        public int backgroundArgb = 0xffffffff;
        /** Optional physical vinyl colors, one per layer; remaining colors default to grayscale. */
        public int[] colors = null;
        /** Cosmetic vinyl hue rotation applied after photo-derived ownership/stack planning. */
        public int paletteHueDegrees=0;
        public Options copy() {
            Options c = new Options(); c.layerCount=layerCount; c.blurRadius=blurRadius;
            c.edgeStrength=edgeStrength; c.edgeThreshold=edgeThreshold;c.sourceColorMode=sourceColorMode;
            c.minimumIslandPixels=minimumIslandPixels;c.minimumIslandAreaMm2=minimumIslandAreaMm2;
            c.underlayTrapMm=underlayTrapMm;c.curveRadiusMm=curveRadiusMm;
            c.matchVinylPalette=matchVinylPalette;c.detail=detail;c.stackedVinyl=stackedVinyl;c.autoWhiteBackground=autoWhiteBackground;
            c.backgroundReference=backgroundReference==null?null:backgroundReference.clone();c.baseLayer=baseLayer;c.bridgeMode=bridgeMode;
            c.bridgeWidthPixels=bridgeWidthPixels; c.bridgeSearchPixels=bridgeSearchPixels;
            c.widthMm=widthMm; c.heightMm=heightMm; c.registration=registration;
            c.markStyle=markStyle; c.markDiameterMm=markDiameterMm;
            c.registrationClearanceMm=registrationClearanceMm;c.minimumCutMm=minimumCutMm;
            c.marks=RegistrationMarks.copyPositions(marks);c.circleCrop=circleCrop;c.cutInsetMm=cutInsetMm;c.outerSpacingMm=outerSpacingMm; c.backgroundArgb=backgroundArgb;
            c.colors=colors==null?null:colors.clone();c.paletteHueDegrees=paletteHueDegrees;return c;
        }
        public void validate(int width,int height) {
            if(width<1||height<1||(long)width*height>MAX_PIXELS)throw new IllegalArgumentException("Image exceeds 1,000,000 processing pixels; lower the final processing size");
            if(layerCount<MIN_LAYERS||layerCount>MAX_LAYERS)throw new IllegalArgumentException("Layer count must be 2..8");
            if(blurRadius<0||blurRadius>8||!Float.isFinite(edgeStrength)||edgeStrength<0||edgeStrength>1||!Float.isFinite(edgeThreshold)||edgeThreshold<0||edgeThreshold>1)throw new IllegalArgumentException("Invalid smoothing/edge controls");
            if(!Float.isFinite(detail)||detail<0||detail>1||minimumIslandPixels<0||minimumIslandPixels>10000||bridgeWidthPixels<1||bridgeWidthPixels>12||bridgeSearchPixels<1||bridgeSearchPixels>128)throw new IllegalArgumentException("Invalid island/bridge controls");
            if(!Float.isFinite(minimumIslandAreaMm2)||minimumIslandAreaMm2<0||minimumIslandAreaMm2>100||
               !Float.isFinite(underlayTrapMm)||underlayTrapMm<0||underlayTrapMm>2||
               !Float.isFinite(curveRadiusMm)||curveRadiusMm<0||curveRadiusMm>1)
                throw new IllegalArgumentException("Invalid physical cut geometry");
            if(matchVinylPalette){
                if(!sourceColorMode||colors==null||colors.length<layerCount)
                    throw new IllegalArgumentException("Select all vinyl colors before matching");
                for(int i=0;i<layerCount;i++)if(colors[i]==0)
                    throw new IllegalArgumentException("Every vinyl swatch must be selected");
            }
            if(baseLayer<-1||baseLayer>=layerCount)throw new IllegalArgumentException("Invalid base layer selection");
            if(paletteHueDegrees < -180 || paletteHueDegrees > 180)throw new IllegalArgumentException("Invalid palette hue");
            if(backgroundReference!=null&&backgroundReference.length!=(long)width*height)throw new IllegalArgumentException("Background reference dimensions differ");
            if(!Float.isFinite(widthMm)||widthMm<=0||widthMm>1200||!Float.isFinite(heightMm)||heightMm<0||heightMm>1200)throw new IllegalArgumentException("Invalid physical size");
            if(!Float.isFinite(cutInsetMm)||cutInsetMm<0||!Float.isFinite(outerSpacingMm)||outerSpacingMm<0||outerSpacingMm>60)throw new IllegalArgumentException("Invalid cut boundary");
            if(registration){RegistrationMarks.checkedSize(markDiameterMm);if(!RegistrationMarks.validStyle(markStyle)||!Float.isFinite(registrationClearanceMm)||registrationClearanceMm<0||registrationClearanceMm>50)throw new IllegalArgumentException("Invalid registration settings");}
        }
    }
    public static final class Layer {
        public final int index, sourceIndex, color, pixelCount, visiblePixelCount, islandsRemoved, bridgesAdded;
        public final boolean baseLayer;
        /** Pixel-aligned opaque build mask, row-major, shared dimensions across layers. */
        public final boolean[] mask;
        /** Exclusive top-visible owner pixels, independent of the supporting cut shape. */
        public final boolean[] visibleMask;
        private Layer(int i,int source,int c,boolean[] m,boolean[] visible,int buildCount,int visibleCount,int removed,int bridges,boolean base){
            index=i;sourceIndex=source;color=c;mask=m;visibleMask=visible;pixelCount=buildCount;visiblePixelCount=visibleCount;islandsRemoved=removed;bridgesAdded=bridges;baseLayer=base;
        }
    }
    public static final class Result {
        public final int width,height;
        public final float widthMm,heightMm;
        public final float[] thresholds;
        public final float[][] registrationPoints;
        public final List<Layer> layers;
        public final Options options;
        public final boolean stackedVinyl;
        public final int baseSourceLayer;
        private Result(int w,int h,float ww,float hh,float[] t,float[][] points,List<Layer> ls,Options opt,boolean stacked,int base){ width=w;height=h;widthMm=ww;heightMm=hh;thresholds=t;registrationPoints=points;layers=ls;options=opt;stackedVinyl=stacked;baseSourceLayer=base; }
        private volatile SmoothStencilContours.Geometry[] smoothedCut;
        private volatile String[] sharedVisible;
        private volatile String contourMode="legacy";
        /** One validated geometry snapshot shared by preview and export. */
        private synchronized void prepareCutPaths(){
            if(smoothedCut!=null)return;
            SmoothStencilContours.Geometry[] build=new SmoothStencilContours.Geometry[layers.size()];
            // The existing Detail control doubles as the artistic smoothing
            // policy: broader clean regions at low detail; tighter facial
            // features at high detail. No separate contour slider.
            float curveMm=options.curveRadiusMm==0f?0f:
                options.curveRadiusMm*(.70f+.80f*(1f-options.detail));
            // Source-color layers must share interfaces. Independently fitting
            // each cumulative binary mask makes the same border bend differently
            // and triggers the old whole-layer pixel-grid fallback. One common
            // interface graph instead makes the cut contours meet by construction.
            if(options.sourceColorMode && !options.bridgeMode && curveMm>0f){
                try{
                    int[] owner=new int[width*height];Arrays.fill(owner,-1);
                    for(int color=0;color<layers.size();color++){if(((color)&63)==0)CancellationCheckpoint.check();
                        boolean[] visible=layers.get(color).visibleMask;
                        for(int p=0;p<owner.length;p++)if(visible[p]){
                            if(owner[p]>=0)throw new IllegalStateException("Overlapping source-color owners");
                            owner[p]=color;
                        }
                    }
                    SharedStencilBoundaries mesh=SharedStencilBoundaries.create(
                        owner,width,height,widthMm,heightMm,curveMm,options.detail);
                    String[] visible=new String[layers.size()];
                    for(int i=0;i<build.length;i++){if(((i)&63)==0)CancellationCheckpoint.check();
                        if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Stencil vectorization cancelled");
                        TracedLoops cut=walkUnionContours(layers.get(i).mask,width,height,true);
                        SharedStencilBoundaries.PathData part=mesh.path(cut.loops);
                        if(part.loops!=cut.count)throw new IllegalStateException("Shared contour topology mismatch");
                        build[i]=new SmoothStencilContours.Geometry(part.data,layers.get(i).mask.clone(),part.curved,part.loops);
                        TracedLoops top=walkUnionContours(layers.get(i).visibleMask,width,height,true);
                        visible[i]=mesh.path(top.loops).data;
                    }
                    if(stackedVinyl)for(int i=1;i<build.length;i++)if(!containsAll(build[i-1].raster,build[i].raster))
                        throw new IllegalStateException("Non-nested stacked vinyl masks");
                    sharedVisible=visible;
                    contourMode="shared-interfaces "+mesh.smoothedVertexCount()+" smoothed vertices";
                    smoothedCut=build;
                    return;
                }catch(IllegalStateException unsupported){
                    // Legacy tracer is an explicit compatibility fallback for
                    // unsupported masks, not the normal source-color pathway.
                    contourMode="legacy fallback: "+unsupported.getMessage();
                }
            }
            // Independent masks only; shared interfaces and stack repair remain serial.
            LayerParallelism.runSpecialty(build.length,(long)width*height*48L,i->{
                Layer l=layers.get(i);
                build[i]=geometry(l.mask,width,height,widthMm,heightMm,curveMm);
            });
            if(stackedVinyl){
                // Each stacked vinyl cut needs a supporting cut beneath it.
                // A single slight overshoot must not revert an entire face
                // region to the old square pixel contour. Back off smoothing
                // progressively at the affected layer only.
                final float[] reductions={.72f,.46f,.24f,0f};
                for(int i=build.length-1;i>0;i--){if(((i)&63)==0)CancellationCheckpoint.check();
                    if(containsAll(build[i-1].raster,build[i].raster))continue;
                    boolean repaired=false;
                    for(float factor:reductions){CancellationCheckpoint.check();
                        SmoothStencilContours.Geometry lower=geometry(
                            layers.get(i-1).mask,width,height,widthMm,heightMm,curveMm*factor);
                        if(containsAll(lower.raster,build[i].raster)){
                            build[i-1]=lower;repaired=true;break;
                        }
                    }
                    if(repaired)continue;
                    for(float factor:reductions){CancellationCheckpoint.check();
                        SmoothStencilContours.Geometry upper=geometry(
                            layers.get(i).mask,width,height,widthMm,heightMm,curveMm*factor);
                        if(containsAll(build[i-1].raster,upper.raster)){
                            build[i]=upper;repaired=true;break;
                        }
                    }
                    if(!repaired){
                        // Topology/containment always outranks appearance.
                        // Keep the fallback local to this pair only.
                        build[i-1]=geometry(layers.get(i-1).mask,width,height,widthMm,heightMm,0f);
                        build[i]=geometry(layers.get(i).mask,width,height,widthMm,heightMm,0f);
                    }
                }
                for(int i=build.length-1;i>0;i--){if(((i)&63)==0)CancellationCheckpoint.check();
                    if(!containsAll(build[i-1].raster,build[i].raster)){
                        build[i-1]=geometry(layers.get(i-1).mask,width,height,widthMm,heightMm,0f);
                        build[i]=geometry(layers.get(i).mask,width,height,widthMm,heightMm,0f);
                    }
                }
            }
            smoothedCut=build;
        }
        private static boolean containsAll(boolean[] lower,boolean[] upper){
            for(int i=0;i<upper.length;i++)if(upper[i]&&!lower[i])return false;
            return true;
        }
        String cutPathData(int index){
            if(index<0||index>=layers.size())throw new IllegalArgumentException("Layer index");
            prepareCutPaths();return smoothedCut[index].data;
        }
        /** Cached final vector topology, independent of registration validity. */
        int cutContourCount(int index){
            if(index<0||index>=layers.size())throw new IllegalArgumentException("Layer index");
            prepareCutPaths();return smoothedCut[index].loops;
        }
        /** Read-only raster representation of the prepared cut geometry. For shared
         * interfaces this is the validated source topology, not a subpixel raster. */
        boolean[] cutRasterForInspection(int index){prepareCutPaths();return smoothedCut[index].raster;}
        boolean[] cutRasterForTest(int index){return cutRasterForInspection(index).clone();}
        String visiblePathData(int index){
            if(index<0||index>=layers.size())throw new IllegalArgumentException("Layer index");
            float curveMm=options.curveRadiusMm==0f?0f:
                options.curveRadiusMm*(.70f+.80f*(1f-options.detail));
            prepareCutPaths();
            if(sharedVisible!=null)return sharedVisible[index];
            return geometry(layers.get(index).visibleMask,width,height,widthMm,heightMm,curveMm).data;
        }
        public String contourMode(){prepareCutPaths();return contourMode;}
        public RegistrationCutGeometry.Report registrationReport(){
            prepareCutPaths();
            RegistrationCutGeometry.Report report=RegistrationCutGeometry.basic(widthMm,heightMm,
                options.markDiameterMm,options.registrationClearanceMm,registrationPoints);
            float radius=RegistrationMarks.markRadius(options.markDiameterMm,options.registrationClearanceMm)
                +Math.max(0f,options.curveRadiusMm);
            for(int mi=0;mi<4;mi++)if(!report.invalid(mi))for(int li=0;li<smoothedCut.length;li++){if(((li)&63)==0)CancellationCheckpoint.check();
                if(RegistrationCutGeometry.hitsRaster(registrationPoints[mi][0],registrationPoints[mi][1],radius,
                        smoothedCut[li].raster,width,height,widthMm,heightMm)){
                    report.fail(mi,"Registration mark "+(mi+1)+" overlaps stencil layer "+(li+1));break;
                }
            }
            return report;
        }
        public String svg(int layerIndex) {
            if(layerIndex<0||layerIndex>=layers.size())throw new IllegalArgumentException("Layer index");
            Layer layer=layers.get(layerIndex);
            RegistrationMarks.Layout reg=options.registration?
                RegistrationMarks.layout(widthMm,heightMm,options.markDiameterMm,options.registrationClearanceMm,registrationPoints,options.outerSpacingMm):null;
            if(reg!=null){String unsafe=registrationReport().firstIssue();
                if(unsafe!=null)throw new IllegalArgumentException(unsafe);}
            float pad=(reg==null?options.outerSpacingMm:reg.paddingMm)*.5f;
            float boardW=widthMm+4*pad,boardH=heightMm+4*pad;
            StringBuilder out=new StringBuilder(16384);
            out.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(fmt(boardW)).append("mm\" height=\"").append(fmt(boardH)).append("mm\" viewBox=\"0 0 ").append(fmt(boardW)).append(' ').append(fmt(boardH)).append("\">\n");
            out.append("<g fill=\"#"); appendHex6(out,layer.color); out.append("\" fill-rule=\"nonzero\">\n");
            // Trace the physical boundary, not scanline rectangles. This avoids
            // double-cutting shared edges between adjacent occupied cells.
            out.append("<path fill-rule=\"evenodd\" d=\"");
            SmoothStencilContours.appendShifted(out,cutPathData(layerIndex),2*pad);
            out.append("\"/>\n");
            if(reg!=null)for(float[] point:reg.positions)
                out.append(RegistrationMarks.svgMark(options.markStyle,point[0]+2*pad,point[1]+2*pad,options.markDiameterMm,options.minimumCutMm));
            return out.append("</g></svg>\n").toString();
        }
        public boolean isEmpty(int layer){return layers.get(layer).pixelCount==0;}
        public String status(){StringBuilder s=new StringBuilder(stackedVinyl?"Stacked vinyl stencil: ":"Tonal stencil: ").append(layers.size()).append(" layers, ").append(fmt(widthMm)).append(" × ").append(fmt(heightMm)).append(" mm");for(Layer l:layers)s.append(" | L").append(l.index+1).append(l.baseLayer?"(base)":"").append(':').append(l.pixelCount).append(" build / ").append(l.visiblePixelCount).append(" visible");return s.toString();}
    }
    /** Directed grid boundary tracing with even-odd fill for enclosed holes. */
    static void appendUnionContours(StringBuilder out,boolean[] m,int w,int h,float mmW,float mmH,float offset){
        traceUnionContours(out,m,w,h,mmW,mmH,offset,0f);
    }
    /** Exact number of closed blade loops, including openings, but not registration marks.
     * Reuses SVG's edge walk without allocating or formatting an SVG string. */
    static int countUnionContours(boolean[] m,int w,int h){
        return traceUnionContours(null,m,w,h,1f,1f,0f,0f);
    }
    private static void appendUnionContours(StringBuilder out,boolean[] m,int w,int h,float mmW,float mmH,float offset,float radiusMm){
        traceUnionContours(out,m,w,h,mmW,mmH,offset,radiusMm);
    }
    /** One byte per grid vertex. Boundary topology is traced once for each mask. */
    private static final class TracedLoops {
        final int count;
        final ArrayList<ArrayList<int[]>> loops;
        TracedLoops(int n,ArrayList<ArrayList<int[]>> p){count=n;loops=p;}
    }
    private static TracedLoops walkUnionContours(boolean[] m,int w,int h,boolean save){
        if(m==null||w<1||h<1||m.length!=(long)w*h)throw new IllegalArgumentException("Invalid contour mask");
        int cols=w+1,rows=h+1;
        byte[] flags=new byte[cols*rows];
        for(int y=0;y<h;y++){if(((y)&63)==0)CancellationCheckpoint.check();
            if((y&31)==0&&Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException("Cut contour scan cancelled");
            for(int x=0;x<w;x++)if(m[y*w+x]){
                if(y==0||!m[(y-1)*w+x])flags[y*cols+x]|=1;
                if(x==w-1||!m[y*w+x+1])flags[y*cols+x+1]|=2;
                if(y==h-1||!m[(y+1)*w+x])flags[(y+1)*cols+x+1]|=4;
                if(x==0||!m[y*w+x-1])flags[(y+1)*cols+x]|=8;
            }
        }
        ArrayList<ArrayList<int[]>> loops=save?new ArrayList<>():null;
        int contours=0;
        final int[] DX={1,0,-1,0},DY={0,1,0,-1};
        final int perimeterBudget=4*w*h+4;
        for(int seed=0;seed<flags.length;seed++)while(flags[seed]!=0){
            if((seed&4095)==0&&Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException("Cut contour trace cancelled");
            contours++;
            int firstDir=Integer.numberOfTrailingZeros(flags[seed]&255);
            int vertex=seed,dir=firstDir,lastDir=-1,steps=0;
            ArrayList<int[]> corners=save?new ArrayList<>():null;
            do{
                if(++steps>perimeterBudget)throw new IllegalStateException("Unclosed stencil contour");
                if(corners!=null&&(lastDir<0||lastDir!=dir))corners.add(new int[]{vertex%cols,vertex/cols});
                flags[vertex]=(byte)(flags[vertex]&~(1<<dir));
                int xx=vertex%cols+DX[dir],yy=vertex/cols+DY[dir];
                if(xx<0||xx>=cols||yy<0||yy>=rows)throw new IllegalStateException("Cut left image boundary");
                vertex=yy*cols+xx;
                if(vertex==seed)break;
                int bits=flags[vertex]&255,chosen=-1;
                int[] priority={(dir+1)&3,dir,(dir+3)&3,(dir+2)&3};
                for(int candidate:priority)if((bits&(1<<candidate))!=0){chosen=candidate;break;}
                if(chosen<0)throw new IllegalStateException("Open stencil contour");
                lastDir=dir;dir=chosen;
            }while(true);
            if(loops!=null)loops.add(corners);
        }
        return new TracedLoops(contours,loops);
    }
    /** Integer grid rings in the same order as the unsmoothed union exporter. */
    static java.util.List<java.util.ArrayList<int[]>> straightUnionRings(boolean[] mask,int w,int h){
        return walkUnionContours(mask,w,h,true).loops;
    }
    static SmoothStencilContours.Geometry geometryForTest(boolean[] m,int w,int h,float mmW,float mmH,float radiusMm){
        return geometry(m,w,h,mmW,mmH,radiusMm);
    }
    private static SmoothStencilContours.Geometry geometry(boolean[] m,int w,int h,
                                                            float mmW,float mmH,float radiusMm){
        TracedLoops loops=walkUnionContours(m,w,h,true);
        return SmoothStencilContours.build(loops.loops,m,w,h,mmW,mmH,0,radiusMm);
    }
    private static int traceUnionContours(StringBuilder out,boolean[] m,int w,int h,
                                          float mmW,float mmH,float offset,float radiusMm){
        if(out==null)return walkUnionContours(m,w,h,false).count;
        SmoothStencilContours.Geometry g=geometry(m,w,h,mmW,mmH,radiusMm);
        out.append("<path fill-rule=\"evenodd\" d=\"");
        SmoothStencilContours.appendShifted(out,g.data,offset);
        out.append("\"/>\n");
        return g.loops;
    }
    private static void appendHex6(StringBuilder b,int color){
        final char[] hex="0123456789ABCDEF".toCharArray();
        for(int shift=20;shift>=0;shift-=4)b.append(hex[(color>>shift)&15]);
    }
    private static String fmt(float v){return String.format(Locale.US,"%.4f",v);}
    private static float clamp(float x,float lo,float hi){return Math.max(lo,Math.min(hi,x));}
    public static Result generate(int[] argb,int width,int height,Options input){
        if(argb==null||input==null||argb.length!=(long)width*height)throw new IllegalArgumentException("ARGB array size mismatch");
        Options opt=input.copy();opt.validate(width,height);
        int n=width*height, count=opt.layerCount;
        float physicalH=opt.heightMm>0?opt.heightMm:opt.widthMm*height/(float)width;
        float[] luminance=new float[n]; boolean[] valid=new boolean[n];
        int bg=opt.backgroundArgb;
        for(int i=0;i<n;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            int p=argb[i],a=p>>>24;
            valid[i]=a>0;
            int x=i%width,y=i/width;
            float px=(x+.5f)*opt.widthMm/width,py=(y+.5f)*physicalH/height;
            float inset=opt.cutInsetMm;
            if(px<inset||py<inset||px>opt.widthMm-inset||py>physicalH-inset)valid[i]=false;
            float r=(((p>>16)&255)*a+((bg>>16)&255)*(255-a))/255f;
            float g=(((p>>8)&255)*a+((bg>>8)&255)*(255-a))/255f;
            float b=((p&255)*a+(bg&255)*(255-a))/255f;
            luminance[i]=(.2126f*r+.7152f*g+.0722f*b)/255f;
        }
        // Background is not vinyl. Only auto-remove a confidently flat, white,
        // border-connected field, and never remove alpha/snipped areas.
        if(opt.autoWhiteBackground){
            int[] reference=opt.backgroundReference==null?argb:opt.backgroundReference;
            valid=VinylSubjectMask.removePlainWhiteBackground(reference,valid,width,height);
        }
        if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Cancelled stencil render");
        // Source-color mode uses perceptual clustering, not tonal histograms.
        // Skip the expensive unused bilateral/gradient passes for that mode.
        // Detail is physical, not only a pixel count: at high detail a 15-inch
        // portrait must not blur a larger real-world region than a 6-inch one.
        // Keep the existing bilateral algorithm; limit only its footprint
        // when the operator explicitly asks for high photographic detail.
        int toneRadius=opt.sourceColorMode?0:tonalSmoothingRadius(
            opt.blurRadius,opt.detail,width,height,opt.widthMm,physicalH);
        float[] smooth=opt.sourceColorMode?new float[n]:cachedBilateral(luminance,valid,width,height,toneRadius);
        float[] edges=opt.sourceColorMode?new float[n]:scharr(smooth,width,height);
        // Quantile occupancy is assigned by histogram rank, not by repeated
        // threshold comparisons. This prevents a limited tonal range from
        // silently collapsing 5 requested bands into only 2 bands.
        // Within quantized equal-intensity bins, ordered spatial dithering
        // distributes the population without inventing new global tones.
        int[] histogram=new int[256];int total=0;
        if(!opt.sourceColorMode)for(int i=0;i<n;i++)if(valid[i]){histogram[Math.max(0,Math.min(255,(int)(smooth[i]*255f+0.5f)))]++;total++;}
        int[] cumulative=new int[256];int running=0;
        for(int bin=0;bin<256;bin++){if(((bin)&63)==0)CancellationCheckpoint.check();cumulative[bin]=running;running+=histogram[bin];}
        float[] thresholds=new float[count-1];
        for(int layer=1;layer<count;layer++){if(((layer)&63)==0)CancellationCheckpoint.check();
            int target=(int)((long)total*layer/count);
            for(int bin=0;bin<256;bin++)if(cumulative[bin]+histogram[bin]>=target){thresholds[layer-1]=(bin+.5f)/255f;break;}
        }
        int[] owner=new int[n];Arrays.fill(owner,-1);
        int[] derivedColors=null;
        if(opt.sourceColorMode){
            SourceColorStencilQuantizer.Result source=opt.matchVinylPalette
                ?SourceColorStencilQuantizer.quantizeToPalette(argb,width,height,
                    // Hue is a vinyl appearance edit, never a change to the
                    // source-to-physical-layer ownership map. Match the saved
                    // unrotated physical swatches and rotate only sheet RGB.
                    Arrays.copyOf(opt.colors,count),count,valid)
                :SourceColorStencilQuantizer.quantize(argb,width,height,count,valid);
            owner=source.owner;
            derivedColors=source.colors;
        }
        if(!opt.sourceColorMode)for(int y=0;y<height;y++)for(int x=0;x<width;x++){if(((x)&63)==0)CancellationCheckpoint.check();
            int i=y*width+x;if(!valid[i])continue;
            float edge=(edges[i]>opt.edgeThreshold)?
                clamp((edges[i]-opt.edgeThreshold)*opt.edgeStrength,0,.18f):0f;
            // Darken edges gently without overriding global occupancy.
            int bin=Math.max(0,Math.min(255,Math.round((smooth[i]-edge*.24f)*255f)));
            // Bayer rank in [0,1); avoids synchronized monochrome patches
            // when thresholds coincide, at the cost of subtle blue-noise texture.
            int bx=x&3,by=y&3;
            int[] bayer={0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5};
            float t=(bayer[by*4+bx]+.5f)/16f;
            float rank=cumulative[bin]+histogram[bin]*t;
            owner[i]=Math.min(count-1,(int)(rank*count/Math.max(1,total)));
        }
        // Clean the shared region ownership map before constructing color masks.
        // Independent cleanup here used to punch paper-colored holes into hair,
        // skin and facial shadows whenever small disconnected islands were lost.
        final double pxPerMm2=(double)width*height/(opt.widthMm*physicalH);
        final double minAreaMm2=Math.max(opt.minimumIslandAreaMm2,.12+3.0*Math.pow(1.0-opt.detail,1.5));
        final int minPixels=(int)Math.min(10000,Math.max(opt.minimumIslandPixels,
                Math.ceil(minAreaMm2*pxPerMm2)));
        StencilRegionRefiner.Output refined=StencilRegionRefiner.refine(
                owner,valid,width,height,count,opt.detail,minPixels);
        owner=refined.owner;
        boolean[][] visibleMasks=new boolean[count][n];
        int[] visibleCounts=new int[count];
        int[] colors=new int[count];
        for(int l=0;l<count;l++)colors[l]=derivedColors!=null?(opt.colors!=null&&l<opt.colors.length&&opt.colors[l]!=0?opt.colors[l]:derivedColors[l]):opt.colors!=null&&l<opt.colors.length?opt.colors[l]:gray(l,count);
        for(int i=0;i<n;i++)if(owner[i]>=0&&owner[i]<count){visibleMasks[owner[i]][i]=true;visibleCounts[owner[i]]++;}
        final boolean stacked=opt.stackedVinyl&&!opt.bridgeMode;
        // Physical palette overrides must NEVER reorder the stack unexpectedly.
        int[] orderColors=derivedColors!=null?derivedColors:colors;
        int[] order=buildStackOrder(visibleMasks,visibleCounts,orderColors,width,height,opt,stacked);
        if(opt.paletteHueDegrees!=0)for(int i=0;i<colors.length;i++)
            colors[i]=PaletteHueShift.rotateOne(colors[i],opt.paletteHueDegrees);
        List<Layer> layers=new ArrayList<>();
        boolean[] support=new boolean[n];
        for(int i=0;i<n;i++)support[i]=owner[i]>=0;
        // Physical geometry is planned bottom-to-top.
        // Layer 1 is the full subject support silhouette.
        // Every later cut layer keeps its own visible islands AND fills any
        // areas that will be covered by higher opaque vinyl colors. This makes
        // the stack build naturally from the bottom upward and avoids needless
        // cut gaps inside assembled regions.
        for(int position=0;position<count;position++){if(((position)&63)==0)CancellationCheckpoint.check();
            if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Cancelled stencil render");
            int sourceLayer=order[position];
            boolean[] mask;
            if(stacked&&position==0){
                mask=support.clone();
            }else if(stacked){
                mask=new boolean[n];
                for(int future=position;future<count;future++){if(((future)&63)==0)CancellationCheckpoint.check();
                    boolean[] src=visibleMasks[order[future]];
                    for(int i=0;i<n;i++)if(src[i])mask[i]=true;
                }
            }else{
                mask=visibleMasks[sourceLayer].clone();
            }
            // Add support overlap UNDER an upper layer, not on the top-visible
            // boundary. Limit growth to the subject silhouette, so snipped and
            // transparent pixels never acquire accidental cuts.
            if(stacked && position>0 && position<count-1 && opt.underlayTrapMm>0){
                boolean[] upper=new boolean[n];
                for(int future=position+1;future<count;future++){if(((future)&63)==0)CancellationCheckpoint.check();
                    boolean[] src=visibleMasks[order[future]];
                    for(int i=0;i<n;i++)if(src[i])upper[i]=true;
                }
                // The cumulative sheet already contains the full upper cut;
                // limiting the overlap to those covered pixels prevents a
                // lower color from becoming newly visible outside its overlay.
                addTrappedUnderlay(mask,upper,upper,width,height,opt.widthMm,physicalH,opt.underlayTrapMm);
            }
            int bridged=opt.bridgeMode?bridgeNearest(mask,valid,width,height,opt.bridgeSearchPixels,opt.bridgeWidthPixels):0;
            int buildPixels=0;for(boolean x:mask)if(x)buildPixels++;
            layers.add(new Layer(position,sourceLayer,colors[sourceLayer],mask,visibleMasks[sourceLayer],buildPixels,
                    visibleCounts[sourceLayer],0,bridged,stacked&&position==0));
        }
        float[][] marks=opt.registration?SheetFrame.of(opt.widthMm,physicalH,true,opt.markDiameterMm,opt.registrationClearanceMm,opt.marks,opt.outerSpacingMm,opt.circleCrop).marks():new float[0][0];
        return new Result(width,height,opt.widthMm,physicalH,thresholds,marks,layers,opt,stacked,order.length==0?-1:order[0]);
    }
    /** Physical disc dilation around upper vinyl's boundary, bounded to the
     * permitted subject matte. Trapping is never added to top-visible vinyl. */
    static int addTrappedUnderlay(boolean[] lower,boolean[] upper,boolean[] allowed,
                                  int w,int h,float widthMm,float heightMm,float trapMm){
        if(trapMm<=0)return 0;
        double cellX=widthMm/w,cellY=heightMm/h;
        int rx=(int)Math.ceil(trapMm/cellX),ry=(int)Math.ceil(trapMm/cellY);
        if(rx>64||ry>64)throw new IllegalArgumentException("Trap exceeds processing geometry budget");
        int[] dx=new int[(rx*2+1)*(ry*2+1)],dy=new int[dx.length];int offsetCount=0;
        for(int oy=-ry;oy<=ry;oy++)for(int ox=-rx;ox<=rx;ox++){if(((ox)&63)==0)CancellationCheckpoint.check();
            if(ox==0&&oy==0)continue;
            if((ox*cellX)*(ox*cellX)+(oy*cellY)*(oy*cellY)>trapMm*trapMm+1e-6)continue;
            dx[offsetCount]=ox;dy[offsetCount++]=oy;
        }
        if(offsetCount==0)return 0;
        boolean[] grown=lower.clone();int added=0;
        for(int y=0;y<h;y++){if(((y)&63)==0)CancellationCheckpoint.check();
            if((y&15)==0&&Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Trapping cancelled");
            for(int x=0;x<w;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                int pos=y*w+x;if(!upper[pos])continue;
                // Only boundary pixels contribute; interior pixels are already covered.
                if(x>0&&x+1<w&&y>0&&y+1<h && upper[pos-1]&&upper[pos+1]&&upper[pos-w]&&upper[pos+w])continue;
                for(int j=0;j<offsetCount;j++){if(((j)&63)==0)CancellationCheckpoint.check();
                    int xx=x+dx[j],yy=y+dy[j];if(xx<0||yy<0||xx>=w||yy>=h)continue;
                    int p=yy*w+xx;
                    if(allowed[p]&&!grown[p]){grown[p]=true;added++;}
                }
            }
        }
        System.arraycopy(grown,0,lower,0,lower.length);
        return added;
    }
    private static int[] actualVinylColors(int[] colors,int hue,int count){
        int[] display=Arrays.copyOf(colors,count);
        if(hue!=0)for(int i=0;i<count;i++)display[i]=PaletteHueShift.rotateOne(display[i],hue);
        return display;
    }
    private static int[] buildStackOrder(boolean[][] visibleMasks,int[] visibleCounts,int[] colors,int w,int h,Options opt,boolean stacked){
        int count=visibleCounts.length;int[] order=new int[count];for(int i=0;i<count;i++)order[i]=i;
        if(!stacked)return order;
        int base=chooseBaseLayer(visibleMasks,visibleCounts,colors,w,h,opt.baseLayer);
        Integer[] rest=new Integer[Math.max(0,count-1)];int at=0;
        for(int i=0;i<count;i++)if(i!=base)rest[at++]=i;
        Arrays.sort(rest,(a,b)->{
            int area=Integer.compare(visibleCounts[b],visibleCounts[a]);
            if(area!=0)return area;
            int lum=Integer.compare(rgbLuma(colors[b]),rgbLuma(colors[a])); // lighter first on lower support layers
            if(lum!=0)return lum;
            return Integer.compare(a,b);
        });
        order[0]=base;
        for(int i=0;i<rest.length;i++)order[i+1]=rest[i];
        return order;
    }
    private static int chooseBaseLayer(boolean[][] visibleMasks,int[] visibleCounts,int[] colors,int w,int h,int explicit){
        if(explicit>=0&&explicit<visibleCounts.length&&visibleCounts[explicit]>0)return explicit;
        int best=0;double bestScore=Double.NEGATIVE_INFINITY;
        final int x0=Math.max(0,w/5),x1=Math.min(w,w-w/5),y0=Math.max(0,h/5),y1=Math.min(h,h-h/5);
        for(int layer=0;layer<visibleCounts.length;layer++){if(((layer)&63)==0)CancellationCheckpoint.check();
            int area=visibleCounts[layer];
            if(area<=0)continue;
            boolean[] mask=visibleMasks[layer];
            int center=0,edge=0,largest=0,components=0;
            boolean[] seen=new boolean[mask.length];int[] q=new int[mask.length];
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                int p=y*w+x;if(!mask[p])continue;
                if(x>=x0&&x<x1&&y>=y0&&y<y1)center++;
                if(x==0||y==0||x==w-1||y==h-1)edge++;
                if(seen[p])continue;
                int head=0,tail=1;q[0]=p;seen[p]=true;components++;int size=0;
                while(head<tail){int v=q[head++],vx=v%w,vy=v/w;size++;
                    if(vx>0)tail=push(v-1,mask,seen,q,tail);
                    if(vx+1<w)tail=push(v+1,mask,seen,q,tail);
                    if(vy>0)tail=push(v-w,mask,seen,q,tail);
                    if(vy+1<h)tail=push(v+w,mask,seen,q,tail);
                }
                if(size>largest)largest=size;
            }
            double centerRatio=center/(double)Math.max(1,area);
            double edgeRatio=edge/(double)Math.max(1,area);
            double largestRatio=largest/(double)Math.max(1,area);
            int luma=rgbLuma(colors[layer]);
            double lightPenalty=luma>245?.18:0.0;
            double darkPenalty=luma<18?.10:0.0;
            double score=area*(0.50+0.80*largestRatio+0.45*centerRatio-0.25*edgeRatio-lightPenalty-darkPenalty)-components*1.5;
            if(score>bestScore){bestScore=score;best=layer;}
        }
        return best;
    }
    private static int rgbLuma(int color){return (2126*((color>>16)&255)+7152*((color>>8)&255)+722*(color&255))/10000;}
    private static int gray(int layer,int n){int v=Math.round(30+195f*layer/Math.max(1,n-1));return 0xff000000|(v<<16)|(v<<8)|v;}
    /** Exact physical smoothing ceiling only when Detail is 70% or above.
     * At maximum detail keep filtering within 0.25 mm where pixel density
     * allows it; pixel-level quantization and island filtering still apply.
     * Never changes source-color separation or the cut safety minimum.
     */
    static int tonalSmoothingRadius(int requested,float detail,int w,int h,float mmW,float mmH){
        if(requested<0||requested>8||!Float.isFinite(detail)||detail<0||detail>1||
           !(mmW>0)||!(mmH>0)||w<1||h<1)
            throw new IllegalArgumentException("Invalid stencil smoothing configuration");
        if(detail<.70f)return requested;
        float fraction=(detail-.70f)/.30f;
        double maxPhysicalMm=.85-.60*fraction;
        double density=Math.min(w/mmW,h/mmH);
        int cap=(int)Math.floor(maxPhysicalMm*density+.5);
        return Math.min(requested,Math.max(0,cap));
    }

    private static float[] bilateral(float[] src,boolean[] valid,int w,int h,int radius){
        if(radius==0)return src.clone();float[] dest=new float[src.length];float spatial=2f*radius*radius;float tonal=.0128f;
        for(int y=0;y<h;y++){if(((y)&63)==0)CancellationCheckpoint.check();
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Cancelled bilateral pass");
            for(int x=0;x<w;x++){if(((x)&63)==0)CancellationCheckpoint.check();
            int p=y*w+x;if(!valid[p]){dest[p]=src[p];continue;}
            float sum=0,weights=0,c=src[p];
            for(int dy=-radius;dy<=radius;dy++)for(int dx=-radius;dx<=radius;dx++){if(((dx)&63)==0)CancellationCheckpoint.check();
                int xx=x+dx,yy=y+dy;if(xx<0||xx>=w||yy<0||yy>=h||!valid[yy*w+xx])continue;
                float d=src[yy*w+xx]-c;
                float weight=(float)Math.exp(-(dx*dx+dy*dy)/spatial-d*d/tonal);
                sum+=src[yy*w+xx]*weight;weights+=weight;
            }
            dest[p]=weights==0?c:sum/weights;
            }
        }
        return dest;
    }
    private static float[] scharr(float[] a,int w,int h){
        float[] out=new float[a.length];int[][] kx={{-3,0,3},{-10,0,10},{-3,0,3}};
        int[][] ky={{-3,-10,-3},{0,0,0},{3,10,3}};
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){if(((x)&63)==0)CancellationCheckpoint.check();
            float gx=0,gy=0;for(int yy=-1;yy<=1;yy++)for(int xx=-1;xx<=1;xx++){if(((xx)&63)==0)CancellationCheckpoint.check();
                float p=a[(y+yy)*w+x+xx];gx+=p*kx[yy+1][xx+1];gy+=p*ky[yy+1][xx+1];
            }out[y*w+x]=clamp((float)Math.hypot(gx,gy)/16f,0,1);
        }return out;
    }
    /** Connected-component filtering; only four-connected pixels form a vinyl island. */
    private static int removeTinyIslands(boolean[] mask,int w,int h,int minimum){
        if(minimum<=1)return 0;boolean[] visited=new boolean[mask.length];int[] q=new int[mask.length];int removed=0;
        for(int seed=0;seed<mask.length;seed++)if(mask[seed]&&!visited[seed]){
            int head=0,tail=1;q[0]=seed;visited[seed]=true;
            while(head<tail){int p=q[head++],x=p%w,y=p/w;
                if(x>0)tail=push(p-1,mask,visited,q,tail);
                if(x+1<w)tail=push(p+1,mask,visited,q,tail);
                if(y>0)tail=push(p-w,mask,visited,q,tail);
                if(y+1<h)tail=push(p+w,mask,visited,q,tail);
            }
            if(tail<minimum){for(int i=0;i<tail;i++)mask[q[i]]=false;removed++;}
        }return removed;
    }
    private static int push(int p,boolean[] m,boolean[] seen,int[] q,int tail){if(m[p]&&!seen[p]){seen[p]=true;q[tail++]=p;}return tail;}
    /**
     * Conservative optional bridges: from each disconnected component's closest
     * pair of pixels, connect using a thin line in this layer only. Bridge additions
     * are never made through transparent/forbidden background pixels. This mode
     * changes layer exclusivity; use ONLY for reusable painting stencil sheets,
     * never for the default multi-color vinyl workflow.
     */
    private static int bridgeNearest(boolean[] mask,boolean[] valid,int w,int h,int maxDistance,int thickness){
        int[] label=new int[mask.length];Arrays.fill(label,-1);int[] q=new int[mask.length];int components=0;
        for(int i=0;i<mask.length;i++)if(mask[i]&&label[i]<0){int head=0,tail=1;q[0]=i;label[i]=components;
            while(head<tail){int p=q[head++],x=p%w,y=p/w;
                if(x>0)tail=pushLabel(p-1,mask,label,components,q,tail);
                if(x+1<w)tail=pushLabel(p+1,mask,label,components,q,tail);
                if(y>0)tail=pushLabel(p-w,mask,label,components,q,tail);
                if(y+1<h)tail=pushLabel(p+w,mask,label,components,q,tail);
            }components++;
        }
        if(components<=1)return 0;
        int added=0;
        // Find nearest other-labeled point around boundary pixels, capped to avoid quadratic scans.
        for(int comp=1;comp<components;comp++){if(((comp)&63)==0)CancellationCheckpoint.check();
            int bestA=-1,bestB=-1,bestD=Integer.MAX_VALUE;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                int p=y*w+x;if(label[p]!=comp)continue;
                for(int dy=-maxDistance;dy<=maxDistance;dy++)for(int dx=-maxDistance;dx<=maxDistance;dx++){if(((dx)&63)==0)CancellationCheckpoint.check();
                    int d=Math.abs(dx)+Math.abs(dy);if(d==0||d>=bestD||d>maxDistance)continue;
                    int xx=x+dx,yy=y+dy;if(xx<0||yy<0||xx>=w||yy>=h)continue;
                    int other=yy*w+xx;if(label[other]<0||label[other]==comp)continue;
                    if(lineAllowed(p,other,valid,w)){bestD=d;bestA=p;bestB=other;}
                }
            }
            if(bestA>=0)added+=rasterBridge(bestA,bestB,mask,valid,w,h,thickness);
        }
        return added;
    }
    private static int pushLabel(int p,boolean[] mask,int[] labels,int c,int[] q,int t){if(mask[p]&&labels[p]<0){labels[p]=c;q[t++]=p;}return t;}
    private static boolean lineAllowed(int a,int b,boolean[] allowed,int w){int x=a%w,y=a/w,x2=b%w,y2=b/w,steps=Math.max(Math.abs(x2-x),Math.abs(y2-y));for(int i=0;i<=steps;i++){if(((i)&63)==0)CancellationCheckpoint.check();int xx=Math.round(x+(x2-x)*i/(float)Math.max(1,steps)),yy=Math.round(y+(y2-y)*i/(float)Math.max(1,steps));if(!allowed[yy*w+xx])return false;}return true;}
    private static int rasterBridge(int a,int b,boolean[] mask,boolean[] valid,int w,int h,int width){int x=a%w,y=a/w,x2=b%w,y2=b/w,steps=Math.max(Math.abs(x2-x),Math.abs(y2-y)),added=0;
        for(int i=0;i<=steps;i++){if(((i)&63)==0)CancellationCheckpoint.check();int xx=Math.round(x+(x2-x)*i/(float)Math.max(1,steps)),yy=Math.round(y+(y2-y)*i/(float)Math.max(1,steps));for(int oy=-(width-1)/2;oy<=width/2;oy++)for(int ox=-(width-1)/2;ox<=width/2;ox++){if(((ox)&63)==0)CancellationCheckpoint.check();int u=xx+ox,v=yy+oy;if(u>=0&&u<w&&v>=0&&v<h&&valid[v*w+u]&&!mask[v*w+u]){mask[v*w+u]=true;added++;}}}return added;
    }
}

package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Physical, CUT-geometry smoothing for the three Organic Flow styles.
 * Operates on actual per-color masks, not on the displayed bitmap. A slider
 * controls low-information micro-island removal and contour-local curve fitting.
 * Every proposed group is checked against image-feature coverage and the shared
 * cut inspector; rejected groups are split, never silently reverted en masse.
 * L1 (the structural Snip) is never modified.
 */
final class OrganicFlowCutSmoother {
    private OrganicFlowCutSmoother() {}
    static final int DEFAULT = 50;
    static final int MAX = 100;
    private static final java.util.concurrent.atomic.AtomicInteger CHANGED_ONLY_ACCEPTS=
        new java.util.concurrent.atomic.AtomicInteger();
    static int changedOnlyAcceptsForTest(){return CHANGED_ONLY_ACCEPTS.get();}
    static void resetChangedOnlyAcceptsForTest(){CHANGED_ONLY_ACCEPTS.set(0);}

    private static final class P {
        final double x, y;
        P(double x, double y) { this.x = x; this.y = y; }
    }
    private static double loopAreaAbs(float[] loop){
        return Math.abs(StyleKit.signedArea(loop));
    }

    /**
     * A 3x3 max footprint protects the shoulders of real image boundaries as
     * well as the pixel on the photographic edge itself. This mask never
     * comes from Snip transparency or the decorative ribbon phase.
     */
    static float[] expandImportance(float[] original,boolean[] valid,int w,int h){
        if(original.length!=w*h||valid.length!=w*h)
            throw new IllegalArgumentException("Feature map dimensions differ");
        float[] out=new float[original.length];
        for(int y=0;y<h;y++){
            if((y&31)==0)StyleKit.checkCancelled();
            for(int x=0;x<w;x++){
                int i=y*w+x;if(!valid[i])continue;
                float best=original[i];
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
                    int xx=x+dx,yy=y+dy;
                    if(xx>=0&&xx<w&&yy>=0&&yy<h&&valid[yy*w+xx])
                        best=Math.max(best,original[yy*w+xx]*.92f);
                }
                out[i]=best;
            }
        }
        return out;
    }

    /**
     * Remove only clearly low-information, physically marginal isolated cuts.
     * Previous 16 mm² cutoff at 100% erased eyes, lips and ribbon structure.
     * Significant photo structures get a smaller threshold independent of
     * smoothing, while still obeying the physical minimum width inspection.
     */
    static void discardMicroIslands(PatternPortraitTrioEngine.Surface surface,
                                    int smoothing,float minCutMm,float[] importance){
        if(smoothing<=0)return;
        if(smoothing>MAX || importance.length!=surface.w*surface.h)
            throw new IllegalArgumentException("Invalid smoothing/importance");
        double f=smoothing/100.0,cellArea=surface.mm*surface.mm;
        double ordinaryMm2=Math.max(.8,minCutMm*minCutMm*1.5)+3.0*f*f;
        double protectedMm2=Math.max(.30,minCutMm*minCutMm*.75);
        int plainLimit=Math.max(2,(int)Math.ceil(ordinaryMm2/cellArea));
        int protectedLimit=Math.max(1,(int)Math.ceil(protectedMm2/cellArea));
        int n=surface.w*surface.h;
        int[] queue=new int[n];
        for(int l=1;l<surface.ink.length;l++){
            StyleKit.checkCancelled();
            boolean[] mask=surface.ink[l];
            boolean[] seen=new boolean[n];
            for(int start=0;start<n;start++){
                if((start&16383)==0)StyleKit.checkCancelled();
                if(!mask[start]||seen[start])continue;
                int head=0,tail=1,photoPixels=0;queue[0]=start;seen[start]=true;
                while(head<tail){
                    int current=queue[head++],x=current%surface.w,y=current/surface.w;
                    if(importance[current]>=.52f)photoPixels++;
                    for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
                        if(dx==0&&dy==0)continue;
                        int xx=x+dx,yy=y+dy;
                        if(xx<0||xx>=surface.w||yy<0||yy>=surface.h)continue;
                        int next=yy*surface.w+xx;
                        if(mask[next]&&!seen[next]){seen[next]=true;queue[tail++]=next;}
                    }
                }
                // One noisy edge pixel is not enough: the island must have
                // meaningful feature support across more than one sample.
                boolean important=photoPixels>=Math.max(2,(tail+7)/8);
                int threshold=important?protectedLimit:plainLimit;
                if(tail<threshold)for(int i=0;i<tail;i++)mask[queue[i]]=false;
            }
            // Small eye/highlight cutouts are equally valuable as small ink
            // islands. Preserve their holes if the photograph has a feature.
            seen=new boolean[n];
            for(int start=0;start<n;start++){
                if((start&16383)==0)StyleKit.checkCancelled();
                if(mask[start]||seen[start]||!surface.valid[start])continue;
                int head=0,tail=1,photoPixels=0;queue[0]=start;seen[start]=true;
                boolean exterior=false;
                while(head<tail){
                    int current=queue[head++],x=current%surface.w,y=current/surface.w;
                    if(importance[current]>=.52f)photoPixels++;
                    if(x==0||y==0||x==surface.w-1||y==surface.h-1)exterior=true;
                    for(int k=0;k<4;k++){
                        int xx=x+(k==0?-1:k==1?1:0);
                        int yy=y+(k==2?-1:k==3?1:0);
                        if(xx<0||xx>=surface.w||yy<0||yy>=surface.h){exterior=true;continue;}
                        int next=yy*surface.w+xx;
                        if(!surface.valid[next]){exterior=true;continue;}
                        if(!mask[next]&&!seen[next]){seen[next]=true;queue[tail++]=next;}
                    }
                }
                boolean important=photoPixels>=Math.max(2,(tail+7)/8);
                int threshold=important?protectedLimit:Math.max(2,plainLimit/3);
                if(!exterior&&tail<threshold)
                    for(int i=0;i<tail;i++)mask[queue[i]]=true;
            }
        }
    }

    /**
     * Contour-local vector smoothing. The previous code asked a heavily blurred
     * multi-loop replacement to pass ONE fidelity and cut test for the complete
     * layer; failure silently restored hundreds of staircase loops together.
     * The independent contour proposal below never changes the identity/order
     * of a sheet or its rings. Failed groups are bisected so safe fitted curves
     * remain while only unsafe shapes retain their original boundary.
     */
    static StyleKit.Result contour(StyleKit.Result original,
                                   PatternPortraitTrioEngine.Surface source,
                                   float minCut,float gap,int smoothing,float[] importance) {
        if(smoothing<=0)return original;
        if(smoothing>MAX||importance.length!=source.w*source.h)
            throw new IllegalArgumentException("Invalid contour smoothing");
        final float strength=smoothing/100f;
        final float cell=source.mm;
        int roundedContours=0;
        for(int layer=1;layer<original.layers.size();layer++){
            StyleKit.checkCancelled();
            final StyleKit.Layer target=original.layers.get(layer);
            int count=target.loops.size();
            if(count==0)continue;
            float[][] proposals=new float[count][];
            ArrayList<Integer> eligible=new ArrayList<>();
            for(int k=0;k<count;k++){
                if((k&127)==0)StyleKit.checkCancelled();
                float[] raw=target.loops.get(k);
                float[] candidate=localCurve(raw,strength,cell,minCut,original.widthMm,original.heightMm);
                if(candidate==null||Arrays.equals(candidate,raw))continue;
                proposals[k]=candidate;
                eligible.add(k);
            }
            // Even when no contour qualifies for the first cubic fit, still run
            // the uniform boundary pass. In particular, broad light sheets can
            // contain many short/fragile loops with no first-pass candidates.
            if(!eligible.isEmpty()){
                eligible.sort((a,b)->Integer.compare(
                    target.loops.get(b).length,target.loops.get(a).length));
                // A phone must not spend its entire render window repeatedly
                // validating tiny optional curve improvements. Preserve large
                // outlines first, then stop at a deterministic work budget.
                int limit=Math.min(56,eligible.size());
                int[] budget={28};
                FidelityTracker layerFidelity=target.loops.size()>8?
                    tryFidelity(source.ink[layer],target.loops,importance,
                        source.valid,source.w,source.h,source.mm):null;
                acceptCurveGroups(original,layer,eligible,proposals,0,limit,source,
                    minCut,gap,importance,budget,layerFidelity);
            }
            roundedContours+=uniformLayerPass(original,layer,source,minCut,gap,
                importance,strength);
            for(int id:eligible)if(target.loops.get(id)==proposals[id])roundedContours++;
        }
        original.summary="Rounded "+roundedContours+" contours";
        return original;
    }

    /** Conservative raster-step polish for selected rounded-motif styles.
     * No mask cleanup, no palette changes, no deleted loops. Exact sampled
     * parity and local cut preflight are required; the physical base is locked.
     * Keep this independent of the Organic Flow artistic smoothing pipeline.
     */
    static int polishRasterMotifs(StyleKit.Result result, PatternPortraitTrioEngine.Surface source,
            float minCut, float gap) {
        int accepted=0;
        for(int layer=1;layer<result.layers.size();layer++){
            StyleKit.checkCancelled();
            StyleKit.Layer target=result.layers.get(layer);
            if(target.loops.isEmpty())continue;
            int count=target.loops.size();
            float[][] bounds=new float[count][],boxes=new float[count][];
            ArrayList<Integer> selected=new ArrayList<>();
            for(int i=0;i<count;i++){
                if((i&127)==0)StyleKit.checkCancelled();
                float[] ring=target.loops.get(i);
                bounds[i]=bounding(ring);boxes[i]=boundaryBlocks(ring);
                if(ring.length>=16&&ring.length<=1200&&awayFromSnip(ring,source))selected.add(i);
            }
            ContourBoundsIndex index=new ContourBoundsIndex(bounds);
            // Prioritize substantial curves, but avoid huge mesh parent rings
            // when many small independent pieces can be improved instead.
            selected.sort((a,b)->Double.compare(loopAreaAbs(target.loops.get(b)),loopAreaAbs(target.loops.get(a))));
            int attempts=0,points=0;
            for(int id:selected){
                StyleKit.checkCancelled();
                float[] before=target.loops.get(id);
                if(attempts++>=32||points+before.length/2>4500)break;
                points+=before.length/2;
                float[] proposed=roundRasterSteps(before,source.mm,Math.min(minCut,gap),
                    result.widthMm,result.heightMm);
                if(proposed==null||Arrays.equals(before,proposed)||
                    !sameRingSamples(before,proposed,source))continue;
                target.loops.set(id,proposed);
                boolean keep=false;
                try{
                    boolean safe=boundedMotifSafety(target,id,before,minCut,gap,result.widthMm,result.heightMm,bounds,boxes,index);
                    StyleKit.checkCancelled();
                    if(safe){
                        bounds[id]=bounding(proposed);boxes[id]=boundaryBlocks(proposed);index.update(id,bounds[id]);
                        keep=true;accepted++;
                    }
                }catch(java.util.concurrent.CancellationException cancelled){throw cancelled;}
                catch(RuntimeException unsafe){StyleKit.checkCancelled();}
                finally{if(!keep)target.loops.set(id,before);}
            }
        }
        return accepted;
    }

    private static boolean boundedMotifSafety(StyleKit.Layer target,int id,float[] before,
            float minCut,float gap,float width,float height,float[][] bounds,float[][] boxes,ContourBoundsIndex index){
        float[] after=target.loops.get(id);
        if(!sameTopologyAndDeviation(before,after,Math.max(minCut*.52f,gap*.70f)))return false;
        if(changedOnlyClean(target,id,before,minCut,gap,width,height,bounds,boxes,index))return true;
        // Bounding-box neighbours can include the enormous parent boundary of a
        // textile mesh. Do not run an expensive differential pass for one tiny
        // optional fillet; retain the original ring in that case.
        float[] a=bounds[id],b=bounding(after);
        float pad=Math.max(minCut,gap)*1.3f+.4f;
        float[] box={Math.min(a[0],b[0])-pad,Math.min(a[1],b[1])-pad,
            Math.max(a[2],b[2])+pad,Math.max(a[3],b[3])+pad};
        int points=0;
        for(int i:index.query(box,id)){
            StyleKit.checkCancelled();
            if(i==id||overlaps(box,bounds[i]))points+=target.loops.get(i).length/2;
            if(points>650)return false;
        }
        return localNonRegression(target,id,before,minCut,gap,width,height,bounds,boxes,index);
    }

    /** Even-odd fill is XOR of individual rings. If a replacement leaves its
     * own sampled fill exactly unchanged, the entire sheet is identical at
     * every working-grid sample, including holes, overlapping rings and Snip.
     * Check just its old/new bounding rectangle rather than rasterizing all
     * other rings for every candidate. */
    static boolean sameRingSamples(float[] before,float[] after,PatternPortraitTrioEngine.Surface s){
        float[] a=bounding(before),b=bounding(after);float mm=s.mm;
        int x0=Math.max(0,(int)Math.floor(Math.min(a[0],b[0])/mm)-1);
        int y0=Math.max(0,(int)Math.floor(Math.min(a[1],b[1])/mm)-1);
        int x1=Math.min(s.w,(int)Math.ceil(Math.max(a[2],b[2])/mm)+1);
        int y1=Math.min(s.h,(int)Math.ceil(Math.max(a[3],b[3])/mm)+1);
        if(x0>=x1||y0>=y1||(long)(x1-x0)*(y1-y0)>120000)return false;
        boolean[] oldMask=rasterizeRegion(java.util.Collections.singletonList(before),s.w,s.h,mm,x0,y0,x1,y1);
        boolean[] newMask=rasterizeRegion(java.util.Collections.singletonList(after),s.w,s.h,mm,x0,y0,x1,y1);
        return oldMask!=null&&newMask!=null&&Arrays.equals(oldMask,newMask);
    }

    /** Protect clipped contours and intentional canvas edges, including holes. */
    private static boolean awayFromSnip(float[] ring,PatternPortraitTrioEngine.Surface s){
        for(int i=0;i<ring.length;i+=2){
            int x=(int)Math.floor(ring[i]/s.mm),y=(int)Math.floor(ring[i+1]/s.mm);
            if(x<1||y<1||x>=s.w-1||y>=s.h-1)return false;
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)
                if(!s.valid[(y+dy)*s.w+x+dx])return false;
        }
        return true;
    }

    /** Short-edge rounding only. Preserve deliberate long-edge junctions;
     * physical displacement is limited even on very coarse or small artwork.
     * A gentle vertex move avoids doubling the plotter's point count. */
    static float[] roundRasterSteps(float[] before,float cell,float floor,float width,float height){
        if(before==null||before.length<16||!Float.isFinite(cell)||cell<=0||floor<=0)return null;
        float[] next=before.clone();int n=before.length/2;
        double maxShift=Math.min(cell*.30,Math.min(.10,floor*.22));
        int moved=0;
        for(int i=0;i<n;i++){
            int a=(i+n-1)%n,b=(i+1)%n;
            double x=before[2*i],y=before[2*i+1];
            double ax=before[2*a]-x,ay=before[2*a+1]-y;
            double bx=before[2*b]-x,by=before[2*b+1]-y;
            double al=Math.hypot(ax,ay),bl=Math.hypot(bx,by);
            if(al<1e-7||bl<1e-7||Math.min(al,bl)>cell*1.6)continue;
            // Do not alter sharp intentional vertices or long straight edges.
            double dot=(ax*bx+ay*by)/(al*bl);
            if(dot>-.25||dot<-.995)continue;
            double dx=(ax/al+bx/bl)*Math.min(al,bl)*.25;
            double dy=(ay/al+by/bl)*Math.min(al,bl)*.25;
            double d=Math.hypot(dx,dy);
            if(d<1e-7)continue;
            double k=Math.min(1,maxShift/d);
            next[2*i]=(float)(x+dx*k);next[2*i+1]=(float)(y+dy*k);moved++;
        }
        if(moved==0)return null;
        next=StyleKit.removeCollinear(next,Math.min(.02f,cell*.06f));
        if(next.length<6||next.length>before.length)return null;
        for(int i=0;i<next.length;i+=2)if(next[i]<0||next[i]>width||next[i+1]<0||next[i+1]>height)return null;
        double area=StyleKit.signedArea(before),after=StyleKit.signedArea(next);
        if(area*after<=0||Math.abs(after-area)>Math.abs(area)*.025||StyleKit.selfIntersections(next,1)>0)return null;
        return next;
    }

    /**
     * A second, uniform pass gives every non-base layer's largest boundaries
     * the same smoothing opportunity. This is especially important for the
     * dominant light fill region, which may survive the first budgeted pass
     * but still keep more staircase edges than adjacent darker layers.
     */
    private static int uniformLayerPass(StyleKit.Result result,int layer,
            PatternPortraitTrioEngine.Surface source,float minCut,float gap,
            float[] importance,float strength){
        StyleKit.Layer target=result.layers.get(layer);
        if(target.loops.isEmpty())return 0;
        ArrayList<Integer> selected=uniformContourCandidates(target,strength,minCut,source.mm);
        // Snapshot once. A single contour's boundary can only change pixels
        // inside the union of its old and proposed bounds. Restrict fidelity
        // checks to that area and maintain the EXACT whole-layer loss totals.
        final FidelityTracker fidelity=tryFidelity(source.ink[layer],target.loops,
            importance,source.valid,source.w,source.h,source.mm);
        int changed=0;
        for(int rank=0;rank<selected.size();rank++){
            StyleKit.checkCancelled();
            int id=selected.get(rank);
            float[] before=target.loops.get(id);
            double area=loopAreaAbs(before);
            if(area<Math.max(minCut*minCut*2.0,source.mm*source.mm*10.0))continue;
            float[] candidate=uniformBoundaryCurve(before,Math.max(strength,.35f),source.mm,
                minCut,result.widthMm,result.heightMm);
            if(candidate==null||Arrays.equals(candidate,before))continue;
            target.loops.set(id,candidate);
            final FidelityTracker.Change delta=fidelity==null?null:
                fidelity.evaluate(target.loops,id,before,candidate);
            boolean featureSafe=fidelity==null?
                preservesPhotoFeatures(source.ink[layer],target.loops,importance,
                    source.valid,source.w,source.h,source.mm):delta!=null;
            boolean ok=featureSafe &&
                localNonRegression(target,id,before,minCut,gap,result.widthMm,result.heightMm,
                    fidelity==null?null:fidelity.bounds,
                    fidelity==null?null:fidelity.segmentBoxes,
                    fidelity==null?null:fidelity.safetyIndex);
            if(ok){if(fidelity!=null)fidelity.commit(delta);changed++;}
            else target.loops.set(id,before);
        }
        return changed;
    }

    private static FidelityTracker tryFidelity(boolean[] reference,List<float[]> loops,
            float[] importance,boolean[] valid,int w,int h,float cell){
        try{return new FidelityTracker(reference,loops,importance,valid,w,h,cell);}
        catch(RuntimeException malformed){
            if(malformed instanceof java.util.concurrent.CancellationException)throw malformed;
            // A non-manifold legacy contour must not crash style selection.
            // Preserve the original conservative whole-layer safety path.
            return null;
        }
    }
    /** Incremental feature-fidelity validator for one changed contour. The
     * reference counts and current output mask are established once per layer.
     * Instead of scan-converting the entire layer for every candidate, it
     * recomputes only the changed contour's union bounds and updates totals on
     * acceptance. The test is algebraically identical to preservesPhotoFeatures.
     * The separate geometric cut inspector remains mandatory and unchanged.
     */
    static final class FidelityTracker {
        final boolean[] reference,valid,current;
        final float[] importance;
        final int w,h;
        final float cell;
        final float[][] bounds;
        final float[][] segmentBoxes;
        final ContourBoundsIndex safetyIndex;
        final int ink,photo;
        int inkLost,photoLost;
        static final class Change {
            final int x0,y0,w,h;
            final boolean[] pixels;
            final int inkDelta,photoDelta,changedIndex;
            final float[] newBounds,newSegmentBoxes;
            Change(int x,int y,int width,int height,boolean[] raster,int a,int b,
                   int changedIndex,float[] newBounds,float[] newSegmentBoxes){
                x0=x;y0=y;w=width;h=height;pixels=raster;inkDelta=a;photoDelta=b;
                this.changedIndex=changedIndex;this.newBounds=newBounds;
                this.newSegmentBoxes=newSegmentBoxes;
            }
        }
        FidelityTracker(boolean[] mask,List<float[]> loops,float[] importance,
                        boolean[] valid,int w,int h,float cell){
            if(mask.length!=w*h||importance.length!=w*h||valid.length!=w*h)
                throw new IllegalArgumentException("Fidelity dimensions differ");
            this.reference=mask;this.importance=importance;this.valid=valid;
            this.w=w;this.h=h;this.cell=cell;
            bounds=new float[loops.size()][];
            segmentBoxes=new float[loops.size()][];
            for(int i=0;i<loops.size();i++){
                bounds[i]=bounding(loops.get(i));
                segmentBoxes[i]=boundaryBlocks(loops.get(i));
            }
            safetyIndex=new ContourBoundsIndex(bounds);
            current=rasterize(loops,w,h,cell);
            if(current==null)throw new IllegalArgumentException("Invalid baseline fill");
            int i=0,p=0,il=0,pl=0;
            for(int k=0;k<mask.length;k++){
                if(!valid[k]||!mask[k])continue;
                i++;if(!current[k])il++;
                if(importance[k]>=.52f){p++;if(!current[k])pl++;}
            }
            ink=i;photo=p;inkLost=il;photoLost=pl;
        }
        Change evaluate(List<float[]> changed,int changedIndex,float[] oldLoop,float[] newLoop){
            float[] a=bounds[changedIndex],b=bounding(newLoop);
            int x0=Math.max(0,(int)Math.floor(Math.min(a[0],b[0])/cell)-2);
            int y0=Math.max(0,(int)Math.floor(Math.min(a[1],b[1])/cell)-2);
            int x1=Math.min(w,(int)Math.ceil(Math.max(a[2],b[2])/cell)+2);
            int y1=Math.min(h,(int)Math.ceil(Math.max(a[3],b[3])/cell)+2);
            if(x1<=x0||y1<=y0)return null;
            // Temporarily expose the candidate bounds for ROI culling; commit
            // the new box only after the geometric cut checks also pass.
            bounds[changedIndex]=b;
            boolean[] proposed;
            try{proposed=rasterizeRegion(changed,w,h,cell,x0,y0,x1,y1,bounds);}
            finally{bounds[changedIndex]=a;}
            if(proposed==null)return null;
            int il=0,pl=0,ww=x1-x0,hh=y1-y0;
            for(int y=0;y<hh;y++){
                if((y&31)==0)StyleKit.checkCancelled();
                int src=(y0+y)*w+x0, row=y*ww;
                for(int x=0;x<ww;x++){
                    int idx=src+x;
                    if(!valid[idx]||!reference[idx]||current[idx]==proposed[row+x])continue;
                    int delta=proposed[row+x]?-1:1;
                    il+=delta;
                    if(importance[idx]>=.52f)pl+=delta;
                }
            }
            if(ink<=0||inkLost+il>Math.max(5,(int)(ink*.095f))||
                photoLost+pl>Math.max(4,(int)(photo*.055f)))return null;
            return new Change(x0,y0,ww,hh,proposed,il,pl,changedIndex,b,
                boundaryBlocks(newLoop));
        }
        void commit(Change delta){
            for(int y=0;y<delta.h;y++)
                System.arraycopy(delta.pixels,y*delta.w,current,(delta.y0+y)*w+delta.x0,delta.w);
            inkLost+=delta.inkDelta;photoLost+=delta.photoDelta;
            bounds[delta.changedIndex]=delta.newBounds;
            segmentBoxes[delta.changedIndex]=delta.newSegmentBoxes;
            safetyIndex.update(delta.changedIndex,delta.newBounds);
        }
    }
    /** Even-odd scan conversion restricted to a physical rectangular region.
     * Every intersecting ring contributes to parity, including nested holes;
     * only the output pixels outside the region are excluded.
     */
    static boolean[] rasterizeRegion(List<float[]> rings,int w,int h,float cell,
                                     int x0,int y0,int x1,int y1){
        return rasterizeRegion(rings,w,h,cell,x0,y0,x1,y1,null);
    }
    private static boolean[] rasterizeRegion(List<float[]> rings,int w,int h,float cell,
                                     int x0,int y0,int x1,int y1,float[][] cachedBounds){
        if(x0<0||y0<0||x1>w||y1>h||x0>=x1||y0>=y1)return null;
        int hh=y1-y0,ww=x1-x0;
        int[] count=new int[hh];
        float[][] intersections=new float[hh][];
        float yMin=(y0+.5f)*cell,yMax=(y1-.5f)*cell;
        float xMin=(x0+.5f)*cell,xMax=(x1-.5f)*cell;
        for(int ringIndex=0;ringIndex<rings.size();ringIndex++){
            float[] ring=rings.get(ringIndex);
            if(ring==null||ring.length<6||(ring.length&1)!=0)return null;
            // Cheap y-interval reject before scanning segment endpoints.
            float[] bounds=cachedBounds==null?bounding(ring):cachedBounds[ringIndex];
            if(bounds[3]<yMin||bounds[1]>yMax||bounds[2]<xMin||bounds[0]>xMax)continue;
            int n=ring.length/2;
            for(int i=0;i<n;i++){
                if((i&4095)==0)StyleKit.checkCancelled();
                int j=(i+1)%n;
                double ax=ring[2*i],ay=ring[2*i+1],bx=ring[2*j],by=ring[2*j+1];
                if(!Double.isFinite(ax)||!Double.isFinite(ay)||
                    !Double.isFinite(bx)||!Double.isFinite(by))return null;
                if(Math.abs(ay-by)<1e-10)continue;
                int lo=Math.max(y0,(int)Math.ceil(Math.min(ay,by)/cell-.5));
                int hi=Math.min(y1,(int)Math.ceil(Math.max(ay,by)/cell-.5));
                for(int y=lo;y<hi;y++){
                    int rowId=y-y0,index=count[rowId]++;
                    float[] row=intersections[rowId];
                    if(row==null){row=new float[8];intersections[rowId]=row;}
                    if(index==row.length){row=Arrays.copyOf(row,row.length*2);intersections[rowId]=row;}
                    double yp=(y+.5)*cell;
                    row[index]=(float)(ax+(yp-ay)*(bx-ax)/(by-ay));
                }
            }
        }
        boolean[] mask=new boolean[ww*hh];
        for(int yy=0;yy<hh;yy++){
            int n=count[yy];if(n==0)continue;
            if((n&1)!=0)return null;
            float[] row=intersections[yy];Arrays.sort(row,0,n);
            for(int k=0;k<n;k+=2){
                int lo=Math.max(x0,(int)Math.ceil(row[k]/cell-.5));
                int hi=Math.min(x1,(int)Math.ceil(row[k+1]/cell-.5));
                // An even-odd interval can lie completely outside the ROI;
                // clipping must SKIP it, not reject the contour as invalid.
                if(hi>lo)Arrays.fill(mask,yy*ww+lo-x0,yy*ww+hi-x0,true);
            }
        }
        return mask;
    }

    /** Select large and mid-sized loops on every sheet, independent of color.
     * Exposed package-private for regression tests: older code silently
     * considered only 18 large loops, leaving many interior cutout edges jagged.
     */
    static ArrayList<Integer> uniformContourCandidates(StyleKit.Layer layer,
            float strength,float minCut,float cell){
        ArrayList<Integer> order=new ArrayList<>();
        for(int i=0;i<layer.loops.size();i++)order.add(i);
        order.sort((a,b)->Double.compare(loopAreaAbs(layer.loops.get(b)),loopAreaAbs(layer.loops.get(a))));
        ArrayList<Integer> selected=new ArrayList<>();
        int large=Math.min(18,order.size());
        for(int i=0;i<large;i++)selected.add(order.get(i));
        // Extra medium contours get a chance to round without expanding the
        // amount of processing without limit on a complex photograph.
        int mediumBudget=strength>=.85f?12:strength>=.50f?8:5;
        double minArea=Math.max(minCut*minCut*2.0,cell*cell*10.0);
        for(int i=large;i<order.size()&&mediumBudget>0;i++){
            int id=order.get(i);
            if(loopAreaAbs(layer.loops.get(id))<minArea)continue;
            selected.add(id);mediumBudget--;
        }
        return selected;
    }

    /**
     * Boundary-only fallback used by the uniform layer pass. It is deliberately
     * shape preserving: two bounded Chaikin passes plus area restoration and a
     * very light simplification remove staircase corners without blurring away
     * the entire region.
     */
    static float[] uniformBoundaryCurve(float[] loop,float strength,float cell,
            float minCut,float widthMm,float heightMm){
        if(loop==null||loop.length<8||(loop.length&1)!=0)return null;
        int passes=strength>=.85f?2:1;
        // XY points use stride TWO; the slider selects iteration count.
        // Passing passes as stride makes 50% operate on raw scalar channels
        // and silently reject the dominant fill contour.
        float[] smooth=StyleKit.chaikin(loop,2,true,passes);
        smooth=restoreArea(loop,smooth,widthMm,heightMm);
        if(smooth==null)return null;
        float tol=Math.max(.02f,Math.min(.11f,.02f+.05f*strength));
        smooth=SolidSnipContourRepair.simplifyClosed(smooth,tol);
        smooth=StyleKit.removeCollinear(smooth,Math.max(.01f,Math.min(.05f,.012f+.02f*strength)));
        if(smooth.length<6)return null;
        double a=Math.abs(StyleKit.signedArea(loop)),b=Math.abs(StyleKit.signedArea(smooth));
        if(!Double.isFinite(a)||!Double.isFinite(b)||a<1e-8)return null;
        if(Math.abs(b-a)/a>(.040+.030*strength))return null;
        if(Math.min(width(loop),height(loop))<Math.max(minCut*.85f,cell*1.4f))return null;
        if(!withinCurveDistance(smooth,loop,Math.max(minCut*.70f,cell*1.6f))||
           !withinCurveDistance(loop,smooth,Math.max(minCut*.85f,cell*1.9f)))return null;
        if(StyleKit.signedArea(loop)*StyleKit.signedArea(smooth)<=0||StyleKit.selfIntersections(smooth,1)>0)return null;
        return smooth;
    }
    private static float width(float[] ring){
        float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
        for(int i=0;i<ring.length;i+=2){min=Math.min(min,ring[i]);max=Math.max(max,ring[i]);}
        return max-min;
    }
    private static float height(float[] ring){
        float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
        for(int i=1;i<ring.length;i+=2){min=Math.min(min,ring[i]);max=Math.max(max,ring[i]);}
        return max-min;
    }

    /** Subpixel physical curve, not a Gaussian filtering of filled-color masks. */
    private static float[] localCurve(float[] loop,float strength,float cell,float minCut,
                                      float widthMm,float heightMm){
        if(loop.length<6 || (loop.length&1)!=0)return null;
        final int n=loop.length/2;
        if(n>14000)return null;
        float minX=Float.MAX_VALUE,minY=Float.MAX_VALUE;
        float maxX=-Float.MAX_VALUE,maxY=-Float.MAX_VALUE;
        for(int i=0;i<loop.length;i+=2){
            minX=Math.min(minX,loop[i]);minY=Math.min(minY,loop[i+1]);
            maxX=Math.max(maxX,loop[i]);maxY=Math.max(maxY,loop[i+1]);
        }
        // Do not change sliver geometry that would be physically fragile.
        if(Math.min(maxX-minX,maxY-minY)<Math.max(minCut*.85,cell*1.55))return null;
        ArrayList<double[]> units=new ArrayList<>(n);
        for(int i=0;i<loop.length;i+=2)
            units.add(new double[]{loop[i]/cell,loop[i+1]/cell});
        float[] rounded=fitCurveLoop(units,cell,widthMm,heightMm,strength,minCut);
        // Short contours are often ineligible for a multi-anchor cubic fit.
        // A conservative Chaikin pass still removes their pixel-grid corners.
        if(rounded==null){
            rounded=StyleKit.chaikin(loop,2,true,1);
            // Chaikin shrinks closed islands. Restore their original physical
            // area before comparing them against tiny eye/nose features.
            rounded=restoreArea(loop,rounded,widthMm,heightMm);
        }
        if(rounded==null||rounded.length<6)return null;
        double a=StyleKit.signedArea(loop),b=StyleKit.signedArea(rounded);
        if(!Double.isFinite(a)||!Double.isFinite(b)||a*b<=0||
            Math.abs(b-a)>Math.abs(a)*(.055+.045*strength))return null;
        if(StyleKit.selfIntersections(rounded,1)>0)return null;
        return rounded;
    }

    private static float[] restoreArea(float[] baseline,float[] rounded,
                                       float widthMm,float heightMm){
        if(rounded==null)return null;
        double before=Math.abs(StyleKit.signedArea(baseline));
        double after=Math.abs(StyleKit.signedArea(rounded));
        if(before<1e-8||after<1e-8)return null;
        double scale=Math.sqrt(before/after);
        if(scale<.88||scale>1.20)return null;
        double cx=0,cy=0;int n=rounded.length/2;
        for(int k=0;k<n;k++){cx+=rounded[2*k];cy+=rounded[2*k+1];}
        cx/=n;cy/=n;
        float[] candidate=rounded.clone();
        for(int k=0;k<n;k++){
            double x=cx+(candidate[2*k]-cx)*scale;
            double y=cy+(candidate[2*k+1]-cy)*scale;
            if(x<0||y<0||x>widthMm||y>heightMm)return null;
            candidate[2*k]=(float)x;candidate[2*k+1]=(float)y;
        }
        return candidate;
    }

    /** Isolated candidate-sheet checks, with recursive reversion of bad loops. */
    private static void acceptCurveGroups(StyleKit.Result result,int sheet,
            ArrayList<Integer> eligible,float[][] proposals,int lo,int hi,
            PatternPortraitTrioEngine.Surface source,float minCut,float gap,
            float[] importance,int[] remaining,FidelityTracker layerFidelity){
        if(lo>=hi||remaining[0]--<=0)return;
        StyleKit.checkCancelled();
        StyleKit.Layer target=result.layers.get(sheet);
        float[][] old=new float[hi-lo][];
        for(int k=lo;k<hi;k++){
            int id=eligible.get(k);
            old[k-lo]=target.loops.get(id);
            target.loops.set(id,proposals[id]);
        }
        if(safeCandidate(result,sheet,source,minCut,gap,importance,
            hi-lo==1?eligible.get(lo):-1,hi-lo==1?old[0]:null,layerFidelity))return;
        for(int k=lo;k<hi;k++)target.loops.set(eligible.get(k),old[k-lo]);
        if(hi-lo==1)return;
        int mid=(lo+hi)/2;
        acceptCurveGroups(result,sheet,eligible,proposals,lo,mid,source,
            minCut,gap,importance,remaining,layerFidelity);
        acceptCurveGroups(result,sheet,eligible,proposals,mid,hi,source,
            minCut,gap,importance,remaining,layerFidelity);
    }
    /**
     * An existing defect elsewhere in a sheet cannot be a reason to reject a
     * safe curve. First try strict full-sheet preflight. If the unsmoothed
     * sheet already contains a warning, compare the SINGLE changed contour
     * and its neighbors inside the same mm bounding envelope. Allow only a
     * non-worsening local change; report existing defects normally at export.
     */
    private static boolean safeCandidate(StyleKit.Result result,int layer,
            PatternPortraitTrioEngine.Surface source,float minCut,float gap,
            float[] importance,int changedIndex,float[] before,FidelityTracker layerFidelity){
        StyleKit.Layer target=result.layers.get(layer);
        // Compact sheets can be checked strictly in one pass. This retains
        // global protection for large smoothable ribbons next to small holes.
        if(target.loops.size()<=8){
            if(!preservesPhotoFeatures(source.ink[layer],target.loops,importance,
                source.valid,source.w,source.h,source.mm))return false;
            try{
                StyleKit.Result isolated=new StyleKit.Result(result.widthMm,result.heightMm);
                isolated.layers.add(target);
                return StyleCutGeometryPreflight.inspect(isolated,minCut,gap,1).isEmpty();
            }catch(RuntimeException invalid){return false;}
        }
        // For a busy layer, try one contour at a time. A pre-existing unsafe
        // gap elsewhere cannot veto a non-worsening improvement here.
        if(changedIndex<0||before==null)return false;
        final FidelityTracker.Change delta=layerFidelity==null?null:
            layerFidelity.evaluate(target.loops,changedIndex,before,target.loops.get(changedIndex));
        if(layerFidelity==null){
            if(!preservesPhotoFeatures(source.ink[layer],target.loops,importance,
                source.valid,source.w,source.h,source.mm))return false;
        }else if(delta==null)return false;
        try{
            if(!localNonRegression(target,changedIndex,before,minCut,gap,
                result.widthMm,result.heightMm,
                layerFidelity==null?null:layerFidelity.bounds,
                layerFidelity==null?null:layerFidelity.segmentBoxes,
                layerFidelity==null?null:layerFidelity.safetyIndex))return false;
            if(layerFidelity!=null)layerFidelity.commit(delta);
            return true;
        }catch(RuntimeException invalid){return false;}
    }

    static boolean localNonRegression(StyleKit.Layer target,int changed,float[] original,
            float minCut,float gap,float widthMm,float heightMm){
        return localNonRegression(target,changed,original,minCut,gap,widthMm,heightMm,null,null,null);
    }
    private static boolean localNonRegression(StyleKit.Layer target,int changed,float[] original,
            float minCut,float gap,float widthMm,float heightMm,float[][] cachedBounds,
            float[][] cachedSegmentBoxes,ContourBoundsIndex index){
        float[] candidate=target.loops.get(changed);
        if(!sameTopologyAndDeviation(original,candidate,Math.max(minCut*.52f,gap*.70f)))return false;
        // The important fast path: if no other contour boundary can touch the
        // changed boundary within the physical guard band, only the changed
        // contour needs geometry inspection. Make the isolated check stricter
        // by using max(width,gap) for BOTH materials, so omitted enclosing
        // loops cannot weaken hole/interior classification.
        if(cachedSegmentBoxes!=null&&changedOnlyClean(target,changed,original,
                minCut,gap,widthMm,heightMm,cachedBounds,cachedSegmentBoxes,index))return true;
        float[] extent=bounding(original);
        float[] other=bounding(candidate);
        for(int k=0;k<4;k++){
            extent[k]=k<2?Math.min(extent[k],other[k]):Math.max(extent[k],other[k]);
        }
        float padding=Math.max(minCut,gap)*1.3f+.4f;
        extent[0]-=padding;extent[1]-=padding;extent[2]+=padding;extent[3]+=padding;
        StyleKit.Layer oldLocal=new StyleKit.Layer("original-local",target.color);
        StyleKit.Layer newLocal=new StyleKit.Layer("candidate-local",target.color);
        for(int i:neighbors(index,extent,changed,target.loops.size())){
            float[] ring=target.loops.get(i);
            if(i!=changed&&!overlaps(extent,cachedBounds==null?bounding(ring):cachedBounds[i]))continue;
            oldLocal.loops.add(i==changed?original:ring);
            newLocal.loops.add(ring);
        }
        StyleKit.Result oldPage=new StyleKit.Result(widthMm,heightMm);
        StyleKit.Result newPage=new StyleKit.Result(widthMm,heightMm);
        oldPage.layers.add(oldLocal);newPage.layers.add(newLocal);
        // Never accept a candidate that introduces a severe intersection,
        // endpoint contact or unverified region. Existing narrow clearances
        // are compared against their original locations, not treated as a
        // blanket prohibition on improving an unrelated boundary.
        final int limit=256;
        // The candidate is the only changed geometry. A fully clean local
        // candidate is always non-worsening, so skip the second full inspection.
        // For nonempty candidate warnings keep the original differential checks.
        List<StyleCutGeometryPreflight.Warning> newRisk=
            StyleCutGeometryPreflight.inspect(newPage,minCut,gap,limit);
        if(newRisk.isEmpty())return true;
        List<StyleCutGeometryPreflight.Warning> oldRisk=
            StyleCutGeometryPreflight.inspect(oldPage,minCut,gap,limit);
        if(oldRisk.size()==limit||newRisk.size()==limit)return false;
        if(newRisk.size()>oldRisk.size())return false;
        // Existing narrow necks do not prohibit smoothing unrelated contours.
        // However, a new contact or intersection is categorically unacceptable.
        int oldCritical=0,newCritical=0;
        for(StyleCutGeometryPreflight.Warning warning:oldRisk)
            if(isCritical(warning.reason))oldCritical++;
        for(StyleCutGeometryPreflight.Warning warning:newRisk)
            if(isCritical(warning.reason))newCritical++;
        if(newCritical>oldCritical)return false;
        // An old endpoint contact elsewhere is not permission for a new
        // intersection at this contour. Severe findings must match locally.
        for(StyleCutGeometryPreflight.Warning warning:newRisk){
            if(!isCritical(warning.reason))continue;
            boolean matched=false;
            for(StyleCutGeometryPreflight.Warning prior:oldRisk){
                if(!warning.reason.equals(prior.reason))continue;
                if(Math.hypot(warning.xMm-prior.xMm,
                    warning.yMm-prior.yMm)<=Math.max(.7f,minCut*1.15f)){
                    matched=true;break;
                }
            }
            if(!matched)return false;
        }
        return true;
    }
    /** A loop's segment boxes, grouped by 24 edges, including the closing
     * segment. They are cheap to cache, and do not make approximations to the
     * underlying cut predicates: a box is only used to prove separation.
     */
    private static float[] boundaryBlocks(float[] loop){
        final int count=loop.length/2,block=24;
        float[] boxes=new float[4*((count+block-1)/block)];
        int write=0;
        for(int first=0;first<count;first+=block){
            float x0=Float.POSITIVE_INFINITY,y0=x0,x1=Float.NEGATIVE_INFINITY,y1=x1;
            for(int i=first;i<Math.min(count,first+block);i++){
                int j=(i+1)%count;
                float ax=loop[2*i],ay=loop[2*i+1],bx=loop[2*j],by=loop[2*j+1];
                x0=Math.min(x0,Math.min(ax,bx));y0=Math.min(y0,Math.min(ay,by));
                x1=Math.max(x1,Math.max(ax,bx));y1=Math.max(y1,Math.max(ay,by));
            }
            boxes[write++]=x0;boxes[write++]=y0;
            boxes[write++]=x1;boxes[write++]=y1;
        }
        return boxes;
    }
    private static boolean boxesNear(float[] a,float[] b,float reach){
        for(int i=0;i<a.length;i+=4){
            for(int j=0;j<b.length;j+=4){
                if(a[i]<=b[j+2]+reach&&b[j]<=a[i+2]+reach&&
                   a[i+1]<=b[j+3]+reach&&b[j+1]<=a[i+3]+reach)return true;
            }
        }
        return false;
    }
    /** Fast acceptance for an isolated changed boundary. This is intentionally
     * one-directional: uncertain proximity falls back to differential preflight.
     * Do not treat an enclosing loop's bounding rectangle as its CUT EDGE.
     */
    private static boolean changedOnlyClean(StyleKit.Layer target,int changed,
            float[] original,float minCut,float gap,float widthMm,float heightMm,
            float[][] cachedBounds,float[][] cachedSegmentBoxes,ContourBoundsIndex index){
        final float[] candidate=target.loops.get(changed);
        final float[] oldBlocks=cachedSegmentBoxes[changed];
        final float[] newBlocks=boundaryBlocks(candidate);
        final float[] a=bounding(original),b=bounding(candidate);
        final float reach=Math.max(minCut,gap)*2.0f+.40f;
        float[] envelope={Math.min(a[0],b[0])-reach,Math.min(a[1],b[1])-reach,
                          Math.max(a[2],b[2])+reach,Math.max(a[3],b[3])+reach};
        for(int i:neighbors(index,envelope,changed,target.loops.size())){
            if(i==changed)continue;
            if((i&63)==0)StyleKit.checkCancelled();
            float[] otherBox=cachedBounds==null?bounding(target.loops.get(i)):cachedBounds[i];
            if(!overlaps(envelope,otherBox))continue;
            float[] other=cachedSegmentBoxes[i];
            if(boxesNear(oldBlocks,other,reach)||boxesNear(newBlocks,other,reach))return false;
        }
        try{
            StyleKit.Result isolated=new StyleKit.Result(widthMm,heightMm);
            StyleKit.Layer cut=new StyleKit.Layer(target.name,target.color);
            cut.loops.add(candidate);isolated.layers.add(cut);
            float stricter=Math.max(minCut,gap);
            if(!StyleCutGeometryPreflight.inspect(isolated,stricter,stricter,1).isEmpty())return false;
            CHANGED_ONLY_ACCEPTS.incrementAndGet();
            return true;
        }catch(RuntimeException invalid){return false;}
    }

    private static int[] neighbors(ContourBoundsIndex index,float[] extent,int changed,int count){
        if(index!=null)return index.query(extent,changed);
        int[] ids=new int[count];for(int i=0;i<count;i++)ids[i]=i;return ids;
    }

    private static boolean isCritical(String reason){
        return reason.contains("Intersect")||reason.contains("Touching")||
            reason.contains("Overlapping")||reason.contains("not verified")||
            reason.contains("exceeded complexity");
    }

    private static float[] bounding(float[] ring){
        float a=Float.POSITIVE_INFINITY,b=a,c=Float.NEGATIVE_INFINITY,d=c;
        for(int i=0;i<ring.length;i+=2){
            a=Math.min(a,ring[i]);b=Math.min(b,ring[i+1]);
            c=Math.max(c,ring[i]);d=Math.max(d,ring[i+1]);
        }
        return new float[]{a,b,c,d};
    }
    private static boolean overlaps(float[] a,float[] b){
        return a[0]<=b[2]&&b[0]<=a[2]&&a[1]<=b[3]&&b[1]<=a[3];
    }
    /** Bound the physical movement in BOTH directions, not just the area. */
    private static boolean sameTopologyAndDeviation(float[] before,float[] after,float shift){
        if(before.length<6||after.length<6||
           StyleKit.signedArea(before)*StyleKit.signedArea(after)<=0||
           StyleKit.selfIntersections(after,1)>0)return false;
        if(!withinCurveDistance(after,before,shift)||!withinCurveDistance(before,after,shift))return false;
        double a=Math.abs(StyleKit.signedArea(before)),b=Math.abs(StyleKit.signedArea(after));
        return a>1e-8&&Math.abs(b-a)/a<.10;
    }
    private static boolean withinCurveDistance(float[] sample,float[] segments,float limit){
        double max2=limit*limit;
        int edges=segments.length/2;
        // Bound the work on very dense contours; every 3rd sample is enough to
        // detect large displacements, while all candidate segments are checked.
        int stride=Math.max(1,sample.length/2/240);
        for(int i=0;i<sample.length;i+=2*stride){
            double px=sample[i],py=sample[i+1];double nearest=Double.POSITIVE_INFINITY;
            for(int j=0;j<edges;j++){
                int k=(j+1)%edges;
                double ax=segments[2*j],ay=segments[2*j+1];
                double bx=segments[2*k],by=segments[2*k+1];
                double dx=bx-ax,dy=by-ay;
                double denom=dx*dx+dy*dy;
                double t=denom<1e-12?0:Math.max(0,Math.min(1,((px-ax)*dx+(py-ay)*dy)/denom));
                double ex=px-ax-dx*t,ey=py-ay-dy*t;
                nearest=Math.min(nearest,ex*ex+ey*ey);
                if(nearest<=max2)break;
            }
            if(nearest>max2)return false;
        }
        return true;
    }

    /**
     * Retain meaningful photographic mask coverage after curve fitting. Pure
     * polygon area checks allowed a large region to swallow small eye/nose
     * openings while keeping roughly the same total material area. Instead,
     * scan-convert the candidate back to the SAME pixel-centre grid and measure
     * how much source material would actually disappear, especially at original
     * photographic edges. Reject the candidate and keep existing safe curves
     * if either local photo support or total material preservation fails.
     */
    static boolean preservesPhotoFeatures(boolean[] reference,List<float[]> loops,
            float[] importance,boolean[] valid,int w,int h,float cell){
        if(reference.length!=w*h||valid.length!=w*h||importance.length!=w*h)return false;
        boolean[] candidate=rasterize(loops,w,h,cell);
        if(candidate==null)return false;
        int photo=0,photoLost=0,ink=0,inkLost=0;
        for(int i=0;i<reference.length;i++){
            if(!valid[i]||!reference[i])continue;
            ink++;
            if(!candidate[i])inkLost++;
            if(importance[i]>=.52f){
                photo++;
                if(!candidate[i])photoLost++;
            }
        }
        if(ink==0)return false;
        return inkLost<=Math.max(5,(int)(ink*.095f))&&
               photoLost<=Math.max(4,(int)(photo*.055f));
    }

    /** Even-odd scan conversion at physical millimetre pixel centres. */
    static boolean[] rasterize(List<float[]> rings,int w,int h,float cell){
        int[] count=new int[h];
        float[][] intersections=new float[h][];
        for(float[] ring:rings){
            if(ring==null||ring.length<6||(ring.length&1)!=0)return null;
            int n=ring.length/2;
            for(int i=0;i<n;i++){
                if((i&4095)==0)StyleKit.checkCancelled();
                int j=(i+1)%n;
                double ax=ring[2*i],ay=ring[2*i+1],
                       bx=ring[2*j],by=ring[2*j+1];
                if(!Double.isFinite(ax)||!Double.isFinite(ay)||
                   !Double.isFinite(bx)||!Double.isFinite(by))return null;
                if(Math.abs(ay-by)<1e-10)continue;
                int lo=Math.max(0,(int)Math.ceil(Math.min(ay,by)/cell-.5));
                int hi=Math.min(h,(int)Math.ceil(Math.max(ay,by)/cell-.5));
                for(int y=lo;y<hi;y++){
                    float[] row=intersections[y];
                    if(row==null){row=new float[8];intersections[y]=row;}
                    int index=count[y]++;
                    if(index==row.length){row=Arrays.copyOf(row,row.length*2);intersections[y]=row;}
                    double yp=(y+.5)*cell;
                    row[index]=(float)(ax+(yp-ay)*(bx-ax)/(by-ay));
                }
            }
        }
        boolean[] out=new boolean[w*h];
        for(int y=0;y<h;y++){
            if((y&63)==0)StyleKit.checkCancelled();
            int n=count[y];if(n==0)continue;
            if((n&1)!=0)return null;
            float[] row=intersections[y];Arrays.sort(row,0,n);
            for(int k=0;k<n;k+=2){
                int lo=Math.max(0,(int)Math.ceil(row[k]/cell-.5));
                int hi=Math.min(w,(int)Math.ceil(row[k+1]/cell-.5));
                if(hi<lo)return null;
                Arrays.fill(out,y*w+lo,y*w+hi,true);
            }
        }
        return out;
    }

    private static float[] rawLoop(ArrayList<double[]> ring,float cell,float widthMm,float heightMm){
        if(ring==null||ring.size()<3)return null;
        float[] xy=new float[ring.size()*2];
        for(int i=0;i<ring.size();i++){
            double px=ring.get(i)[0]*cell, py=ring.get(i)[1]*cell;
            if(!Double.isFinite(px)||!Double.isFinite(py))return null;
            xy[i*2]=(float)Math.max(0,Math.min(widthMm,px));
            xy[i*2+1]=(float)Math.max(0,Math.min(heightMm,py));
        }
        return xy;
    }

    /**
     * Convert a subpixel contour into a genuinely rounded vector loop.
     * Large stable regions use Gaussian-resampled Catmull-Rom curves that are
     * flattened back into cuttable polygon points. Tiny or fragile regions fall
     * back to the raw subpixel polygon rather than staircase pixel edges.
     */
    private static float[] fitCurveLoop(ArrayList<double[]> ring,float cell,float widthMm,float heightMm,float f,float minCut){
        if(ring==null||ring.size()<8||ring.size()>24000)return null;
        ArrayList<P> original=new ArrayList<>(ring.size());
        double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY;
        double minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
        for(double[] xy:ring){
            P p=new P(xy[0]*cell,xy[1]*cell);
            original.add(p);
            minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);
            minY=Math.min(minY,p.y);maxY=Math.max(maxY,p.y);
        }
        double span=Math.min(maxX-minX,maxY-minY);
        double sigmaMm=.16 + .28*f*f;
        if(span<Math.max(1.15,2.4*sigmaMm))return null; // too small/fragile to curve-fit

        int n=original.size();
        double perimeter=0;
        double[] cumulative=new double[n+1];
        for(int i=0;i<n;i++){
            perimeter+=dist(original.get(i),original.get((i+1)%n));
            cumulative[i+1]=perimeter;
        }
        if(!Double.isFinite(perimeter)||perimeter<cell*10)return null;
        double sampleStep=Math.max(.035,Math.min(cell*.45,.20));
        int count=(int)Math.ceil(perimeter/sampleStep);
        if(count<18||count>24000)return null;
        sampleStep=perimeter/count;
        P[] samples=new P[count];
        int edge=0;
        for(int k=0;k<count;k++){
            double along=k*sampleStep;
            while(edge+1<n && cumulative[edge+1]<along)edge++;
            double len=cumulative[edge+1]-cumulative[edge];
            double t=len<=1e-9?0:(along-cumulative[edge])/len;
            samples[k]=mix(original.get(edge),original.get((edge+1)%n),t);
        }
        int reach=(int)Math.ceil(2.6*sigmaMm/sampleStep);
        if(reach<1||reach>=count/6)return null;
        double[] weight=new double[reach+1];double weightSum=0;
        for(int j=0;j<=reach;j++){
            weight[j]=Math.exp(-.5*Math.pow(j*sampleStep/Math.max(.001,sigmaMm),2));
            weightSum+=j==0?weight[j]:2*weight[j];
        }
        P[] smooth=new P[count];
        for(int k=0;k<count;k++){
            if((k&1023)==0)StyleKit.checkCancelled();
            double x=samples[k].x*weight[0], y=samples[k].y*weight[0];
            for(int j=1;j<=reach;j++){
                P a=samples[(k-j+count)%count], b=samples[(k+j)%count];
                x+=weight[j]*(a.x+b.x); y+=weight[j]*(a.y+b.y);
            }
            smooth[k]=new P(x/weightSum,y/weightSum);
        }
        int halfway=count/2;
        boolean[] keep=new boolean[count]; keep[0]=true; keep[halfway]=true;
        double tolerance=.095 + .26*f*f;
        rdp(smooth,0,halfway,tolerance*tolerance,keep,0);
        rdp(smooth,halfway,count,tolerance*tolerance,keep,0);
        ArrayList<P> anchors=new ArrayList<>();
        for(int k=0;k<count;k++)if(keep[k])anchors.add(smooth[k]);
        int m=anchors.size();
        if(m<6||m>12000)return null;

        ArrayList<Float> pts=new ArrayList<>(m*6);
        P start=anchors.get(0);
        pts.add((float)clamp(start.x,0,widthMm)); pts.add((float)clamp(start.y,0,heightMm));
        for(int i=0;i<m;i++){
            if((i&1023)==0)StyleKit.checkCancelled();
            P a=anchors.get((i-1+m)%m), b=anchors.get(i), c=anchors.get((i+1)%m), d=anchors.get((i+2)%m);
            double chord=dist(b,c);
            if(chord<1e-9)continue;
            double control=Math.min(chord*.28, Math.min(dist(a,c),dist(b,d))*.12);
            double len1=Math.max(1e-9,dist(a,c)), len2=Math.max(1e-9,dist(b,d));
            P c1=new P(b.x+(c.x-a.x)/len1*control,b.y+(c.y-a.y)/len1*control);
            P c2=new P(c.x-(d.x-b.x)/len2*control,c.y-(d.y-b.y)/len2*control);
            int steps=Math.max(3,Math.min(48,(int)Math.ceil(chord/Math.max(.10,cell*.26))));
            for(int t=1;t<=steps;t++){
                double u=t/(double)steps, v=1-u;
                double px=v*v*v*b.x+3*v*v*u*c1.x+3*v*u*u*c2.x+u*u*u*c.x;
                double py=v*v*v*b.y+3*v*v*u*c1.y+3*v*u*u*c2.y+u*u*u*c.y;
                pts.add((float)clamp(px,0,widthMm));
                pts.add((float)clamp(py,0,heightMm));
            }
        }
        if(pts.size()<6)return null;
        float[] xy=new float[pts.size()];
        for(int i=0;i<pts.size();i++)xy[i]=pts.get(i);
        xy=SolidSnipContourRepair.simplifyClosed(xy,Math.max(.03f,Math.min(.18f,(float)(tolerance*.22))));
        xy=StyleKit.removeCollinear(xy,Math.max(.01f,Math.min(.08f,(float)(tolerance*.08))));
        if(xy.length<6)return null;
        double area=Math.abs(StyleKit.signedArea(xy));
        if(!Double.isFinite(area)||area<Math.max(cell*cell*.75,minCut*minCut*.15))return null;
        return xy;
    }

    private static double dist(P a,P b){ return Math.hypot(a.x-b.x,a.y-b.y); }
    private static P mix(P a,P b,double t){ return new P(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t); }
    private static double clamp(double v,double lo,double hi){ return Math.max(lo,Math.min(hi,v)); }

    private static void rdp(P[] a,int lo,int hi,double threshold,boolean[] keep,int depth){
        if(hi-lo<2||depth>30)return;
        P from=a[lo%a.length], to=a[hi%a.length];
        double len2=Math.pow(to.x-from.x,2)+Math.pow(to.y-from.y,2);
        int far=-1; double max=threshold;
        for(int i=lo+1;i<hi;i++){
            P p=a[i%a.length]; double dist2;
            if(len2<1e-12)dist2=Math.pow(p.x-from.x,2)+Math.pow(p.y-from.y,2);
            else{
                double u=Math.max(0,Math.min(1,((p.x-from.x)*(to.x-from.x)+(p.y-from.y)*(to.y-from.y))/len2));
                dist2=Math.pow(p.x-from.x-u*(to.x-from.x),2)+Math.pow(p.y-from.y-u*(to.y-from.y),2);
            }
            if(dist2>max){ max=dist2; far=i; }
        }
        if(far>=0){ keep[far%a.length]=true; rdp(a,lo,far,threshold,keep,depth+1); rdp(a,far,hi,threshold,keep,depth+1); }
    }

    private static void simplifyLayer(StyleKit.Result result,int layer,float minCut,float gap,float f){
        StyleKit.Layer base=result.layers.get(layer);
        int before=base.pointCount();
        if(before<12)return;
        float target=.065f+.22f*f*f; // bounded to protect high-frequency detail
        for(int attempt=0;attempt<3;attempt++){
            StyleKit.checkCancelled();
            float tolerance=target/(1<<attempt);
            StyleKit.Layer candidate=new StyleKit.Layer(base.name,base.color);
            int after=0;
            for(float[] loop:base.loops){
                float[] simpler=loop.length>=10?SolidSnipContourRepair.simplifyClosed(loop,tolerance):loop;
                if(simpler.length>=6 && simpler.length<=loop.length){
                    double originalArea=StyleKit.signedArea(loop),newArea=StyleKit.signedArea(simpler);
                    if(!Double.isFinite(originalArea)||!Double.isFinite(newArea)||
                       originalArea*newArea<=0 ||
                       Math.abs(newArea-originalArea)>Math.abs(originalArea)*(.010+.022*f) ||
                       StyleKit.selfIntersections(simpler,1)>0)simpler=loop;
                }else simpler=loop;
                candidate.loops.add(simpler); after+=simpler.length/2;
            }
            if(after>=before)continue;
            StyleKit.Result isolated=new StyleKit.Result(result.widthMm,result.heightMm);
            isolated.layers.add(candidate);
            try{
                if(!StyleCutGeometryPreflight.inspect(isolated,minCut,gap,1).isEmpty())continue;
            }catch(RuntimeException invalid){continue;}
            base.loops.clear(); base.loops.addAll(candidate.loops); return;
        }
    }

    // ------------------------------------------------------------------ M5
    /**
     * M5 raster de-staircasing for organic patch styles (Woodland / Desert / Urban camo).
     * Marching squares on a binary mask only emits 0, 45 and 90 degree steps, so a
     * shallow organic edge is cut as a staircase of pixel steps. Every overlay contour of
     * every sheet (never L1) gets the same treatment, with bounded, verifiable movement:
     *  - arc-length Gaussian smoothing of the traced polyline (sigma 0.8-2.4 px by strength);
     *  - no point moves more than half a raster pixel (the staircase's own quantisation
     *    error), so widths, gaps, holes and nesting stay those of the conditioned mask;
     *  - samples within ~1.5 px of the Snip or artboard edge are pinned exactly, so an
     *    overlay never moves past the L1 foundation outline;
     *  - genuine corners (turn > 50 degrees over +/-2.5 px) are pinned, so angular Urban
     *    breakup and patch junctions stay sharp;
     *  - no sample moves INTO the vinyl (material-preserving), and the result is simplified
     *    to <= min(0.02 mm, 0.1 px) deviation, so newly uncovered area is at most a 0.02 mm band.
     * Each loop must keep its orientation, stay simple and stay inside the half-pixel area
     * band; each sheet must not gain any cut-inspector finding. Loops near a new finding
     * are reverted, and the whole sheet is reverted if a finding remains.
     * Returns the number of contours changed.
     */
    static int destairOrganicContours(StyleKit.Result result,boolean[] valid,int w,int h,float mm,
                                      float minCut,float gap,int strength){
        return destairOrganicContours(result,valid,null,w,h,mm,minCut,gap,strength);
    }
    /** @param ink per-slot raster masks the sheets were traced from (null: treat every
     *  inward move as unbacked, i.e. smoothing is material-preserving everywhere). */
    static int destairOrganicContours(StyleKit.Result result,boolean[] valid,boolean[][] ink,int w,int h,float mm,
                                      float minCut,float gap,int strength){
        if(strength<=0||result==null)return 0;
        if(strength>MAX||valid==null||valid.length!=w*h||!(mm>0))throw new IllegalArgumentException("Invalid contour smoothing");
        float sigmaPx=.8f+1.6f*strength/100f;
        int changed=0;
        for(int li=1;li<result.layers.size();li++){
            StyleKit.checkCancelled();
            StyleKit.Layer layer=result.layers.get(li);
            if(layer.loops.isEmpty()||layer.vectorGeometryLocked)continue;
            ArrayList<float[]> before=new ArrayList<>(layer.loops);
            ArrayList<float[]> after=new ArrayList<>(before.size());
            boolean[] hole=evenOddHoles(before);
            // Vinyl of this sheet that another overlay sheet also covers: uncovering it
            // shows that sheet, never the L1 foundation.
            boolean[] backed=new boolean[w*h];
            if(ink!=null)for(int o=1;o<ink.length&&o<result.layers.size();o++){
                if(o==li||ink[o]==null||ink[o].length!=w*h)continue;
                boolean[] m=ink[o];for(int k=0;k<m.length;k++)if(m[k])backed[k]=true;
            }
            int moved=0;
            for(int i=0;i<before.size();i++){
                if((i&63)==0)StyleKit.checkCancelled();
                float[] raw=before.get(i);
                float[] c=destairLoop(raw,hole[i],valid,backed,w,h,mm,sigmaPx,result.widthMm,result.heightMm);
                if(c==null)c=raw;else moved++;
                after.add(c);
            }
            if(moved==0)continue;
            List<StyleCutGeometryPreflight.Warning> old;
            try{old=isolatedInspect(result,before,minCut,gap);}
            catch(IllegalArgumentException unverifiable){continue;} // leave it to export review
            List<StyleCutGeometryPreflight.Warning> fresh=newFindings(result,after,old,minCut,gap);
            if(fresh!=null&&!fresh.isEmpty()){
                // Revert only loops near the new findings, then re-verify once.
                float pad=2f*Math.max(minCut,gap)+mm;
                for(int i=0;i<after.size();i++){
                    if(after.get(i)==before.get(i))continue;
                    float[] b=bounding(after.get(i));
                    for(StyleCutGeometryPreflight.Warning x:fresh)
                        if(x.xMm>=b[0]-pad&&x.xMm<=b[2]+pad&&x.yMm>=b[1]-pad&&x.yMm<=b[3]+pad){after.set(i,before.get(i));moved--;break;}
                }
                fresh=newFindings(result,after,old,minCut,gap);
            }
            if(fresh==null||!fresh.isEmpty())continue; // keep the original sheet
            layer.loops.clear();layer.loops.addAll(after);
            changed+=moved;
        }
        return changed;
    }
    private static List<StyleCutGeometryPreflight.Warning> isolatedInspect(StyleKit.Result result,List<float[]> loops,float minCut,float gap){
        StyleKit.Result one=new StyleKit.Result(result.widthMm,result.heightMm);
        StyleKit.Layer copy=new StyleKit.Layer("destair",0xff000000);copy.loops.addAll(loops);one.layers.add(copy);
        return StyleCutGeometryPreflight.inspect(one,minCut,gap,100000);
    }
    /** Candidate findings not already present (within one feature size) on the original
     * sheet; null when the candidate cannot be inspected (invalid geometry). */
    private static List<StyleCutGeometryPreflight.Warning> newFindings(StyleKit.Result result,List<float[]> after,
            List<StyleCutGeometryPreflight.Warning> old,float minCut,float gap){
        List<StyleCutGeometryPreflight.Warning> now;
        try{now=isolatedInspect(result,after,minCut,gap);}
        catch(IllegalArgumentException invalid){return null;}
        ArrayList<StyleCutGeometryPreflight.Warning> fresh=new ArrayList<>();
        float near=Math.max(minCut,gap);
        for(StyleCutGeometryPreflight.Warning x:now){
            boolean known=false;
            for(StyleCutGeometryPreflight.Warning y:old)
                if(Math.abs(x.xMm-y.xMm)<=near&&Math.abs(x.yMm-y.yMm)<=near){known=true;break;}
            if(!known)fresh.add(x);
        }
        return fresh;
    }
    /** One contour; null when unchanged or when the candidate fails a local check. */
    static float[] destairLoop(float[] loop,boolean hole,boolean[] valid,boolean[] backed,int w,int h,float mm,float sigmaPx,float widthMm,float heightMm){
        int n=loop.length/2;
        if(n<4||n>20000||(loop.length&1)!=0)return null;
        // 1. Resample at <= half a pixel, keeping every original vertex.
        double step=mm*.5;
        ArrayList<double[]> pts=new ArrayList<>(n*3);
        for(int i=0;i<n;i++){
            double ax=loop[2*i],ay=loop[2*i+1];
            int j=(i+1)%n;double bx=loop[2*j],by=loop[2*j+1];
            int parts=Math.max(1,(int)Math.ceil(Math.hypot(bx-ax,by-ay)/step));
            for(int k=0;k<parts;k++){double t=k/(double)parts;pts.add(new double[]{ax+(bx-ax)*t,ay+(by-ay)*t,k==0?1:0});}
        }
        int m=pts.size();
        if(m<12||m>60000)return null;
        double[] x=new double[m],y=new double[m];boolean[] orig=new boolean[m],pin=new boolean[m];
        for(int i=0;i<m;i++){double[] p=pts.get(i);x[i]=p[0];y[i]=p[1];orig[i]=p[2]>0;}
        // 2. Pin samples near the Snip / artboard edge.
        boolean anyFree=false;
        for(int i=0;i<m;i++){
            int px=(int)Math.floor(x[i]/mm),py=(int)Math.floor(y[i]/mm);
            boolean edge=false;
            for(int yy=py-2;yy<=py+1&&!edge;yy++)for(int xx=px-2;xx<=px+1;xx++){
                if(xx<0||yy<0||xx>=w||yy>=h||!valid[yy*w+xx]){edge=true;break;}
            }
            pin[i]=edge;anyFree|=!edge;
        }
        if(!anyFree)return null;
        // 3. Pin genuine corners: turn above 50 degrees over +/-2.5 px, local maxima only.
        int k=Math.max(2,(int)Math.round(2.5*mm/step));
        if(m<=4*k)return null;
        double[] turn=new double[m];
        for(int i=0;i<m;i++){
            int a=(i-k+m)%m,b=(i+k)%m;
            double ux=x[i]-x[a],uy=y[i]-y[a],vx=x[b]-x[i],vy=y[b]-y[i];
            double nu=Math.hypot(ux,uy),nv=Math.hypot(vx,vy);
            turn[i]=nu<1e-9||nv<1e-9?0:Math.acos(Math.max(-1,Math.min(1,(ux*vx+uy*vy)/(nu*nv))));
        }
        boolean[] corner=new boolean[m];
        double limit=Math.toRadians(50);
        for(int i=0;i<m;i++){
            if(turn[i]<limit)continue;
            boolean max=true;
            for(int d=1;d<=k&&max;d++)max=turn[(i+d)%m]<turn[i]&&turn[(i-d+m)%m]<=turn[i];
            if(max){corner[i]=true;pin[i]=true;}
        }
        // Outward (away from vinyl) normal sign for the right-hand normal (ty, -tx): the
        // shoelace interior lies on the left normal when the signed area is positive.
        double signed=StyleKit.signedArea(loop);
        double outward=(signed>0?1:-1)*(hole?-1:1);
        // 4. Gaussian smoothing along the arc, never across a pinned sample.
        double sigma=sigmaPx*mm/step;int reach=(int)Math.ceil(2.5*sigma);
        if(reach*2+1>=m)return null;
        double[] wt=new double[reach+1];for(int d=0;d<=reach;d++)wt[d]=Math.exp(-.5*d*d/(sigma*sigma));
        double dmax=.5*mm;
        double[] sx=new double[m],sy=new double[m];
        boolean any=false;
        for(int i=0;i<m;i++){
            if(pin[i]){sx[i]=x[i];sy[i]=y[i];continue;}
            double sw=wt[0],ax=x[i]*wt[0],ay=y[i]*wt[0];
            for(int dir=-1;dir<=1;dir+=2){
                for(int d=1;d<=reach;d++){
                    int j=((i+dir*d)%m+m)%m;
                    sw+=wt[d];ax+=x[j]*wt[d];ay+=y[j]*wt[d];
                    if(pin[j])break;
                }
            }
            double dx=ax/sw-x[i],dy=ay/sw-y[i];
            // Never open the L1 foundation: a move INTO this sheet's vinyl is allowed only
            // where another overlay sheet also covers the vinyl being uncovered (a colour
            // edge over hidden lower vinyl). Elsewhere (abutting sheets, lone patches over
            // L1) only the outward part is kept and the step notch is filled instead.
            int pa=(i-1+m)%m,pb=(i+1)%m;
            double tx=x[pb]-x[pa],ty=y[pb]-y[pa],tl=Math.hypot(tx,ty);
            if(tl>1e-12){
                double ox=outward*ty/tl,oy=-outward*tx/tl,along=dx*ox+dy*oy;
                if(along<0){
                    double probe=-along+.35*mm; // just past the new edge position
                    int qx=(int)Math.floor((x[i]-ox*probe)/mm),qy=(int)Math.floor((y[i]-oy*probe)/mm);
                    boolean ok=backed!=null&&qx>=0&&qy>=0&&qx<w&&qy<h&&backed[qy*w+qx];
                    if(!ok){dx-=along*ox;dy-=along*oy;}
                }
            }
            double d=Math.hypot(dx,dy);
            if(d>dmax){dx*=dmax/d;dy*=dmax/d;}
            double nx=x[i]+dx,ny=y[i]+dy;
            sx[i]=nx;sy[i]=ny;if(d>1e-6)any=true;
        }
        if(!any)return null;
        // 5. Simplify between anchors (pinned original vertices and corners).
        boolean[] keep=new boolean[m];int anchors=0;
        for(int i=0;i<m;i++)if((pin[i]&&orig[i])||corner[i]){keep[i]=true;anchors++;}
        double tol=Math.min(.02,.1*mm); // RDP may cut inward by tol: keep it far below the cut tolerance
        if(anchors==0){
            keep[0]=true;int far=0;double best=-1;
            for(int i=1;i<m;i++){double d=Math.hypot(sx[i]-sx[0],sy[i]-sy[0]);if(d>best){best=d;far=i;}}
            keep[far]=true;
        }
        int first=-1;for(int i=0;i<m;i++)if(keep[i]){first=i;break;}
        int a=first;
        do{
            int b=a+1;while(!keep[b%m])b++;
            simplifySpan(sx,sy,m,a,b,tol,keep);
            a=b%m;
        }while(a!=first);
        int cnt=0;for(boolean b:keep)if(b)cnt++;
        if(cnt<3)return null;
        float[] out=new float[cnt*2];int q=0;
        for(int i=0;i<m;i++)if(keep[i]){
            out[q++]=(float)Math.max(0,Math.min(widthMm,sx[i]));out[q++]=(float)Math.max(0,Math.min(heightMm,sy[i]));
        }
        out=StyleKit.removeCollinear(out,.002f);
        if(out.length<6||Arrays.equals(out,loop))return null;
        double a0=StyleKit.signedArea(loop),a1=StyleKit.signedArea(out);
        if(!Double.isFinite(a1)||a0*a1<=0)return null;
        // Area may change by at most the half-pixel band around the perimeter.
        double perim=0;for(int i=0;i<n;i++){int j=(i+1)%n;perim+=Math.hypot(loop[2*j]-loop[2*i],loop[2*j+1]-loop[2*i+1]);}
        if(Math.abs(a1-a0)>perim*dmax)return null;
        if(StyleKit.selfIntersections(out,1)>0)return null;
        return out;
    }
    /** Even-odd nesting parity per loop (true = the loop bounds a hole in the vinyl). */
    static boolean[] evenOddHoles(List<float[]> loops){
        int n=loops.size();float[][] box=new float[n][];
        for(int i=0;i<n;i++)box[i]=bounding(loops.get(i));
        boolean[] hole=new boolean[n];
        for(int i=0;i<n;i++){
            if((i&63)==0)StyleKit.checkCancelled();
            float[] p=loops.get(i);if(p.length<6)continue;
            float px=p[0],py=p[1];int depth=0;
            for(int j=0;j<n;j++){
                if(j==i)continue;float[] b=box[j];
                if(px<b[0]||px>b[2]||py<b[1]||py>b[3])continue;
                if(b[2]-b[0]<box[i][2]-box[i][0]||b[3]-b[1]<box[i][3]-box[i][1])continue;
                if(MaskedFacetClipper.inside(loops.get(j),px,py))depth++;
            }
            hole[i]=(depth&1)==1;
        }
        return hole;
    }
    /** Iterative RDP on the closed sample ring between kept indices a < b (b may exceed m). */
    private static void simplifySpan(double[] x,double[] y,int m,int a,int b,double tol,boolean[] keep){
        java.util.ArrayDeque<int[]> work=new java.util.ArrayDeque<>();work.push(new int[]{a,b});
        double lim=tol*tol;
        while(!work.isEmpty()){
            int[] s=work.pop();int lo=s[0],hi=s[1];
            if(hi-lo<2)continue;
            double ax=x[lo%m],ay=y[lo%m],bx=x[hi%m],by=y[hi%m],dx=bx-ax,dy=by-ay,l2=dx*dx+dy*dy;
            int far=-1;double best=lim;
            for(int i=lo+1;i<hi;i++){
                double px=x[i%m]-ax,py=y[i%m]-ay,d2;
                if(l2<1e-18)d2=px*px+py*py;
                else{double t=Math.max(0,Math.min(1,(px*dx+py*dy)/l2));double ex=px-t*dx,ey=py-t*dy;d2=ex*ex+ey*ey;}
                if(d2>best){best=d2;far=i;}
            }
            if(far>=0){keep[far%m]=true;work.push(new int[]{lo,far});work.push(new int[]{far,hi});}
        }
    }
}

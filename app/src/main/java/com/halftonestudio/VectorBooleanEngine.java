package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Physical-mm motif union. Each Session owns independent sheet buckets; no
 * pixel grid, palette key or global mutable geometry participates in overlay.
 * The platform backend supplies robust area operations, not raster masks. */
final class VectorBooleanEngine {
    private VectorBooleanEngine() {}
    interface Backend {
        Object path(float[][] contours);
        Object clipPath(float[][] contours);
        Object removeMicroFragments(Object shape,float minVinylArea,float minWasteArea);
        /** As {@link #removeMicroFragments} for the post-Boolean conditioned path, whose output need
         * only be geometrically (not byte) equal; backends may avoid re-unioning the sheet. */
        default Object removeMicroFragmentsLocal(Object shape,float minVinylArea,float minWasteArea){
            return removeMicroFragments(shape,minVinylArea,minWasteArea);
        }
        Object batch(Motif[] motifs,int lo,int hi);
        Object combine(Object a,Object b,boolean disjoint);
        Object intersect(Object a,Object b);
        ArrayList<float[]> contours(Object shape);
        /** Morphological close by {@code closeR} (re-clipped), then open by {@code openR}:
         * merges same-sheet weed gaps narrower than 2*closeR and drops vinyl necks
         * narrower than 2*openR. Backends without buffering leave the shape unchanged. */
        default Object cutCondition(Object shape,Object clip,float closeR,float openR,float tolerance){return shape;}
        /** Net filled area, or NaN when the backend cannot measure it. */
        default double area(Object shape){return Double.NaN;}
    }
    private static volatile Backend backend;
    static void install(Backend implementation){backend=implementation;}
    static Session begin(int slots,float minCut){return begin(slots,minCut,minCut);}
    static Session begin(int slots,float minCut,float gap){
        Backend b=backend;return b==null?null:new Session(b,slots,minCut,gap);
    }
    static final int MAX_MOTIFS=250000,MAX_INPUT_POINTS=2500000,MAX_OUTPUT_POINTS=750000;
    static final float FLATNESS=.008f;
    /** Parallel union only pays off for large sheets; depth 3 yields up to 8 concurrent branches. */
    static final int PARALLEL_MIN_MOTIFS=256,PARALLEL_DEPTH=3;
    static final class Motif {
        final float[][] loops;
        final float x0,y0,x1,y1;
        Motif(float[][] loops){
            this.loops=loops;
            float a=Float.POSITIVE_INFINITY,b=a,c=-a,d=-a;
            for(float[] p:loops){
                if(p.length<6||(p.length&1)!=0)throw new IllegalArgumentException("Invalid vector motif");
                for(int k=0;k<p.length;k+=2){
                    if(!Float.isFinite(p[k])||!Float.isFinite(p[k+1]))throw new IllegalArgumentException("Nonfinite vector motif");
                    a=Math.min(a,p[k]);b=Math.min(b,p[k+1]);c=Math.max(c,p[k]);d=Math.max(d,p[k+1]);
                }
            }
            x0=a;y0=b;x1=c;y1=d;
        }
    }
    static final class Node {
        final Object path;
        final float x0,y0,x1,y1;
        Node(Object path,float x0,float y0,float x1,float y1){this.path=path;this.x0=x0;this.y0=y0;this.x1=x1;this.y1=y1;}
        boolean separated(Node b){return x1+.002f<b.x0||b.x1+.002f<x0||y1+.002f<b.y0||b.y1+.002f<y0;}
    }
    static final class Session {
        private final Backend b;
        final float tolerance, minVinylArea,minWasteArea;
        final ArrayList<ArrayList<Motif>> sheets=new ArrayList<>();
        private Object clip;
        private int motifs,points;
        int unionOps,disjointOps;
        Session(Backend b,int slots,float minCut,float gap){
            this.b=b;tolerance=Math.min(FLATNESS,minCut*.04f);
            minVinylArea=minCut*minCut*.30f;minWasteArea=gap*gap*.30f;
            for(int i=0;i<slots;i++)sheets.add(new ArrayList<>());
        }
        void add(int slot,float[]... loops){
            CancellationCheckpoint.check();
            if(slot<=0||slot>=sheets.size())throw new IllegalArgumentException("Invalid vector overlay slot");
            if(++motifs>MAX_MOTIFS)throw new IllegalStateException("Vector motif budget exceeded; reduce Detail or artwork size");
            for(float[] p:loops){points+=p.length/2;if(points>MAX_INPUT_POINTS)throw new IllegalStateException("Vector point budget exceeded; reduce Detail");}
            sheets.get(slot).add(new Motif(loops));
        }
        void setClip(List<float[]> loops){
            if(loops.isEmpty())throw new IllegalStateException("Empty structural Snip");
            clip=b.clipPath(loops.toArray(new float[0][]));
        }
        ArrayList<float[]> foundation(float width,float height){return checkedContours(b.contours(clip),width,height);}
        ArrayList<float[]> finish(int slot,float width,float height){
            return finish(slot,width,height,false);
        }
        // Prismatic preserves unused physical palette slots.
        ArrayList<float[]> finish(int slot,float width,float height,boolean allowEmpty){
            return finish(slot,width,height,allowEmpty,minVinylArea,minWasteArea);
        }
        ArrayList<float[]> finish(int slot,float width,float height,boolean allowEmpty,float vinylArea,float wasteArea){
            CancellationCheckpoint.check();
            if(slot<=0||slot>=sheets.size()||clip==null)throw new IllegalStateException("Vector union needs its structural Snip");
            ArrayList<Motif> list=sheets.get(slot);
            if(list.isEmpty()&&allowEmpty)return new ArrayList<>();
            if(list.isEmpty())throw new IllegalStateException("Vector overlay has no motifs; reduce layers or increase artwork size");
            Motif[] batch=list.toArray(new Motif[0]);list.clear();
            Node merged=batch.length>=PARALLEL_MIN_MOTIFS&&ParallelGeometry.enabled()?
                ParallelGeometry.invoke(new UnionTask(ParallelGeometry.token(),batch,0,batch.length,0)):
                union(batch,0,batch.length);
            CancellationCheckpoint.check();
            // Clean micro-work BEFORE clipping; never fill a protected Snip hole.
            Object polished=b.removeMicroFragments(merged.path,vinylArea,wasteArea);
            ArrayList<float[]> out=checkedContours(b.contours(b.intersect(polished,clip)),width,height);
            if(out.isEmpty()&&!allowEmpty)throw new IllegalStateException("Vector overlay has no cuttable geometry after Snip clipping; reduce layers or increase artwork size");
            return out;
        }
        /** Area changed by the last {@link #finishConditioned}: [before, after] in mm^2. */
        final double[] lastConditionedArea=new double[2];
        /** As {@link #finish} but with post-Boolean, post-clip cut conditioning. The
         * clip is applied before conditioning (so Snip-edge slivers are seen) and again
         * after it (closing never grows past the Snip or into a Snip hole). */
        ArrayList<float[]> finishConditioned(int slot,float width,float height,float vinylArea,float wasteArea,
                                             float closeR,float openR){
            return conditionSheet(slot,width,height,vinylArea,wasteArea,closeR,openR,lastConditionedArea);
        }
        private ArrayList<float[]> conditionSheet(int slot,float width,float height,float vinylArea,float wasteArea,
                                                  float closeR,float openR,double[] measured){
            CancellationCheckpoint.check();
            if(slot<=0||slot>=sheets.size()||clip==null)throw new IllegalStateException("Vector union needs its structural Snip");
            ArrayList<Motif> list=sheets.get(slot);
            measured[0]=measured[1]=0;
            if(list.isEmpty())return new ArrayList<>();
            Motif[] batch=list.toArray(new Motif[0]);list.clear();
            Node merged=batch.length>=PARALLEL_MIN_MOTIFS&&ParallelGeometry.enabled()?
                ParallelGeometry.invoke(new UnionTask(ParallelGeometry.token(),batch,0,batch.length,0)):
                union(batch,0,batch.length);
            CancellationCheckpoint.check();
            Object clipped=b.intersect(b.removeMicroFragmentsLocal(merged.path,vinylArea,wasteArea),clip);
            Object conditioned=b.cutCondition(clipped,clip,closeR,openR,tolerance);
            Object finished=b.intersect(b.removeMicroFragmentsLocal(conditioned,vinylArea,wasteArea),clip);
            measured[0]=b.area(clipped);measured[1]=b.area(finished);
            return checkedContours(b.contours(finished),width,height);
        }
        /** {@link #finishConditioned} for slots 1..n-1. Sheets are independent, so they run
         * as parallel tasks; each sheet's operations and results are identical to the
         * sequential path. area[slot] receives [before, after] in mm^2. */
        ArrayList<ArrayList<float[]>> finishConditionedAll(float width,float height,float vinylArea,float wasteArea,
                                                           float closeR,float openR,double[][] area){
            int n=sheets.size();
            ArrayList<ArrayList<float[]>> out=new ArrayList<>();
            for(int i=0;i<n;i++)out.add(new ArrayList<>());
            if(!ParallelGeometry.enabled()){
                for(int slot=1;slot<n;slot++){
                    out.set(slot,finishConditioned(slot,width,height,vinylArea,wasteArea,closeR,openR));
                    area[slot][0]=lastConditionedArea[0];area[slot][1]=lastConditionedArea[1];
                }
                return out;
            }
            ParallelGeometry.invoke(new SheetsTask(ParallelGeometry.token(),1,n,width,height,vinylArea,wasteArea,closeR,openR,out,area));
            return out;
        }
        private final class SheetsTask extends ParallelGeometry.Task<Void> {
            final int lo,hi;final float width,height,vinylArea,wasteArea,closeR,openR;
            final ArrayList<ArrayList<float[]>> out;final double[][] area;
            SheetsTask(java.util.concurrent.atomic.AtomicBoolean token,int lo,int hi,float width,float height,float vinylArea,
                       float wasteArea,float closeR,float openR,ArrayList<ArrayList<float[]>> out,double[][] area){
                super(token);this.lo=lo;this.hi=hi;this.width=width;this.height=height;this.vinylArea=vinylArea;
                this.wasteArea=wasteArea;this.closeR=closeR;this.openR=openR;this.out=out;this.area=area;
            }
            @Override Void work(){
                if(hi-lo>1){
                    int mid=(lo+hi)>>>1;
                    SheetsTask left=new SheetsTask(token,lo,mid,width,height,vinylArea,wasteArea,closeR,openR,out,area);
                    left.fork();
                    new SheetsTask(token,mid,hi,width,height,vinylArea,wasteArea,closeR,openR,out,area).work();
                    left.join();return null;
                }
                double[] measured=new double[2];
                ArrayList<float[]> sheet=conditionSheet(lo,width,height,vinylArea,wasteArea,closeR,openR,measured);
                synchronized(out){out.set(lo,sheet);area[lo][0]=measured[0];area[lo][1]=measured[1];}
                return null;
            }
        }
        private ArrayList<float[]> checkedContours(ArrayList<float[]> raw,float width,float height){
            ArrayList<float[]> out=new ArrayList<>(raw.size());int count=0;
            for(float[] p:raw){
                CancellationCheckpoint.check();
                float[] q=clean(p);
                if(q.length<6||Math.abs(StyleKit.signedArea(q))<1e-7)continue;
                count+=q.length/2;
                if(count>MAX_OUTPUT_POINTS||out.size()>=7000)throw new IllegalStateException("Vector sheet complexity exceeded; reduce Detail ("+count+" vertices, "+out.size()+" loops)");
                for(int k=0;k<q.length;k+=2){
                    if(!Float.isFinite(q[k])||!Float.isFinite(q[k+1])||q[k]<-.001f||q[k+1]<-.001f||q[k]>width+.001f||q[k+1]>height+.001f)
                        throw new IllegalStateException("Vector contour outside artwork");
                    q[k]=Math.max(0,Math.min(width,q[k]));q[k+1]=Math.max(0,Math.min(height,q[k+1]));
                }
                if(StyleKit.selfIntersections(q,1)>0)throw new IllegalStateException("Invalid self-crossing vector contour");
                out.add(q);
            }
            return out;
        }
        /** Build 555: the two halves of the union tree touch disjoint array ranges and
         * immutable geometry, so they run on the shared pool. The tree shape and every
         * Boolean operation are identical to the sequential path. */
        private final class UnionTask extends ParallelGeometry.Task<Node> {
            final Motif[] a;final int lo,hi,depth;
            UnionTask(java.util.concurrent.atomic.AtomicBoolean token,Motif[] a,int lo,int hi,int depth){super(token);this.a=a;this.lo=lo;this.hi=hi;this.depth=depth;}
            @Override Node work(){return unionParallel(a,lo,hi,depth,token);}
        }
        private Node unionParallel(Motif[] a,int lo,int hi,int depth,java.util.concurrent.atomic.AtomicBoolean token){
            if(depth>=PARALLEL_DEPTH||hi-lo<PARALLEL_MIN_MOTIFS||(hi-lo>=8&&hi-lo<=128))return union(a,lo,hi);
            CancellationCheckpoint.check();
            float x0=Float.POSITIVE_INFINITY,y0=x0,x1=-x0,y1=-x0;
            for(int i=lo;i<hi;i++){
                Motif m=a[i];x0=Math.min(x0,m.x0);y0=Math.min(y0,m.y0);x1=Math.max(x1,m.x1);y1=Math.max(y1,m.y1);
            }
            final boolean x=(x1-x0)>=(y1-y0);
            Arrays.sort(a,lo,hi,Comparator.comparingDouble(m->x?(m.x0+m.x1):(m.y0+m.y1)));
            int mid=lo+(hi-lo)/2;
            UnionTask left=new UnionTask(token,a,lo,mid,depth+1);left.fork();
            Node r=unionParallel(a,mid,hi,depth+1,token);
            Node l=left.join();
            boolean separate=l.separated(r);
            countOp(separate);
            return new Node(b.combine(l.path,r.path,separate),x0,y0,x1,y1);
        }
        private synchronized void countOp(boolean separate){if(separate)disjointOps++;else unionOps++;}
        private Node union(Motif[] a,int lo,int hi){
            CancellationCheckpoint.check();
            if(hi-lo==1){Motif m=a[lo];a[lo]=null;return new Node(b.path(m.loops),m.x0,m.y0,m.x1,m.y1);}
            float x0=Float.POSITIVE_INFINITY,y0=x0,x1=-x0,y1=-x0;
            for(int i=lo;i<hi;i++){
                Motif m=a[i];x0=Math.min(x0,m.x0);y0=Math.min(y0,m.y0);x1=Math.max(x1,m.x1);y1=Math.max(y1,m.y1);
            }
            if(hi-lo>=8&&hi-lo<=128){Object shape=b.batch(a,lo,hi);Arrays.fill(a,lo,hi,null);return new Node(shape,x0,y0,x1,y1);}
            // Spatially adjacent, balanced cascades prevent repeated union of a
            // growing full-canvas shape. Disjoint branches can just concatenate.
            final boolean x=(x1-x0)>=(y1-y0);
            Arrays.sort(a,lo,hi,Comparator.comparingDouble(m->x?(m.x0+m.x1):(m.y0+m.y1)));
            int mid=lo+(hi-lo)/2;
            Node l=union(a,lo,mid),r=union(a,mid,hi);
            // Include a one-grid-cell margin: snap rounding can close a microscopic gap.
            boolean separate=l.separated(r);
            countOp(separate);
            return new Node(b.combine(l.path,r.path,separate),x0,y0,x1,y1);
        }
    }
    // Only remove consecutive duplicates and a duplicate closing vertex. Do
    // not globally simplify: the points represent a physical error budget.
    static float[] clean(float[] p){
        float[] q=new float[p.length];int k=0;
        for(int i=0;i+1<p.length;i+=2){
            float x=p[i],y=p[i+1];
            if(k>0&&Math.abs(x-q[k-2])<=.00001f&&Math.abs(y-q[k-1])<=.00001f)continue;
            q[k++]=x;q[k++]=y;
        }
        if(k>=4&&Math.abs(q[k-2]-q[0])<=.00001f&&Math.abs(q[k-1]-q[1])<=.00001f)k-=2;
        return Arrays.copyOf(q,k);
    }
    static int arcSteps(double radius,double sweep,float error){
        double angle=2*Math.acos(Math.max(-1,Math.min(1,1-error/Math.max(radius,error))));
        return Math.max(4,Math.min(512,(int)Math.ceil(sweep/angle)));
    }
    static float[] ellipse(float x,float y,float rx,float ry,float angle,float error){
        int n=Math.max(16,arcSteps(Math.max(rx,ry),2*Math.PI,error));n=(n+3)&~3;
        float[] p=new float[n*2];double cs=Math.cos(angle),sn=Math.sin(angle);
        for(int i=0;i<n;i++){
            double a=2*Math.PI*i/n,u=rx*Math.cos(a),v=ry*Math.sin(a);
            p[2*i]=(float)(x+u*cs-v*sn);p[2*i+1]=(float)(y+u*sn+v*cs);
        }
        return p;
    }
    static float[] capsule(float ax,float ay,float bx,float by,float radius,float error){
        double angle=Math.atan2(by-ay,bx-ax);
        if(Math.hypot(bx-ax,by-ay)<.0001)return ellipse(ax,ay,radius,radius,0,error);
        int n=arcSteps(radius,Math.PI,error);float[] p=new float[(n+1)*4];int k=0;
        for(int i=0;i<=n;i++){
            double a=angle-Math.PI/2+Math.PI*i/n;
            p[k++]=(float)(bx+radius*Math.cos(a));p[k++]=(float)(by+radius*Math.sin(a));
        }
        for(int i=0;i<=n;i++){
            double a=angle+Math.PI/2+Math.PI*i/n;
            p[k++]=(float)(ax+radius*Math.cos(a));p[k++]=(float)(ay+radius*Math.sin(a));
        }
        return p;
    }
    /** Preserve a quadrilateral's long edges; replace sharp streamer corners
     * with tangent quadratic transitions at a small relative setback. */
    static float[] roundedStrip(float[] p,float error){
        int n=p.length/2;ArrayList<Float> xy=new ArrayList<>();
        for(int i=0;i<n;i++){
            int a=(i+n-1)%n,b=(i+1)%n;
            float x=p[2*i],y=p[2*i+1];
            double da=Math.hypot(p[2*a]-x,p[2*a+1]-y),db=Math.hypot(p[2*b]-x,p[2*b+1]-y);
            double d=Math.min(da,db)*.24;
            if(d<.00001)return p;
            double ax=x+(p[2*a]-x)*d/da,ay=y+(p[2*a+1]-y)*d/da;
            double bx=x+(p[2*b]-x)*d/db,by=y+(p[2*b+1]-y)*d/db;
            int steps=Math.max(3,(int)Math.ceil(Math.sqrt(Math.hypot(ax-2*x+bx,ay-2*y+by)/(4*error))));
            for(int j=0;j<=steps;j++){
                double t=j/(double)steps,u=1-t;
                xy.add((float)(u*u*ax+2*t*u*x+t*t*bx));xy.add((float)(u*u*ay+2*t*u*y+t*t*by));
            }
        }
        float[] r=new float[xy.size()];for(int i=0;i<r.length;i++)r[i]=xy.get(i);return r;
    }
}

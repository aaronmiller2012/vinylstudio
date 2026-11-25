package com.halftonestudio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Bounded, spatially complete review of dense closed-loop sheets. It uses the
 * same precise geometric oracle as ordinary sheets, but groups nearby loops
 * into overlapping physical tiles. A pair of loops closer than minimumMm
 * always has overlapping padded bounds, so the pair occurs in at least one
 * common tile. The original exported geometry is never modified.
 *
 * Resource exhaustion is NOT a passing review. Do not replace this with a
 * larger global complexity budget: large polygon sheets would become O(N^2).
 */
final class DenseCutSheetSafety {
    static final class Result {
        final int critical,narrow,unverified;
        final int tileCount;
        Result(int c,int n,int u,int tiles){critical=c;narrow=n;unverified=u;tileCount=tiles;}
    }
    private DenseCutSheetSafety(){}
    private static long key(int x,int y){return ((long)x<<32)^(y&0xffffffffL);}
    private static Result unverified(int tiles){return new Result(0,0,1,tiles);}
    static Result inspect(List<float[]> loops,float minMm,int maxWarnings) {
        return inspect(loops,minMm,minMm,maxWarnings,false);
    }
    /** Vinyl/waste-classified variant used for export (see StyleCutGeometryPreflight). */
    static Result inspect(List<float[]> loops,float minMm,float gapMm,int maxWarnings) {
        return inspect(loops,minMm,gapMm,maxWarnings,true);
    }
    private static Result inspect(List<float[]> loops,float minMm,float gapMm,int maxWarnings,boolean classified) {
        if(loops==null||!Float.isFinite(minMm)||minMm<=0||!Float.isFinite(gapMm)||gapMm<=0||maxWarnings<1)return unverified(0);
        // Tiles inspect each loop with only nearby loops, so even-odd fill is
        // exact only when a whole nesting chain shares the tile. Every loop is
        // inserted into every tile its padded bounds touch, and a containing
        // loop's bounds cover its holes, so the chain is always present.
        final float reach=Math.max(minMm,gapMm);
        // Hard resource limits are independent of the number of physical
        // contours allowed by any cutter's import format.
        if(loops.size()>50000)return unverified(0);
        final double tileMm=Math.max(10.0,32.0*minMm);
        final HashMap<Long,ArrayList<Integer>> buckets=new HashMap<>();
        int inserts=0;
        for(int i=0;i<loops.size();i++){
            if((i&127)==0&&Thread.currentThread().isInterrupted())return unverified(buckets.size());
            float[] p=loops.get(i);
            if(p==null||p.length<6||(p.length&1)!=0)return new Result(1,0,0,buckets.size());
            float lx=Float.POSITIVE_INFINITY,ly=lx,hx=Float.NEGATIVE_INFINITY,hy=hx;
            for(int k=0;k<p.length;k+=2){
                float x=p[k],y=p[k+1];
                if(!Float.isFinite(x)||!Float.isFinite(y))return new Result(1,0,0,buckets.size());
                lx=Math.min(lx,x);ly=Math.min(ly,y);
                hx=Math.max(hx,x);hy=Math.max(hy,y);
            }
            // Both envelopes are padded by a FULL minimum cut clearance.
            // This may yield false candidate pairs, never missed near pairs.
            int left=(int)Math.floor((lx-reach)/tileMm),right=(int)Math.floor((hx+reach)/tileMm);
            int top=(int)Math.floor((ly-reach)/tileMm),bottom=(int)Math.floor((hy+reach)/tileMm);
            long count=(long)(right-left+1)*(bottom-top+1);
            if(count<1||count>256||inserts+count>250000)return unverified(buckets.size());
            for(int ty=top;ty<=bottom;ty++)for(int tx=left;tx<=right;tx++){
                buckets.computeIfAbsent(key(tx,ty),k->new ArrayList<>()).add(i);
            }
            inserts+=(int)count;
        }
        int critical=0,narrow=0,unknown=0;
        // Repeated tile appearances are de-duplicated only for reporting, not
        // for determining safety. All tiles still undergo inspection.
        HashSet<String> distinct=new HashSet<>();
        for(Map.Entry<Long,ArrayList<Integer>> entry:buckets.entrySet()){
            if(Thread.currentThread().isInterrupted())return new Result(critical,narrow,unknown+1,buckets.size());
            ArrayList<Integer> ids=entry.getValue();
            if(ids.size()>256)return new Result(critical,narrow,unknown+1,buckets.size());
            StyleKit.Result page=new StyleKit.Result(1f,1f);
            StyleKit.Layer layer=new StyleKit.Layer("dense final cut tile",0xff000000);
            page.layers.add(layer);
            for(int id:ids)layer.loops.add(loops.get(id));
            List<StyleCutGeometryPreflight.Warning> findings;
            try{findings=classified?StyleCutGeometryPreflight.inspect(page,minMm,gapMm,maxWarnings):
                StyleCutGeometryPreflight.inspect(page,minMm,maxWarnings);}
            catch(IllegalArgumentException badGeometry){
                return new Result(critical+1,narrow,unknown,buckets.size());
            }catch(RuntimeException incomplete){
                return new Result(critical,narrow,unknown+1,buckets.size());
            }
            if(findings.size()>=maxWarnings)unknown++;
            for(StyleCutGeometryPreflight.Warning w:findings){
                if(w.reason.contains("not verified")||w.reason.contains("complexity budget")){
                    unknown++;continue;
                }
                String marker=w.reason+":"+Math.round(w.xMm*10)+":"+Math.round(w.yMm*10);
                if(!distinct.add(marker))continue;
                if(w.reason.startsWith("Intersecting")||w.reason.startsWith("Touching")||
                   w.reason.startsWith("Overlapping"))critical++;
                else narrow++;
                if(distinct.size()>=maxWarnings)unknown++;
            }
        }
        return new Result(critical,narrow,unknown,buckets.size());
    }
}

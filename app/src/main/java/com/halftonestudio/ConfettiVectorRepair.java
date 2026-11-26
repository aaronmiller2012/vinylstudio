package com.halftonestudio;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
/** Bounded post-union candidate selection; this is not a substitute for final cut review. */
final class ConfettiVectorRepair {
    private ConfettiVectorRepair(){}
    static final int LIMIT=512;
    static boolean eligible(List<float[]> loops){
        if(loops.isEmpty()||loops.size()>2000)return false;
        long points=0;for(float[] p:loops){points+=p.length/2;if(points>60000)return false;}return true;
    }
    static int points(List<float[]> loops){int count=0;for(float[] p:loops)count+=p.length/2;return count;}
    static boolean smallGroup(List<float[]> loops){return loops.size()<=120&&points(loops)<=6500;}
    static final int MAX_GROUP_ATTEMPTS=24,MAX_GROUP_POINTS=12000;
    static List<StyleCutGeometryPreflight.Warning> warnings(List<float[]> loops,float w,float h,float min,float gap){
        StyleKit.Result r=new StyleKit.Result(w,h);StyleKit.Layer l=new StyleKit.Layer("review",0xff000000);
        l.loops.addAll(loops);r.layers.add(l);
        return StyleCutGeometryPreflight.inspect(r,min,gap,LIMIT);
    }
    static int findings(List<float[]> loops,float w,float h,float min,float gap){
        return count(warnings(loops,w,h,min,gap));
    }
    static int count(List<StyleCutGeometryPreflight.Warning> f){
        for(StyleCutGeometryPreflight.Warning a:f)if(a.reason.contains("not verified")||a.reason.contains("complexity"))return -1;
        return f.size();
    }
    /** Warning-centred core tiles. Halo is supplied separately by the Boolean backend.
     * Coalesce repeated segment markers; prefer tiles with the most findings, not
     * simply the first contour in the sheet. Bound both tile count and physical area. */
    static List<float[]> windows(List<StyleCutGeometryPreflight.Warning> warnings,float w,float h,float min,float gap){
        final float pitch=Math.max(3f,6f*Math.max(min,gap));
        int nx=(int)Math.ceil(w/pitch),ny=(int)Math.ceil(h/pitch);
        LinkedHashMap<Long,Integer> counts=new LinkedHashMap<>();
        for(StyleCutGeometryPreflight.Warning a:warnings){
            CancellationCheckpoint.check();
            if(a.reason.contains("not verified")||a.reason.contains("complexity"))return java.util.Collections.emptyList();
            if(!Float.isFinite(a.midXMm)||!Float.isFinite(a.midYMm))continue;
            int x=Math.min(nx-1,Math.max(0,(int)Math.floor(a.midXMm/pitch)));
            int y=Math.min(ny-1,Math.max(0,(int)Math.floor(a.midYMm/pitch)));
            long key=(long)y*nx+x;
            counts.put(key,counts.getOrDefault(key,0)+1);
        }
        ArrayList<Long> keys=new ArrayList<>(counts.keySet());
        keys.sort((a,b)->{int c=Integer.compare(counts.get(b),counts.get(a));return c!=0?c:Long.compare(a,b);});
        ArrayList<float[]> result=new ArrayList<>();double area=0;
        // 72 cores, at most 4% of the sheet. Overlap at tile borders avoids
        // putting a known defect directly on a replacement seam.
        float margin=Math.max(min,gap)*.75f;
        for(long key:keys){
            if(result.size()>=72)break;
            int x=(int)(key%nx),y=(int)(key/nx);
            float x0=Math.max(0,x*pitch-margin),y0=Math.max(0,y*pitch-margin);
            float x1=Math.min(w,(x+1)*pitch+margin),y1=Math.min(h,(y+1)*pitch+margin);
            double next=(x1-x0)*(y1-y0);
            if(next<=0||area+next>w*(double)h*.04)continue;
            result.add(new float[]{x0,y0,x1,y1});area+=next;
        }
        return result;
    }
    static boolean accept(List<float[]> before,List<float[]> after,float w,float h,float min,float gap,int oldFindings){
        if(oldFindings<=0||!retainsCoverage(before,after,w,h,min))return false;
        int next=findings(after,w,h,min,gap);
        // A saturated count is not evidence of improvement. Retain the original if both saturate.
        return next>=0&&next<oldFindings;
    }
    static boolean retainsCoverage(List<float[]> before,List<float[]> after,float w,float h,float min){
        if(!eligible(after))return false;
        // Even-odd spatial change, not net area: equal-area movement cannot pass unchecked.
        float p=Math.max(min*.25f,Math.max(w,h)/768f);
        int nx=(int)Math.ceil(w/p)+2,ny=(int)Math.ceil(h/p)+2;
        boolean[] a,b;
        try{a=CutGeometryConditioner.rasterize(before,nx,ny,p);b=CutGeometryConditioner.rasterize(after,nx,ny,p);}
        catch(CutGeometryConditioner.BudgetExceededException tooDense){return false;}
        int original=0,changed=0,kept=0;
        for(int i=0;i<a.length;i++){if((i&8191)==0)CancellationCheckpoint.check();if(a[i])original++;if(a[i]!=b[i])changed++;if(a[i]&&b[i])kept++;}
        return original>0&&kept>=original*.90&&changed<=original*.15;
    }
}

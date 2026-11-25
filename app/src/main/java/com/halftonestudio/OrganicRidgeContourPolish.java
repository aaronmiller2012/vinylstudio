package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Conservative optional cutter-outline polish for the two continuous-ridge
 * expanded portrait styles.  It works on the SAME polygon geometry used by
 * preview and export; unlike a mask blur, it cannot merge color regions.
 *
 * Only accepts a candidate if winding, enclosed area, displacement, topology
 * and local physical-cut warnings remain acceptable.  Edges close to the Snip
 * boundary and already fragile rings are deliberately left untouched.
 */
final class OrganicRidgeContourPolish {
    private OrganicRidgeContourPolish() {}
    private static final int MAX_CANDIDATES=8;
    private static final int MAX_POINTS=2000;

    static int apply(StyleKit.Result result,float minimumMm,float gapMm,float cellMm){
        int accepted=0;
        for(StyleKit.Layer sheet:result.layers){
            StyleKit.checkCancelled();
            if(sheet.loops.isEmpty())continue;
            ArrayList<Integer> order=new ArrayList<>();
            for(int i=0;i<sheet.loops.size();i++){
                float[] p=sheet.loops.get(i);
                if(p.length>=36&&p.length<=MAX_POINTS*2)order.add(i);
            }
            // Prefer long continuous ridges; do not spend the budget on dust.
            order.sort((a,b)->Integer.compare(sheet.loops.get(b).length,sheet.loops.get(a).length));
            int tries=0;
            for(int index:order){
                if(tries++>=MAX_CANDIDATES)break;
                StyleKit.checkCancelled();
                float[] before=sheet.loops.get(index);
                float[] bounds=bounds(before);
                float span=Math.min(bounds[2]-bounds[0],bounds[3]-bounds[1]);
                if(span<Math.max(minimumMm*2.2f,cellMm*2.3f))continue;
                // Never deform contact with the subject/Snip perimeter.
                float edge=cellMm*.60f;
                if(bounds[0]<=edge||bounds[1]<=edge||
                   bounds[2]>=result.widthMm-edge||bounds[3]>=result.heightMm-edge)continue;
                float[] after=propose(before,Math.min(.11f,Math.max(.035f,cellMm*.16f)),
                        result.widthMm,result.heightMm);
                if(after==null||Arrays.equals(before,after))continue;
                sheet.loops.set(index,after);
                if(localSafe(sheet,index,before,minimumMm,gapMm,result.widthMm,result.heightMm))accepted++;
                else sheet.loops.set(index,before);
            }
        }
        return accepted;
    }

    static float[] propose(float[] before,float tolerance,float widthMm,float heightMm){
        if(before==null||before.length<36||before.length>MAX_POINTS*2)return null;
        float[] next=StyleKit.chaikin(before,2,true,1);
        next=SolidSnipContourRepair.simplifyClosed(next,tolerance);
        if(next.length<6||next.length>before.length*2)return null;
        double a=StyleKit.signedArea(before),b=StyleKit.signedArea(next);
        if(!Double.isFinite(a)||!Double.isFinite(b)||a*b<=0||Math.abs(a)<1e-8)return null;
        // Restore only the small area change caused by corner cutting. Scaling
        // is not a substitute for respecting actual small eye/ridge details.
        double scale=Math.sqrt(Math.abs(a/b));
        if(scale<.975||scale>1.025)return null;
        double cx=0,cy=0;int n=next.length/2;
        for(int k=0;k<n;k++){cx+=next[2*k];cy+=next[2*k+1];}
        cx/=n;cy/=n;
        for(int k=0;k<n;k++){
            double x=cx+(next[2*k]-cx)*scale;
            double y=cy+(next[2*k+1]-cy)*scale;
            if(!Double.isFinite(x)||!Double.isFinite(y)||x<0||x>widthMm||y<0||y>heightMm)return null;
            next[2*k]=(float)x;next[2*k+1]=(float)y;
        }
        if(StyleKit.selfIntersections(next,1)>0)return null;
        if(Math.abs(StyleKit.signedArea(next)-a)>Math.abs(a)*.002)return null;
        return next;
    }

    private static boolean localSafe(StyleKit.Layer sheet,int changed,float[] before,
                                     float minCut,float gap,float width,float height){
        float[] after=sheet.loops.get(changed);
        float threshold=Math.max(.12f,Math.min(.40f,Math.min(minCut,gap)*.52f));
        if(!near(before,after,threshold)||!near(after,before,threshold))return false;
        float[] patch=bounds(before), cb=bounds(after);
        patch[0]=Math.min(patch[0],cb[0]);patch[1]=Math.min(patch[1],cb[1]);
        patch[2]=Math.max(patch[2],cb[2]);patch[3]=Math.max(patch[3],cb[3]);
        float pad=Math.max(minCut,gap)*1.6f+.30f;
        patch[0]-=pad;patch[1]-=pad;patch[2]+=pad;patch[3]+=pad;
        StyleKit.Layer old=new StyleKit.Layer("ridge-original",sheet.color);
        StyleKit.Layer changedLayer=new StyleKit.Layer("ridge-proposal",sheet.color);
        for(int i=0;i<sheet.loops.size();i++){
            float[] ring=sheet.loops.get(i);
            if(i!=changed&&!intersects(patch,bounds(ring)))continue;
            old.loops.add(i==changed?before:ring);
            changedLayer.loops.add(ring);
        }
        StyleKit.Result pageOld=new StyleKit.Result(width,height);pageOld.layers.add(old);
        StyleKit.Result pageNew=new StyleKit.Result(width,height);pageNew.layers.add(changedLayer);
        final int cap=64;
        try{
            List<StyleCutGeometryPreflight.Warning> newer=
                StyleCutGeometryPreflight.inspect(pageNew,minCut,gap,cap);
            if(newer.size()>=cap)return false;
            if(newer.isEmpty())return true;
            List<StyleCutGeometryPreflight.Warning> older=
                StyleCutGeometryPreflight.inspect(pageOld,minCut,gap,cap);
            if(older.size()>=cap||newer.size()>older.size())return false;
            // Pre-existing warnings may remain, but no new warning type/location.
            for(StyleCutGeometryPreflight.Warning w:newer){
                boolean existing=false;
                for(StyleCutGeometryPreflight.Warning prior:older){
                    if(prior.reason.equals(w.reason)&&Math.hypot(prior.xMm-w.xMm,
                        prior.yMm-w.yMm)<=Math.max(.25f,minCut*.75f)){
                        existing=true;break;
                    }
                }
                if(!existing)return false;
            }
            return true;
        }catch(RuntimeException ex){return false;}
    }

    private static float[] bounds(float[] ring){
        float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
        for(int i=0;i<ring.length;i+=2){
            minX=Math.min(minX,ring[i]);minY=Math.min(minY,ring[i+1]);
            maxX=Math.max(maxX,ring[i]);maxY=Math.max(maxY,ring[i+1]);
        }
        return new float[]{minX,minY,maxX,maxY};
    }
    private static boolean intersects(float[] a,float[] b){
        return a[0]<=b[2]&&b[0]<=a[2]&&a[1]<=b[3]&&b[1]<=a[3];
    }
    private static boolean near(float[] sample,float[] poly,float d){
        int n=sample.length/2,m=poly.length/2;
        int stride=Math.max(1,n/180);
        double limit=(double)d*d;
        for(int i=0;i<n;i+=stride){
            double x=sample[2*i],y=sample[2*i+1],best=Double.POSITIVE_INFINITY;
            for(int j=0;j<m;j++){
                int k=(j+1)%m;
                double ax=poly[2*j],ay=poly[2*j+1],dx=poly[2*k]-ax,dy=poly[2*k+1]-ay;
                double norm=dx*dx+dy*dy;
                double t=norm<1e-12?0:Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/norm));
                double ex=x-ax-dx*t,ey=y-ay-dy*t;
                best=Math.min(best,ex*ex+ey*ey);
                if(best<=limit)break;
            }
            if(best>limit)return false;
        }
        return true;
    }
}

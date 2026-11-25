package com.halftonestudio;

import java.util.Arrays;

/**
 * RC62: bounded, deterministic SLIC-style spatial Lab segmentation of the *allowed*
 * photograph. Regions guide colour and text packing; the original Snip validity
 * mask remains authoritative for all physical contours. This is not a fill export.
 */
public final class TextPortraitRegions {
    private TextPortraitRegions() {}
    public static final class Region {
        public final int id, rgb, pixels;
        public final float edge, dark;
        Region(int id,int rgb,int pixels,float edge,float dark){
            this.id=id;this.rgb=rgb;this.pixels=pixels;this.edge=edge;this.dark=dark;
        }
    }
    public static final class Map {
        private final int w,h;
        private final int[] labels;
        private final Region[] regions;
        private final float widthMm,heightMm;
        Map(int w,int h,int[] labels,Region[] regions,float widthMm,float heightMm){
            this.w=w;this.h=h;this.labels=labels;this.regions=regions;
            this.widthMm=widthMm;this.heightMm=heightMm;
        }
        public Region at(float x,float y){
            if(x<0||y<0||x>=widthMm||y>=heightMm)return null;
            int px=Math.min(w-1,Math.max(0,(int)(x/widthMm*w)));
            int py=Math.min(h-1,Math.max(0,(int)(y/heightMm*h)));
            int k=labels[py*w+px];return k<0?null:regions[k];
        }
        public int count(){return regions.length;}
        public int width(){return w;}
        public int height(){return h;}
    }
    private static int clamp(int v,int high){return Math.min(high-1,Math.max(0,v));}
    /** At most 160x160 working samples, capped at 600 spatial clusters. */
    public static Map analyze(StyleKit.Source src,float cellMm){
        int longSide=Math.max(src.w,src.h);
        float reduction=Math.max(1f,longSide/160f);
        int w=Math.max(2,Math.round(src.w/reduction));
        int h=Math.max(2,Math.round(src.h/reduction));
        int n=w*h;
        float[] L=new float[n],A=new float[n],B=new float[n],D=new float[n];
        boolean[] valid=new boolean[n];
        for(int y=0;y<h;y++){
            if((y&15)==0)StyleKit.checkCancelled();
            for(int x=0;x<w;x++){
                float xx=(x+.5f)*src.widthMm/w, yy=(y+.5f)*src.heightMm/h;
                int i=y*w+x;valid[i]=src.validAt(xx,yy);
                if(!valid[i])continue;
                float[] lab=StyleKit.toLab(src.colorAt(xx,yy));
                L[i]=lab[0];A[i]=lab[1];B[i]=lab[2];D[i]=src.darkAt(xx,yy);
            }
        }
        // A ~6-pixel spatial radius adheres to fairly small image structures.
        // Cap clusters so 8-layer previews remain responsive on a phone.
        int step=Math.max(5,(int)Math.ceil(Math.sqrt(n/450.0)));
        step=Math.max(step,(int)Math.ceil(cellMm/src.widthMm*w*1.2f));
        int cw=(w+step-1)/step,ch=(h+step-1)/step;
        int k=cw*ch;
        float[] cx=new float[k],cy=new float[k],cl=new float[k],ca=new float[k],cb=new float[k];
        for(int gy=0;gy<ch;gy++)for(int gx=0;gx<cw;gx++){
            int idx=gy*cw+gx;
            int x=clamp(gx*step+step/2,w),y=clamp(gy*step+step/2,h);
            // Seed centers may land outside a Snip; those clusters simply receive
            // no samples. Locate the nearest valid seed pixel when feasible.
            if(!valid[y*w+x]){
                boolean found=false;
                for(int rad=1;rad<=step&&!found;rad++)for(int oy=-rad;oy<=rad&&!found;oy++)
                    for(int ox=-rad;ox<=rad;ox++){
                        int xx=clamp(x+ox,w),yy=clamp(y+oy,h);
                        if(valid[yy*w+xx]){x=xx;y=yy;found=true;break;}
                    }
            }
            cx[idx]=x;cy[idx]=y;cl[idx]=L[y*w+x];ca[idx]=A[y*w+x];cb[idx]=B[y*w+x];
        }
        int[] labels=new int[n];float[] distances=new float[n];
        for(int iter=0;iter<5;iter++){
            StyleKit.checkCancelled();
            Arrays.fill(labels,-1);Arrays.fill(distances,Float.POSITIVE_INFINITY);
            for(int s=0;s<k;s++){
                int x0=Math.max(0,(int)cx[s]-2*step),x1=Math.min(w-1,(int)cx[s]+2*step);
                int y0=Math.max(0,(int)cy[s]-2*step),y1=Math.min(h-1,(int)cy[s]+2*step);
                for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){
                    int i=y*w+x;if(!valid[i])continue;
                    float dl=L[i]-cl[s],da=A[i]-ca[s],db=B[i]-cb[s];
                    float dx=(x-cx[s])/step,dy=(y-cy[s])/step;
                    // Color variation matters, but prevent disconnected distant matches.
                    float dist=dl*dl+da*da+db*db+144f*(dx*dx+dy*dy);
                    if(dist<distances[i]){distances[i]=dist;labels[i]=s;}
                }
            }
            float[] xl=new float[k],yl=new float[k],ll=new float[k],al=new float[k],bl=new float[k];
            int[] counts=new int[k];
            for(int i=0;i<n;i++)if(labels[i]>=0){
                int s=labels[i];counts[s]++;xl[s]+=i%w;yl[s]+=i/w;
                ll[s]+=L[i];al[s]+=A[i];bl[s]+=B[i];
            }
            for(int s=0;s<k;s++)if(counts[s]>0){
                float inv=1f/counts[s];cx[s]=xl[s]*inv;cy[s]=yl[s]*inv;
                cl[s]=ll[s]*inv;ca[s]=al[s]*inv;cb[s]=bl[s]*inv;
            }
        }
        // Recalculate representative *RGB* directly from physical source colors:
        // Lab -> RGB approximation is unnecessary and could distort user swatches.
        long[] sr=new long[k],sg=new long[k],sb=new long[k];
        float[] sd=new float[k];int[] count=new int[k],boundary=new int[k];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int i=y*w+x,s=labels[i];if(s<0)continue;
            int color=src.colorAt((x+.5f)*src.widthMm/w,(y+.5f)*src.heightMm/h);
            sr[s]+=(color>>16)&255;sg[s]+=(color>>8)&255;sb[s]+=color&255;
            sd[s]+=D[i];count[s]++;
            if(x+1<w&&labels[i+1]!=s)boundary[s]++;
            if(y+1<h&&labels[i+w]!=s)boundary[s]++;
        }
        Region[] result=new Region[k];
        for(int s=0;s<k;s++){
            int q=Math.max(1,count[s]);
            int rgb=0xff000000|((int)(sr[s]/q)<<16)|((int)(sg[s]/q)<<8)|(int)(sb[s]/q);
            result[s]=new Region(s,rgb,count[s],Math.min(1f,boundary[s]/(float)q),sd[s]/q);
        }
        return new Map(w,h,labels,result,src.widthMm,src.heightMm);
    }
}

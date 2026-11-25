package com.halftonestudio;

/** Shared, scale-aware importance for cell-based photographic styles.
 * Measures source luminance AND color transitions; validity boundaries are
 * protected. Uses spatially local sampling rather than assuming faces live in
 * the image center. All values lie in [0,1]. Pure Java, no Android dependency.
 */
public final class PhotoCellImportance {
    private final int w,h;
    private final float[] score;
    private final boolean[] valid;
    private PhotoCellImportance(int w,int h,float[] score,boolean[] valid){
        this.w=w;this.h=h;this.score=score;this.valid=valid;
    }
    public static PhotoCellImportance create(int[] rgb,int w,int h,boolean[] allowed){
        if(rgb==null||w<3||h<3||rgb.length!=(long)w*h||
           (allowed!=null&&allowed.length!=rgb.length))throw new IllegalArgumentException("Invalid photo detail map");
        int n=w*h;
        boolean[] valid=new boolean[n];
        float[] light=new float[n];
        for(int i=0;i<n;i++){
            if((i&65535)==0&&Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            int c=rgb[i];valid[i]=(c>>>24)>=128&&(allowed==null||allowed[i]);
            light[i]=(.2126f*((c>>>16)&255)+.7152f*((c>>>8)&255)+.0722f*(c&255))/255f;
        }
        float[] edge=new float[n];
        for(int y=0;y<h;y++){
            if((y&63)==0&&Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            for(int x=0;x<w;x++){
                int i=y*w+x;if(!valid[i])continue;
                int left=y*w+Math.max(0,x-1),right=y*w+Math.min(w-1,x+1);
                int up=Math.max(0,y-1)*w+x,down=Math.min(h-1,y+1)*w+x;
                if(!valid[left]||!valid[right]||!valid[up]||!valid[down]){edge[i]=1;continue;}
                float dl=Math.abs(light[right]-light[left])+Math.abs(light[down]-light[up]);
                float dc=colorDifference(rgb[left],rgb[right])+colorDifference(rgb[up],rgb[down]);
                edge[i]=Math.min(1f,.85f*dl+.36f*dc);
            }
        }
        // Small neighborhood evidence avoids promoting isolated noisy pixels into
        // hundreds of additional panes. Retain strong true boundaries via max blend.
        float[] smooth=new float[n];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int idx=y*w+x;if(!valid[idx])continue;
            float sum=0;int count=0;
            for(int yy=Math.max(0,y-1);yy<=Math.min(h-1,y+1);yy++)
                for(int xx=Math.max(0,x-1);xx<=Math.min(w-1,x+1);xx++){
                    int j=yy*w+xx;if(valid[j]){sum+=edge[j];count++;}
                }
            smooth[idx]=Math.min(1f,.55f*edge[idx]+.45f*(count==0?0:sum/count));
        }
        return new PhotoCellImportance(w,h,smooth,valid);
    }
    private static float colorDifference(int a,int b){
        return (Math.abs(((a>>>16)&255)-((b>>>16)&255))+
                Math.abs(((a>>>8)&255)-((b>>>8)&255))+
                Math.abs((a&255)-(b&255)))/255f;
    }
    public float at(float x,float y){
        int xx=Math.max(0,Math.min(w-1,(int)Math.floor(x)));
        int yy=Math.max(0,Math.min(h-1,(int)Math.floor(y)));
        return valid[yy*w+xx]?score[yy*w+xx]:0;
    }
    public float maxIn(float x0,float y0,float x1,float y1){
        float max=0;
        for(int yy=0;yy<5;yy++)for(int xx=0;xx<5;xx++){
            float x=x0+(xx+.5f)*(x1-x0)/5f, y=y0+(yy+.5f)*(y1-y0)/5f;
            max=Math.max(max,at(x,y));
        }
        return max;
    }
    /** Select an actual photographic feature, never a fixed face-shaped ellipse. */
    public float[] strongestIn(float x0,float y0,float x1,float y1){
        float best=-1,bx=(x0+x1)*.5f,by=(y0+y1)*.5f;
        for(int yy=0;yy<5;yy++)for(int xx=0;xx<5;xx++){
            float x=x0+(xx+.5f)*(x1-x0)/5f, y=y0+(yy+.5f)*(y1-y0)/5f;
            float s=at(x,y);
            if(s>best){best=s;bx=x;by=y;}
        }
        return new float[]{bx,by};
    }
    public int validCount(){int count=0;for(boolean b:valid)if(b)count++;return count;}
}

package com.halftonestudio;

/** Shared, deterministic photo-to-structure preprocessing for single-line art.
 * Coordinates are source pixels; no Android dependencies. Transparency is white.
 * Luminance percentiles are stretched gently, and edges retain fine contrast.
 */
public final class PortraitStructureMap {
    private final int width,height;
    private final float[] tone,edges;
    public PortraitStructureMap(int[] argb,int w,int h) {
        if(argb==null||w<3||h<3||(long)w*h!=argb.length||argb.length>8_000_000)
            throw new IllegalArgumentException("Invalid portrait size");
        width=w;height=h;
        float[] raw=new float[argb.length];int[] hist=new int[256];
        for(int i=0;i<raw.length;i++){
            int v=argb[i];float alpha=((v>>>24)&255)/255f;
            float lum=(.2126f*((v>>>16)&255)+.7152f*((v>>>8)&255)+.0722f*(v&255))/255f;
            raw[i]=alpha*lum+1f-alpha;
            hist[Math.min(255,Math.max(0,(int)(raw[i]*255f))) ]++;
        }
        int low=0,high=255,sum=0,lowTarget=argb.length/100,highTarget=argb.length*99/100;
        for(int i=0;i<256;i++){sum+=hist[i];if(sum>=lowTarget){low=i;break;}}
        sum=0;for(int i=0;i<256;i++){sum+=hist[i];if(sum>=highTarget){high=i;break;}}
        // Do not stretch almost uniform/white images into false portraits.
        float range=(high-low)/255f;
        float stretch=range>.15f?Math.min(1.5f,1f/range):1f;
        float mid=(low+high)/510f;
        float[] normalized=new float[raw.length];
        for(int i=0;i<raw.length;i++)normalized[i]=clamp((raw[i]-mid)*stretch+.5f);
        float[] a=new float[raw.length];tone=new float[raw.length];edges=new float[raw.length];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int i=y*w+x;a[i]=(normalized[y*w+Math.max(0,x-1)]+2*normalized[i]+normalized[y*w+Math.min(w-1,x+1)])*.25f;
        }
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int i=y*w+x;tone[i]=(a[Math.max(0,y-1)*w+x]+2*a[i]+a[Math.min(h-1,y+1)*w+x])*.25f;
        }
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){
            int i=y*w+x;
            float dx=3*(tone[i-w+1]-tone[i-w-1])+10*(tone[i+1]-tone[i-1])+3*(tone[i+w+1]-tone[i+w-1]);
            float dy=3*(tone[i+w-1]-tone[i-w-1])+10*(tone[i+w]-tone[i-w])+3*(tone[i+w+1]-tone[i-w+1]);
            // Fixed scale rather than max normalization prevents one spike
            // from suppressing eye and hair edges throughout the portrait.
            edges[i]=clamp((float)Math.hypot(dx,dy)/9f);
        }
    }
    private static float clamp(float v){return Math.max(0,Math.min(1,v));}
    public float light(float x,float y){return sample(tone,x,y);}
    public float dark(float x,float y){return 1f-light(x,y);}
    public float edge(float x,float y){return sample(edges,x,y);}
    private float sample(float[] data,float x,float y){
        x=Math.max(0,Math.min(width-1,x));y=Math.max(0,Math.min(height-1,y));
        int ix=(int)x,iy=(int)y,jx=Math.min(ix+1,width-1),jy=Math.min(iy+1,height-1);
        float fx=x-ix,fy=y-iy;
        return (data[iy*width+ix]*(1-fx)+data[iy*width+jx]*fx)*(1-fy)+
               (data[jy*width+ix]*(1-fx)+data[jy*width+jx]*fx)*fy;
    }
}

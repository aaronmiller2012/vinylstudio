package com.halftonestudio;

import java.util.concurrent.CancellationException;

/** Exact global-coordinate lighting in bounded 256-row bands with a 1-pixel
 * Sobel-style halo. Input/output are legacy arrays for engine compatibility,
 * but the former full-image float luminance plane and snip boolean array are gone.
 * This is the raster stage of high-resolution export, not a claim that the
 * final specialty/vector engines can stream arbitrarily large artwork.
 */
public final class TiledImageLevels {
    private TiledImageLevels() {}
    public static final int BAND_ROWS=256;
    private static float clip(float v){return Math.max(0f,Math.min(1f,v));}
    private static int byteClip(float v){return Math.max(0,Math.min(255,Math.round(v)));}
    private static float luma(int p){return (((p>>16)&255)*.2126f+((p>>8)&255)*.7152f+(p&255)*.0722f)/255f;}
    public static int[] apply(int[] source,int w,int h,VinylEngine.Settings s){
        if(source==null||w<1||h<1||(long)w*h!=source.length)throw new IllegalArgumentException("Invalid source");
        if(ImageLevels.isNeutral(s))return source;
        int[] result=source.clone();
        float min=s.levelsBlack/255f,max=s.levelsWhite/255f;
        if(min>=max)return result;
        boolean snipped=s.snips!=null&&!s.snips.isEmpty();
        final double exposure=Math.pow(2d,s.exposureStops);
        final ToneGammaLut gamma=new ToneGammaLut(s.levelsGamma);
        // Scratch buffers are reused across every band instead of reallocating
        // 256 full-width rows (and an optional Snip mask) each iteration.
        float[] lum=new float[w*Math.min(h,BAND_ROWS+2)];
        boolean[] eligible=snipped?new boolean[lum.length]:null;
        for(int y0=0;y0<h;y0+=BAND_ROWS){
            if(Thread.currentThread().isInterrupted())throw new CancellationException("Tiled render canceled");
            int y1=Math.min(h,y0+BAND_ROWS),haloTop=Math.max(0,y0-1),haloBottom=Math.min(h,y1+1);
            for(int y=haloTop;y<haloBottom;y++)for(int x=0;x<w;x++){
                int q=(y-haloTop)*w+x;lum[q]=luma(source[y*w+x]);
                if(snipped)eligible[q]=s.snips.allows((x+.5f)/w,(y+.5f)/h);
            }
            for(int y=y0;y<y1;y++)for(int x=0;x<w;x++){
                if(x==0&&Thread.currentThread().isInterrupted())throw new CancellationException("Tiled render canceled");
                int i=y*w+x,q=(y-haloTop)*w+x,p=source[i];
                if(snipped&&!eligible[q])continue;
                float original=lum[q];
                float base=gamma.map(clip((original-min)/(max-min)));
                base=clip((float)(base*exposure));
                base=clip((base-.5f)*s.photoContrast+.5f);
                float dark=(1-base)*(1-base),bright=base*base;
                float adjusted=clip(base+s.levelsShadows*.28f*dark+s.levelsHighlights*.28f*bright);
                if(s.edgePriority>.0001f){
                    float sum=0,maxDifference=0;int n=0;
                    for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
                        int nx=Math.max(0,Math.min(w-1,x+dx));
                        int ny=Math.max(0,Math.min(h-1,y+dy));
                        int ni=(ny-haloTop)*w+nx;
                        float neighbor=(!snipped||eligible[ni])?lum[ni]:original;
                        sum+=neighbor;n++;maxDifference=Math.max(maxDifference,Math.abs(original-neighbor));
                    }
                    float detail=original-sum/n;
                    float emphasis=clip(maxDifference*5f);
                    adjusted=clip(adjusted+s.edgePriority*detail*(.55f+.90f*emphasis));
                }
                float delta=(adjusted-original)*255f;
                int a=(p>>>24)&255;
                int rr=byteClip(((p>>>16)&255)+delta);
                int gg=byteClip(((p>>>8)&255)+delta);
                int bb=byteClip((p&255)+delta);
                result[i]=(a<<24)|(rr<<16)|(gg<<8)|bb;
            }
        }
        return result;
    }
}

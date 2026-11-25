package com.halftonestudio;

import java.util.concurrent.CancellationException;

/** Bounded display-only rendering of a 1-bit ribbon mask.
 *
 * Unlike allocating an entire ARGB source image and then scaling it, this
 * compositor samples the boolean mask directly into the *final* preview
 * buffer. For shrink operations it computes bilinear ink coverage; it never
 * alters the source mask, vinyl geometry, or SVG export. Package-private for
 * Android-free JVM testing.
 */
final class BoundedBinaryMaskPreview {
    static final class Result {
        final int width,height;
        final int[] argb;
        Result(int width,int height,int[] argb){
            this.width=width;this.height=height;this.argb=argb;
        }
    }
    private BoundedBinaryMaskPreview() {}

    static Result compose(int w,int h,boolean[] mask,int maxSide){
        if(w<1||h<1||mask==null||(long)w*h!=mask.length||
                maxSide<1||maxSide>4096)
            throw new IllegalArgumentException("Invalid ribbon preview dimensions");
        float ratio=Math.min(1f,maxSide/(float)Math.max(w,h));
        int dw=Math.max(1,Math.round(w*ratio));
        int dh=Math.max(1,Math.round(h*ratio));
        int[] argb=new int[dw*dh];
        if(dw==w&&dh==h){
            for(int y=0;y<h;y++){
                if(Thread.currentThread().isInterrupted())
                    throw new CancellationException("Obsolete ribbon preview");
                int row=y*w;
                for(int x=0;x<w;x++)argb[row+x]=mask[row+x]?0xff000000:0xffffffff;
            }
            return new Result(dw,dh,argb);
        }
        // C3: coverage-preserving preview downsampling. A center-only bilinear
        // sample can erase an entire narrow ribbon when scaling 10:1 or more.
        // Integrate each source pixel's *overlap area* with the destination
        // footprint instead. Work is O(source pixels), additional storage is
        // just the bounded destination array; no full-size bitmap or SAT.
        // This changes DISPLAY antialiasing, never mask or exported geometry.
        final double scaleX=w/(double)dw,scaleY=h/(double)dh;
        for(int y=0;y<dh;y++){
            if(Thread.currentThread().isInterrupted())
                throw new CancellationException("Obsolete ribbon preview");
            final double ya=y*scaleY,yb=(y+1)*scaleY;
            final int startY=(int)Math.floor(ya),endY=Math.min(h,(int)Math.ceil(yb));
            for(int x=0;x<dw;x++){
                final double xa=x*scaleX,xb=(x+1)*scaleX;
                final int startX=(int)Math.floor(xa),endX=Math.min(w,(int)Math.ceil(xb));
                double inkArea=0;
                for(int sy=startY;sy<endY;sy++){
                    if((sy&31)==0&&Thread.currentThread().isInterrupted())
                        throw new CancellationException("Obsolete ribbon preview");
                    double wy=Math.min(yb,sy+1d)-Math.max(ya,sy);
                    if(wy<=0)continue;
                    int row=sy*w;
                    for(int sx=startX;sx<endX;sx++){
                        if(mask[row+sx]){
                            double wx=Math.min(xb,sx+1d)-Math.max(xa,sx);
                            if(wx>0)inkArea+=wx*wy;
                        }
                    }
                }
                double coverage=inkArea/(scaleX*scaleY);
                int gray=(int)Math.round(255*(1-Math.max(0,Math.min(1,coverage))));
                argb[y*dw+x]=0xff000000|(gray<<16)|(gray<<8)|gray;
            }
        }
        return new Result(dw,dh,argb);
    }
}

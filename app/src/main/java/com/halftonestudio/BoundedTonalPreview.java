package com.halftonestudio;

import java.util.concurrent.CancellationException;

/** Preview-only, memory-bounded compositing of opaque stencil masks.
 * Each preview pixel samples the corresponding source position; the cutting
 * geometry and high-resolution exported SVG are never modified.
 * Arrays and dimensions are validated before allocating preview pixels.
 */
final class BoundedTonalPreview {
    static final class Result {
        final int width, height;
        final int[] argb;
        Result(int w,int h,int[] pixels){width=w;height=h;argb=pixels;}
    }
    private BoundedTonalPreview() {}

    static Result compose(int sourceWidth,int sourceHeight,int maxSide,int paper,
                          int[] layerColors,boolean[][] masks,int selected){
        if(sourceWidth<1||sourceHeight<1||maxSide<1||maxSide>2048 ||
           (long)sourceWidth*sourceHeight>24_000_000L ||
           layerColors==null||masks==null||layerColors.length!=masks.length ||
           selected < -1 || selected>=masks.length)
            throw new IllegalArgumentException("Invalid stencil preview dimensions or selection");
        long sourcePixels=(long)sourceWidth*sourceHeight;
        for(boolean[] mask:masks)
            if(mask==null||mask.length!=sourcePixels)
                throw new IllegalArgumentException("Invalid stencil mask");
        float ratio=Math.min(1f,maxSide/(float)Math.max(sourceWidth,sourceHeight));
        int outW=Math.max(1,Math.round(sourceWidth*ratio));
        int outH=Math.max(1,Math.round(sourceHeight*ratio));
        int[] output=new int[outW*outH];
        for(int y=0;y<outH;y++){
            if(Thread.currentThread().isInterrupted())
                throw new CancellationException("Stencil thumbnail canceled");
            int sy=Math.min(sourceHeight-1,(int)(((long)(2*y+1)*sourceHeight)/(2L*outH)));
            int row=sy*sourceWidth;
            for(int x=0;x<outW;x++){
                int sx=Math.min(sourceWidth-1,(int)(((long)(2*x+1)*sourceWidth)/(2L*outW)));
                int index=row+sx;
                int ink=paper;
                for(int li=0;li<masks.length;li++){
                    if(selected>=0&&selected!=li)continue;
                    if(masks[li][index])ink=layerColors[li];
                }
                output[y*outW+x]=ink;
            }
        }
        return new Result(outW,outH,output);
    }
}

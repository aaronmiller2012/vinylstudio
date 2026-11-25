package com.halftonestudio;

import java.util.concurrent.CancellationException;

/**
 * Bounded draft-only source image cache. Slider changes keep the same photo,
 * so repeatedly downsampling it is wasted work. The only cache key is the
 * immutable source identity, photo revision and dimensions. Lighting is NOT
 * cached here: every tonal setting still gets evaluated on the draft pixels.
 * No draft pixels are ever used for final geometry or SVG export.
 */
final class RenderDraftSourceCache {
    private int[] source, scaled;
    private long revision=Long.MIN_VALUE;
    private int width,height,draftWidth,draftHeight;

    synchronized int[] draft(int[] input,int w,int h,int dw,int dh,long sourceRevision){
        if(input==null||w<=0||h<=0||dw<=0||dh<=0||(long)w*h!=input.length||dw>w||dh>h)
            throw new IllegalArgumentException("Invalid draft image dimensions");
        if(dw==w&&dh==h)return input;
        if(input==source&&scaled!=null&&revision==sourceRevision&&width==w&&height==h&&
            draftWidth==dw&&draftHeight==dh)return scaled;
        // Allocate and fully prepare before publication. A cancelled render
        // must never place a partially initialized image in the cache.
        int[] candidate=new int[dw*dh];
        for(int y=0;y<dh;y++){
            if(Thread.currentThread().isInterrupted())throw new CancellationException("Obsolete draft");
            int yy=Math.min(h-1,(int)((y+.5)*h/dh));
            for(int x=0;x<dw;x++)candidate[y*dw+x]=
                input[yy*w+Math.min(w-1,(int)((x+.5)*w/dw))];
        }
        source=input;scaled=candidate;revision=sourceRevision;
        width=w;height=h;draftWidth=dw;draftHeight=dh;
        return candidate;
    }
    synchronized void clear(){source=null;scaled=null;revision=Long.MIN_VALUE;width=height=draftWidth=draftHeight=0;}
}

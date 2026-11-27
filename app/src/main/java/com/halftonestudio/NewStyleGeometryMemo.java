package com.halftonestudio;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

/** One exact final vector snapshot. Never reuse based on an approximate hash,
 * and never publish a mutable cached Result. Poster text uses an external
 * rasterizer so it deliberately bypasses this cache. */
final class NewStyleGeometryMemo {
    private static final long LIMIT=8L*1024*1024;
    private long epoch;
    private Entry entry;
    private static final class Entry {
        final int w,h;final int[] rgb;final VinylStyleSuite.Config c;final StyleKit.Result result;
        Entry(int w,int h,int[] rgb,VinylStyleSuite.Config c,StyleKit.Result result){this.w=w;this.h=h;this.rgb=rgb;this.c=c;this.result=result;}
    }
    synchronized void clear(){entry=null;epoch++;}
    StyleKit.Result generate(int[] rgb,int w,int h,VinylStyleSuite.Config c,Supplier<StyleKit.Result> work){
        long sourceBytes=4L*rgb.length+(c.allowed==null?0:c.allowed.length);
        // Maze results carry a separate immutable topology certificate and geometry;
        // do not cache an incomplete snapshot or underestimate its memory.
        if(c.draft||VinylStyleSuite.isMaze(c.style)||VinylStyleSuite.isPopPoster(c.style)||c.popPoster!=null||sourceBytes>LIMIT)return work.get();
        Entry prior;long ticket;synchronized(this){prior=entry;ticket=epoch;}
        if(prior!=null&&prior.w==w&&prior.h==h&&same(prior.c,c)&&Arrays.equals(prior.rgb,rgb))return copy(prior.result);
        int[] input=rgb.clone();VinylStyleSuite.Config config=c.copy();
        StyleKit.Result result=work.get();StyleKit.checkCancelled();
        long estimate=sourceBytes;
        for(StyleKit.Layer layer:result.layers){estimate+=128;
            for(float[] ring:layer.loops)estimate+=32L+4L*ring.length;
            for(float[] line:layer.lines)estimate+=32L+4L*line.length;}
        // Cache budget estimates retained array/list data, not a heap guarantee.
        if(estimate<=LIMIT){Entry next=new Entry(w,h,input,config,copy(result));synchronized(this){if(ticket==epoch)entry=next;}}
        return result;
    }
    private static boolean f(float a,float b){return Float.floatToIntBits(a)==Float.floatToIntBits(b);}
    static boolean same(VinylStyleSuite.Config a,VinylStyleSuite.Config b){
        return a.style==b.style&&a.variant==b.variant&&a.detail==b.detail&&a.colorCount==b.colorCount&&a.motifFamily==b.motifFamily&&a.draft==b.draft&&
            f(a.widthMm,b.widthMm)&&f(a.leadMm,b.leadMm)&&f(a.weightMm,b.weightMm)&&f(a.minimumMm,b.minimumMm)&&f(a.gapMm,b.gapMm)&&
            f(a.prismaticGapMm,b.prismaticGapMm)&&f(a.flowStrength,b.flowStrength)&&a.organicSmoothing==b.organicSmoothing&&
            f(a.trioSpacingMm,b.trioSpacingMm)&&a.trioOrganic==b.trioOrganic&&a.prismaticIrregularity==b.prismaticIrregularity&&
            a.prismaticRoundness==b.prismaticRoundness&&a.prismaticFeature==b.prismaticFeature&&a.prismaticTone==b.prismaticTone&&
            a.leadColor==b.leadColor&&a.paper==b.paper&&Objects.equals(a.words,b.words)&&Arrays.equals(a.palette,b.palette)&&Arrays.equals(a.allowed,b.allowed)&&
            a.popPoster==null&&b.popPoster==null;
    }
    private static StyleKit.Result copy(StyleKit.Result r){
        StyleKit.Result out=new StyleKit.Result(r.widthMm,r.heightMm);out.paperArgb=r.paperArgb;out.summary=r.summary;
        for(StyleKit.Layer l:r.layers){StyleKit.Layer n=new StyleKit.Layer(l.name,l.color);n.strokeMm=l.strokeMm;n.vectorGeometryLocked=l.vectorGeometryLocked;
            for(float[] ring:l.loops){StyleKit.checkCancelled();n.loops.add(ring.clone());}
            for(float[] line:l.lines){StyleKit.checkCancelled();n.lines.add(line.clone());}out.layers.add(n);}
        return out;
    }
}

package com.halftonestudio;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;

/** Render-speed pass: parallel layers, cached luminance and loop-local
 * cancellation must produce byte-identical geometry to the sequential path,
 * surface the same first-layer error, and still stop when interrupted. */
public final class V3RenderSpeedParityTest {
    static int[] photo(int w,int h){
        int[] p=new int[w*h];Random r=new Random(7);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double cx=(x-w*.5)/w,cy=(y-h*.45)/h,d=Math.sqrt(cx*cx*1.6+cy*cy);int face=d<.3?1:0;
            int R=clamp((int)(face*(200-150*d)+(1-face)*(60+120*x/(double)w))+r.nextInt(20));
            int G=clamp((int)(face*(150-120*d)+(1-face)*(90+40*Math.sin(y*.02)))+r.nextInt(20));
            int B=clamp((int)(face*(120-90*d)+(1-face)*140)+r.nextInt(20));
            p[y*w+x]=0xff000000|(R<<16)|(G<<8)|B;
        }
        return p;
    }
    static int clamp(int v){return Math.max(0,Math.min(255,v));}
    static long hash(VinylEngine.Result r){
        long h=1;
        for(List<VinylEngine.Shape> layer:r.shapes){h=h*31+layer.size();
            for(VinylEngine.Shape s:layer){
                if(s.circle){h=h*31+Float.floatToIntBits(s.x);h=h*31+Float.floatToIntBits(s.y);h=h*31+Float.floatToIntBits(s.r);}
                else for(float[] ring:s.contours==null?new float[][]{s.xy}:s.contours)
                    for(float f:ring)h=h*31+Float.floatToIntBits(f);
            }
        }
        return h;
    }
    static VinylEngine.Settings settings(String mode,int pattern){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();s.widthMm=120;s.pattern=pattern;
        if(mode.equals("spot")){s.mode=VinylEngine.SPOT;s.classicCmykLines=false;
            for(VinylEngine.Layer l:s.layers)l.cmykChannel=-1;}
        if(mode.equals("modern"))s.classicCmykLines=false;
        return s;
    }
    public static void main(String[] args) throws Exception {
        int w=640,h=800;int[] px=photo(w,h);
        int[] patterns={VinylEngine.ROUND,VinylEngine.SQUARE,VinylEngine.LINE,VinylEngine.WAVE,
            VinylEngine.STRAIGHT,VinylEngine.CONTOUR,VinylEngine.RINGS,VinylEngine.BEADED,VinylEngine.HEART};
        int checked=0;
        for(String mode:new String[]{"cmyk","modern","spot"})for(int p:patterns){
            LayerParallelism.sequential=true;
            long serial=hash(VinylEngine.generate(px,w,h,settings(mode,p)));
            LayerParallelism.sequential=false;
            long parallel=hash(VinylEngine.generate(px,w,h,settings(mode,p)));
            // Second parallel run reuses the cached luminance plane.
            long cached=hash(VinylEngine.generate(px,w,h,settings(mode,p)));
            if(serial!=parallel||serial!=cached)
                throw new AssertionError("Geometry changed for "+mode+" pattern "+p);
            checked++;
        }
        // Same error text as the sequential loop for an over-dense layer.
        VinylEngine.Settings dense=settings("cmyk",VinylEngine.ROUND);dense.widthMm=400;
        String serialError=null,parallelError=null;
        LayerParallelism.sequential=true;
        try{VinylEngine.generate(px,w,h,dense);}catch(IllegalArgumentException e){serialError=e.getMessage();}
        LayerParallelism.sequential=false;
        try{VinylEngine.generate(px,w,h,dense.copy());}catch(IllegalArgumentException e){parallelError=e.getMessage();}
        if(serialError==null||!serialError.equals(parallelError))
            throw new AssertionError("Error mismatch: "+serialError+" vs "+parallelError);
        // Interruption still cancels a render promptly.
        final boolean[] cancelled={false};
        Thread t=new Thread(()->{
            Thread.currentThread().interrupt();
            try{VinylEngine.generate(px,w,h,settings("spot",VinylEngine.LINE));}
            catch(CancellationException expected){cancelled[0]=true;}
        });
        t.start();t.join(10000);
        if(!cancelled[0])throw new AssertionError("Interrupted render was not cancelled");
        System.out.println("V3RenderSpeedParityTest PASS ("+checked+" configurations, helpers="
            +LayerParallelism.MAX_WORKERS+")");
    }
}

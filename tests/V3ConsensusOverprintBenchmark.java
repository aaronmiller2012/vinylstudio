package com.halftonestudio;

import java.util.Arrays;
import java.util.Random;

/** Non-gating microbenchmark, report medians only. Use a warmed JVM. */
public final class V3ConsensusOverprintBenchmark {
    private static long median(long[] a){Arrays.sort(a);return a[a.length/2];}
    static int[] image(int w,int h){
        int[] p=new int[w*h];Random rnd=new Random(51517L);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int r=(int)Math.max(0,Math.min(255,205-120*Math.hypot((x-w*.5)/w,(y-h*.48)/h)
                +13*Math.sin(x*.085)+rnd.nextInt(11)));
            int g=(int)Math.max(0,Math.min(255,162-90*Math.hypot((x-w*.5)/w,(y-h*.48)/h)
                +11*Math.cos(y*.07)+rnd.nextInt(9)));
            int b=(int)Math.max(0,Math.min(255,125-60*Math.hypot((x-w*.5)/w,(y-h*.48)/h)
                +8*Math.sin((x+y)*.11)+rnd.nextInt(9)));
            p[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        return p;
    }
    private static void run(boolean snip){
        int w=512,h=640;int[] photo=image(w,h);boolean[] mask=null;
        if(snip){mask=new boolean[photo.length];for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double dx=(x-w*.50)/(w*.45),dy=(y-h*.52)/(h*.45);
            mask[y*w+x]=dx*dx+dy*dy<=1;
        }}
        LayeredCellPortraitEngine.Options opts=new LayeredCellPortraitEngine.Options();
        opts.widthMm=210;opts.columns=42;opts.gapMm=.28f;
        opts.minFeatureMm=.30f;opts.seamGapMm=.28f;opts.layerCount=8;
        opts.motifDepth=3;opts.ornamentation=.75f;opts.photoDetail=.8f;
        opts.allowedPixels=mask;opts.registration=false;
        opts.overprintKind=LayeredCellPortraitEngine.OVERPRINT_MIXED;
        opts.colors=new int[]{0xff272324,0xffe8c7a0,0xffbc7666,0xff516da2,
            0xff68543c,0xffc6ae51,0xff7d8c66,0xfff6e7d4};
        long[] times=new long[5];int points=0;
        for(int run=0;run<7;run++){
            long start=System.nanoTime();
            LayeredCellPortraitEngine.Result result=LayeredCellPortraitEngine.generate(photo,w,h,opts);
            long nanos=System.nanoTime()-start;
            if(run>=2)times[run-2]=nanos;
            points=result.pieces.size();
        }
        System.out.println((snip?"snipped":"whole")+" overprint median_ms="+median(times)/1e6+" pieces="+points);
    }
    public static void main(String[] args){run(false);run(true);}
}

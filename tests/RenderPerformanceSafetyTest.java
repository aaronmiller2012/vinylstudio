package com.halftonestudio;

import java.util.Arrays;
import java.util.concurrent.CancellationException;

/** Host tests: exact-key caching, invalidation, edge-preserving cache parity, cancellation. */
public final class RenderPerformanceSafetyTest {
    private static void expect(boolean p,String m){if(!p)throw new AssertionError(m);}
    public static void main(String[] args){
        RenderPreparationCache<String,int[]> cache=new RenderPreparationCache<>(2);
        int[] pixels={1,2,3};cache.put("photo:A|crop:X|levels:0|size:850",pixels);
        expect(cache.get("photo:A|crop:X|levels:0|size:850")==pixels,"reuse identical preparation");
        expect(cache.get("photo:A|crop:X|levels:0|size:650")==null,"draft/final resolution isolated");
        expect(cache.get("photo:A|crop:X|levels:1|size:850")==null,"lighting invalidates");
        expect(cache.get("photo:A|crop:Y|levels:0|size:850")==null,"crop invalidates");
        cache.put("other",new int[]{7});cache.get("photo:A|crop:X|levels:0|size:850");
        cache.put("third",new int[]{9});
        expect(cache.get("other")==null,"LRU evicts least-recently-used");
        expect(cache.size()==2,"bounded cache");
        cache.clear();expect(cache.size()==0,"photo change clears all entries");

        final int w=32,h=34,n=w*h;
        int[] argb=new int[n];
        for(int i=0;i<n;i++){
            int c=(i*19+i/31*7)%245;
            argb[i]=0xff000000|c<<16|(c+2)<<8|c+7;
        }
        MultilayerStencilEngine.Options o=new MultilayerStencilEngine.Options();
        o.registration=false;o.autoWhiteBackground=false;o.blurRadius=3;o.sourceColorMode=false;
        MultilayerStencilEngine.clearSmoothingCacheForTest();
        MultilayerStencilEngine.Result a=MultilayerStencilEngine.generate(argb,w,h,o);
        expect(MultilayerStencilEngine.smoothingComputationsForTest()==1,"initial smoothing calculated");
        MultilayerStencilEngine.Result b=MultilayerStencilEngine.generate(argb,w,h,o);
        expect(MultilayerStencilEngine.smoothingComputationsForTest()==1,"repeat smoothing reused");
        for(int layer=0;layer<o.layerCount;layer++)expect(a.svg(layer).equals(b.svg(layer)),"identical cached SVG layer "+layer);
        o.blurRadius=4;MultilayerStencilEngine.generate(argb,w,h,o);
        expect(MultilayerStencilEngine.smoothingComputationsForTest()==2,"radius change invalidates");
        argb[2]=0xff110a07;MultilayerStencilEngine.generate(argb,w,h,o);
        expect(MultilayerStencilEngine.smoothingComputationsForTest()==3,"photo change invalidates");
        o.sourceColorMode=true;MultilayerStencilEngine.generate(argb,w,h,o);
        expect(MultilayerStencilEngine.smoothingComputationsForTest()==3,"source-color mode skips bilateral");
        // An already-interrupted render must never populate the smoothing cache.
        MultilayerStencilEngine.clearSmoothingCacheForTest();
        boolean stopped=false;Thread.currentThread().interrupt();
        try{MultilayerStencilEngine.generate(argb,w,h,o);}catch(RuntimeException expected){stopped=true;}finally{Thread.interrupted();}
        expect(stopped,"cancellation cooperates");
        expect(MultilayerStencilEngine.smoothingComputationsForTest()==0,"cancelled work not cached");
        System.out.println("PASS: two-stage cache behavior, tonal smoothing exact parity, invalidation, cancellation");
    }
}

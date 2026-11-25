package com.halftonestudio;
import java.util.Arrays;
/** Verifies exact cached image work, invalidation, and no palette coupling. */
public final class V3PhotoCacheParityTest {
    static void check(boolean c,String message){if(!c)throw new AssertionError(message);}
    static int[] photo(int w,int h){
        int[] out=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int r=(x*7+y*3)%256,g=(x*11+y*19)%256,b=(x*17+y*13)%256;
            out[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        return out;
    }
    static long fingerprint(StyleKit.Result r){
        long h=0xcbf29ce484222325L;
        for(StyleKit.Layer l:r.layers){h=(h^l.color)*0x100000001b3L;
            for(float[] xy:l.loops)for(float v:xy)h=(h^Float.floatToIntBits(v))*0x100000001b3L;
        }return h;
    }
    static OrganicFlowPortraitEngine.Options options(float width){
        OrganicFlowPortraitEngine.Options o=new OrganicFlowPortraitEngine.Options();
        o.widthMm=width;o.layers=3;o.detail=85;o.minCutMm=.8f;
        o.smoothing=50;o.mode=OrganicFlowPortraitEngine.MARBLING;o.gapMm=.5f;
        return o;
    }
    public static void main(String[] args){
        int w=94,h=113;int[] photo=photo(w,h);
        boolean[] mask=new boolean[w*h];Arrays.fill(mask,true);
        OrganicFlowPortraitEngine.clearAnalysisCacheForTest();
        OrganicFlowPortraitEngine.Options o=options(101.6f);
        o.allowed=mask;
        long a=fingerprint(OrganicFlowPortraitEngine.generate(photo,w,h,o));
        int hits=OrganicFlowPortraitEngine.analysisCacheHitsForTest();
        check(hits==0,"First image unexpectedly cached");
        o.flow=1.6f;
        long cached=fingerprint(OrganicFlowPortraitEngine.generate(photo,w,h,o));
        check(OrganicFlowPortraitEngine.analysisCacheHitsForTest()>hits,"Flow edit missed cache");
        OrganicFlowPortraitEngine.clearAnalysisCacheForTest();
        long uncached=fingerprint(OrganicFlowPortraitEngine.generate(photo,w,h,o));
        check(cached==uncached,"Cached render differs in cut geometry");
        o.palette=new int[]{0xff000000,0xff668899,0xffffffff};
        OrganicFlowPortraitEngine.generate(photo,w,h,o);
        check(OrganicFlowPortraitEngine.analysisCacheHitsForTest()==1,"Palette edit recomputed photo-only work");
        o.allowed=mask.clone();o.allowed[100]=false;
        OrganicFlowPortraitEngine.generate(photo,w,h,o);
        check(OrganicFlowPortraitEngine.analysisCacheHitsForTest()==1,"Changed Snip mask incorrectly reused");
        o.widthMm=127.0f;
        OrganicFlowPortraitEngine.generate(photo,w,h,o);
        check(OrganicFlowPortraitEngine.analysisCacheHitsForTest()==1,"Changed width incorrectly reused");
        o.widthMm=101.6f;
        photo[0]^=0x00ff00;
        OrganicFlowPortraitEngine.generate(photo,w,h,o);
        check(OrganicFlowPortraitEngine.analysisCacheHitsForTest()==1,"Changed photo incorrectly reused");
        System.out.println("PASS exact geometry cache parity, flow/palette reuse, Snip/size/photo invalidation "+Long.toHexString(a));
    }
}

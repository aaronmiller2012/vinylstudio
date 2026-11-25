package com.halftonestudio;
import java.security.MessageDigest;
import java.util.Locale;

/** Deterministic real-engine geometry fingerprint plus lightweight host timing.
 * Benchmark numbers are descriptive: phones require Android release profiling.
 */
public final class V3OrganicRenderPerformanceTest {
    private static String digest(StyleKit.Result r) throws Exception {
        MessageDigest m=MessageDigest.getInstance("SHA-256");
        for(StyleKit.Layer l:r.layers){
            for(float[] ring:l.loops){
                for(float p:ring){int bits=Float.floatToIntBits(p);
                    m.update((byte)(bits>>>24));m.update((byte)(bits>>>16));
                    m.update((byte)(bits>>>8));m.update((byte)bits);
                }
                m.update((byte)0xff);
            }
            m.update((byte)0x55);
        }
        byte[] hash=m.digest();StringBuilder out=new StringBuilder();
        for(byte b:hash)out.append(String.format(Locale.US,"%02x",b&255));
        return out.toString();
    }
    public static void main(String[] args)throws Exception {
        int w=132,h=151;
        int[] photo=new int[w*h];boolean[] valid=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float xx=(x-w*.5f)/(w*.48f),yy=(y-h*.5f)/(h*.50f);
            valid[y*w+x]=xx*xx+yy*yy<1f;
            int r=(int)Math.max(0,Math.min(255,82+74*xx+25*Math.sin(xx*14+yy*3)));
            int g=(int)Math.max(0,Math.min(255,128+52*yy+35*Math.cos(yy*17+xx*5)));
            int b=(int)Math.max(0,Math.min(255,170-40*xx+20*Math.sin(yy*20)));
            if(Math.abs(yy+.25)<.025&&(Math.abs(xx+.3)<.1||Math.abs(xx-.3)<.1))r=g=b=15;
            photo[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        int[] colors={0xff192b3c,0xff245873,0xff579a92,0xffcfaaa0,0xfff4e3bb,0xfffcf8e5};
        for(int mode=0;mode<3;mode++){
            OrganicFlowPortraitEngine.Options o=new OrganicFlowPortraitEngine.Options();
            o.mode=mode;o.detail=88;o.flow=2.2f;o.layers=6;o.smoothing=50;
            o.widthMm=101.6f;o.minCutMm=.8f;o.gapMm=.5f;
            o.allowed=valid;o.palette=colors;
            long start=System.nanoTime();StyleKit.Result r=OrganicFlowPortraitEngine.generate(photo,w,h,o);
            long elapsed=(System.nanoTime()-start)/1000000;
            int loops=0;for(StyleKit.Layer l:r.layers)loops+=l.loops.size();
            System.out.printf(Locale.US,"mode=%d ms=%d points=%d loops=%d fingerprint=%s%n",
                mode,elapsed,r.totalPoints(),loops,digest(r));
        }
    }
}

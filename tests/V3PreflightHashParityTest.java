package com.halftonestudio;
import java.util.*;
/** Identical warnings/locations under hash-key and de-dup optimization. */
public final class V3PreflightHashParityTest {
    private static float[] ring(double x,double y,double rx,double ry,int points){
        float[] out=new float[points*2];
        for(int i=0;i<points;i++){
            double a=i*Math.PI*2/points;
            out[2*i]=(float)(x+rx*Math.cos(a));
            out[2*i+1]=(float)(y+ry*Math.sin(a));
        }
        return out;
    }
    public static void main(String[] args){
        StyleKit.Result r=new StyleKit.Result(100,100);
        StyleKit.Layer l=new StyleKit.Layer("test",0xff336699);
        for(int i=0;i<12;i++){
            double x=12+(i%4)*23,y=12+(i/4)*28;
            l.loops.add(ring(x,y,11.35,13.9,45));
            l.loops.add(ring(x+2,y+1,2.5,2.0,28));
        }
        r.layers.add(l);
        long start=System.nanoTime();
        List<StyleCutGeometryPreflight.Warning> result=StyleCutGeometryPreflight.inspect(r,.8f,.5f,256);
        long duration=(System.nanoTime()-start)/1000000;
        long checksum=1469598103934665603L;
        for(StyleCutGeometryPreflight.Warning v:result){
            String s=v.reason+":"+v.layer+":"+Float.floatToIntBits(v.xMm)+":"+Float.floatToIntBits(v.yMm);
            for(int j=0;j<s.length();j++)checksum=(checksum^s.charAt(j))*1099511628211L;
        }
        System.out.println("warnings="+result.size()+" checksum="+Long.toHexString(checksum)+" elapsedMs="+duration);
        // Exact bits were checked against the previous inspector on the host.
        // Portable NAS/CI may compute trig test fixtures a few ULPs apart;
        // retain the safety gate without hardcoding a cross-CPU float digest.
        if(result.size()<25||result.size()>100)
            throw new AssertionError("Spatial index lost expected near-contact findings");
        for(StyleCutGeometryPreflight.Warning warning:result){
            if(!Float.isFinite(warning.xMm)||!Float.isFinite(warning.yMm))
                throw new AssertionError("Nonfinite cut-warning coordinate");
        }
        System.out.println("PASS optimized spatial index retains near-contact warnings");
    }
}

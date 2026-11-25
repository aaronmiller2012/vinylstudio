package com.halftonestudio;
import java.util.Arrays;
public final class V3OrganicUniformLayerSmoothingTest {
    private static int stair(float[] p){
        int total=0,n=p.length/2;
        for(int i=0;i<n;i++){
            int j=(i+1)%n;
            double dx=Math.abs(p[2*j]-p[2*i]), dy=Math.abs(p[2*j+1]-p[2*i+1]);
            if(dx<1e-6||dy<1e-6||Math.abs(dx-dy)<.06)total++;
        }
        return total;
    }
    public static void main(String[] args){
        int w=180,h=180; int[] photo=new int[w*h]; Arrays.fill(photo,0xffaaaaaa);
        StyleKit.Source src=new StyleKit.Source(photo,w,h,null,101.6f);
        PatternPortraitTrioEngine.Surface s=new PatternPortraitTrioEngine.Surface(src,3,.8f);
        float[] importance=new float[s.w*s.h];
        for(int y=0;y<s.h;y++)for(int x=0;x<s.w;x++){
            float xx=(x+.5f)*s.mm, yy=(y+.5f)*s.mm;
            s.ink[0][y*s.w+x]=true;
            // Large jagged dominant region + smoothable dark stripe
            boolean dominant = yy > 28 + ((x/3)%2==0?0:1)*s.mm*1.2 && yy < 68 + ((x/4)%2==0?0:1)*s.mm*1.2;
            boolean stripe = yy > 40 + 5*Math.sin(xx/10.0) && yy < 46 + 5*Math.sin(xx/10.0);
            s.ink[1][y*s.w+x]=dominant;
            s.ink[2][y*s.w+x]=stripe;
            if(stripe || (dominant && xx>20 && xx<75)) importance[y*s.w+x]=1f;
        }
        StyleKit.Result r=s.finish(new int[]{0xff000000,0xffeeeeee,0xff111111},"u",2,.8f);
        float[] domBefore=r.layers.get(1).loops.get(0).clone();
        int stepsBefore=stair(domBefore);
        OrganicFlowCutSmoother.contour(r,s,.8f,.5f,100,importance);
        float[] domAfter=r.layers.get(1).loops.get(0);
        int stepsAfter=stair(domAfter);
        if(Arrays.equals(domBefore,domAfter)) throw new AssertionError("Dominant fill loop did not change");
        if(stepsAfter>=stepsBefore) throw new AssertionError("Dominant fill loop not rounded");
        System.out.println("PASS dominant layer also receives smoothing");
    }
}

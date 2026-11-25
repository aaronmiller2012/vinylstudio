package com.halftonestudio;
import java.util.Arrays;
public final class V3OrganicCurveVisibleChangeTest {
    private static int countSteps(StyleKit.Layer layer){
        int total=0;
        for(float[] p:layer.loops){
            int n=p.length/2;
            for(int i=0;i<n;i++){
                int j=(i+1)%n;
                double dx=p[2*j]-p[2*i],dy=p[2*j+1]-p[2*i+1];
                double r=Math.abs(dx)/Math.max(.000001,Math.abs(dy));
                if(r<.10||r>10||Math.abs(r-1)<.13)total++;
            }
        }
        return total;
    }
    private static int countPoints(StyleKit.Layer l){return l.pointCount();}
    public static void main(String[] args){
        int w=180,h=180;int[] photo=new int[w*h];Arrays.fill(photo,0xff999999);
        StyleKit.Source src=new StyleKit.Source(photo,w,h,null,101.6f);
        PatternPortraitTrioEngine.Surface s=new PatternPortraitTrioEngine.Surface(src,2,.8f);
        float[] important=new float[s.w*s.h];
        for(int y=0;y<s.h;y++)for(int x=0;x<s.w;x++){
            float xx=(x+.5f)*s.mm, yy=(y+.5f)*s.mm;
            s.ink[0][y*s.w+x]=true;
            // Jagged edge polygon from sampled analytic curves, no small islands.
            double curve=38.0+11.0*Math.sin(xx/12.0);
            s.ink[1][y*s.w+x]=(yy>curve && yy<curve+18)
                || Math.hypot(xx-84, yy-78)<1.3;
            if(Math.hypot(xx-84, yy-78)<2.0)important[y*s.w+x]=1f;
        }
        StyleKit.Result result=s.finish(new int[]{0xff000000,0xffffffff},"test",1,.8f);
        StyleKit.Layer layer=result.layers.get(1);
        int before=countPoints(layer),stepBefore=countSteps(layer);
        if(layer.loops.size()!=2)throw new AssertionError("Fixture requires ribbon and tiny highlight");
        float[] old=layer.loops.get(0).clone();
        float[] tiny=layer.loops.get(1).clone();
        OrganicFlowCutSmoother.contour(result,s,.8f,.5f,100,important);
        int after=countPoints(layer),stepAfter=countSteps(layer);
        System.out.printf("before=%d after=%d steps=%d/%d changed=%s rings=%d%n",before,after,stepBefore,stepAfter,!Arrays.equals(old,layer.loops.get(0)),layer.loops.size());
        if(Arrays.equals(old,layer.loops.get(0)))throw new AssertionError("Smoothing did not change large staircase contour");
        if(stepAfter>=stepBefore/2)throw new AssertionError("Grid-aligned steps were not materially reduced");
        double tinyBefore=Math.abs(StyleKit.signedArea(tiny));
        double tinyAfter=Math.abs(StyleKit.signedArea(layer.loops.get(1)));
        if(tinyAfter<tinyBefore*.90||tinyAfter>tinyBefore*1.10)
            throw new AssertionError("Tiny highlight lost material area");
        StyleKit.Result single=new StyleKit.Result(result.widthMm,result.heightMm);
        single.layers.add(layer);
        if(!StyleCutGeometryPreflight.inspect(single,.8f,.5f,1).isEmpty())
            throw new AssertionError("Smoothed cut loop failed the physical geometry inspector");
        if(!Arrays.equals(result.layers.get(0).loops.get(0),
                s.finish(new int[]{0xff000000,0xffffffff},"test",1,.8f).layers.get(0).loops.get(0)))
            throw new AssertionError("Structural Snip changed");
        System.out.println("PASS visibly rounded organic contours with identical solid Snip");
    }
}

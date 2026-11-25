package com.halftonestudio;
import java.util.*;
public final class V3RasterMotifPolishTest {
    static void require(boolean b,String msg){if(!b)throw new AssertionError(msg);}
    public static void main(String[] args){
        int w=160,h=160;int[] photo=new int[w*h];Arrays.fill(photo,0xffaaaaaa);
        StyleKit.Source src=new StyleKit.Source(photo,w,h,null,101.6f);
        PatternPortraitTrioEngine.Surface s=new PatternPortraitTrioEngine.Surface(src,3,.5f);
        s.fillValid(0);s.ellipseRing(1,30,30,18,12,.38f,2.4f);s.disk(2,65,65,12);
        StyleKit.Result r=s.finish(new int[]{0xffeeeeee,0xff444444,0xff123456},"test",3,.5f);
        ArrayList<ArrayList<float[]>> before=new ArrayList<>();
        ArrayList<boolean[]> masks=new ArrayList<>();
        for(StyleKit.Layer l:r.layers){before.add(new ArrayList<>(l.loops));masks.add(OrganicFlowCutSmoother.rasterize(l.loops,s.w,s.h,s.mm));}
        int changed=OrganicFlowCutSmoother.polishRasterMotifs(r,s,.5f,.5f);
        require(changed>0,"No eligible rounded geometry improved");
        for(int i=0;i<r.layers.size();i++){
            StyleKit.Layer l=r.layers.get(i);require(l.loops.size()==before.get(i).size(),"loop count changed");
            require(Arrays.equals(masks.get(i),OrganicFlowCutSmoother.rasterize(l.loops,s.w,s.h,s.mm)),"mask changed");
            for(int k=0;k<l.loops.size();k++){
                float[] a=before.get(i).get(k),b=l.loops.get(k);
                require(b.length<=a.length,"node count grew");
                if(i==0)require(a==b,"base modified");
            }
        }
        System.out.println("PASS raster motif polish: "+changed+" changed contours, unchanged sampled masks and base");
    }
}

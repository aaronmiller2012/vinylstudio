package com.halftonestudio;
import java.util.*;
public class StabilityGeometryTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static boolean region(List<float[]> rings,float x,float y){boolean fill=false;for(float[] p:rings)if(MaskedFacetClipper.inside(p,x,y))fill=!fill;return fill;}
 public static void main(String[] args){
    int w=90,h=90;int[] argb=new int[w*h];Arrays.fill(argb,0xffd8ab91);boolean[] mask=new boolean[w*h];
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){
        int d=(x-45)*(x-45)+(y-45)*(y-45);
        mask[y*w+x]=d<38*38 && d>7*7;
    }
    StyleKit.Source src=new StyleKit.Source(argb,w,h,mask,90f);
    float[] pane=StyleKit.rect(15,20,55,53);
    ArrayList<float[]> clipped=MaskedFacetClipper.intersect(pane,src);
    check(!clipped.isEmpty(),"Crossing pane was discarded");
    int found=0,invalid=0;for(int y=0;y<h;y++)for(int x=0;x<w;x++){
        boolean filled=region(clipped,x+.5f,y+.5f);
        if(filled){found++;if(!mask[y*w+x])invalid++;}
    }
    check(found>700,"Clipped facet lost too much area "+found);
    check(invalid<150,"Clipped facet extends into masked background "+invalid);
    float[] aroundHole=StyleKit.rect(29,29,62,62);
    ArrayList<float[]> holed=MaskedFacetClipper.intersect(aroundHole,src);
    check(region(holed,35.5f,35.5f),"Outer pane missing");
    check(!region(holed,45.5f,45.5f),"Internal hole filled");
    StyleKit.Result r=new StyleKit.Result(90,90);StyleKit.Layer layer=new StyleKit.Layer("lowpoly-1",0xff323232);r.layers.add(layer);
    layer.loops.add(StyleKit.rect(10,10,20,20));
    check(StyleCutGeometryPreflight.inspect(r,.6f,20).isEmpty(),"Safe square reported as risky");
    layer.loops.add(StyleKit.rect(30,30,30.02f,37));
    check(!StyleCutGeometryPreflight.inspect(r,.6f,20).isEmpty(),"Narrow shape not detected");
    layer.loops.remove(1);
    layer.loops.add(new float[]{1,1,5,5,1,5,5,1});
    boolean rejected=false;try{StyleCutGeometryPreflight.inspect(r,.6f,20);}catch(IllegalArgumentException expected){rejected=true;}
    check(rejected,"Self-intersecting contour accepted");
    System.out.println("PASS: mask clipping, hole retention, safe/narrow/self-intersecting geometry");
 }
}

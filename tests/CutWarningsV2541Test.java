package com.halftonestudio;
import java.util.ArrayList;
import java.util.List;
public final class CutWarningsV2541Test {
 static void ok(boolean b,String x){if(!b)throw new AssertionError(x);}
 public static void main(String[] args){
   VinylEngine.Settings s=VinylEngine.Settings.defaults();s.substrateAware=false;s.minimumMm=.8f;s.pattern=VinylEngine.LINE;
   VinylEngine.Result r=new VinylEngine.Result(s,100,8,new float[][]{{0,0},{0,0},{0,0},{0,0}});
   for(int i=0;i<s.layers.size();i++)r.shapes.add(new ArrayList<>());
   float[] ribbon={30,30,50,30,50,30.2f,30,30.2f};
   r.shapes.get(0).add(VinylEngine.Shape.polygon(ribbon));
   r.shapes.get(1).add(VinylEngine.Shape.circle(8,9,.1f));
   List<CutWarningLocator.Marker> m=CutWarningLocator.locate(r,4);
   ok(m.size()==2,"Expected two narrow shapes");
   ok(Math.abs(m.get(0).yMm-30.1f)<.01,"Wrong physical coordinate");
   ok(m.get(1).layer==1,"Wrong layer ownership");
   ok(CutWarningLocator.locate(r,1).size()<=1,"Missing bounds");
   System.out.println("PASS cut risk marker coordinates, layer ownership and output bounds");
 }
}

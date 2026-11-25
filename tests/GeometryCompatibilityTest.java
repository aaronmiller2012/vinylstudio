package com.halftonestudio;
import java.util.*;
public final class GeometryCompatibilityTest {
 public static void main(String[] z){
  int w=120,h=140;int[] pix=new int[w*h];boolean[] valid=new boolean[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   int i=y*w+x;valid[i]=(x-60)*(x-60)+(y-60)*(y-60)<50*50;
   pix[i]=valid[i]?(0xff000000|((x%220)<<16)|((y%200)<<8)|110):0;
  }
  for(int style=0;style<8;style++){
    VinylStyleSuite.Config c=new VinylStyleSuite.Config();c.style=style;c.widthMm=150;c.colorCount=6;c.detail=42;c.allowed=valid;
    StyleKit.Result r=VinylStyleSuite.generate(pix,w,h,c);
    try{
     java.util.List<StyleCutGeometryPreflight.Warning> warnings=StyleCutGeometryPreflight.inspect(r,.4f,50);
     System.out.println(VinylStyleSuite.NAMES[style]+": "+r.totalPoints()+" points; "+warnings.size()+" sampled cut risks");
    }catch(Exception e){throw new AssertionError("Unexpected preflight failure for "+VinylStyleSuite.NAMES[style],e);}
  }
 }
}

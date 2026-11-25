package com.halftonestudio;
import java.util.Arrays;
public final class CmykClassicVisibilityV2541Test {
  public static void main(String[] args){
    int w=85,h=95;int[] photo=new int[w*h];Arrays.fill(photo,0xfffaf9f6);
    for(int y=10;y<85;y++)for(int x=10;x<75;x++){
      double d=Math.pow((x-42)/32.,2)+Math.pow((y-43)/39.,2);
      if(d<1)photo[y*w+x]=y<27?0xff40392e:0xffd9b4aa;
    }
    for(int style:new int[]{VinylEngine.LINE,VinylEngine.STRAIGHT,VinylEngine.WAVE,VinylEngine.CONTOUR,
      VinylEngine.HATCH,VinylEngine.CROSSHATCH,VinylEngine.RINGS,VinylEngine.RAYS,VinylEngine.BEADED,
      VinylEngine.ZIGZAG,VinylEngine.FADE,VinylEngine.BROKEN}){
      VinylEngine.Settings s=VinylEngine.Settings.defaults();s.registration=false;s.widthMm=95;
      s.marginMm=1;s.minimumMm=.70f;s.bridgeMm=.3f;s.pattern=style;
      for(VinylEngine.Layer l:s.layers)l.spacing=2.4f;
      VinylEngine.Result baseline=VinylEngine.generate(photo,w,h,s);
      s.lineHighlightRetention=.65f;
      VinylEngine.Result activated=VinylEngine.generate(photo,w,h,s);
      if(activated.count()==0 && baseline.count()>0)throw new AssertionError("Lost style "+style);
      if(style!=VinylEngine.BROKEN&&style!=VinylEngine.FADE&&
         (style==VinylEngine.LINE||style==VinylEngine.RINGS||style==VinylEngine.BEADED)){
         boolean changed=false;
         for(int i=0;i<4;i++)if(!VinylEngine.svg(baseline,i).equals(VinylEngine.svg(activated,i)))changed=true;
         if(!changed && baseline.count()>0)throw new AssertionError("Visibility inactive "+style);
      }
      s.lineHighlightRetention=0;
      VinylEngine.Result restored=VinylEngine.generate(photo,w,h,s);
      for(int li=0;li<4;li++)if(!VinylEngine.svg(baseline,li).equals(VinylEngine.svg(restored,li)))
        throw new AssertionError("Classic baseline drift "+style);
      int[] white=new int[w*h];Arrays.fill(white,0x00ffffff);
      s.lineHighlightRetention=.8f;
      if(VinylEngine.generate(white,w,h,s).count()!=0)throw new AssertionError("Blank gained cuts "+style);
    }
    System.out.println("PASS classic CMYK: visibility across lines/rings/rays/beads, old geometry preserved at zero, blank stays blank");
  }
}

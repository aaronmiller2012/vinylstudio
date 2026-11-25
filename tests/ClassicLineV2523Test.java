package com.halftonestudio;
import java.util.Arrays;

/** User-verified v2.24 geometry and modern-fallback safety checks. */
public final class ClassicLineV2523Test {
  static void require(boolean good,String msg){if(!good)throw new AssertionError(msg);}
  static int[] sample(int w,int h){
    int[] pix=new int[w*h];Arrays.fill(pix,0xfffaf9f6);
    for(int y=12;y<h-10;y++)for(int x=10;x<w-10;x++){
      int r=130+(x%35)*3,g=95+(y%24)*4,b=76+(x+y)%85;
      pix[y*w+x]=0xff000000|(Math.min(255,r)<<16)|(Math.min(255,g)<<8)|Math.min(255,b);
    }
    return pix;
  }
  public static void main(String[] args){
    VinylEngine.Settings defaultSettings=VinylEngine.Settings.defaults();
    require(defaultSettings.classicCmykLines,"new projects must open in v2.24 line mode");
    require(defaultSettings.blackColorProtection==.18f,"black setting wrong");
    require(defaultSettings.shadowColorRecovery==.48f,"shadow setting wrong");
    require(defaultSettings.portraitMidtones==.82f,"midtone setting wrong");
    require(defaultSettings.lineContinuity==0f && defaultSettings.linePresenceRecovery==0f
        && defaultSettings.lineHighlightRetention==0f && defaultSettings.portraitBackgroundGuard==0f,
        "older line filtering is still active");
    require(defaultSettings.layers.get(1).strength==1.12f,"original cyan recovery missing");
    int[] originalColors=new int[4];for(int i=0;i<4;i++)originalColors[i]=defaultSettings.layers.get(i).color;
    VinylEngine.Settings classic=defaultSettings.copy();
    for(int i=0;i<4;i++)require(classic.layers.get(i).color==originalColors[i],"copy changed swatches");
    require(classic.classicCmykLines,"copy dropped classic engine");
    classic.layers.get(1).color=0xff196851;
    ClassicCmykLines.apply(classic);
    require(classic.layers.get(1).color==0xff196851,"classic mode overwrote edited cyan swatch");
    classic.layers.get(1).angleFineTuneDeg=17f;
    ClassicCmykLines.apply(classic);
    require(classic.layers.get(1).angleFineTuneDeg==17f,"classic mode moved angles");
    ClassicCmykLines.useModern(classic);
    require(!classic.classicCmykLines,"modern option failed");
    require(classic.layers.get(1).color==0xff196851,"modern option overwrote swatch");
    require(classic.copy().classicCmykLines==false,"copy lost modern engine choice");
    classic=defaultSettings.copy();classic.pattern=VinylEngine.LINE;
    classic.widthMm=95;classic.minimumMm=.65f;classic.marginMm=2;classic.bridgeMm=.35f;classic.registration=false;
    int w=95,h=110;int[] rgb=sample(w,h);
    VinylEngine.Result result=VinylEngine.generate(rgb,w,h,classic);
    int active=0;
    for(int i=0;i<4;i++){
      require(VinylEngine.svgElementsForLayer(result,i)<=5000,"Cricut path limit exceeded");
      require(VinylEngine.svg(result,i).contains("<svg"),"missing vector layer");
      require(!VinylEngine.svg(result,i).contains("clipPath"),"SVG clipping is not cut geometry");
      if(!result.shapes.get(i).isEmpty())active++;
    }
    require(active>=3,"classic lost CMYK channels");
    int[] empty=new int[w*h];Arrays.fill(empty,0xffffffff);
    require(VinylEngine.generate(empty,w,h,classic).count()==0,"white background produced cuts");
    Arrays.fill(empty,0x00ffffff);
    require(VinylEngine.generate(empty,w,h,classic).count()==0,"transparent background produced cuts");
    // Newer screen modes (stipple, shapes, etc.) must not be affected by classic line routing.
    classic.pattern=VinylEngine.ROUND;
    require(VinylEngine.generate(rgb,w,h,classic).count()>0,"other CMYK shape modes broken");
    System.out.println("PASS v2.52.3 profile, edit preservation, background exclusions, 4 CMYK layers, fallback");
  }
}

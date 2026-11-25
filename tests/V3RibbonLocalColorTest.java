package com.halftonestudio;
import java.util.Arrays;
/** Composite-level tests. Equal row/column means deliberately defeat whole-strand color selection. */
public final class V3RibbonLocalColorTest {
 static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static final int[] PALETTE={0xff43251c,0xffba8060,0xff37616f,0xffe2c9a7};
 static int imageColor(int x,int y,int w,int h){return PALETTE[(x<w/2)==(y<h/2)?1:2];}
 public static void main(String[] args){
  int w=128,h=128;int[] pixels=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)pixels[y*w+x]=imageColor(x,y,w,h);
  for(float mm:new float[]{76.2f,101.6f,279.4f}){
   ExpandedPortraitStylesEngine.Options o=new ExpandedPortraitStylesEngine.Options();o.style=ExpandedPortraitStylesEngine.WOVEN_RIBBON;o.widthMm=mm;o.palette=PALETTE.clone();o.detail=68;
   StyleKit.Result result=ExpandedPortraitStylesEngine.generate(pixels,w,h,o);int right=0,ink=0;
   for(int y=1;y<81;y++)for(int x=1;x<81;x++){
    float u=x/81f,v=y/81f;if(Math.abs(u-.5f)<.09f||Math.abs(v-.5f)<.09f)continue;
    int visible=0;for(StyleKit.Layer l:result.layers)if(PrismaticWeaveV3Test.contains(l,u*result.widthMm,v*result.heightMm))visible=l.color;
    if(visible!=0){ink++;if(visible==imageColor((int)(u*w),(int)(v*h),w,h))right++;}
   }
   System.out.printf("Woven %.1f mm: correct locally visible ink %d/%d (%.1f%%)%n",mm,right,ink,100.*right/Math.max(1,ink));
   require(ink>200&&right>=ink*.90,"local ribbon colors do not follow the source quadrants");
   require(Arrays.equals(o.palette,PALETTE),"swatch mutation");
   o.paperArgb=0xff12a3c4;require(StyleKit.toSvg(result,false).equals(StyleKit.toSvg(ExpandedPortraitStylesEngine.generate(pixels,w,h,o),false)),"mounting surface changed geometry");
  }
  float[][] rgb={RibbonColorMath.rgb(0xff000000),RibbonColorMath.rgb(0xffffffff),RibbonColorMath.rgb(0xff000000)};
  float l=RibbonColorMath.composite(rgb,1,0,.5f,0)[0];require(l>75&&l<77,"50% white on black must average in linear light, not Lab or gamma RGB");
  require(Math.abs(RibbonColorMath.composite(rgb,1,2,1,1)[0])<.01f,"higher physical layer must occlude lower color");
  System.out.println("PASS local ribbon color, mounting isolation and opaque compositing");
 }
}

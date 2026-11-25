package com.halftonestudio;
import java.nio.file.*;
public final class V3PopPosterPortParityTest {
static int freq(byte[] b,int n){int x=0;for(byte v:b)if(v==n)x++;return x;}
public static void main(String[] args)throws Exception {
 int w=144,h=192;byte[] rgba=Files.readAllBytes(Path.of(args[0]+"/fixture-rgba.bin"));int[] pixels=new int[w*h];
 for(int i=0;i<pixels.length;i++)pixels[i]=0xff000000 | ((rgba[4*i]&255)<<16) | ((rgba[4*i+1]&255)<<8) | (rgba[4*i+2]&255);
 PopPosterEngine.Options o=new PopPosterEngine.Options();o.bgMode=PopPosterEngine.BG_OFF;
 PopPosterEngine.Pre pre=PopPosterEngine.preprocess(pixels,w,h,o,null);
 o.keyOff=2f/Math.min(w,h);o.blockOff=2f/Math.min(w,h);
 PopPosterEngine.Labels pop=PopPosterEngine.buildPop(pre,o);
 o.style=PopPosterEngine.POSTER;o.toneCount=4;o.minIslandPx=60;o.texture=true;o.texturePeriod=9;o.textureTone=2;o.textureFill=.5f;o.frame=.03f;
 PopPosterEngine.Labels poster=PopPosterEngine.buildPoster(pre,o);
 System.out.println("JAVA poster thresholds "+java.util.Arrays.toString(poster.thresholds));
 System.out.println("JAVA poster histogram "+java.util.Arrays.toString(new int[]{freq(poster.data,0),freq(poster.data,1),freq(poster.data,2),freq(poster.data,3)}));
 for(String mode:new String[]{"pop","poster"}){
  byte[] expected=Files.readAllBytes(Path.of(args[0]+"/fixture-"+mode+".bin"));
  byte[] actual=mode.equals("pop")?pop.data:poster.data;
  int same=0;for(int i=0;i<expected.length;i++)if(expected[i]==actual[i])same++;
  double ratio=(double)same/expected.length;
  System.out.printf("%s label equality %.3f%%%n",mode,ratio*100);
  if(ratio<1.0)throw new AssertionError("<98% parity in "+mode);
 }
}
}

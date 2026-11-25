package com.halftonestudio;
import java.util.Arrays;
public final class V3OrganicRenderStallTest {
 public static void main(String[] a){
  int n=a.length>0?Integer.parseInt(a[0]):0;
  float mm=a.length>1?Float.parseFloat(a[1]):279.4f;
  float min=a.length>2?Float.parseFloat(a[2]):.254f;
  int colors=a.length>3?Integer.parseInt(a[3]):6;
  int w=850,h=987;int[] p=new int[w*h];boolean[] valid=new boolean[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   double xx=(x-w/2.0)/(w*.48),yy=(y-h/2.0)/(h*.50);
   valid[y*w+x]=xx*xx+yy*yy<1;
   int r=Math.max(0,Math.min(255,(int)(92+70*xx+32*Math.sin(xx*19+yy*7))));
   int g=Math.max(0,Math.min(255,(int)(132+44*yy+40*Math.cos(yy*22+xx*6))));
   int b=Math.max(0,Math.min(255,(int)(166-40*xx+26*Math.sin(yy*25))));
   if(Math.abs(yy+.25)<.025&&(Math.abs(xx+.30)<.12||Math.abs(xx-.3)<.12))r=g=b=15;
   p[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
  }
  OrganicFlowPortraitEngine.Options o=new OrganicFlowPortraitEngine.Options();
  o.mode=n;o.detail=100;o.flow=2.5f;o.layers=colors;o.smoothing=50;o.widthMm=mm;
  o.minCutMm=min;o.gapMm=.5f;o.allowed=valid;
  long start=System.nanoTime();System.out.println("START mode="+n+" width="+mm+" min="+min+" layers="+colors);System.out.flush();
  StyleKit.Result r=OrganicFlowPortraitEngine.generate(p,w,h,o);
  long end=System.nanoTime();System.out.println("DONE ms="+((end-start)/1000000)+" loops="+r.layers.stream().mapToInt(l->l.loops.size()).sum()+" points="+r.totalPoints());
 }
}

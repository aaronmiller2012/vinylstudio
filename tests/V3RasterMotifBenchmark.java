package com.halftonestudio;
import java.util.*;
public class V3RasterMotifBenchmark {
 public static void main(String[] args){
 int w=140,h=160;int[] p=new int[w*h];boolean[] m=new boolean[w*h];
 for(int y=0;y<h;y++)for(int x=0;x<w;x++){
  double xx=(x-w*.5)/(w*.47),yy=(y-h*.5)/(h*.47);
  int t=(int)Math.max(12,Math.min(245,120+90*xx+25*Math.sin(yy*6)-40*Math.exp(-20*(xx*xx+yy*yy))));
  p[y*w+x]=0xff000000|t<<16|Math.max(0,t-10)<<8|Math.min(255,t+10);m[y*w+x]=xx*xx+yy*yy<=1;
 }
 for(float width:new float[]{101.6f,279.4f})for(int style:new int[]{35,40,37,39,28,30,33,41,42,31,27,32,34}){
  VinylStyleSuite.Config c=new VinylStyleSuite.Config();c.style=style;c.widthMm=width;c.detail=65;c.colorCount=4;c.allowed=m;
  c.palette=new int[]{0xff222222,0xff64523f,0xffaaa8a1,0xffeeeeee};
  long t=System.nanoTime();StyleKit.Result r=VinylStyleSuite.generate(p,w,h,c);long elapsed=(System.nanoTime()-t)/1000000;
  long hash=0,base=0;int contours=0,points=0;double jag=0;
  for(int j=0;j<r.layers.size();j++)for(float[] loop:r.layers.get(j).loops){
   if(j==0){base=base*31+Arrays.hashCode(loop);continue;}contours++;points+=loop.length/2;hash=hash*31+Arrays.hashCode(loop);
   int n=loop.length/2;for(int k=0;k<n;k++){
    int a=(k+n-1)%n,b=(k+1)%n;double ax=loop[2*k]-loop[2*a],ay=loop[2*k+1]-loop[2*a+1],bx=loop[2*b]-loop[2*k],by=loop[2*b+1]-loop[2*k+1];
    double al=Math.hypot(ax,ay),bl=Math.hypot(bx,by);
    if(Math.min(al,bl)<.7&&al*bl>1e-10){double angle=Math.acos(Math.max(-1,Math.min(1,(ax*bx+ay*by)/(al*bl))));jag+=angle*angle;}
   }
  }
  System.out.printf(Locale.ROOT,"%d %.1f %d %d %.3f %d %d %d%n",style,width,contours,points,jag,hash,base,elapsed);
 }
 }
}

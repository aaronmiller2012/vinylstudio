package com.halftonestudio;
import java.util.*;
public final class SharedBoundaryPerfV2546Test {
  public static void main(String[] args){
    int w=500,h=500,k=7;int[] pixels=new int[w*h];int[] c={0xff493227,0xff705040,0xff987462,0xffb98e75,0xffd5aa8f,0xffe9c4a8,0xfff0dbc4};
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){
      double wave=22*Math.sin(y*.08)+3*Math.sin(y*.50);
      int color=Math.max(0,Math.min(k-1,(int)((x+wave)*k/w)));
      pixels[y*w+x]=c[color];
    }
    MultilayerStencilEngine.Options o=new MultilayerStencilEngine.Options();o.layerCount=k;o.sourceColorMode=true;o.autoWhiteBackground=false;o.registration=false;o.widthMm=280;o.heightMm=280;o.detail=.12f;
    long t=System.nanoTime();MultilayerStencilEngine.Result r=MultilayerStencilEngine.generate(pixels,w,h,o);long a=System.nanoTime();
    int bytes=0;for(int j=0;j<k;j++)bytes+=r.cutPathData(j).length();long b=System.nanoTime();
    System.out.println("250k photo pixels: quantize "+(a-t)/1000000+"ms, shared vectors "+(b-a)/1000000+"ms; SVG chars="+bytes+"; mode="+r.contourMode());
  }
}

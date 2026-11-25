package com.halftonestudio;
public final class PortraitStructureMapTest {
 public static void main(String[] args){
  int w=64,h=64;int[] px=new int[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   int v=x<32?40:210;px[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;
  }
  PortraitStructureMap m=new PortraitStructureMap(px,w,h);
  if(m.dark(10,20)<=m.dark(50,20))throw new AssertionError("tone orientation");
  if(m.edge(32,20)<=m.edge(10,20))throw new AssertionError("edge strength");
  System.out.println("PASS: shared portrait contrast and edge map");
 }
}

package com.halftonestudio;
public final class V221TonalCountTest {
  public static void main(String[] args) {
    int w=80,h=80;int[] px=new int[w*h];
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){
      int v=(int)Math.round(105+72.0*x/(w-1)+8*Math.sin(y*.20));
      px[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;
    }
    for(int count=2;count<=8;count++){
      MultilayerStencilEngine.Options opt=new MultilayerStencilEngine.Options();
      opt.layerCount=count;opt.registration=false;opt.blurRadius=1;
      opt.minimumIslandPixels=0;
      MultilayerStencilEngine.Result r=MultilayerStencilEngine.generate(px,w,h,opt);
      if(r.layers.size()!=count)throw new AssertionError("Incorrect layer list");
      int[] owners=new int[w*h];int populated=0;
      for(MultilayerStencilEngine.Layer layer:r.layers){
        if(layer.pixelCount>0)populated++;
        for(int i=0;i<owners.length;i++)if(layer.visibleMask[i])owners[i]++;
      }
      if(populated!=count)throw new AssertionError("Populated "+populated+" of "+count);
      for(int n:owners)if(n!=1)throw new AssertionError("Nonexclusive pixel ownership");
    }
    System.out.println("PASS: V2.21 2–8 balanced tonal bands and exclusive visible ownership (physical underlays may overlap)");
  }
}

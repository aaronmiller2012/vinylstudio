package com.halftonestudio;
/** Deterministic 11-in portrait fingerprint, compared to code 505 before release. */
public final class V3OptimizedOrganicGeometryFingerprintTest {
  static long fingerprint(StyleKit.Result result){
    long hash=0xcbf29ce484222325L;
    for(StyleKit.Layer l:result.layers){
      hash=(hash^l.color)*0x100000001b3L;
      hash=(hash^l.loops.size())*0x100000001b3L;
      for(float[] loop:l.loops)for(float v:loop)
        hash=(hash^Float.floatToIntBits(v))*0x100000001b3L;
    }
    return hash;
  }
  public static void main(String[] a){
    int w=220,h=259;int[] p=new int[w*h];boolean[] valid=new boolean[w*h];
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){
      double xx=(x-w/2.0)/(w*.48),yy=(y-h/2.0)/(h*.5);
      valid[y*w+x]=xx*xx+yy*yy<1;
      int r=Math.max(0,Math.min(255,(int)(96+69*xx+38*Math.sin(xx*40+yy*36))));
      int g=Math.max(0,Math.min(255,(int)(135+38*yy+48*Math.cos(yy*53+xx*60))));
      int b=Math.max(0,Math.min(255,(int)(164-38*xx+49*Math.sin(yy*47+xx*15))));
      p[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
    }
    for(int i=0;i<3;i++){
      OrganicFlowPortraitEngine.Options o=new OrganicFlowPortraitEngine.Options();
      o.mode=i;o.widthMm=279.4f;o.minCutMm=.8f;o.gapMm=.5f;
      o.smoothing=50;o.detail=93;o.flow=2.1f;o.layers=6;o.allowed=valid;
      long hash=fingerprint(OrganicFlowPortraitEngine.generate(p,w,h,o));
      System.out.println("mode="+i+" fingerprint="+Long.toHexString(hash));
    }
  }
}

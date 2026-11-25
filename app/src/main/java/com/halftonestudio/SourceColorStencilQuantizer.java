package com.halftonestudio;
import java.util.Arrays;
/** Deterministic approximate perceptual k-means. Every vector owner comes from the original photo. */
public final class SourceColorStencilQuantizer {
  private SourceColorStencilQuantizer(){}
  public static final class Result {
    public final int[] owner,colors;
    Result(int[] o,int[] c){owner=o;colors=c;}
  }
  public static Result quantize(int[] pixels,int w,int h,int k,boolean[] valid){
    return quantize(pixels,w,h,k,valid,false);
  }
  /** @param oklab experimental: cluster in OKLab (M5 evaluation). Production default stays CIELAB so stencil geometry/goldens are unchanged. */
  static Result quantize(int[] pixels,int w,int h,int k,boolean[] valid,boolean oklab){
    if(w<1||h<1||pixels==null||pixels.length!=(long)w*h||valid==null||valid.length!=pixels.length||k<2||k>8)throw new IllegalArgumentException("Invalid quantization source");
    int n=pixels.length,count=0;for(boolean b:valid)if(b)count++;
    if(count==0)throw new IllegalArgumentException("No source pixels after snipping");
    int step=Math.max(1,count/5000);
    int sampleCount=(count+step-1)/step;
    float[][] sample=new float[sampleCount][3];int[] sampleArgb=new int[sampleCount];
    int seen=0,placed=0;
    for(int i=0;i<n;i++)if(valid[i]){
        if(seen%step==0){sampleArgb[placed]=pixels[i];lab(pixels[i],sample[placed],oklab);placed++;}
        seen++;
    }
    sample=Arrays.copyOf(sample,placed);sampleArgb=Arrays.copyOf(sampleArgb,placed);
    float[][] centers=new float[k][3];
    // Seed darkest and then farthest apart in Lab (not forced equal-area bands).
    int darkest=0;for(int i=1;i<placed;i++)if(sample[i][0]<sample[darkest][0])darkest=i;
    centers[0]=sample[darkest].clone();
    for(int j=1;j<k;j++){
       int far=0;float farDistance=-1;
       for(int i=0;i<placed;i++){float d=Float.POSITIVE_INFINITY;for(int c=0;c<j;c++)d=Math.min(d,distance(sample[i],centers[c]));
          if(d>farDistance){farDistance=d;far=i;}}
       centers[j]=sample[far].clone();
    }
    for(int iteration=0;iteration<12;iteration++){
      double[][] sums=new double[k][3];int[] counts=new int[k];
      for(float[] px:sample){int closest=nearest(px,centers);counts[closest]++;for(int j=0;j<3;j++)sums[closest][j]+=px[j];}
      for(int c=0;c<k;c++)if(counts[c]!=0)for(int j=0;j<3;j++)centers[c][j]=(float)(sums[c][j]/counts[c]);
    }
    // Report mean ACTUAL source RGB for each cluster, not idealized synthetic colors.
    long[][] rgb=new long[k][3];long[] counts=new long[k];
    int[] owner=new int[n];Arrays.fill(owner,-1);float[] px=new float[3];
    for(int i=0;i<n;i++)if(valid[i]){
      lab(pixels[i],px,oklab);int c=nearest(px,centers);owner[i]=c;counts[c]++;
      rgb[c][0]+=(pixels[i]>>16)&255;rgb[c][1]+=(pixels[i]>>8)&255;rgb[c][2]+=pixels[i]&255;
    }
    int[] colors=new int[k];
    for(int c=0;c<k;c++){
      if(counts[c]==0){colors[c]=0xff808080;continue;}
      int red=(int)(rgb[c][0]/counts[c]),green=(int)(rgb[c][1]/counts[c]),blue=(int)(rgb[c][2]/counts[c]);
      colors[c]=0xff000000|(red<<16)|(green<<8)|blue;
    }
    // Stable color ID order is derived from SOURCE Lab centers, not the selected
    // physical vinyl swatch. Editing a swatch may never change its source owner.
    Integer[] order=new Integer[k];for(int i=0;i<k;i++)order[i]=i;
    Arrays.sort(order,(a,b)->{
        if(counts[a]==0&&counts[b]!=0)return 1;
        if(counts[b]==0&&counts[a]!=0)return -1;
        int c=Float.compare(centers[a][0],centers[b][0]);
        if(c!=0)return c;
        c=Float.compare(centers[a][1],centers[b][1]);if(c!=0)return c;
        c=Float.compare(centers[a][2],centers[b][2]);return c!=0?c:Integer.compare(a,b);
    });
    int[] remap=new int[k],orderedColors=new int[k];
    for(int i=0;i<k;i++){int source=order[i];remap[source]=i;orderedColors[i]=colors[source];}
    for(int i=0;i<n;i++)if(owner[i]>=0)owner[i]=remap[owner[i]];
    return new Result(owner,orderedColors);
  }
  /** Match source pixels to ONLY the selected physical vinyl swatches.
   * Swatch indices are stable; no k-means cluster reorder and no fake colors. */
  public static Result quantizeToPalette(int[] pixels,int w,int h,int[] swatches,int k,boolean[] valid){
    if(w<1||h<1||pixels==null||pixels.length!=(long)w*h||valid==null||valid.length!=pixels.length
       ||k<2||k>8||swatches==null||swatches.length<k)throw new IllegalArgumentException("Invalid vinyl palette");
    int[] colors=Arrays.copyOf(swatches,k);
    for(int c=0;c<k;c++){
      if(colors[c]==0)throw new IllegalArgumentException("Missing vinyl swatch "+(c+1));
      colors[c]|=0xff000000;
    }
    PhotoSwatchAssociation association=new PhotoSwatchAssociation(
        colors,k,null,pixels,valid,PhotoSwatchAssociation.STENCIL);
    int[] owner=new int[pixels.length];Arrays.fill(owner,-1);
    for(int i=0;i<pixels.length;i++)if(valid[i]){
      if((i&4095)==0&&Thread.currentThread().isInterrupted())
        throw new java.util.concurrent.CancellationException("Color matching cancelled");
      owner[i]=association.chooseCached(pixels[i]);
    }
    return new Result(owner,colors);
  }
  private static int nearest(float[] px,float[][] means){float best=Float.POSITIVE_INFINITY;int sel=0;for(int i=0;i<means.length;i++){float d=distance(px,means[i]);if(d<best){best=d;sel=i;}}return sel;}
  private static float distance(float[] a,float[] b){float dl=a[0]-b[0],da=a[1]-b[1],db=a[2]-b[2];return dl*dl+da*da+db*db;}
  /** D65 CIELAB; sufficient for palette grouping without a device ICC profile. */
  private static void lab(int color,float[] out,boolean oklab){
    if(oklab){double[] o=PerceptualVinylColor.lab(color);out[0]=(float)o[0];out[1]=(float)o[1];out[2]=(float)o[2];return;}
    float r=linear((color>>16)&255),g=linear((color>>8)&255),b=linear(color&255);
    float x=(.4124564f*r+.3575761f*g+.1804375f*b)/.95047f;
    float y=(.2126729f*r+.7151522f*g+.0721750f*b);
    float z=(.0193339f*r+.1191920f*g+.9503041f*b)/1.08883f;
    float fx=pivot(x),fy=pivot(y),fz=pivot(z);
    out[0]=116*fy-16;out[1]=500*(fx-fy);out[2]=200*(fy-fz);
  }
  private static final float[] LINEAR=new float[256];
  static{for(int i=0;i<256;i++){float c=i/255f;LINEAR[i]=c<=.04045f?c/12.92f:(float)Math.pow((c+.055f)/1.055f,2.4);}}
  private static float linear(int color){return LINEAR[color&255];}
  private static float pivot(float t){return t>.008856f?(float)Math.cbrt(t):7.787f*t+16f/116f;}
}

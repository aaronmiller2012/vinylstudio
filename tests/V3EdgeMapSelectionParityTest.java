package com.halftonestudio;
import java.util.Arrays;
import java.util.Random;
/** Exact 97th-percentile equivalence, including non-square and degenerate images. */
public final class V3EdgeMapSelectionParityTest {
  static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
  static float[] reference(float[] field,int w,int h){
    float[] g=new float[w*h];
    for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){
      int i=y*w+x;
      float gx=(field[i-w+1]+2*field[i+1]+field[i+w+1])-(field[i-w-1]+2*field[i-1]+field[i+w-1]);
      float gy=(field[i+w-1]+2*field[i+w]+field[i+w+1])-(field[i-w-1]+2*field[i-w]+field[i-w+1]);
      g[i]=(float)Math.sqrt(gx*gx+gy*gy);
    }
    float[] sorted=g.clone();Arrays.sort(sorted);
    float p=Math.max(1e-6f,sorted[(int)(sorted.length*.97f)]);
    for(int i=0;i<g.length;i++)g[i]=Math.min(1f,g[i]/p);
    return g;
  }
  public static void main(String[] argv){
    Random random=new Random(0xC010CADEL);
    for(int[] wh:new int[][]{{2,2},{3,3},{16,21},{80,120},{513,313},{850,987}}){
      int w=wh[0],h=wh[1];
      for(int mode=0;mode<5;mode++){
        float[] lum=new float[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
          float value=mode==0?.5f:mode==1?(x%13==0?1:0):mode==2?x/(float)w:
              mode==3?random.nextFloat():(float)((x*y)%71)/71;
          lum[y*w+x]=value;
        }
        float[] a=reference(lum,w,h),b=StyleKit.edgeMap(lum,w,h);
        check(a.length==b.length,"Length mismatch");
        for(int i=0;i<a.length;i++)if(Float.floatToIntBits(a[i])!=Float.floatToIntBits(b[i]))
          throw new AssertionError("Percentile mismatch "+w+"x"+h+" mode="+mode+" pixel="+i);
      }
    }
    for(int n:new int[]{1,2,3,4,8,13,67,1000,100001}){
      float[] values=new float[n];
      for(int mode=0;mode<4;mode++){
        for(int i=0;i<n;i++)values[i]=mode==0?i:mode==1?(n-i):mode==2?(i%7):random.nextFloat();
        float[] baseline=values.clone();Arrays.sort(baseline);
        int[] positions={0,n/2,Math.min(n-1,(int)(n*.97f)),n-1};
        for(int k:positions){
          float actual=StyleKit.selectPercentile(values.clone(),k);
          if(Float.floatToIntBits(baseline[k])!=Float.floatToIntBits(actual))
             throw new AssertionError("select mismatch n="+n+" k="+k);
        }
      }
    }
    System.out.println("PASS exact edge-map and percentile selection parity");
  }
}

package com.halftonestudio;
import java.util.*;
public final class V3MilestoneA3FractionalSnipTest {
 static double area(float[]p){double a=0;for(int i=0;i<p.length;i+=2){int j=(i+2)%p.length;a+=(double)p[i]*p[j+1]-(double)p[j]*p[i+1];}return Math.abs(a*.5);}
 static double resultArea(List<RasterRegionClipper.Region> r){double a=0;for(var e:r){a+=area(e.outer);for(float[]hole:e.holes)a-=area(hole);}return a;}
 public static void main(String[]args){Random random=new Random(661123);int good=0,fail=0;
 for(int test=0;test<1200;test++){
  int w=5+random.nextInt(18),h=5+random.nextInt(18);int[]src=new int[w*h];Arrays.fill(src,0xff000000);
  boolean[]mask=new boolean[w*h];for(int i=0;i<mask.length;i++)mask[i]=random.nextDouble()<(.08+(test%10)*.085);
  float x0=(float)(random.nextDouble()*2),y0=(float)(random.nextDouble()*2),x1=w-(float)(random.nextDouble()*2),y1=h-(float)(random.nextDouble()*2);
  if(x1<=x0||y1<=y0)continue;
  float[]outer={0,0,w,0,w,h,0,h};
  double expected=0;for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(mask[y*w+x])expected+=Math.max(0,Math.min(x+1,x1)-Math.max(x,x0))*Math.max(0,Math.min(y+1,y1)-Math.max(y,y0));
  try {var res=RasterRegionClipper.clip(outer,null,src,mask,w,h,0,0,w,h,x0,y0,x1,y1,1f,0);
       double observed=resultArea(res);
       if(Math.abs(observed-expected)>Math.max(.03,expected*.001)){fail++;if(fail<=7)System.out.printf("mismatch #%d expected %.4f observed %.4f rect=[%.3f %.3f %.3f %.3f] N=%d%n",test,expected,observed,x0,y0,x1,y1,res.size());}
       else good++;
  }catch(Exception ex){fail++;if(fail<=7)System.out.println("Exception "+test+" "+ex);}
 }
 System.out.println("A3 fractional clip parity "+good+" valid, "+fail+" mismatched");if(fail>0)System.exit(1);
 }
}

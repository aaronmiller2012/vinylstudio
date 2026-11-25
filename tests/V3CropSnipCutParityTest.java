package com.halftonestudio;
import java.util.*;
/** Full generated cut-shape parity, excluding a safe margin around crop boundaries. */
public final class V3CropSnipCutParityTest {
 static void ok(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 static int[] photo(int w,int h){int[] a=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){
  double u=(x+.5)/(double)w,v=(y+.5)/(double)h;double dx=(u-.5)/.31,dy=(v-.49)/.42;
  int p=0xffede7df;
  if(dx*dx+dy*dy<1){int lum=(int)(187+29*Math.sin(u*29)*Math.cos(v*17));int r=Math.max(0,Math.min(255,lum+18)),g=Math.max(0,Math.min(255,lum-18)),b=Math.max(0,Math.min(255,lum-8));p=0xff000000|(r<<16)|(g<<8)|b;
  if((v>.37&&v<.4&&(u<.43||u>.57))||(v>.64&&v<.67))p=0xff40322d;
  if(v<.22)p=0xff302b2a;
  }a[y*w+x]=p;}return a;}
 static VinylEngine.Settings config(boolean mask,boolean classic, int style, float pitch){VinylEngine.Settings s=VinylEngine.Settings.defaults();s.registration=false;s.widthMm=140;s.marginMm=2;s.minimumMm=.65f;s.bridgeMm=.45f;s.pattern=style;
  s.linkedControls=true;s.masterSpacingMm=pitch;s.masterContrast=1.1f;s.lineHighlightRetention=.85f;s.linePresenceRecovery=.65f;s.portraitBackgroundGuard=.55f;s.opaqueLineBalance=true;s.classicCmykLines=classic;
  if(mask)s.snips.add(new SnipMask.Operation(SnipMask.KEEP_INSIDE,.025f,.025f,.975f,.025f,.975f,.975f,.025f,.975f));return s;}
 static boolean polyContains(float[] xy,float x,float y){boolean inside=false;int n=xy.length/2;for(int i=0,j=n-1;i<n;j=i++){float ax=xy[2*i],ay=xy[2*i+1],bx=xy[2*j],by=xy[2*j+1];if((ay>y)!=(by>y)&&x<(bx-ax)*(y-ay)/(by-ay)+ax)inside=!inside;}return inside;}
 static boolean occupied(List<VinylEngine.Shape> shapes,float x,float y){for(VinylEngine.Shape p:shapes){if(p.circle){if((x-p.x)*(x-p.x)+(y-p.y)*(y-p.y)<=p.r*p.r)return true;continue;}float[] b=p.bounds();if(x<b[0]||x>b[2]||y<b[1]||y>b[3])continue;if(polyContains(p.xy,x,y))return true;}return false;}
 static double[] compare(VinylEngine.Result a,VinylEngine.Result b){int n=0,diff=0,cut=0;for(int li=0;li<a.shapes.size();li++)for(int iy=0;iy<31;iy++)for(int ix=0;ix<31;ix++){
    float x=a.settings.widthMm*(.35f+ix*.010f),y=a.heightMm*(.35f+iy*.010f);boolean av=occupied(a.shapes.get(li),x,y),bv=occupied(b.shapes.get(li),x,y);n++;if(av!=bv)diff++;if(av||bv)cut++;
   }return new double[]{n,diff,diff/(double)n,cut};}
 public static void main(String[] args){
  int w=150,h=175;int[] pixels=photo(w,h),alpha=pixels.clone();
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   float dx=(x-w*.5f)/(w*.5f),dy=(y-h*.5f)/(h*.5f);
   if(dx*dx+dy*dy>1)alpha[y*w+x]=0;
  }
  int matched=0,affected=0;
  for(boolean classic:new boolean[]{true,false})
   for(int style:new int[]{VinylEngine.CONTOUR,VinylEngine.LINE,VinylEngine.HATCH})
    for(float pitch:new float[]{3.175f,6.223f}){
     VinylEngine.Settings plain=config(false,classic,style,pitch);
     VinylEngine.Settings masked=config(true,classic,style,pitch);
     VinylEngine.Result r=VinylEngine.generate(pixels,w,h,plain);
     VinylEngine.Result snip=VinylEngine.generate(pixels,w,h,masked);
     VinylEngine.Result crop=VinylEngine.generate(alpha,w,h,plain);
     double[] sr=compare(r,snip),ar=compare(r,crop);
     ok(sr[3]>35,"Fixture did not produce sufficient line coverage");
     ok(sr[2]<.002,"Snip changed protected center cuts engine="+(classic?"classic":"modern")+
         " style="+style+" pitch="+pitch+" changed="+sr[1]+"/"+sr[0]);
     ok(ar[2]<.002,"Transparent crop changed protected center cuts engine="+(classic?"classic":"modern")+
         " style="+style+" pitch="+pitch+" changed="+ar[1]+"/"+ar[0]);
     LineCutDiagnostics.Report diag=LineCutDiagnostics.inspect(snip),origDiag=LineCutDiagnostics.inspect(r);
     ok(diag.complete&&diag.selfIntersections<=origDiag.selfIntersections&&diag.subMinimumWidthSamples<=origDiag.subMinimumWidthSamples,
         "Mask produced unsafe geometry engine="+(classic?"classic":"modern")+" style="+style);
     for(int li=0;li<r.lineMetrics.size();li++){
       LinePresenceTracker.Metrics before=r.lineMetrics.get(li),after=snip.lineMetrics.get(li);
       if(before.promoted>0){
         ok(after.promoted>0,"Snip disabled line-presence recovery engine="+
            (classic?"classic":"modern")+" style="+style+" layer="+li);
         affected++;
       }
     }
     matched++;
    }
  ok(affected>0,"Fixture failed to exercise line-retention regression");
  System.out.println("PASS crop/Snip final line-sheet center parity: "+matched+
      " engine/style/spacing combinations; "+affected+" retained recovery layers");
 }
}

import com.halftonestudio.*;
import java.util.*;
/** No user photos packaged. Synthetic eligible vs paper/transparent region. */
public final class AdaptiveStippleCoverageTest {
 static void ok(boolean value,String msg){if(!value)throw new AssertionError(msg);}
 static int[] image(int w,int h){
  int[] img=new int[w*h];Arrays.fill(img,0xfffaf9f6);
  for(int y=15;y<h-15;y++)for(int x=16;x<w-16;x++){
   if(x>=w/2-5&&x<=w/2+5&&y<h/3)continue; // real background gap
   img[y*w+x]=x<w/2?0xff6a4645:0xffbc8794;
  }
  return img;
 }
 static double frac(MultiColorStippleEngine.Result r){return r.eligiblePixels==0?0:
  r.coveredPixels/(double)r.eligiblePixels;}
 static void noWrongDots(MultiColorStippleEngine.Result r,int[] src,int w,int h){
  for(var d:r.dots){int x=(int)d.x,y=(int)d.y;int px=src[y*w+x];
   ok((px>>>24)>=16 && px!=0xfffaf9f6,"point in blank background");
   ok(d.r>0 && Float.isFinite(d.r),"invalid dot radius");
  }
 }
 static void noSameSheetCollisions(MultiColorStippleEngine.Result r){
  // Bin the fixed-size synthetic test points; this loop stays small.
  for(int i=0;i<r.dots.size();i++)for(int j=0;j<i;j++){
   var a=r.dots.get(i);var b=r.dots.get(j);if(a.layer!=b.layer)continue;
   double dx=a.x-b.x,dy=a.y-b.y;
   ok(dx*dx+dy*dy+0.002>=(a.r+b.r)*(a.r+b.r)-.05,"same-sheet overlapping dots");
  }
 }
 public static void main(String[] args){
  int w=260,h=328;int[] src=image(w,h);
  var options=new MultiColorStippleEngine.Options();
  options.colors=new int[]{0xff6a4645,0xffbc8794};options.layerCount=2;
  options.detail=100;options.widthMm=110;options.overlap=MultiColorStippleEngine.OVERPRINT;
  for(boolean draft:new boolean[]{true,false}){
   options.draft=draft;options.adaptiveCoverage=false;
   var legacy=MultiColorStippleEngine.generate(src,w,h,options);
   options.adaptiveCoverage=true;
   var improved=MultiColorStippleEngine.generate(src,w,h,options);
   System.out.printf(Locale.US,"draft=%s legacy=%.2f%% adaptive=%.2f%% dots=%d/%d%n",
     draft,frac(legacy)*100,frac(improved)*100,legacy.accepted,improved.accepted);
   ok(improved.coveredPixels >= legacy.coveredPixels,"coverage regressed");
   ok(improved.accepted<= (draft?3200:16000),"global cap exceeded");
   ok(improved.layerCoverage.length==2 && improved.layerCoverage[0]>.25f && improved.layerCoverage[1]>.25f,"swatch ignored");
   noWrongDots(improved,src,w,h);noSameSheetCollisions(improved);
   ok(improved.report().contains("per-color=L1="),"missing per-color coverage report");
  }
  options.draft=true;options.adaptiveCoverage=true;
  boolean[] mask=new boolean[w*h];Arrays.fill(mask,true);
  for(int y=60;y<90;y++)for(int x=25;x<60;x++)mask[y*w+x]=false;
  options.mask=mask;
  var masked=MultiColorStippleEngine.generate(src,w,h,options);
  for(var d:masked.dots)ok(mask[(int)d.y*w+(int)d.x],"masked dot center");
  Arrays.fill(src,0x00ffffff);
  ok(MultiColorStippleEngine.generate(src,w,h,options).accepted==0,"transparent spawned dots");
  options.targetCoverage=.9f;
  try{MultiColorStippleEngine.generate(src,w,h,options);throw new AssertionError("invalid target accepted");}
  catch(IllegalArgumentException expected){}
  System.out.println("PASS adaptive coverage, export monotonicity, no same-layer collision, masks, empty input");
 }
}

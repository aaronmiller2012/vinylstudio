import com.halftonestudio.*;
import java.util.*;
/** Physical geometry, independent point/radius controls, and 2x-budget regression. */
public final class DensitySize2513Test {
 static void ok(boolean p,String msg){if(!p)throw new AssertionError(msg);}
 static int[] sample(int w,int h){
   int[] p=new int[w*h];Arrays.fill(p,0xfffaf9f6);
   for(int y=12;y<h-12;y++)for(int x=12;x<w-12;x++){
     if(Math.pow((x-w/2.0)/(w*.44),2)+Math.pow((y-h/2.0)/(h*.45),2)>1)continue;
     if(x>w/2-7 && x<w/2+7 && y<h/3)continue;
     p[y*w+x]=x<w/2?0xff4b3638:0xffb38683;
   }
   return p;
 }
 static void safe(MultiColorStippleEngine.Result r,int[] src,int w,int h){
   int[] per=new int[r.options.layerCount];
   for(var d:r.dots){
     ok(Float.isFinite(d.r)&&d.r>0,"nonfinite dot radius");
     ok(d.r*2*r.options.widthMm/w+.001f>=r.options.minDotMm[d.layer],"subminimum cut diameter");
     ok(src[(int)d.y*w+(int)d.x]!=0xfffaf9f6,"dot in paper");
     per[d.layer]++;
   }
   for(int c:per)ok(c<=4400,"Cricut per-layer count exceeded");
   // Spot test with spatial bins to avoid a quadratic release-gate cost.
   Map<Long,List<MultiColorStippleEngine.Dot>> bins=new HashMap<>();
   for(var d:r.dots){
     int cx=(int)Math.floor(d.x/4),cy=(int)Math.floor(d.y/4);
     for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++){
       var list=bins.get((((long)(cx+dx))<<32)^((cy+dy)&0xffffffffL));
       if(list==null)continue;
       for(var b:list){if(b.layer!=d.layer)continue;
         float xx=b.x-d.x,yy=b.y-d.y;
         ok(xx*xx+yy*yy+0.01f>=(b.r+d.r)*(b.r+d.r),"same-layer overlap");
       }
     }
     bins.computeIfAbsent((((long)cx)<<32)^(cy&0xffffffffL),k->new ArrayList<>()).add(d);
   }
 }
 public static void main(String[] args){
   int w=210,h=280;int[] src=sample(w,h);
   var o=new MultiColorStippleEngine.Options();o.layerCount=2;
   o.colors=new int[]{0xff4b3638,0xffb38683};o.widthMm=110f;
   o.detail=100;o.draft=true;o.overlap=MultiColorStippleEngine.OVERPRINT;
   o.pointDensity=1f;var standard=MultiColorStippleEngine.generate(src,w,h,o);
   o.pointDensity=2f;var dense=MultiColorStippleEngine.generate(src,w,h,o);
   ok(dense.accepted>standard.accepted,"200% point density did not increase count");
   ok(dense.coveredPixels>=standard.coveredPixels,"more detail lost existing coverage");
   safe(dense,src,w,h);
   o.dotSize=1.2f;var larger=MultiColorStippleEngine.generate(src,w,h,o);
   ok(larger.accepted>0,"larger dots all rejected");
   safe(larger,src,w,h);
   o.dotSize=.6f;var smaller=MultiColorStippleEngine.generate(src,w,h,o);
   safe(smaller,src,w,h);
   o.dotSize=1f;o.pointDensity=1f;o.optimizePacking=false;
   var original=MultiColorStippleEngine.generate(src,w,h,o);
   ok(original.accepted<=3200,"legacy preview budget violated");
   Arrays.fill(src,0x00ffffff);
   ok(MultiColorStippleEngine.generate(src,w,h,o).accepted==0,"blank transparent generated cuts");
   o.pointDensity=Float.NaN;
   try{MultiColorStippleEngine.generate(src,w,h,o);throw new AssertionError("invalid density accepted");}
   catch(IllegalArgumentException expected){}
   System.out.printf(Locale.US,"PASS density/size/safe geometry standard=%d dense=%d enlarged=%d smaller=%d%n",
     standard.accepted,dense.accepted,larger.accepted,smaller.accepted);
 }
}

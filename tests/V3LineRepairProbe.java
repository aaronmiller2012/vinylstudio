package com.halftonestudio;
import java.util.*;
public class V3LineRepairProbe {
 public static void main(String[] z) {
 Random rand=new Random(112358);
 int found=0, remaining=0, unchanged=0;
 for(int i=0;i<1500;i++){
  ArrayList<float[]> run=new ArrayList<>();
  double phase=rand.nextDouble()*Math.PI*2, freq=rand.nextDouble()*.9+.2, amp=rand.nextDouble()*4+.3;
  float half=(float)(rand.nextDouble()*1.6+.3);
  for(int j=0;j<26;j++){
   double x=j*.85,y=amp*Math.sin(j*freq+phase)+.15*j;
   run.add(new float[]{(float)x,(float)y,half});
  }
  float[] a=LineRibbonRepair.outline(run);
  LineRibbonRepair.Crossing bad=LineRibbonRepair.findCrossing(a);
  float[] b=LineRibbonRepair.safeOutline(run,.254f);
  LineRibbonRepair.Crossing after=LineRibbonRepair.findCrossing(b);
  if(bad!=null&&!bad.incomplete){
   if(after==null){found++;if(found<=3)System.out.printf(Locale.ROOT,"FIX %d amp %.2f freq %.2f half %.2f original %d repaired %d%n",i,amp,freq,half,Arrays.hashCode(a),Arrays.hashCode(b));}
   else remaining++;
  }else{if(Arrays.equals(a,b))unchanged++;else throw new AssertionError("Safe changed");}
 }
 System.out.printf("SUMMARY fixed=%d stillUnsafe=%d originallySafeUnchanged=%d%n",found,remaining,unchanged);
 if(found<10)throw new AssertionError("Insufficient recoverable fixtures");
 }
}

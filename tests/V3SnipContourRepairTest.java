package com.halftonestudio;
import java.util.ArrayList;
import java.util.List;
public final class V3SnipContourRepairTest {
  private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
  public static void main(String[] args){
    ArrayList<float[]> smooth=new ArrayList<>();
    smooth.add(new float[]{0,0,60,0,60,60,0,60});
    ArrayList<float[]> unchanged=SolidSnipContourRepair.repair(smooth,.254f);
    check(unchanged.get(0)==smooth.get(0),"safe baseline changed");
    // Stepped narrow slit: simplification must not silently certify a real gap.
    ArrayList<float[]> two=new ArrayList<>();
    two.add(new float[]{0,0,10,0,10,10,0,10});
    two.add(new float[]{10.1f,0,20,0,20,10,10.1f,10});
    check(SolidSnipContourRepair.repair(two,.254f).get(0)==two.get(0),"unsafe gap was rewritten");
    float[] many=new float[4*150];
    int j=0;for(int x=0;x<75;x++){many[j++]=x*.3f;many[j++]=x%2==0?0:.1f;}
    for(int x=74;x>=0;x--){many[j++]=x*.3f;many[j++]=10+(x%2==0?0:.1f);}
    ArrayList<float[]> jagged=new ArrayList<>();jagged.add(many);
    List<float[]> output=SolidSnipContourRepair.repair(jagged,.254f);
    check(output.size()==1,"lost ring");
    check(output.get(0).length<=many.length,"increased vertices");
    System.out.println("PASS Snip contour repair: safe unchanged, unsafe gap unchanged, bounded stair-step processing");
  }
}

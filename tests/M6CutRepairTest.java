package com.halftonestudio;

import java.util.*;

/** Exercises the actual production conditioner and fallback, not only the grid planner. */
public final class M6CutRepairTest {
    static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
    static float[] rect(float x,float y,float w,float h){return new float[]{x,y,x+w,y,x+w,y+h,x,y+h};}
    static float[] translated(float[] a,float x,float y){float[] b=a.clone();for(int i=0;i<b.length;i+=2){b[i]+=x;b[i+1]+=y;}return b;}
    static StyleKit.Result fixture(List<float[]> loops,float w,float h){StyleKit.Result r=new StyleKit.Result(w,h);StyleKit.Layer l=new StyleKit.Layer("test",0xff000000);l.loops.addAll(loops);r.layers.add(l);return r;}
    static void partition(){
        float[] outer=rect(0,0,20,20),hole=rect(5,5,3,3),near=rect(20.5f,0,2,3),far=rect(200,200,3,3);
        List<List<float[]>> groups=CutRepairPartitions.group(Arrays.asList(outer,far,hole,near),1f);
        check(groups.size()==2&&groups.get(0).equals(Arrays.asList(outer,hole,near))&&groups.get(1).equals(Collections.singletonList(far)),"nested or near contours split");
        check(CutRepairPartitions.group(Arrays.asList(rect(0,0,1,1),rect(1.8f,0,1,1),rect(3.6f,0,1,1)),1f).size()==1,"transitive group split");
    }
    static void preserveOnBudget(){
        float[] distant=rect(250,250,1.1f,12); // legal isolated stroke
        float[] block=rect(0,0,5,5),close=rect(5.2f,0,5,5);
        List<float[]> loops=new ArrayList<>(Arrays.asList(block,close,distant));
        try{CutGeometryConditioner.condition(loops,279.4f,279.4f,.8f,.5f);throw new AssertionError("oversize grid coarsened");}
        catch(CutGeometryConditioner.BudgetExceededException expected){}
        StyleKit.Result r=fixture(loops,279.4f,279.4f);
        check(!StyleCutGeometryPreflight.inspect(r,.8f,.5f,20).isEmpty(),"fixture does not require repair");
        VinylStyleSuite.conditionForCutting(r,r.layers.get(0),.8f,.5f,false);
        check(r.layers.get(0).loops.stream().anyMatch(a->a==distant),"clean distant stroke was retraced or erased");
        check(StyleCutGeometryPreflight.inspect(r,.8f,.5f,20).isEmpty(),"independent defect was not repaired");
        // No safe partition exists for overlapping, artboard-spanning contours: retain exact paths.
        float[] a=rect(1,1,270,1),b=rect(1,1.1f,1,270);
        // This pair crosses, so the existing invalid-geometry path must preserve it too.
        StyleKit.Result invalid=fixture(Arrays.asList(a,b),279.4f,279.4f);
        VinylStyleSuite.conditionForCutting(invalid,invalid.layers.get(0),.8f,.5f,false);
        check(invalid.layers.get(0).loops.get(0)==a&&invalid.layers.get(0).loops.get(1)==b,"invalid source mutated");
    }
    static void radius(){
        for(float min:new float[]{.254f,.4f,.8f,1.2f})for(float gap:new float[]{.25f,.5f,1f}){
            CutRepairGrid g=CutRepairGrid.plan(10,10,min,gap,2_500_000);
            int r=CutRepairGrid.openingRadius(min,g.pitch);
            check((2*r+1)*g.pitch<=min+1e-6,"opening footprint larger than minimum");
            check((2*CutRepairGrid.openingRadius(gap,g.pitch)+1)*g.pitch<=gap+1e-6,"opening footprint larger than gap");
        }
    }
    static void shortReport(){
        StyleKit.Result before=fixture(Arrays.asList(rect(0,0,20,20),rect(5,5,2,2),rect(25,25,2,2)),30,30);
        StyleKit.Result after=fixture(Collections.singletonList(rect(0,0,20,20)),30,30);
        CutRepairSummary report=CutRepairSummary.of(before,after);String status=report.shortSentence(.8f,.5f);
        check(status.length()<=100&&status.contains("filled 1 hole")&&status.contains("removed 1 piece")&&StatusLinePolicy.classify(status)==StatusLinePolicy.Kind.PROBLEM,"compact repair report lost information");
    }
    public static void main(String[] args){partition();radius();preserveOnBudget();shortReport();System.out.println("PASS M6 repair partition, physical radius, exact legal stroke retention and actual local repair");}
}

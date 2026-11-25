package com.halftonestudio;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * M5 property tests for the run-length-limited 1-D sigma-delta that turns sub-minimum
 * engraving tone into minimum-width dashes (BanknoteEngravingEngine.DashModulator). Pure traversal
 * geometry along a straight synthetic lane, so every length is exact:
 *  - long-run cut fraction equals the requested duty (tone is conserved);
 *  - every stand-alone dash is at least the minimum dash length and every paper gap
 *    between pieces of the lane is at least the minimum gap;
 *  - FIXED (continuous) ribbon samples are always emitted, never split, and a short gap in
 *    front of a ribbon is filled, never left below the weed gap;
 *  - zero, negative and NaN duty produce no cuts; HARD samples are never crossed;
 *  - deterministic output and staggered phases between lanes.
 */
public final class M5DashModulatorTest {
    static final float STEP=.45f,MIN=.8f,GAP=.65f;
    static int failures=0;
    static void check(boolean ok,String what){if(!ok){failures++;System.out.println("FAIL "+what);}}

    /** Events: 'F' fixed, 'S' soft (duty), 'H' hard. Returns emitted runs as index lists. */
    static final class Lane {
        final List<int[]> runs=new ArrayList<>();
        final BanknoteEngravingEngine.DashPlan plan;
        final BanknoteEngravingEngine.DashModulator dm;
        final List<Object> pieces=new ArrayList<>();
        Lane(float gamma,int laneNumber){
            plan=new BanknoteEngravingEngine.DashPlan(MIN,2*MIN,GAP,gamma,new BanknoteEngravingEngine.DashPlan.Stats());
            dm=new BanknoteEngravingEngine.DashModulator(plan,STEP,laneNumber,pieces,run->{
                int[] idx=new int[run.size()];
                for(int i=0;i<idx.length;i++)idx[i]=Math.round(run.get(i)[0]/STEP);
                runs.add(idx);pieces.add(idx);
            });
        }
        void fixed(int i){dm.fixed(new float[]{i*STEP,0f,MIN});}
        void soft(int i,float d){dm.soft(i*STEP,0f,d,MIN*.5f);}
    }
    static boolean contiguous(int[] r){for(int i=1;i<r.length;i++)if(r[i]!=r[i-1]+1)return false;return true;}

    static void constraints(Lane lane,boolean[] fixed,boolean[] hard,String what){
        int minOnSteps=(int)Math.ceil(2*MIN/STEP-1e-4f),gapSteps=(int)Math.ceil(GAP/STEP-1e-4f);
        int prevEnd=Integer.MIN_VALUE;
        for(int[] r:lane.runs){
            check(contiguous(r),what+": run not contiguous");
            boolean anyFixed=false;
            for(int i:r){if(fixed[i])anyFixed=true;check(!hard[i],what+": run crosses HARD sample "+i);}
            float length=(r[r.length-1]-r[0])*STEP;
            if(!anyFixed)check(length>=2*MIN-1e-3f,what+": dash "+length+" mm shorter than "+2*MIN);
            if(prevEnd!=Integer.MIN_VALUE){
                boolean hardBetween=false;
                for(int k=prevEnd+1;k<r[0];k++)if(hard[k])hardBetween=true;
                if(!hardBetween)check(r[0]-prevEnd>=gapSteps,what+": gap "+(r[0]-prevEnd)*STEP+" mm below "+GAP);
            }
            check(r[0]>prevEnd,what+": runs out of order/overlapping");
            prevEnd=r[r.length-1];
        }
        // Every FIXED sample is emitted exactly once.
        int[] seen=new int[fixed.length];
        for(int[] r:lane.runs)for(int i:r)seen[i]++;
        for(int i=0;i<fixed.length;i++){
            check(seen[i]<=1,what+": sample emitted twice "+i);
            // Like the legacy flush, an isolated one-sample ribbon is not a cuttable piece.
            boolean isolated=(i==0||!fixed[i-1])&&(i+1>=fixed.length||!fixed[i+1]);
            if(fixed[i]&&!isolated)check(seen[i]==1,what+": FIXED sample dropped "+i);
        }
        check(minOnSteps>0,"sanity");
    }

    public static void main(String[] a){
        // 1. Tone conservation for constant duty over a long lane.
        for(float d:new float[]{.05f,.1f,.2f,.35f,.5f,.7f,.9f}){
            int n=4000;Lane lane=new Lane(1f,3);
            for(int i=0;i<n;i++)lane.soft(i,d);lane.dm.end();
            double cut=0;for(int[] r:lane.runs)cut+=r.length-1; // k samples cover k-1 steps
            double fraction=cut/(n-1);
            check(Math.abs(fraction-d)<=.02+ (2*MIN/STEP+2)/n,"duty "+d+" gave cut fraction "+fraction);
            constraints(lane,new boolean[n],new boolean[n],"constant "+d);
            System.out.printf("duty %.2f -> cut fraction %.3f, %d dashes%n",d,fraction,lane.runs.size());
        }
        // 2. Zero / negative / NaN duty: nothing is cut.
        for(float d:new float[]{0f,-1f,Float.NaN,Float.NEGATIVE_INFINITY}){
            Lane lane=new Lane(1f,1);for(int i=0;i<500;i++)lane.soft(i,d);lane.dm.end();
            check(lane.runs.isEmpty(),"duty "+d+" produced cuts");
        }
        // Duty above 1 or +inf is clamped (a fully covered lane), never longer than the lane.
        {Lane lane=new Lane(1f,1);for(int i=0;i<200;i++)lane.soft(i,Float.POSITIVE_INFINITY);lane.dm.end();
            int total=0;for(int[] r:lane.runs)total+=r.length;check(total<=200&&total>=190,"inf duty covered "+total);}
        // 4. Gamma lowers the duty: d^2.
        {int n=4000;Lane lane=new Lane(2f,0);for(int i=0;i<n;i++)lane.soft(i,.5f);lane.dm.end();
            double cut=0;for(int[] r:lane.runs)cut+=r.length-1;
            check(Math.abs(cut/(n-1)-.25)<.02,"gamma 2 fraction "+cut/(n-1));}
        // 5. Randomised mixed lanes (fuzz): FIXED ribbons, SOFT tone, HARD exclusions, NaN.
        Random rnd=new Random(561);
        for(int trial=0;trial<400;trial++){
            int n=50+rnd.nextInt(600);
            boolean[] fixed=new boolean[n],hard=new boolean[n];
            Lane lane=new Lane(1f+rnd.nextFloat()*3f,trial);
            int i=0;
            while(i<n){
                int len=1+rnd.nextInt(30),kind=rnd.nextInt(10);
                float d=rnd.nextFloat();
                for(int k=0;k<len&&i<n;k++,i++){
                    if(kind<3){fixed[i]=true;lane.fixed(i);}
                    else if(kind<4){hard[i]=true;lane.dm.hard();}
                    else if(kind<5)lane.soft(i,Float.NaN);
                    else lane.soft(i,Math.min(1f,Math.max(0f,d+(rnd.nextFloat()-.5f)*.2f)));
                }
            }
            lane.dm.end();
            constraints(lane,fixed,hard,"fuzz "+trial);
        }
        // 6. A gap shorter than the weed gap in front of a ribbon is filled (merged).
        {Lane lane=new Lane(1f,0);boolean[] fixed=new boolean[40];
            for(int i=0;i<10;i++){fixed[i]=true;lane.fixed(i);}
            lane.soft(10,0f); // one paper sample: 0.45 mm < 0.65 mm gap
            for(int i=11;i<20;i++){fixed[i]=true;lane.fixed(i);}
            lane.dm.end();
            check(lane.runs.size()==1&&lane.runs.get(0).length==20,"short gap before ribbon was not filled: "+lane.runs.size());}
        // 7. Hysteresis: a ribbon continues as a min-width tail into near-threshold tone,
        //    and a FIXED run is never split.
        {Lane lane=new Lane(1f,0);
            for(int i=0;i<10;i++)lane.fixed(i);
            for(int i=10;i<30;i++)lane.soft(i,.95f);
            lane.dm.end();
            int covered=0;for(int[] r:lane.runs)covered+=r.length;
            check(lane.runs.get(0).length>=15,"no hysteresis tail at duty .95: "+lane.runs.get(0).length);
            check(covered>=26,"near-threshold tone under-covered: "+covered+"/30");}
        // 8. A dash interrupted by a HARD sample before reaching minimum length is dropped.
        {Lane lane=new Lane(1f,0);
            for(int i=0;i<3;i++)lane.soft(i,1f);
            lane.dm.hard();lane.dm.end();
            check(lane.runs.isEmpty(),"short dash before HARD was emitted");}
        // 9. Determinism and lane phase staggering.
        {Lane a1=new Lane(1f,7),a2=new Lane(1f,7),b=new Lane(1f,8);
            for(int i=0;i<300;i++){a1.soft(i,.2f);a2.soft(i,.2f);b.soft(i,.2f);}
            a1.dm.end();a2.dm.end();b.dm.end();
            check(a1.runs.size()==a2.runs.size()&&a1.runs.get(0)[0]==a2.runs.get(0)[0],"non-deterministic");
            check(a1.runs.get(0)[0]!=b.runs.get(0)[0],"adjacent lanes start dashes in phase");}
        // 10. Piece cap: no new dash once the sheet holds PIECE_CAP pieces; overflow flagged.
        {Lane lane=new Lane(1f,0);
            for(int k=0;k<BanknoteEngravingEngine.DashPlan.PIECE_CAP;k++)lane.pieces.add(k);
            int before=lane.runs.size();
            for(int i=0;i<400;i++)lane.soft(i,.5f);lane.dm.end();
            check(lane.runs.size()==before&&lane.plan.stats.overflow,"cap not enforced");}
        if(failures>0)throw new AssertionError(failures+" dash modulator property failures");
        System.out.println("PASS M5DashModulatorTest");
    }
}

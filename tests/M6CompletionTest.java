package com.halftonestudio;
import java.util.*;
/** Local repair planning and memory scheduling; optional exact backend fixture. */
public final class M6CompletionTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static float[] rect(float x,float y,float w,float h){return new float[]{x,y,x+w,y,x+w,y+h,x,y+h};}
    static void planning(){
        ArrayList<StyleCutGeometryPreflight.Warning> warnings=new ArrayList<>();
        for(int i=0;i<12;i++)warnings.add(new StyleCutGeometryPreflight.Warning(0,0,0,"Local vinyl neck",28.1f,33.4f));
        for(int i=0;i<300;i++)warnings.add(new StyleCutGeometryPreflight.Warning(0,0,0,"Local vinyl neck",(i%20)*9.9f,(i/20)*9.9f));
        List<float[]> a=ConfettiVectorRepair.windows(warnings,279.4f,210f,.254f,.5f),b=ConfettiVectorRepair.windows(warnings,279.4f,210f,.254f,.5f);
        check(!a.isEmpty()&&a.size()<=72&&a.size()==b.size(),"window limit");double area=0;
        for(int i=0;i<a.size();i++){float[] r=a.get(i);check(Arrays.equals(r,b.get(i)),"nondeterministic tiles");check(r[0]>=0&&r[1]>=0&&r[2]<=279.4f&&r[3]<=210f,"outside artwork");area+=(r[2]-r[0])*(r[3]-r[1]);}
        check(area<=279.4*210*.04,"physical area budget");float[] top=a.get(0);check(top[0]<=28.1f&&top[2]>=28.1f&&top[1]<=33.4f&&top[3]>=33.4f,"did not prioritize repeated midpoint");
        warnings.add(new StyleCutGeometryPreflight.Warning(0,0,0,"cut safety is not verified"));
        check(ConfettiVectorRepair.windows(warnings,279.4f,210f,.254f,.5f).isEmpty(),"incomplete scan was used");
        check(!ParallelGeometry.allowConcurrentSheets(20000,279.4f,768L<<20),"large artwork concurrent on small heap");
        check(!ParallelGeometry.allowConcurrentSheets(200000,152.4f,1024L<<20),"complex artwork concurrent");
        check(ParallelGeometry.allowConcurrentSheets(12000,101.6f,1024L<<20),"small workload lost concurrency");
        System.out.println("PASS M6 warning-centred window limits, midpoint priority, deterministic planning and memory scheduling");
    }
    static int[] legacyStrand(float[][] targets,float[][] palette){
        int n=targets.length,m=palette.length;int[] out=new int[n];float[] prev=new float[m],next=new float[m];byte[] back=new byte[n*m];
        for(int k=0;k<n;k++){for(int j=0;j<m;j++){float best=Float.POSITIVE_INFINITY;int from=0;for(int i=0;i<m;i++){
            float v=prev[i]+(i==j?0:115f+.035f*StyleKit.labDist2(palette[i],palette[j]));if(v<best){best=v;from=i;}}
            next[j]=best+(targets[k]==null?0:RibbonColorMath.cost(targets[k],palette[j]));back[k*m+j]=(byte)from;}
            float min=Float.POSITIVE_INFINITY;for(float x:next)min=Math.min(min,x);for(int j=0;j<m;j++)next[j]-=min;float[] swap=prev;prev=next;next=swap;}
        int best=0;for(int j=1;j<m;j++)if(prev[j]<prev[best])best=j;for(int k=n-1;k>=0;k--){out[k]=best;best=back[k*m+best]&255;}return out;
    }
    static void strand(){
        Random rnd=new Random(565);for(int j=0;j<150;j++){int n=1+rnd.nextInt(220),m=2+rnd.nextInt(7);
            float[][] colors=new float[m][3],targets=new float[n][];
            for(int i=0;i<m;i++)colors[i]=RibbonColorMath.lab(rnd.nextFloat(),rnd.nextFloat(),rnd.nextFloat());
            for(int i=0;i<n;i++)if(rnd.nextInt(5)>0)targets[i]=RibbonColorMath.lab(rnd.nextFloat(),rnd.nextFloat(),rnd.nextFloat());
            check(Arrays.equals(legacyStrand(targets,colors),RibbonColorMath.strand(targets,colors)),"woven continuity changed");}
        System.out.println("PASS exact ribbon continuity parity, 150 deterministic strand fixtures");
    }
    static boolean inside(List<float[]> a,float x,float y){boolean in=false;for(float[] p:a){for(int i=0,j=p.length-2;i<p.length;j=i,i+=2){if((p[i+1]>y)!=(p[j+1]>y)&&x<(p[j]-p[i])*(y-p[i+1])/(p[j+1]-p[i+1])+p[i])in=!in;}}return in;}
    static void local(VectorBooleanEngine.Backend backend){
        List<float[]> raw=Arrays.asList(rect(10,10,10,10),rect(20.2f,10,10,10),rect(200,100,2,30),rect(35,35,20,20),rect(40,40,10,10));
        Object base=backend.clipPath(raw.toArray(new float[0][]));Object snip=backend.clipPath(new float[][]{rect(0,0,279.4f,210),rect(40,40,10,10)});
        int before=ConfettiVectorRepair.findings(raw,279.4f,210f,.254f,.5f);
        check(before>0,"fixture missing gap");
        List<float[]> candidate=backend.localizedCondition(base,snip,raw,Collections.singletonList(new float[]{17,8,23,22}),.28f,.157f,.008f);
        check(candidate!=null,"backend did not return local candidate");
        check(ConfettiVectorRepair.accept(raw,candidate,279.4f,210f,.254f,.5f,before),"candidate not accepted");
        for(float y=98.15f;y<132;y+=.31f)for(float x=198.13f;x<204;x+=.29f)check(inside(raw,x,y)==inside(candidate,x,y),"distant legal stroke changed");
        check(!inside(candidate,45,45),"protected snip hole filled");
        System.out.println("PASS localized backend gap repair, exact distant sampled coverage, protected Snip hole");
    }
    public static void main(String[] args)throws Exception{
        planning();strand();if(args.length==0)return;
        if(args[0].equals("--geos")){try(GeosVectorReferenceBackend ref=new GeosVectorReferenceBackend()){local(ref);}}
        else local((VectorBooleanEngine.Backend)Class.forName("com.halftonestudio.JtsVectorBooleanBackend").getDeclaredConstructor().newInstance());
    }
}

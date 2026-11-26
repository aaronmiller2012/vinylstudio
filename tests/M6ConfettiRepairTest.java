package com.halftonestudio;
import java.util.*;
/** Raw versus candidate, independent reference or production backend. */
public final class M6ConfettiRepairTest {
    static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);}
    static float[] rect(float x,float y,float w,float h){return new float[]{x,y,x+w,y,x+w,y+h,x,y+h};}
    static void policy(){
        List<float[]> raw=Arrays.asList(rect(5,5,10,10),rect(15.2f,5,10,10));
        List<float[]> merged=Collections.singletonList(rect(5,5,20.2f,10));
        int n=ConfettiVectorRepair.findings(raw,40,40,.8f,.5f);
        check(n>0,"fixture has no findings");
        check(ConfettiVectorRepair.accept(raw,merged,40,40,.8f,.5f,n),"legal gap fill rejected");
        check(!ConfettiVectorRepair.accept(raw,Collections.singletonList(rect(5,18,20,10)),40,40,.8f,.5f,n),"equal area displacement accepted");
        check(!ConfettiVectorRepair.accept(raw,Collections.emptyList(),40,40,.8f,.5f,n),"empty candidate accepted");
        check(!ConfettiVectorRepair.accept(raw,raw,40,40,.8f,.5f,n),"unchanged findings accepted");
    }
    static void run(){
        int w=160,h=128;int[] p=V3AllStyleExportMatrixTest.portrait(w,h);boolean[] allowed=new boolean[p.length];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){double dx=(x-w*.5)/(w*.48),dy=(y-h*.5)/(h*.47);allowed[y*w+x]=dx*dx+dy*dy<1&&!(x>72&&x<88&&y>55&&y<69);}
        int cases=0,before=0,after=0,changed=0;
        for(float width:new float[]{101.6f,279.4f})for(int mode=0;mode<6;mode++){
            PatternPortraitTrioEngine.Options o=new PatternPortraitTrioEngine.Options();o.widthMm=width;o.layers=6;o.detail=82;o.minCutMm=width<150?.8f:.254f;o.gapMm=.5f;o.allowed=allowed;o.confettiMode=mode;o.palette=new int[]{0xffb08769,0xff241d19,0xff70523b,0xffdbb38d,0xfff5e5cd,0xff9f694d};
            StyleKit.Result a=PatternPortraitTrioEngine.generate(p,w,h,o,false),b=PatternPortraitTrioEngine.generate(p,w,h,o,true);int n0=0,n1=0,nc=0;
            for(int i=0;i<a.layers.size();i++){
                StyleKit.Layer x=a.layers.get(i),y=b.layers.get(i);check(x.color==y.color&&!y.loops.isEmpty(),"slot changed");
                boolean same=x.loops.size()==y.loops.size();if(same)for(int j=0;j<x.loops.size();j++)if(!Arrays.equals(x.loops.get(j),y.loops.get(j))){same=false;break;}
                if(i==0)check(same,"foundation changed");
                int f0=ConfettiVectorRepair.findings(x.loops,a.widthMm,a.heightMm,o.minCutMm,o.gapMm),f1=ConfettiVectorRepair.findings(y.loops,b.widthMm,b.heightMm,o.minCutMm,o.gapMm);
                if(!same){check(ConfettiVectorRepair.accept(x.loops,y.loops,a.widthMm,a.heightMm,o.minCutMm,o.gapMm,f0),"unsafe change");nc++;}
                if(f0!=0)n0++;if(f1!=0)n1++;
            }
            check(n1<=n0,"more sheets in review");cases++;before+=n0;after+=n1;changed+=nc;
            System.out.println("width="+width+" mode="+mode+" review "+n0+" -> "+n1+" changed="+nc);
        }
        System.out.println("PASS "+cases+" cases, review sheets "+before+" -> "+after+", changed overlays="+changed+" (not physical qualification)");
        check(changed>0,"no repair accepted on any fixture");
    }
    public static void main(String[] args)throws Exception{
        policy();if(args.length>0&&args[0].equals("--policy")){System.out.println("PASS acceptance policy");return;}
        if(args.length>0&&args[0].equals("--geos")){ParallelGeometry.forceSequential=true;try(GeosVectorReferenceBackend ref=new GeosVectorReferenceBackend()){VectorBooleanEngine.install(ref);run();}}
        else{VectorBooleanEngine.install((VectorBooleanEngine.Backend)Class.forName("com.halftonestudio.JtsVectorBooleanBackend").getDeclaredConstructor().newInstance());run();}
    }
}

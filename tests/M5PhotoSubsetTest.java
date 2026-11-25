package com.halftonestudio;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

/** M5: active-subset selection of the photo palette and confetti base policy, on real photos
 *  and the real production palette functions. Prints the measured table. */
public final class M5PhotoSubsetTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static double err(int[] set,int n,int[] px){
        double e=0;int c=0;
        for(int i=0;i<px.length;i+=Math.max(1,px.length/2000)){
            double[] x=PerceptualVinylColor.lab(px[i]);double m=1e9;
            for(int j=0;j<n;j++){double[] s=PerceptualVinylColor.lab(set[j]);
                m=Math.min(m,(x[0]-s[0])*(x[0]-s[0])+(x[1]-s[1])*(x[1]-s[1])+(x[2]-s[2])*(x[2]-s[2]));}
            e+=m;c++;}
        return Math.sqrt(e/c); // RMS OKLab dE: the objective PhotoSwatchSubset minimises
    }
    public static double p95L(int[] px){
        double[] l=new double[px.length];for(int i=0;i<l.length;i++)l[i]=PerceptualVinylColor.lab(px[i])[0];
        Arrays.sort(l);return l[(int)(l.length*.95)];
    }
    public static double maxL(int[] set,int n){double m=0;for(int i=0;i<n;i++)m=Math.max(m,PerceptualVinylColor.lab(set[i])[0]);return m;}
    public static void main(String[] a)throws Exception{
        File[] files=new File(a.length>0?a[0]:"tests/m4/photos").listFiles((d,n)->n.endsWith(".png")||n.endsWith(".jpg"));
        Arrays.sort(files);
        double before=0,after=0,gapBefore=0,gapAfter=0;int cases=0;
        System.out.println("photo\tcount\terr_before\terr_after\tp95L\tlightestActive_before\tlightestActive_after");
        for(File f:files){
            M4ArtBench.Photo p=M4ArtBench.load(f,200);
            boolean[] v=new boolean[p.argb.length];Arrays.fill(v,true);
            int[] eight=PhotoPaletteOptimizer.optimize(p.argb,v,8).colors;
            for(int n:new int[]{4,5,6,7}){
                int[] cur=FoundationColorPolicy.anchorPhotoPalette(eight,n,p.argb,false); // pre-M5 flow
                int[] sub=PhotoSwatchSubset.select(eight,n,p.argb);
                int[] multi=sub.clone();Arrays.sort(multi.clone());
                // every swatch survives exactly once
                int[] x=sub.clone(),y=eight.clone();Arrays.sort(x);Arrays.sort(y);
                check(Arrays.equals(x,y),"swatches lost");
                double eb=err(cur,n,p.argb),ea=err(sub,n,p.argb),hi=p95L(p.argb);
                System.out.println(String.format(Locale.US,"%s\t%d\t%.4f\t%.4f\t%.3f\t%.3f\t%.3f",p.name,n,eb,ea,hi,maxL(cur,n),maxL(sub,n)));
                check(ea<=eb+1e-9,"subset must not be worse than first-N for "+p.name+" n="+n);
                before+=eb;after+=ea;cases++;
                gapBefore+=Math.max(0,hi-maxL(cur,n));gapAfter+=Math.max(0,hi-maxL(sub,n));
                // base policy returns an active member, deterministic, never mutates input
                int[] keep=sub.clone();
                for(int mode=0;mode<3;mode++){
                    int[] an=ConfettiBasePolicy.anchor(sub,n,p.argb,mode);
                    int[] q=Arrays.copyOf(an,n),w=Arrays.copyOf(sub,n);Arrays.sort(q);Arrays.sort(w);
                    check(Arrays.equals(q,w),"anchor changed active colours");
                    for(int i=n;i<8;i++)check(an[i]==sub[i],"parked moved");
                    check(Arrays.equals(an,ConfettiBasePolicy.anchor(sub,n,p.argb,mode)),"nondeterministic");
                }
                check(Arrays.equals(keep,sub),"mutated");
            }
        }
        System.out.println(String.format(Locale.US,"RMS OKLab dE first-N=%.4f subset=%.4f  highlight gap (p95 L - lightest active) first-N=%.3f subset=%.3f",
            before/cases,after/cases,gapBefore/cases,gapAfter/cases));
        check(after<before,"no aggregate improvement");
        System.out.println("PASS M5 photo subset");
    }
}

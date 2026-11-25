package com.halftonestudio;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

/** M5: CIELAB (production) vs OKLab k-means for source-colour stencil quantisation.
 *  Reports mean OKLab dE, mean |dL|, 95th percentile dE, and neutral hue leakage on real photos. */
public final class M5QuantizerBench {
    public static void main(String[] a)throws Exception{
        File[] files=new File(a[0]).listFiles((d,n)->n.endsWith(".png")||n.endsWith(".jpg"));Arrays.sort(files);
        System.out.println("photo\tk\tspace\tmeanDE\tp95DE\tmeanDL\tneutralChroma\tskinDE");
        double[][] tot=new double[2][4];int cases=0;
        for(File f:files){
            M4ArtBench.Photo p=M4ArtBench.load(f,300);
            boolean[] v=new boolean[p.argb.length];Arrays.fill(v,true);
            for(int k:new int[]{3,4,6,8}){
                cases++;
                for(int s=0;s<2;s++){
                    SourceColorStencilQuantizer.Result r=SourceColorStencilQuantizer.quantize(p.argb,p.w,p.h,k,v,s==1);
                    double[][] cl=new double[k][];for(int i=0;i<k;i++)cl[i]=PerceptualVinylColor.lab(r.colors[i]);
                    double[] de=new double[p.argb.length];double sde=0,sdl=0,nc=0,skin=0;int nn=0,ns=0;
                    for(int i=0;i<de.length;i++){
                        double[] x=PerceptualVinylColor.lab(p.argb[i]),c=cl[r.owner[i]];
                        double dl=x[0]-c[0],da=x[1]-c[1],db=x[2]-c[2];
                        de[i]=Math.sqrt(dl*dl+da*da+db*db);sde+=de[i];sdl+=Math.abs(dl);
                        double ch=Math.hypot(x[1],x[2]);
                        if(ch<.02){nc+=Math.hypot(c[1],c[2]);nn++;}
                        double hue=Math.toDegrees(Math.atan2(x[2],x[1]));
                        if(x[0]>.6&&ch>.03&&ch<.14&&hue>20&&hue<75){skin+=de[i];ns++;}
                    }
                    Arrays.sort(de);
                    double mean=sde/de.length,p95=de[(int)(de.length*.95)],mdl=sdl/de.length;
                    System.out.println(String.format(Locale.US,"%s\t%d\t%s\t%.4f\t%.4f\t%.4f\t%.4f\t%.4f",p.name,k,s==0?"CIELAB":"OKLab",mean,p95,mdl,nn==0?0:nc/nn,ns==0?0:skin/ns));
                    tot[s][0]+=mean;tot[s][1]+=p95;tot[s][2]+=mdl;tot[s][3]+=ns==0?0:skin/ns;
                }
            }
        }
        for(int s=0;s<2;s++)System.out.println(String.format(Locale.US,"MEAN\t%s\tmeanDE=%.4f\tp95=%.4f\tmeanDL=%.4f\tskin=%.4f",s==0?"CIELAB":"OKLab",tot[s][0]/cases,tot[s][1]/cases,tot[s][2]/cases,tot[s][3]/cases));
    }
}

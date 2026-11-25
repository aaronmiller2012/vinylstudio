package com.halftonestudio;

import java.util.Arrays;

/**
 * M5: choose which {@code count} of the eight photo-derived swatches are ACTIVE.
 *
 * The photo palette is stored dark to light. StudioActivity/FoundationColorPolicy used to
 * treat the first {@code count} entries as active, so a six-sheet project silently parked
 * the two LIGHTEST swatches, which are the skin highlights and bright background of a
 * portrait. This selects the active subset by exhaustive search (C(8,k)<=70) on OKLab squared
 * error against the photo, so range and
 * accents survive. No colour is synthesised; every input swatch is returned exactly once,
 * active ones first in their original (dark to light) relative order, parked ones after.
 * Explicit photo-palette applies only; never call on render, resize or surface change.
 */
public final class PhotoSwatchSubset {
    private PhotoSwatchSubset() {}
    private static final int MAX_SAMPLES=2000;

    public static int[] select(int[] eight,int count,int[] photoArgb){
        if(eight==null||count<1||count>eight.length)
            throw new IllegalArgumentException("Invalid swatch subset");
        int n=eight.length;
        if(count==n)return eight.clone();
        double[][] s=sample(photoArgb);
        boolean[] on=new boolean[n];
        if(s.length==0){ // no photo: keep the full tonal range, evenly spaced by lightness
            Integer[] o=new Integer[n];for(int i=0;i<n;i++)o[i]=i;
            Arrays.sort(o,(x,y)->Double.compare(PerceptualVinylColor.lab(eight[x])[0],PerceptualVinylColor.lab(eight[y])[0]));
            for(int i=0;i<count;i++)on[o[count==1?0:Math.round(i*(n-1f)/(count-1))]]=true;
        }else{
            double[][] lab=new double[n][];
            for(int i=0;i<n;i++)lab[i]=PerceptualVinylColor.lab(eight[i]);
            // Exhaustive: C(8,k) <= 70 subsets, so the optimum is exact and deterministic
            // (ties go to the lowest mask). Greedy forward selection was 0.0009 dE worse on one photo.
            double[][] d=new double[n][s.length];
            for(int i=0;i<n;i++)for(int j=0;j<s.length;j++)d[i][j]=d2(s[j],lab[i]);
            int bestMask=0;double bestE=Double.POSITIVE_INFINITY;
            for(int mask=1;mask<(1<<n);mask++){
                if(Integer.bitCount(mask)!=count)continue;
                double e=0;
                for(int j=0;j<s.length;j++){
                    double m=Double.POSITIVE_INFINITY;
                    for(int i=0;i<n;i++)if((mask>>i&1)!=0&&d[i][j]<m)m=d[i][j];
                    e+=m;
                }
                if(e<bestE-1e-12){bestE=e;bestMask=mask;}
            }
            for(int i=0;i<n;i++)on[i]=(bestMask>>i&1)!=0;
        }
        int[] out=new int[n];int k=0;
        for(int i=0;i<n;i++)if(on[i])out[k++]=eight[i];
        for(int i=0;i<n;i++)if(!on[i])out[k++]=eight[i];
        return out;
    }
    private static double d2(double[] a,double[] b){
        double l=a[0]-b[0],x=a[1]-b[1],y=a[2]-b[2];return l*l+x*x+y*y;
    }
    private static double[][] sample(int[] argb){
        if(argb==null)return new double[0][];
        int n=0;for(int p:argb)if((p>>>24)>=128)n++;
        if(n==0)return new double[0][];
        int stride=Math.max(1,n/MAX_SAMPLES);
        double[][] out=new double[(n+stride-1)/stride][];
        int seen=0,k=0;
        for(int p:argb){if((p>>>24)<128)continue;if(seen++%stride==0)out[k++]=PerceptualVinylColor.lab(p);}
        return k==out.length?out:Arrays.copyOf(out,k);
    }
}

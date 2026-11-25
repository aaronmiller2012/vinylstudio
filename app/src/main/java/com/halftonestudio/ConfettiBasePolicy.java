package com.halftonestudio;

import java.util.Arrays;

/**
 * M5: which of the active vinyl colours is the solid confetti foundation (physical L1).
 *
 * Why this exists. FallenConfettiEngine always cuts L1 as the full Snip silhouette and then
 * scatters overlay fragments over it, covering only part of the area. The base colour
 * therefore shows through between fragments, and at viewing distance the eye integrates
 * base and fragments. Choosing the darkest colour (the pre-M5 rule in
 * PaletteStylePolicy.tonalWithBase) pulls every highlight and mid-tone region towards black.
 *
 * Models, all in OKLab, all choosing among the colours that are ALREADY the active stack
 * (no new RGB is synthesised, the stack is only re-ordered):
 * <ul>
 * <li>{@link #DARKEST}: lowest OKLab L (previous behaviour).</li>
 * <li>{@link #MEDIAN}: the colour whose lightness is the median of the active colours.</li>
 * <li>{@link #PERCEPTUAL}: the colour minimising the expected viewing-scale OKLab error,
 *     where a sampled photo pixel x is reproduced by a fraction (1-rho) of the base b and a
 *     fraction rho of the best overlay o: E(b)=mean_x min_o |x-((1-rho)b+rho*o)|^2.
 *     rho is the fragment area fraction (see {@link #COVERAGE}).</li>
 * </ul>
 * Pure, deterministic, no Android dependency.
 */
public final class ConfettiBasePolicy {
    private ConfettiBasePolicy() {}
    public static final int DARKEST=0, MEDIAN=1, PERCEPTUAL=2;
    /** Fragment area fraction used by the PERCEPTUAL model; measured value is documented in
     * docs/engineering/M5-COLOR.md (fraction of the silhouette not showing the base colour). */
    public static final double COVERAGE=.38;
    private static final int MAX_SAMPLES=1500;

    public static int baseIndex(int[] colors,int active,int[] photoArgb,int mode){
        return baseIndex(colors,active,photoArgb,mode,COVERAGE);
    }
    public static int baseIndex(int[] colors,int active,int[] photoArgb,int mode,double rho){
        if(colors==null||active<1||active>colors.length)
            throw new IllegalArgumentException("Invalid confetti palette");
        if(active==1)return 0;
        final double[][] lab=new double[active][];
        for(int i=0;i<active;i++)lab[i]=PerceptualVinylColor.lab(colors[i]);
        int darkest=0;
        for(int i=1;i<active;i++)if(lab[i][0]<lab[darkest][0]-1e-12)darkest=i;
        if(mode==DARKEST)return darkest;
        Integer[] order=new Integer[active];
        for(int i=0;i<active;i++)order[i]=i;
        Arrays.sort(order,(a,b)->{int c=Double.compare(lab[a][0],lab[b][0]);return c!=0?c:Integer.compare(a,b);});
        if(mode==MEDIAN)return order[(active-1)/2];
        double[][] samples=sample(photoArgb);
        if(samples.length==0)return order[(active-1)/2]; // no photo: the robust choice
        int best=darkest;double bestE=Double.POSITIVE_INFINITY;
        for(int b=0;b<active;b++){
            double e=0;
            for(double[] x:samples){
                double m=Double.POSITIVE_INFINITY;
                for(int o=0;o<active;o++){
                    if(o==b)continue;
                    double dl=x[0]-((1-rho)*lab[b][0]+rho*lab[o][0]);
                    double da=x[1]-((1-rho)*lab[b][1]+rho*lab[o][1]);
                    double db=x[2]-((1-rho)*lab[b][2]+rho*lab[o][2]);
                    m=Math.min(m,dl*dl+da*da+db*db);
                }
                e+=m;
            }
            if(e<bestE-1e-12){bestE=e;best=b;}
        }
        return best;
    }
    /** Deterministic strided sample of visible pixels in OKLab. */
    private static double[][] sample(int[] argb){
        if(argb==null||argb.length==0)return new double[0][];
        int n=0;
        for(int p:argb)if((p>>>24)>=128)n++;
        if(n==0)return new double[0][];
        int stride=Math.max(1,n/MAX_SAMPLES);
        double[][] out=new double[(n+stride-1)/stride][];
        int seen=0,k=0;
        for(int p:argb){
            if((p>>>24)<128)continue;
            if(seen++%stride==0)out[k++]=PerceptualVinylColor.lab(p);
        }
        return k==out.length?out:Arrays.copyOf(out,k);
    }
    /** Move the chosen base to slot 0; other active colours keep their relative order;
     * parked swatches (index >= active) never move. */
    public static int[] anchor(int[] colors,int active,int[] photoArgb,int mode){
        int b=baseIndex(colors,active,photoArgb,mode);
        int[] out=colors.clone();
        System.arraycopy(out,0,out,1,b);
        out[0]=colors[b];
        return out;
    }
}

package com.halftonestudio;

import java.util.Arrays;

/**
 * Reorder candidates on an EXPLICIT Overprint palette tap, not on renders or
 * material/paper changes. The photo's available colours, rather than the
 * input ordering, determine which physical L1..LN rolls are useful.
 *
 * No new RGB values are synthesized. Every original swatch is retained once
 * in the returned eight physical slots, including parked inactive colours.
 */
public final class OpaqueOverlayPhotoPalette {
    private OpaqueOverlayPhotoPalette() {}
    private static final int MAX_SAMPLES=768;
    private static double error(double[] a,double[] b){
        double l=a[0]-b[0],x=a[1]-b[1],y=a[2]-b[2];
        return l*l+x*x+y*y;
    }

    public static int[] arrange(int[] source,int count,int[] photo){
        if(source==null||source.length!=8||count<2||count>8)
            throw new IllegalArgumentException("Expected eight opaque vinyl swatches and 2–8 active layers");
        if(photo==null||photo.length==0)return tonalFallback(source,count);
        // Deterministic reservoir over VALID subject pixels, not fixed source
        // positions. A tiny opaque Snip region must always contribute samples.
        // Work is O(pixels); expensive Lab conversion stays bounded to 768.
        final int[] reservoir=new int[Math.min(MAX_SAMPLES,photo.length)];
        int seen=0;
        for(int i=0;i<photo.length;i++){
            if((i&4095)==0)CancellationCheckpoint.check();
            if((photo[i]>>>24)<128)continue;
            seen++;
            if(seen<=reservoir.length){reservoir[seen-1]=photo[i];continue;}
            int hash=i*0x9e3779b9;
            hash^=hash>>>16;hash*=0x85ebca6b;
            hash^=hash>>>13;hash*=0xc2b2ae35;
            hash^=hash>>>16;
            int slot=(int)((hash&0xffffffffL)%seen);
            if(slot<reservoir.length)reservoir[slot]=photo[i];
        }
        int valid=Math.min(seen,reservoir.length);
        if(valid==0)return tonalFallback(source,count);
        double[][] samples=new double[valid][];
        for(int j=0;j<valid;j++)samples[j]=PerceptualVinylColor.lab(reservoir[j]);
        double[][] chips=new double[8][];
        for(int i=0;i<8;i++)chips[i]=PerceptualVinylColor.lab(source[i]);
        // The exposed L1 foundation should reflect the photograph's shadow
        // range, not the arbitrary third-darkest roll. This avoids a blackish
        // base dominating otherwise bright portraits through uncovered marks.
        // Select a restrained photo shadow quantile; only existing swatches may
        // be chosen and the actual vinyl stack remains fully opaque.
        double[] photoLightness=new double[valid];
        for(int i=0;i<valid;i++)photoLightness[i]=samples[i][0];
        Arrays.sort(photoLightness);
        final double target=Math.min(.65,photoLightness[(int)((valid-1)*.30)]);
        int[] membership=new int[8];
        for(int j=0;j<valid;j++){
            int nearest=0;double best=Double.POSITIVE_INFINITY;
            for(int k=0;k<8;k++){
                double e=error(samples[j],chips[k]);
                if(e<best){best=e;nearest=k;}
            }
            membership[nearest]++;
        }
        int foundation=0;double foundationScore=Double.POSITIVE_INFINITY;
        for(int i=0;i<8;i++){
            double chroma=Math.hypot(chips[i][1],chips[i][2]);
            double score=Math.abs(chips[i][0]-target)
                +.34*Math.max(0, chips[i][0]-target)
                +.16*Math.max(0,chroma-.10)
                -.12*membership[i]/valid;
            if(score<foundationScore-1e-12){foundationScore=score;foundation=i;}
        }
        int[] arranged=new int[8];boolean[] used=new boolean[8];
        int[] selectedIndex=new int[count];
        arranged[0]=source[foundation];selectedIndex[0]=foundation;used[foundation]=true;
        double[] remaining=new double[valid];
        for(int j=0;j<valid;j++)remaining[j]=error(samples[j],chips[foundation]);
        for(int slot=1;slot<count;slot++){
            int bestIndex=-1;double highest=Double.NEGATIVE_INFINITY;
            for(int i=0;i<8;i++)if(!used[i]){
                double improvement=0;
                for(int j=0;j<valid;j++){
                    double e=error(samples[j],chips[i]);
                    if(e<remaining[j])improvement+=remaining[j]-e;
                }
                // Prefer a visibly distinct roll when the photo cannot break
                // ties (e.g. a flat portrait backdrop or duplicate chips).
                double separation=Double.POSITIVE_INFINITY;
                for(int prior=0;prior<slot;prior++)
                    separation=Math.min(separation,error(chips[i],chips[selectedIndex[prior]]));
                double score=improvement/valid+.00010*separation;
                if(score>highest+1e-12){highest=score;bestIndex=i;}
            }
            arranged[slot]=source[bestIndex];used[bestIndex]=true;selectedIndex[slot]=bestIndex;
            for(int j=0;j<valid;j++)
                remaining[j]=Math.min(remaining[j],error(samples[j],chips[bestIndex]));
        }
        // Park everything that is not cut. No swatch or editable RGB is lost.
        int next=count;
        for(int i=0;i<8;i++)if(!used[i])arranged[next++]=source[i];
        return arranged;
    }

    private static int[] tonalFallback(int[] source,int count){
        return PaletteStylePolicy.arrange(PaletteStylePolicy.CONFETTI_STACK,
            source,count,0xffffffff);
    }
}

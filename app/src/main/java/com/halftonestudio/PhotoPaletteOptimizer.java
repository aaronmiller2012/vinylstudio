package com.halftonestudio;

import java.util.Arrays;

/**
 * M4 / D12: deterministic, image-aware photo palette in OKLab.
 *
 * Why not plain k-means on a strided sample (the 558 algorithm):
 *  - a fixed row-major stride aliases on periodic content (stripes, fabric, screens);
 *  - farthest-point seeding starts clusters on single outlier pixels (sensor noise,
 *    JPEG ringing), which then become "invented" swatches;
 *  - empty clusters were reported as a placeholder grey and refilled without
 *    reassigning members, and near-identical clusters were never merged.
 *
 * This version:
 *  1. bins EVERY visible pixel into a 16x16x16 sRGB histogram (no sampling, so no
 *     aliasing; cost is linear in pixels and independent of k);
 *  2. ignores bins below a support floor when seeding, so no swatch is seeded
 *     from a handful of stray pixels;
 *  3. seeds by argmax(support^0.5 * D^2): dominant colours come first, but a
 *     small, clearly distinct accent (an eye, a red shirt) outranks a third shade
 *     of the background;
 *  4. runs weighted Lloyd iterations on the bins, reseeding any empty cluster;
 *  5. merges clusters closer than {@link #MERGE_DE} and gives the slot to the next
 *     most distinct supported colour; if the photo genuinely lacks distinct colour,
 *     the slot splits the cluster with the largest spread instead (still real pixels);
 *  6. reports each swatch as its cluster's OKLab mean, snapped to the nearest member
 *     bin mean when the mean is far from every member (a bimodal cluster's muddy
 *     average is never shown as a "photo colour").
 *
 * Everything is integer-deterministic for identical input: no randomness, ties by index.
 * Android-independent so the identical algorithm is host-tested.
 */
public final class PhotoPaletteOptimizer {
    private PhotoPaletteOptimizer(){}

    /** OKLab distance below which two swatches read as the same vinyl colour. */
    static final double MERGE_DE=.035;
    /** Centroid-to-nearest-member distance above which the centroid is snapped. */
    static final double SNAP_DE=.045;
    static final int BITS=4,LEVELS=1<<BITS,BINS=LEVELS*LEVELS*LEVELS;

    public static final class Result {
        /** Swatches ordered dark to light (OKLab L, then a, b), all opaque. */
        public final int[] colors;
        /** Fraction of visible pixels nearest to each swatch. */
        public final float[] support;
        /** Number of swatches at least MERGE_DE from every other swatch. */
        public final int distinct;
        Result(int[] c,float[] s,int d){colors=c;support=s;distinct=d;}
    }

    public static Result optimize(int[] pixels,boolean[] valid,int k){
        if(pixels==null||valid==null||valid.length!=pixels.length||k<1||k>9)
            throw new IllegalArgumentException("Invalid photo palette request");
        // ---- 1. histogram of every visible pixel
        long[] count=new long[BINS];double[][] sumLab=new double[BINS][3];long[][] sumRgb=new long[BINS][3];
        long total=0;double[] lab=new double[3];
        for(int i=0;i<pixels.length;i++){
            if(!valid[i])continue;
            int c=pixels[i];int r=(c>>16)&255,g=(c>>8)&255,b=c&255;
            int bin=((r>>(8-BITS))*LEVELS+(g>>(8-BITS)))*LEVELS+(b>>(8-BITS));
            oklab(c,lab);lab[0]=toe(lab[0]);count[bin]++;total++;
            sumLab[bin][0]+=lab[0];sumLab[bin][1]+=lab[1];sumLab[bin][2]+=lab[2];
            sumRgb[bin][0]+=r;sumRgb[bin][1]+=g;sumRgb[bin][2]+=b;
        }
        if(total==0)throw new IllegalArgumentException("Photo has no visible pixels");
        int n=0;for(long c:count)if(c>0)n++;
        double[][] p=new double[n][];double[] w=new double[n];int[] rgb=new int[n];
        for(int bin=0,j=0;bin<BINS;bin++)if(count[bin]>0){
            double c=count[bin];
            p[j]=new double[]{sumLab[bin][0]/c,sumLab[bin][1]/c,sumLab[bin][2]/c};w[j]=c;
            rgb[j]=0xff000000|((int)Math.round(sumRgb[bin][0]/c)<<16)|((int)Math.round(sumRgb[bin][1]/c)<<8)|(int)Math.round(sumRgb[bin][2]/c);
            j++;
        }
        // Support is measured over a perceptual neighbourhood, not one 16-level bin: a subject
        // whose colour is spread thinly over many bins (a lit rocket, skin under soft light)
        // is one supported colour. Review finding F1: per-bin support lost such subjects.
        double[] ns=neighbourhoodSupport(p,w);
        // ---- 2. support floor: a seed needs at least 0.15% of the visible pixels
        // (or all of them for a tiny image). Lloyd still assigns every bin.
        double floor=Math.max(1,Math.min(total*.0015,total/(double)(4*k)));
        double[][] centers=new double[k][];int placed=0;
        // ---- 3. seeding
        int first=0;for(int j=1;j<n;j++)if(ns[j]>ns[first])first=j;
        centers[placed++]=p[first].clone();
        while(placed<k){
            int pick=bestCandidate(p,ns,centers,placed,floor,0);
            if(pick<0)break; // no further supported colour; never seed from stray pixels
            centers[placed++]=p[pick].clone();
        }
        int[] owner=new int[n];
        lloyd(p,w,ns,centers,placed,owner,floor);
        // Fewer supported colours than slots: split the widest real cluster.
        while(placed<k){
            double[] split=splitWidest(p,w,ns,owner,centers,placed,-1,floor);
            if(split==null)break;
            centers[placed++]=split;
            lloyd(p,w,ns,centers,placed,owner,floor);
        }
        // ---- 5. merge near-duplicates, reassign slot to the next distinct colour or a split
        for(int guard=0;guard<2*k;guard++){
            int a=-1,b=-1;double closest=MERGE_DE;
            for(int i=0;i<placed;i++)for(int j=i+1;j<placed;j++){
                double d=dist(centers[i],centers[j]);if(d<closest){closest=d;a=i;b=j;}}
            if(a<0)break;
            double[] support=clusterWeights(w,owner,placed);
            int drop=support[a]>=support[b]?b:a;
            int pick=bestCandidate(p,ns,centers,placed,floor,MERGE_DE,drop);
            if(pick>=0){centers[drop]=p[pick].clone();}
            else{
                double[] split=splitWidest(p,w,ns,owner,centers,placed,drop,floor);
                if(split==null)break; // the photo genuinely has fewer colours than slots
                centers[drop]=split;
            }
            lloyd(p,w,ns,centers,placed,owner,floor);
        }
        // ---- 6. report colours
        double[] support=clusterWeights(w,owner,placed);
        int[] colors=new int[placed];
        for(int c=0;c<placed;c++){
            int nearest=-1;double best=Double.POSITIVE_INFINITY,heaviest=-1;int heavy=-1;
            for(int j=0;j<n;j++)if(owner[j]==c){
                double d=dist(p[j],centers[c]);if(d<best){best=d;nearest=j;}
                if(w[j]>heaviest){heaviest=w[j];heavy=j;}
            }
            if(nearest<0){colors[c]=fromWorking(centers[c]);continue;}
            colors[c]=best>SNAP_DE?rgb[heavy]:fromWorking(centers[c]);
        }
        // Order dark to light by the reported colour (stable, deterministic).
        Integer[] order=new Integer[placed];for(int i=0;i<placed;i++)order[i]=i;
        double[][] rep=new double[placed][3];for(int i=0;i<placed;i++)oklab(colors[i],rep[i]);
        Arrays.sort(order,(x,y)->{
            int c=Double.compare(rep[x][0],rep[y][0]);if(c!=0)return c;
            c=Double.compare(rep[x][1],rep[y][1]);if(c!=0)return c;
            c=Double.compare(rep[x][2],rep[y][2]);return c!=0?c:Integer.compare(x,y);
        });
        int[] outColors=new int[k];float[] outSupport=new float[k];
        for(int i=0;i<placed;i++){outColors[i]=colors[order[i]];outSupport[i]=(float)(support[order[i]]/total);}
        // Fewer distinct bins than slots (a flat or two-tone image): repeat the
        // real colours, most supported first. Never invent a colour.
        for(int i=placed;i<k;i++){int src=heaviestIndex(outSupport,placed,i-placed);outColors[i]=outColors[src];outSupport[i]=0f;}
        int distinct=0;
        for(int i=0;i<k;i++){boolean unique=true;
            for(int j=0;j<i;j++)if(distance(outColors[i],outColors[j])<MERGE_DE){unique=false;break;}
            if(unique)distinct++;}
        return new Result(outColors,outSupport,distinct);
    }

    /** Pixels within {@link #NEIGHBOURHOOD_DE} of each bin (including itself). O(bins^2), bins <= 4096. */
    static final double NEIGHBOURHOOD_DE=.05;
    private static double[] neighbourhoodSupport(double[][] p,double[] w){
        int n=p.length;double[] s=new double[n];double r2=NEIGHBOURHOOD_DE*NEIGHBOURHOOD_DE;
        for(int i=0;i<n;i++){s[i]+=w[i];
            for(int j=i+1;j<n;j++){
                double x=p[i][0]-p[j][0];if(x*x>=r2)continue;
                double y=p[i][1]-p[j][1],z=p[i][2]-p[j][2];
                if(x*x+y*y+z*z<r2){s[i]+=w[j];s[j]+=w[i];}
            }
        }
        return s;
    }
    private static int heaviestIndex(float[] s,int n,int rank){
        Integer[] o=new Integer[n];for(int i=0;i<n;i++)o[i]=i;
        Arrays.sort(o,(a,b)->{int c=Float.compare(s[b],s[a]);return c!=0?c:Integer.compare(a,b);});
        return o[rank%n];
    }
    private static int bestCandidate(double[][] p,double[] w,double[][] centers,int placed,double floor,double minD){
        return bestCandidate(p,w,centers,placed,floor,minD,-1);
    }
    /** argmax sqrt(support) * D^2 to the existing centres (excluding {@code skip}). */
    private static int bestCandidate(double[][] p,double[] w,double[][] centers,int placed,double floor,double minD,int skip){
        int best=-1;double score=0;
        for(int j=0;j<p.length;j++){
            if(w[j]<floor)continue;
            double d=Double.POSITIVE_INFINITY;
            for(int c=0;c<placed;c++)if(c!=skip)d=Math.min(d,dist(p[j],centers[c]));
            if(d<=minD||d<1e-9)continue;
            double s=Math.sqrt(w[j])*d*d;
            if(s>score){score=s;best=j;}
        }
        return best;
    }
    private static void lloyd(double[][] p,double[] w,double[] ns,double[][] centers,int placed,int[] owner,double floor){
        for(int it=0;it<24;it++){
            double[][] sum=new double[placed][3];double[] wt=new double[placed];boolean moved=false;
            for(int j=0;j<p.length;j++){
                int c=nearest(p[j],centers,placed);
                if(owner[j]!=c){owner[j]=c;moved=true;}
                sum[c][0]+=w[j]*p[j][0];sum[c][1]+=w[j]*p[j][1];sum[c][2]+=w[j]*p[j][2];wt[c]+=w[j];
            }
            for(int c=0;c<placed;c++){
                if(wt[c]>0){centers[c][0]=sum[c][0]/wt[c];centers[c][1]=sum[c][1]/wt[c];centers[c][2]=sum[c][2]/wt[c];}
                else{ // empty cluster: reseed at the most distinct supported colour
                    int pick=bestCandidate(p,ns,centers,placed,floor,0,c);
                    if(pick>=0){centers[c]=p[pick].clone();moved=true;}
                }
            }
            if(!moved&&it>0)break;
        }
        for(int j=0;j<p.length;j++)owner[j]=nearest(p[j],centers,placed);
    }
    /** New centre for slot {@code slot}: the member of the widest cluster farthest from its centre. */
    private static double[] splitWidest(double[][] p,double[] w,double[] ns,int[] owner,double[][] centers,int placed,int slot,double floor){
        double[] spread=new double[placed];
        for(int j=0;j<p.length;j++)if(ns[j]>=floor&&owner[j]!=slot&&owner[j]<placed)spread[owner[j]]+=w[j]*dist(p[j],centers[owner[j]]);
        int widest=-1;double best=0;
        for(int c=0;c<placed;c++)if(c!=slot&&spread[c]>best){best=spread[c];widest=c;}
        if(widest<0)return null;
        // Split toward the member that best combines support and distance, so a
        // single stray pixel at the cluster's edge does not become the new swatch.
        int far=-1;double fd=0;
        for(int j=0;j<p.length;j++)if(owner[j]==widest&&ns[j]>=floor){
            double d=dist(p[j],centers[widest]);if(d<1e-6)continue;
            double s=Math.sqrt(ns[j])*d*d;if(s>fd){fd=s;far=j;}}
        if(far<0||dist(p[far],centers[widest])<.012)return null; // nothing meaningful to split
        return p[far].clone();
    }
    private static double[] clusterWeights(double[] w,int[] owner,int placed){
        double[] s=new double[placed];for(int j=0;j<w.length;j++)s[owner[j]]+=w[j];return s;
    }
    private static int nearest(double[] x,double[][] c,int placed){
        int best=0;double bd=Double.POSITIVE_INFINITY;
        for(int i=0;i<placed;i++){double d=dist(x,c[i]);if(d<bd){bd=d;best=i;}}
        return best;
    }
    static double dist(double[] a,double[] b){
        double x=a[0]-b[0],y=a[1]-b[1],z=a[2]-b[2];return Math.sqrt(x*x+y*y+z*z);
    }
    /** OKLab Euclidean distance between two opaque sRGB colours. */
    public static double distance(int a,int b){
        double[] x=new double[3],y=new double[3];oklab(a,x);oklab(b,y);return dist(x,y);
    }

    // ------------------------------------------------- working space: OKLab with toe
    // OKLab has no near-black toe, so differences between deep shadows look much larger
    // than they are seen (000000 vs 050505: 0.115 OKLab, ~1.4 CIELAB dE) and pull swatches
    // into the shadows (review F1). Clustering uses Ottosson's toe-corrected lightness Lr,
    // which tracks CIELAB L* in the shadows and equals L elsewhere; a and b are unchanged.
    private static final double K1=.206,K2=.03,K3=(1+K1)/(1+K2);
    static double toe(double l){double t=K3*l-K1;return .5*(t+Math.sqrt(t*t+4*K2*K3*l));}
    static double toeInverse(double lr){return (lr*lr+K1*lr)/(K3*(lr+K2));}
    private static int fromWorking(double[] w){return fromOklab(new double[]{toeInverse(w[0]),w[1],w[2]});}

    // ---------------------------------------------------------------- OKLab
    private static final double[] LINEAR=new double[256];
    static{for(int i=0;i<256;i++){double c=i/255.0;LINEAR[i]=c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4);}}
    static void oklab(int c,double[] out){
        double r=LINEAR[(c>>16)&255],g=LINEAR[(c>>8)&255],b=LINEAR[c&255];
        double l=Math.cbrt(.4122214708*r+.5363325363*g+.0514459929*b);
        double m=Math.cbrt(.2119034982*r+.6806995451*g+.1073969566*b);
        double s=Math.cbrt(.0883024619*r+.2817188376*g+.6299787005*b);
        out[0]=.2104542553*l+.7936177850*m-.0040720468*s;
        out[1]=1.9779984951*l-2.4285922050*m+.4505937099*s;
        out[2]=.0259040371*l+.7827717662*m-.8086757660*s;
    }
    static int fromOklab(double[] lab){
        double l=lab[0]+.3963377774*lab[1]+.2158037573*lab[2];
        double m=lab[0]-.1055613458*lab[1]-.0638541728*lab[2];
        double s=lab[0]-.0894841775*lab[1]-1.2914855480*lab[2];
        l=l*l*l;m=m*m*m;s=s*s*s;
        double r=4.0767416621*l-3.3077115913*m+.2309699292*s;
        double g=-1.2684380046*l+2.6097574011*m-.3413193965*s;
        double b=-.0041960863*l-.7034186147*m+1.7076147010*s;
        return 0xff000000|(enc(r)<<16)|(enc(g)<<8)|enc(b);
    }
    private static int enc(double v){
        v=Math.max(0,Math.min(1,v));
        double s=v<=.0031308?12.92*v:1.055*Math.pow(v,1/2.4)-.055;
        return (int)Math.round(Math.max(0,Math.min(1,s))*255);
    }
}

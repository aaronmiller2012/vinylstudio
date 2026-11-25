package com.halftonestudio;

/** Exact subset selection for small banks of opaque physical vinyl. Returns a permutation,
 * never invents colors, never changes the hue, never changes the original slot identities.
 * A caller must apply the same permutation to every slot-indexed property. */
public final class PaletteReduction529Reference {
    private PaletteReduction529Reference() {}
    public static final int PORTRAIT=0, STRUCTURAL=1, PANE=2, TONAL=3, TONAL_STRUCTURE=4;
    private static final int SAMPLE_LIMIT=2048;
    public static int[] identity(int n){int[] p=new int[n];for(int i=0;i<n;i++)p[i]=i;return p;}
    public static int[] validRanks(int[] ranks,int n){
        if(ranks==null||ranks.length!=n)return identity(n);
        int seen=0;for(int r:ranks){if(r<0||r>=n||(seen&(1<<r))!=0)return identity(n);seen|=1<<r;}
        return ranks.clone();
    }
    public static int policyForStyle(int style,int variant){
        if(style==VinylStyleSuite.GLASS)return PANE;
        if(style==VinylStyleSuite.REDUCTION_WOODCUT||style==VinylStyleSuite.POSTER_STENCIL)return TONAL_STRUCTURE;
        if(style==VinylStyleSuite.TOPOGRAPHIC)return variant==0?TONAL_STRUCTURE:TONAL;
        if(style==VinylStyleSuite.MARQUETRY||style==VinylStyleSuite.TORN_COLLAGE||
           NewStyleLayerPolicy.isStructuralLayer(style,0))return STRUCTURAL;
        return PORTRAIT;
    }
    private static boolean anchored(int policy){return policy==STRUCTURAL||policy==PANE||policy==TONAL_STRUCTURE;}
    /** Input ranks describe original stack order before any count reduction. */
    public static int[] plan(int[] colors,int[] ranks,int count,int[] samples,int policy){
        return plan(colors,ranks,count,samples,policy,0);
    }
    public static int[] plan(int[] colors,int[] ranks,int count,int[] samples,int policy,int required){
        return plan(colors,ranks,count,samples,policy,required,(1<<colors.length)-1);
    }
    public static int[] plan(int[] colors,int[] ranks,int count,int[] samples,int policy,int required,int eligible){
        int n=colors.length;if(n<1||n>9||count<1||count>n)throw new IllegalArgumentException("Invalid vinyl bank");
        ranks=validRanks(ranks,n);
        int[] order=identity(n);for(int i=1;i<n;i++){int j=i,v=order[i];while(j>0&&ranks[order[j-1]]>ranks[v]){order[j]=order[j-1];j--;}order[j]=v;}
        if(anchored(policy)){int pos=0;while(order[pos]!=0)pos++;System.arraycopy(order,0,order,1,pos);order[0]=0;}
        if(count==n)return order;
        double[][] lab=new double[n][];for(int i=0;i<n;i++)lab[i]=PerceptualVinylColor.lab(colors[i]);
        boolean anchor=anchored(policy);
        if(anchor)required|=1; // Structural foundation or lead at current slot 0.
        eligible=(eligible|required)&((1<<n)-1);
        if(Integer.bitCount(eligible)<count)eligible=(1<<n)-1;
        int dark=-1,light=-1;for(int i:order){if((eligible&(1<<i))==0||(policy==PANE&&i==0))continue;
            if(dark<0||lab[i][0]<lab[dark][0])dark=i;if(light<0||lab[i][0]>lab[light][0])light=i;}
        if(Integer.bitCount(required)>count)throw new IllegalArgumentException("Too many protected sheets");
        // Bounded, evenly distributed source samples; alpha is a Snip/coverage mask.
        int size=samples==null?0:samples.length, steps=Math.min(size,SAMPLE_LIMIT);
        double[][] errors=new double[steps+n][n];double[] weights=new double[steps+n];int used=0;
        double minLight=1,maxLight=0;
        for(int t=0;t<steps;t++){
            int rgb=samples[(int)((long)t*size/steps)];if((rgb>>>24)<128)continue;
            double[] p=PerceptualVinylColor.lab(rgb);fill(errors[used],p,lab,policy);
            // Protect rare dark/light regions without forcing unrelated hues into them.
            weights[used++]=1+.65*Math.abs(2*p[0]-1);
            minLight=Math.min(minLight,p[0]);maxLight=Math.max(maxLight,p[0]);
        }
        // A broad tonal subject needs the full available light/dark range. A
        // near-flat photograph may omit an irrelevant extreme instead.
        if(policy!=PANE&&count>=2&&dark>=0&&light>=0&&lab[light][0]-lab[dark][0]>.08&&
           (used==0||maxLight-minLight>.16||policy==TONAL||policy==TONAL_STRUCTURE)){
            int ends=required|(1<<dark)|(1<<light);
            if(Integer.bitCount(ends)<=count)required=ends;
        }
        if(used==0)for(int i=0;i<n;i++){if(policy==PANE&&i==0)continue;fill(errors[used],lab[i],lab,policy);weights[used++]=1;}
        int bestMask=0;double best=Double.POSITIVE_INFINITY;
        // Exact enumeration: at most C(9,4)=126 subsets, rather than a heuristic trim.
        for(int canonicalMask=1;canonicalMask<(1<<n);canonicalMask++){
            if(Integer.bitCount(canonicalMask)!=count)continue;
            int mask=0;for(int j=0;j<n;j++)if((canonicalMask&(1<<j))!=0)mask|=1<<order[j];
            if((mask&required)!=required||(mask&~eligible)!=0)continue;
            int visible=policy==PANE?mask&~1:mask;
            if(visible==0)visible=mask;
            double cost=0;
            for(int s=0;s<used;s++){
                double min=Double.POSITIVE_INFINITY;for(int bits=visible;bits!=0;bits&=bits-1){int j=Integer.numberOfTrailingZeros(bits);min=Math.min(min,errors[s][j]);}
                cost+=weights[s]*min;if(cost>best)break;
            }
            if(cost<best-1e-12){best=cost;bestMask=mask;}
        }
        int[] permutation=new int[n];int at=0;
        for(int i:order)if((bestMask&(1<<i))!=0)permutation[at++]=i;
        for(int i:order)if((bestMask&(1<<i))==0)permutation[at++]=i;
        return permutation;
    }
    private static void fill(double[] errors,double[] p,double[][] lab,int policy){
        for(int j=0;j<lab.length;j++){double l=p[0]-lab[j][0],a=p[1]-lab[j][1],b=p[2]-lab[j][2];errors[j]=(policy==TONAL||policy==TONAL_STRUCTURE?4.0:1.95)*l*l+a*a+b*b;}
    }
    public static int[] permute(int[] values,int[] p){int[] out=values.clone();for(int i=0;i<p.length;i++)out[i]=values[p[i]];return out;}
    public static float[] permute(float[] values,int[] p){float[] out=values.clone();for(int i=0;i<p.length;i++)out[i]=values[p[i]];return out;}
    public static boolean[] permute(boolean[] values,int[] p){boolean[] out=values.clone();for(int i=0;i<p.length;i++)out[i]=values[p[i]];return out;}
    public static int remap(int old,int[] p){for(int i=0;i<p.length;i++)if(p[i]==old)return i;return -1;}
}

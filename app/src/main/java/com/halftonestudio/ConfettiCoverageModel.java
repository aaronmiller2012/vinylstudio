package com.halftonestudio;

/** Local area-mixture model: opaque base and one visible overlay, not alpha vinyl.
 * Linear-light RGB averaging followed by perceptual comparison. Bounded lazy
 * RGB bins avoid repeated colour-space conversions in the scatter loop. */
final class ConfettiCoverageModel {
    private final double[][][] candidates;
    private final byte[] slots=new byte[32768], fractions=new byte[32768];
    private static final double[] LINEAR=new double[256];
    static {for(int i=0;i<256;i++){double x=i/255.;LINEAR[i]=x<=.04045?x/12.92:Math.pow((x+.055)/1.055,2.4);}}
    int slot; float fraction;
    ConfettiCoverageModel(int[] colors){
        candidates=new double[colors.length][9][];
        int base=colors[0];
        for(int s=1;s<colors.length;s++)for(int k=0;k<=8;k++){
            double f=k/8.;int c=colors[s];
            double r=lerp(LINEAR[(base>>>16)&255],LINEAR[(c>>>16)&255],f);
            double g=lerp(LINEAR[(base>>>8)&255],LINEAR[(c>>>8)&255],f);
            double b=lerp(LINEAR[base&255],LINEAR[c&255],f);
            candidates[s][k]=PerceptualVinylColor.lab(0xff000000|(encode(r)<<16)|(encode(g)<<8)|encode(b));
        }
    }
    private static double lerp(double a,double b,double f){return a+(b-a)*f;}
    private static int encode(double x){return Math.max(0,Math.min(255,(int)Math.round(255*(x<=.0031308?12.92*x:1.055*Math.pow(x,1/2.4)-.055))));}
    void choose(int rgb){
        int r=(rgb>>>19)&31,g=(rgb>>>11)&31,b=(rgb>>>3)&31;
        int i=(r<<10)|(g<<5)|b;
        if(slots[i]==0){
            double[] x=PerceptualVinylColor.lab(0xff000000|((r*8+4)<<16)|((g*8+4)<<8)|(b*8+4));
            double best=Double.POSITIVE_INFINITY;int bs=1,bk=0;
            for(int s=1;s<candidates.length;s++)for(int k=0;k<=8;k++){
                double[] y=candidates[s][k];double dl=x[0]-y[0],da=x[1]-y[1],db=x[2]-y[2];
                // Prefer high fidelity, with a small regularizer against using an
                // extreme overlay for an almost invisible area contribution.
                double e=1.5*dl*dl+da*da+db*db+.0004*(8-k)/8.;
                if(e<best){best=e;bs=s;bk=k;}
            }
            slots[i]=(byte)bs;fractions[i]=(byte)bk;
        }
        slot=slots[i]&255;fraction=(fractions[i]&255)/8f;
    }
}

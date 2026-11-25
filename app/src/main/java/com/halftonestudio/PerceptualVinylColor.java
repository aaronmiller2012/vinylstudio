package com.halftonestudio;

/** Compact, deterministic Oklab distance for ranking RGB palette candidates. */
public final class PerceptualVinylColor {
    private PerceptualVinylColor() { }
    private static final double[] LINEAR=new double[256];
    static {
        for(int i=0;i<LINEAR.length;i++) {
            double v=i/255d;
            LINEAR[i]=v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);
        }
    }
    public static double[] lab(int rgb) {
        double r=LINEAR[(rgb>>>16)&255],g=LINEAR[(rgb>>>8)&255],b=LINEAR[rgb&255];
        double l=Math.cbrt(.4122214708*r+.5363325363*g+.0514459929*b);
        double m=Math.cbrt(.2119034982*r+.6806995451*g+.1073969566*b);
        double s=Math.cbrt(.0883024619*r+.2817188376*g+.6299787005*b);
        return new double[]{
            .2104542553*l+.7936177850*m-.0040720468*s,
            1.9779984951*l-2.4285922050*m+.4505937099*s,
            .0259040371*l+.7827717662*m-.8086757660*s
        };
    }
    public static double distance(int a,int b) {
        double[] x=lab(a),y=lab(b);
        double dl=x[0]-y[0],da=x[1]-y[1],db=x[2]-y[2];
        return Math.sqrt(dl*dl+da*da+db*db);
    }
}

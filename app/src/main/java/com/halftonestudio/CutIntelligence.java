package com.halftonestudio;

/** Non-destructive inspection bounds. Area alone does not establish a local width. */
final class CutIntelligence {
    private CutIntelligence() {}
    static boolean cannotContainMinimumDisk(double areaMm2,double diameterMm){
        return Double.isFinite(areaMm2)&&areaMm2>0&&Double.isFinite(diameterMm)&&diameterMm>0
            && areaMm2 < Math.PI*diameterMm*diameterMm/4.0;
    }
    static String summary(double[] pieces,double[] holes,double minimum,double gap){
        int small=0,waste=0;
        for(double a:pieces)if(cannotContainMinimumDisk(a,minimum))small++;
        for(double a:holes)if(cannotContainMinimumDisk(a,gap))waste++;
        return "Area-bound small retained components: "+small+" / "+pieces.length+"\n"
            +(gap>0?"Area-bound small hole rings: "+waste+" / "+holes.length:"Waste threshold disabled; not evaluated")+"\n"
            +"These are conservative area candidates, not local width measurements or a weeding guarantee.\n";
    }
}

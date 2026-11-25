package com.halftonestudio;

/** Physical-mm adaptive cubic sampling for read-only artwork measurements.
 * Each accepted span is within 0.02 mm of its finite chord, and the sum of
 * chord-vs-control-polygon length bounds is <= 0.002 mm per input cubic
 * (before float endpoint rounding). No artwork/export path is modified. */
final class PrecisionCurveSampler {
    private PrecisionCurveSampler(){}
    interface PointSink { void add(float x,float y); }
    static void cubic(double x0,double y0,double x1,double y1,double x2,double y2,double x3,double y3,PointSink sink,Runnable guard){
        sample(x0,y0,x1,y1,x2,y2,x3,y3,.002,0,sink,guard);
    }
    private static void sample(double a,double b,double c,double d,double e,double f,double g,double h,double budget,int depth,PointSink sink,Runnable guard){
        CancellationCheckpoint.check();guard.run();
        double chord=Math.hypot(g-a,h-b), polygon=Math.hypot(c-a,d-b)+Math.hypot(e-c,f-d)+Math.hypot(g-e,h-f);
        double flat=Math.max(distance(c,d,a,b,g,h),distance(e,f,a,b,g,h));
        if(Double.isFinite(polygon)&&polygon-chord<=budget&&flat<=.02){sink.add((float)g,(float)h);return;}
        if(depth>=32||!Double.isFinite(polygon))throw new IllegalStateException("Curve measurement exceeds accuracy budget");
        double ac=(a+c)/2,bd=(b+d)/2,ce=(c+e)/2,df=(d+f)/2,eg=(e+g)/2,fh=(f+h)/2;
        double ace=(ac+ce)/2,bdf=(bd+df)/2,ceg=(ce+eg)/2,dfh=(df+fh)/2;
        double midX=(ace+ceg)/2,midY=(bdf+dfh)/2;
        sample(a,b,ac,bd,ace,bdf,midX,midY,budget/2,depth+1,sink,guard);
        sample(midX,midY,ceg,dfh,eg,fh,g,h,budget/2,depth+1,sink,guard);
    }
    private static double distance(double x,double y,double a,double b,double c,double d){
        double dx=c-a,dy=d-b,den=dx*dx+dy*dy;
        double t=den==0?0:Math.max(0,Math.min(1,((x-a)*dx+(y-b)*dy)/den));
        return Math.hypot(x-a-t*dx,y-b-t*dy);
    }
    /** Maximum stretch of a unit circle mapped by the two axis vectors. */
    static double ellipseRadiusBound(double ux,double uy,double vx,double vy){
        double aa=ux*ux+uy*uy,bb=vx*vx+vy*vy,ab=ux*vx+uy*vy;
        return Math.sqrt((aa+bb+Math.hypot(aa-bb,2*ab))/2);
    }
}

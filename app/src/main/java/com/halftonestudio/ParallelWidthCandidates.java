package com.halftonestudio;
/** Conservative candidate detector for opposing, nearly parallel boundary segments.
 * Returns midpoint of a shared projection, not a universal local thickness. */
final class ParallelWidthCandidates {
    private ParallelWidthCandidates(){}
    static double[] between(double ax,double ay,double bx,double by,double cx,double cy,double dx,double dy,double limit){
        double ux=bx-ax,uy=by-ay,vx=dx-cx,vy=dy-cy;
        double a=Math.hypot(ux,uy),b=Math.hypot(vx,vy);
        if(a<limit||b<limit||limit<=0)return null;
        ux/=a;uy/=a;vx/=b;vy/=b;
        if(ux*vx+uy*vy>-.98)return null;
        double c=(cx-ax)*ux+(cy-ay)*uy,d=(dx-ax)*ux+(dy-ay)*uy;
        double lo=Math.max(0,Math.min(c,d)),hi=Math.min(a,Math.max(c,d));
        if(hi-lo<limit)return null;
        double t=(lo+hi)/2, f=(t-c)/(d-c);
        double px=ax+t*ux,py=ay+t*uy,qx=cx+f*(dx-cx),qy=cy+f*(dy-cy);
        double width=Math.hypot(px-qx,py-qy);
        return width>0&&width+.003<limit?new double[]{px,py,qx,qy,width}:null;
    }
}

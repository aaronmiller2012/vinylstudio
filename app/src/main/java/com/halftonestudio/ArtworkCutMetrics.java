package com.halftonestudio;

import java.util.List;

/** Read-only metrics of standard artwork contours, in physical millimetres.
 * Counts describe authored regions, not a Boolean union of overlapping shapes.
 * Excludes registration and separately generated underlays. */
public final class ArtworkCutMetrics {
    private ArtworkCutMetrics() {}
    public static final class Stats {
        public final long regions, holes, loops;
        public final double lengthMm, summedAreaMm2;
        public final long openPaths;
        public final boolean pathSummary;
        Stats(long regions,long holes,double length,double area) {
            this(regions,holes,regions+holes,0,length,area,false);
        }
        private Stats(long regions,long holes,long loops,long open,double length,double area,boolean summary){
            this.regions=regions;this.holes=holes;this.loops=loops;openPaths=open;
            lengthMm=length;summedAreaMm2=area;pathSummary=summary;
        }
    }
    static Stats pathSummary(long closed,long open,double mm){
        if(closed<0||open<0||!Double.isFinite(mm)||mm<0)throw new IllegalArgumentException("Invalid path summary");
        return new Stats(0,0,closed,open,mm,Double.NaN,true);
    }
    public static Stats measure(List<VinylEngine.Shape> shapes) {
        long regions=0,holes=0; double length=0,area=0;
        for(VinylEngine.Shape s:shapes){
            CancellationCheckpoint.check();
            regions++;
            if(s.circle){
                if(!Float.isFinite(s.x)||!Float.isFinite(s.y)||!Float.isFinite(s.r)||s.r<=0)
                    throw new IllegalArgumentException("Invalid circle");
                length+=2*Math.PI*s.r;area+=Math.PI*(double)s.r*s.r;
                continue;
            }
            float[][] rings=s.contours==null?new float[][]{s.xy}:s.contours;
            double net=0;
            for(int r=0;r<rings.length;r++){
                float[] xy=rings[r];
                if(xy==null||xy.length<6||(xy.length&1)!=0)throw new IllegalArgumentException("Invalid ring");
                double cross=0;
                for(int i=0;i<xy.length;i+=2){
                    if((i&63)==0)CancellationCheckpoint.check();
                    int j=(i+2)%xy.length;
                    if(!Float.isFinite(xy[i])||!Float.isFinite(xy[i+1]))throw new IllegalArgumentException("Nonfinite point");
                    double x=(double)xy[i]-xy[0],y=(double)xy[i+1]-xy[1];
                    double nx=(double)xy[j]-xy[0],ny=(double)xy[j+1]-xy[1];
                    cross+=x*ny-y*nx;length+=Math.hypot(nx-x,ny-y);
                }
                double a=Math.abs(cross)*.5;
                if(!(a>0))throw new IllegalArgumentException("Degenerate ring");
                if(r==0)net=a;else{net-=a;holes++;}
            }
            if(!(net>0))throw new IllegalArgumentException("Invalid region area");
            area+=net;
        }
        return new Stats(regions,holes,length,area);
    }
}

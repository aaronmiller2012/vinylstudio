package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Physical-size-aware stencil contours. Converts grid boundary loops into continuous
 * cubic outlines and validates the result by scan converting the proposed cut paths
 * back onto the original mask grid.  Never modifies layer ownership or stack order.
 * This is an independent implementation; no third-party tracing code is copied.
 */
final class SmoothStencilContours {
    private SmoothStencilContours() {}
    private static final class P {
        final double x,y;
        P(double x,double y){this.x=x;this.y=y;}
    }
    static final class Geometry {
        final String data;
        final boolean[] raster;
        final boolean curved;
        final int loops;
        Geometry(String s,boolean[] r,boolean c,int n){data=s;raster=r;curved=c;loops=n;}
    }
    private static double dist(P a,P b){return Math.hypot(a.x-b.x,a.y-b.y);}
    private static P mix(P a,P b,double t){return new P(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t);}
    private static boolean interrupted(){return Thread.currentThread().isInterrupted();}
    private static void check(){if(interrupted())throw new java.util.concurrent.CancellationException("Stencil contour smoothing cancelled");}
    /** A cubic curve chain, sampled for topology verification and cut preview. */
    private static final class Ring {
        final ArrayList<P> flattened;
        final String svg;
        final boolean curved;
        Ring(ArrayList<P> f,String s,boolean c){flattened=f;svg=s;curved=c;}
    }
    /** Construct a candidate only for contours large enough to survive physical smoothing. */
    private static Ring curve(ArrayList<int[]> xy,double sx,double sy,double sigmaMm,double offset){
        ArrayList<P> original=new ArrayList<>(xy.size());
        for(int[] c:xy)original.add(new P(c[0]*sx,c[1]*sy));
        return curvePoints(original,sx,sy,sigmaMm,offset);
    }
    private static Ring curvePoints(ArrayList<P> original,double sx,double sy,double sigmaMm,double offset){
        int n=original.size();if(n<8||n>24000)return null;
        double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY;
        double minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
        for(P p:original){
            minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);
            minY=Math.min(minY,p.y);maxY=Math.max(maxY,p.y);
        }
        // Preserve thin lines, narrow eye cavities and small weedable islands.
        if(Math.min(maxX-minX,maxY-minY)<Math.max(1.50,3.6*sigmaMm))return null;
        double perimeter=0;
        double[] cumulative=new double[n+1];
        for(int i=0;i<n;i++){
            perimeter+=dist(original.get(i),original.get((i+1)%n));
            cumulative[i+1]=perimeter;
        }
        double step=Math.max(.02,Math.min(sx,sy)*.75);
        int count=(int)Math.ceil(perimeter/step);
        if(count<12||count>24000)return null;
        step=perimeter/count;
        P[] samples=new P[count];
        int edge=0;
        for(int k=0;k<count;k++){
            double along=k*step;
            while(edge+1<n && cumulative[edge+1]<along)edge++;
            double len=cumulative[edge+1]-cumulative[edge];
            samples[k]=mix(original.get(edge),original.get((edge+1)%n),len<=1e-9?0:(along-cumulative[edge])/len);
        }
        double sigma=Math.max(.001,sigmaMm);
        int reach=(int)Math.ceil(2.6*sigma/step);
        if(reach>=count/6)return null;
        double[] weight=new double[reach+1];double weightSum=0;
        for(int j=0;j<=reach;j++){
            weight[j]=Math.exp(-.5*Math.pow(j*step/sigma,2));
            weightSum+=j==0?weight[j]:2*weight[j];
        }
        P[] smooth=new P[count];
        for(int k=0;k<count;k++){
            if((k&1023)==0)check();
            double x=samples[k].x*weight[0],y=samples[k].y*weight[0];
            for(int j=1;j<=reach;j++){
                P a=samples[(k-j+count)%count],b=samples[(k+j)%count];
                x+=weight[j]*(a.x+b.x);y+=weight[j]*(a.y+b.y);
            }
            smooth[k]=new P(x/weightSum,y/weightSum);
        }
        // Closed-loop Douglas-Peucker in physical units. Split at two distant
        // points, otherwise a ring's coincident start/end defeats simplification.
        int halfway=count/2;
        boolean[] keep=new boolean[count];keep[0]=true;keep[halfway]=true;
        double tolerance=Math.min(.32,Math.max(Math.min(sx,sy)*.50,sigma*.54));
        rdp(smooth,0,halfway,tolerance*tolerance,keep,0);
        rdp(smooth,halfway,count,tolerance*tolerance,keep,0);
        ArrayList<P> anchors=new ArrayList<>();
        for(int k=0;k<count;k++)if(keep[k])anchors.add(smooth[k]);
        int m=anchors.size();if(m<6||m>12000)return null;
        StringBuilder svg=new StringBuilder(m*42);
        ArrayList<P> flat=new ArrayList<>(m*3);
        P start=anchors.get(0);svg.append('M');fixed(svg,start.x+offset);svg.append(' ');fixed(svg,start.y+offset);
        flat.add(start);
        for(int i=0;i<m;i++){
            if((i&1023)==0)check();
            P a=anchors.get((i-1+m)%m),b=anchors.get(i),c=anchors.get((i+1)%m),d=anchors.get((i+2)%m);
            // Catmull-Rom-to-cubic with modest tangent clamping; overshoot is
            // bounded to a fraction of local chord before raster validation.
            double chord=dist(b,c);
            if(chord<1e-9)continue;
            double control=Math.min(chord*.30, Math.min(dist(a,c),dist(b,d))*.125);
            double len1=Math.max(1e-9,dist(a,c)),len2=Math.max(1e-9,dist(b,d));
            P c1=new P(b.x+(c.x-a.x)/len1*control,b.y+(c.y-a.y)/len1*control);
            P c2=new P(c.x-(d.x-b.x)/len2*control,c.y-(d.y-b.y)/len2*control);
            svg.append('C');fixed(svg,c1.x+offset);svg.append(' ');fixed(svg,c1.y+offset);
            svg.append(' ');fixed(svg,c2.x+offset);svg.append(' ');fixed(svg,c2.y+offset);
            svg.append(' ');fixed(svg,c.x+offset);svg.append(' ');fixed(svg,c.y+offset);
            int steps=Math.max(2,Math.min(40,(int)Math.ceil(chord/(Math.min(sx,sy)*.32))));
            for(int t=1;t<=steps;t++){
                double u=t/(double)steps,v=1-u;
                double px=v*v*v*b.x+3*v*v*u*c1.x+3*v*u*u*c2.x+u*u*u*c.x;
                double py=v*v*v*b.y+3*v*v*u*c1.y+3*v*u*u*c2.y+u*u*u*c.y;
                flat.add(new P(px,py));
            }
        }
        svg.append('Z');
        return new Ring(flat,svg.toString(),true);
    }
    private static void rdp(P[] a,int lo,int hi,double threshold,boolean[] keep,int depth){
        if(hi-lo<2||depth>30)return;
        P from=a[lo%a.length],to=a[hi%a.length];
        double len2=Math.pow(to.x-from.x,2)+Math.pow(to.y-from.y,2);
        int far=-1;double max=threshold;
        for(int i=lo+1;i<hi;i++){
            P p=a[i%a.length];double dist2;
            if(len2<1e-12)dist2=Math.pow(p.x-from.x,2)+Math.pow(p.y-from.y,2);
            else{
                double u=Math.max(0,Math.min(1,((p.x-from.x)*(to.x-from.x)+(p.y-from.y)*(to.y-from.y))/len2));
                dist2=Math.pow(p.x-from.x-u*(to.x-from.x),2)+Math.pow(p.y-from.y-u*(to.y-from.y),2);
            }
            if(dist2>max){max=dist2;far=i;}
        }
        if(far>=0){keep[far%a.length]=true;rdp(a,lo,far,threshold,keep,depth+1);rdp(a,far,hi,threshold,keep,depth+1);}
    }
    private static void fixed(StringBuilder b,double n){
        long v=Math.round(n*1000);if(v<0){b.append('-');v=-v;}
        b.append(v/1000).append('.');long f=v%1000;
        if(f<100)b.append('0');if(f<10)b.append('0');b.append(f);
    }
    /** Subpixel candidate contours use one consistent winding/fill hierarchy.
     * Small islands remain as subpixel polylines; larger contours use guarded
     * cubic fitting, with a non-curved subpixel fallback rather than grid steps.
     */
    private static Ring isoRing(ArrayList<double[]> loop,double sx,double sy,double offset){
        ArrayList<P> points=new ArrayList<>(loop.size());
        for(double[] xy:loop)points.add(new P(xy[0]*sx,xy[1]*sy));
        return polyRing(points,offset);
    }
    private static Ring polyRing(ArrayList<P> points,double offset){
        StringBuilder b=new StringBuilder(points.size()*24);
        for(int i=0;i<points.size();i++){
            P p=points.get(i);b.append(i==0?'M':'L');
            fixed(b,p.x+offset);b.append(' ');fixed(b,p.y+offset);
        }
        b.append('Z');return new Ring(points,b.toString(),false);
    }
    private static boolean validCandidate(List<Ring> rings,boolean[] exact,int w,int h,
                                          double sx,double sy,int loopCount,int maxChanges){
        ArrayList<ArrayList<P>> loops=new ArrayList<>(rings.size());
        for(Ring r:rings)loops.add(r.flattened);
        boolean[] sampled=rasterize(loops,w,h,sx,sy);
        if(sampled==null)return false;
        int changes=0;
        for(int i=0;i<sampled.length;i++)if(sampled[i]!=exact[i]&&++changes>maxChanges)return false;
        return MultilayerStencilEngine.countUnionContours(sampled,w,h)==loopCount;
    }
    /**
     * Marching-squares at pixel centres, followed by physical-unit curves.
     * The topology and physical-error guard are checked against the original mask.
     * If a blurred field changes topology, retain the unblurred subpixel isoline.
     */
    private static Geometry subpixel(List<ArrayList<int[]>> boundaries,boolean[] exact,int w,int h,
                                     double mmW,double mmH,double offset,double requestedMm){
        final double sx=mmW/w,sy=mmH/h,cell=Math.min(sx,sy);
        final int loopCount=boundaries.size();
        // At coarse processing resolutions, a fixed 0.28 mm radius can be
        // smaller than one pixel: it cannot visually remove grid steps.
        // A subpixel Gaussian narrower than one raster cell cannot eliminate
        // the staircase seen at 600% zoom. Large contours can tolerate a
        // radius of roughly 1.4 cells; protect fragile contours individually.
        final double physicalSigma=Math.max(requestedMm,cell*1.45);
        final double sigmaPx=Math.min(1.35,Math.max(.85,physicalSigma/cell));
        final int allowed=(int)Math.min(exact.length*.035,
                      Math.max(8,Math.round(boundaryLength(boundaries)*.65)));
        List<ArrayList<double[]>> loops=null;
        try{
            List<ArrayList<double[]>> smooth=SubpixelStencilContours.trace(exact,w,h,sigmaPx);
            if(smooth.size()==loopCount){
                ArrayList<Ring> base=new ArrayList<>(smooth.size());
                for(ArrayList<double[]> v:smooth)base.add(isoRing(v,sx,sy,offset));
                if(validCandidate(base,exact,w,h,sx,sy,loopCount,allowed))loops=smooth;
            }
            if(loops==null)loops=SubpixelStencilContours.trace(exact,w,h,0);
        }catch(IllegalStateException bad){
            // Rare malformed saddle: let the old grid tracer handle this mask.
            return null;
        }
        if(loops.size()!=loopCount)return null;
        ArrayList<Ring> raw=new ArrayList<>(loops.size());
        ArrayList<Ring> candidates=new ArrayList<>(loops.size());
        ArrayList<Integer> eligible=new ArrayList<>();
        for(int i=0;i<loops.size();i++){
            check();
            Ring iso=isoRing(loops.get(i),sx,sy,offset);
            raw.add(iso);
            // Each loop is independently eligible for smoothing. Tiny eye
            // openings, isolated dots and thin necks remain subpixel polygons.
            Ring fit=curvePoints(iso.flattened,sx,sy,physicalSigma,offset);
            candidates.add(fit==null?iso:fit);
            if(fit!=null)eligible.add(i);
        }
        if(!validCandidate(raw,exact,w,h,sx,sy,loopCount,allowed))return null;
        if(eligible.isEmpty())return assembled(raw,exact,w,h,sx,sy);

        // Fast path: all fitted contours coexist safely. Unlike the old
        // all-or-nothing fallback, a failed global candidate enters local
        // contour validation, rather than resetting every face/cheek curve.
        if(validCandidate(candidates,exact,w,h,sx,sy,loopCount,allowed))
            return assembled(candidates,exact,w,h,sx,sy);

        ArrayList<Ring> selected=new ArrayList<>(raw);
        // Prioritise broad features: long eye/cheek/silhouette boundaries
        // should not be displaced by hundreds of incidental pixel flecks.
        final List<ArrayList<double[]>> sortedLoops=loops;
        eligible.sort((a,b)->Integer.compare(sortedLoops.get(b).size(),sortedLoops.get(a).size()));
        // Bounded checks: each validation scans the complete physical mask.
        // Prioritise the largest 48 candidates with bounded full-mask validations.
        int limit=Math.min(48,eligible.size());
        int[] budget={40};
        // Batch changes and split only rejected groups.  This substantially
        // reduces the number of complete scan conversions in typical portraits.
        acceptGroups(selected,candidates,eligible,0,limit,exact,w,h,sx,sy,loopCount,allowed,budget);
        return assembled(selected,exact,w,h,sx,sy);
    }
    /** Try several contours as one transaction; split an unsafe batch into
     * smaller independent transactions, down to a single protected contour.
     * The shared sampled mask must pass topology and displacement validation.
     */
    private static void acceptGroups(ArrayList<Ring> selected,List<Ring> candidates,
                                     List<Integer> eligible,int lo,int hi,boolean[] exact,
                                     int w,int h,double sx,double sy,int loops,int allowed,int[] budget){
        if(lo>=hi||budget[0]--<=0)return;
        check();
        ArrayList<Ring> prior=new ArrayList<>(hi-lo);
        for(int i=lo;i<hi;i++){
            int idx=eligible.get(i);
            prior.add(selected.get(idx));
            selected.set(idx,candidates.get(idx));
        }
        if(validCandidate(selected,exact,w,h,sx,sy,loops,allowed))return;
        for(int i=lo;i<hi;i++)selected.set(eligible.get(i),prior.get(i-lo));
        if(hi-lo==1)return; // this loop needs the conservative subpixel path
        int mid=(lo+hi)/2;
        acceptGroups(selected,candidates,eligible,lo,mid,exact,w,h,sx,sy,loops,allowed,budget);
        acceptGroups(selected,candidates,eligible,mid,hi,exact,w,h,sx,sy,loops,allowed,budget);
    }
    private static Geometry assembled(List<Ring> rings,boolean[] exact,int w,int h,double sx,double sy){
        ArrayList<ArrayList<P>> points=new ArrayList<>(rings.size());
        for(Ring r:rings)points.add(r.flattened);
        boolean[] sampled=rasterize(points,w,h,sx,sy);
        if(sampled==null)return null;
        // curved denotes subpixel output, even if safety left some contours
        // as linear segments.
        return join(rings,sampled,true);
    }
    /** Exact polygon for small/risky regions. */
    private static Ring original(ArrayList<int[]> corners,double sx,double sy,double offset){
        StringBuilder b=new StringBuilder(corners.size()*19);
        ArrayList<P> points=new ArrayList<>(corners.size());
        for(int i=0;i<corners.size();i++){
            P p=new P(corners.get(i)[0]*sx,corners.get(i)[1]*sy);points.add(p);
            b.append(i==0?'M':'L');fixed(b,p.x+offset);b.append(' ');fixed(b,p.y+offset);
        }
        b.append('Z');return new Ring(points,b.toString(),false);
    }
    private static final class Doubles {
        double[] a=new double[16];int size=0;
        void add(double x){if(size==a.length)a=Arrays.copyOf(a,a.length*2);a[size++]=x;}
    }
    /** Even-odd polygon scan converter at the original pixel-centre positions. */
    static boolean[] rasterize(List<ArrayList<P>> loops,int w,int h,double sx,double sy){
        Doubles[] hits=new Doubles[h];
        for(ArrayList<P> p:loops){
            int n=p.size();
            for(int i=0;i<n;i++){
                if((i&4095)==0)check();
                P a=p.get(i),b=p.get((i+1)%n);
                if(a.y==b.y)continue;
                double low=Math.min(a.y,b.y),high=Math.max(a.y,b.y);
                int y0=Math.max(0,(int)Math.ceil(low/sy-.5));
                int y1=Math.min(h,(int)Math.ceil(high/sy-.5));
                for(int y=y0;y<y1;y++){
                    if(hits[y]==null)hits[y]=new Doubles();
                    double yy=(y+.5)*sy;
                    hits[y].add(a.x+(yy-a.y)*(b.x-a.x)/(b.y-a.y));
                }
            }
        }
        boolean[] mask=new boolean[w*h];
        for(int y=0;y<h;y++){
            Doubles list=hits[y];if(list==null)continue;
            Arrays.sort(list.a,0,list.size);
            if((list.size&1)!=0)return null;
            for(int k=0;k<list.size;k+=2){
                int lo=Math.max(0,(int)Math.ceil(list.a[k]/sx-.5));
                int hi=Math.min(w,(int)Math.ceil(list.a[k+1]/sx-.5));
                for(int x=lo;x<hi;x++)mask[y*w+x]=true;
            }
        }
        return mask;
    }
    static Geometry build(List<ArrayList<int[]>> boundaries,boolean[] exact,int w,int h,
                          double mmW,double mmH,double offset,double requestedMm){
        // An already-clean four-sided cut needs no smoothing. Expanding its
        // straight edges into hundreds of isocontour points wastes SVG bytes.
        if(boundaries.size()==1 && boundaries.get(0).size()<=5){
            double sx0=mmW/w,sy0=mmH/h;
            ArrayList<Ring> straight=new ArrayList<>();
            straight.add(original(boundaries.get(0),sx0,sy0,offset));
            return join(straight,exact.clone(),false);
        }
        if(requestedMm>0 && boundaries.size()>0){
            Geometry improved=subpixel(boundaries,exact,w,h,mmW,mmH,offset,requestedMm);
            if(improved!=null)return improved;
        }
        double sx=mmW/w,sy=mmH/h;
        ArrayList<Ring> rings=new ArrayList<>(boundaries.size());
        boolean curved=false;
        // The physical curve radius is internal, not another UI slider.
        double radius=Math.max(0,Math.min(.48,requestedMm));
        for(ArrayList<int[]> loop:boundaries){
            check();
            Ring ring=radius>0?curve(loop,sx,sy,radius,offset):null;
            if(ring==null)ring=original(loop,sx,sy,offset);
            curved|=ring.curved;rings.add(ring);
        }
        if(!curved)return join(rings,exact.clone(),false);
        ArrayList<ArrayList<P>> polygons=new ArrayList<>(rings.size());
        for(Ring r:rings)polygons.add(r.flattened);
        boolean[] sampled=rasterize(polygons,w,h,sx,sy);
        int changes=0;
        if(sampled!=null){for(int i=0;i<exact.length;i++)if(sampled[i]!=exact[i])changes++;}
        // Safeguard all islands/holes and constrain the effect of smoothing.
        // Conservative global fallback keeps original topology if not proven safe.
        int maxChanges=Math.max(6,(int)Math.ceil(boundaryLength(boundaries)*Math.min(1.2,radius/Math.min(sx,sy))*.40));
        if(sampled==null||changes>maxChanges||
           MultilayerStencilEngine.countUnionContours(sampled,w,h)!=boundaries.size()){
            rings.clear();for(ArrayList<int[]> loop:boundaries)rings.add(original(loop,sx,sy,offset));
            return join(rings,exact.clone(),false);
        }
        return join(rings,sampled,true);
    }
    /** Translate the very same curve data for SVG board padding, without SVG transforms. */
    static void appendShifted(StringBuilder out,String d,double shift){
        int pos=0;
        while(pos<d.length()){
            char command=d.charAt(pos++);out.append(command);
            if(command=='Z')continue;
            int coords=command=='C'?6:command=='M'||command=='L'?2:0;
            if(coords==0)throw new IllegalArgumentException("Unexpected contour command: "+command);
            for(int i=0;i<coords;i++){
                if(i>0)out.append(' ');
                int from=pos;
                while(pos<d.length()&&"0123456789.+-".indexOf(d.charAt(pos))>=0)pos++;
                if(from==pos)throw new IllegalArgumentException("Missing curve coordinate");
                fixed(out,Double.parseDouble(d.substring(from,pos))+shift);
                while(pos<d.length()&&d.charAt(pos)==' ')pos++;
            }
        }
    }
    private static int boundaryLength(List<ArrayList<int[]>> contours){int length=0;for(ArrayList<int[]> l:contours)length+=l.size();return length;}
    private static Geometry join(List<Ring> rings,boolean[] raster,boolean curved){
        StringBuilder b=new StringBuilder();for(Ring r:rings)b.append(r.svg);
        return new Geometry(b.toString(),raster,curved,rings.size());
    }
}

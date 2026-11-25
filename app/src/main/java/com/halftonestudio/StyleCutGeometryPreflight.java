package com.halftonestudio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;

/** SVG-format compatibility is not physical vinyl safety. Operates on mm loops
 * that will actually be exported. Registration marks and open pen paths are
 * intentionally excluded from vinyl-cut measurements.
 */
public final class StyleCutGeometryPreflight {
    private StyleCutGeometryPreflight() {}
    public enum Category { INTERSECTION, OVERLAP, CONTACT, CLEARANCE, OTHER }
    public static final class Warning {
        public final Category category;
        public final int layer; public final float xMm,yMm;public final String reason;
        /** Centre of the offending clearance (midpoint of the closest pair). */
        final float midXMm,midYMm;
        Warning(int layer,float x,float y,String r){this(layer,x,y,r,x,y);}
        Warning(int layer,float x,float y,String r,float mx,float my){this.layer=layer;xMm=x;yMm=y;reason=r;category=r.startsWith("Intersecting")?Category.INTERSECTION:r.startsWith("Overlapping")?Category.OVERLAP:r.startsWith("Touching")?Category.CONTACT:r.startsWith("Local vinyl")?Category.CLEARANCE:Category.OTHER;midXMm=mx;midYMm=my;}
    }
    private static final class Segment {
        final float x,y,xx,yy;
        final int loop,index,count;
        int global;
        // Arc position is part of the *inspection* loop, not the exported path.
        double arcStart, loopPerimeter;
        Segment(float[] p,int k,int loop){
            int n=p.length/2;this.loop=loop;index=k;count=n;
            x=p[k*2];y=p[k*2+1];xx=p[((k+1)%n)*2];yy=p[((k+1)%n)*2+1];
        }
    }
    // Hash-map keys must distribute well after Long.hashCode() folds high and
    // low words together: raw ((long)x<<32)|y collapses diagonal buckets.
    // This bijective mixer changes hashing only, never coordinate equality.
    private static long key(int x,int y){
        long v=((long)x<<32)^(y&0xffffffffL);
        v=(v^(v>>>33))*0xff51afd7ed558ccdL;
        v=(v^(v>>>33))*0xc4ceb9fe1a85ec53L;
        return v^(v>>>33);
    }
    /** Primitive, insertion-ordered sparse grid for segment samples.
     * Avoid boxed Long keys and per-cell ArrayLists in repeated cut checks.
     * A slot's chain retains ascending segment-insertion order, preserving the
     * warning order and all geometric tests of the original implementation.
     */
    private static final class SegmentGrid {
        private long[] keys;
        private int[] first,last;
        private int buckets;
        private int[] entrySegment,entryNext;
        private int entries;
        SegmentGrid(){
            keys=new long[1024];first=new int[1024];last=new int[1024];
            java.util.Arrays.fill(first,-1);java.util.Arrays.fill(last,-1);
            entrySegment=new int[8192];entryNext=new int[8192];
        }
        private int slot(long key){
            int mask=keys.length-1;
            int index=(int)(key^(key>>>32))&mask;
            while(first[index]!=-1&&keys[index]!=key)index=(index+1)&mask;
            return index;
        }
        private void grow(){
            long[] oldKeys=keys;int[] oldFirst=first,oldLast=last;
            int n=oldKeys.length*2;
            keys=new long[n];first=new int[n];last=new int[n];
            java.util.Arrays.fill(first,-1);java.util.Arrays.fill(last,-1);
            for(int i=0;i<oldFirst.length;i++)if(oldFirst[i]!=-1){
                int j=slot(oldKeys[i]);keys[j]=oldKeys[i];
                first[j]=oldFirst[i];last[j]=oldLast[i];
            }
        }
        void add(long key,int segment){
            if(buckets*3>=keys.length*2)grow();
            int i=slot(key);
            if(first[i]==-1){keys[i]=key;buckets++;}
            else if(entrySegment[last[i]]==segment)return;
            if(entries==entrySegment.length){
                int n=entrySegment.length*2;
                entrySegment=java.util.Arrays.copyOf(entrySegment,n);
                entryNext=java.util.Arrays.copyOf(entryNext,n);
            }
            int entry=entries++;
            entrySegment[entry]=segment;entryNext[entry]=-1;
            if(first[i]==-1)first[i]=entry;
            else entryNext[last[i]]=entry;
            last[i]=entry;
        }
        int head(long key){int i=slot(key);return first[i];}
        int next(int entry){return entryNext[entry];}
        int segment(int entry){return entrySegment[entry];}
    }

    private static float pointDistance(float px,float py,Segment s){
        float dx=s.xx-s.x,dy=s.yy-s.y;
        float d=dx*dx+dy*dy;
        float t=d==0?0:Math.max(0,Math.min(1,((px-s.x)*dx+(py-s.y)*dy)/d));
        return (float)FastGeom.len(px-s.x-t*dx,py-s.y-t*dy);
    }
    // Input coordinates are float mm values; perform predicates in double to
    // avoid float overflow/cancellation when long cut paths meet. A predicate
    // is separate from physical clearance: even a point contact can make
    // vinyl layers weed incorrectly and must not pass as a positive gap.
    private static double orient(double ax,double ay,double bx,double by,double cx,double cy){
        return (bx-ax)*(cy-ay)-(by-ay)*(cx-ax);
    }
    private static int sign(double value,double scale){
        double tolerance=1e-12*Math.max(1.0,scale);
        return value>tolerance?1:value<-tolerance?-1:0;
    }
    private static boolean within(double value,double a,double b){
        return value>=Math.min(a,b)-1e-7&&value<=Math.max(a,b)+1e-7;
    }
    private static boolean onSegment(Segment s,double x,double y){
        double dx=s.xx-s.x,dy=s.yy-s.y;
        return sign(orient(s.x,s.y,s.xx,s.yy,x,y),FastGeom.len(dx,dy))==0
            &&within(x,s.x,s.xx)&&within(y,s.y,s.yy);
    }
    /** 0 disjoint, 1 proper crossing, 2 endpoint touch, 3 collinear overlap. */
    private static int contact(Segment a,Segment b){
        double al=FastGeom.len((double)a.xx-a.x,(double)a.yy-a.y);
        double bl=FastGeom.len((double)b.xx-b.x,(double)b.yy-b.y);
        double scale=al*bl;
        int a0=sign(orient(a.x,a.y,a.xx,a.yy,b.x,b.y),scale);
        int a1=sign(orient(a.x,a.y,a.xx,a.yy,b.xx,b.yy),scale);
        int b0=sign(orient(b.x,b.y,b.xx,b.yy,a.x,a.y),scale);
        int b1=sign(orient(b.x,b.y,b.xx,b.yy,a.xx,a.yy),scale);
        if(a0*a1<0&&b0*b1<0)return 1;
        if(a0==0&&a1==0&&b0==0&&b1==0){
            // Projection onto the longer axis avoids false contacts between
            // disjoint collinear intervals. A shared endpoint is a touch.
            boolean xAxis=Math.abs((double)a.xx-a.x)>=Math.abs((double)a.yy-a.y);
            double alo=Math.min(xAxis?a.x:a.y,xAxis?a.xx:a.yy);
            double ahi=Math.max(xAxis?a.x:a.y,xAxis?a.xx:a.yy);
            double blo=Math.min(xAxis?b.x:b.y,xAxis?b.xx:b.yy);
            double bhi=Math.max(xAxis?b.x:b.y,xAxis?b.xx:b.yy);
            double overlap=Math.min(ahi,bhi)-Math.max(alo,blo);
            if(overlap>1e-7)return 3;
            if(overlap>=-1e-7)return 2;
            return 0;
        }
        if((a0==0&&onSegment(a,b.x,b.y))||
           (a1==0&&onSegment(a,b.xx,b.yy))||
           (b0==0&&onSegment(b,a.x,a.y))||
           (b1==0&&onSegment(b,a.xx,a.yy)))return 2;
        return 0;
    }
    private static float separatedEdges(Segment a,Segment b){
        if(contact(a,b)!=0)return 0f;
        return Math.min(Math.min(pointDistance(a.x,a.y,b),pointDistance(a.xx,a.yy,b)),
            Math.min(pointDistance(b.x,b.y,a),pointDistance(b.xx,b.yy,a)));
    }
    private static boolean adjacent(Segment a,Segment b){
        if(a.loop!=b.loop)return false;
        int dist=Math.abs(a.index-b.index);
        // Only sides sharing a vertex are neighbors. In a four-sided ribbon,
        // the two opposed long sides are index distance 2; skipping them
        // concealed sub-minimum widths at any non-axis-aligned orientation.
        return Math.min(dist,a.count-dist)<=1;
    }
    /** Do not mistake two nearby samples of ONE continuous smooth boundary
     * for opposing sides of a narrow cut. For a densely sampled arc there may
     * be many non-adjacent edges less than the physical min width apart. We
     * suppress only same-loop, locally adjacent, similarly directed edges.
     * Actual segment contact is ALWAYS checked first. Opposed sides of a
     * thin ribbon have negative tangent dot and remain eligible for checks.
     */
    private static boolean sameBoundaryNeighborhood(Segment a,Segment b,float minimumMm){
        if(a.loop!=b.loop||a.loopPerimeter<=0)return false;
        double ax=(double)a.xx-a.x,ay=(double)a.yy-a.y;
        double bx=(double)b.xx-b.x,by=(double)b.yy-b.y;
        double al=FastGeom.len(ax,ay),bl=FastGeom.len(bx,by);
        if(al<1e-10||bl<1e-10)return false;
        double tangent=(ax*bx+ay*by)/(al*bl);
        if(tangent<0.0)return false;
        double am=a.arcStart+al/2,bm=b.arcStart+bl/2;
        double arc=Math.abs(am-bm);
        arc=Math.min(arc,a.loopPerimeter-arc);
        return arc<=2.0*minimumMm;
    }
    /** A short corner bevel joins two sides of the SAME material boundary.
     * A sub-minimum chamfer *edge length* is not a vinyl bridge width. Only
     * suppress a pair if their intervening contour ARC is shorter than the
     * minimum and the tangents do not reverse (a U-turn or opposite-facing
     * neck stays eligible). True crossings/contact are checked first.
     * No exported vertices are changed by this inspection-only exception.
     */
    private static boolean shortCornerConnector(Segment a,Segment b,float minimumMm){
        if(a.loop!=b.loop || a.loopPerimeter<=0 || a.count<4)return false;
        double ax=(double)a.xx-a.x,ay=(double)a.yy-a.y;
        double bx=(double)b.xx-b.x,by=(double)b.yy-b.y;
        double al=FastGeom.len(ax,ay),bl=FastGeom.len(bx,by);
        if(al<=1e-9||bl<=1e-9)return false;
        double tangent=(ax*bx+ay*by)/(al*bl);
        if(tangent < -0.05)return false; // opposed sides of a real thin neck
        double forwardA=(b.arcStart-(a.arcStart+al)+a.loopPerimeter)%a.loopPerimeter;
        double forwardB=(a.arcStart-(b.arcStart+bl)+a.loopPerimeter)%a.loopPerimeter;
        double connector=Math.min(forwardA,forwardB);
        // Exclude adjacent segments: those are handled by adjacent().
        return connector<=minimumMm+0.001 && connector>=0;
    }
    /** Minimum projected convex-hull width. An edge-normal orientation
     * attaining the minimum exists for every convex polygon. For small loops
     * this provides rotation-independent narrow-island detection, without
     * relying on an axis-aligned bounding rectangle. Concave necks are still
     * checked separately by segment-distance comparisons below.
     */
    private static float smallLoopWidth(float[] points){
        int n=points.length/2;
        if(n>16)return Float.POSITIVE_INFINITY; // bounded cost for dense contours
        double min=Double.POSITIVE_INFINITY;
        for(int i=0;i<n;i++){
            int next=(i+1)%n;
            double dx=(double)points[2*next]-points[2*i];
            double dy=(double)points[2*next+1]-points[2*i+1];
            double len=FastGeom.len(dx,dy);
            if(len<1e-10)continue;
            double nx=-dy/len, ny=dx/len;
            double lo=Double.POSITIVE_INFINITY, hi=Double.NEGATIVE_INFINITY;
            for(int j=0;j<n;j++){
                double projection=nx*points[2*j]+ny*points[2*j+1];
                lo=Math.min(lo,projection);hi=Math.max(hi,projection);
            }
            min=Math.min(min,hi-lo);
        }
        return Double.isFinite(min)?(float)min:0;
    }
    /** Remove redundant points for *inspection only*. Chained collinear
     * segments are a single physical boundary, not opposing edges of a neck.
     * Export geometry remains untouched. The tolerance is 0.00001 mm, far
     * below cut widths and the DXF 0.02 mm curve flattening tolerance.
     */
    private static float[] inspectionContour(float[] input){
        int n=input.length/2;
        if(n<=3)return input;
        float[] kept=new float[input.length];int m=0;
        for(int i=0;i<n;i++){
            int prev=(i+n-1)%n,next=(i+1)%n;
            double ax=input[2*prev],ay=input[2*prev+1],
                   bx=input[2*i],by=input[2*i+1],
                   cx=input[2*next],cy=input[2*next+1];
            double dx=cx-ax,dy=cy-ay,length=FastGeom.len(dx,dy);
            double t=length==0?0:((bx-ax)*dx+(by-ay)*dy)/(length*length);
            double deviation=length==0?FastGeom.len(bx-ax,by-ay):
                Math.abs(orient(ax,ay,cx,cy,bx,by))/length;
            if(length>1e-9&&t>=0&&t<=1&&deviation<=1e-5)continue;
            kept[2*m]=input[2*i];kept[2*m+1]=input[2*i+1];m++;
        }
        // Re-run until long runs of collinear points collapse; always retain
        // at least three vertices. Fixed maximum avoids adversarial loops.
        if(m<3||m==n)return m<3?input:kept;
        float[] reduced=java.util.Arrays.copyOf(kept,m*2);
        return reduced;
    }
    private static void add(ArrayList<Warning> warnings,int limit,int layer,float x,float y,String reason){
        if(warnings.size()<limit)warnings.add(new Warning(layer,x,y,reason));
    }
    /** Legacy strict mode: every same-sheet clearance (vinyl neck or weed gap)
     * must reach minimumMm. Retained unchanged for existing callers. */
    public static List<Warning> inspect(StyleKit.Result result,float minimumMm,int limit){
        return inspect(result,minimumMm,minimumMm,limit,false);
    }
    /** Physical vinyl/waste-aware mode used by export.
     * Each sub-minimum clearance is classified by the even-odd fill of the
     * sheet at the midpoint of the closest approach: kept vinyl must reach
     * minimumMm (the "Minimum cut width" setting) and weeded waste between or
     * inside pieces must reach gapMm (the "Minimum gap / bridge" setting).
     * A same-contour dead end (rounded ribbon cap, polygon tip, notch apex)
     * whose cut-off region is smaller than minimumMm^2 is not a neck: the
     * blade traces it as one corner. Contact/crossing is ALWAYS critical. */
    public static List<Warning> inspect(StyleKit.Result result,float minimumMm,float gapMm,int limit){
        if(!Float.isFinite(gapMm)||gapMm<=0)throw new IllegalArgumentException("Invalid minimum weed gap");
        return inspect(result,minimumMm,gapMm,limit,true);
    }
    /** Respect the user's explicit minimum weed gap independently of the
     * vinyl-width minimum. The 0.25 mm floor is the export lower bound, not
     * an upper bound. Larger requested clearances must never be silently
     * lowered to the minimum kept-vinyl width. */
    public static float exportGapMm(float bridgeMm,float minimumMm){
        float gap=Float.isFinite(bridgeMm)?bridgeMm:minimumMm;
        return Math.max(.25f,gap);
    }
    private static List<Warning> inspect(StyleKit.Result result,float minimumMm,float gapMm,int limit,boolean classified){
        if(result==null||limit<=0)return Collections.emptyList();
        if(!Float.isFinite(minimumMm)||minimumMm<=0)throw new IllegalArgumentException("Invalid minimum cut width");
        final float threshold=classified?Math.max(minimumMm,gapMm):minimumMm;
        ArrayList<Warning> warnings=new ArrayList<>();
        for(int li=0;li<result.layers.size()&&warnings.size()<limit;li++){
            java.util.ArrayList<Segment> segments=new java.util.ArrayList<>();
            java.util.ArrayList<float[]> tinyCandidates=new java.util.ArrayList<>();
            int path=0;
            for(float[] p:result.layers.get(li).loops){
                StyleKit.checkCancelled();
                if(p==null||p.length<6||(p.length&1)!=0)
                    throw new IllegalArgumentException("Invalid closed vinyl path in layer "+(li+1));
                float loX=Float.POSITIVE_INFINITY,loY=loX,hiX=Float.NEGATIVE_INFINITY,hiY=hiX;
                for(int k=0;k<p.length;k+=2){
                    if(!Float.isFinite(p[k])||!Float.isFinite(p[k+1]))
                        throw new IllegalArgumentException("Nonfinite vinyl path coordinate");
                    loX=Math.min(loX,p[k]);hiX=Math.max(hiX,p[k]);
                    loY=Math.min(loY,p[k+1]);hiY=Math.max(hiY,p[k+1]);
                }
                if(hiX-loX<.0001f||hiY-loY<.0001f||Math.abs(StyleKit.signedArea(p))<1e-7)
                    throw new IllegalArgumentException("Degenerate vinyl contour in layer "+(li+1));
                if(StyleKit.selfIntersections(p,1)>0)
                    throw new IllegalArgumentException("Self-intersecting vinyl contour in layer "+(li+1));
                float w=Math.min(Math.min(hiX-loX,hiY-loY),smallLoopWidth(p));
                float area=(float)Math.abs(StyleKit.signedArea(p));
                if(w+.003f<threshold||area<threshold*threshold*.3f)
                    tinyCandidates.add(new float[]{path,(loX+hiX)*.5f,(loY+hiY)*.5f,w,area});
                float[] inspection=inspectionContour(p);
                int first=segments.size();
                double arc=0;
                for(int k=0;k<inspection.length/2;k++){
                    Segment segment=new Segment(inspection,k,path);
                    segment.arcStart=arc;segment.global=segments.size();
                    arc+=FastGeom.len((double)segment.xx-segment.x,(double)segment.yy-segment.y);
                    segments.add(segment);
                }
                for(int k=first;k<segments.size();k++)segments.get(k).loopPerimeter=arc;
                path++;
            }
            // A local-width check must compare independent boundary edges.
            // Bounds and polygon area cannot detect a 0.1 mm bridge between
            // two broad lobes. Sample segments into a physical-mm spatial hash
            // and test exact segment distances for nearby nonadjacent edges.
            // Cross-contour comparisons also flag gaps in one physical sheet.
            float grid=Math.max(.15f,threshold);
            SegmentGrid buckets=new SegmentGrid();
            int visits=0;
            boolean truncated=false;
            final int sampleBudget=750000;
            for(int j=0;j<segments.size();j++){
                if((j&127)==0)StyleKit.checkCancelled();
                Segment sg=segments.get(j);
                int samples=Math.max(1,(int)Math.ceil(FastGeom.len(sg.xx-sg.x,sg.yy-sg.y)/(grid*.5f)));
                if(visits+(long)samples>sampleBudget){truncated=true;break;}
                for(int step=0;step<=samples;step++){
                    float t=step/(float)samples;
                    int gx=(int)Math.floor((sg.x+(sg.xx-sg.x)*t)/grid);
                    int gy=(int)Math.floor((sg.y+(sg.yy-sg.y)*t)/grid);
                    long bucket=key(gx,gy);
                    buckets.add(bucket,j);
                }
                visits+=samples+1;
            }
            if(truncated){
                add(warnings,limit,li,0,0,"Detailed local-width scan exceeded complexity budget; cut safety is not verified");
                continue;
            }
            final Fill fill=classified?new Fill(segments,grid):null;
            for(float[] tiny:tinyCandidates){
                // A tiny HOLE is a waste piece judged by the weed gap; a tiny
                // outer piece is kept vinyl judged by the minimum cut width.
                float need=minimumMm;
                if(classified&&fill.depthExcluding(segments,(int)tiny[0])%2==1)need=gapMm;
                if(tiny[3]+.003f<need||tiny[4]<need*need*.3f)
                    add(warnings,limit,li,tiny[1],tiny[2],"Check narrow or tiny vinyl contour");
            }
            int lastLoop=-1,reported=0;
            // A segment pair is only considered for one outer j. Stamping k
            // avoids allocating a boxed Long for each nearby pair.
            final int[] checkedBy=new int[segments.size()];
            java.util.Arrays.fill(checkedBy,-1);
            for(int j=0;j<segments.size()&&warnings.size()<limit;j++){
                if((j&127)==0)StyleKit.checkCancelled();
                Segment sg=segments.get(j);
                int samples=Math.max(1,(int)Math.ceil(FastGeom.len(sg.xx-sg.x,sg.yy-sg.y)/(grid*.5f)));
                boolean flagged=false;
                for(int step=0;step<=samples&&!flagged;step++){
                    float t=step/(float)samples;
                    float x=sg.x+(sg.xx-sg.x)*t,y=sg.y+(sg.yy-sg.y)*t;
                    int gx=(int)Math.floor(x/grid),gy=(int)Math.floor(y/grid);
                    for(int oy=-2;oy<=2&&!flagged;oy++)for(int ox=-2;ox<=2&&!flagged;ox++){
                        for(int entry=buckets.head(key(gx+ox,gy+oy));entry!=-1;entry=buckets.next(entry)){
                            int k=buckets.segment(entry);
                            if(k<=j)continue;
                            Segment other=segments.get(k);
                            if(adjacent(sg,other))continue;
                            if(checkedBy[k]==j)continue;
                            checkedBy[k]=j;
                            int touching=contact(sg,other);
                            // A real crossing/touch is never exempted by
                            // local contour-neighborhood simplification.
                            if(touching==0&&(sameBoundaryNeighborhood(sg,other,minimumMm)
                                    ||shortCornerConnector(sg,other,minimumMm)))continue;
                            float clearance=touching==0?separatedEdges(sg,other):0f;
                            if(touching==0&&clearance+.003f<threshold&&classified){
                                double[] near2=closestPoints(sg,other);
                                double mx=(near2[0]+near2[2])*.5,my=(near2[1]+near2[3])*.5;
                                boolean vinyl=fill.inside(segments,mx,my);
                                float need=vinyl?minimumMm:gapMm;
                                if(clearance+.003f>=need)continue;
                                if(sg.loop==other.loop&&deadEnd(segments,sg,other,near2,minimumMm))continue;
                            }
                            if(touching!=0||clearance+.003f<(classified?threshold:minimumMm)){
                                String reason=touching==1?"Intersecting cut contours":
                                    touching==3?"Overlapping cut edges":
                                    touching==2?"Touching cut contours":
                                    "Local vinyl neck or same-sheet gap below "+(classified?minimumMm+" / "+gapMm:minimumMm+"")+" mm";
                                float mx=x,my=y;
                                if(touching==0){double[] cp=closestPoints(sg,other);mx=(float)((cp[0]+cp[2])*.5);my=(float)((cp[1]+cp[3])*.5);}
                                if(warnings.size()<limit)warnings.add(new Warning(li,x,y,reason,mx,my));
                                flagged=true;reported++;break;
                            }
                        }
                    }
                }
                // Bound markers for an especially fragmented sheet, but do not
                // hide that the complete sheet needs physical cut review.
                if(reported>=limit)break;
            }
        }
        return Collections.unmodifiableList(warnings);
    }

    /** Closest points (ax,ay,bx,by) between two non-touching segments. */
    private static double[] closestPoints(Segment a,Segment b){
        double[][] c={
            proj(a.x,a.y,b,true),proj(a.xx,a.yy,b,true),proj(b.x,b.y,a,false),proj(b.xx,b.yy,a,false)};
        double[] best=c[0];
        for(double[] q:c)if(q[4]<best[4])best=q;
        return best;
    }
    private static double[] proj(double px,double py,Segment s,boolean pointOnA){
        double dx=s.xx-s.x,dy=s.yy-s.y,d=dx*dx+dy*dy;
        double t=d==0?0:Math.max(0,Math.min(1,((px-s.x)*dx+(py-s.y)*dy)/d));
        double qx=s.x+t*dx,qy=s.y+t*dy,dist=FastGeom.len(px-qx,py-qy);
        return pointOnA?new double[]{px,py,qx,qy,dist}:new double[]{qx,qy,px,py,dist};
    }
    /** Same-contour pair enclosing a small dead-end region with its chord. */
    private static boolean deadEnd(java.util.List<Segment> all,Segment a,Segment b,double[] near,float minimumMm){
        int n=a.count;
        int base=a.global-a.index;
        if(base<0)return false;
        double limit=minimumMm*(double)minimumMm;
        // Walk each way around from a to b; accept the smaller enclosed area.
        for(int dir=0;dir<2;dir++){
            Segment from=dir==0?a:b,to=dir==0?b:a;
            double fx=dir==0?near[0]:near[2],fy=dir==0?near[1]:near[3];
            double tx=dir==0?near[2]:near[0],ty=dir==0?near[3]:near[1];
            int steps=(to.index-from.index+n)%n;
            if(steps<1||steps>96)continue;
            double area=0,px=fx,py=fy;
            for(int k=1;k<=steps;k++){
                Segment s=all.get(base+(from.index+k)%n);
                area+=px*s.y-s.x*py;px=s.x;py=s.y;
            }
            area+=px*ty-tx*py;area+=tx*fy-fx*ty;
            if(Math.abs(area)*.5<limit)return true;
        }
        return false;
    }
    /** Even-odd fill of one sheet, by horizontal ray against row-bucketed edges. */
    private static final class Fill {
        final float grid;final HashMap<Integer,ArrayList<Integer>> rows=new HashMap<>();
        Fill(java.util.List<Segment> segments,float grid){
            this.grid=grid;
            for(int i=0;i<segments.size();i++){
                Segment s=segments.get(i);
                int a=(int)Math.floor(Math.min(s.y,s.yy)/grid),b=(int)Math.floor(Math.max(s.y,s.yy)/grid);
                for(int r=a;r<=b;r++)rows.computeIfAbsent(r,k->new ArrayList<>()).add(i);
            }
        }
        int crossings(java.util.List<Segment> segments,double x,double y,int skipLoop){
            ArrayList<Integer> row=rows.get((int)Math.floor(y/grid));
            if(row==null)return 0;
            int n=0;
            for(int i:row){
                Segment s=segments.get(i);
                if(s.loop==skipLoop)continue;
                if((s.y>y)!=(s.yy>y)){
                    double cx=s.x+(y-s.y)*(s.xx-s.x)/(s.yy-s.y);
                    if(cx>x)n++;
                }
            }
            return n;
        }
        boolean inside(java.util.List<Segment> segments,double x,double y){return (crossings(segments,x,y,-1)&1)==1;}
        int depthExcluding(java.util.List<Segment> segments,int loop){
            for(Segment s:segments)if(s.loop==loop)return crossings(segments,s.x,s.y+1e-4,loop);
            return 0;
        }
    }
}

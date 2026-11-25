package com.halftonestudio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/** Physical cut checks for the ORIGINAL standard vector Result, never a preview.
 * Circles are treated analytically rather than flattening thousands of dots
 * into artificial edges (which would introduce false width warnings).
 */
final class StandardCutSafety {
    private StandardCutSafety(){}
    static List<StyleCutGeometryPreflight.Warning> inspect(VinylEngine.Result artwork,
                                                            float minimumMm,int limit){
        return inspect(artwork,minimumMm,-1f,limit);
    }
    /** gapMm > 0 classifies clearances: weeded waste between separate shapes
     * is held to gapMm, kept vinyl to minimumMm. gapMm <= 0 is legacy strict. */
    static List<StyleCutGeometryPreflight.Warning> inspect(VinylEngine.Result artwork,
                                                            float minimumMm,float gapMm,int limit){
        if(artwork==null||limit<=0)return Collections.emptyList();
        final boolean classified=Float.isFinite(gapMm)&&gapMm>0;
        final float wasteGap=classified?gapMm:minimumMm;
        StyleKit.Result polygons=new StyleKit.Result(artwork.settings.widthMm,artwork.heightMm);
        ArrayList<StyleCutGeometryPreflight.Warning> extra=new ArrayList<>();
        for(int li=0;li<artwork.shapes.size();li++){
            StyleKit.Layer layer=new StyleKit.Layer("standard layer",0xff000000);
            polygons.layers.add(layer);
            if(!VinylEngine.cutLayer(artwork,li))continue;
            ArrayList<VinylEngine.Shape> circles=new ArrayList<>();
            for(VinylEngine.Shape sh:artwork.shapes.get(li)){
                if(sh.circle){
                    if(!Float.isFinite(sh.x)||!Float.isFinite(sh.y)||!Float.isFinite(sh.r)||sh.r<=0)
                        throw new IllegalArgumentException("Invalid circular cut in layer "+(li+1));
                    circles.add(sh);
                    if(sh.r*2f+.003f<minimumMm&&extra.size()<limit)
                        extra.add(new StyleCutGeometryPreflight.Warning(li,sh.x,sh.y,"Check narrow or tiny vinyl contour"));
                }else{
                    float[][] rings=sh.contours==null?new float[][]{sh.xy}:sh.contours;
                    for(float[] ring:rings)layer.loops.add(ring);
                }
            }
            // The typical Shape halftone has thousands of independent dots.
            // Examine circle contacts using a neighborhood grid, not O(N^2).
            float maxR=0;
            for(VinylEngine.Shape c:circles)maxR=Math.max(maxR,c.r);
            float grid=Math.max(Math.max(minimumMm,wasteGap),2*maxR+Math.max(minimumMm,wasteGap));
            if(!Float.isFinite(grid)||grid<=0)throw new IllegalArgumentException("Invalid circle grid");
            HashMap<Long,ArrayList<Integer>> cells=new HashMap<>();
            int checks=0;
            for(int i=0;i<circles.size();i++){
                if((i&127)==0)StyleKit.checkCancelled();
                VinylEngine.Shape c=circles.get(i);
                long gx=(long)Math.floor(c.x/grid),gy=(long)Math.floor(c.y/grid);
                if(gx<Integer.MIN_VALUE||gx>Integer.MAX_VALUE||gy<Integer.MIN_VALUE||gy>Integer.MAX_VALUE)
                    throw new IllegalArgumentException("Circle coordinate outside supported grid");
                for(long dx=-1;dx<=1;dx++)for(long dy=-1;dy<=1;dy++){
                    ArrayList<Integer> nearby=cells.get(key(gx+dx,gy+dy));
                    if(nearby==null)continue;
                    for(int j:nearby){
                        if(++checks>2_000_000){
                            extra.add(new StyleCutGeometryPreflight.Warning(li,c.x,c.y,
                                "Detailed circle contact scan exceeded complexity budget; cut safety is not verified"));
                            return combine(polygons,minimumMm,gapMm,limit,extra);
                        }
                        VinylEngine.Shape o=circles.get(j);
                        double centerDistance=Math.hypot((double)c.x-o.x,(double)c.y-o.y);
                        double large=Math.max(c.r,o.r),small=Math.min(c.r,o.r);
                        // Concentric/nested rings are not intersecting boundaries:
                        // their nearest physical cut clearance is the annular gap.
                        double gap=centerDistance+small<large ?
                            large-centerDistance-small : centerDistance-large-small;
                        boolean nested=centerDistance+small<large;
                        double need=nested?minimumMm:wasteGap;
                        if((gap<=.000001||gap+.003<need)&&extra.size()<limit){
                            extra.add(new StyleCutGeometryPreflight.Warning(li,c.x,c.y,
                                gap<=.000001?"Intersecting cut contours":"Local vinyl neck or same-sheet gap below "+need+" mm"));
                        }
                    }
                }
                cells.computeIfAbsent(key(gx,gy),v->new ArrayList<>()).add(i);
            }
            // Precise circle-to-polygon EDGE clearance is used at both the
            // source-level review and the final SVG writer. A contained circle
            // with sufficient annular gap is not an intersection; a tangent or
            // crossing is. The bounded scan reports UNVERIFIED on exhaustion.
            if(!circles.isEmpty()&&!layer.loops.isEmpty()&&extra.size()<limit){
                long compared=0;
                boolean incomplete=false;
                scan:for(VinylEngine.Shape c:circles){
                    for(float[] ring:layer.loops){
                        if(ring==null||ring.length<6||(ring.length&1)!=0){incomplete=true;break scan;}
                        for(int k=0;k<ring.length;k+=2){
                            if((++compared&8191)==0)StyleKit.checkCancelled();
                            if(compared>1_500_000L){incomplete=true;break scan;}
                            int j=(k+2)%ring.length;
                            double ax=ring[k],ay=ring[k+1],bx=ring[j],by=ring[j+1];
                            double dMin=distancePointSegment(c.x,c.y,ax,ay,bx,by);
                            double dMax=Math.max(Math.hypot(c.x-ax,c.y-ay),Math.hypot(c.x-bx,c.y-by));
                            double gap=dMin<=c.r&&dMax>=c.r?0:dMin>c.r?dMin-c.r:c.r-dMax;
                            if(gap<=.000001)extra.add(new StyleCutGeometryPreflight.Warning(li,c.x,c.y,
                                "Intersecting circle and polygon cut contours"));
                            else if(gap+.003<Math.max(minimumMm,wasteGap))extra.add(new StyleCutGeometryPreflight.Warning(li,c.x,c.y,
                                "Circle/polygon physical clearance below "+Math.max(minimumMm,wasteGap)+" mm (conservative width/gap threshold)"));
                            if(extra.size()>=limit){incomplete=true;break scan;}
                        }
                    }
                }
                if(incomplete&&extra.size()<limit)extra.add(new StyleCutGeometryPreflight.Warning(li,0,0,
                    "Detailed circle/polygon scan incomplete; cut safety is not verified"));
            }
        }
        // Sweep tests the actual ribbon polygons in the exported Result, not a
        // raster preview. A proper self crossing is a critical blade defect;
        // an exhausted diagnostic budget is explicitly UNVERIFIED.
        if(extra.size()<limit){
            LineCutDiagnostics.Report ribbons=LineCutDiagnostics.inspect(artwork);
            if(ribbons.selfIntersections>0)
                extra.add(new StyleCutGeometryPreflight.Warning(0,0,0,
                    "Intersecting line ribbon contours: "+ribbons.selfIntersections+
                    " confirmed edge crossings; change spacing, bend or width"));
            if(!ribbons.complete && extra.size()<limit)
                extra.add(new StyleCutGeometryPreflight.Warning(0,0,0,
                    "Line ribbon detailed intersection scan not verified (comparison budget)"));
        }
        return combine(polygons,minimumMm,gapMm,limit,extra);
    }
    private static double distancePointSegment(double x,double y,double ax,double ay,double bx,double by){
        double dx=bx-ax,dy=by-ay,length2=dx*dx+dy*dy;
        double t=length2==0?0:Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/length2));
        return Math.hypot(x-(ax+t*dx),y-(ay+t*dy));
    }
    private static long key(long x,long y){return (x<<32)^(y&0xffffffffL);}
    private static List<StyleCutGeometryPreflight.Warning> combine(StyleKit.Result polygons,float minimumMm,float gapMm,int limit,
                                               List<StyleCutGeometryPreflight.Warning> extra){
        ArrayList<StyleCutGeometryPreflight.Warning> out=new ArrayList<>();
        int room=Math.max(1,limit-extra.size());
        out.addAll(Float.isFinite(gapMm)&&gapMm>0?
            StyleCutGeometryPreflight.inspect(polygons,minimumMm,gapMm,room):
            StyleCutGeometryPreflight.inspect(polygons,minimumMm,room));
        for(StyleCutGeometryPreflight.Warning w:extra)if(out.size()<limit)out.add(w);
        return Collections.unmodifiableList(out);
    }
}

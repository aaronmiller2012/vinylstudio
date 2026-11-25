package com.halftonestudio;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Advisory, bounded safety scan of the FINAL, per-sheet SVG that is placed in
 * an SVG ZIP or converted to DXF. This is deliberately independent of style.
 * No inspection procedure is allowed to rewrite or merge actual cut paths.
 *
 * UNVERIFIED is a first-class result. Unsupported SVG, complexity exhaustion,
 * mixed primitives and drawing-only sheets must never be reported as SAFE.
 */
final class FinalCutSheetSafety {
    static final int MAX_WARNINGS=48;
    static final int MAX_CIRCLES=12000;
    static final int MAX_CIRCLE_COMPARISONS=1500000;
    static final class Report {
        final String sheet;
        final int contours,openPaths,circles,critical,narrow,unverified;
        final String detail;
        // -1 means the SVG could not be parsed as physical cutter geometry.
        final double widthMm,heightMm;
        final double[] rootFrame;
        final boolean outsideArtboard;
        Report(String file,int loops,int open,int circ,int hard,int thin,int unknown,String message,double width,double height,boolean outside,double[] root){
            sheet=file;contours=loops;openPaths=open;circles=circ;
            critical=hard;narrow=thin;unverified=unknown;detail=message;
            widthMm=width;heightMm=height;outsideArtboard=outside;rootFrame=root==null?null:root.clone();
        }
        boolean needsReview(){return critical>0||narrow>0||unverified>0;}
        String status(){return critical>0?"CONTACT_REVIEW":unverified>0?"UNVERIFIED":narrow>0?"WIDTH_REVIEW":openPaths>0&&contours==0&&circles==0?"DRAW_ONLY":"INSPECTED";}
        String csv(){
            return String.format(Locale.US,"%s,%s,%d,%d,%d,%d,%d,%d,%s\n",
                sheet.replace(',','_'),status(),contours,circles,openPaths,critical,narrow,unverified,
                detail.replace(',',';').replace('\n',' '));
        }
    }
    private FinalCutSheetSafety(){}
    /** Legacy strict inspection: weed gaps are held to the minimum cut width. */
    static Report inspect(String sheet,String svg,float minimumMm){
        return inspect(sheet,svg,minimumMm,minimumMm,false);
    }
    /** Export inspection: kept vinyl >= minimumMm, weeded waste >= gapMm.
     * A non-positive gap selects the legacy strict inspection. */
    static Report inspect(String sheet,String svg,float minimumMm,float gapMm){
        if(!(gapMm>0))return inspect(sheet,svg,minimumMm,minimumMm,false);
        return inspect(sheet,svg,minimumMm,gapMm,true);
    }
    private static Report inspect(String sheet,String svg,float minimumMm,float gapMm,boolean classified){
        if(!Float.isFinite(minimumMm)||minimumMm<=0)throw new IllegalArgumentException("Invalid physical cut width");
        if(!Float.isFinite(gapMm)||gapMm<=0)throw new IllegalArgumentException("Invalid physical weed gap");
        SvgDxfR13Exporter.SafetyGeometry geometry;
        try{geometry=SvgDxfR13Exporter.safetyGeometry(svg);}
        catch(IOException|IllegalArgumentException unsupported){
            return new Report(sheet,0,0,0,0,0,1,"Final SVG geometry could not be fully parsed for clearance inspection",-1,-1,false,null);
        }
        int nLoops=geometry.closedLoops.size(), nCircles=geometry.circles.size();
        int hard=0,narrow=0,unknown=0;
        StringBuilder details=new StringBuilder();
        // A cut extending outside the physical artboard can silently shift the
        // intended import size, even when all contours are mutually clear.
        // The 0.05mm allowance absorbs SVG roundoff, not a full pixel of drift.
        final double edgeToleranceMm=.05;
        boolean outside=false;
        for(float[] loop:geometry.closedLoops){
            for(int j=0;j<loop.length;j+=2){
                if(!insideArtboard(loop[j],loop[j+1],geometry.widthMm,geometry.heightMm,edgeToleranceMm)){
                    outside=true;break;
                }
            }
            if(outside)break;
        }
        // Open pen/engraving strokes share the same SVG artboard and transforms.
        // They are not blade cuts, but allowing an out-of-board drawing stroke
        // would make physical layer registration and DXF scaling unreliable.
        if(!outside)for(float[] stroke:geometry.openStrokes){
            for(int j=0;j<stroke.length;j+=2){
                if(!insideArtboard(stroke[j],stroke[j+1],geometry.widthMm,geometry.heightMm,edgeToleranceMm)){
                    outside=true;break;
                }
            }
            if(outside)break;
        }
        if(!outside)for(double[] circle:geometry.circles){
            double x=circle[0],y=circle[1],r=circle[2];
            if(!insideArtboard(x-r,y-r,geometry.widthMm,geometry.heightMm,edgeToleranceMm)||
               !insideArtboard(x+r,y+r,geometry.widthMm,geometry.heightMm,edgeToleranceMm)){
                outside=true;break;
            }
        }
        if(outside){hard++;details.append("Cut contour extends outside physical artboard | ");}
        // Always check the physical artboard even when the expensive proximity
        // scan is over budget. A dense export must never bypass bounds safety.
        if(nCircles>MAX_CIRCLES){
            details.append("Final sheet exceeds detailed circle inspection budget");
            return new Report(sheet,nLoops,geometry.openPaths,nCircles,hard,0,1,
                details.toString(),geometry.widthMm,geometry.heightMm,outside,geometry.rootFrame);
        }
        // Dense geometric halftones must not fail solely because they exceed
        // an arbitrary 3,500-loop threshold. Partition by spatial proximity
        // and run the EXISTING exact local-width/contact checks per tile.
        // If tiling cannot complete, report UNVERIFIED as before.
        boolean tiled=nLoops>3500||geometry.vertices>100000;
        if(tiled){
            DenseCutSheetSafety.Result dense=classified?
                DenseCutSheetSafety.inspect(geometry.closedLoops,minimumMm,gapMm,MAX_WARNINGS):
                DenseCutSheetSafety.inspect(geometry.closedLoops,minimumMm,MAX_WARNINGS);
            hard+=dense.critical;narrow+=dense.narrow;unknown+=dense.unverified;
            details.append("Spatial contour inspection ("+dense.tileCount+" tiles) | ");
            if(dense.unverified>0)details.append("Dense geometry inspection incomplete | ");
        }
        if(nLoops>0&&!tiled){
            StyleKit.Result doc=new StyleKit.Result((float)geometry.widthMm,(float)geometry.heightMm);
            StyleKit.Layer layer=new StyleKit.Layer("final sheet",0xff000000);
            layer.loops.addAll(geometry.closedLoops);doc.layers.add(layer);
            try{
                List<StyleCutGeometryPreflight.Warning> warnings=classified?
                    StyleCutGeometryPreflight.inspect(doc,minimumMm,gapMm,MAX_WARNINGS):
                    StyleCutGeometryPreflight.inspect(doc,minimumMm,MAX_WARNINGS);
                CutSafetyReviewPolicy.Decision decision=CutSafetyReviewPolicy.assess(warnings,MAX_WARNINGS);
                hard+=decision.critical;narrow+=decision.narrow;unknown+=decision.unverified;
                for(StyleCutGeometryPreflight.Warning w:warnings)
                    if(details.length()<320)details.append(w.reason).append(" | ");
            }catch(IllegalArgumentException suspicious){
                // A self-crossing or degenerate shape is NOT an all-clear.
                hard++;details.append("Invalid/self-crossing closed contour | ");
            }catch(java.util.concurrent.CancellationException cancelled){throw cancelled;}catch(RuntimeException budget){
                unknown++;details.append("Contour verification incomplete | ");
            }
        }
        // Analytical circle proximity (no raster tracing or N-gon approximation).
        if(nCircles>0){
            double maxRadius=0;
            for(double[] c:geometry.circles){
                if(c[2]<=0||!Double.isFinite(c[2])){hard++;continue;}
                maxRadius=Math.max(maxRadius,c[2]);
                if(c[2]*2+.003<minimumMm)narrow++;
            }
            double grid=Math.max(Math.max(minimumMm,gapMm),2*maxRadius+Math.max(minimumMm,gapMm));
            if(!(grid>0&&Double.isFinite(grid))){unknown++;}
            else {
                HashMap<Long,ArrayList<Integer>> buckets=new HashMap<>();
                long compared=0;
                outer: for(int i=0;i<nCircles;i++){
                    if((i&127)==0&&Thread.currentThread().isInterrupted()){unknown++;break;}
                    double[] c=geometry.circles.get(i);
                    long gx=(long)Math.floor(c[0]/grid),gy=(long)Math.floor(c[1]/grid);
                    for(long dx=-1;dx<=1;dx++)for(long dy=-1;dy<=1;dy++){
                        List<Integer> near=buckets.get(key(gx+dx,gy+dy));
                        if(near==null)continue;
                        for(int j:near){
                            if(++compared>MAX_CIRCLE_COMPARISONS){unknown++;break outer;}
                            double[] o=geometry.circles.get(j);
                            double distance=FastGeom.len(c[0]-o[0],c[1]-o[1]);
                            double largest=Math.max(c[2],o[2]),smallest=Math.min(c[2],o[2]);
                            // Two nested circular boundaries are separate cuts,
                            // not overlapping contours. A coincident or crossing
                            // pair still has zero physical clearance.
                            double gap=distance+smallest<largest?
                                largest-(distance+smallest):distance-largest-smallest;
                            boolean nested=distance+smallest<largest;
                            // Separate dots are divided by weeded waste; a
                            // ring between nested circles is kept vinyl.
                            double need=classified&&!nested?gapMm:minimumMm;
                            if(gap<=.000001){hard++;}
                            else if(gap+.003<need)narrow++;
                            if(hard+narrow>=MAX_WARNINGS){unknown++;break outer;}
                        }
                    }
                    buckets.computeIfAbsent(key(gx,gy),k->new ArrayList<>()).add(i);
                }
            }
        }
        if(nCircles>0&&nLoops>0){
            // Do not let mixed SVG primitives silently bypass contact checks.
            // The circle/segment test is exact for straight flattened edges,
            // including a segment wholly within a circle. Its bounded budget
            // deliberately yields UNVERIFIED, never an assumed all-clear.
            long comparisons=0;
            boolean exhausted=false;
            outer:for(double[] c:geometry.circles){
                if(Thread.currentThread().isInterrupted()){exhausted=true;break;}
                double cx=c[0],cy=c[1],radius=c[2];
                if(!(radius>0)||!Double.isFinite(radius)){hard++;continue;}
                for(float[] poly:geometry.closedLoops){
                    for(int j=0;j<poly.length;j+=2){
                        if(++comparisons>MAX_CIRCLE_COMPARISONS){exhausted=true;break outer;}
                        int k=(j+2)%poly.length;
                        double ax=poly[j],ay=poly[j+1],bx=poly[k],by=poly[k+1];
                        double dMin=distancePointSegment(cx,cy,ax,ay,bx,by);
                        double dMax=Math.max(FastGeom.len(cx-ax,cy-ay),FastGeom.len(cx-bx,cy-by));
                        double gap;
                        if(dMin<=radius && dMax>=radius){gap=0;} // crossing/tangent
                        else if(dMin>radius){gap=dMin-radius;} // entirely outside
                        else {gap=radius-dMax;} // segment completely inside circle
                        if(gap<=.000001){hard++;}
                        else if(gap+.003<Math.max(minimumMm,gapMm)){narrow++;}
                        if(hard+narrow>=MAX_WARNINGS){exhausted=true;break outer;}
                    }
                }
            }
            if(exhausted){unknown++;details.append("Circle-to-polygon inspection incomplete | ");}
            else if(hard>0||narrow>0)details.append("Mixed circle/polygon clearance findings (conservative width/gap threshold) | ");
        }
        if(geometry.openPaths>0&&(nLoops>0||nCircles>0)){
            unknown++;details.append("Mixed blade and open drawing paths | ");
        }
        // Open-only sheets are pen/engrave output, not vinyl cutting.
        if(geometry.openPaths>0&&nLoops==0&&nCircles==0)
            details.append("Open drawing paths: pen/engrave only");
        if(details.length()==0&&hard+narrow+unknown==0)details.append("No findings within bounded inspection");
        return new Report(sheet,nLoops,geometry.openPaths,nCircles,hard,narrow,unknown,details.toString(),geometry.widthMm,geometry.heightMm,outside,geometry.rootFrame);
    }
    private static boolean insideArtboard(double x,double y,double width,double height,double tolerance){
        return Double.isFinite(x)&&Double.isFinite(y)&&x>=-tolerance&&y>=-tolerance&&
            x<=width+tolerance&&y<=height+tolerance;
    }
    private static double distancePointSegment(double x,double y,double ax,double ay,double bx,double by){
        double dx=bx-ax,dy=by-ay,den=dx*dx+dy*dy;
        double t=den==0?0:Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/den));
        return FastGeom.len(x-(ax+t*dx),y-(ay+t*dy));
    }
    private static long key(long x,long y){return (x<<32)^(y&0xffffffffL);}
}

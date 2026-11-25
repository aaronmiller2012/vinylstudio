package com.halftonestudio;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.algorithm.construct.MaximumInscribedCircle;
import org.locationtech.jts.operation.distance.IndexedFacetDistance;

/** Advisory footprint checks, never repairs. Bounded independently of contour statistics. */
final class FootprintClearance {
    private FootprintClearance() {}
    static String inspect(Geometry g,double width,double gap,Runnable guard){
        if(g.isEmpty())return "No filled footprint to inspect.\n";
        if(g.getNumPoints()>16000||g.getNumGeometries()>256)return "Local footprint checks not evaluated (complexity limit).\n";
        int narrow=0,necks=0,pairs=0,examined=0,ringPairs=0,localPairs=0;
        StringBuilder locations=new StringBuilder();
        long start=System.nanoTime();
        STRtree index=new STRtree();
        for(int i=0;i<g.getNumGeometries();i++)index.insert(g.getGeometryN(i).getEnvelopeInternal(),i);
        index.build();
        for(int i=0;i<g.getNumGeometries();i++){
            guard.run(); CancellationCheckpoint.check();
            if(System.nanoTime()-start>750_000_000L)return "Local footprint checks incomplete (time budget); no complete result.\n";
            Geometry p=g.getGeometryN(i);
            if(width>0){
                double tolerance=Math.max(.002,width/100);
                double radius=new MaximumInscribedCircle(p,tolerance).getRadiusLine().getLength();
                if(2*(radius+tolerance)<width){narrow++; location(locations,"Narrow component",p.getInteriorPoint().getCoordinate());}
                else {Geometry eroded=p.buffer(-width/2,8); if(!eroded.isEmpty()&&eroded.getNumGeometries()>1){necks++; location(locations,"Split component reference",p.getInteriorPoint().getCoordinate());}}
            }
            if(width>0 && p instanceof Polygon){
                Polygon poly=(Polygon)p;
                for(int ring=-1;ring<poly.getNumInteriorRing();ring++){
                    Coordinate[] cs=(ring<0?poly.getExteriorRing():poly.getInteriorRingN(ring)).getCoordinates();
                    STRtree segments=new STRtree();
                    for(int k=0;k<cs.length-1;k++)segments.insert(new Envelope(cs[k],cs[k+1]),k);
                    segments.build();
                    for(int k=0;k<cs.length-1;k++){
                        guard.run(); CancellationCheckpoint.check();
                        if(System.nanoTime()-start>750_000_000L)return "Local footprint checks incomplete (local-width time budget); no complete result.\n";
                        Envelope e=new Envelope(cs[k],cs[k+1]);e.expandBy(width);
                        for(Object item:segments.query(e)){
                            int j=(Integer)item;
                            if(j<=k+1 || (k==0&&j==cs.length-2))continue;
                            if(++examined>4096||System.nanoTime()-start>750_000_000L)return "Local footprint checks incomplete (local-width pair/time budget); no complete result.\n";
                            double[] v=ParallelWidthCandidates.between(cs[k].x,cs[k].y,cs[k+1].x,cs[k+1].y,cs[j].x,cs[j].y,cs[j+1].x,cs[j+1].y,width);
                            if(v!=null && poly.covers(poly.getFactory().createLineString(new Coordinate[]{new Coordinate(v[0],v[1]),new Coordinate(v[2],v[3])}))){
                                localPairs++;location(locations,"Opposing boundary width candidate",new Coordinate((v[0]+v[2])/2,(v[1]+v[3])/2));
                            }
                        }
                    }
                }
            }
            if(width>0){
                if(p instanceof Polygon){
                    Polygon poly=(Polygon)p;
                    int rings=poly.getNumInteriorRing()+1;
                    for(int a=0;a<rings;a++)for(int b=a+1;b<rings;b++){
                        guard.run(); CancellationCheckpoint.check();
                        if(++examined>4096||System.nanoTime()-start>750_000_000L)return "Local footprint checks incomplete (ring/time budget); no complete result.\n";
                        Geometry first=a==0?poly.getExteriorRing():poly.getInteriorRingN(a-1);
                        Geometry second=poly.getInteriorRingN(b-1);
                        if(first.getEnvelopeInternal().distance(second.getEnvelopeInternal())>=width)continue;
                        IndexedFacetDistance d=new IndexedFacetDistance(first);
                        if(d.distance(second)+.003<width){
                            Coordinate[] nearest=d.nearestPoints(second);
                            if(poly.covers(poly.getFactory().createLineString(nearest))){
                                ringPairs++;
                                location(locations,"Distinct boundary rings (thin vinyl)",new Coordinate((nearest[0].x+nearest[1].x)/2,(nearest[0].y+nearest[1].y)/2));
                            }
                        }
                    }
                }
            }
            if(gap>0){
                Envelope env=new Envelope(p.getEnvelopeInternal());env.expandBy(gap);
                for(Object item:index.query(env)){int j=(Integer)item;if(j<=i)continue;
                    guard.run(); CancellationCheckpoint.check();
                    if(++examined>4096||System.nanoTime()-start>750_000_000L)return "Local footprint checks incomplete (pair/time budget); no complete result.\n";
                    IndexedFacetDistance d=new IndexedFacetDistance(p);
                    if(d.distance(g.getGeometryN(j))+.003<gap){pairs++; location(locations,"Separate footprint gap",d.nearestPoints(g.getGeometryN(j))[0]);}
                }
            }
        }
        return (width>0?"Components with insufficient inscribed diameter: "+narrow+"\nPotential narrow connections (inward offset splits): "+necks+"\nOpposing same-ring width candidates: "+localPairs+"\nDistinct boundary ring pairs below width (thin vinyl): "+ringPairs+"\n":"Width checks disabled.\n")
            +(gap>0?"Separate footprint pairs below gap: "+pairs+"\n":"Gap checks disabled.\n")
            +locations.toString()+"Approximate footprint checks; not minimum local thickness, authored-path clearance or physical certification.\n";
    }
    private static void location(StringBuilder out,String type,Coordinate c){
        if(c!=null&&out.length()<1200)out.append(String.format(java.util.Locale.US,"%s reference: x %.2f, y %.2f mm\n",type,c.x,c.y));
    }
}

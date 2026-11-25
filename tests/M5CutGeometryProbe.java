package com.halftonestudio;

import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;
import org.locationtech.jts.operation.union.UnaryUnionOp;

/**
 * M5: measures the FINISHED vinyl of one sheet (even-odd loops in mm) with exact
 * vector morphology, independent of how the engine drew it:
 *  - narrow vinyl: material removed by an opening of radius min/2 (necks, slivers,
 *    undersized posts), reported as area and as count of residue pieces;
 *  - vanished islands: pieces that contain no disk of diameter min;
 *  - necks: extra components created by eroding by min/2 (a piece held together
 *    only through a sub-minimum bridge);
 *  - narrow waste: waste filled by a closing of radius gap/2 (sub-gap slits/holes);
 *  - holes: interior rings, with how many have area below {@code holeMinArea} or
 *    contain no disk of diameter gap.
 * Small fixed tolerances (0.01 mm) absorb float rounding of exactly-minimum features.
 */
final class M5CutGeometryProbe {
    private M5CutGeometryProbe(){}
    static final GeometryFactory F=new GeometryFactory();
    static final class Stats {
        int pieces,holes,tinyHoles,subGapHoles,vanished,necks,narrowPieces,narrowWastePieces;
        double area,narrowArea,narrowWasteArea,minHoleArea=Double.POSITIVE_INFINITY;
        void add(Stats s){pieces+=s.pieces;holes+=s.holes;tinyHoles+=s.tinyHoles;subGapHoles+=s.subGapHoles;
            vanished+=s.vanished;necks+=s.necks;narrowPieces+=s.narrowPieces;narrowWastePieces+=s.narrowWastePieces;
            area+=s.area;narrowArea+=s.narrowArea;narrowWasteArea+=s.narrowWasteArea;minHoleArea=Math.min(minHoleArea,s.minHoleArea);}
        static String header(){return "pieces\tholes\ttiny_holes\tsubgap_holes\tvanished\tnecks\tnarrow_pcs\tnarrow_mm2\tnarrow_waste_pcs\tnarrow_waste_mm2\tmin_hole_mm2\tarea_mm2";}
        @Override public String toString(){return String.format(java.util.Locale.US,"%d\t%d\t%d\t%d\t%d\t%d\t%d\t%.2f\t%d\t%.2f\t%.2f\t%.0f",
            pieces,holes,tinyHoles,subGapHoles,vanished,necks,narrowPieces,narrowArea,narrowWastePieces,narrowWasteArea,
            Double.isInfinite(minHoleArea)?-1:minHoleArea,area);}
    }
    static Polygon ring(float[] xy){
        int n=xy.length/2;Coordinate[] c=new Coordinate[n+1];
        for(int i=0;i<n;i++)c[i]=new Coordinate(xy[2*i],xy[2*i+1]);
        c[n]=c[0];
        Polygon p=F.createPolygon(c);
        return p.isValid()?p:(Polygon)largest(p.buffer(0));
    }
    static Geometry largest(Geometry g){
        Geometry best=F.createPolygon();
        for(int i=0;i<g.getNumGeometries();i++)if(g.getGeometryN(i).getArea()>best.getArea())best=g.getGeometryN(i);
        return best;
    }
    /** Even-odd region of non-crossing nested loops, built depth by depth. */
    static Geometry region(List<float[]> loops){
        int n=loops.size();
        float[][] bb=new float[n][];
        for(int i=0;i<n;i++)bb[i]=M4ArtBench.bounds(loops.get(i));
        int max=0;int[] depth=new int[n];
        for(int i=0;i<n;i++){
            float[] a=loops.get(i);if(a.length<6){depth[i]=-1;continue;}
            float px=a[0],py=a[1];int d=0;
            for(int j=0;j<n;j++){
                if(j==i)continue;float[] b=bb[j];
                if(px<b[0]||px>b[2]||py<b[1]||py>b[3])continue;
                if(b[2]-b[0]<bb[i][2]-bb[i][0])continue;
                if(M4ArtBench.inside(loops.get(j),px,py))d++;
            }
            depth[i]=d;max=Math.max(max,d);
        }
        Geometry out=F.createPolygon();
        for(int d=0;d<=max;d++){
            ArrayList<Geometry> level=new ArrayList<>();
            for(int i=0;i<n;i++)if(depth[i]==d)level.add(ring(loops.get(i)));
            if(level.isEmpty())continue;
            Geometry u=UnaryUnionOp.union(level);
            out=OverlayNGRobust.overlay(out,u,(d&1)==0?OverlayNG.UNION:OverlayNG.DIFFERENCE);
        }
        return out;
    }
    static Geometry buf(Geometry g,double r){
        BufferParameters p=new BufferParameters(8,BufferParameters.CAP_ROUND,BufferParameters.JOIN_ROUND,5);
        return org.locationtech.jts.operation.buffer.BufferOp.bufferOp(g,r,p);
    }
    static int pieces(Geometry g,double minArea){
        int n=0;for(int i=0;i<g.getNumGeometries();i++)if(g.getGeometryN(i).getArea()>minArea)n++;return n;
    }
    static Stats measure(List<float[]> loops,float minMm,float gapMm,float holeMinArea){
        Stats s=new Stats();
        if(loops.isEmpty())return s;
        Geometry g=region(loops);
        s.area=g.getArea();
        double rv=minMm*.5-.01,rg=gapMm*.5-.01;
        Geometry eroded=buf(g,-rv);
        Geometry opened=buf(eroded,rv);
        Geometry narrow=OverlayNGRobust.overlay(g,opened,OverlayNG.DIFFERENCE);
        // An opening always trims convex corners: a 90 degree corner leaves a residue only
        // 0.17 r thick (< 0.07 mm here). Count residue that is a real strand: thicker
        // than 0.08 mm (i.e. it contains a 0.16 mm disk).
        for(int i=0;i<narrow.getNumGeometries();i++){Geometry n=narrow.getGeometryN(i);
            if(!buf(n,-.08).isEmpty()){s.narrowPieces++;s.narrowArea+=n.getArea();}}
        s.pieces=g.getNumGeometries();
        // vanished islands and neck splits
        org.locationtech.jts.index.strtree.STRtree idx=new org.locationtech.jts.index.strtree.STRtree();
        for(int i=0;i<eroded.getNumGeometries();i++){Geometry e=eroded.getGeometryN(i);if(!e.isEmpty())idx.insert(e.getEnvelopeInternal(),e);}
        for(int i=0;i<g.getNumGeometries();i++){
            Polygon p=(Polygon)g.getGeometryN(i);
            int cores=0;
            for(Object o:idx.query(p.getEnvelopeInternal())){Geometry e=(Geometry)o;
                if(p.contains(e.getInteriorPoint()))cores++;}
            if(cores==0)s.vanished++;else s.necks+=cores-1;
            for(int k=0;k<p.getNumInteriorRing();k++){
                LinearRing h=p.getInteriorRingN(k);Polygon hp=F.createPolygon(h);
                s.holes++;double a=hp.getArea();s.minHoleArea=Math.min(s.minHoleArea,a);
                if(a<holeMinArea)s.tinyHoles++;
                if(buf(hp,-rg).isEmpty())s.subGapHoles++;
            }
        }
        Geometry closed=buf(buf(g,rg),-rg);
        Geometry waste=OverlayNGRobust.overlay(closed,g,OverlayNG.DIFFERENCE);
        // Closing fills concave corners (residue < 0.05 mm thick); count real sub-gap slits.
        for(int i=0;i<waste.getNumGeometries();i++){Geometry n=waste.getGeometryN(i);
            if(!buf(n,-.05).isEmpty()){s.narrowWastePieces++;s.narrowWasteArea+=n.getArea();}}
        return s;
    }
}

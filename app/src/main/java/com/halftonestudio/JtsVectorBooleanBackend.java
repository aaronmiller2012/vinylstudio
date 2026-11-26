package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.UnaryUnionNG;

/** Fixed-precision snap-rounded vector operations. The identical pure-Java
 * backend is used by Android and the mandatory NAS regression, rather than
 * comparing unrelated platform-specific floating point implementations. */
final class JtsVectorBooleanBackend implements VectorBooleanEngine.Backend {
    // Binary 1/1024 mm grid is exactly representable by float across <2000mm.
    private static final PrecisionModel PM=new PrecisionModel(1024.0);
    private static final GeometryFactory FACTORY=new GeometryFactory(PM);
    private static final Geometry EMPTY=FACTORY.createPolygon();
    /** Cut conditioning runs in floating precision and is snapped to PM once at the end:
     *  fixed-precision snap rounding on every intermediate buffer/overlay dominated the
     *  allocation of final Prismatic renders (Braided Flow 11 in: 9.7 -> 5.5 GB allocated,
     *  7.4 -> 5.3 s on the host). */
    private static final GeometryFactory FLOAT_FACTORY=new GeometryFactory(new PrecisionModel());
    private static LinearRing ring(float[] xy){
        ArrayList<Coordinate> points=new ArrayList<>(xy.length/2+1);
        for(int i=0;i<xy.length;i+=2){
            CancellationCheckpoint.check();
            Coordinate p=new Coordinate(xy[i],xy[i+1]);PM.makePrecise(p);
            if(points.isEmpty()||!p.equals2D(points.get(points.size()-1)))points.add(p);
        }
        if(points.size()>1&&points.get(0).equals2D(points.get(points.size()-1)))points.remove(points.size()-1);
        if(points.size()<3)return FACTORY.createLinearRing();
        points.add(points.get(0).copy());return FACTORY.createLinearRing(points.toArray(new Coordinate[0]));
    }
    @Override public Object path(float[][] loops){
        LinearRing shell=ring(loops[0]);if(shell.isEmpty())return EMPTY;
        ArrayList<LinearRing> holes=new ArrayList<>(Math.max(0,loops.length-1));
        for(int i=1;i<loops.length;i++){LinearRing hole=ring(loops[i]);if(!hole.isEmpty())holes.add(hole);}
        Polygon p=FACTORY.createPolygon(shell,holes.toArray(new LinearRing[0]));
        return p.isValid()?p:polygonal(GeometryFixer.fix(p));
    }
    @Override public Object clipPath(float[][] contours){
        Geometry[] g=new Geometry[contours.length];
        for(int i=0;i<g.length;i++)g[i]=(Geometry)path(new float[][]{contours[i]});
        return xor(g,0,g.length);
    }
    private Geometry xor(Geometry[] g,int a,int z){
        CancellationCheckpoint.check();if(z==a)return EMPTY;if(z-a==1)return g[a];
        Arrays.sort(g,a,z,Comparator.comparingDouble(p->p.getEnvelopeInternal().getMinX()));
        int mid=a+(z-a)/2;Geometry l=xor(g,a,mid),r=xor(g,mid,z);
        if(l.isEmpty())return r;if(r.isEmpty())return l;
        // Both inputs have already been snapped onto the same grid.
        if(!l.getEnvelopeInternal().intersects(r.getEnvelopeInternal()))return append(l,r);
        return polygonal(OverlayNG.overlay(l,r,OverlayNG.SYMDIFFERENCE,PM));
    }
    @Override public Object batch(VectorBooleanEngine.Motif[] motifs,int lo,int hi){
        ArrayList<Geometry> input=new ArrayList<>(hi-lo);
        for(int i=lo;i<hi;i++){
            CancellationCheckpoint.check();Geometry p=(Geometry)path(motifs[i].loops);
            if(!p.isEmpty())input.add(p);
        }
        return input.isEmpty()?EMPTY:polygonal(UnaryUnionNG.union(input,FACTORY,PM));
    }
    @Override public Object combine(Object a,Object b,boolean disjoint){
        CancellationCheckpoint.check();Geometry l=(Geometry)a,r=(Geometry)b;
        if(l.isEmpty())return r;if(r.isEmpty())return l;
        return disjoint?append(l,r):polygonal(OverlayNG.overlay(l,r,OverlayNG.UNION,PM));
    }
    @Override public Object intersect(Object a,Object b){
        CancellationCheckpoint.check();Geometry l=(Geometry)a,r=(Geometry)b;
        if(l.isEmpty()||r.isEmpty())return EMPTY;
        Geometry result=polygonal(OverlayNG.overlay(l,r,OverlayNG.INTERSECTION,PM));
        if(!result.isValid())throw new IllegalStateException("Invalid vector Boolean sheet; reduce Detail");
        return result;
    }
    /** Eliminate microscopic standalone cut work before clipping the Snip.
     * Filling a tiny hole can absorb a nested island; union again only when
     * something was actually changed so overlapping contours never escape. */
    @Override public Object removeMicroFragments(Object shape,float minVinylArea,float minWasteArea){
        Geometry g=(Geometry)shape;ArrayList<Polygon> polys=new ArrayList<>();collect(g,polys);
        ArrayList<Geometry> kept=new ArrayList<>(polys.size());boolean changed=false;
        for(Polygon p:polys){
            CancellationCheckpoint.check();
            if(p.getArea()<minVinylArea){changed=true;continue;}
            ArrayList<LinearRing> holes=new ArrayList<>(p.getNumInteriorRing());boolean removed=false;
            for(int i=0;i<p.getNumInteriorRing();i++){
                LinearRing h=p.getInteriorRingN(i);
                if(FACTORY.createPolygon(h).getArea()<minWasteArea){removed=true;changed=true;}else holes.add(h);
            }
            kept.add(removed?FACTORY.createPolygon(p.getExteriorRing(),holes.toArray(new LinearRing[0])):p);
        }
        if(!changed)return g;if(kept.isEmpty())return EMPTY;
        return polygonal(UnaryUnionNG.union(kept,FACTORY,PM));
    }
    /** Conditioned-path variant of {@link #removeMicroFragments}: the same polygons are dropped
     * and the same holes filled, but the whole sheet is never re-unioned. The input is a valid
     * polygonal Boolean result, so its polygons are interior-disjoint; filling a hole of P can
     * only swallow whole polygons that lie inside that hole (union(P_filled, island)==P_filled),
     * so those are dropped by an STRtree-assisted cover test and every other polygon is passed
     * through as the same immutable object. Hole areas are measured on the ring (no temporary
     * polygons). Geometry is equal to the shared implementation; vertex/ring order may differ,
     * which is why the pinned (unconditioned) path keeps the original. */
    @Override public Object removeMicroFragmentsLocal(Object shape,float minVinylArea,float minWasteArea){
        Geometry g=(Geometry)shape;ArrayList<Polygon> polys=new ArrayList<>();collect(g,polys);
        int n=polys.size();Polygon[] out=new Polygon[n];boolean changed=false;
        ArrayList<Polygon> filledHoles=null;ArrayList<Integer> filledOwner=null;
        for(int k=0;k<n;k++){
            Polygon p=polys.get(k);CancellationCheckpoint.check();
            if(p.getArea()<minVinylArea){changed=true;continue;}
            int holes=p.getNumInteriorRing();ArrayList<LinearRing> keep=null;
            for(int i=0;i<holes;i++){
                LinearRing h=p.getInteriorRingN(i);
                if(Math.abs(org.locationtech.jts.algorithm.Area.ofRing(h.getCoordinateSequence()))<minWasteArea){
                    if(keep==null){keep=new ArrayList<>(holes);for(int j=0;j<i;j++)keep.add(p.getInteriorRingN(j));}
                    if(filledHoles==null){filledHoles=new ArrayList<>();filledOwner=new ArrayList<>();}
                    filledHoles.add(p.getFactory().createPolygon(h));filledOwner.add(k);changed=true;
                }else if(keep!=null)keep.add(h);
            }
            out[k]=keep==null?p:p.getFactory().createPolygon(p.getExteriorRing(),keep.toArray(new LinearRing[0]));
        }
        if(!changed)return g;
        if(filledHoles!=null){
            org.locationtech.jts.index.strtree.STRtree index=new org.locationtech.jts.index.strtree.STRtree();
            for(int k=0;k<n;k++)if(out[k]!=null)index.insert(polys.get(k).getEnvelopeInternal(),k);
            for(int f=0;f<filledHoles.size();f++){
                Polygon hole=filledHoles.get(f);int owner=filledOwner.get(f);
                for(Object o:index.query(hole.getEnvelopeInternal())){
                    int k=(Integer)o;if(k==owner||out[k]==null)continue;
                    CancellationCheckpoint.check();
                    if(hole.covers(polys.get(k)))out[k]=null;
                }
            }
        }
        ArrayList<Polygon> kept=new ArrayList<>(n);for(Polygon p:out)if(p!=null)kept.add(p);
        return build(kept);
    }
    @Override public Object cutCondition(Object shape,Object clip,float closeR,float openR,float tolerance){
        Geometry g=(Geometry)shape;if(g.isEmpty())return g;
        // Internal morphology tolerates coarser arcs than export curves: the 0.03 mm
        // radius margins absorb up to ~0.02 mm flattening, and vertices stay bounded.
        try{return condition(g,(Geometry)clip,closeR,openR,Math.max(tolerance,.018f));}
        catch(RuntimeException topology){
            if(topology instanceof java.util.concurrent.CancellationException)throw topology;
            CONDITION_FALLBACKS.incrementAndGet();
            // Never fail an artwork because the optional polish failed: keep the
            // exact Boolean sheet. Export inspection still reports any finding.
            return g;
        }
    }
    private Geometry condition(Geometry g,Geometry clip,float closeR,float openR,float tolerance){
        CancellationCheckpoint.check();
        g=FLOAT_FACTORY.createGeometry(g);clip=FLOAT_FACTORY.createGeometry(clip);
        Geometry out=g;
        if(closeR>0){
            out=buffer(buffer(out,closeR,tolerance),-closeR,tolerance);
            // Closing is extensive: re-impose the Snip, including its holes.
            out=overlay(out,clip,OverlayNG.INTERSECTION);
        }
        CancellationCheckpoint.check();
        if(openR>0&&!out.isEmpty())out=buffer(buffer(out,-openR,tolerance),openR,tolerance);
        // Convex ends that nearly touch cannot be bridged at cut width (the bridge
        // would be a sliver, which the opening just removed again). Separate them
        // instead: waste channels narrower than 2*closeR are the parts of a band
        // around the vinyl that a closeR disk cannot enter; carve vinyl back from them.
        if(closeR>0&&!out.isEmpty()){
            CancellationCheckpoint.check();
            // The probe is slightly smaller than the closing radius, so concave
            // corners the closing already rounded (radius closeR) are not "thin".
            // A closing fills exactly the waste a probe disk cannot enter.
            float probe=Math.max(tolerance,closeR-.025f);
            // A carve can move the narrowest point of a saddle sideways, so separation
            // repeats (at most 3 passes). Later passes only examine the pieces next to the
            // previous carve: carving removes vinyl, so a new sub-gap channel can only
            // appear between pieces the carve reached.
            Geometry region=out;
            for(int pass=0;pass<3&&!region.isEmpty();pass++){
                CancellationCheckpoint.check();
                Geometry filled=buffer(buffer(region,probe,tolerance),-probe,tolerance);
                // Discard only arc-flattening slivers (thinner than the tolerance), judged by
                // thickness, not area: a near-touch where two ribbons meet corner to corner
                // leaves a tiny-area saddle that is still a sub-gap channel and must be carved.
                Geometry thin=removeSlivers(overlay(filled,region,OverlayNG.DIFFERENCE),tolerance*.5);
                if(thin.isEmpty())break;
                Geometry carve=buffer(thin,closeR,tolerance);
                out=overlay(out,carve,OverlayNG.DIFFERENCE);
                if(openR>0&&!out.isEmpty())out=reopenNear(out,carve,openR,tolerance);
                region=near(out,carve,2*closeR+openR+tolerance);
            }
        }
        if(out.isEmpty())return EMPTY;
        // One snap to the export grid; snapping can create self-touching rings, so
        // validate after it, then hand back a geometry of the fixed-precision factory.
        out=FACTORY.createGeometry(org.locationtech.jts.precision.GeometryPrecisionReducer.reduce(out,PM));
        if(!out.isValid())out=polygonal(GeometryFixer.fix(out));
        return polygonal(out);
    }
    /** Only the warning-centred core is replaced. Halo supplies the neighbouring
     * geometry needed by buffering; the final intersection protects Snip holes.
     * A single batch of masks avoids buffering the entire large sheet per defect. */
    @Override public ArrayList<float[]> localizedCondition(Object shape,Object clip,java.util.List<float[]> raw,
            java.util.List<float[]> windows,float closeR,float openR,float tolerance){
        if(windows.isEmpty())return null;
        Geometry source=(Geometry)shape,snip=(Geometry)clip;
        // Closing/opening plus up to three carving iterations can propagate beyond
        // one radius. Use a conservative halo; the full-sheet inspector remains authoritative.
        double halo=8.0*(closeR+openR)+.1;
        ArrayList<Geometry> cores=new ArrayList<>(),halos=new ArrayList<>();
        for(float[] r:windows){
            CancellationCheckpoint.check();
            org.locationtech.jts.geom.Envelope core=new org.locationtech.jts.geom.Envelope(r[0],r[2],r[1],r[3]);
            org.locationtech.jts.geom.Envelope expanded=new org.locationtech.jts.geom.Envelope(core);expanded.expandBy(halo);
            cores.add(FACTORY.toGeometry(core));halos.add(FACTORY.toGeometry(expanded));
        }
        Geometry core=polygonal(UnaryUnionNG.union(cores,FACTORY,PM));
        Geometry region=polygonal(UnaryUnionNG.union(halos,FACTORY,PM));
        if(region.getArea()>.30*snip.getEnvelopeInternal().getArea())return null;
        Geometry local=intersectGeometry(source,region);
        if(local.isEmpty()||local.getNumPoints()>60000)return null;
        Geometry localClip=intersectGeometry(snip,region);
        Geometry fixed=condition(local,localClip,closeR,openR,Math.max(tolerance,.018f));
        CancellationCheckpoint.check();
        Geometry patch=intersectGeometry(fixed,core);
        Geometry outside=overlay(source,core,OverlayNG.DIFFERENCE);
        Geometry combined=outside.isEmpty()?patch:patch.isEmpty()?outside:overlay(outside,patch,OverlayNG.UNION);
        combined=intersectGeometry(combined,snip);
        if(!combined.isValid())return null;
        return contours(combined);
    }
    private static Geometry intersectGeometry(Geometry a,Geometry b){
        return a.isEmpty()||b.isEmpty()?EMPTY:overlay(a,b,OverlayNG.INTERSECTION);
    }
    /** Re-opens only the pieces the carve touched. Opening is idempotent and acts on each
     *  disjoint piece separately, so pieces the carve cannot have reached are already open;
     *  re-buffering the whole sheet repeated two full-sheet buffers for a few local changes. */
    private static Geometry reopenNear(Geometry out,Geometry carve,float openR,float tolerance){
        ArrayList<Polygon> keep=new ArrayList<>(),touched=new ArrayList<>();
        split(out,carve,openR+tolerance,touched,keep);
        if(touched.isEmpty())return out;
        CancellationCheckpoint.check();
        collect(buffer(buffer(build(touched),-openR,tolerance),openR,tolerance),keep);
        return build(keep);
    }
    /** Pieces of {@code g} within {@code reach} of {@code of}. */
    private static Geometry near(Geometry g,Geometry of,double reach){
        ArrayList<Polygon> in=new ArrayList<>(),out=new ArrayList<>();
        split(g,of,reach,in,out);return build(in);
    }
    private static void split(Geometry g,Geometry of,double reach,ArrayList<Polygon> near,ArrayList<Polygon> far){
        ArrayList<Polygon> pieces=new ArrayList<>();collect(g,pieces);
        ArrayList<Polygon> cuts=new ArrayList<>();collect(of,cuts);
        org.locationtech.jts.index.strtree.STRtree index=new org.locationtech.jts.index.strtree.STRtree();
        for(Polygon c:cuts)index.insert(c.getEnvelopeInternal(),c);
        for(Polygon p:pieces){
            org.locationtech.jts.geom.Envelope e=new org.locationtech.jts.geom.Envelope(p.getEnvelopeInternal());
            e.expandBy(reach);
            boolean close=false;
            for(Object o:index.query(e))if(((Polygon)o).isWithinDistance(p,reach)){close=true;break;}
            (close?near:far).add(p);
        }
    }
    private static Geometry overlay(Geometry a,Geometry b,int op){
        if(a.isEmpty())return a;if(b.isEmpty())return op==OverlayNG.INTERSECTION?a.getFactory().createPolygon():a;
        if(a.getPrecisionModel().isFloating()){
            // Conditioning path: OverlayNGRobust tries floating noding first and falls
            // back to snapping only when that fails.
            return polygonal(org.locationtech.jts.operation.overlayng.OverlayNGRobust.overlay(a,b,op));
        }
        try{return polygonal(OverlayNG.overlay(a,b,op,PM));}
        catch(org.locationtech.jts.geom.TopologyException snap){
            Geometry r=org.locationtech.jts.operation.overlayng.OverlayNGRobust.overlay(
                GeometryFixer.fix(a),GeometryFixer.fix(b),op);
            return polygonal(org.locationtech.jts.precision.GeometryPrecisionReducer.reduce(r,PM));
        }
    }
    /** Keeps pieces with any part thicker than 2*halfWidth (an inward buffer survives). */
    private static Geometry removeSlivers(Geometry g,double halfWidth){
        ArrayList<Polygon> polys=new ArrayList<>();collect(g,polys);
        org.locationtech.jts.operation.buffer.BufferParameters p=new org.locationtech.jts.operation.buffer.BufferParameters(
            2,org.locationtech.jts.operation.buffer.BufferParameters.CAP_FLAT,
            org.locationtech.jts.operation.buffer.BufferParameters.JOIN_MITRE,2);
        polys.removeIf(q->org.locationtech.jts.operation.buffer.BufferOp.bufferOp(q,-halfWidth,p).isEmpty());
        return build(polys);
    }
    /** Sheets whose optional cut conditioning failed and kept their exact Boolean geometry. */
    static final java.util.concurrent.atomic.AtomicInteger CONDITION_FALLBACKS=new java.util.concurrent.atomic.AtomicInteger();
    @Override public double area(Object shape){return ((Geometry)shape).getArea();}
    private static Geometry buffer(Geometry g,double distance,float tolerance){
        if(g.isEmpty())return g;
        double r=Math.abs(distance),step=2*Math.acos(Math.max(-1,Math.min(1,1-tolerance/Math.max(r,tolerance))));
        int quadrant=Math.max(4,Math.min(24,(int)Math.ceil(Math.PI/2/Math.max(1e-3,step))));
        org.locationtech.jts.operation.buffer.BufferParameters p=new org.locationtech.jts.operation.buffer.BufferParameters(
            quadrant,org.locationtech.jts.operation.buffer.BufferParameters.CAP_ROUND,
            org.locationtech.jts.operation.buffer.BufferParameters.JOIN_ROUND,
            org.locationtech.jts.operation.buffer.BufferParameters.DEFAULT_MITRE_LIMIT);
        return polygonal(org.locationtech.jts.operation.buffer.BufferOp.bufferOp(g,distance,p));
    }
    @Override public ArrayList<float[]> contours(Object shape){
        ArrayList<Polygon> polygons=new ArrayList<>();collect((Geometry)shape,polygons);
        ArrayList<float[]> result=new ArrayList<>();long points=0;
        for(Polygon p:polygons){
            CancellationCheckpoint.check();points+=p.getNumPoints();
            if(points>VectorBooleanEngine.MAX_OUTPUT_POINTS*2L||result.size()+p.getNumInteriorRing()>50000)
                throw new IllegalStateException("Vector sheet complexity exceeded; reduce Detail");
            result.add(loop(p.getExteriorRing()));
            for(int i=0;i<p.getNumInteriorRing();i++)result.add(loop(p.getInteriorRingN(i)));
        }
        return result;
    }
    private static float[] loop(LinearRing ring){
        org.locationtech.jts.geom.CoordinateSequence c=ring.getCoordinateSequence();
        float[] p=new float[(c.size()-1)*2];
        for(int i=0;i<c.size()-1;i++){
            if((i&1023)==0)CancellationCheckpoint.check();
            p[2*i]=(float)c.getX(i);p[2*i+1]=(float)c.getY(i);
        }return p;
    }
    private static Geometry append(Geometry a,Geometry b){
        ArrayList<Polygon> polys=new ArrayList<>();collect(a,polys);collect(b,polys);return build(polys);
    }
    private static Geometry polygonal(Geometry g){
        if(g instanceof Polygon||g instanceof org.locationtech.jts.geom.MultiPolygon)return g;
        ArrayList<Polygon> polys=new ArrayList<>();collect(g,polys);return build(polys);
    }
    private static Geometry build(ArrayList<Polygon> polys){
        if(polys.isEmpty())return EMPTY;if(polys.size()==1)return polys.get(0);
        return polys.get(0).getFactory().createMultiPolygon(polys.toArray(new Polygon[0]));
    }
    private static void collect(Geometry g,ArrayList<Polygon> out){
        if(g.isEmpty())return;if(g instanceof Polygon){out.add((Polygon)g);return;}
        if(g instanceof GeometryCollection)for(int i=0;i<g.getNumGeometries();i++)collect(g.getGeometryN(i),out);
    }
}

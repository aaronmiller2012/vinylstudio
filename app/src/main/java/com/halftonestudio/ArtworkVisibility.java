package com.halftonestudio;

import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.UnaryUnionNG;

/** Advisory standard-artwork coverage on the existing 1/1024 mm grid.
 * No repair, no mutation, no changes to exported contours. */
final class ArtworkVisibility {
    private static final PrecisionModel PM=new PrecisionModel(1024);
    private static final GeometryFactory GF=new GeometryFactory(PM);
    private static final int MAX_POINTS=120000, MAX_REGIONS=12000;
    static final class Layer {
        final double area,visible,hidden;
        final int pieces,holes,fullyHiddenComponents;
        final double smallestComponentArea, smallestComponentSpan;
        final double[] componentAreas, holeAreas;
        private final Geometry footprint;
        Layer(Geometry g,Geometry exposed,Runnable guard){footprint=g;double v=exposed.getArea();area=g.getArea();visible=Math.max(0,Math.min(area,v));hidden=area-visible;
            pieces=g.isEmpty()?0:g.getNumGeometries();int h=0;
            componentAreas=new double[pieces]; ArrayList<Double> wasteAreas=new ArrayList<>();
            double minArea=Double.POSITIVE_INFINITY,minSpan=Double.POSITIVE_INFINITY;int covered=0;
            for(int i=0;i<pieces;i++){check(guard);Polygon part=(Polygon)g.getGeometryN(i);h+=part.getNumInteriorRing();
                componentAreas[i]=part.getArea();
                for(int j=0;j<part.getNumInteriorRing();j++){check(guard); wasteAreas.add(GF.createPolygon((LinearRing)part.getInteriorRingN(j)).getArea());}
                minArea=Math.min(minArea,part.getArea());minSpan=Math.min(minSpan,Math.min(part.getEnvelopeInternal().getWidth(),part.getEnvelopeInternal().getHeight()));
                if(exposed.isEmpty()||!exposed.getEnvelopeInternal().intersects(part.getEnvelopeInternal()))covered++;
                else if(hidden>0 && pieces<=256){Geometry contact=OverlayNG.overlay(part,exposed,OverlayNG.INTERSECTION,PM);check(guard);if(contact.getArea()==0)covered++;}
            }holes=h; holeAreas=new double[wasteAreas.size()]; for(int j=0;j<holeAreas.length;j++)holeAreas[j]=wasteAreas.get(j);smallestComponentArea=pieces==0?0:minArea;smallestComponentSpan=pieces==0?0:minSpan;
            fullyHiddenComponents=hidden==0?0:pieces>256&&!exposed.isEmpty()?-1:covered;}
    }
    static String diagnostics(Layer m,float minimum){
        return diagnostics(m,minimum,minimum);
    }
    static String diagnostics(Layer m,float minimum,float gap){
        return diagnostics(m,minimum,gap,()->{});
    }
    static String diagnostics(Layer m,float minimum,float gap,Runnable guard){
        if(m.pieces==0)return "No filled components.\n";
        String hidden=m.fullyHiddenComponents<0?"not evaluated (component budget)":Integer.toString(m.fullyHiddenComponents);
        String local;
        try{local=FootprintClearance.inspect(m.footprint,minimum,gap,guard);}catch(java.util.concurrent.CancellationException cancelled){throw cancelled;}catch(RuntimeException failure){local="Local footprint checks unavailable or cancelled.\n";}
        return local+CutIntelligence.summary(m.componentAreas,m.holeAreas,minimum,gap)+String.format(java.util.Locale.US,"Smallest component: %.3f mm²\nSmallest axis-aligned span: %.3f mm\nFully obscured footprint components: %s\n%s",m.smallestComponentArea,m.smallestComponentSpan,hidden,m.smallestComponentSpan<minimum?"Review small components in Show cut risks; consider reducing detail or increasing artwork size.\n":"Span is not a minimum neck or gap measurement. Use Show cut risks where available for local clearance.\n");
    }
    private static void check(Runnable guard){CancellationCheckpoint.check();guard.run();}
    static Layer[] measure(List<? extends List<VinylEngine.Shape>> sheets, boolean[] included,Runnable guard){
        if(sheets.size()!=included.length)throw new IllegalArgumentException("Layer mismatch");
        Layer[] result=new Layer[included.length];Geometry above=GF.createPolygon();long points=0,regions=0;
        for(int i=included.length-1;i>=0;i--){
            if(!included[i])continue;
            ArrayList<Geometry> shapes=new ArrayList<>();
            for(VinylEngine.Shape s:sheets.get(i)){
                check(guard);int n=s.circle?128:s.vertexCount();points+=n;regions++;
                if(points>MAX_POINTS||regions>MAX_REGIONS)throw new IllegalStateException("Analysis complexity limit");
                Polygon p=polygon(s);if(!p.isValid()||p.isEmpty()||p.getArea()<=0)throw new IllegalArgumentException("Invalid analysis polygon");shapes.add(p);
            }
            Geometry g=shapes.isEmpty()?GF.createPolygon():UnaryUnionNG.union(shapes,GF,PM);
            validate(g);check(guard);
            Geometry visible=above.isEmpty()?g:OverlayNG.overlay(g,above,OverlayNG.DIFFERENCE,PM);
            validate(visible);result[i]=new Layer(g,visible,guard);check(guard);
            above=above.isEmpty()?g:OverlayNG.overlay(above,g,OverlayNG.UNION,PM);validate(above);
        }
        return result;
    }
    /** Specialty sheets use one even-odd filled path per layer, not independent solid loops. */
    static Layer[] measureEvenOdd(List<? extends List<float[]>> sheets,Runnable guard){
        return measureSpecialty(sheets,null,guard);
    }
    static Layer[] measureSpecialty(List<? extends List<float[]>> sheets,List<? extends List<VinylEngine.Shape>> independent,Runnable guard){
        Layer[] result=new Layer[sheets.size()]; Geometry above=GF.createPolygon();long points=0,regions=0;
        for(int i=sheets.size()-1;i>=0;i--){
            ArrayList<Geometry> rings=new ArrayList<>();
            for(float[] xy:sheets.get(i)){
                check(guard);points+=xy.length/2;regions++;
                if(points>MAX_POINTS||regions>MAX_REGIONS)throw new IllegalStateException("Analysis complexity limit");
                if(independent!=null&&!independent.get(i).isEmpty())continue;
                Polygon p=GF.createPolygon(ring(xy));validate(p);if(p.getArea()<=0)throw new IllegalArgumentException("Empty contour");rings.add(p);
            }
            Geometry g;
            if(independent!=null&&!independent.get(i).isEmpty()){
                ArrayList<Geometry> parts=new ArrayList<>();for(VinylEngine.Shape shape:independent.get(i)){check(guard);Polygon p=polygon(shape);validate(p);parts.add(p);}
                g=UnaryUnionNG.union(parts,GF,PM);
            }else g=parity(rings,0,rings.size(),guard);
            validate(g);check(guard);
            Geometry visible=above.isEmpty()?g:OverlayNG.overlay(g,above,OverlayNG.DIFFERENCE,PM);
            validate(visible);result[i]=new Layer(g,visible,guard);check(guard);
            above=above.isEmpty()?g:OverlayNG.overlay(above,g,OverlayNG.UNION,PM);validate(above);
        }
        return result;
    }
    private static Geometry parity(ArrayList<Geometry> rings,int lo,int hi,Runnable guard){
        check(guard);if(lo==hi)return GF.createPolygon();if(hi-lo==1)return rings.get(lo);
        int mid=(lo+hi)/2;Geometry a=parity(rings,lo,mid,guard),b=parity(rings,mid,hi,guard);
        Geometry g=OverlayNG.overlay(a,b,OverlayNG.SYMDIFFERENCE,PM);validate(g);return g;
    }
    private static void validate(Geometry g){if(g.getNumPoints()>MAX_POINTS||!g.isValid()||(!g.isEmpty()&&!(g instanceof Polygon||g instanceof MultiPolygon)))throw new IllegalStateException("Analysis geometry limit");}
    private static Polygon polygon(VinylEngine.Shape s){
        if(s.circle){if(!Float.isFinite(s.r)||s.r<=0)throw new IllegalArgumentException("Invalid radius");float[] xy=new float[256];for(int i=0;i<128;i++){double a=2*Math.PI*i/128;xy[2*i]=(float)(s.x+s.r*Math.cos(a));xy[2*i+1]=(float)(s.y+s.r*Math.sin(a));}return GF.createPolygon(ring(xy));}
        float[][] rings=s.contours==null?new float[][]{s.xy}:s.contours;
        LinearRing[] holes=new LinearRing[rings.length-1];for(int i=1;i<rings.length;i++)holes[i-1]=ring(rings[i]);return GF.createPolygon(ring(rings[0]),holes);
    }
    private static LinearRing ring(float[] xy){
        if(xy==null||xy.length<6||(xy.length&1)!=0)throw new IllegalArgumentException("Invalid ring");
        ArrayList<Coordinate> c=new ArrayList<>();for(int i=0;i<xy.length;i+=2){CancellationCheckpoint.check();if(!Float.isFinite(xy[i])||!Float.isFinite(xy[i+1]))throw new IllegalArgumentException("Invalid point");Coordinate p=new Coordinate(xy[i],xy[i+1]);PM.makePrecise(p);if(c.isEmpty()||!p.equals2D(c.get(c.size()-1)))c.add(p);}
        if(c.size()>1&&c.get(0).equals2D(c.get(c.size()-1)))c.remove(c.size()-1);if(c.size()<3)throw new IllegalArgumentException("Collapsed ring");c.add(c.get(0).copy());return GF.createLinearRing(c.toArray(new Coordinate[0]));
    }
}

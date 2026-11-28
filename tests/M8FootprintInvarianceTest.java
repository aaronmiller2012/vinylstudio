package com.halftonestudio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.locationtech.jts.geom.*;

/**
 * M8 / I1: FootprintClearance (advisory JTS footprint checks shown in the cut report) must not
 * depend on how the same footprint is represented: ring start vertex, ring orientation, hole
 * order and polygon order. Real footprints (ArtworkVisibility.measureEvenOdd of every
 * selectable style's sheets on the golden portrait) plus a synthetic footprint with necks,
 * thin rings and narrow gaps. Requires JTS on the classpath.
 */
public final class M8FootprintInvarianceTest {
    static final GeometryFactory GF=new GeometryFactory();
    static int footprints,evaluated,variants;
    static boolean DIAG=Boolean.getBoolean("diag");
    static int drifts;
    static Geometry largest;static String largestName;

    static LinearRing ring(GeometryFactory gf,LinearRing r,Random rnd,int mode){
        Coordinate[] c=r.getCoordinates();int n=c.length-1;
        Coordinate[] q=new Coordinate[n+1];
        int k=(mode&1)!=0?rnd.nextInt(n):0;
        for(int i=0;i<n;i++)q[i]=new Coordinate(c[(i+k)%n]);
        if((mode&2)!=0){for(int i=0,j=n-1;i<j;i++,j--){Coordinate t=q[i];q[i]=q[j];q[j]=t;}}
        q[n]=new Coordinate(q[0]);
        return gf.createLinearRing(q);
    }
    /** mode bit 1 rotate ring starts, bit 2 reverse rings, bit 4 shuffle holes and polygons. */
    static Geometry variant(Geometry g,int mode,long seed){
        Random rnd=new Random(seed);
        // Same factory (precision model): buffer and interior points honour it.
        GeometryFactory gf=g.getFactory();
        ArrayList<Polygon> polys=new ArrayList<>();
        for(int i=0;i<g.getNumGeometries();i++){
            Polygon p=(Polygon)g.getGeometryN(i);
            ArrayList<LinearRing> holes=new ArrayList<>();
            for(int h=0;h<p.getNumInteriorRing();h++)holes.add(ring(gf,p.getInteriorRingN(h),rnd,mode));
            if((mode&4)!=0)Collections.shuffle(holes,rnd);
            polys.add(gf.createPolygon(ring(gf,p.getExteriorRing(),rnd,mode),holes.toArray(new LinearRing[0])));
        }
        if((mode&4)!=0)Collections.shuffle(polys,rnd);
        return polys.size()==1?polys.get(0):gf.createMultiPolygon(polys.toArray(new Polygon[0]));
    }
    static void invariant(String name,Geometry g,double width,double gap){
        String base=FootprintClearance.inspect(g,width,gap,()->{});
        footprints++;
        if(base.contains("not evaluated")||base.contains("incomplete"))return;
        evaluated++;
        if(largest==null||g.getNumPoints()>largest.getNumPoints()){largest=g;largestName=name;}
        for(int mode=1;mode<8;mode++)for(long seed=1;seed<=2;seed++){
            String got=FootprintClearance.inspect(variant(g,mode,seed*17+mode),width,gap,()->{});
            variants++;
            if(!base.equals(got)){
                if(DIAG){drifts++;System.out.printf(Locale.US,"DIFF %s mode %d%n--- base%n%s--- variant%n%s",name,mode,base,got);break;}
                throw new AssertionError(name+" mode "+mode+" seed "+seed+": footprint report differs\n--- base\n"+base+"--- variant\n"+got);
            }
        }
    }
    static Polygon rect(double x,double y,double w,double h){return (Polygon)GF.toGeometry(new Envelope(x,x+w,y,y+h));}
    static Geometry synthetic(){
        Geometry a=rect(0,0,10,10).union(rect(10,4.9,4,.15)).union(rect(14,0,10,10));
        // A frame with two windows 0.15 mm apart (a thin vinyl bar between distinct rings).
        Polygon frame=GF.createPolygon(GF.createLinearRing(rect(30,0,20,20).getExteriorRing().getCoordinates()),
            new LinearRing[]{GF.createLinearRing(rect(30.2,.2,9,19.6).getExteriorRing().getCoordinates()),
                GF.createLinearRing(rect(39.35,.2,10.45,19.6).getExteriorRing().getCoordinates())});
        Geometry islands=rect(0,30,10,10).union(rect(10.1,30,10,10)).union(rect(20.2,30,3,3));
        return a.union(frame).union(islands);
    }
    public static void main(String[] args)throws Exception{
        invariant("synthetic",synthetic(),.254,.508);
        int[] photo=V3AllStyleExportMatrixTest.portrait(60,60);
        for(int id=0;id<VinylStyleSuite.NAMES.length;id++){
            if(VinylStyleSuite.isRetiredStyleId(id)||!NewStyleVariantPolicy.selectable(id))continue;
            VinylStyleSuite.Config c=new VinylStyleSuite.Config();
            c.style=id;c.widthMm=76.2f;c.detail=25;c.colorCount=4;c.minimumMm=.254f;c.draft=false;c.weightMm=.7f;
            StyleKit.Result r=VinylStyleSuite.generate(photo,60,60,c);
            ArrayList<List<float[]>> sheets=new ArrayList<>();
            for(StyleKit.Layer l:r.layers)sheets.add(l.loops);
            ArtworkVisibility.Layer[] measured;
            try{measured=ArtworkVisibility.measureEvenOdd(sheets,()->{});}
            catch(RuntimeException limit){continue;}
            java.lang.reflect.Field f=ArtworkVisibility.Layer.class.getDeclaredField("footprint");f.setAccessible(true);
            for(int i=0;i<measured.length;i++){
                if(measured[i]==null)continue;
                Geometry fp=(Geometry)f.get(measured[i]);
                if(!fp.isEmpty())invariant(VinylStyleSuite.NAMES[id]+"#"+i,fp,.254,.508);
            }
        }
        // Device-speed independence: a slow device (every guard call sleeps) must give the same report.
        String fast=FootprintClearance.inspect(largest,.254,.508,()->{});
        long t0=System.nanoTime();
        String slow=FootprintClearance.inspect(largest,.254,.508,()->{try{Thread.sleep(0,300_000);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        long slowMs=(System.nanoTime()-t0)/1_000_000;
        if(!fast.equals(slow))throw new AssertionError("footprint report depends on device speed ("+largestName+", "+slowMs+" ms)\n--- fast\n"+fast+"--- slow\n"+slow);
        if(slowMs<800)throw new AssertionError("slow-device simulation too fast ("+slowMs+" ms); it proves nothing");
        if(DIAG){System.out.printf(Locale.US,"DIAG %d drifting of %d evaluated footprints%n",drifts,evaluated);return;}
        if(evaluated<20)throw new AssertionError("too few footprints evaluated ("+evaluated+"); invariance proved nothing");
        System.out.printf(Locale.US,"PASS I1 footprint-clearance representation invariance: %d footprints (%d fully evaluated), %d variants; same report on a simulated slow device (%s, %d ms)%n",footprints,evaluated,variants,largestName,slowMs);
    }
}

package com.halftonestudio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;

/**
 * M7: property test of removeMicroFragmentsLocal (Prismatic conditioned path; P4 itself was reverted
 * because cut-inspection findings depend on ring order). Originally: finish() used removeMicroFragmentsLocal (no
 * (Chainmail, the six Confetti styles and Fishnet all reach it)
 * full-sheet snap-rounded re-union). On every real Chainmail and Confetti sheet, the local
 * result must be valid and topologically equal to the full re-union: same polygons, same
 * holes (Snip and intentional), same area. Requires JTS on the classpath.
 */
public final class M7MicroFragmentParityTest {
    static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);}
    static int compared,changed;

    static int holes(Geometry g){int n=0;for(int i=0;i<g.getNumGeometries();i++){Geometry p=g.getGeometryN(i);if(p instanceof Polygon)n+=((Polygon)p).getNumInteriorRing();}return n;}

    public static void main(String[] args) throws Exception {
        final VectorBooleanEngine.Backend jts=(VectorBooleanEngine.Backend)Class.forName("com.halftonestudio.JtsVectorBooleanBackend").getDeclaredConstructor().newInstance();
        VectorBooleanEngine.install(new VectorBooleanEngine.Backend(){
            public Object path(float[][] c){return jts.path(c);}
            public Object clipPath(float[][] c){return jts.clipPath(c);}
            // Every full cleanup is also run through the local variant (used by the 561 Prismatic
            // conditioned path) and both are compared; production keeps the full result.
            public Object removeMicroFragments(Object s,float v,float w){Object full=jts.removeMicroFragments(s,v,w);compareLocal(s,v,w);return full;}
            public Object removeMicroFragmentsLocal(Object s,float v,float w){return compareLocal(s,v,w);}
            Object compareLocal(Object s,float v,float w){
                Geometry full=(Geometry)jts.removeMicroFragments(s,v,w);
                Geometry local=(Geometry)jts.removeMicroFragmentsLocal(s,v,w);
                compared++;if(full!=s)changed++;
                check(local.isValid(),"local micro-fragment result invalid");
                check(full.getNumGeometries()==local.getNumGeometries(),"polygon count "+local.getNumGeometries()+" vs "+full.getNumGeometries());
                check(holes(full)==holes(local),"hole count "+holes(local)+" vs "+holes(full));
                check(Math.abs(full.getArea()-local.getArea())<=1e-9*Math.max(1,full.getArea()),"area differs");
                check(full.isEmpty()==local.isEmpty()&&(full.isEmpty()||full.equalsTopo(local)),"local result not topologically equal");
                return local;
            }
            public Object batch(VectorBooleanEngine.Motif[] m,int lo,int hi){return jts.batch(m,lo,hi);}
            public Object combine(Object a,Object b,boolean d){return jts.combine(a,b,d);}
            public Object intersect(Object a,Object b){return jts.intersect(a,b);}
            public ArrayList<float[]> contours(Object s){return jts.contours(s);}
            public Object cutCondition(Object s,Object c,float a,float b,float t){return jts.cutCondition(s,c,a,b,t);}
            public ArrayList<float[]> localizedCondition(Object s,Object c,List<float[]> r,List<float[]> w,float a,float b,float t){return jts.localizedCondition(s,c,r,w,a,b,t);}
            public double area(Object s){return jts.area(s);}
        });
        // Confetti keeps the full re-union (its repair is ring-order sensitive); only the
        // finish() path (Chainmail, Fishnet) uses the local cleanup.
        int[] styles={VinylStyleSuite.CHAINMAIL,VinylStyleSuite.FISHNET};
        // Real CC0 photos at the app's fresh-install cut settings (0.254 mm minimum), as on the device.
        for(String name:new String[]{"astronaut.png","coffee.png"}){
            M4ArtBench.Photo p=M4ArtBench.load(new java.io.File("tests/m4/photos/"+name),1800);
            for(int style:styles)for(float size:new float[]{152.4f,203.2f}){
                VinylStyleSuite.Config c=M4ArtBench.suiteConfig(style,p,size,.254f,.508f);
                int before=compared;
                StyleKit.Result r=VinylStyleSuite.generate(p.argb,p.w,p.h,c);
                check(r.layers.size()>=2,"no layers for "+VinylStyleSuite.NAMES[style]);
                check(compared>before,"micro-fragment cleanup not exercised for "+VinylStyleSuite.NAMES[style]);
            }
        }
        check(changed>0,"no sheet actually needed cleanup; the parity check proved nothing");
        System.out.printf(Locale.US,"PASS P4 local micro-fragment cleanup == full re-union on %d real sheets (%d changed by cleanup)%n",compared,changed);
    }
}

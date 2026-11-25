package com.halftonestudio;
import java.util.Arrays;
import java.util.Collections;

public final class RegistrationCutGeometryRC148Test {
    private static void ok(boolean b,String msg){if(!b)throw new AssertionError(msg);}
    private static float[] square(float left,float top,float right,float bottom){
        return new float[]{left,top,right,top,right,bottom,left,bottom};
    }
    private static float[][] points(float x,float y){return new float[][]{{x,y},{-5,-5},{105,-5},{105,105}};}
    public static void main(String[] args){
        StyleKit.Result portrait=new StyleKit.Result(100,100);
        StyleKit.Layer silhouette=new StyleKit.Layer("subject",0xff000000);
        silhouette.loops.add(square(30,30,70,70));portrait.layers.add(silhouette);
        float[][] safe=points(10,10);
        ok(RegistrationCutGeometry.style(portrait,2.5f,1f,safe).valid(),
            "unused area within artwork rectangle should be legal");
        ok(RegistrationCutGeometry.strictArtwork(100,100,2.5f,1f,safe).invalid(0),
            "unknown geometry must retain strict rectangular fallback");
        float[][] center=points(40,40);
        RegistrationCutGeometry.Report blocked=RegistrationCutGeometry.style(portrait,2.5f,1f,center);
        ok(blocked.invalid(0)&&!blocked.invalid(1)&&!blocked.invalid(2)&&!blocked.invalid(3),
            "only the offending mark should be highlighted");
        silhouette.loops.add(square(38,38,62,62)); // even-odd hole
        ok(RegistrationCutGeometry.style(portrait,2.5f,1f,points(50,50)).valid(),
            "fully empty hole should be valid");
        ok(RegistrationCutGeometry.style(portrait,2.5f,1f,points(38.5f,50)).invalid(0),
            "hole boundary must include full clearance");
        silhouette.loops.clear();
        StyleKit.Layer engraving=new StyleKit.Layer("stroke",0xff000000);
        engraving.strokeMm=1.2f;engraving.lines.add(new float[]{25,25,75,75});
        portrait.layers.add(engraving);
        ok(RegistrationCutGeometry.style(portrait,2.5f,1f,points(50,50)).invalid(0),
            "stroked pen paths must be tested against physical width");
        ok(RegistrationCutGeometry.style(portrait,2.5f,1f,points(50,65)).valid(),
            "empty area off centerline remains usable");
        RegistrationCutGeometry.Report overlap=RegistrationCutGeometry.basic(100,100,4,2,
            new float[][]{{-9,-9},{-5,-8},{105,-5},{105,105}});
        ok(overlap.invalid(0)&&overlap.invalid(1)&&!overlap.invalid(2),
            "collision must mark both offending registration positions");
        boolean[] mask=new boolean[100];mask[55]=true;
        ok(RegistrationCutGeometry.hitsRaster(5.5f,5.5f,.2f,mask,10,10,10,10),
            "filled raster cell collision");
        ok(!RegistrationCutGeometry.hitsRaster(1f,1f,.2f,mask,10,10,10,10),
            "blank raster cell clearance");
        for(int style=0;style<=6;style++){
            float[][] defaults=RegistrationMarks.defaultPositions(100,100,4,1);
            ok(RegistrationCutGeometry.basic(100,100,4,1,defaults).valid(),"default marks "+style);
            ok(RegistrationMarks.outline(style,defaults[0][0],defaults[0][1],4,0.2f)==null ||
               RegistrationMarks.outline(style,defaults[0][0],defaults[0][1],4,0.2f).length>=6,
               "valid physical mark shape "+style);
        }
        System.out.println("PASS RC148: clear internal negative space, filled/holed polygons, open strokes, raster, per-mark feedback, conservative fallback");
    }
}

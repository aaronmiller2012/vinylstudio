package com.halftonestudio;
import java.util.*;

/** Host regression for fail-closed Fix8 automatic cut conditioning. */
public final class V3Fix8AuditRepairTest {
    static float[] rect(float x0,float y0,float x1,float y1){return new float[]{x0,y0,x1,y0,x1,y1,x0,y1};}
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static StyleKit.Result result(float[]... loops){
        StyleKit.Result doc=new StyleKit.Result(150,150);
        StyleKit.Layer layer=new StyleKit.Layer("physical sheet",0xff000000);
        layer.loops.addAll(Arrays.asList(loops));doc.layers.add(layer);return doc;
    }
    public static void main(String[] args){
        require(StyleCutGeometryPreflight.exportGapMm(1.25f,.8f)==1.25f,"requested 1.25 mm gap was silently capped");
        require(StyleCutGeometryPreflight.exportGapMm(0f,.8f)==.25f,"minimum conservative gap changed");
        float[] a=rect(10,10,15,15),b=rect(16.0f,10,21f,15);
        require(!StyleCutGeometryPreflight.inspect(result(a,b),.8f,1.25f,20).isEmpty(),"a 1.0mm gap must fail 1.25mm user setting");
        require(StyleCutGeometryPreflight.inspect(result(a,b),.8f,.5f,20).isEmpty(),"a 1.0mm gap should pass .5mm user setting");
        require(!VinylStyleSuite.preservesArtwork(Arrays.asList(rect(5,5,15,15)),Arrays.asList(rect(5,5,40,40))),"automatic repair must not create much more vinyl");
        require(!VinylStyleSuite.preservesArtwork(Arrays.asList(rect(5,5,15,15)),Collections.emptyList()),"cannot auto-delete whole sheet");
        require(VinylStyleSuite.preservesArtwork(Arrays.asList(rect(5,5,15,15)),Arrays.asList(rect(5,5,15,15))),"unchanged sheet rejected");
        float[] invalid={10,10,30,30,10,30,30,10}; // Crossing bow-tie
        StyleKit.Result crossed=result(invalid);
        VinylStyleSuite.conditionForCutting(crossed,crossed.layers.get(0),.8f,.5f);
        require(crossed.layers.get(0).loops.size()==1&&Arrays.equals(invalid,crossed.layers.get(0).loops.get(0)),"invalid geometry was silently re-contoured");
        // One contour with many alternating vertical sweeps can exceed a
        // scanline-event cap despite containing relatively few vertices.
        ArrayList<float[]> zigzags=new ArrayList<>();
        for(int i=0;i<800;i++){
            float x=10f+i*.1f;
            zigzags.add(rect(x,10,x+.02f,120));
        }
        boolean bounded=false;
        try{CutGeometryConditioner.rasterize(zigzags,1300,1300,.1f);}
        catch(CutGeometryConditioner.BudgetExceededException expected){bounded=true;}
        require(bounded,"long cut edges bypass raster memory budget");
        System.out.println("PASS Fix8 AuditRepair: selected gap, vinyl area parity, invalid paths and raster budget");
    }
}

package com.halftonestudio;

/** Android-free regression for registration pads, forbidden overlaps, and export parity. */
public final class RegistrationDragRC144Test {
    static void ok(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static int[] pixels(){int[] out=new int[32*32];java.util.Arrays.fill(out,0xff777777);return out;}
    public static void main(String[] args){
        for(float size:new float[]{2f,2.5f,4f,12f})for(float clearance:new float[]{0f,1f,3f}){
            float[][] points=RegistrationMarks.defaultPositions(100,80,size,clearance);
            points[0][0]-=11;points[1][0]+=15;points[2][1]+=13;
            float expected=RegistrationMarks.layout(100,80,size,clearance,points,8f).paddingMm;
            float actual=RegistrationPreviewGeometry.padding(100,80,size,clearance,points,8f);
            ok(Math.abs(expected-actual)<.0001f,"Preview and export pad diverged");
            ok(RegistrationPreviewGeometry.issue(100,80,size,clearance,points)==null,"Valid placement rejected");
            ok(RegistrationPreviewGeometry.issue(100,80,size,clearance,new float[][]{{30,30},points[1],points[2],points[3]})==null,
                "Preview must not mistake the entire photo rectangle for physical vinyl");
            ok(RegistrationCutGeometry.strictArtwork(100,80,size,clearance,
                new float[][]{{30,30},points[1],points[2],points[3]}).invalid(0),
                "Unverified specialty cut geometry must remain conservatively blocked");
            ok(RegistrationPreviewGeometry.issue(100,80,size,clearance,new float[][]{points[0],points[0].clone(),points[2],points[3]})!=null,
                "Overlapping marks must be rejected without hiding them from editor");
        }
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.widthMm=100;s.registration=false;
        s.marks=new float[][]{{-8,-8},{-8,-8},{-8,-8},{-8,-8}};
        VinylEngine.Result noMarks=VinylEngine.generate(pixels(),32,32,s);
        ok(VinylEngine.validateMarks(noMarks)==null,"Disabled marks must not block preview or export");
        ok(!VinylEngine.svg(noMarks,0).contains("<circle cx=\"-8.000\""),"Disabled marks leaked to export");
        s.registration=true;s.marks=new float[][]{{35,35},{120,-20},{-20,120},{120,120}};
        VinylEngine.Result unsafe=VinylEngine.generate(pixels(),32,32,s);
        ok(VinylEngine.validateMarks(unsafe)!=null,"Interior registration must block standard export");
        System.out.println("PASS RC144: drag padding equals SVG, invalid state visible, disabled marks and interior guards");
    }
}

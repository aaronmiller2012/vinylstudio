package com.halftonestudio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.ZipOutputStream;

/** Regresses the actual 11-inch circle + 12mm mark + 0.010-inch cut request. */
public final class V3Beta25SnipMatTest {
    private static void ok(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void mat()throws Exception {
        CricutMatPreflight.requireFits(291.7,291.7,"fit.svg");
        boolean rejected=false;
        try{CricutMatPreflight.requireFits(315.544,315.544,"oversized.svg");}
        catch(IOException bad){rejected=bad.getMessage().contains("mat limit");}
        ok(rejected,"Cricut oversized 11-inch legacy sheet not rejected");
        CricutMatPreflight.requireFits(290,596.0,"long-mat.svg");
        rejected=false;
        try{CricutMatPreflight.requireFits(300,600,"too-long.svg");}
        catch(IOException expected){rejected=true;}
        ok(rejected,"12x24 limit bypassed");
    }
    private static void registration()throws Exception {
        float mm=11*25.4f,size=12f,clear=2.286f;
        RegistrationMarks.Layout compact=RegistrationMarks.artworkLayout(mm,mm,size,clear,null,8f,true);
        ok(compact.paddingMm<6.35f,"didn't reclaim circular border padding");
        ok(mm+2*compact.paddingMm<=11.5f*25.4f,"circular artboard still oversized");
        ok(compact.positions[0][0]>0,"automatic mark not in unused corner");
        ok(RegistrationCutGeometry.basic(mm,mm,size,clear,compact.positions).valid(),"compact marks contact");
        // A manually moved mark is never overwritten or implicitly re-centered.
        float[][] manual={{-16,-16},{mm+16,-16},{-16,mm+16},{mm+16,mm+16}};
        RegistrationMarks.Layout preserved=RegistrationMarks.artworkLayout(mm,mm,size,clear,manual,8f,true);
        ok(preserved.positions[0][0]==manual[0][0],"custom registration overwritten");
        ok(preserved.paddingMm>15,"custom artboard silently clipped");
        // Noncircle artwork must keep the previous outside positions.
        RegistrationMarks.Layout rectangle=RegistrationMarks.artworkLayout(mm,mm,size,clear,null,8f,false);
        ok(rectangle.positions[0][0]<0,"rectangular mark accidentally moved into artwork");
    }
    private static void underlay()throws Exception {
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.widthMm=11f*25.4f;s.marginMm=4.445f;s.minimumMm=.254f;
        s.outerSpacingMm=8;s.markSizeMm=12;s.registrationClearanceMm=2.286f;
        s.circleCrop=true;s.registration=true;
        s.snips=new SnipMask();
        s.snips.add(new SnipMask.Operation(SnipMask.KEEP_INSIDE,
            .10f,.10f,.90f,.10f,.90f,.90f,.10f,.90f));
        int[] white=new int[128*128];Arrays.fill(white,0xffffffff);
        AutoSilhouetteUnderlay.Output base=AutoSilhouetteUnderlay.make(s,white,128,128,0xffffffff);
        ok(base!=null,"no base generated");
        ok(base.result.boardW<=11.5f*25.4f+.002f,"underlay is wider than Maker mat");
        ok(VinylEngine.validateMarks(base.result)==null,"underlay overlaps registration");
        FinalCutSheetSafety.Report inspected=FinalCutSheetSafety.inspect("base.svg",base.svg(),s.minimumMm);
        ok(inspected.unverified==0&&inspected.critical==0&&inspected.narrow==0,
            "synthetic solid base still unsafe: "+inspected.status()+" "+inspected.detail);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)){
            CutSheetZipWriter cuts=new CutSheetZipWriter(zip,OutputDeviceProfile.CRICUT,.254f,false);
            cuts.put("base.svg",base.svg());cuts.finish();
        }
        ok(bytes.size()>200,"empty Cricut base ZIP");
        // Confirm standard detail and solid base calculate identical marks and pad.
        VinylEngine.Result detail=VinylEngine.generate(white,128,128,s);
        ok(Math.abs(detail.boardW-base.result.boardW)<.005,"standard/base artboards differ");
        for(int i=0;i<4;i++)for(int k=0;k<2;k++)
            ok(Math.abs(detail.marks[i][k]-base.result.marks[i][k])<.005,"registration coordinates differ");
    }
    public static void main(String[] args)throws Exception{
        mat();registration();underlay();
        System.out.println("PASS Beta25 Snip base, compact registration parity, Cricut mat limits, valid SVG ZIP");
    }
}

package com.halftonestudio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Physical mat dimensions for the Cricut Explore Air 2, driven by REAL standard exports.
 *  - Sheets whose marked board fits 11.5 x 11.5 in are labelled for a 12 x 12 mat.
 *  - Sheets that fit only 11.5 x 23.5 in are labelled for a 12 x 24 mat (the app allows this
 *    by explicit choice in the Size dialog; the export must say so, not stay silent).
 *  - Anything larger is rejected and nothing is published.
 * The requirement is written into the ZIP (CricutMatPreflight.NOTE_FILE) and exposed by the writer.
 */
public final class V3CricutMatRequirementTest {
    static void ok(boolean v,String m){if(!v)throw new AssertionError(m);}

    static VinylEngine.Result render(float widthMm,int w,int h){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.widthMm=widthMm;s.minimumMm=.254f;s.registration=true;s.outerSpacingMm=8f;
        int[] px=V3AllStyleExportMatrixTest.portrait(w,h);
        return VinylEngine.generate(px,w,h,s);
    }
    /** Returns the mat note written into the ZIP, or throws the writer's rejection. */
    static String export(VinylEngine.Result r,String[] matOut) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)){
            CutSheetZipWriter w=new CutSheetZipWriter(zip,OutputDeviceProfile.CRICUT,.254f,true);
            for(int i=0;i<r.settings.layers.size();i++)if(VinylEngine.cutLayer(r,i))w.put(String.format("%02d.svg",i+1),VinylEngine.svg(r,i));
            w.finish();
            matOut[0]=w.requiredCricutMat();
        }
        try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))){
            for(ZipEntry e;(e=in.getNextEntry())!=null;)
                if(e.getName().equals(CricutMatPreflight.NOTE_FILE))return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        String[] mat=new String[1];
        // 8 x 8 in square art + 8 mm margins = 219.2 mm board: 12 x 12.
        String note=export(render(203.2f,60,60),mat);
        ok("12x12".equals(mat[0]),"8 in square should need a 12x12 mat, got "+mat[0]);
        ok(note!=null&&note.contains("12 × 12"),"ZIP lacks 12 x 12 mat note: "+note);
        // 10.5 in wide portrait (5:6) = 282.7 x 336.0 mm board: only a 12 x 24 mat.
        note=export(render(266.7f,60,72),mat);
        ok("12x24".equals(mat[0]),"10.5 in portrait should need a 12x24 mat, got "+mat[0]);
        ok(note!=null&&note.contains("12 × 24")&&note.toLowerCase().contains("will not fit")&&note.contains("12 × 12"),
            "ZIP must warn that a 12 x 24 sheet will not fit a 12 x 12 mat: "+note);
        // 11 in wide (the 11 x 14 preset) + 8 mm margins = 295.4 mm: no Cricut mat.
        boolean rejected=false;
        try{export(render(279.4f,60,76),mat);}catch(IOException expected){rejected=expected.getMessage().contains("mat limit");}
        ok(rejected,"an 11 in wide marked sheet exceeds every Cricut mat and must be rejected");
        // Classification boundaries, independent of rendering.
        ok("12x12".equals(CricutMatPreflight.requiredMat(292.1,292.1)),"11.5 in square is 12x12");
        ok("12x24".equals(CricutMatPreflight.requiredMat(292.1,292.2)),"just over 11.5 in is 12x24");
        ok("12x24".equals(CricutMatPreflight.requiredMat(596.9,292.1)),"landscape 23.5 x 11.5 in is 12x24");
        ok(CricutMatPreflight.requiredMat(292.2,292.2)==null,"over 11.5 in on both sides fits no mat");
        ok(CricutMatPreflight.requiredMat(Double.NaN,10)==null,"NaN fits no mat");
        System.out.println("PASS Cricut mat requirement: 12x12 / 12x24 labelled from real sheets; oversize rejected");
    }
}

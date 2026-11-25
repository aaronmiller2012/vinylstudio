package com.halftonestudio;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipOutputStream;

/** Gradle JUnit coverage for the last-mile cut geometry and revision store. */
public final class V3Beta4UnitTest {
    private static String svg(String geometry){
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100mm\" height=\"100mm\" viewBox=\"0 0 100 100\">"+geometry+"</svg>";
    }
    @Test public void finalSheetDetectsOverlappingBlades(){
        FinalCutSheetSafety.Report r=FinalCutSheetSafety.inspect("L1.svg",
            svg("<rect x=\"10\" y=\"10\" width=\"25\" height=\"25\"/>"+
                "<rect x=\"20\" y=\"20\" width=\"25\" height=\"25\"/>"),.30f);
        assertTrue(r.critical>0);
    }
    @Test public void drawingOnlyIsNotVinylContour(){
        FinalCutSheetSafety.Report r=FinalCutSheetSafety.inspect("pen.svg",
            svg("<path fill=\"none\" stroke=\"black\" d=\"M 0 0 L 90 90\"/>"),.30f);
        assertEquals("DRAW_ONLY",r.status());
    }
    @Test public void cutZipCannotPublishUnreviewedCriticalContour()throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        boolean rejected=false;
        try(ZipOutputStream zip=new ZipOutputStream(bytes)){
            CutSheetZipWriter writer=new CutSheetZipWriter(zip,OutputDeviceProfile.CRICUT,.30f,false);
            writer.put("unsafe.svg",svg("<rect x=\"10\" y=\"10\" width=\"25\" height=\"25\"/>"+
                "<rect x=\"20\" y=\"20\" width=\"25\" height=\"25\"/>"));
            try{writer.finish();}catch(IOException expected){rejected=true;}
        }
        assertTrue("Unreviewed crossing did not block ZIP finalization",rejected);
    }
}

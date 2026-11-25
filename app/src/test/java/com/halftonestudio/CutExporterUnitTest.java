package com.halftonestudio;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.*;

/** Release unit tests run by :app:testReleaseUnitTest before lint and bundleRelease. */
public final class CutExporterUnitTest {
    private static final String SAMPLE="<svg width='50mm' height='30mm' viewBox='0 0 50 30'>"
        +"<circle cx='10' cy='12' r='3'/>"
        +"<path d='M20 2L35 2L35 15L20 15Z M23 5L23 10L29 10L29 5Z'/>"
        +"<path d='M 0 20 C 5 25 15 15 20 20' fill='none' stroke='black'/></svg>";

    @Test public void genuineDxfRetainsSizeAndContours() throws Exception {
        SvgDxfR13Exporter.Result result=SvgDxfR13Exporter.convert(SAMPLE);
        assertEquals(50.0,result.widthMm,1e-6);
        assertEquals(30.0,result.heightMm,1e-6);
        assertEquals(3,result.closedContours);
        assertEquals(1,result.openPaths);
        assertTrue(result.dxf.contains("AC1012"));
        assertTrue(result.dxf.contains("0\r\nCIRCLE\r\n"));
        assertTrue(result.dxf.endsWith("0\r\nEOF\r\n"));
    }
    @Test public void collinearOvershootingCubicPreservesActualBounds() throws Exception {
        SvgDxfR13Exporter.Result result=SvgDxfR13Exporter.convert(
            "<svg width='100mm' height='100mm' viewBox='0 0 100 100'>"+
            "<path fill='none' stroke='black' d='M 0 50 C 100 50 100 50 10 50'/></svg>");
        assertTrue("Overshoot was lost during DXF flattening",result.maxXmm>76.0);
        assertTrue("Curve was incorrectly shortened to one segment",result.vertices>2);
    }
    @Test public void maliciousOrUnsupportedSvgMustFailClosed() throws Exception {
        try {
            SvgDxfR13Exporter.convert("<!DOCTYPE svg [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><svg width='10mm' height='10mm' viewBox='0 0 10 10'>&x;</svg>");
            fail("Entity expansion was accepted");
        } catch(IOException expected) { /* intentional */ }
    }
    @Test public void stageValidatesDxfOnlyZipAndManifest() throws Exception {
        File dir=Files.createTempDirectory("vinyl-unit-").toFile();
        File file=new File(dir,"result.zip");
        try {
            try(StagedCutZip stage=new StagedCutZip(dir)) {
                try(ZipOutputStream zip=new ZipOutputStream(stage.openStream())) {
                    CutSheetZipWriter writer=new CutSheetZipWriter(zip,OutputDeviceProfile.SILHOUETTE_BASIC);
                    assertEquals("01-cut.dxf",writer.put("01-cut.svg",SAMPLE));
                    zip.putNextEntry(new ZipEntry("layer-manifest.csv"));
                    zip.write(writer.manifest("order,file\n1,01-cut.svg\n").getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                    writer.finish();
                }
                stage.verify();
                try(OutputStream out=new FileOutputStream(file)) {stage.copyTo(out);}
            }
            try(ZipFile zip=new ZipFile(file)) {
                assertNotNull(zip.getEntry("01-cut.dxf"));
                assertNotNull(zip.getEntry("dimensions-dxf.txt"));
                assertNull(zip.getEntry("01-cut.svg"));
            }
        } finally {file.delete();dir.delete();}
    }
    @Test public void legacySvgDestinationUnchanged() throws Exception {
        assertEquals(OutputDeviceProfile.CutFormat.SVG,
            OutputDeviceProfile.byId(OutputDeviceProfile.CRICUT).format);
        assertEquals(OutputDeviceProfile.CutFormat.DXF_R13,
            OutputDeviceProfile.byId(OutputDeviceProfile.SILHOUETTE_BASIC).format);
    }
}

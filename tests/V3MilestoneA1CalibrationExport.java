package com.halftonestudio;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** A small, independent cutter-import fixture. Not an app feature. */
public final class V3MilestoneA1CalibrationExport {
    private static final String MARKS=
        "<g fill='#000000'>"+
        "<circle cx='8' cy='8' r='1.5'/>"+
        "<circle cx='271.4' cy='8' r='1.5'/>"+
        "<circle cx='8' cy='195.2' r='1.5'/>"+
        "<circle cx='271.4' cy='195.2' r='1.5'/>"+
        "</g>";
    private static String svg(String art){
        return "<svg xmlns='http://www.w3.org/2000/svg' width='279.4mm' height='203.2mm' "+
           "viewBox='0 0 279.4 203.2'>"+MARKS+art+"</svg>";
    }
    static void put(Path out,String stem,String artwork)throws Exception{
        String svg=svg(artwork);
        Path svgPath=out.resolve(stem+".svg"),dxfPath=out.resolve(stem+".dxf");
        Files.writeString(svgPath,svg,StandardCharsets.UTF_8);
        SvgDxfR13Exporter.Result dxf=SvgDxfR13Exporter.convert(svg);
        if(Math.abs(dxf.widthMm-279.4)>.0001 || Math.abs(dxf.heightMm-203.2)>.0001 || dxf.closedContours!=5)
            throw new AssertionError("Physical calibration geometry was altered");
        Files.writeString(dxfPath,dxf.dxf,StandardCharsets.UTF_8);
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Pass output directory");
        Path out=Path.of(args[0]);Files.createDirectories(out);
        put(out,"01-registration-plus-inch-square","<rect fill='#000000' x='90' y='90' width='25.4' height='25.4'/>");
        put(out,"02-registration-plus-test-rectangle","<rect fill='#000000' x='142' y='90' width='25.4' height='25.4'/>");
        String readme="""
            VinylStudio v3 — Milestone A1 cutter-import calibration

            Two layers, same 11.000 in x 8.000 in declared SVG artboard.
            Both contain four 3.000mm-diameter circular registration marks.
            Their CENTERS are at (8,8), (271.4,8), (8,195.2), (271.4,195.2) mm.
            Center-to-center horizontal = 263.400mm (10.370079in).
            Center-to-center vertical   = 187.200mm (7.370079in).
            Layer A has a 25.400mm (1.000in) square at x=90mm, y=90mm.
            Layer B has the same square at x=142mm, y=90mm.

            Test 1 (Cricut): Import the SVG files separately, verify the one-inch
            square measures exactly one inch; record all four registration center
            positions after import. Do not use a screenshot/pixel measurement.
            Cricut's Android Design Space supports SVG, not DXF uploads.

            Test 2 (Silhouette Studio): Import the DXF files separately and compare
            the one-inch square and mark-to-mark distances, applying the REQUIRED
            scale correction only if that cutter software has changed the units.
            DXF only carries cut geometry, so the importer may crop the empty SVG
            artboard. DO NOT resize its geometry selection to 11 x 8 inches.

            Test 3 (physical): Cut on scrap at an appropriate material setting,
            measure the square and center distances with a ruler/caliper; overlay
            both layers and confirm all four marks align. Record tool/app versions.
            Run no physical cut without verifying blade setting and available mat.

            The source geometry is deliberate and small. A host simulation is not
            proof that Design Space/Silhouette Studio imported it at true size.
            """;
        Files.writeString(out.resolve("READ-MEASURE-FIRST.txt"),readme,StandardCharsets.UTF_8);
        Files.writeString(out.resolve("EXPECTED-MEASUREMENTS.csv"),
             "measurement,expected_mm,expected_in\n"+
             "SVG artboard width,279.400,11.000000\nSVG artboard height,203.200,8.000000\n"+
             "Horizontal registration center separation,263.400,10.370079\n"+
             "Vertical registration center separation,187.200,7.370079\n"+
             "Calibration square side,25.400,1.000000\n"+
             "Registration circle diameter,3.000,0.118110\n",StandardCharsets.UTF_8);
        System.out.printf(Locale.US,"PASS 2 SVG + 2 DXF calibration layers at 279.400 x 203.200mm%n");
    }
}

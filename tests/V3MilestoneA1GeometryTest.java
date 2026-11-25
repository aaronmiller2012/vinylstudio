package com.halftonestudio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

/** Milestone A1 executable oracle: physical artboards and last-mile cut contacts.
 * Compiles on Java 21 without the Android SDK. These tests do NOT replace
 * actual Cricut/Silhouette import, device preview, or physical cut testing.
 */
public final class V3MilestoneA1GeometryTest {
    static void ok(boolean truth,String failure){if(!truth)throw new AssertionError(failure);}
    static String svg(double w,double h,String content){
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\""+w+"mm\" height=\""+h+
            "mm\" viewBox=\"0 0 "+w+" "+h+"\">"+content+"</svg>";
    }
    static FinalCutSheetSafety.Report inspect(String shapes){return FinalCutSheetSafety.inspect("layer.svg",svg(100,100,shapes),.30f);}
    static byte[] zip(int device,boolean approve,String ... sheets)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream out=new ZipOutputStream(bytes)){
            CutSheetZipWriter w=new CutSheetZipWriter(out,device,.3f,approve);
            for(int i=0;i<sheets.length;i++)w.put(String.format("%02d-art.svg",i+1),sheets[i]);
            w.finish();
        }
        return bytes.toByteArray();
    }
    static String entry(byte[] compressed,String name)throws Exception{
        try(ZipInputStream input=new ZipInputStream(new ByteArrayInputStream(compressed))){
            ZipEntry e;while((e=input.getNextEntry())!=null)
                if(name.equals(e.getName()))return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
        throw new AssertionError("ZIP lacks "+name);
    }
    static void shouldRejectMismatchedArtboards(int device)throws Exception{
        boolean rejected=false;
        try{
            zip(device,false,
                svg(279.4,203.2,"<rect x='10' y='10' width='5' height='5'/>"),
                svg(279.8,203.2,"<rect x='10' y='10' width='5' height='5'/>"));
        }catch(IOException expected){rejected=expected.getMessage().contains("artboard");}
        ok(rejected,"Different physical sheet dimensions were allowed: device="+device);
    }
    public static void main(String[] args)throws Exception{
        String poly="<rect x='40' y='40' width='20' height='20'/>";
        FinalCutSheetSafety.Report separate=inspect(poly+"<circle cx='20' cy='20' r='4'/>");
        ok(separate.critical==0&&separate.narrow==0&&separate.unverified==0,
            "Separate mixed primitives must be fully inspected");
        FinalCutSheetSafety.Report contact=inspect(poly+"<circle cx='38' cy='50' r='2'/>");
        ok(contact.critical>0&&contact.unverified==0,"Circle-polygon tangent escaped");
        FinalCutSheetSafety.Report crossing=inspect(poly+"<circle cx='40' cy='50' r='5'/>");
        ok(crossing.critical>0,"Circle-polygon crossing escaped");
        FinalCutSheetSafety.Report near=inspect(poly+"<circle cx='37.8' cy='50' r='2'/>");
        ok(near.narrow>0&&near.critical==0,"0.2mm circle-polygon clearance escaped");
        FinalCutSheetSafety.Report safeHole=inspect("<rect x='35' y='35' width='30' height='30'/>"+
            "<circle cx='50' cy='50' r='3'/>");
        ok(safeHole.critical==0&&safeHole.unverified==0,"Safely inset circular hole flagged");
        FinalCutSheetSafety.Report contained=inspect("<circle cx='50' cy='50' r='12'/>"+
            "<rect x='46' y='46' width='8' height='8'/>");
        ok(contained.critical==0&&contained.unverified==0,
            "Polygon wholly inside circle incorrectly treated as intersection");
        ok(inspect("<circle cx='98' cy='50' r='4'/>").critical>0,"Circle outside artboard passed");
        ok(inspect("<rect x='99' y='30' width='4' height='5'/>").critical>0,"Polygon outside artboard passed");
        ok(inspect("<rect x='90' y='20' width='10' height='5'/>").critical==0,"Valid edge contour flagged");
        // Bounds remain a hard stop even after user review, and cannot be
        // bypassed by exceeding the expensive proximity inspection budget.
        for(boolean approved:new boolean[]{false,true}){
            boolean escaped=false;
            try{zip(OutputDeviceProfile.CRICUT,approved,
                svg(100,100,"<circle cx='98' cy='50' r='4'/>"));}
            catch(IOException expected){escaped=true;}
            ok(escaped,"An out-of-artboard contour was published after review="+approved);
        }
        StringBuilder dense=new StringBuilder();
        for(int i=0;i<3500;i++)dense.append("<rect x='20' y='20' width='1' height='1'/>");
        dense.append("<rect x='99' y='50' width='4' height='1'/>");
        FinalCutSheetSafety.Report beyondBudget=inspect(dense.toString());
        ok(beyondBudget.unverified>0&&beyondBudget.outsideArtboard,
            "Complexity exhaustion hid the artboard escape");
        String small=svg(279.4,203.2,"<rect x='10' y='10' width='5' height='5'/>");
        for(int device:new int[]{OutputDeviceProfile.CRICUT,OutputDeviceProfile.SILHOUETTE_BASIC}){
            shouldRejectMismatchedArtboards(device);
            byte[] archive=zip(device,false,small,small);
            String guide=entry(archive,"physical-artboards.csv");
            ok(guide.contains("279.400,203.200"),"Physical 11-in artboard was not recorded");
            ok(guide.split("\\n").length==3,"Physical artboard guide missing layer");
            ok(entry(archive,"cut-sheet-safety.csv").contains("INSPECTED"),
                "Complete source geometry not inspected");
            if(device==OutputDeviceProfile.SILHOUETTE_BASIC){
                String dxf=entry(archive,"01-art.dxf");
                ok(dxf.contains("AC1012"),"R13 cut path missing");
                ok(entry(archive,"dimensions-dxf.txt").contains("279.400 x 203.200 mm"),
                    "Incorrect 11-inch DXF reference dimensions");
            }
        }
        // Two completely different contours, but the registration circles at
        // fixed physical coordinates must land identically in both CAD sheets.
        String marks="<circle cx='8' cy='8' r='1.5'/><circle cx='271.4' cy='8' r='1.5'/>"+
            "<circle cx='8' cy='195.2' r='1.5'/><circle cx='271.4' cy='195.2' r='1.5'/>";
        String a=svg(279.4,203.2,marks+"<rect x='80' y='70' width='20' height='20'/>");
        String b=svg(279.4,203.2,marks+"<rect x='130' y='70' width='20' height='20'/>");
        byte[] registration=zip(OutputDeviceProfile.SILHOUETTE_BASIC,false,a,b);
        SvgDxfR13Exporter.SafetyGeometry ga=SvgDxfR13Exporter.safetyGeometry(a);
        SvgDxfR13Exporter.SafetyGeometry gb=SvgDxfR13Exporter.safetyGeometry(b);
        for(int i=0;i<4;i++){
            double[] m=ga.circles.get(i),n=gb.circles.get(i);
            ok(Math.abs(m[0]-n[0])<1e-7&&Math.abs(m[1]-n[1])<1e-7&&Math.abs(m[2]-n[2])<1e-7,
                "Registration marks drift between physical layers");
        }
        ok(entry(registration,"physical-artboards.csv").contains("02-art.dxf"),"Multi-sheet calibration manifest missing");
        // Invalid reference-only SVG must be rejected by both output profiles
        // as a controlled IO export failure, with the original cause retained.
        // A raw IllegalArgumentException escaping the ZIP writer used to crash
        // this Milestone A1 host gate and bypass normal export-error handling.
        for(int device:new int[]{OutputDeviceProfile.CRICUT,OutputDeviceProfile.SILHOUETTE_BASIC}){
            boolean rejectsUnknown=false;
            try{zip(device,false,svg(100,100,"<use href='#unsupported'/>") );}
            catch(IOException ex){
                rejectsUnknown=ex.getCause() instanceof IllegalArgumentException
                    &&ex.getMessage()!=null&&ex.getMessage().contains("Unsupported cut-SVG element: use");
            }
            ok(rejectsUnknown,"Unsupported <use> was not safely rejected as IO export failure for "+device);
        }
        System.out.println("PASS Milestone A1: mixed contacts, min gaps, artboard bounds/units, 11-inch multilayer SVG/DXF and registration geometry");
    }
}

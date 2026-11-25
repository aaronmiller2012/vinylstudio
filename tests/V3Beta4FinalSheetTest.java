package com.halftonestudio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

/** Executable final-sheet tests (uses the actual SVG/DXF writer and oracle). */
public final class V3Beta4FinalSheetTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static String svg(String body){return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100mm\" height=\"100mm\" viewBox=\"0 0 100 100\">"+body+"</svg>";}
    private static byte[] archive(String sheet,int device,boolean approved)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)){
            CutSheetZipWriter writer=new CutSheetZipWriter(zip,device,.30f,approved);
            writer.put("01-sheet.svg",sheet);
            writer.finish();
        }
        return bytes.toByteArray();
    }
    private static String entry(byte[] zipBytes,String name)throws Exception{
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(zipBytes))){
            ZipEntry e;while((e=zip.getNextEntry())!=null)
                if(e.getName().equals(name))return new String(zip.readAllBytes(),StandardCharsets.UTF_8);
        }
        throw new AssertionError("Missing ZIP entry "+name);
    }
    static void run()throws Exception{
        String clear=svg("<rect x=\"10\" y=\"10\" width=\"20\" height=\"20\"/>");
        FinalCutSheetSafety.Report good=FinalCutSheetSafety.inspect("L1.svg",clear,.30f);
        check(good.critical==0&&good.unverified==0&&good.contours==1,"Valid rectangle was flagged");
        String intersect=svg("<rect x=\"10\" y=\"10\" width=\"30\" height=\"30\"/>"+
           "<rect x=\"20\" y=\"20\" width=\"30\" height=\"30\"/>");
        FinalCutSheetSafety.Report overlap=FinalCutSheetSafety.inspect("L2.svg",intersect,.30f);
        check(overlap.critical>0,"Overlapping closed contours escaped final-sheet review");
        boolean rejected=false;
        try{archive(intersect,OutputDeviceProfile.CRICUT,false);}catch(IOException expected){rejected=true;}
        check(rejected,"Unreviewed critical geometry was published");
        byte[] approved=archive(intersect,OutputDeviceProfile.CRICUT,true);
        check(entry(approved,"cut-sheet-safety.csv").contains("CONTACT_REVIEW"),"Approved-risk archive lacks review report");
        String circles=svg("<circle cx=\"15\" cy=\"15\" r=\"4\"/><circle cx=\"22\" cy=\"15\" r=\"4\"/>");
        check(FinalCutSheetSafety.inspect("circle.svg",circles,.30f).critical>0,"Touching circles escaped review");
        String pen=svg("<path fill=\"none\" stroke=\"black\" d=\"M 10 10 L 90 90\"/>");
        FinalCutSheetSafety.Report drawing=FinalCutSheetSafety.inspect("pen.svg",pen,.30f);
        check("DRAW_ONLY".equals(drawing.status()),"Open pen path incorrectly identified as blade contour");
        String mixed=svg("<circle cx=\"12\" cy=\"12\" r=\"2\"/>"+
           "<rect x=\"40\" y=\"40\" width=\"10\" height=\"10\"/>");
        check(FinalCutSheetSafety.inspect("mixed.svg",mixed,.30f).unverified==0,
             "Safe mixed primitive clearance should be inspected");
        String unsupported=svg("<use href=\"#unexpected\"/>");
        check(FinalCutSheetSafety.inspect("unsupported.svg",unsupported,.30f).unverified>0,
            "Unsupported SVG falsely certified");
        String source="<path d=\"M 10 10 C 45 10 60 90 90 90 L 10 90 Z\"/>";
        byte[] svgZip=archive(svg(source),OutputDeviceProfile.CRICUT,true);
        byte[] dxfZip=archive(svg(source),OutputDeviceProfile.SILHOUETTE_BASIC,true);
        check(entry(svgZip,"01-sheet.svg").contains("C 45 10"),"SVG writer altered curve");
        check(entry(dxfZip,"01-sheet.dxf").contains("AC1012"),"Missing R13 DXF");
        check(entry(svgZip,"cut-sheet-safety.csv").contains("01-sheet.svg"),"SVG review missing");
        check(entry(dxfZip,"cut-sheet-safety.csv").contains("01-sheet.dxf"),"DXF review missing");
        check(entry(dxfZip,"dimensions-dxf.txt").contains("geometry bounds"),"DXF scale guide missing");
        System.out.println("PASS Beta4 final-cut-sheet SVG, DXF, overlaps, circles, open paths, warnings");
    }
    public static void main(String[] args)throws Exception{run();}
}

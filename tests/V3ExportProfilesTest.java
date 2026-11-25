package com.halftonestudio;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Verify the shared safety and output-format boundary across every cut handoff. */
public final class V3ExportProfilesTest {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static String clean(){return "<svg xmlns='http://www.w3.org/2000/svg' width='40mm' height='40mm' viewBox='0 0 40 40'>"+
        "<rect x='5' y='5' width='5' height='5'/><circle cx='24' cy='24' r='3'/></svg>";}
    static String narrow(){return "<svg xmlns='http://www.w3.org/2000/svg' width='40mm' height='40mm' viewBox='0 0 40 40'>"+
        "<rect x='6' y='6' width='0.05' height='12'/></svg>";}
    static Set<String> zip(int profile,String svg,boolean reviewed) throws Exception {
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(buffer)){
            CutSheetZipWriter cuts=new CutSheetZipWriter(z,profile,.254f,reviewed);
            cuts.put("layer.svg",svg);cuts.finish();
        }
        Set<String> entries=new HashSet<>();
        try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(buffer.toByteArray()))){
            ZipEntry e;while((e=z.getNextEntry())!=null){entries.add(e.getName());check(z.readAllBytes().length>0,"empty ZIP member "+e.getName());}
        }
        return entries;
    }
    public static void main(String[] ignored)throws Exception{
        int count=0;
        for(int id=0;id<OutputDeviceProfile.names().length;id++){
            if(id==OutputDeviceProfile.DESKTOP_PRINTER){
                try{zip(id,clean(),false);throw new AssertionError("printer accepted cut ZIP");}
                catch(IllegalArgumentException expected){}
                continue;
            }
            Set<String> names=zip(id,clean(),false);
            String extension=id==OutputDeviceProfile.SILHOUETTE_BASIC?"layer.dxf":"layer.svg";
            check(names.contains(extension),"wrong format for "+OutputDeviceProfile.byId(id).name);
            check(names.contains("cut-sheet-safety.csv"),"missing physical safety report");
            check(names.contains("physical-artboards.csv"),"missing artboards");
            check(names.contains("cut-complexity.csv"),"missing cutter count report");
            check(!names.contains("NOT-CUT-QUALIFIED.txt"),"clean sheet incorrectly warned");
            count++;
        }
        // Cosmetic XML element count limit must not leak from Cricut to other cutters.
        StringBuilder dense=new StringBuilder("<svg width='50mm' height='50mm' viewBox='0 0 50 50'>");
        for(int i=0;i<5001;i++)dense.append("<circle cx='1' cy='1' r='1'/>");
        dense.append("</svg>");
        boolean cricutRejected=false;
        try{CutSvgCompatibility.verify(dense.toString(),OutputDeviceProfile.CRICUT);}catch(IllegalArgumentException expected){cricutRejected=true;}
        check(cricutRejected,"Cricut path limit missing");
        check(CutSvgCompatibility.verify(dense.toString(),OutputDeviceProfile.GENERIC_SVG)==5001,"Generic SVG incorrectly capped");
        Set<String> diagnostic=zip(OutputDeviceProfile.GENERIC_SVG,narrow(),true);
        check(diagnostic.contains("NOT-CUT-QUALIFIED.txt"),"Narrow diagnostic not labeled unsafe");
        boolean blocked=false;
        try{zip(OutputDeviceProfile.GENERIC_SVG,narrow(),false);}catch(IOException expected){blocked=true;}
        check(blocked,"Narrow contour published without review");
        System.out.println("PASS all "+count+" cut handoffs, printer exclusion, SVG/DXF content, Cricut-only path budget and fail-closed warnings");
    }
}

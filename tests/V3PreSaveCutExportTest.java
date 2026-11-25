package com.halftonestudio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Host integration of pre-publication staging and review-only ZIP labeling. */
public final class V3PreSaveCutExportTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static String cleanSvg(){
        return "<svg xmlns='http://www.w3.org/2000/svg' width='40mm' height='40mm' viewBox='0 0 40 40'>"+
            "<rect x='5' y='5' width='6' height='6'/><rect x='20' y='20' width='6' height='6'/></svg>";
    }
    private static String overlappingSvg(){
        return "<svg xmlns='http://www.w3.org/2000/svg' width='40mm' height='40mm' viewBox='0 0 40 40'>"+
            "<rect x='5' y='5' width='6' height='6'/><rect x='6' y='6' width='5' height='5'/></svg>";
    }
    private static Set<String> members(byte[] bytes) throws IOException {
        Set<String> names=new HashSet<>();
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){
            ZipEntry member;while((member=zip.getNextEntry())!=null){
                names.add(member.getName());
                if("NOT-CUT-QUALIFIED.txt".equals(member.getName())){
                    String warning=new String(zip.readAllBytes(),StandardCharsets.UTF_8);
                    check(warning.contains("NOT FULLY INSPECTED")&&warning.contains("not manufacture"),
                        "Diagnostic warning must describe unqualified cut geometry");
                }
                zip.closeEntry();
            }
        }
        return names;
    }
    private static Set<String> stageAndPublish(File cache,boolean forceSourceReview,boolean overlap) throws Exception {
        File destination=new File(cache,"user-selected.zip");
        check(!destination.exists(),"Destination must not exist before safety preparation");
        try(StagedCutZip stage=new StagedCutZip(cache)){
            try(OutputStream out=stage.openStream();ZipOutputStream zip=new ZipOutputStream(out)){
                CutSheetZipWriter writer=new CutSheetZipWriter(zip,OutputDeviceProfile.GENERIC_SVG,.254f,true);
                writer.noteSourceFindings(forceSourceReview);
                writer.put("01-shape.svg",overlap?overlappingSvg():cleanSvg());
                writer.finish();zip.finish();
            }
            stage.verify();
            check(!destination.exists(),"Safety inspection must finish before document creation");
            try(OutputStream out=new FileOutputStream(destination)){stage.copyTo(out);}
            try(InputStream published=new FileInputStream(destination)){stage.verifyPublishedCopy(published);}
            byte[] data=Files.readAllBytes(destination.toPath());
            boolean truncatedRejected=false;
            try{stage.verifyPublishedCopy(new ByteArrayInputStream(new byte[0]));}
            catch(IOException truncated){truncatedRejected=true;}
            check(truncatedRejected,"Provider returning an empty ZIP must be detected after publication");
            byte[] tampered=data.clone();tampered[tampered.length/2]^=1;
            boolean corruptRejected=false;
            try{stage.verifyPublishedCopy(new ByteArrayInputStream(tampered));}
            catch(IOException corrupt){corruptRejected=true;}
            check(corruptRejected,"Tampered published ZIP must be detected");
            check(data.length>100,"Published ZIP must be nonempty");
            Set<String> names=members(data);
            check(names.contains("01-shape.svg")&&names.contains("cut-sheet-safety.csv"),
                "Missing physical sheet/report in published ZIP");
            check(names.contains("NOT-CUT-QUALIFIED.txt")== (forceSourceReview||overlap),
                "Review ZIP must be labeled when source/final geometry is unsafe");
            Files.delete(destination.toPath());
            return names;
        }
    }
    public static void main(String[] args) throws Exception {
        File cache=Files.createTempDirectory("vinyl-presave-gate-").toFile();
        try{
            stageAndPublish(cache,false,false);
            stageAndPublish(cache,true,false);
            stageAndPublish(cache,false,true);
            File destination=new File(cache,"user-selected.zip");
            try(StagedCutZip unready=new StagedCutZip(cache)){
                boolean rejected=false;
                try{unready.verify();}catch(IOException expected){rejected=true;}
                check(rejected,"Unprepared archive must be rejected");
                check(!destination.exists(),"Rejected preflight must not create a destination");
            }
        }finally{
            File[] leftovers=cache.listFiles();if(leftovers!=null)for(File f:leftovers)f.delete();
            cache.delete();
        }
        System.out.println("PASS prepare-before-save, verified standard ZIP, diagnostic notices, rejected unprepared stage");
    }
}

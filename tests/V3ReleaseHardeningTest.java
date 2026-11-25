package com.halftonestudio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.*;

/** Beta 20 fail-closed publication, archive and registration reliability probes.
 * Plain JVM; no Android SDK, cutter hardware or Play Store certification implied.
 */
public final class V3ReleaseHardeningTest {
    private static void check(boolean pass,String detail){if(!pass)throw new AssertionError(detail);}
    private static final byte[] PNG={1,2,3,4};
    private static byte[] project(String json)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)){
            zip.putNextEntry(new ZipEntry("photo.png"));zip.write(PNG);zip.closeEntry();
            zip.putNextEntry(new ZipEntry("settings.json"));zip.write(json.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
        return bytes.toByteArray();
    }
    private static void projectChecks()throws Exception{
        try(ZipInputStream input=new ZipInputStream(new ByteArrayInputStream(project("{\"widthMm\":110}")))){
            check(ProjectArchiveReader.read(input).settings.length>0,"Valid legacy project rejected");
        }
        for(String json:new String[]{"{\"widthMm\":]}","[]","false"}){
            boolean rejected=false;
            try(ZipInputStream input=new ZipInputStream(new ByteArrayInputStream(project(json)))){
                ProjectArchiveReader.read(input);
            }catch(IOException expected){rejected=true;}
            check(rejected,"Invalid settings accepted by in-memory project reader: "+json);
        }
        Thread.currentThread().interrupt();
        boolean cancelled=false;
        try(ZipInputStream input=new ZipInputStream(new ByteArrayInputStream(project("{}")))){
            ProjectArchiveReader.read(input);
        }catch(IOException expected){cancelled=expected.getMessage().contains("canceled");}
        finally{Thread.interrupted();}
        check(cancelled,"Legacy project reader ignored cancellation");
        boolean blank=false;
        try(ZipOutputStream out=new ZipOutputStream(new ByteArrayOutputStream())){
            StudioProjectArchiveWriter.write(out, image->{try{image.write(PNG);return true;}catch(IOException e){throw new UncheckedIOException(e);}},image->true,"{}");
        }catch(IOException expected){blank=expected.getMessage().contains("empty original.png");}
        check(blank,"Writer accepted empty encoded original");
        System.out.println("PASS C project JSON consistency, cancellation and empty-image rejection");
    }
    private static byte[] stage(Path dir, boolean mixed)throws Exception{
        try(StagedCutZip staged=new StagedCutZip(dir.toFile())){
            try(ZipOutputStream out=new ZipOutputStream(staged.openStream())){
                out.putNextEntry(new ZipEntry("L01.svg"));
                out.write("<svg width='1mm' height='1mm'><rect/></svg>".getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
                if(mixed){
                    out.putNextEntry(new ZipEntry("L02.dxf"));
                    out.write("0\r\nSECTION\r\n2\r\nHEADER\r\n9\r\n$ACADVER\r\n1\r\nAC1012\r\n0\r\nENDSEC\r\n0\r\nEOF\r\n".getBytes(StandardCharsets.US_ASCII));
                    out.closeEntry();
                }
            }
            ByteArrayOutputStream destination=new ByteArrayOutputStream();
            boolean premature=false;
            try{staged.copyTo(destination);}catch(IOException expected){premature=true;}
            check(premature&&destination.size()==0,"Unverified ZIP was published to a destination");
            if(mixed){
                boolean rejected=false;
                try{staged.verify();}catch(IOException expected){rejected=expected.getMessage().contains("Mixed SVG/DXF");}
                check(rejected,"Ambiguous mixed-format cut ZIP published");
                boolean bypass=false;try{staged.copyTo(destination);}catch(IOException expected){bypass=true;}
                check(bypass&&destination.size()==0,"Previously rejected cut archive was published");
            }else{
                staged.verify();staged.copyTo(destination);
                check(destination.size()>0,"Verified staged cut archive was lost");
            }
            return destination.toByteArray();
        }
    }
    private static void stageChecks()throws Exception{
        Path directory=Files.createTempDirectory("v3-release-stage-");
        try{
            check(stage(directory,false).length>0,"Valid cut stage rejected");
            stage(directory,true);
        }finally{try(java.util.stream.Stream<Path> files=Files.list(directory)){
            files.forEach(f->{try{Files.deleteIfExists(f);}catch(IOException ignored){}});
            }Files.deleteIfExists(directory);}
        System.out.println("PASS A staged export verified-before-copy; mixed SVG/DXF prohibited");
    }
    private static String manyCircles(int n){
        StringBuilder s=new StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' width='2000mm' height='2000mm' viewBox='0 0 2000 2000'>");
        for(int i=0;i<n;i++)s.append("<circle cx='").append(3+(i%90)*10).append("' cy='").append(3+(i/90)*10).append("' r='1'/>");
        return s.append("</svg>").toString();
    }
    private static void safetyChecks()throws Exception{
        String svg=manyCircles(12001);  // beyond the bounded circle inspection budget
        FinalCutSheetSafety.Report report=FinalCutSheetSafety.inspect("many.svg",svg,.254f);
        check(report.unverified>0,"Budget exhausted without an UNVERIFIED finding");
        boolean failedClosed=false;
        try(ZipOutputStream out=new ZipOutputStream(new ByteArrayOutputStream())){
            CutSheetZipWriter writer=new CutSheetZipWriter(out,OutputDeviceProfile.GENERIC_SVG,.254f,false);
            writer.put("many.svg",svg);
            try{writer.finish();}catch(IOException expected){failedClosed=expected.getMessage().contains("incomplete");}
        }
        check(failedClosed,"Unverified final physical geometry exported without acknowledgement");
        try(ZipOutputStream out=new ZipOutputStream(new ByteArrayOutputStream())){
            CutSheetZipWriter writer=new CutSheetZipWriter(out,OutputDeviceProfile.GENERIC_SVG,.254f,true);
            writer.put("many.svg",svg);writer.finish();
        }
        System.out.println("PASS A incomplete geometry refuses unreviewed publication; review recorded");
    }
    private static void cancellationChecks(){
        boolean[] source=new boolean[128*128];Arrays.fill(source,true);
        Thread.currentThread().interrupt();
        boolean cancelled=false;
        try{BoundedBinaryMaskPreview.compose(128,128,source,8);}
        catch(java.util.concurrent.CancellationException expected){cancelled=true;}
        finally{Thread.interrupted();}
        check(cancelled,"Cancelled preview computed a superseded image");
        System.out.println("PASS C cancelled preview rejected");
    }
    public static void main(String[] args)throws Exception {
        projectChecks();stageChecks();safetyChecks();cancellationChecks();
        System.out.println("PASS v3 release hardening host gate");
    }
}

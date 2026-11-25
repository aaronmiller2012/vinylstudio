package com.halftonestudio;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public final class RC82IntegrityTest {
    private static void ok(boolean yes,String label){if(!yes)throw new AssertionError(label);}
    private static float[] xy(float[] m,int x,int y){return new float[]{m[0]*x+m[1]*y,m[3]*x+m[4]*y};}
    private static void assertXy(int orientation,int x,int y){float[] a=xy(ExifOrientationTransform.matrix(orientation),3,5);ok(a[0]==x&&a[1]==y,"EXIF "+orientation);}
    private static byte[] zip(String settings) throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zos=new ZipOutputStream(bytes)){
            StudioProjectArchiveWriter.write(zos,output->{try{output.write(new byte[]{11,22,33});return true;}catch(IOException e){return false;}},
                output->{try{output.write(new byte[]{44,55,66});return true;}catch(IOException e){return false;}},settings);
        }
        return bytes.toByteArray();
    }
    public static void main(String[] unused) throws Exception{
        ok(PhotoImportSizing.sampleSize(4000,3000,1800)==2,"4000px sampled by two");
        ok(PhotoImportSizing.sampleSize(8000,6000,1800)==4,"8000px sampled by four");
        ok(Arrays.equals(PhotoImportSizing.finalSize(2000,1500,1800),new int[]{1800,1350}),
            "photo is scaled precisely to 1800 pixels");
        ok(PhotoImportSizing.sampleSize(1000,750,1800)==1,"small photo not needlessly sampled");
        ok(ExifOrientationTransform.matrix(1)==null,"EXIF identity");
        ok(ExifOrientationTransform.matrix(0)==null,"unknown EXIF");
        assertXy(2,-3,5);assertXy(3,-3,-5);assertXy(4,3,-5);
        assertXy(5,5,3);assertXy(6,-5,3);assertXy(7,-5,-3);assertXy(8,5,-3);
        String migrated="u1\\nu2\\nu3\\nu4\\nu5\\nu6";
        String recent=RecentProjectUris.remember("new",migrated);
        ok(recent.equals("new\nu1\nu2\nu3\nu4"),"legacy migration and five-item cap: "+recent);
        recent=RecentProjectUris.remember("u2",recent);
        ok(recent.equals("u2\nnew\nu1\nu3\nu4"),"dedupe and recency");
        ok(RecentProjectUris.remember("",recent).equals(recent),"ignore empty entries");
        byte[] archive=zip("{\"works\":true}");
        try(ZipInputStream zis=new ZipInputStream(new ByteArrayInputStream(archive))){
            ProjectArchiveReader.ProjectEntries read=ProjectArchiveReader.read(zis);
            ok(Arrays.equals(read.photo,new byte[]{11,22,33}),"roundtrip photo");
            ok(Arrays.equals(read.original,new byte[]{44,55,66}),"roundtrip original");
        }
        try{zip("a".repeat(1_000_001));throw new AssertionError("oversized settings accepted");}
        catch(IOException expected){ok(expected.getMessage().contains("1 MB"),"settings message");}
        try{
            ByteArrayOutputStream o=new ByteArrayOutputStream();
            try(ZipOutputStream zos=new ZipOutputStream(o)){
                StudioProjectArchiveWriter.write(zos,out->{
                    try{byte[] block=new byte[65536];for(int k=0;k<600;k++)out.write(block);return true;}
                    catch(IOException e){return false;}
                },out->true,"{}");
            }
            throw new AssertionError("oversized image accepted");
        }catch(IOException expected){ok(expected.getMessage().contains("Cannot encode") ||
                expected.getMessage().contains("32 MB"),"image limit message");}
        VinylEngine.Settings settings=VinylEngine.Settings.defaults();
        settings.marks=new float[][]{{40,40},{42,42},{-4,-4},{160,160}};
        try{RegistrationMarks.layout(settings.widthMm,settings.widthMm,settings.markSizeMm,
              settings.registrationClearanceMm,settings.marks,8f);
            throw new AssertionError("overlapping registration not rejected");
        }catch(IllegalArgumentException expected){ok(expected.getMessage().contains("overlap"),"mark safety preserved");}
        System.out.println("PASS RC82 EXIF 1-8, URI history migration, bounded archive writer, and mark safety");
    }
}

package com.halftonestudio;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.lang.reflect.*;

/** Beta 2 executable safety corpus: semantic ZIP roles, no temp leak,
 * rotation-invariant narrow islands and in-app export review classification. */
public final class V3Beta2SafetyTest {
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static final byte[] PNG=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAFUlEQVR4nGPUSNnCwMDAwMDAxAADABYwAUTo8FNRAAAAAElFTkSuQmCC");
    static final byte[] SETTINGS="{\"schema\":3}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    static byte[] jpg()throws Exception {
        BufferedImage image=new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        check(ImageIO.write(image,"jpg",out),"No host JPEG encoder");
        return out.toByteArray();
    }
    static byte[] zip(String[] names,byte[][] bytes)throws Exception {
        ByteArrayOutputStream result=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(result)){
            for(int i=0;i<names.length;i++){
                z.putNextEntry(new ZipEntry(names[i]));z.write(bytes[i]);z.closeEntry();
            }
        }
        return result.toByteArray();
    }
    static int countSpools(Path dir)throws Exception{
        try(java.util.stream.Stream<Path> stream=Files.list(dir)){
            return (int)stream.filter(p->p.getFileName().toString().startsWith("vinyl-v3-reopen-")).count();
        }
    }
    static boolean readerAccepts(byte[] archive)throws Exception{
        try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(archive))){
            ProjectArchiveReader.read(z);return true;
        }catch(IOException expected){return false;}
    }
    static boolean stagedAccepts(byte[] archive,Path dir)throws Exception{
        try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(archive));
            ProjectArchiveReader.StagedProject stage=ProjectArchiveReader.readToFiles(z,dir.toFile())){
            return true;
        }catch(IOException expected){return false;}
    }
    static boolean libraryAccepts(byte[] archive,Path dir, String method)throws Exception{
        Path file=dir.resolve("corpus.zip");Files.write(file,archive);
        Method verify=StudioProjectLibrary.class.getDeclaredMethod(method,File.class);
        verify.setAccessible(true);
        try{verify.invoke(null,file.toFile());return true;}
        catch(InvocationTargetException cause){
            if(cause.getCause() instanceof IOException)return false;
            throw cause;
        }finally{Files.deleteIfExists(file);}
    }
    static void corpus(String label,String[] names,byte[][] bytes,boolean accepted,Path dir)throws Exception{
        byte[] archive=zip(names,bytes);
        check(readerAccepts(archive)==accepted,label+": legacy reader disagreement");
        check(stagedAccepts(archive,dir)==accepted,label+": streaming reader disagreement");
        check(countSpools(dir)==0,label+": staging leaked temporary image");
        for(String method:new String[]{"verifyArchive","verifyArchivePayload"})
            check(libraryAccepts(archive,dir,method)==accepted,label+": "+method+" disagreement");
    }
    static float[] rect(float length,float width,double degrees){
        double a=Math.toRadians(degrees),c=Math.cos(a),s=Math.sin(a);
        float[] p=new float[8];
        double[] x={0,length,length,0},y={0,0,width,width};
        for(int i=0;i<4;i++){
            p[2*i]=(float)(50+x[i]*c-y[i]*s);
            p[2*i+1]=(float)(50+x[i]*s+y[i]*c);
        }
        return p;
    }
    static List<StyleCutGeometryPreflight.Warning> checkWidth(float[] poly){
        StyleKit.Result result=new StyleKit.Result(130,130);
        StyleKit.Layer layer=new StyleKit.Layer("Cut",0xff000000);layer.loops.add(poly);
        result.layers.add(layer);
        return StyleCutGeometryPreflight.inspect(result,.30f,20);
    }
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("vinyl-beta2-corpus-");
        try{
            byte[] JPEG=jpg();
            corpus("valid png",new String[]{"photo.png","settings.json"},new byte[][]{PNG,SETTINGS},true,dir);
            corpus("valid jpg",new String[]{"photo.jpg","settings.json"},new byte[][]{JPEG,SETTINGS},true,dir);
            corpus("valid mixed",new String[]{"photo.png","original.jpg","settings.json"},new byte[][]{PNG,JPEG,SETTINGS},true,dir);
            corpus("photo role collision",new String[]{"photo.png","photo.jpg","settings.json"},new byte[][]{PNG,JPEG,SETTINGS},false,dir);
            corpus("original role collision",new String[]{"photo.png","original.png","original.jpg","settings.json"},new byte[][]{PNG,PNG,JPEG,SETTINGS},false,dir);
            corpus("missing photo",new String[]{"settings.json"},new byte[][]{SETTINGS},false,dir);
            corpus("missing settings",new String[]{"photo.png"},new byte[][]{PNG},false,dir);
            corpus("traversal",new String[]{"photo.png","../x","settings.json"},new byte[][]{PNG,PNG,SETTINGS},false,dir);
            for(int angle=0;angle<=90;angle+=5){
                List<StyleCutGeometryPreflight.Warning> narrow=checkWidth(rect(40,.10f,angle));
                check(!narrow.isEmpty(),"0.10mm sliver escaped at "+angle+" degrees");
                check(checkWidth(rect(40,.30f,angle)).isEmpty(),"0.30mm legal bar flagged at "+angle);
                check(checkWidth(rect(40,2f,angle)).isEmpty(),"2mm legal bar flagged at "+angle);
                CutSafetyReviewPolicy.Decision narrowReview=CutSafetyReviewPolicy.assess(narrow);
                check(narrowReview.narrow>0&&narrowReview.critical==0,"Sliver severity misclassified");
            }
            StyleKit.Result result=new StyleKit.Result(130,130);
            StyleKit.Layer layer=new StyleKit.Layer("Cut",0xff000000);
            layer.loops.add(StyleKit.rect(2,10,18,26));layer.loops.add(StyleKit.rect(10,2,26,18));
            result.layers.add(layer);
            CutSafetyReviewPolicy.Decision crossing=CutSafetyReviewPolicy.assess(
                StyleCutGeometryPreflight.inspect(result,.6f,20));
            check(crossing.critical>0&&crossing.dangerous(),"Crossings must require conscious acknowledgement");
            CutSafetyReviewPolicy.Decision unknown=CutSafetyReviewPolicy.assess(
                Collections.singletonList(new StyleCutGeometryPreflight.Warning(0,0,0,
                    "Detailed local-width scan exceeded complexity budget; cut safety is not verified")));
            check(unknown.unverified==1&&unknown.dangerous(),"Incomplete scans must not be certified safe");
            java.util.ArrayList<StyleCutGeometryPreflight.Warning> capped=new java.util.ArrayList<>();
            for(int i=0;i<500;i++)capped.add(new StyleCutGeometryPreflight.Warning(0,0,0,
                "Check narrow or tiny vinyl contour"));
            check(CutSafetyReviewPolicy.assess(capped,500).unverified==1,
                "Filled report cap incorrectly claimed complete verification");
            System.out.println("PASS V3 Beta2: 8 ZIP cases across 4 verifiers; temp cleanup; 19 rotations and controls; risk categories");
        }finally{
            try(java.util.stream.Stream<Path> walk=Files.walk(dir)){
                walk.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});
            }
        }
    }
}

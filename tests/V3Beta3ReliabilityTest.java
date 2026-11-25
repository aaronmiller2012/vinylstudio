package com.halftonestudio;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Executable safety, atomic-pair and immutable-source regressions. */
public final class V3Beta3ReliabilityTest {
    static void yes(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static float[] quad(float length,float width,int segmentsPerEdge,double angle){
        double[][] corners={{0,0},{length,0},{length,width},{0,width}};
        double radians=Math.toRadians(angle),c=Math.cos(radians),s=Math.sin(radians);
        float[] p=new float[8*segmentsPerEdge];int i=0;
        for(int k=0;k<4;k++)for(int n=0;n<segmentsPerEdge;n++){
            double t=(double)n/segmentsPerEdge;
            double x=corners[k][0]*(1-t)+corners[(k+1)%4][0]*t;
            double y=corners[k][1]*(1-t)+corners[(k+1)%4][1]*t;
            p[i++]=(float)(50+x*c-y*s);p[i++]=(float)(50+x*s+y*c);
        }
        return p;
    }
    static int warnings(float[] poly,float min){
        StyleKit.Result result=new StyleKit.Result(250,250);
        StyleKit.Layer layer=new StyleKit.Layer("vinyl",0);layer.loops.add(poly);result.layers.add(layer);
        return StyleCutGeometryPreflight.inspect(result,min,500).size();
    }
    static float[] circleContour(float cx,float cy,float radius,int vertices){
        float[] p=new float[vertices*2];
        for(int i=0;i<vertices;i++){
            double a=2*Math.PI*i/vertices;
            p[i*2]=(float)(cx+radius*Math.cos(a));
            p[i*2+1]=(float)(cy+radius*Math.sin(a));
        }
        return p;
    }
    static VinylEngine.Result standard(VinylEngine.Shape... shapes){
        VinylEngine.Settings s=new VinylEngine.Settings();s.registration=false;
        s.layers.add(new VinylEngine.Layer("Black",0xff000000));
        VinylEngine.Result r=new VinylEngine.Result(s,100,0,new float[0][]);
        r.shapes.add(new ArrayList<>(Arrays.asList(shapes)));return r;
    }
    static String data(File f)throws IOException{return Files.readString(f.toPath());}
    static void geometry(){
        for(int angle:new int[]{0,15,30,45,60,90}){
            for(int density:new int[]{1,4,16,200,500}){
                yes(warnings(quad(100,100,density,angle),.30f)==0,
                    "Valid subdivided square flagged: angle="+angle+" density="+density);
                yes(warnings(quad(40,.10f,density,angle),.30f)>0,
                    "Thin ribbon missed: angle="+angle+" density="+density);
                yes(warnings(quad(40,.30f,density,angle),.30f)==0,
                    "Legal-width ribbon flagged: angle="+angle+" density="+density);
            }
        }
        for(int vertices:new int[]{32,256,1000,2000}){
            yes(warnings(circleContour(80,80,25,vertices),.30f)==0,
                "Valid smooth circle flagged as a narrow neck: vertices="+vertices);
        }
        VinylEngine.Result crossing=standard(
            VinylEngine.Shape.polygon(1,1,12,1,12,12,1,12),
            VinylEngine.Shape.polygon(7,7,18,7,18,18,7,18));
        yes(CutSafetyReviewPolicy.assess(StandardCutSafety.inspect(crossing,.30f,100)).critical>0,
            "Overlapping standard vinyl shapes passed geometry review");
        boolean rejected=false;
        try{StandardCutSafety.inspect(standard(VinylEngine.Shape.polygon(1,1,12,12,1,12,12,1)),.30f,100);}
        catch(IllegalArgumentException expected){rejected=true;}
        yes(rejected,"Self-crossing standard polygon escaped preflight");
        yes(StandardCutSafety.inspect(standard(VinylEngine.Shape.circle(10,10,1),
            VinylEngine.Shape.circle(20,10,1)),.30f,100).isEmpty(),"Separated standard circles flagged");
        yes(CutSafetyReviewPolicy.assess(StandardCutSafety.inspect(
            standard(VinylEngine.Shape.circle(10,10,1),VinylEngine.Shape.circle(11,10,1)),.30f,100)).critical>0,
            "Overlapping standard circles passed preflight");
    }
    static void persistence(File dir)throws Exception {
        ProjectPhotoStore store=new ProjectPhotoStore();
        android.graphics.Bitmap p1=new android.graphics.Bitmap("EDITED_A",false);
        android.graphics.Bitmap o1=new android.graphics.Bitmap("ORIGINAL_A",false);
        store.save(dir,p1,o1,1);
        yes(data(PhotoRevisionStore.active(dir).photo).equals("EDITED_A"),"Initial photo not committed");
        boolean failed=false;
        try{store.save(dir,new android.graphics.Bitmap("EDITED_B",true),
                    new android.graphics.Bitmap("ORIGINAL_B",false),2);}
        catch(IOException expected){failed=true;}
        yes(failed,"Injected second-photo failure did not fail");
        PhotoRevisionStore.Pair recovered=PhotoRevisionStore.active(dir);
        yes(data(recovered.photo).equals("EDITED_A")&&data(recovered.original).equals("ORIGINAL_A"),
            "Partial transaction replaced a healthy image pair");
        store.save(dir,new android.graphics.Bitmap("EDITED_C",false),
            new android.graphics.Bitmap("ORIGINAL_C",false),3);
        yes(data(PhotoRevisionStore.active(dir).photo).equals("EDITED_C"),"New pair missing");
        // Corrupt current image: the manifest retains the preceding pair.
        PhotoRevisionStore.active(dir).photo.delete();
        yes(data(PhotoRevisionStore.active(dir).photo).equals("EDITED_A"),"Previous revision not recovered");
        store.save(dir,new android.graphics.Bitmap("EDITED_D",false),
            new android.graphics.Bitmap("ORIGINAL_D",false),4);
        PhotoRevisionStore.active(dir).photo.delete();
        yes(data(PhotoRevisionStore.active(dir).photo).equals("EDITED_A"),
            "Valid fallback revision lost after new commit followed a corrupt active revision");
        ProjectPhotoStore.clear(dir);
    }
    static void snapshot(File dir)throws Exception{
        File original=new File(dir,"editing-archive-original.png");
        Files.writeString(original.toPath(),"SOURCE_A");
        try(ProjectSourceSnapshot captured=ProjectSourceSnapshot.capture(dir,original,null)){
            Files.writeString(original.toPath(),"SOURCE_B");
            yes(data(captured.archiveOriginal).equals("SOURCE_A"),"Export source remains mutable");
            yes(captured.archiveOriginal.getName().endsWith(".png"),"Encoded format identity changed");
        }
        // Regression for A2-03: actual ZIP writer must consume the captured
        // bytes rather than reopening the mutable global cache path.
        byte[] image=Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAFUlEQVR4nGPUSNnCwMDAwMDAxAADABYwAUTo8FNRAAAAAElFTkSuQmCC");
        Files.write(original.toPath(),image);
        try(ProjectSourceSnapshot captured=ProjectSourceSnapshot.capture(dir,original,null)){
            Files.writeString(original.toPath(),"REPLACED_BY_OTHER_PROJECT");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(ZipOutputStream zip=new ZipOutputStream(bytes)){
                StudioProjectArchiveWriter.write(zip,out->{try{out.write(image);return true;}
                    catch(IOException error){return false;}},out->{try{out.write(image);return true;}
                    catch(IOException error){return false;}},"{}",null,captured.archiveOriginal);
            }
            byte[] embedded=null;
            try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))){
                ZipEntry entry;
                while((entry=zip.getNextEntry())!=null)
                    if("original.png".equals(entry.getName()))embedded=zip.readAllBytes();
            }
            yes(Arrays.equals(image,embedded),"Archive used replaced global original, not captured source");
        }
        yes(Arrays.stream(dir.listFiles()).noneMatch(f->f.getName().startsWith("vinyl-project-snapshot-")),
            "Export snapshot leaked after close");
        yes(ProjectSaveGate.begin(),"Could not begin first save");
        yes(!ProjectSaveGate.begin()&&ProjectSaveGate.busy(),"Concurrent project save accepted");
        ProjectSaveGate.end();yes(!ProjectSaveGate.busy(),"Process-scoped save gate not released");
    }
    static void encodedOriginalRecovery(File dir)throws Exception{
        byte[] bytes=new byte[64];Arrays.fill(bytes,(byte)7);
        File verified=new File(dir,"verified-source.bin");Files.write(verified.toPath(),bytes);
        ProjectEncodedOriginal.Stage jpeg=ProjectEncodedOriginal.stage(dir,verified,"jpg");
        ProjectEncodedOriginal.commit(dir,jpeg);
        yes(ProjectEncodedOriginal.active(dir).getName().endsWith(".jpg"),"JPEG revision lost");
        ProjectEncodedOriginal.Stage png=ProjectEncodedOriginal.stage(dir,verified,"png");
        ProjectEncodedOriginal.commit(dir,png);
        yes(ProjectEncodedOriginal.active(dir).getName().endsWith(".png"),"PNG revision not selected");
        // An interrupted cleanup can leave both file extensions. The commit
        // marker, not filename order or modification time, must select PNG.
        Files.write(new File(dir,"editing-archive-original.jpg").toPath(),bytes);
        yes(ProjectEncodedOriginal.active(dir).getName().endsWith(".png"),"Stale sibling displaced committed PNG");
        Files.delete(new File(dir,"editing-archive-original.active").toPath());
        yes(ProjectEncodedOriginal.active(dir)==null,"Ambiguous legacy dual-format cache was guessed");
        ProjectEncodedOriginal.clear(dir);Files.delete(verified.toPath());
    }
    public static void main(String[] args)throws Exception{
        Path tmp=Files.createTempDirectory("vinyl-v3-beta3-");
        try{
            geometry();
            File photos=tmp.resolve("photos").toFile();yes(photos.mkdir(),"Photo test directory");
            persistence(photos);snapshot(tmp.toFile());encodedOriginalRecovery(tmp.toFile());
            System.out.println("PASS Beta3: rotated/subdivided widths, standard contour and circle contacts, atomic photo pairs, immutable snapshots");
        }finally{
            try(java.util.stream.Stream<Path> stream=Files.walk(tmp)){
                stream.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});
            }
        }
    }
}

package com.halftonestudio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

public final class RC173W4CorrectnessTest {
    private static void yes(boolean result,String label){if(!result)throw new AssertionError(label);}
    static final byte[] PNG=Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAFUlEQVR4nGPUSNnCwMDAwMDAxAADABYwAUTo8FNRAAAAAElFTkSuQmCC");
    static String svg(String d){return "<svg width='100mm' height='100mm' viewBox='0 0 100 100'>"+
        "<path fill='none' stroke='black' d='"+d+"'/></svg>";}
    static List<double[]> vertices(String dxf){
        String[] lines=dxf.split("\r?\n");List<double[]> pts=new ArrayList<>();
        for(int i=0;i+1<lines.length;i+=2)if(lines[i].equals("0")&&lines[i+1].equals("VERTEX")){
            double x=Double.NaN,y=Double.NaN;
            for(int j=i+2;j+1<lines.length;j+=2){
                if(lines[j].equals("0"))break;
                if(lines[j].equals("10"))x=Double.parseDouble(lines[j+1]);
                if(lines[j].equals("20"))y=Double.parseDouble(lines[j+1]);
            }
            yes(Double.isFinite(x)&&Double.isFinite(y),"Malformed DXF vertex");pts.add(new double[]{x,y});
        }
        return pts;
    }
    static double distance(double px,double py,double[] a,double[] b){
        double dx=b[0]-a[0],dy=b[1]-a[1],den=dx*dx+dy*dy;
        double t=den==0?0:Math.max(0,Math.min(1,((px-a[0])*dx+(py-a[1])*dy)/den));
        return Math.hypot(px-a[0]-t*dx,py-a[1]-t*dy);
    }
    static void curve(String svg,double[] x,double[] y){
        SvgDxfR13Exporter.Result out;
        try{out=SvgDxfR13Exporter.convert(svg);}catch(IOException e){throw new AssertionError(e);}
        List<double[]> points=vertices(out.dxf);
        yes(points.size()>2,"Curve flattened to a short straight segment");
        double maxDeviation=0;
        for(int i=0;i<=5000;i++){
            double t=i/5000.0,q=1-t;
            double px=q*q*q*x[0]+3*q*q*t*x[1]+3*q*t*t*x[2]+t*t*t*x[3];
            double py=100-(q*q*q*y[0]+3*q*q*t*y[1]+3*q*t*t*y[2]+t*t*t*y[3]);
            double nearest=Double.POSITIVE_INFINITY;
            for(int j=1;j<points.size();j++)nearest=Math.min(nearest,distance(px,py,points.get(j-1),points.get(j)));
            maxDeviation=Math.max(maxDeviation,nearest);
        }
        yes(maxDeviation<=SvgDxfR13Exporter.CURVE_TOLERANCE_MM+0.001,
            "DXF curve error exceeds 0.02mm: "+maxDeviation);
        System.out.println("PASS DXF sampled error <= 0.02mm; vertices="+points.size()+", max error="+maxDeviation);
    }
    static void project(OutputStream out,byte[] png)throws IOException {
        ZipOutputStream zip=new ZipOutputStream(out);
        zip.putNextEntry(new ZipEntry("photo.png"));zip.write(png);zip.closeEntry();
        zip.putNextEntry(new ZipEntry("settings.json"));zip.write("{\"schema\":1}".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        zip.finish();zip.flush();
    }
    public static void main(String[] args)throws Exception {
        String over="M 0 50 C 100 50 100 50 10 50";
        SvgDxfR13Exporter.Result a=SvgDxfR13Exporter.convert(svg(over));
        yes(a.maxXmm>76&&a.vertices>2,"Collinear overshoot missing");
        curve(svg(over),new double[]{0,100,100,10},new double[]{50,50,50,50});
        curve(svg("M 5 5 C 5 95 95 5 95 95"),new double[]{5,5,95,95},new double[]{5,95,5,95});
        curve(svg("M 50 50 C 90 98 5 3 50 50"),new double[]{50,90,5,50},new double[]{50,98,3,50});
        yes(ProjectImageSignature.format(PNG).equals("png"),"Valid PNG rejected");
        try{ProjectImageSignature.format(Arrays.copyOf(PNG,12));throw new AssertionError("Broken PNG accepted");}
        catch(IOException expected){}
        byte[] invalid=PNG.clone();invalid[20]^=1;
        try{ProjectImageSignature.format(invalid);throw new AssertionError("Corrupt PNG IHDR accepted");}
        catch(IOException expected){}
        Path root=Files.createTempDirectory("vinyl-w4-project-");
        try{
            StudioProjectLibrary lib=new StudioProjectLibrary(root.toFile());
            StudioProjectLibrary.Entry entry=lib.save(null,"Portrait","Multilayer",null,out->project(out,PNG));
            yes(new StudioProjectLibrary(root.toFile()).archive(entry.id).isFile(),"Verified project cannot reopen");
            try{lib.save(null,"Invalid","Photo",null,out->project(out,Arrays.copyOf(PNG,12)));
                throw new AssertionError("Invalid image accepted in verified project");
            }catch(IOException expected){}
            yes(lib.list().size()==1,"Corrupt project was published");
            byte[] idatCorrupted=PNG.clone();idatCorrupted[45]^=1; // IDAT payload, NOT ZIP CRC
            try{lib.save(null,"InvalidIDAT","Photo",null,out->project(out,idatCorrupted));
                throw new AssertionError("Bad PNG chunk CRC was published");
            }catch(IOException expected){}
            yes(lib.list().size()==1,"Bad PNG IDAT was published");
            ProjectEncodedOriginal.Stage stage=ProjectEncodedOriginal.stage(root.toFile(),PNG);
            ProjectEncodedOriginal.commit(root.toFile(),stage);
            File cache=ProjectEncodedOriginal.active(root.toFile());
            yes(cache!=null&&Arrays.equals(PNG,Files.readAllBytes(cache.toPath())),"Original bytes changed after cache commit");
            try(ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                ZipOutputStream zip=new ZipOutputStream(bytes);
                StudioProjectArchiveWriter.write(zip,o->{try{o.write(PNG);return true;}catch(IOException e){return false;}},
                    o->{throw new AssertionError("Sampled preview overwritten original");},"{}",null,cache);
                try(ZipInputStream input=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))){
                    ProjectArchiveReader.ProjectEntries got=ProjectArchiveReader.read(input);
                    yes(Arrays.equals(PNG,got.original),"Original encoded bytes not preserved in project resave");
                }
            }
        }finally{Files.walk(root).sorted(Comparator.reverseOrder()).forEach(f->{try{Files.deleteIfExists(f);}catch(IOException ignored){}});}
        CountDownLatch blockerStarted=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(1);
        PhotoPersistenceQueue.execute(()->{blockerStarted.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        yes(blockerStarted.await(3,TimeUnit.SECONDS),"Persistence worker did not start");
        PhotoPersistenceQueue.execute(done::countDown);
        yes(done.getCount()==1,"Queued save executed out of order");
        release.countDown();yes(done.await(3,TimeUnit.SECONDS),"Queued save was discarded");
        System.out.println("PASS RC173-W4 geometry, image integrity, encoded original resave, process photo queue");
    }
}

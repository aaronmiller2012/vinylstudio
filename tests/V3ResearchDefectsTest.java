package com.halftonestudio;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Executable behavioral gates, not source-text checks. Runs without Android. */
public final class V3ResearchDefectsTest {
    private static void ok(boolean pass,String what){if(!pass)throw new AssertionError(what);}
    private static boolean narrow(float[] outline,float min){
        StyleKit.Result r=new StyleKit.Result(100,100);
        StyleKit.Layer l=new StyleKit.Layer("test",0xff000000);
        l.loops.add(outline);r.layers.add(l);
        for(StyleCutGeometryPreflight.Warning w:StyleCutGeometryPreflight.inspect(r,min,40))
            if(w.reason.contains("Local vinyl neck")||w.reason.contains("narrow"))return true;
        return false;
    }
    private static float[] rotated(float[] p,double angle){
        float[] out=new float[p.length];double c=Math.cos(angle),s=Math.sin(angle);
        for(int i=0;i<p.length;i+=2){out[i]=(float)(25+p[i]*c-p[i+1]*s);out[i+1]=(float)(25+p[i]*s+p[i+1]*c);}
        return out;
    }
    private static void mustThrow(Runnable action,String description){
        try{action.run();throw new AssertionError(description);}catch(IllegalArgumentException expected){}
    }
    private static void registration(){
        float[][] extreme={{1900,10},{-10,-10},{110,110},{-10,110}};
        ok(!RegistrationCutGeometry.basic(100,100,2.5f,1,extreme).valid(),"outlier mark accepted");
        mustThrow(()->RegistrationMarks.layout(100,100,2.5f,1,extreme,5),"extreme sheet accepted");
        ok(extreme[0][0]==1900,"invalid stored mark silently moved");
        float[][] good=RegistrationMarks.defaultPositions(100,100,2.5f,1);
        ok(RegistrationCutGeometry.basic(100,100,2.5f,1,good).valid(),"defaults rejected");
        RegistrationMarks.Layout layout=RegistrationMarks.layout(100,100,2.5f,1,good,5);
        ok(layout.paddingMm<40 && layout.positions[0][0]==good[0][0],"safe layout altered");
        ok(RegistrationCutGeometry.clampManual(1900,100,2.5f,1)==125,"manual clamp not enforced");
        ok(RegistrationCutGeometry.clampManual(-1000,100,2.5f,1)==-25,"negative drag clamp not enforced");
        System.out.println("PASS registration: manual bounds, legacy invalid-state visibility, export fail-closed");
    }
    private static void necks(){
        float[] chamfer={.2f,0,59.8f,0,60,.2f,60,59.8f,59.8f,60,.2f,60,0,59.8f,0,.2f};
        for(double rotation:new double[]{0,Math.PI/4,Math.PI/3})
            ok(!narrow(rotated(chamfer,rotation),.3f),"false positive bevel @ "+rotation);
        float[] neck={0,0,20,0,20,9.925f,40,9.925f,40,0,60,0,60,20,40,20,40,10.075f,20,10.075f,20,20,0,20};
        for(double rotation:new double[]{0,Math.PI/4,Math.PI/3})
            ok(narrow(rotated(neck,rotation),.3f),"missed thin neck @ "+rotation);
        float[] sliver={0,0,20,0,20,8,28,8,28,8.12f,20,8.12f,20,20,0,20};
        for(double rotation:new double[]{0,Math.PI/4,Math.PI/3})
            ok(narrow(rotated(sliver,rotation),.3f),"missed thin sliver @ "+rotation);
        System.out.println("PASS chamfer no warnings; rotated 0.15mm neck and 0.12mm sliver still detected");
    }
    private static void original()throws Exception{
        File dir=Files.createTempDirectory("vinyl-source-state-").toFile();
        File png=new File(dir,"editing-archive-original.png"),jpg=new File(dir,"editing-archive-original.jpg"),active=new File(dir,"editing-archive-original.active");
        try{
            ok(ProjectEncodedOriginal.provenance(dir)==ProjectEncodedOriginal.Provenance.MISSING,"missing state");
            Files.write(png.toPath(),"fake-image-1".getBytes(StandardCharsets.US_ASCII));
            ok(ProjectEncodedOriginal.provenance(dir)==ProjectEncodedOriginal.Provenance.SELECTED,"legacy single original");
            ok(ProjectEncodedOriginal.requireSelected(dir,true).equals(png),"legacy fallback");
            Files.write(jpg.toPath(),"fake-image-2".getBytes(StandardCharsets.US_ASCII));
            ok(ProjectEncodedOriginal.provenance(dir)==ProjectEncodedOriginal.Provenance.AMBIGUOUS,"ambiguity not recognized");
            try{ProjectEncodedOriginal.requireSelected(dir,true);throw new AssertionError("ambiguous save allowed");}
            catch(IOException expected){ok(expected.getMessage().contains("reopen"),"no recovery instruction");}
            Files.write(active.toPath(),"jpg".getBytes(StandardCharsets.US_ASCII));
            ok(ProjectEncodedOriginal.requireSelected(dir,true).equals(jpg),"explicit selection ignored");
            Files.write(active.toPath(),"bad".getBytes(StandardCharsets.US_ASCII));
            ok(ProjectEncodedOriginal.provenance(dir)==ProjectEncodedOriginal.Provenance.CORRUPT,"bad marker not corrupt");
            try{ProjectEncodedOriginal.requireSelected(dir,true);throw new AssertionError("corrupt save allowed");}
            catch(IOException expected){}
            ok(ProjectEncodedOriginal.requireSelected(dir,false)==null,"new photo incorrectly requires archive source");
        }finally{png.delete();jpg.delete();active.delete();dir.delete();}
        System.out.println("PASS encoded original: missing/ambiguous/selected/corrupt and fail-closed save");
    }
    private static void dxf(){
        String text=DxfR13Skeleton.PREAMBLE;
        ok(text.contains("$ACADVER\r\n1\r\nAC1012"),"DXF not R13");
        ok(text.contains("$INSUNITS\r\n70\r\n4\r\n"),"DXF header is not millimeters");
        ok(!text.contains("$INSUNITS\r\n70\r\n6\r\n"),"DXF erroneously declares meters");
        ok(OutputDeviceProfile.handoffText(OutputDeviceProfile.SILHOUETTE_BASIC).contains("100.000 mm"),
            "Missing external 100mm calibration instruction");
        System.out.println("PASS R13 DXF millimeter metadata + calibration warning");
    }
    private static void queue()throws Exception{
        StudioRenderController c=new StudioRenderController();
        CountDownLatch inside=new CountDownLatch(1),release=new CountDownLatch(1),saved=new CountDownLatch(1);
        try{
            c.cutPreparation().execute(()->{
                inside.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}
            });
            ok(inside.await(2,TimeUnit.SECONDS),"preflight did not start");
            c.io().execute(saved::countDown);
            ok(saved.await(2,TimeUnit.SECONDS),"disk IO blocked by preflight");
        }finally{release.countDown();c.close();}
        System.out.println("PASS cut preflight cannot block queued durable IO");
    }
    public static void main(String[] args)throws Exception{
        registration();necks();original();dxf();queue();
    }
}

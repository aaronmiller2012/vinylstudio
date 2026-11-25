package com.halftonestudio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

public final class StageTwoFoundationTest {
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        testArchive();testCancellation();testLanes();testShutdown();
        System.out.println("PASS: Stage 2 render lanes + project writer host tests");
    }
    private static void testArchive() throws Exception {
        byte[] png={1,2,3,4};byte[] original={8,7,6};String json="{\"schema\":14,\"name\":\"portrait\"}";
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)) {
            StudioProjectArchiveWriter.write(zip,out->{try{out.write(png);return true;}catch(IOException e){return false;}},
                out->{try{out.write(original);return true;}catch(IOException e){return false;}},json);
        }
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            check("photo.png".equals(zip.getNextEntry().getName()),"photo entry order");
            check(Arrays.equals(zip.readAllBytes(),png),"photo data");
            check("original.png".equals(zip.getNextEntry().getName()),"original entry order");
            check(Arrays.equals(zip.readAllBytes(),original),"original data");
            check("settings.json".equals(zip.getNextEntry().getName()),"settings entry order");
            check(json.equals(new String(zip.readAllBytes(),StandardCharsets.UTF_8)),"settings unchanged");
            // Build 554 format 2: a checksum manifest is always the final entry.
            check(ProjectManifest.ENTRY.equals(zip.getNextEntry().getName()),"manifest must be last");
            ProjectManifest m=ProjectManifest.parse(zip.readAllBytes());
            check(m.entries.size()==3&&m.entries.get("photo.png").bytes==png.length&&m.engineBuild==EngineBuild.CURRENT,"manifest contents");
            java.util.Map<String,ProjectManifest.Digest> actual=new java.util.HashMap<>();
            for(Object[] e:new Object[][]{{"photo.png",png},{"original.png",original},{"settings.json",json.getBytes(StandardCharsets.UTF_8)}})
                actual.put((String)e[0],new ProjectManifest.Digest(ProjectManifest.hex(ProjectManifest.sha256().digest((byte[])e[1])),((byte[])e[1]).length));
            m.verify(actual);
            check(zip.getNextEntry()==null,"unexpected entry");
        }
        ByteArrayOutputStream broken=new ByteArrayOutputStream();
        boolean failed=false;
        try(ZipOutputStream zip=new ZipOutputStream(broken)) {
            StudioProjectArchiveWriter.write(zip,out->false,out->true,"{}");
        }catch(IOException e){failed=true;}
        check(failed,"encoding failure must propagate");
    }
    private static void testCancellation() throws Exception {
        try(StudioRenderController controller=new StudioRenderController()) {
            CountDownLatch started=new CountDownLatch(1),interrupted=new CountDownLatch(1);
            Future<?> a=controller.submit(StudioRenderController.Lane.STANDARD,()->{
                started.countDown();try{Thread.sleep(30000);}catch(InterruptedException e){interrupted.countDown();Thread.currentThread().interrupt();}
            });
            check(started.await(5,TimeUnit.SECONDS),"first job not started");
            CountDownLatch replacement=new CountDownLatch(1);
            Future<?> b=controller.submit(StudioRenderController.Lane.STANDARD,replacement::countDown);
            check(interrupted.await(5,TimeUnit.SECONDS),"superseded job not interrupted");
            check(replacement.await(5,TimeUnit.SECONDS),"replacement not dispatched");
            check(a.isCancelled() && b.isDone(),"future states incorrect");
        }
    }
    private static void testLanes() throws Exception {
        try(StudioRenderController controller=new StudioRenderController()) {
            CountDownLatch blocking=new CountDownLatch(1),started=new CountDownLatch(1);
            controller.submit(StudioRenderController.Lane.SPECIALTY,()->{
                started.countDown();try{blocking.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            });
            check(started.await(5,TimeUnit.SECONDS),"specialty did not begin");
            CountDownLatch preview=new CountDownLatch(1);
            controller.submit(StudioRenderController.Lane.PREVIEW,preview::countDown);
            check(preview.await(5,TimeUnit.SECONDS),"preview blocked by specialty");
            blocking.countDown();
        }
    }
    private static void testShutdown(){
        StudioRenderController controller=new StudioRenderController();controller.close();controller.close();
        boolean failed=false;
        try{controller.submit(StudioRenderController.Lane.STANDARD,()->{});}catch(IllegalStateException e){failed=true;}
        check(failed,"closed controller accepted new work");
    }
}

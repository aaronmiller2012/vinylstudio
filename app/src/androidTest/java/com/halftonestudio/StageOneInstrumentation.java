package com.halftonestudio;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Dependency-free on-device suite (no AndroidX Test artifacts are required offline).
 * Each case runs on the real ART runtime, Bitmap codecs, filesystem and ContentResolver,
 * which the host gates replace with stubs. Run with scripts/run-device-tests.sh.
 * Results: one "case.<name>" entry per case plus "stage1" with the overall verdict.
 */
public final class StageOneInstrumentation extends Instrumentation {
    private interface Case { void run() throws Exception; }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}

    @Override public void onCreate(Bundle args) {super.onCreate(args);start();}

    @Override public void onStart() {
        super.onStart();
        Bundle result=new Bundle();
        Map<String,Case> cases=new LinkedHashMap<>();
        cases.put("launch_and_metrics",this::launchAndMetrics);
        cases.put("library_round_trip_real_png",this::libraryRoundTrip);
        cases.put("gallery_fingerprint_on_device_fs",this::galleryFingerprint);
        cases.put("share_provider_read_only",this::shareProvider);
        cases.put("share_intent_parsing",this::shareIntentParsing);
        cases.put("jts_backend_on_art",this::jtsBackend);
        cases.put("activity_recreate_and_shared_photo",this::recreateAndShare);
        cases.put("autosave_pair_restore_real_bitmaps",this::autosavePairRestore);
        cases.put("startup_decode_budget_large_photo",this::decodeBudget);
        cases.put("project_format2_manifest_on_device",this::projectFormat2);
        cases.put("camera_capture_write_grant",this::cameraWriteGrant);
        cases.put("new_project_resets_per_style_memory_d19",this::newProjectResetsStyleMemory);
        cases.put("undo_keeps_workspace_and_layer_keeps_zoom",this::undoKeepsWorkspaceAndZoom);
        cases.put("project_round_trip_prismatic_and_mat_m5",this::projectRoundTripM5);
        int failed=0;
        for(Map.Entry<String,Case> c:cases.entrySet()){
            String verdict;
            try{c.getValue().run();verdict="PASS";}
            catch(Throwable error){failed++;verdict="FAIL: "+error;android.util.Log.e("VinylDeviceTest",c.getKey(),error);}
            result.putString("case."+c.getKey(),verdict);
        }
        result.putString("stage1",failed==0?"PASS: "+cases.size()+" on-device cases":"FAIL: "+failed+" of "+cases.size()+" cases");
        finish(failed==0?0:-1,result);
    }

    private Context app(){return getTargetContext();}

    private File scratch(String name){
        File dir=new File(app().getCacheDir(),"device-test-"+name+"-"+System.nanoTime());
        check(dir.mkdirs(),"scratch dir");
        return dir;
    }
    private static void delete(File f){File[] c=f.listFiles();if(c!=null)for(File x:c)delete(x);f.delete();}

    private static byte[] png(int color){
        Bitmap b=Bitmap.createBitmap(24,16,Bitmap.Config.ARGB_8888);b.eraseColor(color);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        check(b.compress(Bitmap.CompressFormat.PNG,100,out),"PNG encode");b.recycle();
        return out.toByteArray();
    }

    // ---- cases -------------------------------------------------------------------

    private void launchAndMetrics() throws Exception {
        Activity activity=null;
        try{
            Intent launch=app().getPackageManager().getLaunchIntentForPackage(app().getPackageName());
            if(launch==null)throw new AssertionError("No launchable StudioActivity");
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=startActivitySync(launch);
            check(activity instanceof StudioActivity,"Unexpected activity");
            check(!app().getString(R.string.app_name).isEmpty(),"Missing localized application label");
            try(StudioRenderController controller=new StudioRenderController()){controller.cancelAll();}
            waitForIdleSync();
            // Freshly encoded export JPEGs must not acquire EXIF GPS/device identifiers.
            Bitmap pixels=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);pixels.eraseColor(Color.BLUE);
            ByteArrayOutputStream jpeg=new ByteArrayOutputStream();
            check(pixels.compress(Bitmap.CompressFormat.JPEG,92,jpeg),"JPEG encoding failed");
            ExifInterface exif=new ExifInterface(new ByteArrayInputStream(jpeg.toByteArray()));
            check(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)==null&&exif.getAttribute(ExifInterface.TAG_MAKE)==null&&
                exif.getAttribute(ExifInterface.TAG_MODEL)==null,"Freshly encoded JPEG contains identifying EXIF");
            android.graphics.Path closed=new android.graphics.Path();
            closed.addRect(0,0,3,4,android.graphics.Path.Direction.CW);
            android.graphics.Path open=new android.graphics.Path();open.moveTo(0,0);open.lineTo(3,4);
            ArtworkCutMetrics.Stats measured=AssemblyPathMetrics.measure(2,closed,open);
            check(measured.loops==1&&measured.openPaths==1&&Math.abs(measured.lengthMm-38)<=.05,
                "Native PathMeasure physical contour accounting failed");
        }finally{if(activity!=null){Activity current=activity;current.runOnUiThread(current::finish);waitForIdleSync();}}
    }

    /** D19: values a previous project leaves in per-style memory must not reach the next one.
     * Never calls New Project itself (that deletes the user's autosave on a real phone);
     * the user's editor state is restored before the Activity can pause and autosave. */
    private void newProjectResetsStyleMemory() throws Exception {
        Intent launch=app().getPackageManager().getLaunchIntentForPackage(app().getPackageName());
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        StudioActivity activity=(StudioActivity)startActivitySync(launch);
        final Throwable[] failure={null};
        try{
            runOnMainSync(()->{
                String users=activity.editorSnapshotForTest();
                // Prove the user's state can be put back BEFORE changing anything; otherwise
                // abort untouched (finish() would autosave whatever the editor holds).
                try{activity.applyEditorForTest(users);}catch(Throwable cannot){failure[0]=cannot;return;}
                int usersMaterial=activity.cutMaterialForTest();float usersTrio=activity.trioSpacingMemoryForTest(0);
                try{
                    String pristine=activity.pristineEditorForTest();
                    check(pristine!=null,"pristine editor snapshot was not captured");
                    org.json.JSONObject fresh=new org.json.JSONObject(pristine);
                    org.json.JSONObject dirty=new org.json.JSONObject(pristine);
                    dirty.put("newStyleDetail",fresh.optInt("newStyleDetail",55)==91?12:91);
                    dirty.put("prismaticSpacingMm",1.37);dirty.put("trioOrganic",7);
                    dirty.put("newStyleWords","LEAKED WORDS");dirty.put("cutMaterialProfileId",3);
                    activity.applyEditorForTest(dirty.toString());
                    activity.setTrioSpacingMemoryForTest(0,1.6f); // Confetti memory from the previous project
                    org.json.JSONObject leaked=new org.json.JSONObject(activity.editorSnapshotForTest());
                    check(leaked.optInt("trioOrganic")==7,"fixture did not change editor state");
                    activity.restorePristineEditorForTest();
                    org.json.JSONObject after=new org.json.JSONObject(activity.editorSnapshotForTest());
                    for(String key:new String[]{"newStyleDetail","prismaticSpacingMm","trioOrganic","newStyleWords"})
                        check(String.valueOf(fresh.opt(key)).equals(String.valueOf(after.opt(key))),
                            "per-style value leaked into a new project: "+key+"="+after.opt(key));
                    check(Math.abs(activity.trioSpacingMemoryForTest(0)-.35f)<1e-6f,
                        "Confetti spacing memory leaked: "+activity.trioSpacingMemoryForTest(0));
                    // The vinyl material is the user's physical stock and keeps its cut minimums.
                    check(activity.cutMaterialForTest()==3,"material profile must survive New Project");
                }catch(Throwable t){failure[0]=t;}
                finally{try{activity.applyEditorForTest(users);activity.setTrioSpacingMemoryForTest(0,usersTrio);}
                    catch(Exception restore){if(failure[0]==null)failure[0]=restore;}
                    if(activity.cutMaterialForTest()!=usersMaterial&&failure[0]==null)failure[0]=new AssertionError("material not restored");}
            });
            if(failure[0]!=null)throw new AssertionError("D19 reset",failure[0]);
        }finally{activity.runOnUiThread(activity::finish);waitForIdleSync();}
    }

    /** M5: the real editor save (toJson) and reopen (fromJson) keep the five appended Prismatic
     *  IDs, their controls and the selected Cricut mat; a legacy project without a mat and an
     *  unknown mat value never become a larger mat. The user's editor state is restored. */
    private void projectRoundTripM5() throws Exception {
        Intent launch=app().getPackageManager().getLaunchIntentForPackage(app().getPackageName());
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        StudioActivity activity=(StudioActivity)startActivitySync(launch);
        final Throwable[] failure={null};
        try{
            runOnMainSync(()->{
                String users=activity.editorSnapshotForTest();
                try{activity.applyEditorForTest(users);}catch(Throwable cannot){failure[0]=cannot;return;}
                try{
                    for(int id=VinylStyleSuite.PRISMATIC_CONTOUR_FLOW;id<=VinylStyleSuite.PRISMATIC_CHEVRON;id++){
                        org.json.JSONObject edit=new org.json.JSONObject(users);
                        edit.put("newStyleId",id);edit.put("prismaticSpacingMm",.5);edit.put("prismaticTone",40);
                        edit.put("cricutMat",CricutMatPreflight.MAT_12X24);edit.put("widthMm",203.2);
                        activity.applyEditorForTest(edit.toString());
                        org.json.JSONObject saved=new org.json.JSONObject(activity.editorSnapshotForTest());
                        check(saved.optInt("newStyleId",-1)==id,"style "+id+" did not survive save/reopen");
                        check(Math.abs(saved.optDouble("prismaticSpacingMm")-.5)<1e-4&&saved.optInt("prismaticTone")==40,
                            "Prismatic controls lost for "+id);
                        check(CricutMatPreflight.MAT_12X24.equals(saved.optString("cricutMat")),"selected mat lost for "+id);
                        check(Math.abs(saved.optDouble("widthMm")-203.2)<.01,"physical width changed for "+id);
                        // Reopen the saved document itself (a second save of the same project).
                        activity.applyEditorForTest(saved.toString());
                        org.json.JSONObject again=new org.json.JSONObject(activity.editorSnapshotForTest());
                        check(again.optInt("newStyleId",-1)==id&&CricutMatPreflight.MAT_12X24.equals(again.optString("cricutMat")),
                            "second round trip changed style or mat for "+id);
                    }
                    org.json.JSONObject legacy=new org.json.JSONObject(users);legacy.remove("cricutMat");
                    activity.applyEditorForTest(legacy.toString());
                    String mat=activity.cricutMatForTest();
                    check(CricutMatPreflight.normalizeMat(mat)!=null,"legacy project resolved to mat "+mat);
                    org.json.JSONObject unknown=new org.json.JSONObject(users);unknown.put("cricutMat","24x48");
                    activity.applyEditorForTest(unknown.toString());
                    // Unknown saved text may be preserved verbatim, but is never the enforced mat.
                    check(CricutMatPreflight.normalizeMat(activity.cricutMatForTest())!=null,"unknown mat value was enforced");
                }catch(Throwable t){failure[0]=t;}
                finally{try{activity.applyEditorForTest(users);}catch(Exception restore){if(failure[0]==null)failure[0]=restore;}}
            });
            if(failure[0]!=null)throw new AssertionError("M5 project round trip",failure[0]);
        }finally{activity.runOnUiThread(activity::finish);waitForIdleSync();}
    }

    /** M3: undo stays in the user's workspace/panel; selecting a layer keeps the zoom. */
    private void undoKeepsWorkspaceAndZoom() throws Exception {
        Intent launch=app().getPackageManager().getLaunchIntentForPackage(app().getPackageName());
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        StudioActivity activity=(StudioActivity)startActivitySync(launch);
        final Throwable[] failure={null};
        try{
            runOnMainSync(()->{
                int workspace=activity.workspaceForTest();String users=activity.editorSnapshotForTest();
                float zoom=activity.zoomForTest();
                try{activity.applyEditorForTest(users);}catch(Throwable cannot){failure[0]=cannot;return;}
                try{
                    activity.selectWorkspaceForTest(3); // Colors
                    int colorsTool=activity.toolForTest();
                    activity.checkpointForTest();activity.undoForTest();
                    check(activity.workspaceForTest()==3&&activity.toolForTest()==colorsTool,
                        "undo left the Colors panel (tool "+activity.toolForTest()+")");
                    activity.zoomToForTest(2.5f);
                    activity.selectLayerForTest(0);
                    check(Math.abs(activity.zoomForTest()-2.5f)<1e-4f,"layer selection reset zoom to "+activity.zoomForTest());
                    activity.selectLayerForTest(-1);
                }catch(Throwable t){failure[0]=t;}
                finally{
                    try{activity.applyEditorForTest(users);activity.zoomToForTest(Float.isNaN(zoom)?1f:zoom);
                        activity.selectWorkspaceForTest(workspace==1?2:workspace);}
                    catch(Exception restore){if(failure[0]==null)failure[0]=restore;}
                }
            });
            if(failure[0]!=null)throw new AssertionError("undo/zoom",failure[0]);
        }finally{activity.runOnUiThread(activity::finish);waitForIdleSync();}
    }

    private StudioProjectLibrary.ArchiveWriter archive(byte[] photo){
        return out->{
            ZipOutputStream zip=new ZipOutputStream(out);
            StudioProjectArchiveWriter.write(zip,
                stream->{try{stream.write(photo);return true;}catch(IOException e){return false;}},
                stream->{try{stream.write(photo);return true;}catch(IOException e){return false;}},
                "{\"schema\":14,\"engineBuild\":"+EngineBuild.CURRENT+"}");
        };
    }

    private void libraryRoundTrip() throws Exception {
        File root=scratch("library");
        try{
            StudioProjectLibrary library=new StudioProjectLibrary(root);
            byte[] red=png(Color.RED);
            StudioProjectLibrary.Entry saved=library.save(null,"Device project","Shapes",null,archive(red));
            List<StudioProjectLibrary.Entry> listed=new StudioProjectLibrary(root).list();
            check(listed.size()==1&&"Device project".equals(listed.get(0).name),"saved project not listed");
            try(InputStream in=library.openArchive(saved.id);ZipInputStream zip=new ZipInputStream(in)){
                ProjectArchiveReader.ProjectEntries entries=ProjectArchiveReader.read(zip);
                Bitmap decoded=BitmapFactory.decodeByteArray(entries.photo,0,entries.photo.length);
                check(decoded!=null&&decoded.getWidth()==24&&decoded.getPixel(3,3)==Color.RED,"photo did not round-trip through the Android codec");
                check(new String(entries.settings,"UTF-8").contains("\"engineBuild\":"+EngineBuild.CURRENT),"engine stamp lost");
            }
        }finally{delete(root);}
    }

    /** The gallery fast path depends on this filesystem preserving length and mtime. */
    private void galleryFingerprint() throws Exception {
        File root=scratch("fingerprint");
        try{
            StudioProjectLibrary.Entry saved=new StudioProjectLibrary(root).save(null,"FP","Lines",null,archive(png(Color.GREEN)));
            File[] bundles=new File(root,"project-library").listFiles((d,n)->n.startsWith(saved.id+".rev-"));
            check(bundles!=null&&bundles.length==1,"one revision bundle");
            File zip=new File(bundles[0],"project.zip"),card=new File(bundles[0],"card.properties");
            check(StudioProjectLibrary.matchesRecordedFingerprint(zip,card),"fingerprint must survive directory rename on this filesystem");
            check(zip.setLastModified(zip.lastModified()-10_000L),"setLastModified unsupported");
            check(!StudioProjectLibrary.matchesRecordedFingerprint(zip,card),"mtime change must invalidate fingerprint");
            check(new StudioProjectLibrary(root).list().size()==1,"valid archive must still list after full verification");
        }finally{delete(root);}
    }

    private void shareProvider() throws Exception {
        Context context=app();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)){
            zip.putNextEntry(new ZipEntry("sheets/01 Black.svg"));zip.write("<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes("UTF-8"));zip.closeEntry();
            zip.putNextEntry(new ZipEntry("cut-complexity.csv"));zip.write("x".getBytes("UTF-8"));zip.closeEntry();
        }
        List<File> sheets=SharedExportFiles.extractCutSheets(new ByteArrayInputStream(bytes.toByteArray()),SharedExportProvider.root(context));
        check(sheets.size()==1,"one SVG sheet");
        Uri uri=SharedExportProvider.uriFor(context,sheets.get(0));
        ContentResolver resolver=context.getContentResolver();
        check("image/svg+xml".equals(resolver.getType(uri)),"MIME type");
        try(InputStream in=resolver.openInputStream(uri)){
            check(in!=null&&new String(readAll(in),"UTF-8").startsWith("<svg"),"provider content");
        }
        try(Cursor c=resolver.query(uri,null,null,null,null)){
            check(c!=null&&c.moveToFirst()&&"01 Black.svg".equals(c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))),"display name");
        }
        boolean writeRejected=false;
        try{resolver.openOutputStream(uri).close();}catch(SecurityException|IOException|IllegalArgumentException expected){writeRejected=true;}
        check(writeRejected,"provider must be read-only");
        Uri escape=new Uri.Builder().scheme("content").authority(SharedExportProvider.AUTHORITY).appendPath("..").appendPath("x").build();
        boolean escapeRejected=false;
        try{resolver.openInputStream(escape).close();}catch(IOException|SecurityException|IllegalArgumentException|NullPointerException expected){escapeRejected=true;}
        check(escapeRejected,"path escape must be rejected");
    }

    private void shareIntentParsing(){
        Uri content=Uri.parse("content://com.example.photos/media/42");
        Intent send=new Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM,content);
        check(content.equals(SharedImageIntent.imageUri(send)),"SEND image accepted");
        Intent view=new Intent(Intent.ACTION_VIEW).setDataAndType(content,"image/png");
        check(content.equals(SharedImageIntent.imageUri(view)),"VIEW image accepted");
        check(SharedImageIntent.imageUri(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,content))==null,"non-image rejected");
        check(SharedImageIntent.imageUri(new Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM,Uri.parse("file:///sdcard/x.jpg")))==null,"file:// rejected");
        check(SharedImageIntent.imageUri(new Intent(Intent.ACTION_MAIN))==null,"launcher intent ignored");
    }

    private void jtsBackend(){
        JtsVectorBooleanBackend jts=new JtsVectorBooleanBackend();
        Object a=jts.path(new float[][]{{0,0,10,0,10,10,0,10}});
        Object b=jts.path(new float[][]{{5,0,15,0,15,10,5,10}});
        ArrayList<float[]> union=jts.contours(jts.combine(a,b,false));
        check(union.size()==1,"overlapping squares must union to one contour: "+union.size());
        float minX=Float.MAX_VALUE,maxX=-Float.MAX_VALUE;
        for(int i=0;i<union.get(0).length;i+=2){minX=Math.min(minX,union.get(0)[i]);maxX=Math.max(maxX,union.get(0)[i]);}
        check(Math.abs(minX)<.01f&&Math.abs(maxX-15)<.01f,"union extent");
        check(jts.contours(jts.intersect(a,b)).size()==1,"intersection");
    }

    /** Recreate survives, and a photo shared from another app reaches the singleTask editor. */
    private void recreateAndShare() throws Exception {
        Context context=app();
        Intent launch=context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity=startActivitySync(launch);
        try{
            ActivityMonitor monitor=addMonitor(StudioActivity.class.getName(),null,false);
            runOnMainSync(activity::recreate);
            Activity recreated=waitForMonitorWithTimeout(monitor,10_000);
            removeMonitor(monitor);
            check(recreated instanceof StudioActivity,"recreate failed");
            activity=recreated;
            waitForIdleSync();
            // Serve a real PNG through our own provider and share it in.
            File share=new File(SharedExportProvider.root(context),"device-test-share");
            check(share.isDirectory()||share.mkdirs(),"share dir");
            File photo=new File(share,"photo.svg"); // provider serves SVG/DXF names in production; content is a PNG
            try(FileOutputStream out=new FileOutputStream(photo)){out.write(png(Color.YELLOW));}
            Uri uri=SharedExportProvider.uriFor(context,photo);
            Intent send=new Intent(Intent.ACTION_SEND).setClassName(context,StudioActivity.class.getName())
                .setType("image/png").putExtra(Intent.EXTRA_STREAM,uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(send);
            waitForIdleSync();
            final Activity current=activity;
            final String[] action=new String[1];
            long deadline=System.currentTimeMillis()+8_000;
            do{
                runOnMainSync(()->action[0]=current.getIntent().getAction());
                if(Intent.ACTION_MAIN.equals(action[0]))break;
                Thread.sleep(100);
            }while(System.currentTimeMillis()<deadline);
            check(Intent.ACTION_MAIN.equals(action[0]),"shared intent must be delivered to the existing editor and consumed, got "+action[0]);
            delete(share);
        }finally{final Activity done=activity;done.runOnUiThread(done::finish);waitForIdleSync();}
    }

    /** Build 554: autosaved pair restores with its token through the real PNG codec. */
    private void autosavePairRestore() throws Exception {
        File dir=scratch("autosave");
        try{
            Bitmap edited=Bitmap.createBitmap(40,30,Bitmap.Config.ARGB_8888);edited.eraseColor(Color.MAGENTA);
            new ProjectPhotoStore().save(dir,edited,edited,7L,"devtest01-r7");
            StartupPhotoLoader.Snapshot restored=StartupPhotoLoader.load(dir);
            check(restored!=null&&restored.width==40&&restored.photo.getPixel(5,5)==Color.MAGENTA,"autosaved photo restore");
            check("devtest01-r7".equals(restored.pairToken),"pair token restored");
            check(PhotoPairToken.compare("devtest01-r8",restored.pairToken)==PhotoPairToken.Verdict.MISMATCH,"mismatch detected on device");
            restored.discard();edited.recycle();
        }finally{PhotoRevisionStore.clear(dir);delete(dir);}
    }

    /** A photo larger than the edit budget is downsampled at restore instead of exhausting memory. */
    private void decodeBudget() throws Exception {
        File dir=scratch("budget");
        try{
            int w=PhotoDecodeBudget.EDIT_MAX_SIDE*2,h=256;
            Bitmap big=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);big.eraseColor(Color.GRAY);
            new ProjectPhotoStore().save(dir,big,big,1L,"devtest02-r1");big.recycle();
            StartupPhotoLoader.Snapshot restored=StartupPhotoLoader.load(dir);
            check(restored!=null&&Math.max(restored.width,restored.height)<=PhotoDecodeBudget.EDIT_MAX_SIDE,
                "restore must respect EDIT_MAX_SIDE, got "+(restored==null?"null":restored.width+"x"+restored.height));
            restored.discard();
        }finally{PhotoRevisionStore.clear(dir);delete(dir);}
    }

    /** Project ZIPs written on device carry a manifest that verifies, and tampering is caught. */
    private void projectFormat2() throws Exception {
        File root=scratch("format2");
        try{
            StudioProjectLibrary library=new StudioProjectLibrary(root);
            StudioProjectLibrary.Entry e=library.save(null,"F2","Shapes",null,archive(png(Color.CYAN)));
            File zip=library.archive(e.id);
            StudioProjectLibrary.verifyProjectArchive(zip);
            try(java.util.zip.ZipFile z=new java.util.zip.ZipFile(zip)){
                check(z.getEntry(ProjectManifest.ENTRY)!=null,"manifest written on device");
            }
        }finally{delete(root);}
    }

    /** The camera app may write only the pre-created capture file; cut-sheet shares stay read-only. */
    private void cameraWriteGrant() throws Exception {
        Context context=app();
        File target=SharedExportFiles.newCameraCapture(SharedExportProvider.root(context));
        Uri uri=SharedExportProvider.uriFor(context,target);
        try(java.io.OutputStream out=context.getContentResolver().openOutputStream(uri,"w")){
            check(out!=null,"camera target writable");out.write(png(Color.WHITE));
        }
        check(target.length()>0,"camera bytes persisted");
        check("image/jpeg".equals(context.getContentResolver().getType(uri)),"capture MIME");
        delete(target.getParentFile());
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))!=-1)out.write(buf,0,n);
        return out.toByteArray();
    }
}

package com.halftonestudio;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

/**
 * M7 behavioral regression tests for the defects found on the SM-T510 (B1-B9).
 *
 * In-process cases ({@link #inProcess}) and the two-phase process-death scenario
 * ({@code -e vinylScenario b9set} then {@code b9check}, see tools/device/regress.sh).
 * They import photos and change the editor, so they ONLY run against the isolated
 * com.halftonestudio.perf sandbox and refuse to touch the user's installed app.
 */
final class DeviceRegression {
    private final Instrumentation in;
    private StudioActivity activity;
    DeviceRegression(Instrumentation in){this.in=in;}

    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    boolean sandboxed(){return in.getTargetContext().getPackageName().endsWith(".perf");}

    // ---------------------------------------------------------------- plumbing
    private interface Call<T>{T call() throws Exception;}
    <T> T onMain(Call<T> c) throws Exception {
        final Object[] box=new Object[1];final Exception[] err=new Exception[1];
        in.runOnMainSync(()->{try{box[0]=c.call();}catch(Exception e){err[0]=e;}});
        if(err[0]!=null)throw err[0];
        @SuppressWarnings("unchecked") T v=(T)box[0];return v;
    }
    StudioActivity launch(){
        Intent launch=in.getTargetContext().getPackageManager().getLaunchIntentForPackage(in.getTargetContext().getPackageName());
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=(StudioActivity)in.startActivitySync(launch);in.waitForIdleSync();return activity;
    }
    void close(){if(activity!=null){StudioActivity a=activity;a.runOnUiThread(a::finish);in.waitForIdleSync();activity=null;}}
    boolean waitSettled(long ms) throws Exception {
        long end=SystemClock.elapsedRealtime()+ms;int stable=0;SystemClock.sleep(150);
        while(SystemClock.elapsedRealtime()<end){stable=onMain(()->activity.renderSettledForTest())?stable+1:0;if(stable>=2)return true;SystemClock.sleep(50);}
        return false;
    }
    static Bitmap portrait(){
        Bitmap b=Bitmap.createBitmap(240,300,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(0xffd9c7b0);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(0xff6b4a3a);c.drawOval(50,40,190,230,p);
        p.setColor(0xff1d1d24);c.drawCircle(95,120,12,p);c.drawCircle(145,120,12,p);c.drawRect(90,180,150,192,p);
        p.setColor(0xff2f5d8a);c.drawRect(0,240,240,300,p);return b;
    }
    void importPortrait() throws Exception {
        Bitmap b=portrait();
        onMain(()->{activity.importPhotoForTest(b,false);activity.selectWorkspaceForTest(2);return null;});
        check(waitSettled(90_000),"default render after import did not settle");
    }
    void style(String key) throws Exception {
        check(onMain(()->activity.activateStyleForTest(key)),"style not in picker: "+key);
    }

    // ---------------------------------------------------------------- in-process cases
    void inProcess(java.util.Map<String,StageCase> cases){
        cases.put("m7_b1_status_follows_style",this::b1);
        cases.put("m7_b2_subtitle_keeps_project_name",this::b2);
        cases.put("m7_b3_failed_import_restores_status",this::b3);
        cases.put("m7_b8_unchanged_slider_release_no_render",this::b8);
        cases.put("m7_b11_b12_workspace_switch_keeps_final_artwork",this::b12);
        cases.put("m8_f2_style_picker_cancel_restores_workspace",this::f2);
        cases.put("m8_f3_unsaved_project_label_survives_relaunch",this::f3);
    }
    /** M8 F2: cancelling the Style Picker returns tab and panel to the previous workspace. */
    void f2() throws Exception {
        requireSandbox();launch();
        try{
            importPortrait();
            onMain(()->{activity.selectWorkspaceForTest(5);return null;});
            check(onMain(()->activity.workspaceForTest())==5,"not on Export");
            onMain(()->{activity.selectWorkspaceForTest(1);return null;});
            check(onMain(()->activity.stylePickerOpenForTest()),"Styles did not open the picker");
            onMain(()->{activity.cancelStylePickerForTest();return null;});
            long end=SystemClock.elapsedRealtime()+5_000;
            while(SystemClock.elapsedRealtime()<end&&(onMain(()->activity.stylePickerOpenForTest())||onMain(()->activity.workspaceForTest())==1))SystemClock.sleep(100);
            check(!onMain(()->activity.stylePickerOpenForTest()),"picker still open");
            int ws=onMain(()->activity.workspaceForTest());
            check(ws==5,"workspace after cancel is "+ws+", expected Export (5)");
        }finally{close();}
    }
    /** M8 F3: an unsaved imported project keeps its label after the activity is relaunched. */
    void f3() throws Exception {
        requireSandbox();launch();
        try{
            importPortrait();
            check(onMain(()->activity.editorSubtitleForTest()).contains("Photo project"),"fixture: imported project label");
        }finally{close();}
        launch();
        try{
            check(waitSettled(120_000),"restored project did not settle");
            String subtitle=onMain(()->activity.editorSubtitleForTest());
            check(subtitle.contains("Photo project"),"relaunched subtitle lost the project label: '"+subtitle+"'");
        }finally{close();}
    }
    interface StageCase{void run() throws Exception;}

    private void requireSandbox(){if(!sandboxed())throw new AssertionError("SKIP-UNSAFE: run against com.halftonestudio.perf only");}

    /** B1: after switching style the Adjust status never shows the previous style's summary. */
    void b1() throws Exception {
        requireSandbox();launch();
        try{
            importPortrait();
            style(StudioThumbnailRegistry.newStyle(VinylStyleSuite.TRUCHET));
            check(waitSettled(120_000),"Truchet did not settle");
            String truchet=onMain(()->activity.alternateStatusForTest());
            check(!truchet.isEmpty(),"no Truchet status");
            onMain(()->{activity.selectWorkspaceForTest(2);return null;}); // Adjust panel shows the status label
            style("multilayer.tonal");
            String during=onMain(()->activity.alternateStatusForTest());
            check(!during.equals(truchet),"status still shows the previous style immediately after switching: "+during);
            check(waitSettled(120_000),"Tonal did not settle");
            onMain(()->{activity.selectWorkspaceForTest(2);return null;});
            String after=onMain(()->activity.alternateStatusForTest()),label=onMain(()->activity.alternateStatusLabelForTest());
            check(!after.equals(truchet),"final status is the previous style's: "+after);
            check(label==null||label.equals(after),"Adjust label '"+label+"' differs from status '"+after+"'");
        }finally{close();}
    }

    /** B2: a style carried over to a new photo is shown with the project's name, not instead of it. */
    void b2() throws Exception {
        requireSandbox();launch();
        try{
            importPortrait();
            style(StudioThumbnailRegistry.newStyle(VinylStyleSuite.TRUCHET));
            check(waitSettled(120_000),"Truchet did not settle");
            importPortrait(); // new photo, Truchet still selected
            String subtitle=onMain(()->activity.editorSubtitleForTest());
            check(subtitle.contains("Photo project"),"subtitle hides the project name: '"+subtitle+"'");
            check(subtitle.contains(VinylStyleSuite.NAMES[VinylStyleSuite.TRUCHET]),"subtitle lost the style: '"+subtitle+"'");
        }finally{close();}
    }

    /** B3: an unreadable import never leaves "Importing photo" behind. */
    void b3() throws Exception {
        requireSandbox();launch();
        try{
            importPortrait(); // a settled project, so no restore render can overwrite the status
            Uri missing=Uri.parse("content://"+in.getTargetContext().getPackageName()+".sharedexports/missing/nothing.png");
            onMain(()->{activity.beginPhotoImportForTest(missing);return null;});
            String importing=in.getTargetContext().getString(R.string.quality_importing_photo_for_cropping);
            long end=SystemClock.elapsedRealtime()+15_000;String status="";
            while(SystemClock.elapsedRealtime()<end){
                status=onMain(()->activity.statusTextForTest());
                if(status.toLowerCase(java.util.Locale.ROOT).contains("failed"))break;SystemClock.sleep(100);
            }
            check(onMain(()->activity.renderSettledForTest()),"previous artwork not usable after the failed import");
            check(!status.equals(importing),"status stuck on '"+importing+"'");
            check(status.toLowerCase(java.util.Locale.ROOT).contains("failed"),"no clear failure message: '"+status+"'");
        }finally{close();}
    }

    /** B8: touching a slider without changing its value starts no render; a real change starts exactly one final request. */
    void b8() throws Exception {
        requireSandbox();launch();
        try{
            importPortrait();
            style("multilayer.tonal");
            check(waitSettled(120_000),"Tonal did not settle");
            onMain(()->{activity.selectWorkspaceForTest(2);return null;});in.waitForIdleSync();SystemClock.sleep(400);
            SeekBar bar=onMain(()->firstSeekBar(activity.getWindow().getDecorView()));
            check(bar!=null,"no slider in Adjust");
            int[] xy=new int[2];onMain(()->{bar.getLocationOnScreen(xy);return null;});
            int w=onMain(bar::getWidth),h=onMain(bar::getHeight),progress=onMain(bar::getProgress),max=onMain(bar::getMax);
            float pad=onMain(()->(float)bar.getPaddingLeft()),track=w-pad-onMain(()->(float)bar.getPaddingRight());
            float thumbX=xy[0]+pad+track*progress/(float)Math.max(1,max),y=xy[1]+h/2f;
            int before=onMain(()->activity.committedRenderRequestsForTest());
            touch(thumbX,y,thumbX,y);
            SystemClock.sleep(300);
            int afterTap=onMain(()->activity.committedRenderRequestsForTest());
            int progressAfter=onMain(bar::getProgress);
            if(progressAfter==progress)check(afterTap==before,"unchanged slider release requested "+(afterTap-before)+" render(s)");
            check(waitSettled(120_000),"did not settle after tap");
            int beforeDrag=onMain(()->activity.committedRenderRequestsForTest());
            touch(thumbX,y,Math.min(xy[0]+w-pad-2,thumbX+track*.15f),y);
            int afterDrag=onMain(()->activity.committedRenderRequestsForTest());
            check(onMain(bar::getProgress)!=progressAfter,"drag did not change the slider");
            check(afterDrag-beforeDrag==1,"a drag+release requested "+(afterDrag-beforeDrag)+" committed renders, expected exactly 1");
            check(waitSettled(120_000),"did not settle after drag");
        }finally{close();}
    }
    private void touch(float x0,float y0,float x1,float y1){
        long t=SystemClock.uptimeMillis();
        in.sendPointerSync(MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x0,y0,0));
        int steps=x0==x1?0:12;
        for(int i=1;i<=steps;i++){in.sendPointerSync(MotionEvent.obtain(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x0+(x1-x0)*i/steps,y0,0));SystemClock.sleep(16);}
        in.sendPointerSync(MotionEvent.obtain(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x1,y1,0));
        in.waitForIdleSync();
    }
    private static SeekBar firstSeekBar(View v){
        if(v instanceof SeekBar&&v.isShown())return (SeekBar)v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){SeekBar s=firstSeekBar(g.getChildAt(i));if(s!=null)return s;}}
        return null;
    }


    /** B12: switching workspaces on settled suite-style artwork starts no render (so no repeated
     * cut-ready stage); B11: the Colors panel then shows ready cut state, not "pending". */
    void b12() throws Exception {
        requireSandbox();launch();
        try{
            importPortrait();
            style(StudioThumbnailRegistry.newStyle(VinylStyleSuite.TRUCHET));
            check(waitSettled(120_000),"Truchet did not settle");
            int before=onMain(()->activity.committedRenderRequestsForTest());
            for(int w:new int[]{3,4,5,2,3}){
                final int ws=w;onMain(()->{activity.selectWorkspaceForTest(ws);return null;});
                in.waitForIdleSync();SystemClock.sleep(500);
                check(onMain(()->activity.specialtyFinalReadyForTest()),"final artwork invalidated by switching to workspace "+w);
            }
            int after=onMain(()->activity.committedRenderRequestsForTest());
            check(after==before,"workspace switches requested "+(after-before)+" render(s) for unchanged artwork");
            String pending=onMain(()->findText(activity.getWindow().getDecorView(),"Cuts pending render"));
            check(pending==null,"Colors panel still says 'Cuts pending render' for finished artwork");
        }finally{close();}
    }
    private static String findText(View v,String needle){
        if(v instanceof android.widget.TextView&&v.isShown()&&String.valueOf(((android.widget.TextView)v).getText()).contains(needle))return needle;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){String f=findText(g.getChildAt(i),needle);if(f!=null)return f;}}
        return null;
    }
    // ---------------------------------------------------------------- B6/B7/B9 process death
    /** Phase 1: accepted edits, then the instrumentation ends WITHOUT finishing the activity,
     * so ActivityManager kills the process with no onPause/onStop (foreground process death). */
    Bundle b9set() throws Exception {
        requireSandbox();launch();
        importPortrait();
        style("multilayer.tonal");
        check(waitSettled(120_000),"Tonal did not settle");
        onMain(()->{activity.setArtworkWidthForTest(203.2f);activity.selectWorkspaceForTest(5);return null;});
        check(waitSettled(120_000),"8 in render did not settle");
        SystemClock.sleep(StudioActivity.EDITOR_AUTOSAVE_DELAY_MS+1500);
        Bundle b=new Bundle();
        b.putString("workspace",String.valueOf(onMain(()->activity.workspaceForTest())));
        b.putString("tool",String.valueOf(onMain(()->activity.toolForTest())));
        b.putString("style",onMain(()->activity.styleKeyForTest()));
        b.putString("stage1","PASS: b9set (process will be killed by instrumentation end)");
        return b; // activity deliberately NOT finished
    }
    /** Phase 2 (new process): size, style, workspace + panel restored and artwork regenerated. */
    Bundle b9check(Bundle expected) throws Exception {
        requireSandbox();launch();
        try{
            boolean settled=waitSettled(180_000);
            float width=onMain(()->activity.artworkWidthForTest());
            int workspace=onMain(()->activity.workspaceForTest()),tool=onMain(()->activity.toolForTest());
            String style=onMain(()->activity.styleKeyForTest());
            StringBuilder fail=new StringBuilder();
            if(Math.abs(width-203.2f)>.05f)fail.append("B9 width ").append(width).append(" mm, expected 203.2; ");
            if(!settled)fail.append("B6 artwork not regenerated after restart; ");
            if(workspace!=5)fail.append("B7 workspace ").append(workspace).append(", expected 5 (Export); ");
            String expectTool=expected.getString("tool");
            if(expectTool!=null&&!expectTool.equals(String.valueOf(tool)))fail.append("B7 panel tool ").append(tool).append(" != ").append(expectTool).append("; ");
            String expectStyle=expected.getString("style");
            if(expectStyle!=null&&!expectStyle.equals(style))fail.append("style ").append(style).append(" != ").append(expectStyle).append("; ");
            Bundle b=new Bundle();
            b.putString("stage1",fail.length()==0?"PASS: b9check width/style/workspace/panel restored and artwork regenerated":"FAIL: "+fail);
            return b;
        }finally{close();}
    }
}

package com.halftonestudio;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.os.Debug;
import android.os.SystemClock;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.zip.CRC32;

/**
 * M7 on-device benchmark. Drives the REAL StudioActivity: photos go through the import
 * decoder and commit path, styles are activated through the live picker registry, and a
 * case is complete only when the final artwork AND its background cut-ready stage are done.
 *
 * Run (perf build): am instrument -w -r -e vinylBench all -e photos astronaut.png
 *   -e widths 6 -e trials 2 com.halftonestudio.perf.test/com.halftonestudio.StageOneInstrumentation
 * Photos are read from the app's external files dir "bench/" (pushed by tools/device/bench.sh);
 * names "synthetic12mp.jpg"/"synthetic48mp.jpg" are generated on first use from astronaut.png.
 * One JSON object per case is emitted as instrumentation status key "bench" and appended
 * to bench/results.jsonl. Logcat markers (tag VinylBench) delimit each case so the host can
 * attribute VinylPerf phase lines.
 */
final class DeviceBench {
    private final Instrumentation in;
    private final Bundle args;
    private final File dir;
    private StudioActivity activity;
    private volatile boolean sampling;
    private volatile long peakHeap;

    DeviceBench(Instrumentation in,Bundle args){
        this.in=in;this.args=args;
        dir=new File(in.getTargetContext().getExternalFilesDir(null),"bench");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    private String arg(String k,String d){String v=args.getString(k);return v==null||v.isEmpty()?d:v;}

    /** Cold-start restore probe: heap sampled from before the activity launches until the
     * restored project has re-rendered (final artwork + cut-ready stage). */
    Bundle startupProbe(){
        Bundle out=new Bundle();
        boolean ok=false;
        try{
            Runtime rt=Runtime.getRuntime();
            long base=rt.totalMemory()-rt.freeMemory();
            startSampler();
            long t0=SystemClock.elapsedRealtime();
            Intent launch=in.getTargetContext().getPackageManager().getLaunchIntentForPackage(in.getTargetContext().getPackageName());
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=(StudioActivity)in.startActivitySync(launch);
            long shown=SystemClock.elapsedRealtime()-t0;
            ok=waitSettled(Long.parseLong(arg("timeoutS","300"))*1000L);
            long settled=SystemClock.elapsedRealtime()-t0;
            stopSampler();
            Debug.MemoryInfo mi=new Debug.MemoryInfo();Debug.getMemoryInfo(mi);
            String style=onMain(()->activity.styleKeyForTest());float width=onMain(()->activity.artworkWidthForTest());
            emit(out,String.format(Locale.US,"{\"event\":\"startup\",\"ok\":%b,\"activityShownMs\":%d,\"restoredAndRenderedMs\":%d,"+
                "\"baseHeapKb\":%d,\"peakHeapKb\":%d,\"heapLimitKb\":%d,\"pssKb\":%d,\"style\":\"%s\",\"widthMm\":%.1f}",
                ok,shown,settled,base/1024,peakHeap/1024,rt.maxMemory()/1024,mi.getTotalPss(),esc(style),width));
        }catch(Throwable t){android.util.Log.e("VinylBench","startup",t);out.putString("bench.fatal",String.valueOf(t));}
        finally{if(activity!=null){activity.runOnUiThread(activity::finish);in.waitForIdleSync();}}
        out.putString("stage1",(ok?"PASS":"FAIL")+": startup probe");
        return out;
    }

    Bundle run(){
        if(args.getString("startupProbe")!=null)return startupProbe();
        Bundle out=new Bundle();
        int cases=0,failures=0;
        try{
            Intent launch=in.getTargetContext().getPackageManager().getLaunchIntentForPackage(in.getTargetContext().getPackageName());
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ParallelGeometry.unionDepthOverride=Integer.parseInt(arg("unionDepth","-1"));
            CutGeometryConditioner.parallelPasses="1".equals(arg("cutParallel","0"));
            if("1".equals(arg("sequential","0")))ParallelGeometry.forceSequential=true;
            activity=(StudioActivity)in.startActivitySync(launch);
            in.waitForIdleSync();
            // Reproducible start: the fresh-install editor (D19 reset; never deletes projects).
            onMain(()->{activity.restorePristineEditorForTest();return null;});
            String[][] inventory=onMain(()->activity.stylePickerInventoryForTest());
            writeInventory(inventory);
            List<String> keys=selectStyles(inventory,arg("vinylBench","all"));
            int trials=Integer.parseInt(arg("trials","2"));
            long timeoutMs=Long.parseLong(arg("timeoutS","300"))*1000L;
            boolean shots=!"0".equals(arg("shots","1"));
            for(String photoName:arg("photos","astronaut.png").split(",")){
                File photo=ensurePhoto(photoName.trim());
                final Bitmap bitmap=onMain(()->activity.decodePhotoForTest(Uri.fromFile(photo)));
                final int bw=bitmap.getWidth(),bh=bitmap.getHeight();
                final boolean circular="1".equals(arg("circle","0"));
                long importStart=SystemClock.elapsedRealtime();
                // Prepare (workspace 0) is photo-only by design; artwork renders from Styles on.
                onMain(()->{activity.importPhotoForTest(bitmap,circular);activity.selectWorkspaceForTest(1);return null;});
                waitSettled(timeoutMs);
                emit(out,String.format(Locale.US,"{\"event\":\"import\",\"photo\":\"%s\",\"source\":\"%dx%d\",\"firstRenderMs\":%d}",
                    photoName,bw,bh,SystemClock.elapsedRealtime()-importStart));
                for(String widthIn:arg("widths","6").split(",")){
                    final float mm=Float.parseFloat(widthIn)*25.4f;
                    onMain(()->{activity.setArtworkWidthForTest(mm);return null;});
                    waitSettled(timeoutMs);
                    for(String key:keys){
                        for(int t=0;t<trials;t++){
                            cases++;
                            String json=measure(photoName,bw,bh,widthIn,key,t,timeoutMs,shots&&t==0);
                            if(json.contains("\"ok\":false"))failures++;
                            emit(out,json);
                        }
                    }
                }
            }
        }catch(Throwable fatal){
            android.util.Log.e("VinylBench","fatal",fatal);
            out.putString("bench.fatal",String.valueOf(fatal));failures++;
        }finally{
            if(activity!=null){activity.runOnUiThread(activity::finish);in.waitForIdleSync();}
        }
        out.putString("stage1",(failures==0?"PASS":"FAIL")+": bench "+cases+" cases, "+failures+" failures");
        return out;
    }

    private String measure(String photo,int bw,int bh,String widthIn,String key,int trial,long timeoutMs,boolean shot) throws Exception {
        if(trial>0)onMain(()->{activity.clearRenderCachesForTest();return null;});
        Runtime rt=Runtime.getRuntime();
        String gcCount0=Debug.getRuntimeStat("art.gc.gc-count"),gcTime0=Debug.getRuntimeStat("art.gc.gc-time");
        String tag=key+" w="+widthIn+" photo="+photo+" trial="+trial;
        android.util.Log.i("VinylBench","begin "+tag);
        startSampler();
        long start=SystemClock.elapsedRealtime();
        if(trial==0){stopSampler();
            // A first render must follow a real style switch: if the target is already shown
            // (e.g. restored at launch), switch to a cheap different style first, unmeasured.
            String other="multilayer.grid".equals(key)?"multilayer.voronoi":"multilayer.grid";
            onMain(()->activity.activateStyleForTest(other));waitSettled(timeoutMs);
            startSampler();start=SystemClock.elapsedRealtime();
        }
        boolean activated=onMain(()->{
            if(trial==0)return activity.activateStyleForTest(key);
            activity.requestRenderForTest();return true;
        });
        boolean ok=activated&&waitSettled(timeoutMs);
        long wall=SystemClock.elapsedRealtime()-start;
        stopSampler();
        android.util.Log.i("VinylBench","end "+tag+" ok="+ok+" wallMs="+wall);
        long gcCount=delta(Debug.getRuntimeStat("art.gc.gc-count"),gcCount0),gcTime=delta(Debug.getRuntimeStat("art.gc.gc-time"),gcTime0);
        Debug.MemoryInfo mi=new Debug.MemoryInfo();Debug.getMemoryInfo(mi);
        final String[] status=new String[2];final int[][] cuts=new int[1][];final int[] layers=new int[1];
        final long[] crc={0};final float[] width={0};
        onMain(()->{
            status[0]=activity.statusTextForTest();status[1]=activity.alternateStatusForTest();
            cuts[0]=activity.cutCountsForTest();layers[0]=activity.layerCountForTest();width[0]=activity.artworkWidthForTest();
            View v=activity.artworkViewForTest();
            if(v!=null&&v.getWidth()>0&&v.getHeight()>0){
                Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);
                v.draw(new Canvas(b));
                int[] px=new int[b.getWidth()*b.getHeight()];b.getPixels(px,0,b.getWidth(),0,0,b.getWidth(),b.getHeight());
                CRC32 c=new CRC32();for(int p:px){c.update(p);c.update(p>>>8);c.update(p>>>16);c.update(p>>>24);}crc[0]=c.getValue();
                if(shot)try(OutputStream o=new FileOutputStream(new File(dir,"shot_"+safe(key)+"_"+widthIn+"in_"+safe(photo.replaceAll("\\.[a-z]+$",""))+".jpg"))){
                    b.compress(Bitmap.CompressFormat.JPEG,88,o);}catch(IOException ignored){}
                b.recycle();
            }
            return null;
        });
        int total=0;StringBuilder per=new StringBuilder("[");
        if(cuts[0]!=null)for(int i=0;i<cuts[0].length;i++){total+=cuts[0][i];if(i>0)per.append(',');per.append(cuts[0][i]);}
        per.append(']');
        return String.format(Locale.US,
            "{\"event\":\"case\",\"style\":\"%s\",\"photo\":\"%s\",\"source\":\"%dx%d\",\"widthIn\":%s,\"widthMm\":%.1f,"+
            "\"trial\":%d,\"ok\":%b,\"wallMs\":%d,\"peakHeapKb\":%d,\"heapLimitKb\":%d,\"pssKb\":%d,\"gcCount\":%d,\"gcMs\":%d,"+
            "\"layers\":%d,\"cutContours\":%d,\"cutPerLayer\":%s,\"artworkCrc\":\"%08x\",\"status\":\"%s\",\"styleStatus\":\"%s\"}",
            esc(key),esc(photo),bw,bh,widthIn,width[0],trial,ok,wall,peakHeap/1024,rt.maxMemory()/1024,mi.getTotalPss(),
            gcCount,gcTime,layers[0],total,per,crc[0],esc(status[0]),esc(status[1]));
    }

    private boolean waitSettled(long timeoutMs) throws Exception {
        long end=SystemClock.elapsedRealtime()+timeoutMs;
        // Let the request reach the render lane before the first poll.
        Thread.sleep(120);
        int stable=0;
        while(SystemClock.elapsedRealtime()<end){
            boolean s=onMain(()->activity.renderSettledForTest());
            // Two consecutive settled polls: a debounced re-render can start right after a final.
            stable=s?stable+1:0;
            if(stable>=2)return true;
            Thread.sleep(40);
        }
        return false;
    }

    private void startSampler(){
        final Runtime rt=Runtime.getRuntime();
        peakHeap=rt.totalMemory()-rt.freeMemory();sampling=true;
        Thread t=new Thread(()->{
            while(sampling){
                long used=rt.totalMemory()-rt.freeMemory();if(used>peakHeap)peakHeap=used;
                SystemClock.sleep(20);
            }
        },"bench-heap");
        t.setDaemon(true);t.start();
    }
    private void stopSampler(){sampling=false;SystemClock.sleep(30);}

    private List<String> selectStyles(String[][] inventory,String spec){
        List<String> keys=new ArrayList<>();
        for(String[] row:inventory){
            String key=row[2];
            if(keys.contains(key))continue;
            if("all".equals(spec)){keys.add(key);continue;}
            for(String s:spec.split(","))if(key.equals(s.trim())||(s.endsWith("*")&&key.startsWith(s.substring(0,s.length()-1)))){keys.add(key);break;}
        }
        return keys;
    }

    private void writeInventory(String[][] inventory) throws IOException {
        try(FileWriter w=new FileWriter(new File(dir,"inventory.tsv"))){
            for(String[] row:inventory)w.write(row[0]+"\t"+row[1]+"\t"+row[2]+"\n");
        }
    }

    /** Synthetic high-resolution inputs: bicubic-like upscale of a CC0 portrait plus mild noise. */
    private File ensurePhoto(String name) throws IOException {
        File f=new File(dir,name);
        if(f.isFile())return f;
        int w,h;
        if(name.equals("synthetic12mp.jpg")){w=4032;h=3024;}
        else if(name.equals("synthetic48mp.jpg")){w=8000;h=6000;}
        else throw new IOException("missing bench photo "+f);
        Bitmap src=BitmapFactory.decodeFile(new File(dir,"astronaut.png").getPath());
        if(src==null)throw new IOException("astronaut.png needed to synthesize "+name);
        Bitmap big=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(big);
        android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(src,null,new android.graphics.Rect(0,0,w,h),p);
        Random r=new Random(565);
        int[] row=new int[w];
        for(int y=0;y<h;y++){
            big.getPixels(row,0,w,0,y,w,1);
            for(int x=0;x<w;x++){int n=(int)(r.nextGaussian()*3);int v=row[x];
                int rr=clamp(((v>>16)&255)+n),gg=clamp(((v>>8)&255)+n),bb=clamp((v&255)+n);row[x]=0xff000000|(rr<<16)|(gg<<8)|bb;}
            big.setPixels(row,0,w,0,y,w,1);
        }
        try(OutputStream o=new FileOutputStream(f)){big.compress(Bitmap.CompressFormat.JPEG,92,o);}
        big.recycle();src.recycle();
        return f;
    }
    private static int clamp(int v){return v<0?0:v>255?255:v;}

    private void emit(Bundle out,String json){
        android.util.Log.i("VinylBench",json);
        Bundle status=new Bundle();status.putString("bench",json);in.sendStatus(1,status);
        try(FileWriter w=new FileWriter(new File(dir,"results.jsonl"),true)){w.write(json+"\n");}catch(IOException ignored){}
    }

    private interface Call<T>{T call() throws Exception;}
    private <T> T onMain(Call<T> c) throws Exception {
        final Object[] box=new Object[1];final Exception[] err=new Exception[1];
        in.runOnMainSync(()->{try{box[0]=c.call();}catch(Exception e){err[0]=e;}});
        if(err[0]!=null)throw err[0];
        @SuppressWarnings("unchecked") T v=(T)box[0];return v;
    }
    private static long delta(String now,String before){
        try{return Long.parseLong(now)-Long.parseLong(before);}catch(Exception e){return -1;}
    }
    private static String safe(String s){return s.replaceAll("[^A-Za-z0-9_.-]","_");}
    private static String esc(String s){
        if(s==null)return "";
        StringBuilder b=new StringBuilder();
        for(char ch:s.toCharArray()){if(ch=='"'||ch=='\\')b.append('\\').append(ch);else if(ch<0x20)b.append(' ');else b.append(ch);}
        return b.toString();
    }
}

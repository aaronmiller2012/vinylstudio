package com.halftonestudio;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicBoolean;

/** One private last-crash report, explicitly copied by the user from the project menu.
 * No upload, no saved photos, no background telemetry, and no crash swallowing. */
public final class VinylCrashReport {
    private static final String FILE = "last-java-crash.txt";
    private static final AtomicBoolean installed = new AtomicBoolean(false);
    private static volatile String location = "starting";
    private static final java.util.concurrent.ThreadPoolExecutor diagnosticWorker =
        new java.util.concurrent.ThreadPoolExecutor(1,1,0L,
            java.util.concurrent.TimeUnit.MILLISECONDS,
            new java.util.concurrent.ArrayBlockingQueue<Runnable>(8),
            task->{Thread t=new Thread(task,"VinylLocalDiagnostics");t.setDaemon(true);return t;},
            new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    private VinylCrashReport() { }

    public static void breadcrumb(String value) {
        if (value != null) location = value.substring(0, Math.min(160, value.length()));
    }

    public static void install(Context context) {
        if (!installed.compareAndSet(false, true)) return;
        final File destination = new File(context.getApplicationContext().getFilesDir(), FILE);
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> {
            try {
                StringWriter buffer = new StringWriter();
                PrintWriter writer = new PrintWriter(buffer);
                writer.println("VinylStudio Java crash");
                writer.println("App build: " + EngineBuild.CURRENT);
                writer.println("Thread: " + thread.getName());
                writer.println("Last UI action: " + location);
                writer.println("Android API: " + Build.VERSION.SDK_INT);
                writer.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
                writer.println("Timestamp ms: " + System.currentTimeMillis());
                failure.printStackTrace(writer);
                writer.flush();
                byte[] bytes = buffer.toString().getBytes(StandardCharsets.UTF_8);
                try (FileOutputStream out = new FileOutputStream(destination, false)) {
                    out.write(bytes, 0, Math.min(bytes.length, 64 * 1024));
                    out.flush();
                }
            } catch (Throwable ignored) {
                Log.e("VinylCrash", "Could not store local Java crash report");
            } finally {
                // Delegate to Android's system handler. Never swallow the exception.
                if (previous != null) previous.uncaughtException(thread, failure);
                else { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(10); }
            }
        });
    }

    /** Private, bounded last-library-failure summary. No photo names, paths or project data. */
    public static void recordLibraryError(Context context, Exception error) {
        if(context==null||error==null)return;
        final File destination=new File(context.getApplicationContext().getFilesDir(),"last-library-error.txt");
        final String type=error.getClass().getSimpleName();
        // Rejection may originate on the UI thread; even diagnostic disk I/O stays off it.
        try{diagnosticWorker.execute(()->{
            try(FileOutputStream out=new FileOutputStream(destination,false)) {
                String msg="timestampMs="+System.currentTimeMillis()+"\n"+
                    "type="+type+"\n"+
                    "message=Library operation failed; view the app error dialog\n";
                out.write(msg.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            } catch(Exception ignored) {
                Log.w("VinylLibrary", "Could not save private library diagnostic");
            }
        });}catch(java.util.concurrent.RejectedExecutionException ignored){
            Log.w("VinylLibrary", "Diagnostic queue full");
        }
    }

    /** Modification time of the stored crash report, or 0 when none exists. */
    public static long recordedAt(Context context) {
        File file = new File(context.getApplicationContext().getFilesDir(), FILE);
        return file.isFile() ? file.lastModified() : 0L;
    }

    public static String read(Context context) {
        File file = new File(context.getApplicationContext().getFilesDir(), FILE);
        if (!file.isFile()) return null;
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[(int)Math.min(file.length(), 64 * 1024)];
            int offset = 0;
            while (offset < bytes.length) {
                int n = input.read(bytes, offset, bytes.length - offset);
                if (n < 0) break;
                offset += n;
            }
            return new String(bytes, 0, offset, StandardCharsets.UTF_8);
        } catch (Exception error) { return null; }
    }
}

package com.halftonestudio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

/**
 * Build 553 product flows extracted from StudioActivity: share-in of photos, share-out
 * of verified cut sheets, opt-in crash reports, the library backup reminder and the
 * engine-change notice. StudioActivity supplies only the narrow {@link Host} below,
 * which is the template for moving further responsibilities out of the activity.
 */
final class StudioProductFlows {
    /** The activity services these flows need; all callbacks run on the UI thread. */
    interface Host {
        boolean postUi(Runnable task);
        /** Serial private-storage lane; false when the queue rejected the job. */
        boolean scheduleDiskJob(Runnable job);
        /** Low-priority diagnostics lane; may throw RejectedExecutionException. */
        void runDiagnostics(Runnable job);
        void toast(String message);
        SharedPreferences prefs();
        boolean alive();
        boolean startupRestoring();
        /** Route through unsaved-changes protection, then import for cropping. */
        void importSharedPhoto(Uri uri);
        void startLibraryBackup();
        /** In-process edit revision of the open project. */
        long editRevision();
        /** Autosave photo token of the open photo state (PhotoPairToken). */
        String photoToken();
    }

    private final Activity activity;
    private final Host host;
    private Uri pendingSharedImage;
    private boolean backupReminderShown;

    StudioProductFlows(Activity activity,Host host){this.activity=activity;this.host=host;}

    // ---- Share in -------------------------------------------------------------------

    /** Accept a photo from Share / Open with; delivered once the startup restore settles. */
    void acceptSharedImage(Intent intent){
        Uri shared=SharedImageIntent.imageUri(intent);
        if(shared==null)return;
        // Consume the intent so recreation never imports the same share twice.
        activity.setIntent(new Intent(Intent.ACTION_MAIN));
        pendingSharedImage=shared;
        host.postUi(this::deliverPendingSharedImage);
    }

    boolean hasPendingSharedImage(){return pendingSharedImage!=null;}

    /** Called at every startup-restore exit; no-op until a share is waiting and restore is done. */
    void deliverPendingSharedImage(){
        final Uri shared=pendingSharedImage;
        if(shared==null||host.startupRestoring()||!host.alive())return;
        pendingSharedImage=null;
        host.importSharedPhoto(shared);
    }

    // ---- Share out ------------------------------------------------------------------

    /** After a verified cut ZIP is saved, offer to send its SVG/DXF sheets to a cutter app.
     * Sheets are read back from the document just written, so what is shared is
     * byte-identical to what was verified and saved. */
    void offerShareCutSheets(Uri written){
        final Context app=activity.getApplicationContext();
        if(!host.scheduleDiskJob(()->{
            List<File> sheets;
            try(InputStream in=activity.getContentResolver().openInputStream(written)){
                if(in==null)throw new IOException("Saved ZIP unavailable");
                sheets=SharedExportFiles.extractCutSheets(in,SharedExportProvider.root(app));
            }catch(IOException|RuntimeException error){
                android.util.Log.w("VinylShare","Cut sheets not offered for sharing",error);return;
            }
            String weed=null;
            try(InputStream in=activity.getContentResolver().openInputStream(written)){
                weed=in==null?null:WeedDifficulty.hardest(SharedExportFiles.readTextEntry(in,"cut-complexity.csv"));
            }catch(IOException|RuntimeException ignored){/* advisory only */}
            final String hardest=weed;
            final ArrayList<Uri> uris=new ArrayList<>();
            Uri combinedUri=null;
            try{
                for(File sheet:sheets){
                    Uri uri=SharedExportProvider.uriFor(app,sheet);
                    // Build 556: the all-layers SVG is offered on its own, never mixed
                    // into the separate sheets (that would duplicate every layer).
                    if(CombinedLayerSvg.FILENAME.equals(sheet.getName()))combinedUri=uri;
                    else uris.add(uri);
                }
            }catch(IOException error){android.util.Log.w("VinylShare","Share URI unavailable",error);return;}
            final Uri combined=combinedUri;
            host.postUi(()->{
                if(!host.alive())return;
                String weedLine=hardest==null?"":"\n\n"+activity.getString(R.string.weed_hardest_sheet,hardest);
                AlertDialog.Builder dialog=new AlertDialog.Builder(activity).setTitle(R.string.share_cut_sheets_title)
                    .setNegativeButton(R.string.action_done,null);
                if(combined!=null){
                    dialog.setMessage(activity.getString(R.string.share_combined_message,uris.size())+weedLine)
                        .setPositiveButton(R.string.action_send_one_svg,(d,w)->shareCombinedSvg(combined))
                        .setNeutralButton(R.string.action_separate_sheets,(d,w)->shareCutSheets(uris));
                }else{
                    dialog.setMessage(activity.getString(R.string.share_cut_sheets_message,uris.size())+weedLine)
                        .setPositiveButton(R.string.action_share_sheets,(d,w)->shareCutSheets(uris));
                }
                dialog.show();
            });
        }))android.util.Log.w("VinylShare","Disk queue busy; share offer skipped");
    }

    /** One SVG with every layer: a single Design Space upload instead of one per color.
     *  ACTION_SEND of one image/svg+xml file reaches Design Space when it accepts shares,
     *  and otherwise Files / Drive, from which Design Space's Upload can open it. */
    private void shareCombinedSvg(Uri combined){
        Intent send=new Intent(Intent.ACTION_SEND).setType("image/svg+xml");
        send.putExtra(Intent.EXTRA_STREAM,combined);
        send.setClipData(ClipData.newRawUri(CombinedLayerSvg.FILENAME,combined));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{activity.startActivity(Intent.createChooser(send,activity.getString(R.string.share_combined_chooser)));}
        catch(ActivityNotFoundException none){host.toast(activity.getString(R.string.share_no_receiver));}
    }

    private void shareCutSheets(ArrayList<Uri> uris){
        if(uris.isEmpty())return;
        boolean allSvg=true;
        for(Uri u:uris)if(!u.getLastPathSegment().toLowerCase(Locale.ROOT).endsWith(".svg"))allSvg=false;
        Intent send=new Intent(uris.size()==1?Intent.ACTION_SEND:Intent.ACTION_SEND_MULTIPLE);
        send.setType(allSvg?"image/svg+xml":"*/*");
        if(uris.size()==1)send.putExtra(Intent.EXTRA_STREAM,uris.get(0));
        else send.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);
        ClipData clip=ClipData.newRawUri("Cut sheets",uris.get(0));
        for(int i=1;i<uris.size();i++)clip.addItem(new ClipData.Item(uris.get(i)));
        send.setClipData(clip);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{activity.startActivity(Intent.createChooser(send,activity.getString(R.string.share_cut_sheets_chooser)));}
        catch(ActivityNotFoundException none){host.toast(activity.getString(R.string.share_no_receiver));}
    }

    /** Keep the verified cut ZIP just saved so a library save of this exact edit can carry it.
     * {@code current} is false when the artwork changed while the ZIP was being written. */
    void rememberVerifiedExport(Uri written,boolean current){
        final java.io.File files=activity.getFilesDir();
        final long edit=host.editRevision();
        final String token=host.photoToken();
        host.scheduleDiskJob(()->{
            if(!current){LastVerifiedExport.clear(files);return;}
            try{LastVerifiedExport.store(files,activity.getContentResolver().openInputStream(written),edit,token);}
            catch(IOException|RuntimeException error){
                LastVerifiedExport.clear(files);
                android.util.Log.w("VinylShare","Verified export not kept for the project",error);
            }
        });
    }

    // ---- Crash reports --------------------------------------------------------------

    /** Opt-in only: nothing is uploaded. The user reviews the text and picks where it goes. */
    void showCrashReportActions(String report,boolean afterCrash){
        new AlertDialog.Builder(activity)
            .setTitle(afterCrash?R.string.crash_title_after_crash:R.string.crash_title_last)
            .setMessage((afterCrash?activity.getString(R.string.crash_intro)+"\n\n":"")+
                report.substring(0,Math.min(report.length(),600))+(report.length()>600?"…":""))
            .setNegativeButton(afterCrash?R.string.action_not_now:R.string.action_close,null)
            .setNeutralButton(R.string.action_copy,(d,w)->{
                ClipboardManager clip=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if(clip!=null){clip.setPrimaryClip(ClipData.newPlainText("VinylStudio crash report",report));
                    host.toast(activity.getString(R.string.crash_copied));}
            })
            .setPositiveButton(R.string.action_share,(d,w)->{
                Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT,activity.getString(R.string.crash_subject,EngineBuild.CURRENT))
                    .putExtra(Intent.EXTRA_TEXT,report);
                try{activity.startActivity(Intent.createChooser(send,activity.getString(R.string.crash_chooser)));}
                catch(ActivityNotFoundException none){host.toast(activity.getString(R.string.crash_no_receiver));}
            }).show();
    }

    /** Offer each newly recorded crash exactly once, on the next launch. File IO stays off the UI thread. */
    void maybeOfferCrashReport(){
        final SharedPreferences prefs=host.prefs();
        final long offered=prefs.getLong("crashReportOfferedMs",0L);
        final Context app=activity.getApplicationContext();
        try{host.runDiagnostics(()->{
            final long recorded=VinylCrashReport.recordedAt(app);
            if(recorded<=0||recorded==offered)return;
            final String report=VinylCrashReport.read(app);
            host.postUi(()->{
                if(!host.alive())return;
                prefs.edit().putLong("crashReportOfferedMs",recorded).apply();
                if(report!=null)showCrashReportActions(report,true);
            });
        });}catch(RejectedExecutionException busy){/* offered on a later launch */}
    }

    // ---- Library backup reminder ----------------------------------------------------

    void recordLibraryBackup(){
        host.prefs().edit().putLong(BackupReminderPolicy.PREF_LAST_BACKUP,System.currentTimeMillis()).apply();
    }

    /** At most once per screen instance, and snoozed for a week after dismissal. */
    void maybeShowBackupReminder(int projects){
        if(backupReminderShown)return;
        final SharedPreferences prefs=host.prefs();
        long last=prefs.getLong(BackupReminderPolicy.PREF_LAST_BACKUP,0L);
        final long now=System.currentTimeMillis();
        if(!BackupReminderPolicy.shouldRemind(projects,last,prefs.getLong(BackupReminderPolicy.PREF_LAST_DISMISS,0L),now))return;
        backupReminderShown=true;
        new AlertDialog.Builder(activity).setTitle(R.string.backup_title)
            .setMessage(BackupReminderPolicy.message(projects,last,Build.VERSION.SDK_INT))
            .setNegativeButton(R.string.backup_later,(d,w)->prefs.edit().putLong(BackupReminderPolicy.PREF_LAST_DISMISS,now).apply())
            .setPositiveButton(R.string.backup_now,(d,w)->host.startLibraryBackup()).show();
    }

    // ---- Engine change notice -------------------------------------------------------

    void maybeShowEngineChangeNotice(int savedBuild,String projectKey){
        if(!EngineBuild.regeneratedByNewerEngine(savedBuild))return;
        final SharedPreferences prefs=host.prefs();
        final String token=EngineBuild.ackToken(projectKey);
        final Set<String> acknowledged=new HashSet<>(prefs.getStringSet("engineNoticeAck",Collections.emptySet()));
        if(acknowledged.contains(token))return;
        new AlertDialog.Builder(activity).setTitle(R.string.engine_notice_title)
            .setMessage(EngineBuild.notice(savedBuild))
            .setPositiveButton(R.string.action_ok,(d,w)->{
                // Keep only this build's acknowledgements; older tokens can never match again.
                Set<String> next=new HashSet<>();
                for(String t:acknowledged)if(t.startsWith(EngineBuild.CURRENT+":"))next.add(t);
                next.add(token);prefs.edit().putStringSet("engineNoticeAck",next).apply();
            }).show();
    }
}

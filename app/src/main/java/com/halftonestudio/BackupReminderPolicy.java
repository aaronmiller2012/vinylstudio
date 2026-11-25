package com.halftonestudio;

/**
 * Projects live only in app-private storage. Android 12+ device-to-device transfer
 * carries the library (see data_extraction_rules.xml), but cloud backup stays off and
 * uninstalling erases everything, so users with real work are reminded to export a
 * library backup ZIP. Pure policy: no Android types, host-testable.
 */
final class BackupReminderPolicy {
    static final int MIN_PROJECTS=3;
    static final long BACKUP_STALE_MS=30L*24*60*60*1000;
    static final long SNOOZE_MS=7L*24*60*60*1000;
    static final String PREF_LAST_BACKUP="lastLibraryBackupMs";
    static final String PREF_LAST_DISMISS="lastBackupReminderDismissMs";
    private BackupReminderPolicy() { }

    static boolean shouldRemind(int projects,long lastBackupMs,long lastDismissMs,long nowMs){
        if(projects<MIN_PROJECTS)return false;
        if(lastDismissMs>0&&lastDismissMs<=nowMs&&nowMs-lastDismissMs<SNOOZE_MS)return false;
        if(lastBackupMs<=0||lastBackupMs>nowMs)return true; // never, or clock moved backwards
        return nowMs-lastBackupMs>=BACKUP_STALE_MS;
    }

    static String message(int projects,long lastBackupMs,int sdkInt){
        String when=lastBackupMs<=0?"You have never exported a library backup.":
            "Your last library backup is over 30 days old.";
        String transfer=sdkInt>=31?
            "Projects move with Android's phone-to-phone transfer, but uninstalling or clearing app data erases them.":
            "Android "+(sdkInt>=30?"11":"10 and older")+" cannot move projects to a new phone, and uninstalling erases them.";
        return projects+" projects are stored only on this phone. "+when+" "+transfer;
    }
}

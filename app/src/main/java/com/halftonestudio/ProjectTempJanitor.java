package com.halftonestudio;

import java.io.File;

/** Clean orphaned scratch files from interrupted project opens.
 * Only known ephemeral names older than a day qualify, so concurrent Activity
 * creations never delete files a live worker is still reading.
 */
final class ProjectTempJanitor {
    private static final long AGE_MS = 24L*60*60*1000;
    private ProjectTempJanitor() {}
    static int prune(File cache, long nowMs) {
        if (cache == null || !cache.isDirectory()) return 0;
        File[] children=cache.listFiles();
        if(children==null)return 0;
        int removed=0;
        for(File file:children){
            String name=file.getName();
            boolean known=name.startsWith("vinyl-v3-reopen-") &&
                (name.endsWith(".png") || name.endsWith(".jpg"));
            known|=name.startsWith("vinyl-archive-original-") && name.endsWith(".pending");
            known|=name.startsWith("vinyl-owned-source-") && name.endsWith(".pending");
            known|=name.startsWith("vinyl-cut-") && name.endsWith(".pending.zip");
            long modified=file.lastModified();
            if(known && file.isFile() && modified>0 && nowMs-modified>=AGE_MS &&
                    nowMs>=modified && file.delete())removed++;
        }
        return removed;
    }
}

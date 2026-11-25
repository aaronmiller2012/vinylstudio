package com.halftonestudio;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.UriPermission;
import java.util.Set;
import android.net.Uri;

/** Storage Access Framework grants; provider-neutral and least privilege. */
public final class DocumentAccess {
    private DocumentAccess() {}
    public static boolean isContentUri(Uri uri) {
        return uri!=null && "content".equals(uri.getScheme())
            && uri.getAuthority()!=null && !uri.getAuthority().isEmpty();
    }
    /** Only prune grants this app owns for SAF project history. Keep all retained entries. */
    public static void releaseUnusedProjectGrants(ContentResolver resolver,String history) {
        if(resolver==null)return;
        Set<String> retained=RecentProjectUris.kept(history);
        try {
            for(UriPermission grant:resolver.getPersistedUriPermissions()) {
                Uri uri=grant.getUri();
                if(!isContentUri(uri)||retained.contains(uri.toString()))continue;
                int flags=(grant.isReadPermission()?Intent.FLAG_GRANT_READ_URI_PERMISSION:0)
                    |(grant.isWritePermission()?Intent.FLAG_GRANT_WRITE_URI_PERMISSION:0);
                if(flags!=0)try {resolver.releasePersistableUriPermission(uri,flags);}
                catch(SecurityException|IllegalArgumentException ignored) { /* revocation race */ }
            }
        }catch(SecurityException|IllegalArgumentException ignored){ /* provider unavailable */ }
    }
    public static boolean persist(ContentResolver resolver,Uri uri,int returnedFlags,boolean write) {
        if(!isContentUri(uri))return false;
        int permitted=returnedFlags & (Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        int requested=write?(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION):
            Intent.FLAG_GRANT_READ_URI_PERMISSION;
        // A recent saved project must be readable when opened later, not just writable.
        if((permitted & requested)!=requested)return false;
        // Some providers grant only a temporary access token. Saving/opening still works;
        // only the recent-project shortcut is omitted when persistence is unavailable.
        try {resolver.takePersistableUriPermission(uri,requested);return true;}
        catch (SecurityException|IllegalArgumentException ex) {return false;}
    }
}

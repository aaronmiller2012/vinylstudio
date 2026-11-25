package com.halftonestudio;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;

/** Accepts one image from Share (ACTION_SEND) or Open with (ACTION_VIEW). */
final class SharedImageIntent {
    private SharedImageIntent() { }

    static Uri imageUri(Intent intent){
        if(intent==null)return null;
        String action=intent.getAction();
        String type=intent.getType();
        Uri uri=null;
        if(Intent.ACTION_SEND.equals(action)){
            if(type!=null&&!type.startsWith("image/"))return null;
            uri=Build.VERSION.SDK_INT>=33?intent.getParcelableExtra(Intent.EXTRA_STREAM,Uri.class):legacyStream(intent);
            if(uri==null&&intent.getClipData()!=null&&intent.getClipData().getItemCount()>0)
                uri=intent.getClipData().getItemAt(0).getUri();
        }else if(Intent.ACTION_VIEW.equals(action)){
            if(type!=null&&!type.startsWith("image/"))return null;
            uri=intent.getData();
        }
        // Only provider-mediated content is accepted; never raw file:// paths from other apps.
        return uri!=null&&DocumentAccess.isContentUri(uri)?uri:null;
    }

    @SuppressWarnings("deprecation")
    private static Uri legacyStream(Intent intent){return intent.getParcelableExtra(Intent.EXTRA_STREAM);}
}

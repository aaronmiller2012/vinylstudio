package com.halftonestudio;

import android.graphics.Bitmap;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Serial photo autosave with a process-death-safe pair commit. */
final class ProjectPhotoStore {
    private long savedRevision=Long.MIN_VALUE;
    private Bitmap savedOriginal;
    private static void png(File file,Bitmap bitmap) throws IOException {
        try(FileOutputStream out=new FileOutputStream(file)){
            if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))
                throw new IOException("PNG encoder returned false: "+file.getName());
            out.flush();out.getFD().sync();
        }
    }
    synchronized void reset(){savedRevision=Long.MIN_VALUE;savedOriginal=null;}
    synchronized void markExistingOriginal(Bitmap original,File directory){
        if(original!=null&&PhotoRevisionStore.active(directory)!=null)savedOriginal=original;
    }
    synchronized void save(File directory,Bitmap edited,Bitmap original,long revision)throws IOException {
        save(directory,edited,original,revision,null);
    }
    /** @param pairToken links this pair with the editor settings (see PhotoPairToken). */
    synchronized void save(File directory,Bitmap edited,Bitmap original,long revision,String pairToken)throws IOException {
        if(edited==null)return;
        Bitmap full=original==null?edited:original;
        PhotoRevisionStore.Pair prior=PhotoRevisionStore.active(directory);
        if(revision==savedRevision&&prior!=null&&full==savedOriginal)return;
        boolean reuseOriginal=prior!=null&&full==savedOriginal;
        PhotoRevisionStore.commit(directory,(newPhoto,newOriginal,previous)->{
            png(newPhoto,edited);
            PhotoPairToken.write(newPhoto,pairToken);
            if(reuseOriginal&&previous!=null)PhotoRevisionStore.copyOrLink(previous.original,newOriginal);
            else png(newOriginal,full);
        });
        savedRevision=revision;savedOriginal=full;
    }
    static void clear(File directory){PhotoRevisionStore.clear(directory);}
}

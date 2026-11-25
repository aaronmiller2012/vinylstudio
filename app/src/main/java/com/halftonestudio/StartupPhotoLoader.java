package com.halftonestudio;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import java.io.File;
import java.util.concurrent.CancellationException;

/** Decode local project previews on startupIo, never from onCreate()/onDraw().
 * The full-resolution original stays encoded on disk / in the project archive.
 */
final class StartupPhotoLoader {
    static final class Snapshot {
        final Bitmap photo,original;
        final int[] pixels;
        final int width,height;
        /** PhotoPairToken of the restored pair; "" for legacy data. */
        String pairToken="";
        Snapshot(Bitmap photo,Bitmap original,int[] pixels){
            this.photo=photo;this.original=original;this.pixels=pixels;
            width=photo.getWidth();height=photo.getHeight();
        }
        /** Only call when a stale startup result was NEVER published to StudioActivity. */
        void discard(){
            if(original!=photo&&!original.isRecycled())original.recycle();
            if(!photo.isRecycled())photo.recycle();
        }
    }
    private static File file(File dir,String png,String jpg){
        File p=new File(dir,png);return p.exists()?p:new File(dir,jpg);
    }
    static boolean hasSavedPhoto(File dir){return PhotoRevisionStore.hasPhoto(dir)||file(dir,"editing-photo.png","editing-photo.jpg").isFile();}

    private static Bitmap decode(File file,int maxSide,long maxPixels){
        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);
        PhotoDecodeBudget.Plan plan=PhotoDecodeBudget.plan(bounds.outWidth,bounds.outHeight,maxSide,maxPixels);
        if(Thread.currentThread().isInterrupted())throw new CancellationException("Photo restore canceled");
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inScaled=false;
        options.inSampleSize=plan.sampleSize;
        options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap bitmap=BitmapFactory.decodeFile(file.getAbsolutePath(),options);
        if(bitmap==null)throw new IllegalArgumentException("Unable to decode saved photo");
        try{
            if(Thread.currentThread().isInterrupted())throw new CancellationException("Photo restore canceled");
            int width=bitmap.getWidth(),height=bitmap.getHeight();
            if(width>plan.width||height>plan.height||(long)width*height>maxPixels){
                Bitmap scaled=Bitmap.createScaledBitmap(bitmap,plan.width,plan.height,true);
                if(scaled!=bitmap){bitmap.recycle();bitmap=scaled;}
            }
            return bitmap;
        }catch(RuntimeException | OutOfMemoryError error){bitmap.recycle();throw error;}
    }
    static Snapshot load(File dir){
        File local=PhotoRevisionStore.hasPhoto(dir)?PhotoRevisionStore.photo(dir):file(dir,"editing-photo.png","editing-photo.jpg");
        if(!local.isFile())return null;
        Bitmap photo=null,original=null;
        try{
            photo=decode(local,PhotoDecodeBudget.EDIT_MAX_SIDE,PhotoDecodeBudget.EDIT_MAX_PIXELS);
            if(Thread.currentThread().isInterrupted())throw new CancellationException("Photo restore canceled");
            File originalFile=PhotoRevisionStore.hasPhoto(dir)?PhotoRevisionStore.original(dir):file(dir,"editing-original.png","editing-original.jpg");
            original=originalFile.isFile()?decode(originalFile,PhotoDecodeBudget.ORIGINAL_PREVIEW_MAX_SIDE,
                PhotoDecodeBudget.ORIGINAL_PREVIEW_MAX_PIXELS):photo;
            int w=photo.getWidth(),h=photo.getHeight();
            int[] px=new int[w*h];photo.getPixels(px,0,w,0,0,w,h);
            if(Thread.currentThread().isInterrupted())throw new CancellationException("Photo restore canceled");
            Snapshot snapshot=new Snapshot(photo,original,px);
            snapshot.pairToken=PhotoPairToken.read(local);
            return snapshot;
        }catch(RuntimeException | OutOfMemoryError failure){
            if(original!=null&&original!=photo&&!original.isRecycled())original.recycle();
            if(photo!=null&&!photo.isRecycled())photo.recycle();
            if(failure instanceof OutOfMemoryError){
                Log.w("VinylPerf","Insufficient memory to restore saved photo",failure);
                return null; // Do not terminate the app while recovering a saved project.
            }
            throw failure;
        }
    }
}

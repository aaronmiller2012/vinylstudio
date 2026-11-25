package com.halftonestudio;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Lossless, compressed ORIGINAL PHOTO source. The editable preview remains small;
 * native-source tiles are only decoded when a future vector export needs them.
 * No tile is mistaken for the final global contour or written as an SVG patch.
 */
final class NativeOriginalPhoto {
    private static final String NAME="native-original-source.bin";
    private static final long MAX_BYTES=96L*1024L*1024L;
    private NativeOriginalPhoto() {}
    static File active(File dir){return new File(dir,NAME);}
    static void clear(File dir){new File(dir,NAME+".pending").delete();active(dir).delete();}
    /** Each crop dialog owns its own staged bytes until Apply or Cancel. */
    static OwnedSourceStage stage(File dir,ContentResolver resolver,Uri uri) throws IOException{
        // Conservative pre-check of our own private cache; failure is reported, never silent.
        //noinspection UsableSpace
        long freeAtStart=dir.getUsableSpace();
        long safePayloadBytes=freeAtStart<=0?MAX_BYTES:
            Math.min(MAX_BYTES,Math.max(0L,freeAtStart-32L*1024L*1024L));
        if(safePayloadBytes<=0)throw new IOException("Not enough free storage to cache the original photo");
        return OwnedSourceStage.capture(dir,resolver.openInputStream(uri),safePayloadBytes);
    }
    static void commit(File dir,OwnedSourceStage staged) throws IOException{
        if(staged==null)throw new IOException("Original source staging missing");
        staged.publish(active(dir));
    }
    /** Opens one decoder, allowing repeated regions without decoding the whole photo.
     * The caller owns this session and MUST close it after rendering.
     */
    static final class Session implements AutoCloseable {
        private final BitmapRegionDecoder decoder;
        final int width,height;
        Session(File file) throws IOException{
            if(!file.isFile())throw new IOException("Original image is not available");
            decoder=PlatformCompat.regionDecoder(file.getAbsolutePath());
            if(decoder==null)throw new IOException("Unsupported original image format");
            width=decoder.getWidth();height=decoder.getHeight();
            if(width<=0||height<=0||(long)width*height>400000000L){decoder.recycle();throw new IOException("Source dimensions exceed safe tile budget");}
        }
        Bitmap decode(SourceTilePlan.Tile t){
            if(Thread.currentThread().isInterrupted())throw new CancellationException("Tile decoding canceled");
            if(t.decodeX0<0||t.decodeY0<0||t.decodeX1>width||t.decodeY1>height)
                throw new IllegalArgumentException("Tile falls outside original image");
            BitmapFactory.Options opts=new BitmapFactory.Options();
            opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
            opts.inSampleSize=1;
            Bitmap bitmap=decoder.decodeRegion(new Rect(t.decodeX0,t.decodeY0,t.decodeX1,t.decodeY1),opts);
            if(bitmap==null)throw new IllegalStateException("Unable to decode original source tile");
            return bitmap;
        }
        List<SourceTilePlan.Tile> plan(){return SourceTilePlan.create(width,height,512,2);}
        @Override public void close(){decoder.recycle();}
    }
}

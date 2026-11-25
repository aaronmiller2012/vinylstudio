package com.halftonestudio;

import android.graphics.*;
import android.os.Handler;
import android.widget.ImageView;
import android.util.LruCache;
import android.util.Log;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.concurrent.*;

/** Thumbnail encoding and bounded, weak-view asynchronous loading for gallery cards. */
final class StudioProjectThumbnails implements AutoCloseable {
    private final Handler ui;
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(6*1024*1024){
        @Override protected int sizeOf(String key,Bitmap value){return value.getByteCount();}
    };
    // Fixed-size queue: a rapid gallery scroll must not accumulate unlimited
    // decode requests or retain old ImageViews indirectly. New visible cards
    // replace the oldest unstarted work; the per-ImageView tag rejects stale binds.
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0L,
        TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(32),r->{
            Thread t=new Thread(r,"VinylThumbs");t.setPriority(Thread.NORM_PRIORITY-1);return t;
        },new ThreadPoolExecutor.DiscardOldestPolicy());
    private volatile int epoch;
    private volatile boolean closed;

    StudioProjectThumbnails(Handler main){ui=main;}

    static byte[] encode(Bitmap source) throws IOException {
        if(source==null)throw new IOException("Cannot preview an empty project");
        final int side=360;
        Bitmap thumb=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888);
        try{
            Canvas canvas=new Canvas(thumb);canvas.drawColor(0xff182029);
            float scale=Math.min((side-16f)/source.getWidth(),(side-16f)/source.getHeight());
            float w=source.getWidth()*scale,h=source.getHeight()*scale;
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(source,null,new RectF((side-w)/2,(side-h)/2,(side+w)/2,(side+h)/2),paint);
            ByteArrayOutputStream out=new ByteArrayOutputStream(48_000);
            if(!thumb.compress(Bitmap.CompressFormat.JPEG,86,out))throw new IOException("Preview JPEG failed");
            return out.toByteArray();
        }finally{thumb.recycle();}
    }

    void bind(StudioProjectLibrary library,String id,ImageView view){
        view.setTag(id);
        Bitmap hit=cache.get(id);
        if(hit!=null&&!hit.isRecycled()){view.setImageBitmap(hit);return;}
        view.setImageDrawable(null);
        final int requestEpoch=epoch;
        WeakReference<ImageView> target=new WeakReference<>(view);
        try{worker.execute(()->{
            Bitmap decoded=null;
            try{
                File file=library.preview(id);
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);
                int edge=Math.max(bounds.outWidth,bounds.outHeight);
                if(bounds.outWidth<1||bounds.outHeight<1||edge>12000||
                   (long)bounds.outWidth*bounds.outHeight>48_000_000L)
                    throw new IOException("Invalid thumbnail dimensions");
                BitmapFactory.Options options=new BitmapFactory.Options();
                int sample=1;
                while(edge/sample>480&&sample<128)sample*=2;
                options.inSampleSize=sample;
                // Stored thumbnails are JPEGs. RGB_565 halves cache/decode memory.
                options.inPreferredConfig=Bitmap.Config.RGB_565;
                decoded=BitmapFactory.decodeFile(file.getAbsolutePath(),options);
            }catch(IOException | RuntimeException e){Log.w("VinylLibrary","Thumbnail unavailable",e);}
            catch(OutOfMemoryError memory){Log.w("VinylLibrary","Thumbnail omitted: memory pressure",memory);}
            final Bitmap ready=decoded;
            ui.post(()->{
                if(closed||requestEpoch!=epoch){if(ready!=null)ready.recycle();return;}
                if(ready==null)return;
                cache.put(id,ready);
                ImageView image=target.get();
                if(image!=null&&id.equals(image.getTag()))image.setImageBitmap(ready);
            });
        });}catch(RejectedExecutionException ignored){}
    }
    void remove(String id){epoch++;cache.remove(id);}
    void evictAll(){epoch++;worker.getQueue().clear();cache.evictAll();}
    @Override public void close(){closed=true;epoch++;worker.shutdownNow();cache.evictAll();}
}

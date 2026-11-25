package com.halftonestudio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** Fully native HSV hue/saturation picker. Brightness can be changed separately.
 * No browser, heavyweight color library or remote assets are used.
 */
public final class VinylColorWheel extends View {
    public interface Listener {void onColor(int color);}
    private final Paint paint=new Paint(3);
    private Bitmap wheel;
    private int baseSize=0,color=Color.BLACK;
    private Listener listener;
    private final float[] hsv=new float[3];
    public VinylColorWheel(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);setColor(color);setContentDescription(getResources().getString(R.string.accessibility_color_wheel));setFocusable(true);}
    public void setListener(Listener l){listener=l;}
    public int getColor(){return color;}
    public void setColor(int c){color=0xff000000|(c&0xffffff);Color.colorToHSV(color,hsv);invalidate();}
    public void setBrightness(float v){hsv[2]=Math.max(0,Math.min(1,v));changeColor();}
    private void changeColor(){color=Color.HSVToColor(hsv);if(listener!=null)listener.onColor(color);invalidate();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);int n=Math.min(w,h);if(n<1)return;
        baseSize=n;int[] pixels=new int[n*n];float center=n/2f,r=n*.47f;
        float[] v=new float[]{0f,0f,1f};
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){
            float dx=x-center,dy=y-center,length=(float)Math.sqrt(dx*dx+dy*dy);
            if(length>r){pixels[y*n+x]=0x00000000;continue;}
            v[0]=((float)Math.toDegrees(Math.atan2(dy,dx))+360f)%360f;
            v[1]=Math.min(1f,length/r);
            pixels[y*n+x]=Color.HSVToColor(v);
        }
        wheel=Bitmap.createBitmap(pixels,n,n,Bitmap.Config.ARGB_8888);
    }
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(wheel==null)return;
        float x=(getWidth()-baseSize)/2f,y=(getHeight()-baseSize)/2f,r=baseSize*.47f;
        paint.setColor(Color.WHITE);canvas.drawBitmap(wheel,x,y,paint);
        double a=Math.toRadians(hsv[0]);float pX=x+baseSize*.5f+(float)Math.cos(a)*r*hsv[1];
        float pY=y+baseSize*.5f+(float)Math.sin(a)*r*hsv[1];
        paint.setStyle(Paint.Style.FILL);paint.setColor(color);canvas.drawCircle(pX,pY,12,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(4);paint.setColor(Color.WHITE);canvas.drawCircle(pX,pY,12,paint);
        paint.setColor(Color.BLACK);paint.setStrokeWidth(1.5f);canvas.drawCircle(pX,pY,14,paint);
        paint.setStyle(Paint.Style.FILL);
    }
    @Override public boolean onTouchEvent(MotionEvent e){int act=e.getActionMasked();
        if(act!=MotionEvent.ACTION_DOWN&&act!=MotionEvent.ACTION_MOVE&&act!=MotionEvent.ACTION_UP)return false;
        if(act==MotionEvent.ACTION_DOWN)getParent().requestDisallowInterceptTouchEvent(true);
        float cx=getWidth()/2f,cy=getHeight()/2f,dx=e.getX()-cx,dy=e.getY()-cy;
        float r=baseSize*.47f;
        if(r<1)return true;
        hsv[0]=((float)Math.toDegrees(Math.atan2(dy,dx))+360f)%360f;
        hsv[1]=Math.max(0,Math.min(1,(float)Math.hypot(dx,dy)/r));
        changeColor();if(act==MotionEvent.ACTION_UP)performClick();return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}

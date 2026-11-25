package com.halftonestudio;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;

/** One 2x2 repeating tile, transformed in artboard coordinates. No draw loops in onDraw. */
final class CheckerboardPainter {
    private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix=new Matrix();
    private final Bitmap bitmap;
    CheckerboardPainter(int firstColor,int secondColor){
        bitmap=Bitmap.createBitmap(new int[]{firstColor,secondColor,secondColor,firstColor},
                2,2,Bitmap.Config.ARGB_8888);
        paint.setFilterBitmap(false); // avoid blended checkerboard tile boundaries
        paint.setAntiAlias(false);
        paint.setShader(new BitmapShader(bitmap,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT));
    }
    void draw(Canvas canvas,float width,float height,float squareSize){
        if(width<=0||height<=0)return;
        matrix.reset();matrix.setScale(Math.max(.01f,squareSize),Math.max(.01f,squareSize));
        paint.getShader().setLocalMatrix(matrix);
        canvas.drawRect(0,0,width,height,paint);
    }
}

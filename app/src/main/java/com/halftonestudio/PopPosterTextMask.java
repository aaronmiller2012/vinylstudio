package com.halftonestudio;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;

/** Android-only typography adapter. Geometry generator remains plain Java. */
final class PopPosterTextMask {
    private PopPosterTextMask() {}
    static byte[] create(String text,int w,int h,float frameFraction) {
        if(text==null||text.trim().isEmpty())return new byte[w*h];
        Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        try{
            Canvas canvas=new Canvas(bitmap);
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
            paint.setColor(Color.WHITE);
            paint.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));
            String label=text.trim();int frame=Math.round(frameFraction*Math.min(w,h));
            float bh=h*.14f;float usableWidth=w-frame*2f,usableHeight=bh*.72f;
            paint.setTextSize(usableHeight);
            float rendered=paint.measureText(label);
            if(rendered>usableWidth*.86f)paint.setTextSize(paint.getTextSize()*(usableWidth*.86f/rendered));
            // Use the actual Android font outlines rather than drawText rasterization.
            float x=(w-paint.measureText(label))*.5f;
            Paint.FontMetrics fm=paint.getFontMetrics();
            float baseline=h-frame-bh*.5f-(fm.ascent+fm.descent)*.5f;
            Path glyphs=new Path();paint.getTextPath(label,0,label.length(),x,baseline,glyphs);
            canvas.drawPath(glyphs,paint);
            int[] pix=new int[w*h];bitmap.getPixels(pix,0,w,0,0,w,h);byte[] mask=new byte[w*h];
            for(int i=0;i<mask.length;i++)mask[i]=(byte)(((pix[i]>>>24)>=128)?1:0);
            return mask;
        }finally{bitmap.recycle();}
    }
}

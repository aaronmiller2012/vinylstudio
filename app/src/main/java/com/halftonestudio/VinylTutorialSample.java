package com.halftonestudio;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.LinearGradient;
import android.graphics.Shader;

/** Bundled offline practice composition: no personal photos or permissions required. */
public final class VinylTutorialSample {
    private VinylTutorialSample(){}
    public static Bitmap create(){
        final int w=640,h=800;
        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0,0,w,h,new int[]{0xffF3E8D6,0xffC2D8DD,0xffD6B7A4},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
        p.setColor(0xffF5D68D);c.drawCircle(468,180,110,p);
        p.setColor(0xff5E8D7D);c.drawOval(40,550,610,950,p);
        p.setColor(0xff3D6B59);p.setStrokeWidth(23);p.setStyle(Paint.Style.STROKE);c.drawLine(314,620,324,285,p);p.setStyle(Paint.Style.FILL);
        p.setColor(0xff679A6B);c.drawOval(160,380,330,465,p);c.drawOval(320,420,510,515,p);
        int[] petals={0xffD94F6A,0xffE57675,0xffED8C69,0xffC63F62,0xffF39B70,0xffD26496};
        for(int i=0;i<petals.length;i++){double a=i*Math.PI*2/petals.length;p.setColor(petals[i]);float x=(float)(320+105*Math.cos(a)),y=(float)(276+105*Math.sin(a));c.save();c.rotate((float)Math.toDegrees(a),320,276);c.drawOval(270,112,370,310,p);c.restore();}
        p.setColor(0xff543B43);c.drawCircle(320,276,68,p);p.setColor(0xffDDA64F);c.drawCircle(320,276,46,p);
        p.setColor(0xff634A56);for(int i=0;i<10;i++){double a=i*Math.PI*2/10;c.drawCircle((float)(320+25*Math.cos(a)),(float)(276+25*Math.sin(a)),5,p);}
        return b;
    }
}

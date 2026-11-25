package com.halftonestudio;
import android.graphics.Path;
import android.graphics.PathMeasure;
import java.util.Locale;
/** Export one continuous centerline; not a closed vinyl silhouette. */
public final class ContinuousLineSvg {
    private ContinuousLineSvg() {}
    public static String svg(Path path,int width,int height,float widthMm){
        return svg(path,width,height,widthMm,.35f);
    }
    public static String svg(Path path,int width,int height,float widthMm,float strokeMm){
        if(path==null||width<1||height<1||widthMm<=0||strokeMm<.05f||strokeMm>5f||!Float.isFinite(strokeMm))throw new IllegalArgumentException();
        PathMeasure m=new PathMeasure(path,false);
        float length=m.getLength();if(length<=0)throw new IllegalArgumentException("Empty path");
        float heightMm=widthMm*height/((float)width);
        float strokeSvg=strokeMm*width/widthMm;
        StringBuilder s=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
          .append(String.format(Locale.US,"%.3fmm\" height=\"%.3fmm\" viewBox=\"0 0 %d %d\"><path fill=\"none\" stroke=\"#000000\" stroke-width=\"%.5f\" d=\"",widthMm,heightMm,width,height,strokeSvg));
        float[] position=new float[2];
        int steps=Math.max(1,(int)Math.ceil(length/1.2f));
        if(steps>400000)throw new IllegalArgumentException("Toolpath too complex");
        for(int i=0;i<=steps;i++){
            m.getPosTan(length*i/(float)steps,position,null);
            s.append(i==0?'M':'L');
            SvgNumberWriter.append(s,position[0],3).append(' ');
            SvgNumberWriter.append(s,position[1],3).append(' ');
        }
        if(m.nextContour())throw new IllegalArgumentException("Multiple contours prohibited");
        return s.append("\"/></svg>").toString();
    }
}

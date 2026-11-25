package com.halftonestudio;
import android.graphics.Path;
import android.graphics.PathMeasure;
/** Conservative open-toolpath preflight. Does not establish vinyl weedability. */
public final class ContinuousToolpathValidator {
    private ContinuousToolpathValidator(){}
    public static final class Report {
        public final boolean valid;public final String message;
        Report(boolean valid,String message){this.valid=valid;this.message=message;}
    }
    public static Report check(Path path,int width,int height,float widthMm){return check(path,width,height,widthMm,true);}
    /** Regular photo-mapped space-filling curves are rectangle-bounded. */
    public static Report check(Path path,int width,int height,float widthMm,boolean circular){
        if(path==null||width<1||height<1||!(widthMm>0)||!Float.isFinite(widthMm))return new Report(false,"Invalid geometry or dimensions");
        PathMeasure measure=new PathMeasure(path,false);
        float length=measure.getLength();
        if(!Float.isFinite(length)||length<=0)return new Report(false,"Empty or non-finite centerline");
        if(measure.nextContour())return new Report(false,"Disconnected contours are not permitted");
        android.graphics.RectF bounds=new android.graphics.RectF();path.computeBounds(bounds,true);
        float eps=.15f;
        if(bounds.left < -eps||bounds.top < -eps||bounds.right >width+eps||bounds.bottom>height+eps)
            return new Report(false,"Path exceeds artwork bounds");
        int samples=Math.max(64,Math.min(40000,(int)Math.ceil(length/2f)));
        PathMeasure m=new PathMeasure(path,false);float[] xy=new float[2];
        float cx=(width-1)*.5f,cy=(height-1)*.5f,r=(Math.min(width,height)-2)*.5f;
        for(int i=0;i<=samples;i++){
            if(!m.getPosTan(length*i/(float)samples,xy,null))return new Report(false,"Invalid curve sample");
            if(!Float.isFinite(xy[0])||!Float.isFinite(xy[1]))return new Report(false,"Non-finite coordinates");
            if(circular&&Math.hypot(xy[0]-cx,xy[1]-cy)>r+.6f)return new Report(false,"Path extends beyond circular artwork");
        }
        return new Report(true,"Centerline checked: one contour; "+(circular?"circle":"rectangle")+"-bounded; "+samples+" samples. Self-intersections and minimum gap NOT certified.");
    }
}

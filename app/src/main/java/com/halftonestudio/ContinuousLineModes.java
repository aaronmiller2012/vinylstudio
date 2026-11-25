package com.halftonestudio;

import android.graphics.Bitmap;
import android.graphics.Path;

/**
 * Four connected, circle-bounded centerline toolpaths.
 * A single Path/MOVE_TO does not constitute a closed vinyl-cut silhouette.
 * Run generation off the UI thread.
 */
public final class ContinuousLineModes {
    private ContinuousLineModes() {}
    public enum LineStyle { SPIRAL, CONCENTRIC_RINGS, SINE_RASTER, CONTOUR_HATCH, ADAPTIVE_HILBERT, TONAL_SPIROGRAPH, GOSPER, PEANO, SIERPINSKI, WUNDERLICH, MOORE, COIL, MEURTHE }

    /** All seven new curve families share the safe standalone geometry generator. */
    public static int curveFamily(LineStyle style){
        if(style==null)return -1;
        switch(style){
            case GOSPER:return SpaceFillingCurves.GOSPER;
            case PEANO:return SpaceFillingCurves.PEANO;
            case SIERPINSKI:return SpaceFillingCurves.SIERPINSKI;
            case WUNDERLICH:return SpaceFillingCurves.WUNDERLICH;
            case MOORE:return SpaceFillingCurves.MOORE;
            case COIL:return SpaceFillingCurves.COIL;
            case MEURTHE:return SpaceFillingCurves.MEURTHE;
            default:return -1;
        }
    }
    /** Direct 1-contour Android wrapper; no geometry rebuilt during UI drawing. */
    public static Path curvePath(SpaceFillingCurves.Result result){
        if(result==null||result.xy.length<4)throw new IllegalArgumentException("Empty curve");
        Path p=new Path();p.moveTo(result.xy[0],result.xy[1]);
        for(int i=2;i<result.xy.length;i+=2)p.lineTo(result.xy[i],result.xy[i+1]);
        return p;
    }
    /** Returns exactly one open contour: one moveTo, followed only by lineTo. */
    public static Path generatePath(Bitmap portrait, LineStyle style, int detailDensity) {
        return generatePath(portrait,style,detailDensity,false,120f);
    }
    public static Path generatePath(Bitmap portrait, LineStyle style, int detailDensity,boolean circularCrop) {
        return generatePath(portrait,style,detailDensity,circularCrop,120f);
    }
    public static Path generatePath(Bitmap portrait, LineStyle style, int detailDensity,
                                    boolean circularCrop,float artworkWidthMm) {
        if(!Float.isFinite(artworkWidthMm)||artworkWidthMm<=0)
            throw new IllegalArgumentException("Invalid artwork width");
        if (portrait == null || portrait.isRecycled() || style == null)
            throw new IllegalArgumentException("Portrait and style are required");
        if (detailDensity < 1 || detailDensity > 100)
            throw new IllegalArgumentException("detailDensity must be 1..100");
        int w = portrait.getWidth(), h = portrait.getHeight();
        if (w < 32 || h < 32 || w > 4096 || h > 4096 || (long) w * h > 8_000_000L)
            throw new IllegalArgumentException("Unsupported image size");
        if(curveFamily(style)>=0){
            SpaceFillingCurves.Options o=new SpaceFillingCurves.Options();
            o.family=curveFamily(style);o.depth=detailDensity<34?2:detailDensity<75?3:4;
            o.width=w;o.height=h;o.padding=.04f;o.photoInfluence=0f;
            int[] px=new int[w*h];portrait.getPixels(px,0,w,0,0,w,h);
            AdaptiveCurvePortrait.Options tonal=new AdaptiveCurvePortrait.Options();
            tonal.pixels=px;tonal.width=w;tonal.height=h;tonal.influence=.85f;
            return curvePath(AdaptiveCurvePortrait.render(SpaceFillingCurves.generate(o),w,h,tonal));
        }
        if(style==LineStyle.ADAPTIVE_HILBERT||style==LineStyle.TONAL_SPIROGRAPH)
            return ProceduralPortraitLines.generate(portrait,style==LineStyle.ADAPTIVE_HILBERT,detailDensity,artworkWidthMm);
        final float cx = (w - 1) / 2f, cy = (h - 1) / 2f;
        final float radius = (Math.min(w, h) - 3) / 2f;
        final float pitch = Math.max(2.5f, radius / (10f + detailDensity * 0.46f));
        final Image image = new Image(portrait,artworkWidthMm);
        Builder b = new Builder(cx, cy, radius, 240_000,circularCrop,w,h);
        switch (style) {
            case SPIRAL: spiral(b, image, pitch, detailDensity, false); break;
            case CONCENTRIC_RINGS: spiral(b, image, pitch, detailDensity, true); break;
            case SINE_RASTER: raster(b, image, pitch, false); break;
            case CONTOUR_HATCH: raster(b, image, pitch, true); break;
            default: throw new IllegalArgumentException("Unrecognized style");
        }
        return b.finish();
    }

    /** Every turn joins the previous turn continuously. The rings mode adds radial waves. */
    private static void spiral(Builder b, Image img, float pitch, int density, boolean rings) {
        double t = 0;
        float usable = b.radius - pitch * 0.75f;
        while (true) {
            float base = pitch * 0.5f + pitch * (float)(t / (2 * Math.PI));
            if (base > usable) break;
            float c = (float)Math.cos(t), s = (float)Math.sin(t);
            // Non-circular artwork follows a rectangular/superellipse-style spiral.
            float dir=Math.max(Math.abs(c),Math.abs(s));
            float xUnit=b.circular?c:c/Math.max(.001f,dir);
            float yUnit=b.circular?s:s/Math.max(.001f,dir);
            float sx=b.cx+base*xUnit*(b.circular?1:b.halfWidth/b.radius);
            float sy=b.cy+base*yUnit*(b.circular?1:b.halfHeight/b.radius);
            float tone = img.dark(sx,sy,xUnit,yUnit), edge = img.edge(sx,sy);
            float phase = rings ? 11.0f : 6.0f;
            float amplitude = pitch * (rings ? 0.19f : 0.25f);
            float warp = amplitude * tone * (float)Math.sin(phase*t);
            // Gentle edge-responsive modulation; avoids discontinuous snapping.
            warp += pitch * 0.07f * edge * (float)Math.sin(t * 3.0);
            float r = clamp(base + warp, 0, usable);
            b.add(b.cx+r*xUnit*(b.circular?1:b.halfWidth/b.radius),b.cy+r*yUnit*(b.circular?1:b.halfHeight/b.radius));
            t += Math.min(0.12, 1.15 / Math.max(3, base));
        }
    }

    /**
     * One zig-zag traverse through horizontal circular chords.
     * Adjacent rows are joined along the circle boundary, never by a jump.
     */
    private static void raster(Builder b, Image img, float pitch, boolean contour) {
        final float r = b.radius - 1f;
        if(!b.circular){
            rectangularRaster(b,img,pitch,contour);return;
        }
        final int rows = Math.max(2, (int)Math.ceil((2*r)/pitch));
        for (int row = 0; row <= rows; row++) {if(((row)&63)==0)CancellationCheckpoint.check();
            float y = b.cy - r + 2*r*row/rows;
            float span = (float)Math.sqrt(Math.max(0, r*r-(y-b.cy)*(y-b.cy)));
            boolean rightward = row%2==0;
            float xStart = b.cx + (rightward ? -span : span);
            float xEnd = b.cx + (rightward ? span : -span);
            int samples = Math.max(1, (int)Math.ceil(2*span/1.4f));
            for (int k=0; k<=samples; k++) {if(((k)&63)==0)CancellationCheckpoint.check();
                float alpha = (float)k/samples;
                float x = xStart + (xEnd-xStart)*alpha;
                float v = img.dark(x,y,0,1), edge=img.edge(x,y);
                float osc = (float)Math.sin(alpha * Math.PI * Math.max(3, samples/5));
                // Fade waviness toward chord endpoints so the vertical connector
                // meets the next row at the actual circular boundary.
                float taper = (float)Math.sin(Math.PI*alpha);
                float variation = contour ? (v*0.2f + edge*0.65f) : v*0.7f;
                float yWarp = pitch * 0.30f * variation * osc * taper;
                // Restrict warped points to inscribed circle.
                b.add(x, y + yWarp);
            }
            // A short boundary-following connector to next row is added
            // automatically by sampling the circle arc, avoiding a straight
            // chord through the artwork that would be an unintended cut.
            if (row < rows) {
                float nextY = b.cy - r + 2*r*(row+1)/rows;
                float nextSpan = (float)Math.sqrt(Math.max(0,r*r-(nextY-b.cy)*(nextY-b.cy)));
                float sign = rightward ? 1f : -1f;
                float startAngle = (float)Math.atan2(y-b.cy, sign*span);
                float endAngle = (float)Math.atan2(nextY-b.cy, sign*nextSpan);
                if (Math.abs(endAngle-startAngle)>Math.PI) {
                    if (endAngle<startAngle)endAngle+=(float)(2*Math.PI);
                    else endAngle-=(float)(2*Math.PI);
                }
                int segments=Math.max(2,(int)Math.ceil(Math.abs(endAngle-startAngle)*r/1.5f));
                for(int k=1;k<=segments;k++) {if(((k)&63)==0)CancellationCheckpoint.check();
                    float a=startAngle+(endAngle-startAngle)*k/segments;
                    b.add(b.cx+r*(float)Math.cos(a),b.cy+r*(float)Math.sin(a));
                }
            }
        }
    }

    private static void rectangularRaster(Builder b,Image img,float pitch,boolean contour){
        float margin=1.5f;
        float top=margin,bottom=2*b.cy-margin;
        float left=margin,right=2*b.cx-margin;
        int rows=Math.max(2,(int)Math.ceil((bottom-top)/pitch));
        for(int row=0;row<=rows;row++){if(((row)&63)==0)CancellationCheckpoint.check();
            if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Canceled");
            float y=top+(bottom-top)*row/rows;
            boolean forward=(row&1)==0;
            float start=forward?left:right,end=forward?right:left;
            int samples=Math.max(3,(int)Math.ceil((right-left)/1.7f));
            for(int k=0;k<=samples;k++){if(((k)&63)==0)CancellationCheckpoint.check();
                float alpha=k/(float)samples,x=start+(end-start)*alpha;
                float dark=img.dark(x,y,0,1),edge=img.edge(x,y);
                float taper=(float)Math.sin(Math.PI*alpha);
                float osc=(float)Math.sin(alpha*Math.PI*Math.max(3,samples/5));
                float influence=contour?dark*.2f+edge*.65f:dark*.70f;
                b.add(x,clamp(y+pitch*.30f*influence*osc*taper,top,bottom));
            }
            if(row<rows)b.add(end,top+(bottom-top)*(row+1)/rows);
        }
    }

    private static float clamp(float v, float min, float max) { return Math.max(min,Math.min(max,v)); }

    private static final class Builder {
        final float cx,cy,radius,halfWidth,halfHeight;final boolean circular; final int limit; final Path path = new Path();
        int points=0;
        Builder(float x,float y,float r,int limit,boolean circular,int width,int height) { cx=x;cy=y;radius=r;this.limit=limit;this.circular=circular;halfWidth=(width-3)/2f;halfHeight=(height-3)/2f; }
        void add(float x,float y) {
            if (points >= limit) throw new IllegalArgumentException("Excessive path complexity: reduce density");
            if (!Float.isFinite(x)||!Float.isFinite(y)) throw new IllegalArgumentException("Invalid path coordinate");
            float dx=x-cx,dy=y-cy,d=(float)Math.hypot(dx,dy);
            if(circular){if(d>radius&&d>0f){x=cx+dx*radius/d;y=cy+dy*radius/d;}}
            else{x=clamp(x,cx-halfWidth,cx+halfWidth);y=clamp(y,cy-halfHeight,cy+halfHeight);}
            if(points++==0)path.moveTo(x,y);else path.lineTo(x,y);
        }
        Path finish() { if(points<2)throw new IllegalStateException("Path too short");return path; }
    }

    private static final class Image {
        final PortraitStructureMap map;
        final ScaleAwarePhotoSampler photo;
        final float pixelsToMm;
        Image(Bitmap bmp,float physicalWidthMm) {
            int w=bmp.getWidth(),h=bmp.getHeight();int[] px=new int[w*h];
            bmp.getPixels(px,0,w,0,0,w,h);
            map=new PortraitStructureMap(px,w,h);
            photo=new ScaleAwarePhotoSampler(px,w,h,physicalWidthMm,physicalWidthMm*h/(float)w,0xffffffff);
            pixelsToMm=physicalWidthMm/w;
        }
        float dark(float x,float y,float nx,float ny){
            float mmX=(x+.5f)*pixelsToMm,mmY=(y+.5f)*pixelsToMm;
            // Calibrated tone preserves the existing aesthetic; a small physical
            // footprint makes fine structure less dependent on image pixels.
            return clamp(.76f*map.dark(x,y)+.24f*photo.lineDarkness(
                mmX,mmY,nx,ny,.48f),0,1);
        }
        float edge(float x,float y){return map.edge(x,y);}
    }
}

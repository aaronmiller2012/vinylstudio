package com.halftonestudio;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import java.util.Locale;

/**
 * Continuous centerline swirl. One moveTo and lineTo-only construction.
 * Output is a toolpath (open SVG path), NOT a closed vinyl shape.
 * Generate on a worker thread; use Result.draw() on the UI thread.
 */
public final class SingleLineSwirlEngine {
    private SingleLineSwirlEngine() {}

    public static final class Options {
        public float diameterMm = 180f;
        public float pitchMm = 2.5f;
        public float sampleStepPx = 1.6f;
        public float toneAmplitude = 0.30f; // fraction of pitch, [0..0.4]
        public float edgePull = 0.22f;      // fraction of pitch, [0..0.35]
        public float edgeThreshold = 0.16f;
        public int smoothingRadius = 2;
        public boolean clockwise = true;
        public float maxPoints = 150000;
    }

    public static final class Result {
        public final Path path;
        public final float centerX, centerY, radiusPx;
        public final int pointCount;
        public final float diameterMm;
        private final float[] xs, ys;
        private Result(Path path, float[] xs, float[] ys, int count,
                       float cx, float cy, float radius, float diameterMm) {
            this.path = path;
            this.xs = xs; this.ys = ys; pointCount = count;
            centerX = cx; centerY = cy; radiusPx = radius;
            this.diameterMm = diameterMm;
        }

        public void draw(Canvas canvas, Paint paint) {
            if (canvas == null || paint == null) throw new IllegalArgumentException();
            Paint.Style prior = paint.getStyle();
            canvas.drawPath(path, paint);
            paint.setStyle(prior);
        }

        /** One single open path; same coordinates as Android Path. */
        public String toSvg() {
            StringBuilder svg = new StringBuilder(pointCount * 21 + 300);
            svg.append(String.format(Locale.US,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%.4fmm\" height=\"%.4fmm\" viewBox=\"0 0 %.3f %.3f\">",
                diameterMm, diameterMm, 2f * radiusPx, 2f * radiusPx));
            svg.append("<path fill=\"none\" stroke=\"#000000\" stroke-width=\"0.2\" d=\"");
            for (int i = 0; i < pointCount; i++) {if(((i)&63)==0)CancellationCheckpoint.check();
                svg.append(i == 0 ? 'M' : 'L');
                svg.append(String.format(Locale.US, "%.3f %.3f ", xs[i], ys[i]));
            }
            return svg.append("\"/></svg>").toString();
        }
    }

    /**
     * Source is interpreted as a square circular-crop image. Pixels outside
     * the inscribed circle are never sampled, and all output remains inside.
     */
    private static void checkCancelled(){
        if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Superseded swirl render");
    }
    public static Result generate(Bitmap source, Options options) {
        if (source == null || source.isRecycled() || options == null)
            throw new IllegalArgumentException("Bitmap and options required");
        final int w = source.getWidth(), h = source.getHeight();
        if (w < 24 || h < 24 || w > 8192 || h > 8192 || (long) w * h > 16000000L)
            throw new IllegalArgumentException("Unsupported source dimensions");
        if (!(options.diameterMm > 0) || !(options.pitchMm >= 0.25f) ||
            !(options.sampleStepPx >= 0.3f) || options.smoothingRadius < 0 ||
            options.smoothingRadius > 8 || options.maxPoints < 16 ||
            options.maxPoints > 500000)
            throw new IllegalArgumentException("Invalid options");

        int[] argb = new int[w * h];
        source.getPixels(argb, 0, w, 0, 0, w, h);
        float[] grey = new float[w * h];
        for (int i = 0; i < argb.length; i++) {if(((i)&63)==0)CancellationCheckpoint.check();
            if((i&4095)==0)checkCancelled();
            int p = argb[i];
            float a = ((p >>> 24) & 255) / 255f;
            float r = ((p >>> 16) & 255) / 255f;
            float g = ((p >>> 8) & 255) / 255f;
            float b = (p & 255) / 255f;
            grey[i] = a * (0.2126f * r + 0.7152f * g + 0.0722f * b) + (1f - a);
        }
        PortraitStructureMap portraitMap=new PortraitStructureMap(argb,w,h);
        float[] smoothed = smooth(grey, w, h, options.smoothingRadius);
        float[] gradients = new float[w * h];
        float[] gx = new float[w * h], gy = new float[w * h];
        float maxG = 0f;
        for (int y = 1; y < h-1; y++) for (int x = 1; x < w-1; x++) {if(((x)&63)==0)CancellationCheckpoint.check();
            if(x==1)checkCancelled();
            int i = y*w+x;
            float dx = 3*(smoothed[i-w+1]-smoothed[i-w-1]) +
                       10*(smoothed[i+1]-smoothed[i-1]) +
                       3*(smoothed[i+w+1]-smoothed[i+w-1]);
            float dy = 3*(smoothed[i+w-1]-smoothed[i-w-1]) +
                       10*(smoothed[i+w]-smoothed[i-w]) +
                       3*(smoothed[i+w+1]-smoothed[i-w+1]);
            gx[i]=dx;gy[i]=dy;
            gradients[i]=(float)Math.hypot(dx,dy);
            maxG=Math.max(maxG,gradients[i]);
        }
        if (maxG > 0) for(int i=0;i<gradients.length;i++) gradients[i]/=maxG;

        float cx=(w-1)*0.5f, cy=(h-1)*0.5f;
        float radius=Math.min(w,h)*0.5f - 1.25f;
        float pitchPx=options.pitchMm/options.diameterMm*(2*radius);
        // Keep a meaningful spiral, avoid enormous step counts.
        if (pitchPx < 0.75f) throw new IllegalArgumentException("Pitch too fine for input resolution");
        float toneAmp=clamp(options.toneAmplitude,0f,0.4f);
        float edgeAmp=clamp(options.edgePull,0f,0.35f);
        float threshold=clamp(options.edgeThreshold,0f,1f);
        int limit=(int)options.maxPoints;
        float[] px=new float[limit], py=new float[limit];
        Path path=new Path();
        float maxR=radius - pitchPx*0.7f;
        double theta=0;
        int n=0;
        float sign=options.clockwise ? 1f : -1f;
        double maxTheta=(maxR-pitchPx*0.55f) * 2.0*Math.PI/pitchPx;
        while(theta <= maxTheta) {
            if((n&1023)==0)checkCancelled();
            float base=(float)(pitchPx*0.55 + pitchPx*theta/(2*Math.PI));
            float angle=(float)(sign*theta);
            float cs=(float)Math.cos(angle), sn=(float)Math.sin(angle);
            // Sample on the unmodified base spiral to prevent feedback oscillation.
            float sx=cx+base*cs, sy=cy+base*sn;
            float value=sample(smoothed,w,h,sx,sy);
            float edge=sample(gradients,w,h,sx,sy);
            float dx=sample(gx,w,h,sx,sy);
            float dy=sample(gy,w,h,sx,sy);
            // Tonal phase modulation is continuous, making dark regions
            // more visibly oscillatory without introducing subpaths.
            float dark=Math.min(1f,portraitMap.dark(sx,sy)+.20f*portraitMap.edge(sx,sy));
            float oscillation=(float)Math.sin(theta*5.0);
            float tonal=pitchPx*toneAmp*dark*oscillation;
            // Projection onto gradient direction strengthens sharp contours.
            // Bound edge displacement to protect neighboring turns.
            float radialGradient=(dx*cs+dy*sn);
            float edgeTerm=Math.max(edge,portraitMap.edge(sx,sy))>=threshold ?
                    pitchPx*edgeAmp*(float)Math.tanh(radialGradient*0.25f) : 0f;
            float r=clamp(base+tonal+edgeTerm,0f,maxR);
            float x=cx+r*cs, y=cy+r*sn;
            if (n >= limit) throw new IllegalStateException("Point limit exceeded; increase pitch");
            // Transform to a square artboard whose origin is top-left.
            x += radius-cx;
            y += radius-cy;
            px[n]=x;py[n]=y;
            if(n==0)path.moveTo(x,y); else path.lineTo(x,y);
            n++;
            double delta=Math.min(0.21,options.sampleStepPx/Math.max(base,2f));
            theta+=Math.max(delta,0.004);
        }
        if(n<2)throw new IllegalStateException("No path generated");
        float[] fx=new float[n],fy=new float[n];
        System.arraycopy(px,0,fx,0,n);System.arraycopy(py,0,fy,0,n);
        return new Result(path,fx,fy,n,radius,radius,radius,options.diameterMm);
    }

    private static float clamp(float x,float lo,float hi){return Math.max(lo,Math.min(hi,x));}
    private static float sample(float[] a,int w,int h,float x,float y){
        x=clamp(x,0,w-1);y=clamp(y,0,h-1);
        int x0=(int)x,y0=(int)y,x1=Math.min(w-1,x0+1),y1=Math.min(h-1,y0+1);
        float fx=x-x0,fy=y-y0;
        return (a[y0*w+x0]*(1-fx)+a[y0*w+x1]*fx)*(1-fy)+
               (a[y1*w+x0]*(1-fx)+a[y1*w+x1]*fx)*fy;
    }
    private static float[] smooth(float[] src,int w,int h,int radius){
        if(radius==0)return src;
        // Separable binomial-like 3-tap blur repeated; preserves speed.
        float[] a=src.clone(),b=new float[a.length];
        for(int pass=0;pass<radius;pass++){if(((pass)&63)==0)CancellationCheckpoint.check();
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                int i=y*w+x;
                b[i]=(a[y*w+Math.max(0,x-1)]+2*a[i]+a[y*w+Math.min(w-1,x+1)])*0.25f;
            }
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                int i=y*w+x;
                a[i]=(b[Math.max(0,y-1)*w+x]+2*b[i]+b[Math.min(h-1,y+1)*w+x])*0.25f;
            }
        }
        return a;
    }
}

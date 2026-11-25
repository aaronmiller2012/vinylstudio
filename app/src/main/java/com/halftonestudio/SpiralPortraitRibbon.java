package com.halftonestudio;

import android.graphics.Path;
import java.util.concurrent.CancellationException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Original, cut-ready photographic spiral. The carrier is an Archimedean spiral,
 * while independent positive/negative widths reproduce the image's tonal values.
 * Neither the old oscillating centerline nor another style is altered.
 *
 * Opaque portraits generate one closed contour; alpha-cutout portraits generate
 * multiple closed, separated cuts INSIDE the material mask. No
 * SVG stroke, clipPath, duplicated edges, or preview/export disagreement.
 * Prepared image coordinates and the artwork's physical dimensions are shared
 * with ScaleAwarePhotoSampler. This class contains no Spiral Betty source code.
 */
public final class SpiralPortraitRibbon {
    private SpiralPortraitRibbon() {}

    public static final class Options {
        public int detail = 65;
        public float widthMm = 152.4f;
        public float minCutMm = .8f;
        public float minGapMm = .45f;
        public float insetMm = 0f;
        public int paper = 0xfff7f1e2;
        public boolean draft = false;
    }

    public static final class Result {
        public final Path cutPath;
        public final int imageWidth, imageHeight, samples, cutPieces;
        public final boolean hasTransparentMask;
        public final float widthMm, heightMm, pitchMm, minWidthMm, gapMm;
        public final float observedMinWidthMm, observedMaxWidthMm;
        public final boolean draft;
        private final float[] outsideX, outsideY, insideX, insideY;
        private final int[][] runs;
        private Result(Path cutPath, int w, int h, int points, Options o, float pitch, float gap,
                       float[] ox, float[] oy, float[] ix, float[] iy, int[][] safeRuns,
                       boolean masked, float observedMin,float observedMax) {
            this.cutPath=cutPath; imageWidth=w;imageHeight=h;samples=points;
            widthMm=o.widthMm;heightMm=o.widthMm*h/(float)w;
            pitchMm=pitch;minWidthMm=o.minCutMm;gapMm=gap;draft=o.draft;
            observedMinWidthMm=observedMin;observedMaxWidthMm=observedMax;
            outsideX=ox;outsideY=oy;insideX=ix;insideY=iy;
            runs=safeRuns;cutPieces=safeRuns.length;hasTransparentMask=masked;
        }
        /** Closed filled cuts; preview and SVG share the exact vertices. */
        public String svg() {
            if(draft)throw new IllegalStateException("Draft spiral cannot be exported");
            StringBuilder s=new StringBuilder(samples*44+320);
            s.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"");
            n(s,widthMm);s.append("mm\" height=\"");n(s,heightMm);
            s.append("mm\" viewBox=\"0 0 ");n(s,imageWidth);s.append(' ');n(s,imageHeight);
            s.append("\"><path fill=\"#171717\" d=\"");
            for(int[] run:runs){
                appendSmooth(s,outsideX,outsideY,false,run[0],run[1]);
                appendSmooth(s,insideX,insideY,true,run[0],run[1]);
                s.append('Z');
            }
            s.append("\"/></svg>\n");
            return s.toString();
        }
        /** Actual local-width measurements from the TWO exported ribbon edges.
         * Returns artwork coordinates in mm, not SVG padding or display pixels.
         * Samples are advisory: overlaps and weedability still need physical review.
         */
        public java.util.List<float[]> narrowCutLocations(float minimumMm,int limit){
            java.util.ArrayList<float[]> positions=new java.util.ArrayList<>();
            if(draft||limit<=0||minimumMm<=0||!Float.isFinite(minimumMm))return positions;
            float mmPerX=widthMm/imageWidth,mmPerY=heightMm/imageHeight;
            int separation=Math.max(12,samples/180);
            for(int[] run:runs){
                int previous=-separation;
                for(int i=run[0];i<run[1]&&positions.size()<limit;i++){
                    if((i&2047)==0&&Thread.currentThread().isInterrupted())
                        throw new CancellationException("Spiral warning scan canceled");
                    if(i-previous<separation)continue;
                    float dx=(outsideX[i]-insideX[i])*mmPerX;
                    float dy=(outsideY[i]-insideY[i])*mmPerY;
                    if((float)Math.hypot(dx,dy)+.003f<minimumMm){
                        positions.add(new float[]{(outsideX[i]+insideX[i])*.5f*mmPerX,
                            (outsideY[i]+insideY[i])*.5f*mmPerY});
                        previous=i;
                    }
                }
                if(positions.size()>=limit)break;
            }
            return positions;
        }
        public String summary(){return (hasTransparentMask ? (cutPieces+" separate alpha-clipped vinyl pieces") : "One continuous closed spiral ribbon")+
            ", "+samples+" samples, pitch "+pitchMm+
            " mm, minimum feature "+minWidthMm+" mm, minimum turn gap "+gapMm+
            " mm. Smooth geometric preview matches SVG. Test-cut before applying.";}
    }

    private static void checkpoint() {
        if(Thread.currentThread().isInterrupted())throw new CancellationException("Superseded spiral render");
    }
    private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    // Efficient fixed-point SVG numbers without per-vertex String.format or Locale allocations.
    private static void n(StringBuilder out,float v) {
        long q=Math.round(v*1000.0);if(q<0){out.append('-');q=-q;}
        out.append(q/1000).append('.');long f=q%1000;
        if(f<100)out.append('0');if(f<10)out.append('0');out.append(f);
    }
    private static void point(StringBuilder s,float x,float y){n(s,x);s.append(' ');n(s,y);s.append(' ');}
    /** Same smoothed midpoint quadratic sequence is used by Canvas and SVG. */
    private static void appendSmooth(StringBuilder s,float[] x,float[] y,boolean backwards,int from,int to){
        int count=to-from;if(count==0)return;
        int start=backwards?to-1:from, step=backwards?-1:1;
        if(!backwards){s.append('M');point(s,x[start],y[start]);}
        else {s.append('L');point(s,x[start],y[start]);}
        for(int j=1;j<count;j++){
            int k=start+j*step;
            if(j==count-1){s.append('L');point(s,x[k],y[k]);}
            else {int next=k+step;s.append('Q');point(s,x[k],y[k]);
                point(s,(x[k]+x[next])*.5f,(y[k]+y[next])*.5f);}
        }
    }
    private static void smoothPath(Path p,float[] x,float[] y,boolean backwards,int from,int to){
        int count=to-from,start=backwards?to-1:from,step=backwards?-1:1;
        if(!backwards)p.moveTo(x[start],y[start]);else p.lineTo(x[start],y[start]);
        for(int j=1;j<count;j++){
            int k=start+j*step;
            if(j==count-1)p.lineTo(x[k],y[k]);
            else{int next=k+step;p.quadTo(x[k],y[k],(x[k]+x[next])*.5f,(y[k]+y[next])*.5f);}
        }
    }
    private static float phototone(float darkness){
        float v=clamp((darkness-.06f)/.82f,0f,1f);
        return v*v*(3f-2f*v);
    }

    /**
     * The raster has already passed crop/Snip/alpha preparation. Build a
     * conservative one-pixel eroded subject mask; we never bridge across a
     * transparent zone. A 3x3 test also covers the half-pixel interpolation
     * between neighboring spiral samples and prevents white-background cuts.
     */
    private static byte[] allowedCore(int[] pixels,int w,int h) {
        boolean hasMask=false;
        for(int i=0;i<pixels.length;i++){
            if((i&65535)==0)checkpoint();
            if((pixels[i]>>>24)<224){hasMask=true;break;}
        }
        if(!hasMask)return null;
        byte[] ok=new byte[pixels.length];
        for(int y=1;y<h-1;y++){
            if((y&31)==0)checkpoint();
            for(int x=1;x<w-1;x++){
                boolean safe=true;
                for(int dy=-1;dy<=1&&safe;dy++)for(int dx=-1;dx<=1;dx++){
                    if((pixels[(y+dy)*w+x+dx]>>>24)<224){safe=false;break;}
                }
                if(safe)ok[y*w+x]=1;
            }
        }
        return ok;
    }
    private static boolean safe(byte[] core,int w,int h,float x,float y){
        if(!Float.isFinite(x)||!Float.isFinite(y)||x<0||y<0||x>=w||y>=h)return false;
        return core[((int)y)*w+(int)x]!=0;
    }
    private static boolean safeRibbon(byte[] core,int w,int h,float x,float y,
                                      float ox,float oy,float ix,float iy){
        if(core==null)return true;
        // Sample the cap and both ribbon edges. Do not generate cut material
        // through transparent image pixels, including anti-aliased boundaries.
        return safe(core,w,h,x,y)&&safe(core,w,h,ox,oy)&&safe(core,w,h,ix,iy)&&
               safe(core,w,h,(x+ox)*.5f,(y+oy)*.5f)&&
               safe(core,w,h,(x+ix)*.5f,(y+iy)*.5f)&&
               safe(core,w,h,(ox+ix)*.5f,(oy+iy)*.5f);
    }

    private static int[][] extractRuns(boolean[] valid,float[] ox,float[] oy,
                                       float[] ix,float[] iy,int count,float mmPerPx,
                                       float minCutMm){
        ArrayList<int[]> accepted=new ArrayList<>();
        for(int i=0;i<count;){
            if(!valid[i]){i++;continue;}
            int start=i++;float lengthMm=0f;
            while(i<count&&valid[i]){
                float px=(ox[i]+ix[i])*.5f,py=(oy[i]+iy[i])*.5f;
                float prevX=(ox[i-1]+ix[i-1])*.5f,prevY=(oy[i-1]+iy[i-1])*.5f;
                lengthMm+=(float)Math.hypot(px-prevX,py-prevY)*mmPerPx;
                i++;
            }
            // Discard short, unweedable fragments. Other styles' detail and
            // material controls remain untouched; no new UI parameter needed.
            if(i-start>=4&&lengthMm>=Math.max(minCutMm*2f,1.6f)){
                accepted.add(new int[]{start,i});
                if(accepted.size()>4000)
                    throw new IllegalArgumentException("Too many isolated spiral cut pieces for Cricut SVG export");
            }
        }
        return accepted.toArray(new int[accepted.size()][]);
    }

    public static Result generate(int[] pixels,int w,int h,Options o){
        if(pixels==null||o==null||w<32||h<32||(long)w*h!=pixels.length||
            o.detail<1||o.detail>100||!Float.isFinite(o.widthMm)||o.widthMm<10||o.widthMm>600||
            !Float.isFinite(o.minCutMm)||o.minCutMm<.25f||o.minCutMm>4||
            !Float.isFinite(o.minGapMm)||o.minGapMm<.25f||o.minGapMm>4||
            !Float.isFinite(o.insetMm)||o.insetMm<0||o.insetMm>20)
            throw new IllegalArgumentException("Invalid spiral portrait input or dimensions");
        checkpoint();
        float mmPerPx=o.widthMm/w;
        float radiusMm=Math.min(o.widthMm,o.widthMm*h/(float)w)*.5f-o.insetMm-.45f;
        float radiusPx=radiusMm/mmPerPx;
        if(radiusPx<20)throw new IllegalArgumentException("Spiral artboard too small for inset");
        float diameterMm=2f*radiusPx*mmPerPx;
        float requestedTurns=14f+o.detail*.35f; // Detail reuses the existing adjustment.
        float pitchMm=Math.max(diameterMm/(2f*requestedTurns),o.minCutMm+o.minGapMm+.20f);
        float pitchPx=pitchMm/mmPerPx;
        float gap=Math.max(o.minGapMm,.45f);
        float maxSideMm=.5f*(pitchMm-gap);
        float minSideMm=.5f*o.minCutMm;
        if(maxSideMm<=minSideMm)throw new IllegalArgumentException("Too many spiral turns for the minimum vinyl width and gap");
        float startR=Math.max(pitchPx,pitchPx*.9f+1.5f);
        float endR=radiusPx-maxSideMm/mmPerPx-1.5f;
        if(endR<=startR)throw new IllegalArgumentException("Spiral has no usable turns");
        float pxPerTurn=pitchPx/(float)(2.0*Math.PI);
        double maxTheta=(endR-startR)/pxPerTurn;
        int expected=Math.max(40,(int)Math.ceil(endR*maxTheta/(o.draft?2.1:1.25))+100);
        if(expected>160000)throw new IllegalArgumentException("Spiral is too dense for the available vector budget");
        float[] ox=new float[expected],oy=new float[expected],ix=new float[expected],iy=new float[expected];
        float[] widths=new float[expected];boolean[] valid=new boolean[expected];
        byte[] safeCore=allowedCore(pixels,w,h);
        float cx=w*.5f,cy=h*.5f;
        ScaleAwarePhotoSampler photo=new ScaleAwarePhotoSampler(pixels,w,h,o.widthMm,o.widthMm*h/(float)w,o.paper);
        double theta=0;
        float lastA=minSideMm,lastB=minSideMm;
        int count=0;
        float observedMin=Float.POSITIVE_INFINITY,observedMax=0f;
        while(theta<=maxTheta){
            if((count&511)==0)checkpoint();
            if(count>=expected)throw new IllegalStateException("Spiral sample budget exceeded");
            float angle=(float)theta,r=startR+pxPerTurn*angle;
            float cs=(float)Math.cos(theta),sn=(float)Math.sin(theta);
            float x=cx+r*cs,y=cy+r*sn;
            // Unit normal to the actual Archimedean tangent (not the radial
            // approximation, which distorts inner turns and tight bends).
            float tx=pxPerTurn*cs-r*sn,ty=pxPerTurn*sn+r*cs;
            float inv=(float)(1.0/Math.hypot(tx,ty));
            float nx=ty*inv,ny=-tx*inv;
            float probeMm=Math.min(maxSideMm*.48f,.85f);
            float ax=(x+nx*probeMm/mmPerPx)*mmPerPx,ay=(y+ny*probeMm/mmPerPx)*mmPerPx;
            float bx=(x-nx*probeMm/mmPerPx)*mmPerPx,by=(y-ny*probeMm/mmPerPx)*mmPerPx;
            float a=minSideMm+(maxSideMm-minSideMm)*phototone(photo.darknessAt(ax,ay));
            float b=minSideMm+(maxSideMm-minSideMm)*phototone(photo.darknessAt(bx,by));
            // Low-pass ONLY the width; never distort the centerline into wiggles.
            if(count>0){a=.50f*a+.50f*lastA;b=.50f*b+.50f*lastB;}
            lastA=a;lastB=b;
            ox[count]=x+nx*(a/mmPerPx);oy[count]=y+ny*(a/mmPerPx);
            ix[count]=x-nx*(b/mmPerPx);iy[count]=y-ny*(b/mmPerPx);
            widths[count]=a+b;
            valid[count]=safeRibbon(safeCore,w,h,x,y,ox[count],oy[count],ix[count],iy[count]);
            count++;
            double step=Math.min(.16,(o.draft?2.0:1.2)/Math.max(r,4f));
            theta+=Math.max(.003,step);
        }
        if(count<50)throw new IllegalArgumentException("Spiral too short");
        int[][] runs=extractRuns(valid,ox,oy,ix,iy,count,mmPerPx,o.minCutMm);
        if(runs.length==0)throw new IllegalArgumentException(
            "No cut-safe spiral inside the visible portrait. Increase artwork size or use a stencil.");
        int keptSamples=0;
        for(int[] run:runs){
            keptSamples+=run[1]-run[0];
            for(int i=run[0];i<run[1];i++){
                observedMin=Math.min(observedMin,widths[i]);
                observedMax=Math.max(observedMax,widths[i]);
            }
        }
        float[] aX=Arrays.copyOf(ox,count),aY=Arrays.copyOf(oy,count);
        float[] bX=Arrays.copyOf(ix,count),bY=Arrays.copyOf(iy,count);
        Path path=new Path();path.setFillType(Path.FillType.EVEN_ODD);
        for(int[] run:runs){
            smoothPath(path,aX,aY,false,run[0],run[1]);
            smoothPath(path,bX,bY,true,run[0],run[1]);
            path.close();
        }
        return new Result(path,w,h,keptSamples,o,pitchMm,gap,aX,aY,bX,bY,
            runs,safeCore!=null,observedMin,observedMax);
    }
}

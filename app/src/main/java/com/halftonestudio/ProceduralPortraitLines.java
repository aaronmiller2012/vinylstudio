package com.halftonestudio;

import android.graphics.Bitmap;
import android.graphics.Path;
/** Two experimental connected, circle-constrained procedural portrait paths.
 * Outputs are OPEN pen/engraving centerlines, never weedable vinyl ribbons. */
public final class ProceduralPortraitLines {
    private ProceduralPortraitLines() {}
    private static float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}
    private static final class Sampler {
        final PortraitStructureMap map;
        final ScaleAwarePhotoSampler photo;
        final float pixelMm;
        Sampler(Bitmap bitmap,float artworkWidthMm){int w=bitmap.getWidth(),h=bitmap.getHeight();
            int[] pixels=new int[w*h];bitmap.getPixels(pixels,0,w,0,0,w,h);
            map=new PortraitStructureMap(pixels,w,h);
            photo=new ScaleAwarePhotoSampler(pixels,w,h,artworkWidthMm,artworkWidthMm*h/(float)w,0xffffffff);
            pixelMm=artworkWidthMm/w;
        }
        float darkness(float x,float y){
            float base=map.dark(x,y);
            float sampled=photo.lineDarkness((x+.5f)*pixelMm,(y+.5f)*pixelMm,
                .7071068f,.7071068f,.45f);
            return Math.min(1f,.80f*base+.20f*sampled+.24f*map.edge(x,y));
        }
    }
    private static final class Writer {
        final Path path=new Path();final float cx,cy,r;int count=0;
        Writer(int w,int h){cx=(w-1)*.5f;cy=(h-1)*.5f;r=(Math.min(w,h)-4)*.5f;}
        void add(float x,float y){
            if(!Float.isFinite(x)||!Float.isFinite(y))throw new IllegalStateException("Invalid coordinate");
            float dx=x-cx,dy=y-cy,d=(float)Math.hypot(dx,dy);
            if(d>r){x=cx+dx*r/d;y=cy+dy*r/d;}
            if(count==0)path.moveTo(x,y);else path.lineTo(x,y);
            if(++count>180000)throw new IllegalStateException("Path exceeds complexity budget");
        }
    }
    private static void checkCancelled(){
        if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Superseded procedural portrait");
    }
    public static Path generate(Bitmap bitmap,boolean hilbert,int density){
        return generate(bitmap,hilbert,density,120f);
    }
    public static Path generate(Bitmap bitmap,boolean hilbert,int density,float artworkWidthMm){
        if(bitmap==null||bitmap.isRecycled()||density<1||density>100)
            throw new IllegalArgumentException("Image and detail 1..100 required");
        int w=bitmap.getWidth(),h=bitmap.getHeight();
        if(w<32||h<32||w>4096||h>4096||(long)w*h>8000000)
            throw new IllegalArgumentException("Unsupported image size");
        if(!Float.isFinite(artworkWidthMm)||artworkWidthMm<=0)
            throw new IllegalArgumentException("Invalid artwork width");
        Sampler sample=new Sampler(bitmap,artworkWidthMm);Writer writer=new Writer(w,h);
        if(hilbert)hilbert(writer,sample,density);else spirograph(writer,sample,density);
        return writer.path;
    }
    /** Hilbert lattice visits each node once. Tonal variation is encoded as
     * bounded local transverse wave folding, retaining global connectivity.
     * Fixed traversal depth avoids disjoint adaptive quadtree branches. */
    private static void hilbert(Writer out,Sampler image,int density){
        int order=density<30?5:density<65?6:7;
        int n=1<<order,total=n*n;
        float side=out.r*1.40f;
        float left=out.cx-side*.5f,top=out.cy-side*.5f;
        float spacing=side/(n-1);
        for(int i=0;i<total;i++){
            if((i&255)==0)checkCancelled();
            int[] xy=hilbertXY(n,i);
            float x=left+xy[0]*spacing,y=top+xy[1]*spacing;
            // Extra folded detail is proportional to local darkness and
            // always joins the next lattice node through the same path.
            if(i>0){
                int[] prev=hilbertXY(n,i-1);
                float ax=left+prev[0]*spacing,ay=top+prev[1]*spacing;
                float dark=image.darkness((x+ax)*.5f,(y+ay)*.5f);
                int folds=1+(int)(dark*(density/18f));
                if(folds>1){
                    float dx=x-ax,dy=y-ay,length=(float)Math.hypot(dx,dy);
                    float nx=-dy/Math.max(1e-5f,length),ny=dx/Math.max(1e-5f,length);
                    for(int j=1;j<folds*2;j++){
                        float t=j/(float)(folds*2);
                        float swing=(j%2==0?-1:1)*spacing*.20f*dark;
                        out.add(ax+dx*t+nx*swing,ay+dy*t+ny*swing);
                    }
                }
            }
            out.add(x,y);
        }
    }
    private static int[] hilbertXY(int n,int d){
        int x=0,y=0,t=d;
        for(int s=1;s<n;s*=2){
            int rx=1&(t/2),ry=1&(t^rx);
            if(ry==0){if(rx==1){x=s-1-x;y=s-1-y;}int z=x;x=y;y=z;}
            x+=s*rx;y+=s*ry;t/=4;
        }
        return new int[]{x,y};
    }
    /** Phase-integrated hypotrochoid-style sweep. Tonal modulation influences
     * local phase velocity smoothly; the continuous curve can self-intersect. */
    private static void spirograph(Writer out,Sampler image,int density){
        int points=6000+density*110;
        float radius=out.r*.90f;
        double phase=0,frequency=2.4+density*.045;
        for(int i=0;i<=points;i++){
            if((i&255)==0)checkCancelled();
            double t=i/(double)points*24*Math.PI;
            float bx=out.cx+radius*.59f*(float)Math.cos(t),by=out.cy+radius*.59f*(float)Math.sin(t);
            float dark=image.darkness(bx,by);
            frequency+=(2.4+density*.045+dark*2.0-frequency)*.018;
            phase+=frequency*24*Math.PI/points;
            float amp=radius*(.18f+.19f*dark);
            float x=bx+amp*(float)Math.cos(phase),y=by+amp*(float)Math.sin(phase);
            out.add(x,y);
        }
    }
}

package com.halftonestudio;

import java.util.concurrent.CancellationException;

/**
 * Immutable, physically-scaled, Android-free photographic sampling shared by
 * ribbon, discrete-motif and segmented-region engines.
 *
 * All API coordinates are millimeters. Original ARGB is referenced but never
 * modified; callers must keep the source immutable during generation.
 * Transparent pixels are composited over the selected substrate for gradients.
 * The image dimensions define the mm-to-pixel mapping, never the preview DPI.
 * No extra settings, UI sliders, or mutable static state.
 */
public final class ScaleAwarePhotoSampler {
    @FunctionalInterface public interface Coverage { float ofArgb(int argb); }

    public static final class RegionSignal {
        /** Mean luminance 0..1; gradient components and contrast in 8-bit levels. */
        public final float light, gradientX, gradientY, contrast;
        RegionSignal(float light,float gx,float gy,float contrast){
            this.light=light;this.gradientX=gx;this.gradientY=gy;this.contrast=contrast;
        }
    }

    // Clockwise unit directions; avoid eight trig calls per motif per layer.
    private static final float D=0.70710678f;
    private static final float[] OCTANTS_X={1,D,0,-D,-1,-D,0,D};
    private static final float[] OCTANTS_Y={0,D,1,D,0,-D,-1,-D};
    private final int[] argb;
    private final int width, height;
    private final float widthMm,heightMm,pixelMmX,pixelMmY;
    private final byte[] light; // 1 byte/pixel; no four-float image pyramid on mobile

    public ScaleAwarePhotoSampler(int[] source,int w,int h,float artWidthMm,float artHeightMm,int substrate){
        if(source==null||w<1||h<1||(long)w*h!=source.length||
           !Float.isFinite(artWidthMm)||!Float.isFinite(artHeightMm)||artWidthMm<=0||artHeightMm<=0)
            throw new IllegalArgumentException("Invalid physical photo input");
        argb=source;width=w;height=h;widthMm=artWidthMm;heightMm=artHeightMm;
        pixelMmX=artWidthMm/w;pixelMmY=artHeightMm/h;
        light=cachedLight(source,substrate);
    }
    /** Single-entry cache of the 1-byte luminance plane. The engines treat the
     * source raster as immutable, and StudioActivity reuses the same prepared
     * tone/draft array between renders, so geometry-only edits (spacing, angle,
     * width, pattern...) no longer recompute luminance for every pixel.
     * The source is weakly held; the plane is released on explicit photo-change
     * / memory-trim, or on the next access after source collection. Computing
     * under the lock prevents duplicate large allocations during concurrent
     * layer rendering and prevents a cleared cache from being repopulated by a
     * stale in-flight computation. */
    private static final Object LIGHT_LOCK=new Object();
    private static java.lang.ref.WeakReference<int[]> lightSource=new java.lang.ref.WeakReference<>(null);
    private static byte[] lightPlane;
    private static int lightSubstrate;
    private static byte[] cachedLight(int[] source,int substrate){
        synchronized(LIGHT_LOCK){
            int[] owner=lightSource.get();
            if(owner==source&&lightSubstrate==substrate&&lightPlane!=null&&lightPlane.length==source.length)
                return lightPlane;
            // This is a deterministic, immutable-source calculation. Only one
            // 1-byte/pixel plane should be allocated for the same photograph.
            byte[] plane=computeLight(source,substrate);
            lightSource=new java.lang.ref.WeakReference<>(source);
            lightPlane=plane;
            lightSubstrate=substrate;
            return plane;
        }
    }
    /** Releases the cached luminance plane (low-memory recovery, new photo). */
    static void clearLightCache(){
        synchronized(LIGHT_LOCK){lightSource=new java.lang.ref.WeakReference<>(null);lightPlane=null;}
    }
    private static byte[] computeLight(int[] source,int substrate){
        byte[] light=new byte[source.length];
        final int bgR=(substrate>>>16)&255,bgG=(substrate>>>8)&255,bgB=substrate&255;
        // Most imported camera photos are fully opaque. Avoid three alpha
        // blends and 2D index arithmetic for every pixel in that common case.
        // Keep exactly the same float coefficients and rounding as RC133.
        for(int i=0;i<source.length;i++){
            if((i&8191)==0&&Thread.currentThread().isInterrupted())throw new CancellationException();
            int p=source[i],a=(p>>>24)&255;
            float r,g,b;
            if(a==255){r=(p>>>16)&255;g=(p>>>8)&255;b=p&255;}
            else{
                r=(((p>>>16)&255)*a+bgR*(255-a))/255f;
                g=(((p>>>8)&255)*a+bgG*(255-a))/255f;
                b=((p&255)*a+bgB*(255-a))/255f;
            }
            int v=Math.min(255,Math.max(0,Math.round(.2126f*r+.7152f*g+.0722f*b)));
            light[i]=(byte)v;
        }
        return light;
    }
    private static float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
    private int ix(float mm){return Math.max(0,Math.min(width-1,(int)(mm/pixelMmX)));}
    private int iy(float mm){return Math.max(0,Math.min(height-1,(int)(mm/pixelMmY)));}
    private boolean inside(float x,float y){return Float.isFinite(x)&&Float.isFinite(y)&&x>=0&&y>=0&&x<widthMm&&y<heightMm;}
    public float pixelSizeMm(){return Math.max(pixelMmX,pixelMmY);}
    public float lightAt(float xMm,float yMm){
        // Bilinear interpolated at pixel centers; clamped at image borders.
        float px=clamp(xMm/pixelMmX-.5f,0,width-1),py=clamp(yMm/pixelMmY-.5f,0,height-1);
        int x=(int)px,y=(int)py,x2=Math.min(width-1,x+1),y2=Math.min(height-1,y+1);
        float fx=px-x,fy=py-y;
        float a=(light[y*width+x]&255)*(1-fx)+(light[y*width+x2]&255)*fx;
        float b=(light[y2*width+x]&255)*(1-fx)+(light[y2*width+x2]&255)*fx;
        return (a*(1-fy)+b*fy)/255f;
    }
    public float darknessAt(float xMm,float yMm){return 1f-lightAt(xMm,yMm);}
    public float coverageAt(float xMm,float yMm,Coverage f){
        if(!inside(xMm,yMm))return 0;
        return clamp(f.ofArgb(argb[iy(yMm)*width+ix(xMm)]),0,1);
    }

    /**
     * Motifs retain their center's color ownership. 8 footprint taps contribute
     * at most 32% of the result and cannot create marks on center-blank pixels.
     * Sampling radius is bounded by the already validated motif cell envelope.
     */
    public float motifCoverage(float x,float y,float footprintRadiusMm,Coverage coverage){
        float center=coverageAt(x,y,coverage);
        if(center<=.0001f||footprintRadiusMm<=pixelSizeMm()*.35f)return center;
        float d=footprintRadiusMm*.62f,sum=0f;
        for(int k=0;k<8;k++){
            float xx=x+d*OCTANTS_X[k],yy=y+d*OCTANTS_Y[k];
            // Do not sample across a blank alpha region or outside the document.
            if(!inside(xx,yy)||(argb[iy(yy)*width+ix(xx)]>>>24)<16){sum+=center;continue;}
            sum+=coverageAt(xx,yy,coverage);
        }
        return clamp(.68f*center+.32f*sum/8f,0f,1f);
    }

    /**
     * B2: preserve the photographed layer's tone at a real edge when a motif
     * footprint straddles two very different regions. On uniform areas the
     * result is BIT-FOR-BIT the historical motifCoverage response. Blank
     * centers and transparent centers remain empty, so the edge model cannot
     * create extra islands by borrowing color from a neighboring swatch.
     *
     * Physical radius in mm rather than bitmap pixels keeps a 6/11/15-inch
     * rendering tied to the actual cutter geometry, regardless of photo DPI.
     * It changes coverage only: it never changes motif centers, spacing,
     * minimum cut width, exported paths, or physical layer ownership.
     */
    public float detailPreservingMotifCoverage(float x,float y,float footprintRadiusMm,Coverage coverage){
        if(!inside(x,y)||(argb[iy(y)*width+ix(x)]>>>24)<16)return 0f;
        float center=coverageAt(x,y,coverage);
        if(center<=.0001f||footprintRadiusMm<=pixelSizeMm()*.35f)return center;
        float radius=footprintRadiusMm*.62f;
        float sum=0f,contrast=0f;
        int valid=0;
        for(int k=0;k<8;k++){
            float xx=x+radius*OCTANTS_X[k],yy=y+radius*OCTANTS_Y[k];
            // The historical alpha behavior is retained: a transparent
            // neighbor contributes the center value, not a paper-colored halo.
            if(!inside(xx,yy)||(argb[iy(yy)*width+ix(xx)]>>>24)<16){sum+=center;continue;}
            float sample=coverageAt(xx,yy,coverage);
            sum+=sample;
            contrast+=Math.abs(center-sample);
            valid++;
        }
        float neighborhood=sum/8f;
        float historic=clamp(.68f*center+.32f*neighborhood,0f,1f);
        if(valid<2)return historic;
        // A clear, repeated local change is stronger evidence than a single
        // isolated sample. At normal uniform tone, preserve existing behavior.
        float evidence=clamp((contrast/valid-.12f)/.45f,0f,1f);
        if(evidence<=0f)return historic;
        // Limit increased center fidelity to 0.90, not pure center sampling:
        // a meaningful fraction of each motif's source footprint remains.
        float centerShare=.68f+.22f*evidence;
        return clamp(centerShare*center+(1f-centerShare)*neighborhood,0f,1f);
    }

    /**
     * Signed side-width asymmetry. Sum of two side widths stays unchanged,
     * so minimum total cut width and inter-ribbon corridors stay controlled by
     * their existing generators. Does not invent a line in a blank layer.
     */
    public float ribbonBias(float x,float y,float normalX,float normalY,float radiusMm,
                            float centerCoverage,Coverage coverage){
        if(centerCoverage<=.04f||radiusMm<=0f)return 0;
        float probe=Math.max(pixelSizeMm()*.75f,Math.min(radiusMm*.78f,1.2f));
        float ax=x+normalX*probe,ay=y+normalY*probe;
        float bx=x-normalX*probe,by=y-normalY*probe;
        if(!inside(ax,ay)||!inside(bx,by))return 0;
        // Avoid dragging a ribbon across transparent exclusions.
        if((argb[iy(ay)*width+ix(ax)]>>>24)<16||(argb[iy(by)*width+ix(bx)]>>>24)<16)return 0;
        float positive=coverageAt(ax,ay,coverage),negative=coverageAt(bx,by,coverage);
        return clamp((positive-negative)*.20f,-.20f,.20f);
    }

    /** Restore interior ribbon asymmetry for Snipped subjects without letting
     * a side probe or expanded ribbon borrow a pixel from excluded artwork.
     * Conservative boundary fallback is symmetric: it changes no cut mask.
     */
    public float ribbonBiasWithinSnip(float xMm,float yMm,float nx,float ny,
                                       float radiusMm,float centerCoverage,
                                       Coverage coverage,SnipMask snips){
        if(snips!=null&&!snips.isEmpty()){
            float probe=Math.max(pixelSizeMm()*.75f,Math.min(radiusMm*.78f,1.2f));
            // 1.18 bounds the caller's asymmetry amplification, plus a buffer.
            float clearance=radiusMm*1.18f+probe+.10f;
            if(snips.clearanceWithinMm(xMm,yMm,widthMm,heightMm,clearance+.02f)
                    <=clearance+.01f)return 0f;
        }
        return ribbonBias(xMm,yMm,nx,ny,radiusMm,centerCoverage,coverage);
    }

    /**
     * Image brightness across a carrier's normal in PHYSICAL millimeters.
     * Source alpha is honored: transparent sides never introduce an artificial
     * dark feature. The center retains most of the signal so a thin feature
     * cannot disappear simply because its neighbors are bright.
     */
    public float lineDarkness(float x,float y,float normalX,float normalY,float radiusMm){
        if(!inside(x,y))return 0;
        float center=darknessAt(x,y);
        if((argb[iy(y)*width+ix(x)]>>>24)<128)return 0;
        if(radiusMm<=pixelSizeMm()*.25f)return center;
        float radius=Math.min(radiusMm,1.2f);
        float ax=x+normalX*radius,ay=y+normalY*radius;
        float bx=x-normalX*radius,by=y-normalY*radius;
        float da=inside(ax,ay)&&(argb[iy(ay)*width+ix(ax)]>>>24)>=128?darknessAt(ax,ay):center;
        float db=inside(bx,by)&&(argb[iy(by)*width+ix(bx)]>>>24)>=128?darknessAt(bx,by):center;
        return clamp(.76f*center+.12f*(da+db),0,1);
    }

    /**
     * Independent light/dark sampling on either side of a vinyl carrier.
     * Returns a signed center offset expressed as a fraction of half-width.
     * Width ownership stays with the originating layer; alpha holes give zero.
     * Caller must validate physical clearance INCLUDING the resulting shift.
     */
    public float tonalRibbonBias(float x,float y,float nx,float ny,float halfWidthMm){
        if(!inside(x,y)||halfWidthMm<=0||
           (argb[iy(y)*width+ix(x)]>>>24)<128)return 0;
        float radius=Math.min(Math.max(pixelSizeMm()*.8f,halfWidthMm*.85f),1.2f);
        float ax=x+nx*radius,ay=y+ny*radius;
        float bx=x-nx*radius,by=y-ny*radius;
        if(!inside(ax,ay)||!inside(bx,by))return 0;
        if((argb[iy(ay)*width+ix(ax)]>>>24)<128||
           (argb[iy(by)*width+ix(bx)]>>>24)<128)return 0;
        return clamp((darknessAt(ax,ay)-darknessAt(bx,by))*.16f,-.16f,.16f);
    }

    /** Local portrait-detail priority without face recognition or extra settings.
     * Transparent pixels and smooth paper receive zero priority.
     */
    public float regionalPriority(float xMm,float yMm,float channelCoverage){
        if(!inside(xMm,yMm))return 0f;
        int x=ix(xMm),y=iy(yMm),argbValue=argb[y*width+x];
        float radius=Math.max(pixelSizeMm(),.25f);
        float gx=Math.abs(lightAt(xMm+radius,yMm)-lightAt(xMm-radius,yMm));
        float gy=Math.abs(lightAt(xMm,yMm+radius)-lightAt(xMm,yMm-radius));
        return RegionalPortraitDetail.priority(xMm,yMm,widthMm,heightMm,
            (float)Math.hypot(gx,gy),channelCoverage,(argbValue>>>24)&255);
    }

    /** Compute portrait detail without borrowing a gradient across a user's
     * geometric Snip boundary. No Snip means bit-identical legacy sampling.
     * A masked-out probe does not disable *tonal* recovery at the center.
     * The generator separately enforces the full ribbon/circle cut clearance.
     */
    public float regionalPriorityWithinSnip(float xMm,float yMm,
                                             float channelCoverage,SnipMask snips){
        if(snips!=null&&!snips.isEmpty()){
            float radius=Math.max(pixelSizeMm(),.25f);
            if(snips.clearanceWithinMm(xMm,yMm,widthMm,heightMm,radius+.02f)
                    <=radius+.01f)return 0f;
        }
        return regionalPriority(xMm,yMm,channelCoverage);
    }

    /** 5x5 physical-area gradient and mean for cell/region geometry. */
    public RegionSignal region(float leftMm,float topMm,float rightMm,float bottomMm){
        if(!Float.isFinite(leftMm)||!Float.isFinite(topMm)||
           !Float.isFinite(rightMm)||!Float.isFinite(bottomMm)||
           !(rightMm>leftMm)||!(bottomMm>topMm))
            throw new IllegalArgumentException("Invalid region bounds");
        float stepX=Math.max(pixelMmX,.16f),stepY=Math.max(pixelMmY,.16f);
        double gx=0,gy=0,contrast=0,luma=0;
        for(int j=1;j<=5;j++)for(int i=1;i<=5;i++){
            float x=leftMm+(rightMm-leftMm)*i/6f;
            float y=topMm+(bottomMm-topMm)*j/6f;
            float dx=(lightAt(x+stepX,y)-lightAt(x-stepX,y))*255f;
            float dy=(lightAt(x,y+stepY)-lightAt(x,y-stepY))*255f;
            gx+=dx;gy+=dy;contrast+=Math.hypot(dx,dy);luma+=lightAt(x,y);
        }
        return new RegionSignal((float)(luma/25),(float)(gx/25),
            (float)(gy/25),(float)(contrast/25));
    }
}

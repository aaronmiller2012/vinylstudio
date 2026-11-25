package com.halftonestudio;

import java.util.Locale;

/**
 * Closed cut silhouette of an opaque, photo-weighted single-stroke vinyl ribbon.
 * Shape UNION is performed in one occupancy mask. A shared edge is traced once,
 * no duplicated/nested cut paths are intentionally introduced.
 *
 * This is a raster-precision approximation with a documented physical pixel
 * resolution, not unrestricted Bézier offset geometry.
 */
public final class SpaceFillingRibbon {
    public static final int MAX_RASTER_SIDE=2500;
    public static final long MAX_RASTER_PIXELS=6_250_000L;
    private SpaceFillingRibbon() {}
    public static final class Options {
        public float artworkWidthMm=120f,maximumWidthMm=1.10f,minimumFeatureMm=.55f,minimumGapMm=.45f;
        public float photoInfluence=.85f,toneContrast=.55f,highlightProtection=.85f;
        public boolean draft=false; // reduced raster, preview-only (never a Cricut export)
        /** Internal regression fallback; not a user-facing adjustment. */
        public boolean independentEdgeSampling=true;
        public int[] image=null;public int imageWidth=0,imageHeight=0;
    }
    public static final class Result {
        public final int width,height,coveredPixels;
        public final float widthMm,heightMm,pixelMm,minLocalPitchMm,minimumWidthMm,maximumWidthMm;
        public final boolean[] mask;public final boolean draft;
        Result(int w,int h,float mmW,float mmH,float pitch,float minW,float maxW,boolean[] m,int count,boolean provisional){
            width=w;height=h;widthMm=mmW;heightMm=mmH;pixelMm=mmW/w;minLocalPitchMm=pitch;
            minimumWidthMm=minW;maximumWidthMm=maxW;mask=m;coveredPixels=count;draft=provisional;
        }
        public String svg(){
            if(draft)throw new IllegalStateException("Draft preview cannot be exported. Release the slider and regenerate at full quality.");
            StringBuilder out=new StringBuilder(Math.min(200000,coveredPixels*2+250));
            out.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(mm(widthMm)).append("mm\" height=\"")
                .append(mm(heightMm)).append("mm\" viewBox=\"0 0 ").append(mm(widthMm)).append(' ').append(mm(heightMm)).append("\">\n")
                .append("<g fill=\"#141414\" fill-rule=\"evenodd\">\n");
            // The contour tracer enumerates each occupancy boundary only once.
            MultilayerStencilEngine.appendUnionContours(out,mask,width,height,widthMm,heightMm,0f);
            return out.append("</g></svg>\n").toString();
        }
        public String report(){return "Unioned vinyl ribbon: "+width+" x "+height+" raster samples, "+mm(pixelMm)+
            " mm/sampling pixel; stroke "+mm(minimumWidthMm)+"–"+mm(maximumWidthMm)+
            " mm; approximate local pitch "+mm(minLocalPitchMm)+" mm. Test-cut and visually inspect narrow bends, necks, and cutouts.";}
    }
    private static String mm(float x){return String.format(Locale.US,"%.3f",x);}
    public static Result generate(SpaceFillingCurves.Result line,float sourceW,float sourceH,Options o){
        if(line==null||line.xy.length<4||o==null||!Float.isFinite(sourceW)||!Float.isFinite(sourceH)||sourceW<=0||sourceH<=0||
            !Float.isFinite(o.artworkWidthMm)||o.artworkWidthMm<10||o.artworkWidthMm>600||
            !Float.isFinite(o.maximumWidthMm)||o.maximumWidthMm<.5f||o.maximumWidthMm>8||
            !Float.isFinite(o.minimumFeatureMm)||o.minimumFeatureMm<.35f||o.minimumFeatureMm>o.maximumWidthMm||
            !Float.isFinite(o.minimumGapMm)||o.minimumGapMm<.25f||o.minimumGapMm>8||
            !Float.isFinite(o.photoInfluence)||o.photoInfluence<0||o.photoInfluence>1||
            !Float.isFinite(o.toneContrast)||o.toneContrast<0||o.toneContrast>1||
            !Float.isFinite(o.highlightProtection)||o.highlightProtection<0||o.highlightProtection>1)
            throw new IllegalArgumentException("Invalid vinyl ribbon settings");
        if(o.image!=null&&(o.imageWidth<1||o.imageHeight<1||o.image.length!=(long)o.imageWidth*o.imageHeight))
            throw new IllegalArgumentException("Invalid ribbon photo");
        float physicalHeight=o.artworkWidthMm*sourceH/sourceW;
        float pitchMm=line.shortestStep*o.artworkWidthMm/sourceW;
        if(pitchMm<o.maximumWidthMm+o.minimumGapMm)
            throw new IllegalArgumentException(String.format(Locale.US,
                "Curve pitch %.2f mm is too tight for %.2f mm ribbon and %.2f mm weed gap. Reduce recursion, ribbon width, or enlarge artwork.",
                pitchMm,o.maximumWidthMm,o.minimumGapMm));
        // Ensure fine features have >=4 output pixels across their width,
        // while bounding raster memory and SVG boundary complexity.
        float pxPerMm=Math.max(6f,4f/o.minimumFeatureMm);
        int w=(int)Math.ceil(o.artworkWidthMm*pxPerMm),h=(int)Math.ceil(physicalHeight*pxPerMm);
        float scale=Math.min(1f,(o.draft?580f:MAX_RASTER_SIDE)/Math.max(w,h));w=Math.max(2,Math.round(w*scale));h=Math.max(2,Math.round(h*scale));
        if((long)w*h>MAX_RASTER_PIXELS)throw new IllegalArgumentException("Vinyl ribbon mask exceeds processing budget");
        float realPixelMm=o.artworkWidthMm/w;
        if(!o.draft&&o.minimumFeatureMm/realPixelMm<3.2f)throw new IllegalArgumentException("Too little raster resolution for minimum vinyl width; reduce artboard size");
        boolean[] mask=new boolean[w*h];
        float[] radii=new float[line.vertices];
        float[] shifts=new float[line.vertices];
        float[] normalX=new float[line.vertices],normalY=new float[line.vertices];
        ScaleAwarePhotoSampler photo=o.image==null?null:new ScaleAwarePhotoSampler(
            o.image,o.imageWidth,o.imageHeight,o.artworkWidthMm,physicalHeight,0xffffffff);
        float maxR=o.maximumWidthMm/(2f*realPixelMm);
        float minR=o.minimumFeatureMm/(2f*realPixelMm);
        for(int i=0;i<line.vertices;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            float tone=1f;
            if(photo!=null){
                float xMm=line.xy[2*i]*o.artworkWidthMm/sourceW;
                float yMm=line.xy[2*i+1]*physicalHeight/sourceH;
                tone=photo.darknessAt(xMm,yMm);
            }
            tone=Math.max(0f,Math.min(1f,.5f+(tone-.5f)*(1f+o.toneContrast*1.8f)));
            float f=1f-o.photoInfluence*(1f-tone)*(0.50f+o.highlightProtection*.50f);
            radii[i]=Math.max(minR,maxR*f);
        }
        // The source carrier remains unchanged. A small signed offset produces
        // unequal ribbon widths to the left and right, as in a two-sided
        // photographic ribbon. Endpoints are smoothed by unioned round caps.
        // Never let this optimization weaken the legacy safe-ribbon result.
        if(photo!=null && o.photoInfluence>0f && o.independentEdgeSampling){
            for(int i=0;i<line.vertices;i++){if(((i)&63)==0)CancellationCheckpoint.check();
                if((i&1023)==0&&Thread.currentThread().isInterrupted())
                    throw new IllegalStateException("Cancelled vinyl ribbon");
                int before=Math.max(0,i-1),after=Math.min(line.vertices-1,i+1);
                float dx=(line.xy[2*after]-line.xy[2*before])*o.artworkWidthMm/sourceW;
                float dy=(line.xy[2*after+1]-line.xy[2*before+1])*physicalHeight/sourceH;
                float length=(float)Math.hypot(dx,dy);
                if(length<1e-6f)continue;
                normalX[i]=-dy/length;normalY[i]=dx/length;
                float xMm=line.xy[2*i]*o.artworkWidthMm/sourceW;
                float yMm=line.xy[2*i+1]*physicalHeight/sourceH;
                shifts[i]=radii[i]*o.photoInfluence*photo.tonalRibbonBias(
                    xMm,yMm,normalX[i],normalY[i],radii[i]*realPixelMm);
            }
        }
        // Validate physical clearances on the POST-fold, POST-shortcut line,
        // using local ribbon widths. Carrier shortestStep alone is unsafe.
        // A failure never creates a cut-ready SVG.
        if(!o.draft){
            // A shifted ribbon can occupy farther from its carrier than the
            // symmetric one. Verify that enlarged envelope before accepting it.
            // If the photo-driven shift fails, keep the original valid geometry.
            boolean biased=false;
            for(float shift:shifts)if(Math.abs(shift)>1e-6f){biased=true;break;}
            if(biased){
                float[] envelope=new float[line.vertices];
                for(int i=0;i<envelope.length;i++)envelope[i]=radii[i]+Math.abs(shifts[i]);
                try{
                    RibbonClearance.verify(line.xy,o.artworkWidthMm/sourceW,
                        envelope,realPixelMm,o.minimumGapMm,o.maximumWidthMm);
                }catch(IllegalArgumentException unsafeBias){
                    // Photographic modulation is optional; never disable a
                    // perfectly valid ribbon because the added bias is unsafe.
                    java.util.Arrays.fill(shifts,0f);
                }
            }
            RibbonClearance.verify(line.xy,o.artworkWidthMm/sourceW,
                radii,realPixelMm,o.minimumGapMm,o.maximumWidthMm);
        }
        for(int segment=1;segment<line.vertices;segment++){if(((segment)&63)==0)CancellationCheckpoint.check();
            if((segment&255)==0&&Thread.currentThread().isInterrupted())throw new IllegalStateException("Cancelled vinyl ribbon");
            float ax=line.xy[(segment-1)*2]/sourceW*w+normalX[segment-1]*shifts[segment-1];
            float ay=line.xy[(segment-1)*2+1]/sourceH*h+normalY[segment-1]*shifts[segment-1];
            float bx=line.xy[segment*2]/sourceW*w+normalX[segment]*shifts[segment];
            float by=line.xy[segment*2+1]/sourceH*h+normalY[segment]*shifts[segment];
            float radiusA=radii[segment-1],radiusB=radii[segment];
            float bound=Math.max(radiusA,radiusB)+1;
            int xmin=Math.max(0,(int)Math.floor(Math.min(ax,bx)-bound));int xmax=Math.min(w-1,(int)Math.ceil(Math.max(ax,bx)+bound));
            int ymin=Math.max(0,(int)Math.floor(Math.min(ay,by)-bound));int ymax=Math.min(h-1,(int)Math.ceil(Math.max(ay,by)+bound));
            double vx=bx-ax,vy=by-ay,len2=vx*vx+vy*vy;
            for(int y=ymin;y<=ymax;y++)for(int x=xmin;x<=xmax;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                double t=len2<1e-7?0:((x+.5-ax)*vx+(y+.5-ay)*vy)/len2;
                t=Math.max(0,Math.min(1,t));
                double dx=x+.5-(ax+t*vx),dy=y+.5-(ay+t*vy);
                float r=radiusA+(radiusB-radiusA)*(float)t;
                if(dx*dx+dy*dy<=r*r)mask[y*w+x]=true;
            }
        }
        // Honor transparent source pixels, including subject extraction and
        // circular clipping prepared by the Android editor. Cropped regions
        // must not silently become cut vinyl on the finished sheet.
        if(o.image!=null){
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(mask[y*w+x]){
                int px=Math.min(o.imageWidth-1,(int)((x+.5f)/w*o.imageWidth));
                int py=Math.min(o.imageHeight-1,(int)((y+.5f)/h*o.imageHeight));
                if((o.image[py*o.imageWidth+px]>>>24)<128)mask[y*w+x]=false;
            }
        }
        int covered=0,boundaries=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){if(((x)&63)==0)CancellationCheckpoint.check();
            if(!mask[y*w+x])continue;
            covered++;
            if(y==0||!mask[(y-1)*w+x])boundaries++;
            if(y==h-1||!mask[(y+1)*w+x])boundaries++;
            if(x==0||!mask[y*w+x-1])boundaries++;
            if(x==w-1||!mask[y*w+x+1])boundaries++;
        }
        if(covered<8)throw new IllegalArgumentException("Vinyl ribbon produced no cuttable silhouette");
        if(boundaries>150000)throw new IllegalArgumentException("Vinyl ribbon has too many cut-edge segments; lower depth or enlarge artwork");
        return new Result(w,h,o.artworkWidthMm,physicalHeight,pitchMm,o.minimumFeatureMm,o.maximumWidthMm,mask,covered,o.draft);
    }
}

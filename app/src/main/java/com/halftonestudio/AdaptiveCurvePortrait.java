package com.halftonestudio;

import java.util.Arrays;

/**
 * Converts a mathematically regular continuous space-filling traversal into
 * image-responsive SINGLE-STROKE line art without splitting the pen path.
 *
 * Bright regions: replace local recursive detours with a shortcut between
 * retained vertices (fewer millimeters of ink per square millimeter).
 * Dark regions: retain the carrier traversal and optionally add bounded
 * perpendicular folds between neighboring vertices (more local path length).
 *
 * Photo samples influence geometry, not merely a tiny vertex displacement.
 * All authored vertices are joined in order. The carrier remains the ground
 * truth for the pitch estimate used by vinyl-ribbon safety checks.
 *
 * This is a deterministic photographic approximation, not a mathematical
 * variable-order Hilbert curve or a proof of non-self-intersection.
 */
public final class AdaptiveCurvePortrait {
    private AdaptiveCurvePortrait(){}
    public static final int DENSITY=0, FOLD=1, HYBRID=2;
    public static final String[] METHODS={"Adaptive density", "Shadow folding", "Hybrid density + folds"};
    public static final int MAX_OUTPUT_POINTS=240_000;
    /** Rendering budget for ordinary Android previews and export centerlines.
     * Large enough for detailed portraits, bounded for Android Path rasterization. */
    public static final int DEVICE_POINT_BUDGET=90_000;
    public static final int DRAFT_POINT_BUDGET=32_000;

    public static final class Options {
        public int method=HYBRID;
        public float influence=.85f,contrast=.55f,shadowBoost=.75f,highlightProtection=.85f,edgePriority=.55f;
        public int[] pixels=null;public int width=0,height=0;
        /** Optional physical artboard width; old callers preserve the default. */
        public float artworkWidthMm=120f;
        public PortraitStructureMap structure=null; // immutable map may be reused for an unchanged photo
        public boolean draft=false;
        public void validate(){
            if(method<DENSITY||method>HYBRID||!valid(influence)||!valid(contrast)||
               !valid(shadowBoost)||!valid(highlightProtection)||!valid(edgePriority)||
               !Float.isFinite(artworkWidthMm)||artworkWidthMm<=0)
                throw new IllegalArgumentException("Invalid adaptive portrait controls");
            if(pixels!=null&&(width<3||height<3||pixels.length!=(long)width*height))
                throw new IllegalArgumentException("Invalid portrait photo");
        }
        private static boolean valid(float value){return Float.isFinite(value)&&value>=0&&value<=1;}
    }
    public static SpaceFillingCurves.Result render(SpaceFillingCurves.Result carrier,
                                                     float artworkW,float artworkH,Options settings){
        if(carrier==null||carrier.xy.length<4||!Float.isFinite(artworkW)||!Float.isFinite(artworkH)||artworkW<=0||artworkH<=0)
            throw new IllegalArgumentException("Invalid carrier geometry");
        settings.validate();
        if(settings.pixels==null||settings.influence<.001f)return carrier;
        PortraitStructureMap photo=settings.structure!=null?settings.structure:
            new PortraitStructureMap(settings.pixels,settings.width,settings.height);
        ScaleAwarePhotoSampler photoScale=new ScaleAwarePhotoSampler(settings.pixels,
            settings.width,settings.height,settings.artworkWidthMm,
            settings.artworkWidthMm*settings.height/(float)settings.width,0xffffffff);
        final float pixelToMm=settings.artworkWidthMm/settings.width;
        final float[] pts=carrier.xy;
        final int count=carrier.vertices;
        final float pitch=Math.max(.0001f,carrier.shortestStep);
        final float scaleX=(settings.width-1)/artworkW,scaleY=(settings.height-1)/artworkH;
        final float influence=settings.influence;
        // High-order ternary carriers have 59,049 vertices at depth five.
        // v2.43 expanded dark edges sevenfold and threw before preview could
        // reach the screen. Reserve an allocation for each original vertex,
        // then distribute extra folds without ever exhausting the point budget.
        final int budget=Math.max(count,Math.min(MAX_OUTPUT_POINTS,
            settings.draft?DRAFT_POINT_BUDGET:DEVICE_POINT_BUDGET));
        final float spareRatio=Math.max(0f,(budget-count)/(float)Math.max(1,count));
        final int desiredSteps=spareRatio>=7f?8:spareRatio>=3f?4:2;
        final boolean shortcut=settings.method!=FOLD;
        final boolean fold=settings.method!=DENSITY;
        final float[] out=new float[Math.min(MAX_OUTPUT_POINTS*2,Math.max(4096,pts.length*2))];
        Dynamic result=new Dynamic(out);
        result.add(pts[0],pts[1]);
        int i=0;
        while(i<count-1){
            if((i&255)==0&&Thread.currentThread().isInterrupted())throw new IllegalStateException("Cancelled curve portrait render");
            float x=pts[2*i],y=pts[2*i+1];
            float edge=photo.edge(x*scaleX,y*scaleY);
            float dark=toneWithFootprint(photo,photoScale,x*scaleX,y*scaleY,
                pixelToMm,settings,edge);
            int end=i+1;
            if(shortcut){
                // Bright, smooth cells are *traversed* using long chords rather
                // than disappearing. The path stays mathematically continuous.
                float whiteness=1f-dark;
                float preserve=1f-Math.min(1f,edge*(2.0f+settings.edgePriority*2f));
                float range=(settings.draft?72f:48f)*settings.highlightProtection;
                int maxSkip=1+Math.round(range*influence*whiteness*whiteness*preserve);
                maxSkip=Math.max(1,Math.min(settings.draft?80:54,maxSkip));
                float maxChord=pitch*(settings.draft?7f:12f);
                int maxPossible=Math.min(count-1,i+maxSkip);
                for(int candidate=i+2;candidate<=maxPossible;candidate++){
                    float xx=pts[2*candidate],yy=pts[2*candidate+1];
                    if(Math.hypot(xx-x,yy-y)>maxChord)break;
                    float ce=photo.edge(xx*scaleX,yy*scaleY);
                    float cd=toneWithFootprint(photo,photoScale,xx*scaleX,yy*scaleY,
                        pixelToMm,settings,ce);
                    // Also sample the shortcut's INTERIOR. Otherwise a narrow
                    // eye/hair edge between two bright vertices is discarded.
                    float mx=(x+xx)*.5f*scaleX,my=(y+yy)*.5f*scaleY;
                    float midEdge=photo.edge(mx,my);
                    float midTone=toneWithFootprint(photo,photoScale,mx,my,
                        pixelToMm,settings,midEdge);
                    if(midTone>Math.max(.38f,dark+.17f)||
                        midEdge>.25f+(.18f*(1f-settings.edgePriority)))break;
                    // Stop before a visible feature, instead of drawing a long
                    // chord through an eye, hairline, or facial boundary.
                    if(cd>Math.max(.34f,dark+.13f)||ce>.23f+(.20f*(1f-settings.edgePriority)))break;
                    end=candidate;
                }
            }
            float ex=pts[2*end],ey=pts[2*end+1];
            float dx=ex-x,dy=ey-y;
            float length=(float)Math.hypot(dx,dy);
            if(length<1e-5f){i=end;continue;}
            float midX=(x+ex)*.5f,midY=(y+ey)*.5f;
            float middle=toneWithFootprint(photo,photoScale,midX*scaleX,midY*scaleY,
                pixelToMm,settings,photo.edge(midX*scaleX,midY*scaleY));
            float amplitude=0;
            if(fold&&end==i+1){
                // A line's local darkness is proportional to physical path
                // length per area. Unlike 2.42, folds change that quantity.
                float shadow=Math.max(0f,Math.min(1f,(middle-.43f)/.57f));
                amplitude=pitch*.38f*settings.shadowBoost*influence*shadow;
                amplitude=Math.min(amplitude,length*.36f);
            }
            if(amplitude>=pitch*.025f&&spareRatio>.0001f){
                // For very dense carriers use a single offset apex rather than
                // 7 added points per segment. Distribute limited folds evenly
                // across the curve instead of exhausting all capacity on the
                // first dark region. The remaining carrier vertices are always
                // reserved to guarantee a valid finished centerline.
                int subdivisions=desiredSteps;
                if(spareRatio<1f){
                    // Golden-ratio sequence distributes folds independent of
                    // traversal order and does not concentrate them in a prefix.
                    double distributed=(i*0.6180339887498949)%1.0;
                    if(distributed>=spareRatio)subdivisions=1;
                }
                int remainingCarrier=count-end-1;
                if(result.size+subdivisions+remainingCarrier>budget)subdivisions=1;
                if(subdivisions>1){
                    float nx=-dy/length,ny=dx/length;
                    for(int k=1;k<subdivisions;k++){
                        float t=k/(float)subdivisions;
                        float envelope=(float)Math.sin(Math.PI*t);
                        // A two-subdivision fold has one apex. The old full
                        // period sine was zero at its midpoint, cancelling the
                        // photographic change entirely in high-density mode.
                        double cycles=subdivisions==8?2.0:1.0;
                        float wave=subdivisions==2?1f:(float)Math.sin(2*Math.PI*cycles*t);
                        float offset=wave*amplitude*envelope;
                        result.add(x+t*dx+nx*offset,y+t*dy+ny*offset);
                    }
                }
            }
            result.add(ex,ey);
            i=end;
        }
        // A closed Moore traversal must remain closed after the tonal edits.
        float[] rendered=result.finish();
        if(carrier.closed){
            rendered[rendered.length-2]=rendered[0];
            rendered[rendered.length-1]=rendered[1];
        }
        return SpaceFillingCurves.remap(carrier,rendered);
    }
    private static float toneWithFootprint(PortraitStructureMap map,ScaleAwarePhotoSampler source,
                                           float x,float y,float mmPerPx,Options controls,float edge){
        float corrected=tone(map,x,y,controls,edge);
        float raw=source.lineDarkness((x+.5f)*mmPerPx,(y+.5f)*mmPerPx,
            .7071068f,.7071068f,.42f);
        // Gentle mixing maintains the established shadow/edge response while
        // making the cutter-scale signal less dependent on bitmap resolution.
        return Math.max(0f,Math.min(1f,.86f*corrected+.14f*raw));
    }

    /** Tone is calibrated with a shared portrait structure map. */
    static float tone(PortraitStructureMap photo,float x,float y,Options controls){
        return tone(photo,x,y,controls,photo.edge(x,y));
    }
    static float tone(PortraitStructureMap photo,float x,float y,Options controls,float edge){
        float d=photo.dark(x,y);
        d=.5f+(d-.5f)*(1f+controls.contrast*1.8f);
        d=Math.max(0f,Math.min(1f,d));
        // Preserve edge detail without turning every soft gradient into dark ink.
        d+=controls.edgePriority*.14f*edge;
        return Math.max(0f,Math.min(1f,d));
    }
    private static final class Dynamic {
        float[] points;int size;
        Dynamic(float[] buffer){points=buffer;}
        void add(float x,float y){
            if(size>=MAX_OUTPUT_POINTS)throw new IllegalArgumentException("Portrait curve too complex");
            if(size>0){float dx=points[2*size-2]-x,dy=points[2*size-1]-y;
                if(dx*dx+dy*dy<1e-10f)return;
            }
            if(2*size+2>points.length)points=Arrays.copyOf(points,Math.min(MAX_OUTPUT_POINTS*2,points.length*2));
            points[2*size]=x;points[2*size+1]=y;size++;
        }
        float[] finish(){return Arrays.copyOf(points,size*2);}
    }
}

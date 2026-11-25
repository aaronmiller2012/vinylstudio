package com.halftonestudio;

import java.util.*;
import java.util.concurrent.CancellationException;

/** Android-free, millimeter-aware multicolor dot stippler. Deterministic per source/settings.
 * DRAFT deliberately never substitutes for an export. Every output dot is one physically
 * closed vinyl silhouette. No existing CMYK/line rendering code is called or altered.
 */
public final class MultiColorStippleEngine {
    private MultiColorStippleEngine() {}
    public static final int SHARED=0, SPACED=1, KNOCKOUT=2, OVERPRINT=3;
    public static final int NEAREST=0, ERROR_DIFFUSION=1;
    /** All silhouettes are closed SVG polygons. IDs are saved without changing legacy styles. */
    public static final int ROUND=0, SQUARE=1, DIAMOND=2, HEXAGON=3,
        ELLIPSE=4, CAPSULE=5, ROUNDED_SQUARE=6, STAR=7;
    public static final String[] SHAPE_NAMES={"Round","Square","Diamond","Hexagon",
        "Ellipse","Capsule","Rounded square","Star"};
    private static final int MAX_DOTS=12000;
    /** Only adaptive exports may use unused per-color SVG capacity. */
    private static final int MAX_ADAPTIVE_EXPORT_DOTS=32000;
    // Cross-layer collision detection in legacy spacing/knockout is quadratic;
    // retain its previous maximum until that diagnostic is spatially indexed.
    private static final int MAX_NON_OVERPRINT_EXPORT_DOTS=16000;
    // Stay below Cricut's 5000 drawable SVG elements including registration marks.
    private static final int MAX_LAYER_DOTS=4400;
    private static final int MAX_PREVIEW_DOTS=6400;
    public static final class Options {
        public int layerCount=5, overlap=OVERPRINT, assignment=NEAREST, detail=70, shape=ROUND;
        public float widthMm=152.4f, minGapMm=.35f, trapMm=.30f;
        /** Within-sheet gap for stacked overprint; saved separately from cross-layer spacing. */
        public float overprintGapMm=.08f;
        /** Independent controls: point budget .5..2, physical radius .6..1.6. */
        public float pointDensity=1f, dotSize=1f;
        /** Older saved projects opt out to retain their pre-RC14 cut paths. */
        public boolean optimizePacking=true;
        public int paper=0xfffaf9f6;
        public int[] colors={0xff24252c,0xff71485e,0xffb36e89,0xffe3aa9e,0xfff1d6ba,0xff7695b1,0xff694b9d,0xffd87b45};
        public float[] density={1,1,1,1,1,1,1,1}, minDotMm={.8f,.8f,.8f,.8f,.8f,.8f,.8f,.8f};
        public boolean[] enabled={true,true,true,true,true,true,true,true};
        /** indices in bottom-to-top application order. */
        public int[] order={0,1,2,3,4,5,6,7};
        public boolean[] mask=null;
        public boolean draft=true;
        /** Adaptive, eligible-area-aware completion. Old project files retain their saved mode. */
        public boolean adaptiveCoverage=true;
        /** Fraction of eligible swatch-owned image pixels to cover with safe vinyl geometry. */
        public float targetCoverage=.64f;
        public Options copy(){Options o=new Options();o.layerCount=layerCount;o.overlap=overlap;o.assignment=assignment;
            o.detail=detail;o.shape=shape;o.widthMm=widthMm;o.minGapMm=minGapMm;o.overprintGapMm=overprintGapMm;o.trapMm=trapMm;o.paper=paper;
            o.colors=colors.clone();o.density=density.clone();o.minDotMm=minDotMm.clone();
            o.enabled=enabled.clone();o.order=order.clone();o.mask=mask;o.draft=draft;
            o.adaptiveCoverage=adaptiveCoverage;o.targetCoverage=targetCoverage;
            o.pointDensity=pointDensity;o.dotSize=dotSize;
            o.optimizePacking=optimizePacking;return o;}
    }
    public static final class Dot {
        public final float x,y,r;public final int layer;
        Dot(float x,float y,float r,int layer){this.x=x;this.y=y;this.r=r;this.layer=layer;}
    }
    public static final class Piece {
        public final int layer;public final float[] outer;public final List<float[]> holes;
        Piece(int layer,float[] outer,List<float[]> holes){this.layer=layer;this.outer=outer;this.holes=holes;}
    }
    public static final class Collision {
        public final float x,y;public final int a,b;
        Collision(float x,float y,int a,int b){this.x=x;this.y=y;this.a=a;this.b=b;}
    }
    public static final class Result {
        public final Options options; public final int width,height;
        public final List<Dot> dots;public final List<Piece> pieces;public final List<Collision> originalOverlaps;
        public final int skippedSpace,skippedMask,accepted;public final boolean draft;
        /** Approximate eligible raster coverage by circle envelopes, not the exact SVG union. */
        public final int eligiblePixels,coveredPixels; public final float[] layerCoverage;

        Result(Options o,int w,int h,List<Dot> dots,List<Piece> pieces,List<Collision> overlaps,int space,int mask,
               int eligiblePixels,int coveredPixels,float[] layerCoverage){
            options=o.copy();width=w;height=h;this.dots=Collections.unmodifiableList(dots);
            this.pieces=Collections.unmodifiableList(pieces);originalOverlaps=Collections.unmodifiableList(overlaps);
            skippedSpace=space;skippedMask=mask;accepted=dots.size();draft=o.draft;
            this.eligiblePixels=eligiblePixels;this.coveredPixels=coveredPixels;
            this.layerCoverage=layerCoverage.clone();
        }
        public String report(){return "Overlap mode="+new String[]{"Shared point set","Cross-layer spacing","Priority knockout","Stacked/overprint"}[options.overlap]
            +"; actionable cross-layer collisions="+(options.overlap==OVERPRINT?0:options.overlap==KNOCKOUT?0:originalOverlaps.size())
            +"; pre-knockout intersections="+(options.overlap==KNOCKOUT?originalOverlaps.size():0)
            +"; expected residual collisions="+(options.overlap==OVERPRINT?"intentional/unrestricted":options.overlap==KNOCKOUT?"trap-only":"0")
            +"; dots="+accepted+"; clipped contours="+pieces.size()+"; spacing rejects="+skippedSpace
            +"; mask rejects="+skippedMask+"; eligible covered="+
            (eligiblePixels==0?"n/a":String.format(Locale.US,"%.1f%%",100.0*coveredPixels/eligiblePixels))+
            "; point density="+Math.round(options.pointDensity*100)+"%"+
            "; dot size="+Math.round(options.dotSize*100)+"%"+
            "; per-color="+formatCoverage(layerCoverage)+
            " (circle-envelope estimate; target "+Math.round(options.targetCoverage*100)+"%); draft="+draft+
            ". Geometry is not a Cricut cut certification.";
        }
    }
    private static String formatCoverage(float[] fractions){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<fractions.length;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            if(i>0)b.append(',');
            b.append('L').append(i+1).append('=').append(Math.round(fractions[i]*100)).append('%');
        }
        return b.toString();
    }
    private static final class Seed {float x,y; Seed(float x,float y){this.x=x;this.y=y;}}
    private static float clamp(float f){return Math.max(0f,Math.min(1f,f));}
    private static int rr(int rgb){return (rgb>>16)&255;}
    private static int gg(int rgb){return (rgb>>8)&255;}
    private static int bb(int rgb){return rgb&255;}
    private static float light(int c){return (.2126f*rr(c)+.7152f*gg(c)+.0722f*bb(c))/255f;}
    private static boolean nearSubstrate(int c,int paper){
        int dr=rr(c)-rr(paper),dg=gg(c)-gg(paper),db=bb(c)-bb(paper);
        return dr*dr+dg*dg+db*db<=360;
    }
    private static float interest(int c,int paper){
        return clamp((float)Math.sqrt(colorDistance(rr(c),gg(c),bb(c),paper))/355f);
    }
    private static float colorDistance(float r,float g,float b,int c){
        float dr=r-rr(c),dg=g-gg(c),db=b-bb(c);
        return (2f*dr*dr+4f*dg*dg+1.3f*db*db);
    }
    /** Perceptual photo-to-vinyl ownership used by both Voronoi motifs and Stipple.
     * No face detection, forced use of every swatch, or source-color rewriting.
     * Match locally sampled source color against only the enabled physical rolls.
     * Density controls dot quantity, NOT ownership of unrelated swatch colors.
     */
    static final class PhotoColorMatcher {
        private static final double[] LINEAR=new double[256];
        static {for(int i=0;i<256;i++){if(((i)&63)==0)CancellationCheckpoint.check();double v=i/255.0;
            LINEAR[i]=v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);}}
        private final float[][] labs;
        private final double[] chromas;
        private final boolean[] enabled;
        private final float[] reusableSource=new float[3];
        private final PhotoSwatchAssociation association;
        PhotoColorMatcher(int[] colors,int count,boolean[] active){
            this(colors,count,active,null,null);
        }
        PhotoColorMatcher(int[] colors,int count,boolean[] active,int[] src,boolean[] mask){
            association=new PhotoSwatchAssociation(colors,count,active,src,mask,
                PhotoSwatchAssociation.SHAPES);
            if(colors==null||count<1||count>colors.length)throw new IllegalArgumentException("Palette");
            labs=new float[count][];chromas=new double[count];enabled=new boolean[count];
            for(int i=0;i<count;i++){if(((i)&63)==0)CancellationCheckpoint.check();
                enabled[i]=active==null || (i<active.length&&active[i]);
                labs[i]=lab(colors[i]);chromas[i]=Math.hypot(labs[i][1],labs[i][2]);
            }
        }
        /** D65 CIELAB, without allocating per candidate in the hot loop. */
        private static float[] lab(int rgb){float[] out=new float[3];lab(rgb,out);return out;}
        private static void lab(int rgb,float[] out){
            double r=LINEAR[(rgb>>16)&255],g=LINEAR[(rgb>>8)&255],b=LINEAR[rgb&255];
            double x=(.4124564*r+.3575761*g+.1804375*b)/.95047;
            double y=.2126729*r+.7151522*g+.0721750*b;
            double z=(.0193339*r+.1191920*g+.9503041*b)/1.08883;
            x=x>.008856451679?Math.cbrt(x):7.787037*x+16.0/116.0;
            y=y>.008856451679?Math.cbrt(y):7.787037*y+16.0/116.0;
            z=z>.008856451679?Math.cbrt(z):7.787037*z+16.0/116.0;
            out[0]=(float)(116*y-16);out[1]=(float)(500*(x-y));out[2]=(float)(200*(y-z));
        }
        int choose(int color){return association.chooseCached(color);}
    }
    private static int pixel(int[] p,int w,int h,float x,float y){return p[Math.min(h-1,Math.max(0,(int)y))*w+Math.min(w-1,Math.max(0,(int)x))];}
    private static boolean valid(Options o,int w,int h,float x,float y,float r){
        if(x-r<0||y-r<0||x+r>=w||y+r>=h)return false;
        if(o.mask==null)return true;
        for(int k=0;k<32;k++){if(((k)&63)==0)CancellationCheckpoint.check();double a=2*Math.PI*k/32;
            int px=(int)(x+Math.cos(a)*r),py=(int)(y+Math.sin(a)*r);
            if(px<0||py<0||px>=w||py>=h||!o.mask[py*w+px])return false;
        }
        return o.mask[(int)y*w+(int)x];
    }
    private static float[] ring(float x,float y,float r){float[] p=new float[48];
        for(int k=0;k<24;k++){if(((k)&63)==0)CancellationCheckpoint.check();double a=2*Math.PI*k/24;p[2*k]=x+(float)Math.cos(a)*r;p[2*k+1]=y+(float)Math.sin(a)*r;}return p;
    }
    /** Every shape fits its radius envelope, so per-sheet spacing uses the same
     * conservative bounding circles regardless of the chosen silhouette. */
    private static float[] silhouette(int shape,float x,float y,float r){
        if(shape==ROUND)return ring(x,y,r);
        if(shape==SQUARE)return new float[]{x-r*.71f,y-r*.71f,x+r*.71f,y-r*.71f,
            x+r*.71f,y+r*.71f,x-r*.71f,y+r*.71f};
        if(shape==DIAMOND)return new float[]{x,y-r,x+r,y,x,y+r,x-r,y};
        if(shape==HEXAGON)return radial(x,y,r,6,Math.PI/6);
        if(shape==ELLIPSE){float[] a=new float[48];for(int i=0;i<24;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            double t=Math.PI*2*i/24; a[2*i]=x+(float)Math.cos(t)*r*.73f;
            a[2*i+1]=y+(float)Math.sin(t)*r;
        }return a;}
        if(shape==CAPSULE){float[] a=new float[48];float arc=r*.66f,top=y-r*.34f,bot=y+r*.34f;
            for(int i=0;i<12;i++){if(((i)&63)==0)CancellationCheckpoint.check();double t=Math.PI+(Math.PI*i/11);a[2*i]=x+(float)Math.cos(t)*arc;
                a[2*i+1]=top+(float)Math.sin(t)*arc;}
            for(int i=0;i<12;i++){if(((i)&63)==0)CancellationCheckpoint.check();double t=Math.PI*i/11;a[24+2*i]=x+(float)Math.cos(t)*arc;
                a[24+2*i+1]=bot+(float)Math.sin(t)*arc;}
            return a;}
        if(shape==ROUNDED_SQUARE){float[] a=new float[32];int k=0;float corner=r*.23f,side=r*.75f;
            for(int cornerIndex=0;cornerIndex<4;cornerIndex++){if(((cornerIndex)&63)==0)CancellationCheckpoint.check();
                double start=-Math.PI/2+cornerIndex*Math.PI/2;
                float cx=x+(cornerIndex==0||cornerIndex==1?side-corner:-side+corner);
                float cy=y+(cornerIndex==0||cornerIndex==3?-side+corner:side-corner);
                for(int j=0;j<4;j++){if(((j)&63)==0)CancellationCheckpoint.check();double theta=start+j*Math.PI/6;
                    a[k++]=cx+(float)Math.cos(theta)*corner;
                    a[k++]=cy+(float)Math.sin(theta)*corner;}
            }return a;}
        if(shape==STAR){float[] a=new float[20];for(int i=0;i<10;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            float rad=(i&1)==0?r:r*.65f;double t=-Math.PI/2+i*Math.PI/5;
            a[2*i]=x+(float)Math.cos(t)*rad;a[2*i+1]=y+(float)Math.sin(t)*rad;
        }return a;}
        return ring(x,y,r);
    }
    private static float[] radial(float x,float y,float r,int corners,double offset){
        float[] a=new float[2*corners];for(int i=0;i<corners;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            double t=offset+2*Math.PI*i/corners;
            a[2*i]=x+(float)Math.cos(t)*r;a[2*i+1]=y+(float)Math.sin(t)*r;
        }return a;
    }
    private static boolean insidePolygon(float[] poly,float x,float y){return contains(poly,x,y);}
    private static int[] orderedColors(Options o){
        boolean[] seen=new boolean[o.layerCount];int[] actual=new int[o.layerCount];
        for(int i=0;i<o.layerCount;i++){if(((i)&63)==0)CancellationCheckpoint.check();int v=o.order[i];if(v<0||v>=o.layerCount||seen[v])throw new IllegalArgumentException("Invalid layer z-order");seen[v]=true;actual[i]=v;}
        return actual;
    }
    private static int choose(int px,Options o,float er,float eg,float eb){
        float r=clamp((rr(px)+er)/255f)*255f,g=clamp((gg(px)+eg)/255f)*255f,b=clamp((bb(px)+eb)/255f)*255f;
        int best=-1;float score=Float.MAX_VALUE;
        for(int i=0;i<o.layerCount;i++)if(o.enabled[i]&&o.density[i]>.001f){
            float d=colorDistance(r,g,b,o.colors[i])/Math.max(.12f,o.density[i]);
            if(d<score){score=d;best=i;}
        }
        return best;
    }
    /** Spatial Floyd-Steinberg diffusion, serpentine scan. Each raster sample has
     * one swatch owner; point assignment consults the same map in all overlap modes.
     * Quantization errors are never propagated into transparent or near-paper pixels.
     */
    private static int[] ownership(int[] src,int w,int h,Options o){
        int[] owners=new int[w*h];Arrays.fill(owners,-1);
        if(o.assignment==NEAREST){
            PhotoColorMatcher perceptual=new PhotoColorMatcher(o.colors,o.layerCount,o.enabled,
                src,o.mask);
            for(int i=0;i<src.length;i++){if(((i)&63)==0)CancellationCheckpoint.check();
                if((src[i]>>>24)<16||nearSubstrate(src[i],o.paper)||(o.mask!=null&&!o.mask[i]))continue;
                owners[i]=perceptual.choose(src[i]);
            }
            // Suppress isolated quantization outliers only in ambiguous neighborhoods.
            // A genuine accent-color patch is strongly preferred by the source color
            // and is not changed. Keep transparent and snipped pixels unassigned.
            return tidyIsolatedOwners(src,w,h,owners,perceptual);

        }
        float[] er=new float[w*h],eg=new float[w*h],eb=new float[w*h];
        for(int y=0;y<h;y++){if(((y)&63)==0)CancellationCheckpoint.check();
            boolean reverse=(y&1)!=0;
            for(int z=0;z<w;z++){if(((z)&63)==0)CancellationCheckpoint.check();
                int x=reverse?w-1-z:z,i=y*w+x,p=src[i];
                if((p>>>24)<16||nearSubstrate(p,o.paper)||(o.mask!=null&&!o.mask[i]))continue;
                int color=choose(p,o,er[i],eg[i],eb[i]);if(color<0)continue;
                owners[i]=color;
                float dr=Math.max(-255,Math.min(255,rr(p)+er[i]-rr(o.colors[color])));
                float dg=Math.max(-255,Math.min(255,gg(p)+eg[i]-gg(o.colors[color])));
                float db=Math.max(-255,Math.min(255,bb(p)+eb[i]-bb(o.colors[color])));
                int step=reverse?-1:1;
                propagate(er,eg,eb,owners,w,h,x+step,y,dr,dg,db,7/16f);
                propagate(er,eg,eb,owners,w,h,x-step,y+1,dr,dg,db,3/16f);
                propagate(er,eg,eb,owners,w,h,x,y+1,dr,dg,db,5/16f);
                propagate(er,eg,eb,owners,w,h,x+step,y+1,dr,dg,db,1/16f);
            }
        }
        return owners;
    }
    /** Remove a lone color assignment surrounded by a coherent alternative,
     * only when the source LAB match does not strongly prefer the original.
     * No new UI, no global recoloring and no modifications across alpha gaps. */
    private static int[] tidyIsolatedOwners(int[] src,int w,int h,int[] owner,PhotoColorMatcher matcher){
        int[] out=owner.clone();int[] counts=new int[matcher.labs.length];
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){if(((x)&63)==0)CancellationCheckpoint.check();
            int at=y*w+x,old=owner[at];if(old<0)continue;
            Arrays.fill(counts,0);
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)if(dx!=0||dy!=0){
                int n=owner[at+dy*w+dx];if(n>=0)counts[n]++;
            }
            int best=-1,number=0;for(int k=0;k<counts.length;k++)if(counts[k]>number){best=k;number=counts[k];}
            if(best<0||best==old||number<7)continue;
            float[] source=PhotoColorMatcher.lab(src[at]);
            float[] prior=matcher.labs[old],next=matcher.labs[best];
            double a=(source[0]-prior[0])*(source[0]-prior[0])+(source[1]-prior[1])*(source[1]-prior[1])+(source[2]-prior[2])*(source[2]-prior[2]);
            double b=(source[0]-next[0])*(source[0]-next[0])+(source[1]-next[1])*(source[1]-next[1])+(source[2]-next[2])*(source[2]-next[2]);
            if(b<=a+24)out[at]=best;
        }
        return out;
    }
    private static void propagate(float[] r,float[] g,float[] b,int[] owners,int w,int h,int x,int y,
                                  float dr,float dg,float db,float weight){
        if(x<0||y<0||x>=w||y>=h)return;
        int i=y*w+x;r[i]+=dr*weight;g[i]+=dg*weight;b[i]+=db*weight;
    }
    /** Each seed gets the centroid of its own luminance-weighted raster Voronoi region.
     * The shared mode runs this relaxation ONCE, before any swatch assignment. Independent
     * modes run it once for each channel (with channel-specific color likelihood).
     */
    private static void relax(List<Seed> pts,int[] src,int w,int h,Options o,int layer,int rounds){
        relax(pts,src,w,h,o,layer,rounds,null);
    }
    private static void relax(List<Seed> pts,int[] src,int w,int h,Options o,int layer,int rounds,int[] ownership){
        if(pts.size()<2)return;
        int cell=Math.max(3,(int)Math.sqrt((double)w*h/pts.size())*2);
        int cols=(w+cell-1)/cell,rows=(h+cell-1)/cell;
        int sample=Math.max(1,Math.min(4,Math.round(Math.max(w,h)/180f)));
        for(int iter=0;iter<rounds;iter++){if(((iter)&63)==0)CancellationCheckpoint.check();
            if(Thread.currentThread().isInterrupted())throw new CancellationException();
            @SuppressWarnings("unchecked") ArrayList<Integer>[] bins=new ArrayList[cols*rows];
            for(int i=0;i<pts.size();i++){if(((i)&63)==0)CancellationCheckpoint.check();Seed p=pts.get(i);int cx=Math.max(0,Math.min(cols-1,(int)p.x/cell)),cy=Math.max(0,Math.min(rows-1,(int)p.y/cell));int k=cy*cols+cx;
                if(bins[k]==null)bins[k]=new ArrayList<>();bins[k].add(i);}
            double[] mx=new double[pts.size()],my=new double[pts.size()],mass=new double[pts.size()];
            for(int y=sample/2;y<h;y+=sample)for(int x=sample/2;x<w;x+=sample){if(((x)&63)==0)CancellationCheckpoint.check();
                if(o.mask!=null&&!o.mask[y*w+x])continue;
                if(layer>=0 && ownership!=null && ownership[y*w+x]!=layer)continue;
                int px=src[y*w+x];if((px>>>24)<16||nearSubstrate(px,o.paper))continue;
                float weight=.12f+.88f*interest(px,o.paper);
                if(layer>=0){float d=colorDistance(rr(px),gg(px),bb(px),o.colors[layer]);weight*=.10f+.9f*clamp(1f-d/90000f);}
                if(weight<.035f)continue;
                int bx=x/cell,by=y/cell,nearest=-1;float dist=Float.MAX_VALUE;
                for(int yy=Math.max(0,by-2);yy<=Math.min(rows-1,by+2);yy++)for(int xx=Math.max(0,bx-2);xx<=Math.min(cols-1,bx+2);xx++){if(((xx)&63)==0)CancellationCheckpoint.check();
                    ArrayList<Integer> ids=bins[yy*cols+xx];if(ids==null)continue;
                    for(int id:ids){CancellationCheckpoint.check();Seed p=pts.get(id);float dx=x-p.x,dy=y-p.y,dd=dx*dx+dy*dy;
                        if(dd<dist){dist=dd;nearest=id;}}
                }
                if(nearest<0)continue;mx[nearest]+=x*weight;my[nearest]+=y*weight;mass[nearest]+=weight;
            }
            for(int i=0;i<pts.size();i++)if(mass[i]>0){Seed p=pts.get(i);float x=(float)(mx[i]/mass[i]),y=(float)(my[i]/mass[i]);
                if(valid(o,w,h,x,y,.25f) && (ownership==null || layer<0 || ownership[Math.min(h-1,Math.max(0,(int)y))*w+Math.min(w-1,Math.max(0,(int)x))]==layer)){
                    float movedX=p.x*.2f+x*.8f,movedY=p.y*.2f+y*.8f;
                    // For concave color islands, the centroid can fall outside the assigned region.
                    // Retain the original seed instead of crossing into another physical swatch.
                    if(ownership==null || layer<0 || ownership[Math.min(h-1,Math.max(0,(int)movedY))*w+Math.min(w-1,Math.max(0,(int)movedX))]==layer){
                        p.x=movedX;p.y=movedY;
                    }
                }}
        }
    }
    private static List<Seed> seeds(int[] src,int w,int h,Options o,int desired,int targetLayer,long randomSeed){
        Random rng=new Random(randomSeed);ArrayList<Seed> points=new ArrayList<>();
        int attempts=0,maxAttempts=Math.max(desired*30,2000);float spread=(float)Math.sqrt(w*(double)h/Math.max(1,desired));
        while(points.size()<desired&&attempts++<maxAttempts){
            float x=.5f+rng.nextFloat()*(w-1),y=.5f+rng.nextFloat()*(h-1);
            if(!valid(o,w,h,x,y,spread*.12f))continue;
            int px=pixel(src,w,h,x,y);if((px>>>24)<16||nearSubstrate(px,o.paper))continue;
            float weight=.08f+.92f*interest(px,o.paper);
            if(targetLayer>=0){float dist=colorDistance(rr(px),gg(px),bb(px),o.colors[targetLayer]);
                weight*=.10f+.90f*clamp(1f-dist/90000f);}
            if(rng.nextFloat()>weight)continue;
            points.add(new Seed(x,y));
        }
        // Spatially dispersed initialization; relaxation will remove small clusters.
        return points;
    }
    /** Sample independently from pixels actually owned by this vinyl color. Previous
     * code generated points in the whole photograph and discarded roughly 3/4 of
     * them after owner lookup, yielding a sparse "max detail" portrait. Each color
     * now gets its fair share of the active, cuttable point budget. */
    private static List<Seed> ownedSeeds(int[] src,int w,int h,Options o,int[] pool,
                                         int desired,long randomSeed){
        ArrayList<Seed> points=new ArrayList<>(Math.max(0,desired));
        if(desired<=0||pool.length==0)return points;
        Random rng=new Random(randomSeed);
        int attempts=0,maxAttempts=Math.max(150,desired*32);
        float marginPx=.5f;
        while(points.size()<desired && attempts++<maxAttempts){
            int i=pool[rng.nextInt(pool.length)];int x=i%w,y=i/w;
            int color=src[i];
            float probability=.20f+.80f*interest(color,o.paper);
            if(rng.nextFloat()>probability)continue;
            float sx=x+.12f+rng.nextFloat()*.76f,sy=y+.12f+rng.nextFloat()*.76f;
            if(!valid(o,w,h,sx,sy,marginPx))continue;
            points.add(new Seed(sx,sy));
        }
        return points;
    }
    private static int[][] eligibleByLayer(int[] owner,int layers){
        int[] count=new int[layers];for(int v:owner)if(v>=0&&v<layers)count[v]++;
        int[][] result=new int[layers][];for(int i=0;i<layers;i++)result[i]=new int[count[i]];
        Arrays.fill(count,0);
        for(int k=0;k<owner.length;k++){if(((k)&63)==0)CancellationCheckpoint.check();int v=owner[k];if(v>=0&&v<layers)result[v][count[v]++]=k;}
        return result;
    }
    private static int[] ownerBudgets(int[][] eligible,Options o,int total){
        double[] mass=new double[o.layerCount];double all=0;
        for(int i=0;i<o.layerCount;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            if(!o.enabled[i] || o.density[i]<=.001f)continue;
            // Density weights affect dot frequency, not ownership of other swatches.
            mass[i]=eligible[i].length * o.density[i];all+=mass[i];
        }
        int[] budgets=new int[o.layerCount];if(all<=0)return budgets;
        double[] fractions=new double[o.layerCount];int assigned=0;
        for(int i=0;i<o.layerCount;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            double exact=total*mass[i]/all;
            budgets[i]=(int)Math.floor(exact);
            fractions[i]=exact-budgets[i];assigned+=budgets[i];
        }
        // Deterministic largest-remainder rounding: never allocate more than total.
        while(assigned<total){int best=-1;double value=-1;
            for(int i=0;i<o.layerCount;i++)if(mass[i]>0&&fractions[i]>value){value=fractions[i];best=i;}
            if(best<0)break;budgets[best]++;fractions[best]=-1;assigned++;
        }
        return budgets;
    }
    private static final class Grid {
        private final HashMap<Long,ArrayList<Dot>> cells=new HashMap<>();final float cell;
        private float largestRadius=0f;
        Grid(float c){cell=Math.max(.2f,c);}
        private long key(int x,int y){return (((long)x)<<32)^(y&0xffffffffL);}
        void add(Dot d){int x=(int)Math.floor(d.x/cell),y=(int)Math.floor(d.y/cell);
            cells.computeIfAbsent(key(x,y),k->new ArrayList<>()).add(d);largestRadius=Math.max(largestRadius,d.r);}
        boolean free(float x,float y,float r,int layer,float gap,boolean cross){
            return freeExcept(x,y,r,layer,gap,cross,null);
        }
        boolean freeExcept(float x,float y,float r,int layer,float gap,boolean cross,Dot exclude){
            int gx=(int)Math.floor(x/cell),gy=(int)Math.floor(y/cell),reach=(int)Math.ceil((r+gap+largestRadius+cell)/cell);
            for(int yy=gy-reach;yy<=gy+reach;yy++)for(int xx=gx-reach;xx<=gx+reach;xx++){if(((xx)&63)==0)CancellationCheckpoint.check();
                ArrayList<Dot> list=cells.get(key(xx,yy));if(list==null)continue;
                for(Dot b:list){CancellationCheckpoint.check();if(b==exclude||(!cross&&b.layer!=layer))continue;
                    float spacing=r+b.r+(b.layer==layer?gap:gap);
                    float dx=b.x-x,dy=b.y-y;if(dx*dx+dy*dy<(spacing-.0002f)*(spacing-.0002f))return false;}
            }return true;
        }
        void replace(Dot old,Dot next){
            int gx=(int)Math.floor(old.x/cell),gy=(int)Math.floor(old.y/cell);
            ArrayList<Dot> list=cells.get(key(gx,gy));
            if(list==null||!list.remove(old))throw new IllegalStateException("Missing stipple dot");
            add(next);
        }
    }
    public static Result generate(int[] src,int w,int h,Options opts){
        if(src==null||w<12||h<12||src.length!=w*h||opts==null)throw new IllegalArgumentException("Invalid stipple image");
        Options o=opts.copy();if(o.layerCount<1||o.layerCount>8||o.colors.length<o.layerCount||o.density.length<o.layerCount||o.minDotMm.length<o.layerCount||o.enabled.length<o.layerCount ||o.order.length<o.layerCount||
            (o.mask!=null&&o.mask.length!=src.length)||o.widthMm<20||!Float.isFinite(o.widthMm)||o.overlap<0||o.overlap>3||o.assignment<0||o.assignment>1||o.trapMm<0||o.trapMm>1.5f||o.minGapMm<0||o.minGapMm>3f||!Float.isFinite(o.overprintGapMm)||o.overprintGapMm<0||o.overprintGapMm>3f||o.shape<0||o.shape>=SHAPE_NAMES.length ||!Float.isFinite(o.targetCoverage)||o.targetCoverage<.20f||o.targetCoverage>.80f||
            !Float.isFinite(o.pointDensity)||o.pointDensity<.5f||o.pointDensity>2f||
            !Float.isFinite(o.dotSize)||o.dotSize<.6f||o.dotSize>1.6f)
            throw new IllegalArgumentException("Invalid stipple settings");
        int[] order=orderedColors(o);
        int[] owner=ownership(src,w,h,o);
        for(int i=0;i<o.layerCount;i++)if(o.density[i]<0||o.density[i]>1||o.minDotMm[i]<.35f||o.minDotMm[i]>5f)throw new IllegalArgumentException("Invalid dot size/weight");
        // Independent layers cannot manufacture detail beyond physical dot clearance; caps
        // protect both memory/preview latency and each individual Cricut SVG.
        boolean adaptiveIndependent=o.adaptiveCoverage&&o.overlap!=SHARED;
        // Initial sites are the proven RC13 placement. Additional density only
        // extends the coverage pass, not the initial distribution or dot radii.
        int baseDesired=Math.min(o.draft?3200:(adaptiveIndependent?16000:MAX_DOTS),
            Math.max(80,(int)(160+o.detail*(o.draft?32.0:(adaptiveIndependent?168.0:128.0)))));
        int ceiling=o.draft?MAX_PREVIEW_DOTS:
            (adaptiveIndependent?(o.overlap==OVERPRINT?MAX_ADAPTIVE_EXPORT_DOTS:MAX_NON_OVERPRINT_EXPORT_DOTS):MAX_DOTS);
        int desired=adaptiveIndependent?Math.min(ceiling,Math.max(40,Math.round(baseDesired*o.pointDensity))):baseDesired;
        int initialDesired=Math.min(baseDesired,desired);
        float mmPerPixel=o.widthMm/w;
        ArrayList<Dot> all=new ArrayList<>();int skippedSpace=0,skippedMask=0;
        int[] perLayerCounts=new int[o.layerCount];
        int[] eligibleCounts=new int[o.layerCount];int eligibleTotal=0;
        for(int c:owner)if(c>=0){eligibleCounts[c]++;eligibleTotal++;}
        Grid grid=new Grid(Math.max(2f,(float)Math.sqrt((w*(double)h)/initialDesired)));
        if(o.overlap==SHARED){
            List<Seed> pts=seeds(src,w,h,o,initialDesired,-1,0x57A6E31L);
            relax(pts,src,w,h,o,-1,o.draft?1:5);
            pts.sort(Comparator.comparingDouble((Seed p)->p.y).thenComparingDouble(p->p.x));
            for(int z=0;z<pts.size();z++){if(((z)&63)==0)CancellationCheckpoint.check();
                Seed p=pts.get(z);int px=pixel(src,w,h,p.x,p.y);
                int layer=owner[Math.min(h-1,(int)p.y)*w+Math.min(w-1,(int)p.x)];
                if(layer<0||perLayerCounts[layer]>=MAX_LAYER_DOTS)continue;
                float base=(float)Math.sqrt(w*(double)h/Math.max(1,initialDesired))*mmPerPixel;
                float rMm=Math.max(o.minDotMm[layer]*.5f,Math.min(base*.32f,base*(.24f+.10f*(1-light(px)))));
                float r=scaledRadius(o,rMm,layer)/mmPerPixel;
                if(!valid(o,w,h,p.x,p.y,r)){skippedMask++;continue;}
                // Shared centers belong to exactly one swatch. A global clearance check
                // guarantees actual dot silhouettes cannot overlap after relaxation.
                if(!grid.free(p.x,p.y,r,layer,.08f/mmPerPixel,true)){skippedSpace++;continue;}
                Dot d=new Dot(p.x,p.y,r,layer);all.add(d);grid.add(d);perLayerCounts[layer]++;
            }
        }else{
            int[][] eligible=eligibleByLayer(owner,o.layerCount);
            int[] budgets=ownerBudgets(eligible,o,initialDesired);
            for(int li:order)if(budgets[li]>0){
                budgets[li]=Math.min(MAX_LAYER_DOTS,budgets[li]);
                List<Seed> pts=ownedSeeds(src,w,h,o,eligible[li],budgets[li],0x321CD94L+li*1097L);
                relax(pts,src,w,h,o,li,o.draft?1:4,owner);
                for(Seed p:pts){CancellationCheckpoint.check();int px=pixel(src,w,h,p.x,p.y);
                    int assigned=owner[Math.min(h-1,(int)p.y)*w+Math.min(w-1,(int)p.x)];
                    if(assigned!=li)continue;
                    float base=(float)Math.sqrt(w*(double)h/Math.max(1,initialDesired))*mmPerPixel;
                    float rMm=o.adaptiveCoverage
                        ? adaptiveRadiusMm(o,li,px,eligibleTotal,initialDesired,mmPerPixel,w,h)
                        : Math.max(o.minDotMm[li]*.5f,Math.min(base*.40f,base*(.24f+.15f*(1-light(px)))));
                    float r=scaledRadius(o,rMm,li)/mmPerPixel;
                    if(!(o.adaptiveCoverage?supportedEnvelope(owner,o,w,h,p.x,p.y,r):valid(o,w,h,p.x,p.y,r))){skippedMask++;continue;}
                    // Separate layers may deliberately collide in knockout or overprint.
                    boolean cross=o.overlap==SPACED;
                    if(!grid.free(p.x,p.y,r,li,(o.overlap==OVERPRINT?o.overprintGapMm:o.minGapMm)/mmPerPixel,cross)){
                        boolean moved=false;
                        for(int k=0;k<12;k++){if(((k)&63)==0)CancellationCheckpoint.check();
                            double a=2*Math.PI*k/12;
                            float px2=p.x+(float)Math.cos(a)*r*.45f,py2=p.y+(float)Math.sin(a)*r*.45f;
                            if(valid(o,w,h,px2,py2,r) &&
                               (!o.adaptiveCoverage || (owner[Math.min(h-1,(int)py2)*w+Math.min(w-1,(int)px2)]==li &&
                                   supportedEnvelope(owner,o,w,h,px2,py2,r))) &&
                               grid.free(px2,py2,r,li,(o.overlap==OVERPRINT?o.overprintGapMm:o.minGapMm)/mmPerPixel,cross)){
                                p.x=px2;p.y=py2;moved=true;break;}
                        }
                        if(!moved){skippedSpace++;continue;}
                    }
                    Dot d=new Dot(p.x,p.y,r,li);all.add(d);grid.add(d);perLayerCounts[li]++;
                }
            }
        }
        if(adaptiveIndependent){
            // The desired count is only a safety budget. Completion is determined by
            // measured, per-color eligible coverage; no unbounded rejection loop.
            adaptiveCompletion(src,owner,w,h,o,initialDesired,desired,eligibleTotal,eligibleCounts,
                mmPerPixel,all,grid,perLayerCounts);
            if(o.pointDensity>1.001f && all.size()<desired){
                // With a higher density request, use physically minimum-sized
                // detail dots where normal-size placement cannot fit. Unlike
                // doubling a cap alone, this visits actual uncovered pixels.
                fineCompletion(owner,w,h,o,desired,mmPerPixel,eligibleCounts,all,grid,perLayerCounts);
            }
            // Expand existing dots only when the extra vinyl is supported and
            // remains physically separated from other cuts in the same sheet.
            if(o.optimizePacking)growExisting(owner,w,h,o,eligibleCounts,all,grid);
        }else{
        // Secondary blue-noise-style coverage pass. The first stochastic Lloyd
        // allocation can leave large holes because many relaxed seeds land outside
        // their originally assigned swatch or collide with an accepted neighbor.
        // Fill only confirmed photo-owned pixels, never invent cuts on background or snips.
        // SHARED remains a single globally relaxed point set by contract.
        if(o.overlap!=SHARED && all.size()<desired){
            int[][] eligible=eligibleByLayer(owner,o.layerCount);
            int[] budgets=ownerBudgets(eligible,o,initialDesired);
            float pitch=Math.max(.85f, (float)Math.sqrt(w*(double)h/Math.max(1,desired))*.69f);
            for(int pass=0;pass<3&&all.size()<desired;pass++){if(((pass)&63)==0)CancellationCheckpoint.check();
                float offset=(pass*.31f+.17f)*pitch;
                for(int iy=0;iy<(int)Math.ceil(h/pitch)&&all.size()<desired;iy++){if(((iy)&63)==0)CancellationCheckpoint.check();
                    float yy=(iy+.5f)*pitch+offset%pitch-pitch*.45f;
                    for(int ix=0;ix<(int)Math.ceil(w/pitch)&&all.size()<desired;ix++){if(((ix)&63)==0)CancellationCheckpoint.check();
                        if(Thread.currentThread().isInterrupted())throw new CancellationException();
                        float xx=(ix+.5f)*pitch+((iy&1)*.37f+pass*.23f)*pitch-pitch*.30f;
                        if(xx<1||yy<1||xx>=w-1||yy>=h-1)continue;
                        int idx=(int)yy*w+(int)xx,li=owner[idx];
                        if(li<0||!o.enabled[li]||o.density[li]<=0||perLayerCounts[li]>=MAX_LAYER_DOTS||
                            perLayerCounts[li]>=budgets[li])continue;
                        float base=(float)Math.sqrt(w*(double)h/Math.max(1,initialDesired))*mmPerPixel;
                        float radiusMm=Math.max(o.minDotMm[li]*.5f, Math.min(base*.43f,
                            base*(.27f+.16f*(1-light(src[idx])))));
                        float r=scaledRadius(o,radiusMm,li)/mmPerPixel;
                        if(!valid(o,w,h,xx,yy,r))continue;
                        boolean cross=o.overlap==SPACED;
                        float gap=(o.overlap==OVERPRINT?o.overprintGapMm:o.minGapMm)/mmPerPixel;
                        if(!grid.free(xx,yy,r,li,gap,cross))continue;
                        Dot d=new Dot(xx,yy,r,li);all.add(d);grid.add(d);perLayerCounts[li]++;
                    }
                }
            }
        }
        }
        ArrayList<Collision> collisions=new ArrayList<>();
        if(o.overlap!=OVERPRINT)for(int i=0;i<all.size();i++){if(((i)&63)==0)CancellationCheckpoint.check();Dot a=all.get(i);
            for(int j=0;j<i;j++){if(((j)&63)==0)CancellationCheckpoint.check();Dot b=all.get(j);if(a.layer==b.layer)continue;
                float dx=a.x-b.x,dy=a.y-b.y,r=a.r+b.r;
                if(dx*dx+dy*dy<r*r-.0001f)collisions.add(new Collision((a.x+b.x)*.5f,(a.y+b.y)*.5f,a.layer,b.layer));
            }
        }
        ArrayList<Piece> pieces=new ArrayList<>();
        int[] priority=new int[o.layerCount];for(int i=0;i<o.layerCount;i++)priority[order[i]]=i;
        for(Dot dot:all){CancellationCheckpoint.check();
            if(Thread.currentThread().isInterrupted())throw new CancellationException();
            if(o.overlap!=KNOCKOUT){pieces.add(new Piece(dot.layer,silhouette(o.shape,dot.x,dot.y,dot.r),Collections.emptyList()));continue;}
            ArrayList<Dot> covering=new ArrayList<>();
            for(Dot higher:all)if(priority[higher.layer]>priority[dot.layer]){
                float effective=Math.max(0,higher.r-o.trapMm/mmPerPixel);
                float dx=dot.x-higher.x,dy=dot.y-higher.y;
                if(effective>0&&dx*dx+dy*dy<(dot.r+effective)*(dot.r+effective))covering.add(higher);
            }
            if(covering.isEmpty())pieces.add(new Piece(dot.layer,silhouette(o.shape,dot.x,dot.y,dot.r),Collections.emptyList()));
            else pieces.addAll(clipRaster(dot,covering,o.trapMm/mmPerPixel,.10f/mmPerPixel,o.shape));
        }
        Coverage covered=coverage(owner,w,h,o.layerCount,all);
        float[] fraction=new float[o.layerCount];int sumCovered=0;
        for(int i=0;i<o.layerCount;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            fraction[i]=eligibleCounts[i]==0?0f:covered.perLayer[i]/(float)eligibleCounts[i];
            sumCovered+=covered.perLayer[i];
        }
        return new Result(o,w,h,all,pieces,collisions,skippedSpace,skippedMask,
            eligibleTotal,sumCovered,fraction);
    }
    /** Physical radius is computed from eligible artwork area, NOT the total
     * rectangular canvas. A detail increase increases available samples without
     * forcing vinyl area to drop. Each individual cut still has its minimum size. */
    private static float scaledRadius(Options o,float radiusMm,int li){
        return Math.max(o.minDotMm[li]*.5f,radiusMm*o.dotSize);
    }
    private static float adaptiveRadiusMm(Options o,int li,int color,int eligibleTotal,
                                          int desired,float mmPerPixel,int w,int h){
        // Freeze maximum export detail's physical radius at the known v2.51.1
        // reference budget. Additional dots can fill space but cannot thin cuts.
        int radiusBudget=o.draft?desired:Math.min(desired,MAX_DOTS);
        float fullPitch=(float)Math.sqrt(w*(double)h/Math.max(1,radiusBudget))*mmPerPixel;
        float legacy=Math.max(o.minDotMm[li]*.5f,Math.min(fullPitch*.40f,
            fullPitch*(.24f+.15f*(1-light(color)))));
        if(!o.draft)return legacy; // Export must never lose coverage by shrinking original dots.
        float supportedPitch=(float)Math.sqrt(Math.max(1,eligibleTotal)/(double)Math.max(1,desired))*mmPerPixel;
        float tonal=.88f+.12f*interest(color,o.paper);
        return Math.max(legacy,Math.max(o.minDotMm[li]*.5f,supportedPitch*.52f*tonal));
    }
    private static final class Coverage {
        final BitSet[] perColor;final int[] perLayer;
        Coverage(int layers,int pixels){perColor=new BitSet[layers];perLayer=new int[layers];
            for(int i=0;i<layers;i++)perColor[i]=new BitSet(pixels);}
    }
    /** Exact pixels of each circular envelope (approximation for other motifs).
     * Ownership prevents a large dot on one color from hiding a coverage deficit
     * on another swatch; this is NOT opaque compositing or a guarantee of full fill. */
    private static void paintCoverage(Coverage cover,int[] owner,int w,int h,Dot d){
        int x0=Math.max(0,(int)Math.floor(d.x-d.r)),x1=Math.min(w-1,(int)Math.ceil(d.x+d.r));
        int y0=Math.max(0,(int)Math.floor(d.y-d.r)),y1=Math.min(h-1,(int)Math.ceil(d.y+d.r));
        BitSet bits=cover.perColor[d.layer];float r2=d.r*d.r;
        for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){if(((x)&63)==0)CancellationCheckpoint.check();
            float dx=x+.5f-d.x,dy=y+.5f-d.y;
            int pos=y*w+x;
            if(owner[pos]==d.layer && dx*dx+dy*dy<=r2 && !bits.get(pos)){
                bits.set(pos);cover.perLayer[d.layer]++;
            }
        }
    }
    private static Coverage coverage(int[] owner,int w,int h,int layers,List<Dot> dots){
        Coverage result=new Coverage(layers,w*h);
        for(Dot d:dots)paintCoverage(result,owner,w,h,d);
        return result;
    }
    /** Confirm the full physical envelope stays within supported source pixels.
     * Supports dots near other photo colors, but excludes empty paper/alpha and Snips. */
    private static boolean supportedEnvelope(int[] owner,Options o,int w,int h,float x,float y,float r){
        if(!valid(o,w,h,x,y,r))return false;
        int cx=(int)x,cy=(int)y;
        if(owner[cy*w+cx]<0)return false;
        for(int k=0;k<16;k++){if(((k)&63)==0)CancellationCheckpoint.check();
            double t=k*(Math.PI*2/16);
            int px=(int)Math.floor(x+Math.cos(t)*r),py=(int)Math.floor(y+Math.sin(t)*r);
            if(px<0||px>=w||py<0||py>=h||owner[py*w+px]<0)return false;
        }
        return true;
    }
    /** Bounded, multiscale residual-coverage correction. Unlike the old three
     * fixed grid passes, every insertion updates measured coverage and each color
     * stops when its target is met or the finite candidate domain is exhausted.
     * Candidates are tested by the same spatial grid used for all original dots. */
    private static int gcd(int a,int b){while(b!=0){int t=a%b;a=b;b=t;}return a;}
    private static void adaptiveCompletion(int[] src,int[] owner,int w,int h,Options o,
                                           int initialDesired,int desired,
                                           int eligibleTotal,int[] eligibleCounts,float mmPerPixel,
                                           ArrayList<Dot> dots,Grid grid,int[] layerCounts){
        if(eligibleTotal==0||dots.size()>=desired)return;
        Coverage cov=coverage(owner,w,h,o.layerCount,dots);
        float nominalRadiusPx=scaledRadius(o,
            adaptiveRadiusMm(o,0,0xff666666,eligibleTotal,initialDesired,mmPerPixel,w,h),0)/mmPerPixel;
        // Strides shrink towards a physical minimum; at most six complete passes.
        int[] stride={Math.max(2,Math.round(nominalRadiusPx*2.2f)),
            Math.max(2,Math.round(nominalRadiusPx*1.5f)),
            Math.max(2,Math.round(nominalRadiusPx)),2,2,2};
        for(int pass=0;pass<stride.length&&dots.size()<desired;pass++){if(((pass)&63)==0)CancellationCheckpoint.check();
            if(Thread.currentThread().isInterrupted())throw new CancellationException();
            int step=stride[pass];
            float offX=(pass*.382f)%1f,offY=(pass*.618f)%1f;
            int added=0;
            int nx=(w+step-1)/step,ny=(h+step-1)/step,cells=nx*ny;
            // Coprime modular stride visits every tile exactly once, distributing
            // the remaining global budget across the entire image rather than
            // spending it all in the top rows.
            int jump=Math.max(1,(int)(cells*.6180339887));
            while(gcd(jump,cells)!=1)jump++;
            int cursor=(int)((pass*2654435761L & 0x7fffffffL)%cells);
            for(int t=0;t<cells&&dots.size()<desired;t++){if(((t)&63)==0)CancellationCheckpoint.check();
                    if((t&1023)==0 && Thread.currentThread().isInterrupted())throw new CancellationException();
                    int gx=(cursor%nx)*step,gy=(cursor/nx)*step;
                    cursor=(cursor+jump)%cells;
                    int ix=Math.min(w-1,gx+(int)(step*offX));
                    int iy=Math.min(h-1,gy+(int)(step*offY));
                    int pos=iy*w+ix,li=owner[pos];
                    if(li<0||layerCounts[li]>=MAX_LAYER_DOTS||o.density[li]<=0)continue;
                    float target=o.targetCoverage*o.density[li];
                    if(eligibleCounts[li]==0 || cov.perLayer[li]>=target*eligibleCounts[li] ||
                        cov.perColor[li].get(pos))continue;
                    float radius=scaledRadius(o,
                        adaptiveRadiusMm(o,li,src[pos],eligibleTotal,initialDesired,mmPerPixel,w,h),li)/mmPerPixel;
                    float[][] offsets={{.5f,.5f},{.22f,.22f},{.78f,.78f},{.22f,.78f},{.78f,.22f}};
                    boolean accepted=false;
                    for(float[] a:offsets){CancellationCheckpoint.check();
                        float x=Math.min(w-1.001f,gx+step*a[0]),y=Math.min(h-1.001f,gy+step*a[1]);
                        if(!supportedEnvelope(owner,o,w,h,x,y,radius))continue;
                        int at=Math.min(h-1,(int)y)*w+Math.min(w-1,(int)x);
                        if(owner[at]!=li||cov.perColor[li].get(at))continue;
                        float gap=(o.overlap==OVERPRINT?o.overprintGapMm:o.minGapMm)/mmPerPixel;
                        if(!grid.free(x,y,radius,li,gap,o.overlap==SPACED))continue;
                        Dot dot=new Dot(x,y,radius,li);dots.add(dot);grid.add(dot);
                        layerCounts[li]++;paintCoverage(cov,owner,w,h,dot);
                        added++;accepted=true;break;
                    }
            }
            if(added==0 && pass>=3)break;
        }
    }
    /** Deterministic maximal-gap search for high-density mode.
     * Only additional details use the smallest physically valid dot. Existing
     * dots are neither displaced nor thinned. Every added center is eligible,
     * every envelope respects Snips/transparent paper, and a spatial grid
     * enforces intra-layer clearance. An unfilled region is not forcibly painted
     * when the blade clearance or per-layer SVG capacity blocks placement.
     */
    private static void fineCompletion(int[] owner,int w,int h,Options o,int desired,
                                       float mmPerPixel,int[] eligibleCounts,
                                       ArrayList<Dot> dots,Grid grid,int[] layerCounts){
        Coverage cover=coverage(owner,w,h,o.layerCount,dots);
        int cells=w*h,skip=Math.max(1,(int)(cells*.6180339887));
        while(gcd(skip,cells)!=1)skip++;
        int pos=(int)((long)cells*37/101);
        final float gap=(o.overlap==OVERPRINT?o.overprintGapMm:o.minGapMm)/mmPerPixel;
        final boolean cross=o.overlap==SPACED;
        float requested=(o.pointDensity-1f);
        final float[][] offsets={{.5f,.5f},{.23f,.23f},{.77f,.77f},{.23f,.77f},{.77f,.23f}};
        for(int pass=0;pass<2 && dots.size()<desired;pass++){if(((pass)&63)==0)CancellationCheckpoint.check();
            int added=0;
            for(int t=0;t<cells && dots.size()<desired;t++){if(((t)&63)==0)CancellationCheckpoint.check();
                if((t&2047)==0 && Thread.currentThread().isInterrupted())throw new CancellationException();
                pos+=skip;if(pos>=cells)pos-=cells;
                int li=owner[pos];
                if(li<0 || layerCounts[li]>=MAX_LAYER_DOTS || o.density[li]<=0 ||
                   eligibleCounts[li]==0 || cover.perColor[li].get(pos))continue;
                // Density above 100% raises the coverage target without changing
                // the user's saved nominal coverage preference.
                float target=Math.min(.92f,o.targetCoverage+.25f*requested)*o.density[li];
                if(cover.perLayer[li]>=target*eligibleCounts[li])continue;
                int px=pos%w,py=pos/w;
                float physicalMin=o.minDotMm[li]*.5f;
                float r=Math.max(physicalMin,physicalMin*o.dotSize)/mmPerPixel;
                for(float[] offset:offsets){CancellationCheckpoint.check();
                    float x=px+offset[0],y=py+offset[1];
                    if(!supportedEnvelope(owner,o,w,h,x,y,r)||
                       !grid.free(x,y,r,li,gap,cross))continue;
                    Dot dot=new Dot(x,y,r,li);
                    dots.add(dot);grid.add(dot);layerCounts[li]++;
                    paintCoverage(cover,owner,w,h,dot);
                    added++;break;
                }
            }
            if(added==0)break;
        }
    }
    /** Conservative, bounded enlargement of dots with available same-sheet
     * clearance. Does not extend cuts into background, Snips or transparency.
     * Dot quantity and dot radius are now independent design parameters.
     */
    private static void growExisting(int[] owner,int w,int h,Options o,int[] eligible,
                                     ArrayList<Dot> dots,Grid grid){
        if(dots.isEmpty())return;
        Coverage covered=coverage(owner,w,h,o.layerCount,dots);
        final float mmPerPixel=o.widthMm/w;
        final float gap=(o.overlap==OVERPRINT?o.overprintGapMm:o.minGapMm)/mmPerPixel;
        final boolean cross=o.overlap==SPACED;
        final float[] factors={1.13f,1.075f,1.035f};
        for(int i=0;i<dots.size();i++){if(((i)&63)==0)CancellationCheckpoint.check();
            if((i&255)==0 && Thread.currentThread().isInterrupted())throw new CancellationException();
            Dot current=dots.get(i);int li=current.layer;
            if(eligible[li]==0 || covered.perLayer[li]>=eligible[li]*o.targetCoverage*o.density[li])continue;
            for(float factor:factors){CancellationCheckpoint.check();
                float proposed=current.r*factor;
                if(!supportedEnvelope(owner,o,w,h,current.x,current.y,proposed))continue;
                if(!grid.freeExcept(current.x,current.y,proposed,li,gap,cross,current))continue;
                if(!uncoveredAnnulus(covered.perColor[li],owner,w,h,current,proposed))continue;
                Dot next=new Dot(current.x,current.y,proposed,li);
                grid.replace(current,next);dots.set(i,next);paintCoverage(covered,owner,w,h,next);
                break;
            }
        }
    }
    private static boolean uncoveredAnnulus(BitSet covered,int[] owner,int w,int h,Dot dot,float larger){
        float middle=(dot.r+larger)*.5f;
        for(int j=0;j<16;j++){if(((j)&63)==0)CancellationCheckpoint.check();
            double angle=j*Math.PI/8;
            int x=(int)Math.floor(dot.x+Math.cos(angle)*middle);
            int y=(int)Math.floor(dot.y+Math.sin(angle)*middle);
            if(x>=0&&x<w&&y>=0&&y<h){int pos=y*w+x;
                if(owner[pos]==dot.layer&&!covered.get(pos))return true;
            }
        }
        return false;
    }
    /** Conservative boolean circle difference, rasterized only where paths actually overlap.
     * All exported cut contours are in physical units; 0.10 mm clipping precision is
     * recorded. Clipped fragments below the requested min dot diameter are discarded.
     * These clipped paths require a physical test cut; partial crescents can be fragile.
     */
    private static List<Piece> clipRaster(Dot lower,List<Dot> higher,float trap,float step,int shape){
        float s=Math.max(.04f,step);
        int nx=(int)Math.ceil(2*lower.r/s)+4,ny=nx;
        if(nx>140||ny>140)return Collections.emptyList();
        float x0=lower.x-nx*s*.5f,y0=lower.y-ny*s*.5f;
        float[] lowerBoundary=silhouette(shape,lower.x,lower.y,lower.r);
        float[][] higherBoundaries=new float[higher.size()][];
        for(int i=0;i<higher.size();i++){if(((i)&63)==0)CancellationCheckpoint.check();
            Dot dot=higher.get(i);higherBoundaries[i]=silhouette(shape,dot.x,dot.y,Math.max(0,dot.r-trap));
        }
        boolean[] on=new boolean[nx*ny];
        for(int y=0;y<ny;y++)for(int x=0;x<nx;x++){if(((x)&63)==0)CancellationCheckpoint.check();
            float px=x0+(x+.5f)*s,py=y0+(y+.5f)*s;
            if(!insidePolygon(lowerBoundary,px,py))continue;
            boolean blocked=false;
            for(float[] high:higherBoundaries)if(insidePolygon(high,px,py)){
                blocked=true;break;
            }
            on[y*nx+x]=!blocked;
        }
        // Trace oriented boundary edges. Convert from integer cell coordinates to
        // millimeter-space vertices, removing all collinear vertices.
        Map<Long,List<Long>> next=new LinkedHashMap<>();
        for(int y=0;y<ny;y++)for(int x=0;x<nx;x++)if(on[y*nx+x]){
            if(y==0||!on[(y-1)*nx+x])edge(next,pt(x,y),pt(x+1,y));
            if(x==nx-1||!on[y*nx+x+1])edge(next,pt(x+1,y),pt(x+1,y+1));
            if(y==ny-1||!on[(y+1)*nx+x])edge(next,pt(x+1,y+1),pt(x,y+1));
            if(x==0||!on[y*nx+x-1])edge(next,pt(x,y+1),pt(x,y));
        }
        ArrayList<float[]> outers=new ArrayList<>(),holes=new ArrayList<>();
        int guard=0;
        while(!next.isEmpty()&&guard++<500){long first=next.keySet().iterator().next();
            ArrayList<Long> loop=new ArrayList<>();long at=first;
            for(int iter=0;iter<20000;iter++){if(((iter)&63)==0)CancellationCheckpoint.check();
                loop.add(at);List<Long> dest=next.get(at);if(dest==null||dest.isEmpty())break;
                long end=dest.remove(dest.size()-1);if(dest.isEmpty())next.remove(at);
                at=end;if(at==first)break;
            }
            if(at!=first||loop.size()<4)continue;
            ArrayList<Long> clean=new ArrayList<>();
            for(int k=0;k<loop.size();k++){if(((k)&63)==0)CancellationCheckpoint.check();
                long a=loop.get((k+loop.size()-1)%loop.size()),b=loop.get(k),c=loop.get((k+1)%loop.size());
                int dx1=xi(b)-xi(a),dy1=yi(b)-yi(a),dx2=xi(c)-xi(b),dy2=yi(c)-yi(b);
                if(dx1*dy2-dy1*dx2!=0)clean.add(b);
            }
            if(clean.size()<3)continue;
            float[] p=new float[clean.size()*2];double area=0;
            for(int k=0;k<clean.size();k++){if(((k)&63)==0)CancellationCheckpoint.check();long b=clean.get(k),c=clean.get((k+1)%clean.size());
                p[2*k]=x0+xi(b)*s;p[2*k+1]=y0+yi(b)*s;
                area+=(double)xi(b)*yi(c)-(double)yi(b)*xi(c);
            }
            if(Math.abs(area)*s*s<.04f)continue;
            if(area>0)outers.add(p);else holes.add(p);
        }
        ArrayList<Piece> result=new ArrayList<>();
        for(float[] outer:outers){CancellationCheckpoint.check();float[] bounds=bounds(outer);
            if(Math.min(bounds[2]-bounds[0],bounds[3]-bounds[1])<Math.min(lower.r*2,2.5f*step))continue;
            ArrayList<float[]> inside=new ArrayList<>();
            for(float[] hole:holes)if(contains(outer,hole[0],hole[1]))inside.add(hole);
            result.add(new Piece(lower.layer,outer,inside));
        }
        return result;
    }
    private static long pt(int x,int y){return ((long)x<<32)|(y&0xffffffffL);}
    private static int xi(long p){return (int)(p>>32);}
    private static int yi(long p){return (int)p;}
    private static void edge(Map<Long,List<Long>> m,long a,long b){m.computeIfAbsent(a,k->new ArrayList<>()).add(b);}
    private static float[] bounds(float[] p){float x=Float.POSITIVE_INFINITY,y=x,xx=-x,yy=-x;
        for(int k=0;k<p.length;k+=2){if(((k)&63)==0)CancellationCheckpoint.check();x=Math.min(x,p[k]);y=Math.min(y,p[k+1]);xx=Math.max(xx,p[k]);yy=Math.max(yy,p[k+1]);}return new float[]{x,y,xx,yy};}
    private static boolean contains(float[] p,float x,float y){boolean in=false;for(int a=0,b=p.length-2;a<p.length;b=a,a+=2){if(((a)&63)==0)CancellationCheckpoint.check();
        float ax=p[a],ay=p[a+1],bx=p[b],by=p[b+1];if((ay>y)!=(by>y)&&x<(bx-ax)*(y-ay)/(by-ay)+ax)in=!in;
        }return in;}
}

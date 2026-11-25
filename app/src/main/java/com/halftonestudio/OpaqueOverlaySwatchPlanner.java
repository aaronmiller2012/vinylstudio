package com.halftonestudio;

import java.util.Arrays;

/**
 * Opaque-vinyl colour separation for independent overprint marks.
 *
 * Palette indices are PHYSICAL sheet IDs: L1 is the solid foundation and a
 * larger index always covers a smaller index.  This is not translucent CMYK,
 * alpha compositing or a reordering of user-selected rolls.  The three
 * candidate shapes are fixed by the geometry engine; only their physical
 * colour owners (or their omission) are chosen here.
 */
public final class OpaqueOverlaySwatchPlanner {
    private OpaqueOverlaySwatchPlanner() {}
    private static final int SAMPLE_SIDE=7;
    private static int clamp(int n,int max){return Math.max(0,Math.min(max-1,n));}
    private static boolean inside(float[] polygon,float x,float y){
        if(polygon==null||polygon.length<6)return false;
        boolean odd=false;int n=polygon.length/2;
        for(int i=0,j=n-1;i<n;j=i++){
            float xi=polygon[2*i],yi=polygon[2*i+1];
            float xj=polygon[2*j],yj=polygon[2*j+1];
            if((yi>y)!=(yj>y)&&x<(xj-xi)*(y-yi)/(yj-yi)+xi)odd=!odd;
        }
        return odd;
    }
    /** Reject a duplicate same-sheet cut only if polygons touch, cross or contain
     * one another. Bounding-box overlap alone is NOT evidence of interference. */
    static boolean polygonsContact(float[] a,float[] b){
        if(a==null||b==null||a.length<6||b.length<6)return false;
        int na=a.length/2,nb=b.length/2;
        float aminX=Float.POSITIVE_INFINITY,aminY=Float.POSITIVE_INFINITY;
        float amaxX=Float.NEGATIVE_INFINITY,amaxY=Float.NEGATIVE_INFINITY;
        float bminX=Float.POSITIVE_INFINITY,bminY=Float.POSITIVE_INFINITY;
        float bmaxX=Float.NEGATIVE_INFINITY,bmaxY=Float.NEGATIVE_INFINITY;
        for(int i=0;i<na;i++){
            float x=a[i*2],y=a[i*2+1];
            if(!Float.isFinite(x)||!Float.isFinite(y))return true; // unsafe data: fail closed
            aminX=Math.min(aminX,x);amaxX=Math.max(amaxX,x);
            aminY=Math.min(aminY,y);amaxY=Math.max(amaxY,y);
        }
        for(int i=0;i<nb;i++){
            float x=b[i*2],y=b[i*2+1];
            if(!Float.isFinite(x)||!Float.isFinite(y))return true;
            bminX=Math.min(bminX,x);bmaxX=Math.max(bmaxX,x);
            bminY=Math.min(bminY,y);bmaxY=Math.max(bmaxY,y);
        }
        // Broad phase: separated boxes cannot contact; avoid vertex-pair work.
        if(amaxX<bminX||bmaxX<aminX||amaxY<bminY||bmaxY<aminY)return false;
        for(int i=0;i<na;i++){
            float ax=a[2*i],ay=a[2*i+1];
            if(inside(b,ax,ay))return true;
            int ia=(i+1)%na;
            for(int j=0;j<nb;j++){
                float bx=b[2*j],by=b[2*j+1];
                if(i==0&&inside(a,bx,by))return true;
                int jb=(j+1)%nb;
                if(segmentsContact(ax,ay,a[2*ia],a[2*ia+1],
                        bx,by,b[2*jb],b[2*jb+1]))return true;
            }
        }
        return false;
    }
    private static double cross(float ax,float ay,float bx,float by,float cx,float cy){
        return ((double)bx-ax)*(cy-ay)-((double)by-ay)*(cx-ax);
    }
    private static boolean within(float v,float a,float b){
        return v>=Math.min(a,b)-1e-6f&&v<=Math.max(a,b)+1e-6f;
    }
    private static boolean onSegment(float x,float y,float ax,float ay,float bx,float by){
        return Math.abs(cross(ax,ay,bx,by,x,y))<=1e-6&&
            within(x,ax,bx)&&within(y,ay,by);
    }
    private static boolean segmentsContact(float ax,float ay,float bx,float by,
                                           float cx,float cy,float dx,float dy){
        double o1=cross(ax,ay,bx,by,cx,cy),o2=cross(ax,ay,bx,by,dx,dy);
        double o3=cross(cx,cy,dx,dy,ax,ay),o4=cross(cx,cy,dx,dy,bx,by);
        if(((o1>1e-6&&o2< -1e-6)||(o1< -1e-6&&o2>1e-6))&&
           ((o3>1e-6&&o4< -1e-6)||(o3< -1e-6&&o4>1e-6)))return true;
        return onSegment(cx,cy,ax,ay,bx,by)||onSegment(dx,dy,ax,ay,bx,by)||
               onSegment(ax,ay,cx,cy,dx,dy)||onSegment(bx,by,cx,cy,dx,dy);
    }
    // Lightness carries portrait structure and must not be traded for a small
    // chroma improvement. A fixed 1.45 multiplier is a conservative rendering
    // heuristic, not a calibrated CIEDE2000 skin-tone model.
    private static final double LIGHTNESS_WEIGHT=1.45;
    static double error(double[] desired,double[] actual){
        double dl=desired[0]-actual[0],da=desired[1]-actual[1],db=desired[2]-actual[2];
        return LIGHTNESS_WEIGHT*dl*dl+da*da+db*db;
    }
    /** Immutable once constructed; one snapshot per generation, never a global
     * photo/palette cache. Manual RGB edits create a new palette automatically. */
    static final class PreparedPalette {
        final double[][] ok;
        final int count;
        PreparedPalette(int[] physicalColors,int count){
            if(physicalColors==null||count<2||count>8||count>physicalColors.length)
                throw new IllegalArgumentException("Invalid physical vinyl palette");
            this.count=count;
            ok=new double[count][];
            for(int layer=0;layer<count;layer++)
                ok[layer]=PerceptualVinylColor.lab(physicalColors[layer]);
        }
    }
    /**
     * The photo is sampled at its real coordinates.  Grouping samples by the
     * three shape-membership bits makes ordered-stack optimization inexpensive:
     * at most eight regions and 8^3 combinations, irrespective of image size.
     * Unselected slots are 0, which is reserved for the permanent foundation.
     */
    public static int[] choose(int[] photo,boolean[] allowed,int w,int h,
            float x0,float y0,float x1,float y1,float[][] marks,
            int[] physicalColors,int count,float ornamentation){
        // Retain the public API for external callers and regression fixtures.
        return choose(photo,allowed,w,h,x0,y0,x1,y1,marks,
            new PreparedPalette(physicalColors,count),ornamentation);
    }
    static int[] choose(int[] photo,boolean[] allowed,int w,int h,
            float x0,float y0,float x1,float y1,float[][] marks,
            PreparedPalette palette,float ornamentation){
        if(photo==null||w<1||h<1||(long)w*h!=photo.length||
           (allowed!=null&&allowed.length!=photo.length)||
           marks==null||marks.length!=3||palette==null||
           !Float.isFinite(x0)||!Float.isFinite(y0)||
           !Float.isFinite(x1)||!Float.isFinite(y1)||
           x0>=x1||y0>=y1||!Float.isFinite(ornamentation))
            throw new IllegalArgumentException("Invalid opaque overlay palette or photograph");
        final int count=palette.count;
        final double[][] rgb=palette.ok;
        double[][] sums=new double[8][3];int[] populations=new int[8];
        int samples=0;
        for(int sy=0;sy<SAMPLE_SIDE;sy++)for(int sx=0;sx<SAMPLE_SIDE;sx++){
            if(sx==0)CancellationCheckpoint.check();
            float x=x0+(sx+.5f)*(x1-x0)/SAMPLE_SIDE;
            float y=y0+(sy+.5f)*(y1-y0)/SAMPLE_SIDE;
            int idx=clamp((int)y,h)*w+clamp((int)x,w);
            int c=photo[idx];
            if((c>>>24)<128||(allowed!=null&&!allowed[idx]))continue;
            int mask=0;
            for(int k=0;k<3;k++)if(inside(marks[k],x,y))mask|=1<<k;
            double[] perceptual=PerceptualVinylColor.lab(c);
            sums[mask][0]+=perceptual[0];
            sums[mask][1]+=perceptual[1];
            sums[mask][2]+=perceptual[2];
            populations[mask]++;samples++;
        }
        // A fixed 7x7 cell grid cannot see a valid accent situated entirely
        // between its sample centers.  This is a physical-geometry blind spot,
        // not an absence of photo coverage: raster-scan the clipped bounds of
        // ONLY a mark with zero sampled occupancy and add one representative
        // valid pixel.  The normal, fast 49-point sampling remains unchanged.
        // The fallback examines actual photo pixel centers, Snip validity, and
        // alpha, so a wholly excluded mark cannot acquire a spurious sheet.
        for(int k=0;k<3;k++){
            boolean seen=false;
            for(int m=0;m<8;m++)if(populations[m]>0&&(m&(1<<k))!=0){seen=true;break;}
            if(seen)continue;
            float[] polygon=marks[k];
            if(polygon==null||polygon.length<6||(polygon.length&1)!=0)continue;
            float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY;
            float maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
            boolean valid=true;
            for(int v=0;v<polygon.length;v+=2){
                float px=polygon[v],py=polygon[v+1];
                if(!Float.isFinite(px)||!Float.isFinite(py)){valid=false;break;}
                minX=Math.min(minX,px);maxX=Math.max(maxX,px);
                minY=Math.min(minY,py);maxY=Math.max(maxY,py);
            }
            if(!valid)continue;
            int left=Math.max(0,(int)Math.ceil(Math.max(x0,minX)-.5));
            int right=Math.min(w-1,(int)Math.floor(Math.min(x1,maxX)-.5));
            int top=Math.max(0,(int)Math.ceil(Math.max(y0,minY)-.5));
            int bottom=Math.min(h-1,(int)Math.floor(Math.min(y1,maxY)-.5));
            boolean found=false;
            for(int py=top;py<=bottom&&!found;py++){
                if((py&15)==0)CancellationCheckpoint.check();
                for(int px=left;px<=right;px++){
                    int idx=py*w+px,color=photo[idx];
                    if((color>>>24)<128||(allowed!=null&&!allowed[idx]))continue;
                    float sx=px+.5f,sy=py+.5f;
                    if(!inside(polygon,sx,sy))continue;
                    int mask=0;
                    for(int j=0;j<3;j++)if(inside(marks[j],sx,sy))mask|=1<<j;
                    double[] perceptual=PerceptualVinylColor.lab(color);
                    sums[mask][0]+=perceptual[0];
                    sums[mask][1]+=perceptual[1];
                    sums[mask][2]+=perceptual[2];
                    populations[mask]++;samples++;found=true;break;
                }
            }
        }
        if(samples==0)return new int[3];
        for(int m=0;m<8;m++)if(populations[m]>0)
            for(int k=0;k<3;k++)sums[m][k]/=populations[m];
        // Empty marks and marks outside a thin Snip cannot acquire a colour.
        int[] limit=new int[3];
        for(int m=0;m<8;m++)if(populations[m]>0)
            for(int k=0;k<3;k++)if((m&(1<<k))!=0)limit[k]=count-1;
        // Per-shape weed/complexity cost prevents unnecessary upper vinyl
        // layers when the photograph is already reproduced by lower sheets.
        // Same units as OKLab squared error. An unused/fully covered cut must
        // earn a visible perceptual improvement before adding another sheet.
        double penalty=.00010+.00065*(1-Math.max(0,Math.min(1,ornamentation)));
        // A cell has at most eight shape-membership regions and eight physical
        // sheet colors. Compute their perceptual losses once rather than doing
        // three-channel math for every candidate triplet (up to 729 candidates).
        final double[][] regionCost=new double[8][count];
        final int[] occupied=new int[8];int occupiedCount=0;
        for(int m=0;m<8;m++)if(populations[m]>0){
            occupied[occupiedCount++]=m;
            double weight=populations[m]/(double)samples;
            for(int layer=0;layer<count;layer++)
                regionCost[m][layer]=weight*error(sums[m],rgb[layer]);
        }
        // Geometry remains fixed for each cell. Compute contacts once, not for
        // every palette combination (up to 729 candidate assignments).
        boolean ab=polygonsContact(marks[0],marks[1]);
        boolean ac=polygonsContact(marks[0],marks[2]);
        boolean bc=polygonsContact(marks[1],marks[2]);
        double best=Double.POSITIVE_INFINITY;int[] chosen={0,0,0};
        for(int a=0;a<=limit[0];a++)for(int b=0;b<=limit[1];b++){
            if(ab&&a!=0&&b==a)continue;
            for(int c=0;c<=limit[2];c++){
                if(c!=0&&((ac&&c==a)||(bc&&c==b)))continue;
                double score=penalty*((a!=0?1:0)+(b!=0?1:0)+(c!=0?1:0));
                for(int i=0;i<occupiedCount;i++){
                    int m=occupied[i];
                    int visible=(m&1)!=0?a:0;
                    if((m&2)!=0&&b>visible)visible=b;
                    if((m&4)!=0&&c>visible)visible=c;
                    score+=regionCost[m][visible];
                }
                if(score<best-1e-12){best=score;
                    chosen[0]=a;chosen[1]=b;chosen[2]=c;}
            }
        }
        return chosen;
    }

    /** Raster estimate from the ACTUAL clipped output polygons (not source
     * guesses). Calculate once on the render worker; the UI only reads it. */
    public static final class Visibility {
        public final int[] cutArea,visibleArea;
        public final int subjectArea;
        private Visibility(int[] cut,int[] visible,int total){
            cutArea=cut;visibleArea=visible;subjectArea=total;
        }
        public int visiblePercent(int layer){
            return subjectArea==0?0:Math.round(100f*visibleArea[layer]/subjectArea);
        }
        public int hiddenPercent(int layer){
            return cutArea[layer]==0?0:Math.round(100f*(cutArea[layer]-visibleArea[layer])/cutArea[layer]);
        }
    }
    public static Visibility visibility(LayeredCellPortraitEngine.Result result){
        if(result==null)throw new IllegalArgumentException("Missing cell result");
        // This is a UI estimate, never a source of cut geometry. Extremely tall
        // Snips must not allocate an unbounded grid on a memory-limited phone.
        final int sw=128,sh=Math.max(12,Math.min(512,
            (int)Math.round(128d*result.height/result.width)));
        final int[] owner=new int[sw*sh];Arrays.fill(owner,-1);
        final int[] covered=new int[result.options.layerCount];
        for(int layer=0;layer<covered.length;layer++){
            CancellationCheckpoint.check();
            for(LayeredCellPortraitEngine.Piece piece:result.pieces){
                if(piece.layer!=layer)continue;
                float[] p=piece.outer;
                float minX=Float.MAX_VALUE,minY=Float.MAX_VALUE,maxX=-Float.MAX_VALUE,maxY=-Float.MAX_VALUE;
                for(int i=0;i<p.length;i+=2){
                    minX=Math.min(minX,p[i]);maxX=Math.max(maxX,p[i]);
                    minY=Math.min(minY,p[i+1]);maxY=Math.max(maxY,p[i+1]);
                }
                int xa=Math.max(0,(int)Math.floor(minX*sw/result.width));
                int xb=Math.min(sw-1,(int)Math.ceil(maxX*sw/result.width));
                int ya=Math.max(0,(int)Math.floor(minY*sh/result.height));
                int yb=Math.min(sh-1,(int)Math.ceil(maxY*sh/result.height));
                for(int y=ya;y<=yb;y++)for(int x=xa;x<=xb;x++){
                    if((x&31)==0)CancellationCheckpoint.check();
                    int index=y*sw+x;
                    if(owner[index]==layer)continue;
                    float px=(x+.5f)*result.width/sw,py=(y+.5f)*result.height/sh;
                    if(piece.contains(px,py)){owner[index]=layer;covered[layer]++;}
                }
            }
        }
        int[] visible=new int[covered.length];int total=0;
        for(int current:owner)if(current>=0){visible[current]++;total++;}
        return new Visibility(covered,visible,total);
    }
}

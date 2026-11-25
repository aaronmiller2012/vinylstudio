package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Opaque-vinyl photographic cells: legacy sliced mode or detail-first stacked mode.
 * Pure Java, no Android APIs.
 * A cell is partitioned into a rectangular outer region and up to three nested motifs.
 * Adjacent same-color regions are merged BEFORE vector export, so their common boundary
 * is never cut. Each remaining piece is a filled polygon or a compound polygon with a hole.
 *
 * Stacked mode uses the Snip silhouette on sheet 1, cell overlays on middle
 * sheets, and photo-edge motifs on the final sheet. Legacy output is preserved.
 */
public final class LayeredCellPortraitEngine {
    public static final int AUTO=0, ELLIPSE=1, ROUNDED_SQUARE=2, DIAMOND=3, HEXAGON=4,
            OCTAGON=5, CAPSULE=6, SOFT_STAR=7, SCALLOP=8;
    /** Independent opaque-vinyl overprint geometry, not concentric/nested motifs. */
    public static final int OVERPRINT_NONE=0, OVERPRINT_ORBITS=1,
            OVERPRINT_LOZENGES=2, OVERPRINT_DIAMONDS=3, OVERPRINT_MIXED=4,
            OVERPRINT_PAINTERLY=5; // retired ID, never select or render
    public static String overprintName(int kind){
        switch(kind){
            case OVERPRINT_ORBITS:return "Overprint Orbits";
            case OVERPRINT_LOZENGES:return "Overprint Lozenge";
            case OVERPRINT_DIAMONDS:return "Overprint Diamonds";
            case OVERPRINT_MIXED:return "Overprint Mixed Marks";
            case OVERPRINT_PAINTERLY:return "Retired portrait style";
            default:return "Layered Cell Portrait";
        }
    }
    public static final int MAX_CELLS=1200;
    public static final int MAX_DETAIL_CELLS=2400; // enhanced mode only, per-layer paths remain bounded
    private static final int MAX_CUT_CONTOURS=4800; // headroom below Cricut's 5000-path limit
    private static final float[] MOTIF_SCALES={.72f,.46f,.22f};

    public static final class Options {
        public int columns=22, motifDepth=3, family=AUTO;
        /** -1 inherits global family. Selected shape applies where this color dominates the source cell. */
        public int[] layerFamilies;
        public float photoDetail=.65f; // edge-aware motif direction and photo-local fidelity
        public float ornamentation=.45f; // 0: fidelity first; 1: more nested color accents
        public int[] colors={0xff24252c,0xff71485e,0xffb36e89,0xffe3aa9e,0xfff1d6ba,0xff7695b1,0xff694b9d,0xffd87b45};
        public int layerCount=5;
        /** Opt-in; existing saved projects retain the original sliced geometry. */
        public boolean solidBase=false, detailMotifOverlay=false;
        /** Offset Cell only: alternate rows use a half-cell running-bond layout. */
        public boolean offsetRows=false;
        /** 0 is legacy; 1..4 are supported overprints; 5 is a retired saved-project tombstone. */
        public int overprintKind=OVERPRINT_NONE;
        /** Bottle Cap Mosaic only: close-packed, non-overlapping circular vinyl caps. */
        public boolean bottleCaps=false;
        /** Fish Scale Mosaic only; distinct from bottle caps and legacy cells. */
        public boolean fishScales=false;
        public float widthMm=152.4f, gapMm=.35f, seamGapMm=.12f, minFeatureMm=.50f, outerSpacingMm=8f;
        public int lockOuterToLayer=-1; // -1: pick from image, 0...N-1: fill cell background with that vinyl color
        public boolean[] allowedPixels; // source-aligned snip/crop mask; partial tiles are clipped
        public boolean registration=true;
        public int markStyle=RegistrationMarks.CROSS;
        public float markSizeMm=2.5f;
        public float registrationClearanceMm=1f,minimumCutMm=.8f;
        public float[][] marks=null;
        /** Circle crop: automatic marks sit in the unused corners, as the editor preview draws them (ADR-0001). */
        public boolean circleCrop=false;
        public Options copy(){Options a=new Options();a.columns=columns;a.motifDepth=motifDepth;a.family=family;
            a.solidBase=solidBase;a.detailMotifOverlay=detailMotifOverlay;a.offsetRows=offsetRows;a.overprintKind=overprintKind;a.bottleCaps=bottleCaps;a.fishScales=fishScales;
            a.colors=colors==null?null:colors.clone();a.layerFamilies=layerFamilies==null?null:layerFamilies.clone();a.layerCount=layerCount;a.ornamentation=ornamentation;a.photoDetail=photoDetail;a.widthMm=widthMm;a.gapMm=gapMm;
            a.seamGapMm=seamGapMm;a.minFeatureMm=minFeatureMm;a.outerSpacingMm=outerSpacingMm;a.lockOuterToLayer=lockOuterToLayer;
            a.allowedPixels=allowedPixels==null?null:allowedPixels.clone();a.registration=registration;
            a.markStyle=markStyle;a.markSizeMm=markSizeMm;
            a.registrationClearanceMm=registrationClearanceMm;a.minimumCutMm=minimumCutMm;
            a.marks=RegistrationMarks.copyPositions(marks);a.circleCrop=circleCrop;return a;}
    }

    public static final class Piece {
        public final int layer, cell;
        public final float[] outer, hole; // retain first-hole compatibility for preview callers
        public final List<float[]> holes; // supports multiple snip holes and disjoint clipped islands
        private Piece(int layer,int cell,float[] outer,float[] hole){this(layer,cell,outer,
            hole==null?Collections.emptyList():Collections.singletonList(hole));}
        private Piece(int layer,int cell,float[] outer,List<float[]> holes){
            this.layer=layer;this.cell=cell;this.outer=outer.clone();
            ArrayList<float[]> copy=new ArrayList<>();for(float[] h:holes)copy.add(h.clone());
            this.holes=Collections.unmodifiableList(copy);
            this.hole=this.holes.isEmpty()?null:this.holes.get(0);
        }
        public boolean contains(float x,float y){
            if(!inPoly(outer,x,y))return false;
            for(float[] h:holes)if(inPoly(h,x,y))return false;
            return true;
        }
        public int cutContours(){return 1+holes.size();}
    }

    public static final class Result {
        public final int width,height,columns,rows,skippedCells;
        public final Options options;
        public final List<Piece> pieces;
        public final int[] pieceCounts,contourCounts;
        public final OpaqueOverlaySwatchPlanner.Visibility swatchVisibility;
        private Result(int width,int height,int cols,int rows,int skips,Options options,List<Piece> pieces){
            this.width=width;this.height=height;this.columns=cols;this.rows=rows;this.skippedCells=skips;
            this.options=options.copy();this.pieces=Collections.unmodifiableList(new ArrayList<>(pieces));
            pieceCounts=new int[options.layerCount];contourCounts=new int[options.layerCount];
            for(Piece p:pieces){CancellationCheckpoint.check();pieceCounts[p.layer]++;contourCounts[p.layer]+=p.cutContours();}
            swatchVisibility=options.overprintKind>OVERPRINT_NONE&&options.overprintKind<=OVERPRINT_MIXED?
                OpaqueOverlaySwatchPlanner.visibility(this):null;
        }
        public int visibleLayers(){int n=0;for(int count:pieceCounts)if(count>0)n++;return n;}
        public String summary(){return (options.overprintKind!=OVERPRINT_NONE?overprintName(options.overprintKind)+": ":options.fishScales?"Fish Scale Mosaic Portrait: ":options.bottleCaps?"Bottle Cap Mosaic Portrait: ":options.offsetRows?"Offset Cell Portrait: ":"Layered Cell Portrait: ")+pieces.size()+" cut pieces / "+
            ((options.fishScales?(columns+2)*rows:columns*rows+(options.offsetRows?rows/2:0))-skippedCells)+" active cells, "+visibleLayers()+" populated vinyl layers, "+
            (options.overprintKind==OVERPRINT_PAINTERLY?"photo-fitted organic brushwork":
            options.overprintKind!=OVERPRINT_NONE?"independent overprinted vinyl marks":options.fishScales?"staggered scalloped scales":options.bottleCaps?"separate circular vinyl caps":options.solidBase?"stacked detail geometry":"sliced geometry")+"; maximum "+maxContours()+" contours per SVG including registration";}
        public int maxContours(){int m=0;for(int n:contourCounts)m=Math.max(m,n+(options.registration?4:0));return m;}
        /** ADR-0001 frame shared with the editor preview. */
        public SheetFrame frame(){float mm=options.widthMm/width;
            return SheetFrame.of(options.widthMm,height*mm,options.registration,options.markSizeMm,
                options.registrationClearanceMm,options.marks,options.outerSpacingMm,options.circleCrop);}
        public String svg(int layer){
            if(layer<0||layer>=options.layerCount)throw new IllegalArgumentException("Invalid layer");
            float mmPerPx=options.widthMm/width, heightMm=height*mmPerPx;
            SheetFrame reg=options.registration?frame():null;
            if(reg!=null){String unsafe=RegistrationCutGeometry.cells(this).firstIssue();
                if(unsafe!=null)throw new IllegalArgumentException(unsafe);}
            float pad=reg==null?options.outerSpacingMm:reg.paddingMm;
            float boardW=options.widthMm+2*pad,boardH=heightMm+2*pad;
            StringBuilder out=new StringBuilder(512+pieceCounts[layer]*160);
            out.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(f(boardW))
                .append("mm\" height=\"").append(f(boardH)).append("mm\" viewBox=\"0 0 ")
                .append(f(boardW)).append(' ').append(f(boardH)).append("\">\n<g fill=\"")
                .append(String.format(Locale.US,"#%06X",options.colors[layer]&0xffffff))
                .append("\" fill-rule=\"evenodd\" stroke=\"none\">\n");
            for(Piece p:pieces)if(p.layer==layer){
                out.append("<path d=\"");path(out,p.outer,mmPerPx,pad,false);
                for(float[] hole:p.holes){CancellationCheckpoint.check();out.append(' ');path(out,hole,mmPerPx,pad,true);}
                out.append("\"/>\n");
            }
            if(reg!=null)for(float[] xy:reg.marks())
                out.append(RegistrationMarks.svgMark(options.markStyle,pad+xy[0],pad+xy[1],options.markSizeMm,options.minimumCutMm));
            return out.append("</g>\n</svg>\n").toString();
        }
    }

    private static String f(float x){return SvgNumberWriter.append(new StringBuilder(12),x,3).toString();}
    private static void path(StringBuilder b,float[] xy,float scale,float offset,boolean reverse){
        int n=xy.length/2;
        for(int j=0;j<n;j++){if(((j)&63)==0)CancellationCheckpoint.check();
            int i=reverse?(n-j)%n:j;
            b.append(j==0?'M':'L');
            SvgNumberWriter.append(b,offset+xy[i*2]*scale,3).append(' ');
            SvgNumberWriter.append(b,offset+xy[i*2+1]*scale,3);
        }
        b.append('Z');
    }
    private static boolean inPoly(float[] poly,float x,float y){
        boolean odd=false;int len=poly.length/2;
        for(int i=0,j=len-1;i<len;j=i++){if(((i)&63)==0)CancellationCheckpoint.check();
            float xi=poly[i*2], yi=poly[i*2+1],xj=poly[j*2],yj=poly[j*2+1];
            if((yi>y)!=(yj>y)&&x<(xj-xi)*(y-yi)/(yj-yi)+xi)odd=!odd;
        }
        return odd;
    }
    private static float polygonArea(float[] p){double sum=0;for(int i=0;i<p.length;i+=2){if(((i)&63)==0)CancellationCheckpoint.check();int j=(i+2)%p.length;
        sum+=(double)p[i]*p[j+1]-(double)p[j]*p[i+1];}return (float)Math.abs(sum*.5);}
    private static int mix(int x){x^=x>>>16;x*=0x7feb352d;x^=x>>>15;x*=0x846ca68b;x^=x>>>16;return x;}
    private static int clamp(int x,int max){return Math.min(max-1,Math.max(0,x));}
    private static float rgbLinear(int byteChannel){float s=byteChannel/255f;
        return s<=.04045f?s/12.92f:(float)Math.pow((s+.055f)/1.055f,2.4);}
    private static float[] lin(int argb){return new float[]{rgbLinear((argb>>16)&255),rgbLinear((argb>>8)&255),rgbLinear(argb&255)};}
    private static float diff(float[] a,float[] b){float dr=a[0]-b[0],dg=a[1]-b[1],db=a[2]-b[2];
        return .2126f*dr*dr+.7152f*dg*dg+.0722f*db*db;}
    private static float luminance(int argb){return .2126f*((argb>>16)&255)+.7152f*((argb>>8)&255)+.0722f*(argb&255);}

    private static final class Gradient {
        float gx,gy,contrast;
    }
    private static boolean gradientAllowed(int[] pix,boolean[] allowed,int i){
        return (pix[i]>>>24)>=128&&(allowed==null||allowed[i]);
    }
    private static Gradient gradient(int[] pix,boolean[] allowed,int w,int h,
                                     float x0,float y0,float x1,float y1){
        Gradient g=new Gradient();double xx=0,yy=0,abs=0;int samples=0;
        for(int ry=1;ry<=5;ry++)for(int rx=1;rx<=5;rx++){
            int x=clamp((int)(x0+(x1-x0)*rx/6f),w),y=clamp((int)(y0+(y1-y0)*ry/6f),h);
            int center=y*w+x;
            if(!gradientAllowed(pix,allowed,center))continue;
            int left=y*w+clamp(x-1,w),right=y*w+clamp(x+1,w);
            int up=clamp(y-1,h)*w+x,down=clamp(y+1,h)*w+x;
            float mid=luminance(pix[center]);
            float dx=gradientAllowed(pix,allowed,left)?
                (gradientAllowed(pix,allowed,right)?
                    luminance(pix[right])-luminance(pix[left]):mid-luminance(pix[left])):
                (gradientAllowed(pix,allowed,right)?luminance(pix[right])-mid:0f);
            float dy=gradientAllowed(pix,allowed,up)?
                (gradientAllowed(pix,allowed,down)?
                    luminance(pix[down])-luminance(pix[up]):mid-luminance(pix[up])):
                (gradientAllowed(pix,allowed,down)?luminance(pix[down])-mid:0f);
            xx+=dx;yy+=dy;abs+=Math.sqrt(dx*dx+dy*dy);samples++;
        }
        if(samples>0){g.gx=(float)(xx/samples);g.gy=(float)(yy/samples);
            g.contrast=(float)(abs/samples);}
        return g;
    }
    private static int family(Options o,int col,int row,Gradient g){
        if(o.family!=AUTO)return o.family;
        if(g.contrast>12f)return CAPSULE; // follows visible edges rather than arbitrary tile hash
        int[] selections={ELLIPSE,ROUNDED_SQUARE,DIAMOND,HEXAGON,OCTAGON,SOFT_STAR,SCALLOP};
        return selections[Math.floorMod(mix(col*73856093^row*19349663),selections.length)];
    }
    // Trigonometric motif templates depend only on shape family. Preserve
    // double precision until rotation and the original float normalization.
    // No angle quantization or palette/photo dependence. Lazy per-family
    // construction avoids retaining thousands of per-cell rotated polygons.
    private static final double[][] CELL_TEMPLATES=new double[SCALLOP+1][];
    private static double[] cellTemplate(int family){
        synchronized(CELL_TEMPLATES){
            double[] hit=CELL_TEMPLATES[family];if(hit!=null)return hit;
            int n=family==DIAMOND?4:family==HEXAGON?6:family==OCTAGON?8:
                    family==ELLIPSE?40:family==CAPSULE?48:family==SOFT_STAR?60:family==SCALLOP?64:32;
            double[] out=new double[2*n];
            for(int i=0;i<n;i++){
                double theta=(i*2*Math.PI)/n-Math.PI/2;
                double c=Math.cos(theta),si=Math.sin(theta);
                if(family==ROUNDED_SQUARE){c=Math.copySign(Math.pow(Math.abs(c),.53),c);
                    si=Math.copySign(Math.pow(Math.abs(si),.53),si);}
                if(family==CAPSULE)si*=.62;
                if(family==SOFT_STAR){double r=.77+.23*Math.cos(5*(theta+Math.PI/2));c*=r;si*=r;}
                if(family==SCALLOP){double r=.88+.12*Math.cos(8*(theta+Math.PI/2));c*=r;si*=r;}
                out[2*i]=c;out[2*i+1]=si;
            }
            CELL_TEMPLATES[family]=out;return out;
        }
    }
    static float[] normalizedShape(int family,double rotation){
        double[] template=cellTemplate(family);double cos=Math.cos(rotation),sin=Math.sin(rotation);
        float[] pts=new float[template.length];double maxX=0,maxY=0;
        for(int i=0;i<pts.length;i+=2){if((i&127)==0)CancellationCheckpoint.check();
            double c=template[i],si=template[i+1];
            double x=c*cos-si*sin,y=c*sin+si*cos;
            pts[i]=(float)x;pts[i+1]=(float)y;
            maxX=Math.max(maxX,Math.abs(x));maxY=Math.max(maxY,Math.abs(y));
        }
        double max=Math.max(maxX,maxY);
        for(int i=0;i<pts.length;i+=2){if((i&63)==0)CancellationCheckpoint.check();pts[i]/=max;pts[i+1]/=max;}
        return pts;
    }
    private static float[] scaledShape(float[] norm,float cx,float cy,float halfWidth,float halfHeight,float motifScale){
        float[] out=new float[norm.length];for(int i=0;i<out.length;i+=2){if(((i)&63)==0)CancellationCheckpoint.check();
            out[i]=cx+norm[i]*halfWidth*motifScale;
            out[i+1]=cy+norm[i+1]*halfHeight*motifScale;
        }return out;
    }
    private static int maskCoverage(int[] pix,boolean[] mask,int w,int h,float x0,float y0,float x1,float y1){
        // 0=empty, 1=partial, 2=full; only partially masked cells need raster contour clipping.
        int left=Math.max(0,(int)Math.floor(x0)),right=Math.min(w,(int)Math.ceil(x1));
        int top=Math.max(0,(int)Math.floor(y0)),bottom=Math.min(h,(int)Math.ceil(y1));
        int allowed=0,total=0;
        for(int y=top;y<bottom;y++)for(int x=left;x<right;x++){if(((x)&63)==0)CancellationCheckpoint.check();
            if(x+.5f<x0||x+.5f>=x1||y+.5f<y0||y+.5f>=y1)continue;
            int idx=y*w+x;total++;
            if((pix[idx]>>>24)>=128&&(mask==null||mask[idx]))allowed++;
        }
        return allowed==0?0:allowed==total?2:1;
    }
    private static float[][] zoneSamples(int[] src,boolean[] mask,int w,int h,float x0,float y0,float x1,float y1,float[][] contours){
        int zones=contours.length+1;
        float[][] sum=new float[zones][3];int[] counts=new int[zones];float[] patch={0,0,0};
        int sampleN=11,all=0;
        for(int iy=0;iy<sampleN;iy++)for(int ix=0;ix<sampleN;ix++){if(((ix)&63)==0)CancellationCheckpoint.check();
            float x=x0+(ix+.5f)/sampleN*(x1-x0),y=y0+(iy+.5f)/sampleN*(y1-y0);
            int z=0;for(int j=0;j<contours.length;j++)if(inPoly(contours[j],x,y))z=j+1;
            int idx=clamp((int)y,h)*w+clamp((int)x,w);
            if((src[idx]>>>24)<128||(mask!=null&&!mask[idx]))continue;
            int c=src[idx];float[] rgb=lin(c);
            for(int k=0;k<3;k++){if(((k)&63)==0)CancellationCheckpoint.check();sum[z][k]+=rgb[k];patch[k]+=rgb[k];}
            counts[z]++;all++;
        }
        if(all==0){
            int left=clamp((int)Math.floor(x0),w),right=clamp((int)Math.ceil(x1)-1,w);
            int top=clamp((int)Math.floor(y0),h),bottom=clamp((int)Math.ceil(y1)-1,h);
            for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                int idx=y*w+x;
                if((src[idx]>>>24)<128||(mask!=null&&!mask[idx]))continue;
                float[] rgb=lin(src[idx]);for(int k=0;k<3;k++)patch[k]+=rgb[k];
                all++;
            }
            if(all==0)throw new IllegalArgumentException("Cell contains no source samples");
        }
        for(int i=0;i<3;i++)patch[i]/=all;
        for(int z=0;z<zones;z++)for(int k=0;k<3;k++)sum[z][k]=counts[z]>0?sum[z][k]/counts[z]:patch[k];
        return sum;
    }
    private static final class Search {
        final int[] best,work;
        final float[][] target,palette;
        final float[] weight,mean;
        final int n,palCount,locked;
        final boolean ordered;
        final float transitionPenalty,photoDetail;
        final float[][] localCost;
        double bestScore=Double.POSITIVE_INFINITY;
        Search(float[][] target,float[] weight,float[][] palette,int locked,float transitionPenalty,float photoDetail){
            this(target,weight,palette,locked,transitionPenalty,photoDetail,false);
        }
        Search(float[][] target,float[] weight,float[][] palette,int locked,float transitionPenalty,float photoDetail,boolean ordered){
            this.ordered=ordered;
            this.target=target;this.weight=weight;this.palette=palette;this.locked=locked;this.transitionPenalty=transitionPenalty;this.photoDetail=photoDetail;
            n=target.length;palCount=palette.length;mean=new float[3];best=new int[n];work=new int[n];
            localCost=new float[n][palCount];
            for(int i=0;i<n;i++)for(int k=0;k<3;k++)mean[k]+=target[i][k]*weight[i];
            double[][] targetLab=new double[n][],vinylLab=new double[palCount][];
            for(int i=0;i<n;i++)targetLab[i]=linearOklab(target[i]);
            for(int k=0;k<palCount;k++)vinylLab[k]=linearOklab(palette[k]);
            for(int i=0;i<n;i++)for(int k=0;k<palCount;k++){
                double dl=targetLab[i][0]-vinylLab[k][0],
                    da=targetLab[i][1]-vinylLab[k][1],db=targetLab[i][2]-vinylLab[k][2];
                double perceived=1.70*dl*dl+1.12*(da*da+db*db);
                localCost[i][k]=weight[i]*(.70f*diff(target[i],palette[k])+.30f*(float)perceived);
            }
        }
        void visit(int index,double zoneCost,float r,float g,float b,int transitions){
            if(index==n){
                float[] mix={r,g,b};
                double localWeight=.20+.52*photoDetail;
                double score=localWeight*zoneCost+(1-localWeight)*diff(mean,mix)+transitionPenalty*transitions;
                if(score<bestScore-1e-9){bestScore=score;System.arraycopy(work,0,best,0,n);}
                return;
            }
            int start=index==0&&locked>=0?locked:0,end=index==0&&locked>=0?locked+1:palCount;
            if(ordered&&index>0)start=Math.max(start,work[index-1]);
            for(int color=start;color<end;color++){if(((color)&63)==0)CancellationCheckpoint.check();
                // Duplicate material colors do not justify another physical mark.
                if(ordered&&index>0&&color!=work[index-1]&&Arrays.equals(palette[color],palette[work[index-1]]))continue;
                work[index]=color;
                float share=weight[index];
                visit(index+1,zoneCost+localCost[index][color],
                    r+share*palette[color][0],g+share*palette[color][1],b+share*palette[color][2],
                    transitions+(index>0&&work[index-1]!=color?1:0));
            }
        }
    }

    /** Oklab from already-linear source samples (no double gamma encoding). */
    private static double[] linearOklab(float[] c){
        double r=c[0],g=c[1],b=c[2];
        double l=Math.cbrt(.4122214708*r+.5363325363*g+.0514459929*b);
        double m=Math.cbrt(.2119034982*r+.6806995451*g+.1073969566*b);
        double ss=Math.cbrt(.0883024619*r+.2817188376*g+.6299787005*b);
        return new double[]{.2104542553*l+.7936177850*m-.0040720468*ss,
            1.9779984951*l-2.4285922050*m+.4505937099*ss,
            .0259040371*l+.7827717662*m-.8086757660*ss};
    }

    private static float pointSegmentDist(float px,float py,float ax,float ay,float bx,float by){
        float dx=bx-ax,dy=by-ay,denom=dx*dx+dy*dy;
        float t=denom==0?0:Math.max(0,Math.min(1,((px-ax)*dx+(py-ay)*dy)/denom));
        return (float)Math.hypot(px-ax-t*dx,py-ay-t*dy);
    }
    private static float minBoundaryDistance(float[] a,float[] b){
        float best=Float.POSITIVE_INFINITY;
        for(int i=0;i<a.length;i+=2){if(((i)&63)==0)CancellationCheckpoint.check();int j=(i+2)%a.length;
            for(int k=0;k<b.length;k+=2){if(((k)&63)==0)CancellationCheckpoint.check();int l=(k+2)%b.length;
                best=Math.min(best,pointSegmentDist(a[i],a[i+1],b[k],b[k+1],b[l],b[l+1]));
                best=Math.min(best,pointSegmentDist(a[j],a[j+1],b[k],b[k+1],b[l],b[l+1]));
                best=Math.min(best,pointSegmentDist(b[k],b[k+1],a[i],a[i+1],a[j],a[j+1]));
                best=Math.min(best,pointSegmentDist(b[l],b[l+1],a[i],a[i+1],a[j],a[j+1]));
            }
        }
        return best;
    }
    private static void mergeUnsafeRings(int[] color,float[][] targets,float[][] palette,float[] norm,
            float cx,float cy,float x0,float y0,float x1,float y1,int depth,float minPx,float seamPx){
        float[] rect={x0,y0,x1,y0,x1,y1,x0,y1};
        float hw=(x1-x0)*.5f,hh=(y1-y0)*.5f;
        for(int pass=0;pass<depth+2;pass++){if(((pass)&63)==0)CancellationCheckpoint.check();
            boolean merged=false;
            int start=0;
            while(start<=depth){
                int end=start+1;while(end<=depth&&color[end]==color[start])end++;
                float[] outer=start==0?rect:scaledShape(norm,cx,cy,hw,hh,
                    MOTIF_SCALES[start-1]-seamPx/(2*Math.min(hw,hh)));
                float[] inner=end<=depth?scaledShape(norm,cx,cy,hw,hh,
                    MOTIF_SCALES[end-1]+seamPx/(2*Math.min(hw,hh))):null;
                if(inner!=null&&minBoundaryDistance(outer,inner)+.0001f<minPx){
                    int neighbor=end; // merge inner into its outer neighbor, removing the hole contour
                    if(start==0&&end==depth+1)break;
                    if(neighbor<=depth)for(int k=neighbor;k<=depth&&color[k]==color[neighbor];k++)color[k]=color[start];
                    merged=true;break;
                }
                start=end;
            }
            if(!merged)break;
        }
    }

    private static boolean validFamilies(int[] a,int n){for(int i=0;i<n;i++)if(a[i]<-1||a[i]>SCALLOP)return false;return true;}
    private static int closestPhotoColor(int[] src,int w,int h,float x,float y,float[][] colors){
        int px=src[Math.max(0,Math.min(h-1,Math.round(y)))*w+Math.max(0,Math.min(w-1,Math.round(x)))];
        float[] rgba=lin(px);double best=Double.POSITIVE_INFINITY;int result=0;
        for(int i=0;i<colors.length;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            double score=0;for(int k=0;k<3;k++){if(((k)&63)==0)CancellationCheckpoint.check();double d=rgba[k]-colors[i][k];score+=d*d;}
            if(score<best){best=score;result=i;}
        }return result;
    }
    /** Trace a bounded-resolution Snip silhouette into actual cut contours, not
     * SVG clipPaths. The silhouette belongs to L1 and remains one coherent
     * sheet regardless of how many colored cells overlay it. */
    private static void addSolidSilhouette(int[] source,int w,int h,Options o,List<Piece> out){
        int stride=Math.max(1,(int)Math.ceil(Math.max(w,h)/640f));
        int sw=(w+stride-1)/stride,sh=(h+stride-1)/stride;
        int[] reduced=new int[sw*sh];boolean[] mask=new boolean[sw*sh];
        for(int y=0;y<sh;y++){if(((y)&63)==0)CancellationCheckpoint.check();
            if((y&31)==0&&Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException("Silhouette interrupted");
            for(int x=0;x<sw;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                int sx=Math.min(w-1,x*stride+stride/2),sy=Math.min(h-1,y*stride+stride/2);
                int idx=sy*w+sx;
                reduced[y*sw+x]=source[idx];
                mask[y*sw+x]=(source[idx]>>>24)>=128 &&
                    (o.allowedPixels==null||o.allowedPixels[idx]);
            }
        }
        float[] rect={0f,0f,sw,0f,sw,sh,0f,sh};
        List<RasterRegionClipper.Region> traced=RasterRegionClipper.clip(rect,null,reduced,mask,
            sw,sh,0,0,sw,sh,0f,0f,sw,sh,o.widthMm/sw,o.minFeatureMm);
        float sx=w/(float)sw,sy=h/(float)sh;
        for(RasterRegionClipper.Region region:traced){CancellationCheckpoint.check();
            if(Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException("Silhouette interrupted");
            ArrayList<float[]> holes=new ArrayList<>();
            for(float[] hole:region.holes)holes.add(scaleLoop(hole,sx,sy));
            out.add(new Piece(0,-1,scaleLoop(region.outer,sx,sy),holes));
        }
        if(out.isEmpty())throw new IllegalArgumentException("No solid subject silhouette after Snip");
    }
    private static float[] scaleLoop(float[] points,float xScale,float yScale){
        float[] out=points.clone();
        for(int i=0;i<out.length;i+=2){if(((i)&63)==0)CancellationCheckpoint.check();out[i]*=xScale;out[i+1]*=yScale;}
        return out;
    }
    /** One additional, cuttable upper-sheet motif. It is bounded to its cell
     * and never overlaps another motif on the same sheet. */
    private static void addFeatureMotif(int[] src,boolean[] mask,int w,int h,Options o,
                List<Piece> pieces,int layer,int cell,int family,float angle,
                float x0,float y0,float x1,float y1,float priority,float edge){
        float importance=Math.max(priority,Math.min(1f,edge/24f)*.78f);
        if(importance<.26f)return;
        float cx=(x0+x1)*.5f,cy=(y0+y1)*.5f;
        float width=x1-x0,height=y1-y0;
        float scale=.34f+.13f*Math.min(1f,importance);
        if(Math.min(width,height)*scale*o.widthMm/w<o.minFeatureMm*1.6f)return;
        // Sparse alternating anchors retain the portrait's local edge information
        // without generating a second full-screen background pattern.
        float[] norm=normalizedShape(family==AUTO?ELLIPSE:family,angle);
        float[] motif=scaledShape(norm,cx,cy,width*.5f,height*.5f,scale);
        int coverage=maskCoverage(src,mask,w,h,x0,y0,x1,y1);
        if(coverage==2){pieces.add(new Piece(layer,cell,motif,(float[])null));return;}
        List<RasterRegionClipper.Region> clipped=RasterRegionClipper.clip(motif,null,src,mask,w,h,
            Math.max(0,(int)Math.floor(x0)),Math.max(0,(int)Math.floor(y0)),
            Math.min(w,(int)Math.ceil(x1)),Math.min(h,(int)Math.ceil(y1)),
            x0,y0,x1,y1,o.widthMm/w,o.minFeatureMm);
        for(RasterRegionClipper.Region part:clipped)
            pieces.add(new Piece(layer,cell,part.outer,part.holes));
    }
    public static Result generate(int[] source,int w,int h,Options input){
        if(source==null||w<12||h<12||(long)w*h!=source.length)throw new IllegalArgumentException("Invalid source image");
        if(input==null)throw new IllegalArgumentException("Missing options");
        Options o=input.copy();
        if(o.layerCount<2||o.layerCount>8||o.colors==null||o.colors.length<o.layerCount||
           o.columns<4||o.columns>80||o.motifDepth<1||o.motifDepth>3||o.family<AUTO||o.family>SCALLOP||o.overprintKind<OVERPRINT_NONE||o.overprintKind>OVERPRINT_MIXED||
           (o.layerFamilies!=null&&(o.layerFamilies.length<o.layerCount||!validFamilies(o.layerFamilies,o.layerCount)))||
           !Float.isFinite(o.photoDetail)||o.photoDetail<0||o.photoDetail>1||
           o.lockOuterToLayer<-1||o.lockOuterToLayer>=o.layerCount||
           !Float.isFinite(o.widthMm)||o.widthMm<25||o.widthMm>1200||
           // The user-defined material gap may be 20 mm. The render path
           // adds 0.06 mm rounding clearance; rejecting at 2 / 0.5 mm
           // previously made default Layered/Offset/Cap/Scale styles fail.
           !Float.isFinite(o.gapMm)||o.gapMm<0||o.gapMm>20.1f||
           !Float.isFinite(o.ornamentation)||o.ornamentation<0||o.ornamentation>1||
           !Float.isFinite(o.seamGapMm)||o.seamGapMm<0||o.seamGapMm>20.1f||
           !Float.isFinite(o.minFeatureMm)||o.minFeatureMm<.2f||o.minFeatureMm>20.1f||
           !Float.isFinite(o.outerSpacingMm)||o.outerSpacingMm<0||o.outerSpacingMm>60||
           (o.allowedPixels!=null&&o.allowedPixels.length!=source.length)||
           (o.registration&&(!RegistrationMarks.validStyle(o.markStyle)||!Float.isFinite(o.markSizeMm)||o.markSizeMm<2||o.markSizeMm>12)))
            throw new IllegalArgumentException("Invalid vinyl geometry options");
        if(o.overprintKind!=OVERPRINT_NONE)return generateOverprint(source,w,h,o);
        if(o.fishScales)return generateFishScales(source,w,h,o);
        if(o.bottleCaps)return generateBottleCaps(source,w,h,o);
        int cols=o.columns,rows=Math.max(1,Math.round(cols*h/(float)w));
        int maxCells=o.solidBase?MAX_DETAIL_CELLS:MAX_CELLS;
        if((long)cols*rows+(o.offsetRows?rows/2:0)>maxCells)throw new IllegalArgumentException("Reduce cell detail to "+maxCells+" or fewer tiles");
        float scale=o.widthMm/w,xCell=w/(float)cols,yCell=h/(float)rows;
        ScaleAwarePhotoSampler photo=new ScaleAwarePhotoSampler(source,w,h,o.widthMm,h*scale,0xffffffff);
        float gapPx=o.gapMm/scale;
        float minTileMm=Math.min(xCell,yCell)*scale-o.gapMm;
        if(minTileMm<=o.minFeatureMm*3)throw new IllegalArgumentException("Cells too small for vinyl; lower detail or raise artwork size");
        float[][] palette=new float[o.layerCount][];for(int k=0;k<o.layerCount;k++)palette[k]=lin(o.colors[k]);
        PhotoSwatchAssociation familyMatcher=new PhotoSwatchAssociation(
            o.colors,o.layerCount,null,source,o.allowedPixels,PhotoSwatchAssociation.REGIONS);
        ArrayList<Piece> pieces=new ArrayList<>();int skipped=0;
        if(o.solidBase)addSolidSilhouette(source,w,h,o,pieces);
        boolean accent=o.detailMotifOverlay&&o.layerCount>=4;
        final boolean opaqueMotifs=o.solidBase&&!o.detailMotifOverlay;
        final int accentLayer=accent?o.layerCount-1:-1;
        final float[][] workingPalette=accent?Arrays.copyOf(palette,o.layerCount-1):palette;
        for(int row=0;row<rows;row++)for(int col=(o.offsetRows&&(row&1)==1?-1:0);col<cols;col++){if(((col)&63)==0)CancellationCheckpoint.check();
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Generation interrupted");
            float shift=(o.offsetRows&&(row&1)==1)?xCell*.5f:0f;
            float rawX0=col*xCell+shift,rawX1=(col+1)*xCell+shift;
            float x0=Math.max(0f,rawX0+gapPx*.5f),x1=Math.min(w,rawX1-gapPx*.5f);
            float y0=row*yCell+gapPx*.5f,y1=(row+1)*yCell-gapPx*.5f;
            if(x1<=x0){skipped++;continue;}
            int coverage=maskCoverage(source,o.allowedPixels,w,h,x0,y0,x1,y1);
            if(coverage==0){skipped++;continue;}
            float[] rect={x0,y0,x1,y0,x1,y1,x0,y1};
            float cx=(x0+x1)*.5f,cy=(y0+y1)*.5f;
            // One scale-aware region signal drives motif orientation and local
            // detail without adding new UI controls or changing color ownership.
            ScaleAwarePhotoSampler.RegionSignal region=photo.region(
                (o.offsetRows?x0:col*xCell)*scale,row*yCell*scale,
                (o.offsetRows?x1:(col+1)*xCell)*scale,(row+1)*yCell*scale);
            Gradient edge=new Gradient();edge.gx=region.gradientX;
            edge.gy=region.gradientY;edge.contrast=region.contrast;
            int family=family(o,col,row,edge);
            if(o.layerFamilies!=null){
                int dominant=familyMatcher.choose(source[Math.min(h-1,Math.max(0,(int)cy))*w+Math.min(w-1,Math.max(0,(int)cx))]);
                if(o.layerFamilies[dominant]>=AUTO)family=o.layerFamilies[dominant];
            }
            double angle=(family==CAPSULE||family==DIAMOND)?Math.atan2(edge.gy,edge.gx)+Math.PI/2:0;
            if(edge.contrast<6||o.photoDetail==0)angle=0;
            float[] normalized=normalizedShape(family,angle);
            float[][] shapes=new float[o.motifDepth][];
            int depth=0;
            for(int i=0;i<o.motifDepth;i++){if(((i)&63)==0)CancellationCheckpoint.check();
                float s=MOTIF_SCALES[i];
                float previous=depth==0?1:MOTIF_SCALES[depth-1];
                if(opaqueMotifs){
                    // Solid marks on separate sheets need no thin weedable ring.
                    s-=.5f*o.seamGapMm/minTileMm;
                    if(s*minTileMm<2.4f*o.minFeatureMm)break;
                }else{
                    if((previous-s)*minTileMm*.5f<o.minFeatureMm+o.seamGapMm)break;
                    if(s*minTileMm<2*o.minFeatureMm+o.seamGapMm)break;
                }
                shapes[depth++]=scaledShape(normalized,cx,cy,(x1-x0)*.5f,(y1-y0)*.5f,s);
            }
            float[][] boundaries=Arrays.copyOf(shapes,depth);
            int regions=depth+1;
            float[] weight=new float[regions];float oldArea=(x1-x0)*(y1-y0);
            for(int i=0;i<depth;i++){if(((i)&63)==0)CancellationCheckpoint.check();
                float a=polygonArea(boundaries[i]);weight[i]=(oldArea-a)/((x1-x0)*(y1-y0));oldArea=a;
            }
            weight[regions-1]=oldArea/((x1-x0)*(y1-y0));
            float[][] samples=zoneSamples(source,o.allowedPixels,w,h,x0,y0,x1,y1,boundaries);
            int cell=o.offsetRows?row*(cols+1)+col+1:row*cols+col;
            // A bounded artistic reward encourages Closeify-style motifs even in
            // mostly flat regions, while the photo-reconstruction objective remains.
            // A per-cell hash varies density without nondeterministic randomness.
            float patternShare=(Math.floorMod(mix(cell*0x9e3779b9),1000))/999f;
            float penalty=.001f-o.ornamentation*(.005f+.006f*patternShare)*(1f-.85f*o.photoDetail);
            float cellMmX=cx*scale,cellMmY=cy*scale;
            int centerArgb=source[Math.min(h-1,Math.max(0,(int)cy))*w+
                Math.min(w-1,Math.max(0,(int)cx))];
            float priority=RegionalPortraitDetail.priority(cellMmX,cellMmY,o.widthMm,h*scale,
                Math.min(1f,edge.contrast/28f),Math.max(0f,1f-region.light),(centerArgb>>>24)&255);
            // An automatic local budget: retain the original motif family and
            // color palette, but favor fine zone matching in supported subject areas.
            float localDetail=Math.min(1f,o.photoDetail*(1f+.18f*priority));
            int locked=o.lockOuterToLayer<workingPalette.length?o.lockOuterToLayer:-1;
            if(opaqueMotifs){
                float reward=o.ornamentation*(.007f+.009f*patternShare);
                Search stacked=new Search(samples,weight,workingPalette,locked,-reward,
                    .50f+.50f*localDetail,true);
                stacked.visit(0,0,0,0,0,0);
                int prior=-1;
                for(int z=0;z<regions;z++){
                    int layer=stacked.best[z];
                    if(layer==prior)continue;
                    prior=layer;
                    if(layer==0)continue;
                    float[] shape=z==0?rect:boundaries[z-1];
                    if(coverage==2)pieces.add(new Piece(layer,cell,shape,(float[])null));
                    else for(RasterRegionClipper.Region part:RasterRegionClipper.clip(shape,null,source,
                        o.allowedPixels,w,h,Math.max(0,(int)Math.floor(x0)),Math.max(0,(int)Math.floor(y0)),
                        Math.min(w,(int)Math.ceil(x1)),Math.min(h,(int)Math.ceil(y1)),
                        x0,y0,x1,y1,scale,o.minFeatureMm))pieces.add(new Piece(layer,cell,part.outer,part.holes));
                }
                continue;
            }
            Search search=new Search(samples,weight,workingPalette,locked,penalty,
                localDetail*(o.solidBase?Math.max(.40f,Math.min(1f,edge.contrast/18f)):
                    Math.min(1f,edge.contrast/18f)));
            search.visit(0,0,0,0,0,0);
            int[] colors=search.best;
            // Suppress contours that would create a ring narrower than the physical
            // minimum. Merging colors removes that cut boundary rather than just
            // deleting a sliver and leaving an unintended void.
            mergeUnsafeRings(colors,samples,palette,normalized,cx,cy,x0,y0,x1,y1,
                depth,o.minFeatureMm/scale,o.seamGapMm/scale);
            for(int start=0;start<regions;){if(((start)&63)==0)CancellationCheckpoint.check();
                int end=start+1;while(end<regions&&colors[end]==colors[start])end++;
                // Give different vinyl colors real paper-negative-space clearance.
                // The same boundary is contracted on one side and expanded on the
                // other; merged same-color regions retain no internal seam.
                float epsilon=o.seamGapMm/minTileMm;
                float[] outer=start==0?rect:scaledShape(normalized,cx,cy,(x1-x0)*.5f,(y1-y0)*.5f,
                    MOTIF_SCALES[start-1]-epsilon);
                float[] hole=end<regions?scaledShape(normalized,cx,cy,(x1-x0)*.5f,(y1-y0)*.5f,
                    MOTIF_SCALES[end-1]+epsilon):null;
                if(o.solidBase&&colors[start]==0){start=end;continue;} // Already present in L1: no duplicate cut
                if(coverage==2){pieces.add(new Piece(colors[start],cell,outer,hole));}
                else {
                    List<RasterRegionClipper.Region> clipped=RasterRegionClipper.clip(outer,hole,source,
                        o.allowedPixels,w,h,Math.max(0,(int)Math.floor(o.offsetRows?x0:col*xCell)),
                        Math.max(0,(int)Math.floor(row*yCell)),
                        Math.min(w,(int)Math.ceil(o.offsetRows?x1:(col+1)*xCell)),
                        Math.min(h,(int)Math.ceil((row+1)*yCell)),
                        x0,y0,x1,y1,scale,o.minFeatureMm);
                    for(RasterRegionClipper.Region part:clipped)
                        pieces.add(new Piece(colors[start],cell,part.outer,part.holes));
                }
                start=end;
            }
            if(accent){
                addFeatureMotif(source,o.allowedPixels,w,h,o,pieces,accentLayer,cell,
                    family,(float)angle,x0,y0,x1,y1,priority,edge.contrast);
            }
        }
        Result result=new Result(w,h,cols,rows,skipped,o,pieces);
        if(pieces.isEmpty())throw new IllegalArgumentException("No visible cells after crop/snip");
        for(int k=0;k<o.layerCount;k++)if(result.contourCounts[k]+(o.registration?4:0)>MAX_CUT_CONTOURS)
            throw new IllegalArgumentException("Layer "+(k+1)+" exceeds conservative SVG contour budget");
        return result;
    }

    /**
     * True staggered scallop lattice. An upper tile's lower curve is the exact
     * upper boundary of the row below. This is what makes the pattern read as
     * connected fish scales instead of isolated horizontal capsules.
     *
     * The cutter receives inset polygons: horizontal lanes are separated by
     * gapPx and matching shared curves are separated vertically by gapPx.
     * Thus different scales never overlap on a single physical vinyl sheet.
     */
    private static Result generateFishScales(int[] src,int w,int h,Options o){
        final float mmPx=o.widthMm/w;
        final float pitch=w/(float)o.columns;
        final float rowPitch=pitch*.69f;
        final int rows=(int)Math.ceil(h/rowPitch)+3;
        if((long)(o.columns+2)*rows>MAX_DETAIL_CELLS)
            throw new IllegalArgumentException("Too many fish scales; reduce density");
        final float gapPx=o.gapMm/mmPx;
        final float minFeaturePx=o.minFeatureMm/mmPx;
        // The narrow side of each scale must remain weeding-safe even after
        // subtracting the gap from its two shared boundaries.
        if((pitch-gapPx)*mmPx<o.minFeatureMm*2.5f ||
           (rowPitch-gapPx)*mmPx<o.minFeatureMm*1.8f)
            throw new IllegalArgumentException("Fish scales too small for safe cutting");
        final float wave=Math.min(rowPitch*.76f,
            rowPitch-gapPx-minFeaturePx*1.10f);
        if(wave<=rowPitch*.12f)
            throw new IllegalArgumentException("Not enough height for scalloped cut geometry");
        final boolean accent=o.detailMotifOverlay&&o.layerCount>=3;
        final int accentLayer=accent?o.layerCount-1:-1;
        final int first=o.solidBase?1:0;
        final int end=accent?o.layerCount-1:o.layerCount;
        if(first>=end)throw new IllegalArgumentException("Not enough vinyl layers for fish scales");
        float[][] pal=new float[end-first][];
        for(int i=first;i<end;i++)pal[i-first]=lin(o.colors[i]);
        PhotoSwatchAssociation fishMatcher=new PhotoSwatchAssociation(
            Arrays.copyOfRange(o.colors,first,end),end-first,null,src,o.allowedPixels,
            PhotoSwatchAssociation.SHAPES);
        ArrayList<Piece> pieces=new ArrayList<>();
        if(o.solidBase)addSolidSilhouette(src,w,h,o,pieces);
        int skipped=0;
        final float accentThreshold=accent?fishAccentThreshold(src,o.allowedPixels):0f;
        // Include one row above the artwork so no flat cropped starter row is
        // visible. The row boundary always matches its preceding neighbor.
        for(int row=-1;row<rows-1;row++){if(((row)&63)==0)CancellationCheckpoint.check();
            if(Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException("Fish scale render cancelled");
            float shift=((row&1)!=0)?pitch*.5f:0f;
            float prevShift=(((row-1)&1)!=0)?pitch*.5f:0f;
            float lowerBound=(row+1)*rowPitch+wave;
            float upperBound=row*rowPitch;
            if(lowerBound<0||upperBound>h)continue;
            for(int col=-1;col<=o.columns;col++){if(((col)&63)==0)CancellationCheckpoint.check();
                float left=col*pitch+shift;
                float right=left+pitch;
                if(right<=0||left>=w){skipped++;continue;}
                float x0=Math.max(0,left),x1=Math.min(w,right);
                if(x1-x0<1f){skipped++;continue;}
                float[] scale=fishScaleShape(left,right,row,rowPitch,pitch,
                    shift,prevShift,wave,gapPx);
                if(scale==null){skipped++;continue;}
                // A scale can cross a nominal row boundary; use its full actual
                // bounds for photo sampling and Snip clipping.
                float y0=h,y1=0;
                for(int si=1;si<scale.length;si+=2){if(((si)&63)==0)CancellationCheckpoint.check();
                    y0=Math.min(y0,scale[si]);y1=Math.max(y1,scale[si]);
                }
                y0=Math.max(0,y0);y1=Math.min(h,y1);
                if(y1-y0<1f||maskCoverage(src,o.allowedPixels,w,h,x0,y0,x1,y1)==0){
                    skipped++;continue;
                }
                int average=averageFishColor(src,o.allowedPixels,w,h,scale,x0,y0,x1,y1,o.photoDetail);
                if(average==Integer.MIN_VALUE){skipped++;continue;}
                int selected=first+fishMatcher.choose(average);
                // +1 reserves one positive cell index for all columns; row+1
                // keeps leading clipping rows distinct from ordinary pieces.
                int cell=(row+1)*(o.columns+2)+col+1;
                int bodyStart=pieces.size();
                addFishScalePart(src,w,h,o,mmPx,pieces,selected,cell,scale);
                int r=(average>>16)&255,g=(average>>8)&255,blue=average&255;
                float sourceLuma=.2126f*r+.7152f*g+.0722f*blue;
                if(accent&&pieces.size()>bodyStart&&sourceLuma>=accentThreshold){
                    int bodyEnd=pieces.size();
                    for(int bi=bodyStart;bi<bodyEnd;bi++){
                        float[] glint=fishBodyAccent(pieces.get(bi),minFeaturePx,pitch);
                        if(glint!=null){pieces.add(new Piece(accentLayer,cell,glint,(float[])null));break;}
                    }
                }
            }
        }
        Result result=new Result(w,h,o.columns,rows,skipped,o,pieces);
        if(result.pieces.isEmpty())throw new IllegalArgumentException("No fish scales inside the Snip");
        for(int i=0;i<o.layerCount;i++)
            if(result.contourCounts[i]+(o.registration?4:0)>MAX_CUT_CONTOURS)
                throw new IllegalArgumentException("Fish scale layer exceeds SVG path budget");
        return result;
    }

    /** A continuous half-scallop wave. The power gently rounds the zero-height
     * junction so the tip is never a mathematical needle for the cutter. */
    private static float fishWave(float x,float origin,float pitch){
        float u=(x-origin)/pitch;
        u-= (float)Math.floor(u);
        return (float)Math.pow(Math.max(0d,Math.sin(Math.PI*u)),1.16d);
    }

    /** The lower curve matches the adjacent row's top curve exactly. Each
     * polyline is inset by half a weed gap on both sides of that boundary. */
    /** Sampled boundary offset in the unit normal direction. The original
     * vertical y-offset let high-slope portions fall substantially below the
     * requested millimetre weed gap. Keep an extra safety allowance for the
     * polygonal approximation of curves sampled in neighbouring rows.
     * Callers pass a signed offset: positive is toward increasing y. */
    private static void fishNormalPoint(float[] out,int index,float x,float baseY,
            float origin,float pitch,float amplitude,float offset){
        final float delta=Math.max(.001f,pitch*.002f);
        float slope=amplitude*(fishWave(x+delta,origin,pitch)-
            fishWave(x-delta,origin,pitch))/(2f*delta);
        float unit=(float)(1.0/Math.sqrt(1.0+slope*slope));
        out[index]=x-offset*slope*unit;
        out[index+1]=baseY+amplitude*fishWave(x,origin,pitch)+offset*unit;
    }

    private static float[] fishScaleShape(float left,float right,int row,
            float rowPitch,float pitch,float shift,float prevShift,float amplitude,float gap){
        final int n=32;
        // Keep side clearance between neighbouring scales in the same row.
        final float side=gap*1.75f;
        final float x0=left+side,x1=right-side;
        if(x1<=x0)return null;
        final float half=gap*.60f; // >= requested gap after curve sampling
        // Each edge is the exact envelope of all points within `half` of the
        // shared row boundary (a function of x). Offsetting along the normal
        // instead folded into a self-crossing loop at every arch cusp.
        float[] top=new float[n+1],bottom=new float[n+1];
        for(int i=0;i<=n;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            float x=x0+(x1-x0)*i/n;
            top[i]=fishEnvelope(x,row*rowPitch,prevShift,pitch,amplitude,half);
            bottom[i]=fishEnvelope(x,(row+1)*rowPitch,shift,pitch,amplitude,-half);
        }
        // Keep only the longest run where the scale has real height; where
        // the two envelopes meet, the edges would touch or cross.
        final float eps=Math.max(.05f,gap*.25f);
        int bestStart=-1,bestLen=0;
        for(int i=0;i<=n;){
            if(bottom[i]-top[i]<=eps){i++;continue;}
            int j=i;while(j<=n&&bottom[j]-top[j]>eps)j++;
            if(j-i>bestLen){bestLen=j-i;bestStart=i;}
            i=j;
        }
        if(bestLen<3)return null;
        float[] xy=new float[4*bestLen];int k=0;
        for(int i=bestStart;i<bestStart+bestLen;i++){
            xy[k++]=x0+(x1-x0)*i/n;xy[k++]=top[i];
        }
        for(int i=bestStart+bestLen-1;i>=bestStart;i--){
            xy[k++]=x0+(x1-x0)*i/n;xy[k++]=bottom[i];
        }
        return xy;
    }
    /** y of the envelope at signed distance d from the wave boundary
     * (d>0: below it, d<0: above it), as the max/min over a disk sweep. */
    private static float fishEnvelope(float x,float baseY,float origin,float pitch,float amplitude,float d){
        final int steps=12;float r=Math.abs(d);
        float best=d>0?Float.NEGATIVE_INFINITY:Float.POSITIVE_INFINITY;
        for(int s=-steps;s<=steps;s++){
            float t=r*s/steps;
            float rise=(float)Math.sqrt(Math.max(0f,r*r-t*t));
            float y=baseY+amplitude*fishWave(x+t,origin,pitch);
            if(d>0)best=Math.max(best,y+rise);else best=Math.min(best,y-rise);
        }
        return best;
    }

    private static float fishAccentThreshold(int[] src,boolean[] allowed){
        int[] hist=new int[256];int count=0;
        for(int i=0;i<src.length;i++){
            if((i&8191)==0)CancellationCheckpoint.check();
            int p=src[i];if((p>>>24)<128||(allowed!=null&&!allowed[i]))continue;
            int l=(int)(.2126f*((p>>16)&255)+.7152f*((p>>8)&255)+.0722f*(p&255));
            hist[StyleKit.clampI(l,0,255)]++;count++;
        }
        if(count==0)return 95f;
        int target=Math.max(1,(int)(count*.55f)),seen=0;
        for(int l=0;l<hist.length;l++){seen+=hist[l];if(seen>=target)return Math.max(80f,Math.min(95f,l));}
        return 95f;
    }

    /** Fit a solid raised accent inside the actual clipped scale body. */
    private static float[] fishBodyAccent(Piece body,float minPx,float pitch){
        float loX=Float.POSITIVE_INFINITY,hiX=Float.NEGATIVE_INFINITY,loY=loX,hiY=hiX;
        for(int i=0;i<body.outer.length;i+=2){loX=Math.min(loX,body.outer[i]);hiX=Math.max(hiX,body.outer[i]);
            loY=Math.min(loY,body.outer[i+1]);hiY=Math.max(hiY,body.outer[i+1]);}
        float thickness=Math.max(minPx*1.28f,pitch*.085f);
        float[] norm=normalizedShape(CAPSULE,0);
        for(float length:new float[]{pitch*.36f,pitch*.27f,minPx*2.0f}){
            if(length<thickness*1.3f)continue;
            for(float yf:new float[]{.67f,.58f,.76f,.49f,.40f})for(float xf:new float[]{.5f,.42f,.58f}){
                CancellationCheckpoint.check();
                float cx=loX+(hiX-loX)*xf,cy=loY+(hiY-loY)*yf;
                float[] mark=scaledShape(norm,cx,cy,length*.5f,thickness*.5f,1f);
                boolean ok=true;
                for(int j=0;j<mark.length;j+=2){if(!body.contains(mark[j],mark[j+1])){ok=false;break;}}
                if(ok&&PhysicalContourClearance.minDistance(mark,body.outer)>=minPx*.14f){
                    for(float[] hole:body.holes)if(PhysicalContourClearance.minDistance(mark,hole)<minPx*.14f||inPoly(mark,hole[0],hole[1])){ok=false;break;}
                    if(ok)return mark;
                }
            }
        }
        return null;
    }

    /** Clip to real canvas bounds before raster Snip clipping. The raster
     * clipper expands edge-aligned contours by up to a pixel, which can cause
     * adjacent subpixel-spaced scales to overlap along the outer image edge. */
    private static float[] fishClipCanvas(float[] polygon,int w,int h){
        float[] pts=polygon;
        for(int side=0;side<4;side++){if(((side)&63)==0)CancellationCheckpoint.check();
            if(pts.length<6)return null;
            float[] next=new float[pts.length*2+8];int n=0;
            int count=pts.length/2;
            float bx=pts[pts.length-2],by=pts[pts.length-1];
            float bval=fishBoundary(bx,by,side,w,h);
            for(int i=0;i<count;i++){if(((i)&63)==0)CancellationCheckpoint.check();
                float ax=pts[2*i],ay=pts[2*i+1];
                float aval=fishBoundary(ax,ay,side,w,h);
                if((aval>=0)!=(bval>=0)){
                    float t=bval/(bval-aval);
                    next[n++]=bx+t*(ax-bx);next[n++]=by+t*(ay-by);
                }
                if(aval>=0){next[n++]=ax;next[n++]=ay;}
                bx=ax;by=ay;bval=aval;
            }
            pts=Arrays.copyOf(next,n);
        }
        return pts.length>=6?pts:null;
    }
    private static float fishBoundary(float x,float y,int side,int w,int h){
        switch(side){
            case 0:return x;
            case 1:return w-x;
            case 2:return y;
            default:return h-y;
        }
    }
    private static void addFishScalePart(int[] src,int w,int h,Options o,float mmPx,
            List<Piece> pieces,int layer,int cell,float[] polygon){
        float[] clipped=fishClipCanvas(polygon,w,h);
        if(clipped==null)return;
        float x0=w,y0=h,x1=0,y1=0;
        for(int j=0;j<clipped.length;j+=2){if(((j)&63)==0)CancellationCheckpoint.check();
            x0=Math.min(x0,clipped[j]);x1=Math.max(x1,clipped[j]);
            y0=Math.min(y0,clipped[j+1]);y1=Math.max(y1,clipped[j+1]);
        }
        if(x1-x0<.05f||y1-y0<.05f)return;
        int coverage=maskCoverage(src,o.allowedPixels,w,h,x0,y0,x1,y1);
        if(coverage==0)return;
        if(coverage==2){pieces.add(new Piece(layer,cell,clipped,(float[])null));return;}
        List<RasterRegionClipper.Region> regions=RasterRegionClipper.clipFishScale(clipped,null,src,o.allowedPixels,
            w,h,Math.max(0,(int)Math.floor(x0)),Math.max(0,(int)Math.floor(y0)),
            Math.min(w,(int)Math.ceil(x1)),Math.min(h,(int)Math.ceil(y1)),
            x0,y0,x1,y1,mmPx,o.minFeatureMm);
        for(RasterRegionClipper.Region part:regions){CancellationCheckpoint.check();
            // The raster mask expands a curved shape to whole-pixel boundaries.
            // At subpixel weed gaps, two separate pieces can otherwise share
            // an identical pixel edge and produce duplicate cut paths.
            // Inset BOTH adjacent clipped pieces by >gap/2, measured in mm.
            float[] outer=part.outer;
            if(StyleKit.signedArea(outer)<0)outer=StyleKit.reversed(outer);
            outer=StyleKit.offsetLeft(outer,Math.max(o.gapMm/(mmPx)*.60f,.8f),2.5f);
            if(outer==null||StyleKit.selfIntersections(outer,1)>0)continue;
            float loX=Float.POSITIVE_INFINITY,hiX=Float.NEGATIVE_INFINITY;
            float loY=Float.POSITIVE_INFINITY,hiY=Float.NEGATIVE_INFINITY;
            for(int j=0;j<outer.length;j+=2){if(((j)&63)==0)CancellationCheckpoint.check();
                loX=Math.min(loX,outer[j]);hiX=Math.max(hiX,outer[j]);
                loY=Math.min(loY,outer[j+1]);hiY=Math.max(hiY,outer[j+1]);
            }
            if(Math.min(hiX-loX,hiY-loY)*mmPx<o.minFeatureMm)continue;
            // Holes are handled conservatively: if a partial clipped part has
            // holes, retain the original raster contour rather than invent an
            // incorrect hole inset. The export validator will reject any
            // remaining unsafe spacing.
            if(!part.holes.isEmpty()){
                pieces.add(new Piece(layer,cell,part.outer,part.holes));
            }else pieces.add(new Piece(layer,cell,outer,(float[])null));
        }
    }

    /** Sample within the actual scale footprint rather than its enclosing
     * rectangle. Respect partial Snip/alpha regions and linear-light color. */
    private static int averageFishColor(int[] src,boolean[] allowed,int w,int h,
            float[] shape,float x0,float y0,float x1,float y1,float detail){
        int n=detail>=.85f?17:11;
        float[] total=new float[3];int count=0;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){if(((x)&63)==0)CancellationCheckpoint.check();
            float px=x0+(x+.5f)*(x1-x0)/n;
            float py=y0+(y+.5f)*(y1-y0)/n;
            if(px<0||px>=w||py<0||py>=h||!inPoly(shape,px,py))continue;
            int ix=(int)px,iy=(int)py,idx=iy*w+ix,c=src[idx];
            if((c>>>24)<128||(allowed!=null&&!allowed[idx]))continue;
            total[0]+=rgbLinear((c>>16)&255);
            total[1]+=rgbLinear((c>>8)&255);
            total[2]+=rgbLinear(c&255);count++;
        }
        if(count==0){
            // The regular 11x11/17x17 grid can miss a thin but real Snip
            // fragment. Scan valid source pixels in the actual polygon instead
            // of silently deleting that visible fragment.
            int sx=Math.max(0,(int)Math.floor(x0)),ex=Math.min(w,(int)Math.ceil(x1));
            int sy=Math.max(0,(int)Math.floor(y0)),ey=Math.min(h,(int)Math.ceil(y1));
            for(int yy=sy;yy<ey;yy++){if(((yy)&63)==0)CancellationCheckpoint.check();
                if((yy&31)==0&&Thread.currentThread().isInterrupted())
                    throw new java.util.concurrent.CancellationException("Fish source sampling cancelled");
                for(int xx=sx;xx<ex;xx++){if(((xx)&63)==0)CancellationCheckpoint.check();
                    if(!inPoly(shape,xx+.5f,yy+.5f))continue;
                    int idx=yy*w+xx,c=src[idx];
                    if((c>>>24)<128||(allowed!=null&&!allowed[idx]))continue;
                    total[0]+=rgbLinear((c>>16)&255);
                    total[1]+=rgbLinear((c>>8)&255);
                    total[2]+=rgbLinear(c&255);count++;
                }
            }
        }
        if(count==0)return Integer.MIN_VALUE;
        return 0xff000000|(linearByte(total[0]/count)<<16)|
            (linearByte(total[1]/count)<<8)|linearByte(total[2]/count);
    }

    private static Result generateBottleCaps(int[] src,int w,int h,Options o){
        final float mmPx=o.widthMm/w;
        final float xPitch=w/(float)o.columns;
        final float yPitch=xPitch*.8660254f;
        final int rows=(int)Math.ceil(h/yPitch)+1;
        if((long)o.columns*rows+rows/2>MAX_DETAIL_CELLS)
            throw new IllegalArgumentException("Too many bottle caps; reduce density");
        final float gapPx=o.gapMm/mmPx;
        final float neighbor=(float)Math.hypot(xPitch*.5f,yPitch);
        final float diameter=Math.min(xPitch,neighbor)-gapPx;
        if(diameter*mmPx<o.minFeatureMm*2.25f)
            throw new IllegalArgumentException("Caps too small for vinyl; reduce density or enlarge the artwork");
        final float radius=diameter*.5f;
        final boolean rim=o.detailMotifOverlay&&o.layerCount>=3;
        final int rimLayer=rim?o.layerCount-1:-1;
        final int colorStart=o.solidBase?1:0;
        final int colorEnd=rim?o.layerCount-1:o.layerCount;
        if(colorStart>=colorEnd)throw new IllegalArgumentException("Not enough colored vinyl sheets");
        float[][] pal=new float[colorEnd-colorStart][];
        for(int i=colorStart;i<colorEnd;i++)pal[i-colorStart]=lin(o.colors[i]);
        PhotoSwatchAssociation capMatcher=new PhotoSwatchAssociation(
            Arrays.copyOfRange(o.colors,colorStart,colorEnd),colorEnd-colorStart,null,
            src,o.allowedPixels,PhotoSwatchAssociation.SHAPES);
        ArrayList<Piece> pieces=new ArrayList<>();
        if(o.solidBase)addSolidSilhouette(src,w,h,o,pieces);
        int skipped=0;
        for(int row=0;row<rows;row++){if(((row)&63)==0)CancellationCheckpoint.check();
            if(Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException("Bottle cap render interrupted");
            float cy=row*yPitch+radius;
            float offset=(row&1)==0?0:xPitch*.5f;
            for(int col=-1;col<=o.columns;col++){if(((col)&63)==0)CancellationCheckpoint.check();
                float cx=col*xPitch+radius+offset;
                if(cx+radius<=0||cy+radius<=0||cx-radius>=w||cy-radius>=h){skipped++;continue;}
                float x0=Math.max(0,cx-radius),y0=Math.max(0,cy-radius);
                float x1=Math.min(w,cx+radius),y1=Math.min(h,cy+radius);
                if(maskCoverage(src,o.allowedPixels,w,h,x0,y0,x1,y1)==0){skipped++;continue;}
                int avg=averageCapColor(src,o.allowedPixels,w,h,cx,cy,radius,o.photoDetail);
                if(avg==Integer.MIN_VALUE){skipped++;continue;}
                int best=colorStart+capMatcher.choose(avg);
                int cell=row*(o.columns+2)+col+1;
                float[] circle=circleCap(cx,cy,radius,48);
                addCapPart(src,w,h,o,mmPx,pieces,best,cell,circle,x0,y0,x1,y1,new float[]{cx,cy,radius});
                // A rim cut on the cap's own sheet would become a hole a hair
                // inside that cap, leaving a sub-minimum ring: skip it there.
                if(rim&&best!=rimLayer&&radius*mmPx>=o.minFeatureMm*2.6f){
                    float[] arc=capRim(cx,cy,radius,o.minFeatureMm/mmPx);
                    if(arc!=null)addCapPart(src,w,h,o,mmPx,pieces,rimLayer,cell,arc,x0,y0,x1,y1);
                }
            }
        }
        Result result=new Result(w,h,o.columns,rows,skipped,o,pieces);
        if(result.pieces.isEmpty())throw new IllegalArgumentException("No caps inside subject mask");
        for(int k=0;k<o.layerCount;k++)
            if(result.contourCounts[k]+(o.registration?4:0)>MAX_CUT_CONTOURS)
                throw new IllegalArgumentException("Bottle cap sheet exceeds safe SVG contour budget");
        return result;
    }

    /** Sample over a circular footprint in linear-light RGB, respecting Snip. */
    private static int averageCapColor(int[] image,boolean[] allowed,int w,int h,
                                       float cx,float cy,float radius,float photoDetail){
        // Use 13x13 or 19x19 samples: bounded per-cap work on a phone.
        int sampleN=photoDetail>=.85f?19:13;
        float sampleRadius=radius*(photoDetail>=.85f?.85f:1f);
        float[] total=new float[3];int count=0;
        for(int yy=0;yy<sampleN;yy++)for(int xx=0;xx<sampleN;xx++){if(((xx)&63)==0)CancellationCheckpoint.check();
            float u=2f*(xx+.5f)/sampleN-1f,v=2f*(yy+.5f)/sampleN-1f;
            if(u*u+v*v>1f)continue;
            int x=(int)(cx+u*sampleRadius),y=(int)(cy+v*sampleRadius);
            if(x<0||x>=w||y<0||y>=h)continue;
            int idx=y*w+x,c=image[idx];
            if((c>>>24)<128||(allowed!=null&&!allowed[idx]))continue;
            total[0]+=rgbLinear((c>>16)&255);
            total[1]+=rgbLinear((c>>8)&255);
            total[2]+=rgbLinear(c&255);
            count++;
        }
        if(count==0)return Integer.MIN_VALUE;
        int r=linearByte(total[0]/count),g=linearByte(total[1]/count),b=linearByte(total[2]/count);
        return 0xff000000|(r<<16)|(g<<8)|b;
    }
    private static int linearByte(float linear){
        float x=Math.max(0f,Math.min(1f,linear));
        float s=x<=.0031308f?12.92f*x:1.055f*(float)Math.pow(x,1/2.4f)-.055f;
        return Math.max(0,Math.min(255,Math.round(s*255f)));
    }
    private static float[] circleCap(float cx,float cy,float radius,int n){
        float[] shape=new float[n*2];
        for(int i=0;i<n;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            double a=2*Math.PI*i/n;
            shape[i*2]=cx+radius*(float)Math.cos(a);
            shape[i*2+1]=cy+radius*(float)Math.sin(a);
        }
        return shape;
    }
    /** A closed, minimum-width partial rim, placed on a separate vinyl sheet. */
    private static float[] capRim(float cx,float cy,float r,float minPx){
        float thick=Math.max(minPx,r*.13f);
        float outer=r*.85f,inner=outer-thick;
        if(inner<=r*.32f)return null;
        int n=18;float[] shape=new float[(n+1)*4];
        for(int i=0;i<=n;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            double a=Math.toRadians(195f+115f*i/n);
            shape[2*i]=cx+outer*(float)Math.cos(a);
            shape[2*i+1]=cy+outer*(float)Math.sin(a);
        }
        for(int i=0;i<=n;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            double a=Math.toRadians(310f-115f*i/n);
            int index=n+1+i;
            shape[2*index]=cx+inner*(float)Math.cos(a);
            shape[2*index+1]=cy+inner*(float)Math.sin(a);
        }
        return shape;
    }
    private static void addCapPart(int[] source,int w,int h,Options o,float mmPx,List<Piece> pieces,
                                   int layer,int cell,float[] poly,float x0,float y0,float x1,float y1){
        addCapPart(source,w,h,o,mmPx,pieces,layer,cell,poly,x0,y0,x1,y1,null);
    }
    /** {@code circle} = {cx,cy,r} for a full cap: if it must be raster-clipped
     * at the Snip/canvas edge, shrink it first so the whole-pixel clip cannot
     * close the weed gap to its neighbours. */
    private static void addCapPart(int[] source,int w,int h,Options o,float mmPx,List<Piece> pieces,
                                   int layer,int cell,float[] poly,float x0,float y0,float x1,float y1,float[] circle){
        int coverage=maskCoverage(source,o.allowedPixels,w,h,x0,y0,x1,y1);
        boolean inBounds=true;
        for(int i=0;i<poly.length;i+=2)
            if(poly[i]<0f||poly[i]>=w||poly[i+1]<0f||poly[i+1]>=h){inBounds=false;break;}
        if(coverage==2&&inBounds){pieces.add(new Piece(layer,cell,poly,(float[])null));return;}
        if(circle!=null){
            float pull=Math.max(o.gapMm/mmPx*.5f,1f);
            if(circle[2]-pull<=1f)return;
            poly=circleCap(circle[0],circle[1],circle[2]-pull,48);
        }
        List<RasterRegionClipper.Region> regions=RasterRegionClipper.clip(poly,null,source,o.allowedPixels,
                w,h,Math.max(0,(int)Math.floor(x0)),Math.max(0,(int)Math.floor(y0)),
                Math.min(w,(int)Math.ceil(x1)),Math.min(h,(int)Math.ceil(y1)),
                x0,y0,x1,y1,mmPx,o.minFeatureMm);
        for(RasterRegionClipper.Region part:regions)
            pieces.add(new Piece(layer,cell,part.outer,part.holes));
    }


    /**
     * Portrait cell overprints: each color sheet receives independent, CLOSED solid
     * pieces. No sheet contains a nested ring, knock-out, or duplicate cut within
     * the same cell. Real overlaps exist only between distinct physical sheets.
     * Each mark stays inside its own tile, maintaining configured same-sheet gap.
     */
    private static Result generateOverprint(int[] source,int w,int h,Options o){
        o.solidBase=true; // permanent silhouette sheet, even for old/corrupt settings
        o.detailMotifOverlay=false;
        int cols=o.columns,rows=Math.max(1,Math.round(cols*h/(float)w));
        if((long)cols*rows>MAX_DETAIL_CELLS)
            throw new IllegalArgumentException("Reduce overprint cells to "+MAX_DETAIL_CELLS+" or fewer tiles");
        float mmPx=o.widthMm/w,cellW=w/(float)cols,cellH=h/(float)rows;
        // Extra 0.025 mm guards cut/export coordinate rounding and raster clipping.
        float gapPx=(o.gapMm+.025f)/mmPx;
        float usableMm=Math.min(cellW,cellH)*mmPx-o.gapMm;
        if(usableMm<=o.minFeatureMm*3f)
            throw new IllegalArgumentException("Overprint cells too small for vinyl; lower density or increase artwork size");
        ArrayList<Piece> pieces=new ArrayList<>();
        addSolidSilhouette(source,w,h,o,pieces);
        // Prepare user-edited physical swatches just once for all cells. A new
        // render always creates a fresh immutable snapshot, so no stale colors.
        OpaqueOverlaySwatchPlanner.PreparedPalette physicalPalette=
            new OpaqueOverlaySwatchPlanner.PreparedPalette(o.colors,o.layerCount);
        int skipped=0;
        for(int row=0;row<rows;row++)for(int col=0;col<cols;col++){
            CancellationCheckpoint.check();
            float x0=col*cellW+gapPx*.5f,x1=Math.min(w,(col+1)*cellW-gapPx*.5f);
            float y0=row*cellH+gapPx*.5f,y1=Math.min(h,(row+1)*cellH-gapPx*.5f);
            if(x1<=x0||y1<=y0){skipped++;continue;}
            int cellMaskCoverage=maskCoverage(source,o.allowedPixels,w,h,x0,y0,x1,y1);
            if(cellMaskCoverage==0){skipped++;continue;}
            float[] mean=overprintMean(source,o.allowedPixels,w,h,x0,y0,x1,y1);
            if(mean==null){skipped++;continue;}
            Gradient edge=gradient(source,o.allowedPixels,w,h,x0,y0,x1,y1);
            // Blend the cell average with its actual centre tone. As the user
            // increases photo detail, edges and local values gain influence.
            int centerX=clamp((int)((x0+x1)*.5f),w),centerY=clamp((int)((y0+y1)*.5f),h);
            int centerIndex=centerY*w+centerX;
            if((source[centerIndex]>>>24)>=128&&
                (o.allowedPixels==null||o.allowedPixels[centerIndex])){
                float[] center=lin(source[centerIndex]);
                float influence=.10f+.44f*o.photoDetail;
                for(int k=0;k<3;k++)mean[k]=mean[k]*(1f-influence)+center[k]*influence;
            }
            int cell=row*cols+col;
            float angle=(float)Math.atan2(edge.gy,edge.gx)+(float)Math.PI*.5f;
            if(edge.contrast<2f||o.photoDetail<.05f)
                angle=(Math.floorMod(mix(cell*397),7)-3)*.12f;
            int hash=mix(cell*0x9e3779b9+o.overprintKind*7193);
            float overlap=Math.max(0f,Math.min(1f,o.ornamentation));
            float shift=.17f-.085f*overlap;
            float cx=(x0+x1)*.5f,cy=(y0+y1)*.5f;
            float dx=((hash&1)==0?1f:-1f)*shift*(x1-x0);
            float dy=((hash&2)==0?1f:-1f)*shift*(y1-y0);
            float major=.72f+.10f*overlap,minor=.61f+.13f*overlap;
            // The shapes are established independently of the palette. Assign
            // swatches only after evaluating their actual OPAQUE stack order;
            // nearest-colour-per-mark selects several lookalike swatches and
            // ignores how upper sheets cover the first one.
            float[][] marks={
                overprintMarkPolygon(o,o.overprintKind,cell,0,angle,cx-dx,cy-dy,
                    x0,y0,x1,y1,major,mmPx),
                o.motifDepth>=2&&o.layerCount>=3?
                    overprintMarkPolygon(o,o.overprintKind,cell,1,angle,cx+dx,cy+dy,
                        x0,y0,x1,y1,minor,mmPx):null,
                o.motifDepth>=3&&o.layerCount>=4&&
                    (edge.contrast>=6f||o.ornamentation>.72f)?
                    overprintMarkPolygon(o,o.overprintKind,cell,2,angle,
                        cx+(dy>0?-.08f:.08f)*(x1-x0),
                        cy+(dx>0?.08f:-.08f)*(y1-y0),
                        x0,y0,x1,y1,.47f,mmPx):null
            };
            int[] selected=OpaqueOverlaySwatchPlanner.choose(source,o.allowedPixels,w,h,
                x0,y0,x1,y1,marks,physicalPalette,o.ornamentation);
            for(int k=0;k<3;k++)if(selected[k]>0&&marks[k]!=null)
                addOverprintMark(source,w,h,o,pieces,selected[k],cell,marks[k],
                    x0,y0,x1,y1,mmPx,cellMaskCoverage);
        }
        Result result=new Result(w,h,cols,rows,skipped,o,pieces);
        if(result.maxContours()>MAX_CUT_CONTOURS)
            throw new IllegalArgumentException("Overprint contour budget exceeded; reduce cell density");
        return result;
    }
    /**
     * The painterly style cannot use the ordinary Overprint generator as its
     * colour field: that generator always draws two/three similarly-sized marks
     * in every cell, drowning pupils, lips and shadow boundaries in confetti.
     * Here colour and coverage are measured independently for every tile.
     * All geometry remains in the ordinary cut-safe per-cell boundary.
     */
    private static Result generatePainterlyCells(int[] source,int w,int h,Options o){
        final int cols=o.columns,rows=Math.max(1,Math.round(cols*h/(float)w));
        if((long)cols*rows>MAX_DETAIL_CELLS)
            throw new IllegalArgumentException("Reduce painterly cells to "+MAX_DETAIL_CELLS+" or fewer tiles");
        final float mmPx=o.widthMm/w,cellW=w/(float)cols,cellH=h/(float)rows;
        final float gapPx=(o.gapMm+.025f)/mmPx;
        if(Math.min(cellW,cellH)*mmPx-o.gapMm<=o.minFeatureMm*3f)
            throw new IllegalArgumentException("Painterly cells too small for vinyl; reduce density");
        float[][] palette=new float[o.layerCount][];
        for(int i=0;i<o.layerCount;i++)palette[i]=lin(o.colors[i]);
        final float baseTone=luminance(o.colors[0]);
        ArrayList<Piece> pieces=new ArrayList<>();
        addSolidSilhouette(source,w,h,o,pieces);
        int skipped=0;
        for(int row=0;row<rows;row++)for(int col=0;col<cols;col++){
            CancellationCheckpoint.check();
            float x0=col*cellW+gapPx*.5f,x1=Math.min(w,(col+1)*cellW-gapPx*.5f);
            float y0=row*cellH+gapPx*.5f,y1=Math.min(h,(row+1)*cellH-gapPx*.5f);
            if(x0>=x1||y0>=y1||maskCoverage(source,o.allowedPixels,w,h,x0,y0,x1,y1)==0){
                skipped++;continue;
            }
            float[] mean=overprintMean(source,o.allowedPixels,w,h,x0,y0,x1,y1);
            PainterlyTileTone tone=painterlyTileTone(source,o.allowedPixels,w,h,x0,y0,x1,y1);
            if(mean==null||tone==null){skipped++;continue;}
            int first=overprintNearest(mean,palette,-1,-1);
            int cell=row*cols+col;
            Gradient direction=gradient(source,o.allowedPixels,w,h,x0,y0,x1,y1);
            float angle=(float)Math.atan2(direction.gy,direction.gx)+(float)Math.PI*.5f;
            if(direction.contrast<3f)angle=(Math.floorMod(mix(cell*397),9)-4)*.11f;
            int hash=mix(cell*0x9e3779b9+OVERPRINT_PAINTERLY*7193);
            // Coverage follows the *target tone* relative to the opaque backing,
            // not a fixed confetti size. Light skin can form broad connected
            // optical areas; dark beard remains patterned but not speckled white.
            float spread=Math.max(-1f,Math.min(1f,(tone.mean-baseTone)/75f));
            float firstSize=Math.max(.69f,Math.min(.97f,.82f+.135f*spread));
            float cx=(x0+x1)*.5f,cy=(y0+y1)*.5f;
            float sx=((hash&1)==0?-.07f:.07f)*(x1-x0);
            float sy=((hash&2)==0?-.07f:.07f)*(y1-y0);
            addOverprintMark(source,w,h,o,pieces,first,cell,OVERPRINT_MIXED,0,angle,
                    cx+sx,cy+sy,x0,y0,x1,y1,firstSize,mmPx);
            // A low-tone accent is localised where the photo is actually dark,
            // rather than in a random corner of the tile. This is essential
            // for the pupil/nostril/lip silhouette and dark glasses.
            if(o.motifDepth>=2 && tone.range>17f && tone.mean-tone.low>10f){
                int dark=overprintNearest(tone.lowColor,palette,first,-1);
                if(dark>0){
                    float size=.41f+.16f*Math.min(1f,(tone.range-17f)/75f);
                    addOverprintMark(source,w,h,o,pieces,dark,cell,OVERPRINT_LOZENGES,1,angle,
                            tone.lowX,tone.lowY,x0,y0,x1,y1,size,mmPx);
                }
            }
            // The third colour is reserved for real within-cell contrast;
            // uniform skin and uniform shadow no longer receive random dots.
            if(o.motifDepth>=3 && o.layerCount>=4 && tone.range>48f){
                int second=overprintNearest(tone.lowColor,palette,first,-1);
                int bright=overprintNearest(tone.highColor,palette,first,second);
                if(bright>0){
                    float size=.32f+.09f*Math.min(1f,(tone.range-48f)/65f);
                    addOverprintMark(source,w,h,o,pieces,bright,cell,OVERPRINT_ORBITS,2,angle,
                            tone.highX,tone.highY,x0,y0,x1,y1,size,mmPx);
                }
            }
        }
        Result result=new Result(w,h,cols,rows,skipped,o,pieces);
        if(result.maxContours()>MAX_CUT_CONTOURS)
            throw new IllegalArgumentException("Painterly contour budget exceeded; reduce cell density");
        return result;
    }
    private static final class PainterlyTileTone {
        float mean,low,high,range,lowX,lowY,highX,highY;
        float[] lowColor,highColor;
    }
    /** Robust near-decile samples; no single hair pixel may control a face cell. */
    private static PainterlyTileTone painterlyTileTone(int[] source,boolean[] allowed,
            int w,int h,float x0,float y0,float x1,float y1){
        final int n=7;
        float[] tones=new float[n*n];int[] colours=new int[n*n];
        float[] xs=new float[n*n],ys=new float[n*n];int count=0;
        for(int j=0;j<n;j++)for(int i=0;i<n;i++){
            float x=x0+(i+.5f)*(x1-x0)/n,y=y0+(j+.5f)*(y1-y0)/n;
            if(!portraitAllowed(source,allowed,w,h,x,y))continue;
            int c=source[(int)y*w+(int)x];
            tones[count]=luminance(c);colours[count]=c;xs[count]=x;ys[count]=y;count++;
        }
        if(count==0)return null;
        float total=0f;
        for(int i=0;i<count;i++)total+=tones[i];
        // Stable insertion sort on at most 49 primitives. Avoid boxing 49
        // Integer objects per cell on every adjustment/preview render.
        for(int i=1;i<count;i++){
            float v=tones[i],x=xs[i],y=ys[i];int color=colours[i],j=i-1;
            while(j>=0&&tones[j]>v){
                tones[j+1]=tones[j];xs[j+1]=xs[j];ys[j+1]=ys[j];colours[j+1]=colours[j];j--;
            }
            tones[j+1]=v;xs[j+1]=x;ys[j+1]=y;colours[j+1]=color;
        }
        int lo=Math.min(count-1,(int)(count*.14f));
        int hi=Math.min(count-1,(int)(count*.85f));
        PainterlyTileTone t=new PainterlyTileTone();
        t.mean=total/count;t.low=tones[lo];t.high=tones[hi];t.range=t.high-t.low;
        t.lowX=xs[lo];t.lowY=ys[lo];t.highX=xs[hi];t.highY=ys[hi];
        t.lowColor=lin(colours[lo]);t.highColor=lin(colours[hi]);
        return t;
    }

    /**
     * Multi-scale dark-feature preservation. Identify only dark structures
     * that persist relative to the neighbouring 1–2-cell *photographic* tone,
     * e.g. pupils, nostrils, lids, and lips. Beard hairs and skin noise are
     * mostly removed by the two-scale filter. Connected regions are expressed
     * as solid, smooth PCA-aligned patches, never photo pixel tracings.
     * Analysis is done at ~12 samples/cell, independent of a large input JPEG.
     */
    private static ArrayList<Piece> painterlyDarkAnchors(int[] src,int w,int h,Options o,
            float cw,float ch,float mmPx){
        ArrayList<Piece> found=new ArrayList<>();
        if(o.layerCount<2)return found;
        int darkLayer=-1;float darkTone=Float.POSITIVE_INFINITY;
        for(int l=1;l<o.layerCount;l++){
            float tone=luminance(o.colors[l]);
            if(tone<darkTone){darkTone=tone;darkLayer=l;}
        }
        if(darkLayer<0)return found;
        int sw=Math.max(48,Math.min(360,o.columns*12));
        int sh=Math.max(24,Math.min(540,Math.round(sw*h/(float)w)));
        if((long)sw*sh>260000)return found;
        int n=sw*sh,stride=sw+1;
        float[] luma=new float[n];boolean[] valid=new boolean[n];
        float sx=w/(float)sw,sy=h/(float)sh;
        for(int y=0;y<sh;y++)for(int x=0;x<sw;x++){
            if(((y*sw+x)&1023)==0)CancellationCheckpoint.check();
            int ox=Math.min(w-1,(int)((x+.5f)*sx));
            int oy=Math.min(h-1,(int)((y+.5f)*sy));
            int idx=oy*w+ox,i=y*sw+x;
            valid[i]=(src[idx]>>>24)>=128&&(o.allowedPixels==null||o.allowedPixels[idx]);
            luma[i]=valid[i]?luminance(src[idx]):0f;
        }
        // Summed-area tables calculate both scales exactly in O(1)/pixel.
        double[] integral=new double[(sw+1)*(sh+1)];
        int[] count=new int[integral.length];
        for(int y=0;y<sh;y++){
            double rows=0;int rowCount=0;
            for(int x=0;x<sw;x++){
                int i=y*sw+x,k=(y+1)*stride+x+1;
                if(valid[i]){rows+=luma[i];rowCount++;}
                integral[k]=integral[k-stride]+rows;
                count[k]=count[k-stride]+rowCount;
            }
        }
        float samplesCell=sw/(float)o.columns;
        int rFine=Math.max(1,Math.round(samplesCell*.17f));
        int rContext=Math.max(4,Math.round(samplesCell*1.15f));
        boolean[] mark=new boolean[n];
        for(int y=0;y<sh;y++)for(int x=0;x<sw;x++){
            int i=y*sw+x;
            if(!valid[i])continue;
            float fine=portraitIntegralMean(integral,count,stride,sw,sh,x,y,rFine);
            float context=portraitIntegralMean(integral,count,stride,sw,sh,x,y,rContext);
            if(Float.isNaN(fine)||Float.isNaN(context))continue;
            // Require a dark accent that actually improves the selected vinyl
            // palette; do not invent dark spots when the dark sheet is too light.
            mark[i]=context-fine>26f && fine<175f && fine>darkTone-40f;
        }
        boolean[] visited=new boolean[n];int[] queue=new int[n];
        float cellPixels=samplesCell*samplesCell;
        for(int i=0;i<n;i++){
            if(!mark[i]||visited[i])continue;
            CancellationCheckpoint.check();
            int head=0,tail=0;queue[tail++]=i;visited[i]=true;
            double sumX=0,sumY=0,sumXX=0,sumYY=0,sumXY=0,sumTone=0;
            int minX=sw,maxX=0,minY=sh,maxY=0;
            while(head<tail){
                int k=queue[head++],x=k%sw,y=k/sw;
                minX=Math.min(minX,x);maxX=Math.max(maxX,x);
                minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                sumX+=x+.5;sumY+=y+.5;
                sumXX+=(x+.5)*(x+.5);sumYY+=(y+.5)*(y+.5);
                sumXY+=(x+.5)*(y+.5);sumTone+=luma[k];
                for(int yy=Math.max(0,y-1);yy<=Math.min(sh-1,y+1);yy++)
                    for(int xx=Math.max(0,x-1);xx<=Math.min(sw-1,x+1);xx++){
                        int ni=yy*sw+xx;
                        if(mark[ni]&&!visited[ni]){visited[ni]=true;queue[tail++]=ni;}
                    }
            }
            // Too-small dots make impossible weeding. Very large dark blobs
            // are hair/beard regions better described by the coloured cells.
            if(tail<Math.max(6,Math.round(cellPixels*.10f))||tail>cellPixels*11f)continue;
            if(Math.max(maxX-minX,maxY-minY)>samplesCell*6.5f)continue;
            float mx=(float)(sumX/tail),my=(float)(sumY/tail);
            float vx=(float)(sumXX/tail-mx*mx),vy=(float)(sumYY/tail-my*my),
                    covariance=(float)(sumXY/tail-mx*my);
            float root=(float)Math.hypot(vx-vy,2f*covariance);
            float major=Math.max(0f,(vx+vy+root)*.5f),minor=Math.max(0f,(vx+vy-root)*.5f);
            float a=Math.max(1.1f,(float)Math.sqrt(major)*1.83f);
            float b=Math.max(.9f,(float)Math.sqrt(minor)*1.62f);
            // Do not enlarge a connected dark feature beyond its actual extent.
            a=Math.min(a,(float)Math.hypot(maxX-minX+1,maxY-minY+1)*.65f);
            float width=2f*b*Math.min(sx,sy)*mmPx;
            if(width<o.minFeatureMm*1.35f)continue;
            float cx=mx*sx,cy=my*sy,angle=.5f*(float)Math.atan2(2f*covariance,vx-vy);
            float cs=(float)Math.cos(angle),sn=(float)Math.sin(angle);
            int verts=36;float[] shape=new float[verts*2];
            for(int t=0;t<verts;t++){
                float phi=(float)(2*Math.PI*t/verts);
                float ex=a*(float)Math.cos(phi),ey=b*(float)Math.sin(phi);
                shape[2*t]=cx+(cs*ex-sn*ey)*sx;
                shape[2*t+1]=cy+(sn*ex+cs*ey)*sy;
            }
            float[] bb=polygonBounds(shape);
            if(bb[0]<0||bb[1]<0||bb[2]>=w||bb[3]>=h)continue;
            int coverage=maskCoverage(src,o.allowedPixels,w,h,bb[0],bb[1],bb[2],bb[3]);
            if(coverage==0)continue;
            if(coverage!=2){
                List<RasterRegionClipper.Region> clipped=RasterRegionClipper.clip(shape,null,src,o.allowedPixels,w,h,
                        Math.max(0,(int)Math.floor(bb[0])),Math.max(0,(int)Math.floor(bb[1])),
                        Math.min(w,(int)Math.ceil(bb[2])),Math.min(h,(int)Math.ceil(bb[3])),
                        bb[0],bb[1],bb[2],bb[3],mmPx,o.minFeatureMm);
                if(clipped.size()!=1||!clipped.get(0).holes.isEmpty())continue;
                shape=clipped.get(0).outer;
            }
            if(StyleKit.selfIntersections(shape,1)>0)continue;
            // Centre of the recorded region should be substantially darker
            // than the context, so the mark belongs in the photograph.
            if(sumTone/tail>170f)continue;
            float minGap=(o.gapMm+.03f)/mmPx;
            boolean conflict=false;
            for(Piece existing:found)if(polygonConflicts(shape,existing.outer,minGap)){
                conflict=true;break;
            }
            if(!conflict)found.add(new Piece(darkLayer,-3,shape,(float[])null));
            if(found.size()>=26)break;
        }
        return found;
    }
    private static float portraitIntegralMean(double[] sum,int[] count,int stride,int w,int h,
            int x,int y,int r){
        int x0=Math.max(0,x-r),x1=Math.min(w,x+r+1),
            y0=Math.max(0,y-r),y1=Math.min(h,y+r+1);
        int a=y0*stride+x0,b=y0*stride+x1,c=y1*stride+x0,d=y1*stride+x1;
        int n=count[d]-count[b]-count[c]+count[a];
        if(n<(x1-x0)*(y1-y0)*.62f)return Float.NaN;
        return (float)((sum[d]-sum[b]-sum[c]+sum[a])/n);
    }

    /**
     * Photo-guided painterly cells. Unlike the four existing (tile-bounded)
     * Overprint styles, this mode reserves a small number of continuous,
     * rounded strokes that FOLLOW strong original-photo gradients across 2-4
     * tiles. The rest of the image retains independently colored mixed cells.
     * The strokes are genuine filled, single-contour blade paths, never SVG
     * strokes, masks, clipped paint, or nested vinyl knockouts.
     *
     * This is a legacy contour renderer, superseded by generatePainterlyField.
     * This is a procedural interpretation of photo-grid/biomorphic mark
     * painting; it is not a semantic facial-landmark model. Eyeglass frames,
     * noses and jawlines are followed where the source has usable contrast.
     */
    private static Result generatePainterly(int[] source,int w,int h,Options o){
        o.solidBase=true;
        o.detailMotifOverlay=false;
        Result ordinary=generatePainterlyCells(source,w,h,o);
        int cols=ordinary.columns,rows=ordinary.rows;
        float cw=w/(float)cols,ch=h/(float)rows,mmPx=o.widthMm/w;
        float sampleRadius=Math.max(1.4f,Math.min(7f,Math.min(cw,ch)*.33f));
        ArrayList<PortraitEdgeSeed> seeds=new ArrayList<>();
        // Only photo gradients are considered: the alpha/Snip boundary itself
        // is never mistaken for a painted highlight or facial outline.
        for(int row=0;row<rows;row++)for(int col=0;col<cols;col++){
            if(((row*cols+col)&63)==0)CancellationCheckpoint.check();
            PortraitEdgeSeed best=null;
            for(int sy=1;sy<=4;sy++)for(int sx=1;sx<=4;sx++){
                float x=(col+(sx-.5f)/4f)*cw;
                float y=(row+(sy-.5f)/4f)*ch;
                PortraitEdgeSeed edge=portraitGradient(source,o.allowedPixels,w,h,x,y,sampleRadius);
                if(edge!=null&&(best==null||edge.power>best.power))best=edge;
            }
            if(best!=null&&best.power>31f)seeds.add(best);
        }
        seeds.sort((a,b)->{
            int c=Float.compare(b.power,a.power);
            if(c!=0)return c;
            c=Float.compare(a.y,b.y);return c!=0?c:Float.compare(a.x,b.x);
        });
        // Bounded number of extended brushstrokes, with density chosen from
        // actual photographic detail rather than a wholly artificial pattern.
        int budget=Math.min(11,Math.max(3,cols*rows/78));
        float minSpan=Math.min(cw,ch)*2.3f;
        float gapPx=(o.gapMm+.028f)/mmPx;
        ArrayList<Piece> anchors=painterlyDarkAnchors(source,w,h,o,cw,ch,mmPx);
        ArrayList<Piece> strokes=new ArrayList<>();
        for(PortraitEdgeSeed seed:seeds){
            if(strokes.size()>=budget)break;
            CancellationCheckpoint.check();
            ArrayList<float[]> back=followPortraitEdge(source,o.allowedPixels,w,h,seed,-1,
                    cw,ch,sampleRadius);
            ArrayList<float[]> forward=followPortraitEdge(source,o.allowedPixels,w,h,seed,1,
                    cw,ch,sampleRadius);
            ArrayList<float[]> centerline=new ArrayList<>();
            for(int i=back.size()-1;i>=0;i--)centerline.add(back.get(i));
            centerline.add(new float[]{seed.x,seed.y});
            centerline.addAll(forward);
            if(centerline.size()<5||pathLength(centerline)<minSpan)continue;
            // Tip width never tapers to an unweedable needle. Curvature and
            // internal radii are conservative, even at small physical scales.
            float cellMm=Math.min(cw,ch)*mmPx;
            float brushMm=Math.max(o.minFeatureMm*1.5f,
                    Math.min(1.2f,cellMm*(.085f+.025f*o.ornamentation)));
            float[] outline=portraitRibbon(centerline,brushMm/mmPx);
            if(outline==null||StyleKit.selfIntersections(outline,1)!=0)continue;
            float[] bb=polygonBounds(outline);
            int covered=maskCoverage(source,o.allowedPixels,w,h,
                    Math.max(0,bb[0]),Math.max(0,bb[1]),
                    Math.min(w,bb[2]),Math.min(h,bb[3]));
            if(covered==0)continue;
            // Trim against the REAL source mask before collision testing and
            // vector export. Only the largest contiguous trace is retained:
            // a slit in a Snip must not turn into disconnected specks.
            float[] trimmed=outline;
            if(covered!=2){
                List<RasterRegionClipper.Region> region=RasterRegionClipper.clip(outline,null,
                        source,o.allowedPixels,w,h,
                        Math.max(0,(int)Math.floor(bb[0])),Math.max(0,(int)Math.floor(bb[1])),
                        Math.min(w,(int)Math.ceil(bb[2])),Math.min(h,(int)Math.ceil(bb[3])),
                        Math.max(0f,bb[0]),Math.max(0f,bb[1]),
                        Math.min(w,bb[2]),Math.min(h,bb[3]),mmPx,o.minFeatureMm);
                if(region.isEmpty())continue;
                region.sort((a,b)->Float.compare(polygonArea(b.outer),polygonArea(a.outer)));
                if(!region.get(0).holes.isEmpty())continue;
                trimmed=region.get(0).outer;
                if(StyleKit.selfIntersections(trimmed,1)>0)continue;
            }
            // Cropping/partial alpha may shorten a bridge considerably.
            float[] trimmedBounds=polygonBounds(trimmed);
            if(Math.max(trimmedBounds[2]-trimmedBounds[0],
                    trimmedBounds[3]-trimmedBounds[1])<minSpan*.75f)continue;
            int layer=portraitStrokeLayer(o,seed);
            if(layer<=0)continue;
            boolean conflicting=false;
            for(Piece feature:anchors)if(feature.layer==layer &&
                    polygonConflicts(trimmed,feature.outer,gapPx)){conflicting=true;break;}
            if(conflicting)continue;
            for(Piece previous:strokes){
                if(previous.layer==layer&&polygonConflicts(trimmed,previous.outer,gapPx)){
                    conflicting=true;break;
                }
            }
            if(conflicting)continue;
            strokes.add(new Piece(layer,-2,trimmed,(float[])null));
        }
        // A physical sheet must NEVER contain overlapping cuts. Reserve the
        // full, connected stroke geometry by suppressing only those original
        // same-sheet cell marks that lie within its required clearance band.
        // Cross-sheet overlaps are intentional, and still assemble in order.
        ArrayList<Piece> out=new ArrayList<>(ordinary.pieces.size()+strokes.size()+anchors.size());
        for(Piece p:ordinary.pieces){
            CancellationCheckpoint.check();
            if(p.layer==0){out.add(p);continue;}
            boolean collision=false;
            for(Piece feature:anchors){
                // Preserve important dark landmarks even when a paler vinyl
                // is physically stacked above them. Removing an overlapping
                // upper colour piece is safer than punching a tiny hole.
                if(p.layer>=feature.layer&&polygonConflicts(p.outer,feature.outer,gapPx)){
                    collision=true;break;
                }
            }
            if(collision)continue;
            for(Piece stroke:strokes){
                if(stroke.layer==p.layer&&polygonConflicts(p.outer,stroke.outer,gapPx)){
                    collision=true;break;
                }
            }
            if(!collision)out.add(p);
        }
        out.addAll(anchors);
        out.addAll(strokes);
        Result result=new Result(w,h,cols,rows,ordinary.skippedCells,o,out);
        if(result.maxContours()>MAX_CUT_CONTOURS)
            throw new IllegalArgumentException("Painterly contour budget exceeded; reduce cell density");
        return result;
    }

    private static final class PortraitEdgeSeed {
        final float x,y,gx,gy,power;
        PortraitEdgeSeed(float x,float y,float gx,float gy){
            this.x=x;this.y=y;this.gx=gx;this.gy=gy;
            this.power=(float)Math.hypot(gx,gy);
        }
    }
    private static boolean portraitAllowed(int[] src,boolean[] mask,int w,int h,float xf,float yf){
        if(xf<0f||yf<0f||xf>=w||yf>=h)return false;
        int x=(int)xf,y=(int)yf;
        int i=y*w+x;
        return (src[i]>>>24)>=128&&(mask==null||mask[i]);
    }
    /**
     * 3x3 box-smoothed brightness sampled on both sides of a macro contour.
     * Original 1px-gradient tracing mistook beard hairs and skin noise for
     * long structural strokes. Require a valid support region on BOTH sides.
     */
    private static float portraitSmoothTone(int[] src,boolean[] mask,int w,int h,
            float x,float y,float radius){
        float sum=0f;
        for(int j=-1;j<=1;j++)for(int i=-1;i<=1;i++){
            float px=x+i*radius,py=y+j*radius;
            if(!portraitAllowed(src,mask,w,h,px,py))return Float.NaN;
            sum+=luminance(src[(int)py*w+(int)px]);
        }
        return sum/9f;
    }
    private static PortraitEdgeSeed portraitGradient(int[] src,boolean[] mask,int w,int h,
                                                      float x,float y,float radius){
        if(!portraitAllowed(src,mask,w,h,x,y))return null;
        float sr=Math.max(.6f,radius*.43f);
        float gx=0f,gy=0f;
        for(int k=-1;k<=1;k++){
            float offset=k*radius*.42f,weight=k==0?2f:1f;
            float l=portraitSmoothTone(src,mask,w,h,x-radius,y+offset,sr);
            float r=portraitSmoothTone(src,mask,w,h,x+radius,y+offset,sr);
            float u=portraitSmoothTone(src,mask,w,h,x+offset,y-radius,sr);
            float d=portraitSmoothTone(src,mask,w,h,x+offset,y+radius,sr);
            if(!Float.isFinite(l)||!Float.isFinite(r)||!Float.isFinite(u)||!Float.isFinite(d))return null;
            gx+=weight*(r-l);gy+=weight*(d-u);
        }
        return new PortraitEdgeSeed(x,y,gx*.25f,gy*.25f);
    }
    private static ArrayList<float[]> followPortraitEdge(int[] src,boolean[] mask,int w,int h,
            PortraitEdgeSeed seed,int sign,float cw,float ch,float radius){
        ArrayList<float[]> points=new ArrayList<>();
        float step=Math.max(1.3f,Math.min(cw,ch)*.22f);
        float longest=Math.min(cw,ch)*1.75f;
        float travel=0,x=seed.x,y=seed.y;
        float inv=1f/seed.power,dx=-seed.gy*inv*sign,dy=seed.gx*inv*sign;
        while(travel+step<=longest){
            CancellationCheckpoint.check();
            float nx=x+dx*step,ny=y+dy*step;
            PortraitEdgeSeed found=portraitGradient(src,mask,w,h,nx,ny,radius);
            if(found==null||found.power<Math.max(16f,seed.power*.47f))break;
            float tx=-found.gy/found.power,ty=found.gx/found.power;
            if(tx*dx+ty*dy<0){tx=-tx;ty=-ty;}
            float dot=tx*dx+ty*dy;
            if(dot<.86f)break; // require coherent structural orientation
            float mx=dx*.64f+tx*.36f,my=dy*.64f+ty*.36f;
            float norm=(float)Math.hypot(mx,my);dx=mx/norm;dy=my/norm;
            x=nx;y=ny;travel+=step;
            points.add(new float[]{x,y});
        }
        return points;
    }
    private static float pathLength(List<float[]> points){
        float total=0;for(int i=1;i<points.size();i++){
            float[] a=points.get(i-1),b=points.get(i);
            total+=(float)Math.hypot(a[0]-b[0],a[1]-b[1]);
        }return total;
    }
    /** Closed variable-width rounded ribbon. No 0-width ends, pen paths, or holes. */
    private static float[] portraitRibbon(List<float[]> centerline,float widthPx){
        int n=centerline.size();if(n<3)return null;
        ArrayList<Float> xy=new ArrayList<>(n*4+32);
        for(int i=0;i<n;i++){
            float[] before=centerline.get(Math.max(0,i-1)),after=centerline.get(Math.min(n-1,i+1));
            float dx=after[0]-before[0],dy=after[1]-before[1];
            float norm=(float)Math.hypot(dx,dy);if(norm<1e-4f)return null;
            float half=widthPx*.5f*(i==0||i==n-1?.94f:1f);
            xy.add(centerline.get(i)[0]-dy/norm*half);
            xy.add(centerline.get(i)[1]+dx/norm*half);
        }
        float[] last=centerline.get(n-1),pre=centerline.get(n-2);
        addPortraitCap(xy,last,pre,widthPx*.47f);
        for(int i=n-1;i>=0;i--){
            float[] before=centerline.get(Math.max(0,i-1)),after=centerline.get(Math.min(n-1,i+1));
            float dx=after[0]-before[0],dy=after[1]-before[1];
            float norm=(float)Math.hypot(dx,dy);if(norm<1e-4f)return null;
            float half=widthPx*.5f*(i==0||i==n-1?.94f:1f);
            xy.add(centerline.get(i)[0]+dy/norm*half);
            xy.add(centerline.get(i)[1]-dx/norm*half);
        }
        float[] first=centerline.get(0),next=centerline.get(1);
        addPortraitCap(xy,first,next,widthPx*.47f);
        float[] poly=new float[xy.size()];
        for(int i=0;i<poly.length;i++)poly[i]=xy.get(i);
        return poly;
    }
    private static void addPortraitCap(List<Float> out,float[] end,float[] other,float radius){
        float[] center=end;
        float dx=end[0]-other[0],dy=end[1]-other[1];
        float norm=(float)Math.hypot(dx,dy);if(norm<1e-4f)return;
        dx/=norm;dy/=norm;
        // From left edge to right edge of endpoint, via forward tip.
        // At the start the vector is reversed, so the same arc applies.
        for(int j=1;j<=4;j++){
            double phi=Math.PI*j/5;
            float along=(float)Math.sin(phi)*radius;
            float side=(float)Math.cos(phi)*radius;
            out.add(center[0]+dx*along-dy*side);
            out.add(center[1]+dy*along+dx*side);
        }
    }
    private static float[] polygonBounds(float[] p){
        float[] b={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        for(int i=0;i<p.length;i+=2){
            b[0]=Math.min(b[0],p[i]);b[1]=Math.min(b[1],p[i+1]);
            b[2]=Math.max(b[2],p[i]);b[3]=Math.max(b[3],p[i+1]);
        }return b;
    }
    private static boolean polygonConflicts(float[] a,float[] b,float gap){
        float[] ba=polygonBounds(a),bb=polygonBounds(b);
        if(ba[2]+gap<bb[0]||bb[2]+gap<ba[0]||ba[3]+gap<bb[1]||bb[3]+gap<ba[1])return false;
        if(inPoly(a,b[0],b[1])||inPoly(b,a[0],a[1]))return true;
        return PhysicalContourClearance.minDistance(a,b)<gap;
    }
    private static int portraitStrokeLayer(Options o,PortraitEdgeSeed seed){
        int first=-1,second=-1;float firstTone=Float.POSITIVE_INFINITY,secondTone=Float.POSITIVE_INFINITY;
        for(int l=1;l<o.layerCount;l++){
            float brightness=luminance(o.colors[l]);
            if(brightness<firstTone){second=first;secondTone=firstTone;first=l;firstTone=brightness;}
            else if(brightness<secondTone){second=l;secondTone=brightness;}
        }
        if(first<0)return -1;
        // Strong structural edges belong on the darkest available physical
        // vinyl; medium edges may use the second-darkest, retaining variety.
        return second>0&&seed.power<56f?second:first;
    }

    /** Valid alpha/Snip-only 5x5 sample so transparent background never biases tones. */
    private static float[] overprintMean(int[] src,boolean[] allowed,int w,int h,
                                         float x0,float y0,float x1,float y1){
        float[] sum=new float[3];int count=0;
        for(int y=0;y<5;y++)for(int x=0;x<5;x++){
            int px=clamp((int)(x0+(x+.5f)*(x1-x0)/5f),w);
            int py=clamp((int)(y0+(y+.5f)*(y1-y0)/5f),h);
            int i=py*w+px;
            if((src[i]>>>24)<128||(allowed!=null&&!allowed[i]))continue;
            float[] rgb=lin(src[i]);for(int k=0;k<3;k++)sum[k]+=rgb[k];count++;
        }
        if(count==0){
            // Thin Snip slivers can miss every sample; find an actual included pixel.
            for(int y=Math.max(0,(int)y0);y<Math.min(h,(int)Math.ceil(y1));y++)
                for(int x=Math.max(0,(int)x0);x<Math.min(w,(int)Math.ceil(x1));x++){
                    int i=y*w+x;if((src[i]>>>24)<128||(allowed!=null&&!allowed[i]))continue;
                    return lin(src[i]);
                }
            return null;
        }
        for(int k=0;k<3;k++)sum[k]/=count;
        return sum;
    }
    private static int overprintNearest(float[] color,float[][] palette,int excludeA,int excludeB){
        int best=0;float score=Float.POSITIVE_INFINITY;
        // Sheet 0 is the solid foundation. Its color must never be used as
        // an upper cut sheet or turned into a ring of tiny unweedable holes.
        for(int layer=1;layer<palette.length;layer++){
            if(layer==excludeA||layer==excludeB)continue;
            float cost=diff(color,palette[layer]);
            if(cost<score){score=cost;best=layer;}
        }
        return best;
    }
    private static float[] overprintMarkPolygon(Options o,int kind,int cell,int depth,float angle,
            float cx,float cy,float x0,float y0,float x1,float y1,float size,float mmPx){
        int family;
        switch(kind){
            case OVERPRINT_ORBITS:family=ELLIPSE;break;
            case OVERPRINT_LOZENGES:family=CAPSULE;break;
            case OVERPRINT_DIAMONDS:family=ROUNDED_SQUARE;angle+=(float)Math.PI*.25f;break;
            case OVERPRINT_MIXED:
                int[] types={ELLIPSE,CAPSULE,ROUNDED_SQUARE,HEXAGON};
                family=types[Math.floorMod(mix(cell*15427+depth*877),types.length)];break;
            default:throw new IllegalArgumentException("Unknown overprint type");
        }
        if(depth==1)angle+=(float)Math.PI*.35f;
        else if(depth==2)angle-=(float)Math.PI*.31f;
        float[] norm=normalizedShape(family,angle);
        float[] poly=scaledShape(norm,cx,cy,(x1-x0)*.5f,(y1-y0)*.5f,size);
        float left=Float.POSITIVE_INFINITY,top=Float.POSITIVE_INFINITY;
        float right=Float.NEGATIVE_INFINITY,bottom=Float.NEGATIVE_INFINITY;
        for(int i=0;i<poly.length;i+=2){
            left=Math.min(left,poly[i]);right=Math.max(right,poly[i]);
            top=Math.min(top,poly[i+1]);bottom=Math.max(bottom,poly[i+1]);
        }
        // The tighter size test is conservative on small cutter artwork.
        if(Math.min(right-left,bottom-top)*mmPx<o.minFeatureMm*1.5f)return null;
        // Allow for rotation without crossing the cell safety envelope.
        float pull=Math.max(0,Math.max(Math.max(x0-left,right-x1)/(x1-x0),
                                       Math.max(y0-top,bottom-y1)/(y1-y0)));
        if(pull>0){
            float safe=Math.max(.3f,1f-pull*2.1f);
            poly=scaledShape(norm,cx,cy,(x1-x0)*.5f,(y1-y0)*.5f,size*safe);
        }
        // Large painterly patches have shifted centres; constrain *every*
        // vertex inside the cell's pre-inset cutting envelope. A bounding-box
        // shrink based on the overshoot alone is insufficient after rotation.
        if(o.overprintKind==OVERPRINT_PAINTERLY){
            float margin=.04f/mmPx;
            float factor=1f;
            float loX=x0+margin,hiX=x1-margin,loY=y0+margin,hiY=y1-margin;
            if(loX>=hiX||loY>=hiY)return null;
            for(int i=0;i<poly.length;i+=2){
                float dx=poly[i]-cx,dy=poly[i+1]-cy;
                if(dx>0)factor=Math.min(factor,(hiX-cx)/dx);
                else if(dx<0)factor=Math.min(factor,(loX-cx)/dx);
                if(dy>0)factor=Math.min(factor,(hiY-cy)/dy);
                else if(dy<0)factor=Math.min(factor,(loY-cy)/dy);
            }
            if(factor<=0f)return null;
            if(factor<1f)for(int i=0;i<poly.length;i+=2){
                poly[i]=cx+(poly[i]-cx)*factor;
                poly[i+1]=cy+(poly[i+1]-cy)*factor;
            }
        }
        // Do not export a shape that has become a sub-minimum island after fitting.
        float width=Float.NEGATIVE_INFINITY,height=Float.NEGATIVE_INFINITY;
        float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY;
        for(int i=0;i<poly.length;i+=2){minX=Math.min(minX,poly[i]);minY=Math.min(minY,poly[i+1]);
            width=Math.max(width,poly[i]);height=Math.max(height,poly[i+1]);}
        if(Math.min(width-minX,height-minY)*mmPx<o.minFeatureMm*1.5f)return null;
        return poly;
    }
    // Compatibility for retired painterly prototype internals. The saved ID
    // remains disabled at generate() validation; do not re-enable that style.
    private static void addOverprintMark(int[] src,int w,int h,Options o,
            List<Piece> pieces,int layer,int cell,int kind,int depth,float angle,
            float cx,float cy,float x0,float y0,float x1,float y1,float size,float mmPx){
        float[] polygon=overprintMarkPolygon(o,kind,cell,depth,angle,
            cx,cy,x0,y0,x1,y1,size,mmPx);
        addOverprintMark(src,w,h,o,pieces,layer,cell,polygon,x0,y0,x1,y1,mmPx);
    }
    private static void addOverprintMark(int[] src,int w,int h,Options o,
            List<Piece> pieces,int layer,int cell,float[] poly,
            float x0,float y0,float x1,float y1,float mmPx){
        addOverprintMark(src,w,h,o,pieces,layer,cell,poly,x0,y0,x1,y1,mmPx,
            maskCoverage(src,o.allowedPixels,w,h,x0,y0,x1,y1));
    }
    private static void addOverprintMark(int[] src,int w,int h,Options o,
            List<Piece> pieces,int layer,int cell,float[] poly,
            float x0,float y0,float x1,float y1,float mmPx,int mask){
        if(layer<=0||layer>=o.layerCount||poly==null||mask==0)return;
        // Only a partially clipped cell needs pixel-mask raster tracing. Full
        // tiles preserve smooth analytic contours and the same SVG coordinates.
        if(mask==2){pieces.add(new Piece(layer,cell,poly,(float[])null));return;}
        List<RasterRegionClipper.Region> clipped=RasterRegionClipper.clip(poly,null,src,o.allowedPixels,
            w,h,Math.max(0,(int)Math.floor(x0)),Math.max(0,(int)Math.floor(y0)),
            Math.min(w,(int)Math.ceil(x1)),Math.min(h,(int)Math.ceil(y1)),
            x0,y0,x1,y1,mmPx,o.minFeatureMm);
        // A Snip boundary may split one mark into pixel islands that touch at
        // a corner. Those cannot be cut twice on the same physical sheet.
        // Keep the largest printable island and then only truly separated peers.
        clipped.sort((a,b)->Float.compare(polygonArea(b.outer),polygonArea(a.outer)));
        ArrayList<float[]> accepted=new ArrayList<>();
        for(RasterRegionClipper.Region part:clipped){
            CancellationCheckpoint.check();
            float bx0=Float.MAX_VALUE,by0=Float.MAX_VALUE,bx1=-Float.MAX_VALUE,by1=-Float.MAX_VALUE;
            for(int i=0;i<part.outer.length;i+=2){
                bx0=Math.min(bx0,part.outer[i]);bx1=Math.max(bx1,part.outer[i]);
                by0=Math.min(by0,part.outer[i+1]);by1=Math.max(by1,part.outer[i+1]);
            }
            if(Math.min(bx1-bx0,by1-by0)*mmPx<o.minFeatureMm*1.1f)continue;
            boolean tooClose=false;
            for(float[] other:accepted)
                if(PhysicalContourClearance.minDistance(part.outer,other)*mmPx<o.gapMm+.008f){
                    tooClose=true;break;
                }
            if(tooClose)continue;
            accepted.add(part.outer);
            pieces.add(new Piece(layer,cell,part.outer,part.holes));
        }
    }

    /**
     * Photo-first painterly reconstruction. The photo determines EVERY mark:
     * cells/columns control the sampling budget, not shape boundaries. No
     * circles, lozenges, tesserae, or independent facial-outline pass.
     *
     * We grow broad organic paint swells first and smaller coherent dabs next.
     * Candidate pigment/shape combinations are accepted only if their actual
     * opaque compositing decreases a multi-scale photo reconstruction error.
     * The same signed error is recomputed after each accepted shape, so later
     * sheets cannot casually cover pupils, lips, or nose shadows.
     *
     * Raster pixels are transient objective samples ONLY. Output consists of
     * smooth, CLOSED, non-overlapping (same physical sheet) vector polygons.
     */
    private static Result generatePainterlyField(int[] photo,int w,int h,Options o){
        o.solidBase=true;
        o.detailMotifOverlay=false;
        int sw=Math.min(190,Math.max(76,Math.max(o.columns*5,
                Math.round(o.widthMm*.63f))));
        int sh=Math.max(16,Math.round(sw*h/(float)w));
        // Large, tall photographs otherwise exceed preview memory/contour budget.
        if((long)sw*sh>72000){float shrink=(float)Math.sqrt(72000.0/(sw*(double)sh));
            sw=Math.max(32,(int)(sw*shrink));sh=Math.max(16,(int)(sh*shrink));}
        final float scaleX=w/(float)sw,scaleY=h/(float)sh;
        final float pxMm=o.widthMm/sw;
        PainterlyPhotoField field=new PainterlyPhotoField(photo,w,h,o.allowedPixels,sw,sh,o.colors,o.layerCount,o.photoDetail);
        ArrayList<Piece> pieces=new ArrayList<>();
        addSolidSilhouette(photo,w,h,o,pieces);
        @SuppressWarnings("unchecked") ArrayList<PainterlyPlaced>[] placed=new ArrayList[o.layerCount];
        for(int layer=0;layer<o.layerCount;layer++)placed[layer]=new ArrayList<>();
        int total=0;
        // Paint the low-frequency photographic color mass FIRST. Without this,
        // a dark silhouette backing peeks through all small organic marks and
        // wipes out the identity of the face. This underpainting is a traced
        // photo region, NOT a cell, tile or catalogued motif.
        PainterlyUnderpaint underpaint=field.underpaint();
        PainterlyUnderpaint midpaint=field.underpaintMidtones(underpaint);
        if(underpaint!=null)field.emphasizeNearUnderpaint(underpaint);
        // A conservative budget protects editor, SVG import, and phone render.
        int totalLimit=Math.min(MAX_CUT_CONTOURS-20,Math.min(1800,
                Math.max(560,(int)(o.widthMm*6.0f*(.65f+.42f*o.ornamentation)))));
        int eachLimit=Math.max(45,totalLimit/(o.layerCount-1)+15);
        float minWidth=Math.max(o.minFeatureMm*1.65f,o.minimumCutMm*.95f);
        float physicalGap=Math.max(o.gapMm,.025f)+.085f;
        for(int layer=1;layer<o.layerCount;layer++){
            for(PainterlyUnderpaint foundation:new PainterlyUnderpaint[]{midpaint,underpaint}){
                if(foundation==null||foundation.layer!=layer)continue;
                java.util.List<RasterRegionClipper.Region> regions=field.underpaintRegions(
                        foundation,photo,w,h,o.allowedPixels,o.minFeatureMm,o.widthMm);
                for(RasterRegionClipper.Region region:regions){
                    if(region.outer.length<6||StyleKit.selfIntersections(region.outer,1)>0)continue;
                    float[] bb=polygonBounds(region.outer);
                    if(painterlyTooClose(region.outer,bb,placed[layer],
                            (o.gapMm+.09f)*w/o.widthMm))continue;
                    pieces.add(new Piece(layer,-4,region.outer,region.holes));
                    placed[layer].add(new PainterlyPlaced(region.outer,bb));
                    field.paintUnderpaint(region.outer,region.holes,scaleX,scaleY,layer);
                    total++;
                }
            }
            // Descending physical swells emulate overpaint: broad compositions
            // set color masses, local marks finish their structure.
            int made=0;
            for(int pass=0;pass<Math.min(4,o.motifDepth+1)&&made<eachLimit&&total<totalLimit;pass++){
                CancellationCheckpoint.check();
                int passLimit=Math.min(eachLimit,Math.round(eachLimit*(pass==0?.20f:
                        pass==1?.46f:pass==2?.77f:1f)));
                float unit=sw/(float)o.columns;
                float width=pass==0?unit*.84f:pass==1?unit*.55f:pass==2?unit*.32f:unit*.26f;
                width=Math.max(width,minWidth/pxMm);
                float length=pass==3?unit*3.25f:width*(pass==0?2.40f:pass==1?2.05f:1.80f);
                // Never allow long, skinny cuts. Organic shapes need physical
                // body width (not just a favorable bounding rectangle).
                if(width*pxMm<minWidth-.001f)continue;
                int stride=Math.max(2,Math.round(width*(pass==0?.72f:pass==1?.73f:.88f)));
                java.util.PriorityQueue<PainterlyCandidate> queue=new java.util.PriorityQueue<>(
                        (a,b)->Double.compare(b.gain,a.gain));
                int seed=layer*719+pass*1987;
                for(int y=1;y<sh-1;y+=stride)for(int x=1;x<sw-1;x+=stride){
                    int z=y*sw+x;
                    if(!field.valid[z])continue;
                    if(pass==3&&field.coherence[z]<.035f)continue;
                    if((z&127)==0)CancellationCheckpoint.check();
                    int hash=mix(z*317+seed);
                    float xx=x+.4f+.21f*(Math.floorMod(hash,9)-4)/4f;
                    float yy=y+.4f+.19f*(Math.floorMod(hash>>>5,9)-4)/4f;
                    float theta=field.angle[z];
                    // The reference photo chooses the orientation. In a flat
                    // region, small deterministic deviations prevent printing
                    // an artificial uniform hatch or rigid mosaic.
                    if(field.coherence[z]<.025f)
                        theta=(float)((Math.floorMod(hash>>>13,17)-8)*Math.PI/20);
                    float bend=(Math.floorMod(hash>>>20,9)-4)*.025f;
                    for(int attempt=0;attempt<2;attempt++){
                        float rot=theta+(attempt==0?0f:(float)Math.PI*.5f);
                        float[] polygon=painterlyBrush(xx,yy,length,width,rot,
                                bend*(.45f+.85f*o.ornamentation),hash);
                        double gain=field.gain(polygon,layer);
                        if(gain>field.minimumGain(polygon,pass))
                            queue.offer(new PainterlyCandidate(polygon,layer,z,gain));
                    }
                }
                while(!queue.isEmpty()&&made<passLimit&&total<totalLimit){
                    CancellationCheckpoint.check();
                    PainterlyCandidate candidate=queue.poll();
                    // Previous accepted marks change the raster objective;
                    // reject stale wins, not just stale collisions.
                    double newGain=field.gain(candidate.polygon,layer);
                    if(newGain<=field.minimumGain(candidate.polygon,pass))continue;
                    float[] original=scaleLoop(candidate.polygon,scaleX,scaleY);
                    float[] bounds=polygonBounds(original);
                    if(bounds[0]<.01f||bounds[1]<.01f||bounds[2]>=w-.01f||bounds[3]>=h-.01f)continue;
                    if(StyleKit.selfIntersections(original,1)>0)continue;
                    if(painterlyTooClose(original,bounds,placed[layer],
                            physicalGap*w/o.widthMm))continue;
                    // The Snip mask is applied at export geometry resolution,
                    // AFTER tone analysis. Never score transparent surroundings
                    // as dark photo pixels and never export shapes outside Snip.
                    int mask=maskCoverage(photo,o.allowedPixels,w,h,
                            bounds[0],bounds[1],bounds[2],bounds[3]);
                    if(mask==0)continue;
                    ArrayList<float[]> accepted=new ArrayList<>();
                    if(mask==2){
                        accepted.add(original);
                    }else{
                        List<RasterRegionClipper.Region> clipped=RasterRegionClipper.clip(
                            original,null,photo,o.allowedPixels,w,h,
                            Math.max(0,(int)Math.floor(bounds[0])),Math.max(0,(int)Math.floor(bounds[1])),
                            Math.min(w,(int)Math.ceil(bounds[2])),Math.min(h,(int)Math.ceil(bounds[3])),
                            bounds[0],bounds[1],bounds[2],bounds[3],o.widthMm/w,minWidth);
                        // Disconnected Snip fragments are not safe paintstrokes.
                        if(clipped.size()!=1||!clipped.get(0).holes.isEmpty())continue;
                        accepted.add(clipped.get(0).outer);
                    }
                    boolean ok=true;
                    for(float[] outline:accepted){
                        float[] bb=polygonBounds(outline);
                        if(Math.min(bb[2]-bb[0],bb[3]-bb[1])*o.widthMm/w<minWidth)ok=false;
                        if(StyleKit.selfIntersections(outline,1)>0)ok=false;
                        if(painterlyTooClose(outline,bb,placed[layer],physicalGap*w/o.widthMm))ok=false;
                    }
                    if(!ok)continue;
                    for(float[] outline:accepted){
                        Piece p=new Piece(layer,candidate.seed,outline,(float[])null);
                        pieces.add(p);placed[layer].add(new PainterlyPlaced(outline,polygonBounds(outline)));
                        made++;total++;
                    }
                    // Rasterized paint is a scoring buffer, never exported.
                    field.paint(candidate.polygon,layer);
                }
            }
        }
        Result result=new Result(w,h,o.columns,Math.max(1,Math.round(o.columns*h/(float)w)),0,o,pieces);
        if(result.maxContours()>MAX_CUT_CONTOURS)
            throw new IllegalArgumentException("Painterly contour budget exceeded; reduce detail");
        return result;
    }
    private static final class PainterlyPlaced {
        final float[] poly,b;
        PainterlyPlaced(float[] poly,float[] b){this.poly=poly;this.b=b;}
    }
    private static boolean painterlyTooClose(float[] polygon,float[] bb,
            ArrayList<PainterlyPlaced> peers,float gap){
        for(PainterlyPlaced p:peers){
            float[] b=p.b;
            if(bb[2]+gap<b[0]||b[2]+gap<bb[0]||bb[3]+gap<b[1]||b[3]+gap<bb[1])continue;
            if(inPoly(polygon,p.poly[0],p.poly[1])||inPoly(p.poly,polygon[0],polygon[1]))return true;
            if(PhysicalContourClearance.minDistance(polygon,p.poly)<gap)return true;
        }
        return false;
    }
    private static final class PainterlyCandidate {
        final float[] polygon;
        final int layer,seed;
        final double gain;
        PainterlyCandidate(float[] polygon,int layer,int seed,double gain){
            this.polygon=polygon;this.layer=layer;this.seed=seed;this.gain=gain;
        }
    }
    /** Free-form curved paint footprint, independent of preset mosaic shapes. */
    private static float[] painterlyBrush(float cx,float cy,float len,float width,
            float angle,float bend,int hash){
        int n=32;float[] xy=new float[n*2];
        float ca=(float)Math.cos(angle),sa=(float)Math.sin(angle);
        float skew=(Math.floorMod(hash>>>7,9)-4)*.02f;
        float wobble=(Math.floorMod(hash>>>11,9)-4)*.011f;
        for(int j=0;j<n;j++){
            float t=(float)(2*Math.PI*j/n);
            float u=(float)Math.cos(t),v=(float)Math.sin(t);
            // Smooth paint swells: no acute corners or infinitely tapered tails.
            float longitudinal=u*len*.50f;
            float radial=(1f+skew*u+wobble*(float)Math.sin(t*3))*width*.50f;
            float lateral=v*radial+bend*width*(1-u*u);
            xy[2*j]=cx+ca*longitudinal-sa*lateral;
            xy[2*j+1]=cy+sa*longitudinal+ca*lateral;
        }
        return xy;
    }
    private static final class PainterlyUnderpaint {
        final int layer;
        final boolean[] mask;
        PainterlyUnderpaint(int layer,boolean[] mask){this.layer=layer;this.mask=mask;}
    }
    private static final class PainterlyPhotoField {
        final int sw,sh,count,colors;
        final boolean[] valid;
        final float[] target,display,weight,angle,coherence;
        final float[][] palette;
        int validCount;
        PainterlyPhotoField(int[] source,int w,int h,boolean[] mask,
                int sw,int sh,int[] physicalColors,int nLayers,float detail){
            this.sw=sw;this.sh=sh;count=sw*sh;colors=nLayers;
            valid=new boolean[count];target=new float[count*3];display=new float[count*3];
            weight=new float[count];angle=new float[count];coherence=new float[count];
            palette=new float[nLayers][];
            for(int i=0;i<nLayers;i++)palette[i]=lin(physicalColors[i]);
            float[] original=new float[count*3];
            for(int y=0;y<sh;y++)for(int x=0;x<sw;x++){
                int i=y*sw+x;
                if((i&511)==0)CancellationCheckpoint.check();
                // Five mask-aware photo samples per analysis pixel preserve
                // subcell highlight/feature structure without JPEG noise.
                int rcount=0;
                for(int s=0;s<5;s++){
                    float ox=(x+(s==0?.5f:s==1?.22f:s==2?.78f:.5f))*w/sw;
                    float oy=(y+(s==0?.5f:s==3?.22f:s==4?.78f:.5f))*h/sh;
                    int xx=clamp((int)ox,w),yy=clamp((int)oy,h),id=yy*w+xx;
                    if((source[id]>>>24)<128||(mask!=null&&!mask[id]))continue;
                    int rgb=source[id];float[] v=lin(rgb);
                    for(int k=0;k<3;k++)original[i*3+k]+=v[k];
                    rcount++;
                }
                if(rcount<3)continue;
                valid[i]=true;validCount++;
                for(int k=0;k<3;k++)original[i*3+k]/=rcount;
            }
            float[] medium=box(original,2),macro=box(original,6);
            // Weighted original + denoised photo. No later rendering step
            // reads Snip-edge color as though it were actual image content.
            for(int i=0;i<count;i++){
                for(int k=0;k<3;k++){
                    int p=i*3+k;
                    target[p]=(.20f+.32f*detail)*original[p]+
                        (.50f-.10f*detail)*medium[p]+(.30f-.22f*detail)*macro[p];
                    display[p]=palette[0][k];
                }
            }
            for(int y=1;y<sh-1;y++)for(int x=1;x<sw-1;x++){
                int i=y*sw+x;if(!valid[i])continue;
                float lx=luma(medium,sw,y*sw+x+1)-luma(medium,sw,y*sw+x-1),
                        ly=luma(medium,sw,(y+1)*sw+x)-luma(medium,sw,(y-1)*sw+x);
                float contrast=(float)Math.hypot(lx,ly);
                coherence[i]=contrast;
                angle[i]=(float)Math.atan2(ly,lx)+(float)Math.PI*.5f;
                // Do not prefer high-frequency beard noise over coherent
                // facial structure. Tone differences survive in target;
                // medium-scale transitions determine the priority weight.
                weight[i]=1f+Math.min(2.3f,contrast*(3f+4f*detail));
            }
            for(int i=0;i<count;i++)if(valid[i]&&weight[i]==0)weight[i]=1f;
        }
        /** Detect a sustained bright subject mass in the original photo. */
        PainterlyUnderpaint underpaint(){
            float[] coarse=box(target,4);
            double low=0,high=0;int n=0;
            for(int i=0;i<count;i++)if(valid[i]){
                float value=luma(coarse,sw,i);low+=value;high+=value;n++;
            }
            if(n<60)return null;
            low/=n;high=low;
            // Bimodal photo segmentation: k-means, no model or face template.
            for(int pass=0;pass<9;pass++){
                double loSum=0,hiSum=0;int loN=0,hiN=0;
                float threshold=(float)((low+high)*.5);
                if(pass==0){low*=.63;high=Math.min(1,high*1.38);threshold=(float)((low+high)*.5);}
                for(int i=0;i<count;i++)if(valid[i]){
                    float lum=luma(coarse,sw,i);
                    if(lum<threshold){loSum+=lum;loN++;}else{hiSum+=lum;hiN++;}
                }
                if(loN==0||hiN==0)return null;
                low=loSum/loN;high=hiSum/hiN;
            }
            if(high-low<.09)return null;
            float threshold=(float)(low*.51+high*.49);
            boolean[] mask=new boolean[count];int filled=0;
            float[] mean={0f,0f,0f};
            for(int i=0;i<count;i++)if(valid[i]&&luma(coarse,sw,i)>=threshold){
                mask[i]=true;filled++;
                for(int k=0;k<3;k++)mean[k]+=coarse[3*i+k];
            }
            if(filled<validCount*.16f||filled>validCount*.87f)return null;
            // Morphological majority keeps only broad photographic masses;
            // stray skin pixels/beard highlights are not punched as islands.
            for(int pass=0;pass<3;pass++){
                boolean[] next=mask.clone();
                for(int y=1;y<sh-1;y++)for(int x=1;x<sw-1;x++){
                    int i=y*sw+x;if(!valid[i]){next[i]=false;continue;}
                    int count=0;
                    for(int yy=y-1;yy<=y+1;yy++)for(int xx=x-1;xx<=x+1;xx++)
                        if(mask[yy*sw+xx])count++;
                    if(count>=6)next[i]=true;
                    else if(count<=3)next[i]=false;
                }
                mask=next;
            }
            // Fill photographic shadow holes in the skin underpaint; the
            // actual dark pupil/lip marks belong to later pigment sheets.
            // A disk closing is not a tile shape; it only prevents giant
            // empty holes from appearing where a realistic eye should be.
            int closeR=Math.max(4,Math.round(sw*.065f));
            boolean[] grown=new boolean[count];
            for(int y=0;y<sh;y++)for(int x=0;x<sw;x++)if(mask[y*sw+x]){
                for(int dy=-closeR;dy<=closeR;dy++)for(int dx=-closeR;dx<=closeR;dx++){
                    if(dx*dx+dy*dy>closeR*closeR)continue;
                    int xx=x+dx,yy=y+dy;
                    if(xx>=0&&yy>=0&&xx<sw&&yy<sh&&valid[yy*sw+xx])grown[yy*sw+xx]=true;
                }
            }
            boolean[] closed=new boolean[count];
            for(int y=0;y<sh;y++)for(int x=0;x<sw;x++)if(valid[y*sw+x]){
                boolean safe=true;
                for(int dy=-closeR;dy<=closeR&&safe;dy++)for(int dx=-closeR;dx<=closeR;dx++){
                    if(dx*dx+dy*dy>closeR*closeR)continue;
                    int xx=x+dx,yy=y+dy;
                    if(xx<0||yy<0||xx>=sw||yy>=sh||!grown[yy*sw+xx]){safe=false;break;}
                }
                closed[y*sw+x]=safe;
            }
            mask=closed;
            for(int k=0;k<3;k++)mean[k]/=filled;
            int best=-1;float error=Float.POSITIVE_INFINITY;
            float baseError=diff(mean,palette[0]);
            for(int l=1;l<colors;l++){
                float d=diff(mean,palette[l]);
                if(d<error){best=l;error=d;}
            }
            if(best<1||error>=baseError*.79f)return null;
            return new PainterlyUnderpaint(best,mask);
        }
        /** A second, spatially coherent underpaint captures midtone facial
         * masses (shadow-side cheeks, brow, neck) without confetti. */
        PainterlyUnderpaint underpaintMidtones(PainterlyUnderpaint highMass){
            if(highMass==null)return null;
            float[] coarse=box(target,4);
            double low=.12,high=.42;
            for(int k=0;k<8;k++){
                double loSum=0,hiSum=0;int nLo=0,nHi=0;
                float threshold=(float)((low+high)*.5);
                for(int i=0;i<count;i++)if(valid[i]){
                    float lum=luma(coarse,sw,i);
                    if(lum<threshold){loSum+=lum;nLo++;}else{hiSum+=lum;nHi++;}
                }
                if(nLo==0||nHi==0)return null;
                low=loSum/nLo;high=hiSum/nHi;
            }
            float cutoff=(float)(low+(high-low)*.30);
            boolean[] mask=new boolean[count];
            float[] mean={0f,0f,0f};int filled=0;
            for(int i=0;i<count;i++)if(valid[i]&&!highMass.mask[i]&&
                    luma(coarse,sw,i)>=cutoff){
                mask[i]=true;filled++;
                for(int c=0;c<3;c++)mean[c]+=coarse[3*i+c];
            }
            if(filled<validCount*.08f||filled>validCount*.70f)return null;
            for(int pass=0;pass<2;pass++){
                boolean[] next=mask.clone();
                for(int y=1;y<sh-1;y++)for(int x=1;x<sw-1;x++){
                    int i=y*sw+x;if(!valid[i]||highMass.mask[i]){next[i]=false;continue;}
                    int count=0;
                    for(int yy=y-1;yy<=y+1;yy++)for(int xx=x-1;xx<=x+1;xx++)
                        if(mask[yy*sw+xx])count++;
                    next[i]=count>=5;
                }
                mask=next;
            }
            for(int c=0;c<3;c++)mean[c]/=filled;
            float baseline=diff(mean,palette[0]);
            int best=-1;float err=Float.POSITIVE_INFINITY;
            for(int l=1;l<colors;l++){
                if(l==highMass.layer)continue;
                float score=diff(mean,palette[l]);
                if(score<err){err=score;best=l;}
            }
            if(best<0||err>baseline*.93f)return null;
            return new PainterlyUnderpaint(best,mask);
        }
        /** Underpaint adjacency is a photo-derived proxy for perceptually
         * important shadows (eyes, nose, lips) without a face detector. */
        void emphasizeNearUnderpaint(PainterlyUnderpaint mass){
            int[] distance=new int[count];java.util.Arrays.fill(distance,9999);
            int[] queue=new int[count];int head=0,tail=0;
            for(int i=0;i<count;i++)if(mass.mask[i]){
                distance[i]=0;queue[tail++]=i;
            }
            int horizon=Math.max(8,Math.round(sw*.09f));
            while(head<tail){
                int i=queue[head++];int d=distance[i];
                if(d>=horizon)continue;
                int x=i%sw,y=i/sw;
                for(int n=0;n<4;n++){
                    int xx=x+(n==0?-1:n==1?1:0),yy=y+(n==2?-1:n==3?1:0);
                    if(xx<0||xx>=sw||yy<0||yy>=sh)continue;
                    int k=yy*sw+xx;
                    if(!valid[k]||distance[k]<=d+1)continue;
                    distance[k]=d+1;queue[tail++]=k;
                }
            }
            for(int i=0;i<count;i++)if(valid[i]&&distance[i]<horizon){
                float near=1f-distance[i]/(float)horizon;
                float dark=1f-luma(target,sw,i);
                weight[i]*=1f+near*2.9f*dark;
            }
        }
        /** Trace the region at preview resolution then enforce the exact Snip. */
        java.util.List<RasterRegionClipper.Region> underpaintRegions(PainterlyUnderpaint patch,
                int[] source,int w,int h,boolean[] sourceMask,float minMm,float widthMm){
            int[] synthetic=new int[count];
            boolean[] mask=patch.mask;
            for(int i=0;i<count;i++)synthetic[i]=mask[i]?0xff555555:0;
            float[] rect={0,0,sw,0,sw,sh,0,sh};
            java.util.List<RasterRegionClipper.Region> raw=RasterRegionClipper.clip(
                    rect,null,synthetic,null,sw,sh,0,0,sw,sh,0,0,sw,sh,
                    widthMm/sw,Math.max(minMm,1.2f));
            java.util.ArrayList<RasterRegionClipper.Region> out=new java.util.ArrayList<>();
            for(RasterRegionClipper.Region region:raw){
                float[] unscaled=region.outer;
                float[] large=scaleLoop(unscaled,w/(float)sw,h/(float)sh);
                if(polygonArea(large)*widthMm/w*widthMm/w<12)continue;
                float[] b=polygonBounds(large);
                java.util.List<RasterRegionClipper.Region> clipped=RasterRegionClipper.clip(
                        large,null,source,sourceMask,w,h,
                        Math.max(0,(int)Math.floor(b[0])),Math.max(0,(int)Math.floor(b[1])),
                        Math.min(w,(int)Math.ceil(b[2])),Math.min(h,(int)Math.ceil(b[3])),
                        b[0],b[1],b[2],b[3],widthMm/w,minMm);
                for(RasterRegionClipper.Region r:clipped){
                    if(polygonArea(r.outer)*widthMm/w*widthMm/w>20&&
                        StyleKit.selfIntersections(r.outer,1)==0)out.add(r);
                }
                if(out.size()>=6)break;
            }
            return out;
        }
        void paintUnderpaint(float[] polygon,java.util.List<float[]> holes,float sx,float sy,int layer){
            float[] b=polygonBounds(polygon);
            int x0=Math.max(0,(int)Math.floor(b[0]/sx)),x1=Math.min(sw,(int)Math.ceil(b[2]/sx)),
                y0=Math.max(0,(int)Math.floor(b[1]/sy)),y1=Math.min(sh,(int)Math.ceil(b[3]/sy));
            for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){
                int i=y*sw+x;if(!valid[i])continue;
                float xx=(x+.5f)*sx,yy=(y+.5f)*sy;
                if(!inPoly(polygon,xx,yy))continue;
                boolean hole=false;
                for(float[] h:holes)if(inPoly(h,xx,yy)){hole=true;break;}
                if(hole)continue;
                for(int k=0;k<3;k++)display[3*i+k]=palette[layer][k];
            }
        }
        private float luma(float[] array,int w,int i){
            if(i<0||i>=count||!valid[i])return 0f;
            return .2126f*array[3*i]+.7152f*array[3*i+1]+.0722f*array[3*i+2];
        }
        private float[] box(float[] rgb,int radius){
            float[] result=new float[count*3];
            // Mask-aware summed-area tables, O(1) per output pixel/scale.
            int stride=sw+1;int n=(sw+1)*(sh+1);
            float[][] sums=new float[3][n];int[] counts=new int[n];
            for(int y=0;y<sh;y++){
                float[] row={0,0,0};int nrow=0;
                for(int x=0;x<sw;x++){
                    int i=y*sw+x,p=(y+1)*stride+x+1;
                    if(valid[i]){
                        for(int k=0;k<3;k++)row[k]+=rgb[3*i+k];
                        nrow++;
                    }
                    for(int k=0;k<3;k++)sums[k][p]=row[k]+sums[k][p-stride];
                    counts[p]=nrow+counts[p-stride];
                }
            }
            for(int y=0;y<sh;y++)for(int x=0;x<sw;x++){
                int i=y*sw+x;if(!valid[i])continue;
                int x0=Math.max(0,x-radius),x1=Math.min(sw,x+radius+1),
                    y0=Math.max(0,y-radius),y1=Math.min(sh,y+radius+1);
                int a=y0*stride+x0,b=y0*stride+x1,c=y1*stride+x0,d=y1*stride+x1;
                int nn=counts[d]-counts[b]-counts[c]+counts[a];
                for(int k=0;k<3;k++)result[3*i+k]=nn>0?
                        (sums[k][d]-sums[k][b]-sums[k][c]+sums[k][a])/nn:rgb[3*i+k];
            }
            return result;
        }
        private double squared(int idx,float[] pal){
            int i=idx*3;
            double dr=target[i]-pal[0],dg=target[i+1]-pal[1],db=target[i+2]-pal[2];
            return .2126*dr*dr+.7152*dg*dg+.0722*db*db;
        }
        private double currentError(int idx){
            int i=idx*3;
            double dr=target[i]-display[i],dg=target[i+1]-display[i+1],db=target[i+2]-display[i+2];
            return .2126*dr*dr+.7152*dg*dg+.0722*db*db;
        }
        double gain(float[] polygon,int layer){
            float[] b=polygonBounds(polygon);
            int x0=Math.max(0,(int)Math.floor(b[0])),x1=Math.min(sw,(int)Math.ceil(b[2])),
                y0=Math.max(0,(int)Math.floor(b[1])),y1=Math.min(sh,(int)Math.ceil(b[3]));
            if(x0==x1||y0==y1)return 0;
            double score=0;int active=0,cut=0;
            float[] pal=palette[layer];
            for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){
                int i=y*sw+x;
                if(!inPoly(polygon,x+.5f,y+.5f))continue;
                cut++;if(!valid[i])continue;
                active++;
                score+=weight[i]*(currentError(i)-squared(i,pal));
            }
            // Punish painting outside the real photographic silhouette and
            // don't accept only a few valid pixels inside a mostly empty mark.
            if(active<2||active<cut*.85f)return -1;
            return score;
        }
        double minimumGain(float[] polygon,int pass){
            // Physically costly pieces require a minimum objective reduction.
            // Smaller last-pass strokes may target subtle but meaningful edges.
            float[] b=polygonBounds(polygon);double area=(b[2]-b[0])*(b[3]-b[1]);
            return area*(pass==0?.0021:pass==1?.0014:pass==2?.00075:.00060);
        }
        void paint(float[] polygon,int layer){
            float[] b=polygonBounds(polygon);
            int x0=Math.max(0,(int)Math.floor(b[0])),x1=Math.min(sw,(int)Math.ceil(b[2])),
                y0=Math.max(0,(int)Math.floor(b[1])),y1=Math.min(sh,(int)Math.ceil(b[3]));
            for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){
                int i=y*sw+x;
                if(valid[i]&&inPoly(polygon,x+.5f,y+.5f)){
                    for(int k=0;k<3;k++)display[3*i+k]=palette[layer][k];
                }
            }
        }
    }
    private LayeredCellPortraitEngine(){}
}

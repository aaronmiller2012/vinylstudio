package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Photo-colored polygon tessellations, designed for opaque stacked vinyl.
 * Only filled, closed polygon loops are exported. No Android dependencies.
 * The base geometry is a shared gapless tiling; insetting creates physical weed gaps.
 * Cairo-type preset is a periodic pentagon-weave approximation, not the
 * mathematically exact dual of the snub-square (3.3.4.3.4) tiling.
 */
public final class TessellationMotifEngine {
    private TessellationMotifEngine() { }
    public static final int SQUARE=0, TRIANGLE=1, HEXAGON=2, TRIHEX=3,
        RHOMBILLE=4, OCTAGON_SQUARE=5, PENTAGON_WEAVE=6, RHOMBITRIHEX=7;
    // IDs 0..7 are persistent project variant IDs: never renumber.
    public static final int TRUNCATED_HEX=8, TRUNCATED_TRIHEX=9,
        SNUB_TRIHEX=10, KISRHOMBILLE=11, TETRAKIS=12,
        ELONGATED_TRIANGLE=13, DELTOIDAL_TRIHEX=14, TRIAKIS=15,
        FLORET_PENTAGON=16, PRISMATIC_PENTAGON=17,
        RUNNING_BOND=18, BASKET_WEAVE=19, HERRINGBONE=20, PYTHAGOREAN=21;
    // IDs 18..21 are retired saved-project variants: leave them unavailable.
    // Seven new photographic reference geometries receive IDs 22..28.
    public static final int INTERLOCKING_BASKET=22, FRAMED_SQUARE=23,
        INTERLOCKING_DUMBBELL=24, TRIANGULAR_RIBBONS=25,
        STEPPED_CROSS=26, DIAMOND_STAR=27, ISOMETRIC_RIBBONS=28;
    // New reference IDs are append-only to preserve saved projects.
    public static final int CROSSHAIR_GRID=29, ROUNDED_HEX_NETWORK=30,
        DIAGONAL_FRAMED_SQUARE=31, CIRCLE_CHAIN_LATTICE=32,
        DIAMOND_ARC_ROSETTE=33, NESTED_HEXAGONS=34,
        INSET_RHOMBUS_CUBES=35, OCTAGON_DIAMOND_LINKS=36;
    // IDs 24, 27, 32, 33 and 36 are retired by user request (Beta 8).
    // Stable tombstones prevent old saved variants from silently changing meaning.
    public static final int ACTIVE_PATTERN_COUNT=28;
    public static final int PATTERN_SLOT_COUNT=OCTAGON_DIAMOND_LINKS+1;
    public static boolean retiredPattern(int pattern){
        return (pattern>=RUNNING_BOND && pattern<=PYTHAGOREAN) ||
            pattern==INTERLOCKING_DUMBBELL || pattern==DIAMOND_STAR ||
            pattern==CIRCLE_CHAIN_LATTICE || pattern==DIAMOND_ARC_ROSETTE ||
            pattern==OCTAGON_DIAMOND_LINKS;
    }
    public static boolean selectablePattern(int pattern){
        return pattern>=0 && pattern<PATTERN_SLOT_COUNT && !retiredPattern(pattern);
    }
    public static final String[] PATTERNS={"Square","Equilateral triangles","Hexagon honeycomb",
        "Trihexagonal","Rhombille diamonds","Octagon + square",
        "Pentagonal weave","Rhombitrihexagonal",
        "Truncated hexagonal (3.12.12)","Truncated trihexagonal (4.6.12)",
        "Snub trihexagonal (3.3.3.3.6)","Kisrhombille (30-60-90)",
        "Tetrakis square","Elongated triangular (3.3.3.4.4)",
        "Deltoidal trihexagonal","Triakis triangular","Floret pentagonal",
        "Prismatic pentagonal","Running bond","Basket weave","Herringbone",
        "Pythagorean squares", "Interlocking Basketweave", "Framed Square Lattice",
        "Interlocking Dumbbells", "Triangular Ribbon Weave", "Stepped Cross Interlock",
        "Diamond Star Lattice", "Isometric Ribbon Cubes",
        "Crosshair Grid", "Rounded Hexagon Network", "Diagonal Framed Squares",
        "Circle Chain Lattice", "Diamond Arc Rosette", "Nested Hexagon Frames",
        "Inset Rhombus Cubes", "Octagon Diamond Links"};
    // Reserved legacy motif IDs: old project JSON may still contain an index.
    // They no longer select shapes or consume a dedicated physical sheet.
    /** Count of reserved legacy motif IDs accepted when reading old project JSON. */
    public static final int LEGACY_MOTIF_COUNT=8;
    @Deprecated public static final String[] MOTIFS={"Photo adaptive","Circles","Diamonds",
        "Hexagons","Stars","Rings","Capsules","Flowers"};
    private static final int MAX_CELLS=2300, MAX_PATHS=4800;
    private static final double ROOT3=Math.sqrt(3.0);

    public static final class Options {
        public float widthMm=152.4f, minimumMm=.254f, gapMm=.22f;
        public int detail=85, pattern=TRIHEX, motif=0, layerCount=8;
        // The two legacy fields are accepted for compatibility; neither enables motifs.
        public boolean solidBase=true, extraMotifs=false;
        public int[] palette={0xff251d23,0xff6a403a,0xffad6a55,0xffd7a88b,
            0xfff1d3b9,0xff6b7890,0xff846c89,0xff261e29};
        public int paperArgb=0xfffaf9f5;
        public boolean[] allowed;
        public Options copy(){Options n=new Options();n.widthMm=widthMm;n.minimumMm=minimumMm;
            n.gapMm=gapMm;n.detail=detail;n.pattern=pattern;n.motif=motif;
            n.layerCount=layerCount;n.solidBase=solidBase;n.extraMotifs=extraMotifs;
            n.palette=palette==null?null:palette.clone();n.paperArgb=paperArgb;
            n.allowed=allowed==null?null:allowed.clone();return n;}
        public void validate(){
            if(!Float.isFinite(widthMm)||widthMm<20||widthMm>2000||
                !Float.isFinite(minimumMm)||minimumMm<.10f||minimumMm>20.1f||
                !Float.isFinite(gapMm)||gapMm<.10f||gapMm>20.1f||
                detail<0||detail>100||!selectablePattern(pattern)||
                motif<0||motif>=LEGACY_MOTIF_COUNT||layerCount<2||layerCount>8||
                palette==null||palette.length<layerCount)
                throw new IllegalArgumentException("Invalid tessellation options");
        }
    }

    private static float[] poly(double... xy){float[] f=new float[xy.length];
        for(int i=0;i<xy.length;i++)f[i]=(float)xy[i];return f;}
    private static float[] regular(double x,double y,double radius,int sides,double phase){
        float[] p=new float[sides*2];for(int i=0;i<sides;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            double a=phase+2*Math.PI*i/sides;p[2*i]=(float)(x+radius*Math.cos(a));
            p[2*i+1]=(float)(y+radius*Math.sin(a));}return p;
    }
    private static float signed(float[] p){double sum=0;int n=p.length/2;
        for(int i=0;i<n;i++){if(((i)&63)==0)CancellationCheckpoint.check();int j=(i+1)%n;
            sum+=(double)p[2*i]*p[2*j+1]-(double)p[2*j]*p[2*i+1];}
        return (float)(.5*sum);
    }
    private static float[] reverse(float[] p){int n=p.length/2;float[] q=new float[p.length];
        for(int i=0;i<n;i++){if(((i)&63)==0)CancellationCheckpoint.check();q[2*i]=p[2*(n-i-1)];q[2*i+1]=p[2*(n-i-1)+1];}return q;}
    /** Polygon intersection with the interior of an oriented edge, offset by distance mm. */
    private static float[] halfPlane(float[] p,double ax,double ay,double bx,double by,double inset){
        int n=p.length/2;if(n<3)return new float[0];
        double dx=bx-ax,dy=by-ay, len=Math.hypot(dx,dy);
        if(len<1e-8)return new float[0];double limit=inset*len;
        float[] output=new float[(2*n+2)*2];int out=0;
        double px=p[2*(n-1)],py=p[2*(n-1)+1];
        double pd=dx*(py-ay)-dy*(px-ax)-limit;
        for(int i=0;i<n;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            double x=p[2*i],y=p[2*i+1],d=dx*(y-ay)-dy*(x-ax)-limit;
            if((pd>=0)!=(d>=0)){
                double t=pd/(pd-d);output[out++]=(float)(px+(x-px)*t);
                output[out++]=(float)(py+(y-py)*t);
            }
            if(d>=-1e-6){output[out++]=(float)x;output[out++]=(float)y;}
            px=x;py=y;pd=d;
        }
        return Arrays.copyOf(output,out);
    }
    static float[] inset(float[] src,float mm){
        if(src.length<6)return new float[0];
        float[] oriented=signed(src)<0?reverse(src):src.clone();
        float[] p=oriented;
        for(int i=0,n=oriented.length/2;i<n;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            int j=(i+1)%n;p=halfPlane(p,oriented[2*i],oriented[2*i+1],oriented[2*j],oriented[2*j+1],mm);
            if(p.length<6)return new float[0];
        }
        return p;
    }
    private static float[] clipTo(float[] p,float[] region){
        float[] q=signed(region)<0?reverse(region):region;
        for(int i=0,n=q.length/2;i<n;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            int j=(i+1)%n;
            p=halfPlane(p,q[2*i],q[2*i+1],q[2*j],q[2*j+1],0);
            if(p.length<6)return new float[0];
        }return p;
    }
    private static float[] rect(float x0,float y0,float x1,float y1){return new float[]{x0,y0,x1,y0,x1,y1,x0,y1};}
    private static void append(List<float[]> dest,float[] p,HashSet<String> seen){
        if(p.length<6||Math.abs(signed(p))<.05f)return;
        float cx=0,cy=0;for(int i=0;i<p.length;i+=2){if(((i)&63)==0)CancellationCheckpoint.check();cx+=p[i];cy+=p[i+1];}
        cx/=p.length/2;cy/=p.length/2;
        // Periodic constructions visit shared edges/vertices multiple times.
        String key=p.length+":"+Math.round(cx*1000)+":"+Math.round(cy*1000);
        if(seen.add(key))dest.add(p);
    }
    /** Builds exact periodic triangular / hexagonal / square-lattice tilings. */
    static List<float[]> tiles(int pattern,float w,float h,float unit){
        return GeometryTemplateCache.get(GeometryTemplateCache.key("tess",pattern,w,h,unit),()->buildTiles(pattern,w,h,unit));
    }
    private static List<float[]> buildTiles(int pattern,float w,float h,float unit){
        if(pattern>=CROSSHAIR_GRID)
            return TessellationSecondReferenceLayouts.tiles(pattern,w,h,unit);
        if(pattern>=INTERLOCKING_BASKET)
            return TessellationReferenceLayouts.tiles(pattern,w,h,unit);
        if(pattern>=TRUNCATED_HEX)
            return TessellationExtendedLayouts.tiles(pattern,w,h,unit);
        ArrayList<float[]> out=new ArrayList<>();HashSet<String> seen=new HashSet<>();
        if(pattern==SQUARE||pattern==OCTAGON_SQUARE||pattern==PENTAGON_WEAVE){
            int nx=(int)Math.ceil(w/unit)+2,ny=(int)Math.ceil(h/unit)+2;
            for(int y=-2;y<=ny;y++){
                StyleKit.checkCancelled();
                for(int x=-2;x<=nx;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                    double a=x*unit,b=y*unit,s=unit;
                    if(pattern==SQUARE)append(out,poly(a,b,a+s,b,a+s,b+s,a,b+s),seen);
                    else if(pattern==OCTAGON_SQUARE){
                        double k=s/(2+Math.sqrt(2));
                        append(out,poly(a+k,b,a+s-k,b,a+s,b+k,a+s,b+s-k,
                            a+s-k,b+s,a+k,b+s,a,b+s-k,a,b+k),seen);
                        append(out,poly(a,b-k,a+k,b,a,b+k,a-k,b),seen);
                    }else{
                        // Two edge-matched pentagons per square unit. A convex
                        // classical Cairo pentagon requires a snub-square dual;
                        // this is deliberately a different pentagonal weave.
                        double m=a+s*.5, k=s*.20*(1-2*(y&1));
                        append(out,poly(a,b,m,b,m+k,b+s*.5,m,b+s,a,b+s),seen);
                        append(out,poly(m,b,a+s,b,a+s,b+s,m,b+s,m+k,b+s*.5),seen);
                    }
                }
            }
        }else if(pattern==TRIANGLE){
            double dy=unit*ROOT3*.5;
            int nx=(int)Math.ceil(w/unit)+4,ny=(int)Math.ceil(h/dy)+4;
            for(int row=-3;row<=ny;row++){
                StyleKit.checkCancelled();double y=row*dy, shift=(row&1)*unit*.5;
                for(int col=-3;col<=nx;col++){if(((col)&63)==0)CancellationCheckpoint.check();
                    double x=col*unit+shift;
                    // Triangular lattice edges: two up/down equilateral triangles per parallelogram.
                    double next=x+((row&1)==0?unit*.5:-unit*.5),y2=y+dy;
                    if((row&1)==0){
                        append(out,poly(x,y,x+unit,y,next,y2),seen);
                        append(out,poly(x+unit,y,next+unit,y2,next,y2),seen);
                    }else{
                        append(out,poly(x,y,x+unit,y,next+unit,y2),seen);
                        append(out,poly(x,y,next+unit,y2,next,y2),seen);
                    }
                }
            }
        }else{
            double r=unit,dy=ROOT3*r;
            int nx=(int)Math.ceil(w/(1.5*r))+5,ny=(int)Math.ceil(h/dy)+6;
            double ratio=1/(1+1/ROOT3);
            for(int q=-4;q<=nx;q++){
                StyleKit.checkCancelled();
                for(int row=-4;row<=ny;row++){if(((row)&63)==0)CancellationCheckpoint.check();
                    double cx=1.5*r*q,cy=dy*(row+(q&1)*.5);
                    float[] hex=regular(cx,cy,r,6,0);
                    if(pattern==HEXAGON){append(out,hex,seen);continue;}
                    if(pattern==RHOMBILLE){
                        for(int k=0;k<6;k+=2){if(((k)&63)==0)CancellationCheckpoint.check();int a=k,b=(k+1)%6,c=(k+2)%6;
                            append(out,poly(cx,cy,hex[2*a],hex[2*a+1],
                                hex[2*b],hex[2*b+1],hex[2*c],hex[2*c+1]),seen);}
                        continue;
                    }
                    if(pattern==TRIHEX){
                        float[] small=new float[12];
                        for(int k=0;k<6;k++){if(((k)&63)==0)CancellationCheckpoint.check();int j=(k+1)%6;
                            small[2*k]=.5f*(hex[2*k]+hex[2*j]);
                            small[2*k+1]=.5f*(hex[2*k+1]+hex[2*j+1]);}
                        append(out,small,seen);
                        for(int k=0;k<6;k++){if(((k)&63)==0)CancellationCheckpoint.check();
                            int prev=(k+5)%6,next=(k+1)%6;
                            double vx=hex[2*k],vy=hex[2*k+1];
                            append(out,poly((vx+hex[2*prev])*.5,(vy+hex[2*prev+1])*.5,
                                (vx+hex[2*next])*.5,(vy+hex[2*next+1])*.5,
                                vx+(vx-cx)*.5,vy+(vy-cy)*.5),seen);
                        }
                    }else if(pattern==RHOMBITRIHEX){
                        float[] small=new float[12];
                        for(int k=0;k<6;k++){if(((k)&63)==0)CancellationCheckpoint.check();
                            small[2*k]=(float)(cx+ratio*(hex[2*k]-cx));
                            small[2*k+1]=(float)(cy+ratio*(hex[2*k+1]-cy));
                        }
                        append(out,small,seen);
                        for(int k=0;k<6;k++){if(((k)&63)==0)CancellationCheckpoint.check();
                            int next=(k+1)%6;
                            double angle=(k+.5)*Math.PI/3;
                            double ox=cx+ROOT3*r*Math.cos(angle),oy=cy+ROOT3*r*Math.sin(angle);
                            append(out,poly(small[2*k],small[2*k+1],small[2*next],small[2*next+1],
                                ox+ratio*(hex[2*next]-ox),oy+ratio*(hex[2*next+1]-oy),
                                ox+ratio*(hex[2*k]-ox),oy+ratio*(hex[2*k+1]-oy)),seen);
                            double angPrev=(k-.5)*Math.PI/3;
                            double o2x=cx+ROOT3*r*Math.cos(angPrev),o2y=cy+ROOT3*r*Math.sin(angPrev);
                            double vx=hex[2*k],vy=hex[2*k+1];
                            append(out,poly(small[2*k],small[2*k+1],
                                ox+ratio*(vx-ox),oy+ratio*(vy-oy),
                                o2x+ratio*(vx-o2x),o2y+ratio*(vy-o2y)),seen);
                        }
                    }
                }
            }
        }
        return out;
    }
    private static float[] bounds(float[] p){float x0=Float.POSITIVE_INFINITY,y0=x0,x1=-x0,y1=-x0;
        for(int i=0;i<p.length;i+=2){if(((i)&63)==0)CancellationCheckpoint.check();x0=Math.min(x0,p[i]);y0=Math.min(y0,p[i+1]);
            x1=Math.max(x1,p[i]);y1=Math.max(y1,p[i+1]);}
        return new float[]{x0,y0,x1,y1};}
    private static float centroid(float[] p,boolean horizontal){double s=0;for(int i=horizontal?0:1;i<p.length;i+=2)s+=p[i];return (float)(s/(p.length/2));}
    private static int photoRgb(StyleKit.Source s,float[] polygon,float detailEmphasis){
        float[] box=bounds(polygon);
        double r=0,g=0,b=0,weightTotal=0;
        // Existing layouts retain their original uniform area mean. Reference
        // motifs use a mild centre-weighted area sample to preserve eyes,
        // mouth and other localized contrast without introducing dithering or
        // changing a user's chosen palette. Samples outside the Snip or the
        // polygon never influence tone (including at the page perimeter).
        for(int y=0;y<7;y++)for(int x=0;x<7;x++){
            if((x&63)==0)CancellationCheckpoint.check();
            float px=box[0]+(x+.5f)/7f*(box[2]-box[0]);
            float py=box[1]+(y+.5f)/7f*(box[3]-box[1]);
            if(px<0||py<0||px>=s.widthMm||py>=s.heightMm||
                !s.validAt(px,py)||!MaskedFacetClipper.inside(polygon,px,py))continue;
            int color=s.colorAt(px,py);
            double dx=(x-3.0)/3.5,dy=(y-3.0)/3.5;
            double weight=1.0+1.5*detailEmphasis*Math.max(0,1.0-(dx*dx+dy*dy));
            r+=((color>>>16)&255)*weight;
            g+=((color>>>8)&255)*weight;
            b+=(color&255)*weight;
            weightTotal+=weight;
        }
        if(weightTotal==0){float x=centroid(polygon,true),y=centroid(polygon,false);
            return s.validAt(x,y)?s.colorAt(x,y):0;}
        return 0xff000000|((int)(r/weightTotal)<<16)|
            ((int)(g/weightTotal)<<8)|(int)(b/weightTotal);
    }
    private static void addMaskClipped(StyleKit.Layer layer,float[] polygon,StyleKit.Source src){
        addMaskClipped(layer,polygon,src,0f);
    }
    /** Pieces narrower than the cut minimum (edge slivers from the page or
     * Snip clip, very small tiles) can be neither cut nor weeded: skip them. */
    private static void addMaskClipped(StyleKit.Layer layer,float[] polygon,StyleKit.Source src,float minimumMm){
        if(polygon.length<6||Math.abs(signed(polygon))<.015)return;
        if(VinylStyleSuite.fullyInside(polygon,src)){
            if(minimumMm<=0||!VinylEngine.tinyPiece(polygon,minimumMm))layer.loops.add(polygon);
            return;
        }
        for(float[] piece:MaskedFacetClipper.intersect(polygon,src))
            if(minimumMm<=0||cuttableEdgePiece(piece,minimumMm))layer.loops.add(piece);
    }
    /** A silhouette-clipped piece is re-traced on the photo grid; keep it only
     * if it is, on its own, a cuttable piece of vinyl (no sub-minimum neck). */
    private static boolean cuttableEdgePiece(float[] piece,float minimumMm){
        if(VinylEngine.tinyPiece(piece,minimumMm))return false;
        StyleKit.Result one=new StyleKit.Result(1f,1f);
        StyleKit.Layer layer=new StyleKit.Layer("edge piece",0);
        layer.loops.add(piece);one.layers.add(layer);
        try{return StyleCutGeometryPreflight.inspect(one,minimumMm,minimumMm,1).isEmpty();}
        catch(IllegalArgumentException invalid){return false;}
    }
    public static StyleKit.Result generate(int[] argb,int w,int h,Options opts){
        if(opts==null)throw new IllegalArgumentException("Options required");opts.validate();Options o=opts.copy();
        StyleKit.Source src=new StyleKit.Source(argb,w,h,o.allowed,o.widthMm);
        if(!src.anyValid())throw new IllegalArgumentException("No visible subject");
        StyleKit.Result res=new StyleKit.Result(src.widthMm,src.heightMm);res.paperArgb=o.paperArgb;
        for(int i=0;i<o.layerCount;i++)res.layers.add(new StyleKit.Layer(
            i==0&&o.solidBase&&o.layerCount>=2?"tessellation-base-1":"tessellation-color-"+(i+1),o.palette[i]));
        boolean base=o.solidBase&&o.layerCount>=2;
        // Every non-base sheet is photographic tile color. Older project motif
        // selections are deliberately ignored rather than stealing a color sheet.
        TessellationToneMapper tonalMapper=new TessellationToneMapper(src,o,base);
        if(base)res.layers.get(0).loops.addAll(VinylStyleSuite.subjectLoops(src));
        // The reference patterns are built from macrocells with 2..9 small
        // pieces each. Beta 5 counted Detail against the sub-cell pitch, so
        // even at 100 the photographed face had only ~100..500 actual pieces.
        // Increase their density progressively, but preserve legacy tessellation
        // geometry, the 0..100 slider contract, physical millimetre dimensions,
        // and the existing 2300-cell/4800-path hard safety budgets.
        final boolean reference=o.pattern>=INTERLOCKING_BASKET;
        final float fractionalDetail=o.detail/100f;
        final float densityBoost=reference?1f+.90f*fractionalDetail:1f;
        float unit=Math.max(2.6f,
            o.widthMm/((10f+21f*fractionalDetail)*densityBoost));
        List<float[]> cells=null;
        for(int tryNum=0;tryNum<9;tryNum++){
            StyleKit.checkCancelled();cells=tiles(o.pattern,src.widthMm,src.heightMm,unit);
            int visible=0;
            for(float[] candidate:cells){CancellationCheckpoint.check();
                float[] b=bounds(candidate);
                if(b[2]>0 && b[0]<src.widthMm && b[3]>0 && b[1]<src.heightMm &&
                   // A silhouette covering 30% of the canvas should not lose
                   // 70% of the available detail budget to empty background.
                   // Legacy layouts keep the exact old full-canvas budget.
                   (!reference || photoRgb(src,candidate,fractionalDetail)!=0))visible++;
            }
            if(visible<=MAX_CELLS-40)break;
            // Preserve all cells by reducing density globally. Never truncate
            // the bottom-right of the portrait at the cut-piece ceiling.
            unit*=Math.max(1.08f,(float)Math.sqrt(visible/(double)(MAX_CELLS-80))*1.05f);
        }
        float[] page=rect(.05f,.05f,src.widthMm-.05f,src.heightMm-.05f);
        int used=0;
        for(float[] raw:cells){CancellationCheckpoint.check();
            if((used&63)==0)StyleKit.checkCancelled();
            float[] cropped=clipTo(raw,page);
            if(cropped.length<6||Math.abs(signed(cropped))<o.minimumMm*o.minimumMm*3)continue;
            int color=photoRgb(src,cropped,reference?fractionalDetail:0f);if(color==0)continue;
            float baseLuma=(.2126f*((color>>>16)&255)+.7152f*((color>>>8)&255)+.0722f*(color&255))/255f;
            int layer=tonalMapper.select(color);
            if(layer==TessellationToneMapper.BASE)continue; // expose matching solid-base vinyl
            // Keep a real edge-parallel physical gap. Short smooth regions show more base color,
            // while important features keep large photo-colored cells.
            float margin=o.gapMm*.5f + Math.max(0,.34f-baseLuma)*o.gapMm*.24f;
            final boolean concave=o.pattern==INTERLOCKING_DUMBBELL ||
                o.pattern==STEPPED_CROSS || o.pattern==DIAMOND_STAR ||
                o.pattern==CROSSHAIR_GRID || o.pattern==DIAMOND_ARC_ROSETTE;
            float[] tile=concave
                ? TessellationReferenceLayouts.insetConcave(cropped,margin)
                : inset(cropped,margin);
            // A tile crossing the Snip/silhouette edge is re-traced on the photo
            // pixel grid, which can move its edges by up to half a pixel and
            // eat the weed gap to its neighbour. Pull such tiles in further.
            if(tile.length>=6&&!VinylStyleSuite.fullyInside(tile,src)){
                float extra=margin+src.mmPerPx*.75f;
                tile=concave?TessellationReferenceLayouts.insetConcave(cropped,extra):inset(cropped,extra);
            }
            if(tile.length<6||Math.abs(signed(tile))<o.minimumMm*o.minimumMm*3)continue;
            addMaskClipped(res.layers.get(layer),tile,src,o.minimumMm);used++;
            if(used>MAX_CELLS)
                throw new IllegalArgumentException("Tessellation exceeds cell budget; reduce Detail");
        }
        // Bound each physical SVG sheet; never publish an incomplete excess-path cut sheet.
        for(StyleKit.Layer l:res.layers)if(l.loops.size()>MAX_PATHS)
            throw new IllegalArgumentException("Tessellation exceeds cut limit; reduce Detail");
        res.summary="Tessellation Portrait: "+PATTERNS[o.pattern]+", "+used+
            " photo-colored tiles, "+o.layerCount+" sheets";
        return res;
    }
}

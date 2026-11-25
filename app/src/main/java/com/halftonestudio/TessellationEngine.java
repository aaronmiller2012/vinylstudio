package com.halftonestudio;

import java.util.*;

/** Deterministic vector-first photo tessellation. No Android/runtime dependencies.
 * Each cell is one nonoverlapping, closed vinyl contour. Different layers own
 * disjoint cells from a single shared layout; gaps are actual empty substrate.
 */
public final class TessellationEngine {
    public static final int VORONOI=0, GRID_MOTIF=1, STIPPLE=2;
    public static final int MAX_TILES=1800, MAX_PATHS_PER_LAYER=4500;
    public static final class Options {
        public int style=VORONOI, layerCount=5, detail=52;
        public MultiColorStippleEngine.Options stippleOptions=null;
        public float gapMm=.36f, widthMm=152.4f, edgeEmphasis=.65f,outerSpacingMm=8f;
        public boolean[] allowedPixels=null; // optional physical snip/crop mask
        public int[] colors={0xff24252c,0xff71485e,0xffb36e89,0xffe3aa9e,0xfff1d6ba,0xff7695b1,0xff694b9d,0xffd87b45};
        public boolean registration=true;
        public int markStyle=RegistrationMarks.CROSS;
        public float markSizeMm=2.5f;
        public float registrationClearanceMm=1f,minimumCutMm=.8f;
        public float[][] marks=null;
        /** Circle crop: automatic marks sit in the unused corners, as the editor preview draws them (ADR-0001). */
        public boolean circleCrop=false;
        public Options copy(){Options a=new Options();a.style=style;a.layerCount=layerCount;a.detail=detail;
            a.gapMm=gapMm;a.widthMm=widthMm;a.edgeEmphasis=edgeEmphasis;a.outerSpacingMm=outerSpacingMm;a.colors=colors.clone();
            a.allowedPixels=allowedPixels;a.stippleOptions=stippleOptions==null?null:stippleOptions.copy();a.registration=registration;a.markStyle=markStyle;a.markSizeMm=markSizeMm;
            a.registrationClearanceMm=registrationClearanceMm;a.minimumCutMm=minimumCutMm;
            a.marks=RegistrationMarks.copyPositions(marks);a.circleCrop=circleCrop;return a;}
    }
    public static final class Tile {
        public final float[] xy;public final float[][] holes;public final int layer;public final float tone;
        Tile(float[] xy,int layer,float tone){this(xy,new float[0][],layer,tone);}
        Tile(float[] xy,float[][] holes,int layer,float tone){this.xy=xy;this.holes=holes;this.layer=layer;this.tone=tone;}
    }
    public static final class Result {
        public final int width,height;public final Options options;public final List<Tile> tiles;
        public final int[] counts;public final boolean[] nonempty;
        public final MultiColorStippleEngine.Result stipple;
        Result(int w,int h,Options o,List<Tile> tiles){this(w,h,o,tiles,null);}
        Result(int w,int h,Options o,List<Tile> tiles,MultiColorStippleEngine.Result dots){width=w;height=h;options=o.copy();this.stipple=dots;this.tiles=Collections.unmodifiableList(tiles);
            counts=new int[o.layerCount];nonempty=new boolean[o.layerCount];for(Tile t:tiles){CancellationCheckpoint.check();counts[t.layer]+=1+t.holes.length;nonempty[t.layer]=true;}}
        /** Registration-only revision: export workers keep the old result, and
         * no geometry, masks, color assignments or contour counts are rebuilt. */
        private Result(Result old,Options revised){
            width=old.width;height=old.height;options=revised;tiles=old.tiles;
            counts=old.counts;nonempty=old.nonempty;stipple=old.stipple;
        }
        Result withRegistration(float[][] marks,boolean registration,int style,float size,float clearance){
            Options next=options.copy();next.marks=RegistrationMarks.copyPositions(marks);
            next.registration=registration;next.markStyle=style;next.markSizeMm=size;
            next.registrationClearanceMm=clearance;return new Result(this,next);
        }
        public int populated(){int n=0;for(boolean b:nonempty)if(b)n++;return n;}
        public String summary(){return (stipple==null?"Tessellation ":"Stipple ")+populated()+"/"+options.layerCount+" layers · "+tiles.size()+" closed vector tiles · max "+maxPaths()+" cuts/layer";}
        public int maxPaths(){int n=0;for(int i:counts)n=Math.max(n,i+(options.registration?4:0));return n;}
        /** ADR-0001 frame shared with the Snip base sheet and the editor preview. */
        public SheetFrame frame(){final float scale=options.widthMm/width;
            return SheetFrame.of(options.widthMm,height*scale,options.registration,options.markSizeMm,
                options.registrationClearanceMm,options.marks,options.outerSpacingMm,options.circleCrop);}
        public String svg(int layer){if(layer<0||layer>=counts.length)throw new IllegalArgumentException("Invalid layer");
            final float scale=options.widthMm/width, hMm=height*scale;
            final SheetFrame reg=options.registration?frame():null;
            if(reg!=null){String unsafe=RegistrationCutGeometry.tessellation(this).firstIssue();
                if(unsafe!=null)throw new IllegalArgumentException(unsafe);}
            final float marksPadding=reg==null?options.outerSpacingMm:reg.paddingMm;
            final float totalW=options.widthMm+2*marksPadding,totalH=hMm+2*marksPadding;
            StringBuilder s=new StringBuilder(400+counts[layer]*115);
            s.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(f(totalW)).append("mm\" height=\"").append(f(totalH))
                .append("mm\" viewBox=\"0 0 ").append(f(totalW)).append(' ').append(f(totalH)).append("\">\n");
            if(stipple==null) s.append("<g fill=\"").append(String.format(Locale.US,"#%06X",options.colors[layer]&0xffffff)).append("\" stroke=\"none\">\n");
            else s.append("<g id=\"swatch-").append(String.format(Locale.US,"%06X",options.colors[layer]&0xffffff))
                .append("-layer-").append(layer+1).append("\" fill=\"")
                .append(String.format(Locale.US,"#%06X",options.colors[layer]&0xffffff)).append("\" stroke=\"none\">\n");
            for(Tile t:tiles){CancellationCheckpoint.check();if(t.layer!=layer)continue;
                if(stipple==null){
                    s.append("<path d=\"M");for(int i=0;i<t.xy.length;i+=2){if(((i)&63)==0)CancellationCheckpoint.check();if(i>0)s.append(" L");
                        s.append(f(marksPadding+t.xy[i]*scale)).append(' ').append(f(marksPadding+t.xy[i+1]*scale));}
                    s.append(" Z\"/>\n");
                }else{
                    s.append("<path fill-rule=\"evenodd\" d=\"");
                    appendRing(s,t.xy,scale,marksPadding);
                    for(float[] hole:t.holes)appendRing(s,hole,scale,marksPadding);
                    s.append("\"/>\n");
                }
            }
            if(reg!=null)for(float[] xy:reg.marks())
                s.append(RegistrationMarks.svgMark(options.markStyle,marksPadding+xy[0],marksPadding+xy[1],options.markSizeMm,options.minimumCutMm));
            return s.append("</g>\n</svg>\n").toString();
        }
        private static void appendRing(StringBuilder s,float[] xy,float scale,float pad){
            s.append("M");for(int i=0;i<xy.length;i+=2){if(((i)&63)==0)CancellationCheckpoint.check();if(i>0)s.append(" L");
                s.append(f(pad+xy[i]*scale)).append(' ').append(f(pad+xy[i+1]*scale));}
            s.append(" Z ");
        }
        public String combinedSvg(){
            String first=svg(0);int start=first.indexOf("<g ");
            if(start<0)throw new IllegalStateException("Missing SVG group");
            StringBuilder out=new StringBuilder(first.substring(0,start));
            for(int position=0;position<options.layerCount;position++){if(((position)&63)==0)CancellationCheckpoint.check();
                int i=stipple==null?position:stipple.options.order[position];
                String single=svg(i);int a=single.indexOf("<g ");int b=single.indexOf("</g>",a);
                if(a>=0&&b>=0)out.append(single,a,b+4).append('\n');
            }
            return out.append("</svg>\n").toString();
        }
        private static String f(float x){return String.format(Locale.US,"%.3f",x);}
    }
    private static class Seed {final float x,y,tone;final int color;
        Seed(float x,float y,float tone,int color){this.x=x;this.y=y;this.tone=tone;this.color=color;}}
    private static float luma(int c){return ((c>>16)&255)*.2126f+((c>>8)&255)*.7152f+(c&255)*.0722f;}
    private static float brightness(int[] px,int w,int h,float x,float y){int ix=Math.min(w-1,Math.max(0,Math.round(x))),iy=Math.min(h-1,Math.max(0,Math.round(y)));return luma(px[iy*w+ix]);}
    /** Local, edge-respecting footprint color. An isolated noisy pixel cannot
     * dominate a whole cut cell, while genuine shirt/iris color borders remain.
     * The source photograph is never recolored or classified by face position. */
    private static int footprintColor(int[] px,int w,int h,float x,float y,float radius){
        int cx=Math.max(0,Math.min(w-1,Math.round(x))),cy=Math.max(0,Math.min(h-1,Math.round(y)));
        int step=Math.max(1,Math.min(8,Math.round(radius)));
        int[] samples=new int[9];int count=0;
        int center=px[cy*w+cx];
        if((center>>>24)<128)return center;
        samples[count++]=center;
        for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)if(dx!=0||dy!=0){
            int ix=Math.max(0,Math.min(w-1,cx+dx*step)),iy=Math.max(0,Math.min(h-1,cy+dy*step));
            int v=px[iy*w+ix];if((v>>>24)>=128)samples[count++]=v;
        }
        // A lone, isolated accent-colored source pixel surrounded by uniform skin
        // should not control an entire motif. This is color evidence, not a skin
        // detection heuristic; a coherent green patch is retained unchanged.
        int source=center,most=0,best=0,nearCenter=0;
        for(int i=0;i<count;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            int candidate=samples[i],matches=0;
            for(int j=0;j<count;j++)if(rgbDistance(candidate,samples[j])<2600)matches++;
            if(i==0)nearCenter=matches;
            if(matches>most){most=matches;best=i;}
        }
        if(nearCenter<=2 && most>=5)source=samples[best];
        int rr=(source>>16)&255,gg=(source>>8)&255,bb=source&255;
        double red=rr*2,green=gg*2,blue=bb*2,total=2;
        for(int i=0;i<count;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            int v=samples[i];int r=(v>>16)&255,g=(v>>8)&255,b=v&255;
            double weight=rgbDistance(source,v)<2600?1.0:.06;
            red+=weight*r;green+=weight*g;blue+=weight*b;total+=weight;
        }
        return 0xff000000|((int)Math.round(red/total)<<16)|((int)Math.round(green/total)<<8)|(int)Math.round(blue/total);
    }
    private static int rgbDistance(int a,int b){
        int dr=((a>>16)&255)-((b>>16)&255),dg=((a>>8)&255)-((b>>8)&255),db=(a&255)-(b&255);
        return dr*dr+dg*dg+db*db;
    }
    private static float edge(int[] px,int w,int h,int x,int y){
        x=Math.max(1,Math.min(w-2,x));y=Math.max(1,Math.min(h-2,y));
        return Math.min(1f,(Math.abs(luma(px[y*w+x-1])-luma(px[y*w+x+1]))+
                Math.abs(luma(px[(y-1)*w+x])-luma(px[(y+1)*w+x])))/140f);
    }
    private static float area(float[] p){double a=0;for(int i=0;i<p.length;i+=2){if(((i)&63)==0)CancellationCheckpoint.check();int j=(i+2)%p.length;a+=p[i]*p[j+1]-p[j]*p[i+1];}return (float)(a*.5);}
    /** Half-plane intersection: keep points nearer a than b. */
    private static float[] clip(float[] xy,Seed a,Seed b){
        if(xy.length<6)return new float[0];float dx=b.x-a.x,dy=b.y-a.y;
        float limit=(b.x*b.x+b.y*b.y-a.x*a.x-a.y*a.y)*.5f;
        float[] out=new float[xy.length+6];int n=0;
        float prevX=xy[xy.length-2],prevY=xy[xy.length-1];float prev=prevX*dx+prevY*dy-limit;
        for(int k=0;k<xy.length;k+=2){if(((k)&63)==0)CancellationCheckpoint.check();float x=xy[k],y=xy[k+1],next=x*dx+y*dy-limit;
            if((prev<0&&next>0)||(prev>0&&next<0)){
                float t=prev/(prev-next);if(n+2>out.length)out=Arrays.copyOf(out,out.length*2);
                out[n++]=prevX+t*(x-prevX);out[n++]=prevY+t*(y-prevY);
            }
            if(next<=0){if(n+2>out.length)out=Arrays.copyOf(out,out.length*2);out[n++]=x;out[n++]=y;}
            prevX=x;prevY=y;prev=next;
        }
        return Arrays.copyOf(out,n);
    }
    private static float[] inset(float[] p,float pad){
        float cx=0,cy=0;for(int i=0;i<p.length;i+=2){if(((i)&63)==0)CancellationCheckpoint.check();cx+=p[i];cy+=p[i+1];}cx/=p.length/2;cy/=p.length/2;
        float min=Float.MAX_VALUE;for(int i=0;i<p.length;i+=2)min=Math.min(min,(float)Math.hypot(p[i]-cx,p[i+1]-cy));
        if(min<pad*1.08f)return new float[0];
        float f=Math.min(.97f,pad/min);float[] out=new float[p.length];
        for(int i=0;i<p.length;i+=2){if(((i)&63)==0)CancellationCheckpoint.check();out[i]=cx+(p[i]-cx)*(1-f);out[i+1]=cy+(p[i+1]-cy)*(1-f);}return out;
    }
    /** Reject a tile rather than exporting vector geometry outside a crop/snip.
     * Conservative sampling favors correct physical cutting over filling every boundary pixel. */
    private static boolean allowed(float[] xy,boolean[] mask,int w,int h){
        if(mask==null)return true;
        for(int i=0;i<xy.length;i+=2){if(((i)&63)==0)CancellationCheckpoint.check();int j=(i+2)%xy.length;
            float dx=xy[j]-xy[i],dy=xy[j+1]-xy[i+1];
            int steps=Math.max(1,(int)Math.ceil(Math.hypot(dx,dy)/1.25));
            for(int k=0;k<=steps;k++){if(((k)&63)==0)CancellationCheckpoint.check();
                float x=xy[i]+dx*k/steps,y=xy[i+1]+dy*k/steps;
                int ix=Math.min(w-1,Math.max(0,(int)x)),iy=Math.min(h-1,Math.max(0,(int)y));
                if(!mask[iy*w+ix])return false;
            }
        }
        return true;
    }
    private static float[] gridMotif(float x,float y,float w,float h,float tone,float pad,int seed){
        float cx=x+w/2,cy=y+h/2;
        float available=Math.min(w,h)-2*pad;
        if(available<1.4f)return new float[0];
        // Larger shapes for darker photo tone: one CLOSED silhouette per tile.
        float ink=.38f+.53f*(1-tone/255f);
        float rx=Math.max(.5f,(w*.5f-pad)*ink),ry=Math.max(.5f,(h*.5f-pad)*ink);
        if(rx<.7f||ry<.7f)return new float[0];
        int family=(seed%4);
        if(family==0){ // ellipse
            float[] p=new float[24];for(int i=0;i<12;i++){if(((i)&63)==0)CancellationCheckpoint.check();double a=2*Math.PI*i/12;p[2*i]=cx+rx*(float)Math.cos(a);p[2*i+1]=cy+ry*(float)Math.sin(a);}return p;
        }
        if(family==1){ // cut-safe capsule rectangle
            float k=.42f;return new float[]{cx-rx*k,cy-ry,cx+rx*k,cy-ry,cx+rx,cy-ry*.55f,cx+rx,cy+ry*.55f,
                cx+rx*k,cy+ry,cx-rx*k,cy+ry,cx-rx,cy+ry*.55f,cx-rx,cy-ry*.55f};
        }
        if(family==2){return new float[]{cx,cy-ry,cx+rx,cy,cx,cy+ry,cx-rx,cy};} // diamond
        return new float[]{cx-rx,cy-ry*.45f,cx-rx*.45f,cy-ry,cx+rx*.45f,cy-ry,
            cx+rx,cy-ry*.45f,cx+rx,cy+ry*.45f,cx+rx*.45f,cy+ry,cx-rx*.45f,cy+ry,cx-rx,cy+ry*.45f};
    }
    private static boolean validPoint(int[] pixels,boolean[] mask,int w,int h,float x,float y){
        int xx=Math.min(w-1,Math.max(0,(int)x)),yy=Math.min(h-1,Math.max(0,(int)y));int i=yy*w+xx;
        return (pixels[i]>>>24)>=128&&(mask==null||mask[i]);
    }
    private static final class Cell {
        final float x,y,w,h;final int depth;
        Cell(float x,float y,float w,float h,int depth){this.x=x;this.y=y;this.w=w;this.h=h;this.depth=depth;}
    }
    /** Quadtree motifs: disjoint cells, precise local detail allocation and
     * minimum physical dimensions. Four children share the parent's envelope. */
    private static void generateAdaptiveGrid(int[] pixels,int w,int h,Options o,
            PhotoCellImportance importance,ArrayList<Tile> output,int budget){
        int base=Math.max(4,(int)Math.sqrt(budget*.60));
        float cw=w/(float)base,ch=h/(float)base;
        float minSpan=Math.max(2.7f, .70f*w/o.widthMm); // physical-size guard
        ArrayDeque<Cell> pending=new ArrayDeque<>();
        for(int row=0;row<base;row++)for(int col=0;col<base;col++)
            pending.addLast(new Cell(col*cw,row*ch,cw,ch,0));
        MultiColorStippleEngine.PhotoColorMatcher matcher=
            new MultiColorStippleEngine.PhotoColorMatcher(o.colors,o.layerCount,null);
        int examined=0;
        while(!pending.isEmpty()){
            if((examined++&255)==0&&Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException("Grid render replaced");
            Cell cell=pending.removeFirst();
            float feature=importance.maxIn(cell.x,cell.y,cell.x+cell.w,cell.y+cell.h);
            boolean split=cell.depth<2&&feature>(cell.depth==0?.21f:.42f)&&
                cell.w>2*minSpan&&cell.h>2*minSpan&&
                output.size()+pending.size()+4<Math.min(MAX_TILES,budget+(budget/4));
            if(split){
                float hw=cell.w*.5f,hh=cell.h*.5f;
                pending.addLast(new Cell(cell.x,cell.y,hw,hh,cell.depth+1));
                pending.addLast(new Cell(cell.x+hw,cell.y,cell.w-hw,hh,cell.depth+1));
                pending.addLast(new Cell(cell.x,cell.y+hh,hw,cell.h-hh,cell.depth+1));
                pending.addLast(new Cell(cell.x+hw,cell.y+hh,cell.w-hw,cell.h-hh,cell.depth+1));
                continue;
            }
            float x=cell.x+cell.w*.5f,y=cell.y+cell.h*.5f;
            if(!validPoint(pixels,o.allowedPixels,w,h,x,y))continue;
            float pad=o.gapMm*w/o.widthMm*.65f;
            float tone=brightness(pixels,w,h,x,y);
            float[] poly=gridMotif(cell.x,cell.y,cell.w,cell.h,tone,pad,
                    ((int)(cell.x*13+cell.y*29)+cell.depth)&0x7fffffff);
            if(poly.length<6||Math.abs(area(poly))<2f||!allowed(poly,o.allowedPixels,w,h))continue;
            int sourceColor=footprintColor(pixels,w,h,x,y,Math.min(cell.w,cell.h)*.25f);
            output.add(new Tile(poly,matcher.choose(sourceColor),tone));
        }
    }
    public static Result generate(int[] pixels,int width,int height,Options opts){
        if(pixels==null||width<12||height<12||pixels.length!=width*height)throw new IllegalArgumentException("Invalid image");
        if(opts==null||opts.layerCount<2||opts.layerCount>8||opts.colors==null||opts.colors.length<opts.layerCount)
            throw new IllegalArgumentException("Need 2–8 vinyl colors");
        if(opts.widthMm<=10||!Float.isFinite(opts.widthMm)||!Float.isFinite(opts.gapMm)||opts.gapMm<0||opts.gapMm>20.1f ||
           (opts.allowedPixels!=null&&opts.allowedPixels.length!=width*height))
            throw new IllegalArgumentException("Invalid artwork width or gap");
        final Options o=opts.copy();
        if(o.style==STIPPLE){
            MultiColorStippleEngine.Options so=o.stippleOptions==null?new MultiColorStippleEngine.Options():o.stippleOptions.copy();
            so.layerCount=o.layerCount;so.detail=o.detail;so.widthMm=o.widthMm;
            so.colors=o.colors.clone();so.mask=o.allowedPixels;
            MultiColorStippleEngine.Result points=MultiColorStippleEngine.generate(pixels,width,height,so);
            ArrayList<Tile> contours=new ArrayList<>();
            for(MultiColorStippleEngine.Piece piece:points.pieces){CancellationCheckpoint.check();
                contours.add(new Tile(piece.outer,piece.holes.toArray(new float[0][]),piece.layer,1f));
            }
            return new Result(width,height,o,contours,points);
        }
        final PhotoCellImportance importance=PhotoCellImportance.create(pixels,width,height,o.allowedPixels);
        final ArrayList<Seed> seeds=new ArrayList<>();
        final int goal=Math.min(MAX_TILES,Math.max(130,Math.round(130+o.detail*(o.style==VORONOI?15.0f:12.4f))));
        final float step=(float)Math.sqrt((double)width*height/(goal*.95));
        final int cols=Math.max(3,(int)Math.ceil(width/step)),rows=Math.max(3,(int)Math.ceil(height/step));
        final float cw=width/(float)cols,ch=height/(float)rows;
        final Random rng=new Random(0x7E5517A9L+width*61L+height*137L+o.detail*391L+o.style);
        if(o.style==GRID_MOTIF){
            ArrayList<Tile> gridTiles=new ArrayList<>();
            generateAdaptiveGrid(pixels,width,height,o,importance,gridTiles,goal);
            if(gridTiles.isEmpty())throw new IllegalArgumentException("No cuttable tiles; lower gap or raise detail");
            Result gridResult=new Result(width,height,o,gridTiles);
            if(gridResult.maxPaths()>MAX_PATHS_PER_LAYER)throw new IllegalArgumentException("Too many SVG paths");
            return gridResult;
        }else{
            // Give every spatial cell a base site; move high-evidence sites
            // toward real edges, not a guessed face position. Add extra sites
            // only to the strongest local regions within the global budget.
            ArrayList<float[]> extra=new ArrayList<>();
            for(int row=0;row<rows;row++)for(int col=0;col<cols;col++){if(((col)&63)==0)CancellationCheckpoint.check();
                float x0=col*cw,y0=row*ch,x1=Math.min(width,(col+1)*cw),y1=Math.min(height,(row+1)*ch);
                float evidence=importance.maxIn(x0,y0,x1,y1);
                // Do not waste an equal point budget on completely flat
                // backgrounds. Deterministic thinning keeps coarse coverage
                // and leaves detail budget for photographic edges.
                if(evidence<.09f && (row+col*2)%3==0)continue;
                float x=(col+.5f+(rng.nextFloat()-.5f)*.22f)*cw;
                float y=(row+.5f+(rng.nextFloat()-.5f)*.22f)*ch;
                if(evidence>.25f){float[] focus=importance.strongestIn(x0,y0,x1,y1);
                    x=x*.45f+focus[0]*.55f;y=y*.45f+focus[1]*.55f;}
                x=Math.min(width-.5f,Math.max(.5f,x));y=Math.min(height-.5f,Math.max(.5f,y));
                if(!validPoint(pixels,o.allowedPixels,width,height,x,y))continue;
                seeds.add(new Seed(x,y,brightness(pixels,width,height,x,y),
                    footprintColor(pixels,width,height,x,y,Math.min(cw,ch)*.22f)));
                if(evidence>.30f){
                    float[] focus=importance.strongestIn(x0,y0,x1,y1);
                    extra.add(new float[]{evidence,focus[0],focus[1]});
                }
            }
            // Secondary higher-resolution feature proposals. A single coarse
            // seed per cell cannot follow a short eyelid or mouth boundary.
            // This pass spends remaining sites on actual photographic edges;
            // it does not spend a color or point quota on flat background.
            float fine=Math.max(1.5f,step*.54f);
            for(float fy=0;fy<height;fy+=fine)for(float fx=0;fx<width;fx+=fine){CancellationCheckpoint.check();
                float ex=Math.min(width,fx+fine),ey=Math.min(height,fy+fine);
                float evidence=importance.maxIn(fx,fy,ex,ey);
                if(evidence<.29f)continue;
                float[] focus=importance.strongestIn(fx,fy,ex,ey);
                extra.add(new float[]{evidence,focus[0],focus[1]});
            }
            extra.sort((a,b)->Float.compare(b[0],a[0]));
            for(float[] e:extra){CancellationCheckpoint.check();
                if(seeds.size()>=MAX_TILES||seeds.size()>=goal)break;
                boolean near=false;for(Seed seed:seeds)if(Math.abs(seed.x-e[1])<cw*.28f&&Math.abs(seed.y-e[2])<ch*.28f){near=true;break;}
                if(near||!validPoint(pixels,o.allowedPixels,width,height,e[1],e[2]))continue;
                seeds.add(new Seed(e[1],e[2],brightness(pixels,width,height,e[1],e[2]),
                    footprintColor(pixels,width,height,e[1],e[2],Math.min(cw,ch)*.20f)));
            }
        }
        // Assign by the ACTUAL photo color, never quantile-balanced brightness.
        // A swatch for a green shirt must not get an equal share of peach skin.
        // Unused physical rolls stay unused, even when all eight are selected.
        final int n=seeds.size();int[] group=new int[n];
        MultiColorStippleEngine.PhotoColorMatcher matcher=
            new MultiColorStippleEngine.PhotoColorMatcher(o.colors,o.layerCount,null);
        for(int i=0;i<n;i++)group[i]=matcher.choose(seeds.get(i).color);
        ArrayList<Tile> tiles=new ArrayList<>(n);
        float pad=o.gapMm*width/o.widthMm*.5f;
        for(int idx=0;idx<n;idx++){if(((idx)&63)==0)CancellationCheckpoint.check();
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Tessellation replaced");
            Seed a=seeds.get(idx);float[] poly;
            if(o.style==GRID_MOTIF){int row=Math.min(rows-1,(int)(a.y/ch)),col=Math.min(cols-1,(int)(a.x/cw));
                poly=gridMotif(col*cw,row*ch,cw,ch,a.tone,pad*1.3f,idx);
            }else{
                poly=new float[]{0,0,width,0,width,height,0,height};
                for(int k=0;k<n;k++){if(((k)&63)==0)CancellationCheckpoint.check();
                    if(k==idx)continue;Seed b=seeds.get(k);
                    // Safe conservative rejection for distant points: clip only if bisector
                    // can reach the current geometry bounds (otherwise never affects it).
                    float radius=(float)Math.hypot(b.x-a.x,b.y-a.y);
                    if(radius>step*7f)continue;
                    poly=clip(poly,a,b);if(poly.length<6)break;
                }
                // Move every edge inward by exactly half the gap. (Scaling
                // toward the centroid moves near edges less than the pad and
                // left neighbouring cells closer than the requested gap.)
                if(poly.length>=6){float[] in=StyleKit.insetConvex(poly,pad);poly=in==null?new float[0]:in;}
                // A cell narrower than the cut minimum cannot be cut or weeded.
                if(poly.length>=6&&VinylEngine.tinyPiece(poly,o.minimumCutMm*width/o.widthMm))poly=new float[0];
            }
            if(poly.length<6||Math.abs(area(poly))<2.0f)continue;
            boolean finite=true;for(float v:poly)if(!Float.isFinite(v)){finite=false;break;}
            if(finite&&allowed(poly,o.allowedPixels,width,height))tiles.add(new Tile(poly,group[idx],a.tone));
        }
        Result result=new Result(width,height,o,tiles);
        if(result.tiles.isEmpty())throw new IllegalArgumentException("No cuttable tiles; lower gap or raise detail");
        if(result.maxPaths()>MAX_PATHS_PER_LAYER)throw new IllegalArgumentException("Too many SVG paths");
        return result;
    }
    private TessellationEngine(){}
}

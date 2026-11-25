package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Six approved photographic vector styles built on one bounded, Android-free pipeline.
 *
 * The engine deliberately shares image sampling, palette matching, raster union, contour tracing,
 * cancellation and physical feature cleanup.  Each style changes the geometry model, not merely a
 * display preset.  Filled output is reconstructed as non-self-intersecting even-odd contours; pen
 * variants are explicit open centerlines and are never presented as blade-cut sheets.
 */
public final class ExpandedPortraitStylesEngine {
    private ExpandedPortraitStylesEngine() { }

    public static final int MARQUETRY=0, FINGERPRINT=1, CONNECTED_LACE=2,
        REDUCTION_WOODCUT=3, WOVEN_RIBBON=4, SCRATCHBOARD=5,
        DELAUNAY_WIREFRAME=6, CONTINUOUS_SCRIBBLE=7, STRING_ART=8,
        KINTSUGI=9, WOODGRAIN=10, CIRCUIT_TRACE=11, CELTIC_BRAID=12,
        TORN_COLLAGE=13, ARCHITECTURAL=14;

    /** Tombstones retain the RC141 local IDs so saved projects never reopen as another style. */
    private static final boolean[] ACTIVE={
        true,true,false,true,true,false,false,false,false,false,true,false,false,true,false
    };

    public static final String[] NAMES={
        "Organic Marquetry Portrait","Fingerprint Portrait","(retired expanded style 2)",
        "Reduction Woodcut","Woven Ribbon Portrait","(retired expanded style 5)",
        "(retired expanded style 6)","(retired expanded style 7)","(retired expanded style 8)",
        "(retired expanded style 9)","Flowing Woodgrain Portrait","(retired expanded style 11)",
        "(retired expanded style 12)","Torn-Paper Collage","(retired expanded style 14)"
    };
    public static final String[] DESCRIPTIONS={
        "Smooth organic color inlays","Whorled photographic ridges","",
        "Registered carved color passes","Image-driven warp and weft","","","","","",
        "Nested grain around facial structure","","","Jagged layered color regions",""
    };
    public static final String[] CATEGORIES={
        "Multilayer","Lines","","Multilayer","Lines","","","","","","Lines","","",
        "Multilayer",""
    };
    public static final String[] THUMBNAIL_KEYS={
        "art.marquetry","art.fingerprint","","art.reduction_woodcut",
        "art.woven_ribbon","","","","","","art.woodgrain","","",
        "art.torn_collage",""
    };

    public static boolean isActive(int style){
        return style>=0&&style<ACTIVE.length&&ACTIVE[style];
    }

    public static final class Options {
        public int style=MARQUETRY, variant=0, detail=55, colorCount=6;
        public float widthMm=152.4f, minimumMm=.254f, gapMm=.24f, weightMm=.7f;
        public int[] palette=null;
        public int paperArgb=0xfffaf9f5;
        public boolean[] allowed=null;
        public long seed=20261104L;
        /** M4: label sheets underlap the sheets above them (hidden trap band). */
        public boolean sharedEdgeUnderlap=true;
        public Options copy(){
            Options o=new Options();o.style=style;o.variant=variant;o.detail=detail;o.colorCount=colorCount;
            o.sharedEdgeUnderlap=sharedEdgeUnderlap;
            o.widthMm=widthMm;o.minimumMm=minimumMm;o.gapMm=gapMm;o.weightMm=weightMm;
            o.palette=palette==null?null:palette.clone();o.paperArgb=paperArgb;
            o.allowed=allowed==null?null:allowed.clone();o.seed=seed;return o;
        }
        void validate(){
            if(!isActive(style))throw new IllegalArgumentException("Retired or unknown expanded style");
            if(variant<0||variant>=variantCount(style))throw new IllegalArgumentException("Unknown expanded style variant");
            if(detail<0||detail>100||colorCount<1||colorCount>8)throw new IllegalArgumentException("Invalid detail or color count");
            if(!Float.isFinite(widthMm)||widthMm<20||widthMm>2000||
               !Float.isFinite(minimumMm)||minimumMm<.10f||minimumMm>4||
               !Float.isFinite(gapMm)||gapMm<.10f||gapMm>4||
               !Float.isFinite(weightMm)||weightMm<.10f||weightMm>5)
                throw new IllegalArgumentException("Invalid physical dimensions");
            if(palette!=null&&(palette.length<minimumColors(style)||palette.length>8))
                throw new IllegalArgumentException("Style requires at least "+minimumColors(style)+" colors");
        }
    }

    private static int minimumColors(int style){
        if(style==WOVEN_RIBBON)return 2;
        return 1;
    }

    public static int variantCount(int style){
        return isActive(style)?1:0;
    }
    public static String variantName(int style,int variant){
        if(!isActive(style)||variant!=0)throw new IllegalArgumentException("Retired or unknown variant");
        return "Cut Vinyl";
    }
    public static boolean supportsWeight(int style){
        return style==FINGERPRINT||style==WOVEN_RIBBON||style==WOODGRAIN;
    }

    private static final class Grid {
        final int w,h;final float px,widthMm,heightMm;
        final boolean[] valid;final float[] lum,edge;final int[] argb;
        Grid(StyleKit.Source src,int detail){
            int side=Math.min(300,Math.max(112,112+detail*2));
            float scale=Math.min(1f,side/(float)Math.max(src.w,src.h));
            w=Math.max(24,Math.round(src.w*scale));h=Math.max(24,Math.round(src.h*scale));
            widthMm=src.widthMm;px=widthMm/w;heightMm=h*px;
            valid=new boolean[w*h];lum=new float[w*h];argb=new int[w*h];
            for(int y=0;y<h;y++){if(((y)&63)==0)CancellationCheckpoint.check();
                if((y&31)==0)StyleKit.checkCancelled();
                for(int x=0;x<w;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                    int i=y*w+x;float mx=(x+.5f)*px,my=(y+.5f)*px;
                    valid[i]=src.validAt(mx,my);lum[i]=src.lumAt(mx,my);argb[i]=src.colorAt(mx,my);
                    if(!valid[i])lum[i]=1f;
                }
            }
            edge=StyleKit.edgeMap(StyleKit.blur(lum,w,h,Math.max(.75f,.8f/px)),w,h);
        }
        int index(float x,float y){
            int xx=StyleKit.clampI((int)(x/px),0,w-1),yy=StyleKit.clampI((int)(y/px),0,h-1);
            return yy*w+xx;
        }
        boolean validAt(float x,float y){return x>=0&&y>=0&&x<widthMm&&y<heightMm&&valid[index(x,y)];}
        float darkAt(float x,float y){return 1f-lum[index(x,y)];}
    }

    public static StyleKit.Result generate(int[] pixels,int width,int height,Options raw){
        Options o=raw.copy();o.validate();
        StyleKit.Source src=new StyleKit.Source(pixels,width,height,o.allowed,o.widthMm).autoLevels(.02f,.98f);
        if(!src.anyValid())throw new IllegalArgumentException("No usable pixels");
        Grid g=new Grid(src,o.detail);
        boolean automatic=o.palette==null;
        int wanted=Math.max(minimumColors(o.style),Math.min(8,automatic?o.colorCount:o.palette.length));
        int[] palette;
        if(!automatic)palette=Arrays.copyOf(o.palette,wanted);
        else palette=derivePalette(g,wanted,o.seed);
        if(automatic&&o.style==REDUCTION_WOODCUT)sortByLuminance(palette,false);
        StyleKit.Result result=new StyleKit.Result(g.widthMm,g.heightMm);result.paperArgb=o.paperArgb;
        switch(o.style){
            case MARQUETRY:marquetry(result,g,palette,o,false);break;
            case FINGERPRINT:fingerprint(result,g,palette[0],o);break;
            case REDUCTION_WOODCUT:reductionWoodcut(result,g,palette,o);break;
            case WOVEN_RIBBON:woven(result,g,palette,o);break;
            case WOODGRAIN:woodgrain(result,g,palette[0],o);break;
            case TORN_COLLAGE:marquetry(result,g,palette,o,true);break;
            default:throw new IllegalArgumentException("Retired or unknown expanded style");
        }
        // Narrow curving ridges can benefit from a second, *optional* vector
        // polish. Other styles deliberately retain original boundaries: the
        // color inlays need shared edges and woven bands need knockout gaps.
        if(o.style==FINGERPRINT||o.style==WOODGRAIN)
            OrganicRidgeContourPolish.apply(result,o.minimumMm,o.gapMm,g.px);
        result.layers.removeIf(layer->layer.loops.isEmpty()&&layer.lines.isEmpty());
        if(result.layers.isEmpty())throw new IllegalArgumentException("No cuttable geometry at this detail and size");
        if(result.layers.size()>8)throw new IllegalStateException("Expanded style exceeded eight physical layers");
        int cuts=0,points=0;
        for(StyleKit.Layer layer:result.layers){CancellationCheckpoint.check();
            if(layer.loops.size()>5000)throw new IllegalStateException(layer.name+" exceeds 5000 cut pieces");
            cuts+=layer.loops.size();points+=layer.pointCount();
            for(float[] loop:layer.loops){CancellationCheckpoint.check();
                if(loop.length<6||(loop.length&1)!=0||StyleKit.selfIntersections(loop,1)>0)
                    throw new IllegalStateException("Unsafe polygon in "+layer.name);
                for(float v:loop)if(!Float.isFinite(v))throw new IllegalStateException("Non-finite geometry");
            }
        }
        result.summary=NAMES[o.style]+": "+result.layers.size()+" operation"+(result.layers.size()==1?"":"s")+
            ", "+cuts+" closed cut shape"+(cuts==1?"":"s")+", "+points+" vector points";
        return result;
    }

    private static int[] derivePalette(Grid g,int count,long seed){
        int valid=0;for(boolean b:g.valid)if(b)valid++;
        if(valid==0)throw new IllegalArgumentException("No colors inside subject");
        int stride=Math.max(1,valid/4096),n=(valid+stride-1)/stride;
        int[] colors=new int[n];float[] weights=new float[n];int out=0,seen=0;
        for(int i=0;i<g.argb.length&&out<n;i++)if(g.valid[i]){
            if((seen++%stride)==0){colors[out]=g.argb[i];weights[out]=.5f+g.edge[i];out++;}
        }
        colors=Arrays.copyOf(colors,out);weights=Arrays.copyOf(weights,out);
        int[] p=StyleKit.kmeans(colors,weights,Math.min(count,out),seed);
        if(p.length==count)return p;
        int[] filled=new int[count];
        for(int i=0;i<count;i++)filled[i]=p[Math.min(p.length-1,i*p.length/count)];
        return filled;
    }

    private static void sortByLuminance(int[] palette,boolean darkestFirst){
        for(int i=1;i<palette.length;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            int color=palette[i],lum=StyleKit.luma255(color),j=i-1;
            while(j>=0&&(darkestFirst?StyleKit.luma255(palette[j])>lum:StyleKit.luma255(palette[j])<lum)){
                palette[j+1]=palette[j];j--;
            }
            palette[j+1]=color;
        }
    }

    private static int[] paletteLabels(Grid g,int[] palette){
        PhotoSwatchAssociation association=new PhotoSwatchAssociation(
            palette,palette.length,null,g.argb,g.valid,PhotoSwatchAssociation.REGIONS);
        int[] cache=new int[32768];Arrays.fill(cache,-1);
        int[] labels=new int[g.w*g.h];
        for(int i=0;i<labels.length;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            if((i&8191)==0)StyleKit.checkCancelled();
            if(!g.valid[i]){labels[i]=-1;continue;}
            int c=g.argb[i],key=(((c>>19)&31)<<10)|(((c>>11)&31)<<5)|((c>>3)&31);
            int label=cache[key];
            if(label<0){int q=0xff000000|(((key>>10)&31)*255/31<<16)|(((key>>5)&31)*255/31<<8)|(key&31)*255/31;
                cache[key]=label=association.choose(q);}
            labels[i]=label;
        }
        return labels;
    }

    private static int[] smoothLabels(Grid g,int[] input,int colors,int passes){
        int[] a=input.clone(),b=new int[a.length];int[] counts=new int[colors];
        for(int pass=0;pass<passes;pass++){
            StyleKit.checkCancelled();
            for(int y=0;y<g.h;y++)for(int x=0;x<g.w;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                int i=y*g.w+x;if(a[i]<0){b[i]=-1;continue;}
                if(g.edge[i]>.63f){b[i]=a[i];continue;}
                Arrays.fill(counts,0);
                for(int yy=Math.max(0,y-1);yy<=Math.min(g.h-1,y+1);yy++)
                    for(int xx=Math.max(0,x-1);xx<=Math.min(g.w-1,x+1);xx++){if(((xx)&63)==0)CancellationCheckpoint.check();
                        int v=a[yy*g.w+xx];if(v>=0)counts[v]++;
                    }
                int best=a[i];for(int k=0;k<colors;k++)if(counts[k]>counts[best])best=k;
                b[i]=counts[best]>=5?best:a[i];
            }
            int[] t=a;a=b;b=t;
        }
        return a;
    }

    private static void marquetry(StyleKit.Result out,Grid g,int[] palette,Options o,boolean torn){
        int[] labels=paletteLabels(g,palette);
        if(torn){
            for(int y=0;y<g.h;y++)for(int x=0;x<g.w;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                int i=y*g.w+x;if(labels[i]<0)continue;
                int noise=hash(x/2,y/2,(int)o.seed);float n=((noise>>>8)&255)/255f;
                if(n<.10f+o.detail*.0008f){int nx=StyleKit.clampI(x+((noise&1)==0?-2:2),0,g.w-1);
                    int source=y*g.w+nx;if(labels[source]>=0)labels[i]=labels[source];}
            }
        }else labels=smoothLabels(g,labels,palette.length,2+o.detail/35);
        boolean[] base=g.valid.clone();
        addMaskLayer(out,torn?"torn-collage-1":"marquetry-1",palette[0],base,g,torn?0:1,o.minimumMm*o.minimumMm*2);
        // M4 shared edges: each piece underlaps the pieces stacked ABOVE it by a trap
        // band. The upper sheet hides the band, so the visible seam is unchanged, but
        // per-sheet cut-ready repair can no longer open a hairline of base colour
        // where two formerly shared edges move independently.
        int trap=o.sharedEdgeUnderlap?underlapPixels(o,g.px):0;
        for(int k=1;k<palette.length;k++){if(((k)&63)==0)CancellationCheckpoint.check();
            boolean[] mask=new boolean[labels.length];for(int i=0;i<mask.length;i++)mask[i]=labels[i]==k;
            if(trap>0)underlap(mask,labels,k,g.w,g.h,trap);
            addMaskLayer(out,(torn?"torn-collage-":"marquetry-")+(k+1),palette[k],mask,g,torn?0:1,o.minimumMm*o.minimumMm*2);
        }
    }
    /** Trap width: one minimum cut width plus one weed gap, the most a repair moves an edge. */
    static int underlapPixels(Options o,float px){
        float trapMm=o.minimumMm+o.gapMm;
        return px>0?Math.max(1,(int)Math.ceil(trapMm/px)):0;
    }
    /** Grow {@code mask} (label k) by up to {@code radius} pixels, only into labels above k. */
    static void underlap(boolean[] mask,int[] labels,int k,int w,int h,int radius){
        int[] dist=new int[mask.length];java.util.Arrays.fill(dist,Integer.MAX_VALUE);
        int[] queue=new int[mask.length];int head=0,tail=0;
        for(int i=0;i<mask.length;i++)if(mask[i]){dist[i]=0;queue[tail++]=i;}
        while(head<tail){
            int at=queue[head++];int d=dist[at];if(d>=radius)continue;
            if((head&4095)==0)StyleKit.checkCancelled();
            int x=at%w,y=at/w;
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
                if(dx==0&&dy==0)continue;int nx=x+dx,ny=y+dy;
                if(nx<0||ny<0||nx>=w||ny>=h)continue;int n=ny*w+nx;
                if(dist[n]!=Integer.MAX_VALUE||labels[n]<=k)continue; // never into lower or same labels
                dist[n]=d+1;mask[n]=true;queue[tail++]=n;
            }
        }
    }

    private static void fingerprint(StyleKit.Result out,Grid g,int color,Options o){
        boolean[] mask=new boolean[g.w*g.h];float cx=g.widthMm*.50f,cy=g.heightMm*.44f;
        float pitch=Math.max(o.minimumMm+o.gapMm+.12f,g.widthMm/(30f+o.detail*.42f));
        float maximum=Math.max(o.minimumMm,Math.min(pitch-o.gapMm,pitch*.68f));
        for(int y=0;y<g.h;y++)for(int x=0;x<g.w;x++){if(((x)&63)==0)CancellationCheckpoint.check();
            int i=y*g.w+x;if(!g.valid[i])continue;float xx=(x+.5f)*g.px,yy=(y+.5f)*g.px;
            float dx=xx-cx,dy=(yy-cy)*1.20f,r=(float)Math.hypot(dx,dy);
            double angle=Math.atan2(dy,dx);
            float core=(float)(r+.12f*r*Math.sin(2*angle)+pitch*.9f*Math.sin(angle+g.lum[i]*1.7f));
            float phase=core-Math.round(core/pitch)*pitch;
            float width=o.minimumMm+(maximum-o.minimumMm)*(1f-g.lum[i])*.85f+maximum*.15f*g.edge[i];
            mask[i]=Math.abs(phase)<=width*.5f;
        }
        addMaskLayer(out,"fingerprint-1",color,mask,g,1,o.minimumMm*o.minimumMm);
    }

    private static void reductionWoodcut(StyleKit.Result out,Grid g,int[] palette,Options o){
        addMaskLayer(out,"reduction-woodcut-1",palette[0],g.valid.clone(),g,1,o.minimumMm*o.minimumMm*2);
        for(int layer=1;layer<palette.length;layer++){if(((layer)&63)==0)CancellationCheckpoint.check();
            boolean[] mask=new boolean[g.w*g.h];float threshold=layer/(float)(palette.length+.35f);
            float grain=Math.max(1.2f,o.minimumMm+o.gapMm)*3.2f;
            for(int y=0;y<g.h;y++)for(int x=0;x<g.w;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                int i=y*g.w+x;if(!g.valid[i])continue;float xx=x*g.px,yy=y*g.px;
                float carved=.055f*(float)Math.sin((xx*.82f+yy*.29f)/grain*2*Math.PI)+.13f*g.edge[i];
                mask[i]=(1f-g.lum[i])+carved>threshold;
            }
            addMaskLayer(out,"reduction-woodcut-"+(layer+1),palette[layer],mask,g,1,o.minimumMm*o.minimumMm*2);
        }
    }

    private static void woven(StyleKit.Result out,Grid g,int[] palette,Options o){
        int count=Math.max(2,Math.min(4,palette.length));
        boolean[][] masks=new boolean[count][g.w*g.h];
        float pitch=Math.max(o.minimumMm+o.gapMm+.12f,g.widthMm/(18f+o.detail*.30f));
        float maxWidth=Math.max(o.minimumMm,Math.min(pitch-o.gapMm,pitch*.72f));
        int rows=(int)Math.ceil(g.heightMm/pitch),cols=(int)Math.ceil(g.widthMm/pitch);
        float[][] labs=StyleKit.labOf(Arrays.copyOf(palette,count));
        int[][] horizontal=new int[rows][cols],vertical=new int[cols][rows];
        for(int row=0;row<rows;row++){
            float[][] target=new float[cols][];
            for(int c=0;c<cols;c++){float x=(c+.5f)*pitch;target[c]=wovenSample(g,x,wovenCenter(x,row,pitch,false),pitch);}
            horizontal[row]=RibbonColorMath.strand(target,labs);
        }
        for(int col=0;col<cols;col++){
            float[][] target=new float[rows][];
            for(int r=0;r<rows;r++){float y=(r+.5f)*pitch;target[r]=wovenSample(g,wovenCenter(y,col,pitch,true),y,pitch);}
            vertical[col]=RibbonColorMath.strand(target,labs);
        }
        // A crossing retains the under-sheet unless its physical order would
        // obscure the desired over-ribbon. No destructive disk knockout.
        for(int y=0;y<g.h;y++){StyleKit.checkCancelled();for(int x=0;x<g.w;x++){
            int at=y*g.w+x;if(!g.valid[at])continue;
            float xx=(x+.5f)*g.px,yy=(y+.5f)*g.px;
            float half=.5f*(o.minimumMm+(maxWidth-o.minimumMm)*(1f-g.lum[at]));
            int row=wovenLane(xx,yy,pitch,rows,false,half),col=wovenLane(yy,xx,pitch,cols,true,half);
            int hslot=row<0?-1:horizontal[row][Math.min(cols-1,(int)(xx/pitch))];
            int vslot=col<0?-1:vertical[col][Math.min(rows-1,(int)(yy/pitch))];
            if(hslot>=0&&vslot>=0&&hslot!=vslot){
                boolean horizontalOver=((row+col)&1)==0;
                if(horizontalOver&&vslot>hslot)vslot=-1;
                if(!horizontalOver&&hslot>vslot)hslot=-1;
            }
            if(hslot>=0)masks[hslot][at]=true;
            if(vslot>=0)masks[vslot][at]=true;
        }}
        for(int i=0;i<count;i++)addMaskLayer(out,"woven-ribbon-"+(i+1),palette[i],masks[i],g,1,o.minimumMm*o.minimumMm);
    }
    private static float wovenCenter(float along,int lane,float pitch,boolean cross){return (lane+.5f)*pitch+pitch*.16f*(float)Math.sin(along/pitch*(cross?.69f:.72f)+lane*(cross?.37f:.43f));}
    private static int wovenLane(float along,float side,float pitch,int lanes,boolean cross,float half){int center=(int)(side/pitch),best=-1;float distance=half;for(int i=Math.max(0,center-1);i<=Math.min(lanes-1,center+1);i++){float d=Math.abs(side-wovenCenter(along,i,pitch,cross));if(d<=distance){distance=d;best=i;}}return best;}
    private static float[] wovenSample(Grid g,float x,float y,float pitch){float r=0,b=0,green=0,w=0;for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){float xx=x+dx*pitch*.24f,yy=y+dy*pitch*.24f;if(!g.validAt(xx,yy))continue;float weight=dx==0&&dy==0?4:1;float[] c=RibbonColorMath.rgb(g.argb[g.index(xx,yy)]);r+=weight*c[0];green+=weight*c[1];b+=weight*c[2];w+=weight;}return w==0?null:RibbonColorMath.lab(r/w,green/w,b/w);}

    private static void woodgrain(StyleKit.Result out,Grid g,int color,Options o){
        boolean[] mask=new boolean[g.w*g.h];float pitch=Math.max(o.minimumMm+o.gapMm+.12f,g.widthMm/(26f+o.detail*.30f));
        float max=Math.max(o.minimumMm,Math.min(pitch-o.gapMm,Math.max(o.weightMm,pitch*.55f)));
        float[][] knots={{g.widthMm*.33f,g.heightMm*.38f},{g.widthMm*.69f,g.heightMm*.63f}};
        for(int y=0;y<g.h;y++)for(int x=0;x<g.w;x++){if(((x)&63)==0)CancellationCheckpoint.check();
            int i=y*g.w+x;if(!g.valid[i])continue;float xx=(x+.5f)*g.px,yy=(y+.5f)*g.px;
            float best=Float.MAX_VALUE;
            for(float[] k:knots){CancellationCheckpoint.check();float dx=xx-k[0],dy=(yy-k[1])*1.42f;best=Math.min(best,(float)Math.hypot(dx,dy));}
            float phase=best+pitch*.75f*(g.lum[i]-.5f)+pitch*.24f*(float)Math.sin((xx+yy*.27f)/pitch);
            phase-=Math.round(phase/pitch)*pitch;float width=o.minimumMm+(max-o.minimumMm)*(1f-g.lum[i]);
            mask[i]=Math.abs(phase)<=width*.5f;
        }
        addMaskLayer(out,"woodgrain-1",color,mask,g,1,o.minimumMm*o.minimumMm);
    }

    private static void addMaskLayer(StyleKit.Result out,String name,int color,boolean[] raw,Grid g,int smooth,float minArea){
        StyleKit.checkCancelled();
        boolean any=false;for(boolean b:raw)if(b){any=true;break;}if(!any)return;
        boolean[] mask=removeTiny(raw,g.w,g.h,Math.max(1,(int)Math.ceil(minArea/(g.px*g.px))),g.valid);
        int pw=g.w+2,ph=g.h+2;float[] field=new float[pw*ph];Arrays.fill(field,-1f);
        for(int y=0;y<g.h;y++)for(int x=0;x<g.w;x++)if(mask[y*g.w+x])field[(y+1)*pw+x+1]=1f;
        ArrayList<float[]> contours=TopographicLayersEngine.contours(field,pw,ph,0f,g.px);
        StyleKit.Layer layer=new StyleKit.Layer(name,color);
        for(float[] loop:contours){CancellationCheckpoint.check();
            if((layer.loops.size()&255)==0)StyleKit.checkCancelled();
            if(smooth>0&&loop.length>=8)loop=StyleKit.chaikin(loop,2,true,smooth);
            loop=StyleKit.removeCollinear(loop,Math.max(.01f,g.px*.04f));
            if(loop.length>=6&&Math.abs(StyleKit.signedArea(loop))>=minArea&&StyleKit.selfIntersections(loop,1)==0)layer.loops.add(loop);
        }
        if(!layer.loops.isEmpty())out.layers.add(layer);
    }

    private static boolean[] removeTiny(boolean[] input,int w,int h,int minCells,boolean[] allowed){
        boolean[] out=input.clone(),seen=new boolean[input.length];int[] queue=new int[input.length];ArrayList<Integer> component=new ArrayList<>();
        for(int start=0;start<input.length;start++)if(input[start]&&!seen[start]){
            int head=0,tail=0;queue[tail++]=start;seen[start]=true;component.clear();
            while(head<tail){int at=queue[head++];component.add(at);int x=at%w,y=at/w;
                int[] next={at-1,at+1,at-w,at+w};
                for(int k=0;k<4;k++){if(((k)&63)==0)CancellationCheckpoint.check();if((k==0&&x==0)||(k==1&&x==w-1)||(k==2&&y==0)||(k==3&&y==h-1))continue;int n=next[k];
                    if(input[n]&&!seen[n]){seen[n]=true;queue[tail++]=n;}}
            }
            if(component.size()<minCells)for(int i:component)out[i]=false;
        }
        for(int i=0;i<out.length;i++)out[i]&=allowed[i];return out;
    }

    private static void paintLine(boolean[] mask,Grid g,float x0,float y0,float x1,float y1,float radius){
        float len=(float)Math.hypot(x1-x0,y1-y0);int steps=Math.max(1,(int)Math.ceil(len/Math.max(g.px*.55f,.08f)));
        for(int i=0;i<=steps;i++){if(((i)&63)==0)CancellationCheckpoint.check();float t=i/(float)steps;paintDisk(mask,g,x0+(x1-x0)*t,y0+(y1-y0)*t,radius);}
    }
    private static void paintDisk(boolean[] mask,Grid g,float cx,float cy,float radius){
        int r=Math.max(1,(int)Math.ceil(radius/g.px));int x0=StyleKit.clampI((int)(cx/g.px),0,g.w-1),y0=StyleKit.clampI((int)(cy/g.px),0,g.h-1);
        float rr=Math.max(radius,g.px*.55f);rr*=rr;
        for(int y=Math.max(0,y0-r);y<=Math.min(g.h-1,y0+r);y++)for(int x=Math.max(0,x0-r);x<=Math.min(g.w-1,x0+r);x++){if(((x)&63)==0)CancellationCheckpoint.check();
            int i=y*g.w+x;float dx=(x+.5f)*g.px-cx,dy=(y+.5f)*g.px-cy;if(g.valid[i]&&dx*dx+dy*dy<=rr)mask[i]=true;
        }
    }
    private static void eraseDisk(boolean[] mask,Grid g,float cx,float cy,float radius){
        int r=Math.max(1,(int)Math.ceil(radius/g.px));int x0=StyleKit.clampI((int)(cx/g.px),0,g.w-1),y0=StyleKit.clampI((int)(cy/g.px),0,g.h-1);float rr=radius*radius;
        for(int y=Math.max(0,y0-r);y<=Math.min(g.h-1,y0+r);y++)for(int x=Math.max(0,x0-r);x<=Math.min(g.w-1,x0+r);x++){if(((x)&63)==0)CancellationCheckpoint.check();
            float dx=(x+.5f)*g.px-cx,dy=(y+.5f)*g.px-cy;if(dx*dx+dy*dy<=rr)mask[y*g.w+x]=false;
        }
    }
    private static int hash(int x,int y,int seed){int h=seed^x*0x45d9f3b^y*0x27d4eb2d;h^=h>>>16;h*=0x7feb352d;h^=h>>>15;return h;}
}

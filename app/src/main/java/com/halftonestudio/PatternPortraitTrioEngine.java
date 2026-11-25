package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * RC152: three image-aware but geometrically independent cut styles.
 * Everything is deterministic, Android-free, measured in mm and raster-unioned
 * before vectorization. Each physical sheet has even-odd, non-overlapping paths.
 * No stroke-only pen output is silently passed off as a vinyl cut file.
 */
public final class PatternPortraitTrioEngine {
    private PatternPortraitTrioEngine() {}
    public static final int CONFETTI=0, CHAINMAIL=1, SPIDER_WEB=2;
    public static final class Options {
        public int kind=CONFETTI, detail=55, layers=6, organic=50,
            confettiMode=FallenConfettiEngine.MODE_MIXED;
        public float widthMm=152.4f, minCutMm=.254f, spacingMm=.40f;
        public float gapMm=.5f;
        public int[] palette=null;
        public int paperArgb=0xffffffff;
        public boolean[] allowed=null;
        public long seed=0x514cA203L;
        public boolean draft=false;
        public void validate() {
            if(kind<0||kind>2||confettiMode<0||confettiMode>FallenConfettiEngine.MODE_FALLEN||
               detail<0||detail>100||organic<0||organic>100||
               layers<2||layers>8||!Float.isFinite(widthMm)||widthMm<20||widthMm>2000||
               !Float.isFinite(minCutMm)||minCutMm<.1f||minCutMm>4||
               !Float.isFinite(gapMm)||gapMm<=0||
               !Float.isFinite(spacingMm)||spacingMm<.1f||spacingMm>5||
               palette!=null&&palette.length!=(kind==SPIDER_WEB?1:layers))
                throw new IllegalArgumentException("Invalid Confetti/Chainmail/Spider Web options");
        }
    }
    static final class Surface {
        final StyleKit.Source src;
        final int w,h;
        final float mm;
        final boolean[] valid;
        final boolean[][] ink;
        private final VectorBooleanEngine.Session vector;
        boolean vectorEnabled(){return vector!=null;}
        boolean vectorSlot(int slot){return vector!=null&&slot>0;}
        float vectorTolerance(){return vector.tolerance;}
        Surface(StyleKit.Source s,int slots,float minCut) {this(s,slots,minCut,false);}
        Surface(StyleKit.Source s,int slots,float minCut,boolean draft) {this(s,slots,minCut,minCut,draft,false);}
        Surface(StyleKit.Source s,int slots,float minCut,float gap,boolean draft,boolean useVector) {
            vector=!draft&&useVector?VectorBooleanEngine.begin(slots,minCut,gap):null;
            src=s;
            // Physical feature floor scales with output size. Avoid enormous masks.
            float target=Math.max(.13f,minCut/3.0f);
            int max=(int)Math.min(draft?460:880,Math.max(280,Math.ceil(Math.max(s.widthMm,s.heightMm)/target)));
            float pixel=Math.max(s.widthMm,s.heightMm)/max;
            w=Math.max(3,(int)Math.ceil(s.widthMm/pixel));
            h=Math.max(3,(int)Math.ceil(s.heightMm/pixel));
            mm=s.widthMm/w;
            valid=new boolean[w*h];ink=new boolean[slots][];
            for(int i=0;i<slots;i++)if(!vectorSlot(i))ink[i]=new boolean[w*h];
            for(int y=0;y<h;y++){if(((y)&63)==0)CancellationCheckpoint.check();
                if((y&31)==0)StyleKit.checkCancelled();
                for(int x=0;x<w;x++)valid[y*w+x]=s.validAt((x+.5f)*mm,(y+.5f)*mm);
            }
        }
        boolean inside(float x,float y) {return x>=0&&y>=0&&x<src.widthMm&&y<src.heightMm&&src.validAt(x,y);}
        void fillValid(int slot){
            for(int i=0;i<valid.length;i++)if(valid[i])ink[slot][i]=true;
        }
        void disk(int slot,float x,float y,float radius) {
            if(vectorSlot(slot)){vector.add(slot,VectorBooleanEngine.ellipse(x,y,radius,radius,0,vector.tolerance));return;}
            int x0=Math.max(0,(int)Math.floor((x-radius)/mm)),x1=Math.min(w-1,(int)Math.ceil((x+radius)/mm));
            int y0=Math.max(0,(int)Math.floor((y-radius)/mm)),y1=Math.min(h-1,(int)Math.ceil((y+radius)/mm));
            float r2=radius*radius;
            for(int yy=y0;yy<=y1;yy++)for(int xx=x0;xx<=x1;xx++){if(((xx)&63)==0)CancellationCheckpoint.check();
                int i=yy*w+xx;
                if(!valid[i])continue;
                float dx=(xx+.5f)*mm-x,dy=(yy+.5f)*mm-y;
                if(dx*dx+dy*dy<=r2)ink[slot][i]=true;
            }
        }
        void segment(int slot,float ax,float ay,float bx,float by,float width) {
            if(width<=0)return;
            if(vectorSlot(slot)){vector.add(slot,VectorBooleanEngine.capsule(ax,ay,bx,by,width*.5f,vector.tolerance));return;}
            float half=width*.5f,dx=bx-ax,dy=by-ay,len2=dx*dx+dy*dy;
            if(len2<.0001f){disk(slot,ax,ay,half);return;}
            int x0=Math.max(0,(int)Math.floor((Math.min(ax,bx)-half)/mm));
            int x1=Math.min(w-1,(int)Math.ceil((Math.max(ax,bx)+half)/mm));
            int y0=Math.max(0,(int)Math.floor((Math.min(ay,by)-half)/mm));
            int y1=Math.min(h-1,(int)Math.ceil((Math.max(ay,by)+half)/mm));
            float rr=half*half;
            for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                int i=y*w+x;if(!valid[i])continue;
                float px=(x+.5f)*mm,py=(y+.5f)*mm;
                float t=StyleKit.clampF(((px-ax)*dx+(py-ay)*dy)/len2,0f,1f);
                float xx=ax+t*dx-px,yy=ay+t*dy-py;
                if(xx*xx+yy*yy<=rr)ink[slot][i]=true;
            }
        }
        void ellipseRing(int slot,float cx,float cy,float rx,float ry,float theta,float thick) {
            if(vectorSlot(slot)){
                float r0=Math.max(mm,rx-thick),r1=Math.max(mm,ry-thick);
                vector.add(slot,VectorBooleanEngine.ellipse(cx,cy,rx,ry,theta,vector.tolerance),
                    VectorBooleanEngine.ellipse(cx,cy,r0,r1,theta,vector.tolerance));return;
            }
            float cs=(float)Math.cos(theta),sn=(float)Math.sin(theta);
            float rr=Math.max(rx,ry);
            int x0=Math.max(0,(int)Math.floor((cx-rr)/mm)),x1=Math.min(w-1,(int)Math.ceil((cx+rr)/mm));
            int y0=Math.max(0,(int)Math.floor((cy-rr)/mm)),y1=Math.min(h-1,(int)Math.ceil((cy+rr)/mm));
            float rx0=Math.max(mm,rx-thick),ry0=Math.max(mm,ry-thick);
            for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                int i=y*w+x;if(!valid[i])continue;
                float dx=(x+.5f)*mm-cx,dy=(y+.5f)*mm-cy;
                float ux=cs*dx+sn*dy,uy=-sn*dx+cs*dy;
                float outer=ux*ux/(rx*rx)+uy*uy/(ry*ry);
                float inner=ux*ux/(rx0*rx0)+uy*uy/(ry0*ry0);
                if(outer<=1f&&inner>=1f)ink[slot][i]=true;
            }
        }
        void polygon(int slot,float[] p) {
            if(vectorSlot(slot)){vector.add(slot,p);return;}
            float loX=Float.POSITIVE_INFINITY,loY=Float.POSITIVE_INFINITY,
                hiX=Float.NEGATIVE_INFINITY,hiY=Float.NEGATIVE_INFINITY;
            for(int i=0;i<p.length;i+=2){if(((i)&63)==0)CancellationCheckpoint.check();loX=Math.min(loX,p[i]);hiX=Math.max(hiX,p[i]);
                loY=Math.min(loY,p[i+1]);hiY=Math.max(hiY,p[i+1]);}
            int x0=Math.max(0,(int)Math.floor(loX/mm)),x1=Math.min(w-1,(int)Math.ceil(hiX/mm));
            int y0=Math.max(0,(int)Math.floor(loY/mm)),y1=Math.min(h-1,(int)Math.ceil(hiY/mm));
            for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                int idx=y*w+x;if(!valid[idx])continue;
                float px=(x+.5f)*mm,py=(y+.5f)*mm;
                boolean inside=false;
                for(int i=0,j=p.length-2;i<p.length;j=i,i+=2){if(((i)&63)==0)CancellationCheckpoint.check();
                    float ax=p[i],ay=p[i+1],bx=p[j],by=p[j+1];
                    if((ay>py)!=(by>py)&&px<(bx-ax)*(py-ay)/(by-ay)+ax)inside=!inside;
                }
                if(inside)ink[slot][idx]=true;
            }
        }
        /** Independent physical sheets share only the immutable clip; union them concurrently. */
        private void finishVectorSheets(java.util.List<Integer> slots,java.util.List<StyleKit.Layer> layers){
            if(slots.isEmpty())return;
            if(slots.size()==1||!ParallelGeometry.enabled()){
                for(int i=0;i<slots.size();i++)layers.get(i).loops.addAll(vector.finish(slots.get(i),src.widthMm,src.heightMm));
                return;
            }
            final java.util.concurrent.atomic.AtomicBoolean token=ParallelGeometry.token();
            ParallelGeometry.invoke(new ParallelGeometry.Task<Void>(token){
                @Override Void work(){
                    java.util.ArrayList<ParallelGeometry.Task<java.util.ArrayList<float[]>>> sheets=new java.util.ArrayList<>();
                    for(final int slot:slots)sheets.add(new ParallelGeometry.Task<java.util.ArrayList<float[]>>(token){
                        @Override java.util.ArrayList<float[]> work(){return vector.finish(slot,src.widthMm,src.heightMm);}
                    });
                    invokeAll(sheets);
                    for(int i=0;i<sheets.size();i++)layers.get(i).loops.addAll(sheets.get(i).join());
                    return null;
                }
            });
        }
        StyleKit.Result finish(int[] palette,String prefix,int elements,float minCut) {
            StyleKit.Result out=new StyleKit.Result(src.widthMm,src.heightMm);
            int pw=w+2,ph=h+2;
            out.paperArgb=0xffffffff;
            final java.util.ArrayList<Integer> vectorSlots=new java.util.ArrayList<>();
            final java.util.ArrayList<StyleKit.Layer> vectorLayers=new java.util.ArrayList<>();
            for(int slot=0;slot<ink.length;slot++){
                StyleKit.checkCancelled();
                StyleKit.Layer l=new StyleKit.Layer(prefix+"-"+(slot+1),palette[slot]);
                if(vectorSlot(slot)){
                    // Build 555: filled after slot 0 has set the Snip clip, all sheets in parallel.
                    vectorSlots.add(slot);vectorLayers.add(l);
                    l.vectorGeometryLocked=true;out.layers.add(l);continue;
                }
                float[] field=new float[pw*ph];Arrays.fill(field,-1f);
                for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(ink[slot][y*w+x])field[(y+1)*pw+x+1]=1f;
                ArrayList<float[]> loops=TopographicLayersEngine.contours(field,pw,ph,0f,mm);
                for(float[] loop:loops){CancellationCheckpoint.check();
                    loop=StyleKit.removeCollinear(loop,Math.max(.012f,mm*.1f));
                    if(loop.length<6||Math.abs(StyleKit.signedArea(loop))<Math.max(mm*mm*3f,minCut*minCut*.25f))continue;
                    if(StyleKit.selfIntersections(loop,1)>0)continue;
                    l.loops.add(loop);
                    if(l.loops.size()>7000)throw new IllegalStateException(prefix+" excessive tiny fragments");
                }
                if(vector!=null&&slot==0){
                    for(int i=0;i<l.loops.size();i++)l.loops.set(i,StyleKit.clipToArtboardOrOriginal(l.loops.get(i),src.widthMm,src.heightMm));
                    vector.setClip(l.loops);
                    ArrayList<float[]> foundation=vector.foundation(src.widthMm,src.heightMm);
                    if(foundation.isEmpty())throw new IllegalStateException("Vector Snip is empty");
                    l.loops.clear();l.loops.addAll(foundation);l.vectorGeometryLocked=true;
                }
                out.layers.add(l); // Preserve physical slot index, even for empty colors.
            }
            finishVectorSheets(vectorSlots,vectorLayers);
            out.summary=prefix+" · "+elements+" elements · "+out.layers.size()+
                " registered physical sheet(s); cutter minimum "+minCut+" mm";
            return out;
        }
    }

    private static final class TonePlan {
        final int[] byTone; // darkest .. lightest => slot index
        final float[] thresholds; // ascending luminance cutoffs
        TonePlan(StyleKit.Source src,int[] colors){
            Integer[] order=new Integer[colors.length];
            for(int i=0;i<colors.length;i++)order[i]=i;
            Arrays.sort(order,(a,b)->Integer.compare(StyleKit.luma255(colors[a]),StyleKit.luma255(colors[b])));
            byTone=new int[colors.length];
            for(int i=0;i<colors.length;i++)byTone[i]=order[i];
            thresholds=new float[Math.max(0,colors.length-1)];
            if(thresholds.length==0)return;
            int cnt=0;
            for(boolean v:src.valid)if(v)cnt++;
            if(cnt==0){
                for(int i=0;i<thresholds.length;i++)thresholds[i]=(i+1f)/colors.length;
                return;
            }
            float[] vals=new float[cnt];
            int p=0;
            for(int i=0;i<src.lum.length;i++)if(src.valid[i])vals[p++]=src.lum[i];
            Arrays.sort(vals);
            for(int i=0;i<thresholds.length;i++){if(((i)&63)==0)CancellationCheckpoint.check();
                float q=(i+1f)/colors.length;
                int pick=Math.min(vals.length-1,Math.max(0,Math.round((vals.length-1)*q)));
                thresholds[i]=vals[pick];
            }
        }
        int desiredRank(float lum){
            int rank=0;
            while(rank<thresholds.length && lum>thresholds[rank])rank++;
            return rank;
        }
        int slotFor(int argb,float lum,float[][] labs){
            if(byTone.length==1)return byTone[0];
            float[] lab=StyleKit.toLab(argb);
            int rank=desiredRank(lum);
            int best=byTone[rank];
            float bestScore=Float.MAX_VALUE;
            for(int r=Math.max(0,rank-1);r<=Math.min(byTone.length-1,rank+1);r++){if(((r)&63)==0)CancellationCheckpoint.check();
                int slot=byTone[r];
                float d=StyleKit.labDist2(lab,labs[slot]);
                float penalty=(r-rank)*(r-rank)*180f;
                float score=d+penalty;
                if(score<bestScore){bestScore=score;best=slot;}
            }
            return best;
        }
    }
    public static StyleKit.Result generate(int[] pixels,int width,int height,Options o){
        if(o==null)throw new IllegalArgumentException("Options required");o.validate();
        StyleKit.Source source=new StyleKit.Source(pixels,width,height,o.allowed,o.widthMm).autoLevels(.02f,.98f);
        if(!source.anyValid())throw new IllegalArgumentException("No valid subject pixels");
        int slots=o.kind==SPIDER_WEB?1:o.layers;
        int[] colors=o.palette!=null?o.palette.clone():
            (o.kind==CONFETTI||o.kind==CHAINMAIL)?FallenConfettiEngine.autoPalette(source,slots):autoColors(source,slots,o.seed);
        if(o.kind==SPIDER_WEB&&o.palette==null)colors[0]=0xff111111;
        // A manually edited palette is a physical vinyl stack: never reorder it.
        // Only auto-generated palettes are sorted into base -> upper light tones.
        Surface surface=new Surface(source,slots,o.minCutMm,o.gapMm,o.draft,o.kind!=SPIDER_WEB);
        int n=o.kind==CONFETTI?FallenConfettiEngine.render(surface,o,colors):o.kind==CHAINMAIL?
            InterlockingChainmailEngine.render(surface,o,colors):spiderWeb(surface,o);
        StyleKit.Result result=surface.finish(colors,
            o.kind==CONFETTI?"confetti":o.kind==CHAINMAIL?"chainmail":"spider-web",n,o.minCutMm);
        if(!surface.vectorEnabled()&&!o.draft&&(o.kind==CHAINMAIL||(o.kind==CONFETTI&&
                (o.confettiMode==FallenConfettiEngine.MODE_ROUND||o.confettiMode==FallenConfettiEngine.MODE_STREAMER))))
            OrganicFlowCutSmoother.polishRasterMotifs(result,surface,o.minCutMm,o.gapMm);
        if(o.kind==CONFETTI){
            result.summary="confetti - solid silhouette base + stacked overlapping fragments - "+n+
                " scattered fragment elements - mode "+o.confettiMode+" - "+result.layers.size()+
                " registered physical sheet(s); cutter minimum "+o.minCutMm+" mm";
        }else if(o.kind==CHAINMAIL){
            result.summary="chainmail - filled silhouette L1 + "+n+
                " staggered linked ring elements, European four-in-one sheet - "+result.layers.size()+" registered physical sheet(s)";
        }
        if(surface.vectorEnabled())result.summary+=" · independent vector Boolean overlays";
        result.paperArgb=o.paperArgb;
        return result;
    }
    private static int spiderWeb(Surface s,Options o){
        float width=s.src.widthMm,height=s.src.heightMm;
        // Genuine orb-web vocabulary: hubs, spokes and connected spiral strands.
        // Multiple overlapping fields follow a portrait instead of one decoration.
        int across=Math.max(2,Math.min(4,(int)(width/80f)+1));
        int down=Math.max(2,Math.min(5,(int)(height/85f)+1));
        float dx=width/across,dy=height/down;
        float minWidth=Math.max(o.minCutMm,2.9f*s.mm);
        int segments=0;
        for(int row=0;row<down;row++)for(int col=0;col<across;col++){
            StyleKit.checkCancelled();
            long seed=mix(o.seed^row*0x9e3779b97f4a7c15L^col*0x85ebca6bL);
            float cx=(col+.5f)*dx+(rand(seed)-.5f)*dx*.22f;
            float cy=(row+.5f)*dy+(rand(seed+1)-.5f)*dy*.22f;
            int radial=10+(int)(o.detail*.075f);
            int turns=Math.max(3,(int)Math.round((5+o.detail*.095f)*Math.sqrt(.40f/o.spacingMm)));
            float rx=dx*.69f,ry=dy*.70f;
            float start=rand(seed+2)*6.2831853f;
            for(int k=0;k<radial;k++){if(((k)&63)==0)CancellationCheckpoint.check();
                float theta=start+(float)(2*Math.PI*k/radial);
                float previousX=cx,previousY=cy;
                for(int t=1;t<=turns;t++){if(((t)&63)==0)CancellationCheckpoint.check();
                    float q=t/(float)turns;
                    float jitter=1f+.055f*(rand(seed+11+k)-.5f)*o.organic/50f;
                    float x=cx+rx*q*jitter*(float)Math.cos(theta);
                    float y=cy+ry*q*jitter*(float)Math.sin(theta);
                    float lum=s.src.lumAt((previousX+x)*.5f,(previousY+y)*.5f);
                    float w=minWidth*(1.00f+.9f*(1f-lum));
                    s.segment(0,previousX,previousY,x,y,w);
                    previousX=x;previousY=y;segments++;
                }
            }
            for(int t=1;t<=turns;t++){if(((t)&63)==0)CancellationCheckpoint.check();
                float q=t/(float)turns;
                float prevX=0,prevY=0;
                for(int k=0;k<=radial;k++){if(((k)&63)==0)CancellationCheckpoint.check();
                    float theta=start+(float)(2*Math.PI*(k%radial)/radial);
                    // angular-local irregularity, stable across frame + spiral
                    float noise=rand(seed+100+t*47+k%radial*13)-.5f;
                    float radius=q*(1f+noise*.11f*o.organic/100f);
                    float x=cx+rx*radius*(float)Math.cos(theta);
                    float y=cy+ry*radius*(float)Math.sin(theta);
                    if(k>0){
                        float lum=s.src.lumAt((prevX+x)*.5f,(prevY+y)*.5f);
                        float ww=minWidth*(1f+.60f*(1-lum));
                        s.segment(0,prevX,prevY,x,y,ww);segments++;
                    }
                    prevX=x;prevY=y;
                }
            }
        }
        return segments;
    }
    private static float localEdge(StyleKit.Source src,float x,float y,float d){
        float gx=src.lumAt(x+d,y)-src.lumAt(x-d,y),
            gy=src.lumAt(x,y+d)-src.lumAt(x,y-d);
        return StyleKit.clampF((float)Math.hypot(gx,gy)*2f,0,1);
    }
    private static int[] autoColors(StyleKit.Source s,int count,long seed){
        int stride=Math.max(1,s.argb.length/3200),n=0;
        int[] sample=new int[(s.argb.length+stride-1)/stride];float[] weights=new float[sample.length];
        for(int i=0;i<s.argb.length;i+=stride)if(s.valid[i]){
            sample[n]=s.argb[i];weights[n++]=1f;
        }
        if(n==0){for(int i=0;i<s.valid.length;i++)if(s.valid[i]){
            sample[0]=s.argb[i];weights[0]=1f;n=1;break;}}
        int[] quant=StyleKit.kmeans(Arrays.copyOf(sample,n),Arrays.copyOf(weights,n),Math.min(n,count),seed);
        int[] out=new int[count];for(int i=0;i<count;i++)out[i]=quant[Math.min(i,quant.length-1)];
        sortPaletteDarkToLight(out);
        return out;
    }
    private static void sortPaletteDarkToLight(int[] colors){
        Integer[] order=new Integer[colors.length];
        for(int i=0;i<colors.length;i++)order[i]=i;
        Arrays.sort(order,(a,b)->Integer.compare(StyleKit.luma255(colors[a]),StyleKit.luma255(colors[b])));
        int[] copy=colors.clone();
        for(int i=0;i<colors.length;i++)colors[i]=copy[order[i]];
    }
    private static long mix(long z){z=(z^(z>>>30))*0xbf58476d1ce4e5b9L;
        z=(z^(z>>>27))*0x94d049bb133111ebL;return z^(z>>>31);}
    private static float rand(long n){return (mix(n)>>>40)/16777216f;}
}

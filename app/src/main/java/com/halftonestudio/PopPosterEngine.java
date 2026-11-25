package com.halftonestudio;

import java.util.*;

/**
 * Pop Panels and Poster Stencil, ported from the supplied vs-core.js reference.
 * No Android classes or global application state. All dimensions are physical mm
 * in the returned StyleKit.Result; one label field is shared by all Pop panels.
 * The physical style is rendered once, then the exact contours are translated
 * into each panel and coalesced by vinyl color (not by render instance).
 */
public final class PopPosterEngine {
    private PopPosterEngine() {}
    public static final int POP=0, POSTER=1, STACKED=0, KNOCKOUT=1;
    public static final int BG_AUTO=0,BG_TONE=1,BG_OFF=2;
    public static final int BACK_SPLIT=0,BACK_RAYS=1,BACK_SOLID=2,BACK_PAPER=3,BACK_NONE=4;
    public static final String[] POP_NAMES={"Hot pink","Mint","Lemon","Grape","Tangerine","Sky","Silver","Cherry"};
    public static final int[][] POP_COLORS={
        {0xffff4fa3,0xffffe14d,0xff2bb3e8,0xff141414},
        {0xff2bd17a,0xffff8a3d,0xff9b4dff,0xff141414},
        {0xffffd23f,0xff3fa7ff,0xffff3f5e,0xff141414},
        {0xff7a3cff,0xffff9ec7,0xff18c4b0,0xff141414},
        {0xffff7a1a,0xfffff1a8,0xffe8327c,0xff1b1b3a},
        {0xff5fd0ff,0xffffb3d1,0xffffcc00,0xff141414},
        {0xffc9ccd1,0xffffffff,0xff7a7f87,0xff111111},
        {0xffe01e37,0xffffd6a5,0xff0a9396,0xff141414}
    };
    public static final String[] POSTER_NAMES={"Classic","Red mono","Olive","Royal","Teal","Ink"};
    public static final int[][] POSTER_COLORS={
        {0xfff1e3c3,0xffa9c7cf,0xff7fa6b4,0xffc8312c,0xff1b2a3a},
        {0xfff4ede1,0xfff0a8a0,0xffe0605a,0xffb3221c,0xff2a0c0a},
        {0xffefe8cf,0xffc7c48a,0xff8f9a4f,0xffc06a2b,0xff1f2a1c},
        {0xfff3ead8,0xffd8c47a,0xffe0a820,0xff7a3c8f,0xff21123a},
        {0xffeef2ea,0xff9fd3c7,0xff3a9d9b,0xffe2583e,0xff0f2a33},
        {0xffffffff,0xffcfcfcf,0xff8c8c8c,0xff4a4a4a,0xff0d0d0d}
    };
    public static final int[][] GRIDS={{2,2},{1,1},{1,4},{3,3},{2,3},{4,1}};
    public static final String[] POP_PRESETS={"Pop classic","Pop single","Pop strip","Pop nine","Pop clean","Pop silver"};
    public static final String[] POSTER_PRESETS={"Poster classic","Poster rays","Poster 3-tone","Poster bold","Poster deluxe"};

    public interface TextMaskRenderer { byte[] rasterize(String text,int width,int height,float frame); }
    public static final class Options {
        public int style=POP, preset=0, grid=0, bgMode=BG_AUTO, posterBackdrop=BACK_SPLIT;
        public int toneCount=9, posterPalette=0, rays=24, textureTone=-1, mode=STACKED;
        public int detail=75, minIslandPx=60;
        public int[] panelPalettes={0,1,2,3,4,5,6,7,0};
        /** Override panel colors, 4 ARGB entries per panel; null means named palette. */
        public int[][] panelColors=null;
        public int[] posterColors=null;
        public float gutter=.02f,shadowT=.45f,keyT=.72f,keyOff=.012f,blockOff=.010f;
        public float contrast=1.15f,gamma=1f,blur=2f,bgTolerance=.12f,bgToneThreshold=.12f;
        public float toneBias=0,texturePeriod=9f,textureFill=.5f,frame=.03f,trapMm=.4f;
        public float widthMm=254f,minCutMm=.8f,gapMm=.5f;
        public boolean texture=true,includeBase=true,draft=false,autoLevels=true;
        public boolean[] allowed;
        public String banner="";
        /** Optional pre-rasterized glyph masks; coordinates use the label grid. */
        public byte[] textMask;
        /** Optional Android glyph rasterization callback; never used on host JVM. */
        public TextMaskRenderer textRenderer;
        public float[] manualThresholds;
        public Options copy(){Options o=new Options();o.style=style;o.preset=preset;o.grid=grid;o.bgMode=bgMode;
            o.posterBackdrop=posterBackdrop;o.toneCount=toneCount;o.posterPalette=posterPalette;o.rays=rays;
            o.textureTone=textureTone;o.mode=mode;o.detail=detail;o.minIslandPx=minIslandPx;
            o.panelPalettes=panelPalettes==null?null:panelPalettes.clone();
            if(panelColors!=null){o.panelColors=new int[panelColors.length][];for(int i=0;i<panelColors.length;i++)o.panelColors[i]=panelColors[i]==null?null:panelColors[i].clone();}
            o.posterColors=posterColors==null?null:posterColors.clone();o.gutter=gutter;o.shadowT=shadowT;o.keyT=keyT;
            o.keyOff=keyOff;o.blockOff=blockOff;o.contrast=contrast;o.gamma=gamma;o.blur=blur;
            o.bgTolerance=bgTolerance;o.bgToneThreshold=bgToneThreshold;o.toneBias=toneBias;
            o.texturePeriod=texturePeriod;o.textureFill=textureFill;o.frame=frame;o.trapMm=trapMm;
            o.widthMm=widthMm;o.minCutMm=minCutMm;o.gapMm=gapMm;o.texture=texture;o.includeBase=includeBase;
            o.draft=draft;o.autoLevels=autoLevels;o.allowed=allowed==null?null:allowed.clone();o.banner=banner;
            o.textMask=textMask==null?null:textMask.clone();o.textRenderer=textRenderer;o.manualThresholds=manualThresholds==null?null:manualThresholds.clone();
            return o;
        }
        public void check(){
            if(style<0||style>1||detail<0||detail>100||grid<0||grid>=GRIDS.length||toneCount<2||toneCount>9||
                bgMode<0||bgMode>2||posterBackdrop<0||posterBackdrop>4||posterPalette<0||posterPalette>=POSTER_COLORS.length||
                mode<0||mode>1||!Float.isFinite(widthMm)||widthMm<20||widthMm>2000||
                !Float.isFinite(minCutMm)||minCutMm<.1f||minCutMm>10||!Float.isFinite(gutter)||gutter<0||gutter>.08f||
                !Float.isFinite(trapMm)||trapMm<0||trapMm>2||!Float.isFinite(frame)||frame<0||frame>.08f||
                !Float.isFinite(toneBias)||toneBias<-.2f||toneBias>.2f||!Float.isFinite(textureFill)||textureFill<.3f||textureFill>.7f||
                !Float.isFinite(texturePeriod)||texturePeriod<4||texturePeriod>20||
                !Float.isFinite(shadowT)||shadowT<.15f||shadowT>.85f||!Float.isFinite(keyT)||keyT<.4f||keyT>.95f||
                !Float.isFinite(keyOff)||keyOff<0||keyOff>.04f||!Float.isFinite(blockOff)||blockOff<0||blockOff>.04f||
                !Float.isFinite(contrast)||contrast<=0||contrast>4||!Float.isFinite(gamma)||gamma<=0||gamma>4||
                !Float.isFinite(blur)||blur<0||blur>8||rays<8||rays>48||
                (banner!=null&&banner.length()>24))throw new IllegalArgumentException("Invalid Pop/Poster settings");
        }
    }
    public static Options preset(int style,int id,float mm){
        Options o=new Options();o.style=style;o.preset=id;o.widthMm=mm;
        if(style==POP){
            switch(id){
                case 1:o.grid=1;o.keyOff=.008f;o.blockOff=.006f;break;
                case 2:o.grid=2;o.gutter=.01f;break;
                case 3:o.grid=3;o.minIslandPx=100;break;
                case 4:o.keyOff=0;o.blockOff=0;break;
                case 5:o.grid=1;o.panelPalettes[0]=6;o.keyOff=.015f;break;
                default:break;
            }
        }else{
            // Presets affect geometry and aesthetic, not physical slot count.
            o.toneCount=9;
            switch(id){
                case 1:o.posterBackdrop=BACK_RAYS;o.banner="";break;
                case 2:o.posterPalette=1;o.posterBackdrop=BACK_SOLID;o.texture=false;break;
                case 3:o.posterPalette=5;o.posterBackdrop=BACK_PAPER;o.texture=false;o.frame=0;break;
                case 4:o.posterPalette=4;break;
                default:break;
            }
        }
        return o;
    }
    public static final class Pre {
        public final int w,h;
        public final float[] lum,dark;
        public final byte[] bg;
        Pre(int w,int h,float[] lum,byte[] bg){this.w=w;this.h=h;this.lum=lum;this.bg=bg;
            dark=new float[lum.length];for(int i=0;i<lum.length;i++)dark[i]=1-lum[i];}
    }
    public static final class Labels {
        public final int w,h,levels;public final byte[] data;public final float[] thresholds;
        Labels(int w,int h,int levels,byte[] data,float[] thresholds){this.w=w;this.h=h;this.levels=levels;this.data=data;this.thresholds=thresholds;}
    }
    private static int ci(int v,int a,int b){return Math.max(a,Math.min(b,v));}
    private static float cf(float v,float a,float b){return Math.max(a,Math.min(b,v));}
    public static float[] luminance(int[] pixels){float[] g=new float[pixels.length];
        for(int i=0;i<pixels.length;i++){int p=pixels[i];g[i]=(float)((.2126*((p>>16)&255)+.7152*((p>>8)&255)+.0722*(p&255))/255.0);}
        return g;}
    public static float[] autoLevels(float[] g){
        int[] hist=new int[256];for(float v:g)hist[ci((int)(v*255),0,255)]++;
        int N=g.length,acc=0,lo=0,hi=255;
        for(int i=0;i<256;i++){acc+=hist[i];if(acc>=N*.01){lo=i;break;}}
        acc=0;for(int i=255;i>=0;i--){acc+=hist[i];if(acc>=N*.01){hi=i;break;}}
        float a=lo/255f,b=Math.max(hi/255f,a+1/255f);float[] out=new float[N];
        for(int i=0;i<N;i++)out[i]=cf((g[i]-a)/(b-a),0,1);return out;
    }
    public static float[] adjust(float[] g,float contrast,float gamma){float[] v=new float[g.length];
        for(int i=0;i<g.length;i++){double f=Math.max(0,Math.min(1,(g[i]-0.5)*contrast+0.5));v[i]=(float)Math.pow(f,gamma);}return v;}
    static float[] box(float[] src,int w,int h,int r,boolean horizontal){
        float[] dst=new float[src.length];int len=horizontal?w:h,lines=horizontal?h:w;double norm=1d/(2*r+1);
        for(int l=0;l<lines;l++){
            int idx=horizontal?l*w:l,delta=horizontal?1:w;double sum=0;
            for(int k=-r;k<=r;k++)sum+=src[idx+ci(k,0,len-1)*delta];
            for(int k=0;k<len;k++){
                dst[idx+k*delta]=(float)(sum*norm);
                sum+=src[idx+Math.min(len-1,k+r+1)*delta]-src[idx+Math.max(0,k-r)*delta];
            }
        }return dst;
    }
    public static float[] blur(float[] g,int w,int h,int r){if(r<=0)return g;
        float[] a=g;for(int pass=0;pass<3;pass++){a=box(a,w,h,r,true);a=box(a,w,h,r,false);}return a;}
    static float borderMedian(float[] g,int w,int h){float[] border=new float[2*w+2*h];int k=0;
        for(int x=0;x<w;x++){border[k++]=g[x];border[k++]=g[(h-1)*w+x];}
        for(int y=0;y<h;y++){border[k++]=g[y*w];border[k++]=g[y*w+w-1];}
        Arrays.sort(border);return border[border.length/2];}
    public static byte[] floodBackground(float[] g,int w,int h,float tolerance){
        float med=borderMedian(g,w,h);byte[] bg=new byte[g.length];int[] q=new int[g.length];int head=0,tail=0;
        for(int x=0;x<w;x++){int i=x,j=(h-1)*w+x;
            if(bg[i]==0&&Math.abs(g[i]-med)<tolerance){bg[i]=1;q[tail++]=i;}
            if(bg[j]==0&&Math.abs(g[j]-med)<tolerance){bg[j]=1;q[tail++]=j;}}
        for(int y=0;y<h;y++){int i=y*w,j=i+w-1;
            if(bg[i]==0&&Math.abs(g[i]-med)<tolerance){bg[i]=1;q[tail++]=i;}
            if(bg[j]==0&&Math.abs(g[j]-med)<tolerance){bg[j]=1;q[tail++]=j;}}
        while(head<tail){int i=q[head++],x=i%w,y=i/w;
            if(x>0&&bg[i-1]==0&&Math.abs(g[i-1]-med)<tolerance){bg[i-1]=1;q[tail++]=i-1;}
            if(x<w-1&&bg[i+1]==0&&Math.abs(g[i+1]-med)<tolerance){bg[i+1]=1;q[tail++]=i+1;}
            if(y>0&&bg[i-w]==0&&Math.abs(g[i-w]-med)<tolerance){bg[i-w]=1;q[tail++]=i-w;}
            if(y<h-1&&bg[i+w]==0&&Math.abs(g[i+w]-med)<tolerance){bg[i+w]=1;q[tail++]=i+w;}
        }return bg;
    }
    /** Two passes of four-connected component removal; exact longest shared border rule. */
    public static byte[] cleanLabels(byte[] lab,int w,int h,int minArea){
        if(minArea<=1)return lab;int n=w*h;
        int[] comp=new int[n],stack=new int[n];int[] sizes=new int[n];
        for(int pass=0;pass<2;pass++){
            Arrays.fill(comp,-1);int count=0;
            for(int start=0;start<n;start++){
                if(comp[start]>=0)continue;
                byte label=lab[start];int size=0,sp=0;stack[sp++]=start;comp[start]=count;
                while(sp>0){int i=stack[--sp];size++;int x=i%w,y=i/w;
                    if(x>0&&comp[i-1]<0&&lab[i-1]==label){comp[i-1]=count;stack[sp++]=i-1;}
                    if(x<w-1&&comp[i+1]<0&&lab[i+1]==label){comp[i+1]=count;stack[sp++]=i+1;}
                    if(y>0&&comp[i-w]<0&&lab[i-w]==label){comp[i-w]=count;stack[sp++]=i-w;}
                    if(y<h-1&&comp[i+w]<0&&lab[i+w]==label){comp[i+w]=count;stack[sp++]=i+w;}
                }sizes[count++]=size;
            }
            int[] border=new int[count*16];boolean any=false;
            for(int i=0;i<n;i++){
                int c=comp[i];if(sizes[c]>=minArea)continue;any=true;
                int x=i%w,y=i/w,offset=16*c;
                if(x>0&&comp[i-1]!=c)border[offset+(lab[i-1]&255)]++;
                if(x<w-1&&comp[i+1]!=c)border[offset+(lab[i+1]&255)]++;
                if(y>0&&comp[i-w]!=c)border[offset+(lab[i-w]&255)]++;
                if(y<h-1&&comp[i+w]!=c)border[offset+(lab[i+w]&255)]++;
            }
            if(!any)break;
            byte[] remap=new byte[count];Arrays.fill(remap,(byte)-1);
            for(int c=0;c<count;c++)if(sizes[c]<minArea){int best=-1,bestN=0;
                for(int L=0;L<16;L++)if(border[c*16+L]>bestN){bestN=border[c*16+L];best=L;}
                remap[c]=(byte)best;
            }
            byte[] out=lab.clone();for(int i=0;i<n;i++)if(remap[comp[i]]>=0)out[i]=remap[comp[i]];
            lab=out;
        }return lab;
    }
    public static Pre preprocess(int[] px,int w,int h,Options o,boolean[] valid){
        float[] g=luminance(px);if(o.autoLevels)g=autoLevels(g);
        g=adjust(g,o.contrast,o.gamma);g=blur(g,w,h,ci(Math.round(o.blur),0,8));
        byte[] bg=new byte[w*h];
        if(o.bgMode==BG_AUTO){bg=floodBackground(g,w,h,o.bgTolerance);
            bg=cleanLabels(bg,w,h,Math.max(64,w*h/400));
            int num=0;for(byte b:bg)num+=b;
            // An ambiguous/noisy flood does not erase the subject.
            if(num<w*h*.005||num>w*h*.90)Arrays.fill(bg,(byte)0);
        }
        // Snip is physical silhouette; transparent area is never a backdrop.
        if(valid!=null)for(int i=0;i<bg.length;i++)if(!valid[i])bg[i]=1;
        return new Pre(w,h,g,bg);
    }
    public static Labels buildPop(Pre pre,Options o){
        int w=pre.w,h=pre.h;byte[] lab=new byte[w*h];
        int kdx=Math.round(o.keyOff*Math.min(w,h)),kdy=Math.round(kdx*.6f);
        int bdx=-Math.round(o.blockOff*Math.min(w,h)),bdy=Math.round(-bdx*.5f);
        float keyT=Math.max(o.shadowT+.02f,o.keyT);
        for(int y=0;y<h;y++){
            if((y&63)==0)StyleKit.checkCancelled();
            for(int x=0;x<w;x++){
                int label=0;
                if(subject(pre,o,x-bdx,y-bdy))label=1;
                if(subject(pre,o,x,y)&&pre.dark[y*w+x]>=o.shadowT)label=2;
                if(subject(pre,o,x-kdx,y-kdy)&&pre.dark[(y-kdy)*w+x-kdx]>=keyT)label=3;
                lab[y*w+x]=(byte)label;
            }
        }
        return new Labels(w,h,4,cleanLabels(lab,w,h,o.minIslandPx),null);
    }
    private static boolean subject(Pre p,Options o,int x,int y){
        if(x<0||y<0||x>=p.w||y>=p.h)return false;int i=y*p.w+x;
        return o.bgMode==BG_TONE?p.dark[i]>=o.bgToneThreshold:p.bg[i]==0;
    }
    public static Labels buildPoster(Pre pre,Options o){
        int w=pre.w,h=pre.h,n=o.toneCount;float[] th;
        if(o.manualThresholds!=null&&o.manualThresholds.length==n-1){th=o.manualThresholds.clone();Arrays.sort(th);}
        else{
            boolean useBg=o.bgMode==BG_AUTO;int step=Math.max(1,w*h/40000),m=0;
            float[] sample=new float[(w*h+step-1)/step];
            for(int i=0;i<w*h;i+=step)if(!useBg||pre.bg[i]==0)sample[m++]=pre.dark[i];
            if(m==0){for(int i=0;i<w*h;i+=step)sample[m++]=pre.dark[i];}
            sample=Arrays.copyOf(sample,m);Arrays.sort(sample);th=new float[n-1];
            for(int k=1;k<n;k++)th[k-1]=sample[ci(Math.round(cf(k/(float)n+o.toneBias,0,1)*(m-1)),0,m-1)];
        }
        byte[] lab=new byte[w*h];
        for(int i=0;i<lab.length;i++){int L=0;for(int k=0;k<th.length;k++)if(pre.dark[i]>=th[k])L=k+1;lab[i]=(byte)L;}
        if(o.texture&&n>=3){int lvl=o.textureTone<0?n-2:ci(o.textureTone,1,n-1);
            // Honor an actual 1.5 mm stripe when parameters are translated into pixels.
            float period=o.texturePeriod,fill=o.textureFill;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                int i=y*w+x;if(lab[i]!=(byte)lvl||(o.bgMode==BG_AUTO&&pre.bg[i]!=0))continue;
                double u=((x+y)*Math.sqrt(.5))%period;
                if(u>=period*fill)lab[i]=(byte)(lvl-1);
            }
        }
        lab=cleanLabels(lab,w,h,o.minIslandPx);
        if(o.bgMode==BG_AUTO&&o.posterBackdrop!=BACK_NONE){
            int red=n-2>=1?n-2:n-1,light=Math.min(1,n-1);
            double cx=w*.5,cy=h*.42;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                int i=y*w+x;if(pre.bg[i]==0)continue;int L=0;
                switch(o.posterBackdrop){
                    case BACK_SOLID:L=red;break;
                    case BACK_RAYS:double a=Math.atan2(y-cy,x-cx)+Math.PI;
                        L=(((int)Math.floor(a/(2*Math.PI)*o.rays))&1)!=0?red:0;break;
                    case BACK_PAPER:L=0;break;
                    default:L=x<w/2?red:light;
                }lab[i]=(byte)L;
            }
        }
        int frame=Math.round(o.frame*Math.min(w,h));
        if(frame>0)for(int y=0;y<h;y++)for(int x=0;x<w;x++)
            if(x<frame||y<frame||x>=w-frame||y>=h-frame)lab[y*w+x]=0;
        if(o.banner!=null&&!o.banner.isEmpty()){
            int bh=Math.round(h*.14f),y0=h-frame-bh;
            for(int y=Math.max(0,y0);y<h-frame;y++)for(int x=frame;x<w-frame;x++){
                int i=y*w+x;lab[i]=(byte)(o.textMask!=null&&o.textMask.length==lab.length&&o.textMask[i]!=0?0:n-1);
            }
        }
        return new Labels(w,h,n,lab,th);
    }
    public static int[] posterPalette(int palette,int tones){
        if(tones<2||tones>9)throw new IllegalArgumentException("Poster layer count");
        int[] src=POSTER_COLORS[ci(palette,0,POSTER_COLORS.length-1)];
        // Preserve the historical 2–5 tone palettes for old projects.
        if(tones<=5){
            int[] ids=tones==2?new int[]{0,4}:tones==3?new int[]{0,3,4}:
                tones==4?new int[]{0,1,3,4}:new int[]{0,1,2,3,4};
            int[] out=new int[tones];for(int i=0;i<tones;i++)out[i]=src[ids[i]];return out;
        }
        // Nine real, independently editable vinyl colors interpolated from
        // each named poster palette; L1 is the light solid Snip foundation.
        int[] out=new int[tones];
        for(int i=0;i<tones;i++){
            double pos=4d*i/(tones-1);int a=Math.min(3,(int)pos),b=a+1;
            double t=pos-a;int left=src[a],right=src[b],color=0xff000000;
            for(int shift:new int[]{0,8,16}){
                int component=(int)Math.round(((left>>shift)&255)*(1-t)+((right>>shift)&255)*t);
                color|=ci(component,0,255)<<shift;
            }
            out[i]=color;
        }
        return out;
    }
    /** Automatic photo palette: balanced nine light-to-dark tonal clusters.
     * Not a nearest-neighbor replacement for an explicitly edited vinyl palette.
     * Sampling uses the unaltered photo; Snip affects selection, not tone values.
     */
    public static int[] photoTonePalette(int[] pixels,int w,int h,boolean[] allowed,int count,int[] fallback){
        if(count<2||count>9||pixels==null||(long)w*h!=pixels.length||
            (allowed!=null&&allowed.length!=pixels.length))throw new IllegalArgumentException("Photo palette size");
        long[] hist=new long[256],red=new long[256],green=new long[256],blue=new long[256];
        long total=0;int low=255,high=0;
        for(int i=0;i<pixels.length;i++){
            if((i&65535)==0)StyleKit.checkCancelled();
            if(allowed!=null&&!allowed[i])continue;
            int p=pixels[i];if((p>>>24)<128)continue;
            int r=(p>>16)&255,g=(p>>8)&255,b=p&255;
            int luminance=ci((int)Math.round(.2126*r+.7152*g+.0722*b),0,255);
            hist[luminance]++;red[luminance]+=r;green[luminance]+=g;blue[luminance]+=b;
            low=Math.min(low,luminance);high=Math.max(high,luminance);total++;
        }
        if(total<count*4L||high-low<18)return fallback.clone();
        int[] result=new int[count];long from=0;
        for(int slot=0;slot<count;slot++){
            long to=total*(slot+1L)/count;
            long rank=0,weight=0,rr=0,gg=0,bb=0;
            for(int k=255;k>=0;k--){
                long next=rank+hist[k];long overlap=Math.max(0,Math.min(next,to)-Math.max(rank,from));
                if(overlap>0){weight+=overlap;rr+=overlap*(red[k]/hist[k]);gg+=overlap*(green[k]/hist[k]);bb+=overlap*(blue[k]/hist[k]);}
                rank=next;if(rank>=to)break;
            }
            result[slot]=weight>0?0xff000000|(((int)(rr/weight))<<16)|
                (((int)(gg/weight))<<8)|(int)(bb/weight):fallback[slot];
            from=to;
        }
        return result;
    }
    /** Create nested masks. The knockout trap is allowed only under a higher color. */
    public static byte[][] layerMasks(Labels lab,int mode,int trapPx){
        int w=lab.w,h=lab.h;byte[][] masks=new byte[lab.levels][w*h];
        for(int L=0;L<lab.levels;L++){
            byte[] m=masks[L];for(int i=0;i<m.length;i++)m[i]=(byte)(mode==STACKED?(lab.data[i]>=L?1:0):(lab.data[i]==L?1:0));
            if(mode==KNOCKOUT&&trapPx>0&&L<lab.levels-1){byte[] d=dilate(m,w,h,trapPx);
                for(int i=0;i<m.length;i++)if(d[i]!=0&&lab.data[i]>L)m[i]=1;}
        }return masks;
    }
    public static byte[] dilate(byte[] mask,int w,int h,int r){
        if(r<=0)return mask;byte[] tmp=new byte[w*h],out=new byte[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            for(int k=Math.max(0,x-r);k<=Math.min(w-1,x+r);k++)if(mask[y*w+k]!=0){tmp[y*w+x]=1;break;}}
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            for(int k=Math.max(0,y-r);k<=Math.min(h-1,y+r);k++)if(tmp[k*w+x]!=0){out[y*w+x]=1;break;}}
        return out;
    }
    /** Directed crack tracing, right turn at saddles. Y axis points down. */
    static ArrayList<float[]> trace(byte[] mask,int w,int h){
        int max=w*h*4,vw=w+1,nv=vw*(h+1);
        int[] sx=new int[max],sy=new int[max],dir=new int[max],a=new int[nv],b=new int[nv];
        Arrays.fill(a,-1);Arrays.fill(b,-1);int edges=0;
        for(int y=0;y<h;y++){
            if((y&63)==0)StyleKit.checkCancelled();
            for(int x=0;x<w;x++){
                int i=y*w+x;if(mask[i]==0)continue;
                if(y==0||mask[i-w]==0){int v=y*vw+x;sx[edges]=x;sy[edges]=y;dir[edges]=0;if(a[v]<0)a[v]=edges;else b[v]=edges;edges++;}
                if(x==w-1||mask[i+1]==0){int v=y*vw+x+1;sx[edges]=x+1;sy[edges]=y;dir[edges]=1;if(a[v]<0)a[v]=edges;else b[v]=edges;edges++;}
                if(y==h-1||mask[i+w]==0){int v=(y+1)*vw+x+1;sx[edges]=x+1;sy[edges]=y+1;dir[edges]=2;if(a[v]<0)a[v]=edges;else b[v]=edges;edges++;}
                if(x==0||mask[i-1]==0){int v=(y+1)*vw+x;sx[edges]=x;sy[edges]=y+1;dir[edges]=3;if(a[v]<0)a[v]=edges;else b[v]=edges;edges++;}
            }
        }
        byte[] used=new byte[edges];ArrayList<float[]> loops=new ArrayList<>();
        final int[] dx={1,0,-1,0},dy={0,1,0,-1};
        for(int first=0;first<edges;first++){
            if(used[first]!=0)continue;int[] vertices=new int[Math.min(edges*2,128)];int count=0,e=first,guard=0;
            while(e>=0&&used[e]==0&&guard++<=edges){
                used[e]=1;if(count+2>vertices.length)vertices=Arrays.copyOf(vertices,Math.min(edges*2,vertices.length*2));
                vertices[count++]=sx[e];vertices[count++]=sy[e];
                int vertex=(sy[e]+dy[dir[e]])*vw+sx[e]+dx[dir[e]];
                int na=a[vertex],nb=b[vertex];boolean ua=na>=0&&used[na]==0,ub=nb>=0&&used[nb]==0;
                if(ua&&ub){int right=(dir[e]+1)%4;e=dir[na]==right?na:dir[nb]==right?nb:na;}
                else e=ua?na:ub?nb:-1;
            }
            if(count>=8){float[] poly=new float[count];for(int i=0;i<count;i++)poly[i]=vertices[i];loops.add(poly);}
        }
        return loops;
    }
    static double area(float[] p){double a=0;for(int i=0;i<p.length;i+=2){int j=(i+2)%p.length;a+=p[i]*p[j+1]-p[j]*p[i+1];}return a*.5;}
    static float[] simplifyOpen(float[] p,float eps){
        int n=p.length/2;if(n<3)return p.clone();byte[] keep=new byte[n];keep[0]=keep[n-1]=1;
        int[] a=new int[n*2],b=new int[n*2];int sp=0;a[sp]=0;b[sp++]=n-1;
        while(sp>0){sp--;int lo=a[sp],hi=b[sp];double ax=p[2*lo],ay=p[2*lo+1],bx=p[2*hi],by=p[2*hi+1];
            double dx=bx-ax,dy=by-ay,len=Math.max(1e-9,Math.hypot(dx,dy)),md=-1;int mi=-1;
            for(int i=lo+1;i<hi;i++){double d=Math.abs(dy*p[2*i]-dx*p[2*i+1]+bx*ay-by*ax)/len;
                if(d>md){md=d;mi=i;}}
            if(md>eps){keep[mi]=1;a[sp]=lo;b[sp++]=mi;a[sp]=mi;b[sp++]=hi;}
        }
        int m=0;for(byte v:keep)if(v!=0)m++;float[] out=new float[m*2];int k=0;
        for(int i=0;i<n;i++)if(keep[i]!=0){out[k++]=p[2*i];out[k++]=p[2*i+1];}return out;
    }
    static float[] simplifyClosed(float[] p,float eps){
        int n=p.length/2;if(n<4)return p.clone();int far=0;double best=-1;
        for(int i=1;i<n;i++){double d=Math.hypot(p[2*i]-p[0],p[2*i+1]-p[1]);if(d>best){best=d;far=i;}}
        float[] one=Arrays.copyOf(p,2*far+2),two=new float[2*(n-far+1)];
        System.arraycopy(p,2*far,two,0,2*(n-far));two[two.length-2]=p[0];two[two.length-1]=p[1];
        float[] a=simplifyOpen(one,eps),b=simplifyOpen(two,eps);
        float[] out=new float[a.length+b.length-4];System.arraycopy(a,0,out,0,a.length-2);
        System.arraycopy(b,0,out,a.length-2,b.length-2);return out;
    }
    static float[] chaikin(float[] p,int iters,float maxCut){
        for(int pass=0;pass<iters;pass++){
            int n=p.length/2;float[] out=new float[p.length*2];
            for(int i=0;i<n;i++){int j=(i+1)%n;float x=p[2*i],y=p[2*i+1],xx=p[2*j],yy=p[2*j+1];
                double len=Math.max(1e-9,Math.hypot(xx-x,yy-y)),c=Math.min(.25,maxCut/len);
                out[4*i]=(float)(x+c*(xx-x));out[4*i+1]=(float)(y+c*(yy-y));
                out[4*i+2]=(float)(xx-c*(xx-x));out[4*i+3]=(float)(yy-c*(yy-y));
            }p=out;
        }return p;
    }
    public static ArrayList<float[]> vectorize(byte[] mask,int w,int h){
        ArrayList<float[]> out=new ArrayList<>();
        for(float[] raw:trace(mask,w,h)){
            int n=raw.length/2;float[] mid=new float[raw.length];
            for(int i=0;i<n;i++){int j=(i+1)%n;
                float x=(raw[2*i]+raw[2*j])*.5f,y=(raw[2*i+1]+raw[2*j+1])*.5f;
                mid[2*i]=x<=.5f?0:x>=w-.5f?w:x;
                mid[2*i+1]=y<=.5f?0:y>=h-.5f?h:y;}
            float[] p=simplifyClosed(mid,.7f);if(p.length<6)continue;
            p=chaikin(p,2,2f);if(Math.abs(area(p))<6)continue;
            out.add(p);
        }return out;
    }
    private static final class Panel {final int x,y;final int[] colors;
        Panel(int x,int y,int[] c){this.x=x;this.y=y;this.colors=c;}}
    /** Build SVG/DXF-friendly filled paths, one physical sheet per unique vinyl color. */
    public static StyleKit.Result generate(int[] pixels,int w,int h,Options o){
        o.check();if(pixels==null||w<=0||h<=0||(long)w*h!=pixels.length)throw new IllegalArgumentException("Photo dimensions mismatch");
        if(o.allowed!=null&&o.allowed.length!=pixels.length)throw new IllegalArgumentException("Snip size mismatch");
        int cols=o.style==POP?GRIDS[o.grid][0]:1,rows=o.style==POP?GRIDS[o.grid][1]:1;
        // Use the exact integer gutter that will be rasterized. The physical artboard
        // width must not drift after rounding gaps, even at small artwork sizes.
        // High-detail exports retain substantially more photograph structure than the
        // rapid draft, but never decode the original 48 MP image into a giant array.
        // Oversampling beyond four samples per cut-width has little physical value.
        int photoLimit=o.draft?Math.min(300,180+o.detail):Math.min(1600,600+o.detail*10);
        int physicalLimit=Math.max(250,(int)Math.ceil(o.widthMm/o.minCutMm*5f));
        int limit=Math.min(photoLimit,physicalLimit);
        int down=Math.max(1,(int)Math.ceil(Math.max(w,h)/(double)limit));
        int sw=(w+down-1)/down,sh=(h+down-1)/down;
        int gap=Math.round(o.gutter*Math.min(sw,sh));
        float mmScale=o.widthMm/(cols*sw+(cols-1)*gap);
        float panelWidthMm=sw*mmScale;
        int[] rgba=new int[sw*sh];boolean[] valid=new boolean[sw*sh];
        for(int y=0;y<sh;y++){
            if((y&63)==0)StyleKit.checkCancelled();
            for(int x=0;x<sw;x++){
                long r=0,g=0,b=0;int count=0,total=0,inside=0;
                for(int yy=y*down;yy<Math.min(h,(y+1)*down);yy++)for(int xx=x*down;xx<Math.min(w,(x+1)*down);xx++){
                    int i=yy*w+xx,p=pixels[i];total++;
                    if((p>>>24)<128)continue;
                    // Sample original photo tones even outside the Snip. Clip only the
                    // final colored masks, preventing a white halo or lost near-edge lines.
                    r+=(p>>16)&255;g+=(p>>8)&255;b+=p&255;count++;
                    if(o.allowed==null||o.allowed[i])inside++;
                }
                int i=y*sw+x;valid[i]=inside>0&&inside*2>=total;
                rgba[i]=count==0?0xffffffff:0xff000000|((int)(r/count)<<16)|((int)(g/count)<<8)|(int)(b/count);
            }
        }
        Options adjusted=o.copy();
        // Detail cleanup is never less than a physical 2 mm square or selected cutter minimum.
        float mmPx=panelWidthMm/sw;float minimumMm=Math.max(2f,o.minCutMm);
        adjusted.minIslandPx=Math.max(o.minIslandPx,(int)Math.ceil(minimumMm*minimumMm/(mmPx*mmPx)));
        // Lower detail removes more residual islands, while the cutter physical floor never weakens.
        adjusted.minIslandPx=Math.max(adjusted.minIslandPx,(int)Math.ceil((115-o.detail)*.045f*adjusted.minIslandPx));
        if(adjusted.style==POSTER&&adjusted.texture){
            float minStripe=Math.max(1.5f,adjusted.minCutMm);
            adjusted.texturePeriod=Math.max(adjusted.texturePeriod,minStripe/mmPx/adjusted.textureFill);
        }
        if(adjusted.style==POSTER&&adjusted.banner!=null&&!adjusted.banner.isEmpty()&&adjusted.textRenderer!=null)
            adjusted.textMask=adjusted.textRenderer.rasterize(adjusted.banner,sw,sh,adjusted.frame);
        Pre pre=preprocess(rgba,sw,sh,adjusted,valid);
        Labels labels=adjusted.style==POP?buildPop(pre,adjusted):buildPoster(pre,adjusted);
        int totalW=cols*sw+(cols-1)*gap,totalH=rows*sh+(rows-1)*gap;
        StyleKit.Result result=new StyleKit.Result(totalW*mmScale,totalH*mmScale);
        result.paperArgb=adjusted.style==POP?POP_COLORS[0][0]:POSTER_COLORS[adjusted.posterPalette][0];
        int trapPx=ci(Math.round(o.trapMm/mmPx),0,12);
        byte[][] masks=layerMasks(labels,o.mode,trapPx);
        // Poster L1 always forms the filled silhouette, even in knockout mode;
        // otherwise the base disappears and the nine-slot stack is incomplete.
        if(adjusted.style==POSTER&&adjusted.includeBase)Arrays.fill(masks[0],(byte)1);
        for(byte[] mask:masks)for(int i=0;i<mask.length;i++)if(!valid[i])mask[i]=0;
        @SuppressWarnings("unchecked") ArrayList<float[]>[] loops=new ArrayList[labels.levels];
        for(int L=0;L<labels.levels;L++){
            if(L==0&&o.mode==STACKED){
                boolean full=true;for(boolean v:valid)if(!v){full=false;break;}
                if(full){loops[L]=new ArrayList<>();loops[L].add(new float[]{0,0,sw,0,sw,sh,0,sh});continue;}
            }
            loops[L]=vectorize(masks[L],sw,sh);
        }
        ArrayList<Panel> panels=new ArrayList<>();int count=cols*rows;
        for(int p=0;p<count;p++){
            int[] colors;
            if(o.style==POP){
                if(o.panelColors!=null&&p<o.panelColors.length&&o.panelColors[p]!=null&&o.panelColors[p].length==4)
                    colors=o.panelColors[p].clone();
                else{int index=o.panelPalettes!=null&&p<o.panelPalettes.length?o.panelPalettes[p]:p%4;
                    colors=POP_COLORS[Math.floorMod(index,POP_COLORS.length)].clone();}
            }else colors=o.posterColors!=null&&o.posterColors.length==o.toneCount?o.posterColors.clone():posterPalette(o.posterPalette,o.toneCount);
            panels.add(new Panel((p%cols)*(sw+gap),(p/cols)*(sh+gap),colors));
        }
        if(o.style==POSTER){
            // Never group by HEX. L1..L9 remain separate physical sheets even
            // when users intentionally select identical colors in two slots.
            for(int L=0;L<labels.levels;L++){
                StyleKit.Layer layer=new StyleKit.Layer("poster-vinyl-"+(L+1),panels.get(0).colors[L]);
                for(float[] poly:loops[L]){
                    float[] mm=new float[poly.length];
                    for(int j=0;j<poly.length;j++)mm[j]=poly[j]*mmScale;
                    layer.loops.add(mm);
                }
                if(!layer.loops.isEmpty())result.layers.add(layer);
            }
        }else{
            LinkedHashMap<Integer,StyleKit.Layer> byColor=new LinkedHashMap<>();
            for(int L=0;L<labels.levels;L++){
                if(L==0&&!o.includeBase)continue;
                for(int p=0;p<count;p++){
                    int color=panels.get(p).colors[L];StyleKit.Layer layer=byColor.get(color);
                    if(layer==null){layer=new StyleKit.Layer("pop-vinyl-"+(byColor.size()+1),color);byColor.put(color,layer);}
                    float ox=panels.get(p).x,oy=panels.get(p).y;
                    for(float[] poly:loops[L]){
                        float[] mm=new float[poly.length];
                        for(int j=0;j<poly.length;j+=2){mm[j]=(poly[j]+ox)*mmScale;mm[j+1]=(poly[j+1]+oy)*mmScale;}
                        layer.loops.add(mm);
                    }
                }
            }
            for(StyleKit.Layer layer:byColor.values())if(!layer.loops.isEmpty())result.layers.add(layer);
        }
        result.summary=(o.style==POP?"Pop Panels "+cols+"×"+rows:"Poster Stencil · "+o.toneCount+" physical colors")+
            " · "+result.layers.size()+" cut sheets · "+(o.mode==STACKED?"stacked":"knockout")+
            " · "+String.format(Locale.US,"%.1f",result.widthMm)+" mm";
        return result;
    }
}

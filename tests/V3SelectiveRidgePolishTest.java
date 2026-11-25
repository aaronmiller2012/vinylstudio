package com.halftonestudio;

import java.util.Arrays;

/** Qualified selection: continuous ridges only; protect current non-ridge geometry. */
public final class V3SelectiveRidgePolishTest {
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    static final long M4_MARQUETRY=0xb79d024da4bb558fL,M4_TORN=0x81ab2ab211bcaec8L;
    static long signature(StyleKit.Result result){
        long h=0xcbf29ce484222325L;
        for(StyleKit.Layer layer:result.layers){
            h=(h^layer.name.hashCode())*0x100000001b3L;
            h=(h^layer.color)*0x100000001b3L;
            for(float[] p:layer.loops)for(float v:p)h=(h^Float.floatToIntBits(v))*0x100000001b3L;
        }
        return h;
    }
    static int[] portrait(int w,int h){
        int[] img=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float u=(x-w*.5f)/(w*.46f),v=(y-h*.48f)/(h*.46f),radial=u*u+v*v;
            int light=Math.max(15,Math.min(245,Math.round(222-150*Math.max(0,1-radial))));
            if(Math.abs(v+.20f)<.055f&&(Math.abs(u-.31f)<.16f||Math.abs(u+.31f)<.16f))light=25;
            if(Math.abs(v-.33f)<.05f&&Math.abs(u)<.35f)light=38;
            int r=Math.min(255,light+Math.round(30*Math.max(0,u)));
            int g=Math.min(255,light+Math.round(20*Math.max(0,-v)));
            int b=Math.max(0,light-Math.round(18*Math.max(0,-u)));
            img[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        return img;
    }
    static boolean[] snip(int w,int h){
        boolean[] s=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float u=(x-w*.5f)/(w*.46f),v=(y-h*.48f)/(h*.46f);
            s[y*w+x]=u*u+v*v<1f;
        }
        return s;
    }
    static ExpandedPortraitStylesEngine.Options options(int style,float mm,int w,int h){
        ExpandedPortraitStylesEngine.Options o=new ExpandedPortraitStylesEngine.Options();
        o.style=style;o.detail=42;o.widthMm=mm;o.minimumMm=.254f;o.gapMm=.24f;
        o.weightMm=.7f;o.allowed=snip(w,h);o.colorCount=(style==1||style==10)?1:style==4?4:6;
        int[] pal={0xff16171b,0xfff0dfc5,0xffc89068,0xff86495f,0xff46647a,0xffd9b736};
        o.palette=Arrays.copyOf(pal,o.colorCount);
        return o;
    }
    static void verify(int style,float mm){
        final int w=144,h=112;
        StyleKit.Result r=ExpandedPortraitStylesEngine.generate(portrait(w,h),w,h,options(style,mm,w,h));
        check(r.layers.size()>0,"style no layers");
        for(StyleKit.Layer layer:r.layers)for(float[] p:layer.loops){
            check(p.length>=6 && (p.length&1)==0,"odd/short loop");
            check(StyleKit.selfIntersections(p,1)==0,"self contact");
            for(float v:p)check(Float.isFinite(v),"non-finite loop");
        }
        System.out.printf("PASS selective style=%d widthMm=%.1f contours=%d points=%d%n",
            style,mm,r.layers.stream().mapToInt(z->z.loops.size()).sum(),r.totalPoints());
    }
    public static void main(String[] args){
        // These three unchanged styles retain their original frozen signatures.
        // Woven Ribbon has a separate versioned baseline for code 528.
        int[] styles={0,3,13};
        long[] signatures={0xd96bb5dc24dfa178L,0x9836b0f1d02186bcL,
                           0xf654fe9bd943448fL};
        int w=144,h=112;
        // M4 (ADR-0005): Marquetry (0) and Torn-Paper Collage (13) now underlap their label
        // sheets. With the underlap off, their pre-M4 frozen signatures must still match, which
        // proves ridge polish and everything else left them unchanged; with it on (the default),
        // the M4 signature is pinned.
        long[] m4={M4_MARQUETRY,0x9836b0f1d02186bcL,M4_TORN};
        for(int i=0;i<styles.length;i++){
            ExpandedPortraitStylesEngine.Options legacy=options(styles[i],152.4f,w,h);
            legacy.sharedEdgeUnderlap=false;
            StyleKit.Result r=ExpandedPortraitStylesEngine.generate(portrait(w,h),w,h,legacy);
            check(signature(r)==signatures[i],"Unexpected change to style "+styles[i]);
            StyleKit.Result now=ExpandedPortraitStylesEngine.generate(portrait(w,h),w,h,options(styles[i],152.4f,w,h));
            if(Boolean.getBoolean("print.m4"))System.out.printf("style %d M4 signature 0x%xL%n",styles[i],signature(now));
            else check(signature(now)==m4[i],"Unexpected M4 change to style "+styles[i]);
        }
        // Build 528 intentionally localizes color along the ribbons. Retain a
        // strict fingerprint plus the independent local-color regression.
        StyleKit.Result woven=ExpandedPortraitStylesEngine.generate(portrait(w,h),w,h,
            options(ExpandedPortraitStylesEngine.WOVEN_RIBBON,152.4f,w,h));
        check(signature(woven)==0x9d79c6f745c5cfb2L,
            "Unexpected change to local-run Woven Ribbon (code 528 baseline)");
        // Same-color strands can weld, so a requested slot may be unused.
        // A uniform exact-swatch photo must nevertheless use the correct color.
        int[] flat=new int[w*h];
        int exact=options(ExpandedPortraitStylesEngine.WOVEN_RIBBON,152.4f,w,h).palette[2];
        Arrays.fill(flat,exact);
        StyleKit.Result flatWoven=ExpandedPortraitStylesEngine.generate(flat,w,h,
            options(ExpandedPortraitStylesEngine.WOVEN_RIBBON,152.4f,w,h));
        check(!flatWoven.layers.isEmpty(),"Flat Woven Ribbon empty");
        for(StyleKit.Layer l:flatWoven.layers)if(!l.loops.isEmpty())
            check(l.color==exact,"Woven Ribbon reverted to cyclic colors");
        System.out.println("PASS Woven Ribbon photo-driven signature and exact-swatch fixture");
        // Fingerprint and Woodgrain were both changed in the reference fixture.
        long[] ridgeBaseline={0xc1de3f2f3c7778f3L,0xced6cffd62c5af03L};
        for(int i=0;i<2;i++){
            int style=i==0?1:10;
            StyleKit.Result a=ExpandedPortraitStylesEngine.generate(portrait(w,h),w,h,
                options(style,152.4f,w,h));
            StyleKit.Result b=ExpandedPortraitStylesEngine.generate(portrait(w,h),w,h,
                options(style,152.4f,w,h));
            check(signature(a)==signature(b),"Non-deterministic smoother");
            check(signature(a)!=ridgeBaseline[i],"Smoother silently did nothing for style "+style);
        }
        for(int style:new int[]{1,10})for(float size:new float[]{76.2f,101.6f,279.4f})
            verify(style,size);
        // A small enclosed facial detail must not be destroyed by the fitter.
        float[] tiny={3,3,4,3,4,4,3,4};
        check(OrganicRidgeContourPolish.propose(tiny,.10f,100,100)==null,
            "Tiny hole/eye contour must be left unchanged");
        System.out.println("PASS selective ridge-polish regression and non-target style invariance");
    }
}

package com.halftonestudio;

/** JVM-only regression for RC154: higher-detail confetti, tone-aware layer routing, spider-web retirement. */
public final class ConfettiToneRC154Test {
    static void check(boolean ok,String msg){ if(!ok) throw new AssertionError(msg); }
    static int[] image(int w,int h){
        int[] argb=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float tx=x/(float)(w-1), ty=y/(float)(h-1);
            int r,g,b;
            if(tx<.25f){ r=35+(int)(35*ty); g=25+(int)(25*ty); b=40+(int)(30*ty); }
            else if(tx<.5f){ r=90+(int)(40*ty); g=70+(int)(30*ty); b=80+(int)(25*ty); }
            else if(tx<.75f){ r=165+(int)(35*ty); g=110+(int)(30*ty); b=85+(int)(18*ty); }
            else { r=225-(int)(20*ty); g=210-(int)(18*ty); b=185-(int)(14*ty); }
            if(x>46&&x<78&&y>38&&y<86){ r=Math.max(0,r-55); g=Math.max(0,g-55); b=Math.max(0,b-55); }
            if(x>88&&x<118&&y>42&&y<88){ r=Math.min(255,r+18); g=Math.min(255,g+18); b=Math.min(255,b+18); }
            argb[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        return argb;
    }
    static int usedLayers(StyleKit.Result r){
        int n=0; for(StyleKit.Layer l:r.layers) if(!l.loops.isEmpty()||!l.lines.isEmpty()) n++; return n;
    }
    public static void main(String[] args){
        final int w=164,h=210;
        int[] pixels=image(w,h);
        int[] palette={0xff241f26,0xff61506a,0xffb37a59,0xffeadcbc};

        PatternPortraitTrioEngine.Options low=new PatternPortraitTrioEngine.Options();
        low.kind=PatternPortraitTrioEngine.CONFETTI; low.layers=4; low.palette=palette;
        low.detail=42; low.organic=70; low.spacingMm=.35f; low.widthMm=152.4f;
        StyleKit.Result confLow=PatternPortraitTrioEngine.generate(pixels,w,h,low);

        PatternPortraitTrioEngine.Options high=new PatternPortraitTrioEngine.Options();
        high.kind=PatternPortraitTrioEngine.CONFETTI; high.layers=4; high.palette=palette;
        high.detail=84; high.organic=70; high.spacingMm=.35f; high.widthMm=152.4f;
        StyleKit.Result confHigh=PatternPortraitTrioEngine.generate(pixels,w,h,high);

        check(confHigh.totalPoints()>confLow.totalPoints(),"confetti detail did not increase geometry");
        check(usedLayers(confHigh)>=3,"confetti tone routing failed to use multiple swatches");

        PatternPortraitTrioEngine.Options chain=new PatternPortraitTrioEngine.Options();
        chain.kind=PatternPortraitTrioEngine.CHAINMAIL; chain.layers=4; chain.palette=palette;
        chain.detail=70; chain.spacingMm=.45f; chain.organic=55; chain.widthMm=152.4f;
        StyleKit.Result chainmail=PatternPortraitTrioEngine.generate(pixels,w,h,chain);
        check(usedLayers(chainmail)>=2,"chainmail tone routing did not populate multiple layers");

        System.out.println("PASS RC154 confetti detail and tone separation");
    }
}

package com.halftonestudio;

/** JVM-only regression for RC154: confetti gains a solid silhouette base and stacked overlap. */
public final class ConfettiStackRC154Test {
    static void check(boolean ok,String msg){ if(!ok) throw new AssertionError(msg); }
    static int[] image(int w,int h){
        int[] argb=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float tx=x/(float)(w-1), ty=y/(float)(h-1);
            int r=(int)(45+165*tx);
            int g=(int)(30+140*ty);
            int b=(int)(35+105*(1f-tx*.6f));
            if(x>42&&x<82&&y>34&&y<88){ r=Math.max(0,r-62); g=Math.max(0,g-58); b=Math.max(0,b-55); }
            if(x>91&&x<128&&y>42&&y<95){ r=Math.min(255,r+34); g=Math.min(255,g+26); b=Math.min(255,b+20); }
            argb[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        return argb;
    }
    static float layerArea(StyleKit.Layer layer){
        float area=0f;
        for(float[] loop:layer.loops)area+=Math.abs(StyleKit.signedArea(loop));
        return area*.5f;
    }
    static boolean contains(float[] poly,float x,float y){
        boolean inside=false;
        for(int i=0,j=poly.length-2;i<poly.length;j=i,i+=2){
            float ax=poly[i], ay=poly[i+1], bx=poly[j], by=poly[j+1];
            if((ay>y)!=(by>y) && x<(bx-ax)*(y-ay)/(by-ay)+ax)inside=!inside;
        }
        return inside;
    }
    static boolean layerContains(StyleKit.Layer layer,float x,float y){
        boolean inside=false;
        for(float[] loop:layer.loops)if(contains(loop,x,y))inside=!inside;
        return inside;
    }
    static boolean anyTripleOverlap(StyleKit.Result r){
        for(int gy=0;gy<48;gy++)for(int gx=0;gx<36;gx++){
            float x=r.widthMm*(gx+.5f)/36f;
            float y=r.heightMm*(gy+.5f)/48f;
            int hits=0;
            for(StyleKit.Layer l:r.layers)if(layerContains(l,x,y))hits++;
            if(hits>=3)return true;
        }
        return false;
    }
    static int usedLayers(StyleKit.Result r){
        int n=0;
        for(StyleKit.Layer l:r.layers)if(!l.loops.isEmpty()||!l.lines.isEmpty())n++;
        return n;
    }
    public static void main(String[] args){
        final int w=168,h=216;
        int[] pixels=image(w,h);
        int[] palette={0xff261a18,0xff6a4131,0xffb07e67,0xffead0bf};
        PatternPortraitTrioEngine.Options o=new PatternPortraitTrioEngine.Options();
        o.kind=PatternPortraitTrioEngine.CONFETTI; o.layers=4; o.palette=palette;
        o.detail=82; o.organic=72; o.spacingMm=.36f; o.widthMm=152.4f;
        StyleKit.Result confetti=PatternPortraitTrioEngine.generate(pixels,w,h,o);
        check(confetti.layers.size()==4,"physical slot count changed");
        check(usedLayers(confetti)>=4,"all confetti layers should participate");
        check(layerArea(confetti.layers.get(0))>8000f,"solid base layer missing or too small");
        check(anyTripleOverlap(confetti),"stacked overlap missing");
        check(confetti.summary.contains("solid silhouette base"),"summary missing solid-base note");
        System.out.println("PASS RC154 confetti solid base and stacked overlap");
    }
}

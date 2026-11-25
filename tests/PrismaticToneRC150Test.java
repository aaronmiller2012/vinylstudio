package com.halftonestudio;

import java.util.Arrays;
/** RC150: tone-safe ink coverage and stable physical palette regression. */
public final class PrismaticToneRC150Test {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static int[] portrait(int w,int h){
        int[] out=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float nx=(x-w*.5f)/(w*.34f),ny=(y-h*.5f)/(h*.44f);
            if(nx*nx+ny*ny>=1){out[y*w+x]=0;continue;}
            boolean brow=(Math.abs(ny+.32f)<.08f&&Math.abs(nx)<.72f);
            boolean mouth=Math.abs(ny-.56f)<.065f&&Math.abs(nx)<.39f;
            int tone=Math.round(130f+30f*nx-28f*ny+15f*(float)Math.cos(2*nx));
            if(brow||mouth)tone=47;
            if(Math.abs(nx)<.22f&&ny<.26f&&ny>-.27f)tone+=22;
            int red=Math.max(0,Math.min(255,tone+34));
            int green=Math.max(0,Math.min(255,tone+3));
            int blue=Math.max(0,Math.min(255,tone-10));
            out[y*w+x]=0xff000000|(red<<16)|(green<<8)|blue;
        }
        return out;
    }
    private static boolean inside(float[] p,float x,float y){
        boolean b=false;int n=p.length/2;
        for(int i=0,j=n-1;i<n;j=i++){
            float yi=p[2*i+1],yj=p[2*j+1];
            if((yi>y)!=(yj>y)&&x<(p[2*j]-p[2*i])*(y-yi)/(yj-yi)+p[2*i])b=!b;
        }
        return b;
    }
    private static float sampledLightness(StyleKit.Result r){
        double sum=0;int n=0;
        for(int y=1;y<20;y++)for(int x=1;x<14;x++){
            float px=r.widthMm*x/14f,py=r.heightMm*y/20f;
            int color=-1;
            for(StyleKit.Layer layer:r.layers){
                boolean fill=false;
                for(float[] loop:layer.loops)if(inside(loop,px,py))fill=!fill;
                if(fill)color=layer.color;
            }
            if(color!=-1){sum+=StyleKit.toLab(color)[0];n++;}
        }
        return n==0?0:(float)(sum/n);
    }
    public static void main(String[] args){
        float[] base={87,8,18}, mid={55,18,16}, dark={24,5,-3};
        float[][] labs={base,mid,dark};
        check(PrismaticDiagonalPortraitEngine.coverageForTone(80,87,24)<
              PrismaticDiagonalPortraitEngine.coverageForTone(30,87,24),
              "highlights must use less opaque dark material than shadows");
        check(PrismaticDiagonalPortraitEngine.coverageForTone(72,30,85)>
              PrismaticDiagonalPortraitEngine.coverageForTone(46,30,85),
              "reversed/custom dark base must be supported");
        check(PrismaticDiagonalPortraitEngine.toneAwareSlot(new float[]{60,15,13},labs,87)>0,
              "valid tone pigment slot");
        final int w=110,h=138;
        int[] pixels=portrait(w,h);
        PrismaticDiagonalPortraitEngine.Options o=new PrismaticDiagonalPortraitEngine.Options();
        o.widthMm=279.4f;o.layers=6;o.detail=65;
        o.palette=new int[]{0xffeee3d6,0xffd5a279,0xffb97565,0xff946481,0xff634568,0xff33293b};
        PrismaticDiagonalPortraitEngine.Options darkOpt=o.copy();darkOpt.toneBalance=20;
        PrismaticDiagonalPortraitEngine.Options lightOpt=o.copy();lightOpt.toneBalance=90;
        StyleKit.Result a=PrismaticDiagonalPortraitEngine.generate(pixels,w,h,darkOpt);
        StyleKit.Result b=PrismaticDiagonalPortraitEngine.generate(pixels,w,h,lightOpt);
        float darkL=sampledLightness(a),lightL=sampledLightness(b);
        System.out.println("RC150 measured composite mean Lab L*: tone20="+darkL+" tone90="+lightL);
        check(lightL>darkL+6f,"tonal slider must increase actual finished composite lightness");
        check(a.layers.get(0).color==o.palette[0]&&b.layers.get(0).color==o.palette[0],
              "manual base and physical layer order preserved");
        check(!StyleKit.toSvg(a,false).equals(StyleKit.toSvg(b,false)),"tone slider edits geometry");
        for(StyleKit.Layer layer:b.layers){
            check(layer.lines.isEmpty(),"no motif strokes or open paths");
            for(float[] loop:layer.loops){
                check(loop.length>=6&&StyleKit.selfIntersections(loop,1)==0,"valid closed cut contour");
            }
        }
        PrismaticDiagonalPortraitEngine.Options automatic=o.copy();automatic.palette=null;
        StyleKit.Result auto=PrismaticDiagonalPortraitEngine.generate(pixels,w,h,automatic);
        check(StyleKit.toLab(auto.layers.get(0).color)[0]>=79f,"auto palette uses visible light base");
        check(auto.layers.size()==o.layers,"physical layer slots do not shift when an accent is empty");
        System.out.println("PASS RC150 tonal balance, contour safety, manual colors, automatic base");
    }
}

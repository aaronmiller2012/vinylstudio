package com.halftonestudio;

import java.util.Arrays;
/** Android-free render integration and cut-safety tests for new 45 degree portrait. */
public final class PrismaticDiagonalRC149Test {
    private static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    private static int[] image(int w,int h,boolean[] mask){
        int[] image=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float xx=(x-w*.5f)/(w*.34f),yy=(y-h*.5f)/(h*.43f);
            boolean in=xx*xx+yy*yy<1f;
            if(mask!=null)mask[y*w+x]=in;
            int color;
            float shadow=.35f+.45f*x/w+(.18f*(float)Math.sin(y*.067f));
            int r=Math.max(0,Math.min(255,Math.round(90+shadow*160)));
            int g=Math.max(0,Math.min(255,Math.round(60+shadow*120)));
            int b=Math.max(0,Math.min(255,Math.round(70+shadow*105)));
            // High contrast eyes/eyebrow edge, nose and mouth: image-driven orientation.
            if((Math.abs(yy+.28f)<.07f&&Math.abs(xx)<.75f)||
               (Math.abs(yy-.52f)<.06f&&Math.abs(xx)<.42f)) {r=50;g=45;b=76;}
            else if(Math.abs(yy-.08f)<.12f&&Math.abs(xx-.10f)<.2f){r=245;g=197;b=140;}
            color=0xff000000|(r<<16)|(g<<8)|b;
            image[y*w+x]=mask==null||in?color:0;
        }
        return image;
    }
    private static int cuts(StyleKit.Result r){int s=0;for(StyleKit.Layer l:r.layers)s+=l.loops.size();return s;}
    private static String signature(StyleKit.Result r){return StyleKit.toSvg(r,false);}
    public static void main(String[] args){
        final int w=124,h=174;boolean[] mask=new boolean[w*h];int[] photo=image(w,h,mask);
        PrismaticDiagonalPortraitEngine.Options o=new PrismaticDiagonalPortraitEngine.Options();
        o.widthMm=279.4f;o.detail=66;o.layers=6;o.minCutMm=.254f;o.allowed=mask;
        o.palette=new int[]{0xffe9d2bd,0xffc88175,0xffaa7291,0xff69457d,0xff2c365a,0xffdaaa5a};
        StyleKit.Result a=PrismaticDiagonalPortraitEngine.generate(photo,w,h,o);
        System.out.println(a.summary);
        check(a.layers.size()>=2&&a.layers.size()<=6,"2..6 populated layers");
        check(a.widthMm==o.widthMm,"physical width preserved");
        check(a.heightMm>o.widthMm,"physical aspect preserved");
        check(cuts(a)>10&&cuts(a)<500,"continuous ribbons instead of hundreds of inlay islands");
        check(a.summary.contains("interwoven ribbons"),"ribbon summary present");
        check(Integer.parseInt(a.summary.split("interwoven ribbons, ")[1].split(" cross-color")[0])>100,
              "substantial overlap in generated masks");
        int spanning=0;
        for(int k=1;k<a.layers.size();k++)for(float[] loop:a.layers.get(k).loops){
            float x0=Float.MAX_VALUE,x1=-Float.MAX_VALUE,y0=Float.MAX_VALUE,y1=-Float.MAX_VALUE;
            for(int j=0;j<loop.length;j+=2){x0=Math.min(x0,loop[j]);x1=Math.max(x1,loop[j]);
                y0=Math.min(y0,loop[j+1]);y1=Math.max(y1,loop[j+1]);}
            if(Math.hypot(x1-x0,y1-y0)>a.widthMm*.13f)spanning++;
        }
        check(spanning>12,"many cut components span multiple diagonal cells");
        check(a.layers.get(0).name.equals("prismatic-diagonal-1"),"solid Snip L1 is always first");
        check(a.layers.get(0).loops.size()>=1,"full silhouette base");
        for(StyleKit.Layer layer:a.layers){
            check(layer.lines.isEmpty(),"no pen strokes / no open motifs");
            for(float[] loop:layer.loops){
                check(loop.length>=6,"closed polygon with >=3 points");
                check(StyleKit.selfIntersections(loop,1)==0,"simple cut polygon");
                for(int j=0;j<loop.length;j+=2){
                    check(Float.isFinite(loop[j])&&Float.isFinite(loop[j+1]),"finite points");
                    check(loop[j]>=-.5f&&loop[j]<=a.widthMm+.5f,"clipped horizontal coordinate");
                    check(loop[j+1]>=-.5f&&loop[j+1]<=a.heightMm+.5f,"clipped vertical coordinate");
                }
            }
        }
        check(signature(a).equals(signature(PrismaticDiagonalPortraitEngine.generate(photo,w,h,o))),
              "deterministic from seed/photo/settings");
        PrismaticDiagonalPortraitEngine.Options more=o.copy();more.irregularity=100;
        check(!signature(a).equals(signature(PrismaticDiagonalPortraitEngine.generate(photo,w,h,more))),
              "serpentine bend changes generated geometry");
        more=o.copy();more.detail=10;
        check(cuts(PrismaticDiagonalPortraitEngine.generate(photo,w,h,more))!=cuts(a),
              "density changes generated cell count");
        more=o.copy();more.gapMm=1.4f;
        check(!signature(a).equals(signature(PrismaticDiagonalPortraitEngine.generate(photo,w,h,more))),
              "spacing changes actual cut geometry");
        more=o.copy();more.roundness=0;
        check(!signature(a).equals(signature(PrismaticDiagonalPortraitEngine.generate(photo,w,h,more))),
              "roundness modifies organic cut contours");
        more=o.copy();more.featureEmphasis=15;
        check(!signature(a).equals(signature(PrismaticDiagonalPortraitEngine.generate(photo,w,h,more))),
              "facial detail emphasis modifies ribbon geometry");
        more=o.copy();more.layers=2;more.palette=Arrays.copyOf(o.palette,2);
        StyleKit.Result two=PrismaticDiagonalPortraitEngine.generate(photo,w,h,more);
        check(two.layers.size()<=2&&two.layers.size()>=1,"two physical plates supported");
        System.out.println("PASS RC149 prismatic diagonal geometry, cut paths, Snip and multi-color layers");
    }
}

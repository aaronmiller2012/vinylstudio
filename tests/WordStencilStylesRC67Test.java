package com.halftonestudio;

import java.util.Arrays;

/** End-to-end host geometry, Snip, layer and Cricut export tests for RC67. */
public final class WordStencilStylesRC67Test {
    static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    static final int[] C={0xff15283a,0xffdfbb9d,0xff9e615b,0xffe9cf76,
        0xff4a839a,0xff609269,0xffad729b,0xfff1e2bf};
    static int[] image(int w,int h){
        int[] image=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int k=x<w/3?1:x<2*w/3?2:3;
            if(y>h*.63)k=4+((x/27)%3);
            image[y*w+x]=C[k];
        }
        return image;
    }
    static boolean[] mask(int w,int h){
        boolean[] m=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float nx=(x-w*.5f)/(w*.46f),ny=(y-h*.5f)/(h*.47f);
            boolean hole=(x-w*.48f)*(x-w*.48f)+(y-h*.5f)*(y-h*.5f)<9*9;
            m[y*w+x]=nx*nx+ny*ny<1&&!hole;
        }
        return m;
    }
    static long contours(StyleKit.Result r){long n=0;for(StyleKit.Layer l:r.layers)n+=l.loops.size();return n;}
    static int darkSlot(StyleKit.Result r){return r.layers.size()>1?r.layers.get(1).color:0;}
    public static void main(String[] argv){
        check(WordVectorGlyphs.knockout('O')!=WordVectorGlyphs.find('O'),
            "counter-bearing letters must have a stencil-safe form");
        check(WordVectorGlyphs.knockout('A')!=WordVectorGlyphs.find('A'),
            "A needs an open counter for knockout plates");
        final int w=144,h=180;int[] px=image(w,h);boolean[] allowed=mask(w,h);
        for(int count:new int[]{2,4,8}){
            for(int variant:new int[]{1,2}){
                VinylStyleSuite.Config cfg=new VinylStyleSuite.Config();
                cfg.style=VinylStyleSuite.TEXT;cfg.variant=variant;
                cfg.colorCount=count;cfg.widthMm=144f;
                cfg.palette=Arrays.copyOf(C,count);cfg.minimumMm=.45f;
                cfg.detail=90;cfg.allowed=allowed;cfg.words="JANE SMITH VINYL FAMILY";
                StyleKit.Result r=VinylStyleSuite.generate(px,w,h,cfg);
                check(r.layers.size()>=1&&r.layers.size()<=count,"over eight physical layers");
                check(r.layers.get(0).name.equals("snipped-background-1"),"L1 Snip base missing");
                check(r.layers.get(0).loops.size()>=2,"Snip hole not present on L1");
                check(r.layers.get(0).color==C[0],"base color changed");
                check(r.summary.contains(variant==1?"whole words":"whole-word cutouts"),
                    "incorrect Word style selected: "+r.summary);
                if(variant==2)check(r.summary.contains("color plates"),"Knockout not plate-based");
                int detail=0;String all="";
                for(int i=1;i<r.layers.size();i++){
                    StyleKit.Layer l=r.layers.get(i);
                    int slot=NewStyleColorSlots.slot(VinylStyleSuite.TEXT,l.name,i);
                    check(slot>0&&slot<count,"invalid detail layer name / slot");
                    check(l.color==C[slot],"swatch mismatch");
                    check(l.loops.size()<5000,"Cricut SVG path limit exceeded");
                    for(float[] poly:l.loops){
                        detail++;
                        check(poly.length>=6&&poly.length%2==0,"bad closed contour");
                        for(int a=0;a<poly.length;a+=2){
                            check(Float.isFinite(poly[a])&&Float.isFinite(poly[a+1]),"NaN contour");
                            int xx=Math.max(0,Math.min(w-1,(int)poly[a]));
                            int yy=Math.max(0,Math.min(h-1,(int)poly[a+1]));
                            check(allowed[yy*w+xx],"cut outside Snip or into Snip hole at "+xx+","+yy);
                        }
                    }
                    String svg=NewStyleExport.layerSvg(r,i,false,0,2f,2f);
                    check(svg.contains("<path")&&svg.contains("fill-rule=\"evenodd\""),
                        "must export actual even-odd closed regions");
                    check(!svg.contains("<text")&&!svg.contains("<clipPath")&&!svg.contains("<image"),
                        "unsupported live text/clipping/image in SVG");
                    all+=svg.length();
                }
                check(detail>0,"no visible word-derived artwork");
                System.out.println("RC67 variant "+variant+", "+count+" layers: "+r.summary+
                    ", detail contours "+detail+", SVG chars "+all.length());
            }
        }
        // Ensure new style does not mutate the ASCII Shapes ID or legacy Word Shapes ID.
        check(VinylStyleSuite.TEXT==5,"stable saved TEXT style id changed");
        System.out.println("PASS RC67 Word Mosaic and Word Knockout export/geometry/compatibility");
    }
}

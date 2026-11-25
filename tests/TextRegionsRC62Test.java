package com.halftonestudio;

import java.util.Arrays;

/** RC62 release contract: spatial colors, distinct word/ASCII engines, cuttable layers. */
public final class TextRegionsRC62Test {
    static void ok(boolean b,String why){if(!b)throw new AssertionError(why);}
    static int[] image(int w,int h){
        int[] p=new int[w*h];
        int[] c={0xffeecfaf,0xff865447,0xff294c70,0xff2a704e,0xffa85031,0xff372c49,0xffa89031,0xff1f889a};
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int dx=x-w/2,dy=y-h/2;
            int band=(x*4/w)+(y*2/h)*4;
            int color=c[Math.min(7,Math.max(0,band))];
            if(dx*dx+dy*dy<500)color=0xff27232a;
            p[y*w+x]=color;
        }
        return p;
    }
    static TextHalftoneEngine.Options option(int n,TextHalftoneEngine.Mode mode){
        TextHalftoneEngine.Options o=new TextHalftoneEngine.Options();
        o.widthMm=170;o.cellMm=3.4f;o.minStrokeMm=.4f;o.autoLevels=false;
        o.silhouetteUnderlay=true;o.mode=mode;o.colorLayers=n;
        o.palette=Arrays.copyOf(new int[]{0xffeecfaf,0xff865447,0xff294c70,0xff2a704e,0xffa85031,0xff372c49,0xffa89031,0xff1f889a},n);
        o.text="JANE SMITH VINYL STUDIO";
        return o;
    }
    public static void main(String[] args){
        int w=200,h=220;int[] data=image(w,h);
        StyleKit.Source source=new StyleKit.Source(data,w,h,null,170);
        TextPortraitRegions.Map segmentation=TextPortraitRegions.analyze(source,3.4f);
        ok(segmentation.width()<=160&&segmentation.height()<=160,"excessive segmentation memory");
        ok(segmentation.count()>10&&segmentation.count()<=600,"segmentation bounds");
        TextPortraitRegions.Region upper=segmentation.at(12,12),lower=segmentation.at(160,179);
        ok(upper!=null&&lower!=null&&upper.id!=lower.id,"regions lose spatial separation");
        ok(WordPortraitEngine.tokenize("Jane Smith's studio").length==4,"word tokens not whole");
        ok(WordPortraitEngine.tokenize("a-b")[0].equals("A"),"letter punctuation boundary");
        for(int n:new int[]{2,4,6,8}){
            for(TextHalftoneEngine.Mode mode:TextHalftoneEngine.Mode.values()){
                TextHalftoneEngine.Options o=option(n,mode);
                StyleKit.Result out=TextHalftoneEngine.generate(data,w,h,o);
                ok(out.layers.size()>=2&&out.layers.size()<=n,"physical layer count");
                ok(out.layers.get(0).name.equals("snipped-background-1"),"base swatch identity");
                ok(out.layers.get(0).loops.size()==1,"rectangular complete base");
                ok(out.summary.contains("region-aware"),"old pixel-only rendering path");
                if(mode==TextHalftoneEngine.Mode.WORDS){
                    ok(out.summary.contains("whole words"),"failed whole-word packing");
                    ok(!out.summary.contains("no full words fit"),"whole words are absent");
                }
                int loops=0;for(int i=1;i<out.layers.size();i++)loops+=out.layers.get(i).loops.size();
                ok(loops>25,"insufficient detail contours");
            }
        }
        boolean[] mask=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float dx=(x-w*.5f)/(w*.45f),dy=(y-h*.5f)/(h*.45f);
            boolean hole=(x-w/2)*(x-w/2)+(y-h/2)*(y-h/2)<13*13;
            mask[y*w+x]=dx*dx+dy*dy<1&&!hole;
        }
        for(TextHalftoneEngine.Mode mode:TextHalftoneEngine.Mode.values()){
            TextHalftoneEngine.Options o=option(7,mode);o.allowed=mask;
            StyleKit.Result cut=TextHalftoneEngine.generate(data,w,h,o);
            ok(cut.layers.get(0).loops.size()>=2,"Snip hole disappeared");
            for(int k=1;k<cut.layers.size();k++)for(float[] path:cut.layers.get(k).loops)
                for(int v=0;v<path.length;v+=2){
                    int x=Math.min(w-1,Math.max(0,(int)(path[v]/(o.widthMm/w))));
                    int y=Math.min(h-1,Math.max(0,(int)(path[v+1]/(o.widthMm/w))));
                    ok(mask[y*w+x],"text glyph entered removed Snip pixel");
                }
        }
        System.out.println("PASS RC62 region-first 2–8 sheets, whole words, ASCII, filled Snip and hole-safe closed contours");
    }
}

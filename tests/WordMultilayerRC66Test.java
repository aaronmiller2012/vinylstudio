package com.halftonestudio;

import java.util.Arrays;

/** RC66 whole-word multi-layer photograph and physical cut geometry contract. */
public final class WordMultilayerRC66Test {
    static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    static final int[] PALETTE={0xff293444,0xfff0d8b9,0xffce9c88,0xff8eaab0,
        0xffad705c,0xff557c75,0xff774a71,0xffdac264};
    static int[] photo(int w,int h){
        int[] a=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int ix=(x*4/w+y*2/h)%7;
            int c=PALETTE[1+ix];
            // Facial features concentrate around face center.
            float nx=(x-w*.5f)/(w*.36f),ny=(y-h*.38f)/(h*.33f);
            if(nx*nx+ny*ny<1f){
                c=PALETTE[1+Math.min(6,(x*3/w+y*3/h))];
                if(Math.abs(ny+.2f)<.06f&&Math.abs(Math.abs(nx)-.35f)<.2f)c=PALETTE[0];
            }
            a[y*w+x]=c;
        }
        return a;
    }
    static boolean[] mask(int w,int h){
        boolean[] a=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float nx=(x-w*.5f)/(w*.43f),ny=(y-h*.5f)/(h*.45f);
            boolean hole=(x-w*.48f)*(x-w*.48f)+(y-h*.55f)*(y-h*.55f)<10*10;
            a[y*w+x]=nx*nx+ny*ny<1&&!hole;
        }
        return a;
    }
    public static void main(String[] args){
        require(WordPortraitEngine.tokenize("Jane, Smith's STUDIO").length==4,
            "whole token parser must retain word boundaries");
        for(char cp='A';cp<='Z';cp++)require(WordVectorGlyphs.find(cp)!=null,"missing letter: "+cp);
        for(char cp='0';cp<='9';cp++)require(WordVectorGlyphs.find(cp)!=null,"missing digit: "+cp);
        require(!WordVectorGlyphs.find('B').fits(2f,.8f),
            "complex glyph must refuse impossible minimum cut sizes");
        int w=160,h=200;int[] p=photo(w,h);boolean[] allowed=mask(w,h);
        for(int layers:new int[]{2,4,8}){
            VinylStyleSuite.Config c=new VinylStyleSuite.Config();
            c.style=VinylStyleSuite.TEXT;c.variant=1;c.detail=95;
            c.widthMm=140;c.minimumMm=.4f;c.colorCount=layers;
            c.palette=Arrays.copyOf(PALETTE,layers);c.allowed=allowed;
            c.words="JANE SMITH FAMILY FOREVER";
            StyleKit.Result output=VinylStyleSuite.generate(p,w,h,c);
            require(output.layers.size()>=2&&output.layers.size()<=layers,"2–8 total physical sheets including base");
            require(output.layers.get(0).name.equals("snipped-background-1"),"L1 must be the actual filled Snip base");
            require(output.layers.get(0).loops.size()>=2,"Snip interior cut-out not preserved");
            require(output.summary.contains("whole words")&&output.summary.contains("region-aware"),
                "whole-word adaptive layout not selected");
            int shapes=0;float minY=Float.MAX_VALUE,maxY=Float.NEGATIVE_INFINITY;
            for(int l=1;l<output.layers.size();l++){
                StyleKit.Layer layer=output.layers.get(l);
                int slot=NewStyleColorSlots.slot(VinylStyleSuite.TEXT,layer.name,l);
                require(slot>=1&&slot<layers,"detail assigned to solid base or invalid slot");
                require(layer.color==PALETTE[slot],"user color swatch changed");
                require(layer.loops.size()<=4600,"too many contours in a physical layer");
                for(float[] loop:layer.loops){
                    shapes++;require(loop.length>=6&&loop.length%2==0,"open or broken polygon");
                    for(int i=0;i<loop.length;i+=2){
                        float x=loop[i]/(c.widthMm/w),y=loop[i+1]/(c.widthMm/w);
                        int px=Math.max(0,Math.min(w-1,(int)x)),py=Math.max(0,Math.min(h-1,(int)y));
                        require(allowed[py*w+px],"word crosses Snip boundary / internal hole");
                        minY=Math.min(minY,loop[i+1]);maxY=Math.max(maxY,loop[i+1]);
                    }
                }
            }
            require(shapes>20,"word layout lacks usable detail");
            require(maxY-minY>c.widthMm*.60f,"words are stuck in one part of the photograph");
            String svg=NewStyleExport.layerSvg(output,1,false,0,2f,1.5f);
            require(svg.contains("<path")&&!svg.contains("<text")&&!svg.contains("<clipPath"),
                "export must contain closed SVG cuts, never SVG font paths or clips");
            System.out.println("RC66 "+layers+" layers: "+output.summary);
        }
        System.out.println("PASS RC66 Multilayer Word routing, variable-size vector words, mask holes, layer colors and SVG");
    }
}

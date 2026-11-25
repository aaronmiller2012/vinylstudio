package com.halftonestudio;
public final class EightStyleIntegrationTest {
    static void check(boolean x,String msg){if(!x)throw new AssertionError(msg);}
    public static void main(String[] ignored){
        int w=120,h=140;int[] img=new int[w*h];boolean[] allowed=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            boolean in=((x-60)*(x-60)/1800.0+(y-67)*(y-67)/2900.0<=1) && !(x>48&&x<54&&y>48&&y<64);
            allowed[y*w+x]=in;int r=x<60?200:130,g=y<70?150:100,b=90;
            img[y*w+x]=(in?0xff000000:0)|r<<16|g<<8|b;
        }
        for(int style=0;style<8;style++){
            VinylStyleSuite.Config c=new VinylStyleSuite.Config();c.style=style;c.widthMm=152.4f;
            c.colorCount=style==VinylStyleSuite.GLASS?6:6;c.detail=42;c.allowed=allowed;c.variant=0;
            long before=System.currentTimeMillis();
            StyleKit.Result r=VinylStyleSuite.generate(img,w,h,c);
            check(r.layers.size()<=8,"too many physical layers for "+style);
            check(!r.layers.isEmpty(),"no output for style "+style);
            String svg=StyleKit.toSvg(r,false);check(svg.startsWith("<svg")&&svg.endsWith("</svg>\n"),"svg "+style);
            System.out.printf("%s: %d layers %d points %d ms%n",VinylStyleSuite.NAMES[style],r.layers.size(),r.totalPoints(),System.currentTimeMillis()-before);
            if(style==0||style==1){
                StyleKit.Source src=new StyleKit.Source(img,w,h,allowed,c.widthMm);
                for(int li=0;li<r.layers.size();li++){
                    if(style==1&&li==0)continue;
                    // Subpixel boundaries may lie ON an excluded cell border.
                    // Assert no excluded pixel interior is filled; do not require
                    // boundary vertices to map to an allowed pixel by floor().
                    for(float[] loop:r.layers.get(li).loops){
                        for(int y=0;y<src.h;y++)for(int x=0;x<src.w;x++)if(!src.valid[y*src.w+x]){
                            check(!MaskedFacetClipper.inside(loop,(x+.5f)*src.mmPerPx,
                                (y+.5f)*src.mmPerPx),"masked interior covered: "+style+" / "+li);
                        }
                    }
                }
            }
        }
        System.out.println("Eight styles integration PASS");
    }
}

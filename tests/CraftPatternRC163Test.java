package com.halftonestudio;

import java.util.Arrays;

public final class CraftPatternRC163Test {
    private static void require(boolean test,String message){if(!test)throw new AssertionError(message);}
    private static final int W=96,H=116;
    private static int[] image(boolean flat) {
        int[] px=new int[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            int t=flat?128:(int)(30+190*(.46*x/(W-1.)+.54*y/(H-1.)));
            px[y*W+x]=0xff000000|t<<16|Math.max(0,t-17)<<8|Math.max(0,t-35);
        }
        return px;
    }
    private static boolean[] mask(){
        boolean[] m=new boolean[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            double xx=(x-W/2.)/(W*.41),yy=(y-H/2.)/(H*.44);
            m[y*W+x]=xx*xx+yy*yy<=1;
        }
        return m;
    }
    public static void main(String[] args) {
        int[] pixels=image(false);boolean[] allowed=mask();
        long[] signatures=new long[5];
        for(int type=0;type<5;type++){
            for(int layers:new int[]{2,4,9}){
                NineLayerCraftPatternEngine.Options o=new NineLayerCraftPatternEngine.Options();
                o.pattern=type;o.layers=layers;o.detail=55;o.widthMm=279.4f;
                o.minCutMm=.254f;o.allowed=allowed;o.draft=true;
                StyleKit.Result result=NineLayerCraftPatternEngine.generate(pixels,W,H,o);
                require(result.layers.size()==layers,"physical sheet count "+type+"/"+layers);
                int cuts=0;long hash=0xCBF29CE484222325L;
                for(int i=0;i<layers;i++){
                    StyleKit.Layer layer=result.layers.get(i);
                    require(!layer.loops.isEmpty(),"empty vinyl layer "+type+" L"+(i+1));
                    require(layer.lines.isEmpty(),"cut style contains pen paths");
                    for(float[] loop:layer.loops){
                        require(loop.length>=6,"uncuttable loop");
                        for(int j=0;j<loop.length;j+=2){
                            require(Float.isFinite(loop[j])&&Float.isFinite(loop[j+1]),"invalid coordinate");
                            require(loop[j]>=-.75&&loop[j]<=result.widthMm+.75,"x out of art bounds");
                            require(loop[j+1]>=-.75&&loop[j+1]<=result.heightMm+.75,"y out of art bounds");
                        }
                        hash=(hash^loop.length)*0x100000001b3L;
                        hash=(hash^Float.floatToIntBits(loop[0]))*0x100000001b3L;
                    }
                    cuts+=layer.loops.size();
                }
                if(layers==9)signatures[type]=hash;
                System.out.println("PASS "+NineLayerCraftPatternEngine.NAMES[type]+" "+layers+
                    " layers, "+cuts+" blade contours");
            }
        }
        for(int i=0;i<5;i++)for(int j=i+1;j<5;j++)
            require(signatures[i]!=signatures[j],"Duplicate style geometry "+i+" and "+j);
        NineLayerCraftPatternEngine.Options flat=new NineLayerCraftPatternEngine.Options();
        flat.pattern=NineLayerCraftPatternEngine.CAMOUFLAGE;flat.layers=9;flat.detail=55;
        flat.widthMm=279.4f;flat.allowed=allowed;flat.draft=true;
        StyleKit.Result zero=NineLayerCraftPatternEngine.generate(image(true),W,H,flat);
        for(StyleKit.Layer l:zero.layers)require(!l.loops.isEmpty(),"flat tone emptied a layer");
        int[] nine=Arrays.copyOf(new int[]{0xff123456,0xff234567,0xff345678,0xff456789,
            0xff56789a,0xff6789ab,0xff789abc,0xff89abcd,0xff9abcde},9);
        int[] shifted=PaletteHueShift.rotate(nine,45);
        require(shifted.length==9&&nine[8]!=shifted[8],"9th hue swatch not rotated");
        require(NewStyleColorSlots.maximumSelectable(VinylStyleSuite.CROCHET)==9,"count max");
        require(NewStyleLayerPolicy.isStructuralLayer(VinylStyleSuite.FISHNET,0),"L1 protected");
        require(!NewStyleLayerPolicy.canDelete(VinylStyleSuite.LACE,0,9),"base deletion exposed");
        VinylStyleSuite.Config c=new VinylStyleSuite.Config();c.style=VinylStyleSuite.CROCHET;
        c.colorCount=9;c.widthMm=279.4f;c.detail=60;c.draft=true;c.allowed=allowed;
        c.palette=nine;
        require(VinylStyleSuite.generate(pixels,W,H,c).layers.size()==9,"9-layer suite dispatch");
        System.out.println("PASS RC163 all five distinct cuttable styles, 9-layer route, empty-layer prevention, flat tone and hue");
    }
}

package com.halftonestudio;

import java.util.Arrays;

/** No Android dependency: deterministic physical cap geometry and legacy isolation. */
public final class BottleCapRC118Test {
    private static void ok(boolean b,String message){if(!b)throw new AssertionError(message);}
    private static final int W=120,H=130;
    private static int[] portrait(){
        int[] input=new int[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            int value=(x*2+y*3)%256;
            if(x>32&&x<49&&y>37&&y<52)value=15;
            if(x>70&&x<88&&y>37&&y<52)value=15;
            input[y*W+x]=0xff000000|(value<<16)|(value<<8)|value;
        }
        return input;
    }
    private static boolean[] mask(){
        boolean[] allow=new boolean[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            int dx=x-60,dy=y-66;
            allow[y*W+x]=dx*dx*1.35f+dy*dy<56*56;
        }
        return allow;
    }
    private static void equal(String left,String right,String name){ok(left.equals(right),name);}
    public static void main(String[] args){
        int[] photo=portrait();
        LayeredCellPortraitEngine.Options in=new LayeredCellPortraitEngine.Options();
        in.widthMm=152.4f;in.columns=28;in.gapMm=.28f;in.minFeatureMm=.30f;
        in.layerCount=8;in.photoDetail=1f;in.solidBase=false;in.detailMotifOverlay=true;
        in.registration=false;in.allowedPixels=mask();
        LayeredCellPortraitEngine.Result caps=BottleCapMosaicEngine.generate(photo,W,H,in);
        ok(caps.pieces.size()>100,"Too few photo caps");
        ok(caps.options.bottleCaps,"Bottle Cap mode was not enabled");
        ok(!in.bottleCaps,"Caller options were modified");
        ok(caps.contourCounts[7]>10,"Rim highlights missing");
        boolean layerUsed=false;
        for(int layer=0;layer<caps.options.layerCount;layer++){
            ok(caps.contourCounts[layer]<4800,"Cricut contour ceiling");
            String svg=caps.svg(layer);
            ok(svg.startsWith("<svg") && svg.contains("fill-rule=\"evenodd\""),"Invalid SVG");
            if(caps.pieceCounts[layer]>0)layerUsed=true;
            for(LayeredCellPortraitEngine.Piece piece:caps.pieces){
                if(piece.layer!=layer)continue;
                for(int k=0;k<piece.outer.length;k+=2){
                    float x=piece.outer[k],y=piece.outer[k+1];
                    ok(Float.isFinite(x)&&Float.isFinite(y),"Nonfinite geometry");
                    ok(x>=0f&&x<=W&&y>=0f&&y<=H,"Cap outside page");
                }
            }
        }
        ok(layerUsed,"No colored caps");
        String first=caps.svg(1);
        equal(first,BottleCapMosaicEngine.generate(photo,W,H,in).svg(1),"Not deterministic");
        LayeredCellPortraitEngine.Options solid=in.copy();solid.solidBase=true;
        LayeredCellPortraitEngine.Result base=BottleCapMosaicEngine.generate(photo,W,H,solid);
        ok(base.pieceCounts[0]>0,"Optional solid base missing");
        LayeredCellPortraitEngine.Options plain=in.copy();plain.bottleCaps=false;
        LayeredCellPortraitEngine.Result legacy=LayeredCellPortraitEngine.generate(photo,W,H,plain);
        ok(!legacy.options.bottleCaps,"Legacy geometry mode changed");
        LayeredCellPortraitEngine.Options shift=in.copy();shift.offsetRows=true;
        LayeredCellPortraitEngine.Result stagger=OffsetCellPortraitEngine.generate(photo,W,H,shift);
        ok(stagger.options.offsetRows&&!stagger.options.bottleCaps,"Offset Cell changed");
        System.out.println("PASS RC118 Bottle Cap: "+caps.pieces.size()+" closed contours, 8 sheets, mask, deterministic SVG, legacy isolation");
    }
}

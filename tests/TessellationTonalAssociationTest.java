package com.halftonestudio;

import java.util.Arrays;
import java.util.HashSet;

/** Beta 9: photograph-to-physical-swatch ownership and tonal contrast. */
public final class TessellationTonalAssociationTest {
    private static void require(boolean b,String s){if(!b)throw new AssertionError(s);}
    private static int gray(int x){return 0xff000000|(x<<16)|(x<<8)|x;}
    private static int[] ramp(int w,int h){
        int[] a=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)
            a[y*w+x]=gray(16+(int)(224.0*x/(w-1)));return a;
    }
    private static TessellationMotifEngine.Options opts(int style){
        TessellationMotifEngine.Options o=new TessellationMotifEngine.Options();
        o.pattern=style;o.widthMm=152.4f;o.detail=80;
        o.palette=new int[]{gray(14),gray(44),gray(78),gray(109),gray(145),gray(177),gray(209),gray(240)};
        return o;
    }
    private static int overlayCount(StyleKit.Result r){int total=0;for(int i=1;i<r.layers.size();i++)total+=r.layers.get(i).loops.size();return total;}
    public static void main(String[] args){
        int w=176,h=128;
        int[] source=ramp(w,h);
        StyleKit.Source input=new StyleKit.Source(source,w,h,null,152.4f);
        int ran=0;
        for(int style=0;style<TessellationMotifEngine.PATTERN_SLOT_COUNT;style++){
            if(!TessellationMotifEngine.selectablePattern(style))continue;
            TessellationMotifEngine.Options o=opts(style);
            TessellationToneMapper tonal=new TessellationToneMapper(input,o,true);
            require(tonal.select(o.palette[0])==TessellationToneMapper.BASE,"Base exact swatch ignored: "+style);
            for(int i=1;i<o.layerCount;i++)
                require(tonal.select(o.palette[i])==i,"Overlay exact swatch ignored: style "+style+" slot "+i);
            int prev=-1;HashSet<Integer> used=new HashSet<>();
            for(int x=14;x<=240;x+=3){
                int slot=tonal.select(gray(x));
                require(slot>=prev,"Grayscale palette ownership reversed at "+style+" x="+x+" slot="+slot+" prev="+prev);
                used.add(slot);prev=slot;
            }
            require(used.size()>=6,"Collapsed light/dark association: "+style+" count="+used.size());
            StyleKit.Result result=TessellationMotifEngine.generate(source,w,h,o);
            require(result.layers.size()==8,"Changed physical layer count: "+style);
            require(overlayCount(result)>0,"Photographic colored overlay disappeared: "+style);
            for(int i=0;i<8;i++)require(result.layers.get(i).color==o.palette[i],"Swatch changed: "+style+" / "+i);
            String svg=StyleKit.toSvg(result,false);
            require(svg.equals(StyleKit.toSvg(TessellationMotifEngine.generate(source,w,h,o),false)),
                "Non-deterministic tessellation output: "+style);
            ran++;
        }
        require(ran==28,"Expected exactly 28 active tessellation renderers");
        TessellationMotifEngine.Options o=opts(TessellationMotifEngine.SQUARE);
        TessellationToneMapper tonal=new TessellationToneMapper(input,o,true);
        // Exact skin-tone and blue/green color matches must remain tied to
        // actual swatch indices, not be resorted by hue or by physical sheet.
        o.palette=new int[]{0xff1a1317,0xff2264d9,0xffba8060,0xff49a45c,
            0xffcbaca0,0xffd6a21b,0xff745b94,0xffebd9d1};
        tonal=new TessellationToneMapper(input,o,true);
        for(int i=1;i<8;i++)require(tonal.select(o.palette[i])==i,"Exact colored swatch reassigned: "+i);
        o.solidBase=false;
        tonal=new TessellationToneMapper(input,o,false);
        require(tonal.select(o.palette[0])==0,"Base-disabled zero slot must be available");
        TessellationToneMapper samePaper=new TessellationToneMapper(input,o,false);
        o.paperArgb=0xff060606;
        TessellationToneMapper differentPaper=new TessellationToneMapper(input,o,false);
        for(int x=0;x<255;x+=11){int c=gray(x);
            require(samePaper.select(c)==differentPaper.select(c),"Mounting color altered swatch association");}
        // Constant subjects cannot be spuriously normalized into full contrast.
        int[] flat=new int[w*h];Arrays.fill(flat,0xffb08064);
        TessellationToneMapper flatMapper=new TessellationToneMapper(new StyleKit.Source(flat,w,h,null,152.4f),o,false);
        require(flatMapper.select(0xffb08064)==flatMapper.select(0xffb08064),"Unstable flat photo");
        // A 2-layer base+one-color setup must never index out of bounds.
        o.layerCount=2;o.solidBase=true;
        tonal=new TessellationToneMapper(input,o,true);
        for(int x=0;x<=255;x+=3){int slot=tonal.select(gray(x));require(slot==-1||slot==1,"Invalid two-sheet owner");}
        System.out.println("PASS Beta9: 28 style profiles, lightness ordering, true swatch ID, base exposure, paper independence and deterministic output");
    }
}

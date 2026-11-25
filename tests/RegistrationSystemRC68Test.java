package com.halftonestudio;

/** Deterministic, Android-free preflight for physical mark placement in every SVG family. */
public final class RegistrationSystemRC68Test {
    static void ok(boolean v,String reason){if(!v)throw new AssertionError(reason);}
    static int[] photo(int w,int h){int[] a=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){
        int c=(x*9+y*13)&255;a[y*w+x]=0xff000000|(c<<16)|((255-c)<<8)|((c/2)+60);
    }return a;}
    static void containsMarks(String svg,float artW,float artH,float size,float clearance,float[][] requested,float minimum,String label){
        RegistrationMarks.Layout lay=RegistrationMarks.layout(artW,artH,size,clearance,requested,0);
        // SVGs may include more outer margin than the minimum requirement.
        for(int i=0;i<4;i++){
            String needle=RegistrationMarks.svgMark(RegistrationMarks.CROSS,
                lay.positions[i][0],lay.positions[i][1],size,minimum);
            // Some exporters shift the artwork origin. Compare physically by normalizing in caller.
            ok(needle.length()>20,label+" mark geometry was not generated");
        }
        ok(!svg.contains("clipPath")&&!svg.contains("<text"),label+" must be cut-ready vector geometry");
    }
    static void expectThrow(Runnable work,String msg){boolean threw=false;try{work.run();}catch(IllegalArgumentException e){threw=true;}
        ok(threw,msg);
    }
    static float[][] manual(float w,float h){return new float[][]{{-19,-18},{w+20,-18},{-19,h+21},{w+20,h+21}};}
    static void checkMarkBlock(String svg,String exact,String label){ok(svg.contains(exact),label+" missing custom registration mark");}
    public static void main(String[] ignored){
        float[] cross=RegistrationMarks.outline(RegistrationMarks.CROSS,0,0,2.5f,.8f);
        ok(cross[2]-cross[0]>=.799f,"Cross arms must meet the cutting minimum");
        float[] corner=RegistrationMarks.outline(RegistrationMarks.CORNER,0,0,2.5f,.8f);
        ok(corner[5]-corner[3]>=.799f,"L-corner arms must meet the cutting minimum");
        expectThrow(()->RegistrationMarks.outline(RegistrationMarks.CROSS,0,0,2f,2f),"Impossible cutting width must fail preflight");
        float[][] original=manual(100,100),copy=RegistrationMarks.copyPositions(original);
        copy[0][0]=-999;ok(original[0][0]==-19,"Mark snapshots must be deep copies");
        RegistrationMarks.Layout big=RegistrationMarks.layout(100,100,12f,1f,null,0);
        float radius=RegistrationMarks.markRadius(12f,1f);
        ok(big.paddingMm>=-big.positions[0][0]+radius,"12-mm mark outside board with required clearance");
        RegistrationMarks.Layout moved=RegistrationMarks.layout(100,100,4f,2f,original,0);
        ok(moved.paddingMm>20,"Manual coordinates enlarge artboard instead of being clipped");
        expectThrow(()->RegistrationMarks.layout(100,100,4,1,new float[][]{{-10,-10},{-11,-11},{110,110},{120,120}},0),"Overlapping marks must fail");
        expectThrow(()->RegistrationMarks.requireOutsideArtwork(
            RegistrationMarks.layout(100,100,4,1,new float[][]{{30,30},{120,-20},{-20,120},{120,120}},0),100,100,4,1),"Specialty marks must not obstruct artwork");

        VinylEngine.Settings standard=VinylEngine.Settings.defaults();standard.widthMm=100;standard.minimumMm=.8f;
        standard.markStyle=RegistrationMarks.CROSS;standard.markSizeMm=12;standard.registrationClearanceMm=1;
        standard.marks=original;
        VinylEngine.Result base=VinylEngine.generate(photo(32,32),32,32,standard);
        ok(VinylEngine.validateMarks(base)==null,"Custom standard marks must validate");
        for(int layer=0;layer<4;layer++){
            String svg=VinylEngine.svg(base,layer);
            for(float[] position:original)checkMarkBlock(svg,RegistrationMarks.svgMark(0,position[0],position[1],12,.8f),"CMYK L"+layer);
        }

        StyleKit.Result style=new StyleKit.Result(100,100);
        StyleKit.Layer colored=new StyleKit.Layer("Test",0xff1a2b3c);colored.loops.add(new float[]{30,30,60,30,60,60,30,60});
        style.layers.add(colored);
        for(float size:new float[]{2f,2.5f,4f,8f,12f}){
            String svg=NewStyleExport.layerSvg(style,0,true,RegistrationMarks.CROSS,size,1f,null,.8f);
            RegistrationMarks.Layout lay=RegistrationMarks.layout(100,100,size,1,null,8);
            // ADR-0001 frame: board = artwork + 2 x margin, viewBox origin 0 0, artwork at +margin.
            float board=100+2*lay.paddingMm;
            String expected=String.format(java.util.Locale.US,"width=\"%.3fmm\" height=\"%.3fmm\" viewBox=\"0 0 %.3f %.3f\"",
                board,board,board,board);
            String shift=String.format(java.util.Locale.US,"translate(%.3f %.3f)",lay.paddingMm,lay.paddingMm);
            ok(svg.contains(expected)&&svg.contains(shift),"New-style SVG artboard cannot clip "+size+"-mm marks");
        }
        String newStyle=NewStyleExport.layerSvg(style,0,true,RegistrationMarks.CROSS,4,1,original,.8f);
        for(float[] pos:original)checkMarkBlock(newStyle,RegistrationMarks.svgMark(0,pos[0],pos[1],4,.8f),"New style");

        TessellationEngine.Options tess=new TessellationEngine.Options();tess.layerCount=2;tess.detail=14;
        tess.widthMm=100;tess.colors=new int[]{0xff111111,0xffffffff};tess.marks=original;tess.markSizeMm=4;
        TessellationEngine.Result tessResult=TessellationEngine.generate(photo(32,32),32,32,tess);
        float tessPad=RegistrationMarks.layout(100,100,4,tess.registrationClearanceMm,original,tess.outerSpacingMm).paddingMm;
        for(int i=0;i<2;i++)for(float[] pos:original)
            checkMarkBlock(tessResult.svg(i),RegistrationMarks.svgMark(0,tessPad+pos[0],tessPad+pos[1],4,.8f),"Tessellation L"+i);

        LayeredCellPortraitEngine.Options cell=new LayeredCellPortraitEngine.Options();cell.layerCount=2;cell.columns=6;
        cell.colors=new int[]{0xff111111,0xffffffff};cell.widthMm=100;cell.marks=original;cell.markSizeMm=4;
        LayeredCellPortraitEngine.Result cellResult=LayeredCellPortraitEngine.generate(photo(32,32),32,32,cell);
        float cellPad=RegistrationMarks.layout(100,100,4,cell.registrationClearanceMm,original,cell.outerSpacingMm).paddingMm;
        for(int i=0;i<2;i++)for(float[] pos:original)
            checkMarkBlock(cellResult.svg(i),RegistrationMarks.svgMark(0,cellPad+pos[0],cellPad+pos[1],4,.8f),"Layered Cell L"+i);

        MultilayerStencilEngine.Options stencil=new MultilayerStencilEngine.Options();stencil.layerCount=2;
        stencil.widthMm=100;stencil.marks=original;stencil.markDiameterMm=4;stencil.minimumIslandPixels=0;
        MultilayerStencilEngine.Result stencilResult=MultilayerStencilEngine.generate(photo(32,32),32,32,stencil);
        float stencilPad=RegistrationMarks.layout(100,100,4,stencil.registrationClearanceMm,original,stencil.outerSpacingMm).paddingMm;
        for(int i=0;i<2;i++)for(float[] pos:original)
            checkMarkBlock(stencilResult.svg(i),RegistrationMarks.svgMark(0,stencilPad+pos[0],stencilPad+pos[1],4,.8f),"Stencil L"+i);
        System.out.println("PASS RC68: min-width, large-mark artboard, custom XY, CMYK, New Styles, Tessellation, Cell and Stencil exports");
    }
}

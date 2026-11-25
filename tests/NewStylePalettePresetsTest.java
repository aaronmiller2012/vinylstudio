package com.halftonestudio;

import java.util.Arrays;

/** Regression: user-picked vinyl presets must own actual exported color slots. */
public final class NewStylePalettePresetsTest {
    private static void require(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
    }
    private static int[] copy(int[] x){return x.clone();}
    private static int[] sourcePixels(int w,int h) {
        int[] pixels=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            int v=(int)(235.0*(x/(double)(w-1)));
            // A grayscale portrait-like range of brightness and horizontal shading.
            pixels[y*w+x]=0xff000000|v<<16|v<<8|v;
        }
        return pixels;
    }
    public static void main(String[] args) {
        int paper=0xfffaf9f6;
        int[] pixels=sourcePixels(96,96);
        for(int style:new int[]{VinylStyleSuite.LOW_POLY,VinylStyleSuite.GLASS,
                VinylStyleSuite.TOPOGRAPHIC,VinylStyleSuite.TEXT}) {
            for(int count=style==VinylStyleSuite.GLASS?3:2;count<=8;count++) {
                for(int preset=0;preset<VinylPalettes.NAMES.length;preset++) {
                    int[] swatches=NewStylePalettePresets.standard(style,count,paper,preset);
                    require(swatches.length==8,"All eight colors retained");
                    int[] allocated=NewStyleColorSlots.fixedPalette(style,0,count,swatches);
                    require(allocated.length==(style==VinylStyleSuite.GLASS?count-1:count),
                            "Allocated physical colors for style "+style);
                    if(style==VinylStyleSuite.GLASS) {
                        require(swatches[0]==VinylPalettes.get(preset,8)[0],"Lead is L1");
                        require(allocated[0]==swatches[1],"Glass first layer is L2");
                    }
                    int[] before=copy(swatches);
                    // Simulate count changes and ensure formerly inactive colors were not lost.
                    for(int n=style==VinylStyleSuite.GLASS?3:2;n<=8;n++)
                        NewStyleColorSlots.fixedPalette(style,0,n,swatches);
                    require(Arrays.equals(before,swatches),"Count change must not alter palette");
                }
                for(int preset=0;preset<ColorWorkspacePalette.TESS_NAMES.length;preset++) {
                    int[] swatches=NewStylePalettePresets.creative(style,count,paper,preset);
                    require(swatches.length==8,"Creative palette has eight physical colors");
                }
            }
        }
        int[] named=VinylPalettes.get(0,8);
        int[] namedOut=NewStylePalettePresets.saved(named,30);
        require(namedOut.length==8,"Named saved palette");
        for(int i=0;i<8;i++)require(namedOut[i]==PaletteHueShift.rotateOne(named[i],30),
                "Saved hue preserved on swatch "+i);
        // Exercise actual Topographic renderer + physical slot mapping used by preview and export.
        for(int count:new int[]{2,4,6,8}) {
            int[] swatches=NewStylePalettePresets.standard(VinylStyleSuite.TOPOGRAPHIC,count,paper,7);
            VinylStyleSuite.Config cfg=new VinylStyleSuite.Config();
            cfg.style=VinylStyleSuite.TOPOGRAPHIC;cfg.variant=0;cfg.colorCount=count;
            cfg.widthMm=120;cfg.detail=45;cfg.paper=paper;
            cfg.palette=NewStyleColorSlots.fixedPalette(cfg.style,0,count,swatches);
            StyleKit.Result result=VinylStyleSuite.generate(pixels,96,96,cfg);
            require(!result.layers.isEmpty(),"Topographic generated paths");
            for(int i=0;i<result.layers.size();i++) {
                StyleKit.Layer layer=result.layers.get(i);
                int slot=NewStyleColorSlots.slot(cfg.style,layer.name,i);
                require(slot>=0&&slot<count,"Topographic physical index "+slot);
                require(layer.color==swatches[slot],"Topographic SVG/preview color L"+(slot+1));
            }
        }
        // Glass lead underlay must be swatch L1, and pane colors belong to L2-LN.
        int[] swatches=NewStylePalettePresets.standard(VinylStyleSuite.GLASS,5,paper,0);
        VinylStyleSuite.Config c=new VinylStyleSuite.Config();
        c.style=VinylStyleSuite.GLASS;c.colorCount=5;c.detail=30;
        c.widthMm=120;c.paper=paper;c.leadMm=1.3f;c.leadColor=swatches[0];
        c.palette=NewStyleColorSlots.fixedPalette(c.style,0,c.colorCount,swatches);
        StyleKit.Result glass=VinylStyleSuite.generate(pixels,96,96,c);
        require(!glass.layers.isEmpty(),"Glass generated");
        for(int i=0;i<glass.layers.size();i++){
            StyleKit.Layer layer=glass.layers.get(i);
            int slot=NewStyleColorSlots.slot(c.style,layer.name,i);
            require(slot>=0&&slot<5,"Glass layer physical slot");
            require(layer.color==swatches[slot],"Glass physical SVG/preview color L"+(slot+1));
        }
        System.out.println("PASS: 19 built-in + 7 creative presets, 2-8 physical colors, saved hue, Topographic mapping, Glass lead");
    }
}

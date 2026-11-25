package com.halftonestudio;
import java.util.*;
public final class StyleColorsIntegrationTest {
    static void ok(boolean b,String message){if(!b)throw new AssertionError(message);}
    public static void main(String[] args){
        int[] chosen={0xff201f2a,0xff7c4236,0xfff2d0a3,0xffa86f52,0xff1b567e,0xffa0ae6d,0xffd0803f,0xff703e8c};
        ok(NewStyleColorSlots.slotCount(VinylStyleSuite.GLASS,0,8)==8,"lead included in eight");
        ok(NewStyleColorSlots.slotCount(VinylStyleSuite.TSP,0,6)==1,"TSP one actual layer");
        ok(NewStyleColorSlots.slotCount(VinylStyleSuite.LINOCUT,1,6)==2,"crosshatch has two layers");
        ok(NewStyleColorSlots.slotCount(VinylStyleSuite.LINOCUT,0,6)==1,"single pass one layer");
        ok(NewStyleColorSlots.slot(VinylStyleSuite.GLASS,"lead",99)==0,"lead L1");
        ok(NewStyleColorSlots.slot(VinylStyleSuite.GLASS,"glass-3",0)==3,"glass-3 L4");
        ok(NewStyleColorSlots.slot(VinylStyleSuite.LOW_POLY,"lowpoly-6",0)==5,"skipped lowpoly swatches");
        ok(NewStyleColorSlots.slot(VinylStyleSuite.TEXT,"text-6",0)==5,"text swatch");
        ok(NewStyleColorSlots.slot(VinylStyleSuite.TOPOGRAPHIC,"isoline-4",0)==3,"isoline swatch");
        ok(NewStyleColorSlots.slot(VinylStyleSuite.LINOCUT,"linocut-cross",0)==1,"cross ink slot");
        int[] glass=NewStyleColorSlots.fixedPalette(VinylStyleSuite.GLASS,0,6,chosen);
        ok(glass.length==5&&glass[0]==chosen[1]&&glass[4]==chosen[5],"glass palette excludes lead");
        int[] testRGB=new int[112*128];
        for(int y=0;y<128;y++)for(int x=0;x<112;x++){
            int col=(x<48?chosen[2]:x<82?chosen[3]:chosen[4]);
            if(y>65)col=(x<56?chosen[0]:chosen[5]);
            testRGB[y*112+x]=col;
        }
        for(int style=0;style<8;style++){
            for(int variant=0;variant< (style==VinylStyleSuite.LINOCUT?2:1);variant++){
                VinylStyleSuite.Config cfg=new VinylStyleSuite.Config();
                cfg.style=style;cfg.detail=35;cfg.variant=variant;cfg.colorCount=6;
                cfg.widthMm=152.4f;cfg.paper=0xfffaf9f5;cfg.leadColor=chosen[0];
                cfg.palette=NewStyleColorSlots.fixedPalette(style,variant,cfg.colorCount,chosen);
                StyleKit.Result result=VinylStyleSuite.generate(testRGB,112,128,cfg);
                ok(result.layers.size()>0,"empty "+style);
                for(int i=0;i<result.layers.size();i++){
                    StyleKit.Layer layer=result.layers.get(i);
                    int slot=NewStyleColorSlots.slot(style,layer.name,i);
                    ok(slot<NewStyleColorSlots.slotCount(style,variant,cfg.colorCount),"invalid color slot "+style+" "+layer.name);
                    ok(layer.color==chosen[slot],"wrong cut color "+style+" "+layer.name+" #"+Integer.toHexString(layer.color)+" wanted #"+Integer.toHexString(chosen[slot]));
                }
                ok(result.layers.size()<=NewStyleColorSlots.slotCount(style,variant,cfg.colorCount),"too many colors "+style);
                System.out.println(VinylStyleSuite.NAMES[style]+" variant "+variant+" palette/physical layer PASS");
            }
        }
        System.out.println("All eight color and layer mappings PASS");
    }
}

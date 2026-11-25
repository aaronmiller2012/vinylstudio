package com.halftonestudio;
import java.util.*;

/** Cutting-only regression tests: fixed CMYK channel identities across physical stack changes. */
public final class VinylOnlyWorkflowTest {
    static void check(boolean valid,String description){if(!valid)throw new AssertionError(description);}
    public static void main(String[] args){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        check(s.mode==VinylEngine.CMYK&&s.layers.size()==4,"four-color CMYK remains default");
        int[] channels={2,0,1,3};
        for(int i=0;i<4;i++)check(s.layers.get(i).cmykChannel==channels[i],"CMYK semantic identity at start");
        s.widthMm=65;
        s.masterSpacingMm=4.6f;
        s.minimumMm=.65f;
        s.marginMm=2;
        s.pattern=VinylEngine.ROUND;
        s.anglePreset=VinylEngine.ANGLES_CLASSIC;
        int w=40,h=35;int[] pix=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int red=(x*255)/(w-1),green=(y*255)/(h-1),blue=((x+y)*255)/(w+h-2);
            pix[y*w+x]=0xff000000|(red<<16)|(green<<8)|blue;
        }
        VinylEngine.Result original=VinylEngine.generate(pix,w,h,s);
        Map<Integer,String> sourcePaths=new HashMap<>();
        for(int i=0;i<4;i++)sourcePaths.put(original.settings.layers.get(i).cmykChannel,VinylEngine.svg(original,i));
        Collections.reverse(s.layers); // Reordering the physical stack must not alter channel-specific geometry.
        VinylEngine.Result reversed=VinylEngine.generate(pix,w,h,s);
        for(int i=0;i<4;i++){
            int channel=reversed.settings.layers.get(i).cmykChannel;
            check(sourcePaths.get(channel).equals(VinylEngine.svg(reversed,i)),
                "physical application reorder must not change the "+channel+" CMYK cut geometry");
        }
        check(VinylEngine.svgElementsForLayer(reversed,0)==reversed.shapes.get(0).size()+VinylEngine.registrationElements(reversed),"each SVG includes its own registration geometry");
        check(VinylEngine.validateExport(reversed)==null,"reversed test stack remains individually exportable");
        VinylEngine.Settings custom=s.copy();custom.mode=VinylEngine.SPOT;
        for(VinylEngine.Layer l:custom.layers)l.cmykChannel=-1;
        check(custom.layers.size()==4,"switching to spot retains four independent vinyl colors");
        System.out.println("PASS: vinyl-only 4-color defaults; reverse stack preserves channel geometry and SVG marks; custom colors remain available");
    }
}

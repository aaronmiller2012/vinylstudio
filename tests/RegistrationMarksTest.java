package com.halftonestudio;

import java.util.ArrayList;
import java.util.List;

/** Generated SVGs must carry identical coordinates, shapes, sizes and artboard in each layer. */
public final class RegistrationMarksTest {
    private static void ok(boolean v,String reason){if(!v)throw new AssertionError(reason);}
    static int[] pixels(int w,int h){int[] a=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)a[y*w+x]=0xff000000 | ((x*255/w)<<16) | ((y*255/h)<<8) | 0x000066;return a;}
    private static String markerBlock(VinylEngine.Result result, int li){
        String svg=VinylEngine.svg(result,li);
        StringBuilder out=new StringBuilder();for(float[] p:result.marks)out.append(RegistrationMarks.svgMark(result.settings.markStyle,p[0],p[1],result.settings.markSizeMm));
        ok(svg.contains(out.toString()),"SVG must contain same four closed registration shapes: " + li);
        return out.toString();
    }
    public static void main(String[] args){
        for(int style=0;style<=6;style++)for(float diameter:new float[]{2f,2.5f,4f,8f,12f}){
            VinylEngine.Settings settings=VinylEngine.Settings.defaults();settings.mode=VinylEngine.CMYK;settings.markStyle=style;settings.markSizeMm=diameter;
            VinylEngine.Result result=VinylEngine.generate(pixels(32,32),32,32,settings);
            ok(VinylEngine.validateMarks(result)==null,"New default marks should fit: "+style+"/"+diameter);
            ok(VinylEngine.registrationElements(result)==4,"Each mark must be one closed vector element");
            String expected=markerBlock(result,0);
            for(int li=1;li<4;li++)ok(markerBlock(result,li).equals(expected),"Every vinyl color gets EXACT same marks");
            ok(VinylEngine.svg(result,0).contains("width=\""),"Physical mm SVG artboard is mandatory");
            float radius=RegistrationMarks.markRadius(diameter,settings.registrationClearanceMm);
            ok(result.marks[0][0]-radius >= -result.padMm,"Mark+clearance must fit canvas");
            float[] xy=RegistrationMarks.outline(style,0,0,diameter);
            if(style==RegistrationMarks.CIRCLE)ok(xy==null,"Circle must remain a single SVG circle");
            else ok(xy.length>=6&&xy.length%2==0,"Single closed outline required");
        }
        VinylEngine.Settings custom=VinylEngine.Settings.defaults();custom.markStyle=RegistrationMarks.STAR;custom.markSizeMm=10f;
        custom.marks=new float[][]{{-12,-11},{custom.widthMm+12,-11},{-12,custom.widthMm+12},{custom.widthMm+12,custom.widthMm+12}};
        VinylEngine.Result result=VinylEngine.generate(pixels(32,32),32,32,custom);
        ok(VinylEngine.validateMarks(result)==null,"Manually positioned stars are allowed when clear");
        ok(result.settings.markSizeMm==10f,"Mark size survives settings snapshot");
        custom.markSizeMm=2f;ok(result.settings.markSizeMm==10f,"Render snapshot must not mutate after settings edit");
        custom.markSizeMm=13f;
        try{VinylEngine.generate(pixels(32,32),32,32,custom);throw new AssertionError("Invalid mark size accepted");}
        catch(IllegalArgumentException expected){}
        System.out.println("PASS: 7 registration shapes, five sizes, identical C/M/Y/K SVG marks, single-cut paths and clearance");
    }
}

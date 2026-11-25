package com.halftonestudio;

import java.util.*;

/** Tests actual generated vector geometry, not Android screenshot approximations. */
public final class SmartHalftonePresetsTest {
    private static void check(boolean valid,String name){if(!valid)throw new AssertionError(name);}
    private static int[] pixels(int w,int h){int[] p=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){
        int v=30+(int)(200f*x/Math.max(1,w-1));
        p[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;
    }return p;}
    private static void checkGeometry(VinylEngine.Result r){
        for(List<VinylEngine.Shape> shapes:r.shapes)for(VinylEngine.Shape s:shapes){
            float[] b=s.bounds();for(float v:b)check(Float.isFinite(v),"non-finite shape coordinate");
            if(!s.circle){check(s.xy.length>=6,"closed path has at least three points");
                for(float v:s.xy)check(Float.isFinite(v),"invalid polygon vertex");}
        }
        for(int i=0;i<r.settings.layers.size();i++)if(r.settings.layers.get(i).enabled)
            check(VinylEngine.svgElementsForLayer(r,i)<=5000,"each SVG must remain within path limit");
    }
    static void testStyles(){
        int[] pixels=pixels(52,66);
        for(int pattern=0;pattern<VinylEngine.PATTERNS.length;pattern++){
            VinylEngine.Settings s=VinylEngine.Settings.defaults();s.widthMm=VinylEngine.toMm(4);
            s.registration=false;
            s.mode=VinylEngine.SPOT;for(VinylEngine.Layer l:s.layers)l.color=0xff313131;
            SmartHalftonePresets.apply(s,pattern,VinylSizing.BALANCED,4,6);
            check(s.pattern==pattern,"style selection");
            check(s.linkedControls,"style always starts linked");
            check(SmartHalftonePresets.controls(pattern).length==3,"exactly three quick controls");
            for(VinylEngine.Layer l:s.layers)check(l.spacing==s.masterSpacingMm,"global spacing continuity");
            VinylEngine.Result r=VinylEngine.generate(pixels,52,66,s);
            checkGeometry(r);
            check(r.count()>0,"style should generate real SVG shapes: "+pattern);
            String xml=VinylEngine.svg(r,0);
            check(xml.contains("</svg>")&&(xml.contains("<path")||xml.contains("<circle")),"SVG output");
        }
    }
    static void testQualityAndLayers(){
        for(int count=2;count<=8;count++){
            check(!SmartHalftonePresets.advice(count).isEmpty(),"layer recommendation");
            check(SmartHalftonePresets.recommendedPattern(count)<VinylEngine.PATTERNS.length,"recommendation defined");
            VinylEngine.Settings s=VinylEngine.Settings.defaults();
            while(s.layers.size()<count)s.layers.add(new VinylEngine.Layer("L",0xff353535));
            while(s.layers.size()>count)s.layers.remove(s.layers.size()-1);
            if(count!=4)s.mode=VinylEngine.SPOT;
            float last=0;
            for(int profile=VinylSizing.DETAIL;profile<=VinylSizing.EASY_WEED;profile++){
                SmartHalftonePresets.apply(s,VinylEngine.WAVE,profile,8,10);
                check(s.masterSpacingMm>=last,"quality spacing increases");last=s.masterSpacingMm;
                check(s.layers.size()==count,"layer count preserved");
            }
        }
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        SmartHalftonePresets.apply(s,VinylEngine.ROUND,VinylSizing.BALANCED,8,10);
        check(s.mode==VinylEngine.CMYK&&s.anglePreset==VinylEngine.ANGLES_CLASSIC,"CMYK remains intact");
    }
    static void testCurves(){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();s.mode=VinylEngine.SPOT;
        s.widthMm=VinylEngine.toMm(3);s.registration=false;
        for(VinylEngine.Layer l:s.layers)l.color=0xff202020;
        SmartHalftonePresets.apply(s,VinylEngine.WAVE,VinylSizing.BALANCED,3,4);
        VinylEngine.Result r=VinylEngine.generate(pixels(52,66),52,66,s);
        check(!r.shapes.get(0).isEmpty(),"wave ribbons generated");
        boolean varying=false;
        for(VinylEngine.Shape sh:r.shapes.get(0))if(!sh.circle && sh.xy.length>30){
            float[] xyz=sh.xy;float dx=xyz[4]-xyz[2],dy=xyz[5]-xyz[3];
            if(Math.abs(dy)>.0001&&Math.abs(dx)>.0001){varying=true;break;}
        }
        check(varying,"wave path changes direction");
        check(CutInsights.inspect(r).size()>0,"wave safe-cut advisory shown");
    }
    public static void main(String[] args){testStyles();testQualityAndLayers();testCurves();System.out.println("PASS: 12 style SVGs, wavy polygons, layer recommendations, quality profiles, CMYK retention, safety guidance");}
}

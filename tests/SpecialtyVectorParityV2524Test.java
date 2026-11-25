package com.halftonestudio;

import android.graphics.Path;
import java.util.*;
/** Host Android-Path-stub tests: prove rendered closed contours match SVG geometry. */
public final class SpecialtyVectorParityV2524Test {
    static void check(boolean condition,String why){if(!condition)throw new AssertionError(why);}
    static int[] image(int w,int h){
        int[] pixels=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int v=(x*7+y*11)%175+60;
            int r=v,g=(v+60)%255,b=(v+20)%255;
            pixels[y*w+x]=((x>8&&x<w-8&&y>7&&y<h-7)?0xff000000:0x00000000)|(r<<16)|(g<<8)|b;
        }
        return pixels;
    }
    static void testTess(){
        int w=90,h=100;TessellationEngine.Options o=new TessellationEngine.Options();
        o.layerCount=4;o.detail=35;o.style=TessellationEngine.GRID_MOTIF;o.registration=false;
        TessellationEngine.Result r=TessellationEngine.generate(image(w,h),w,h,o);
        SpecialtyVectorCache c=SpecialtyVectorCache.tessellation(r);
        check(c.source==r&&c.width==w&&c.height==h,"tess source and coordinates");
        for(int i=0;i<4;i++)check(c.layers[i].moves==r.counts[i],"tess contours L"+i+": "+c.layers[i].moves+" != "+r.counts[i]);
    }
    static void testLayered(){
        int w=90,h=100;LayeredCellPortraitEngine.Options o=new LayeredCellPortraitEngine.Options();
        o.layerCount=4;o.columns=12;o.motifDepth=2;o.registration=false;
        LayeredCellPortraitEngine.Result r=LayeredCellPortraitEngine.generate(image(w,h),w,h,o);
        SpecialtyVectorCache c=SpecialtyVectorCache.layered(r);
        for(int i=0;i<4;i++)check(c.layers[i].moves==r.contourCounts[i],"layered contours L"+i);
    }
    static void testTonal(){
        int w=48,h=60;MultilayerStencilEngine.Options o=new MultilayerStencilEngine.Options();
        o.layerCount=4;o.minimumIslandPixels=0;o.autoWhiteBackground=false;o.registration=false;
        MultilayerStencilEngine.Result r=MultilayerStencilEngine.generate(image(w,h),w,h,o);
        SpecialtyVectorCache c=SpecialtyVectorCache.tonal(r);
        for(int i=0;i<4;i++){
            String path=r.cutPathData(i);
            if(path.contains("C"))check(c.layers[i].cubics>0,"tonal preview must render export curves L"+i);
            check(c.layers[i].moves==MultilayerStencilEngine.countUnionContours(r.layers.get(i).mask,w,h),"tonal cut contours L"+i);
            check(c.topVisible[i].moves==MultilayerStencilEngine.countUnionContours(r.layers.get(i).visibleMask,w,h),"tonal visible contours L"+i);
            check(c.layers[i].fillType==Path.FillType.EVEN_ODD,"tonal holes L"+i);
        }
    }
    static void testRibbon(){
        int w=30,h=35;boolean[] m=new boolean[w*h];
        for(int y=2;y<h-2;y++)for(int x=4;x<w-4;x++)m[y*w+x]=true;
        for(int y=14;y<21;y++)for(int x=10;x<16;x++)m[y*w+x]=false;
        SpaceFillingRibbon.Result r=new SpaceFillingRibbon.Result(w,h,80,90,3,.6f,2,m,640,false);
        SpecialtyVectorCache c=SpecialtyVectorCache.ribbon(r);
        check(c.layers[0].moves==2,"ribbon has outer and hole");
        check(c.layers[0].closes==2,"ribbon loops closed");
    }
    static void testParser(){
        Path p=SpecialtyVectorCache.pathFromMLZ("M1 2 L3 4 L5 6 Z M8 9 L11 10 Z",0,30);
        check(p.moves==2&&p.lines==3&&p.closes==2,"contour parser segments");
        boolean fail=false;try{SpecialtyVectorCache.pathFromMLZ("M1 2 Q3 4",0,9);}catch(IllegalArgumentException ok){fail=true;}
        check(fail,"parser rejects incompatible SVG commands instead of misrendering");
    }
    public static void main(String[] args){testParser();testTess();testLayered();testTonal();testRibbon();System.out.println("PASS v2.52.4 vector parity: tessellation, layered-cell, tonal masks, ribbon holes, parser");}
}

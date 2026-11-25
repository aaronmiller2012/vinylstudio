package com.halftonestudio;
import java.util.*;
/** Derive coverage IDs from the actual Java registries; no fake benchmarks. */
public final class V531ProfileRegistry {
    static void line(String key,String name){System.out.println(key+","+name.replace(',',' '));}
    public static void main(String[] args){
        System.out.println("style_key,name");
        for(int style:VinylEngine.SHAPE_STYLES)line("standard:"+style,VinylEngine.PATTERNS[style]);
        for(int style:new int[]{VinylEngine.LINE,VinylEngine.STRAIGHT,VinylEngine.CONTOUR,VinylEngine.WAVE,VinylEngine.HATCH,VinylEngine.CROSSHATCH,VinylEngine.ZIGZAG,VinylEngine.BROKEN})line("standard:"+style,VinylEngine.PATTERNS[style]);
        for(int style=0;style<VinylStyleSuite.NAMES.length;style++)if(NewStyleVariantPolicy.selectable(style)){
            for(int v=0;v<NewStyleVariantPolicy.count(style);v++)if(NewStyleVariantPolicy.restore(style,v)==v){
                String name=style==VinylStyleSuite.TESSELLATION_MOTIF?TessellationMotifEngine.PATTERNS[v]:VinylStyleSuite.NAMES[style]+" variant "+v;
                line("newstyle:"+style+":"+v,name);
            }
        }
        line("tonal","Tonal Stencil");line("mosaic:0","Voronoi Mosaic");line("mosaic:1","Grid Mosaic");line("mosaic:2","Weighted Stipple");
        for(String k:new String[]{"layered","offset","cap","scale","overprint-1","overprint-2","overprint-3","overprint-4"})line("cell:"+k,"Cell "+k);
        for(String k:new String[]{"SPIRAL","ADAPTIVE_HILBERT","GOSPER","PEANO","WUNDERLICH","MOORE","COIL"})line("single:"+k,k);
    }
}

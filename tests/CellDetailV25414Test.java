package com.halftonestudio;

import java.util.*;
/** Hard-fail photographic detail/cut geometry regression for all three cell styles. */
public final class CellDetailV25414Test {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static int[] photo(int w,int h){
        int[] out=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int c=0xffd5d6d4; // intentionally uniform background
            boolean face=(x-100)*(x-100)/4900f+(y-100)*(y-100)/7000f<1;
            if(face)c=0xffbb876d;
            if(face&&(y<42||x<39))c=0xff5b4039;
            if((x-76)*(x-76)/130f+(y-87)*(y-87)/40f<1 ||
               (x-123)*(x-123)/130f+(y-87)*(y-87)/40f<1)c=0xff27222c;
            if(y>121&&y<129&&x>79&&x<119)c=0xff59362d;
            if(y>170&&x>30&&x<170)c=0xff25495c;
            out[y*w+x]=c;
        }
        return out;
    }
    static int paneCount(StyleKit.Result result){int n=0;for(StyleKit.Layer l:result.layers)if(!l.name.equals("lead"))n+=l.loops.size();return n;}
    static int inCenter(List<TessellationEngine.Tile> tiles,int x0,int y0,int x1,int y1){
        int count=0;for(TessellationEngine.Tile t:tiles){float[] p=t.xy;float x=0,y=0;
            for(int i=0;i<p.length;i+=2){x+=p[i];y+=p[i+1];}
            x/=p.length/2;y/=p.length/2;
            if(x>=x0&&x<x1&&y>=y0&&y<y1)count++;
        }return count;
    }
    public static void main(String[] args){
        int w=200,h=210;int[] photo=photo(w,h);
        PhotoCellImportance map=PhotoCellImportance.create(photo,w,h,null);
        check(map.maxIn(60,78,136,95)>.3f,"eye detail missing from map");
        check(map.maxIn(1,1,35,30)<.15f,"uniform background incorrectly high");
        for(int style=0;style<=1;style++){
            TessellationEngine.Options low=new TessellationEngine.Options();
            low.style=style;low.detail=10;low.layerCount=5;low.registration=false;
            TessellationEngine.Options high=low.copy();high.detail=92;
            TessellationEngine.Result a=TessellationEngine.generate(photo,w,h,low);
            TessellationEngine.Result b=TessellationEngine.generate(photo,w,h,high);
            check(b.tiles.size()>a.tiles.size(),"increasing detail did not increase shape count for style "+style);
            check(b.tiles.size()<=TessellationEngine.MAX_TILES,"unbounded tessellation tiles");
            check(b.svg(1).equals(TessellationEngine.generate(photo,w,h,high).svg(1)),"non-deterministic tessellation output");
            int nearEyes=inCenter(b.tiles,53,66,144,111);
            int background=inCenter(b.tiles,0,0,90,45);
            check(nearEyes>0&&background>0,"missing sample regions for style "+style);
            System.out.println((style==0?"Voronoi":"Grid")+": low="+a.tiles.size()+" high="+b.tiles.size()+" eye="+nearEyes+" bg="+background);
        }
        StainedGlassEngine.Options low=new StainedGlassEngine.Options();low.widthMm=152.4f;
        low.leadMode=StainedGlassEngine.LeadMode.UNDERLAY;low.maxPieces=190;
        low.minSpacingMm=4.3f;low.maxSpacingMm=20f;
        low.palette=new int[]{0xffbb876d,0xff5b4039,0xff27222c,0xff25495c};
        StainedGlassEngine.Options high=low.copy();high.maxPieces=900;
        high.minSpacingMm=2.95f;high.maxSpacingMm=8f;
        StyleKit.Result l=StainedGlassEngine.generate(photo,w,h,low),r=StainedGlassEngine.generate(photo,w,h,high);
        check(paneCount(r)>paneCount(l),"stained glass high detail did not add panes");
        check(r.layers.size()<=8,"stained glass more than 8 layers");
        check(r.layers.get(0).name.equals("lead"),"lead not base");
        check(r.layers.get(0).loops.size()==1,"lead underlay missing");
        check(r.layers.get(0).color==low.leadArgb,"lead color changed");
        System.out.println("Glass: low="+paneCount(l)+" high="+paneCount(r));
        boolean[] mask=new boolean[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)
            mask[y*w+x]=x>25&&x<175&&y>20&&y<190;
        high.allowed=mask;
        VinylStyleSuite.Config config=new VinylStyleSuite.Config();config.style=VinylStyleSuite.GLASS;
        config.colorCount=5;config.detail=92;config.allowed=mask;
        StyleKit.Result clipped=VinylStyleSuite.generate(photo,w,h,config);
        check(!clipped.layers.isEmpty()&&clipped.layers.get(0).name.equals("lead"),"clipped lead missing");
        check(clipped.layers.size()<=8,"masked glass layer overflow");
        System.out.println("PASS: shared feature importance, detail scaling, mask, palette, lead and deterministic cells");
    }
}

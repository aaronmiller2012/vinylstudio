package com.halftonestudio;

import java.util.*;

/** Geometry and export regression test; requires standard Java only. */
public final class TessellationEngineTest {
    static void check(boolean good,String message){if(!good)throw new AssertionError(message);}
    static int paths(String svg){int c=0,i=0;while((i=svg.indexOf("<path",i))>=0){c++;i+=5;}return c;}
    static boolean contains(float[] polygon,float x,float y){boolean hit=false;for(int i=0,j=polygon.length/2-1;i<polygon.length/2;j=i++){
        float xi=polygon[2*i],yi=polygon[2*i+1],xj=polygon[2*j],yj=polygon[2*j+1];
        if((yi>y)!=(yj>y)&&x<(xj-xi)*(y-yi)/(yj-yi)+xi)hit=!hit;
    }return hit;}
    public static void main(String[] args){
        int w=120,h=130;int[] px=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int lum=Math.max(0,Math.min(255,(x*2+y/2)%256));
            if((x-61)*(x-61)+(y-56)*(y-56)<500)lum/=2;
            px[y*w+x]=0xff000000|(lum<<16)|(lum<<8)|lum;
        }
        for(int mode=0;mode<=1;mode++)for(int layers=2;layers<=8;layers++){
            TessellationEngine.Options o=new TessellationEngine.Options();o.style=mode;o.layerCount=layers;o.detail=34;o.gapMm=.6f;
            TessellationEngine.Result result=TessellationEngine.generate(px,w,h,o);
            check(result.populated()>=1&&result.populated()<=layers,
                "invalid photo-owned layer count: "+mode+" / "+layers);
            check(result.tiles.size()>=layers*2,"too few shapes");
            int count=0;
            for(int i=0;i<layers;i++){
                String svg=result.svg(i);count+=result.counts[i];
                check(paths(svg)==result.counts[i]+4,"SVG path count mismatch");
                check(!svg.contains("<clipPath")&&!svg.contains("<image"),"unsupported raster or clipping");
                check(svg.contains(" Z\"/>"),"closed contour missing");
                check(paths(svg)<5000,"SVG path budget");
                check(svg.contains("width=\""),"physical size missing");
            }
            check(count==result.tiles.size(),"vector contour count mismatch");
            // No two independent polygons should cover the same interior sample.
            Random rand=new Random(9);for(int n=0;n<350;n++){
                float x=rand.nextFloat()*w,y=rand.nextFloat()*h;int overlap=0;
                for(TessellationEngine.Tile tile:result.tiles)if(contains(tile.xy,x,y))overlap++;
                check(overlap<=1,"overlap at "+x+","+y+" / mode "+mode);
            }
            TessellationEngine.Result again=TessellationEngine.generate(px,w,h,o);
            check(result.svg(0).equals(again.svg(0)),"non-deterministic output");
        }
        TessellationEngine.Options circle=new TessellationEngine.Options();circle.style=0;circle.layerCount=4;circle.detail=42;
        circle.allowedPixels=new boolean[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)circle.allowedPixels[y*w+x]=(x>19&&x<100&&y>21&&y<114);
        TessellationEngine.Result masked=TessellationEngine.generate(px,w,h,circle);
        for(TessellationEngine.Tile t:masked.tiles)for(int i=0;i<t.xy.length;i+=2)check(t.xy[i]>=19&&t.xy[i]<=100&&t.xy[i+1]>=21&&t.xy[i+1]<=114,"snip escaped");
        System.out.println("PASS: V2.26 mosaic 2–8 vector colors, paths/counts, disjoint cells, masks, deterministic export, physical SVG size");
    }
}

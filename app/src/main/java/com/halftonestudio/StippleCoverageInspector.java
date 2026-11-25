package com.halftonestudio;

import java.util.Arrays;
/** Rasterized circle-envelope diagnostic. Not a final SVG geometry or cut certification. */
public final class StippleCoverageInspector {
    private StippleCoverageInspector() {}
    public static final class Scan {
        public final int width,height,eligible,covered;public final int[] heatmap;
        Scan(int w,int h,int e,int c,int[] map){width=w;height=h;eligible=e;covered=c;heatmap=map;}
        public float percent(){return eligible==0?0f:100f*covered/eligible;}
    }
    private static boolean nearPaper(int c,int paper){
        int a=(c>>>24)&255;if(a<64)return true;
        int delta=Math.abs(((c>>16)&255)-((paper>>16)&255))+
                  Math.abs(((c>>8)&255)-((paper>>8)&255))+Math.abs((c&255)-(paper&255));
        return delta<22;
    }
    public static Scan inspect(MultiColorStippleEngine.Result result,int[] source,int selectedLayer){
        if(result==null)throw new IllegalArgumentException("Missing stipple vectors");
        final int w=result.width,h=result.height,sz=w*h;
        if(sz<=0||sz>2000000)throw new IllegalArgumentException("Invalid diagnostic raster");
        if(source!=null&&source.length!=sz)throw new IllegalArgumentException("Source size differs from vector raster");
        boolean[] eligible=new boolean[sz],covered=new boolean[sz];
        for(int i=0;i<sz;i++){
            eligible[i]=(result.options.mask==null||result.options.mask[i]) &&
                (source==null||!nearPaper(source[i],result.options.paper));
        }
        for(MultiColorStippleEngine.Dot dot:result.dots){
            if(selectedLayer>=0&&dot.layer!=selectedLayer)continue;
            int x0=Math.max(0,(int)Math.floor(dot.x-dot.r)),x1=Math.min(w-1,(int)Math.ceil(dot.x+dot.r));
            int y0=Math.max(0,(int)Math.floor(dot.y-dot.r)),y1=Math.min(h-1,(int)Math.ceil(dot.y+dot.r));
            float rr=dot.r*dot.r;
            for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){
                float dx=x+.5f-dot.x,dy=y+.5f-dot.y;
                if(dx*dx+dy*dy<=rr)covered[y*w+x]=true;
            }
        }
        // Two-pass 8-neighbour chamfer distance; distance measured from covered pixels.
        int[] dist=new int[sz];Arrays.fill(dist,10000);
        int total=0,done=0;
        for(int i=0;i<sz;i++)if(eligible[i]){total++;if(covered[i]){done++;dist[i]=0;}}
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int i=y*w+x;
            if(x>0)dist[i]=Math.min(dist[i],dist[i-1]+3);
            if(y>0)dist[i]=Math.min(dist[i],dist[i-w]+3);
            if(x>0&&y>0)dist[i]=Math.min(dist[i],dist[i-w-1]+4);
            if(x<w-1&&y>0)dist[i]=Math.min(dist[i],dist[i-w+1]+4);
        }
        for(int y=h-1;y>=0;y--)for(int x=w-1;x>=0;x--){
            int i=y*w+x;
            if(x<w-1)dist[i]=Math.min(dist[i],dist[i+1]+3);
            if(y<h-1)dist[i]=Math.min(dist[i],dist[i+w]+3);
            if(x<w-1&&y<h-1)dist[i]=Math.min(dist[i],dist[i+w+1]+4);
            if(x>0&&y<h-1)dist[i]=Math.min(dist[i],dist[i+w-1]+4);
        }
        int[] argb=new int[sz];
        for(int i=0;i<sz;i++)argb[i]=!eligible[i]?0xff212933:covered[i]?0xff488b7e:dist[i]<24?0xffdfaa4d:0xffcf6266;
        return new Scan(w,h,total,done,argb);
    }
}

package com.halftonestudio;

/**
 * Beta 6 host gate: dense reference motifs at the actual maximum UI Detail.
 * Run with -ea; no Android APIs or source photograph required.
 */
public final class TessellationHighDetailHostTest {
    private static void check(boolean ok,String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static int[] image(int w,int h) {
        int[] pixels=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int v=(int)(60+165*(.5+.5*Math.sin((x-50)*.07)*Math.cos((y-80)*.07)));
            pixels[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;
        }
        return pixels;
    }
    private static boolean[] oval(int w,int h){
        boolean[] result=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double xx=(x-w*.5)/(w*.31),yy=(y-h*.5)/(h*.41);
            result[y*w+x]=xx*xx+yy*yy<1;
        }
        return result;
    }
    private static int overlays(StyleKit.Result result){
        int count=0;
        for(int i=1;i<result.layers.size();i++)for(float[] poly:result.layers.get(i).loops){
            check(poly.length>=6&&poly.length%2==0,"Malformed closed polygon");
            for(float coordinate:poly)check(Float.isFinite(coordinate),"Nonfinite vertex");
            count++;
        }
        return count;
    }
    public static void main(String[] args){
        final int w=192,h=192;
        int[] pixels=image(w,h);
        boolean[] mask=oval(w,h);
        // These are intentionally below the measured 100% counts for the
        // frozen silhouette fixture, but above the Beta 5 outputs. This catches
        // accidental reintroduction of the coarse base unit and wasted mask budget.
        final int[] minAt100={600,650,300,1100,650,850,1000};
        for(int id=22;id<=28;id++){
            if(!TessellationMotifEngine.selectablePattern(id))continue;
            TessellationMotifEngine.Options options=new TessellationMotifEngine.Options();
            options.pattern=id;options.allowed=mask;options.widthMm=152.4f;
            options.detail=50;int medium=overlays(TessellationMotifEngine.generate(pixels,w,h,options));
            options.detail=100;
            StyleKit.Result high=TessellationMotifEngine.generate(pixels,w,h,options);
            int dense=overlays(high);
            check(dense>=minAt100[id-22],"Detail 100 is still too coarse for "+id+": "+dense);
            check(dense>medium*1.65,"Density slider must still increase detail for "+id);
            check(dense<=2300,"A silhouette exceeded the physical motif budget for "+id);
            check(high.layers.size()==8,"Default physical layer count changed");
            for(StyleKit.Layer layer:high.layers){
                check(layer.loops.size()<=4800,"A physical cut sheet exceeded budget");
                check(layer.lines.isEmpty(),"A blade-cut style gained open centerlines");
            }
            check(StyleKit.toSvg(high,false).equals(StyleKit.toSvg(
                TessellationMotifEngine.generate(pixels,w,h,options),false)),
                "Unstable export geometry for "+id);
            System.out.println(TessellationMotifEngine.PATTERNS[id]+": "+medium+" -> "+dense+" overlay contours");
        }
        // 2-layer and width extreme cases must remain valid, not overflow or
        // throw when the crop is small or the physical substrate is large.
        for(float width:new float[]{20f,279.4f,2000f}){
            TessellationMotifEngine.Options o=new TessellationMotifEngine.Options();
            o.widthMm=width;o.layerCount=2;o.palette=new int[]{0xff221e20,0xffefcbb2};
            o.pattern=TessellationMotifEngine.FRAMED_SQUARE;
            o.detail=100;o.allowed=mask;
            StyleKit.Result r=TessellationMotifEngine.generate(pixels,w,h,o);
            check(r.layers.size()==2,"Two-layer tessellation changed sheet count");
            check(overlays(r)>5,"Small/large substrate unexpectedly empty");
        }
        // Zero Detail must remain a usable, not an all-empty setting.
        TessellationMotifEngine.Options low=new TessellationMotifEngine.Options();
        low.pattern=TessellationMotifEngine.STEPPED_CROSS;
        low.allowed=mask;low.detail=0;
        check(overlays(TessellationMotifEngine.generate(pixels,w,h,low))>2,"Detail zero empty");
        System.out.println("PASS Beta 6 higher photographic tessellation density, physical bounds, 2/8 sheets and SVG parity");
    }
}

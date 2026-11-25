package com.halftonestudio;
import java.util.List;
/** Topology coverage, no raw gaps/overlaps, and cuttable output of extended presets. */
public final class ExtendedTessellationHostTest {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static int[] photo(int w,int h){int[] a=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){
        int v=Math.max(0,Math.min(255,(x*153/w+y*92/h)));
        a[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;
    }return a;}
    public static void main(String[] args){
        require(TessellationMotifEngine.PATTERNS.length==37,"Variant table size changed");
        int[] img=photo(60,76);
        for(int variant=8;variant<TessellationMotifEngine.RUNNING_BOND;variant++){
            long start=System.currentTimeMillis();
            List<float[]> raw=TessellationMotifEngine.tiles(variant,75,82,9.4f);
            require(raw.size()>30,"Sparse tiling "+variant);
            int tested=0,holes=0,overlaps=0;
            for(int y=0;y<13;y++)for(int x=0;x<13;x++){
                float px=(x+.273f)*75f/13,py=(y+.417f)*82f/13;
                int n=0;
                for(float[] p:raw)if(MaskedFacetClipper.inside(p,px,py))n++;
                if(n==0)holes++;if(n>1)overlaps++;tested++;
            }
            require(holes==0&&overlaps==0,"Raw tiling not exact "+variant+" holes="+holes+" overlaps="+overlaps);
            TessellationMotifEngine.Options o=new TessellationMotifEngine.Options();o.widthMm=110;o.detail=40;o.pattern=variant;
            StyleKit.Result output=TessellationMotifEngine.generate(img,60,76,o);
            int count=0;for(StyleKit.Layer l:output.layers){count+=l.loops.size();require(l.loops.size()<=4800,"Cut path ceiling");}
            require(count>20,"Empty cut output "+variant);
            require(output.layers.size()==8,"Layer mismatch");
            System.out.println(variant+" "+TessellationMotifEngine.PATTERNS[variant]+" "+raw.size()+" tiles "+count+" paths "+(System.currentTimeMillis()-start)+"ms");
        }
        System.out.println("PASS extended tessellation coverage, cut paths and palette layers");
    }
}

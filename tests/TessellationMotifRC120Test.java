package com.halftonestudio;

import java.util.Arrays;
import java.util.List;

/** Host JVM geometry / layer / project / deterministic output checks. */
public final class TessellationMotifRC120Test {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static int[] sample(int w,int h){
        int[] a=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int v=Math.max(0,Math.min(255,(x*170/w+y*85/h)));
            if(y>h*.37&&y<h*.43&&x>w*.22&&x<w*.76)v=25;
            if(x>w*.47&&x<w*.53&&y>h*.46&&y<h*.68)v=45;
            a[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;
        }return a;
    }
    private static float area(float[] xy){double sum=0;int n=xy.length/2;
        for(int i=0;i<n;i++){int j=(i+1)%n;sum+=(double)xy[2*i]*xy[2*j+1]-(double)xy[2*j]*xy[2*i+1];}
        return (float)Math.abs(sum/2);}
    private static int contours(StyleKit.Result r){int n=0;for(StyleKit.Layer l:r.layers)n+=l.loops.size();return n;}
    public static void main(String[] args){
        check(VinylStyleSuite.TESSELLATION_MOTIF==10,"style ID changed");
        check(VinylStyleSuite.restoredStyleId(8)==-1,"retired style reused");
        check(NewStyleColorSlots.slotCount(10,3,8)==8,"palette slots missing");
        check(NewStyleVariantPolicy.count(10)==TessellationMotifEngine.PATTERN_SLOT_COUNT,"tiling count");
        int w=92,h=114;int[] photo=sample(w,h);boolean[] mask=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)mask[y*w+x]=x>8&&x<w-9&&y>6&&y<h-7;
        int total=0;
        for(int pattern=0;pattern<8;pattern++){
            TessellationMotifEngine.Options o=new TessellationMotifEngine.Options();
            o.widthMm=152.4f;o.pattern=pattern;o.detail=55;o.allowed=mask;
            StyleKit.Result a=TessellationMotifEngine.generate(photo,w,h,o);
            check(a.layers.size()==8,"layer count pattern "+pattern);
            check(contours(a)>30,"empty pattern "+pattern);
            check(a.totalPoints()>250,"missing vertices pattern "+pattern);
            for(StyleKit.Layer layer:a.layers){
                check(layer.loops.size()<4801,"cut path overflow "+pattern);
                check(layer.lines.isEmpty(),"open cutter strokes");
                for(float[] p:layer.loops){
                    check(p.length>=6&&p.length%2==0,"bad loop");
                    for(int k=0;k<p.length;k+=2)check(Float.isFinite(p[k])&&Float.isFinite(p[k+1]),"NaN");
                }
            }
            check(StyleKit.toSvg(a,false).equals(StyleKit.toSvg(TessellationMotifEngine.generate(photo,w,h,o),false)),"nondeterminism");
            total+=contours(a);
            System.out.println("Tessellation "+TessellationMotifEngine.PATTERNS[pattern]+" -> "+contours(a)+" closed loops, "+a.totalPoints()+" vertices");
        }
        // The tile substrate itself must tessellate exactly, before inset gaps.
        // Probe points deliberately avoid shared-edge coordinates.
        for(int pattern=0;pattern<8;pattern++){
            List<float[]> raw=TessellationMotifEngine.tiles(pattern,60f,55f,7.1f);
            for(int iy=0;iy<24;iy++)for(int ix=0;ix<24;ix++){
                float x=(ix+.239f)*60f/24f,y=(iy+.317f)*55f/24f;
                int covers=0;
                for(float[] cell:raw)if(MaskedFacetClipper.inside(cell,x,y))covers++;
                check(covers==1,"raw tiling has hole/overlap pattern="+pattern+" hits="+covers);
            }
        }
        check(total>350,"too few polygons");
        VinylStyleSuite.Config c=new VinylStyleSuite.Config();c.style=10;c.variant=3;
        c.detail=65;c.colorCount=8;c.widthMm=152.4f;c.minimumMm=.254f;c.motifFamily=5;
        StyleKit.Result integrated=VinylStyleSuite.generate(photo,w,h,c);
        check(contours(integrated)>30,"suite did not render");
        TessellationMotifEngine.Options noMotifs=new TessellationMotifEngine.Options();
        noMotifs.pattern=TessellationMotifEngine.TRIHEX;noMotifs.detail=80;
        noMotifs.widthMm=152.4f;noMotifs.motif=0;noMotifs.extraMotifs=false;
        StyleKit.Result pureTiles=TessellationMotifEngine.generate(photo,w,h,noMotifs);
        noMotifs.motif=5;noMotifs.extraMotifs=true;
        StyleKit.Result legacySetting=TessellationMotifEngine.generate(photo,w,h,noMotifs);
        check(StyleKit.toSvg(pureTiles,false).equals(StyleKit.toSvg(legacySetting,false)),
            "Retired motif setting still changes output");
        check(pureTiles.summary.contains("photo-colored tiles"),"Unexpected motif layer summary");
        for(StyleKit.Layer layer:pureTiles.layers)
            check(!layer.name.contains("detail"),"Decorative detail sheet still active");
        TessellationMotifEngine.Options invalid=new TessellationMotifEngine.Options();invalid.pattern=TessellationMotifEngine.PATTERNS.length;
        try{TessellationMotifEngine.generate(photo,w,h,invalid);throw new AssertionError("invalid pattern accepted");}
        catch(IllegalArgumentException expected){}
        System.out.println("PASS RC121 motif-free tessellation patterns, cut geometry, stable IDs and deterministic SVG");
    }
}

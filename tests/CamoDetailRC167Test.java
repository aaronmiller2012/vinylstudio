package com.halftonestudio;

import java.util.HashSet;
import java.util.concurrent.CancellationException;

/** Executable RC167 camouflage detail, safe cut sheets and palette fidelity. */
public final class CamoDetailRC167Test {
    private static final int W=150,H=166;
    private static void require(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
    private static int[] portrait(boolean flat){
        int[] px=new int[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            float dx=(x-W*.50f)/(W*.44f),dy=(y-H*.50f)/(H*.47f);
            float base=70f+150f*(.42f*x/W+.58f*y/H);
            if(dy<-.30f)base=48f;
            if(Math.abs(dx-.30f)<.09f && Math.abs(dy+.11f)<.055f)base=37f; // eye
            if(Math.abs(dx+.30f)<.09f && Math.abs(dy+.11f)<.055f)base=37f; // eye
            if(Math.abs(dx)<.13f && Math.abs(dy-.27f)<.045f)base=45f; // mouth
            int t=flat?122:Math.min(250,Math.max(4,Math.round(base)));
            px[y*W+x]=0xff000000|(t<<16)|(Math.max(0,t-5)<<8)|Math.max(0,t-11);
        }
        return px;
    }
    private static boolean[] snip(){
        boolean[] m=new boolean[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            double dx=(x-W*.50)/(W*.44),dy=(y-H*.50)/(H*.47);
            m[y*W+x]=dx*dx+dy*dy<=1;
        }
        return m;
    }
    private static long geometryHash(StyleKit.Result r){
        long h=0xcbf29ce484222325L;
        for(StyleKit.Layer l:r.layers)for(float[] loop:l.loops){
            h=(h^loop.length)*0x100000001b3L;
            for(int i=0;i<loop.length;i+=Math.max(2,((loop.length/13)&~1)))
                h=(h^Float.floatToIntBits(loop[i]))*0x100000001b3L;
        }
        return h;
    }
    public static void main(String[] ignored){
        final int[] photo=portrait(false),flat=portrait(true);
        final boolean[] crop=snip();
        final int[] styles={VinylStyleSuite.CAMOUFLAGE,VinylStyleSuite.CAMO_DESERT,
            VinylStyleSuite.CAMO_URBAN,VinylStyleSuite.CAMO_DIGITAL};
        final int[] patterns={NineLayerCraftPatternEngine.CAMOUFLAGE,
            NineLayerCraftPatternEngine.CAMO_DESERT,NineLayerCraftPatternEngine.CAMO_URBAN,
            NineLayerCraftPatternEngine.CAMO_DIGITAL};
        HashSet<Long> signatures=new HashSet<>();
        for(int k=0;k<styles.length;k++){
            int style=styles[k],pattern=patterns[k];
            NineLayerCraftPatternEngine.Options o=new NineLayerCraftPatternEngine.Options();
            o.pattern=pattern;o.widthMm=279.4f;o.detail=94;o.layers=9;o.minCutMm=.254f;
            o.allowed=crop;o.palette=CamouflagePalette.defaults(style,null);
            StyleKit.Source source=new StyleKit.Source(photo,W,H,crop,o.widthMm);
            float pitch=NineLayerCraftPatternEngine.camouflagePitch(source,o);
            float legacy=(8.1f-o.detail*.037f)*1.09f;
            require(pitch<legacy*.72f,"Camo detail not denser than RC166 "+style);
            require(pitch>o.minCutMm*5f,"Pitch undermines physical minimum");
            o.draft=true;
            require(NineLayerCraftPatternEngine.camouflagePitch(source,o)>pitch,"Draft not cheaper");
            o.draft=false;
            float left=NineLayerCraftPatternEngine.camouflageTone(source,42,79,pitch,.7f,pattern);
            float right=NineLayerCraftPatternEngine.camouflageTone(source,75,79,pitch,.7f,pattern);
            require(Float.isFinite(left)&&Float.isFinite(right),"Nonfinite feature tone");
            StyleKit.Result r=NineLayerCraftPatternEngine.generate(photo,W,H,o);
            require(r.layers.size()==9,"Physical L1..L9 missing");
            int loops=0;
            for(int layer=0;layer<9;layer++){
                StyleKit.Layer l=r.layers.get(layer);
                require(!l.loops.isEmpty(),"No cut path on L"+(layer+1));
                require(l.lines.isEmpty(),"Blade contours replaced with pen paths");
                require(l.color==o.palette[layer],"Family palette lost on L"+(layer+1));
                for(float[] p:l.loops){
                    require(p.length>=6 && (p.length&1)==0,"Invalid cut path");
                    for(float v:p)require(Float.isFinite(v),"Nonfinite cut coordinate");
                }
                loops+=l.loops.size();
            }
            require(signatures.add(geometryHash(r)),"Duplicate camouflage geometry");
            require(r.summary.contains("9 real physical vinyl layers"),"Physical summary missing");
            String svg=StyleKit.toSvg(r,true);
            require(svg.contains("fill-rule=\"evenodd\""),"Vector loops not fillable");
            require(svg.contains("craft-"+pattern+"-layer-9"),"Last layer missing from SVG");
            // Low-contrast images must remain able to generate nine real sheets.
            o.draft=true;
            StyleKit.Result flatResult=NineLayerCraftPatternEngine.generate(flat,W,H,o);
            for(StyleKit.Layer l:flatResult.layers)require(!l.loops.isEmpty(),"Flat image lost a sheet");
            System.out.println("PASS RC167 "+CamouflagePalette.name(style)+
                " high-detail 11-inch 9-layer cut paths, "+loops+" contours; pitch "+pitch+"mm");
        }
        NineLayerCraftPatternEngine.Options opts=new NineLayerCraftPatternEngine.Options();
        opts.pattern=NineLayerCraftPatternEngine.CAMOUFLAGE;
        opts.allowed=crop;opts.widthMm=279.4f;opts.detail=88;opts.layers=2;opts.draft=true;
        require(NineLayerCraftPatternEngine.generate(photo,W,H,opts).layers.size()==2,
            "Two-layer project compatibility lost");
        // The cancellation checkpoint must let a slider supersede a stale render.
        Thread.currentThread().interrupt();
        boolean cancelled=false;
        try{NineLayerCraftPatternEngine.generate(photo,W,H,opts);}
        catch(CancellationException expected){cancelled=true;}
        finally{Thread.interrupted();}
        require(cancelled,"Cancelled render continued");
        for(int style:styles){
            int d=PhotoStyleDefaults.forNewStyle(style,null,.254f,279.4f).detail;
            require(d>=88,"Family detail defaults are too coarse: "+style+"="+d);
        }
        System.out.println("PASS RC167 four distinct adaptive camouflage styles, draft bounds, cancellation and defaults");
    }
}

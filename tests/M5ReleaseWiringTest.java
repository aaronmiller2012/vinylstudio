package com.halftonestudio;

/**
 * M5 release gate: the PRODUCTION entry points (VinylStyleSuite.generate, SuiteCutStage,
 * VinylPaletteMapping) carry each adopted M5 engine change. Every engine change also has its
 * own gate; this one fails if a later edit silently unwires one of them from the app route.
 * Synthetic fixtures (M5Fixtures) keep it fast and licence-free.
 */
public final class M5ReleaseWiringTest {
    static int failures=0;
    static void check(boolean ok,String what){System.out.println((ok?"ok   ":"FAIL ")+what);if(!ok)failures++;}

    static int[] fixture(String name,int maxW,int[] dims){
        int[] d=new int[2];int[] px=M5Fixtures.render(name,d);
        float s=Math.min(1f,maxW/(float)d[0]);int w=Math.max(2,Math.round(d[0]*s)),h=Math.max(2,Math.round(d[1]*s));
        int[] o=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)o[y*w+x]=px[Math.min(d[1]-1,(int)(y/s))*d[0]+Math.min(d[0]-1,(int)(x/s))];
        dims[0]=w;dims[1]=h;return o;
    }
    static M4ArtBench.Photo photo(String name,int maxW){int[] d=new int[2];int[] px=fixture(name,maxW,d);return new M4ArtBench.Photo(name,px,d[0],d[1]);}
    static int loops(StyleKit.Result r){int n=0;for(StyleKit.Layer l:r.layers)n+=l.loops.size();return n;}
    static int holes(StyleKit.Result r){return M4ArtBench.topology(r)[1];}

    public static void main(String[] a)throws Exception{
        try{VectorBooleanEngine.install(new JtsVectorBooleanBackend());}catch(Throwable t){throw new AssertionError("JTS backend required",t);}
        M4ArtBench.Photo face=photo("syn-glasses",240),dark=photo("syn-deep-shadow",240);

        // Linocut relief polarity: shadows are carved solid blocks, so the ink sheet has holes (gouges).
        StyleKit.Result lino=VinylStyleSuite.generate(dark.argb,dark.w,dark.h,M4ArtBench.suiteConfig(VinylStyleSuite.LINOCUT,dark,152.4f,.8f,.5f));
        check(holes(lino)>0,"Linocut production route carves gouges into solid shadow blocks (holes="+holes(lino)+")");
        check(StyleCutGeometryPreflight.inspect(lino,.8f,StyleCutGeometryPreflight.exportGapMm(.5f,.8f),100000).isEmpty(),"Linocut production output has no cut findings");

        // Crochet filet redesign: cut-clean by construction, so the cut stage repairs nothing.
        StyleKit.Result cro=VinylStyleSuite.generate(face.argb,face.w,face.h,M4ArtBench.suiteConfig(VinylStyleSuite.CROCHET,face,101.6f,.8f,.5f));
        check(!SuiteCutStage.prepare(cro,VinylStyleSuite.CROCHET,.8f,.5f).repair.any(),"Crochet production output needs no cut repair");

        // Diagonal size-aware pitch: above 6 in the ribbon count grows with size (560 held it flat).
        int d6=loops(VinylStyleSuite.generate(face.argb,face.w,face.h,M4ArtBench.suiteConfig(VinylStyleSuite.PRISMATIC_DIAGONAL,face,152.4f,.8f,.5f)));
        int d11=loops(VinylStyleSuite.generate(face.argb,face.w,face.h,M4ArtBench.suiteConfig(VinylStyleSuite.PRISMATIC_DIAGONAL,face,279.4f,.8f,.5f)));
        System.out.println("     Diagonal loops 6 in "+d6+", 11 in "+d11);
        check(d11>=d6*1.25f,"Prismatic Diagonal production route uses size-aware pitch above 6 in");

        // Confetti perceptual base: with photo pixels the solid base is not simply the darkest swatch.
        int[] eight={0xff101010,0xff3a2a20,0xff6b4530,0xff9a6a50,0xffc49478,0xffe2bca0,0xfff1d6c0,0xfffaf2ea};
        int[] withPhoto=VinylPaletteMapping.forStyle(VinylStyleSuite.CONFETTI,6,VinylEngine.DEFAULT_SURFACE,eight,false,true,face.argb);
        int[] saved=VinylPaletteMapping.forStyle(VinylStyleSuite.CONFETTI,6,VinylEngine.DEFAULT_SURFACE,eight,false,true,null);
        check(withPhoto[0]!=0xff101010,"Confetti production mapping chooses a perceptual base, not the darkest swatch");
        check(java.util.Arrays.equals(saved,VinylPaletteMapping.forStyle(VinylStyleSuite.CONFETTI,6,VinylEngine.DEFAULT_SURFACE,eight,false,true)),"without photo pixels (saved palettes) the 560 mapping is unchanged");

        if(failures>0)throw new AssertionError(failures+" M5 release wiring failures");
        System.out.println("PASS M5 release wiring");
    }
}

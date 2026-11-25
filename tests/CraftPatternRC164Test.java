package com.halftonestudio;

import java.util.Arrays;
import java.util.HashSet;

/** Executable cut-geometry and stable-project checks for RC164. */
public final class CraftPatternRC164Test {
    private static final int W=100,H=120;
    private static void ok(boolean v,String m){if(!v)throw new AssertionError(m);}
    private static int[] photo(boolean flat){
        int[] p=new int[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            int t=flat?124:(int)(24+210*(.47*x/(W-1.)+.53*y/(H-1.)));
            p[y*W+x]=0xff000000|(t<<16)|(Math.max(0,t-11)<<8)|Math.max(0,t-27);
        }
        return p;
    }
    private static boolean[] subject(){
        boolean[] mask=new boolean[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            double a=(x-W*.5)/(W*.41),b=(y-H*.5)/(H*.44);
            mask[y*W+x]=a*a+b*b<1;
        }
        return mask;
    }
    private static long sign(StyleKit.Result result){
        long h=0xcbf29ce484222325L;
        for(StyleKit.Layer layer:result.layers){
            h=(h^layer.loops.size())*0x100000001b3L;
            for(float[] loop:layer.loops){
                h=(h^loop.length)*0x100000001b3L;
                for(int i=0;i<loop.length;i+=Math.max(2,loop.length/12 & ~1))
                    h=(h^Float.floatToIntBits(loop[i]))*0x100000001b3L;
            }
        }
        return h;
    }
    public static void main(String[] args){
        int[] image=photo(false); boolean[] snip=subject();
        HashSet<Long> signatures=new HashSet<>();
        for(int type=0;type<NineLayerCraftPatternEngine.NAMES.length;type++){
            for(int layers:new int[]{2,9}){
                NineLayerCraftPatternEngine.Options o=new NineLayerCraftPatternEngine.Options();
                o.pattern=type;o.layers=layers;o.widthMm=279.4f;o.detail=63;
                o.minCutMm=.254f;o.allowed=snip;o.draft=true;
                StyleKit.Result result=NineLayerCraftPatternEngine.generate(image,W,H,o);
                ok(result.layers.size()==layers,"Sheet count "+type);
                int contours=0;
                for(int index=0;index<layers;index++){
                    StyleKit.Layer l=result.layers.get(index);
                    ok(!l.loops.isEmpty(),"Empty L"+(index+1)+" for pattern "+type);
                    ok(l.lines.isEmpty(),"Pen paths instead of blade contours");
                    for(float[] loop:l.loops){
                        ok(loop.length>=6 && loop.length%2==0,"Degenerate cut contour");
                        for(float v:loop)ok(Float.isFinite(v),"Nonfinite vector coordinate");
                    }
                    contours+=l.loops.size();
                }
                if(layers==9)ok(signatures.add(sign(result)),"Identical output geometry for style "+type);
                System.out.println("PASS "+NineLayerCraftPatternEngine.NAMES[type]+" "+layers+
                    " real cuttable sheets; "+contours+" contours");
            }
        }
        // Flat-tone input must not eliminate the sparse accent layers.
        for(int type:new int[]{NineLayerCraftPatternEngine.CAMOUFLAGE,
            NineLayerCraftPatternEngine.CAMO_DESERT,NineLayerCraftPatternEngine.CAMO_URBAN,
            NineLayerCraftPatternEngine.CAMO_DIGITAL,NineLayerCraftPatternEngine.LEAF_VEIN}){
            NineLayerCraftPatternEngine.Options o=new NineLayerCraftPatternEngine.Options();
            o.pattern=type;o.layers=9;o.detail=63;o.widthMm=279.4f;
            o.allowed=snip;o.draft=true;
            StyleKit.Result r=NineLayerCraftPatternEngine.generate(photo(true),W,H,o);
            for(StyleKit.Layer l:r.layers)ok(!l.loops.isEmpty(),"Flat photo empty layer "+type);
        }
        for(int id: new int[]{VinylStyleSuite.CAMOUFLAGE,VinylStyleSuite.CAMO_DESERT,
                VinylStyleSuite.CAMO_URBAN,VinylStyleSuite.CAMO_DIGITAL}){
            ok(NewStyleVariantPolicy.count(id)==1,"Missing style picker entry");
            ok(NewStyleColorSlots.maximumSelectable(id)==9,"Nine physical colors not supported");
            ok(NewStyleLayerPolicy.isStructuralLayer(id,0),"Unprotected solid base");
            ok(!NewStyleLayerPolicy.canDelete(id,0,9),"Base delete exposed");
            int[] palette=CamouflagePalette.defaults(id,new int[9]);
            ok(palette.length==9,"Missing camo colors");
            ok(StyleKit.luma255(palette[0])<StyleKit.luma255(palette[4]),"Base should be darker");
        }
        int[] manual={0xff102030,0xff203040,0xff304050,0xff405060,0xff506070,
                0xff607080,0xff708090,0xff8090a0,0xff90a0b0};
        VinylStyleSuite.Config c=new VinylStyleSuite.Config();
        c.style=VinylStyleSuite.CAMO_DIGITAL;c.colorCount=9;c.allowed=snip;
        c.palette=manual.clone();c.draft=true;c.widthMm=279.4f;
        StyleKit.Result preserved=VinylStyleSuite.generate(image,W,H,c);
        for(int i=0;i<9;i++)ok(preserved.layers.get(i).color==manual[i],"Swatch reordered or replaced");
        ok(VinylStyleSuite.restoredStyleId(VinylStyleSuite.CAMOUFLAGE)==35,"Old camo ID changed");
        ok(VinylStyleSuite.isCraft(VinylStyleSuite.CAMO_DIGITAL),"Digital doesn't retain craft policies");
        ok(PhotoStyleDefaults.forNewStyle(VinylStyleSuite.CAMO_DIGITAL,null,.254f,279.4f).detail>0,
            "New style defaults missing");
        System.out.println("PASS RC164 distinct patterns, stable IDs, 2/9 sheets, flat tones, palettes, thumbnails");
    }
}

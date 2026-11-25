package com.halftonestudio;

import java.util.Arrays;
import java.util.HashSet;

/** RC165: actual family colors must reach generated, cuttable layers and survive toggles. */
public final class CamoPaletteDefaultsRC165Test {
    private static void ok(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
    private static final int W=94,H=114;
    private static int[] photo(){int[] image=new int[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            int k=Math.min(245,Math.max(15,(int)(18+210*(x*.37/(W-1)+y*.63/(H-1)))));
            image[y*W+x]=0xff000000 | (k<<16) | ((k-5)<<8) | (k-9);
        }return image;
    }
    private static boolean[] mask(){boolean[] a=new boolean[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            double u=(x-W*.5)/(W*.42),v=(y-H*.5)/(H*.42);a[y*W+x]=u*u+v*v<1;
        }return a;
    }
    public static void main(String[] args){
        int[] all={VinylStyleSuite.CAMOUFLAGE,VinylStyleSuite.CAMO_DESERT,
            VinylStyleSuite.CAMO_URBAN,VinylStyleSuite.CAMO_DIGITAL};
        HashSet<String> palettes=new HashSet<>();
        for(int id:all){
            int[] colors=CamouflagePalette.defaults(id,null);
            ok(colors.length==9,"Nine family colors required for "+id);
            ok(palettes.add(Arrays.toString(colors)),"Camouflage families must be distinct");
            ok(CamouflagePalette.useFamilyDefault(id,false,false,false),"First selection is not fixed");
            ok(CamouflagePalette.useFamilyDefault(id,true,false,false),"Legacy RC164 auto mode not upgraded");
            ok(!CamouflagePalette.useFamilyDefault(id,true,false,true),"Explicit photo mode was overridden");
            ok(!CamouflagePalette.useFamilyDefault(id,true,true,false),"Manual colors must be preserved");
            int[] clone=CamouflagePalette.defaults(id,new int[9]);clone[0]=0xffabcdef;
            ok(CamouflagePalette.defaults(id,null)[0]==colors[0],"Family preset mutated");
            for(int count:new int[]{2,4,9}){
                VinylStyleSuite.Config config=new VinylStyleSuite.Config();
                config.style=id;config.detail=72;config.colorCount=count;config.widthMm=279.4f;
                config.draft=true;config.minimumMm=.254f;config.allowed=mask();
                config.palette=NewStyleColorSlots.fixedPalette(id,0,count,colors);
                StyleKit.Result result=VinylStyleSuite.generate(photo(),W,H,config);
                ok(result.layers.size()==count,"Wrong physical sheet count "+count);
                for(int i=0;i<count;i++){
                    StyleKit.Layer l=result.layers.get(i);
                    ok(l.color==colors[i],"Auto photo colors replaced L"+(i+1)+" in "+id);
                    ok(!l.loops.isEmpty(),"Blank blade layer "+(i+1)+" in "+id);
                }
                System.out.println("PASS "+CamouflagePalette.name(id)+" "+count+" physical family colors and cut sheets");
            }
        }
        ok(!CamouflagePalette.isCamouflage(VinylStyleSuite.CROCHET),"Craft style mistaken for camo");
        ok(!CamouflagePalette.useFamilyDefault(VinylStyleSuite.LEAF_VEIN,false,false,false),"Leaf defaults changed");
        System.out.println("PASS RC165 all four curated families, explicit photo/manual preservation, 2-9 blade sheets");
    }
}

package com.halftonestudio;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Behavioral guard for style-specific 1–8 color organization and photo identity. */
public final class RC100PaletteOrganizationTest {
    private static void ok(boolean yes,String reason){if(!yes)throw new AssertionError(reason);}
    private static boolean in(int[] colors,int c){for(int x:colors)if(x==c)return true;return false;}
    public static void main(String[] args){
        ok(VinylPalettes.NAMES.length>=19&&"Urban Concrete".equals(VinylPalettes.NAMES[18]),"standard IDs changed");
        ok(ColorWorkspacePalette.TESS_NAMES.length==7,"creative IDs changed");
        ok(PerceptualVinylColor.distance(0xff808080,0xff808080)<1e-12,"gray delta");
        ok(PerceptualVinylColor.distance(0xff000000,0xffffffff)>.8,"black and white not perceptually distinct");
        ok(Math.abs(PerceptualVinylColor.distance(0xff224466,0xffbb8877)-
            PerceptualVinylColor.distance(0xffbb8877,0xff224466))<1e-12,"delta symmetry");
        int[] photoRed={0xff771a24,0xffcb5647,0xffeaa16b,0xff22232b,0xffecce9b,0xffb55948,0xffce8b73,0xffeed7bc};
        int[] photoBlue={0xff101e31,0xff234e70,0xff3579a2,0xff80c3d1,0xffccf0ea,0xff244b77,0xff4e7ca7,0xffecece2};
        int[] red=PaletteRecommendations.choose(PaletteStylePolicy.PHOTO_CELLS,photoRed,0xfffaf9f6,5,6);
        int[] blue=PaletteRecommendations.choose(PaletteStylePolicy.PHOTO_CELLS,photoBlue,0xfffaf9f6,5,6);
        ok(Arrays.equals(red,PaletteRecommendations.choose(PaletteStylePolicy.PHOTO_CELLS,photoRed,0xfffaf9f6,5,6)),
            "recommendations not deterministic");
        ok(!Arrays.equals(red,blue),"recommended cards ignore photograph hues");
        for(int family:new int[]{PaletteStylePolicy.PHOTO_CELLS,PaletteStylePolicy.LINE_RIBBONS,
                PaletteStylePolicy.STENCIL,PaletteStylePolicy.GLASS,PaletteStylePolicy.TOPOGRAPHIC,PaletteStylePolicy.TEXT}) {
            int[] ids=PaletteRecommendations.choose(family,photoRed,0xff18191e,6,6);
            Set<Integer> seen=new HashSet<>();
            for(int id:ids){
                ok(id>=0&&id<PaletteRecommendations.CATALOG_SIZE&&seen.add(id),"duplicate or invalid recommended ID");
                ok(PaletteRecommendations.colors(id).length==8,"catalog incomplete");
                ok(!PaletteRecommendations.key(id).isEmpty()&&!PaletteRecommendations.name(id).isEmpty(),"catalog label missing");
            }
        }
        int[] original=VinylPalettes.get(0,8);
        for(int paper:new int[]{0xfffaf9f6,0xfff4e7ce,0xff18191e}){
            for(int count=2;count<=8;count++){
                for(int family:new int[]{PaletteStylePolicy.PHOTO_CELLS,PaletteStylePolicy.LINE_RIBBONS,
                        PaletteStylePolicy.STENCIL,PaletteStylePolicy.GLASS,PaletteStylePolicy.TOPOGRAPHIC,PaletteStylePolicy.TEXT}){
                    int[] mapped=PaletteStylePolicy.arrange(family,original,count,paper);
                    ok(mapped.length==8,"lost parked swatches");
                    for(int c:original)ok(in(mapped,c),"invented/forgotten preset RGB in family "+family);
                    ok(new HashSet<Integer>(){{for(int c:mapped)add(c);}}.size()==8,"duplicated source preset RGB");
                    if(family==PaletteStylePolicy.GLASS||family==PaletteStylePolicy.TEXT)
                        ok(mapped[0]==original[0],"L1 structural sheet moved");
                    if(family==PaletteStylePolicy.TOPOGRAPHIC)
                        for(int j=1;j<count;j++)ok(ColorWorkspacePalette.lightness(mapped[j-1])>=
                            ColorWorkspacePalette.lightness(mapped[j]),"topographic tonal order regressed");
                }
                int[] topReverse=VinylPaletteMapping.forStyle(VinylStyleSuite.TOPOGRAPHIC,count,paper,original,true,false);
                for(int j=1;j<count;j++)ok(ColorWorkspacePalette.lightness(topReverse[j-1])<=
                    ColorWorkspacePalette.lightness(topReverse[j]),"topographic reversal not effective");
                int[] textReverse=VinylPaletteMapping.forStyle(VinylStyleSuite.TEXT,count,paper,original,true,false);
                ok(textReverse[0]==original[0],"text silhouette base moved on reverse");
                int[] glass=VinylPaletteMapping.forStyle(VinylStyleSuite.GLASS,Math.max(count,3),paper,original,true,false);
                ok(glass[0]==original[0],"glass lead moved on reverse");
            }
        }
        int[] genuineGray=new int[160*160];
        Arrays.fill(genuineGray,0xff808080);
        int[] gray=StipplePhotoPalette.derive(genuineGray,160,160,8,0xfffaf9f6);
        for(int c:gray)ok(c==0xff808080,"genuine gray photo became invented color: "+Integer.toHexString(c));
        int[] distinct=VinylPalettes.get(2,8);
        int[] filled=new int[160*160];
        for(int i=0;i<filled.length;i++)filled[i]=distinct[(i/20)%8];
        int[] before=filled.clone();
        int[] extracted=StipplePhotoPalette.derive(filled,160,160,8,0xfffaf9f6);
        ok(Arrays.equals(before,filled),"photo extraction modified source pixels");
        for(int c:extracted)ok((c>>>24)==255,"non-opaque photo swatch");
        int[] saved={0xff112233,0xff334455,0xff556677,0xff778899,0xff99aabb,0xffaabbcc,0xffccddee,0xffeef0f1};
        ok(Arrays.equals(saved,PaletteOrientation.orient(saved,false)),"saved palette rewritten");
        ok(Arrays.equals(original,VinylPalettes.get(0,8)),"catalog mutated in recommendation");
        System.out.println("PASS RC100: 26 stable presets, perceptual ranking, 6 style families, stable structural slots, gray photo, tonal reversal, no color mutation");
    }
}

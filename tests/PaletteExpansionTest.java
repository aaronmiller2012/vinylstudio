package com.halftonestudio;
import java.util.*;
public final class PaletteExpansionTest {
    static void ok(boolean valid,String msg){if(!valid)throw new AssertionError(msg);}
    public static void main(String[] args){
        // M4 appended ten schemes (29 presets + CMYK); earlier IDs below are unchanged.
        ok(VinylPalettes.NAMES.length==29,"29 non-CMYK presets + 1 CMYK = 30 total tiles");
        ok(new HashSet<>(Arrays.asList(VinylPalettes.NAMES)).size()==29,"Palette names must be unique");
        String[] extras={"Signal Primary","Jewel Cabinet","Soft Sorbet","Copper Patina","Acid Citrus","Winter Cardinal","Urban Concrete"};
        for(int i=0;i<extras.length;i++){
            int id=12+i;ok(VinylPalettes.NAMES[id].equals(extras[i]),"New palette position "+i);
            int[] full=VinylPalettes.get(id,8);
            ok(new HashSet<Integer>(){{for(int c:full)add(c);}}.size()==8,"Every preset must have 8 unique colors");
            for(int n=2;n<=8;n++){
                int[] actual=ColorWorkspacePalette.select(full,n);
                ok(actual.length==n,"Palette selected count");
                Set<Integer> unique=new HashSet<>();for(int color:actual)unique.add(color);
                ok(unique.size()==n,"No duplicated colors on 2–8 layers");
                ColorWorkspacePalette.sortForSurface(actual,n,0xfffaf9f6);
                for(int j=1;j<actual.length;j++)ok(ColorWorkspacePalette.lightness(actual[j])<=ColorWorkspacePalette.lightness(actual[j-1])+1e-8,"Lightest on the bottom, darkest top");
            }
        }
        // Exact IDs for all older palettes; old saved project paletteId values remain stable.
        String[] old={"Portrait Vinyl","Retro Sunset","Ocean","Pop Art","Forest","Neon Night","Earth & Rose","Monochrome","Desert Clay","Arctic Blue","Vintage Print","Wildflower"};
        for(int i=0;i<old.length;i++)ok(VinylPalettes.NAMES[i].equals(old[i]),"Legacy ID changed");
        System.out.println("PASS: 30 tiles (CMYK + 29 palettes), seven additions, 8 unique colors, 2–8 layers, stable old IDs");
    }
}

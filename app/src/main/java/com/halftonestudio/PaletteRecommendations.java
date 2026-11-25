package com.halftonestudio;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * Recommend existing palette IDs, never manufacture colors. This helper only
 * changes visual gallery order; applying a choice remains a deliberate action.
 * No photo data is stored or sent anywhere.
 */
public final class PaletteRecommendations {
    private PaletteRecommendations() { }
    /** Presets then creative palettes. Derived, so appended presets are listed (558 hard-coded 26). */
    public static final int CATALOG_SIZE=VinylPalettes.NAMES.length+ColorWorkspacePalette.TESS_NAMES.length;

    public static String key(int index) {
        if(index<0||index>=CATALOG_SIZE)throw new IllegalArgumentException("Palette index");
        return index<VinylPalettes.NAMES.length?"preset:"+index:
            "creative:"+(index-VinylPalettes.NAMES.length);
    }
    public static String name(int index) {
        if(index<0||index>=CATALOG_SIZE)throw new IllegalArgumentException("Palette index");
        return index<VinylPalettes.NAMES.length?VinylPalettes.NAMES[index]:
            ColorWorkspacePalette.TESS_NAMES[index-VinylPalettes.NAMES.length];
    }
    public static int[] colors(int index) {
        if(index<0||index>=CATALOG_SIZE)throw new IllegalArgumentException("Palette index");
        return index<VinylPalettes.NAMES.length?VinylPalettes.get(index,8):
            ColorWorkspacePalette.tessPalette(index-VinylPalettes.NAMES.length);
    }

    public static int[] choose(int family,int[] photo,int surface,int count,int maximum) {
        if(count<2||count>8||maximum<1||maximum>CATALOG_SIZE)
            throw new IllegalArgumentException("Invalid palette recommendation size");
        ArrayList<Integer> order=new ArrayList<>();
        final double[] scores=new double[CATALOG_SIZE];
        for(int index=0;index<CATALOG_SIZE;index++) {
            order.add(index);
            int[] full=colors(index);
            int[] arranged=family==PaletteStylePolicy.CMYK?full:
                PaletteStylePolicy.arrange(family,full,count,surface);
            // Compare only physical sheets in use, not dimmed parked colors.
            double photoFit=0d;
            if(photo!=null&&photo.length>0) {
                for(int target:photo) {
                    double nearest=Double.POSITIVE_INFINITY;
                    for(int j=0;j<count;j++)
                        nearest=Math.min(nearest,PerceptualVinylColor.distance(target,arranged[j]));
                    photoFit+=nearest;
                }
                photoFit/=photo.length;
            }
            double span=0,distinction=0,contrast=0;
            double min=100,max=0;
            int from=(family==PaletteStylePolicy.GLASS||family==PaletteStylePolicy.TEXT||
                      family==PaletteStylePolicy.CONFETTI_STACK||family==PaletteStylePolicy.PRISMATIC_CELLS)?1:0;
            for(int j=from;j<count;j++) {
                double light=ColorWorkspacePalette.lightness(arranged[j]);
                min=Math.min(min,light);max=Math.max(max,light);
                contrast+=PerceptualVinylColor.distance(arranged[j],surface);
                if(j>from)distinction+=PerceptualVinylColor.distance(arranged[j],arranged[j-1]);
            }
            span=(max-min)/100d;
            contrast/=Math.max(1,count-from);
            distinction/=Math.max(1,count-from-1);
            double value=-photoFit*3.8d+contrast*.30d+distinction*.32d+span*.12d;
            if(family==PaletteStylePolicy.LINE_RIBBONS)value+=contrast*.58d;
            if(family==PaletteStylePolicy.TOPOGRAPHIC||family==PaletteStylePolicy.DARK_OVER_LIGHT||family==PaletteStylePolicy.STENCIL||
               family==PaletteStylePolicy.CONFETTI_STACK||family==PaletteStylePolicy.PRISMATIC_CELLS||
               family==PaletteStylePolicy.CHAINMAIL_TONES)
                value+=span*.48d;
            if(family==PaletteStylePolicy.PHOTO_CELLS)value-=photoFit*.40d;
            if(family==PaletteStylePolicy.TEXT)value+=contrast*.25d;
            scores[index]=value;
        }
        order.sort(Comparator.<Integer>comparingDouble(i->scores[i]).reversed()
            .thenComparingInt(i->i));
        int[] chosen=new int[maximum];
        for(int i=0;i<maximum;i++)chosen[i]=order.get(i);
        return chosen;
    }
}

package com.halftonestudio;

import java.util.Arrays;

/** RC124 physical palette mapping and layer removal, no Android runtime required. */
public final class RC124PaletteLayersHostTest {
    private static void yes(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static final int[] SAMPLE={0xff132211,0xfff7c44e,0xff3d89c4,0xffeb604c,
        0xff37b591,0xff56408c,0xffdfab91,0xff181818};
    public static void main(String[] args){
        for(int style:new int[]{VinylStyleSuite.GLASS,VinylStyleSuite.TOPOGRAPHIC,
                VinylStyleSuite.TESSELLATION_MOTIF,VinylStyleSuite.LOW_POLY,VinylStyleSuite.TEXT}){
            for(int active=style==VinylStyleSuite.GLASS?3:2;active<=8;active++){
                int[] forward=VinylPaletteMapping.forStyle(style,active,0xfff8f8f8,SAMPLE,false,false);
                int[] reversed=VinylPaletteMapping.forStyle(style,active,0xfff8f8f8,SAMPLE,true,false);
                // Tessellation Motif now has the same permanent structural L1 as
                // Glass and Text; Reverse must not move that foundation.
                int first=style==VinylStyleSuite.GLASS||style==VinylStyleSuite.TEXT||
                    style==VinylStyleSuite.TESSELLATION_MOTIF?1:0;
                for(int i=0;i<active;i++){
                    yes(reversed[i]==forward[i<first?i:active-1-(i-first)],
                        "Reverse physical sheets style="+style+" count="+active+" index="+i);
                }
                for(int i=active;i<8;i++)yes(reversed[i]==forward[i],"Parked swatch moved");
            }
        }
        int[] colors=SAMPLE.clone();
        VinylLayerDelete.remove(colors,2,6);
        yes(colors[0]==SAMPLE[0]&&colors[1]==SAMPLE[1]&&colors[2]==SAMPLE[3]&&
            colors[3]==SAMPLE[4]&&colors[4]==SAMPLE[5]&&colors[5]==SAMPLE[2],"Color removal mapping");
        float[] widths={1,2,3,4,5,6,7,8};
        VinylLayerDelete.remove(widths,2,6);
        yes(widths[2]==4f&&widths[4]==6f&&widths[5]==3f,"Float-slot removal");
        boolean[] enabled={true,false,true,false,true,false,true,true};
        VinylLayerDelete.remove(enabled,1,5);
        yes(enabled[1]&& !enabled[2]&&enabled[3]&&!enabled[4],"Boolean-slot removal");
        int[] oldOrder={4,0,2,1,3,5,6,7};
        int[] newer=VinylLayerDelete.removeOrder(oldOrder,2,5);
        yes(Arrays.equals(Arrays.copyOf(newer,4),new int[]{3,0,1,2}),"Stipple order remap");
        System.out.println("PASS RC124 palette reversal, anchored lead, parking, physical deletion and order");
    }
}

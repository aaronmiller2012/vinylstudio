package com.halftonestudio;

import java.util.Arrays;
/** Pure mapping rules: orientation NEVER depends on mounting surface. */
public final class PaletteOrientation {
    private PaletteOrientation() {}
    public static int[] orient(int[] colors,boolean reverse) {
        if(colors==null)throw new IllegalArgumentException("Missing colors");
        int[] out=colors.clone();
        if(reverse)for(int i=0,j=out.length-1;i<j;i++,j--){int c=out[i];out[i]=out[j];out[j]=c;}
        return out;
    }
    /** Stained Glass: the lead sheet at L1 is structural, not a pane color.
     * Reverse the seven panes without moving the lead or modifying the source. */
    public static int[] orientKeepingLead(int[] colors,boolean reverse) {
        if(colors==null||colors.length!=8)throw new IllegalArgumentException("Eight glass swatches required");
        int[] out=colors.clone();
        if(reverse)for(int i=1,j=7;i<j;i++,j--){int c=out[i];out[i]=out[j];out[j]=c;}
        return out;
    }
    /** Preserve the physical Y and K anchors and their real colors; reverse only interior CMY accents.
     * These are channel identities, not ink blending or reversed material stack. */
    public static int[] orientCmyk(int[] physical,boolean reverse) {
        int[] out=physical.clone();
        if(reverse)for(int i=1,j=out.length-2;i<j;i++,j--){int c=out[i];out[i]=out[j];out[j]=c;}
        return out;
    }
    /** orientCmyk over only the {@code active} physical layers; parked slots never move.
     *  A saved palette is an 8-slot array whose K anchor is at index active-1, not 7. */
    public static int[] orientCmykActive(int[] slots,int active,boolean reverse){
        if(slots==null||active<2||active>slots.length)throw new IllegalArgumentException("Invalid CMYK layer count");
        int[] out=slots.clone();
        int[] head=orientCmyk(java.util.Arrays.copyOf(slots,active),reverse);
        System.arraycopy(head,0,out,0,active);
        return out;
    }
    /** Reverse committed physical sheet slots AFTER a palette's style-specific
     * sorting. This is an involution: toggling twice restores the original
     * order, including parked swatches (which are never moved). */
    public static int[] reverseActive(int[] colors,int count,boolean reverse,boolean keepLead){
        if(colors==null||count<1||count>colors.length)throw new IllegalArgumentException("Invalid swatch count");
        int[] out=colors.clone();
        if(reverse)for(int i=keepLead?1:0,j=count-1;i<j;i++,j--){
            int tmp=out[i];out[i]=out[j];out[j]=tmp;
        }
        return out;
    }
    /** Preserve every source color, including repeated genuine photo colors. */
    public static int[] arrange(int[] full,int count,boolean reverse){
        int[] ordered=orient(full,reverse);
        int[] active=ColorWorkspacePalette.select(ordered,count);
        int[] out=new int[8];System.arraycopy(active,0,out,0,count);
        int k=count;
        for(int i=0;i<8&&k<8;i++){
            // An index can appear in the active sample; duplicates remain allowed.
            boolean selected=false;
            for(int a=0;a<count;a++)if(Math.round(a*7f/(count-1))==i){selected=true;break;}
            if(!selected)out[k++]=ordered[i];
        }
        return out;
    }
    public static int[] select(int[] full,int count,boolean reverse) {
        if(full==null||full.length!=8||count<2||count>8)throw new IllegalArgumentException("2–8 colors");
        return ColorWorkspacePalette.select(orient(full,reverse),count);
    }
}

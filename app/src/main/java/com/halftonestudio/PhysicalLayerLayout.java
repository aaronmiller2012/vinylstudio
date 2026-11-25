package com.halftonestudio;
/** Stable numbering for the physical cut stack, independent of render-engine slots. */
public final class PhysicalLayerLayout {
    private PhysicalLayerLayout(){}
    public static int physicalNumber(int detailIndex,boolean hasBase){
        if(detailIndex<0)throw new IllegalArgumentException("detailIndex");
        return detailIndex+(hasBase?2:1);
    }
    public static int physicalCount(int detailColors,boolean hasBase){
        if(detailColors<0||detailColors>8)throw new IllegalArgumentException("detailColors");
        return detailColors+(hasBase?1:0);
    }
    public static int detailColorsForPhysical(int physical,boolean hasBase){
        if(physical<1||physical>9)throw new IllegalArgumentException("physical");
        if(hasBase&&physical==1)return 0;
        int n=physical-(hasBase?1:0);
        if(n<2||n>8)throw new IllegalArgumentException("Renderer requires 2–8 detail colors");
        return n;
    }
}

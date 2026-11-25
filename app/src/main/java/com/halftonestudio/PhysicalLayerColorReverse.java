package com.halftonestudio;

/**
 * Reverses the displayed physical vinyl stack while preserving stable source
 * slot IDs. All data is copied; callers apply the returned overrides once.
 */
public final class PhysicalLayerColorReverse {
    private PhysicalLayerColorReverse() { }
    public static int[] reverse(int[] overrides,int[] displayed,int[] sourceOrder,int hue){
        if(overrides==null||displayed==null||sourceOrder==null||
                displayed.length!=sourceOrder.length||displayed.length>overrides.length)
            throw new IllegalArgumentException("Invalid physical palette mapping");
        int[] out=overrides.clone();boolean[] seen=new boolean[overrides.length];
        for(int i=0;i<displayed.length;i++){
            int source=sourceOrder[i];
            if(source<0||source>=out.length||seen[source])
                throw new IllegalArgumentException("Invalid physical source order");
            seen[source]=true;
            out[source]=PaletteHueShift.rotateOne(
                displayed[displayed.length-1-i],-hue);
        }
        return out;
    }
}

package com.halftonestudio;

import java.util.ArrayList;
import java.util.List;

/** Physical vinyl application order: Yellow, Cyan, Magenta, Black.
 * Channel IDs and all per-layer properties travel together when restacked.
 * This is intentionally opt-in for loaded legacy projects: old serialized
 * order cannot be silently changed without affecting their assembled image.
 */
public final class CmykStackOrder {
    private static final int[] BOTTOM_TO_TOP={2,0,1,3};
    private CmykStackOrder() {}
    public static boolean needsCorrection(VinylEngine.Settings settings){
        if(settings==null||settings.mode!=VinylEngine.CMYK||settings.layers.size()!=4)return false;
        boolean[] seen=new boolean[4];
        boolean mismatch=false;
        for(int i=0;i<4;i++){
            int channel=settings.layers.get(i).cmykChannel;
            if(channel<0||channel>=4||seen[channel])return false;
            seen[channel]=true;
            if(channel!=BOTTOM_TO_TOP[i])mismatch=true;
        }
        return mismatch;
    }
    /** Returns false when no safe transformation exists or already canonical. */
    public static boolean normalize(VinylEngine.Settings settings){
        if(!needsCorrection(settings))return false;
        List<VinylEngine.Layer> reordered=new ArrayList<>(4);
        for(int channel:BOTTOM_TO_TOP){
            for(VinylEngine.Layer layer:settings.layers){
                if(layer.cmykChannel==channel){reordered.add(layer);break;}
            }
        }
        if(reordered.size()!=4)return false;
        settings.layers.clear();settings.layers.addAll(reordered);
        return true;
    }
}

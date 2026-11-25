package com.halftonestudio;

/** Physical and complexity cap for interactive layered portraits. No Android APIs. */
public final class LayeredCellReleaseProfile {
    private LayeredCellReleaseProfile(){}
    /** Never silently exceed the cell-count or minimum usable tile size of the vector engine. */
    public static int safeColumns(int requested,int widthPx,int heightPx,float widthMm,float minimumMm,float gapMm){
        return safeColumns(requested,widthPx,heightPx,widthMm,minimumMm,gapMm,false);
    }
    public static int safeColumns(int requested,int widthPx,int heightPx,float widthMm,
                                  float minimumMm,float gapMm,boolean enhanced){
        if(widthPx<12||heightPx<12||widthMm<=0||minimumMm<=0||gapMm<0)
            throw new IllegalArgumentException("Invalid artwork dimensions for layered portrait");
        int from=Math.max(4,Math.min(80,requested));
        for(int cols=from;cols>=4;cols--){
            int rows=Math.max(1,Math.round((float)cols*heightPx/widthPx));
            if((long)cols*rows>(enhanced?LayeredCellPortraitEngine.MAX_DETAIL_CELLS:
                    LayeredCellPortraitEngine.MAX_CELLS))continue;
            float cellW=widthMm/cols;
            float cellH=widthMm*heightPx/(float)widthPx/rows;
            if(Math.min(cellW,cellH)-gapMm>minimumMm*3.01f)return cols;
        }
        throw new IllegalArgumentException("Artwork too small for safe layered cells. Enlarge artwork or reduce minimum feature width.");
    }
}

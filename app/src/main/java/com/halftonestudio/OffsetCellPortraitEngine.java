package com.halftonestudio;

/**
 * Dedicated running-bond portrait style. It shares the tested Layered Cell
 * vector cutter, but opts into half-cell staggered rows. Layered Cell itself
 * always keeps offsetRows=false and retains byte-identical legacy geometry.
 */
public final class OffsetCellPortraitEngine {
    private OffsetCellPortraitEngine() { }

    public static LayeredCellPortraitEngine.Result generate(
            int[] argb,int width,int height,LayeredCellPortraitEngine.Options settings) {
        if(settings==null)throw new IllegalArgumentException("Offset Cell options required");
        LayeredCellPortraitEngine.Options o=settings.copy();
        o.offsetRows=true;
        // Offset Cell has one permanent solid Snip base; all remaining physical
        // sheets represent photo-colored cells, never a dedicated motif sheet.
        o.solidBase=true;
        o.detailMotifOverlay=false;
        // The extra half-cells at row ends must count toward the actual ceiling.
        int budget=o.solidBase?LayeredCellPortraitEngine.MAX_DETAIL_CELLS:
                LayeredCellPortraitEngine.MAX_CELLS;
        while(o.columns>4){
            int rows=Math.max(1,Math.round(o.columns*height/(float)width));
            if((long)o.columns*rows+rows/2<=budget)break;
            o.columns--;
        }
        return LayeredCellPortraitEngine.generate(argb,width,height,o);
    }
}

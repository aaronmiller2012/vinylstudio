package com.halftonestudio;

/** Number of physical ARTWORK cut loops per vinyl sheet, excluding registration marks.
 * Counts are derived from cut geometry, not the raster preview or visible dot density.
 */
public final class LayerCutCounts {
    private LayerCutCounts() {}

    public static int[] standard(VinylEngine.Result result) {
        if (result == null) return new int[0];
        int[] counts = new int[result.shapes.size()];
        for (int i = 0; i < counts.length; i++) {
            if (!result.settings.layers.get(i).enabled) continue;
            // svgElementsForLayer already accounts for compound shape contours;
            // registration marks are separate and must not appear in this count.
            counts[i] = VinylEngine.svgElementsForLayer(result, i)
                    - VinylEngine.registrationElements(result);
        }
        return counts;
    }

    public static int[] mosaic(TessellationEngine.Result result) {
        return result == null ? new int[0] : result.counts.clone();
    }

    public static int[] layeredCell(LayeredCellPortraitEngine.Result result) {
        // One piece can contain interior holes requiring additional blade loops.
        return result == null ? new int[0] : result.contourCounts.clone();
    }

    public static int[] tonal(MultilayerStencilEngine.Result result) {
        if (result == null) return new int[0];
        int[] counts = new int[result.layers.size()];
        for (int i = 0; i < counts.length; i++) {
            if (Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException("Cut count cancelled");
            MultilayerStencilEngine.Layer layer = result.layers.get(i);
            if (layer.pixelCount != 0)
                counts[i] = result.cutContourCount(i);
        }
        return counts;
    }
}

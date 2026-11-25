package com.halftonestudio;

/** Single source of truth for the physical eight-sheet order of named palettes.
 * No Android dependency, source RGB alteration, or mutation of the passed swatches.
 */
public final class VinylPaletteMapping {
    private VinylPaletteMapping() {}

    public static int[] forStyle(int style,int physicalLayers,int surface,int[] eight,
                                 boolean reversed,boolean photoDerived) {
        return forStyle(style,physicalLayers,surface,eight,reversed,photoDerived,null);
    }

    /**
     * M5. {@code photoArgb} is the prepared photo and is passed ONLY on an explicit palette apply
     * (the integrator passes null for saved palettes and for every non-apply call).
     * Confetti is the one family whose base is chosen from the photo, see {@link ConfettiBasePolicy}:
     * the solid L1 shows through between fragments (about 62 % of the silhouette on the real
     * photos), so a darkest-colour base drags the whole portrait to black. With a photo:
     * a photo-derived palette first keeps the best {@code physicalLayers} swatches
     * ({@link PhotoSwatchSubset}, so highlights are not parked), then the base is the
     * perceptually best active colour. Without a photo a named palette gets the median-tone base.
     */
    public static int[] forStyle(int style,int physicalLayers,int surface,int[] eight,
                                 boolean reversed,boolean photoDerived,int[] photoArgb) {
        if(eight==null||eight.length!=8)
            throw new IllegalArgumentException("Expected exactly eight swatches");
        if(physicalLayers<2||physicalLayers>8 ||
           (style==VinylStyleSuite.GLASS&&physicalLayers<3))
            throw new IllegalArgumentException("Invalid physical sheets for artwork style");
        // Sort the original palette for this style first, then reverse the
        // resulting physical cut-sheet order. Reversing before sorting had no
        // visible effect for some ranked/tonal families.
        boolean anchored=style==VinylStyleSuite.GLASS||style==VinylStyleSuite.TEXT||
            VinylStyleSuite.isConfetti(style)||VinylStyleSuite.isPrismaticFamily(style)||
            style==VinylStyleSuite.CHAINMAIL||style==VinylStyleSuite.TESSELLATION_MOTIF||
            VinylStyleSuite.isCraft(style)||VinylStyleSuite.isOrganicFlow(style)||
            style==VinylStyleSuite.POSTER_STENCIL;
        final boolean confetti=VinylStyleSuite.isConfetti(style);
        if(photoDerived){
            if(confetti&&photoArgb!=null){
                int[] subset=PhotoSwatchSubset.select(eight,physicalLayers,photoArgb);
                int[] seated=ConfettiBasePolicy.anchor(subset,physicalLayers,photoArgb,ConfettiBasePolicy.PERCEPTUAL);
                return PaletteOrientation.reverseActive(seated,physicalLayers,reversed,anchored);
            }
            return PaletteOrientation.reverseActive(eight,physicalLayers,reversed,anchored);
        }
        int[] mapped=PaletteStylePolicy.arrange(PaletteStylePolicy.familyForNewStyle(style),
            eight,physicalLayers,surface);
        if(confetti)mapped=ConfettiBasePolicy.anchor(mapped,physicalLayers,photoArgb,ConfettiBasePolicy.PERCEPTUAL);
        return PaletteOrientation.reverseActive(mapped,physicalLayers,reversed,anchored);

    }

    /** CMYK-inspired specialty styles use opaque vinyl sheets, not ink mixing.
     * Glass always uses black lead as the first sheet.
     */
    public static int[] processEight(int style,int physicalLayers,int surface,boolean reversed) {
        int[] colors={0xff131313,0xff00aeef,0xffec008c,0xffffdd00,
            0xff1672de,0xfff47e26,0xff12a894,0xfff8f5e9};
        return forStyle(style,physicalLayers,surface,colors,reversed,false);
    }
}

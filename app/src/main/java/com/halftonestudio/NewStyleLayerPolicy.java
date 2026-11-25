package com.halftonestudio;

/** Common invariants for physically structural sheets across every Layers panel. */
public final class NewStyleLayerPolicy {
    private NewStyleLayerPolicy() { }
    /** This sheet is not a normal overlay and cannot be deleted. */
    public static boolean isStructuralLayer(int style,int slot) {
        return slot==0&&(style==VinylStyleSuite.GLASS||
            style==VinylStyleSuite.TEXT||style==VinylStyleSuite.TESSELLATION_MOTIF||
            VinylStyleSuite.isPrismaticFamily(style)||VinylStyleSuite.isConfetti(style)||
            style==VinylStyleSuite.CHAINMAIL||VinylStyleSuite.isCraft(style)||
            VinylStyleSuite.isOrganicFlow(style)||style==VinylStyleSuite.POSTER_STENCIL);
    }
    public static boolean canDelete(int style,int slot,int count) {
        return NewStyleColorSlots.multi(style)&&
            count>NewStyleColorSlots.minimumSelectable(style)&&
            slot>=0&&slot<count&&!isStructuralLayer(style,slot);
    }
}

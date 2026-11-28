package com.halftonestudio;

/** Physical color slots for the additional art generators. No Android dependencies. */
public final class NewStyleColorSlots {
    private NewStyleColorSlots() {}
    public static boolean multi(int style) {
        return style == VinylStyleSuite.MULTILAYER_MAZE || style == VinylStyleSuite.LOW_POLY || style == VinylStyleSuite.GLASS ||
            style == VinylStyleSuite.TEXT || style == VinylStyleSuite.TOPOGRAPHIC ||
            style == VinylStyleSuite.TESSELLATION_MOTIF ||
            style == VinylStyleSuite.MARQUETRY || style == VinylStyleSuite.REDUCTION_WOODCUT ||
            style == VinylStyleSuite.WOVEN_RIBBON || style == VinylStyleSuite.TORN_COLLAGE ||
            VinylStyleSuite.isPrismaticFamily(style) ||
            VinylStyleSuite.isConfetti(style) || style == VinylStyleSuite.CHAINMAIL ||
            VinylStyleSuite.isOrganicFlow(style) || VinylStyleSuite.isPopPoster(style) ||
            VinylStyleSuite.isCraft(style);
    }
    public static int slotCount(int style, int variant, int requested) {
        if(VinylStyleSuite.isMaze(style))return style==VinylStyleSuite.MULTILAYER_MAZE?2:1;
        if (style == VinylStyleSuite.LINOCUT && variant != 0) return 2;
        if (style == VinylStyleSuite.BANKNOTE &&
                (variant==BanknoteEngravingEngine.CROSS_ETCH ||
                 variant==BanknoteEngravingEngine.TWO_INK)) return 2;
        if (style == VinylStyleSuite.WOVEN_RIBBON) return Math.max(2, Math.min(4, requested));
        if (style==VinylStyleSuite.POP_PANELS) return 4;
        if (style==VinylStyleSuite.POSTER_STENCIL) return Math.max(2,Math.min(9,requested));
        if (VinylStyleSuite.isCraft(style)) return Math.max(2,Math.min(9,requested));
        if (!multi(style)) return 1;
        return Math.max(style == VinylStyleSuite.GLASS ? 3 : 2, Math.min(8, requested));
    }
    public static int minimumSelectable(int style) {
        if(VinylStyleSuite.isMaze(style))return style==VinylStyleSuite.MULTILAYER_MAZE?2:1;
        if (style==VinylStyleSuite.POP_PANELS) return 4;
        if (style==VinylStyleSuite.GLASS) return 3;
        return multi(style)?2:1;
    }
    public static int maximumSelectable(int style) {
        if(VinylStyleSuite.isMaze(style))return style==VinylStyleSuite.MULTILAYER_MAZE?2:1;
        if (style==VinylStyleSuite.POP_PANELS) return 4;
        if (style==VinylStyleSuite.POSTER_STENCIL) return 9;
        if (style==VinylStyleSuite.WOVEN_RIBBON) return 4;
        if (VinylStyleSuite.isCraft(style)) return 9;
        return multi(style)?8:1;
    }
    /** Returns the stable physical swatch slot even if unused engine layers were omitted. */
    public static int slot(int style, String layerName, int fallback) {
        if (style == VinylStyleSuite.POP_PANELS) return Math.max(0,fallback);
        if (style == VinylStyleSuite.GLASS && "lead".equals(layerName)) return 0;
        if (style == VinylStyleSuite.TEXT && "snipped-background-1".equals(layerName)) return 0;
        if (style == VinylStyleSuite.LINOCUT) {
            return layerName != null && layerName.contains("cross") ? 1 : 0;
        }
        if (style == VinylStyleSuite.BANKNOTE)
            return layerName != null &&
                   layerName.contains("cross-etch") ? 1 : 0;
        if (!multi(style)) return 0;
        if (layerName != null) {
            int end = layerName.length(), start = end;
            while (start > 0 && Character.isDigit(layerName.charAt(start - 1))) start--;
            if (start < end) {
                try {
                    int ordinal = Integer.parseInt(layerName.substring(start));
                    int slot = ordinal - 1 + (style == VinylStyleSuite.GLASS ? 1 : 0);
                    if (slot >= 0 && slot < ((VinylStyleSuite.isCraft(style)||style==VinylStyleSuite.POSTER_STENCIL)?9:8)) return slot;
                } catch (NumberFormatException ignored) { }
            }
        }
        return Math.max(0, Math.min((VinylStyleSuite.isCraft(style)||style==VinylStyleSuite.POSTER_STENCIL)?8:7, fallback));
    }
    public static int[] fixedPalette(int style, int variant, int requested, int[] swatches) {
        if (swatches == null || swatches.length < ((VinylStyleSuite.isCraft(style)||style==VinylStyleSuite.POSTER_STENCIL)?9:8))
            throw new IllegalArgumentException("Not enough physical swatches");
        if (style == VinylStyleSuite.GLASS) {
            int count = slotCount(style, variant, requested);
            int[] glass = new int[count - 1];
            System.arraycopy(swatches, 1, glass, 0, glass.length);
            return glass;
        }
        return java.util.Arrays.copyOf(swatches, slotCount(style, variant, requested));
    }
}

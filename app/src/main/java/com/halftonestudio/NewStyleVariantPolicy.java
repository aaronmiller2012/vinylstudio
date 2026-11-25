package com.halftonestudio;

/** Pure JVM model of stable, saved variant IDs. Keep historical IDs intact. */
final class NewStyleVariantPolicy {
    private NewStyleVariantPolicy() { }
    static int count(int style) {
        switch(style) {
            case VinylStyleSuite.BANKNOTE: return BanknoteEngravingEngine.VARIANTS.length;
            case VinylStyleSuite.TESSELLATION_MOTIF: return TessellationMotifEngine.PATTERN_SLOT_COUNT;
            case VinylStyleSuite.TRUCHET: return TruchetTilesEngine.Pattern.values().length;
            case VinylStyleSuite.TEXT: return 3; // Saved legacy Word styles remain readable.
            case VinylStyleSuite.LINOCUT:
            case VinylStyleSuite.TSP:
            case VinylStyleSuite.TOPOGRAPHIC:
            case VinylStyleSuite.LOW_POLY:
            case VinylStyleSuite.GLASS: return 1;
            case VinylStyleSuite.MARQUETRY:
            case VinylStyleSuite.FINGERPRINT:
            case VinylStyleSuite.REDUCTION_WOODCUT:
            case VinylStyleSuite.WOVEN_RIBBON:
            case VinylStyleSuite.WOODGRAIN:
            case VinylStyleSuite.TORN_COLLAGE:
                return ExpandedPortraitStylesEngine.variantCount(style-VinylStyleSuite.MARQUETRY);
            case VinylStyleSuite.POP_PANELS:
            case VinylStyleSuite.POSTER_STENCIL:
            case VinylStyleSuite.PRISMATIC_DIAGONAL:
            case VinylStyleSuite.PRISMATIC_CONTOUR_FLOW:
            case VinylStyleSuite.PRISMATIC_TWILL:
            case VinylStyleSuite.PRISMATIC_SILK:
            case VinylStyleSuite.PRISMATIC_BRAIDED_FLOW:
            case VinylStyleSuite.PRISMATIC_CHEVRON:
            case VinylStyleSuite.QUILLING_RIBBONS:
            case VinylStyleSuite.MARBLED_INK:
            case VinylStyleSuite.QUILLED_MARBLING:
            case VinylStyleSuite.CONFETTI:
            case VinylStyleSuite.CHAINMAIL:
            case VinylStyleSuite.CONFETTI_ROUND:
            case VinylStyleSuite.CONFETTI_SQUARE:
            case VinylStyleSuite.CONFETTI_PAPER:
            case VinylStyleSuite.CONFETTI_STREAMER:
            case VinylStyleSuite.CONFETTI_FALLEN:
            case VinylStyleSuite.CAMOUFLAGE: case VinylStyleSuite.LEAF_VEIN:
            case VinylStyleSuite.FISHNET: case VinylStyleSuite.LACE:
            case VinylStyleSuite.CROCHET:
            case VinylStyleSuite.CAMO_DESERT:
            case VinylStyleSuite.CAMO_URBAN:
            case VinylStyleSuite.CAMO_DIGITAL: return 1;
            default: return 0; // Includes tombstone 8 and unknown IDs.
        }
    }
    static int restore(int style, int savedVariant) {
        int n=count(style);
        if(style==VinylStyleSuite.TESSELLATION_MOTIF &&
            !TessellationMotifEngine.selectablePattern(savedVariant))
            return TessellationMotifEngine.TRIHEX;
        if(style==VinylStyleSuite.BANKNOTE && savedVariant==BanknoteEngravingEngine.RETIRED_GUILLOCHE)
            return BanknoteEngravingEngine.CONTOUR;
        return n>0 && savedVariant>=0 && savedVariant<n ? savedVariant : 0;
    }
    static int next(int style,int current) {
        int n=count(style);
        if(n<=1)return 0;
        int candidate=restore(style,current);
        for(int i=0;i<n;i++){
            candidate=(candidate+1)%n;
            if((style!=VinylStyleSuite.BANKNOTE || candidate!=BanknoteEngravingEngine.RETIRED_GUILLOCHE)
                 && (style!=VinylStyleSuite.TESSELLATION_MOTIF ||
                     TessellationMotifEngine.selectablePattern(candidate)))
                return candidate;
        }
        return 0;
    }
    static boolean selectable(int style) {
        return VinylStyleSuite.restoredStyleId(style)==style && count(style)>0;
    }
}

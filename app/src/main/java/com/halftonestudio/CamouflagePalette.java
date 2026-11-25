package com.halftonestudio;

/**
 * RC164 US-inspired artistic color families, not official uniform color specifications.
 * Applied once at first style selection; photo-derived and edited swatches are never overwritten.
 * Nine swatches = L1 foundation plus eight distinct physical overlay colors.
 */
public final class CamouflagePalette {
    private CamouflagePalette(){}
    private static final int[] WOODLAND={0xff222a20,0xff33442d,0xff5d6540,
        0xff756249,0xffa18b65,0xff3a3429,0xff4b583f,0xffc3ad80,0xff788557};
    private static final int[] DESERT={0xff694930,0xff866444,0xffb18b64,
        0xffccb087,0xffe3d0a5,0xffd4b487,0xffaa8a63,0xffefe0bd,0xff97805b};
    private static final int[] URBAN={0xff23272b,0xff454b51,0xff6c747a,
        0xffa5aab0,0xffd0d2d1,0xff525b61,0xff7c858b,0xffbac0c0,0xff384046};
    private static final int[] DIGITAL={0xff353d34,0xff535d50,0xff788475,
        0xffa0a89d,0xffc0c5b5,0xff717a66,0xffb5b09d,0xffd5d3c5,0xff929786};
    /** Palette authority is intentionally independent of the style's geometry.
     *  New camouflage projects start in fixed vinyl-color mode, not photo auto mode.
     *  An explicit photo-mode selection is retained on later style switches.
     */
    public static boolean isCamouflage(int style){
        return style==VinylStyleSuite.CAMOUFLAGE || style==VinylStyleSuite.CAMO_DESERT ||
            style==VinylStyleSuite.CAMO_URBAN || style==VinylStyleSuite.CAMO_DIGITAL;
    }
    public static boolean useFamilyDefault(int style,boolean hasSavedPalette,
            boolean savedFixedColors,boolean explicitPhotoMode){
        return isCamouflage(style) && (!hasSavedPalette ||
            (!savedFixedColors && !explicitPhotoMode));
    }
    public static String name(int style){
        switch(style){
            case VinylStyleSuite.CAMOUFLAGE:return "Woodland";
            case VinylStyleSuite.CAMO_DESERT:return "Desert";
            case VinylStyleSuite.CAMO_URBAN:return "Urban";
            case VinylStyleSuite.CAMO_DIGITAL:return "Digital";
            default:return "";
        }
    }
    public static int[] defaults(int style,int[] existing){
        int[] selected=style==VinylStyleSuite.CAMOUFLAGE?WOODLAND:
            style==VinylStyleSuite.CAMO_DESERT?DESERT:
            style==VinylStyleSuite.CAMO_URBAN?URBAN:
            style==VinylStyleSuite.CAMO_DIGITAL?DIGITAL:null;
        return selected==null?existing: selected.clone();
    }
}

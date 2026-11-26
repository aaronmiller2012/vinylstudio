package com.halftonestudio;

/** Presentation-only filters over stable thumbnail identities. The existing category,
 * saved style ID, favorites and recents are deliberately not migrated. */
final class StylePickerFamilies {
    static final String PRISMATIC="Prismatic", TEXTILE="Textile", CRAFT="Paper & craft", CAMO="Camouflage", MOSAIC="Mosaic";
    private StylePickerFamilies(){}
    static String family(String key){
        if(key==null)return "";
        if(key.startsWith("multilayer.prismatic_"))return PRISMATIC;
        if(key.startsWith("multilayer.confetti_")||key.equals("art.torn_collage")||
            key.equals("multilayer.quilling_ribbons")||key.equals("multilayer.marbled_ink")||key.equals("multilayer.quilled_marbling"))return CRAFT;
        if(key.equals("multilayer.camouflage")||key.startsWith("multilayer.camo_"))return CAMO;
        switch(key){
            case "multilayer.chainmail_linked":case "multilayer.fishnet":case "multilayer.crochet":case "art.woven_ribbon":return TEXTILE;
            case "multilayer.voronoi":case "multilayer.grid":case "multilayer.layered_cells":case "multilayer.offset_cells":
            case "multilayer.bottle_caps":case "multilayer.fish_scales":case "multilayer.low_poly":case "multilayer.stained_glass":
            case "multilayer.weighted_stipple":return MOSAIC;
            default:return key.startsWith("multilayer.overprint_")?MOSAIC:"";
        }
    }
    static boolean matches(String selected,String legacyCategory,String key){
        return "All".equals(selected)||selected.equals(legacyCategory)||selected.equals(family(key));
    }
    static String[] tabs(){return new String[]{"All","Favorites","Recent","Multilayer",PRISMATIC,TEXTILE,CRAFT,CAMO,MOSAIC,
        "Tessellation","Geometric styles","Lines","Single Line","Art techniques"};}
}

package com.halftonestudio;

public final class RetiredPortraitStylesTest {
    private static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    public static void main(String[] args){
        // Never recycle a stored style ID: doing so silently changes saved art.
        check(VinylStyleSuite.RIDGE==7,"Historic Ridge ID changed");
        check(VinylStyleSuite.isRetiredStyleId(VinylStyleSuite.RIDGE),"Ridge ID still selectable");
        check(VinylStyleSuite.restoredStyleId(VinylStyleSuite.RIDGE)==-1,"Ridge project reactivates");
        check(!NewStyleVariantPolicy.selectable(VinylStyleSuite.RIDGE),"Ridge variant still selectable");
        check(StudioThumbnailRegistry.allKeys().size()==117,"Expected 89 active + 28 tessellation thumbnails");
        check(!StudioThumbnailRegistry.isKnown("art.ridges"),"Ridge thumbnail still registered");
        check(!StudioThumbnailRegistry.isKnown("multilayer.painterly_cell_weave"),"Painterly thumbnail still registered");
        for(String name:new String[]{"multilayer.overprint_orbits","multilayer.overprint_lozenge",
              "multilayer.overprint_diamonds","multilayer.overprint_mixed","art.banknote",
              "multilayer.layered_cells","multilayer.offset_cells"})
            check(StudioThumbnailRegistry.isKnown(name),"Missing retained thumbnail "+name);
        check("art.banknote".equals(StudioThumbnailRegistry.newStyle(VinylStyleSuite.BANKNOTE)),
            "Banknote art ID shifted by removing Ridge");
        boolean rejected=false;
        try{StudioThumbnailRegistry.newStyle(VinylStyleSuite.RIDGE);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Retired Ridge thumbnail still maps to active entry");
        rejected=false;
        try{StudioThumbnailRegistry.specialty(26);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Retired Painterly specialty is still addressable");
        int[] src=new int[24*24];java.util.Arrays.fill(src,0xffaabbcc);
        LayeredCellPortraitEngine.Options options=new LayeredCellPortraitEngine.Options();
        options.overprintKind=LayeredCellPortraitEngine.OVERPRINT_PAINTERLY;
        options.registration=false;
        rejected=false;
        try{LayeredCellPortraitEngine.generate(src,24,24,options);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Retired Painterly engine still generates");
        VinylStyleSuite.Config c=new VinylStyleSuite.Config();c.style=VinylStyleSuite.RIDGE;
        rejected=false;
        try{VinylStyleSuite.generate(src,24,24,c);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Retired Ridge engine still generates");
        System.out.println("PASS retired style identities and remaining thumbnail selection");
    }
}

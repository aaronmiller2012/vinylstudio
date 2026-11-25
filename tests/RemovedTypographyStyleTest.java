package com.halftonestudio;

/** Permanent style-ID tombstone and no nine-layer runtime regression. */
public final class RemovedTypographyStyleTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        check(VinylStyleSuite.NAMES.length>=26,"IDs 9–25 must remain addressable");
        check(VinylStyleSuite.RETIRED_STYLE_ID==8,"Never recycle the deleted style ID");
        for(int id=0;id<7;id++)check(VinylStyleSuite.restoredStyleId(id)==id,"Legacy style changed: "+id);
        int[] retired={7,8,13,16,17,18,19,20,22,23,25};
        for(int id:new int[]{-200,-2,-1,7,8,13,16,17,18,19,20,22,23,25,1000})
            check(VinylStyleSuite.restoredStyleId(id)==-1,"Invalid or retired ID was activated: "+id);
        check(VinylStyleSuite.restoredStyleId(9)==9,"Banknote ID 9 not restored");
        check(VinylStyleSuite.restoredStyleId(10)==10,"Tessellation ID 10 not restored");
        for(int id:new int[]{11,12,14,15,21,24})check(VinylStyleSuite.restoredStyleId(id)==id,"Approved RC142 style not restored: "+id);
        for(int id:retired){
            check(!NewStyleColorSlots.multi(id),"Deleted style remains eligible for multilayer UI: "+id);
            VinylStyleSuite.Config removed=new VinylStyleSuite.Config();removed.style=id;
            try {VinylStyleSuite.generate(new int[]{0xff555555,0xff666666,0xff777777,0xff888888},2,2,removed);
                throw new AssertionError("Deleted engine can still generate: "+id);
            }catch(IllegalArgumentException expected) { }
        }
        for(int id=0;id<7;id++){
            int layers=NewStyleColorSlots.slotCount(id,0,8);
            check(layers>=1&&layers<=8,"Retained style exceeded eight physical sheets: "+id);
        }
        System.out.println("PASS RC142: retired IDs safely map to photo workspace; approved IDs remain stable");
    }
}

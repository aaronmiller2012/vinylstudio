package com.halftonestudio;

import java.util.Arrays;

/** Preview/commit/source identity without loading Android UI classes. */
public final class OpaqueOverlayPaletteMappingTest {
    private static void ok(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static final int[] SWATCHES={0xff191919,0xffcc4829,0xffe4c7a7,0xff5286b2,
                                        0xff838f4c,0xff563049,0xffe4ae3c,0xfff4e9da};
    private static void savedAndReverse(){
        for(int count=2;count<=8;count++)for(int hue:new int[]{-68,0,97}){
            int[] physical=SWATCHES.clone();
            int[] preview=OpaqueOverlayPaletteMapping.savedPreview(physical,count,true,hue);
            int[] committed=PaletteHueShift.rotate(
                OpaqueOverlayPaletteMapping.saved(physical,count,true),hue);
            ok(Arrays.equals(preview,committed),"Saved palette preview mismatches actual stack");
            ok(OpaqueOverlayPaletteMapping.saved(physical,count,true)[0]==SWATCHES[0],
                "Reverse displaced the solid foundation");
            int[] after=OpaqueOverlayPaletteMapping.saved(
                OpaqueOverlayPaletteMapping.saved(physical,count,true),count,true);
            ok(Arrays.equals(after,physical),"Double Reverse did not restore eight swatches");
            for(int index=count;index<8;index++)
                ok(OpaqueOverlayPaletteMapping.saved(physical,count,true)[index]==physical[index],
                    "Inactive swatch reordered by Reverse");
        }
        ok(Arrays.equals(SWATCHES,OpaqueOverlayPaletteMapping.saved(SWATCHES,5,false)),
            "Saved palette unexpectedly re-sorted by paper or photograph");
    }
    private static void presetAndIsolation(){
        int[] photo={0xff998a79,0xff27211f,0xffe8c7a4,0xffdfba93,
                     0xffd8ae89,0xff564841,0xffd7bdab,0xff45413c};
        int[] pixels={0xff161310,0xff282322,0xff2e2925,0xff5b4c45,
                      0xff4b3831,0xff93837b};
        for(int count=2;count<=8;count++){
            int[] unmoved=photo.clone();
            int[] mapped=OpaqueOverlayPaletteMapping.preset(photo,count,0xffffffff,pixels,true,false);
            int[] inverted=OpaqueOverlayPaletteMapping.preset(photo,count,0xffffffff,pixels,true,true);
            ok(mapped[0]==inverted[0],"Photo foundation moved after Reverse");
            for(int i=count;i<8;i++)ok(mapped[i]==inverted[i],"Parked photo chip moved");
            int[] restored=OpaqueOverlayPaletteMapping.saved(inverted,count,true);
            ok(Arrays.equals(mapped,restored),"Reverse did not recover chosen physical stack");
            ok(Arrays.equals(photo,unmoved),"Photo swatches were modified");
            // Paper is an input only to an EXPLICIT new preset. Existing saved
            // physical swatches must not be re-computed when paper changes.
            int[] saved=OpaqueOverlayPaletteMapping.saved(mapped,count,false);
            ok(Arrays.equals(saved,mapped),"Paper change altered existing swatches");
        }
        boolean rejected=false;
        try{OpaqueOverlayPaletteMapping.saved(new int[7],4,false);}
        catch(IllegalArgumentException good){rejected=true;}
        ok(rejected,"Malformed physical palettes must be rejected");
    }
    private static void activePhotoCoverage(){
        // The first four input chips intentionally contain no blue or warm
        // skin highlight. The photo contains both. Four active sheets must
        // promote the useful candidates out of the parked L5–L8 slots.
        int[] swatches={0xff353331,0xff514844,0xff68605b,0xff77716c,
                        0xffb85c48,0xff467da9,0xfff2dfbf,0xff191c22};
        int[] photo=new int[400];
        for(int i=0;i<photo.length;i++)
            photo[i]=i%10<6?0xffb85c48:i%10<8?0xff467da9:0xfff2dfbf;
        int[] mapped=OpaqueOverlayPaletteMapping.preset(swatches,4,0xffeeeeee,photo,true,false);
        boolean warm=false,blue=false;
        for(int i=0;i<4;i++){
            if(mapped[i]==swatches[4])warm=true;
            if(mapped[i]==swatches[5])blue=true;
        }
        ok(warm&&blue,"Active photo colours were stranded in parked swatches: "+Arrays.toString(mapped));
        int[] all=swatches.clone(),sorted=mapped.clone();
        Arrays.sort(all);Arrays.sort(sorted);
        ok(Arrays.equals(all,sorted),"Palette optimizer fabricated or dropped physical vinyl colours");
        ok(Arrays.equals(mapped,OpaqueOverlayPaletteMapping.preset(swatches,4,0xff080808,photo,true,false)),
            "Mounting surface improperly changed the opaque photo palette");
        ok(Arrays.equals(mapped,OpaqueOverlayPaletteMapping.saved(mapped,4,false)),
            "Saved photo palette changed after it was applied");
        int[] manual=swatches.clone();manual[1]=0xff009922;
        ok(Arrays.equals(manual,OpaqueOverlayPaletteMapping.saved(manual,4,false)),
            "Manual edits changed on saved palette re-read");
    }
    private static void uniformAndEmptyPhotos(){
        int[] photo={0x00222222,0x00000000,0x00887744};
        for(int count=2;count<=8;count++){
            int[] empty=OpaqueOverlayPaletteMapping.preset(SWATCHES,count,0xffffffff,photo,true,false);
            int[] fallback=OpaqueOverlayPaletteMapping.preset(SWATCHES,count,0xffffffff,null,true,false);
            ok(Arrays.equals(empty,fallback),"Fully transparent photo did not use deterministic fallback");
            int[] uniform=new int[128];Arrays.fill(uniform,SWATCHES[3]);
            int[] mapped=OpaqueOverlayPaletteMapping.preset(SWATCHES,count,0xffffffff,uniform,true,false);
            ok(mapped.length==8,"Uniform photo generated invalid stack");
        }
    }
    public static void main(String[] args){
        savedAndReverse();System.out.println("PASS saved-palette preview, hue, anchored reverse and parked slots");
        presetAndIsolation();activePhotoCoverage();uniformAndEmptyPhotos();
        System.out.println("PASS photo-driven active swatches, paper independence, flat/empty samples, source isolation");
    }
}

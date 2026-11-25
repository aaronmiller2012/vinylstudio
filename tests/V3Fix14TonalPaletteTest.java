package com.halftonestudio;

import java.util.Arrays;

/** Executable physical-layer/source-ID color mapping contract. */
public final class V3Fix14TonalPaletteTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void rgbNear(int a, int b, int tolerance, String message) {
        for (int shift : new int[]{0, 8, 16})
            check(Math.abs(((a >>> shift) & 255) - ((b >>> shift) & 255)) <= tolerance, message);
    }
    public static void main(String[] args) {
        int[] sources = {4, 1, 3, 0, 2}; // physical L1..L5 is not source order
        int[] old = new int[8];
        int[] photo = {0xff221109,0xff65462b,0xffe5bc94,0xff9b603d,0xffb77b58};
        int[] shown = TonalPaletteMapping.display(photo,sources,old,0,0);
        check(Arrays.equals(photo,shown),"Auto photo swatches must match the finished renderer");
        int[] picked = {0xff1e334b,0xff3a7181,0xffb29d54,0xffca6140,0xffe4d6bd};
        int[] mapped = TonalPaletteMapping.fromPhysical(old,picked,sources,0);
        check(mapped[4]==picked[0] && mapped[0]==picked[3],"Physical palette must follow sourceIndex");
        check(mapped[5]==0 && mapped[6]==0,"Inactive source entries must remain unchanged");
        int[] after = TonalPaletteMapping.display(photo,sources,mapped,0,0);
        check(Arrays.equals(picked,after),"Selected physical colors must match UI L1..L5");
        // Simulated save named palette: store actual displayed colors with zero
        // extra Hue, then apply them back in physical order after reopening.
        int[] saved = after.clone();
        int[] restored = TonalPaletteMapping.fromPhysical(new int[8],saved,sources,0);
        check(Arrays.equals(TonalPaletteMapping.display(photo,sources,restored,0,0),saved),
            "Saved tonal colors must round-trip in displayed physical order");
        int[] hueShown = TonalPaletteMapping.display(photo,sources,mapped,0,50);
        for (int i=0;i<picked.length;i++)
            rgbNear(hueShown[i],PaletteHueShift.rotateOne(picked[i],50),0,"Hue must use current source RGB");
        int[] reversed = PhysicalLayerColorReverse.reverse(mapped,hueShown,sources,50);
        int[] reversedDisplay = TonalPaletteMapping.display(photo,sources,reversed,0,50);
        for (int i=0;i<5;i++)rgbNear(reversedDisplay[i],hueShown[4-i],1,"Reverse must follow physical order");
        int[] twice = PhysicalLayerColorReverse.reverse(reversed,reversedDisplay,sources,50);
        int[] twiceShown = TonalPaletteMapping.display(photo,sources,twice,0,50);
        for (int i=0;i<5;i++)rgbNear(twiceShown[i],hueShown[i],2,"Reverse twice must restore colors");
        // Changing hue while render is pending must update swatches immediately,
        // without reusing old prepared-render colors or moving ownership.
        int[] pending = TonalPaletteMapping.display(photo,sources,new int[8],0,35);
        for (int i=0;i<5;i++)rgbNear(pending[i],PaletteHueShift.rotateOne(photo[i],35),0,
            "Pending Hue must rotate photo-derived swatches, not display stale result");
        boolean failed=false;
        try {TonalPaletteMapping.fromPhysical(old,picked,new int[]{4,1,3,0,0},0);}
        catch (IllegalArgumentException expected) { failed=true; }
        check(failed,"Duplicate physical-to-source mapping must be rejected");
        System.out.println("PASS Fix14 tonal palette: nonidentity source order, saved swatches, Hue, Reverse, pending render, invalid mapping");
    }
}

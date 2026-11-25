package com.halftonestudio;

/** Saved CMYK palette + Reverse keeps Y first and K last among ACTIVE layers (pre-M4 bug). */
public final class M4SavedCmykReverseTest {
    public static void main(String[] args){
        int Y=0xffffdd00,C=0xff00aeef,M=0xffec008c,K=0xff131313;
        for(int count=4;count<=8;count++){
            int[] slots={Y,C,M,0xff111111,0xff222222,0xff333333,0xff444444,0xff555555};
            slots[count-1]=K;
            int[] legacy=PaletteOrientation.orientCmyk(slots,true);
            int[] fixed=PaletteOrientation.orientCmykActive(slots,count,true);
            if(count<8&&legacy[count-1]==K)throw new AssertionError("fixture should reproduce the old K displacement at "+count);
            if(fixed[0]!=Y||fixed[count-1]!=K)throw new AssertionError("Y/K anchors moved at "+count+" layers");
            for(int i=count;i<8;i++)if(fixed[i]!=slots[i])throw new AssertionError("parked slot moved");
            int[] twice=PaletteOrientation.orientCmykActive(fixed,count,true);
            if(!java.util.Arrays.equals(twice,slots))throw new AssertionError("reverse is not an involution");
        }
        System.out.println("PASS M4 saved CMYK reverse keeps Y/K anchors among active layers");
    }
}

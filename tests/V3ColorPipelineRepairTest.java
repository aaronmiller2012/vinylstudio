package com.halftonestudio;
import java.util.Arrays;

/** Host-executable focused regression: nonidentity physical stack and Hue/Reverse. */
public final class V3ColorPipelineRepairTest {
    static void ok(boolean valid,String message){if(!valid)throw new AssertionError(message);}
    public static void main(String[] args){
        int[] current={0xff2050c0,0xff30a040,0xffe0c040,0xffc03020};
        int[] nonidentity={3,0,2,1};
        int[] physical=new int[4];
        for(int i=0;i<4;i++)physical[i]=current[nonidentity[i]];
        int[] once=PhysicalLayerColorReverse.reverse(new int[8],physical,nonidentity,0);
        int[] after=new int[4];for(int i=0;i<4;i++)after[i]=once[nonidentity[i]];
        for(int i=0;i<4;i++)ok(after[i]==physical[3-i],"Nonidentity Reverse differs from visual reversal");
        int[] twice=PhysicalLayerColorReverse.reverse(once,after,nonidentity,0);
        for(int i=0;i<4;i++)ok(twice[nonidentity[i]]==physical[i],"Reverse twice must restore exact RGB");
        ok(twice[4]==0,"Inactive source slots must not be edited");
        // At nonzero hue the reverse must always use physical display order.
        int[] hueShown=new int[4];for(int i=0;i<4;i++)hueShown[i]=PaletteHueShift.rotateOne(physical[i],40);
        int[] shifted=PhysicalLayerColorReverse.reverse(new int[8],hueShown,nonidentity,40);
        for(int i=0;i<4;i++){
            int actual=PaletteHueShift.rotateOne(shifted[nonidentity[i]],40);
            int expected=hueShown[3-i];
            ok(Math.abs(((actual>>16)&255)-((expected>>16)&255))<=1 &&
                Math.abs(((actual>>8)&255)-((expected>>8)&255))<=1 &&
                Math.abs((actual&255)-(expected&255))<=1,"Hue-adjusted reverse drift >1 channel");
        }
        boolean invalid=false;
        try{PhysicalLayerColorReverse.reverse(new int[8],physical,new int[]{3,3,2,1},0);}
        catch(IllegalArgumentException expected){invalid=true;}
        ok(invalid,"Duplicate physical source IDs must fail closed");
        System.out.println("PASS color repair: nonidentity physical Reverse, involution, hue and invalid mapping");
    }
}

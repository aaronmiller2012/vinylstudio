package com.halftonestudio;

import java.util.Arrays;
import java.util.concurrent.CancellationException;

public final class BoundedTonalPreviewTest {
    private static void eq(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    private static void bad(Runnable action){try{action.run();throw new AssertionError("Expected validation failure");}
        catch(IllegalArgumentException expected) {}}
    public static void main(String[] args){
        int width=4,height=3;
        boolean[] base=new boolean[width*height];Arrays.fill(base,true);
        boolean[] top=new boolean[width*height];top[1]=top[5]=true;
        int[] colors={0xff123456,0xffcc22aa};
        BoundedTonalPreview.Result full=BoundedTonalPreview.compose(width,height,1200,0xffeeeeee,
            colors,new boolean[][]{base,top},-1);
        eq(full.width==4&&full.height==3,"original size preserved");
        eq(full.argb[0]==colors[0]&&full.argb[1]==colors[1]&&full.argb[5]==colors[1],"layer ordering and mask");
        BoundedTonalPreview.Result only=BoundedTonalPreview.compose(width,height,1200,0xffeeeeee,
            colors,new boolean[][]{base,top},1);
        eq(only.argb[1]==colors[1]&&only.argb[0]==0xffeeeeee,"selected layer on paper");
        BoundedTonalPreview.Result down=BoundedTonalPreview.compose(width,height,2,0xffeeeeee,
            colors,new boolean[][]{base,top},-1);
        eq(down.width==2&&down.height==2&&down.argb.length==4,"bounded aspect ratio");
        boolean[] large=new boolean[2000*1500];Arrays.fill(large,true);
        BoundedTonalPreview.Result bounded=BoundedTonalPreview.compose(2000,1500,600,0xffeeeeee,
            new int[]{0xff442211},new boolean[][]{large},-1);
        eq(bounded.width==600&&bounded.height==450,"large source scaled");
        eq(bounded.argb.length==270000&&bounded.argb[1200]==0xff442211,"output limited to preview resolution");
        bad(()->BoundedTonalPreview.compose(3000,2000,3000,0,new int[0],new boolean[0][], -1));
        bad(()->BoundedTonalPreview.compose(3,2,400,0,new int[]{3},new boolean[][]{new boolean[4]}, -1));
        bad(()->BoundedTonalPreview.compose(3,2,400,0,new int[]{3},new boolean[][]{new boolean[6]}, 1));
        Thread.currentThread().interrupt();
        try{BoundedTonalPreview.compose(4,3,3,0,colors,new boolean[][]{base,top},-1);
            throw new AssertionError("Canceled preview must exit");}
        catch(CancellationException expected){} finally{Thread.interrupted();}
        System.out.println("PASS RC57 bounded stencil preview, masks, layering, dimensions and cancellation");
    }
}

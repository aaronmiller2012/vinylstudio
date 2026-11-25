package com.halftonestudio;

import java.util.Arrays;

/** RC125: Offset Cell permanently has a silhouette and no dedicated accent sheet. */
public final class RC125OffsetHostTest {
    private static void ok(boolean test,String why){if(!test)throw new AssertionError(why);}
    private static int[] image(int w,int h){
        int[] data=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int c=(x*3+y*2)%256;
            data[y*w+x]=0xff000000|(c<<16)|(c<<8)|c;
        }
        return data;
    }
    public static void main(String[] args){
        int w=100,h=120;int[] pix=image(w,h);
        LayeredCellPortraitEngine.Options opts=new LayeredCellPortraitEngine.Options();
        opts.widthMm=152.4f;opts.columns=18;opts.layerCount=8;
        opts.minFeatureMm=.3f;opts.gapMm=.18f;opts.seamGapMm=.06f;
        opts.registration=false;opts.solidBase=false;opts.detailMotifOverlay=true;
        opts.colors=new int[]{0xff222222,0xff606060,0xff898989,0xffafafaf,
            0xffd1d1d1,0xffeeeeee,0xffdbaa88,0xff887744};
        LayeredCellPortraitEngine.Result result=OffsetCellPortraitEngine.generate(pix,w,h,opts);
        ok(!opts.solidBase&&opts.detailMotifOverlay,"Engine mutated original options");
        ok(result.options.solidBase,"Offset silhouette not permanent");
        ok(!result.options.detailMotifOverlay,"Offset still has a dedicated motif sheet");
        ok(result.options.offsetRows,"Offset no longer staggered");
        ok(result.pieceCounts[0]>0,"No solid silhouette on L1");
        boolean lastPhotoColor=false;
        for(LayeredCellPortraitEngine.Piece piece:result.pieces){
            if(piece.layer==7 && piece.cell>=0){lastPhotoColor=true;break;}
        }
        ok(lastPhotoColor,"Final sheet did not become a normal color sheet");
        // The legacy Layered Cell style is an independent, unchanged algorithm.
        LayeredCellPortraitEngine.Options plain=opts.copy();plain.offsetRows=false;
        LayeredCellPortraitEngine.Result original=LayeredCellPortraitEngine.generate(pix,w,h,plain);
        ok(!original.options.solidBase&&original.options.detailMotifOverlay,
           "Original Layered Cell mode changed");
        // Reverse acts on actual painted colors; preserves lead and parked slot.
        int[] test={1,2,3,4,5,6,7,8};
        int[] once=PaletteOrientation.reverseActive(test,5,true,true);
        int[] twice=PaletteOrientation.reverseActive(once,5,true,true);
        ok(Arrays.equals(test,twice),"Global reverse not reversible");
        ok(once[0]==1&&once[5]==6&&once[1]==5,"Structural base or parked slot moved");
        System.out.println("PASS RC125 Offset permanent base, 7 colors, legacy isolation, global reverse policy");
    }
}

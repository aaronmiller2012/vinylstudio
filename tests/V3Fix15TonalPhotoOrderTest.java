package com.halftonestudio;

import java.util.Arrays;

/** Host regression: preset RGB belongs to photo tone, not physical L1..Ln. */
public final class V3Fix15TonalPhotoOrderTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] unused) {
        // A deliberately scrambled palette and a nonidentity physical source order.
        int[] preset = {0xffd5ad93, 0xff361e16, 0xffac7052, 0xff68402d};
        int[] copy=preset.clone();
        int[] sorted=TonalPhotoPaletteOrder.sortDarkToLight(preset);
        check(Arrays.equals(copy,preset),"Sorting must not mutate the selected palette");
        for (int i=1;i<sorted.length;i++)
            check(ColorWorkspacePalette.lightness(sorted[i-1])<=ColorWorkspacePalette.lightness(sorted[i]),
                "Vinyl must be darkest-to-lightest by CIELAB L*");
        int[] existing=new int[8];existing[4]=0xff876543;
        int[] mapped=TonalPhotoPaletteOrder.mapToSource(existing,preset,4,0);
        for(int i=0;i<4;i++)check(mapped[i]==sorted[i],"Tonal source rank incorrectly assigned "+i);
        check(mapped[4]==existing[4],"Inactive saved colors modified");
        int[] physicalIds={2,0,3,1};
        int[] physical=new int[4];for(int i=0;i<4;i++)physical[i]=mapped[physicalIds[i]];
        check(physical[1]==sorted[0] && physical[2]==sorted[3],
            "L1..Ln must NOT determine photo luminance assignment");
        int[] hueMapped=TonalPhotoPaletteOrder.mapToSource(existing,preset,4,42);
        for(int i=0;i<4;i++){
            int reapply=PaletteHueShift.rotateOne(hueMapped[i],42);
            for(int shift:new int[]{0,8,16})
                check(Math.abs(((reapply>>>shift)&255)-((sorted[i]>>>shift)&255))<=1,
                    "Hue double rotation degraded RGB more than 1");
        }
        int[] reverse=PhysicalLayerColorReverse.reverse(mapped,physical,physicalIds,0);
        int[] reversedPhysical=new int[4];for(int i=0;i<4;i++)reversedPhysical[i]=reverse[physicalIds[i]];
        for(int i=0;i<4;i++)check(reversedPhysical[i]==physical[3-i],
            "Reverse must remain explicitly physical, not re-sort by lightness");
        int[] twice=PhysicalLayerColorReverse.reverse(reverse,reversedPhysical,physicalIds,0);
        check(Arrays.equals(Arrays.copyOf(mapped,4),Arrays.copyOf(twice,4)),
            "Reverse twice fails to restore the picked source palette");
        // Runtime engine: presets recolor only; no source ownership or geometry changes.
        final int w=60,h=60;int[] px=new int[w*h];
        int[] photo={0xff281c19,0xff6e4738,0xffa77058,0xffe2baa6};
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int region=(x<w/2?0:1)+(y<h/2?0:2);
            px[y*w+x]=photo[region];
        }
        MultilayerStencilEngine.Options opt=new MultilayerStencilEngine.Options();
        opt.sourceColorMode=true;opt.autoWhiteBackground=false;opt.layerCount=4;
        opt.widthMm=120;opt.outerSpacingMm=5;opt.registration=false;
        opt.minimumIslandPixels=1;opt.detail=.75f;opt.underlayTrapMm=0;
        MultilayerStencilEngine.Result auto=MultilayerStencilEngine.generate(px,w,h,opt);
        opt.colors=Arrays.copyOf(mapped,4);
        MultilayerStencilEngine.Result colored=MultilayerStencilEngine.generate(px,w,h,opt);
        check(auto.layers.size()==4 && colored.layers.size()==4,"Expected 4 physical layers");
        boolean nonidentity=false;
        for(int i=0;i<4;i++){
            MultilayerStencilEngine.Layer a=auto.layers.get(i),b=colored.layers.get(i);
            if(a.sourceIndex!=i)nonidentity=true;
            check(a.sourceIndex==b.sourceIndex,"Changing preset moved physical source identity");
            check(Arrays.equals(a.visibleMask,b.visibleMask),"Changing preset reassigned visible photo regions");
            check(Arrays.equals(a.mask,b.mask),"Changing preset altered cut geometry");
            check(b.color==sorted[b.sourceIndex],"Rendered color not assigned by photographic tone");
        }
        check(nonidentity,"Fixture must test nonidentity physical stack order");
        for(Runnable bad:new Runnable[]{
            ()->TonalPhotoPaletteOrder.mapToSource(new int[8],preset,4,181),
            ()->TonalPhotoPaletteOrder.sortDarkToLight(new int[]{0,0xff000000}),
            ()->TonalPhotoPaletteOrder.mapToSource(new int[8],preset,5,0)}){
            boolean failed=false;
            try{bad.run();}catch(IllegalArgumentException expected){failed=true;}
            check(failed,"Malformed palette input must fail closed");
        }
        System.out.println("PASS Fix15: CIELAB source rank, nonidentity physical stack, color-only geometry, Reverse, hue and invalid presets");
    }
}

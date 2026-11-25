package com.halftonestudio;

import java.util.Arrays;

/** Host regression: photographic foundations, structural slots, manual palette parity. */
public final class FoundationColorRC159Test {
    static void ok(boolean test,String message){if(!test)throw new AssertionError(message);}
    static int[] pixels(int[] colors,int[] frequencies){
        int n=0;for(int f:frequencies)n+=f;
        int[] result=new int[n];int p=0;
        for(int i=0;i<colors.length;i++)for(int j=0;j<frequencies[i];j++)result[p++]=colors[i];
        return result;
    }
    public static void main(String[] args){
        int black=0xff111119, violet=0xff37213d, brown=0xff62432f, tan=0xffb18468,
            peach=0xffe0b9a0, light=0xfff0d4bd, red=0xffd83040, medium=0xff8c6652;
        int[] colors={black,violet,brown,tan,peach,light,red,medium};
        int[] sampled=pixels(new int[]{black,violet,brown,tan,peach,light,red,medium},
            new int[]{12,18,235,125,315,160,8,127});
        int idx=FoundationColorPolicy.bestIndex(colors,colors.length,sampled,false);
        ok(idx==2,"Dark brown, not rare near-black/violet or pale skin, should be foundation; actual="+idx);
        int[] sorted=FoundationColorPolicy.anchorPhotoPalette(colors,8,sampled,false);
        ok(sorted[0]==brown,"Foundation anchor not dark brown");
        ok(Arrays.equals(Arrays.stream(sorted).sorted().toArray(),Arrays.stream(colors).sorted().toArray()),
            "Photo selection changed or discarded real swatches");
        ok(colors[0]==black&&colors[2]==brown,"Source palette mutated");
        int[] two=FoundationColorPolicy.anchorPhotoPalette(colors,2,sampled,false);
        ok(two[0]==violet||two[0]==black,"Two-sheet foundation left active physical slots");
        ok(FoundationColorPolicy.bestColor(colors,8,sampled,true)==light||
            FoundationColorPolicy.bestColor(colors,8,sampled,true)==peach,
            "Prismatic light foundation not chosen");
        ok(FoundationColorPolicy.newStylePolicy(VinylStyleSuite.CONFETTI)==FoundationColorPolicy.STRUCTURAL,
            "Confetti foundation missing");
        ok(FoundationColorPolicy.newStylePolicy(VinylStyleSuite.CHAINMAIL)==FoundationColorPolicy.STRUCTURAL,
            "Chainmail foundation missing");
        ok(FoundationColorPolicy.newStylePolicy(VinylStyleSuite.TESSELLATION_MOTIF)==FoundationColorPolicy.STRUCTURAL,
            "Tessellation foundation missing");
        ok(FoundationColorPolicy.newStylePolicy(VinylStyleSuite.PRISMATIC_DIAGONAL)==FoundationColorPolicy.STRUCTURAL,
            "Prismatic foundation missing");
        ok(FoundationColorPolicy.newStylePolicy(VinylStyleSuite.TSP)==FoundationColorPolicy.NONE,
            "Single line should not be forced to have a base");
        ok(FoundationColorPolicy.newStylePolicy(VinylStyleSuite.BANKNOTE)==FoundationColorPolicy.NONE,
            "Engraving should not be forced to have a base");
        for(int count:new int[]{2,3,6,8}){
            ok(NewStyleLayerPolicy.isStructuralLayer(VinylStyleSuite.TESSELLATION_MOTIF,0),
                "Tessellation structural base not protected");
            ok(!NewStyleLayerPolicy.canDelete(VinylStyleSuite.TESSELLATION_MOTIF,0,count),
                "Tessellation L1 was deletable");
            ok(!NewStyleLayerPolicy.canDelete(VinylStyleSuite.PRISMATIC_DIAGONAL,0,count),
                "Prismatic L1 was deletable");
        }
        int[] reversed=VinylPaletteMapping.forStyle(VinylStyleSuite.CHAINMAIL,6,0xffffffff,
            sorted,true,true);
        ok(reversed[0]==brown,"Reverse moved intrinsic Chainmail foundation");
        // No base palette is synthesized for explicitly edited manual swatches:
        int[] manual={0xffeec2a3,0xff1a1a1a,0xff335577,0xffaabbcc,0xffdcb987,0xff991144,0xff337755,0xffaa8844};
        int[] after=VinylPaletteMapping.forStyle(VinylStyleSuite.CHAINMAIL,8,0xffeeeeee,
            manual,false,true);
        ok(Arrays.equals(manual,after),"Manual physical color sequence was altered");
        int[] pixels=new int[84*100];
        for(int y=0;y<100;y++)for(int x=0;x<84;x++)pixels[y*84+x]=0xff000000|
            ((35+x*2)<<16)|((32+y*2)<<8)|(23+x);
        for(int count:new int[]{2,4,8}){
            TessellationMotifEngine.Options tess=new TessellationMotifEngine.Options();
            tess.pattern=TessellationMotifEngine.SQUARE;tess.layerCount=count;tess.detail=30;
            tess.palette=Arrays.copyOf(sorted,count);
            StyleKit.Result result=TessellationMotifEngine.generate(pixels,84,100,tess);
            ok(result.layers.size()==count,"Tessellation exceeded physical sheet count");
            ok(result.layers.get(0).name.contains("base"),"Tessellation L1 is not a structural base");
            ok(!result.layers.get(0).loops.isEmpty(),"Tessellation L1 did not contain filled silhouette");
        }
        System.out.println("PASS RC159 foundation scoring, tessellation 2–8 sheets, manual color stability");
    }
}

package com.halftonestudio;
import java.util.Arrays;
import java.util.ArrayList;
public final class V3PerceptualSwatchRepairTest {
    static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
    static final int[] GRAY={0xff000000,0xff222222,0xff444444,0xff666666,0xff888888,0xffaaaaaa,0xffcccccc,0xffffffff};
    static void desc(int[] colors,int n){for(int j=1;j<n;j++)ok(ColorWorkspacePalette.lightness(colors[j-1])>=ColorWorkspacePalette.lightness(colors[j]),"nested tone order");}
    public static void main(String[] args){
        for(int style:new int[]{VinylStyleSuite.TOPOGRAPHIC,VinylStyleSuite.REDUCTION_WOODCUT,VinylStyleSuite.POSTER_STENCIL})for(int count=2;count<=8;count++){
            int[] normal=VinylPaletteMapping.forStyle(style,count,0xff101010,GRAY,false,false);
            desc(normal,count);int[] original=GRAY.clone();
            int[] reverse=VinylPaletteMapping.forStyle(style,count,0xffffffff,GRAY,true,false);
            ok(Arrays.equals(GRAY,original),"preset mutation");
            ok(Arrays.equals(reverse,PaletteOrientation.reverseActive(normal,count,true,NewStyleLayerPolicy.isStructuralLayer(style,0))),"reverse parity");
        }
        int[] nine=Arrays.copyOf(GRAY,9);nine[8]=0xff123456;
        int[] poster=PerceptualLayerColorEngine.finishPreset(VinylStyleSuite.POSTER_STENCIL,nine,9,false);desc(poster,9);
        int[] rev=PerceptualLayerColorEngine.finishPreset(VinylStyleSuite.POSTER_STENCIL,nine,9,true);
        ok(rev[0]==poster[0]&&rev[8]==poster[1],"poster reverse all overlays");
        SavedVinylPaletteLibrary.Entry entry=new SavedVinylPaletteLibrary.Entry("9","Nine",nine,0);
        nine[8]=0;ok(entry.baseColors[8]==0xff123456,"ninth saved exactly");
        ok(NewStylePalettePresets.saved(entry.baseColors,0)[8]==0xff123456,"ninth restored exactly");
        ok(NewStylePalettePresets.saved(entry.baseColors,23)[8]==PaletteHueShift.rotateOne(0xff123456,23),"ninth hue");
        int[] p={0xff000000,0xff804020,0xffc09080,0xffffffff};
        PhotoSwatchAssociation a=new PhotoSwatchAssociation(p,4,null,null,null,PhotoSwatchAssociation.ORNAMENTS);
        ok(a.choose(p[3])==3,"exact unrestricted");ok(a.chooseNearRank(p[3],0,0)==0,"exact out of rank must not bypass restriction");
        ok(a.chooseNearRankCached(p[3],0)<=1,"rank cached must remain restricted");
        int[] dup={p[0],p[1],p[1],p[3]};
        PhotoSwatchAssociation b=new PhotoSwatchAssociation(dup,4,new boolean[]{true,false,true,true},null,null,PhotoSwatchAssociation.ORNAMENTS);
        ok(b.choose(p[1])==2,"duplicate disabled identity");
        int[] organic=VinylPaletteMapping.forStyle(VinylStyleSuite.QUILLING_RIBBONS,5,0xffffffff,GRAY,false,false);
        int[] organicRev=VinylPaletteMapping.forStyle(VinylStyleSuite.QUILLING_RIBBONS,5,0xffffffff,GRAY,true,false);
        ok(organicRev[0]==organic[0]&&organicRev[1]==organic[4]&&organicRev[5]==organic[5],"protected base + dormant slots");
        int[] samples={p[1],p[1],p[1]};ok(PerceptualLayerColorEngine.strandColor(samples,3,a)==1,"photo strand selection");
        int w=80,h=80;int[] image=new int[w*h];Arrays.fill(image,p[1]);
        ExpandedPortraitStylesEngine.Options o=new ExpandedPortraitStylesEngine.Options();
        o.style=ExpandedPortraitStylesEngine.WOVEN_RIBBON;o.palette=p;o.widthMm=101.6f;
        StyleKit.Result woven=ExpandedPortraitStylesEngine.generate(image,w,h,o);
        ok(!woven.layers.isEmpty(),"woven image empty");
        for(StyleKit.Layer l:woven.layers)if(!l.loops.isEmpty())ok(l.color==p[1],"woven unrelated color on uniform photo");
        PatternPortraitTrioEngine.Options c=new PatternPortraitTrioEngine.Options();c.kind=PatternPortraitTrioEngine.CONFETTI;c.layers=4;c.palette=p;c.widthMm=101.6f;
        StyleKit.Source src=new StyleKit.Source(image,w,h,null,c.widthMm);
        FallenConfettiEngine.Plan plan=FallenConfettiEngine.sample(src,c,p);
        ok(plan.clustered>0,"confetti fixture requires clustered pieces");
        int[] population=new int[p.length];
        for(FallenConfettiEngine.Piece piece:plan.pieces)population[piece.slot]++;
        ok(population[1]>=plan.pieces.size()-8&&population[2]==4&&population[3]==4,"only minimum coverage may override exact photo color");
        System.out.println("PASS nested tone order, structural reverse, ninth saved swatch, ranked color matching, Woven Ribbon and clustered Confetti");
    }
}

package com.halftonestudio;

/** Independent fish-scale shingle style. All legacy modes remain unchanged. */
public final class FishScaleMosaicEngine {
    private FishScaleMosaicEngine() { }
    public static LayeredCellPortraitEngine.Result generate(
            int[] pixels,int width,int height,LayeredCellPortraitEngine.Options settings){
        if(settings==null)throw new IllegalArgumentException("Fish scale options required");
        LayeredCellPortraitEngine.Options o=settings.copy();
        o.fishScales=true;o.bottleCaps=false;o.offsetRows=false;
        while(o.columns>4){
            float pitch=width/(float)o.columns;
            int rows=(int)Math.ceil(height/(pitch*.69f))+3;
            if((long)(o.columns+2)*rows<=LayeredCellPortraitEngine.MAX_DETAIL_CELLS)break;
            o.columns--;
        }
        return LayeredCellPortraitEngine.generate(pixels,width,height,o);
    }
}

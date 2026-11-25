package com.halftonestudio;

/** Independent bottle-cap style; legacy cell and offset-cell settings are untouched. */
public final class BottleCapMosaicEngine {
    private BottleCapMosaicEngine(){}
    public static LayeredCellPortraitEngine.Result generate(
            int[] pixels,int width,int height,LayeredCellPortraitEngine.Options settings){
        if(settings==null)throw new IllegalArgumentException("Bottle cap options required");
        LayeredCellPortraitEngine.Options o=settings.copy();
        o.bottleCaps=true;
        o.offsetRows=false;
        // Respect the Cricut path budget by reducing draft density when needed.
        while(o.columns>4){
            float pitch=width/(float)o.columns;
            int rows=(int)Math.ceil(height/(pitch*.8660254f))+1;
            if((long)o.columns*rows+rows/2<=LayeredCellPortraitEngine.MAX_DETAIL_CELLS)break;
            o.columns--;
        }
        return LayeredCellPortraitEngine.generate(pixels,width,height,o);
    }
}

package com.halftonestudio;
public final class CutSetupTest {
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    static int[] source(int w,int h){int[] px=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)px[y*w+x]=0xff000000|((x*255/w)<<16)|((y*255/h)<<8)|((x+y)*127/(w+h));return px;}
    public static void main(String[] args){
        check(VinylEngine.insideCutBoundary(VinylEngine.Shape.circle(50,50,10),100,100,5,true),"center circle inside");
        check(!VinylEngine.insideCutBoundary(VinylEngine.Shape.circle(88,50,10),100,100,5,true),"outer-edge circle excluded");
        check(VinylEngine.insideCutBoundary(VinylEngine.Shape.polygon(16,16,80,16,80,80,16,80),100,100,5,false),"rect polygon retained");
        check(!VinylEngine.insideCutBoundary(VinylEngine.Shape.polygon(1,16,80,16,80,80,1,80),100,100,5,false),"rect inset excludes boundary");
        check(!VinylEngine.insideCutBoundary(VinylEngine.Shape.polygon(7,7,45,7,45,45,7,45),100,100,5,true),"circle crop rejects rectangular corners");
        VinylEngine.Settings s=VinylEngine.Settings.defaults();s.widthMm=100;s.marginMm=6;s.outerSpacingMm=17;s.registration=false;
        VinylEngine.Result r=VinylEngine.generate(source(48,48),48,48,s);
        check(r.padMm>=17&&r.boardW>=134,"outside artboard spacing");
        for(java.util.List<VinylEngine.Shape> shapes:r.shapes)for(VinylEngine.Shape shape:shapes)
            check(VinylEngine.insideCutBoundary(shape,r.settings.widthMm,r.heightMm,r.settings.marginMm,false),"rect cut escapes inset");
        s.circleCrop=true;r=VinylEngine.generate(source(48,48),48,48,s);
        for(java.util.List<VinylEngine.Shape> shapes:r.shapes)for(VinylEngine.Shape shape:shapes)
            check(VinylEngine.insideCutBoundary(shape,r.settings.widthMm,r.heightMm,r.settings.marginMm,true),"circular cut escapes inset");
        MultilayerStencilEngine.Options o=new MultilayerStencilEngine.Options();o.sourceColorMode=true;o.layerCount=4;
        o.widthMm=100;o.cutInsetMm=12;o.outerSpacingMm=20;o.registration=false;o.minimumIslandPixels=0;
        MultilayerStencilEngine.Result t=MultilayerStencilEngine.generate(source(32,32),32,32,o);
        for(MultilayerStencilEngine.Layer layer:t.layers)for(int y=0;y<32;y++)for(int x=0;x<32;x++)
            if(layer.mask[y*32+x])check((x+.5f)*100/32>=12&&(x+.5f)*100/32<=88&&(y+.5f)*100/32>=12&&(y+.5f)*100/32<=88,"tonal layer escapes inset");
        check(t.svg(0).contains("140.0000mm"),"tonal artboard padding");
        TessellationEngine.Options m=new TessellationEngine.Options();m.widthMm=100;m.outerSpacingMm=16;m.layerCount=2;m.detail=10;m.registration=false;
        TessellationEngine.Result tx=TessellationEngine.generate(source(42,42),42,42,m);
        check(tx.svg(0).contains("132.000mm"),"tessellation SVG external spacing");
        System.out.println("PASS: cut exclusion rectangle/circle, real shape filtering, outer artboard, stencil masks, tessellation SVG");
    }
}

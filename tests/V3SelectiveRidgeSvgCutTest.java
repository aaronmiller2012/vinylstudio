package com.halftonestudio;
/** Export uses exactly the final polished polygons, not a bitmap-only preview effect. */
public final class V3SelectiveRidgeSvgCutTest {
    public static void main(String[] args)throws Exception{
        for(int id:new int[]{VinylStyleSuite.FINGERPRINT,VinylStyleSuite.WOODGRAIN}){
            for(float width:new float[]{101.6f,279.4f}){
                int[] image=V3SelectiveRidgePolishTest.portrait(144,112);
                VinylStyleSuite.Config cfg=new VinylStyleSuite.Config();
                cfg.style=id;cfg.widthMm=width;cfg.detail=42;cfg.colorCount=4;
                cfg.minimumMm=.254f;cfg.gapMm=.24f;cfg.weightMm=.70f;cfg.draft=false;
                cfg.allowed=V3SelectiveRidgePolishTest.snip(144,112);
                StyleKit.Result page=VinylStyleSuite.generate(image,144,112,cfg);
                if(page.layers.isEmpty())throw new AssertionError("Empty output style "+id);
                for(int layer=0;layer<page.layers.size();layer++){
                    String svg=NewStyleExport.layerSvg(page,layer,false,0,3f,2f,null,.254f);
                    CutSvgCompatibility.verify(svg,OutputDeviceProfile.GENERIC_SVG);
                }
                System.out.printf("PASS polished SVG style=%d widthMm=%.1f pts=%d%n",id,width,page.totalPoints());
            }
        }
        System.out.println("PASS selective ridge SVG export parity");
    }
}

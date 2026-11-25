package com.halftonestudio;
import java.util.*;
/** Independent tests of flat multicolor stencil paths for the new workflow. */
public final class StencilModeTest {
    static void ok(boolean good,String message){if(!good)throw new AssertionError(message);}
    private static int[] photo(int n,int width,int height,int[] colors){
        int[] px=new int[width*height];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int layer=Math.min(n-1,x*n/width);px[y*width+x]=colors[layer];
        }
        return px;
    }
    public static void main(String[] args){
        for(int count:new int[]{2,4,8}){
            VinylEngine.Settings settings=VinylEngine.Settings.defaults();
            settings.layers.clear();
            int[] palette=VinylPalettes.get(0,count);
            for(int i=0;i<count;i++)settings.layers.add(new VinylEngine.Layer("Stencil "+(i+1),palette[i]));
            settings.mode=VinylEngine.STENCIL;
            settings.dither=VinylEngine.BAYER; // ignored by stencil: never insert print-like dithering.
            settings.stencilCleanup=1;
            settings.widthMm=110;settings.masterSpacingMm=3.5f;
            settings.minimumMm=.35f;settings.marginMm=3;
            int w=80,h=54;
            VinylEngine.Result result=VinylEngine.generate(photo(count,w,h,palette),w,h,settings);
            ok(result.shapes.size()==count,"correct layer count");
            ok(result.settings.mode==VinylEngine.STENCIL,"stencil mode persisted");
            for(int i=0;i<count;i++){
                ok(!result.shapes.get(i).isEmpty(),"color "+i+" must have cut regions");
                for(VinylEngine.Shape sh:result.shapes.get(i))ok(!sh.circle&&sh.xy.length>=8,"flat stencil emits filled connected vector contours");
                String svg=VinylEngine.svg(result,i);
                ok(!svg.contains("clipPath")&&!svg.contains("<mask"),"SVG paths cut without unsupported clipping");
                ok(VinylEngine.svgElementsForLayer(result,i)<=5000,"independent Cricut limit");
            }
            ok(VinylEngine.validateExport(result)==null,"stencil cut/mark validation");
            // Generate a second time and ensure deterministic vector outputs.
            VinylEngine.Result again=VinylEngine.generate(photo(count,w,h,palette),w,h,settings);
            for(int i=0;i<count;i++)ok(VinylEngine.svg(result,i).equals(VinylEngine.svg(again,i)),"deterministic stencil SVG");
        }
        System.out.println("PASS: flat multicolor stencils with 2, 4, 8 separately cuttable colors; deterministic SVG and registration");
    }
}

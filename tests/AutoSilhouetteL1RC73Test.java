package com.halftonestudio;
import java.util.Arrays;
public final class AutoSilhouetteL1RC73Test {
    static void ok(boolean success,String message){if(!success)throw new AssertionError(message);}
    public static void main(String[] args){
        ok(AutoSilhouetteUnderlay.DEFAULT_COLOR==0xffffffff,"new projects should default to white base");
        int[] pixels=new int[140*160];Arrays.fill(pixels,0xffa0b7c0);
        VinylEngine.Settings s=VinylEngine.Settings.defaults();s.mode=VinylEngine.SPOT;
        s.widthMm=130;s.minimumMm=.8f;s.registration=true;
        s.snips=new SnipMask();
        s.snips.add(new SnipMask.Operation(SnipMask.KEEP_INSIDE,.05f,.08f,.95f,.08f,.95f,.92f,.05f,.92f));
        s.snips.add(new SnipMask.Operation(SnipMask.REMOVE_INSIDE,.30f,.34f,.62f,.34f,.62f,.65f,.30f,.65f));
        int original=s.layers.size();
        AutoSilhouetteUnderlay.Output base=AutoSilhouetteUnderlay.make(s,pixels,140,160,AutoSilhouetteUnderlay.DEFAULT_COLOR);
        ok(base!=null,"white base must generate");
        ok(s.layers.size()==original,"detail sheets must remain stored");
        ok(base.result.settings.layers.size()==1,"only one actual base SVG layer");
        ok(base.result.shapes.size()==1&&base.result.shapes.get(0).size()==1,"one compound filled shape");
        ok(base.cutContours()==2,"outer contour and internal Snip hole");
        String svg=base.svg();ok(svg.contains("#FFFFFF"),"white base svg");
        ok(svg.contains("fill-rule=\"evenodd\""),"valid closed openings");
        ok(!svg.contains("<clipPath")&&!svg.contains("<text"),"Cricut compatible");
        ok(VinylEngine.validateMarks(base.result)==null,"registration positions legal");
        System.out.println("PASS RC73 white one-shape filled L1, 2 blade contours, preserved detail state and registration");
    }
}

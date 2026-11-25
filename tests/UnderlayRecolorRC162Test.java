package com.halftonestudio;
import java.util.Arrays;

/** An independent solid Snip foundation can be recolored without reconstructing cut geometry. */
public final class UnderlayRecolorRC162Test {
    private static void need(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        int[] rgba=new int[96*120];Arrays.fill(rgba,0xff765c4d);
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.widthMm=140f;s.mode=VinylEngine.SPOT;s.registration=true;
        s.snips=new SnipMask();
        s.snips.add(new SnipMask.Operation(SnipMask.KEEP_INSIDE,.10f,.08f,.90f,.08f,.90f,.92f,.10f,.92f));
        AutoSilhouetteUnderlay.Output original=AutoSilhouetteUnderlay.make(s,rgba,96,120,0xff563d2b);
        need(original!=null,"solid base was not generated");
        AutoSilhouetteUnderlay.Output newColor=original.recolor(0xffd0ac88);
        need(newColor.result==original.result,"base geometry unexpectedly regenerated");
        need(newColor.shape==original.shape,"base contour identity changed");
        need(newColor.cutContours()==original.cutContours(),"base cut count changed");
        need(newColor.color==0xffd0ac88,"recolor did not change the swatch");
        need(newColor.svg().contains("#D0AC88"),"recolored SVG not updated");
        need(!newColor.svg().equals(original.svg()),"recolor should change fill only");
        need(VinylEngine.validateMarks(newColor.result)==null,"registration no longer valid");
        need(original.recolor(original.color)==original,"unchanged color should be reused");
        System.out.println("PASS RC162 solid Snip recolor reuses geometry and keeps SVG registration/cuts intact");
    }
}

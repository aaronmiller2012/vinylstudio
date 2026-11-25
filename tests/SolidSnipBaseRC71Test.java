package com.halftonestudio;

import java.util.*;

/** Opt-in solid L1 vinyl must not be confused with the black image channel. */
public final class SolidSnipBaseRC71Test {
    private static void must(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    private static int[] image(int w,int h){int[] a=new int[w*h];Arrays.fill(a,0xffae927a);return a;}
    private static VinylEngine.Settings baseCase(){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.widthMm=100;s.marginMm=1;s.registration=false;s.paper=0xff18191e;
        s.snips=new SnipMask();
        s.snips.add(new SnipMask.Operation(SnipMask.KEEP_INSIDE,.12f,.12f,.88f,.12f,.88f,.88f,.12f,.88f));
        s.snips.add(new SnipMask.Operation(SnipMask.REMOVE_INSIDE,.36f,.36f,.64f,.36f,.64f,.64f,.36f,.64f));
        VinylEngine.Layer base=new VinylEngine.Layer("Solid Snip background",0xffe8c7ad);
        base.silhouetteBase=true;
        s.layers.add(0,base);
        s.layers.get(4).enabled=false;
        s.layers.get(4).autoDisabledForBase=true;
        VinylEngine.CmykExtendedStack.canonicalize(s.layers);
        return s;
    }
    public static void main(String[] args){
        VinylEngine.Settings s=baseCase();
        must(s.layers.size()==5,"four CMYK channels plus real base");
        must(s.layers.get(0).silhouetteBase,"base must stay at physical bottom");
        must(s.layers.get(4).cmykChannel==3,"K channel preserved");
        VinylEngine.Result r=VinylEngine.generate(image(120,120),120,120,s);
        must(r.shapes.get(0).size()==1,"one compound filled background");
        VinylEngine.Shape base=r.shapes.get(0).get(0);
        must(base.contours!=null&&base.contours.length>=2,"outer ring and removed hole");
        String svg=VinylEngine.svg(r,0);
        must(svg.contains("fill-rule=\"evenodd\""),"filled even-odd geometry");
        must(!svg.contains("clipPath")&&!svg.contains("<text"),"no unsupported SVG clipping or text");
        must(!VinylEngine.cutLayer(r,4),"suppressed K not exported");
        must(VinylEngine.cutLayer(r,0),"visible colored base exported");
        int[] cuts=LayerCutCounts.standard(r);
        must(cuts[0]>=2,"outer and hole are physical blade contours");
        must(cuts[4]==0,"inactive black no longer reports 621 cuts");
        must(VinylEngine.validateExport(r)==null,"registration/cut preflight");
        VinylEngine.Settings restored=s.copy();
        must(restored.layers.get(0).silhouetteBase && restored.layers.get(4).autoDisabledForBase,"copy retains role");
        System.out.println("PASS RC71 filled L1, real Snip hole, 3 CMY detail, disabled K, SVG and preview geometry");
    }
}

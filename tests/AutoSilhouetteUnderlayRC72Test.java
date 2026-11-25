package com.halftonestudio;

import java.util.Arrays;

/** The automatic base is one ADDITIONAL physical sheet; detail layers are immutable. */
public final class AutoSilhouetteUnderlayRC72Test {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static int[] photo(){int[] p=new int[160*160];Arrays.fill(p,0xffb28b79);return p;}
    static VinylEngine.Settings settings(int detail){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.widthMm=120;s.marginMm=1;s.paper=0xff16191c;
        s.snipFillMode=SnipBackground.BLACK;
        s.registration=true;
        s.snips=new SnipMask();
        s.snips.add(new SnipMask.Operation(SnipMask.KEEP_INSIDE,.12f,.12f,.88f,.12f,.88f,.88f,.12f,.88f));
        s.snips.add(new SnipMask.Operation(SnipMask.REMOVE_INSIDE,.38f,.38f,.62f,.38f,.62f,.62f,.38f,.62f));
        s.mode=VinylEngine.SPOT;
        while(s.layers.size()<detail){
            VinylEngine.Layer l=new VinylEngine.Layer("Color "+s.layers.size(),0xff8495ae);
            l.cmykChannel=-1;s.layers.add(l);
        }
        while(s.layers.size()>detail)s.layers.remove(s.layers.size()-1);
        return s;
    }
    static void check(int n){
        VinylEngine.Settings s=settings(n);
        int[] before=new int[n];for(int i=0;i<n;i++)before[i]=s.layers.get(i).color;
        require(AutoSilhouetteUnderlay.standardEligible(s),"standard shape/line eligible");
        AutoSilhouetteUnderlay.Output out=AutoSilhouetteUnderlay.make(s,photo(),160,160,0xffe5b99e);
        require(out!=null,"actual base");
        require(s.layers.size()==n,"should not consume selected detail slots");
        for(int i=0;i<n;i++)require(s.layers.get(i).color==before[i],"swatch unchanged");
        require(s.layers.size()+out.result.settings.layers.size()==n+1,"one additional physical sheet");
        require(out.result.settings.layers.size()==1,"one independent cut sheet");
        require(out.cutContours()>=2,"outer ring and removed Snip interior");
        String svg=out.svg();
        require(svg.contains("fill-rule=\"evenodd\""),"real closed compound cut");
        require(svg.contains("#E5B99E"),"chosen physical base color");
        require(!svg.contains("<clipPath")&&!svg.contains("<text"),"Cricut supported geometry");
        require(!svg.contains("<image"),"no raster cuts");
        require(VinylEngine.validateMarks(out.result)==null,"base marks outside cut");
        require(VinylEngine.svgElementsForLayer(out.result,0)<=5000,"path budget");
        // Old manual RC71 bases remain one sheet and must not get a duplicate.
        VinylEngine.Layer legacy=new VinylEngine.Layer("Legacy base",0xffe5b99e);legacy.silhouetteBase=true;
        s.layers.add(0,legacy);
        require(!AutoSilhouetteUnderlay.standardEligible(s),"never duplicate saved base");
        System.out.println("RC72: "+n+" detail colors + 1 Snip underlay, "+out.cutContours()+" contours");
    }
    public static void main(String[] args){
        for(int n:new int[]{2,4,6,8})check(n);
        VinylEngine.Settings s=settings(4);s.snips=new SnipMask();
        require(!AutoSilhouetteUnderlay.standardEligible(s),"do not invent silhouette without Snip");
        require(AutoSilhouetteUnderlay.specialtyEligible(VinylStyleSuite.LINOCUT,0,false),"linocut");
        require(AutoSilhouetteUnderlay.specialtyEligible(VinylStyleSuite.RIDGE,1,false),"ridge");
        require(AutoSilhouetteUnderlay.specialtyEligible(VinylStyleSuite.TOPOGRAPHIC,1,false),"topo contours");
        require(!AutoSilhouetteUnderlay.specialtyEligible(VinylStyleSuite.TOPOGRAPHIC,0,false),"filled topo already has base");
        require(!AutoSilhouetteUnderlay.specialtyEligible(VinylStyleSuite.GLASS,0,false),"glass already has lead");
        require(!AutoSilhouetteUnderlay.specialtyEligible(VinylStyleSuite.TEXT,1,false),"legacy text already has base");
        require(AutoSilhouetteUnderlay.specialtyEligible(-1,0,true),"stipple/voronoi/grid");
        System.out.println("PASS RC72 automatic base, 2–8 detail slots, hole/cut geometry, color identity and specialty routing");
    }
}

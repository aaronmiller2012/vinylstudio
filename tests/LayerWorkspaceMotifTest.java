package com.halftonestudio;
import java.util.Arrays;
public final class LayerWorkspaceMotifTest {
    private static void need(boolean c,String s){if(!c)throw new AssertionError(s);}
    public static void main(String[] args){
        int w=200,h=250;int[] src=new int[w*h];
        int[] palette={0xff171923,0xff624353,0xffac7163,0xffedb692,0xffeedccc,0xff4a7fa8,0xffd1b64a,0xff746aa5};
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double face=Math.max(0,1-Math.pow((x-w*.52)/(w*.36),2)-Math.pow((y-h*.52)/(h*.46),2));
            int r=Math.max(0,Math.min(255,(int)(36+160*face+20*Math.sin(x*.08))));
            int g=Math.max(0,Math.min(255,(int)(42+96*face+24*Math.cos(y*.09))));
            int b=Math.max(0,Math.min(255,(int)(54+65*face+20*Math.sin((x+y)*.06))));
            if(y>h*.35&&y<h*.39&&x>w*.33&&x<w*.70)r=g=b=20;
            src[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        LayeredCellPortraitEngine.Options o=new LayeredCellPortraitEngine.Options();
        o.layerCount=5;o.colors=palette;o.columns=18;o.widthMm=160f;o.family=LayeredCellPortraitEngine.ELLIPSE;
        o.motifDepth=2;o.minFeatureMm=.5f;
        LayeredCellPortraitEngine.Result base=LayeredCellPortraitEngine.generate(src,w,h,o);
        o.layerFamilies=new int[]{LayeredCellPortraitEngine.HEXAGON,LayeredCellPortraitEngine.SOFT_STAR,LayeredCellPortraitEngine.DIAMOND,LayeredCellPortraitEngine.OCTAGON,-1};
        LayeredCellPortraitEngine.Result changed=LayeredCellPortraitEngine.generate(src,w,h,o);
        boolean altered=false;for(int i=0;i<o.layerCount;i++)if(!base.svg(i).equals(changed.svg(i)))altered=true;
        need(altered,"Cell motif overrides must alter actual SVG paths, not only UI labels");
        LayeredCellPortraitEngine.Result again=LayeredCellPortraitEngine.generate(src,w,h,o);
        for(int i=0;i<o.layerCount;i++){
            need(changed.svg(i).equals(again.svg(i)),"Deterministic per-layer shapes");
            if(changed.pieceCounts[i]>0)LayeredCellExportSafety.validate(changed,i,changed.svg(i));
        }
        o.layerFamilies[0]=LayeredCellPortraitEngine.SOFT_STAR;
        need(changed.options.layerFamilies[0]==LayeredCellPortraitEngine.HEXAGON,"Result retains defensive snapshot");
        boolean bad=false;try{
            LayeredCellPortraitEngine.Options invalid=o.copy();invalid.layerFamilies=new int[]{90,0,0,0,0};
            LayeredCellPortraitEngine.generate(src,w,h,invalid);
        }catch(IllegalArgumentException ex){bad=true;}
        need(bad,"Unknown shape rejected");
        System.out.println("PASS: per-color photo-dominant cell motifs change actual SVG, are deterministic, safely validated and snapshot-isolated");
    }
}

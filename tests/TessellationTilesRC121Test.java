package com.halftonestudio;
import java.util.Arrays;

/** RC121 regression: Tessellation Portrait uses colored tiles, never decorative motifs. */
public final class TessellationTilesRC121Test {
    private static void need(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static int count(StyleKit.Result out){int n=0;for(StyleKit.Layer l:out.layers)n+=l.loops.size();return n;}
    public static void main(String[] ignored){
        final int w=76,h=84;
        int[] src=new int[w*h];
        boolean[] mask=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int r=x*255/(w-1),g=y*255/(h-1),b=128;
            src[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
            mask[y*w+x]=x>3&&y>3&&x<w-4&&y<h-4;
        }
        for(int pattern=0;pattern<TessellationMotifEngine.RUNNING_BOND;pattern++){
            TessellationMotifEngine.Options o=new TessellationMotifEngine.Options();
            o.widthMm=152.4f;o.pattern=pattern;o.detail=45;o.allowed=mask;
            StyleKit.Result base=TessellationMotifEngine.generate(src,w,h,o);
            need(base.layers.size()==8,"Expected eight physical sheets");
            need(count(base)>20,"No colored tile geometry");
            need(base.summary.contains("photo-colored tiles"),"Motifs remain in summary");
            need(base.layers.get(0).name.contains("base"),"Missing solid substrate");
            int colored=0;
            for(int i=1;i<base.layers.size();i++){
                need(!base.layers.get(i).name.contains("detail"),"Decorative sheet retained");
                colored+=base.layers.get(i).loops.size();
            }
            need(colored>20,"No colored tessellation cells");
            String frozen=StyleKit.toSvg(base,false);
            o.extraMotifs=true;o.motif=7;
            StyleKit.Result legacy=TessellationMotifEngine.generate(src,w,h,o);
            need(frozen.equals(StyleKit.toSvg(legacy,false)),
                "Saved legacy motif options changed tessellation cut geometry");
            o.solidBase=false;
            StyleKit.Result noBase=TessellationMotifEngine.generate(src,w,h,o);
            need(noBase.layers.get(0).name.startsWith("tessellation-color-"),
                "Disabled silhouette produced a hidden base");
            need(noBase.layers.size()==8,"Missing color sheet after disabling base");
            for(StyleKit.Layer layer:legacy.layers){
                need(layer.lines.isEmpty(),"Open cut path");
                need(layer.loops.size()<=4800,"Cricut path limit exceeded");
            }
        }
        need(VinylStyleSuite.restoredStyleId(8)==-1,"Retired style 8 reused");
        need(VinylStyleSuite.restoredStyleId(10)==10,"Tessellation style ID changed");
        need("Tessellation Portrait".equals(VinylStyleSuite.NAMES[10]),"Old motif label still visible");
        System.out.println("PASS RC121 photo-colored tessellations, retired motifs, eight sheets, masks and IDs");
    }
}

package com.halftonestudio;

import java.util.Arrays;

/** High-detail stacked mode: host JVM, no Android dependencies. */
public final class LayeredCellHighDetailRC116Test {
    static void need(boolean okay,String msg){if(!okay)throw new AssertionError(msg);}
    static int[] image(int w,int h){
        int[] pix=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double dx=(x-w*.5)/(w*.44),dy=(y-h*.48)/(h*.46);
            double f=Math.min(1.,Math.sqrt(dx*dx+dy*dy));
            int v=(int)(210-145*(1-f));
            if(y>h*.32&&y<h*.40&&((x>w*.25&&x<w*.44)||(x>w*.56&&x<w*.75)))v=20;
            if(x>w*.46&&x<w*.55&&y>h*.43&&y<h*.62)v=38;
            if(y>h*.70&&y<h*.75&&x>w*.32&&x<w*.69)v=28;
            int r=Math.min(255,Math.max(0,v+25)),g=Math.max(0,v-4),b=Math.max(0,v-24);
            pix[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        return pix;
    }
    static boolean[] mask(int w,int h){
        boolean[] mask=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double dx=(x-w*.5)/(w*.45),dy=(y-h*.50)/(h*.46);
            mask[y*w+x]=dx*dx+dy*dy<1 && !(x>w*.49&&x<w*.54&&y>h*.22&&y<h*.26);
        }
        return mask;
    }
    public static void main(String[] args){
        int w=160,h=200;int[] pixels=image(w,h);boolean[] allowed=mask(w,h);
        LayeredCellPortraitEngine.Options o=new LayeredCellPortraitEngine.Options();
        o.widthMm=152.4f;o.columns=40;o.photoDetail=1f;o.ornamentation=.94f;
        o.layerCount=8;o.motifDepth=3;o.minFeatureMm=.30f;o.gapMm=.18f;o.seamGapMm=.06f;
        o.colors=new int[]{0xff351b19,0xff7d4632,0xffad765e,0xffd5a792,0xffedd1b3,
            0xff573129,0xffd4b074,0xff171212};
        o.allowedPixels=allowed;o.solidBase=true;o.detailMotifOverlay=true;
        o.registration=false;
        LayeredCellPortraitEngine.Result a=LayeredCellPortraitEngine.generate(pixels,w,h,o);
        need(a.columns==40 && a.columns*a.rows>LayeredCellPortraitEngine.MAX_CELLS,
            "High-detail mode not denser than legacy");
        need(a.pieceCounts[0]>=1,"Missing full silhouette base");
        need(a.pieceCounts[7]>0,"Feature motif sheet is empty");
        int base=0;for(LayeredCellPortraitEngine.Piece piece:a.pieces)if(piece.layer==0){
            need(piece.cell==-1,"Base sheet still has independent cell cuts");
            base++;
        }
        need(base<20,"Base silhouette fragmented into too many pieces");
        for(int i=0;i<o.layerCount;i++)if(a.pieceCounts[i]>0){
            String svg=a.svg(i);LayeredCellExportSafety.validate(a,i,svg);
            need(a.contourCounts[i]<=4800,"Cricut contour ceiling");
        }
        LayeredCellPortraitEngine.Result b=LayeredCellPortraitEngine.generate(pixels,w,h,o);
        for(int i=0;i<8;i++)if(a.pieceCounts[i]>0)
            need(a.svg(i).equals(b.svg(i)),"Nondeterministic SVG "+i);
        need(a.options.solidBase&&a.options.detailMotifOverlay,"Option snapshot missing");
        LayeredCellPortraitEngine.Options classic=o.copy();
        classic.solidBase=false;classic.detailMotifOverlay=false;classic.columns=26;
        LayeredCellPortraitEngine.Result legacy=LayeredCellPortraitEngine.generate(pixels,w,h,classic);
        need(legacy.pieces.stream().noneMatch(p->p.cell==-1),"Legacy projects changed to solid base");
        need(LayeredCellReleaseProfile.safeColumns(40,w,h,152.4f,.3f,.18f,true)==40,
            "High detail preflight did not retain 40 columns");
        need(LayeredCellReleaseProfile.safeColumns(40,w,h,152.4f,.3f,.18f,false)<40,
            "Legacy preflight cap unexpectedly changed");
        System.out.println("PASS RC116 stacked high detail, 8 layers, " +
            a.pieceCounts[7]+" accent motifs, "+base+" silhouette pieces, mask, SVG, legacy parity");
    }
}

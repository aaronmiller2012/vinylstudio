package com.halftonestudio;

import java.util.Arrays;

/** Host regression: new scales are cuttable, Snip-safe, and isolated from RC118. */
public final class FishScaleRC119Test {
    private static void ok(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private static final int W=126,H=144;
    private static int[] photo(){
        int[] p=new int[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            int r=Math.min(255,35+2*x),g=Math.min(255,40+2*y),b=Math.min(255,15+x+y);
            p[y*W+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        return p;
    }
    private static boolean[] mask(){
        boolean[] out=new boolean[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            float dx=(x-63)/54f,dy=(y-72)/64f;
            out[y*W+x]=dx*dx+dy*dy<1f;
        }
        return out;
    }
    public static void main(String[] args) {
        int[] photo=photo();
        LayeredCellPortraitEngine.Options opt=new LayeredCellPortraitEngine.Options();
        opt.widthMm=152.4f;opt.columns=27;opt.gapMm=.28f;opt.minFeatureMm=.3f;
        opt.layerCount=8;opt.photoDetail=1;opt.registration=false;
        opt.solidBase=false;opt.detailMotifOverlay=true;opt.allowedPixels=mask();
        LayeredCellPortraitEngine.Result result=FishScaleMosaicEngine.generate(photo,W,H,opt);
        ok(result.options.fishScales,"Fish scale mode not set");
        ok(!opt.fishScales,"Input settings mutated");
        ok(result.pieces.size()>100,"Too few scales");
        ok(result.pieceCounts[7]>10,"Highlight layer missing");
        boolean scalloped=false;
        for(LayeredCellPortraitEngine.Piece piece:result.pieces){
            ok(piece.layer>=0&&piece.layer<8,"Invalid layer slot");
            for(int i=0;i<piece.outer.length;i+=2){
                float x=piece.outer[i],y=piece.outer[i+1];
                ok(Float.isFinite(x)&&Float.isFinite(y),"Nonfinite geometry");
                ok(x>=0&&x<=W&&y>=0&&y<=H,"Path outside page");
            }
            if(piece.layer!=7&&piece.outer.length>32)scalloped=true;
        }
        ok(scalloped,"Scalloped scale outlines missing");
        for(int i=0;i<8;i++){
            ok(result.contourCounts[i]<4800,"Cricut contour limit");
            if(result.contourCounts[i]>0)LayeredCellExportSafety.validate(result,i,result.svg(i));
        }
        String sig=result.svg(0)+result.svg(7);
        ok(sig.equals(FishScaleMosaicEngine.generate(photo,W,H,opt).svg(0)+
                FishScaleMosaicEngine.generate(photo,W,H,opt).svg(7)),"Non-deterministic scales");
        LayeredCellPortraitEngine.Options solid=opt.copy();solid.solidBase=true;
        ok(FishScaleMosaicEngine.generate(photo,W,H,solid).pieceCounts[0]>0,"Silhouette missing");
        LayeredCellPortraitEngine.Options plain=opt.copy();plain.allowedPixels=null;
        plain.detailMotifOverlay=false;plain.columns=12;
        LayeredCellPortraitEngine.Result full=FishScaleMosaicEngine.generate(photo,W,H,plain);
        ok(full.rows>4,"No rows");
        // The new profile crosses nominal row bands, but adjacent rows share
        // one mathematical scallop boundary separated by the physical weed gap.
        // Check that the resulting body polygons have no same-sheet overlap.
        for(LayeredCellPortraitEngine.Piece a:full.pieces){
            if(a.cell<0)continue;
            for(int i=0;i<a.outer.length;i+=2){
                float x=a.outer[i],y=a.outer[i+1];
                ok(x>=-0.001f&&x<=W+0.001f&&y>=-0.001f&&y<=H+0.001f,"Fish scale escapes artwork cell="+a.cell+" coord="+x+","+y);
            }
        }
        for(int i=0;i<full.pieces.size();i++){
            LayeredCellPortraitEngine.Piece a=full.pieces.get(i);
            if(a.cell<0)continue;
            for(int k=i+1;k<full.pieces.size();k++){
                LayeredCellPortraitEngine.Piece b=full.pieces.get(k);
                if(b.cell<0||b.layer!=a.layer)continue;
                for(int j=0;j<a.outer.length;j+=2)
                    ok(!b.contains(a.outer[j],a.outer[j+1]),"Overlapping fish-scale cuts cells="+a.cell+"/"+b.cell+" at "+a.outer[j]+","+a.outer[j+1]);
            }
        }
        LayeredCellPortraitEngine.Result caps=BottleCapMosaicEngine.generate(photo,W,H,opt);
        ok(!caps.options.fishScales&&caps.options.bottleCaps,"Bottle Cap changed");
        LayeredCellPortraitEngine.Result normal=LayeredCellPortraitEngine.generate(photo,W,H,opt);
        ok(!normal.options.fishScales&&!normal.options.bottleCaps,"Original cell changed");
        System.out.println("PASS RC119 Fish Scale: "+result.pieces.size()+" closed pieces, masks, eight layers, deterministic SVG, legacy isolation");
    }
}

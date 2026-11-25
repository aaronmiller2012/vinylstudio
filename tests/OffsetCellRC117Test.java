package com.halftonestudio;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** RC117 host-only offset grid, legacy byte identity and cut-path regression. */
public final class OffsetCellRC117Test {
    private static void check(boolean ok,String error){if(!ok)throw new AssertionError(error);}
    private static String digest(String s)throws Exception {
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02x",b&255));return out.toString();
    }
    public static void main(String[] args)throws Exception {
        int w=160,h=200;
        int[] image=LayeredCellHighDetailRC116Test.image(w,h);
        boolean[] mask=LayeredCellHighDetailRC116Test.mask(w,h);
        LayeredCellPortraitEngine.Options opt=new LayeredCellPortraitEngine.Options();
        opt.widthMm=152.4f;opt.columns=32;opt.photoDetail=1f;opt.ornamentation=.94f;
        opt.layerCount=8;opt.motifDepth=3;opt.minFeatureMm=.3f;
        opt.gapMm=.18f;opt.seamGapMm=.06f;
        opt.allowedPixels=mask;opt.registration=false;
        opt.colors=new int[]{0xff351b19,0xff7d4632,0xffad765e,0xffd5a792,0xffedd1b3,
                0xff573129,0xffd4b074,0xff171212};
        opt.solidBase=true;opt.detailMotifOverlay=true;
        LayeredCellPortraitEngine.Result original=LayeredCellPortraitEngine.generate(image,w,h,opt);
        check(!original.options.offsetRows,"Legacy style shifted its rows");
        StringBuilder legacySvg=new StringBuilder();
        for(int layer=0;layer<8;layer++)legacySvg.append(original.svg(layer));
        String originalHash=digest(legacySvg.toString());
        // Frozen from unmodified Build 532 legacy dedicated-accent path (verified
        // before the Build 533 repair; RC116 historical hash was already stale).
        check(originalHash.equals("af1418cea155995e436608f9bb90baced31970abaf0d3afe94300501fcc726e1"),"Original Layered Cell geometry changed: "+originalHash);
        LayeredCellPortraitEngine.Result shifted=OffsetCellPortraitEngine.generate(image,w,h,opt);
        check(!opt.offsetRows,"Offset Cell mutated caller's options");
        check(shifted.options.offsetRows,"Offset style did not activate staggered rows");
        check(shifted.pieceCounts[0]>0 && shifted.pieceCounts[7]>0,
                "Solid Snip base or motif accent missing");
        check(shifted.columns==original.columns&&shifted.rows==original.rows,
                "Offset style changed cell density unexpectedly");
        // Edge and half-cell layout need an unmasked image; the portrait Snip
        // intentionally excludes the outermost half-cells.
        LayeredCellPortraitEngine.Options full=opt.copy();
        full.allowedPixels=null;full.solidBase=false;full.detailMotifOverlay=false;
        full.columns=12;full.motifDepth=1;
        LayeredCellPortraitEngine.Result exposed=OffsetCellPortraitEngine.generate(image,w,h,full);
        boolean hasHalfCell=false,hasFullCell=false;
        float xPitch=w/(float)exposed.columns;
        for(LayeredCellPortraitEngine.Piece p:exposed.pieces){
            if(p.cell<0)continue;
            int row=p.cell/(exposed.columns+1);
            float minX=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY;
            for(int i=0;i<p.outer.length;i+=2){
                minX=Math.min(minX,p.outer[i]);maxX=Math.max(maxX,p.outer[i]);
            }
            if((row&1)==0 && maxX-minX>.8f*xPitch)hasFullCell=true;
            if((row&1)!=0 && minX<.20f*xPitch && maxX<.60f*xPitch)hasHalfCell=true;
        }
        check(hasFullCell&&hasHalfCell,"No staggered half-cell at the image edge");
        for(int layer=0;layer<8;layer++){
            if(shifted.contourCounts[layer]==0)continue;
            String svg=shifted.svg(layer);
            LayeredCellExportSafety.validate(shifted,layer,svg);
            check(shifted.contourCounts[layer]<=4800,"Offset exceeded SVG contour budget");
        }
        LayeredCellPortraitEngine.Result again=OffsetCellPortraitEngine.generate(image,w,h,opt);
        StringBuilder one=new StringBuilder(),two=new StringBuilder();
        for(int i=0;i<8;i++){one.append(shifted.svg(i));two.append(again.svg(i));}
        check(one.toString().equals(two.toString()),"Offset export not deterministic");
        check(!one.toString().equals(legacySvg.toString()),"Offset matches original grid exactly");
        System.out.println("PASS RC117 separate offset rows, clipped half-cells, silhouette, details, SVG safety, deterministic output, Build 532 legacy hash parity");
    }
}

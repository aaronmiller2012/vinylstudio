package com.halftonestudio;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.util.Arrays;
import java.util.Locale;

/** Host-only visual QA harness; javax ImageIO intentionally NOT part of Android app. */
public final class LayeredCellPhotoQA {
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("usage: PhotoQA photo.png output-dir");
        BufferedImage image=ImageIO.read(Path.of(args[0]).toFile());
        int w=image.getWidth(),h=image.getHeight();int[] rgb=new int[w*h];image.getRGB(0,0,w,h,rgb,0,w);
        boolean[] allowed=new boolean[rgb.length];
        for(int i=0;i<rgb.length;i++)allowed[i]=(rgb[i]>>>24)>=128;
        int[] photoColors=SourceColorStencilQuantizer.quantize(rgb,w,h,5,allowed).colors;
        Path out=Path.of(args[1]);Files.createDirectories(out);
        LayeredCellPortraitEngine.Options o=new LayeredCellPortraitEngine.Options();
        o.colors=photoColors;o.layerCount=5;o.columns=LayeredCellReleaseProfile.safeColumns(30,w,h,152.4f,.5f,.30f);
        o.photoDetail=.75f;o.ornamentation=.60f;o.motifDepth=3;o.gapMm=.30f;o.seamGapMm=.12f;
        o.minFeatureMm=.5f;o.widthMm=152.4f;o.allowedPixels=allowed;
        LayeredCellPortraitEngine.Result r=LayeredCellPortraitEngine.generate(rgb,w,h,o);
        float pad=Math.max(o.outerSpacingMm,o.registration?Math.max(8f,3*o.markSizeMm):0f);
        float bw=o.widthMm+2*pad,bh=h*(o.widthMm/w)+2*pad;
        StringBuilder compound=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 "+bw+" "+bh+"\" width=\"800\" height=\"800\">\n<rect width=\"100%\" height=\"100%\" fill=\"#faf9f6\"/>\n");
        for(int i=0;i<5;i++){
            if(r.pieceCounts[i]==0)continue;
            String svg=r.svg(i);
            LayeredCellExportSafety.validate(r,i,svg);
            Files.writeString(out.resolve(String.format(Locale.US,"layer-%02d.svg",i+1)),svg);
            compound.append(svg,svg.indexOf("<g fill="),svg.indexOf("</g>")+4).append('\n');
        }
        compound.append("</svg>\n");
        Files.writeString(out.resolve("photo-composite.svg"),compound.toString());
        Files.writeString(out.resolve("report.txt"),r.summary()+"\nPhoto-suggested swatches: "+Arrays.toString(photoColors)+
            "\nPieces per layer: "+Arrays.toString(r.pieceCounts)+"\nContours per layer: "+Arrays.toString(r.contourCounts)+"\n");
        System.out.println("PHOTO_QA "+r.summary());
    }
}

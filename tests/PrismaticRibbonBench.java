package com.halftonestudio;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Prismatic/ribbon milestone benchmark. Same metrics, composites and cut-ready path as
 * {@link M4ArtBench}, but with the production JTS vector backend installed so Prismatic
 * styles take their real vector Boolean route (M4ArtBench measures the raster fallback).
 *
 * Usage: java ... PrismaticRibbonBench <photoDir> <outDir> <label> [sizesInches,comma] [styleIds,comma]
 * Writes metrics.tsv plus one composite PNG per case. Numbers support visual review only.
 */
public final class PrismaticRibbonBench {
    static final int[] DEFAULT_STYLES={
        VinylStyleSuite.PRISMATIC_DIAGONAL,
        VinylStyleSuite.PRISMATIC_CONTOUR_FLOW,VinylStyleSuite.PRISMATIC_TWILL,VinylStyleSuite.PRISMATIC_SILK,
        VinylStyleSuite.PRISMATIC_BRAIDED_FLOW,VinylStyleSuite.PRISMATIC_CHEVRON,
        VinylStyleSuite.WOVEN_RIBBON,VinylStyleSuite.WOODGRAIN,VinylStyleSuite.QUILLING_RIBBONS,
        VinylStyleSuite.MARBLED_INK,VinylStyleSuite.QUILLED_MARBLING};

    public static void main(String[] a)throws Exception{
        VectorBooleanEngine.install(new JtsVectorBooleanBackend());
        File photos=new File(a[0]);Path out=Paths.get(a[1]);String label=a[2];
        float[] sizes={6f};
        if(a.length>3&&!a[3].isEmpty()){String[] p=a[3].split(",");sizes=new float[p.length];for(int i=0;i<p.length;i++)sizes[i]=Float.parseFloat(p[i]);}
        int[] styles=DEFAULT_STYLES;
        if(a.length>4&&!a[4].isEmpty()){String[] p=a[4].split(",");styles=new int[p.length];for(int i=0;i<p.length;i++)styles[i]=Integer.parseInt(p[i]);}
        Files.createDirectories(out.resolve("img"));
        float minMm=Float.parseFloat(System.getProperty("m4.min","0.8")),gapMm=Float.parseFloat(System.getProperty("m4.bridge","0.5"));
        String only=System.getProperty("bench.photos","");
        File[] files=photos.listFiles((d,n)->n.endsWith(".png")||n.endsWith(".jpg"));Arrays.sort(files);
        List<String> rows=new ArrayList<>();rows.add(M4ArtBench.HEADER);
        for(File f:files){
            M4ArtBench.Photo p=M4ArtBench.load(f,850);
            if(!only.isEmpty()&&!Arrays.asList(only.split(",")).contains(p.name))continue;
            for(int id:styles){
                M4ArtBench.Case k=new M4ArtBench.Case("suite",VinylStyleSuite.NAMES[id],M4ArtBench.SUITE,id);
                for(float inches:sizes){
                    float size=inches*25.4f;
                    String key=String.format(Locale.US,"%s__%s__%.0fin",p.name,k.name.replaceAll("[^A-Za-z0-9]+","-"),inches);
                    String head=p.name+"\t"+k.name+"\t"+k.family+"\t"+String.format(Locale.US,"%.1f",inches);
                    try{
                        M4ArtBench.Render r=M4ArtBench.render(k,p,size,minMm,gapMm);
                        float ppmm=Math.min(6f,1400f/Math.max(r.widthMm,r.heightMm));
                        BufferedImage comp=M4ArtBench.composite(r,ppmm);
                        String m=M4ArtBench.metrics(r,p,comp);
                        rows.add(head+"\t"+m);
                        ImageIO.write(M4ArtBench.fit(comp,900),"png",out.resolve("img/"+key+".png").toFile());
                        System.out.println(label+" "+key+" "+m);
                    }catch(Throwable t){
                        rows.add(head+"\tERROR "+t);
                        System.out.println(label+" "+key+" ERROR "+t);
                    }
                }
            }
            ImageIO.write(M4ArtBench.fit(M4ArtBench.source(p,p.w,p.h),900),"png",out.resolve("img/"+p.name+"__source.png").toFile());
        }
        Files.write(out.resolve("metrics.tsv"),rows);
    }
}

package com.halftonestudio;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Builds the M4 before/after gallery from two M4ArtBench output directories.
 * For every case present in both: one JPEG strip "source | before | after" and a row in
 * comparison.tsv with the metric deltas. Usage:
 *   java ... M4Gallery <beforeDir> <afterDir> <outDir> [stripHeight] [beforeLabel] [afterLabel]
 * Numbers support, never replace, the visual comparison.
 */
public final class M4Gallery {
    public static void main(String[] a)throws Exception{
        Path before=Paths.get(a[0]),after=Paths.get(a[1]),out=Paths.get(a[2]);
        int stripH=a.length>3?Integer.parseInt(a[3]):300;
        final String beforeLabel=a.length>4?a[4]:"558",afterLabel=a.length>5?a[5]:"M4";
        Files.createDirectories(out.resolve("strips"));
        Map<String,String[]> b=read(before.resolve("metrics.tsv")),f=read(after.resolve("metrics.tsv"));
        String[] header=Files.readAllLines(before.resolve("metrics.tsv")).get(0).split("\t");
        List<String> rows=new ArrayList<>();
        rows.add("photo\tstyle\tsize_in\tmetric\tbefore\tafter\tdelta");
        for(Map.Entry<String,String[]> e:b.entrySet()){
            String[] x=e.getValue(),y=f.get(e.getKey());
            if(y==null||x[4].startsWith("ERROR")||y[4].startsWith("ERROR"))continue;
            String id=String.format(Locale.US,"%s__%s__%sin",x[0],x[1].replaceAll("[^A-Za-z0-9]+","-"),
                String.valueOf(Math.round(Double.parseDouble(x[3]))));
            File src=before.resolve("img/"+x[0]+"__source.png").toFile();
            File bi=before.resolve("img/"+id+".png").toFile(),ai=after.resolve("img/"+id+".png").toFile();
            if(!bi.isFile()||!ai.isFile())continue;
            strip(new File[]{src,bi,ai},new String[]{"photo",beforeLabel,
                afterLabel},stripH,out.resolve("strips/"+id+".jpg").toFile());
            for(int i=4;i<header.length;i++){
                double p=num(x[i]),q=num(y[i]);
                rows.add(String.join("\t",x[0],x[1],x[3],header[i],x[i],y[i],
                    Double.isNaN(p)||Double.isNaN(q)?"":String.format(Locale.US,"%.4f",q-p)));
            }
        }
        Files.write(out.resolve("comparison.tsv"),rows,StandardCharsets.UTF_8);
        System.out.println("gallery: "+(rows.size()-1)+" metric rows, strips in "+out.resolve("strips"));
    }
    static double num(String s){try{return Double.parseDouble(s);}catch(RuntimeException e){return Double.NaN;}}
    static Map<String,String[]> read(Path p)throws Exception{
        Map<String,String[]> m=new LinkedHashMap<>();
        List<String> lines=Files.readAllLines(p);
        for(int i=1;i<lines.size();i++){String[] f=lines.get(i).split("\t");if(f.length>4)m.put(f[0]+"|"+f[1]+"|"+f[3],f);}
        return m;
    }
    static void strip(File[] files,String[] labels,int h,File out)throws Exception{
        BufferedImage[] im=new BufferedImage[files.length];int w=0;
        for(int i=0;i<files.length;i++){BufferedImage s=ImageIO.read(files[i]);
            int sw=Math.max(1,Math.round(s.getWidth()*h/(float)s.getHeight()));
            BufferedImage d=new BufferedImage(sw,h,BufferedImage.TYPE_INT_RGB);Graphics2D g=d.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(s,0,0,sw,h,null);g.dispose();im[i]=d;w+=sw+(i>0?8:0);}
        BufferedImage o=new BufferedImage(w,h+22,BufferedImage.TYPE_INT_RGB);Graphics2D g=o.createGraphics();
        g.setColor(Color.WHITE);g.fillRect(0,0,o.getWidth(),o.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,14));g.setColor(new Color(0x33,0x33,0x33));
        int x=0;for(int i=0;i<im.length;i++){g.drawImage(im[i],x,22,null);g.drawString(labels[i],x+4,16);x+=im[i].getWidth()+8;}
        g.dispose();
        ImageWriter wr=ImageIO.getImageWritersByFormatName("jpg").next();ImageWriteParam p=wr.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);p.setCompressionQuality(.82f);
        try(ImageOutputStream os=ImageIO.createImageOutputStream(out)){wr.setOutput(os);wr.write(null,new IIOImage(o,null,null),p);}
        wr.dispose();
    }
}

package com.halftonestudio;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import javax.imageio.ImageIO;

/**
 * Before/after strips for two M5Bench output directories: "source | before | after" for every
 * composite present in both. Usage: java ... M5Gallery <beforeDir> <afterDir> <outDir> [height] [beforeLabel] [afterLabel]
 */
public final class M5Gallery {
    public static void main(String[] a)throws Exception{
        File before=new File(a[0],"img"),after=new File(a[1],"img"),out=new File(a[2]);out.mkdirs();
        int h=a.length>3?Integer.parseInt(a[3]):320;String bl=a.length>4?a[4]:"before",al=a.length>5?a[5]:"after";
        String[] names=after.list((d,n)->n.endsWith(".png")&&!n.contains("__source"));Arrays.sort(names);int n=0;
        for(String name:names){
            File b=new File(before,name);if(!b.isFile())continue;
            String photo=name.substring(0,name.indexOf("__"));
            BufferedImage src=ImageIO.read(new File(after,photo+"__source.png")),bi=ImageIO.read(b),ai=ImageIO.read(new File(after,name));
            BufferedImage[] parts={src,bi,ai};String[] labels={"source",bl,al};
            int w=0;int[] ws=new int[3];for(int i=0;i<3;i++){ws[i]=Math.round(parts[i].getWidth()*(h/(float)parts[i].getHeight()));w+=ws[i]+8;}
            BufferedImage strip=new BufferedImage(w,h+26,BufferedImage.TYPE_INT_RGB);Graphics2D g=strip.createGraphics();
            g.setColor(Color.WHITE);g.fillRect(0,0,w,h+26);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,15));int x=0;
            for(int i=0;i<3;i++){g.drawImage(parts[i],x,26,ws[i],h,null);g.setColor(Color.BLACK);g.drawString(labels[i]+"  "+name.replace(".png",""),x+4,18);x+=ws[i]+8;}
            g.dispose();ImageIO.write(strip,"jpg",new File(out,name.replace(".png",".jpg")));n++;
        }
        System.out.println("M5Gallery: "+n+" strips in "+out);
    }
}

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
 * M5 Linocut benchmark. Real photos through the production suite path (VinylStyleSuite.generate,
 * then the export cut-ready stage), opaque composite, M4ArtBench metrics plus five-band coverage
 * (source OKLab L: <.35, .35-.50, .50-.65, .65-.80, >.80) so the shadow ceiling is visible.
 * Usage: java ... M5LinocutBench <photoDir> <outDir> <label> [sizesInches comma list]
 *        java ... M5LinocutBench gallery <beforeDir> <afterDir> <outDir>   (source | before | after strips)
 */
public final class M5LinocutBench {
    public static void main(String[] a)throws Exception{
        if(a.length>0&&"gallery".equals(a[0])){gallery(Paths.get(a[1]),Paths.get(a[2]),Paths.get(a[3]));return;}
        File photos=new File(a[0]);Path out=Paths.get(a[1]);String label=a[2];
        String[] sz=(a.length>3?a[3]:"3,4,6,8,11").split(",");
        Files.createDirectories(out.resolve("img"));
        String only=System.getProperty("m5.only","");
        File[] files=photos.listFiles((d,n)->(n.endsWith(".png")||n.endsWith(".jpg"))&&(only.isEmpty()||n.startsWith(only)));
        Arrays.sort(files);
        List<String> rows=new ArrayList<>();
        rows.add(M4ArtBench.HEADER+"\tcov_b0\tcov_b1\tcov_b2\tcov_b3\tcov_b4\tsrc_b0\tsrc_b1\tsrc_b2\tsrc_b3\tsrc_b4");
        M4ArtBench.Case k=new M4ArtBench.Case("suite","Linocut",M4ArtBench.SUITE,VinylStyleSuite.LINOCUT);
        for(File f:files){
            M4ArtBench.Photo p=M4ArtBench.load(f,850);
            for(String s:sz){
                float in=Float.parseFloat(s),size=in*25.4f;
                String id=String.format(Locale.US,"%s__%.0fin",p.name,in);
                try{
                    M4ArtBench.Render r=M4ArtBench.render(k,p,size,.8f,.5f);
                    float ppmm=Math.min(6f,1400f/Math.max(r.widthMm,r.heightMm));
                    BufferedImage comp=M4ArtBench.composite(r,ppmm);
                    String m=M4ArtBench.metrics(r,p,comp);
                    rows.add(p.name+"\tLinocut\tsuite\t"+s+"\t"+m+"\t"+bands(r,p,comp));
                    ImageIO.write(M4ArtBench.fit(comp,900),"png",out.resolve("img/"+id+".png").toFile());
                    // 1:1-ish detail crop of the centre third for halo / carving inspection.
                    int w=comp.getWidth(),h=comp.getHeight();
                    ImageIO.write(comp.getSubimage(w/3,h/4,Math.min(w/3,900),Math.min(h/3,900)),"png",out.resolve("img/"+id+"__crop.png").toFile());
                    // Optional named crop "x0,y0,x1,y1" in fractions of the artwork (e.g. the face), -Dm5.crop.<photo>=...
                    String cr=System.getProperty("m5.crop."+p.name);
                    if(cr!=null){String[] q=cr.split(",");
                        int cx0=(int)(w*Float.parseFloat(q[0])),cy0=(int)(h*Float.parseFloat(q[1])),cx1=(int)(w*Float.parseFloat(q[2])),cy1=(int)(h*Float.parseFloat(q[3]));
                        ImageIO.write(comp.getSubimage(cx0,cy0,cx1-cx0,cy1-cy0),"png",out.resolve("img/"+id+"__face.png").toFile());}
                    String[] c=m.split("\t");
                    System.out.println(label+" "+id+" tone="+c[0]+" ssim="+c[1]+" find="+c[16]+"/"+c[17]+" ms="+c[20]);
                }catch(Throwable t){
                    rows.add(p.name+"\tLinocut\tsuite\t"+s+"\tERROR "+t);
                    System.out.println(label+" "+id+" ERROR "+t);t.printStackTrace();
                }
            }
            ImageIO.write(M4ArtBench.fit(M4ArtBench.source(p,p.w,p.h),900),"png",out.resolve("img/"+p.name+"__source.png").toFile());
        }
        Files.write(out.resolve("metrics.tsv"),rows);
    }
    /** Vinyl coverage (pixel differs from the mounting surface) per five source-tone bands, and the source darkness there. */
    static String bands(M4ArtBench.Render r,M4ArtBench.Photo p,BufferedImage comp){
        int w=comp.getWidth(),h=comp.getHeight();
        float[] L=M4ArtBench.oklab(M4ArtBench.source(p,w,h))[0];
        int[] px=comp.getRGB(0,0,w,h,null,0,w);int paper=r.paper&0xffffff;
        double[] cov=new double[5];int[] n=new int[5];
        for(int i=0;i<px.length;i++){
            float l=L[i];int b=l<.35f?0:l<.50f?1:l<.65f?2:l<.80f?3:4;
            n[b]++;if((px[i]&0xffffff)!=paper)cov[b]++;
        }
        StringBuilder sb=new StringBuilder();
        for(int b=0;b<5;b++)sb.append(String.format(Locale.US,"%.3f\t",n[b]==0?Double.NaN:cov[b]/n[b]));
        for(int b=0;b<5;b++)sb.append(String.format(Locale.US,"%.3f%s",n[b]/(double)px.length,b<4?"\t":""));
        return sb.toString();
    }
    /** One PNG per case: source | before | after at equal height, labelled. */
    static void gallery(Path before,Path after,Path out)throws Exception{
        Files.createDirectories(out);
        File[] imgs=after.resolve("img").toFile().listFiles((d,n)->n.endsWith(".png")&&!n.contains("__source"));
        if(imgs==null)return;Arrays.sort(imgs);
        for(File f:imgs){
            File b=before.resolve("img").resolve(f.getName()).toFile();if(!b.exists())continue;
            String photo=f.getName().substring(0,f.getName().indexOf("__"));
            File src=after.resolve("img").resolve(photo+"__source.png").toFile();
            BufferedImage[] parts={src.exists()&&!f.getName().contains("crop")&&!f.getName().contains("face")?ImageIO.read(src):null,ImageIO.read(b),ImageIO.read(f)};
            int H=520;int W=0;for(BufferedImage im:parts)if(im!=null)W+=Math.round(im.getWidth()*H/(float)im.getHeight())+8;
            BufferedImage o=new BufferedImage(W,H+28,BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g=o.createGraphics();
            g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,W,H+28);g.setColor(java.awt.Color.BLACK);
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            String[] lab={"source","before (M4, 561 base)","after (M5)"};int x=0;
            for(int i=0;i<3;i++){if(parts[i]==null)continue;int w=Math.round(parts[i].getWidth()*H/(float)parts[i].getHeight());
                g.drawImage(parts[i],x,28,w,H,null);g.drawString(lab[i]+"  "+f.getName().replace(".png",""),x+4,18);x+=w+8;}
            g.dispose();ImageIO.write(o,"png",out.resolve(f.getName()).toFile());
        }
    }
}

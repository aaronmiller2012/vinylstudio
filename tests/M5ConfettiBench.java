package com.halftonestudio;

import java.awt.Graphics2D;
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
 * M5 confetti base-colour benchmark. Real photos, real production palette flow, real
 * FallenConfettiEngine through VinylStyleSuite, opaque stacked composite, M4 metrics.
 * Usage: M5ConfettiBench photoDir outDir [sizeInches] [mode ids comma]
 * Variants: A=darkest base (pre-M5), B=median base, C=perceptual base.
 */
public final class M5ConfettiBench {
    static int[] named(int id,int count){
        int[] full=id<100?VinylPalettes.get(id,8):null;
        return PaletteStylePolicy.arrange(PaletteStylePolicy.CONFETTI_STACK,full,count,VinylEngine.DEFAULT_SURFACE);
    }
    static int[] photoPalette(M4ArtBench.Photo p){
        boolean[] v=new boolean[p.argb.length];Arrays.fill(v,true);
        return PhotoPaletteOptimizer.optimize(p.argb,v,8).colors;
    }
    /** Re-seat the base colour inside a fixed active set. Others ascend by lightness. */
    static int[] withBase(int[] active,int mode,int[] photo){
        int n=active.length,b=mode==3?ConfettiBasePolicy.baseIndex(active,n,photo,ConfettiBasePolicy.PERCEPTUAL,.60):ConfettiBasePolicy.baseIndex(active,n,photo,mode);
        Integer[] o=new Integer[n];for(int i=0;i<n;i++)o[i]=i;
        Arrays.sort(o,(x,y)->Double.compare(PerceptualVinylColor.lab(active[x])[0],PerceptualVinylColor.lab(active[y])[0]));
        int[] out=new int[8];out[0]=active[b];int k=1;
        for(int i:o)if(i!=b)out[k++]=active[i];
        return Arrays.copyOf(out,n);
    }
    public static void main(String[] a)throws Exception{
        File[] files=new File(a[0]).listFiles((d,n)->n.endsWith(".png")||n.endsWith(".jpg"));Arrays.sort(files);
        Path out=Paths.get(a[1]);Files.createDirectories(out);
        float inches=a.length>2?Float.parseFloat(a[2]):11f;
        int[] styles=a.length>3?Arrays.stream(a[3].split(",")).mapToInt(Integer::parseInt).toArray():new int[]{VinylStyleSuite.CONFETTI};
        int count=Integer.getInteger("m5.count",6);
        String[] variants={"A-darkest","B-median","C-perceptual","E-as-shipped-pre-M5"};
        StringBuilder tsv=new StringBuilder("photo\tstyle\tpalette\tvariant\tbase_L\ttone_corr\tssim\tdE\tcov_hi\tcov_mid\tcov_sh\terr_hi\terr_mid\terr_sh\tbase_visible\tfragments\tms\n");
        for(File f:files){
            M4ArtBench.Photo p=M4ArtBench.load(f,850);
            int[] ph=M4ArtBench.thumb(p,96);
            String filter=System.getProperty("m5.photos","");
            if(!filter.isEmpty()&&!Arrays.asList(filter.split(",")).contains(p.name))continue;
            String[] pal=System.getProperty("m5.pal","photo,Portrait Vinyl,Natural Skin,Pop Art,Ocean,Monochrome").split(",");
            for(int style:styles)for(String pn:pal){
                int[] active,shipped;
                if(pn.equals("photo")){
                    // production flow today: sorted dark->light 8, first `count` become active
                    int[] eight=photoPalette(p);
                    active=Arrays.copyOf(PaletteOrientation.reverseActive(FoundationColorPolicy.anchorPhotoPalette(eight,count,ph,false),count,false,true),count);shipped=active;
                    if(Boolean.getBoolean("m5.subset"))active=Arrays.copyOf(PhotoSwatchSubset.select(eight,count,ph),count);
                }else{
                    int id=Arrays.asList(VinylPalettes.NAMES).indexOf(pn);
                    active=Arrays.copyOf(named(id,count),count);shipped=active;
                }
                BufferedImage[] ims=new BufferedImage[5];
                for(int v=0;v<4;v++){
                    int[] colors=v==3?Arrays.copyOf(shipped,count):withBase(active,v,ph);
                    VinylStyleSuite.Config c=M4ArtBench.suiteConfig(style,p,inches*25.4f,.8f,.5f);
                    c.colorCount=count;c.palette=Arrays.copyOf(colors,count);c.leadColor=colors[0];
                    long t0=System.nanoTime();
                    StyleKit.Result r=VinylStyleSuite.generate(p.argb,p.w,p.h,c);
                    long ms=(System.nanoTime()-t0)/1000000;
                    M4ArtBench.Render rr=new M4ArtBench.Render();rr.widthMm=inches*25.4f;rr.heightMm=r.heightMm;rr.paper=r.paperArgb;
                    for(StyleKit.Layer l:r.layers){M4ArtBench.Sheet sh=new M4ArtBench.Sheet();sh.color=l.color;sh.loops.addAll(l.loops);sh.lines.addAll(l.lines);sh.strokeMm=l.strokeMm;rr.sheets.add(sh);}
                    BufferedImage comp=M4ArtBench.composite(rr,Math.min(5f,1400f/Math.max(rr.widthMm,rr.heightMm)));
                    String m=M4ArtBench.metrics(rr,p,comp);
                    String[] mf=m.split("\t");
                    int[] px=comp.getRGB(0,0,comp.getWidth(),comp.getHeight(),null,0,comp.getWidth());
                    int base=colors[0]&0xffffff;long bv=0;for(int q:px)if((q&0xffffff)==base)bv++;
                    int pieces=0;for(M4ArtBench.Sheet s:rr.sheets)pieces+=s.loops.size();
                    tsv.append(String.format(Locale.US,"%s\t%d\t%s\t%s\t%.3f\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%.3f\t%d\t%d\n",
                        p.name,style,pn,variants[v],PerceptualVinylColor.lab(colors[0])[0],mf[0],mf[1],mf[3],mf[4],mf[5],mf[6],mf[7],mf[8],mf[9],bv/(double)px.length,pieces,ms));
                    ims[v]=comp;
                }
                ims[4]=M4ArtBench.source(p,ims[0].getWidth(),ims[0].getHeight());
                int w=ims[0].getWidth(),h=ims[0].getHeight();
                BufferedImage sheet=new BufferedImage(w*5/2,h/2,BufferedImage.TYPE_INT_RGB);
                Graphics2D g=sheet.createGraphics();
                for(int i=0;i<5;i++)g.drawImage(ims[i==0?4:i-1],i*w/2,0,w/2,h/2,null);
                g.dispose();
                ImageIO.write(sheet,"png",out.resolve(p.name+"__"+style+"__"+pn.replace(' ','_')+".png").toFile());
                System.out.println(p.name+" "+pn);
                Files.write(out.resolve("confetti.tsv"),tsv.toString().getBytes());
            }
        }
        Files.write(out.resolve("confetti.tsv"),tsv.toString().getBytes());
    }
}

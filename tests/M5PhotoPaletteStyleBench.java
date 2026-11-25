package com.halftonestudio;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;

/** M5: photo palette active-subset (PhotoSwatchSubset) vs the pre-M5 first-N flow, rendered through
 *  the real engine of one style (default Prismatic Diagonal) and measured with the M4 metrics.
 *  Usage: M5PhotoPaletteStyleBench photoDir styleId inches count */
public final class M5PhotoPaletteStyleBench {
    public static void main(String[] a)throws Exception{
        File[] files=new File(a[0]).listFiles((d,n)->n.endsWith(".png")||n.endsWith(".jpg"));Arrays.sort(files);
        int style=Integer.parseInt(a[1]);float inches=Float.parseFloat(a[2]);int count=Integer.parseInt(a[3]);
        boolean light=FoundationColorPolicy.lightFoundation(style);
        System.out.println("photo\tvariant\ttone_corr\tssim\tdE\tcov_hi\tcov_mid\tcov_sh\terr_hi\terr_mid\terr_sh\tms");
        double[][] tot=new double[2][4];int n=0;
        for(File f:files){
            M4ArtBench.Photo p=M4ArtBench.load(f,850);
            int[] ph=M4ArtBench.thumb(p,96);boolean[] v=new boolean[ph.length];Arrays.fill(v,true);
            int[] eight=PhotoPaletteOptimizer.optimize(ph,v,8).colors;
            n++;
            for(int var=0;var<2;var++){
                int[] pal=var==0?FoundationColorPolicy.anchorPhotoPalette(eight,count,ph,light)
                    :FoundationColorPolicy.anchorPhotoPalette(PhotoSwatchSubset.select(eight,count,ph),count,ph,light);
                VinylStyleSuite.Config c=M4ArtBench.suiteConfig(style,p,inches*25.4f,.8f,.5f);
                c.colorCount=count;c.palette=Arrays.copyOf(pal,count);c.leadColor=pal[0];
                long t0=System.nanoTime();
                StyleKit.Result r=VinylStyleSuite.generate(p.argb,p.w,p.h,c);
                long ms=(System.nanoTime()-t0)/1000000;
                M4ArtBench.Render rr=new M4ArtBench.Render();rr.widthMm=inches*25.4f;rr.heightMm=r.heightMm;rr.paper=r.paperArgb;
                for(StyleKit.Layer l:r.layers){M4ArtBench.Sheet sh=new M4ArtBench.Sheet();sh.color=l.color;sh.loops.addAll(l.loops);sh.lines.addAll(l.lines);sh.strokeMm=l.strokeMm;rr.sheets.add(sh);}
                BufferedImage comp=M4ArtBench.composite(rr,Math.min(4f,1000f/Math.max(rr.widthMm,rr.heightMm)));
                String[] m=M4ArtBench.metrics(rr,p,comp).split("\t");
                System.out.println(String.format(Locale.US,"%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%d",p.name,var==0?"first-N":"subset",m[0],m[1],m[3],m[4],m[5],m[6],m[7],m[8],m[9],ms));
                tot[var][0]+=Double.parseDouble(m[0]);tot[var][1]+=Double.parseDouble(m[1]);tot[var][2]+=Double.parseDouble(m[3]);tot[var][3]+=Double.parseDouble(m[7]);
                javax.imageio.ImageIO.write(comp,"png",new File(System.getProperty("m5.out","/tmp"),"pal_"+style+"_"+p.name+"_"+(var==0?"firstN":"subset")+".png"));
            }
        }
        for(int var=0;var<2;var++)System.out.println(String.format(Locale.US,"MEAN %s corr=%.3f ssim=%.3f dE=%.4f err_hi=%.3f",var==0?"first-N":"subset",tot[var][0]/n,tot[var][1]/n,tot[var][2]/n,tot[var][3]/n));
    }
}

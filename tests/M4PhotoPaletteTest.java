package com.halftonestudio;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * M4 / D12 photo palette. Every case is a counterexample the build-558 extractor got
 * wrong (reproduced here through the unchanged legacy path), or a contract property
 * of the new {@link PhotoPaletteOptimizer} on real photographs.
 */
public final class M4PhotoPaletteTest {
    static int failures=0;
    static void check(boolean ok,String msg){if(!ok){failures++;System.out.println("FAIL "+msg);}}

    /** Build 558 PhotoPaletteExtractor, kept verbatim for before/after evidence. */
    static int[] legacy558(int[] pixels,int w,int h){
        boolean[] valid=new boolean[pixels.length];
        for(int i=0;i<pixels.length;i++)valid[i]=(pixels[i]>>>24)>=128;
        SourceColorStencilQuantizer.Result result=SourceColorStencilQuantizer.quantize(pixels,w,h,8,valid);
        int[] colors=result.colors;int[] members=new int[8];
        for(int owner:result.owner)if(owner>=0&&owner<8)members[owner]++;
        for(int slot=0;slot<8;slot++)if(members[slot]==0){
            int best=0;boolean found=false;long score=Long.MIN_VALUE;
            for(int index=0;index<pixels.length;index+=Math.max(1,pixels.length/4096)){
                if(!valid[index])continue;
                int color=0xff000000|(pixels[index]&0xffffff);long nearest=Long.MAX_VALUE;
                for(int i=0;i<8;i++)if(i!=slot&&members[i]>0){
                    long dr=((color>>>16)&255)-((colors[i]>>>16)&255);
                    long dg=((color>>>8)&255)-((colors[i]>>>8)&255);
                    long db=(color&255)-(colors[i]&255);
                    nearest=Math.min(nearest,dr*dr+2*dg*dg+db*db);
                }
                if(nearest>score){score=nearest;best=color;found=true;}
            }
            if(found)colors[slot]=best;
        }
        return colors;
    }
    static double nearestSwatch(int[] pal,int color){
        double d=Double.POSITIVE_INFINITY;for(int c:pal)d=Math.min(d,PhotoPaletteOptimizer.distance(c,color));return d;
    }
    static int nearDuplicatePairs(int[] pal){
        int n=0;for(int i=0;i<pal.length;i++)for(int j=i+1;j<pal.length;j++)if(PhotoPaletteOptimizer.distance(pal[i],pal[j])<PhotoPaletteOptimizer.MERGE_DE)n++;
        return n;
    }
    /** Worst distance from a swatch to the nearest pixel that actually occurs in the photo. */
    static double worstInvented(int[] pal,int[] px){
        double worst=0;
        for(int c:pal){double best=Double.POSITIVE_INFINITY;
            for(int i=0;i<px.length;i+=1){best=Math.min(best,PhotoPaletteOptimizer.distance(c,px[i]|0xff000000));if(best<.005)break;}
            worst=Math.max(worst,best);}
        return worst;
    }

    public static void main(String[] args)throws Exception{
        // 1. Stride aliasing: 160x160 (25,600 px -> 558 stride 5); columns x%5==0 are navy,
        //    the other 80% of the image is orange. 558 sampled only the navy column.
        {
            int w=160,h=160;int[] px=new int[w*h];int navy=0xff1b2a49,orange=0xffe8742c;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)px[y*w+x]=x%5==0?navy:orange;
            int[] before=legacy558(px,w,h),after=PhotoPaletteExtractor.extract(px,w,h);
            System.out.printf(Locale.US,"alias: 558 nearest-to-orange %.3f, M4 %.3f%n",nearestSwatch(before,orange),nearestSwatch(after,orange));
            check(nearestSwatch(before,orange)>.05,"fixture must reproduce the 558 aliasing defect");
            check(nearestSwatch(after,orange)<.01,"dominant orange (80% of pixels) must be in the palette");
            check(nearestSwatch(after,navy)<.01,"navy must be in the palette");
        }
        // 2. Outlier seeding: four flat regions plus 12 isolated magenta noise pixels.
        //    558's farthest-point seeding made a magenta swatch out of 0.05% of the image.
        {
            int w=160,h=160;int[] px=new int[w*h];
            int[] flat={0xff2b2d31,0xff8c6b52,0xffd9c3a5,0xff4f7d8c};
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)px[y*w+x]=flat[(x/80)+2*(y/80)];
            int magenta=0xffff00ff;
            for(int i=0;i<12;i++)px[(17+i*11)*w+(11+i*11)]=magenta;
            int[] before=legacy558(px,w,h),after=PhotoPaletteExtractor.extract(px,w,h);
            System.out.printf(Locale.US,"outlier: 558 nearest-to-magenta %.3f, M4 %.3f%n",nearestSwatch(before,magenta),nearestSwatch(after,magenta));
            check(nearestSwatch(before,magenta)<.02,"fixture must reproduce the 558 outlier swatch");
            check(nearestSwatch(after,magenta)>.2,"12 noise pixels must not become a vinyl swatch");
            for(int c:flat)check(nearestSwatch(after,c)<.01,"every flat region colour is represented");
        }
        // 3. Accent survival: a smooth grey-beige gradient (95%) with a 5% red accent.
        {
            int w=160,h=120;int[] px=new int[w*h];int red=0xffc4232f;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                int v=60+(int)(170.0*x/(w-1));px[y*w+x]=0xff000000|(v<<16)|((v*9/10)<<8)|(v*8/10);
            }
            for(int y=50;y<68;y++)for(int x=70;x<92;x++)px[y*w+x]=red;
            int[] after=PhotoPaletteExtractor.extract(px,w,h);
            check(nearestSwatch(after,red)<.02,"a 5% saturated accent must keep its own swatch");
            check(nearDuplicatePairs(after)==0,"gradient must not produce near-duplicate swatches: "+hex(after));
        }
        // 4. Two-tone photo: the palette must remain usable by the surface arranger
        //    (558 threw "Duplicate source swatch" or left transparent slots).
        {
            int w=64,h=64;int[] px=new int[w*h];
            for(int i=0;i<px.length;i++)px[i]=(i%64)<32?0xff111111:0xfff4f1ea;
            int[] pal=PhotoPaletteExtractor.extract(px,w,h);
            PhotoPaletteOptimizer.Result r=PhotoPaletteExtractor.analyze(px,w,h);
            check(r.distinct==2,"two-tone photo reports two distinct colours, got "+r.distinct);
            for(int c:pal)check(nearestSwatch(new int[]{0xff111111,0xfff4f1ea},c)<.005,"no invented colour in a two-tone photo: "+hex(pal));
            for(int count=2;count<=8;count++)for(int surface:new int[]{0xfffaf9f6,0xff18191e}){
                int[] arranged=ColorWorkspacePalette.arrangedForSurface(pal,count,surface);
                for(int c:arranged)check((c>>>24)==255,"arranged palette has no transparent slot");
                int[] sorted=arranged.clone(),orig=pal.clone();Arrays.sort(sorted);Arrays.sort(orig);
                check(Arrays.equals(sorted,orig),"arranged palette is a permutation of the photo swatches");
            }
        }
        // 4b. Review F3: a repeated swatch never displaces a distinct one.
        {
            int[] mixed={0xff000000,0xff404040,0xff808080,0xff8e8e8e,0xffffffff,0xff000000,0xff808080,0xffffffff};
            int[] chosen=ColorWorkspacePalette.selectForSurface(mixed,5,0xfffaf9f6);
            check(new java.util.HashSet<Integer>(){{for(int c:chosen)add(c);}}.size()==5,"five distinct swatches available, five chosen: "+hex(chosen));
        }
        // 5. White photo keeps white (RC69 contract) and determinism.
        {
            int[] pale=new int[16*16];Arrays.fill(pale,0xffffffff);
            for(int c:PhotoPaletteExtractor.extract(pale,16,16))check(c==0xffffffff,"white photo stays white");
        }
        // 6. Real photographs: deterministic, no near-duplicates when the photo has colour
        //    variety, every swatch close to a colour that occurs in the photo.
        File dir=new File("tests/m4/photos");
        String[] photos={"astronaut.png","coffee.png","chelsea.png","rocket.jpg","moon.png","camera.png"};
        System.out.println("photo        558dup  M4dup  558invent  M4invent  M4distinct");
        for(String name:photos){
            BufferedImage img=ImageIO.read(new File(dir,name));
            float s=160f/Math.max(img.getWidth(),img.getHeight());
            int w=Math.round(img.getWidth()*s),h=Math.round(img.getHeight()*s);
            BufferedImage sc=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g=sc.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img,0,0,w,h,null);g.dispose();
            int[] px=sc.getRGB(0,0,w,h,null,0,w);for(int i=0;i<px.length;i++)px[i]|=0xff000000;
            int[] before=legacy558(px,w,h);
            PhotoPaletteOptimizer.Result r=PhotoPaletteExtractor.analyze(px,w,h);
            check(Arrays.equals(r.colors,PhotoPaletteExtractor.extract(px.clone(),w,h)),name+" deterministic");
            double inv=worstInvented(r.colors,px);
            System.out.printf(Locale.US,"%-12s %6d %6d %10.3f %9.3f %10d%n",name,nearDuplicatePairs(before),nearDuplicatePairs(r.colors),
                worstInvented(before,px),inv,r.distinct);
            check(inv<.03,name+" every swatch is within 0.03 OKLab of a real photo colour ("+inv+")");
            // Coverage (review F1): the palette must represent the photo, including a small bright subject.
            double[] cb=coverage(before,px),ca=coverage(r.colors,px);
            System.out.printf(Locale.US,"   coverage mean %.3f -> %.3f, brightest 5%% %.3f -> %.3f%n",cb[0],ca[0],cb[1],ca[1]);
            // Tolerance 0.005 OKLab: a quarter of a just-noticeable difference (~0.02). Plain OKLab
            // over-weights deep shadows, the region the toe correction deliberately de-emphasises.
            check(ca[0]<=cb[0]+.005,name+" mean photo coverage no worse than 558 beyond 0.005");
            check(ca[1]<=Math.max(cb[1]*1.25,.05),name+" bright subject still represented ("+ca[1]+" vs 558 "+cb[1]+")");
            if(r.distinct==8)check(nearDuplicatePairs(r.colors)==0,name+" no near-duplicate swatches");
            float sum=0;for(float f:r.support)sum+=f;check(Math.abs(sum-1f)<1e-3,name+" support sums to 1");
        }
        if(failures>0)throw new AssertionError(failures+" M4 photo palette failures");
        System.out.println("PASS M4 photo palette (D12): aliasing, outliers, accents, two-tone, real photos");
    }
    /** [mean nearest-swatch distance over all pixels, the same over the brightest 5%]. */
    static double[] coverage(int[] pal,int[] px){
        double[][] lab=new double[px.length][3];Integer[] idx=new Integer[px.length];
        for(int i=0;i<px.length;i++){PhotoPaletteOptimizer.oklab(px[i],lab[i]);idx[i]=i;}
        Arrays.sort(idx,(a,b)->Double.compare(lab[b][0],lab[a][0]));
        double all=0,bright=0;int nb=Math.max(1,px.length/20);
        double[][] sw=new double[pal.length][3];for(int k=0;k<pal.length;k++)PhotoPaletteOptimizer.oklab(pal[k],sw[k]);
        for(int r=0;r<px.length;r++){int i=idx[r];double m=9;for(double[] s:sw)m=Math.min(m,PhotoPaletteOptimizer.dist(lab[i],s));
            all+=m;if(r<nb)bright+=m;}
        return new double[]{all/px.length,bright/nb};
    }
    static String hex(int[] a){StringBuilder b=new StringBuilder();for(int c:a)b.append(String.format("%06x ",c&0xffffff));return b.toString();}
}

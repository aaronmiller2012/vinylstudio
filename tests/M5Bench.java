package com.halftonestudio;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * M5 multi-scale benchmark. Reuses the {@link M4ArtBench} render/composite/metric code (opaque
 * bottom-to-top vinyl composite on the mounting surface) and adds:
 *  - the deterministic synthetic fixtures of {@link M5Fixtures} next to the CC0 photos;
 *  - 3/4/6/8/11 in sizes by default;
 *  - Snip runs (circle, irregular): the standard engine uses its real SnipMask; suite styles
 *    get the mask applied to the source pixels (a proxy - production clips suite vectors in
 *    the activity). `snip_cov_ratio` = vinyl coverage inside the retained region with the
 *    Snip / without it (1.0 means the Snip did not thin the artwork it keeps);
 *  - per-render bytes allocated by all live threads, and approximate export SVG size.
 *
 * Usage: java ... M5Bench <photoDir|-> <outDir> <label> [sizesIn,comma] [cases,comma] [snips,comma]
 *  cases: suite ids (e.g. 26,48) and/or std:<VinylEngine pattern> (e.g. std:5) and/or stencil, cell0, cell1.
 *  -Dbench.sources=name,name restricts sources; -Dbench.fixtures=0 drops synthetic fixtures.
 * Writes metrics.tsv plus composites. Numbers support, and never replace, visual review.
 */
public final class M5Bench {
    static final String HEADER=M4ArtBench.HEADER+"\tsnip\tsnip_cov_ratio\talloc_mb\tsvg_kb";

    public static void main(String[] a)throws Exception{
        try{VectorBooleanEngine.install(new JtsVectorBooleanBackend());}catch(Throwable t){System.out.println("JTS backend unavailable: raster fallback");}
        Path out=Paths.get(a[1]);String label=a[2];
        float[] sizes={3,4,6,8,11};
        if(a.length>3&&!a[3].isEmpty()){String[] p=a[3].split(",");sizes=new float[p.length];for(int i=0;i<p.length;i++)sizes[i]=Float.parseFloat(p[i]);}
        List<M4ArtBench.Case> cases=new ArrayList<>();
        String caseArg=a.length>4&&!a[4].isEmpty()?a[4]:"26,52,3,27,39,35,47";
        for(String c:caseArg.split(","))cases.add(parse(c.trim()));
        String[] snips=a.length>5&&!a[5].isEmpty()?a[5].split(","):new String[]{"none"};
        Files.createDirectories(out.resolve("img"));
        float minMm=Float.parseFloat(System.getProperty("m4.min","0.8")),gapMm=Float.parseFloat(System.getProperty("m4.bridge","0.5"));
        List<M4ArtBench.Photo> sources=new ArrayList<>();
        if(!"-".equals(a[0])){
            File[] files=new File(a[0]).listFiles((d,n)->n.endsWith(".png")||n.endsWith(".jpg"));Arrays.sort(files);
            for(File f:files)sources.add(M4ArtBench.load(f,850));
        }
        if(!"0".equals(System.getProperty("bench.fixtures","1")))
            for(String n:M5Fixtures.NAMES){int[] d=new int[2];int[] px=M5Fixtures.render(n,d);sources.add(new M4ArtBench.Photo(n,px,d[0],d[1]));}
        String only=System.getProperty("bench.sources","");
        List<String> rows=new ArrayList<>();rows.add(HEADER);
        com.sun.management.ThreadMXBean tmx=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        for(M4ArtBench.Photo p:sources){
            if(!only.isEmpty()&&!Arrays.asList(only.split(",")).contains(p.name))continue;
            for(M4ArtBench.Case k:cases)for(float inches:sizes)for(String snip:snips){
                String key=String.format(Locale.US,"%s__%s__%.0fin__%s",p.name,k.name.replaceAll("[^A-Za-z0-9]+","-"),inches,snip);
                String head=p.name+"\t"+k.name+"\t"+k.family+"\t"+String.format(Locale.US,"%.1f",inches);
                try{
                    float size=inches*25.4f;
                    long a0=allocated(tmx);
                    Object[] holder=new Object[1];
                    M4ArtBench.Render r=render(k,p,size,minMm,gapMm,snip,holder);
                    long alloc=(allocated(tmx)-a0)>>20;
                    float ppmm=Math.min(6f,1400f/Math.max(r.widthMm,r.heightMm));
                    BufferedImage comp=M4ArtBench.composite(r,ppmm);
                    String m=M4ArtBench.metrics(r,p,comp);
                    double ratio=Double.NaN;
                    if(!"none".equals(snip)){
                        M4ArtBench.Render full=render(k,p,size,minMm,gapMm,"none",new Object[1]);
                        BufferedImage fc=M4ArtBench.composite(full,ppmm);
                        ratio=insideCoverage(comp,r.paper,snip)/Math.max(1e-9,insideCoverage(fc,full.paper,snip));
                    }
                    long svg=svgBytes(holder[0]);
                    String row=head+"\t"+m+"\t"+snip+"\t"+String.format(Locale.US,"%.3f",ratio)+"\t"+alloc+"\t"+(svg>>10);
                    rows.add(row);
                    ImageIO.write(M4ArtBench.fit(comp,900),"png",out.resolve("img/"+key+".png").toFile());
                    System.out.println(label+" "+key+" tone="+m.split("\t")[0]+" ssim="+m.split("\t")[1]);
                }catch(Throwable t){
                    rows.add(head+"\tERROR "+t);System.out.println(label+" "+key+" ERROR "+t);
                }
            }
            ImageIO.write(M4ArtBench.fit(M4ArtBench.source(p,p.w,p.h),900),"png",out.resolve("img/"+p.name+"__source.png").toFile());
        }
        Files.write(out.resolve("metrics.tsv"),rows,StandardCharsets.UTF_8);
    }

    /** Bytes allocated by all live threads (worker pools persist, as in PrismaticProfile). */
    static long allocated(com.sun.management.ThreadMXBean t){long s=0;for(long b:t.getThreadAllocatedBytes(t.getAllThreadIds()))if(b>0)s+=b;return s;}

    static M4ArtBench.Case parse(String c){
        if(c.startsWith("std:")){int id=Integer.parseInt(c.substring(4));return new M4ArtBench.Case("standard",VinylEngine.PATTERNS[id]+" (CMYK)",M4ArtBench.STANDARD,id);}
        if(c.equals("stencil"))return new M4ArtBench.Case("stencil","Tonal stencil (spot)",M4ArtBench.STENCIL_MODE,0);
        if(c.equals("cell0"))return new M4ArtBench.Case("cells","Layered cell portrait",M4ArtBench.CELL,0);
        if(c.equals("cell1"))return new M4ArtBench.Case("cells","Offset cell portrait",M4ArtBench.CELL,1);
        int id=Integer.parseInt(c);return new M4ArtBench.Case("suite",VinylStyleSuite.NAMES[id],M4ArtBench.SUITE,id);
    }

    static SnipMask mask(String snip){
        SnipMask m=new SnipMask();
        for(float[] poly:M5Fixtures.snip(snip))m.add(new SnipMask.Operation(SnipMask.KEEP_INSIDE,poly));
        return m;
    }

    /** Standard engine: native Snip. Suite: pixel proxy (outside -> white) then the real engine. */
    static M4ArtBench.Render render(M4ArtBench.Case k,M4ArtBench.Photo p,float size,float minMm,float gapMm,String snip,Object[] svgOut)throws Exception{
        M4ArtBench.Photo src=p;
        if(!"none".equals(snip)&&k.kind!=M4ArtBench.STANDARD){
            int[] px=SnipBackground.apply(p.argb.clone(),p.w,p.h,mask(snip),SnipBackground.WHITE);
            src=new M4ArtBench.Photo(p.name,px,p.w,p.h);
        }
        if(k.kind==M4ArtBench.STANDARD&&!"none".equals(snip)){
            M4ArtBench.SNIP=mask(snip);
            try{M4ArtBench.Render r=M4ArtBench.render(k,src,size,minMm,gapMm);svgOut[0]=r;return r;}finally{M4ArtBench.SNIP=null;}
        }
        M4ArtBench.Render r=M4ArtBench.render(k,src,size,minMm,gapMm);
        svgOut[0]=r;
        return r;
    }

    static double insideCoverage(BufferedImage comp,int paper,String snip){
        int w=comp.getWidth(),h=comp.getHeight();SnipMask m=mask(snip);
        int[] px=comp.getRGB(0,0,w,h,null,0,w);long in=0,cov=0;
        for(int y=0;y<h;y+=2)for(int x=0;x<w;x+=2){
            float nx=(x+.5f)/w,ny=(y+.5f)/h;
            // Shrink the region by 3% so boundary clearance is not counted as loss.
            float sx=.5f+(nx-.5f)*1.03f,sy=.5f+(ny-.5f)*1.03f;
            if(sx<0||sy<0||sx>1||sy>1||!m.allows(sx,sy))continue;
            in++;if((px[y*w+x]&0xffffff)!=(paper&0xffffff))cov++;
        }
        return in==0?0:cov/(double)in;
    }

    /** Approximate export size: closed loops as SVG path data with 2-decimal mm coordinates. */
    static long svgBytes(Object o){
        if(!(o instanceof M4ArtBench.Render))return 0;
        long n=0;
        for(M4ArtBench.Sheet s:((M4ArtBench.Render)o).sheets){
            for(float[] xy:s.loops)n+=8+xy.length*7L;
            for(float[] xy:s.lines)n+=8+xy.length*7L;
            n+=s.circles.size()*48L;
        }
        return n;
    }
}

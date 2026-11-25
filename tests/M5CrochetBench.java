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
 * M5 Crochet (style 39) benchmark. Real photos, real VinylStyleSuite generation and
 * cut-ready stage, M4 fidelity metrics on the cut-ready composite, and exact JTS
 * measurement of the FINISHED overlay sheets (M5CutGeometryProbe).
 *
 * Usage: java -cp ...:jts M5CrochetBench <photoDir> <outDir> <label> [sizesIn comma list]
 *  -Dm5.style=39 (any suite id)  -Dm5.photos=astronaut,horse  -Dm5.portrait=true adds a
 *  centre portrait crop of astronaut (tall artwork at the same width).
 */
public final class M5CrochetBench {
    static M4ArtBench.Photo portraitCrop(M4ArtBench.Photo p){
        int w=Math.round(p.h*.70f);if(w>=p.w)return p;
        int x0=(p.w-w)/2;int[] a=new int[w*p.h];
        for(int y=0;y<p.h;y++)System.arraycopy(p.argb,y*p.w+x0,a,y*w,w);
        return new M4ArtBench.Photo(p.name+"-portrait",a,w,p.h);
    }
    public static void main(String[] a)throws Exception{
        File photos=new File(a[0]);Path out=Paths.get(a[1]);String label=a[2];
        String[] sz=(a.length>3?a[3]:"3,4,6,8,11").split(",");
        int style=Integer.getInteger("m5.style",VinylStyleSuite.CROCHET);
        String only=System.getProperty("m5.photos","");
        boolean portrait=Boolean.getBoolean("m5.portrait");
        float minMm=.8f,bridge=.5f;
        float gap=StyleCutGeometryPreflight.exportGapMm(bridge,minMm);
        float holeMin=Float.parseFloat(System.getProperty("m5.holeMin","1.0"));
        Files.createDirectories(out.resolve("img"));
        File[] files=photos.listFiles((d,n)->n.endsWith(".png")||n.endsWith(".jpg"));Arrays.sort(files);
        List<M4ArtBench.Photo> list=new ArrayList<>();
        for(File f:files){
            String n=f.getName().substring(0,f.getName().lastIndexOf('.'));
            if(!only.isEmpty()&&!Arrays.asList(only.split(",")).contains(n))continue;
            M4ArtBench.Photo p=M4ArtBench.load(f,850);list.add(p);
            if(portrait&&n.equals("astronaut"))list.add(portraitCrop(p));
        }
        List<String> rows=new ArrayList<>();
        rows.add("photo\tsize_in\ttone_corr\tssim\tdE\tcov_hi\tcov_mid\tcov_sh\tholes_gen\tholes_cut\tholes_lost\tisl_lost\tfind_gen\tfind_cut\trender_ms\tcut_ms\tloops\tverts\tcut_len_mm\t"
            +M5CutGeometryProbe.Stats.header()+"\tprobe_gen_narrow_mm2\tprobe_gen_tiny_holes\tprobe_gen_necks");
        M4ArtBench.Case k=new M4ArtBench.Case("suite",VinylStyleSuite.NAMES[style],M4ArtBench.SUITE,style);
        for(M4ArtBench.Photo p:list)for(String s:sz){
            float in=Float.parseFloat(s),widthMm=in*25.4f;
            String id=String.format(Locale.US,"%s__%s__%sin",p.name,k.name.replaceAll("[^A-Za-z0-9]+","-"),s);
            try{
                VinylStyleSuite.Config c=M4ArtBench.suiteConfig(style,p,widthMm,minMm,bridge);
                long t0=System.nanoTime();
                StyleKit.Result r=VinylStyleSuite.generate(p.argb,p.w,p.h,c);
                long renderMs=M4ArtBench.ms(t0);
                int findGen=StyleCutGeometryPreflight.inspect(r,minMm,gap,100000).size();
                long t1=System.nanoTime();
                StyleKit.Result cut=VinylStyleSuite.prepareForCutting(r,style,minMm,bridge);
                long cutMs=M4ArtBench.ms(t1);
                int findCut=StyleCutGeometryPreflight.inspect(cut,minMm,gap,100000).size();
                int[] tb=M4ArtBench.topology(r),ta=M4ArtBench.topology(cut);
                M4ArtBench.Render rr=new M4ArtBench.Render();rr.widthMm=widthMm;rr.heightMm=cut.heightMm;rr.paper=cut.paperArgb;
                int loops=0,verts=0;double len=0;
                M5CutGeometryProbe.Stats fin=new M5CutGeometryProbe.Stats(),gen=new M5CutGeometryProbe.Stats();
                for(int li=0;li<cut.layers.size();li++){StyleKit.Layer l=cut.layers.get(li);
                    M4ArtBench.Sheet sh=new M4ArtBench.Sheet();sh.color=l.color;sh.loops.addAll(l.loops);rr.sheets.add(sh);
                    loops+=l.loops.size();for(float[] xy:l.loops){verts+=xy.length/2;len+=M4ArtBench.perimeter(xy,true);}
                    if(li==0)continue; // L1 is the solid silhouette foundation
                    fin.add(M5CutGeometryProbe.measure(l.loops,minMm,gap,holeMin));
                    gen.add(M5CutGeometryProbe.measure(r.layers.get(li).loops,minMm,gap,holeMin));
                }
                float ppmm=Math.min(6f,1400f/Math.max(rr.widthMm,rr.heightMm));
                BufferedImage comp=M4ArtBench.composite(rr,ppmm);
                String[] m=M4ArtBench.metrics(rr,p,comp).split("\t");
                rows.add(String.join("\t",p.name,s,m[0],m[1],m[3],m[4],m[5],m[6],
                    ""+tb[1],""+ta[1],""+Math.max(0,tb[1]-ta[1]),""+Math.max(0,tb[0]-ta[0]),""+findGen,""+findCut,
                    ""+renderMs,""+cutMs,""+loops,""+verts,String.format(Locale.US,"%.0f",len),fin.toString(),
                    String.format(Locale.US,"%.2f",gen.narrowArea),""+gen.tinyHoles,""+gen.necks));
                ImageIO.write(M4ArtBench.fit(comp,900),"png",out.resolve("img/"+id+".png").toFile());
                // 1:1 physical detail crop (6 px/mm) at the image centre-left (face region on portraits).
                BufferedImage det=M4ArtBench.composite(rr,6f);
                int cw=Math.min(det.getWidth(),360),ch=Math.min(det.getHeight(),360);
                int cx=Math.max(0,Math.min(det.getWidth()-cw,det.getWidth()*2/5-cw/2)),cy=Math.max(0,Math.min(det.getHeight()-ch,det.getHeight()*2/5-ch/2));
                ImageIO.write(det.getSubimage(cx,cy,cw,ch),"png",out.resolve("img/"+id+"__detail.png").toFile());
                System.out.println(label+" "+id+" tone="+m[0]+" holesLost="+Math.max(0,tb[1]-ta[1])+"/"+tb[1]+" fin="+fin+" ms="+renderMs+"+"+cutMs);
            }catch(Throwable t){
                rows.add(p.name+"\t"+s+"\tERROR "+t);
                System.out.println(label+" "+id+" ERROR "+t);t.printStackTrace(System.out);
            }
            Files.write(out.resolve("metrics.tsv"),rows);
        }
        for(M4ArtBench.Photo p:list)ImageIO.write(M4ArtBench.fit(M4ArtBench.source(p,p.w,p.h),900),"png",out.resolve("img/"+p.name+"__source.png").toFile());
    }
}

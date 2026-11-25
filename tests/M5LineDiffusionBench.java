package com.halftonestudio;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
 * M5 line-dash diffusion benchmark (not a gate). Renders real photographs through the
 * production line engines on the three routes a user can reach, with highlight dashes off
 * (legacy) and on, composites the opaque sheets bottom to top (M4ArtBench) and records
 * M4 fidelity metrics plus line-specific cut metrics.
 *
 * Routes:
 *   classic - new-project defaults (Settings.defaults: Classic v2.24 CMYK lines, 2.54 mm pitch);
 *   picker  - CMYK line style chosen in the Styles picker (SmartHalftonePresets + the photo
 *             suggestion StudioActivity.applyHalftoneStyle applies: modern engine, presence tracker);
 *   spot    - the same picker settings in SPOT mode with four Portrait Vinyl colours.
 *
 * Usage: java ... M5LineDiffusionBench photoDir outDir [sizesInCsv]
 *   -Dm5.routes=classic,picker,spot -Dm5.styles=5,8,12,13,9,14,16 -Dm5.dash=0,1
 *   -Dm5.photos=astronaut,chelsea -Dm5.min=0.8 -Dm5.bridge=0.5 -Dm5.strips=1
 */
public final class M5LineDiffusionBench {
    static final String[] STYLE_NAMES=new String[VinylEngine.PATTERNS.length];
    static{for(int i=0;i<STYLE_NAMES.length;i++)STYLE_NAMES[i]=VinylEngine.PATTERNS[i].replaceAll("[^A-Za-z0-9]+","-");}

    static VinylEngine.Settings settings(String route,int style,M4ArtBench.Photo p,float widthMm,float minMm,float gapMm,float dash){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        if(route.equals("spot")){
            s.mode=VinylEngine.SPOT;s.layers.clear();
            int[] colors=VinylPalettes.get(0,4);
            for(int i=0;i<colors.length;i++){VinylEngine.Layer l=new VinylEngine.Layer("S"+i,colors[i]);l.spacing=s.masterSpacingMm;s.layers.add(l);}
        }
        s.widthMm=widthMm;s.pattern=style;
        if(!route.equals("classic")){
            float wIn=widthMm/25.4f,hIn=wIn*p.h/(float)p.w;
            SmartHalftonePresets.apply(s,style,VinylSizing.BALANCED,wIn,hIn);
            // StudioActivity.applyHalftoneStyle: photo suggestion (96 px thumbnail).
            int[] t=M4ArtBench.thumb(p,96);
            AutoPhotoTuner.Stats stats=AutoPhotoTuner.analyze(t,M4ArtBench.tw,M4ArtBench.th,null,false);
            AutoPhotoTuner.Suggestion start=AutoPhotoTuner.suggest(stats,AutoPhotoTuner.LINES,AutoPhotoTuner.BALANCED,
                s.widthMm,Math.max(VinylEngine.MINIMUM_CUT_WIDTH_MM,minMm));
            s.masterSpacingMm=Math.max(minMm,start.spacingMm);
            s.masterContrast=start.screenContrast;
            s.lineMaxFraction=start.lineWidth;s.detailBoost=start.lineDetail;s.contourStrength=start.lineContour;
            for(VinylEngine.Layer l:s.layers){l.spacing=s.masterSpacingMm;l.density=s.masterDensity;l.contrast=s.masterContrast;}
        }
        s.minimumMm=minMm;s.bridgeMm=gapMm;
        return s;
    }

    static final class Out { M4ArtBench.Render render;VinylEngine.Result result;int maxElements,pieces; }

    static Out render(VinylEngine.Settings s,M4ArtBench.Photo p,float minMm,float gapMm,boolean findings){
        Out o=new Out();M4ArtBench.Render out=new M4ArtBench.Render();o.render=out;
        out.widthMm=s.widthMm;
        long t0=System.nanoTime();
        VinylEngine.Result r=VinylEngine.generate(p.argb,p.w,p.h,s);
        out.renderMs=(System.nanoTime()-t0)/1_000_000L;
        o.result=r;out.heightMm=r.heightMm;
        int f=0;
        for(int li=0;li<r.settings.layers.size();li++){
            VinylEngine.Layer l=r.settings.layers.get(li);
            if(!l.enabled||l.excludeFromExport)continue;
            M4ArtBench.Sheet sh=new M4ArtBench.Sheet();sh.color=l.color;
            for(VinylEngine.Shape shape:r.shapes.get(li)){
                if(shape.circle)sh.circles.add(new float[]{shape.x,shape.y,shape.r});
                else if(shape.contours!=null)sh.loops.addAll(Arrays.asList(shape.contours));
                else sh.loops.add(shape.xy);
            }
            out.sheets.add(sh);
            o.maxElements=Math.max(o.maxElements,VinylEngine.svgElementsForLayer(r,li));
            o.pieces+=r.shapes.get(li).size();
            if(findings&&!r.shapes.get(li).isEmpty()){
                FinalCutSheetSafety.Report rep=FinalCutSheetSafety.inspect("L"+li,VinylEngine.svg(r,li),minMm,
                    StyleCutGeometryPreflight.exportGapMm(gapMm,minMm));
                f+=rep.critical+rep.narrow+rep.unverified;
            }
        }
        out.findingsBefore=findings?f:-1;out.findingsAfter=out.findingsBefore;
        return o;
    }

    /** Banknote Engraving through the production suite route, including the cut-ready stage. */
    static Out banknote(M4ArtBench.Photo p,int variant,float widthMm,float minMm,float gapMm,float dash,boolean findings){
        Out o=new Out();M4ArtBench.Render out=new M4ArtBench.Render();o.render=out;out.widthMm=widthMm;
        VinylStyleSuite.Config c=M4ArtBench.suiteConfig(VinylStyleSuite.BANKNOTE,p,widthMm,minMm,gapMm);
        c.variant=variant;
        long t0=System.nanoTime();
        StyleKit.Result r=VinylStyleSuite.generate(p.argb,p.w,p.h,c);
        out.renderMs=(System.nanoTime()-t0)/1_000_000L;
        out.heightMm=r.heightMm;out.paper=r.paperArgb;
        float gap=StyleCutGeometryPreflight.exportGapMm(gapMm,minMm);
        if(findings)out.findingsBefore=StyleCutGeometryPreflight.inspect(r,minMm,gap,100000).size();
        long t1=System.nanoTime();
        StyleKit.Result cut=VinylStyleSuite.prepareForCutting(r,VinylStyleSuite.BANKNOTE,minMm,gapMm);
        out.cutReadyMs=(System.nanoTime()-t1)/1_000_000L;
        if(findings)out.findingsAfter=StyleCutGeometryPreflight.inspect(cut,minMm,gap,100000).size();
        for(StyleKit.Layer l:cut.layers){
            M4ArtBench.Sheet sh=new M4ArtBench.Sheet();sh.color=l.color;sh.loops.addAll(l.loops);sh.lines.addAll(l.lines);sh.strokeMm=l.strokeMm;
            out.sheets.add(sh);o.maxElements=Math.max(o.maxElements,l.loops.size()+l.lines.size());o.pieces+=l.loops.size();
        }
        return o;
    }

    public static void main(String[] a)throws Exception{
        File photos=new File(a[0]);Path out=Paths.get(a[1]);
        String[] sizeArg=(a.length>2?a[2]:"6").split(",");
        String[] routes=System.getProperty("m5.routes","classic,picker,spot").split(",");
        String[] styleArg=System.getProperty("m5.styles","5,12,13,8,16,9,14").split(",");
        String[] dashArg={"prod"}; // production defaults of the compiled tree (compile base and M5 trees to compare)
        String only=System.getProperty("m5.photos","");
        boolean strips=!"0".equals(System.getProperty("m5.strips","1"));
        boolean findings=!"0".equals(System.getProperty("m5.findings","1"));
        float minMm=Float.parseFloat(System.getProperty("m5.min","0.8")),gapMm=Float.parseFloat(System.getProperty("m5.bridge","0.5"));
        Files.createDirectories(out.resolve("strips"));
        File[] files=photos.listFiles((d,n)->n.endsWith(".png")||n.endsWith(".jpg"));Arrays.sort(files);
        List<String> rows=new ArrayList<>();
        rows.add("photo\troute\tstyle\tsize_in\tdash\t"+M4ArtBench.HEADER.substring(M4ArtBench.HEADER.indexOf("tone_corr"))+"\ttone_coarse\tssim_coarse\tmax_svg_elements\tlayer_pieces");
        for(File file:files){
            String name=file.getName().substring(0,file.getName().lastIndexOf('.'));
            if(!only.isEmpty()&&!Arrays.asList(only.split(",")).contains(name))continue;
            M4ArtBench.Photo p=M4ArtBench.load(file,850);
            for(String route:routes)for(String st:styleArg)for(String sz:sizeArg){
                int style=Integer.parseInt(st.trim());float inches=Float.parseFloat(sz);
                List<BufferedImage> panels=new ArrayList<>();List<String> labels=new ArrayList<>();
                for(String ds:dashArg){
                    float dash=0f;
                    String id=String.format(Locale.US,"%s__%s__%s__%.0fin__dash%s",name,route,label(route,style),inches,ds);
                    try{
                        Out o;
                        if(route.equals("banknote"))o=banknote(p,style,inches*25.4f,minMm,gapMm,dash,findings);
                        else o=render(settings(route,style,p,inches*25.4f,minMm,gapMm,dash),p,minMm,gapMm,findings);
                        float ppmm=Math.min(6f,1400f/Math.max(o.render.widthMm,o.render.heightMm));
                        BufferedImage comp=M4ArtBench.composite(o.render,ppmm);
                        String m=M4ArtBench.metrics(o.render,p,comp)+"\t"+coarse(comp,p);
                        rows.add(name+"\t"+route+"\t"+label(route,style)+"\t"+sz+"\t"+ds+"\t"+m+"\t"+o.maxElements+"\t"+o.pieces);
                        String[] f=m.split("\t");
                        System.out.println(id+" tone="+f[0]+" ssim="+f[1]+" dE="+f[3]+" toneC="+f[f.length-2]+" ssimC="+f[f.length-1]+" pieces="+o.pieces+" maxEl="+o.maxElements+" find="+o.render.findingsBefore+" ms="+o.render.renderMs);
                        if(strips){panels.add(M4ArtBench.fit(comp,700));labels.add("dash "+ds+" tone "+m.split("\t")[0].substring(0,5));}
                    }catch(Throwable t){
                        rows.add(name+"\t"+route+"\t"+label(route,style)+"\t"+sz+"\t"+ds+"\tERROR "+t);
                        System.out.println(id+" ERROR "+t);
                    }
                }
                if(strips&&!panels.isEmpty()){
                    BufferedImage src=M4ArtBench.fit(M4ArtBench.source(p,panels.get(0).getWidth(),panels.get(0).getHeight()),700);
                    panels.add(0,src);labels.add(0,"photo");
                    ImageIO.write(strip(panels,labels),"png",out.resolve(String.format(Locale.US,"strips/%s__%s__%s__%.0fin.png",name,route,label(route,style),inches)).toFile());
                }
            }
        }
        Files.write(out.resolve("metrics.tsv"),rows);
    }
    /** Same metrics at a 3x coarser viewing scale (about 3 mm at 6 in): a far-viewing check
     *  that dashes are integrated by the eye rather than seen as texture. */
    static String coarse(BufferedImage comp,M4ArtBench.Photo p){
        int w=comp.getWidth(),h=comp.getHeight();
        float[] c=M4ArtBench.oklab(comp)[0],s=M4ArtBench.oklab(M4ArtBench.source(p,w,h))[0];
        float sigma=Math.max(1f,w/50f);
        float[] cl=M4ArtBench.blur(c,w,h,sigma),sl=M4ArtBench.blur(s,w,h,sigma);
        int f=Math.max(1,w/60);int[] d=new int[2];
        float[] dc=M4ArtBench.down(cl,w,h,f,d),ds=M4ArtBench.down(sl,w,h,f,d);
        return String.format(Locale.US,"%.4f\t%.4f",M4ArtBench.corr(cl,sl),M4ArtBench.ssim(dc,ds,d[0],d[1]));
    }
    static String label(String route,int style){
        return route.equals("banknote")?BanknoteEngravingEngine.VARIANTS[style].replaceAll("[^A-Za-z0-9]+","-"):STYLE_NAMES[style];
    }
    static BufferedImage strip(List<BufferedImage> panels,List<String> labels){
        int h=0,w=0;for(BufferedImage b:panels){h=Math.max(h,b.getHeight());w+=b.getWidth()+6;}
        BufferedImage o=new BufferedImage(w,h+22,BufferedImage.TYPE_INT_RGB);Graphics2D g=o.createGraphics();
        g.setColor(Color.WHITE);g.fillRect(0,0,o.getWidth(),o.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));
        int x=0;for(int i=0;i<panels.size();i++){g.drawImage(panels.get(i),x,22,null);g.setColor(Color.BLACK);g.drawString(labels.get(i),x+4,16);x+=panels.get(i).getWidth()+6;}
        g.dispose();return o;
    }
}

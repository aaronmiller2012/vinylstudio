package com.halftonestudio;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * M4 artwork-quality benchmark. Renders real photographs through the real engines
 * (standard VinylEngine line/dot screens, suite styles including the cut-ready stage,
 * layered cells), composites the physical sheets as OPAQUE vinyl stacked bottom to top
 * on the mounting surface, and measures:
 *
 *  fidelity  - correlation / SSIM of viewing-scale luminance against the photo,
 *              gradient (structure) correlation, per-tone-band vinyl coverage and error,
 *              mean OKLab colour difference;
 *  geometry  - outer pieces, holes, tiny islands, open paths, total cut length, vertices,
 *              classified cut findings before and after the cut-ready stage, holes and
 *              islands removed by the cut-ready stage;
 *  cost      - render ms, cut-ready ms, peak heap.
 *
 * Numbers support, but never replace, human visual review: every case also writes the
 * composite PNG for the before/after gallery.
 *
 * Usage: java ... M4ArtBench <photoDir> <outDir> <label> [quick|full]
 */
public final class M4ArtBench {
    static final int PAPER=VinylEngine.DEFAULT_SURFACE;

    static final class Case {
        final String family,name;final int kind,id;
        Case(String family,String name,int kind,int id){this.family=family;this.name=name;this.kind=kind;this.id=id;}
    }
    static final int STANDARD=0,SUITE=1,CELL=2,STENCIL_MODE=3;
    /** Optional real Snip for the standard engine (set by M5Bench; null = none). */
    static SnipMask SNIP=null;

    static List<Case> cases(){
        List<Case> c=new ArrayList<>();
        c.add(new Case("lines","Variable-width lines (CMYK)",STANDARD,VinylEngine.LINE));
        c.add(new Case("lines","Wavy lines (CMYK)",STANDARD,VinylEngine.WAVE));
        c.add(new Case("lines","Straight lines (CMYK)",STANDARD,VinylEngine.STRAIGHT));
        c.add(new Case("lines","Engraving hatches (CMYK)",STANDARD,VinylEngine.HATCH));
        c.add(new Case("halftone","Round dots (CMYK)",STANDARD,VinylEngine.ROUND));
        c.add(new Case("stencil","Tonal stencil (spot)",STENCIL_MODE,0));
        int[] suite={VinylStyleSuite.LINOCUT,VinylStyleSuite.LOW_POLY,VinylStyleSuite.GLASS,
            VinylStyleSuite.REDUCTION_WOODCUT,VinylStyleSuite.TOPOGRAPHIC,VinylStyleSuite.BANKNOTE,
            VinylStyleSuite.TORN_COLLAGE,VinylStyleSuite.CONFETTI,VinylStyleSuite.CHAINMAIL,
            VinylStyleSuite.CROCHET,VinylStyleSuite.CAMOUFLAGE,VinylStyleSuite.QUILLED_MARBLING,
            VinylStyleSuite.POSTER_STENCIL};
        for(int id:suite)c.add(new Case("suite",VinylStyleSuite.NAMES[id],SUITE,id));
        c.add(new Case("cells","Layered cell portrait",CELL,0));
        c.add(new Case("cells","Offset cell portrait",CELL,1));
        return c;
    }

    // ---------------------------------------------------------------- photo
    static final class Photo { final String name;final int[] argb;final int w,h;
        Photo(String n,int[] a,int w,int h){name=n;argb=a;this.w=w;this.h=h;} }

    static Photo load(File f,int maxSide)throws Exception{
        BufferedImage in=ImageIO.read(f);
        if(in==null)throw new IllegalArgumentException("Unreadable "+f);
        float s=Math.min(1f,maxSide/(float)Math.max(in.getWidth(),in.getHeight()));
        int w=Math.max(2,Math.round(in.getWidth()*s)),h=Math.max(2,Math.round(in.getHeight()*s));
        BufferedImage out=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(Color.WHITE);g.fillRect(0,0,w,h); // flatten any alpha onto white, as the picker would
        g.drawImage(in,0,0,w,h,null);g.dispose();
        int[] p=out.getRGB(0,0,w,h,null,0,w);
        for(int i=0;i<p.length;i++)p[i]|=0xff000000;
        String n=f.getName();n=n.substring(0,n.lastIndexOf('.'));
        return new Photo(n,p,w,h);
    }

    // ------------------------------------------------------------- geometry
    /** Physical sheet list bottom->top: colour + closed loops (mm) + open strokes. */
    static final class Sheet { int color;final List<float[]> loops=new ArrayList<>();final List<float[]> circles=new ArrayList<>();
        final List<float[]> lines=new ArrayList<>();float strokeMm=.3f; }
    static final class Render { final List<Sheet> sheets=new ArrayList<>();float widthMm,heightMm;int paper=PAPER;
        long renderMs,cutReadyMs=-1;int findingsBefore=-1,findingsAfter=-1;int holesLost=-1,islandsLost=-1;long peakHeapMb; }

    static Render render(Case k,Photo p,float widthMm,float minMm,float gapMm)throws Exception{
        Render out=new Render();out.widthMm=widthMm;out.heightMm=widthMm*p.h/(float)p.w;
        resetPeaks();
        long t0=System.nanoTime();
        if(k.kind==STANDARD||k.kind==STENCIL_MODE){
            VinylEngine.Settings s=VinylEngine.Settings.defaults();
            s.widthMm=widthMm;s.minimumMm=minMm;s.bridgeMm=gapMm;
            if(SNIP!=null)s.snips=SNIP.copy();
            if(k.kind==STENCIL_MODE){s.mode=VinylEngine.STENCIL;}
            else s.pattern=k.id;
            VinylEngine.Result r=VinylEngine.generate(p.argb,p.w,p.h,s);
            out.renderMs=ms(t0);
            out.heightMm=r.heightMm;
            for(int li=0;li<r.settings.layers.size();li++){
                VinylEngine.Layer l=r.settings.layers.get(li);
                if(!l.enabled||l.excludeFromExport)continue;
                Sheet sh=new Sheet();sh.color=l.color;
                for(VinylEngine.Shape shape:r.shapes.get(li)){
                    if(shape.circle)sh.circles.add(new float[]{shape.x,shape.y,shape.r});
                    else if(shape.contours!=null)sh.loops.addAll(Arrays.asList(shape.contours));
                    else sh.loops.add(shape.xy);
                }
                out.sheets.add(sh);
            }
            int findings=0;
            for(int li=0;li<r.settings.layers.size();li++){
                VinylEngine.Layer l=r.settings.layers.get(li);
                if(!l.enabled||l.excludeFromExport||r.shapes.get(li).isEmpty())continue;
                FinalCutSheetSafety.Report rep=FinalCutSheetSafety.inspect("L"+li,VinylEngine.svg(r,li),minMm,
                    StyleCutGeometryPreflight.exportGapMm(gapMm,minMm));
                findings+=rep.critical+rep.narrow+rep.unverified;
            }
            out.findingsBefore=findings;out.findingsAfter=findings;
        }else if(k.kind==SUITE){
            VinylStyleSuite.Config c=suiteConfig(k.id,p,widthMm,minMm,gapMm);
            StyleKit.Result r=VinylStyleSuite.generate(p.argb,p.w,p.h,c);
            out.renderMs=ms(t0);
            out.heightMm=r.heightMm;out.paper=r.paperArgb;
            float gap=StyleCutGeometryPreflight.exportGapMm(gapMm,minMm);
            out.findingsBefore=StyleCutGeometryPreflight.inspect(r,minMm,gap,100000).size();
            long t1=System.nanoTime();
            StyleKit.Result cut=VinylStyleSuite.prepareForCutting(r,k.id,minMm,gapMm);
            out.cutReadyMs=ms(t1);
            out.findingsAfter=StyleCutGeometryPreflight.inspect(cut,minMm,gap,100000).size();
            int[] before=topology(r),after=topology(cut);
            out.holesLost=Math.max(0,before[1]-after[1]);out.islandsLost=Math.max(0,before[0]-after[0]);
            for(StyleKit.Layer l:cut.layers){
                Sheet sh=new Sheet();sh.color=l.color;sh.loops.addAll(l.loops);sh.lines.addAll(l.lines);sh.strokeMm=l.strokeMm;
                out.sheets.add(sh);
            }
        }else{
            LayeredCellPortraitEngine.Options o=new LayeredCellPortraitEngine.Options();
            o.widthMm=widthMm;o.minimumCutMm=minMm;o.minFeatureMm=Math.max(o.minFeatureMm,minMm);
            o.offsetRows=k.id==1;o.registration=false;
            LayeredCellPortraitEngine.Result r=LayeredCellPortraitEngine.generate(p.argb,p.w,p.h,o);
            out.renderMs=ms(t0);
            float s=widthMm/r.width;out.heightMm=r.height*s;
            Sheet[] sheets=new Sheet[r.options.layerCount];
            for(int i=0;i<sheets.length;i++){sheets[i]=new Sheet();sheets[i].color=r.options.colors[i];}
            for(LayeredCellPortraitEngine.Piece pc:r.pieces){
                sheets[pc.layer].loops.add(scale(pc.outer,s));
                for(float[] h:pc.holes)sheets[pc.layer].loops.add(scale(h,s));
            }
            out.sheets.addAll(Arrays.asList(sheets));
        }
        out.peakHeapMb=peakHeapMb();
        return out;
    }

    static VinylStyleSuite.Config suiteConfig(int id,Photo p,float widthMm,float minMm,float gapMm){
        VinylStyleSuite.Config c=new VinylStyleSuite.Config();
        AutoPhotoTuner.Stats stats=AutoPhotoTuner.analyze(thumb(p,96),tw,th,null,false);
        PhotoStyleDefaults.Initial init=PhotoStyleDefaults.forNewStyle(id,stats,minMm,widthMm);
        c.style=id;c.variant=id==VinylStyleSuite.TESSELLATION_MOTIF?TessellationMotifEngine.TRIHEX:0;
        c.detail=init.detail;c.weightMm=init.weightMm;c.leadMm=init.leadMm;c.draft=false;
        c.widthMm=widthMm;c.minimumMm=minMm;c.paper=PAPER;
        c.gapMm=benchGap(gapMm,minMm);
        c.flowStrength=VinylStyleSuite.isOrganicFlow(id)?init.weightMm:1f;
        int preferred=(id==VinylStyleSuite.POSTER_STENCIL||VinylStyleSuite.isCraft(id))?9:6;
        int selectable=Math.max(NewStyleColorSlots.minimumSelectable(id),Math.min(NewStyleColorSlots.maximumSelectable(id),preferred));
        c.colorCount=Math.max(id==VinylStyleSuite.GLASS?3:2,Math.min(NewStyleColorSlots.maximumSelectable(id),selectable)); // as StudioActivity
        int[] tess={0xff24252c,0xff71485e,0xffb36e89,0xffe3aa9e,0xfff1d6ba,0xff7695b1,0xff694b9d,0xffd87b45};
        int[] colors=CamouflagePalette.defaults(id,expand(tess));
        boolean match=CamouflagePalette.useFamilyDefault(id,false,false,false);
        if(id==VinylStyleSuite.POSTER_STENCIL){
            PopPosterEngine.Options po=new PopPosterEngine.Options();
            colors=PopPosterEngine.posterPalette(po.posterPalette,9);match=true;
            c.popPoster=po.copy();c.popPoster.toneCount=c.colorCount;
        }
        c.leadColor=colors[0];
        if(!NewStyleColorSlots.multi(id)||match)c.palette=NewStyleColorSlots.fixedPalette(id,0,c.colorCount,colors);
        return c;
    }
    /** Generation gap. With the default bridge (0.5 mm) the 558 wiring (fixed 0.5) and the
     *  M4 contract (export gap) coincide, so the benchmark is unaffected by that fix. */
    static float benchGap(float bridgeMm,float minMm){
        return StyleCutGeometryPreflight.exportGapMm(bridgeMm,minMm);
    }
    static int tw,th;
    static int[] thumb(Photo p,int max){
        float r=Math.min(1f,max/(float)Math.max(p.w,p.h));
        tw=Math.max(2,Math.round(p.w*r));th=Math.max(2,Math.round(p.h*r));
        int[] o=new int[tw*th];
        for(int y=0;y<th;y++)for(int x=0;x<tw;x++)o[y*tw+x]=p.argb[Math.min(p.h-1,(int)(y/r))*p.w+Math.min(p.w-1,(int)(x/r))];
        return o;
    }
    static int[] expand(int[] in){
        int[] c=Arrays.copyOf(in,9);int a=in[5],b=in[7];
        c[8]=0xff000000|(((((a>>16)&255)+((b>>16)&255))/2)<<16)|(((((a>>8)&255)+((b>>8)&255))/2)<<8)|(((a&255)+(b&255))/2);
        return c;
    }
    static float[] scale(float[] xy,float s){float[] o=new float[xy.length];for(int i=0;i<xy.length;i++)o[i]=xy[i]*s;return o;}
    static long ms(long t){return (System.nanoTime()-t)/1_000_000L;}

    /** [islands(outer pieces), holes] across all sheets, by even-odd nesting depth. */
    static int[] topology(StyleKit.Result r){
        int outer=0,holes=0;
        for(StyleKit.Layer l:r.layers){int[] t=topology(l.loops);outer+=t[0];holes+=t[1];}
        return new int[]{outer,holes};
    }
    static int[] topology(List<float[]> loops){
        int n=loops.size();
        if(n>30000)return new int[]{n,0};
        float[][] bb=new float[n][];
        for(int i=0;i<n;i++)bb[i]=bounds(loops.get(i));
        int outer=0,holes=0;
        for(int i=0;i<n;i++){
            float[] a=loops.get(i);if(a.length<6)continue;
            float px=a[0],py=a[1];int depth=0;
            for(int j=0;j<n;j++){
                if(j==i)continue;float[] b=bb[j];
                if(px<b[0]||px>b[2]||py<b[1]||py>b[3])continue;
                if(b[2]-b[0]<bb[i][2]-bb[i][0])continue;
                if(inside(loops.get(j),px,py))depth++;
            }
            if((depth&1)==0)outer++;else holes++;
        }
        return new int[]{outer,holes};
    }
    static float[] bounds(float[] xy){float a=Float.MAX_VALUE,b=a,c=-a,d=-a;
        for(int i=0;i+1<xy.length;i+=2){a=Math.min(a,xy[i]);c=Math.max(c,xy[i]);b=Math.min(b,xy[i+1]);d=Math.max(d,xy[i+1]);}
        return new float[]{a,b,c,d};}
    static boolean inside(float[] xy,float x,float y){
        boolean in=false;int n=xy.length/2;
        for(int i=0,j=n-1;i<n;j=i++){
            float xi=xy[2*i],yi=xy[2*i+1],xj=xy[2*j],yj=xy[2*j+1];
            if((yi>y)!=(yj>y)&&x<(xj-xi)*(y-yi)/(yj-yi+1e-12f)+xi)in=!in;
        }
        return in;
    }

    // ------------------------------------------------------------ raster
    static BufferedImage composite(Render r,float ppmm){
        int w=Math.max(8,Math.round(r.widthMm*ppmm)),h=Math.max(8,Math.round(r.heightMm*ppmm));
        BufferedImage img=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(r.paper,false));g.fillRect(0,0,w,h);
        g.transform(AffineTransform.getScaleInstance(w/r.widthMm,h/r.heightMm));
        for(Sheet s:r.sheets){ // opaque vinyl, bottom to top: the later sheet hides the earlier one
            g.setColor(new Color(s.color,false));
            if(!s.loops.isEmpty()){
                // Each closed loop of one sheet is an even-odd ring of the same vinyl piece set.
                Path2D.Float path=new Path2D.Float(Path2D.WIND_EVEN_ODD);
                for(float[] xy:s.loops){if(xy.length<6)continue;path.moveTo(xy[0],xy[1]);
                    for(int i=2;i+1<xy.length;i+=2)path.lineTo(xy[i],xy[i+1]);path.closePath();}
                g.fill(path);
            }
            for(float[] c:s.circles)g.fill(new Ellipse2D.Float(c[0]-c[2],c[1]-c[2],2*c[2],2*c[2]));
            if(!s.lines.isEmpty()){
                g.setStroke(new BasicStroke(s.strokeMm,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                for(float[] xy:s.lines){Path2D.Float p=new Path2D.Float();p.moveTo(xy[0],xy[1]);
                    for(int i=2;i+1<xy.length;i+=2)p.lineTo(xy[i],xy[i+1]);g.draw(p);}
            }
        }
        g.dispose();return img;
    }
    static BufferedImage source(Photo p,int w,int h){
        BufferedImage in=new BufferedImage(p.w,p.h,BufferedImage.TYPE_INT_RGB);in.setRGB(0,0,p.w,p.h,p.argb,0,p.w);
        BufferedImage o=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);Graphics2D g=o.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(in,0,0,w,h,null);g.dispose();return o;
    }

    // ------------------------------------------------------------ metrics
    static float[][] oklab(BufferedImage img){
        int w=img.getWidth(),h=img.getHeight();int[] p=img.getRGB(0,0,w,h,null,0,w);
        float[][] o=new float[3][w*h];
        for(int i=0;i<p.length;i++){
            double r=lin((p[i]>>16)&255),g=lin((p[i]>>8)&255),b=lin(p[i]&255);
            double l=Math.cbrt(.4122214708*r+.5363325363*g+.0514459929*b);
            double m=Math.cbrt(.2119034982*r+.6806995451*g+.1073969566*b);
            double s=Math.cbrt(.0883024619*r+.2817188376*g+.6299787005*b);
            o[0][i]=(float)(.2104542553*l+.7936177850*m-.0040720468*s);
            o[1][i]=(float)(1.9779984951*l-2.4285922050*m+.4505937099*s);
            o[2][i]=(float)(.0259040371*l+.7827717662*m-.8086757660*s);
        }
        return o;
    }
    static double lin(int c){double v=c/255.0;return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);}
    static float[] blur(float[] a,int w,int h,float sigma){
        int rad=Math.max(1,(int)Math.ceil(sigma*3));float[] k=new float[2*rad+1];float sum=0;
        for(int i=-rad;i<=rad;i++){k[i+rad]=(float)Math.exp(-i*i/(2*sigma*sigma));sum+=k[i+rad];}
        for(int i=0;i<k.length;i++)k[i]/=sum;
        float[] t=new float[a.length],o=new float[a.length];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){float s=0;for(int i=-rad;i<=rad;i++){int xx=Math.max(0,Math.min(w-1,x+i));s+=k[i+rad]*a[y*w+xx];}t[y*w+x]=s;}
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){float s=0;for(int i=-rad;i<=rad;i++){int yy=Math.max(0,Math.min(h-1,y+i));s+=k[i+rad]*t[yy*w+x];}o[y*w+x]=s;}
        return o;
    }
    static double corr(float[] a,float[] b){
        double ma=0,mb=0;for(int i=0;i<a.length;i++){ma+=a[i];mb+=b[i];}ma/=a.length;mb/=b.length;
        double sab=0,saa=0,sbb=0;for(int i=0;i<a.length;i++){double x=a[i]-ma,y=b[i]-mb;sab+=x*y;saa+=x*x;sbb+=y*y;}
        return saa<1e-12||sbb<1e-12?0:sab/Math.sqrt(saa*sbb);
    }
    static double ssim(float[] a,float[] b,int w,int h){
        float[] ma=blur(a,w,h,1.5f),mb=blur(b,w,h,1.5f);
        float[] aa=new float[a.length],bb=new float[a.length],ab=new float[a.length];
        for(int i=0;i<a.length;i++){aa[i]=a[i]*a[i];bb[i]=b[i]*b[i];ab[i]=a[i]*b[i];}
        float[] saa=blur(aa,w,h,1.5f),sbb=blur(bb,w,h,1.5f),sab=blur(ab,w,h,1.5f);
        double c1=1e-4,c2=9e-4,sum=0;
        for(int i=0;i<a.length;i++){
            double va=saa[i]-ma[i]*ma[i],vb=sbb[i]-mb[i]*mb[i],cv=sab[i]-ma[i]*mb[i];
            sum+=((2*ma[i]*mb[i]+c1)*(2*cv+c2))/((ma[i]*ma[i]+mb[i]*mb[i]+c1)*(va+vb+c2));
        }
        return sum/a.length;
    }
    static float[] gradient(float[] a,int w,int h){
        float[] g=new float[a.length];
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){
            float gx=a[y*w+x+1]-a[y*w+x-1],gy=a[(y+1)*w+x]-a[(y-1)*w+x];g[y*w+x]=(float)Math.sqrt(gx*gx+gy*gy);}
        return g;
    }
    static float[] down(float[] a,int w,int h,int f,int[] dims){
        int dw=Math.max(1,w/f),dh=Math.max(1,h/f);float[] o=new float[dw*dh];
        for(int y=0;y<dh;y++)for(int x=0;x<dw;x++){float s=0;for(int j=0;j<f;j++)for(int i=0;i<f;i++)s+=a[(y*f+j)*w+x*f+i];o[y*dw+x]=s/(f*f);}
        dims[0]=dw;dims[1]=dh;return o;
    }

    static String metrics(Render r,Photo p,BufferedImage comp){
        int w=comp.getWidth(),h=comp.getHeight();
        float[][] c=oklab(comp),s=oklab(source(p,w,h));
        // Viewing scale: about 1/150 of the artwork width, so dot/line screens integrate as the eye sees them.
        float sigma=Math.max(1f,w/150f);
        float[] cl=blur(c[0],w,h,sigma),sl=blur(s[0],w,h,sigma);
        float[] ca=blur(c[1],w,h,sigma),sa=blur(s[1],w,h,sigma),cb=blur(c[2],w,h,sigma),sb=blur(s[2],w,h,sigma);
        int f=Math.max(1,w/160);int[] d=new int[2];
        float[] dcl=down(cl,w,h,f,d),dsl=down(sl,w,h,f,d);
        double toneCorr=corr(cl,sl),ss=ssim(dcl,dsl,d[0],d[1]);
        double edgeCorr=corr(gradient(dcl,d[0],d[1]),gradient(dsl,d[0],d[1]));
        double de=0;for(int i=0;i<cl.length;i++){double x=cl[i]-sl[i],y=ca[i]-sa[i],z=cb[i]-sb[i];de+=Math.sqrt(x*x+y*y+z*z);}de/=cl.length;
        // Tone bands (source OKLab L): vinyl coverage = pixel differs from the mounting surface.
        int paper=r.paper&0xffffff;int[] px=comp.getRGB(0,0,w,h,null,0,w);float[] raw=s[0];
        double[] cov=new double[3],err=new double[3];int[] n=new int[3];
        for(int i=0;i<px.length;i++){
            int band=raw[i]>.80f?0:raw[i]>.50f?1:2;
            n[band]++;if((px[i]&0xffffff)!=paper)cov[band]++;err[band]+=Math.abs(cl[i]-sl[i]);
        }
        for(int b=0;b<3;b++){cov[b]=n[b]==0?Double.NaN:cov[b]/n[b];err[b]=n[b]==0?Double.NaN:err[b]/n[b];}
        // Geometry
        int pieces=0,holes=0,tiny=0,open=0,verts=0;double length=0;
        float minMm=Float.parseFloat(System.getProperty("m4.min","0.8"));
        double tinyArea=minMm*minMm*2;
        for(Sheet sh:r.sheets){
            int[] t=topology(sh.loops);pieces+=t[0]+sh.circles.size();holes+=t[1];
            for(float[] xy:sh.loops){verts+=xy.length/2;length+=perimeter(xy,true);if(Math.abs(StyleKit.signedArea(xy))<tinyArea)tiny++;}
            for(float[] cc:sh.circles){length+=2*Math.PI*cc[2];if(Math.PI*cc[2]*cc[2]<tinyArea)tiny++;}
            for(float[] xy:sh.lines){open++;verts+=xy.length/2;length+=perimeter(xy,false);}
        }
        return String.format(Locale.US,"%.4f\t%.4f\t%.4f\t%.4f\t%.3f\t%.3f\t%.3f\t%.4f\t%.4f\t%.4f\t%d\t%d\t%d\t%d\t%d\t%.0f\t%d\t%d\t%d\t%d\t%d\t%d\t%d",
            toneCorr,ss,edgeCorr,de,cov[0],cov[1],cov[2],err[0],err[1],err[2],pieces,holes,tiny,open,verts,length,
            r.findingsBefore,r.findingsAfter,r.holesLost,r.islandsLost,r.renderMs,r.cutReadyMs,r.peakHeapMb);
    }
    static final String HEADER="photo\tstyle\tfamily\tsize_in\ttone_corr\tssim\tedge_corr\tdE_oklab\tcov_hi\tcov_mid\tcov_sh\terr_hi\terr_mid\terr_sh"+
        "\tpieces\tholes\ttiny\topen\tvertices\tcut_len_mm\tfind_before\tfind_after\tholes_lost\tislands_lost\trender_ms\tcutready_ms\tpeak_heap_mb";
    static double perimeter(float[] xy,boolean closed){
        double s=0;int n=xy.length/2;
        for(int i=1;i<n;i++)s+=Math.hypot(xy[2*i]-xy[2*i-2],xy[2*i+1]-xy[2*i-1]);
        if(closed&&n>2)s+=Math.hypot(xy[0]-xy[2*n-2],xy[1]-xy[2*n-1]);
        return s;
    }

    static void resetPeaks(){System.gc();for(MemoryPoolMXBean m:ManagementFactory.getMemoryPoolMXBeans())if(m.getType()==MemoryType.HEAP)m.resetPeakUsage();}
    static long peakHeapMb(){long s=0;for(MemoryPoolMXBean m:ManagementFactory.getMemoryPoolMXBeans())if(m.getType()==MemoryType.HEAP)s+=m.getPeakUsage().getUsed();return s>>20;}

    public static void main(String[] a)throws Exception{
        File photos=new File(a[0]);Path out=Paths.get(a[1]);String label=a[2];
        boolean full=a.length>3&&"full".equals(a[3]);
        String only=System.getProperty("m4.only","");
        Files.createDirectories(out.resolve("img"));
        float minMm=Float.parseFloat(System.getProperty("m4.min","0.8")),gapMm=Float.parseFloat(System.getProperty("m4.bridge","0.5"));
        File[] files=photos.listFiles((d,n)->n.endsWith(".png")||n.endsWith(".jpg"));Arrays.sort(files);
        List<String> rows=new ArrayList<>();rows.add(HEADER);
        float[] sizes=full?new float[]{76.2f,152.4f,266.7f}:new float[]{152.4f};
        for(File f:files){
            Photo p=load(f,850);
            for(Case k:cases()){
                if(!only.isEmpty()&&!k.name.toLowerCase(Locale.US).contains(only.toLowerCase(Locale.US)))continue;
                for(float size:sizes){
                    String id=String.format(Locale.US,"%s__%s__%.0fin",p.name,k.name.replaceAll("[^A-Za-z0-9]+","-"),size/25.4f);
                    try{
                        Render r=render(k,p,size,minMm,gapMm);
                        float ppmm=Math.min(6f,1400f/Math.max(r.widthMm,r.heightMm));
                        BufferedImage comp=composite(r,ppmm);
                        String m=metrics(r,p,comp);
                        rows.add(p.name+"\t"+k.name+"\t"+k.family+"\t"+String.format(Locale.US,"%.1f",size/25.4f)+"\t"+m);
                        ImageIO.write(fit(comp,900),"png",out.resolve("img/"+id+".png").toFile());
                        System.out.println(label+" "+id+" "+m.split("\t")[0]);
                    }catch(Throwable t){
                        rows.add(p.name+"\t"+k.name+"\t"+k.family+"\t"+String.format(Locale.US,"%.1f",size/25.4f)+"\tERROR "+t);
                        System.out.println(label+" "+id+" ERROR "+t);
                    }
                }
            }
            ImageIO.write(fit(source(p,p.w,p.h),900),"png",out.resolve("img/"+p.name+"__source.png").toFile());
        }
        Files.write(out.resolve("metrics.tsv"),rows);
    }
    static BufferedImage fit(BufferedImage in,int max){
        if(Math.max(in.getWidth(),in.getHeight())<=max)return in;
        float s=max/(float)Math.max(in.getWidth(),in.getHeight());
        int w=Math.round(in.getWidth()*s),h=Math.round(in.getHeight()*s);
        BufferedImage o=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);Graphics2D g=o.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(in,0,0,w,h,null);g.dispose();return o;
    }
}

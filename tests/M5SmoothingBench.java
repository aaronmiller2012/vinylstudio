package com.halftonestudio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;
import org.locationtech.jts.operation.union.UnaryUnionOp;

/**
 * M5 contour-smoothing audit. For each suite style, size and photo it renders the real
 * engine output and the cut-ready stage, and measures the overlay contours:
 *  stair      - fraction of vertices in a pixel staircase (consecutive opposite turns of
 *               >= 20 degrees joined by a segment shorter than 1 mm);
 *  excess     - total absolute turning beyond 2 pi per loop, per mm of contour (rad/mm):
 *               a smooth outline is near 0, a staircase is large;
 *  rast       - share of contour length in short (< 2 mm) segments at exactly 0/45/90 deg;
 *  verts/10mm - vertex density.
 * With -Dm5.compare=true and a camouflage style, it also renders the same artwork with
 * contour smoothing off (NineLayerCraftPatternEngine directly, same mapping as the suite)
 * and reports per-sheet IoU, newly exposed foundation and inspector findings for both.
 * Usage: java -cp ...:jts M5SmoothingBench <photoDir> <outDir> <label> <ids> <sizesIn>
 */
public final class M5SmoothingBench {
    public static final class Stair { int loops,verts,stairV; double len,absTurn,rastLen;
        double stair(){return verts==0?0:stairV/(double)verts;}
        double excess(){return len==0?0:Math.max(0,absTurn-2*Math.PI*loops)/len;}
        double rast(){return len==0?0:rastLen/len;}
        double density(){return len==0?0:verts*10/len;} }
    static void stair(float[] raw,Stair s){
        float[] p=StyleKit.removeCollinear(raw,.001f);
        int n=p.length/2;if(n<3)return;
        s.loops++;s.verts+=n;
        double[] th=new double[n];double[] sl=new double[n];
        for(int i=0;i<n;i++){
            int a=(i+n-1)%n,b=(i+1)%n;
            double ux=p[2*i]-p[2*a],uy=p[2*i+1]-p[2*a+1],vx=p[2*b]-p[2*i],vy=p[2*b+1]-p[2*i+1];
            th[i]=Math.atan2(ux*vy-uy*vx,ux*vx+uy*vy);
            sl[i]=Math.hypot(vx,vy);s.len+=sl[i];s.absTurn+=Math.abs(th[i]);
            double ang=Math.toDegrees(Math.atan2(Math.abs(vy),Math.abs(vx)));
            boolean grid=Math.abs(ang)<.5||Math.abs(ang-45)<.5||Math.abs(ang-90)<.5;
            if(grid&&sl[i]<2)s.rastLen+=sl[i];
        }
        double lim=Math.toRadians(20);
        for(int i=0;i<n;i++){int b=(i+1)%n;
            if(Math.abs(th[i])>=lim&&Math.abs(th[b])>=lim&&Math.signum(th[i])!=Math.signum(th[b])&&sl[i]<1)s.stairV++;}
    }
    static Stair stair(StyleKit.Result r){Stair s=new Stair();
        for(int l=1;l<r.layers.size();l++)for(float[] p:r.layers.get(l).loops)stair(p,s);return s;}
    static StyleKit.Result craftDirect(int style,M4ArtBench.Photo p,float widthMm,float minMm,float bridge,int smoothing){
        VinylStyleSuite.Config c=M4ArtBench.suiteConfig(style,p,widthMm,minMm,bridge);
        NineLayerCraftPatternEngine.Options o=new NineLayerCraftPatternEngine.Options();
        o.pattern=style-VinylStyleSuite.CAMOUFLAGE;o.detail=Math.max(0,Math.min(100,c.detail));o.layers=c.colorCount;
        float min=Math.max(VinylEngine.MINIMUM_CUT_WIDTH_MM,c.minimumMm);
        o.widthMm=widthMm;o.minCutMm=Math.min(4f,min*1.12f+.04f);o.draft=false;o.paperArgb=c.paper;
        o.gapMm=StyleCutGeometryPreflight.exportGapMm(c.gapMm,min);
        o.palette=c.palette==null?null:c.palette.clone();o.allowed=c.allowed;
        // Reflective so the same bench also compiles against the pre-M5 engine (no field).
        try{NineLayerCraftPatternEngine.Options.class.getField("contourSmoothing").setInt(o,smoothing);}
        catch(ReflectiveOperationException absent){}
        return NineLayerCraftPatternEngine.generate(p.argb,p.w,p.h,o);
    }
    static Geometry union(StyleKit.Result r){
        ArrayList<Geometry> g=new ArrayList<>();
        for(int l=1;l<r.layers.size();l++)g.add(M5CutGeometryProbe.region(r.layers.get(l).loops));
        return UnaryUnionOp.union(g);
    }
    public static void main(String[] a)throws Exception{
        File photos=new File(a[0]);Path out=Paths.get(a[1]);String label=a[2];
        String[] ids=a[3].split(","),sizes=a[4].split(",");
        String only=System.getProperty("m5.photos","");
        boolean compare=Boolean.getBoolean("m5.compare");
        float minMm=.8f,bridge=.5f,gap=StyleCutGeometryPreflight.exportGapMm(bridge,minMm);
        Files.createDirectories(out.resolve("img"));
        File[] files=photos.listFiles((d,n)->n.endsWith(".png")||n.endsWith(".jpg"));Arrays.sort(files);
        List<String> rows=new ArrayList<>();
        rows.add("photo\tstyle\tsize_in\tstage\tloops\tverts\tlen_mm\tstair\texcess_rad_mm\trast\tverts_per10mm\tfindings\tms\tmin_iou\tmean_iou\texposed_mm2\texposed_pcs");
        for(File f:files){
            String name=f.getName().substring(0,f.getName().lastIndexOf('.'));
            if(!only.isEmpty()&&!Arrays.asList(only.split(",")).contains(name))continue;
            M4ArtBench.Photo p=M4ArtBench.load(f,850);
            for(String sid:ids)for(String sz:sizes){
                int style=Integer.parseInt(sid);float w=Float.parseFloat(sz)*25.4f;
                String tag=name+"\t"+style+"\t"+sz;
                try{
                    VinylStyleSuite.Config c=M4ArtBench.suiteConfig(style,p,w,minMm,bridge);
                    long t0=System.nanoTime();
                    StyleKit.Result r=VinylStyleSuite.generate(p.argb,p.w,p.h,c);
                    long ms=M4ArtBench.ms(t0);
                    StyleKit.Result cut=VinylStyleSuite.prepareForCutting(r,style,minMm,bridge);
                    rows.add(row(tag,"generated",r,minMm,gap,ms,""));
                    rows.add(row(tag,"cut_ready",cut,minMm,gap,-1,""));
                    if(compare&&VinylStyleSuite.isCraft(style)){
                        long t1=System.nanoTime();
                        StyleKit.Result off=craftDirect(style,p,w,minMm,bridge,0);
                        long msOff=M4ArtBench.ms(t1);
                        StyleKit.Result on=craftDirect(style,p,w,minMm,bridge,OrganicFlowCutSmoother.DEFAULT);
                        double minIou=1,sumIou=0;int k=0;
                        for(int l=1;l<on.layers.size();l++){
                            Geometry ga=M5CutGeometryProbe.region(off.layers.get(l).loops),gb=M5CutGeometryProbe.region(on.layers.get(l).loops);
                            double inter=OverlayNGRobust.overlay(ga,gb,OverlayNG.INTERSECTION).getArea();
                            double uni=OverlayNGRobust.overlay(ga,gb,OverlayNG.UNION).getArea();
                            double iou=uni<=0?1:inter/uni;minIou=Math.min(minIou,iou);sumIou+=iou;k++;
                        }
                        Geometry exposed=OverlayNGRobust.overlay(union(off),union(on),OverlayNG.DIFFERENCE);
                        int pcs=0;for(int i=0;i<exposed.getNumGeometries();i++)if(exposed.getGeometryN(i).getArea()>.01)pcs++;
                        String extra=String.format(Locale.US,"%.5f\t%.5f\t%.2f\t%d",minIou,sumIou/Math.max(1,k),exposed.getArea(),pcs);
                        rows.add(row(tag,"off_generated",off,minMm,gap,msOff,""));
                        rows.add(row(tag,"on_generated",on,minMm,gap,-1,extra));
                        StyleKit.Result offCut=VinylStyleSuite.prepareForCutting(off,style,minMm,bridge);
                        rows.add(row(tag,"off_cut_ready",offCut,minMm,gap,-1,""));
                        M4ArtBench.Render ro=render(offCut),rn=render(cut);
                        javax.imageio.ImageIO.write(M4ArtBench.composite(ro,8f).getSubimage(0,0,Math.min(480,Math.round(w*8)),480),"png",
                            out.resolve("img/"+name+"_"+style+"_"+sz+"in_off.png").toFile());
                        javax.imageio.ImageIO.write(M4ArtBench.composite(rn,8f).getSubimage(0,0,Math.min(480,Math.round(w*8)),480),"png",
                            out.resolve("img/"+name+"_"+style+"_"+sz+"in_on.png").toFile());
                    }
                    ImageIO.write(M4ArtBench.fit(M4ArtBench.composite(render(cut),Math.min(6f,1400f/w)),900),"png",
                        out.resolve("img/"+name+"_"+style+"_"+sz+"in.png").toFile());
                    System.out.println(label+" "+tag.replace('\t',' ')+" done");
                }catch(Throwable t){rows.add(tag+"\tERROR "+t);System.out.println(label+" "+tag+" ERROR "+t);t.printStackTrace(System.out);}
                Files.write(out.resolve("smoothing.tsv"),rows);
            }
        }
    }
    static M4ArtBench.Render render(StyleKit.Result r){
        M4ArtBench.Render rr=new M4ArtBench.Render();rr.widthMm=r.widthMm;rr.heightMm=r.heightMm;rr.paper=r.paperArgb;
        for(StyleKit.Layer l:r.layers){M4ArtBench.Sheet s=new M4ArtBench.Sheet();s.color=l.color;s.loops.addAll(l.loops);s.lines.addAll(l.lines);s.strokeMm=l.strokeMm;rr.sheets.add(s);}
        return rr;
    }
    static String row(String tag,String stage,StyleKit.Result r,float min,float gap,long ms,String extra){
        Stair s=stair(r);int find;
        try{find=StyleCutGeometryPreflight.inspect(r,min,gap,100000).size();}catch(RuntimeException e){find=-1;}
        return String.format(Locale.US,"%s\t%s\t%d\t%d\t%.0f\t%.4f\t%.4f\t%.4f\t%.2f\t%d\t%d\t%s",
            tag,stage,s.loops,s.verts,s.len,s.stair(),s.excess(),s.rast(),s.density(),find,ms,extra.isEmpty()?"\t\t\t":extra);
    }
}

package com.halftonestudio;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;
import org.locationtech.jts.operation.union.UnaryUnionOp;

/**
 * M5 raster de-staircasing (OrganicFlowCutSmoother.destairOrganicContours) on the real
 * camouflage engine output. Opt-in (Options.contourSmoothing, default 0), so the suite
 * output is unchanged; when enabled it must: cut the staircase fraction by >= 5x, keep
 * every sheet's silhouette (IoU >= 0.97, loop count and nesting), leave L1 bit-identical,
 * never push an overlay past L1, add no cut-inspector finding away from an existing one,
 * bound newly exposed foundation, and never touch Digital camo.
 */
public final class M5CamoDestairTest {
    static int failures=0;
    static void check(boolean ok,String what){
        if(ok)System.out.println("PASS "+what);else{failures++;System.out.println("FAIL "+what);}
    }
    static StyleKit.Result render(int style,M4ArtBench.Photo p,float widthMm,int smoothing){
        return M5SmoothingBench.craftDirect(style,p,widthMm,.8f,.5f,smoothing);
    }
    static boolean identical(List<float[]> a,List<float[]> b){
        if(a.size()!=b.size())return false;
        for(int i=0;i<a.size();i++)if(!Arrays.equals(a.get(i),b.get(i)))return false;
        return true;
    }
    public static void main(String[] args)throws Exception{
        File dir=new File(args.length>0?args[0]:"tests/m4/photos");
        M4ArtBench.Photo astro=M4ArtBench.load(new File(dir,"astronaut.png"),850);
        // Synthetic: a slanted bar traced from a binary raster is a staircase; the pass
        // straightens it without moving more than half a pixel.
        {
            int w=120,h=60;float mm=.3f;boolean[] valid=new boolean[w*h];Arrays.fill(valid,true);
            float[] field=new float[(w+2)*(h+2)];Arrays.fill(field,-1f);
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                double d=Math.abs((y-30)-(x-60)*.18);
                if(d<8&&x>10&&x<110)field[(y+1)*(w+2)+x+1]=1f;
            }
            List<float[]> loops=TopographicLayersEngine.contours(field,w+2,h+2,0f,mm);
            float[] raw=StyleKit.removeCollinear(loops.get(0),.03f);
            // Fully backed by another sheet: free to straighten in both directions.
            boolean[] backed=new boolean[w*h];Arrays.fill(backed,true);
            float[] out=OrganicFlowCutSmoother.destairLoop(raw,false,valid,backed,w,h,mm,1.6f,w*mm,h*mm);
            M5SmoothingBench.Stair a=new M5SmoothingBench.Stair(),b=new M5SmoothingBench.Stair();
            M5SmoothingBench.stair(raw,a);if(out!=null)M5SmoothingBench.stair(out,b);
            check(out!=null&&b.stair()<a.stair()*.25,"synthetic staircase "+a.stair()+" -> "+b.stair());
            double hd=out==null?1e9:hausdorff(raw,out);
            check(hd<=.5*mm+.03,"synthetic movement "+hd+" mm <= half pixel");
            // Over bare L1 (no backing sheet) it is material-preserving: area never
            // decreases beyond the 0.02 mm simplification band.
            float[] bare=OrganicFlowCutSmoother.destairLoop(raw,false,valid,null,w,h,mm,1.6f,w*mm,h*mm);
            check(bare!=null&&Math.abs(StyleKit.signedArea(bare))>=Math.abs(StyleKit.signedArea(raw))-.02*perimeter(raw),
                "unbacked smoothing never uncovers vinyl");
        }
        // Default is off: the suite renders exactly the unsmoothed engine output (goldens unchanged).
        VinylStyleSuite.Config c=M4ArtBench.suiteConfig(VinylStyleSuite.CAMOUFLAGE,astro,101.6f,.8f,.5f);
        StyleKit.Result viaSuite=VinylStyleSuite.generate(astro.argb,astro.w,astro.h,c);
        StyleKit.Result off4=render(VinylStyleSuite.CAMOUFLAGE,astro,101.6f,0);
        boolean same=true;for(int l=0;l<off4.layers.size();l++)same&=identical(viaSuite.layers.get(l).loops,off4.layers.get(l).loops);
        check(same&&new NineLayerCraftPatternEngine.Options().contourSmoothing==0,"default off: suite output unchanged");
        for(int style:new int[]{VinylStyleSuite.CAMOUFLAGE,VinylStyleSuite.CAMO_DESERT,VinylStyleSuite.CAMO_URBAN}){
            String tag=VinylStyleSuite.NAMES[style]+" 6 in";
            StyleKit.Result off=render(style,astro,152.4f,0),on=render(style,astro,152.4f,OrganicFlowCutSmoother.DEFAULT);
            check(identical(off.layers.get(0).loops,on.layers.get(0).loops),tag+" L1 foundation untouched");
            M5SmoothingBench.Stair so=M5SmoothingBench.stair(off),sn=M5SmoothingBench.stair(on);
            check(sn.stair()<so.stair()*.2,tag+" staircase vertex fraction "+fmt(so.stair())+" -> "+fmt(sn.stair()));
            check(sn.excess()<so.excess(),tag+" excess turning "+fmt(so.excess())+" -> "+fmt(sn.excess())+" rad/mm");
            Geometry base=M5CutGeometryProbe.region(on.layers.get(0).loops);
            double minIou=1,outside=0;boolean loopsKept=true;
            java.util.ArrayList<Geometry> uo=new java.util.ArrayList<>(),un=new java.util.ArrayList<>();
            for(int l=1;l<on.layers.size();l++){
                loopsKept&=on.layers.get(l).loops.size()==off.layers.get(l).loops.size();
                boolean[] ho=OrganicFlowCutSmoother.evenOddHoles(off.layers.get(l).loops),hn=OrganicFlowCutSmoother.evenOddHoles(on.layers.get(l).loops);
                loopsKept&=Arrays.equals(ho,hn);
                Geometry a=M5CutGeometryProbe.region(off.layers.get(l).loops),b=M5CutGeometryProbe.region(on.layers.get(l).loops);
                uo.add(a);un.add(b);
                double u=OverlayNGRobust.overlay(a,b,OverlayNG.UNION).getArea();
                if(u>0)minIou=Math.min(minIou,OverlayNGRobust.overlay(a,b,OverlayNG.INTERSECTION).getArea()/u);
                outside+=Math.max(0,OverlayNGRobust.overlay(b,base,OverlayNG.DIFFERENCE).getArea()
                    -OverlayNGRobust.overlay(a,base,OverlayNG.DIFFERENCE).getArea());
            }
            check(loopsKept,tag+" loop count and even-odd nesting preserved on every sheet");
            check(minIou>=.97,tag+" per-sheet silhouette IoU >= 0.97 (min "+fmt(minIou)+")");
            check(outside<1e-3,tag+" overlays do not grow past L1 ("+outside+" mm2)");
            double exposed=OverlayNGRobust.overlay(UnaryUnionOp.union(uo),UnaryUnionOp.union(un),OverlayNG.DIFFERENCE).getArea();
            double art=on.widthMm*on.heightMm;
            check(exposed<art*2e-4,tag+" newly exposed foundation "+fmt(exposed)+" mm2 (< 0.02% of artwork)");
            float gap=StyleCutGeometryPreflight.exportGapMm(.5f,.8f);
            int fresh=0;
            for(int l=1;l<on.layers.size();l++){
                List<StyleCutGeometryPreflight.Warning> wo=one(off,l,gap),wn=one(on,l,gap);
                for(StyleCutGeometryPreflight.Warning x:wn){boolean known=false;
                    for(StyleCutGeometryPreflight.Warning y:wo)if(Math.abs(x.xMm-y.xMm)<=.8f&&Math.abs(x.yMm-y.yMm)<=.8f){known=true;break;}
                    if(!known)fresh++;}
            }
            check(fresh==0,tag+" no cut-inspector finding away from an existing one ("+fresh+")");
        }
        StyleKit.Result d0=render(VinylStyleSuite.CAMO_DIGITAL,astro,101.6f,0),d1=render(VinylStyleSuite.CAMO_DIGITAL,astro,101.6f,100);
        boolean digital=true;for(int l=0;l<d0.layers.size();l++)digital&=identical(d0.layers.get(l).loops,d1.layers.get(l).loops);
        check(digital,"Digital camo stays pixel-square (smoothing ignored)");
        NineLayerCraftPatternEngine.Options bad=new NineLayerCraftPatternEngine.Options();bad.contourSmoothing=101;
        boolean rejected=false;try{bad.validate();}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"smoothing outside 0..100 rejected");
        if(failures>0){System.out.println(failures+" M5 camo destair check(s) FAILED");System.exit(1);}
        System.out.println("PASS M5 camo de-staircasing test");
    }
    static List<StyleCutGeometryPreflight.Warning> one(StyleKit.Result r,int l,float gap){
        StyleKit.Result x=new StyleKit.Result(r.widthMm,r.heightMm);x.layers.add(r.layers.get(l));
        return StyleCutGeometryPreflight.inspect(x,.8f,gap,100000);
    }
    static String fmt(double v){return String.format(java.util.Locale.US,"%.4f",v);}
    static double perimeter(float[] p){double s=0;int n=p.length/2;for(int i=0;i<n;i++){int j=(i+1)%n;s+=Math.hypot(p[2*j]-p[2*i],p[2*j+1]-p[2*i+1]);}return s;}
    static double hausdorff(float[] a,float[] b){return Math.max(directed(a,b),directed(b,a));}
    static double directed(float[] a,float[] b){
        double worst=0;int n=b.length/2;
        for(int i=0;i<a.length;i+=2){double best=1e18;
            for(int j=0;j<n;j++){int k=(j+1)%n;best=Math.min(best,seg(a[i],a[i+1],b[2*j],b[2*j+1],b[2*k],b[2*k+1]));}
            worst=Math.max(worst,best);}
        return worst;
    }
    static double seg(double px,double py,double ax,double ay,double bx,double by){
        double dx=bx-ax,dy=by-ay,l=dx*dx+dy*dy,t=l<1e-18?0:Math.max(0,Math.min(1,((px-ax)*dx+(py-ay)*dy)/l));
        return Math.hypot(px-ax-t*dx,py-ay-t*dy);
    }
}

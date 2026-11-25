package com.halftonestudio;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * M5 Linocut relief polarity, measured on the FINAL production geometry (VinylStyleSuite.generate and
 * the export cut-ready stage), never on nominal widths:
 *  - shadows invert: dark tones are a solid block with white gouges, coverage keeps rising past the
 *    M4 ~50 % ceiling, with no seam (coverage dip or jump) where black-line turns into white-line;
 *  - halos: light strokes come as close to a dark band as the mean of both spacings allows;
 *  - facial features (eyes, brows, nostrils, mouth, glasses) keep ink, the skin between them stays light;
 *  - cut safety: no self-intersection or crossing loops, gouges are holes inside the block, every
 *    vinyl piece and weed gap passes the classified export inspector (findings counted at 3-11 in
 *    including an 11 in portrait), loop budget, Snip containment, determinism, cross sheet keeps the
 *    gouges open, all-white / all-black / tiny / NaN inputs.
 */
public final class M5LinocutReliefTest {
    static int failures=0;
    static void check(boolean ok,String what){System.out.println((ok?"  ok   ":"  FAIL ")+what);if(!ok)failures++;}

    public static void main(String[] a)throws Exception{
        polarityAndSeam();
        halo();
        features();
        physical();
        edgeCases();
        if(failures>0)throw new AssertionError(failures+" M5 Linocut relief failures");
        System.out.println("PASS M5 Linocut relief polarity, halos, features and final cut geometry");
    }

    // ------------------------------------------------------------------ helpers
    static VinylStyleSuite.Config config(float widthMm){
        VinylStyleSuite.Config c=new VinylStyleSuite.Config();
        c.style=VinylStyleSuite.LINOCUT;c.variant=0;c.detail=55;c.weightMm=.7f;c.widthMm=widthMm;
        c.minimumMm=.8f;c.gapMm=.5f;c.colorCount=2;c.palette=new int[]{0xff15151a};
        return c;
    }
    /** The suite's Linocut options with the M5 switches set as requested (M4 = all off). */
    static LinocutEngine.Options options(VinylStyleSuite.Config c,boolean m5){
        LinocutEngine.Options o=new LinocutEngine.Options();o.widthMm=c.widthMm;
        float min=Math.max(VinylEngine.MINIMUM_CUT_WIDTH_MM,c.minimumMm),f=c.detail/100f;
        o.gapMm=StyleCutGeometryPreflight.exportGapMm(c.gapMm,min);o.minWidthMm=min+.08f;
        o.pitchMm=Math.max(Math.max(Math.max(min*2.2f,1.0f),o.minWidthMm+o.gapMm*1.6f),3.35f-2.35f*f);
        o.maxWidthFraction=StyleKit.clampF(.47f+.24f*c.weightMm,.28f,.86f);
        o.flowSmoothMm=Math.max(2.1f,4.2f-1.6f*f);o.cutoff=.11f;o.minLengthMm=2.1f;
        o.shadowCarve=m5;o.balancedSpacing=m5;o.featureContrast=m5?.8f:0f;
        return o;
    }
    static int[] gray(int w,int h,java.util.function.IntBinaryOperator v){
        int[] p=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int g=Math.max(0,Math.min(255,v.applyAsInt(x,y)));p[y*w+x]=0xff000000|(g<<16)|(g<<8)|g;}
        return p;
    }
    /** Even-odd raster of one sheet at ppmm pixels per mm: true = vinyl. */
    static boolean[] raster(List<float[]> loops,float wMm,float hMm,float ppmm,int[] dims){
        int w=Math.max(1,Math.round(wMm*ppmm)),h=Math.max(1,Math.round(hMm*ppmm));
        BufferedImage img=new BufferedImage(w,h,BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g=img.createGraphics();g.setColor(Color.BLACK);g.fillRect(0,0,w,h);
        g.scale(ppmm,ppmm);g.setColor(Color.WHITE);
        Path2D.Float path=new Path2D.Float(Path2D.WIND_EVEN_ODD);
        for(float[] l:loops){if(l.length<6)continue;path.moveTo(l[0],l[1]);for(int i=2;i+1<l.length;i+=2)path.lineTo(l[i],l[i+1]);path.closePath();}
        g.fill(path);g.dispose();
        byte[] px=((java.awt.image.DataBufferByte)img.getRaster().getDataBuffer()).getData();
        boolean[] m=new boolean[w*h];for(int i=0;i<m.length;i++)m[i]=(px[i]&255)>127;
        dims[0]=w;dims[1]=h;return m;
    }
    static List<float[]> allLoops(StyleKit.Result r){List<float[]> l=new ArrayList<>();for(StyleKit.Layer x:r.layers)l.addAll(x.loops);return l;}
    static StyleKit.Layer layer(StyleKit.Result r,String name){for(StyleKit.Layer l:r.layers)if(l.name.equals(name))return l;return null;}

    // ------------------------------------------------------------------ 1. polarity and seam
    static void polarityAndSeam(){
        System.out.println("1. Shadow polarity on a tonal ramp (6 in, 0.8 mm minimum, 0.5 mm gap)");
        int w=480,h=240;
        int[] px=gray(w,h,(x,y)->255-(int)(255f*x/(w-1)));
        VinylStyleSuite.Config c=config(152.4f);
        double[][] cov=new double[2][];
        for(int m=0;m<2;m++){
            StyleKit.Result r=m==1?VinylStyleSuite.generate(px,w,h,c):LinocutEngine.generate(px,w,h,options(c,false));
            int[] d=new int[2];boolean[] ink=raster(layer(r,"linocut-ink").loops,r.widthMm,r.heightMm,8f,d);
            int bands=10;double[] cv=new double[bands];int[] n=new int[bands];
            for(int y=d[1]/8;y<d[1]*7/8;y++)for(int x=0;x<d[0];x++){int b=Math.min(bands-1,x*bands/d[0]);n[b]++;if(ink[y*d[0]+x])cv[b]++;}
            for(int b=0;b<bands;b++)cv[b]/=Math.max(1,n[b]);
            cov[m]=cv;
            System.out.println("   "+(m==0?"M4":"M5")+" coverage by darkness decile: "+fmt(cv));
        }
        double m4Dark=(cov[0][8]+cov[0][9])/2,m5Dark=(cov[1][8]+cov[1][9])/2;
        check(m4Dark<.62,String.format(Locale.US,"M4 reference: darkest fifth tops out at %.2f (the reported ceiling)",m4Dark));
        check(m5Dark>=.80,String.format(Locale.US,"M5 darkest fifth is solid block with gouges: %.2f >= 0.80",m5Dark));
        check(cov[1][7]>=cov[0][7]+.15,String.format(Locale.US,"M5 decile 8 (%.2f) clearly darker than M4 (%.2f)",cov[1][7],cov[0][7]));
        // Light deciles keep the M4 black-line rendering (on a uniform ramp the structure field saturates,
        // so M4 already draws more there than on photos; M5 must not add to it).
        check(cov[1][0]<=cov[0][0]+.05&&cov[1][1]<=cov[0][1]+.08,String.format(Locale.US,"highlights not darkened vs M4 (%.2f/%.2f vs %.2f/%.2f)",cov[1][0],cov[1][1],cov[0][0],cov[0][1]));
        double worstDip=0,worstJump=0;
        for(int b=5;b<10;b++){worstDip=Math.max(worstDip,cov[1][b-1]-cov[1][b]);worstJump=Math.max(worstJump,cov[1][b]-cov[1][b-1]);}
        check(worstDip<=.06,String.format(Locale.US,"from mid-tone into shadow coverage never falls (worst dip %.3f <= 0.06): no light seam at the polarity switch",worstDip));
        check(worstJump<=.30,String.format(Locale.US,"no hard step between deciles 5-10 (worst rise %.3f <= 0.30): smooth transition band",worstJump));
        // The darkest fifth must still be CARVED (white gouges), not a flat stencil, until the very deepest tone.
        StyleKit.Result r=VinylStyleSuite.generate(px,w,h,c);
        int holes=M4ArtBench.topology(layer(r,"linocut-ink").loops)[1];
        check(holes>=10,"shadow block carries white gouges (holes = "+holes+")");
    }
    static String fmt(double[] v){StringBuilder s=new StringBuilder();for(double x:v)s.append(String.format(Locale.US,"%.2f ",x));return s.toString();}

    // ------------------------------------------------------------------ 2. halo
    static void halo(){
        System.out.println("2. Halo beside a dark stroke: 2 mm dark line (0.85) on a light-mid ground (0.30), 6 in");
        int w=480,h=320;
        VinylStyleSuite.Config c=config(152.4f);
        final float lineMm=2f,mmPerPx=152.4f/w,cx=w*.5f;
        int[] px=gray(w,h,(x,y)->Math.abs(x-cx)*mmPerPx<lineMm/2?(int)(255*(1-.85f)):(int)(255*(1-.30f)));
        double[] ratio=new double[2];
        for(int m=0;m<2;m++){
            LinocutEngine.Options o=options(c,m==1);o.autoLevels=false;o.featureContrast=0f;
            StyleKit.Result r=LinocutEngine.generate(px,w,h,o);
            int[] d=new int[2];float ppmm=8f;boolean[] ink=raster(r.layers.get(0).loops,r.widthMm,r.heightMm,ppmm,d);
            float edge=cx*mmPerPx+lineMm/2+.25f,p=o.pitchMm;
            double near=0,far=0;int nn=0,nf=0;
            for(int y=d[1]/8;y<d[1]*7/8;y++)for(int x=0;x<d[0];x++){
                float dist=Math.abs(x/ppmm-cx*mmPerPx)-lineMm/2;
                if(dist>.25f&&dist<.25f+2f*p){nn++;if(ink[y*d[0]+x])near++;}
                else if(dist>8*p&&dist<16*p){nf++;if(ink[y*d[0]+x])far++;}
            }
            near/=Math.max(1,nn);far/=Math.max(1,nf);ratio[m]=near/Math.max(1e-6,far);
            System.out.printf(Locale.US,"   %s coverage within 2 spacings of the line %.3f, far %.3f, ratio %.2f%n",m==0?"M4":"M5",near,far,ratio[m]);
        }
        check(ratio[1]>=.80,String.format(Locale.US,"M5 no white halo beside the dark line: near/far %.2f >= 0.80 (M4 %.2f)",ratio[1],ratio[0]));
    }

    // ------------------------------------------------------------------ 3. features
    static void features(){
        System.out.println("3. Synthetic portrait with glasses: features keep ink, skin stays light (3, 6, 11 in)");
        int w=400,h=500;
        // light skin oval on a mid background; dark eyes, brows, nostrils, mouth and round glasses frames.
        double[][] eyes={{150,215},{250,215}};
        int[] px=gray(w,h,(x,y)->{
            double fx=(x-200)/150.0,fy=(y-250)/200.0;boolean face=fx*fx+fy*fy<1;
            int v=face?218:120;
            for(double[] e:eyes){
                double ex=(x-e[0])/22.0,ey=(y-e[1])/11.0;if(ex*ex+ey*ey<1)v=35;
                double r=Math.hypot(x-e[0],y-e[1]);if(r>34&&r<40)v=40;                    // glasses frame
                if(Math.abs(y-(e[1]-30+Math.pow((x-e[0])/30.0,2)*6))<5&&Math.abs(x-e[0])<30)v=70; // brow
            }
            if(Math.abs(x-200)<8&&y>285&&y<300&&Math.abs(x-200)>2)v=60;                      // nostrils
            if(Math.abs(y-(350+Math.pow((x-200)/45.0,2)*-10))<4&&Math.abs(x-200)<45)v=55;      // mouth
            return v;});
        for(float in:new float[]{3f,6f,11f}){
            VinylStyleSuite.Config c=config(in*25.4f);
            StyleKit.Result r=VinylStyleSuite.generate(px,w,h,c);
            int[] d=new int[2];float ppmm=Math.min(8f,1600f/r.widthMm);
            boolean[] ink=raster(layer(r,"linocut-ink").loops,r.widthMm,r.heightMm,ppmm,d);
            float s=r.widthMm/w;
            double eye=0,skin=0,mouth=0,frame=0;int ne=0,ns=0,nm=0,nfr=0;
            for(int y=0;y<d[1];y++)for(int x=0;x<d[0];x++){
                float sx=x/ppmm/s,sy=y/ppmm/s;boolean v=ink[y*d[0]+x];
                boolean inEye=false,inFrame=false;
                for(double[] e:eyes){double ex=(sx-e[0])/22.0,ey=(sy-e[1])/11.0;if(ex*ex+ey*ey<.8)inEye=true;double rr=Math.hypot(sx-e[0],sy-e[1]);if(rr>28&&rr<46)inFrame=true;}
                if(inEye){ne++;if(v)eye++;}
                if(inFrame){nfr++;if(v)frame++;}
                if(Math.abs(sy-(350-Math.pow((sx-200)/45.0,2)*10))<10&&Math.abs(sx-200)<40){nm++;if(v)mouth++;}
                double fx=(sx-200)/150.0,fy=(sy-250)/200.0;
                if(fx*fx+fy*fy<.8&&!inFrame&&!inEye&&Math.abs(sy-(350-Math.pow((sx-200)/45.0,2)*10))>25&&sy>130&&sy<420&&Math.abs(sy-185)>20&&!(Math.abs(sx-200)<16&&sy>270&&sy<315)){ns++;if(v)skin++;}
            }
            eye/=Math.max(1,ne);skin/=Math.max(1,ns);mouth/=Math.max(1,nm);frame/=Math.max(1,nfr);
            System.out.printf(Locale.US,"   %2.0f in: eyes %.2f, glasses band %.2f, mouth band %.2f, skin %.2f%n",in,eye,frame,mouth,skin);
            check(eye>=.55,String.format(Locale.US,"%.0f in eyes are inked (%.2f >= 0.55)",in,eye));
            check(eye>=skin+.35,String.format(Locale.US,"%.0f in eyes stand out from skin by >= 0.35",in));
            check(frame>=skin+.12,String.format(Locale.US,"%.0f in glasses band darker than skin by >= 0.12",in));
            check(mouth>=skin+.12,String.format(Locale.US,"%.0f in mouth band darker than skin by >= 0.12",in));
            check(skin<=.30,String.format(Locale.US,"%.0f in skin stays light (%.2f <= 0.30)",in,skin));
        }
    }

    // ------------------------------------------------------------------ 4. physical cut geometry
    static void physical()throws Exception{
        System.out.println("4. Final cut geometry on real photos, 3 / 4 / 6 / 8 / 11 in and an 11 in portrait");
        M4ArtBench.Photo astro=M4ArtBench.load(new File("tests/m4/photos/astronaut.png"),850);
        // 3:4 portrait crop of the astronaut, rendered 8.25 in wide = 11 in tall.
        int cw=astro.h*3/4,ox=(astro.w-cw)/2;int[] crop=new int[cw*astro.h];
        for(int y=0;y<astro.h;y++)System.arraycopy(astro.argb,y*astro.w+ox,crop,y*cw,cw);
        Object[][] cases={
            {"astronaut",astro.argb,astro.w,astro.h,3f},{"astronaut",astro.argb,astro.w,astro.h,4f},{"astronaut",astro.argb,astro.w,astro.h,6f},
            {"astronaut",astro.argb,astro.w,astro.h,8f},{"astronaut",astro.argb,astro.w,astro.h,11f},
            {"astronaut-portrait-11in-tall",crop,cw,astro.h,8.25f}};
        List<Object[]> all=new ArrayList<>(Arrays.asList(cases));
        for(String n:new String[]{"camera.png","chelsea.png","moon.png"}){M4ArtBench.Photo p=M4ArtBench.load(new File("tests/m4/photos",n),850);all.add(new Object[]{p.name,p.argb,p.w,p.h,6f});}
        for(Object[] k:all){
            String name=(String)k[0];int[] px=(int[])k[1];int w=(Integer)k[2],h=(Integer)k[3];float in=(Float)k[4];
            VinylStyleSuite.Config c=config(in*25.4f);
            for(int variant=0;variant<(in==6f&&name.equals("astronaut")?2:1);variant++){
                c.variant=variant;c.palette=variant==0?new int[]{0xff15151a}:new int[]{0xff15151a,0xff7a2830};
                StyleKit.Result r=VinylStyleSuite.generate(px,w,h,c);
                String tag=String.format(Locale.US,"%s %.2f in v%d",name,in,variant);
                geometry(r,tag,.8f,.5f);
            }
        }
        // Cut-setting extremes at 6 in: Removable preset (0.65 / 0.25), thick material (1.1 / 0.5), Detail 0 and 100, weight extremes.
        float[][] settings={{.65f,.25f,55,.7f},{1.1f,.5f,55,.7f},{.8f,.5f,0,.2f},{.8f,.5f,100,1.6f}};
        for(float[] s:settings){
            VinylStyleSuite.Config c=config(152.4f);c.minimumMm=s[0];c.gapMm=s[1];c.detail=(int)s[2];c.weightMm=s[3];
            StyleKit.Result r=VinylStyleSuite.generate(astro.argb,astro.w,astro.h,c);
            geometry(r,String.format(Locale.US,"astronaut 6 in min %.2f gap %.2f detail %.0f weight %.1f",s[0],s[1],s[2],s[3]),s[0],s[1]);
        }
    }
    static void geometry(StyleKit.Result r,String tag,float min,float bridge){
        float gap=StyleCutGeometryPreflight.exportGapMm(bridge,min);
        int loops=0;boolean finite=true,inside=true,simple=true;
        for(StyleKit.Layer l:r.layers){
            loops+=l.loops.size();
            for(float[] p:l.loops){
                for(int i=0;i<p.length;i+=2){
                    if(!Float.isFinite(p[i])||!Float.isFinite(p[i+1]))finite=false;
                    if(p[i]<-1e-3f||p[i+1]<-1e-3f||p[i]>r.widthMm+1e-3f||p[i+1]>r.heightMm+1e-3f)inside=false;
                }
                if(StyleKit.selfIntersections(p,1)>0)simple=false;
            }
        }
        List<StyleCutGeometryPreflight.Warning> found=StyleCutGeometryPreflight.inspect(r,min,gap,100000);
        int crossing=0;for(StyleCutGeometryPreflight.Warning x:found)if(x.category==StyleCutGeometryPreflight.Category.INTERSECTION||x.category==StyleCutGeometryPreflight.Category.OVERLAP)crossing++;
        StyleKit.Layer ink=layer(r,"linocut-ink");
        int[] topo=ink==null?new int[2]:M4ArtBench.topology(ink.loops);
        int maxDepth=ink==null?0:maxDepth(ink.loops);
        StyleKit.Result cut=VinylStyleSuite.prepareForCutting(r,VinylStyleSuite.LINOCUT,min,bridge);
        int after=StyleCutGeometryPreflight.inspect(cut,min,gap,100000).size();
        System.out.printf(Locale.US,"   %-52s loops %5d pieces %5d holes %4d depth %d findings %4d (crossing %d) after cut prep %4d%n",tag,loops,topo[0],topo[1],maxDepth,found.size(),crossing,after);
        check(finite&&inside,tag+": finite coordinates inside the artboard");
        check(simple,tag+": no self-intersecting loop");
        check(crossing==0,tag+": no crossing or overlapping loops (even-odd holes are clean)");
        check(maxDepth<=3,tag+": nesting at most block > gouge > stroke-in-light-pocket");
        check(loops<=LinocutEngine.MAX_INK_LOOPS+400,tag+": loop budget");
        check(found.size()<=Math.max(2,loops/250),tag+": classified width/gap findings before cut prep <= max(2, 0.4 % of loops) ("+found.size()+")");
        check(after<=found.size(),tag+": cut prep never adds findings");
        // Cut prep must not repaint the sheet: with clean generated geometry it is a no-op, so light
        // minimum-width strokes are not erased by a whole-sheet raster repair (the 8-11 in light-tone loss).
        double before=0,afterArea=0;
        for(StyleKit.Layer l:r.layers)for(float[] p:l.loops)before+=Math.abs(StyleKit.signedArea(p));
        for(StyleKit.Layer l:cut.layers)for(float[] p:l.loops)afterArea+=Math.abs(StyleKit.signedArea(p));
        int loopsAfter=0;for(StyleKit.Layer l:cut.layers)loopsAfter+=l.loops.size();
        check(loopsAfter>=loops*.97,tag+": cut prep keeps the strokes ("+loops+" -> "+loopsAfter+" loops)");
    }
    static int maxDepth(List<float[]> loops){
        int max=0;
        for(int i=0;i<loops.size();i++){
            float[] p=loops.get(i);int d=0;
            for(int j=0;j<loops.size();j++)if(j!=i&&Math.abs(StyleKit.signedArea(loops.get(j)))>Math.abs(StyleKit.signedArea(p))&&M4ArtBench.inside(loops.get(j),p[0],p[1]))d++;
            max=Math.max(max,d);
        }
        return max;
    }

    // ------------------------------------------------------------------ 5. edge cases
    static void edgeCases(){
        System.out.println("5. Edge cases");
        VinylStyleSuite.Config c=config(76.2f);
        // All white: nothing to cut, no exception.
        StyleKit.Result white=VinylStyleSuite.generate(gray(120,120,(x,y)->255),120,120,c);
        check(allLoops(white).isEmpty(),"all-white photo: no vinyl ("+allLoops(white).size()+" loops)");
        // All black: one solid block (no gouges in the deepest tone), inside the artboard.
        StyleKit.Result black=VinylStyleSuite.generate(gray(120,120,(x,y)->0),120,120,c);
        int[] t=M4ArtBench.topology(allLoops(black));
        check(t[0]>=1&&t[1]==0,"all-black photo: solid block, no carving ("+t[0]+" pieces, "+t[1]+" holes)");
        double area=0;for(float[] l:allLoops(black))area+=Math.abs(StyleKit.signedArea(l));
        check(area>=.85*black.widthMm*black.heightMm,String.format(Locale.US,"all-black block covers %.0f%% of the artboard",100*area/(black.widthMm*black.heightMm)));
        // Tiny photo.
        try{VinylStyleSuite.generate(gray(4,4,(x,y)->40*x),4,4,c);check(true,"4x4 photo renders");}
        catch(IllegalArgumentException ok){check(true,"4x4 photo rejected cleanly: "+ok.getMessage());}
        // Snip: circular mask. All ink must lie inside the circle (within one weed gap of rounding).
        int w=300,h=300;boolean[] allowed=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)allowed[y*w+x]=Math.hypot(x-150,y-150)<120;
        VinylStyleSuite.Config cs=config(152.4f);cs.allowed=allowed;
        StyleKit.Result snip=VinylStyleSuite.generate(gray(w,h,(x,y)->(int)(255*(x/(float)w))),w,h,cs);
        float s=snip.widthMm/w,R=120*s,cx=150*s;double worst=0;
        for(float[] l:allLoops(snip))for(int i=0;i<l.length;i+=2)worst=Math.max(worst,Math.hypot(l[i]-cx,l[i+1]-cx)-R);
        check(worst<=.75,String.format(Locale.US,"Snip circle: ink extends at most %.2f mm past the mask edge",worst));
        // Determinism.
        StyleKit.Result r1=VinylStyleSuite.generate(gray(160,160,(x,y)->(x*y)%256),160,160,c),r2=VinylStyleSuite.generate(gray(160,160,(x,y)->(x*y)%256),160,160,c);
        boolean same=r1.layers.size()==r2.layers.size();
        for(int i=0;same&&i<r1.layers.size();i++){List<float[]> A=r1.layers.get(i).loops,B=r2.layers.get(i).loops;same=A.size()==B.size();for(int k=0;same&&k<A.size();k++)same=Arrays.equals(A.get(k),B.get(k));}
        check(same,"deterministic geometry for identical input");
        // Cross sheet keeps the gouges open: no cross vinyl over carved white.
        int[] ramp=gray(320,240,(x,y)->255-(int)(255f*x/319));
        VinylStyleSuite.Config cc=config(152.4f);cc.variant=1;cc.palette=new int[]{0xff15151a,0xff7a2830};
        StyleKit.Result cross=VinylStyleSuite.generate(ramp,320,240,cc);
        StyleKit.Layer inkL=layer(cross,"linocut-ink"),crossL=layer(cross,"linocut-cross");
        if(inkL!=null&&crossL!=null){
            // Carved white = the block's holes (odd nesting depth). The cross sheet must not cover them.
            ArrayList<float[]> holes=new ArrayList<>();
            int[] depth=LinocutEngine.nestingDepth(inkL.loops);
            for(int i=0;i<depth.length;i++)if((depth[i]&1)==1)holes.add(inkL.loops.get(i));
            int[] d=new int[2];boolean[] hole=raster(holes,cross.widthMm,cross.heightMm,8f,d);
            boolean[] cr=raster(crossL.loops,cross.widthMm,cross.heightMm,8f,d);
            int over=0,gouge=0;
            for(int i=0;i<hole.length;i++)if(hole[i]){gouge++;if(cr[i])over++;}
            check(gouge>0&&over<=gouge*.02,"cross sheet leaves carved gouges open ("+over+" of "+gouge+" gouge pixels covered)");
        }else check(crossL==null||inkL!=null,"cross variant produced its sheets");
        // NaN / out-of-range options are rejected, never drawn.
        for(String bad:new String[]{"carveTone","featureContrast","pitchMm","gapMm"}){
            LinocutEngine.Options o=options(config(76.2f),true);
            switch(bad){case "carveTone":o.carveTone=Float.NaN;break;case "featureContrast":o.featureContrast=Float.NaN;break;case "pitchMm":o.pitchMm=Float.NaN;break;default:o.gapMm=-1f;}
            boolean threw=false;try{LinocutEngine.generate(gray(40,40,(x,y)->x*6),40,40,o);}catch(IllegalArgumentException e){threw=true;}
            check(threw,"invalid "+bad+" is rejected");
        }
    }
}

package com.halftonestudio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * M7 device-performance changes: behavioral equivalence tests.
 * Sections run by name: p1 (SVG number parser), p3 (stencil refiner early exit),
 * p2 (cancellation latency). Run all with no arguments.
 */
public final class M7DevicePerfTest {
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}

    public static void main(String[] args) throws Exception {
        List<String> run=args.length==0?List.of("p1","p2","p3","b5","m1","m2","m3"):List.of(args);
        if(run.contains("p1"))p1();
        if(run.contains("p2"))p2();
        if(run.contains("p3"))p3();
        if(run.contains("b5"))b5();
        if(run.contains("m1"))m1();
        if(run.contains("m2"))m2();
        if(run.contains("m3"))m3();
        System.out.println("PASS M7DevicePerfTest "+run);
    }

    // ------------------------------------------------------------------ P1
    /** Same float bits AND same exception class as Float.parseFloat. */
    static void same(String s){
        Object expect,got;
        try{expect=Float.floatToRawIntBits(Float.parseFloat(s));}catch(RuntimeException e){expect=e.getClass();}
        try{got=Float.floatToRawIntBits(SvgNumber.parse("#"+s+"#",1,s.length()+1));}catch(RuntimeException e){got=e.getClass();}
        if(!expect.equals(got))throw new AssertionError("SvgNumber mismatch for '"+s+"': "+got+" vs Float.parseFloat "+expect);
    }

    static void p1() throws Exception {
        String[] edge={"0","-0","+0","-0.0","0.0","00012.5000","-.5",".5","5.","-5.","1","-1","+1.25",
            "16777215","16777216","16777217","-16777217","123456789","0.1","0.2","0.3","1.1","-152.4","292.1",
            "0.0000000001","0.00000000001","1.0000000001","9999999.999","3.4028235E38","3.4028236E38","1e39","-1e39",
            "1.4E-45","1e-46","1e5","1E5","2.5e-3","-2.5E+3","1e","e5","-","+",".","-.","1.2.3","1-2","1+","--1",
            "+-1","1e5.5","NaN","Infinity","-Infinity","0x1p3","1f","1d"," 1","1 ",""};
        for(String s:edge)same(s);
        Random r=new Random(565);
        long n=0;
        for(int i=0;i<400_000;i++){
            float f=Float.intBitsToFloat(r.nextInt());
            if(!Float.isFinite(f))continue;
            same(Float.toString(f));n++;
            double d=(r.nextDouble()-.5)*Math.pow(10,r.nextInt(9));
            for(int p=0;p<=11;p++){same(String.format(Locale.US,"%."+p+"f",d));n++;}
            same(Long.toString(r.nextLong()%100_000_000L));n++;
        }
        // Real exported coordinates: every number token of every layer SVG written for
        // every selectable suite style at the smallest golden size.
        int[] photo=V3AllStyleExportMatrixTest.portrait(60,60);
        long real=0;
        for(int id=0;id<VinylStyleSuite.NAMES.length;id++){
            if(VinylStyleSuite.isRetiredStyleId(id)||!NewStyleVariantPolicy.selectable(id))continue;
            VinylStyleSuite.Config c=new VinylStyleSuite.Config();
            c.style=id;c.widthMm=76.2f;c.detail=25;c.colorCount=4;c.minimumMm=.254f;c.draft=false;c.weightMm=.7f;
            StyleKit.Result res=VinylStyleSuite.generate(photo,60,60,c);
            for(int i=0;i<res.layers.size();i++){
                StyleKit.Layer l=res.layers.get(i);if(l.loops.isEmpty()&&l.lines.isEmpty())continue;
                String svg=NewStyleExport.layerSvg(res,i,false,0,3f,2f,null,.254f);
                int k=0;
                while(k<svg.length()){
                    char ch=svg.charAt(k);
                    if((ch>='0'&&ch<='9')||ch=='-'||ch=='.'){
                        int from=k;while(k<svg.length()&&SvgNumber.tokenChar(svg.charAt(k)))k++;
                        same(svg.substring(from,k));real++;
                    }else k++;
                }
            }
        }
        check(real>100_000,"too few real SVG coordinates harvested: "+real);
        System.out.println("PASS P1 SvgNumber == Float.parseFloat on "+n+" synthetic and "+real+" exported tokens");
    }

    // ------------------------------------------------------------------ P2
    /** Reference: the pre-M7 run counting (no cancellation), for exact-count parity. */
    static int[] referenceCounts(MultilayerStencilEngine.Result result,int index,float minMm,float gapMm){
        boolean[] m=result.cutRasterForInspection(index);
        int w=result.width,h=result.height,hcount=0,vcount=0,gapH=0,gapV=0;
        float cw=result.widthMm/w,ch=result.heightMm/h;
        for(int y=0;y<h;y++)for(int x=0;x<w;){if(!m[y*w+x]){x++;continue;}int st=x;while(x<w&&m[y*w+x])x++;if((x-st)*cw<minMm)hcount+=x-st;}
        for(int x=0;x<w;x++)for(int y=0;y<h;){if(!m[y*w+x]){y++;continue;}int st=y;while(y<h&&m[y*w+x])y++;if((y-st)*ch<minMm)vcount+=y-st;}
        for(int y=0;y<h;y++)for(int x=0;x<w;){if(m[y*w+x]){x++;continue;}int st=x;while(x<w&&!m[y*w+x])x++;if(st>0&&x<w&&(x-st)*cw<gapMm)gapH+=x-st;}
        for(int x=0;x<w;x++)for(int y=0;y<h;){if(m[y*w+x]){y++;continue;}int st=y;while(y<h&&!m[y*w+x])y++;if(st>0&&y<h&&(y-st)*ch<gapMm)gapV+=y-st;}
        return new int[]{hcount,vcount,gapH,gapV};
    }

    static void p2() throws Exception {
        int w=900,h=900;
        int[] argb=V3AllStyleExportMatrixTest.portrait(w,h);
        MultilayerStencilEngine.Options o=new MultilayerStencilEngine.Options();
        o.registration=false;o.layerCount=5;
        MultilayerStencilEngine.Result r=MultilayerStencilEngine.generate(argb,w,h,o);
        check(r.layers.size()>=3,"stencil layers");
        // 1. Exact parity with the previous counting for every layer and two tolerances.
        for(int i=0;i<r.layers.size();i++)for(float min:new float[]{.6f,1.2f}){
            VinylCutPreflight.Report rep=VinylCutPreflight.inspect(r,i,min);
            int[] ref=referenceCounts(r,i,min,Math.max(.35f,.6f*min));
            check(rep.narrowHorizontalPixels==ref[0]&&rep.narrowVerticalPixels==ref[1]&&
                rep.narrowGapPixelsHorizontal==ref[2]&&rep.narrowGapPixelsVertical==ref[3],
                "preflight counts changed for layer "+i+" min "+min);
        }
        // 2. Cancellation is still honoured, and promptly, while several workers inspect at once.
        final int workers=Math.max(2,Math.min(6,Runtime.getRuntime().availableProcessors()-1));
        long worstNs=0;int cancelled=0;
        for(int round=0;round<30;round++){
            Thread[] t=new Thread[workers];final long[] doneAt=new long[workers];final boolean[] threw=new boolean[workers];
            for(int k=0;k<workers;k++){final int id=k;t[k]=new Thread(()->{
                try{while(true)for(int i=0;i<r.layers.size();i++)VinylCutPreflight.inspect(r,i,.8f);}
                catch(java.util.concurrent.CancellationException expected){threw[id]=true;}
                finally{doneAt[id]=System.nanoTime();}
            });t[k].start();}
            Thread.sleep(15+round%7);
            long sent=System.nanoTime();
            for(Thread x:t)x.interrupt();
            for(Thread x:t){x.join(5000);check(!x.isAlive(),"inspection ignored interrupt");}
            for(int k=0;k<workers;k++){check(threw[k],"worker exited without CancellationException");cancelled++;worstNs=Math.max(worstNs,doneAt[k]-sent);}
        }
        check(worstNs<250_000_000L,"cancellation latency "+worstNs/1e6+" ms");
        System.out.printf(java.util.Locale.US,"PASS P2 preflight parity; %d cancellations across %d concurrent workers, worst latency %.2f ms%n",
            cancelled,workers,worstNs/1e6);
    }

    // ------------------------------------------------------------------ P3
    /** Verbatim pre-M7 smoothing pass + merge (reference for exact P3 parity). */
    static int[] referenceRefine(int[] source,boolean[] allowed,int w,int h,int colors,float detail,int minimumAreaPixels){
        int[] labels=source.clone();
        final int passes=detail>=.9f?0:detail>=.72f?1:detail>=.35f?2:3;
        final int radius=detail<.18f?2:1;
        for(int pass=0;pass<passes;pass++){
            int[] next=labels.clone();int[] scores=new int[colors];
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                int p=y*w+x,original=labels[p];if(!allowed[p]||original<0)continue;
                java.util.Arrays.fill(scores,0);int total=0;
                for(int dy=-radius;dy<=radius;dy++)for(int dx=-radius;dx<=radius;dx++){
                    int xx=x+dx,yy=y+dy;if(xx<0||xx>=w||yy<0||yy>=h)continue;int q=yy*w+xx;
                    if(!allowed[q]||labels[q]<0||labels[q]>=colors)continue;
                    int weight=(dx==0&&dy==0)?2:1;scores[labels[q]]+=weight;total+=weight;
                }
                int winner=original;for(int c=0;c<colors;c++)if(scores[c]>scores[winner])winner=c;
                if(winner!=original&&scores[winner]*2>total&&scores[winner]>=scores[original]+2)next[p]=winner;
            }
            labels=next;
        }
        return labels; // merge stage is unchanged code; compared through refine() output below
    }

    static void p3() throws Exception {
        Random r=new Random(7);
        int cases=0;long refNs=0,newNs=0;
        float[] details={0f,.1f,.2f,.4f,.6f,.8f,.95f};
        for(int t=0;t<1200;t++){
            int w=1+r.nextInt(t<200?6:90),h=1+r.nextInt(t<200?6:90),colors=2+r.nextInt(7),n=w*h;
            int[] src=new int[n];boolean[] allowed=new boolean[n];
            int mode=t%4; // 0 noise, 1 blobs (stencil-like), 2 stripes, 3 blobs + unlabeled holes
            int cx=r.nextInt(w),cy=r.nextInt(h);
            for(int i=0;i<n;i++){
                int x=i%w,y=i/w;
                src[i]=mode==0?r.nextInt(colors):mode==2?(x/3+y/5)%colors:
                    (int)((Math.hypot(x-cx,y-cy)/4+Math.sin(x*.3)*2+Math.cos(y*.2)*2)%colors+colors)%colors;
                if(r.nextInt(40)==0&&mode!=2)src[i]=r.nextInt(colors);
                if(mode==3&&r.nextInt(25)==0)src[i]=-1;
                allowed[i]=r.nextInt(mode==3?8:30)!=0;
            }
            float detail=details[t%details.length];int minArea=r.nextInt(12);
            long a=System.nanoTime();
            // Reference smoothing then the same (unchanged) merge, by feeding refine() detail=.95 (0 passes).
            int[] smoothed=referenceRefine(src,allowed,w,h,colors,detail,minArea);
            StencilRegionRefiner.Output ref=StencilRegionRefiner.refine(smoothed,allowed,w,h,colors,.95f,minArea);
            long b=System.nanoTime();
            StencilRegionRefiner.Output got=StencilRegionRefiner.refine(src,allowed,w,h,colors,detail,minArea);
            long c=System.nanoTime();
            refNs+=b-a;newNs+=c-b;
            check(java.util.Arrays.equals(ref.owner,got.owner),"P3 refine output differs, case "+t+" "+w+"x"+h+" colors "+colors+" detail "+detail);
            check(ref.smallRegionsMerged==got.smallRegionsMerged,"P3 merge count differs, case "+t);
            cases++;
        }
        // Out-of-range labels must still fail exactly as before.
        try{StencilRegionRefiner.refine(new int[]{0,9,0,0},new boolean[]{true,true,true,true},2,2,2,.1f,0);
            throw new AssertionError("out-of-range label accepted");}
        catch(ArrayIndexOutOfBoundsException expected){}
        System.out.printf(Locale.US,"PASS P3 refine identical on %d randomized/boundary cases (host time ref %.0f ms, new %.0f ms)%n",cases,refNs/1e6,newNs/1e6);
    }

    // ------------------------------------------------------------------ B5
    static void b5(){
        check(ExportFileNames.cutZip("Photo project","Tonal Stencil").equals("VinylStudio-Tonal-Stencil-cut-layers.zip"),"generic project name kept");
        check(ExportFileNames.cutZip("Grandma's 80th!","Mixed Confetti").equals("VinylStudio-Grandmas-80th-Mixed-Confetti-cut-layers.zip"),
            ExportFileNames.cutZip("Grandma's 80th!","Mixed Confetti"));
        check(ExportFileNames.cutZip("Chainmail","Chainmail").equals("VinylStudio-Chainmail-cut-layers.zip"),"style duplicated");
        check(ExportFileNames.cutZip(null,null).equals("VinylStudio-cut-layers.zip"),"null names");
        String hostile=ExportFileNames.cutZip("../../etc/passwd","a/b\\c");
        check(!hostile.contains("/")&&!hostile.contains("\\")&&!hostile.contains("..")&&hostile.startsWith("VinylStudio-"),"path characters leaked: "+hostile);
        String longName=ExportFileNames.cutZip("x".repeat(200),"Prismatic Twill");
        check(longName.length()<120&&longName.endsWith("-Prismatic-Twill-cut-layers.zip"),"long name not bounded");
        for(String n:VinylStyleSuite.NAMES){String f=ExportFileNames.cutZip("New project",n);
            check(f.matches("VinylStudio(-[A-Za-z0-9-]+)?-cut-layers\\.zip"),"unsafe file name "+f);}
        System.out.println("PASS B5 export file names are style-specific and filesystem-safe");
    }

    // ------------------------------------------------------------------ M1 morphology
    static void m1(){
        // Heap-tiered union-tree depth (scheduling only): 192 MB tablet -> 1, phones 512 MB -> 2, large -> 3.
        ParallelGeometry.configureMemoryBudget(false,192L<<20);check(ParallelGeometry.unionDepth()==1,"192 MB union depth");
        ParallelGeometry.configureMemoryBudget(false,512L<<20);check(ParallelGeometry.unionDepth()==2,"512 MB union depth");
        ParallelGeometry.configureMemoryBudget(false,2048L<<20);check(ParallelGeometry.unionDepth()==3,"2 GB union depth");
        ParallelGeometry.configureMemoryBudget(false,192L<<20);
        check(ParallelGeometry.unionDepth(152.4f)==1&&ParallelGeometry.unionDepth(203.2f)==0&&ParallelGeometry.unionDepth(274.3f)==0,"192 MB width rule");
        ParallelGeometry.configureMemoryBudget(false,512L<<20);check(ParallelGeometry.unionDepth(274.3f)==2,"512 MB large artwork");
        ParallelGeometry.configureMemoryBudget(false,2048L<<20);
        Random r=new Random(35);int cases=0;long brute=0,fast=0;
        for(int t=0;t<900;t++){
            int w=1+r.nextInt(t<300?12:140),h=1+r.nextInt(t<300?12:140),rad=r.nextInt(t<300?6:13);
            boolean ortho=(t&1)==1,dil=(t&2)==2;
            boolean[] in=new boolean[w*h];
            int mode=t%3;double density=r.nextDouble();
            int cx=r.nextInt(w),cy=r.nextInt(h);
            for(int i=0;i<in.length;i++){int x=i%w,y=i/w;
                in[i]=mode==0?r.nextDouble()<density:mode==1?Math.hypot(x-cx,y-cy)<(w+h)*.2+3*Math.sin(x*.4):((x/5+y/7)&1)==0^r.nextInt(30)==0;}
            boolean[] a=new boolean[w*h],b=new boolean[w*h];
            long t0=System.nanoTime();BinaryMorphology.bruteForce(in,a,w,h,rad,ortho,dil);
            long t1=System.nanoTime();BinaryMorphology.apply(in,b,w,h,rad,ortho,dil);
            long t2=System.nanoTime();brute+=t1-t0;fast+=t2-t1;
            check(java.util.Arrays.equals(a,b),"morphology differs: case "+t+" "+w+"x"+h+" r="+rad+" ortho="+ortho+" dilation="+dil);
            cases++;
        }
        System.out.printf(Locale.US,"PASS M1 exact morphology == brute-force kernel on %d cases (host %.0f ms vs %.0f ms)%n",cases,fast/1e6,brute/1e6);
    }

    // ------------------------------------------------------------------ M2 conditioner EDT
    static void m2(){
        // Both schedules: sequential (phone low-RAM / forced) and the parallel pool.
        boolean prior=ParallelGeometry.forceSequential;
        try{for(boolean seq:new boolean[]{true,false}){ParallelGeometry.forceSequential=seq;CutGeometryConditioner.parallelPasses=!seq;ParallelGeometry.configureMemoryBudget(false,1L<<30);m2once(seq);}}
        finally{ParallelGeometry.forceSequential=prior;CutGeometryConditioner.parallelPasses=false;}
    }
    static void m2once(boolean sequential){
        Random r=new Random(40);int cases=0;long ref=0,fast=0;
        for(int t=0;t<700;t++){
            int w=1+r.nextInt(t<200?10:t<650?400:1400),h=1+r.nextInt(t<200?10:t<650?400:1400);
            boolean[] seed=new boolean[w*h];double p=t%5==0?0:t%5==1?1:r.nextDouble()*(t%2==0?.02:.6);
            for(int i=0;i<seed.length;i++)seed[i]=r.nextDouble()<p;
            long t0=System.nanoTime();float[] a=CutGeometryConditioner.edtReference(seed,w,h);
            long t1=System.nanoTime();float[] b=CutGeometryConditioner.edt(seed,w,h);
            long t2=System.nanoTime();ref+=t1-t0;fast+=t2-t1;
            for(int i=0;i<a.length;i++)if(Float.floatToRawIntBits(a[i])!=Float.floatToRawIntBits(b[i]))
                throw new AssertionError("EDT differs at "+i+" case "+t+" "+w+"x"+h+": "+b[i]+" vs "+a[i]);
            cases++;
        }
        System.out.printf(Locale.US,"PASS M2 conditioner EDT bit-identical to the FH reference on %d cases, %s (host %.0f ms vs %.0f ms)%n",cases,sequential?"sequential":"parallel",fast/1e6,ref/1e6);
    }

    // ------------------------------------------------------------------ M3 thresholded EDT
    static void m3(){
        boolean prior=CutGeometryConditioner.parallelPasses;
        try{
            for(boolean par:new boolean[]{false,true}){
                CutGeometryConditioner.parallelPasses=par;ParallelGeometry.configureMemoryBudget(false,1L<<30);
                Random r=new Random(41);int cases=0;long ref=0,fast=0;
                for(int t=0;t<600;t++){
                    int w=1+r.nextInt(t<200?12:t<560?300:1200),h=1+r.nextInt(t<200?12:t<560?300:1200);
                    boolean[] seed=new boolean[w*h];double p=t%7==0?0:t%7==1?1:r.nextDouble()*(t%2==0?.01:.5);
                    for(int i=0;i<seed.length;i++)seed[i]=r.nextDouble()<p;
                    int rad=r.nextInt(16);float lim=t%3==0?rad*(float)rad+rad+.01f:t%3==1?rad*(float)rad:r.nextFloat()*300f;
                    long t0=System.nanoTime();float[] d=CutGeometryConditioner.edtReference(seed,w,h);
                    long t1=System.nanoTime();boolean[] got=CutGeometryConditioner.within(seed,w,h,lim);
                    long t2=System.nanoTime();ref+=t1-t0;fast+=t2-t1;
                    for(int i=0;i<d.length;i++)if((d[i]<=lim)!=got[i])
                        throw new AssertionError("within differs at "+i+" case "+t+" "+w+"x"+h+" lim "+lim+" d="+d[i]);
                    cases++;
                }
                System.out.printf(Locale.US,"PASS M3 thresholded transform == (FH reference <= lim) on %d cases, %s (host %.0f ms vs %.0f ms)%n",cases,par?"parallel":"sequential",fast/1e6,ref/1e6);
            }
        }finally{CutGeometryConditioner.parallelPasses=prior;}
    }
}

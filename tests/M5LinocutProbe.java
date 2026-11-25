package com.halftonestudio;

import java.io.File;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Developer probe: one photo/size through the Linocut engine with the suite's option mapping and each
 * M5 switch selectable (-Dcarve, -Dbal, -Dfc), prints timing, findings and ink coverage inside an
 * optional rect (-Drect=x0,y0,x1,y1 fractions); writes the composite to -Dout when given.
 * Usage: java ... M5LinocutProbe photo inches
 */
public final class M5LinocutProbe {
    public static void main(String[] a)throws Exception{
        M4ArtBench.Photo p=M4ArtBench.load(new File(a[0]),850);
        float in=Float.parseFloat(a[1]);
        VinylStyleSuite.Config c=M4ArtBench.suiteConfig(VinylStyleSuite.LINOCUT,p,in*25.4f,.8f,.5f);
        LinocutEngine.Options o=M5LinocutReliefTest.options(c,true);
        o.shadowCarve=Boolean.parseBoolean(System.getProperty("carve","true"));
        o.balancedSpacing=Boolean.parseBoolean(System.getProperty("bal","true"));
        o.featureContrast=Float.parseFloat(System.getProperty("fc","0.8"));
        o.carveTone=Float.parseFloat(System.getProperty("tone","0.52"));
        long t=System.nanoTime();
        StyleKit.Result r=LinocutEngine.generate(p.argb,p.w,p.h,o);
        long ms=(System.nanoTime()-t)/1000000;
        int loops=0;for(StyleKit.Layer l:r.layers)loops+=l.loops.size();
        float gap=StyleCutGeometryPreflight.exportGapMm(c.gapMm,.8f);
        java.util.List<StyleCutGeometryPreflight.Warning> ws=StyleCutGeometryPreflight.inspect(r,.8f,gap,100000);int find=ws.size();
        if(Boolean.getBoolean("why")){
            java.util.List<float[]> L=r.layers.get(0).loops;
            java.util.regex.Matcher mm=java.util.regex.Pattern.compile("Linocut: (\\d+) strokes.*, (\\d+) white gouges").matcher(r.summary);
            int nb=L.size(),ng=0;if(mm.find()){nb=Integer.parseInt(mm.group(1));ng=Integer.parseInt(mm.group(2));}
            java.util.Map<String,Integer> kinds=new java.util.TreeMap<>();
            for(StyleCutGeometryPreflight.Warning wv:ws){
                int[] best={-1,-1};double[] bd={1e9,1e9};
                for(int i=0;i<L.size();i++){float[] q=L.get(i);double dmin=1e9;for(int k=0;k<q.length;k+=2)dmin=Math.min(dmin,Math.hypot(q[k]-wv.xMm,q[k+1]-wv.yMm));
                    if(dmin<bd[0]){bd[1]=bd[0];best[1]=best[0];bd[0]=dmin;best[0]=i;}else if(dmin<bd[1]){bd[1]=dmin;best[1]=i;}}
                String a1=best[0]<nb?"B":best[0]>=L.size()-ng?"G":"S",a2=best[1]<0?"-":best[1]<nb?"B":best[1]>=L.size()-ng?"G":"S";
                String key=(best[0]==best[1]?"same ":"")+(a1.compareTo(a2)<0?a1+a2:a2+a1);
                kinds.merge(key,1,Integer::sum);if(Boolean.getBoolean("whyxy"))System.out.printf(Locale.US,"  %-6s %.2f,%.2f d=%.2f/%.2f %s%n",key,wv.xMm,wv.yMm,bd[0],bd[1],wv.reason);
            }
            System.out.println("finding kinds (nearest two loops; B stroke, S block, G gouge): "+kinds);
        }
        double cov=Double.NaN;
        String rect=System.getProperty("rect");
        if(rect!=null){
            String[] q=rect.split(",");int[] d=new int[2];
            boolean[] ink=M5LinocutReliefTest.raster(r.layers.get(0).loops,r.widthMm,r.heightMm,4f,d);
            int x0=(int)(d[0]*Float.parseFloat(q[0])),y0=(int)(d[1]*Float.parseFloat(q[1])),x1=(int)(d[0]*Float.parseFloat(q[2])),y1=(int)(d[1]*Float.parseFloat(q[3]));
            int n=0,k=0;for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){n++;if(ink[y*d[0]+x])k++;}cov=k/(double)n;
        }
        {   // coverage per source-luminance band (0 = darkest)
            int[] d=new int[2];boolean[] ink=M5LinocutReliefTest.raster(r.layers.get(0).loops,r.widthMm,r.heightMm,4f,d);
            double[] cv=new double[5];int[] n=new int[5];
            for(int y=0;y<d[1];y++)for(int x=0;x<d[0];x++){
                int c0=p.argb[Math.min(p.h-1,y*p.h/d[1])*p.w+Math.min(p.w-1,x*p.w/d[0])];
                float L=(0.2126f*((c0>>16)&255)+0.7152f*((c0>>8)&255)+0.0722f*(c0&255))/255f;
                int b=Math.min(4,(int)(L*5));n[b]++;if(ink[y*d[0]+x])cv[b]++;}
            StringBuilder sb=new StringBuilder("bands");for(int b=0;b<5;b++)sb.append(String.format(Locale.US," %.2f",cv[b]/Math.max(1,n[b])));
            System.out.println(sb);
        }
        System.out.printf(Locale.US,"%s %.0fin detail=%d carve=%b bal=%b fc=%.2f ms=%d loops=%d findings=%d rectCov=%.3f | %s%n",
            p.name,in,c.detail,o.shadowCarve,o.balancedSpacing,o.featureContrast,ms,loops,find,cov,r.summary);
        String out=System.getProperty("out");
        if(out!=null){
            M4ArtBench.Render render=new M4ArtBench.Render();render.widthMm=r.widthMm;render.heightMm=r.heightMm;render.paper=r.paperArgb;
            for(StyleKit.Layer l:r.layers){M4ArtBench.Sheet s=new M4ArtBench.Sheet();s.color=l.color;s.loops.addAll(l.loops);render.sheets.add(s);}
            ImageIO.write(M4ArtBench.fit(M4ArtBench.composite(render,Math.min(6f,1400f/r.widthMm)),900),"png",new File(out));
        }
    }
}

package com.halftonestudio;

import java.io.File;
import java.util.Locale;

/**
 * M4 fidelity: Linocut tone-to-line mapping at the app's default 0.8 mm cut minimum.
 * Build 558 drew near-uniform coverage (tone correlation 0.01-0.12 on real photos)
 * because stroke width could only vary between about 0.88 and 1.2 mm. With spacing
 * driven by tone, coverage must track the photograph. Measured through M4ArtBench's
 * opaque composite (viewing-scale OKLab lightness correlation), real cut-ready geometry.
 */
public final class M4LinocutToneTest {
    public static void main(String[] args)throws Exception{
        int failures=0;
        for(String name:new String[]{"astronaut.png","camera.png","chelsea.png"}){
            M4ArtBench.Photo p=M4ArtBench.load(new File("tests/m4/photos",name),850);
            double[] corr=new double[2];
            for(int mode=0;mode<2;mode++){
                VinylStyleSuite.Config c=M4ArtBench.suiteConfig(VinylStyleSuite.LINOCUT,p,152.4f,.8f,.5f);
                StyleKit.Result r=mode==0?legacy(p,c):VinylStyleSuite.generate(p.argb,p.w,p.h,c);
                M4ArtBench.Render render=new M4ArtBench.Render();render.widthMm=r.widthMm;render.heightMm=r.heightMm;render.paper=r.paperArgb;
                for(StyleKit.Layer l:r.layers){M4ArtBench.Sheet s=new M4ArtBench.Sheet();s.color=l.color;s.loops.addAll(l.loops);render.sheets.add(s);}
                String m=M4ArtBench.metrics(render,p,M4ArtBench.composite(render,4f));
                corr[mode]=Double.parseDouble(m.split("\t")[0]);
            }
            System.out.printf(Locale.US,"%-14s tone correlation 558 spacing %.3f -> M4 %.3f%n",name,corr[0],corr[1]);
            if(!(corr[1]>=corr[0]+.15)){failures++;System.out.println("  FAIL Linocut must track the photograph's tone");}
            if(!(corr[1]>=.30)){failures++;System.out.println("  FAIL Linocut likeness below 0.30");}
        }
        // Review finding: light tones must keep flowing strokes, not self-blocked dashes.
        for(int grey:new int[]{0xd8,0xc0}){
            int w=200,h=200;int[] px=new int[w*h];
            for(int i=0;i<px.length;i++){int x=i%w,y=i/w;int v=Math.max(0,Math.min(255,grey+(int)(6*Math.sin(x*.05)+6*Math.cos(y*.07))));px[i]=0xff000000|(v<<16)|(v<<8)|v;}
            double[] med=new double[2];
            for(int m=0;m<2;m++){
                LinocutEngine.Options o=new LinocutEngine.Options();o.widthMm=152.4f;o.minWidthMm=.88f;o.gapMm=.5f;o.pitchMm=2.06f;
                o.maxWidthFraction=.64f;o.cutoff=.11f;o.minLengthMm=2.1f;o.autoLevels=false;o.toneSpacing=m==1;
                java.util.ArrayList<Double> lengths=new java.util.ArrayList<>();
                for(float[] l:LinocutEngine.generate(px,w,h,o).layers.get(0).loops){double p=0;
                    for(int i=2;i<l.length;i+=2)p+=Math.hypot(l[i]-l[i-2],l[i+1]-l[i-1]);lengths.add(p/2);}
                java.util.Collections.sort(lengths);med[m]=lengths.isEmpty()?0:lengths.get(lengths.size()/2);
            }
            System.out.printf(Locale.US,"light grey %02x median stroke 558 %.1f mm -> M4 %.1f mm%n",grey,med[0],med[1]);
            if(med[1]<med[0]){failures++;System.out.println("  FAIL light-tone strokes shorter than 558 (self-blocking)");}
        }
        if(failures>0)throw new AssertionError(failures+" Linocut tone failures");
        System.out.println("PASS M4 Linocut tone-to-line mapping");
    }
    /** The same suite configuration with the 558 spacing rule. */
    static StyleKit.Result legacy(M4ArtBench.Photo p,VinylStyleSuite.Config c){
        LinocutEngine.Options o=new LinocutEngine.Options();o.widthMm=c.widthMm;
        float min=Math.max(VinylEngine.MINIMUM_CUT_WIDTH_MM,c.minimumMm),f=Math.max(0,Math.min(100,c.detail))/100f;
        o.gapMm=StyleCutGeometryPreflight.exportGapMm(c.gapMm,min);o.minWidthMm=min+.08f;
        o.pitchMm=Math.max(Math.max(Math.max(min*2.2f,1.0f),o.minWidthMm+o.gapMm*1.6f),3.35f-2.35f*f);
        o.maxWidthFraction=StyleKit.clampF(.47f+.24f*c.weightMm,.28f,.86f);
        o.flowSmoothMm=Math.max(2.1f,4.2f-1.6f*f);o.cutoff=.11f;o.minLengthMm=2.1f;
        o.inkArgb=c.palette!=null?c.palette[0]:0xff15151a;o.crossArgb=o.inkArgb;o.paperArgb=c.paper;
        o.toneSpacing=false;
        return LinocutEngine.generate(p.argb,p.w,p.h,o);
    }
}

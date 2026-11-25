package com.halftonestudio;

import java.util.Arrays;

/** Warm portrait regression: prevents vanishing cyan and disappearing facial midtones. */
public final class PortraitCMYKRegressionTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static int[] photo(int w,int h){
        int[] pix=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float nx=(x+.5f-w*.5f)/w, ny=(y+.5f-h*.5f)/h;
            float face=(float)Math.exp(-(nx*nx/.045f+ny*ny/.10f));
            float eyeL=(float)Math.exp(-((nx+.09f)*(nx+.09f)/.0012f+(ny+.045f)*(ny+.045f)/.00055f));
            float eyeR=(float)Math.exp(-((nx-.09f)*(nx-.09f)/.0012f+(ny+.045f)*(ny+.045f)/.00055f));
            float mouth=(float)Math.exp(-(nx*nx/.009f+(ny-.12f)*(ny-.12f)/.0007f));
            float hair=ny<-.20f?.9f:Math.abs(nx)>.23f?.72f:.12f;
            float darkness=Math.max(.03f,.60f*hair+.9f*Math.max(eyeL,eyeR)+.55f*mouth);
            int r=channel(235-150*darkness+20*face),g=channel(177-124*darkness+20*face),b=channel(135-100*darkness+18*face);
            pix[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }return pix;
    }
    static int channel(float v){return Math.max(0,Math.min(255,Math.round(v)));}
    static int layer(VinylEngine.Result result,int channel){
        for(int i=0;i<result.settings.layers.size();i++)if(result.settings.layers.get(i).cmykChannel==channel)return i;
        throw new AssertionError("missing channel "+channel);
    }
    static VinylEngine.Settings setup(){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();s.widthMm=115f;s.marginMm=2f;
        s.masterSpacingMm=3.6f;s.minimumMm=.65f;s.bridgeMm=.30f;s.pattern=VinylEngine.LINE;
        s.lineSampleMm=.38f;s.detailBoost=.5f;s.lineGamma=.86f;
        return s;
    }
    public static void main(String[] args){
        int w=116,h=150;int[] pix=photo(w,h);
        VinylEngine.Settings s=setup();
        s.shadowColorRecovery=0f;s.portraitMidtones=0f;
        VinylEngine.Result legacy=VinylEngine.generate(pix,w,h,s);
        int oldCyan=legacy.shapes.get(layer(legacy,0)).size(),oldBlack=legacy.shapes.get(layer(legacy,3)).size();
        s.shadowColorRecovery=.65f;s.portraitMidtones=.7f;
        VinylEngine.Result balanced=VinylEngine.generate(pix,w,h,s);
        int cyan=balanced.shapes.get(layer(balanced,0)).size();
        int black=balanced.shapes.get(layer(balanced,3)).size();
        System.out.println("Warm synthetic portrait cuts: legacy C="+oldCyan+", K="+oldBlack+"; balanced C="+cyan+", K="+black);
        VinylDiagnostics.Report diag=VinylDiagnostics.analyze(balanced,110);
        System.out.println("Synthetic cyan layer coverage="+diag.layers[layer(balanced,0)].coveredPct+"%, visible="+diag.layers[layer(balanced,0)].visiblePct+"%");
        check(cyan>0,"cyan must have real SVG geometry for warm dark hair and shadows");
        check(cyan>oldCyan,"shadow color recovery must add cyan geometry");
        check(black>=oldBlack,"midtone recovery should not erase black facial outlines");
        check(VinylEngine.validateExport(balanced)==null,"balanced portrait must remain export-safe");
        check(balanced.settings.layers.get(1).cmykChannel==0,"physical stack must keep cyan identity");
        // Input array must not be modified by generation.
        check(Arrays.equals(pix,photo(w,h)),"generation mutated source photo");
        // Recovering shadow CMY must not create cyan cuts on paper-white alone.
        int[] white=new int[64*64];Arrays.fill(white,0xffffffff);
        VinylEngine.Result paper=VinylEngine.generate(white,64,64,s);
        check(paper.shapes.get(layer(paper,0)).isEmpty(),"paper-white must contain no cyan");
        // All geometry must be filled closed shapes, independent of registration paths.
        String svg=VinylEngine.svg(balanced,layer(balanced,0));
        check(svg.contains("<path")&&!svg.contains("clipPath")&&!svg.contains("<line "),"physical cyan cut paths required");
        // Enabling fine-detail ribbons must not choke main lines at a moderate physical minimum.
        s.detailBoost=0f;VinylEngine.Result noDetail=VinylEngine.generate(pix,w,h,s);
        check(black>0&&noDetail.shapes.get(layer(noDetail,3)).size()>0,"detailBoost must not eliminate black ribbon family");
        System.out.println("PASS: warm portraits have cyan cuts; midtone detail survives; original unchanged; white paper empty; SVG safety");
    }
}

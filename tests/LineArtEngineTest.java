package com.halftonestudio;

import java.util.*;

/** Regression tests for independent CMYK line families and physical vinyl SVG output. */
public final class LineArtEngineTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static int[] portrait(int w,int h){
        int[] src=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float xx=(x-w*.5f)/w,yy=(y-h*.5f)/h;
            float face=(float)Math.exp(-((xx*xx)/.064f+(yy*yy)/.21f));
            float eyeL=(float)Math.exp(-((xx+.13f)*(xx+.13f)/.0013f+(yy+.08f)*(yy+.08f)/.001f));
            float eyeR=(float)Math.exp(-((xx-.13f)*(xx-.13f)/.0013f+(yy+.08f)*(yy+.08f)/.001f));
            float mouth=(float)Math.exp(-(xx*xx/.012f+(yy-.18f)*(yy-.18f)/.0007f));
            int red=clamp(Math.round(205-80*face-150*(eyeL+eyeR)-110*mouth+40*xx));
            int green=clamp(Math.round(200-95*face-155*(eyeL+eyeR)-110*mouth+40*yy));
            int blue=clamp(Math.round(200-65*face-140*(eyeL+eyeR)-90*mouth-60*xx));
            src[y*w+x]=0xff000000|(red<<16)|(green<<8)|blue;
        }return src;
    }
    static int clamp(int x){return Math.max(0,Math.min(255,x));}
    static VinylEngine.Settings settings(){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();s.widthMm=75f;s.marginMm=1.7f;
        s.registration=true;s.linePresenceRecovery=0f;s.portraitBackgroundGuard=0f;s.minimumMm=.35f;s.bridgeMm=.30f;s.masterSpacingMm=3.25f;
        s.linkedControls=true;s.lineSampleMm=.42f;s.contourStrength=.70f;
        return s;
    }
    static float area(VinylEngine.Result r,int li){
        float a=0;for(VinylEngine.Shape p:r.shapes.get(li)){
            if(p.circle){a+=Math.PI*p.r*p.r;continue;}
            float[] xy=p.xy;double signed=0;
            for(int k=0;k<xy.length;k+=2){int q=(k+2)%xy.length;
                signed+=(double)xy[k]*xy[q+1]-(double)xy[q]*xy[k+1];}
            a+=Math.abs(signed)*.5f;
        }return a;
    }
    public static void main(String[] args){
        int w=70,h=84;int[] src=portrait(w,h);
        int[] types={VinylEngine.STRAIGHT,VinylEngine.LINE,VinylEngine.WAVE,VinylEngine.CONTOUR,
            VinylEngine.CROSSHATCH,VinylEngine.RINGS,VinylEngine.RAYS,VinylEngine.ZIGZAG,
            VinylEngine.BROKEN,VinylEngine.BEADED,VinylEngine.FADE,VinylEngine.HATCH};
        for(int style:types){
            VinylEngine.Settings s=settings();s.pattern=style;
            if(style==VinylEngine.CONTOUR)s.detailBoost=.65f;
            VinylEngine.Result r=VinylEngine.generate(src,w,h,s);
            check(r.count()>0,"no generated cut shapes for style="+style);
            for(int i=0;i<4;i++){
                check(VinylEngine.svgElementsForLayer(r,i)<=5000,"exceeds Cricut SVG limit "+style);
                check(VinylEngine.svg(r,i).contains("<svg"),"SVG header missing");
                check(!VinylEngine.svg(r,i).contains("clipPath"),"SVG clipping forbidden");
                check(!VinylEngine.svg(r,i).contains("<line "),"SVG strokes forbidden");
                for(VinylEngine.Shape cut:r.shapes.get(i)){
                    float[] bounds=cut.bounds();for(float value:bounds)check(Float.isFinite(value),"nonfinite geometry");
                    if(cut.circle){check(cut.r*2+.001f>=s.minimumMm,"bead smaller than min cut width");continue;}
                    int n=cut.xy.length/4;
                    for(int j=0;j<n;j++){
                        float dx=cut.xy[j*2]-cut.xy[2*n+2*(n-1-j)];
                        float dy=cut.xy[j*2+1]-cut.xy[2*n+2*(n-1-j)+1];
                        float width=(float)Math.hypot(dx,dy);
                        check(width+.002f>=s.minimumMm,"line sample below minimum cut width: "+width+" in style "+style);
                    }
                }
            }
            check(VinylEngine.validateExport(r)==null,"export invalid for style="+style+" / "+VinylEngine.validateExport(r));
            if(style==VinylEngine.BEADED)for(VinylEngine.Shape sh:r.shapes.get(0))check(sh.circle,"beaded screen must use circular vinyl cuts");
        }
        // Channel-specific line styles and fine angle offsets survive physical reordering.
        VinylEngine.Settings mixed=settings();mixed.pattern=VinylEngine.STRAIGHT;
        for(VinylEngine.Layer l:mixed.layers){
            if(l.cmykChannel==0){l.lineStyleOverride=VinylEngine.WAVE;l.lineGammaOverride=1.8f;l.angleFineTuneDeg=3f;}
            if(l.cmykChannel==1){l.lineStyleOverride=VinylEngine.CONTOUR;l.lineWidthScale=.75f;l.angleFineTuneDeg=-2f;}
            if(l.cmykChannel==3){l.lineStyleOverride=VinylEngine.STRAIGHT;}
        }
        VinylEngine.Result mixedResult=VinylEngine.generate(src,w,h,mixed);
        HashMap<Integer,String> original=new HashMap<>();
        for(int i=0;i<4;i++)original.put(mixedResult.settings.layers.get(i).cmykChannel,VinylEngine.svg(mixedResult,i));
        Collections.reverse(mixed.layers);
        VinylEngine.Result reversed=VinylEngine.generate(src,w,h,mixed);
        for(int i=0;i<4;i++)check(original.get(reversed.settings.layers.get(i).cmykChannel).equals(VinylEngine.svg(reversed,i)),
            "reordered CMYK channels changed cut shapes");
        // Individual gamma/width values have geometry effects, not cosmetic effects.
        VinylEngine.Settings widths=settings();widths.pattern=VinylEngine.STRAIGHT;
        VinylEngine.Result base=VinylEngine.generate(src,w,h,widths);
        widths.layers.get(1).lineWidthScale=.52f;
        VinylEngine.Result narrow=VinylEngine.generate(src,w,h,widths);
        check(area(base,1)>area(narrow,1),"per-channel width had no effect");
        check(VinylEngine.svg(base,0).equals(VinylEngine.svg(narrow,0)),"one-channel width affected another color");
        widths=settings();widths.pattern=VinylEngine.STRAIGHT;
        widths.layers.get(1).lineGammaOverride=2.5f;
        VinylEngine.Result changed=VinylEngine.generate(src,w,h,widths);
        check(Math.abs(area(base,1)-area(changed,1))>0.2f,"per-channel gamma did not affect SVG shapes");
        VinylEngine.Settings detail=settings();detail.pattern=VinylEngine.STRAIGHT;detail.detailBoost=0f;
        VinylEngine.Result noDetail=VinylEngine.generate(src,w,h,detail);
        detail.detailBoost=.85f;
        VinylEngine.Result withDetail=VinylEngine.generate(src,w,h,detail);
        check(withDetail.shapes.get(3).size()>noDetail.shapes.get(3).size(),"adaptive detail failed to add localized ribbon segments");
        check(!VinylEngine.svg(noDetail,3).equals(VinylEngine.svg(withDetail,3)),"detail adaptation had no geometric effect");
        check(VinylEngine.validateExport(withDetail)==null,"adaptive screen not SVG safe");
        // Focal placement affects ring geometry.
        VinylEngine.Settings ring=settings();ring.pattern=VinylEngine.RINGS;
        VinylEngine.Result centered=VinylEngine.generate(src,w,h,ring);
        ring.radialFocusX=.25f;ring.radialFocusY=.7f;
        VinylEngine.Result offset=VinylEngine.generate(src,w,h,ring);
        check(!VinylEngine.svg(centered,3).equals(VinylEngine.svg(offset,3)),"radial focal point is inert");
        check(Math.abs(VinylEngine.MM_PER_INCH/3.25f-7.815f)<.01,"incorrect LPI units");
        System.out.println("PASS: 12 line families; closed SVGs; per-channel styles/gamma/width; CMYK reorder; adaptive detail; radial focus; cut limits");
    }
}

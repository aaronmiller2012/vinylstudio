package com.halftonestudio;
import java.util.Arrays;

/** Regression gate for fresh CMYK presets, existing projects and CUTTABLE micro gaps. */
public final class LineV2504CMYKContinuityTest {
    static final int[] STYLES={5,8,9,12,13,14,15,20,16,17,18,19};
    static void check(boolean v,String msg){if(!v)throw new AssertionError(msg);}
    static int[] fill(int w,int h,int c){int[] a=new int[w*h];Arrays.fill(a,c);return a;}
    static VinylEngine.Settings state(int style){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();s.widthMm=70;s.marginMm=2;
        ClassicCmykLines.useModern(s);s.registration=false;s.linePresenceRecovery=0;s.portraitBackgroundGuard=0;s.pattern=style;s.minimumMm=.8f;s.bridgeMm=.45f;
        return s;
    }
    static int[] tonalFixture(int w,int h){
        int[] src=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double q=.5+.5*Math.sin(x*.51+y*.06+.4);
            int r=(int)(80+160*q),g=(int)(170+80*q),b=(int)(130+120*q);
            src[y*w+x]=0xff000000|r<<16|g<<8|b;
        }
        return src;
    }
    public static void main(String[] args){
        VinylEngine.Settings d=VinylEngine.Settings.defaults();
        check(d.classicCmykLines,"Default CMYK line engine must remain the classic cut geometry");
        ClassicCmykLines.useModern(d);
        CmykPortraitDefaults.applyPreset(d);
        d.lineContinuity=CmykPortraitDefaults.LINE_CONTINUITY;
        check(d.blackColorProtection==CmykPortraitDefaults.BLACK_COLOR_PROTECTION,"black default mismatch");
        check(d.shadowColorRecovery==CmykPortraitDefaults.SHADOW_COLOR_RECOVERY,"shadow default mismatch");
        check(d.portraitMidtones==CmykPortraitDefaults.PORTRAIT_MIDTONES,"midtone default mismatch");
        check(d.lineContinuity==CmykPortraitDefaults.LINE_CONTINUITY,"continuity default mismatch");
        for(VinylEngine.Layer layer:d.layers)check(layer.strength==1f,"hidden CMYK boost present");
        VinylEngine.Settings reused=d.copy();
        reused.shadowColorRecovery=.8f;reused.blackColorProtection=.6f;reused.portraitMidtones=.9f;
        reused.layers.get(1).strength=1.5f;reused.lineContinuity=.95f;
        SmartHalftonePresets.apply(reused,VinylEngine.WAVE,VinylSizing.BALANCED,6,6);
        VinylEngine.Settings styleBaseline=VinylEngine.Settings.defaults();
        SmartHalftonePresets.apply(styleBaseline,VinylEngine.WAVE,VinylSizing.BALANCED,6,6);
        check(reused.shadowColorRecovery==styleBaseline.shadowColorRecovery && reused.portraitMidtones==styleBaseline.portraitMidtones,"style reset leaked hidden separation");
        check(reused.layers.get(1).strength==styleBaseline.layers.get(1).strength,"style reset failed to remove hidden cyan boost");
        check(reused.lineContinuity==styleBaseline.lineContinuity,"style reset did not normalize micro-gap tuning");
        check(reused.shapeDefinition<=.35f,"Wavy preset is too aggressive");
        check(d.copy().lineContinuity==d.lineContinuity,"copy lost micro-gap configuration");

        int w=100,h=100;int[] colored=tonalFixture(w,h);
        int[] offwhite=fill(w,h,d.paper),alpha=fill(w,h,0),white=fill(w,h,0xffffffff);
        int affected=0,unchanged=0,checked=0;
        for(int style:STYLES){
            VinylEngine.Settings s=state(style);
            s.lineContinuity=0;
            VinylEngine.Result before=VinylEngine.generate(colored,w,h,s);
            s.lineContinuity=CmykPortraitDefaults.LINE_CONTINUITY;
            VinylEngine.Result after=VinylEngine.generate(colored,w,h,s);
            for(int li=0;li<4;li++){
                String a=VinylEngine.svg(before,li),b=VinylEngine.svg(after,li);
                boolean changed=!a.equals(b);
                if(changed)affected++;else unchanged++;
                check(!changed||LineArtEngine.supportsMicroContinuity(style),"Intentional cuts changed in style "+style);
                check(!b.contains("<clipPath")&&!b.contains("<line "),"Noncuttable SVG geometry");
                check(VinylEngine.svgElementsForLayer(after,li)<=5000,"Cricut element count exceeded");
                checked++;
            }
            for(int[] blank:new int[][]{offwhite,alpha,white})
                check(VinylEngine.generate(blank,w,h,s).count()==0,"Paper/alpha gained cuts style "+style);
            if(after.count()>0)check(VinylEngine.validateExport(after)==null,"Export failed style "+style);
            if(LineArtEngine.supportsMicroContinuity(style)){
                LineCutDiagnostics.Report geo=LineCutDiagnostics.inspect(after);
                check(geo.complete && geo.selfIntersections==0 && geo.subMinimumWidthSamples==0,
                    "Unsafe recovered cut geometry style "+style);
            }
        }
        check(affected>0,"Micro gap fix is inert on tonal transitions");
        // A full transparent white stripe must NEVER be filled even when recovery=100%.
        int[] withGap=tonalFixture(w,h);
        for(int y=0;y<h;y++)for(int x=46;x<=52;x++)withGap[y*w+x]=0;
        VinylEngine.Settings gapSettings=state(VinylEngine.STRAIGHT);
        gapSettings.lineContinuity=1f;
        VinylEngine.Result gap=VinylEngine.generate(withGap,w,h,gapSettings);
        for(int li=0;li<4;li++){
            String svg=VinylEngine.svg(gap,li);
            check(svg.contains("<svg"),"Malformed gap SVG");
            for(VinylEngine.Shape sh:gap.shapes.get(li)){
                float[] bounds=sh.bounds();
                check(!(bounds[0]<30f && bounds[2]>41f),
                    "A single ribbon crossed a transparent full-height stripe on layer "+li);
            }
        }
        // RC101: A Snip must NOT disable the modern micro-gap correction.
        // Both strengths are tested against exactly the same excluded hole;
        // recovered shapes must remain complete legal cut polygons.
        int snipChanged=0;
        for(int style:new int[]{5,8,12,13,16}){
            VinylEngine.Settings masked=state(style);
            masked.snips.add(new SnipMask.Operation(SnipMask.REMOVE_INSIDE,
                .38f,.21f,.62f,.21f,.62f,.79f,.38f,.79f));
            masked.lineContinuity=0f;
            VinylEngine.Result unchangedMask=VinylEngine.generate(colored,w,h,masked);
            masked.lineContinuity=1f;
            VinylEngine.Result fullMask=VinylEngine.generate(colored,w,h,masked);
            for(int li=0;li<4;li++){
                if(!VinylEngine.svg(unchangedMask,li).equals(VinylEngine.svg(fullMask,li)))snipChanged++;
                for(VinylEngine.Shape sh:fullMask.shapes.get(li))if(!sh.circle)
                    check(masked.snips.allowsPolygon(sh.xy,masked.widthMm,fullMask.heightMm),
                        "Snip recovery escaped the allowed artwork style="+style+" layer="+li);
            }
            LineCutDiagnostics.Report geo=LineCutDiagnostics.inspect(fullMask);
            check(geo.complete && geo.selfIntersections==0 && geo.subMinimumWidthSamples==0,
                "Snip recovery created an invalid ribbon style="+style);
        }
        check(snipChanged>0,"Snip-aware micro-continuity never changes any cut");
        // Geometric evidence cannot be fabricated from all blank input, even
        // with a maximum continuity slider and high portrait enhancement.
        for(int style:new int[]{5,8,12,13,16}){
            VinylEngine.Settings s=state(style);
            s.lineContinuity=1f;s.portraitMidtones=1f;s.shapeDefinition=1f;
            check(VinylEngine.generate(alpha,w,h,s).count()==0,"Alpha leak at strength 100 style "+style);
            check(VinylEngine.generate(offwhite,w,h,s).count()==0,"Paper leak at strength 100 style "+style);
        }
        System.out.println("PASS: "+checked+" CMYK style/channels; "+affected+" controlled continuity changes, "+unchanged+" unchanged; 36 blank checks plus high-strength alpha/paper; default/reset/copy checks");
    }
}

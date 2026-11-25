package com.halftonestudio;
import java.util.Arrays;

/** Actual generator regression: intentionally under-width sustained portrait tones. */
public final class LineV2521HighlightRetentionTest {
    static void ok(boolean b,String message){ if(!b)throw new AssertionError(message); }
    static int[] pixels(int w,int h){
        int[] rgb=new int[w*h];Arrays.fill(rgb,0xfff5f3ed);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float dx=(x-w*.50f)/(w*.34f),dy=(y-h*.47f)/(h*.44f);
            if(dx*dx+dy*dy<=1){
                rgb[y*w+x]=0xffdec0c1; // pale warm facial tones, below physical width
                if(y<h*.22f)rgb[y*w+x]=0xff3f3029; // dark hair
                if(y>h*.65f)rgb[y*w+x]=0xff4c342c; // beard
            }
        }
        return rgb;
    }
    static VinylEngine.Settings state(int type){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();s.classicCmykLines=false;s.widthMm=100;s.marginMm=1.4f;
        s.registration=false;s.minimumMm=.72f;s.bridgeMm=.35f;s.pattern=type;
        s.linePresenceRecovery=.20f;s.portraitMidtones=.65f;
        s.portraitBackgroundGuard=.5f;s.masterSpacingMm=2.35f;
        for(VinylEngine.Layer l:s.layers){l.spacing=2.35f;l.lineGammaOverride=1.6f;}
        return s;
    }
    public static void main(String[] args){
        int w=135,h=165;int[] img=pixels(w,h);
        long promotedBefore=0,promotedAfter=0;
        for(int style:new int[]{VinylEngine.LINE,VinylEngine.CONTOUR,VinylEngine.STRAIGHT,VinylEngine.WAVE,VinylEngine.ZIGZAG}){
            VinylEngine.Settings s=state(style);
            VinylEngine.Result before=VinylEngine.generate(img,w,h,s);
            s.lineHighlightRetention=1f;
            VinylEngine.Result after=VinylEngine.generate(img,w,h,s);
            for(int i=0;i<4;i++){
                promotedBefore+=before.lineMetrics.get(i).promoted;
                promotedAfter+=after.lineMetrics.get(i).promoted;
                ok(VinylEngine.svgElementsForLayer(after,i)<=5000,"SVG path limit "+style);
                ok(VinylEngine.svg(after,i).contains("<svg"),"SVG missing "+style);
            }
            LineCutDiagnostics.Report d=LineCutDiagnostics.inspect(after);
            ok(d.complete && d.selfIntersections==0 && d.subMinimumWidthSamples==0,
              "unsafe geometry "+style+" self="+d.selfIntersections+" narrow="+d.subMinimumWidthSamples);
            for(int blank:new int[]{0xffffffff,0x00ffffff,s.paper}){
                int[] zeros=new int[w*h];Arrays.fill(zeros,blank);
                ok(VinylEngine.generate(zeros,w,h,s).count()==0,"blank gained vinyl " +style);
            }
            // Legacy path does not depend on new knob when reset to zero.
            s.lineHighlightRetention=0f;
            for(int i=0;i<4;i++)ok(VinylEngine.svg(before,i).equals(VinylEngine.svg(VinylEngine.generate(img,w,h,s),i)),"legacy changed "+style);
        }
        ok(promotedAfter>promotedBefore+60,"No meaningful recovery: before="+promotedBefore+" after="+promotedAfter);
        VinylEngine.Settings s=state(VinylEngine.LINE);
        s.snips.add(new SnipMask.Operation(SnipMask.REMOVE_INSIDE,.43f,.10f,.57f,.10f,.57f,.80f,.43f,.80f));
        s.lineHighlightRetention=0;
        VinylEngine.Result before=VinylEngine.generate(img,w,h,s);
        s.lineHighlightRetention=1;
        VinylEngine.Result after=VinylEngine.generate(img,w,h,s);
        int maskedChanged=0;
        for(int i=0;i<4;i++){
            if(!VinylEngine.svg(before,i).equals(VinylEngine.svg(after,i)))maskedChanged++;
            for(VinylEngine.Shape shape:after.shapes.get(i))if(!shape.circle)
                ok(s.snips.allowsPolygon(shape.xy,s.widthMm,after.heightMm),
                    "Snip-aware highlight crosses excluded region "+i);
        }
        ok(maskedChanged>0,"Snip-aware highlight recovery was disabled");
        LineCutDiagnostics.Report maskedAudit=LineCutDiagnostics.inspect(after);
        ok(maskedAudit.complete&&maskedAudit.selfIntersections==0&&maskedAudit.subMinimumWidthSamples==0,
            "Snip highlight geometry unsafe");
        ok(state(VinylEngine.LINE).copy().lineHighlightRetention==0,"Default is not 0");
        s.lineHighlightRetention=0.8f;
        ok(s.copy().lineHighlightRetention==0.8f,"Copy drops retention");
        System.out.println("PASS highlight recovery: before="+promotedBefore+" after="+promotedAfter+"; cut safety, blank, snip, legacy, copy");
    }
}

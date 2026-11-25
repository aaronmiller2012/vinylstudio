package com.halftonestudio;

import java.util.Arrays;

/** Behavioral and physical-cut regression for Snip-aware modern ribbon recovery. */
public final class LineCoverageRC101Test {
    static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    static int[] portrait(int w,int h){
        int[] rgb=new int[w*h];
        Arrays.fill(rgb,0x00ffffff);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float dx=(x-w*.5f)/(w*.39f),dy=(y-h*.50f)/(h*.47f);
            if(dx*dx+dy*dy>1)continue;
            int color=0xffd4a6aa;
            if(y<h*.23f)color=0xff43342f;
            if(y>h*.64f)color=0xff634540;
            if(y>h*.35f&&y<h*.43f && (x<w*.44f||x>w*.56f))color=0xff29262b;
            if(y>h*.53f&&y<h*.58f&&x>w*.40f&&x<w*.60f)color=0xff63313f;
            rgb[y*w+x]=color;
        }
        return rgb;
    }
    static VinylEngine.Settings modern(int style){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.classicCmykLines=false;s.widthMm=110f;s.marginMm=1.5f;s.registration=false;
        s.minimumMm=.72f;s.bridgeMm=.42f;s.pattern=style;
        s.lineSampleMm=.40f;s.portraitMidtones=.62f;s.lineMaxFraction=.88f;
        s.shapeDefinition=.32f;s.detailBoost=0f;
        s.lineHighlightRetention=.55f;s.linePresenceRecovery=.65f;
        s.portraitBackgroundGuard=0f;
        for(VinylEngine.Layer l:s.layers){l.spacing=2.7f;l.lineGammaOverride=1.55f;}
        return s;
    }
    static float area(VinylEngine.Result result){
        double total=0;
        for(var layer:result.shapes)for(VinylEngine.Shape sh:layer){
            if(sh.circle){total+=Math.PI*sh.r*sh.r;continue;}
            float[] p=sh.xy;double signed=0;
            for(int i=0;i<p.length;i+=2){int j=(i+2)%p.length;signed+=(double)p[i]*p[j+1]-(double)p[j]*p[i+1];}
            total+=Math.abs(signed)*.5;
        }
        return (float)total;
    }
    static void physical(VinylEngine.Result result){
        VinylEngine.Settings s=result.settings;
        for(int li=0;li<result.shapes.size();li++){
            check(VinylEngine.svgElementsForLayer(result,li)<=5000,"path budget exceeded");
            for(VinylEngine.Shape shape:result.shapes.get(li)){
                if(shape.circle)continue;
                check(s.snips.allowsPolygon(shape.xy,s.widthMm,result.heightMm),"escaped Snip polygon");
            }
        }
        check(VinylEngine.validateExport(result)==null,"unsafe export: "+VinylEngine.validateExport(result));
        LineCutDiagnostics.Report report=LineCutDiagnostics.inspect(result);
        check(report.complete&&report.subMinimumWidthSamples==0&&report.selfIntersections==0,
            "unsafe line paths: underwidth="+report.subMinimumWidthSamples+" crossings="+report.selfIntersections);
    }
    public static void main(String[] args){
        check(LineToneRecovery.widthResponse(.20f,1f,.7f)>LineToneRecovery.widthResponse(.20f,1f,0f),"modern transfer did not retain weak tones");
        check(LineToneRecovery.directionalFootprint(0f,1f,1f)==0f,"blank channel fabricated");
        check(LineToneRecovery.localBendAllowance(0f,.1f,.2f,.05f)>.05f,"local bend allowance missing");
        VinylEngine.Settings selection=VinylEngine.Settings.defaults();
        SmartHalftonePresets.apply(selection,VinylEngine.LINE,VinylSizing.BALANCED,4.2f,4.8f);
        check(!selection.classicCmykLines&&selection.linePresenceRecovery>0,"new line selection didn't activate modern recovery");
        VinylEngine.Settings classic=VinylEngine.Settings.defaults();
        check(classic.classicCmykLines,"classic manual baseline lost");
        int w=120,h=144;int[] image=portrait(w,h);
        int improved=0;long examined=0,promoted=0;
        for(int style:new int[]{VinylEngine.LINE,VinylEngine.STRAIGHT,VinylEngine.CONTOUR,VinylEngine.WAVE,VinylEngine.ZIGZAG}){
            VinylEngine.Settings s=modern(style);
            s.snips.add(new SnipMask.Operation(SnipMask.REMOVE_INSIDE,
                .465f,.16f,.535f,.16f,.535f,.85f,.465f,.85f));
            s.linePresenceRecovery=0f;s.lineHighlightRetention=0f;
            VinylEngine.Result before=VinylEngine.generate(image,w,h,s);
            s.linePresenceRecovery=.78f;s.lineHighlightRetention=.70f;
            VinylEngine.Result after=VinylEngine.generate(image,w,h,s);
            float a=area(before),b=area(after);
            if(b>a+2)improved++;
            physical(after);
            for(int i=0;i<after.lineMetrics.size();i++){
                examined+=after.lineMetrics.get(i).examined;
                promoted+=after.lineMetrics.get(i).promoted;
            }
        }
        check(improved>=2,"Snipped portrait coverage did not improve: "+improved);
        check(examined>100&&promoted>0,"Snip recovery never examined or promoted a carrier");
        VinylEngine.Settings s=modern(VinylEngine.LINE);
        s.snips.add(new SnipMask.Operation(SnipMask.REMOVE_INSIDE,.45f,.1f,.55f,.1f,.55f,.9f,.45f,.9f));
        for(int blank:new int[]{0xffffffff,0x00ffffff,s.paper}){
            int[] photo=new int[w*h];Arrays.fill(photo,blank);
            check(VinylEngine.generate(photo,w,h,s).count()==0,"blank gained colored ribbons");
        }
        System.out.println("PASS RC101: modern line defaults, 5 Snipped line families, physical masks, width and blank checks; improved="+improved+" promoted="+promoted+" examined="+examined);
    }
}

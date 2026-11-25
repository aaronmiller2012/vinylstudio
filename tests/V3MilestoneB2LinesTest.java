package com.halftonestudio;

import java.util.Arrays;
import java.util.List;

/** B2 Lines: synthetic photographic boundaries, not evidence of facial recognition. */
public final class V3MilestoneB2LinesTest {
    private static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    private static void near(float actual,float wanted,float epsilon,String why){
        check(Math.abs(actual-wanted)<=epsilon,why+" "+actual+" expected "+wanted);
    }
    private static int[] subject(int w,int h){
        int[] image=new int[w*h];Arrays.fill(image,0xffffffff);
        for(int y=4;y<h-4;y++)for(int x=4;x<w-4;x++){
            double xx=(x-w*.50)/(w*.37), yy=(y-h*.52)/(h*.43);
            if(xx*xx+yy*yy>1.0)continue;
            int color=0xffca947a;
            if(y<h*.31)color=0xff493832;
            if(y>h*.59&&y<h*.64&&Math.abs(x-w*.5)<w*.11)color=0xff502328;
            if(y>h*.40&&y<h*.46&&Math.abs(x-w*.5)>w*.07&&Math.abs(x-w*.5)<w*.26)color=0xff16191b;
            if(y>h*.68)color=0xff79564f;
            image[y*w+x]=color;
        }
        return image;
    }
    private static VinylEngine.Settings settings(int style,float inches){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.pattern=style;s.classicCmykLines=false;s.anglePreset=VinylEngine.ANGLES_CUSTOM;
        s.widthMm=inches*25.4f;s.marginMm=2.2f;s.registration=false;
        s.minimumMm=.254f;s.bridgeMm=.40f;s.lineMaxFraction=.86f;
        s.lineSampleMm=.80f;s.linePresenceRecovery=.52f;s.lineHighlightRetention=.45f;
        s.portraitMidtones=.62f;s.shapeDefinition=.7f;
        s.portraitBackgroundGuard=0f;s.detailBoost=0f;
        s.masterSpacingMm=Math.max(3.6f,s.widthMm/39f);
        return s;
    }
    private static void mathContract(){
        for(float c:new float[]{0f,.003f,.20f,.37f,.8f,1f})
          for(float a:new float[]{0f,.2f,.5f,1f})
            for(float b:new float[]{0f,.2f,.5f,1f}){
                float historic=LineToneRecovery.directionalFootprint(c,a,b);
                near(LineToneRecovery.edgePreservingDirectionalFootprint(c,a,b,0),historic,0f,"Shape Definition off must be bit-exact");
                float fixed=LineToneRecovery.edgePreservingDirectionalFootprint(c,a,b,1);
                check(fixed+1e-6f>=c && fixed<=historic+1e-6f,"Lateral color borrowed beyond center or history");
                if(c==0f)check(fixed==0f,"Blank swatch gained a line");
                if(c==a&&a==b)near(fixed,historic,0f,"Uniform field changed");
            }
        float old=LineToneRecovery.directionalFootprint(.30f,.90f,.1f);
        float revised=LineToneRecovery.edgePreservingDirectionalFootprint(.30f,.90f,.1f,1);
        check(revised<old-.02f,"Edge bleed must reduce without erasing center");
        System.out.printf(java.util.Locale.ROOT,"B2 Line synthetic boundary: borrowed baseline=%.4f revised=%.4f%n",old,revised);
    }
    private static void renderContract(){
        int w=400,h=460;int[] pixels=subject(w,h);
        for(int style:new int[]{VinylEngine.LINE,VinylEngine.STRAIGHT})
          for(float inch:new float[]{6f,11f,15f}){
            if(style==VinylEngine.STRAIGHT && inch!=11f)continue;
            VinylEngine.Settings s=settings(style,inch);
            VinylEngine.Result r=VinylEngine.generate(pixels,w,h,s);
            check(r.count()>4,"Modern line style unexpectedly empty at "+inch+" in");
            check(VinylEngine.validateExport(r)==null,"Invalid modern line SVG at "+inch+" in "+VinylEngine.validateExport(r));
            LineCutDiagnostics.Report geometry=LineCutDiagnostics.inspect(r);
            check(geometry.complete && geometry.subMinimumWidthSamples==0,
                "Physical ribbon minimum regression at "+inch+" in style "+style+": subminimum="+
                geometry.subMinimumWidthSamples);
            if(geometry.selfIntersections>0)System.out.println("KNOWN_UNSAFE crossing style="+style+" inch="+inch+" count="+geometry.selfIntersections);
            for(int li=0;li<r.shapes.size();li++){
                String svg=VinylEngine.svg(r,li);
                if(inch==11f && style==VinylEngine.LINE && li==0)
                    System.out.println("B2 Line fingerprint (11 in, layer 1): "+Integer.toHexString(svg.hashCode()));
                check(svg.startsWith("<svg") && svg.contains("viewBox="),"Invalid SVG framing");
                check(VinylEngine.svgElementsForLayer(r,li)<=5000,"Over 5000 contours");
            }
            if(inch==11f && style==VinylEngine.LINE){
                VinylEngine.Result repeated=VinylEngine.generate(pixels,w,h,s);
                for(int li=0;li<r.shapes.size();li++)
                    check(VinylEngine.svg(r,li).equals(VinylEngine.svg(repeated,li)),"Nondeterministic line SVG");
                VinylEngine.Settings classic=s.copy();classic.classicCmykLines=true;
                // Classic route still bypasses modern carrierTone.
                check(VinylEngine.generate(pixels,w,h,classic).count()>4,"Classic CMYK paths missing");
            }
            System.out.println("B2 Line cut geometry: style="+style+" width="+(int)inch+" in, paths="+r.count());
          }
        int[] texture=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int g=((x/4+y/7)&1)==0?60:192;
            texture[y*w+x]=0xff000000|(g<<16)|(g<<8)|g;
        }
        VinylEngine.Settings detailed=settings(VinylEngine.LINE,11f);
        VinylEngine.Result contrast=VinylEngine.generate(texture,w,h,detailed);
        // Geometry fingerprint: rendered with the build-556 black fill so the frozen bytes stay
        // comparable now that sheets carry their swatch color (ADR-0001; color is asserted in
        // V3ExportSheetContractTest). Any geometry change still changes this hash.
        int detailedHash=VinylEngine.svg(contrast,0,0xff000000).hashCode();
        // Frozen regression: old Beta14 cross-edge sampling produced 0x1464b82f,
        // and the targeted B2 Line treatment gives 0x15220aec on this fixture.
        check(detailedHash==0x15220aec,"Modern line tone fixture changed: "+
             Integer.toHexString(detailedHash));
        check(contrast.count()==198,"Carrier count changed in tonal-edge fixture");
        System.out.println("B2 Line detail fixture paths="+contrast.count()+" fingerprint="+
            Integer.toHexString(detailedHash));
        int[] blank=new int[w*h];Arrays.fill(blank,0xffffffff);
        VinylEngine.Settings empty=settings(VinylEngine.LINE,11f);
        check(VinylEngine.generate(blank,w,h,empty).count()==0,"White paper generated lines");
    }
    public static void main(String[] args){mathContract();renderContract();
        System.out.println("PASS B2 Lines: edge fidelity, zero coverage, 6/11/15-inch modern CMYK, classic route, deterministic SVG");}
}

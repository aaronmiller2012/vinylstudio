package com.halftonestudio;
import java.util.*;
/** Cross-layer invariants. Tests real SVG vector geometry, not UI screenshots. */
public final class ScreenAndVinylBalanceTest {
    static void ok(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static int role(VinylEngine.Result r,int channel){for(int i=0;i<r.settings.layers.size();i++)if(r.settings.layers.get(i).cmykChannel==channel)return i;throw new AssertionError("missing channel");}
    static void anglesStable(){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        VinylEngine.applyAnglePreset(s);
        float[] first=new float[4];for(int i=0;i<4;i++)first[i]=s.layers.get(i).angle;
        Collections.swap(s.layers,0,3);VinylEngine.applyAnglePreset(s);
        for(VinylEngine.Layer l:s.layers)ok(l.angle==first[l.cmykChannel==2?0:l.cmykChannel==0?1:l.cmykChannel==1?2:3],"CMYK identity changed on reorder");
        VinylEngine.Layer extra=new VinylEngine.Layer("Skin highlight",0xffb37e72);s.layers.add(extra);s.mode=VinylEngine.SPOT;
        VinylEngine.applyAnglePreset(s);
        for(VinylEngine.Layer l:s.layers)if(l.cmykChannel>=0){
            float expected=l.cmykChannel==0?15:l.cmykChannel==1?75:l.cmykChannel==2?0:45;
            ok(l.angle==expected,"adding a fifth color rotated a CMYK channel");
        }
        ok(extra.angle!=15&&extra.angle!=75&&extra.angle!=0&&extra.angle!=45,"supplemental layer shares anchor angle");
        s.anglePreset=VinylEngine.ANGLES_PARALLEL;VinylEngine.applyAnglePreset(s);
        ok(s.anglePreset==VinylEngine.ANGLES_CLASSIC,"parallel preset must auto-repair");
    }
    static void paletteBalance(){
        final int white=0xffffffff,black=0xff090909;
        final int peach=0xffdb9f81,shadow=0xff3b2828,neutral=0xff686868;
        ok(CmykWhitePaperBalance.adjust(.5f,peach,3,white)<.5f,"black chromatic midtone not reduced");
        ok(CmykWhitePaperBalance.adjust(.6f,peach,2,white)<.6f,"yellow chromatic midtone not reduced");
        ok(CmykWhitePaperBalance.adjust(.5f,shadow,3,white)>.48f,"deep facial outline lost");
        ok(CmykWhitePaperBalance.adjust(.5f,neutral,3,white)==.5f,"neutral black should remain unchanged");
        ok(CmykWhitePaperBalance.adjust(.5f,peach,3,black)==.5f,"black paper must not use white separation");
        ok(CmykWhitePaperBalance.adjust(0f,peach,0,white)==0f,"must not add imaginary cyan geometry");
    }
    static void perColorCutStyles(){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();s.widthMm=84;s.marginMm=2;s.minimumMm=.35f;s.bridgeMm=.2f;
        s.masterSpacingMm=4f;s.pattern=VinylEngine.LINE;s.registration=false;
        // Test a representative image with dark, chromatic and paper-white patches.
        int w=48,h=48;int[] p=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)p[y*w+x]=(x<w/3?0xff7e5162:x<2*w/3?0xff2a678a:0xffdec09d);
        s.layers.get(1).lineStyleOverride=VinylEngine.ROUND;
        VinylEngine.Result round=VinylEngine.generate(p,w,h,s);
        String cyan=VinylEngine.svg(round,role(round,0));
        ok(cyan.contains("<circle"),"per-color dot override did not produce circles");
        s.layers.get(1).lineStyleOverride=VinylEngine.DIAMOND;
        VinylEngine.Result diamond=VinylEngine.generate(p,w,h,s);
        ok(!VinylEngine.svg(diamond,role(diamond,0)).contains("<circle"),"diamond incorrectly used global line or circle");
        int magenta=role(round,1);
        ok(VinylEngine.svg(round,magenta).equals(VinylEngine.svg(diamond,role(diamond,1))),"cyan override modified another channel's SVG");
        ok(VinylEngine.validateExport(round)==null&&VinylEngine.validateExport(diamond)==null,"override must be cut-safe");
    }
    /** Real closed SVG shape area must respond to the white-paper profile. */
    static double area(VinylEngine.Result r,int channel){
        double sum=0;
        for(int i=0;i<r.shapes.size();i++)if(r.settings.layers.get(i).cmykChannel==channel){
            for(VinylEngine.Shape sh:r.shapes.get(i)){
                if(sh.circle){sum+=Math.PI*sh.r*sh.r;continue;}
                float[] p=sh.xy;double a=0;
                for(int j=0;j<p.length;j+=2){int k=(j+2)%p.length;
                    a+=p[j]*p[k+1]-p[k]*p[j+1];}
                sum+=Math.abs(a)*.5;
            }
        }
        return sum;
    }
    static void actualWhitePaperCuts(){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        ClassicCmykLines.useModern(s);s.widthMm=120;s.masterSpacingMm=3f;s.minimumMm=.30f;s.bridgeMm=.3f;
        s.registration=false;s.marginMm=2;s.pattern=VinylEngine.LINE;
        int w=80,h=80;int[] pixels=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)
            pixels[y*w+x]=x<w/2?0xffdb9f81:0xff503d36;
        s.paper=0xfffaf9f6;VinylEngine.Result balanced=VinylEngine.generate(pixels,w,h,s);
        // Same fully opaque source with a non-white substrate disables the
        // calibration; the shape area difference proves the actual SVG changed.
        s.paper=0xff141414;VinylEngine.Result original=VinylEngine.generate(pixels,w,h,s);
        ok(area(balanced,2)<area(original,2)*.95,
            "white CMYK yellow line polygons were not reduced");
        ok(area(balanced,3)<area(original,3)*.95,
            "white CMYK black midtone polygons were not reduced");
        ok(area(balanced,0)>0&&area(balanced,1)>0,
            "source cyan and magenta must survive coverage balancing");
        ok(VinylEngine.validateExport(balanced)==null,
            "white-profile SVG is invalid for cutting");
    }
    public static void main(String[] args){anglesStable();paletteBalance();perColorCutStyles();actualWhitePaperCuts();
        System.out.println("PASS: CMYK identity-safe angles; white paper channel protection; true per-color dot/ribbon SVGs");}
}

package com.halftonestudio;

import java.util.*;
/** v1.9 regression tests: global continuity, 2–8 palettes, angles, variable-width lines, SVG limits. */
public class VinylWorkbenchTest {
    static void ok(boolean b,String label){if(!b)throw new AssertionError(label);}
    static int[] grayscale(int w,int h){int[] p=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){
        int v=(x*255)/(w-1);p[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;
    }return p;}
    static float polygonArea(VinylEngine.Shape p){if(p.circle)return (float)Math.PI*p.r*p.r;float sum=0;for(int i=0;i<p.xy.length;i+=2){int j=(i+2)%p.xy.length;
        sum+=p.xy[i]*p.xy[j+1]-p.xy[j]*p.xy[i+1];}return Math.abs(sum)*.5f;}
    static float layerArea(VinylEngine.Result result,int index){float total=0;for(VinylEngine.Shape p:result.shapes.get(index))total+=polygonArea(p);return total;}
    static void testInches(){ok(Math.abs(VinylEngine.toInches(25.4f)-1)<.0001,"25.4mm = 1 inch");
        ok(Math.abs(VinylEngine.toMm(.1f)-2.54f)<.0001,"inch-to-mm precision");}
    static void testPalettes(){for(int id=0;id<VinylPalettes.NAMES.length;id++)for(int n=2;n<=8;n++){
        int[] c=VinylPalettes.get(id,n);ok(c.length==n,"palette count");
        HashSet<Integer> uniq=new HashSet<>();for(int x:c)uniq.add(x);ok(uniq.size()==n,"repeated palette color");
    }}
    static void testLink(){VinylEngine.Settings s=VinylEngine.Settings.defaults();s.pattern=VinylEngine.ROUND;s.masterSpacingMm=3.81f;
        s.linkedControls=true;s.masterDensity=.65f;s.masterContrast=1.4f;
        for(VinylEngine.Layer l:s.layers){l.spacing=8f;l.density=2f;l.contrast=.1f;}
        VinylEngine.Result r=VinylEngine.generate(grayscale(38,25),38,25,s);
        for(VinylEngine.Layer l:r.settings.layers){ok(Math.abs(l.spacing-3.81f)<.0001,"linked spacing mismatch");
            ok(Math.abs(l.density-.65f)<.0001,"linked coverage mismatch");
            ok(Math.abs(l.contrast-1.4f)<.0001,"linked contrast mismatch");}
        // Generator snapshots must never mutate the edited settings on the UI thread.
        for(VinylEngine.Layer l:s.layers)ok(l.spacing==8f,"input mutated");
        s.linkedControls=false;r=VinylEngine.generate(grayscale(38,25),38,25,s);
        for(VinylEngine.Layer l:r.settings.layers)ok(l.spacing==8f,"per-layer overrides lost");
    }
    static void testAngles(){VinylEngine.Settings s=VinylEngine.Settings.defaults();
        int[] channels={2,0,1,3};float[] classicByChannel={15,75,0,45};
        String[] order={"Yellow","Cyan","Magenta","Black"};
        for(int i=0;i<4;i++){ok(s.layers.get(i).name.equals(order[i]),"vinyl CMYK order "+i);ok(s.layers.get(i).cmykChannel==channels[i],"vinyl channel identity "+i);} 
        VinylEngine.applyAnglePreset(s);for(int i=0;i<4;i++)ok(s.layers.get(i).angle==classicByChannel[s.layers.get(i).cmykChannel],"classic CMYK "+i);
        s.anglePreset=VinylEngine.ANGLES_PHOTOSHOP;VinylEngine.applyAnglePreset(s);
        float[] adobe={108,162,90,45};for(int i=0;i<4;i++)ok(s.layers.get(i).angle==adobe[s.layers.get(i).cmykChannel],"Photoshop style "+i);
        s.anglePreset=VinylEngine.ANGLES_PARALLEL;VinylEngine.applyAnglePreset(s);
        ok(s.anglePreset==VinylEngine.ANGLES_CLASSIC,"CMYK should repair old parallel presets");
        for(VinylEngine.Layer l:s.layers)ok(l.angle==classicByChannel[l.cmykChannel],"repaired CMYK separation");
        s.mode=VinylEngine.SPOT;s.anglePreset=VinylEngine.ANGLES_PARALLEL;VinylEngine.applyAnglePreset(s);
        ok(s.anglePreset==VinylEngine.ANGLES_CLASSIC,"legacy parallel spot normalized");
        for(VinylEngine.Layer l:s.layers)ok(l.angle==classicByChannel[l.cmykChannel],"spot layers retain CMYK identities");
        s.anglePreset=VinylEngine.ANGLES_FAN;while(s.layers.size()<8)s.layers.add(new VinylEngine.Layer("extra",0xff202020));
        VinylEngine.applyAnglePreset(s);HashSet<Integer> a=new HashSet<>();for(VinylEngine.Layer l:s.layers)a.add(Math.round(l.angle));
        ok(a.size()>=7,"fan angles duplicate");
    }
    static void testVariableWidth(){VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.widthMm=110;s.marginMm=1;s.minimumMm=.15f;s.bridgeMm=.15f;s.pattern=VinylEngine.LINE;
        s.linkedControls=true;s.masterSpacingMm=3.0f;s.anglePreset=VinylEngine.ANGLES_PARALLEL;
        s.screenRotationDeg=-45;s.lineMaxFraction=.82f;s.lineSmoothing=.25f;s.lineSampleMm=.3f;
        int[] picture=grayscale(84,42);
        s.lineGamma=.55f;VinylEngine.Result broad=VinylEngine.generate(picture,84,42,s);
        s.lineGamma=2.1f;VinylEngine.Result slim=VinylEngine.generate(picture,84,42,s);
        float areaBroad=layerArea(broad,3),areaSlim=layerArea(slim,3);
        ok(areaBroad>areaSlim*1.15f,"changing gamma should alter continuous ribbon width: "+areaBroad+" vs "+areaSlim);
        boolean longRibbon=false;for(VinylEngine.Shape sh:broad.shapes.get(3))if(!sh.circle&&sh.xy.length>60)longRibbon=true;
        ok(longRibbon,"no continuous variable-width ribbons");
        ok(VinylEngine.validateExport(broad)==null,"valid marks and SVG limit for lines");
    }
    static void testCricutCap(){VinylEngine.Settings s=VinylEngine.Settings.defaults();s.pattern=VinylEngine.ROUND;
        s.widthMm=400;s.masterSpacingMm=.88f;s.marginMm=4;s.minimumMm=.1f;s.bridgeMm=.1f;
        int[] dark=new int[100*100];Arrays.fill(dark,0xff000000);
        boolean guarded=false;try{VinylEngine.generate(dark,100,100,s);}catch(IllegalArgumentException e){guarded=e.getMessage().contains("5,000")||e.getMessage().contains("samples");}
        ok(guarded,"Cricut path cap not enforced");
    }
    public static void main(String[] args){testInches();testPalettes();testLink();testAngles();testVariableWidth();testCricutCap();
        System.out.println("PASS: inches; eight unique palettes x 2-8; globally linked controls and overrides; CMYK/Photoshop/fan screens; variable-width line response; SVG limits");}
}

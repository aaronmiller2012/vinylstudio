package com.halftonestudio;

import java.util.Arrays;
import java.util.HashSet;

/** All user-facing vinyl styles, screen-angle persistence and real vector geometry. */
public final class PresetMatrixRegressionTest {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static int[] scene(int w,int h){
        int[] p=new int[w*h];
        final int[] swatches={0xff1167a9,0xffb83a85,0xffe1bc26,0xff272c34,
                0xffc66d3a,0xfff2ddd0,0xff58a8a0,0xff705a9b};
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int c=swatches[(x*4/w+y*4/h)%swatches.length];
            if(x<w/9||y<h/11)c=0xfff9f9f7;
            p[y*w+x]=c;
        }return p;
    }
    private static VinylEngine.Settings base(){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.widthMm=62;s.marginMm=2;s.masterSpacingMm=3.5f;s.minimumMm=.33f;
        s.bridgeMm=.27f;s.lineSampleMm=.5f;s.registration=true;
        return s;
    }
    private static int index(VinylEngine.Result r,int channel){
        for(int i=0;i<r.settings.layers.size();i++)if(r.settings.layers.get(i).cmykChannel==channel)return i;
        throw new AssertionError("Missing CMYK channel "+channel);
    }
    private static void classic(VinylEngine.Result r){
        float[] expected={15,75,0,45};
        require(r.settings.mode==VinylEngine.CMYK,"Lost CMYK mode");
        require(r.settings.anglePreset==VinylEngine.ANGLES_CLASSIC,"CMYK preset not canonical");
        for(int channel=0;channel<4;channel++){
            float v=r.settings.layers.get(index(r,channel)).angle;
            require(Math.abs(v-expected[channel])<.001,"Wrong CMYK angle for "+channel+": "+v);
        }
    }
    private static void validCuts(VinylEngine.Result r,String label){
        require(r.count()>0,"No cut geometry in "+label);
        for(int i=0;i<r.shapes.size();i++){
            String svg=VinylEngine.svg(r,i);
            require(svg.contains("</svg>")&&!svg.contains("<clipPath")&&!svg.contains("<line "),"Non-cuttable SVG in "+label);
            require(VinylEngine.svgElementsForLayer(r,i)<=5000,"Path cap exceeded in "+label);
            require(svg.contains("<path")||svg.contains("<circle"),"No SVG paths in "+label);
            for(VinylEngine.Shape sh:r.shapes.get(i)){
                if(sh.circle)require(Float.isFinite(sh.x)&&Float.isFinite(sh.y)&&Float.isFinite(sh.r),"Invalid circle in "+label);
                else for(float v:sh.xy)require(Float.isFinite(v),"Invalid polygon in "+label);
            }
        }
        require(VinylEngine.validateExport(r)==null,"Export invalid in "+label+": "+VinylEngine.validateExport(r));
    }
    private static void testAllHalftones(){
        int[] pix=scene(60,76);
        for(int style=0;style<VinylEngine.PATTERNS.length;style++){
            VinylEngine.Settings s=base();
            SmartHalftonePresets.apply(s,style,VinylSizing.BALANCED,2.45f,3.10f);
            s.widthMm=62;s.masterSpacingMm=3.5f;
            require(SmartHalftonePresets.controls(style).length==3,"Wrong control count for "+style);
            require(s.anglePreset==VinylEngine.ANGLES_CLASSIC,"Style collapsed CMYK screens: "+style);
            VinylEngine.Result r=VinylEngine.generate(pix,60,76,s);
            classic(r);
            validCuts(r,VinylEngine.PATTERNS[style]);
            if(style==VinylEngine.BEADED){
                for(VinylEngine.Shape bead:r.shapes.get(index(r,3)))require(bead.circle,"Beaded path is not a bead");
            }
        }
    }
    private static void testPresetsAndLegacy(){
        int[] pix=scene(60,76);
        VinylEngine.Settings s=base();s.pattern=VinylEngine.STRAIGHT;
        s.anglePreset=VinylEngine.ANGLES_PARALLEL;
        VinylEngine.Result legacy=VinylEngine.generate(pix,60,76,s);
        classic(legacy);require(s.anglePreset==VinylEngine.ANGLES_PARALLEL,"Generate should not change editor settings");
        VinylEngine.applyAnglePreset(s);
        require(s.anglePreset==VinylEngine.ANGLES_CLASSIC,"In-place legacy preset repair missing");
        // A saved favorite can carry the wrong flag; choosing a new pattern must repair it.
        s.anglePreset=VinylEngine.ANGLES_PARALLEL;
        SmartHalftonePresets.apply(s,VinylEngine.WAVE,VinylSizing.BALANCED,3,4);
        classic(VinylEngine.generate(pix,60,76,s));
        // Photoshop preset is distinct and survives generation.
        s.anglePreset=VinylEngine.ANGLES_PHOTOSHOP;
        VinylEngine.Result photoshop=VinylEngine.generate(pix,60,76,s);
        float[] adobe={108,162,90,45};for(int ch=0;ch<4;ch++)
            require(Math.abs(photoshop.settings.layers.get(index(photoshop,ch)).angle-adobe[ch])<.001,"Adobe channel "+ch);
        // Fine-tuning must only alter one screen.
        s=base();s.pattern=VinylEngine.ELLIPSE;
        VinylEngine.Result baseline=VinylEngine.generate(pix,60,76,s);
        s.layers.get(1).angleFineTuneDeg=6;
        VinylEngine.Result changed=VinylEngine.generate(pix,60,76,s);
        require(!VinylEngine.svg(baseline,1).equals(VinylEngine.svg(changed,1)),"Cyan ellipse angle did not affect cut geometry");
        require(VinylEngine.svg(baseline,0).equals(VinylEngine.svg(changed,0)),"Cyan adjustment changed yellow paths");
        // Legacy parallel projects are repaired to independent screens even in spot mode.
        s=base();s.mode=VinylEngine.SPOT;s.anglePreset=VinylEngine.ANGLES_PARALLEL;
        VinylEngine.applyAnglePreset(s);
        require(s.anglePreset==VinylEngine.ANGLES_CLASSIC,"Spot screens must reject legacy parallel angles");
        float prev=-1;for(VinylEngine.Layer l:s.layers){require(l.angle!=prev,"Custom vinyl screens must be separated");prev=l.angle;}
    }
    private static void testPreviouslyInertControls(){
        int[] pix=scene(60,76);
        VinylEngine.Settings s=base();s.pattern=VinylEngine.SPIRAL;
        VinylEngine.Result spiral=VinylEngine.generate(pix,60,76,s);
        s.layers.get(1).angleFineTuneDeg=8;
        VinylEngine.Result spiralAdjusted=VinylEngine.generate(pix,60,76,s);
        require(!VinylEngine.svg(spiral,1).equals(VinylEngine.svg(spiralAdjusted,1)),"Spiral ignores CMYK angle");
        s=base();s.pattern=VinylEngine.CROSSHATCH;
        VinylEngine.Result crossA=VinylEngine.generate(pix,60,76,s);
        s.crossAngleDeg=37;
        VinylEngine.Result crossB=VinylEngine.generate(pix,60,76,s);
        require(!VinylEngine.svg(crossA,3).equals(VinylEngine.svg(crossB,3)),"Cross Angle slider doesn't change SVG");
        s=base();s.pattern=VinylEngine.HATCH;
        VinylEngine.Result engraveA=VinylEngine.generate(pix,60,76,s);
        s.hatchThreshold=.93f;
        VinylEngine.Result engraveB=VinylEngine.generate(pix,60,76,s);
        require(!VinylEngine.svg(engraveA,3).equals(VinylEngine.svg(engraveB,3)),"Hatch threshold has no effect");
        s=base();s.pattern=VinylEngine.BEADED;s.lineMaxFraction=.35f;
        VinylEngine.Result beadsSmall=VinylEngine.generate(pix,60,76,s);
        s.lineMaxFraction=.95f;
        VinylEngine.Result beadsLarge=VinylEngine.generate(pix,60,76,s);
        require(!VinylEngine.svg(beadsSmall,3).equals(VinylEngine.svg(beadsLarge,3)),"Bead size slider is inert");
        s=base();s.pattern=VinylEngine.RINGS;
        VinylEngine.Result rings=VinylEngine.generate(pix,60,76,s);
        // Radial lines are rotation-invariant; CMYK rings instead use phase offsets.
        require(!VinylEngine.svg(rings,index(rings,0)).equals(VinylEngine.svg(rings,index(rings,1))),"Rings have no channel-specific phase");
    }
    private static void testStencilPalettes(){
        final int[] pix=scene(60,76);
        for(int count=2;count<=8;count++)for(int palette=0;palette<VinylPalettes.NAMES.length;palette++){
            VinylEngine.Settings s=base();s.mode=VinylEngine.STENCIL;s.paletteId=palette;
            s.masterSpacingMm=3.2f;s.stencilCleanup=1;s.stencilSmoothing=.08f;
            while(s.layers.size()>count)s.layers.remove(s.layers.size()-1);
            while(s.layers.size()<count)s.layers.add(new VinylEngine.Layer("custom",0xff444444));
            int[] colors=VinylPalettes.get(palette,count);
            for(int li=0;li<count;li++){s.layers.get(li).name="Vinyl "+li;s.layers.get(li).color=colors[li];s.layers.get(li).cmykChannel=-1;}
            VinylEngine.Result result=VinylEngine.generate(pix,60,76,s);
            require(result.shapes.size()==count,"Stencil layer count mismatch");
            validCuts(result,"stencil "+palette+" count "+count);
            for(int li=0;li<count;li++)for(VinylEngine.Shape sh:result.shapes.get(li))
                require(sh.contours!=null,"Stencil should emit traced compound vector regions");
        }
        // Paper background and transparent input must not generate phantom vinyl.
        VinylEngine.Settings s=base();s.mode=VinylEngine.STENCIL;s.stencilPaperBackground=true;
        int[] transparent=new int[60*76];Arrays.fill(transparent,0x00000000);
        VinylEngine.Result empty=VinylEngine.generate(transparent,60,76,s);
        require(empty.count()==0,"Transparent stencil unexpectedly produced vinyl");
    }
    public static void main(String[] args){
        testAllHalftones();testPresetsAndLegacy();testPreviouslyInertControls();testStencilPalettes();
        System.out.println("PASS: all "+VinylEngine.PATTERNS.length+" halftone styles, 7 stencil palettes x 2-8 layers, 4 CMYK angles, legacy/favorite repair, per-channel vector rotation, hatch controls, vinyl export");
    }
}

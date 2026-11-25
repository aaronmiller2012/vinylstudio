package com.halftonestudio;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.imageio.ImageIO;

/**
 * M5: every line-style slider the Adjust panel shows (StudioActivity.toolControls, the
 * shapeControls group and the shared ribbon sliders) must change the production cut
 * geometry when moved across its UI range. Real photo (tests/m4/photos/astronaut.png),
 * production engine, CMYK picker route (SmartHalftonePresets, modern engine) and the
 * new-project Classic route. A slider is "effective" when the sheets' total vinyl area
 * changes by at least 0.2 % or the piece count by at least 2.
 *
 * Sliders the UI shows but the engine provably ignores are listed in KNOWN_NO_OP with the
 * reason; they are reported, and the test fails if one of them starts working (so the list
 * is kept honest) or if any other slider has no effect.
 */
public final class M5LineSliderEffectTest {
    static int[] px;static int pw,ph;

    static final class Slider {
        final String name;final float lo,hi;final BiConsumer<VinylEngine.Settings,Float> set;
        Slider(String n,float lo,float hi,BiConsumer<VinylEngine.Settings,Float> set){name=n;this.lo=lo;this.hi=hi;this.set=set;}
    }
    static Slider s(String n,float lo,float hi,BiConsumer<VinylEngine.Settings,Float> set){return new Slider(n,lo,hi,set);}

    /** Mirrors StudioActivity's per-style switch (UI ranges in engine units). */
    static List<Slider> styleSliders(int style){
        List<Slider> l=new ArrayList<>();
        switch(style){
            case VinylEngine.LINE:
                l.add(s("Maximum line width",.30f,.92f,(t,v)->t.lineMaxFraction=v));
                l.add(s("Contour flow",0f,1f,(t,v)->t.contourStrength=v));
                l.add(s("Fine-detail ribbon strokes",0f,.85f,(t,v)->t.detailBoost=v));break;
            case VinylEngine.STRAIGHT:case VinylEngine.FADE:
                l.add(s("Maximum line width",.25f,.90f,(t,v)->t.lineMaxFraction=v));
                l.add(s("Fine-detail strokes",0f,1f,(t,v)->t.detailBoost=v));break;
            case VinylEngine.CONTOUR:
                l.add(s("Contour flow",0f,1f,(t,v)->t.contourStrength=v));
                l.add(s("Fine-detail strokes",0f,1f,(t,v)->t.detailBoost=v));break;
            case VinylEngine.CROSSHATCH:
                l.add(s("Hatch rotation",25f,90f,(t,v)->t.crossAngleDeg=v));
                l.add(s("Shadow emphasis",.95f,.35f,(t,v)->t.hatchThreshold=v));break;
            case VinylEngine.RINGS:case VinylEngine.RAYS:
                l.add(s("Focal point X",0f,1f,(t,v)->t.radialFocusX=v));
                l.add(s("Focal point Y",0f,1f,(t,v)->t.radialFocusY=v));break;
            case VinylEngine.ZIGZAG:
                l.add(s("Zigzag height",0f,.22f,(t,v)->t.waveAmplitude=v));
                l.add(s("Zigzags per inch",.3f,5f,(t,v)->t.waveCyclesPerInch=v));break;
            case VinylEngine.BROKEN:
                l.add(s("Break response (gamma)",.45f,2.8f,(t,v)->t.lineGamma=v));
                l.add(s("Maximum line width",.25f,.90f,(t,v)->t.lineMaxFraction=v));break;
            case VinylEngine.BEADED:
                l.add(s("Bead size",.30f,.95f,(t,v)->t.lineMaxFraction=v));
                l.add(s("Bead tonal response",.45f,2.8f,(t,v)->t.lineGamma=v));break;
            case VinylEngine.WAVE:
                l.add(s("Wave intensity",0f,.22f,(t,v)->t.waveAmplitude=v));
                l.add(s("Maximum line width",.30f,.78f,(t,v)->t.lineMaxFraction=v));break;
            case VinylEngine.HATCH:
                l.add(s("Shadow emphasis",.95f,.45f,(t,v)->t.hatchThreshold=v));
                l.add(s("Maximum line width",.30f,.78f,(t,v)->t.lineMaxFraction=v));break;
            default:break;
        }
        if(LineArtEngine.supportsShapeDefinition(style)){
            l.add(s("Edge-following lines",0f,1f,(t,v)->t.shapeDefinition=v));
            l.add(s("Follow form (bend)",0f,1f,(t,v)->t.contourStrength=v));
            l.add(s("Line weight in shadows",.40f,.95f,(t,v)->t.lineMaxFraction=v));
        }
        l.add(s("Spacing",.065f*25.4f,.40f*25.4f,(t,v)->{t.masterSpacingMm=v;for(VinylEngine.Layer y:t.layers)y.spacing=v;}));
        l.add(s("Tonal response",.35f,2.8f,(t,v)->t.lineGamma=v));
        l.add(s("Contour smoothing",0f,.9f,(t,v)->t.lineSmoothing=v));
        return l;
    }
    /** CMYK-only sliders, shown by the same conditions as StudioActivity. */
    static List<Slider> cmykSliders(VinylEngine.Settings base,int style){
        List<Slider> l=new ArrayList<>();
        if(style!=VinylEngine.BROKEN&&style!=VinylEngine.FADE)
            l.add(s("Light-area lines",0f,1f,(t,v)->t.lineHighlightRetention=v));
        if(!base.classicCmykLines&&LineArtEngine.supportsMicroContinuity(style)){
            l.add(s("Portrait line retention",0f,1f,(t,v)->t.linePresenceRecovery=v));
            l.add(s("Outer background reduction",0f,1f,(t,v)->t.portraitBackgroundGuard=v));
        }
        if(LineArtEngine.supportsPortraitMidtones(style))
            l.add(s("Portrait midtone recovery",0f,1f,(t,v)->t.portraitMidtones=v));
        if(LineArtEngine.supportsMicroContinuity(style)&&base.linePresenceRecovery<=.001f)
            l.add(s("Micro-gap recovery",0f,1f,(t,v)->t.lineContinuity=v));
        return l;
    }

    /** UI-visible controls the engine ignores for that style/route, with the reason. */
    static final Map<String,String> KNOWN_NO_OP=new LinkedHashMap<>();
    static void noop(String route,int style,String slider,String why){
        KNOWN_NO_OP.put(route+"/"+VinylEngine.PATTERNS[style]+"/"+slider,why);
    }
    static{
        // Found by this test (M5). The fix is UI gating in StudioActivity (not an engine
        // change): see docs/engineering/M5-LINES-DIFFUSION.md, "Slider audit".
        String smooth="lineSmoothing only low-passes the luminance bend of Variable-width, Contour and Hatch centrelines";
        String bend="contourStrength bends only Variable-width, Contour and Hatch centrelines";
        String micro="Classic v2.24 engine has no micro-gap stage (lineContinuity is read only by the modern engine)";
        String light="modern engine applies lineHighlightRetention only to parallel ribbon presence tracking";
        for(String route:new String[]{"picker","classic"}){
            for(int st:new int[]{VinylEngine.STRAIGHT,VinylEngine.WAVE,VinylEngine.CROSSHATCH,VinylEngine.ZIGZAG,
                    VinylEngine.BROKEN,VinylEngine.FADE,VinylEngine.BEADED,VinylEngine.RINGS,VinylEngine.RAYS})
                noop(route,st,"Contour smoothing",smooth);
            for(int st:new int[]{VinylEngine.STRAIGHT,VinylEngine.WAVE,VinylEngine.FADE})
                noop(route,st,"Follow form (bend)",bend);
        }
        for(int st:new int[]{VinylEngine.LINE,VinylEngine.STRAIGHT,VinylEngine.CONTOUR,VinylEngine.WAVE,VinylEngine.ZIGZAG})
            noop("classic",st,"Micro-gap recovery",micro);
        for(int st:new int[]{VinylEngine.BEADED,VinylEngine.RINGS,VinylEngine.RAYS})
            noop("picker",st,"Light-area lines",light);
    }

    static VinylEngine.Settings base(String route,int style){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.widthMm=101.6f;s.minimumMm=VinylEngine.MINIMUM_CUT_WIDTH_MM;s.registration=false;
        s.pattern=style;
        if(route.equals("picker"))SmartHalftonePresets.apply(s,style,VinylSizing.BALANCED,4f,4f*ph/pw);
        else for(VinylEngine.Layer l:s.layers)l.lineStyleOverride=-1;
        return s;
    }
    static double[] signature(VinylEngine.Settings s){
        VinylEngine.Result r=VinylEngine.generate(px,pw,ph,s);
        double area=0;int pieces=0;
        for(List<VinylEngine.Shape> layer:r.shapes)for(VinylEngine.Shape sh:layer){
            pieces++;
            if(sh.circle)area+=Math.PI*sh.r*sh.r;
            else{float[][] rings=sh.contours==null?new float[][]{sh.xy}:sh.contours;
                for(int i=0;i<rings.length;i++)area+=(i==0?1:-1)*Math.abs(StyleKit.signedArea(rings[i]));}
        }
        return new double[]{area,pieces};
    }

    public static void main(String[] a)throws Exception{
        BufferedImage in=ImageIO.read(new File("tests/m4/photos/astronaut.png"));
        pw=150;ph=Math.round(150f*in.getHeight()/in.getWidth());
        BufferedImage img=new BufferedImage(pw,ph,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=img.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(in,0,0,pw,ph,null);g.dispose();
        px=img.getRGB(0,0,pw,ph,null,0,pw);for(int i=0;i<px.length;i++)px[i]|=0xff000000;

        int[] styles={VinylEngine.LINE,VinylEngine.STRAIGHT,VinylEngine.CONTOUR,VinylEngine.WAVE,VinylEngine.HATCH,
            VinylEngine.CROSSHATCH,VinylEngine.ZIGZAG,VinylEngine.BROKEN,VinylEngine.FADE,VinylEngine.BEADED,
            VinylEngine.RINGS,VinylEngine.RAYS};
        List<String> dead=new ArrayList<>(),fixed=new ArrayList<>();int checked=0;
        for(String route:new String[]{"picker","classic"}){
            for(int style:styles){
                VinylEngine.Settings base=base(route,style);
                List<Slider> sliders=styleSliders(style);sliders.addAll(cmykSliders(base,style));
                for(Slider sl:sliders){
                    VinylEngine.Settings lo=base.copy(),hi=base.copy();
                    sl.set.accept(lo,sl.lo);sl.set.accept(hi,sl.hi);
                    double[] x=signature(lo),y=signature(hi);
                    double rel=Math.abs(x[0]-y[0])/Math.max(1e-6,Math.max(x[0],y[0]));
                    boolean effective=rel>=.002||Math.abs(x[1]-y[1])>=2;
                    String key=route+"/"+VinylEngine.PATTERNS[style]+"/"+sl.name;
                    checked++;
                    System.out.printf(Locale.US,"%-62s area %9.1f -> %9.1f mm2 (%5.1f%%) pieces %5.0f -> %5.0f %s%n",key,x[0],y[0],rel*100,x[1],y[1],effective?"":"NO EFFECT");
                    boolean known=KNOWN_NO_OP.containsKey(key);
                    if(!effective&&!known)dead.add(key);
                    if(effective&&known)fixed.add(key);
                }
            }
        }
        for(Map.Entry<String,String> e:KNOWN_NO_OP.entrySet())System.out.println("KNOWN NO-OP (reported to integrator): "+e.getKey()+" - "+e.getValue());
        if(!fixed.isEmpty())throw new AssertionError("Sliders listed as no-op now work; update KNOWN_NO_OP: "+fixed);
        if(!dead.isEmpty())throw new AssertionError("Sliders without measurable effect: "+dead);
        // M5 integration: every measured no-op must be hidden by the editor's visibility policy,
        // and no slider with an effect may be hidden.
        List<String> shown=new ArrayList<>();
        for(String key:KNOWN_NO_OP.keySet()){
            String[] k=key.split("/",3);int style=-1;
            for(int i=0;i<VinylEngine.PATTERNS.length;i++)if(VinylEngine.PATTERNS[i].equals(k[1]))style=i;
            if(LineSliderVisibility.visible(k[0],style,k[2]))shown.add(key);
        }
        if(!shown.isEmpty())throw new AssertionError("No-op sliders still shown in the editor: "+shown);
        for(String route:new String[]{"picker","classic"})for(int style:styles)for(Slider sl:styleSliders(style)){
            String key=route+"/"+VinylEngine.PATTERNS[style]+"/"+sl.name;
            if(!KNOWN_NO_OP.containsKey(key)&&!LineSliderVisibility.visible(route,style,sl.name))
                throw new AssertionError("Effective slider hidden: "+key);
        }
        // QA fix: a per-layer line style override makes the bend and smoothing sliders effective even
        // when the main style ignores them, so they must stay visible.
        for(int over:new int[]{VinylEngine.LINE,VinylEngine.HATCH}){
            VinylEngine.Settings s=base("picker",VinylEngine.STRAIGHT);
            s.layers.get(0).lineStyleOverride=over;
            VinylEngine.Settings lo=s.copy(),hi=s.copy();lo.contourStrength=0f;hi.contourStrength=1f;
            double[] x=signature(lo),y=signature(hi);
            if(Math.abs(x[0]-y[0])/Math.max(1e-6,Math.max(x[0],y[0]))<.002)throw new AssertionError("override fixture has no bend effect");
            if(!LineSliderVisibility.followForm(s)||!LineSliderVisibility.contourSmoothing(s))
                throw new AssertionError("Bend/smoothing hidden although layer 1 is "+VinylEngine.PATTERNS[over]);
            s.layers.get(0).enabled=false;
            if(LineSliderVisibility.followForm(s))throw new AssertionError("disabled layer override must not show the slider");
        }
        System.out.println("PASS M5LineSliderEffectTest: "+checked+" slider/style/route cases");
    }
}

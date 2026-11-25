package com.halftonestudio;

import java.util.*;

/** Independent, Android-free engine. Coordinates and widths are millimeters.
 * The same generated shape objects are used for on-screen preview and SVG export.
 */
public final class VinylEngine {
    private VinylEngine() {}
    public static final int SPOT=0, MOSAIC=1, CMYK=2, STENCIL=3;
    public static final int NEAREST=0, BAYER=1, FLOYD=2;
    public static final int ANGLES_PARALLEL=0, ANGLES_CLASSIC=1, ANGLES_PHOTOSHOP=2, ANGLES_FAN=3, ANGLES_CUSTOM=4;
    public static final float MM_PER_INCH=25.4f;
    public static float toInches(float mm){return mm/MM_PER_INCH;}
    public static float toMm(float inches){return inches*MM_PER_INCH;}
    public static final int ROUND=0,SQUARE=1,ELLIPSE=2,DIAMOND=3,DASH=4,LINE=5,HEX=6,SPIRAL=7,WAVE=8,HATCH=9,CROSS=10,STAR=11;
    public static final int STRAIGHT=12,CONTOUR=13,CROSSHATCH=14,RINGS=15,ZIGZAG=16,BROKEN=17,BEADED=18,FADE=19,RAYS=20;
    // Append new shape IDs: existing saved projects retain the original 0..20 values.
    public static final int TRIANGLE=21,OCTAGON=22,ROUNDED_SQUARE=23,TEARDROP=24,HEART=25,CAPSULE=26,ASCII=27,WORD_SCREEN=28;
    public static final int[] SHAPE_STYLES={ROUND,SQUARE,ELLIPSE,DIAMOND,HEX,STAR,CROSS,TRIANGLE,OCTAGON,ROUNDED_SQUARE,TEARDROP,HEART,CAPSULE,DASH,ASCII};
    public static boolean isShapeStyle(int style){for(int id:SHAPE_STYLES)if(id==style)return true;return style==SPIRAL;}

    public static final String[] PATTERNS={"Round dots","Square dots","Ellipses","Diamonds","Dashes","Variable-width lines","Hexagons","Spiral dots","Wavy lines","Engraving hatches","Crosses","Stars", "Straight lines","Contour lines","Crosshatch","Concentric rings","Zigzag lines","Broken lines","Beaded lines","Fade lines","Radial rays", "Triangles","Octagons","Rounded squares","Teardrops","Hearts","Capsules","ASCII screen","Word portrait"};
    public static final int[] START_COLORS={0xff22242b,0xffd65b90,0xff20a5bb,0xffe0b746,0xff435c83,0xffb65c46,0xff6b9471,0xffae8bba};
    public static final class Layer {
        public String name; public int color; public boolean enabled=true;
        /** The user deliberately omits this physical SVG sheet. It does NOT erase the
         *  source contours or modify other colors. Independent of mounting-surface color. */
        public boolean excludeFromExport=false;
        /** Original CMYK separation identity, independent of vinyl application/stack order.
         * -1 means this is a custom-color spot-vinyl layer.
         */
        public int cmykChannel=-1;
        /** Explicit real vinyl base, NEVER a CMYK/spot separation. */
        public boolean silhouetteBase=false,autoDisabledForBase=false;
        public float angle=45f,spacing=3f,density=1f,contrast=1f,strength=1f;
        /** -1 inherits artwork style; non-negative overrides with an independent line family. */
        public int lineStyleOverride=-1;
        /** Physical width scale, tone response and small angle correction for this vinyl color. */
        public float lineWidthScale=1f,lineGammaOverride=0f,angleFineTuneDeg=0f;
        public float[] curve={0f,.25f,.5f,.75f,1f};
        public Layer(String n,int c){name=n;color=c;}
        public Layer copy(){Layer l=new Layer(name,color);l.enabled=enabled;l.excludeFromExport=excludeFromExport;l.cmykChannel=cmykChannel;l.silhouetteBase=silhouetteBase;l.autoDisabledForBase=autoDisabledForBase;l.angle=angle;l.spacing=spacing;l.density=density;l.contrast=contrast;l.strength=strength;
            l.lineStyleOverride=lineStyleOverride;l.lineWidthScale=lineWidthScale;l.lineGammaOverride=lineGammaOverride;l.angleFineTuneDeg=angleFineTuneDeg;
            l.curve=curve.clone();return l;}
    }
    /** 0.010 in is the UI slider floor; actual cutter/material limits may be higher. */
    public static final float MINIMUM_CUT_WIDTH_MM = 0.254f;
    /** Default mounting surface and separation background (warm white). */
    public static final int DEFAULT_SURFACE=0xfffaf9f6;
    public static final class Settings {
        public ArrayList<Layer> layers=new ArrayList<>();
        public float widthMm=152.4f,marginMm=5,outerSpacingMm=8f,minimumMm=.8f,bridgeMm=.5f,registrationClearanceMm=1f;
        public int mode=SPOT,dither=BAYER,pattern=LINE,paper=DEFAULT_SURFACE,markStyle=0;
        /** Separation background: the color tone separation treats as "no vinyl" (transparent
         *  pixels, spot ownership, CMYK white balance, stencil quantization). Deliberately NOT the
         *  mounting surface ({@link #paper}), which is display-only, so changing the surface never
         *  changes cut geometry (D5). Projects saved without this field inherit their saved paper. */
        public int separationBackground=DEFAULT_SURFACE;
        /** Per-render Oklab spot owner, never persisted or shared between projects. */
        private transient PhotoSwatchAssociation spotMatcher;
        /** Diameter in millimeters, identical on all vinyl export layers. */
        public float markSizeMm=RegistrationMarks.DEFAULT_DIAMETER_MM;
        /** -1 means a user-customized palette; 7 is neutral grayscale. */
        public int paletteId=-1;
        /** Input levels apply after snips in a temporary raster, before cut-path generation. */
        public int levelsBlack=0,levelsWhite=255;
        public float levelsGamma=1f,levelsShadows=0f,levelsHighlights=0f,edgePriority=.55f;
        /** Non-destructive lighting controls in Prepare → Light. */
        public float exposureStops=0f,photoContrast=1f;
        /** Stencil-only removal of isolated raster palette speckles. */
        public int stencilCleanup=1;
        /** Stencil contour corner rounding in cell fractions. Zero keeps exact segmented edges. */
        public float stencilSmoothing=.10f;
        /** If true, the paper replaces the lightest/nearest background color. */
        public boolean stencilPaperBackground=true;
        public boolean registration=true;
        /** Skip physical vinyl when its color is supplied by the mounting substrate. */
        public boolean substrateAware=true;
        /** Maximum Euclidean RGB distance between vinyl and surface; 0 means exact only. */
        public int substrateTolerance=24;
        /** Crop-to-circle constraints follow the artwork and all four vinyl layers. */
        public boolean circleCrop=false;
        /** Selected Cricut mat ("12x12" or "12x24"); null = legacy project with no selection yet.
         *  Never changes geometry: exports that do not fit the selected mat are rejected. */
        public String cricutMat=null;
        /** M5 saved "markLayout": RegistrationMarks.PLACEMENT_CORNERS (default) or PLACEMENT_ABOVE_BELOW. */
        public int markLayout=RegistrationMarks.PLACEMENT_CORNERS;
        /** 0: Empty/transparent; 1: opaque white; 2: opaque black in snipped regions. */
        public int snipFillMode=0;
        /** Leave more underlying chromatic vinyl visible beneath the top black layer. */
        public float blackColorProtection=CmykPortraitDefaults.BLACK_COLOR_PROTECTION;
        /** 0 = legacy maximum-black GCR; 1 = zero under-color removal.
         * For opaque vinyl this is a creative shadow-colour recovery control,
         * not an ICC-managed printing profile. It never adds cuts to paper-white pixels.
         */
        public float shadowColorRecovery=CmykPortraitDefaults.SHADOW_COLOR_RECOVERY;
        /** Restores restrained midtone lines in portraits without lowering cutter minimums. */
        public float portraitMidtones=CmykPortraitDefaults.PORTRAIT_MIDTONES;
        /** Global settings are applied to all layers by default. Layer angles may follow a preset. */
        public boolean linkedControls=true;
        public float masterSpacingMm=2.54f,masterDensity=1f,masterContrast=1f,screenRotationDeg=0f;
        public int anglePreset=ANGLES_CLASSIC;
        /** Variable-width line ribbon controls. */
        public float lineMaxFraction=.85f,lineGamma=1.0f,lineSmoothing=.38f,lineSampleMm=.45f;
        /** Bounded one-sample near-threshold continuity. Zero is pixel-exact legacy. */
        public float lineContinuity=CmykPortraitDefaults.LINE_CONTINUITY;
        /** Explicitly gated 1-D connected tonal screening (0 leaves all legacy SVGs unchanged). */
        public float linePresenceRecovery=.55f;
        /** New-style CMYK line screens compensate for opaque physical stacking. */
        public boolean opaqueLineBalance=false;
        /** Optional subject-supported minimum-width lines in sustained light portrait tones. 0 is legacy. */
        public float lineHighlightRetention=0f;
        /** New CMYK line projects use the proven v2.24 separation and ribbons.
         * Projects saved before this release default to the newer engine.
         * This does not affect non-line styles or manually edited vinyl colors.
         */
        public boolean classicCmykLines=true;
        /** Conservative low-texture outer-background attenuation for line portraits. */
        public float portraitBackgroundGuard=.55f;
        public boolean lineInvert=false;
        /** How much continuous portrait lines bend toward local photo structure, 0=straight. */
        public float contourStrength=.90f;
        /** Add fine, shorter ribbons only where the source photograph has local detail. */
        public float detailBoost=.32f;
        /** 0..1: increases ribbon width around existing tonal/feature edges, strongest on black. */
        public float shapeDefinition=.72f;
        /** Extra pass angle for crosshatch; independent from CMYK screen orientation. */
        public float crossAngleDeg=60f;
        /** Relative focal point used for rings and rays, in normalized artwork coordinates. */
        public float radialFocusX=.50f,radialFocusY=.35f;
        /** Amplitude is a fraction of screen spacing; constrained below half the gap. */
        public float waveAmplitude=.14f,waveCyclesPerInch=1.6f;
        /** Fraction of dark coverage needed before secondary engraving strokes appear. */
        public float hatchThreshold=.70f;
        /** Dot shape scale; keep individual shapes inset from cell boundaries. */
        public float shapeScale=.88f;
        /** Word-screen motif text. Serialized independently of legacy TEXT style. */
        public String screenWords="VINYL STUDIO";
        // Positions measured from top-left of artwork, NOT from padded SVG artboard.
        public float[][] marks=null;
        /** Shared nondestructive polygon snips, applied before all separations. */
        public SnipMask snips=new SnipMask();
        public static Settings defaults(){Settings s=new Settings();s.mode=CMYK;
            int[] colors={0xffffdd00,0xff00aeef,0xffec008c,0xff131313};
            String[] names={"Yellow","Cyan","Magenta","Black"};
            int[] channels={2,0,1,3};
            float[] angles={0,15,75,45};
            for(int i=0;i<4;i++){if(((i)&63)==0)CancellationCheckpoint.check();Layer l=new Layer(names[i],colors[i]);l.cmykChannel=channels[i];l.angle=angles[i];l.strength=1f;l.spacing=s.masterSpacingMm;s.layers.add(l);}
            ClassicCmykLines.apply(s);
            return s;}
        public Settings copy(){Settings s=new Settings();s.separationBackground=separationBackground;s.widthMm=widthMm;s.marginMm=marginMm;s.outerSpacingMm=outerSpacingMm;s.minimumMm=minimumMm;s.bridgeMm=bridgeMm;s.registrationClearanceMm=registrationClearanceMm;s.mode=mode;s.dither=dither;s.pattern=pattern;s.paper=paper;s.stencilCleanup=stencilCleanup;s.stencilSmoothing=stencilSmoothing;s.stencilPaperBackground=stencilPaperBackground;s.markStyle=markStyle;s.markSizeMm=markSizeMm;s.registration=registration;s.substrateAware=substrateAware;s.substrateTolerance=substrateTolerance;s.circleCrop=circleCrop;s.cricutMat=cricutMat;s.markLayout=markLayout;s.snipFillMode=snipFillMode;
            s.paletteId=paletteId;s.exposureStops=exposureStops;s.photoContrast=photoContrast;s.levelsBlack=levelsBlack;s.levelsWhite=levelsWhite;s.levelsGamma=levelsGamma;s.levelsShadows=levelsShadows;s.levelsHighlights=levelsHighlights;s.edgePriority=edgePriority;s.blackColorProtection=blackColorProtection;s.shadowColorRecovery=shadowColorRecovery;s.portraitMidtones=portraitMidtones;s.linkedControls=linkedControls;s.masterSpacingMm=masterSpacingMm;s.masterDensity=masterDensity;s.masterContrast=masterContrast;
            s.screenRotationDeg=screenRotationDeg;s.anglePreset=anglePreset;
            s.lineMaxFraction=lineMaxFraction;s.lineGamma=lineGamma;s.lineSmoothing=lineSmoothing;s.lineSampleMm=lineSampleMm;s.lineContinuity=lineContinuity;s.linePresenceRecovery=linePresenceRecovery;s.opaqueLineBalance=opaqueLineBalance;s.lineHighlightRetention=lineHighlightRetention;s.classicCmykLines=classicCmykLines;s.portraitBackgroundGuard=portraitBackgroundGuard;s.lineInvert=lineInvert;s.contourStrength=contourStrength;s.detailBoost=detailBoost;s.shapeDefinition=shapeDefinition;s.crossAngleDeg=crossAngleDeg;
            s.radialFocusX=radialFocusX;s.radialFocusY=radialFocusY;s.waveAmplitude=waveAmplitude;s.waveCyclesPerInch=waveCyclesPerInch;s.hatchThreshold=hatchThreshold;s.shapeScale=shapeScale;s.screenWords=screenWords;s.snips=snips==null?new SnipMask():snips.copy();
            for(Layer l:layers)s.layers.add(l.copy());if(marks!=null){s.marks=new float[marks.length][];for(int i=0;i<marks.length;i++)s.marks[i]=marks[i].clone();}return s;}
    }
    public static final class Shape {
        public final float[] xy;public final float x,y,r;public final boolean circle;
        /** One filled region may include inner contours (paper openings); all rings are physical cut paths. */
        public final float[][] contours;
        private Shape(float x,float y,float r,float[] coords,float[][] rings){this.x=x;this.y=y;this.r=r;circle=coords==null;xy=coords;contours=rings;}
        public static Shape circle(float x,float y,float r){return new Shape(x,y,r,null,null);}
        public static Shape polygon(float... xy){return new Shape(0,0,0,xy,null);}
        public static Shape compound(float[][] rings){
            if(rings==null||rings.length==0||rings[0]==null||rings[0].length<6)throw new IllegalArgumentException("No exterior ring");
            for(float[] ring:rings)if(ring==null||ring.length<6||(ring.length&1)!=0)throw new IllegalArgumentException("Invalid contour");
            return new Shape(0,0,0,rings[0],rings);
        }
        public int vertexCount(){if(circle)return 12;if(contours==null)return xy.length/2;int n=0;for(float[] ring:contours)n+=ring.length/2;return n;}
        public float[] bounds(){if(circle)return new float[]{x-r,y-r,x+r,y+r};float left=Float.POSITIVE_INFINITY,top=left,right=Float.NEGATIVE_INFINITY,bottom=right;
            float[][] rings=contours==null?new float[][]{xy}:contours;
            for(float[] ring:rings)for(int i=0;i<ring.length;i+=2){if(((i)&63)==0)CancellationCheckpoint.check();left=Math.min(left,ring[i]);right=Math.max(right,ring[i]);top=Math.min(top,ring[i+1]);bottom=Math.max(bottom,ring[i+1]);}return new float[]{left,top,right,bottom};}
    }
    public static final class Result {
        public final Settings settings;public final float heightMm,padMm,boardW,boardH;
        public final ArrayList<List<Shape>> shapes=new ArrayList<>();
        public final float[][] marks;
        public final ArrayList<LinePresenceTracker.Metrics> lineMetrics=new ArrayList<>();
        /** Shared immutable image analysis for this render; never saved into projects. */
        ScaleAwarePhotoSampler photoSampler;
        /** Transient source-sized tonal factors; released before preview/export. */
        float[] opaqueLineScales,opaqueLineDetails;
        boolean opaqueLineHasAlphaCutout;
        int opaqueLineImageWidth,opaqueLineImageHeight;
        Result(Settings s,float h,float pad,float[][] m){settings=s;heightMm=h;padMm=pad;boardW=s.widthMm+2*pad;boardH=h+2*pad;marks=m;}
        public int count(){int n=0;for(List<Shape> l:shapes)n+=l.size();return n;}
    }
    /** Automatic color screen angles are stable under Y/C/M/K reorder and added spot colors.
     * Classic CMYK roles remain Y=0 C=15 M=75 K=45 independent of application order.
     * The additional angles are engineering presets, not guaranteed moire-free combinations.
     */
    public static void applyAnglePreset(Settings s){
        if(s.anglePreset==ANGLES_CUSTOM)return;
        // Parallel is obsolete for screened color vinyl. Old saved projects are repaired.
        if(s.anglePreset==ANGLES_PARALLEL)s.anglePreset=ANGLES_CLASSIC;
        final float[] classic={15f,75f,0f,45f};
        final float[] photoshop={108f,162f,90f,45f};
        // Screen symmetry differs by shape, so an angle alone cannot guarantee no moire.
        // These four fallback slots are separated from CMYK anchors for an 8-color palette.
        final float[] extra={30f,60f,7.5f,82.5f};
        boolean[] represented=new boolean[4];
        for(Layer l:s.layers)if(l.cmykChannel>=0&&l.cmykChannel<4)represented[l.cmykChannel]=true;
        int customIndex=0;
        for(Layer l:s.layers){CancellationCheckpoint.check();
            float angle;
            if(l.cmykChannel>=0&&l.cmykChannel<4){
                angle=s.anglePreset==ANGLES_PHOTOSHOP?photoshop[l.cmykChannel]:classic[l.cmykChannel];
            }else if(s.anglePreset==ANGLES_FAN){
                angle=15f+(customIndex++*150f/Math.max(1,s.layers.size()-1));
            }else if(!represented[0]&&!represented[1]&&!represented[2]&&!represented[3]&&customIndex<4){
                // Pure spot-color projects use the established four-role cycle first.
                angle=classic[customIndex++];
            }else{
                angle=extra[(customIndex++)%extra.length];
            }
            l.angle=((angle+s.screenRotationDeg+l.angleFineTuneDeg)%180f+180f)%180f;
        }
    }
    public static float curve(float v,float[] p){v=clamp(v);float pos=v*4;int k=Math.min(3,(int)pos);float f=pos-k;return clamp(p[k]+(p[k+1]-p[k])*f);}
    private static float clamp(float x){return Math.max(0,Math.min(1,x));}
    private static float brightness(int c){return (float)(.2126*((c>>16)&255)+.7152*((c>>8)&255)+.0722*(c&255))/255f;}
    /** Weighted RGB distance; foreground brightness perceptually weighted. */
    private static float dist(int a,int b){int ar=(a>>16)&255,ag=(a>>8)&255,ab=a&255,br=(b>>16)&255,bg=(b>>8)&255,bb=b&255;int dr=ar-br,dg=ag-bg,db=ab-bb;return 2.2f*dr*dr+4.0f*dg*dg+1.6f*db*db;}
    private static int color(int r,int g,int b){return 0xff000000|(Math.min(255,Math.max(0,r))<<16)|(Math.min(255,Math.max(0,g))<<8)|Math.min(255,Math.max(0,b));}
    static int at(int[] pix,int w,int h,float x,float y,float widthMm,float heightMm){int ix=Math.max(0,Math.min(w-1,(int)(x/widthMm*w))),iy=Math.max(0,Math.min(h-1,(int)(y/heightMm*h)));return pix[iy*w+ix];}
    private static int flatten(int pixel,int paper){int alpha=(pixel>>>24)&255;if(alpha==255)return pixel;if(alpha==0)return paper;int r=(((pixel>>16)&255)*alpha+((paper>>16)&255)*(255-alpha))/255;int g=(((pixel>>8)&255)*alpha+((paper>>8)&255)*(255-alpha))/255;int b=((pixel&255)*alpha+(paper&255)*(255-alpha))/255;return color(r,g,b);}
    private static float brightnessAt(int[] pix,int w,int h,float x,float y,float widthMm,float heightMm,int paper){return brightness(flatten(at(pix,w,h,x,y,widthMm,heightMm),paper));}
    /** Low-pass image-gradient displacement in the normal direction of a line.
     * Uses several neighboring luminance samples to resist noisy zigzags.
     * Bending is applied equally in direction-independent image coordinates:
     * no CMYK layer is forced to coincide with another layer.
     */
    private static float contourShift(int[] src,int w,int h,Settings s,float heightMm,float x,float y,
                                      float nx,float ny,float tx,float ty,float spacing){
        if(s.contourStrength<=0)return 0;
        float probe=Math.max(.25f,Math.min(spacing*.65f,1.8f));
        float along=probe*.9f;
        float gradient=0;
        for(int k=-1;k<=1;k++){if(((k)&63)==0)CancellationCheckpoint.check();
            float bx=x+tx*along*k,by=y+ty*along*k;
            float plus=brightnessAt(src,w,h,bx+nx*probe,by+ny*probe,s.widthMm,heightMm,s.separationBackground);
            float minus=brightnessAt(src,w,h,bx-nx*probe,by-ny*probe,s.widthMm,heightMm,s.separationBackground);
            gradient+=(minus-plus)*(k==0?.50f:.25f);
        }
        // A bounded gentle drift near edges; neutral regions stay straight.
        float maxShift=spacing*.16f*s.contourStrength;
        float shift=gradient*spacing*.55f*s.contourStrength;
        return Math.max(-maxShift,Math.min(maxShift,shift));
    }
    static float coverage(int pixel,Settings s,int layer){pixel=flatten(pixel,s.separationBackground);Layer l=s.layers.get(layer);float raw;
        if(s.mode==CMYK&&(l.cmykChannel>=0||s.layers.size()==4)){int channel=l.cmykChannel>=0?l.cmykChannel:layer;float r=((pixel>>16)&255)/255f,g=((pixel>>8)&255)/255f,b=(pixel&255)/255f;float k=1-Math.max(r,Math.max(g,b));if(channel==3){
                float max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b));
                float saturation=(max-min)/Math.max(.08f,max);
                raw=k*(1f-s.blackColorProtection*clamp(saturation));
            }else {
                // The prior full-GCR conversion used all shared neutral darkness as black.
                // When red was dominant (as in brown hair and warm portraits), cyan was
                // identically zero: raising cyan strength could NEVER recover it.
                // Preserve a controlled fraction of dark neutral components in CMY
                // to produce independently cuttable shadow colors on opaque vinyl.
                float extractedK=k*(1f-s.shadowColorRecovery);
                float component=channel==0?r:channel==1?g:b;
                raw=extractedK>.999f?0:clamp((1f-component-extractedK)/(1f-extractedK));
                float chroma=Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b));
                // Opaque vinyl overlays obscure underlying work. In nearly
                // neutral shadow pixels reserve more of the structure for K.
                float neutralShare=clamp((.24f-chroma)/.24f);
                raw*=1f-.30f*neutralShare;
            }}
        else{float own=dist(pixel,l.color),paper=dist(pixel,s.separationBackground);float best=paper;for(Layer other:s.layers)if(other.enabled)best=Math.min(best,dist(pixel,other.color));
            // soft spot separations deliberately permit overlay, unlike mosaic assignment
            raw=clamp((paper-own+30000f)/110000f);
            // Keep established spot coverage/line continuity; only replace the
            // competing-color decision with perceptual swatch ownership.
            // Disabled sheets and substrate remain correctly excluded/included.
            boolean wrongOwner=s.spotMatcher==null?own>best:
                s.spotMatcher.chooseCached(pixel)!=layer;
            if(wrongOwner)raw*=.52f;
            raw*=.35f+.65f*clamp(1f-brightness(pixel)+brightness(l.color)*.3f);
        }
        if(s.mode==CMYK&&(l.cmykChannel>=0||s.layers.size()==4)
                && !(s.classicCmykLines && l.cmykChannel>=0 &&
                    LineArtEngine.isLineStyle(l.lineStyleOverride>=0?l.lineStyleOverride:s.pattern)))
            raw=CmykWhitePaperBalance.adjust(raw,pixel,l.cmykChannel>=0?l.cmykChannel:layer,s.separationBackground);
        raw=clamp((raw-.5f)*l.contrast+.5f);return clamp(curve(raw,l.curve)*l.density*l.strength);
    }
    public static Result generate(int[] pixels,int w,int h,Settings settings){
        if(pixels==null||w<1||h<1||pixels.length!=w*h)throw new IllegalArgumentException("Invalid source image");
        Settings s=settings.copy();
        // Repair old aligned CMYK projects in the render snapshot as well.
        // Import/preset paths repair the editor state separately.
        if(s.mode==CMYK&&s.anglePreset==ANGLES_PARALLEL)applyAnglePreset(s);
        // CMYK base channels retain their separations for all 4–8 physical sheets.
        // Additional vinyl accents use the existing spot coverage logic and sit below K.
        if(s.mode==CMYK)CmykExtendedStack.canonicalize(s.layers);
        if(s.linkedControls){for(Layer l:s.layers){CancellationCheckpoint.check();l.spacing=s.masterSpacingMm;l.density=s.masterDensity;l.contrast=s.masterContrast;}}
        if(s.anglePreset!=ANGLES_CUSTOM)applyAnglePreset(s);
        if(s.layers.size()<2||s.layers.size()>8)throw new IllegalArgumentException("Choose 2–8 vinyl colors");
        if(s.mode==SPOT){
            // Construct a private render-snapshot classifier. CMYK separations
            // and classic connected line engines must keep their tone algorithms.
            int[] swatches=new int[s.layers.size()+1];boolean[] active=new boolean[swatches.length];
            for(int i=0;i<s.layers.size();i++){
                swatches[i]=s.layers.get(i).color;active[i]=s.layers.get(i).enabled;
            }
            swatches[s.layers.size()]=s.separationBackground;active[s.layers.size()]=true;
            s.spotMatcher=new PhotoSwatchAssociation(swatches,swatches.length,active,
                (int[])null,null,PhotoSwatchAssociation.SHAPES);
        }
        if(!(s.widthMm>=10&&s.widthMm<=1000&&s.minimumMm>.05&&Float.isFinite(s.marginMm)&&s.marginMm>=0&&s.marginMm<s.widthMm*.49f&&Float.isFinite(s.outerSpacingMm)&&s.outerSpacingMm>=0&&s.outerSpacingMm<=60
            &&s.lineMaxFraction>.05&&s.lineMaxFraction<=.98&&s.lineGamma>=.2&&s.lineGamma<=4
            &&s.lineSmoothing>=0&&s.lineSmoothing<=.98&&s.lineSampleMm>=.1&&s.lineSampleMm<=2&&s.contourStrength>=0&&s.contourStrength<=1
            &&s.detailBoost>=0&&s.detailBoost<=1&&s.crossAngleDeg>=25&&s.crossAngleDeg<=90
            &&s.radialFocusX>=0&&s.radialFocusX<=1&&s.radialFocusY>=0&&s.radialFocusY<=1
            &&s.exposureStops>=-1.5f&&s.exposureStops<=1.5f&&s.photoContrast>=.5f&&s.photoContrast<=1.8f&&s.levelsBlack>=0&&s.levelsWhite<=255&&s.levelsBlack<s.levelsWhite&&s.levelsGamma>=.3f&&s.levelsGamma<=3f&&s.levelsShadows>=-1&&s.levelsShadows<=1&&s.levelsHighlights>=-1&&s.levelsHighlights<=1&&s.edgePriority>=0&&s.edgePriority<=1
            &&s.waveAmplitude>=0&&s.waveAmplitude<=.23f&&s.waveCyclesPerInch>=.1f&&s.waveCyclesPerInch<=8
            &&s.stencilSmoothing>=0&&s.stencilSmoothing<=.25f&&s.blackColorProtection>=0&&s.blackColorProtection<=.8f&&s.shadowColorRecovery>=0&&s.shadowColorRecovery<=.85f&&s.portraitMidtones>=0&&s.portraitMidtones<=1&&s.lineContinuity>=0&&s.lineContinuity<=1&&s.linePresenceRecovery>=0&&s.linePresenceRecovery<=1&&Float.isFinite(s.lineHighlightRetention)&&s.lineHighlightRetention>=0&&s.lineHighlightRetention<=1&&s.portraitBackgroundGuard>=0&&s.portraitBackgroundGuard<=1&&s.hatchThreshold>=.3f&&s.hatchThreshold<=.95f&&s.shapeScale>=.3f&&s.shapeScale<=1f))throw new IllegalArgumentException("Invalid physical dimensions");
        for(Layer layer:s.layers){CancellationCheckpoint.check();
            if(layer.lineWidthScale<.3f||layer.lineWidthScale>1.8f||layer.lineGammaOverride<0||layer.lineGammaOverride>4f||
                layer.angleFineTuneDeg < -12f || layer.angleFineTuneDeg > 12f ||
                (layer.lineStyleOverride>=PATTERNS.length))
                throw new IllegalArgumentException("Invalid per-vinyl line settings");
        }
        float hh=s.widthMm*h/w;if(hh<1||hh>1500||s.marginMm>=hh*.49f)throw new IllegalArgumentException("Unsupported artwork proportions");
        RegistrationMarks.checkedSize(s.markSizeMm);
        if(!RegistrationMarks.validStyle(s.markStyle))throw new IllegalArgumentException("Invalid registration shape");
        // ADR-0001: the same frame (margin + marks) as every other sheet of the export.
        SheetFrame frame=SheetFrame.of(s.widthMm,hh,s.registration,s.markSizeMm,s.registrationClearanceMm,s.marks,s.outerSpacingMm,s.circleCrop);
        float[][] marks=frame.registration()?frame.marks():
            RegistrationMarks.automaticPositions(s.widthMm,hh,s.markSizeMm,s.registrationClearanceMm,s.circleCrop);
        float pad=frame.paddingMm;
        if(s.registration&&s.minimumMm>=s.markSizeMm)throw new IllegalArgumentException("Increase registration mark size above the minimum vinyl cut width");
        Result result=new Result(s,hh,pad,marks);
        result.photoSampler=new ScaleAwarePhotoSampler(pixels,w,h,s.widthMm,hh,s.separationBackground);
        checkGenerationCancelled();
        int selectedStyle=s.pattern;
        if(OpaqueCmykLineBudget.eligible(s,selectedStyle)) {
            // Crop transparency is authoritative where present: don't apply
            // a fixed oval/warm-skin test to an already-masked subject.
            for(int sourcePixel:pixels)if((sourcePixel>>>24)<128){
                result.opaqueLineHasAlphaCutout=true;break;
            }
            result.opaqueLineScales=OpaqueCmykLineBudget.prepare(pixels,w,h,s);
            result.opaqueLineDetails=OpaqueCmykLineBudget.localDetail(pixels,w,h,s);
            result.opaqueLineImageWidth=w;result.opaqueLineImageHeight=h;
        }
        for(int i=0;i<s.layers.size();i++){if(((i)&63)==0)CancellationCheckpoint.check();result.shapes.add(new ArrayList<>());result.lineMetrics.add(new LinePresenceTracker.Metrics());}
        if(s.mode==STENCIL)StencilSeparationEngine.generate(result,pixels,w,h);
        else if(s.mode==MOSAIC)generateMosaic(result,pixels,w,h);
        else{
            // Layers are independent: each reads the shared immutable raster and
            // writes only result.shapes.get(i)/lineMetrics.get(i).
            int enabled=0;for(Layer l:s.layers)if(l.enabled)enabled++;
            int[] order=new int[enabled];
            for(int i=0,k=0;i<s.layers.size();i++)if(s.layers.get(i).enabled)order[k++]=i;
            final Result out=result;
            LayerParallelism.run(order,i->{
                checkGenerationCancelled();
                int style=s.layers.get(i).lineStyleOverride<0?s.pattern:s.layers.get(i).lineStyleOverride;
                if(s.layers.get(i).silhouetteBase)SolidSnipBase.generate(out,pixels,w,h,i);
                else if(LineArtEngine.isLineStyle(style))LineArtEngine.generate(out,pixels,w,h,i,style);
                else if(style==WORD_SCREEN)WordScreenMotif.generate(out,pixels,w,h,i);
                else generateDots(out,pixels,w,h,i,style);
            });
        }
        result.opaqueLineScales=null;result.opaqueLineDetails=null; // temporary screen buffers, not retained
        return result;
    }
    private static void checkGenerationCancelled(){
        if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Cancelled obsolete preview generation");
    }
    public static float[][] defaultMarks(float w,float h){return RegistrationMarks.defaultPositions(w,h,RegistrationMarks.DEFAULT_DIAMETER_MM,1f);}
    /** This physical no-cut inset follows the crop geometry; actual SVG shapes are filtered,
     * never masked using an unsupported SVG clipPath. Conservatively omit whole boundary-touching shapes.
     */
    public static boolean insideCutBoundary(Shape shape,float widthMm,float heightMm,float insetMm,boolean circleCrop){
        if(!Float.isFinite(insetMm)||insetMm<0||insetMm*2>=Math.min(widthMm,heightMm))return false;
        float[] bounds=shape.bounds();
        if(bounds[0]<insetMm-0.0001f||bounds[1]<insetMm-0.0001f||
           bounds[2]>widthMm-insetMm+0.0001f||bounds[3]>heightMm-insetMm+0.0001f)return false;
        if(!circleCrop)return true;
        float cx=widthMm*.5f,cy=heightMm*.5f,rx=cx-insetMm,ry=cy-insetMm;
        if(shape.circle){
            // Conservative clearance for circular cut shapes inside an oval/circle crop.
            return Math.hypot((shape.x-cx)/rx,(shape.y-cy)/ry)+shape.r/Math.min(rx,ry)<=1.000001;
        }
        float[][] rings=shape.contours==null?new float[][]{shape.xy}:shape.contours;
        for(float[] ring:rings)for(int i=0;i<ring.length;i+=2){if(((i)&63)==0)CancellationCheckpoint.check();
            double x=(ring[i]-cx)/rx,y=(ring[i+1]-cy)/ry;
            if(x*x+y*y>1.000001)return false;
        }
        return true;
    }
    static void add(Result r,int layer,Shape shape){
        if(!shape.circle){
            // A zero-area or collapsed ring is not a cut path; it would make
            // the export inspector reject the whole sheet.
            float[][] rings=shape.contours==null?new float[][]{shape.xy}:shape.contours;
            java.util.ArrayList<float[]> kept=new java.util.ArrayList<>(rings.length);
            for(float[] ring:rings)if(!degenerateRing(ring))kept.add(ring);
            if(kept.isEmpty()||degenerateRing(rings[0]))return;
            if(kept.size()!=rings.length)shape=Shape.compound(kept.toArray(new float[0][]));
            // A piece narrower than the cut minimum (a two-sample ribbon stub,
            // a clipped sliver) can be neither cut nor weeded. This mirrors the
            // export inspector's narrow/tiny-piece rule, so it never removes a
            // piece that inspection would accept.
            if(tinyPiece(rings[0],Math.max(r.settings.minimumMm,.2f)))return;
        }
        if(!insideCutBoundary(shape,r.settings.widthMm,r.heightMm,r.settings.marginMm,r.settings.circleCrop))return;
        if(r.settings.snips!=null&&!r.settings.snips.isEmpty()){
            boolean allowed=shape.circle?r.settings.snips.allowsCircle(shape.x,shape.y,shape.r,r.settings.widthMm,r.heightMm)
                :true;
            if(!shape.circle){
                float[][] rings=shape.contours==null?new float[][]{shape.xy}:shape.contours;
                for(float[] ring:rings)if(!r.settings.snips.allowsPolygon(ring,r.settings.widthMm,r.heightMm)){
                    allowed=false;break;
                }
            }
            if(!allowed)return;
        }
        List<Shape> list=r.shapes.get(layer);
        if(list.size()>=4990)throw new IllegalArgumentException("Layer "+(layer+1)+" exceeds the Cricut 5,000-shape SVG limit. Increase spacing or simplify the image.");
        list.add(shape);
    }
    static boolean tinyPiece(float[] ring,float min){
        float lx=Float.POSITIVE_INFINITY,ly=lx,hx=Float.NEGATIVE_INFINITY,hy=hx;
        for(int k=0;k+1<ring.length;k+=2){lx=Math.min(lx,ring[k]);hx=Math.max(hx,ring[k]);ly=Math.min(ly,ring[k+1]);hy=Math.max(hy,ring[k+1]);}
        float width=Math.min(hx-lx,hy-ly);
        int n=ring.length/2;
        if(n<=16){
            for(int i=0;i<n;i++){
                int j=(i+1)%n;double dx=ring[2*j]-ring[2*i],dy=ring[2*j+1]-ring[2*i+1],len=Math.hypot(dx,dy);
                if(len<1e-9)continue;
                double nx=-dy/len,ny=dx/len,lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
                for(int k=0;k<n;k++){double pr=nx*ring[2*k]+ny*ring[2*k+1];lo=Math.min(lo,pr);hi=Math.max(hi,pr);}
                width=Math.min(width,(float)(hi-lo));
            }
        }
        return width+.003f<min||Math.abs(StyleKit.signedArea(ring))<min*min*.3f;
    }
    static boolean degenerateRing(float[] ring){
        if(ring==null||ring.length<6)return true;
        float lx=Float.POSITIVE_INFINITY,ly=lx,hx=Float.NEGATIVE_INFINITY,hy=hx;
        for(int k=0;k+1<ring.length;k+=2){lx=Math.min(lx,ring[k]);hx=Math.max(hx,ring[k]);ly=Math.min(ly,ring[k+1]);hy=Math.max(hy,ring[k+1]);}
        return hx-lx<.0005f||hy-ly<.0005f||Math.abs(StyleKit.signedArea(ring))<1e-6;
    }
    /** Narrowest projected width of each motif as a fraction of 2*radius. */
    static float narrowestWidthFraction(int style){
        switch(style){
            case ELLIPSE:return .65f;
            case DASH:return .80f; // thickness is clamped to the minimum below
            case CAPSULE:return .55f;
            case DIAMOND:case ROUNDED_SQUARE:return .7071f;
            case HEX:return .866f;
            case OCTAGON:return .9239f;
            case TRIANGLE:return .75f;
            case TEARDROP:return .80f;
            default:return 1f;
        }
    }
    private static boolean in(float x,float y,float w,float h,float margin){return x>=margin&&x<=w-margin&&y>=margin&&y<=h-margin;}
    private static void generateDots(Result r,int[] src,int w,int h,int li,int style){
        final ScaleAwarePhotoSampler.Coverage shade=p->coverage(p,r.settings,li);
        Settings s=r.settings;Layer layer=s.layers.get(li);
        final AsciiScreenMotif ascii=style==ASCII?new AsciiScreenMotif():null;
        int asciiContours=0;
        float sp=Math.max(.5f,layer.spacing),min=Math.max(s.minimumMm,.2f);
        // Shapes return to the original regular CMYK-separated grid.
        // Wavy Lines has an independent ribbon generator and is unaffected.
        float lim=sp-s.bridgeMm;
        if(style==STAR&&ShapeAreaResponse.starSafeRadius(min)>lim*s.shapeScale*.5f+1e-5f)
            throw new IllegalArgumentException("Stars cannot meet the requested cut width at this spacing. Increase Spacing or Shape Size.");
        double a=Math.toRadians(layer.angle),ca=Math.cos(a),sa=Math.sin(a);
        float extent=(float)Math.hypot(s.widthMm,r.heightMm)/2+sp*2;
        int steps=(int)Math.ceil(extent/sp);
        if((long)(2*steps+1)*(2*steps+1)>600000L)
            throw new IllegalArgumentException("Too many grid samples; increase spacing or reduce artwork size");
        for(int row=-steps;row<=steps;row++){if(((row)&63)==0)CancellationCheckpoint.check();
            checkGenerationCancelled();
            float asciiCarry=0f; // bounded, channel-local residual, reset at each screen row
            int cancelTick1=0;
            for(int col=-steps;col<=steps;col++){if((++cancelTick1&63)==0)CancellationCheckpoint.check();
            float u=col*sp,v=row*sp;
            if(style==HEX&&(row&1)!=0)u+=sp*.5f;
            float x=(float)(s.widthMm/2+u*ca-v*sa);
            float y=(float)(r.heightMm/2+u*sa+v*ca);
            if(style==SPIRAL){float radial=(float)Math.hypot(u,v);
                double rot=radial/sp*.22f+Math.toRadians(layer.angle);
                x=(float)(s.widthMm/2+u*Math.cos(rot)-v*Math.sin(rot));
                y=(float)(r.heightMm/2+u*Math.sin(rot)+v*Math.cos(rot));}
            if(!in(x,y,s.widthMm,r.heightMm,s.marginMm+sp/2)){if(style==ASCII)asciiCarry=0f;continue;}
            if(s.snips!=null&&!s.snips.isEmpty()&&!s.snips.allows(x/s.widthMm,y/r.heightMm)){if(style==ASCII)asciiCarry=0f;continue;}
            // Physical footprint sampling preserves the selected motif and center
            // color ownership while retaining thin photographic details in cells.
            float weight=r.photoSampler.detailPreservingMotifCoverage(x,y,Math.min(lim*.5f,sp*.43f),shade);
            float diameter=(float)(sp*Math.sqrt(weight));diameter=Math.min(diameter,lim)*s.shapeScale;
            if(style!=ASCII&&style!=STAR&&diameter<min)continue;
            float rad=diameter/2,orientation=layer.angle;
            // Physical area, not bounding diameter, determines photographic tone.
            // Preserve Round/Spiral and ASCII exactly; other Shapes are calibrated
            // against the ROUND screen's existing area response without enlarging
            // a motif beyond the selected Shape Size or the validated cell gap.
            final float starInner=style==STAR?ShapeAreaResponse.starInner(weight):0f;
            if(style!=ASCII&&style!=ROUND&&style!=SPIRAL){
                rad=ShapeAreaResponse.radius(style,rad,lim*s.shapeScale*.5f,starInner);
                // Judge the motif's NARROWEST physical width, not its bounding
                // diameter: an ellipse, dash or capsule is much thinner than it
                // is long, and a highlight motif thinner than the cut minimum
                // can neither be cut nor weeded.
                // 4% allowance: polygonal motifs are inscribed in their curves.
                if(2f*rad*narrowestWidthFraction(style)*.96f<min+.01f&&style!=STAR)continue;
                if(style==STAR){
                    // A small five-point star may have legal overall width but
                    // acute bays/tips narrower than the requested cutting width.
                    // Promote only selected motifs to a physically safer size;
                    // deterministic area dithering preserves the expected tone
                    // instead of enlarging every light-area star into a blob.
                    float safe=ShapeAreaResponse.starSafeRadius(min);
                    if(safe>lim*s.shapeScale*.5f)continue;
                    if(rad<safe){
                        float probability=(rad/safe)*(rad/safe);
                        if(!ShapeAreaResponse.keepPromotedStar(row,col,li,probability))continue;
                        rad=safe;
                    }
                }
                // Minimum material neck across concave star bays and cross arms.
                // Sharp outer tips are still subject to cutter preflight and a
                // physical scrap cut; this does not pretend a tip has finite width.
                if(style==STAR&&2f*rad*starInner<min)continue;
                if(style==CROSS&&2f*rad*.36f<min)continue;
            }
            if(style==ASCII){
                // Same CMYK/spot coverage and screen placement as regular Shapes.
                // Text replaces only the motif geometry, never the color separator.
                float probe=Math.max(sp*.35f,.55f);
                float gx=coverage(at(src,w,h,x+probe,y,s.widthMm,r.heightMm),s,li)
                        -coverage(at(src,w,h,x-probe,y,s.widthMm,r.heightMm),s,li);
                float gy=coverage(at(src,w,h,x,y+probe,s.widthMm,r.heightMm),s,li)
                        -coverage(at(src,w,h,x,y-probe,s.widthMm,r.heightMm),s,li);
                // Upright glyph bounding boxes cannot overlap on a rotated screen.
                // 1/sqrt(2) is the worst-case axis projection of grid spacing.
                float footprint=Math.min(lim,sp*.70710678f)*s.shapeScale;
                // Diffuse quantization residual only along the current physical
                // screen row, never across colors, masks or photo boundaries.
                // This recovers small legal marks when weak tones would vanish.
                float adjusted=Math.max(0f,Math.min(1f,weight+asciiCarry));
                VinylEngine.Shape glyph=ascii.choose(x,y,footprint,
                        adjusted,min,gx,gy,row,col);
                if(glyph!=null){
                    int paths=glyph.contours==null?1:glyph.contours.length;
                    if(asciiContours+paths+4<=4990){
                        int before=r.shapes.get(li).size();
                        add(r,li,glyph);
                        if(r.shapes.get(li).size()>before){
                            asciiContours+=paths;ascii.accepted(row);
                            asciiCarry=Math.max(-.25f,Math.min(.25f,
                                    (adjusted-ascii.lastCoverage())*.65f));
                        }else asciiCarry=0f;
                    }else asciiCarry=0f;
                }else asciiCarry=Math.min(.25f,(adjusted)*.65f);
                continue;
            }
            switch(style){
                case ROUND:case SPIRAL:add(r,li,Shape.circle(x,y,rad));break;
                case SQUARE:{float safe=Math.min(rad,lim*.5f/(float)Math.sqrt(2));
                    if(2*safe<min)break;
                    add(r,li,rotated(Shape.polygon(x-safe,y-safe,x+safe,y-safe,x+safe,y+safe,x-safe,y+safe),x,y,orientation));break;}
                case ELLIPSE:add(r,li,rotated(ellipse(x,y,rad,rad*.65f,14),x,y,orientation));break;
                case DIAMOND:add(r,li,rotated(Shape.polygon(x,y-rad,x+rad,y,x,y+rad,x-rad,y),x,y,orientation));break;
                case DASH:{
                    // Keep at least the cut minimum as dash thickness; tone is
                    // carried by dash length, so small sizes stay cuttable.
                    float half=Math.max(rad*.35f,min*.5f+.01f);
                    if(half>rad*.8f)break;
                    add(r,li,rotated(Shape.polygon(x-rad,y-half,x+rad,y-half,x+rad,y+half,x-rad,y+half),x,y,orientation));break;}
                case HEX:add(r,li,rotated(ellipse(x,y,rad,rad,6),x,y,orientation));break;
                case CROSS:add(r,li,rotated(cross(x,y,rad),x,y,orientation));break;
                case STAR:add(r,li,rotated(star(x,y,rad,starInner),x,y,orientation));break;
                case TRIANGLE:add(r,li,rotated(regularPolygon(x,y,rad,3,-Math.PI/2),x,y,orientation));break;
                case OCTAGON:add(r,li,rotated(regularPolygon(x,y,rad,8,Math.PI/8),x,y,orientation));break;
                case ROUNDED_SQUARE:add(r,li,rotated(roundedSquare(x,y,rad*.70710678f),x,y,orientation));break;
                case TEARDROP:add(r,li,rotated(teardrop(x,y,rad),x,y,orientation));break;
                case HEART:add(r,li,rotated(heart(x,y,rad),x,y,orientation));break;
                case CAPSULE:add(r,li,rotated(capsule(x,y,rad),x,y,orientation));break;
            }
        }
        }
    }
    private static Shape regularPolygon(float x,float y,float r,int n,double phase){float[] pts=new float[2*n];
        for(int i=0;i<n;i++){if(((i)&63)==0)CancellationCheckpoint.check();double t=phase+i*2*Math.PI/n;pts[2*i]=x+r*(float)Math.cos(t);pts[2*i+1]=y+r*(float)Math.sin(t);}return Shape.polygon(pts);}
    /** Rounded square, 4 rounded corners, 4 edge tangencies per side. */
    private static Shape roundedSquare(float x,float y,float r){float[] pts=new float[32];int n=0;
        float inset=r*.32f,cx[]={x+r-inset,x+r-inset,x-r+inset,x-r+inset},cy[]={y-r+inset,y+r-inset,y+r-inset,y-r+inset};
        for(int corner=0;corner<4;corner++)for(int k=0;k<4;k++){if(((k)&63)==0)CancellationCheckpoint.check();
            double a=-Math.PI/2+corner*Math.PI/2+k*Math.PI/6;
            pts[n++]=cx[corner]+inset*(float)Math.cos(a);pts[n++]=cy[corner]+inset*(float)Math.sin(a);}
        return Shape.polygon(pts);}
    private static Shape teardrop(float x,float y,float r){float[] pts=new float[24];
        pts[0]=x;pts[1]=y-r;
        for(int i=1;i<12;i++){if(((i)&63)==0)CancellationCheckpoint.check();double a=-Math.PI/2+i*2*Math.PI/12;
            float rr=(float)(r*(.80+.20*Math.sin(a)));
            pts[2*i]=x+rr*(float)Math.cos(a);pts[2*i+1]=y+rr*(float)Math.sin(a);}
        return Shape.polygon(pts);}
    private static Shape heart(float x,float y,float r){float[] pts=new float[40];
        for(int i=0;i<20;i++){if(((i)&63)==0)CancellationCheckpoint.check();double t=2*Math.PI*i/20;
            double xx=16*Math.pow(Math.sin(t),3)/18;
            double yy=-(13*Math.cos(t)-5*Math.cos(2*t)-2*Math.cos(3*t)-Math.cos(4*t))/18;
            pts[2*i]=x+r*(float)xx;pts[2*i+1]=y+r*(float)yy;}
        return Shape.polygon(pts);}
    private static Shape capsule(float x,float y,float r){float[] pts=new float[24];float rad=r*.55f;
        for(int i=0;i<6;i++){if(((i)&63)==0)CancellationCheckpoint.check();double t=-Math.PI/2+i*Math.PI/5;
            pts[i*2]=x+r-rad+rad*(float)Math.cos(t);pts[i*2+1]=y+rad*(float)Math.sin(t);}
        for(int i=0;i<6;i++){if(((i)&63)==0)CancellationCheckpoint.check();double t=Math.PI/2+i*Math.PI/5;
            pts[12+i*2]=x-r+rad+rad*(float)Math.cos(t);pts[13+i*2]=y+rad*(float)Math.sin(t);}
        return Shape.polygon(pts);}
    /** Directional polygons follow the screen angle in the same millimeter plane as SVG export. */
    private static Shape rotated(Shape shape,float cx,float cy,float degrees){
        if(shape.circle||Math.abs(degrees)<.00001f)return shape;
        double a=Math.toRadians(degrees),co=Math.cos(a),si=Math.sin(a);
        float[] xy=shape.xy.clone();
        for(int i=0;i<xy.length;i+=2){if(((i)&63)==0)CancellationCheckpoint.check();float dx=xy[i]-cx,dy=xy[i+1]-cy;
            xy[i]=cx+(float)(dx*co-dy*si);xy[i+1]=cy+(float)(dx*si+dy*co);
        }
        return Shape.polygon(xy);
    }
    private static Shape ellipse(float x,float y,float rx,float ry,int n){float[] xy=new float[n*2];for(int i=0;i<n;i++){if(((i)&63)==0)CancellationCheckpoint.check();double a=i*Math.PI*2/n;xy[2*i]=x+rx*(float)Math.cos(a);xy[2*i+1]=y+ry*(float)Math.sin(a);}return Shape.polygon(xy);}
    /** Simple concave plus-sign contour, without intersecting interior edges. */
    private static Shape cross(float x,float y,float r){float a=r*.36f;
        return Shape.polygon(x-a,y-r,x+a,y-r,x+a,y-a,x+r,y-a,x+r,y+a,
                x+a,y+a,x+a,y+r,x-a,y+r,x-a,y+a,x-r,y+a,x-r,y-a,x-a,y-a);
    }
    /** Alternating radial vertices make one non-self-intersecting closed star. */
    private static Shape star(float x,float y,float r,float innerFraction){float[] xy=new float[20];for(int i=0;i<10;i++){if(((i)&63)==0)CancellationCheckpoint.check();
        double a=-Math.PI/2+i*Math.PI/5;float rr=(i&1)==0?r:r*innerFraction;
        xy[2*i]=x+(float)Math.cos(a)*rr;xy[2*i+1]=y+(float)Math.sin(a)*rr;
    }return Shape.polygon(xy);}
    /** Exact unit-geometry areas for physical tone calibration (no raster estimate). */
    static final class ShapeAreaResponse {
        private ShapeAreaResponse(){}
        private static final float[] AREA=unitAreas();
        // Cached from the actual polygon vertex templates (including faceted
        // ellipses), not idealized shapes. Freeze these constants so a cancelled
        // first preview cannot poison Java class initialization. The host gate
        // checks generated geometry against these coefficients.
        private static float[] unitAreas(){
            float[] a=new float[WORD_SCREEN+1];
            a[ROUND]=(float)Math.PI;a[SPIRAL]=(float)Math.PI;
            a[SQUARE]=4f;a[ELLIPSE]=1.974170923f;
            a[DIAMOND]=2f;a[DASH]=1.4f;a[HEX]=2.598076105f;
            a[CROSS]=2.361600161f;a[TRIANGLE]=1.299038053f;
            a[OCTAGON]=2.828427076f;a[ROUNDED_SQUARE]=1.948799968f;
            a[TEARDROP]=2.097320557f;a[HEART]=1.687616825f;
            a[CAPSULE]=1.879025221f;
            return a;
        }
        static float polygonArea(float[] xy){
            double sum=0;for(int i=0;i<xy.length;i+=2){
                int j=(i+2)%xy.length;
                sum+=(double)xy[i]*xy[j+1]-(double)xy[j]*xy[i+1];
            }return (float)Math.abs(sum*.5);
        }
        // Practical geometry floor for sharply indented five-point vinyl stars.
        // Tested using the actual local-gap preflight at 0.254 mm. This is a
        // conservative generator floor, NOT a certification of machine ability.
        static float starSafeRadius(float requestedCutWidthMm){
            return Math.max(.50f,requestedCutWidthMm*2f);
        }
        /** Fixed per-layer / per-grid decision, so render and export always agree.
         *  Spatially decorrelates the star screen without mutating global RNG state.
         */
        static boolean keepPromotedStar(int row,int col,int layer,float probability){
            if(!(probability>0f))return false;
            if(probability>=1f)return true;
            int h=(row*0x9e3779b9)^(col*0x85ebca6b)^(layer*0xc2b2ae35)^0x73a6c923;
            h^=h>>>16;h*=0x7feb352d;h^=h>>>15;h*=0x846ca68b;h^=h>>>16;
            float threshold=(float)((h&0xffffffffL)/4294967296.0);
            return threshold<probability;
        }
        /** Dark stars become fuller, but retain five visible bays and tips. */
        static float starInner(float coverage){
            float t=clamp(coverage);t=t*t*(3f-2f*t);
            return .48f+.24f*t;
        }
        static float unitArea(int style,float starInner){
            // 20-vertex smooth-lobed star: four radial stations per lobe.
            // The same coefficient drives the actual cut geometry and area mapping.
            return style==STAR?(float)(5f*Math.sin(Math.PI/5)*starInner):
                   style>=0&&style<AREA.length?AREA[style]:0f;
        }
        /** Invert A = areaCoefficient * radius^2, capped to physical cell.
         *  A fully dark star cannot equal a full circle at the same physical
         *  diameter: do not fabricate equality by violating blade clearance.
         */
        static float radius(int style,float reference,float max,float starInner){
            if(reference<=0||max<=0)return 0f;
            float coeff=unitArea(style,starInner);
            if(coeff<=0f)return reference;
            float target=(float)(reference*Math.sqrt(Math.PI/coeff));
            return Math.min(max,Math.max(0f,target));
        }
    }
    /** Legacy helper retained for source compatibility, no longer used by
     * the Android engine: a second hatch direction must NOT double-cut vinyl. */
    private static void generateEngraving(Result r,int[] src,int w,int h,int li){
        Settings s=r.settings;generateLines(r,src,w,h,li);
        // Slightly narrower second family; constrained by current cut safety.
        generateCurvedLines(r,src,w,h,li,true);
    }
    /** Continuous sine-modulated ribbons. Adjacent lines use a common bounded
     * amplitude and wave period; normals are calculated from the sampled
     * centerline to retain physically filled SVG polygons. */
    private static void generateCurvedLines(Result r,int[] src,int w,int h,int li,boolean secondary){
        Settings s=r.settings;Layer layer=s.layers.get(li);
        float spacing=Math.max(.8f,layer.spacing),margin=s.marginMm;
        float amplitude=secondary?0:Math.min(spacing*.22f,spacing*s.waveAmplitude);
        float halfLimit=Math.min(spacing*s.lineMaxFraction*.5f,
                    (spacing-s.bridgeMm-2*amplitude)*.46f);
        if(secondary)halfLimit*=.43f;
        if(2*halfLimit<s.minimumMm)return;
        double angle=Math.toRadians(layer.angle+(secondary?85f:0f)),ca=Math.cos(angle),sa=Math.sin(angle);
        float cx=s.widthMm/2,cy=r.heightMm/2;
        float extent=(float)Math.hypot(s.widthMm,r.heightMm)*.57f;
        float step=Math.min(s.lineSampleMm,Math.max(.16f,spacing*.2f));
        if((2*extent/spacing)*(2*extent/step)>650000)
            throw new IllegalArgumentException("Too many line samples; increase spacing");
        // Dormant legacy helper: keep its dimensional arithmetic correct in
        // case a future renderer calls it. Active Wavy styles use LineArtEngine.
        float phaseSpacing=2f*(float)Math.PI*Math.max(.1f,s.waveCyclesPerInch)/MM_PER_INCH;
        for(float v=-extent;v<=extent;v+=spacing){CancellationCheckpoint.check();
            checkGenerationCancelled();
            ArrayList<float[]> run=new ArrayList<>();float previous=-1f;
            int cancelTick2=0;
            for(float t=-extent;t<=extent+step;t+=step){
                if((++cancelTick2&63)==0)CancellationCheckpoint.check();
                float wave=amplitude*(float)Math.sin(t*phaseSpacing+v/spacing*.17f);
                float x=(float)(cx+t*ca-(v+wave)*sa),y=(float)(cy+t*sa+(v+wave)*ca);
                if(!in(x,y,s.widthMm,r.heightMm,margin+halfLimit+amplitude)){
                    flushCurved(r,li,run);run.clear();previous=-1f;continue;
                }
                SnipMask mask=s.snips;
                if(mask!=null&&!mask.isEmpty()){
                    float boundary=mask.clearanceWithinMm(x,y,s.widthMm,r.heightMm,halfLimit+amplitude+.08f);
                    // Keep a physical safety buffer between every line edge and the snip.
                    if(boundary<s.minimumMm/2f+.08f ||
                      (!run.isEmpty()&&!mask.safeSegment(run.get(run.size()-1)[0],run.get(run.size()-1)[1],x,y,s.widthMm,r.heightMm))){
                        flushCurved(r,li,run);run.clear();previous=-1f;continue;
                    }
                }
                float weight=coverage(at(src,w,h,x,y,s.widthMm,r.heightMm),s,li);
                if(s.lineInvert)weight=1-weight;
                if(secondary)weight=clamp((weight-s.hatchThreshold)/(1-s.hatchThreshold));
                float desired=halfLimit*(float)Math.pow(clamp(weight),s.lineGamma);
                float half=previous<0?desired:(previous*s.lineSmoothing+desired*(1-s.lineSmoothing));
                if(mask!=null&&!mask.isEmpty())half=Math.min(half,Math.max(0,mask.clearanceWithinMm(x,y,s.widthMm,r.heightMm,halfLimit+amplitude+.08f)-.08f));
                previous=half;
                if(2*half<s.minimumMm){flushCurved(r,li,run);run.clear();previous=-1f;continue;}
                run.add(new float[]{x,y,Math.min(halfLimit,half)});
            }
            flushCurved(r,li,run);
        }
    }
    private static void flushCurved(Result r,int li,ArrayList<float[]> run){
        int n=run.size();if(n<2)return;
        float[] xy=new float[n*4];
        for(int i=0;i<n;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            float[] item=run.get(i),before=run.get(Math.max(0,i-1)),after=run.get(Math.min(n-1,i+1));
            float dx=after[0]-before[0],dy=after[1]-before[1];float dist=(float)Math.hypot(dx,dy);
            if(dist<.00001f)continue;
            float nx=-dy/dist,ny=dx/dist,half=item[2];
            xy[2*i]=item[0]+nx*half;xy[2*i+1]=item[1]+ny*half;
            int reverse=2*n+2*(n-1-i);
            xy[reverse]=item[0]-nx*half;xy[reverse+1]=item[1]-ny*half;
        }
        add(r,li,Shape.polygon(xy));
    }
    /** Connected variable-width ribbons, with sampling, tonal response and controlled smoothing.
     * Each uninterrupted line is a CLOSED polygon made from the two opposite contours.
     * It is not a fixed-width SVG stroke or a series of dashes.
     *
     * For vinyl portraits, the centerline is allowed to drift slightly along the screen normal
     * based on local luminance and contrast. This keeps the ribbon family readable while helping
     * lines bend around facial forms instead of remaining perfectly rigid hatch stripes.
     */
    private static void generateLines(Result r,int[] src,int w,int h,int li){
        Settings s=r.settings;Layer layer=s.layers.get(li);
        float spacing=Math.max(.8f,layer.spacing),margin=s.marginMm;
        float driftMax=spacing*.16f*s.contourStrength;
        float halfLimit=Math.max(0,Math.min(Math.min((spacing-s.bridgeMm)/2f,spacing*s.lineMaxFraction/2f),
                (spacing-s.bridgeMm-2*driftMax)/2f));
        if(2*halfLimit<s.minimumMm)return;
        double angle=Math.toRadians(layer.angle),ca=Math.cos(angle),sa=Math.sin(angle);
        float cx=s.widthMm/2,cy=r.heightMm/2;
        float extent=(float)Math.hypot(s.widthMm,r.heightMm)*.55f;
        float step=Math.min(s.lineSampleMm,Math.max(.16f,spacing*.25f));
        if((2*extent/spacing)*(2*extent/step)>650000)
            throw new IllegalArgumentException("Too many line samples; increase spacing or reduce size");
        float nx=(float)-sa,ny=(float)ca;
        for(float v=-extent;v<=extent;v+=spacing){CancellationCheckpoint.check();
            checkGenerationCancelled();
            ArrayList<float[]> run=new ArrayList<>();float previous=-1f;
            float smoothShift=0f;
            int cancelTick3=0;
            for(float t=-extent;t<=extent+step;t+=step){
                if((++cancelTick3&63)==0)CancellationCheckpoint.check();
                float x=(float)(cx+t*ca-v*sa),y=(float)(cy+t*sa+v*ca);
                float desiredShift=contourShift(src,w,h,s,r.heightMm,x,y,nx,ny,(float)ca,(float)sa,spacing);
                // Limit changes along the ribbon so its edges remain smooth at high zoom.
                float change=Math.max(-step*.20f,Math.min(step*.20f,desiredShift-smoothShift));
                smoothShift+=change*.75f;
                x+=nx*smoothShift;y+=ny*smoothShift;
                if(!in(x,y,s.widthMm,r.heightMm,margin+halfLimit)){
                    flushCurved(r,li,run);run.clear();previous=-1f;continue;
                }
                SnipMask mask=s.snips;
                if(mask!=null&&!mask.isEmpty()){
                    float boundary=mask.clearanceWithinMm(x,y,s.widthMm,r.heightMm,halfLimit+.08f);
                    if(boundary<s.minimumMm/2f+.08f ||
                      (!run.isEmpty()&&!mask.safeSegment(run.get(run.size()-1)[0],run.get(run.size()-1)[1],x,y,s.widthMm,r.heightMm))){
                        flushCurved(r,li,run);run.clear();previous=-1f;continue;
                    }
                }
                float weight=coverage(at(src,w,h,x,y,s.widthMm,r.heightMm),s,li);
                if(s.lineInvert)weight=1-weight;
                float desired=halfLimit*(float)Math.pow(clamp(weight),s.lineGamma);
                float half=previous<0?desired:(previous*s.lineSmoothing+desired*(1-s.lineSmoothing));
                if(mask!=null&&!mask.isEmpty())half=Math.min(half,Math.max(0,mask.clearanceWithinMm(x,y,s.widthMm,r.heightMm,halfLimit+.08f)-.08f));
                previous=half;
                if(2*half<s.minimumMm){flushCurved(r,li,run);run.clear();previous=-1f;continue;}
                run.add(new float[]{x,y,Math.min(halfLimit,half)});
            }
            flushCurved(r,li,run);
        }
    }
    /** Legacy pixel mosaic deliberately remains separate from the vector stencil processor. */
    private static void generateMosaic(Result r,int[] src,int w,int h){Settings s=r.settings;boolean stencil=false;float spacing=Math.max(s.minimumMm+s.bridgeMm,Math.max(.8f,s.layers.get(0).spacing));float usableW=s.widthMm-2*s.marginMm,usableH=r.heightMm-2*s.marginMm;if(usableW<=0||usableH<=0)return;
        int nx=Math.max(1,(int)Math.ceil(usableW/spacing)),ny=Math.max(1,(int)Math.ceil(usableH/spacing));if((long)nx*ny>250000)throw new IllegalArgumentException("Mosaic too detailed; increase spacing");
        int[] idx=new int[nx*ny],chosen=new int[nx*ny];Arrays.fill(idx,-1);
        float[] er=new float[(nx+2)*(ny+2)],eg=er.clone(),eb=er.clone();
        final int[][] matrix={{0,8,2,10},{12,4,14,6},{3,11,1,9},{15,7,13,5}};
        for(int row=0;row<ny;row++){if(((row)&63)==0)CancellationCheckpoint.check();
            checkGenerationCancelled();
            checkGenerationCancelled();
            int cancelTick4=0;
            for(int col=0;col<nx;col++){if((++cancelTick4&63)==0)CancellationCheckpoint.check();
            int pos=row*nx+col;float x=s.marginMm+(col+.5f)*usableW/nx,y=s.marginMm+(row+.5f)*usableH/ny;
            if(s.snips!=null&&!s.snips.isEmpty()&&!s.snips.allows(x/s.widthMm,y/r.heightMm)){idx[pos]=-1;chosen[pos]=s.separationBackground;continue;}
            int pixel=flatten(at(src,w,h,x,y,s.widthMm,r.heightMm),s.separationBackground),red=(pixel>>16)&255,green=(pixel>>8)&255,blue=pixel&255;
            if(!stencil&&s.dither==BAYER){int d=matrix[row&3][col&3]-8;red=Math.min(255,Math.max(0,red+d*3));green=Math.min(255,Math.max(0,green+d*3));blue=Math.min(255,Math.max(0,blue+d*3));}
            if(!stencil&&s.dither==FLOYD){int e=(row+1)*(nx+2)+col+1;red=Math.min(255,Math.max(0,Math.round(red+er[e])));green=Math.min(255,Math.max(0,Math.round(green+eg[e])));blue=Math.min(255,Math.max(0,Math.round(blue+eb[e])));}
            int actual=color(red,green,blue),best=-1;float bd=dist(actual,s.separationBackground);
            for(int i=0;i<s.layers.size();i++)if(s.layers.get(i).enabled){float d=dist(actual,s.layers.get(i).color);if(d<bd){bd=d;best=i;}}
            idx[pos]=best;int match=best<0?s.separationBackground:s.layers.get(best).color;chosen[pos]=match;
            if(!stencil&&s.dither==FLOYD){float[] diffs={red-((match>>16)&255),green-((match>>8)&255),blue-(match&255)};int e=(row+1)*(nx+2)+col+1;for(int ch=0;ch<3;ch++){if(((ch)&63)==0)CancellationCheckpoint.check();float[] err=ch==0?er:ch==1?eg:eb;err[e+1]+=diffs[ch]*7/16f;err[e+nx+1]+=diffs[ch]*3/16f;err[e+nx+2]+=diffs[ch]*5/16f;err[e+nx+3]+=diffs[ch]/16f;}}
        }
        }
        float cw=usableW/nx,ch=usableH/ny,gap=stencil?0:Math.min(s.bridgeMm*.5f,Math.min(cw,ch)*.2f);
        for(int row=0;row<ny;row++){if(((row)&63)==0)CancellationCheckpoint.check();
            checkGenerationCancelled();
            checkGenerationCancelled();
            int cancelTick5=0;
            for(int col=0;col<nx;){if((++cancelTick5&63)==0)CancellationCheckpoint.check();
int owner=idx[row*nx+col],start=col;if(owner<0){col++;continue;}while(col<nx&&idx[row*nx+col]==owner)col++;
            float x0=s.marginMm+start*cw+gap,x1=s.marginMm+col*cw-gap,y0=s.marginMm+row*ch+gap,y1=s.marginMm+(row+1)*ch-gap;
            if(x1-x0<s.minimumMm||y1-y0<s.minimumMm)continue;
            add(r,owner,Shape.polygon(x0,y0,x1,y0,x1,y1,x0,y1));
        }
        }
    }
    private static String f(float v){return String.format(Locale.US,"%.3f",v);}
    /** Only actual nonempty user-included vinyl sheets produce SVGs.
     *  The mounting surface is a preview/background setting; color similarity
     *  must never silently disable a physically cuttable layer. */
    public static boolean cutLayer(Result result,int index){
        if(result==null || index<0 || index>=result.settings.layers.size() || index>=result.shapes.size())return false;
        Layer l=result.settings.layers.get(index);
        return l.enabled && !l.excludeFromExport && !result.shapes.get(index).isEmpty();
    }
    public static int exportedShapeCount(Result r){int total=0;for(int i=0;i<r.shapes.size();i++)if(cutLayer(r,i))total+=r.shapes.get(i).size();return total;}
    /** Artboard footprint, not material sheet nesting. */
    public static String productionSummary(Result r){
        StringBuilder out=new StringBuilder("VINYLSTUDIO PRODUCTION PLAN\n");
        out.append("Substrate #").append(String.format(Locale.US,"%06X",r.settings.paper&0xffffff)).append(" (not cut)\n");
        out.append("Surface color is preview-only. Sheets are omitted only when empty, disabled, or explicitly skipped.\n");
        out.append("Artboard: ").append(f(r.boardW)).append(" x ").append(f(r.boardH)).append(" mm (includes registration allowance)\n");
        out.append("Estimated rectangular material per exported color: ").append(f(r.boardW*r.boardH/10000f)).append(" cm²; actual usage depends on mat placement and waste\n");
        out.append("Application order: bottom to top. All registration centers identical.\n");
        for(int i=0;i<r.shapes.size();i++){if(((i)&63)==0)CancellationCheckpoint.check();
            Layer l=r.settings.layers.get(i);
            out.append(String.format(Locale.US,"%02d %s #%06X : ",i+1,l.name,l.color&0xffffff));
            out.append(cutLayer(r,i)?"CUT "+r.shapes.get(i).size()+" closed shapes":"NOT CUT (empty, disabled, or skipped by user)").append('\n');
        }
        out.append("Layer intersections, tiny islands and under-layer hidden regions are NOT automatically boolean-knocked-out. Inspect individual cuts and test on scrap material.\n");
        for(int i=0;i<r.lineMetrics.size();i++){if(((i)&63)==0)CancellationCheckpoint.check();
            LinePresenceTracker.Metrics m=r.lineMetrics.get(i);
            if(m.examined>0)out.append("Line diagnostics ").append(r.settings.layers.get(i).name)
                .append(": samples=").append(m.examined).append(", strong=").append(m.strong)
                .append(", weak-recovered=").append(m.promoted).append(", weak-rejected=").append(m.rejected)
                .append(", background-downweighted=").append(m.backgroundDownweighted).append('\n');
        }
        return out.toString();
    }
    /** One physical vinyl sheet, filled with that layer's swatch (D2: was always #000000). */
    public static String svg(Result r,int li){
        if(li<0||li>=r.settings.layers.size())throw new IllegalArgumentException("Invalid layer");
        return svg(r,li,r.settings.layers.get(li).color);
    }
    /** As {@link #svg(Result,int)} with an explicit sheet color (the Snip base recolors without regenerating). */
    public static String svg(Result r,int li,int fillArgb){Settings s=r.settings;if(li<0||li>=s.layers.size())throw new IllegalArgumentException("Invalid layer");StringBuilder o=new StringBuilder(1000+r.shapes.get(li).size()*80);
        o.append(SheetFrame.open(r.boardW,r.boardH,r.padMm,fillArgb));
        for(Shape sh:r.shapes.get(li)){CancellationCheckpoint.check();if(sh.circle)o.append("<circle cx=\"").append(f(sh.x)).append("\" cy=\"").append(f(sh.y)).append("\" r=\"").append(f(sh.r)).append("\"/>\n");else {o.append("<path fill-rule=\"evenodd\" d=\"");
                float[][] rings=sh.contours==null?new float[][]{sh.xy}:sh.contours;
                for(float[] ring:rings){CancellationCheckpoint.check();o.append("M ");for(int i=0;i<ring.length;i+=2){if(((i)&63)==0)CancellationCheckpoint.check();if(i!=0)o.append(" L ");o.append(f(ring[i])).append(' ').append(f(ring[i+1]));}o.append(" Z ");}
                o.append("\"/>\n");}}
        if(s.registration)for(float[] m:r.marks)
            o.append(RegistrationMarks.svgMark(s.markStyle,m[0],m[1],s.markSizeMm,s.minimumMm));
        return o.append("</g></svg>\n").toString();
    }
    /** Conservative mark safety: tests full mark bounding boxes against every produced cut polygon/circle. */
    public static String validateMarks(Result r){if(!r.settings.registration)return null;
        return RegistrationCutGeometry.standard(r).firstIssue();
    }
    /** Includes registration geometry because each color is uploaded as a separate SVG. */
    public static int registrationElements(Result r){return r.settings.registration?4:0;}
    public static int svgElementsForLayer(Result r,int layer){
        int total=registrationElements(r);
        for(Shape sh:r.shapes.get(layer))total+=sh.contours==null?1:sh.contours.length;
        // Conservatively count individual cut loops inside compound paths as distinct paths.
        return total;
    }
    /** Cricut's documented SVG vector-shape limit is 5,000 per uploaded file. */
    public static String validateExport(Result r){
        if(exportedShapeCount(r)==0)return "No vinyl cut shapes were generated. Revisit the artwork type, palette, light settings or minimum feature size";
        String marks=validateMarks(r);if(marks!=null)return marks;
        for(int i=0;i<r.shapes.size();i++)if(cutLayer(r,i)){
            if(svgElementsForLayer(r,i)>5000)
                return "Layer "+(i+1)+" exceeds 5,000 Cricut vector elements; increase screen spacing";
        }
        return null;
    }
    public static String report(Result r){StringBuilder s=new StringBuilder("VinylStudio cut report (vinyl-cutting only)\nDisplay: inches, SVG: precise millimeter geometry\n");s.append("Artwork inches: ").append(f(toInches(r.settings.widthMm))).append(" x ").append(f(toInches(r.heightMm))).append("\n");s.append("Mode: ").append(r.settings.mode==STENCIL?"palette-quantized vector stencil":r.settings.mode==MOSAIC?"legacy mosaic":r.settings.mode==SPOT?"custom vinyl halftone":"CMYK vinyl halftone").append("\nArtboard: ").append(f(r.boardW)).append(" x ").append(f(r.boardH)).append("\nArtwork: ").append(f(r.settings.widthMm)).append(" x ").append(f(r.heightMm)).append("\nPattern: ").append(PATTERNS[r.settings.pattern]).append("\n");for(int i=0;i<r.shapes.size();i++){if(((i)&63)==0)CancellationCheckpoint.check();Layer l=r.settings.layers.get(i);s.append(i+1).append(". ").append(l.name).append(" #").append(String.format(Locale.US,"%06X",l.color&0xffffff)).append(" strength=").append(f(l.strength)).append(" lineStyle=").append(PATTERNS[l.lineStyleOverride>=0?l.lineStyleOverride:r.settings.pattern]).append(" widthScale=").append(f(l.lineWidthScale)).append(" gamma=").append(f(l.lineGammaOverride>0?l.lineGammaOverride:r.settings.lineGamma)).append(" shapes=").append(r.shapes.get(i).size()).append(" svgElements=").append(svgElementsForLayer(r,i)).append("/5000 angle=").append(l.angle).append(" spacingIn=").append(f(toInches(l.spacing))).append(" (LPI=").append(f(MM_PER_INCH/l.spacing)).append(")").append("\n");}String issue=validateMarks(r);s.append("Cut exclusion inset (mm): ").append(f(r.settings.marginMm)).append("; outer spacing (mm): ").append(f(r.settings.outerSpacingMm)).append(". Boundary is not a cut path.\n");s.append("SVG counts are PER COLOR FILE; combined project count is not an SVG upload limit.\n");if(r.settings.mode==STENCIL)s.append("Stencil uses nearest-color Lab segmentation, native region contours and paper openings. Minimum detail, contour smoothing and isolated-island cleanup are approximations; inspect the cut.\n");s.append("Cricut SVG validation: ").append(validateExport(r)==null?"PASS":"FAILED: "+validateExport(r)).append("\n");s.append("Registration marks: ").append(RegistrationMarks.name(r.settings.markStyle)).append(" · ").append(f(r.settings.markSizeMm)).append(" mm · shared centers for all layers\n");s.append("Registration check: ").append(issue==null?"PASS (physical cut contours and clearance)":issue).append("\n");if(r.settings.pattern==CROSSHATCH||r.settings.pattern==HATCH){s.append("Hatching is one non-crossing ribbon family per physical color layer. Composite multi-angle hatching is possible across layers.\n");}
        for(Layer layer:r.settings.layers)if(layer.lineStyleOverride==CROSSHATCH||layer.lineStyleOverride==HATCH){s.append("Per-channel hatch uses a single non-crossing family to avoid double cuts.\n");break;}
        s.append("Minimum requested cut width: ").append(f(r.settings.minimumMm)).append(" mm; line LPI is based on the base screen spacing. Detail ribbons can locally double line density.\n");
        s.append("The preview and SVG share generated cut shapes. Export files are numbered in bottom-to-top vinyl APPLICATION order; each is cut separately. SVG geometry never forces ink-style overprinting.\nPolygon snips: ");s.append(r.settings.snips==null?0:r.settings.snips.size()).append(" shared nondestructive edits; never exported as SVG clipping paths.\n");
        s.append("Standard parallel lines are trimmed after full-image tone analysis; other cut shapes touching Snips may be omitted. Inspect detail near boundaries.\nTest cut before production.\n");return s.toString();}

    /** Fixed CMYK source channels with optional opaque-vinyl spot accents.
     * Physical order: Y at the bottom, C and M in the middle, accents, then K on top.
     * Preserves the CMYK separation identity and original layer object settings.
     */
    static final class CmykExtendedStack {
        private CmykExtendedStack(){}
        private static final int[] ORDER={2,0,1,3};
        public static final int[] ACCENT_COLORS={0xfff2b99d,0xff779e76,0xff7955a5,0xff5d8199};
        public static final String[] ACCENT_NAMES={"Peach accent","Sage accent","Violet accent","Slate accent"};
    
        public static boolean hasAnchors(List<VinylEngine.Layer> layers){
            if(layers==null||layers.size()<4||layers.size()>8)return false;
            boolean[] seen=new boolean[4];
            for(VinylEngine.Layer layer:layers){CancellationCheckpoint.check();
                int c=layer.cmykChannel;
                if(c>=0){if(c>3||seen[c])return false;seen[c]=true;}
            }
            for(boolean found:seen)if(!found)return false;
            return true;
        }
    
        /** Sort a copy of the list; extra colors retain their existing relative order. */
        public static void canonicalize(List<VinylEngine.Layer> layers){
            if(!hasAnchors(layers))throw new IllegalArgumentException("Four Y/C/M/K anchors required");
            List<VinylEngine.Layer> old=new ArrayList<>(layers);
            layers.clear();
            for(VinylEngine.Layer layer:old)if(layer.silhouetteBase)layers.add(layer);
            for(int c:ORDER){CancellationCheckpoint.check();
                if(c==3)for(VinylEngine.Layer layer:old)
                    if(layer.cmykChannel==-1&&!layer.silhouetteBase)layers.add(layer);
                for(VinylEngine.Layer layer:old)if(layer.cmykChannel==c&&!layer.silhouetteBase)layers.add(layer);
            }
        }
    
        public static int addSpot(List<VinylEngine.Layer> layers,float spacing,float density,float contrast){
            if(!hasAnchors(layers)||layers.size()>=8)throw new IllegalArgumentException("Four to eight CMYK + vinyl layers");
            canonicalize(layers);
            int extraCount=layers.size()-4;
            VinylEngine.Layer added=new VinylEngine.Layer(ACCENT_NAMES[extraCount],ACCENT_COLORS[extraCount]);
            added.cmykChannel=-1;
            added.spacing=spacing;added.density=density;added.contrast=contrast;
            int index=layers.size()-1;
            layers.add(index,added);
            return index;
        }
    
        public static void resize(List<VinylEngine.Layer> layers,int count,float spacing,float density,float contrast){
            if(count<4||count>8||!hasAnchors(layers))throw new IllegalArgumentException("CMYK needs 4–8 layers");
            canonicalize(layers);
            while(layers.size()<count)addSpot(layers,spacing,density,contrast);
            while(layers.size()>count)layers.remove(layers.size()-2);
        }
        /** Removing an anchor is prohibited; only added accent sheets can be removed. */
        public static boolean removeSpot(List<VinylEngine.Layer> layers,int index){
            if(!hasAnchors(layers)||layers.size()<=4||index<0||index>=layers.size()||layers.get(index).cmykChannel!=-1||layers.get(index).silhouetteBase)return false;
            layers.remove(index);canonicalize(layers);return true;
        }
    }
}

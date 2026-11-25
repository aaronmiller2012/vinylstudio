package com.halftonestudio;

/** Independently developed vinyl-first halftone presets.
 * These values are starting points, not guaranteed blade/material tolerances.
 * Applies physical artwork size, never print DPI, to select cuttable spacing.
 */
public final class SmartHalftonePresets {
    private SmartHalftonePresets() {}
    public static final int[] RECOMMENDED={VinylEngine.LINE,VinylEngine.HATCH,VinylEngine.ROUND,
            VinylEngine.WAVE,VinylEngine.LINE,VinylEngine.HATCH,VinylEngine.ROUND};
    public static final String[] LAYER_GUIDANCE={
        "2 layers: Duotone lines, silhouettes or simple engraving. Paper supplies highlights.",
        "3 layers: Tritone portrait, engraving or round dots; reserve a dark detail layer.",
        "4 layers: CMYK dots or separated-angle vinyl portrait lines. Recommended default.",
        "5 layers: Four-color composition plus a purposeful accent; avoid gratuitous detail.",
        "6 layers: Expanded color palette with shared geometry spacing and separate SVG cuts.",
        "7 layers: Fine-art spot color with conservative feature widths and registration.",
        "8 layers: Advanced multicolor artwork; check every physical layer and cut length."
    };
    public static int recommendedPattern(int layerCount){return RECOMMENDED[Math.max(2,Math.min(8,layerCount))-2];}
    public static String advice(int layerCount){return LAYER_GUIDANCE[Math.max(2,Math.min(8,layerCount))-2];}
    public static boolean ribbons(int pattern){return LineArtEngine.isLineStyle(pattern);}
    public static String explanation(int style){switch(style){
        case VinylEngine.LINE:return "Connected lines change width with photographic tone. Excellent for portraits.";
        case VinylEngine.STRAIGHT:return "Parallel, variable-width screened ribbons with independent CMYK angles.";
        case VinylEngine.CONTOUR:return "Luminance-guided bends following contrast and photographic forms (not semantic face tracing).";
        case VinylEngine.CROSSHATCH:return "Layer-safe crosshatching: each vinyl color has a single shifted hatch angle, while colors intersect in the composite. No duplicate cuts within one sheet.";
        case VinylEngine.RINGS:return "Tonal concentric curves around your chosen focal point.";
        case VinylEngine.RAYS:return "Tonal radial ribbons expanding from the selected focal point.";
        case VinylEngine.ZIGZAG:return "Angular sawtooth ribbons whose widths follow image density.";
        case VinylEngine.BROKEN:return "Broken ribbons interrupted in light and midtone regions.";
        case VinylEngine.BEADED:return "Discrete circular cut shapes aligned into screen lines.";
        case VinylEngine.FADE:return "Tapered ribbons disappear in highlights, respecting minimum feature width.";
        case VinylEngine.WAVE:return "Flowing sine-wave ribbons with bounded bends for vinyl cutting.";
        case VinylEngine.HATCH:return "Engraving ribbons with adjustable dark-region emphasis. One cut-safe direction per vinyl color.";
        case VinylEngine.CROSS:return "Graphic plus-sign screen; each cross is one closed cut shape.";
        case VinylEngine.STAR:return "Decorative five-point star screen with cut-safe shape scaling.";
        case VinylEngine.ROUND:return "Classic tonal dots for photographic shading.";
        case VinylEngine.SQUARE:return "Graphic halftone blocks with crisp geometric edges.";
        case VinylEngine.ELLIPSE:return "Elongated dots that add a directional print texture.";
        case VinylEngine.DIAMOND:return "Angular dots for striking high-contrast designs.";
        case VinylEngine.DASH:return "Separate short line segments, suitable for bold images.";
        case VinylEngine.HEX:return "Six-sided screening for geometric posters.";
        case VinylEngine.TRIANGLE:return "Three-sided motifs that follow a smooth deformable screen.";
        case VinylEngine.OCTAGON:return "Eight-sided motifs with an independent CMYK wave field.";
        case VinylEngine.ROUNDED_SQUARE:return "Soft-corner square tiles for geometric portraits.";
        case VinylEngine.TEARDROP:return "Directional closed droplets with waveable grid placement.";
        case VinylEngine.HEART:return "Decorative heart halftones with waveable grid placement.";
        case VinylEngine.CAPSULE:return "Rounded elongated lozenges in a cut-safe shape grid.";
        case VinylEngine.WORD_SCREEN:return "Complete cut-safe words over CMYK and spot-color photo separations. Adaptive packing preserves photographic color regions without a forced underlay.";
        case VinylEngine.ASCII:return "ASCII symbols use the same CMYK and spot-vinyl screens as geometric styles. Simple cut-safe marks at small sizes; larger cells support complete letters.";
        case VinylEngine.SPIRAL:return "Rotating dot grid that follows a broad spiral field.";
        default:return "Vinyl halftone pattern";
    }}
    /** Three controls are chosen specifically for the selected shape family. */
    public static String[] controls(int style){switch(style){
        case VinylEngine.LINE:return new String[]{"Spacing", "Max line width", "Contour flow"};
        case VinylEngine.STRAIGHT:case VinylEngine.FADE:return new String[]{"Spacing","Max line width","Fine detail"};
        case VinylEngine.CONTOUR:return new String[]{"Spacing","Contour flow","Fine detail"};
        case VinylEngine.CROSSHATCH:return new String[]{"Spacing","Hatch rotation","Shadow density"};
        case VinylEngine.RINGS:case VinylEngine.RAYS:return new String[]{"Spacing","Focal X","Focal Y"};
        case VinylEngine.ZIGZAG:return new String[]{"Spacing","Zigzag amplitude","Frequency"};
        case VinylEngine.BROKEN:return new String[]{"Spacing","Break sensitivity","Line width"};
        case VinylEngine.BEADED:return new String[]{"Spacing","Bead size","Tone gamma"};
        case VinylEngine.WAVE:return new String[]{"Spacing", "Wave strength", "Line width"};
        case VinylEngine.HATCH:return new String[]{"Spacing", "Shadow emphasis", "Line width"};
        case VinylEngine.CROSS:case VinylEngine.STAR:case VinylEngine.ROUND:case VinylEngine.SQUARE:case VinylEngine.ELLIPSE:case VinylEngine.DIAMOND:case VinylEngine.HEX:case VinylEngine.SPIRAL:case VinylEngine.TRIANGLE:case VinylEngine.OCTAGON:case VinylEngine.ROUNDED_SQUARE:case VinylEngine.TEARDROP:case VinylEngine.HEART:case VinylEngine.CAPSULE:case VinylEngine.DASH:case VinylEngine.ASCII:case VinylEngine.WORD_SCREEN:return new String[]{"Spacing", "Shape size", "Contrast"};
        default:return new String[]{"Spacing", "Dot coverage", "Contrast"};
    }}
    public static void apply(VinylEngine.Settings settings,int style,int profile,float widthIn,float heightIn){
        if(style<0||style>=VinylEngine.PATTERNS.length)throw new IllegalArgumentException("Pattern not available");
        settings.pattern=style;
        // Initial opaque-sheet calibration only: selecting another family
        // cannot carry a stale CMYK line compensation into Shapes or Classic.
        settings.opaqueLineBalance=settings.mode==VinylEngine.CMYK &&
            (style==VinylEngine.LINE||style==VinylEngine.STRAIGHT||
             style==VinylEngine.CONTOUR||style==VinylEngine.WAVE||
             style==VinylEngine.HATCH||style==VinylEngine.CROSSHATCH||
             style==VinylEngine.ZIGZAG);
        // Newly selected line styles use cut-safe tonal recovery immediately.
        // Previously saved Classic CMYK projects retain their serialized mode;
        // the explicit Classic v2.24 toggle remains available for comparison.
        if(settings.mode==VinylEngine.CMYK && LineArtEngine.isLineStyle(style)) {
            ClassicCmykLines.useModern(settings);
            CmykPortraitDefaults.applyPreset(settings);
            settings.lineContinuity=CmykPortraitDefaults.LINE_CONTINUITY;
            settings.linePresenceRecovery=.65f;
            settings.lineHighlightRetention=.48f;
            settings.portraitBackgroundGuard=.55f;
        } else { CmykPortraitDefaults.applyPreset(settings); settings.classicCmykLines=false;
            settings.lineContinuity=CmykPortraitDefaults.LINE_CONTINUITY;settings.linePresenceRecovery=.55f;settings.portraitBackgroundGuard=.55f; }
        VinylSizing.apply(settings,widthIn,heightIn,profile);
        settings.linkedControls=true;
        settings.masterDensity=1f;settings.masterContrast=1.10f;
        settings.shapeScale=.88f;
        settings.lineMaxFraction=.81f;
        settings.lineGamma=.93f;
        settings.lineSmoothing=.46f;
        settings.contourStrength=.62f;
        settings.waveAmplitude=.14f;
        settings.waveCyclesPerInch=1.6f;
        settings.hatchThreshold=.70f;
        settings.detailBoost=0f;
        settings.shapeDefinition=LineArtEngine.supportsShapeDefinition(style)?.40f:0f;
        if(style==VinylEngine.LINE){settings.lineMaxFraction=.85f;settings.lineGamma=.84f;settings.lineSmoothing=.40f;settings.contourStrength=.80f;settings.detailBoost=.18f;settings.shapeDefinition=.43f;}
        else if(style==VinylEngine.STRAIGHT){settings.lineMaxFraction=.83f;settings.contourStrength=0f;}
        else if(style==VinylEngine.CONTOUR){settings.lineMaxFraction=.76f;settings.contourStrength=.82f;settings.detailBoost=.16f;settings.shapeDefinition=.42f;}
        else if(style==VinylEngine.CROSSHATCH){settings.lineMaxFraction=.62f;settings.crossAngleDeg=60f;settings.hatchThreshold=.73f;}
        else if(style==VinylEngine.RINGS||style==VinylEngine.RAYS){settings.lineMaxFraction=.70f;}
        else if(style==VinylEngine.ZIGZAG){settings.lineMaxFraction=.63f;settings.waveAmplitude=.12f;settings.waveCyclesPerInch=1.5f;}
        else if(style==VinylEngine.BROKEN){settings.lineMaxFraction=.70f;settings.lineGamma=.92f;}
        else if(style==VinylEngine.BEADED){settings.lineMaxFraction=.72f;settings.lineGamma=.85f;}
        else if(style==VinylEngine.FADE){settings.lineMaxFraction=.72f;settings.lineGamma=1.15f;settings.detailBoost=.25f;}
        else if(style==VinylEngine.WAVE){settings.lineMaxFraction=.69f;settings.lineGamma=.92f;settings.waveAmplitude=.11f;settings.lineSmoothing=.30f;settings.contourStrength=.40f;settings.shapeDefinition=.32f;}
        else if(style==VinylEngine.HATCH){settings.lineMaxFraction=.72f;settings.hatchThreshold=.74f;settings.lineSmoothing=.32f;settings.contourStrength=.56f;}
        else if(style==VinylEngine.DASH){settings.shapeScale=.82f;settings.masterContrast=1.24f;}
        else if(style==VinylEngine.ROUND){settings.shapeScale=.90f;settings.masterContrast=1.05f;}
        else if(style==VinylEngine.CROSS||style==VinylEngine.STAR){settings.shapeScale=.83f;settings.masterContrast=1.28f;}
        if(profile==VinylSizing.DETAIL){settings.shapeScale=Math.min(.94f,settings.shapeScale+.03f);settings.lineMaxFraction=Math.min(.9f,settings.lineMaxFraction+.04f);}
        if(profile==VinylSizing.EASY_WEED){settings.shapeScale=Math.min(.85f,settings.shapeScale);settings.lineMaxFraction=Math.min(.74f,settings.lineMaxFraction);settings.hatchThreshold=.80f;}
        // Four-layer CMYK vinyl should keep proper separated screen angles even for line-based styles.
        // Spot-color line families can still share a common orientation when desired.
        if(style==VinylEngine.STAR){
            // Stars need a minimum radius for their bays and tips; pick a
            // spacing at which cut-safe stars fit, instead of failing render.
            float safe=VinylEngine.ShapeAreaResponse.starSafeRadius(Math.max(settings.minimumMm,.2f));
            float needed=2f*safe/Math.max(.1f,settings.shapeScale)+settings.bridgeMm+.05f;
            settings.masterSpacingMm=Math.max(settings.masterSpacingMm,needed);
        }
        if(settings.mode==VinylEngine.CMYK&&settings.layers.size()>=4)settings.anglePreset=VinylEngine.ANGLES_CLASSIC;
        else if(ribbons(style))settings.anglePreset=VinylEngine.ANGLES_PARALLEL;
        for(VinylEngine.Layer l:settings.layers){l.spacing=settings.masterSpacingMm;l.density=settings.masterDensity;l.contrast=settings.masterContrast;}
        VinylEngine.applyAnglePreset(settings);
    }
}

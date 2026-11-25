package com.halftonestudio;

import java.util.*;

/** Integration boundary for supported external generators. No Android APIs.
 * Guarantees <= 8 legacy layers or <= 9 for five new craft styles, with conservative silhouette containment
 * for the two planar mosaic styles. Legacy projects and engines are untouched.
 */
public final class VinylStyleSuite {
    private VinylStyleSuite() {}
    public static final int LOW_POLY=0, GLASS=1, TSP=2, LINOCUT=3,
        TRUCHET=4, TEXT=5, TOPOGRAPHIC=6, RIDGE=7, BANKNOTE=9, TESSELLATION_MOTIF=10,
        MARQUETRY=11, FINGERPRINT=12, CONNECTED_LACE=13, REDUCTION_WOODCUT=14,
        WOVEN_RIBBON=15, SCRATCHBOARD=16, DELAUNAY_WIREFRAME=17, CONTINUOUS_SCRIBBLE=18,
        STRING_ART=19, KINTSUGI=20, WOODGRAIN=21, CIRCUIT_TRACE=22, CELTIC_BRAID=23,
        TORN_COLLAGE=24, ARCHITECTURAL=25, PRISMATIC_DIAGONAL=26,
        CONFETTI=27, CHAINMAIL=28, SPIDER_WEB=29,
        CONFETTI_ROUND=30, CONFETTI_SQUARE=31, CONFETTI_PAPER=32,
        CONFETTI_STREAMER=33, CONFETTI_FALLEN=34,
        CAMOUFLAGE=35, LEAF_VEIN=36, FISHNET=37, LACE=38, CROCHET=39,
        CAMO_DESERT=40, CAMO_URBAN=41, CAMO_DIGITAL=42,
        QUILLING_RIBBONS=43, MARBLED_INK=44, QUILLED_MARBLING=45,
        POP_PANELS=46, POSTER_STENCIL=47,
        // Appended Prismatic ribbon family. Never reorder or reuse these IDs.
        PRISMATIC_CONTOUR_FLOW=48, PRISMATIC_TWILL=49, PRISMATIC_SILK=50,
        PRISMATIC_BRAIDED_FLOW=51, PRISMATIC_CHEVRON=52;
    // Reserved historical IDs. Never reuse them for another style.
    public static final int RETIRED_STYLE_ID=8;
    public static final String[] NAMES={"Low-poly Delaunay","Stained Glass","TSP Single Line",
        "Linocut / Woodcut","Truchet Tiles","Multilayer Text Portrait","Topographic Layers","(retired style 7)",
        "(retired style 8)","Banknote Engraving","Tessellation Portrait",
        "Organic Marquetry Portrait","Fingerprint Portrait","(retired style 13)",
        "Reduction Woodcut","Woven Ribbon Portrait","(retired style 16)",
        "(retired style 17)","(retired style 18)","(retired style 19)",
        "(retired style 20)","Flowing Woodgrain Portrait","(retired style 22)",
        "(retired style 23)","Torn-Paper Collage","(retired style 25)","Prismatic Diagonal Portrait",
        "Mixed Confetti Portrait","Chainmail Portrait","(retired style 29)",
        "Round Confetti Portrait","Square Confetti Portrait","Paper Confetti Portrait",
        "Streamer Confetti Portrait","Fallen Confetti Portrait",
        "Woodland Camo Portrait","(retired style 36)","Fishnet Portrait",
        "(retired style 38)","Crochet Portrait","Desert Camo Portrait",
        "Urban Camo Portrait","Digital Camo Portrait",
        "Quilling Ribbons","Marbled Ink","Quilled Marbling",
        "(retired style 46)","Poster Stencil",
        "Prismatic Contour Flow","Prismatic Twill","Prismatic Silk",
        "Prismatic Braided Flow","Prismatic Chevron"};
    public static boolean isRetiredStyleId(int id){
        switch(id){
            case RIDGE:case 8:case CONNECTED_LACE:case SCRATCHBOARD:case DELAUNAY_WIREFRAME:
            case CONTINUOUS_SCRIBBLE:case STRING_ART:case KINTSUGI:case CIRCUIT_TRACE:
            case CELTIC_BRAID:case ARCHITECTURAL:case SPIDER_WEB:
            case LEAF_VEIN:case LACE:case POP_PANELS:return true;
            default:return false;
        }
    }
    /** Retired projects reopen in photo mode with their original photo and Snip retained. */
    public static int restoredStyleId(int savedId) {
        return !isRetiredStyleId(savedId) && savedId >= 0 && savedId < NAMES.length ? savedId : -1;
    }
    /** The original saved Confetti ID 27 remains mixed; new IDs are append-only. */
    public static boolean isConfetti(int style) {
        return style==CONFETTI || (style>=CONFETTI_ROUND&&style<=CONFETTI_FALLEN);
    }
    public static int confettiShapeMode(int style) {
        switch(style){
            case CONFETTI_ROUND:return FallenConfettiEngine.MODE_ROUND;
            case CONFETTI_SQUARE:return FallenConfettiEngine.MODE_SQUARE;
            case CONFETTI_PAPER:return FallenConfettiEngine.MODE_PAPER;
            case CONFETTI_STREAMER:return FallenConfettiEngine.MODE_STREAMER;
            case CONFETTI_FALLEN:return FallenConfettiEngine.MODE_FALLEN;
            default:return FallenConfettiEngine.MODE_MIXED;
        }
    }
    public static boolean isCraft(int style){return style>=CAMOUFLAGE&&style<=CAMO_DIGITAL;}
    public static boolean isOrganicFlow(int style){return style>=QUILLING_RIBBONS&&style<=QUILLED_MARBLING;}
    public static boolean isPopPoster(int style){return style==POP_PANELS||style==POSTER_STENCIL;}
    /** The five appended ribbon siblings of Prismatic Diagonal (not Diagonal itself). */
    public static boolean isPrismaticRibbon(int style){return style>=PRISMATIC_CONTOUR_FLOW&&style<=PRISMATIC_CHEVRON;}
    /** Diagonal plus its ribbon siblings: shared slot, foundation, palette and locked-vector policy. */
    public static boolean isPrismaticFamily(int style){return style==PRISMATIC_DIAGONAL||isPrismaticRibbon(style);}
    public static PrismaticRibbonPortraitEngine.Mode ribbonMode(int style){
        switch(style){
            case PRISMATIC_CONTOUR_FLOW:return PrismaticRibbonPortraitEngine.Mode.CONTOUR_FLOW;
            case PRISMATIC_TWILL:return PrismaticRibbonPortraitEngine.Mode.TWILL;
            case PRISMATIC_SILK:return PrismaticRibbonPortraitEngine.Mode.SILK;
            case PRISMATIC_BRAIDED_FLOW:return PrismaticRibbonPortraitEngine.Mode.BRAIDED_FLOW;
            case PRISMATIC_CHEVRON:return PrismaticRibbonPortraitEngine.Mode.CHEVRON;
            default:throw new IllegalArgumentException("Not a Prismatic ribbon style: "+style);
        }
    }
    public static final class Config {
        public int style=LOW_POLY, variant=0, detail=55, colorCount=6, motifFamily=0;
        public boolean draft=false;
        public float widthMm=152.4f, leadMm=1.4f, weightMm=0.7f, minimumMm=0.4f;
        /** "Minimum gap / bridge": weed-waste floor between pieces on one sheet. */
        public float gapMm=0.5f;
        public int[] palette=null;
        public int leadColor=0xff1f242b, paper=0xfffaf9f5;
        public float prismaticGapMm=.32f;
        public float flowStrength=1f;
        public int organicSmoothing=OrganicFlowCutSmoother.DEFAULT;
        public float trioSpacingMm=.40f;
        public int trioOrganic=50;
        public int prismaticIrregularity=43, prismaticRoundness=52, prismaticFeature=76, prismaticTone=65;
        public String words="VINYL STUDIO";
        public boolean[] allowed=null;
        public PopPosterEngine.Options popPoster=null;
        public Config copy(){Config c=new Config(); c.style=style;c.variant=variant;c.detail=detail;
            c.colorCount=colorCount;c.motifFamily=motifFamily;c.draft=draft;c.widthMm=widthMm;c.leadMm=leadMm;c.weightMm=weightMm;
            c.minimumMm=minimumMm;c.gapMm=gapMm;c.prismaticGapMm=prismaticGapMm;
            c.trioSpacingMm=trioSpacingMm;c.trioOrganic=trioOrganic;c.flowStrength=flowStrength;
            c.organicSmoothing=organicSmoothing;
            c.prismaticIrregularity=prismaticIrregularity;c.prismaticRoundness=prismaticRoundness;
            c.prismaticFeature=prismaticFeature;c.prismaticTone=prismaticTone;c.palette=palette==null?null:palette.clone();
            c.leadColor=leadColor;c.paper=paper;c.words=words;c.allowed=allowed==null?null:allowed.clone();
            c.popPoster=popPoster==null?null:popPoster.copy();
            return c;}
    }
    public static StyleKit.Result generate(int[] pixels,int width,int height,Config c){
        if(c==null||c.style<0||c.style>=NAMES.length||isRetiredStyleId(c.style))throw new IllegalArgumentException("Invalid or retired style");
        if(c.colorCount<2||c.colorCount>((isCraft(c.style)||c.style==POSTER_STENCIL)?9:8))
            throw new IllegalArgumentException("Invalid layer count for selected style");
        if(c.palette!=null&&(c.palette.length<1||c.palette.length>((isCraft(c.style)||c.style==POSTER_STENCIL)?9:8)))
            throw new IllegalArgumentException("Invalid swatch count");
        // Legacy projects may store two layers for GLASS even though lead +
        // two glass colors requires three physical slots. Canonicalize the
        // count without changing the saved values of unrelated styles.
        final int d=Math.max(0,Math.min(100,c.detail));
        final float f=d/100f;
        final float w=c.widthMm;
        final int[] colors=c.palette==null?null:c.palette.clone();
        final float min=Math.max(VinylEngine.MINIMUM_CUT_WIDTH_MM,
            Float.isFinite(c.minimumMm)?c.minimumMm:VinylEngine.MINIMUM_CUT_WIDTH_MM);
        final StyleKit.Result result;
        if(isPopPoster(c.style)){
            PopPosterEngine.Options options=c.popPoster==null?new PopPosterEngine.Options():c.popPoster.copy();
            options.style=c.style==POP_PANELS?PopPosterEngine.POP:PopPosterEngine.POSTER;
            options.widthMm=w;options.minCutMm=min;
            options.gapMm=StyleCutGeometryPreflight.exportGapMm(c.gapMm,min);
            options.draft=c.draft;options.allowed=c.allowed;options.detail=d;
            if(c.style==POSTER_STENCIL){
                // One physical swatch slot per tone. Shared Colors/Layers state is
                // authoritative; preset names never silently overwrite manual edits.
                options.toneCount=c.colorCount;
                options.includeBase=true;
                options.posterColors=colors==null?
                    PopPosterEngine.photoTonePalette(pixels,width,height,c.allowed,c.colorCount,
                        PopPosterEngine.posterPalette(options.posterPalette,c.colorCount)):
                    Arrays.copyOf(colors,c.colorCount);
            }
            result=PopPosterEngine.generate(pixels,width,height,options);
            if(c.style==POSTER_STENCIL)result.paperArgb=c.paper;
        }else if(isOrganicFlow(c.style)){
            OrganicFlowPortraitEngine.Options o=new OrganicFlowPortraitEngine.Options();
            o.mode=c.style-QUILLING_RIBBONS;o.widthMm=w;o.detail=d;o.layers=c.colorCount;
            o.minCutMm=min;o.gapMm=StyleCutGeometryPreflight.exportGapMm(c.gapMm,min);
            o.flow=StyleKit.clampF(c.flowStrength,.25f,2.5f);o.draft=c.draft;
            o.smoothing=StyleKit.clampI(c.organicSmoothing,0,100);
            o.paperArgb=c.paper;o.palette=colors;o.allowed=c.allowed;
            result=OrganicFlowPortraitEngine.generate(pixels,width,height,o);
        }else if(isCraft(c.style)){
            NineLayerCraftPatternEngine.Options o=new NineLayerCraftPatternEngine.Options();
            o.pattern=c.style-CAMOUFLAGE;o.detail=d;o.layers=c.colorCount;
            // Craft meshes are drawn on a raster surface: aim above the cut
            // minimum so grid rounding cannot leave sub-minimum strands.
            o.widthMm=w;o.minCutMm=Math.min(4f,min*1.12f+.04f);o.draft=c.draft;o.paperArgb=c.paper;
            o.gapMm=StyleCutGeometryPreflight.exportGapMm(c.gapMm,min);
            o.palette=colors;o.allowed=c.allowed;
            result=NineLayerCraftPatternEngine.generate(pixels,width,height,o);
        }else if(isConfetti(c.style)||c.style==CHAINMAIL){
            PatternPortraitTrioEngine.Options o=new PatternPortraitTrioEngine.Options();
            o.kind=isConfetti(c.style)?PatternPortraitTrioEngine.CONFETTI:
                PatternPortraitTrioEngine.CHAINMAIL;
            o.confettiMode=confettiShapeMode(c.style);
            o.widthMm=w;o.detail=d;o.layers=c.colorCount;
            o.minCutMm=min;o.spacingMm=c.trioSpacingMm;o.organic=c.trioOrganic;o.draft=c.draft;
            o.gapMm=StyleCutGeometryPreflight.exportGapMm(c.gapMm,min);
            o.palette=colors;o.paperArgb=c.paper;o.allowed=c.allowed;
            result=PatternPortraitTrioEngine.generate(pixels,width,height,o);
        }else if(c.style==PRISMATIC_DIAGONAL){
            PrismaticDiagonalPortraitEngine.Options o=new PrismaticDiagonalPortraitEngine.Options();
            o.detail=d;o.layers=c.colorCount;o.widthMm=w;o.minCutMm=min;o.draft=c.draft;
            o.gapMm=c.prismaticGapMm;o.irregularity=c.prismaticIrregularity;
            // M5: layout keeps its historical spacing; the final sheets are conditioned to the
            // export weed gap so the cut file no longer carries ~300 near-touching ribbon ends.
            o.conditionGapMm=Math.max(c.prismaticGapMm,StyleCutGeometryPreflight.exportGapMm(c.gapMm,min));
            // M5 (product decision 2026-11-25): Diagonal adopts the siblings' size-aware pitch above 6 in
            // (8 photos at 11 in: tone 0.848 -> 0.908, SSIM 0.63 -> 0.77). The 559 engine pin runs the
            // engine directly with this option off.
            o.sizeAwarePitch=true;
            o.roundness=c.prismaticRoundness;o.featureEmphasis=c.prismaticFeature;
            o.toneBalance=c.prismaticTone;
            o.palette=colors;o.paperArgb=c.paper;o.allowed=c.allowed;
            result=PrismaticDiagonalPortraitEngine.generate(pixels,width,height,o);
        }else if(isPrismaticRibbon(c.style)){
            if(colors!=null&&colors.length!=c.colorCount)
                throw new IllegalArgumentException("Prismatic palette must match the physical layer count");
            PrismaticRibbonPortraitEngine.Options o=new PrismaticRibbonPortraitEngine.Options();
            o.mode=ribbonMode(c.style);
            o.detail=d;o.layers=c.colorCount;o.widthMm=w;o.minCutMm=min;o.draft=c.draft;
            // New styles honour both the Prismatic spacing slider and the M4 weed-gap
            // contract. Diagonal keeps its historical spacing-only wiring.
            o.gapMm=Math.max(c.prismaticGapMm,StyleCutGeometryPreflight.exportGapMm(c.gapMm,min));
            o.irregularity=c.prismaticIrregularity;
            o.roundness=c.prismaticRoundness;o.featureEmphasis=c.prismaticFeature;
            o.toneBalance=c.prismaticTone;
            o.palette=colors;o.paperArgb=c.paper;o.allowed=c.allowed;
            result=PrismaticRibbonPortraitEngine.generate(pixels,width,height,o);
        }else if(c.style>=MARQUETRY&&c.style<=ARCHITECTURAL){
            ExpandedPortraitStylesEngine.Options o=new ExpandedPortraitStylesEngine.Options();
            o.style=c.style-MARQUETRY;o.variant=c.variant;o.detail=d;o.colorCount=c.colorCount;
            // These styles draw ribbons on the photo's pixel grid. Aim one grid
            // pixel above the cut minimum and weed gap so pixel rounding can
            // never leave a ribbon or gap below the physical limits.
            final int fold=Math.max(1,(int)Math.ceil(Math.max(width,height)/(double)StyleKit.Source.MAX_SIDE));
            final float gridMm=w/((width+fold-1)/fold);
            final float gap=StyleCutGeometryPreflight.exportGapMm(c.gapMm,min);
            o.widthMm=w;o.minimumMm=Math.min(4f,min+gridMm);
            o.gapMm=Math.min(4f,Math.max(Math.max(.18f,min*.70f),gap+gridMm));
            o.weightMm=Math.max(o.minimumMm,Float.isFinite(c.weightMm)?c.weightMm:.7f);
            o.palette=colors;o.paperArgb=c.paper;o.allowed=c.allowed;
            result=ExpandedPortraitStylesEngine.generate(pixels,width,height,o);
        }else switch(c.style){
            case LOW_POLY:{
                LowPolyDelaunayEngine.Options o=new LowPolyDelaunayEngine.Options();
                o.widthMm=w;o.layerCount=c.colorCount;o.palette=colors;o.paperArgb=c.paper;o.allowed=c.allowed;
                o.minSpacingMm=3.2f-1.6f*f;o.maxSpacingMm=15f-8f*f;
                o.maxPoints=300+(int)(2400*f);
                // Facets are inset by half the gap each: keep a full weed gap.
                o.gapMm=Math.max(Math.max(.20f,min*.45f),StyleCutGeometryPreflight.exportGapMm(c.gapMm,min)+.06f);
                o.mergeSameColor=false;result=LowPolyDelaunayEngine.generate(pixels,width,height,o);break;
            }
            case GLASS:{
                StainedGlassEngine.Options o=new StainedGlassEngine.Options();
                o.widthMm=w;o.glassColors=colors==null?Math.max(2,c.colorCount-1):colors.length;
                if(o.glassColors>7)throw new IllegalArgumentException("Lead plus glass exceeds eight layers");
                o.palette=colors;o.paperArgb=c.paper;o.allowed=c.allowed;
                o.minSpacingMm=4.5f-1.55f*f;
                o.maxPieces=160+(int)(1000*f);o.leadMm=Math.max(.60f,c.leadMm);
                o.leadMode=StainedGlassEngine.LeadMode.UNDERLAY;
                o.leadArgb=c.leadColor;o.saturationBoost=0f;
                o.minPieceAreaMm2=Math.max(3f,min*min*5f);
                result=StainedGlassEngine.generate(pixels,width,height,o);break;
            }
            case TSP:{
                TspArtEngine.Options o=new TspArtEngine.Options();o.widthMm=w;
                o.pointCount=600+(int)(5300*f);o.timeBudgetMs=2500;
                // A cut-material profile may demand a wider line than the UI default.
                // Reconcile the requested stroke to that floor; never lower the floor.
                o.minStrokeMm=min;
                o.strokeMm=Math.max(min,Math.max(.5f,Float.isFinite(c.weightMm)?c.weightMm:.7f));
                o.ribbon=c.variant!=0;
                o.inkArgb=colors!=null?colors[0]:0xff111111;
                o.paperArgb=c.paper;o.allowed=c.allowed;
                result=TspArtEngine.generate(pixels,width,height,o);break;
            }
            case LINOCUT:{
                LinocutEngine.Options o=new LinocutEngine.Options();o.widthMm=w;
                o.gapMm=StyleCutGeometryPreflight.exportGapMm(c.gapMm,min);
                o.minWidthMm=min+.08f; // ribbon sampling margin above the cut minimum
                o.pitchMm=Math.max(Math.max(Math.max(min*2.2f, 1.0f),o.minWidthMm+o.gapMm*1.6f),3.35f-2.35f*f);
                // The Adjust > Line weight slider must change cut geometry,
                // subject to the selected minimum cut width and spacing.
                o.maxWidthFraction=StyleKit.clampF(.47f+.24f*c.weightMm,.28f,.86f);
                o.flowSmoothMm=Math.max(2.1f,4.2f-1.6f*f);
                o.cutoff=0.11f;
                o.minLengthMm=2.1f;
                // M5 relief polarity: solid carved shadows, halo-free balanced stroke spacing.
                o.shadowCarve=true;o.balancedSpacing=true;o.featureContrast=.8f;
                o.crossPass=c.variant!=0;
                o.inkArgb=colors!=null?colors[0]:0xff15151a;
                o.crossArgb=colors!=null&&colors.length>1?colors[1]:o.inkArgb;
                o.paperArgb=c.paper;o.allowed=c.allowed;
                result=LinocutEngine.generate(pixels,width,height,o);break;
            }
            case TRUCHET:{
                TruchetTilesEngine.Options o=new TruchetTilesEngine.Options();o.widthMm=w;
                o.cellMm=11f-7f*f;o.minWidthMm=min;
                o.pattern=TruchetTilesEngine.Pattern.values()[Math.min(Math.max(0,c.variant),TruchetTilesEngine.Pattern.values().length-1)];
                o.inkArgb=colors!=null?colors[0]:0xff17181d;
                o.paperArgb=c.paper;o.allowed=c.allowed;
                result=TruchetTilesEngine.generate(pixels,width,height,o);break;
            }
            case TEXT:{
                TextHalftoneEngine.Options o=new TextHalftoneEngine.Options();o.widthMm=w;
                // 5x7 vinyl characters need >= minStroke for each square-pixel stroke.
                // Keep the grid dense enough to recognize faces but physically weedable.
                o.cellMm=Math.max(Math.max(3.35f, 6.7f-3.35f*f), min*7f/.92f*1.03f);
                o.minStrokeMm=min;
                o.mode=c.variant==0?TextHalftoneEngine.Mode.RAMP:TextHalftoneEngine.Mode.WORDS;
                // Append-only variant 2: new Knockout. Older saved Word variant 1
                // remains Mosaic, ASCII variant 0 remains loadable.
                o.wordStyle=c.variant==2?TextHalftoneEngine.WordStyle.KNOCKOUT:
                    TextHalftoneEngine.WordStyle.MOSAIC;
                o.text=c.words==null||c.words.trim().isEmpty()?"VINYL STUDIO":c.words;
                o.edgeAware=true;o.orderedDither=true;
                o.silhouetteUnderlay=true; // slot 1 is the filled Snip/crop underlay; total slots remain 2..8
                o.palette=colors;o.colorLayers=colors==null?c.colorCount:colors.length;
                o.paperArgb=c.paper;o.allowed=c.allowed;
                result=TextHalftoneEngine.generate(pixels,width,height,o);break;
            }
            case TOPOGRAPHIC:{
                TopographicLayersEngine.Options o=new TopographicLayersEngine.Options();o.widthMm=w;
                o.mode=c.variant==0?TopographicLayersEngine.Mode.LAYERS:TopographicLayersEngine.Mode.LINES;
                o.levels=Math.min(c.colorCount,8);o.smoothMm=2.0f-1.2f*f;
                o.minAreaMm2=Math.max(3f,min*min*6f);
                // Contour ribbons must clear the cut minimum.
                o.ribbon=true;o.lineMm=Math.max(Math.max(.5f,min+.1f),c.weightMm);o.indexLineMm=o.lineMm;
                o.paperArgb=c.paper;o.allowed=c.allowed;
                if(colors!=null){
                    if(colors.length!=o.levels)throw new IllegalArgumentException("Topographic palette count mismatch");
                    o.palette=colors.clone();
                }
                result=TopographicLayersEngine.generate(pixels,width,height,o);break;
            }
            case RIDGE:{
                RidgeLinesEngine.Options o=new RidgeLinesEngine.Options();o.widthMm=w;
                o.mode=c.variant==0?RidgeLinesEngine.Mode.RIDGE:RidgeLinesEngine.Mode.SINE;
                // Ribbons must clear the cut minimum, and the hidden-line
                // clearance is vertical, so allow for sloped ridges.
                final float ridgeGap=StyleCutGeometryPreflight.exportGapMm(c.gapMm,min);
                o.ribbonMm=Math.min(3f,Math.max(Math.max(.5f,min+.1f),c.weightMm));
                o.gapMm=Math.max(Math.max(.2f,min*.5f),ridgeGap*1.6f+.05f);
                float ridgeHeight=w*height/(float)width-8f;
                int fitLines=(int)Math.floor(ridgeHeight/(o.ribbonMm+o.gapMm+.25f));
                o.lineCount=Math.max(10,Math.min(24+(int)(100*f),fitLines));
                o.inkArgb=colors!=null?colors[0]:0xff0e0e12;
                o.paperArgb=c.paper;o.allowed=c.allowed;
                result=RidgeLinesEngine.generate(pixels,width,height,o);break;
            }
            case BANKNOTE:{
                BanknoteEngravingEngine.Options o=new BanknoteEngravingEngine.Options();
                o.widthMm=w;o.variant=c.variant;
                if(o.variant<0||o.variant>=BanknoteEngravingEngine.VARIANTS.length)
                    throw new IllegalArgumentException("Unknown banknote variant");
                // Detail maps to line density, constrained by the physical minimum and weed gap.
                float diagonal=(float)Math.hypot(w,w*height/(float)width);
                o.pitchMm=Math.max(min+0.35f,diagonal/(55f+95f*f));
                o.minMm=Math.max(.254f,Float.isFinite(c.minimumMm)?c.minimumMm:.254f);
                o.gapMm=Math.max(.24f,min*.70f);
                o.pitchMm=Math.max(o.pitchMm,o.minMm+o.gapMm+.24f);
                o.warpMm=.18f*o.pitchMm;o.weightMm=Math.max(min,Float.isFinite(c.weightMm)?c.weightMm:.7f);
                o.angleDeg=35f;o.stepMm=.4f;o.minRunMm=Math.max(1.4f,o.minMm*3f);
                o.crossEtch=c.variant==BanknoteEngravingEngine.CROSS_ETCH||
                    c.variant==BanknoteEngravingEngine.TWO_INK;
                o.vignette=c.variant==BanknoteEngravingEngine.VIGNETTE;
                o.dashHighlights=c.variant==BanknoteEngravingEngine.HIGHLIGHT_DASH;
                o.inkArgb=colors!=null?colors[0]:0xff204438;
                o.crossArgb=colors!=null&&colors.length>1?colors[1]:0xff151515;
                o.paperArgb=c.paper;o.allowed=c.allowed;
                result=BanknoteEngravingEngine.generate(pixels,width,height,o);break;
            }
            case TESSELLATION_MOTIF:{
                TessellationMotifEngine.Options o=new TessellationMotifEngine.Options();
                o.widthMm=w;o.minimumMm=min;
                // Tiles are inset by half the gap on each side: keep a full
                // weed gap (plus rounding margin) between neighbouring tiles.
                o.gapMm=Math.max(Math.max(.18f,min*.60f),StyleCutGeometryPreflight.exportGapMm(c.gapMm,min)+.06f);
                o.pattern=c.variant;o.detail=d;
                o.extraMotifs=false; // no hidden motif-overlay sheet
                o.layerCount=colors==null?c.colorCount:colors.length;
                if(colors!=null)o.palette=colors;
                o.paperArgb=c.paper;o.allowed=c.allowed;
                result=TessellationMotifEngine.generate(pixels,width,height,o);break;
            }
            default:throw new IllegalArgumentException("Unknown style");
        }
        if(c.style==TSP){
            if(c.variant!=0){
                boolean cutReady=false;
                for(StyleKit.Layer layer:result.layers)if(!layer.loops.isEmpty())cutReady=true;
                if(!cutReady)throw new IllegalArgumentException("TSP vinyl ribbon could not be made cut-safe. Choose Pen output or reduce Detail.");
                // The centerline must not be exported onto a separate blade-cut sheet.
                for(StyleKit.Layer layer:result.layers)layer.lines.clear();
            }else{
                for(StyleKit.Layer layer:result.layers)layer.loops.clear();
            }
        }
        if(c.style==TOPOGRAPHIC && colors!=null){
            for(int i=0;i<result.layers.size();i++){
                int slot=NewStyleColorSlots.slot(c.style,result.layers.get(i).name,i);
                result.layers.get(i).color=colors[Math.min(colors.length-1,slot)];
            }
        }
        if(c.style==LOW_POLY||c.style==GLASS){
            StyleKit.Source src=new StyleKit.Source(pixels,width,height,c.allowed,c.widthMm);
            boolean restricted=false;for(boolean b:src.valid)if(!b){restricted=true;break;}
            if(restricted){
                if(c.style==GLASS && !result.layers.isEmpty()){
                    // Replace full-rectangle lead with subject silhouette, including holes.
                    StyleKit.Layer lead=result.layers.get(0);lead.loops.clear();
                    lead.loops.addAll(subjectLoops(src));
                }
                // Clip ONLY boundary-crossing facets; do not discard all of a valid pane.
                // This is a raster-mask intersection reconstructed as closed, subpixel
                // isolines, including disconnected pieces and holes. The original exact
                // polygons are retained wherever entirely inside the allowed silhouette.
                for(StyleKit.Layer layer:result.layers){
                    if(c.style==GLASS&&layer==result.layers.get(0))continue;
                    ArrayList<float[]> clipped=new ArrayList<>();
                    for(float[] poly:layer.loops){
                        StyleKit.checkCancelled();
                        if(fullyInside(poly,src))clipped.add(poly);
                        else clipped.addAll(MaskedFacetClipper.intersect(poly,src));
                    }
                    layer.loops.clear();layer.loops.addAll(clipped);
                }
            }
        }
        if(c.style!=TSP&&c.style!=TEXT&&!organicCutGeometry(c.style)){
            // Exact geometric styles: a facet, tile or clipped edge piece
            // narrower than the cut minimum can be neither cut nor weeded.
            for(StyleKit.Layer layer:result.layers){
                if(c.style==GLASS&&"lead".equals(layer.name))continue;
                // A locked Prismatic sheet has already been filtered as a
                // complete polygon with its holes. Removing individual loops
                // here could fill a hole or change the validated topology.
                if(isPrismaticFamily(c.style)&&layer.vectorGeometryLocked)continue;
                layer.loops.removeIf(loop->VinylEngine.tinyPiece(loop,min));
            }
        }
        result.layers.removeIf(layer->layer.loops.isEmpty()&&layer.lines.isEmpty());
        if(c.style==GLASS){
            int actualPanes=0;
            for(StyleKit.Layer layer:result.layers)
                if(!"lead".equals(layer.name))actualPanes+=layer.loops.size();
            final int maxPanes=160+(int)(1000*f);
            if(actualPanes==0||actualPanes>maxPanes)
                throw new IllegalArgumentException("Glass panel count is not cuttable ("+actualPanes+"); reduce Detail or Lead thickness, or enlarge artwork");
        }
        if(result.layers.size()>(c.style==POP_PANELS?36:(isCraft(c.style)||c.style==POSTER_STENCIL)?9:8))
            throw new IllegalStateException("Exceeded style-specific physical layer capacity");
        for(StyleKit.Layer layer:result.layers){
            if(layer.loops.size()>5000)throw new IllegalStateException("Layer "+layer.name+" exceeds 5000 cut pieces");
            for(float[] poly:layer.loops){
                if(poly.length<6 || (poly.length&1)!=0)throw new IllegalStateException("Malformed polygon");
                for(float v:poly)if(!Float.isFinite(v))throw new IllegalStateException("Non-finite polygon coordinate");
            }
        }
        return result;
    }
    /**
     * Physical cut preparation for export. Returns a copy whose sheets pass the
     * export inspector wherever a local repair can achieve it: sheets with no
     * finding are shared unchanged; raster-derived organic sheets are repaired
     * whole; exact geometric sheets only around flagged pieces. Pen-only
     * output (TSP line) is never altered. The preview Result is not modified.
     */
    /**
     * M4 gap contract: the one weed-gap value for a suite artwork. It is passed to
     * generation ({@link Config#gapMm}) and used by the cut-ready stage and the export
     * inspection. Engines that read Config.gapMm design to it; some keep their own
     * spacing rules (Banknote, Prismatic, Truchet, Text, Topographic, TSP, Glass,
     * Torn Collage, Crochet, Confetti) and are still held to it by the cut-ready stage.
     * Build 558 generated every suite style at a fixed 0.5 mm while the cut-ready
     * stage and export used the material's bridge (0.25-0.45 mm presets, or a custom
     * value), so non-default materials were designed to one gap and repaired to another.
     */
    public static float generationGapMm(float bridgeMm,float minimumMm){
        return StyleCutGeometryPreflight.exportGapMm(bridgeMm,cutMinimumMm(minimumMm));
    }
    static float cutMinimumMm(float minimumMm){
        return Math.max(VinylEngine.MINIMUM_CUT_WIDTH_MM,Float.isFinite(minimumMm)?minimumMm:VinylEngine.MINIMUM_CUT_WIDTH_MM);
    }
    public static StyleKit.Result prepareForCutting(StyleKit.Result source,int style,float minimumMm,float bridgeMm){
        return prepareForCutting(source,style,minimumMm,bridgeMm,PREPARE_BUDGET_WORK);
    }
    static StyleKit.Result prepareForCutting(StyleKit.Result source,int style,float minimumMm,float bridgeMm,long workBudget){
        if(source==null)return null;
        final float min=cutMinimumMm(minimumMm);
        final float gap=generationGapMm(bridgeMm,minimumMm);
        StyleKit.Result out=new StyleKit.Result(source.widthMm,source.heightMm);
        out.paperArgb=source.paperArgb;out.summary=source.summary;
        final boolean organic=organicCutGeometry(style);
        // M5: the budget is workload (vertices submitted to conditioning), never wall clock, so a
        // fast and a slow device prepare exactly the same sheets and produce identical artwork.
        long preparedWork=0;
        for(StyleKit.Layer layer:source.layers){
            StyleKit.checkCancelled();
            StyleKit.Layer copy=new StyleKit.Layer(layer.name,layer.color);
            copy.strokeMm=layer.strokeMm;copy.vectorGeometryLocked=layer.vectorGeometryLocked;
            copy.loops.addAll(layer.loops);copy.lines.addAll(layer.lines);
            boolean leadSheet=style==GLASS&&"lead".equals(layer.name);
            // Past the work budget, remaining sheets keep their generated
            // geometry; the export review still reports any finding.
            if(!leadSheet&&!copy.vectorGeometryLocked&&copy.lines.isEmpty()&&preparedWork<workBudget){
                preparedWork+=layerWork(copy.loops);
                conditionForCutting(out,copy,min,gap,!organic);
            }
            out.layers.add(copy);
        }
        return out;
    }
    /** Upper bound on export-time cut preparation for one artwork, in vertices (plus a per-loop
     *  overhead) submitted to conditioning. Deterministic: depends only on the generated geometry.
     *  Replaces the old 45 s wall-clock cap; it is a runaway bound that
     *  realistic artwork (layers over 2000 loops are skipped anyway) does not reach. */
    static final long PREPARE_BUDGET_WORK=2_500_000L;
    static long layerWork(List<float[]> loops){
        long w=0;for(float[] l:loops)w+=l.length/2+8;return w;
    }
    /** Styles whose pieces are traced from photo rasters (no exact motif geometry). */
    static boolean organicCutGeometry(int style){
        return isConfetti(style)||style==CHAINMAIL||isCraft(style)||isOrganicFlow(style)||isPopPoster(style)||
            (style>=MARQUETRY&&style<=TORN_COLLAGE);
    }
    /** Replaces a sheet's loops with the conditioned equivalent when the
     * classified export inspection finds a vinyl neck, speck or weed gap. */
    static void conditionForCutting(StyleKit.Result result,StyleKit.Layer layer,float minimumMm,float gapMm){
        conditionForCutting(result,layer,minimumMm,gapMm,false);
    }
    static void conditionForCutting(StyleKit.Result result,StyleKit.Layer layer,float minimumMm,float gapMm,boolean local){
        if(layer.loops.isEmpty())return;
        // A very dense layer should retain source geometry and surface its
        // existing cut-review warnings rather than trigger expensive N^2 area
        // comparisons and potentially exceed the Android heap.
        if(layer.loops.size()>2000)return;
        List<StyleCutGeometryPreflight.Warning> found;
        try{found=inspectLayer(result,layer.loops,minimumMm,gapMm,local?5000:1);}
        catch(IllegalArgumentException bad){
            // Invalid/self-crossing source paths are not safe inputs for a
            // raster repair. Preserve them for the final fail-closed review.
            return;
        }
        if(found.isEmpty())return;
        if(local&&!incomplete(found)){
            ArrayList<float[]> keep=new ArrayList<>(),repair=new ArrayList<>();
            splitAroundFindings(layer.loops,found,2f*Math.max(minimumMm,gapMm),keep,repair);
            ArrayList<float[]> fixedPart;
            try{fixedPart=CutGeometryConditioner.condition(repair,result.widthMm,result.heightMm,minimumMm,gapMm);}
            catch(CutGeometryConditioner.BudgetExceededException budget){return;}
            if(!preservesArtwork(repair,fixedPart))return; // keep original; export review reports it
            ArrayList<float[]> merged=new ArrayList<>(keep);
            merged.addAll(fixedPart);
            boolean clean;
            try{clean=inspectLayer(result,merged,minimumMm,gapMm,1).isEmpty();}
            catch(IllegalArgumentException bad){clean=false;}
            if(clean){layer.loops.clear();layer.loops.addAll(merged);return;}
        }
        ArrayList<float[]> repaired;
        try{repaired=CutGeometryConditioner.condition(layer.loops,result.widthMm,result.heightMm,minimumMm,gapMm);}
        catch(CutGeometryConditioner.BudgetExceededException budget){return;}
        if(!preservesArtwork(layer.loops,repaired))return; // keep original; export review reports it
        layer.loops.clear();layer.loops.addAll(repaired);
    }
    /** Conditioning repairs local defects. If it would erase a quarter of the
     * vinyl, the style itself is too fine for the chosen cut settings; keep
     * the artwork and let the export review explain, never silently erase. */
    static boolean preservesArtwork(List<float[]> before,List<float[]> after){
        double a=evenOddArea(before),b=evenOddArea(after);
        // Both extensive removal and excessive filling are material changes
        // to the artist's layer; do not silently accept either. Export review
        // remains available for the unmodified source geometry.
        return Double.isFinite(a)&&Double.isFinite(b)&&a>0&&b>0
            &&b>=a*.75&&b<=a*1.25;
    }
    private static double evenOddArea(List<float[]> loops){
        // Loops are simple and non-crossing; nesting depth alternates sign.
        int n=loops.size();float[][] box=new float[n][];
        for(int i=0;i<n;i++){
            float[] p=loops.get(i);float a=Float.POSITIVE_INFINITY,c=a,b2=Float.NEGATIVE_INFINITY,d=b2;
            for(int k=0;k+1<p.length;k+=2){a=Math.min(a,p[k]);b2=Math.max(b2,p[k]);c=Math.min(c,p[k+1]);d=Math.max(d,p[k+1]);}
            box[i]=new float[]{a,c,b2,d};
        }
        double total=0;
        for(int i=0;i<n;i++){
            if((i&63)==0)StyleKit.checkCancelled();
            float[] p=loops.get(i);float x=p[0],y=p[1];
            int depth=0;
            for(int j=0;j<n;j++){
                if(j==i||x<box[j][0]||x>box[j][2]||y<box[j][1]||y>box[j][3])continue;
                if(MaskedFacetClipper.inside(loops.get(j),x,y))depth++;
            }
            total+=(depth%2==0?1:-1)*Math.abs(StyleKit.signedArea(p));
        }
        return total;
    }
    private static List<StyleCutGeometryPreflight.Warning> inspectLayer(StyleKit.Result result,List<float[]> loops,float minimumMm,float gapMm,int limit){
        StyleKit.Result one=new StyleKit.Result(result.widthMm,result.heightMm);
        StyleKit.Layer copy=new StyleKit.Layer("inspect",0xff000000);
        copy.loops.addAll(loops);one.layers.add(copy);
        return StyleCutGeometryPreflight.inspect(one,minimumMm,gapMm,limit);
    }
    private static boolean incomplete(List<StyleCutGeometryPreflight.Warning> found){
        for(StyleCutGeometryPreflight.Warning w:found)
            if(w.reason.contains("not verified")||w.reason.contains("complexity"))return true;
        return found.size()>=5000;
    }
    /** Loops whose padded bounds contain a finding, plus every loop whose
     * bounds come within {@code pad} of those (neighbours across a gap, and
     * outlines enclosing a flagged hole), go to {@code repair}. */
    private static void splitAroundFindings(List<float[]> loops,List<StyleCutGeometryPreflight.Warning> found,float pad,
                                            List<float[]> keep,List<float[]> repair){
        int n=loops.size();float[][] box=new float[n][];
        for(int i=0;i<n;i++){
            float[] p=loops.get(i);float a=Float.POSITIVE_INFINITY,b=a,c2=Float.NEGATIVE_INFINITY,d2=c2;
            for(int k=0;k+1<p.length;k+=2){a=Math.min(a,p[k]);c2=Math.max(c2,p[k]);b=Math.min(b,p[k+1]);d2=Math.max(d2,p[k+1]);}
            box[i]=new float[]{a,b,c2,d2};
        }
        boolean[] hit=new boolean[n];
        for(StyleCutGeometryPreflight.Warning w:found){
            float x=w.midXMm,y=w.midYMm;
            for(int i=0;i<n;i++)
                if(x>=box[i][0]-pad&&x<=box[i][2]+pad&&y>=box[i][1]-pad&&y<=box[i][3]+pad)hit[i]=true;
        }
        boolean[] take=hit.clone();
        for(int i=0;i<n;i++){
            if(!hit[i])continue;
            for(int j=0;j<n;j++){
                if(take[j])continue;
                if(box[j][0]<=box[i][2]+pad&&box[i][0]<=box[j][2]+pad&&box[j][1]<=box[i][3]+pad&&box[i][1]<=box[j][3]+pad)take[j]=true;
            }
        }
        // Keep nesting families together: an outline enclosing any taken loop.
        for(int i=0;i<n;i++){
            if(take[i])continue;
            for(int j=0;j<n;j++)if(take[j]&&box[i][0]<=box[j][0]&&box[i][1]<=box[j][1]&&box[i][2]>=box[j][2]&&box[i][3]>=box[j][3]){take[i]=true;break;}
        }
        for(int i=0;i<n;i++)(take[i]?repair:keep).add(loops.get(i));
    }
    static ArrayList<float[]> subjectLoops(StyleKit.Source src){
        int w=src.w,h=src.h,pw=w+2,ph=h+2;
        float[] field=new float[pw*ph];Arrays.fill(field,-1f);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(src.valid[y*w+x])field[(y+1)*pw+x+1]=1f;
        return TopographicLayersEngine.contours(field,pw,ph,0f,src.mmPerPx);
    }
    static boolean fullyInside(float[] poly,StyleKit.Source src){
        if(poly.length<6)return false;
        // Vertices and densely sampled edges check against subject exclusion,
        // including small notches smaller than the polygon bounding region.
        int n=poly.length/2;float px=src.mmPerPx;
        for(int i=0;i<n;i++){
            float ax=poly[2*i],ay=poly[2*i+1],bx=poly[2*((i+1)%n)],by=poly[2*((i+1)%n)+1];
            float len=(float)Math.hypot(bx-ax,by-ay);
            int steps=Math.max(1,(int)Math.ceil(len/Math.max(.01f,px*.40f)));
            for(int s=0;s<=steps;s++){
                float t=s/(float)steps;
                if(!src.validAt(ax+(bx-ax)*t,ay+(by-ay)*t))return false;
            }
        }
        float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
        for(int i=0;i<poly.length;i+=2){minX=Math.min(minX,poly[i]);maxX=Math.max(maxX,poly[i]);minY=Math.min(minY,poly[i+1]);maxY=Math.max(maxY,poly[i+1]);}
        int x0=Math.max(0,(int)Math.floor(minX/px)),x1=Math.min(src.w-1,(int)Math.ceil(maxX/px));
        int y0=Math.max(0,(int)Math.floor(minY/px)),y1=Math.min(src.h-1,(int)Math.ceil(maxY/px));
        for(int y=y0;y<=y1;y++){
            if((y&31)==0)StyleKit.checkCancelled();
            for(int x=x0;x<=x1;x++)if(!src.valid[y*src.w+x]){
                float xx=(x+.5f)*px,yy=(y+.5f)*px;
                if(pointInPolygon(poly,xx,yy))return false;
            }
        }
        return true;
    }
    private static boolean pointInPolygon(float[] p,float x,float y){
        int n=p.length/2;boolean inside=false;
        for(int i=0,j=n-1;i<n;j=i++){
            float ax=p[2*i],ay=p[2*i+1],bx=p[2*j],by=p[2*j+1];
            if((ay>y)!=(by>y)&&x<(bx-ax)*(y-ay)/(by-ay)+ax)inside=!inside;
        }
        return inside;
    }
}

package com.halftonestudio;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Stable UI-only artwork identity. Never use these keys as persisted style IDs. */
public final class StudioThumbnailRegistry {
    private StudioThumbnailRegistry() { }

    public static final String[] MULTILAYER = {
        "multilayer.tonal", "multilayer.voronoi", "multilayer.grid",
        "multilayer.layered_cells", "multilayer.offset_cells", "multilayer.bottle_caps",
        "multilayer.fish_scales", "multilayer.weighted_stipple", "multilayer.low_poly",
        "multilayer.stained_glass", "multilayer.topographic", "multilayer.tessellation",
        "multilayer.prismatic_diagonal", "multilayer.confetti_mixed",
        "multilayer.chainmail_linked", "multilayer.confetti_round",
        "multilayer.confetti_square", "multilayer.confetti_paper",
        "multilayer.confetti_streamer", "multilayer.confetti_fallen",
        "multilayer.camouflage", "multilayer.fishnet",
        "multilayer.crochet",
        "multilayer.camo_desert", "multilayer.camo_urban", "multilayer.camo_digital",
        "multilayer.quilling_ribbons", "multilayer.marbled_ink", "multilayer.quilled_marbling",
        "multilayer.pop_panels", "multilayer.poster_stencil",
        "multilayer.overprint_orbits", "multilayer.overprint_lozenge",
        "multilayer.overprint_diamonds", "multilayer.overprint_mixed",
        "multilayer.prismatic_contour_flow", "multilayer.prismatic_twill",
        "multilayer.prismatic_silk", "multilayer.prismatic_braided_flow",
        "multilayer.prismatic_chevron"
    };
    public static final String[] SHAPES = {
        "shape.round", "shape.square", "shape.ellipse", "shape.diamond", "shape.hexagon",
        "shape.star", "shape.cross", "shape.triangle", "shape.octagon",
        "shape.rounded_square", "shape.teardrop", "shape.heart", "shape.capsule",
        "shape.dash", "shape.ascii"
    };
    public static final String[] LINES = {
        "line.variable_width", "line.straight", "line.contour", "line.wavy",
        "line.engraving_hatches", "line.crosshatch", "line.zigzag", "line.broken",
        "line.spider_web"
    };
    public static final String[] SINGLE = {
        "single.spiral", "single.hilbert", "single.gosper", "single.peano",
        "single.wunderlich", "single.moore", "single.coil"
    };
    public static final String[] ART = {
        "art.tsp", "art.linocut", "art.truchet", "art.banknote",
        "art.marquetry", "art.fingerprint", "art.reduction_woodcut",
        "art.woven_ribbon", "art.woodgrain", "art.torn_collage"
    };
    public static final int TOP_LEVEL_COUNT = 81, TESSELLATION_COUNT = 28;
    private static final Set<String> KEYS;
    static {
        HashSet<String> keys = new HashSet<>();
        for (String[] family : new String[][]{MULTILAYER, SHAPES, LINES, SINGLE, ART}) {
            for (String key : family) if (!keys.add(key)) throw new IllegalStateException("Duplicate thumbnail key: " + key);
        }
        for (int i=0; i<TessellationMotifEngine.PATTERN_SLOT_COUNT; ++i) {
            if (!TessellationMotifEngine.selectablePattern(i))continue;
            if (!keys.add(tessellationKey(i))) throw new IllegalStateException("Duplicate tessellation key");
        }
        if (keys.size()!=TOP_LEVEL_COUNT+TESSELLATION_COUNT)
            throw new IllegalStateException("Incomplete thumbnail registry: " + keys.size());
        KEYS=Collections.unmodifiableSet(keys);
    }
    public static Set<String> allKeys() { return KEYS; }
    public static boolean isKnown(String key) { return KEYS.contains(key); }
    public static String tessellationKey(int variant) {
        if (!TessellationMotifEngine.selectablePattern(variant)) throw new IllegalArgumentException("Unknown tessellation " + variant);
        return "tess.variant."+(variant<10?"0":"")+variant;
    }
    public static int tessellationVariant(String key) {
        if (key==null || !key.matches("tess\\.variant\\.[0-9]{2}")) return -1;
        int id=Integer.parseInt(key.substring(13));
        return TessellationMotifEngine.selectablePattern(id) ? id : -1;
    }
    public static String specialty(int id) {
        switch (id) {
            case 6:return MULTILAYER[0]; case 8:return MULTILAYER[1];
            case 9:return MULTILAYER[2]; case 10:return MULTILAYER[3];
            case 19:return MULTILAYER[4]; case 20:return MULTILAYER[5];
            case 21:return MULTILAYER[6]; case 18:return MULTILAYER[7];
            case 22:return MULTILAYER[31]; case 23:return MULTILAYER[32];
            case 24:return MULTILAYER[33]; case 25:return MULTILAYER[34];
            default:throw new IllegalArgumentException("Unknown multilayer specialty "+id);
        }
    }
    public static String newStyle(int id) {
        switch (id) {
            case VinylStyleSuite.LOW_POLY:return MULTILAYER[8];
            case VinylStyleSuite.GLASS:return MULTILAYER[9];
            case VinylStyleSuite.TOPOGRAPHIC:return MULTILAYER[10];
            case VinylStyleSuite.TESSELLATION_MOTIF:return MULTILAYER[11];
            case VinylStyleSuite.TSP:return ART[0];
            case VinylStyleSuite.LINOCUT:return ART[1];
            case VinylStyleSuite.TRUCHET:return ART[2];
            case VinylStyleSuite.BANKNOTE:return ART[3];
            case VinylStyleSuite.MARQUETRY:return ART[4];
            case VinylStyleSuite.FINGERPRINT:return ART[5];
            case VinylStyleSuite.REDUCTION_WOODCUT:return ART[6];
            case VinylStyleSuite.WOVEN_RIBBON:return ART[7];
            case VinylStyleSuite.WOODGRAIN:return ART[8];
            case VinylStyleSuite.TORN_COLLAGE:return ART[9];
            case VinylStyleSuite.PRISMATIC_DIAGONAL:return MULTILAYER[12];
            case VinylStyleSuite.PRISMATIC_CONTOUR_FLOW:return MULTILAYER[35];
            case VinylStyleSuite.PRISMATIC_TWILL:return MULTILAYER[36];
            case VinylStyleSuite.PRISMATIC_SILK:return MULTILAYER[37];
            case VinylStyleSuite.PRISMATIC_BRAIDED_FLOW:return MULTILAYER[38];
            case VinylStyleSuite.PRISMATIC_CHEVRON:return MULTILAYER[39];
            case VinylStyleSuite.CONFETTI:return MULTILAYER[13];
            case VinylStyleSuite.CHAINMAIL:return MULTILAYER[14];
            case VinylStyleSuite.CONFETTI_ROUND:return MULTILAYER[15];
            case VinylStyleSuite.CONFETTI_SQUARE:return MULTILAYER[16];
            case VinylStyleSuite.CONFETTI_PAPER:return MULTILAYER[17];
            case VinylStyleSuite.CONFETTI_STREAMER:return MULTILAYER[18];
            case VinylStyleSuite.CONFETTI_FALLEN:return MULTILAYER[19];
            case VinylStyleSuite.CAMOUFLAGE:return MULTILAYER[20];
            case VinylStyleSuite.FISHNET:return MULTILAYER[21];
            case VinylStyleSuite.CROCHET:return MULTILAYER[22];
            case VinylStyleSuite.CAMO_DESERT:return MULTILAYER[23];
            case VinylStyleSuite.CAMO_URBAN:return MULTILAYER[24];
            case VinylStyleSuite.CAMO_DIGITAL:return MULTILAYER[25];
            case VinylStyleSuite.QUILLING_RIBBONS:return MULTILAYER[26];
            case VinylStyleSuite.MARBLED_INK:return MULTILAYER[27];
            case VinylStyleSuite.QUILLED_MARBLING:return MULTILAYER[28];
            case VinylStyleSuite.POP_PANELS:return MULTILAYER[29];
            case VinylStyleSuite.POSTER_STENCIL:return MULTILAYER[30];
            case VinylStyleSuite.SPIDER_WEB:return LINES[8];
            default:throw new IllegalArgumentException("Unknown new style "+id);
        }
    }
    public static String shape(int id) {
        switch (id) {
            case VinylEngine.ROUND:return SHAPES[0]; case VinylEngine.SQUARE:return SHAPES[1];
            case VinylEngine.ELLIPSE:return SHAPES[2];case VinylEngine.DIAMOND:return SHAPES[3];
            case VinylEngine.HEX:return SHAPES[4];case VinylEngine.STAR:return SHAPES[5];
            case VinylEngine.CROSS:return SHAPES[6];case VinylEngine.TRIANGLE:return SHAPES[7];
            case VinylEngine.OCTAGON:return SHAPES[8];case VinylEngine.ROUNDED_SQUARE:return SHAPES[9];
            case VinylEngine.TEARDROP:return SHAPES[10];case VinylEngine.HEART:return SHAPES[11];
            case VinylEngine.CAPSULE:return SHAPES[12];case VinylEngine.DASH:return SHAPES[13];
            case VinylEngine.ASCII:return SHAPES[14];
            default:throw new IllegalArgumentException("Unknown shape style "+id);
        }
    }
    public static String line(int id) {
        switch (id) {
            case VinylEngine.LINE:return LINES[0]; case VinylEngine.STRAIGHT:return LINES[1];
            case VinylEngine.CONTOUR:return LINES[2];case VinylEngine.WAVE:return LINES[3];
            case VinylEngine.HATCH:return LINES[4];case VinylEngine.CROSSHATCH:return LINES[5];
            case VinylEngine.ZIGZAG:return LINES[6];case VinylEngine.BROKEN:return LINES[7];
            default:throw new IllegalArgumentException("Unknown line style "+id);
        }
    }
    public static String single(ContinuousLineModes.LineStyle style) {
        switch (style) {
            case SPIRAL:return SINGLE[0];
            case ADAPTIVE_HILBERT:return SINGLE[1];
            case GOSPER:return SINGLE[2];
            case PEANO:return SINGLE[3];
            case WUNDERLICH:return SINGLE[4];
            case MOORE:return SINGLE[5];
            case COIL:return SINGLE[6];
            default:throw new IllegalArgumentException("Unknown single-line style "+style);
        }
    }
}

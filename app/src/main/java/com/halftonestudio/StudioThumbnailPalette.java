package com.halftonestudio;

/**
 * Deterministic thumbnail-only palettes. This changes neither project swatches nor
 * cutting/export ink assignments. Each preview gets its palette during construction;
 * no Color conversions, random selection or heap allocations occur while drawing.
 *
 * Multilayer/Art/Lines/Tessellation previews use individually selected colorful
 * examples. Shape previews are CMYK-only and every Single Line preview is strict
 * black on white. Keys are identifiers, never display-name heuristics.
 */
public final class StudioThumbnailPalette {
    private StudioThumbnailPalette() {}

    public static final int CYAN=0xff00bce8;
    public static final int MAGENTA=0xfff32691;
    public static final int YELLOW=0xffffdc21;
    public static final int BLACK=0xff111111;
    public static final int WHITE=0xffffffff;

    private static final int[][] MULTI = {
        {0xffffd166,0xffef476f,0xff06d6a0,0xff118ab2,0xff6a4c93}, // tonal
        {0xfff8961e,0xfff94144,0xff43aa8b,0xff577590,0xfff3722c}, // Voronoi
        {0xff00d2d3,0xffff86a5,0xffffde6b,0xff5874d6,0xfff76c32}, // grid
        {0xffe9ff70,0xffbb66ff,0xffff5d8f,0xff31d6c3,0xff5c80e5}, // layered cell
        {0xfffdc14e,0xff45d6af,0xfffe6d73,0xff4885ed,0xff9e6aff}, // offset cell
        {0xffffb627,0xfff72585,0xff4cc9f0,0xff3a86ff,0xff7209b7}, // caps
        {0xff95f985,0xff11adba,0xfffee440,0xffff5178,0xff7b6cf6}, // scales
        {0xffec5eaa,0xff64dfdf,0xffffe16a,0xffaf76d9,0xfff77f5e}, // stipple
        {0xffee6352,0xff08b2e3,0xffffe45c,0xff7dce82,0xffaa78a6}, // low poly
        {0xffffc857,0xffdb3a34,0xff2e86ab,0xff6bcf9f,0xffe07be0}, // glass
        {0xfff5d547,0xfff27f58,0xff65c5c8,0xff8080d5,0xff7fcf83}, // topo
        {0xfffe7f2d,0xff48cae4,0xfff15bb5,0xfffee440,0xff9b5de5}, // tilings
        {0xfff9c74f,0xfff3722c,0xfff72585,0xff43bccd,0xffa177ff}, // prismatic
        {0xffa855f7,0xff3ac9dd,0xffffad48,0xfff15b9d,0xff56d6a4}, // mixed
        {0xfffe6a80,0xffffd166,0xff06d6a0,0xff5289f5,0xff9e7aff}, // chainmail
        {0xffffba49,0xfffb7192,0xff50cee5,0xff67c889,0xffad76ec}, // round
        {0xff39e0cd,0xfffd7c63,0xfffad267,0xffaa6ef6,0xff64aaff}, // square
        {0xfff76b60,0xffe9bc54,0xff5fc1a7,0xffa174d8,0xff52bad2}, // paper
        {0xffbb78fa,0xffee638e,0xffffcc62,0xff6cdfbd,0xff61a7f8}, // streamer
        {0xfff8a45c,0xffda698e,0xff7c5ce5,0xff4fd0b5,0xfffdd35e}, // fallen
        {0xff6d4a35,0xff34735b,0xffd4b05d,0xff444b39,0xff8f7753}, // camouflage
        {0xff1a4662,0xff39a3c1,0xffa3d9d5,0xff3182a9,0xffb6dde5}, // fishnet
        {0xff4f3d67,0xffa37bba,0xfff0d1c7,0xffd8a9a9,0xffc19b75}, // crochet
        {0xff62462c,0xff9c7651,0xffdec38f,0xffe9d8b0,0xffbda178}, // desert
        {0xff20252c,0xff545d67,0xff87929b,0xffc4c9c9,0xff3c4651}, // urban
        {0xff394238,0xff687666,0xffa8a695,0xffd5d0bd,0xff868c79}, // digital
        {0xffc577b1,0xffe2a4b8,0xffffd89e,0xff6dc9c7,0xff8f8bd8}, // Quilling Ribbons
        {0xff1fbec7,0xff5c7ccf,0xffed8fb2,0xffffc66b,0xff8fd7aa}, // Marbled Ink
        {0xffca85d5,0xffef719d,0xffffc66d,0xff66bbd7,0xff8addb5}, // Quilled Marbling
        {0xffff4fa3,0xffffe14d,0xff2bb3e8,0xff141414,0xff7a3cff}, // Pop Panels
        {0xfff1e3c3,0xffa9c7cf,0xffc8312c,0xff1b2a3a,0xff7fa6b4}, // Poster Stencil
        {0xffe3b9aa,0xff713e6b,0xff66b9be,0xffffcf73,0xffe57699}, // Orbits
        {0xff1b4654,0xff63b2aa,0xffefac73,0xffd86470,0xff9961b4}, // Lozenge
        {0xff362d50,0xff7d64ba,0xffe69db6,0xffffd585,0xff73c8c4}, // Diamonds
        {0xff27384a,0xffd7637f,0xffefc064,0xff77cabb,0xff9f83ca}, // Mixed
        {0xfff6e7d0,0xffe0794f,0xff3f8f8b,0xff2f3f73,0xffe6b34a}, // Prismatic Contour Flow
        {0xfff3ecdc,0xff9b3d4f,0xff2c6e8f,0xffd9a441,0xff3f3a52}, // Prismatic Twill
        {0xfffbf1ee,0xffe7a1b0,0xff9ec9e2,0xffc6a6e0,0xff5c5d8f}, // Prismatic Silk
        {0xfff4eadb,0xffcb5a3c,0xff2f8f6f,0xff294b7a,0xffe9c05a}, // Prismatic Braided Flow
        {0xfff7efe2,0xfff08a24,0xff1d7f9e,0xffc2335b,0xff3a2f55} // Prismatic Chevron
    };
    static {
        if(MULTI.length!=StudioThumbnailRegistry.MULTILAYER.length)
            throw new IllegalStateException("Multilayer palette count does not match thumbnail identities");
    }
    private static final int[][] LINES = {
        {0xff42e2f4,0xfff25bb7,0xffffe252,0xffa86bff,0xff68e197},
        {0xffe65595,0xfff1bd4d,0xff4dc5ef,0xff6f93ff,0xffb575eb},
        {0xff63e9c9,0xfff88b4a,0xffd68ef8,0xfff6d74e,0xff65b1ef},
        {0xff22e0ff,0xffff5d99,0xffddf04b,0xff7d79ff,0xff50dfa1},
        {0xffdd6df2,0xffffc44e,0xff4fd9d5,0xfff66f71,0xff79b9fc},
        {0xffffdb5a,0xffb076ed,0xff3bc6ed,0xfff56ea4,0xff62deaa},
        {0xffef6d61,0xff63e0fc,0xfff6cf43,0xffb277f1,0xff60eb89},
        {0xff4ed5ef,0xffff86a5,0xfffeca54,0xff8b7cff,0xff72e3c5},
        {0xff86d7ef,0xfff5b54a,0xffda7beb,0xff85e6a8,0xfff16e86}
    };
    private static final int[][] ART = {
        {BLACK,BLACK,BLACK,BLACK,BLACK},                        // TSP: pen route
        {0xfff7d36a,0xfff37846,0xffd83b65,0xff24233e,0xff78c5b4}, // linocut
        {0xff5ed5e7,0xffe75aaf,0xffffd057,0xff7373eb,0xff50cc87}, // truchet
        {0xffe8bb79,0xffc58d5b,0xff6e523e,0xff312b35,0xfff5e2bb}, // banknote
        {0xffd98a4e,0xfff2be74,0xff5eb2a4,0xff965d80,0xffdc696e}, // marquetry
        {0xfff58aad,0xffcb78c0,0xff64b9d6,0xff475e92,0xfffdd883}, // fingerprint
        {0xffffd45e,0xfff45b69,0xff2b89ab,0xff433270,0xff54c3a2}, // reduction
        {0xfff65b89,0xff5ccbe5,0xfffbd25b,0xff8068df,0xff48c797}, // woven
        {0xfff5b879,0xffa87d5d,0xff67b99d,0xff494a73,0xffd59196}, // woodgrain
        {0xfffbc463,0xffe65e9c,0xff4bd6cb,0xff7468e7,0xffee7958} // torn collage
    };
    // Explicit plate orders ensure every shape has its own CMYK example,
    // including the same four physical inks in 15 different arrangements.
    private static final int[][] SHAPE_ORDERS={
        {0,1,2,3},{1,2,3,0},{2,3,0,1},{3,0,1,2},{0,2,1,3},
        {1,3,2,0},{2,0,3,1},{3,1,0,2},{0,3,1,2},{1,0,2,3},
        {2,1,3,0},{3,2,0,1},{0,1,3,2},{1,2,0,3},{2,3,1,0}
    };
    private static final int[] INKS={CYAN,MAGENTA,YELLOW,BLACK};

    public static boolean isCmyk(int color) {
        return color==CYAN||color==MAGENTA||color==YELLOW||color==BLACK||color==WHITE;
    }

    /** Only ink colors; UI background is handled separately. */
    public static int[] colors(String key) {
        int n=indexOf(StudioThumbnailRegistry.MULTILAYER,key);
        if(n>=0) return MULTI[n];
        n=indexOf(StudioThumbnailRegistry.SHAPES,key);
        if(n>=0) {
            int[] order=SHAPE_ORDERS[n];
            return new int[]{INKS[order[0]],INKS[order[1]],INKS[order[2]],
                INKS[order[3]],WHITE};
        }
        n=indexOf(StudioThumbnailRegistry.LINES,key);
        if(n>=0)return LINES[n];
        n=indexOf(StudioThumbnailRegistry.SINGLE,key);
        if(n>=0)return new int[]{BLACK,BLACK,BLACK,BLACK,BLACK};
        n=indexOf(StudioThumbnailRegistry.ART,key);
        if(n>=0)return ART[n];
        if(key!=null&&key.startsWith("maze."))return new int[]{BLACK,BLACK,BLACK,BLACK,BLACK};
        n=StudioThumbnailRegistry.tessellationVariant(key);
        if(n>=0) {
            // A distinct harmonious 5-hue set for each active geometric layout.
            float seed=(n*47f+184f)%360f;
            return new int[]{hsl(seed,.91f,.69f),hsl(seed+58,.84f,.62f),
                hsl(seed+138,.81f,.65f),hsl(seed+212,.73f,.60f),
                hsl(seed+294,.89f,.72f)};
        }
        throw new IllegalArgumentException("Unknown thumbnail palette: "+key);
    }
    public static int background(String key) {
        if(key!=null&&key.startsWith("maze."))return 0xfff2dfb8;
        if(indexOf(StudioThumbnailRegistry.SINGLE,key)>=0)return WHITE;
        if(indexOf(StudioThumbnailRegistry.SHAPES,key)>=0)return WHITE;
        if("art.tsp".equals(key))return WHITE;
        if("art.banknote".equals(key))return 0xfffff2da;
        if("art.linocut".equals(key))return 0xff1a172c;
        if("art.woodgrain".equals(key))return 0xff2f2935;
        if("art.marquetry".equals(key))return 0xff332635;
        return 0xff152036;
    }
    public static int outline(String key) {
        if(key!=null&&key.startsWith("maze."))return BLACK;
        if(indexOf(StudioThumbnailRegistry.SHAPES,key)>=0||
            indexOf(StudioThumbnailRegistry.SINGLE,key)>=0||"art.tsp".equals(key))return BLACK;
        if("art.banknote".equals(key))return 0xff29202a;
        return 0xff172035;
    }
    private static int indexOf(String[] keys,String key) {
        for(int i=0;i<keys.length;i++)if(keys[i].equals(key))return i;
        return -1;
    }
    // Android-independent HSL conversion; computed only during tile construction.
    private static int hsl(float hue,float saturation,float lightness) {
        hue=(hue%360f+360f)%360f;
        float chroma=(1f-Math.abs(2f*lightness-1f))*saturation;
        float x=chroma*(1f-Math.abs((hue/60f)%2f-1f));
        float r=0,g=0,b=0;
        if(hue<60){r=chroma;g=x;}
        else if(hue<120){r=x;g=chroma;}
        else if(hue<180){g=chroma;b=x;}
        else if(hue<240){g=x;b=chroma;}
        else if(hue<300){r=x;b=chroma;}
        else{r=chroma;b=x;}
        float m=lightness-chroma/2f;
        return 0xff000000|Math.round(255f*(r+m))<<16|Math.round(255f*(g+m))<<8|Math.round(255f*(b+m));
    }
}

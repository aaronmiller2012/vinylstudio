package com.halftonestudio;

/** Carefully ordered opaque-vinyl palette presets. Each has eight distinct swatches.
 * The first N swatches are used for a 2–8 layer project; the paper remains exposed.
 * These are illustrative RGB swatches, not calibrated manufacturer vinyl colors.
 */
public final class VinylPalettes {
    private VinylPalettes(){}
    public static final String[] NAMES={"Portrait Vinyl","Retro Sunset","Ocean","Pop Art","Forest","Neon Night","Earth & Rose","Monochrome","Desert Clay","Arctic Blue","Vintage Print","Wildflower",
        "Signal Primary","Jewel Cabinet","Soft Sorbet","Copper Patina",
        "Acid Citrus","Winter Cardinal","Urban Concrete",
        // M4: appended only. IDs 0-18 are stored in saved projects and never move.
        "Natural Skin","Graphic Noir","Faded Kodachrome","Nordic Modern","Terra & Moss",
        "Muted Gallery","Riso Pop","Indigo Study","Teal & Coral","Chiaroscuro"};
    private static final int[][] COLORS={
        {0xff25252a,0xffcc6489,0xfff2b6b8,0xff88bed1,0xfff0ce74,0xff624972,0xff699486,0xffd68d5a},
        {0xff1e3453,0xffe7704f,0xffefbd58,0xff7b566a,0xff71b9ba,0xfff8e9d1,0xffac4e4b,0xffb8a0cf},
        {0xff0d2c43,0xff0787a1,0xff56b7c8,0xffc3e3df,0xff477aa7,0xff54a68c,0xffd4a8bb,0xfff0d59a},
        {0xff222329,0xffe6387b,0xffedc63b,0xff11a5c9,0xff694bcb,0xffed804f,0xff56b886,0xfff6b5db},
        {0xff173d36,0xff62836a,0xffafb987,0xffe8d7a5,0xff456b89,0xffa86e5a,0xffc59fb0,0xff89b8ae},
        {0xff15172e,0xffe13da4,0xff34b9d8,0xfff0c72e,0xff8d5ad7,0xffee753d,0xff64d18b,0xfff6adc7},
        {0xff3f3134,0xffb96568,0xffd9a58f,0xffeddac8,0xff6c7979,0xff90966b,0xff815f8e,0xffc6a76a},
        {0xff181818,0xff343434,0xff505050,0xff6d6d6d,0xff8c8c8c,0xffadadad,0xffcfcfcf,0xfff1f1f1},
        {0xff35302c,0xffb85c38,0xffe6a161,0xffe4c169,0xff607369,0xff95624a,0xffc49283,0xfff6eada},
        {0xff112b40,0xff3389aa,0xff65b7c7,0xffd1eef0,0xff516a9c,0xff91b6d1,0xff8fc3ae,0xffefebda},
        {0xff292523,0xffc14633,0xffe5a849,0xff3d7184,0xff809e72,0xffbc827e,0xffe6ce94,0xffeee4cb},
        {0xff292b39,0xffb35c8a,0xffd68bb0,0xffefd2a0,0xff728a74,0xff719eaf,0xffaa80ac,0xfff4e5da},
        // Signal Primary: clean primaries, sharp contrast and neutral white.
        {0xff111827,0xffed3434,0xff1672de,0xffffdf35,0xfff8f5e9,0xff12a894,0xfff47e26,0xff7651a5},
        // Jewel Cabinet: saturated mineral hues over a dark, regal base.
        {0xff1c1535,0xff007e72,0xff1761b3,0xff742a92,0xffc92754,0xffdfaa36,0xffa9c49e,0xffeee0c5},
        // Soft Sorbet: pale, creamy, low-contrast candy hues.
        {0xff342b42,0xfff6b6ac,0xffffd5a9,0xfff4df7b,0xffa9dbbe,0xffa2d8e6,0xffc6b4eb,0xfffff5e8},
        // Copper Patina: oxidized teal, copper and antique material tones.
        {0xff233939,0xff367f78,0xff83b0a2,0xffb5623e,0xffdb8d51,0xffbcb3a0,0xffebd8b4,0xff6c4e43},
        // Acid Citrus: lime and orange-driven high-key contemporary scheme.
        {0xff25351d,0xff76bb2a,0xffbef03f,0xffffeb3d,0xffff982d,0xfff35c32,0xff327d78,0xfff4e8b0},
        // Winter Cardinal: icy blues with deep pine and bold cardinal red.
        {0xff162d45,0xff286f9f,0xffa2d9eb,0xffe5f4f3,0xffa71635,0xffe34545,0xff275d52,0xff7999af},
        // Urban Concrete: industrial neutrals punctuated by safety accents.
        {0xff25292e,0xff58636b,0xffaeb5b5,0xffe9e6db,0xffe67a26,0xffd1b944,0xff8b4f42,0xff497e98},
        // ---- M4 schemes. Slot 0 is the darkest anchor, as in every preset. Physical
        // stacking is not the array order: ColorWorkspacePalette.selectForSurface picks the
        // most distinct N and sorts them by lightness distance from the mounting surface.
        // Natural Skin (natural portraits): a warm skin ramp from umber shadow to cream
        // highlight, plus one cool grey-green so backgrounds and hair do not turn orange.
        {0xff2b1d18,0xff5a3a2e,0xff8c5a45,0xffb9806a,0xffd9a78c,0xffecc9b0,0xfff7ebde,0xff6b7a74},
        // Graphic Noir (high contrast): ink black, paper white, two steel greys and a
        // single crimson; reads as poster art even at 2-3 layers.
        {0xff0b0b0d,0xff2f3237,0xff6c7178,0xffaab0b6,0xfff7f5f0,0xffc8102e,0xff7a0f1c,0xffe7d8c9},
        // Faded Kodachrome (warm vintage): sepia black, oxblood, burnt orange, mustard,
        // aged-paper cream, faded teal, khaki and dusty rose.
        {0xff2f2420,0xff7a3b2e,0xffc0623b,0xffe09f3e,0xfff1dcb2,0xff5d7d6f,0xff9c8a6e,0xffd8a99a},
        // Nordic Modern (cool modern): ink navy to fog white in four cool steps, fjord
        // teal, driftwood and a lilac accent.
        {0xff1c2733,0xff3e5c76,0xff7b9bb3,0xffc6d6e1,0xfff2f5f7,0xff4f8a85,0xffb7a99a,0xffb9a3c9},
        // Terra & Moss (earth tones): walnut, clay, sienna, sand, moss, sage and rust.
        {0xff2e2a24,0xff5c4632,0xff8b5e3c,0xffc08552,0xffe6cfa3,0xff4f5d3a,0xff8a9a5b,0xffa34e36},
        // Muted Gallery (muted): low-chroma greys and dusty tints, nothing above about
        // 0.06 OKLab chroma, for quiet framed portraits.
        {0xff3b3a3f,0xff6e6a6f,0xffa39e93,0xffe0dacd,0xff7d8c88,0xff94706b,0xff8e8aa3,0xffc9b38f},
        // Riso Pop (vivid pop art): risograph-inspired fluorescent spot hues on a
        // near-black key: pink, yellow, blue, green, orange and violet.
        {0xff1f1f24,0xffff48b0,0xffffe800,0xff0a6fd6,0xff00a95c,0xffff6c2f,0xff765ba7,0xfffff4e6},
        // Indigo Study (monochromatic): one indigo hue in eight even lightness steps,
        // unlike the neutral Monochrome preset.
        {0xff0f1630,0xff1d2b55,0xff2f437f,0xff4a61a3,0xff6f86c0,0xff9aaed8,0xffc5d2ea,0xffedf1f9},
        // Teal & Coral (complementary): a teal ramp against a coral ramp around a
        // neutral cream, so any 3+ layer subset keeps both sides of the pair.
        {0xff12343b,0xff1f6f78,0xff4fb0ae,0xffa8dcd1,0xfff4ece1,0xffe9806e,0xffb7403a,0xfff6c1a6},
        // Chiaroscuro (dramatic dark/light): three deep shadows, a lamp-lit highlight,
        // gold and amber glow and one deep red; the mid-tones are deliberately sparse.
        {0xff0a0908,0xff23201c,0xff3f342a,0xfff8f1e3,0xffe9c46a,0xffb5651d,0xff8f2d26,0xffd9a066}
    };
    public static int[] get(int id,int count){
        if(id<0||id>=COLORS.length||count<2||count>8)throw new IllegalArgumentException("Palette needs 2–8 layers");
        int[] out=new int[count];System.arraycopy(COLORS[id],0,out,0,count);return out;
    }
}

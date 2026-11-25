package com.halftonestudio;

/** Selected manufacturer's published SCREEN sRGB reference colors.
 * These are NOT spectrophotometer measurements or physical color guarantees.
 * Identity stays in the selected palette key; every swatch remains editable.
 * Data: ORAFOL official ORACAL 651 page (gloss variants); Siser EasyWeed
 * distributor reference chart (A-codes). Do not treat material types as
 * interchangeable: adhesive sign vinyl and heat-transfer vinyl differ.
 */
public final class ManufacturerVinylPalettes {
    private ManufacturerVinylPalettes() {}
    public static final int ORACAL_651=0, SISER_EASYWEED=1, COUNT=2;
    public static final class Chip {
        public final String code,name;
        public final int argb;
        Chip(String id,String label,int rgb){code=id;name=label;argb=0xff000000|rgb;}
    }
    private static final Chip[][] CHIPS={
        { // ORACAL 651 glossy, physical product identifiers (ORAFOL sRGB reference)
            new Chip("070 G","Black",0x0D0E11),
            new Chip("080 G","Brown",0x432F1E),
            new Chip("073 G","Dark grey",0x4B4C4C),
            new Chip("071 G","Grey",0x757D7C),
            new Chip("083 G","Nut brown",0xAF591F),
            new Chip("082 G","Beige",0xCEC09F),
            new Chip("023 G","Cream",0xEBD494),
            new Chip("010 G","White",0xE6E9EE)
        },
        { // Siser EasyWeed HTV distributor reference sRGB, not exact vinyl
            new Chip("A0019","Black",0x313030),
            new Chip("A0017","Brown",0x4E423D),
            new Chip("A0086","Chocolate",0x795C46),
            new Chip("A0020","Gold",0x89764B),
            new Chip("A0002","Cream",0xF4E1AD),
            new Chip("A0051","Pale blue",0xB9CBEB),
            new Chip("A0031","Light pink",0xF2C6CF),
            new Chip("A0001","White",0xF1F2F1)
        }
    };
    public static String name(int id){checkId(id);return id==ORACAL_651?"ORACAL 651 · 8-color portrait":"Siser EasyWeed · 8-color portrait";}
    public static String material(int id){checkId(id);return id==ORACAL_651?"Adhesive vinyl":"Heat-transfer vinyl";}
    public static String source(int id){checkId(id);return id==ORACAL_651?
        "https://www.orafol.com/en/europe/products/oracal-651-intermediate-cal":
        "https://www.scandraft.com/Datablad/Siser/COLOR-REFERENCE-CHART-EASYWEED.pdf";}
    public static Chip[] chips(int id){checkId(id);return CHIPS[id].clone();}
    public static int[] colors(int id){checkId(id);int[] out=new int[8];for(int i=0;i<8;i++)out[i]=CHIPS[id][i].argb;return out;}
    public static String details(int id){
        StringBuilder s=new StringBuilder(name(id)).append(" · ").append(material(id))
            .append(". Screen-color approximations; verify against physical material.\n");
        for(Chip c:chips(id))s.append(c.code).append(" ").append(c.name).append(String.format(java.util.Locale.US," #%06X\n",c.argb&0xffffff));
        return s.toString();
    }
    /** Return the catalog code only if the currently selected physical RGB
     * is still identical to a published reference. A hue edit or custom
     * material can never inherit the product ID just because it is similar.
     */
    public static String matchCode(String key,int argb){
        int id=fromKey(key);
        if(id<0)return "";
        for(Chip chip:CHIPS[id])if((chip.argb&0xffffff)==(argb&0xffffff))
            return chip.code+" "+chip.name+" (screen reference only)";
        return "CUSTOM / UNMATCHED — verify actual vinyl material";
    }

    public static int fromKey(String key){
        if("material:0".equals(key))return ORACAL_651;
        if("material:1".equals(key))return SISER_EASYWEED;
        return -1;
    }
    private static void checkId(int id){if(id<0||id>=COUNT)throw new IllegalArgumentException("Unknown manufacturer palette ID");}
}

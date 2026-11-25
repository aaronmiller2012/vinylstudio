package com.halftonestudio;

/** Independent host regression: the role of a screen is NOT the physical vinyl color. */
public final class ExportColorLabelsRC70Test {
    public static void main(String[] args){
        check("L1 · #E9B18F (Y separation)".equals(
            ExportColorLabels.physicalLabel(0,0xffe9b18f,2)),"Y role keeps actual peach vinyl color");
        check("L3 · #F2B6B8 (M separation)".equals(
            ExportColorLabels.physicalLabel(2,0xfff2b6b8,1)),"M role keeps actual pink vinyl color");
        check("L4 · #25252A (K separation)".equals(
            ExportColorLabels.physicalLabel(3,0xff25252a,3)),"dark vinyl accurately labeled");
        check("L2 · #AA77EE".equals(ExportColorLabels.physicalLabel(1,0xffaa77ee,-1)),
            "non-CMYK spot palette has no CMYK label");
        check("03-apply-L3-f2b6b8.svg".equals(ExportColorLabels.fileName(2,0xfff2b6b8)),
            "filenames describe actual swatch, never Yellow/Cyan/Magenta/Black");
        check("NOT EXPORTED — matches mounting surface".equals(ExportColorLabels.status(true,false)),
            "subtrate-matched layer not counted as exported");
        check("NOT EXPORTED — disabled".equals(ExportColorLabels.status(false,false)),
            "disabled layer not counted as exported");
        check(ExportColorLabels.status(true,true).isEmpty(),"exported color does not show exclusion label");
        System.out.println("PASS RC70 actual swatches, channel metadata, substrate exclusions, filenames");
    }
    static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);}
}

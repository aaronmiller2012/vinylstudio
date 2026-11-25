package com.halftonestudio;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Compatibility and meaningful style distinctness tests. No Android dependency. */
public final class RC107ReliabilityHostTest {
    private static void require(boolean good,String problem){if(!good)throw new AssertionError(problem);}
    public static void main(String[] args)throws Exception {
        for(int style=0;style<VinylStyleSuite.NAMES.length;style++){
            if(VinylStyleSuite.isRetiredStyleId(style)){
                require(!NewStyleVariantPolicy.selectable(style),"retired style can be selected: "+style);continue;
            }
            require(NewStyleVariantPolicy.selectable(style),"active style cannot be selected: "+style);
            int count=NewStyleVariantPolicy.count(style);
            for(int v=0;v<count;v++) {
                int expected=style==VinylStyleSuite.BANKNOTE && v==BanknoteEngravingEngine.RETIRED_GUILLOCHE
                    ? BanknoteEngravingEngine.CONTOUR : v;
                require(NewStyleVariantPolicy.restore(style,v)==expected,"variant "+v+" migrated incorrectly: "+style);
            }
            for(int v:new int[]{-2,-1,count,count+50,200})
                require(NewStyleVariantPolicy.restore(style,v)==0,"out-of-range variant must reset: "+style);
        }
        require(NewStyleVariantPolicy.count(9)==9,"Banknote v4-v8 unavailable");
        for(int id:new int[]{8,13,16,17,18,19,20,22,23,25})
            require(VinylStyleSuite.restoredStyleId(id)==-1,"retired ID reused: "+id);
        require(VinylEngine.MINIMUM_CUT_WIDTH_MM==.254f,"physical min regression");
        PhotoStyleDefaults.Initial initial=PhotoStyleDefaults.forNewStyle(
            VinylStyleSuite.BANKNOTE,null, .254f,152.4f);
        require(initial.weightMm>=.254f,"tuner produced invalid weight");
        Map<String,String> rows=new HashMap<>();
        List<String> gold=Files.readAllLines(Paths.get("tests/golden/geometry-signatures.tsv"), StandardCharsets.UTF_8);
        for(String line:gold){String[] cols=line.split("\t",-1);if(cols.length==7)rows.put(cols[0],cols[6]);}
        for(String photo:new String[]{"portrait","highcontrast","dim"}){
            String str=rows.get("style-9-0-"+photo),dash=rows.get("style-9-4-"+photo);
            String cross=rows.get("style-9-2-"+photo),ink=rows.get("style-9-7-"+photo);
            require(str!=null&&dash!=null&&cross!=null&&ink!=null,"missing Banknote golden rows");
            require(!str.equals(dash),"Straight and Highlight Dashes are identical: "+photo);
            require(!cross.equals(ink),"Cross-etch and Two-ink are identical: "+photo);
        }
        System.out.println("PASS RC142: Banknote variants restore, ten retired IDs blocked, duplicate variants differentiated");
    }
}

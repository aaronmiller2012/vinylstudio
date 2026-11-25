package com.halftonestudio;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Build 556: every multi-layer SVG export also carries ONE all-layers SVG for a single
 *  Design Space upload, built byte-for-byte from the verified sheets. */
public final class V3CombinedLayerSvgTest {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static final String HEAD="<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40mm\" height=\"40mm\" viewBox=\"0 0 40 40\"";
    static String marks(String hex){
        return "<g fill=\"#"+hex+"\"><circle cx=\"2\" cy=\"2\" r=\"1\"/><circle cx=\"38\" cy=\"38\" r=\"1\"/></g>";
    }
    static String red(){return HEAD+">\n<g transform=\"translate(4 4)\" fill=\"#D01010\"><rect id=\"a\" x=\"2\" y=\"2\" width=\"6\" height=\"6\"/></g>"+marks("D01010")+"</svg>\n";}
    // Fill on the ROOT: must move onto the sheet's group, not be lost or leak to other layers.
    static String blue(){return HEAD+" fill=\"#1030D0\">\n<rect id=\"a\" x=\"20\" y=\"20\" width=\"6\" height=\"6\"/>"+marks("1030D0")+"</svg>\n";}

    static Map<String,String> zip(int device,boolean reviewed,String[][] sheets) throws Exception {
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(buffer)){
            CutSheetZipWriter cuts=new CutSheetZipWriter(z,device,.254f,reviewed);
            for(String[] s:sheets)cuts.put(s[0],s[1]);
            cuts.finish();
        }
        Map<String,String> out=new LinkedHashMap<>();
        try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(buffer.toByteArray()))){
            ZipEntry e;while((e=z.getNextEntry())!=null)out.put(e.getName(),new String(z.readAllBytes(),StandardCharsets.UTF_8));
        }
        return out;
    }
    static int count(String s,String needle){int n=0,p=0;while((p=s.indexOf(needle,p))>=0){n++;p+=needle.length();}return n;}

    public static void main(String[] ignored) throws Exception {
        String[][] two={{"01-vinyl-d01010.svg",red()},{"02-vinyl-1030d0.svg",blue()}};
        Map<String,String> cricut=zip(OutputDeviceProfile.CRICUT,false,two);
        String all=cricut.get(CombinedLayerSvg.FILENAME);
        check(all!=null,"Cricut export lacks the one-upload SVG: "+cricut.keySet());
        check(cricut.containsKey("01-vinyl-d01010.svg")&&cricut.containsKey("02-vinyl-1030d0.svg"),"separate sheets must remain");
        // Same physical frame as each sheet.
        check(all.contains("width=\"40mm\"")&&all.contains("height=\"40mm\"")&&all.contains("viewBox=\"0 0 40 40\""),"frame lost");
        check(count(all,"<svg")==1&&count(all,"</svg>")==1,"must be one SVG document");
        check(all.contains("<g id=\"L01-vinyl-d01010\">")&&all.contains("<g id=\"L02-vinyl-1030d0\" fill=\"#1030D0\">"),
            "one named group per layer, root fill moved to its group:\n"+all);
        check(all.indexOf("L01-vinyl")<all.indexOf("L02-vinyl"),"layer order changed");
        check(all.contains("id=\"L1-a\"")&&all.contains("id=\"L2-a\"")&&!all.contains("id=\"a\""),"colliding ids not made unique");
        check(all.contains("translate(4 4)"),"inner transforms must be kept");
        // Every element of every sheet, each layer keeps its own registration marks.
        check(CutSvgCompatibility.verify(all,OutputDeviceProfile.CRICUT)==
              CutSvgCompatibility.verify(red())+CutSvgCompatibility.verify(blue()),"geometry was dropped or duplicated");
        check(count(all,"<circle")==4,"each layer must carry its own registration marks");
        // The combined file is valid physical geometry at the same size.
        FinalCutSheetSafety.Report r=FinalCutSheetSafety.inspect("all",all,.254f,-1f);
        check(Math.abs(r.widthMm-40)<.01&&Math.abs(r.heightMm-40)<.01&&!r.outsideArtboard,"combined artboard differs");
        // Not counted as a cut sheet in the safety reports.
        check(!cricut.get("cut-sheet-safety.csv").contains(CombinedLayerSvg.FILENAME),"combined file must not be a safety row");
        check(!cricut.get("physical-artboards.csv").contains(CombinedLayerSvg.FILENAME),"combined file must not be an artboard row");

        // Other SVG cutters get it too; DXF (Silhouette Basic) and single-layer exports do not.
        check(zip(OutputDeviceProfile.GENERIC_SVG,false,two).containsKey(CombinedLayerSvg.FILENAME),"generic SVG lacks combined");
        Map<String,String> dxf=zip(OutputDeviceProfile.SILHOUETTE_BASIC,false,two);
        check(!dxf.containsKey(CombinedLayerSvg.FILENAME)&&!dxf.containsKey(CombinedLayerSvg.SKIPPED_NOTE),"DXF must not get an SVG");
        Map<String,String> one=zip(OutputDeviceProfile.CRICUT,false,new String[][]{two[0]});
        check(!one.containsKey(CombinedLayerSvg.FILENAME)&&!one.containsKey(CombinedLayerSvg.SKIPPED_NOTE),"single layer needs no combined file");

        // Over Cricut's 5,000-shape upload limit: export still succeeds, with a note instead.
        String[][] dense=new String[2][];
        for(int k=0;k<2;k++){
            StringBuilder s=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"120mm\" height=\"120mm\" viewBox=\"0 0 120 120\"><g fill=\"#"+(k==0?"111111":"EEEEEE")+"\">");
            for(int y=0;y<52;y++)for(int x=0;x<52;x++)
                s.append("<circle cx=\"").append(4+x*2+k).append("\" cy=\"").append(4+y*2).append("\" r=\".3\"/>");
            dense[k]=new String[]{"0"+(k+1)+"-dense.svg",s.append("</g></svg>").toString()};
        }
        Map<String,String> big=zip(OutputDeviceProfile.CRICUT,true,dense);
        check(!big.containsKey(CombinedLayerSvg.FILENAME),"combined file over the Cricut limit was written");
        check(big.containsKey(CombinedLayerSvg.SKIPPED_NOTE)&&big.get(CombinedLayerSvg.SKIPPED_NOTE).contains("5000"),
            "missing explanation when combined file is skipped");
        check(big.containsKey("01-dense.svg")&&big.containsKey("02-dense.svg"),"separate sheets must still export");
        check(zip(OutputDeviceProfile.GENERIC_SVG,true,dense).containsKey(CombinedLayerSvg.FILENAME),"Cricut limit leaked to generic SVG");

        // Shared extraction keeps the combined file as its own sheet name.
        ByteArrayOutputStream z=new ByteArrayOutputStream();
        try(ZipOutputStream out=new ZipOutputStream(z)){for(Map.Entry<String,String> e:cricut.entrySet()){out.putNextEntry(new ZipEntry(e.getKey()));out.write(e.getValue().getBytes(StandardCharsets.UTF_8));out.closeEntry();}}
        File root=java.nio.file.Files.createTempDirectory("combined-share").toFile();
        boolean found=false;
        for(File f:SharedExportFiles.extractCutSheets(new ByteArrayInputStream(z.toByteArray()),root))
            found|=CombinedLayerSvg.FILENAME.equals(f.getName());
        check(found,"share extraction renamed or dropped the combined SVG");
        System.out.println("PASS combined one-upload SVG: frame, groups, colors, marks, ids, limits, DXF exclusion, share extraction");
    }
}

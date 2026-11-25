package com.halftonestudio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Deterministic two-sheet physical-size / registration import fixture.
 * It does not simulate Silhouette Studio or a physical cutter.
 */
public final class V3Beta4CutterFixture {
    private static String svg(boolean first){
        StringBuilder s=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100mm\" height=\"100mm\" viewBox=\"0 0 100 100\">");
        for(int x:new int[]{8,90})for(int y:new int[]{8,90})
            s.append("<rect x=\"").append(x).append("\" y=\"").append(y)
                .append("\" width=\"2\" height=\"2\"/>");
        if(first)s.append("<rect x=\"40\" y=\"40\" width=\"20\" height=\"20\"/>");
        else s.append("<circle cx=\"50\" cy=\"50\" r=\"8\"/>");
        return s.append("</svg>").toString();
    }
    private static void add(ZipOutputStream zip,String name,String content)throws IOException{
        zip.putNextEntry(new ZipEntry(name));zip.write(content.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
    private static void output(File file,int target)throws Exception{
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(file))){
            CutSheetZipWriter writer=new CutSheetZipWriter(zip,target,.30f,false);
            writer.put("L01-registration-square.svg",svg(true));
            writer.put("L02-registration-circle.svg",svg(false));
            writer.finish();
            add(zip,"TEST-PLAN.txt",
                "VinylStudio v3 Beta4 CALIBRATION TEST - NOT production art.\n"+
                "Expected reference artboard: 100.00 x 100.00 mm.\n"+
                "Four physical 2.00 mm square markers on BOTH sheets; positions x/y 8..10 and 90..92 mm.\n"+
                "Expected mark-center horizontal and vertical spacing: 82.00 mm.\n"+
                "Expected selection geometry bounds INCLUDING marks: x=8..92 mm, y=8..92 mm; size 84.00 x 84.00 mm.\n"+
                "Sheet 1 central square = 20.00 x 20.00 mm; sheet 2 central circle diameter = 16.00 mm.\n"+
                "Import each file independently; VERIFY geometry size and marker spacing after import.\n"+
                "Never resize individual layers independently or use Fit to Page.\n"+
                "These physical marks are NOT optical Print & Cut registration marks.\n"+
                "Test-cut on scrap, record measured dimensions and differences, DO NOT use as real project.\n");
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Provide output directory");
        File dir=new File(args[0]);if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot create fixture directory");
        output(new File(dir,"VinylStudio-Beta4-Calibration-SVG.zip"),OutputDeviceProfile.CRICUT);
        output(new File(dir,"VinylStudio-Beta4-Calibration-DXF.zip"),OutputDeviceProfile.SILHOUETTE_BASIC);
        System.out.println("PASS Generated two-sheet SVG and DXF calibration archives");
    }
}

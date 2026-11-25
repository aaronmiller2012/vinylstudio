package com.halftonestudio;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class RC172DxfR13Test {
    private static void yes(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private static void broken(String svg){
        try{SvgDxfR13Exporter.convert(svg);throw new AssertionError("Invalid cut SVG accepted: "+svg);}
        catch(IOException expected){}
    }
    public static void main(String[] args)throws Exception {
        String svg="<svg xmlns='http://www.w3.org/2000/svg' width='100mm' height='75mm' viewBox='0 0 100 75'>"+
          "<g transform='translate(10 5)' fill='#000000'>"+
          "<circle cx='2' cy='3' r='1'/>"+
          "<path fill-rule='evenodd' d='M 0 0 L 20 0 L 20 20 L 0 20 Z M 5 5 L 5 15 L 15 15 L 15 5 Z'/>"+
          "<path fill='none' stroke='black' d='M 0 25 C 5 30 10 20 15 25 L 20 25'/>"+
          "<path fill='none' stroke='black' d='M 30 30 a 4 6 0 0 1 10 10'/>"+
          "</g></svg>";
        SvgDxfR13Exporter.Result r=SvgDxfR13Exporter.convert(svg);
        yes(r.entities==5&&r.closedContours==3&&r.openPaths==2,"Mismatch in cut contours and open drawing paths");
        yes(r.minXmm<=10.0001&&r.maxXmm>=30.0&&r.maxYmm>=70.0,"Physical cut bounds missing");
        yes(r.vertices>30,"Curves were not approximated");
        yes(r.dxf.startsWith("0\r\nSECTION\r\n")&&r.dxf.contains("AC1012")&&r.dxf.endsWith("0\r\nEOF\r\n"),"Not real DXF R13");
        yes(r.dxf.contains("0\r\nCIRCLE\r\n")&&r.dxf.contains("0\r\nPOLYLINE\r\n"),"Missing recognized DXF entities");
        yes(r.dxf.contains("10\r\n12.000000\r\n20\r\n67.000000\r\n"),"Circle misaligned by viewBox/translate/Y flip");
        yes(!r.dxf.contains("NaN")&&!r.dxf.contains("Infinity"),"Non-finite DXF coordinate");
        String scaled="<svg width='25.4mm' height='50.8mm' viewBox='10 20 100 200'><g transform='translate(2 3)'>"+
           "<path d='M10 20 L90 20 L90 100 L10 100 Z'/></g></svg>";
        SvgDxfR13Exporter.Result sr=SvgDxfR13Exporter.convert(scaled);
        yes(sr.dxf.contains("10\r\n0.508000\r\n20\r\n50.038000\r\n"),"ViewBox to millimeters is incorrect");
        String calibration="<svg width='100mm' height='100mm' viewBox='0 0 100 100'><path d='M0 0 L100 0 L100 100 L0 100 Z'/></svg>";
        String calibrationDxf=SvgDxfR13Exporter.convert(calibration).dxf;
        yes(calibrationDxf.contains("$INSUNITS\r\n70\r\n4\r\n"),"DXF has wrong physical units");
        // Re-read the encoded R13 group records, independently of the result metadata.
        // The calibration square is a closed POLYLINE with four VERTEX entities.
        String[] pairs=calibrationDxf.split("\r\n");
        double loX=Double.POSITIVE_INFINITY,hiX=Double.NEGATIVE_INFINITY;
        double loY=Double.POSITIVE_INFINITY,hiY=Double.NEGATIVE_INFINITY;
        int calibrationVertices=0;boolean vertex=false;
        for(int k=0;k+1<pairs.length;k+=2){
            String code=pairs[k].trim(),value=pairs[k+1].trim();
            if(code.equals("0")){vertex=value.equals("VERTEX");continue;}
            if(!vertex)continue;
            if(code.equals("10")){
                double x=Double.parseDouble(value);
                loX=Math.min(loX,x);hiX=Math.max(hiX,x);
            }
            if(code.equals("20")){
                double y=Double.parseDouble(value);
                loY=Math.min(loY,y);hiY=Math.max(hiY,y);calibrationVertices++;
            }
        }
        yes(calibrationVertices==4&&Math.abs(hiX-loX-100)<0.00001&&
            Math.abs(hiY-loY-100)<0.00001,
            "Decoded DXF square dimensions are not exactly 100 by 100 mm");
        String rect="<svg width='10mm' height='10mm' viewBox='0 0 10 10'><rect x='1' y='1' width='4' height='3'/></svg>";
        yes(SvgDxfR13Exporter.convert(rect).closedContours==1,"Rectangle cut missing");
        broken("<svg width='10mm' height='10mm' viewBox='0 0 10 10'><image href='test.png'/></svg>");
        broken("<svg width='10px' height='10mm' viewBox='0 0 10 10'><circle r='1'/></svg>");
        broken("<!DOCTYPE svg [<!ENTITY p SYSTEM 'file:///etc/passwd'>]><svg width='10mm' height='10mm' viewBox='0 0 10 10'><path d='M0 0L1 1'/></svg>");
        broken("<svg width='10mm' height='10mm' viewBox='0 0 10 10'><path d='M0 0 X1 1'/></svg>");
        yes(OutputDeviceProfile.byId(OutputDeviceProfile.SILHOUETTE_BASIC).format==OutputDeviceProfile.CutFormat.DXF_R13,"DXF profile missing");
        yes(OutputDeviceProfile.byId(0).format==OutputDeviceProfile.CutFormat.SVG,"Existing Cricut profile changed");
        File tmp=Files.createTempDirectory("rc172").toFile();
        try {
            File bundle=new File(tmp,"stage.zip");
            try(StagedCutZip stage=new StagedCutZip(tmp)){
                try(ZipOutputStream zip=new ZipOutputStream(stage.openStream())){
                    CutSheetZipWriter cuts=new CutSheetZipWriter(zip,OutputDeviceProfile.SILHOUETTE_BASIC);
                    String a=cuts.put("01-cut.svg",svg);
                    yes("01-cut.dxf".equals(a),"Export extension incorrect");
                    zip.putNextEntry(new ZipEntry("layer-manifest.csv"));
                    zip.write(cuts.manifest("order,file\n1,01-cut.svg\n").getBytes(StandardCharsets.UTF_8));zip.closeEntry();
                    cuts.finish();
                }
                stage.verify();
                try(FileOutputStream out=new FileOutputStream(bundle)){stage.copyTo(out);}
            }
            try(ZipFile zip=new ZipFile(bundle)){
                yes(zip.getEntry("01-cut.dxf")!=null&&zip.getEntry("01-cut.svg")==null,"DXF ZIP retained raw SVG cut");
                yes(zip.getEntry("dimensions-dxf.txt")!=null,"DXF size guide missing");
                try(InputStream in=zip.getInputStream(zip.getEntry("dimensions-dxf.txt"))){
                    String guide=new String(in.readAllBytes(),StandardCharsets.UTF_8);
                    yes(guide.contains("01-cut.dxf: geometry bounds")&&guide.contains("Do NOT set the selection bounding box"),
                        "DXF per-file physical geometry scaling guidance missing");
                }
                try(InputStream in=zip.getInputStream(zip.getEntry("01-cut.dxf"))){
                    String saved=new String(in.readAllBytes(),StandardCharsets.US_ASCII);
                    yes(saved.equals(r.dxf),"Staged DXF changed geometry");
                }
                try(InputStream in=zip.getInputStream(zip.getEntry("layer-manifest.csv"))){
                    yes(new String(in.readAllBytes(),StandardCharsets.UTF_8).contains("01-cut.dxf"),"Manifest names not converted");
                }
            }
            File dxf=new File(tmp,"sample.dxf");Files.write(dxf.toPath(),r.dxf.getBytes(StandardCharsets.US_ASCII));
            System.out.println("INDEPENDENT_DXF_SAMPLE="+dxf.getAbsolutePath());
        }finally{for(File f:tmp.listFiles())f.delete();tmp.delete();}
        System.out.println("PASS RC172 vector geometry, paths, transformations, fail-closed security and DXF-only staged ZIP");
    }
}

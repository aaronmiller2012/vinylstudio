package com.halftonestudio;
import java.io.*;
import java.util.zip.*;
public class MaterialCalibrationSheetTest {
 public static void main(String[] args)throws Exception{
  String s=MaterialCalibrationSheet.svg();
  SvgDxfR13Exporter.Result d=SvgDxfR13Exporter.convert(s);
  if(Math.abs(d.widthMm-120)>1e-6||Math.abs(d.heightMm-100)>1e-6||d.closedContours!=66||d.openPaths!=0)throw new AssertionError("geometry: "+d.closedContours);
  for(boolean dx:new boolean[]{false,true})try(StagedCutZip stage=new StagedCutZip(new File(System.getProperty("java.io.tmpdir")))){
   MaterialCalibrationSheet.write(stage.openStream(),dx);stage.verify();ByteArrayOutputStream b=new ByteArrayOutputStream();stage.copyTo(b);
   int count=0;try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(b.toByteArray()))){ZipEntry e;while((e=z.getNextEntry())!=null){count++;if(e.getName().equals("results.csv")&&new String(z.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).split("\n").length!=49)throw new AssertionError("results rows");}}
   if(count!=3)throw new AssertionError("archive members");
  }
  System.out.println("PASS calibration dimensions, 66 closed contours, SVG/DXF staged ZIP and 48 result rows");
 }
}

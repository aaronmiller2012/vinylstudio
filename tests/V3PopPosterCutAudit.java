package com.halftonestudio;
import java.util.*;
/** Validate all populated nine-color Poster physical sheets against final SVG cut inspector. */
public final class V3PopPosterCutAudit {
 public static void main(String[] args)throws Exception{
  float width=args.length>0?Float.parseFloat(args[0]):101.6f;
  int w=150,h=170;int[] pixels=V3AllStyleExportMatrixTest.portrait(w,h);
  VinylStyleSuite.Config cfg=new VinylStyleSuite.Config();cfg.style=VinylStyleSuite.POSTER_STENCIL;
  cfg.detail=92;cfg.colorCount=9;cfg.widthMm=width;cfg.minimumMm=.8f;cfg.gapMm=.5f;
  cfg.popPoster=PopPosterEngine.preset(PopPosterEngine.POSTER,0,width);
  cfg.palette=PopPosterEngine.posterPalette(0,9);
  long start=System.nanoTime();
  StyleKit.Result raw=VinylStyleSuite.generate(pixels,w,h,cfg);
  StyleKit.Result prepared=VinylStyleSuite.prepareForCutting(raw,cfg.style,cfg.minimumMm,cfg.gapMm);
  if(prepared.layers.size()!=9)throw new AssertionError("Not all nine layers populated at "+width);
  int narrow=0,critical=0,unverified=0,contours=0;
  for(int i=0;i<prepared.layers.size();i++){
   String svg=NewStyleExport.layerSvg(prepared,i,false,0,3,2,null,cfg.minimumMm);
   FinalCutSheetSafety.Report report=FinalCutSheetSafety.inspect("layer"+i,svg,cfg.minimumMm,cfg.gapMm);
   narrow+=report.narrow;critical+=report.critical;unverified+=report.unverified;contours+=report.contours;
  }
  System.out.printf(Locale.US,"POSTER AUDIT %.1f mm sheets=%d contours=%d narrow=%d critical=%d unverified=%d ms=%d%n",
   width,prepared.layers.size(),contours,narrow,critical,unverified,(System.nanoTime()-start)/1000000);
  if(contours<3||narrow>0||critical>0||unverified>0)
   throw new AssertionError("Nine-layer Poster cut preflight failed at "+width);
 }
}

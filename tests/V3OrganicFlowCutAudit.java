package com.halftonestudio;
import java.util.*;
public final class V3OrganicFlowCutAudit {
 public static void main(String[] args)throws Exception{
   int w=180,h=215;int[] pixels=V3AllStyleExportMatrixTest.portrait(w,h);
   for(int id=VinylStyleSuite.QUILLING_RIBBONS;id<=VinylStyleSuite.QUILLED_MARBLING;id++){
     if(args.length>1 && id!=Integer.parseInt(args[1]))continue;
     VinylStyleSuite.Config c=new VinylStyleSuite.Config();c.style=id;
     c.widthMm=args.length>0?Float.parseFloat(args[0]):279.4f;
     c.colorCount=6;c.minimumMm=.8f;c.gapMm=.5f;c.detail=90;
     long start=System.nanoTime();
     StyleKit.Result r=VinylStyleSuite.generate(pixels,w,h,c);
     System.out.println(id+" src layers="+r.layers.size()+" pts="+r.totalPoints()+" tookMs="+((System.nanoTime()-start)/1000000));
     StyleKit.Result prepared=VinylStyleSuite.prepareForCutting(r,id,c.minimumMm,c.gapMm);
     int actual=0,narrow=0,hard=0,unknown=0;
     for(int i=0;i<prepared.layers.size();i++){
       String svg=NewStyleExport.layerSvg(prepared,i,false,0,3,2,null,c.minimumMm);
       FinalCutSheetSafety.Report rep=FinalCutSheetSafety.inspect("layer"+i,svg,c.minimumMm,c.gapMm);
       actual+=rep.contours;narrow+=rep.narrow;hard+=rep.critical;unknown+=rep.unverified;
       System.out.println("  L"+i+" loops="+rep.contours+" narrow="+rep.narrow+" hard="+rep.critical+" unverified="+rep.unverified);
     }
     System.out.println("TOTAL "+id+" sheets="+prepared.layers.size()+" contours="+actual+" narrow="+narrow+" hard="+hard+" unknown="+unknown);
     if(actual<3 || narrow>0 || hard>0 || unknown>0)throw new AssertionError("Cut preflight failed for style "+id);
   }
 }
}

package com.halftonestudio;
import java.util.*;
public final class PrismaticVectorBooleanTest {
 static void run()throws Exception {
  PrismaticWeaveV3Test.main(new String[0]);
  int w=104,h=96;int[] p=PrismaticWeaveV3Test.ramp(w,h);
  for(float mm:new float[]{76.2f,101.6f,279.4f}){
   PrismaticDiagonalPortraitEngine.Options o=new PrismaticDiagonalPortraitEngine.Options();
   o.widthMm=mm;o.layers=4;o.palette=new int[]{0xff966b57,0xff794a37,0xff53301e,0xff2b1d15};
   VinylStyleSuite.Config c=new VinylStyleSuite.Config();c.style=VinylStyleSuite.PRISMATIC_DIAGONAL;
   c.widthMm=mm;c.detail=o.detail;c.colorCount=o.layers;c.palette=o.palette.clone();c.minimumMm=o.minCutMm;
   // M5 (build 560): the suite conditions Diagonal's final sheets to the export weed gap.
   // The direct engine call gets the same option, so the suite must still pass every
   // setting through unchanged (exact points below).
   o.conditionGapMm=Math.max(c.prismaticGapMm,StyleCutGeometryPreflight.exportGapMm(c.gapMm,c.minimumMm));
   // M5 (build 561): production Diagonal uses size-aware pitch above 6 in; same option here,
   // so parity stays exact (the 279.4 mm case exercises it).
   o.sizeAwarePitch=true;
   long t=System.nanoTime();StyleKit.Result r=PrismaticDiagonalPortraitEngine.generate(p,w,h,o);
   V3VectorBooleanTest.require(r.summary.contains("independent vector Boolean overlays"),"missing vector route");
   V3VectorBooleanTest.require(r.summary.contains("cut conditioning"),"Diagonal production sheets must be conditioned");
   StyleKit.Result suite=VinylStyleSuite.generate(p,w,h,c);
   for(int i=0;i<suite.layers.size();i++){
    StyleKit.Layer l=suite.layers.get(i);int slot=NewStyleColorSlots.slot(c.style,l.name,i);
    StyleKit.Layer expected=r.layers.get(slot);
    V3VectorBooleanTest.require(l.vectorGeometryLocked&&l.color==expected.color&&l.loops.size()==expected.loops.size(),"suite changed slot topology");
    for(int k=0;k<l.loops.size();k++)V3VectorBooleanTest.require(Arrays.equals(l.loops.get(k),expected.loops.get(k)),"suite changed vector points");
   }
   StyleKit.Result cut=VinylStyleSuite.prepareForCutting(r,VinylStyleSuite.PRISMATIC_DIAGONAL,o.minCutMm,o.gapMm);
   for(int i=0;i<o.layers;i++){
    StyleKit.Layer l=r.layers.get(i);V3VectorBooleanTest.require(l.vectorGeometryLocked&&l.color==o.palette[i],"unlocked/changed sheet");
    V3VectorBooleanTest.require(l.loops.equals(cut.layers.get(i).loops),"cut prep changed contour");
   }
   System.out.printf(Locale.US,"Prismatic %.1fmm %d points %.2f sec%n",mm,r.totalPoints(),(System.nanoTime()-t)/1e9);
  }
 }
 public static void main(String[] args)throws Exception{
  if(args.length>0&&args[0].equals("--geos")){try(GeosVectorReferenceBackend b=new GeosVectorReferenceBackend()){VectorBooleanEngine.install(b);run();}}
  else{VectorBooleanEngine.install((VectorBooleanEngine.Backend)Class.forName("com.halftonestudio.JtsVectorBooleanBackend").getDeclaredConstructor().newInstance());run();}
 }
}

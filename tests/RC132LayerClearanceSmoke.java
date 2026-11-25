package com.halftonestudio;
public final class RC132LayerClearanceSmoke {
 public static void main(String[] args){
  int w=140,h=160;int[] p=new int[w*h];
  java.util.Arrays.fill(p,0xff996655);
  LayeredCellPortraitEngine.Options o=new LayeredCellPortraitEngine.Options();
  o.widthMm=200;o.columns=18;o.layerCount=8;o.gapMm=.2f;o.minFeatureMm=.254f;o.registration=false;
  o.detailMotifOverlay=false;
  LayeredCellPortraitEngine.Result r=FishScaleMosaicEngine.generate(p,w,h,o);
  for(int i=0;i<8;i++){
   if(r.pieceCounts[i]>0){
    System.out.println("checking layer "+i+" pieces "+r.pieceCounts[i]);
    PhysicalContourClearance.requireLayerClearance(r,i);
   }
  }
  System.out.println("PASS RC132 no same-sheet overlap");
 }
}

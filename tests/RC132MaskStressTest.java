package com.halftonestudio;
public final class RC132MaskStressTest {
 public static void main(String[] args){
  int w=160,h=180;int[] px=new int[w*h];
  boolean[] allowed=new boolean[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   int k=y*w+x;int v=40+(x*140/w)+(y*70/h);
   px[k]=0xff000000|(v<<16)|(v<<8)|v;
  }
  for(int mode=0;mode<3;mode++){
   for(int y=0;y<h;y++)for(int x=0;x<w;x++){
    double dx=(x-w*.5)/(w*.45),dy=(y-h*.5)/(h*.45);
    allowed[y*w+x]=(mode==0?true:mode==1?dx*dx+dy*dy<1.0:(dx*dx+dy*dy<1 && !(x>60 && x<80&&y>35&&y<95)));
   }
   LayeredCellPortraitEngine.Options opt=new LayeredCellPortraitEngine.Options();
   opt.widthMm=254f;opt.columns=24;opt.layerCount=8;
   opt.gapMm=.20f;opt.minFeatureMm=.254f;
   opt.photoDetail=1;opt.detailMotifOverlay=false;opt.registration=false;
   opt.allowedPixels=allowed;
   LayeredCellPortraitEngine.Result r=FishScaleMosaicEngine.generate(px,w,h,opt);
   for(int sheet=0;sheet<8;sheet++)if(r.pieceCounts[sheet]>0)
      LayeredCellExportSafety.validate(r,sheet,r.svg(sheet));
   System.out.println("PASS RC132 mask mode "+mode+" scales "+r.pieces.size());
  }
 }
}

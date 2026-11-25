package com.halftonestudio;
import java.util.Random;
import java.util.Arrays;
public final class TiledLevelsV2541Test {
  public static void main(String[] a){
    int w=421,h=531;int[] image=new int[w*h];Random rnd=new Random(2541);
    for(int i=0;i<image.length;i++) image[i]=(rnd.nextInt(5)==0?0x55000000:0xff000000)|rnd.nextInt(0x1000000);
    VinylEngine.Settings s=VinylEngine.Settings.defaults();
    s.levelsBlack=14;s.levelsWhite=239;s.levelsGamma=.9f;s.exposureStops=.33f;
    s.photoContrast=1.2f;s.levelsShadows=.45f;s.levelsHighlights=-.15f;s.edgePriority=.65f;
    assertParity(image,w,h,s,"unmasked");
    s.snips.add(new SnipMask.Operation(SnipMask.REMOVE_INSIDE,.26f,.21f,.68f,.21f,.68f,.71f,.26f,.71f));
    assertParity(image,w,h,s,"snipped");
    System.out.println("PASS tile lighting: exact ImageLevels parity at two tile joins, alpha and global Snip coordinates");
  }
  static void assertParity(int[] image,int w,int h,VinylEngine.Settings s,String name){
    int[] old=ImageLevels.apply(image,w,h,s),newer=TiledImageLevels.apply(image,w,h,s);
    for(int i=0;i<old.length;i++)if(old[i]!=newer[i])throw new AssertionError(name+" mismatch at "+i+
      " row "+i/w+" expected "+Integer.toHexString(old[i])+" found "+Integer.toHexString(newer[i]));
  }
}

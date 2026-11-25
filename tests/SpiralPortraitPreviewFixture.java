package com.halftonestudio;
import java.nio.file.*;
public final class SpiralPortraitPreviewFixture {
 public static void main(String[] a)throws Exception {
  int w=600,h=600;int[] image=SpiralPortraitRibbonV2532Test.portrait(w,h,false);
  SpiralPortraitRibbon.Options o=SpiralPortraitRibbonV2532Test.opts();
  SpiralPortraitRibbon.Result r=SpiralPortraitRibbon.generate(image,w,h,o);
  Files.writeString(Path.of(a[0]),r.svg());
 }
}

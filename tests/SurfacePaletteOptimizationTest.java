package com.halftonestudio;
import java.util.*;
public final class SurfacePaletteOptimizationTest {
  private static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
  private static Set<Integer> colors(int[] a){Set<Integer> s=new HashSet<>();for(int c:a)s.add(c);return s;}
  public static void main(String[] args){
    int[] surfaces={0xfffaf9f6,0xfff4e7ce,0xff18191e};
    for(int[] full:new int[][]{VinylPalettes.get(0,8),VinylPalettes.get(5,8)}){
      int[] previous=null;
      for(int n=2;n<=8;n++){
        int[] chosen=ColorWorkspacePalette.selectForSurface(full,n,surfaces[0]);
        check(chosen.length==n&&colors(chosen).size()==n,"Unique count "+n);
        if(previous!=null)check(colors(chosen).containsAll(colors(previous)),"Increasing count must only add swatches");
        previous=chosen;
      }
    }
    for(int id=0;id<VinylPalettes.NAMES.length;id++){
      int[] full=VinylPalettes.get(id,8),snapshot=full.clone();
      for(int surface:surfaces){
        for(int n=2;n<=8;n++){
          int[] chosen=ColorWorkspacePalette.selectForSurface(full,n,surface);
          check(chosen.length==n,"Count="+n);
          check(colors(chosen).size()==n,"Duplicate selection="+id+"/"+n);
          check(colors(full).containsAll(colors(chosen)),"Invented a new swatch");
          check(Arrays.equals(chosen,ColorWorkspacePalette.selectForSurface(full,n,surface)),"Deterministic selection");
          int[] arranged=ColorWorkspacePalette.arrangedForSurface(full,n,surface);
          check(arranged.length==8&&colors(arranged).size()==8,"Complete reserve palette");
          for(int j=0;j<n;j++)check(arranged[j]==chosen[j],"Active preview/application mismatch");
          double prev=-1,base=ColorWorkspacePalette.lightness(surface);
          for(int c:chosen){double difference=Math.abs(ColorWorkspacePalette.lightness(c)-base);
            check(difference+1e-7>=prev,"Physical stack sorting id="+id+"/"+n);prev=difference;}
        }
      }
      check(Arrays.equals(snapshot,full),"Cannot mutate palette IDs/colors");
    }
    int[] white=ColorWorkspacePalette.selectForSurface(VinylPalettes.get(0,8),6,0xfffaf9f6);
    int[] black=ColorWorkspacePalette.selectForSurface(VinylPalettes.get(0,8),6,0xff18191e);
    check(!Arrays.equals(white,black),"Different mounting surfaces need different application stacks");
    int[] anchors={0xffffdd00,0xff00aeef,0xffec008c,0xff131313};
    check(anchors[0]==0xffffdd00&&anchors[3]==0xff131313,"CMYK not sorted via this helper");
    System.out.println("PASS: all 19 vinyl palettes x 2-8 layers x 3 mounting surfaces, nested count selections, stable IDs, and sorted previews");
  }
}

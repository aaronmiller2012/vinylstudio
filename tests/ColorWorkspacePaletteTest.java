package com.halftonestudio;
import java.util.*;
public final class ColorWorkspacePaletteTest {
 private static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);}
 public static void main(String[] ignored){
   for(int id=0;id<VinylPalettes.NAMES.length;id++)for(int n=2;n<=8;n++){
     int[] out=ColorWorkspacePalette.select(VinylPalettes.get(id,8),n);
     check(out.length==n,"Standard palette count "+id+"/"+n);
     check(new HashSet<Integer>(){{for(int c:out)add(c);}}.size()==n,"Duplicate color "+id+"/"+n);
     check(ColorWorkspacePalette.columns(n)==Math.min(n,4),"Adaptive phone grid "+n);
   }
   for(int i=0;i<ColorWorkspacePalette.TESS_NAMES.length;i++)for(int n=2;n<=8;n++){
     int[] out=ColorWorkspacePalette.select(ColorWorkspacePalette.tessPalette(i),n);
     check(out.length==n,"Tessellation palette "+i+"/"+n);
     check(new HashSet<Integer>(){{for(int c:out)add(c);}}.size()==n,"Tess duplicate "+i+"/"+n);
   }
   int[] seed=VinylPalettes.get(0,8);
   int[] used={seed[0],seed[2],seed[5],seed[7]};
   int additional=ColorWorkspacePalette.nextUnusedColor(seed,used);
   for(int old:used)check(additional!=old,"Layer expansion must not duplicate an existing preset color");
   int[] original=ColorWorkspacePalette.tessPalette(2);int[] copy=ColorWorkspacePalette.tessPalette(2);
   copy[0]=0;check(original[0]!=copy[0],"Palette must be defensive copy");
   int[] p=ColorWorkspacePalette.select(VinylPalettes.get(0,8),5);
   check(p[0]==VinylPalettes.get(0,8)[0]&&p[4]==VinylPalettes.get(0,8)[7],"Full-palette endpoints");
   // Preset stack uses substrate-relative L*: pale mounting -> light first/dark last.
   for(int i=0;i<VinylPalettes.NAMES.length;i++)for(int n=2;n<=8;n++){
     int[] chosen=ColorWorkspacePalette.select(VinylPalettes.get(i,8),n);
     int[] sorted=chosen.clone();
     ColorWorkspacePalette.sortForSurface(sorted,n,0xfffaf9f6);
     check(Arrays.equals(chosen,ColorWorkspacePalette.select(VinylPalettes.get(i,8),n)),"Must not mutate preset");
     Set<Integer> before=new HashSet<>(),after=new HashSet<>();
     for(int c:chosen)before.add(c);for(int c:sorted)after.add(c);
     check(before.equals(after),"No preset color lost or duplicated");
     double prev=-1;
     for(int c:sorted){double d=Math.abs(ColorWorkspacePalette.lightness(c)-ColorWorkspacePalette.lightness(0xfffaf9f6));
       check(d+1e-8>=prev,"Light background: nearest substrate bottom -> darkest top");prev=d;}
     int[] once=sorted.clone();ColorWorkspacePalette.sortForSurface(sorted,n,0xfffaf9f6);
     check(Arrays.equals(once,sorted),"Idempotent preset stack order");
   }
   int[] mixed={0xff1d1d1d,0xfff8f8f8,0xff909090,0xff7a7a7a,0xffe23b3b};
   int[] bg=mixed.clone();ColorWorkspacePalette.sortForSurface(bg,4,0xfffaf9f6);
   check(bg[0]==0xfff8f8f8&&bg[3]==0xff1d1d1d,"Light substrate stack positions");
   check(bg[4]==mixed[4],"Inactive preset swatches are never reordered");
   int[] dark=mixed.clone();ColorWorkspacePalette.sortForSurface(dark,4,0xff18191e);
   check(dark[0]==0xff1d1d1d&&dark[3]==0xfff8f8f8,"Dark substrate stack positions");
   int[] cmyk={0xffffdd00,0xff00aeef,0xffec008c,0xff131313};
   // CMYK must continue to be configured separately, never fed to sorting.
   check(cmyk[0]==0xffffdd00&&cmyk[3]==0xff131313,"CMYK remains Y-C-M-K");
   System.out.println("PASS: 19 standard palettes x 2-8 + 7 tess palettes x 2-8; no color collisions; 1-2 row swatch grids; copy isolation");
 }
}

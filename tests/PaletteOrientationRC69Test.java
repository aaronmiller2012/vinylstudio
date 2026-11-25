package com.halftonestudio;
import java.util.Arrays;
public final class PaletteOrientationRC69Test {
  private static void check(boolean x,String reason){if(!x)throw new AssertionError(reason);}
  public static void main(String[] args){
    int[] colors={0xff110001,0xff220002,0xff330003,0xff440004,0xff550005,0xff660006,0xff770007,0xff880008};
    int[] untouched=colors.clone();
    check(PaletteOrientation.orient(colors,false)[0]==colors[0],"normal order");
    check(PaletteOrientation.orient(colors,true)[0]==colors[7],"reverse order");
    check(Arrays.equals(PaletteOrientation.orient(PaletteOrientation.orient(colors,true),true),colors),"double reverse");
    for(int n=2;n<=8;n++){
       int[] selected=PaletteOrientation.select(colors,n,true);
       check(selected.length==n&&selected[0]==colors[7]&&selected[n-1]==colors[0],"reversed subset "+n);
       int[] packed=PaletteOrientation.arrange(colors,n,true);
       check(packed.length==8&&packed[0]==colors[7],"full retained tail "+n);
    }
    int[] cmyk={0xffffff00,0xff00ffff,0xffff00ff,0xff000000};
    int[] opposite=PaletteOrientation.orientCmyk(cmyk,true);
    check(opposite[0]==cmyk[0]&&opposite[3]==cmyk[3]&&opposite[1]==cmyk[2],"CMYK anchors");
    check(Arrays.equals(PaletteOrientation.orientCmyk(opposite,true),cmyk),"CMYK double reversal");
    int[] repeated={0xff777777,0xff777777,0xff777777,0xff777777,0xff777777,0xff777777,0xff777777,0xff777777};
    for(int n=2;n<=8;n++)check(PaletteOrientation.arrange(repeated,n,true).length==8,"near monochrome photo "+n);
    check(Arrays.equals(colors,untouched),"no mutation");
    System.out.println("PASS RC69 explicit orientation, CMYK anchors, mono palettes, 2–8 counts");
  }
}

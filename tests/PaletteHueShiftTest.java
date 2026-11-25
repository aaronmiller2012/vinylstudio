import com.halftonestudio.PaletteHueShift;
import java.util.Arrays;
public class PaletteHueShiftTest {
  static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  static int ch(int c,int shift){return PaletteHueShift.rotateOne(c,shift);}
  static int spread(int c){int r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;return Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b));}
  static int value(int c){return Math.max((c>>>16)&255,Math.max((c>>>8)&255,c&255));}
  public static void main(String[]args){
    int[] ocean={0xff162c40,0xff235276,0xff3687ad,0xff70b9bf,0xffc9eddf,0xff47769e,0xffa3cdd6,0xfff8eee0};
    int[] origin=ocean.clone();
    check(Arrays.equals(PaletteHueShift.rotate(ocean,0),ocean),"Zero shift must be bit-exact");
    check(Arrays.equals(ocean,origin),"Must never mutate source palette");
    check(ch(0xff777777,110)==0xff777777,"Gray unaffected");
    check(ch(0xff000000,-180)==0xff000000,"Black unaffected");
    check(ch(0xffffffff,170)==0xffffffff,"White unaffected");
    check(ch(0xff0000ff,120)==0xffff0000,"Blue shifted 120 degrees = red");
    check(ch(0xff0000ff,-120)==0xff00ff00,"Blue shifted -120 degrees = green");
    for(int c:ocean){
      for(int d=-180;d<=180;d+=15){int shifted=ch(c,d);
        check(((shifted>>>24)&255)==((c>>>24)&255),"Alpha preserved");
        check(Math.abs(spread(c)-spread(shifted))<=2,"Saturation range preserved");
        check(Math.abs(value(c)-value(shifted))<=2,"Value preserved");
      }
    }
    int[] green=PaletteHueShift.rotate(ocean,-80);
    int[] pink=PaletteHueShift.rotate(ocean,110);
    check(!Arrays.equals(green,pink),"Palette variants must differ");
    // Reset must use original baseline, not a rotated/quantized previous value.
    check(Arrays.equals(PaletteHueShift.rotate(origin,0),origin),"Exact source recovery");
    check(PaletteHueShift.clampDegrees(1000)==180,"Clamp upper bound");
    check(PaletteHueShift.clampDegrees(-900)==-180,"Clamp lower bound");
    boolean rejected=false;try{PaletteHueShift.rotate(new int[2],50);}catch(IllegalArgumentException expected){rejected=true;}
    check(rejected,"Require an eight-swatch baseline");
    System.out.println("PASS 8-color hue wheel: exact reset, HSV attributes, hue wrapping and source immutability");
  }
}

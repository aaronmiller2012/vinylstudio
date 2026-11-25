import com.halftonestudio.MultilayerStencilEngine;
import com.halftonestudio.PaletteHueShift;
import java.util.Arrays;
public class UniversalHueTonalTest {
  static void check(boolean good,String message){if(!good)throw new AssertionError(message);}
  public static void main(String[]args){
    final int w=48,h=48;
    int[] src=new int[w*h];
    int[] palette={0xff326eca,0xffbf5734,0xff438e57,0xffae7cad};
    for(int y=0;y<h;y++)for(int x=0;x<w;x++)src[y*w+x]=palette[(y>=h/2?2:0)+(x>=w/2?1:0)];
    MultilayerStencilEngine.Options o=new MultilayerStencilEngine.Options();
    o.layerCount=4;o.sourceColorMode=true;o.autoWhiteBackground=false;
    o.blurRadius=0;o.widthMm=120;o.registration=false;o.outerSpacingMm=5;
    o.detail=.75f;o.minimumIslandPixels=1;
    MultilayerStencilEngine.Result base=MultilayerStencilEngine.generate(src,w,h,o);
    o.paletteHueDegrees=105;
    MultilayerStencilEngine.Result shifted=MultilayerStencilEngine.generate(src,w,h,o);
    check(base.layers.size()==4&&shifted.layers.size()==4,"Four layers generated");
    boolean changed=false;
    for(int i=0;i<base.layers.size();i++){
      MultilayerStencilEngine.Layer a=base.layers.get(i),b=shifted.layers.get(i);
      check(a.sourceIndex==b.sourceIndex,"Color change must preserve source ownership");
      check(Arrays.equals(a.mask,b.mask),"Hue slider must not alter physical stencil cuts");
      check(Arrays.equals(a.visibleMask,b.visibleMask),"Hue must not change visible regions");
      check(b.color==PaletteHueShift.rotateOne(a.color,105),"Layer hue must apply to export colors");
      if(b.color!=a.color)changed=true;
    }
    check(changed,"At least one swatch must change");
    o.paletteHueDegrees=0;
    MultilayerStencilEngine.Result recovered=MultilayerStencilEngine.generate(src,w,h,o);
    for(int i=0;i<4;i++)check(recovered.layers.get(i).color==base.layers.get(i).color,"Exact source reset");
    // Fixed/match-my-vinyl must also keep exact physical ownership under Hue.
    o.colors=palette.clone();o.matchVinylPalette=true;o.paletteHueDegrees=0;
    MultilayerStencilEngine.Result matched=MultilayerStencilEngine.generate(src,w,h,o);
    for(int degree:new int[]{60,120,180,-90}){
      o.paletteHueDegrees=degree;
      MultilayerStencilEngine.Result colorOnly=MultilayerStencilEngine.generate(src,w,h,o);
      for(int i=0;i<matched.layers.size();i++){
        MultilayerStencilEngine.Layer a=matched.layers.get(i),b=colorOnly.layers.get(i);
        check(a.sourceIndex==b.sourceIndex,"Matched palette Hue reordered physical sheets "+degree);
        check(Arrays.equals(a.mask,b.mask),"Matched palette Hue changed cuts "+degree);
        check(Arrays.equals(a.visibleMask,b.visibleMask),"Matched palette Hue changed owner "+degree);
        check(b.color==PaletteHueShift.rotateOne(a.color,degree),"Matched palette RGB not rotated "+degree);
      }
    }
    o.matchVinylPalette=false;o.colors=null;
    o.paletteHueDegrees=181;
    boolean rejected=false;
    try{MultilayerStencilEngine.generate(src,w,h,o);}catch(IllegalArgumentException ex){rejected=true;}
    check(rejected,"Reject out-of-bounds hue on host");
    System.out.println("PASS tonal hue: 4 source swatches, exact unmodified masks, shifted SVG colors and zero-degree reset");
  }
}

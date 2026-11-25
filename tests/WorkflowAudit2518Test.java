import com.halftonestudio.*;
import java.util.*;

public final class WorkflowAudit2518Test {
  static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
  public static void main(String[] args){
    check(CutMaterialProfile.names().length==5,"profiles");
    check(CutMaterialProfile.byId(3).gapMm>0,"material cut clearance");
    int w=110,h=130,white=0xfffaf9f6;int[] src=new int[w*h];Arrays.fill(src,white);
    for(int y=18;y<h-18;y++)for(int x=13;x<w-13;x++)src[y*w+x]=x<w/2?0xff414e66:0xff9e5671;
    var options=new MultiColorStippleEngine.Options();options.layerCount=2;
    options.widthMm=100;options.colors=new int[]{0xff414e66,0xff9e5671};options.detail=82;
    options.overprintGapMm=.08f;options.draft=true;
    var a=MultiColorStippleEngine.generate(src,w,h,options);
    check(a.accepted>0,"no stipple test dots");
    var v=StippleCoverageInspector.inspect(a,src,-1);
    check(v.width==w&&v.height==h&&v.eligible>0&&v.covered>0,"heatmap empty");
    check(v.eligible<w*h,"paper not excluded");
    check(v.heatmap[0]==0xff212933,"paper heatmap not excluded");
    var p=StipplePreflight.inspect(a,.2f,.08f);
    check(p.counts[0]+p.counts[1]==a.accepted,"preflight counts");
    check(p.overSvgLimit==0,"fixture exceeds SVG paths");
    var only0=StippleCoverageInspector.inspect(a,src,0);
    check(only0.covered<=v.covered,"single layer exceeds composite");
    options.overprintGapMm=.45f;
    check(options.copy().overprintGapMm==.45f,"gap copy lost");
    var spaced=MultiColorStippleEngine.generate(src,w,h,options);
    var preflight=StipplePreflight.inspect(spaced,.2f,.45f);
    check(preflight.tooClose==0,"configured same-layer clearance violated: "+preflight.tooClose);
    boolean[] mask=new boolean[w*h];Arrays.fill(mask,true);
    for(int y=35;y<52;y++)for(int x=30;x<70;x++)mask[y*w+x]=false;
    options.mask=mask;var cropped=MultiColorStippleEngine.generate(src,w,h,options);
    var scan=StippleCoverageInspector.inspect(cropped,src,-1);
    check(scan.heatmap[40*w+45]==0xff212933,"Snip mask not excluded");
    System.out.println("PASS 2518 profiles, circle heatmap, paper/Snip exclusion, per-layer diagnostics, physical gap and option copying");
  }
}

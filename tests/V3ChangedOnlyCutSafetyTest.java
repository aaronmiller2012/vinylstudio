package com.halftonestudio;
import java.util.Arrays;
/** Proves the shortcut is exercised only on isolated cut-boundaries. */
public final class V3ChangedOnlyCutSafetyTest {
  static void assertIt(boolean a,String s){if(!a)throw new AssertionError(s);}
  public static void main(String[] args){
    final int w=160,h=160;
    int[] p=new int[w*h];Arrays.fill(p,0xff888888);
    StyleKit.Source src=new StyleKit.Source(p,w,h,null,101.6f);
    PatternPortraitTrioEngine.Surface surface=new PatternPortraitTrioEngine.Surface(src,2,.8f);
    float[] importance=new float[surface.w*surface.h];
    for(int y=0;y<surface.h;y++)for(int x=0;x<surface.w;x++){
      float xx=(x+.5f)*surface.mm,yy=(y+.5f)*surface.mm;
      surface.ink[0][y*surface.w+x]=true;
      // Isolated ribbons spread far apart; older page-wide preflight did
      // needless work for the same cut sheet on each candidate.
      boolean first=xx>=14&&xx<=26&&(yy>10+2*Math.sin(yy/20.)&&yy<65);
      boolean second=xx>=70&&xx<=81&&(yy>22+3*Math.sin(xx/5.)&&yy<80);
      surface.ink[1][y*surface.w+x]=first||second;
    }
    StyleKit.Result result=surface.finish(new int[]{0xff000000,0xffeeeeee},"guard",2,.8f);
    OrganicFlowCutSmoother.resetChangedOnlyAcceptsForTest();
    OrganicFlowCutSmoother.contour(result,surface,.8f,.5f,50,importance);
    int accepts=OrganicFlowCutSmoother.changedOnlyAcceptsForTest();
    // If a fixture is too simple to curve-fit, it cannot prove the fast path.
    assertIt(accepts>0,"No isolated contour used the guarded changed-only path");
    System.out.println("PASS changed-contour-only safe acceptance, count="+accepts);
  }
}

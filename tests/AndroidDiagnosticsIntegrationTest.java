package com.halftonestudio;

import java.util.Arrays;

/** Tests status strings before, after and without background visibility diagnostics. */
public final class AndroidDiagnosticsIntegrationTest {
    private static void check(boolean ok, String message){if(!ok)throw new AssertionError(message);}
    private static int channel(VinylEngine.Result r,int c){
        for(int i=0;i<r.settings.layers.size();i++)if(r.settings.layers.get(i).cmykChannel==c)return i;
        throw new AssertionError("Missing CMYK channel " + c);
    }
    public static void main(String[] args){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.widthMm=82;s.marginMm=2;s.masterSpacingMm=3.8f;s.minimumMm=.32f;
        s.pattern=VinylEngine.ROUND;
        int w=54,h=64;int[] pixels=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int r=65+(x*130)/(w-1),g=60+(y*115)/(h-1),b=48+(x+y)*75/(w+h-2);
            pixels[y*w+x]=0xff000000 |(r<<16)|(g<<8)|b;
        }
        VinylEngine.Result picture=VinylEngine.generate(pixels,w,h,s);
        int cyan=channel(picture,0);
        String initial=LayerRenderStatus.describe(picture,null,cyan,null);
        check(initial.startsWith("Cyan · "+picture.shapes.get(cyan).size()+" cuts"),"exact count missing before diagnostics");
        check(initial.contains("Visibility calculating"),"pending state missing");
        VinylDiagnostics.Report diagnostics=VinylDiagnostics.analyze(picture,55);
        String completed=LayerRenderStatus.describe(picture,diagnostics,cyan,null);
        check(completed.contains("% covered")&&completed.contains("% visible"),"percentages absent after diagnostics");
        check(!completed.contains("calculating"),"completed status still pending");
        String failed=LayerRenderStatus.describe(picture,null,cyan,"Visibility estimate unavailable");
        check(failed.contains("Visibility estimate unavailable")&&failed.contains(" cuts"),"advisory failure hid exported cuts");
        check(LayerRenderStatus.describe(picture,null,-1,null).contains("SVG max"),"all layers summary changed");
        int[] white=new int[w*h];Arrays.fill(white,0xffffffff);
        VinylEngine.Result paper=VinylEngine.generate(white,w,h,s);
        check(paper.shapes.get(channel(paper,0)).size()==0,"white photograph unexpectedly produces cyan");
        String cyanHint=LayerRenderStatus.describe(paper,null,channel(paper,0),null);
        check(cyanHint.contains("Check Color → Shadow recovery"),"zero-cyan hint missing");
        check(LayerRenderStatus.describe(paper,null,-1,null).contains("0 cut shapes"),"empty artboard hint missing");
        for(int type:new int[]{VinylEngine.LINE,VinylEngine.STRAIGHT,VinylEngine.CONTOUR,VinylEngine.WAVE,VinylEngine.HATCH,
                                   VinylEngine.CROSSHATCH,VinylEngine.ZIGZAG,VinylEngine.BROKEN,VinylEngine.FADE})
            check(LineArtEngine.supportsPortraitMidtones(type),"missing slider support for ribbon style " +type);
        for(int type:new int[]{VinylEngine.RINGS,VinylEngine.RAYS,VinylEngine.BEADED})
            check(!LineArtEngine.supportsPortraitMidtones(type),"inert control offered for style " +type);
        s.pattern=VinylEngine.WAVE;s.portraitMidtones=0f;
        VinylEngine.Result noRecovery=VinylEngine.generate(pixels,w,h,s);
        s.portraitMidtones=.85f;
        VinylEngine.Result withRecovery=VinylEngine.generate(pixels,w,h,s);
        check(!VinylEngine.svg(noRecovery,channel(noRecovery,3)).equals(VinylEngine.svg(withRecovery,channel(withRecovery,3))),
              "Wavy Portrait Midtone Recovery slider has no effect on SVG output");
        System.out.println("PASS: immediate exact cut count; async coverage; advisory failure; cyan hint; empty canvas; line control availability; wavy slider changes SVG");
    }
}

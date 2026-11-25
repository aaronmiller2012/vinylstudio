package com.halftonestudio;
import java.util.Arrays;
/** v2.10.1 regression: stage navigation, light values, and empty-cut export protections. */
public final class LivePreviewLightTest {
    private static void check(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
    public static void main(String[] args){
        check(WorkflowFlow.next(WorkflowFlow.SNIP,1)==WorkflowFlow.LIGHT,"Light must follow Snip");
        check(WorkflowFlow.next(WorkflowFlow.LIGHT,1)==WorkflowFlow.LAYERS,"Layers must follow Light");
        check(WorkflowFlow.phaseOf(WorkflowFlow.LIGHT)==WorkflowFlow.PREPARE,"Light belongs to Prepare");
        check(WorkflowFlow.navigate(WorkflowFlow.LIGHT,true,true)==WorkflowFlow.SNIP,"pending Snip must block Light");
        VinylEngine.Settings s=VinylEngine.Settings.defaults();s.edgePriority=0;
        int w=64,h=48;int[] src=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int red=(x*240)/(w-1),green=(y*225)/(h-1),blue=(x+y)*125/(w+h-2);
            src[y*w+x]=0xff000000|(red<<16)|(green<<8)|blue;
        }
        int[] before=src.clone();
        s.exposureStops=.7f;s.photoContrast=1.2f;s.levelsShadows=.25f;
        int[] altered=ImageLevels.apply(src,w,h,s);
        check(!Arrays.equals(src,altered),"lighting must change output pixels");
        check(Arrays.equals(before,src),"lighting modified original pixels");
        check(s.copy().exposureStops==s.exposureStops&&s.copy().photoContrast==s.photoContrast,"settings copy lost lighting");
        s.widthMm=100;s.minimumMm=.30f;s.masterSpacingMm=3.5f;s.pattern=VinylEngine.ROUND;
        VinylEngine.Result a=VinylEngine.generate(src,w,h,s),b=VinylEngine.generate(altered,w,h,s);
        check(!VinylEngine.svg(a,0).equals(VinylEngine.svg(b,0)) ||
              !VinylEngine.svg(a,3).equals(VinylEngine.svg(b,3)),"light adjustments must alter real vinyl cuts");
        VinylEngine.Settings blank=VinylEngine.Settings.defaults();blank.mode=VinylEngine.STENCIL;
        blank.widthMm=100;blank.stencilPaperBackground=true;
        int[] paper=new int[w*h];Arrays.fill(paper,blank.paper);
        VinylEngine.Result noCuts=VinylEngine.generate(paper,w,h,blank);
        check(noCuts.count()==0,"white paper should not create colored stencil regions");
        check(VinylEngine.validateExport(noCuts)!=null,"empty cutting project must not export as valid");
        System.out.println("PASS: post-snip Light order; exposure/contrast/shadows; preserved source; cut geometry changes; empty exports rejected");
    }
}

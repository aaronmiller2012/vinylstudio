package com.halftonestudio;

import java.util.*;
/** v1.9.2 path limits, physical preset scaling, retaining overlapping vinyl. */
public final class VinylSizingTest {
    private static void require(boolean test,String label){if(!test)throw new AssertionError(label);}
    private static int[] solid(int w,int h,int color){int[] p=new int[w*h];Arrays.fill(p,color);return p;}
    static void testPresets(){
        for(VinylSizing.Size size:VinylSizing.SIZES){
            for(int pattern:new int[]{VinylEngine.ROUND,VinylEngine.LINE}){
                float fine=VinylSizing.spacingMm(size.widthIn,size.heightIn,pattern,VinylSizing.DETAIL);
                float balanced=VinylSizing.spacingMm(size.widthIn,size.heightIn,pattern,VinylSizing.BALANCED);
                float easy=VinylSizing.spacingMm(size.widthIn,size.heightIn,pattern,VinylSizing.EASY_WEED);
                require(fine<=balanced&&balanced<=easy,"preset order "+size.label+" pattern "+pattern);
                require(fine>=1.7&&easy<=9.15,"preset size mm range");
                VinylEngine.Settings s=VinylEngine.Settings.defaults();s.pattern=pattern;s.marks=new float[][]{{1,1},{2,2},{3,3},{4,4}};
                VinylSizing.apply(s,size.widthIn,size.heightIn,VinylSizing.BALANCED);
                require(s.linkedControls,"shared spacing");require(Math.abs(s.widthMm-25.4f*size.widthIn)<.01f,"physical width");
                require(s.marks!=null,"size profiling must not silently discard manual registration marks");
            }
        }
    }
    static void testPerSvgAndOverlap(){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();s.mode=VinylEngine.SPOT;s.pattern=VinylEngine.ROUND;
        s.widthMm=140;s.marginMm=2;s.minimumMm=.25f;s.bridgeMm=.2f;s.masterSpacingMm=3f;
        s.anglePreset=VinylEngine.ANGLES_PARALLEL;s.screenRotationDeg=-45;
        for(VinylEngine.Layer l:s.layers)l.color=0xff222222;
        VinylEngine.Result r=VinylEngine.generate(solid(44,44,0xff222222),44,44,s);
        require(r.count()>5000,"combined project should be permitted to exceed 5000");
        require(VinylEngine.validateExport(r)==null,"each individual SVG must be within Cricut limit");
        for(int i=0;i<r.shapes.size();i++)require(VinylEngine.svgElementsForLayer(r,i)<5000,"per-color limit");
        require(!r.shapes.get(0).isEmpty()&&!r.shapes.get(1).isEmpty(),"spot layers populated");
        // Since V2.32 spot screens automatically separate, aligned first dots are no longer expected.
        // Both layers must still have independent closed dot paths that may overlap elsewhere.
        VinylEngine.Shape a=r.shapes.get(0).get(0),b=r.shapes.get(1).get(0);
        require(a.circle&&b.circle,"spot-color geometry must remain round closed shapes");
        require(r.settings.layers.get(0).angle!=r.settings.layers.get(1).angle,"spot screens must separate automatically");
        require(VinylEngine.registrationElements(r)==4,"cross marks are a single closed shape per registration point");
    }
    static void testImageAwareFit(){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();s.mode=VinylEngine.SPOT;
        s.pattern=VinylEngine.ROUND;s.widthMm=110;s.masterSpacingMm=1.6f;
        for(VinylEngine.Layer l:s.layers)l.color=0xff282828;
        VinylSizing.Fit fit=VinylSizing.fit(solid(40,40,0xff282828),40,40,s,VinylSizing.BALANCED);
        require(fit.maxLayer<=VinylSizing.targetPaths(VinylSizing.BALANCED),"real SVG count must fit quality budget");
        require(VinylEngine.validateExport(fit.result)==null,"Cricut SVG export is safe");
        require(fit.settings.linkedControls,"fit maintains global spacing continuity");
    }
    public static void main(String[] args){testPresets();testPerSvgAndOverlap();testImageAwareFit();System.out.println("PASS: inch-size presets, per-SVG cap beyond 5000 total, intentional spot overlap, auto-fit path budgets");}
}

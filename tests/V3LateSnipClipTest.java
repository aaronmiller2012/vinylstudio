package com.halftonestudio;

import java.util.List;

/** Post-tone Snip application: unchanged retained carriers and safe cut contours. */
public final class V3LateSnipClipTest {
    private static void ok(boolean b,String message){if(!b)throw new AssertionError(message);}
    private static SnipMask rectangularKeep(){
        SnipMask m=new SnipMask();
        m.add(new SnipMask.Operation(SnipMask.KEEP_INSIDE,
            .08f,.07f,.92f,.07f,.92f,.93f,.08f,.93f));
        return m;
    }
    private static SnipMask keepAndHole(){
        SnipMask m=rectangularKeep();
        m.add(new SnipMask.Operation(SnipMask.REMOVE_INSIDE,
            .48f,.30f,.52f,.30f,.52f,.67f,.48f,.67f));
        return m;
    }
    private static void safe(VinylEngine.Result r){
        int n=0;
        for(List<VinylEngine.Shape> layer:r.shapes)
            for(VinylEngine.Shape shape:layer){
                ok(!shape.circle,"unexpected circle in parallel line test");
                ok(r.settings.snips.allowsPolygon(shape.xy,r.settings.widthMm,r.heightMm),
                    "an exported ribbon crosses a keep/exclusion boundary");
                ok(VinylEngine.insideCutBoundary(shape,r.settings.widthMm,r.heightMm,
                    r.settings.marginMm,r.settings.circleCrop),"export extends beyond artwork");
                n++;
            }
        ok(n>15,"Snip eliminated almost all cut geometry");
    }
    public static void main(String[] args){
        int w=130,h=150;int[] pixels=V3CropSnipCutParityTest.photo(w,h);
        int cases=0;
        for(boolean classic:new boolean[]{true,false})
          for(int style:new int[]{VinylEngine.LINE,VinylEngine.CONTOUR,VinylEngine.HATCH})
           for(float pitch:new float[]{3.175f,6.223f}){
            VinylEngine.Settings plain=V3CropSnipCutParityTest.config(false,classic,style,pitch);
            VinylEngine.Result original=VinylEngine.generate(pixels,w,h,plain);
            for(SnipMask mask:new SnipMask[]{rectangularKeep(),keepAndHole()}){
                VinylEngine.Settings candidate=plain.copy();candidate.snips=mask;
                VinylEngine.Result cropped=VinylEngine.generate(pixels,w,h,candidate);
                safe(cropped);
                for(int li=0;li<original.lineMetrics.size();li++){
                    LinePresenceTracker.Metrics a=original.lineMetrics.get(li),b=cropped.lineMetrics.get(li);
                    ok(a.promoted==b.promoted,
                        "Snip changed full-image line recovery style="+style+" layer="+li+
                        " original="+a.promoted+" clipped="+b.promoted);
                }
                // Compare far from both the keep boundary and central hole.
                int samples=0,mismatches=0;
                for(int y=0;y<21;y++)for(int x=0;x<21;x++){
                    float nx=.18f+x*.006f,ny=.20f+y*.006f;
                    float mmx=nx*original.settings.widthMm,mmy=ny*original.heightMm;
                    for(int li=0;li<original.shapes.size();li++){
                        boolean a=V3CropSnipCutParityTest.occupied(original.shapes.get(li),mmx,mmy);
                        boolean b=V3CropSnipCutParityTest.occupied(cropped.shapes.get(li),mmx,mmy);
                        samples++;if(a!=b)mismatches++;
                    }
                }
                ok(mismatches==0,"Protected interior geometry changed after Snip: "+mismatches+"/"+samples);
                // Removed central rectangle must be absent on all layers.
                if(mask.size()==2)for(int li=0;li<cropped.shapes.size();li++)
                    ok(!V3CropSnipCutParityTest.occupied(cropped.shapes.get(li),
                        cropped.settings.widthMm*.5f,cropped.heightMm*.5f),
                        "a cut crosses a removed hole");
                cases++;
            }
          }
        System.out.println("PASS vector-late Snip: "+cases+
            " full-photo CMYK line/Contour/Hatch comparisons, hole topology, exact interior fidelity");
    }
}

package com.halftonestudio;

import java.util.*;

/** Regression tests for Cricut-safe, shared, nondestructive polygon snipping. */
public final class SnipMaskTest {
    static void ok(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static int[] source(int w,int h){int[] pixels=new int[w*h];Arrays.fill(pixels,0xff141414);return pixels;}
    static SnipMask.Operation removeRectangle(){return new SnipMask.Operation(SnipMask.REMOVE_INSIDE,
            .38f,.21f, .62f,.21f, .62f,.79f, .38f,.79f);}
    static SnipMask.Operation keepRectangle(){return new SnipMask.Operation(SnipMask.KEEP_INSIDE,
            .16f,.14f, .86f,.14f, .86f,.84f, .16f,.84f);}
    static void bad(Runnable fn,String reason){try{fn.run();throw new AssertionError("Accepted invalid snip: "+reason);}catch(IllegalArgumentException expected){}}
    static void validation(){
        bad(()->new SnipMask.Operation(SnipMask.REMOVE_INSIDE,.1f,.1f,.9f,.9f),"two vertices");
        bad(()->new SnipMask.Operation(SnipMask.REMOVE_INSIDE,-.1f,.1f,.3f,.1f,.4f,.4f),"outside photo");
        bad(()->new SnipMask.Operation(SnipMask.REMOVE_INSIDE,.1f,.1f,.9f,.9f,.1f,.9f,.9f,.1f),"crossing edges");
        bad(()->new SnipMask.Operation(SnipMask.REMOVE_INSIDE,.1f,.1f,.1f,.1f,.5f,.6f),"duplicate vertices");
        SnipMask mask=new SnipMask();mask.add(removeRectangle());ok(mask.allows(.2f,.5f),"outside removed area should be allowed");
        ok(!mask.allows(.5f,.5f),"inside removed area should be excluded");
        ok(!mask.safeSegment(20,50,80,50,100,100),"segment crosses removal");
        ok(mask.safeSegment(4,50,20,50,100,100),"outside segment should remain");
        SnipMask copy=mask.copy();copy.clear();ok(mask.size()==1,"original mask mutated by copy");
        mask.add(keepRectangle());ok(!mask.allows(.05f,.05f),"keep-area must reject outside");
        ok(mask.allows(.25f,.3f),"point inside keep and outside removal allowed");
        mask.removeLast();ok(mask.size()==1,"undo last operation failed");
        // Polygon strictly covering a removed snip must be excluded even if no edges cross.
        ok(!mask.allowsPolygon(new float[]{10,10,90,10,90,90,10,90},100,100),"enclosed removal hole accepted");
        ok(!mask.allowsCircle(50,50,30,100,100),"large circle enclosing snip accepted");
    }
    static void allLayersAndPatterns(){
        int[] pixels=source(48,46);
        for(int count:new int[]{2,4,8}){
            VinylEngine.Settings settings=VinylEngine.Settings.defaults();
            while(settings.layers.size()<count)settings.layers.add(new VinylEngine.Layer("Vinyl "+settings.layers.size(),VinylEngine.START_COLORS[settings.layers.size()]));
            while(settings.layers.size()>count)settings.layers.remove(settings.layers.size()-1);
            settings.mode=VinylEngine.SPOT;
            settings.widthMm=108;
            settings.minimumMm=.34f;
            settings.masterSpacingMm=4.4f;
            settings.snips.add(removeRectangle());
            // -1 is the exclusive-mosaic mode; 0..11 are all twelve vector patterns.
            for(int pattern=-1;pattern<VinylEngine.PATTERNS.length;pattern++){
                settings.pattern=pattern<0?VinylEngine.ROUND:pattern;
                settings.mode=pattern<0?VinylEngine.MOSAIC:VinylEngine.SPOT;
                VinylEngine.Result result=VinylEngine.generate(pixels,48,46,settings);
                ok(result.settings.snips.size()==1,"snip lost in engine copy");
                int remaining=0;
                for(int layer=0;layer<count;layer++){
                    for(VinylEngine.Shape shape:result.shapes.get(layer)){
                        boolean allowed=shape.circle?settings.snips.allowsCircle(shape.x,shape.y,shape.r,result.settings.widthMm,result.heightMm):
                            settings.snips.allowsPolygon(shape.xy,result.settings.widthMm,result.heightMm);
                        ok(allowed,"masked geometry in layer "+layer+" pattern "+pattern);
                        remaining++;
                    }
                    String svg=VinylEngine.svg(result,layer);
                    ok(!svg.contains("<clipPath")&&!svg.contains("clip-path")&&!svg.contains("<mask"),"Cricut-incompatible clipping SVG");
                    ok(svg.contains("width=\"")&&svg.contains("viewBox=\""),"export dimensions missing");
                    ok(VinylEngine.svgElementsForLayer(result,layer)<=5000,"SVG complexity exceeded");
                }
                ok(remaining>0,"snip erased entire artwork for "+pattern);
                ok(VinylEngine.validateMarks(result)==null,"registration safety changed for "+pattern);
                if(pattern==VinylEngine.LINE&&count==4){
                    int left=0,right=0;
                    for(VinylEngine.Shape sh:result.shapes.get(0)){
                        float[] bounds=sh.bounds();
                        if(bounds[2]<settings.widthMm*.38f)left++;
                        if(bounds[0]>settings.widthMm*.62f)right++;
                    }
                    ok(left>0&&right>0,"lines failed to continue on both sides of removed region");
                }
            }
        }
    }
    static void keepOnly(){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();s.minimumMm=.34f;s.widthMm=100;s.masterSpacingMm=4.5f;
        s.snips.add(keepRectangle());
        VinylEngine.Result r=VinylEngine.generate(source(40,40),40,40,s);
        int total=0;
        for(java.util.List<VinylEngine.Shape> layer:r.shapes)for(VinylEngine.Shape sh:layer){
            ok(sh.circle?s.snips.allowsCircle(sh.x,sh.y,sh.r,r.settings.widthMm,r.heightMm):s.snips.allowsPolygon(sh.xy,r.settings.widthMm,r.heightMm),"keep inside failed");total++;
        }
        ok(total>0,"keep inside removed everything");
    }
    public static void main(String[] args){validation();allLayersAndPatterns();keepOnly();
        System.out.println("PASS: polygon snip validation, undo/copy, 2-8 layer masks, all 12 styles + mosaic, Cricut-ready SVG geometry, line splitting, keep/remove, registration");}
}

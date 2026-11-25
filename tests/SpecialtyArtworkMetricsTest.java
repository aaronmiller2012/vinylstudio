package com.halftonestudio;
import java.util.*;
public final class SpecialtyArtworkMetricsTest {
    private static void eq(double a,double b){if(Math.abs(a-b)>1e-6)throw new AssertionError(a+" != "+b);}
    public static void main(String[] args){
        StyleKit.Result r=new StyleKit.Result(20,20);StyleKit.Layer l=new StyleKit.Layer("color-1",0xff000000);r.layers.add(l);
        float[] outer={0,0,10,0,10,10,0,10};l.loops.add(outer);l.loops.add(new float[]{2,2,4,2,4,4,2,4});l.lines.add(new float[]{0,0,3,4});
        SpecialtyArtworkMetrics.Input in=SpecialtyArtworkMetrics.collect(r,VinylStyleSuite.LINOCUT,null,null,null,null,null,false,()->{});
        eq(in.openLength.get(0),5);eq(in.sheets.get(0).size(),2);eq(SpecialtyArtworkMetrics.length(outer,true),40);
        ArtworkVisibility.Layer[] m=ArtworkVisibility.measureSpecialty(in.sheets,in.independent,()->{});eq(m[0].area,96);eq(m[0].holes,1);
        in.sheet("independent");in.piece(1,outer,Collections.emptyList(),1);in.piece(1,new float[]{5,0,15,0,15,10,5,10},Collections.emptyList(),1);
        m=ArtworkVisibility.measureSpecialty(in.sheets,in.independent,()->{});eq(m[1].area,150);eq(m[0].visible,0);eq(m[0].hidden,96);
        if(outer[0]!=0||outer[2]!=10)throw new AssertionError("Mutated source");
        // Identical swatches fully overlap; actual order 1 -> 0 must make swatch 0 visible.
        MultiColorStippleEngine.Options dots=new MultiColorStippleEngine.Options();dots.layerCount=2;dots.order=new int[]{1,0};
        MultiColorStippleEngine.Result dotsResult=new MultiColorStippleEngine.Result(dots,20,20,Collections.emptyList(),Collections.emptyList(),Collections.emptyList(),0,0,0,0,new float[2]);
        TessellationEngine.Options tiles=new TessellationEngine.Options();tiles.layerCount=2;tiles.widthMm=20;
        TessellationEngine.Result stack=new TessellationEngine.Result(20,20,tiles,Arrays.asList(new TessellationEngine.Tile(outer,0,0),new TessellationEngine.Tile(outer,1,0)),dotsResult);
        SpecialtyArtworkMetrics.Input order=SpecialtyArtworkMetrics.collect(null,0,stack,null,null,null,null,false,()->{});
        if(!order.names.equals(Arrays.asList("Color 2","Color 1")))throw new AssertionError("Physical order lost");
        m=ArtworkVisibility.measureSpecialty(order.sheets,order.independent,()->{});eq(m[0].visible,0);eq(m[0].hidden,100);eq(m[1].visible,100);
        final int[] checks={0};
        try{SpecialtyArtworkMetrics.report(order,.25f,.5f,()->{if(++checks[0]>2)throw new java.util.concurrent.CancellationException();});throw new AssertionError("Cancellation swallowed");}catch(java.util.concurrent.CancellationException expected){}
        SpecialtyArtworkMetrics.Input path=new SpecialtyArtworkMetrics.Input();path.sheet("stencil");
        SpecialtyArtworkMetrics.appendPath(path,0,"M0 0L10 0L10 10L0 10Z",()->{});eq(SpecialtyArtworkMetrics.length(path.sheets.get(0).get(0),true),40);
        SpecialtyArtworkMetrics.appendPath(path,0,"M0 0C1 0 2 0 3 0L3 3L0 3Z",()->{});eq(SpecialtyArtworkMetrics.length(path.sheets.get(0).get(1),true),12);
        SpecialtyArtworkMetrics.appendPath(path,0,"M0 0Q5 0 10 0L10 10L0 10Z",2f,()->{});eq(SpecialtyArtworkMetrics.length(path.sheets.get(0).get(2),true),80);
        try{SpecialtyArtworkMetrics.appendPath(path,0,"M0 0L1 1",()->{});throw new AssertionError();}catch(IllegalArgumentException expected){}
        SpecialtyArtworkMetrics.Input dense=new SpecialtyArtworkMetrics.Input();dense.sheet("dense");
        for(int i=0;i<31000;i++)dense.ring(0,outer,1);
        if(!dense.footprintLimited||!dense.sheets.get(0).isEmpty())throw new AssertionError("Footprint budget");
        eq(dense.closed.get(0),31000);eq(dense.closedLength.get(0),1240000);
        if(!SpecialtyArtworkMetrics.report(dense,()->{}).contains("31000 closed contours"))throw new AssertionError("Basic measurements lost");
        int w=180,h=180;int[] photo=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int g=(x+y)*255/(w+h-2);photo[y*w+x]=0xff000000|g*0x010101;}
        SpiralPortraitRibbon.Options so=new SpiralPortraitRibbon.Options();so.widthMm=279.4f;so.detail=100;so.minCutMm=.254f;so.minGapMm=.254f;
        SpiralPortraitRibbon.Result sr=SpiralPortraitRibbon.generate(photo,w,h,so);
        SpecialtyArtworkMetrics.Input si=SpecialtyArtworkMetrics.collectSpiral(sr,null,false,()->{});
        eq(si.closed.get(0),sr.cutPieces);if(si.closedLength.get(0)<=0)throw new AssertionError("Spiral length");
        MultilayerStencilEngine.Options to=new MultilayerStencilEngine.Options();to.widthMm=150;to.heightMm=150;to.layerCount=4;to.registration=false;to.autoWhiteBackground=false;
        MultilayerStencilEngine.Result tr=MultilayerStencilEngine.generate(photo,w,h,to);
        SpecialtyArtworkMetrics.Input ti=SpecialtyArtworkMetrics.collect(null,0,null,null,tr,null,null,false,()->{});
        if(ti.closedLength.stream().mapToDouble(Double::doubleValue).sum()<=0)throw new AssertionError("Tonal length");
        System.out.println("PASS generated spiral and tonal contour measurements");
        System.out.println("PASS specialty parity holes, independent-piece union, occlusion, open length, stencil path parsing");
    }
}

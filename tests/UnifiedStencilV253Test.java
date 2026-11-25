package com.halftonestudio;

import java.util.Arrays;
import java.util.Random;

/** Standalone deterministic host acceptance of the new physical stencil pipeline. */
public final class UnifiedStencilV253Test {
    private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    private static MultilayerStencilEngine.Options options(){
        MultilayerStencilEngine.Options o=new MultilayerStencilEngine.Options();
        o.sourceColorMode=true;o.registration=false;o.autoWhiteBackground=false;
        o.layerCount=3;o.minimumIslandPixels=1;o.minimumIslandAreaMm2=0;o.detail=1f;
        o.widthMm=120;o.heightMm=80;return o;
    }
    private static String cuts(String svg){int p=svg.indexOf(" d=\"");if(p<0)return "";int start=p+4;return svg.substring(start,svg.indexOf('"',start));}
    private static void paletteReallyChangesOwnership(){
        int w=90,h=60,n=w*h;int[] src=new int[n];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)src[y*w+x]=x<30?0xfffa3520:x<60?0xff2629ed:0xff20e340;
        MultilayerStencilEngine.Options o=options();o.matchVinylPalette=true;
        o.colors=new int[]{0xfff00f0f,0xff1111f9,0xff14e51a};
        MultilayerStencilEngine.Result r=MultilayerStencilEngine.generate(src,w,h,o);
        int[] sourceForPoint=new int[n];Arrays.fill(sourceForPoint,-1);
        for(MultilayerStencilEngine.Layer layer:r.layers){
            check(layer.color==o.colors[layer.sourceIndex],"wrong physical roll mapped");
            for(int i=0;i<n;i++)if(layer.visibleMask[i]){
                check(sourceForPoint[i]==-1,"owners overlap");sourceForPoint[i]=layer.sourceIndex;
            }
        }
        for(int i=0;i<n;i++)check(sourceForPoint[i]==(i%w<30?0:i%w<60?1:2),"not actually matched to vinyl palette at "+i);
        o.colors[1]=0;boolean threw=false;
        try{MultilayerStencilEngine.generate(src,w,h,o);}catch(IllegalArgumentException e){threw=true;}
        check(threw,"missing swatch must be rejected instead of silently painting source cluster");
    }
    private static void curvedPathsStayClosedAndPhysical(){
        int w=100,h=90;int[] src=new int[w*h];Arrays.fill(src,0xffece5db);
        for(int y=10;y<80;y++)for(int x=12;x<85;x++)if((x-48)*(x-48)+(y-45)*(y-45)<32*32)src[y*w+x]=0xff25365a;
        MultilayerStencilEngine.Options o=options();o.widthMm=100;o.heightMm=90;
        MultilayerStencilEngine.Result r=MultilayerStencilEngine.generate(src,w,h,o);
        int curved=0;
        for(int i=0;i<r.layers.size();i++){
            String svg=r.svg(i);
            check(svg.startsWith("<svg")&&svg.contains("mm\""),"physical artboard missing");
            check(!svg.contains("NaN")&&!svg.contains("Infinity")&&!svg.contains("clipPath"),"bad SVG geometry");
            check(svg.contains("fill-rule=\"evenodd\""),"holes must use even-odd winding");
            if(!r.isEmpty(i))check(svg.contains("Z"),"open cutting path");
            if(cuts(svg).contains("C"))curved++;
            int moves=0;for(int p=0;p<svg.length();p++)if(svg.charAt(p)=='M')moves++;
            check(moves==MultilayerStencilEngine.countUnionContours(r.layers.get(i).mask,w,h),"vector/physical cut count differs");
            check(r.layers.get(i).pixelCount>=r.layers.get(i).visiblePixelCount,"inverted support");
        }
        check(curved>=1,"new curve fitting is not active");
        o.curveRadiusMm=0;
        MultilayerStencilEngine.Result exact=MultilayerStencilEngine.generate(src,w,h,o);
        for(int i=0;i<exact.layers.size();i++)check(!cuts(exact.svg(i)).contains("C"),"exact/legacy mode generated curves");
    }
    private static void topologyAndNestedUnderlays(){
        Random rnd=new Random(2530);
        for(int t=0;t<90;t++){
            int w=4+rnd.nextInt(20),h=4+rnd.nextInt(20);
            boolean[] mask=new boolean[w*h];
            for(int i=0;i<mask.length;i++)mask[i]=rnd.nextDouble()>.57;
            int count=MultilayerStencilEngine.countUnionContours(mask,w,h);
            StringBuilder out=new StringBuilder();
            MultilayerStencilEngine.appendUnionContours(out,mask,w,h,47,45,3);
            int moves=0;for(int i=0;i<out.length();i++)if(out.charAt(i)=='M')moves++;
            check(moves==count,"diagonal / hole topology mismatch t="+t);
            check(count>=0&&count<=mask.length,"invalid connected component count");
        }
        int w=180,h=100,n=w*h;int[] pixels=new int[n];Arrays.fill(pixels,0x00000000);
        for(int y=15;y<87;y++)for(int x=20;x<160;x++){
            pixels[y*w+x]=x<80?0xffd79c8a:x<110?0xff7f5540:0xff252127;
        }
        MultilayerStencilEngine.Options o=options();o.widthMm=180;o.heightMm=100;
        MultilayerStencilEngine.Result r=MultilayerStencilEngine.generate(pixels,w,h,o);
        for(int i=0;i<r.layers.size();i++){
            MultilayerStencilEngine.Layer layer=r.layers.get(i);
            for(int p=0;p<n;p++){
                if(layer.mask[p])check((pixels[p]>>>24)!=0,"trap leaked into snipped/transparent substrate");
                if(i>0&&layer.mask[p])check(r.layers.get(i-1).mask[p],"upper layer missing physical underlay");
            }
        }
    }
    private static void physicalGapsAndCancellation(){
        int w=100,h=40;int[] px=new int[w*h];Arrays.fill(px,0xffeeeedd);
        for(int y=10;y<30;y++)for(int x=20;x<80;x++)px[y*w+x]=0xff303030;
        for(int y=10;y<30;y++)px[y*w+50]=0xffeeeedd; // narrow negative-space line
        MultilayerStencilEngine.Options o=options();o.layerCount=2;o.widthMm=40;o.heightMm=16;
        MultilayerStencilEngine.Result r=MultilayerStencilEngine.generate(px,w,h,o);
        boolean sawGap=false;
        for(int i=0;i<r.layers.size();i++){
            VinylCutPreflight.Report report=VinylCutPreflight.inspect(r,i,.5f,.6f);
            if(report.narrowGapPixelsHorizontal>0)sawGap=true;
        }
        check(sawGap,"physical gap validator missed a thin internal gap");
        boolean[] lower=new boolean[16*16],upper=new boolean[16*16],allowed=new boolean[16*16];
        Arrays.fill(allowed,true);upper[8*16+8]=true;lower[8*16+8]=true;allowed[8*16+9]=false;
        int added=MultilayerStencilEngine.addTrappedUnderlay(lower,upper,allowed,16,16,16,16,1.2f);
        check(added>0,"trap implementation did not grow tested underlay");
        check(!lower[8*16+9],"trap crossed forbidden region");
        Thread.currentThread().interrupt();boolean cancelled=false;
        try{MultilayerStencilEngine.generate(px,w,h,o);}catch(RuntimeException e){cancelled=true;}finally{Thread.interrupted();}
        check(cancelled,"render cancellation not honored");
    }
    public static void main(String[] args){
        paletteReallyChangesOwnership();
        curvedPathsStayClosedAndPhysical();
        topologyAndNestedUnderlays();
        physicalGapsAndCancellation();
        System.out.println("PASS v2.53: selected-roll ownership, rounded SVG physical cuts, topology/holes, nested masks, physical gaps, trap bounds, cancellation");
    }
}

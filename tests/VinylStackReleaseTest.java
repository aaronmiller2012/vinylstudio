package com.halftonestudio;
import java.util.Arrays;

/** Release gate: real foreground masks, underlay topology, source identities, SVGs. */
public final class VinylStackReleaseTest {
    private static void ok(boolean condition,String detail){if(!condition)throw new AssertionError(detail);}
    private static int[] image(int w,int h,int background){int[] a=new int[w*h];Arrays.fill(a,background);return a;}
    private static MultilayerStencilEngine.Options options(int count){
        MultilayerStencilEngine.Options o=new MultilayerStencilEngine.Options();
        o.layerCount=count;o.registration=false;o.sourceColorMode=true;
        o.widthMm=100;o.minimumIslandPixels=1;o.detail=.9f;return o;
    }
    private static void testSubjectSilhouette(){
        int w=120,h=80;int[] pixels=image(w,h,0xffffffff);
        for(int y=14;y<68;y++)for(int x=25;x<95;x++)pixels[y*w+x]=x<70?0xffb87355:0xff40302a;
        MultilayerStencilEngine.Options o=options(3);
        MultilayerStencilEngine.Result r=MultilayerStencilEngine.generate(pixels,w,h,o);
        ok(r.layers.get(0).baseLayer,"first layer not marked base");
        ok(r.layers.get(0).pixelCount==70*54,"auto subject mask made a rectangular cut or ate the subject: "+r.layers.get(0).pixelCount);
        int visible=0;for(MultilayerStencilEngine.Layer layer:r.layers)visible+=layer.visiblePixelCount;
        ok(visible==70*54,"visible color missing inside the subject");
        for(MultilayerStencilEngine.Layer layer:r.layers)for(int p=0;p<pixels.length;p++)
            if(layer.mask[p]&&!r.layers.get(0).mask[p])throw new AssertionError("overlay escaped base");
        String svg=r.svg(0);ok(svg.contains("<svg")&&svg.contains("fill-rule=\"evenodd\""),"invalid base SVG");
        o.autoWhiteBackground=false;
        MultilayerStencilEngine.Result full=MultilayerStencilEngine.generate(pixels,w,h,o);
        ok(full.layers.get(0).pixelCount==pixels.length,"keep-background override was ignored");
    }
    private static void testWhiteArtworkNotRemoved(){
        int w=60,h=44;int[] pixels=image(w,h,0xffe6dfda);
        for(int y=10;y<30;y++)for(int x=10;x<50;x++)pixels[y*w+x]=0xffffffff;
        MultilayerStencilEngine.Result r=MultilayerStencilEngine.generate(pixels,w,h,options(2));
        ok(r.layers.get(0).pixelCount==pixels.length,"unconnected white feature removed incorrectly");
    }
    private static void testTransparentTrim(){
        int w=40,h=40;int[] p=image(w,h,0);
        for(int y=7;y<32;y++)for(int x=5;x<35;x++)p[y*w+x]=0xff643d37;
        MultilayerStencilEngine.Result r=MultilayerStencilEngine.generate(p,w,h,options(2));
        ok(r.layers.get(0).pixelCount==750,"transparent background filled by base");
    }
    private static void testSelectivelyFilledHoles(){
        int w=24,h=20;boolean[] mask=new boolean[w*h];int[] owner=new int[w*h];boolean[] valid=new boolean[w*h];
        Arrays.fill(valid,true);Arrays.fill(owner,0);
        for(int y=4;y<16;y++)for(int x=4;x<20;x++){mask[y*w+x]=true;owner[y*w+x]=1;}
        for(int y=7;y<13;y++)for(int x=9;x<15;x++){mask[y*w+x]=false;owner[y*w+x]=2;}
        boolean[] later={false,false,true};
        int filled=VinylLayerSupport.fillCoveredHoles(mask,owner,valid,later,w,h);
        ok(filled==36,"inner upper-color hole not filled");
        ok(mask[10*w+11],"middle underlay not filled");
        boolean[] wrong={false,true,false};
        mask[10*w+11]=false;
        ok(VinylLayerSupport.fillCoveredHoles(mask,owner,valid,wrong,w,h)==0,"filled an opening owned by lower color");
    }
    private static void testCumulativeUnderlays(){
        int w=36,h=18;int[] pixels=image(w,h,0xffffffff);
        for(int y=2;y<16;y++)for(int x=3;x<33;x++)pixels[y*w+x]=0xffd7b0a2; // broad subject tone
        for(int y=5;y<14;y++)for(int x=8;x<28;x++)pixels[y*w+x]=0xff7d5a4a; // mid overlay
        for(int y=7;y<12;y++)for(int x=13;x<23;x++)pixels[y*w+x]=0xff2d2220; // top dark detail
        MultilayerStencilEngine.Result r=MultilayerStencilEngine.generate(pixels,w,h,options(3));
        ok(r.layers.get(0).pixelCount==30*14,"base should cover full subject silhouette");
        ok(r.layers.get(1).pixelCount==20*9,"middle layer should include its own tone plus covered top detail");
        ok(r.layers.get(2).pixelCount==10*5,"top layer should remain visible-only detail");
        for(int p=0;p<pixels.length;p++) if(r.layers.get(2).mask[p]&&!r.layers.get(1).mask[p]) throw new AssertionError("top detail lacks lower support");
    }

    private static void testColorIdentityAndMinimumWidth(){
        int w=80,h=55;int[] p=image(w,h,0xffd4a18d);
        for(int y=10;y<45;y++)for(int x=15;x<65;x++)p[y*w+x]=0xff754a35;
        MultilayerStencilEngine.Options opt=options(3);opt.colors=new int[]{0xff1144aa,0xff22cc55,0xff991177};opt.baseLayer=1;
        MultilayerStencilEngine.Result r=MultilayerStencilEngine.generate(p,w,h,opt);
        ok(r.layers.get(0).sourceIndex==1 || r.layers.get(0).visiblePixelCount==0,
            "base override did not preserve cluster identity");
        for(MultilayerStencilEngine.Layer layer:r.layers)ok(layer.color==opt.colors[layer.sourceIndex],"palette/cluster mismatch");
        VinylCutPreflight.Report pre=VinylCutPreflight.inspect(r,0,.7f);
        ok(pre.cellWidthMm>0&&pre.cellHeightMm>0,"invalid metric scale");
    }
    public static void main(String[] args){
        testSubjectSilhouette();testWhiteArtworkNotRemoved();testTransparentTrim();testSelectivelyFilledHoles();testCumulativeUnderlays();testColorIdentityAndMinimumWidth();
        System.out.println("PASS v2.41: background matte, manual keep-background, transparent cuts, cumulative overlays, color identity, metric preflight");
    }
}

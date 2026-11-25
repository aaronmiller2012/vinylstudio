package com.halftonestudio;

import java.util.Arrays;

/** v2.37: detail presets and white-speckle regression tests. */
public final class StencilRegionRefinerTest {
    private static void ok(boolean p,String why){if(!p)throw new AssertionError(why);}
    private static void testSinglePixelPinholes(){
        int w=41,h=37,k=8;int[] owner=new int[w*h];boolean[] valid=new boolean[owner.length];
        Arrays.fill(owner,0);Arrays.fill(valid,true);
        // Three isolated speckles, in the middle of a broad black region.
        owner[18*w+19]=1;owner[18*w+21]=2;owner[23*w+18]=3;
        StencilRegionRefiner.Output result=StencilRegionRefiner.refine(owner,valid,w,h,k,.45f,20);
        for(int i=0;i<owner.length;i++)ok(result.owner[i]==0,"colored speckle survived at "+i);
        ok(result.changedPixels==3,"unexpected changes on a solid field");
        ok(owner[18*w+19]==1,"mutated input map");
    }
    private static void testPhotoRegionsAndSnips(){
        int w=80,h=48,k=5;int[] owner=new int[w*h];boolean[] valid=new boolean[owner.length];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int p=y*w+x;valid[p]=!(x>=35&&x<45&&y>=16&&y<25);
            owner[p]=valid[p]?Math.min(k-1,x*k/w):-1;
        }
        // Introduce alternating color noise, but don't erase the five genuine regions.
        for(int y=3;y<h;y+=7)for(int x=3;x<w;x+=9){int p=y*w+x;if(valid[p])owner[p]=(owner[p]+2)%k;}
        StencilRegionRefiner.Output out=StencilRegionRefiner.refine(owner,valid,w,h,k,.40f,15);
        boolean[] seen=new boolean[k];
        for(int p=0;p<out.owner.length;p++){
            if(!valid[p])ok(out.owner[p]==-1,"filled transparent snip");
            else {ok(out.owner[p]>=0&&out.owner[p]<k,"paper gap in valid area");seen[out.owner[p]]=true;}
        }
        for(boolean x:seen)ok(x,"deleted a valid major color band");
    }
    private static void testDetailPreservesTexture(){
        int w=71,h=59;int[] owner=new int[w*h];boolean[] allowed=new boolean[owner.length];
        Arrays.fill(allowed,true);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int p=y*w+x;owner[p]=(x+y)%5==0?1:0;
        }
        int lowChanges=StencilRegionRefiner.refine(owner,allowed,w,h,2,.10f,20).changedPixels;
        int highChanges=StencilRegionRefiner.refine(owner,allowed,w,h,2,.98f,1).changedPixels;
        ok(lowChanges>highChanges,"detail slider does not change geometry");
    }
    private static void testSvgContoursSimplified(){
        int w=240,h=12;int[] pixels=new int[w*h];Arrays.fill(pixels,0xff121212);
        MultilayerStencilEngine.Options o=new MultilayerStencilEngine.Options();
        o.sourceColorMode=true;o.registration=false;o.layerCount=2;o.widthMm=120;o.minimumIslandPixels=0;
        MultilayerStencilEngine.Result r=MultilayerStencilEngine.generate(pixels,w,h,o);
        String svg=r.svg(0);
        int segmentCommands=0;for(int i=0;i<svg.length();i++)if(svg.charAt(i)=='L')segmentCommands++;
        ok(segmentCommands<=5,"collinear contour vertices not removed: "+segmentCommands);
        boolean[] union=new boolean[pixels.length];int visible=0;
        for(MultilayerStencilEngine.Layer layer:r.layers){visible+=layer.visiblePixelCount;for(int i=0;i<layer.mask.length;i++)if(layer.mask[i])union[i]=true;}
        int occupied=0;for(boolean pixel:union)if(pixel)occupied++;
        ok(occupied==pixels.length,"white holes punched into opaque source");
        ok(visible==pixels.length,"visible ownership lost during stacking");
        ok(r.layers.get(0).baseLayer&&r.layers.get(0).pixelCount==pixels.length,"base layer should be the full support silhouette");
    }
    private static void testStackedBuildMasks(){
        int w=18,h=12;int[] pixels=new int[w*h];
        int a=0xffb98f81,b=0xff6e4a3f,c=0xffe6cfbf;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int i=y*w+x;
            if(x<9&&y<10)pixels[i]=a;
            else if(x>=9&&y<8)pixels[i]=b;
            else pixels[i]=c;
        }
        MultilayerStencilEngine.Options o=new MultilayerStencilEngine.Options();
        o.sourceColorMode=true;o.registration=false;o.layerCount=3;o.minimumIslandPixels=0;o.widthMm=90;
        MultilayerStencilEngine.Result r=MultilayerStencilEngine.generate(pixels,w,h,o);
        ok(r.stackedVinyl,"vinyl stencil should default to stacked build masks");
        ok(r.layers.size()==3,"unexpected layer count");
        int visibleSum=0;
        for(int i=0;i<r.layers.size();i++){
            MultilayerStencilEngine.Layer layer=r.layers.get(i);
            visibleSum+=layer.visiblePixelCount;
            if(i==0)ok(layer.baseLayer,"first exported layer must be the base support color");
            if(i>0)ok(layer.pixelCount<=r.layers.get(0).pixelCount,"overlay cannot exceed entire support silhouette");
        }
        ok(visibleSum==pixels.length,"visible pixels must partition the source image");
        ok(r.layers.get(0).pixelCount==pixels.length,"base mask must cover the whole visible silhouette");
        for(int i=1;i<r.layers.size();i++){
            boolean[] lower=r.layers.get(i-1).mask,upper=r.layers.get(i).mask;
            for(int p=0;p<upper.length;p++){
                if(upper[p]&&!r.layers.get(0).mask[p])throw new AssertionError("overlay escaped base support");
                if(upper[p]&&!lower[p])throw new AssertionError("higher layer lost required lower support");
            }
            ok(r.layers.get(i).pixelCount<=r.layers.get(i-1).pixelCount,"stacked masks should shrink as layers move upward");
        }
    }

    public static void main(String[] args){
        testSinglePixelPinholes();testPhotoRegionsAndSnips();testDetailPreservesTexture();testSvgContoursSimplified();testStackedBuildMasks();
        System.out.println("PASS v2.41: speckle removal, stable color boundaries, transparent snips, detail range, compact SVG contours, cumulative vinyl underlays");
    }
}

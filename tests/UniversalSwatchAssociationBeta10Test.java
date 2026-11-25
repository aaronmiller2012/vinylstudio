package com.halftonestudio;

import java.util.Arrays;

/** Beta 10: real physical swatches must be preserved across non-tessellation engines. */
public final class UniversalSwatchAssociationBeta10Test {
    private static void check(boolean good,String message){if(!good)throw new AssertionError(message);}
    private static int gray(int x){return 0xff000000|(x<<16)|(x<<8)|x;}
    private static int[] gradient(int w,int h){
        int[] pixels=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int g=18+(int)(215d*x/(w-1));pixels[y*w+x]=gray(g);
        }
        return pixels;
    }
    private static void matcher(){
        final int[] c={gray(16),gray(50),gray(84),gray(117),gray(150),gray(185),gray(218),gray(248)};
        int[] photo=gradient(90,70);boolean[] mask=new boolean[photo.length];
        for(int i=0;i<mask.length;i++)mask[i]=(i%90)>=10;
        for(int n=2;n<=8;n++){
            PhotoSwatchAssociation a=new PhotoSwatchAssociation(c,n,null,photo,mask,PhotoSwatchAssociation.SHAPES);
            for(int j=0;j<n;j++)check(a.choose(c[j])==j,"Exact user swatch reassigned, n="+n+" i="+j);
            int previous=-1;
            for(int x=10;x<251;x+=5){int chosen=a.choose(gray(x));
                check(chosen>=previous,"Gray tone ownership reversed at "+x+" n="+n);previous=chosen;
                check(chosen>=0&&chosen<n,"Out-of-range layer ID");
            }
            PhotoSwatchAssociation repeat=new PhotoSwatchAssociation(c,n,null,photo,mask,PhotoSwatchAssociation.SHAPES);
            for(int x=0;x<255;x+=11)check(a.choose(gray(x))==repeat.choose(gray(x)),"Nondeterministic match");
        }
        int[] accented={0xff1a1519,0xff1958cf,0xffc08566,0xff3aa26b,0xffb8a192,0xffd9b9aa};
        boolean[] enabled={true,true,true,false,true,true};
        PhotoSwatchAssociation a=new PhotoSwatchAssociation(accented,accented.length,enabled,photo,mask,PhotoSwatchAssociation.REGIONS);
        check(a.choose(accented[1])==1,"Blue eye accent failed");
        check(a.choose(accented[2])==2,"Skin swatch failed");
        for(int x=0;x<255;x+=13)check(a.choose(gray(x))!=3,"Disabled layer was selected");
        int[] unaltered=accented.clone();a.chooseCached(0xffbca198);
        check(Arrays.equals(accented,unaltered),"Matcher mutated physical swatches");
        int[] flat=new int[photo.length];Arrays.fill(flat,0xff9e9180);
        PhotoSwatchAssociation constant=new PhotoSwatchAssociation(accented,accented.length,enabled,flat,mask,PhotoSwatchAssociation.REGIONS);
        for(int j=0;j<10;j++)check(constant.choose(0xff9e9180)==constant.choose(0xff9e9180),"Flat photo drifting");
    }
    private static void stencil(){
        int w=40,h=25;int[] pixels=gradient(w,h);boolean[] mask=new boolean[pixels.length];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)mask[y*w+x]=x>=4 && y<h-2;
        int[] rolls={gray(25),gray(132),gray(235)};
        SourceColorStencilQuantizer.Result out=SourceColorStencilQuantizer.quantizeToPalette(pixels,w,h,rolls,3,mask);
        check(Arrays.equals(rolls,out.colors),"Raster source matching recolored the vinyl swatches");
        boolean dark=false,light=false;
        for(int i=0;i<pixels.length;i++){
            if(!mask[i])check(out.owner[i]==-1,"Raster stencil filled the Snip mask");
            else {check(out.owner[i]>=0&&out.owner[i]<3,"Raster stencil invalid owner");
                dark|=out.owner[i]==0;light|=out.owner[i]==2;}
        }
        check(dark&&light,"Photographic tonal range collapsed");
        int[] out2=SourceColorStencilQuantizer.quantizeToPalette(pixels,w,h,rolls,3,mask).owner;
        check(Arrays.equals(out.owner,out2),"Raster stencil unstable ownership");
    }
    private static void topography(){
        int w=96,h=72;int[] image=gradient(w,h);
        TopographicLayersEngine.Options o=new TopographicLayersEngine.Options();
        o.widthMm=152.4f;o.levels=4;o.minAreaMm2=0.2f;o.smoothMm=0.5f;
        o.palette=new int[]{0xff271b26,0xff6b5164,0xffbb8f78,0xfff2c2a5};
        int[] exact=o.palette.clone();
        StyleKit.Result r=TopographicLayersEngine.generate(image,w,h,o);
        check(r.layers.size()==4,"Topographic count changed");
        for(int i=0;i<4;i++)check(r.layers.get(i).color==exact[i],"Topographic generated wrong swatch "+i);
        String svg=StyleKit.toSvg(r,false);
        check(svg.contains("<svg"),"Topographic SVG missing");
        o.mode=TopographicLayersEngine.Mode.LINES;o.ribbon=false;
        r=TopographicLayersEngine.generate(image,w,h,o);
        check(r.layers.size()==4,"Topographic isoline count changed");
        for(int i=0;i<4;i++)check(r.layers.get(i).color==exact[i],"Isoline swatch incorrect "+i);
        check(Arrays.equals(exact,o.palette),"Topographic mutated selected swatches");
    }
    private static void facets(){
        int w=90,h=80;int[] image=gradient(w,h);LowPolyDelaunayEngine.Options o=new LowPolyDelaunayEngine.Options();
        o.widthMm=150;o.palette=new int[]{gray(23),gray(84),gray(143),gray(234)};
        o.layerCount=4;o.maxPoints=170;o.minSpacingMm=4;o.maxSpacingMm=11;o.gapMm=0.25f;
        StyleKit.Result r=LowPolyDelaunayEngine.generate(image,w,h,o);
        check(!r.layers.isEmpty(),"Low-poly geometry missing");
        for(StyleKit.Layer layer:r.layers){
            boolean known=false;for(int color:o.palette)if(layer.color==color)known=true;
            check(known,"Low-poly generated unselected swatch");
        }
        check(StyleKit.toSvg(r,false).equals(StyleKit.toSvg(LowPolyDelaunayEngine.generate(image,w,h,o),false)),
            "Low-poly SVG no longer deterministic");
    }
    public static void main(String[] args){
        matcher();System.out.println("PASS shared Oklab 2-8 layer, exact IDs, disabled slots, dark/light, masks");
        stencil();System.out.println("PASS masked multilayer stencil, correct swatch IDs, stable ownership");
        topography();System.out.println("PASS topographic filled contours + isolines use actual vinyl swatches");
        facets();System.out.println("PASS low-poly selected physical rolls, deterministic SVG");
        System.out.println("PASS Beta10 universal swatch regression");
    }
}

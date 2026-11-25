package com.halftonestudio;

import java.util.Arrays;
import java.util.Random;

/** Acceptance tests for physical-smooth mask outlines, holes and preview data. */
public final class SmoothStencilContoursTest {
    private static void ok(boolean b,String message){if(!b)throw new AssertionError(message);}
    private static int occurrences(String s,char c){int n=0;for(int i=0;i<s.length();i++)if(s.charAt(i)==c)n++;return n;}
    private static String path(String svg){int i=svg.indexOf(" d=\"");return svg.substring(i+4,svg.indexOf('"',i+4));}
    private static void diskAndDonut(){
        int w=190,h=190;boolean[] disk=new boolean[w*h],donut=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double d=Math.hypot(x-93.5,y-95.5);
            disk[y*w+x]=d<72;donut[y*w+x]=d<72&&d>22;
        }
        StringBuilder b=new StringBuilder();
        MultilayerStencilEngine.Options op=new MultilayerStencilEngine.Options();
        MultilayerStencilEngine.appendUnionContours(b,disk,w,h,38,38,0);
        ok(occurrences(path(b.toString()),'M')==1,"disc must remain a single closed loop");
        String raw=path(b.toString());
        SmoothStencilContours.Geometry curved=MultilayerStencilEngine.geometryForTest(disk,w,h,38,38,.28f);
        ok(curved.curved,"large disc did not get a smooth path");
        ok(curved.data.contains("C"),"disc must have cubic commands");
        ok(curved.data.length()<raw.length(),"smooth geometry did not reduce SVG bytes");
        ok(curved.data.endsWith("Z"),"the ribbon must close");
        b.setLength(0);MultilayerStencilEngine.appendUnionContours(b,donut,w,h,38,38,0);
        ok(occurrences(path(b.toString()),'M')==2,"hole must stay a distinct cut loop");
        curved=MultilayerStencilEngine.geometryForTest(donut,w,h,38,38,.28f);
        ok(MultilayerStencilEngine.countUnionContours(curved.raster,w,h)==2,"smoothed donut lost its hole");
    }
    private static void thinAndDiagonal(){
        int w=64,h=64;boolean[] shape=new boolean[w*h];
        for(int y=8;y<55;y++)shape[y*w+12]=true;
        for(int y=20;y<30;y++)for(int x=25;x<40;x++)shape[y*w+x]=true;
        shape[30*w+40]=true; // diagonal touch must not fuse
        SmoothStencilContours.Geometry g=MultilayerStencilEngine.geometryForTest(shape,w,h,32,32,.4f);
        ok(MultilayerStencilEngine.countUnionContours(g.raster,w,h)==3,"thin/diagonal path lost or merged");
        ok(g.raster[40*w+12],"one-pixel ribbon lost");
        ok(!g.raster[30*w+39],"negative space between diagonals became filled");
    }
    private static void nestedAndTranslation(){
        int w=140,h=130;int[] img=new int[w*h];Arrays.fill(img,0x00000000);
        for(int y=7;y<h-7;y++)for(int x=7;x<w-7;x++){
            double r=Math.hypot((x-70)/1.2,(y-63));
            if(r<48){int val=((x+5*y)/17)%3;
                img[y*w+x]=val==0?0xff171b2f:val==1?0xffedb5a0:0xff865447;}
        }
        MultilayerStencilEngine.Options o=new MultilayerStencilEngine.Options();
        o.registration=false;o.autoWhiteBackground=false;o.sourceColorMode=true;
        o.layerCount=3;o.widthMm=70f;o.heightMm=65f;o.minimumIslandPixels=1;o.minimumIslandAreaMm2=0;o.detail=1;
        MultilayerStencilEngine.Result r=MultilayerStencilEngine.generate(img,w,h,o);
        for(int i=0;i<r.layers.size();i++){
            String d=r.cutPathData(i),svg=r.svg(i);
            ok(!d.contains("NaN"),"invalid curve value");
            ok(!svg.contains("clipPath")&&!svg.contains("transform="),"export uses unsupported geometry shortcuts");
            ok(occurrences(d,'M')==MultilayerStencilEngine.countUnionContours(r.layers.get(i).mask,w,h),"cut loop mismatch");
            ok(svg.contains("fill-rule=\"evenodd\""),"no even-odd holes");
        }
        for(int i=1;i<r.layers.size();i++){
            boolean[] parent=r.cutRasterForTest(i-1),child=r.cutRasterForTest(i);
            for(int k=0;k<child.length;k++)if(child[k])ok(parent[k],"smoothed underlay no longer contains upper layer");
        }
    }
    private static void randomizedAndCancellation(){
        Random rng=new Random(2543);
        for(int trial=0;trial<25;trial++){
            int w=55,h=43;boolean[] m=new boolean[w*h];
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)
                m[y*w+x]=rng.nextDouble()<.15||(x>8&&x<42&&y>9&&y<34&&rng.nextDouble()<.83);
            SmoothStencilContours.Geometry g=MultilayerStencilEngine.geometryForTest(m,w,h,27.5f,21.5f,.28f);
            ok(occurrences(g.data,'M')==MultilayerStencilEngine.countUnionContours(m,w,h),"lost raster loop");
            ok(MultilayerStencilEngine.countUnionContours(g.raster,w,h)==MultilayerStencilEngine.countUnionContours(m,w,h),"changed cut topology");
        }
        boolean[] m=new boolean[100*100];Arrays.fill(m,true);
        Thread.currentThread().interrupt();boolean cancelled=false;
        try{MultilayerStencilEngine.geometryForTest(m,100,100,40,40,.2f);}
        catch(java.util.concurrent.CancellationException e){cancelled=true;}
        finally{Thread.interrupted();}
        ok(cancelled,"contour work must be cancellable");
    }
    public static void main(String[] a){
        diskAndDonut();thinAndDiagonal();nestedAndTranslation();randomizedAndCancellation();
        System.out.println("PASS smooth contour geometry: cubic curves, compact SVG, holes, thin paths, stacking, physical bounds, cancellation");
    }
}

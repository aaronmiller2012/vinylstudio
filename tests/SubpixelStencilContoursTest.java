package com.halftonestudio;

import java.util.Random;

/** Subpixel fidelity tests for deliberately jagged portrait-like color boundaries. */
public final class SubpixelStencilContoursTest {
    private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    private static int count(String a,char ch){int n=0;for(int i=0;i<a.length();i++)if(a.charAt(i)==ch)n++;return n;}
    private static boolean[] portraitMask(int w,int h){
        boolean[] a=new boolean[w*h];
        for(int y=5;y<h-5;y++)for(int x=3;x<w-3;x++){
            double wave=28+9*Math.sin(y*.046)+3*Math.sin(y*.115);
            // Round-to-pixel jagged edge such as quantized cheek shadow.
            if(x<Math.round(wave) && x>4)a[y*w+x]=true;
        }
        return a;
    }
    private static void compare(){
        int w=180,h=165;boolean[] m=portraitMask(w,h);
        SmoothStencilContours.Geometry old=MultilayerStencilEngine.geometryForTest(m,w,h,90,82.5f,0);
        SmoothStencilContours.Geometry now=MultilayerStencilEngine.geometryForTest(m,w,h,90,82.5f,.28f);
        System.out.printf("portrait contours: grid=%d bytes, new=%d bytes, Cubics=%d, lines=%d, rasterdiff=%d%n",
            old.data.length(),now.data.length(),count(now.data,'C'),count(now.data,'L'),errors(m,now.raster));
        check(now.raster.length==m.length,"raster dimensions");
        check(MultilayerStencilEngine.countUnionContours(now.raster,w,h)==MultilayerStencilEngine.countUnionContours(m,w,h),"mask component topology");
        check(!now.data.contains("NaN") && now.data.endsWith("Z"),"invalid SVG");
        check(count(now.data,'C')>0,"subpixel contour must actually emit smooth cubic geometry");
    }
    private static int errors(boolean[] a,boolean[] b){int n=0;for(int i=0;i<a.length;i++)if(a[i]!=b[i])n++;return n;}
    private static void mixedDetail(){
        int w=180,h=165;boolean[] m=portraitMask(w,h);
        // Real portraits have tiny holes and isolated micro-regions that would
        // defeat an all-or-nothing Gaussian field fallback.
        m[9*w+110]=true;m[12*w+120]=true;
        for(int y=40;y<44;y++)for(int x=16;x<20;x++)m[y*w+x]=false;
        SmoothStencilContours.Geometry g=MultilayerStencilEngine.geometryForTest(m,w,h,90,82.5f,.28f);
        System.out.printf("mixed portrait: cubics=%d, lines=%d, loops=%d, pixelChanges=%d%n",
            count(g.data,'C'),count(g.data,'L'),g.loops,errors(m,g.raster));
        check(g.loops==MultilayerStencilEngine.countUnionContours(m,w,h),"mixed loop loss");
        check(count(g.data,'C')>5,"small islands disabled all photographic contour curves");
        check(!g.raster[41*w+18],"eye hole covered");
    }
    private static void protect(){
        int w=74,h=63;boolean[] a=new boolean[w*h];
        for(int y=4;y<h-4;y++)for(int x=4;x<w-4;x++)if((x>9&&x<65)&&(y>8&&y<55))a[y*w+x]=true;
        for(int y=28;y<31;y++)for(int x=28;x<33;x++)a[y*w+x]=false;
        a[2*w+2]=true;a[3*w+3]=true;
        SmoothStencilContours.Geometry g=MultilayerStencilEngine.geometryForTest(a,w,h,37,31.5f,.28f);
        check(MultilayerStencilEngine.countUnionContours(g.raster,w,h)==MultilayerStencilEngine.countUnionContours(a,w,h),"hole and diagonal island topology");
        check(g.raster[2*w+2] && g.raster[3*w+3],"fine component missing");
        check(!g.raster[29*w+30],"tiny eye opening filled");
    }
    private static void cancellation(){
        boolean[] m=portraitMask(400,400);boolean interrupted=false;
        Thread.currentThread().interrupt();
        try{SubpixelStencilContours.trace(m,400,400,.8);}catch(java.util.concurrent.CancellationException e){interrupted=true;}
        finally{Thread.interrupted();}
        check(interrupted,"cancel ignored");
    }
    public static void main(String[] args){compare();mixedDetail();protect();cancellation();System.out.println("PASS subpixel contour regression");}
}

package com.halftonestudio;

import java.util.Arrays;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Verifies that multiple vinyl colors reuse a literal, identical curve. */
public final class SharedBoundaryV2546Test {
    private static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
    private static MultilayerStencilEngine.Options options(int n,int w,int h){
        MultilayerStencilEngine.Options o=new MultilayerStencilEngine.Options();
        o.layerCount=n;o.widthMm=w*.36f;o.heightMm=h*.36f;
        o.sourceColorMode=true;o.autoWhiteBackground=false;o.registration=false;
        o.minimumIslandPixels=0;o.minimumIslandAreaMm2=0;
        o.detail=.15f;return o;
    }
    private static String point(double x,double y){return String.format(java.util.Locale.US,"%.3f,%.3f",x,y);}
    private static Set<String> sharedSegments(String data){
        Set<String> s=new HashSet<>();Matcher m=Pattern.compile("[MCZ][^MCZ]*").matcher(data);
        double x=0,y=0;
        while(m.find()){
            String cmd=m.group();char type=cmd.charAt(0);
            if(type=='Z')continue;
            String[] p=cmd.substring(1).trim().split("\\s+");
            if(type=='M'){x=Double.parseDouble(p[0]);y=Double.parseDouble(p[1]);}
            else if(type=='C'){
                ok(p.length==6,"Unexpected curve tokens");
                double a=Double.parseDouble(p[0]),b=Double.parseDouble(p[1]),c=Double.parseDouble(p[2]),d=Double.parseDouble(p[3]);
                double nx=Double.parseDouble(p[4]),ny=Double.parseDouble(p[5]);
                String f=point(x,y)+"|"+point(a,b)+"|"+point(c,d)+"|"+point(nx,ny);
                String r=point(nx,ny)+"|"+point(c,d)+"|"+point(a,b)+"|"+point(x,y);
                s.add(f.compareTo(r)<0?f:r);x=nx;y=ny;
            }
        }
        return s;
    }
    private static void adjacentRegionsShareExactCubics(){
        int w=170,h=120;int[] photo=new int[w*h];
        int[] colors={0xff6a4438,0xffc2927c,0xffedc0a5};
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double wave=55+12*Math.sin(y*.085)+3*Math.sin(y*.43);
            photo[y*w+x]=x<wave?colors[0]:x<wave+65?colors[1]:colors[2];
        }
        MultilayerStencilEngine.Options o=options(3,w,h);
        MultilayerStencilEngine.Result r=MultilayerStencilEngine.generate(photo,w,h,o);
        ok(r.contourMode().startsWith("shared-interfaces"),"Shared interface renderer was bypassed: "+r.contourMode());
        List<Set<String>> boundary=new ArrayList<>();for(int i=0;i<3;i++)boundary.add(null);
        for(int i=0;i<3;i++){
            boundary.set(r.layers.get(i).sourceIndex,sharedSegments(r.visiblePathData(i)));
            ok(r.cutPathData(i).contains("C"),"Missing cut cubics");
            ok(r.svg(i).contains("fill-rule=\"evenodd\""),"Missing hole rule");
            if(i>0){
                boolean[] above=r.cutRasterForTest(i),below=r.cutRasterForTest(i-1);
                for(int k=0;k<above.length;k++)ok(!above[k]||below[k],"Lost support");
            }
        }
        Set<String> common=new HashSet<>(boundary.get(0));common.retainAll(boundary.get(1));
        ok(common.size()>70,"Adjacent colors do not share geometric curves, count="+common.size());
    }
    private static void sevenLayersAndHoles(){
        int w=220,h=150,n=7;int[] rgb={0xff62382d,0xffa06e54,0xffd4a88b,0xffedba9e,0xff29365e,0xffefddce,0xffd69172};
        int[] photo=new int[w*h];Arrays.fill(photo,0x00000000);
        for(int y=5;y<h-5;y++)for(int x=10;x<w-10;x++){
            int owner=Math.min(n-1,(int)(7*(x+5*Math.sin(y*.12))/(w-10)));
            photo[y*w+x]=rgb[Math.max(0,owner)];
        }
        MultilayerStencilEngine.Options o=options(n,w,h);
        MultilayerStencilEngine.Result r=MultilayerStencilEngine.generate(photo,w,h,o);
        ok(r.contourMode().startsWith("shared-interfaces"),"Seven-layer shared-boundary fallback: "+r.contourMode());
        for(int i=0;i<n;i++){
            String data=r.cutPathData(i);
            ok(data.indexOf('C')>=0,"Missing smooth contour layer "+i);
            ok(!data.contains("NaN"),"Invalid coordinate");
        }
    }
    private static void holesAndNonStacked(){
        int w=128,h=128;int[] image=new int[w*h];Arrays.fill(image,0x00000000);
        for(int y=7;y<h-7;y++)for(int x=7;x<w-7;x++){
            double d=Math.hypot(x-64,y-64);
            if(d>=12&&d<52)image[y*w+x]=d<28?0xff3d4970:0xffdab18e;
        }
        MultilayerStencilEngine.Options o=options(2,w,h);o.stackedVinyl=false;
        MultilayerStencilEngine.Result r=MultilayerStencilEngine.generate(image,w,h,o);
        ok(r.contourMode().startsWith("shared-interfaces"),"Nonstacked should use shared geometry: "+r.contourMode());
        for(int i=0;i<2;i++){
            String d=r.cutPathData(i);
            ok(d.indexOf('C')>=0,"No curves for donut");
            int loops=0;for(int j=0;j<d.length();j++)if(d.charAt(j)=='M')loops++;
            ok(loops>=2,"Donut opening or alpha boundary missing: "+loops);
        }
    }
    private static void legacyAndCancellation(){
        int w=60,h=50;int[] rgb=new int[w*h];
        Arrays.fill(rgb,0xffddccbb);
        for(int y=10;y<40;y++)for(int x=12;x<42;x++)rgb[y*w+x]=0xff663b35;
        MultilayerStencilEngine.Options o=options(2,w,h);o.curveRadiusMm=0;
        MultilayerStencilEngine.Result exact=MultilayerStencilEngine.generate(rgb,w,h,o);
        ok(exact.contourMode().equals("legacy"),"Disabled curves should use legacy geometry");
        Thread.currentThread().interrupt();boolean cancelled=false;
        try{o.curveRadiusMm=.28f;MultilayerStencilEngine.generate(rgb,w,h,o).cutPathData(0);}catch(RuntimeException e){cancelled=true;}
        finally{Thread.interrupted();}
        ok(cancelled,"Ignored cancellation");
    }
    public static void main(String[] args){
        adjacentRegionsShareExactCubics();sevenLayersAndHoles();holesAndNonStacked();legacyAndCancellation();
        System.out.println("PASS v2.54.6 shared-boundary: exact common cubic geometry, nested seven-layer cuts, legacy and cancellation");
    }
}

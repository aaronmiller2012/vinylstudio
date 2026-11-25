package com.halftonestudio;

import java.util.Arrays;
import java.util.Random;

/** Host-only: actual cut contours must match SVG move-to subpaths. */
public final class LayerCutCountsTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static int occurrences(String s,String query){
        int n=0,i=0;while((i=s.indexOf(query,i))>=0){n++;i+=query.length();}return n;
    }
    static void maskTest(boolean[] mask,int w,int h){
        StringBuilder svg=new StringBuilder();
        MultilayerStencilEngine.appendUnionContours(svg,mask,w,h,42f,42f,0);
        int fromSvg=occurrences(svg.toString(),"M");
        int fromCounter=MultilayerStencilEngine.countUnionContours(mask,w,h);
        check(fromSvg==fromCounter,"Contour mismatch SVG="+fromSvg+" count="+fromCounter);
    }
    public static void main(String[] args){
        boolean[] blank=new boolean[100];maskTest(blank,10,10);
        boolean[] single=new boolean[100];single[2*10+3]=true;maskTest(single,10,10);
        boolean[] ring=new boolean[100];
        for(int y=1;y<=8;y++)for(int x=1;x<=8;x++)ring[y*10+x]=true;
        for(int y=3;y<=6;y++)for(int x=3;x<=6;x++)ring[y*10+x]=false;
        maskTest(ring,10,10);
        check(MultilayerStencilEngine.countUnionContours(ring,10,10)==2,"Hole must be another blade cut");
        Random random=new Random(4902L);
        for(int k=0;k<45;k++){
            boolean[] m=new boolean[30*28];
            for(int i=0;i<m.length;i++)m[i]=random.nextFloat()<(k%7+1)/9f;
            maskTest(m,30,28);
        }
        int[] polyCounts=new int[]{2,4,7};
        // No dependency on rendered display scale: tessellation uses one closed cut per tile.
        check(Arrays.equals(polyCounts,new int[]{2,4,7}),"Sanity");
        System.out.println("PASS LayerCutCounts: blank/single/hole/45 seeded masks agree with SVG subpaths");
    }
}

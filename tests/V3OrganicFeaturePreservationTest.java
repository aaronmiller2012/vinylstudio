package com.halftonestudio;

import java.util.Arrays;
import java.util.ArrayList;

/** Adversarial proof that feature-aware smoothing retains photo detail. */
public final class V3OrganicFeaturePreservationTest {
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
    private static int area(boolean[] m){int n=0;for(boolean b:m)if(b)n++;return n;}
    private static float[] rect(float ax,float ay,float bx,float by){
        return new float[]{ax,ay,bx,ay,bx,by,ax,by};
    }
    private static void islandPreservation(){
        final int w=80,h=80;
        int[] photo=new int[w*h];Arrays.fill(photo,0xff707070);
        StyleKit.Source image=new StyleKit.Source(photo,w,h,null,60);
        PatternPortraitTrioEngine.Surface s=new PatternPortraitTrioEngine.Surface(image,3,.8f);
        int fw=s.w,fh=s.h;
        float[] importance=new float[fw*fh];
        float scale=s.mm;
        for(int y=0;y<fh;y++)for(int x=0;x<fw;x++){
            double xx=(x+.5)*scale,yy=(y+.5)*scale;
            boolean face=Math.hypot(xx-17,yy-17)<.98;
            boolean speck=Math.hypot(xx-43,yy-38)<.98;
            if(face||speck)s.ink[1][y*fw+x]=true;
            if(face)importance[y*fw+x]=1;
        }
        int keptBefore=area(s.ink[1]);
        OrganicFlowCutSmoother.discardMicroIslands(s,100,.8f,importance);
        int keptAfter=area(s.ink[1]);
        check(keptAfter<keptBefore,"Unimportant micro-speck should be pruned");
        boolean featureSurvived=false,noiseSurvived=false;
        for(int y=0;y<fh;y++)for(int x=0;x<fw;x++){
            int i=y*fw+x;
            if(!s.ink[1][i])continue;
            double xx=(x+.5)*scale,yy=(y+.5)*scale;
            if(Math.hypot(xx-17,yy-17)<1.3)featureSurvived=true;
            if(Math.hypot(xx-43,yy-38)<1.3)noiseSurvived=true;
        }
        check(featureSurvived,"Photographic detail island was erased");
        check(!noiseSurvived,"Low-information speck was not simplified");
        System.out.println("PASS small feature island kept; equal-sized background speck pruned");
    }
    private static void featureCoverageBudget(){
        int w=64,h=64;boolean[] valid=new boolean[w*h],source=new boolean[w*h];
        float[] salient=new float[w*h];Arrays.fill(valid,true);
        for(int y=10;y<30;y++)for(int x=10;x<30;x++){
            source[y*w+x]=true;
            if(x>=24)salient[y*w+x]=1f;
        }
        ArrayList<float[]> cut=new ArrayList<>();
        cut.add(rect(10,10,24,30));
        check(!OrganicFlowCutSmoother.preservesPhotoFeatures(source,cut,salient,valid,w,h,1),
              "Geometry swallowed high-contrast portrait detail but passed coverage guard");
        cut.clear();cut.add(rect(10,10,29.8f,30));
        check(OrganicFlowCutSmoother.preservesPhotoFeatures(source,cut,salient,valid,w,h,1),
              "Subpixel-sized boundary adjustment needlessly rejected");
        System.out.println("PASS photo-edge raster fidelity blocks destructive curves");
    }
    public static void main(String[] args){
        islandPreservation();featureCoverageBudget();
        System.out.println("PASS Organic Flow feature-preservation gates");
    }
}

package com.halftonestudio;

import java.util.Arrays;

/** Regression: Smoothing=50 must treat each contour vertex as an (x,y) pair. */
public final class V3OrganicUniformStrideTest {
    private V3OrganicUniformStrideTest() {}
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static float[] largeLightRegion(){
        int n=75;
        float[] p=new float[4*n];
        for(int i=0;i<n;i++){
            float x=9+i*.80f;
            float y=(float)(18+2*Math.sin(x/7.0)+(i%2==0?0:.38));
            p[2*i]=x;p[2*i+1]=y;
        }
        for(int i=0;i<n;i++){
            int j=n-1-i;
            float x=9+j*.80f;
            float y=(float)(43+2*Math.sin(x/7.0)+(j%2==0?0:.38));
            p[2*(n+i)]=x;p[2*(n+i)+1]=y;
        }
        return p;
    }
    static double edgeError(float[] p){
        double sum=0;int n=0;
        for(int i=0;i<p.length;i+=2){
            double x=p[i],y=p[i+1];
            if(x<12||x>66)continue;
            double top=18+2*Math.sin(x/7.0)+.19;
            double bottom=43+2*Math.sin(x/7.0)+.19;
            double err=Math.min(Math.abs(y-top),Math.abs(y-bottom));
            if(err<2.5){sum+=err;n++;}
        }
        return sum/Math.max(1,n);
    }
    static void assertCurve(float[] source,float[] result,String label){
        require(result!=null,label+" was rejected");
        require(result.length>=6 && (result.length&1)==0,label+" corrupt vertex pairs");
        require(!Arrays.equals(source,result),label+" did not smooth");
        double before=StyleKit.signedArea(source),after=StyleKit.signedArea(result);
        require(before*after>0,label+" reversed contour winding");
        require(Math.abs(after-before)/Math.abs(before)<.08,label+" changed fill area too far");
        require(StyleKit.selfIntersections(result,1)==0,label+" self-intersects");
        for(float value:result)require(Float.isFinite(value)&&value>=0&&value<=80,label+" coordinate escaped canvas");
    }
    public static void main(String[] args){
        float[] original=largeLightRegion();
        require(StyleKit.selfIntersections(original,1)==0,"Invalid test shape");
        float[] medium=OrganicFlowCutSmoother.uniformBoundaryCurve(original,.50f,.16f,.8f,80f,80f);
        float[] high=OrganicFlowCutSmoother.uniformBoundaryCurve(original,1f,.16f,.8f,80f,80f);
        assertCurve(original,medium,"50% smoothing");
        assertCurve(original,high,"100% smoothing");
        System.out.printf("original pts=%d error=%.4f, medium pts=%d error=%.4f, high pts=%d error=%.4f%n",original.length/2,edgeError(original),medium.length/2,edgeError(medium),high.length/2,edgeError(high));
        require(edgeError(medium)<edgeError(original),"50% did not reduce staircase noise");
        require(edgeError(high)<=edgeError(medium)*1.05,"100% was not at least as smooth as 50%");
        System.out.println("PASS 50% and 100% smoothing preserve XY pairs, area, winding, bounds, and remove stair steps");
    }
}

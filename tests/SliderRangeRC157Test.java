package com.halftonestudio;
/** Regression for slider values, inclusive endpoints, negative ranges and step snapping. */
public final class SliderRangeRC157Test {
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    private static void close(float a,float b){check(Math.abs(a-b)<.0002f,a+" != "+b);}
    private static void range(float min,float max,float step){
        int n=SliderRangePolicy.steps(min,max,step);
        check(n>=0,"Negative number of steps");
        close(SliderRangePolicy.value(0,min,max,step),min);
        close(SliderRangePolicy.value(n,min,max,step),max);
        check(SliderRangePolicy.progress(min-100,min,max,step)==0,"Low clamp");
        check(SliderRangePolicy.progress(max+100,min,max,step)==n,"High clamp");
        float previous=min;
        for(int i=0;i<=n;i++){
            float v=SliderRangePolicy.value(i,min,max,step);
            check(v>=min-.0001f&&v<=max+.0001f,"Step value exceeds bounds");
            check(v>=previous,"Slider values must be monotonic");
            previous=v;
        }
    }
    public static void main(String[] args){
        range(0,100,5);range(.20f,1.80f,.10f);range(.15f,1.20f,.05f);
        range(-1.5f,1.5f,.10f);range(.065f,.40f,.005f);
        range(0,179,1);range(.35f,2.8f,.05f);range(30,95,1);
        range(0,.95f,.10f);range(-180,180,15);range(.1f,.1f,.02f);
        close(SliderRangePolicy.value(SliderRangePolicy.progress(Float.NaN,0,100,5),0,100,5),0);
        try{SliderRangePolicy.steps(0,1,0);throw new AssertionError("Zero step accepted");}
        catch(IllegalArgumentException expected){}
        System.out.println("PASS RC157 slider range, endpoint and step policy");
    }
}

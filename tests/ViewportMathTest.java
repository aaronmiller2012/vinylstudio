package com.halftonestudio;

public final class ViewportMathTest {
    static void near(float actual,float expected,String note){
        if(Math.abs(actual-expected)>1e-3)throw new AssertionError(note+" "+actual+" != "+expected);
    }
    public static void main(String[] args){
        float center=200,focus=270,previousPan=18,oldZoom=2,newZoom=3;
        float before=ViewportMath.toContent(focus,center,previousPan,oldZoom);
        float newPan=ViewportMath.zoomPan(previousPan,center,focus,oldZoom,newZoom);
        float after=ViewportMath.toContent(focus,center,newPan,newZoom);
        near(before,after,"Pinch should preserve photo pixel under fingertips");
        float tracked=ViewportMath.toContent(144,center,25,2.5f);
        float moved=ViewportMath.toContent(164,center,45,2.5f);
        near(tracked,moved,"two-finger pan should track content position");
        near(ViewportMath.bounded(0.1f),1,"minimum zoom");
        near(ViewportMath.bounded(30f),12,"maximum zoom");
        near(ViewportMath.limitPan(10000,3,400),400,"pan bounded to drawable viewport");
        System.out.println("PASS: focal-point pinch; two-finger translation; zoom and pan bounds");
    }
}

package com.halftonestudio;
import java.util.*;
public final class ArtworkVisibilityTest {
    static VinylEngine.Shape box(float x,float y,float w,float h){return VinylEngine.Shape.polygon(x,y,x+w,y,x+w,y+h,x,y+h);}
    static void eq(double a,double b){if(Math.abs(a-b)>1e-7)throw new AssertionError(a+" != "+b);}
    public static void main(String[] args){
        List<List<VinylEngine.Shape>> sheets=Arrays.asList(Arrays.asList(box(0,0,10,10),box(5,0,10,10)),Collections.singletonList(box(5,0,5,10)));
        ArtworkVisibility.Layer[] m=ArtworkVisibility.measure(sheets,new boolean[]{true,true},()->{});
        eq(m[0].area,150);eq(m[0].visible,100);eq(m[0].hidden,50);eq(m[0].pieces,1);eq(m[0].smallestComponentArea,150);eq(m[0].smallestComponentSpan,10);eq(m[0].fullyHiddenComponents,0);eq(m[1].visible,50);
        m=ArtworkVisibility.measure(sheets,new boolean[]{true,false},()->{});eq(m[0].visible,150);if(m[1]!=null)throw new AssertionError();
        m=ArtworkVisibility.measure(Arrays.asList(Arrays.asList(box(0,0,2,2),box(5,0,3,3)),Collections.singletonList(box(0,0,2,2))),new boolean[]{true,true},()->{});
        eq(m[0].fullyHiddenComponents,1);eq(m[0].smallestComponentArea,4);eq(m[0].smallestComponentSpan,2);
        VinylEngine.Shape hole=VinylEngine.Shape.compound(new float[][]{{0,0,10,0,10,10,0,10},{2,2,4,2,4,4,2,4}});
        m=ArtworkVisibility.measure(Collections.singletonList(Collections.singletonList(hole)),new boolean[]{true},()->{});eq(m[0].area,96);eq(m[0].holes,1);
        try{ArtworkVisibility.measure(sheets,new boolean[]{true,true},()->{throw new IllegalStateException();});throw new AssertionError();}catch(IllegalStateException expected){}
        System.out.println("PASS artwork union, visibility, excluded layers, holes, cancellation");
    }
}

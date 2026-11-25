package com.halftonestudio;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/** Reproduction fixtures for verified build-517 slider, Snip, and same-sheet defects. */
public final class V3SliderOverprintRepairTest {
    private V3SliderOverprintRepairTest() {}
    private static void ok(boolean passed,String message){if(!passed)throw new AssertionError(message);}
    private static void near(float a,float b){ok(Math.abs(a-b)<.0003f,a+" != "+b);}
    private static float[] rect(float x0,float y0,float x1,float y1){
        return new float[]{x0,y0,x1,y0,x1,y1,x0,y1};
    }
    private static void sliderPrecisionAndPhysicalFloor(){
        ok("0.25".equals(SliderRangePolicy.format(.25f,.05f,false)),"Lost hundredths");
        ok("0.065".equals(SliderRangePolicy.format(.065f,.005f,false)),"Lost thousandths");
        ok("35%".equals(SliderRangePolicy.format(35f,5f,true)),"Percent formatting");
        ok("0.25".equals(SliderRangePolicy.format(.25f,.05f,false)),"Duplicate step labels");
        near(SliderRangePolicy.normalize(.273f,.15f,1.2f,.05f),.25f);
        near(SliderRangePolicy.ceiling(.56f,.15f,.05f),.60f);
        near(SliderRangePolicy.ceiling(3.10f,.15f,.05f),3.10f);
        near(StyleCutGeometryPreflight.exportGapMm(.5f,.254f)+.06f,.56f);
        for(int i=0;i<SliderRangePolicy.steps(.6f,1.2f,.05f);i++){
            float left=SliderRangePolicy.value(i,.6f,1.2f,.05f);
            float right=SliderRangePolicy.value(i+1,.6f,1.2f,.05f);
            ok(right>left,"Dead step in cut-safe slider");
            ok(!SliderRangePolicy.format(left,.05f,false).equals(
                SliderRangePolicy.format(right,.05f,false)),"Two distinct steps show one label");
        }
    }
    private static void explicitLayerLink(){
        VinylEngine.Settings model=VinylEngine.Settings.defaults();
        VinylEngine.Layer custom=model.layers.get(2);
        custom.spacing=1.11f;custom.density=.68f;custom.contrast=1.35f;
        model.linkedControls=false;
        model.masterSpacingMm=3.34f;model.masterDensity=1.75f;model.masterContrast=.6f;
        MasterLayerLinkPolicy.applyIfLinked(model);
        near(custom.spacing,1.11f);near(custom.density,.68f);near(custom.contrast,1.35f);
        // Copy is also used for undo/export; unlinked values must survive it.
        VinylEngine.Settings saved=model.copy();
        ok(!saved.linkedControls,"Copy silently relinked layers");
        near(saved.layers.get(2).spacing,1.11f);
        saved.linkedControls=true;
        MasterLayerLinkPolicy.applyIfLinked(saved);
        for(VinylEngine.Layer layer:saved.layers){
            near(layer.spacing,3.34f);near(layer.density,1.75f);near(layer.contrast,.6f);
        }
        near(model.layers.get(2).spacing,1.11f);
    }
    private static void sameSheetIsGeometryAware(){
        float[] left=rect(1,1,20,30),right=rect(30,1,50,30);
        ok(!OpaqueOverlaySwatchPlanner.polygonsContact(left,right),"Separated marks were deemed contacting");
        ok(OpaqueOverlaySwatchPlanner.polygonsContact(left,rect(15,1,40,30)),"Overlap undetected");
        ok(OpaqueOverlaySwatchPlanner.polygonsContact(left,rect(20,1,30,30)),"Touching cuts need a union");
        ok(OpaqueOverlaySwatchPlanner.polygonsContact(left,rect(5,5,10,10)),"Nested cuts need a union");
        int w=70,h=42,black=0xff101010,white=0xfff4f4f4,blue=0xff133d98;
        int[] photo=new int[w*h];Arrays.fill(photo,black);
        for(int y=1;y<30;y++)for(int x=1;x<50;x++)if(x<20||x>=30)photo[y*w+x]=white;
        float[][] disjoint={left,right,null};
        int[] owner=OpaqueOverlaySwatchPlanner.choose(photo,null,w,h,0,0,w,h,
            disjoint,new int[]{black,white,blue},3,1f);
        ok(Arrays.equals(owner,new int[]{1,1,0}),
            "Two separated white marks must share the same vinyl roll: "+Arrays.toString(owner));
        int[] crossing=OpaqueOverlaySwatchPlanner.choose(photo,null,w,h,0,0,w,h,
            new float[][]{left,rect(15,1,40,30),null},
            new int[]{black,white,blue},3,1f);
        ok(crossing[0]==0||crossing[1]==0||crossing[0]!=crossing[1],
            "Intersecting cuts were assigned twice to the same roll");
    }
    private static void sparseSnipPalette(){
        int[] chips={0xff171717,0xffed2816,0xff1944d3,0xff4c9840,
                     0xffe9e6dc,0xff8252a6,0xffb58216,0xff5c5956};
        int[] invisible=new int[10000],oneRed=invisible.clone();
        oneRed[9999]=0xffed2816;
        int[] empty=OpaqueOverlayPhotoPalette.arrange(chips,4,invisible);
        int[] sample=OpaqueOverlayPhotoPalette.arrange(chips,4,oneRed);
        ok(!Arrays.equals(empty,sample),"Single valid Snip pixel was not sampled");
        ok(Arrays.equals(sample,OpaqueOverlayPhotoPalette.arrange(chips,4,oneRed)),
            "Valid-pixel reservoir must be deterministic");
        int[] sourceSorted=chips.clone(),resultSorted=sample.clone();
        Arrays.sort(sourceSorted);Arrays.sort(resultSorted);
        ok(Arrays.equals(sourceSorted,resultSorted),"Photo sampling lost original chip identity");
    }
    private static void excludedPixelsDoNotRotateMarks() throws Exception {
        final int w=40,h=40;int[] a=new int[w*h],b=new int[w*h];
        boolean[] allowed=new boolean[w*h];
        Arrays.fill(a,0xff000000);Arrays.fill(b,0xffffffff);
        for(int y=10;y<30;y++)for(int x=10;x<30;x++){
            int i=y*w+x;allowed[i]=true;
            a[i]=b[i]=0xff000000|(x*6<<16)|(y*5<<8)|70;
        }
        Method gradient=LayeredCellPortraitEngine.class.getDeclaredMethod("gradient",
            int[].class,boolean[].class,int.class,int.class,float.class,float.class,float.class,float.class);
        gradient.setAccessible(true);
        Object first=gradient.invoke(null,a,allowed,w,h,10f,10f,30f,30f);
        Object second=gradient.invoke(null,b,allowed,w,h,10f,10f,30f,30f);
        for(String fieldName:new String[]{"gx","gy","contrast"}){
            Field f=first.getClass().getDeclaredField(fieldName);f.setAccessible(true);
            near(f.getFloat(first),f.getFloat(second));
        }
    }
    public static void main(String[] args) throws Exception {
        sliderPrecisionAndPhysicalFloor();
        System.out.println("PASS canonical slider steps, precise labels, physical tessellation floor");
        explicitLayerLink();
        System.out.println("PASS custom layer isolation, copied settings, explicit relinking");
        sameSheetIsGeometryAware();
        System.out.println("PASS disjoint same-color marks, overlapping/touching cut protection");
        sparseSnipPalette();
        System.out.println("PASS sparse opaque Snip pixels, photo palette determinism and swatch identity");
        excludedPixelsDoNotRotateMarks();
        System.out.println("PASS gradient invariance to changes outside the Snip");
    }
}

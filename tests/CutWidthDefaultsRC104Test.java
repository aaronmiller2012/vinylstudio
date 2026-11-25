package com.halftonestudio;

/** RC104: automatic style/size tuning must never silently raise minimum cut width. */
public final class CutWidthDefaultsRC104Test {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void close(float actual,float expected,String message){
        require(Math.abs(actual-expected)<.00001f,message+": "+actual+" != "+expected);
    }
    public static void main(String[] args){
        final float floor=VinylEngine.MINIMUM_CUT_WIDTH_MM;
        close(floor,VinylEngine.toMm(.010f),"exact .010 inch app floor");
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        // The historical headless engine fixture retains its former baseline.
        close(s.minimumMm,.8f,"legacy engine initial settings unchanged");
        s.minimumMm=floor;
        for(int style:new int[]{VinylEngine.LINE,VinylEngine.STRAIGHT,VinylEngine.CONTOUR,
                VinylEngine.WAVE,VinylEngine.ROUND,VinylEngine.HEX,VinylEngine.HATCH}){
            for(int profile=VinylSizing.DETAIL;profile<=VinylSizing.EASY_WEED;profile++){
                SmartHalftonePresets.apply(s,style,profile,11,14);
                close(s.minimumMm,floor,"style/size must preserve 0.010 inch minimum");
            }
        }
        // A deliberately edited saved project keeps a larger minimum across styles.
        final float custom=VinylEngine.toMm(.035f);
        s.minimumMm=custom;
        SmartHalftonePresets.apply(s,VinylEngine.LINE,VinylSizing.BALANCED,8,10);
        close(s.minimumMm,custom,"manual minimum retained after style change");
        VinylSizing.apply(s,11,14,VinylSizing.EASY_WEED);
        close(s.minimumMm,custom,"manual minimum retained after size selection");
        close(s.copy().minimumMm,custom,"saved copy retains minimum");
        System.out.println("PASS RC104: smallest cut width defaults, style and size retention, legacy geometry");
    }
}

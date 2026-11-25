package com.halftonestudio;

/** Normalizes every SeekBar range and displayed value to exactly match settings. */
public final class SliderRangePolicy {
    private SliderRangePolicy() { }
    public static int steps(float min,float max,float increment) {
        if(!Float.isFinite(min)||!Float.isFinite(max)||!Float.isFinite(increment)||
           min>max||increment<=0f)throw new IllegalArgumentException("Invalid slider range");
        // Round up so the endpoint can be reached without exceeding max.
        return Math.max(0,(int)Math.ceil((max-min)/increment-0.00001f));
    }
    public static int progress(float value,float min,float max,float increment) {
        int steps=steps(min,max,increment);
        float v=Float.isFinite(value)?Math.max(min,Math.min(max,value)):min;
        return Math.max(0,Math.min(steps,Math.round((v-min)/increment)));
    }
    public static float value(int progress,float min,float max,float increment) {
        return Math.max(min,Math.min(max,min+Math.max(0,progress)*increment));
    }
    /** The value represented by the actual thumb, including imported/off-step values. */
    public static float normalize(float raw,float min,float max,float step){
        return value(progress(raw,min,max,step),min,max,step);
    }
    /** First valid grid point at or above a non-negotiable physical constraint. */
    public static float ceiling(float floor,float min,float step){
        if(!Float.isFinite(floor)||!Float.isFinite(min)||!Float.isFinite(step)||step<=0f)
            throw new IllegalArgumentException("Invalid physical slider floor");
        return min+Math.max(0,(int)Math.ceil((floor-min)/step-0.00001f))*step;
    }
    /** Digits are determined by the physical step, not by a label's text. */
    public static int decimalPlaces(float step){
        if(!Float.isFinite(step)||step<=0f)throw new IllegalArgumentException("Invalid slider step");
        float scale=1f;
        for(int digits=0;digits<=5;digits++){
            float scaled=step*scale;
            if(Math.abs(scaled-Math.round(scaled))<0.0001f)return digits;
            scale*=10f;
        }
        return 6;
    }
    public static String format(float value,float step,boolean percent){
        int digits=decimalPlaces(step);
        return String.format(java.util.Locale.US,"%."+digits+"f"+(percent?"%%":""),value);
    }
}

package com.halftonestudio;

/** Pure-Java pointer arbitration for finger and Android stylus/S Pen editing. */
public final class SnipInputPolicy {
    private SnipInputPolicy() {}
    // Android MotionEvent TOOL_TYPE_FINGER, STYLUS and ERASER (API 14+).
    public static final int FINGER=1, STYLUS=2, ERASER=4;

    public static boolean isPen(int toolType){
        return toolType==STYLUS||toolType==ERASER;
    }
    /** While a pen is hovering, a finger down event is more likely to be a palm. */
    public static boolean ignoreForPalm(int toolType,boolean penHoverNearby){
        return toolType==FINGER&&penHoverNearby;
    }
    /** Releasing a separate palm pointer must not cancel the captured pen vertex. */
    public static boolean keepPenAfterSecondaryExit(boolean penCaptured,int capturedPointerId,int leavingPointerId){
        return penCaptured&&capturedPointerId>=0&&leavingPointerId!=capturedPointerId;
    }
}

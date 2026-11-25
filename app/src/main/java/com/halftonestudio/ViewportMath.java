package com.halftonestudio;

/** View-only image navigation. Coordinates in source artboard never change. */
public final class ViewportMath {
    private ViewportMath(){}
    public static final float MIN_ZOOM=1f,MAX_ZOOM=12f;
    public static float bounded(float zoom){return Math.max(MIN_ZOOM,Math.min(MAX_ZOOM,zoom));}
    /** screen point -> unzoomed point, where pan is measured around viewport center */
    public static float toContent(float screen,float center,float pan,float zoom){
        return (screen-center-pan)/zoom+center;
    }
    /** Pan needed for a target zoom to keep screen focal point fixed. */
    public static float zoomPan(float oldPan,float center,float focus,float oldZoom,float newZoom){
        float ratio=newZoom/oldZoom;float focusOffset=focus-center;
        return focusOffset-ratio*(focusOffset-oldPan);
    }
    public static float limitPan(float pan,float zoom,float viewportSpan){
        float max=(bounded(zoom)-1)*viewportSpan*.5f;
        return Math.max(-max,Math.min(max,pan));
    }
}

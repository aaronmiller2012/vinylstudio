package com.halftonestudio;

/**
 * Inner elliptical cut boundary used while tracing ribbons.
 *
 * The generic SVG safety filter intentionally rejects a WHOLE polygon when
 * any vertex is outside a circular crop. A ribbon generated across a full
 * rectangular artboard can therefore disappear despite most of its length
 * being in the crop. Break the run before that happens.
 *
 * Conservatively erode the physical crop ellipse by the maximum ribbon
 * half-width (plus a small floating-point tolerance). An eroded ellipse is
 * convex; any polygon joining only vertices within it remains cut-safe.
 * This preserves the original path geometry for non-circular projects.
 */
final class LineCropBoundary {
    private LineCropBoundary() {}

    static boolean accepts(boolean circleCrop,float x,float y,
                           float widthMm,float heightMm,float insetMm,float halfWidthMm) {
        if(!circleCrop)return true;
        final float clearance=Math.max(0f,halfWidthMm)+.035f;
        final double rx=widthMm*.5-insetMm-clearance;
        final double ry=heightMm*.5-insetMm-clearance;
        if(!(rx>0&&ry>0))return false;
        final double dx=(x-widthMm*.5)/rx,dy=(y-heightMm*.5)/ry;
        return dx*dx+dy*dy<=1.0;
    }
}

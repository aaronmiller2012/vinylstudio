package com.halftonestudio;

/** Physical artboard and registration layout shared by every assembly-guide route. */
final class AssemblyLayout {
    final float widthMm,heightMm,padMm,markSizeMm,minimumMm;
    final boolean registration;
    final float[][] marks;
    final int markStyle;
    AssemblyLayout(float width,float height,float pad,boolean enabled,float[][] points,
            int style,float size,float minimum){
        widthMm=width;heightMm=height;padMm=pad;registration=enabled;marks=points;
        markStyle=style;markSizeMm=size;minimumMm=minimum;
    }
}

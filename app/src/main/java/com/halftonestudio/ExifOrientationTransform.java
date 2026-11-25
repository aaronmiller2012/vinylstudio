package com.halftonestudio;

/** Linear photo orientation transform, EXIF 1..8 (including mirrored photos).
 * Bitmap.createBitmap translates the resulting bounds so transformed pixels remain visible.
 */
public final class ExifOrientationTransform {
    private ExifOrientationTransform() {}
    /** null = identity; row-major affine matrix for android.graphics.Matrix.setValues. */
    public static float[] matrix(int orientation) {
        switch(orientation) {
            case 2: return values(-1,0,0,1);   // horizontal mirror
            case 3: return values(-1,0,0,-1);  // 180 degrees
            case 4: return values(1,0,0,-1);   // vertical mirror
            case 5: return values(0,1,1,0);    // transpose, mirror + 90
            case 6: return values(0,-1,1,0);   // 90 degrees clockwise
            case 7: return values(0,-1,-1,0);  // transverse
            case 8: return values(0,1,-1,0);   // 270 degrees clockwise
            default:return null;
        }
    }
    private static float[] values(int a,int b,int c,int d){
        return new float[]{a,b,0,c,d,0,0,0,1};
    }
}

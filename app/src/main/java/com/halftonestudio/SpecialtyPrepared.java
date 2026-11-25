package com.halftonestudio;

import android.graphics.Bitmap;

/** Prepared specialty source with and without lighting (cache value). */
final class SpecialtyPrepared {
    final Bitmap bitmap;
    final Bitmap unlit;
    final int[] pixels;
    final int[] unlitPixels;
    SpecialtyPrepared(Bitmap bitmap,Bitmap unlit,int[] pixels,int[] unlitPixels){
        this.bitmap=bitmap;this.unlit=unlit;this.pixels=pixels;this.unlitPixels=unlitPixels;
    }
}

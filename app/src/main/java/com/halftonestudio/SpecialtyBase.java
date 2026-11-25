package com.halftonestudio;

import android.graphics.Bitmap;

/** Decoded specialty-render base photo and its pixels (cache value). */
final class SpecialtyBase {
    final Bitmap bitmap;
    final int[] pixels;
    SpecialtyBase(Bitmap bitmap,int[] pixels){this.bitmap=bitmap;this.pixels=pixels;}
}

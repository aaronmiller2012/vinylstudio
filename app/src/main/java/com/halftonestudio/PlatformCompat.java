package com.halftonestudio;

import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;

/**
 * The only place that touches platform APIs deprecated on newer Android releases.
 * Each legacy call runs solely on the API levels where no replacement exists
 * (minSdk 26), so the deprecation suppression is scoped and documented here instead
 * of being scattered through UI code.
 */
final class PlatformCompat {
    private PlatformCompat() { }

    /** Android 15+ enforces transparent edge-to-edge bars; color them only below 35. */
    @SuppressWarnings("deprecation")
    static void colorSystemBars(Window window,int color){
        if(window==null||Build.VERSION.SDK_INT>=35)return;
        window.setStatusBarColor(color);
        window.setNavigationBarColor(color);
    }

    /** System bar + cutout + IME insets as {left, top, right, bottom}. */
    @SuppressWarnings("deprecation")
    static int[] safeInsets(WindowInsets insets){
        if(Build.VERSION.SDK_INT>=30){
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|
                WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
            return new int[]{bars.left,bars.top,bars.right,bars.bottom};
        }
        return new int[]{insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
            insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()};
    }

    /** API 26–29 immersive navigation; API 30+ callers use WindowInsetsController. */
    @SuppressWarnings("deprecation")
    static void legacyHideNavigation(View decor,boolean hide){
        if(Build.VERSION.SDK_INT>=30)return;
        decor.setSystemUiVisibility(hide?View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION:0);
    }

    /** Pixel size expressed in sp, honoring Android 14 non-linear font scaling. */
    @SuppressWarnings("deprecation")
    static float pxToSp(float px,DisplayMetrics metrics){
        if(Build.VERSION.SDK_INT>=34)return TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_SP,px,metrics);
        return px/metrics.scaledDensity;
    }

    /** Android 14+ delivers only UI_HIDDEN and BACKGROUND; older releases also send RUNNING_* levels. */
    @SuppressWarnings("deprecation")
    static boolean shouldReleaseCaches(int level){
        return level>=android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW;
    }

    @SuppressWarnings("deprecation")
    static android.graphics.BitmapRegionDecoder regionDecoder(String path) throws java.io.IOException {
        return Build.VERSION.SDK_INT>=31?android.graphics.BitmapRegionDecoder.newInstance(path):
            android.graphics.BitmapRegionDecoder.newInstance(path,false);
    }
}

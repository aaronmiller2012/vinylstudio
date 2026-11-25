package com.halftonestudio;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Shared view factory for every screen built in code (build 554). StudioActivity's
 * helpers delegate here, and extracted screens (ProjectGalleryDialog, StudioProductFlows)
 * use it directly, so moving a screen out of the activity no longer drags the activity along.
 */
final class StudioUi {
    final Context context;
    StudioUi(Context context){this.context=context;}

    int dp(float val){return (int)(val*context.getResources().getDisplayMetrics().density+.5f);}
    GradientDrawable shape(int c,float rad){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(rad));return d;}
    GradientDrawable outlined(int color,int border,float rad){GradientDrawable d=shape(color,rad);d.setStroke(dp(1),border);return d;}
    /** Touch ripple clipped to the same rounded rectangle, so every pill/card gives press feedback. */
    Drawable ripple(Drawable content,float rad){
        return new RippleDrawable(android.content.res.ColorStateList.valueOf(StudioTheme.RIPPLE),content,shape(Color.WHITE,rad));
    }
    Drawable pillBackground(boolean selected,float rad){
        return ripple(selected?outlined(StudioTheme.ACCENT_SOFT,StudioTheme.ACCENT,rad):outlined(StudioTheme.SURFACE_HIGH,StudioTheme.OUTLINE,rad),rad);
    }
    void styleHeaderButton(TextView t){t.setBackground(ripple(shape(Color.TRANSPARENT,24),24));}
    TextView text(String value,int size,int color,boolean strong){
        TextView t=new TextView(context);t.setText(value);t.setTextSize(Math.max(12,size));t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);t.setFontFeatureSettings("kern");
        if(strong)t.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        return t;
    }
}

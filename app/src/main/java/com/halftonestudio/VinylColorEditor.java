package com.halftonestudio;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;

/** Shared native vinyl editor: hue/saturation, brightness, exact black/white.
 * Selection remains provisional until the enclosing dialog's Apply callback.
 */
// Created only from code with its required collaborators; never inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
public final class VinylColorEditor extends LinearLayout {
    public interface Listener { void onColor(int color); }
    private final VinylColorWheel wheel;
    private final SeekBar value;
    private final TextView valueLabel, hsLabel, preview;
    private Listener listener;
    private boolean syncing;

    public VinylColorEditor(Context ctx,int initialColor){
        super(ctx);
        setOrientation(VERTICAL);
        setPadding(dp(14),dp(5),dp(14),dp(6));
        wheel=new VinylColorWheel(ctx);
        wheel.setColor(initialColor);
        addView(wheel,new LayoutParams(-1,dp(198)));
        TextView heading=label("Hue / Saturation · drag within wheel",13);
        heading.setGravity(Gravity.CENTER_HORIZONTAL);
        addView(heading);
        hsLabel=label("",12);hsLabel.setGravity(Gravity.CENTER_HORIZONTAL);addView(hsLabel);
        valueLabel=label("Value / Brightness",14);addView(valueLabel);
        value=new SeekBar(ctx);value.setMax(100);value.setMinimumHeight(dp(48));
        value.setContentDescription(value.getContext().getString(R.string.quality_value_brightness_0_to_100_percent));
        addView(value,new LayoutParams(-1,dp(48)));
        preview=label("",15);preview.setGravity(Gravity.CENTER);
        addView(preview,new LayoutParams(-1,dp(38)));

        LinearLayout shortcuts=new LinearLayout(ctx);
        shortcuts.setOrientation(HORIZONTAL);
        LayoutParams shortcutsParams=new LayoutParams(-1,dp(53));
        shortcutsParams.topMargin=dp(12);
        addView(shortcuts,shortcutsParams);
        addSolidColorButton(shortcuts,"Solid Black",Color.BLACK);
        addSolidColorButton(shortcuts,"Solid White",Color.WHITE);

        wheel.setListener(this::acceptFromWheel);
        value.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){}
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
                if(fromUser&&!syncing)wheel.setBrightness(progress/100f);
            }
        });
        refresh(initialColor);
    }

    private void addSolidColorButton(LinearLayout row,String title,int color){
        TextView button=label(title,14);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(color==Color.BLACK?Color.WHITE:Color.BLACK);
        button.setTypeface(null,android.graphics.Typeface.BOLD);
        button.setContentDescription(title+" vinyl color");
        button.setClickable(true);button.setFocusable(true);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(color);bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1),0xff8d9baa);
        button.setBackground(bg);
        LayoutParams params=new LayoutParams(0,-1,1);
        params.setMargins(dp(3),0,dp(3),0);
        row.addView(button,params);
        button.setOnClickListener(v->setColor(color));
    }

    public void setListener(Listener callback){listener=callback;}
    /** Retained for five existing dialog Apply handlers; any wheel color is valid. */
    public int getColorOrThrow(){return getColor();}
    public int getColor(){return wheel.getColor();}
    public void setColor(int color){
        int rgb=0xff000000|(color&0xffffff);
        wheel.setColor(rgb);
        refresh(rgb);
    }
    private void acceptFromWheel(int color){refresh(color);}
    private void refresh(int color){
        if(syncing)return;
        syncing=true;
        try{
            float[] hsv=new float[3];Color.colorToHSV(color,hsv);
            int percent=Math.round(hsv[2]*100);
            value.setProgress(percent);
            valueLabel.setText(getContext().getString(R.string.color_value_brightness_label,percent));
            hsLabel.setText(String.format(Locale.US,"Hue %d°  ·  Saturation %d%%",
                Math.round(hsv[0]),Math.round(hsv[1]*100)));
            preview.setText(String.format(Locale.US,"#%06X",color&0xffffff));
            preview.setTextColor(perceivedContrast(color));
            GradientDrawable fill=new GradientDrawable();fill.setColor(color);fill.setCornerRadius(dp(8));
            preview.setBackground(fill);
        }finally{syncing=false;}
        if(listener!=null)listener.onColor(color);
    }
    private static int perceivedContrast(int c){
        double y=.2126*Color.red(c)+.7152*Color.green(c)+.0722*Color.blue(c);
        return y>150?Color.BLACK:Color.WHITE;
    }
    private TextView label(String s,int size){
        TextView t=new TextView(getContext());t.setText(s);t.setTextSize(size);
        t.setTextColor(0xffdde6ee);t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(dp(28));return t;
    }
    private int dp(float x){return Math.round(getResources().getDisplayMetrics().density*x);}
}

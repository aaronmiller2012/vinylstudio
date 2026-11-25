package com.halftonestudio;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.function.IntConsumer;

/** Accessible, offline welcome tour. Never references or modifies the render engine. */
public final class VinylQuickStartGuide {
    private VinylQuickStartGuide() {}
    static final String KEY_SEEN="quickStartGuideSeenV2494";
    static final String KEY_TIPS="quickStartTipsEnabledV2494";
    private static final int BG=StudioTheme.SURFACE, PANEL=StudioTheme.SURFACE_HIGH, PRIMARY=StudioTheme.ACCENT_INK, TEXT=StudioTheme.TEXT, SECONDARY=StudioTheme.MUTED;
    // Five short first-run pages, then question-style Tips & Tricks topics.
    private static final int FIRST_RUN_PAGES=VinylGuidePolicy.GUIDE_PAGES;
    private static final int[] TITLES={R.string.guide_title_0,R.string.guide_title_1,R.string.guide_title_2,R.string.guide_title_3,R.string.guide_title_4,R.string.guide_title_5,R.string.guide_title_6,R.string.guide_title_7,R.string.guide_title_8,R.string.guide_title_9,R.string.guide_title_10,R.string.guide_title_11,R.string.guide_title_12,R.string.guide_title_13,R.string.guide_title_14,R.string.guide_title_15};
    private static final int[] DESCRIPTIONS={R.string.guide_body_0,R.string.guide_body_1,R.string.guide_body_2,R.string.guide_body_3,R.string.guide_body_4,R.string.guide_body_5,R.string.guide_body_6,R.string.guide_body_7,R.string.guide_body_8,R.string.guide_body_9,R.string.guide_body_10,R.string.guide_body_11,R.string.guide_body_12,R.string.guide_body_13,R.string.guide_body_14,R.string.guide_body_15};
    private static final int[] TIPS={R.string.guide_tip_0,R.string.guide_tip_1,R.string.guide_tip_2,R.string.guide_tip_3,R.string.guide_tip_4,R.string.guide_tip_5,R.string.guide_tip_6,R.string.guide_tip_7,R.string.guide_tip_8,R.string.guide_tip_9,R.string.guide_tip_10,R.string.guide_tip_11,R.string.guide_tip_12,R.string.guide_tip_13,R.string.guide_tip_14,R.string.guide_tip_15};
    private static int dp(Activity a,int value){return Math.round(value*a.getResources().getDisplayMetrics().density);}
    private static GradientDrawable shape(int fill,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(radius);return d;}
    private static TextView label(Activity a,int size,int color){TextView t=new TextView(a);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private static TextView button(Activity a,String name,boolean primary){TextView t=label(a,14,primary?BG:TEXT);t.setText(name);t.setGravity(Gravity.CENTER);t.setTypeface(null,android.graphics.Typeface.BOLD);t.setBackground(shape(primary?PRIMARY:PANEL,dp(a,13)));t.setMinimumHeight(dp(a,48));t.setContentDescription(name);return t;}
    public static boolean isFirstInstall(Activity activity,SharedPreferences prefs){
        if(prefs.contains(KEY_SEEN))return false;
        boolean oldState=prefs.contains("v231SwipeTip")||prefs.contains("editor")||prefs.contains("workflowStep")||prefs.contains("draftSnipV22")||prefs.contains("sizeProfile")||prefs.contains("recentProjectUris")
            ||PhotoRevisionStore.hasPhoto(activity.getFilesDir())||new java.io.File(activity.getFilesDir(),"editing-photo.png").exists()||new java.io.File(activity.getFilesDir(),"editing-photo.jpg").exists();
        boolean updated=true; // On uncertainty, do not interrupt an existing user.
        try{
            PackageInfo info=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);
            updated=info.lastUpdateTime-info.firstInstallTime>2000L;
        }catch(Exception ignored){}
        boolean show=VinylGuidePolicy.shouldAutoShow(false,oldState,updated);
        if(!show)prefs.edit().putBoolean(KEY_SEEN,true).putBoolean(KEY_TIPS,false).apply();
        return show;
    }
    public static void tip(Activity a,SharedPreferences prefs,String name,int message){
        String key="quickStartTipSeen_"+name;
        if(!VinylGuidePolicy.mayShowTip(prefs.getBoolean(KEY_SEEN,false),prefs.getBoolean(KEY_TIPS,false),prefs.getBoolean(key,false)))return;
        prefs.edit().putBoolean(key,true).apply();
        Toast.makeText(a,a.getString(message),Toast.LENGTH_LONG).show();
    }
    public static boolean tipsEnabled(SharedPreferences prefs){return prefs.getBoolean(KEY_TIPS,false);}
    public static void setTips(SharedPreferences prefs,boolean enabled){prefs.edit().putBoolean(KEY_TIPS,enabled).apply();}

    public static void show(Activity activity,SharedPreferences prefs,Runnable trySample,boolean fullHelp){
        show(activity,prefs,trySample,fullHelp,-1,OutputDeviceProfile.CRICUT,false,null,null);
    }
    /**
     * @param workspace current workspace (or -1 for the first-run tour) used to open on the most relevant page
     * @param openWorkspace optional shortcut that closes the guide and switches to a workspace; null hides it
     */
    public static void show(Activity activity,SharedPreferences prefs,Runnable trySample,boolean fullHelp,
                            int workspace,int outputDevice,boolean hasPhoto,String[] workspaceLabels,IntConsumer openWorkspace){
        final int firstPage=fullHelp?FIRST_RUN_PAGES:0;
        final int pageCount=(fullHelp?TITLES.length:FIRST_RUN_PAGES)-firstPage;
        final int suggested=Math.min(pageCount-1,VinylGuidePolicy.suggestedPage(fullHelp,workspace,outputDevice));
        final boolean contextual=workspace>=0&&workspaceLabels!=null&&workspace<workspaceLabels.length;
        Dialog dialog=new Dialog(activity);
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity,19),dp(activity,17),dp(activity,19),dp(activity,16));root.setBackground(shape(BG,dp(activity,22)));
        ScrollView scroller=new ScrollView(activity);scroller.setFillViewport(true);scroller.addView(root);
        // Floating dialogs normally receive safe bounds; also honor IME/cutout insets
        // when a device/vendor renders them edge-to-edge (Android 15/16).
        scroller.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=35){
                android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|
                    android.view.WindowInsets.Type.displayCutout()|android.view.WindowInsets.Type.ime());
                scroller.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            }else scroller.setPadding(0,0,0,0); // pre-35 dialog window already fits bars
            return insets;
        });
        dialog.setContentView(scroller);
        TextView kicker=label(activity,12,PRIMARY);kicker.setText(fullHelp?R.string.guide_kicker_help:R.string.guide_kicker);root.addView(kicker);
        TextView page=label(activity,12,SECONDARY);page.setGravity(Gravity.END);root.addView(page);
        TextView topics=button(activity,activity.getString(R.string.guide_topics),false);
        if(fullHelp||contextual)root.addView(topics,new LinearLayout.LayoutParams(-1,dp(activity,48)));
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(activity,12),dp(activity,19),dp(activity,12),dp(activity,16));
        content.setBackground(shape(PANEL,dp(activity,15)));LinearLayout.LayoutParams card=new LinearLayout.LayoutParams(-1,-2);card.topMargin=dp(activity,13);root.addView(content,card);
        TextView title=label(activity,23,TEXT);title.setGravity(Gravity.CENTER);title.setTypeface(null,android.graphics.Typeface.BOLD);content.addView(title);
        TextView description=label(activity,15,TEXT);description.setGravity(Gravity.START);description.setPadding(0,dp(activity,14),0,dp(activity,12));content.addView(description);
        TextView tip=label(activity,13,PRIMARY);tip.setPadding(dp(activity,11),dp(activity,12),dp(activity,11),dp(activity,12));tip.setBackground(shape(BG,dp(activity,10)));content.addView(tip);
        TextView go=button(activity,"",true);
        LinearLayout.LayoutParams gol=new LinearLayout.LayoutParams(-1,dp(activity,48));gol.topMargin=dp(activity,10);root.addView(go,gol);
        TextView dots=label(activity,14,SECONDARY);dots.setGravity(Gravity.CENTER);dots.setPadding(0,dp(activity,13),0,dp(activity,5));root.addView(dots);
        CheckBox disable=new CheckBox(activity);disable.setText(R.string.guide_disable_tips);disable.setTextColor(TEXT);
        disable.setChecked(!prefs.getBoolean(KEY_TIPS,true));disable.setPadding(0,dp(activity,7),0,dp(activity,10));root.addView(disable);
        LinearLayout actions=new LinearLayout(activity);actions.setGravity(Gravity.CENTER_VERTICAL);root.addView(actions);
        TextView skip=button(activity,activity.getString(fullHelp?R.string.guide_close:R.string.guide_skip),false);
        TextView back=button(activity,activity.getString(R.string.guide_back),false);
        TextView next=button(activity,activity.getString(R.string.guide_next),true);
        LinearLayout.LayoutParams s=new LinearLayout.LayoutParams(0,dp(activity,48),1);s.rightMargin=dp(activity,5);actions.addView(skip,s);
        LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,dp(activity,48),1);b.rightMargin=dp(activity,5);actions.addView(back,b);
        actions.addView(next,new LinearLayout.LayoutParams(0,dp(activity,48),1.6f));
        TextView sample=button(activity,activity.getString(R.string.guide_sample),false);
        LinearLayout.LayoutParams spl=new LinearLayout.LayoutParams(-1,dp(activity,48));spl.topMargin=dp(activity,10);root.addView(sample,spl);
        sample.setVisibility(trySample==null?View.GONE:View.VISIBLE);
        final int[] index={contextual?suggested:0};
        Runnable render=()->{
            int i=index[0];int topic=firstPage+i;page.setText(activity.getString(R.string.guide_page,i+1,pageCount));
            kicker.setText(contextual&&i==suggested?activity.getString(R.string.guide_suggested,workspaceLabels[workspace]).toUpperCase(java.util.Locale.ROOT)
                :activity.getString(fullHelp?R.string.guide_kicker_help:R.string.guide_kicker));
            int target=VinylGuidePolicy.workspaceForPage(fullHelp,i);
            boolean offer=openWorkspace!=null&&workspaceLabels!=null&&target<workspaceLabels.length
                &&VinylGuidePolicy.mayOfferShortcut(target,hasPhoto);
            go.setVisibility(offer?View.VISIBLE:View.GONE);
            if(offer){String text=activity.getString(R.string.guide_go,workspaceLabels[target]);go.setText(text);go.setContentDescription(text);}
            title.setText(TITLES[topic]);description.setText(DESCRIPTIONS[topic]);tip.setText(activity.getString(R.string.guide_tip_prefix,activity.getString(TIPS[topic])));
            StringBuilder ds=new StringBuilder();for(int j=0;j<pageCount;j++)ds.append(j==i?"●":"○").append(' ');dots.setText(ds.toString());
            scroller.scrollTo(0,0);
            back.setVisibility(i==0?View.INVISIBLE:View.VISIBLE);
            next.setText(i==pageCount-1?(fullHelp?R.string.guide_done:R.string.guide_start):R.string.guide_next);
            next.setContentDescription(next.getText());
        };
        topics.setOnClickListener(v->{
            String[] names=new String[pageCount];
            for(int j=0;j<pageCount;j++)names[j]=(contextual&&j==suggested?"★ ":"")+activity.getString(TITLES[firstPage+j]);
            new android.app.AlertDialog.Builder(activity).setTitle(R.string.guide_topics)
                .setItems(names,(d,which)->{index[0]=which;render.run();}).show();
        });
        Runnable persist=()->prefs.edit().putBoolean(KEY_SEEN,true).putBoolean(KEY_TIPS,!disable.isChecked()).apply();
        skip.setOnClickListener(v->{persist.run();dialog.dismiss();});
        back.setOnClickListener(v->{index[0]=Math.max(0,index[0]-1);render.run();});
        next.setOnClickListener(v->{if(index[0]>=pageCount-1){persist.run();dialog.dismiss();}else{index[0]++;render.run();}});
        go.setOnClickListener(v->{
            int target=VinylGuidePolicy.workspaceForPage(fullHelp,index[0]);
            persist.run();dialog.dismiss();
            if(openWorkspace!=null&&target>=0)openWorkspace.accept(target);
        });
        sample.setOnClickListener(v->{persist.run();dialog.dismiss();if(trySample!=null)trySample.run();});
        dialog.setOnDismissListener(d->persist.run()); // Back, tapping outside, and Skip are all one-time dismissals.
        final float[] touch=new float[2];
        // Swipe handling; taps fall through to performClick() so TalkBack activation still works.
        //noinspection ClickableViewAccessibility
        content.setOnTouchListener((v,e)->{
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:touch[0]=e.getX();touch[1]=e.getY();return true;
                case MotionEvent.ACTION_UP:
                    float dx=e.getX()-touch[0],dy=e.getY()-touch[1];
                    if(Math.abs(dx)>dp(activity,55)&&Math.abs(dx)>Math.abs(dy)*1.4f){index[0]=Math.min(pageCount-1,Math.max(0,index[0]+(dx<0?1:-1)));render.run();}
                    else v.performClick();return true;
                case MotionEvent.ACTION_MOVE:
                    if(Math.abs(e.getY()-touch[1])>dp(activity,10)&&Math.abs(e.getY()-touch[1])>Math.abs(e.getX()-touch[0])){
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:return true;
                default:return true;
            }
        });
        content.setContentDescription(activity.getString(R.string.guide_swipe_hint));
        render.run();dialog.show();
        Window win=dialog.getWindow();if(win!=null){win.setBackgroundDrawableResource(android.R.color.transparent);win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp=win.getAttributes();lp.width=Math.min(activity.getResources().getDisplayMetrics().widthPixels-dp(activity,18),dp(activity,480));lp.height=-2;lp.gravity=Gravity.CENTER;lp.dimAmount=.5f;win.setAttributes(lp);}
    }
}

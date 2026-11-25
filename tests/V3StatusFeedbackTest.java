package com.halftonestudio;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * D9: rendering/export feedback is visible and never shows raw technical text.
 * Messages below are the app's actual status strings (StudioActivity and res/values/strings.xml).
 */
public final class V3StatusFeedbackTest {
    static void ok(boolean v,String m){if(!v)throw new AssertionError(m);}
    static void kind(String text,StatusLinePolicy.Kind want){
        StatusLinePolicy.Kind got=StatusLinePolicy.classify(text);
        ok(got==want,"\""+text+"\" classified "+got+", expected "+want);
    }
    public static void main(String[] args){
        // Work in progress stays visible until replaced.
        for(String t:new String[]{"Generating cut paths…","Draft cut preview…","Writing SVG archive…",
                "Checking cut sheets and preparing ZIP…","Writing verified ZIP…","Preparing cut-ready geometry…"}){
            kind(t,StatusLinePolicy.Kind.PROGRESS);ok(StatusLinePolicy.displayMs(t)==0,t+" must not auto-hide");
        }
        // Problems stay long enough to read and act on.
        for(String t:new String[]{"Render failed: These settings could not be rendered","Export failed · see details",
                "Project save failed: Unable to save project","Project not saved: insufficient memory",
                "Auto fit: Unable to fit this artwork","Layer preview unavailable; cut geometry remains unchanged",
                "Not enough memory for this style; lower Detail","Diagnostic ZIP saved · NOT CUT QUALIFIED",
                "12 shapes · Check registration marks","Photo preview needs more memory",
                "Cut-ready geometry shown; this is what will be cut. To meet the 0.80 mm minimum and 0.50 mm weed gap, cut prep filled 42 holes. The preview shows the result. A test cut is still required.",
                "Cut-ready geometry shown; this is what will be cut. To meet the 0.80 mm minimum and 0.50 mm weed gap, cut prep removed 3 pieces. The preview shows the result. A test cut is still required.",
                "Cut-ready · cut prep filled 42 holes (0.50 mm gap). Tap for Cut Check; test-cut first.",
                "Cut-ready · cut prep removed 1 piece (0.80 mm min). Tap for Cut Check; test-cut first."}){
            kind(t,StatusLinePolicy.Kind.PROBLEM);ok(StatusLinePolicy.displayMs(t)>=10_000,t+" hides too soon");
        }
        // Routine completions get out of the way.
        for(String t:new String[]{"Crop applied · Snip, then adjust Lighting and Size",
                "Cut-ready geometry shown; this is what will be cut. A test cut is still required.",
                "Cut-ready dots shown; these are the dots that will be cut. A test cut is still required.",
                // Background stages may be cancelled without a follow-up message: never a sticky PROGRESS.
                "Preparing cut-ready geometry in the background","1234 dots · Preparing cut-ready dots in the background",
                "Select a photo to begin"}){
            kind(t,StatusLinePolicy.Kind.INFO);long ms=StatusLinePolicy.displayMs(t);ok(ms>0&&ms<=6000,t+" should auto-hide, got "+ms);
        }
        kind("Cut-ready geometry unavailable; export will prepare it and show the result",StatusLinePolicy.Kind.PROBLEM);
        kind("",StatusLinePolicy.Kind.HIDDEN);kind(null,StatusLinePolicy.Kind.HIDDEN);

        // Raw technical detail never reaches the user.
        String fb="These settings could not be rendered";
        Throwable[] technical={
            new NullPointerException("Cannot invoke \"com.halftonestudio.StyleKit$Layer.loops\" because \"layer\" is null"),
            new IOException("/data/user/0/com.halftonestudio/cache/x.part: open failed: ENOSPC (No space left on device)"),
            new IllegalStateException("java.util.ConcurrentModificationException"),
            new ArrayIndexOutOfBoundsException("Index 12 out of bounds for length 12"),
            new ClassCastException("class java.lang.Integer cannot be cast to class java.lang.String"),
            new RuntimeException("Failed: content://com.android.providers/document/123"),
            new IOException(),new RuntimeException("null")};
        for(Throwable t:technical){
            String shown=FailureText.describe(t,fb);
            ok(fb.equals(shown),"leaked technical text: "+shown);
        }
        ok("Not enough device memory".equals(FailureText.describe(new OutOfMemoryError("Failed to allocate 64 MB"),fb)),"OOM text");
        ok(FailureText.describe(new FileNotFoundException("/storage/emulated/0/x.zip"),fb).startsWith("Document is unavailable"),"file access text");
        // The app's own actionable validation messages are kept.
        for(String own:new String[]{"Too many paths in layer 3; reduce Detail",
                "Stars cannot meet the requested cut width at this spacing. Increase Spacing or Shape Size.",
                "Style requires at least 3 colors"})
            ok(own.equals(FailureText.describe(new IllegalArgumentException(own),fb)),"own actionable message was hidden: "+own);
        ok(fb.equals(FailureText.describe(new IllegalStateException("failed at com.halftonestudio.StyleKit.toSvg(StyleKit.java:760)"),fb)),"stack frame leaked");
        System.out.println("PASS status feedback: progress/problem/info policy on real messages; no raw technical text");
    }
}

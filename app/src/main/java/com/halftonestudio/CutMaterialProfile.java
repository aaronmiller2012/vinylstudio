package com.halftonestudio;

/** Editable, advisory starting points, NEVER machine/material certifications. */
public final class CutMaterialProfile {
    public static final int CUSTOM=0, REMOVABLE=1, PERMANENT=2, HTV=3, DELICATE=4;
    public static final class Profile {
        public final String name; public final float featureMm, gapMm, dotMm, sheetGapMm;
        Profile(String n,float f,float g,float d,float s){name=n;featureMm=f;gapMm=g;dotMm=d;sheetGapMm=s;}
    }
    private static final Profile[] PRESETS={
        new Profile("Custom · preserve current dimensions",0f,0f,0f,0f),
        new Profile("Removable vinyl · starting point",.80f,.30f,.80f,.20f),
        new Profile("Permanent vinyl · starting point",.65f,.25f,.70f,.18f),
        new Profile("Heat-transfer vinyl · starting point",.85f,.35f,.90f,.25f),
        new Profile("Delicate material · conservative",1.10f,.45f,1.10f,.35f)
    };
    private CutMaterialProfile() {}
    public static Profile byId(int id){return PRESETS[Math.max(0,Math.min(PRESETS.length-1,id))];}
    public static String[] names(){String[] n=new String[PRESETS.length];for(int i=0;i<n.length;i++)n[i]=PRESETS[i].name;return n;}
}

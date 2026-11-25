package com.halftonestudio;

/** Immutable, advisory named calibration record. Never applies rendering settings. */
public final class CalibrationProfile {
    public final String name, notes, results;
    public CalibrationProfile(String name,String notes,String results){
        if(name==null || name.trim().isEmpty() || name.trim().length()>80 || notes==null || notes.length()>2048 || !CalibrationRecord.valid(results)) throw new IllegalArgumentException("Invalid calibration profile");
        this.name=name.trim();this.notes=notes;this.results=results;
    }
    public static String encode(CalibrationProfile p){return enc(p.name)+"."+enc(p.notes)+"."+p.results;}
    private static String enc(String s){return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static String dec(String s){return new String(java.util.Base64.getUrlDecoder().decode(s),java.nio.charset.StandardCharsets.UTF_8);}
    public static CalibrationProfile decode(String s){if(s==null||s.length()>12000)throw new IllegalArgumentException("Invalid profile");String[] a=s.split("\\.",-1);if(a.length!=3)throw new IllegalArgumentException("Invalid profile");return new CalibrationProfile(dec(a[0]),dec(a[1]),a[2]);}
}

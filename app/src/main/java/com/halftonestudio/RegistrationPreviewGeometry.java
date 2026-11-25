package com.halftonestudio;

/** Android-independent registration geometry for preview transforms and hit-test parity.
 * The padding calculation is deliberately non-throwing during a live drag:
 * a temporarily invalid position must remain visible so it can be moved back.
 */
public final class RegistrationPreviewGeometry {
    private RegistrationPreviewGeometry() {}
    public static float padding(float w,float h,float diameter,float clearance,
            float[][] marks,float basePad){
        float r=RegistrationMarks.markRadius(diameter,clearance);
        float pad=Math.max(0,basePad);
        if(marks==null)return pad;
        for(float[] m:marks){
            if(m==null||m.length!=2||!Float.isFinite(m[0])||!Float.isFinite(m[1]))continue;
            pad=Math.max(pad,Math.max(Math.max(-m[0],m[0]-w),
                Math.max(-m[1],m[1]-h))+r+.5f);
        }
        return pad;
    }
    public static String issue(float w,float h,float diameter,float clearance,float[][] marks){
        try {
            return RegistrationCutGeometry.basic(w,h,diameter,clearance,marks).firstIssue();
        }catch(IllegalArgumentException err){return err.getMessage();}
    }
}

package com.halftonestudio;

/** Regression for immersive editor/stipple/cell portrait mark drag geometry. */
public final class RegistrationViewportRC147Test {
    private static void near(float actual,float expected,String note){
        if(Math.abs(actual-expected)>1e-4f)
            throw new AssertionError(note+": "+actual+" != "+expected);
    }
    private static void check(boolean valid,String note){
        if(!valid)throw new AssertionError(note);
    }
    public static void main(String[] args){
        for(float width:new float[]{60f,125f,279.4f})
        for(float height:new float[]{60f,192f,279.4f})
        for(float diameter:new float[]{2f,4f,12f}){
            float clearance=1.5f, outside=8f;
            float base=RegistrationViewportMath.stablePreviewPadding(width,height,
                diameter,clearance,outside);
            float[][] marks=RegistrationMarks.defaultPositions(width,height,diameter,clearance);
            float defaultPhysical=RegistrationPreviewGeometry.padding(width,height,diameter,
                clearance,marks,outside);
            near(base,defaultPhysical,"default mark must match physical sheet in preview");
            marks[0][0]-=48f;marks[1][0]+=72f;marks[2][1]+=22f;
            float expandedPhysical=RegistrationPreviewGeometry.padding(width,height,diameter,
                clearance,marks,outside);
            check(expandedPhysical>defaultPhysical,"extreme marks must expand export sheet");
            float after=RegistrationViewportMath.stablePreviewPadding(width,height,
                diameter,clearance,outside);
            near(after,base,"custom mark drag must not change artwork fit");
            float screenWidth=716,screenHeight=1300;
            float scale=Math.min((screenWidth-25)/(width+2*base),
                (screenHeight-28)/(height+2*base));
            float originX=(screenWidth-(width+2*base)*scale)*.5f;
            float originY=(screenHeight-(height+2*base)*scale)*.5f;
            float[] mark=RegistrationMarks.defaultPositions(width,height,diameter,clearance)[0];
            float sx=originX+(mark[0]+base)*scale;
            float sy=originY+(mark[1]+base)*scale;
            // Drawing overlay translated by pad and touch mapping subtracting it
            // MUST round-trip the exact physical mark coordinate.
            near((sx-originX)/scale-base,mark[0],"screen X / artwork X parity");
            near((sy-originY)/scale-base,mark[1],"screen Y / artwork Y parity");
        }
        near(RegistrationViewportMath.clampTouch(-5f,716f,13f),13f,"left clipping");
        near(RegistrationViewportMath.clampTouch(800f,716f,13f),703f,"right clipping");
        near(RegistrationViewportMath.clampTouch(120f,716f,13f),120f,"unchanged normal drag");
        near(RegistrationViewportMath.clampTouch(-1f,40f,200f),19.2f,"extreme zoom inset");
        System.out.println("PASS RC147: overlay translation, stable preview fit, export sheet growth, drag recovery");
    }
}

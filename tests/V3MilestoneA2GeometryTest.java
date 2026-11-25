package com.halftonestudio;

import java.io.IOException;

/** Executable A2 regression: actual final SVG geometry is the authority.
 * No cutter API or Android SDK is used by this host test.
 */
public final class V3MilestoneA2GeometryTest {
    private static void require(boolean value, String description) {
        if(!value)throw new AssertionError(description);
    }
    private static String svg(String viewBox, String shape) {
        return "<svg xmlns='http://www.w3.org/2000/svg' width='100mm' height='100mm' viewBox='"+
            viewBox+"'>"+shape+"</svg>";
    }
    private static void mismatch(int device,String first,String second) throws Exception {
        boolean rejected=false;
        try {V3MilestoneA1GeometryTest.zip(device,false,first,second);}
        catch(IOException expected) { rejected=expected.getMessage().contains("frame"); }
        require(rejected,"Mismatched root viewBox frame was allowed for target "+device);
    }
    public static void main(String[] args) throws Exception {
        // A nested ring with distinct circular edges is NOT a crossing cut.
        String outer="<circle cx='45' cy='45' r='12'/>";
        FinalCutSheetSafety.Report nested=V3MilestoneA1GeometryTest.inspect(outer+
            "<circle cx='45' cy='45' r='4'/>");
        require(nested.critical==0 && nested.narrow==0 && nested.unverified==0,
            "Concentric circular ring incorrectly flagged as cut collision");
        FinalCutSheetSafety.Report offsetInner=V3MilestoneA1GeometryTest.inspect(outer+
            "<circle cx='51' cy='45' r='4'/>");
        require(offsetInner.critical==0 && offsetInner.narrow==0,
            "Safely enclosed non-concentric ring flagged as collision");
        FinalCutSheetSafety.Report nearInner=V3MilestoneA1GeometryTest.inspect(outer+
            "<circle cx='52.85' cy='45' r='4'/>");
        require(nearInner.narrow>0 && nearInner.critical==0,
            "Narrow inset between circular contours was not reported");
        FinalCutSheetSafety.Report internalTangent=V3MilestoneA1GeometryTest.inspect(outer+
            "<circle cx='53' cy='45' r='4'/>");
        require(internalTangent.critical>0,"Internally tangent circles passed as safe");
        FinalCutSheetSafety.Report crossing=V3MilestoneA1GeometryTest.inspect(outer+
            "<circle cx='54' cy='45' r='4'/>");
        require(crossing.critical>0,"Crossing circles passed as safe");
        FinalCutSheetSafety.Report identical=V3MilestoneA1GeometryTest.inspect(outer+outer);
        require(identical.critical>0,"Duplicate circular cutting path passed as safe");
        FinalCutSheetSafety.Report separated=V3MilestoneA1GeometryTest.inspect(outer+
            "<circle cx='70' cy='45' r='4'/>");
        require(separated.critical==0,"Distant circles incorrectly flagged");

        String square="<rect x='25' y='25' width='8' height='8'/>";
        String frame=svg("0 0 100 100",square);
        String shifted=svg("-6 0 100 100",square);
        String scaled=svg("0 0 200 200",square);
        String rotatedRoot="<svg xmlns='http://www.w3.org/2000/svg' width='100mm' height='100mm' "+
            "viewBox='0 0 100 100' transform='translate(5 0)'>"+square+"</svg>";
        for(int target:new int[]{OutputDeviceProfile.CRICUT,OutputDeviceProfile.SILHOUETTE_BASIC}) {
            mismatch(target,frame,shifted);
            mismatch(target,frame,scaled);
            mismatch(target,frame,rotatedRoot);
            byte[] archive=V3MilestoneA1GeometryTest.zip(target,false,frame,frame);
            require(V3MilestoneA1GeometryTest.entry(archive,"physical-artboards.csv").contains("100.000,100.000"),
                "Identical root frame was incorrectly rejected");
        }
        System.out.println("PASS A2 circle containment, narrow holes, root viewBox/scale/transform placement parity SVG+DXF");
    }
}

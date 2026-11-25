package com.halftonestudio;

import java.util.List;

/** Separates hard cut-contour contact, under-width concerns and incomplete
 * scans. Acknowledgement never changes paths or the physical minimum width.
 */
public final class CutSafetyReviewPolicy {
    private CutSafetyReviewPolicy() {}
    public static final class Decision {
        public final int critical, narrow, unverified;
        Decision(int critical,int narrow,int unverified){
            this.critical=critical;this.narrow=narrow;this.unverified=unverified;
        }
        public int total(){return critical+narrow+unverified;}
        public boolean requiresConfirmation(){return total()>0;}
        public boolean dangerous(){return critical>0||unverified>0;}
        public String title(){return dangerous()?"Cut geometry needs review":"Narrow cut warning";}
        public String message(){
            StringBuilder sb=new StringBuilder();
            if(critical>0)sb.append(critical).append(" touching, crossing or overlapping cut-contour findings.\n");
            if(unverified>0)sb.append(unverified).append(" scan(s) exceeded the verification budget. Cut safety is unverified.\n");
            if(narrow>0)sb.append(narrow).append(" narrow contour or spacing findings.\n");
            sb.append("The ZIP will include cut-safety-review.csv. Review and test-cut before applying vinyl. ");
            if(dangerous())sb.append("Continuing may produce defective or unweedable cuts.");
            return sb.toString();
        }
    }
    /** The bounded warning collector stops scanning after reaching its cap.
     * A full report cannot be claimed from exactly maxWarnings findings. */
    public static Decision assess(List<StyleCutGeometryPreflight.Warning> warnings,int maxWarnings){
        Decision observed=assess(warnings);
        return maxWarnings>0&&warnings.size()>=maxWarnings?
            new Decision(observed.critical,observed.narrow,observed.unverified+1):observed;
    }
    public static Decision assess(List<StyleCutGeometryPreflight.Warning> warnings){
        int hard=0,narrow=0,unknown=0;
        for(StyleCutGeometryPreflight.Warning w:warnings){
            String reason=w.reason;
            if(reason.startsWith("Intersecting")||reason.startsWith("Touching")||
                    reason.startsWith("Overlapping"))hard++;
            else if(reason.contains("not verified")||reason.contains("complexity budget"))unknown++;
            else narrow++;
        }
        return new Decision(hard,narrow,unknown);
    }
}

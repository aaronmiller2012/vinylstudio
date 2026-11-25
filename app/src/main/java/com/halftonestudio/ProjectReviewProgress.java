package com.halftonestudio;

/** Session-only observations; publication is not a physical cutting qualification. */
final class ProjectReviewProgress {
    static final int CUT_ZIP=1, ASSEMBLY_PDF=2, REFERENCE_PDF=4, DIAGNOSTIC_ZIP=8;
    private final boolean[] checks=new boolean[5];
    private long revision;
    private int written;
    synchronized long revision(){return revision;}
    synchronized boolean checked(int index){return checks[index];}
    synchronized boolean confirm(long token,int index,boolean checked){
        if(token!=revision)return false;checks[index]=checked;return true;
    }
    synchronized void invalidate(){java.util.Arrays.fill(checks,false);written=0;revision++;}
    synchronized boolean record(long token,int kind){
        if(token!=revision)return false;
        if(kind!=CUT_ZIP&&kind!=ASSEMBLY_PDF&&kind!=REFERENCE_PDF&&kind!=DIAGNOSTIC_ZIP)throw new IllegalArgumentException("Unknown document kind");
        written|=kind;return true;
    }
    synchronized boolean written(int kind){return (written&kind)!=0;}
}

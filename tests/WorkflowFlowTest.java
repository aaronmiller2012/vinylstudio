package com.halftonestudio;
public final class WorkflowFlowTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        check(WorkflowFlow.NAMES.length==9,"9 stages incl. Light");
        check(WorkflowFlow.tool(WorkflowFlow.IMPORT)==0,"import tool");
        check(WorkflowFlow.tool(WorkflowFlow.CROP)==0,"crop and import share preparation engine");
        check(WorkflowFlow.tool(WorkflowFlow.SNIP)==8,"snip tool");
        check(WorkflowFlow.tool(WorkflowFlow.LIGHT)==10,"light tool");
        check(WorkflowFlow.tool(WorkflowFlow.LAYERS)==3,"layers");
        check(WorkflowFlow.tool(WorkflowFlow.TYPE)==9,"type selection");
        check(WorkflowFlow.tool(WorkflowFlow.ADJUST)==1,"adjustment engine");
        check(WorkflowFlow.tool(WorkflowFlow.MARKS)==5,"registration marks");
        check(WorkflowFlow.tool(WorkflowFlow.EXPORT)==7,"export");
        check(!WorkflowFlow.needsPhoto(0)&&WorkflowFlow.needsPhoto(1),"image required after import");
        check(!WorkflowFlow.mustFinishSnip(2,true)&&WorkflowFlow.mustFinishSnip(WorkflowFlow.LIGHT,true)&&WorkflowFlow.mustFinishSnip(3,true),"unfinished snip blocks later steps");
        check(WorkflowFlow.clamp(-4)==0&&WorkflowFlow.clamp(12)==WorkflowFlow.LIGHT,"safe bounds");
        check(WorkflowFlow.next(WorkflowFlow.SNIP,1)==WorkflowFlow.LIGHT,"Light follows Snip");
        check(WorkflowFlow.next(WorkflowFlow.LIGHT,1)==WorkflowFlow.LAYERS,"Layers follow Light");
        check(WorkflowFlow.next(WorkflowFlow.EXPORT,1)==-2,"export is final");
        System.out.println("PASS: nine-step workflow with post-snip Light, navigation and pending-snip safeguards");
    }
}

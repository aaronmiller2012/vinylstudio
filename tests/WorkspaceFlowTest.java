package com.halftonestudio;
import java.util.*;
public final class WorkspaceFlowTest {
    private static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
    public static void main(String[] args){
        check(WorkflowFlow.PHASE_NAMES.length==3,"there must be exactly three workspaces");
        check(Arrays.equals(WorkflowFlow.stages(WorkflowFlow.PREPARE),new int[]{0,1,2,8}),"prepare tools");
        check(Arrays.equals(WorkflowFlow.stages(WorkflowFlow.CREATE),new int[]{3,4,5}),"create tools");
        check(Arrays.equals(WorkflowFlow.stages(WorkflowFlow.CUT),new int[]{6,7}),"production tools");
        int[] stageToPhase={0,0,0,1,1,1,2,2,0};
        for(int stage=0;stage<9;stage++){
            check(WorkflowFlow.phaseOf(stage)==stageToPhase[stage],"phase match "+stage);
            check(WorkflowFlow.tool(stage)==WorkflowFlow.TOOL_IDS[stage],"tool match "+stage);
            check(WorkflowFlow.navigate(stage,true,false)==stage,"can revisit any completed stage "+stage);
        }
        check(WorkflowFlow.navigate(7,false,false)==WorkflowFlow.IMPORT,"cannot export without a photo");
        check(WorkflowFlow.navigate(5,true,true)==WorkflowFlow.SNIP,"unfinished snip blocks Create");
        check(WorkflowFlow.navigate(7,true,true)==WorkflowFlow.SNIP,"unfinished snip blocks Cut");
        check(WorkflowFlow.navigate(1,true,true)==WorkflowFlow.CROP,"earlier prepare stages remain reachable");
        check(WorkflowFlow.stageForTool(2,5)==WorkflowFlow.LAYERS,"palette shortcut stays in Create/Layers");
        check(WorkflowFlow.stageForTool(3,5)==WorkflowFlow.LAYERS,"stack shortcut stays in Create/Layers");
        check(WorkflowFlow.stageForTool(4,5)==WorkflowFlow.LAYERS,"curves shortcut stays in Create/Layers");
        check(WorkflowFlow.stageForTool(6,5)==WorkflowFlow.EXPORT,"diagnostics shortcut goes to Cut");
        check(WorkflowFlow.stageForTool(8,5)==WorkflowFlow.SNIP,"Snip shortcut goes to Prepare");
        check(WorkflowFlow.stageForTool(10,5)==WorkflowFlow.LIGHT,"Light shortcut goes to Prepare");
        check(WorkflowFlow.stageForTool(0,WorkflowFlow.CROP)==WorkflowFlow.CROP,"crop panel maintains Crop highlight");
        check(WorkflowFlow.contextualName(3,2,false).equals("Vinyl palette"),"palette title");
        check(WorkflowFlow.contextualName(3,3,true).equals("Application stack"),"stack title");
        check(WorkflowFlow.contextualName(7,6,false).equals("Cut readiness"),"diagnostics title");
        int[] copied=WorkflowFlow.stages(0);copied[0]=99;
        check(WorkflowFlow.stages(0)[0]==0,"phase tool lists must not be mutable by callers");
        System.out.println("PASS: 3 workspaces; contextual navigation; snip protection; crop state; palette/stack/curve/detailed cut shortcuts");
    }
}

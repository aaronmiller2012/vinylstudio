package com.halftonestudio;
/** Android-independent phase and contextual-tool navigation for vinyl projects.
 * The eight underlying steps remain stable for saved project compatibility.
 */
public final class WorkflowFlow {
    private WorkflowFlow() {}
    /** Saved project stage IDs 0–7 are unchanged. LIGHT is appended to preserve v2.10 files. */
    public static final int IMPORT=0,CROP=1,SNIP=2,LAYERS=3,TYPE=4,ADJUST=5,MARKS=6,EXPORT=7,LIGHT=8;
    /** User-facing order can differ from persisted stage identifiers. */
    private static final int[] ORDER={IMPORT,CROP,SNIP,LIGHT,LAYERS,TYPE,ADJUST,MARKS,EXPORT};
    public static final int PREPARE=0,CREATE=1,CUT=2;
    public static final String[] NAMES={"Import","Crop","Snip","Layers","Type","Adjust","Marks","Check & Export","Lighting"};
    public static final String[] PHASE_NAMES={"Prepare","Create","Cut"};
    public static final String[] PHASE_DESCRIPTIONS={"Import · Crop · Snip · Lighting · Size","Colors · Type · Adjust","Marks · Check · Export"};
    public static final int[] TOOL_IDS={0,0,8,3,9,1,5,7,10};
    public static final int[][] PHASE_STAGES={{IMPORT,CROP,SNIP,LIGHT},{LAYERS,TYPE,ADJUST},{MARKS,EXPORT}};
    public static int clamp(int stage){return Math.max(IMPORT,Math.min(LIGHT,stage));}
    public static int next(int stage,int direction){
        int position=0;for(int i=0;i<ORDER.length;i++)if(ORDER[i]==clamp(stage)){position=i;break;}
        int dest=position+direction;return dest<0?-1:dest>=ORDER.length?-2:ORDER[dest];
    }
    /** Preserve the legacy 0-7 stage IDs when reading older saved projects. */
    public static int clampPhase(int phase){return Math.max(PREPARE,Math.min(CUT,phase));}
    public static int phaseOf(int stage){int s=clamp(stage);return s<=SNIP||s==LIGHT?PREPARE:s<=ADJUST?CREATE:CUT;}
    public static int firstStage(int phase){return PHASE_STAGES[clampPhase(phase)][0];}
    public static int lastStage(int phase){int[] stages=PHASE_STAGES[clampPhase(phase)];return stages[stages.length-1];}
    public static int[] stages(int phase){return PHASE_STAGES[clampPhase(phase)].clone();}
    public static boolean needsPhoto(int stage){return clamp(stage)!=IMPORT;}
    public static boolean mustFinishSnip(int stage,boolean unfinished){return unfinished&&clamp(stage)!=IMPORT&&clamp(stage)!=CROP&&clamp(stage)!=SNIP;}
    public static int tool(int stage){return TOOL_IDS[clamp(stage)];}
    public static int navigate(int target,boolean hasPhoto,boolean pendingSnip){
        int next=clamp(target);if(!hasPhoto&&needsPhoto(next))return IMPORT;
        if(mustFinishSnip(next,pendingSnip))return SNIP;
        return next;
    }
    /** Maps an auxiliary subpanel to its visible workspace tool; preserves Crop vs Import. */
    public static int stageForTool(int requested,int currentStage){switch(requested){
        case 0:return currentStage==CROP?CROP:IMPORT;
        case 8:return SNIP;
        case 2:case 3:case 4:return LAYERS; // Palette, stack, curves use the Layers tool.
        case 9:return TYPE;
        case 1:return ADJUST;
        case 10:return LIGHT;
        case 5:return MARKS;
        case 6:case 7:return EXPORT; // Cut checks are contextual to production.
        default:return clamp(currentStage);
    }}
    public static String contextualName(int stage,int subtool,boolean stack){
        if(stage==LAYERS){if(subtool==2)return "Vinyl palette";if(subtool==4)return "Tone curves";if(stack)return "Application stack";}
        if(stage==LIGHT)return "Lighting · Photo Levels";
        if(stage==EXPORT&&subtool==6)return "Cut readiness";
        return NAMES[clamp(stage)];
    }
}

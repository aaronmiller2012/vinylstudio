package com.halftonestudio;

/** Pure-Java contract tests for project editing and stale asynchronous work. */
public final class StudioProjectSessionStateTest {
    static void yes(boolean value,String label){if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        StudioProjectSessionState state=new StudioProjectSessionState();
        yes(!state.isDirty()&&!state.isSaving(),"initial state");
        state.markEdited();long first=state.revision();
        yes(state.isDirty()&&state.beginSave(),"begin snapshot");
        yes(!state.beginSave(),"reject duplicate save");
        state.markEdited();
        yes(!state.completeSave(first,true),"new edits must remain dirty");
        yes(state.isDirty()&&!state.isSaving(),"snapshot completed but edits remain");
        yes(state.beginSave(),"second snapshot allowed");
        long second=state.revision();
        yes(state.completeSave(second,true)&&!state.isDirty(),"fresh save clears dirty");
        state.markEdited();yes(state.beginSave(),"save before source change");
        yes(!state.completeSave(state.revision(),false),"old photo save must not erase dirty flag");
        yes(state.isDirty(),"old photo not clean");
        long loadA=state.beginLoad();long openRevision=state.revision();
        long loadB=state.beginLoad();
        yes(!state.isCurrentLoad(loadA)&&state.isCurrentLoad(loadB),"stale open dismissed");
        state.markEdited();yes(state.revision()!=openRevision,"edits during load detected");
        state.invalidateLoads();yes(!state.isCurrentLoad(loadB),"photo import cancels stale load");
        long loadC=state.beginLoad();
        state.closeProject();
        yes(!state.isCurrentLoad(loadC),"close cancels in-flight load");
        yes(!state.isDirty(),"close resets dirty");
        yes(state.beginSave(),"save after close");
        state.failSave();yes(!state.isSaving(),"failed save unlocks queue");
        System.out.println("PASS RC56 project session state and stale-work guards");
    }
}

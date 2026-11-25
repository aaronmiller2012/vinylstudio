package com.halftonestudio;

import java.util.ArrayList;

public final class SnipTimelineTest {
    static void check(boolean value,String note){if(!value)throw new AssertionError(note);}
    static ArrayList<float[]> polygon(){ArrayList<float[]> p=new ArrayList<>();p.add(new float[]{.1f,.1f});p.add(new float[]{.5f,.1f});p.add(new float[]{.5f,.5f});return p;}
    public static void main(String[] args){
        SnipTimeline timeline=new SnipTimeline();SnipMask m=new SnipMask();ArrayList<float[]> draft=polygon();
        timeline.remember(draft,false,m,-1);
        draft.get(0)[0]=.24f; // edit should not change stored checkpoint
        SnipTimeline.State original=timeline.undo(draft,false,m,-1);
        check(original.points.get(0)[0]==.1f,"drag undo restores original coordinates");
        SnipTimeline.State changed=timeline.redo(original.points,false,original.mask,-1);
        check(changed.points.get(0)[0]==.24f,"drag redo restores moved point");
        timeline.remember(changed.points,false,m,-1);
        SnipMask.Operation op=new SnipMask.Operation(SnipMask.REMOVE_INSIDE,.1f,.1f,.6f,.1f,.6f,.6f);
        m.add(op);
        SnipTimeline.State beforeApply=timeline.undo(changed.points,true,m,-1);
        check(beforeApply.mask.size()==0,"undo snip restores uncut image");
        SnipTimeline.State afterApply=timeline.redo(beforeApply.points,beforeApply.closed,beforeApply.mask,-1);
        check(afterApply.mask.size()==1,"redo snip restores cut mask");
        check(afterApply.mask.operations().get(0).kind==SnipMask.REMOVE_INSIDE,"snip operation preserved");
        SnipMask second=new SnipMask();second.add(op);SnipMask.Operation extracted=second.removeAt(0);
        check(second.size()==0,"editable mask removed");second.insert(0,extracted);
        check(second.size()==1,"editable mask restored at original index");
        timeline.remember(beforeApply.points,false,beforeApply.mask,-1);
        SnipTimeline.State canceled=timeline.cancelLast();
        check(canceled!=null&&timeline.redoCount()==0,"palm gesture rollback cannot be redone");
        // Reopening an applied polygon and moving an old vertex is one undoable edit.
        SnipTimeline editTimeline=new SnipTimeline();SnipMask preEdit=new SnipMask();preEdit.add(op);
        editTimeline.remember(new ArrayList<float[]>(),false,preEdit,-1,null);
        SnipMask.Operation old=preEdit.removeAt(0);
        ArrayList<float[]> edited=polygon();edited.get(1)[0]=.4f;
        editTimeline.remember(edited,true,preEdit,0,old);
        edited.get(1)[0]=.47f;
        SnipTimeline.State editBefore=editTimeline.undo(edited,true,preEdit,0,old);
        check(editBefore.original!=null&&editBefore.original.xy[0]==.1f,"original snip preserved in edit history");
        check(editBefore.points.get(1)[0]==.4f,"undo applied snip vertex edit");
        SnipTimeline.State editAfter=editTimeline.redo(editBefore.points,true,editBefore.mask,0,editBefore.original);
        check(editAfter.points.get(1)[0]==.47f,"redo edited applied snip vertex");
        timeline.clear();check(timeline.undoCount()==0,"new project clears edit history");
        System.out.println("PASS: stylus vertex undo/redo; applied mask undo/redo; canceled gesture rollback; edit-at-index");
    }
}

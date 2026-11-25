package com.halftonestudio;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Non-destructive edit history for polygon vertices AND applied snip operations.
 * No Android dependency, so stylus cancellation and undo/redo are host-testable.
 * The owner records BEFORE any mutation, then optionally rolls the mutation back
 * when Android reports a canceled pointer or an interrupted drag.
 */
public final class SnipTimeline {
    public static final int MAX_UNDO=100;
    public static final class State {
        public final ArrayList<float[]> points=new ArrayList<>();
        public final SnipMask mask;
        public final boolean closed;
        public final int editingIndex;
        public final SnipMask.Operation original;
        public State(List<float[]> draft,boolean closed,SnipMask mask,int editingIndex){
            this(draft,closed,mask,editingIndex,null);
        }
        public State(List<float[]> draft,boolean closed,SnipMask mask,int editingIndex,SnipMask.Operation original){
            for(float[] p:draft)points.add(p.clone());
            this.closed=closed;
            this.mask=mask==null?new SnipMask():mask.copy();
            this.editingIndex=editingIndex;
            this.original=original==null?null:original.copy();
        }
    }
    private final ArrayDeque<State> backwards=new ArrayDeque<>(),forwards=new ArrayDeque<>();
    public void clear(){backwards.clear();forwards.clear();}
    public int undoCount(){return backwards.size();}
    public int redoCount(){return forwards.size();}
    public void remember(List<float[]> draft,boolean closed,SnipMask mask,int editingIndex){
        remember(draft,closed,mask,editingIndex,null);
    }
    public void remember(List<float[]> draft,boolean closed,SnipMask mask,int editingIndex,SnipMask.Operation original){
        backwards.push(new State(draft,closed,mask,editingIndex,original));
        if(backwards.size()>MAX_UNDO)backwards.removeLast();
        forwards.clear();
    }
    public State undo(List<float[]> draft,boolean closed,SnipMask mask,int editingIndex){
        return undo(draft,closed,mask,editingIndex,null);
    }
    public State undo(List<float[]> draft,boolean closed,SnipMask mask,int editingIndex,SnipMask.Operation original){
        if(backwards.isEmpty())return null;
        forwards.push(new State(draft,closed,mask,editingIndex,original));
        return backwards.pop();
    }
    public State redo(List<float[]> draft,boolean closed,SnipMask mask,int editingIndex){
        return redo(draft,closed,mask,editingIndex,null);
    }
    public State redo(List<float[]> draft,boolean closed,SnipMask mask,int editingIndex,SnipMask.Operation original){
        if(forwards.isEmpty())return null;
        backwards.push(new State(draft,closed,mask,editingIndex,original));
        return forwards.pop();
    }
    /** Cancel an uncommitted edit without making that palm touch redoable. */
    public State cancelLast(){
        if(backwards.isEmpty())return null;
        forwards.clear();return backwards.pop();
    }
}

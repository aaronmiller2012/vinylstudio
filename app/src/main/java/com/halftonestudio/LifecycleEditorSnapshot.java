package com.halftonestudio;

import java.util.function.Supplier;

/** One main-thread serialization per unchanged background lifecycle state. */
final class LifecycleEditorSnapshot {
    static final class Value {
        final String editor,draft;
        Value(String editor,String draft){this.editor=editor;this.draft=draft;}
    }
    private Value cached;
    private long revision,photoRevision;
    Value capture(long revision,long photoRevision,Supplier<String> editor,Supplier<String> draft){
        if(cached==null||this.revision!=revision||this.photoRevision!=photoRevision){
            cached=new Value(editor.get(),draft.get());this.revision=revision;this.photoRevision=photoRevision;
        }
        return cached;
    }
    void invalidate(){cached=null;}
}

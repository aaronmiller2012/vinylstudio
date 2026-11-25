package com.halftonestudio;

/**
 * UI-owned project edit and load state. No Android dependencies, bitmap ownership or
 * mutable settings objects: stale background operations check monotonic generations.
 * This class does not persist data; StudioProjectLibrary is the sole durable store.
 */
final class StudioProjectSessionState {
    private long revision;
    private long loadGeneration;
    private boolean dirty;
    private boolean saving;

    synchronized void markEdited() { revision++; dirty=true; }
    synchronized long revision() { return revision; }
    synchronized boolean isDirty() { return dirty; }
    synchronized void setDirty(boolean value) { dirty=value; }
    synchronized boolean isSaving() { return saving; }

    /** Returns false on a duplicate Save request. */
    synchronized boolean beginSave() {
        if(saving) return false;
        saving=true;
        return true;
    }

    /** A save of an earlier revision must never clear newer, unsaved edits. */
    synchronized boolean completeSave(long savedRevision, boolean samePhoto) {
        saving=false;
        if(samePhoto && savedRevision==revision)dirty=false;
        return samePhoto && !dirty;
    }

    synchronized void failSave() { saving=false; }

    /** A new document invalidates all older asynchronous project-open callbacks. */
    synchronized void closeProject() {
        loadGeneration++;
        revision++;
        dirty=false;
    }

    /** Starting a photo import invalidates older project open results. */
    synchronized void invalidateLoads() { loadGeneration++; }

    synchronized long beginLoad() { return ++loadGeneration; }
    synchronized boolean isCurrentLoad(long token) { return token==loadGeneration; }
}

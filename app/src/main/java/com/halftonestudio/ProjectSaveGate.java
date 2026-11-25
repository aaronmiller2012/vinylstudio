package com.halftonestudio;

/** Process-wide guard: a queued archive snapshot must not be invalidated by a
 * second Activity while the first Activity's disk worker is still running. */
final class ProjectSaveGate {
    private static boolean saving;
    private ProjectSaveGate(){}
    static synchronized boolean begin(){if(saving)return false;saving=true;return true;}
    static synchronized void end(){saving=false;}
    static synchronized boolean busy(){return saving;}
}

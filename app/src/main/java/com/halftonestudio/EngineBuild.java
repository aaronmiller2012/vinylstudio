package com.halftonestudio;

/**
 * Projects store the photo and settings; artwork is regenerated on open by the
 * current style engines. Each save records the engine build that produced it, so a
 * newer build can tell the user when a project will be redrawn by changed code
 * before they cut material from it. CURRENT must equal app versionCode
 * (enforced by tests/check-v3-product553.py).
 */
final class EngineBuild {
    static final int CURRENT=567;
    static final String KEY="engineBuild";
    private EngineBuild() { }

    /** Normalizes a stored value; 0 means saved before build 553 recorded the engine. */
    static int normalize(int stored){return Math.max(0,stored);}

    static boolean regeneratedByNewerEngine(int savedBuild){return savedBuild<CURRENT;}

    static String notice(int savedBuild){
        String origin=savedBuild<=0?"an earlier VinylStudio build":"VinylStudio build "+savedBuild;
        return "This project was saved by "+origin+". Artwork is redrawn by the current style engine (build "+
            CURRENT+"), so shapes, colors or cut paths can differ from the original. Compare it with your earlier "+
            "export before cutting vinyl. Saving the project records the current build.";
    }

    /** Acknowledgement token: one notice per project per installed engine build. */
    static String ackToken(String projectKey){return CURRENT+":"+(projectKey==null?"external":projectKey);}
}

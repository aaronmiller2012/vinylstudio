package com.halftonestudio;

import java.util.Locale;

/** D9: how long the canvas status line stays visible for a message. Pure, host-testable.
 *  Work in progress (ending in an ellipsis) stays until replaced; problems stay long enough to
 *  read and act on (tapping opens Cut Check); routine completions get out of the way. */
final class StatusLinePolicy {
    private StatusLinePolicy(){}
    enum Kind { HIDDEN, PROGRESS, PROBLEM, INFO }
    static final long PROBLEM_MS=12_000L, INFO_MS=4_000L;

    static Kind classify(CharSequence text){
        if(text==null)return Kind.HIDDEN;
        String t=text.toString().trim();
        if(t.isEmpty())return Kind.HIDDEN;
        if(t.endsWith("…")||t.endsWith("..."))return Kind.PROGRESS;
        String l=t.toLowerCase(Locale.ROOT);
        String[] problem={"fail","unavailable","not enough","needs attention","need attention","cannot","can't",
            "invalid","error","exceeds","too many","disabled","risk","check registration","unsafe","not cut-qualified",
            "not cut qualified","unable","not saved","insufficient","rejected","missing","memory",
            // M4: cut prep changed the artwork; long enough to read, tap opens Cut Check.
            "cut prep filled","cut prep removed"};
        for(String p:problem)if(l.contains(p))return Kind.PROBLEM;
        return Kind.INFO;
    }
    /** Milliseconds until auto-hide; 0 means stay until replaced; -1 means hide now. */
    static long displayMs(CharSequence text){
        switch(classify(text)){
            case HIDDEN: return -1;
            case PROGRESS: return 0;
            case PROBLEM: return PROBLEM_MS;
            default: return INFO_MS;
        }
    }
}

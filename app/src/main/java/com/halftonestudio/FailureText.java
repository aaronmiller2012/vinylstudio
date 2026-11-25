package com.halftonestudio;

/** User-facing failure text (D9). Technical detail belongs in the log, never on screen:
 *  file paths, Java class names, stack fragments and programming-error messages are replaced
 *  by the caller's plain-language fallback. */
final class FailureText {
    private FailureText(){}
    /** A stack frame ("at com.x.Y.method(Y.java:12)"), not ordinary English such as "at this spacing". */
    private static final java.util.regex.Pattern STACK_FRAME=
        java.util.regex.Pattern.compile("\\bat [A-Za-z_$][\\w$]*(\\.[\\w$<>]+)+\\(");
    static String describe(Throwable error,String fallback){
        if(error==null)return fallback;
        if(error instanceof java.io.FileNotFoundException||error instanceof SecurityException)
            return "Document is unavailable or access was denied";
        if(error instanceof OutOfMemoryError)return "Not enough device memory";
        // Programming errors carry developer text (e.g. Java 17 helpful NullPointerException
        // messages naming classes and variables); they are never actionable for a user.
        if(error instanceof NullPointerException||error instanceof ClassCastException||
           error instanceof IndexOutOfBoundsException||error instanceof ArithmeticException||
           error instanceof NegativeArraySizeException||error instanceof ArrayStoreException||
           error instanceof UnsupportedOperationException||error instanceof StackOverflowError)return fallback;
        String detail=error.getMessage();
        if(detail==null||detail.trim().isEmpty()||"null".equalsIgnoreCase(detail.trim()))return fallback;
        String clean=detail.trim().replace('\r',' ').replace('\n',' ');
        if(clean.contains("/data/")||clean.contains("/storage/")||clean.contains("/sdcard")||clean.contains("content://")||
           clean.contains("java.")||clean.contains("android.")||clean.contains("com.halftonestudio")||
           clean.contains("Exception")||STACK_FRAME.matcher(clean).find()||clean.contains("because \"")||
           clean.contains("ENOSPC")||clean.contains("errno"))return fallback;
        if(clean.length()>130){
            int end=clean.lastIndexOf(' ',127);
            if(end<72)end=127;
            clean=clean.substring(0,end).trim()+"…";
        }
        return clean;
    }
}

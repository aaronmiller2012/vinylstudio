package android.util;
public final class Log {
    public static int e(String tag,String msg,Throwable error){throw new AssertionError(msg,error);}
}

package com.halftonestudio;
/** Small, persistent-across-recreation state for Android's document picker result. */
final class PendingLibraryExport {
    private String id;
    void set(String next){id=next;}
    String save(){return id;}
    void restore(String saved){id=saved;}
    void clear(){id=null;}
    String consume(){String value=id;id=null;return value;}
}

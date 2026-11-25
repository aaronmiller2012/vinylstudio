package com.halftonestudio;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * D14: a library save that completes while the Activity is being recreated (dark mode,
 * locale, font scale) used to lose the new project's id: the id was only stored by a
 * UI callback that the destroyed Activity never ran, so the next Save created a
 * duplicate project.
 *
 * The IO thread now records a durable handoff right after the save is published. A
 * (re)created Activity adopts it only when it still shows that exact document: its
 * autosaved photo token equals the saved one, and its library id is still the one the
 * save started from. Anything else is stale and discarded. Pure Java; tested on the host.
 */
final class LibraryIdHandoff {
    /** Minimal key/value view of the editor's SharedPreferences. */
    interface Store {
        String get(String key);
        /** Durable write of all values together (commit, not apply). */
        void putAll(Map<String,String> values);
        void removeAll(String... keys);
    }
    static final String ID="handoffLibraryId",NAME="handoffLibraryName",TOKEN="handoffPhotoToken",PREVIOUS="handoffPreviousId";
    private static final String NONE="";

    static final class Adopted {
        final String id,name;
        Adopted(String id,String name){this.id=id;this.name=name;}
    }
    private LibraryIdHandoff(){}

    /** IO thread, after the library revision is published. */
    static void record(Store store,String id,String name,String photoToken,String previousId){
        if(id==null||id.isEmpty()||!PhotoPairToken.valid(photoToken))return;
        Map<String,String> values=new LinkedHashMap<>();
        values.put(ID,id);values.put(NAME,name==null?"":name);values.put(TOKEN,photoToken);
        values.put(PREVIOUS,previousId==null?NONE:previousId);
        store.putAll(values);
    }
    /** The UI delivered the result itself; nothing to hand off. */
    static void clear(Store store){store.removeAll(ID,NAME,TOKEN,PREVIOUS);}

    /**
     * @param currentId the Activity's library id (null for a never-saved project)
     * @param currentPhotoToken the token of the photo the Activity is showing
     * @return the id to adopt, or null. A handoff that does not match is removed.
     */
    static Adopted adopt(Store store,String currentId,String currentPhotoToken){
        String id=store.get(ID);
        if(id==null||id.isEmpty())return null;
        String token=store.get(TOKEN),previous=store.get(PREVIOUS),name=store.get(NAME);
        boolean sameDocument=token!=null&&token.equals(currentPhotoToken);
        boolean unchangedId=(previous==null?NONE:previous).equals(currentId==null?NONE:currentId);
        clear(store);
        if(!sameDocument||!unchangedId)return null;
        return new Adopted(id,name==null||name.isEmpty()?"Photo project":name);
    }
}

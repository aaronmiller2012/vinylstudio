package com.halftonestudio;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Restores the autosaved editor state without ever discarding it silently.
 *
 * If the saved text cannot be parsed (a kill during a write, a restore from an
 * unsupported newer schema, a migration bug), the exact original text is copied to
 * {@code <key>.corrupt} with a time and reason BEFORE the caller falls back to
 * defaults. The caller's next autosave may then overwrite {@code <key>} safely.
 * One earlier, different quarantine is kept as {@code <key>.corrupt.previous}.
 * Pure Java: the Android preference store is adapted through {@link Store}.
 */
final class EditorAutosaveRecovery {
    private EditorAutosaveRecovery(){}

    interface Store {
        String get(String key);
        /** Applies all changes together; a null value removes the key. */
        void putAll(Map<String,String> changes);
    }
    interface Parser<T> { T parse(String raw) throws Exception; }

    static final class Outcome<T> {
        final T value;final boolean quarantined;final String reason;
        private Outcome(T value,boolean quarantined,String reason){this.value=value;this.quarantined=quarantined;this.reason=reason;}
        boolean restored(){return value!=null;}
    }

    static String quarantineKey(String key){return key+".corrupt";}

    static <T> Outcome<T> restore(Store store,String key,Parser<T> parser,long nowMillis){
        String raw=store.get(key);
        if(raw==null)return new Outcome<>(null,false,null);
        try{
            T value=parser.parse(raw);
            if(value!=null)return new Outcome<>(value,false,null);
            return quarantine(store,key,raw,"Saved editor state was empty after parsing",nowMillis);
        }catch(Exception|StackOverflowError failure){
            String reason=failure.getClass().getSimpleName()+(failure.getMessage()==null?"":": "+failure.getMessage());
            return quarantine(store,key,raw,reason,nowMillis);
        }
    }

    private static <T> Outcome<T> quarantine(Store store,String key,String raw,String reason,long nowMillis){
        String q=quarantineKey(key);
        String existing=store.get(q);
        if(raw.equals(existing))return new Outcome<>(null,true,reason); // already preserved on an earlier launch
        Map<String,String> changes=new LinkedHashMap<>();
        if(existing!=null){
            changes.put(q+".previous",existing);
            changes.put(q+".previous.time",store.get(q+".time"));
            changes.put(q+".previous.reason",store.get(q+".reason"));
        }
        changes.put(q,raw);
        changes.put(q+".time",Long.toString(nowMillis));
        changes.put(q+".reason",reason.length()>300?reason.substring(0,300):reason);
        store.putAll(changes);
        return new Outcome<>(null,true,reason);
    }
}

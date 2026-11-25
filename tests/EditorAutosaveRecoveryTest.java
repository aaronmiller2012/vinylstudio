package com.halftonestudio;

import java.util.HashMap;
import java.util.Map;

/**
 * D1: an unreadable autosaved editor state must never be silently discarded.
 * EditorAutosaveRecovery is the policy StudioActivity uses when restoring the "editor"
 * preference. A parse failure must copy the exact original text to a quarantine key
 * BEFORE the editor falls back to defaults (whose next autosave overwrites "editor").
 */
public final class EditorAutosaveRecoveryTest {
    static void ok(boolean v,String m){if(!v)throw new AssertionError(m);}

    static final class MapStore implements EditorAutosaveRecovery.Store {
        final Map<String,String> values=new HashMap<>();int writes;
        public String get(String key){return values.get(key);}
        public void putAll(Map<String,String> changes){writes++;for(Map.Entry<String,String> e:changes.entrySet())
            if(e.getValue()==null)values.remove(e.getKey());else values.put(e.getKey(),e.getValue());}
    }
    /** Stand-in for StudioActivity's JSONObject + fromJson parse: accepts only {...} text. */
    static String parse(String raw){
        String t=raw.trim();
        if(!t.startsWith("{")||!t.endsWith("}"))throw new IllegalArgumentException("Unterminated object at character "+t.length());
        if(t.contains("\"schema\":999"))throw new IllegalStateException("Unsupported migration");
        return t;
    }

    public static void main(String[] args){
        // Valid state: parsed, nothing quarantined, no extra writes.
        MapStore store=new MapStore();
        store.values.put("editor","{\"mode\":1}");
        EditorAutosaveRecovery.Outcome<String> good=EditorAutosaveRecovery.restore(store,"editor",EditorAutosaveRecoveryTest::parse,1000L);
        ok(good.restored()&&"{\"mode\":1}".equals(good.value),"valid editor state not restored");
        ok(store.writes==0&&store.get("editor.corrupt")==null,"valid state must not be quarantined");

        // Missing state: nothing to restore, nothing quarantined.
        MapStore empty=new MapStore();
        EditorAutosaveRecovery.Outcome<String> none=EditorAutosaveRecovery.restore(empty,"editor",EditorAutosaveRecoveryTest::parse,1000L);
        ok(!none.restored()&&!none.quarantined&&empty.writes==0,"absent state mishandled");

        // Truncated state (process killed mid-write): exact text preserved before fallback.
        String truncated="{\"mode\":1,\"layers\":[{\"name\":\"Cyan\",\"color\":-16";
        store.values.put("editor",truncated);
        EditorAutosaveRecovery.Outcome<String> bad=EditorAutosaveRecovery.restore(store,"editor",EditorAutosaveRecoveryTest::parse,2000L);
        ok(!bad.restored()&&bad.quarantined,"corrupt state must be reported as quarantined");
        ok(truncated.equals(store.get("editor.corrupt")),"quarantine must hold the exact original text");
        ok("2000".equals(store.get("editor.corrupt.time")),"quarantine time missing");
        ok(store.get("editor.corrupt.reason")!=null&&store.get("editor.corrupt.reason").contains("Unterminated"),"reason missing");
        ok(truncated.equals(store.get("editor")),"recovery itself must not delete or rewrite the original key");
        ok(bad.reason!=null&&bad.reason.contains("Unterminated"),"outcome reason missing");

        // A second, different corruption keeps the earlier copy too.
        String newer="{\"schema\":999}";
        store.values.put("editor",newer);
        EditorAutosaveRecovery.Outcome<String> migration=EditorAutosaveRecovery.restore(store,"editor",EditorAutosaveRecoveryTest::parse,3000L);
        ok(migration.quarantined,"runtime migration failure must also be quarantined");
        ok(newer.equals(store.get("editor.corrupt")),"latest quarantine wrong");
        ok(truncated.equals(store.get("editor.corrupt.previous")),"earlier quarantine was overwritten");

        // The same corrupt text seen again (repeated launches) does not push out the earlier copy.
        EditorAutosaveRecovery.restore(store,"editor",EditorAutosaveRecoveryTest::parse,4000L);
        ok(truncated.equals(store.get("editor.corrupt.previous")),"repeat launch rotated away the earlier quarantine");
        ok("3000".equals(store.get("editor.corrupt.time")),"repeat launch should keep the first quarantine time");

        // Oversized text is still preserved intact (no truncation of user data).
        StringBuilder big=new StringBuilder("{\"snips\":\"");for(int i=0;i<200_000;i++)big.append('x');
        MapStore large=new MapStore();large.values.put("editor",big.toString());
        EditorAutosaveRecovery.restore(large,"editor",EditorAutosaveRecoveryTest::parse,5000L);
        ok(big.toString().equals(large.get("editor.corrupt")),"large corrupt state truncated");
        System.out.println("PASS editor autosave recovery: corrupt state quarantined verbatim before fallback");
    }
}

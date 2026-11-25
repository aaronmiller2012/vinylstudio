package com.halftonestudio;

import java.util.*;

/** D14: a save finishing during Activity recreation must not make the next Save a duplicate. */
public final class V3M3LibraryIdHandoffTest {
    private static void yes(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    static final class Prefs implements LibraryIdHandoff.Store {
        final Map<String,String> map=new HashMap<>();
        @Override public String get(String key){return map.get(key);}
        @Override public void putAll(Map<String,String> values){map.putAll(values);}
        @Override public void removeAll(String... keys){for(String k:keys)map.remove(k);}
    }
    public static void main(String[] args){
        String token="proc1234-r7",otherToken="proc1234-r9";
        String newId="0b6e9a4c-1f6b-4d1e-9c55-1c1c2f3a4b5c",originalId="7d4c2b1a-0000-4000-8000-000000000001";

        // 1. First save of a new project; Activity recreated before the UI callback ran.
        Prefs prefs=new Prefs();
        LibraryIdHandoff.record(prefs,newId,"Portrait",token,null);
        LibraryIdHandoff.Adopted adopted=LibraryIdHandoff.adopt(prefs,null,token);
        yes(adopted!=null&&newId.equals(adopted.id)&&"Portrait".equals(adopted.name),"recreated Activity lost the new id");
        yes(prefs.map.isEmpty(),"handoff must be consumed");
        yes(LibraryIdHandoff.adopt(prefs,null,token)==null,"handoff adopted twice");

        // 2. The user imported another photo meanwhile: never label it with the saved id.
        prefs=new Prefs();
        LibraryIdHandoff.record(prefs,newId,"Portrait",token,null);
        yes(LibraryIdHandoff.adopt(prefs,null,otherToken)==null,"different document adopted the saved id");
        yes(prefs.map.isEmpty(),"stale handoff kept");

        // 3. Save As while editing an existing project: adopt the new copy's id, so the
        //    next Save updates the copy and never overwrites the original.
        prefs=new Prefs();
        LibraryIdHandoff.record(prefs,newId,"Portrait copy",token,originalId);
        adopted=LibraryIdHandoff.adopt(prefs,originalId,token);
        yes(adopted!=null&&newId.equals(adopted.id),"Save As copy not adopted");

        // 4. The editor already moved to a different library project: discard.
        prefs=new Prefs();
        LibraryIdHandoff.record(prefs,newId,"Portrait",token,null);
        yes(LibraryIdHandoff.adopt(prefs,originalId,token)==null,"handoff overrode a different project");

        // 5. The UI callback ran normally: nothing left to adopt.
        prefs=new Prefs();
        LibraryIdHandoff.record(prefs,newId,"Portrait",token,null);
        LibraryIdHandoff.clear(prefs);
        yes(LibraryIdHandoff.adopt(prefs,null,token)==null&&prefs.map.isEmpty(),"delivered save left a handoff");

        // 6. Invalid tokens or ids are never recorded; a blank name gets a label.
        prefs=new Prefs();
        LibraryIdHandoff.record(prefs,newId,"x","",null);
        LibraryIdHandoff.record(prefs,"", "x",token,null);
        yes(prefs.map.isEmpty(),"invalid handoff recorded");
        LibraryIdHandoff.record(prefs,newId,"",token,null);
        yes("Photo project".equals(LibraryIdHandoff.adopt(prefs,null,token).name),"blank name not labelled");
        System.out.println("PASS D14 library id survives recreation; never adopted by another document or project");
    }
}

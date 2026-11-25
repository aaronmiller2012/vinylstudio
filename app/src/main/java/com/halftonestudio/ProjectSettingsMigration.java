package com.halftonestudio;

import java.util.HashMap;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/** Android JSONObject bridge for the JVM-tested schema transition rules. */
final class ProjectSettingsMigration {
    static final int CURRENT_SCHEMA=ProjectSettingsSchema.CURRENT;
    private ProjectSettingsMigration() { }
    static int readSchema(JSONObject saved) {return Math.max(0,saved.optInt("schema",0));}
    static boolean isFuture(JSONObject saved) {return readSchema(saved)>CURRENT_SCHEMA;}
    static JSONObject migrate(JSONObject original) throws JSONException {
        // Deep-copy first: never mutate the library's original document.
        JSONObject result=new JSONObject(original.toString());
        Map<String,Object> semanticKeys=new HashMap<>();
        semanticKeys.put("schema",readSchema(original));
        if(result.has("minimumMm"))semanticKeys.put("minimumMm",result.get("minimumMm"));
        if(result.has("offsetCellMode"))semanticKeys.put("offsetCellMode",result.optBoolean("offsetCellMode",false));
        if(result.has("layeredSolidBase"))semanticKeys.put("layeredSolidBase",result.optBoolean("layeredSolidBase",false));
        if(result.has("layeredExtraMotif"))semanticKeys.put("layeredExtraMotif",result.optBoolean("layeredExtraMotif",false));
        for(String key:new String[]{"stipplePointDensity","stippleDotSize","masterDensity"}){
            if(result.has(key))semanticKeys.put(key,result.optDouble(key,1));
        }
        for(Map.Entry<String,Object> entry:ProjectSettingsSchema.migrate(semanticKeys).entrySet())
            result.put(entry.getKey(),entry.getValue());
        return result;
    }
}

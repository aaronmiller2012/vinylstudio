package com.halftonestudio;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Pure, fail-closed decoding preflight for untrusted project settings.
 * This runs on the disk worker without touching the active Activity. The
 * accepted state is deserialized on the main thread only after load gates.
 */
final class ProjectOpenPreflight {
    private ProjectOpenPreflight() { }

    static void validate(JSONObject original) throws JSONException {
        if(original==null)throw new JSONException("Missing project settings");
        JSONObject json=ProjectSettingsMigration.migrate(original);
        JSONArray layers=json.getJSONArray("layers");
        if(layers.length()<2||layers.length()>8)
            throw new JSONException("Layer count must be 2–8");
        for(int i=0;i<layers.length();i++){
            JSONObject layer=layers.getJSONObject(i);
            // Runtime parser reads optional numeric settings; corrupted nonfinite
            // values are bounded by normalizeLoadedLayer, not trusted as geometry.
            if(layer==null)throw new JSONException("Missing layer "+i);
        }
        JSONArray snips=json.optJSONArray("snips");
        if(snips!=null){
            if(snips.length()>SnipMask.MAX_OPERATIONS)
                throw new JSONException("Too many snips");
            for(int i=0;i<snips.length();i++){
                JSONObject item=snips.getJSONObject(i);
                JSONArray coordinates=item.getJSONArray("points");
                if(coordinates.length()<6||coordinates.length()>2*SnipMask.MAX_VERTICES||
                        (coordinates.length()&1)!=0)
                    throw new JSONException("Invalid snip vertex count");
                float[] points=new float[coordinates.length()];
                for(int j=0;j<points.length;j++)points[j]=(float)coordinates.getDouble(j);
                try{new SnipMask.Operation(item.getInt("kind"),points);}
                catch(IllegalArgumentException bad){
                    throw new JSONException("Invalid snip: "+bad.getMessage());
                }
            }
        }
        JSONArray marks=json.optJSONArray("marks");
        if(marks!=null&&marks.length()==4){
            for(int i=0;i<4;i++){
                JSONArray pair=marks.getJSONArray(i);
                if(pair.length()<2)throw new JSONException("Incomplete registration mark");
                double x=pair.getDouble(0),y=pair.getDouble(1);
                if(!Double.isFinite(x)||!Double.isFinite(y))
                    throw new JSONException("Nonfinite registration mark");
                // Legacy outlying finite marks remain loadable for explicit repair;
                // the final-sheet geometry validator still blocks unsafe export.
            }
        }
    }
}

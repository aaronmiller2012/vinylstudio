package com.halftonestudio;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure JVM schema rules. Unknown keys and saved values survive each transition. */
final class ProjectSettingsSchema {
    static final int CURRENT=29;
    static final float OLD_DEFAULT_MINIMUM_MM=.80f;
    private ProjectSettingsSchema() { }
    static int version(Map<String,?> fields){
        Object value=fields.get("schema");
        return value instanceof Number?Math.max(0,((Number)value).intValue()):0;
    }
    static boolean isNewer(Map<String,?> fields){return version(fields)>CURRENT;}
    static Map<String,Object> migrate(Map<String,?> source){
        Map<String,Object> out=new LinkedHashMap<>();out.putAll(source);
        int version=version(source);
        if(version>CURRENT)return out; // Cannot safely interpret newer meaning.
        if(version<18){
            // v0..17 -> v18: omitted physical cut floor retains historical .8mm.
            if(!out.containsKey("minimumMm"))out.put("minimumMm",OLD_DEFAULT_MINIMUM_MM);
            version=18;
        }
        if(version<19){
            // v18 -> v19: RC104 changed default to .254mm ONLY on new designs.
            // Persist old default explicitly instead of changing existing geometry.
            if(!out.containsKey("minimumMm"))out.put("minimumMm",OLD_DEFAULT_MINIMUM_MM);
            version=19;
        }
        if(version<20){
            // v19 -> v20: specialty geometry now honors the 0.254mm user
            // minimum and Banknote variants 4..8 restore as their own IDs.
            // Never rewrite a user's explicitly saved width or variant.
            if(!out.containsKey("minimumMm"))out.put("minimumMm",OLD_DEFAULT_MINIMUM_MM);
            version=20;
        }
        if(version<21){
            if(!out.containsKey("minimumMm"))out.put("minimumMm",OLD_DEFAULT_MINIMUM_MM);
            // v20 -> v21: stacked high-detail is opt-in for new selections only.
            // Existing projects retain exactly their sliced geometry by default.
            if(!out.containsKey("layeredSolidBase"))out.put("layeredSolidBase",false);
            if(!out.containsKey("layeredExtraMotif"))out.put("layeredExtraMotif",false);
            version=21;
        }
        if(version<22){
            if(!out.containsKey("minimumMm"))out.put("minimumMm",OLD_DEFAULT_MINIMUM_MM);
            // New Offset Cell is a separate opt-in style. All legacy projects
            // keep their original aligned cells by default.
            if(!out.containsKey("offsetCellMode"))out.put("offsetCellMode",false);
            version=22;
        }
        if(version<23){
            if(!out.containsKey("minimumMm"))out.put("minimumMm",OLD_DEFAULT_MINIMUM_MM);
            // Bottle Cap is a separate opt-in style; all old projects remain unchanged.
            if(!out.containsKey("bottleCapMode"))out.put("bottleCapMode",false);
            version=23;
        }
        if(version<24){
            if(!out.containsKey("minimumMm"))out.put("minimumMm",OLD_DEFAULT_MINIMUM_MM);
            // Fish Scale is new and opt-in; all older projects retain their style.
            if(!out.containsKey("fishScaleMode"))out.put("fishScaleMode",false);
            version=24;
        }
        if(version<25){
            if(!out.containsKey("minimumMm"))out.put("minimumMm",OLD_DEFAULT_MINIMUM_MM);
            // Tessellation Motif is a new style, old projects retain their own styles.
            if(!out.containsKey("newStyleTessMotif"))out.put("newStyleTessMotif",0);
            version=25;
        }
        if(version<26){
            if(!out.containsKey("minimumMm"))out.put("minimumMm",OLD_DEFAULT_MINIMUM_MM);
            // Tessellation style 10 now exports photo-colored tiles only.
            // Preserve newStyleTessMotif as an ignored legacy key, not an active
            // control; do not remap style IDs or alter any saved palette.
            version=26;
        }
        if(version<27){
            if(!out.containsKey("minimumMm"))out.put("minimumMm",OLD_DEFAULT_MINIMUM_MM);
            // Offset Cell's L1 is a permanent silhouette. Its former dedicated
            // motif sheet becomes a normal photographic color slot. No other
            // cell style is changed; keep unrelated unknown/saved keys.
            if(Boolean.TRUE.equals(out.get("offsetCellMode"))){
                out.put("layeredSolidBase",true);
                out.put("layeredExtraMotif",false);
            }
            version=27;
        }
        if(version<28){
            if(!out.containsKey("minimumMm"))out.put("minimumMm",OLD_DEFAULT_MINIMUM_MM);
            // v27 -> v28: percentage sliders stop at 100%.
            // Archive legacy over-range values under nonoperative keys so users
            // can recover them if opening an older build; current geometry uses
            // bounded values to prevent fineCompletion's miniature filler dots.
            clampPercentage(out,"stipplePointDensity","legacyPointDensityBeforeCap",.50f);
            clampPercentage(out,"stippleDotSize","legacyDotSizeBeforeCap",.60f);
            clampPercentage(out,"masterDensity","legacyDotCoverageBeforeCap",.35f);
            version=28;
        }
        if(version<29){
            if(!out.containsKey("minimumMm"))out.put("minimumMm",OLD_DEFAULT_MINIMUM_MM);
            // RC136 retires the original Layered Cell portrait's dedicated
            // fine-detail motif sheet. Bottle Cap and Fish Scale keep their
            // separate highlight/accent sheets. Preserve the saved key but
            // disable it for plain/offset cell portraits.
            boolean bottle=Boolean.TRUE.equals(out.get("bottleCapMode"));
            boolean fish=Boolean.TRUE.equals(out.get("fishScaleMode"));
            if(!bottle&&!fish)out.put("layeredExtraMotif",false);
            version=29;
        }
        out.put("schema",CURRENT);
        return out;
    }
    private static void clampPercentage(Map<String,Object> fields,String key,String backup,float min){
        Object value=fields.get(key);
        if(!(value instanceof Number))return;
        float v=((Number)value).floatValue();
        if(!Float.isFinite(v))return;
        if(v>1f&&!fields.containsKey(backup))fields.put(backup,value);
        fields.put(key,Math.max(min,Math.min(1f,v)));
    }
}

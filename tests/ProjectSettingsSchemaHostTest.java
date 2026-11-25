package com.halftonestudio;
import java.util.*;

public final class ProjectSettingsSchemaHostTest {
    private static void eq(Object a,Object b,String issue){if(!Objects.equals(a,b))throw new AssertionError(issue+": "+a+" != "+b);}
    private static Map<String,Object> save(int v,Object min){
        Map<String,Object> o=new LinkedHashMap<>();
        if(v>=0)o.put("schema",v);
        if(min!=null)o.put("minimumMm",min);
        o.put("unknownFutureControl", "keep me");
        o.put("newStyleId",9);
        o.put("layers", Arrays.asList("yellow","cyan","magenta","black"));
        return o;
    }
    public static void main(String[] args){
        for(int v:new int[]{-1,0,12,17,18,19,20,21,22,23,24,25,26,27,28,29,35}){
            for(Object width:new Object[]{null,.46f}){
                Map<String,Object> project=save(v,width),migrated=ProjectSettingsSchema.migrate(project);
                int original=v<0?0:v;
                eq(migrated.get("schema"),Math.max(ProjectSettingsSchema.CURRENT,original),"schema not preserved");
                eq(migrated.get("unknownFutureControl"),"keep me","unknown key lost");
                if(original<21){
                    eq(migrated.get("layeredSolidBase"),false,"Legacy project unexpectedly stacked");
                    eq(migrated.get("layeredExtraMotif"),false,"Legacy project unexpectedly got detail sheet");
                }
                if(original<22)eq(migrated.get("offsetCellMode"),false,"Existing project became Offset Cell");
                if(original<23)eq(migrated.get("bottleCapMode"),false,"Existing project became Bottle Cap");
                if(original<24)eq(migrated.get("fishScaleMode"),false,"Existing project became Fish Scale");
                if(original<25)eq(migrated.get("newStyleTessMotif"),0,"Legacy project field lost");
                if(original==25){
                    Map<String,Object> withLegacy=save(25,.254f);
                    withLegacy.put("newStyleId",VinylStyleSuite.TESSELLATION_MOTIF);
                    withLegacy.put("newStyleTessMotif",5);
                    eq(ProjectSettingsSchema.migrate(withLegacy).get("newStyleTessMotif"),5,
                        "Legacy motif key must survive without applying its geometry");
                }
                eq(migrated.get("newStyleId"),9,"style ID moved");
                eq(migrated.get("layers"),project.get("layers"),"layers changed");
                if(width!=null)eq(migrated.get("minimumMm"),width,"explicit cut width overwritten");
                else if(original<ProjectSettingsSchema.CURRENT)eq(migrated.get("minimumMm"),.8f,"legacy default changed");
                else eq(migrated.containsKey("minimumMm"),false,"future/current defaults assumed");
                eq(ProjectSettingsSchema.migrate(migrated),migrated,"migration not idempotent");
                eq(project.get("schema"),v<0?null:v,"original was mutated");
            }
        }
        Map<String,Object> savedDetail=save(20,.30f);
        savedDetail.put("layeredSolidBase",true);
        savedDetail.put("layeredExtraMotif",true);
        Map<String,Object> detailMigrated=ProjectSettingsSchema.migrate(savedDetail);
        eq(detailMigrated.get("layeredSolidBase"),true,"Explicit solid base lost");
        eq(detailMigrated.get("layeredExtraMotif"),false,"Cell portrait motif sheet not retired");
        Map<String,Object> offset=save(26,.254f);
        offset.put("offsetCellMode",true);
        offset.put("layeredSolidBase",false);
        offset.put("layeredExtraMotif",true);
        Map<String,Object> offsetMigrated=ProjectSettingsSchema.migrate(offset);
        eq(offsetMigrated.get("layeredSolidBase"),true,"Offset silhouette not enforced");
        eq(offsetMigrated.get("layeredExtraMotif"),false,"Offset motif sheet not retired");
        Map<String,Object> original=save(26,.254f);
        original.put("offsetCellMode",false);
        original.put("layeredSolidBase",false);
        original.put("layeredExtraMotif",true);
        Map<String,Object> originalMigrated=ProjectSettingsSchema.migrate(original);
        eq(originalMigrated.get("layeredSolidBase"),false,"Original Layered Cell base changed");
        eq(originalMigrated.get("layeredExtraMotif"),false,"Original Layered Cell motif sheet not retired");
        Map<String,Object> overrange=save(27,.254f);
        overrange.put("stipplePointDensity",2.0d);
        overrange.put("stippleDotSize",1.6d);
        overrange.put("masterDensity",1.65d);
        Map<String,Object> migratedPercent=ProjectSettingsSchema.migrate(overrange);
        eq(migratedPercent.get("stipplePointDensity"),1f,"Point density not capped");
        eq(migratedPercent.get("stippleDotSize"),1f,"Dot size not capped");
        eq(migratedPercent.get("masterDensity"),1f,"Dot coverage not capped");
        eq(migratedPercent.get("legacyPointDensityBeforeCap"),2.0d,"Original point density lost");
        eq(migratedPercent.get("legacyDotSizeBeforeCap"),1.6d,"Original dot size lost");
        eq(migratedPercent.get("legacyDotCoverageBeforeCap"),1.65d,"Original dot coverage lost");
        eq(ProjectSettingsSchema.migrate(migratedPercent),migratedPercent,"Percentage migration not idempotent");
        Map<String,Object> normal=save(27,.254f);
        normal.put("stipplePointDensity",.85d);
        normal.put("stippleDotSize",.9d);
        normal.put("masterDensity",.65d);
        Map<String,Object> unchanged=ProjectSettingsSchema.migrate(normal);
        eq(unchanged.get("stipplePointDensity"),.85f,"Existing point density changed");
        eq(unchanged.get("stippleDotSize"),.9f,"Existing dot size changed");
        eq(unchanged.get("masterDensity"),.65f,"Existing coverage changed");
        eq(unchanged.containsKey("legacyPointDensityBeforeCap"),false,"Invented legacy backup");
        for(int id=0;id<VinylStyleSuite.NAMES.length;id++){
            int restored=VinylStyleSuite.restoredStyleId(id);
            eq(restored,VinylStyleSuite.isRetiredStyleId(id)?-1:id,"historical style ID changed");
        }
        eq(VinylStyleSuite.restoredStyleId(-1),-1,"negative style accepted");
        eq(VinylStyleSuite.restoredStyleId(99),-1,"unshipped style accepted");
        System.out.println("PASS schema v0..29/future, idempotence, unknown values, explicit widths, stable style IDs");
    }
}

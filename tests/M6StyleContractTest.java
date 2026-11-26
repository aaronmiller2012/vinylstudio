package com.halftonestudio;
import java.lang.reflect.*;
import java.util.*;

/** Ensure all suite configuration fields affect an exact geometry cache key, unless the
 * style is intentionally uncacheable. Verify the UI-only group filters preserve identities. */
public final class M6StyleContractTest {
    static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
    static Object different(Field f,Object v)throws Exception{
        Class<?> t=f.getType();
        if(t==int.class)return ((Integer)v)+1;
        if(t==float.class)return ((Float)v)+.123f;
        if(t==boolean.class)return !(Boolean)v;
        if(t==String.class)return v+" different";
        if(t==int[].class)return new int[]{1,2,3};
        if(t==boolean[].class)return new boolean[]{true,false};
        if(t==PopPosterEngine.Options.class)return new PopPosterEngine.Options();
        throw new AssertionError("Unknown config field type: "+f);
    }
    static void cacheKey()throws Exception{
        VinylStyleSuite.Config baseline=new VinylStyleSuite.Config();
        check(NewStyleGeometryMemo.same(baseline,baseline.copy()),"copy cache miss");
        int n=0;
        for(Field f:VinylStyleSuite.Config.class.getFields()){
            if(Modifier.isStatic(f.getModifiers()))continue;
            VinylStyleSuite.Config copy=baseline.copy();
            f.set(copy,different(f,f.get(copy)));
            check(!NewStyleGeometryMemo.same(baseline,copy),"Config field missing from exact key: "+f.getName());n++;
        }
        check(n>=20,"configuration field coverage unexpectedly small");
        VinylStyleSuite.Config c=baseline.copy();c.palette=new int[]{1,2};c.allowed=new boolean[]{true,false};
        VinylStyleSuite.Config d=c.copy();check(NewStyleGeometryMemo.same(c,d),"equal cloned arrays miss cache");
        d.palette[1]=3;check(!NewStyleGeometryMemo.same(c,d),"palette content ignored");d=c.copy();d.allowed[1]=true;check(!NewStyleGeometryMemo.same(c,d),"mask content ignored");
        System.out.println("PASS exact geometry cache key covers "+n+" public Config fields");
    }
    static void families(){
        Set<String> tabs=new HashSet<>(Arrays.asList(StylePickerFamilies.tabs()));
        check(tabs.size()==StylePickerFamilies.tabs().length,"duplicate tab");
        Map<String,Integer> counts=new HashMap<>();
        for(String key:StudioThumbnailRegistry.allKeys()){
            String family=StylePickerFamilies.family(key);
            if(!family.isEmpty()){check(tabs.contains(family),"inaccessible family "+family);counts.merge(family,1,Integer::sum);}
            check(StylePickerFamilies.matches("All","Art techniques",key),"All excluded "+key);
        }
        check(counts.get(StylePickerFamilies.PRISMATIC)==6,"Prismatic group incomplete");
        check(counts.get(StylePickerFamilies.TEXTILE)==4,"Textile group incomplete");
        check(counts.get(StylePickerFamilies.CAMO)==4,"Camo group incomplete");
        check(counts.get(StylePickerFamilies.CRAFT)==10,"Craft group incomplete");
        String key=StudioThumbnailRegistry.newStyle(VinylStyleSuite.PRISMATIC_DIAGONAL);
        check(StylePickerFamilies.matches("Multilayer","Multilayer",key),"legacy category lost");
        check(!StylePickerFamilies.matches(StylePickerFamilies.CAMO,"Multilayer",key),"unrelated filter leak");
        check(StyleDiscovery.decode(key+"\nart.woven_ribbon",StyleDiscovery.LIMIT).equals(Arrays.asList(key,"art.woven_ribbon")),"favorite identity migration");
    }
    public static void main(String[] args)throws Exception{cacheKey();families();System.out.println("PASS stable style family filters and favorite identities");}
}

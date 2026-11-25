package com.halftonestudio;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/**
 * M5 lifecycle: render workers receive Settings.copy() / Config.copy(). A field that copy() omits
 * silently reverts to its default inside the worker, so the preview ignores a user edit.
 * This test perturbs EVERY public field by reflection and requires the copy to carry it, and the
 * copy to be independent (mutating the copy never reaches the original).
 */
public final class M5SnapshotCopyTest {
    static int failures;
    static void check(boolean ok,String msg){if(!ok){failures++;System.out.println("FAIL "+msg);}}

    static Object perturbed(Field f,Object current){
        Class<?> t=f.getType();
        if(t==int.class)return ((Integer)current)+7;
        if(t==float.class)return ((Float)current)+0.37f;
        if(t==boolean.class)return !((Boolean)current);
        if(t==long.class)return ((Long)current)+3L;
        if(t==String.class)return current+"-x";
        if(t==int[].class)return new int[]{1,2,3};
        if(t==boolean[].class)return new boolean[]{true,false,true};
        if(t==float[][].class)return new float[][]{{1f,2f},{3f,4f}};
        if(t==float[].class)return new float[]{9f,8f};
        return null;
    }
    static boolean same(Object a,Object b){
        if(a instanceof float[][])return Arrays.deepEquals((Object[])a,(Object[])b);
        if(a instanceof int[])return Arrays.equals((int[])a,(int[])b);
        if(a instanceof float[])return Arrays.equals((float[])a,(float[])b);
        if(a instanceof boolean[])return Arrays.equals((boolean[])a,(boolean[])b);
        return a==null?b==null:a.equals(b);
    }

    static void settings() throws Exception {
        for(Field f:VinylEngine.Settings.class.getFields()){
            if(Modifier.isStatic(f.getModifiers())||Modifier.isTransient(f.getModifiers()))continue;
            VinylEngine.Settings s=VinylEngine.Settings.defaults();
            Object next=perturbed(f,f.get(s));
            if(next==null)continue;
            f.set(s,next);
            check(same(next,f.get(s.copy())),"Settings.copy() drops public field "+f.getName());
        }
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.marks=new float[][]{{1,2},{3,4},{5,6},{7,8}};
        VinylEngine.Settings c=s.copy();
        c.marks[0][0]=99;c.layers.get(0).color=0x12345678;c.layers.get(0).curve[1]=.9f;
        c.snips.add(new SnipMask.Operation(SnipMask.REMOVE_INSIDE,new float[]{0,0,1,0,1,1}));
        check(s.marks[0][0]==1,"marks shared with copy");
        check(s.layers.get(0).color!=0x12345678,"layer shared with copy");
        check(s.layers.get(0).curve[1]!=.9f,"layer curve shared with copy");
        check(s.snips.isEmpty(),"snips shared with copy");
        for(Field f:VinylEngine.Layer.class.getFields()){
            if(Modifier.isStatic(f.getModifiers()))continue;
            VinylEngine.Layer l=new VinylEngine.Layer("a",0xff112233);
            Object next=perturbed(f,f.get(l));
            if(next==null)continue;
            f.set(l,next);
            check(same(next,f.get(l.copy())),"Layer.copy() drops field "+f.getName());
        }
    }

    static void config() throws Exception {
        for(Field f:VinylStyleSuite.Config.class.getFields()){
            if(Modifier.isStatic(f.getModifiers()))continue;
            VinylStyleSuite.Config cfg=new VinylStyleSuite.Config();
            if(f.getType()==PopPosterEngine.Options.class){
                PopPosterEngine.Options o=new PopPosterEngine.Options();
                cfg.popPoster=o;
                VinylStyleSuite.Config copy=cfg.copy();
                check(copy.popPoster!=null&&copy.popPoster!=o,"Config.copy popPoster not independent");
                continue;
            }
            Object cur=f.get(cfg);
            Object next=cur==null?perturbed(f,null):perturbed(f,cur);
            if(next==null){System.out.println("NOTE Config field not probed: "+f.getName()+" "+f.getType());continue;}
            f.set(cfg,next);
            check(same(next,f.get(cfg.copy())),"Config.copy() drops field "+f.getName());
        }
        VinylStyleSuite.Config a=new VinylStyleSuite.Config();
        a.palette=new int[]{1,2,3};a.allowed=new boolean[]{true,true};
        VinylStyleSuite.Config b=a.copy();b.palette[0]=9;b.allowed[0]=false;
        check(a.palette[0]==1&&a.allowed[0],"Config copy shares arrays");
    }

    public static void main(String[] args) throws Exception {
        settings();config();
        if(failures>0)throw new AssertionError(failures+" snapshot copy defects");
        System.out.println("PASS M5SnapshotCopyTest");
    }
}

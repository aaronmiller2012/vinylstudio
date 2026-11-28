package com.halftonestudio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * M8 / I1: cut-inspector findings must be a function of the cut geometry, not of its
 * representation. The same sheets re-expressed with a different ring start vertex, reversed
 * traversal, shuffled ring order and all three combined must produce identical findings
 * (same list, same order, same marker positions) at an unlimited and at a capped warning limit.
 * Real engine output (Chainmail, Confetti, Fishnet on CC0 photos) plus synthetic necks, gaps,
 * touching contours, tiny holes and tiny islands.
 */
public final class M8RingOrderInvarianceTest {
    static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);}
    static int sheets,findings,variants;

    static String sig(List<StyleCutGeometryPreflight.Warning> w){
        StringBuilder s=new StringBuilder();
        for(StyleCutGeometryPreflight.Warning x:w)s.append(String.format(Locale.US,"%d|%s|%.5f|%.5f|%.5f|%.5f%n",
            x.layer,x.reason,x.xMm,x.yMm,x.midXMm,x.midYMm));
        return s.toString();
    }
    static final boolean DIAG=Boolean.getBoolean("diag");
    static String sorted(String s){String[] a=s.split("\n");java.util.Arrays.sort(a);return String.join("\n",a);}
    static float[] rotate(float[] p,int k){int n=p.length/2;float[] q=new float[p.length];
        for(int i=0;i<n;i++){q[2*i]=p[2*((i+k)%n)];q[2*i+1]=p[2*((i+k)%n)+1];}return q;}
    static float[] reverse(float[] p){int n=p.length/2;float[] q=new float[p.length];
        for(int i=0;i<n;i++){q[2*i]=p[2*(n-1-i)];q[2*i+1]=p[2*(n-1-i)+1];}return q;}
    /** mode bit 1 rotate start, bit 2 reverse, bit 4 shuffle ring order. */
    static StyleKit.Result variant(StyleKit.Result r,int mode,long seed){
        Random rnd=new Random(seed);
        StyleKit.Result o=new StyleKit.Result(r.widthMm,r.heightMm);
        for(StyleKit.Layer l:r.layers){
            StyleKit.Layer c=new StyleKit.Layer(l.name,l.color);
            for(float[] p:l.loops){float[] q=p.clone();
                if((mode&1)!=0)q=rotate(q,1+rnd.nextInt(q.length/2));
                if((mode&2)!=0)q=reverse(q);
                c.loops.add(q);}
            if((mode&4)!=0)Collections.shuffle(c.loops,rnd);
            c.lines.addAll(l.lines);o.layers.add(c);
        }
        return o;
    }
    static void invariant(String name,StyleKit.Result r,float min,float gap){
        for(int limit:new int[]{100000,40}){
            String base=sig(StyleCutGeometryPreflight.inspect(r,min,gap,limit));
            String strict=sig(StyleCutGeometryPreflight.inspect(r,min,limit));
            if(limit==100000)findings+=base.isEmpty()?0:base.split("\n").length;
            for(int mode=1;mode<8;mode++)for(long seed=1;seed<=2;seed++){
                StyleKit.Result v=variant(r,mode,seed*31+mode);
                String got=sig(StyleCutGeometryPreflight.inspect(v,min,gap,limit));
                String gotStrict=sig(StyleCutGeometryPreflight.inspect(v,min,limit));
                variants++;
                if(DIAG){int a=base.isEmpty()?0:base.split("\n").length,b=got.isEmpty()?0:got.split("\n").length;
                    if(!base.equals(got))System.out.printf(Locale.US,"DIFF %s limit %d mode %d: %d -> %d (sameSet=%b)%n",name,limit,mode,a,b,sorted(base).equals(sorted(got)));continue;}
                if(!base.equals(got)||!strict.equals(gotStrict)){
                    int a=base.isEmpty()?0:base.split("\n").length,b=got.isEmpty()?0:got.split("\n").length;
                    throw new AssertionError(String.format(Locale.US,
                        "%s limit %d mode %d seed %d: findings differ (%d vs %d classified; strict equal=%b)",
                        name,limit,mode,seed,a,b,strict.equals(gotStrict)));
                }
            }
        }
        sheets++;
    }
    static float[] rect(float x,float y,float w,float h){return new float[]{x,y,x+w,y,x+w,y+h,x,y+h};}
    static StyleKit.Result synthetic(){
        StyleKit.Result r=new StyleKit.Result(100,100);
        StyleKit.Layer l=new StyleKit.Layer("synthetic",0xff000000);
        // Dumbbell with a 0.15 mm neck, a densely sampled arc, two islands 0.1 mm apart,
        // a touching pair, a frame with a tiny hole, and a tiny island.
        l.loops.add(new float[]{0,0,10,0,10,4.9f,14,4.9f,14,0,24,0,24,10,14,10,14,5.05f,10,5.05f,10,10,0,10});
        float[] arc=new float[2*64];for(int i=0;i<64;i++){double a=Math.PI*2*i/64;arc[2*i]=(float)(50+8*Math.cos(a));arc[2*i+1]=(float)(20+8*Math.sin(a));}
        l.loops.add(arc);
        l.loops.add(rect(0,30,10,10));l.loops.add(rect(10.1f,30,10,10));
        l.loops.add(rect(30,30,10,10));l.loops.add(rect(40,32,10,6));
        l.loops.add(rect(0,60,30,30));l.loops.add(rect(10,70,.12f,.12f));l.loops.add(rect(14,74,8,8));
        l.loops.add(rect(60,60,.1f,.3f));
        r.layers.add(l);
        return r;
    }
    public static void main(String[] args)throws Exception{
        invariant("synthetic",synthetic(),.254f,.508f);
        int[] styles={VinylStyleSuite.CHAINMAIL,VinylStyleSuite.CONFETTI,VinylStyleSuite.FISHNET};
        for(String photo:new String[]{"astronaut.png"}){
            M4ArtBench.Photo p=M4ArtBench.load(new java.io.File("tests/m4/photos/"+photo),1800);
            for(int style:styles){
                VinylStyleSuite.Config c=M4ArtBench.suiteConfig(style,p,152.4f,.254f,.508f);
                StyleKit.Result r=VinylStyleSuite.generate(p.argb,p.w,p.h,c);
                check(r.layers.size()>=2,"no layers");
                invariant(VinylStyleSuite.NAMES[style]+"/"+photo,r,.254f,.508f);
            }
        }
        check(findings>0,"no findings at all; invariance proved nothing");
        System.out.printf(Locale.US,"PASS I1 cut-inspector representation invariance: %d sheets, %d findings, %d variants%n",sheets,findings,variants);
    }
}

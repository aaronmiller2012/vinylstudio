package com.halftonestudio;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Host JVM behavioral tests: dimension budgets, render memory/cancellation and scheduler. */
public final class RC87PerformanceTest {
    private static void check(boolean ok,String detail){if(!ok)throw new AssertionError(detail);}
    private static void sizing(int w,int h,int maxSide,long maxPixels){
        PhotoDecodeBudget.Plan p=PhotoDecodeBudget.plan(w,h,maxSide,maxPixels);
        check(p.width>=1&&p.height>=1,"Invalid photo size");
        check(Math.max(p.width,p.height)<=maxSide,"Photo exceeded side budget");
        check((long)p.width*p.height<=maxPixels,"Photo exceeded pixel budget");
        check((p.sampleSize&(p.sampleSize-1))==0,"Sample not a power of two");
        check((long)w*h/(p.sampleSize*(long)p.sampleSize)<=maxPixels*1.51,"Intermediate bitmap too large");
        check(Math.abs((double)p.width/p.height-(double)w/h)<.03,"Aspect ratio changed");
    }
    private static void photos(){
        sizing(4000,3000,1800,3000000);
        sizing(12000,9000,1800,3000000);
        sizing(12000,9000,2400,4500000);
        sizing(1080,1920,1800,3000000);
        sizing(10000,500,1800,3000000);
        sizing(300,200,1800,3000000);
        PhotoDecodeBudget.Plan p=PhotoDecodeBudget.plan(4000,3000,1800,3000000);
        check(p.width==1800&&p.height==1350,"Camera image should be accurately scaled to 1800x1350");
        boolean refused=false;
        try{PhotoDecodeBudget.plan(Integer.MAX_VALUE,8,1800,3000000);}catch(IllegalArgumentException expected){refused=true;}
        check(refused,"Malformed image bounds accepted");
        check(PhotoDecodeBudget.plan(300,200,1800,3000000).sampleSize==1,"Small input was sampled");
        System.out.println("PASS RC87: 1800px working photos / 2400px bounded originals / source aspect ratio");
    }
    private static void scheduling()throws Exception{
        final StudioRenderController c=new StudioRenderController();
        try{
            CountDownLatch heavyEntered=new CountDownLatch(1),heavyRelease=new CountDownLatch(1);
            Future<?> standard=c.submit(StudioRenderController.Lane.STANDARD,()->{
                heavyEntered.countDown();
                try{heavyRelease.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}
            });
            check(heavyEntered.await(2,TimeUnit.SECONDS),"Standard renderer never started");
            AtomicBoolean otherHeavyRan=new AtomicBoolean(false);
            Future<?> specialty=c.submit(StudioRenderController.Lane.SPECIALTY,()->otherHeavyRan.set(true));
            CountDownLatch previewRan=new CountDownLatch(1);
            Future<?> preview=c.submit(StudioRenderController.Lane.PREVIEW,previewRan::countDown);
            check(previewRan.await(2,TimeUnit.SECONDS),"Lightweight layer preview blocked behind geometry");
            check(!otherHeavyRan.get(),"Two simultaneous heavy engines exceeded memory budget");
            specialty.cancel(true);
            heavyRelease.countDown();
            standard.get(2,TimeUnit.SECONDS);preview.get(2,TimeUnit.SECONDS);
            check(!otherHeavyRan.get(),"Canceled specialty task unexpectedly executed");
            CountDownLatch complete=new CountDownLatch(1);
            c.submit(StudioRenderController.Lane.SPECIALTY,complete::countDown);
            check(complete.await(2,TimeUnit.SECONDS),"Canceled slots were not released");
        }finally{c.close();}
        System.out.println("PASS RC87: weighted render scheduling, cancellation, interactive preview");
    }
    public static void main(String[]args)throws Exception{photos();scheduling();}
}

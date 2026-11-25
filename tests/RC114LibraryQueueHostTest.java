package com.halftonestudio;

import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Host test: one worker, 32 bounded pending tasks, never execute file I/O on caller. */
public final class RC114LibraryQueueHostTest {
    private static void need(boolean ok,String text){if(!ok)throw new AssertionError(text);}
    public static void main(String[] unused) throws Exception {
        File temp=Files.createTempDirectory("vinyl-rc114-queue-").toFile();
        StudioProjectLibrary library=new StudioProjectLibrary(temp);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicInteger callbacks=new AtomicInteger(),diagnostics=new AtomicInteger();
        try(StudioLibraryJobs jobs=new StudioLibraryJobs(library,Runnable::run,
                error->diagnostics.incrementAndGet())) {
            Thread caller=Thread.currentThread();
            jobs.backup(()->{
                need(Thread.currentThread()!=caller,"file IO ran on caller/UI thread");
                entered.countDown();
                try{release.await(4,TimeUnit.SECONDS);}
                catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IOException("Cancelled",ex);}
                return new ByteArrayOutputStream();
            },ignored->callbacks.incrementAndGet(),ex->callbacks.incrementAndGet());
            need(entered.await(4,TimeUnit.SECONDS),"worker not started");
            for(int n=0;n<StudioLibraryJobs.MAX_QUEUED;n++){
                jobs.list(ignored->callbacks.incrementAndGet(),ex->callbacks.incrementAndGet());
            }
            need(jobs.queuedOperations()==32,"queue capacity changed");
            AtomicReference<Exception> rejected=new AtomicReference<>();
            jobs.list(ignored->throwFailure("overflow accepted"),rejected::set);
            need(rejected.get() instanceof RejectedExecutionException,"overflow not reported");
            need(jobs.queuedOperations()==32,"queue exceeded 32 tasks");
            need(diagnostics.get()==1,"diagnostics not delivered for rejection");
            release.countDown();
        }finally{release.countDown();}
        System.out.println("PASS bounded library queue, no UI file IO, rejection feedback and lifecycle shutdown");
    }
    private static void throwFailure(String reason){throw new AssertionError(reason);}
}

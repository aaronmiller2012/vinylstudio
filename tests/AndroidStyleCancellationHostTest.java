package com.halftonestudio;

import android.graphics.Bitmap;
import java.util.Arrays;
import java.util.concurrent.CancellationException;

/** Cancellation checks for Android-dependent style engines using minimal host stubs. */
public final class AndroidStyleCancellationHostTest {
    private interface Job {void run();}
    private static void test(String name,Job job){
        Thread.currentThread().interrupt();boolean cancelled=false;
        try{job.run();}catch(CancellationException expected){cancelled=true;}
        finally{Thread.interrupted();}
        if(!cancelled)throw new AssertionError(name+" ignored cancellation");
    }
    public static void main(String[] args){
        int[] source=new int[96*96];Arrays.fill(source,0xff334455);
        Bitmap bitmap=new Bitmap(96,96,source);
        test("maze",()->MazePortraitEngine.generate(bitmap,new MazePortraitEngine.Options()));
        test("swirl",()->SingleLineSwirlEngine.generate(bitmap,new SingleLineSwirlEngine.Options()));
        test("hilbert",()->ProceduralPortraitLines.generate(bitmap,true,60));
        test("spirograph",()->ProceduralPortraitLines.generate(bitmap,false,60));
        // Normal generation must remain deterministic after interruption was cleared.
        MazePortraitEngine.Options opt=new MazePortraitEngine.Options();
        MazePortraitEngine.Result first=MazePortraitEngine.generate(bitmap,opt);
        MazePortraitEngine.Result second=MazePortraitEngine.generate(bitmap,opt);
        if(!first.toWallSvg(.35f).equals(second.toWallSvg(.35f)))throw new AssertionError("Normal maze changed");
        System.out.println("PASS: maze, swirl, Hilbert and spirograph terminate on interrupt; resumed maze deterministic");
    }
}

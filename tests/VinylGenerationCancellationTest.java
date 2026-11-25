package com.halftonestudio;

/** Standard engine must stop cleanly when an obsolete preview task is canceled. */
public final class VinylGenerationCancellationTest {
    public static void main(String[] args) {
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        int[] img=new int[80*80];java.util.Arrays.fill(img,0xff7b879a);
        boolean cancelled=false;
        Thread.currentThread().interrupt();
        try { VinylEngine.generate(img,80,80,s); }
        catch(java.util.concurrent.CancellationException expected) {cancelled=true;}
        finally { Thread.interrupted(); }
        if(!cancelled) throw new AssertionError("Obsolete vector generation must honor interruption");
        // Cancellation must not poison a subsequent normal calculation.
        VinylEngine.Result normal=VinylEngine.generate(img,80,80,s);
        if(normal==null)throw new AssertionError("Post-cancel generation failed");
        System.out.println("PASS: V2.41 canceled standard render exits; subsequent generation succeeds");
    }
}

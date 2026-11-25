package com.halftonestudio;
import java.util.Arrays;
import java.util.Random;

/** Exact color output parity against unmodified v2.48.1 lighting for snipped portraits. */
public class ImageLevelsParityTest {
    private static void ok(boolean good,String detail){if(!good)throw new AssertionError(detail);}
    public static void main(String[] args){
        int w=960,h=1100;
        int[] src=new int[w*h];Random random=new Random(7641);
        for(int i=0;i<src.length;i++)src[i]=0xff000000|random.nextInt(0x1000000);
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.snips.add(new SnipMask.Operation(SnipMask.KEEP_INSIDE,
            .14f,.06f, .65f,.07f, .92f,.36f, .88f,.89f, .45f,.99f, .08f,.62f));
        s.snips.add(new SnipMask.Operation(SnipMask.REMOVE_INSIDE,
            .65f,.19f, .70f,.20f, .73f,.29f, .69f,.33f, .64f,.28f));
        s.exposureStops=.3f;s.photoContrast=1.15f;s.levelsShadows=.35f;
        s.levelsHighlights=-.2f;s.edgePriority=.55f;
        long t=System.nanoTime();int[] expected=ImageLevelsReference.apply(src,w,h,s);
        long oldMs=(System.nanoTime()-t)/1_000_000L;
        t=System.nanoTime();int[] actual=ImageLevels.apply(src,w,h,s);
        long newMs=(System.nanoTime()-t)/1_000_000L;
        ok(Arrays.equals(expected,actual),"lighting pixels differ from v2.48.1 after snips");
        // Neutral and unmasked modes must also preserve exact output.
        s.snips.clear();ok(Arrays.equals(ImageLevelsReference.apply(src,w,h,s),ImageLevels.apply(src,w,h,s)),"non-snip lighting mismatch");
        s.edgePriority=0;s.exposureStops=0;s.photoContrast=1;s.levelsShadows=0;s.levelsHighlights=0;
        ok(ImageLevels.isNeutral(s),"neutral detection changed");
        ok(ImageLevels.apply(src,w,h,s)==src,"neutral inputs must not copy photo");
        System.out.println("PASS: pixel-for-pixel snip lighting parity, neutral and unsnipped parity; reference="+oldMs+"ms, optimized="+newMs+"ms");
    }
}

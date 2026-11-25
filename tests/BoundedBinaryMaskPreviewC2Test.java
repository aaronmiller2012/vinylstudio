package com.halftonestudio;

import java.util.Arrays;
import java.util.concurrent.CancellationException;

/** Host-only regression for C2: bounded preview memory and unchanged binary geometry. */
public final class BoundedBinaryMaskPreviewC2Test {
    private static void require(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    private static void bad(Runnable f){
        try{f.run();throw new AssertionError("Expected invalid dimensions");}
        catch(IllegalArgumentException expected){}
    }
    public static void main(String[] ignored)throws Exception {
        boolean[] src={true,false,false,true,false,true};
        BoundedBinaryMaskPreview.Result nativeSize=BoundedBinaryMaskPreview.compose(3,2,src,200);
        require(nativeSize.width==3&&nativeSize.height==2,"native size unchanged");
        int[] expected={0xff000000,0xffffffff,0xffffffff,0xff000000,0xffffffff,0xff000000};
        require(Arrays.equals(expected,nativeSize.argb),"full-size exact black/white mask parity");
        BoundedBinaryMaskPreview.Result smaller=BoundedBinaryMaskPreview.compose(3,2,src,2);
        require(smaller.width==2&&smaller.height==1,"bounded non-square dimensions");
        require(Arrays.equals(smaller.argb,BoundedBinaryMaskPreview.compose(3,2,src,2).argb),"deterministic previews");
        // Four source pixels (two black and two white) must average to 50% ink.
        boolean[] checks={true,false,false,true};
        int center=BoundedBinaryMaskPreview.compose(2,2,checks,1).argb[0];
        require((center&255)>=127&&(center&255)<=128,"bilinear coverage of checkerboard");
        require((center>>>24)==255,"preview stays opaque");
        boolean[] fullInk=new boolean[10000];Arrays.fill(fullInk,true);
        require((BoundedBinaryMaskPreview.compose(100,100,fullInk,11).argb[0]&0xffffff)==0,"dark solid fill");
        boolean[] big=new boolean[5000*3000];
        for(int i=0;i<big.length;i+=37)big[i]=true;
        BoundedBinaryMaskPreview.Result bounded=BoundedBinaryMaskPreview.compose(5000,3000,big,480);
        require(bounded.width==480&&bounded.height==288,"large mask output bound");
        require(bounded.argb.length==480*288,"no output sized to 15m source pixels");
        bad(()->BoundedBinaryMaskPreview.compose(0,2,src,2));
        bad(()->BoundedBinaryMaskPreview.compose(3,2,new boolean[5],2));
        bad(()->BoundedBinaryMaskPreview.compose(3,2,src,0));
        bad(()->BoundedBinaryMaskPreview.compose(3,2,src,5000));
        Thread canceled=new Thread(()->{
            Thread.currentThread().interrupt();
            try{BoundedBinaryMaskPreview.compose(5000,3000,big,480);
                throw new AssertionError("Cancelled thumbnail rendered");
            }catch(CancellationException expectedCancel){}
        });
        final java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        Thread checked=new Thread(()->{
            try{
                Thread.currentThread().interrupt();
                BoundedBinaryMaskPreview.compose(5000,3000,big,480);
                throw new AssertionError("Missing cancellation");
            }catch(CancellationException expectedCancel){}
            catch(Throwable t){failure.set(t);}
        });
        checked.start();checked.join(2000);
        require(!checked.isAlive()&&failure.get()==null,"prompt cancellation of obsolete preview");
        System.out.println("PASS C2 bounded binary-mask preview: source parity, interpolation, allocation bound, cancellation, invalid dimensions");
    }
}

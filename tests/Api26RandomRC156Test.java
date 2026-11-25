package com.halftonestudio;

import java.util.SplittableRandom;

/** Guards the API-26-compatible confetti RNG paths against future changes. */
public final class Api26RandomRC156Test {
    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args){
        SplittableRandom a=new SplittableRandom(0x514cA203L);
        SplittableRandom b=new SplittableRandom(0x514cA203L);
        float sum=0f;
        int high=0,heads=0;
        for(int i=0;i<100000;i++){
            float x=FallenConfettiEngine.unitFloat(a);
            float y=FallenConfettiEngine.unitFloat(b);
            require(x==y,"seeded scatter not deterministic");
            require(x>=0f&&x<1f,"randomUnit outside [0,1)");
            sum+=x;
            if(x>=.5f)high++;
            boolean c=FallenConfettiEngine.coinFlip(a);
            boolean d=FallenConfettiEngine.coinFlip(b);
            require(c==d,"seeded coin flip not deterministic");
            if(c)heads++;
        }
        require(sum/100000f>.49f&&sum/100000f<.51f,"fraction bias");
        require(high>48000&&high<52000,"uniform fraction regression");
        require(heads>48000&&heads<52000,"coin flip regression");
        System.out.println("PASS RC156 API-26-compatible random fractions, seed parity and distribution");
    }
}

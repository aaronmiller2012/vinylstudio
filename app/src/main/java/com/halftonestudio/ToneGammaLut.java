package com.halftonestudio;

/** Per-render gamma transfer table; immutable after construction, no shared mutable state.
 * Linear interpolation avoids the repeated transcendental operation in each pixel loop.
 * Quantization error is bounded by tests to at most one 8-bit channel value.
 */
final class ToneGammaLut {
    private static final int BITS=14, SIZE=1<<BITS;
    private final float[] values=new float[SIZE+1];
    ToneGammaLut(float gamma){
        if(!Float.isFinite(gamma)||gamma<=0f)throw new IllegalArgumentException("Invalid gamma");
        double power=1d/gamma;
        for(int i=0;i<=SIZE;i++)values[i]=(float)Math.pow(i/(double)SIZE,power);
    }
    float map(float normalized){
        if(normalized<=0f)return 0f;
        if(normalized>=1f)return 1f;
        float at=normalized*SIZE;
        int i=(int)at;
        float frac=at-i;
        return values[i]+(values[i+1]-values[i])*frac;
    }
}

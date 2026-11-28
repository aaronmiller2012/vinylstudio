package com.halftonestudio;

/**
 * M7 P1: allocation-free parsing of the SVG path coordinates VinylStudio itself writes.
 *
 * {@link #parse} returns exactly {@code Float.parseFloat(s.substring(from,to))} for every
 * input, including the exceptions it throws:
 * <ul>
 * <li>Fast path: an optional sign, decimal digits and at most one '.', with the digit
 *   mantissa below 2^24 and at most 10 fraction digits. The mantissa and 10^k (k&lt;=10;
 *   5^10 &lt; 2^24) are then both exactly representable floats, so one IEEE division is
 *   the correctly rounded quotient, which is what {@code Float.parseFloat} returns.</li>
 * <li>Everything else (exponents, long mantissas, malformed text) defers to
 *   {@code Float.parseFloat}, so unsupported or invalid syntax still fails identically.</li>
 * </ul>
 * Callers keep their own finiteness checks (fail closed on NaN/Infinity).
 */
final class SvgNumber {
    private SvgNumber(){}
    private static final float[] POW10={1f,10f,100f,1e3f,1e4f,1e5f,1e6f,1e7f,1e8f,1e9f,1e10f};

    /** The characters the existing tokenizers accept inside one number token. */
    static boolean tokenChar(char c){return (c>='0'&&c<='9')||c=='.'||c=='+'||c=='-'||c=='e'||c=='E';}

    static float parse(String s,int from,int to){
        if(from>=to)return Float.parseFloat(s.substring(from,to)); // throws like the original
        int i=from;boolean neg=false;
        char c=s.charAt(i);
        if(c=='-'||c=='+'){neg=c=='-';i++;}
        int mant=0,frac=-1,digits=0;
        for(;i<to;i++){
            c=s.charAt(i);
            if(c>='0'&&c<='9'){
                mant=mant*10+(c-'0');digits++;
                if(frac>=0)frac++;
                if(mant>=(1<<24))return Float.parseFloat(s.substring(from,to));
            }else if(c=='.'&&frac<0)frac=0;
            else return Float.parseFloat(s.substring(from,to));
        }
        if(digits==0||frac>10)return Float.parseFloat(s.substring(from,to));
        float v=frac<=0?(float)mant:mant/POW10[frac];
        return neg?-v:v;
    }
}

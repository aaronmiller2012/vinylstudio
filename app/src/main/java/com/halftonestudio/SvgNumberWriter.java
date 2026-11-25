package com.halftonestudio;

/** Allocation-light numeric SVG writer. 3 and 5 decimals, using rounded fixed-point. */
final class SvgNumberWriter {
    private SvgNumberWriter() {}
    static StringBuilder append(StringBuilder dst,float value,int decimals){
        if(!Float.isFinite(value))throw new IllegalArgumentException("Non-finite SVG coordinate");
        if(decimals<0||decimals>6)throw new IllegalArgumentException("SVG decimal precision");
        long factor=1;
        for(int i=0;i<decimals;i++)factor*=10;
        long rounded=Math.round((double)value*factor);
        if(rounded<0){dst.append('-');rounded=-rounded;}
        dst.append(rounded/factor);
        if(decimals>0){
            dst.append('.');long remainder=rounded%factor;
            for(long unit=factor/10;unit>remainder&&unit>1;unit/=10)dst.append('0');
            dst.append(remainder);
        }
        return dst;
    }
}

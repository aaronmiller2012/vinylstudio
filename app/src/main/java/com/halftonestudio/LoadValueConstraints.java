package com.halftonestudio;
/** Import-bound numeric normalization; matches VinylEngine.generate physical ranges. */
final class LoadValueConstraints {
    private LoadValueConstraints(){}
    static float finiteRange(double value,float min,float max,float fallback){
        if(!Double.isFinite(value))return fallback;
        return (float)Math.max(min,Math.min(max,value));
    }
}

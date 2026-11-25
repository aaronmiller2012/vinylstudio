package com.halftonestudio;

/** Build 555 hot-path math for cut inspection. Math.hypot is overflow-safe but several
 * times slower (StrictMath, plus finiteness checks per call); inspection inputs are
 * millimetre coordinates far from overflow, where sqrt(dx*dx+dy*dy) in double
 * precision is correctly rounded and rounds to the same float result. */
final class FastGeom {
    private FastGeom() { }
    static double len(double dx,double dy){return Math.sqrt(dx*dx+dy*dy);}
}

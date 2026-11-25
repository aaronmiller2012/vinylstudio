package com.halftonestudio;

public final class SnipInputPolicyTest {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        check(SnipInputPolicy.isPen(2),"S Pen / Android stylus recognized");
        check(SnipInputPolicy.isPen(4),"inverted stylus recognized");
        check(!SnipInputPolicy.isPen(1),"finger is not stylus");
        check(!SnipInputPolicy.ignoreForPalm(1,false),"finger works on normal phones");
        check(SnipInputPolicy.ignoreForPalm(1,true),"hovering pen suppresses accidental palm");
        check(!SnipInputPolicy.ignoreForPalm(2,true),"pen is never blocked by its own hover");
        check(SnipInputPolicy.keepPenAfterSecondaryExit(true,9,13),"palm release keeps pen");
        check(!SnipInputPolicy.keepPenAfterSecondaryExit(true,9,9),"pen release finishes pen");
        check(!SnipInputPolicy.keepPenAfterSecondaryExit(false,9,13),"finger pinch is not pen");
        System.out.println("PASS: finger/stylus arbitration, pen prioritization, palm suppression");
    }
}

package com.halftonestudio;
public final class RegionalDetailV2541Test {
  static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
  public static void main(String[] args){
    check(RegionalPortraitDetail.priority(50,44,100,100,.8f,.3f,255)>0.6f,"central region not prioritized");
    check(RegionalPortraitDetail.priority(2,2,100,100,.8f,.3f,255)==0,"background should not be prioritized");
    check(RegionalPortraitDetail.priority(50,44,100,100,.8f,.3f,0)==0,"transparent priority");
    check(CmykLineVisibility.apply(0,.9f,1)==0,"invented blank CMYK");
    check(CmykLineVisibility.apply(.07f,0,1)==.07f,"zero control must be exact");
    check(CmykLineVisibility.apply(.07f,.9f,1)>.07f,"light facial detail not recovered");
    check(CmykLineVisibility.apply(.25f,.9f,0)>0.25f,"unified base response missing");
    System.out.println("PASS regional priority, alpha, blank suppression, zero-compatibility, CMYK visibility");
  }
}

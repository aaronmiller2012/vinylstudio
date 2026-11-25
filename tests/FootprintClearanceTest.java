package com.halftonestudio;
import org.locationtech.jts.io.WKTReader;
public final class FootprintClearanceTest {
 public static void main(String[] args) throws Exception {
  WKTReader r=new WKTReader();
  String s=FootprintClearance.inspect(r.read("MULTIPOLYGON (((0 0, 1 0, 1 1, 0 1, 0 0)), ((1.1 0, 2.1 0, 2.1 1, 1.1 1, 1.1 0)))"),.3,.2,()->{});
  if(!s.contains("pairs below gap: 1"))throw new AssertionError(s);
  s=FootprintClearance.inspect(r.read("POLYGON ((0 0, 2 0, 2 .9, 4 .9, 4 0, 6 0, 6 2, 4 2, 4 1.1, 2 1.1, 2 2, 0 2, 0 0))"),.5,0,()->{});
  if(!s.contains("offset splits): 1"))throw new AssertionError(s);
  s=FootprintClearance.inspect(r.read("POLYGON ((0 0, 2 0, 2 .1, 0 .1, 0 0))"),.3,0,()->{});
  if(!s.contains("inscribed diameter: 1")||!s.contains("same-ring width candidates: 1"))throw new AssertionError(s);
  s=FootprintClearance.inspect(r.read("POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0), (.1 1, 2 1, 2 2, .1 2, .1 1))"),.2,0,()->{});
  if(!s.contains("ring pairs below width (thin vinyl): 1")||!s.contains("reference: x"))throw new AssertionError(s);
  String noWidth=FootprintClearance.inspect(r.read("POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0), (.1 1, 2 1, 2 2, .1 2, .1 1))"),0,.5,()->{});
  if(!noWidth.contains("Width checks disabled")||noWidth.contains("thin vinyl): 1"))throw new AssertionError(noWidth);
  String smallerWidth=FootprintClearance.inspect(r.read("POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0), (.1 1, 2 1, 2 2, .1 2, .1 1))"),.05,.5,()->{});
  if(!smallerWidth.contains("ring pairs below width (thin vinyl): 0"))throw new AssertionError(smallerWidth);
  System.out.println("PASS footprint clearance candidates");
 }
}

package com.halftonestudio;
import java.util.*;
public final class StyleDiscoveryTest {
 public static void main(String[] args){
  check(StyleDiscovery.matches("Round Dots circle", "CIRCLE round"));
  check(StyleDiscovery.matches("Hexagon "+StyleDiscovery.aliases("shape.hexagon"),"honeycomb"));
  check(StyleDiscovery.matches("Torn-Paper Collage", "paper collage"));
  check(!StyleDiscovery.matches("Hexagon", "spiral"));
  check(StyleDiscovery.matches("Hexagon", "  "));
  List<String> keys=StyleDiscovery.decode("shape.round\nshape.round\ninvalid key\nshape.hexagon",128);
  check(keys.equals(Arrays.asList("shape.round","shape.hexagon")));
  StyleDiscovery.touch(keys,"shape.hexagon",2);check(keys.get(0).equals("shape.hexagon"));
  StyleDiscovery.touch(keys,"line.straight",2);check(keys.equals(Arrays.asList("line.straight","shape.hexagon")));
  check(StyleDiscovery.decode(null,12).isEmpty());
  check(StyleDiscovery.decode(String.join("\n",keys),12).equals(keys));
  System.out.println("PASS style discovery search, synonyms, normalization, bounded recency and persistence codec");
 }
 static void check(boolean ok){if(!ok)throw new AssertionError();}
}

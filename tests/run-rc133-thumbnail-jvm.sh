#!/usr/bin/env bash
# Android-free compilation and deterministic drawing signatures, using minimal fake Canvas classes.
set -euo pipefail
cd "$(dirname "$0")/.."
t="$(mktemp -d)";trap 'rm -rf "$t"' EXIT
mkdir -p "$t/src/android/content" "$t/src/android/graphics" "$t/src/android/view" "$t/src/com/halftonestudio" "$t/classes"
cat >"$t/src/android/content/Context.java" <<'JAVA'
package android.content; public class Context {}
JAVA
cat >"$t/src/android/view/View.java" <<'JAVA'
package android.view; import android.content.Context; import android.graphics.Canvas;
public class View {
 public static final int IMPORTANT_FOR_ACCESSIBILITY_NO=2;
 public View(Context c) {} public void setImportantForAccessibility(int n){}
 public int getWidth(){return 100;}public int getHeight(){return 60;}
 protected void onDraw(Canvas c){}
}
JAVA
cat >"$t/src/android/graphics/Typeface.java" <<'JAVA'
package android.graphics; public class Typeface {public static final Typeface MONOSPACE=new Typeface();}
JAVA
cat >"$t/src/android/graphics/RectF.java" <<'JAVA'
package android.graphics;
public class RectF{public void set(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}public float left,top,right,bottom; public RectF(){}public RectF(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}public float width(){return right-left;}public float height(){return bottom-top;}}
JAVA
cat >"$t/src/android/graphics/Paint.java" <<'JAVA'
package android.graphics;
public class Paint {
 public static final int ANTI_ALIAS_FLAG=1;
 public enum Style{FILL,STROKE}public enum Cap{ROUND}public enum Join{ROUND,MITER}
 private int color;private float width;private Style style=Style.FILL;
 public Paint(int flags){}public void setColor(int c){color=c;}public int getColor(){return color;}
 public void setStyle(Style s){style=s;} public Style getStyle(){return style;}
 public void setStrokeWidth(float w){width=w;}public float getStrokeWidth(){return width;}
 public void setStrokeCap(Cap c){}public void setStrokeJoin(Join j){}
 public void setTypeface(Typeface f){}public void setTextSize(float f){}
}
JAVA
cat >"$t/src/android/graphics/Path.java" <<'JAVA'
package android.graphics;
public class Path{
 private final StringBuilder b=new StringBuilder();private float minX,maxX,minY,maxY;
 public void reset(){b.setLength(0);minX=minY=Float.POSITIVE_INFINITY;maxX=maxY=Float.NEGATIVE_INFINITY;}
 public Path(){reset();}private void add(char op,float... values){b.append(op);for(float n:values){b.append((int)(n*1000)).append(',');}}
 private void point(float x,float y){minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
 public void moveTo(float x,float y){point(x,y);add('M',x,y);}public void lineTo(float x,float y){point(x,y);add('L',x,y);}
 public void cubicTo(float x1,float y1,float x2,float y2,float x3,float y3){point(x1,y1);point(x2,y2);point(x3,y3);add('C',x1,y1,x2,y2,x3,y3);}
 public void close(){b.append('Z');}
 public void computeBounds(RectF o,boolean accurate){o.left=minX;o.right=maxX;o.top=minY;o.bottom=maxY;}
 public String signature(){return b.toString();}
}
JAVA
cat >"$t/src/android/graphics/Canvas.java" <<'JAVA'
package android.graphics;
public class Canvas {
 public StringBuilder log=new StringBuilder();
 private void item(String type,Paint p,String data){log.append(type).append(':').append(p.getColor()).append(':').append(p.getStyle()).append(':').append((int)(p.getStrokeWidth()*100)).append(':').append(data).append(';');}
 private static String v(float... f){StringBuilder b=new StringBuilder();for(float x:f)b.append((int)(x*1000)).append(',');return b.toString();}
 public int save(){return 0;}public void restore(){}public void scale(float x,float y){}public void translate(float x,float y){}public void rotate(float deg,float px,float py){}public void clipRect(float l,float t,float r,float b){}
 public void drawRect(float l,float t,float r,float b,Paint p){item("rect",p,v(l,t,r,b));}
 public void drawRoundRect(float l,float t,float r,float b,float rx,float ry,Paint p){item("round",p,v(l,t,r,b,rx,ry));}
 public void drawOval(float l,float t,float r,float b,Paint p){item("oval",p,v(l,t,r,b));}
 public void drawCircle(float x,float y,float r,Paint p){item("circle",p,v(x,y,r));}
 public void drawPath(Path path,Paint p){item("path",p,path.signature());}
 public void drawText(String s,float x,float y,Paint p){item("text",p,s+v(x,y));}
 public void drawArc(float l,float t,float r,float b,float start,float sweep,boolean center,Paint p){item("arc",p,v(l,t,r,b,start,sweep));}
 public void drawArc(RectF r,float start,float sweep,boolean center,Paint p){drawArc(r.left,r.top,r.right,r.bottom,start,sweep,center,p);}
}
JAVA
# Stubs supply the stable style identity constants. The actual Tessellation engine is compiled below.
cat >"$t/src/com/halftonestudio/VinylStyleSuite.java" <<'JAVA'
package com.halftonestudio;
public class VinylStyleSuite{public static final int LOW_POLY=0,GLASS=1,TSP=2,LINOCUT=3,TRUCHET=4,TOPOGRAPHIC=6,RIDGE=7,BANKNOTE=9,TESSELLATION_MOTIF=10,
 MARQUETRY=11,FINGERPRINT=12,CONNECTED_LACE=13,REDUCTION_WOODCUT=14,WOVEN_RIBBON=15,SCRATCHBOARD=16,DELAUNAY_WIREFRAME=17,CONTINUOUS_SCRIBBLE=18,STRING_ART=19,KINTSUGI=20,WOODGRAIN=21,CIRCUIT_TRACE=22,CELTIC_BRAID=23,TORN_COLLAGE=24,ARCHITECTURAL=25,PRISMATIC_DIAGONAL=26;
 public static boolean fullyInside(float[] p,StyleKit.Source s){return true;}
 public static java.util.List<float[]> subjectLoops(StyleKit.Source s){return new java.util.ArrayList<float[]>();}}
JAVA
cat >"$t/src/com/halftonestudio/VinylEngine.java" <<'JAVA'
package com.halftonestudio;
public class VinylEngine{public static final int ROUND=0,SQUARE=1,ELLIPSE=2,DIAMOND=3,DASH=4,LINE=5,HEX=6,WAVE=8,HATCH=9,CROSS=10,STAR=11,STRAIGHT=12,CONTOUR=13,CROSSHATCH=14,ZIGZAG=16,BROKEN=17,TRIANGLE=21,OCTAGON=22,ROUNDED_SQUARE=23,TEARDROP=24,HEART=25,CAPSULE=26,ASCII=27;}
JAVA
cat >"$t/src/com/halftonestudio/ContinuousLineModes.java" <<'JAVA'
package com.halftonestudio;
public class ContinuousLineModes{public enum LineStyle{SPIRAL,ADAPTIVE_HILBERT,GOSPER,PEANO,WUNDERLICH,MOORE,COIL}}
JAVA
cat >"$t/src/com/halftonestudio/ThumbnailJVMTest.java" <<'JAVA'
package com.halftonestudio;
import android.content.Context;import android.graphics.Canvas;
import java.security.MessageDigest;import java.nio.charset.StandardCharsets;
import java.util.HashSet;
public class ThumbnailJVMTest{
 public static void main(String[] args)throws Exception{
  if(StudioThumbnailRegistry.allKeys().size()!=76)throw new AssertionError("Count");
  java.util.HashMap<String,String> sig=new java.util.HashMap<>();
  for(String key:StudioThumbnailRegistry.allKeys()){
   StudioStylePreview view=new StudioStylePreview(new Context(),key);
   Canvas c=new Canvas();view.onDraw(c);
   String s=c.log.toString();if(s.length()<100)throw new AssertionError("Empty graphic "+key);
   MessageDigest md=MessageDigest.getInstance("SHA-256");
   byte[] hash=md.digest(s.getBytes(StandardCharsets.UTF_8));
   StringBuilder hex=new StringBuilder();for(byte b:hash)hex.append(String.format("%02x",b&255));
   String prior=sig.putIfAbsent(hex.toString(),key);if(prior!=null)throw new AssertionError("Duplicate vector preview: "+prior+" and "+key);
  }
  try{new StudioStylePreview(new Context(),"unknown.thumb");throw new AssertionError("Missing key allowed");}
  catch(IllegalArgumentException ok){}
  for(int i=0;i<22;i++){
   String key=StudioThumbnailRegistry.tessellationKey(i);
   if(StudioThumbnailRegistry.tessellationVariant(key)!=i)throw new AssertionError(key);
  }
  System.out.println("PASS 76 unique, nonblank deterministic vector signatures; unknown keys rejected");
 }
}
JAVA
cat >"$t/src/com/halftonestudio/PaletteContractTest.java" <<'JAVA'
package com.halftonestudio;
import android.content.Context;
import android.graphics.Canvas;
import java.util.*;
public final class PaletteContractTest {
 private static void eq(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
 private static void family(String[] keys,int expected,boolean strictCmyk,boolean strictMono) {
  eq(keys.length==expected,"Family count "+keys.length+" expected "+expected);
  Set<String> signatures=new HashSet<>();
  for(String key:keys){
   int[] colors=StudioThumbnailPalette.colors(key);
   eq(colors.length==5,"Missing colors: "+key);
   if(!strictMono)eq(signatures.add(Arrays.toString(colors)),"Duplicate palette: "+key);
   Canvas c=new Canvas();new StudioStylePreview(new Context(),key).onDraw(c);
   for(String draw:c.log.toString().split(";")){
    if(draw.length()==0)continue;
    String[] parts=draw.split(":",4);
    eq(parts.length>=3,"Malformed preview draw: "+key);
    int ink=Integer.parseInt(parts[1]);
    if(strictCmyk)eq(StudioThumbnailPalette.isCmyk(ink),"Non-CMYK shape color: "+key+" "+ink);
    if(strictMono)eq(ink==StudioThumbnailPalette.BLACK || ink==StudioThumbnailPalette.WHITE,
      "Single-line color is not B/W: "+key+" "+ink);
   }
   if(strictMono){
    eq(StudioThumbnailPalette.background(key)==StudioThumbnailPalette.WHITE,"Single not white: "+key);
    for(int color:colors)eq(color==StudioThumbnailPalette.BLACK,"Single not black: "+key);
   }
   if(strictCmyk){
    eq(StudioThumbnailPalette.background(key)==StudioThumbnailPalette.WHITE,"Shape paper is not white: "+key);
    for(int color:colors)eq(StudioThumbnailPalette.isCmyk(color),"Shape ink is not CMYK: "+key);
   }
  }
 }
 public static void main(String[] args){
  family(StudioThumbnailRegistry.MULTILAYER,13,false,false);
  family(StudioThumbnailRegistry.SHAPES,15,true,false);
  family(StudioThumbnailRegistry.LINES,8,false,false);
  family(StudioThumbnailRegistry.SINGLE,7,false,true);
  family(StudioThumbnailRegistry.ART,11,false,false);
  Set<String> tessPalettes=new HashSet<>();
  for(int i=0;i<22;i++){
   String key=StudioThumbnailRegistry.tessellationKey(i);
   int[] ink=StudioThumbnailPalette.colors(key);
   eq(tessPalettes.add(Arrays.toString(ink)),"Repeated tessellation palette "+i);
   Canvas c=new Canvas();new StudioStylePreview(new Context(),key).onDraw(c);
   eq(c.log.length()>100,"Empty tessellation "+i);
  }
  eq(StudioThumbnailRegistry.newStyle(VinylStyleSuite.PRISMATIC_DIAGONAL)
    .equals("multilayer.prismatic_diagonal"),"Prismatic migrated preview key");
  eq(StudioThumbnailRegistry.allKeys().size()==76,"Registry count");
  System.out.println("PASS RC151 individual palettes, CMYK-only Shapes, pure black/white Single Line, 76 thumbnails");
 }
}
JAVA
# Compile production tessellation tiling primitives, production thumbnail registry and preview, and fake Android drawing contracts.
compile_java(){
  if command -v javac >/dev/null 2>&1; then javac -J-Xmx700m "$@"
  else java -Xmx700m -m jdk.compiler/com.sun.tools.javac.Main "$@"
  fi
}
compile_java -sourcepath "$t/src:app/src/main/java" -d "$t/classes" \
  "$t/src/android/content/Context.java" "$t/src/android/view/View.java" "$t/src/android/graphics/Typeface.java" \
  "$t/src/android/graphics/RectF.java" "$t/src/android/graphics/Paint.java" "$t/src/android/graphics/Path.java" \
  "$t/src/android/graphics/Canvas.java" "$t/src/com/halftonestudio/VinylStyleSuite.java" \
  "$t/src/com/halftonestudio/VinylEngine.java" "$t/src/com/halftonestudio/ContinuousLineModes.java" \
  app/src/main/java/com/halftonestudio/StyleKit.java \
  app/src/main/java/com/halftonestudio/TessellationExtendedLayouts.java \
  app/src/main/java/com/halftonestudio/TessellationMotifEngine.java \
  app/src/main/java/com/halftonestudio/StudioThumbnailRegistry.java \
  app/src/main/java/com/halftonestudio/StudioThumbnailPalette.java \
  app/src/main/java/com/halftonestudio/StudioStylePreview.java \
  "$t/src/com/halftonestudio/ThumbnailJVMTest.java" \
  "$t/src/com/halftonestudio/PaletteContractTest.java"
java -Xmx800m -cp "$t/classes" com.halftonestudio.ThumbnailJVMTest
java -Xmx800m -cp "$t/classes" com.halftonestudio.PaletteContractTest

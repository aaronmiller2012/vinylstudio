package com.halftonestudio;
public final class ArtworkDocumentExport {
 public interface Drawer{void draw(android.graphics.Canvas c);}
 public static final class Page {
  public final float widthMm,heightMm;public final Drawer drawing;
  public Page(float w,float h,Drawer drawer){widthMm=w;heightMm=h;drawing=drawer;}
 }
}

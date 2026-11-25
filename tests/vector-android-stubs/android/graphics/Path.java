package android.graphics;
public class Path {
 public enum FillType {WINDING, EVEN_ODD}
 public FillType fillType = FillType.WINDING;
 public int moves,lines,closes,quads,cubics;
 public void setFillType(FillType t){fillType=t;}
 public void moveTo(float x,float y){moves++;}
 public void lineTo(float x,float y){lines++;}
 public void quadTo(float x1,float y1,float x2,float y2){quads++;}
 public void cubicTo(float x1,float y1,float x2,float y2,float x3,float y3){cubics++;}
 public void transform(Matrix m){}
 public void close(){closes++;}
}

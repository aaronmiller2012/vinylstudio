package android.graphics;
public class Paint {
 public static final int ANTI_ALIAS_FLAG=1;public enum Style{FILL,STROKE}
 public Paint(int flags){} public void setColor(int c){} public void setTextSize(float v){} public void setTypeface(Typeface t){}
 public void setStyle(Style s){}public void setStrokeWidth(float f){}
 public enum Align{LEFT,RIGHT} public void setTextAlign(Align a){}
 public float measureText(String s){return s.length()*5.6f;}
}

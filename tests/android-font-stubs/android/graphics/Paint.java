package android.graphics;
public class Paint {
    public static final int ANTI_ALIAS_FLAG=1;
    public enum Style { FILL }
    private java.awt.Font font=new java.awt.Font("SansSerif",java.awt.Font.BOLD,100);
    private final java.awt.font.FontRenderContext frc=new java.awt.font.FontRenderContext(null,true,true);
    public Paint(int flags){}
    public void setTypeface(Typeface f){font=new java.awt.Font("SansSerif",f.weight==Typeface.BOLD?java.awt.Font.BOLD:java.awt.Font.PLAIN,font.getSize());}
    public void setTextSize(float pixels){font=font.deriveFont(pixels);}
    public void setStyle(Style s){}
    public void getTextPath(String text,int start,int end,float x,float y,Path path){
        if (Boolean.getBoolean("vinyl.test.emptyAndroidFont")) return;
        java.awt.font.GlyphVector v=font.createGlyphVector(frc,text.substring(start,end));
        path.outline=v.getOutline(x,y);
    }
    public float measureText(String text){
        return (float)font.createGlyphVector(frc,text).getGlyphPosition(text.length()).getX();
    }
}

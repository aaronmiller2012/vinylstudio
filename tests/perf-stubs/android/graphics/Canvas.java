package android.graphics;
public final class Canvas {
    public int draws;
    public void drawRect(float l,float t,float r,float b,Paint paint){if(paint.getShader()==null)throw new AssertionError();draws++;}
}

package android.graphics;
public final class Paint {
    public static final int FILTER_BITMAP_FLAG=2;
    private Shader shader;
    public Paint(int flags){}
    public void setFilterBitmap(boolean enabled){}
    public void setAntiAlias(boolean enabled){}
    public void setShader(Shader value){shader=value;}
    public Shader getShader(){return shader;}
}

package android.graphics;
public final class Matrix {
    public void reset(){}
    public void setScale(float x,float y){if(x<=0||y<=0)throw new AssertionError();}
}

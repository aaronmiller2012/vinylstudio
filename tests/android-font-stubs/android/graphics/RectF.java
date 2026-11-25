package android.graphics;
public class RectF {
    public void set(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}
    public float left,top,right,bottom;
    public float width(){return right-left;}
    public float height(){return bottom-top;}
}

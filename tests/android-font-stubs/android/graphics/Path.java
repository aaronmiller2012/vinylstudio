package android.graphics;
public class Path {
    java.awt.Shape outline;
    public void computeBounds(RectF rect,boolean exact){
        if(outline==null) {rect.left=rect.right=rect.top=rect.bottom=0f;return;}
        java.awt.geom.Rectangle2D bounds=outline.getBounds2D();
        rect.left=(float)bounds.getMinX();rect.top=(float)bounds.getMinY();
        rect.right=(float)bounds.getMaxX();rect.bottom=(float)bounds.getMaxY();
    }
}

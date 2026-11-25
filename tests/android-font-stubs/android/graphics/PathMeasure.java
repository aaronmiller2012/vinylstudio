package android.graphics;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;
/** Test-only approximation of Android PathMeasure using a flattened AWT path. */
public class PathMeasure {
    private final List<float[]> contours=new ArrayList<>();
    private int current=0;
    public PathMeasure(Path path,boolean forceClosed){
        if(path.outline==null)return;
        ArrayList<Float> one=new ArrayList<>();
        double[] point=new double[6];
        PathIterator it=path.outline.getPathIterator(null,.24);
        while(!it.isDone()){
            int kind=it.currentSegment(point);
            if(kind==PathIterator.SEG_MOVETO || kind==PathIterator.SEG_CLOSE){
                add(one,forceClosed);one.clear();
            }
            if(kind==PathIterator.SEG_MOVETO || kind==PathIterator.SEG_LINETO){one.add((float)point[0]);one.add((float)point[1]);}
            it.next();
        }
        add(one,forceClosed);
    }
    private void add(ArrayList<Float> pts,boolean closed){
        if(pts.size()<6)return;
        int n=pts.size();
        if(Math.hypot(pts.get(0)-pts.get(n-2),pts.get(1)-pts.get(n-1))<.0001){pts.remove(n-1);pts.remove(n-2);}
        float[] a=new float[pts.size()];for(int i=0;i<a.length;i++)a[i]=pts.get(i);
        contours.add(a);
    }
    private float[] current(){return current<contours.size()?contours.get(current):null;}
    public float getLength(){
        float[] c=current();if(c==null)return 0;
        float result=0f;for(int i=0;i<c.length;i+=2){int j=(i+2)%c.length;result+=Math.hypot(c[j]-c[i],c[j+1]-c[i+1]);}
        return result;
    }
    public boolean getPosTan(float dist,float[] pos,float[] tan){
        float[] c=current();if(c==null||pos==null)return false;
        float remain=dist;
        for(int i=0;i<c.length;i+=2){int j=(i+2)%c.length;
            float dx=c[j]-c[i],dy=c[j+1]-c[i+1];float d=(float)Math.hypot(dx,dy);
            if(remain<=d || i==c.length-2){float t=d==0?0:Math.max(0,Math.min(1,remain/d));
                pos[0]=c[i]+dx*t;pos[1]=c[i+1]+dy*t;
                if(tan!=null){tan[0]=dx/d;tan[1]=dy/d;}
                return true;}
            remain-=d;
        }
        return false;
    }
    public boolean nextContour(){if(current+1>=contours.size())return false;current++;return true;}
}

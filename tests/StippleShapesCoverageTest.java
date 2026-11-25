import com.halftonestudio.*;
import java.util.*;
public class StippleShapesCoverageTest {
    static void ok(boolean b,String message){if(!b)throw new AssertionError(message);}
    static double signed(float[] a){double v=0;for(int i=0;i<a.length;i+=2){int j=(i+2)%a.length;v+=(double)a[i]*a[j+1]-(double)a[j]*a[i+1];}return v*.5;}
    static boolean crossing(float[] a){int n=a.length/2;for(int i=0;i<n;i++)for(int j=i+2;j<n;j++){
        if(i==0&&j==n-1)continue;
        int ii=(i+1)%n,jj=(j+1)%n;
        if(intersects(a[i*2],a[i*2+1],a[ii*2],a[ii*2+1],a[j*2],a[j*2+1],a[jj*2],a[jj*2+1]))return true;
    }return false;}
    static double cross(double ax,double ay,double bx,double by,double cx,double cy){return (bx-ax)*(cy-ay)-(by-ay)*(cx-ax);}
    static boolean intersects(float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy){
        double a=cross(ax,ay,bx,by,cx,cy),b=cross(ax,ay,bx,by,dx,dy),c=cross(cx,cy,dx,dy,ax,ay),d=cross(cx,cy,dx,dy,bx,by);
        return a*b<-.000001&&c*d<-.000001;
    }
    static int[] picture(int w,int h){int[] p=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){
        if(x<12||x>=w-12||y<12||y>=h-12){p[y*w+x]=0xffffffff;continue;}
        // source colors alternate, but all the interior needs candidate coverage
        p[y*w+x]=(x<w/2?0xff836e8e:0xff795442);
    }return p;}
    public static void main(String[] args){
        int w=140,h=170;int[] p=picture(w,h);
        MultiColorStippleEngine.Options base=new MultiColorStippleEngine.Options();
        base.layerCount=2;base.colors=new int[]{0xff836e8e,0xff795442};base.widthMm=110;
        base.detail=100;base.draft=true;base.overlap=MultiColorStippleEngine.OVERPRINT;
        MultiColorStippleEngine.Result b=MultiColorStippleEngine.generate(p,w,h,base);
        ok(b.dots.size()>450,"sparse subject field: "+b.dots.size());
        int left=0,right=0;for(var dot:b.dots){if(dot.x<w/2)left++;else right++;}
        ok(left>100&&right>100,"incomplete colored regions: "+left+" / "+right);
        for(int style=0;style<MultiColorStippleEngine.SHAPE_NAMES.length;style++){
            MultiColorStippleEngine.Options o=base.copy();o.shape=style;
            var r=MultiColorStippleEngine.generate(p,w,h,o);
            ok(r.accepted>400,"no fill for shape "+style);
            for(var part:r.pieces){ok(part.outer.length>=6,"short polygon");
                ok(Math.abs(signed(part.outer))>1e-5,"zero-area polygon " +style);
                ok(!crossing(part.outer),"self intersection " +style);
                for(int i=0;i<part.outer.length;i+=2){
                    ok(part.outer[i]>=0&&part.outer[i]<=w&&part.outer[i+1]>=0&&part.outer[i+1]<=h,"outside artboard " +style);
                }
            }
            System.out.println("SHAPE="+MultiColorStippleEngine.SHAPE_NAMES[style]+" dots="+r.accepted);
        }
        // Disabled and excluded regions never receive added coverage.
        Arrays.fill(p,0x00ffffff);
        ok(MultiColorStippleEngine.generate(p,w,h,base).accepted==0,"background spawned stipples");
        System.out.println("PASS stipple shape silhouettes / coverage / empty background");
    }
}

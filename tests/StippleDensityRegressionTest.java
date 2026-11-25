import com.halftonestudio.*;
import java.util.*;
/** Copyright-free synthetic warm portrait-like image. Never bundles the user's actual photograph. */
public final class StippleDensityRegressionTest {
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static int[] portrait(int w,int h){
        int[] p=new int[w*h];Arrays.fill(p,0xfffaf9f6);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double nx=(x-w*.5)/(w*.39),ny=(y-h*.49)/(h*.47);
            if(nx*nx+ny*ny>1.0)continue;
            int px=0xffe0a8a0;
            if(y<h*.26)px=0xff27262e;
            else if(y>h*.67)px=0xff453238;
            else if(x<w*.31||x>w*.70)px=0xff9b5d67;
            else if(y>h*.50&&y<h*.64)px=0xffb76f84;
            else if((x>w*.38&&x<w*.44||x>w*.58&&x<w*.64)&&y>h*.32&&y<h*.40)px=0xff241e27;
            p[y*w+x]=px;
        }
        return p;
    }
    static void clear(List<MultiColorStippleEngine.Dot> dots){
        for(int i=0;i<dots.size();i++)for(int j=0;j<i;j++){
            var a=dots.get(i);var b=dots.get(j);
            if(a.layer==b.layer)continue;
            float dx=a.x-b.x,dy=a.y-b.y;
            check(dx*dx+dy*dy>=(a.r+b.r-.001f)*(a.r+b.r-.001f),"cross-layer overlap");
        }
    }
    public static void main(String[] args){
        int w=262,h=350;int[] image=portrait(w,h);
        var o=new MultiColorStippleEngine.Options();o.widthMm=152.4f;o.layerCount=5;o.detail=100;o.minGapMm=0;
        o.overlap=MultiColorStippleEngine.SPACED;o.draft=true;
        var first=MultiColorStippleEngine.generate(image,w,h,o);
        System.out.println("DRAFT active="+first.accepted+" droppedBySpacing="+first.skippedSpace);
        check(first.accepted>550,"max preview remains sparse despite available source areas");
        clear(first.dots);
        o.draft=false;var full=MultiColorStippleEngine.generate(image,w,h,o);
        System.out.println("EXPORT active="+full.accepted+" droppedBySpacing="+full.skippedSpace);
        check(full.accepted>first.accepted*2,"export quality does not exceed draft");
        clear(full.dots);
        o.density[2]=0;o.enabled[4]=false;o.draft=true;
        var filtered=MultiColorStippleEngine.generate(image,w,h,o);
        for(var d:filtered.dots)check(d.layer!=2 && d.layer!=4,"disabled/zero-weight layer generated a dot");
        o.draft=false;o.density[2]=1;o.enabled[4]=true;
        for(int mode=0;mode<4;mode++){
            o.overlap=mode;
            var res=MultiColorStippleEngine.generate(image,w,h,o);
            check(res.accepted>900,"mode "+mode+" has sparse full-resolution output");
            if(mode<2)clear(res.dots);
            for(var part:res.pieces)for(float f:part.outer)check(Float.isFinite(f),"nonfinite cut path");
            System.out.println("MODE="+mode+" active="+res.accepted+" polygons="+res.pieces.size());
        }
        System.out.println("PASS_STIPPLE_DENSITY_RC8");
    }
}

import com.halftonestudio.*;
import java.util.*;
public final class EightColorStippleTest {
    static void yes(boolean b,String message){if(!b)throw new AssertionError(message);}
    static int[] picture(int w,int h){int[] p=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){
        int r=(int)(255*x/(float)w),g=(int)(255*y/(float)h),b=(int)(120+100*Math.sin(x*.08));
        p[y*w+x]=0xff000000|(r<<16)|(g<<8)|Math.min(255,b);}
        return p;
    }
    static boolean collides(List<MultiColorStippleEngine.Dot> dots){for(int i=0;i<dots.size();i++)for(int j=0;j<i;j++){
        var a=dots.get(i);var b=dots.get(j);if(a.layer==b.layer)continue;
        float dx=a.x-b.x,dy=a.y-b.y;if(dx*dx+dy*dy<(a.r+b.r-.001f)*(a.r+b.r-.001f))return true;
        }return false;
    }
    public static void main(String[] args){int w=140,h=160;int[] pic=picture(w,h);
        for(int overlap=0;overlap<=3;overlap++){
            MultiColorStippleEngine.Options o=new MultiColorStippleEngine.Options();o.layerCount=8;
            o.widthMm=100;o.detail=50;o.overlap=overlap;o.draft=false;
            o.colors=new int[]{0xff141414,0xffdb315e,0xfff0bd35,0xff1387b7,0xff5e4cb3,0xff639c41,0xffc87924,0xffcec1b0};
            o.assignment=MultiColorStippleEngine.ERROR_DIFFUSION;
            MultiColorStippleEngine.Result res=MultiColorStippleEngine.generate(pic,w,h,o);
            yes(res.accepted>0,"mode "+overlap+" no dots");
            yes(res.pieces.size()>0,"mode "+overlap+" no pieces");
            if(overlap==0||overlap==1)yes(!collides(res.dots),"mode "+overlap+" colliding dots");
            for(var part:res.pieces){yes(part.outer.length>=6,"empty path");for(float v:part.outer)yes(Float.isFinite(v),"nonfinite path");}
            System.out.println("MODE="+overlap+" "+res.report());
            TessellationEngine.Options t=new TessellationEngine.Options();t.style=TessellationEngine.STIPPLE;
            t.layerCount=8;t.widthMm=100;t.colors=o.colors;t.detail=50;t.stippleOptions=o;
            TessellationEngine.Result wrap=TessellationEngine.generate(pic,w,h,t);
            String svg=wrap.combinedSvg();
            yes(svg.contains("swatch-"),"named groups missing");
            yes(svg.contains("mm\""),"missing physical units");
            yes(svg.split("<g id=",-1).length-1==8,"not eight groups");
            for(int i=0;i<8;i++){
                String indiv=wrap.svg(i);yes(indiv.contains("swatch-"),"group absent");yes(indiv.contains("<svg"),"SVG absent");}
        }
        int[] white=new int[140*160];Arrays.fill(white,0x00ffffff);
        MultiColorStippleEngine.Options blank=new MultiColorStippleEngine.Options();blank.layerCount=8;
        yes(MultiColorStippleEngine.generate(white,140,160,blank).dots.size()==0,"transparent spawned dots");
        for(int i=0;i<white.length;i++)white[i]=0xffffffff;
        // Pure white must not create artificial vinyl when the source has no pigment.
        yes(MultiColorStippleEngine.generate(white,140,160,blank).dots.size()==0,"white spawned dots");
        MultiColorStippleEngine.Options off=new MultiColorStippleEngine.Options();off.layerCount=4;
        off.enabled[1]=false;off.density[3]=0;
        var no=MultiColorStippleEngine.generate(pic,140,160,off);
        for(var d:no.dots)yes(d.layer!=1&&d.layer!=3,"disabled swatch made cut");
        System.out.println("EIGHT_COLOR_STIPPLE_PASS");
    }
}

import com.halftonestudio.*;
import java.util.*;
/** Source-only invariants; no user's original portrait is included in release archives. */
public final class StipplePhotoReleaseTest {
    static void ok(boolean v,String msg){if(!v)throw new AssertionError(msg);}
    static int[] testPhoto(int w,int h){
        int[] pixels=new int[w*h];Arrays.fill(pixels,0xfffaf9f6);
        int[] colors={0xff24232a,0xff4a3230,0xff715552,0xff996e64,0xffc48e7e,0xffdcad9d,0xffefd1b6,0xffabc2a9};
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double nx=(x-w*.5)/(w*.44),ny=(y-h*.5)/(h*.47);
            if(nx*nx+ny*ny>1)continue;
            int ix=Math.min(7,Math.max(0,(int)(8.0*(x/(double)w + y/(double)h)*.50)));
            if(y<h*.23)ix=0;if(y>h*.66)ix=1;
            pixels[y*w+x]=colors[ix];
        }
        return pixels;
    }
    public static void main(String[] args){
        int w=260,h=340;int[] src=testPhoto(w,h);
        int[] photo=StipplePhotoPalette.derive(src,w,h,8,0xfffaf9f6);
        System.out.println("PHOTO COLORS "+Arrays.toString(photo));Set<Integer> unique=new HashSet<>();for(int c:photo){ok(c!=0xff808080,"missing photo cluster");unique.add(c);}
        ok(unique.size()>=5,"palette collapsed");
        var o=new MultiColorStippleEngine.Options();
        ok(o.overlap==MultiColorStippleEngine.OVERPRINT,"overprint not standard");
        ok(o.assignment==MultiColorStippleEngine.NEAREST,"nearest not standard");
        o.layerCount=8;o.detail=100;o.colors=photo;o.widthMm=152.4f;
        o.draft=true;var draft=MultiColorStippleEngine.generate(src,w,h,o);
        ok(draft.accepted>1000,"draft still sparse on colored photo");
        o.draft=false;var full=MultiColorStippleEngine.generate(src,w,h,o);
        ok(full.accepted>draft.accepted*2,"full resolution did not add useful dots");
        int[] counts=new int[8];for(var d:full.dots)counts[d.layer]++;
        for(int c:counts)ok(c<=4400,"layer exceeds path budget");
        var t=new TessellationEngine.Options();t.style=TessellationEngine.STIPPLE;
        t.layerCount=8;t.detail=100;t.colors=photo;t.widthMm=152.4f;t.stippleOptions=o;
        var art=TessellationEngine.generate(src,w,h,t);
        for(int i=0;i<8;i++){
            String svg=art.svg(i);
            ok(svg.contains("mm\""),"SVG lacks physical units");
            ok(art.counts[i]<=4400,"Cricut limit exceeded");
            ok(svg.contains("swatch-"),"missing named group");
        }
        // An old project's explicitly chosen mode must still be supported.
        o.overlap=MultiColorStippleEngine.SHARED;o.draft=true;
        ok(MultiColorStippleEngine.generate(src,w,h,o).accepted>0,"legacy saved mode broken");
        int[] empty=new int[w*h];Arrays.fill(empty,0x00ffffff);
        boolean rejected=false;
        try{StipplePhotoPalette.derive(empty,w,h,8,0xfffaf9f6);}catch(IllegalArgumentException e){rejected=true;}
        ok(rejected,"transparent background wrongly generated photo colors");
        System.out.println("PASS_PHOTO_STIPPLE_RC9 draft="+draft.accepted+" full="+full.accepted+" colors="+unique.size()+" maxPerLayer="+Arrays.stream(counts).max().orElse(0));
    }
}

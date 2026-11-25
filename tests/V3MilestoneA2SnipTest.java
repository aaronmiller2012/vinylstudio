package com.halftonestudio;
import java.util.*;

/** Deterministic adversarial Snip masks. Checks no lost visible pixel area;
 * all touching/non-manifold geometry must be found at final-cut preflight.
 */
public final class V3MilestoneA2SnipTest {
    static void ok(boolean b,String why){if(!b)throw new AssertionError(why);}
    static double area(float[] p){double x=0;for(int i=0;i<p.length;i+=2){int j=(i+2)%p.length;x+=(double)p[i]*p[j+1]-(double)p[j]*p[i+1];}return Math.abs(x/2);}
    static double area(List<RasterRegionClipper.Region> regions){double n=0;for(var p:regions){n+=area(p.outer);for(float[] hole:p.holes)n-=area(hole);}return n;}
    static List<RasterRegionClipper.Region> clip(boolean[] mask,int w,int h,float minFeature){
        int[] photo=new int[w*h];Arrays.fill(photo,0xff123456);
        float[] rect={0,0,w,0,w,h,0,h};
        return RasterRegionClipper.clip(rect,null,photo,mask,w,h,0,0,w,h,0,0,w,h,1f,minFeature);
    }
    static void invalid(){boolean[] mask=new boolean[16];int[] src=new int[16];
        float[] rect={0,0,4,0,4,4,0,4};
        for(int error=0;error<4;error++){
            boolean failed=false;
            try{RasterRegionClipper.clip(rect,null,src,error==0?new boolean[3]:mask,4,4,
                0,0,error==1?5:4,4,0,0,4,4,error==2?0:1f,error==3?Float.NaN:0);}
            catch(IllegalArgumentException ex){failed=true;}
            ok(failed,"Invalid raster bounds/scale accepted: "+error);
        }
        boolean strict=false;
        try{RasterRegionClipper.clipFishScale(rect,null,src,mask,4,4,
            0,0,4,4,0,0,4,4,.01f,3f);}
        catch(IllegalArgumentException ex){strict=true;}
        // With an all-transparent source there is no generated cut and no
        // strict feature bound to certify; use actual nonempty source below.
        if(!strict){Arrays.fill(src,0xff000000);Arrays.fill(mask,true);
            try{RasterRegionClipper.clipFishScale(rect,null,src,mask,4,4,
                0,0,4,4,0,0,4,4,.01f,3f);}
            catch(IllegalArgumentException ex){strict=true;}
        }
        ok(strict,"Strict minimum width silently clamped to a weaker kernel");
    }
    public static void main(String[] args){
        invalid();
        Random rand=new Random(0x4C595E2L);
        int cases=0;
        for(int i=0;i<600;i++){
            int w=5+rand.nextInt(17),h=5+rand.nextInt(17);
            boolean[] mask=new boolean[w*h];int visible=0;
            double density=.08+.84*(i%13)/12.0;
            for(int j=0;j<mask.length;j++){mask[j]=rand.nextDouble()<density;if(mask[j])visible++;}
            var result=clip(mask,w,h,0);
            ok(Math.abs(area(result)-visible)<1e-5,"Snip lost mask pixels at case "+i);
            for(var piece:result){
                ok(piece.outer.length>=6&&StyleKit.selfIntersections(piece.outer,1)==0,"Invalid Snip outer");
                for(float[] hole:piece.holes)ok(StyleKit.selfIntersections(hole,1)==0,"Invalid Snip hole");
            }
            cases++;
        }
        // Diagonal-only contact preserves two separate islands. The final
        // safety oracle must require explicit review of their touching edges.
        boolean[] diagonal=new boolean[16];diagonal[1*4+1]=true;diagonal[2*4+2]=true;
        var islands=clip(diagonal,4,4,0);
        ok(islands.size()==2 && area(islands)==2,"Diagonal Snip islands merged or vanished");
        StringBuilder content=new StringBuilder();
        for(var p:islands){content.append("<polygon points='");
            for(int k=0;k<p.outer.length;k+=2){if(k>0)content.append(' ');content.append(p.outer[k]).append(',').append(p.outer[k+1]);}
            content.append("'/>");}
        String svg="<svg xmlns='http://www.w3.org/2000/svg' width='4mm' height='4mm' viewBox='0 0 4 4'>"+content+"</svg>";
        FinalCutSheetSafety.Report warning=FinalCutSheetSafety.inspect("Snip.svg",svg,.30f);
        ok(warning.critical>0,"Point-touching Snip islands were treated as safe");
        System.out.println("PASS A2 Snip: "+cases+" adversarial random masks, pixel parity, nonmanifold contact reporting, bounds and strict width");
    }
}

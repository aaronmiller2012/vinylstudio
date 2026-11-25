package com.halftonestudio;

import java.util.Arrays;

/** Source/photo ownership, physical order, Snip, and final visible swatches. */
public final class OpaqueOverlaySwatchPlannerTest {
    private static void ok(boolean v,String why){if(!v)throw new AssertionError(why);}
    private static float[] rect(float x0,float y0,float x1,float y1){
        return new float[]{x0,y0,x1,y0,x1,y1,x0,y1};
    }
    private static int[] image(int w,int h,int color){
        int[] p=new int[w*h];Arrays.fill(p,color);return p;
    }
    private static int[] choose(int[] pixels,boolean[] valid,int w,int h,
                                float[][] marks,int[] colors){
        return OpaqueOverlaySwatchPlanner.choose(pixels,valid,w,h,0,0,w,h,
            marks,colors,colors.length,.65f);
    }
    private static void opaqueOrder(){
        int w=70,h=50;
        int[] colors={0xff111111,0xffe04438,0xff2f61df};
        float[][] shapes={rect(3,3,48,47),rect(26,3,67,47),null};
        int[] pixels=image(w,h,colors[0]);
        for(int y=3;y<47;y++)for(int x=3;x<67;x++){
            if(x>=26)pixels[y*w+x]=colors[2];
            else pixels[y*w+x]=colors[1];
        }
        int[] owners=choose(pixels,null,w,h,shapes,colors);
        ok(Arrays.equals(owners,new int[]{1,2,0}),
            "Upper blue sheet must cover the red sheet in overlapping samples: "+Arrays.toString(owners));
        // Change only photograph, not palette: lower red must win at overlap.
        for(int y=3;y<47;y++)for(int x=3;x<67;x++)
            pixels[y*w+x]=x<48?colors[1]:colors[2];
        int[] changed=choose(pixels,null,w,h,shapes,colors);
        ok(changed[0]>0&&changed[1]>0&&changed[0]!=changed[1],
            "Physical overlap lost separate cut sheets: "+Arrays.toString(changed));
        ok(colors[0]==0xff111111&&colors[1]==0xffe04438&&colors[2]==0xff2f61df,
            "Planner modified selected physical rolls");
    }
    private static void maskAndFlat(){
        int w=56,h=42;
        int[] colors={0xff333333,0xffdc3434,0xffd1d1d1,0xff313eaf};
        float[][] marks={rect(3,3,29,39),rect(23,3,53,39),null};
        int[] photo=image(w,h,colors[0]);
        int[] flat=choose(photo,null,w,h,marks,colors);
        ok(Arrays.equals(flat,new int[3]),"Flat foundation acquired unwanted cuts");
        boolean[] valid=new boolean[photo.length];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            photo[y*w+x]=x<28?colors[1]:colors[3];
            valid[y*w+x]=x>=28;
        }
        int[] masked=choose(photo,valid,w,h,marks,colors);
        ok(masked[0]!=1&&masked[1]!=1,
            "Masked red half contaminated photo/swatches: "+Arrays.toString(masked));
        ok(masked[0]==0||masked[1]==0||masked[0]!=masked[1],
            "Two overlapping physical marks assigned same cut sheet");
        int[] repeat=choose(photo,valid,w,h,marks,colors);
        ok(Arrays.equals(masked,repeat),"Non-deterministic photo matching");
    }
    private static void photoSensitivity(){
        int w=49,h=49;
        int[] colors={0xff222222,0xffd33e30,0xff3168cb};
        float[][] shapes={rect(0,0,w,h),null,null};
        int[] red=choose(image(w,h,colors[1]),null,w,h,shapes,colors);
        int[] blue=choose(image(w,h,colors[2]),null,w,h,shapes,colors);
        ok(Arrays.equals(red,new int[]{1,0,0}),"Red photo must choose red sheet: "+Arrays.toString(red));
        ok(Arrays.equals(blue,new int[]{2,0,0}),"Blue photo must choose blue sheet: "+Arrays.toString(blue));
    }
    private static void geometryAndVisibility(){
        int w=96,h=72;int[] photo=new int[w*h];
        int[] colors={0xff211f23,0xffead4b2,0xffb74a3d,0xff6e94ab,0xffece5ce};
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)
            photo[y*w+x]=x<w/3?colors[2]:x<2*w/3?colors[3]:colors[1];
        LayeredCellPortraitEngine.Options o=new LayeredCellPortraitEngine.Options();
        o.colors=colors.clone();o.layerCount=5;o.columns=12;o.widthMm=152.4f;
        o.minFeatureMm=.30f;o.gapMm=.28f;o.overprintKind=LayeredCellPortraitEngine.OVERPRINT_MIXED;
        o.registration=false;o.motifDepth=3;o.ornamentation=.82f;
        LayeredCellPortraitEngine.Result r=LayeredCellPortraitEngine.generate(photo,w,h,o);
        OpaqueOverlaySwatchPlanner.Visibility v=r.swatchVisibility;
        ok(v!=null&&v.subjectArea>0,"Missing finished-polygon visibility report");
        int sum=0;for(int i=0;i<o.layerCount;i++){
            ok(v.visibleArea[i]>=0&&v.visibleArea[i]<=v.cutArea[i],"Visibility/cut area inconsistent");
            sum+=v.visibleArea[i];
            String svg=r.svg(i);
            ok(svg.contains(String.format("#%06X",colors[i]&0xffffff)),
                "SVG sheet color differs from edited chip");
        }
        ok(sum==v.subjectArea,"Final visible shares must partition the subject");
        LayeredCellPortraitEngine.Result second=LayeredCellPortraitEngine.generate(photo,w,h,o);
        ok(Arrays.equals(second.swatchVisibility.visibleArea,v.visibleArea),"Visibility changed on re-render");
        ok(Arrays.equals(colors,o.colors),"Renderer reordered the user swatches");
    }
    public static void main(String[] args){
        opaqueOrder();System.out.println("PASS opaque upper-sheet precedence and photo-specific matching");
        maskAndFlat();photoSensitivity();System.out.println("PASS flat photo, Snip, perceptual color choice, distinct physical sheets, deterministic ownership");
        geometryAndVisibility();System.out.println("PASS SVG colors, actual clipped visibility and unchanged swatch IDs");
    }
}

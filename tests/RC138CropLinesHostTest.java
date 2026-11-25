package com.halftonestudio;

import java.util.Arrays;

/** Crop regression: keep actual cuttable line sections instead of discarding full ribbons. */
public final class RC138CropLinesHostTest {
    private static void check(boolean good,String msg){if(!good)throw new AssertionError(msg);}
    static int[] photo(int w,int h,boolean alphaCircle){
        int[] out=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double nx=(x+.5-w*.5)/(w*.5),ny=(y+.5-h*.5)/(h*.5);
            int r=55+Math.round(68f*x/w),g=39+Math.round(72f*y/h),b=38+Math.round(45f*x/w);
            if(y<h/5){r=44;g=40;b=37;}
            if(y>h*.36 && y<h*.43 && (x<w*.43 || x>w*.57)){r=25;g=22;b=29;}
            if(alphaCircle && nx*nx+ny*ny>1)out[y*w+x]=0;
            else out[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        return out;
    }
    static VinylEngine.Settings settings(int style,boolean classic,boolean circle){
        VinylEngine.Settings s=LineCoverageRC101Test.modern(style);
        s.widthMm=279.4f; s.minimumMm=.30f;s.marginMm=3f;
        s.circleCrop=circle;s.classicCmykLines=classic;s.registration=false;
        s.snips=new SnipMask();
        for(VinylEngine.Layer layer:s.layers)layer.spacing=3.2f;
        return s;
    }
    static void verifyInside(VinylEngine.Result result){
        VinylEngine.Settings s=result.settings;
        for(int i=0;i<result.shapes.size();i++)for(VinylEngine.Shape path:result.shapes.get(i)){
            check(VinylEngine.insideCutBoundary(path,s.widthMm,result.heightMm,s.marginMm,true),"escaped crop at layer "+i);
        }
        check(VinylEngine.validateExport(result)==null,"failed SVG export: "+VinylEngine.validateExport(result));
        LineCutDiagnostics.Report diag=LineCutDiagnostics.inspect(result);
        check(diag.complete && diag.selfIntersections==0 && diag.subMinimumWidthSamples==0,
            "unsafe crop line widths / self-intersections: "+diag.text(s.minimumMm,s.bridgeMm));
    }
    public static void main(String[] args){
        int w=96,h=96;int[] opaque=photo(w,h,false);
        int[] croppedAlpha=photo(w,h,true);
        int combinations=0;
        for(boolean classic:new boolean[]{false,true}){
            for(int style:new int[]{VinylEngine.LINE,VinylEngine.STRAIGHT,VinylEngine.HATCH,VinylEngine.WAVE}){
                VinylEngine.Result full=VinylEngine.generate(opaque,w,h,settings(style,classic,false));
                VinylEngine.Result crop=VinylEngine.generate(opaque,w,h,settings(style,classic,true));
                check(full.count()>0,"uncropped fixture has no ribbons: "+style);
                check(crop.count()>full.count()*.52,"crop erased too many ribbons: classic="+classic+" style="+style+" crop="+crop.count()+" full="+full.count());
                verifyInside(crop);
                check(crop.shapes.size()==4,"lost CMYK physical sheets");
                System.out.printf("Crop retention %s %s: %d/%d (%.1f%%)%n",classic?"classic":"modern",style,crop.count(),full.count(),100.0*crop.count()/full.count());
                combinations++;
            }
        }
        for(int style:new int[]{VinylEngine.CONTOUR,VinylEngine.CROSSHATCH,VinylEngine.ZIGZAG,
            VinylEngine.BROKEN,VinylEngine.FADE,VinylEngine.RINGS,VinylEngine.RAYS,VinylEngine.BEADED}){
            VinylEngine.Settings extra=settings(style,false,true);
            if(style==VinylEngine.BEADED){extra.masterSpacingMm=7f;for(VinylEngine.Layer l:extra.layers)l.spacing=7f;}
            VinylEngine.Result shape=VinylEngine.generate(opaque,w,h,extra);
            verifyInside(shape);
            check(shape.count()>0,"crop lost another line family "+style);
        }
        for(boolean classic:new boolean[]{false,true}){
            VinylEngine.Result cut=VinylEngine.generate(croppedAlpha,w,h,settings(VinylEngine.LINE,classic,true));
            verifyInside(cut);
            check(cut.count()>50,"transparent corner crop lost interior lines");
        }
        // Rectangular crop with changed aspect ratio must remain a full line-art input.
        int cw=75,ch=96;int[] rectangle=new int[cw*ch];
        for(int y=0;y<ch;y++)System.arraycopy(opaque,y*w+10,rectangle,y*cw,cw);
        for(boolean classic:new boolean[]{false,true}){
            VinylEngine.Result rect=VinylEngine.generate(rectangle,cw,ch,settings(VinylEngine.LINE,classic,false));
            check(rect.count()>150,"rectangular crop unexpectedly removed lines");
            check(VinylEngine.validateExport(rect)==null,"rectangular crop has invalid cut geometry");
        }
        // Helper never changes the historic non-circular path.
        check(LineCropBoundary.accepts(false,-100,-100,279.4f,279.4f,3,9),"noncircle geometry changed");
        check(!LineCropBoundary.accepts(true,3,3,279.4f,279.4f,3,.5f),"ellipse boundary ignored");
        System.out.println("PASS RC138 crop coverage: "+combinations+" classic/modern line styles, circular alpha/opaque and rectangular crops, SVG safety");
    }
}

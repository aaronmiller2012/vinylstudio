package com.halftonestudio;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipOutputStream;

/** New Star-only manufacturability policy, without reconfiguring other styles. */
public final class V3StarCutSafeHalftoneTest {
    private static void ok(boolean v,String why){if(!v)throw new AssertionError(why);}
    private static int[] photo(int w,int h){
        int[] pixels=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float nx=(x-w*.5f)/(w*.32f),ny=(y-h*.5f)/(h*.43f);
            boolean subject=nx*nx+ny*ny<1f;
            int tone=Math.min(240,Math.max(8,(int)(235-170f*y/(float)h-31*Math.sin(x*.09))));
            pixels[y*w+x]=subject?0xff000000|(tone<<16)|(tone<<8)|tone:0x00ffffff;
        }
        return pixels;
    }
    private static VinylEngine.Settings settings(float inches,float spacing,int style){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.pattern=style;s.mode=VinylEngine.CMYK;s.registration=false;
        s.widthMm=inches*25.4f;s.minimumMm=.254f;
        s.marginMm=3f;s.bridgeMm=.30f;s.shapeScale=.82f;s.masterSpacingMm=spacing;
        for(VinylEngine.Layer layer:s.layers)layer.spacing=spacing;
        return s;
    }
    private static void deterministicAreaDither(){
        for(float p:new float[]{.05f,.20f,.50f,.80f}){
            int keep=0;
            for(int y=0;y<100;y++)for(int x=0;x<100;x++){
                boolean a=VinylEngine.ShapeAreaResponse.keepPromotedStar(y,x,1,p);
                boolean b=VinylEngine.ShapeAreaResponse.keepPromotedStar(y,x,1,p);
                ok(a==b,"Dither not deterministic");
                if(a)keep++;
            }
            ok(Math.abs(keep/10000f-p)<.025f,"Area dithering distorts tones at "+p+": "+keep);
        }
        ok(!VinylEngine.ShapeAreaResponse.keepPromotedStar(0,0,0,0),"Zero tone still cut");
        ok(VinylEngine.ShapeAreaResponse.keepPromotedStar(0,0,0,1),"Full tone lost");
    }
    private static void validateActualShapes(){
        int[] pixels=photo(112,124);
        float min=VinylEngine.ShapeAreaResponse.starSafeRadius(.254f);
        ok(min>=.50f,"Star floor below 0.5 mm radius");
        for(float inches:new float[]{6f,11f,15f}){
            float spacing=Math.max(2.2f,2.794f*inches/11f);
            VinylEngine.Settings s=settings(inches,spacing,VinylEngine.STAR);
            VinylEngine.Result first=VinylEngine.generate(pixels,112,124,s);
            VinylEngine.Result repeated=VinylEngine.generate(pixels,112,124,s);
            int total=0;
            for(int i=0;i<s.layers.size();i++){
                List<VinylEngine.Shape> shapes=first.shapes.get(i);
                for(VinylEngine.Shape sh:shapes){
                    ok(!sh.circle&&sh.xy!=null&&sh.xy.length==20,"Unexpected Star shape");
                    double cx=0,cy=0;
                    for(int k=0;k<10;k++){cx+=sh.xy[2*k];cy+=sh.xy[2*k+1];}
                    cx/=10;cy/=10;
                    double outer=0;
                    for(int k=0;k<10;k+=2)outer+=Math.hypot(sh.xy[2*k]-cx,sh.xy[2*k+1]-cy)/5;
                    ok(outer+0.001>=min,"Unsafe miniature star: "+outer+" vs "+min);
                }
                total+=shapes.size();
                String firstSvg=VinylEngine.svg(first,i);
                ok(firstSvg.equals(VinylEngine.svg(repeated,i)),"Star SVG not deterministic");
                if(inches==11){
                    FinalCutSheetSafety.Report report=FinalCutSheetSafety.inspect("star.svg",firstSvg,.254f);
                    ok(!report.outsideArtboard&&report.critical==0&&report.narrow==0&&report.unverified==0,
                        "Unsafe Star export: "+report.status()+" narrow="+report.narrow+" unknown="+report.unverified);
                    ByteArrayOutputStream buffer=new ByteArrayOutputStream();
                    try(ZipOutputStream zip=new ZipOutputStream(buffer)){
                        CutSheetZipWriter out=new CutSheetZipWriter(zip,OutputDeviceProfile.GENERIC_SVG,.254f,false);
                        out.put("star.svg",firstSvg);out.finish();
                    }catch(java.io.IOException ex){throw new AssertionError("Verified Star sheet blocked",ex);}
                    ok(buffer.size()>1000,"Empty exported Star ZIP");
                }
            }
            ok(total>200,"Star portrait unexpectedly empty");
            System.out.printf(Locale.ROOT,"%d in Stars: %d cut motifs, min outer radius %.3f mm%n",(int)inches,total,min);
        }
        // Requesting physically impossible spacing must fail visibly, not
        // silently export a blank layer or output nonconforming stars.
        VinylEngine.Settings impossible=settings(11f,.85f,VinylEngine.STAR);
        boolean rejected=false;
        try{VinylEngine.generate(pixels,112,124,impossible);}catch(IllegalArgumentException ex){rejected=ex.getMessage().contains("Increase Spacing");}
        ok(rejected,"Uncuttable Star spacing silently accepted");
    }
    public static void main(String[] args){
        deterministicAreaDither();validateActualShapes();
        System.out.println("PASS Star cut-safe size floor, compensated tone, 6/11/15in, 11in dense ZIP, impossible settings");
    }
}

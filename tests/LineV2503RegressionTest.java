package com.halftonestudio;

/** Runtime checks exercising all line styles, four CMYK vinyl colors, and paper/alpha. */
public final class LineV2503RegressionTest {
    private static final int[] STYLES={5,8,9,12,13,14,15,20,16,17,18,19};
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static VinylEngine.Settings settings(){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();s.widthMm=70;
        s.marginMm=2;s.registration=false;s.masterSpacingMm=2.85f;
        s.minimumMm=.65f;s.bridgeMm=.4f;return s;
    }
    private static int[] filled(int w,int h,int c){int[] p=new int[w*h];java.util.Arrays.fill(p,c);return p;}
    public static void main(String[] args){
        final int w=68,h=68;int[] colorful=LineV2494SignatureFixture.photo(w,h,1,settings().paper);
        int[] paper=filled(w,h,settings().paper),white=filled(w,h,0xffffffff),alpha=filled(w,h,0);
        int checked=0;
        for(int style:STYLES){
            VinylEngine.Settings s=settings();s.pattern=style;
            VinylEngine.Result colored=VinylEngine.generate(colorful,w,h,s);
            for(int li=0;li<4;li++){
                int count=colored.shapes.get(li).size();
                check(count>0,"Missing CMYK channel "+li+" for style "+style);
                String svg=VinylEngine.svg(colored,li);
                check(svg.contains("<svg") && !svg.contains("<clipPath") && !svg.contains("<line "),
                    "Export must contain closed cut shapes, not strokes or clipping paths");
                check(VinylEngine.svgElementsForLayer(colored,li)<=5000,"Cricut element budget exceeded");
                checked++;
            }
            check(VinylEngine.validateExport(colored)==null,"Invalid CMYK SVG from style "+style);
            for(int[] empty:new int[][]{paper,white,alpha}){
                VinylEngine.Result generated=VinylEngine.generate(empty,w,h,s);
                check(generated.count()==0,"Style "+style+" invents "+generated.count()+" cuts on empty paper/alpha");
            }
        }
        // Variable Width is not silently identical to Contour when contour tracking is enabled.
        VinylEngine.Settings a=settings();a.pattern=VinylEngine.LINE;a.contourStrength=.88f;
        VinylEngine.Settings b=a.copy();b.pattern=VinylEngine.CONTOUR;
        VinylEngine.Result variable=VinylEngine.generate(colorful,w,h,a);
        VinylEngine.Result contour=VinylEngine.generate(colorful,w,h,b);
        check(!VinylEngine.svg(variable,3).equals(VinylEngine.svg(contour,3)),"Contour duplicates Variable Width");
        // Broken is deliberate, not automatically bridged to become Straight.
        VinylEngine.Settings broken=settings();broken.pattern=VinylEngine.BROKEN;
        VinylEngine.Settings straight=broken.copy();straight.pattern=VinylEngine.STRAIGHT;
        check(!VinylEngine.svg(VinylEngine.generate(colorful,w,h,broken),3)
            .equals(VinylEngine.svg(VinylEngine.generate(colorful,w,h,straight),3)),"Broken gaps lost");
        // A fully closed ring is two disjoint cut contours (inner paper opening and outer boundary),
        // never a self-crossing open strip with coincident endpoints.
        VinylEngine.Settings ring=settings();ring.pattern=VinylEngine.RINGS;
        VinylEngine.Result darkRing=VinylEngine.generate(filled(w,h,0xff101010),w,h,ring);
        boolean foundAnnulus=false;
        for(VinylEngine.Shape shape:darkRing.shapes.get(3)){
            if(shape.contours!=null && shape.contours.length==2){foundAnnulus=true;break;}
        }
        check(foundAnnulus,"Rings missing proper inner and outer closed cut contours");
        check(VinylEngine.validateExport(darkRing)==null,"Annular ring SVG invalid");
        System.out.println("PASS: "+checked+" CMYK style channels; 36 paper/white/alpha exclusions; distinct Contour/Broken; annular Rings; closed SVG exports");
    }
}

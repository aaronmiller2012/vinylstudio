import com.halftonestudio.MultiColorStippleEngine;
import com.halftonestudio.TessellationEngine;
import java.util.Arrays;

/** Small, real engine smoke case: an SVG must contain closed vectors, no bitmap payload. */
public final class StippleVectorExportSmokeTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        final int w=90,h=100;int[] pixels=new int[w*h];Arrays.fill(pixels,0xfffaf9f6);
        final int[] colors={0xff513729,0xffad7065,0xffe4b1a7};
        for(int y=12;y<h-12;y++)for(int x=9;x<w-9;x++)pixels[y*w+x]=colors[Math.min(2,(x*3)/w)];
        MultiColorStippleEngine.Options stipple=new MultiColorStippleEngine.Options();
        stipple.colors=colors.clone();stipple.layerCount=3;stipple.paper=0xfffaf9f6;
        stipple.widthMm=120;stipple.detail=60;stipple.draft=true;
        stipple.overlap=MultiColorStippleEngine.OVERPRINT;
        TessellationEngine.Options opts=new TessellationEngine.Options();
        opts.style=TessellationEngine.STIPPLE;opts.widthMm=120;
        opts.colors=colors.clone();opts.layerCount=3;opts.stippleOptions=stipple;
        TessellationEngine.Result result=TessellationEngine.generate(pixels,w,h,opts);
        check(!result.tiles.isEmpty(),"No vector dots were generated");
        int paths=0;
        for(int i=0;i<3;i++){
            String svg=result.svg(i);
            check(svg.startsWith("<svg "),"Not an SVG document");
            check(svg.contains("mm\" height=\""),"Missing physical dimensions");
            check(svg.contains("swatch-"),"SVG missing layer name");
            check(!svg.contains("<image")&&!svg.contains("data:image"),"Raster image embedded in SVG");
            check(svg.contains("<path fill-rule=\"evenodd\""),"Vector cut polygons missing");
            paths+=result.counts[i];
        }
        String combined=result.combinedSvg();
        check(combined.contains("swatch-")&&!combined.contains("<image"),"Combined SVG invalid");
        check(paths>0,"No cut paths");
        System.out.println("PASS: "+paths+" named, physical-mm vector cut polygons; no embedded bitmap");
    }
}

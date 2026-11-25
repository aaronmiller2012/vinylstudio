package com.halftonestudio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.zip.ZipOutputStream;

/** A: all vector geometry must fit; C: physically faithful bounded previews. */
public final class V3MilestoneC3Test {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static String svg(String shapes){
        return "<svg xmlns='http://www.w3.org/2000/svg' width='100mm' height='50mm' viewBox='0 0 100 50'>"+shapes+"</svg>";
    }
    private static void rejected(String svg) throws Exception{
        FinalCutSheetSafety.Report r=FinalCutSheetSafety.inspect("test.svg",svg,.254f);
        require(r.outsideArtboard,"Out-of-artboard geometry was not detected: "+svg+" status="+r.status()+" open="+r.openPaths+" report="+r.detail);
        try(ZipOutputStream out=new ZipOutputStream(new ByteArrayOutputStream())){
            CutSheetZipWriter writer=new CutSheetZipWriter(out,OutputDeviceProfile.GENERIC_SVG,.254f,true);
            try {writer.put("layer.svg",svg);throw new AssertionError("Out-of-board sheet exported");}
            catch(IOException ok){require(ok.getMessage().contains("artboard"),"Wrong rejection reason");}
        }
    }
    private static void physicalBounds() throws Exception {
        String valid=svg("<path fill='none' d='M 0 0 L 100 50'/>");
        FinalCutSheetSafety.Report ok=FinalCutSheetSafety.inspect("draw.svg",valid,.254f);
        require(!ok.outsideArtboard&&ok.openPaths==1,"Valid pen stroke was lost or rejected");
        rejected(svg("<path fill='none' d='M 10 10 L 105 10'/>"));
        rejected(svg("<line x1='50' y1='-0.06' x2='50' y2='10'/>"));
        rejected(svg("<g transform='translate(101 0)'><polyline fill='none' points='0,4 4,4'/></g>"));
        rejected(svg("<path d='M 10 10 L 20 10 L 20 20 Z'/><line x1='20' y1='20' x2='21' y2='-2'/>"));
        String near=svg("<line x1='0' y1='-0.049' x2='1' y2='0' />");
        require(!FinalCutSheetSafety.inspect("near.svg",near,.254f).outsideArtboard,
            "Round-off tolerance lost");
        System.out.println("PASS A open SVG/DXF strokes obey transformed artboard and 0.05-mm tolerance");
    }
    private static void coverage() {
        // A 1-pixel line is easy to miss by center-only 10x bilinear sampling.
        boolean[] thin=new boolean[100*100];
        for(int y=0;y<100;y++)thin[y*100+4]=true;
        BoundedBinaryMaskPreview.Result img=BoundedBinaryMaskPreview.compose(100,100,thin,10);
        require(img.width==10&&img.height==10,"Preview bound changed");
        for(int y=0;y<10;y++){
            int px=img.argb[y*10]&255;
            require(px>=228&&px<=231,"Thin 10%%-coverage ribbon vanished or was amplified: "+px);
            for(int x=1;x<10;x++)require((img.argb[y*10+x]&255)==255,"Ink leaked to unoccupied tile");
        }
        // Total covered area should be conserved to within 1/255 rounding.
        boolean[] stripe=new boolean[53*37];
        for(int y=0;y<37;y++)for(int x=0;x<53;x++)stripe[y*53+x]=(x+3*y)%7==0;
        BoundedBinaryMaskPreview.Result a=BoundedBinaryMaskPreview.compose(53,37,stripe,7);
        int sum=0;for(int p:a.argb)sum+=255-(p&255);
        double measured=sum/(255.0*a.argb.length);
        double source=0;for(boolean v:stripe)if(v)source++;
        source/=stripe.length;
        require(Math.abs(measured-source)<0.001,"Downscale lost ink coverage: "+measured+" vs "+source);
        require(Arrays.equals(a.argb,BoundedBinaryMaskPreview.compose(53,37,stripe,7).argb),"Preview non-deterministic");
        boolean[] blank=new boolean[100*100];
        for(int p:BoundedBinaryMaskPreview.compose(100,100,blank,10).argb)
            require(p==0xffffffff,"Blank pixels became ink");
        System.out.println("PASS C preview coverage of subpixel ribbons, blank masks and deterministic conservation");
    }
    public static void main(String[] args) throws Exception {
        physicalBounds();coverage();System.out.println("PASS v3 A/B/C3 cross-family gate");
    }
}

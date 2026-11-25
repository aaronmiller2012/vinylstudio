package com.halftonestudio;

/** Host integration: actual dense CMYK Round/Stars generator and final-sheet SVG oracle. */
public final class V3DenseRealShapesTest {
    public static void main(String[] args) throws Exception {
        int w=112,h=124;int[] photo=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int v=Math.max(6,Math.min(240,(int)(195-130.0*y/h-35.0*Math.sin(x*.14))));
            double nx=(x-w*.5)/(w*.32),ny=(y-h*.5)/(h*.43);
            photo[y*w+x]=nx*nx+ny*ny<1?0xff000000|(v<<16)|(v<<8)|v:0x00ffffff;
        }
        for(int style:new int[]{VinylEngine.ROUND,VinylEngine.STAR}){
            VinylEngine.Settings s=VinylEngine.Settings.defaults();
            s.pattern=style;s.mode=VinylEngine.CMYK;
            s.registration=false;s.widthMm=279.4f;s.minimumMm=.254f;
            s.marginMm=3f;s.bridgeMm=.30f;s.shapeScale=.82f;
            s.masterSpacingMm=2.794f;
            for(VinylEngine.Layer layer:s.layers)layer.spacing=s.masterSpacingMm;
            VinylEngine.Result r=VinylEngine.generate(photo,w,h,s);
            int total=0,unverified=0;
            for(int i=0;i<s.layers.size();i++){
                String svg=VinylEngine.svg(r,i);
                FinalCutSheetSafety.Report report=FinalCutSheetSafety.inspect("layer.svg",svg,.254f);
                total+=report.contours+report.circles;
                unverified+=report.unverified;
                if(report.contours+report.circles>0 && !(report.widthMm>0))throw new AssertionError("Invalid artboard");
                if(report.outsideArtboard)throw new AssertionError("Out of artboard: "+style);
                System.out.println("Style "+style+" layer "+i+" loops "+report.contours+" circles "+report.circles+" critical "+report.critical+" narrow "+report.narrow+" unknown "+report.unverified+" : "+report.detail);
            }
            if(total<6000)throw new AssertionError("Fixture unexpectedly sparse");
            if(style==VinylEngine.ROUND&&unverified!=0)
                throw new AssertionError("Valid round sheets should complete inspection");
            // Dense Stars may have too many legitimate narrow-point findings;
            // incomplete scans must stay unverified, never quietly certified.
            if(style==VinylEngine.STAR&&unverified==0)
                System.out.println("Stars completed all dense scans without budget exhaustion");
        }
    }
}

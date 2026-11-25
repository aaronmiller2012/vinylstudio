package com.halftonestudio;

import java.util.Arrays;

/** Release candidate regression: correct multi-layer vinyl geometry, SVG and crop-safe preflight. */
public final class LayeredCellReleaseCandidateTest {
    private static void require(boolean pass,String msg){if(!pass)throw new AssertionError(msg);}
    private static int[] photo(int w,int h){
        int[] rgb=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double face=Math.max(0,1-Math.pow((x-w*.52)/(w*.36),2)-Math.pow((y-h*.52)/(h*.46),2));
            int r=Math.max(0,Math.min(255,(int)(36+160*face+20*Math.sin(x*.08))));
            int g=Math.max(0,Math.min(255,(int)(42+96*face+24*Math.cos(y*.09))));
            int b=Math.max(0,Math.min(255,(int)(54+65*face+20*Math.sin((x+y)*.06))));
            if(y>h*.35&&y<h*.39&&x>w*.33&&x<w*.70)r=g=b=20;
            rgb[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        return rgb;
    }
    public static void main(String[] args){
        final int w=200,h=250;
        int[] picture=photo(w,h);
        int[] palette={0xff171923,0xff624353,0xffac7163,0xffedb692,0xffeedccc,0xff4a7fa8,0xffd1b64a,0xff746aa5};
        for(int paletteId=0;paletteId<ColorWorkspacePalette.TESS_NAMES.length;paletteId++)
            for(int count=2;count<=8;count++){
                int[] selected=ColorWorkspacePalette.select(ColorWorkspacePalette.tessPalette(paletteId),count);
                int[] arranged=ColorWorkspacePalette.arranged(ColorWorkspacePalette.tessPalette(paletteId),count);
                require(arranged.length==8,"Full preset retained");
                for(int i=0;i<count;i++)require(selected[i]==arranged[i],"Displayed palette preview matches real swatches");
                require(Arrays.stream(arranged).distinct().count()==8,"No duplicate future swatches");
            }
        for(int layers=2;layers<=8;layers++){
            LayeredCellPortraitEngine.Options o=new LayeredCellPortraitEngine.Options();
            o.layerCount=layers;o.colors=palette;o.columns=LayeredCellReleaseProfile.safeColumns(30,w,h,152.4f,.5f,.35f);
            o.registration=true;o.markStyle=RegistrationMarks.CIRCLE;o.markSizeMm=2.5f;
            o.widthMm=152.4f;o.minFeatureMm=.5f;
            LayeredCellPortraitEngine.Result result=LayeredCellPortraitEngine.generate(picture,w,h,o);
            require(result.visibleLayers()>=2,"Should show more than one populated layer: "+layers);
            require(result.columns*result.rows<=1200,"Cell cap");
            for(int i=0;i<layers;i++)if(result.pieceCounts[i]>0){
                String svg=result.svg(i);
                LayeredCellExportSafety.validate(result,i,svg);
                require(svg.contains("fill-rule=\"evenodd\""),"Compound evenodd rule");
                require(result.contourCounts[i]+4<=4800,"Contour budget");
            }
        }
        for(int family=0;family<=8;family++){
            LayeredCellPortraitEngine.Options o=new LayeredCellPortraitEngine.Options();
            o.colors=palette;o.layerCount=5;o.columns=18;o.family=family;
            o.widthMm=150;o.minFeatureMm=.5f;o.registration=true;
            LayeredCellPortraitEngine.Result result=LayeredCellPortraitEngine.generate(picture,w,h,o);
            for(int i=0;i<5;i++)if(result.pieceCounts[i]>0)LayeredCellExportSafety.validate(result,i,result.svg(i));
        }
        LayeredCellPortraitEngine.Options masked=new LayeredCellPortraitEngine.Options();
        masked.colors=palette;masked.layerCount=5;masked.columns=20;masked.widthMm=152.4f;
        masked.allowedPixels=new boolean[picture.length];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double ellipse=Math.pow((x-w*.5)/(w*.48),2)+Math.pow((y-h*.50)/(h*.48),2);
            masked.allowedPixels[y*w+x]=ellipse<1&&!(x>w*.4&&x<w*.62&&y>h*.46&&y<h*.63);
        }
        LayeredCellPortraitEngine.Result a=LayeredCellPortraitEngine.generate(picture,w,h,masked);
        LayeredCellPortraitEngine.Result b=LayeredCellPortraitEngine.generate(picture,w,h,masked);
        require(a.skippedCells>0&&a.pieces.size()>0,"Partial mask retains visible cells");
        for(int li=0;li<masked.layerCount;li++)if(a.pieceCounts[li]>0){
            String svg=a.svg(li);
            LayeredCellExportSafety.validate(a,li,svg);
            require(svg.equals(b.svg(li)),"Deterministic export for layer "+li);
        }
        for(LayeredCellPortraitEngine.Piece p:a.pieces){
            for(float[] ring:p.holes)require(ring.length>=6,"Valid hole");
            for(int i=0;i<p.outer.length;i+=2)require(Float.isFinite(p.outer[i])&&Float.isFinite(p.outer[i+1]),"Finite vector points");
        }
        LayeredCellPortraitEngine.Options bg=masked.copy();bg.allowedPixels=null;bg.lockOuterToLayer=0;
        LayeredCellPortraitEngine.Result backdrop=LayeredCellPortraitEngine.generate(picture,w,h,bg);
        require(backdrop.pieceCounts[0]>0,"Locked backdrop retained");
        int safe=LayeredCellReleaseProfile.safeColumns(80,100,600,150,.7f,.35f);
        int rows=Math.round(safe*600f/100f);
        require(safe*rows<=LayeredCellPortraitEngine.MAX_CELLS,"Portrait aspect density capped");
        boolean threw=false;
        try{LayeredCellReleaseProfile.safeColumns(40,100,100,14,2,.5f);}catch(IllegalArgumentException e){threw=true;}
        require(threw,"Uncuttable tiny artwork must be rejected");
        threw=false;
        try{LayeredCellExportSafety.validate(a,0,"<svg><clipPath/></svg>");}catch(IllegalArgumentException e){threw=true;}
        require(threw,"SVG clip path rejected");
        System.out.println("PASS: release candidate 2–8 color layers, 9 motif choices, masks/holes, background lock, metric SVG and density preflight");
    }
}

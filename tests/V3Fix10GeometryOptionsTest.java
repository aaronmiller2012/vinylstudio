package com.halftonestudio;

/** Regression for Fix8 material-gap floor vs legacy cell/tile option ceilings.
 * Valid material settings must reach the generator; impossible geometry still
 * fails closed under the existing cut-sheet inspection. */
public final class V3Fix10GeometryOptionsTest {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static int[] image(int w,int h){
        int[] px=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int r=60+(x*130/w),g=40+(y*150/h),b=75+((x+y)*100/(w+h));
            px[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        return px;
    }
    private static LayeredCellPortraitEngine.Result generate(int id,int[] px,int w,int h,float gap,float min){
        LayeredCellPortraitEngine.Options o=new LayeredCellPortraitEngine.Options();
        o.layerCount=4;o.columns=10;o.widthMm=152.4f;o.gapMm=gap;
        o.seamGapMm=gap;o.minFeatureMm=min;o.minimumCutMm=min-.05f;
        o.motifDepth=2;o.registration=false;
        if(id==0)return LayeredCellPortraitEngine.generate(px,w,h,o);
        if(id==1)return OffsetCellPortraitEngine.generate(px,w,h,o);
        if(id==2)return BottleCapMosaicEngine.generate(px,w,h,o);
        return FishScaleMosaicEngine.generate(px,w,h,o);
    }
    private static void invalid(Runnable run,String what){
        try{run.run();throw new AssertionError("Unsafe "+what+" accepted");}
        catch(IllegalArgumentException expected){/* invalid input must fail closed */}
    }
    public static void main(String[] args){
        int w=96,h=120;int[] px=image(w,h);
        float regular=StyleCutGeometryPreflight.exportGapMm(.5f,.8f)+.06f;
        require(regular>.5f,"Regression setup must exceed old 0.50 mm seam ceiling");
        for(int id=0;id<4;id++){
            LayeredCellPortraitEngine.Result r=generate(id,px,w,h,regular,.85f);
            require(!r.pieces.isEmpty(),"default cut settings made style "+id+" empty");
            require(r.options.seamGapMm==regular,"style "+id+" lost physical seam floor");
            require(r.options.gapMm==regular,"style "+id+" lost physical gap floor");
            for(int layer=0;layer<r.options.layerCount;layer++)if(r.pieceCounts[layer]>0){
                String svg=r.svg(layer);
                require(svg.contains("<svg ")&&svg.contains("</svg>"),"invalid sheet in style "+id);
            }
        }
        // Custom cut material can request 3.00 mm, not capped at the historical
        // 2.00 mm limit. Use a lower cell density for this large real-world gap.
        float custom=StyleCutGeometryPreflight.exportGapMm(3f,.8f)+.06f;
        for(int id=0;id<4;id++){
            LayeredCellPortraitEngine.Result r=generate(id,px,w,h,custom,1.1f);
            require(!r.pieces.isEmpty(),"custom gap rejected style "+id);
        }
        TessellationEngine.Options tess=new TessellationEngine.Options();
        tess.widthMm=152.4f;tess.gapMm=custom;tess.detail=4;
        tess.style=TessellationEngine.GRID_MOTIF;tess.layerCount=4;
        TessellationEngine.Result tiled=TessellationEngine.generate(px,w,h,tess);
        require(!tiled.tiles.isEmpty(),"valid tessellation material gap rejected");
        // Avoid opening the engine to NaN/Infinity, extreme numbers or unsafe
        // negative gaps. This repair only aligns bounds with valid UI settings.
        LayeredCellPortraitEngine.Options unsafe=new LayeredCellPortraitEngine.Options();
        unsafe.registration=false;
        unsafe.gapMm=Float.NaN;
        invalid(()->LayeredCellPortraitEngine.generate(px,w,h,unsafe),"NaN cell gap");
        unsafe.gapMm=.5f;unsafe.seamGapMm=Float.POSITIVE_INFINITY;
        invalid(()->LayeredCellPortraitEngine.generate(px,w,h,unsafe),"infinite cell seam");
        unsafe.seamGapMm=.5f;unsafe.gapMm=21f;
        invalid(()->LayeredCellPortraitEngine.generate(px,w,h,unsafe),"oversized cell gap");
        // Exercise the real art-style adapter, not only its option validators.
        for(int style:new int[]{VinylStyleSuite.TESSELLATION_MOTIF,VinylStyleSuite.LOW_POLY}){
            VinylStyleSuite.Config cfg=new VinylStyleSuite.Config();
            cfg.style=style;cfg.variant=0;cfg.colorCount=4;cfg.detail=20;
            cfg.widthMm=152.4f;cfg.minimumMm=.8f;cfg.gapMm=3f;
            cfg.palette=new int[]{0xff24252c,0xff71485e,0xffb36e89,0xffe3aa9e};
            StyleKit.Result generated=VinylStyleSuite.generate(px,w,h,cfg);
            require(generated!=null&&!generated.layers.isEmpty(),"valid custom material gap rejected art style "+style);
        }
        TessellationMotifEngine.Options motif=new TessellationMotifEngine.Options();
        motif.gapMm=custom;motif.minimumMm=1.1f;
        motif.validate(); // A valid custom material gap must not be mistaken for corrupt settings.
        LowPolyDelaunayEngine.Options poly=new LowPolyDelaunayEngine.Options();
        poly.gapMm=custom;poly.validate();
        motif.gapMm=Float.NaN;
        invalid(motif::validate,"NaN motif gap");
        motif.gapMm=21f;
        invalid(motif::validate,"oversized motif gap");
        poly.gapMm=Float.NaN;
        invalid(poly::validate,"NaN low-poly gap");
        poly.gapMm=21f;
        invalid(poly::validate,"oversized low-poly gap");
        tess.gapMm=Float.NaN;
        invalid(()->TessellationEngine.generate(px,w,h,tess),"NaN tessellation gap");
        tess.gapMm=21f;
        invalid(()->TessellationEngine.generate(px,w,h,tess),"oversized tessellation gap");
        System.out.println("PASS Fix10 default and custom material gaps across four cell modes, tessellations and low-poly; unsafe values blocked");
    }
}

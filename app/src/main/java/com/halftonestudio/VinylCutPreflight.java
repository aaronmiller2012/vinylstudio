package com.halftonestudio;

/** Conservative, grid-derived risk indicator in physical millimeters.
 * Thin runs and sharp tips require a test cut; this is not a guarantee of safe weeding. */
public final class VinylCutPreflight {
    private VinylCutPreflight() {}
    public static final class Report {
        public final int layer,narrowHorizontalPixels,narrowVerticalPixels,narrowGapPixelsHorizontal,narrowGapPixelsVertical;
        public final float requestedMinimumMm,requestedGapMm,cellWidthMm,cellHeightMm;
        private Report(int l,int h,int v,int gh,int gv,float mm,float gap,float cw,float ch){
            layer=l;narrowHorizontalPixels=h;narrowVerticalPixels=v;
            narrowGapPixelsHorizontal=gh;narrowGapPixelsVertical=gv;
            requestedMinimumMm=mm;requestedGapMm=gap;
            cellWidthMm=cw;cellHeightMm=ch;
        }
        public boolean needsReview(){return narrowHorizontalPixels>0||narrowVerticalPixels>0||
                narrowGapPixelsHorizontal>0||narrowGapPixelsVertical>0;}
    }
    public static Report inspect(MultilayerStencilEngine.Result result,int index,float minMm){
        return inspect(result,index,minMm,Math.max(.35f,.6f*minMm));
    }
    /** Both widths and negative-space clearances are measured on the final cut mask. */
    public static Report inspect(MultilayerStencilEngine.Result result,int index,float minMm,float gapMm){
        if(result==null||index<0||index>=result.layers.size()||!Float.isFinite(minMm)||minMm<=0||
           !Float.isFinite(gapMm)||gapMm<0)
            throw new IllegalArgumentException("Invalid cut preflight");
        boolean[] m=result.cutRasterForInspection(index);
        CancellationCheckpoint.check();
        int w=result.width,h=result.height,hcount=0,vcount=0,gapH=0,gapV=0;
        float cw=result.widthMm/w,ch=result.heightMm/h;
        for(int y=0;y<h;y++){CancellationCheckpoint.check();for(int x=0;x<w;){
            if(!m[y*w+x]){x++;continue;}
            int start=x;while(x<w&&m[y*w+x])x++;
            int run=x-start;if(run*cw<minMm)hcount+=run;
        }}
        for(int x=0;x<w;x++){CancellationCheckpoint.check();for(int y=0;y<h;){
            if(!m[y*w+x]){y++;continue;}
            int start=y;while(y<h&&m[y*w+x])y++;
            int run=y-start;if(run*ch<minMm)vcount+=run;
        }}
        // Count only internal gaps bracketed on both ends by the same cut
        // mask, never external paper/background or the photo's edge.
        for(int y=0;y<h;y++){CancellationCheckpoint.check();for(int x=0;x<w;){
            if(m[y*w+x]){x++;continue;}
            int start=x;while(x<w&&!m[y*w+x])x++;
            if(start>0&&x<w&&(x-start)*cw<gapMm)gapH+=x-start;
        }}
        for(int x=0;x<w;x++){CancellationCheckpoint.check();for(int y=0;y<h;){
            if(m[y*w+x]){y++;continue;}
            int start=y;while(y<h&&!m[y*w+x])y++;
            if(start>0&&y<h&&(y-start)*ch<gapMm)gapV+=y-start;
        }}
        return new Report(index,hcount,vcount,gapH,gapV,minMm,gapMm,cw,ch);
    }
}

package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/** Estimates actual opaque-vinyl coverage from the same closed cut contours as SVG export.
 * Registration marks are deliberately excluded: they are alignment aids, not photographic coverage.
 * Areas are sampled, not mathematically exact; this class has no Android dependencies.
 */
public final class VinylDiagnostics {
    private VinylDiagnostics() {}
    public static final class LayerStats {
        public final int pathCount;
        /** Percent of entire artwork area cut by this layer before stacking. */
        public final float coveredPct;
        /** Percent of artwork area still showing after all higher layers are added. */
        public final float visiblePct;
        /** Percent of this layer's own covered area hidden by layers applied later. */
        public final float hiddenFractionPct;
        LayerStats(int count,int covered,int visible,int gridCells){
            pathCount=count;coveredPct=100f*covered/gridCells;visiblePct=100f*visible/gridCells;
            hiddenFractionPct=covered==0?0:100f*(covered-visible)/covered;
        }
    }
    public static final class Report {
        public final LayerStats[] layers;
        public final int sampleColumns,sampleRows;
        Report(LayerStats[] l,int x,int y){layers=l;sampleColumns=x;sampleRows=y;}
    }
    /** The resolution is horizontal grid samples, normally 96 for fast mobile feedback. */
    public static Report analyze(VinylEngine.Result result,int resolution){
        if(result==null)throw new IllegalArgumentException("Missing geometry");
        final int nx=Math.max(32,Math.min(192,resolution));
        final int ny=Math.max(24,Math.min(240,Math.round(nx*result.heightMm/result.settings.widthMm)));
        final int total=nx*ny;
        BitSet[] layerCells=new BitSet[result.shapes.size()];
        for(int li=0;li<layerCells.length;li++){
            layerCells[li]=new BitSet(total);
            if(!result.settings.layers.get(li).enabled)continue;
            for(VinylEngine.Shape sh:result.shapes.get(li)){
                if(sh.circle)rasterCircle(layerCells[li],sh,nx,ny,result.settings.widthMm,result.heightMm);
                else rasterPolygon(layerCells[li],sh,nx,ny,result.settings.widthMm,result.heightMm);
            }
        }
        BitSet obscured=new BitSet(total);
        LayerStats[] stats=new LayerStats[layerCells.length];
        for(int li=layerCells.length-1;li>=0;li--){
            BitSet visible=(BitSet)layerCells[li].clone();
            visible.andNot(obscured);
            int coveredCount=layerCells[li].cardinality();
            stats[li]=new LayerStats(result.shapes.get(li).size(),coveredCount,visible.cardinality(),total);
            obscured.or(layerCells[li]);
        }
        return new Report(stats,nx,ny);
    }
    private static void rasterCircle(BitSet cells,VinylEngine.Shape sh,int nx,int ny,float width,float height){
        int rowStart=Math.max(0,(int)Math.floor((sh.y-sh.r)/height*ny-.5));
        int rowEnd=Math.min(ny-1,(int)Math.ceil((sh.y+sh.r)/height*ny-.5));
        for(int row=rowStart;row<=rowEnd;row++){
            float y=(row+.5f)*height/ny;
            float dy=y-sh.y;
            if(Math.abs(dy)>sh.r)continue;
            double radiusX=Math.sqrt(Math.max(0,sh.r*sh.r-dy*dy));
            fillBetween(cells,row,nx,width,sh.x-(float)radiusX,sh.x+(float)radiusX);
        }
    }
    private static void rasterPolygon(BitSet cells,VinylEngine.Shape sh,int nx,int ny,float width,float height){
        float[][] rings=sh.contours==null?new float[][]{sh.xy}:sh.contours;
        if(rings[0]==null||rings[0].length<6)return;
        float[] bbox=sh.bounds();
        int start=Math.max(0,(int)Math.floor(bbox[1]/height*ny-.5));
        int end=Math.min(ny-1,(int)Math.ceil(bbox[3]/height*ny-.5));
        int maxCrossings=0;for(float[] ring:rings)maxCrossings+=ring.length/2;
        float[] crossings=new float[maxCrossings];
        for(int row=start;row<=end;row++){
            float y=(row+.5f)*height/ny;int count=0;
            for(float[] xy:rings)for(int i=0;i<xy.length;i+=2){
                int j=(i+2)%xy.length;
                float y0=xy[i+1],y1=xy[j+1];
                if((y0>y)!=(y1>y)){
                    float x=xy[i]+(y-y0)*(xy[j]-xy[i])/(y1-y0);
                    crossings[count++]=x;
                }
            }
            Arrays.sort(crossings,0,count);
            for(int i=0;i+1<count;i+=2)fillBetween(cells,row,nx,width,crossings[i],crossings[i+1]);
        }
    }
    private static void fillBetween(BitSet cells,int row,int nx,float width,float x0,float x1){
        if(x1<=x0)return;
        int start=Math.max(0,(int)Math.ceil(x0/width*nx-.5f));
        int end=Math.min(nx-1,(int)Math.floor(x1/width*nx-.5f));
        if(end>=start)cells.set(row*nx+start,row*nx+end+1);
    }
}

package com.halftonestudio;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Geometry-derived, conservative warning markers for completed specialty cut output.
 * Markers use ARTWORK MILLIMETERS, not preview pixels or padded SVG coordinates.
 * Bounding-box tests are only sufficient warnings: no false claim of full cut safety.
 */
public final class SpecialtyCutWarningLocator {
    private SpecialtyCutWarningLocator() {}
    private static void check(){if(Thread.currentThread().isInterrupted())throw new CancellationException("Cut review canceled");}
    private static float[] bounds(float[] xy){
        if(xy==null||xy.length<6)return null;
        float x0=Float.POSITIVE_INFINITY,y0=x0,x1=Float.NEGATIVE_INFINITY,y1=x1;
        for(int k=0;k+1<xy.length;k+=2){float x=xy[k],y=xy[k+1];
            if(!Float.isFinite(x)||!Float.isFinite(y))return null;
            x0=Math.min(x0,x);y0=Math.min(y0,y);x1=Math.max(x1,x);y1=Math.max(y1,y);
        }
        return new float[]{x0,y0,x1,y1};
    }
    private static void polygon(List<CutWarningLocator.Marker> out,float[] xy,int layer,
                                float mmPerPixel,float minimumMm,int limit){
        if(out.size()>=limit)return;
        float[] b=bounds(xy);if(b==null)return;
        float narrow=Math.min(b[2]-b[0],b[3]-b[1])*mmPerPixel;
        if(narrow+.003f<minimumMm)
            out.add(new CutWarningLocator.Marker(layer,(b[0]+b[2])*.5f*mmPerPixel,
                (b[1]+b[3])*.5f*mmPerPixel,"Small closed vinyl shape (bounding box)"));
    }
    /** Four-connected components of a layer's actual cut mask; reports only narrow
     * bounding boxes or tiny isolated components. Holes are respected by the mask.
     * No per-pixel object allocations or SVG parsing; work is cancellable.
     */
    static void mask(List<CutWarningLocator.Marker> out,boolean[] pixels,int w,int h,
                     int layer,float mmPerPixel,float minimumMm,int limit){
        if(out.size()>=limit||pixels==null||pixels.length!=(long)w*h)return;
        BitSet seen=new BitSet(pixels.length);
        int[] queue=new int[pixels.length];
        float areaFloor=Math.max(.04f,minimumMm*minimumMm*.30f);
        for(int i=0;i<pixels.length&&out.size()<limit;i++){
            if((i&8191)==0)check();
            if(!pixels[i]||seen.get(i))continue;
            int head=0,tail=0,minX=w,minY=h,maxX=-1,maxY=-1;
            queue[tail++]=i;seen.set(i);
            while(head<tail){
                if((head&8191)==0)check();
                int v=queue[head++],x=v%w,y=v/w;
                minX=Math.min(minX,x);maxX=Math.max(maxX,x);
                minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                if(x>0){int n=v-1;if(pixels[n]&&!seen.get(n)){seen.set(n);queue[tail++]=n;}}
                if(x+1<w){int n=v+1;if(pixels[n]&&!seen.get(n)){seen.set(n);queue[tail++]=n;}}
                if(y>0){int n=v-w;if(pixels[n]&&!seen.get(n)){seen.set(n);queue[tail++]=n;}}
                if(y+1<h){int n=v+w;if(pixels[n]&&!seen.get(n)){seen.set(n);queue[tail++]=n;}}
            }
            float shortSide=Math.min(maxX-minX+1,maxY-minY+1)*mmPerPixel;
            float area=tail*mmPerPixel*mmPerPixel;
            if(shortSide+.003f<minimumMm||area<areaFloor){
                out.add(new CutWarningLocator.Marker(layer,(minX+maxX+1)*.5f*mmPerPixel,
                    (minY+maxY+1)*.5f*mmPerPixel,"Tiny or narrow separated cut piece"));
            }
        }
    }
    /** Only filled, physical vinyl geometry is eligible. Pen centerlines deliberately
     * have no cut markers; there is no safe width calculation for an open toolpath.
     */
    public static List<CutWarningLocator.Marker> locate(Object result,float minimumMm,int limit){
        if(result==null||limit<1||!Float.isFinite(minimumMm)||minimumMm<=0)return Collections.emptyList();
        ArrayList<CutWarningLocator.Marker> out=new ArrayList<>();
        if(result instanceof TessellationEngine.Result){
            TessellationEngine.Result r=(TessellationEngine.Result)result;
            float px=r.options.widthMm/r.width;
            for(TessellationEngine.Tile tile:r.tiles){check();polygon(out,tile.xy,tile.layer,px,minimumMm,limit);
                if(out.size()>=limit)break;
            }
        }else if(result instanceof LayeredCellPortraitEngine.Result){
            LayeredCellPortraitEngine.Result r=(LayeredCellPortraitEngine.Result)result;
            float px=r.options.widthMm/r.width;
            for(LayeredCellPortraitEngine.Piece piece:r.pieces){check();polygon(out,piece.outer,piece.layer,px,minimumMm,limit);
                if(out.size()>=limit)break;
            }
        }else if(result instanceof MultilayerStencilEngine.Result){
            MultilayerStencilEngine.Result r=(MultilayerStencilEngine.Result)result;
            float px=r.widthMm/r.width;
            for(int layer=0;layer<r.layers.size()&&out.size()<limit;layer++){
                check();mask(out,r.layers.get(layer).mask,r.width,r.height,layer,px,minimumMm,limit);
            }
        }else if(result instanceof SpaceFillingRibbon.Result){
            SpaceFillingRibbon.Result r=(SpaceFillingRibbon.Result)result;
            mask(out,r.mask,r.width,r.height,0,r.widthMm/r.width,minimumMm,limit);
        }
        return Collections.unmodifiableList(out);
    }
}

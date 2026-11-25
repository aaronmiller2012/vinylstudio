package com.halftonestudio;

import android.graphics.Path;
import android.graphics.Matrix;
import java.util.Arrays;

/**
 * Display-only, immutable vector geometry cache for specialty cut styles.
 * Constructed on the specialty worker. CanvasView never traces pixels, parses SVG,
 * constructs cell paths, or re-runs the export generator inside onDraw().
 * The geometry is the SAME union contour / piece / tile data as the actual SVG.
 * Registration marks and paper are painted by the view, not added to cut paths.
 */
final class SpecialtyVectorCache {
    final Object source;
    final int width, height;
    final Path[] layers, topVisible;
    final int[] colors, stackOrder;
    final String kind;

    private SpecialtyVectorCache(Object src,int w,int h,Path[] p,Path[] top,int[] c,int[] order,String type){
        source=src;width=w;height=h;layers=p;topVisible=top;colors=c;stackOrder=order;kind=type;
    }
    /** Rebind unchanged tessellation paths to the new registration revision. */
    SpecialtyVectorCache rebindTessellation(TessellationEngine.Result result){
        return new SpecialtyVectorCache(result,width,height,layers,topVisible,colors,stackOrder,kind);
    }
    private static Path polygon(float[] outer,java.util.List<float[]> holes){
        Path p=new Path();p.setFillType(Path.FillType.EVEN_ODD);
        ring(p,outer);
        if(holes!=null)for(float[] h:holes)ring(p,h);
        return p;
    }
    private static void ring(Path p,float[] xy){
        if(xy==null||xy.length<6)return;
        p.moveTo(xy[0],xy[1]);
        for(int i=2;i<xy.length;i+=2)p.lineTo(xy[i],xy[i+1]);
        p.close();
    }
    private static void appendRing(Path p,float[] xy){ring(p,xy);}
    static SpecialtyVectorCache tessellation(TessellationEngine.Result r){
        Path[] p=new Path[r.options.layerCount];int[] col=r.options.colors.clone();
        for(int i=0;i<p.length;i++){p[i]=new Path();p[i].setFillType(Path.FillType.EVEN_ODD);}
        for(TessellationEngine.Tile t:r.tiles){
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            if(t.layer<0||t.layer>=p.length)continue;
            appendRing(p[t.layer],t.xy);
            for(float[] hole:t.holes)appendRing(p[t.layer],hole);
        }
        int[] order=new int[p.length];for(int i=0;i<order.length;i++)order[i]=i;
        return new SpecialtyVectorCache(r,r.width,r.height,p,null,col,order,"tessellation");
    }
    static SpecialtyVectorCache layered(LayeredCellPortraitEngine.Result r){
        Path[] p=new Path[r.options.layerCount];int[] col=r.options.colors.clone();
        for(int i=0;i<p.length;i++){p[i]=new Path();p[i].setFillType(Path.FillType.EVEN_ODD);}
        for(LayeredCellPortraitEngine.Piece piece:r.pieces){
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            if(piece.layer<0||piece.layer>=p.length)continue;
            appendRing(p[piece.layer],piece.outer);
            for(float[] h:piece.holes)appendRing(p[piece.layer],h);
        }
        int[] order=new int[p.length];for(int i=0;i<order.length;i++)order[i]=i;
        return new SpecialtyVectorCache(r,r.width,r.height,p,null,col,order,"layered-cell");
    }
    static SpecialtyVectorCache tonal(MultilayerStencilEngine.Result r){
        int n=r.layers.size();Path[] cut=new Path[n],visible=new Path[n];int[] colors=new int[n],order=new int[n];
        for(int i=0;i<n;i++){
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            MultilayerStencilEngine.Layer layer=r.layers.get(i);
            // Use the exact same validated cubic geometry as the exported sheet.
            cut[i]=tonalPath(r.cutPathData(i),r.width,r.height,r.widthMm,r.heightMm);
            // Top-visible diagnostic geometry has no independent cut export.
            visible[i]=tonalPath(r.visiblePathData(i),r.width,r.height,r.widthMm,r.heightMm);
            colors[i]=layer.color;order[i]=i;
        }
        return new SpecialtyVectorCache(r,r.width,r.height,cut,visible,colors,order,"tonal-stencil");
    }
    static SpecialtyVectorCache spiral(Object r,int w,int h,Path path){
        return new SpecialtyVectorCache(r,w,h,
            new Path[]{path},null,new int[]{0xff171717},new int[]{0},"spiral-portrait-ribbon");
    }
    static SpecialtyVectorCache ribbon(SpaceFillingRibbon.Result r){
        Path[] paths={unionMask(r.mask,r.width,r.height)};
        return new SpecialtyVectorCache(r,r.width,r.height,paths,null,new int[]{0xff171717},new int[]{0},"cut-ribbon");
    }
    /** Source raster union contours: exact shared-boundary tracer used by SVG export.
     * Direct grid rings avoid generating/parsing SVG for unsmoothed ribbon masks. */
    private static Path unionMask(boolean[] mask,int w,int h){
        Path p=new Path();p.setFillType(Path.FillType.EVEN_ODD);
        for(java.util.ArrayList<int[]> loop:MultilayerStencilEngine.straightUnionRings(mask,w,h)){
            CancellationCheckpoint.check();
            if(loop.isEmpty())continue;
            int[] first=loop.get(0);p.moveTo(first[0],first[1]);
            for(int i=1;i<loop.size();i++){if((i&255)==0)CancellationCheckpoint.check();int[] v=loop.get(i);p.lineTo(v[0],v[1]);}
            p.close();
        }
        return p;
    }
    private static Path tonalPath(String data,int w,int h,float widthMm,float heightMm){
        Path p=pathFromMLCZ(data,0,data.length());
        Matrix toPixels=new Matrix();
        toPixels.setScale(w/widthMm,h/heightMm);
        p.transform(toPixels);
        return p;
    }
    static Path pathFromMLCZ(String data,int start,int end){
        Path p=new Path();p.setFillType(Path.FillType.EVEN_ODD);
        int[] position={start};
        while(position[0]<end){
            char op=data.charAt(position[0]++);
            if(Character.isWhitespace(op)||op==',')continue;
            if(op=='Z'||op=='z'){p.close();continue;}
            if(op=='M'||op=='L'){
                float x=nextPathNumber(data,position,end),y=nextPathNumber(data,position,end);
                if(op=='M')p.moveTo(x,y);else p.lineTo(x,y);
            }else if(op=='C'){
                float x1=nextPathNumber(data,position,end),y1=nextPathNumber(data,position,end);
                float x2=nextPathNumber(data,position,end),y2=nextPathNumber(data,position,end);
                float x3=nextPathNumber(data,position,end),y3=nextPathNumber(data,position,end);
                p.cubicTo(x1,y1,x2,y2,x3,y3);
            }else throw new IllegalArgumentException("Unsupported cut vector command: "+op);
        }
        return p;
    }
    private static float nextPathNumber(String data,int[] pos,int end){
        while(pos[0]<end && (Character.isWhitespace(data.charAt(pos[0]))||data.charAt(pos[0])==','))pos[0]++;
        int from=pos[0];
        while(pos[0]<end && "0123456789.+-eE".indexOf(data.charAt(pos[0]))>=0)pos[0]++;
        if(from==pos[0])throw new IllegalArgumentException("Missing SVG curve coordinate");
        float n=Float.parseFloat(data.substring(from,pos[0]));
        if(!Float.isFinite(n))throw new IllegalArgumentException("Invalid SVG curve coordinate");
        return n;
    }
    static Path pathFromMLZ(String data,int start,int end){
        Path p=new Path();p.setFillType(Path.FillType.EVEN_ODD);
        int i=start;
        while(i<end){
            char c=data.charAt(i);
            if(Character.isWhitespace(c)||c==','){i++;continue;}
            if(c=='Z'||c=='z'){p.close();i++;continue;}
            if(c!='M'&&c!='L')throw new IllegalArgumentException("Unsupported preview path command: "+c);
            i++;
            while(i<end&&Character.isWhitespace(data.charAt(i)))i++;
            int xStart=i;
            while(i<end&&"0123456789.+-eE".indexOf(data.charAt(i))>=0)i++;
            if(xStart==i)throw new IllegalArgumentException("Missing contour X");
            float x=Float.parseFloat(data.substring(xStart,i));
            while(i<end&&(Character.isWhitespace(data.charAt(i))||data.charAt(i)==','))i++;
            int yStart=i;
            while(i<end&&"0123456789.+-eE".indexOf(data.charAt(i))>=0)i++;
            if(yStart==i)throw new IllegalArgumentException("Missing contour Y");
            float y=Float.parseFloat(data.substring(yStart,i));
            if(!Float.isFinite(x)||!Float.isFinite(y))throw new IllegalArgumentException("Invalid vector point");
            if(c=='M')p.moveTo(x,y);else p.lineTo(x,y);
        }
        return p;
    }
}

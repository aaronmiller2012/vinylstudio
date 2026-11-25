package com.halftonestudio;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.pdf.PdfDocument;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A composite artwork, not cutter instructions. PNG uses up to 300 dpi;
 * PDF preserves Canvas paths as vectors when the style has vector geometry.
 * Both exports use physical board dimensions, including registration margins.
 */
public final class ArtworkDocumentExport {
    private ArtworkDocumentExport() { }
    public interface Drawing { void draw(Canvas canvas); }
    public static final class Page {
        public final float widthMm, heightMm;
        public final Drawing drawing;
        public Page(float width,float height,Drawing draw) {
            ArtworkExportLayout.pdfPoints(width,height);
            if(draw==null)throw new IllegalArgumentException("Missing drawing");
            widthMm=width;heightMm=height;drawing=draw;
        }
    }
    public static void writePng(Page page,OutputStream output) throws IOException {
        int[] size=ArtworkExportLayout.pngPixels(page.widthMm,page.heightMm);
        Bitmap bmp=null;
        try {
            bmp=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(bmp);
            canvas.scale(size[0]/page.widthMm,size[1]/page.heightMm);
            page.drawing.draw(canvas);
            if(!bmp.compress(Bitmap.CompressFormat.PNG,100,output))
                throw new IOException("PNG encoder failed");
            output.flush();
        } finally { if(bmp!=null)bmp.recycle(); }
    }
    public static void writePdf(Page page,OutputStream output) throws IOException {
        int[] points=ArtworkExportLayout.pdfPoints(page.widthMm,page.heightMm);
        PdfDocument doc=new PdfDocument();
        try {
            PdfDocument.Page pdf=doc.startPage(new PdfDocument.PageInfo.Builder(
                    points[0],points[1],1).create());
            Canvas canvas=pdf.getCanvas();
            canvas.scale(points[0]/page.widthMm,points[1]/page.heightMm);
            page.drawing.draw(canvas);
            doc.finishPage(pdf);
            doc.writeTo(output);
            output.flush();
        } finally { doc.close(); }
    }
    private static Path contour(float[][] rings) {
        Path path=new Path();path.setFillType(Path.FillType.EVEN_ODD);
        if(rings!=null)for(float[] ring:rings){
            if(ring==null||ring.length<6)continue;
            path.moveTo(ring[0],ring[1]);
            for(int i=2;i<ring.length;i+=2)path.lineTo(ring[i],ring[i+1]);
            path.close();
        }
        return path;
    }
    private static void shape(Canvas canvas,Paint paint,VinylEngine.Shape s){
        if(s.circle)canvas.drawCircle(s.x,s.y,s.r,paint);
        else canvas.drawPath(contour(s.contours==null?new float[][]{s.xy}:s.contours),paint);
    }
    private static void underlay(Canvas canvas,Paint paint,AutoSilhouetteUnderlay.Output underlay){
        if(underlay==null)return;
        paint.setColor(underlay.color);paint.setStyle(Paint.Style.FILL);
        shape(canvas,paint,underlay.shape);
    }
    private static void marks(Canvas canvas,Paint paint,float[][] positions,
                              int shape,float sizeMm,float minMm,int color){
        if(positions==null)return;
        paint.setStyle(Paint.Style.FILL);paint.setColor(color);
        for(float[] m:positions){
            float[] poly=RegistrationMarks.outline(shape,m[0],m[1],sizeMm,minMm);
            if(poly==null)canvas.drawCircle(m[0],m[1],sizeMm/2f,paint);
            else canvas.drawPath(contour(new float[][]{poly}),paint);
        }
    }
    /** Full physical board; cut sheets draw bottom to top, paper is not a vinyl sheet. */
    public static Page standard(VinylEngine.Result result,AutoSilhouetteUnderlay.Output base,
                                boolean baseOnly) {return standard(result,base,baseOnly,true);}
    public static Page standard(VinylEngine.Result result,AutoSilhouetteUnderlay.Output base,
                                boolean baseOnly,boolean includeMarks) {
        final float bw=result.boardW,bh=result.boardH,pad=result.padMm;
        return new Page(bw,bh,canvas->{
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            canvas.drawColor(result.settings.paper);
            canvas.save();canvas.translate(pad,pad);
            underlay(canvas,paint,base);
            if(!baseOnly)for(int i=0;i<result.shapes.size();i++){
                if(!VinylEngine.cutLayer(result,i))continue;
                paint.setColor(result.settings.layers.get(i).color);
                paint.setStyle(Paint.Style.FILL);
                for(VinylEngine.Shape shape:result.shapes.get(i)){
                    if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
                    shape(canvas,paint,shape);
                }
            }
            if(includeMarks&&result.settings.registration)
                marks(canvas,paint,result.marks,result.settings.markStyle,
                        result.settings.markSizeMm,result.settings.minimumMm,0xff333333);
            canvas.restore();
        });
    }
    /** One physical standard sheet, using the same closed shapes and mark centers as SVG export. */
    public static Page standardLayer(VinylEngine.Result result,AutoSilhouetteUnderlay.Output base,
                                     int layerIndex) {return standardLayer(result,base,layerIndex,true);}
    public static Page standardLayer(VinylEngine.Result result,AutoSilhouetteUnderlay.Output base,
                                     int layerIndex,boolean includeMarks) {
        if(layerIndex<-1||layerIndex>=result.shapes.size())throw new IllegalArgumentException("Standard layer index");
        if(layerIndex<0&&base==null)throw new IllegalArgumentException("Missing solid Snip layer");
        final float bw=result.boardW,bh=result.boardH,pad=result.padMm;
        final int color=layerIndex<0?base.color:result.settings.layers.get(layerIndex).color;
        return new Page(bw,bh,canvas->{
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);canvas.drawColor(result.settings.paper);
            canvas.save();canvas.translate(pad,pad);
            if(layerIndex<0)underlay(canvas,paint,base);
            else{
                paint.setColor(color);paint.setStyle(Paint.Style.FILL);
                for(VinylEngine.Shape item:result.shapes.get(layerIndex))shape(canvas,paint,item);
            }
            if(includeMarks&&result.settings.registration)
                marks(canvas,paint,result.marks,result.settings.markStyle,result.settings.markSizeMm,
                    result.settings.minimumMm,color);
            canvas.restore();
        });
    }
    public static Page newStyle(StyleKit.Result result,NewStyleVectorCache vectors,
            AutoSilhouetteUnderlay.Output base,int paper,float pad,
            boolean baseOnly,boolean registration,float[][] markPositions,
            int markShape,float markSize,float minimum) {
        return new Page(result.widthMm+2*pad,result.heightMm+2*pad,canvas->{
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            canvas.drawColor(paper);canvas.save();canvas.translate(pad,pad);
            underlay(canvas,p,base);
            if(!baseOnly)for(int i=0;i<vectors.fills.length;i++){
                if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
                p.setColor(vectors.colors[i]);p.setStyle(Paint.Style.FILL);
                canvas.drawPath(vectors.fills[i],p);
                if(!result.layers.get(i).lines.isEmpty()){
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(vectors.strokeWidths[i]);
                    p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
                    canvas.drawPath(vectors.strokes[i],p);
                }
            }
            if(registration)marks(canvas,p,markPositions,markShape,markSize,minimum,0xff333333);
            canvas.restore();
        });
    }
    /** One physical art-style sheet. Filled and open geometry retain their export semantics. */
    public static Page newStyleLayer(StyleKit.Result result,NewStyleVectorCache vectors,
            AutoSilhouetteUnderlay.Output base,int paper,float pad,int layerIndex,
            boolean registration,float[][] markPositions,int markShape,float markSize,float minimum) {
        if(layerIndex<-1||layerIndex>=vectors.fills.length)throw new IllegalArgumentException("Art-style layer index");
        if(layerIndex<0&&base==null)throw new IllegalArgumentException("Missing solid Snip layer");
        final int color=layerIndex<0?base.color:vectors.colors[layerIndex];
        return new Page(result.widthMm+2*pad,result.heightMm+2*pad,canvas->{
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);canvas.drawColor(paper);
            canvas.save();canvas.translate(pad,pad);
            if(layerIndex<0)underlay(canvas,p,base);
            else{
                p.setColor(color);p.setStyle(Paint.Style.FILL);canvas.drawPath(vectors.fills[layerIndex],p);
                if(!result.layers.get(layerIndex).lines.isEmpty()){
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(vectors.strokeWidths[layerIndex]);
                    p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
                    canvas.drawPath(vectors.strokes[layerIndex],p);
                }
            }
            if(registration)marks(canvas,p,markPositions,markShape,markSize,minimum,color);
            canvas.restore();
        });
    }
    public static Page specialty(SpecialtyVectorCache vectors,AutoSilhouetteUnderlay.Output base,
                                  float widthMm,int paper,float pad,boolean baseOnly){
        return specialty(vectors,base,widthMm,paper,pad,baseOnly,false,null,
            RegistrationMarks.CROSS,0f,.2f);
    }
    public static Page specialty(SpecialtyVectorCache vectors,AutoSilhouetteUnderlay.Output base,
            float widthMm,int paper,float pad,boolean baseOnly,boolean registration,
            float[][] markPositions,int markShape,float markSize,float minimum){
        final float heightMm=widthMm*vectors.height/(float)Math.max(1,vectors.width);
        return new Page(widthMm+2*pad,heightMm+2*pad,canvas->{
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            canvas.drawColor(paper);canvas.save();canvas.translate(pad,pad);
            underlay(canvas,p,base);
            if(!baseOnly){
                canvas.save();
                canvas.scale(widthMm/vectors.width,heightMm/vectors.height);
                for(int index:vectors.stackOrder){
                    if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
                    if(index<0||index>=vectors.layers.length)continue;
                    p.setColor(vectors.colors[index]);p.setStyle(Paint.Style.FILL);
                    canvas.drawPath(vectors.layers[index],p);
                }
                canvas.restore();
            }
            if(registration)marks(canvas,p,markPositions,markShape,markSize,minimum,0xff333333);
            canvas.restore();
        });
    }
    public static Page specialtyLayer(SpecialtyVectorCache vectors,AutoSilhouetteUnderlay.Output base,
            float widthMm,int paper,float pad,int layerIndex,boolean registration,
            float[][] markPositions,int markShape,float markSize,float minimum){
        if(layerIndex<-1||layerIndex>=vectors.layers.length)throw new IllegalArgumentException("Specialty layer index");
        if(layerIndex<0&&base==null)throw new IllegalArgumentException("Missing solid Snip layer");
        final float heightMm=widthMm*vectors.height/(float)Math.max(1,vectors.width);
        final int color=layerIndex<0?base.color:vectors.colors[layerIndex];
        return new Page(widthMm+2*pad,heightMm+2*pad,canvas->{
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);canvas.drawColor(paper);
            canvas.save();canvas.translate(pad,pad);
            if(layerIndex<0)underlay(canvas,p,base);
            else{
                canvas.save();canvas.scale(widthMm/vectors.width,heightMm/vectors.height);
                p.setColor(color);p.setStyle(Paint.Style.FILL);canvas.drawPath(vectors.layers[layerIndex],p);
                canvas.restore();
            }
            if(registration)marks(canvas,p,markPositions,markShape,markSize,minimum,color);
            canvas.restore();
        });
    }
    /** Stipple artwork uses the exact completed tile Paths, not a low-resolution JPEG. */
    public static Page stipple(Path[] layers,int[] colors,int[] stackOrder,int width,int height,
                                AutoSilhouetteUnderlay.Output base,float artWidth,int paper,float pad,
                                boolean baseOnly){
        return stipple(layers,colors,stackOrder,width,height,base,artWidth,paper,pad,baseOnly,
            false,null,RegistrationMarks.CROSS,0f,.2f);
    }
    public static Page stipple(Path[] layers,int[] colors,int[] stackOrder,int width,int height,
            AutoSilhouetteUnderlay.Output base,float artWidth,int paper,float pad,boolean baseOnly,
            boolean registration,float[][] markPositions,int markShape,float markSize,float minimum){
        final float artHeight=artWidth*height/(float)Math.max(1,width);
        return new Page(artWidth+2*pad,artHeight+2*pad,canvas->{
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            canvas.drawColor(paper);canvas.save();canvas.translate(pad,pad);
            underlay(canvas,p,base);
            if(!baseOnly){
                canvas.save();
                canvas.scale(artWidth/width,artHeight/height);
                for(int index:stackOrder){
                    if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
                    if(index<0||index>=layers.length)continue;
                    p.setColor(colors[index]);p.setStyle(Paint.Style.FILL);
                    canvas.drawPath(layers[index],p);
                }
                canvas.restore();
            }
            if(registration)marks(canvas,p,markPositions,markShape,markSize,minimum,0xff333333);
            canvas.restore();
        });
    }
    public static Page stippleLayer(Path[] layers,int[] colors,int width,int height,
            AutoSilhouetteUnderlay.Output base,float artWidth,int paper,float pad,int layerIndex,
            boolean registration,float[][] markPositions,int markShape,float markSize,float minimum){
        if(layerIndex<-1||layerIndex>=layers.length)throw new IllegalArgumentException("Stipple layer index");
        if(layerIndex<0&&base==null)throw new IllegalArgumentException("Missing solid Snip layer");
        final float artHeight=artWidth*height/(float)Math.max(1,width);
        final int color=layerIndex<0?base.color:colors[layerIndex];
        return new Page(artWidth+2*pad,artHeight+2*pad,canvas->{
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);canvas.drawColor(paper);
            canvas.save();canvas.translate(pad,pad);
            if(layerIndex<0)underlay(canvas,p,base);
            else{
                canvas.save();canvas.scale(artWidth/width,artHeight/height);
                p.setColor(color);p.setStyle(Paint.Style.FILL);canvas.drawPath(layers[layerIndex],p);
                canvas.restore();
            }
            if(registration)marks(canvas,p,markPositions,markShape,markSize,minimum,color);
            canvas.restore();
        });
    }
    /** Exact vector centerline page for pen/engrave artwork such as maze and TSP paths. */
    public static Page linePath(Path path,int sourceWidth,int sourceHeight,float widthMm,
            int paper,int color,float strokeMm){
        if(path==null||sourceWidth<1||sourceHeight<1)throw new IllegalArgumentException("Missing line artwork");
        final float heightMm=widthMm*sourceHeight/(float)sourceWidth;
        return new Page(widthMm,heightMm,canvas->{
            canvas.drawColor(paper);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            canvas.save();canvas.scale(widthMm/sourceWidth,heightMm/sourceHeight);
            p.setColor(color);p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(.01f,strokeMm*sourceWidth/widthMm));
            p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            canvas.drawPath(path,p);canvas.restore();
        });
    }
    /** Some pen/maze styles have only a final bitmap; do not invent SVG geometry. */
    public static Page image(Bitmap bitmap,float widthMm,float heightMm,int paper){
        if(bitmap==null||bitmap.isRecycled())throw new IllegalArgumentException("Missing final preview image");
        return new Page(widthMm,heightMm,canvas->{
            canvas.drawColor(paper);
            Paint p=new Paint(Paint.FILTER_BITMAP_FLAG|Paint.ANTI_ALIAS_FLAG);
            canvas.drawBitmap(bitmap,null,new android.graphics.RectF(0,0,widthMm,heightMm),p);
        });
    }
}

package com.halftonestudio;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Multi-page printable assembly guide. Artwork pages retain native Canvas vectors. */
public final class AssemblyGuideDocument {
    private AssemblyGuideDocument(){}
    private static final int PAGE_W=612,PAGE_H=792;
    private static final int INK=0xff17212b,MUTED=0xff52606d,ACCENT=0xff147d73;

    public static final class LayerPage {
        public final AssemblyGuidePlan.Layer layer;
        public final ArtworkDocumentExport.Page artwork;
        final java.util.function.Supplier<ArtworkCutMetrics.Stats> metrics;
        public LayerPage(AssemblyGuidePlan.Layer item,ArtworkDocumentExport.Page page){
            this(item,page,null);
        }
        LayerPage(AssemblyGuidePlan.Layer item,ArtworkDocumentExport.Page page,java.util.function.Supplier<ArtworkCutMetrics.Stats> metrics){
            if(item==null||page==null)throw new IllegalArgumentException("Assembly layer page is incomplete");
            layer=item;artwork=page;this.metrics=metrics;
        }
    }
    public static final class Document {
        public final AssemblyGuidePlan plan;
        public final ArtworkDocumentExport.Page composite;
        public final List<LayerPage> pages;
        public final AssemblyMaterialPlan material;
        public Document(AssemblyGuidePlan metadata,ArtworkDocumentExport.Page finished,List<LayerPage> layers){
            if(metadata==null||finished==null||layers==null)throw new IllegalArgumentException("Assembly guide is incomplete");
            if(!metadata.layerMappingComplete)throw new IllegalArgumentException("Assembly layer mapping is incomplete");
            if(metadata.layers.size()!=layers.size())throw new IllegalArgumentException("Assembly page count differs from physical operations");
            PhysicalSheetParity.requireFitsArtwork(finished.widthMm,finished.heightMm,metadata.widthMm,metadata.heightMm);
            ArrayList<LayerPage> copy=new ArrayList<>(layers.size());
            for(int i=0;i<layers.size();i++){
                LayerPage page=layers.get(i);
                if(page==null||page.layer.physicalIndex!=metadata.layers.get(i).physicalIndex)
                    throw new IllegalArgumentException("Assembly page order differs from physical stack");
                // The PDF must never individually fit differently sized sheets
                // into the same preview rectangle: that disguises misregistration.
                PhysicalSheetParity.requireSameBoard(finished.widthMm,finished.heightMm,
                    page.artwork.widthMm,page.artwork.heightMm);
                copy.add(page);
            }
            plan=metadata;composite=finished;pages=Collections.unmodifiableList(copy);
            material=AssemblyMaterialPlan.from(metadata,finished.widthMm,finished.heightMm);
        }
    }

    public static void writePdf(Document guide,OutputStream output) throws IOException{
        if(guide==null||output==null)throw new IllegalArgumentException("Missing assembly guide destination");
        PdfDocument pdf=new PdfDocument();
        try{
            PdfDocument.Page cover=pdf.startPage(new PdfDocument.PageInfo.Builder(PAGE_W,PAGE_H,1).create());
            drawCover(cover.getCanvas(),guide);pdf.finishPage(cover);
            PdfDocument.Page checklist=pdf.startPage(new PdfDocument.PageInfo.Builder(PAGE_W,PAGE_H,2).create());
            drawWorkflowPage(checklist.getCanvas(),guide);pdf.finishPage(checklist);
            for(int i=0;i<guide.pages.size();i++){
                if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
                PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(PAGE_W,PAGE_H,i+3).create());
                drawLayerPage(page.getCanvas(),guide,guide.pages.get(i),i+3);
                pdf.finishPage(page);
            }
            pdf.writeTo(output);output.flush();
        }finally{pdf.close();}
    }

    private static Paint textPaint(float size,int color,boolean bold){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setTextSize(size);
        p.setTypeface(bold?android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD):
            android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL));
        return p;
    }
    private static void drawCover(Canvas c,Document guide){
        c.drawColor(Color.WHITE);
        Paint title=textPaint(25,INK,true),body=textPaint(10.5f,INK,false),small=textPaint(9,MUTED,false);
        c.drawText("VinylStudio Assembly Guide",36,48,title);
        c.drawText(guide.plan.projectName,36,77,textPaint(16,ACCENT,true));
        c.drawText(guide.plan.styleName,36,98,textPaint(11,MUTED,false));
        String size=String.format(Locale.US,"Finished artwork: %.2f × %.2f in  (%.1f × %.1f mm)",
            guide.plan.widthMm/25.4f,guide.plan.heightMm/25.4f,guide.plan.widthMm,guide.plan.heightMm);
        c.drawText(size,36,122,body);
        Paint swatch=new Paint(Paint.ANTI_ALIAS_FLAG);swatch.setColor(guide.plan.mountingColor);
        c.drawRoundRect(new RectF(36,136,58,158),4,4,swatch);
        swatch.setStyle(Paint.Style.STROKE);swatch.setStrokeWidth(1);swatch.setColor(0xffa7b0b8);
        c.drawRoundRect(new RectF(36,136,58,158),4,4,swatch);
        c.drawText("Mounting surface "+ExportColorLabels.hex(guide.plan.mountingColor),66,152,body);
        drawArtwork(c,guide.composite,new RectF(36,174,576,520));
        float y=548;
        c.drawText("Application order",36,y,textPaint(13,INK,true));y+=20;
        String order=guide.plan.referenceOnly?
            "REFERENCE ONLY: registration positions failed the physical-cut check. This PDF omits all registration marks and must not be used as a production alignment template.":
            guide.plan.vinylLayerCount()>0?
            "Apply the numbered vinyl sheets from bottom to top. Align every registration mark before burnishing the next color.":
            "Complete the numbered pen or engraving operations in order. These pages are drawing paths, not weedable vinyl silhouettes.";
        y=drawWrapped(c,order,36,y,540,13,body)+9;
        String marks=guide.plan.referenceOnly?
            "Registration: UNSAFE / OMITTED. Correct mark positions and export SVG cut sheets before applying layers.":
            guide.plan.registration?
            "Registration: "+guide.plan.registrationShape+String.format(Locale.US," · %.1f mm · identical centers on every vinyl page.",guide.plan.registrationSizeMm):
            "Registration marks are off. Use the common artwork bounds and physical size to align layers.";
        y=drawWrapped(c,marks,36,y,540,13,body)+8;
        y=drawWrapped(c,"Material planning: "+guide.material.summary(),36,y,540,13,body)+4;
        y=drawWrapped(c,guide.material.disclaimer(),36,y,540,12,small)+4;
        drawWrapped(c,"Keep every SVG at its original physical dimensions in cutter software. Test-cut a representative detail before the full sheet.",36,y,540,13,body);
        footer(c,1,guide.pages.size()+2);
    }
    /** One printable production checklist for all physical operations.
     * No checkbox is ticked automatically: the operator must verify actual cuts.
     * Work from immutable assembly order; colors are displayed, not inferred.
     */
    private static void drawWorkflowPage(Canvas c,Document guide){
        c.drawColor(Color.WHITE);
        c.drawText("Production and assembly checklist",36,49,textPaint(21,INK,true));
        Paint body=textPaint(10,INK,false),muted=textPaint(9,MUTED,false);
        c.drawText(guide.plan.projectName,36,72,textPaint(12,ACCENT,true));
        c.drawText(String.format(Locale.US,"Artboard %.2f × %.2f in · %d vinyl sheets · %d total operations",
            guide.material.sheetWidthMm/25.4f,guide.material.sheetHeightMm/25.4f,
            guide.material.vinylSheets,guide.pages.size()),36,91,body);
        float y=117;
        y=drawWrapped(c,"Inspect each imported file at its intended dimensions. Verify the calibration square and registration centers before cutting.",
            36,y,540,13,body)+14;
        c.drawText("LAYER / OPERATION",36,y,muted);
        c.drawText("CUT",385,y,muted);c.drawText("WEED",445,y,muted);c.drawText("APPLY",515,y,muted);
        y+=16;
        for(AssemblyGuidePlan.Layer layer:guide.plan.layers){
            if(y>647)throw new IllegalArgumentException("Too many layer rows for checklist");
            Paint swatch=new Paint(Paint.ANTI_ALIAS_FLAG);swatch.setColor(layer.color);
            c.drawRect(36,y-9,50,y+5,swatch);
            String description=layer.physicalIndex+". "+layer.name;
            if(description.length()>49)description=description.substring(0,46)+"...";
            c.drawText(description,58,y+1,body);
            if(layer.exported){
                c.drawText("[   ]",385,y+1,body);
                c.drawText(layer.kind==AssemblyGuidePlan.Kind.VINYL?"[   ]":"N/A",445,y+1,body);
                c.drawText(layer.kind==AssemblyGuidePlan.Kind.VINYL?"[   ]":"N/A",515,y+1,body);
            }else c.drawText("Not exported",385,y+1,muted);
            y+=28;
        }
        y+=10;
        y=drawWrapped(c,"Before layering: check each color against the physical vinyl roll/sheet. RGB previews are not proof of material match.",
            36,y,540,13,body)+7;
        y=drawWrapped(c,guide.plan.registration?
            "Align the same registration marks on each sheet before burnishing; measure at least one horizontal and vertical mark distance.":
            "Registration marks are OFF: verify alignment against the original physical artboard dimensions.",
            36,y,540,13,body)+7;
        y=drawWrapped(c,"After layering: inspect all small islands and edges; remove registration marks only after the final layer is secure.",
            36,y,540,13,body);
        if(guide.plan.referenceOnly)
            c.drawText("REFERENCE ONLY — NOT A VERIFIED CUTTING OR REGISTRATION TEMPLATE",36,736,textPaint(10,0xffaf3030,true));
        footer(c,2,guide.pages.size()+2);
    }

    private static void drawLayerPage(Canvas c,Document guide,LayerPage page,int pageNumber){
        c.drawColor(Color.WHITE);AssemblyGuidePlan.Layer layer=page.layer;
        Paint title=textPaint(21,INK,true),body=textPaint(10.5f,INK,false),small=textPaint(9,MUTED,false);
        c.drawText("Step "+layer.physicalIndex+" of "+guide.pages.size(),36,46,textPaint(11,ACCENT,true));
        c.drawText(layer.name,36,74,title);
        c.drawText(layer.operationLabel(),36,96,small);
        Paint swatch=new Paint(Paint.ANTI_ALIAS_FLAG);swatch.setColor(layer.color);
        c.drawRoundRect(new RectF(36,112,70,146),5,5,swatch);
        swatch.setStyle(Paint.Style.STROKE);swatch.setStrokeWidth(1);swatch.setColor(0xffa7b0b8);
        c.drawRoundRect(new RectF(36,112,70,146),5,5,swatch);
        c.drawText(ExportColorLabels.hex(layer.color),82,127,body);
        c.drawText(layer.countLabel(),82,144,small);
        if(layer.kind==AssemblyGuidePlan.Kind.VINYL)
            c.drawText(String.format(Locale.US,"Blank: %.2f × %.2f in (full sheet, no extra handling margin)",
                guide.material.sheetWidthMm/25.4f,guide.material.sheetHeightMm/25.4f),270,144,small);
        if(page.metrics!=null){
            try{
                ArtworkCutMetrics.Stats m=page.metrics.get();
                if(m.pathSummary)
                    c.drawText(String.format(Locale.US,"Artwork paths: %d closed · %d open · approx. %.1f mm total length",m.loops,m.openPaths,m.lengthMm),36,158,small);
                else c.drawText(String.format(Locale.US,"Artwork only: %d regions · %d holes · %.1f mm cut length",m.regions,m.holes,m.lengthMm),36,158,small);
            }catch(java.util.concurrent.CancellationException cancelled){throw cancelled;}
            catch(RuntimeException unavailable){c.drawText("Detailed artwork measurements unavailable",36,158,small);}
        }
        drawArtwork(c,page.artwork,new RectF(36,166,576,626));
        float y=652;
        String instruction;
        if(guide.plan.referenceOnly){
            instruction="Reference only - NOT TO SCALE. Registration marks omitted because their placement is unsafe. Correct the marks before cutting.";
        }else if(layer.kind==AssemblyGuidePlan.Kind.VINYL){
            instruction=layer.physicalIndex==1?
                "Place this bottom sheet first. Keep the carrier square to the mounting surface.":
                "Align this sheet to the previous registration marks, hinge it in place, then burnish from the center outward.";
        }else if(layer.kind==AssemblyGuidePlan.Kind.PEN_OR_ENGRAVE){
            instruction="Use this as a pen or engraving path. Do not treat the centerline as a closed vinyl cut.";
        }else instruction="This page is a visual reference and is not an applied material layer.";
        y=drawWrapped(c,instruction,36,y,540,13,body)+7;
        if(!layer.note.isEmpty())drawWrapped(c,layer.note,36,y,540,12,small);
        footer(c,pageNumber,guide.pages.size()+2);
    }
    private static void drawArtwork(Canvas c,ArtworkDocumentExport.Page artwork,RectF box){
        Paint border=new Paint(Paint.ANTI_ALIAS_FLAG);border.setStyle(Paint.Style.FILL);border.setColor(0xfff4f6f8);
        c.drawRoundRect(box,7,7,border);border.setStyle(Paint.Style.STROKE);border.setStrokeWidth(1);border.setColor(0xffcbd3da);
        c.drawRoundRect(box,7,7,border);
        float inset=10f,availableW=box.width()-2*inset,availableH=box.height()-2*inset;
        float scale=Math.min(availableW/artwork.widthMm,availableH/artwork.heightMm);
        if(!Float.isFinite(scale)||scale<=0)throw new IllegalArgumentException("Invalid guide artwork size");
        float drawW=artwork.widthMm*scale,drawH=artwork.heightMm*scale;
        float left=box.left+(box.width()-drawW)/2f,top=box.top+(box.height()-drawH)/2f;
        c.save();c.clipRect(box.left+1,box.top+1,box.right-1,box.bottom-1);
        c.translate(left,top);c.scale(scale,scale);artwork.drawing.draw(c);c.restore();
        c.drawText("Illustration not to scale",box.left+10,box.bottom-6,textPaint(8.5f,MUTED,false));
    }
    private static float drawWrapped(Canvas c,String value,float x,float y,float maxWidth,float lineHeight,Paint paint){
        if(value==null||value.trim().isEmpty())return y;
        StringBuilder line=new StringBuilder();
        for(String word:value.trim().split("\\s+")){
            String candidate=line.length()==0?word:line+" "+word;
            if(line.length()>0&&paint.measureText(candidate)>maxWidth){
                c.drawText(line.toString(),x,y,paint);y+=lineHeight;line.setLength(0);line.append(word);
            }else{if(line.length()>0)line.append(' ');line.append(word);}
        }
        if(line.length()>0){c.drawText(line.toString(),x,y,paint);y+=lineHeight;}
        return y;
    }
    private static void footer(Canvas c,int page,int total){
        Paint p=textPaint(8.5f,MUTED,false);
        c.drawText("VinylStudio · Verify scale and test cut before production",36,766,p);
        p.setTextAlign(Paint.Align.RIGHT);c.drawText(page+" / "+total,576,766,p);p.setTextAlign(Paint.Align.LEFT);
    }
}

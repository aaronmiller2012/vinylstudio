package com.halftonestudio;
import java.util.*;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipOutputStream;
/** Poster nine-plate regression. Pop ID 46 remains reserved, never selectable. */
public final class V3PopPosterStylesTest {
    private static void ok(boolean condition,String why){if(!condition)throw new AssertionError(why);}
    static long fingerprint(StyleKit.Result r){long n=0xcbf29ce484222325L;
        for(StyleKit.Layer l:r.layers){n=(n^l.color)*0x100000001b3L;
            for(float[] p:l.loops)for(float v:p)n=(n^Float.floatToIntBits(v))*0x100000001b3L;}
        return n;
    }
    static void validate(StyleKit.Result r,float mm,int max){
        ok(r!=null&&!r.layers.isEmpty(),"no physical sheets");
        ok(r.layers.size()<=max,"more physical sheets than selected");
        ok(r.totalPoints()>100,"lost photographic detail");
        ok(Math.abs(r.widthMm-mm)<.2f,"physical art width drift");
        for(StyleKit.Layer l:r.layers){
            ok(!l.loops.isEmpty(),"empty physical sheet");ok(l.lines.isEmpty(),"unexpected open blade path");
            for(float[] polygon:l.loops){ok(polygon.length>=6&&(polygon.length&1)==0,"bad contour");
                for(int i=0;i<polygon.length;i+=2)
                    ok(Float.isFinite(polygon[i])&&Float.isFinite(polygon[i+1])&&
                        polygon[i]>=-.01f&&polygon[i]<=r.widthMm+.01f&&
                        polygon[i+1]>=-.01f&&polygon[i+1]<=r.heightMm+.01f,"out of bounds");
            }
        }
    }
    public static void main(String[] args)throws Exception{
        ok(VinylStyleSuite.isRetiredStyleId(VinylStyleSuite.POP_PANELS),"Pop Panels must be retired");
        ok(VinylStyleSuite.restoredStyleId(VinylStyleSuite.POP_PANELS)==-1,"saved Pop project must not reopen removed style");
        ok(!NewStyleVariantPolicy.selectable(VinylStyleSuite.POP_PANELS),"Pop still selectable");
        ok(NewStyleVariantPolicy.selectable(VinylStyleSuite.POSTER_STENCIL),"Poster missing");
        ok(NewStyleColorSlots.maximumSelectable(VinylStyleSuite.POSTER_STENCIL)==9,"Poster cap must be 9");
        ok(NewStyleColorSlots.slotCount(VinylStyleSuite.POSTER_STENCIL,0,9)==9,"missing swatch slot");
        ok(NewStyleLayerPolicy.isStructuralLayer(VinylStyleSuite.POSTER_STENCIL,0),"solid base not structural");
        ok(!NewStyleLayerPolicy.canDelete(VinylStyleSuite.POSTER_STENCIL,0,9),"L1 removable");
        ok(NewStyleLayerPolicy.canDelete(VinylStyleSuite.POSTER_STENCIL,8,9),"L9 cannot be removed");
        for(int count=2;count<=9;count++){
            int[] palette=PopPosterEngine.posterPalette(0,count);
            ok(palette.length==count,"wrong named-palette count");
            for(int i=0;i<count;i++)ok((palette[i]>>>24)==255,"transparent vinyl color");
        }
        int w=150,h=170;int[] pixels=V3AllStyleExportMatrixTest.portrait(w,h);
        boolean[] allowed=new boolean[pixels.length];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)allowed[y*w+x]=x>=5&&x<w-4&&y>=8&&y<h-9;
        VinylStyleSuite.Config cfg=new VinylStyleSuite.Config();cfg.style=VinylStyleSuite.POSTER_STENCIL;
        cfg.widthMm=254;cfg.detail=90;cfg.minimumMm=.8f;cfg.gapMm=.5f;
        cfg.colorCount=9;cfg.popPoster=PopPosterEngine.preset(PopPosterEngine.POSTER,0,254);cfg.allowed=allowed;
        int[] colors=PopPosterEngine.posterPalette(4,9);
        cfg.palette=colors.clone();
        int[] reversed=PaletteOrientation.reverseActive(colors,9,true,true);
        ok(reversed[0]==colors[0],"reverse shifted structural L1");
        ok(Arrays.equals(colors,PaletteOrientation.reverseActive(reversed,9,true,true)),"reverse twice changed colors");
        StyleKit.Result output=VinylStyleSuite.generate(pixels,w,h,cfg);
        validate(output,254f,9);
        ok(output.paperArgb==cfg.paper,"mounting surface changed with palette");
        ok(output.layers.size()==9,"nine detailed poster colors should populate 9 sheets, got "+output.layers.size());
        for(int slot=0;slot<9;slot++){
            StyleKit.Layer sheet=output.layers.get(slot);
            ok(NewStyleColorSlots.slot(cfg.style,sheet.name,slot)==slot,"physical layer ID drift");
            ok(sheet.color==colors[slot],"color-to-layer mapping L"+(slot+1));
        }
        ok(fingerprint(output)==fingerprint(VinylStyleSuite.generate(pixels,w,h,cfg)),"nondeterministic rendering");
        // Every physical swatch must reach the real shared SVG and R13 DXF writer.
        for(int index=0;index<9;index++){
            String svg=NewStyleExport.layerSvg(output,index,false,0,3f,2f,null,cfg.minimumMm);
            for(int device:new int[]{OutputDeviceProfile.GENERIC_SVG,OutputDeviceProfile.SILHOUETTE_BASIC}){
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(ZipOutputStream zip=new ZipOutputStream(bytes)){
                    CutSheetZipWriter writer=new CutSheetZipWriter(zip,device,cfg.minimumMm,true);
                    writer.put("poster-L"+(index+1)+".svg",svg);writer.finish();
                }
                ok(bytes.size()>100,"empty SVG/DXF physical sheet "+(index+1));
            }
        }
        // Edit L9 as through the ordinary color wheel. No edit may shift layer order.
        cfg.palette[8]=0xfffed301;
        StyleKit.Result edited=VinylStyleSuite.generate(pixels,w,h,cfg);
        ok(edited.layers.get(8).color==0xfffed301,"last swatch edit ignored");
        for(int i=0;i<8;i++)ok(edited.layers.get(i).color==colors[i],"earlier palette unexpectedly overwritten");
        // Duplicate colors must remain separate L4/L5, not be collapsed as pop sheets were.
        cfg.palette[4]=cfg.palette[3];
        StyleKit.Result duplicates=VinylStyleSuite.generate(pixels,w,h,cfg);
        ok(duplicates.layers.size()==9,"identical HEX collapsed two physical sheets");
        ok(StyleLayerOperations.bySlot(duplicates,cfg.style,0,9).length==9,"cut-count slot mismatch");
        cfg.popPoster.mode=PopPosterEngine.KNOCKOUT;
        StyleKit.Result knockout=VinylStyleSuite.generate(pixels,w,h,cfg);
        validate(knockout,254,9);
        ok(!knockout.layers.get(0).loops.isEmpty(),"knockout destroyed solid L1");
        cfg.palette=null;cfg.popPoster.mode=PopPosterEngine.STACKED;
        StyleKit.Result automatic=VinylStyleSuite.generate(pixels,w,h,cfg);
        validate(automatic,254f,9);
        ok(automatic.layers.size()==9,"photo palette lost layers");
        ok(!Arrays.equals(pixels,new int[pixels.length]),"test fixture invalid");
        ok(Arrays.equals(pixels,V3AllStyleExportMatrixTest.portrait(w,h)),"photo mutated");
        boolean invalid=false;try{PopPosterEngine.Options bad=new PopPosterEngine.Options();bad.blur=Float.NaN;bad.check();}catch(IllegalArgumentException expected){invalid=true;}
        ok(invalid,"invalid params accepted");
        System.out.println("PASS Poster nine physical layers, palette, selection, masks, export-slot mapping and Pop retirement");
    }
}

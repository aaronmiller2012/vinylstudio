package com.halftonestudio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * M5 export profiles for the whole Prismatic family on the PRODUCTION vector route (JTS):
 *  - Cricut ZIP, registration marks on, selected 12x12 mat: every sheet plus the single-upload
 *    SVG, mat note and safety CSV; the single-upload SVG carries one group per colour.
 *  - Silhouette Basic DXF R13: per-sheet board size and every contour kept.
 *  - Each SVG cutter profile accepts the same sheets.
 *  - Assembly material plan built from the same board.
 *  - Final cut geometry measured by the writer's own inspector (FinalCutSheetSafety):
 *    no critical, narrow or unverified finding on this fixture, so no inspection-only export.
 * PNG and PDF rendering use Android graphics and are covered on the device only.
 */
public final class PrismaticExportProfilesTest {
    static int checks=0;
    static void check(boolean ok,String what){checks++;if(!ok)throw new AssertionError(what);}
    static final int[] STYLES={VinylStyleSuite.PRISMATIC_DIAGONAL,VinylStyleSuite.PRISMATIC_CONTOUR_FLOW,
        VinylStyleSuite.PRISMATIC_TWILL,VinylStyleSuite.PRISMATIC_SILK,VinylStyleSuite.PRISMATIC_BRAIDED_FLOW,
        VinylStyleSuite.PRISMATIC_CHEVRON};
    static final int[] PALETTE={0xfff4ece0,0xffd9a07a,0xffb0583c,0xff6a7fa8,0xff3b3550,0xff1c1a20};

    static Map<String,byte[]> unzip(byte[] bytes)throws IOException{
        Map<String,byte[]> files=new HashMap<>();
        try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(bytes))){
            for(ZipEntry z;(z=in.getNextEntry())!=null;)files.put(z.getName(),in.readAllBytes());
        }
        return files;
    }
    interface Write{void sheets(CutSheetZipWriter w)throws IOException;}
    static Map<String,byte[]> zip(int device,String mat,Write write,CutSheetZipWriter[] keep)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(out)){
            CutSheetZipWriter w=new CutSheetZipWriter(zip,device,.8f,.5f,true);
            if(mat!=null)w.selectMat(mat);
            write.sheets(w);w.finish();
            if(keep!=null)keep[0]=w;
        }
        return unzip(out.toByteArray());
    }

    public static void main(String[] args)throws Exception{
        VectorBooleanEngine.install(new JtsVectorBooleanBackend());
        int w=72,h=90;int[] photo=V3AllStyleExportMatrixTest.portrait(w,h);
        VinylEngine.Settings d=VinylEngine.Settings.defaults();
        StringBuilder report=new StringBuilder();
        for(int id:STYLES){
            VinylStyleSuite.Config c=new VinylStyleSuite.Config();
            c.style=id;c.widthMm=152.4f;c.detail=55;c.colorCount=6;c.minimumMm=.8f;
            c.gapMm=VinylStyleSuite.generationGapMm(.5f,.8f);c.paper=0xfffaf9f5;c.palette=PALETTE.clone();
            StyleKit.Result r=VinylStyleSuite.generate(photo,w,h,c);
            String name=VinylStyleSuite.NAMES[id];
            check(r.summary.contains("cut conditioning"),name+": production route must be the conditioned vector route: "+r.summary);
            Write sheets=cuts->{
                for(int i=0;i<r.layers.size();i++)if(!r.layers.get(i).loops.isEmpty())
                    cuts.put(String.format(Locale.US,"%02d.svg",i+1),
                        NewStyleExport.layerSvg(r,i,true,0,d.markSizeMm,d.registrationClearanceMm,null,.8f));
            };
            CutSheetZipWriter[] kept=new CutSheetZipWriter[1];
            Map<String,byte[]> cricut=zip(OutputDeviceProfile.CRICUT,CricutMatPreflight.MAT_12X12,sheets,kept);
            int layers=0;for(StyleKit.Layer l:r.layers)if(!l.loops.isEmpty())layers++;
            int svgSheets=0;for(String n:cricut.keySet())if(n.matches("\\d\\d\\.svg"))svgSheets++;
            check(svgSheets==layers,name+": one Cricut sheet per non-empty layer ("+svgSheets+"/"+layers+")");
            byte[] one=cricut.get(CombinedLayerSvg.FILENAME);
            check(one!=null,name+": single-upload SVG written");
            String combined=new String(one,StandardCharsets.UTF_8);
            CutSvgCompatibility.verify(combined,OutputDeviceProfile.CRICUT);
            for(StyleKit.Layer l:r.layers)if(!l.loops.isEmpty())
                check(combined.toUpperCase(Locale.ROOT).contains(SheetFrame.hex(l.color)),name+": single upload keeps colour "+SheetFrame.hex(l.color));
            check(new String(cricut.get(CricutMatPreflight.NOTE_FILE),StandardCharsets.UTF_8).startsWith("Selected mat: 12 × 12 in"),name+": mat note");
            check(cricut.containsKey("cut-sheet-safety.csv")&&cricut.containsKey("physical-artboards.csv"),name+": safety and artboard reports");
            int critical=kept[0].criticalFindingCount(),narrow=kept[0].narrowFindingCount(),unknown=kept[0].unknownFindingCount();
            check(critical==0,name+": final sheets must not contain crossing or invalid contours ("+critical+")");
            // M5: the conditioner separates near-touching saddles, so this fixture exports
            // without an inspection-only review on every Prismatic style.
            check(narrow==0&&unknown==0,name+": final sheets need no cut review ("+narrow+" narrow, "+unknown+" unverified)");
            report.append(String.format(Locale.US,"  %-30s sheets=%d critical=%d narrow=%d unverified=%d review_sheets=%d%n",
                name,svgSheets,critical,narrow,unknown,kept[0].reviewSheetCount()));

            Map<String,byte[]> dxf=zip(OutputDeviceProfile.SILHOUETTE_BASIC,null,sheets,null);
            String dims=new String(dxf.get("dimensions-dxf.txt"),StandardCharsets.UTF_8);
            SheetFrame frame=NewStyleExport.frame(r,d.markSizeMm,d.registrationClearanceMm,null,false);
            check(dims.contains(String.format(Locale.US,"%.3f x %.3f mm",frame.boardWidthMm(),frame.boardHeightMm())),
                name+": DXF reference artboard equals the SVG board");
            for(int i=0;i<r.layers.size();i++)if(!r.layers.get(i).loops.isEmpty()){
                SvgDxfR13Exporter.Result conv=SvgDxfR13Exporter.convert(
                    NewStyleExport.layerSvg(r,i,true,0,d.markSizeMm,d.registrationClearanceMm,null,.8f));
                check(conv.closedContours>=r.layers.get(i).loops.size(),name+": DXF keeps every contour of layer "+(i+1));
            }
            for(int device:new int[]{OutputDeviceProfile.SILHOUETTE_DESIGNER,OutputDeviceProfile.BROTHER_CANVAS,
                    OutputDeviceProfile.GENERIC_SVG,OutputDeviceProfile.GRAPHTEC_STUDIO,OutputDeviceProfile.SISER_LEONARDO}){
                Map<String,byte[]> z=zip(device,null,sheets,null);
                check(z.containsKey("01.svg")&&!z.containsKey(CricutMatPreflight.NOTE_FILE),
                    name+": "+OutputDeviceProfile.byId(device).name+" profile");
            }
            AssemblyGuidePlan.Builder plan=new AssemblyGuidePlan.Builder(name,"Portrait",r.widthMm,r.heightMm,r.paperArgb)
                .registration(true,"cross",d.markSizeMm);
            for(int i=0;i<r.layers.size();i++)
                plan.addLayer(new AssemblyGuidePlan.Layer(i+1,r.layers.get(i).name,r.layers.get(i).color,r.layers.get(i).loops.size(),true));
            AssemblyMaterialPlan material=AssemblyMaterialPlan.from(plan.build(),frame.boardWidthMm(),frame.boardHeightMm());
            check(material!=null,name+": assembly material plan from the cut board");
        }
        System.out.print(report);
        System.out.println("PASS Prismatic export profiles ("+checks+" checks): Cricut + single upload, DXF, SVG profiles, assembly plan");
    }
}

package com.halftonestudio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Milestone A host geometry repairs + C1 cut-production complexity manifest.
 * This is not a cutter test, nor proof of real-portrait photographic quality.
 */
public final class V3MilestoneABWrapC1Test {
    private static void require(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    private static String svg(String content){return "<svg xmlns='http://www.w3.org/2000/svg' width='285mm' height='285mm' viewBox='0 0 285 285'>"+content+"</svg>";}
    private static byte[] zip(String svg,int target) throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream out=new ZipOutputStream(bytes)){
            CutSheetZipWriter writer=new CutSheetZipWriter(out,target,.30f,true);
            writer.put("sheet.svg",svg);
            writer.finish();
        }
        return bytes.toByteArray();
    }
    private static String entry(byte[] bytes,String name)throws Exception{
        try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(bytes))){
            ZipEntry e;while((e=z.getNextEntry())!=null)if(name.equals(e.getName()))
                return new String(z.readAllBytes(),StandardCharsets.UTF_8);
        }
        throw new AssertionError("Missing ZIP member "+name);
    }
    private static void ribbonRepair(){
        Random random=new Random(112358);
        int fixed=0,stillUnsafe=0,safeUnchanged=0;
        for(int i=0;i<1500;i++){
            ArrayList<float[]> run=new ArrayList<>();
            double phase=random.nextDouble()*Math.PI*2;
            double freq=random.nextDouble()*.9+.2;
            double amplitude=random.nextDouble()*4+.3;
            float width=(float)(random.nextDouble()*1.6+.3);
            for(int j=0;j<26;j++)run.add(new float[]{(float)(j*.85),
                (float)(amplitude*Math.sin(j*freq+phase)+.15*j),width});
            float[] baseline=LineRibbonRepair.outline(run);
            LineRibbonRepair.Crossing before=LineRibbonRepair.findCrossing(baseline);
            float[] repaired=LineRibbonRepair.safeOutline(run,.254f);
            LineRibbonRepair.Crossing after=LineRibbonRepair.findCrossing(repaired);
            if(before==null){
                require(Arrays.equals(baseline,repaired),"Safe ribbon geometry changed");
                safeUnchanged++;
            } else if(!before.incomplete){
                if(after==null)fixed++;
                else {
                    require(Arrays.equals(baseline,repaired),"Unrepairable contour modified or hidden");
                    stillUnsafe++;
                }
            }
            for(int j=0;j<run.size();j++){
                // At the matching upper/lower offset pair the cut is still
                // at least the material width, never narrowed to a hairline.
                int n=run.size();
                double thick=Math.hypot(repaired[j*2]-repaired[2*(2*n-1-j)],
                    repaired[j*2+1]-repaired[2*(2*n-1-j)+1]);
                require(thick>=.254f-.003f,"Repair introduced subminimum neck");
            }
        }
        require(fixed>=500&&stillUnsafe>0&&safeUnchanged>=900,"Missing repair coverage");
        System.out.println("A/B bounded ribbons: repaired="+fixed+" unchanged="+safeUnchanged+
            " unrepairable retained for fail-closed="+stillUnsafe);
    }
    private static void mixedPrimitives(){
        int[] src=new int[32*32];Arrays.fill(src,0xff555555);
        VinylEngine.Settings settings=VinylEngine.Settings.defaults();
        settings.pattern=VinylEngine.LINE;settings.classicCmykLines=false;
        settings.registration=false;settings.widthMm=100;
        VinylEngine.Result r=VinylEngine.generate(src,32,32,settings);
        r.shapes.get(0).clear();
        r.shapes.get(0).add(VinylEngine.Shape.polygon(40,40,60,40,60,60,40,60));
        r.shapes.get(0).add(VinylEngine.Shape.circle(20,20,2));
        java.util.List<StyleCutGeometryPreflight.Warning> clear=StandardCutSafety.inspect(r,.30f,60);
        require(clear.stream().noneMatch(x->x.reason.contains("circle/polygon")),"Separated primitives flagged");
        r.shapes.get(0).set(1,VinylEngine.Shape.circle(38,50,2));
        java.util.List<StyleCutGeometryPreflight.Warning> tangent=StandardCutSafety.inspect(r,.30f,60);
        require(tangent.stream().anyMatch(x->x.reason.contains("Intersecting circle and polygon")),"Tangent escaped preflight");
        r.shapes.get(0).set(1,VinylEngine.Shape.circle(37.8f,50,2));
        java.util.List<StyleCutGeometryPreflight.Warning> near=StandardCutSafety.inspect(r,.30f,60);
        require(near.stream().anyMatch(x->x.reason.contains("physical clearance")),"Near cut gap escaped preflight");
        r.shapes.get(0).set(1,VinylEngine.Shape.circle(50,50,3));
        java.util.List<StyleCutGeometryPreflight.Warning> nested=StandardCutSafety.inspect(r,.30f,60);
        require(nested.stream().noneMatch(x->x.reason.contains("circle/polygon")),"Safe nested hole mislabeled");
    }
    private static void complexity() throws Exception{
        byte[] clear=zip(svg("<rect x='5' y='5' width='20' height='20'/><circle cx='60' cy='60' r='3'/>"),OutputDeviceProfile.CRICUT);
        String report=entry(clear,"cut-complexity.csv");
        require(report.contains("sheet.svg,SVG,285.000,285.000,1,1,0,2,5000,COUNTED"),"Missing C1 output count");
        require(entry(clear,"cut-sheet-safety.csv").contains("INSPECTED"),"A1 safety report lost");
        // More than the documented Cricut SVG path limit; no automatic removal.
        StringBuilder many=new StringBuilder();
        for(int i=0;i<5001;i++){
            double x=5+(i%75)*3.65,y=5+(i/75)*3.65;
            many.append("<circle cx='").append(x).append("' cy='").append(y).append("' r='0.9'/>");
        }
        boolean blocked=false;
        try{zip(svg(many.toString()),OutputDeviceProfile.CRICUT);}
        catch(IOException expected){
            // Either hard stop may run first: the compatibility preflight
            // rejects >5000 SVG XML shapes; the final-sheet oracle rejects
            // >5000 physical contours. Both are fail-closed export results.
            String message=expected.getMessage();
            blocked=message!=null&&(message.contains("5000-contour")
                ||message.contains("at most 5000 per upload"));
        }
        require(blocked,"Oversize Cricut SVG must not be published");
        byte[] generic=zip(svg(many.toString()),OutputDeviceProfile.GENERIC_SVG);
        require(entry(generic,"cut-complexity.csv").contains("5001,not_applicable"),
            "Generic SVG export may not impose Cricut-only limits");
        System.out.println("C1: 2-contour report, 5001-contour Cricut reject, generic SVG 5001-contour manifest PASS");
    }
    public static void main(String[] args) throws Exception{
        ribbonRepair();mixedPrimitives();complexity();
        System.out.println("PASS Milestone A continuity repair, mixed-edge preflight, C1 per-sheet complexity handoff");
    }
}

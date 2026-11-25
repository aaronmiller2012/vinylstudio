package com.halftonestudio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * M5 export / mat contract, driven by real engines and parsed-back output.
 *  1. Compact above/below registration layout (pure function shared by preview and export).
 *  2. Mat enforcement on every ZIP path (standard, suite, combined single-upload SVG, DXF, other cutters).
 *  3. Export audit: mm viewBox/width, DXF $INSUNITS=4 and mm coordinates, closed contours, fixtures.
 */
public final class M5ExportMatTest {
    static int checks=0;
    static void check(boolean ok,String what){checks++;if(!ok)throw new AssertionError(what);}
    static final double USABLE=11.5*25.4;
    static final int CIRCLE=RegistrationMarks.CIRCLE;

    static final class Zip {
        IOException rejected;Map<String,String> files=new HashMap<>();double w,h;
    }
    interface Sheets{void write(CutSheetZipWriter cuts)throws IOException;}
    static Zip export(int device,String mat,Sheets sheets)throws IOException{
        Zip z=new Zip();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)){
            CutSheetZipWriter cuts=new CutSheetZipWriter(zip,device,.8f,.5f,true);
            if(mat!=null)cuts.selectMat(mat);
            try{sheets.write(cuts);cuts.finish();}catch(IOException e){z.rejected=e;return z;}
        }
        try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))){
            for(ZipEntry e;(e=in.getNextEntry())!=null;)z.files.put(e.getName(),new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }
        String[] row=z.files.get("physical-artboards.csv").split("\n")[1].split(",");
        z.w=Double.parseDouble(row[1]);z.h=Double.parseDouble(row[2]);
        return z;
    }

    static VinylEngine.Settings std(float inches,float[][] marks){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.widthMm=inches*25.4f;s.registration=true;s.markStyle=CIRCLE;s.marks=marks;
        return s;
    }
    static Sheets standard(VinylEngine.Settings s,int w,int h){
        return cuts->{
            VinylEngine.Result r=VinylEngine.generate(V3AllStyleExportMatrixTest.portrait(w,h),w,h,s);
            for(int i=0;i<r.settings.layers.size();i++)if(VinylEngine.cutLayer(r,i))
                cuts.put(String.format(Locale.US,"%02d.svg",i+1),VinylEngine.svg(r,i));
        };
    }
    static StyleKit.Result suiteResult(float inches,int w,int h,int colors){
        VinylStyleSuite.Config c=new VinylStyleSuite.Config();
        c.style=VinylStyleSuite.PRISMATIC_CHEVRON;c.widthMm=inches*25.4f;c.detail=40;c.colorCount=colors;c.minimumMm=.8f;
        c.gapMm=VinylStyleSuite.generationGapMm(.5f,.8f);c.paper=0xfffaf9f5;
        int[] pal={0xfff4ece0,0xffd9a07a,0xff6a7fa8,0xff1c1a20,0xff884422};
        c.palette=java.util.Arrays.copyOf(pal,colors);
        return VinylStyleSuite.generate(V3AllStyleExportMatrixTest.portrait(w,h),w,h,c);
    }
    static Sheets suite(StyleKit.Result r,float[][] marks,float markMm){
        return cuts->{
            for(int i=0;i<r.layers.size();i++)if(!r.layers.get(i).loops.isEmpty())
                cuts.put(String.format(Locale.US,"%02d.svg",i+1),
                    NewStyleExport.layerSvg(r,i,true,CIRCLE,markMm,1f,marks,.8f,false));
        };
    }

    /** Circle centres (artboard mm, Y down) of radius markMm/2 found in one sheet. */
    static List<double[]> markCircles(String svg,double markMm)throws IOException{
        SvgDxfR13Exporter.SafetyGeometry g=SvgDxfR13Exporter.safetyGeometry(svg);
        List<double[]> out=new java.util.ArrayList<>();
        for(double[] c:g.circles)if(Math.abs(c[2]-markMm/2)<.002)out.add(new double[]{c[0],g.heightMm-c[1]});
        return out;
    }
    static boolean has(List<double[]> found,double x,double y){
        for(double[] f:found)if(Math.abs(f[0]-x)<.01&&Math.abs(f[1]-y)<.01)return true;
        return false;
    }
    static boolean throwsIae(Runnable r){try{r.run();return false;}catch(IllegalArgumentException e){return true;}}

    static void layoutContract(){
        float d=2.5f,clr=1f,w=279.4f,h=186.3f;
        float[][] m=RegistrationMarks.aboveBelowPositions(w,h,d,clr);
        float pad=RegistrationMarks.compactPaddingMm(d,clr);
        check(Math.abs(pad-6f)<1e-4,"compact margin for 2.5 mm mark / 1 mm clearance is 6.0 mm: "+pad);
        check(RegistrationMarks.isAboveBelow(m,w,h,d,clr),"recognises its own positions");
        check(java.util.Arrays.deepEquals(m,RegistrationMarks.aboveBelowPositions(w,h,d,clr)),"pure: zoom/pan are not inputs");
        RegistrationMarks.Layout lay=RegistrationMarks.layout(w,h,d,clr,m,8f);
        check(Math.abs(lay.paddingMm-pad)<1e-4,"layout margin equals compact margin, outer-spacing floor not applied");
        RegistrationMarks.requireOutsideArtwork(lay,w,h,d,clr);
        for(float[] p:lay.positions){check(p[1]<0||p[1]>h,"mark above or below the artwork");check(p[0]>=0&&p[0]<=w,"no side overhang");}
        check(Math.abs(RegistrationMarks.layout(w,h,d,clr,null,8f).paddingMm-8f)<1e-4,"default corners unchanged (8 mm)");
        check(RegistrationCutGeometry.basic(w,h,d,clr,m).valid(),"compact marks pass the authoring corridor");
        float[][] manual=RegistrationMarks.aboveBelowPositions(w,h,d,clr);manual[0][0]+=3f;
        RegistrationMarks.Layout lm=RegistrationMarks.layout(w,h,d,clr,manual,8f);
        check(lm.paddingMm>=8f,"a hand-moved mark keeps the 8 mm floor");
        check(lm.positions[0][0]==manual[0][0]&&lm.positions[1][0]==manual[1][0],"manual positions preserved exactly");
        float[][] saved={{-9,-9},{w+9,-9},{-9,h+9},{w+9,h+9}};
        check(java.util.Arrays.deepEquals(saved,RegistrationMarks.resolveSaved(1,saved,w,h,d,clr,false)),"restoration never replaces saved marks");
        check(RegistrationMarks.resolveSaved(0,null,w,h,d,clr,false)==null,"legacy: no stored marks stays null");
        check(RegistrationMarks.isAboveBelow(RegistrationMarks.resolveSaved(1,null,w,h,d,clr,false),w,h,d,clr),"placement 1 with no marks resolves compact");
        check(RegistrationMarks.normalizePlacement(7)==0&&RegistrationMarks.normalizePlacement(-1)==0&&RegistrationMarks.normalizePlacement(1)==1,"unknown saved placement -> legacy corners");
        check(throwsIae(()->RegistrationMarks.layout(w,h,d,clr,new float[][]{{Float.NaN,0},{1,1},{2,2},{3,3}},8f)),"NaN mark rejected");
        check(throwsIae(()->RegistrationMarks.layout(w,h,d,clr,new float[][]{{1e9f,0},{0,1e9f},{-1e9f,0},{0,-1e9f}},8f)),"extreme marks rejected, sheet never inflated");
        check(throwsIae(()->RegistrationMarks.layout(w,h,d,clr,new float[][]{{0,0}},8f)),"wrong mark count rejected");
        check(throwsIae(()->RegistrationMarks.layout(8f,50f,d,clr,RegistrationMarks.aboveBelowPositions(8f,50f,d,clr),8f)),"too-narrow artwork refused, not shifted");
    }

    static void matAcceptance()throws IOException{
        int w=84,h=60;
        float d=RegistrationMarks.DEFAULT_DIAMETER_MM,clr=1f;
        for(int colors:new int[]{2,4}){
            float wmm=11f*25.4f,hmm=wmm*h/w;
            float[][] compact=RegistrationMarks.aboveBelowPositions(wmm,hmm,d,clr);
            VinylEngine.Settings sc=std(11f,compact);sc.outerSpacingMm=8f;
            Zip ok=export(OutputDeviceProfile.CRICUT,"12x12",standard(sc,w,h));
            check(ok.rejected==null,"standard 11in landscape + compact marks fits 12x12: "+(ok.rejected==null?"":ok.rejected.getMessage()));
            check(ok.w<=USABLE+.001&&ok.h<=USABLE+.001&&Math.abs(ok.w-291.4)<.05,"standard sheet 291.4 mm wide: "+ok.w);
            check(Math.abs(ok.w-(wmm+12))<.02,"artwork exactly 11.000 in, not resized");
            int layers=0;
            for(Map.Entry<String,String> e:ok.files.entrySet())if(e.getKey().matches("\\d\\d\\.svg")){
                layers++;
                List<double[]> found=markCircles(e.getValue(),d);
                for(float[] p:compact)check(has(found,p[0]+6.0,p[1]+6.0),e.getKey()+": mark at pure-function position + margin");
            }
            check(layers>=2,"multi-layer standard export ("+layers+")");
            SheetFrame fr=SheetFrame.of(wmm,hmm,true,d,clr,compact,8f,false);
            check(java.util.Arrays.deepEquals(fr.marks(),compact)&&Math.abs(fr.paddingMm-6f)<1e-4,"SheetFrame equals pure function");
            check(CricutSizeAdvisor.estimate(11,w,h,true,d,clr,8f,false,compact).fits12x12,"size advisor agrees");
            VinylEngine.Settings sl=std(11f,null);
            Zip legacy=export(OutputDeviceProfile.CRICUT,"12x12",standard(sl,w,h));
            check(legacy.rejected!=null&&legacy.rejected.getMessage().contains("Selected Cricut mat is 12 × 12"),"legacy corners 11in still refused on 12x12");
            Zip legacy24=export(OutputDeviceProfile.CRICUT,"12x24",standard(sl,w,h));
            check(legacy24.rejected==null&&Math.abs(legacy24.w-295.4)<.05,"legacy corners 295.4 mm accepted on 12x24: "+legacy24.w);
            StyleKit.Result sr=suiteResult(11f,w,h,Math.max(3,colors));
            float[][] sm=RegistrationMarks.aboveBelowPositions(sr.widthMm,sr.heightMm,d,clr);
            Zip sz=export(OutputDeviceProfile.CRICUT,"12x12",suite(sr,sm,d));
            check(sz.rejected==null&&Math.abs(sz.w-(sr.widthMm+12))<.02,"suite 11in + compact fits 12x12: "+(sz.rejected==null?"":sz.rejected.getMessage()));
            for(Map.Entry<String,String> e:sz.files.entrySet())if(e.getKey().matches("\\d\\d\\.svg")){
                List<double[]> found=markCircles(e.getValue(),d);
                for(float[] p:sm)check(has(found,p[0]+6.0,p[1]+6.0),"suite "+e.getKey()+": identical marks");
            }
            // QA fix: the editor places compact marks from the PHOTO aspect; the suite engine sizes the
            // artwork from its prepared raster, 0.1-0.2 mm different. Those marks must still give the
            // compact frame, re-seated on this artwork, identical on every sheet.
            for(float delta:new float[]{.16f,-.18f,.9f}){
                float[][] ui=RegistrationMarks.aboveBelowPositions(sr.widthMm,sr.heightMm+delta,d,clr);
                Zip uz=export(OutputDeviceProfile.CRICUT,"12x12",suite(sr,ui,d));
                check(uz.rejected==null&&Math.abs(uz.w-(sr.widthMm+12))<.02,"editor-aspect compact marks (dh="+delta+") fit 12x12: "+(uz.rejected==null?"":uz.rejected.getMessage()));
                for(Map.Entry<String,String> e:uz.files.entrySet())if(e.getKey().matches("\\d\\d\\.svg")){
                    List<double[]> found=markCircles(e.getValue(),d);
                    for(float[] p:sm)check(has(found,p[0]+6.0,p[1]+6.0),"re-seated "+e.getKey()+": exact compact marks");
                }
            }
            // Hand-placed marks are never re-seated: a 0.5 mm sideways drag, or a bottom row 1.5 mm off.
            float[][] dragged=RegistrationMarks.aboveBelowPositions(sr.widthMm,sr.heightMm,d,clr);dragged[2][0]+=.5f;
            check(!RegistrationMarks.isAboveBelowNear(dragged,sr.widthMm,sr.heightMm,d,clr),"dragged mark is manual");
            check(java.util.Arrays.deepEquals(RegistrationMarks.layout(sr.widthMm,sr.heightMm,d,clr,dragged,8f).positions,dragged),"dragged mark not moved");
            check(export(OutputDeviceProfile.CRICUT,"12x12",suite(sr,dragged,d)).rejected!=null,"dragged compact marks keep the 8 mm floor (refused on 12x12)");
            float[][] far=RegistrationMarks.aboveBelowPositions(sr.widthMm,sr.heightMm+1.5f,d,clr);
            check(!RegistrationMarks.isAboveBelowNear(far,sr.widthMm,sr.heightMm,d,clr),"bottom row 1.5 mm off is manual");
            // The size advisor honours the saved layout at a width other than the current one.
            check(CricutSizeAdvisor.estimate(11,w,h,true,d,clr,8f,false,
                CricutSizeAdvisor.layoutMarks(RegistrationMarks.PLACEMENT_ABOVE_BELOW,11,w,h,d,clr)).fits12x12,"advisor: compact 11 in fits 12x12");
            check(CricutSizeAdvisor.maximumWidthIn(false,w,h,true,d,clr,8f,false,RegistrationMarks.PLACEMENT_ABOVE_BELOW)>=11.0
                &&CricutSizeAdvisor.maximumWidthIn(false,w,h,true,d,clr,8f,false)<11.0,"advisor: largest width depends on layout");
            String combined=sz.files.get(CombinedLayerSvg.FILENAME);
            check(combined!=null,"single-upload SVG produced");
            SvgDxfR13Exporter.SafetyGeometry cg=SvgDxfR13Exporter.safetyGeometry(combined);
            check(Math.abs(cg.widthMm-sz.w)<.01&&cg.widthMm<=USABLE+.001,"single-upload SVG artboard within 12x12");
            check(sz.files.get(CricutMatPreflight.NOTE_FILE).startsWith("Selected mat: 12 × 12 in"),"CRICUT-MAT.txt names selected mat");
            float w2=11.25f*25.4f,h2=w2*h/w;
            VinylEngine.Settings sw=std(11.25f,RegistrationMarks.aboveBelowPositions(w2,h2,d,clr));
            check(export(OutputDeviceProfile.CRICUT,"12x12",standard(sw,w,h)).rejected!=null,"11.25 in refused on 12x12 even with compact marks");
            VinylEngine.Settings sb=std(11f,RegistrationMarks.aboveBelowPositions(wmm,hmm,4f,clr));sb.markSizeMm=4f;
            check(export(OutputDeviceProfile.CRICUT,"12x12",standard(sb,w,h)).rejected!=null,"4 mm marks need 7.5 mm margin: refused on 12x12");
        }
    }

    static void matEnforcementPaths()throws IOException{
        int w=84,h=60;
        VinylEngine.Settings sl=std(11f,null);
        StyleKit.Result sr=suiteResult(11f,w,h,3);
        check(export(OutputDeviceProfile.CRICUT,"12x12",standard(sl,w,h)).rejected!=null,"standard refused on 12x12");
        check(export(OutputDeviceProfile.CRICUT,"12x12",suite(sr,null,2.5f)).rejected!=null,"suite refused on 12x12");
        check(export(OutputDeviceProfile.CRICUT,"12x24",suite(sr,null,2.5f)).rejected==null,"suite accepted on 12x24");
        Zip legacy=export(OutputDeviceProfile.CRICUT,null,suite(sr,null,2.5f));
        check(legacy.rejected==null&&legacy.files.get(CricutMatPreflight.NOTE_FILE).startsWith("Mat: 12 × 24 in REQUIRED"),"no selection: 12x24 limit + note");
        StyleKit.Result huge=suiteResult(24f,w,h,3);
        check(export(OutputDeviceProfile.CRICUT,"12x24",suite(huge,null,2.5f)).rejected!=null,"24 in refused on every Cricut mat");
        Sheets mixed=cuts->{
            cuts.put("01.svg",NewStyleExport.layerSvg(sr,0,true,CIRCLE,2.5f,1f,null,.8f,false));
            cuts.put("02.svg",NewStyleExport.layerSvg(sr,1,true,CIRCLE,4f,1f,new float[][]{{-20,-20},{sr.widthMm+20,-20},{-20,sr.heightMm+20},{sr.widthMm+20,sr.heightMm+20}},.8f,false));
        };
        check(export(OutputDeviceProfile.CRICUT,"12x24",mixed).rejected!=null,"layers with different artboards refused");
        for(int dev:new int[]{OutputDeviceProfile.SILHOUETTE_DESIGNER,OutputDeviceProfile.GENERIC_SVG,OutputDeviceProfile.GRAPHTEC_STUDIO,
                OutputDeviceProfile.SISER_LEONARDO,OutputDeviceProfile.BROTHER_CANVAS,OutputDeviceProfile.SILHOUETTE_BASIC}){
            Zip z=export(dev,"12x12",suite(sr,null,2.5f));
            check(z.rejected==null,"device "+dev+": Cricut mat not applied (documented policy): "+(z.rejected==null?"":z.rejected.getMessage()));
            check(!z.files.containsKey(CricutMatPreflight.NOTE_FILE),"device "+dev+": no CRICUT-MAT.txt");
        }
    }

    static void audit()throws IOException{
        int w=84,h=60;
        StyleKit.Result sr=suiteResult(6f,w,h,3);
        Zip dxf=export(OutputDeviceProfile.SILHOUETTE_BASIC,null,suite(sr,null,2.5f));
        check(dxf.rejected==null,"DXF export");
        Zip svg=export(OutputDeviceProfile.GENERIC_SVG,null,suite(sr,null,2.5f));
        int sheetsSeen=0;
        for(String name:svg.files.keySet())if(name.matches("\\d\\d\\.svg")){
            sheetsSeen++;
            String s=svg.files.get(name);
            Matcher m=Pattern.compile("<svg[^>]*width=\"([0-9.]+)mm\"[^>]*height=\"([0-9.]+)mm\"[^>]*viewBox=\"0 0 ([0-9.]+) ([0-9.]+)\"").matcher(s);
            check(m.find(),name+": mm width/height and 0-origin viewBox");
            check(m.group(1).equals(m.group(3))&&m.group(2).equals(m.group(4)),name+": viewBox units == mm");
            String dx=dxf.files.get(name.replace(".svg",".dxf"));
            check(dx!=null,name+": DXF twin exists");
            check(dx.contains("$INSUNITS\r\n70\r\n4\r\n"),name+": $INSUNITS=4");
            check(dx.contains("AC1012"),name+": R13");
            check(!dx.contains("\r\nSPLINE\r\n")&&!dx.contains("\r\nLWPOLYLINE\r\n")&&!dx.contains("\r\nHATCH\r\n")&&!dx.contains("\r\nIMAGE\r\n"),name+": only CIRCLE/POLYLINE");
            Matcher pl=Pattern.compile("AcDb2dPolyline\r\n66\r\n1\r\n10\r\n0.0\r\n20\r\n0.0\r\n30\r\n0.0\r\n70\r\n(\\d+)\r\n").matcher(dx);
            while(pl.find())check("1".equals(pl.group(1)),name+": polyline closed");
            SvgDxfR13Exporter.Result r=SvgDxfR13Exporter.convert(s);
            check(r.openPaths==0&&r.closedContours>0,name+": all contours closed");
            check(Math.abs(r.widthMm-Double.parseDouble(m.group(1)))<1e-6,name+": DXF artboard mm == SVG mm");
            SvgDxfR13Exporter.SafetyGeometry g=SvgDxfR13Exporter.safetyGeometry(s);
            check(r.minXmm>=-.05&&r.maxXmm<=g.widthMm+.05&&r.minYmm>=-.05&&r.maxYmm<=g.heightMm+.05,name+": DXF coordinates inside artboard");
        }
        check(sheetsSeen>=2,"multiple sheets audited");
        check(dxf.files.get("dimensions-dxf.txt").contains("PER-FILE PHYSICAL CUT GEOMETRY BOUNDS"),"DXF dimensions note");
        for(int colors:new int[]{2,5})
            check(export(OutputDeviceProfile.SILHOUETTE_BASIC,null,suite(suiteResult(4f,w,h,colors),null,2.5f)).rejected==null,"DXF "+colors+" colours");
        String ok="<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10mm\" height=\"10mm\" viewBox=\"0 0 10 10\"><path d=\"M 1 1 C 2 0 4 0 5 1 L 5 5 Z\"/></svg>";
        CutSvgCompatibility.verify(ok);
        check(SvgDxfR13Exporter.convert(ok).closedContours==1,"cubic path flattened to a closed DXF polyline");
        for(String bad:new String[]{"<image/>","<text>x</text>","<use/>","<clipPath/>","<style/>","<linearGradient/>","<mask/>","<pattern/>","<filter/>"}){
            final String tag=bad;
            check(throwsIae(()->CutSvgCompatibility.verify(ok.replace("</svg>",tag+"</svg>"))),"forbidden construct rejected: "+bad);
        }
        String head="<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10mm\" height=\"10mm\" viewBox=\"0 0 10 10\">";
        String[] malformed={
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\" viewBox=\"0 0 10 10\"><path d=\"M 1 1 L 2 2 Z\"/></svg>",
            head+"<path d=\"M 1 1 L NaN 2 Z\"/></svg>",
            head+"<path d=\"M 1 1 L 1e12 2 Z\"/></svg>",
            head+"<path d=\"M 1 1 L 50 50 L 1 50 Z\"/></svg>",
            head+"<path d=\"M 1 1 L 5\"/></svg>",
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10mm\" height=\"10mm\" viewBox=\"0 0 20 10\"><path d=\"M 1 1 L 5 5 L 1 5 Z\"/></svg>",
            "not svg at all"};
        for(int i=0;i<malformed.length;i++){
            final String bad=malformed[i];
            for(int dev:new int[]{OutputDeviceProfile.CRICUT,OutputDeviceProfile.SILHOUETTE_BASIC})
                check(export(dev,"12x12",cuts->cuts.put("bad.svg",bad)).rejected!=null,"malformed fixture "+i+" refused (device "+dev+")");
        }
        String tiny="<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"20.000mm\" height=\"20.000mm\" viewBox=\"0 0 20.000 20.000\"><g fill=\"#FF0000\"><path d=\"M 2 2 L 18 2 L 18 18 L 2 18 Z\"/></g></svg>";
        Zip t=export(OutputDeviceProfile.SILHOUETTE_BASIC,null,cuts->cuts.put("tiny.svg",tiny));
        check(t.rejected==null&&Math.abs(t.w-20)<1e-6,"tiny sheet exports at 20 mm");
        check(t.files.get("tiny.dxf").contains("18.000000")&&t.files.get("tiny.dxf").contains("2.000000"),"DXF carries mm coordinates");
    }

    public static void main(String[] args)throws Exception{
        layoutContract();
        matAcceptance();
        matEnforcementPaths();
        audit();
        System.out.println("PASS M5 export/mat ("+checks+" checks)");
    }
}

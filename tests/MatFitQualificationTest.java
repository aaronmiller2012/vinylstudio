package com.halftonestudio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * M5 mat fit, driven by the ACTUAL marked export artboards of real engines.
 *  - Standard engine (VinylEngine) and a suite style (Prismatic Chevron) at 4, 6, 8 and 11 in,
 *    portrait, landscape and square.
 *  - The selected mat is authoritative: a 12x12 selection rejects any sheet larger than
 *    11.5 x 11.5 in; a 12x24 selection accepts up to 11.5 x 23.5 in. Nothing is rescaled:
 *    accepted sheets keep exactly the requested artwork width.
 *  - The Size dialog estimate never under-reports an exporter's sheet, and the early
 *    (pre-render) refusal bound never over-reports it, so it cannot refuse a sheet that fits.
 *  - The single-upload SVG shares the sheets' artboard, so the same check covers it.
 */
public final class MatFitQualificationTest {
    static int checks=0;
    static void check(boolean ok,String what){checks++;if(!ok)throw new AssertionError(what);}
    static final double USABLE=11.5*25.4,LONG=23.5*25.4;

    static final class Export {
        double w,h;String note;Map<String,byte[]> files=new HashMap<>();IOException rejected;
    }
    interface Sheets{void write(CutSheetZipWriter cuts)throws IOException;}

    static Export export(String mat,Sheets sheets)throws IOException{
        Export e=new Export();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)){
            CutSheetZipWriter cuts=new CutSheetZipWriter(zip,OutputDeviceProfile.CRICUT,.8f,.5f,true).selectMat(mat);
            check(mat.equals(cuts.selectedMat()),"writer keeps the selected mat");
            try{sheets.write(cuts);cuts.finish();}
            catch(IOException rejected){e.rejected=rejected;return e;}
        }
        try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))){
            for(ZipEntry z;(z=in.getNextEntry())!=null;)e.files.put(z.getName(),in.readAllBytes());
        }
        String boards=new String(e.files.get("physical-artboards.csv"),StandardCharsets.UTF_8);
        String[] row=boards.split("\n")[1].split(",");
        e.w=Double.parseDouble(row[1]);e.h=Double.parseDouble(row[2]);
        e.note=new String(e.files.get(CricutMatPreflight.NOTE_FILE),StandardCharsets.UTF_8);
        return e;
    }
    static final Pattern SIZE=Pattern.compile("<svg[^>]*?width=\"([0-9.]+)mm\"[^>]*?height=\"([0-9.]+)mm\"");
    static double[] svgSize(String svg){
        Matcher m=SIZE.matcher(svg);check(m.find(),"svg has physical size");
        return new double[]{Double.parseDouble(m.group(1)),Double.parseDouble(m.group(2))};
    }

    /** Runs both mats on one artwork and checks every contract against the actual sheet. */
    static void qualify(String label,float widthMm,int imageW,int imageH,float outerSpacing,
                        float markMm,float clearance,Sheets sheets)throws IOException{
        // Actual artboard from an unconstrained (12x24) export, or from the error if it fits nothing.
        Export long24=export(CricutMatPreflight.MAT_12X24,sheets);
        Export short12=export(CricutMatPreflight.MAT_12X12,sheets);
        CricutSizeAdvisor.Estimate high=CricutSizeAdvisor.estimate(widthMm/25.4,imageW,imageH,true,markMm,clearance,
            outerSpacing,false,null);
        CricutSizeAdvisor.Estimate low=CricutSizeAdvisor.estimate(widthMm/25.4,imageW,imageH,true,markMm,clearance,
            Math.min(SheetFrame.SPECIALTY_PADDING_MM,outerSpacing),false,null);
        if(long24.rejected==null){
            double w=long24.w,h=long24.h;
            check(high.sheetWidthMm>=w-.02&&high.sheetHeightMm>=h-.02,
                String.format(Locale.US,"%s: size estimate %.2fx%.2f under-reports the actual sheet %.2fx%.2f",
                    label,high.sheetWidthMm,high.sheetHeightMm,w,h));
            check(low.sheetWidthMm<=w+.02&&low.sheetHeightMm<=h+.02,
                String.format(Locale.US,"%s: early refusal bound %.2fx%.2f exceeds the actual sheet %.2fx%.2f",
                    label,low.sheetWidthMm,low.sheetHeightMm,w,h));
            boolean fits12=w<=USABLE+.001&&h<=USABLE+.001;
            check(fits12==(short12.rejected==null),label+": 12x12 selection must accept exactly the sheets within 11.5 x 11.5 in ("+
                String.format(Locale.US,"%.2fx%.2f mm",w,h)+")"+(short12.rejected==null?"":": "+short12.rejected.getMessage()));
            if(!fits12){
                String msg=short12.rejected.getMessage();
                check(msg.contains("Selected Cricut mat is 12 × 12 in")&&msg.contains("never silently rescale"),
                    label+": refusal names the selected mat and promises no rescale: "+msg);
            }else{
                check(short12.note.startsWith("Selected mat: 12 × 12 in"),label+": 12x12 note "+short12.note);
                check(Math.abs(short12.w-w)<1e-6&&Math.abs(short12.h-h)<1e-6,label+": mat choice never changes the sheet");
            }
            check(long24.note.startsWith("Selected mat: 12 × 24 in")&&long24.note.contains(fits12?"would also fit":"will not fit"),
                label+": 12x24 note "+long24.note);
            byte[] combined=long24.files.get(CombinedLayerSvg.FILENAME);
            if(combined!=null){
                double[] c=svgSize(new String(combined,StandardCharsets.UTF_8));
                check(Math.abs(c[0]-w)<.01&&Math.abs(c[1]-h)<.01,label+": single-upload SVG has the sheets' artboard");
            }
            // No resizing: the artwork width inside the sheet is the requested width exactly.
            double pad=(w-widthMm)/2;
            check(pad>0&&Math.abs((h-2*pad)-widthMm*imageH/(double)imageW)<.6,label+": artwork keeps its aspect and width");
            System.out.printf(Locale.US,"  %-34s sheet %6.1f x %6.1f mm  12x12:%-8s 12x24:accepted%n",label,w,h,
                fits12?"accepted":"REFUSED");
        }else{
            check(long24.rejected.getMessage().contains("mat limit"),label+": over-limit message "+long24.rejected.getMessage());
            check(short12.rejected!=null,label+": nothing larger than 12x24 is accepted on 12x12");
            check(!(high.fits12x24),label+": estimate agrees the sheet fits no Cricut mat");
            System.out.printf(Locale.US,"  %-34s REFUSED on both mats (%s)%n",label,long24.rejected.getMessage().split(":")[0]);
        }
    }

    static void standard(float inches,int w,int h)throws IOException{
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.widthMm=inches*25.4f;s.registration=true;
        VinylEngine.Result r=VinylEngine.generate(V3AllStyleExportMatrixTest.portrait(w,h),w,h,s);
        qualify(String.format(Locale.US,"standard %dx%d @ %.0f in",w,h,inches),s.widthMm,w,h,s.outerSpacingMm,
            s.markSizeMm,s.registrationClearanceMm,cuts->{
                for(int i=0;i<r.settings.layers.size();i++)if(VinylEngine.cutLayer(r,i))
                    cuts.put(String.format(Locale.US,"%02d.svg",i+1),VinylEngine.svg(r,i));
            });
    }
    static void suite(float inches,int w,int h)throws IOException{
        VinylEngine.Settings d=VinylEngine.Settings.defaults();
        VinylStyleSuite.Config c=new VinylStyleSuite.Config();
        c.style=VinylStyleSuite.PRISMATIC_CHEVRON;c.widthMm=inches*25.4f;c.detail=40;c.colorCount=4;c.minimumMm=.8f;
        c.gapMm=VinylStyleSuite.generationGapMm(.5f,.8f);c.paper=0xfffaf9f5;
        c.palette=new int[]{0xfff4ece0,0xffd9a07a,0xff6a7fa8,0xff1c1a20};
        StyleKit.Result r=VinylStyleSuite.generate(V3AllStyleExportMatrixTest.portrait(w,h),w,h,c);
        qualify(String.format(Locale.US,"Prismatic Chevron %dx%d @ %.0f in",w,h,inches),c.widthMm,w,h,d.outerSpacingMm,
            d.markSizeMm,d.registrationClearanceMm,cuts->{
                for(int i=0;i<r.layers.size();i++)if(!r.layers.get(i).loops.isEmpty())
                    cuts.put(String.format(Locale.US,"%02d.svg",i+1),
                        NewStyleExport.layerSvg(r,i,true,0,d.markSizeMm,d.registrationClearanceMm,null,.8f));
            });
    }

    static void boundaries()throws IOException{
        check(CricutMatPreflight.normalizeMat(null)==null&&CricutMatPreflight.normalizeMat("24x24")==null,
            "unknown mats are not selections");
        check(CricutMatPreflight.fitsMat(292.1,292.1,"12x12")&&!CricutMatPreflight.fitsMat(292.2,292.1,"12x12"),"12x12 edge");
        check(CricutMatPreflight.fitsMat(292.1,596.9,"12x24")&&CricutMatPreflight.fitsMat(596.9,292.1,"12x24"),"12x24 both orientations");
        check(!CricutMatPreflight.fitsMat(292.2,292.2,"12x24"),"over 11.5 in both sides fits no mat");
        CricutMatPreflight.requireFits(290,500,"legacy.svg",null); // no selection: legacy 12x24 limit
        boolean refused=false;
        try{CricutMatPreflight.requireFits(290,500,"x.svg","12x12");}catch(IOException e){refused=true;}
        check(refused,"12x12 selection refuses a 12x24 sheet");
        // Custom marks enlarge the estimate exactly as they enlarge the exported frame.
        float wmm=6*25.4f,hmm=wmm;
        float[][] far={{-20,-20},{wmm+20,-20},{-20,hmm+20},{wmm+20,hmm+20}};
        CricutSizeAdvisor.Estimate custom=CricutSizeAdvisor.estimate(6,100,100,true,6f,1f,8f,false,far);
        SheetFrame frame=SheetFrame.of(wmm,hmm,true,6f,1f,far,8f,false);
        check(Math.abs(custom.sheetWidthMm-frame.boardWidthMm())<.01,"custom marks: estimate equals the export frame");
    }

    public static void main(String[] args)throws Exception{
        boundaries();
        int[][] dims={{60,84},{84,60},{72,72}};
        for(int[] d:dims)for(float in:new float[]{4,6,8,11}){standard(in,d[0],d[1]);suite(in,d[0],d[1]);}
        System.out.println("PASS mat fit qualification ("+checks+" checks): selected mat authoritative, estimates bracket real sheets");
    }
}

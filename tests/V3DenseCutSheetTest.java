package com.halftonestudio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.util.Locale;

/** Exact dense contour safety tests for 11-inch high-detail Shapes exports. */
public final class V3DenseCutSheetTest {
    private static void ok(boolean yes,String what){if(!yes)throw new AssertionError(what);}
    private static String manyRectangles(boolean collide){
        StringBuilder svg=new StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' width='280mm' height='280mm' viewBox='0 0 280 280'>");
        for(int row=0;row<62;row++)for(int col=0;col<62;col++){
            double x=3+col*4,y=3+row*4;
            svg.append("<rect x='").append(x).append("' y='").append(y).append("' width='1' height='1'/>");
        }
        if(collide)svg.append("<rect x='3.5' y='3.5' width='1' height='1'/>");
        return svg.append("</svg>").toString();
    }
    private static String manyStars(){
        StringBuilder svg=new StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' width='280mm' height='280mm' viewBox='0 0 280 280'>");
        for(int row=0;row<62;row++)for(int col=0;col<62;col++){
            double x=3+col*4,y=3+row*4;
            svg.append("<path d='");
            for(int i=0;i<10;i++){
                double a=i*Math.PI/5-Math.PI/2,r=(i%2==0?1.0:.58);
                svg.append(i==0?"M":"L").append(String.format(Locale.US,"%.4f %.4f ",x+r*Math.cos(a),y+r*Math.sin(a)));
            }
            svg.append("Z'/>");
        }
        return svg.append("</svg>").toString();
    }
    private static void archive(String svg,boolean approved) throws Exception{
        ByteArrayOutputStream data=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(data)){
            CutSheetZipWriter out=new CutSheetZipWriter(zip,OutputDeviceProfile.GENERIC_SVG,.254f,approved);
            out.put("dense.svg",svg);out.finish();
        }
        ok(data.size()>4096,"Dense SVG ZIP missing");
    }
    public static void main(String[] args)throws Exception{
        String safe=manyRectangles(false);
        FinalCutSheetSafety.Report report=FinalCutSheetSafety.inspect("dense.svg",safe,.254f);
        ok(report.contours==3844,"Incorrect contour count");
        ok(report.unverified==0,"Clean 3844 contours did not finish spatial inspection: "+report.detail);
        ok(report.critical==0,"Clean dense contours flagged: "+report.detail);
        archive(safe,false);
        String star=manyStars();
        FinalCutSheetSafety.Report stars=FinalCutSheetSafety.inspect("stars.svg",star,.254f);
        ok(stars.contours==3844,"Wrong dense-star count");
        ok(stars.unverified==0,"Dense star inspection incomplete: "+stars.detail);
        ok(stars.critical==0,"Unexpected star intersections: "+stars.detail);
        String crossing=manyRectangles(true);
        FinalCutSheetSafety.Report bad=FinalCutSheetSafety.inspect("dense.svg",crossing,.254f);
        ok(bad.critical>0,"Overlapping dense contours not detected");
        boolean blocked=false;
        try{archive(crossing,false);}catch(IOException ex){blocked=true;}
        ok(blocked,"Unreviewed dense collision exported");
        archive(crossing,true);
        // An incomplete scan requires explicit acknowledgement and must put
        // a prominent NON-PRODUCTION warning in the reviewed ZIP itself.
        StringBuilder many=new StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' width='2000mm' height='2000mm' viewBox='0 0 2000 2000'>");
        for(int i=0;i<12001;i++)many.append("<circle cx='").append(5+(i%100)*10)
            .append("' cy='").append(5+(i/100)*10).append("' r='1'/>");
        many.append("</svg>");
        boolean requiresReview=false;
        try(ZipOutputStream zip=new ZipOutputStream(new ByteArrayOutputStream())){
            CutSheetZipWriter writer=new CutSheetZipWriter(zip,OutputDeviceProfile.GENERIC_SVG,.254f,false);
            writer.put("many.svg",many.toString());
            try{writer.finish();}catch(FinalCutReviewRequired incomplete){requiresReview=true;}
        }
        ok(requiresReview,"Incomplete scan should trigger explicit reviewed-export workflow");
        ByteArrayOutputStream reviewed=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(reviewed)){
            CutSheetZipWriter writer=new CutSheetZipWriter(zip,OutputDeviceProfile.GENERIC_SVG,.254f,true);
            writer.put("many.svg",many.toString());writer.finish();
        }
        boolean warning=false;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(reviewed.toByteArray()))){
            ZipEntry item;while((item=zip.getNextEntry())!=null)
                if("NOT-CUT-QUALIFIED.txt".equals(item.getName())){
                    warning=new String(zip.readAllBytes(),StandardCharsets.UTF_8).contains("NOT FULLY INSPECTED");
                }
        }
        ok(warning,"Inspection-only export lacks conspicuous unsafe-cut notice");
        System.out.println("PASS dense contours, collision rejection, explicit review and diagnostic ZIP labeling");
    }
}

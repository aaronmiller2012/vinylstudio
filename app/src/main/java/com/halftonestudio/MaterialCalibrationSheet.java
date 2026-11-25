package com.halftonestudio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.*;

/** Independent scrap test; never filters deliberately undersized specimens. */
public final class MaterialCalibrationSheet {
    private static final double[] SIZES={.254,.35,.5,.65,.8,1,1.25,1.5};
    private MaterialCalibrationSheet(){}
    private static String n(double v){return String.format(Locale.US,"%.4f",v);}
    private static String rect(double x,double y,double w,double h){return "<rect x='"+n(x)+"' y='"+n(y)+"' width='"+n(w)+"' height='"+n(h)+"'/>";}
    public static String svg(){
        StringBuilder b=new StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' width='120mm' height='100mm' viewBox='0 0 120 100'><g fill='#000000'>");
        // Two opposite reference squares establish the actual import bounds.
        b.append(rect(0,0,10,10)).append(rect(110,90,10,10));
        for(int i=0;i<SIZES.length;i++){
            double s=SIZES[i],x=12+13*i;
            b.append("<circle cx='").append(n(x)).append("' cy='20' r='").append(n(s/2)).append("'/>");
            b.append(rect(x-s/2,30-s/2,s,s));
            b.append("<polygon points='").append(n(x)).append(",").append(n(40-s/2)).append(" ").append(n(x+s/2)).append(",40 ").append(n(x)).append(",").append(n(40+s/2)).append(" ").append(n(x-s/2)).append(",40'/>");
            b.append(rect(x-s/2,48,s,8));
            b.append(rect(x-4,63,4-s/2,6)).append(rect(x+s/2,63,4-s/2,6));
            b.append("<path fill-rule='evenodd' d='M ").append(n(x-4)).append(" 75 h 8 v 8 h -8 z M ").append(n(x-s/2)).append(" ").append(n(79-s/2)).append(" v ").append(n(s)).append(" h ").append(n(s)).append(" v -").append(n(s)).append(" z'/>");
        }
        return b.append("</g></svg>").toString();
    }
    private static void entry(ZipOutputStream z,String name,String content)throws IOException{
        z.putNextEntry(new ZipEntry(name));z.write(content.getBytes(StandardCharsets.UTF_8));z.closeEntry();
    }
    public static void write(OutputStream out,boolean dxf)throws IOException{
        String svg=svg();
        try(ZipOutputStream z=new ZipOutputStream(out)){
            entry(z,dxf?"calibration.dxf":"calibration.svg",dxf?SvgDxfR13Exporter.convert(svg).dxf:svg);
            entry(z,"READ-FIRST.txt","Scrap test only: deliberately tiny features. Verify 120 x 100 mm geometry bounds and both 10 mm squares after import. Keep all shapes attached at original scale. Columns left to right increase in size. Rows top to bottom: dots, squares, diamonds (diagonal), ribbons, paired rectangles (gap), square holes. Record cut, weed and transfer separately. Repeat before choosing reliable limits. Do not treat a single successful cut as certification.\n");
            StringBuilder csv=new StringBuilder("row,size_mm,cut,weed,transfer\n");
            for(String row:new String[]{"dot","square","diamond_diagonal","ribbon","gap","hole"})for(double s:SIZES)csv.append(row).append(',').append(n(s)).append(",,,\n");
            entry(z,"results.csv",csv.toString());
        }
    }
}

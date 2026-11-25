package com.halftonestudio;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.zip.ZipOutputStream;

/** Real style registry smoke matrix: each live style -> final geometry -> SVG/DXF writer. */
public final class V3AllStyleExportMatrixTest {
    static int[] portrait(int w,int h){
        int[] p=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double fx=(x-w*.5)/(w*.5),fy=(y-h*.49)/(h*.5);
            double detail=.42+.26*Math.cos(fx*11)*Math.sin(fy*13);
            double face=Math.max(0,1.0-(fx*fx*1.2+fy*fy));
            int k=(int)Math.max(5,Math.min(250,255*(.25+.58*face+.16*detail)));
            p[y*w+x]=0xff000000 | (k<<16) | (((k*5)/6)<<8) | ((k*3)/4);
        }
        return p;
    }
    public static void main(String[] args)throws Exception{
        int[] pixels=portrait(60,60);
        int selected=0,rendered=0,sheets=0,failed=0,skipped=0;
        ArrayList<String> failures=new ArrayList<>();
        for(int style=0;style<VinylStyleSuite.NAMES.length;style++){
            if(VinylStyleSuite.isRetiredStyleId(style)||!NewStyleVariantPolicy.selectable(style)){skipped++;continue;}
            selected++;
            String name=VinylStyleSuite.NAMES[style];
            try{
                VinylStyleSuite.Config cfg=new VinylStyleSuite.Config();
                cfg.style=style;cfg.widthMm=101.6f;cfg.detail=25;cfg.colorCount=4;
                cfg.minimumMm=.254f;cfg.draft=false;cfg.weightMm=.7f;
                StyleKit.Result result=VinylStyleSuite.generate(pixels,60,60,cfg);
                if(result==null||result.layers.isEmpty())throw new IllegalStateException("no output layers");
                rendered++;
                int nonEmpty=0;
                for(int i=0;i<result.layers.size();i++){
                    StyleKit.Layer layer=result.layers.get(i);
                    if(layer.loops.isEmpty()&&layer.lines.isEmpty())continue;
                    nonEmpty++;
                    String svg=NewStyleExport.layerSvg(result,i,false,0,3f,2f,null,.254f);
                    CutSvgCompatibility.verify(svg,OutputDeviceProfile.GENERIC_SVG);
                    for(int device:new int[]{OutputDeviceProfile.GENERIC_SVG,OutputDeviceProfile.SILHOUETTE_BASIC}){
                        ByteArrayOutputStream out=new ByteArrayOutputStream();
                        try(ZipOutputStream zip=new ZipOutputStream(out)){
                            CutSheetZipWriter cuts=new CutSheetZipWriter(zip,device,.254f,true);
                            cuts.put("layer.svg",svg);cuts.finish();
                        }
                        if(out.size()<100)throw new IllegalStateException("no ZIP output");
                        sheets++;
                    }
                }
                if(nonEmpty==0)throw new IllegalStateException("all vector layers empty");
                System.out.println("PASS "+style+" "+name+" layers="+nonEmpty);
            }catch(Exception e){
                failed++;failures.add(style+" "+name+": "+e.getClass().getSimpleName()+" "+e.getMessage());
                System.out.println("FAIL "+failures.get(failures.size()-1));
            }
        }
        System.out.printf(Locale.US,"Style export matrix: registered=%d selected=%d rendered=%d  per-format sheets=%d failures=%d retired=%d%n",VinylStyleSuite.NAMES.length,selected,rendered,sheets,failed,skipped);
        if(failed>0)throw new AssertionError(String.join(" | ",failures));
        System.out.println("PASS live style export to SVG and R13 DXF through final sheet writer");
    }
}
